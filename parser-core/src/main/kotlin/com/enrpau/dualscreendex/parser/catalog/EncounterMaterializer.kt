package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import java.util.LinkedHashMap

object EncounterMethods {
    const val GRASS = 1
    const val WATER = 2
    const val ROCK_SMASH = 3
    const val FISHING = 4
    const val GRASS_MORNING = 5
    const val GRASS_DAY = 6
    const val GRASS_NIGHT = 7
    const val HIDDEN = 8
}

internal data class EncounterMaterializationResult(
    val areas: List<EncounterArea>,
    val status: CapabilityStatus,
    val reasons: List<String>,
    val reviewStatus: CapabilityReviewStatus = CapabilityReviewStatus.NONE,
    val probeStats: EncounterProbeStats = EncounterProbeStats(),
    val selectedRootOffset: Int? = null,
    val headerSize: Int? = null,
    val headerCount: Int? = null,
    val populatedMethodCount: Int? = null,
    val referenceCount: Int? = null,
    val candidateCount: Int = 0,
)

internal data class EncounterProbeStats(val emptyClassicShellWalks: Int = 0)

object EncounterMaterializer {
    fun materialize(rom: RomImage, layout: ResolvedRomLayout): List<EncounterArea> =
        materializeWithEvidence(rom, layout).areas

    internal fun materializeWithEvidence(rom: RomImage, layout: ResolvedRomLayout): EncounterMaterializationResult = when {
        layout.pokeemeraldExpansion != null -> availableOrNotFound(
            pokeemeraldExpansion(rom, layout.speciesCount ?: 0),
        )
        else -> when (layout.generation) {
        1 -> availableOrNotFound(gen1(rom, layout.speciesCount ?: 190))
        2 -> availableOrNotFound(gen2(rom, layout.speciesCount ?: 251))
        3 -> gen3(rom, layout.speciesCount ?: 412)
        else -> availableOrNotFound(emptyList())
        }
    }

    private fun availableOrNotFound(areas: List<EncounterArea>) = EncounterMaterializationResult(
        areas = areas,
        status = if (areas.isNotEmpty()) CapabilityStatus.AVAILABLE else CapabilityStatus.NOT_FOUND,
        reasons = listOf(
            if (areas.isNotEmpty()) "structurally decoded encounter areas" else "encounter tables were not located",
        ),
    )

    private fun pokeemeraldExpansion(rom: RomImage, speciesCount: Int): List<EncounterArea> {
        val table = findExpansionHeaderTable(rom, speciesCount) ?: return emptyList()
        return buildList {
            repeat(table.second) { index ->
                val header = table.first + index * EXPANSION_HEADER_SIZE
                val group = rom.u8(header)
                val map = rom.u8(header + 1)
                repeat(EXPANSION_TIME_COUNT) { time ->
                    EXPANSION_METHODS.forEachIndexed { methodIndex, method ->
                        val info = rom.gbaPointer(header + 4 + (time * EXPANSION_METHODS.size + methodIndex) * 4)
                            ?: return@forEachIndexed
                        val slots = rom.gbaPointer(info + 4) ?: return@forEachIndexed
                        add(
                            EncounterArea(
                                id = groupMapId(group, map) * 100 + time * 10 + method,
                                baseAreaId = groupMapId(group, map),
                                name = CatalogField.available(
                                    "Map $group-$map - ${EXPANSION_TIME_LABELS[time]} ${EXPANSION_LABELS[methodIndex]}",
                                ),
                                methodId = method,
                                slots = readGen3Slots(
                                    rom,
                                    slots,
                                    EXPANSION_SLOT_COUNTS[methodIndex],
                                    EXPANSION_WEIGHTS[methodIndex],
                                ),
                                windows = setOf(EXPANSION_WINDOWS[time]),
                            ),
                        )
                    }
                }
            }
        }.distinctBy { it.id }
    }

    private fun findExpansionHeaderTable(rom: RomImage, speciesCount: Int): Pair<Int, Int>? {
        val candidates = mutableListOf<Pair<Int, Int>>()
        var offset = 0
        while (offset + EXPANSION_HEADER_SIZE <= rom.size) {
            if (validExpansionHeader(rom, offset, speciesCount)) {
                var count = 0
                var cursor = offset
                while (count < 2048 && cursor + EXPANSION_HEADER_SIZE <= rom.size &&
                    validExpansionHeader(rom, cursor, speciesCount)
                ) {
                    count++
                    cursor += EXPANSION_HEADER_SIZE
                }
                if (count >= 3 && cursor + 1 < rom.size && rom.u8(cursor) == 0xFF && rom.u8(cursor + 1) == 0xFF) {
                    candidates += offset to count
                }
            }
            offset += 4
        }
        val best = candidates.maxByOrNull { it.second } ?: return null
        return best.takeIf { winner -> candidates.count { it.second == winner.second } == 1 }
    }

