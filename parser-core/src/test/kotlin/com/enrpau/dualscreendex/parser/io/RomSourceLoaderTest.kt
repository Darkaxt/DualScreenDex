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

    private fun temporaryDirectory(): Path {
        Files.createDirectories(Path.of("build"))
        return Files.createTempDirectory(Path.of("build"), "rom-source-")
    }
}
