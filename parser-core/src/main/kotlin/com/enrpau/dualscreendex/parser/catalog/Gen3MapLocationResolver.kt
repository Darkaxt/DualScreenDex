package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

/** Resolves semantic map identities from compiled map-header consumers and region-map data. */
object Gen3MapLocationResolver {
    fun resolve(
        rom: RomImage,
        encounterBaseIds: Set<Int>,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): Map<Int, String> {
        cancellation.throwIfCancellationRequested()
        val requiredMaps = requiredMaps(encounterBaseIds)
        if (requiredMaps.isEmpty()) return emptyMap()
        val compiled = findCompiledMapGroupsConsumerRoots(rom, cancellation).filter { layout ->
            enumerateRequiredMapHeaders(rom, layout, requiredMaps, cancellation) != null
        }
        val layout = if (compiled.isNotEmpty()) {
            compiled.singleOrNull()
        } else {
            findMapGroupsRoots(rom, requiredMaps, cancellation).singleOrNull()?.let(::MapGroupsLayout)
        } ?: return emptyMap()
        return resolveFromRoot(rom, layout, codec, cancellation).namesByBaseArea
    }

    fun resolve(
        rom: RomImage,
        encounterBaseIds: Set<Int>,
        references: GbaReferenceIndex,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): Map<Int, String> {
        val names = resolveNamesBySection(rom, encounterBaseIds, references, codec, cancellation)
        return resolveHeaderByBaseArea(rom, encounterBaseIds, references, cancellation).mapNotNull { (area, header) ->
            names[rom.u8(header + REGION_SECTION_OFFSET)]?.let { area to it }
        }.toMap()
    }

    internal fun resolveNamesBySection(
        rom: RomImage,
        encounterBaseIds: Set<Int>,
        references: GbaReferenceIndex,
        codec: PokemonTextCodec?,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
        extentLimit: Long = com.enrpau.dualscreendex.parser.analysis.ResolutionLimits().maxDatasetExtentBytes,
    ): Map<Int, String> {
        cancellation.throwIfCancellationRequested()
        if (codec == null) return emptyMap()
        val entries = resolveDetailed(rom, encounterBaseIds, references, codec, cancellation)?.entriesBySection
        if (entries != null) return entries.mapNotNull { (section, entry) -> entry.displayName?.let { section to it } }.toMap()
        val sections = resolveHeaderByBaseArea(rom, encounterBaseIds, references, cancellation).values
            .map { rom.u8(it + REGION_SECTION_OFFSET) }.toSet()
        return com.enrpau.dualscreendex.parser.parse.CompiledRegionSectionNames.resolve(rom, references, sections, codec, cancellation, extentLimit)
    }

    internal fun resolveDetailed(
        rom: RomImage,
        encounterBaseIds: Set<Int>,
        references: GbaReferenceIndex,
        codec: PokemonTextCodec?,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): Gen3MapLocationResolution? {
        cancellation.throwIfCancellationRequested()
        val requiredMaps = requiredMaps(encounterBaseIds)
        if (requiredMaps.isEmpty()) return null
        val requiredSections = resolveMapSections(rom, requiredMaps, references, cancellation) ?: return null
        mapTrace(
            "map-sections count=${requiredSections.values.toSet().size} ids=${requiredSections.values.toSortedSet()}",
        )
        val regionEntries = findRegionEntryResolution(
            rom,
            requiredSections.values.toSet(),
            codec,
            cancellation,
        ) ?: return null
        val preferredSections = resolveHeaderByBaseArea(rom, encounterBaseIds, references, cancellation)
            .mapValues { (_, header) -> rom.u8(header + REGION_SECTION_OFFSET) }
        val enrichedSections = preferredSections.filterValues { section ->
            decodeRegionEntry(rom, regionEntries.root, section, codec, cancellation) != null
        }
        val sections = enrichedSections.takeIf { it.keys.containsAll(requiredSections.keys) } ?: requiredSections
        val entries = sections.values.toSet().mapNotNull { section ->
            cancellation.throwIfCancellationRequested()
            decodeRegionEntry(rom, regionEntries.root, section, codec, cancellation)?.let { section to it }
        }.toMap(linkedMapOf())
        return Gen3MapLocationResolution(sections, entries).takeIf { it.entriesBySection.isNotEmpty() }
    }

    internal fun resolveSectionByBaseArea(
        rom: RomImage,
        encounterBaseIds: Set<Int>,
        references: GbaReferenceIndex,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): Map<Int, Int> {
        cancellation.throwIfCancellationRequested()
        val requiredMaps = requiredMaps(encounterBaseIds)
        if (requiredMaps.isEmpty()) return emptyMap()
        return resolveMapSections(rom, requiredMaps, references, cancellation).orEmpty()
    }

