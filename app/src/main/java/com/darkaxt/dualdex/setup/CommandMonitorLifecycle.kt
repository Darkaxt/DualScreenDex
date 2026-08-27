package com.darkaxt.dualdex.setup

import com.darkaxt.dualdex.retroarch.SessionMonitor

internal class CommandMonitorLifecycle(
    private val factory: () -> SessionMonitor,
) : AutoCloseable {
    private var monitor: SessionMonitor? = null
    private var consecutiveFailures = 0

    @Synchronized
    fun monitor(): SessionMonitor = monitor ?: factory().also { monitor = it }

    @Synchronized
    fun recordSuccess() {
        consecutiveFailures = 0
    }

    @Synchronized
    fun recordFailure() {
        val failed = monitor
        monitor = null
        runCatching { failed?.close() }
        consecutiveFailures = (consecutiveFailures + 1).coerceAtMost(5)
    }

    @Synchronized
    fun nextDelayMillis(): Long = commandMonitorRetryDelayMillis(consecutiveFailures)

    @Synchronized
    override fun close() {
        val closing = monitor
        monitor = null
        runCatching { closing?.close() }
    }
}

internal fun commandMonitorRetryDelayMillis(consecutiveFailures: Int): Long {
    require(consecutiveFailures >= 0)
    if (consecutiveFailures == 0) return 2_000L
    val exponent = (consecutiveFailures - 1).coerceAtMost(4)
    return (2_000L shl exponent).coerceAtMost(30_000L)
}
