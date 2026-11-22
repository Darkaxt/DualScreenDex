package com.darkaxt.dualdex.performance

import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedPerformanceWorkDispatcherTest {
    @Test
    fun `queues work off caller and retains the newest bounded samples`() {
        val scheduled = mutableListOf<Runnable>()
        val completed = mutableListOf<Int>()
        val dispatcher = BoundedPerformanceWorkDispatcher(
            capacity = 2,
            executor = Executor(scheduled::add),
        )

        dispatcher.dispatch { completed += 1 }
        dispatcher.dispatch { completed += 2 }
        dispatcher.dispatch { completed += 3 }

        assertTrue(completed.isEmpty())
        assertEquals(1, scheduled.size)
        scheduled.single().run()
        assertEquals(listOf(2, 3), completed)
    }

    @Test
    fun `flush waits behind all queued work`() {
        val completed = mutableListOf<Int>()
        val dispatcher = BoundedPerformanceWorkDispatcher(
            capacity = 2,
            executor = Executor(Runnable::run),
        )

        dispatcher.dispatch { completed += 1 }
        dispatcher.flush()

        assertEquals(listOf(1), completed)
    }
}
