package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoi
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiItem
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiOrganicVisibility
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextToken
import kotlin.math.abs

internal object Gen1LocalMapPoiResolver {
    fun resolve(
        rom: RomImage,
        sources: List<Source>,
        maps: List<LocalMap>,
        codec: PokemonTextCodec?,
    ): Resolution {
        val mapsByBaseArea = maps.associateBy(LocalMap::baseAreaId)
        val sourcesByBaseArea = sources.associateBy(Source::baseAreaId)
        val pois = mutableListOf<LocalMapPoi>()
        val skipped = mutableListOf<String>()
        mapsByBaseArea.toSortedMap().forEach { (baseAreaId, map) ->
            val source = sourcesByBaseArea[baseAreaId] ?: return@forEach
            runCatching { readMapPois(rom, source, map, mapsByBaseArea.keys, codec) }
                .onSuccess(pois::addAll)
                .onFailure { failure -> skipped += "map 0x${baseAreaId.hex4()} POIs: ${failure.message}" }
        }
        runCatching { readHiddenItems(rom, mapsByBaseArea) }
            .onSuccess { resolution ->
                pois += resolution.pois
                skipped += resolution.skippedReasons
            }
            .onFailure { failure -> skipped += "Gen I hidden items: ${failure.message}" }
        return Resolution(pois.sortedBy(LocalMapPoi::key), skipped)
    }

