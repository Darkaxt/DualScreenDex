package com.darkaxt.dualdex

import com.darkaxt.dualdex.retroarch.ConfigParameter
import com.darkaxt.dualdex.retroarch.NetworkResponse
import com.darkaxt.dualdex.retroarch.RetroArchCommandPort
import com.darkaxt.dualdex.retroarch.RetroArchStatus
import com.darkaxt.dualdex.retroarch.SessionMonitor
import java.io.File

class RetroArchFreeUiQaApplication : DualDexApplication() {
    override fun sessionMonitorFactory(): (() -> SessionMonitor)? =
        RetroArchFreeUiQaMode.sessionMonitorFactory(filesDir)
}

internal object RetroArchFreeUiQaMode {
    const val MARKER_FILE_NAME = "retroarch-free-ui-qa"

    fun sessionMonitorFactory(filesDirectory: File): (() -> SessionMonitor)? {
        if (!File(filesDirectory, MARKER_FILE_NAME).isFile) return null
        return { SessionMonitor(ContentlessCommandPort) }
    }

    private object ContentlessCommandPort : RetroArchCommandPort {
        override fun requestStatus() = Unit

        override fun requestVersion() = Unit

        override fun requestConfig(parameter: ConfigParameter) = Unit

        override fun poll(): List<NetworkResponse> =
            listOf(NetworkResponse.Status(RetroArchStatus.Contentless))

        override fun close() = Unit
    }
}
