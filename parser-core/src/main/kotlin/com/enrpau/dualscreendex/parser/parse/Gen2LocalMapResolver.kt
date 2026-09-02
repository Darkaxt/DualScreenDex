package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.IndexedMapAsset
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.LocalMapLightingPolicy
import com.enrpau.dualscreendex.parser.catalog.LocalMapRasterCodec
import com.enrpau.dualscreendex.parser.catalog.MapLightingPalettes
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.sprite.IndexedSprite
import com.enrpau.dualscreendex.parser.sprite.Lz3Decoder
import com.enrpau.dualscreendex.parser.sprite.TileRenderer
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

internal object Gen2LocalMapResolver {
    fun resolve(
        session: RomAnalysisSession,
        encounterBaseIds: Set<Int>,
        family: EngineFamily,
        codec: PokemonTextCodec?,
    ): LocalMapResolution {
        val label = when (family) {
            EngineFamily.GOLD_SILVER -> "Gold/Silver"
            EngineFamily.CRYSTAL -> "Crystal"
            else -> return LocalMapResolution.Unavailable(
                "map-abi",
                "no canonical Gen II local-map ABI exists for $family",
            )
        }
        val requiredMaps = encounterBaseIds.filterTo(sortedSetOf()) { baseAreaId ->
            val group = baseAreaId ushr 8
            val map = baseAreaId and 0xff
            group in 1..MAX_MAP_GROUPS && map in 1..MAX_MAPS_PER_GROUP
        }
        if (requiredMaps.isEmpty()) {
            return LocalMapResolution.Unavailable("encounter-binding", "no encounter-bound Gen II group/map IDs")
        }

        val groupAuthorities = Gen2WorldMapResolver.findMapGroupRoots(session.rom, requiredMaps)
        val tilesetAuthorities = findTilesetAuthorities(session.rom)
        val roofAuthorities = findRoofAuthorities(session.rom)
        val paletteAuthorities = findPaletteAuthorities(session.rom)
        val tilePaletteBanks = findTilePaletteBanks(session.rom)
        if (System.getenv("DUALDEX_MAP_TRACE") == "1") {
            println(
                "local-map-trace gen2 candidates groups=${groupAuthorities.map { it.tableOffset }} " +
                    "tilesets=${tilesetAuthorities.map { it.root }} roofs=${roofAuthorities.map { it.table }} " +
                    "palettes=${paletteAuthorities.map { it.environmentPointers }} tilePaletteBanks=$tilePaletteBanks",
            )
        }
        val authorities = buildList {
            groupAuthorities.forEach { groups ->
                tilesetAuthorities.forEach { tilesets ->
                    roofAuthorities.forEach { roofs ->
                        paletteAuthorities.forEach { palettes ->
                            tilePaletteBanks.forEach { tilePaletteBank ->
                                buildAuthority(
                                    session.rom,
                                    groups,
                                    tilesets,
                                    roofs,
                                    palettes,
                                    tilePaletteBank,
                                    requiredMaps,
                                )?.let(::add)
                            }
                        }
                    }
                }
            }
        }.distinctBy { authority ->
            listOf(
                authority.groups.tableOffset,
                authority.tilesets.root,
                authority.roofs.table,
                authority.palettes.environmentPointers,
                authority.palettes.tilesetPalettes,
                authority.palettes.roofPalettes,
                authority.palettes.timeOfDayWramOffset,
                authority.tilePaletteBank,
            )
        }
        if (authorities.isEmpty()) {
            return LocalMapResolution.Unavailable(
                "map-authority",
                "no complete Gen II map-group, tileset, roof, and palette consumer chain resolved every encounter map",
            )
        }
        if (authorities.size != 1) {
            return LocalMapResolution.Unavailable(
                "map-authority",
                "${authorities.size} complete Gen II local-map authority chains remained",
            )
        }

        val authority = authorities.single()
        if (authority.descriptors.size > MAX_MAPS) {
            return LocalMapResolution.BudgetExceeded(
                "map-count",
                "resolved ${authority.descriptors.size} Gen II maps (limit $MAX_MAPS)",
            )
        }
        val totalPixels = authority.descriptors.sumOf(MapDescriptor::pixelCount)
        if (totalPixels > MAX_TOTAL_PIXELS) {
            return LocalMapResolution.BudgetExceeded(
                "raster-pixels",
                "resolved $totalPixels local-map pixels (limit $MAX_TOTAL_PIXELS)",
            )
        }
        val landmarkNames = codec?.let {
            runCatching {
                Gen2WorldMapResolver.resolveLandmarkNames(
                    session,
                    requiredMaps,
                    it,
                    authority.descriptors.mapTo(linkedSetOf(), MapDescriptor::landmarkId),
                )
            }.getOrDefault(emptyMap())
        }.orEmpty()
        val namedMapCount = authority.descriptors.count { landmarkNames.containsKey(it.landmarkId) }

        val maps = mutableListOf<LocalMap>()
        val assets = linkedMapOf<String, IndexedMapAsset>()
        val skippedReasons = mutableListOf<String>()
        val tileVariants = mutableMapOf<TilesetVariant, IndexedSprite>()
        var compressedBytes = 0L
        authority.descriptors.forEach { descriptor ->
            runCatching {
                val tileset = authority.tilesetData.getValue(descriptor.tilesetId)
                val roofIndex = authority.roofs.indexFor(descriptor)
                val variant = TilesetVariant(descriptor.tilesetId, roofIndex)
                val tiles = tileVariants.getOrPut(variant) {
                    tileset.renderTiles(session.rom, authority.roofs, roofIndex)
                }
                val indices = renderIndices(descriptor, tileset, tiles)
                val asset = IndexedMapAsset(
                    pixelWidth = descriptor.gridWidth * METATILE_PIXELS,
                    pixelHeight = descriptor.gridHeight * METATILE_PIXELS,
                    compressedIndices = LocalMapRasterCodec.compress(indices),
                    lightingPolicy = descriptor.lightingPolicy,
                    palettes = authority.palettes.palettesFor(session.rom, descriptor),
                ).validate()
                compressedBytes += asset.compressedIndices.size
                if (compressedBytes > MAX_COMPRESSED_ASSET_BYTES) {
                    return LocalMapResolution.BudgetExceeded(
                        "compressed-assets",
                        "compressed local-map index assets exceed $MAX_COMPRESSED_ASSET_BYTES bytes",
                    )
                }
                maps += descriptor.toLocalMap(landmarkNames[descriptor.landmarkId])
                assets[descriptor.assetKey] = asset
            }.onFailure { failure ->
                skippedReasons += "map 0x${descriptor.baseAreaId.toString(16).padStart(4, '0')} render: ${failure.message}"
            }
        }
        if (maps.isEmpty()) {
            return LocalMapResolution.Unavailable("render", "no Gen II local map could be rendered")
        }
        val sceneResolution = runCatching {
            Gen2MapSceneResolver.resolve(
                rom = session.rom,
                sources = authority.descriptors.map(MapDescriptor::toSceneSource),
                maps = maps,
            ).also { resolution ->
                LocalMapCatalog(
                    maps = maps,
                    indexedAssets = assets,
                    scenes = resolution.scenes,
                ).validate()
            }
        }.getOrElse { failure ->
            Gen2MapSceneResolver.Resolution(
                scenes = emptyList(),
                skippedReasons = listOf("Gen II scenes: ${failure.message}"),
            )
        }

        val poiResolution = runCatching {
            Gen2LocalMapPoiResolver.resolve(
                rom = session.rom,
                sources = authority.descriptors.map(MapDescriptor::toPoiSource),
                maps = maps,
                family = family,
                codec = codec,
            ).also { resolution ->
                LocalMapCatalog(
                    maps = maps,
                    indexedAssets = assets,
                    scenes = sceneResolution.scenes,
                    pois = resolution.pois,
                ).validate()
            }
        }.getOrElse { failure ->
            Gen2LocalMapPoiResolver.Resolution(
                pois = emptyList(),
                skippedReasons = listOf("Gen II POIs: ${failure.message}"),
            )
        }

        return LocalMapResolution.Resolved(
            catalog = LocalMapCatalog(
                maps = maps,
                indexedAssets = assets,
                scenes = sceneResolution.scenes,
                pois = poiResolution.pois,
            ).validate(),
            reasons = listOf(
                "resolved compiled Gen II map-group, tileset, roof, environment-color, and palette consumers",
                "rendered ${maps.size} bounded $label maps from 32x32 ROM blocks and LZ3 2bpp tiles",
                "built ${sceneResolution.scenes.size} bounded Local-map scenes from compiled cardinal connections",
                if (codec == null) {
                    "resolved ${poiResolution.pois.size} bounded structural Local-map POIs; " +
                        "omitted localized Gen II map and POI names because no ROM text codec was authoritative"
                } else {
                    "resolved ${poiResolution.pois.size} bounded Local-map POIs and " +
                        "$namedMapCount map display names"
                },
                "stored time-independent indexed rasters with native morning, day, night, and dark GBC palettes",
                "bound all ${requiredMaps.size} encounter-authoritative group/map IDs",
            ) + skippedReasons + sceneResolution.skippedReasons + poiResolution.skippedReasons,
            skippedMaps = skippedReasons.size,
            gen2TimeOfDayWramOffset = authority.palettes.timeOfDayWramOffset,
        )
    }

