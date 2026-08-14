package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage

/** Resolves single-root Gen 2 encounter tables from their complete compiled lookup consumers. */
internal object Gen2CompiledEncounterResolver {
    fun resolve(rom: RomImage, speciesCount: Int): Gen2CompiledEncounterResolution {
        val consumers = findConsumers(rom)
        if (consumers.isEmpty()) return Gen2CompiledEncounterResolution(detected = false)
        val layouts = consumers.flatMap { consumer ->
            findProbabilityAuthorities(rom, consumer).mapNotNull { probabilities ->
                buildLayout(rom, speciesCount, consumer, probabilities)
            }
        }.distinctBy { layout ->
            buildString {
                layout.grassTables.forEach { append("${it.offset}:${it.count}:${it.recordSize};") }
                append("${layout.waterTable.offset}:${layout.waterTable.count}:${layout.waterTable.recordSize};")
                append(layout.grassWeights.joinToString(","))
                append(';')
                append(layout.waterWeights.joinToString(","))
            }
        }
        return Gen2CompiledEncounterResolution(
            detected = true,
            layout = layouts.singleOrNull(),
        )
    }

    private fun findConsumers(rom: RomImage): List<EncounterConsumer> = buildList {
        val bankCount = rom.size / BANK_BYTES
        for (bank in 1 until bankCount) {
            val bankStart = bank * BANK_BYTES
            val bankEnd = minOf(bankStart + BANK_BYTES, rom.size)
            var offset = bankStart + LOOKBEHIND_BYTES
            while (offset + CONSUMER_BYTES <= bankEnd) {
                parseConsumerAt(rom, bank, offset)?.let(::add)
                offset++
            }
        }
    }.distinct()

    private fun parseConsumerAt(rom: RomImage, bank: Int, offset: Int): EncounterConsumer? = runCatching {
        val grassRecordSize = rom.u16le(offset + 10)
        val waterRecordSize = rom.u16le(offset + 27)
        val grassSlotCount = grassSlotCount(grassRecordSize) ?: return@runCatching null
        val waterSlotCount = waterSlotCount(waterRecordSize) ?: return@runCatching null
        if (
            rom.u8(offset - 5) != CALL || rom.u8(offset - 2) != JR_Z ||
            branchTarget(offset - 2, rom.u8(offset - 1)) != offset + WATER_LOADER_OFFSET ||
            rom.u8(offset) != LOAD_HL_IMMEDIATE || rom.u8(offset + 3) != LOAD_DE_IMMEDIATE ||
            rom.u8(offset + 6) != CALL || rom.u8(offset + 9) != LOAD_BC_IMMEDIATE ||
            rom.u8(offset + 12) != JR ||
            branchTarget(offset + 12, rom.u8(offset + 13)) != offset + COMMON_LOOKUP_OFFSET ||
            rom.u8(offset + 14) != LOAD_A_ABSOLUTE || rom.u8(offset + 17) != COMPARE_IMMEDIATE ||
            rom.u8(offset + 19) != RETURN_NZ || rom.u8(offset + 20) != LOAD_H_D ||
            rom.u8(offset + 21) != LOAD_L_E || rom.u8(offset + 22) != RETURN ||
            rom.u8(offset + WATER_LOADER_OFFSET) != LOAD_HL_IMMEDIATE ||
            rom.u8(offset + 26) != LOAD_BC_IMMEDIATE ||
            rom.u8(offset + COMMON_LOOKUP_OFFSET) != CALL
        ) return@runCatching null

        val grassRoot = rom.gbBankAddress(bank, rom.u16le(offset + 1)) ?: return@runCatching null
        val alternateGrassRoot = rom.gbBankAddress(bank, rom.u16le(offset + 4)) ?: return@runCatching null
        val waterRoot = rom.gbBankAddress(bank, rom.u16le(offset + 24)) ?: return@runCatching null
        if (setOf(grassRoot, alternateGrassRoot, waterRoot).size != 3) return@runCatching null
        EncounterConsumer(
            bank = bank,
            checkOnWaterCall = rom.u16le(offset - 4),
            grassRoot = grassRoot,
            alternateGrassRoot = alternateGrassRoot,
            grassRecordSize = grassRecordSize,
            grassSlotCount = grassSlotCount,
            waterRoot = waterRoot,
            waterRecordSize = waterRecordSize,
            waterSlotCount = waterSlotCount,
        )
    }.getOrNull()

