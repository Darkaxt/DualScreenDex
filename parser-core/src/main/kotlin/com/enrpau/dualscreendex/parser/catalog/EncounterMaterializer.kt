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
        val compiled = Gen1CompiledEncounterResolver.resolve(rom, speciesCount)
        if (compiled.detected) {
            return compiled.layout?.let { layout ->
                materializeGen1(rom, speciesCount, layout.offset, layout.bank, layout.count)
            }.orEmpty()
        }

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
        return materializeGen1(rom, speciesCount, table.offset, table.bank, pointerCount)
    }

    private fun materializeGen1(
        rom: RomImage,
        speciesCount: Int,
        pointerTable: Int,
        bank: Int,
        pointerCount: Int,
    ): List<EncounterArea> = buildList {
        repeat(pointerCount) { mapId ->
            val target = rom.gbBankAddress(bank, rom.u16le(pointerTable + mapId * 2)) ?: return@repeat
            val (grass, water) = readGen1Record(rom, target, speciesCount) ?: return@repeat
            if (grass.isNotEmpty()) add(area(mapId, EncounterMethods.GRASS, "Map $mapId - grass", grass))
            if (water.isNotEmpty()) add(area(mapId, EncounterMethods.WATER, "Map $mapId - water", water))
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

    private fun gen2(rom: RomImage, speciesCount: Int): List<EncounterArea> {
        val compactConsumers = findCompactGen2Consumers(rom)
        if (compactConsumers.isNotEmpty()) {
            val consumer = compactConsumers.singleOrNull() ?: return emptyList()
            val levelAuthority = findCompactGen2LevelAuthorities(rom, consumer.bank).singleOrNull()
                ?: return emptyList()
            return materializeCompactGen2(rom, speciesCount, consumer, levelAuthority).orEmpty()
        }
        val compiled = Gen2CompiledEncounterResolver.resolve(rom, speciesCount)
        if (compiled.detected) {
            return compiled.layout?.let { materializeCompiledGen2(rom, speciesCount, it) }.orEmpty()
        }
        return standardGen2(rom, speciesCount)
    }

    private fun materializeCompiledGen2(
        rom: RomImage,
        speciesCount: Int,
        layout: Gen2CompiledEncounterLayout,
    ): List<EncounterArea> = buildList {
        layout.grassTables.forEach { table ->
            repeat(table.count) { recordIndex ->
                val base = table.offset + recordIndex * table.recordSize
                val group = rom.u8(base)
                val map = rom.u8(base + 1)
                repeat(GEN2_TIME_COUNT) { time ->
                    if (rom.u8(base + 2 + time) > 0) {
                        val method = EncounterMethods.GRASS_MORNING + time
                        val label = GEN2_GRASS_LABELS[time]
                        val slots = readByteSlots(
                            rom,
                            base + GEN2_GRASS_HEADER_BYTES + time * layout.grassSlotCount * GEN2_SLOT_BYTES,
                            layout.grassSlotCount,
                            speciesCount,
                            layout.grassWeights,
                        )
                        add(area(groupMapId(group, map), method, "Map $group-$map - $label", slots))
                    }
                }
            }
        }
        val water = layout.waterTable
        repeat(water.count) { recordIndex ->
            val base = water.offset + recordIndex * water.recordSize
            val group = rom.u8(base)
            val map = rom.u8(base + 1)
            val slots = readByteSlots(
                rom,
                base + GEN2_WATER_HEADER_BYTES,
                layout.waterSlotCount,
                speciesCount,
                layout.waterWeights,
            )
            add(area(groupMapId(group, map), EncounterMethods.WATER, "Map $group-$map - water", slots))
        }
    }.distinctBy { it.id }

    private fun standardGen2(rom: RomImage, speciesCount: Int): List<EncounterArea> = buildList {
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

    private fun findCompactGen2Consumers(rom: RomImage): List<CompactGen2Consumer> = buildList {
        val bankCount = rom.size / GB_BANK_SIZE
        for (bank in 1 until bankCount) {
            val bankStart = bank * GB_BANK_SIZE
            val bankEnd = minOf(bankStart + GB_BANK_SIZE, rom.size)
            var offset = bankStart
            while (offset + COMPACT_GEN2_CONSUMER_BYTES <= bankEnd) {
                parseCompactGen2ConsumerAt(rom, bank, offset, bankEnd)?.let(::add)
                offset++
            }
        }
    }.distinct()

    private fun parseCompactGen2ConsumerAt(
        rom: RomImage,
        bank: Int,
        offset: Int,
        bankEnd: Int,
    ): CompactGen2Consumer? = runCatching {
        if (
            rom.u8(offset) != 0x21 ||
            rom.u8(offset + 3) != 0x01 || rom.u16le(offset + 4) != COMPACT_GEN2_RECORD_BYTES ||
            rom.u8(offset + 6) != 0xcd || rom.u8(offset + 9) != 0xd8 ||
            rom.u8(offset + 10) != 0xcd || rom.u8(offset + 13) != 0x54 || rom.u8(offset + 14) != 0x5d ||
            rom.u8(offset + 15) != 0x21 || rom.u8(offset + 18) != 0xcd ||
            rom.u8(offset + 21) != 0x01 || rom.u16le(offset + 22) != COMPACT_GEN2_RECORD_BYTES ||
            rom.u8(offset + 24) != 0x18 ||
            rom.u8(offset + 26) != 0x21 ||
            rom.u8(offset + 29) != 0x01 || rom.u16le(offset + 30) != COMPACT_GEN2_RECORD_BYTES ||
            rom.u8(offset + 32) != 0x21 || rom.u8(offset + 35) != 0x11 ||
            rom.u8(offset + 38) != 0xcd || rom.u16le(offset + 39) != rom.u16le(offset + 19) ||
            rom.u8(offset + 41) != 0x01 || rom.u16le(offset + 42) != COMPACT_GEN2_RECORD_BYTES ||
            rom.u8(offset + 44) != 0x18 ||
            compactBranchTarget(offset + 24, rom.u8(offset + 25)) !=
            compactBranchTarget(offset + 44, rom.u8(offset + 45))
        ) return@runCatching null

        val selector = rom.gbBankAddress(bank, rom.u16le(offset + 11)) ?: return@runCatching null
        if (
            selector + COMPACT_GEN2_KANTO_SELECTOR_BYTES > bankEnd ||
            rom.u8(selector) != 0x21 || rom.u8(selector + 3) != 0xcb ||
            !isBitTestHl(rom.u8(selector + 4)) || rom.u8(selector + 5) != 0x21 ||
            rom.u8(selector + 8) != 0xc0 || rom.u8(selector + 9) != 0x21 ||
            rom.u8(selector + 12) != 0xc9
        ) return@runCatching null

        CompactGen2Consumer(
            bank = bank,
            johtoGrass = rom.gbBankAddress(bank, rom.u16le(offset + 16)) ?: return@runCatching null,
            kantoGrass = rom.gbBankAddress(bank, rom.u16le(selector + 6)) ?: return@runCatching null,
            alternateKantoGrass = rom.gbBankAddress(bank, rom.u16le(selector + 10)) ?: return@runCatching null,
            swarmGrass = rom.gbBankAddress(bank, rom.u16le(offset + 1)) ?: return@runCatching null,
            johtoWater = rom.gbBankAddress(bank, rom.u16le(offset + 33)) ?: return@runCatching null,
            kantoWater = rom.gbBankAddress(bank, rom.u16le(offset + 36)) ?: return@runCatching null,
            swarmWater = rom.gbBankAddress(bank, rom.u16le(offset + 27)) ?: return@runCatching null,
        )
    }.getOrNull()

    private fun isBitTestHl(opcode: Int): Boolean =
        opcode in 0x46..0x7e && (opcode - 0x46) % 8 == 0

    private fun compactBranchTarget(opcodeOffset: Int, encodedDelta: Int): Int =
        opcodeOffset + 2 + encodedDelta.toByte().toInt()

    private fun findCompactGen2LevelAuthorities(rom: RomImage, bank: Int): List<CompactGen2LevelAuthority> {
        val bankStart = bank * GB_BANK_SIZE
        val bankEnd = minOf(bankStart + GB_BANK_SIZE, rom.size)
        return buildList {
            var offset = bankStart
            while (offset + COMPACT_GEN2_LEVEL_CONSUMER_BYTES <= bankEnd) {
                parseCompactGen2LevelAuthorityAt(rom, bank, offset, bankEnd)?.let(::add)
                offset++
            }
        }.distinctBy { authority ->
            authority.weights.joinToString("|") { it.contentHashCode().toString() } + ":" +
                authority.levelPointers.contentHashCode() + ":" + authority.levelAdjustments.contentHashCode()
        }
    }

    private fun parseCompactGen2LevelAuthorityAt(
        rom: RomImage,
        bank: Int,
        offset: Int,
        bankEnd: Int,
    ): CompactGen2LevelAuthority? = runCatching {
        if (
            rom.u8(offset) != 0x1a || rom.u8(offset + 1) != 0xe6 || rom.u8(offset + 2) != 0x0f ||
            rom.u8(offset + 3) != 0xd5 || rom.u8(offset + 4) != 0x21 ||
            rom.u8(offset + 7) != 0x4f || rom.u8(offset + 8) != 0x06 || rom.u8(offset + 9) != 0x00 ||
            rom.u8(offset + 10) != 0x3e || rom.u8(offset + 11) != COMPACT_GEN2_SLOT_COUNT ||
            rom.u8(offset + 12) != 0xcd ||
            rom.u8(offset + 39) != 0xe1 || rom.u8(offset + 40) != 0xe5 ||
            rom.u8(offset + 41) != 0xfa || rom.u8(offset + 44) != 0x4f ||
            rom.u8(offset + 45) != 0x7e || rom.u8(offset + 46) != 0xcb || rom.u8(offset + 47) != 0x37 ||
            rom.u8(offset + 48) != 0xe6 || rom.u8(offset + 49) != 0x0f || rom.u8(offset + 50) != 0x28 ||
            rom.u8(offset + 52) != 0x06 || rom.u8(offset + 53) != 0x00 || rom.u8(offset + 54) != 0x3d ||
            rom.u8(offset + 55) != 0x21 || rom.u8(offset + 58) != 0x09 ||
            rom.u8(offset + 59) != 0x0e || rom.u8(offset + 60) != COMPACT_GEN2_TIME_COUNT ||
            rom.u8(offset + 61) != 0xcd || rom.u16le(offset + 62) != rom.u16le(offset + 13) ||
            rom.u8(offset + 64) != 0x7e || rom.u8(offset + 65) != 0xa7 || rom.u8(offset + 66) != 0x28 ||
            rom.u8(offset + 68) != 0x3d || rom.u8(offset + 69) != 0x21 ||
            rom.u8(offset + 72) != 0x0e || rom.u8(offset + 73) != COMPACT_GEN2_SLOT_COUNT ||
            rom.u8(offset + 74) != 0xcd || rom.u16le(offset + 75) != rom.u16le(offset + 13) ||
            rom.u8(offset + 77) != 0x19 || rom.u8(offset + 78) != 0x7e ||
            rom.u8(offset + 79) != 0xe1 || rom.u8(offset + 80) != 0x2b || rom.u8(offset + 81) != 0x86 ||
            rom.u8(offset + 82) != 0xe1 || rom.u8(offset + 83) != 0x19 || rom.u8(offset + 84) != 0x47 ||
            rom.u8(offset + 85) != 0xcd
        ) return@runCatching null

        val probabilityRoot = rom.gbBankAddress(bank, rom.u16le(offset + 5)) ?: return@runCatching null
        val levelPointerRoot = rom.gbBankAddress(bank, rom.u16le(offset + 56)) ?: return@runCatching null
        val levelTableRoot = rom.gbBankAddress(bank, rom.u16le(offset + 70)) ?: return@runCatching null
        if (
            probabilityRoot + COMPACT_GEN2_PROBABILITY_BYTES > bankEnd ||
            levelPointerRoot + COMPACT_GEN2_LEVEL_POINTER_BYTES > bankEnd
        ) return@runCatching null

        val weights = List(COMPACT_GEN2_PROBABILITY_COUNT) { table ->
            val result = IntArray(COMPACT_GEN2_SLOT_COUNT)
            var previous = 0
            repeat(COMPACT_GEN2_SLOT_COUNT) { slot ->
                val cumulative = rom.u8(probabilityRoot + table * COMPACT_GEN2_SLOT_COUNT + slot)
                val delta = cumulative - previous
                if (delta <= 0 || delta % 2 != 0) return@runCatching null
                result[slot] = delta / 2
                previous = cumulative
            }
            if (previous != COMPACT_GEN2_PROBABILITY_TOTAL) return@runCatching null
            result
        }
        val levelPointers = rom.slice(levelPointerRoot, COMPACT_GEN2_LEVEL_POINTER_BYTES)
        val levelTableCount = levelPointers.maxOf { it.toInt() and 0xff }
        if (levelTableCount == 0 || levelTableRoot + levelTableCount * COMPACT_GEN2_SLOT_COUNT > bankEnd) {
            return@runCatching null
        }
        CompactGen2LevelAuthority(
            weights,
            levelPointers,
            rom.slice(levelTableRoot, levelTableCount * COMPACT_GEN2_SLOT_COUNT),
        )
    }.getOrNull()

    private fun materializeCompactGen2(
        rom: RomImage,
        speciesCount: Int,
        consumer: CompactGen2Consumer,
        levels: CompactGen2LevelAuthority,
    ): List<EncounterArea>? = runCatching {
        val requiredGrass = listOf(consumer.johtoGrass, consumer.kantoGrass, consumer.alternateKantoGrass).map { root ->
            readCompactGen2Array(rom, root, speciesCount, levels, allowEmpty = false) ?: return@runCatching null
        }
        val requiredWater = listOf(consumer.johtoWater, consumer.kantoWater).map { root ->
            readCompactGen2Array(rom, root, speciesCount, levels, allowEmpty = false) ?: return@runCatching null
        }
        val grass = requiredGrass.flatten() +
            (readCompactGen2Array(rom, consumer.swarmGrass, speciesCount, levels, allowEmpty = true)
                ?: return@runCatching null)
        val water = requiredWater.flatten() +
            (readCompactGen2Array(rom, consumer.swarmWater, speciesCount, levels, allowEmpty = true)
                ?: return@runCatching null)
        val decoded = buildList {
            grass.forEach { record ->
                repeat(COMPACT_GEN2_TIME_COUNT) { time ->
                    val method = EncounterMethods.GRASS_MORNING + time
                    val label = listOf("morning grass", "day grass", "night grass")[time]
                    add(
                        area(
                            groupMapId(record.group, record.map),
                            method,
                            "Map ${record.group}-${record.map} - $label",
                            readCompactGen2Slots(rom, record, time, levels),
                        ),
                    )
                }
            }
            water.forEach { record ->
                val slots = (0 until COMPACT_GEN2_TIME_COUNT).flatMap { time ->
                    readCompactGen2Slots(rom, record, time, levels)
                }.distinct()
                add(
                    area(
                        groupMapId(record.group, record.map),
                        EncounterMethods.WATER,
                        "Map ${record.group}-${record.map} - water",
                        slots,
                    ),
                )
            }
        }
        decoded.groupBy { it.id }.values.map { variants ->
            variants.first().copy(slots = variants.flatMap { it.slots }.distinct())
        }
    }.getOrNull()

    private fun readCompactGen2Array(
        rom: RomImage,
        root: Int,
        speciesCount: Int,
        levels: CompactGen2LevelAuthority,
        allowEmpty: Boolean,
    ): List<CompactGen2Record>? = runCatching {
        val bankEnd = minOf((root / GB_BANK_SIZE + 1) * GB_BANK_SIZE, rom.size)
        val records = buildList {
            var cursor = root
            while (size < MAX_COMPACT_GEN2_RECORDS && cursor < bankEnd && rom.u8(cursor) != 0xff) {
                if (cursor + COMPACT_GEN2_RECORD_BYTES > bankEnd) return@runCatching null
                val group = rom.u8(cursor)
                val map = rom.u8(cursor + 1)
                val encounterRate = rom.u8(cursor + 2)
                val baseLevel = rom.u8(cursor + 3)
                val configuration = rom.u8(cursor + 4)
                if (
                    group !in 1..63 || map !in 1..254 || encounterRate == 0 || baseLevel !in 1..100 ||
                    configuration and 0x0f !in levels.weights.indices ||
                    (0 until COMPACT_GEN2_SPECIES_BYTES).any { index ->
                        rom.u8(cursor + COMPACT_GEN2_HEADER_BYTES + index) !in 1..speciesCount
                    }
                ) return@runCatching null
                add(CompactGen2Record(cursor, group, map, baseLevel, configuration))
                cursor += COMPACT_GEN2_RECORD_BYTES
            }
            if (cursor >= bankEnd || rom.u8(cursor) != 0xff) return@runCatching null
        }
        records.takeIf { allowEmpty || it.isNotEmpty() }
    }.getOrNull()

    private fun readCompactGen2Slots(
        rom: RomImage,
        record: CompactGen2Record,
        time: Int,
        levels: CompactGen2LevelAuthority,
    ): List<EncounterSlot> {
        val probability = record.configuration and 0x0f
        val levelProfile = record.configuration ushr 4
        val levelTable = if (levelProfile == 0) {
            0
        } else {
            levels.levelPointers[(levelProfile - 1) * COMPACT_GEN2_TIME_COUNT + time].toInt() and 0xff
        }
        return List(COMPACT_GEN2_SLOT_COUNT) { slot ->
            val adjustment = if (levelTable == 0) {
                0
            } else {
                levels.levelAdjustments[(levelTable - 1) * COMPACT_GEN2_SLOT_COUNT + slot].toInt()
            }
            val minimum = (record.baseLevel + adjustment).coerceIn(1, 100)
            val maximum = (record.baseLevel + adjustment + COMPACT_GEN2_LEVEL_VARIANCE).coerceIn(1, 100)
            EncounterSlot(
                speciesId = rom.u8(
                    record.offset + COMPACT_GEN2_HEADER_BYTES + time * COMPACT_GEN2_SLOT_COUNT + slot,
                ),
                minimumLevel = minimum,
                maximumLevel = maximum,
                weight = levels.weights[probability][slot],
            )
        }
    }

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
                    if (slots.isNotEmpty()) {
                        add(area(groupMapId(group, map), method, "Map $group-$map - $label", slots))
                    }
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
            if (species != 0) {
                add(EncounterSlot(species, rom.u8(entry), rom.u8(entry + 1), weights[index]))
            }
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
    private data class CompactGen2Consumer(
        val bank: Int,
        val johtoGrass: Int,
        val kantoGrass: Int,
        val alternateKantoGrass: Int,
        val swarmGrass: Int,
        val johtoWater: Int,
        val kantoWater: Int,
        val swarmWater: Int,
    )
    private data class CompactGen2LevelAuthority(
        val weights: List<IntArray>,
        val levelPointers: ByteArray,
        val levelAdjustments: ByteArray,
    )
    private data class CompactGen2Record(
        val offset: Int,
        val group: Int,
        val map: Int,
        val baseLevel: Int,
        val configuration: Int,
    )
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
    private const val GEN2_GRASS_HEADER_BYTES = 5
    private const val GEN2_WATER_HEADER_BYTES = 3
    private const val GEN2_TIME_COUNT = 3
    private const val GEN2_SLOT_BYTES = 2
    private const val COMPACT_GEN2_HEADER_BYTES = 5
    private const val COMPACT_GEN2_SLOT_COUNT = 16
    private const val COMPACT_GEN2_TIME_COUNT = 3
    private const val COMPACT_GEN2_SPECIES_BYTES = COMPACT_GEN2_SLOT_COUNT * COMPACT_GEN2_TIME_COUNT
    private const val COMPACT_GEN2_RECORD_BYTES = COMPACT_GEN2_HEADER_BYTES + COMPACT_GEN2_SPECIES_BYTES
    private const val COMPACT_GEN2_CONSUMER_BYTES = 46
    private const val COMPACT_GEN2_KANTO_SELECTOR_BYTES = 13
    private const val COMPACT_GEN2_LEVEL_CONSUMER_BYTES = 88
    private const val COMPACT_GEN2_PROBABILITY_COUNT = 4
    private const val COMPACT_GEN2_PROBABILITY_TOTAL = 200
    private const val COMPACT_GEN2_PROBABILITY_BYTES = COMPACT_GEN2_PROBABILITY_COUNT * COMPACT_GEN2_SLOT_COUNT
    private const val COMPACT_GEN2_LEVEL_POINTER_BYTES = 15 * COMPACT_GEN2_TIME_COUNT
    private const val COMPACT_GEN2_LEVEL_VARIANCE = 4
    private const val MAX_COMPACT_GEN2_RECORDS = 256
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
    private val GEN2_GRASS_LABELS = arrayOf("morning grass", "day grass", "night grass")
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
