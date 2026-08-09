package com.darkaxt.dualdex.retroarch

class RestartVerifier {
    var restartRequired: Boolean = false
        private set
    private var observedDisconnect = false

    @Synchronized
    fun requireRestart(connection: RetroArchConnection) {
        restartRequired = true
        observedDisconnect = connection == RetroArchConnection.DISCONNECTED
    }

    @Synchronized
    fun observe(connection: RetroArchConnection): Boolean {
        if (!restartRequired) return connection != RetroArchConnection.DISCONNECTED
        if (connection == RetroArchConnection.DISCONNECTED) {
            observedDisconnect = true
            return false
        }
        if (!observedDisconnect) return false
        restartRequired = false
        observedDisconnect = false
        return true
    }
}
