package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.parser.catalog.MapLighting
import com.enrpau.dualscreendex.parser.catalog.RenderedMapAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MapAssetRenderCacheTest {
    @Test
    fun coalescesConcurrentAndSuccessiveRendersForTheSameVariant() {
        val cache = MapAssetRenderCache(maxEncodedBytes = 32)
        val key = MapAssetRenderKey("rom", "local/1", "DAY")
        val renders = AtomicInteger()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<RenderedMapAsset?> {
                cache.getOrRender(key) {
                    renders.incrementAndGet()
                    started.countDown()
                    release.await()
                    RenderedMapAsset(byteArrayOf(1, 2, 3, 4), MapLighting.DAY)
                }
            }
            started.await()
            val second = executor.submit<RenderedMapAsset?> {
                cache.getOrRender(key) {
                    renders.incrementAndGet()
                    RenderedMapAsset(byteArrayOf(9), MapLighting.DAY)
                }
            }
            release.countDown()

            assertSame(first.get(), second.get())
            assertSame(first.get(), cache.getOrRender(key) { error("cached render should not run") })
            assertEquals(1, renders.get())
            assertEquals(2L, cache.stats().hits)
            assertEquals(1L, cache.stats().renders)
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun evictsLeastRecentlyUsedEncodedBytesAboveTheBudget() {
        val cache = MapAssetRenderCache(maxEncodedBytes = 10)
        val first = MapAssetRenderKey("rom", "first", "STATIC")
        val second = MapAssetRenderKey("rom", "second", "STATIC")
        var renders = 0

        cache.getOrRender(first) { renders++; RenderedMapAsset(ByteArray(6), null) }
        cache.getOrRender(second) { renders++; RenderedMapAsset(ByteArray(6), null) }
        cache.getOrRender(first) { renders++; RenderedMapAsset(ByteArray(6), null) }

        assertEquals(3, renders)
        assertEquals(1, cache.stats().entries)
        assertTrue(cache.stats().encodedBytes <= 10)
        assertEquals(2L, cache.stats().evictions)
    }
}
