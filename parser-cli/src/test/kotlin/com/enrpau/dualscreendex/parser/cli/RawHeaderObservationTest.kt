package com.enrpau.dualscreendex.parser.cli

import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RawHeaderObservationTest {
    @Test fun observationUsesTheLoadedImageAfterItsSyntheticSourceIsRemoved() {
        val directory = Files.createTempDirectory("dualdex-loaded-header-")
        val path = directory.resolve("misleading-gba-name.gba")
        try {
            Files.write(path, gbBytes())
            val input = CorpusInput(path.fileName.toString(), "fabricated", path = path)
            val rom = input.loadRom()
            val attempt = CatalogParser.parseCatching(rom)
            assertEquals(Platform.GB, attempt.analysis.header.platform)
            // Changing and deleting the fabricated source cannot change or supply this observation.
            Files.write(path, ByteArray(512))
            Files.delete(path)
            val observation = observeRawHeader(rom, attempt.analysis.header.platform)
            assertEquals(GB_HASH, observation?.rawHeaderSha256)
            assertEquals(80, observation?.byteCount)
            assertEquals(rom.sha256, attempt.analysis.sha256)
        } finally {
            Files.deleteIfExists(path)
            Files.delete(directory)
        }
    }

    @Test fun observedByteCountsAreExactForRecognizedGbAndGbcImages() {
        for (flag in listOf(0, 0x80)) {
            val bytes = gbBytes().also { it[0x143] = flag.toByte() }
            val rom = RomImage(bytes)
            val platform = RomHeaderReader.read(rom).platform
            assertEquals(if (flag == 0) Platform.GB else Platform.GBC, platform)
            assertEquals(if (flag == 0) GB_HASH else GBC_HASH, observeRawHeader(rom, platform)?.rawHeaderSha256)
            assertEquals(80, observeRawHeader(rom, platform)?.byteCount)
            assertTrue(rom.slice(0, rom.size).contentEquals(bytes))
        }
    }

    @Test fun knownPlatformsCannotHashTruncatedOrPaddedWindows() {
        for (platform in listOf(Platform.GB, Platform.GBC, Platform.GBA)) {
            val endExclusive = if (platform == Platform.GBA) 0xC0 else 0x150
            for (size in listOf(0, 1, endExclusive - 1)) {
                assertNull("$platform size=$size", observeRawHeader(RomImage(ByteArray(size)), platform))
            }
        }
    }

    @Test fun unknownPlatformCannotBorrowAnyKnownHeaderWindow() {
        assertNull(observeRawHeader(RomImage(gbBytes()), Platform.UNKNOWN))
        assertNull(observeRawHeader(RomImage(ByteArray(0)), Platform.UNKNOWN))
    }

    private fun gbBytes() = ByteArray(512) { ((it * 73 + 19) and 255).toByte() }.also {
        "SYNTHETIC".toByteArray(Charsets.US_ASCII).copyInto(it, 0x134)
        it.fill(0, 0x13D, 0x144)
    }

    companion object {
        // Same independent Python hashlib vectors as the packaged CLI tests.
        private const val GB_HASH = "a2e47919e1b3b8e5b8562655908a1efd28fe0fb575b3ff33ddcf75b4f872d713"
        private const val GBC_HASH = "dfc267150d87105e958df10df65d1d9631b343faf946d775e7d7fce4ac8e1b69"
    }
}