    private fun readMapPois(
        rom: RomImage,
        source: Source,
        map: LocalMap,
        acceptedAreaIds: Set<Int>,
        codec: PokemonTextCodec?,
    ): List<LocalMapPoi> {
        val bankLimit = bankEnd(rom, source.headerBank)
        val connectionCount = Integer.bitCount(rom.u8(source.header + CONNECTION_FLAGS_OFFSET) and CONNECTION_MASK)
        val objectPointerField = source.header + FIXED_HEADER_BYTES + connectionCount * CONNECTION_RECORD_BYTES
        require(objectPointerField + 2 <= bankLimit) { "object pointer is truncated" }
        val objectRoot = requireNotNull(
            rom.gbBankAddress(source.headerBank, rom.u16le(objectPointerField)),
        ) { "object pointer is outside its bank" }
        require(objectRoot + 2 <= bankLimit) { "object event root is truncated" }

        var cursor = objectRoot + 1
        val warpCount = rom.u8(cursor++)
        require(warpCount <= MAX_EVENTS_PER_KIND) { "resolved $warpCount warps" }
        require(cursor.toLong() + warpCount.toLong() * WARP_RECORD_BYTES <= bankLimit.toLong()) {
            "warp records are truncated"
        }
        val warps = List(warpCount) { index ->
            val row = cursor + index * WARP_RECORD_BYTES
            WarpRecord(
                index = index,
                x = rom.u8(row + 1),
                y = rom.u8(row),
                destinationBaseAreaId = rom.u8(row + 3).takeIf { it != LAST_MAP_SENTINEL && it in acceptedAreaIds },
            )
        }
        cursor += warpCount * WARP_RECORD_BYTES

        require(cursor < bankLimit) { "background event count is truncated" }
        val backgroundCount = rom.u8(cursor++)
        require(backgroundCount <= MAX_EVENTS_PER_KIND) { "resolved $backgroundCount background events" }
        require(cursor.toLong() + backgroundCount.toLong() * BACKGROUND_RECORD_BYTES <= bankLimit.toLong()) {
            "background events are truncated"
        }
        val backgrounds = List(backgroundCount) { index ->
            val row = cursor + index * BACKGROUND_RECORD_BYTES
            BackgroundRecord(
                index = index,
                x = rom.u8(row + 1),
                y = rom.u8(row),
                textId = rom.u8(row + 2),
            )
        }
        cursor += backgroundCount * BACKGROUND_RECORD_BYTES

        require(cursor < bankLimit) { "object event count is truncated" }
        val objectCount = rom.u8(cursor++)
        require(objectCount <= MAX_EVENTS_PER_KIND) { "resolved $objectCount object events" }
        val objects = mutableListOf<ObjectRecord>()
        repeat(objectCount) { index ->
            require(cursor + MIN_OBJECT_RECORD_BYTES <= bankLimit) { "object event $index is truncated" }
            val type = rom.u8(cursor + OBJECT_TYPE_OFFSET)
            val typeBits = type and OBJECT_TYPE_MASK
            val recordBytes = when (typeBits) {
                OBJECT_TYPE_NORMAL -> NORMAL_OBJECT_RECORD_BYTES
                OBJECT_TYPE_TRAINER -> TRAINER_OBJECT_RECORD_BYTES
                OBJECT_TYPE_ITEM -> ITEM_OBJECT_RECORD_BYTES
                else -> error("object event $index has conflicting type bits")
            }
            require(cursor + recordBytes <= bankLimit) { "object event $index is truncated" }
            objects += ObjectRecord(
                index = index,
                x = rom.u8(cursor + 2) - OBJECT_COORDINATE_BIAS,
                y = rom.u8(cursor + 1) - OBJECT_COORDINATE_BIAS,
                itemId = if (typeBits == OBJECT_TYPE_ITEM) rom.u8(cursor + 6) else null,
            )
            cursor += recordBytes
        }

        val validWarps = warps.filter { it.inside(map) }
        val validBackgrounds = backgrounds.filter { it.inside(map) }
        val backgroundWarps = associateBackgroundsWithWarps(validBackgrounds, validWarps)
        val representedWarpIndexes = backgroundWarps.values.mapTo(mutableSetOf(), WarpRecord::index)
        return buildList {
            objects.forEach { objectEvent ->
                val itemId = objectEvent.itemId ?: return@forEach
                if (!objectEvent.inside(map) || itemId == 0) return@forEach
                add(
                    LocalMapPoi(
                        key = "${map.key}/object/${objectEvent.index}",
                        localMapKey = map.key,
                        baseAreaId = map.baseAreaId,
                        tileX = objectEvent.x,
                        tileY = objectEvent.y,
                        kind = LocalMapPoiKind.VISIBLE_ITEM,
                        item = LocalMapPoiItem(itemId = itemId),
                    ),
                )
            }
            validWarps.filter { it.index !in representedWarpIndexes }.forEach { warp ->
                add(warp.toPoi(map))
            }
            validBackgrounds.forEach { background ->
                val destination = backgroundWarps[background.index]
                add(
                    LocalMapPoi(
                        key = "${map.key}/bg/${background.index}",
                        localMapKey = map.key,
                        baseAreaId = map.baseAreaId,
                        tileX = background.x,
                        tileY = background.y,
                        kind = LocalMapPoiKind.PLACE,
                        organicVisibility = LocalMapPoiOrganicVisibility.ENTRANCE_PROXIMITY,
                        displayName = codec?.let {
                            readSignHeadline(rom, source, background.textId, it)
                        },
                        destinationBaseAreaId = destination?.destinationBaseAreaId,
                    ),
                )
            }
        }
    }

    private fun associateBackgroundsWithWarps(
        backgrounds: List<BackgroundRecord>,
        warps: List<WarpRecord>,
    ): Map<Int, WarpRecord> {
        val nominations = backgrounds.mapNotNull { background ->
            val nearby = warps.map { warp -> warp to abs(warp.x - background.x) + abs(warp.y - background.y) }
                .filter { (_, distance) -> distance <= SIGN_ENTRANCE_MAX_DISTANCE }
            val minimum = nearby.minOfOrNull { it.second } ?: return@mapNotNull null
            val nearest = nearby.filter { it.second == minimum }.map { it.first }
            background.index to (nearest.singleOrNull() ?: return@mapNotNull null)
        }
        return nominations.groupBy { it.second.index }.values
            .filter { it.size == 1 }
            .associate { it.single() }
    }

