package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.Gen3MapLocationResolver
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.LocalMapRasterCodec
import com.enrpau.dualscreendex.parser.catalog.PngMapAsset
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.TimedIndexedMapAsset
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.sprite.GbaDecodeContract
import com.enrpau.dualscreendex.parser.sprite.GbaRomCompression
import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import com.enrpau.dualscreendex.parser.sprite.TileRenderer
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import java.util.concurrent.CancellationException

internal object Gen3LocalMapResolver {
    fun resolve(
        session: RomAnalysisSession,
        encounterBaseIds: Set<Int>,
        family: EngineFamily,
        codec: PokemonTextCodec?,
    ): LocalMapResolution {
        val cancellation = session.cancellation
        cancellation.throwIfCancellationRequested()
        val format = Gen3LocalMapFormat.forFamily(family)
            ?: return LocalMapResolution.Unavailable("tileset-abi", "no canonical local-map ABI exists for $family")
        val references = session.gbaReferenceIndex
            ?.takeUnless { it.overflowed }
            ?: return LocalMapResolution.Unavailable("reference-index", "compiled GBA reference index is unavailable")
        val completeHeaders = Gen3MapLocationResolver.resolveHeaderByBaseArea(
            session.rom,
            encounterBaseIds,
            references,
            cancellation,
        )
        cancellation.throwIfCancellationRequested()
        if (completeHeaders.isEmpty()) {
            return LocalMapResolution.Unavailable("map-groups", "no unique compiled gMapGroups authority was resolved")
        }
        if (completeHeaders.size > MAX_MAPS) {
            return LocalMapResolution.BudgetExceeded(
                "map-count",
                "resolved ${completeHeaders.size} map headers (limit $MAX_MAPS)",
            )
        }

        val names = Gen3MapLocationResolver.resolveNamesBySection(
            session.rom,
            encounterBaseIds,
            references,
            codec,
            cancellation,
            session.limits.maxDatasetExtentBytes,
        )
        cancellation.throwIfCancellationRequested()
        var selectedHeaders = completeHeaders
        var descriptorBatch = readDescriptors(session.rom, selectedHeaders, names, cancellation)
        if (descriptorBatch.descriptors.sumOf { it.pixelCount.toLong() } > MAX_TOTAL_PIXELS) {
            val reachableHeaders = Gen3MapLocationResolver.resolveReachableHeaderByBaseArea(
                session.rom,
                encounterBaseIds,
                references,
                cancellation,
            )
            cancellation.throwIfCancellationRequested()
            if (reachableHeaders.isEmpty()) {
                return LocalMapResolution.BudgetExceeded(
                    "raster-pixels",
                    "complete local-map catalog exceeds $MAX_TOTAL_PIXELS pixels and no complete reachable graph resolved",
                )
            }
            selectedHeaders = reachableHeaders
            descriptorBatch = readDescriptors(session.rom, selectedHeaders, names, cancellation)
        }
        val descriptors = descriptorBatch.descriptors
        val skippedReasons = descriptorBatch.skippedReasons
        val lightingModel = Gen3MapLightingResolver.resolve(session.rom)
        val tilesets = mutableMapOf<Int, TilesetData>()
        val maps = mutableListOf<LocalMap>()
        val assets = linkedMapOf<String, PngMapAsset>()
        val timedAssets = linkedMapOf<String, TimedIndexedMapAsset>()
        val assetKeyByPng = linkedMapOf<PngMapAsset, String>()
        val assetKeyByTimed = linkedMapOf<TimedIndexedMapAsset, String>()
        var encodedBytes = 0L
        var uniquePixels = 0L
        descriptors.forEach { descriptor ->
            cancellation.throwIfCancellationRequested()
            runCatching {
                val dynamicLighting = lightingModel != null && descriptor.naturalLighting
                val raster = renderIndexed(session.rom, descriptor, format, tilesets, dynamicLighting, cancellation)
                cancellation.throwIfCancellationRequested()
                val assetKey = if (dynamicLighting) {
                    val compressed = LocalMapRasterCodec.compress(raster.indices)
                    cancellation.throwIfCancellationRequested()
                    val asset = TimedIndexedMapAsset(
                        pixelWidth = raster.pixelWidth,
                        pixelHeight = raster.pixelHeight,
                        compressedIndices = compressed,
                        baseColors = raster.palettes.base,
                        alternateColors = raster.palettes.alternate,
                        alternatePaletteMask = raster.palettes.alternateMask,
                        paletteModel = requireNotNull(lightingModel),
                    )
                    assetKeyByTimed[asset] ?: descriptor.assetKey.also { newAssetKey ->
                        uniquePixels += descriptor.pixelCount
                        if (uniquePixels > MAX_TOTAL_PIXELS) {
                            return LocalMapResolution.BudgetExceeded(
                                "raster-pixels",
                                "resolved $uniquePixels unique local-map pixels (limit $MAX_TOTAL_PIXELS)",
                            )
                        }
                        encodedBytes += compressed.size + TIMED_PALETTE_BYTES
                        if (encodedBytes > MAX_ENCODED_BYTES) {
                            return LocalMapResolution.BudgetExceeded(
                                "encoded-assets",
                                "unique local-map assets exceed $MAX_ENCODED_BYTES bytes",
                            )
                        }
                        assetKeyByTimed[asset] = newAssetKey
                        timedAssets[newAssetKey] = asset
                    }
                } else {
                    val asset = PngMapAsset(PngEncoder.encode(raster.toRgbaSprite(cancellation), cancellation))
                    cancellation.throwIfCancellationRequested()
                    assetKeyByPng[asset] ?: descriptor.assetKey.also { newAssetKey ->
                        uniquePixels += descriptor.pixelCount
                        if (uniquePixels > MAX_TOTAL_PIXELS) {
                            return LocalMapResolution.BudgetExceeded(
                                "raster-pixels",
                                "resolved $uniquePixels unique local-map pixels (limit $MAX_TOTAL_PIXELS)",
                            )
                        }
                        encodedBytes += asset.bytes.size
                        if (encodedBytes > MAX_ENCODED_BYTES) {
                            return LocalMapResolution.BudgetExceeded(
                                "encoded-assets",
                                "unique local-map assets exceed $MAX_ENCODED_BYTES bytes",
                            )
                        }
                        assetKeyByPng[asset] = newAssetKey
                        assets[newAssetKey] = asset
                    }
                }
                cancellation.throwIfCancellationRequested()
                maps += descriptor.toLocalMap(assetKey)
            }.onFailure { failure ->
                if (failure is CancellationException) throw failure
                skippedReasons += "map 0x${descriptor.baseAreaId.toString(16).padStart(4, '0')} render: ${failure.message}"
            }
        }
        if (maps.isEmpty()) {
            return LocalMapResolution.Unavailable("render", "no local map could be rendered from resolved map headers")
        }
        cancellation.throwIfCancellationRequested()
        val scenes = runCatching {
            Gen3MapSceneResolver.resolve(session.rom, selectedHeaders, maps).also {
                cancellation.throwIfCancellationRequested()
            }
        }.getOrElse { failure ->
            if (failure is CancellationException) throw failure
            emptyList()
        }
        cancellation.throwIfCancellationRequested()
        val poiResolution = Gen3LocalMapPoiResolver.resolve(
            session.rom,
            selectedHeaders,
            maps,
            family,
            codec,
        )
        cancellation.throwIfCancellationRequested()
        val catalog = LocalMapCatalog(
            maps = maps,
            assets = assets,
            timedAssets = timedAssets,
            scenes = scenes,
            pois = poiResolution.pois,
        ).validate()
        cancellation.throwIfCancellationRequested()
        return LocalMapResolution.Resolved(
            catalog = catalog,
            reasons = listOf(
                "resolved ${maps.size} local maps from a compiled gMapGroups consumer",
                "rendered ${assets.size + timedAssets.size} unique bounded ${format.label} 16x16 metatile rasters " +
                    "from ROM tilesets and palettes",
            ) + if (scenes.isNotEmpty()) {
                listOf("placed ${scenes.sumOf { it.placements.size }} maps in ${scenes.size} connection-derived scenes")
            } else {
                emptyList()
            } + if (lightingModel != null) {
                listOf("retained ${timedAssets.size} source-backed natural-light maps as indexed timed rasters")
            } else {
                emptyList()
            } + if (codec == null) {
                listOf(
                    "resolved ${poiResolution.pois.size} bounded structural local-map POIs; " +
                        "omitted localized Gen III map and POI names because no ROM text codec was authoritative",
                )
            } else if (poiResolution.pois.isNotEmpty()) {
                listOf("resolved ${poiResolution.pois.size} bounded local-map POIs from typed MapEvents records")
            } else {
                emptyList()
            } + skippedReasons + poiResolution.skippedReasons,
            skippedMaps = skippedReasons.size,
            partialSubsystemFailures = poiResolution.skippedReasons.size,
        )
    }

