package com.darkaxt.dualdex.retroarch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetroArchSaveConfigTest {
    @Test
    fun readsTheFinalEffectiveSaveValuesAndIgnoresComments() {
        val config = (
            "# autosave_interval = \"0\"\n" +
                "autosave_interval = \"0\"\n" +
                "savefile_directory = \"/old\"\n" +
                "autosave_interval = \"10\"\n" +
                "savefile_directory = \"/storage/emulated/0/RetroArch/saves\"\n" +
                "sort_savefiles_enable = \"true\"\n" +
                "sort_savefiles_by_content_enable = \"false\"\n"
            ).toByteArray()

        val result = RetroArchSaveConfig.read(config)

        assertEquals(10, result.autosaveIntervalSeconds)
        assertEquals("VERIFIED", result.autosaveStatus)
        assertEquals("/storage/emulated/0/RetroArch/saves", result.savefileDirectory)
        assertEquals(true, result.sortByCore)
        assertEquals(false, result.sortByContentDirectory)
    }

    @Test
    fun distinguishesDisabledFromUnverifiedAutosave() {
        assertEquals("DISABLED", RetroArchSaveConfig.read("autosave_interval = \"0\"".toByteArray()).autosaveStatus)
        val absent = RetroArchSaveConfig.read("video_driver = \"vulkan\"".toByteArray())
        assertEquals("UNVERIFIED", absent.autosaveStatus)
        assertNull(absent.autosaveIntervalSeconds)
    }
}