    internal fun resolveHeaderByBaseArea(
        rom: RomImage,
        encounterBaseIds: Set<Int>,
        references: GbaReferenceIndex,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): Map<Int, Int> {
        cancellation.throwIfCancellationRequested()
        val requiredMaps = requiredMaps(encounterBaseIds)
        if (requiredMaps.isEmpty()) return emptyMap()
        val layout = selectMapGroupsLayout(rom, requiredMaps, references, cancellation) ?: return emptyMap()
        return enumeratePreferredMapHeaders(rom, layout, requiredMaps, cancellation).orEmpty()
    }

    internal fun resolveReachableHeaderByBaseArea(
        rom: RomImage,
        encounterBaseIds: Set<Int>,
        references: GbaReferenceIndex,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): Map<Int, Int> {
        cancellation.throwIfCancellationRequested()
        val requiredMaps = requiredMaps(encounterBaseIds)
        if (requiredMaps.isEmpty()) return emptyMap()
        val layout = selectMapGroupsLayout(rom, requiredMaps, references, cancellation) ?: return emptyMap()
        val required = enumerateRequiredMapHeaders(rom, layout, requiredMaps, cancellation) ?: return emptyMap()
        val complete = enumerateMapHeaders(rom, layout, cancellation)
            ?.takeIf { headers -> headers.keys.containsAll(required.keys) }
            ?: return required
        return enumerateReachableMapHeaders(rom, complete, required.keys, cancellation).orEmpty().also { reachable ->
            mapTrace(
                "map-groups root=0x${layout.root.toString(16)} reachable=${reachable.size}/${complete.size} " +
                    "from=${required.size} encounter maps",
            )
        }
    }

    private fun selectMapGroupsLayout(
        rom: RomImage,
        requiredMaps: Map<Int, Set<Int>>,
        references: GbaReferenceIndex,
        cancellation: ParserCancellationToken,
    ): MapGroupsLayout? {
        val compiledRoots = findCompiledMapGroupsConsumerRoots(rom, cancellation)
        if (compiledRoots.isNotEmpty()) {
            return compiledRoots.filter { candidate ->
                enumerateRequiredMapHeaders(rom, candidate, requiredMaps, cancellation) != null
            }.singleOrNull()
        }
        val roots = findMapGroupsRoots(rom, requiredMaps, cancellation)
        val maximumReferences = roots.maxOfOrNull(references::referenceCount)?.takeIf { it > 0 } ?: return null
        return roots.filter { references.referenceCount(it) == maximumReferences }
            .singleOrNull()
            ?.let(::MapGroupsLayout)
    }

    private fun resolveMapSections(
        rom: RomImage,
        requiredMaps: Map<Int, Set<Int>>,
        references: GbaReferenceIndex,
        cancellation: ParserCancellationToken,
    ): Map<Int, Int>? {
        val compiledRoots = findCompiledMapGroupsConsumerRoots(rom, cancellation)
        if (compiledRoots.isNotEmpty()) {
            val requiredCount = requiredMaps.values.sumOf { maps -> maps.size }
            val resolved = compiledRoots.mapNotNull { layout ->
                cancellation.throwIfCancellationRequested()
                enumerateRequiredMapSections(rom, layout, requiredMaps, cancellation)?.also { sections ->
                    mapTrace(
                        "map-groups root=0x${layout.root.toString(16)} " +
                            "resolved=${sections.size} required=$requiredCount",
                    )
                }
            }.distinct()
            mapTrace(
                "map-groups compiledRoots=${compiledRoots.joinToString { "0x${it.root.toString(16)}" }} " +
                    "validLayouts=${resolved.size} requiredGroups=${requiredMaps.keys.sorted()} " +
                    "maxMaps=${requiredMaps.mapValues { it.value.maxOrNull() }}",
            )
            return resolved.singleOrNull()
        }
        val roots = findMapGroupsRoots(rom, requiredMaps, cancellation)
        val maximumReferences = roots.maxOfOrNull(references::referenceCount)?.takeIf { it > 0 } ?: return null
        val root = roots.filter { references.referenceCount(it) == maximumReferences }.singleOrNull() ?: return null
        return enumerateMapSections(rom, MapGroupsLayout(root), cancellation)
    }

    /**
     * A complete, structurally valid gMapGroups catalog is authoritative even when a map has no
     * wild-encounter row. Sparse or relocated hacks retain the encounter-keyed fallback.
     */
    private fun enumeratePreferredMapHeaders(
        rom: RomImage,
        layout: MapGroupsLayout,
        requiredMaps: Map<Int, Set<Int>>,
        cancellation: ParserCancellationToken,
    ): Map<Int, Int>? {
        val required = enumerateRequiredMapHeaders(rom, layout, requiredMaps, cancellation) ?: return null
        val complete = enumerateMapHeaders(rom, layout, cancellation)
        return complete?.takeIf { headers -> headers.keys.containsAll(required.keys) } ?: required
    }

