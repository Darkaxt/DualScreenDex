package com.darkaxt.dualdex.overlay

import kotlin.math.min
import kotlin.math.roundToInt

data class OverlayInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
)

enum class OverlayGutter { LEFT, NONE }

data class OverlayPanelPlacement(
    val width: Int,
    val height: Int,
    val x: Int,
    val y: Int,
    val scale: Double,
    val gutter: OverlayGutter,
) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height
}

object OverlayPanelSizer {
    const val MIN_SCALE = 0.45
    const val MAX_SCALE = 1.0

    private const val WIDTH_FRACTION = 0.72
    private const val HEIGHT_FRACTION = 0.88
    private const val GUTTER_PADDING = 12

    fun clampScale(scale: Double): Double = scale
        .takeIf(Double::isFinite)
        ?.coerceIn(MIN_SCALE, MAX_SCALE)
        ?: MAX_SCALE

    fun fit(
        screenWidth: Int,
        screenHeight: Int,
        insets: OverlayInsets = OverlayInsets(),
        scale: Double = MAX_SCALE,
        minimumWidth: Int = 320,
    ): OverlayPanelPlacement {
        require(screenWidth > 0 && screenHeight > 0) { "screen dimensions must be positive" }
        require(insets.left >= 0 && insets.top >= 0 && insets.right >= 0 && insets.bottom >= 0) {
            "insets must not be negative"
        }
        require(minimumWidth > 0) { "minimum width must be positive" }

        val availableWidth = (screenWidth - insets.left - insets.right).coerceAtLeast(1)
        val availableHeight = (screenHeight - insets.top - insets.bottom).coerceAtLeast(1)
        val largestContainedWidth = min(availableWidth.toDouble(), availableHeight * 4.0 / 3.0)
        val readableMinimum = min(minimumWidth.toDouble(), largestContainedWidth)
        val normalizedScale = clampScale(scale)

        val centeredGameWidth = min(availableWidth.toDouble(), availableHeight * 4.0 / 3.0)
        val sideGutterWidth = ((availableWidth - centeredGameWidth) / 2.0).coerceAtLeast(0.0)
        val gutterMaximum = (sideGutterWidth - GUTTER_PADDING * 2).coerceAtLeast(0.0)
        val useGutter = gutterMaximum >= readableMinimum

        val baseWidth = if (useGutter) {
            min(gutterMaximum, availableHeight * HEIGHT_FRACTION * 4.0 / 3.0)
        } else {
            min(availableWidth * WIDTH_FRACTION, availableHeight * HEIGHT_FRACTION * 4.0 / 3.0)
        }.coerceAtLeast(readableMinimum)

        val width = (baseWidth * normalizedScale)
            .coerceAtLeast(readableMinimum)
            .coerceAtMost(largestContainedWidth)
            .roundToInt()
            .coerceAtLeast(1)
        val height = (width * 3.0 / 4.0).roundToInt().coerceAtLeast(1)
        val x = if (useGutter) {
            insets.left + ((sideGutterWidth - width) / 2.0).roundToInt()
        } else {
            insets.left + (availableWidth - width) / 2
        }.coerceIn(insets.left, (screenWidth - insets.right - width).coerceAtLeast(insets.left))
        val y = (insets.top + (availableHeight - height) / 2)
            .coerceIn(insets.top, (screenHeight - insets.bottom - height).coerceAtLeast(insets.top))

        return OverlayPanelPlacement(
            width = width,
            height = height,
            x = x,
            y = y,
            scale = normalizedScale,
            gutter = if (useGutter) OverlayGutter.LEFT else OverlayGutter.NONE,
        )
    }
}
