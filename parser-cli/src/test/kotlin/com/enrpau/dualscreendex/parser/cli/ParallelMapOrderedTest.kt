package com.enrpau.dualscreendex.parser.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ParallelMapOrderedTest {
    @Test
    fun runsWorkConcurrentlyAndReturnsResultsInInputOrder() {
        val started = CountDownLatch(2)
        val release = CountDownLatch(1)
        val caller = Executors.newSingleThreadExecutor()
        val future = caller.submit<List<Int>> {
            mapConcurrentlyOrdered(listOf(3, 1, 2), jobs = 2) { _, value ->
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS)) { "parallel worker timed out" }
                value * 10
            }
        }

        try {
            assertTrue("two workers did not start concurrently", started.await(5, TimeUnit.SECONDS))
            release.countDown()
            assertEquals(listOf(30, 10, 20), future.get(5, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            caller.shutdownNow()
        }
    }
}
