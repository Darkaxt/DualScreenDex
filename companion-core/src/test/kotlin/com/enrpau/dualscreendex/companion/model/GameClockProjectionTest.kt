package com.enrpau.dualscreendex.companion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameClockProjectionTest {
    @Test
    fun `projects exact source-defined day and night boundaries`() {
        assertClock(5, 59, GameClockPhase.NIGHT, 539.0 / 540.0)
        assertClock(6, 0, GameClockPhase.DAY, 0.0)
        assertClock(20, 59, GameClockPhase.DAY, 899.0 / 900.0)
        assertClock(21, 0, GameClockPhase.NIGHT, 0.0)
    }

    @Test
    fun `keeps numeric clock but withholds phase without a validated schedule`() {
        val clock = projectGameClock(12, 34, dayStartHour = null, nightStartHour = null)

        assertEquals(12, clock.hours)
        assertEquals(34, clock.minutes)
        assertNull(clock.phase)
        assertNull(clock.phaseProgress)
    }

    @Test
    fun `phase only clock does not invent numeric time or progress`() {
        val clock = GameClock(phase = GameClockPhase.MORNING)

        assertNull(clock.hours)
        assertNull(clock.minutes)
        assertEquals(GameClockPhase.MORNING, clock.phase)
        assertNull(clock.phaseProgress)
    }

    private fun assertClock(hours: Int, minutes: Int, phase: GameClockPhase, progress: Double) {
        val clock = projectGameClock(hours, minutes, dayStartHour = 6, nightStartHour = 21)

        assertEquals(phase, clock.phase)
        assertEquals(progress, requireNotNull(clock.phaseProgress), 0.000_001)
    }
}
