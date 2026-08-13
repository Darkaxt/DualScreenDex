package com.enrpau.dualscreendex.parser.sprite

import com.enrpau.dualscreendex.parser.catalog.RgbaSprite

data class IndexedSprite(
    val width: Int,
    val height: Int,
    val indices: ByteArray,
) {
    init {
        require(width > 0 && height > 0 && indices.size == width * height)
    }

    fun indexAt(x: Int, y: Int): Int = indices[y * width + x].toInt() and 0xFF
}

object TileRenderer {
    fun gameBoy2Bpp(bytes: ByteArray, tileWidth: Int, tileHeight: Int): IndexedSprite {
        require(bytes.size >= tileWidth * tileHeight * 16) { "not enough 2bpp tile data" }
        val width = tileWidth * 8
        val height = tileHeight * 8
        val pixels = ByteArray(width * height)
        repeat(tileWidth * tileHeight) { tile ->
            val tileX = tile % tileWidth
            val tileY = tile / tileWidth
            repeat(8) { row ->
                val low = bytes[tile * 16 + row * 2].toInt() and 0xFF
                val high = bytes[tile * 16 + row * 2 + 1].toInt() and 0xFF
                repeat(8) { column ->
                    val bit = 7 - column
                    val value = ((low ushr bit) and 1) or (((high ushr bit) and 1) shl 1)
                    pixels[(tileY * 8 + row) * width + tileX * 8 + column] = value.toByte()
                }
            }
        }
        return IndexedSprite(width, height, pixels)
    }

    fun gba4Bpp(bytes: ByteArray, tileWidth: Int, tileHeight: Int): IndexedSprite {
        require(bytes.size >= tileWidth * tileHeight * 32) { "not enough 4bpp tile data" }
        val width = tileWidth * 8
        val height = tileHeight * 8
        val pixels = ByteArray(width * height)
        repeat(tileWidth * tileHeight) { tile ->
            val tileX = tile % tileWidth
            val tileY = tile / tileWidth
            repeat(8) { row ->
                repeat(8) { column ->
                    val packed = bytes[tile * 32 + row * 4 + column / 2].toInt() and 0xFF
                    val value = if (column and 1 == 0) packed and 0x0F else packed ushr 4
                    pixels[(tileY * 8 + row) * width + tileX * 8 + column] = value.toByte()
                }
            }
        }
        return IndexedSprite(width, height, pixels)
    }

    fun applyBgr555Palette(indexed: IndexedSprite, palette: ShortArray): RgbaSprite {
        require(palette.isNotEmpty()) { "palette is empty" }
        val pixels = IntArray(indexed.indices.size) { position ->
            val index = indexed.indices[position].toInt() and 0xFF
            require(index in palette.indices) { "sprite index exceeds palette" }
            bgr555ToArgb(palette[index].toInt() and 0xFFFF, transparent = index == 0)
        }
        return RgbaSprite(indexed.width, indexed.height, pixels)
    }

    fun applyArgbPalette(indexed: IndexedSprite, palette: IntArray): RgbaSprite {
        require(palette.isNotEmpty()) { "palette is empty" }
        return RgbaSprite(
            indexed.width,
            indexed.height,
            IntArray(indexed.indices.size) { position -> palette[indexed.indices[position].toInt() and 0xFF] },
        )
    }

    fun bgr555ToArgb(value: Int, transparent: Boolean): Int {
        if (transparent) return 0
        val red5 = value and 0x1F
        val green5 = value ushr 5 and 0x1F
        val blue5 = value ushr 10 and 0x1F
        val red = (red5 shl 3) or (red5 ushr 2)
        val green = (green5 shl 3) or (green5 ushr 2)
        val blue = (blue5 shl 3) or (blue5 ushr 2)
        return 0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
    }
}
