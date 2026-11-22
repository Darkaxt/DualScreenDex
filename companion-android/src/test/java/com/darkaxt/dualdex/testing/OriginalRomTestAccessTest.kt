package com.darkaxt.dualdex.testing

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
    fun unconditionalRealControlClassesRequireOptInBeforeTestBodies() {
        val controls = listOf(
            com.darkaxt.dualdex.live.UnifiedGameStateRealControlTest::class.java,
            com.darkaxt.dualdex.battle.MemoryDumpReplayRealControlTest::class.java,
            com.darkaxt.dualdex.progress.ChallengeOfficialControlsTest::class.java,
            com.darkaxt.dualdex.progress.ChallengeHackControlsTest::class.java,
            com.darkaxt.dualdex.web.DamageFormulaPolicyRealControlTest::class.java,
            com.darkaxt.dualdex.web.OfficialEmeraldPlayerStateRealControlTest::class.java,
            com.darkaxt.dualdex.web.UnboundOdysseySaveCompletionLiveRomTest::class.java,
        )
        for (control in controls) {
            val before = control.getDeclaredMethod("requireReviewedOriginalAccess")
            assertTrue(control.simpleName, before.isAnnotationPresent(Before::class.java))
        }
    }
}