    /**
     * Keeps the complete statically playable map graph rather than packaging unreferenced editor,
     * cut-scene, and retired layouts. The graph starts at every encounter-backed map and follows
     * the source-defined WarpEvent and MapConnection destinations in their gameplay direction.
     */
    private fun enumerateReachableMapHeaders(
        rom: RomImage,
        headers: Map<Int, Int>,
        startingMaps: Set<Int>,
        cancellation: ParserCancellationToken,
    ): Map<Int, Int>? {
        if (startingMaps.isEmpty() || !headers.keys.containsAll(startingMaps)) return null
        val reached = linkedSetOf<Int>()
        val pending = ArrayDeque<Int>()
        startingMaps.sorted().forEach { baseAreaId ->
            reached += baseAreaId
            pending += baseAreaId
        }
        while (pending.isNotEmpty()) {
            cancellation.throwIfCancellationRequested()
            val baseAreaId = pending.removeFirst()
            val destinations = staticMapDestinations(
                rom,
                headers.getValue(baseAreaId),
                headers.keys,
                cancellation,
            ) ?: return null
            destinations.sorted().forEach { destination ->
                if (reached.add(destination)) pending += destination
            }
        }
        return reached.associateWithTo(linkedMapOf()) { headers.getValue(it) }
    }

    private fun staticMapDestinations(
        rom: RomImage,
        header: Int,
        knownMaps: Set<Int>,
        cancellation: ParserCancellationToken,
    ): Set<Int>? {
        cancellation.throwIfCancellationRequested()
        val destinations = linkedSetOf<Int>()
        val events = rom.gbaPointer(header + MAP_EVENTS_OFFSET)
        if (events != null) {
            if (events.toLong() + MAP_EVENTS_BYTES > rom.size.toLong()) return null
            val warpCount = rom.u8(events + MAP_EVENTS_WARP_COUNT_OFFSET)
            if (warpCount > 0) {
                val warps = rom.gbaPointer(events + MAP_EVENTS_WARPS_OFFSET) ?: return null
                if (warps.toLong() + warpCount.toLong() * WARP_EVENT_BYTES > rom.size.toLong()) return null
                repeat(warpCount) { index ->
                    cancellation.throwIfCancellationRequested()
                    val warp = warps + index * WARP_EVENT_BYTES
                    val destination = (rom.u8(warp + WARP_EVENT_GROUP_OFFSET) shl 8) or
                        rom.u8(warp + WARP_EVENT_MAP_OFFSET)
                    if (destination in knownMaps) destinations += destination
                }
            }
        }
        val connections = rom.gbaPointer(header + MAP_CONNECTIONS_OFFSET)
        if (connections != null) {
            if (connections.toLong() + MAP_CONNECTIONS_BYTES > rom.size.toLong()) return null
            val count = rom.u32le(connections)
            if (count > MAX_MAP_CONNECTIONS.toLong()) return null
            if (count > 0) {
                val entries = rom.gbaPointer(connections + MAP_CONNECTIONS_ENTRIES_OFFSET) ?: return null
                if (entries.toLong() + count * MAP_CONNECTION_BYTES > rom.size.toLong()) return null
                repeat(count.toInt()) { index ->
                    cancellation.throwIfCancellationRequested()
                    val connection = entries + index * MAP_CONNECTION_BYTES
                    val destination = (rom.u8(connection + MAP_CONNECTION_GROUP_OFFSET) shl 8) or
                        rom.u8(connection + MAP_CONNECTION_MAP_OFFSET)
                    if (destination in knownMaps) destinations += destination
                }
            }
        }
        return destinations
    }

    private fun enumerateRequiredMapSections(
        rom: RomImage,
        layout: MapGroupsLayout,
        requiredMaps: Map<Int, Set<Int>>,
        cancellation: ParserCancellationToken,
    ): Map<Int, Int>? = enumerateRequiredMapHeaders(rom, layout, requiredMaps, cancellation)
        ?.mapValues { (_, header) ->
            rom.u8(header + REGION_SECTION_OFFSET)
        }

    private fun enumerateRequiredMapHeaders(
        rom: RomImage,
        layout: MapGroupsLayout,
        requiredMaps: Map<Int, Set<Int>>,
        cancellation: ParserCancellationToken,
    ): Map<Int, Int>? {
        val requiredCount = requiredMaps.values.sumOf(Set<Int>::size)
        val headers = linkedMapOf<Int, Int>()
        var unbindableHeaders = 0
        requiredMaps.toSortedMap().forEach groupLoop@{ (group, maps) ->
            cancellation.throwIfCancellationRequested()
            val groupPointerOffset = layout.root.toLong() + group.toLong() * 4L
            if (groupPointerOffset < 0 || groupPointerOffset + 4L > rom.size.toLong()) {
                return@groupLoop
            }
            val groupRoot = decodeGroupRoot(rom, layout, group) ?: return@groupLoop
            maps.sorted().forEach mapLoop@{ map ->
                cancellation.throwIfCancellationRequested()
                val headerPointerOffset = groupRoot.toLong() + map.toLong() * 4L
                if (headerPointerOffset < 0 || headerPointerOffset + 4L > rom.size.toLong()) {
                    return@mapLoop
                }
                val rawHeader = rom.u32le(headerPointerOffset.toInt())
                val header = rom.gbaPointer(headerPointerOffset.toInt())
                if (header == null) {
                    if (rawHeader != 0L) unbindableHeaders++
                    return@mapLoop
                }
                if (!validMapHeader(rom, header)) return@mapLoop
                headers[(group shl 8) or map] = header
            }
        }
        mapTrace(
            "map-groups root=0x${layout.root.toString(16)} bound=${headers.size}/$requiredCount " +
                "unbindable=$unbindableHeaders",
        )
        return headers.takeIf {
            it.isNotEmpty() && it.size + unbindableHeaders == requiredCount
        }
    }

