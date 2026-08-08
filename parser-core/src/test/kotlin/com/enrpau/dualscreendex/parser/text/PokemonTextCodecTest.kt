package com.enrpau.dualscreendex.parser.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PokemonTextCodecTest {
    @Test
    fun decodesGbaTerminatedText() {
        val decoded = PokemonTextCodec.gbaEnglish.decodeDetailed(
            byteArrayOf(0xBB.toByte(), 0xBC.toByte(), 0xBD.toByte(), 0xFF.toByte()),
        )
        assertEquals("ABC", decoded.text)
        assertTrue(decoded.terminated)
        assertEquals(1.0, decoded.validRatio, 0.001)
    }

    @Test
    fun decodesGbTerminatedText() {
        assertEquals(
            "Ab0",
            PokemonTextCodec.gbEnglish.decode(
                byteArrayOf(0x80.toByte(), 0xA1.toByte(), 0xF6.toByte(), 0x50.toByte()),
            ),
        )
    }
}
