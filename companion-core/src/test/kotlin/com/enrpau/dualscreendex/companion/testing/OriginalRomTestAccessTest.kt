package com.enrpau.dualscreendex.companion.testing

import org.junit.Assert.*
import org.junit.AssumptionViolatedException
import org.junit.Before
import org.junit.Test

class OriginalRomTestAccessTest {
    @Test
    fun absentDisabledAndLookalikeFlagsNeverInvokeTheReader() {
        var reads = 0
        for (optIn in listOf(null, "", "false", "0", "1", "TRUE", "true ")) {
            assertThrows(AssumptionViolatedException::class.java) {
                OriginalRomTestAccess.readIfEnabled(optIn) { reads++ }
            }
        }
        assertEquals(0, reads)
    }

    @Test
    fun exactOptInInvokesOnlyTheSuppliedFabricatedReader() {
        var reads = 0
        assertEquals(42, OriginalRomTestAccess.readIfEnabled("true") { reads++; 42 })
        assertEquals(1, reads)
    }

    @Test
    fun realControlClassesRequireOptInBeforeTestBodies() {
        for (control in listOf(
            com.enrpau.dualscreendex.companion.battle.DamageForecastRealControlTest::class.java,
            com.enrpau.dualscreendex.companion.map.AreaGuideBuilderRealControlTest::class.java,
        )) {
            val before = control.getDeclaredMethod("requireReviewedOriginalAccess")
            assertTrue(control.simpleName, before.isAnnotationPresent(Before::class.java))
        }
    }
}
