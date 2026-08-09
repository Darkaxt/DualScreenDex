package com.darkaxt.dualdex.overlay

import kotlin.math.min
import kotlin.math.roundToInt

data class OverlayPanelSize(val width: Int, val height: Int)

object OverlayPanelSizer {
    private const val WIDTH_FRACTION = 0.72
    private const val HEIGHT_FRACTION = 0.88

    fun fit(screenWidth: Int, screenHeight: Int): OverlayPanelSize {
        require(screenWidth > 0 && screenHeight > 0) { "screen dimensions must be positive" }
        val width = min(
            screenWidth * WIDTH_FRACTION,
            screenHeight * HEIGHT_FRACTION * 4.0 / 3.0,
        ).roundToInt().coerceAtLeast(4)
        return OverlayPanelSize(width, (width * 3.0 / 4.0).roundToInt())
    }
}
