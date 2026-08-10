package com.darkaxt.dualdex.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPanelSizerTest {
    @Test
    fun fitsAFourByThreePanelInsideLandscapeDisplaysAndInsets() {
        val placement = OverlayPanelSizer.fit(
            2560,
            1600,
            OverlayInsets(left = 12, top = 48, right = 20, bottom = 72),
            scale = 1.0,
            minimumWidth = 320,
        )

        assertEquals(4.0 / 3.0, placement.width.toDouble() / placement.height, 0.01)
        assertTrue(placement.x >= 12)
        assertTrue(placement.y >= 48)
        assertTrue(placement.right <= 2560 - 20)
        assertTrue(placement.bottom <= 1600 - 72)
    }

    @Test
    fun keepsTheSameAspectOnAThorSizedViewport() {
        val placement = OverlayPanelSizer.fit(1920, 1080, scale = 0.6, minimumWidth = 320)

        assertEquals(4.0 / 3.0, placement.width.toDouble() / placement.height, 0.01)
        assertTrue(placement.width >= 320)
        assertEquals(OverlayGutter.NONE, placement.gutter)
    }

    @Test
    fun prefersAnUnusedSideGutterOnUltraWideDisplays() {
        val placement = OverlayPanelSizer.fit(
            2520,
            1080,
            OverlayInsets(top = 48, bottom = 72),
            scale = 0.6,
            minimumWidth = 320,
        )

        assertEquals(OverlayGutter.LEFT, placement.gutter)
        assertTrue(placement.right < 620)
        assertTrue(placement.bottom <= 1080 - 72)
    }

    @Test
    fun clampsScaleAndRefitsDeterministicallyAfterRotation() {
        assertEquals(0.45, OverlayPanelSizer.clampScale(0.1), 0.0)
        assertEquals(1.0, OverlayPanelSizer.clampScale(9.0), 0.0)

        val portrait = OverlayPanelSizer.fit(1080, 1920, scale = 0.45, minimumWidth = 320)
        val repeated = OverlayPanelSizer.fit(1080, 1920, scale = 0.45, minimumWidth = 320)

        assertEquals(portrait, repeated)
        assertEquals(OverlayGutter.NONE, portrait.gutter)
        assertTrue(portrait.right <= 1080)
        assertTrue(portrait.bottom <= 1920)
    }
}