    private fun readSignHeadline(
        rom: RomImage,
        source: Source,
        textId: Int,
        codec: PokemonTextCodec,
    ): String? = runCatching {
        if (textId == 0) return@runCatching null
        val table = requireNotNull(rom.gbBankAddress(source.headerBank, rom.u16le(source.header + TEXT_POINTER_OFFSET)))
        val pointerField = table + (textId - 1) * 2
        require(pointerField + 2 <= bankEnd(rom, source.headerBank))
        val script = requireNotNull(rom.gbBankAddress(source.headerBank, rom.u16le(pointerField)))
        val text = when (rom.u8(script)) {
            TEXT_START -> script
            TEXT_FAR -> {
                require(script + TEXT_FAR_BYTES <= bankEnd(rom, source.headerBank))
                requireNotNull(rom.gbBankAddress(rom.u8(script + 3), rom.u16le(script + 1)))
            }
            else -> return@runCatching null
        }
        decodeHeadline(rom, text, codec)
    }.getOrNull()

    private fun decodeHeadline(rom: RomImage, offset: Int, codec: PokemonTextCodec): String? {
        if (rom.u8(offset) != TEXT_START) return null
        val output = StringBuilder()
        val limit = minOf(rom.size, offset + MAX_SIGN_TEXT_BYTES)
        var cursor = offset + 1
        var terminated = false
        while (cursor < limit) {
            val value = rom.u8(cursor)
            if (value in SIGN_HEADLINE_ENDS) {
                terminated = true
                break
            }
            when (value) {
                TEXT_PLAYER -> {
                    output.append("{PLAYER}")
                    cursor++
                }
                TEXT_RIVAL -> {
                    output.append("{RIVAL}")
                    cursor++
                }
                TEXT_POKEMON -> {
                    output.append("POKé")
                    cursor++
                }
                TEXT_ELLIPSIS -> {
                    output.append("……")
                    cursor++
                }
                else -> {
                    val token = codec.decodeToken(rom, cursor, limit)
                    cursor += token.byteCount
                    when (token) {
                        is PokemonTextToken.Glyph -> output.append(token.text)
                        is PokemonTextToken.Whitespace -> output.append(token.text)
                        is PokemonTextToken.Substitution -> output.append(token.text)
                        is PokemonTextToken.Control -> output.append(token.replacement)
                        is PokemonTextToken.Invalid -> return null
                        is PokemonTextToken.Terminator -> {
                            terminated = true
                            break
                        }
                    }
                }
            }
        }
        if (!terminated) return null
        return output.toString().replace(WHITESPACE, " ").trim().takeIf { it.length >= MIN_SIGN_HEADLINE_CHARS }
    }

    private fun readHiddenItems(
        rom: RomImage,
        mapsByBaseArea: Map<Int, LocalMap>,
    ): HiddenResolution {
        val eventAuthorities = findHiddenEventAuthorities(rom)
        if (eventAuthorities.size != 1) {
            return HiddenResolution(emptyList(), listOf("Gen I hidden-event authorities: ${eventAuthorities.size}"))
        }
        val itemAuthorities = findHiddenItemAuthorities(rom)
        if (itemAuthorities.size != 1) {
            return HiddenResolution(emptyList(), listOf("Gen I hidden-item authorities: ${itemAuthorities.size}"))
        }
        val events = eventAuthorities.single()
        val items = itemAuthorities.single()
        val coordinateIndexes = items.coordinates.withIndex().groupBy { it.value }
        val pois = mutableListOf<LocalMapPoi>()
        val skipped = mutableListOf<String>()
        events.entries.forEach { entry ->
            val baseAreaId = entry.baseAreaId
            val map = mapsByBaseArea[baseAreaId] ?: return@forEach
            runCatching {
                var cursor = entry.root
                var eventIndex = 0
                while (true) {
                    require(cursor < bankEnd(rom, events.bank)) { "hidden-event list is truncated" }
                    if (rom.u8(cursor) == EVENT_LIST_END) break
                    require(eventIndex < MAX_HIDDEN_EVENTS_PER_MAP) { "too many hidden events" }
                    require(cursor + HIDDEN_EVENT_RECORD_BYTES <= bankEnd(rom, events.bank)) {
                        "hidden event $eventIndex is truncated"
                    }
                    val y = rom.u8(cursor)
                    val x = rom.u8(cursor + 1)
                    val itemId = rom.u8(cursor + 2)
                    val function = rom.gbBankAddress(rom.u8(cursor + 3), rom.u16le(cursor + 4))
                    if (function == items.function && x in 0 until map.gridWidth && y in 0 until map.gridHeight) {
                        val coordinate = HiddenCoordinate(baseAreaId, x, y)
                        val coordinateIndex = coordinateIndexes[coordinate]?.singleOrNull()?.index
                        if (coordinateIndex == null) {
                            skipped += "map 0x${baseAreaId.hex4()} hidden item $eventIndex has no unique flag coordinate"
                        } else if (itemId != 0) {
                            pois += LocalMapPoi(
                                key = "${map.key}/hidden/$coordinateIndex",
                                localMapKey = map.key,
                                baseAreaId = map.baseAreaId,
                                tileX = x,
                                tileY = y,
                                kind = LocalMapPoiKind.HIDDEN_ITEM,
                                organicVisibility = LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE,
                                item = LocalMapPoiItem(itemId = itemId, collectionFlagId = coordinateIndex),
                            )
                        }
                    }
                    cursor += HIDDEN_EVENT_RECORD_BYTES
                    eventIndex++
                }
            }.onFailure { failure ->
                skipped += "map 0x${baseAreaId.hex4()} hidden items: ${failure.message}"
            }
        }
        return HiddenResolution(pois, skipped)
    }

