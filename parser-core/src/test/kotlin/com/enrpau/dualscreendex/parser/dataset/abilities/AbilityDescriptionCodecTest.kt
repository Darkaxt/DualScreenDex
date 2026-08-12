package com.enrpau.dualscreendex.parser.dataset.abilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AbilityDescriptionCodecTest {
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

        val decoded = AbilityDescriptionCodec().decode(abilitySession(bytes), layout)
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

        val decoded = AbilityDescriptionCodec().decode(abilitySession(bytes), layout)
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

        val separateRows = (AbilityDescriptionCodec().decode(abilitySession(bytes), separate)
            as AbilityDescriptionTableOutcome.Decoded).rows
        val embeddedRows = (AbilityDescriptionCodec().decode(abilitySession(bytes), embedded)
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

        val decoded = AbilityDescriptionCodec().decode(abilitySession(bytes), layout)
            as AbilityDescriptionTableOutcome.Decoded

        assertTrue(decoded.rows[2] is AbilityDescriptionRowOutcome.Malformed)
        assertTrue(decoded.rows[3] is AbilityDescriptionRowOutcome.Decoded)
        assertTrue(decoded.rows[4] is AbilityDescriptionRowOutcome.Decoded)
    }
}
