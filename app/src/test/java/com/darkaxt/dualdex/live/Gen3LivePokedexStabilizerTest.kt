package com.darkaxt.dualdex.live

import com.darkaxt.dualdex.battle.LivePokedexState
import com.darkaxt.dualdex.battle.LiveValue
import com.darkaxt.dualdex.battle.valueOrNull
import com.darkaxt.dualdex.save.OwnedIndividual
import org.junit.Assert.assertEquals
import org.junit.Test

class Gen3LivePokedexStabilizerTest {
    @Test
    fun `withholds the transient starter layout and accepts the plausible correction immediately`() {
        val stabilizer = Gen3LivePokedexStabilizer()

        assertCounts(0, 0, stabilizer.accept(candidate(), party(0)))
        assertCounts(0, 0, stabilizer.accept(candidate(0x80, 48, 48), party(1)))
        assertCounts(1, 1, stabilizer.accept(candidate(0x28, 1, 1), party(1)))
    }

    @Test
    fun `publishes an identical suspicious candidate on its second poll`() {
        val stabilizer = Gen3LivePokedexStabilizer()
        stabilizer.accept(candidate(), party(0))

        assertCounts(0, 0, stabilizer.accept(candidate(0x80, 48, 48), party(1)))
        assertCounts(48, 48, stabilizer.accept(candidate(0x80, 48, 48), party(1)))
    }

    @Test
    fun `rejects a conflicting offset after the live layout is confirmed`() {
        val stabilizer = Gen3LivePokedexStabilizer()
        stabilizer.accept(candidate(), party(0))
        assertCounts(1, 1, stabilizer.accept(candidate(0x28, 1, 1), party(1)))

        assertCounts(1, 1, stabilizer.accept(candidate(0x2C, 2, 2), party(1)))
        assertCounts(1, 1, stabilizer.accept(candidate(0x2C, 2, 2), party(1)))
    }

    @Test
    fun `publishes ordinary same-layout discoveries on the first poll`() {
        val stabilizer = Gen3LivePokedexStabilizer()
        stabilizer.accept(candidate(), party(0))
        stabilizer.accept(candidate(0x28, 1, 1), party(1))

        assertCounts(1, 2, stabilizer.accept(candidate(0x28, 1, 2), party(1)))
    }

    @Test
    fun `publishes two newly seen opponents from a double battle immediately`() {
        val stabilizer = Gen3LivePokedexStabilizer()
        stabilizer.accept(candidate(), party(0))
        stabilizer.accept(candidate(0x28, 1, 1), party(1))

        assertCounts(1, 3, stabilizer.accept(candidate(0x28, 1, 3), party(1)))
    }

    @Test
    fun `reset permits a different layout in a new session`() {
        val stabilizer = Gen3LivePokedexStabilizer()
        stabilizer.accept(candidate(), party(0))
        stabilizer.accept(candidate(0x28, 1, 1), party(1))

        stabilizer.reset()
        stabilizer.accept(candidate(), party(0))

        assertCounts(1, 1, stabilizer.accept(candidate(0x2C, 1, 1), party(1)))
    }

    private fun candidate(
        offset: Int? = null,
        caughtCount: Int = 0,
        seenCount: Int = caughtCount,
    ): LivePokedexState {
        val caught = (1..caughtCount).toSet()
        val seen = (1..seenCount).toSet()
        return LivePokedexState(
            seenDexNumbers = LiveValue.Available(seen),
            caughtDexNumbers = LiveValue.Available(caught),
            ownedFlagOffset = offset,
        )
    }

    private fun party(count: Int): LiveValue<List<OwnedIndividual>> = LiveValue.Available(
        (1..count).map { index -> OwnedIndividual("party-$index", speciesId = index) },
    )

    private fun assertCounts(expectedCaught: Int, expectedSeen: Int, actual: LivePokedexState) {
        assertEquals(expectedCaught, actual.caughtDexNumbers.valueOrNull()?.size)
        assertEquals(expectedSeen, actual.seenDexNumbers.valueOrNull()?.size)
    }
}