    private fun findProbabilityAuthorities(
        rom: RomImage,
        consumer: EncounterConsumer,
    ): List<ProbabilityAuthority> {
        val bankStart = consumer.bank * BANK_BYTES
        val bankEnd = minOf(bankStart + BANK_BYTES, rom.size)
        return buildList {
            var offset = bankStart
            while (offset + PROBABILITY_CONSUMER_BYTES <= bankEnd) {
                parseProbabilityAuthorityAt(rom, consumer, offset)?.let(::add)
                offset++
            }
        }.distinctBy { it.grassWeights.joinToString(",") + ";" + it.waterWeights.joinToString(",") }
    }

    private fun parseProbabilityAuthorityAt(
        rom: RomImage,
        consumer: EncounterConsumer,
        offset: Int,
    ): ProbabilityAuthority? = runCatching {
        if (
            rom.u8(offset) != INC_HL || rom.u8(offset + 1) != INC_HL || rom.u8(offset + 2) != INC_HL ||
            rom.u8(offset + 3) != CALL || rom.u16le(offset + 4) != consumer.checkOnWaterCall ||
            rom.u8(offset + 6) != LOAD_DE_IMMEDIATE || rom.u8(offset + 9) != JR_Z ||
            branchTarget(offset + 9, rom.u8(offset + 10)) != offset + PROBABILITY_LOOP_OFFSET ||
            rom.u8(offset + 11) != INC_HL || rom.u8(offset + 12) != INC_HL ||
            rom.u8(offset + 13) != LOAD_A_ABSOLUTE || rom.u8(offset + 16) != LOAD_BC_IMMEDIATE ||
            rom.u16le(offset + 17) != consumer.grassSlotCount * SLOT_BYTES ||
            rom.u8(offset + 19) != CALL || rom.u8(offset + 22) != LOAD_DE_IMMEDIATE ||
            rom.u8(offset + PROBABILITY_LOOP_OFFSET) != PUSH_HL || rom.u8(offset + 26) != CALL ||
            rom.u8(offset + 29) != COMPARE_IMMEDIATE || rom.u8(offset + 30) != PROBABILITY_TOTAL ||
            rom.u8(offset + 31) != JR_NC ||
            branchTarget(offset + 31, rom.u8(offset + 32)) != offset + 26 ||
            rom.u8(offset + 33) != INC_A || rom.u8(offset + 34) != LOAD_B_A ||
            rom.u8(offset + 35) != LOAD_H_D || rom.u8(offset + 36) != LOAD_L_E ||
            rom.u8(offset + 37) != LOAD_A_HL_INCREMENT || rom.u8(offset + 38) != COMPARE_B ||
            rom.u8(offset + 39) != JR_NC ||
            branchTarget(offset + 39, rom.u8(offset + 40)) != offset + 44 ||
            rom.u8(offset + 41) != INC_HL || rom.u8(offset + 42) != JR ||
            branchTarget(offset + 42, rom.u8(offset + 43)) != offset + 37
        ) return@runCatching null

        val waterRoot = rom.gbBankAddress(consumer.bank, rom.u16le(offset + 7)) ?: return@runCatching null
        val grassRoot = rom.gbBankAddress(consumer.bank, rom.u16le(offset + 23)) ?: return@runCatching null
        val grassWeights = parseProbabilityTable(rom, grassRoot, consumer.grassSlotCount)
            ?: return@runCatching null
        val waterWeights = parseProbabilityTable(rom, waterRoot, consumer.waterSlotCount)
            ?: return@runCatching null
        ProbabilityAuthority(grassWeights, waterWeights)
    }.getOrNull()

