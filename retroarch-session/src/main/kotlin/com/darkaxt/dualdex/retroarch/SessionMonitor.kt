package com.darkaxt.dualdex.retroarch

enum class RetroArchConnection { DISCONNECTED, CONTENTLESS, PLAYING, PAUSED, MALFORMED }

data class SessionMonitorState(
    val sequence: Long = 0,
    val connection: RetroArchConnection = RetroArchConnection.DISCONNECTED,
    val lastStatus: RetroArchStatus? = null,
    val version: String? = null,
    val savefileDirectory: String? = null,
    val savestateDirectory: String? = null,
    val systemDirectory: String? = null,
    val error: String? = null,
)

class SessionMonitor(
    private val port: RetroArchCommandPort,
    private val missedHeartbeatLimit: Int = 3,
) : AutoCloseable {
    private var state = SessionMonitorState()
    private var missedHeartbeats = 0

    init {
        require(missedHeartbeatLimit > 0) { "missed heartbeat limit must be positive" }
    }

    @Synchronized
    fun heartbeat(): SessionMonitorState {
        val responses = port.poll()
        state = responses.fold(state.copy(sequence = state.sequence + 1)) { current, response ->
            when (response) {
                is NetworkResponse.Status -> current.withStatus(response.value)
                is NetworkResponse.Config -> when (response.parameter) {
                    ConfigParameter.SAVEFILE_DIRECTORY -> current.copy(savefileDirectory = response.value)
                    ConfigParameter.SAVESTATE_DIRECTORY -> current.copy(savestateDirectory = response.value)
                    ConfigParameter.SYSTEM_DIRECTORY -> current.copy(systemDirectory = response.value)
                }
                is NetworkResponse.Version -> current.copy(version = response.value)
                is NetworkResponse.Unknown -> current
            }
        }
        if (responses.isEmpty()) missedHeartbeats += 1 else missedHeartbeats = 0
        if (missedHeartbeats >= missedHeartbeatLimit) {
            state = state.copy(connection = RetroArchConnection.DISCONNECTED, error = null)
        }

        port.requestStatus()
        port.requestConfig(ConfigParameter.SAVEFILE_DIRECTORY)
        return state
    }

    @Synchronized
    fun snapshot(): SessionMonitorState = state

    override fun close() = port.close()

    private fun SessionMonitorState.withStatus(status: RetroArchStatus): SessionMonitorState = when (status) {
        RetroArchStatus.Contentless -> copy(
            connection = RetroArchConnection.CONTENTLESS,
            lastStatus = status,
            error = null,
        )
        is RetroArchStatus.Running -> copy(
            connection = if (status.paused) RetroArchConnection.PAUSED else RetroArchConnection.PLAYING,
            lastStatus = status,
            error = null,
        )
        is RetroArchStatus.Malformed -> copy(
            connection = RetroArchConnection.MALFORMED,
            lastStatus = status,
            error = status.reason,
        )
    }
}
