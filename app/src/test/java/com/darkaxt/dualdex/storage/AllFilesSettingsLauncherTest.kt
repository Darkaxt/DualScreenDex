package com.darkaxt.dualdex.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllFilesSettingsLauncherTest {
    @Test
    fun `uses package settings when available`() {
        var globalOpened = false
        var safOpened = false
        val launcher = AllFilesSettingsLaunchCoordinator(
            openPackageSettings = { true },
            openGlobalSettings = { globalOpened = true; true },
            openSafFallback = { safOpened = true },
        )

        assertEquals(AllFilesSettingsDestination.PACKAGE_SETTINGS, launcher.open())
        assertFalse(globalOpened)
        assertFalse(safOpened)
    }

    @Test
    fun `falls back to global settings when package settings are unavailable`() {
        var safOpened = false
        val launcher = AllFilesSettingsLaunchCoordinator(
            openPackageSettings = { false },
            openGlobalSettings = { true },
            openSafFallback = { safOpened = true },
        )

        assertEquals(AllFilesSettingsDestination.GLOBAL_SETTINGS, launcher.open())
        assertFalse(safOpened)
    }

    @Test
    fun `opens SAF guidance when neither settings intent is available`() {
        var safOpened = false
        val launcher = AllFilesSettingsLaunchCoordinator(
            openPackageSettings = { false },
            openGlobalSettings = { false },
            openSafFallback = { safOpened = true },
        )

        assertEquals(AllFilesSettingsDestination.SAF_FALLBACK, launcher.open())
        assertTrue(safOpened)
    }

    @Test
    fun `contains a launch race and continues through fallbacks`() {
        var safOpened = false
        val launcher = AllFilesSettingsLaunchCoordinator(
            openPackageSettings = { throw IllegalStateException("handler disappeared") },
            openGlobalSettings = { throw IllegalStateException("handler disappeared") },
            openSafFallback = { safOpened = true },
        )

        assertEquals(AllFilesSettingsDestination.SAF_FALLBACK, launcher.open())
        assertTrue(safOpened)
    }
}