    private fun readDescriptors(
        rom: RomImage,
        headers: Map<Int, Int>,
        names: Map<Int, String>,
        cancellation: ParserCancellationToken,
    ): DescriptorBatch {
        val descriptors = mutableListOf<MapDescriptor>()
        val skippedReasons = mutableListOf<String>()
        headers.toSortedMap().forEach { (baseAreaId, header) ->
            cancellation.throwIfCancellationRequested()
            runCatching { readDescriptor(rom, baseAreaId, header, names) }
                .onSuccess {
                    cancellation.throwIfCancellationRequested()
                    descriptors += it
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    skippedReasons += "map 0x${baseAreaId.toString(16).padStart(4, '0')} metadata: ${failure.message}"
                }
        }
        cancellation.throwIfCancellationRequested()
        return DescriptorBatch(descriptors, skippedReasons)
    }

    private fun readDescriptor(
        rom: RomImage,
        baseAreaId: Int,
        header: Int,
        names: Map<Int, String>,
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
        val displayName = names[section]
        return MapDescriptor(
            baseAreaId = baseAreaId,
            displayName = displayName,
            width = width,
            height = height,
            mapCells = mapCells,
            primaryTileset = primaryTileset,
            secondaryTileset = secondaryTileset,
            mapType = rom.u8(header + MAP_TYPE_OFFSET),
        )
    }

