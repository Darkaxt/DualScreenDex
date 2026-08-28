package com.darkaxt.dualdex.performance

import com.darkaxt.dualdex.live.ResolvedStateTraceEvent
import java.util.UUID
import java.util.concurrent.TimeUnit

class PerformanceRecorder(
    private val monotonicNanos: () -> Long = System::nanoTime,
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val sampler: PerformanceMetricSampler = PerformanceMetricSampler { PerformanceMetrics() },
    private val componentCounters: () -> Map<String, Long> = { emptyMap() },
    private val workDispatcher: PerformanceWorkDispatcher = PerformanceWorkDispatcher { work -> work() },
    private val sinks: List<PerformanceEventSink> = emptyList(),
) {
    private var session: ActiveSession? = null

    @Synchronized
    fun beginLoad(@Suppress("UNUSED_PARAMETER") romSha256: String, generation: Int?) {
        val now = monotonicNanos()
        session = ActiveSession(
            id = sessionIdFactory(),
            generation = generation,
            startedAtNanos = now,
            lastRuntimeMinute = 0L,
        )
        emit(PerformanceEventKind.LOAD_STARTED, now = now)
    }

    @Synchronized
    fun cacheDecision(decision: String) {
        if (session == null) return
        emit(PerformanceEventKind.CACHE_DECISION, cacheDecision = decision)
    }

    @Synchronized
    fun transitionStage(stage: String) {
        val active = session ?: return
        if (active.stage == stage) return
        val now = monotonicNanos()
        closeStage(active, now)
        active.stage = stage
        active.stageStartedAtNanos = now
    }

    @Synchronized
    fun catalogReady() {
        val active = session ?: return
        if (active.catalogReady) return
        val now = monotonicNanos()
        closeStage(active, now)
        active.catalogReady = true
        emit(PerformanceEventKind.CATALOG_READY, now = now)
    }

    @Synchronized
    fun waitingForGameAccess() {
        val active = session ?: return
        if (active.waitingForGameAccess || active.gameAccessReady) return
        active.waitingForGameAccess = true
        emit(PerformanceEventKind.WAITING_FOR_GAME_ACCESS)
    }

    @Synchronized
    fun gameAccessReady() {
        val active = session ?: return
        if (active.gameAccessReady) return
        active.gameAccessReady = true
        emit(PerformanceEventKind.GAME_ACCESS_READY)
    }

    @Synchronized
    fun loadFailed(failure: Throwable) {
        val active = session ?: return
        if (active.failed) return
        val now = monotonicNanos()
        closeStage(active, now)
        active.failed = true
        emit(
            PerformanceEventKind.LOAD_FAILED,
            now = now,
            failureType = PrivacySafeDiagnostics.failureCategory(failure),
        )
    }

    @Synchronized
    fun runtimeHeartbeat() {
        val active = session?.takeIf { it.catalogReady && !it.failed } ?: return
        val now = monotonicNanos()
        val minute = TimeUnit.NANOSECONDS.toMinutes((now - active.startedAtNanos).coerceAtLeast(0L))
        if (minute <= 0L || minute <= active.lastRuntimeMinute) return
        active.lastRuntimeMinute = minute
        emit(PerformanceEventKind.RUNTIME_MINUTE, now = now, runtimeMinute = minute)
    }

    @Synchronized
    fun stateChanged(stateChange: ResolvedStateTraceEvent) {
        val active = session ?: return
        val now = monotonicNanos()
        val event = PerformanceEvent(
            sessionId = active.id,
            wallClockEpochMillis = wallClockMillis(),
            elapsedMillis = TimeUnit.NANOSECONDS.toMillis((now - active.startedAtNanos).coerceAtLeast(0L)),
            kind = PerformanceEventKind.STATE_CHANGED,
            generation = active.generation,
            stateChange = stateChange,
        )
        runCatching {
            workDispatcher.dispatch {
                sinks.forEach { sink -> runCatching { sink.append(event) } }
            }
        }
    }

    private fun closeStage(active: ActiveSession, now: Long) {
        val stage = active.stage ?: return
        val startedAt = active.stageStartedAtNanos ?: now
        active.stage = null
        active.stageStartedAtNanos = null
        emit(
            PerformanceEventKind.STAGE_FINISHED,
            now = now,
            stage = stage,
            stageElapsedMillis = TimeUnit.NANOSECONDS.toMillis((now - startedAt).coerceAtLeast(0L)),
        )
    }

    private fun emit(
        kind: PerformanceEventKind,
        now: Long = monotonicNanos(),
        stage: String? = null,
        stageElapsedMillis: Long? = null,
        runtimeMinute: Long? = null,
        cacheDecision: String? = null,
        failureType: String? = null,
    ) {
        val active = session ?: return
        val sessionId = active.id
        val wallClockEpochMillis = wallClockMillis()
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis((now - active.startedAtNanos).coerceAtLeast(0L))
        val generation = active.generation
        runCatching {
            workDispatcher.dispatch {
                val sampled = runCatching(sampler::sample).getOrDefault(PerformanceMetrics())
                val counters = runCatching(componentCounters).getOrDefault(emptyMap())
                val event = PerformanceEvent(
                    sessionId = sessionId,
                    wallClockEpochMillis = wallClockEpochMillis,
                    elapsedMillis = elapsedMillis,
                    kind = kind,
                    generation = generation,
                    stage = stage,
                    stageElapsedMillis = stageElapsedMillis,
                    runtimeMinute = runtimeMinute,
                    cacheDecision = cacheDecision,
                    failureType = failureType,
                    metrics = sampled.copy(counters = counters.toSortedMap()),
                )
                sinks.forEach { sink -> runCatching { sink.append(event) } }
            }
        }
    }

    private data class ActiveSession(
        val id: String,
        val generation: Int?,
        val startedAtNanos: Long,
        var stage: String? = null,
        var stageStartedAtNanos: Long? = null,
        var lastRuntimeMinute: Long,
        var catalogReady: Boolean = false,
        var waitingForGameAccess: Boolean = false,
        var gameAccessReady: Boolean = false,
        var failed: Boolean = false,
    )
}
