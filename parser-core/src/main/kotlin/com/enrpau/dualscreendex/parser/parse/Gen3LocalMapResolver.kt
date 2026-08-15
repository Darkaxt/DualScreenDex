package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.Gen3MapLocationResolver
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.PngMapAsset
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.sprite.GbaRomCompression
import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import com.enrpau.dualscreendex.parser.sprite.TileRenderer

internal object Gen3LocalMapResolver {
    fun resolve(session: RomAnalysisSession, encounterBaseIds: Set<Int>): LocalMapResolution {
        val references = session.gbaReferenceIndex
            ?.takeUnless { it.overflowed }
            ?: return LocalMapResolution.Unavailable("reference-index", "compiled GBA reference index is unavailable")
        val headers = Gen3MapLocationResolver.resolveHeaderByBaseArea(session.rom, encounterBaseIds, references)
        if (headers.isEmpty()) {
            return LocalMapResolution.Unavailable("map-groups", "no unique compiled gMapGroups authority was resolved")
        }
        if (headers.size > MAX_MAPS) {
            return LocalMapResolution.BudgetExceeded(
                "map-count",
                "resolved ${headers.size} map headers (limit $MAX_MAPS)",
            )
        }

        val names = Gen3MapLocationResolver.resolveDetailed(session.rom, encounterBaseIds, references)
        val descriptors = mutableListOf<MapDescriptor>()
        val skippedReasons = mutableListOf<String>()
        headers.toSortedMap().forEach { (baseAreaId, header) ->
            runCatching { readDescriptor(session.rom, baseAreaId, header, names) }
                .onSuccess(descriptors::add)
                .onFailure { failure ->
                    skippedReasons += "map 0x${baseAreaId.toString(16).padStart(4, '0')} metadata: ${failure.message}"
                }
        }
        val totalPixels = descriptors.sumOf { it.pixelCount.toLong() }
        if (totalPixels > MAX_TOTAL_PIXELS) {
            return LocalMapResolution.BudgetExceeded(
                "raster-pixels",
                "resolved $totalPixels local-map pixels (limit $MAX_TOTAL_PIXELS)",
            )
        }

        val tilesets = mutableMapOf<Int, TilesetData>()
        val maps = mutableListOf<LocalMap>()
        val assets = linkedMapOf<String, PngMapAsset>()
        var encodedBytes = 0L
        descriptors.forEach { descriptor ->
            runCatching {
                val raster = render(session.rom, descriptor, tilesets)
                val png = PngEncoder.encode(raster)
                encodedBytes += png.size
                if (encodedBytes > MAX_ENCODED_BYTES) {
                    return LocalMapResolution.BudgetExceeded(
                        "encoded-assets",
                        "local-map PNG assets exceed $MAX_ENCODED_BYTES bytes",
                    )
                }
                maps += descriptor.toLocalMap()
                assets[descriptor.assetKey] = PngMapAsset(png)
            }.onFailure { failure ->
                skippedReasons += "map 0x${descriptor.baseAreaId.toString(16).padStart(4, '0')} render: ${failure.message}"
            }
        }
        if (maps.isEmpty()) {
            return LocalMapResolution.Unavailable("render", "no local map could be rendered from resolved map headers")
        }
        val catalog = LocalMapCatalog(maps, assets).validate()
        return LocalMapResolution.Resolved(
            catalog = catalog,
            reasons = listOf(
                "resolved ${maps.size} local maps from a compiled gMapGroups consumer",
                "rendered bounded 16x16 metatile rasters from ROM tilesets and palettes",
            ) + skippedReasons,
            skippedMaps = skippedReasons.size,
        )
    }

    private fun readDescriptor(
        rom: RomImage,
        baseAreaId: Int,
        header: Int,
        names: com.enrpau.dualscreendex.parser.catalog.Gen3MapLocationResolution?,
    ): MapDescriptor {
        val layout = requireNotNull(rom.gbaPointer(header)) { "map header has no layout" }
        val width = boundedDimension(rom.u32le(layout), "width")
        val height = boundedDimension(rom.u32le(layout + 4), "height")
        val pixelCount = width.toLong() * METATILE_PIXELS * height.toLong() * METATILE_PIXELS
        require(pixelCount <= MAX_MAP_PIXELS) { "local map exceeds per-map pixel bound" }
        val mapCells = requireNotNull(rom.gbaPointer(layout + 12)) { "map layout has no cell grid" }
        val primaryTileset = requireNotNull(rom.gbaPointer(layout + 16)) { "map layout has no primary tileset" }
        val secondaryTileset = requireNotNull(rom.gbaPointer(layout + 20)) { "map layout has no secondary tileset" }
        require(mapCells.toLong() + width.toLong() * height * 2L <= rom.size.toLong()) {
            "map cell grid is truncated"
        }
        val section = rom.u8(header + MAP_SECTION_OFFSET)
        val displayName = names?.entriesBySection?.get(section)?.displayName
        return MapDescriptor(
            baseAreaId = baseAreaId,
            displayName = displayName,
            width = width,
            height = height,
            mapCells = mapCells,
            primaryTileset = primaryTileset,
            secondaryTileset = secondaryTileset,
        )
    }