    /**
     * Matches the source consumer `gMapGroups[group][map]`, including the compiler's u16
     * zero-extension and four-byte pointer indexing. The data arrays may be sparse or relocated;
     * only the required encounter keys are authoritative.
     */
    private fun findCompiledMapGroupsConsumerRoots(
        rom: RomImage,
        cancellation: ParserCancellationToken,
    ): List<MapGroupsLayout> = buildList {
        var offset = 0
        while (offset.toLong() + MAP_GROUP_LOOKUP_NARROW_BYTES <= rom.size.toLong()) {
            if (offset % CANCELLATION_CHECK_INTERVAL_BYTES == 0) {
                cancellation.throwIfCancellationRequested()
            }
            val u16Arguments = offset.toLong() + MAP_GROUP_LOOKUP_U16_BYTES <= rom.size.toLong() &&
                rom.u16le(offset) == THUMB_LSL_R0_16 &&
                rom.u16le(offset + 2) == THUMB_LSL_R1_16 &&
                isLiteralLoadR2(rom.u16le(offset + 4)) &&
                rom.u16le(offset + 6) == THUMB_LSR_R0_14 &&
                rom.u16le(offset + 8) == THUMB_ADD_R0_R0_R2 &&
                rom.u16le(offset + 10) == THUMB_LDR_R0_R0 &&
                rom.u16le(offset + 12) == THUMB_LSR_R1_14 &&
                rom.u16le(offset + 14) == THUMB_ADD_R1_R1_R0 &&
                rom.u16le(offset + 16) == THUMB_LDR_R0_R1 &&
                rom.u16le(offset + 18) == THUMB_BX_LR
            val indexedU16Arguments = isLiteralLoadR3(rom.u16le(offset)) &&
                rom.u16le(offset + 2) == THUMB_LSL_R0_16 &&
                rom.u16le(offset + 4) == THUMB_LSR_R0_14 &&
                rom.u16le(offset + 6) == THUMB_LDR_R3_R0_R3 &&
                rom.u16le(offset + 8) == THUMB_LSL_R1_16 &&
                rom.u16le(offset + 10) == THUMB_LSR_R1_14 &&
                rom.u16le(offset + 12) == THUMB_LDR_R0_R1_R3 &&
                rom.u16le(offset + 14) == THUMB_BX_LR
            val narrowArguments = isLiteralLoadR2(rom.u16le(offset)) &&
                rom.u16le(offset + 2) == THUMB_LSL_R0_2 &&
                rom.u16le(offset + 4) == THUMB_ADD_R0_R0_R2 &&
                rom.u16le(offset + 6) == THUMB_LDR_R0_R0 &&
                rom.u16le(offset + 8) == THUMB_LSL_R1_2 &&
                rom.u16le(offset + 10) == THUMB_ADD_R1_R1_R0 &&
                rom.u16le(offset + 12) == THUMB_LDR_R0_R1 &&
                rom.u16le(offset + 14) == THUMB_BX_LR
            if (u16Arguments || indexedU16Arguments || narrowArguments) {
                val literalOffset = if (u16Arguments) offset + 4 else offset
                val literalInstruction = rom.u16le(literalOffset)
                val literal = ((literalOffset + 4) and -4) + (literalInstruction and 0xff) * 4
                if (literal.toLong() + 4L <= rom.size.toLong()) {
                    rom.gbaPointer(literal)?.let { root -> add(MapGroupsLayout(root)) }
                }
            }
            encodedMapGroupsLayoutAt(rom, offset)?.let(::add)
            offset += 2
        }
    }.distinct()

