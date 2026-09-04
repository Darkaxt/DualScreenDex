package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextToken
import com.enrpau.dualscreendex.parser.text.PokemonTextTokenDecoder
import com.enrpau.dualscreendex.parser.text.WesternPokemonTextCodecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen2LandmarkNameCodecTest {
    @Test fun doneStopsDisplayButStillRequiresTheCopiedStringTerminator() {
        assertEquals(
            "PEEL",
            decode(byteArrayOf(0x8f.toByte(), 0x84.toByte(), 0x84.toByte(), 0x8b.toByte(), 0x57, 0x99.toByte(), 0x50)),
        )
        assertNull(
            decode(byteArrayOf(0x8f.toByte(), 0x84.toByte(), 0x84.toByte(), 0x8b.toByte(), 0x57)),
        )
    }

    @Test fun nullControlFailsClosedInsteadOfPrefixTruncating() {
        assertNull(
            decode(
                byteArrayOf(0x8f.toByte(), 0x84.toByte(), 0x84.toByte(), 0x8b.toByte(), 0x00, 0x57, 0x50),
            ),
        )
    }

    @Test fun runtimeTextFlowControlFailsClosed() {
        assertNull(
            decode(byteArrayOf(0x8f.toByte(), 0x84.toByte(), 0x4c, 0x84.toByte(), 0x8b.toByte(), 0x50)),
        )
    }

    @Test fun staticTownMapLineBreakControlsNormalizeToSpaces() {
        assertEquals(
            "LAKE RAGE",
            decode(
                byteArrayOf(
                    0x8b.toByte(), 0x80.toByte(), 0x8a.toByte(), 0x84.toByte(),
                    0x1f,
                    0x91.toByte(), 0x80.toByte(), 0x86.toByte(), 0x84.toByte(),
                    0x50,
                ),
            ),
        )
    }

    @Test fun expandedDialectDecodesShiftedDigitsAndPunctuation() {
        assertEquals(
            "FUKUHARA №.4",
            decode(
                byteArrayOf(
                    0x85.toByte(), 0x94.toByte(), 0x8a.toByte(), 0x94.toByte(),
                    0x87.toByte(), 0x80.toByte(), 0x91.toByte(), 0x80.toByte(), 0x7f,
                    0xcd.toByte(), 0xd8.toByte(), 0xea.toByte(), 0x50,
                ),
                Gen2LandmarkNameEncoding.EXPANDED,
            ),
        )
    }

    @Test fun englishContractionGlyphsAreDecodedInFull() {
        assertEquals(
            "DIGLETT's CAVE",
            decode(
                byteArrayOf(
                    0x83.toByte(), 0x88.toByte(), 0x86.toByte(), 0x8b.toByte(), 0x84.toByte(), 0x93.toByte(), 0x93.toByte(),
                    0xd4.toByte(), 0x1f,
                    0x82.toByte(), 0x80.toByte(), 0x95.toByte(), 0x84.toByte(), 0x50,
                ),
            ),
        )
    }

    @Test fun standardDialectDefersLocalizedGlyphsToSelectedCodec() {
        assertEquals(
            "ìíñòóúº",
            decode(
                byteArrayOf(
                    0xd0.toByte(), 0xd1.toByte(), 0xd2.toByte(), 0xd3.toByte(),
                    0xd4.toByte(), 0xd5.toByte(), 0xd6.toByte(), 0x50,
                ),
                codec = WesternPokemonTextCodecs.gen2Spanish,
            ),
        )
        assertEquals(
            "c'",
            decode(
                byteArrayOf(0xd4.toByte(), 0x50),
                codec = WesternPokemonTextCodecs.gen2French,
            ),
        )
    }

    @Test fun expandedEnglishDialectFailsClosedForLocalizedCodecs() {
        val bytes = byteArrayOf(0xea.toByte(), 0x50)

        assertEquals("é", decode(bytes, codec = WesternPokemonTextCodecs.gen2French))
        assertNull(
            decode(
                bytes,
                encoding = Gen2LandmarkNameEncoding.EXPANDED,
                codec = WesternPokemonTextCodecs.gen2French,
            ),
        )
    }

    @Test fun technicalMachineSubstitutionFollowsTheSelectedWesternLanguage() {
        assertEquals(
            "CT",
            decode(byteArrayOf(0x5c, 0x50), codec = WesternPokemonTextCodecs.gen2French),
        )
        assertEquals(
            "TM",
            decode(byteArrayOf(0x5c, 0x50), codec = WesternPokemonTextCodecs.gen2German),
        )
        assertEquals(
            "MT",
            decode(byteArrayOf(0x5c, 0x50), codec = WesternPokemonTextCodecs.gen2Italian),
        )
        assertEquals(
            "MT",
            decode(byteArrayOf(0x5c, 0x50), codec = WesternPokemonTextCodecs.gen2Spanish),
        )
    }

    @Test fun englishOnlyPlaceStringSubstitutionsFailClosedForLocalizedCodecs() {
        assertNull(
            decode(
                byteArrayOf(0x5d, 0x50),
                codec = WesternPokemonTextCodecs.gen2French,
            ),
        )
        assertNull(
            decode(
                byteArrayOf(0x5e, 0x50),
                codec = WesternPokemonTextCodecs.gen2German,
            ),
        )
    }

    @Test fun variableWidthTokensConsumeEveryByteBeforeFindingTheRealTerminator() {
        val codec = variableWidthCodec()

        assertEquals("AB", decode(byteArrayOf(0x70, 0x50, 0x50), codec = codec))
        assertNull(decode(byteArrayOf(0x70, 0x50), codec = codec))
    }

    private fun decode(
        bytes: ByteArray,
        encoding: Gen2LandmarkNameEncoding = Gen2LandmarkNameEncoding.STANDARD,
        codec: PokemonTextCodec = PokemonTextCodec.gbEnglish,
    ): String? = Gen2LandmarkNameCodec.decode(bytes, encoding, codec)

    private fun variableWidthCodec() = PokemonTextCodec(
        id = "test-gb-variable",
        version = 1,
        language = LanguageTag.ENGLISH,
        applicableGenerations = setOf(2),
        applicablePlatforms = setOf(Platform.GBC),
        terminator = 0x50,
        tokenDecoder = PokemonTextTokenDecoder { rom, offset, endExclusive ->
            when {
                rom.u8(offset) == 0x70 && offset + 1 < endExclusive -> PokemonTextToken.Glyph("AB", 2)
                rom.u8(offset) == 0x50 -> PokemonTextToken.Terminator()
                else -> PokemonTextToken.Invalid()
            }
        },
    )
}
