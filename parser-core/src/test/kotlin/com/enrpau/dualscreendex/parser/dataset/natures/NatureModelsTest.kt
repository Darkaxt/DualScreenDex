package com.enrpau.dualscreendex.parser.dataset.natures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NatureModelsTest {
    @Test
    fun `normalized Nature record preserves ROM order and independent flavor data`() {
        val adamant = NatureRecord(
            id = 3,
            name = "Adamant",
            statModifiers = listOf(1, 0, 0, -1, 0),
            positivePercent = 110,
            negativePercent = 90,
            flavorModifiers = listOf(1, -1, 0, 0, 0),
        )

        assertEquals(NatureStat.ATTACK, adamant.raisedStat)
        assertEquals(NatureStat.SPECIAL_ATTACK, adamant.loweredStat)
        assertEquals(NatureFlavor.SPICY, adamant.likedFlavor)
        assertEquals(NatureFlavor.DRY, adamant.dislikedFlavor)
        assertEquals(110, adamant.multiplierPercent(NatureStat.ATTACK))
        assertEquals(90, adamant.multiplierPercent(NatureStat.SPECIAL_ATTACK))
        assertEquals(100, adamant.multiplierPercent(NatureStat.DEFENSE))
    }

    @Test
    fun `flavor data may be independently unavailable`() {
        val hardy = NatureRecord(
            id = 0,
            name = "Hardy",
            statModifiers = List(5) { 0 },
            positivePercent = 110,
            negativePercent = 90,
            flavorModifiers = null,
        )

        assertEquals(null, hardy.likedFlavor)
        assertEquals(null, hardy.dislikedFlavor)
    }

    @Test
    fun `invalid Nature shapes and modifier values fail closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            NatureRecord(0, "Hardy", listOf(0, 0), 110, 90, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NatureRecord(0, "Hardy", listOf(0, 0, 0, 0, 2), 110, 90, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NatureRecord(0, "Hardy", List(5) { 0 }, 110, 90, listOf(0, 0, -2, 0, 0))
        }
    }
}
