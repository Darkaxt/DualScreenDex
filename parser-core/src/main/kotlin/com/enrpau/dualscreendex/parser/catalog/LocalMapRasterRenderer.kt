package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import com.enrpau.dualscreendex.parser.sprite.TileRenderer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

data class RasterRect(val x: Int, val y: Int, val width: Int, val height: Int)

data class RenderedMapAsset(
    val bytes: ByteArray,
    val effectiveLighting: MapLighting?,
    val cacheVariant: String = effectiveLighting?.name ?: "STATIC",
)

object LocalMapRasterCodec {
    fun compress(indices: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        DeflaterOutputStream(output).use { it.write(indices) }
    }.toByteArray()

    fun inflate(asset: IndexedMapAsset): ByteArray = inflate(asset.compressedIndices, asset.pixelCount)

    fun inflate(asset: TimedIndexedMapAsset): ByteArray = inflate(asset.compressedIndices, asset.pixelCount)

    private fun inflate(compressed: ByteArray, expected: Int): ByteArray = try {
        val output = ByteArray(expected)
        InflaterInputStream(ByteArrayInputStream(compressed)).use { input ->
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
        requireSource(source, asset.pixelWidth, asset.pixelHeight)
        val indices = LocalMapRasterCodec.inflate(asset)
        val colors = asset.palettes[effectiveLighting(asset, requested)]
        return renderIndices(indices, asset.pixelWidth, source) { index ->
            require(index in colors.indices) { "indexed map pixel $index has no palette color" }
            colors[index]
        }
    }

    fun renderPng(asset: IndexedMapAsset, requested: MapLighting): RenderedMapAsset {
        val effective = effectiveLighting(asset, requested)
        return RenderedMapAsset(PngEncoder.encode(render(asset, requested)), effective)
    }

    internal fun requireSource(source: RasterRect, pixelWidth: Int, pixelHeight: Int) {
        require(source.x >= 0 && source.y >= 0 && source.width > 0 && source.height > 0)
        require(source.x.toLong() + source.width <= pixelWidth.toLong())
        require(source.y.toLong() + source.height <= pixelHeight.toLong())
    }

    internal fun renderIndices(
        indices: ByteArray,
        sourceWidth: Int,
        source: RasterRect,
        color: (Int) -> Int,
    ): RgbaSprite {
        val pixels = IntArray(source.width * source.height)
        repeat(source.height) { y ->
            repeat(source.width) { x ->
                val index = indices[(source.y + y) * sourceWidth + source.x + x].toInt() and 0xff
                pixels[y * source.width + x] = color(index)
            }
        }
        return RgbaSprite(source.width, source.height, pixels)
    }
}

object TimedLocalMapRasterRenderer {
    fun render(
        asset: TimedIndexedMapAsset,
        time: MapTimeOfDay,
        source: RasterRect = RasterRect(0, 0, asset.pixelWidth, asset.pixelHeight),
    ): RgbaSprite {
        LocalMapRasterRenderer.requireSource(source, asset.pixelWidth, asset.pixelHeight)
        val indices = LocalMapRasterCodec.inflate(asset)
        val colors = renderPalette(asset, time)
        return LocalMapRasterRenderer.renderIndices(indices, asset.pixelWidth, source) { index -> colors[index] }
    }

    fun renderPng(asset: TimedIndexedMapAsset, time: MapTimeOfDay): RenderedMapAsset = RenderedMapAsset(
        bytes = PngEncoder.encode(render(asset, time)),
        effectiveLighting = null,
        cacheVariant = "TIME-${time.minuteOfDay}",
    )

    internal fun renderPalette(asset: TimedIndexedMapAsset, time: MapTimeOfDay): IntArray {
        val blend = blendState(asset.paletteModel, time)
        val colors = applyAlternatePalettes(asset, blend.alternateWeight)
        repeat(MAP_PALETTES) { palette ->
            val start = palette * COLORS_PER_PALETTE
            val alternateLightColor = colors[start]
            repeat(COLORS_PER_PALETTE - 1) { index ->
                val source = colors[start + index + 1]
                val color0 = applyBlend(source, alternateLightColor, blend.first)
                val color1 = applyBlend(source, alternateLightColor, blend.second)
                colors[start + index + 1] = weighted(color0, color1, blend.weight)
            }
        }
        return IntArray(colors.size) { index -> TileRenderer.bgr555ToArgb(colors[index] and 0x7FFF, false) }
    }

    private fun applyAlternatePalettes(asset: TimedIndexedMapAsset, weight: Int): IntArray =
        asset.baseColors.copyOf().also { colors ->
            repeat(MAP_PALETTES) { palette ->
                if (asset.alternatePaletteMask and (1 shl palette) == 0) return@repeat
                val start = palette * COLORS_PER_PALETTE
                repeat(COLORS_PER_PALETTE - 1) { index ->
                    val colorIndex = start + index + 1
                    colors[colorIndex] = weighted(
                        asset.baseColors[colorIndex],
                        asset.alternateColors[colorIndex],
                        weight,
                    ) or (asset.baseColors[colorIndex] and LIGHT_MARKER)
                }
            }
        }

