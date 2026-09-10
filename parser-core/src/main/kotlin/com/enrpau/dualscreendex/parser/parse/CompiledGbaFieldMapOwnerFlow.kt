package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage

/** Complete field-map owner topology only; called functions retain their independent proof boundaries. */
internal class CompiledGbaFieldMapOwnerFlow private constructor(
    val dynamicSectionPrinterOffset: Int,
    val fadeOffset: Int,
) : CompiledGbaFieldMapOwnerAuthority {
    companion object {
        fun resolve(
            rom: RomImage,
            declaration: CompiledGbaFieldMapTitle.Declaration,
            cancellation: ParserCancellationToken,
        ): CompiledGbaFieldMapOwnerFlow? {
            cancellation.throwIfCancellationRequested()
            val windowFlow = declaration.windowFlow as? CompiledGbaTitleWindowFlow ?: return null
            if (declaration.window == 0) return null
            val r = Reader(rom)
            val owner = declaration.owner
            val lateShift = when {
                r.literal(owner + 0x7C, 2, owner + 0xB8) == 0x08000000L + declaration.source -> 0
                r.literal(owner + 0x7C, 5, owner + 0xC4) == 0x08000000L + declaration.source -> 0xC
                else -> return null
            }
            fun late(relative: Int) = owner + relative + lateShift
            val ownerSize = 0x18C + lateShift
            val end = late(0x17A)
            if (owner < 0xC0 || owner and 3 != 0 || !r.range(owner, ownerSize)) return null
            val packet = CompiledGbaTextPrinter.resolve(rom, declaration.printer, cancellation) ?: return null
            val source = declaration.source.toLong()
            val codeRanges = listOf(
                owner.toLong() until owner.toLong() + ownerSize,
                declaration.printer.toLong() until declaration.printer.toLong() + 0xA8,
                packet.dispatcherOffset.toLong() until packet.dispatcherOffset.toLong() + 0xE8,
                declaration.frame.toLong() until declaration.frame.toLong() + 0x48,
            )
            val knownEntries = listOf(owner + ownerSize, windowFlow.rendererOffset,
                windowFlow.frameCallbackOffset, windowFlow.windowCopyOffset)
            if (source < 0xC0 || source >= rom.size || codeRanges.any { source in it } ||
                knownEntries.any { source == it.toLong() }
            ) return null

            if (!r.ops(owner, 0xB530, 0xB083) ||
                !r.ops(owner + 0x06, 0x6808) ||
                !r.ops(owner + 0x0A, 0x1880, 0x8800, 0x1C0C, 0x2806, 0xD900) ||
                r.branch(owner + 0x14) != end ||
                !r.ops(owner + 0x16, 0x0080) ||
                !r.ops(owner + 0x1A, 0x1840, 0x6800, 0x4687) ||
                r.literal(owner + 0x18, 1, owner + 0x28) != 0x08000000L + owner + 0x2C
            ) return null
            val handlerRoot = r.literal(owner + 0x04, 1, owner + 0x20) ?: return null
            val stateOffset = r.literal(owner + 0x08, 2, owner + 0x24) ?: return null
            if (!ramWord(handlerRoot) || stateOffset !in 0..0xFFFF || stateOffset and 1L != 0L) return null
            val arms = intArrayOf(0x48, 0x70, 0xBC, 0xE4, 0xF8, 0x124, 0x14C)
            if (arms.indices.any { index ->
                    val relative = arms[index] + if (index >= 2) lateShift else 0
                    r.word(owner + 0x2C + index * 4) != 0x08000000L + owner + relative
                }) return null

            cancellation.throwIfCancellationRequested()
            if (!r.ops(owner + 0x48, 0x6820, 0x3008, 0x2100) ||
                !r.externalCall(owner + 0x4E, owner, ownerSize) ||
                !r.ops(owner + 0x52, 0x2000, 0x2100) ||
                !r.externalCall(owner + 0x56, owner, ownerSize) ||
                !r.ops(owner + 0x5A, 0x2001, 0x2101) ||
                !r.externalCall(owner + 0x5E, owner, ownerSize) ||
                !r.ops(owner + 0x62, 0x6821) ||
                r.literal(owner + 0x64, 0, owner + 0x6C) != stateOffset ||
                !r.ops(owner + 0x66, 0x1809) ||
                r.branch(owner + 0x68) != late(0x13C)
            ) return null

            val tile = r.immediate(owner + 0x74, 2) ?: return null
            val palette = r.immediate(owner + 0x76, 3) ?: return null
            val schedule: Int
            val dynamic: Int
            if (lateShift == 0) {
                if (r.immediate(owner + 0x70, 0) != declaration.window ||
                    !r.ops(owner + 0x72, 0x2100) ||
                    r.call(owner + 0x78) != declaration.frame ||
                    !r.ops(owner + 0x7E, 0x2002, 0x9000, 0x2400, 0x9401, 0x9402) ||
                    r.immediate(owner + 0x88, 0) != declaration.window ||
                    !r.ops(owner + 0x8A, 0x2101, 0x2300) ||
                    r.call(owner + 0x8E) != declaration.printer ||
                    !r.ops(owner + 0x92, 0x2000) ||
                    !r.externalCall(owner + 0x94, owner, ownerSize) ||
                    !r.ops(owner + 0x98, 0x2000, 0x2100) ||
                    r.immediate(owner + 0x9C, 2) != tile ||
                    r.immediate(owner + 0x9E, 3) != palette ||
                    r.call(owner + 0xA0) != declaration.frame ||
                    !r.externalCall(owner + 0xA4, owner, ownerSize) ||
                    !r.ops(owner + 0xA8, 0x2001, 0x4240, 0x9400, 0x2100, 0x2210, 0x2300) ||
                    r.branch(owner + 0xB4) != late(0x130)
                ) return null
                schedule = r.call(owner + 0x94) ?: return null
                dynamic = r.call(owner + 0xA4) ?: return null
            } else {
                if (r.immediate(owner + 0x70, 0) != declaration.window ||
                    !r.ops(owner + 0x72, 0x2100) ||
                    r.call(owner + 0x78) != declaration.frame ||
                    !r.ops(owner + 0x7E, 0x2001, 0x1C29) ||
                    r.immediate(owner + 0x82, 2) != 0x38 ||
                    !r.externalCall(owner + 0x84, owner, ownerSize) ||
                    !r.ops(owner + 0x88, 0x1C03, 0x061B, 0x0E1B, 0x2001, 0x9000, 0x2400,
                        0x9401, 0x9402, 0x2101, 0x1C2A) ||
                    r.call(owner + 0x9C) != declaration.printer ||
                    !r.ops(owner + 0xA0, 0x2000) ||
                    !r.externalCall(owner + 0xA2, owner, ownerSize) ||
                    !r.ops(owner + 0xA6, 0x2000, 0x2100) ||
                    r.immediate(owner + 0xAA, 2) != tile ||
                    r.immediate(owner + 0xAC, 3) != palette ||
                    r.call(owner + 0xAE) != declaration.frame ||
                    !r.externalCall(owner + 0xB2, owner, ownerSize) ||
                    !r.ops(owner + 0xB6, 0x2001, 0x4240, 0x9400, 0x2100, 0x2210, 0x2300) ||
                    r.branch(owner + 0xC2) != late(0x130)
                ) return null
                schedule = r.call(owner + 0xA2) ?: return null
                dynamic = r.call(owner + 0xB2) ?: return null
            }
            if (schedule == dynamic || dynamic != owner + ownerSize ||
                dynamic == declaration.frame || dynamic == declaration.printer
            ) return null

            cancellation.throwIfCancellationRequested()
            if (!r.ops(late(0xBC), 0x2182, 0x0149, 0x2000) ||
                !r.externalCall(late(0xC2), owner, ownerSize) ||
                !r.ops(late(0xC6), 0x2000) ||
                !r.externalCall(late(0xC8), owner, ownerSize) ||
                !r.ops(late(0xCC), 0x2002) ||
                r.call(late(0xCE)) != r.call(late(0xC8)) ||
                r.literal(late(0xD2), 0, late(0xDC)) != handlerRoot ||
                !r.ops(late(0xD4), 0x6801) ||
                r.literal(late(0xD6), 0, late(0xE0)) != stateOffset ||
                !r.ops(late(0xD8), 0x1809) ||
                r.branch(late(0xDA)) != late(0x13C)
            ) return null

            val fadeRoot = r.literal(late(0xE4), 0, late(0xF4)) ?: return null
            if (!ramWord(fadeRoot) || fadeRoot == handlerRoot ||
                !r.ops(late(0xE6), 0x79C1, 0x2080, 0x4008, 0x2800) ||
                r.conditionalBranch(late(0xEE), 1) != end ||
                !r.ops(late(0xF0), 0x6821) ||
                r.branch(late(0xF2)) != late(0x138)
            ) return null

            if (!r.externalCall(late(0xF8), owner, ownerSize) ||
                !r.ops(late(0xFC), 0x0600, 0x0E00, 0x2803) ||
                r.conditionalBranch(late(0x102), 1) != late(0x10A) ||
                r.call(late(0x104)) != dynamic ||
                r.branch(late(0x108)) != end ||
                !r.ops(late(0x10A), 0x2803) ||
                r.conditionalBranch(late(0x10C), 0xB) != end ||
                !r.ops(late(0x10E), 0x2805) ||
                r.conditionalBranch(late(0x110), 0xC) != end ||
                r.literal(late(0x112), 0, late(0x11C)) != handlerRoot ||
                !r.ops(late(0x114), 0x6801) ||
                r.literal(late(0x116), 0, late(0x120)) != stateOffset ||
                !r.ops(late(0x118), 0x1809) ||
                r.branch(late(0x11A)) != late(0x13C)
            ) return null

            cancellation.throwIfCancellationRequested()
            if (!r.ops(late(0x124), 0x2001, 0x4240, 0x2100, 0x9100, 0x2200, 0x2310) ||
                !r.externalCall(late(0x130), owner, ownerSize) ||
                r.literal(late(0x134), 0, late(0x144)) != handlerRoot ||
                !r.ops(late(0x136), 0x6801) ||
                r.literal(late(0x138), 2, late(0x148)) != stateOffset ||
                !r.ops(late(0x13A), 0x1889) ||
                !r.ops(late(0x13C), 0x8808, 0x3001, 0x8008) ||
                r.branch(late(0x142)) != end
            ) return null
            val fade = r.call(late(0x130)) ?: return null

            if (r.literal(late(0x14C), 0, late(0x184)) != fadeRoot ||
                !r.ops(late(0x14E), 0x79C1, 0x2080, 0x4008, 0x0600, 0x0E05, 0x2D00) ||
                r.conditionalBranch(late(0x15A), 1) != end ||
                !r.externalCall(late(0x15C), owner, ownerSize) ||
                r.literal(late(0x160), 4, late(0x188)) != handlerRoot ||
                !r.ops(late(0x162), 0x6820, 0x6800) ||
                !r.externalCall(late(0x166), owner, ownerSize) ||
                !r.ops(late(0x16A), 0x6820, 0x2800) ||
                r.conditionalBranch(late(0x16E), 0) != late(0x176) ||
                !r.externalCall(late(0x170), owner, ownerSize) ||
                !r.ops(late(0x174), 0x6025) ||
                !r.externalCall(late(0x176), owner, ownerSize) ||
                !r.ops(end, 0xB003, 0xBC30, 0xBC01, 0x4700)
            ) return null
            cancellation.throwIfCancellationRequested()
            return CompiledGbaFieldMapOwnerFlow(dynamic, fade)
        }

        private fun ramWord(raw: Long): Boolean = raw and 3L == 0L &&
            (raw in 0x02000000L..0x0203FFFCL || raw in 0x03000000L..0x03007FFCL)
    }

    private class Reader(private val rom: RomImage) {
        fun range(at: Int, size: Int): Boolean = at >= 0 && at.toLong() + size <= rom.size
        fun ops(at: Int, vararg values: Int): Boolean = range(at, values.size * 2) &&
            values.indices.all { rom.u16le(at + it * 2) == values[it] }
        fun word(at: Int): Long? = if (range(at, 4)) rom.u32le(at) else null
        fun immediate(at: Int, register: Int): Int? = if (range(at, 2))
            rom.u16le(at).takeIf { it and 0xFF00 == 0x2000 or (register shl 8) }?.and(0xFF)
        else null
        fun literal(at: Int, register: Int, pool: Int): Long? {
            if (!range(at, 2) || !range(pool, 4)) return null
            val delta = pool - ((at + 4) and -4)
            if (delta !in 0..1020 || delta and 3 != 0 ||
                rom.u16le(at) != 0x4800 or (register shl 8) or (delta / 4)
            ) return null
            return rom.u32le(pool)
        }
        fun call(at: Int): Int? {
            if (!range(at, 4)) return null
            val high = rom.u16le(at)
            val low = rom.u16le(at + 2)
            if (high and 0xF800 != 0xF000 || low and 0xF800 != 0xF800) return null
            var delta = ((high and 0x7FF) shl 12) or ((low and 0x7FF) shl 1)
            if (delta and 0x400000 != 0) delta -= 0x800000
            return (at.toLong() + 4 + delta).takeIf { it >= 0xC0 && it + 2 <= rom.size }?.toInt()
        }
        fun externalCall(at: Int, owner: Int, ownerSize: Int): Boolean =
            call(at)?.let { it !in owner until owner + ownerSize } == true
        fun branch(at: Int): Int? {
            if (!range(at, 2)) return null
            val op = rom.u16le(at)
            if (op and 0xF800 != 0xE000) return null
            var delta = (op and 0x7FF) shl 1
            if (delta and 0x800 != 0) delta -= 0x1000
            return (at.toLong() + 4 + delta).takeIf { it >= 0xC0 && it + 2 <= rom.size }?.toInt()
        }
        fun conditionalBranch(at: Int, condition: Int): Int? {
            if (!range(at, 2)) return null
            val op = rom.u16le(at)
            if (condition !in 0..13 || op and 0xFF00 != 0xD000 or (condition shl 8)) return null
            var delta = (op and 0xFF) shl 1
            if (delta and 0x100 != 0) delta -= 0x200
            return (at.toLong() + 4 + delta).takeIf { it >= 0xC0 && it + 2 <= rom.size }?.toInt()
        }
    }
}
