package com.darkaxt.dualdex.setup

import com.darkaxt.dualdex.retroarch.ConfigParameter
import com.darkaxt.dualdex.retroarch.NetworkResponse
import com.darkaxt.dualdex.retroarch.RetroArchCommandPort
import com.darkaxt.dualdex.retroarch.SessionMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandMonitorLifecycleTest {
    @Test
    fun failedMonitorIsClosedAndRecreatedOnTheNextAttempt() {
        val ports = mutableListOf<FakePort>()
        val lifecycle = CommandMonitorLifecycle {
            SessionMonitor(FakePort().also(ports::add))
        }
        val first = lifecycle.monitor()
        ports.single().failPoll = true

        runCatching(first::heartbeat)
        lifecycle.recordFailure()
        val second = lifecycle.monitor()

        assertFalse(first === second)
        assertTrue(ports[0].closed)
        assertFalse(ports[1].closed)
        lifecycle.close()
        assertTrue(ports[1].closed)
    }

    @Test
    fun retryDelayUsesCappedExponentialBackoffAndResetsAfterSuccess() {
        val lifecycle = CommandMonitorLifecycle { SessionMonitor(FakePort()) }
        val delays = buildList {
            add(lifecycle.nextDelayMillis())
            repeat(6) {
                lifecycle.recordFailure()
                add(lifecycle.nextDelayMillis())
            }
        }

        assertEquals(listOf(2_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), delays)
        lifecycle.recordSuccess()
        assertEquals(2_000L, lifecycle.nextDelayMillis())
    }

    private class FakePort : RetroArchCommandPort {
        var failPoll = false
        var closed = false
        override fun requestStatus() = Unit
        override fun requestVersion() = Unit
        override fun requestConfig(parameter: ConfigParameter) = Unit
        override fun poll(): List<NetworkResponse> {
            if (failPoll) error("injected command failure")
            return emptyList()
        }
        override fun close() {
            closed = true
        }
    }
}
