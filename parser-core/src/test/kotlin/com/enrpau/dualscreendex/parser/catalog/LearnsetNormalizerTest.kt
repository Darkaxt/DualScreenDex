package com.enrpau.dualscreendex.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class LearnsetNormalizerTest {
    @Test
    fun groupsInitialAndLaterEntriesWithoutLosingEitherSource() {
        val result = LearnsetNormalizer.normalize(
            listOf(LearnsetEntry(1, 106), LearnsetEntry(7, 106)),
        )

        assertEquals(
            listOf(NormalizedLevelUpMove(moveId = 106, initial = true, levels = listOf(7))),
            result,
        )
    }

    @Test
    fun keepsDistinctLaterLevelsSortedAndUnique() {
        val result = LearnsetNormalizer.normalize(
            listOf(LearnsetEntry(20, 52), LearnsetEntry(7, 52), LearnsetEntry(7, 52)),
        )

        assertEquals(listOf(7, 20), result.single().levels)
    }

    @Test
    fun preservesFirstRomOccurrenceOrderAcrossMoves() {
        val result = LearnsetNormalizer.normalize(
            listOf(LearnsetEntry(13, 108), LearnsetEntry(1, 52), LearnsetEntry(7, 52)),
        )

        assertEquals(listOf(108, 52), result.map { it.moveId })
    }
}
