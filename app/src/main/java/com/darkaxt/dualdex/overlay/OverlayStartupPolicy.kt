package com.darkaxt.dualdex.overlay

import com.enrpau.dualscreendex.companion.model.DisplayMode

enum class OverlayStartupAction {
    STAY_DOCKED,
    START_OVERLAY,
    REVERT_TO_DOCKED,
}

object OverlayStartupPolicy {
    fun resolve(mode: DisplayMode, canDrawOverlays: Boolean): OverlayStartupAction = when {
        mode != DisplayMode.OVERLAY -> OverlayStartupAction.STAY_DOCKED
        canDrawOverlays -> OverlayStartupAction.START_OVERLAY
        else -> OverlayStartupAction.REVERT_TO_DOCKED
    }
}