    private fun findHiddenEventAuthorities(rom: RomImage): List<HiddenEventAuthority> = buildList {
        var offset = 0
        while (offset + MIN_HIDDEN_EVENT_CONSUMER_BYTES <= rom.size) {
            parseSeparatedHiddenEventAuthorityAt(rom, offset)?.let(::add)
            parseInlineHiddenEventAuthorityAt(rom, offset)?.let(::add)
            offset++
        }
    }.distinctBy { it.bank to it.entries }

    private fun parseSeparatedHiddenEventAuthorityAt(rom: RomImage, offset: Int): HiddenEventAuthority? = runCatching {
        if (
            rom.u8(offset) != LOAD_HL_IMMEDIATE || rom.u8(offset + 3) != XOR_A ||
            rom.u8(offset + 4) != LOAD_HLI_A || rom.u8(offset + 5) != LOAD_HLI_A ||
            rom.u8(offset + 6) != LOAD_HLI_A || rom.u8(offset + 7) != LOAD_HL_A ||
            rom.u8(offset + 8) != LOAD_DE_IMMEDIATE || rom.u16le(offset + 9) != 0 ||
            rom.u8(offset + 11) != LOAD_HL_IMMEDIATE || rom.u8(offset + 14) != LOAD_A_HLI ||
            rom.u8(offset + 15) != LOAD_B_A || rom.u8(offset + 16) != COMPARE_IMMEDIATE ||
            rom.u8(offset + 17) != EVENT_LIST_END || rom.u8(offset + 18) != JUMP_RELATIVE_Z ||
            rom.u8(offset + 20) != LOAD_A_ABSOLUTE || rom.u8(offset + 23) != COMPARE_B ||
            rom.u8(offset + 24) != JUMP_RELATIVE_Z || rom.u8(offset + 26) != INC_DE ||
            rom.u8(offset + 27) != INC_DE || rom.u8(offset + 28) != JUMP_RELATIVE ||
            rom.u8(offset + 30) != LOAD_HL_IMMEDIATE || rom.u8(offset + 33) != ADD_HL_DE ||
            rom.u8(offset + 34) != LOAD_A_HLI || rom.u8(offset + 35) != LOAD_H_HL ||
            rom.u8(offset + 36) != LOAD_L_A
        ) return@runCatching null
        val bank = offset / BANK_BYTES
        val mapRoot = rom.gbBankAddress(bank, rom.u16le(offset + 12)) ?: return@runCatching null
        val pointerRoot = rom.gbBankAddress(bank, rom.u16le(offset + 31)) ?: return@runCatching null
        val mapIds = mutableListOf<Int>()
        var cursor = mapRoot
        while (true) {
            require(cursor < bankEnd(rom, bank))
            val mapId = rom.u8(cursor++)
            if (mapId == EVENT_LIST_END) break
            require(mapIds.size < MAX_MAP_IDS && mapId !in mapIds)
            mapIds += mapId
        }
        require(mapIds.isNotEmpty())
        require(pointerRoot.toLong() + mapIds.size.toLong() * 2 <= bankEnd(rom, bank).toLong())
        val entries = mapIds.mapIndexed { index, baseAreaId ->
            val pointerField = pointerRoot + index * 2
            HiddenEventEntry(
                baseAreaId = baseAreaId,
                root = requireNotNull(rom.gbBankAddress(bank, rom.u16le(pointerField))),
            )
        }
        HiddenEventAuthority(bank, entries)
    }.getOrNull()

