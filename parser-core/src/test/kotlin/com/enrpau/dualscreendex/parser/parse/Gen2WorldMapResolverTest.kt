package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen2WorldMapResolverTest {
    @Test fun requiredDynamicSpecialLandmarkFailsClosed() {
        val source = sourceBytes("DUALDEX_POKEGOLD_ROM")
        val groupPointer = u16le(source, MAP_GROUP_POINTERS + (JOHTO_GROUP - 1) * 2)
        val groupRoot = MAP_DATA_BANK * BANK_BYTES + groupPointer - BANK_BYTES
        val header = groupRoot + (JOHTO_MAP - 1) * MAP_HEADER_BYTES
        source[header + MAP_LOCATION_FIELD] = SPECIAL_LANDMARK.toByte()
        val mutated = RomImage(source)

        val result = Gen2WorldMapResolver.resolve(
            RomAnalysisSession(mutated, RomHeaderReader.read(mutated)),
            setOf(JOHTO_BASE_ID, KANTO_BASE_ID),
            PokemonTextCodec.gbEnglish,
        )

        assertTrue("expected unavailable join, got $result", result is WorldMapResolution.Unavailable)
        assertEquals("landmark-join", (result as WorldMapResolution.Unavailable).stage)
    }

    private fun sourceBytes(env: String): ByteArray {
        val configured = System.getenv(env)
        assumeTrue("set $env to run this source-derived rejection control", !configured.isNullOrBlank())
        return Files.readAllBytes(Path.of(requireNotNull(configured)))
    }

    private fun u16le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private companion object {
        const val BANK_BYTES = 0x4000
        const val MAP_DATA_BANK = 0x25
        const val MAP_GROUP_POINTERS = 0x940ed
        const val MAP_HEADER_BYTES = 9
        const val MAP_LOCATION_FIELD = 5
        const val SPECIAL_LANDMARK = 0
        const val JOHTO_GROUP = 1
        const val JOHTO_MAP = 12
        const val JOHTO_BASE_ID = (JOHTO_GROUP shl 8) or JOHTO_MAP
        const val KANTO_BASE_ID = 843
    }
}