    private fun boundedDimension(raw: Long, label: String): Int {
        require(raw in 1..MAX_GRID_DIMENSION.toLong()) { "local-map $label exceeds structural bound" }
        return raw.toInt()
    }

    private fun render(
        rom: RomImage,
        map: MapDescriptor,
        tilesetCache: MutableMap<Int, TilesetData>,
    ): RgbaSprite {
        val primary = tilesetCache.getOrPut(map.primaryTileset) { readTileset(rom, map.primaryTileset) }
        val secondary = tilesetCache.getOrPut(map.secondaryTileset) { readTileset(rom, map.secondaryTileset) }
        require(!primary.secondary && secondary.secondary) { "map layout has invalid primary/secondary tileset roles" }
        val palettes = readPalettes(rom, primary, secondary)
        val metatileCache = mutableMapOf<Int, IntArray>()
        val pixelWidth = map.width * METATILE_PIXELS
        val pixels = IntArray(map.pixelCount)
        repeat(map.height) { mapY ->
            repeat(map.width) { mapX ->
                val cell = rom.u16le(map.mapCells + (mapY * map.width + mapX) * 2)
                val metatileId = cell and METATILE_ID_MASK
                val metatile = metatileCache.getOrPut(metatileId) {
                    renderMetatile(rom, metatileId, primary, secondary, palettes)
                }
                repeat(METATILE_PIXELS) { row ->
                    metatile.copyInto(
                        pixels,
                        destinationOffset = (mapY * METATILE_PIXELS + row) * pixelWidth + mapX * METATILE_PIXELS,
                        startIndex = row * METATILE_PIXELS,
                        endIndex = (row + 1) * METATILE_PIXELS,
                    )
                }
            }
        }
        return RgbaSprite(pixelWidth, map.height * METATILE_PIXELS, pixels)
    }

    private fun readTileset(rom: RomImage, offset: Int): TilesetData {
        require(offset >= 0 && offset.toLong() + TILESET_BYTES <= rom.size.toLong()) { "tileset is truncated" }
        val graphicsOffset = requireNotNull(rom.gbaPointer(offset + 4)) { "tileset has no graphics" }
        val graphics = if (rom.u8(offset) and COMPRESSED_FLAG != 0) {
            GbaRomCompression.decodeAt(rom, graphicsOffset)
        } else {
            require(graphicsOffset.toLong() + TILESET_GRAPHICS_BYTES <= rom.size.toLong()) {
                "uncompressed tileset graphics are truncated"
            }
            rom.slice(graphicsOffset, TILESET_GRAPHICS_BYTES)
        }
        require(graphics.isNotEmpty() && graphics.size % TILE_BYTES == 0) {
            "tileset graphics are not complete 4bpp tiles"
        }
        return TilesetData(
            secondary = rom.u8(offset + 1) != 0,
            graphics = graphics,
            palettes = requireNotNull(rom.gbaPointer(offset + 8)) { "tileset has no palettes" },
            metatiles = requireNotNull(rom.gbaPointer(offset + 12)) { "tileset has no metatiles" },
        )
    }

    private fun readPalettes(rom: RomImage, primary: TilesetData, secondary: TilesetData): IntArray {
        val colors = IntArray(TOTAL_PALETTES * COLORS_PER_PALETTE)
        repeat(PRIMARY_PALETTES) { palette ->
            readPalette(rom, primary.palettes, palette, colors, palette)
        }
        repeat(SECONDARY_PALETTES) { index ->
            val palette = index + PRIMARY_PALETTES
            readPalette(rom, secondary.palettes, palette, colors, palette)
        }
        return colors
    }

    private fun readPalette(
        rom: RomImage,
        source: Int,
        sourcePalette: Int,
        colors: IntArray,
        destinationPalette: Int,
    ) {
        val offset = source.toLong() + sourcePalette.toLong() * COLORS_PER_PALETTE * 2L
        require(offset >= 0 && offset + COLORS_PER_PALETTE * 2L <= rom.size.toLong()) {
            "tileset palette is truncated"
        }
        repeat(COLORS_PER_PALETTE) { color ->
            colors[destinationPalette * COLORS_PER_PALETTE + color] = TileRenderer.bgr555ToArgb(
                rom.u16le(offset.toInt() + color * 2),
                transparent = false,
            )
        }
    }