    private fun validExpansionHeader(rom: RomImage, offset: Int, speciesCount: Int): Boolean = runCatching {
        if (!validGroupMap(rom.u8(offset), rom.u8(offset + 1)) || rom.u8(offset + 2) != 0 || rom.u8(offset + 3) != 0) {
            return@runCatching false
        }
        var populated = 0
        repeat(EXPANSION_TIME_COUNT * EXPANSION_METHODS.size) { index ->
            val raw = rom.u32le(offset + 4 + index * 4)
            if (raw != 0L) {
                val info = rom.gbaPointer(offset + 4 + index * 4) ?: return@runCatching false
                val methodIndex = index % EXPANSION_METHODS.size
                if (!validGen3Info(rom, info, EXPANSION_SLOT_COUNTS[methodIndex], speciesCount)) {
                    return@runCatching false
                }
                populated++
            }
        }
        populated > 0
    }.getOrDefault(false)

    private fun gen1(rom: RomImage, speciesCount: Int): List<EncounterArea> {
        val pointerCount = 248
        var best: Gen1PointerTable? = null
        rom.findAll(byteArrayOf(0xFF.toByte(), 0xFF.toByte())).forEach { sentinel ->
            val offset = sentinel - pointerCount * 2
            if (offset < 0) return@forEach
            val bank = offset / GB_BANK_SIZE
            val pointers = IntArray(pointerCount) { index -> rom.u16le(offset + index * 2) }
            val dominantPointerCount = pointers.asIterable().groupingBy { it }.eachCount().values.maxOrNull() ?: 0
            if (bank > 0 && pointers.all { it in GB_SWITCHABLE_ADDRESS } && dominantPointerCount >= 100) {
                var encounteredMaps = 0
                var valid = true
                repeat(pointerCount) { mapId ->
                    if (valid) {
                        val target = rom.gbBankAddress(bank, rom.u16le(offset + mapId * 2))
                        val record = target?.let { readGen1Record(rom, it, speciesCount) }
                        if (record == null) valid = false
                        else if (record.first.isNotEmpty() || record.second.isNotEmpty()) encounteredMaps++
                    }
                }
                if (valid && encounteredMaps >= 10 && (best == null || encounteredMaps > best.encounteredMaps)) {
                    best = Gen1PointerTable(offset, bank, encounteredMaps)
                }
            }
        }
        val table = best ?: return emptyList()
        return buildList {
            repeat(pointerCount) { mapId ->
                val target = rom.gbBankAddress(table.bank, rom.u16le(table.offset + mapId * 2)) ?: return@repeat
                val (grass, water) = readGen1Record(rom, target, speciesCount) ?: return@repeat
                if (grass.isNotEmpty()) add(area(mapId, EncounterMethods.GRASS, "Map $mapId - grass", grass))
                if (water.isNotEmpty()) add(area(mapId, EncounterMethods.WATER, "Map $mapId - water", water))
            }
        }
    }

    private fun readGen1Record(
        rom: RomImage,
        offset: Int,
        speciesCount: Int,
    ): Pair<List<EncounterSlot>, List<EncounterSlot>>? = runCatching {
        var cursor = offset
        val grassRate = rom.u8(cursor++)
        val grass = if (grassRate == 0) emptyList() else {
            readByteSlots(rom, cursor, 10, speciesCount, GEN1_WEIGHTS).also { cursor += 20 }
        }
        val waterRate = rom.u8(cursor++)
        val water = if (waterRate == 0) emptyList() else {
            readByteSlots(rom, cursor, 10, speciesCount, GEN1_WEIGHTS)
        }
        grass to water
    }.getOrNull()

