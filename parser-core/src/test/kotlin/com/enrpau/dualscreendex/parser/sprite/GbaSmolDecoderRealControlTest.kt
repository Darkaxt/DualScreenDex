package com.enrpau.dualscreendex.parser.sprite

import com.enrpau.dualscreendex.parser.io.RomImage
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class GbaSmolDecoderRealControlTest {
    @Test
    fun battleTheaterTilemapsMatchTheirSourceProducts() {
        val romPath = configuredPath("DUALDEX_BATTLE_THEATER_ROM")
        val sourceRoot = configuredPath("DUALDEX_BATTLE_THEATER_SOURCE")
        val rom = RomImage(Files.readAllBytes(romPath))
        assertEquals(BATTLE_THEATER_SHA, rom.sha256)

        listOf(
            ExpectedTilemap(0xCBD458, "map.bin"),
            ExpectedTilemap(0xCB8CD0, "map_kanto.bin"),
            ExpectedTilemap(0xCB830C, "map_sevii_123.bin"),
            ExpectedTilemap(0xCB7CA0, "map_sevii_45.bin"),
            ExpectedTilemap(0xCB7130, "map_sevii_67.bin"),
        ).forEach { expected ->
            val source = Files.readAllBytes(
                sourceRoot.resolve("graphics/pokenav/region_map/${expected.fileName}"),
            )
            assertEquals(4096, source.size)
            assertEquals(4096, GbaRomCompression.decodedSizeAtOrNull(rom, expected.offset))
            assertArrayEquals(
                expected.fileName,
                source,
                GbaRomCompression.decodeAt(rom, expected.offset),
            )
        }
    }

    private fun configuredPath(name: String): Path {
        val configured = System.getenv(name)
        assumeTrue("set $name to run this real-ROM control", !configured.isNullOrBlank())
        return Path.of(requireNotNull(configured)).also {
            assumeTrue("configured path does not exist: $it", Files.exists(it))
        }
    }

    private data class ExpectedTilemap(val offset: Int, val fileName: String)

    private companion object {
        const val BATTLE_THEATER_SHA =
            "99c84950e2be2f887a84bdc32c741c92385bb4a54843d871a8876e9b47e1d59d"
    }
}
