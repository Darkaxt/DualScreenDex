package com.darkaxt.dualdex

import com.darkaxt.dualdex.retroarch.RetroArchConnection
import com.darkaxt.dualdex.retroarch.RetroArchStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RetroArchFreeUiQaModeTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `missing marker preserves the production session monitor factory`() {
        assertNull(RetroArchFreeUiQaMode.sessionMonitorFactory(temporaryFolder.root))
    }

    @Test
    fun `marker directory does not enable QA mode`() {
        File(temporaryFolder.root, RetroArchFreeUiQaMode.MARKER_FILE_NAME).mkdir()

        assertNull(RetroArchFreeUiQaMode.sessionMonitorFactory(temporaryFolder.root))
    }

    @Test
    fun `marker file enables an inert contentless session monitor`() {
        File(temporaryFolder.root, RetroArchFreeUiQaMode.MARKER_FILE_NAME).createNewFile()

        val factory = RetroArchFreeUiQaMode.sessionMonitorFactory(temporaryFolder.root)
        assertTrue(factory != null)
        factory!!().use { monitor ->
            val state = monitor.heartbeat()
            assertEquals(RetroArchConnection.CONTENTLESS, state.connection)
            assertEquals(RetroArchStatus.Contentless, state.lastStatus)
            assertNull(state.error)
        }
    }
}