    private fun findTilesetAuthorities(rom: RomImage): List<TilesetAuthority> = buildList {
        val end = minOf(BANK_BYTES, rom.size)
        var offset = 0
        while (offset + TILESET_CONSUMER_BYTES <= end) {
            parseTilesetAuthorityAt(rom, offset)?.let(::add)
            offset++
        }
    }.distinctBy { it.bank to it.root }

    private fun parseTilesetAuthorityAt(rom: RomImage, offset: Int): TilesetAuthority? = runCatching {
        if (
            rom.u8(offset) != PUSH_HL || rom.u8(offset + 1) != PUSH_BC ||
            rom.u8(offset + 2) != LOAD_HL_IMMEDIATE || rom.u8(offset + 5) != LOAD_BC_IMMEDIATE ||
            rom.u16le(offset + 6) != TILESET_HEADER_BYTES || rom.u8(offset + 8) != LOAD_A_ABSOLUTE ||
            rom.u8(offset + 11) != CALL || rom.u8(offset + 14) != LOAD_DE_IMMEDIATE ||
            rom.u8(offset + 17) != LOAD_BC_IMMEDIATE || rom.u16le(offset + 18) != TILESET_HEADER_BYTES ||
            rom.u8(offset + 20) != LOAD_A_IMMEDIATE || rom.u8(offset + 22) != CALL
        ) return@runCatching null
        val bank = rom.u8(offset + 21)
        val root = rom.gbBankAddress(bank, rom.u16le(offset + 3)) ?: return@runCatching null
        TilesetAuthority(bank, root)
    }.getOrNull()

    private fun findRoofAuthorities(rom: RomImage): List<RoofAuthority> = buildList {
        var offset = BANK_BYTES
        while (offset + ROOF_CONSUMER_BYTES <= rom.size) {
            parseRoofAuthorityAt(rom, offset)?.let(::add)
            offset++
        }
    }.distinctBy { Triple(it.table, it.roofs, it.destinationTile) }

