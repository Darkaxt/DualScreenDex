package com.darkaxt.dualdex.performance

import com.darkaxt.dualdex.live.ResolvedStateTraceEvent

enum class PerformanceEventKind {
    LOAD_STARTED,
    CACHE_DECISION,
    STAGE_FINISHED,
    CATALOG_READY,
    WAITING_FOR_GAME_ACCESS,
    GAME_ACCESS_READY,
    LOAD_FAILED,
    RUNTIME_MINUTE,
    STATE_CHANGED,
}

data class PerformanceMetrics(
    val javaHeapUsedBytes: Long? = null,
    val javaHeapCommittedBytes: Long? = null,
    val nativeHeapAllocatedBytes: Long? = null,
    val totalPssKilobytes: Long? = null,
    val processCpuMillis: Long? = null,
    val gcCount: Long? = null,
    val gcTimeMillis: Long? = null,
    val threadCount: Int? = null,
    val counters: Map<String, Long> = emptyMap(),
)

data class PerformanceEvent(
    val schemaVersion: Int = PERFORMANCE_SCHEMA_VERSION,
    val sessionId: String,
    val wallClockEpochMillis: Long,
    val elapsedMillis: Long,
    val kind: PerformanceEventKind,
    val romSha256Prefix: String? = null,
    val generation: Int? = null,
    val stage: String? = null,
    val stageElapsedMillis: Long? = null,
    val runtimeMinute: Long? = null,
    val cacheDecision: String? = null,
    val failureType: String? = null,
    val stateChange: ResolvedStateTraceEvent? = null,
    val metrics: PerformanceMetrics = PerformanceMetrics(),
)

fun interface PerformanceMetricSampler {
    fun sample(): PerformanceMetrics
}

fun interface PerformanceEventSink {
    fun append(event: PerformanceEvent)
}

fun interface PerformanceWorkDispatcher {
    fun dispatch(work: () -> Unit)
}

const val PERFORMANCE_SCHEMA_VERSION = 2