    private fun boundedDimension(raw: Long, label: String): Int {
        require(raw in 1..MAX_GRID_DIMENSION.toLong()) { "local-map $label exceeds structural bound" }
        return raw.toInt()
    }

    private fun renderIndexed(
        rom: RomImage,
        map: MapDescriptor,
        format: Gen3LocalMapFormat,
        tilesetCache: MutableMap<Int, TilesetData>,
        dynamicLighting: Boolean,
        cancellation: ParserCancellationToken,
    ): IndexedRaster {
        cancellation.throwIfCancellationRequested()
        val primary = tilesetCache.getOrPut(map.primaryTileset) { readTileset(rom, map.primaryTileset, format) }
        cancellation.throwIfCancellationRequested()
        val secondary = tilesetCache.getOrPut(map.secondaryTileset) { readTileset(rom, map.secondaryTileset, format) }
        cancellation.throwIfCancellationRequested()
        require(!primary.secondary && secondary.secondary) { "map layout has invalid primary/secondary tileset roles" }
        val palettes = readPalettes(rom, primary, secondary, format, dynamicLighting)
        cancellation.throwIfCancellationRequested()
        val metatileCache = mutableMapOf<Int, ByteArray>()
        val pixelWidth = map.width * METATILE_PIXELS
        val pixels = ByteArray(map.pixelCount)
        repeat(map.height) { mapY ->
            repeat(map.width) { mapX ->
                val cellIndex = mapY * map.width + mapX
                if (cellIndex % RASTER_CANCELLATION_CELLS == 0) {
                    cancellation.throwIfCancellationRequested()
                }
                val cell = rom.u16le(map.mapCells + cellIndex * 2)
                val metatileId = cell and format.metatileIdMask
                val metatile = metatileCache.getOrPut(metatileId) {
                    renderMetatile(rom, metatileId, primary, secondary, format)
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
        cancellation.throwIfCancellationRequested()
        return IndexedRaster(pixelWidth, map.height * METATILE_PIXELS, pixels, palettes)
    }

    private fun readTileset(rom: RomImage, offset: Int, format: Gen3LocalMapFormat): TilesetData {
        require(offset >= 0 && offset.toLong() + TILESET_BYTES <= rom.size.toLong()) { "tileset is truncated" }
        val flags = rom.u8(offset)
        val secondary = rom.u8(offset + 1) != 0
        val graphicsOffset = requireNotNull(rom.gbaPointer(offset + 4)) { "tileset has no graphics" }
        val tileCount = if (secondary) format.totalTiles - format.primaryTiles else format.primaryTiles
        val graphicsBytes = tileCount * TILE_BYTES
        val graphics = if (flags and COMPRESSED_FLAG != 0) {
            val decoded = GbaRomCompression.decodeAt(rom, graphicsOffset, GbaDecodeContract.LOCAL_MAP)
            require(decoded.size <= graphicsBytes) {
                "compressed tileset graphics exceed canonical ${format.label} capacity"
            }
            decoded.copyOf(graphicsBytes)
        } else {
            require(graphicsOffset.toLong() + graphicsBytes <= rom.size.toLong()) {
                "uncompressed tileset graphics are truncated"
            }
            rom.slice(graphicsOffset, graphicsBytes)
        }
        require(graphics.isNotEmpty() && graphics.size % TILE_BYTES == 0) {
            "tileset graphics are not complete 4bpp tiles"
        }
        return TilesetData(
            secondary = secondary,
            graphics = graphics,
            palettes = requireNotNull(rom.gbaPointer(offset + 8)) { "tileset has no palettes" },
            metatiles = requireNotNull(rom.gbaPointer(offset + 12)) { "tileset has no metatiles" },
            alternatePaletteMask = flags ushr 1,
            lightPaletteMask = rom.u8(offset + 2),
            customLightColorMask = rom.u8(offset + 3),
        )
    }

    private fun readPalettes(
        rom: RomImage,
        primary: TilesetData,
        secondary: TilesetData,
        format: Gen3LocalMapFormat,
        dynamicLighting: Boolean,
    ): PaletteData {
        val base = IntArray(format.totalPalettes * COLORS_PER_PALETTE)
        val alternate = IntArray(base.size)
        repeat(format.primaryPalettes) { palette ->
            readPalette(rom, primary.palettes, palette, base, palette)
            if (dynamicLighting) {
                readPalette(
                    rom,
                    primary.palettes,
                    (palette + ALTERNATE_PALETTE_DELTA) % HARDWARE_BG_PALETTES,
                    alternate,
                    palette,
                )
            }
        }
        repeat(format.totalPalettes - format.primaryPalettes) { index ->
            val palette = index + format.primaryPalettes
            readPalette(rom, secondary.palettes, palette, base, palette)
            if (dynamicLighting) {
                val alternatePalette = (palette + ALTERNATE_PALETTE_DELTA) % HARDWARE_BG_PALETTES
                readPalette(rom, secondary.palettes, alternatePalette, alternate, palette)
            }
        }
        if (!dynamicLighting) return PaletteData(base, base.copyOf(), 0)
        markLightColors(base, primary, 0, format.primaryPalettes)
        markLightColors(base, secondary, format.primaryPalettes, MAP_PALETTES)
        val alternateMask = (
            (primary.alternatePaletteMask and ((1 shl format.primaryPalettes) - 1)) or
                (secondary.alternatePaletteMask shl format.primaryPalettes)
            ) and MAP_PALETTE_MASK and 0xFFFE
        return PaletteData(base, alternate, alternateMask)
    }

    private fun markLightColors(colors: IntArray, tileset: TilesetData, low: Int, high: Int) {
        repeat(high - low) { localPalette ->
            if (tileset.lightPaletteMask and (1 shl localPalette) == 0) return@repeat
            val start = (low + localPalette) * COLORS_PER_PALETTE
            var markedColors = colors[start]
            var color = 1
            while (color < COLORS_PER_PALETTE && markedColors != 0) {
                if (markedColors and 1 != 0) colors[start + color] = colors[start + color] or LIGHT_MARKER
                markedColors = markedColors ushr 1
                color++
            }
            if (tileset.customLightColorMask and (1 shl localPalette) != 0) {
                colors[start] = colors[start + COLORS_PER_PALETTE - 1] or LIGHT_MARKER
            }
        }
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
            colors[destinationPalette * COLORS_PER_PALETTE + color] = rom.u16le(offset.toInt() + color * 2)
        }
    }

    private fun renderMetatile(
        rom: RomImage,
        metatileId: Int,
        primary: TilesetData,
        secondary: TilesetData,
        format: Gen3LocalMapFormat,
    ): ByteArray {
        val tileset = if (metatileId < format.primaryMetatiles) primary else secondary
        val localId = if (metatileId < format.primaryMetatiles) {
            metatileId
        } else {
            metatileId - format.primaryMetatiles
        }
        val metatileOffset = tileset.metatiles.toLong() + localId.toLong() * format.metatileBytes
        require(metatileOffset >= 0 && metatileOffset + format.metatileBytes <= rom.size.toLong()) {
            "metatile definition is truncated"
        }
        val pixels = ByteArray(METATILE_PIXELS * METATILE_PIXELS)
        repeat(format.layersPerMetatile) { layer ->
            repeat(4) { quadrant ->
                val entry = rom.u16le(metatileOffset.toInt() + (layer * 4 + quadrant) * 2)
                drawTile(
                    entry = entry,
                    primary = primary,
                    secondary = secondary,
                    format = format,
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
        format: Gen3LocalMapFormat,
        pixels: ByteArray,
        originX: Int,
        originY: Int,
        transparentZero: Boolean,
    ) {
        val tileId = entry and TILE_ID_MASK
        val tileset = if (tileId < format.primaryTiles) primary else secondary
        val localId = if (tileId < format.primaryTiles) tileId else tileId - format.primaryTiles
        require((localId + 1).toLong() * TILE_BYTES <= tileset.graphics.size.toLong()) {
            val role = if (tileset.secondary) "secondary" else "primary"
            "metatile references unavailable $role tile graphics " +
                "(tile=$tileId local=$localId available=${tileset.graphics.size / TILE_BYTES})"
        }
        val palette = entry ushr PALETTE_SHIFT and PALETTE_MASK
        require(palette < format.totalPalettes) { "metatile references an unavailable palette" }
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
                        (palette * COLORS_PER_PALETTE + colorIndex).toByte()
                }
            }
        }
    }

    private data class Gen3LocalMapFormat(
        val label: String,
        val primaryTiles: Int,
        val totalTiles: Int,
        val primaryMetatiles: Int,
        val metatileIdMask: Int,
        val primaryPalettes: Int,
        val totalPalettes: Int,
        val layersPerMetatile: Int,
    ) {
        val metatileBytes: Int = layersPerMetatile * TILES_PER_LAYER * 2

        companion object {
            fun forFamily(family: EngineFamily): Gen3LocalMapFormat? = when (family) {
                EngineFamily.RUBY_SAPPHIRE -> Gen3LocalMapFormat(
                    label = "RSE",
                    primaryTiles = 512,
                    totalTiles = 1024,
                    primaryMetatiles = 512,
                    metatileIdMask = 0x03FF,
                    primaryPalettes = 6,
                    // Tileset arrays retain all hardware-addressable BG banks. Maps may reference
                    // dynamically loaded banks above the engine's routinely copied palette range.
                    totalPalettes = HARDWARE_BG_PALETTES,
                    layersPerMetatile = 2,
                )
                EngineFamily.EMERALD -> Gen3LocalMapFormat(
                    label = "Emerald",
                    primaryTiles = 512,
                    totalTiles = 1024,
                    primaryMetatiles = 512,
                    metatileIdMask = 0x03FF,
                    primaryPalettes = 6,
                    totalPalettes = HARDWARE_BG_PALETTES,
                    layersPerMetatile = 2,
                )
                EngineFamily.FIRERED_LEAFGREEN -> Gen3LocalMapFormat(
                    label = "FRLG",
                    primaryTiles = 640,
                    totalTiles = 1024,
                    primaryMetatiles = 640,
                    metatileIdMask = 0x03FF,
                    primaryPalettes = 7,
                    totalPalettes = HARDWARE_BG_PALETTES,
                    layersPerMetatile = 2,
                )
                else -> null
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
        val mapType: Int,
    ) {
        val pixelCount: Int = width * METATILE_PIXELS * height * METATILE_PIXELS
        val naturalLighting: Boolean = mapType in NATURAL_LIGHT_MAP_TYPES
        val key: String = "local/${baseAreaId.toString(16).padStart(4, '0')}"
        val assetKey: String = "$key/map"

        fun toLocalMap(imageAssetKey: String): LocalMap = LocalMap(
            key = key,
            displayName = displayName,
            baseAreaId = baseAreaId,
            pixelWidth = width * METATILE_PIXELS,
            pixelHeight = height * METATILE_PIXELS,
            gridWidth = width,
            gridHeight = height,
            imageAssetKey = imageAssetKey,
        )
    }

    private data class DescriptorBatch(
        val descriptors: MutableList<MapDescriptor>,
        val skippedReasons: MutableList<String>,
    )

    private data class TilesetData(
        val secondary: Boolean,
        val graphics: ByteArray,
        val palettes: Int,
        val metatiles: Int,
        val alternatePaletteMask: Int,
        val lightPaletteMask: Int,
        val customLightColorMask: Int,
    )

    private data class PaletteData(
        val base: IntArray,
        val alternate: IntArray,
        val alternateMask: Int,
    )

    private data class IndexedRaster(
        val pixelWidth: Int,
        val pixelHeight: Int,
        val indices: ByteArray,
        val palettes: PaletteData,
    ) {
        fun toRgbaSprite(cancellation: ParserCancellationToken): RgbaSprite = RgbaSprite(
            pixelWidth,
            pixelHeight,
            IntArray(indices.size) { index ->
                if (index % PIXEL_CANCELLATION_INTERVAL == 0) {
                    cancellation.throwIfCancellationRequested()
                }
                TileRenderer.bgr555ToArgb(palettes.base[indices[index].toInt() and 0xff] and 0x7FFF, false)
            }.also {
                cancellation.throwIfCancellationRequested()
            },
        )
    }

    private const val MAP_SECTION_OFFSET = 0x14
    private const val MAP_TYPE_OFFSET = 0x17
    private const val TILESET_BYTES = 24
    private const val COMPRESSED_FLAG = 1
    private const val TILE_ID_MASK = 0x03FF
    private const val FLIP_X_MASK = 0x0400
    private const val FLIP_Y_MASK = 0x0800
    private const val PALETTE_SHIFT = 12
    private const val PALETTE_MASK = 0x0F
    private const val COLORS_PER_PALETTE = 16
    private const val HARDWARE_BG_PALETTES = 16
    private const val MAP_PALETTES = 13
    private const val MAP_PALETTE_MASK = (1 shl MAP_PALETTES) - 1
    private const val ALTERNATE_PALETTE_DELTA = 9
    private const val LIGHT_MARKER = 0x8000
    private const val TIMED_PALETTE_BYTES = HARDWARE_BG_PALETTES * COLORS_PER_PALETTE * 2 * Int.SIZE_BYTES
    private val NATURAL_LIGHT_MAP_TYPES = setOf(1, 2, 3, 6)
    private const val TILE_BYTES = 32
    private const val TILE_PIXELS = 8
    private const val TILES_PER_LAYER = 4
    private const val METATILE_PIXELS = 16
    private const val RASTER_CANCELLATION_CELLS = 64
    private const val PIXEL_CANCELLATION_INTERVAL = 4_096
    private const val MAX_MAPS = 1024
    private const val MAX_GRID_DIMENSION = 256
    private const val MAX_MAP_PIXELS = 2_000_000L
    private const val MAX_TOTAL_PIXELS = 100_000_000L
    private const val MAX_ENCODED_BYTES = 64L * 1024 * 1024
}