    /**
     * Matches a source-compatible hardened `gMapGroups[group][map]` consumer where only the
     * group-array pointers are stored XOR-encoded. Both the table and key come from the decoded
     * consumer; map-header pointers remain ordinary ROM pointers and receive the normal structural
     * validation below.
     */
    private fun encodedMapGroupsLayoutAt(rom: RomImage, offset: Int): MapGroupsLayout? {
        if (offset.toLong() + MAP_GROUP_LOOKUP_ENCODED_BYTES > rom.size.toLong()) return null
        if (
            !isLiteralLoadR3(rom.u16le(offset)) ||
            rom.u16le(offset + 2) != THUMB_LSL_R0_2 ||
            rom.u16le(offset + 4) != THUMB_LDR_R2_R0_R3 ||
            !isLiteralLoadR3(rom.u16le(offset + 6)) ||
            rom.u16le(offset + 8) != THUMB_LSL_R1_2 ||
            rom.u16le(offset + 10) != THUMB_EOR_R3_R2 ||
            rom.u16le(offset + 12) != THUMB_LDR_R0_R1_R3 ||
            rom.u16le(offset + 14) != THUMB_BX_LR
        ) return null
        val tableLiteral = thumbLiteralOffset(offset, rom.u16le(offset)) ?: return null
        val keyLiteral = thumbLiteralOffset(offset + 6, rom.u16le(offset + 6)) ?: return null
        if (tableLiteral.toLong() + 4L > rom.size.toLong() || keyLiteral.toLong() + 4L > rom.size.toLong()) {
            return null
        }
        val root = rom.gbaPointer(tableLiteral) ?: return null
        val key = rom.u32le(keyLiteral)
        if (key == 0L) return null
        val layout = MapGroupsLayout(root, key)
        val first = decodeGroupRoot(rom, layout, 0) ?: return null
        val second = decodeGroupRoot(rom, layout, 1) ?: return null
        if (first == second) return null
        return layout
    }

    private fun thumbLiteralOffset(instructionOffset: Int, instruction: Int): Int? {
        val literal = ((instructionOffset + 4) and -4) + (instruction and 0xff) * 4
        return literal.takeIf { it >= 0 }
    }

    private fun decodeGroupRoot(rom: RomImage, layout: MapGroupsLayout, group: Int): Int? {
        val pointerOffset = layout.root.toLong() + group.toLong() * 4L
        if (pointerOffset < 0 || pointerOffset + 4L > rom.size.toLong()) return null
        val address = rom.u32le(pointerOffset.toInt()) xor layout.groupPointerXor
        val decoded = address - GBA_ROM_BASE
        return decoded.takeIf { it >= 0 && it + 4L <= rom.size.toLong() }?.toInt()
    }

    private fun isLiteralLoadR2(instruction: Int): Boolean =
        isLiteralLoad(instruction, 2)

    private fun isLiteralLoadR3(instruction: Int): Boolean =
        isLiteralLoad(instruction, 3)

    private fun isLiteralLoad(instruction: Int, register: Int): Boolean =
        instruction and THUMB_LITERAL_LOAD_MASK == THUMB_LITERAL_LOAD_OPCODE &&
            instruction ushr THUMB_REGISTER_SHIFT and THUMB_REGISTER_MASK == register

    private fun requiredMaps(encounterBaseIds: Set<Int>): Map<Int, Set<Int>> = encounterBaseIds
        .filter { it in 0..0xFFFF }
        .groupBy({ it ushr 8 }, { it and 0xFF })
        .mapValues { (_, maps) -> maps.toSet() }

    private fun resolveFromRoot(
        rom: RomImage,
        layout: MapGroupsLayout,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
    ): Gen3MapLocationResolution {
        val sections = enumerateMapSections(rom, layout, cancellation).orEmpty()
        val entries = findRegionEntries(rom, sections.values.toSet(), codec, cancellation).orEmpty()
        return Gen3MapLocationResolution(sections, entries)
    }

    private fun findMapGroupsRoots(
        rom: RomImage,
        requiredMaps: Map<Int, Set<Int>>,
        cancellation: ParserCancellationToken,
    ): List<Int> {
        val maxGroup = requiredMaps.keys.maxOrNull() ?: return emptyList()
        val roots = mutableListOf<Int>()
        var root = 0
        val last = rom.size - (maxGroup + 1) * 4
        while (root <= last) {
            if (root % CANCELLATION_CHECK_INTERVAL_BYTES == 0) {
                cancellation.throwIfCancellationRequested()
            }
            val valid = requiredMaps.all { (group, maps) ->
                val groupRoot = rom.gbaPointer(root + group * 4) ?: return@all false
                maps.all { map ->
                    val pointerOffset = groupRoot.toLong() + map.toLong() * 4
                    pointerOffset + 4 <= rom.size.toLong() &&
                        rom.gbaPointer(pointerOffset.toInt())?.let { validMapHeader(rom, it) } == true
                }
            }
            if (valid && enumerateMapSections(rom, MapGroupsLayout(root), cancellation) != null) roots += root
            root += 4
        }
        return roots
    }

    private fun enumerateMapSections(
        rom: RomImage,
        layout: MapGroupsLayout,
        cancellation: ParserCancellationToken,
    ): Map<Int, Int>? = enumerateMapHeaders(rom, layout, cancellation)
        ?.mapValues { (_, header) -> rom.u8(header + REGION_SECTION_OFFSET) }