    private fun parseInlineHiddenEventAuthorityAt(rom: RomImage, offset: Int): HiddenEventAuthority? = runCatching {
        if (
            rom.u8(offset) != LOAD_HL_IMMEDIATE || rom.u8(offset + 3) != XOR_A ||
            rom.u8(offset + 4) != LOAD_HLI_A || rom.u8(offset + 5) != LOAD_HLI_A ||
            rom.u8(offset + 6) != LOAD_HLI_A || rom.u8(offset + 7) != LOAD_HL_A ||
            rom.u8(offset + 8) != LOAD_HL_IMMEDIATE || rom.u8(offset + 11) != LOAD_DE_IMMEDIATE ||
            rom.u16le(offset + 12) != INLINE_HIDDEN_EVENT_RECORD_BYTES ||
            rom.u8(offset + 14) != LOAD_A_ABSOLUTE || rom.u8(offset + 17) != CALL ||
            rom.u8(offset + 20) != JUMP_RELATIVE_NC || rom.u8(offset + 22) != INC_HL ||
            rom.u8(offset + 23) != LOAD_A_HLI || rom.u8(offset + 24) != LOAD_H_HL ||
            rom.u8(offset + 25) != LOAD_L_A
        ) return@runCatching null
        val bank = offset / BANK_BYTES
        val mapRoot = rom.gbBankAddress(bank, rom.u16le(offset + 9)) ?: return@runCatching null
        val entries = mutableListOf<HiddenEventEntry>()
        var cursor = mapRoot
        while (true) {
            require(cursor < bankEnd(rom, bank))
            val baseAreaId = rom.u8(cursor)
            if (baseAreaId == EVENT_LIST_END) break
            require(entries.size < MAX_MAP_IDS && entries.none { it.baseAreaId == baseAreaId })
            require(cursor + INLINE_HIDDEN_EVENT_RECORD_BYTES <= bankEnd(rom, bank))
            entries += HiddenEventEntry(
                baseAreaId = baseAreaId,
                root = requireNotNull(rom.gbBankAddress(bank, rom.u16le(cursor + 1))),
            )
            cursor += INLINE_HIDDEN_EVENT_RECORD_BYTES
        }
        require(entries.isNotEmpty())
        HiddenEventAuthority(bank, entries)
    }.getOrNull()

    private fun findHiddenItemAuthorities(rom: RomImage): List<HiddenItemAuthority> = buildList {
        var offset = 0
        while (offset + HIDDEN_ITEM_CONSUMER_BYTES <= rom.size) {
            parseHiddenItemAuthorityAt(rom, offset)?.let(::add)
            offset++
        }
    }.distinctBy { it.function to it.coordinateRoot }

    private fun parseHiddenItemAuthorityAt(
        rom: RomImage,
        offset: Int,
    ): HiddenItemAuthority? = runCatching {
        if (
            rom.u8(offset) != LOAD_HL_IMMEDIATE || rom.u8(offset + 3) != CALL ||
            rom.u8(offset + 6) != STORE_A_ABSOLUTE || rom.u8(offset + 9) != LOAD_HL_IMMEDIATE ||
            rom.u8(offset + 12) != LOAD_A_ABSOLUTE || rom.u16le(offset + 13) != rom.u16le(offset + 7) ||
            rom.u8(offset + 15) != LOAD_C_A || rom.u8(offset + 16) != LOAD_B_IMMEDIATE ||
            rom.u8(offset + 18) != LOAD_A_IMMEDIATE || rom.u8(offset + 20) != CALL ||
            rom.u8(offset + 23) != LOAD_A_C || rom.u8(offset + 24) != AND_A ||
            !hasHiddenItemContinuation(rom, offset)
        ) return@runCatching null
        val bank = offset / BANK_BYTES
        val coordinateRoot = rom.gbBankAddress(bank, rom.u16le(offset + 1)) ?: return@runCatching null
        val coordinates = mutableListOf<HiddenCoordinate>()
        var cursor = coordinateRoot
        while (true) {
            require(cursor < bankEnd(rom, bank))
            val baseAreaId = rom.u8(cursor)
            if (baseAreaId == EVENT_LIST_END) break
            require(coordinates.size < MAX_HIDDEN_COORDINATES)
            require(cursor + HIDDEN_COORDINATE_BYTES <= bankEnd(rom, bank))
            val y = rom.u8(cursor + 1)
            val x = rom.u8(cursor + 2)
            coordinates += HiddenCoordinate(baseAreaId, x, y)
            cursor += HIDDEN_COORDINATE_BYTES
        }
        require(coordinates.isNotEmpty() && coordinates.toSet().size == coordinates.size)
        HiddenItemAuthority(offset, coordinateRoot, coordinates)
    }.getOrNull()

