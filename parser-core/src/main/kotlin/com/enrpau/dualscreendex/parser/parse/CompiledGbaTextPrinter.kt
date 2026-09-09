package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage

/** A printer argument ABI, not authority for any title or selected raster. */
internal class CompiledGbaTextPrinter private constructor(val dispatcherOffset: Int) {
    val sourceRegister: Int = 2
    val windowRegister: Int = 0

    companion object {
        fun resolve(rom: RomImage, entry: Int, cancellation: ParserCancellationToken): CompiledGbaTextPrinter? {
            cancellation.throwIfCancellationRequested()
            if (entry < 0xC0 || entry and 1 != 0 || entry.toLong() + FUNCTION_BYTES > rom.size) return null
            fun matches(relative: Int, expected: IntArray): Boolean = expected.indices.all { index ->
                rom.u16le(entry + relative + index * 2) == expected[index]
            }
            if (!matches(0, PACKET_PREFIX) || !matches(0x34, FONT_FIELDS_AND_DISPATCH_ARGUMENTS) ||
                !matches(0x98, RETURN_TAIL)
            ) return null

            // The sole literal is a RAM word containing the font-table pointer, not inline code.
            val loadSite = entry + 0x32
            val load = rom.u16le(loadSite)
            if (load and 0xFF00 != 0x4800) return null
            val pool = ((loadSite.toLong() + 4) and -4L) + (load and 255) * 4L
            if (pool < entry.toLong() + FUNCTION_BYTES || pool + 4 > rom.size) return null
            val root = rom.u32le(pool.toInt())
            if (root and 3L != 0L ||
                (root !in 0x02000000L..0x0203FFFCL && root !in 0x03000000L..0x03007FFCL)
            ) return null

            val call = entry + 0x94
            val high = rom.u16le(call)
            val low = rom.u16le(call + 2)
            if (high and 0xF800 != 0xF000 || low and 0xF800 != 0xF800) return null
            var delta = ((high and 0x7FF) shl 12) or ((low and 0x7FF) shl 1)
            if (delta and 0x400000 != 0) delta -= 0x800000
            val target = call.toLong() + 4 + delta
            if (target < 0xC0 || target + 2 > rom.size ||
                target in entry.toLong() until entry.toLong() + FUNCTION_BYTES ||
                target in pool until pool + 4
            ) return null
            cancellation.throwIfCancellationRequested()
            return CompiledGbaTextPrinter(target.toInt())
        }

        private const val FUNCTION_BYTES = 0xA8

        // Save r4-r8/LR, allocate 16 bytes, then bind source/window/font/x/y/current-x/current-y.
        private val PACKET_PREFIX = intArrayOf(
            0xB5F0, 0x4647, 0xB480, 0xB084, 0x9C0A, 0x9D0B, 0x9F0C,
            0x0609, 0x0E09, 0x061B, 0x0E1B, 0x0624, 0x0E24, 0x062D, 0x0E2D, 0x46A8,
            0x9200, 0x466A, 0x7110, 0x4668, 0x7141, 0x7183, 0x71C4, 0x7203, 0x7244,
        )

        // All intervening instructions are checked: font reads only change packet bytes 10..13.
        // No wildcard can hide a source overwrite, SP adjustment, branch or additional call.
        private val FONT_FIELDS_AND_DISPATCH_ARGUMENTS = intArrayOf(
            0x6800, 0x004B, 0x185B, 0x009B, 0x181B, 0x7998, 0x7290,
            0x4669, 0x79D8, 0x72C8, 0x466D, 0x7A19, 0x0709, 0x260F, 0x0F09, 0x7B2C,
            0x2210, 0x4252, 0x1C10, 0x4020, 0x4308, 0x7328, 0x466C, 0x7A19, 0x0909,
            0x0109, 0x4030, 0x4308, 0x7320, 0x7A58, 0x0700, 0x0F00, 0x1C31, 0x4001,
            0x7B60, 0x4002, 0x430A, 0x7362, 0x4669, 0x7A58, 0x0900, 0x0100, 0x4032,
            0x4302, 0x734A, 0x4668, 0x4641, 0x1C3A,
        )

        private val RETURN_TAIL = intArrayOf(
            0x0400, 0x0C00, 0xB004, 0xBC08, 0x4698, 0xBCF0, 0xBC02, 0x4708,
        )
    }
}