    private fun enumerateMapHeaders(
        rom: RomImage,
        layout: MapGroupsLayout,
        cancellation: ParserCancellationToken,
    ): Map<Int, Int>? {
        cancellation.throwIfCancellationRequested()
        if (layout.groupPointerXor != 0L) return enumerateEncodedMapHeaders(rom, layout, cancellation)
        val groupRoots = mutableListOf<Int>()
        var cursor = layout.root
        while (cursor + 4 <= rom.size) {
            cancellation.throwIfCancellationRequested()
            val groupRoot = rom.gbaPointer(cursor) ?: break
            groupRoots += groupRoot
            cursor += 4
        }
        if (groupRoots.isEmpty() || groupRoots.distinct().size != groupRoots.size) return null
        val boundaries = (groupRoots + layout.root).distinct().sorted()
        val headers = linkedMapOf<Int, Int>()
        groupRoots.forEachIndexed { group, groupRoot ->
            cancellation.throwIfCancellationRequested()
            val end = boundaries.firstOrNull { it > groupRoot } ?: return null
            if (end <= groupRoot || (end - groupRoot) % 4 != 0) return null
            val mapCount = (end - groupRoot) / 4
            if (mapCount <= 0) return null
            repeat(mapCount) { map ->
                cancellation.throwIfCancellationRequested()
                val header = rom.gbaPointer(groupRoot + map * 4) ?: return null
                if (!validMapHeader(rom, header)) return null
                headers[(group shl 8) or map] = header
            }
        }
        return headers
    }

    private fun enumerateEncodedMapHeaders(
        rom: RomImage,
        layout: MapGroupsLayout,
        cancellation: ParserCancellationToken,
    ): Map<Int, Int>? {
        val groupRoots = mutableListOf<Pair<Int, Int>>()
        var group = 0
        while (group < MAX_MAP_GROUPS) {
            cancellation.throwIfCancellationRequested()
            val groupRoot = decodeGroupRoot(rom, layout, group) ?: break
            groupRoots += group to groupRoot
            group++
        }
        if (groupRoots.isEmpty() || groupRoots.map { it.second }.distinct().size != groupRoots.size) return null
        val headers = linkedMapOf<Int, Int>()
        groupRoots.forEach { (groupIndex, groupRoot) ->
            cancellation.throwIfCancellationRequested()
            var added = 0
            for (map in 0 until MAX_MAPS_PER_GROUP) {
                cancellation.throwIfCancellationRequested()
                val pointerOffset = groupRoot.toLong() + map.toLong() * 4L
                if (pointerOffset < 0 || pointerOffset + 4L > rom.size.toLong()) break
                val header = rom.gbaPointer(pointerOffset.toInt()) ?: break
                if (!validMapHeader(rom, header)) break
                headers[(groupIndex shl 8) or map] = header
                added++
            }
            if (added == 0) return null
        }
        return headers.takeIf { it.isNotEmpty() }
    }

    private fun validMapHeader(rom: RomImage, offset: Int): Boolean {
        if (offset < 0 || offset.toLong() + MAP_HEADER_BYTES > rom.size.toLong()) return false
        val layout = rom.gbaPointer(offset) ?: return false
        for (pointerOffset in listOf(offset + 4, offset + 8, offset + 12)) {
            val raw = rom.u32le(pointerOffset)
            if (raw != 0L && rom.gbaPointer(pointerOffset) == null) return false
        }
        val events = rom.gbaPointer(offset + 4)
        if (events != null && layout == events) return false
        return rom.u16le(offset + 0x12) != 0
    }

    private fun findRegionEntries(
        rom: RomImage,
        sectionIds: Set<Int>,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
    ): Map<Int, Gen3RegionMapEntry>? =
        findRegionEntryResolution(rom, sectionIds, codec, cancellation)?.entries

