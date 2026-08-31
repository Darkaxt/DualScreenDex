package com.darkaxt.dualdex

import com.darkaxt.dualdex.retroarch.CoreMemoryReadSession
import com.darkaxt.dualdex.retroarch.CoreMemoryReadState
import com.darkaxt.dualdex.retroarch.CoreMemoryRegion
import com.darkaxt.dualdex.retroarch.NetworkCommandClient
import com.darkaxt.dualdex.retroarch.RetroArchConnection
import com.darkaxt.dualdex.retroarch.RetroArchStatus
import com.darkaxt.dualdex.retroarch.SessionMonitor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RetroArchFreeUiQaModeTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `marker loads the packaged controller and malformed assets fail closed`() {
        val marker = File(temporaryFolder.root, RetroArchFreeUiQaMode.MARKER_FILE_NAME)
        marker.createNewFile()
        val asset = File("src/debug/assets/${RetroArchFreeUiQaMode.SCENARIO_ASSET_PATH}").readBytes()

        val controller = RetroArchFreeUiQaMode.loadController(temporaryFolder.root) { asset }
        assertNotNull(controller)
        assertEquals("modern-normal", controller!!.snapshot().scenarioId)
        controller.close()

        assertNull(RetroArchFreeUiQaMode.loadController(temporaryFolder.root) { byteArrayOf(1, 2, 3) })
        val fallback = RetroArchFreeUiQaMode.transportFactory(temporaryFolder.root)
        assertNotNull(fallback)
    }

    @Test
    fun `runtime identity attests the debug package sanitized transport and active scenario`() {
        File(temporaryFolder.root, RetroArchFreeUiQaMode.MARKER_FILE_NAME).createNewFile()
        val asset = File("src/debug/assets/${RetroArchFreeUiQaMode.SCENARIO_ASSET_PATH}").readBytes()
        val controller = requireNotNull(RetroArchFreeUiQaMode.loadController(temporaryFolder.root) { asset })
        try {
            assertEquals(
                QaRuntimeIdentityView(
                    applicationId = "com.darkaxt.dualdex.debug",
                    transport = "SANITIZED_RAW_MEMORY",
                    scenarioId = "modern-normal",
                ),
                RetroArchFreeUiQaMode.runtimeIdentity("com.darkaxt.dualdex.debug", controller),
            )
        } finally {
            controller.close()
        }
    }

    @Test
    fun `missing marker preserves the production transport factory`() {
        assertNull(RetroArchFreeUiQaMode.transportFactory(temporaryFolder.root))
    }

    @Test
    fun `marker directory does not enable QA mode`() {
        File(temporaryFolder.root, RetroArchFreeUiQaMode.MARKER_FILE_NAME).mkdir()

        assertNull(RetroArchFreeUiQaMode.transportFactory(temporaryFolder.root))
    }

    @Test
    fun `marker transport factory creates independent command endpoints`() {
        File(temporaryFolder.root, RetroArchFreeUiQaMode.MARKER_FILE_NAME).createNewFile()

        val factory = RetroArchFreeUiQaMode.transportFactory(temporaryFolder.root)!!
        val first = factory()
        val second = factory()
        try {
            assertNotSame(first, second)
            first.send("GET_STATUS".toByteArray(Charsets.US_ASCII))
            assertEquals("GET_STATUS CONTENTLESS", first.poll()!!.toString(Charsets.US_ASCII))
            assertNull(second.poll())
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `missing raw memory range is rejected with the production wire grammar`() {
        File(temporaryFolder.root, RetroArchFreeUiQaMode.MARKER_FILE_NAME).createNewFile()

        val transport = RetroArchFreeUiQaMode.transportFactory(temporaryFolder.root)!!()
        transport.use {
            val reader = CoreMemoryReadSession(transport::send, transport::poll)
            reader.start(listOf(CoreMemoryRegion("probe", 0x02000000, 4)))

            assertEquals(
                CoreMemoryReadState.Failed("RetroArch rejected the memory read"),
                reader.heartbeat(),
            )
        }
    }

    @Test
    fun `marker file enables a contentless raw command transport`() {
        File(temporaryFolder.root, RetroArchFreeUiQaMode.MARKER_FILE_NAME).createNewFile()

        val factory = RetroArchFreeUiQaMode.transportFactory(temporaryFolder.root)
        assertTrue(factory != null)
        SessionMonitor(NetworkCommandClient(factory!!())).use { monitor ->
            assertEquals(RetroArchConnection.DISCONNECTED, monitor.heartbeat().connection)
            val state = monitor.heartbeat()
            assertEquals(RetroArchConnection.CONTENTLESS, state.connection)
            assertEquals(RetroArchStatus.Contentless, state.lastStatus)
            assertNull(state.error)
        }
    }
}
