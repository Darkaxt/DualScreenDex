package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionTableLayout
import com.enrpau.dualscreendex.parser.io.RomImage

/** Finite data-flow proof for a species lookup result consumed by the selected dex dimensions ABI. */
internal object CompiledDescriptionIndexBinding {
    fun mappingWrapperReturnsRow(rom: RomImage, entry: Int): Boolean {
        if (entry !in 0..rom.size - 34) return false
        // Both proven exits POP {r1}; BX r1, so the stack must contain only the caller's LR.
        // Additional saved registers would be consumed as the return address instead.
        return rom.u16le(entry) == 0xb500 && rom.u16le(entry + 8) == 0xd008 &&
            (rom.u16le(entry + 10) ushr 8) and 7 != 1 &&
            rom.u16le(entry + 12) == 0x3901 &&
            rom.u16le(entry + 18) and 0x7 == 0 &&
            rom.u16le(entry + 28) == 0x2000 &&
            returnsR0(rom, entry + 20) && returnsR0(rom, entry + 30)
    }

    fun matchesAccessor(rom: RomImage, entry: Int, table: DescriptionTableLayout): Boolean {
        if (table.recordSize !in setOf(28, 32, 36) || entry !in 0..rom.size - 22) return false
        // u16 index in r2; u8 selector in r1; independent selector-zero and selector-one branches.
        val prefix = listOf(0xb500, 0x0400, 0x0c02, 0x0609, 0x0e09, 0x2900)
        if (prefix.indices.any { rom.u16le(entry + it * 2) != prefix[it] } ||
            rom.u16le(entry + 12) and 0xff00 != 0xd000 ||
            rom.u16le(entry + 14) != 0x2901 ||
            rom.u16le(entry + 16) and 0xff00 != 0xd000
        ) return false
        val height = if (table.recordSize == 28) 6 else 12
        val weight = if (table.recordSize == 28) 8 else 14
        return matchesField(rom, branchTarget(rom, entry + 12, conditional = true), table, height) &&
            matchesField(rom, branchTarget(rom, entry + 16, conditional = true), table, weight)
    }

    private data class Address(val indexScale: Long, val constant: Long) {
        fun plus(other: Address) = Address(indexScale + other.indexScale, constant + other.constant)
        fun minus(other: Address) = Address(indexScale - other.indexScale, constant - other.constant)
    }

    private fun matchesField(rom: RomImage, start: Int, table: DescriptionTableLayout, field: Int): Boolean {
        val registers = arrayOfNulls<Address>(8)
        registers[2] = Address(1, 0)
        var pc = start
        // Only straight-line scale/root arithmetic is allowed; unknown instructions fail closed.
        repeat(8) {
            if (pc !in 0..rom.size - 2) return false
            val instruction = rom.u16le(pc)
            val destination = instruction and 7
            when {
                instruction and 0xf800 == 0x4800 -> {
                    val literal = ((pc + 4) and -4) + (instruction and 255) * 4
                    if (literal !in 0..rom.size - 4) return false
                    registers[(instruction ushr 8) and 7] = Address(0, rom.u32le(literal))
                }
                instruction and 0xf800 == 0 -> {
                    val value = registers[(instruction ushr 3) and 7] ?: return false
                    val shift = (instruction ushr 6) and 31
                    if (shift > 5) return false
                    registers[destination] = Address(value.indexScale shl shift, value.constant shl shift)
                }
                instruction and 0xfc00 == 0x1800 -> {
                    val left = registers[(instruction ushr 3) and 7] ?: return false
                    val right = registers[(instruction ushr 6) and 7] ?: return false
                    registers[destination] = if (instruction and 0x0200 == 0) left.plus(right) else left.minus(right)
                }
                instruction and 0xffc0 == 0x1c00 -> {
                    registers[destination] = registers[(instruction ushr 3) and 7] ?: return false
                }
                instruction and 0xf800 == 0x8800 -> {
                    val address = registers[(instruction ushr 3) and 7] ?: return false
                    val displacement = ((instruction ushr 6) and 31) * 2
                    return destination == 0 && address.indexScale == table.recordSize.toLong() &&
                        address.constant + displacement == 0x08000000L + table.offset + field &&
                        returnsR0(rom, pc + 2)
                }
                else -> return false
            }
            pc += 2
        }
        return false
    }

    private fun returnsR0(rom: RomImage, start: Int): Boolean {
        var pc = start
        if (pc !in 0..rom.size - 4) return false
        if (rom.u16le(pc) and 0xf800 == 0xe000) pc = branchTarget(rom, pc, conditional = false)
        return pc in 0..rom.size - 4 && rom.u16le(pc) == 0xbc02 && rom.u16le(pc + 2) == 0x4708
    }

    private fun branchTarget(rom: RomImage, pc: Int, conditional: Boolean): Int {
        val bits = if (conditional) 8 else 11
        val mask = (1 shl bits) - 1
        var displacement = rom.u16le(pc) and mask
        if (displacement and (1 shl (bits - 1)) != 0) displacement -= 1 shl bits
        return pc + 4 + displacement * 2
    }
}
