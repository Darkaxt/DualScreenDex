package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat

/** Eight-byte moves linked to complete copying, category reads, and bank restoration. */
internal object Gen2CompactMoveResolver {
    fun resolve(rom: RomImage, moveCount: Int, typeIds: Set<Int>): TableLayout? {
        if (moveCount !in 1..255 || typeIds.isEmpty() || typeIds.any { it !in 0..255 }) return null
        val candidates = (0..minOf(0x4000, rom.size) - 24).mapNotNull { offset ->
            if (!homeBytes(rom, offset, 0x3D, 0x21)) return@mapNotNull null
            parseConsumer(rom, offset, moveCount, typeIds)
        }
        return candidates.distinct().singleOrNull()
    }

    private fun parseConsumer(
        rom: RomImage,
        offset: Int,
        count: Int,
        typeIds: Set<Int>,
    ): TableLayout? {
        if (!homeBytes(rom, offset + 4, 0x01, 0x08, 0x00) ||
            !homeBytes(rom, offset + 8, 0x3E) ||
            !homeBytes(rom, offset + 10, 0xD5, 0xCD) ||
            !homeBytes(rom, offset + 14, 0xE1, 0xCD) ||
            !homeBytes(rom, offset + 18, 0x01, 0x07, 0x00, 0x09, 0x77, 0xC9) ||
            !restartMultiply(rom, rom.u8(offset + 7))
        ) return null
        val copy = GbCompiledFarCopy.resolve(rom, rom.u16le(offset + 12)) ?: return null
        val bank = rom.u8(offset + 9).takeIf { it in 1..copy.maximumBank } ?: return null
        val address = rom.u16le(offset + 2)
        if (address !in 0x4000..0x7FFF || address.toLong() + count * 8 > 0x8000) return null
        val root = rom.gbBankAddress(bank, address) ?: return null
        if (root.toLong() + count * 8 > rom.size ||
            !categoryConsumer(rom, rom.u16le(offset + 16), bank, copy, typeIds)
        ) return null
        for (index in 0 until count) {
            val row = root + index * 8
            if (rom.u8(row) != index + 1 || rom.u8(row + 3) !in typeIds ||
                rom.u8(row + 5) !in 1..64 || rom.u8(row + 7) !in 0..2
            ) return null
        }
        return TableLayout(root, count, 8, bank = bank, format = TableRecordFormat.GEN2_SPLIT_MOVE_8)
    }

    private fun categoryConsumer(
        rom: RomImage,
        offset: Int,
        bank: Int,
        copy: GbCompiledFarCopyAuthority,
        typeIds: Set<Int>,
    ): Boolean {
        if (!within(rom, offset, 40) ||
            !homeBytes(rom, offset, 0xE5, 0x01, 0x07, 0x00, 0x09, 0x3E, bank, 0xCD) ||
            !homeBytes(rom, offset + 10, 0xE1, 0xFE, 0x02, 0xC8, 0x47, 0xFA) ||
            !homeBytes(rom, offset + 18, 0xCB) || rom.u8(offset + 19) and 0xC7 != 0x47 ||
            !homeBytes(rom, offset + 20, 0x78, 0xC0, 0xE5, 0x01, 0x03, 0x00, 0x09, 0x3E, bank, 0xCD) ||
            !homeBytes(rom, offset + 32, 0xE1, 0xFE) ||
            !homeBytes(rom, offset + 35, 0x3E, 0x00, 0xD8, 0x3C, 0xC9)
        ) return false
        val options = rom.u16le(offset + 16)
        val threshold = rom.u8(offset + 34)
        if (options !in 0xC000..0xDFFF || threshold == 0 || threshold !in typeIds) return false
        val read = rom.u16le(offset + 8)
        return read == rom.u16le(offset + 30) && GbCompiledFarCopy.readByte(rom, read) == copy
    }

    private fun restartMultiply(rom: RomImage, opcode: Int): Boolean {
        if (opcode and 0xC7 != 0xC7) return false
        val vector = opcode and 0x38
        if (!within(rom, vector, 3)) return false
        val target = if (rom.u8(vector) == 0xC3) rom.u16le(vector + 1) else vector
        return GbCompiledBankCalls.repeatedAdd(rom, target)
    }

    private fun homeBytes(rom: RomImage, offset: Int, vararg values: Int): Boolean =
        within(rom, offset, values.size) && values.indices.all { rom.u8(offset + it) == values[it] }

    private fun within(rom: RomImage, offset: Int, count: Int): Boolean =
        offset >= 0 && offset.toLong() + count <= minOf(0x4000, rom.size)
}
