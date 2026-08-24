package com.darkaxt.dualdex.performance

import android.os.Debug
import android.os.Process

class AndroidPerformanceSampler(
    private val javaHeap: () -> Pair<Long, Long> = {
        Runtime.getRuntime().let { runtime ->
            (runtime.totalMemory() - runtime.freeMemory()) to runtime.totalMemory()
        }
    },
    private val nativeHeapAllocatedBytes: () -> Long = Debug::getNativeHeapAllocatedSize,
    private val totalPssKilobytes: () -> Long = {
        Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss.toLong()
    },
    private val processCpuMillis: () -> Long = Process::getElapsedCpuTime,
    private val runtimeStats: () -> Map<String, String> = Debug::getRuntimeStats,
    private val threadCount: () -> Int = Thread::activeCount,
) : PerformanceMetricSampler {
    override fun sample(): PerformanceMetrics {
        val (used, committed) = javaHeap()
        val stats = runtimeStats()
        return PerformanceMetrics(
            javaHeapUsedBytes = used,
            javaHeapCommittedBytes = committed,
            nativeHeapAllocatedBytes = nativeHeapAllocatedBytes(),
            totalPssKilobytes = totalPssKilobytes(),
            processCpuMillis = processCpuMillis(),
            gcCount = stats[GC_COUNT_KEY]?.toLongOrNull(),
            gcTimeMillis = stats[GC_TIME_KEY]?.toLongOrNull(),
            threadCount = threadCount(),
        )
    }

    private companion object {
        const val GC_COUNT_KEY = "art.gc.gc-count"
        const val GC_TIME_KEY = "art.gc.gc-time"
    }
}
