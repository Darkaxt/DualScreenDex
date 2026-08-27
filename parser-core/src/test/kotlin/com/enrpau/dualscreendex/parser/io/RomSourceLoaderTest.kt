package com.enrpau.dualscreendex.parser.io

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RomSourceLoaderTest {
    @Test
    fun loadsRawSourcesFromOwnedPathsWithoutChangingIdentity() {
        val directory = temporaryDirectory()
        val path = directory.resolve("upload.body")
        val bytes = ByteArray(0x200) { it.toByte() }
        try {
            Files.write(path, bytes)

            val loaded = RomSourceLoader.load("Pokemon Emerald.gba", path)

            assertEquals("Pokemon Emerald.gba", loaded.displayName)
            assertEquals(bytes.size, loaded.rom.size)
            assertArrayEquals(bytes.copyOfRange(0x40, 0x60), loaded.rom.slice(0x40, 0x20))
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun trimsBoundedUnaddressableTrailerFromStructurallyValidGbaSources() {
        val source = gbaBytes(RomImage.MAX_SIZE_BYTES + 1_131)
        source.fill(0x5A, RomImage.MAX_SIZE_BYTES)

        val loaded = RomSourceLoader.load("Pokemon Adventure.gba", source)

        assertEquals(RomImage.MAX_SIZE_BYTES, loaded.rom.size)
        assertEquals(0, loaded.rom.u8(RomImage.MAX_SIZE_BYTES - 1))
    }

    @Test
    fun trimsBoundedUnaddressableTrailerFromOwnedGbaPaths() {
        val directory = temporaryDirectory()
        val path = directory.resolve("upload.body")
        try {
            Files.newByteChannel(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
                channel.write(java.nio.ByteBuffer.wrap(gbaBytes(0x200)))
                channel.position(RomImage.MAX_SIZE_BYTES.toLong() + 1_130)
                channel.write(java.nio.ByteBuffer.wrap(byteArrayOf(0x5A)))
            }

            val loaded = RomSourceLoader.load("Pokemon Adventure.gba", path)

            assertEquals(RomImage.MAX_SIZE_BYTES, loaded.rom.size)
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun rejectsUnrecognizedOrUnboundedGbaOverflow() {
        val unrecognized = assertThrows(IllegalArgumentException::class.java) {
            RomSourceLoader.load("invalid.gba", ByteArray(RomImage.MAX_SIZE_BYTES + 1))
        }
        assertTrue(unrecognized.message.orEmpty().contains("32 MiB"))

        val unbounded = assertThrows(IllegalArgumentException::class.java) {
            RomSourceLoader.load("oversized.gba", gbaBytes(RomImage.MAX_SIZE_BYTES + 4_097))
        }
        assertTrue(unbounded.message.orEmpty().contains("32 MiB"))
    }

    @Test
    fun inspectionAndLoadingRetainTheSameAddressableGbaIdentity() {
        val directory = temporaryDirectory()
        val path = directory.resolve("trailer.gba")
        try {
            val source = gbaBytes(RomImage.MAX_SIZE_BYTES + 1_131)
            source.fill(0x5A, RomImage.MAX_SIZE_BYTES)
            Files.write(path, source)

            val inspected = RomSourceLoader.inspect(path)
            val loaded = RomSourceLoader.load(path)

            assertEquals(loaded.rom.sha256, inspected.sha256)
            assertEquals(loaded.rom.crc32, inspected.crc32)
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun preflightsMultipleZipMembersAndLoadsOneSelectedEntry() {
        val directory = temporaryDirectory()
        val path = directory.resolve("corpus.zip")
        val first = ByteArray(0x150) { 1 }
        val second = ByteArray(0x150) { 2 }
        try {
            ZipOutputStream(Files.newOutputStream(path)).use { zip ->
                zip.putNextEntry(ZipEntry("first.gb"))
                zip.write(first)
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("second.gbc"))
                zip.write(second)
                zip.closeEntry()
            }

            assertEquals(
                listOf("first.gb", "second.gbc"),
                RomSourceLoader.zipRomEntries("corpus.zip", path),
            )
            val loaded = RomSourceLoader.loadZipEntry(
                "corpus.zip",
                path,
                "second.gbc",
            )

            assertEquals("corpus.zip!second.gbc", loaded.displayName)
            assertArrayEquals(second, loaded.rom.slice(0, second.size))
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun rejectsOversizedDeflatedNonRomMembersAfterAValidRom() {
        val directory = temporaryDirectory()
        val path = directory.resolve("deflated-bomb.zip")
        try {
            ZipOutputStream(Files.newOutputStream(path)).use { zip ->
                zip.putNextEntry(ZipEntry("valid.gba"))
                zip.write(gbaBytes(0x200))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("notes.txt"))
                val block = ByteArray(64 * 1024)
                repeat((8 * 1024 * 1024 / block.size) + 1) { zip.write(block) }
                zip.closeEntry()
            }

            val loadFailure = assertThrows(IllegalArgumentException::class.java) {
                RomSourceLoader.load(path)
            }
            val inspectFailure = assertThrows(IllegalArgumentException::class.java) {
                RomSourceLoader.inspect(path)
            }
            val discoveryFailure = assertThrows(IllegalArgumentException::class.java) {
                RomSourceLoader.zipRomEntries(path.fileName.toString(), path)
            }
            val selectedFailure = assertThrows(IllegalArgumentException::class.java) {
                RomSourceLoader.loadZipEntry(path.fileName.toString(), path, "valid.gba")
            }

            assertTrue(loadFailure.message.orEmpty().contains("non-ROM member"))
            assertTrue(inspectFailure.message.orEmpty().contains("non-ROM member"))
            assertTrue(discoveryFailure.message.orEmpty().contains("non-ROM member"))
            assertTrue(selectedFailure.message.orEmpty().contains("non-ROM member"))
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun rejectsArchivesBeyondTheEntryCountLimit() {
        val directory = temporaryDirectory()
        val path = directory.resolve("too-many-entries.zip")
        try {
            ZipOutputStream(Files.newOutputStream(path)).use { zip ->
                zip.putNextEntry(ZipEntry("valid.gba"))
                zip.write(gbaBytes(0x200))
                zip.closeEntry()
                repeat(1_024) { index ->
                    zip.putNextEntry(ZipEntry("notes/$index.txt"))
                    zip.closeEntry()
                }
            }

            val failure = assertThrows(IllegalArgumentException::class.java) {
                RomSourceLoader.load(path)
            }
            val discoveryFailure = assertThrows(IllegalArgumentException::class.java) {
                RomSourceLoader.zipRomEntries(path.fileName.toString(), path)
            }
            val selectedFailure = assertThrows(IllegalArgumentException::class.java) {
                RomSourceLoader.loadZipEntry(path.fileName.toString(), path, "valid.gba")
            }

            assertTrue(failure.message.orEmpty().contains("entry count"))
            assertTrue(discoveryFailure.message.orEmpty().contains("entry count"))
            assertTrue(selectedFailure.message.orEmpty().contains("entry count"))
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun rejectsOversizedRawInspectionFromFileMetadata() {
        val directory = temporaryDirectory()
        val path = directory.resolve("oversized.gb")
        try {
            Files.newByteChannel(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
                channel.position(RomImage.MAX_SIZE_BYTES.toLong())
                channel.write(java.nio.ByteBuffer.wrap(byteArrayOf(0)))
            }

            val failure = assertThrows(IllegalArgumentException::class.java) {
                RomSourceLoader.inspect(path)
            }

            assertTrue(failure.message.orEmpty().contains("32 MiB"))
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun rejectsCompressedSourcesAboveSixtyFourMebibytesBeforeDecode() {
        val directory = temporaryDirectory()
        val path = directory.resolve("oversized.body")
        try {
            Files.newByteChannel(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
                channel.position(64L * 1024 * 1024)
                channel.write(java.nio.ByteBuffer.wrap(byteArrayOf(0)))
            }

            val failure = assertThrows(IllegalArgumentException::class.java) {
                RomSourceLoader.load("oversized.zip", path)
            }

            assertTrue(failure.message.orEmpty().contains("64 MiB"))
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun rejectsArchiveEntriesAboveThirtyTwoMebibytesDuringStreamingExtraction() {
        val directory = temporaryDirectory()
        val path = directory.resolve("oversized.zip")
        try {
            ZipOutputStream(Files.newOutputStream(path)).use { zip ->
                zip.putNextEntry(ZipEntry("oversized.gba"))
                val block = ByteArray(64 * 1024)
                repeat((32 * 1024 * 1024 / block.size) + 1) { zip.write(block) }
                zip.closeEntry()
            }

            val failure = assertThrows(IllegalArgumentException::class.java) {
                RomSourceLoader.load(path)
            }

            assertTrue(failure.message.orEmpty().contains("32 MiB"))
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    private fun gbaBytes(size: Int): ByteArray = ByteArray(size).also { bytes ->
        byteArrayOf(
            0x24, 0xFF.toByte(), 0xAE.toByte(), 0x51, 0x69, 0x9A.toByte(),
            0xA2.toByte(), 0x21, 0x3D, 0x84.toByte(), 0x82.toByte(), 0x0A,
        ).copyInto(bytes, 0x04)
        "TEST ROM".encodeToByteArray().copyInto(bytes, 0xA0)
        "TEST".encodeToByteArray().copyInto(bytes, 0xAC)
    }

    private fun temporaryDirectory(): Path {
        Files.createDirectories(Path.of("build"))
        return Files.createTempDirectory(Path.of("build"), "rom-source-")
    }
}
