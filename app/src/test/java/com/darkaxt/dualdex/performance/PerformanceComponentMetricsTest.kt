package com.darkaxt.dualdex.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PerformanceComponentMetricsTest {
    @Test
    fun `exports only available bounded component counters with stable names`() {
        val counters = PerformanceComponentMetrics(
            mapCacheEntries = 3,
            mapCacheEncodedBytes = 4096,
            mapCacheHits = 8,
            mapCacheRenders = 2,
            mapCacheEvictions = 1,
            activeWebViewSurfaces = 1,
            loopbackWorkerThreads = 4,
            loopbackActiveWorkers = 2,
            loopbackQueuedConnections = 1,
            loopbackActiveConnections = 3,
            mapperSnapshots = 5,
            mapperRetainedBytes = 2048,
        ).counters()

        assertEquals(3L, counters["mapCache.entries"])
        assertEquals(4096L, counters["mapCache.encodedBytes"])
        assertEquals(1L, counters["webView.activeSurfaces"])
        assertEquals(4L, counters["loopback.workerThreads"])
        assertEquals(3L, counters["loopback.activeConnections"])
        assertEquals(5L, counters["mapper.snapshots"])
        assertEquals(2048L, counters["mapper.retainedBytes"])
        assertFalse(counters.keys.any { it.contains("path", ignoreCase = true) })
    }

    @Test
    fun `omits unavailable components instead of inventing zero observations`() {
        val counters = PerformanceComponentMetrics(activeWebViewSurfaces = 0).counters()

        assertEquals(mapOf("webView.activeSurfaces" to 0L), counters)
    }
}
