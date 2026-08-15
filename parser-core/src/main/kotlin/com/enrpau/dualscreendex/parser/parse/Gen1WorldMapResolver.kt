package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog
import com.enrpau.dualscreendex.parser.catalog.WorldMapCell
import com.enrpau.dualscreendex.parser.catalog.WorldMapLocation
import com.enrpau.dualscreendex.parser.catalog.WorldMapRegion
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

/** Resolves a Gen I Town Map from one structurally complete loader and entry-lookup chain. */
object Gen1WorldMapResolver {
    fun resolve(session: RomAnalysisSession, encounterBaseIds: Set<Int>): WorldMapResolution {
        val requiredMaps = encounterBaseIds.filterTo(sortedSetOf()) { it in 0..MAX_MAP_ID }
        if (requiredMaps.isEmpty()) {
            return WorldMapResolution.Unavailable("encounter-binding", "no encounter-bound Gen I map IDs")
        }
        val chains = findChains(session.rom, requiredMaps)
        if (chains.isEmpty()) {
            return WorldMapResolution.Unavailable(
                "asset-loader",
                "no complete Gen I Town Map loader and entry-table chain passed structural validation",
            )
        }
        if (chains.size != 1) {
            return WorldMapResolution.Ambiguous(
                "asset-loader",
                "${chains.size} complete Gen I Town Map authority chains remained",
            )
        }

        val chain = chains.single()
        val raster = compose(chain.tiles, chain.tilemap)
        val locations = requiredMaps.map { mapId ->
            val entry = chain.entries.getValue(mapId)
            WorldMapLocation(
                key = "map-$mapId",
                displayName = entry.name,
                baseAreaIds = setOf(mapId),
                geometry = listOf(WorldMapCell(entry.x, entry.y, 1, 1)),
            )
        }
        val regionKey = "gen1-kanto"
        val assetKey = "world/$regionKey"
        val catalog = WorldMapCatalog(
            regions = listOf(
                WorldMapRegion(
                    key = regionKey,
                    displayName = "Kanto",
                    pixelWidth = PIXEL_WIDTH,
                    pixelHeight = PIXEL_HEIGHT,
                    gridWidth = GRID_WIDTH,
                    gridHeight = GRID_HEIGHT,
                    imageAssetKey = assetKey,
                    locations = locations,
                ),
            ),
            assets = mapOf(assetKey to raster),
        ).validate()
        return WorldMapResolution.Resolved(
            catalog,
            listOf(
                "validated one 16-tile 2bpp and 360-cell nibble-RLE loader",
                "joined ${locations.size} encounter map IDs through the compiled entry lookup",
            ),
        )
    }

    private fun findChains(rom: RomImage, requiredMaps: Set<Int>): List<TownMapChain> = buildList {
        val bankCount = rom.size / BANK_BYTES
        for (bank in 1 until bankCount) {
            val loaders = findLoaders(rom, bank)
            if (loaders.isEmpty()) continue
            val entryTables = findEntryTables(rom, bank, requiredMaps)
            loaders.forEach { loader ->
                entryTables.forEach { table ->
                    add(TownMapChain(loader.offset, table.offset, loader.tiles, loader.tilemap, table.entries))
                }
            }
        }
    }

    private fun findLoaders(rom: RomImage, bank: Int): List<LoaderAsset> {
        val start = bank * BANK_BYTES
        val end = minOf(start + BANK_BYTES, rom.size)
        return buildList {
            var cursor = start
            while (cursor + FAR_COPY_BYTES <= end) {
                val loader = parseLoaderAt(rom, bank, cursor, end)
                if (loader != null) add(loader)
                cursor++
            }
        }
    }