    private fun parseRoofAuthorityAt(rom: RomImage, offset: Int): RoofAuthority? = runCatching {
        if (
            rom.u8(offset) != LOAD_A_ABSOLUTE || rom.u8(offset + 3) != LOAD_E_A ||
            rom.u8(offset + 4) != LOAD_D_IMMEDIATE || rom.u8(offset + 5) != 0 ||
            rom.u8(offset + 6) != LOAD_HL_IMMEDIATE || rom.u8(offset + 9) != ADD_HL_DE ||
            rom.u8(offset + 10) != LOAD_A_HL || rom.u8(offset + 11) != COMPARE_IMMEDIATE ||
            rom.u8(offset + 12) != 0xff || rom.u8(offset + 13) != RETURN_Z ||
            rom.u8(offset + 14) != LOAD_HL_IMMEDIATE || rom.u8(offset + 17) != LOAD_BC_IMMEDIATE ||
            rom.u16le(offset + 18) != ROOF_BYTES || rom.u8(offset + 20) != CALL ||
            rom.u8(offset + 23) != LOAD_DE_IMMEDIATE || rom.u8(offset + 26) != LOAD_BC_IMMEDIATE ||
            rom.u16le(offset + 27) != ROOF_BYTES || rom.u8(offset + 29) != CALL
        ) return@runCatching null
        val destination = rom.u16le(offset + 24)
        require(destination in VRAM_TILE_START until VRAM_TILE_END)
        require((destination - VRAM_TILE_START) % TILE_BYTES == 0)
        val bank = offset / BANK_BYTES
        val table = rom.gbBankAddress(bank, rom.u16le(offset + 7)) ?: return@runCatching null
        val roofs = rom.gbBankAddress(bank, rom.u16le(offset + 15)) ?: return@runCatching null
        RoofAuthority(bank, table, roofs, (destination - VRAM_TILE_START) / TILE_BYTES)
    }.getOrNull()

    private fun findTilePaletteBanks(rom: RomImage): List<Int> = buildList {
        var offset = BANK_BYTES
        while (offset + TILE_PALETTE_CONSUMER_BYTES <= rom.size) {
            parseTilePaletteBankAt(rom, offset)?.let(::add)
            offset++
        }
    }.distinct()

    private fun parseTilePaletteBankAt(rom: RomImage, offset: Int): Int? = runCatching {
        if (
            rom.u8(offset) != BIT_OPCODE_PREFIX || rom.u8(offset + 1) != SHIFT_RIGHT_A_OPCODE ||
            rom.u8(offset + 2) != JUMP_RELATIVE_CARRY || rom.u8(offset + 4) != LOAD_HL_IMMEDIATE ||
            rom.u8(offset + 7) != ADD_A_HL || rom.u8(offset + 8) != LOAD_L_A ||
            rom.u8(offset + 9) != LOAD_A_ABSOLUTE || rom.u8(offset + 12) != ADD_WITH_CARRY_IMMEDIATE ||
            rom.u8(offset + 13) != 0 || rom.u8(offset + 14) != LOAD_H_A ||
            rom.u8(offset + 15) != LOAD_A_HL || rom.u8(offset + 16) != AND_IMMEDIATE ||
            rom.u8(offset + 17) != PALETTE_ATTRIBUTE_MASK
        ) return@runCatching null

        val upper = offset + 4 + rom.u8(offset + 3).toByte().toInt()
        require(upper >= offset + TILE_PALETTE_CONSUMER_BYTES)
        require(upper / BANK_BYTES == offset / BANK_BYTES)
        require(upper + TILE_PALETTE_UPPER_PATH_BYTES <= bankEnd(rom, offset / BANK_BYTES))
        if (
            rom.u8(upper) != LOAD_HL_IMMEDIATE || rom.u16le(upper + 1) != rom.u16le(offset + 5) ||
            rom.u8(upper + 3) != ADD_A_HL || rom.u8(upper + 4) != LOAD_L_A ||
            rom.u8(upper + 5) != LOAD_A_ABSOLUTE || rom.u16le(upper + 6) != rom.u16le(offset + 10) ||
            rom.u8(upper + 8) != ADD_WITH_CARRY_IMMEDIATE || rom.u8(upper + 9) != 0 ||
            rom.u8(upper + 10) != LOAD_H_A || rom.u8(upper + 11) != LOAD_A_HL ||
            rom.u8(upper + 12) != BIT_OPCODE_PREFIX || rom.u8(upper + 13) != SWAP_A_OPCODE ||
            rom.u8(upper + 14) != AND_IMMEDIATE || rom.u8(upper + 15) != PALETTE_ATTRIBUTE_MASK
        ) return@runCatching null
        offset / BANK_BYTES
    }.getOrNull()

    private fun findPaletteAuthorities(rom: RomImage): List<PaletteAuthority> = buildList {
        var offset = BANK_BYTES
        while (offset + PALETTE_CONSUMER_BYTES <= rom.size) {
            parsePaletteAuthorityAt(rom, offset)?.let(::add)
            offset++
        }
    }.distinctBy {
        listOf(it.environmentPointers, it.tilesetPalettes, it.roofPalettes, it.timeOfDayWramOffset)
    }