    private fun gen2(rom: RomImage, speciesCount: Int): List<EncounterArea> = buildList {
        val discoveredGrass = findGen2Arrays(rom, recordSize = 47, minimumRecords = 3) { offset ->
            validGen2GrassRecord(rom, offset, speciesCount)
        }
        val discoveredWater = findGen2Arrays(rom, recordSize = 9, minimumRecords = 5) { offset ->
            validGen2WaterRecord(rom, offset, speciesCount)
        }.filter { water ->
            discoveredGrass.none { grass -> rangesOverlap(water.offset, water.endExclusive, grass.offset, grass.endExclusive) }
        }
        val pairedGrass = discoveredGrass.filter { grass -> discoveredWater.any { it.offset == grass.endExclusive } }
        val grassArrays = pairedGrass.takeIf { it.isNotEmpty() } ?: discoveredGrass
        val pairedWater = discoveredWater.filter { water -> grassArrays.any { it.endExclusive == water.offset } }
        val waterArrays = pairedWater.takeIf { it.isNotEmpty() } ?: discoveredWater
        grassArrays.forEach { sequence ->
            repeat(sequence.count) { recordIndex ->
                val base = sequence.offset + recordIndex * 47
                val group = rom.u8(base)
                val map = rom.u8(base + 1)
                repeat(3) { time ->
                    if (rom.u8(base + 2 + time) > 0) {
                        val method = EncounterMethods.GRASS_MORNING + time
                        val label = listOf("morning grass", "day grass", "night grass")[time]
                        val slots = readByteSlots(rom, base + 5 + time * 14, 7, speciesCount, GEN2_GRASS_WEIGHTS)
                        add(area(groupMapId(group, map), method, "Map $group-$map - $label", slots))
                    }
                }
            }
        }
        waterArrays.forEach { sequence ->
            repeat(sequence.count) { recordIndex ->
                val base = sequence.offset + recordIndex * 9
                val group = rom.u8(base)
                val map = rom.u8(base + 1)
                val slots = readByteSlots(rom, base + 3, 3, speciesCount, GEN2_WATER_WEIGHTS)
                add(area(groupMapId(group, map), EncounterMethods.WATER, "Map $group-$map - water", slots))
            }
        }
    }.distinctBy { it.id }

    private fun findGen2Arrays(
        rom: RomImage,
        recordSize: Int,
        minimumRecords: Int,
        validator: (Int) -> Boolean,
    ): List<FixedSequence> {
        val candidates = mutableListOf<FixedSequence>()
        var offset = 0
        while (offset <= rom.size - recordSize) {
            if (!validator(offset)) {
                offset++
                continue
            }
            var count = 0
            var cursor = offset
            while (count < 256 && cursor + recordSize <= rom.size && validator(cursor)) {
                count++
                cursor += recordSize
            }
            if (count >= minimumRecords && cursor < rom.size && rom.u8(cursor) == 0xFF) {
                candidates += FixedSequence(offset, count, cursor + 1)
            }
            offset++
        }
        val selected = mutableListOf<FixedSequence>()
        candidates.sortedByDescending { it.count }.forEach { candidate ->
            if (selected.none { rangesOverlap(candidate.offset, candidate.endExclusive, it.offset, it.endExclusive) }) {
                selected += candidate
            }
        }
        return selected.sortedBy { it.offset }
    }

    private fun validGen2GrassRecord(rom: RomImage, offset: Int, speciesCount: Int): Boolean = runCatching {
        if (!validGroupMap(rom.u8(offset), rom.u8(offset + 1))) return@runCatching false
        if ((0 until 3).any { rom.u8(offset + 2 + it) !in 1..100 }) return@runCatching false
        (0 until 21).all { slot -> validByteSlot(rom, offset + 5 + slot * 2, speciesCount) }
    }.getOrDefault(false)

    private fun validGen2WaterRecord(rom: RomImage, offset: Int, speciesCount: Int): Boolean = runCatching {
        validGroupMap(rom.u8(offset), rom.u8(offset + 1)) && rom.u8(offset + 2) in 1..100 &&
            (0 until 3).all { slot -> validByteSlot(rom, offset + 3 + slot * 2, speciesCount) }
    }.getOrDefault(false)

