package com.darkaxt.dualdex.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPanelSizerTest {
    @Test
    fun fitsAFourByThreePanelInsideLandscapeDisplays() {
        val size = OverlayPanelSizer.fit(2560, 1600)

        assertEquals(4.0 / 3.0, size.width.toDouble() / size.height, 0.01)
        assertTrue(size.width <= 2560 * 0.72)
        assertTrue(size.height <= 1600 * 0.88)
    }

    @Test
    fun keepsTheSameAspectOnAThorSizedViewport() {
        val size = OverlayPanelSizer.fit(1920, 1080)

        assertEquals(4.0 / 3.0, size.width.toDouble() / size.height, 0.01)
        assertTrue(size.width > 0)
        assertTrue(size.height > 0)
    }
}