    private fun parsePaletteAuthorityAt(rom: RomImage, offset: Int): PaletteAuthority? = runCatching {
        if (
            rom.u8(offset) != LOAD_A_ABSOLUTE || rom.u8(offset + 3) != AND_IMMEDIATE ||
            rom.u8(offset + 4) != ENVIRONMENT_MASK || rom.u8(offset + 5) != LOAD_E_A ||
            rom.u8(offset + 6) != LOAD_D_IMMEDIATE || rom.u8(offset + 7) != 0 ||
            rom.u8(offset + 8) != LOAD_HL_IMMEDIATE || rom.u8(offset + 11) != ADD_HL_DE ||
            rom.u8(offset + 12) != ADD_HL_DE || rom.u8(offset + 13) != LOAD_A_HLI ||
            rom.u8(offset + 14) != LOAD_H_HL || rom.u8(offset + 15) != LOAD_L_A ||
            rom.u8(offset + 16) != LOAD_A_ABSOLUTE || rom.u8(offset + 19) != AND_IMMEDIATE ||
            rom.u8(offset + 20) != DAYTIME_MASK || rom.u8(offset + 21) != ADD_A ||
            rom.u8(offset + 22) != ADD_A || rom.u8(offset + 23) != ADD_A ||
            rom.u8(offset + 24) != LOAD_E_A || rom.u8(offset + 25) != LOAD_D_IMMEDIATE ||
            rom.u8(offset + 26) != 0 || rom.u8(offset + 27) != ADD_HL_DE ||
            rom.u8(offset + 28) != LOAD_E_L || rom.u8(offset + 29) != LOAD_D_H
        ) return@runCatching null

        val bank = offset / BANK_BYTES
        val timeOfDayAddress = rom.u16le(offset + 17)
        require(timeOfDayAddress in WRAM_START until WRAM_END)
        val environmentPointers = rom.gbBankAddress(bank, rom.u16le(offset + 9)) ?: return@runCatching null
        val searchEnd = minOf(offset + PALETTE_CONSUMER_BYTES, bankEnd(rom, bank))
        val tilesetOperand = (offset + 30 until searchEnd - 11).firstOrNull { candidate ->
            rom.u8(candidate) == LOAD_L_A && rom.u8(candidate + 1) == LOAD_H_IMMEDIATE &&
                rom.u8(candidate + 2) == 0 && rom.u8(candidate + 3) == ADD_HL_HL &&
                rom.u8(candidate + 4) == ADD_HL_HL && rom.u8(candidate + 5) == ADD_HL_HL &&
                rom.u8(candidate + 6) == LOAD_DE_IMMEDIATE && rom.u8(candidate + 9) == ADD_HL_DE &&
                rom.u8(candidate + 10) == LOAD_E_L && rom.u8(candidate + 11) == LOAD_D_H
        } ?: return@runCatching null
        val roofOperand = (tilesetOperand + 12 until searchEnd - 16).firstOrNull { candidate ->
            rom.u8(candidate) == LOAD_L_A && rom.u8(candidate + 1) == LOAD_H_IMMEDIATE &&
                rom.u8(candidate + 2) == 0 && rom.u8(candidate + 3) == ADD_HL_HL &&
                rom.u8(candidate + 4) == ADD_HL_HL && rom.u8(candidate + 5) == ADD_HL_HL &&
                rom.u8(candidate + 6) == LOAD_DE_IMMEDIATE && rom.u8(candidate + 9) == ADD_HL_DE &&
                rom.u8(candidate + 10) == LOAD_A_ABSOLUTE && rom.u8(candidate + 13) == AND_IMMEDIATE &&
                rom.u8(candidate + 14) == DAYTIME_MASK && rom.u8(candidate + 15) == COMPARE_IMMEDIATE &&
                rom.u8(candidate + 16) == NITE_TIME
        } ?: return@runCatching null
        require(rom.u16le(roofOperand + 11) == timeOfDayAddress)
        val tilesetPalettes = rom.gbBankAddress(bank, rom.u16le(tilesetOperand + 7)) ?: return@runCatching null
        val roofPalettes = rom.gbBankAddress(bank, rom.u16le(roofOperand + 7)) ?: return@runCatching null
        PaletteAuthority(
            bank,
            environmentPointers,
            tilesetPalettes,
            roofPalettes,
            timeOfDayAddress - WRAM_START,
        )
    }.getOrNull()

    private fun buildAuthority(
        rom: RomImage,
        groups: Gen2WorldMapResolver.MapGroupAuthority,
        tilesets: TilesetAuthority,
        roofs: RoofAuthority,
        palettes: PaletteAuthority,
        tilePaletteBank: Int,
        requiredMaps: Set<Int>,
    ): MapAuthority? = runCatching {
        val groupCount = readGroupCount(rom, groups)
        palettes.validate(rom, groupCount)
        require(roofs.table + groupCount + 1 <= bankEnd(rom, roofs.bank)) { "roof group table is truncated" }
        val roofIndexes = rom.slice(roofs.table, groupCount + 1)
        val maxRoof = roofIndexes.asSequence()
            .map { it.toInt() and 0xff }
            .filter { it != 0xff }
            .maxOrNull()
        if (maxRoof != null) {
            require(roofs.roofs + (maxRoof + 1) * ROOF_BYTES <= bankEnd(rom, roofs.bank)) {
                "roof graphics are truncated"
            }
        }

        val descriptors = mutableListOf<MapDescriptor>()
        val pointers = List(groupCount) { index ->
            requireNotNull(rom.gbBankAddress(groups.bank, rom.u16le(groups.tableOffset + index * 2)))
        }
        require(pointers.zipWithNext().all { (first, second) -> second > first }) {
            "map-group pointers are not strictly ascending"
        }
        pointers.forEachIndexed { index, root ->
            val group = index + 1
            val next = pointers.getOrNull(index + 1)
            if (next != null) {
                require((next - root) % MAP_HEADER_BYTES == 0)
                val count = (next - root) / MAP_HEADER_BYTES
                require(count in 1..MAX_MAPS_PER_GROUP)
                repeat(count) { mapIndex ->
                    descriptors += requireNotNull(
                        readDescriptor(rom, group, mapIndex + 1, root + mapIndex * MAP_HEADER_BYTES),
                    ) { "invalid map descriptor $group:${mapIndex + 1}" }
                }
            } else {
                var map = 1
                while (map <= MAX_MAPS_PER_GROUP) {
                    val descriptor = readDescriptor(rom, group, map, root + (map - 1) * MAP_HEADER_BYTES) ?: break
                    descriptors += descriptor
                    map++
                }
                require(map > 1)
            }
        }
        val resolvedIds = descriptors.mapTo(hashSetOf(), MapDescriptor::baseAreaId)
        require(resolvedIds.containsAll(requiredMaps))
        require(descriptors.size <= MAX_MAPS)

        val byTileset = descriptors.groupBy(MapDescriptor::tilesetId)
        val tilesetData = byTileset.mapValues { (tilesetId, maps) ->
            runCatching { loadTileset(rom, tilesets, tilePaletteBank, tilesetId, maps) }
                .getOrElse { failure -> error("tileset $tilesetId: ${failure.message}") }
        }
        if (System.getenv("DUALDEX_MAP_TRACE") == "1") {
            println(
                "local-map-trace gen2 groups=0x${groups.tableOffset.toString(16)} " +
                    "tilesets=0x${tilesets.root.toString(16)} roofs=0x${roofs.table.toString(16)} " +
                    "palettes=0x${palettes.environmentPointers.toString(16)} tilePaletteBank=$tilePaletteBank " +
                    "groupCount=$groupCount maps=${descriptors.size} required=${requiredMaps.size}",
            )
        }
        MapAuthority(
            groups = groups,
            tilesets = tilesets,
            roofs = roofs.copy(indexes = roofIndexes),
            palettes = palettes,
            tilePaletteBank = tilePaletteBank,
            descriptors = descriptors.sortedBy(MapDescriptor::baseAreaId),
            tilesetData = tilesetData,
        )
    }.getOrElse { failure ->
        if (System.getenv("DUALDEX_MAP_TRACE") == "1") {
            println("local-map-trace gen2 authority-reject ${failure.message}")
        }
        null
    }

