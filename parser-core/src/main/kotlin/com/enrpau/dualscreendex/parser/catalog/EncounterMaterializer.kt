package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout

object EncounterMethods {
    const val GRASS = 1
    const val WATER = 2
    const val ROCK_SMASH = 3
    const val FISHING = 4
    const val GRASS_MORNING = 5
    const val GRASS_DAY = 6
    const val GRASS_NIGHT = 7
}

object EncounterMaterializer {
    fun materialize(rom: RomImage, layout: ResolvedRomLayout): List<EncounterArea> = when (layout.generation) {
        1 -> gen1(rom, layout.speciesCount ?: 190)
        2 -> gen2(rom, layout.speciesCount ?: 251)
        3 -> gen3(rom, layout.speciesCount ?: 412)
        else -> emptyList()
    }

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

    private fun gen3(rom: RomImage, speciesCount: Int): List<EncounterArea> {
        val anchored = runCatching { rom.gbaPointer(CFRU_WILD_HEADER_POINTER) }.getOrNull()
            ?.let { table -> table to anchoredGen3HeaderCount(rom, table, speciesCount) }
            ?.takeIf { (_, count) -> count > 0 }
        val (bestOffset, bestCount) = anchored ?: findGen3HeaderTable(rom, speciesCount) ?: return emptyList()
        val decoded = buildList {
            repeat(bestCount) { index ->
                val header = bestOffset + index * GEN3_HEADER_SIZE
                if (!validGen3Header(rom, header, speciesCount)) return@repeat
                val group = rom.u8(header)
                val map = rom.u8(header + 1)
                GEN3_METHODS.forEachIndexed { methodIndex, method ->
                    val info = rom.gbaPointer(header + 4 + methodIndex * 4) ?: return@forEachIndexed
                    val slotPointer = rom.gbaPointer(info + 4) ?: return@forEachIndexed
                    val slots = readGen3Slots(rom, slotPointer, GEN3_SLOT_COUNTS[methodIndex], GEN3_WEIGHTS[methodIndex])
                    add(area(groupMapId(group, map), method, "Map $group-$map - ${GEN3_LABELS[methodIndex]}", slots))
                }
            }
        }
        return decoded.groupBy { it.id }.values.map { variants ->
            variants.first().copy(slots = variants.flatMap { it.slots }.distinct())
        }
    }

    private fun findGen3HeaderTable(rom: RomImage, speciesCount: Int): Pair<Int, Int>? {
        var bestOffset: Int? = null
        var bestCount = 0
        var offset = 0
        while (offset <= rom.size - GEN3_HEADER_SIZE) {
            if (offset % 4 == 0 && validGen3Header(rom, offset, speciesCount)) {
                val count = gen3HeaderCount(rom, offset, speciesCount)
                val cursor = offset + count * GEN3_HEADER_SIZE
                if (count >= 3 && cursor + 1 < rom.size && rom.u8(cursor) == 0xFF && rom.u8(cursor + 1) == 0xFF && count > bestCount) {
                    bestOffset = offset
                    bestCount = count
                }
            }
            offset += 4
        }
        return bestOffset?.let { it to bestCount }
    }

    private fun gen3HeaderCount(rom: RomImage, offset: Int, speciesCount: Int): Int {
        var count = 0
        var cursor = offset
        while (count < 1024 && cursor + GEN3_HEADER_SIZE <= rom.size) {
            val group = rom.u8(cursor)
            val map = rom.u8(cursor + 1)
            if (group == 0xFF && map == 0xFF) break
            if (!validGen3Header(rom, cursor, speciesCount)) return 0
            count++
            cursor += GEN3_HEADER_SIZE
        }
        return count
    }

    private fun anchoredGen3HeaderCount(rom: RomImage, offset: Int, speciesCount: Int): Int {
        var count = 0
        var valid = 0
        while (count < 1024 && offset + (count + 1) * GEN3_HEADER_SIZE <= rom.size) {
            val header = offset + count * GEN3_HEADER_SIZE
            if (rom.u8(header) == 0xFF && rom.u8(header + 1) == 0xFF) break
            if (validGen3Header(rom, header, speciesCount)) valid++
            count++
        }
        return count.takeIf { it > 0 && valid.toDouble() / it >= 0.75 } ?: 0
    }

    private fun validGen3Header(rom: RomImage, offset: Int, speciesCount: Int): Boolean = runCatching {
        val group = rom.u8(offset)
        val map = rom.u8(offset + 1)
        if (!validGroupMap(group, map) || rom.u8(offset + 2) != 0 || rom.u8(offset + 3) != 0) return@runCatching false
        var populated = 0
        repeat(4) { method ->
            val raw = rom.u32le(offset + 4 + method * 4)
            if (raw != 0L) {
                val info = rom.gbaPointer(offset + 4 + method * 4) ?: return@runCatching false
                if (!validGen3Info(rom, info, GEN3_SLOT_COUNTS[method], speciesCount)) return@runCatching false
                populated++
            }
        }
        populated > 0
    }.getOrDefault(false)

    private fun validGen3Info(rom: RomImage, offset: Int, slotCount: Int, speciesCount: Int): Boolean = runCatching {
        if ((1..3).any { rom.u8(offset + it) != 0 }) return@runCatching false
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
        name = CatalogField.available(name),
        methodId = method,
        slots = slots,
    )

    private fun groupMapId(group: Int, map: Int): Int = (group shl 8) or map

    private fun rangesOverlap(firstStart: Int, firstEnd: Int, secondStart: Int, secondEnd: Int): Boolean =
        firstStart < secondEnd && secondStart < firstEnd

    private data class Gen1PointerTable(val offset: Int, val bank: Int, val encounteredMaps: Int)
    private data class FixedSequence(val offset: Int, val count: Int, val endExclusive: Int)

    private const val GB_BANK_SIZE = 0x4000
    private val GB_SWITCHABLE_ADDRESS = 0x4000..0x7FFF
    private const val GEN3_HEADER_SIZE = 20
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
}