    private fun parseProbabilityTable(rom: RomImage, offset: Int, slotCount: Int): IntArray? = runCatching {
        if (offset + slotCount * PROBABILITY_ROW_BYTES > rom.size) return@runCatching null
        val weights = IntArray(slotCount)
        var previous = 0
        repeat(slotCount) { slot ->
            val cumulative = rom.u8(offset + slot * PROBABILITY_ROW_BYTES)
            if (
                cumulative <= previous || cumulative > PROBABILITY_TOTAL ||
                rom.u8(offset + slot * PROBABILITY_ROW_BYTES + 1) != slot * SLOT_BYTES
            ) return@runCatching null
            weights[slot] = cumulative - previous
            previous = cumulative
        }
        weights.takeIf { previous == PROBABILITY_TOTAL }
    }.getOrNull()

    private fun buildLayout(
        rom: RomImage,
        speciesCount: Int,
        consumer: EncounterConsumer,
        probabilities: ProbabilityAuthority,
    ): Gen2CompiledEncounterLayout? {
        val grass = readTable(
            rom,
            consumer.grassRoot,
            consumer.grassRecordSize,
            consumer.grassSlotCount,
            speciesCount,
            grass = true,
            allowEmpty = false,
        ) ?: return null
        val alternateGrass = readTable(
            rom,
            consumer.alternateGrassRoot,
            consumer.grassRecordSize,
            consumer.grassSlotCount,
            speciesCount,
            grass = true,
            allowEmpty = true,
        ) ?: return null
        val water = readTable(
            rom,
            consumer.waterRoot,
            consumer.waterRecordSize,
            consumer.waterSlotCount,
            speciesCount,
            grass = false,
            allowEmpty = false,
        ) ?: return null
        return Gen2CompiledEncounterLayout(
            grassTables = listOf(grass, alternateGrass),
            grassSlotCount = consumer.grassSlotCount,
            grassWeights = probabilities.grassWeights,
            waterTable = water,
            waterSlotCount = consumer.waterSlotCount,
            waterWeights = probabilities.waterWeights,
        )
    }

    private fun readTable(
        rom: RomImage,
        root: Int,
        recordSize: Int,
        slotCount: Int,
        speciesCount: Int,
        grass: Boolean,
        allowEmpty: Boolean,
    ): Gen2CompiledEncounterTable? = runCatching {
        val bankEnd = minOf((root / BANK_BYTES + 1) * BANK_BYTES, rom.size)
        var cursor = root
        var count = 0
        var maximumSpeciesId = 0
        while (count < MAX_RECORDS && cursor < bankEnd && rom.u8(cursor) != END_MARKER) {
            if (cursor + recordSize > bankEnd) return@runCatching null
            if (rom.u8(cursor) !in 1..MAX_MAP_GROUP || rom.u8(cursor + 1) !in 1..MAX_MAP_NUMBER) {
                return@runCatching null
            }
            if (grass) {
                if ((0 until TIME_COUNT).any { rom.u8(cursor + 2 + it) !in 1..MAX_ENCOUNTER_RATE }) {
                    return@runCatching null
                }
                if ((0 until TIME_COUNT * slotCount).any { slot ->
                        !validSlot(rom, cursor + GRASS_HEADER_BYTES + slot * SLOT_BYTES, speciesCount)
                    }
                ) return@runCatching null
            } else {
                if (rom.u8(cursor + 2) !in 1..MAX_ENCOUNTER_RATE) return@runCatching null
                if ((0 until slotCount).any { slot ->
                        !validSlot(rom, cursor + WATER_HEADER_BYTES + slot * SLOT_BYTES, speciesCount)
                    }
                ) return@runCatching null
            }
            val slotTotal = if (grass) TIME_COUNT * slotCount else slotCount
            val slotRoot = cursor + if (grass) GRASS_HEADER_BYTES else WATER_HEADER_BYTES
            repeat(slotTotal) { slot ->
                maximumSpeciesId = maxOf(maximumSpeciesId, rom.u8(slotRoot + slot * SLOT_BYTES + 1))
            }
            count++
            cursor += recordSize
        }
        if (cursor >= bankEnd || rom.u8(cursor) != END_MARKER || (!allowEmpty && count == 0)) {
            return@runCatching null
        }
        Gen2CompiledEncounterTable(root, count, recordSize, maximumSpeciesId)
    }.getOrNull()

    private fun validSlot(rom: RomImage, offset: Int, speciesCount: Int): Boolean =
        rom.u8(offset) in 1..MAX_LEVEL && rom.u8(offset + 1) in 1..speciesCount

