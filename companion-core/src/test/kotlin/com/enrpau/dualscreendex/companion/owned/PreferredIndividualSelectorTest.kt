package com.enrpau.dualscreendex.companion.owned

import com.enrpau.dualscreendex.companion.model.OwnedPokemon
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferredIndividualSelectorTest {
    @Test
    fun choosesHighestIvSumRegardlessOfLevel() {
        val weakLevelPerfect = OwnedPokemon("box-2", 6, 3, 5, ivs = List(6) { 31 }, captureBallId = 4)
        val highLevelAverage = OwnedPokemon("party-1", 6, 3, 90, ivs = List(6) { 20 }, captureBallId = 2, party = true)

        assertEquals(weakLevelPerfect, PreferredIndividualSelector.select(listOf(highLevelAverage, weakLevelPerfect)))
        assertEquals("ACE", PreferredIndividualSelector.tier(weakLevelPerfect))
    }

    @Test
    fun usesStableKeyForEqualQuality() {
        val a = OwnedPokemon("party-1", 1, 3, 10, ivs = List(6) { 20 })
        val b = a.copy(stableKey = "box-1", level = 50)
        assertEquals(b, PreferredIndividualSelector.select(listOf(a, b)))
    }
}
