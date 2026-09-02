package com.enrpau.dualscreendex.parser.text

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test
    fun decodesDirectlyFromABoundedRomWindow() {
        val rom = RomImage(
            byteArrayOf(0x00, 0xBB.toByte(), 0xBC.toByte(), 0xFF.toByte(), 0xBD.toByte()),
        )

        val decoded = PokemonTextCodec.gbaEnglish.decodeDetailed(
            rom = rom,
            offset = 1,
            maximumBytes = 3,
            cancellation = ParserCancellationToken.NONE,
        )

        assertEquals("AB", decoded.text)
        assertTrue(decoded.terminated)
        assertEquals(3, decoded.consumedBytes)
        assertEquals(2, decoded.glyphUnits)
        assertEquals(0, decoded.controlUnits)
        assertEquals(0, decoded.invalidUnits)
    }

    @Test
    fun reportsControlsInvalidUnitsAndTruncation() {
        val decoded = PokemonTextCodec.gbaEnglish.decodeDetailed(
            rom = RomImage(byteArrayOf(0xBB.toByte(), 0xFE.toByte(), 0x7F, 0xBC.toByte(), 0xFF.toByte())),
            offset = 0,
            maximumBytes = 4,
            cancellation = ParserCancellationToken.NONE,
        )

        assertEquals("A B", decoded.text)
        assertTrue(!decoded.terminated)
        assertEquals(4, decoded.consumedBytes)
        assertEquals(2, decoded.glyphUnits)
        assertEquals(1, decoded.controlUnits)
        assertEquals(1, decoded.invalidUnits)
    }

    @Test
    fun measuresVariableWidthTokensByUnitsAndKinds() {
        val codec = variableWidthCodec()

        val decoded = codec.decodeDetailed(
            byteArrayOf(0x10, 0x11, 0x20, 0x30, 0x40, 0xFF.toByte()),
        )

        assertEquals("字 ?", decoded.text)
        assertTrue(decoded.terminated)
        assertEquals(6, decoded.consumedBytes)
        assertEquals(4, decoded.validBytes)
        assertEquals(5, decoded.contentBytes)
        assertEquals(3, decoded.validUnits)
        assertEquals(4, decoded.contentUnits)
        assertEquals(0.75, decoded.validRatio, 0.001)
        assertEquals(1, decoded.glyphUnits)
        assertEquals(1, decoded.whitespaceUnits)
        assertEquals(1, decoded.substitutionUnits)
        assertEquals(0, decoded.controlUnits)
        assertEquals(1, decoded.invalidUnits)
    }

    @Test
    fun rejectsATruncatedVariableWidthTokenWithoutCrossingTheWindow() {
        val decoded = variableWidthCodec().decodeDetailed(
            rom = RomImage(byteArrayOf(0x10, 0x11)),
            offset = 0,
            maximumBytes = 1,
            cancellation = ParserCancellationToken.NONE,
        )

        assertEquals("", decoded.text)
        assertEquals(1, decoded.consumedBytes)
        assertEquals(0, decoded.validUnits)
        assertEquals(1, decoded.contentUnits)
        assertEquals(1, decoded.invalidUnits)
    }

    @Test
    fun checksCancellationBetweenVariableWidthTokens() {
        var checks = 0

        assertThrows(ParserCancellationException::class.java) {
            variableWidthCodec().decodeDetailed(
                rom = RomImage(byteArrayOf(0x10, 0x11, 0x10, 0x11, 0xFF.toByte())),
                offset = 0,
                maximumBytes = 5,
                cancellation = ParserCancellationToken {
                    checks++
                    if (checks == 2) throw ParserCancellationException()
                },
            )
        }
        assertEquals(2, checks)
    }

    @Test
    fun stopsAtEofAndHonorsCancellation() {
        val eof = PokemonTextCodec.gbEnglish.decodeDetailed(
            rom = RomImage(byteArrayOf(0x80.toByte())),
            offset = 0,
            maximumBytes = 10,
            cancellation = ParserCancellationToken.NONE,
        )
        assertEquals("A", eof.text)
        assertEquals(1, eof.consumedBytes)
        assertTrue(!eof.terminated)

        assertThrows(ParserCancellationException::class.java) {
            PokemonTextCodec.gbEnglish.decodeDetailed(
                rom = RomImage(byteArrayOf(0x80.toByte(), 0x50)),
                offset = 0,
                maximumBytes = 2,
                cancellation = ParserCancellationToken { throw ParserCancellationException() },
            )
        }
    }

    private fun variableWidthCodec(): PokemonTextCodec = PokemonTextCodec(
        id = "test-variable",
        version = 1,
        language = LanguageTag.JAPANESE,
        applicableGenerations = setOf(2),
        applicablePlatforms = setOf(Platform.GBC),
        terminator = 0xFF,
        tokenDecoder = PokemonTextTokenDecoder { rom, offset, endExclusive ->
            when (rom.u8(offset)) {
                0x10 -> {
                    if (offset + 1 < endExclusive && rom.u8(offset + 1) == 0x11) {
                        PokemonTextToken.Glyph("字", byteCount = 2)
                    } else {
                        PokemonTextToken.Invalid(byteCount = 2)
                    }
                }
                0x20 -> PokemonTextToken.Whitespace()
                0x30 -> PokemonTextToken.Substitution("?")
                0xFF -> PokemonTextToken.Terminator()
                else -> PokemonTextToken.Invalid()
            }
        },
    )
}
