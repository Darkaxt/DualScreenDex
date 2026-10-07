package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage

/** Local packed-numeric / move-minus-one pointer branch and source-defined window text ABI. */
internal object CompiledMoveDescriptionPointerBinding {
    fun root(rom: RomImage, site: Int, numericRoot: Int, work: () -> Unit): Int? {
        if (site !in 84..rom.size - 50) return null
        fun words(start: Int, vararg expected: Int): Boolean =
            start >= 0 && start.toLong() + expected.size * 2 <= rom.size && expected.indices.all {
                work()
                rom.u16le(start + it * 2) == expected[it]
            }
        fun literalSlot(at: Int, register: Int): Int? {
            if (at !in 0..rom.size - 2) return null
            work()
            val op = rom.u16le(at)
            if (op and 0xff00 != (0x4800 or (register shl 8))) return null
            val slot = ((at + 4) and -4) + (op and 255) * 4
            return slot.takeIf { it in 0..rom.size - 4 }
        }
        fun literal(at: Int, register: Int): Int? = literalSlot(at, register)?.let(rom::gbaPointer)
        fun call(at: Int): Int? {
            if (at !in 0..rom.size - 4) return null
            work()
            val high = rom.u16le(at)
            val low = rom.u16le(at + 2)
            if (high and 0xf800 != 0xf000 || low and 0xf800 != 0xf800) return null
            var displacement = ((high and 0x7ff) shl 12) or ((low and 0x7ff) shl 1)
            if (displacement and 0x400000 != 0) displacement -= 0x800000
            return (at + 4 + displacement).takeIf { it in 0..rom.size - 2 }
        }
        // u16 move parameter; retain r5 across the local ABI calls and ordinary battle-page path.
        if (!words(site - 84, 0xb570, 0xb082, 0x0400, 0x0c05, 0x1c2c) ||
            literal(site - 74, 0) == null || !words(site - 72, 0x2102) || call(site - 70) == null ||
            !words(site - 66, 0x0600, 0x0e06, 0x1c30, 0x2100) || call(site - 58) == null ||
            !words(site - 54, 0x2d00, 0xd04c) || literalSlot(site - 50, 0) == null ||
            !words(site - 48, 0x6800) || literalSlot(site - 46, 1) == null ||
            !words(site - 44, 0x1840, 0x7800, 0x2802, 0xd12d)
        ) return null
        // The paired byte field uses exactly (move * 4 + move) * 4; no unchecked index gap.
        if (literal(site - 36, 1) != numericRoot ||
            !words(site - 34, 0x00a8, 0x1940, 0x0080, 0x1840, 0x7804, 0x1c28) ||
            call(site - 22) == null || !words(site - 18, 0x0600, 0x0e00) ||
            call(site - 14) == null || !words(site - 10, 0x1c28) || call(site - 8) == null
        ) return null
        // The special-effect exit skips this ordinary pointer branch. No call or clobber occurs
        // from the selected root through the r1 text argument; the branch skips its literal pool.
        work()
        if (rom.u16le(site - 4) and 0xff00 != 0x2c00 ||
            !words(site - 2, 0xd00f) ||
            !words(site + 2, 0x1e68, 0x0080, 0x1840, 0x6801, 0xe00a) ||
            !words(site + 34, 0x2000, 0x9000, 0x9001, 0x1c30, 0x2206, 0x2301)
        ) return null
        val root = literal(site, 1)?.takeIf { it % 4 == 0 } ?: return null
        val wrapper = call(site + 46) ?: return null
        // PrintTextOnWindow preserves r1 until storing it as the last text-printer argument.
        // This finite role ABI is shared with the native direct-record route, not a global CFG.
        if (!words(wrapper, 0xb570, 0xb085, 0x9c09, 0x9d0a, 0x0600, 0x0e00,
                0x0612, 0x0e12, 0x061b, 0x0e1b, 0x0624, 0x0e24, 0x062d, 0x0e2d,
                0x2600, 0x9600, 0x9401, 0x006c, 0x1964) ||
            literal(wrapper + 0x26, 5) == null ||
            !words(wrapper + 0x28, 0x1964, 0x9402, 0x9603, 0x9104, 0x2101) ||
            call(wrapper + 0x32) == null ||
            !words(wrapper + 0x36, 0xb005, 0xbc70, 0xbc01, 0x4700)
        ) return null
        return root
    }
}