    private fun readGroupCount(rom: RomImage, authority: Gen2WorldMapResolver.MapGroupAuthority): Int {
        val firstGroup = requireNotNull(
            rom.gbBankAddress(authority.bank, rom.u16le(authority.tableOffset)),
        )
        val pointerBytes = firstGroup - authority.tableOffset
        require(pointerBytes > 0 && pointerBytes % 2 == 0)
        return (pointerBytes / 2).also { require(it in 1..MAX_MAP_GROUPS) }
    }

    private fun readDescriptor(
        rom: RomImage,
        group: Int,
        map: Int,
        row: Int,
    ): MapDescriptor? = runCatching {
        require(row + MAP_HEADER_BYTES <= rom.size)
        val attributesBank = rom.u8(row)
        val tilesetId = rom.u8(row + 1)
        val environment = rom.u8(row + 2)
        require(tilesetId in 1..MAX_TILESET_ID)
        require(environment in 1..MAX_ENVIRONMENT)
        val attributes = rom.gbBankAddress(attributesBank, rom.u16le(row + 3)) ?: return@runCatching null
        require(attributes + MAP_ATTRIBUTES_BYTES <= bankEnd(rom, attributesBank))
        val landmarkId = rom.u8(row + 5)
        val paletteMode = rom.u8(row + 7) and PALETTE_MODE_MASK
        require(paletteMode in PALETTE_AUTO..PALETTE_DARK)
        val blockHeight = rom.u8(attributes + 1)
        val blockWidth = rom.u8(attributes + 2)
        require(blockWidth in 1..MAX_BLOCK_DIMENSION && blockHeight in 1..MAX_BLOCK_DIMENSION)
        val blockCount = blockWidth * blockHeight
        val blockBank = rom.u8(attributes + 3)
        val blocks = rom.gbBankAddress(blockBank, rom.u16le(attributes + 4)) ?: return@runCatching null
        val scriptsBank = rom.u8(attributes + 6)
        requireNotNull(rom.gbBankAddress(scriptsBank, rom.u16le(attributes + 7)))
        requireNotNull(rom.gbBankAddress(scriptsBank, rom.u16le(attributes + 9)))
        require(rom.u8(attributes + 11) and CONNECTION_MASK.inv() == 0)
        require(blocks.toLong() + blockCount <= bankEnd(rom, blockBank).toLong())
        val pixelCount = blockCount.toLong() * BLOCK_PIXELS * BLOCK_PIXELS
        require(pixelCount <= MAX_MAP_PIXELS)
        MapDescriptor(
            group = group,
            map = map,
            attributesBank = attributesBank,
            attributes = attributes,
            blockBank = blockBank,
            blocks = blocks,
            tilesetId = tilesetId,
            environment = environment,
            paletteMode = paletteMode,
            landmarkId = landmarkId,
            blockWidth = blockWidth,
            blockHeight = blockHeight,
            blockIds = rom.slice(blocks, blockCount),
        )
    }.getOrNull()

    private fun loadTileset(
        rom: RomImage,
        authority: TilesetAuthority,
        tilePaletteBank: Int,
        tilesetId: Int,
        maps: List<MapDescriptor>,
    ): TilesetData {
        val row = authority.root + tilesetId * TILESET_HEADER_BYTES
        require(row + TILESET_HEADER_BYTES <= bankEnd(rom, authority.bank)) { "tileset row is truncated" }
        val graphicsBank = rom.u8(row)
        val graphics = requireNotNull(rom.gbBankAddress(graphicsBank, rom.u16le(row + 1)))
        val metatileBank = rom.u8(row + 3)
        val metatileRoot = requireNotNull(rom.gbBankAddress(metatileBank, rom.u16le(row + 4)))
        val collisionBank = rom.u8(row + 6)
        requireNotNull(rom.gbBankAddress(collisionBank, rom.u16le(row + 7)))

        val decoded = Lz3Decoder.decode(rom.slice(graphics, bankEnd(rom, graphicsBank) - graphics))
        require(decoded.size % TILE_BYTES == 0 && decoded.size in PRIMARY_TILE_COUNT * TILE_BYTES..COMPRESSED_TILE_COUNT * TILE_BYTES) {
            "decoded ${decoded.size} graphics bytes outside the canonical Gen II capacity"
        }
        val vramTiles = ByteArray(VRAM_BANKS * VRAM_BANK_TILE_COUNT * TILE_BYTES)
        val primaryBytes = minOf(decoded.size, PRIMARY_TILE_COUNT * TILE_BYTES)
        decoded.copyInto(vramTiles, endIndex = primaryBytes)
        if (decoded.size > primaryBytes) {
            decoded.copyInto(
                destination = vramTiles,
                destinationOffset = VRAM_BANK_TILE_COUNT * TILE_BYTES,
                startIndex = primaryBytes,
            )
        }

        val usedBlocks = maps.flatMapTo(sortedSetOf()) { it.usedBlockIds }
        val metatileBytes = (requireNotNull(usedBlocks.maxOrNull()) + 1) * TILES_PER_BLOCK
        require(metatileRoot + metatileBytes <= bankEnd(rom, metatileBank)) { "metatile table is truncated" }
        val metatiles = rom.slice(metatileRoot, metatileBytes)
        val usedTiles = usedBlocks.flatMapTo(sortedSetOf()) { blockId ->
            List(TILES_PER_BLOCK) { tileIndex ->
                metatiles[blockId * TILES_PER_BLOCK + tileIndex].toInt() and 0xff
            }
        }
        val paletteMapRoot = requireNotNull(rom.gbBankAddress(tilePaletteBank, rom.u16le(row + 13)))
        val paletteMapBytes = requireNotNull(usedTiles.maxOrNull()) / 2 + 1
        require(paletteMapRoot + paletteMapBytes <= bankEnd(rom, tilePaletteBank)) {
            "tileset palette map is truncated"
        }
        return TilesetData(vramTiles, metatiles, rom.slice(paletteMapRoot, paletteMapBytes))
    }