    private fun blendState(model: MapTimePaletteModel, time: MapTimeOfDay): TimeBlendState {
        val minute = time.minuteOfDay
        return when {
            minute < 4 * 60 -> TimeBlendState(model.night, model.night, 256, 0)
            minute < 7 * 60 -> transition(model.night, model.twilight, minute, 4 * 60, 7 * 60, false)
            minute < 10 * 60 -> transition(model.twilight, model.day, minute, 7 * 60, 10 * 60, true)
            minute < 18 * 60 -> TimeBlendState(model.day, model.day, 256, 256)
            minute < 20 * 60 -> transition(model.day, model.twilight, minute, 18 * 60, 20 * 60, true)
            minute < 22 * 60 -> transition(model.twilight, model.night, minute, 20 * 60, 22 * 60, false)
            else -> TimeBlendState(model.night, model.night, 256, 0)
        }
    }

    private fun transition(
        first: MapTimeBlend,
        second: MapTimeBlend,
        minute: Int,
        start: Int,
        end: Int,
        daySide: Boolean,
    ): TimeBlendState {
        val weight = 256 - 256 * (minute - start) / (end - start)
        val alternateWeight = if (daySide) {
            if (start == 18 * 60) weight / 2 + 128 else (256 - weight) / 2 + 128
        } else {
            if (start == 4 * 60) (256 - weight) / 2 else weight / 2
        }
        return TimeBlendState(first, second, weight, alternateWeight)
    }

    private fun applyBlend(source: Int, alternateLightColor: Int, settings: MapTimeBlend): Int {
        val sourceChannels = channels(source)
        if (source and LIGHT_MARKER != 0) {
            val target = if (alternateLightColor and LIGHT_MARKER != 0) {
                alternateLightColor
            } else {
                DEFAULT_LIGHT_COLOR
            }
            return blendChannels(sourceChannels, channels(target), if (settings.tint) 16 else settings.coefficient * 2)
        }
        if (settings.tint) {
            val red = settings.blendColor and 0xff
            val green = settings.blendColor ushr 8 and 0xff
            val blue = settings.blendColor ushr 16 and 0xff
            return rgb(
                minOf(31, red * sourceChannels.red ushr 8),
                minOf(31, green * sourceChannels.green ushr 8),
                minOf(31, blue * sourceChannels.blue ushr 8),
            )
        }
        return blendChannels(sourceChannels, channels(settings.blendColor), settings.coefficient * 2)
    }

    private fun blendChannels(source: Channels, target: Channels, coefficient: Int): Int = rgb(
        source.red + ((target.red - source.red) * coefficient shr 5),
        source.green + ((target.green - source.green) * coefficient shr 5),
        source.blue + ((target.blue - source.blue) * coefficient shr 5),
    )

    private fun weighted(first: Int, second: Int, firstWeight: Int): Int {
        val firstChannels = channels(first)
        val secondChannels = channels(second)
        return rgb(
            secondChannels.red + ((firstChannels.red - secondChannels.red) * firstWeight shr 8),
            secondChannels.green + ((firstChannels.green - secondChannels.green) * firstWeight shr 8),
            secondChannels.blue + ((firstChannels.blue - secondChannels.blue) * firstWeight shr 8),
        )
    }

    private fun channels(color: Int): Channels = Channels(
        red = color and 31,
        green = color ushr 5 and 31,
        blue = color ushr 10 and 31,
    )

    private fun rgb(red: Int, green: Int, blue: Int): Int = red or (green shl 5) or (blue shl 10)

    private data class Channels(val red: Int, val green: Int, val blue: Int)

    private data class TimeBlendState(
        val first: MapTimeBlend,
        val second: MapTimeBlend,
        val weight: Int,
        val alternateWeight: Int,
    )

    private const val COLORS_PER_PALETTE = 16
    private const val MAP_PALETTES = 13
    private const val LIGHT_MARKER = 0x8000
    private const val DEFAULT_LIGHT_COLOR = 0x3F9F
}

object LocalMapAssetRenderer {
    fun render(
        catalog: LocalMapCatalog,
        key: String,
        requested: MapLighting,
        time: MapTimeOfDay? = null,
    ): RenderedMapAsset? = catalog.assets[key]?.let { RenderedMapAsset(it.bytes, null) }
        ?: catalog.indexedAssets[key]?.let { LocalMapRasterRenderer.renderPng(it, requested) }
        ?: catalog.timedAssets[key]?.let {
            TimedLocalMapRasterRenderer.renderPng(it, time ?: MapTimeOfDay(DEFAULT_HOUR, 0))
        }

    private const val DEFAULT_HOUR = 12
}
