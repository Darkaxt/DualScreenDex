package com.enrpau.dualscreendex.parser.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalMapRasterBudgetTest {
    @Test
    fun pixelRejectionPreservesPriorAssetsAndLeavesRoomForLaterSmallAssets() {
        val budget = LocalMapRasterBudget(512, 1024)
        budget.reserve(256, 300)
        assertThrows(IllegalArgumentException::class.java) { budget.reserve(257, 10) }
        assertEquals(256L, budget.pixels)
        assertEquals(300L, budget.encodedBytes)
        budget.reserve(256, 724)
        assertEquals(512L, budget.pixels)
        assertEquals(1024L, budget.encodedBytes)
    }

    @Test
    fun encodedRejectionDoesNotSpendPixelsAndOverflowCannotBypassTheBudget() {
        val budget = LocalMapRasterBudget(1000, 1024)
        budget.reserve(256, 1024)
        assertThrows(IllegalArgumentException::class.java) { budget.reserve(1, Long.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) { budget.reserve(1, -1) }
        assertEquals(256L, budget.pixels)
        budget.reserve(256, 0)
        assertEquals(512L, budget.pixels)
        assertEquals(1024L, budget.encodedBytes)
    }
}
