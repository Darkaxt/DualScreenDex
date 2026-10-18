package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/** Lossless static raster with a self-contained 16x16 ARGB dictionary and a little-endian u16 grid. */
data class TiledMapAsset(
    val pixelWidth: Int,
    val pixelHeight: Int,
    val tileCount: Int,
    val compressedTiles: ByteArray,
    val compressedGrid: ByteArray,
) {
    val storedPixelCount: Int
        get() {
            requireShape()
            return tileCount * TILE_PIXELS * TILE_PIXELS
        }
    val encodedBytes: Long
        get() = compressedTiles.size.toLong() + compressedGrid.size

    internal fun requireShape() {
        require(pixelWidth in TILE_PIXELS..4096 && pixelHeight in TILE_PIXELS..4096 && pixelWidth % TILE_PIXELS == 0 && pixelHeight % TILE_PIXELS == 0) {
            "tiled map dimensions must be positive multiples of 16"
        }
        val pixels = pixelWidth.toLong() * pixelHeight
        require(pixels <= MAX_MAP_PIXELS) { "tiled map exceeds per-map pixel bound" }
        require(tileCount in 1..(pixels / (TILE_PIXELS * TILE_PIXELS)).toInt()) {
            "tiled map dictionary exceeds the map grid"
        }
        require(compressedTiles.isNotEmpty() && compressedGrid.isNotEmpty() && encodedBytes <= MAX_ENCODED_BYTES) {
            "tiled map compressed data exceeds its bounds"
        }
    }

    fun validate(): TiledMapAsset = apply { TiledMapRasterCodec.decode(this) }

    override fun equals(other: Any?): Boolean = other is TiledMapAsset &&
        pixelWidth == other.pixelWidth && pixelHeight == other.pixelHeight && tileCount == other.tileCount &&
        compressedTiles.contentEquals(other.compressedTiles) && compressedGrid.contentEquals(other.compressedGrid)

    override fun hashCode(): Int = listOf(
        pixelWidth, pixelHeight, tileCount, compressedTiles.contentHashCode(), compressedGrid.contentHashCode(),
    ).fold(1) { result, value -> 31 * result + value }

    companion object {
        const val TILE_PIXELS = 16
        private const val MAX_MAP_PIXELS = 2_000_000L
        private const val MAX_ENCODED_BYTES = 64L * 1024 * 1024
    }
}

object TiledMapRasterCodec {
    fun encode(
        raster: RgbaSprite,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): TiledMapAsset {
        require(raster.width in TILE..4096 && raster.height in TILE..4096 && raster.width % TILE == 0 && raster.height % TILE == 0)
        require(raster.width.toLong() * raster.height <= 2_000_000L) { "tiled map exceeds per-map pixel bound" }
        cancellation.throwIfCancellationRequested()
        val gridWidth = raster.width / TILE
        val cells = gridWidth * (raster.height / TILE)
        val grid = ByteArray(cells * 2)
        val tiles = linkedMapOf<RgbaSprite, Int>()
        repeat(cells) { cell ->
            if (cell % CANCELLATION_CELLS == 0) cancellation.throwIfCancellationRequested()
            val originX = cell % gridWidth * TILE
            val originY = cell / gridWidth * TILE
            val tile = RgbaSprite(TILE, TILE, IntArray(TILE * TILE) { pixel ->
                raster.argb[(originY + pixel / TILE) * raster.width + originX + pixel % TILE]
            })
            val id = tiles.getOrPut(tile) { tiles.size }
            grid[cell * 2] = id.toByte()
            grid[cell * 2 + 1] = (id ushr 8).toByte()
        }
        val dictionary = ByteArray(tiles.size * TILE * TILE * Int.SIZE_BYTES)
        tiles.keys.forEachIndexed { tileIndex, tile ->
            if (tileIndex % CANCELLATION_CELLS == 0) cancellation.throwIfCancellationRequested()
            tile.argb.forEachIndexed { pixel, argb ->
                val offset = (tileIndex * TILE * TILE + pixel) * Int.SIZE_BYTES
                repeat(Int.SIZE_BYTES) { byte -> dictionary[offset + byte] = (argb ushr (byte * 8)).toByte() }
            }
        }
        cancellation.throwIfCancellationRequested()
        val compressedTiles = LocalMapRasterCodec.compress(dictionary)
        cancellation.throwIfCancellationRequested()
        val compressedGrid = LocalMapRasterCodec.compress(grid)
        cancellation.throwIfCancellationRequested()
        return TiledMapAsset(raster.width, raster.height, tiles.size, compressedTiles, compressedGrid)
    }

    internal data class Decoded(val tiles: ByteArray, val grid: ByteArray)

    internal fun decode(asset: TiledMapAsset): Decoded {
        asset.requireShape()
        val tiles = inflate(asset.compressedTiles, asset.storedPixelCount * Int.SIZE_BYTES)
        val cells = (asset.pixelWidth / TILE) * (asset.pixelHeight / TILE)
        val grid = inflate(asset.compressedGrid, cells * 2)
        repeat(cells) { cell ->
            require(tileId(grid, cell) < asset.tileCount) { "tiled map grid references an unavailable tile" }
        }
        return Decoded(tiles, grid)
    }

    internal fun tileId(grid: ByteArray, cell: Int): Int =
        (grid[cell * 2].toInt() and 0xff) or ((grid[cell * 2 + 1].toInt() and 0xff) shl 8)

    private fun inflate(compressed: ByteArray, expected: Int): ByteArray {
        val inflater = Inflater()
        return try {
            inflater.setInput(compressed)
            val output = ByteArray(expected)
            var offset = 0
            while (offset < expected) {
                val read = inflater.inflate(output, offset, expected - offset)
                require(read > 0) { "tiled map data ended before its declared size" }
                offset += read
            }
            require(inflater.inflate(ByteArray(1)) == 0 && inflater.finished() && inflater.remaining == 0) {
                "tiled map data exceeds its declared size or has trailing bytes"
            }
            output
        } catch (failure: DataFormatException) {
            throw IllegalArgumentException("tiled map data is not valid zlib", failure)
        } finally {
            inflater.end()
        }
    }

    private const val TILE = TiledMapAsset.TILE_PIXELS
    private const val CANCELLATION_CELLS = 64
}

object TiledLocalMapRasterRenderer {
    fun render(
        asset: TiledMapAsset,
        source: RasterRect = RasterRect(0, 0, asset.pixelWidth, asset.pixelHeight),
    ): RgbaSprite {
        asset.requireShape()
        LocalMapRasterRenderer.requireSource(source, asset.pixelWidth, asset.pixelHeight)
        val decoded = TiledMapRasterCodec.decode(asset)
        val gridWidth = asset.pixelWidth / TILE
        return RgbaSprite(source.width, source.height, IntArray(source.width * source.height) { pixel ->
            val x = source.x + pixel % source.width
            val y = source.y + pixel / source.width
            val cell = y / TILE * gridWidth + x / TILE
            val tile = TiledMapRasterCodec.tileId(decoded.grid, cell)
            val offset = (tile * TILE * TILE + y % TILE * TILE + x % TILE) * Int.SIZE_BYTES
            var argb = 0
            repeat(Int.SIZE_BYTES) { byte ->
                argb = argb or ((decoded.tiles[offset + byte].toInt() and 0xff) shl (byte * 8))
            }
            argb
        })
    }

    fun renderPng(asset: TiledMapAsset): RenderedMapAsset =
        RenderedMapAsset(PngEncoder.encode(render(asset)), null)

    private const val TILE = TiledMapAsset.TILE_PIXELS
}
