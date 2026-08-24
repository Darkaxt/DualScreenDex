package com.darkaxt.dualdex.battle

import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.TrainerIdentity
import com.darkaxt.dualdex.save.TrainerPlayTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LiveMemoryModelsTest {
    @Test
    fun availableEmptyPartyIsNotUnavailable() {
        val party: LiveValue<List<OwnedIndividual>> = LiveValue.Available(emptyList())

        assertEquals(emptyList<OwnedIndividual>(), party.valueOrNull())
    }

    @Test
    fun trainerIdDoesNotDependOnPokedexCounts() {
        val trainer = LiveTrainerState(
            identity = LiveValue.Available(TrainerIdentity("RED", 0)),
            publicTrainerId = LiveValue.Available(12_345),
            money = LiveValue.Available(3_000L),
            playTime = LiveValue.Available(TrainerPlayTime(2, 17)),
            badgeFlags = LiveValue.Available(0),
            stars = unavailable(),
        )
        val pokedex = LivePokedexState(
            seenDexNumbers = unavailable(),
            caughtDexNumbers = unavailable(),
        )

        assertEquals(12_345, trainer.publicTrainerId.valueOrNull())
        assertNull(pokedex.seenDexNumbers.valueOrNull())
    }

    @Test
    fun clockCanRepresentValidatedGen2PhaseWithoutInventingNumericTime() {
        val clock = LiveClockState(phase = LiveClockPhase.NIGHT)

        assertNull(clock.hours)
        assertNull(clock.minutes)
        assertNull(clock.seconds)
        assertEquals(LiveClockPhase.NIGHT, clock.phase)
        assertThrows(IllegalArgumentException::class.java) { LiveClockState() }
    }

    private fun unavailable(): LiveValue.Unavailable = LiveValue.Unavailable(
        LiveUnavailableReason(
            code = LiveUnavailableCode.MISSING_REGION,
            detail = "test region is absent",
        ),
    )
}
