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
