package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog
import com.enrpau.dualscreendex.parser.catalog.WorldMapCell
import com.enrpau.dualscreendex.parser.catalog.WorldMapLocation
import com.enrpau.dualscreendex.parser.catalog.WorldMapRegion
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.sprite.Lz3Decoder
import com.enrpau.dualscreendex.parser.sprite.TileRenderer
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import java.util.concurrent.CancellationException

/** Resolves the Gen II Town Map through compiled asset, map-header, and landmark consumers. */
object Gen2WorldMapResolver {
    internal fun resolveLandmarkNames(
        session: RomAnalysisSession,
        mapIds: Set<Int>,
        codec: PokemonTextCodec,
        landmarkIds: Set<Int> = emptySet(),
    ): Map<Int, String> {
        val cancellation = session.cancellation
        cancellation.throwIfCancellationRequested()
        val requiredMaps = mapIds.filterTo(sortedSetOf()) { base ->
            cancellation.throwIfCancellationRequested()
            val group = base ushr 8
            val map = base and 0xff
            group in 1..MAX_MAP_GROUPS && map in 1..MAX_MAPS_PER_GROUP
        }
        if (requiredMaps.isEmpty()) return emptyMap()
        val binding = findBindingChains(session.rom, requiredMaps, codec, cancellation).chains.singleOrNull()
            ?: return emptyMap()
        val boundLandmarks = binding.johto + binding.kanto
        val known = boundLandmarks.mapNotNull { landmark -> landmark.name?.let { landmark.id to it } }.toMap()
        val requestedIds = boundLandmarks.mapTo(sortedSetOf()) { it.id }
        landmarkIds.filterTo(requestedIds) { id ->
            cancellation.throwIfCancellationRequested()
            id in FIRST_STATIC_LANDMARK..MAX_STATIC_LANDMARK
        }
        // Only sources that passed the numeric join and its whole-name dialect check can add labels.
        // An undecodable source remains a text veto; never fall back to the first readable source.
        val candidates = binding.nameSources.map { source ->
            cancellation.throwIfCancellationRequested()
            val encoding = source.encoding ?: return@map emptyMap()
            requestedIds.mapNotNull { id ->
                cancellation.throwIfCancellationRequested()
                decodeLandmarkName(session.rom, source.authority, id, encoding, codec, cancellation)?.let { id to it }
            }.toMap()
        }
        val names = requestedIds.mapNotNull { id ->
            cancellation.throwIfCancellationRequested()
            val name = candidates.map { it[id] }.distinct().singleOrNull() ?: return@mapNotNull null
            id to name
        }.toMap()
        return known + names
    }

