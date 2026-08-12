package com.enrpau.dualscreendex.parser.sprite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TileRendererTest {
    @Test
    fun rendersGameBoyTwoBitplanesInTileOrder() {
        val tile = ByteArray(16)
        tile[0] = 0x80.toByte()
        tile[1] = 0x40

        val sprite = TileRenderer.gameBoy2Bpp(tile, 1, 1)

        assertEquals(1, sprite.indexAt(0, 0))
        assertEquals(2, sprite.indexAt(1, 0))
        assertEquals(0, sprite.indexAt(2, 0))
    }

    @Test
    fun rendersGbaFourBitplanesLowNibbleFirst() {
        val tile = ByteArray(32)
        tile[0] = 0x21

        val sprite = TileRenderer.gba4Bpp(tile, 1, 1)

        assertEquals(1, sprite.indexAt(0, 0))
        assertEquals(2, sprite.indexAt(1, 0))
    }

    @Test
    fun convertsBgr555AndMakesPaletteZeroTransparent() {
        val indexed = IndexedSprite(1, 1, byteArrayOf(0))
        val rgba = TileRenderer.applyBgr555Palette(indexed, shortArrayOf(0x001F, 0x03E0))

        assertEquals(0, rgba.argb[0])
        assertEquals(0xFFFF0000.toInt(), TileRenderer.bgr555ToArgb(0x001F, transparent = false))
    }

    @Test
    fun decodesGbaEightBitPaletteIndicesIncludingZeroAnd255() {
        val tile = ByteArray(64)
        tile[0] = 0
        tile[1] = 0xFF.toByte()

        val sprite = TileRenderer.gba8Bpp(tile, 1, 1)
        val palette = ShortArray(256).also { it[255] = 0x7C00.toShort() }
        val rgba = TileRenderer.applyBgr555Palette(sprite, palette)

        assertEquals(0, sprite.indexAt(0, 0))
        assertEquals(255, sprite.indexAt(1, 0))
        assertEquals(0, rgba.argb[0])
        assertEquals(0xFF0000FF.toInt(), rgba.argb[1])
    }

    @Test
    fun composesAdjacentGbaEightBitTilesAndHonorsBothFlipBits() {
        val tiles = ByteArray(2 * 64)
        repeat(64) { position ->
            tiles[position] = (position + 1).toByte()
            tiles[64 + position] = 90
        }
        val tilemap = tilemap(
            1,
            0x0400,
            0x0800,
            0x0C00,
        )

        val sprite = TileRenderer.gba8BppTilemap(tiles, tilemap, 4, 1)

        assertEquals(32, sprite.width)
        assertEquals(8, sprite.height)
        assertEquals(90, sprite.indexAt(0, 0))
        assertEquals(8, sprite.indexAt(8, 0))
        assertEquals(57, sprite.indexAt(16, 0))
        assertEquals(64, sprite.indexAt(24, 0))
    }

    @Test
    fun rejectsTruncatedGbaEightBitTilesAndInvalidTilemapReferences() {
        assertThrows(IllegalArgumentException::class.java) {
            TileRenderer.gba8Bpp(ByteArray(63), 1, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TileRenderer.gba8BppTilemap(ByteArray(64), tilemap(1), 1, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TileRenderer.gba8BppTilemap(ByteArray(64), tilemap(0x1000), 1, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TileRenderer.gba8BppTilemap(ByteArray(64), byteArrayOf(0), 1, 1)
        }
    }

    @Test
    fun rejectsPaletteOverflowWhenColoringAnEightBitTilemap() {
        val tiles = ByteArray(64)
        tiles[0] = 0xFF.toByte()
        val indexed = TileRenderer.gba8BppTilemap(tiles, tilemap(0), 1, 1)

        assertThrows(IllegalArgumentException::class.java) {
            TileRenderer.applyBgr555Palette(indexed, ShortArray(255))
        }
    }

    private fun tilemap(vararg entries: Int): ByteArray = ByteArray(entries.size * 2).also { bytes ->
        entries.forEachIndexed { index, entry ->
            bytes[index * 2] = entry.toByte()
            bytes[index * 2 + 1] = (entry ushr 8).toByte()
        }
    }
}
