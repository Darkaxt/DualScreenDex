package com.darkaxt.dualdex.performance

data class PerformanceComponentMetrics(
    val mapCacheEntries: Int? = null,
    val mapCacheEncodedBytes: Int? = null,
    val mapCacheHits: Long? = null,
    val mapCacheRenders: Long? = null,
    val mapCacheEvictions: Long? = null,
    val activeWebViewSurfaces: Int? = null,
    val loopbackWorkerThreads: Int? = null,
    val loopbackActiveWorkers: Int? = null,
    val loopbackQueuedConnections: Int? = null,
    val loopbackActiveConnections: Int? = null,
    val mapperSnapshots: Int? = null,
    val mapperRetainedBytes: Long? = null,
    val areaGuideProjections: Long? = null,
    val areaGuideProjectionCpuNanos: Long? = null,
    val areaGuideRetainedItems: Long? = null,
) {
    fun counters(): Map<String, Long> = buildMap {
        mapCacheEntries?.let { put("mapCache.entries", it.toLong()) }
        mapCacheEncodedBytes?.let { put("mapCache.encodedBytes", it.toLong()) }
        mapCacheHits?.let { put("mapCache.hits", it) }
        mapCacheRenders?.let { put("mapCache.renders", it) }
        mapCacheEvictions?.let { put("mapCache.evictions", it) }
        activeWebViewSurfaces?.let { put("webView.activeSurfaces", it.toLong()) }
        loopbackWorkerThreads?.let { put("loopback.workerThreads", it.toLong()) }
        loopbackActiveWorkers?.let { put("loopback.activeWorkers", it.toLong()) }
        loopbackQueuedConnections?.let { put("loopback.queuedConnections", it.toLong()) }
        loopbackActiveConnections?.let { put("loopback.activeConnections", it.toLong()) }
        mapperSnapshots?.let { put("mapper.snapshots", it.toLong()) }
        mapperRetainedBytes?.let { put("mapper.retainedBytes", it) }
        areaGuideProjections?.let { put("areaGuide.projections", it) }
        areaGuideProjectionCpuNanos?.let { put("areaGuide.projectionCpuNanos", it) }
        areaGuideRetainedItems?.let { put("areaGuide.retainedItems", it) }
    }
}
