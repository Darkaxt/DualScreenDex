package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoi
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiItem
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiOrganicVisibility
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextToken
import kotlin.math.abs

internal object Gen3LocalMapPoiResolver {
    fun resolve(
        rom: RomImage,
        headers: Map<Int, Int>,
        maps: List<LocalMap>,
        family: EngineFamily,
        codec: PokemonTextCodec?,
    ): Resolution {
        val mapsByBaseArea = maps.associateBy(LocalMap::baseAreaId)
        val pois = mutableListOf<LocalMapPoi>()
        val skipped = mutableListOf<String>()
        mapsByBaseArea.toSortedMap().forEach { (baseAreaId, map) ->
            val header = headers[baseAreaId] ?: return@forEach
            runCatching { readMapEvents(rom, header, map, family, codec) }
                .onSuccess(pois::addAll)
                .onFailure { failure ->
                    skipped += "map 0x${baseAreaId.hex4()} POIs: ${failure.message}"
                }
        }
        return Resolution(pois, skipped)
    }

    private fun readMapEvents(
        rom: RomImage,
        header: Int,
        map: LocalMap,
        family: EngineFamily,
        codec: PokemonTextCodec?,
    ): List<LocalMapPoi> {
        val events = rom.gbaPointer(header + MAP_EVENTS_OFFSET) ?: return emptyList()
        val objectCount = rom.u8(events + OBJECT_COUNT_OFFSET)
        val warpCount = rom.u8(events + WARP_COUNT_OFFSET)
        val bgCount = rom.u8(events + BG_COUNT_OFFSET)
        val objects = recordsPointer(rom, events + OBJECT_POINTER_OFFSET, objectCount, OBJECT_RECORD_BYTES, "object events")
        val warps = recordsPointer(rom, events + WARP_POINTER_OFFSET, warpCount, WARP_RECORD_BYTES, "warps")
        val backgrounds = recordsPointer(rom, events + BG_POINTER_OFFSET, bgCount, BG_RECORD_BYTES, "background events")
        val warpRecords = buildList {
            if (warps != null) repeat(warpCount) { index ->
                val offset = warps + index * WARP_RECORD_BYTES
                val x = rom.s16le(offset)
                val y = rom.s16le(offset + 2)
                if (x in 0 until map.gridWidth && y in 0 until map.gridHeight) {
                    add(
                        WarpRecord(
                            index = index,
                            x = x,
                            y = y,
                            destinationBaseAreaId = rom.u8(offset + 7) shl 8 or rom.u8(offset + 6),
                        ),
                    )
                }
            }
        }
        val backgroundRecords = buildList {
            if (backgrounds != null) repeat(bgCount) { index ->
                val offset = backgrounds + index * BG_RECORD_BYTES
                val x = rom.u16le(offset)
                val y = rom.u16le(offset + 2)
                if (x in 0 until map.gridWidth && y in 0 until map.gridHeight) {
                    add(BackgroundRecord(index, offset, x, y, rom.u8(offset + 5)))
                }
            }
        }
        val nominatedBackgroundWarps = backgroundRecords
            .filter { it.kind in BG_EVENT_SIGN_KINDS }
            .mapNotNull { background ->
                val nearby = warpRecords
                    .map { warp -> warp to abs(warp.x - background.x) + abs(warp.y - background.y) }
                    .filter { (_, distance) -> distance <= SIGN_ENTRANCE_MAX_DISTANCE }
                val minimum = nearby.minOfOrNull { it.second } ?: return@mapNotNull null
                val nearest = nearby.filter { it.second == minimum }.map { it.first }
                (nearest.singleOrNull() ?: return@mapNotNull null).let { background.index to it }
            }
            .toMap()
        val backgroundWarps = nominatedBackgroundWarps.entries
            .groupBy { it.value.index }
            .values
            .filter { nominations -> nominations.size == 1 }
            .associate { nominations -> nominations.single().let { it.key to it.value } }
        val representedWarpIndexes = backgroundWarps.values.mapTo(mutableSetOf()) { it.index }
        return buildList(objectCount + warpCount + bgCount) {
            if (objects != null) repeat(objectCount) { index ->
                val offset = objects + index * OBJECT_RECORD_BYTES
                val x = rom.s16le(offset + 4)
                val y = rom.s16le(offset + 6)
                if (x !in 0 until map.gridWidth || y !in 0 until map.gridHeight) return@repeat
                val script = rom.gbaPointer(offset + 0x10) ?: return@repeat
                val itemId = readVisibleItemId(rom, script) ?: return@repeat
                add(
                    LocalMapPoi(
                        key = "${map.key}/object/$index",
                        localMapKey = map.key,
                        baseAreaId = map.baseAreaId,
                        tileX = x,
                        tileY = y,
                        kind = LocalMapPoiKind.VISIBLE_ITEM,
                        item = LocalMapPoiItem(
                            itemId = itemId,
                            collectionFlagId = rom.u16le(offset + 0x14),
                        ),
                    ),
                )
            }
            warpRecords.filter { it.index !in representedWarpIndexes }.forEach { warp ->
                add(
                    LocalMapPoi(
                        key = "${map.key}/warp/${warp.index}",
                        localMapKey = map.key,
                        baseAreaId = map.baseAreaId,
                        tileX = warp.x,
                        tileY = warp.y,
                        kind = LocalMapPoiKind.PLACE,
                        organicVisibility = LocalMapPoiOrganicVisibility.ENTRANCE_PROXIMITY,
                        destinationBaseAreaId = warp.destinationBaseAreaId,
                    ),
                )
            }
            backgroundRecords.forEach { background ->
                if (background.kind == BG_EVENT_HIDDEN_ITEM) {
                    val hiddenFlagIndex = when (family) {
                        EngineFamily.FIRERED_LEAFGREEN -> rom.u8(background.offset + 10)
                        else -> rom.u16le(background.offset + 10)
                    }
                    add(
                        LocalMapPoi(
                            key = "${map.key}/bg/${background.index}",
                            localMapKey = map.key,
                            baseAreaId = map.baseAreaId,
                            tileX = background.x,
                            tileY = background.y,
                            kind = LocalMapPoiKind.HIDDEN_ITEM,
                            organicVisibility = LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE,
                            item = LocalMapPoiItem(
                                itemId = rom.u16le(background.offset + 8),
                                collectionFlagId = HIDDEN_ITEMS_FLAG_START + hiddenFlagIndex,
                            ),
                        ),
                    )
                } else {
                    val destination = backgroundWarps[background.index]
                    val isSign = background.kind in BG_EVENT_SIGN_KINDS
                    val signHeadline = if (isSign) {
                        codec?.let { selectedCodec ->
                            rom.gbaPointer(background.offset + 8)?.let {
                                readSignHeadline(rom, it, selectedCodec)
                            }
                        }
                    } else {
                        null
                    }
                    add(
                        LocalMapPoi(
                            key = "${map.key}/bg/${background.index}",
                            localMapKey = map.key,
                            baseAreaId = map.baseAreaId,
                            tileX = background.x,
                            tileY = background.y,
                            kind = if (isSign || destination != null) {
                                LocalMapPoiKind.PLACE
                            } else {
                                LocalMapPoiKind.UNKNOWN
                            },
                            organicVisibility = if (isSign) {
                                LocalMapPoiOrganicVisibility.ENTRANCE_PROXIMITY
                            } else {
                                LocalMapPoiOrganicVisibility.VISIBLE
                            },
                            displayName = signHeadline?.displayName,
                            displayNamesByTrainerGender = signHeadline?.byTrainerGender.orEmpty(),
                            destinationBaseAreaId = destination?.destinationBaseAreaId,
                        ),
                    )
                }
            }
        }
    }