    private fun parseLoaderAt(rom: RomImage, bank: Int, offset: Int, bankEnd: Int): LoaderAsset? = runCatching {
        if (
            rom.u8(offset) != LOAD_HL_IMMEDIATE ||
            rom.u8(offset + 3) != LOAD_DE_IMMEDIATE ||
            rom.u8(offset + 6) != LOAD_BC_IMMEDIATE ||
            rom.u16le(offset + 7) != TILE_BYTES ||
            rom.u8(offset + 9) != LOAD_A_IMMEDIATE ||
            rom.u8(offset + 11) != CALL
        ) return@runCatching null

        val destination = rom.u16le(offset + 4)
        if (destination !in VRAM_TILE_RANGE || (destination - VRAM_TILE_ORIGIN) % TILE_BYTES_PER_TILE != 0) {
            return@runCatching null
        }
        val tileBase = (destination - VRAM_TILE_ORIGIN) / TILE_BYTES_PER_TILE
        if (tileBase !in 0..0xff - TILE_COUNT) return@runCatching null
        val source = rom.gbBankAddress(rom.u8(offset + 10), rom.u16le(offset + 1))
            ?: return@runCatching null
        if (source + TILE_BYTES > rom.size) return@runCatching null

        val windowEnd = minOf(offset + LOADER_WINDOW_BYTES, bankEnd)
        val maps = buildList {
            var cursor = offset + FAR_COPY_BYTES
            while (cursor + 3 < windowEnd) {
                if (rom.u8(cursor) == LOAD_DE_IMMEDIATE) {
                    val rle = rom.gbBankAddress(bank, rom.u16le(cursor + 1))
                    if (rle != null && provesRleLoop(rom, cursor + 3, windowEnd, tileBase)) {
                        decodeRle(rom, rle)?.let(::add)
                    }
                }
                cursor++
            }
        }.distinctBy(ByteArray::contentHashCode)
        if (maps.size != 1) return@runCatching null
        LoaderAsset(offset, rom.slice(source, TILE_BYTES), maps.single())
    }.getOrNull()

    private fun provesRleLoop(rom: RomImage, start: Int, end: Int, tileBase: Int): Boolean {
        var sawRead = false
        var sawZeroTest = false
        var sawRunNibble = false
        var sawSwap = false
        var sawTileBase = false
        var sawWrite = false
        var cursor = start
        while (cursor < minOf(start + RLE_LOOP_WINDOW_BYTES, end)) {
            when {
                rom.u8(cursor) == 0x1a -> sawRead = true // ld a,[de]
                rom.u8(cursor) in ZERO_TESTS && cursor + 1 < end && rom.u8(cursor + 1) in CONDITIONAL_JUMPS ->
                    sawZeroTest = true
                rom.u8(cursor) == 0xe6 && cursor + 1 < end && rom.u8(cursor + 1) == 0x0f -> sawRunNibble = true
                rom.u8(cursor) == 0xcb && cursor + 1 < end && rom.u8(cursor + 1) == 0x37 -> sawSwap = true
                rom.u8(cursor) == 0xc6 && cursor + 1 < end && rom.u8(cursor + 1) == tileBase -> sawTileBase = true
                rom.u8(cursor) == 0x22 -> sawWrite = true // ld [hli],a
            }
            cursor++
        }
        return sawRead && sawZeroTest && sawRunNibble && sawSwap && sawTileBase && sawWrite
    }

    private fun decodeRle(rom: RomImage, offset: Int): ByteArray? = runCatching {
        val result = ByteArray(GRID_AREA)
        var cursor = offset
        var written = 0
        repeat(MAX_RLE_BYTES) {
            if (cursor >= rom.size) return@runCatching null
            val value = rom.u8(cursor++)
            if (value == 0) return@runCatching result.takeIf { written == GRID_AREA }
            val count = value and 0x0f
            val tile = value ushr 4
            if (count == 0 || tile !in 0 until TILE_COUNT || written + count > GRID_AREA) {
                return@runCatching null
            }
            repeat(count) { result[written++] = tile.toByte() }
        }
        null
    }.getOrNull()

    private fun findEntryTables(rom: RomImage, bank: Int, requiredMaps: Set<Int>): List<EntryTable> {
        val start = bank * BANK_BYTES
        val end = minOf(start + BANK_BYTES, rom.size)
        return buildList {
            var offset = start
            while (offset + LOOKUP_PREFIX_BYTES < end) {
                parseEntryLookupAt(rom, bank, offset, end, requiredMaps)?.let(::add)
                offset++
            }
        }
    }