    private fun renderIndices(
        map: MapDescriptor,
        tileset: TilesetData,
        tiles: IndexedSprite,
    ): ByteArray {
        val pixelWidth = map.gridWidth * METATILE_PIXELS
        val pixels = ByteArray(map.pixelCount.toInt())
        repeat(map.blockHeight) { blockY ->
            repeat(map.blockWidth) { blockX ->
                val blockId = map.blockIds[blockY * map.blockWidth + blockX].toInt() and 0xff
                repeat(TILES_PER_BLOCK) { tileIndex ->
                    val tileId = tileset.metatiles[blockId * TILES_PER_BLOCK + tileIndex].toInt() and 0xff
                    drawIndexedTile(
                        tiles,
                        tileId,
                        tileset.paletteIndex(tileId),
                        pixels,
                        pixelWidth,
                        blockX * BLOCK_PIXELS + tileIndex % BLOCK_TILE_EDGE * TILE_PIXELS,
                        blockY * BLOCK_PIXELS + tileIndex / BLOCK_TILE_EDGE * TILE_PIXELS,
                    )
                }
            }
        }
        return pixels
    }

    private fun drawIndexedTile(
        tiles: IndexedSprite,
        tileId: Int,
        paletteIndex: Int,
        pixels: ByteArray,
        pixelWidth: Int,
        originX: Int,
        originY: Int,
    ) {
        repeat(TILE_PIXELS) { y ->
            repeat(TILE_PIXELS) { x ->
                val colorIndex = tiles.indexAt(tileId * TILE_PIXELS + x, y)
                pixels[(originY + y) * pixelWidth + originX + x] =
                    (paletteIndex * COLORS_PER_PALETTE + colorIndex).toByte()
            }
        }
    }

    private fun bankEnd(rom: RomImage, bank: Int): Int =
        minOf(rom.size.toLong(), (bank.toLong() + 1L) * BANK_BYTES).toInt()

    private data class TilesetAuthority(val bank: Int, val root: Int)

    private data class PaletteAuthority(
        val bank: Int,
        val environmentPointers: Int,
        val tilesetPalettes: Int,
        val roofPalettes: Int,
        val timeOfDayWramOffset: Int,
    ) {
        fun validate(rom: RomImage, groupCount: Int) {
            require(environmentPointers + ENVIRONMENT_COUNT * 2 <= bankEnd(rom, bank)) {
                "environment-color pointer table is truncated"
            }
            var maxPaletteIndex = 0
            repeat(ENVIRONMENT_COUNT) { environment ->
                val pointer = requireNotNull(
                    rom.gbBankAddress(bank, rom.u16le(environmentPointers + environment * 2)),
                )
                require(pointer + DAYTIME_COUNT * ACTIVE_PALETTE_COUNT <= bankEnd(rom, bank)) {
                    "environment $environment color rows are truncated"
                }
                repeat(DAYTIME_COUNT * ACTIVE_PALETTE_COUNT) { index ->
                    maxPaletteIndex = maxOf(maxPaletteIndex, rom.u8(pointer + index))
                }
            }
            require(tilesetPalettes + (maxPaletteIndex + 1) * PALETTE_BYTES <= bankEnd(rom, bank)) {
                "tileset background palette table is truncated"
            }
            repeat((maxPaletteIndex + 1) * COLORS_PER_PALETTE) { color ->
                require(rom.u16le(tilesetPalettes + color * COLOR_BYTES) <= MAX_BGR555)
            }
            require(roofPalettes + (groupCount + 1) * ROOF_PALETTE_BYTES <= bankEnd(rom, bank)) {
                "map-group roof palette table is truncated"
            }
            repeat((groupCount + 1) * ROOF_PALETTE_BYTES / COLOR_BYTES) { color ->
                require(rom.u16le(roofPalettes + color * COLOR_BYTES) <= MAX_BGR555)
            }
        }

        fun palettesFor(rom: RomImage, map: MapDescriptor): MapLightingPalettes = MapLightingPalettes(
            morning = colorsFor(rom, map, MORN_TIME),
            day = colorsFor(rom, map, DAY_TIME),
            night = colorsFor(rom, map, NITE_TIME),
            dark = colorsFor(rom, map, DARK_TIME),
        )

        private fun colorsFor(rom: RomImage, map: MapDescriptor, time: Int): IntArray {
            require(time in MORN_TIME..DARK_TIME)
            val environmentColors = requireNotNull(
                rom.gbBankAddress(bank, rom.u16le(environmentPointers + map.environment * 2)),
            )
            val colors = IntArray(ACTIVE_PALETTE_COUNT * COLORS_PER_PALETTE)
            repeat(ACTIVE_PALETTE_COUNT) { palette ->
                val paletteIndex = rom.u8(environmentColors + time * ACTIVE_PALETTE_COUNT + palette)
                repeat(COLORS_PER_PALETTE) { color ->
                    val bgr555 = rom.u16le(
                        tilesetPalettes + paletteIndex * PALETTE_BYTES + color * COLOR_BYTES,
                    )
                    colors[palette * COLORS_PER_PALETTE + color] =
                        TileRenderer.bgr555ToArgb(bgr555, transparent = false)
                }
            }
            if (map.environment in OUTDOOR_ENVIRONMENTS) {
                val roofTimeOffset = if (time >= NITE_TIME) ROOF_TIME_PALETTE_BYTES else 0
                val source = roofPalettes + map.group * ROOF_PALETTE_BYTES + roofTimeOffset
                repeat(ROOF_OVERRIDE_COLOR_COUNT) { color ->
                    val bgr555 = rom.u16le(source + color * COLOR_BYTES)
                    colors[ROOF_PALETTE_INDEX * COLORS_PER_PALETTE + ROOF_OVERRIDE_COLOR_START + color] =
                        TileRenderer.bgr555ToArgb(bgr555, transparent = false)
                }
            }
            return colors
        }
    }

