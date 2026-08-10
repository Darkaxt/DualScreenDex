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
    fun decodesGbaSpeciesNameCharacters() {
        assertEquals(
            "Flabébé ♂♀",
            PokemonTextCodec.gbaEnglish.decode(
                byteArrayOf(
                    0xC0.toByte(), 0xE0.toByte(), 0xD5.toByte(), 0xD6.toByte(), 0x1B,
                    0xD6.toByte(), 0x1B, 0x00, 0xB5.toByte(), 0xB6.toByte(), 0xFF.toByte(),
                ),
            ),
        )
    }

    @Test
    fun turnsGameTextLineControlsIntoWordBoundaries() {
        assertEquals(
            "that has power. It wins",
            PokemonTextCodec.gbaEnglish.decode(
                byteArrayOf(
                    0xE8.toByte(), 0xDC.toByte(), 0xD5.toByte(), 0xE8.toByte(), 0xFE.toByte(),
                    0xDC.toByte(), 0xD5.toByte(), 0xE7.toByte(), 0x00, 0xE4.toByte(), 0xE3.toByte(), 0xEB.toByte(), 0xD9.toByte(), 0xE6.toByte(), 0xAD.toByte(),
                    0xFE.toByte(), 0xC3.toByte(), 0xE8.toByte(), 0x00, 0xEB.toByte(), 0xDD.toByte(), 0xE2.toByte(), 0xE7.toByte(), 0xFF.toByte(),
                ),
            ),
        )
        assertEquals(
            "Ab cd",
            PokemonTextCodec.gbEnglish.decode(
                byteArrayOf(0x80.toByte(), 0xA1.toByte(), 0x4E, 0xA2.toByte(), 0xA3.toByte(), 0x50),
            ),
        )
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