    private fun parseEntryLookupAt(
        rom: RomImage,
        bank: Int,
        offset: Int,
        bankEnd: Int,
        requiredMaps: Set<Int>,
    ): EntryTable? = runCatching {
        if (
            rom.u8(offset) != COMPARE_IMMEDIATE || rom.u8(offset + 2) != JR_C ||
            rom.u8(offset + 4) != LOAD_BC_IMMEDIATE || rom.u16le(offset + 5) != INTERNAL_ENTRY_BYTES ||
            rom.u8(offset + 7) != LOAD_HL_IMMEDIATE
        ) return@runCatching null
        val threshold = rom.u8(offset + 1)
        if (threshold !in MIN_EXTERNAL_MAPS..MAX_MAP_ID) return@runCatching null
        val branchTarget = offset + 4 + rom.u8(offset + 3).toByte().toInt()
        if (branchTarget !in offset + LOOKUP_PREFIX_BYTES until minOf(offset + LOOKUP_WINDOW_BYTES, bankEnd)) {
            return@runCatching null
        }
        if (rom.u8(branchTarget) != LOAD_HL_IMMEDIATE) return@runCatching null
        val internal = rom.gbBankAddress(bank, rom.u16le(offset + 8)) ?: return@runCatching null
        val external = rom.gbBankAddress(bank, rom.u16le(branchTarget + 1)) ?: return@runCatching null
        val entries = parseEntryTables(rom, bank, threshold, external, internal, requiredMaps)
            ?: return@runCatching null
        EntryTable(offset, entries)
    }.getOrNull()

    private fun parseEntryTables(
        rom: RomImage,
        bank: Int,
        threshold: Int,
        external: Int,
        internal: Int,
        requiredMaps: Set<Int>,
    ): Map<Int, MapEntry>? = runCatching {
        val externalEntries = List(threshold) { mapId ->
            parseEntry(rom, bank, external + mapId * EXTERNAL_ENTRY_BYTES) ?: return@runCatching null
        }
        val internalEntries = mutableListOf<InternalEntry>()
        var cursor = internal
        // The compiled lookup accepts the first greater limit. Duplicate or decreasing rows are
        // unreachable, but do not make the remaining lookup nondeterministic.
        repeat(MAX_INTERNAL_GROUPS) {
            if (cursor >= rom.size) return@runCatching null
            val limit = rom.u8(cursor)
            if (limit == END_MARKER) return@repeat
            val entry = parseEntry(rom, bank, cursor + 1) ?: return@runCatching null
            internalEntries += InternalEntry(limit, entry)
            cursor += INTERNAL_ENTRY_BYTES
        }
        if (cursor >= rom.size || rom.u8(cursor) != END_MARKER || internalEntries.isEmpty()) {
            return@runCatching null
        }
        val resolved = linkedMapOf<Int, MapEntry>()
        for (mapId in requiredMaps) {
            val entry = if (mapId < threshold) {
                externalEntries[mapId]
            } else {
                internalEntries.firstOrNull { mapId < it.limit }?.entry ?: return@runCatching null
            }
            resolved[mapId] = entry
        }
        resolved
    }.getOrNull()

    private fun parseEntry(rom: RomImage, bank: Int, offset: Int): MapEntry? = runCatching {
        if (offset + EXTERNAL_ENTRY_BYTES > rom.size) return@runCatching null
        val packed = rom.u8(offset)
        val x = packed and 0x0f
        val y = packed ushr 4
        if (x !in 0 until GRID_WIDTH || y !in 0 until GRID_HEIGHT) return@runCatching null
        val nameOffset = rom.gbBankAddress(bank, rom.u16le(offset + 1)) ?: return@runCatching null
        val name = decodeTownMapName(rom, nameOffset) ?: return@runCatching null
        MapEntry(x, y, name)
    }.getOrNull()

    private fun decodeTownMapName(rom: RomImage, offset: Int): String? = runCatching {
        val output = StringBuilder()
        var valid = 0
        var content = 0
        var terminated = false
        val available = minOf(MAX_NAME_BYTES, rom.size - offset)
        for (index in 0 until available) {
            val value = rom.u8(offset + index)
            if (value == PokemonTextCodec.gbEnglish.terminator) {
                terminated = true
                break
            }
            content++
            val token = when (value) {
                GB_LINE_FEED -> " "
                GB_POKEMON_ABBREVIATION -> "PKMN"
                GB_POKE_PREFIX -> "POKé"
                else -> PokemonTextCodec.gbEnglish.decodeByte(value)?.toString()
            }
            if (token != null) {
                output.append(token)
                valid++
            }
        }
        val text = output.toString().replace(WHITESPACE, " ").trim()
        text.takeIf {
            terminated && content > 0 && valid.toDouble() / content >= MIN_TEXT_RATIO && it.isNotBlank()
        }
    }.getOrNull()