    private fun renderMetatile(
        rom: RomImage,
        metatileId: Int,
        primary: TilesetData,
        secondary: TilesetData,
        palettes: IntArray,
    ): IntArray {
        val tileset = if (metatileId < PRIMARY_METATILES) primary else secondary
        val localId = if (metatileId < PRIMARY_METATILES) metatileId else metatileId - PRIMARY_METATILES
        val metatileOffset = tileset.metatiles.toLong() + localId.toLong() * METATILE_BYTES
        require(metatileOffset >= 0 && metatileOffset + METATILE_BYTES <= rom.size.toLong()) {
            "metatile definition is truncated"
        }
        val pixels = IntArray(METATILE_PIXELS * METATILE_PIXELS)
        repeat(2) { layer ->
            repeat(4) { quadrant ->
                val entry = rom.u16le(metatileOffset.toInt() + (layer * 4 + quadrant) * 2)
                drawTile(
                    entry = entry,
                    primary = primary,
                    secondary = secondary,
                    palettes = palettes,
                    pixels = pixels,
                    originX = (quadrant and 1) * TILE_PIXELS,
                    originY = (quadrant ushr 1) * TILE_PIXELS,
                    transparentZero = layer == 1,
                )
            }
        }
        return pixels
    }

    private fun drawTile(
        entry: Int,
        primary: TilesetData,
        secondary: TilesetData,
        palettes: IntArray,
        pixels: IntArray,
        originX: Int,
        originY: Int,
        transparentZero: Boolean,
    ) {
        val tileId = entry and TILE_ID_MASK
        val tileset = if (tileId < PRIMARY_TILES) primary else secondary
        val localId = if (tileId < PRIMARY_TILES) tileId else tileId - PRIMARY_TILES
        require((localId + 1).toLong() * TILE_BYTES <= tileset.graphics.size.toLong()) {
            "metatile references unavailable tile graphics"
        }
        val palette = entry ushr PALETTE_SHIFT and PALETTE_MASK
        require(palette < TOTAL_PALETTES) { "metatile references an unavailable palette" }
        val flipX = entry and FLIP_X_MASK != 0
        val flipY = entry and FLIP_Y_MASK != 0
        repeat(TILE_PIXELS) { y ->
            val sourceY = if (flipY) TILE_PIXELS - 1 - y else y
            repeat(TILE_PIXELS) { x ->
                val sourceX = if (flipX) TILE_PIXELS - 1 - x else x
                val packed = tileset.graphics[localId * TILE_BYTES + sourceY * 4 + sourceX / 2].toInt() and 0xFF
                val colorIndex = if (sourceX and 1 == 0) packed and 0x0F else packed ushr 4
                if (!transparentZero || colorIndex != 0) {
                    pixels[(originY + y) * METATILE_PIXELS + originX + x] =
                        palettes[palette * COLORS_PER_PALETTE + colorIndex]
                }
            }
        }
    }

    private data class MapDescriptor(
        val baseAreaId: Int,
        val displayName: String?,
        val width: Int,
        val height: Int,
        val mapCells: Int,
        val primaryTileset: Int,
        val secondaryTileset: Int,
    ) {
        val pixelCount: Int = width * METATILE_PIXELS * height * METATILE_PIXELS
        val key: String = "local/${baseAreaId.toString(16).padStart(4, '0')}"
        val assetKey: String = "$key/map"

        fun toLocalMap(): LocalMap = LocalMap(
            key = key,
            displayName = displayName,
            baseAreaId = baseAreaId,
            pixelWidth = width * METATILE_PIXELS,
            pixelHeight = height * METATILE_PIXELS,
            gridWidth = width,
            gridHeight = height,
            imageAssetKey = assetKey,
        )
    }

    private data class TilesetData(
        val secondary: Boolean,
        val graphics: ByteArray,
        val palettes: Int,
        val metatiles: Int,
    )

    private const val MAP_SECTION_OFFSET = 0x14
    private const val TILESET_BYTES = 24
    private const val TILESET_GRAPHICS_BYTES = 512 * 32
    private const val COMPRESSED_FLAG = 1
    private const val METATILE_ID_MASK = 0x03FF
    private const val TILE_ID_MASK = 0x03FF
    private const val FLIP_X_MASK = 0x0400
    private const val FLIP_Y_MASK = 0x0800
    private const val PALETTE_SHIFT = 12
    private const val PALETTE_MASK = 0x0F
    private const val PRIMARY_TILES = 512
    private const val PRIMARY_METATILES = 512
    private const val PRIMARY_PALETTES = 6
    private const val SECONDARY_PALETTES = 7
    private const val TOTAL_PALETTES = PRIMARY_PALETTES + SECONDARY_PALETTES
    private const val COLORS_PER_PALETTE = 16
    private const val TILE_BYTES = 32
    private const val TILE_PIXELS = 8
    private const val METATILE_BYTES = 16
    private const val METATILE_PIXELS = 16
    private const val MAX_MAPS = 1024
    private const val MAX_GRID_DIMENSION = 256
    private const val MAX_MAP_PIXELS = 2_000_000L
    private const val MAX_TOTAL_PIXELS = 100_000_000L
    private const val MAX_ENCODED_BYTES = 64L * 1024 * 1024
}
