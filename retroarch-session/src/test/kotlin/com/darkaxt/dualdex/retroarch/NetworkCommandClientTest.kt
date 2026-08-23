package com.darkaxt.dualdex.retroarch

import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer

class NetworkCommandClientTest {
    @Test
    fun exposesOnlyReadOnlySessionQueries() {
        val transport = FakeTransport()
        val client = NetworkCommandClient(transport)

        client.requestStatus()
        client.requestVersion()
        client.requestConfig(ConfigParameter.SAVEFILE_DIRECTORY)

        assertEquals(
            listOf("GET_STATUS", "VERSION", "GET_CONFIG_PARAM savefile_directory"),
            transport.sent.map { it.toString(Charsets.US_ASCII) },
        )
    }

    @Test
    fun pollsEveryAvailableResponseWithoutBlocking() {
        val transport = FakeTransport().apply {
            responses += "GET_STATUS CONTENTLESS".toByteArray()
            responses += "GET_CONFIG_PARAM savefile_directory /storage/emulated/0/RetroArch/saves".toByteArray()
        }
        val client = NetworkCommandClient(transport)

        assertEquals(
            listOf(
                NetworkResponse.Status(RetroArchStatus.Contentless),
                NetworkResponse.Config(ConfigParameter.SAVEFILE_DIRECTORY, "/storage/emulated/0/RetroArch/saves"),
            ),
            client.poll(),
        )
        assertEquals(emptyList<NetworkResponse>(), client.poll())
    }

    @Test
    fun reusesOneReceiveBufferAcrossEmptyUdpPolls() {
        var allocations = 0
        UdpNetworkCommandTransport(
            receiveBufferFactory = {
                allocations++
                ByteBuffer.allocate(4096)
            },
        ).use { transport ->
            repeat(100) { assertNull(transport.poll()) }
        }

        assertEquals(1, allocations)
    }

    private class FakeTransport : NetworkCommandTransport {
        val sent = mutableListOf<ByteArray>()
        val responses = ArrayDeque<ByteArray>()
        override fun send(payload: ByteArray) { sent += payload }
        override fun poll(): ByteArray? = responses.pollFirst()
        override fun close() = Unit
    }
}
