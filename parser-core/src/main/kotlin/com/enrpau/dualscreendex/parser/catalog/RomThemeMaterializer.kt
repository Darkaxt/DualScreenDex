package com.enrpau.dualscreendex.parser.catalog

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object RomThemeMaterializer {
    fun materialize(
        assets: Map<CatalogThemeAssetClass, List<RgbaSprite>>,
        directPalettes: List<DirectCatalogThemePalette> = emptyList(),
    ): CatalogTheme {
        val validDirect = directPalettes.filter(::validDirectPalette)
        if (validDirect.size == 1 && validDirect.single().colorModel == CatalogThemePaletteModel.RGB) {
            return buildTheme(
                CatalogThemeMethod.DIRECT_UI_PALETTE,
                setOf(CatalogThemeAssetClass.INTERFACE),
                histogram(validDirect.single().colors),
            ) ?: CatalogTheme.neutral()
        }

        val classHistograms = assets.entries
            .asSequence()
            .filter { (assetClass, _) -> assetClass != CatalogThemeAssetClass.INTERFACE }
            .mapNotNull { (assetClass, sprites) -> classHistogram(sprites)?.let { assetClass to it } }
            .toMap()
        if (classHistograms.size < MINIMUM_ASSET_CLASSES) return CatalogTheme.neutral()

        val combined = linkedMapOf<Int, Double>()
        classHistograms.toSortedMap(compareBy(CatalogThemeAssetClass::ordinal)).values.forEach { histogram ->
            val total = histogram.values.sum().takeIf { it > 0.0 } ?: return@forEach
            histogram.forEach { (rgb, count) ->
                combined[rgb] = combined.getOrDefault(rgb, 0.0) + count / total
            }
        }
        return buildTheme(
            CatalogThemeMethod.MULTI_ASSET_QUANTIZATION,
            classHistograms.keys.toSortedSet(compareBy(CatalogThemeAssetClass::ordinal)),
            combined,
        ) ?: CatalogTheme.neutral()
    }

    private fun validDirectPalette(palette: DirectCatalogThemePalette): Boolean =
        palette.colors.size >= MINIMUM_DIRECT_COLORS &&
            palette.colors.all { it in 0..0xFFFFFF } &&
            palette.colors.distinct().size >= MINIMUM_DIRECT_COLORS

    private fun classHistogram(sprites: List<RgbaSprite>): Map<Int, Double>? {
        val selected = sprites.distinctBy(::assetFingerprint).sortedBy(::assetFingerprint).take(MAX_ASSETS_PER_CLASS)
        if (selected.isEmpty()) return null
        val aggregate = linkedMapOf<Int, Double>()
        selected.forEach { sprite ->
            val sampled = sample(sprite)
            if (sampled.isEmpty()) return@forEach
            val assetHistogram = histogram(sampled)
            val total = assetHistogram.values.sum()
            assetHistogram.forEach { (rgb, count) ->
                aggregate[rgb] = aggregate.getOrDefault(rgb, 0.0) + count / total
            }
        }
        if (aggregate.isEmpty()) return null
        val total = aggregate.values.sum()
        return aggregate.mapValues { (_, weight) -> weight / total }
    }

    private fun sample(sprite: RgbaSprite): List<Int> {
        val sampleCount = min(sprite.argb.size, MAX_SAMPLES_PER_ASSET)
        return buildList(sampleCount) {
            repeat(sampleCount) { sampleIndex ->
                val pixel = sprite.argb[(sampleIndex.toLong() * sprite.argb.size / sampleCount).toInt()]
                if (pixel ushr 24 >= MINIMUM_ALPHA) add(pixel and 0xFFFFFF)
            }
        }
    }

    private fun histogram(colors: List<Int>): Map<Int, Double> = colors
        .groupingBy(::quantize)
        .eachCount()
        .mapValues { (_, count) -> count.toDouble() }

    private fun buildTheme(
        method: CatalogThemeMethod,
        contributors: Set<CatalogThemeAssetClass>,
        weights: Map<Int, Double>,
    ): CatalogTheme? {
        if (weights.size < MINIMUM_QUANTIZED_COLORS) return null
        val colors = weights.entries.map { (rgb, weight) -> WeightedColor(rgb, weight) }
        val darkest = colors.minWith(compareBy<WeightedColor>({ it.luminance }, { it.rgb }))
        val lightest = colors.maxWith(compareBy<WeightedColor>({ it.luminance }, { -it.rgb }))
        val field = colors.maxWith(
            compareBy<WeightedColor>({ it.weight + it.chroma * 1.5 - abs(it.luminance - 0.32) }, { -it.rgb }),
        )
        val header = colors.maxWith(
            compareBy<WeightedColor>(
                { it.chroma * 2.0 + colorDistance(it.rgb, field.rgb) + it.weight * 0.25 },
                { -it.rgb },
            ),
        )
        val accent = colors.maxWith(
            compareBy<WeightedColor>(
                { it.chroma * 2.0 + colorDistance(it.rgb, field.rgb) + colorDistance(it.rgb, header.rgb) },
                { -it.rgb },
            ),
        )

        val panel = blend(lightest.rgb, WHITE, 0.72)
        val menu = blend(lightest.rgb, WHITE, 0.38)
        val fieldRgb = blend(field.rgb, BLACK, 0.08)
        val headerRgb = blend(header.rgb, BLACK, 0.12)
        var corrected = false
        val text = readableForeground(darkest.rgb, panel, CatalogTheme.NORMAL_TEXT_CONTRAST).also {
            corrected = corrected || it != darkest.rgb
        }
        val accentText = readableForeground(lightest.rgb, accent.rgb, CatalogTheme.NORMAL_TEXT_CONTRAST).also {
            corrected = corrected || it != lightest.rgb
        }
        val initialBorder = blend(darkest.rgb, BLACK, 0.46)
        val border = readableForeground(initialBorder, panel, CatalogTheme.CONTROL_CONTRAST).also {
            corrected = corrected || it != initialBorder
        }
        return CatalogTheme(
            method = method,
            assetClasses = contributors,
            contrastCorrected = corrected,
            tokens = CatalogThemeTokens(
                field = fieldRgb,
                fieldPattern = blend(fieldRgb, menu, 0.12),
                header = headerRgb,
                headerShadow = blend(headerRgb, BLACK, 0.38),
                menu = menu,
                menuShadow = blend(menu, BLACK, 0.42),
                panel = panel,
                border = border,
                text = text,
                textShadow = if (text == BLACK) WHITE else BLACK,
                accent = accent.rgb,
                accentText = accentText,
            ),
        )
    }

    private fun readableForeground(candidate: Int, background: Int, minimumContrast: Double): Int {
        if (CatalogTheme.contrastRatio(candidate, background) >= minimumContrast) return candidate
        return listOf(BLACK, WHITE).maxWith(compareBy<Int>({ CatalogTheme.contrastRatio(it, background) }, { -it }))
    }

    private fun quantize(rgb: Int): Int {
        val red = (((rgb shr 16 and 0xff) and 0xf8) + 3).coerceAtMost(255)
        val green = (((rgb shr 8 and 0xff) and 0xf8) + 3).coerceAtMost(255)
        val blue = (((rgb and 0xff) and 0xf8) + 3).coerceAtMost(255)
        return (red shl 16) or (green shl 8) or blue
    }

    private fun blend(first: Int, second: Int, secondWeight: Double): Int {
        fun channel(shift: Int): Int {
            val firstValue = first shr shift and 0xff
            val secondValue = second shr shift and 0xff
            return (firstValue * (1.0 - secondWeight) + secondValue * secondWeight).toInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun colorDistance(first: Int, second: Int): Double {
        val red = ((first shr 16 and 0xff) - (second shr 16 and 0xff)) / 255.0
        val green = ((first shr 8 and 0xff) - (second shr 8 and 0xff)) / 255.0
        val blue = ((first and 0xff) - (second and 0xff)) / 255.0
        return red * red + green * green + blue * blue
    }

    private fun assetFingerprint(sprite: RgbaSprite): Long {
        var hash = FNV_OFFSET
        hash = (hash xor sprite.width.toLong()) * FNV_PRIME
        hash = (hash xor sprite.height.toLong()) * FNV_PRIME
        sprite.argb.forEach { pixel -> hash = (hash xor pixel.toLong()) * FNV_PRIME }
        return hash
    }

    private data class WeightedColor(val rgb: Int, val weight: Double) {
        val luminance = ((rgb shr 16 and 0xff) * 0.2126 + (rgb shr 8 and 0xff) * 0.7152 +
            (rgb and 0xff) * 0.0722) / 255.0
        val chroma = (max(rgb shr 16 and 0xff, max(rgb shr 8 and 0xff, rgb and 0xff)) -
            min(rgb shr 16 and 0xff, min(rgb shr 8 and 0xff, rgb and 0xff))) / 255.0
    }

    private const val MINIMUM_ASSET_CLASSES = 2
    private const val MINIMUM_DIRECT_COLORS = 4
    private const val MINIMUM_QUANTIZED_COLORS = 2
    private const val MAX_ASSETS_PER_CLASS = 16
    private const val MAX_SAMPLES_PER_ASSET = 512
    private const val MINIMUM_ALPHA = 0x80
    private const val BLACK = 0x000000
    private const val WHITE = 0xFFFFFF
    private const val FNV_OFFSET = -0x340d631b7bdddcdbL
    private const val FNV_PRIME = 0x100000001b3L
}
