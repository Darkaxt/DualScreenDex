package com.darkaxt.dualdex

import com.darkaxt.dualdex.retroarch.NetworkCommandTransport
import java.io.File
import java.util.ArrayDeque

class RetroArchFreeUiQaApplication : DualDexApplication() {
    private var rawMemoryController: RawLiveMemoryQaController? = null

    override fun onCreate() {
        rawMemoryController = RetroArchFreeUiQaMode.loadController(filesDir) {
            assets.open(RetroArchFreeUiQaMode.SCENARIO_ASSET_PATH).use { it.readBytes() }
        }
        super.onCreate()
    }

    override fun networkCommandTransportFactory(): () -> NetworkCommandTransport =
        rawMemoryController?.transportFactory()
            ?: RetroArchFreeUiQaMode.transportFactory(filesDir)
            ?: super.networkCommandTransportFactory()

    internal fun rawMemoryQaController(): RawLiveMemoryQaController? = rawMemoryController

    override fun onTerminate() {
        rawMemoryController?.close()
        rawMemoryController = null
        super.onTerminate()
    }
}

internal object RetroArchFreeUiQaMode {
    const val MARKER_FILE_NAME = "retroarch-free-ui-qa"
    const val SCENARIO_ASSET_PATH = "retroarch-free-ui-qa/raw-live-memory-scenarios.json"

    fun loadController(filesDirectory: File, readAsset: () -> ByteArray): RawLiveMemoryQaController? {
        if (!File(filesDirectory, MARKER_FILE_NAME).isFile) return null
        return try {
            RawLiveMemoryQaController(RawLiveMemoryScenarioLoader.decode(readAsset()))
        } catch (_: Exception) {
            null
        }
    }

    fun transportFactory(filesDirectory: File): (() -> NetworkCommandTransport)? {
        if (!File(filesDirectory, MARKER_FILE_NAME).isFile) return null
        return { ContentlessNetworkCommandTransport() }
    }

    private class ContentlessNetworkCommandTransport : NetworkCommandTransport {
        private val replies = ArrayDeque<ByteArray>()
        private var closed = false

        @Synchronized
        override fun send(payload: ByteArray) {
            check(!closed) { "network command transport is closed" }
            val command = payload.toString(Charsets.US_ASCII).trim()
            val response = when {
                command == "GET_STATUS" -> "GET_STATUS CONTENTLESS"
                command == "VERSION" -> "1.0.0-dualdex-qa"
                command.startsWith("GET_CONFIG_PARAM ") -> command
                command.startsWith("READ_CORE_MEMORY ") ->
                    "READ_CORE_MEMORY ${command.substringAfter("READ_CORE_MEMORY ").substringBefore(' ')} ERROR"
                else -> return
            }
            replies.add(response.toByteArray(Charsets.US_ASCII))
        }

        @Synchronized
        override fun poll(): ByteArray? {
            check(!closed) { "network command transport is closed" }
            return replies.pollFirst()
        }

        @Synchronized
        override fun close() {
            closed = true
            replies.clear()
        }
    }
}
