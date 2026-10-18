package com.enrpau.dualscreendex.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomThemeMaterializerTest {
    @Test
    fun `streaming selection preserves the legacy fingerprint order and first duplicate`() {
        val sprites = (0 until 256).map { seed ->
            RgbaSprite(16, 16, IntArray(256) { pixel -> 0xff000000.toInt() or (seed shl 8) or pixel })
        }
        fun fingerprint(sprite: RgbaSprite): Long {
            var hash = -0x340d631b7bdddcdbL
            hash = (hash xor sprite.width.toLong()) * 0x100000001b3L
            hash = (hash xor sprite.height.toLong()) * 0x100000001b3L
            sprite.argb.forEach { hash = (hash xor it.toLong()) * 0x100000001b3L }
            return hash
        }
        for (size in listOf(0, 1, 15, 16, 17, 64, 256)) {
            val input = sprites.take(size).shuffled(kotlin.random.Random(size)).flatMap {
                listOf(it, it.copy(argb = it.argb.copyOf()))
            }
            val expected = input.distinctBy(::fingerprint).sortedBy(::fingerprint).take(16)
            var visited = 0
            val selected = RomThemeMaterializer.selectAssets(input.asSequence().onEach { visited++ }.constrainOnce())
            assertEquals(input.size, visited)
            assertEquals(expected, selected)
            assertTrue(selected.size <= 16)
            expected.zip(selected).forEach { (first, actual) -> assertTrue(first === actual) }
        }
    }

    @Test
    fun `multi asset theme is deterministic under class and asset reordering`() {
        val evidence = linkedMapOf(
            CatalogThemeAssetClass.WORLD_MAP to listOf(
                sprite(0xff285b42.toInt(), 0xff7ebc72.toInt(), 0xffe8ddb4.toInt()),
                sprite(0xff1d3d31.toInt(), 0xffb2d58a.toInt()),
            ),
            CatalogThemeAssetClass.TRAINER to listOf(
                sprite(0xff924b3d.toInt(), 0xfff3e7c5.toInt(), 0xff33251d.toInt()),
                sprite(0xff5279a5.toInt(), 0xffe4d19d.toInt()),
            ),
        )
        val forward = RomThemeMaterializer.materialize(evidence)
        val reversed = RomThemeMaterializer.materialize(
            evidence.entries.reversed().associate { (assetClass, assets) -> assetClass to assets.reversed() },
        )
        assertEquals(CatalogThemeMethod.MULTI_ASSET_QUANTIZATION, forward.method)
        assertEquals(forward, reversed)
        assertEquals(setOf(CatalogThemeAssetClass.WORLD_MAP, CatalogThemeAssetClass.TRAINER), forward.assetClasses)
    }

    @Test
    fun `large raster cannot outweigh an independent asset class`() {
        val small = mapOf(
            CatalogThemeAssetClass.WORLD_MAP to listOf(solidSprite(8, 8, 0xffb84432.toInt())),
            CatalogThemeAssetClass.TRAINER to listOf(solidSprite(8, 8, 0xff3977b8.toInt())),
        )
        val large = small + (CatalogThemeAssetClass.WORLD_MAP to listOf(solidSprite(512, 512, 0xffb84432.toInt())))
        assertEquals(RomThemeMaterializer.materialize(small), RomThemeMaterializer.materialize(large))
    }

    @Test
    fun `transparent pixels do not influence quantization`() {
        val opaque = mapOf(
            CatalogThemeAssetClass.WORLD_MAP to listOf(sprite(0xff35684b.toInt(), 0xffdfd29d.toInt())),
            CatalogThemeAssetClass.SPECIES to listOf(sprite(0xff825037.toInt(), 0xffe9e1c7.toInt())),
        )
        val noisy = opaque + (CatalogThemeAssetClass.SPECIES to listOf(
            sprite(0x00ff00ff, 0x0000ffff, 0xff825037.toInt(), 0xffe9e1c7.toInt()),
        ))
        assertEquals(RomThemeMaterializer.materialize(opaque), RomThemeMaterializer.materialize(noisy))
    }

    @Test
    fun `one asset class and empty evidence use neutral fallback`() {
        val oneClass = RomThemeMaterializer.materialize(
            mapOf(CatalogThemeAssetClass.SPECIES to listOf(sprite(0xff315a45.toInt(), 0xffe8ddb4.toInt()))),
        )
        assertEquals(CatalogTheme.neutral(), oneClass)
        assertEquals(CatalogTheme.neutral(), RomThemeMaterializer.materialize(emptyMap()))
    }

    @Test
    fun `DMG intensity palette stays neutral without physical color evidence`() {
        val result = RomThemeMaterializer.materialize(
            emptyMap(),
            listOf(DirectCatalogThemePalette(listOf(0xffffff, 0xaaaaaa, 0x555555, 0x000000), CatalogThemePaletteModel.DMG_INTENSITY)),
        )
        assertEquals(CatalogTheme.neutral(), result)
    }

    @Test
    fun `one chromatic direct palette wins while ambiguity falls through to assets`() {
        val palette = DirectCatalogThemePalette(
            listOf(0x153d2e, 0x2c7551, 0xe9dca9, 0xf8f2da, 0x412d24, 0xd86b3f),
            CatalogThemePaletteModel.RGB,
        )
        val direct = RomThemeMaterializer.materialize(emptyMap(), listOf(palette))
        val ambiguous = RomThemeMaterializer.materialize(
            mapOf(
                CatalogThemeAssetClass.WORLD_MAP to listOf(sprite(0xff285b42.toInt(), 0xffe8ddb4.toInt())),
                CatalogThemeAssetClass.TRAINER to listOf(sprite(0xff924b3d.toInt(), 0xfff3e7c5.toInt())),
            ),
            listOf(palette, palette.copy(colors = palette.colors.reversed())),
        )
        assertEquals(CatalogThemeMethod.DIRECT_UI_PALETTE, direct.method)
        assertEquals(setOf(CatalogThemeAssetClass.INTERFACE), direct.assetClasses)
        assertEquals(CatalogThemeMethod.MULTI_ASSET_QUANTIZATION, ambiguous.method)
    }

    @Test
    fun `every emitted theme is complete and readable`() {
        val theme = RomThemeMaterializer.materialize(
            mapOf(
                CatalogThemeAssetClass.WORLD_MAP to listOf(sprite(0xff214b38.toInt(), 0xff8bbb69.toInt())),
                CatalogThemeAssetClass.TRAINER to listOf(sprite(0xfff0dfae.toInt(), 0xff5b3526.toInt())),
            ),
        )
        theme.validate()
        assertTrue(CatalogTheme.contrastRatio(theme.tokens.text, theme.tokens.panel) >= 4.5)
        assertTrue(CatalogTheme.contrastRatio(theme.tokens.accentText, theme.tokens.accent) >= 4.5)
        assertTrue(CatalogTheme.contrastRatio(theme.tokens.accentText, theme.tokens.header) >= 4.5)
        assertTrue(CatalogTheme.contrastRatio(theme.tokens.border, theme.tokens.panel) >= 3.0)
        assertFalse(theme.assetClasses.isEmpty())
    }

    private fun sprite(vararg pixels: Int) = RgbaSprite(pixels.size, 1, pixels)
    private fun solidSprite(width: Int, height: Int, color: Int) =
        RgbaSprite(width, height, IntArray(width * height) { color })
}