    private fun findRegionEntryResolution(
        rom: RomImage,
        sectionIds: Set<Int>,
        codec: PokemonTextCodec?,
        cancellation: ParserCancellationToken,
    ): RegionEntryResolution? {
        cancellation.throwIfCancellationRequested()
        if (sectionIds.isEmpty()) return null
        val maxSection = sectionIds.maxOrNull() ?: return null
        val requiredAnchorCount = minOf(REGION_ENTRY_ANCHORS, sectionIds.size)
        val provisionalAnchors = sectionIds.sortedDescending().take(requiredAnchorCount)
        val candidates = linkedMapOf<Int, Map<Int, Gen3RegionMapEntry>>()
        var root = 0
        val last = rom.size - (maxSection + 1) * REGION_ENTRY_BYTES
        while (root <= last) {
            if (root % CANCELLATION_CHECK_INTERVAL_BYTES == 0) {
                cancellation.throwIfCancellationRequested()
            }
            if (provisionalAnchors.all {
                    emptyRegionEntry(rom, root, it) ||
                        decodableRegionEntry(rom, root, it, codec, cancellation)
                } &&
                sectionIds.all {
                    emptyRegionEntry(rom, root, it) ||
                        validRegionEntryShell(rom, root, it, codec, cancellation)
                }
            ) {
                val authoritativeAnchors = sectionIds.asSequence()
                    .filter { validRegionEntry(rom, root, it, codec, cancellation) }
                    .take(requiredAnchorCount)
                    .toList()
                if (authoritativeAnchors.size == requiredAnchorCount) {
                    candidates[root] = sectionIds.mapNotNull { section ->
                        cancellation.throwIfCancellationRequested()
                        decodeRegionEntry(rom, root, section, codec, cancellation)?.let { section to it }
                    }.toMap()
                }
            }
            root += 4
        }
        if (candidates.isEmpty()) return null
        val referenceCounts = candidates.keys.associateWith { 0 }.toMutableMap()
        var pointerOffset = 0
        while (pointerOffset <= rom.size - 4) {
            if (pointerOffset % CANCELLATION_CHECK_INTERVAL_BYTES == 0) {
                cancellation.throwIfCancellationRequested()
            }
            val target = (rom.u32le(pointerOffset) - GBA_ROM_BASE).toInt()
            if (target in referenceCounts) referenceCounts[target] = referenceCounts.getValue(target) + 1
            pointerOffset += 4
        }
        val maximumReferences = referenceCounts.values.maxOrNull()?.takeIf { it > 0 } ?: return null
        val winners = referenceCounts.filterValues { it == maximumReferences }.keys
        mapTrace(
            "region-entries candidates=${candidates.size} maxReferences=$maximumReferences " +
                "winners=${winners.joinToString { "0x${it.toString(16)}" }}",
        )
        val winner = winners.singleOrNull() ?: return null
        return RegionEntryResolution(winner, candidates.getValue(winner))
    }

    private fun emptyRegionEntry(rom: RomImage, root: Int, sectionId: Int): Boolean {
        val offset = root.toLong() + sectionId.toLong() * REGION_ENTRY_BYTES
        if (offset < 0 || offset + REGION_ENTRY_BYTES > rom.size.toLong()) return false
        return (0 until REGION_ENTRY_BYTES).all { byte ->
            rom.u8(offset.toInt() + byte) == 0
        }
    }

    private fun decodableRegionEntry(
        rom: RomImage,
        root: Int,
        sectionId: Int,
        codec: PokemonTextCodec?,
        cancellation: ParserCancellationToken,
    ): Boolean = validRegionEntryShell(rom, root, sectionId, codec, cancellation) &&
        decodeRegionEntry(rom, root, sectionId, codec, cancellation) != null

    private fun validRegionEntry(
        rom: RomImage,
        root: Int,
        sectionId: Int,
        codec: PokemonTextCodec?,
        cancellation: ParserCancellationToken,
    ): Boolean {
        val offset = root + sectionId * REGION_ENTRY_BYTES
        return validRegionEntryShell(rom, root, sectionId, codec, cancellation) &&
            !offMapCoordinates(
                rom.u8(offset),
                rom.u8(offset + 1),
            ) &&
            (codec == null || decodeRegionEntry(rom, root, sectionId, codec, cancellation)
                ?.displayName
                ?.any(Char::isLetterOrDigit) == true)
    }

    private fun validRegionEntryShell(
        rom: RomImage,
        root: Int,
        sectionId: Int,
        codec: PokemonTextCodec?,
        cancellation: ParserCancellationToken,
    ): Boolean {
        cancellation.throwIfCancellationRequested()
        val offset = root + sectionId * REGION_ENTRY_BYTES
        val x = rom.u8(offset)
        val y = rom.u8(offset + 1)
        val width = rom.u8(offset + 2)
        val height = rom.u8(offset + 3)
        val offMap = offMapCoordinates(x, y)
        if (
            !offMap &&
            (x >= REGION_GRID_WIDTH ||
                y >= REGION_GRID_HEIGHT)
        ) return false
        if (width !in 1..REGION_GRID_WIDTH || height !in 1..REGION_GRID_HEIGHT) return false
        if (
            !offMap &&
            (x + width > REGION_GRID_WIDTH ||
                y + height > REGION_GRID_HEIGHT)
        ) return false
        val text = rom.gbaPointer(offset + 4) ?: return false
        val available = minOf(MAX_REGION_NAME_BYTES, rom.size - text)
        return available > 0 && (codec == null || codec.decodeDetailed(
            rom = rom,
            offset = text,
            maximumBytes = available,
            cancellation = cancellation,
        ).terminated)
    }

    private fun offMapCoordinates(x: Int, y: Int): Boolean =
        x == OFF_MAP_COORDINATE &&
            y == OFF_MAP_COORDINATE

