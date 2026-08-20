package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

data class RasterRect(val x: Int, val y: Int, val width: Int, val height: Int)

data class RenderedMapAsset(
    val bytes: ByteArray,
    val effectiveLighting: MapLighting?,
)

object LocalMapRasterCodec {
    fun compress(indices: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        DeflaterOutputStream(output).use { it.write(indices) }
    }.toByteArray()

    fun inflate(asset: IndexedMapAsset): ByteArray = try {
        val expected = asset.pixelCount
        val output = ByteArray(expected)
        InflaterInputStream(ByteArrayInputStream(asset.compressedIndices)).use { input ->
            var offset = 0
            while (offset < output.size) {
                val read = input.read(output, offset, output.size - offset)
                require(read > 0) { "indexed map data ended before $expected pixels" }
                offset += read
            }
            require(input.read() == -1) { "indexed map data exceeds $expected pixels" }
        }
        output
    } catch (failure: IllegalArgumentException) {
        throw failure
    } catch (failure: Exception) {
        throw IllegalArgumentException("indexed map data is not valid zlib", failure)
    }
}

object LocalMapRasterRenderer {
    fun effectiveLighting(asset: IndexedMapAsset, requested: MapLighting): MapLighting =
        asset.lightingPolicy.resolve(requested)

    fun render(
        asset: IndexedMapAsset,
        requested: MapLighting,
        source: RasterRect = RasterRect(0, 0, asset.pixelWidth, asset.pixelHeight),
    ): RgbaSprite {
        require(source.x >= 0 && source.y >= 0 && source.width > 0 && source.height > 0)
        require(source.x.toLong() + source.width <= asset.pixelWidth.toLong())
        require(source.y.toLong() + source.height <= asset.pixelHeight.toLong())
        val indices = LocalMapRasterCodec.inflate(asset)
        val colors = asset.palettes[effectiveLighting(asset, requested)]
        val pixels = IntArray(source.width * source.height)
        repeat(source.height) { y ->
            repeat(source.width) { x ->
                val index = indices[(source.y + y) * asset.pixelWidth + source.x + x].toInt() and 0xff
                require(index in colors.indices) { "indexed map pixel $index has no palette color" }
                pixels[y * source.width + x] = colors[index]
            }
        }
        return RgbaSprite(source.width, source.height, pixels)
    }

    fun renderPng(asset: IndexedMapAsset, requested: MapLighting): RenderedMapAsset {
        val effective = effectiveLighting(asset, requested)
        return RenderedMapAsset(PngEncoder.encode(render(asset, requested)), effective)
    }
}

object LocalMapAssetRenderer {
    fun render(catalog: LocalMapCatalog, key: String, requested: MapLighting): RenderedMapAsset? =
        catalog.assets[key]?.let { RenderedMapAsset(it.bytes, null) }
            ?: catalog.indexedAssets[key]?.let { LocalMapRasterRenderer.renderPng(it, requested) }
}
