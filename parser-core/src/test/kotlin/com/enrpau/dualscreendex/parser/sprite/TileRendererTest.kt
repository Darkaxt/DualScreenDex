package com.enrpau.dualscreendex.parser.sprite

import org.junit.Assert.assertEquals
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
}
