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

    fun gameBoy2BppTilemap(
        tiles: ByteArray,
        tilemap: ByteArray,
        tileWidth: Int,
        tileHeight: Int,
    ): IndexedSprite {
        require(tileWidth > 0 && tileHeight > 0) { "2bpp tilemap dimensions must be positive" }
        require(tiles.isNotEmpty() && tiles.size % GAME_BOY_2BPP_TILE_BYTES == 0) {
            "2bpp tile data must contain complete tiles"
        }
        val mapEntries = tileWidth.toLong() * tileHeight.toLong()
        require(mapEntries <= Int.MAX_VALUE && tilemap.size.toLong() == mapEntries) {
            "2bpp tilemap has an invalid byte length"
        }
        val pixelWidth = tileWidth.toLong() * TILE_EDGE
        val pixelHeight = tileHeight.toLong() * TILE_EDGE
        require(pixelWidth * pixelHeight <= Int.MAX_VALUE) { "2bpp tilemap dimensions exceed pixel bounds" }
        val tileCount = tiles.size / GAME_BOY_2BPP_TILE_BYTES
        val pixels = ByteArray((pixelWidth * pixelHeight).toInt())
        repeat(mapEntries.toInt()) { mapIndex ->
            val tile = tilemap[mapIndex].toInt() and 0xFF
            require(tile < tileCount) { "2bpp tilemap index exceeds tile data" }
            val mapX = mapIndex % tileWidth
            val mapY = mapIndex / tileWidth
            repeat(TILE_EDGE) { row ->
                val low = tiles[tile * GAME_BOY_2BPP_TILE_BYTES + row * 2].toInt() and 0xFF
                val high = tiles[tile * GAME_BOY_2BPP_TILE_BYTES + row * 2 + 1].toInt() and 0xFF
                repeat(TILE_EDGE) { column ->
                    val bit = TILE_EDGE - 1 - column
                    val value = ((low ushr bit) and 1) or (((high ushr bit) and 1) shl 1)
                    val destination = (mapY * TILE_EDGE + row) * pixelWidth.toInt() +
                        mapX * TILE_EDGE + column
                    pixels[destination] = value.toByte()
                }
            }
        }
        return IndexedSprite(pixelWidth.toInt(), pixelHeight.toInt(), pixels)
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

    fun gba8Bpp(bytes: ByteArray, tileWidth: Int, tileHeight: Int): IndexedSprite {
        require(tileWidth > 0 && tileHeight > 0) { "8bpp tile dimensions must be positive" }
        val tileCount = tileWidth.toLong() * tileHeight.toLong()
        val requiredBytes = tileCount * GBA_8BPP_TILE_BYTES
        require(requiredBytes <= Int.MAX_VALUE && bytes.size.toLong() >= requiredBytes) {
            "not enough 8bpp tile data"
        }
        val width = tileWidth * TILE_EDGE
        val height = tileHeight * TILE_EDGE
        val pixels = ByteArray(width * height)
        repeat(tileCount.toInt()) { tile ->
            val tileX = tile % tileWidth
            val tileY = tile / tileWidth
            repeat(TILE_EDGE) { row ->
                bytes.copyInto(
                    pixels,
                    destinationOffset = (tileY * TILE_EDGE + row) * width + tileX * TILE_EDGE,
                    startIndex = tile * GBA_8BPP_TILE_BYTES + row * TILE_EDGE,
                    endIndex = tile * GBA_8BPP_TILE_BYTES + (row + 1) * TILE_EDGE,
                )
            }
        }
        return IndexedSprite(width, height, pixels)
    }

    fun gba8BppTilemap(
        tiles: ByteArray,
        tilemap: ByteArray,
        tileWidth: Int,
        tileHeight: Int,
    ): IndexedSprite {
        require(tileWidth > 0 && tileHeight > 0) { "8bpp tilemap dimensions must be positive" }
        require(tiles.isNotEmpty() && tiles.size % GBA_8BPP_TILE_BYTES == 0) {
            "8bpp tile data must contain complete tiles"
        }
        val tileCount = tiles.size / GBA_8BPP_TILE_BYTES
        require(tileCount <= GBA_TILE_INDEX_MASK + 1) { "8bpp tile data exceeds addressable tile indices" }
        val entryCount = tileWidth.toLong() * tileHeight.toLong()
        val expectedMapBytes = entryCount * GBA_TILEMAP_ENTRY_BYTES
        val pixelWidth = tileWidth.toLong() * TILE_EDGE
        val pixelHeight = tileHeight.toLong() * TILE_EDGE
        require(expectedMapBytes <= Int.MAX_VALUE && tilemap.size.toLong() == expectedMapBytes) {
            "8bpp tilemap has an invalid byte length"
        }
        require(pixelWidth * pixelHeight <= Int.MAX_VALUE) { "8bpp tilemap dimensions exceed pixel bounds" }
        val pixels = ByteArray((pixelWidth * pixelHeight).toInt())
        repeat(entryCount.toInt()) { mapIndex ->
            val entryOffset = mapIndex * GBA_TILEMAP_ENTRY_BYTES
            val entry = (tilemap[entryOffset].toInt() and 0xFF) or
                ((tilemap[entryOffset + 1].toInt() and 0xFF) shl 8)
            require(entry and GBA_8BPP_RESERVED_MASK == 0) { "8bpp tilemap entry uses reserved palette bits" }
            val tileIndex = entry and GBA_TILE_INDEX_MASK
            require(tileIndex < tileCount) { "8bpp tilemap index exceeds tile data" }
            val horizontalFlip = entry and GBA_HORIZONTAL_FLIP != 0
            val verticalFlip = entry and GBA_VERTICAL_FLIP != 0
            val mapX = mapIndex % tileWidth
            val mapY = mapIndex / tileWidth
            repeat(TILE_EDGE) { row ->
                val sourceY = if (verticalFlip) TILE_EDGE - 1 - row else row
                repeat(TILE_EDGE) { column ->
                    val sourceX = if (horizontalFlip) TILE_EDGE - 1 - column else column
                    val source = tileIndex * GBA_8BPP_TILE_BYTES + sourceY * TILE_EDGE + sourceX
                    val destination = (mapY * TILE_EDGE + row) * pixelWidth.toInt() + mapX * TILE_EDGE + column
                    pixels[destination] = tiles[source]
                }
            }
        }
        return IndexedSprite(pixelWidth.toInt(), pixelHeight.toInt(), pixels)
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

    private const val TILE_EDGE = 8
    private const val GAME_BOY_2BPP_TILE_BYTES = 16
    private const val GBA_8BPP_TILE_BYTES = 64
    private const val GBA_TILEMAP_ENTRY_BYTES = 2
    private const val GBA_TILE_INDEX_MASK = 0x03FF
    private const val GBA_HORIZONTAL_FLIP = 0x0400
    private const val GBA_VERTICAL_FLIP = 0x0800
    private const val GBA_8BPP_RESERVED_MASK = 0xF000
}
