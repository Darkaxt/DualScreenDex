package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapNameDisposition
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoi
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiItem
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiTextObligation
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
            runCatching { readMapEvents(rom, header, map, mapsByBaseArea, family, codec) }
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
        mapsByBaseArea: Map<Int, LocalMap>,
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
                        textObligation = LocalMapPoiTextObligation.ITEM_NAME,
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
                        textObligation = if (
                            warp.destinationBaseAreaId == DYNAMIC_DESTINATION_BASE_AREA_ID ||
                            mapsByBaseArea[warp.destinationBaseAreaId]?.nameDisposition ==
                            LocalMapNameDisposition.CONTEXT_DEPENDENT
                        ) {
                            LocalMapPoiTextObligation.CONTEXTUAL_TEXT
                        } else {
                            LocalMapPoiTextObligation.DESTINATION_NAME
                        },
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
                            textObligation = LocalMapPoiTextObligation.ITEM_NAME,
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
                    val script = if (isSign) rom.gbaPointer(background.offset + 8) else null
                    val genderConditioned = script != null && script.toLong() + 2 <= rom.size &&
                        rom.u8(script) == SCR_OP_LOCK_ALL && rom.u8(script + 1) == SCR_OP_CHECK_PLAYER_GENDER
                    val structurallyDirect = script != null && findLinearSignMessage(rom, script, family) != null
                    val signHeadline = if (script != null && codec != null) {
                        readSignHeadline(rom, script, family, codec)
                    } else {
                        null
                    }
                    val obligation = when {
                        background.kind == BG_EVENT_SECRET_BASE -> LocalMapPoiTextObligation.NO_TEXT
                        !isSign -> LocalMapPoiTextObligation.UNRESOLVED
                        signHeadline?.contextual == true -> LocalMapPoiTextObligation.CONTEXTUAL_TEXT
                        genderConditioned -> LocalMapPoiTextObligation.GENDERED_DIRECT_TEXT
                        structurallyDirect -> LocalMapPoiTextObligation.DIRECT_TEXT
                        else -> LocalMapPoiTextObligation.UNRESOLVED
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
                            textObligation = obligation,
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
        family: EngineFamily,
        codec: PokemonTextCodec,
    ): SignHeadline? =
        readLinearSignHeadline(rom, script, family, codec)?.let { headline ->
            SignHeadline(
                displayName = headline.displayName.takeUnless { headline.contextual },
                contextual = headline.contextual,
            )
        } ?: readGenderConditionedSignHeadline(rom, script, codec)

    private fun readGenderConditionedSignHeadline(
        rom: RomImage,
        script: Int,
        codec: PokemonTextCodec,
    ): SignHeadline? {
        if (script.toLong() + GENDER_SIGN_MIN_SCRIPT_BYTES > rom.size.toLong()) return null
        if (rom.u8(script) != SCR_OP_LOCK_ALL || rom.u8(script + 1) != SCR_OP_CHECK_PLAYER_GENDER) return null
        var cursor = script + 2
        val names = linkedMapOf<Int, String>()
        var contextual = false
        repeat(2) {
            if (rom.u8(cursor) != SCR_OP_COMPARE_VAR_TO_VALUE || rom.u16le(cursor + 1) != VAR_RESULT) return null
            val gender = rom.u16le(cursor + 3)
            if (gender !in 0..1 || gender in names) return null
            cursor += COMPARE_VAR_TO_VALUE_BYTES
            if (rom.u8(cursor) !in CONDITIONAL_BRANCH_OPS || rom.u8(cursor + 1) != COMPARISON_EQUAL) return null
            val target = rom.gbaPointer(cursor + 2) ?: return null
            val headline = readSignBranchHeadline(rom, target, codec) ?: return null
            contextual = contextual || headline.contextual
            names[gender] = headline.displayName
            cursor += CONDITIONAL_BRANCH_BYTES
        }
        val terminalBytes = when (rom.u8(cursor)) {
            SCR_OP_END -> 1
            SCR_OP_RELEASE_ALL -> 2
            else -> return null
        }
        if (cursor.toLong() + terminalBytes > rom.size.toLong()) return null
        if (terminalBytes == 2 && rom.u8(cursor + 1) != SCR_OP_END) return null
        return SignHeadline(
            displayName = names.values.firstOrNull().takeUnless { contextual },
            byTrainerGender = names.takeUnless { contextual }.orEmpty(),
            contextual = contextual,
        )
    }

    private fun readSignBranchHeadline(
        rom: RomImage,
        script: Int,
        codec: PokemonTextCodec,
    ): DecodedSignHeadline? = readSimpleSignHeadline(rom, script, codec)
        ?: readSoundPrefacedSignHeadline(rom, script, codec)

    private fun readSoundPrefacedSignHeadline(
        rom: RomImage,
        script: Int,
        codec: PokemonTextCodec,
    ): DecodedSignHeadline? {
        if (script.toLong() + SOUND_PREFACED_SIGN_PREFIX_BYTES > rom.size.toLong()) return null
        if (rom.u8(script) != SCR_OP_SET_VAR || rom.u16le(script + 1) !in SPECIAL_VARIABLE_RANGE) return null
        if (rom.u16le(script + 3) in SCRIPT_VARIABLE_RANGE) return null
        if (rom.u8(script + 5) != SCR_OP_SPECIAL || rom.u8(script + 8) != SCR_OP_PLAY_SE) return null
        return readSimpleSignHeadline(rom, script + SOUND_PREFACED_SIGN_PREFIX_BYTES, codec)
    }

    private fun readLinearSignHeadline(
        rom: RomImage,
        script: Int,
        family: EngineFamily,
        codec: PokemonTextCodec,
    ): DecodedSignHeadline? = findLinearSignMessage(rom, script, family)?.let { message ->
        readTextHeadline(rom, message.text, codec)
    }

    private fun findLinearSignMessage(rom: RomImage, script: Int, family: EngineFamily): MessageReference? {
        val endExclusive = minOf(rom.size.toLong(), script.toLong() + MAX_SIGN_SCRIPT_PREFIX_BYTES).toInt()
        var cursor = script
        while (cursor < endExclusive) {
            readMessageReference(rom, cursor)?.let { return it }
            val instructionBytes = when (rom.u8(cursor)) {
                SCR_OP_LOCK_ALL,
                SCR_OP_LOCK,
                SCR_OP_FACE_PLAYER,
                -> 1
                SCR_OP_SET_VAR -> if (
                    cursor.toLong() + SET_VAR_BYTES <= endExclusive &&
                    rom.u16le(cursor + 1) in SPECIAL_VARIABLE_RANGE &&
                    rom.u16le(cursor + 3) !in SCRIPT_VARIABLE_RANGE
                ) {
                    SET_VAR_BYTES
                } else {
                    return null
                }
                SCR_OP_SPECIAL -> if (
                    cursor.toLong() + SPECIAL_BYTES <= endExclusive &&
                    rom.u16le(cursor + 1) in allowedPreludeSpecials(family)
                ) {
                    SPECIAL_BYTES
                } else {
                    return null
                }
                SCR_OP_CALL -> if (
                    cursor.toLong() + CALL_BYTES <= endExclusive &&
                    rom.gbaPointer(cursor + 1)?.let { isMessageFreeReturningPrelude(rom, it, family) } == true
                ) {
                    CALL_BYTES
                } else {
                    return null
                }
                SCR_OP_APPLY_MOVEMENT -> if (
                    cursor.toLong() + APPLY_MOVEMENT_BYTES <= endExclusive && rom.gbaPointer(cursor + 3) != null
                ) {
                    APPLY_MOVEMENT_BYTES
                } else {
                    return null
                }
                SCR_OP_WAIT_MOVEMENT -> if (cursor.toLong() + WAIT_MOVEMENT_BYTES <= endExclusive) {
                    WAIT_MOVEMENT_BYTES
                } else {
                    return null
                }
                SCR_OP_SHOW_MON_PIC -> if (
                    family == EngineFamily.FIRERED_LEAFGREEN && cursor.toLong() + SHOW_MON_PIC_BYTES <= endExclusive
                ) {
                    SHOW_MON_PIC_BYTES
                } else {
                    return null
                }
                SCR_OP_SHOW_MONEY_BOX -> if (cursor.toLong() + SHOW_MONEY_BOX_BYTES <= endExclusive) {
                    SHOW_MONEY_BOX_BYTES
                } else {
                    return null
                }
                SCR_OP_TEXT_COLOR -> if (
                    family == EngineFamily.FIRERED_LEAFGREEN && cursor.toLong() + TEXT_COLOR_BYTES <= endExclusive
                ) {
                    TEXT_COLOR_BYTES
                } else {
                    return null
                }
                else -> return null
            }
            cursor += instructionBytes
        }
        return null
    }

    private fun isMessageFreeReturningPrelude(rom: RomImage, script: Int, family: EngineFamily): Boolean {
        val endExclusive = minOf(rom.size.toLong(), script.toLong() + MAX_CALLEE_BYTES).toInt()
        var cursor = script
        while (cursor < endExclusive) {
            val instructionBytes = when (rom.u8(cursor)) {
                SCR_OP_RETURN -> return true
                SCR_OP_DO_TIME_BASED_EVENTS -> 1
                SCR_OP_SET_VAR -> if (
                    cursor.toLong() + SET_VAR_BYTES <= endExclusive &&
                    rom.u16le(cursor + 1) in SPECIAL_VARIABLE_RANGE &&
                    rom.u16le(cursor + 3) !in SCRIPT_VARIABLE_RANGE
                ) {
                    SET_VAR_BYTES
                } else {
                    return false
                }
                SCR_OP_SPECIAL -> if (
                    family in HOENN_FAMILIES && cursor.toLong() + SPECIAL_BYTES <= endExclusive &&
                    rom.u16le(cursor + 1) == SPECIAL_BUFFER_TRENDY_PHRASE
                ) {
                    SPECIAL_BYTES
                } else {
                    return false
                }
                else -> return false
            }
            cursor += instructionBytes
        }
        return false
    }

    private fun readMessageReference(rom: RomImage, script: Int): MessageReference? = when {
        isSimpleSignScript(rom, script) -> MessageReference(requireNotNull(rom.gbaPointer(script + 2)))
        script.toLong() + MESSAGE_BYTES <= rom.size.toLong() &&
            rom.u8(script) == SCR_OP_MESSAGE && rom.u8(script + 5) == SCR_OP_WAIT_MESSAGE ->
            rom.gbaPointer(script + 1)?.let(::MessageReference)
        script.toLong() + BRAILLE_MESSAGE_BYTES <= rom.size.toLong() &&
            rom.u8(script) == SCR_OP_BRAILLE_MESSAGE && rom.u8(script + 5) == SCR_OP_WAIT_BUTTON_PRESS ->
            rom.gbaPointer(script + 1)?.let(::MessageReference)
        else -> null
    }

    private fun allowedPreludeSpecials(family: EngineFamily): Set<Int> = when (family) {
        EngineFamily.RUBY_SAPPHIRE,
        EngineFamily.EMERALD,
        -> HOENN_LINEAR_SIGN_SPECIALS
        EngineFamily.FIRERED_LEAFGREEN -> FIRERED_LINEAR_SIGN_SPECIALS
        else -> emptySet()
    }

    private fun isSimpleSignScript(rom: RomImage, script: Int): Boolean =
        script.toLong() + SIMPLE_MSGBOX_BYTES <= rom.size.toLong() &&
            rom.u8(script) == SCR_OP_LOAD_WORD && rom.u8(script + 1) == 0 &&
            rom.gbaPointer(script + 2) != null && rom.u8(script + 6) == SCR_OP_CALL_STD &&
            rom.u8(script + 7) in 0..MAX_MSGBOX_TYPE

    private fun readSimpleSignHeadline(
        rom: RomImage,
        script: Int,
        codec: PokemonTextCodec,
    ): DecodedSignHeadline? {
        if (!isSimpleSignScript(rom, script)) return null
        return readTextHeadline(rom, requireNotNull(rom.gbaPointer(script + 2)), codec)
    }

    private fun readTextHeadline(
        rom: RomImage,
        text: Int,
        codec: PokemonTextCodec,
    ): DecodedSignHeadline? {
        val available = minOf(MAX_SIGN_TEXT_BYTES, rom.size - text)
        if (available <= 0) return null
        return decodeSignHeadline(rom.slice(text, available), codec)
    }

    private fun decodeSignHeadline(raw: ByteArray, codec: PokemonTextCodec): DecodedSignHeadline? {
        val rom = RomImage(raw)
        val output = StringBuilder()
        var cursor = 0
        var contextual = false
        var terminated = false
        while (cursor < rom.size) {
            val byte = rom.u8(cursor)
            if (byte == codec.terminator || byte in SIGN_LINE_BREAKS) {
                terminated = true
                break
            }
            if (byte == EXT_CTRL_CODE_BEGIN && cursor + 1 < rom.size &&
                rom.u8(cursor + 1) in RUNTIME_SUBSTITUTION_IDS
            ) {
                contextual = true
                output.append("{RUNTIME}")
                cursor += 2
                continue
            }
            val token = codec.decodeToken(rom, cursor, rom.size)
            cursor += token.byteCount
            when (token) {
                is PokemonTextToken.Glyph -> output.append(token.text)
                is PokemonTextToken.Whitespace -> output.append(token.text)
                is PokemonTextToken.Substitution -> {
                    contextual = true
                    output.append(token.text)
                }
                is PokemonTextToken.Control -> output.append(token.replacement)
                is PokemonTextToken.Invalid -> return null
                is PokemonTextToken.Terminator -> {
                    terminated = true
                    break
                }
            }
        }
        if (!terminated) return null
        val displayName = output.toString().replace(WHITESPACE, " ").trim()
            .takeIf { it.length >= MIN_SIGN_HEADLINE_CHARS } ?: return null
        return DecodedSignHeadline(displayName, contextual)
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

    private data class MessageReference(val text: Int)

    private data class SignHeadline(
        val displayName: String? = null,
        val byTrainerGender: Map<Int, String> = emptyMap(),
        val contextual: Boolean = false,
    )

    private data class DecodedSignHeadline(
        val displayName: String,
        val contextual: Boolean,
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
    private const val BG_EVENT_SECRET_BASE = 8
    private const val DYNAMIC_DESTINATION_BASE_AREA_ID = 0x7f7f
    private val BG_EVENT_SIGN_KINDS = 0..4
    private const val HIDDEN_ITEMS_FLAG_START = 1000
    private const val SCR_OP_CALL_STD = 0x09
    private const val SCR_OP_END = 0x02
    private const val SCR_OP_RETURN = 0x03
    private const val SCR_OP_CALL = 0x04
    private const val SCR_OP_GOTO_IF = 0x06
    private const val SCR_OP_CALL_IF = 0x07
    private const val SCR_OP_LOAD_WORD = 0x0F
    private const val SCR_OP_SETORCOPYVAR = 0x1A
    private const val SCR_OP_SET_VAR = 0x16
    private const val SCR_OP_SPECIAL = 0x25
    private const val SCR_OP_DO_TIME_BASED_EVENTS = 0x2D
    private const val SCR_OP_PLAY_SE = 0x2F
    private const val SCR_OP_APPLY_MOVEMENT = 0x4F
    private const val SCR_OP_WAIT_MOVEMENT = 0x51
    private const val SCR_OP_COMPARE_VAR_TO_VALUE = 0x21
    private const val SCR_OP_FACE_PLAYER = 0x5A
    private const val SCR_OP_WAIT_MESSAGE = 0x66
    private const val SCR_OP_MESSAGE = 0x67
    private const val SCR_OP_LOCK_ALL = 0x69
    private const val SCR_OP_LOCK = 0x6A
    private const val SCR_OP_RELEASE_ALL = 0x6B
    private const val SCR_OP_WAIT_BUTTON_PRESS = 0x6D
    private const val SCR_OP_SHOW_MON_PIC = 0x75
    private const val SCR_OP_BRAILLE_MESSAGE = 0x78
    private const val SCR_OP_SHOW_MONEY_BOX = 0x93
    private const val SCR_OP_CHECK_PLAYER_GENDER = 0xA0
    private const val SCR_OP_TEXT_COLOR = 0xC7
    private const val COMPARISON_EQUAL = 1
    private const val STD_FIND_ITEM = 1
    private const val VAR_0x8000 = 0x8000
    private const val VAR_0x8001 = 0x8001
    private const val VAR_RESULT = 0x800D
    private val CONDITIONAL_BRANCH_OPS = setOf(SCR_OP_GOTO_IF, SCR_OP_CALL_IF)
    private val SPECIAL_VARIABLE_RANGE = 0x8000..0x800F
    private val SCRIPT_VARIABLE_RANGE = 0x4000..0x40FF
    private val HOENN_FAMILIES = setOf(EngineFamily.RUBY_SAPPHIRE, EngineFamily.EMERALD)
    private val HOENN_LINEAR_SIGN_SPECIALS = setOf(0x77, 0x79)
    private val FIRERED_LINEAR_SIGN_SPECIALS = setOf(0x163, 0x173, 0x18B)
    private const val SPECIAL_BUFFER_TRENDY_PHRASE = 0x7E
    private const val FIND_ITEM_SCRIPT_BYTES = 12
    private const val SIMPLE_MSGBOX_BYTES = 8
    private const val MESSAGE_BYTES = 6
    private const val BRAILLE_MESSAGE_BYTES = 6
    private const val SET_VAR_BYTES = 5
    private const val SPECIAL_BYTES = 3
    private const val CALL_BYTES = 5
    private const val APPLY_MOVEMENT_BYTES = 7
    private const val WAIT_MOVEMENT_BYTES = 3
    private const val SHOW_MON_PIC_BYTES = 5
    private const val SHOW_MONEY_BOX_BYTES = 4
    private const val TEXT_COLOR_BYTES = 2
    private const val MAX_SIGN_SCRIPT_PREFIX_BYTES = 48
    private const val MAX_CALLEE_BYTES = 24
    private const val SOUND_PREFACED_SIGN_PREFIX_BYTES = 11
    private const val COMPARE_VAR_TO_VALUE_BYTES = 5
    private const val CONDITIONAL_BRANCH_BYTES = 6
    private const val GENDER_SIGN_MIN_SCRIPT_BYTES = 25
    private const val MAX_MSGBOX_TYPE = 10
    private const val MAX_SIGN_TEXT_BYTES = 160
    private const val MIN_SIGN_HEADLINE_CHARS = 2
    private const val SIGN_ENTRANCE_MAX_DISTANCE = 2
    private val SIGN_LINE_BREAKS = setOf(0xFA, 0xFB, 0xFE)
    private const val EXT_CTRL_CODE_BEGIN = 0xFD
    private val RUNTIME_SUBSTITUTION_IDS = 0x01..0x06
    private val WHITESPACE = Regex("\\s+")
}