    private fun compose(tiles: ByteArray, tilemap: ByteArray): RgbaSprite {
        val pixels = IntArray(PIXEL_WIDTH * PIXEL_HEIGHT)
        repeat(GRID_HEIGHT) { tileY ->
            repeat(GRID_WIDTH) { tileX ->
                val tile = tilemap[tileY * GRID_WIDTH + tileX].toInt() and 0xff
                repeat(TILE_EDGE) { row ->
                    val low = tiles[tile * TILE_BYTES_PER_TILE + row * 2].toInt() and 0xff
                    val high = tiles[tile * TILE_BYTES_PER_TILE + row * 2 + 1].toInt() and 0xff
                    repeat(TILE_EDGE) { column ->
                        val bit = 7 - column
                        val color = ((low ushr bit) and 1) or (((high ushr bit) and 1) shl 1)
                        val x = tileX * TILE_EDGE + column
                        val y = tileY * TILE_EDGE + row
                        pixels[y * PIXEL_WIDTH + x] = DMG_PALETTE[color]
                    }
                }
            }
        }
        return RgbaSprite(PIXEL_WIDTH, PIXEL_HEIGHT, pixels)
    }

    private data class LoaderAsset(val offset: Int, val tiles: ByteArray, val tilemap: ByteArray)
    private data class EntryTable(val offset: Int, val entries: Map<Int, MapEntry>)
    private data class MapEntry(val x: Int, val y: Int, val name: String)
    private data class InternalEntry(val limit: Int, val entry: MapEntry)
    private data class TownMapChain(
        val loaderOffset: Int,
        val tableOffset: Int,
        val tiles: ByteArray,
        val tilemap: ByteArray,
        val entries: Map<Int, MapEntry>,
    )

    private const val BANK_BYTES = 0x4000
    private const val TILE_EDGE = 8
    private const val TILE_COUNT = 16
    private const val TILE_BYTES_PER_TILE = 16
    private const val TILE_BYTES = TILE_COUNT * TILE_BYTES_PER_TILE
    private const val GRID_WIDTH = 20
    private const val GRID_HEIGHT = 18
    private const val GRID_AREA = GRID_WIDTH * GRID_HEIGHT
    private const val PIXEL_WIDTH = GRID_WIDTH * TILE_EDGE
    private const val PIXEL_HEIGHT = GRID_HEIGHT * TILE_EDGE
    private const val MAX_RLE_BYTES = GRID_AREA + 1
    private const val FAR_COPY_BYTES = 14
    private const val LOADER_WINDOW_BYTES = 160
    private const val RLE_LOOP_WINDOW_BYTES = 72
    private const val LOOKUP_PREFIX_BYTES = 10
    private const val LOOKUP_WINDOW_BYTES = 96
    private const val MIN_EXTERNAL_MAPS = 16
    private const val MAX_MAP_ID = 0xff
    private const val MAX_INTERNAL_GROUPS = 256
    private const val EXTERNAL_ENTRY_BYTES = 3
    private const val INTERNAL_ENTRY_BYTES = 4
    private const val END_MARKER = 0xff
    private const val MAX_NAME_BYTES = 32
    private const val MIN_TEXT_RATIO = 0.85
    private const val GB_LINE_FEED = 0x1f
    private const val GB_POKEMON_ABBREVIATION = 0x4a
    private const val GB_POKE_PREFIX = 0x54
    private val WHITESPACE = Regex("\\s+")
    private const val VRAM_TILE_ORIGIN = 0x9000
    private val VRAM_TILE_RANGE = 0x9000..0x97f0
    private const val LOAD_HL_IMMEDIATE = 0x21
    private const val LOAD_DE_IMMEDIATE = 0x11
    private const val LOAD_BC_IMMEDIATE = 0x01
    private const val LOAD_A_IMMEDIATE = 0x3e
    private const val CALL = 0xcd
    private const val COMPARE_IMMEDIATE = 0xfe
    private const val JR_C = 0x38
    private val CONDITIONAL_JUMPS = setOf(0x20, 0x28, 0x30, 0x38)
    private val ZERO_TESTS = setOf(0xa7, 0xb7)
    private val DMG_PALETTE = intArrayOf(
        0xffffffff.toInt(),
        0xffaaaaaa.toInt(),
        0xff555555.toInt(),
        0xff000000.toInt(),
    )
}
