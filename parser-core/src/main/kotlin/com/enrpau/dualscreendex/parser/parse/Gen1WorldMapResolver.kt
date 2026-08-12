package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog
import com.enrpau.dualscreendex.parser.catalog.WorldMapCell
import com.enrpau.dualscreendex.parser.catalog.WorldMapLocation
import com.enrpau.dualscreendex.parser.catalog.WorldMapRegion
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.sprite.TileRenderer
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

sealed interface Gen1WorldMapResolution {
    data class Resolved(val catalog: WorldMapCatalog, val reasons: List<String>) : Gen1WorldMapResolution
    data class Unavailable(val reason: String) : Gen1WorldMapResolution
    data class Ambiguous(val reason: String) : Gen1WorldMapResolution
}

/** Resolves the Gen I Town Map only from compiled loader, RLE, graphics, and entry-table evidence. */
object Gen1WorldMapResolver {
    fun resolve(session: RomAnalysisSession, encounterBaseIds: Set<Int>): Gen1WorldMapResolution {
        val requiredMaps = encounterBaseIds.filter { it in 0..MAX_MAP_ID }.toSet()
        if (requiredMaps.isEmpty()) return Gen1WorldMapResolution.Unavailable("no encounter-bound Gen I maps")
        val candidates = findChains(session.rom, requiredMaps)
        if (candidates.isEmpty()) {
            return Gen1WorldMapResolution.Unavailable("compiled Town Map loader/table chain was not found")
        }
        if (candidates.size != 1) {
            return Gen1WorldMapResolution.Ambiguous("${candidates.size} authoritative Gen I Town Map chains remained")
        }
        val chain = candidates.single()
        val tilemap = chain.tilemap.map { (it - TILE_BASE).toByte() }.toByteArray()
        val indexed = TileRenderer.gameBoy2BppTilemap(chain.tiles, tilemap, SCREEN_WIDTH, SCREEN_HEIGHT)
        val raster = TileRenderer.applyArgbPalette(indexed, DMG_PALETTE)
        val locations = requiredMaps.sorted().map { mapId ->
            val entry = chain.entries.getValue(mapId)
            WorldMapLocation(
                key = "map-$mapId",
                displayName = entry.name,
                baseAreaIds = setOf(mapId),
                geometry = listOf(WorldMapCell(entry.x, entry.y, 1, 1)),
            )
        }
        val assetKey = "world/gen1-region-0"
        return Gen1WorldMapResolution.Resolved(
            WorldMapCatalog(
                regions = listOf(
                    WorldMapRegion(
                        key = "region-0",
                        displayName = null,
                        pixelWidth = SCREEN_WIDTH * TILE_EDGE,
                        pixelHeight = SCREEN_HEIGHT * TILE_EDGE,
                        gridWidth = SCREEN_WIDTH,
                        gridHeight = SCREEN_HEIGHT,
                        imageAssetKey = assetKey,
                        locations = locations,
                    ),
                ),
                assets = mapOf(assetKey to raster),
            ),
            listOf(
                "resolved ROM-derived Gen I Town Map",
                "joined ${locations.size} raw map IDs to Town Map entries",
            ),
        )
    }

    private fun findChains(rom: RomImage, requiredMaps: Set<Int>): List<TownMapChain> {
        val candidates = mutableListOf<TownMapChain>()
        for (bank in 1 until rom.size / BANK_BYTES) {
            val start = bank * BANK_BYTES
            val end = minOf(start + BANK_BYTES, rom.size)
            var cursor = start
            while (cursor + MAX_LOADER_WINDOW < end) {
                resolveLoader(rom, cursor, bank)?.let { loader ->
                    val entries = resolveEntries(rom, bank, requiredMaps)
                    entries.forEach { entryTable ->
                        candidates += TownMapChain(loader.tiles, loader.tilemap, entryTable)
                    }
                }
                cursor++
            }
        }
        return candidates.distinctBy { chain ->
            Triple(
                chain.tiles.contentHashCode(),
                chain.tilemap.contentHashCode(),
                chain.entries.entries.map { it.key to it.value }.hashCode(),
            )
        }
    }

