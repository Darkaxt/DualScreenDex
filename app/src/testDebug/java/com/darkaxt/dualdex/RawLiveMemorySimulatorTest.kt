package com.darkaxt.dualdex

import com.darkaxt.dualdex.retroarch.ConfigParameter
import com.darkaxt.dualdex.retroarch.CoreMemoryReadSession
import com.darkaxt.dualdex.retroarch.CoreMemoryReadState
import com.darkaxt.dualdex.retroarch.CoreMemoryRegion
import com.darkaxt.dualdex.retroarch.NetworkCommandClient
import com.darkaxt.dualdex.retroarch.NetworkResponse
import com.darkaxt.dualdex.retroarch.RetroArchConnection
import com.darkaxt.dualdex.retroarch.RetroArchStatus
import com.darkaxt.dualdex.retroarch.SessionMonitor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RawLiveMemorySimulatorTest {
    @Test
    fun `status config and version travel through production command parsing`() {
        val simulator = simulator()
        val transport = simulator.openTransport()
        SessionMonitor(NetworkCommandClient(transport)).use { monitor ->
            assertEquals(RetroArchConnection.DISCONNECTED, monitor.heartbeat().connection)
            val state = monitor.heartbeat()
            assertEquals(RetroArchConnection.PAUSED, state.connection)
            assertEquals(
                RetroArchStatus.Running(
                    paused = true,
                    systemId = "game_boy_advance",
                    gameBasename = "Modern Emerald.gba",
                    crc32 = "8C7DBECA",
                ),
                state.lastStatus,
            )
            assertEquals("/qa/saves", state.savefileDirectory)
        }

        NetworkCommandClient(simulator.openTransport()).use { client ->
            client.requestVersion()
            client.requestConfig(ConfigParameter.SYSTEM_DIRECTORY)
            assertEquals(
                listOf(
                    NetworkResponse.Version("1.0.0-dualdex-qa"),
                    NetworkResponse.Config(ConfigParameter.SYSTEM_DIRECTORY, "/qa/system"),
                ),
                client.poll(),
            )
        }
        simulator.close()
    }

    @Test
    fun `core memory reads use production request and reply parsing`() {
        val simulator = simulator()
        simulator.openTransport().use { transport ->
            val reader = CoreMemoryReadSession(transport::send, transport::poll)
            assertEquals(
                CoreMemoryReadState.Reading(0, 3),
                reader.start(listOf(CoreMemoryRegion("probe", 0x02000001, 3))),
            )
            val complete = reader.heartbeat() as CoreMemoryReadState.Complete
            assertArrayEquals(byteArrayOf(0x11, 0x12, 0x13), complete.regions["probe"])
        }
        simulator.close()
    }

    @Test
    fun `each consumer receives an independent reply queue`() {
        val simulator = simulator()
        val first = simulator.openTransport()
        val second = simulator.openTransport()
        try {
            first.send("GET_STATUS".toByteArray(Charsets.US_ASCII))
            assertNull(second.poll())
            assertTrue(first.poll()!!.toString(Charsets.US_ASCII).startsWith("GET_STATUS PAUSED "))
            assertNull(first.poll())
        } finally {
            first.close()
            second.close()
            simulator.close()
        }
    }

    @Test
    fun `missing partial and malformed ranges fail closed`() {
        val cases = listOf(
            faultScenario("missing", emptyList()) to "RetroArch rejected the memory read",
            faultScenario(
                "partial",
                listOf(RawLiveMemoryReadFault(0x02000000, 4, RawLiveMemoryReadFaultKind.PARTIAL)),
            ) to "short READ_CORE_MEMORY reply: expected 4 bytes, received 3",
            faultScenario(
                "malformed",
                listOf(RawLiveMemoryReadFault(0x02000000, 4, RawLiveMemoryReadFaultKind.MALFORMED)),
            ) to "invalid memory byte in RetroArch reply",
        )

        cases.forEach { (scenarioAndReason, reason) ->
            val simulator = RawLiveMemorySimulator(scenarioAndReason)
            simulator.openTransport().use { transport ->
                val reader = CoreMemoryReadSession(transport::send, transport::poll)
                reader.start(listOf(CoreMemoryRegion("probe", 0x02000000, 4)))
                assertEquals(CoreMemoryReadState.Failed(reason), reader.heartbeat())
            }
            simulator.close()
        }
    }

    @Test
    fun `pause play and step advance deterministic raw frames`() {
        var nowNanos = 0L
        val simulator = simulator(frameDurationNanos = 100, monotonicNanos = { nowNanos })
        assertTrue(simulator.snapshot().paused)
        assertEquals(0, simulator.snapshot().frameIndex)

        simulator.step()
        assertTrue(simulator.snapshot().paused)
        assertEquals(1, simulator.snapshot().frameIndex)
        nowNanos += 1_000
        assertEquals(1, simulator.snapshot().frameIndex)

        simulator.play()
        assertFalse(simulator.snapshot().paused)
        nowNanos += 100
        assertEquals(2, simulator.snapshot().frameIndex)
        nowNanos += 200
        assertEquals(1, simulator.snapshot().frameIndex)

        simulator.pause()
        nowNanos += 1_000
        assertTrue(simulator.snapshot().paused)
        assertEquals(1, simulator.snapshot().frameIndex)
        simulator.close()

        nowNanos = 0
        val directStep = simulator(frameDurationNanos = 100, monotonicNanos = { nowNanos })
        directStep.play()
        nowNanos = 250
        assertEquals(0, directStep.step().frameIndex)
        assertTrue(directStep.snapshot().paused)
        directStep.close()
    }

    @Test
    fun `scenario switch terminates an in-flight multi-chunk memory read`() {
        fun scenario(id: String, value: Byte) = RawLiveMemoryScenario(
            id = id,
            systemId = "game_boy_advance",
            gameBasename = "$id.gba",
            crc32 = "8C7DBECA",
            frames = listOf(
                RawLiveMemoryFrame(
                    id = id,
                    regions = listOf(RawLiveMemoryRegion(0x02000000, ByteArray(2_048) { value })),
                ),
            ),
        )

        val simulator = RawLiveMemorySimulator(scenario("first", 1))
        simulator.openTransport().use { endpoint ->
            val reader = CoreMemoryReadSession(
                sender = endpoint::send,
                poller = endpoint::poll,
                maximumChunkBytes = 1_024,
                maximumPacketsPerHeartbeat = 1,
            )
            reader.start(listOf(CoreMemoryRegion("ewram", 0x02000000, 2_048)))
            assertEquals(CoreMemoryReadState.Reading(1_024, 2_048), reader.heartbeat())

            simulator.selectScenario(scenario("second", 2))
            assertEquals(CoreMemoryReadState.Reading(1_024, 2_048), reader.heartbeat())
            assertEquals(
                CoreMemoryReadState.Failed("RetroArch rejected the memory read"),
                reader.heartbeat(),
            )
        }
        simulator.close()
    }

    @Test
    fun `scenario switch clears stale replies and emits one contentless boundary`() {
        val simulator = simulator()
        val endpoint = simulator.openTransport()
        endpoint.send("READ_CORE_MEMORY 2000000 1".toByteArray(Charsets.US_ASCII))

        simulator.selectScenario(
            scenario(
                id = "second",
                gameBasename = "Second.gba",
                crc32 = "1234ABCD",
                frameValues = listOf(0x44),
            ),
        )

        assertEquals("GET_STATUS CONTENTLESS", endpoint.poll()!!.toString(Charsets.US_ASCII))
        assertNull(endpoint.poll())
        endpoint.send("GET_STATUS".toByteArray(Charsets.US_ASCII))
        assertEquals(
            "GET_STATUS PAUSED game_boy_advance,Second.gba,crc32=1234ABCD",
            endpoint.poll()!!.toString(Charsets.US_ASCII),
        )
        simulator.openTransport().use { replacementMemoryEndpoint ->
            replacementMemoryEndpoint.send("READ_CORE_MEMORY 2000000 1".toByteArray(Charsets.US_ASCII))
            assertEquals(
                "READ_CORE_MEMORY 2000000 44",
                replacementMemoryEndpoint.poll()!!.toString(Charsets.US_ASCII),
            )
        }
        assertEquals(0, simulator.snapshot().frameIndex)
        assertTrue(simulator.snapshot().paused)
        endpoint.close()
        simulator.close()
    }

    @Test
    fun `scenario memory and endpoint queues are bounded`() {
        assertThrows(IllegalArgumentException::class.java) {
            RawLiveMemoryFrame(
                id = "oversized",
                regions = listOf(RawLiveMemoryRegion(0, ByteArray(4 * 1024 * 1024 + 1))),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RawLiveMemoryFrame(
                id = "too-many-regions",
                regions = List(17) { index -> RawLiveMemoryRegion(index.toLong(), byteArrayOf(0)) },
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RawLiveMemoryFrame(
                id = "too-many-faults",
                regions = emptyList(),
                readFaults = List(65) { index ->
                    RawLiveMemoryReadFault(index.toLong(), 1, RawLiveMemoryReadFaultKind.UNREADABLE)
                },
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RawLiveMemoryScenario(
                id = "oversized-wire-field",
                systemId = "x".repeat(129),
                gameBasename = "game.gba",
                crc32 = "8C7DBECA",
                frames = listOf(RawLiveMemoryFrame("frame", emptyList())),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RawLiveMemoryScenario(
                id = "delimiter-in-basename",
                systemId = "game_boy_advance",
                gameBasename = "unsafe,name.gba",
                crc32 = "8C7DBECA",
                frames = listOf(RawLiveMemoryFrame("frame", emptyList())),
            )
        }

        val simulator = simulator()
        val endpoint = simulator.openTransport()
        repeat(1_024) { endpoint.send("GET_STATUS".toByteArray(Charsets.US_ASCII)) }
        assertThrows(IllegalStateException::class.java) {
            endpoint.send("GET_STATUS".toByteArray(Charsets.US_ASCII))
        }
        endpoint.close()
        simulator.close()
    }

    @Test
    fun `closed and simulator-stale endpoints reject commands`() {
        val simulator = simulator()
        val closedEndpoint = simulator.openTransport()
        closedEndpoint.close()
        assertThrows(IllegalStateException::class.java) {
            closedEndpoint.send("GET_STATUS".toByteArray(Charsets.US_ASCII))
        }
        assertThrows(IllegalStateException::class.java) { closedEndpoint.poll() }

        val staleEndpoint = simulator.openTransport()
        simulator.close()
        assertThrows(IllegalStateException::class.java) {
            staleEndpoint.send("GET_STATUS".toByteArray(Charsets.US_ASCII))
        }
        assertThrows(IllegalStateException::class.java) { simulator.openTransport() }
    }

    private fun simulator(
        frameDurationNanos: Long = 1_000_000_000L,
        monotonicNanos: () -> Long = System::nanoTime,
    ) = RawLiveMemorySimulator(
        initialScenario = scenario(
            id = "modern-emerald",
            gameBasename = "Modern Emerald.gba",
            crc32 = "8C7DBECA",
            frameValues = listOf(0x10, 0x20, 0x30),
        ),
        frameDurationNanos = frameDurationNanos,
        monotonicNanos = monotonicNanos,
    )

    private fun scenario(
        id: String,
        gameBasename: String,
        crc32: String,
        frameValues: List<Int>,
    ) = RawLiveMemoryScenario(
        id = id,
        systemId = "game_boy_advance",
        gameBasename = gameBasename,
        crc32 = crc32,
        savefileDirectory = "/qa/saves",
        systemDirectory = "/qa/system",
        frames = frameValues.mapIndexed { index, value ->
            RawLiveMemoryFrame(
                id = "frame-$index",
                regions = listOf(
                    RawLiveMemoryRegion(
                        baseAddress = 0x02000000,
                        bytes = byteArrayOf(value.toByte(), 0x11, 0x12, 0x13),
                    ),
                ),
            )
        },
    )

    private fun faultScenario(
        id: String,
        faults: List<RawLiveMemoryReadFault>,
    ) = RawLiveMemoryScenario(
        id = id,
        systemId = "game_boy_advance",
        gameBasename = "$id.gba",
        crc32 = "8C7DBECA",
        frames = listOf(
            RawLiveMemoryFrame(
                id = id,
                regions = if (id == "missing") emptyList() else {
                    listOf(RawLiveMemoryRegion(0x02000000, byteArrayOf(1, 2, 3, 4)))
                },
                readFaults = faults,
            ),
        ),
    )
}