    private fun gen3(rom: RomImage, speciesCount: Int): EncounterMaterializationResult {
        val resolved = resolveGen3HeaderTable(rom, speciesCount)
        val table = resolved.table ?: return EncounterMaterializationResult(
            areas = emptyList(),
            status = resolved.status,
            reasons = listOf(resolved.reason),
            reviewStatus = resolved.reviewStatus,
            probeStats = resolved.probeStats,
        )
        val decoded = buildList {
            repeat(table.count) { index ->
                val header = table.offset + index * table.abi.headerSize
                val group = rom.u8(header)
                val map = rom.u8(header + 1)
                table.abi.methods.forEachIndexed { methodIndex, method ->
                    val info = rom.gbaPointer(header + 4 + methodIndex * 4) ?: return@forEachIndexed
                    val slotPointer = rom.gbaPointer(info + 4) ?: return@forEachIndexed
                    val label = if (method == EncounterMethods.HIDDEN) {
                        "hidden (${if (rom.u8(info) == 1) "water" else "land"})"
                    } else {
                        table.abi.labels[methodIndex]
                    }
                    val slots = readGen3Slots(
                        rom,
                        slotPointer,
                        table.abi.slotCounts[methodIndex],
                        table.abi.weights[methodIndex],
                    )
                    add(area(groupMapId(group, map), method, "Map $group-$map - $label", slots))
                }
            }
        }
        val areas = decoded.groupBy { it.id }.values.map { variants ->
            variants.first().copy(slots = variants.flatMap { it.slots }.distinct())
        }
        val reason = "selected Gen 3 encounter root=0x${table.offset.toString(16)} " +
            "ABI=${table.abi.headerSize}-byte headers=${table.count} populatedMethods=${table.populatedCount} " +
            "areas=${areas.size} references=${table.referenceCount} candidates=${resolved.candidateCount} " +
            "authority=${resolved.selectionAuthority}"
        return EncounterMaterializationResult(
            areas = areas,
            status = if (areas.isNotEmpty()) CapabilityStatus.AVAILABLE else CapabilityStatus.NOT_FOUND,
            reasons = listOf(reason),
            probeStats = resolved.probeStats,
            selectedRootOffset = table.offset,
            headerSize = table.abi.headerSize,
            headerCount = table.count,
            populatedMethodCount = table.populatedCount,
            referenceCount = table.referenceCount,
            candidateCount = resolved.candidateCount,
        )
    }

    private fun resolveGen3HeaderTable(rom: RomImage, speciesCount: Int): Gen3HeaderResolution {
        val anchoredOffset = runCatching { rom.gbaPointer(CFRU_WILD_HEADER_POINTER) }.getOrNull()
        val referenceScan = gen3HeaderReferenceCounts(rom, speciesCount)
        val probeStats = EncounterProbeStats(referenceScan.emptyClassicShellWalks)
        referenceScan.issue?.let { return ambiguousGen3Resolution(it, probeStats) }
        val referenceCounts = referenceScan.counts
        val candidates = mutableListOf<Gen3HeaderTable>()
        var offset = 0
        while (offset <= rom.size - GEN3_STANDARD_ABI.headerSize) {
            GEN3_ABIS.forEach { abi ->
                if (offset + abi.headerSize > rom.size) return@forEach
                val anchored = offset == anchoredOffset
                if (!anchored && offset !in referenceCounts && offset >= abi.headerSize &&
                    (validGen3HeaderRecord(rom, offset - abi.headerSize, speciesCount, abi) ?: 0) > 0
                ) {
                    return@forEach
                }
                resolveGen3HeaderCandidate(
                    rom,
                    offset,
                    speciesCount,
                    abi,
                    anchored,
                    allowEmptyFirst = abi.requiresCompiledReference && offset in referenceCounts,
                )?.let(candidates::add)
                if (candidates.size > MAX_GEN3_HEADER_CANDIDATES) {
                    return ambiguousGen3Resolution(GEN3_HEADER_CANDIDATE_BUDGET_REASON, probeStats)
                }
            }
            offset += 4
        }
        if (anchoredOffset != null && candidates.none { it.offset == anchoredOffset }) {
            GEN3_ABIS.forEach { abi ->
                resolveGen3HeaderCandidate(
                    rom,
                    anchoredOffset,
                    speciesCount,
                    abi,
                    anchored = true,
                    allowEmptyFirst = abi.requiresCompiledReference && anchoredOffset in referenceCounts,
                )
                    ?.let(candidates::add)
            }
        }
        if (candidates.isEmpty()) return unavailableGen3Resolution(probeStats)
        if (candidates.size > MAX_GEN3_HEADER_CANDIDATES) {
            return ambiguousGen3Resolution(GEN3_HEADER_CANDIDATE_BUDGET_REASON, probeStats)
        }
        if (candidates.groupBy { it.offset }.values.any { sameRoot -> sameRoot.map { it.abi.headerSize }.distinct().size > 1 }) {
            return ambiguousGen3Resolution(
                "the same Gen 3 encounter root validates under multiple header ABIs",
                probeStats,
            )
        }

        val withReferences = candidates.map { candidate ->
            candidate.copy(referenceCount = referenceCounts[candidate.offset] ?: 0)
        }
        val permitted = withReferences.filter { candidate ->
            !candidate.abi.requiresCompiledReference || candidate.referenceCount > 0
        }
        if (permitted.isEmpty()) return unavailableGen3Resolution(probeStats)
        val referenced = permitted.filter { it.referenceCount > 0 }
        val eligible = referenced.ifEmpty { permitted }
        val winner = eligible.singleOrNull { candidate ->
            eligible.all { other -> candidate === other || candidate.dominates(other) }
        }
        return winner?.let {
            Gen3HeaderResolution(
                table = it,
                probeStats = probeStats,
                candidateCount = withReferences.size,
                selectionAuthority = "compiled-reference-and-structural-dominance",
            )
        }
            ?: ambiguousGen3Resolution(
                "multiple structurally credible Gen 3 encounter roots remain ambiguous",
                probeStats,
            )
    }

