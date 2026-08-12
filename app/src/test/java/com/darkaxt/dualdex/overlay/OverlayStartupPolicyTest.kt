package com.darkaxt.dualdex.overlay

import com.enrpau.dualscreendex.companion.model.DisplayMode
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayStartupPolicyTest {
    @Test
    fun persistedOverlayStartsWhenPermissionIsStillGranted() {
        assertEquals(
            OverlayStartupAction.START_OVERLAY,
            OverlayStartupPolicy.resolve(DisplayMode.OVERLAY, canDrawOverlays = true),
        )
    }

    @Test
    fun persistedOverlayRevertsWhenPermissionWasRevoked() {
        assertEquals(
            OverlayStartupAction.REVERT_TO_DOCKED,
            OverlayStartupPolicy.resolve(DisplayMode.OVERLAY, canDrawOverlays = false),
        )
    }

    @Test
    fun dockedStartupDoesNotCreateAnOverlay() {
        assertEquals(
            OverlayStartupAction.STAY_DOCKED,
            OverlayStartupPolicy.resolve(DisplayMode.DOCKED, canDrawOverlays = true),
        )
    }

    @Test
    fun romDockedModeStopsTheOverlayWithoutAResumedActivity() {
        assertEquals(
            RomDisplayModeApplicationAction.DOCK_OVERLAY,
            RomDisplayModeApplicationPolicy.resolve(
                DisplayMode.DOCKED,
                activityResumed = false,
                canDrawOverlays = true,
            ),
        )
    }

    @Test
    fun headlessDockUsesTheRunningForegroundServiceToSurfaceTheActivity() {
        assertEquals(
            HeadlessDockAction.REQUEST_SERVICE_DOCK_AND_SURFACE,
            HeadlessDockPolicy.resolve(overlayServiceRunning = true),
        )
    }

    @Test
    fun headlessDockDoesNotAttemptABlockedBackgroundActivityLaunchWithoutTheService() {
        assertEquals(
            HeadlessDockAction.NO_ACTION,
            HeadlessDockPolicy.resolve(overlayServiceRunning = false),
        )
    }

    @Test
    fun romOverlayModeStartsFromApplicationContextWhenPermissionIsGranted() {
        assertEquals(
            RomDisplayModeApplicationAction.SHOW_OVERLAY,
            RomDisplayModeApplicationPolicy.resolve(
                DisplayMode.OVERLAY,
                activityResumed = false,
                canDrawOverlays = true,
            ),
        )
    }

    @Test
    fun romOverlayModeWaitsForAnActivityWhenPermissionMustBeRequested() {
        assertEquals(
            RomDisplayModeApplicationAction.WAIT_FOR_ACTIVITY,
            RomDisplayModeApplicationPolicy.resolve(
                DisplayMode.OVERLAY,
                activityResumed = false,
                canDrawOverlays = false,
            ),
        )
    }
}
