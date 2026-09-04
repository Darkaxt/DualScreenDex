package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import com.enrpau.dualscreendex.parser.text.KoreanGen2PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextToken
import com.enrpau.dualscreendex.parser.text.PokemonTextTokenDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizedNameValidationTest {
    @Test
    fun variableNameBoundariesFollowCompleteTokens() {
        val codec = PokemonTextCodec(
            id = "synthetic-variable-name",
            version = 1,
            language = LanguageTag.KOREAN,
            applicableGenerations = setOf(2),
            applicablePlatforms = setOf(Platform.GBC),
            terminator = 0x50,
            tokenDecoder = PokemonTextTokenDecoder { rom, offset, end ->
                when (rom.u8(offset)) {
                    0x01 -> if (offset + 1 < end) {
                        PokemonTextToken.Glyph("가", byteCount = 2)
                    } else {
                        PokemonTextToken.Invalid()
                    }
                    0x50 -> PokemonTextToken.Terminator()
                    else -> PokemonTextToken.Invalid()
                }
            },
        )
        val result = TableValidators.variableNames(
            RomImage(byteArrayOf(0x01, 0x50, 0x50, 0x01, 0x50, 0x50)),
            offset = 0, count = 2, codec = codec,
        )

        assertTrue(result.compatible)
        assertEquals(2, result.validRecords)
    }

    @Test
    fun invalidKoreanPairDoesNotShiftFollowingNames() {
        val result = TableValidators.variableNames(
            RomImage(byteArrayOf(0x01, 0x50, 0x50, 0x01, 0x01, 0x50)),
            offset = 0, count = 2, codec = KoreanGen2PokemonTextCodec.codec,
        )

        assertFalse(result.compatible)
        assertEquals(1, result.validRecords)
    }

    @Test
    fun doesNotGuessTheNextBoundaryAfterAnUnterminatedName() {
        val bytes = byteArrayOf(0x80.toByte(), 0x81.toByte()) +
            ByteArray(14) { if (it % 2 == 0) 0x80.toByte() else 0x50 }
        val result = TableValidators.variableNames(
            RomImage(bytes), offset = 0, count = 8, codec = PokemonTextCodec.gbEnglish, maximumWidth = 2,
        )

        assertFalse(result.compatible)
        assertEquals(0, result.validRecords)
    }

    @Test
    fun rejectsLostBoundaryEvenAfterEnoughValidNames() {
        val bytes = ByteArray(14) { if (it % 2 == 0) 0x80.toByte() else 0x50 } +
            byteArrayOf(0x80.toByte(), 0x81.toByte())
        val result = TableValidators.variableNames(
            RomImage(bytes), offset = 0, count = 8, codec = PokemonTextCodec.gbEnglish, maximumWidth = 2,
        )

        assertFalse(result.compatible)
        assertEquals(7, result.validRecords)
        assertTrue(result.reasons.any { it.contains("unterminated") })
    }

    @Test
    fun rejectsTruncatedKoreanLeadAndInvalidByteWindows() {
        val rom = RomImage(byteArrayOf(0x01, 0x01, 0x50))
        for ((offset, width) in listOf(0 to 1, -1 to 3, 4 to 3, 0 to 0, 0 to -1)) {
            val result = TableValidators.variableNames(
                rom, offset, count = 1, codec = KoreanGen2PokemonTextCodec.codec, maximumWidth = width,
            )
            assertFalse(result.compatible)
            assertEquals(0, result.validRecords)
        }
    }

    @Test
    fun retainsFullWidthJapaneseNameBeforeTerminatedName() {
        val result = TableValidators.inferFixedNameCount(
            RomImage(byteArrayOf(0x80.toByte(), 0x81.toByte(), 0x82.toByte(), 0x50)),
            offset = 0, width = 2, codec = JapanesePokemonTextCodecs.gen2,
            minimumCount = 2, maximumCount = 2,
        )

        assertEquals(2, result)
    }

    @Test
    fun retainsFullWidthKoreanNameBeforeTerminatedName() {
        val result = TableValidators.inferFixedNameCount(
            RomImage(byteArrayOf(0x01, 0x01, 0x01, 0x02, 0x01, 0x03, 0x50, 0x50)),
            offset = 0, width = 4, codec = KoreanGen2PokemonTextCodec.codec,
            minimumCount = 2, maximumCount = 2,
        )

        assertEquals(2, result)
    }
}