    private fun readSignHeadline(
        rom: RomImage,
        script: Int,
        codec: PokemonTextCodec,
    ): SignHeadline? =
        readSimpleSignHeadline(rom, script, codec)?.let { SignHeadline(displayName = it) }
            ?: readGenderConditionedSignHeadline(rom, script, codec)

    private fun readGenderConditionedSignHeadline(
        rom: RomImage,
        script: Int,
        codec: PokemonTextCodec,
    ): SignHeadline? {
        if (script.toLong() + GENDER_SIGN_SCRIPT_BYTES > rom.size.toLong()) return null
        if (rom.u8(script) != SCR_OP_LOCK_ALL || rom.u8(script + 1) != SCR_OP_CHECK_PLAYER_GENDER) return null
        var cursor = script + 2
        val names = linkedMapOf<Int, String>()
        repeat(2) {
            if (rom.u8(cursor) != SCR_OP_COMPARE_VAR_TO_VALUE || rom.u16le(cursor + 1) != VAR_RESULT) return null
            val gender = rom.u16le(cursor + 3)
            if (gender !in 0..1 || gender in names) return null
            cursor += COMPARE_VAR_TO_VALUE_BYTES
            if (rom.u8(cursor) != SCR_OP_CALL_IF || rom.u8(cursor + 1) != COMPARISON_EQUAL) return null
            val target = rom.gbaPointer(cursor + 2) ?: return null
            names[gender] = readSimpleSignHeadline(rom, target, codec) ?: return null
            cursor += CALL_IF_BYTES
        }
        if (rom.u8(cursor) != SCR_OP_RELEASE_ALL || rom.u8(cursor + 1) != SCR_OP_END) return null
        return SignHeadline(
            displayName = names.values.firstOrNull(),
            byTrainerGender = names,
        )
    }

