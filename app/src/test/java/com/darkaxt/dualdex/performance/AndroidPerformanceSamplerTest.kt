package com.darkaxt.dualdex.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidPerformanceSamplerTest {
    @Test
    fun `samples process metrics and parses available ART counters`() {
        val sampler = AndroidPerformanceSampler(
            javaHeap = { 30L to 80L },
            nativeHeapAllocatedBytes = { 20L },
            totalPssKilobytes = { 1234L },
            processCpuMillis = { 77L },
            runtimeStats = {
                mapOf(
                    "art.gc.gc-count" to "9",
                    "art.gc.gc-time" to "45",
                )
            },
            threadCount = { 6 },
        )

        assertEquals(
            PerformanceMetrics(
                javaHeapUsedBytes = 30L,
                javaHeapCommittedBytes = 80L,
                nativeHeapAllocatedBytes = 20L,
                totalPssKilobytes = 1234L,
                processCpuMillis = 77L,
                gcCount = 9L,
                gcTimeMillis = 45L,
                threadCount = 6,
            ),
            sampler.sample(),
        )
    }

    @Test
    fun `omits malformed optional ART counters instead of failing sampling`() {
        val sampler = AndroidPerformanceSampler(
            javaHeap = { 1L to 2L },
            nativeHeapAllocatedBytes = { 3L },
            totalPssKilobytes = { 4L },
            processCpuMillis = { 5L },
            runtimeStats = { mapOf("art.gc.gc-count" to "unknown") },
            threadCount = { 6 },
        )

        val metrics = sampler.sample()

        assertNull(metrics.gcCount)
        assertNull(metrics.gcTimeMillis)
    }
}