    private fun decodeRegionEntry(
        rom: RomImage,
        root: Int,
        sectionId: Int,
        codec: PokemonTextCodec?,
        cancellation: ParserCancellationToken,
    ): Gen3RegionMapEntry? {
        if (!validRegionEntryShell(rom, root, sectionId, codec, cancellation)) return null
        val offset = root + sectionId * REGION_ENTRY_BYTES
        val name = codec?.let {
            val text = rom.gbaPointer(offset + 4) ?: return null
            val available = minOf(MAX_REGION_NAME_BYTES, rom.size - text)
            val decoded = it.decodeDetailed(
                rom = rom,
                offset = text,
                maximumBytes = available,
                cancellation = cancellation,
            )
            decoded.text.takeIf { value ->
                decoded.terminated && decoded.validRatio >= MIN_TEXT_RATIO && value.isNotBlank()
            }
        }
        return Gen3RegionMapEntry(
            sectionId,
            rom.u8(offset),
            rom.u8(offset + 1),
            rom.u8(offset + 2),
            rom.u8(offset + 3),
            name,
        )
    }

    private fun mapTrace(message: String) {
        if (System.getenv("DUALDEX_MAP_TRACE") == "1") {
            println("world-map-trace $message")
        }
    }

    private const val MAP_HEADER_BYTES = 28
    private const val MAP_EVENTS_OFFSET = 4
    private const val MAP_CONNECTIONS_OFFSET = 12
    private const val MAP_EVENTS_BYTES = 20
    private const val MAP_EVENTS_WARP_COUNT_OFFSET = 1
    private const val MAP_EVENTS_WARPS_OFFSET = 8
    private const val WARP_EVENT_BYTES = 8
    private const val WARP_EVENT_GROUP_OFFSET = 5
    private const val WARP_EVENT_MAP_OFFSET = 6
    private const val MAP_CONNECTIONS_BYTES = 8
    private const val MAP_CONNECTIONS_ENTRIES_OFFSET = 4
    private const val MAP_CONNECTION_BYTES = 12
    private const val MAP_CONNECTION_GROUP_OFFSET = 8
    private const val MAP_CONNECTION_MAP_OFFSET = 9
    private const val MAX_MAP_CONNECTIONS = 256
    private const val CANCELLATION_CHECK_INTERVAL_BYTES = 4096
    private const val GBA_ROM_BASE = 0x08000000L
    private const val REGION_SECTION_OFFSET = 0x14
    private const val REGION_ENTRY_BYTES = 8
    private const val REGION_ENTRY_ANCHORS = 3
    private const val OFF_MAP_COORDINATE = 0xFF
    private const val REGION_GRID_WIDTH = 32
    private const val REGION_GRID_HEIGHT = 32
    private const val MAX_REGION_NAME_BYTES = 32
    private const val MIN_TEXT_RATIO = 0.85
    private const val MAP_GROUP_LOOKUP_U16_BYTES = 20L
    private const val MAP_GROUP_LOOKUP_NARROW_BYTES = 16L
    private const val MAP_GROUP_LOOKUP_ENCODED_BYTES = 16L
    private const val THUMB_LSL_R0_16 = 0x0400
    private const val THUMB_LSL_R1_16 = 0x0409
    private const val THUMB_LSR_R0_14 = 0x0B80
    private const val THUMB_LDR_R3_R0_R3 = 0x58C3
    private const val THUMB_LDR_R2_R0_R3 = 0x58C2
    private const val THUMB_LDR_R0_R1_R3 = 0x58C8
    private const val THUMB_EOR_R3_R2 = 0x4053
    private const val THUMB_LSL_R0_2 = 0x0080
    private const val THUMB_ADD_R0_R0_R2 = 0x1880
    private const val THUMB_LDR_R0_R0 = 0x6800
    private const val THUMB_LSR_R1_14 = 0x0B89
    private const val THUMB_LSL_R1_2 = 0x0089
    private const val THUMB_ADD_R1_R1_R0 = 0x1809
    private const val THUMB_LDR_R0_R1 = 0x6808
    private const val THUMB_BX_LR = 0x4770
    private const val THUMB_LITERAL_LOAD_MASK = 0xF800
    private const val THUMB_LITERAL_LOAD_OPCODE = 0x4800
    private const val THUMB_REGISTER_SHIFT = 8
    private const val THUMB_REGISTER_MASK = 0x7
    private const val MAX_MAP_GROUPS = 256
    private const val MAX_MAPS_PER_GROUP = 256
}

private data class MapGroupsLayout(
    val root: Int,
    val groupPointerXor: Long = 0L,
)

private data class RegionEntryResolution(
    val root: Int,
    val entries: Map<Int, Gen3RegionMapEntry>,
)

internal data class Gen3MapLocationResolution(
    val sectionByBaseArea: Map<Int, Int>,
    val entriesBySection: Map<Int, Gen3RegionMapEntry>,
) {
    val namesByBaseArea: Map<Int, String> = sectionByBaseArea.mapNotNull { (baseId, sectionId) ->
        entriesBySection[sectionId]?.displayName?.let { baseId to it }
    }.toMap(linkedMapOf())
}

internal data class Gen3RegionMapEntry(
    val sectionId: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val displayName: String?,
)
