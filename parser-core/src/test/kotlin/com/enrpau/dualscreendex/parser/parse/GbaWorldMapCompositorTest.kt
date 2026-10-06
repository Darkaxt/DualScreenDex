package com.enrpau.dualscreendex.parser.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GbaWorldMapCompositorTest {
    @Test
    fun legacyAffineAndTiledCropsKeepExactPalettePixels() {
        val tiles = ByteArray(128) { if (it < 64) 1 else 2 }
        val palette = shortArrayOf(0, 0x001F, 0x03E0)
        val affine = ByteArray(4096)
        affine[2 * 64 + 1] = 1
        val tiled = ByteArray(1280)
        tiled[(2 * 32 + 1) * 2] = 1
        listOf(affine to GbaWorldMapFormat.AFFINE_8BPP_64X64,
            tiled to GbaWorldMapFormat.TILED_8BPP_32X20).forEach { (map, format) ->
            val result = GbaWorldMapCompositor.compose(tiles, map, palette) as GbaWorldMapComposition.Resolved
            assertEquals(format, result.format)
            assertEquals(28, result.gridWidth)
            assertEquals(15, result.gridHeight)
            assertEquals(224, result.raster.width)
            assertEquals(120, result.raster.height)
            assertEquals(0xFF00FF00.toInt(), result.raster.argb[0])
            assertEquals(0xFFFF0000.toInt(), result.raster.argb[8])
        }
    }

    @Test
    fun textCropAndCompletePaletteBanksRemainIndependentOfAffineTables() {
        val result = GbaWorldMapCompositor.compose(
            ByteArray(32) { 0x11 }, ByteArray(1200), ShortArray(16) { if (it == 1) 0x7C00 else 0 },
        ) as GbaWorldMapComposition.Resolved
        assertEquals(GbaWorldMapFormat.TEXT_4BPP_30X20, result.format)
        assertEquals(22, result.gridWidth)
        assertEquals(176, result.raster.width)
        assertTrue(result.raster.argb.all { it == 0xFF0000FF.toInt() })
    }

    @Test
    fun malformedLengthsTileIndicesAndPaletteCoverageRemainRejected() {
        assertTrue(GbaWorldMapCompositor.compose(ByteArray(64), ByteArray(4095), shortArrayOf(0, 1))
            is GbaWorldMapComposition.Rejected)
        val map = ByteArray(4096)
        map[2 * 64 + 1] = 3
        assertTrue(GbaWorldMapCompositor.compose(ByteArray(128) { 1 }, map, shortArrayOf(0, 1))
            is GbaWorldMapComposition.Rejected)
        map[2 * 64 + 1] = 0
        assertTrue(GbaWorldMapCompositor.compose(ByteArray(64) { 15 }, map, shortArrayOf(0, 1))
            is GbaWorldMapComposition.Rejected)
    }
}