    private fun readSimpleSignHeadline(
        rom: RomImage,
        script: Int,
        codec: PokemonTextCodec,
    ): String? {
        if (script.toLong() + SIMPLE_MSGBOX_BYTES > rom.size.toLong()) return null
        if (rom.u8(script) != SCR_OP_LOAD_WORD || rom.u8(script + 1) != 0) return null
        val text = rom.gbaPointer(script + 2) ?: return null
        if (rom.u8(script + 6) != SCR_OP_CALL_STD || rom.u8(script + 7) !in 0..MAX_MSGBOX_TYPE) return null
        val available = minOf(MAX_SIGN_TEXT_BYTES, rom.size - text)
        if (available <= 0) return null
        return decodeSignHeadline(rom.slice(text, available), codec)
    }

    private fun decodeSignHeadline(raw: ByteArray, codec: PokemonTextCodec): String? {
        val rom = RomImage(raw)
        val output = StringBuilder()
        var cursor = 0
        var terminated = false
        while (cursor < rom.size) {
            val byte = rom.u8(cursor)
            if (byte == codec.terminator || byte in SIGN_LINE_BREAKS) {
                terminated = true
                break
            }
            if (byte == EXT_CTRL_CODE_BEGIN) {
                if (cursor + 1 >= rom.size || rom.u8(cursor + 1) != EXT_CTRL_CODE_PLAYER) return null
                output.append("{PLAYER}")
                cursor += 2
                continue
            }
            val token = codec.decodeToken(rom, cursor, rom.size)
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
        if (!terminated) return null
        return output.toString().replace(WHITESPACE, " ").trim().takeIf { it.length >= MIN_SIGN_HEADLINE_CHARS }
    }

    private fun readVisibleItemId(rom: RomImage, script: Int): Int? {
        if (script.toLong() + FIND_ITEM_SCRIPT_BYTES > rom.size.toLong()) return null
        if (rom.u8(script) != SCR_OP_SETORCOPYVAR || rom.u16le(script + 1) != VAR_0x8000) return null
        val itemId = rom.u16le(script + 3)
        if (rom.u8(script + 5) != SCR_OP_SETORCOPYVAR || rom.u16le(script + 6) != VAR_0x8001) return null
        if (rom.u16le(script + 8) == 0) return null
        if (rom.u8(script + 10) != SCR_OP_CALL_STD || rom.u8(script + 11) != STD_FIND_ITEM) return null
        return itemId.takeUnless { it in SCRIPT_VARIABLE_RANGE }
    }

    private fun recordsPointer(
        rom: RomImage,
        pointerField: Int,
        count: Int,
        recordBytes: Int,
        label: String,
    ): Int? {
        if (count == 0) return null
        val pointer = requireNotNull(rom.gbaPointer(pointerField)) { "non-empty $label have no ROM pointer" }
        require(pointer.toLong() + count.toLong() * recordBytes <= rom.size.toLong()) { "$label are truncated" }
        return pointer
    }

    data class Resolution(
        val pois: List<LocalMapPoi>,
        val skippedReasons: List<String>,
    )

    private data class WarpRecord(
        val index: Int,
        val x: Int,
        val y: Int,
        val destinationBaseAreaId: Int,
    )

    private data class BackgroundRecord(
        val index: Int,
        val offset: Int,
        val x: Int,
        val y: Int,
        val kind: Int,
    )

    private data class SignHeadline(
        val displayName: String? = null,
        val byTrainerGender: Map<Int, String> = emptyMap(),
    )

    private fun RomImage.s16le(offset: Int): Int = u16le(offset).let { if (it and 0x8000 != 0) it - 0x10000 else it }

    private fun Int.hex4(): String = toString(16).padStart(4, '0')

    private const val MAP_EVENTS_OFFSET = 0x04
    private const val OBJECT_COUNT_OFFSET = 0x00
    private const val WARP_COUNT_OFFSET = 0x01
    private const val BG_COUNT_OFFSET = 0x03
    private const val OBJECT_POINTER_OFFSET = 0x04
    private const val WARP_POINTER_OFFSET = 0x08
    private const val BG_POINTER_OFFSET = 0x10
    private const val OBJECT_RECORD_BYTES = 0x18
    private const val WARP_RECORD_BYTES = 0x08
    private const val BG_RECORD_BYTES = 0x0C
    private const val BG_EVENT_HIDDEN_ITEM = 7
    private val BG_EVENT_SIGN_KINDS = 0..4
    private const val HIDDEN_ITEMS_FLAG_START = 1000
    private const val SCR_OP_CALL_STD = 0x09
    private const val SCR_OP_END = 0x02
    private const val SCR_OP_CALL_IF = 0x07
    private const val SCR_OP_LOAD_WORD = 0x0F
    private const val SCR_OP_SETORCOPYVAR = 0x1A
    private const val SCR_OP_COMPARE_VAR_TO_VALUE = 0x21
    private const val SCR_OP_LOCK_ALL = 0x69
    private const val SCR_OP_RELEASE_ALL = 0x6B
    private const val SCR_OP_CHECK_PLAYER_GENDER = 0xA0
    private const val COMPARISON_EQUAL = 1
    private const val STD_FIND_ITEM = 1
    private const val VAR_0x8000 = 0x8000
    private const val VAR_0x8001 = 0x8001
    private const val VAR_RESULT = 0x800D
    private val SCRIPT_VARIABLE_RANGE = 0x4000..0x40FF
    private const val FIND_ITEM_SCRIPT_BYTES = 12
    private const val SIMPLE_MSGBOX_BYTES = 8
    private const val COMPARE_VAR_TO_VALUE_BYTES = 5
    private const val CALL_IF_BYTES = 6
    private const val GENDER_SIGN_SCRIPT_BYTES = 26
    private const val MAX_MSGBOX_TYPE = 10
    private const val MAX_SIGN_TEXT_BYTES = 160
    private const val MIN_SIGN_HEADLINE_CHARS = 2
    private const val SIGN_ENTRANCE_MAX_DISTANCE = 2
    private val SIGN_LINE_BREAKS = setOf(0xFA, 0xFB, 0xFE)
    private const val EXT_CTRL_CODE_BEGIN = 0xFD
    private const val EXT_CTRL_CODE_PLAYER = 0x01
    private val WHITESPACE = Regex("\\s+")
}