    private fun grassSlotCount(recordSize: Int): Int? {
        val payload = recordSize - GRASS_HEADER_BYTES
        if (payload <= 0 || payload % (TIME_COUNT * SLOT_BYTES) != 0) return null
        return (payload / (TIME_COUNT * SLOT_BYTES)).takeIf { it in 1..MAX_SLOT_COUNT }
    }

    private fun waterSlotCount(recordSize: Int): Int? {
        val payload = recordSize - WATER_HEADER_BYTES
        if (payload <= 0 || payload % SLOT_BYTES != 0) return null
        return (payload / SLOT_BYTES).takeIf { it in 1..MAX_SLOT_COUNT }
    }

    private fun branchTarget(opcodeOffset: Int, encodedDelta: Int): Int =
        opcodeOffset + 2 + encodedDelta.toByte().toInt()

    private data class EncounterConsumer(
        val bank: Int,
        val checkOnWaterCall: Int,
        val grassRoot: Int,
        val alternateGrassRoot: Int,
        val grassRecordSize: Int,
        val grassSlotCount: Int,
        val waterRoot: Int,
        val waterRecordSize: Int,
        val waterSlotCount: Int,
    )

    private data class ProbabilityAuthority(
        val grassWeights: IntArray,
        val waterWeights: IntArray,
    )

    private const val BANK_BYTES = 0x4000
    private const val LOOKBEHIND_BYTES = 5
    private const val CONSUMER_BYTES = 32
    private const val PROBABILITY_CONSUMER_BYTES = 44
    private const val WATER_LOADER_OFFSET = 23
    private const val COMMON_LOOKUP_OFFSET = 29
    private const val PROBABILITY_LOOP_OFFSET = 25
    private const val GRASS_HEADER_BYTES = 5
    private const val WATER_HEADER_BYTES = 3
    private const val TIME_COUNT = 3
    private const val SLOT_BYTES = 2
    private const val PROBABILITY_ROW_BYTES = 2
    private const val PROBABILITY_TOTAL = 100
    private const val MAX_SLOT_COUNT = 16
    private const val MAX_RECORDS = 256
    private const val MAX_MAP_GROUP = 63
    private const val MAX_MAP_NUMBER = 254
    private const val MAX_ENCOUNTER_RATE = 0xff
    private const val MAX_LEVEL = 100
    private const val END_MARKER = 0xff

    private const val LOAD_BC_IMMEDIATE = 0x01
    private const val LOAD_DE_IMMEDIATE = 0x11
    private const val LOAD_HL_IMMEDIATE = 0x21
    private const val INC_HL = 0x23
    private const val INC_A = 0x3c
    private const val LOAD_B_A = 0x47
    private const val LOAD_H_D = 0x62
    private const val LOAD_L_E = 0x6b
    private const val LOAD_A_HL_INCREMENT = 0x2a
    private const val COMPARE_B = 0xb8
    private const val LOAD_A_ABSOLUTE = 0xfa
    private const val COMPARE_IMMEDIATE = 0xfe
    private const val PUSH_HL = 0xe5
    private const val CALL = 0xcd
    private const val RETURN = 0xc9
    private const val RETURN_NZ = 0xc0
    private const val JR = 0x18
    private const val JR_Z = 0x28
    private const val JR_NC = 0x30
}

internal data class Gen2CompiledEncounterResolution(
    val detected: Boolean,
    val layout: Gen2CompiledEncounterLayout? = null,
)

internal data class Gen2CompiledEncounterLayout(
    val grassTables: List<Gen2CompiledEncounterTable>,
    val grassSlotCount: Int,
    val grassWeights: IntArray,
    val waterTable: Gen2CompiledEncounterTable,
    val waterSlotCount: Int,
    val waterWeights: IntArray,
) {
    val maximumSpeciesId: Int
        get() = maxOf(grassTables.maxOf { it.maximumSpeciesId }, waterTable.maximumSpeciesId)
}

internal data class Gen2CompiledEncounterTable(
    val offset: Int,
    val count: Int,
    val recordSize: Int,
    val maximumSpeciesId: Int,
)
