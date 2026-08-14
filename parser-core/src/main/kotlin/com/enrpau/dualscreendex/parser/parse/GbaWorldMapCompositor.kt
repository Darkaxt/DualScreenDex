package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.sprite.TileRenderer

enum class GbaWorldMapFormat {
    AFFINE_8BPP_64X64,
    TEXT_4BPP_30X20,
}

sealed interface GbaWorldMapComposition {
    data class Resolved(
        val format: GbaWorldMapFormat,
        val gridWidth: Int,
        val gridHeight: Int,
        val raster: RgbaSprite,
    ) : GbaWorldMapComposition

    data class Rejected(val reason: String) : GbaWorldMapComposition
}

/**
 * Composes only loader formats proven by real Gen III source and binary controls.
 * Source identity and ROM placement are deliberately outside this boundary.
 */
object GbaWorldMapCompositor {
    fun compose(
        tiles: ByteArray,
        tilemap: ByteArray,
        palette: ShortArray,
    ): GbaWorldMapComposition = when {
        tilemap.size == AFFINE_MAP_BYTES -> composeAffine(tiles, tilemap, palette)
        isTextMapByteLength(tilemap.size) ->
            composeText(tiles, tilemap, palette)
        else -> GbaWorldMapComposition.Rejected(
            "tilemap byte length ${tilemap.size} does not match a proven world-map format",
        )
    }

    internal fun isTextMapByteLength(byteLength: Int): Boolean =
        (byteLength in TEXT_REQUIRED_CROP_BYTES..TEXT_MAP_BYTES && byteLength % 2 == 0) ||
            (byteLength in TEXT_MAP_BYTES + TEXT_PADDING_ALIGNMENT..TEXT_MAX_PADDED_MAP_BYTES &&
                (byteLength - TEXT_MAP_BYTES) % TEXT_PADDING_ALIGNMENT == 0)

    private fun composeAffine(
        tiles: ByteArray,
        tilemap: ByteArray,
        palette: ShortArray,
    ): GbaWorldMapComposition {
        if (tiles.isEmpty() || tiles.size % AFFINE_TILE_BYTES != 0) {
            return GbaWorldMapComposition.Rejected("affine tile bytes are not a non-empty 8bpp tile stream")
        }
        if (palette.size !in 2..MAX_AFFINE_PALETTE_COLORS) {
            return GbaWorldMapComposition.Rejected("affine palette length ${palette.size} is invalid")
        }
        val tileCount = tiles.size / AFFINE_TILE_BYTES
        val interleavedRows = hasInterleavedAffineRows(tilemap)
        val indices = ByteArray(AFFINE_OUTPUT_WIDTH * TILE_PIXELS * AFFINE_OUTPUT_HEIGHT * TILE_PIXELS)
        repeat(AFFINE_OUTPUT_HEIGHT) { cellY ->
            repeat(AFFINE_OUTPUT_WIDTH) { cellX ->
                val mapOffset = if (interleavedRows) {
                    cellY * AFFINE_INTERLEAVED_ROW_STRIDE + cellX
                } else {
                    (AFFINE_CROP_Y + cellY) * AFFINE_MAP_WIDTH + AFFINE_CROP_X + cellX
                }
                val tileIndex = tilemap[mapOffset].toInt() and 0xff
                if (tileIndex >= tileCount) {
                    return GbaWorldMapComposition.Rejected(
                        "affine tile index $tileIndex exceeds $tileCount decoded tiles",
                    )
                }
                copy8BppTile(
                    tiles = tiles,
                    tileIndex = tileIndex,
                    output = indices,
                    outputWidth = AFFINE_OUTPUT_WIDTH * TILE_PIXELS,
                    cellX = cellX,
                    cellY = cellY,
                )
            }
        }
        val usedIndices = indices.map { it.toInt() and 0xff }.filter { it != 0 }
        if (usedIndices.isEmpty()) {
            return GbaWorldMapComposition.Rejected("affine crop contains no nonzero palette indices")
        }
        val paletteBase = usedIndices.min() / PALETTE_BANK_COLORS * PALETTE_BANK_COLORS
        if (usedIndices.max() >= paletteBase + palette.size) {
            return GbaWorldMapComposition.Rejected(
                "affine palette does not cover used indices ${usedIndices.min()}..${usedIndices.max()}",
            )
        }
        val argb = IntArray(indices.size) { position ->
            val sourceIndex = indices[position].toInt() and 0xff
            if (sourceIndex == 0) {
                0
            } else {
                TileRenderer.bgr555ToArgb(
                    palette[sourceIndex - paletteBase].toInt() and 0xffff,
                    transparent = false,
                )
            }
        }
        return GbaWorldMapComposition.Resolved(
            format = GbaWorldMapFormat.AFFINE_8BPP_64X64,
            gridWidth = AFFINE_OUTPUT_WIDTH,
            gridHeight = AFFINE_OUTPUT_HEIGHT,
            raster = RgbaSprite(AFFINE_OUTPUT_WIDTH * TILE_PIXELS, AFFINE_OUTPUT_HEIGHT * TILE_PIXELS, argb),
        )
    }