    private fun gen3HeaderReferenceCounts(rom: RomImage, speciesCount: Int): Gen3ReferenceScan {
        val counts = mutableMapOf<Int, Int>()
        val emptyClassicCandidates = mutableMapOf<Int, Boolean>()
        val rejectedEmptyClassicCandidates = object : LinkedHashMap<Int, Unit>(
            MAX_REJECTED_EMPTY_CLASSIC_TARGETS + 1,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Unit>?): Boolean =
                size > MAX_REJECTED_EMPTY_CLASSIC_TARGETS
        }
        var emptyClassicShellWalks = 0
        var offset = 0
        while (offset + 4 <= rom.size) {
            val target = rom.gbaPointer(offset)
            if (target != null && target % 4 == 0 && GEN3_ABIS.any { abi ->
                    if (target + abi.headerSize > rom.size) return@any false
                    val populated = validGen3HeaderRecord(rom, target, speciesCount, abi) ?: return@any false
                    if (populated > 0) return@any true
                    if (!abi.requiresCompiledReference) return@any false
                    emptyClassicCandidates[target]?.let { return@any it }
                    if (rejectedEmptyClassicCandidates[target] != null) return@any false
                    if (emptyClassicShellWalks >= MAX_EMPTY_CLASSIC_SHELL_WALKS) {
                        return Gen3ReferenceScan(
                            issue = EMPTY_CLASSIC_SHELL_WALK_BUDGET_REASON,
                            emptyClassicShellWalks = emptyClassicShellWalks,
                        )
                    }
                    emptyClassicShellWalks++
                    if (!plausibleEmptyClassicCandidate(rom, target, abi)) {
                        rejectedEmptyClassicCandidates[target] = Unit
                        return@any false
                    }
                    if (emptyClassicCandidates.size >= MAX_EMPTY_CLASSIC_CANDIDATES) {
                        return Gen3ReferenceScan(
                            issue = EMPTY_CLASSIC_CANDIDATE_BUDGET_REASON,
                            emptyClassicShellWalks = emptyClassicShellWalks,
                        )
                    }
                    val valid = resolveGen3HeaderCandidate(
                        rom,
                        target,
                        speciesCount,
                        abi,
                        anchored = false,
                        allowEmptyFirst = true,
                    ) != null
                    emptyClassicCandidates[target] = valid
                    valid
                }
            ) {
                counts[target] = (counts[target] ?: 0) + 1
                if (counts.size > MAX_GEN3_REFERENCED_ROOTS) {
                    return Gen3ReferenceScan(
                        issue = GEN3_REFERENCED_ROOT_BUDGET_REASON,
                        emptyClassicShellWalks = emptyClassicShellWalks,
                    )
                }
            }
            offset += 4
        }
        return Gen3ReferenceScan(counts, emptyClassicShellWalks = emptyClassicShellWalks)
    }

    private fun plausibleEmptyClassicCandidate(
        rom: RomImage,
        offset: Int,
        abi: Gen3EncounterAbi,
    ): Boolean = runCatching {
        var index = 1
        while (index <= MAX_GEN3_HEADERS) {
            val header = offset.toLong() + index.toLong() * abi.headerSize
            if (header + 2 > rom.size || header > Int.MAX_VALUE) return@runCatching false
            val headerOffset = header.toInt()
            if (rom.u8(headerOffset) == 0xFF && rom.u8(headerOffset + 1) == 0xFF) {
                return@runCatching index >= MIN_GEN3_HEADERS
            }
            if (index == MAX_GEN3_HEADERS || header + abi.headerSize > rom.size) return@runCatching false
            if (!structurallyPossibleGen3HeaderRecord(rom, headerOffset, abi)) return@runCatching false
            index++
        }
        false
    }.getOrDefault(false)

    private fun structurallyPossibleGen3HeaderRecord(
        rom: RomImage,
        offset: Int,
        abi: Gen3EncounterAbi,
    ): Boolean = runCatching {
        if (!validGroupMap(rom.u8(offset), rom.u8(offset + 1))) {
            return@runCatching false
        }
        abi.methods.indices.all { method ->
            val pointerOffset = offset + 4 + method * 4
            rom.u32le(pointerOffset) == 0L || rom.gbaPointer(pointerOffset) != null
        }
    }.getOrDefault(false)

    private fun unavailableGen3Resolution(probeStats: EncounterProbeStats) = Gen3HeaderResolution(
        status = CapabilityStatus.NOT_FOUND,
        reason = "encounter tables were not located",
        probeStats = probeStats,
    )

    private fun ambiguousGen3Resolution(reason: String, probeStats: EncounterProbeStats) = Gen3HeaderResolution(
        status = CapabilityStatus.AMBIGUOUS,
        reason = reason,
        reviewStatus = CapabilityReviewStatus.MANUAL_REVIEW,
        probeStats = probeStats,
    )

    private fun resolveGen3HeaderCandidate(
        rom: RomImage,
        offset: Int,
        speciesCount: Int,
        abi: Gen3EncounterAbi,
        anchored: Boolean,
        allowEmptyFirst: Boolean = false,
    ): Gen3HeaderTable? = runCatching {
        var count = 0
        var populated = 0
        while (count < MAX_GEN3_HEADERS) {
            val header = offset.toLong() + count.toLong() * abi.headerSize
            if (header + abi.headerSize > rom.size || header > Int.MAX_VALUE) return@runCatching null
            val headerOffset = header.toInt()
            if (rom.u8(headerOffset) == 0xFF && rom.u8(headerOffset + 1) == 0xFF) break
            val methods = validGen3HeaderRecord(rom, headerOffset, speciesCount, abi) ?: return@runCatching null
            if (count == 0 && methods == 0 && !allowEmptyFirst) return@runCatching null
            populated += methods
            count++
        }
        val end = offset.toLong() + count.toLong() * abi.headerSize
        if (end + 2 > rom.size || end > Int.MAX_VALUE ||
            rom.u8(end.toInt()) != 0xFF || rom.u8(end.toInt() + 1) != 0xFF
        ) {
            return@runCatching null
        }
        val minimum = if (anchored) 1 else MIN_GEN3_HEADERS
        if (count < minimum || populated < minimum) return@runCatching null
        Gen3HeaderTable(offset, count, populated, abi, anchored)
    }.getOrNull()

    private fun validGen3HeaderRecord(
        rom: RomImage,
        offset: Int,
        speciesCount: Int,
        abi: Gen3EncounterAbi,
    ): Int? = runCatching {
        val group = rom.u8(offset)
        val map = rom.u8(offset + 1)
        if (!validGroupMap(group, map)) return@runCatching null
        var populated = 0
        abi.methods.indices.forEach { method ->
            val raw = rom.u32le(offset + 4 + method * 4)
            if (raw != 0L) {
                val info = rom.gbaPointer(offset + 4 + method * 4) ?: return@runCatching null
                if (!validGen3Info(
                        rom,
                        info,
                        abi.slotCounts[method],
                        speciesCount,
                        hidden = abi.methods[method] == EncounterMethods.HIDDEN,
                    )
                ) {
                    return@runCatching null
                }
                populated++
            }
        }
        populated
    }.getOrNull()

    private fun validGen3Info(
        rom: RomImage,
        offset: Int,
        slotCount: Int,
        speciesCount: Int,
        hidden: Boolean = false,
    ): Boolean = runCatching {
        if (hidden && rom.u8(offset) !in 0..1) return@runCatching false
        val slots = rom.gbaPointer(offset + 4) ?: return@runCatching false
        repeat(slotCount) { slot ->
            val entry = slots + slot * 4
            val minimum = rom.u8(entry)
            val maximum = rom.u8(entry + 1)
            val species = rom.u16le(entry + 2)
            if (minimum !in 0..100 || maximum !in 0..100 || species !in 0 until speciesCount) {
                return@runCatching false
            }
        }
        true
    }.getOrDefault(false)

    private fun readGen3Slots(
        rom: RomImage,
        offset: Int,
        count: Int,
        weights: IntArray,
    ): List<EncounterSlot> = buildList {
        repeat(count) { index ->
            val entry = offset + index * 4
            val species = rom.u16le(entry + 2)
            add(EncounterSlot(species, rom.u8(entry), rom.u8(entry + 1), weights[index]))
        }
    }

    private fun readByteSlots(
        rom: RomImage,
        offset: Int,
        count: Int,
        speciesCount: Int,
        weights: IntArray,
    ): List<EncounterSlot> = buildList {
        repeat(count) { index ->
            val entry = offset + index * 2
            val species = rom.u8(entry + 1)
            require(species in 1..speciesCount)
            add(EncounterSlot(species, rom.u8(entry), rom.u8(entry), weights[index]))
        }
    }

    private fun validByteSlot(rom: RomImage, offset: Int, speciesCount: Int): Boolean =
        rom.u8(offset) in 1..100 && rom.u8(offset + 1) in 1..speciesCount

    private fun validGroupMap(group: Int, map: Int): Boolean = group in 0..63 && map in 0..254

    private fun area(baseId: Int, method: Int, name: String, slots: List<EncounterSlot>) = EncounterArea(
        id = baseId * 10 + method,
        baseAreaId = baseId,
        name = CatalogField.available(name),
        methodId = method,
        slots = slots,
        windows = when (method) {
            EncounterMethods.GRASS_MORNING -> setOf(EncounterWindow.MORNING)
            EncounterMethods.GRASS_DAY -> setOf(EncounterWindow.DAY)
            EncounterMethods.GRASS_NIGHT -> setOf(EncounterWindow.NIGHT)
            else -> setOf(EncounterWindow.ANY)
        },
    )

    private fun groupMapId(group: Int, map: Int): Int = (group shl 8) or map

    private fun rangesOverlap(firstStart: Int, firstEnd: Int, secondStart: Int, secondEnd: Int): Boolean =
        firstStart < secondEnd && secondStart < firstEnd

    private data class Gen1PointerTable(val offset: Int, val bank: Int, val encounteredMaps: Int)
    private data class FixedSequence(val offset: Int, val count: Int, val endExclusive: Int)
    private data class Gen3EncounterAbi(
        val headerSize: Int,
        val methods: IntArray,
        val labels: Array<String>,
        val slotCounts: IntArray,
        val weights: Array<IntArray>,
        val requiresCompiledReference: Boolean = false,
    )
    private data class Gen3HeaderTable(
        val offset: Int,
        val count: Int,
        val populatedCount: Int,
        val abi: Gen3EncounterAbi,
        val anchored: Boolean,
        val referenceCount: Int = 0,
    ) {
        fun dominates(other: Gen3HeaderTable): Boolean =
            referenceCount >= other.referenceCount && populatedCount >= other.populatedCount && count >= other.count &&
                (referenceCount > other.referenceCount || populatedCount > other.populatedCount || count > other.count)
    }
    private data class Gen3HeaderResolution(
        val table: Gen3HeaderTable? = null,
        val status: CapabilityStatus = CapabilityStatus.AVAILABLE,
        val reason: String = "structurally decoded encounter areas",
        val reviewStatus: CapabilityReviewStatus = CapabilityReviewStatus.NONE,
        val probeStats: EncounterProbeStats = EncounterProbeStats(),
        val candidateCount: Int = 0,
        val selectionAuthority: String = "structural-evidence",
    )
    private data class Gen3ReferenceScan(
        val counts: Map<Int, Int> = emptyMap(),
        val issue: String? = null,
        val emptyClassicShellWalks: Int = 0,
    )

    private const val GB_BANK_SIZE = 0x4000
    private val GB_SWITCHABLE_ADDRESS = 0x4000..0x7FFF
    private const val MIN_GEN3_HEADERS = 3
    private const val MAX_GEN3_HEADERS = 1024
    private const val MAX_GEN3_HEADER_CANDIDATES = 256
    private const val MAX_GEN3_REFERENCED_ROOTS = 4096
    private const val MAX_EMPTY_CLASSIC_CANDIDATES = 256
    private const val MAX_REJECTED_EMPTY_CLASSIC_TARGETS = 4096
    private const val MAX_EMPTY_CLASSIC_SHELL_WALKS = 16384
    private const val EMPTY_CLASSIC_SHELL_WALK_BUDGET_REASON =
        "empty-first Classic24 total shell-walk budget exceeded (16384); encounter table selection is ambiguous"
    private const val EMPTY_CLASSIC_CANDIDATE_BUDGET_REASON =
        "empty-first Classic24 candidate budget exceeded (256); encounter table selection is ambiguous"
    private const val GEN3_HEADER_CANDIDATE_BUDGET_REASON =
        "Gen 3 encounter header candidate budget exceeded (256); encounter table selection is ambiguous"
    private const val GEN3_REFERENCED_ROOT_BUDGET_REASON =
        "Gen 3 referenced encounter-root budget exceeded (4096); encounter table selection is ambiguous"
    private const val EXPANSION_HEADER_SIZE = 84
    private const val EXPANSION_TIME_COUNT = 4
    private const val CFRU_WILD_HEADER_POINTER = 0x82990
    private val GEN1_WEIGHTS = intArrayOf(20, 20, 15, 10, 10, 10, 5, 5, 4, 1)
    private val GEN2_GRASS_WEIGHTS = intArrayOf(30, 30, 20, 10, 5, 4, 1)
    private val GEN2_WATER_WEIGHTS = intArrayOf(60, 30, 10)
    private val GEN3_METHODS = intArrayOf(
        EncounterMethods.GRASS,
        EncounterMethods.WATER,
        EncounterMethods.ROCK_SMASH,
        EncounterMethods.FISHING,
    )
    private val GEN3_LABELS = arrayOf("grass", "water", "rock smash", "fishing")
    private val GEN3_SLOT_COUNTS = intArrayOf(12, 5, 5, 10)
    private val GEN3_WEIGHTS = arrayOf(
        intArrayOf(20, 20, 10, 10, 10, 10, 5, 5, 4, 4, 1, 1),
        intArrayOf(60, 30, 5, 4, 1),
        intArrayOf(60, 30, 5, 4, 1),
        intArrayOf(70, 30, 60, 20, 20, 40, 40, 15, 4, 1),
    )
    private val GEN3_STANDARD_ABI = Gen3EncounterAbi(
        headerSize = 20,
        methods = GEN3_METHODS,
        labels = GEN3_LABELS,
        slotCounts = GEN3_SLOT_COUNTS,
        weights = GEN3_WEIGHTS,
    )
    private val GEN3_HIDDEN_ABI = Gen3EncounterAbi(
        headerSize = 24,
        methods = intArrayOf(
            EncounterMethods.GRASS,
            EncounterMethods.WATER,
            EncounterMethods.ROCK_SMASH,
            EncounterMethods.HIDDEN,
            EncounterMethods.FISHING,
        ),
        labels = arrayOf("grass", "water", "rock smash", "hidden", "fishing"),
        slotCounts = intArrayOf(12, 5, 5, 3, 10),
        weights = arrayOf(
            GEN3_WEIGHTS[0],
            GEN3_WEIGHTS[1],
            GEN3_WEIGHTS[2],
            intArrayOf(60, 30, 10),
            GEN3_WEIGHTS[3],
        ),
        requiresCompiledReference = true,
    )
    private val GEN3_ABIS = arrayOf(GEN3_STANDARD_ABI, GEN3_HIDDEN_ABI)
    private val EXPANSION_METHODS = intArrayOf(
        EncounterMethods.GRASS,
        EncounterMethods.WATER,
        EncounterMethods.ROCK_SMASH,
        EncounterMethods.FISHING,
        EncounterMethods.HIDDEN,
    )
    private val EXPANSION_LABELS = arrayOf("grass", "water", "rock smash", "fishing", "hidden")
    private val EXPANSION_TIME_LABELS = arrayOf("morning", "day", "evening", "night")
    private val EXPANSION_WINDOWS = arrayOf(
        EncounterWindow.MORNING,
        EncounterWindow.DAY,
        EncounterWindow.DAY,
        EncounterWindow.NIGHT,
    )
    private val EXPANSION_SLOT_COUNTS = intArrayOf(12, 5, 5, 10, 3)
    private val EXPANSION_WEIGHTS = arrayOf(
        GEN3_WEIGHTS[0],
        GEN3_WEIGHTS[1],
        GEN3_WEIGHTS[2],
        GEN3_WEIGHTS[3],
        intArrayOf(60, 30, 10),
    )
}
