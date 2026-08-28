package com.enrpau.dualscreendex.parser.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

    @Test
    fun boundsLazyDiscoveryToRunningAndQueuedWork() {
        val discovered = AtomicInteger()
        val fourthDiscovered = CountDownLatch(1)
        val workersStarted = CountDownLatch(2)
        val release = CountDownLatch(1)
        val inputs = sequence {
            repeat(20) { value ->
                if (discovered.incrementAndGet() == 4) fourthDiscovered.countDown()
                yield(value)
            }
        }.asIterable()
        val caller = Executors.newSingleThreadExecutor()
        val future = caller.submit<List<Int>> {
            mapConcurrentlyOrdered(inputs, jobs = 2) { _, value ->
                workersStarted.countDown()
                check(release.await(5, TimeUnit.SECONDS)) { "bounded worker timed out" }
                value
            }
        }

        try {
            assertTrue("two workers did not start", workersStarted.await(5, TimeUnit.SECONDS))
            assertTrue("bounded input window was not filled", fourthDiscovered.await(5, TimeUnit.SECONDS))
            assertEquals(4, discovered.get())
            release.countDown()
            assertEquals((0 until 20).toList(), future.get(5, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            caller.shutdownNow()
        }
    }

    @Test
    fun slowFirstResultDoesNotPreventLaterInputsFromStarting() {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val fifthStarted = CountDownLatch(1)
        val caller = Executors.newSingleThreadExecutor()
        val future = caller.submit<List<Int>> {
            mapConcurrentlyOrdered(0 until 6, jobs = 2) { index, value ->
                if (index == 0) {
                    firstStarted.countDown()
                    check(releaseFirst.await(5, TimeUnit.SECONDS)) { "slow first worker timed out" }
                }
                if (index == 4) fifthStarted.countDown()
                value
            }
        }

        try {
            assertTrue("first input did not start", firstStarted.await(5, TimeUnit.SECONDS))
            assertTrue("later input remained blocked behind the first result", fifthStarted.await(2, TimeUnit.SECONDS))
            releaseFirst.countDown()
            assertEquals((0 until 6).toList(), future.get(5, TimeUnit.SECONDS))
        } finally {
            releaseFirst.countDown()
            caller.shutdownNow()
        }
    }

    @Test
    fun capsEffectiveWorkerConcurrencyDefensively() {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val workerLimit = 8
        val workersStarted = CountDownLatch(workerLimit)
        val release = CountDownLatch(1)
        val caller = Executors.newSingleThreadExecutor()
        val future = caller.submit<List<Int>> {
            mapConcurrentlyOrdered(0 until 20, jobs = Int.MAX_VALUE) { _, value ->
                val concurrent = active.incrementAndGet()
                peak.accumulateAndGet(concurrent, ::maxOf)
                workersStarted.countDown()
                try {
                    check(release.await(5, TimeUnit.SECONDS)) { "capped worker timed out" }
                    value
                } finally {
                    active.decrementAndGet()
                }
            }
        }

        try {
            assertTrue("capped workers did not start", workersStarted.await(5, TimeUnit.SECONDS))
            assertTrue("worker cap was exceeded: ${peak.get()}", peak.get() <= workerLimit)
            release.countDown()
            assertEquals((0 until 20).toList(), future.get(5, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            caller.shutdownNow()
        }
    }
}
