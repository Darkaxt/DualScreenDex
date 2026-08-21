package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout

/** Resolves the Gen I TM/HM move list from its compiled search and lookup consumers. */
internal object Gen1CompiledMachineResolver {
    fun resolve(rom: RomImage, moveCount: Int): TableLayout? {
        if (moveCount <= 0) return null
        val roots = canLearnRoots(rom).intersect(tmToMoveRoots(rom))
        val candidates = roots.mapNotNull { root ->
            val count = machineCount(rom, root, moveCount) ?: return@mapNotNull null
            TableLayout(root, count, 1)
        }.distinct()
        return candidates.singleOrNull()
    }

    private fun canLearnRoots(rom: RomImage): Set<Int> = buildSet {
        var offset = 0
        while (offset + CAN_LEARN_SEARCH_BYTES <= rom.size) {
            val hasStandardPrelude = offset >= 7 &&
                rom.u8(offset - 7) == PUSH_HL &&
                rom.u8(offset - 6) == LOAD_A_ABSOLUTE &&
                rom.u8(offset - 3) == LOAD_B_FROM_A &&
                rom.u8(offset - 2) == LOAD_C_IMMEDIATE &&
                rom.u8(offset - 1) == 0
            val hasSimplifiedPrelude = offset >= 4 &&
                rom.u8(offset - 4) == PUSH_HL &&
                rom.u8(offset - 3) == LOAD_B_FROM_A &&
                rom.u8(offset - 2) == LOAD_C_IMMEDIATE &&
                rom.u8(offset - 1) == 0
            val standardSearch =
                rom.u8(offset + 3) == LOAD_A_INCREMENT_HL &&
                    rom.u8(offset + 4) == COMPARE_B &&
                    rom.u8(offset + 5) == JUMP_RELATIVE_ZERO &&
                    rom.u8(offset + 7) == INCREMENT_C &&
                    rom.u8(offset + 8) == JUMP_RELATIVE &&
                    rom.u8(offset + 10) == POP_HL
            val terminatedSearch =
                rom.u8(offset + 3) == LOAD_A_INCREMENT_HL &&
                    rom.u8(offset + 4) == COMPARE_IMMEDIATE &&
                    rom.u8(offset + 5) == 0xFF &&
                    rom.u8(offset + 6) == JUMP_RELATIVE_ZERO &&
                    rom.u8(offset + 8) == COMPARE_B &&
                    rom.u8(offset + 9) == JUMP_RELATIVE_ZERO &&
                    rom.u8(offset + 11) == INCREMENT_C &&
                    rom.u8(offset + 12) == JUMP_RELATIVE &&
                    rom.u8(offset + 14) == POP_HL
            if (
                (hasStandardPrelude || hasSimplifiedPrelude) &&
                rom.u8(offset) == LOAD_HL_IMMEDIATE &&
                (standardSearch || terminatedSearch)
            ) {
                sameBankRoot(rom, offset, rom.u16le(offset + 1))?.let(::add)
            }
            offset++
        }
    }

    private fun tmToMoveRoots(rom: RomImage): Set<Int> = buildSet {
        var offset = 0
        while (offset + TM_TO_MOVE_CONSUMER_BYTES <= rom.size) {
            if (
                rom.u8(offset) == LOAD_A_ABSOLUTE &&
                rom.u8(offset + 3) == DEC_A &&
                rom.u8(offset + 4) == LOAD_HL_IMMEDIATE &&
                rom.u8(offset + 7) == LOAD_B_IMMEDIATE &&
                rom.u8(offset + 8) == 0 &&
                rom.u8(offset + 9) == LOAD_C_FROM_A &&
                rom.u8(offset + 10) == ADD_HL_BC &&
                rom.u8(offset + 11) == LOAD_A_HL &&
                rom.u8(offset + 12) == STORE_A_ABSOLUTE &&
                rom.u16le(offset + 13) == rom.u16le(offset + 1) &&
                rom.u8(offset + 15) == RETURN
            ) {
                sameBankRoot(rom, offset, rom.u16le(offset + 5))?.let(::add)
            }
            offset++
        }
    }

    private fun machineCount(rom: RomImage, root: Int, moveCount: Int): Int? {
        val moves = linkedSetOf<Int>()
        var cursor = root
        while (cursor < rom.size && moves.size <= MAX_MACHINE_COUNT) {
            val move = rom.u8(cursor++)
            if (move !in 1..moveCount || !moves.add(move)) {
                return moves.size.takeIf { it >= MIN_MACHINE_COUNT }
            }
        }
        return null
    }

    private fun sameBankRoot(rom: RomImage, consumerOffset: Int, address: Int): Int? {
        val bank = consumerOffset / BANK_BYTES
        if (bank == 0) return address.takeIf { it in 0 until BANK_BYTES && it < rom.size }
        if (address !in SWITCHABLE_ADDRESS_RANGE) return null
        return rom.gbBankAddress(bank, address)
    }

    private val SWITCHABLE_ADDRESS_RANGE = 0x4000..0x7FFF
    private const val BANK_BYTES = 0x4000
    private const val MIN_MACHINE_COUNT = 55
    private const val MAX_MACHINE_COUNT = 128
    private const val CAN_LEARN_SEARCH_BYTES = 15
    private const val TM_TO_MOVE_CONSUMER_BYTES = 16
    private const val LOAD_B_IMMEDIATE = 0x06
    private const val INCREMENT_C = 0x0C
    private const val LOAD_C_IMMEDIATE = 0x0E
    private const val JUMP_RELATIVE = 0x18
    private const val LOAD_HL_IMMEDIATE = 0x21
    private const val LOAD_A_INCREMENT_HL = 0x2A
    private const val JUMP_RELATIVE_ZERO = 0x28
    private const val DEC_A = 0x3D
    private const val LOAD_B_FROM_A = 0x47
    private const val LOAD_C_FROM_A = 0x4F
    private const val LOAD_A_HL = 0x7E
    private const val COMPARE_B = 0xB8
    private const val STORE_A_ABSOLUTE = 0xEA
    private const val PUSH_HL = 0xE5
    private const val POP_HL = 0xE1
    private const val LOAD_A_ABSOLUTE = 0xFA
    private const val COMPARE_IMMEDIATE = 0xFE
    private const val RETURN = 0xC9
    private const val ADD_HL_BC = 0x09
}
