package com.darkaxt.dualdex.overlay

import com.enrpau.dualscreendex.companion.model.CompanionSettings

class OverlaySizeStore(
    private val readSettings: () -> CompanionSettings,
    private val writeSettings: (CompanionSettings) -> Unit,
) {
    fun readScale(): Double = OverlayPanelSizer.clampScale(readSettings().overlayScale)

    fun writeScale(scale: Double) {
        val current = readSettings()
        writeSettings(current.copy(overlayScale = OverlayPanelSizer.clampScale(scale)))
    }
}
