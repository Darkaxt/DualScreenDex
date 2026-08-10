package com.darkaxt.dualdex.retroarch

import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionMonitorTest {
    @Test
    fun heartbeatsPublishContentAndRuntimeSaveDirectory() {
        val port = FakePort()
        val monitor = SessionMonitor(port, missedHeartbeatLimit = 3)

        assertEquals(RetroArchConnection.DISCONNECTED, monitor.heartbeat().connection)
        assertEquals(listOf("status", "savefile_directory"), port.requests)

        port.responses += NetworkResponse.Status(
            RetroArchStatus.Running(false, "Nintendo - Game Boy Advance", "Pokemon Emerald", "1F1C08FB"),
        )
        port.responses += NetworkResponse.Config(
            ConfigParameter.SAVEFILE_DIRECTORY,
            "/storage/emulated/0/RetroArch/saves",
        )
        val connected = monitor.heartbeat()

        assertEquals(RetroArchConnection.PLAYING, connected.connection)
        assertEquals("/storage/emulated/0/RetroArch/saves", connected.savefileDirectory)
    }

    @Test
    fun missedMonitoringHeartbeatsBecomeDisconnectedWithoutClearingLastContent() {
        val port = FakePort().apply {
            responses += NetworkResponse.Status(RetroArchStatus.Contentless)
        }
        val monitor = SessionMonitor(port, missedHeartbeatLimit = 2)
        monitor.heartbeat()

        assertEquals(RetroArchConnection.CONTENTLESS, monitor.heartbeat().connection)
        val disconnected = monitor.heartbeat()

        assertEquals(RetroArchConnection.DISCONNECTED, disconnected.connection)
        assertEquals(RetroArchStatus.Contentless, disconnected.lastStatus)
        assertNull(disconnected.error)
    }

    private class FakePort : RetroArchCommandPort {
        val requests = mutableListOf<String>()
        val responses = ArrayDeque<NetworkResponse>()
        override fun requestStatus() { requests += "status" }
        override fun requestVersion() { requests += "version" }
        override fun requestConfig(parameter: ConfigParameter) { requests += parameter.wireName }
        override fun poll(): List<NetworkResponse> = buildList {
            while (responses.isNotEmpty()) add(responses.removeFirst())
        }
        override fun close() = Unit
    }
}