    private fun composeText(
        tiles: ByteArray,
        tilemap: ByteArray,
        palette: ShortArray,
    ): GbaWorldMapComposition {
        if (tilemap.size < TEXT_REQUIRED_CROP_BYTES) {
            return GbaWorldMapComposition.Rejected("text tilemap does not contain the complete display crop")
        }
        if (tiles.isEmpty() || tiles.size % TEXT_TILE_BYTES != 0) {
            return GbaWorldMapComposition.Rejected("text tile bytes are not a non-empty 4bpp tile stream")
        }
        if (palette.size < PALETTE_BANK_COLORS || palette.size % PALETTE_BANK_COLORS != 0) {
            return GbaWorldMapComposition.Rejected("text palette is not composed of complete 16-color banks")
        }
        val tileCount = tiles.size / TEXT_TILE_BYTES
        val outputWidth = TEXT_OUTPUT_WIDTH * TILE_PIXELS
        val outputHeight = TEXT_OUTPUT_HEIGHT * TILE_PIXELS
        val argb = IntArray(outputWidth * outputHeight)
        repeat(TEXT_OUTPUT_HEIGHT) { cellY ->
            repeat(TEXT_OUTPUT_WIDTH) { cellX ->
                val sourceEntry = (TEXT_CROP_Y + cellY) * TEXT_MAP_WIDTH + TEXT_CROP_X + cellX
                val byteOffset = sourceEntry * 2
                val entry = (tilemap[byteOffset].toInt() and 0xff) or
                    ((tilemap[byteOffset + 1].toInt() and 0xff) shl 8)
                val tileIndex = entry and TEXT_TILE_INDEX_MASK
                val paletteBank = entry ushr TEXT_PALETTE_SHIFT
                if (tileIndex >= tileCount) {
                    return GbaWorldMapComposition.Rejected(
                        "text tile index $tileIndex exceeds $tileCount decoded tiles",
                    )
                }
                if ((paletteBank + 1) * PALETTE_BANK_COLORS > palette.size) {
                    return GbaWorldMapComposition.Rejected(
                        "text palette bank $paletteBank exceeds ${palette.size / PALETTE_BANK_COLORS} banks",
                    )
                }
                copy4BppTextTile(
                    tiles = tiles,
                    tileIndex = tileIndex,
                    paletteBank = paletteBank,
                    horizontalFlip = entry and TEXT_HORIZONTAL_FLIP != 0,
                    verticalFlip = entry and TEXT_VERTICAL_FLIP != 0,
                    palette = palette,
                    output = argb,
                    outputWidth = outputWidth,
                    cellX = cellX,
                    cellY = cellY,
                )
            }
        }
        return GbaWorldMapComposition.Resolved(
            format = GbaWorldMapFormat.TEXT_4BPP_30X20,
            gridWidth = TEXT_OUTPUT_WIDTH,
            gridHeight = TEXT_OUTPUT_HEIGHT,
            raster = RgbaSprite(outputWidth, outputHeight, argb),
        )
    }

    private fun hasInterleavedAffineRows(tilemap: ByteArray): Boolean {
        var nonzeroMatches = 0
        val rowsMatch = (1 until AFFINE_INTERLEAVED_LOGICAL_ROWS).all { row ->
            val trailing = (row * 2 - 1) * AFFINE_MAP_WIDTH + AFFINE_INTERLEAVED_HALF_WIDTH
            val leading = row * AFFINE_INTERLEAVED_ROW_STRIDE
            (0 until AFFINE_INTERLEAVED_HALF_WIDTH).all { column ->
                val value = tilemap[trailing + column]
                val matches = value == tilemap[leading + column]
                if (matches && value.toInt() != 0) nonzeroMatches++
                matches
            }
        }
        return rowsMatch && nonzeroMatches >= AFFINE_INTERLEAVED_HALF_WIDTH
    }

