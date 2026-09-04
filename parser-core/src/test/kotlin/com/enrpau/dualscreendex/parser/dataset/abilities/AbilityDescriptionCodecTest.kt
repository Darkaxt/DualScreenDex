package com.enrpau.dualscreendex.parser.dataset.abilities

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextTokenDecoder
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AbilityDescriptionCodecTest {
    @Test
    fun decodesJapaneseRawByteProseWithoutSpaces() {
        val layout = AbilityDescriptionTableLayout(0x100, 2)
        val bytes = ByteArray(0x800)
        putGbaPointer(bytes, 0x100, 0x400)
        // Gen III Japanese: あいてのこうげきをふせぐ (blocks the opponent's attack).
        byteArrayOf(0x01, 0x02, 0x13, 0x19, 0x0A, 0x03, 0x3A, 0x07, 0x2D, 0x1C, 0x0E, 0x39, 0xFF.toByte())
            .copyInto(bytes, 0x400)

        val decoded = AbilityDescriptionCodec(JapanesePokemonTextCodecs.gen3Later).decode(abilitySession(bytes), layout)
            as AbilityDescriptionTableOutcome.Decoded

        assertEquals(AbilityDescriptionRowOutcome.Decoded(0, "あいてのこうげきをふせぐ"), decoded.rows[0])
    }

    @Test
    fun cancellationAtEntryPrecedesInvalidExtentRejection() {
        val failure = ParserCancellationException()
        val session = abilitySession(ByteArray(0x100), cancellation = ParserCancellationToken { throw failure })

        assertSame(failure, assertThrows(ParserCancellationException::class.java) {
            AbilityDescriptionCodec(PokemonTextCodec.gbaEnglish).decode(session, AbilityDescriptionTableLayout(0x200, 2))
        })
    }

    @Test
    fun cancellationIsCheckedForEveryMalformedPointerRow() {
        val failure = ParserCancellationException()
        var checks = 0
        val session = abilitySession(ByteArray(0x800), cancellation = ParserCancellationToken {
            if (++checks == 3) throw failure
        })

        assertSame(failure, assertThrows(ParserCancellationException::class.java) {
            AbilityDescriptionCodec(PokemonTextCodec.gbaEnglish).decode(session, AbilityDescriptionTableLayout(0x100, 3))
        })
        assertEquals(3, checks)
    }

    @Test
    fun cancellationDuringTextDecodingPropagatesUnchanged() {
        val layout = AbilityDescriptionTableLayout(0x100, 2)
        val bytes = ByteArray(0x800)
        putAbilityDescriptions(bytes, layout, listOf("FIRST ABILITY EFFECT", "SECOND ABILITY EFFECT"), 0x400)
        val failure = ParserCancellationException()
        var checks = 0
        val session = abilitySession(bytes, cancellation = ParserCancellationToken {
            if (++checks == 6) throw failure
        })

        assertSame(failure, assertThrows(ParserCancellationException::class.java) {
            AbilityDescriptionCodec(PokemonTextCodec.gbaEnglish).decode(session, layout)
        })
        assertEquals(6, checks)
    }

    @Test
    fun controlParameterTerminatorDoesNotCutOffJapaneseProse() {
        val layout = AbilityDescriptionTableLayout(0x100, 2)
        val bytes = ByteArray(0x800)
        putGbaPointer(bytes, 0x100, 0x400)
        // FC 01 FF is a complete color control, not an end-of-text marker.
        byteArrayOf(0x01, 0x02, 0x13, 0x19, 0xFC.toByte(), 0x01, 0xFF.toByte(),
            0x0A, 0x03, 0x3A, 0x07, 0x2D, 0x1C, 0x0E, 0x39, 0xFF.toByte())
            .copyInto(bytes, 0x400)

        val decoded = AbilityDescriptionCodec(JapanesePokemonTextCodecs.gen3Later).decode(abilitySession(bytes), layout)
            as AbilityDescriptionTableOutcome.Decoded

        assertEquals(AbilityDescriptionRowOutcome.Decoded(0, "あいての こうげきをふせぐ"), decoded.rows[0])
    }

    @Test
    fun unterminatedAndBoundTruncatedRowsDoNotDiscardLaterProse() {
        val layout = AbilityDescriptionTableLayout(0x100, 4)
        val bytes = ByteArray(0x1000)
        putGbaPointer(bytes, 0x100, 0xFFE)
        bytes[0xFFE] = 0xBB.toByte()
        bytes[0xFFF] = 0xBB.toByte()
        putGbaPointer(bytes, 0x104, 0x400)
        putGbaText(bytes, 0x400, "ABILITY EFFECT ".repeat(16))
        putGbaPointer(bytes, 0x108, 0x600)
        // The terminator at byte 193 is outside the 192-byte decode budget.
        putGbaText(bytes, 0x600, "ABILITY EFFECT ".repeat(13).take(192))
        putGbaPointer(bytes, 0x10C, 0x800)
        putGbaText(bytes, 0x800, "LATER ABILITY EFFECT")

        val decoded = AbilityDescriptionCodec(PokemonTextCodec.gbaEnglish).decode(abilitySession(bytes), layout)
            as AbilityDescriptionTableOutcome.Decoded

        assertTrue(decoded.rows.take(3).all { it is AbilityDescriptionRowOutcome.Malformed })
        assertEquals(AbilityDescriptionRowOutcome.Decoded(3, "LATER ABILITY EFFECT"), decoded.rows[3])
    }

    @Test
    fun invalidOnlyTextIsMalformedRatherThanMissingProse() {
        val layout = AbilityDescriptionTableLayout(0x100, 2)
        val bytes = ByteArray(0x800)
        putGbaPointer(bytes, 0x100, 0x400)
        byteArrayOf(0xF8.toByte(), 0xFF.toByte()).copyInto(bytes, 0x400)
        putGbaPointer(bytes, 0x104, 0x500)
        putGbaText(bytes, 0x500, "LATER ABILITY EFFECT")

        val decoded = AbilityDescriptionCodec(PokemonTextCodec.gbaEnglish).decode(abilitySession(bytes), layout)
            as AbilityDescriptionTableOutcome.Decoded

        assertTrue(decoded.rows[0] is AbilityDescriptionRowOutcome.Malformed)
        assertEquals(AbilityDescriptionRowOutcome.Decoded(1, "LATER ABILITY EFFECT"), decoded.rows[1])
    }

    @Test
    fun truncatedControlAndParameterOnlyTerminatorFailClosedAtRomEnd() {
        for (suffix in listOf(byteArrayOf(0xFC.toByte()), byteArrayOf(0xFC.toByte(), 0x01, 0xFF.toByte()))) {
            val layout = AbilityDescriptionTableLayout(0x100, 2)
            val bytes = ByteArray(0x800)
            val prose = byteArrayOf(0x01, 0x02, 0x13, 0x19, 0x0A, 0x03, 0x3A, 0x07, 0x2D, 0x1C, 0x0E, 0x39)
            val target = bytes.size - prose.size - suffix.size
            putGbaPointer(bytes, 0x100, target)
            prose.copyInto(bytes, target)
            suffix.copyInto(bytes, target + prose.size)
            putGbaPointer(bytes, 0x104, 0x400)
            (prose + 0xFF.toByte()).copyInto(bytes, 0x400)

            val decoded = AbilityDescriptionCodec(JapanesePokemonTextCodecs.gen3Later)
                .decode(abilitySession(bytes), layout) as AbilityDescriptionTableOutcome.Decoded

            assertTrue(decoded.rows[0] is AbilityDescriptionRowOutcome.Malformed)
            assertEquals(AbilityDescriptionRowOutcome.Decoded(1, "あいてのこうげきをふせぐ"), decoded.rows[1])
        }
    }

    @Test
    fun optionalDecoderFailureIsIsolatedToItsRow() {
        val layout = AbilityDescriptionTableLayout(0x100, 2)
        val bytes = ByteArray(0x800)
        putAbilityDescriptions(bytes, layout, listOf("FIRST ABILITY EFFECT", "SECOND ABILITY EFFECT"), 0x400)
        val codec = failingTextCodec(IllegalArgumentException("malformed fixture token"))

        val decoded = AbilityDescriptionCodec(codec).decode(abilitySession(bytes), layout)
            as AbilityDescriptionTableOutcome.Decoded

        assertTrue(decoded.rows[0] is AbilityDescriptionRowOutcome.Malformed)
        assertEquals(AbilityDescriptionRowOutcome.Decoded(1, "SECOND ABILITY EFFECT"), decoded.rows[1])
    }

    @Test
    fun decoderCancellationExceptionIsNeverConvertedToMalformedText() {
        val layout = AbilityDescriptionTableLayout(0x100, 2)
        val bytes = ByteArray(0x800)
        putAbilityDescriptions(bytes, layout, listOf("FIRST ABILITY EFFECT", "SECOND ABILITY EFFECT"), 0x400)
        val failure = CancellationException("fixture token cancelled")

        assertSame(failure, assertThrows(CancellationException::class.java) {
            AbilityDescriptionCodec(failingTextCodec(failure)).decode(abilitySession(bytes), layout)
        })
    }

    private fun failingTextCodec(failure: Exception) = PokemonTextCodec(
        id = "ability-description-failure-fixture",
        version = 1,
        language = LanguageTag.ENGLISH,
        applicableGenerations = setOf(3),
        applicablePlatforms = setOf(Platform.GBA),
        terminator = 0xFF,
        tokenDecoder = PokemonTextTokenDecoder { rom, offset, end ->
            if (offset == 0x400) throw failure
            PokemonTextCodec.gbaEnglish.decodeToken(rom, offset, end)
        },
    )

    @Test
    fun blankNoneDescriptionIsStructuralMissingProse() {
        val layout = AbilityDescriptionTableLayout(0x100, 3)
        val bytes = ByteArray(0x1000)
        putAbilityDescriptions(
            bytes,
            layout,
            listOf("", "FIRST ABILITY EFFECT", "SECOND ABILITY EFFECT"),
            textBase = 0x400,
        )

        val decoded = AbilityDescriptionCodec(PokemonTextCodec.gbaEnglish).decode(abilitySession(bytes), layout)
            as AbilityDescriptionTableOutcome.Decoded

        assertEquals(AbilityDescriptionRowOutcome.MissingProse(0, ""), decoded.rows[0])
    }

    @Test
    fun cloverShortPlaceholderIsMissingProseAndDoesNotTerminateTheTail() {
        val count = 221
        val layout = AbilityDescriptionTableLayout(0x100, count.toLong())
        val descriptions = MutableList<String?>(count) { index ->
            if (index == 0) "NO SPECIAL ABILITY" else "ABILITY EFFECT NUMBER $index"
        }
        descriptions[218] = "-"
        val bytes = ByteArray(0x10000)
        putAbilityDescriptions(bytes, layout, descriptions, textBase = 0x1000)

        val decoded = AbilityDescriptionCodec(PokemonTextCodec.gbaEnglish).decode(abilitySession(bytes), layout)
            as AbilityDescriptionTableOutcome.Decoded

        assertEquals(AbilityDescriptionRowOutcome.MissingProse(218, "-"), decoded.rows[218])
        assertTrue(decoded.rows[219] is AbilityDescriptionRowOutcome.Decoded)
        assertTrue(decoded.rows[220] is AbilityDescriptionRowOutcome.Decoded)
    }

    @Test
    fun decodesSeparateAndEmbeddedPointerLayoutsWithTheSameRowSemantics() {
        val separate = AbilityDescriptionTableLayout(0x100, 4)
        val embedded = AbilityDescriptionTableLayout(0x300, 4, recordStride = 28, pointerOffset = 20)
        val descriptions = listOf(
            "NO SPECIAL ABILITY",
            "FIRST ABILITY EFFECT",
            "SECOND ABILITY EFFECT",
            "THIRD ABILITY EFFECT",
        )
        val bytes = ByteArray(0x2000)
        putAbilityDescriptions(bytes, separate, descriptions, 0x800)
        putAbilityDescriptions(bytes, embedded, descriptions, 0x1000)

        val separateRows = (AbilityDescriptionCodec(PokemonTextCodec.gbaEnglish).decode(abilitySession(bytes), separate)
            as AbilityDescriptionTableOutcome.Decoded).rows
        val embeddedRows = (AbilityDescriptionCodec(PokemonTextCodec.gbaEnglish).decode(abilitySession(bytes), embedded)
            as AbilityDescriptionTableOutcome.Decoded).rows

        assertEquals(separateRows, embeddedRows)
    }

    @Test
    fun malformedPointersRemainRowIndexedWithoutStoppingLaterDescriptions() {
        val layout = AbilityDescriptionTableLayout(0x100, 5)
        val descriptions = listOf(
            "NO SPECIAL ABILITY",
            "FIRST ABILITY EFFECT",
            null,
            "THIRD ABILITY EFFECT",
            "FOURTH ABILITY EFFECT",
        )
        val bytes = ByteArray(0x2000)
        putAbilityDescriptions(bytes, layout, descriptions, 0x800)

        val decoded = AbilityDescriptionCodec(PokemonTextCodec.gbaEnglish).decode(abilitySession(bytes), layout)
            as AbilityDescriptionTableOutcome.Decoded

        assertTrue(decoded.rows[2] is AbilityDescriptionRowOutcome.Malformed)
        assertTrue(decoded.rows[3] is AbilityDescriptionRowOutcome.Decoded)
        assertTrue(decoded.rows[4] is AbilityDescriptionRowOutcome.Decoded)
    }
}
