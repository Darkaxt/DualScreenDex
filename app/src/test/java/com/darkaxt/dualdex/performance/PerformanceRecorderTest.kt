package com.darkaxt.dualdex.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceRecorderTest {
    @Test
    fun `sampling and persistence can be dispatched outside the calling runtime lock`() {
        val pending = mutableListOf<() -> Unit>()
        val events = mutableListOf<PerformanceEvent>()
        var samples = 0
        val recorder = PerformanceRecorder(
            monotonicNanos = { 0L },
            wallClockMillis = { 0L },
            sessionIdFactory = { "async-session" },
            sampler = PerformanceMetricSampler {
                samples += 1
                PerformanceMetrics(javaHeapUsedBytes = 7L)
            },
            workDispatcher = PerformanceWorkDispatcher(pending::add),
            sinks = listOf(PerformanceEventSink(events::add)),
        )

        recorder.beginLoad("d".repeat(64), generation = 4)

        assertEquals(0, samples)
        assertTrue(events.isEmpty())
        pending.single().invoke()
        assertEquals(1, samples)
        assertEquals(PerformanceEventKind.LOAD_STARTED, events.single().kind)
    }

    @Test
    fun `records load stages cache decision and one-way game readiness without private identifiers`() {
        var monotonicNanos = 10_000_000L
        var wallClockMillis = 1_725_000_000_000L
        val events = mutableListOf<PerformanceEvent>()
        val recorder = PerformanceRecorder(
            monotonicNanos = { monotonicNanos },
            wallClockMillis = { wallClockMillis },
            sessionIdFactory = { "session-1" },
            sampler = PerformanceMetricSampler { PerformanceMetrics(javaHeapUsedBytes = 12L) },
            sinks = listOf(PerformanceEventSink(events::add)),
        )

        recorder.beginLoad("A".repeat(64), generation = 3)
        monotonicNanos += 5_000_000L
        recorder.cacheDecision("MISS_FILE_ABSENT")
        recorder.transitionStage("ROM_IDENTITY")
        monotonicNanos += 7_000_000L
        recorder.transitionStage("MAPS")
        monotonicNanos += 11_000_000L
        recorder.catalogReady()
        recorder.waitingForGameAccess()
        recorder.waitingForGameAccess()
        recorder.gameAccessReady()
        recorder.gameAccessReady()

        assertEquals(
            listOf(
                PerformanceEventKind.LOAD_STARTED,
                PerformanceEventKind.CACHE_DECISION,
                PerformanceEventKind.STAGE_FINISHED,
                PerformanceEventKind.STAGE_FINISHED,
                PerformanceEventKind.CATALOG_READY,
                PerformanceEventKind.WAITING_FOR_GAME_ACCESS,
                PerformanceEventKind.GAME_ACCESS_READY,
            ),
            events.map(PerformanceEvent::kind),
        )
        assertEquals("aaaaaaaaaaaa", events.first().romSha256Prefix)
        assertEquals("MISS_FILE_ABSENT", events[1].cacheDecision)
        assertEquals("ROM_IDENTITY", events[2].stage)
        assertEquals(7L, events[2].stageElapsedMillis)
        assertEquals("MAPS", events[3].stage)
        assertEquals(11L, events[3].stageElapsedMillis)
        assertEquals(12L, events.last().metrics.javaHeapUsedBytes)
        assertNull(events.last().failureType)
    }

    @Test
    fun `runtime heartbeat emits only the current newly observed minute bucket`() {
        var monotonicNanos = 0L
        val events = mutableListOf<PerformanceEvent>()
        val recorder = PerformanceRecorder(
            monotonicNanos = { monotonicNanos },
            wallClockMillis = { 1_725_000_000_000L },
            sessionIdFactory = { "session-2" },
            sampler = PerformanceMetricSampler { PerformanceMetrics() },
            sinks = listOf(PerformanceEventSink(events::add)),
        )

        recorder.beginLoad("b".repeat(64), generation = 2)
        recorder.catalogReady()
        monotonicNanos = 59_000_000_000L
        recorder.runtimeHeartbeat()
        monotonicNanos = 60_000_000_000L
        recorder.runtimeHeartbeat()
        recorder.runtimeHeartbeat()
        monotonicNanos = 180_000_000_000L
        recorder.runtimeHeartbeat()

        val runtime = events.filter { it.kind == PerformanceEventKind.RUNTIME_MINUTE }
        assertEquals(2, runtime.size)
        assertEquals(listOf(1L, 3L), runtime.map(PerformanceEvent::runtimeMinute))
    }

    @Test
    fun `diagnostic sink failure never fails the application workflow`() {
        val recorder = PerformanceRecorder(
            monotonicNanos = { 0L },
            wallClockMillis = { 0L },
            sessionIdFactory = { "session-3" },
            sampler = PerformanceMetricSampler { error("sampler unavailable") },
            sinks = listOf(PerformanceEventSink { error("storage unavailable") }),
        )

        recorder.beginLoad("c".repeat(64), generation = 1)
        recorder.cacheDecision("HIT")
        recorder.catalogReady()
        recorder.runtimeHeartbeat()
    }
}
