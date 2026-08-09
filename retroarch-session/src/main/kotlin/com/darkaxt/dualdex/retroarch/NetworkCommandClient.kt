package com.darkaxt.dualdex.retroarch

import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

enum class ConfigParameter(val wireName: String) {
    SAVEFILE_DIRECTORY("savefile_directory"),
    SAVESTATE_DIRECTORY("savestate_directory"),
    SYSTEM_DIRECTORY("system_directory"),
}

sealed interface NetworkResponse {
    data class Status(val value: RetroArchStatus) : NetworkResponse
    data class Config(val parameter: ConfigParameter, val value: String) : NetworkResponse
    data class Version(val value: String) : NetworkResponse
    data class Unknown(val raw: String) : NetworkResponse
}

interface NetworkCommandTransport : AutoCloseable {
    fun send(payload: ByteArray)
    fun poll(): ByteArray?
}

interface RetroArchCommandPort : AutoCloseable {
    fun requestStatus()
    fun requestVersion()
    fun requestConfig(parameter: ConfigParameter)
    fun poll(): List<NetworkResponse>
}

class NetworkCommandClient(
    private val transport: NetworkCommandTransport,
) : RetroArchCommandPort {
    override fun requestStatus() = send("GET_STATUS")
    override fun requestVersion() = send("VERSION")
    override fun requestConfig(parameter: ConfigParameter) = send("GET_CONFIG_PARAM ${parameter.wireName}")

    override fun poll(): List<NetworkResponse> = buildList {
        while (true) {
            val payload = transport.poll() ?: break
            add(parseResponse(payload.toString(Charsets.US_ASCII).trim().trimEnd('\u0000')))
        }
    }

    override fun close() = transport.close()

    private fun send(command: String) = transport.send(command.toByteArray(Charsets.US_ASCII))

    private fun parseResponse(raw: String): NetworkResponse {
        if (raw.startsWith("GET_STATUS ")) return NetworkResponse.Status(RetroArchStatusParser.parse(raw))
        if (raw.startsWith("GET_CONFIG_PARAM ")) {
            val payload = raw.removePrefix("GET_CONFIG_PARAM ")
            val parameter = ConfigParameter.entries.firstOrNull { payload == it.wireName || payload.startsWith("${it.wireName} ") }
            if (parameter != null) {
                return NetworkResponse.Config(parameter, payload.removePrefix(parameter.wireName).trim())
            }
        }
        return if (raw.matches(Regex("[0-9]+(?:\\.[0-9A-Za-z_-]+)+.*"))) {
            NetworkResponse.Version(raw)
        } else {
            NetworkResponse.Unknown(raw)
        }
    }
}

class UdpNetworkCommandTransport(
    port: Int = DEFAULT_PORT,
    host: InetAddress = InetAddress.getByName("127.0.0.1"),
) : NetworkCommandTransport {
    private val channel = DatagramChannel.open().apply {
        configureBlocking(false)
        connect(InetSocketAddress(host, port))
    }

    init {
        require(port in 1..65535) { "network command port must be between 1 and 65535" }
    }

    override fun send(payload: ByteArray) {
        require(payload.isNotEmpty() && payload.size <= MAX_PACKET_BYTES) { "network command packet size is invalid" }
        channel.write(ByteBuffer.wrap(payload))
    }

    override fun poll(): ByteArray? {
        val buffer = ByteBuffer.allocate(MAX_PACKET_BYTES)
        val count = channel.read(buffer)
        if (count <= 0) return null
        return buffer.array().copyOf(count)
    }

    override fun close() = channel.close()

    companion object {
        const val DEFAULT_PORT = 55355
        private const val MAX_PACKET_BYTES = 4096
    }
}