    fun resolve(
        session: RomAnalysisSession,
        encounterBaseIds: Set<Int>,
        codec: PokemonTextCodec?,
    ): WorldMapResolution {
        val cancellation = session.cancellation
        cancellation.throwIfCancellationRequested()
        val requiredMaps = encounterBaseIds.filterTo(sortedSetOf()) { base ->
            cancellation.throwIfCancellationRequested()
            val group = base ushr 8
            val map = base and 0xff
            group in 1..MAX_MAP_GROUPS && map in 1..MAX_MAPS_PER_GROUP
        }
        if (requiredMaps.isEmpty()) {
            return WorldMapResolution.Unavailable("encounter-binding", "no encounter-bound Gen II group/map IDs")
        }
        val assets = findAssetChains(session.rom, cancellation)
        if (assets.isEmpty()) {
            return WorldMapResolution.Unavailable(
                "asset-loader",
                "no complete Gen II Town Map graphics, plane, palette-map, and palette chain passed validation",
            )
        }
        if (assets.size != 1) {
            return WorldMapResolution.Ambiguous("asset-loader", "${assets.size} complete Gen II asset chains remained")
        }
        val bindingSearch = findBindingChains(session.rom, requiredMaps, codec, cancellation)
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
        val hasBothEncounterRegions = binding.johto.isNotEmpty() && binding.kanto.isNotEmpty()
        val regionInputs = listOf(
            RegionInput(
                "gen2-johto",
                codec?.let { "Johto" }?.takeIf { hasBothEncounterRegions },
                asset.johtoMap,
                binding.johto,
            ),
            RegionInput(
                "gen2-kanto",
                codec?.let { "Kanto" }?.takeIf { hasBothEncounterRegions },
                asset.kantoMap,
                binding.kanto,
            ),
        ).filter { it.landmarks.isNotEmpty() }
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
            assets = regionInputs.associate { input ->
                "world/${input.key}" to compose(
                    asset.tiles,
                    input.map,
                    asset.paletteTileLimit,
                    asset.paletteMap,
                    asset.palettes,
                    cancellation,
                )
            },
        ).validate()
        return WorldMapResolution.Resolved(
            catalog,
            listOf(
                "validated one compiled ${asset.requestedTileCount}-tile request/${asset.decodedTileCount}-tile decoded " +
                    "2bpp, two-plane, palette-map, and ${asset.paletteCount}-palette asset chain",
                "joined ${requiredMaps.size} encounter maps through one compiled header, landmark, and region chain",
            ),
        )
    }

    private fun findAssetChains(rom: RomImage, cancellation: ParserCancellationToken): List<AssetChain> {
        val bankCount = rom.size / BANK_BYTES
        val mapAuthorities = buildList {
            for (bank in 1 until bankCount) {
                cancellation.throwIfCancellationRequested()
                addAll(findMapAuthorities(rom, bank, cancellation))
            }
        }
        val graphics = buildList {
            for (bank in 1 until bankCount) {
                cancellation.throwIfCancellationRequested()
                addAll(findGraphicsLoaders(rom, bank, cancellation))
            }
        }
        val palettes = findPaletteLoaders(rom, cancellation)
        if (System.getenv("DUALDEX_MAP_TRACE") == "1") {
            println(
                "world-map-trace gen2 assets " +
                    "maps=${mapAuthorities.map { "${it.bank}:${it.paletteLookupLimit}:${it.paletteMapOffset}" }} " +
                    "graphics=${graphics.map { "${it.loaderBank}:${it.requestedTileCount}:${it.decodedTileCount}" }} " +
                    "palettes=${palettes.map { it.count }} " +
                    "paletteCallers=${mapAuthorities.associate { it.bank to hasPaletteSelectionCaller(rom, it.bank, cancellation) }}",
            )
        }
        return buildList {
            for (map in mapAuthorities) {
                cancellation.throwIfCancellationRequested()
                if (!hasPaletteSelectionCaller(rom, map.bank, cancellation)) continue
                for (gfx in graphics.filter { candidate ->
                    candidate.loaderBank == map.bank &&
                        candidate.requestedTileCount <= map.paletteLookupLimit &&
                        map.allPlanes.all { plane ->
                            hasCompleteTileDomain(
                                plane,
                                candidate.decodedTileCount,
                                candidate.requestedTileCount,
                                map.paletteLookupLimit,
                            )
                        }
                }) {
                    val paletteMap = readPaletteMap(
                        rom,
                        map.paletteMapOffset,
                        gfx.requestedTileCount,
                    ) ?: continue
                    for (palette in palettes.filter { it.count >= paletteMap.requiredPaletteCount }) {
                        cancellation.throwIfCancellationRequested()
                        add(
                            AssetChain(
                                requestedTileCount = gfx.requestedTileCount,
                                decodedTileCount = gfx.decodedTileCount,
                                paletteTileLimit = gfx.requestedTileCount,
                                paletteCount = palette.count,
                                tiles = gfx.tiles,
                                johtoMap = map.johtoMap,
                                kantoMap = map.kantoMap,
                                paletteMap = paletteMap.bytes,
                                palettes = palette.colors,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun findMapAuthorities(rom: RomImage, bank: Int, cancellation: ParserCancellationToken): List<MapAuthority> {
        val bankStart = bank * BANK_BYTES
        val bankEnd = minOf(bankStart + BANK_BYTES, rom.size)
        return buildList {
            var offset = bankStart
            while (offset + MAP_AUTHORITY_BYTES <= bankEnd) {
                cancellation.throwIfCancellationRequested()
                parseMapAuthorityAt(rom, bank, offset, bankEnd)?.let(::add)
                offset++
            }
        }
    }

    private fun parseMapAuthorityAt(
        rom: RomImage,
        bank: Int,
        offset: Int,
        bankEnd: Int,
    ): MapAuthority? =
        parseDirectMapAuthorityAt(rom, bank, offset, bankEnd)
            ?: parseGuardedMapAuthorityAt(rom, bank, offset, bankEnd)

    private fun parseDirectMapAuthorityAt(rom: RomImage, bank: Int, offset: Int, bankEnd: Int): MapAuthority? = runCatching {
        if (
            rom.u8(offset) != LOAD_DE_IMMEDIATE || rom.u8(offset + 3) != JR ||
            rom.u8(offset + 5) != LOAD_DE_IMMEDIATE || rom.u8(offset + 8) != LOAD_HL_IMMEDIATE
        ) return@runCatching null
        val sharedLoop = offset + 5 + rom.u8(offset + 4).toByte().toInt()
        if (sharedLoop != offset + 8 || !provesPlaneCopyLoop(rom, sharedLoop + 3, minOf(bankEnd, sharedLoop + 32))) {
            return@runCatching null
        }
        val paletteMap = parsePaletteMapConsumer(rom, offset + MAP_COPY_BYTES, minOf(bankEnd, offset + 144))
            ?: return@runCatching null
        val johto = readPlane(rom, bank, rom.u16le(offset + 1)) ?: return@runCatching null
        val kanto = readPlane(rom, bank, rom.u16le(offset + 6)) ?: return@runCatching null
        MapAuthority(
            bank = bank,
            paletteLookupLimit = paletteMap.tileLimit,
            johtoMap = johto,
            kantoMap = kanto,
            additionalMaps = emptyList(),
            paletteMapOffset = paletteMap.offset,
        )
    }.getOrNullUnlessCancelled()

    private fun parseGuardedMapAuthorityAt(
        rom: RomImage,
        bank: Int,
        offset: Int,
        bankEnd: Int,
    ): MapAuthority? = runCatching {
        if (
            rom.u8(offset) != LOAD_DE_IMMEDIATE ||
            rom.u8(offset + 3) != JR ||
            rom.u8(offset + 5) != LOAD_A_ABSOLUTE ||
            rom.u8(offset + 8) != CB_PREFIX ||
            !isBitTestA(rom.u8(offset + 9)) ||
            rom.u8(offset + 10) != LOAD_DE_IMMEDIATE ||
            rom.u8(offset + 13) != JR_NZ ||
            rom.u8(offset + 15) != LOAD_DE_IMMEDIATE ||
            rom.u8(offset + 18) != LOAD_HL_IMMEDIATE
        ) return@runCatching null

        val sharedLoop = offset + 18
        val johtoTarget = offset + 5 + rom.u8(offset + 4).toByte().toInt()
        val kantoTarget = offset + 15 + rom.u8(offset + 14).toByte().toInt()
        if (
            johtoTarget != sharedLoop ||
            kantoTarget != sharedLoop ||
            !provesPlaneCopyLoop(rom, sharedLoop + 3, minOf(bankEnd, sharedLoop + 32))
        ) return@runCatching null

        val paletteMap = parsePaletteMapConsumer(
            rom,
            offset + GUARDED_MAP_COPY_BYTES,
            minOf(bankEnd, offset + 160),
        ) ?: return@runCatching null
        val johto = readPlane(rom, bank, rom.u16le(offset + 1)) ?: return@runCatching null
        val kanto = readPlane(rom, bank, rom.u16le(offset + 11)) ?: return@runCatching null
        val alternate = readPlane(rom, bank, rom.u16le(offset + 16)) ?: return@runCatching null
        MapAuthority(
            bank = bank,
            paletteLookupLimit = paletteMap.tileLimit,
            johtoMap = johto,
            kantoMap = kanto,
            additionalMaps = listOf(alternate),
            paletteMapOffset = paletteMap.offset,
        )
    }.getOrNullUnlessCancelled()

    private fun isBitTestA(opcode: Int): Boolean =
        opcode in BIT_0_A..BIT_7_A &&
            (opcode - BIT_0_A) % BIT_OPCODE_STRIDE == 0

    private fun provesPlaneCopyLoop(rom: RomImage, start: Int, end: Int): Boolean {
        val expected = intArrayOf(0x1a, 0xfe, 0xff, 0xc8, 0x1a, 0x22, 0x13, 0x18)
        return start + expected.size <= end && expected.indices.all { rom.u8(start + it) == expected[it] }
    }

    private fun readPlane(rom: RomImage, bank: Int, pointer: Int): ByteArray? {
        val root = rom.gbBankAddress(bank, pointer) ?: return null
        if (root + PLANE_BYTES > rom.size || rom.u8(root + GRID_AREA) != END_MARKER) return null
        return rom.slice(root, GRID_AREA)
    }

    /**
     * Tiles between the proven palette-map extent and the compiled lookup threshold would make the
     * game read beyond that map, so reject them. A Town Map may still reserve its first row for the
     * landmark HUD using one unloaded blank tile, but only when that tile is also beyond the lookup
     * threshold; every other tile must be backed by decoded graphics.
     */
    private fun hasCompleteTileDomain(
        plane: ByteArray,
        decodedTileCount: Int,
        paletteCoveredTileCount: Int,
        paletteLookupLimit: Int,
    ): Boolean {
        if (plane.any { tile ->
                val value = tile.toInt() and 0xff
                value >= paletteCoveredTileCount && value < paletteLookupLimit
            }
        ) return false
        val unresolved = plane.indices.filter { (plane[it].toInt() and 0xff) >= decodedTileCount }
        if (unresolved.isEmpty()) return true
        val headerTile = plane.first().toInt() and 0xff
        return headerTile >= paletteLookupLimit &&
            (0 until GRID_WIDTH).all { (plane[it].toInt() and 0xff) == headerTile } &&
            unresolved.all { it < GRID_WIDTH }
    }

    private fun parsePaletteMapConsumer(rom: RomImage, start: Int, end: Int): PaletteMapAuthority? {
        var offset = start
        while (offset + 4 <= end) {
            val paletteLookupLimit = rom.u8(offset + 1)
            if (
                rom.u8(offset) == COMPARE_IMMEDIATE &&
                rom.u8(offset + 2) == JR_NC &&
                paletteLookupLimit in MIN_TILE_COUNT..MAX_TILE_COUNT &&
                paletteLookupLimit % 2 == 0
            ) {
                var cursor = offset + 2
                while (cursor + 3 <= minOf(end, offset + 48)) {
                    if (rom.u8(cursor) == LOAD_HL_IMMEDIATE) {
                        val bank = cursor / BANK_BYTES
                        val root = rom.gbBankAddress(bank, rom.u16le(cursor + 1))
                        val minimum = root?.let {
                            readPaletteMap(rom, it, MIN_TILE_COUNT)
                        }
                        if (minimum != null) {
                            return PaletteMapAuthority(paletteLookupLimit, requireNotNull(root))
                        }
                    }
                    cursor++
                }
            }
            offset++
        }
        return null
    }

    private fun readPaletteMap(
        rom: RomImage,
        offset: Int,
        tileCount: Int,
    ): ResolvedPaletteMap? = runCatching {
        if (tileCount !in MIN_TILE_COUNT..MAX_TILE_COUNT || tileCount % 2 != 0) {
            return@runCatching null
        }
        val byteCount = tileCount / 2
        if (offset + byteCount > rom.size) return@runCatching null
        val bytes = rom.slice(offset, byteCount)
        if (bytes.any { packed ->
                (packed.toInt() and 0x0f) >= MAX_PALETTE_COUNT ||
                    ((packed.toInt() and 0xff) ushr 4) >= MAX_PALETTE_COUNT
            }
        ) return@runCatching null
        val requiredPaletteCount = bytes.maxOf { packed ->
            maxOf(
                packed.toInt() and 0x0f,
                (packed.toInt() and 0xff) ushr 4,
            )
        } + 1
        ResolvedPaletteMap(bytes, requiredPaletteCount)
    }.getOrNullUnlessCancelled()

    private fun findGraphicsLoaders(rom: RomImage, bank: Int, cancellation: ParserCancellationToken): List<GraphicsLoader> {
        val start = bank * BANK_BYTES
        val end = minOf(start + BANK_BYTES, rom.size)
        return buildList {
            var offset = start
            while (offset + GRAPHICS_LOADER_BYTES <= end) {
                cancellation.throwIfCancellationRequested()
                parseGraphicsLoaderAt(rom, bank, offset)?.let(::add)
                offset++
            }
        }
    }

    private fun parseGraphicsLoaderAt(rom: RomImage, bank: Int, offset: Int): GraphicsLoader? = runCatching {
        val requestedTileCount = rom.u8(offset + 7)
        val terminal = rom.u8(offset + 9) == JUMP ||
            (rom.u8(offset + 9) == CALL && rom.u8(offset + 12) == RETURN)
        if (
            rom.u8(offset) != LOAD_HL_IMMEDIATE || rom.u8(offset + 3) != LOAD_DE_IMMEDIATE ||
            rom.u16le(offset + 4) !in VRAM_TILE_RANGE || rom.u8(offset + 6) != LOAD_BC_IMMEDIATE ||
            requestedTileCount !in MIN_TILE_COUNT..MAX_TILE_COUNT || !terminal
        ) return@runCatching null
        val compressedBank = rom.u8(offset + 8)
        val root = rom.gbBankAddress(compressedBank, rom.u16le(offset + 1)) ?: return@runCatching null
        val source = rom.slice(root, minOf(MAX_COMPRESSED_BYTES, rom.size - root))
        val decoded = runCatching { Lz3Decoder.decode(source) }.getOrNullUnlessCancelled() ?: return@runCatching null
        if (System.getenv("DUALDEX_MAP_TRACE") == "1") {
            println("world-map-trace gen2 graphics bank=$bank requested=$requestedTileCount decoded=${decoded.size}")
        }
        if (decoded.size % BYTES_PER_TILE != 0) return@runCatching null
        val decodedTileCount = decoded.size / BYTES_PER_TILE
        if (decodedTileCount !in requestedTileCount..MAX_TILE_COUNT) return@runCatching null
        GraphicsLoader(bank, requestedTileCount, decodedTileCount, decoded)
    }.getOrNullUnlessCancelled()

    private fun findPaletteLoaders(rom: RomImage, cancellation: ParserCancellationToken): List<PaletteAuthority> = buildList {
        var offset = 0
        while (offset + DYNAMIC_PALETTE_LOADER_BYTES <= rom.size) {
            cancellation.throwIfCancellationRequested()
            parseStaticPaletteLoaderAt(rom, offset, cancellation)?.let(::add)
            parseDynamicPaletteLoaderAt(rom, offset)?.let(::add)
            offset++
        }
    }.distinctBy { it.colors.contentHashCode() }

    private fun parseStaticPaletteLoaderAt(rom: RomImage, offset: Int, cancellation: ParserCancellationToken): PaletteAuthority? = runCatching {
        val paletteCopyBytes = rom.u16le(offset + 7)
        if (
            rom.u8(offset) != LOAD_HL_IMMEDIATE || rom.u8(offset + 3) != LOAD_DE_IMMEDIATE ||
            rom.u16le(offset + 4) !in WRAM_PALETTE_DESTINATIONS || rom.u8(offset + 6) != LOAD_BC_IMMEDIATE ||
            paletteCopyBytes !in MIN_PALETTE_BYTES..MAX_BG_PALETTE_BYTES ||
            paletteCopyBytes % BYTES_PER_PALETTE != 0 || !provesPaletteJumpTableAuthority(rom, offset, cancellation)
        ) return@runCatching null
        val bank = offset / BANK_BYTES
        val root = rom.gbBankAddress(bank, rom.u16le(offset + 1)) ?: return@runCatching null
        parseTownMapPalettes(rom, root, paletteCopyBytes / BYTES_PER_PALETTE)
    }.getOrNullUnlessCancelled()

    private fun parseDynamicPaletteLoaderAt(rom: RomImage, offset: Int): PaletteAuthority? = runCatching {
        if (
            rom.u8(offset) != LOAD_HL_IMMEDIATE || rom.u8(offset + 3) != LOAD_A_ABSOLUTE ||
            rom.u8(offset + 6) != CB_PREFIX || !isBitTestA(rom.u8(offset + 7)) ||
            rom.u8(offset + 8) != JR_Z || branchTarget(offset + 8, rom.u8(offset + 9)) != offset + 13 ||
            rom.u8(offset + 10) != LOAD_HL_IMMEDIATE || rom.u8(offset + 13) != LOAD_DE_IMMEDIATE ||
            rom.u16le(offset + 14) !in WRAM_PALETTE_DESTINATIONS ||
            rom.u8(offset + 16) != LOAD_BC_IMMEDIATE || rom.u8(offset + 19) != LOAD_A_IMMEDIATE ||
            rom.u8(offset + 21) != CALL
        ) return@runCatching null
        val paletteCopyBytes = rom.u16le(offset + 17)
        if (
            paletteCopyBytes !in MIN_PALETTE_BYTES..MAX_BG_PALETTE_BYTES ||
            paletteCopyBytes % BYTES_PER_PALETTE != 0
        ) return@runCatching null
        val bank = offset / BANK_BYTES
        val firstRoot = rom.gbBankAddress(bank, rom.u16le(offset + 1)) ?: return@runCatching null
        val secondRoot = rom.gbBankAddress(bank, rom.u16le(offset + 11)) ?: return@runCatching null
        if (firstRoot == secondRoot) return@runCatching null
        val paletteCount = paletteCopyBytes / BYTES_PER_PALETTE
        val first = parseTownMapPalettes(rom, firstRoot, paletteCount) ?: return@runCatching null
        val second = parseTownMapPalettes(rom, secondRoot, paletteCount) ?: return@runCatching null
        first.takeIf { it.colors.contentEquals(second.colors) }
    }.getOrNullUnlessCancelled()

    /**
     * A six-to-eight-palette copy must be selected by the third entry of a compiled CGB layout jump
     * table. The map code selects that same entry through `ld b, 2` before its layout call. This
     * joins the otherwise bank-separated palette code to the Town Map consumer without an identity
     * value; the asset join later requires every palette ID named by the compiled palette map.
     */
    private fun provesPaletteJumpTableAuthority(rom: RomImage, paletteCopy: Int, cancellation: ParserCancellationToken): Boolean {
        val bank = paletteCopy / BANK_BYTES
        val localCopy = (paletteCopy % BANK_BYTES) + BANK_BYTES
        val start = bank * BANK_BYTES
        val end = minOf(start + BANK_BYTES, rom.size)
        var table = start
        while (table + PALETTE_LAYOUT_INDEX * 2 + 2 <= end) {
            cancellation.throwIfCancellationRequested()
            val entry = rom.u16le(table + PALETTE_LAYOUT_INDEX * 2)
            if (entry in (localCopy - MAX_PALETTE_ENTRY_PREFIX)..localCopy) {
                val localTable = (table % BANK_BYTES) + BANK_BYTES
                val dispatcherStart = maxOf(start, table - MAX_LAYOUT_DISPATCH_BYTES)
                var cursor = dispatcherStart
                while (cursor + 12 <= table) {
                    cancellation.throwIfCancellationRequested()
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

    private fun hasPaletteSelectionCaller(rom: RomImage, bank: Int, cancellation: ParserCancellationToken): Boolean {
        val start = bank * BANK_BYTES
        val end = minOf(start + BANK_BYTES, rom.size)
        var offset = start
        while (offset + 5 <= end) {
            cancellation.throwIfCancellationRequested()
            if (
                rom.u8(offset) == LOAD_B_IMMEDIATE && rom.u8(offset + 1) == PALETTE_LAYOUT_INDEX &&
                rom.u8(offset + 2) == CALL
            ) return true
            offset++
        }
        return false
    }

    private fun parseTownMapPalettes(
        rom: RomImage,
        offset: Int,
        paletteCount: Int,
    ): PaletteAuthority? = runCatching {
        if (paletteCount !in MIN_PALETTE_COUNT..MAX_PALETTE_COUNT) return@runCatching null
        val colorCount = paletteCount * COLORS_PER_PALETTE
        val byteCount = colorCount * 2
        if (offset + byteCount > rom.size) return@runCatching null
        val colors = ShortArray(colorCount) { rom.u16le(offset + it * 2).toShort() }
        if (colors.any { (it.toInt() and 0xffff) > MAX_BGR555 }) return@runCatching null
        val commonBackgrounds = (0 until paletteCount)
            .groupingBy { colors[it * COLORS_PER_PALETTE] }
            .eachCount()
            .values
            .maxOrNull()
            ?: 0
        if (commonBackgrounds < MIN_PALETTE_COUNT) return@runCatching null
        if ((0 until paletteCount).map { index ->
                (0 until COLORS_PER_PALETTE).map { colors[index * COLORS_PER_PALETTE + it] }
            }.distinct().size < MIN_DISTINCT_PALETTES
        ) return@runCatching null
        PaletteAuthority(paletteCount, colors)
    }.getOrNullUnlessCancelled()

    private fun findBindingChains(
        rom: RomImage,
        requiredMaps: Set<Int>,
        codec: PokemonTextCodec?,
        cancellation: ParserCancellationToken,
    ): BindingSearch {
        val groupRoots = findMapGroupRoots(rom, requiredMaps, cancellation)
        val landmarkAuthorities = findLandmarkAuthorities(rom, cancellation)
        val classifiers = findRegionClassifiers(rom, cancellation)
        if (System.getenv("DUALDEX_MAP_TRACE") == "1") {
            println(
                "world-map-trace gen2 bindings " +
                    "headers=${groupRoots.map { "${it.bank}:${it.tableOffset}" }} " +
                    "landmarks=${landmarkAuthorities.map { "${it.bank}:${it.tableOffset}" }} " +
                    "classifiers=$classifiers",
            )
        }
        val chains = buildList {
            for (groupRoot in groupRoots) {
                cancellation.throwIfCancellationRequested()
                for (landmarks in landmarkAuthorities) {
                    cancellation.throwIfCancellationRequested()
                    for (classifier in classifiers) {
                        cancellation.throwIfCancellationRequested()
                        buildBindings(rom, requiredMaps, groupRoot, landmarks, classifier, codec, cancellation)?.let(::add)
                    }
                }
            }
        }.groupBy { chain ->
            chain.johto.joinToString("|") { "${it.id}:${it.x}:${it.y}:${it.baseAreaIds}" } + "/" +
                chain.kanto.joinToString("|") { "${it.id}:${it.x}:${it.y}:${it.baseAreaIds}" }
        }.values.map { equivalent ->
            cancellation.throwIfCancellationRequested()
            // Numeric agreement does not prove text agreement between different compiled roots.
            fun consensus(select: (BindingChain) -> List<Landmark>): List<Landmark> =
                select(equivalent.first()).mapIndexed { index, landmark ->
                    cancellation.throwIfCancellationRequested()
                    landmark.copy(name = equivalent.map { select(it)[index].name }.distinct().singleOrNull())
                }
            BindingChain(
                johto = consensus { it.johto },
                kanto = consensus { it.kanto },
                nameSources = equivalent.flatMap { it.nameSources }.distinct(),
            )
        }
        return BindingSearch(chains, groupRoots.size, landmarkAuthorities.size, classifiers.size)
    }

    internal fun findMapGroupRoots(
        rom: RomImage,
        requiredMaps: Set<Int>,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): List<MapGroupAuthority> = buildList {
        var offset = 0
        while (offset + MAP_POINTER_CONSUMER_BYTES <= minOf(BANK_BYTES, rom.size)) {
            cancellation.throwIfCancellationRequested()
            val authority = parseMapPointerConsumerAt(rom, offset, requiredMaps, cancellation)
            if (authority != null) add(authority)
            offset++
        }
    }.distinctBy { it.bank to it.tableOffset }

    private fun parseMapPointerConsumerAt(
        rom: RomImage,
        offset: Int,
        requiredMaps: Set<Int>,
        cancellation: ParserCancellationToken,
    ): MapGroupAuthority? = runCatching {
        val prefix = intArrayOf(0xc5, 0x05, 0x48, 0x06, 0x00, 0x21)
        if (!prefix.indices.all { rom.u8(offset + it) == prefix[it] }) return@runCatching null
        val terminal = rom.u8(offset + 19) == JUMP ||
            (rom.u8(offset + 19) == CALL && rom.u8(offset + 22) == RETURN)
        if (
            rom.u8(offset + 8) != 0x09 || rom.u8(offset + 9) != 0x09 ||
            rom.u8(offset + 10) != 0x2a || rom.u8(offset + 11) != 0x66 || rom.u8(offset + 12) != 0x6f ||
            rom.u8(offset + 13) != 0xc1 || rom.u8(offset + 14) != 0x0d ||
            rom.u8(offset + 15) != 0x06 || rom.u8(offset + 16) != 0x00 ||
            rom.u8(offset + 17) != LOAD_A_IMMEDIATE || rom.u8(offset + 18) != MAP_HEADER_BYTES ||
            !terminal
        ) return@runCatching null
        val bank = findMapDataBank(rom, offset, cancellation) ?: return@runCatching null
        val table = rom.gbBankAddress(bank, rom.u16le(offset + 6)) ?: return@runCatching null
        val invalidMaps = requiredMaps.filterNot { base ->
            cancellation.throwIfCancellationRequested()
            val group = base ushr 8
            val map = base and 0xff
            val groupPointer = rom.u16le(table + (group - 1) * 2)
            val root = rom.gbBankAddress(bank, groupPointer) ?: return@filterNot true
            root + (map - 1) * MAP_HEADER_BYTES + MAP_HEADER_BYTES <= rom.size
        }
        if (System.getenv("DUALDEX_MAP_TRACE") == "1") {
            println(
                "world-map-trace gen2 map-headers bank=$bank table=$table " +
                    "required=${requiredMaps.size} invalid=$invalidMaps",
            )
        }
        if (invalidMaps.isNotEmpty()) return@runCatching null
        MapGroupAuthority(bank, table)
    }.getOrNullUnlessCancelled()

    private fun findMapDataBank(rom: RomImage, consumerOffset: Int, cancellation: ParserCancellationToken): Int? {
        val banks = buildSet {
            var offset = 0
            while (offset + MAP_HEADER_MEMBER_CONSUMER_BYTES <= minOf(BANK_BYTES, rom.size)) {
                cancellation.throwIfCancellationRequested()
                if (
                    rom.u8(offset) == LOAD_A_HIGH &&
                    rom.u8(offset + 2) == PUSH_AF &&
                    rom.u8(offset + 3) == LOAD_A_IMMEDIATE &&
                    rom.u8(offset + 5) == RST_BANKSWITCH &&
                    rom.u8(offset + 6) == CALL &&
                    rom.u16le(offset + 7) == consumerOffset &&
                    rom.u8(offset + 9) == ADD_HL_DE &&
                    rom.u8(offset + 10) == LOAD_C_HL &&
                    rom.u8(offset + 11) == INC_HL &&
                    rom.u8(offset + 12) == LOAD_B_HL &&
                    rom.u8(offset + 13) == POP_AF &&
                    rom.u8(offset + 14) == RST_BANKSWITCH &&
                    rom.u8(offset + 15) == RETURN
                ) add(rom.u8(offset + 4))
                offset++
            }
        }
        return banks.singleOrNull()
    }

    private fun findLandmarkAuthorities(rom: RomImage, cancellation: ParserCancellationToken): List<LandmarkAuthority> = buildList {
        var offset = BANK_BYTES
        while (offset + LANDMARK_CONSUMER_BYTES <= rom.size) {
            cancellation.throwIfCancellationRequested()
            parseStandardLandmarkConsumerAt(rom, offset)?.let(::add)
            parseExtendedLandmarkConsumerAt(rom, offset, cancellation)?.let(::add)
            offset++
        }
    }.distinctBy { Triple(it.tableOffset, it.recordSize, it.namePointerOffset) }

    private fun parseStandardLandmarkConsumerAt(rom: RomImage, offset: Int): LandmarkAuthority? = runCatching {
        val prefix = intArrayOf(0xe5, 0x6b, 0x26, 0x00, 0x29, 0x29, 0x11)
        if (!prefix.indices.all { rom.u8(offset + it) == prefix[it] }) return@runCatching null
        val bank = offset / BANK_BYTES
        val table = rom.gbBankAddress(bank, rom.u16le(offset + 7)) ?: return@runCatching null
        if (
            rom.u8(offset + 9) != 0x19 || rom.u8(offset + 10) != 0x2a ||
            rom.u8(offset + 11) != 0x5f || rom.u8(offset + 12) != 0x56 ||
            rom.u8(offset + 13) != 0xe1 || rom.u8(offset + 14) != RETURN
        ) return@runCatching null
        LandmarkAuthority(bank, table, STANDARD_LANDMARK_BYTES, STANDARD_LANDMARK_NAME_FIELD)
    }.getOrNullUnlessCancelled()

    private fun parseExtendedLandmarkConsumerAt(rom: RomImage, offset: Int, cancellation: ParserCancellationToken): LandmarkAuthority? = runCatching {
        if (
            rom.u8(offset) != LOAD_A_E || rom.u8(offset + 1) != AND_IMMEDIATE ||
            rom.u8(offset + 2) !in MIN_LANDMARK_MASK..MAX_LANDMARK_MASK ||
            rom.u8(offset + 3) != LOAD_E_A || rom.u8(offset + 4) != PUSH_HL ||
            rom.u8(offset + 5) != LOAD_L_E || rom.u8(offset + 6) != LOAD_H_IMMEDIATE ||
            rom.u8(offset + 7) != 0 || rom.u8(offset + 8) != ADD_HL_HL ||
            rom.u8(offset + 9) != ADD_HL_HL || rom.u8(offset + 10) != LOAD_D_IMMEDIATE ||
            rom.u8(offset + 11) != 0 || rom.u8(offset + 12) != ADD_HL_DE ||
            rom.u8(offset + 13) != LOAD_DE_IMMEDIATE || rom.u8(offset + 16) != ADD_HL_DE ||
            rom.u8(offset + 17) != LOAD_A_HL_INCREMENT || rom.u8(offset + 18) != LOAD_E_A ||
            rom.u8(offset + 19) != LOAD_D_HL || rom.u8(offset + 20) != POP_HL ||
            rom.u8(offset + 21) != RETURN
        ) return@runCatching null
        val bank = offset / BANK_BYTES
        val table = rom.gbBankAddress(bank, rom.u16le(offset + 14)) ?: return@runCatching null
        val nameRoot = table + EXTENDED_LANDMARK_NAME_FIELD
        if (!hasExtendedLandmarkNameConsumer(rom, bank, nameRoot, cancellation)) return@runCatching null
        LandmarkAuthority(bank, table, EXTENDED_LANDMARK_BYTES, EXTENDED_LANDMARK_NAME_FIELD)
    }.getOrNullUnlessCancelled()

    private fun hasExtendedLandmarkNameConsumer(rom: RomImage, bank: Int, nameRoot: Int, cancellation: ParserCancellationToken): Boolean {
        val start = bank * BANK_BYTES
        val end = minOf(start + BANK_BYTES, rom.size)
        var offset = start
        while (offset + EXTENDED_LANDMARK_NAME_CONSUMER_BYTES <= end) {
            cancellation.throwIfCancellationRequested()
            if (
                rom.u8(offset) == PUSH_HL && rom.u8(offset + 1) == PUSH_DE &&
                rom.u8(offset + 2) == PUSH_BC && rom.u8(offset + 3) == LOAD_L_E &&
                rom.u8(offset + 4) == LOAD_H_IMMEDIATE && rom.u8(offset + 5) == 0 &&
                rom.u8(offset + 6) == ADD_HL_HL && rom.u8(offset + 7) == ADD_HL_HL &&
                rom.u8(offset + 8) == LOAD_D_IMMEDIATE && rom.u8(offset + 9) == 0 &&
                rom.u8(offset + 10) == ADD_HL_DE && rom.u8(offset + 11) == LOAD_DE_IMMEDIATE &&
                rom.gbBankAddress(bank, rom.u16le(offset + 12)) == nameRoot &&
                rom.u8(offset + 14) == ADD_HL_DE && rom.u8(offset + 15) == LOAD_A_HL_INCREMENT &&
                rom.u8(offset + 16) == LOAD_H_HL && rom.u8(offset + 17) == LOAD_L_A
            ) return true
            offset++
        }
        return false
    }

    private fun findRegionClassifiers(rom: RomImage, cancellation: ParserCancellationToken): List<RegionClassifier> {
        val detailed = findDetailedRegionClassifiers(rom, cancellation)
        if (detailed.isNotEmpty()) return detailed
        val withShip = findOneThresholdRegionClassifiers(rom, cancellation)
        return if (withShip.isNotEmpty()) withShip else findDirectThresholdRegionClassifiers(rom, cancellation)
    }

    private fun findDetailedRegionClassifiers(rom: RomImage, cancellation: ParserCancellationToken): List<RegionClassifier> = buildList {
        var offset = 0
        while (offset + REGION_CLASSIFIER_BYTES <= rom.size) {
            cancellation.throwIfCancellationRequested()
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
    }.getOrNullUnlessCancelled()

    /**
     * Some source-compatible Gen II builds retain the complete `IsInJohto` consumer but omit a
     * valid Victory Road exception in the far region helper. Its one-threshold form still proves
     * the same current-map call, FAST_SHIP path, dynamic-special retry, threshold, and common
     * Johto/Kanto returns. Prefer the richer two-threshold authority whenever it is complete.
     */
    private fun findOneThresholdRegionClassifiers(rom: RomImage, cancellation: ParserCancellationToken): List<RegionClassifier> = buildList {
        var offset = 0
        while (offset + REGION_CLASSIFIER_BYTES <= rom.size) {
            cancellation.throwIfCancellationRequested()
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
        if (threshold <= SPECIAL_LANDMARK || threshold > common.ship) return@runCatching null
        RegionClassifier(threshold, null, common.ship)
    }.getOrNullUnlessCancelled()

    private fun findDirectThresholdRegionClassifiers(rom: RomImage, cancellation: ParserCancellationToken): List<RegionClassifier> = buildList {
        var offset = 0
        while (offset + DIRECT_REGION_CLASSIFIER_BYTES <= rom.size) {
            cancellation.throwIfCancellationRequested()
            parseDirectThresholdRegionClassifierAt(rom, offset)?.let(::add)
            offset++
        }
    }.distinct()

    private fun parseDirectThresholdRegionClassifierAt(rom: RomImage, offset: Int): RegionClassifier? = runCatching {
        val currentCall = parseMapLocationCall(rom, offset) ?: return@runCatching null
        val special = offset + MAP_LOCATION_CALL_BYTES
        if (
            rom.u8(special) != COMPARE_IMMEDIATE || rom.u8(special + 1) != SPECIAL_LANDMARK ||
            rom.u8(special + 2) != JR_NZ
        ) return@runCatching null
        val backup = special + 4
        if (parseMapLocationCall(rom, backup) != currentCall) return@runCatching null
        val check = backup + MAP_LOCATION_CALL_BYTES
        if (
            branchTarget(special + 2, rom.u8(special + 3)) != check ||
            rom.u8(check) != COMPARE_IMMEDIATE || rom.u8(check + 2) != JR_NC
        ) return@runCatching null
        val primary = check + 4
        val secondary = primary + 2
        if (
            rom.u8(primary) != XOR_A || rom.u8(primary + 1) != RETURN ||
            rom.u8(secondary) != LOAD_A_IMMEDIATE || rom.u8(secondary + 1) != KANTO_REGION ||
            rom.u8(secondary + 2) != RETURN ||
            branchTarget(check + 2, rom.u8(check + 3)) != secondary
        ) return@runCatching null
        val threshold = rom.u8(check + 1)
        if (threshold <= SPECIAL_LANDMARK) return@runCatching null
        RegionClassifier(threshold, null, null)
    }.getOrNullUnlessCancelled()

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
    }.getOrNullUnlessCancelled()

    private fun parseMapLocationCall(rom: RomImage, offset: Int): Int? = runCatching {
        if (
            rom.u8(offset) != LOAD_A_ABSOLUTE || rom.u8(offset + 3) != LOAD_B_A ||
            rom.u8(offset + 4) != LOAD_A_ABSOLUTE || rom.u8(offset + 7) != LOAD_C_A ||
            rom.u8(offset + 8) != CALL
        ) return@runCatching null
        rom.u16le(offset + 9)
    }.getOrNullUnlessCancelled()

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
        codec: PokemonTextCodec?,
        cancellation: ParserCancellationToken,
    ): BindingChain? = runCatching {
        val grouped = linkedMapOf<Int, MutableSet<Int>>()
        for (base in requiredMaps) {
            cancellation.throwIfCancellationRequested()
            val group = base ushr 8
            val map = base and 0xff
            val pointer = rom.u16le(groups.tableOffset + (group - 1) * 2)
            val root = rom.gbBankAddress(groups.bank, pointer) ?: return@runCatching null
            val row = root + (map - 1) * MAP_HEADER_BYTES
            val landmark = rom.u8(row + MAP_LOCATION_FIELD)
            if (landmark == SPECIAL_LANDMARK) return@runCatching null
            if (classifier.ship != null && landmark > classifier.ship) return@runCatching null
            grouped.getOrPut(landmark) { linkedSetOf() } += base
        }
        val landmarkIds = grouped.keys + setOf(FIRST_STATIC_LANDMARK, classifier.kanto)
        val nameEncoding = codec?.let {
            resolveLandmarkNameEncoding(rom, landmarks, landmarkIds, it, cancellation)
        }
        if (
            decodeLandmark(rom, landmarks, FIRST_STATIC_LANDMARK, emptySet(), nameEncoding, codec, cancellation) == null ||
            decodeLandmark(rom, landmarks, classifier.kanto, emptySet(), nameEncoding, codec, cancellation) == null
        ) return@runCatching null
        val decoded = grouped.map { (id, baseIds) ->
            cancellation.throwIfCancellationRequested()
            decodeLandmark(rom, landmarks, id, baseIds, nameEncoding, codec, cancellation) ?: return@runCatching null
        }
        BindingChain(
            johto = decoded.filter { landmark ->
                landmark.id < classifier.kanto ||
                    landmark.id == classifier.ship ||
                    (classifier.johtoReturn != null && landmark.id >= classifier.johtoReturn)
            },
            kanto = decoded.filter { landmark ->
                landmark.id >= classifier.kanto &&
                    landmark.id != classifier.ship &&
                    (classifier.johtoReturn == null || landmark.id < classifier.johtoReturn)
            },
            nameSources = listOf(LandmarkNameSource(landmarks, nameEncoding)),
        ).takeIf { decoded.isNotEmpty() }
    }.getOrNullUnlessCancelled()

    private fun resolveLandmarkNameEncoding(
        rom: RomImage,
        landmarks: LandmarkAuthority,
        ids: Set<Int>,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
    ): Gen2LandmarkNameEncoding? {
        val candidates = Gen2LandmarkNameEncoding.entries.mapNotNull { encoding ->
            cancellation.throwIfCancellationRequested()
            val names = mutableListOf<String>()
            for (id in ids.sorted()) {
                cancellation.throwIfCancellationRequested()
                val name = decodeLandmarkName(rom, landmarks, id, encoding, codec, cancellation)
                if (name == null) {
                    traceBindingFailure("$encoding landmark $id has undecodable name")
                    return@mapNotNull null
                }
                names += name
            }
            encoding to names
        }
        if (candidates.size == 1) return candidates.single().first
        if (candidates.size != 2) return null
        if (candidates[0].second == candidates[1].second) return Gen2LandmarkNameEncoding.STANDARD
        val scores = candidates.map { candidate -> candidate.second.sumOf { name -> name.count(Char::isDigit) } }
        val best = scores.maxOrNull() ?: return null
        return candidates.singleOrNull { candidate ->
            scores[candidates.indexOf(candidate)] == best && best > 0
        }?.first
    }

    private fun decodeLandmarkName(
        rom: RomImage,
        landmarks: LandmarkAuthority,
        id: Int,
        encoding: Gen2LandmarkNameEncoding,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
    ): String? = runCatching {
        val row = landmarks.tableOffset + id * landmarks.recordSize
        if (row + landmarks.recordSize > rom.size) return@runCatching null
        val namePointer = rom.u16le(row + landmarks.namePointerOffset)
        val nameRoot = rom.gbBankAddress(landmarks.bank, namePointer) ?: return@runCatching null
        Gen2LandmarkNameCodec.decode(
            rom.slice(nameRoot, minOf(MAX_NAME_BYTES, rom.size - nameRoot)),
            encoding,
            codec,
            cancellation,
        )
    }.getOrNullUnlessCancelled()

    private fun decodeLandmark(
        rom: RomImage,
        landmarks: LandmarkAuthority,
        id: Int,
        baseAreaIds: Set<Int>,
        nameEncoding: Gen2LandmarkNameEncoding?,
        codec: PokemonTextCodec?,
        cancellation: ParserCancellationToken,
    ): Landmark? = runCatching {
        val row = landmarks.tableOffset + id * landmarks.recordSize
        if (row + landmarks.recordSize > rom.size) {
            traceBindingFailure("landmark $id row exceeds ROM")
            return@runCatching null
        }
        val rawX = rom.u8(row)
        val rawY = rom.u8(row + 1)
        if (rawX < COORDINATE_X_BIAS || rawY < COORDINATE_Y_BIAS) {
            traceBindingFailure("landmark $id has biased coordinate $rawX,$rawY")
            return@runCatching null
        }
        val x = (rawX - COORDINATE_X_BIAS) / TILE_EDGE
        val y = (rawY - COORDINATE_Y_BIAS) / TILE_EDGE
        if (x !in 0 until GRID_WIDTH || y !in 0 until GRID_HEIGHT) {
            traceBindingFailure("landmark $id has off-map cell $x,$y")
            return@runCatching null
        }
        // The compiled record still requires a valid pointer, even when its localized payload fails.
        val namePointer = rom.u16le(row + landmarks.namePointerOffset)
        if (rom.gbBankAddress(landmarks.bank, namePointer) == null) return@runCatching null
        val name = if (codec != null && nameEncoding != null) {
            decodeLandmarkName(rom, landmarks, id, nameEncoding, codec, cancellation)
        } else {
            null
        }
        Landmark(id, x, y, name, baseAreaIds.toSet())
    }.getOrNullUnlessCancelled()

    private fun <T> Result<T>.getOrNullUnlessCancelled(): T? = getOrElse { failure ->
        if (failure is CancellationException) throw failure
        null
    }

    private fun traceBindingFailure(reason: String) {
        if (System.getenv("DUALDEX_MAP_TRACE") == "1") {
            println("world-map-trace gen2 binding-reject $reason")
        }
    }

    private fun compose(
        tiles: ByteArray,
        plane: ByteArray,
        paletteTileLimit: Int,
        paletteMap: ByteArray,
        palettes: ShortArray,
        cancellation: ParserCancellationToken,
    ): RgbaSprite {
        require(paletteMap.size * 2 == paletteTileLimit)
        val pixels = IntArray(PIXEL_WIDTH * PIXEL_HEIGHT)
        repeat(GRID_HEIGHT) { tileY ->
            cancellation.throwIfCancellationRequested()
            repeat(GRID_WIDTH) { tileX ->
                val tile = plane[tileY * GRID_WIDTH + tileX].toInt() and 0xff
                val palette = if (tile < paletteTileLimit) {
                    val packedPalette = paletteMap[tile / 2].toInt() and 0xff
                    if (tile and 1 == 0) packedPalette and 0x0f else packedPalette ushr 4
                } else {
                    DEFAULT_PALETTE
                }
                val tileOffset = tile * BYTES_PER_TILE
                repeat(TILE_EDGE) { row ->
                    val rowOffset = tileOffset + row * 2
                    val low = if (rowOffset + 1 < tiles.size) tiles[rowOffset].toInt() and 0xff else 0
                    val high = if (rowOffset + 1 < tiles.size) tiles[rowOffset + 1].toInt() and 0xff else 0
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

    private data class PaletteMapAuthority(
        val tileLimit: Int,
        val offset: Int,
    )
    private data class ResolvedPaletteMap(
        val bytes: ByteArray,
        val requiredPaletteCount: Int,
    )
    private data class MapAuthority(
        val bank: Int,
        val paletteLookupLimit: Int,
        val johtoMap: ByteArray,
        val kantoMap: ByteArray,
        val additionalMaps: List<ByteArray>,
        val paletteMapOffset: Int,
    ) {
        val allPlanes: List<ByteArray>
            get() = listOf(johtoMap, kantoMap) + additionalMaps
    }
    private data class GraphicsLoader(
        val loaderBank: Int,
        val requestedTileCount: Int,
        val decodedTileCount: Int,
        val tiles: ByteArray,
    )
    private data class PaletteAuthority(val count: Int, val colors: ShortArray)
    private data class AssetChain(
        val requestedTileCount: Int,
        val decodedTileCount: Int,
        val paletteTileLimit: Int,
        val paletteCount: Int,
        val tiles: ByteArray,
        val johtoMap: ByteArray,
        val kantoMap: ByteArray,
        val paletteMap: ByteArray,
        val palettes: ShortArray,
    )
    internal data class MapGroupAuthority(val bank: Int, val tableOffset: Int)
    private data class LandmarkAuthority(
        val bank: Int,
        val tableOffset: Int,
        val recordSize: Int,
        val namePointerOffset: Int,
    )
    private data class RegionClassifier(val kanto: Int, val johtoReturn: Int?, val ship: Int?)
    private data class RegionClassifierPrefix(val ship: Int, val shipTarget: Int, val checkOffset: Int)
    private data class Landmark(val id: Int, val x: Int, val y: Int, val name: String?, val baseAreaIds: Set<Int>)
    private data class LandmarkNameSource(val authority: LandmarkAuthority, val encoding: Gen2LandmarkNameEncoding?)
    private data class BindingChain(
        val johto: List<Landmark>,
        val kanto: List<Landmark>,
        val nameSources: List<LandmarkNameSource>,
    )
    private data class BindingSearch(
        val chains: List<BindingChain>,
        val groupRoots: Int,
        val landmarks: Int,
        val classifiers: Int,
    )
    private data class RegionInput(
        val key: String,
        val displayName: String?,
        val map: ByteArray,
        val landmarks: List<Landmark>,
    )

    private const val BANK_BYTES = 0x4000
    private const val TILE_EDGE = 8
    private const val BYTES_PER_TILE = 16
    private const val MIN_TILE_COUNT = 48
    private const val MAX_TILE_COUNT = 128
    private const val GRID_WIDTH = 20
    private const val GRID_HEIGHT = 18
    private const val GRID_AREA = GRID_WIDTH * GRID_HEIGHT
    private const val PLANE_BYTES = GRID_AREA + 1
    private const val PIXEL_WIDTH = GRID_WIDTH * TILE_EDGE
    private const val PIXEL_HEIGHT = GRID_HEIGHT * TILE_EDGE
    private const val MIN_PALETTE_COUNT = 6
    private const val MAX_PALETTE_COUNT = 8
    private const val COLORS_PER_PALETTE = 4
    private const val DEFAULT_PALETTE = 0
    private const val BYTES_PER_PALETTE = COLORS_PER_PALETTE * 2
    private const val MIN_PALETTE_BYTES = MIN_PALETTE_COUNT * BYTES_PER_PALETTE
    private const val MAX_BG_PALETTE_BYTES = MAX_PALETTE_COUNT * BYTES_PER_PALETTE
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
    private const val STANDARD_LANDMARK_BYTES = 4
    private const val STANDARD_LANDMARK_NAME_FIELD = 2
    private const val EXTENDED_LANDMARK_BYTES = 5
    private const val EXTENDED_LANDMARK_NAME_FIELD = 3
    private const val MIN_LANDMARK_MASK = 0x1f
    private const val MAX_LANDMARK_MASK = 0x7f
    private const val SPECIAL_LANDMARK = 0
    private const val FIRST_STATIC_LANDMARK = 1
    private const val MAX_STATIC_LANDMARK = 127
    private const val COORDINATE_X_BIAS = 8
    private const val COORDINATE_Y_BIAS = 16
    private const val MAX_NAME_BYTES = 32
    private const val END_MARKER = 0xff
    private const val MAP_AUTHORITY_BYTES = 24
    private const val MAP_COPY_BYTES = 20
    private const val GUARDED_MAP_COPY_BYTES = 30
    private const val GRAPHICS_LOADER_BYTES = 13
    private const val DYNAMIC_PALETTE_LOADER_BYTES = 23
    private const val MAP_POINTER_CONSUMER_BYTES = 23
    private const val MAP_HEADER_MEMBER_CONSUMER_BYTES = 16
    private const val LANDMARK_CONSUMER_BYTES = 15
    private const val EXTENDED_LANDMARK_NAME_CONSUMER_BYTES = 18
    private const val REGION_CLASSIFIER_BYTES = 40
    private const val DIRECT_REGION_CLASSIFIER_BYTES = 35
    private const val MAP_LOCATION_CALL_BYTES = 11
    private const val LOAD_HL_IMMEDIATE = 0x21
    private const val LOAD_DE_IMMEDIATE = 0x11
    private const val LOAD_BC_IMMEDIATE = 0x01
    private const val LOAD_A_HIGH = 0xf0
    private const val LOAD_A_IMMEDIATE = 0x3e
    private const val LOAD_A_ABSOLUTE = 0xfa
    private const val LOAD_A_E = 0x7b
    private const val LOAD_A_HL_INCREMENT = 0x2a
    private const val LOAD_B_A = 0x47
    private const val LOAD_C_A = 0x4f
    private const val LOAD_D_HL = 0x56
    private const val LOAD_E_A = 0x5f
    private const val LOAD_H_HL = 0x66
    private const val LOAD_L_A = 0x6f
    private const val LOAD_L_E = 0x6b
    private const val LOAD_C_HL = 0x4e
    private const val LOAD_B_HL = 0x46
    private const val LOAD_E_IMMEDIATE = 0x1e
    private const val LOAD_D_IMMEDIATE = 0x16
    private const val LOAD_H_IMMEDIATE = 0x26
    private const val LOAD_B_IMMEDIATE = 0x06
    private const val AND_IMMEDIATE = 0xe6
    private const val COMPARE_IMMEDIATE = 0xfe
    private const val PUSH_BC = 0xc5
    private const val PUSH_DE = 0xd5
    private const val PUSH_HL = 0xe5
    private const val PUSH_AF = 0xf5
    private const val POP_HL = 0xe1
    private const val POP_AF = 0xf1
    private const val ADD_HL_DE = 0x19
    private const val ADD_HL_HL = 0x29
    private const val INC_HL = 0x23
    private const val CALL = 0xcd
    private const val JUMP = 0xc3
    private const val RST_BANKSWITCH = 0xd7
    private const val RETURN = 0xc9
    private const val JR = 0x18
    private const val JR_NZ = 0x20
    private const val JR_Z = 0x28
    private const val JR_C = 0x38
    private const val JR_NC = 0x30
    private const val CB_PREFIX = 0xcb
    private const val BIT_0_A = 0x47
    private const val BIT_7_A = 0x7f
    private const val BIT_OPCODE_STRIDE = 8
    private const val XOR_A = 0xaf
    private const val JOHTO_REGION = 0
    private const val KANTO_REGION = 1
    private val VRAM_TILE_RANGE = 0x8800..0x97ff
    private val WRAM_PALETTE_DESTINATIONS = setOf(0xc200, 0xd000)
}
