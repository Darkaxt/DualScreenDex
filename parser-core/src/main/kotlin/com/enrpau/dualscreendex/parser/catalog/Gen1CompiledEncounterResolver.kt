package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage

internal data class Gen1CompiledEncounterLayout(
    val offset: Int,
    val bank: Int,
    val count: Int,
)

internal data class Gen1CompiledEncounterResolution(
    val detected: Boolean,
    val layout: Gen1CompiledEncounterLayout? = null,
)

/** Resolves a Gen I wild-data pointer table from its complete compiled loader. */
internal object Gen1CompiledEncounterResolver {
    fun resolve(rom: RomImage, speciesCount: Int): Gen1CompiledEncounterResolution {
        if (speciesCount !in 1..MAX_SPECIES_COUNT) return Gen1CompiledEncounterResolution(false)
        var detected = false
        val candidates = buildList {
            var offset = 0
            while (offset + CONSUMER_BYTES <= rom.size) {
                if (isConsumerAt(rom, offset)) {
                    detected = true
                    parseLayout(rom, offset, speciesCount)?.let(::add)
                }
                offset++
            }
        }.distinct()
        return Gen1CompiledEncounterResolution(detected, candidates.singleOrNull())
    }

    private fun isConsumerAt(rom: RomImage, offset: Int): Boolean = runCatching {
        rom.u8(offset) == LOAD_HL_IMMEDIATE && rom.u8(offset + 3) == LOAD_A_ABSOLUTE &&
            rom.u8(offset + 6) == LOAD_C_A && rom.u8(offset + 7) == LOAD_B_IMMEDIATE &&
            rom.u8(offset + 8) == 0 && rom.u8(offset + 9) == ADD_HL_BC &&
            rom.u8(offset + 10) == ADD_HL_BC && rom.u8(offset + 11) == LOAD_A_HL_INCREMENT &&
            rom.u8(offset + 12) == LOAD_H_HL && rom.u8(offset + 13) == LOAD_L_A &&
            rom.u8(offset + 14) == LOAD_A_HL_INCREMENT && rom.u8(offset + 15) == STORE_A_ABSOLUTE &&
            rom.u8(offset + 18) == AND_A && rom.u8(offset + 19) == JUMP_RELATIVE_ZERO &&
            rom.u8(offset + 21) == PUSH_HL && rom.u8(offset + 22) == LOAD_DE_IMMEDIATE &&
            rom.u8(offset + 25) == LOAD_BC_IMMEDIATE && rom.u16le(offset + 26) == SLOT_BYTES &&
            rom.u8(offset + 28) == CALL && rom.u8(offset + 31) == POP_HL &&
            rom.u8(offset + 32) == LOAD_BC_IMMEDIATE && rom.u16le(offset + 33) == SLOT_BYTES &&
            rom.u8(offset + 35) == ADD_HL_BC && rom.u8(offset + 36) == LOAD_A_HL_INCREMENT &&
            rom.u8(offset + 37) == STORE_A_ABSOLUTE && rom.u8(offset + 40) == AND_A &&
            rom.u8(offset + 41) == RETURN_ZERO && rom.u8(offset + 42) == LOAD_DE_IMMEDIATE &&
            rom.u8(offset + 45) == LOAD_BC_IMMEDIATE && rom.u16le(offset + 46) == SLOT_BYTES &&
            rom.u8(offset + 48) == JUMP &&
            rom.u16le(offset + 49) == rom.u16le(offset + 29)
    }.getOrDefault(false)

    private fun parseLayout(
        rom: RomImage,
        consumerOffset: Int,
        speciesCount: Int,
    ): Gen1CompiledEncounterLayout? = runCatching {
        val bank = consumerOffset / GB_BANK_SIZE
        if (bank == 0) return@runCatching null
        val root = rom.gbBankAddress(bank, rom.u16le(consumerOffset + 1)) ?: return@runCatching null
        val bankEnd = minOf(rom.size, (bank + 1) * GB_BANK_SIZE)
        var count = 0
        while (count < MAX_MAP_COUNT && root + count * 2 + 1 < bankEnd) {
            val pointer = rom.u16le(root + count * 2)
            if (pointer == POINTER_TERMINATOR) break
            if (pointer !in GB_SWITCHABLE_ADDRESS) return@runCatching null
            count++
        }
        if (count !in MIN_MAP_COUNT..MAX_MAP_COUNT || root + count * 2 + 1 >= bankEnd ||
            rom.u16le(root + count * 2) != POINTER_TERMINATOR
        ) return@runCatching null

        var encounteredMaps = 0
        repeat(count) { mapId ->
            val target = rom.gbBankAddress(bank, rom.u16le(root + mapId * 2))
                ?: return@runCatching null
            val populated = validateRecord(rom, target, speciesCount) ?: return@runCatching null
            if (populated) encounteredMaps++
        }
        if (encounteredMaps < MIN_ENCOUNTER_MAPS) return@runCatching null
        Gen1CompiledEncounterLayout(root, bank, count)
    }.getOrNull()

    private fun validateRecord(rom: RomImage, offset: Int, speciesCount: Int): Boolean? = runCatching {
        var cursor = offset
        val grassRate = rom.u8(cursor++)
        if (grassRate > 0) {
            if (!validateSlots(rom, cursor, speciesCount)) return@runCatching null
            cursor += SLOT_BYTES
        }
        val waterRate = rom.u8(cursor++)
        if (waterRate > 0 && !validateSlots(rom, cursor, speciesCount)) return@runCatching null
        grassRate > 0 || waterRate > 0
    }.getOrNull()

    private fun validateSlots(rom: RomImage, offset: Int, speciesCount: Int): Boolean =
        (0 until SLOT_COUNT).all { index ->
            val entry = offset + index * 2
            rom.u8(entry) in 1..MAX_LEVEL && rom.u8(entry + 1) in 1..speciesCount
        }

    private val GB_SWITCHABLE_ADDRESS = 0x4000..0x7fff
    private const val GB_BANK_SIZE = 0x4000
    private const val CONSUMER_BYTES = 51
    private const val SLOT_COUNT = 10
    private const val SLOT_BYTES = SLOT_COUNT * 2
    private const val MIN_MAP_COUNT = 64
    private const val MAX_MAP_COUNT = 256
    private const val MIN_ENCOUNTER_MAPS = 10
    private const val MAX_SPECIES_COUNT = 254
    private const val MAX_LEVEL = 100
    private const val POINTER_TERMINATOR = 0xffff
    private const val LOAD_B_IMMEDIATE = 0x06
    private const val ADD_HL_BC = 0x09
    private const val LOAD_DE_IMMEDIATE = 0x11
    private const val LOAD_HL_IMMEDIATE = 0x21
    private const val LOAD_A_HL_INCREMENT = 0x2a
    private const val LOAD_C_A = 0x4f
    private const val LOAD_H_HL = 0x66
    private const val LOAD_L_A = 0x6f
    private const val AND_A = 0xa7
    private const val RETURN_ZERO = 0xc8
    private const val JUMP = 0xc3
    private const val PUSH_HL = 0xe5
    private const val POP_HL = 0xe1
    private const val LOAD_A_ABSOLUTE = 0xfa
    private const val STORE_A_ABSOLUTE = 0xea
    private const val JUMP_RELATIVE_ZERO = 0x28
    private const val LOAD_BC_IMMEDIATE = 0x01
    private const val CALL = 0xcd
}
