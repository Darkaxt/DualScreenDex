package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog
import com.enrpau.dualscreendex.parser.catalog.WorldMapCell
import com.enrpau.dualscreendex.parser.catalog.WorldMapLocation
import com.enrpau.dualscreendex.parser.catalog.WorldMapRegion
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.sprite.Lz3Decoder
import com.enrpau.dualscreendex.parser.sprite.TileRenderer

/** Resolves the Gen II Town Map through compiled asset, map-header, and landmark consumers. */
object Gen2WorldMapResolver {
    fun resolve(session: RomAnalysisSession, encounterBaseIds: Set<Int>): WorldMapResolution {
        val requiredMaps = encounterBaseIds.filterTo(sortedSetOf()) { base ->
            val group = base ushr 8
            val map = base and 0xff
            group in 1..MAX_MAP_GROUPS && map in 1..MAX_MAPS_PER_GROUP
        }
        if (requiredMaps.isEmpty()) {
            return WorldMapResolution.Unavailable("encounter-binding", "no encounter-bound Gen II group/map IDs")
        }
        val assets = findAssetChains(session.rom)
        if (assets.isEmpty()) {
            return WorldMapResolution.Unavailable(
                "asset-loader",
                "no complete Gen II Town Map graphics, plane, palette-map, and palette chain passed validation",
            )
        }
        if (assets.size != 1) {
            return WorldMapResolution.Ambiguous("asset-loader", "${assets.size} complete Gen II asset chains remained")
        }
        val bindingSearch = findBindingChains(session.rom, requiredMaps)
        val bindings = bindingSearch.chains
        if (bindings.isEmpty()) {
            return WorldMapResolution.Unavailable(
                "landmark-join",
                "no complete Gen II map-header, landmark, and region-classifier chain passed validation " +
                    "(headers=${bindingSearch.groupRoots}, landmarks=${bindingSearch.landmarks}, " +
                    "classifiers=${bindingSearch.classifiers})",
            )
        }
        if (bindings.size != 1) {
            return WorldMapResolution.Ambiguous("landmark-join", "${bindings.size} complete Gen II binding chains remained")
        }

        val asset = assets.single()
        val binding = bindings.single()
        val regionInputs = listOf(
            RegionInput("gen2-johto", "Johto", asset.johtoMap, binding.johto),
            RegionInput("gen2-kanto", "Kanto", asset.kantoMap, binding.kanto),
        )
        val regions = regionInputs.map { input ->
            val assetKey = "world/${input.key}"
            WorldMapRegion(
                key = input.key,
                displayName = input.displayName,
                pixelWidth = PIXEL_WIDTH,
                pixelHeight = PIXEL_HEIGHT,
                gridWidth = GRID_WIDTH,
                gridHeight = GRID_HEIGHT,
                imageAssetKey = assetKey,
                locations = input.landmarks.map { landmark ->
                    WorldMapLocation(
                        key = "landmark-${landmark.id}",
                        displayName = landmark.name,
                        baseAreaIds = landmark.baseAreaIds,
                        geometry = listOf(WorldMapCell(landmark.x, landmark.y, 1, 1)),
                    )
                },
            )
        }
        val catalog = WorldMapCatalog(
            regions = regions,
            assets = mapOf(
                "world/gen2-johto" to compose(asset.tiles, asset.johtoMap, asset.paletteMap, asset.palettes),
                "world/gen2-kanto" to compose(asset.tiles, asset.kantoMap, asset.paletteMap, asset.palettes),
            ),
        ).validate()
        return WorldMapResolution.Resolved(
            catalog,
            listOf(
                "validated one compiled 48-tile 2bpp, two-plane, palette-map, and six-palette asset chain",
                "joined ${requiredMaps.size} encounter maps through one compiled header, landmark, and region chain",
            ),
        )
    }

