package com.darkaxt.dualdex.display

import com.enrpau.dualscreendex.companion.model.DisplayTarget

typealias CompanionDisplay = DisplayCandidate

/** Resolves a preference without moving AUTO sessions away from the launcher-selected display. */
object DisplayTargetController {
    fun resolve(target: DisplayTarget, currentDisplayId: Int, displays: List<CompanionDisplay>): Int {
        return when (
            val decision = DisplayContinuityPolicy.decide(
                target = target,
                currentDisplayId = currentDisplayId,
                candidates = displays,
                event = DisplayEvent.Resumed,
            )
        ) {
            is DisplayContinuityDecision.Move -> decision.displayId
            DisplayContinuityDecision.ReevaluateOnResume,
            DisplayContinuityDecision.Stay,
            -> currentDisplayId
        }
    }
}