    private fun resolveLoader(rom: RomImage, root: Int, bank: Int): LoaderAsset? {
        val end = minOf(root + MAX_LOADER_WINDOW, (bank + 1) * BANK_BYTES, rom.size)
        val farCopies = mutableListOf<FarCopy>()
        val rlePointers = mutableListOf<Int>()
        var cursor = root
        while (cursor + 14 < end) {
            if (
                rom.u8(cursor) == LOAD_HL_IMMEDIATE && rom.u8(cursor + 3) == LOAD_DE_IMMEDIATE &&
                rom.u8(cursor + 6) == LOAD_BC_IMMEDIATE && rom.u16le(cursor + 7) == TILE_BYTES &&
                rom.u8(cursor + 9) == LOAD_A_IMMEDIATE && rom.u8(cursor + 11) == CALL
            ) {
                val sourceBank = rom.u8(cursor + 10)
                rom.gbBankAddress(sourceBank, rom.u16le(cursor + 1))?.let { source ->
                    if (source + TILE_BYTES <= rom.size) farCopies += FarCopy(source)
                }
            }
            if (rom.u8(cursor) == LOAD_DE_IMMEDIATE && isRleLoop(rom, cursor + 3, end)) {
                rom.gbBankAddress(bank, rom.u16le(cursor + 1))?.let(rlePointers::add)
            }
            cursor++
        }
        val assets = farCopies.flatMap { copy ->
            rlePointers.mapNotNull { pointer ->
                decodeRle(rom, pointer)?.let { map -> LoaderAsset(rom.slice(copy.source, TILE_BYTES), map) }
            }
        }.distinctBy { it.tiles.contentHashCode() to it.tilemap.contentHashCode() }
        return assets.singleOrNull()
    }

    private fun isRleLoop(rom: RomImage, offset: Int, end: Int): Boolean {
        if (offset + MIN_RLE_LOOP_BYTES > end) return false
        var sawRead = false
        var sawZeroTest = false
        var sawLowNibble = false
        var sawSwap = false
        var sawTileBase = false
        var sawWriteIncrement = false
        var cursor = offset
        while (cursor < minOf(offset + MAX_RLE_LOOP_BYTES, end)) {
            when {
                rom.u8(cursor) == 0x1a -> sawRead = true
                rom.u8(cursor) in ACCUMULATOR_ZERO_TESTS &&
                    rom.u8(cursor + 1) in CONDITIONAL_RELATIVE_JUMPS -> sawZeroTest = true
                rom.u8(cursor) == 0xe6 && rom.u8(cursor + 1) == 0x0f -> sawLowNibble = true
                rom.u8(cursor) == 0xcb && rom.u8(cursor + 1) == 0x37 -> sawSwap = true
                rom.u8(cursor) == 0xc6 && rom.u8(cursor + 1) == TILE_BASE -> sawTileBase = true
                rom.u8(cursor) == 0x22 -> sawWriteIncrement = true
            }
            cursor++
        }
        return sawRead && sawZeroTest && sawLowNibble && sawSwap && sawTileBase && sawWriteIncrement
    }

    private fun decodeRle(rom: RomImage, offset: Int): ByteArray? = runCatching {
        val output = ByteArray(SCREEN_AREA)
        var cursor = offset
        var written = 0
        repeat(MAX_RLE_BYTES) {
            val value = rom.u8(cursor++)
            if (value == 0) return@runCatching output.takeIf { written == SCREEN_AREA }
            val run = value and 0x0f
            val tile = (value ushr 4) + TILE_BASE
            if (run == 0 || written + run > SCREEN_AREA) return@runCatching null
            repeat(run) { output[written++] = tile.toByte() }
        }
        null
    }.getOrNull()

    private fun resolveEntries(rom: RomImage, bank: Int, requiredMaps: Set<Int>): List<Map<Int, MapEntry>> {
        val start = bank * BANK_BYTES
        val end = minOf(start + BANK_BYTES, rom.size)
        val results = mutableListOf<Map<Int, MapEntry>>()
        var cursor = start
        while (cursor + MAX_LOOKUP_WINDOW < end) {
            val threshold = parseThreshold(rom, cursor) ?: run { cursor++; continue }
            val tablePointers = immediateHlPointers(rom, bank, cursor, cursor + MAX_LOOKUP_WINDOW)
            if (tablePointers.size < 2) {
                cursor++
                continue
            }
            tablePointers.forEach { internal ->
                tablePointers.forEach { external ->
                    if (internal == external) return@forEach
                    parseEntryTables(rom, bank, threshold, external, internal, requiredMaps)?.let(results::add)
                }
            }
            cursor++
        }
        return results.distinctBy { it.entries.map { entry -> entry.key to entry.value } }
    }

    private fun parseThreshold(rom: RomImage, offset: Int): Int? {
        val end = minOf(offset + MAX_LOOKUP_WINDOW, rom.size)
        var cursor = offset
        while (cursor + 2 < end) {
            if (rom.u8(cursor) == COMPARE_IMMEDIATE && rom.u8(cursor + 2) in CONDITIONAL_RELATIVE_JUMPS) {
                return rom.u8(cursor + 1).takeIf { it in MIN_EXTERNAL_MAPS..MAX_MAP_ID }
            }
            cursor++
        }
        return null
    }

