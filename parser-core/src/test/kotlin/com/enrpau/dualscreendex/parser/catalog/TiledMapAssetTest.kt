package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TiledMapAssetTest {
    @Test
    fun selfContainedDictionaryPreservesPixelsAlphaAndCrossTileCrops() {
        val raster = raster()
        val asset = TiledMapRasterCodec.encode(raster).validate()
        assertEquals(2, asset.tileCount)
        assertEquals(512, asset.storedPixelCount)
        assertEquals(raster, TiledLocalMapRasterRenderer.render(asset))
        val crop = RasterRect(9, 7, 35, 19)
        assertArrayEquals(IntArray(crop.width * crop.height) { i ->
            raster.argb[(crop.y + i / crop.width) * raster.width + crop.x + i % crop.width]
        }, TiledLocalMapRasterRenderer.render(asset, crop).argb)
        assertEquals(asset, TiledMapRasterCodec.encode(raster))
        assertEquals(asset.hashCode(), TiledMapRasterCodec.encode(raster).hashCode())
    }

    @Test
    fun sharedPngEndpointAndThemeReceiveTheUnchangedStaticPixels() {
        val raster = raster()
        val asset = TiledMapRasterCodec.encode(raster)
        val map = LocalMap("local/0102", "Generated", 0x0102, 64, 32, 4, 2, "local/0102/map")
        val catalog = LocalMapCatalog(maps = listOf(map), tiledAssets = mapOf(map.imageAssetKey to asset))
        val rendered = requireNotNull(LocalMapAssetRenderer.render(catalog, map.imageAssetKey, MapLighting.NIGHT))
        assertNull(rendered.effectiveLighting)
        assertEquals("STATIC", rendered.cacheVariant)
        assertArrayEquals(PngEncoder.encode(raster), rendered.bytes)
        val image = ImageIO.read(ByteArrayInputStream(rendered.bytes))
        assertArrayEquals(raster.argb, image.getRGB(0, 0, raster.width, raster.height, null, 0, raster.width))
        val theme = CatalogMaterializer.catalogThemeAssets(emptyMap(), TrainerAssetCatalog(), WorldMapCatalog(), catalog)
        assertEquals(listOf(raster), theme[CatalogThemeAssetClass.LOCAL_MAP])
        assertFalse(catalog.tiledAssets.isEmpty())
    }

    @Test
    fun themeCollectorBoundsMixedStaticBackingsWithoutChangingTheSelectedPixels() {
        val rasters = (0 until 40).map { seed ->
            RgbaSprite(32, 16, IntArray(512) { pixel -> 0xff000000.toInt() or (seed shl 16) or pixel })
        }
        val maps = rasters.indices.map { id -> LocalMap("local/$id", "Generated", id, 32, 16, 2, 1, "map/$id") }
        val pngs = maps.indices.filter { it % 2 == 0 }.associate { id ->
            maps[id].imageAssetKey to PngMapAsset(PngEncoder.encode(rasters[id]))
        }
        val tiled = maps.indices.filter { it % 2 != 0 }.associate { id ->
            maps[id].imageAssetKey to TiledMapRasterCodec.encode(rasters[id])
        }
        val catalog = LocalMapCatalog(maps = maps, assets = pngs, tiledAssets = tiled)
        val collected = CatalogMaterializer.catalogThemeAssets(emptyMap(), TrainerAssetCatalog(), WorldMapCatalog(), catalog)
            .getValue(CatalogThemeAssetClass.LOCAL_MAP)
        val expected = RomThemeMaterializer.selectAssets(maps.sortedBy { it.imageAssetKey }.asSequence().map { rasters[it.baseAreaId] })
        assertEquals(16, collected.size)
        assertEquals(expected, collected)
        val trainer = listOf(raster())
        assertEquals(
            RomThemeMaterializer.materialize(mapOf(CatalogThemeAssetClass.LOCAL_MAP to rasters, CatalogThemeAssetClass.TRAINER to trainer)),
            RomThemeMaterializer.materialize(collected.let { mapOf(CatalogThemeAssetClass.LOCAL_MAP to it, CatalogThemeAssetClass.TRAINER to trainer) }),
        )
    }

    @Test
    fun inconsistentMetadataOrDuplicateRasterOwnershipIsRejected() {
        val asset = TiledMapRasterCodec.encode(raster())
        val map = LocalMap("local/0102", "Generated", 0x0102, 64, 32, 4, 2, "local/0102/map")
        assertThrows(IllegalArgumentException::class.java) {
            LocalMapCatalog(listOf(map.copy(pixelWidth = 32, gridWidth = 2)), tiledAssets = mapOf(map.imageAssetKey to asset))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalMapCatalog(listOf(map), assets = mapOf(map.imageAssetKey to PngMapAsset(PngEncoder.encode(raster()))),
                tiledAssets = mapOf(map.imageAssetKey to asset))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalMapCatalog(tiledAssets = mapOf(map.imageAssetKey to asset))
        }
    }

    @Test
    fun invalidDimensionsDictionarySizesAndCropsFailBeforeAllocation() {
        val asset = TiledMapRasterCodec.encode(raster())
        listOf(
            asset.copy(pixelWidth = -16), asset.copy(pixelHeight = 0), asset.copy(pixelWidth = 17),
            asset.copy(pixelWidth = Int.MAX_VALUE, pixelHeight = Int.MAX_VALUE),
            asset.copy(pixelWidth = 4096, pixelHeight = 4096), asset.copy(tileCount = 0),
            asset.copy(tileCount = Int.MAX_VALUE), asset.copy(tileCount = 9),
        ).forEach { bad -> assertThrows(IllegalArgumentException::class.java) { bad.validate() } }
        listOf(RasterRect(-1, 0, 1, 1), RasterRect(63, 0, 2, 1), RasterRect(0, 0, Int.MAX_VALUE, 1))
            .forEach { crop -> assertThrows(IllegalArgumentException::class.java) { TiledLocalMapRasterRenderer.render(asset, crop) } }
    }

    @Test
    fun badTileReferencesAndMalformedCompressedStreamsAreRejected() {
        val asset = TiledMapRasterCodec.encode(raster())
        val decoded = TiledMapRasterCodec.decode(asset)
        val badGrid = decoded.grid.copyOf().also { it[0] = 2 }
        assertThrows(IllegalArgumentException::class.java) {
            asset.copy(compressedGrid = LocalMapRasterCodec.compress(badGrid)).validate()
        }
        val streams = listOf(
            byteArrayOf(), byteArrayOf(1, 2, 3), asset.compressedTiles.copyOf(asset.compressedTiles.size - 1),
            asset.compressedTiles + byteArrayOf(0), LocalMapRasterCodec.compress(decoded.tiles.copyOf(decoded.tiles.size - 1)),
            LocalMapRasterCodec.compress(decoded.tiles + byteArrayOf(0)),
        )
        streams.forEach { bytes ->
            assertThrows(IllegalArgumentException::class.java) { asset.copy(compressedTiles = bytes).validate() }
        }
        listOf(asset.compressedGrid.copyOf(asset.compressedGrid.size - 1), asset.compressedGrid + byteArrayOf(0),
            LocalMapRasterCodec.compress(decoded.grid + byteArrayOf(0)))
            .forEach { bytes -> assertThrows(IllegalArgumentException::class.java) { asset.copy(compressedGrid = bytes).validate() } }
    }

    @Test
    fun cancellationDuringDictionaryConstructionIsNotSwallowed() {
        var checkpoints = 0
        val token = ParserCancellationToken { if (++checkpoints == 3) throw ParserCancellationException() }
        assertThrows(ParserCancellationException::class.java) { TiledMapRasterCodec.encode(raster(), token) }
        assertEquals(3, checkpoints)
    }

    private fun raster(): RgbaSprite = RgbaSprite(64, 32, IntArray(64 * 32) { pixel ->
        val x = pixel % 64
        val y = pixel / 64
        val tile = (x / 16 + y / 16) % 2
        ((if (tile == 0) 0x80 else 0xff) shl 24) or ((x % 16) shl 16) or ((y % 16) shl 8) or tile
    })
}
