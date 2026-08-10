package com.darkaxt.dualdex.settings

import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.Density
import com.enrpau.dualscreendex.companion.model.DisplayMode
import com.enrpau.dualscreendex.companion.model.DisplayTarget
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.companion.model.Theme
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryTest {
    @Test
    fun roundTripsEveryUserSetting() {
        var document: String? = null
        val repository = SettingsRepository({ document }, { document = it })
        val settings = CompanionSettings(
            knowledgeMode = KnowledgeMode.HIDDEN,
            attackEnabled = false,
            rarityEnabled = false,
            movesEnabled = false,
            fontScale = 1.25,
            density = Density.COMPACT,
            highContrast = true,
            autoOpenTarget = false,
            ruleset = "modern",
            displayMode = DisplayMode.OVERLAY,
            theme = Theme.DARK,
            displayTarget = DisplayTarget.EXTERNAL,
            overlayScale = 0.65,
        )

        repository.write(settings)

        assertEquals(settings, repository.read())
    }

    @Test
    fun invalidFieldsFallBackIndependentlyWithoutCrashingStartup() {
        val document = """{
          "knowledgeMode":"NOT_A_MODE",
          "fontScale":99,
          "density":"COMPACT",
          "ruleset":"",
          "displayMode":"NOT_A_DISPLAY",
          "theme":"DARK",
          "displayTarget":"NOPE"
        }"""
        val settings = SettingsRepository({ document }, {}).read()

        assertEquals(KnowledgeMode.ORGANIC, settings.knowledgeMode)
        assertEquals(1.35, settings.fontScale, 0.0)
        assertEquals(Density.COMPACT, settings.density)
        assertEquals("AUTO", settings.ruleset)
        assertEquals(DisplayMode.DOCKED, settings.displayMode)
        assertEquals(Theme.DARK, settings.theme)
        assertEquals(DisplayTarget.AUTO, settings.displayTarget)
        assertEquals(1.0, settings.overlayScale, 0.0)
    }

    @Test
    fun clampsPersistedOverlayScaleAndMigratesLegacyDocuments() {
        val legacy = SettingsRepository({ "{}" }, {}).read()
        val invalid = SettingsRepository({ """{"overlayScale":8}""" }, {}).read()

        assertEquals(1.0, legacy.overlayScale, 0.0)
        assertEquals(1.0, invalid.overlayScale, 0.0)
    }
}
