package com.darkaxt.dualdex.display

import com.enrpau.dualscreendex.companion.model.DisplayTarget

data class CompanionDisplay(
    val id: Int,
    val isDefault: Boolean,
    val isPresentation: Boolean,
)

/** Resolves a preference without moving AUTO sessions away from the launcher-selected display. */
object DisplayTargetController {
    fun resolve(target: DisplayTarget, currentDisplayId: Int, displays: List<CompanionDisplay>): Int {
        val current = displays.firstOrNull { it.id == currentDisplayId }
        return when (target) {
            DisplayTarget.AUTO -> current?.id ?: displays.firstOrNull { it.isDefault }?.id ?: currentDisplayId
            DisplayTarget.HANDHELD -> displays.firstOrNull { it.isDefault }?.id ?: current?.id ?: currentDisplayId
            DisplayTarget.EXTERNAL -> displays.firstOrNull { it.isPresentation && !it.isDefault }?.id
                ?: displays.firstOrNull { !it.isDefault }?.id
                ?: current?.id
                ?: currentDisplayId
        }
    }
}
