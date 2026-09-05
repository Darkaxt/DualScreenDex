package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import com.enrpau.dualscreendex.parser.text.KoreanGen2PokemonTextCodec
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

    @Test fun japaneseVoicedKanaDoNotEnterTheWesternLowByteControlGate() {
        assertEquals(
            "ガギがぎ",
            decode(bytes(0x05, 0x06, 0x26, 0x27, 0x50), codec = JapanesePokemonTextCodecs.gen2),
        )
    }

    @Test fun japaneseFontGlyphsAreNotReplacedWithWesternPunctuation() {
        assertEquals(
            "ホマミム",
            decode(bytes(0x9c, 0x9d, 0x9e, 0x9f, 0x50), codec = JapanesePokemonTextCodecs.gen2),
        )
    }

    @Test fun japaneseStaticSubstitutionsRemainOwnedByTheExactCodec() {
        assertEquals(
            "ここは ポケモンパソコンわざマシントレーナーロケットだん",
            decode(bytes(0x37, 0x54, 0x5b, 0x5c, 0x5d, 0x5e, 0x50), codec = JapanesePokemonTextCodecs.gen2),
        )
        assertEquals(
            "アを イた！ウは エの オが カ",
            decode(
                bytes(0x80, 0x1f, 0x81, 0x22, 0x82, 0x24, 0x83, 0x25, 0x84, 0x4a, 0x85, 0x50),
                codec = JapanesePokemonTextCodecs.gen2,
            ),
        )
    }

    @Test fun koreanLeadBytesReachTheMultibyteCodec() {
        assertEquals(
            "가각ㄱ",
            decode(bytes(0x01, 0x01, 0x01, 0x02, 0x0b, 0x00, 0x50), codec = KoreanGen2PokemonTextCodec.codec),
        )
    }

    @Test fun koreanStaticSubstitutionsAreNotWesternFlowControls() {
        assertEquals(
            "포켓몬컴퓨터기술머신로켓단트레이너",
            decode(
                bytes(0x47, 0x49, 0x4a, 0x4b, 0x55, 0x50),
                codec = KoreanGen2PokemonTextCodec.codec,
            ),
        )
    }

    @Test fun koreanDoneStopsDisplayAtItsOwnControlAndStillNeedsARealTerminator() {
        val codec = KoreanGen2PokemonTextCodec.codec
        assertEquals("가", decode(bytes(0x01, 0x01, 0x5e, 0x01, 0x02, 0x50), codec = codec))
        assertNull(decode(bytes(0x01, 0x01, 0x5e), codec = codec))
        assertNull(decode(bytes(0x01, 0x01, 0x5e, 0x01, 0x50), codec = codec))
        assertEquals("가", decode(bytes(0x01, 0x01, 0x5e, 0x01, 0x50, 0x50), codec = codec))
    }

    @Test fun koreanStaticLineControlsUseTheKoreanDispatch() {
        for (control in listOf(0x1d, 0x1e, 0x34, 0x59, 0x5a)) {
            assertEquals(
                "가 각",
                decode(bytes(0x01, 0x01, control, 0x01, 0x02, 0x50), codec = KoreanGen2PokemonTextCodec.codec),
            )
        }
    }

    @Test fun koreanRuntimeNamesAndScrollControlsFailClosed() {
        for (control in listOf(0x4d, 0x4e, 0x4f, 0x51, 0x52, 0x53, 0x54, 0x56, 0x57, 0x5b, 0x5c, 0x5d, 0x5f)) {
            assertNull(
                decode(bytes(0x01, 0x01, control, 0x50), codec = KoreanGen2PokemonTextCodec.codec),
            )
        }
    }

    @Test fun japaneseDoneAndStaticLinesRetainBoundedDisplaySemantics() {
        val codec = JapanesePokemonTextCodecs.gen2
        assertEquals("アイ ウエ", decode(bytes(0x80, 0x81, 0x4e, 0x82, 0x83, 0x50), codec = codec))
        assertEquals("アイ", decode(bytes(0x80, 0x81, 0x57, 0x82, 0x83, 0x50), codec = codec))
        assertNull(decode(bytes(0x80, 0x81, 0x57), codec = codec))
        assertNull(decode(bytes(0x80, 0x81, 0x52, 0x50), codec = codec))
    }

    @Test fun malformedKoreanPairsDoNotPublishAValidPrefix() {
        val codec = KoreanGen2PokemonTextCodec.codec
        // The terminator-valued trail is an unmapped pair, not a string boundary.
        assertNull(decode(bytes(0x01, 0x01, 0x01, 0x50, 0x50), codec = codec))
        assertNull(decode(bytes(0x01, 0x01, 0x01), codec = codec))
    }

    @Test fun anUnregisteredEnglishDialectOwnsItsCollidingGlyphs() {
        val codec = collisionCodec()
        assertEquals("CUSTOM", decode(bytes(0xd4, 0x50), codec = codec))
        assertNull(decode(bytes(0xd4, 0x50), Gen2LandmarkNameEncoding.EXPANDED, codec))
    }

    @Test fun aTerminatorValuedParameterCannotEndAnExactCodecToken() {
        val codec = collisionCodec()
        assertEquals("AB", decode(bytes(0x24, 0x50, 0x50), codec = codec))
        assertNull(decode(bytes(0x24, 0x50), codec = codec))
    }

    @Test fun nativeNamesRejectLatinOnlyDigitsOnlyAndForeignScriptShapes() {
        assertNull(decode(bytes(0x80, 0x81, 0x82, 0x50), codec = KoreanGen2PokemonTextCodec.codec))
        assertNull(decode(bytes(0x6e, 0x6f, 0x50), codec = KoreanGen2PokemonTextCodec.codec))
        assertNull(decode(bytes(0xf6, 0xf7, 0xf8, 0x50), codec = JapanesePokemonTextCodecs.gen2))
    }

    @Test fun nativeNamesAllowShortAbbreviationsButRejectNativeLetterPadding() {
        val codec = KoreanGen2PokemonTextCodec.codec
        assertEquals("가각HP", decode(bytes(0x01, 0x01, 0x01, 0x02, 0x87, 0x8f, 0x50), codec = codec))
        assertNull(decode(bytes(0x01, 0x01, 0x80, 0x81, 0x82, 0x83, 0x50), codec = codec))
    }

    private fun collisionCodec() = PokemonTextCodec(
        id = "test-gen2-english-collisions",
        version = 1,
        language = LanguageTag.ENGLISH,
        applicableGenerations = setOf(2),
        applicablePlatforms = setOf(Platform.GBC),
        terminator = 0x50,
        tokenDecoder = PokemonTextTokenDecoder { rom, offset, endExclusive ->
            when {
                rom.u8(offset) == 0x24 && offset + 1 < endExclusive -> PokemonTextToken.Glyph("AB", 2)
                rom.u8(offset) == 0xd4 -> PokemonTextToken.Glyph("CUSTOM")
                rom.u8(offset) == 0x50 -> PokemonTextToken.Terminator()
                else -> PokemonTextToken.Invalid()
            }
        },
    )

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

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
