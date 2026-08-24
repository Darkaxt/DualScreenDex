package com.darkaxt.dualdex.performance

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
    fun beginLoad(romSha256: String, generation: Int?) {
        val now = monotonicNanos()
        session = ActiveSession(
            id = sessionIdFactory(),
            romSha256Prefix = minimizedShaPrefix(romSha256),
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
            failureType = failure.javaClass.simpleName.takeIf(String::isNotBlank) ?: "Failure",
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
        val romSha256Prefix = active.romSha256Prefix
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
                    romSha256Prefix = romSha256Prefix,
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

    private fun minimizedShaPrefix(value: String): String? = value
        .lowercase()
        .takeIf { it.length == 64 && it.all { character -> character in '0'..'9' || character in 'a'..'f' } }
        ?.take(SHA_PREFIX_LENGTH)

    private data class ActiveSession(
        val id: String,
        val romSha256Prefix: String?,
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

    private companion object {
        const val SHA_PREFIX_LENGTH = 12
    }
}
