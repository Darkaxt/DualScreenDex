package com.enrpau.dualscreendex.parser.catalog

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalMapRasterRendererTest {
    private val palettes = MapLightingPalettes(
        morning = IntArray(32) { 0xff100000.toInt() + it },
        day = IntArray(32) { 0xff200000.toInt() + it },
        night = IntArray(32) { 0xff300000.toInt() + it },
        dark = IntArray(32) { 0xff400000.toInt() + it },
    )
    private val indices = byteArrayOf(0, 1, 4, 7, 8, 31)

    @Test
    fun compressedIndicesRoundTripWithoutRetainingTheRawSurface() {
        val compressed = LocalMapRasterCodec.compress(indices)
        val asset = IndexedMapAsset(3, 2, compressed, LocalMapLightingPolicy.AUTO, palettes)

        assertArrayEquals(indices, LocalMapRasterCodec.inflate(asset))
        assertEquals(6, asset.pixelCount)
        assertFalse(compressed.contentEquals(indices))
    }

    @Test
    fun autoPolicyUsesTheRequestedPaletteAndClippingMatchesTheFullRaster() {
        val asset = IndexedMapAsset(
            3,
            2,
            LocalMapRasterCodec.compress(indices),
            LocalMapLightingPolicy.AUTO,
            palettes,
        )

        val full = LocalMapRasterRenderer.render(asset, MapLighting.NIGHT)
        val clipped = LocalMapRasterRenderer.render(
            asset,
            MapLighting.NIGHT,
            RasterRect(1, 0, 2, 2),
        )

        assertArrayEquals(
            intArrayOf(
                palettes.night[0],
                palettes.night[1],
                palettes.night[4],
                palettes.night[7],
                palettes.night[8],
                palettes.night[31],
            ),
            full.argb,
        )
        assertArrayEquals(
            intArrayOf(palettes.night[1], palettes.night[4], palettes.night[8], palettes.night[31]),
            clipped.argb,
        )
    }

    @Test
    fun everyExplicitPolicyOverridesTheRequestedGameLighting() {
        MapLighting.entries.forEach { forced ->
            val requested = MapLighting.entries.first { it != forced }
            val asset = IndexedMapAsset(
                3,
                2,
                LocalMapRasterCodec.compress(indices),
                LocalMapLightingPolicy.valueOf(forced.name),
                palettes,
            )

            val rendered = LocalMapRasterRenderer.renderPng(asset, requested)

            assertEquals(forced, rendered.effectiveLighting)
            assertArrayEquals(
                LocalMapRasterRenderer.render(asset, forced).argb,
                LocalMapRasterRenderer.render(asset, requested).argb,
            )
        }
    }

    @Test
    fun malformedCompressionPixelBoundsAndOutOfBoundsClipsFailClosed() {
        val malformed = IndexedMapAsset(
            3,
            2,
            byteArrayOf(1, 2, 3),
            LocalMapLightingPolicy.AUTO,
            palettes,
        )
        val tooShort = IndexedMapAsset(
            3,
            2,
            LocalMapRasterCodec.compress(indices.copyOf(5)),
            LocalMapLightingPolicy.AUTO,
            palettes,
        )
        val tooLong = IndexedMapAsset(
            3,
            2,
            LocalMapRasterCodec.compress(indices.copyOf(7)),
            LocalMapLightingPolicy.AUTO,
            palettes,
        )
        val invalidDomain = IndexedMapAsset(
            3,
            2,
            LocalMapRasterCodec.compress(indices.copyOf().also { it[0] = 32 }),
            LocalMapLightingPolicy.AUTO,
            palettes,
        )
        val valid = IndexedMapAsset(
            3,
            2,
            LocalMapRasterCodec.compress(indices),
            LocalMapLightingPolicy.AUTO,
            palettes,
        )

        listOf(malformed, tooShort, tooLong, invalidDomain).forEach { asset ->
            assertThrows(IllegalArgumentException::class.java) { asset.validate() }
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalMapRasterRenderer.render(valid, MapLighting.DAY, RasterRect(2, 1, 2, 1))
        }
    }

    @Test
    fun catalogRequiresEachMapKeyInExactlyOneAssetStore() {
        val map = LocalMap("local/0001", "Test", 1, 48, 32, 3, 2, "local/0001/map")
        val indexed = IndexedMapAsset(
            48,
            32,
            LocalMapRasterCodec.compress(ByteArray(48 * 32)),
            LocalMapLightingPolicy.AUTO,
            palettes,
        )
        val png = PngMapAsset(PNG_SIGNATURE)

        LocalMapCatalog(listOf(map), indexedAssets = mapOf(map.imageAssetKey to indexed)).validate()
        assertThrows(IllegalArgumentException::class.java) {
            LocalMapCatalog(
                listOf(map),
                assets = mapOf(map.imageAssetKey to png),
                indexedAssets = mapOf(map.imageAssetKey to indexed),
            ).validate()
        }
    }

    @Test
    fun staticAssetRenderingReturnsTheOriginalPngBytes() {
        val key = "local/0001/map"
        val bytes = PNG_SIGNATURE + byteArrayOf(1, 2, 3)
        val catalog = LocalMapCatalog(
            maps = listOf(LocalMap("local/0001", "Test", 1, 16, 16, 1, 1, key)),
            assets = mapOf(key to PngMapAsset(bytes)),
        )

        val rendered = requireNotNull(LocalMapAssetRenderer.render(catalog, key, MapLighting.NIGHT))

        assertArrayEquals(bytes, rendered.bytes)
        assertEquals(null, rendered.effectiveLighting)
    }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    }
}
