package com.darkaxt.dualdex.display

import com.enrpau.dualscreendex.companion.model.DisplayTarget

data class DisplayCandidate(
    val id: Int,
    val isDefault: Boolean,
    val isPresentation: Boolean,
)

sealed interface DisplayEvent {
    data class Added(val displayId: Int) : DisplayEvent
    data class Changed(val displayId: Int) : DisplayEvent
    data class Removed(val displayId: Int) : DisplayEvent
    data object Resumed : DisplayEvent
    data object TargetChanged : DisplayEvent
}

sealed interface DisplayContinuityDecision {
    data object Stay : DisplayContinuityDecision
    data class Move(val displayId: Int) : DisplayContinuityDecision
    data object ReevaluateOnResume : DisplayContinuityDecision
}

/** Pure, deterministic policy for retaining or restoring the companion activity display. */
object DisplayContinuityPolicy {
    fun decide(
        target: DisplayTarget,
        currentDisplayId: Int,
        candidates: List<DisplayCandidate>,
        event: DisplayEvent,
        attemptedDisplayId: Int? = null,
    ): DisplayContinuityDecision = when (target) {
        DisplayTarget.AUTO -> DisplayContinuityDecision.Stay
        DisplayTarget.HANDHELD -> decideDestination(
            currentDisplayId = currentDisplayId,
            eligible = candidates.filter(DisplayCandidate::isDefault),
            attemptedDisplayId = attemptedDisplayId,
        )
        DisplayTarget.EXTERNAL -> {
            if (event is DisplayEvent.Removed && event.displayId == currentDisplayId) {
                DisplayContinuityDecision.ReevaluateOnResume
            } else {
                decideDestination(
                    currentDisplayId = currentDisplayId,
                    eligible = candidates.filter { it.isPresentation && !it.isDefault },
                    attemptedDisplayId = attemptedDisplayId,
                )
            }
        }
    }

    private fun decideDestination(
        currentDisplayId: Int,
        eligible: List<DisplayCandidate>,
        attemptedDisplayId: Int?,
    ): DisplayContinuityDecision {
        if (eligible.any { it.id == currentDisplayId }) return DisplayContinuityDecision.Stay
        val destination = eligible.singleOrNull()?.id ?: return DisplayContinuityDecision.Stay
        if (destination == attemptedDisplayId) return DisplayContinuityDecision.Stay
        return DisplayContinuityDecision.Move(destination)
    }
}