    private fun copy8BppTile(
        tiles: ByteArray,
        tileIndex: Int,
        output: ByteArray,
        outputWidth: Int,
        cellX: Int,
        cellY: Int,
    ) {
        repeat(TILE_PIXELS) { pixelY ->
            val source = tileIndex * AFFINE_TILE_BYTES + pixelY * TILE_PIXELS
            val destination = (cellY * TILE_PIXELS + pixelY) * outputWidth + cellX * TILE_PIXELS
            tiles.copyInto(output, destination, source, source + TILE_PIXELS)
        }
    }

    private fun copy4BppTextTile(
        tiles: ByteArray,
        tileIndex: Int,
        paletteBank: Int,
        horizontalFlip: Boolean,
        verticalFlip: Boolean,
        palette: ShortArray,
        output: IntArray,
        outputWidth: Int,
        cellX: Int,
        cellY: Int,
    ) {
        repeat(TILE_PIXELS) { pixelY ->
            val sourceY = if (verticalFlip) TILE_PIXELS - 1 - pixelY else pixelY
            repeat(TILE_PIXELS) { pixelX ->
                val sourceX = if (horizontalFlip) TILE_PIXELS - 1 - pixelX else pixelX
                val packed = tiles[tileIndex * TEXT_TILE_BYTES + sourceY * 4 + sourceX / 2].toInt() and 0xff
                val localIndex = if (sourceX and 1 == 0) packed and 0x0f else packed ushr 4
                val paletteIndex = paletteBank * PALETTE_BANK_COLORS + localIndex
                val destination = (cellY * TILE_PIXELS + pixelY) * outputWidth + cellX * TILE_PIXELS + pixelX
                output[destination] = TileRenderer.bgr555ToArgb(
                    palette[paletteIndex].toInt() and 0xffff,
                    transparent = paletteIndex == 0,
                )
            }
        }
    }

    private const val TILE_PIXELS = 8
    private const val PALETTE_BANK_COLORS = 16
    private const val MAX_AFFINE_PALETTE_COLORS = 256

    private const val AFFINE_TILE_BYTES = 64
    private const val AFFINE_MAP_WIDTH = 64
    private const val AFFINE_MAP_BYTES = 64 * 64
    private const val AFFINE_INTERLEAVED_HALF_WIDTH = 32
    private const val AFFINE_INTERLEAVED_LOGICAL_ROWS = 32
    private const val AFFINE_INTERLEAVED_ROW_STRIDE = AFFINE_MAP_WIDTH * 2
    private const val AFFINE_CROP_X = 1
    private const val AFFINE_CROP_Y = 2
    private const val AFFINE_OUTPUT_WIDTH = 28
    private const val AFFINE_OUTPUT_HEIGHT = 15

    private const val TEXT_TILE_BYTES = 32
    private const val TEXT_MAP_WIDTH = 30
    private const val TEXT_MAP_HEIGHT = 20
    private const val TEXT_MAP_BYTES = TEXT_MAP_WIDTH * TEXT_MAP_HEIGHT * 2
    // Source consumes exactly 600 u16 cells. Proven real loaders may align-pad the decompressed
    // root by up to three 16-byte units while retaining 1200-byte destination-slot stride.
    private const val TEXT_PADDING_ALIGNMENT = 16
    private const val TEXT_MAX_PADDED_MAP_BYTES = TEXT_MAP_BYTES + 3 * TEXT_PADDING_ALIGNMENT
    private const val TEXT_CROP_X = 4
    private const val TEXT_CROP_Y = 4
    private const val TEXT_OUTPUT_WIDTH = 22
    private const val TEXT_OUTPUT_HEIGHT = 15
    // Real loaders may omit an unused suffix while retaining the same 30-cell row stride.
    // Require every byte through the final cell touched by the 22x15 display crop.
    private const val TEXT_REQUIRED_CROP_BYTES =
        ((TEXT_CROP_Y + TEXT_OUTPUT_HEIGHT - 1) * TEXT_MAP_WIDTH + TEXT_CROP_X + TEXT_OUTPUT_WIDTH) * 2
    private const val TEXT_TILE_INDEX_MASK = 0x03ff
    private const val TEXT_HORIZONTAL_FLIP = 0x0400
    private const val TEXT_VERTICAL_FLIP = 0x0800
    private const val TEXT_PALETTE_SHIFT = 12
}