    private data class RoofAuthority(
        val bank: Int,
        val table: Int,
        val roofs: Int,
        val destinationTile: Int,
        val indexes: ByteArray = byteArrayOf(),
    ) {
        fun indexFor(map: MapDescriptor): Int? {
            if (map.environment !in OUTDOOR_ENVIRONMENTS || map.group !in indexes.indices) return null
            return (indexes[map.group].toInt() and 0xff).takeUnless { it == 0xff }
        }

        fun read(rom: RomImage, index: Int): ByteArray = rom.slice(roofs + index * ROOF_BYTES, ROOF_BYTES)
    }

    private data class TilesetData(
        val vramTiles: ByteArray,
        val metatiles: ByteArray,
        val paletteMap: ByteArray,
    ) {
        fun paletteIndex(tileId: Int): Int = attribute(tileId) and PALETTE_INDEX_MASK

        fun renderTiles(rom: RomImage, roofs: RoofAuthority, roofIndex: Int?): IndexedSprite {
            val vram = if (roofIndex == null) {
                vramTiles
            } else {
                vramTiles.copyOf().also { tiles ->
                    roofs.read(rom, roofIndex).copyInto(tiles, roofs.destinationTile * TILE_BYTES)
                }
            }
            val renderedTiles = ByteArray(TILE_DOMAIN * TILE_BYTES)
            repeat(TILE_DOMAIN) { tileId ->
                val bank = attribute(tileId) ushr VRAM_BANK_ATTRIBUTE_BIT and 1
                val hardwareTile = tileId and VRAM_TILE_INDEX_MASK
                val source = (bank * VRAM_BANK_TILE_COUNT + hardwareTile) * TILE_BYTES
                vram.copyInto(
                    destination = renderedTiles,
                    destinationOffset = tileId * TILE_BYTES,
                    startIndex = source,
                    endIndex = source + TILE_BYTES,
                )
            }
            return TileRenderer.gameBoy2Bpp(renderedTiles, TILE_DOMAIN, 1)
        }

        private fun attribute(tileId: Int): Int {
            if (tileId / 2 !in paletteMap.indices) return 0
            val packed = paletteMap[tileId / 2].toInt() and 0xff
            return packed ushr ((tileId and 1) * PALETTE_ATTRIBUTE_BITS) and PALETTE_ATTRIBUTE_MASK
        }
    }

    private data class MapAuthority(
        val groups: Gen2WorldMapResolver.MapGroupAuthority,
        val tilesets: TilesetAuthority,
        val roofs: RoofAuthority,
        val palettes: PaletteAuthority,
        val tilePaletteBank: Int,
        val descriptors: List<MapDescriptor>,
        val tilesetData: Map<Int, TilesetData>,
    )

    private data class TilesetVariant(val tilesetId: Int, val roofIndex: Int?)

    private data class MapDescriptor(
        val group: Int,
        val map: Int,
        val attributesBank: Int,
        val attributes: Int,
        val blockBank: Int,
        val blocks: Int,
        val tilesetId: Int,
        val environment: Int,
        val paletteMode: Int,
        val landmarkId: Int,
        val blockWidth: Int,
        val blockHeight: Int,
        val blockIds: ByteArray,
    ) {
        val baseAreaId: Int = (group shl 8) or map
        val gridWidth: Int = blockWidth * BLOCK_METATILE_EDGE
        val gridHeight: Int = blockHeight * BLOCK_METATILE_EDGE
        val pixelCount: Long = gridWidth.toLong() * METATILE_PIXELS * gridHeight * METATILE_PIXELS
        val usedBlockIds: Set<Int> = blockIds.mapTo(sortedSetOf()) { it.toInt() and 0xff }
        val lightingPolicy: LocalMapLightingPolicy = when (paletteMode) {
            PALETTE_AUTO -> LocalMapLightingPolicy.AUTO
            PALETTE_DAY -> LocalMapLightingPolicy.DAY
            PALETTE_NITE -> LocalMapLightingPolicy.NIGHT
            PALETTE_MORN -> LocalMapLightingPolicy.MORNING
            PALETTE_DARK -> LocalMapLightingPolicy.DARK
            else -> error("unsupported Gen II map palette mode $paletteMode")
        }
        val key: String = "local/${baseAreaId.toString(16).padStart(4, '0')}"
        val assetKey: String = "$key/map"

        fun toSceneSource(): Gen2MapSceneResolver.Source = Gen2MapSceneResolver.Source(
            baseAreaId = baseAreaId,
            attributesBank = attributesBank,
            attributes = attributes,
            blockBank = blockBank,
            blocks = blocks,
        )

        fun toPoiSource(): Gen2LocalMapPoiResolver.Source = Gen2LocalMapPoiResolver.Source(
            baseAreaId = baseAreaId,
            attributesBank = attributesBank,
            attributes = attributes,
        )

        fun toLocalMap(displayName: String?): LocalMap = LocalMap(
            key = key,
            displayName = displayName,
            baseAreaId = baseAreaId,
            pixelWidth = gridWidth * METATILE_PIXELS,
            pixelHeight = gridHeight * METATILE_PIXELS,
            gridWidth = gridWidth,
            gridHeight = gridHeight,
            imageAssetKey = assetKey,
        )
    }

