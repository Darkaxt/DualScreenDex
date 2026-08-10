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
}
