package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationSource
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
    private val locatorCodec = PokemonTextCodec(
        id = "synthetic-locator-boundaries",
        version = 1,
        language = LanguageTag.KOREAN,
        applicableGenerations = setOf(2),
        applicablePlatforms = setOf(Platform.GBC),
        terminator = 0x50,
        tokenDecoder = PokemonTextTokenDecoder { rom, offset, end ->
            when (rom.u8(offset)) {
                0x01 -> if (offset + 1 < end) PokemonTextToken.Glyph("가", 2) else PokemonTextToken.Invalid()
                0x02 -> PokemonTextToken.Glyph("나")
                0x50 -> PokemonTextToken.Terminator()
                else -> PokemonTextToken.Invalid()
            }
        },
    )

    @Test
    fun locatorConsumesTerminatorValuedTrailsBeforeFollowingNames() {
        assertEquals(
            1,
            TableValidators.locateVariableNameSequenceNear(
                RomImage(byteArrayOf(0x50, 0x01, 0x50, 0x50, 0x02, 0x50)),
                approximateOffset = 1, codec = locatorCodec, expectedNames = listOf("가", "나"),
                recordStarts = sequenceOf(1), searchRadius = 8,
            ),
        )
    }

    @Test
    fun locatorDoesNotPromoteSuffixAfterTerminatorValuedTrail() {
        assertEquals(
            null,
            TableValidators.locateVariableNameSequenceNear(
                RomImage(byteArrayOf(0x50, 0x01, 0x50, 0x02, 0x50)),
                approximateOffset = 1, codec = locatorCodec, expectedNames = listOf("나"),
                recordStarts = sequenceOf(1), searchRadius = 8,
            ),
        )
    }

    @Test
    fun locatorAcceptsEstablishedStartWithoutPrecedingTerminator() {
        assertEquals(
            1,
            TableValidators.locateVariableNameSequenceNear(
                RomImage(byteArrayOf(0x00, 0x02, 0x50)),
                approximateOffset = 1, codec = locatorCodec, expectedNames = listOf("나"),
                recordStarts = sequenceOf(1), searchRadius = 8,
            ),
        )
    }

    @Test
    fun locatorRejectsInvalidPrefixHiddenByDecodedText() {
        assertEquals(
            null,
            TableValidators.locateVariableNameSequenceNear(
                RomImage(byteArrayOf(0x50, 0x00, 0x02, 0x50)),
                approximateOffset = 1, codec = locatorCodec, expectedNames = listOf("나"),
                recordStarts = sequenceOf(1), searchRadius = 8,
            ),
        )
    }

    @Test
    fun locatorDoesNotDiscoverUnprovidedStartsEvenAtZeroOrAfterARealTerminator() {
        val rom = RomImage(byteArrayOf(0x02, 0x50, 0x02, 0x50))
        assertEquals(null, TableValidators.locateVariableNameSequenceNear(
            rom, 0, locatorCodec, listOf("나"), emptySequence(),
        ))
        assertEquals(2, TableValidators.locateVariableNameSequenceNear(
            rom, 0, locatorCodec, listOf("나"), sequenceOf(2),
        ))
    }

    @Test
    fun locatorRejectsTruncationWithoutSearchingForANewBoundary() {
        val rom = RomImage(byteArrayOf(0x01, 0x50, 0x50, 0x02, 0x50))
        for (width in listOf(1, 2)) {
            assertEquals(null, TableValidators.locateVariableNameSequenceNear(
                rom, 0, locatorCodec, listOf("가", "나"), sequenceOf(0), maximumWidth = width,
            ))
        }
        assertEquals(0, TableValidators.locateVariableNameSequenceNear(
            rom, 0, locatorCodec, listOf("가", "나"), sequenceOf(0), maximumWidth = 3,
        ))
        assertEquals(null, TableValidators.locateVariableNameSequenceNear(
            RomImage(byteArrayOf(0x02)), 0, locatorCodec, listOf("나"), sequenceOf(0),
        ))
        assertEquals(null, TableValidators.locateVariableNameSequenceNear(
            RomImage(byteArrayOf(0x02, 0x50)), 0, locatorCodec, listOf("나", "나"), sequenceOf(0),
        ))
    }

    @Test
    fun locatorRejectsInvalidKoreanPairRatherThanMatchingItsVisibleSuffix() {
        val codec = KoreanGen2PokemonTextCodec.codec
        val name = codec.decode(byteArrayOf(0x01, 0x01, 0x50))
        assertTrue(name.isNotBlank())
        assertEquals(null, TableValidators.locateVariableNameSequenceNear(
            RomImage(byteArrayOf(0x01, 0x50, 0x01, 0x01, 0x50)),
            0, codec, listOf(name), sequenceOf(0),
        ))
        assertEquals(0, TableValidators.locateVariableNameSequenceNear(
            RomImage(byteArrayOf(0x01, 0x01, 0x50)), 0, codec, listOf(name), sequenceOf(0),
        ))
    }

    @Test
    fun locatorRejectsInvalidArgumentsAndOutOfRangeStarts() {
        val rom = RomImage(byteArrayOf(0x02, 0x50))
        for (width in listOf(0, -1)) {
            assertEquals(null, TableValidators.locateVariableNameSequenceNear(
                rom, 0, locatorCodec, listOf("나"), sequenceOf(0), maximumWidth = width,
            ))
        }
        assertEquals(null, TableValidators.locateVariableNameSequenceNear(
            rom, 0, locatorCodec, listOf("나"), sequenceOf(0), searchRadius = -1,
        ))
        assertEquals(null, TableValidators.locateVariableNameSequenceNear(
            rom, 0, locatorCodec, emptyList(), sequenceOf(0),
        ))
        assertEquals(null, TableValidators.locateVariableNameSequenceNear(
            rom, 0, locatorCodec, listOf(" "), sequenceOf(0),
        ))
        assertEquals(null, TableValidators.locateVariableNameSequenceNear(
            rom, 0, locatorCodec, listOf("나"), sequenceOf(-1, rom.size, Int.MAX_VALUE),
        ))
        assertEquals(null, TableValidators.locateVariableNameSequenceNear(
            RomImage(byteArrayOf()), 0, locatorCodec, listOf("나"), sequenceOf(0),
        ))
    }

    @Test
    fun locatorRanksOnlySuppliedStartsWithinAnOverflowSafeRadius() {
        val rom = RomImage(byteArrayOf(0x02, 0x50, 0x02, 0x50, 0x02, 0x50))
        assertEquals(0, TableValidators.locateVariableNameSequenceNear(
            rom, 1, locatorCodec, listOf("나"), sequenceOf(4, 2, 0), searchRadius = 1,
        ))
        assertEquals(2, TableValidators.locateVariableNameSequenceNear(
            rom, 2, locatorCodec, listOf("나"), sequenceOf(0, 4, 2), searchRadius = 0,
        ))
        assertEquals(null, TableValidators.locateVariableNameSequenceNear(
            rom, 1, locatorCodec, listOf("나"), sequenceOf(0, 2), searchRadius = 0,
        ))
        assertEquals(4, TableValidators.locateVariableNameSequenceNear(
            rom, Int.MAX_VALUE, locatorCodec, listOf("나"), sequenceOf(0, 4, 2), searchRadius = Int.MAX_VALUE,
        ))
        assertEquals(null, TableValidators.locateVariableNameSequenceNear(
            rom, Int.MIN_VALUE, locatorCodec, listOf("나"), sequenceOf(0), searchRadius = Int.MAX_VALUE,
        ))
    }

    @Test(expected = ParserCancellationException::class)
    fun locatorChecksCancellationBeforeEmptyCandidateDiscovery() {
        val cancellation = ParserCancellationSource().also { it.cancel() }
        TableValidators.locateVariableNameSequenceNear(
            RomImage(byteArrayOf()), 0, locatorCodec, emptyList(), emptySequence(),
            cancellation = cancellation.token,
        )
    }

    @Test
    fun locatorChecksCancellationBetweenExactCodecTokens() {
        val cancellation = ParserCancellationSource()
        var decodedTokens = 0
        val codec = PokemonTextCodec(
            id = "cancelled-locator", version = 1, language = LanguageTag.KOREAN,
            applicableGenerations = setOf(2), applicablePlatforms = setOf(Platform.GBC), terminator = 0x50,
            tokenDecoder = PokemonTextTokenDecoder { _, _, _ ->
                decodedTokens++
                cancellation.cancel()
                PokemonTextToken.Glyph("나")
            },
        )
        try {
            TableValidators.locateVariableNameSequenceNear(
                RomImage(byteArrayOf(0x02, 0x50)), 0, codec, listOf("나"), sequenceOf(0),
                cancellation = cancellation.token,
            )
            throw AssertionError("expected cancellation before the next token")
        } catch (_: ParserCancellationException) {
            assertEquals(1, decodedTokens)
        }
    }

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
