package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoi
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiItem
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiOrganicVisibility
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiService
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextToken
import kotlin.math.abs

internal object Gen2LocalMapPoiResolver {
    fun resolve(
        rom: RomImage,
        sources: List<Source>,
        maps: List<LocalMap>,
        family: EngineFamily,
        codec: PokemonTextCodec?,
        limits: ResolutionLimits = ResolutionLimits(),
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): Resolution {
        cancellation.throwIfCancellationRequested()
        val declaration = if (codec == null) Gen2DeclaredSignAbi.Resolution(Gen2DeclaredSignAbi.Status.ABSENT)
            else Gen2DeclaredSignAbi.resolve(rom, sources, limits, cancellation)
        val mapsByBaseArea = maps.associateBy(LocalMap::baseAreaId)
        val sourcesByBaseArea = sources.associateBy(Source::baseAreaId)
        val pois = mutableListOf<LocalMapPoi>()
        val skipped = mutableListOf<String>()
        mapsByBaseArea.toSortedMap().forEach { (baseAreaId, map) ->
            val source = sourcesByBaseArea[baseAreaId] ?: return@forEach
            cancellation.throwIfCancellationRequested()
            runCatching { readMapPois(rom, source, map, mapsByBaseArea.keys, family, codec, declaration, cancellation) }
                .onSuccess(pois::addAll)
                .onFailure { failure -> cancellation.throwIfCancellationRequested(); skipped += "map 0x${baseAreaId.hex4()} POIs: ${failure.message}" }
        }
        return Resolution(pois.sortedBy(LocalMapPoi::key), skipped)
    }