    private fun findAssetChains(rom: RomImage): List<AssetChain> {
        val bankCount = rom.size / BANK_BYTES
        val mapAuthorities = buildList {
            for (bank in 1 until bankCount) addAll(findMapAuthorities(rom, bank))
        }
        val graphics = buildList {
            for (bank in 1 until bankCount) addAll(findGraphicsLoaders(rom, bank))
        }
        val palettes = findPaletteLoaders(rom)
        return buildList {
            for (map in mapAuthorities) {
                if (!hasPaletteSelectionCaller(rom, map.bank)) continue
                for (gfx in graphics.filter { it.loaderBank == map.bank }) {
                    for (palette in palettes) {
                        add(
                            AssetChain(
                                tiles = gfx.tiles,
                                johtoMap = map.johtoMap,
                                kantoMap = map.kantoMap,
                                paletteMap = map.paletteMap,
                                palettes = palette,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun findMapAuthorities(rom: RomImage, bank: Int): List<MapAuthority> {
        val bankStart = bank * BANK_BYTES
        val bankEnd = minOf(bankStart + BANK_BYTES, rom.size)
        return buildList {
            var offset = bankStart
            while (offset + MAP_AUTHORITY_BYTES <= bankEnd) {
                parseMapAuthorityAt(rom, bank, offset, bankEnd)?.let(::add)
                offset++
            }
        }
    }

    private fun parseMapAuthorityAt(rom: RomImage, bank: Int, offset: Int, bankEnd: Int): MapAuthority? = runCatching {
        if (
            rom.u8(offset) != LOAD_DE_IMMEDIATE || rom.u8(offset + 3) != JR ||
            rom.u8(offset + 5) != LOAD_DE_IMMEDIATE || rom.u8(offset + 8) != LOAD_HL_IMMEDIATE
        ) return@runCatching null
        val sharedLoop = offset + 5 + rom.u8(offset + 4).toByte().toInt()
        if (sharedLoop != offset + 8 || !provesPlaneCopyLoop(rom, sharedLoop + 3, minOf(bankEnd, sharedLoop + 32))) {
            return@runCatching null
        }
        val johto = readPlane(rom, bank, rom.u16le(offset + 1)) ?: return@runCatching null
        val kanto = readPlane(rom, bank, rom.u16le(offset + 6)) ?: return@runCatching null
        val paletteMap = parsePaletteMapConsumer(rom, offset + MAP_COPY_BYTES, minOf(bankEnd, offset + 144))
            ?: return@runCatching null
        MapAuthority(bank, johto, kanto, paletteMap)
    }.getOrNull()

    private fun provesPlaneCopyLoop(rom: RomImage, start: Int, end: Int): Boolean {
        val expected = intArrayOf(0x1a, 0xfe, 0xff, 0xc8, 0x1a, 0x22, 0x13, 0x18)
        return start + expected.size <= end && expected.indices.all { rom.u8(start + it) == expected[it] }
    }

    private fun readPlane(rom: RomImage, bank: Int, pointer: Int): ByteArray? {
        val root = rom.gbBankAddress(bank, pointer) ?: return null
        if (root + PLANE_BYTES > rom.size || rom.u8(root + GRID_AREA) != END_MARKER) return null
        val plane = rom.slice(root, GRID_AREA)
        return plane.takeIf { bytes -> bytes.all { (it.toInt() and 0xff) < TILE_COUNT } }
    }

    private fun parsePaletteMapConsumer(rom: RomImage, start: Int, end: Int): ByteArray? {
        var offset = start
        while (offset + 4 <= end) {
            if (rom.u8(offset) == COMPARE_IMMEDIATE && rom.u8(offset + 1) == PALETTE_TILE_LIMIT) {
                var cursor = offset + 2
                while (cursor + 3 <= minOf(end, offset + 48)) {
                    if (rom.u8(cursor) == LOAD_HL_IMMEDIATE) {
                        val bank = cursor / BANK_BYTES
                        val root = rom.gbBankAddress(bank, rom.u16le(cursor + 1))
                        if (root != null && root + PALETTE_MAP_BYTES <= rom.size) {
                            val value = rom.slice(root, PALETTE_MAP_BYTES)
                            if (value.all { packed ->
                                    (packed.toInt() and 0x0f) < PALETTE_COUNT &&
                                        ((packed.toInt() and 0xff) ushr 4) < PALETTE_COUNT
                                }
                            ) return value
                        }
                    }
                    cursor++
                }
            }
            offset++
        }
        return null
    }

    private fun findGraphicsLoaders(rom: RomImage, bank: Int): List<GraphicsLoader> {
        val start = bank * BANK_BYTES
        val end = minOf(start + BANK_BYTES, rom.size)
        return buildList {
            var offset = start
            while (offset + GRAPHICS_LOADER_BYTES <= end) {
                parseGraphicsLoaderAt(rom, bank, offset)?.let(::add)
                offset++
            }
        }
    }

    private fun parseGraphicsLoaderAt(rom: RomImage, bank: Int, offset: Int): GraphicsLoader? = runCatching {
        if (
            rom.u8(offset) != LOAD_HL_IMMEDIATE || rom.u8(offset + 3) != LOAD_DE_IMMEDIATE ||
            rom.u16le(offset + 4) !in VRAM_TILE_RANGE || rom.u8(offset + 6) != LOAD_BC_IMMEDIATE ||
            rom.u8(offset + 7) != TILE_COUNT || rom.u8(offset + 9) != CALL || rom.u8(offset + 12) != RETURN
        ) return@runCatching null
        val compressedBank = rom.u8(offset + 8)
        val root = rom.gbBankAddress(compressedBank, rom.u16le(offset + 1)) ?: return@runCatching null
        val source = rom.slice(root, minOf(MAX_COMPRESSED_BYTES, rom.size - root))
        val decoded = runCatching { Lz3Decoder.decode(source) }.getOrNull() ?: return@runCatching null
        if (decoded.size != TILE_BYTES) return@runCatching null
        GraphicsLoader(bank, decoded)
    }.getOrNull()

    private fun findPaletteLoaders(rom: RomImage): List<ShortArray> = buildList {
        var offset = 0
        while (offset + PALETTE_LOADER_BYTES <= rom.size) {
            if (
                rom.u8(offset) == LOAD_HL_IMMEDIATE && rom.u8(offset + 3) == LOAD_DE_IMMEDIATE &&
                rom.u16le(offset + 4) in WRAM_PALETTE_DESTINATIONS && rom.u8(offset + 6) == LOAD_BC_IMMEDIATE &&
                rom.u16le(offset + 7) == PALETTE_BYTES
            ) {
                val bank = offset / BANK_BYTES
                val root = rom.gbBankAddress(bank, rom.u16le(offset + 1))
                if (root != null && provesPaletteJumpTableAuthority(rom, offset)) {
                    parseTownMapPalettes(rom, root)?.let(::add)
                }
            }
            offset++
        }
    }.distinctBy(ShortArray::contentHashCode)

    /**
     * The six-palette copy must be the third entry of a compiled CGB layout jump table. The map
     * code selects that same entry through `ld b, 2` before its layout call. This joins the
     * otherwise bank-separated palette code to the Town Map consumer without an identity value.
     */
    private fun provesPaletteJumpTableAuthority(rom: RomImage, paletteCopy: Int): Boolean {
        val bank = paletteCopy / BANK_BYTES
        val localCopy = (paletteCopy % BANK_BYTES) + BANK_BYTES
        val start = bank * BANK_BYTES
        val end = minOf(start + BANK_BYTES, rom.size)
        var table = start
        while (table + PALETTE_LAYOUT_INDEX * 2 + 2 <= end) {
            val entry = rom.u16le(table + PALETTE_LAYOUT_INDEX * 2)
            if (entry in (localCopy - MAX_PALETTE_ENTRY_PREFIX)..localCopy) {
                val localTable = (table % BANK_BYTES) + BANK_BYTES
                val dispatcherStart = maxOf(start, table - MAX_LAYOUT_DISPATCH_BYTES)
                var cursor = dispatcherStart
                while (cursor + 12 <= table) {
                    if (
                        rom.u8(cursor) == LOAD_DE_IMMEDIATE && rom.u16le(cursor + 1) == localTable &&
                        provesJumpTableDispatch(rom, cursor + 3, table)
                    ) return true
                    cursor++
                }
            }
            table++
        }
        return false
    }

    private fun provesJumpTableDispatch(rom: RomImage, start: Int, end: Int): Boolean {
        var sawAdd = false
        var sawRead = false
        var sawJump = false
        var cursor = start
        while (cursor < end) {
            when (rom.u8(cursor)) {
                0x19 -> sawAdd = true // add hl,de
                0x2a -> sawRead = true // ld a,[hli]
                0xe9 -> sawJump = true // jp hl
            }
            cursor++
        }
        return sawAdd && sawRead && sawJump
    }

    private fun hasPaletteSelectionCaller(rom: RomImage, bank: Int): Boolean {
        val start = bank * BANK_BYTES
        val end = minOf(start + BANK_BYTES, rom.size)
        var offset = start
        while (offset + 5 <= end) {
            if (
                rom.u8(offset) == LOAD_B_IMMEDIATE && rom.u8(offset + 1) == PALETTE_LAYOUT_INDEX &&
                rom.u8(offset + 2) == CALL
            ) return true
            offset++
        }
        return false
    }

    private fun parseTownMapPalettes(rom: RomImage, offset: Int): ShortArray? = runCatching {
        if (offset + PALETTE_BYTES > rom.size) return@runCatching null
        val colors = ShortArray(PALETTE_COLOR_COUNT) { rom.u16le(offset + it * 2).toShort() }
        if (colors.any { (it.toInt() and 0xffff) > MAX_BGR555 }) return@runCatching null
        if ((0 until PALETTE_COUNT).map { colors[it * COLORS_PER_PALETTE] }.distinct().size != 1) {
            return@runCatching null
        }
        if ((0 until PALETTE_COUNT).map { index ->
                (0 until COLORS_PER_PALETTE).map { colors[index * COLORS_PER_PALETTE + it] }
            }.distinct().size < MIN_DISTINCT_PALETTES
        ) return@runCatching null
        colors
    }.getOrNull()

    private fun findBindingChains(rom: RomImage, requiredMaps: Set<Int>): BindingSearch {
        val groupRoots = findMapGroupRoots(rom, requiredMaps)
        val landmarkAuthorities = findLandmarkAuthorities(rom)
        val classifiers = findRegionClassifiers(rom)
        val chains = buildList {
            for (groupRoot in groupRoots) {
                for (landmarks in landmarkAuthorities) {
                    for (classifier in classifiers) {
                        buildBindings(rom, requiredMaps, groupRoot, landmarks, classifier)?.let(::add)
                    }
                }
            }
        }.distinctBy { chain ->
            chain.johto.joinToString("|") { "${it.id}:${it.x}:${it.y}:${it.baseAreaIds}" } + "/" +
                chain.kanto.joinToString("|") { "${it.id}:${it.x}:${it.y}:${it.baseAreaIds}" }
        }
        return BindingSearch(chains, groupRoots.size, landmarkAuthorities.size, classifiers.size)
    }

    private fun findMapGroupRoots(rom: RomImage, requiredMaps: Set<Int>): List<MapGroupAuthority> = buildList {
        var offset = 0
        while (offset + MAP_POINTER_CONSUMER_BYTES <= minOf(BANK_BYTES, rom.size)) {
            val authority = parseMapPointerConsumerAt(rom, offset, requiredMaps)
            if (authority != null) add(authority)
            offset++
        }
    }.distinctBy { it.tableOffset }

    private fun parseMapPointerConsumerAt(
        rom: RomImage,
        offset: Int,
        requiredMaps: Set<Int>,
    ): MapGroupAuthority? = runCatching {
        val prefix = intArrayOf(0xc5, 0x05, 0x48, 0x06, 0x00, 0x21)
        if (!prefix.indices.all { rom.u8(offset + it) == prefix[it] }) return@runCatching null
        if (
            rom.u8(offset + 8) != 0x09 || rom.u8(offset + 9) != 0x09 ||
            rom.u8(offset + 10) != 0x2a || rom.u8(offset + 11) != 0x66 || rom.u8(offset + 12) != 0x6f ||
            rom.u8(offset + 13) != 0xc1 || rom.u8(offset + 14) != 0x0d ||
            rom.u8(offset + 15) != 0x06 || rom.u8(offset + 16) != 0x00 ||
            rom.u8(offset + 17) != LOAD_A_IMMEDIATE || rom.u8(offset + 18) != MAP_HEADER_BYTES ||
            rom.u8(offset + 19) != CALL || rom.u8(offset + 22) != RETURN
        ) return@runCatching null
        val table = rom.gbBankAddress(MAP_DATA_BANK, rom.u16le(offset + 6)) ?: return@runCatching null
        if (!requiredMaps.all { base ->
                val group = base ushr 8
                val map = base and 0xff
                val groupPointer = rom.u16le(table + (group - 1) * 2)
                val root = rom.gbBankAddress(MAP_DATA_BANK, groupPointer) ?: return@all false
                root + (map - 1) * MAP_HEADER_BYTES + MAP_HEADER_BYTES <= rom.size
            }
        ) return@runCatching null
        MapGroupAuthority(table)
    }.getOrNull()

    private fun findLandmarkAuthorities(rom: RomImage): List<LandmarkAuthority> = buildList {
        var offset = BANK_BYTES
        while (offset + LANDMARK_CONSUMER_BYTES <= rom.size) {
            parseLandmarkConsumerAt(rom, offset)?.let(::add)
            offset++
        }
    }.distinctBy { it.tableOffset }

    private fun parseLandmarkConsumerAt(rom: RomImage, offset: Int): LandmarkAuthority? = runCatching {
        val prefix = intArrayOf(0xe5, 0x6b, 0x26, 0x00, 0x29, 0x29, 0x11)
        if (!prefix.indices.all { rom.u8(offset + it) == prefix[it] }) return@runCatching null
        val bank = offset / BANK_BYTES
        val table = rom.gbBankAddress(bank, rom.u16le(offset + 7)) ?: return@runCatching null
        if (
            rom.u8(offset + 9) != 0x19 || rom.u8(offset + 10) != 0x2a ||
            rom.u8(offset + 11) != 0x5f || rom.u8(offset + 12) != 0x56 ||
            rom.u8(offset + 13) != 0xe1 || rom.u8(offset + 14) != RETURN
        ) return@runCatching null
        LandmarkAuthority(bank, table)
    }.getOrNull()

    private fun findRegionClassifiers(rom: RomImage): List<RegionClassifier> {
        val detailed = findDetailedRegionClassifiers(rom)
        return if (detailed.isNotEmpty()) detailed else findOneThresholdRegionClassifiers(rom)
    }

    private fun findDetailedRegionClassifiers(rom: RomImage): List<RegionClassifier> = buildList {
        var offset = 0
        while (offset + REGION_CLASSIFIER_BYTES <= rom.size) {
            parseDetailedRegionClassifierAt(rom, offset)?.let(::add)
            offset++
        }
    }.distinct()

    private fun parseDetailedRegionClassifierAt(rom: RomImage, offset: Int): RegionClassifier? = runCatching {
        val common = parseRegionClassifierPrefix(rom, offset) ?: return@runCatching null
        val check = common.checkOffset
        if (
            rom.u8(check) != COMPARE_IMMEDIATE || rom.u8(check + 2) != JR_C ||
            rom.u8(check + 4) != COMPARE_IMMEDIATE || rom.u8(check + 6) != JR_C
        ) return@runCatching null
        val johto = check + 8
        val kanto = johto + 3
        if (
            !provesRegionReturnE(rom, johto, JOHTO_REGION) || !provesRegionReturnE(rom, kanto, KANTO_REGION) ||
            branchTarget(check + 2, rom.u8(check + 3)) != johto ||
            branchTarget(check + 6, rom.u8(check + 7)) != kanto || common.shipTarget != johto
        ) return@runCatching null
        val firstThreshold = rom.u8(check + 1)
        val secondThreshold = rom.u8(check + 5)
        if (
            firstThreshold <= SPECIAL_LANDMARK || secondThreshold <= firstThreshold ||
            common.ship < secondThreshold
        ) return@runCatching null
        RegionClassifier(firstThreshold, secondThreshold, common.ship)
    }.getOrNull()

    /**
     * Some source-compatible Gen II builds retain the complete `IsInJohto` consumer but omit a
     * valid Victory Road exception in the far region helper. Its one-threshold form still proves
     * the same current-map call, FAST_SHIP path, dynamic-special retry, threshold, and common
     * Johto/Kanto returns. Prefer the richer two-threshold authority whenever it is complete.
     */
    private fun findOneThresholdRegionClassifiers(rom: RomImage): List<RegionClassifier> = buildList {
        var offset = 0
        while (offset + REGION_CLASSIFIER_BYTES <= rom.size) {
            parseOneThresholdRegionClassifierAt(rom, offset)?.let(::add)
            offset++
        }
    }.distinct()

    private fun parseOneThresholdRegionClassifierAt(rom: RomImage, offset: Int): RegionClassifier? = runCatching {
        val common = parseRegionClassifierPrefix(rom, offset) ?: return@runCatching null
        val check = common.checkOffset
        if (rom.u8(check) != COMPARE_IMMEDIATE || rom.u8(check + 2) != JR_NC) return@runCatching null
        val johto = check + 4
        val kanto = johto + 2
        if (
            rom.u8(johto) != XOR_A || rom.u8(johto + 1) != RETURN ||
            rom.u8(kanto) != LOAD_A_IMMEDIATE || rom.u8(kanto + 1) != KANTO_REGION || rom.u8(kanto + 2) != RETURN ||
            branchTarget(check + 2, rom.u8(check + 3)) != kanto || common.shipTarget != johto
        ) return@runCatching null
        val threshold = rom.u8(check + 1)
        if (threshold <= SPECIAL_LANDMARK || threshold >= common.ship) return@runCatching null
        RegionClassifier(threshold, common.ship, common.ship)
    }.getOrNull()

    private fun parseRegionClassifierPrefix(rom: RomImage, offset: Int): RegionClassifierPrefix? = runCatching {
        if (
            offset < MAP_LOCATION_CALL_BYTES || rom.u8(offset) != COMPARE_IMMEDIATE ||
            rom.u8(offset + 2) != JR_Z || rom.u8(offset + 4) != COMPARE_IMMEDIATE ||
            rom.u8(offset + 5) != SPECIAL_LANDMARK || rom.u8(offset + 6) != JR_NZ
        ) return@runCatching null
        val currentCall = parseMapLocationCall(rom, offset - MAP_LOCATION_CALL_BYTES) ?: return@runCatching null
        val backupCall = parseMapLocationCall(rom, offset + 8) ?: return@runCatching null
        if (currentCall != backupCall) return@runCatching null
        val check = offset + 8 + MAP_LOCATION_CALL_BYTES
        if (branchTarget(offset + 6, rom.u8(offset + 7)) != check) return@runCatching null
        RegionClassifierPrefix(
            ship = rom.u8(offset + 1),
            shipTarget = branchTarget(offset + 2, rom.u8(offset + 3)),
            checkOffset = check,
        )
    }.getOrNull()

    private fun parseMapLocationCall(rom: RomImage, offset: Int): Int? = runCatching {
        if (
            rom.u8(offset) != LOAD_A_ABSOLUTE || rom.u8(offset + 3) != LOAD_B_A ||
            rom.u8(offset + 4) != LOAD_A_ABSOLUTE || rom.u8(offset + 7) != LOAD_C_A ||
            rom.u8(offset + 8) != CALL
        ) return@runCatching null
        rom.u16le(offset + 9)
    }.getOrNull()

    private fun provesRegionReturnE(rom: RomImage, offset: Int, region: Int): Boolean =
        rom.u8(offset) == LOAD_E_IMMEDIATE && rom.u8(offset + 1) == region && rom.u8(offset + 2) == RETURN

    private fun branchTarget(opcodeOffset: Int, encodedDelta: Int): Int =
        opcodeOffset + 2 + encodedDelta.toByte().toInt()

    private fun buildBindings(
        rom: RomImage,
        requiredMaps: Set<Int>,
        groups: MapGroupAuthority,
        landmarks: LandmarkAuthority,
        classifier: RegionClassifier,
    ): BindingChain? = runCatching {
        val grouped = linkedMapOf<Int, MutableSet<Int>>()
        for (base in requiredMaps) {
            val group = base ushr 8
            val map = base and 0xff
            val pointer = rom.u16le(groups.tableOffset + (group - 1) * 2)
            val root = rom.gbBankAddress(MAP_DATA_BANK, pointer) ?: return@runCatching null
            val row = root + (map - 1) * MAP_HEADER_BYTES
            val landmark = rom.u8(row + MAP_LOCATION_FIELD)
            if (landmark == SPECIAL_LANDMARK) return@runCatching null
            if (landmark > classifier.ship) return@runCatching null
            grouped.getOrPut(landmark) { linkedSetOf() } += base
        }
        val decoded = grouped.map { (id, baseIds) ->
            val row = landmarks.tableOffset + id * LANDMARK_BYTES
            if (row + LANDMARK_BYTES > rom.size) return@runCatching null
            val rawX = rom.u8(row)
            val rawY = rom.u8(row + 1)
            if (rawX < COORDINATE_X_BIAS || rawY < COORDINATE_Y_BIAS) return@runCatching null
            val x = (rawX - COORDINATE_X_BIAS) / TILE_EDGE
            val y = (rawY - COORDINATE_Y_BIAS) / TILE_EDGE
            if (x !in 0 until GRID_WIDTH || y !in 0 until GRID_HEIGHT) return@runCatching null
            val namePointer = rom.u16le(row + 2)
            val nameRoot = rom.gbBankAddress(landmarks.bank, namePointer) ?: return@runCatching null
            val name = Gen2LandmarkNameCodec.decode(
                rom.slice(nameRoot, minOf(MAX_NAME_BYTES, rom.size - nameRoot)),
            ) ?: return@runCatching null
            Landmark(id, x, y, name, baseIds.toSet())
        }
        BindingChain(
            johto = decoded.filter { it.id < classifier.kanto || it.id >= classifier.victory },
            kanto = decoded.filter { it.id in classifier.kanto until classifier.victory },
        ).takeIf { it.johto.isNotEmpty() && it.kanto.isNotEmpty() }
    }.getOrNull()

    private fun compose(tiles: ByteArray, plane: ByteArray, paletteMap: ByteArray, palettes: ShortArray): RgbaSprite {
        val pixels = IntArray(PIXEL_WIDTH * PIXEL_HEIGHT)
        repeat(GRID_HEIGHT) { tileY ->
            repeat(GRID_WIDTH) { tileX ->
                val tile = plane[tileY * GRID_WIDTH + tileX].toInt() and 0xff
                val packedPalette = paletteMap[tile / 2].toInt() and 0xff
                val palette = if (tile and 1 == 0) packedPalette and 0x0f else packedPalette ushr 4
                repeat(TILE_EDGE) { row ->
                    val low = tiles[tile * BYTES_PER_TILE + row * 2].toInt() and 0xff
                    val high = tiles[tile * BYTES_PER_TILE + row * 2 + 1].toInt() and 0xff
                    repeat(TILE_EDGE) { column ->
                        val bit = 7 - column
                        val color = ((low ushr bit) and 1) or (((high ushr bit) and 1) shl 1)
                        val bgr555 = palettes[palette * COLORS_PER_PALETTE + color].toInt() and 0xffff
                        val x = tileX * TILE_EDGE + column
                        val y = tileY * TILE_EDGE + row
                        pixels[y * PIXEL_WIDTH + x] = TileRenderer.bgr555ToArgb(bgr555, transparent = false)
                    }
                }
            }
        }
        return RgbaSprite(PIXEL_WIDTH, PIXEL_HEIGHT, pixels)
    }

    private data class MapAuthority(val bank: Int, val johtoMap: ByteArray, val kantoMap: ByteArray, val paletteMap: ByteArray)
    private data class GraphicsLoader(val loaderBank: Int, val tiles: ByteArray)
    private data class AssetChain(
        val tiles: ByteArray,
        val johtoMap: ByteArray,
        val kantoMap: ByteArray,
        val paletteMap: ByteArray,
        val palettes: ShortArray,
    )
    private data class MapGroupAuthority(val tableOffset: Int)
    private data class LandmarkAuthority(val bank: Int, val tableOffset: Int)
    private data class RegionClassifier(val kanto: Int, val victory: Int, val ship: Int)
    private data class RegionClassifierPrefix(val ship: Int, val shipTarget: Int, val checkOffset: Int)
    private data class Landmark(val id: Int, val x: Int, val y: Int, val name: String, val baseAreaIds: Set<Int>)
    private data class BindingChain(val johto: List<Landmark>, val kanto: List<Landmark>)
    private data class BindingSearch(
        val chains: List<BindingChain>,
        val groupRoots: Int,
        val landmarks: Int,
        val classifiers: Int,
    )
    private data class RegionInput(val key: String, val displayName: String, val map: ByteArray, val landmarks: List<Landmark>)

    private const val BANK_BYTES = 0x4000
    private const val MAP_DATA_BANK = 0x25
    private const val TILE_EDGE = 8
    private const val BYTES_PER_TILE = 16
    private const val TILE_COUNT = 48
    private const val TILE_BYTES = TILE_COUNT * BYTES_PER_TILE
    private const val GRID_WIDTH = 20
    private const val GRID_HEIGHT = 18
    private const val GRID_AREA = GRID_WIDTH * GRID_HEIGHT
    private const val PLANE_BYTES = GRID_AREA + 1
    private const val PIXEL_WIDTH = GRID_WIDTH * TILE_EDGE
    private const val PIXEL_HEIGHT = GRID_HEIGHT * TILE_EDGE
    private const val PALETTE_COUNT = 6
    private const val COLORS_PER_PALETTE = 4
    private const val PALETTE_COLOR_COUNT = PALETTE_COUNT * COLORS_PER_PALETTE
    private const val PALETTE_BYTES = PALETTE_COLOR_COUNT * 2
    private const val PALETTE_MAP_BYTES = TILE_COUNT / 2
    private const val PALETTE_TILE_LIMIT = 0x60
    private const val MIN_DISTINCT_PALETTES = 5
    private const val MAX_BGR555 = 0x7fff
    private const val PALETTE_LAYOUT_INDEX = 2
    private const val MAX_PALETTE_ENTRY_PREFIX = 24
    private const val MAX_LAYOUT_DISPATCH_BYTES = 64
    private const val MAX_COMPRESSED_BYTES = 4096
    private const val MAX_MAP_GROUPS = 63
    private const val MAX_MAPS_PER_GROUP = 254
    private const val MAP_HEADER_BYTES = 9
    private const val MAP_LOCATION_FIELD = 5
    private const val LANDMARK_BYTES = 4
    private const val SPECIAL_LANDMARK = 0
    private const val COORDINATE_X_BIAS = 8
    private const val COORDINATE_Y_BIAS = 16
    private const val MAX_NAME_BYTES = 32
    private const val END_MARKER = 0xff
    private const val MAP_AUTHORITY_BYTES = 24
    private const val MAP_COPY_BYTES = 20
    private const val GRAPHICS_LOADER_BYTES = 13
    private const val PALETTE_LOADER_BYTES = 9
    private const val MAP_POINTER_CONSUMER_BYTES = 23
    private const val LANDMARK_CONSUMER_BYTES = 15
    private const val REGION_CLASSIFIER_BYTES = 40
    private const val MAP_LOCATION_CALL_BYTES = 11
    private const val LOAD_HL_IMMEDIATE = 0x21
    private const val LOAD_DE_IMMEDIATE = 0x11
    private const val LOAD_BC_IMMEDIATE = 0x01
    private const val LOAD_A_IMMEDIATE = 0x3e
    private const val LOAD_A_ABSOLUTE = 0xfa
    private const val LOAD_B_A = 0x47
    private const val LOAD_C_A = 0x4f
    private const val LOAD_E_IMMEDIATE = 0x1e
    private const val LOAD_B_IMMEDIATE = 0x06
    private const val COMPARE_IMMEDIATE = 0xfe
    private const val CALL = 0xcd
    private const val RETURN = 0xc9
    private const val JR = 0x18
    private const val JR_NZ = 0x20
    private const val JR_Z = 0x28
    private const val JR_C = 0x38
    private const val JR_NC = 0x30
    private const val XOR_A = 0xaf
    private const val JOHTO_REGION = 0
    private const val KANTO_REGION = 1
    private val VRAM_TILE_RANGE = 0x8800..0x97ff
    private val WRAM_PALETTE_DESTINATIONS = setOf(0xc200, 0xd000)
}
