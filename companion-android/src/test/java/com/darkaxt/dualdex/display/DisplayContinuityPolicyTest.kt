package com.darkaxt.dualdex.display

import com.enrpau.dualscreendex.companion.model.DisplayTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayContinuityPolicyTest {
    private val handheld = DisplayCandidate(id = 0, isDefault = true, isPresentation = false)
    private val external7 = DisplayCandidate(id = 7, isDefault = false, isPresentation = true)
    private val external8 = DisplayCandidate(id = 8, isDefault = false, isPresentation = true)
    private val policy = DisplayContinuityPolicy

    @Test fun autoStaysOnTheLauncherSelectedDisplay() {
        assertEquals(
            DisplayContinuityDecision.Stay,
            policy.decide(
                target = DisplayTarget.AUTO,
                currentDisplayId = 7,
                candidates = listOf(handheld, external7, external8),
                event = DisplayEvent.Added(8),
            ),
        )
    }

    @Test fun externalMovesOnlyToOneEligiblePresentationDisplay() {
        assertEquals(
            DisplayContinuityDecision.Move(7),
            policy.decide(
                target = DisplayTarget.EXTERNAL,
                currentDisplayId = 0,
                candidates = listOf(handheld, external7),
                event = DisplayEvent.Added(7),
            ),
        )
    }

    @Test fun equalExternalCandidatesAreAmbiguous() {
        assertEquals(
            DisplayContinuityDecision.Stay,
            policy.decide(
                target = DisplayTarget.EXTERNAL,
                currentDisplayId = 0,
                candidates = listOf(handheld, external7, external8),
                event = DisplayEvent.Changed(7),
            ),
        )
    }

    @Test fun eligibleCurrentExternalDisplayStaysDespiteOtherCandidates() {
        assertEquals(
            DisplayContinuityDecision.Stay,
            policy.decide(
                target = DisplayTarget.EXTERNAL,
                currentDisplayId = 7,
                candidates = listOf(handheld, external7, external8),
                event = DisplayEvent.Changed(8),
            ),
        )
    }

    @Test fun removingTheCurrentExternalDisplayDefersRecoveryUntilResume() {
        assertEquals(
            DisplayContinuityDecision.ReevaluateOnResume,
            policy.decide(
                target = DisplayTarget.EXTERNAL,
                currentDisplayId = 7,
                candidates = listOf(handheld),
                event = DisplayEvent.Removed(7),
            ),
        )
    }

    @Test fun attemptedDisplaySuppressesAnImmediateRepeatedLaunch() {
        assertEquals(
            DisplayContinuityDecision.Stay,
            policy.decide(
                target = DisplayTarget.EXTERNAL,
                currentDisplayId = 0,
                candidates = listOf(handheld, external7),
                event = DisplayEvent.Changed(7),
                attemptedDisplayId = 7,
            ),
        )
    }

    @Test fun handheldMovesToTheDefaultDisplayOnlyWhenItIsKnown() {
        assertEquals(
            DisplayContinuityDecision.Move(0),
            policy.decide(
                target = DisplayTarget.HANDHELD,
                currentDisplayId = 7,
                candidates = listOf(handheld, external7),
                event = DisplayEvent.TargetChanged,
            ),
        )
        assertEquals(
            DisplayContinuityDecision.Stay,
            policy.decide(
                target = DisplayTarget.HANDHELD,
                currentDisplayId = 7,
                candidates = listOf(external7),
                event = DisplayEvent.TargetChanged,
            ),
        )
    }
}