    private fun readMapPois(
        rom: RomImage,
        source: Source,
        map: LocalMap,
        acceptedAreaIds: Set<Int>,
        family: EngineFamily,
        codec: PokemonTextCodec?,
        declaration: Gen2DeclaredSignAbi.Resolution,
        cancellation: ParserCancellationToken,
    ): List<LocalMapPoi> {
        val scriptsBank = rom.u8(source.attributes + SCRIPTS_BANK_OFFSET)
        val events = requireNotNull(
            rom.gbBankAddress(scriptsBank, rom.u16le(source.attributes + EVENTS_POINTER_OFFSET)),
        ) { "event pointer is outside its bank" }
        val bankLimit = bankEnd(rom, scriptsBank)
        require(events + EVENT_FILLER_BYTES < bankLimit) { "event root is truncated" }
        var cursor = events + EVENT_FILLER_BYTES

        val warpCount = rom.u8(cursor++)
        require(warpCount <= MAX_EVENTS_PER_KIND) { "resolved $warpCount warps" }
        require(cursor.toLong() + warpCount.toLong() * WARP_RECORD_BYTES <= bankLimit.toLong()) {
            "warp records are truncated"
        }
        val warps = List(warpCount) { index ->
            val row = cursor + index * WARP_RECORD_BYTES
            val destination = rom.u8(row + 3) shl 8 or rom.u8(row + 4)
            WarpRecord(
                index = index,
                x = rom.u8(row + 1),
                y = rom.u8(row),
                destinationBaseAreaId = destination.takeIf { it in acceptedAreaIds },
            )
        }
        cursor += warpCount * WARP_RECORD_BYTES

        require(cursor < bankLimit) { "coordinate event count is truncated" }
        val coordinateCount = rom.u8(cursor++)
        require(coordinateCount <= MAX_EVENTS_PER_KIND) { "resolved $coordinateCount coordinate events" }
        require(cursor.toLong() + coordinateCount.toLong() * COORDINATE_RECORD_BYTES <= bankLimit.toLong()) {
            "coordinate events are truncated"
        }
        cursor += coordinateCount * COORDINATE_RECORD_BYTES

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
                kind = rom.u8(row + 2),
                data = requireNotNull(rom.gbBankAddress(scriptsBank, rom.u16le(row + 3))) {
                    "background event $index has no banked data"
                },
            )
        }
        cursor += backgroundCount * BACKGROUND_RECORD_BYTES

        require(cursor < bankLimit) { "object event count is truncated" }
        val objectCount = rom.u8(cursor++)
        require(objectCount <= MAX_EVENTS_PER_KIND) { "resolved $objectCount object events" }
        require(cursor.toLong() + objectCount.toLong() * OBJECT_RECORD_BYTES <= bankLimit.toLong()) {
            "object events are truncated"
        }
        val objects = List(objectCount) { index ->
            val row = cursor + index * OBJECT_RECORD_BYTES
            val type = rom.u8(row + 7) and OBJECT_TYPE_MASK
            ObjectRecord(
                index = index,
                x = rom.u8(row + 2) - OBJECT_COORDINATE_BIAS,
                y = rom.u8(row + 1) - OBJECT_COORDINATE_BIAS,
                type = type,
                data = if (type == OBJECT_TYPE_ITEMBALL) {
                    requireNotNull(rom.gbBankAddress(scriptsBank, rom.u16le(row + 9))) {
                        "item object $index has no banked data"
                    }
                } else {
                    null
                },
                eventFlag = rom.u16le(row + 11).takeUnless { it == NO_EVENT_FLAG },
            )
        }

        val validWarps = warps.filter { it.inside(map) }
        val validBackgrounds = backgrounds.filter { it.inside(map) }
        val signs = validBackgrounds.filter { it.kind in SIGN_KINDS }
        val backgroundWarps = associateBackgroundsWithWarps(signs, validWarps)
        val representedWarpIndexes = backgroundWarps.values.mapTo(mutableSetOf(), WarpRecord::index)
        val forbidden = listOf(events until (cursor + objectCount * OBJECT_RECORD_BYTES), source.attributes until source.attributes + 12) +
            backgrounds.filter { it.kind in SIGN_KINDS }.map { it.data until it.data + 3 }
        // A selected declaration bounds its predecessor even when its own text is malformed.
        // Collect before decoding; distinct roots preserve legitimate same-record aliases.
        val declaredTextRoots = signs.mapNotNull { background ->
            declaredSignText(rom, scriptsBank, background.data, background.kind, declaration, forbidden)?.first
        }.distinct().sorted()
        return buildList {
            objects.forEach { objectEvent ->
                if (objectEvent.type != OBJECT_TYPE_ITEMBALL || !objectEvent.inside(map)) return@forEach
                val data = objectEvent.data ?: return@forEach
                require(data + ITEMBALL_DATA_BYTES <= bankEnd(rom, scriptsBank)) {
                    "item object ${objectEvent.index} data is truncated"
                }
                val itemId = rom.u8(data)
                val quantity = rom.u8(data + 1)
                if (itemId == 0 || quantity == 0) return@forEach
                add(
                    LocalMapPoi(
                        key = "${map.key}/object/${objectEvent.index}",
                        localMapKey = map.key,
                        baseAreaId = map.baseAreaId,
                        tileX = objectEvent.x,
                        tileY = objectEvent.y,
                        kind = LocalMapPoiKind.VISIBLE_ITEM,
                        item = LocalMapPoiItem(
                            itemId = itemId,
                            collectionFlagId = objectEvent.eventFlag,
                        ),
                    ),
                )
            }
            validWarps.filter { it.index !in representedWarpIndexes }.forEach { warp -> add(warp.toPoi(map)) }
            validBackgrounds.forEach { background ->
                if (background.kind == BGEVENT_ITEM) {
                    require(background.data + HIDDEN_ITEM_DATA_BYTES <= bankEnd(rom, scriptsBank)) {
                        "hidden item ${background.index} data is truncated"
                    }
                    val collectionFlag = rom.u16le(background.data)
                    val itemId = rom.u8(background.data + 2)
                    if (collectionFlag != NO_EVENT_FLAG && itemId != 0) {
                        add(
                            LocalMapPoi(
                                key = "${map.key}/bg/${background.index}",
                                localMapKey = map.key,
                                baseAreaId = map.baseAreaId,
                                tileX = background.x,
                                tileY = background.y,
                                kind = LocalMapPoiKind.HIDDEN_ITEM,
                                organicVisibility = LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE,
                                item = LocalMapPoiItem(itemId = itemId, collectionFlagId = collectionFlag),
                            ),
                        )
                    }
                } else if (background.kind in SIGN_KINDS) {
                    val semantics = readSignSemantics(rom, scriptsBank, background.data, family, codec, declaration, background.kind, forbidden, declaredTextRoots, cancellation)
                    val destination = backgroundWarps[background.index]
                    add(
                        LocalMapPoi(
                            key = "${map.key}/bg/${background.index}",
                            localMapKey = map.key,
                            baseAreaId = map.baseAreaId,
                            tileX = background.x,
                            tileY = background.y,
                            kind = if (semantics.service == null) LocalMapPoiKind.PLACE else LocalMapPoiKind.SERVICE,
                            organicVisibility = LocalMapPoiOrganicVisibility.ENTRANCE_PROXIMITY,
                            displayName = semantics.displayName,
                            service = semantics.service,
                            destinationBaseAreaId = destination?.destinationBaseAreaId,
                        ),
                    )
                } else {
                    add(
                        LocalMapPoi(
                            key = "${map.key}/bg/${background.index}",
                            localMapKey = map.key,
                            baseAreaId = map.baseAreaId,
                            tileX = background.x,
                            tileY = background.y,
                            kind = LocalMapPoiKind.UNKNOWN,
                        ),
                    )
                }
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

    private fun readSignSemantics(
        rom: RomImage,
        scriptsBank: Int,
        script: Int,
        family: EngineFamily,
        codec: PokemonTextCodec?,
        declaration: Gen2DeclaredSignAbi.Resolution,
        kind: Int,
        forbidden: List<IntRange>,
        declaredTextRoots: List<Int>,
        cancellation: ParserCancellationToken,
    ): SignSemantics {
        if (script + 3 > bankEnd(rom, scriptsBank)) return SignSemantics()
        if (rom.u8(script) == JUMP_STD_COMMAND) {
            return when (rom.u16le(script + 1)) {
                POKECENTER_SIGN_STD_INDEX -> SignSemantics(service = LocalMapPoiService.POKEMON_CENTER)
                MART_SIGN_STD_INDEX -> SignSemantics(service = LocalMapPoiService.MART)
                else -> SignSemantics()
            }
        }
        if (declaration.status != Gen2DeclaredSignAbi.Status.ABSENT) {
            // Only the compiled kind-0 direct declaration is in this bounded ABI. Terminal
            // incomplete/conflicting/budget evidence never re-enters family opcode decoding.
            if (codec == null) return SignSemantics()
            val (text, grammar) = declaredSignText(rom, scriptsBank, script, kind, declaration, forbidden) ?: return SignSemantics()
            val limit = minOf(bankEnd(rom, scriptsBank), text + MAX_SIGN_TEXT_BYTES,
                forbidden.map { it.first }.filter { it > text }.minOrNull() ?: rom.size,
                declaredTextRoots.firstOrNull { it > text } ?: rom.size)
            return SignSemantics(displayName = decodeDeclaredHeadline(rom, text, limit, codec, grammar, cancellation))
        }
        val jumpTextCommand = when (family) {
            EngineFamily.GOLD_SILVER -> GOLD_SILVER_JUMP_TEXT_COMMAND
            EngineFamily.CRYSTAL -> CRYSTAL_JUMP_TEXT_COMMAND
            else -> return SignSemantics()
        }
        if (rom.u8(script) != jumpTextCommand) return SignSemantics()
        val text = rom.gbBankAddress(scriptsBank, rom.u16le(script + 1)) ?: return SignSemantics()
        return SignSemantics(displayName = codec?.let { decodeHeadline(rom, text, it) })
    }

    private fun declaredSignText(
        rom: RomImage, scriptsBank: Int, script: Int, kind: Int,
        declaration: Gen2DeclaredSignAbi.Resolution, forbidden: List<IntRange>,
    ): Pair<Int, Gen2DeclaredSignAbi.Grammar>? {
        if (kind != 0 || script / BANK_BYTES != scriptsBank || script + 3 > bankEnd(rom, scriptsBank)) return null
        val grammar = declaration.abi?.grammar(rom.u8(script)) ?: return null
        val text = rom.gbBankAddress(scriptsBank, rom.u16le(script + 1)) ?: return null
        if (text / BANK_BYTES != scriptsBank || forbidden.any { text in it }) return null
        return text to grammar // No START/content/DONE check: root authority is the declaration.
    }

    private fun decodeDeclaredHeadline(
        rom: RomImage, offset: Int, limit: Int, codec: PokemonTextCodec,
        grammar: Gen2DeclaredSignAbi.Grammar, cancellation: ParserCancellationToken,
    ): String? {
        if (offset >= limit || rom.u8(offset) != grammar.start) return null
        val headline = StringBuilder()
        var firstLine = true
        var cursor = offset + 1
        while (cursor < limit) {
            cancellation.throwIfCancellationRequested()
            val value = rom.u8(cursor)
            if (value == grammar.done) return headline.toString().replace(WHITESPACE, " ").trim().takeIf { it.length >= MIN_SIGN_HEADLINE_CHARS }
            if (value == grammar.line) { firstLine = false; cursor++; continue }
            val token = codec.decodeToken(rom, cursor, limit)
            val expectedWidth = if (value in 1 until grammar.leadLimit) 2 else 1
            if (token.byteCount != expectedWidth || cursor + token.byteCount > limit) return null
            val text = when (token) {
                is PokemonTextToken.Glyph -> token.text
                is PokemonTextToken.Whitespace -> token.text
                is PokemonTextToken.Substitution -> token.text
                else -> return null // No unconditional Western controls/substitutions or codec terminator.
            }
            if (firstLine) headline.append(text)
            cursor += token.byteCount
        }
        return null // A LINE alone is not a complete declared text record.
    }

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

    data class Source(
        val baseAreaId: Int,
        val attributesBank: Int,
        val attributes: Int,
    )

    data class Resolution(
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

    private data class BackgroundRecord(
        val index: Int,
        val x: Int,
        val y: Int,
        val kind: Int,
        val data: Int,
    ) {
        fun inside(map: LocalMap): Boolean = x in 0 until map.gridWidth && y in 0 until map.gridHeight
    }

    private data class ObjectRecord(
        val index: Int,
        val x: Int,
        val y: Int,
        val type: Int,
        val data: Int?,
        val eventFlag: Int?,
    ) {
        fun inside(map: LocalMap): Boolean = x in 0 until map.gridWidth && y in 0 until map.gridHeight
    }

    private data class SignSemantics(
        val displayName: String? = null,
        val service: LocalMapPoiService? = null,
    )

    private fun bankEnd(rom: RomImage, bank: Int): Int =
        minOf(rom.size.toLong(), (bank.toLong() + 1L) * BANK_BYTES).toInt()

    private fun Int.hex4(): String = toString(16).padStart(4, '0')

    private const val BANK_BYTES = 0x4000
    private const val SCRIPTS_BANK_OFFSET = 6
    private const val EVENTS_POINTER_OFFSET = 9
    private const val EVENT_FILLER_BYTES = 2
    private const val WARP_RECORD_BYTES = 5
    private const val COORDINATE_RECORD_BYTES = 8
    private const val BACKGROUND_RECORD_BYTES = 5
    private const val OBJECT_RECORD_BYTES = 13
    private const val OBJECT_COORDINATE_BIAS = 4
    private const val OBJECT_TYPE_MASK = 0x0F
    private const val OBJECT_TYPE_ITEMBALL = 1
    private const val ITEMBALL_DATA_BYTES = 2
    private const val HIDDEN_ITEM_DATA_BYTES = 3
    private const val NO_EVENT_FLAG = 0xFFFF
    private const val BGEVENT_ITEM = 7
    private val SIGN_KINDS = 0..4
    private const val MAX_EVENTS_PER_KIND = 64
    private const val SIGN_ENTRANCE_MAX_DISTANCE = 3

    private const val JUMP_STD_COMMAND = 0x0C
    private const val POKECENTER_SIGN_STD_INDEX = 16
    private const val MART_SIGN_STD_INDEX = 17
    private const val GOLD_SILVER_JUMP_TEXT_COMMAND = 0x52
    private const val CRYSTAL_JUMP_TEXT_COMMAND = 0x53
    private const val TEXT_START = 0x00
    private const val TEXT_PLAYER = 0x52
    private const val TEXT_RIVAL = 0x53
    private const val TEXT_POKEMON = 0x54
    private const val TEXT_ELLIPSIS = 0x56
    private val SIGN_HEADLINE_ENDS = setOf(0x4C, 0x4E, 0x4F, 0x50, 0x51, 0x55, 0x57, 0x58)
    private const val MAX_SIGN_TEXT_BYTES = 96
    private const val MIN_SIGN_HEADLINE_CHARS = 2
    private val WHITESPACE = Regex("\\s+")
}
