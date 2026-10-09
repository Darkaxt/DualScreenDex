package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage

/** Qualifies the out-of-domain blank branch separately from the bounded name-table reader. */
internal object CompiledGen3BlankNameConsumer {
    fun isNomination(rom: RomImage, guard: Int): Boolean {
        val entry = guard - 24
        if (entry < 0) return false
        // Either independent entry anchor nominates a competitor; neither grants blank authority.
        return matches(rom, entry, intArrayOf(0xB530, 0x1C05)) ||
            matches(rom, entry + 4, intArrayOf(0x0409, 0x0C09, 0x0412, 0x0C14))
    }

    fun proves(rom: RomImage, guard: Int, table: CompiledGen3RegionEntryTable.Table): Boolean {
        val entry = guard - 24
        if (entry < 0 || guard.toLong() + 88 > rom.size.toLong()) return false
        if (!matches(rom, entry, intArrayOf(0xB530, 0x1C05, 0x0409, 0x0C09, 0x0412, 0x0C14))) return false
        val special = rom.u16le(entry + 12)
        if (special and 0xFF00 != 0x2900 || !table.contains(special and 255) ||
            conditionalTarget(rom, entry + 14, 1) != guard ||
            callTarget(rom, entry + 16) == null || rom.u16le(entry + 20) != 0x1C01 ||
            branchTarget(rom, entry + 22) != guard + 46
        ) return false
        if (rom.u16le(guard) != (0x2900 or (table.count - 1)) ||
            conditionalTarget(rom, guard + 2, 8) != guard + 28 ||
            rom.u16le(guard + 4) and 0xFF00 != 0x4800 ||
            !matches(rom, guard + 6, intArrayOf(0x00C9, 0x3004, 0x1809, 0x6809, 0x1C28)) ||
            callTarget(rom, guard + 16) == null || rom.u16le(guard + 20) != 0x1C01 ||
            branchTarget(rom, guard + 22) != guard + 46
        ) return false
        val load = rom.u16le(guard + 4)
        val literal = ((guard + 8) and -4) + (load and 255) * 4
        if (literal != guard + 24 || rom.gbaPointer(literal) != table.root) return false
        val padding = rom.u16le(guard + 32)
        if (rom.u16le(guard + 28) != 0x2C00 ||
            conditionalTarget(rom, guard + 30, 1) != guard + 34 ||
            padding and 0xFF00 != 0x2400 || padding and 255 == 0 ||
            !matches(rom, guard + 34, intArrayOf(0x1C28, 0x2100, 0x1C22)) ||
            branchTarget(rom, guard + 44) != guard + 82
        ) return false
        val fill = callTarget(rom, guard + 40) ?: return false
        // Check every loop edge, store and saved-register return, not just the fallback call.
        if (rom.u16le(guard + 46) != 0x2C00 ||
            conditionalTarget(rom, guard + 48, 0) != guard + 80 ||
            !matches(rom, guard + 50, intArrayOf(0x1B48, 0x0400, 0x0C00, 0x42A0)) ||
            conditionalTarget(rom, guard + 58, 2) != guard + 76 ||
            !matches(rom, guard + 60, intArrayOf(0x2200, 0x700A, 0x3101, 0x3001, 0x0400, 0x0C00, 0x42A0)) ||
            conditionalTarget(rom, guard + 74, 3) != guard + 62 ||
            !matches(rom, guard + 76, intArrayOf(0x20FF, 0x7008, 0x1C08, 0xBC30, 0xBC02, 0x4708))
        ) return false
        return provesFill(rom, fill)
    }

    private fun provesFill(rom: RomImage, at: Int): Boolean =
        at.toLong() + 42 <= rom.size.toLong() &&
            matches(rom, at, intArrayOf(0xB500, 0x1C03, 0x0609, 0x0E09, 0x0412, 0x0C12, 0x2000, 0x4290)) &&
            conditionalTarget(rom, at + 16, 2) == at + 32 &&
            matches(rom, at + 18, intArrayOf(0x7019, 0x3301, 0x3001, 0x0400, 0x0C00, 0x4290)) &&
            conditionalTarget(rom, at + 30, 3) == at + 18 &&
            matches(rom, at + 32, intArrayOf(0x20FF, 0x7018, 0x1C18, 0xBC02, 0x4708))

    private fun matches(rom: RomImage, at: Int, instructions: IntArray): Boolean =
        instructions.indices.all { rom.u16le(at + it * 2) == instructions[it] }

    private fun conditionalTarget(rom: RomImage, at: Int, condition: Int): Int? {
        val op = rom.u16le(at)
        if (op and 0xFF00 != (0xD000 or (condition shl 8))) return null
        return at + 4 + (op and 255).toByte().toInt() * 2
    }

    private fun branchTarget(rom: RomImage, at: Int): Int? {
        val op = rom.u16le(at)
        if (op and 0xF800 != 0xE000) return null
        val delta = (op and 2047).let { if (it and 1024 != 0) it - 2048 else it }
        return at + 4 + delta * 2
    }

    private fun callTarget(rom: RomImage, at: Int): Int? {
        val high = rom.u16le(at)
        val low = rom.u16le(at + 2)
        if (high and 0xF800 != 0xF000 || low and 0xF800 != 0xF800) return null
        val signed = (high and 2047).let { if (it and 1024 != 0) it - 2048 else it }
        val target = at.toLong() + 4 + (signed.toLong() shl 12) + ((low and 2047) shl 1)
        return target.takeIf { it >= 0 && it + 2 <= rom.size.toLong() }?.toInt()
    }
}
