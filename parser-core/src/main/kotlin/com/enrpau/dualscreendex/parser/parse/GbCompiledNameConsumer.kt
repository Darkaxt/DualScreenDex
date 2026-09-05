package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily

/** Complete repeated-add/copy ABI, not a language-based record-width guess.
 * Native RB uses inline banking; Yellow calls the same bounded bank-store helper on entry/exit.
 * Gen II uses RST $10. Source stride, copy length and appended destination terminator must agree.
 */
internal object GbCompiledNameConsumer {
    data class Root(val offset: Int, val width: Int, val gen1Family: EngineFamily? = null)

    fun discover(
        rom: RomImage,
        generation: Int,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): List<Root> {
        cancellation.throwIfCancellationRequested()
        if (generation !in 1..2) return emptyList()
        val end = minOf(0x4000, rom.size)
        val roots = linkedSetOf<Root>()
        // One home-bank walk bounds both work and the number of possible complete consumers.
        for (offset in 0 until end) {
            cancellation.throwIfCancellationRequested()
            parse(rom, offset, end, generation)?.let(roots::add)
        }
        return roots.toList()
    }

    private fun parse(rom: RomImage, start: Int, end: Int, generation: Int): Root? {
        var p = start
        fun byte(): Int = if (p < end) rom.u8(p++) else -1
        fun word(): Int { val lo = byte(); val hi = byte(); return if (lo < 0 || hi < 0) -1 else lo or (hi shl 8) }
        fun expect(vararg bytes: Int): Boolean = bytes.all { byte() == it }
        val bank: Int
        val bankRegister: Int
        var bankStore = -1
        var helper = -1
        if (generation == 1) {
            if (!expect(0xe5, 0xf0)) return null
            bankRegister = byte()
            if (!expect(0xf5, 0x3e)) return null
            bank = byte()
            when (byte()) {
                0xe0 -> {
                    if (!expect(bankRegister, 0xea)) return null
                    bankStore = word()
                    if (bankStore !in 0x2000..0x3fff) return null
                }
                0xcd -> {
                    helper = word()
                    if (helper < 0 || helper + 6 > end || rom.u8(helper) != 0xe0 ||
                        rom.u8(helper + 1) != bankRegister || rom.u8(helper + 2) != 0xea ||
                        rom.u16le(helper + 3) !in 0x2000..0x3fff || rom.u8(helper + 5) != 0xc9
                    ) return null
                }
                else -> return null
            }
        } else {
            if (!expect(0xf0)) return null
            bankRegister = byte()
            if (!expect(0xf5, 0xe5, 0x3e)) return null
            bank = byte()
            if (!expect(0xd7)) return null
        }
        if (bank <= 0 || !expect(0xfa) || word() !in 0xc000..0xdfff || !expect(0x3d, 0x21)) return null
        val address = word()
        if (address !in 0x4000..0x7fff || !expect(0x5f, 0x16, 0)) return null
        var width = 0
        while (p < end && rom.u8(p) == 0x19 && width <= 16) { p++; width++ }
        if (width !in 5..16 || !expect(0x11)) return null
        val destination = word()
        if (destination !in 0xc000..0xdfff || destination + width > 0xdfff || !expect(0xd5, 0x01) ||
            word() != width || !expect(0xcd) || word() !in 1 until end || !expect(0x21) ||
            word() != destination + width || !expect(0x36, 0x50, 0xd1)
        ) return null
        if (generation == 1) {
            if (!expect(0xf1)) return null
            if (helper >= 0) {
                if (!expect(0xcd) || word() != helper) return null
            } else if (!expect(0xe0, bankRegister, 0xea) || word() != bankStore) return null
            if (!expect(0xe1, 0xc9)) return null
        } else if (!expect(0xe1, 0xf1, 0xd7, 0xc9)) return null
        val root = rom.gbBankAddress(bank, address) ?: return null
        return Root(root, width, if (generation != 1) null else if (helper >= 0) EngineFamily.YELLOW else EngineFamily.RED_BLUE)
    }
}
