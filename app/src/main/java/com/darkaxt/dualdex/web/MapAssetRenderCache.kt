package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.parser.catalog.RenderedMapAsset
import java.util.LinkedHashMap
import java.util.concurrent.CompletableFuture

data class MapAssetRenderKey(
    val romSha256: String,
    val assetKey: String,
    val variant: String,
)

data class MapAssetRenderCacheStats(
    val entries: Int,
    val encodedBytes: Int,
    val hits: Long,
    val renders: Long,
    val evictions: Long,
)

class MapAssetRenderCache(
    private val maxEncodedBytes: Int = DEFAULT_MAX_ENCODED_BYTES,
) {
    private val entries = LinkedHashMap<MapAssetRenderKey, RenderedMapAsset>(16, 0.75f, true)
    private val inFlight = mutableMapOf<MapAssetRenderKey, CompletableFuture<RenderedMapAsset?>>()
    private var encodedBytes = 0
    private var hits = 0L
    private var renders = 0L
    private var evictions = 0L
    private var generation = 0L

    init {
        require(maxEncodedBytes > 0) { "map render cache budget must be positive" }
    }

    fun getOrRender(key: MapAssetRenderKey, render: () -> RenderedMapAsset?): RenderedMapAsset? {
        val pending: CompletableFuture<RenderedMapAsset?>
        val leader: Boolean
        val renderGeneration: Long
        synchronized(this) {
            entries[key]?.let { cached ->
                hits++
                return cached
            }
            val existing = inFlight[key]
            if (existing != null) {
                hits++
                pending = existing
                leader = false
            } else {
                pending = CompletableFuture()
                inFlight[key] = pending
                renders++
                leader = true
            }
            renderGeneration = generation
        }
        if (!leader) return pending.join()

        return try {
            val rendered = render()
            synchronized(this) {
                inFlight.remove(key)
                if (rendered != null && generation == renderGeneration && rendered.bytes.size <= maxEncodedBytes) {
                    entries.put(key, rendered)?.let { replaced -> encodedBytes -= replaced.bytes.size }
                    encodedBytes += rendered.bytes.size
                    evictToBudget()
                }
            }
            pending.complete(rendered)
            rendered
        } catch (failure: Throwable) {
            synchronized(this) { inFlight.remove(key) }
            pending.completeExceptionally(failure)
            throw failure
        }
    }

    @Synchronized
    fun stats(): MapAssetRenderCacheStats = MapAssetRenderCacheStats(
        entries = entries.size,
        encodedBytes = encodedBytes,
        hits = hits,
        renders = renders,
        evictions = evictions,
    )

    @Synchronized
    fun clear() {
        generation++
        entries.clear()
        encodedBytes = 0
    }

    private fun evictToBudget() {
        val iterator = entries.entries.iterator()
        while (encodedBytes > maxEncodedBytes && iterator.hasNext()) {
            encodedBytes -= iterator.next().value.bytes.size
            iterator.remove()
            evictions++
        }
    }

    private companion object {
        const val DEFAULT_MAX_ENCODED_BYTES = 32 * 1024 * 1024
    }
}