    private const val BANK_BYTES = 0x4000
    private const val MAP_HEADER_BYTES = 9
    private const val MAP_ATTRIBUTES_BYTES = 12
    private const val TILESET_HEADER_BYTES = 15
    private const val MAX_MAP_GROUPS = 63
    private const val MAX_MAPS_PER_GROUP = 254
    private const val MAX_TILESET_ID = 63
    private const val MAX_ENVIRONMENT = 7
    private const val MAX_BLOCK_DIMENSION = 128
    private const val CONNECTION_MASK = 0x0f
    private const val BLOCK_METATILE_EDGE = 2
    private const val BLOCK_TILE_EDGE = 4
    private const val TILES_PER_BLOCK = BLOCK_TILE_EDGE * BLOCK_TILE_EDGE
    private const val TILE_BYTES = 16
    private const val TILE_PIXELS = 8
    private const val METATILE_PIXELS = 16
    private const val BLOCK_PIXELS = BLOCK_TILE_EDGE * TILE_PIXELS
    private const val PRIMARY_TILE_COUNT = 96
    private const val COMPRESSED_TILE_COUNT = 192
    private const val VRAM_BANKS = 2
    private const val VRAM_BANK_TILE_COUNT = 128
    private const val VRAM_BANK_ATTRIBUTE_BIT = 3
    private const val VRAM_TILE_INDEX_MASK = 0x7f
    private const val TILE_DOMAIN = 256
    private const val PALETTE_ATTRIBUTE_BITS = 4
    private const val PALETTE_ATTRIBUTE_MASK = 0x0f
    private const val PALETTE_INDEX_MASK = 0x07
    private const val ENVIRONMENT_COUNT = 8
    private const val DAYTIME_COUNT = 4
    private const val ACTIVE_PALETTE_COUNT = 8
    private const val COLORS_PER_PALETTE = 4
    private const val COLOR_BYTES = 2
    private const val PALETTE_BYTES = COLORS_PER_PALETTE * COLOR_BYTES
    private const val MAX_BGR555 = 0x7fff
    private const val PALETTE_MODE_MASK = 0x0f
    private const val PALETTE_AUTO = 0
    private const val PALETTE_DAY = 1
    private const val PALETTE_NITE = 2
    private const val PALETTE_MORN = 3
    private const val PALETTE_DARK = 4
    private const val MORN_TIME = 0
    private const val DAY_TIME = 1
    private const val NITE_TIME = 2
    private const val DARK_TIME = 3
    private const val ROOF_TILE_COUNT = 9
    private const val ROOF_BYTES = ROOF_TILE_COUNT * TILE_BYTES
    private const val ROOF_PALETTE_BYTES = 8
    private const val ROOF_TIME_PALETTE_BYTES = 4
    private const val ROOF_PALETTE_INDEX = 6
    private const val ROOF_OVERRIDE_COLOR_START = 1
    private const val ROOF_OVERRIDE_COLOR_COUNT = 2
    private const val WRAM_START = 0xc000
    private const val WRAM_END = 0xe000
    private const val VRAM_TILE_START = 0x9000
    private const val VRAM_TILE_END = 0x9800
    private val OUTDOOR_ENVIRONMENTS = setOf(1, 2)
    private const val MAX_MAPS = 512
    private const val MAX_MAP_PIXELS = 2_000_000L
    private const val MAX_TOTAL_PIXELS = 100_000_000L
    private const val MAX_COMPRESSED_ASSET_BYTES = 64L * 1024 * 1024

    private const val TILESET_CONSUMER_BYTES = 23
    private const val ROOF_CONSUMER_BYTES = 32
    private const val TILE_PALETTE_CONSUMER_BYTES = 18
    private const val TILE_PALETTE_UPPER_PATH_BYTES = 16
    private const val PALETTE_CONSUMER_BYTES = 160
    private const val ENVIRONMENT_MASK = 0x07
    private const val DAYTIME_MASK = 0x03
    private const val PUSH_HL = 0xe5
    private const val PUSH_BC = 0xc5
    private const val LOAD_A_IMMEDIATE = 0x3e
    private const val LOAD_A_ABSOLUTE = 0xfa
    private const val LOAD_A_HL = 0x7e
    private const val LOAD_A_HLI = 0x2a
    private const val LOAD_D_IMMEDIATE = 0x16
    private const val LOAD_D_H = 0x54
    private const val LOAD_E_A = 0x5f
    private const val LOAD_E_L = 0x5d
    private const val LOAD_H_HL = 0x66
    private const val LOAD_H_IMMEDIATE = 0x26
    private const val LOAD_L_A = 0x6f
    private const val LOAD_HL_IMMEDIATE = 0x21
    private const val LOAD_DE_IMMEDIATE = 0x11
    private const val LOAD_BC_IMMEDIATE = 0x01
    private const val ADD_HL_DE = 0x19
    private const val ADD_HL_HL = 0x29
    private const val ADD_A = 0x87
    private const val ADD_A_HL = 0x86
    private const val ADD_WITH_CARRY_IMMEDIATE = 0xce
    private const val BIT_OPCODE_PREFIX = 0xcb
    private const val SHIFT_RIGHT_A_OPCODE = 0x3f
    private const val SWAP_A_OPCODE = 0x37
    private const val JUMP_RELATIVE_CARRY = 0x38
    private const val LOAD_H_A = 0x67
    private const val AND_IMMEDIATE = 0xe6
    private const val COMPARE_IMMEDIATE = 0xfe
    private const val CALL = 0xcd
    private const val RETURN_Z = 0xc8
}
