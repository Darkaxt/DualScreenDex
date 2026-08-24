package com.darkaxt.dualdex.performance

import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class BoundedPerformanceWorkDispatcher(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val executor: Executor = daemonExecutor(),
) : PerformanceWorkDispatcher {
    private val pending = ArrayDeque<() -> Unit>()
    private var draining = false

    init {
        require(capacity > 0) { "performance work capacity must be positive" }
    }

    override fun dispatch(work: () -> Unit) {
        val shouldSchedule = synchronized(this) {
            if (pending.size >= capacity) pending.removeFirst()
            pending.addLast(work)
            if (draining) {
                false
            } else {
                draining = true
                true
            }
        }
        if (shouldSchedule) {
            runCatching { executor.execute(::drain) }.onFailure {
                synchronized(this) {
                    pending.clear()
                    draining = false
                }
            }
        }
    }

    fun flush() {
        val completed = CountDownLatch(1)
        dispatch(completed::countDown)
        completed.await()
    }

    private fun drain() {
        while (true) {
            val work = synchronized(this) {
                if (pending.isEmpty()) {
                    draining = false
                    null
                } else {
                    pending.removeFirst()
                }
            } ?: return
            runCatching(work)
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 128

        private fun daemonExecutor(): Executor = Executors.newSingleThreadExecutor { work ->
            Thread(work, "dualdex-performance").apply { isDaemon = true }
        }
    }
}