    private fun hasHiddenItemContinuation(rom: RomImage, offset: Int): Boolean = when (rom.u8(offset + 25)) {
        RETURN_NZ -> rom.u8(offset + 26) == CALL
        JUMP_RELATIVE_NZ -> {
            val branchTarget = offset + 27 + rom.u8(offset + 26).toByte().toInt()
            rom.u8(offset + 27) == CALL &&
                branchTarget > offset + 27 &&
                branchTarget + ITEM_ALREADY_FOUND_EXIT_BYTES <= bankEnd(rom, offset / BANK_BYTES) &&
                rom.u8(branchTarget) == LOAD_A_IMMEDIATE &&
                rom.u8(branchTarget + 1) == EVENT_LIST_END &&
                rom.u8(branchTarget + 2) == STORE_HIGH_A &&
                rom.u8(branchTarget + 4) == RETURN
        }
        else -> false
    }

    data class Source(
        val baseAreaId: Int,
        val headerBank: Int,
        val header: Int,
    )

    data class Resolution(
        val pois: List<LocalMapPoi>,
        val skippedReasons: List<String>,
    )

    private data class HiddenResolution(
        val pois: List<LocalMapPoi>,
        val skippedReasons: List<String>,
    )

    private data class WarpRecord(
        val index: Int,
        val x: Int,
        val y: Int,
        val destinationBaseAreaId: Int?,
    ) {
        fun inside(map: LocalMap): Boolean = x in 0 until map.gridWidth && y in 0 until map.gridHeight

        fun toPoi(map: LocalMap) = LocalMapPoi(
            key = "${map.key}/warp/$index",
            localMapKey = map.key,
            baseAreaId = map.baseAreaId,
            tileX = x,
            tileY = y,
            kind = LocalMapPoiKind.PLACE,
            organicVisibility = LocalMapPoiOrganicVisibility.ENTRANCE_PROXIMITY,
            destinationBaseAreaId = destinationBaseAreaId,
        )
    }

    private data class BackgroundRecord(val index: Int, val x: Int, val y: Int, val textId: Int) {
        fun inside(map: LocalMap): Boolean = x in 0 until map.gridWidth && y in 0 until map.gridHeight
    }

    private data class ObjectRecord(val index: Int, val x: Int, val y: Int, val itemId: Int?) {
        fun inside(map: LocalMap): Boolean = x in 0 until map.gridWidth && y in 0 until map.gridHeight
    }

    private data class HiddenEventAuthority(
        val bank: Int,
        val entries: List<HiddenEventEntry>,
    )

    private data class HiddenEventEntry(
        val baseAreaId: Int,
        val root: Int,
    )

    private data class HiddenItemAuthority(
        val function: Int,
        val coordinateRoot: Int,
        val coordinates: List<HiddenCoordinate>,
    )

    private data class HiddenCoordinate(val baseAreaId: Int, val x: Int, val y: Int)

    private fun bankEnd(rom: RomImage, bank: Int): Int =
        minOf(rom.size.toLong(), (bank.toLong() + 1L) * BANK_BYTES).toInt()

    private fun Int.hex4(): String = toString(16).padStart(4, '0')

