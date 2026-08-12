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

enum class RomDisplayModeApplicationAction {
    APPLY_IN_ACTIVITY,
    SHOW_OVERLAY,
    DOCK_OVERLAY,
    WAIT_FOR_ACTIVITY,
}

object RomDisplayModeApplicationPolicy {
    fun resolve(
        mode: DisplayMode,
        activityResumed: Boolean,
        canDrawOverlays: Boolean,
    ): RomDisplayModeApplicationAction = when {
        activityResumed -> RomDisplayModeApplicationAction.APPLY_IN_ACTIVITY
        mode == DisplayMode.DOCKED -> RomDisplayModeApplicationAction.DOCK_OVERLAY
        canDrawOverlays -> RomDisplayModeApplicationAction.SHOW_OVERLAY
        else -> RomDisplayModeApplicationAction.WAIT_FOR_ACTIVITY
    }
}

enum class HeadlessDockAction { REQUEST_SERVICE_DOCK_AND_SURFACE, NO_ACTION }

object HeadlessDockPolicy {
    fun resolve(overlayServiceRunning: Boolean): HeadlessDockAction =
        if (overlayServiceRunning) HeadlessDockAction.REQUEST_SERVICE_DOCK_AND_SURFACE
        else HeadlessDockAction.NO_ACTION
}
