package com.darkaxt.dualdex.retroarch

class RestartVerifier {
    @Volatile var restartRequired: Boolean = false
        private set
    private var observedDisconnect = false

    @Synchronized
    fun requireRestart(connection: RetroArchConnection) {
        restartRequired = true
        observedDisconnect = connection == RetroArchConnection.DISCONNECTED
    }

    @Synchronized
    fun observe(connection: RetroArchConnection): Boolean {
        if (!restartRequired) return connection.hasValidCommandStatus
        if (connection == RetroArchConnection.DISCONNECTED) {
            observedDisconnect = true
            return false
        }
        if (!connection.hasValidCommandStatus || !observedDisconnect) return false
        restartRequired = false
        observedDisconnect = false
        return true
    }
}