    private const val BANK_BYTES = 0x4000
    private const val CONNECTION_FLAGS_OFFSET = 9
    private const val CONNECTION_MASK = 0x0F
    private const val FIXED_HEADER_BYTES = 10
    private const val CONNECTION_RECORD_BYTES = 11
    private const val TEXT_POINTER_OFFSET = 5
    private const val WARP_RECORD_BYTES = 4
    private const val BACKGROUND_RECORD_BYTES = 3
    private const val MIN_OBJECT_RECORD_BYTES = 6
    private const val NORMAL_OBJECT_RECORD_BYTES = 6
    private const val ITEM_OBJECT_RECORD_BYTES = 7
    private const val TRAINER_OBJECT_RECORD_BYTES = 8
    private const val OBJECT_TYPE_OFFSET = 5
    private const val OBJECT_TYPE_MASK = 0xC0
    private const val OBJECT_TYPE_NORMAL = 0x00
    private const val OBJECT_TYPE_TRAINER = 0x40
    private const val OBJECT_TYPE_ITEM = 0x80
    private const val OBJECT_COORDINATE_BIAS = 4
    private const val LAST_MAP_SENTINEL = 0xFF
    private const val MAX_EVENTS_PER_KIND = 64
    private const val SIGN_ENTRANCE_MAX_DISTANCE = 3
    private const val TEXT_START = 0x00
    private const val TEXT_FAR = 0x17
    private const val TEXT_FAR_BYTES = 4
    private const val TEXT_PLAYER = 0x52
    private const val TEXT_RIVAL = 0x53
    private const val TEXT_POKEMON = 0x54
    private const val TEXT_ELLIPSIS = 0x56
    private val SIGN_HEADLINE_ENDS = setOf(0x4C, 0x4E, 0x4F, 0x50, 0x51, 0x55, 0x57, 0x58)
    private const val MAX_SIGN_TEXT_BYTES = 96
    private const val MIN_SIGN_HEADLINE_CHARS = 2
    private val WHITESPACE = Regex("\\s+")

    private const val EVENT_LIST_END = 0xFF
    private const val HIDDEN_EVENT_RECORD_BYTES = 6
    private const val INLINE_HIDDEN_EVENT_RECORD_BYTES = 3
    private const val HIDDEN_COORDINATE_BYTES = 3
    private const val MAX_MAP_IDS = 256
    private const val MAX_HIDDEN_EVENTS_PER_MAP = 64
    private const val MAX_HIDDEN_COORDINATES = 256
    private const val MIN_HIDDEN_EVENT_CONSUMER_BYTES = 26
    private const val HIDDEN_ITEM_CONSUMER_BYTES = 28
    private const val ITEM_ALREADY_FOUND_EXIT_BYTES = 5

    private const val LOAD_HL_IMMEDIATE = 0x21
    private const val LOAD_DE_IMMEDIATE = 0x11
    private const val LOAD_A_IMMEDIATE = 0x3E
    private const val LOAD_B_IMMEDIATE = 0x06
    private const val LOAD_A_ABSOLUTE = 0xFA
    private const val STORE_A_ABSOLUTE = 0xEA
    private const val STORE_HIGH_A = 0xE0
    private const val LOAD_A_HLI = 0x2A
    private const val LOAD_HLI_A = 0x22
    private const val LOAD_HL_A = 0x77
    private const val LOAD_H_HL = 0x66
    private const val LOAD_L_A = 0x6F
    private const val LOAD_B_A = 0x47
    private const val LOAD_C_A = 0x4F
    private const val LOAD_A_C = 0x79
    private const val XOR_A = 0xAF
    private const val AND_A = 0xA7
    private const val COMPARE_IMMEDIATE = 0xFE
    private const val COMPARE_B = 0xB8
    private const val INC_HL = 0x23
    private const val INC_DE = 0x13
    private const val ADD_HL_DE = 0x19
    private const val JUMP_RELATIVE = 0x18
    private const val JUMP_RELATIVE_NZ = 0x20
    private const val JUMP_RELATIVE_Z = 0x28
    private const val JUMP_RELATIVE_NC = 0x30
    private const val CALL = 0xCD
    private const val RETURN_NZ = 0xC0
    private const val RETURN = 0xC9
}