    private fun immediateHlPointers(rom: RomImage, bank: Int, start: Int, end: Int): Set<Int> = buildSet {
        var cursor = start
        while (cursor + 2 < end) {
            if (rom.u8(cursor) == LOAD_HL_IMMEDIATE) rom.gbBankAddress(bank, rom.u16le(cursor + 1))?.let(::add)
            cursor++
        }
    }

    private fun parseEntryTables(
        rom: RomImage,
        bank: Int,
        threshold: Int,
        external: Int,
        internal: Int,
        requiredMaps: Set<Int>,
    ): Map<Int, MapEntry>? = runCatching {
        val externalEntries = List(threshold) { id ->
            parseEntry(rom, bank, external + id * EXTERNAL_ENTRY_BYTES) ?: return@runCatching null
        }
        val internalEntries = mutableListOf<InternalEntry>()
        var cursor = internal
        var previous = threshold
        repeat(MAX_INTERNAL_GROUPS) {
            val limit = rom.u8(cursor)
            if (limit == END_MARKER) return@repeat
            if (limit <= previous) return@runCatching null
            val entry = parseEntry(rom, bank, cursor + 1) ?: return@runCatching null
            internalEntries += InternalEntry(limit, entry)
            previous = limit
            cursor += INTERNAL_ENTRY_BYTES
        }
        if (rom.u8(cursor) != END_MARKER || internalEntries.isEmpty()) return@runCatching null
        buildMap<Int, MapEntry> {
            requiredMaps.forEach { mapId ->
                val entry = if (mapId < threshold) externalEntries[mapId]
                else internalEntries.firstOrNull { mapId < it.limit }?.entry ?: return@runCatching null
                put(mapId, entry)
            }
        }
    }.getOrNull()

    private fun parseEntry(rom: RomImage, bank: Int, offset: Int): MapEntry? = runCatching {
        val packed = rom.u8(offset)
        val nameOffset = rom.gbBankAddress(bank, rom.u16le(offset + 1)) ?: return@runCatching null
        val available = minOf(MAX_NAME_BYTES, rom.size - nameOffset)
        val decoded = PokemonTextCodec.gbEnglish.decodeDetailed(rom.slice(nameOffset, available))
        val name = decoded.text.takeIf {
            decoded.terminated && decoded.validRatio >= MIN_TEXT_RATIO && it.isNotBlank()
        } ?: return@runCatching null
        MapEntry(packed and 0x0f, packed ushr 4, name)
    }.getOrNull()

    private data class FarCopy(val source: Int)
    private data class LoaderAsset(val tiles: ByteArray, val tilemap: ByteArray)
    private data class MapEntry(val x: Int, val y: Int, val name: String)
    private data class InternalEntry(val limit: Int, val entry: MapEntry)
    private data class TownMapChain(val tiles: ByteArray, val tilemap: ByteArray, val entries: Map<Int, MapEntry>)

    private const val BANK_BYTES = 0x4000
    private const val TILE_EDGE = 8
    private const val TILE_COUNT = 16
    private const val TILE_BYTES = TILE_COUNT * 16
    private const val TILE_BASE = 0x60
    private const val SCREEN_WIDTH = 20
    private const val SCREEN_HEIGHT = 18
    private const val SCREEN_AREA = SCREEN_WIDTH * SCREEN_HEIGHT
    private const val MAX_RLE_BYTES = SCREEN_AREA + 1
    private const val MAX_LOADER_WINDOW = 128
    private const val MIN_RLE_LOOP_BYTES = 20
    private const val MAX_RLE_LOOP_BYTES = 64
    private const val MAX_LOOKUP_WINDOW = 80
    private const val MIN_EXTERNAL_MAPS = 16
    private const val MAX_MAP_ID = 0xff
    private const val MAX_INTERNAL_GROUPS = 256
    private const val EXTERNAL_ENTRY_BYTES = 3
    private const val INTERNAL_ENTRY_BYTES = 4
    private const val END_MARKER = 0xff
    private const val MAX_NAME_BYTES = 32
    private const val MIN_TEXT_RATIO = 0.85
    private const val LOAD_HL_IMMEDIATE = 0x21
    private const val LOAD_DE_IMMEDIATE = 0x11
    private const val LOAD_BC_IMMEDIATE = 0x01
    private const val LOAD_A_IMMEDIATE = 0x3e
    private const val CALL = 0xcd
    private const val COMPARE_IMMEDIATE = 0xfe
    private val CONDITIONAL_RELATIVE_JUMPS = setOf(0x20, 0x28, 0x30, 0x38)
    private val ACCUMULATOR_ZERO_TESTS = setOf(0xa7, 0xb7)
    private val DMG_PALETTE = intArrayOf(
        0xffffffff.toInt(),
        0xffaaaaaa.toInt(),
        0xff555555.toInt(),
        0xff000000.toInt(),
    )
}
