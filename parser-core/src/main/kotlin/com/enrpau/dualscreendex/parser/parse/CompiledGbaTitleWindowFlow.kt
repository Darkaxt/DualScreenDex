package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage

/** Window argument flow only; renderer and callback targets are not certified callees. */
@ConsistentCopyVisibility
internal data class CompiledGbaTitleWindowFlow private constructor(
    val rendererOffset: Int,
    val frameCallbackOffset: Int,
    val windowCopyOffset: Int,
) {
    companion object {
        fun resolve(rom: RomImage, printer: Int, frame: Int, window: Int,
            cancellation: ParserCancellationToken): CompiledGbaTitleWindowFlow? {
            cancellation.throwIfCancellationRequested()
            if (window !in 0..255) return null
            val packet = CompiledGbaTextPrinter.resolve(rom, printer, cancellation) ?: return null
            val d = Envelope(rom, packet.dispatcherOffset, 0xE8)
            val f = Envelope(rom, frame, 0x48)
            if (!d.valid || !f.valid || !d.matches(DISPATCHER) || !f.matches(FRAME)) return null
            val envelopes = listOf(printer.toLong() until printer.toLong() + 0xA8, d.range, f.range,
                packet.fontLiteralOffset.toLong() until packet.fontLiteralOffset.toLong() + 4)
            if (envelopes.indices.any { i -> (i + 1 until envelopes.size).any { j ->
                overlaps(envelopes[i], envelopes[j])
            } }) return null

            if (d.literal(0x0A, 0, 0x18) != packet.fontRoot) return null
            val temporary = d.literal(0x1C, 0, 0x84) ?: return null
            if (d.literal(0x8C, 1, 0x98) != temporary || d.literal(0xAA, 0, 0xDC) != temporary ||
                d.literal(0xB8, 0, 0xDC) != temporary || d.literal(0x94, 7, 0x9C) != 0x3FFL
            ) return null
            val printers = d.literal(0x6A, 0, 0x88) ?: return null
            if (d.literal(0xC2, 0, 0xE0) != printers) return null
            val flag = d.literal(0xCE, 1, 0xE4) ?: return null
            val tiles = f.literal(0x0C, 0, 0x3C) ?: return null
            val palette = f.literal(0x10, 0, 0x40) ?: return null
            if (temporary and 3L != 0L || printers and 3L != 0L || tiles and 1L != 0L) return null
            // Include preceding slots: the nominated window must not alias another packet or frame state.
            val fields = listOf(packet.fontRoot until packet.fontRoot + 4,
                temporary until temporary + 32, printers until printers + (window + 1) * 32L,
                flag..flag, tiles until tiles + 2, palette..palette)
            if (fields.any { !ram(it) } || fields.indices.any { i ->
                (i + 1 until fields.size).any { j -> overlaps(fields[i], fields[j]) }
            }) return null

            val color = d.call(0x58) ?: return null
            val renderer = d.call(0xAC) ?: return null
            val copy = d.call(0xBE) ?: return null
            val frameDispatch = f.call(0x18) ?: return null
            val fill = f.call(0x20) ?: return null
            val tilemap = f.call(0x26) ?: return null
            if (f.call(0x32) != copy) return null
            val callbackRaw = f.literal(0x14, 1, 0x44) ?: return null
            if (callbackRaw and 1L == 0L) return null
            val callback = (callbackRaw and -2L) - 0x08000000L
            val targets = listOf(color.toLong(), renderer.toLong(), copy.toLong(), frameDispatch.toLong(),
                fill.toLong(), tilemap.toLong(), callback)
            if (targets.distinct().size != targets.size || targets.any { target ->
                target < 0xC0 || target + 2 > rom.size || envelopes.any { target in it }
            }) return null
            cancellation.throwIfCancellationRequested()
            return CompiledGbaTitleWindowFlow(renderer, callback.toInt(), copy)
        }

        private fun overlaps(a: LongRange, b: LongRange): Boolean = a.first <= b.last && b.first <= a.last
        private fun ram(range: LongRange): Boolean =
            (range.first >= 0x02000000L && range.last <= 0x0203FFFFL) ||
                (range.first >= 0x03000000L && range.last <= 0x03007FFFL)

        // Complete instruction runs, including all branches and returns. The omitted words are only
        // checked literal operands, atomic BLs, or padding skipped by a checked unconditional branch.
        private val DISPATCHER = listOf(
            0x00 to intArrayOf(0xB5F0, 0x1C06, 0x4694, 0x0609, 0x0E0D),
            0x0C to intArrayOf(0x6800, 0x2800, 0xD104, 0x2000, 0xE05F),
            0x1E to intArrayOf(0x2200, 0x2101, 0x76C1, 0x7702, 0x7745, 0x7782, 0x77C2,
                0x1C04, 0x2106, 0x301A, 0x7002, 0x3801, 0x3901, 0x2900, 0xDAFA,
                0x1C21, 0x1C30, 0xC88C, 0xC18C, 0x6800, 0x6008, 0x4660, 0x6120,
                0x7B30, 0x0900, 0x7B72, 0x0711, 0x0F09, 0x0912),
            0x5C to intArrayOf(0x2DFF, 0xD015, 0x2D00, 0xD013, 0x7F60, 0x3801, 0x7760),
            0x6C to intArrayOf(0x7931, 0x0149, 0x1809, 0x1C20, 0xC81C, 0xC11C,
                0xC88C, 0xC18C, 0xC890, 0xC190, 0xE025),
            0x8E to intArrayOf(0x2000, 0x7748, 0x2400),
            0x96 to intArrayOf(0xE006),
            0xA0 to intArrayOf(0x1C60, 0x0400, 0x0C04, 0x42BC, 0xD804),
            0xB0 to intArrayOf(0x2801, 0xD1F5, 0x2DFF, 0xD004),
            0xBA to intArrayOf(0x7900, 0x2102),
            0xC4 to intArrayOf(0x7931, 0x0149, 0x1809, 0x2000, 0x76C8),
            0xD0 to intArrayOf(0x2000, 0x7008, 0x2001, 0xBCF0, 0xBC02, 0x4708),
        )

        private val FRAME = listOf(
            0x00 to intArrayOf(0xB530, 0x1C0C, 0x0600, 0x0E05, 0x0624, 0x0E24),
            0x0E to intArrayOf(0x8002), 0x12 to intArrayOf(0x7003), 0x16 to intArrayOf(0x1C28),
            0x1C to intArrayOf(0x1C28, 0x2111), 0x24 to intArrayOf(0x1C28),
            0x2A to intArrayOf(0x2C01, 0xD103, 0x1C28, 0x2103),
            0x36 to intArrayOf(0xBC30, 0xBC01, 0x4700),
        )
    }

    private class Envelope(val rom: RomImage, val entry: Int, size: Int) {
        val range = entry.toLong() until entry.toLong() + size
        val valid = entry >= 0xC0 && entry and 3 == 0 && range.last < rom.size
        fun matches(runs: List<Pair<Int, IntArray>>): Boolean = runs.all { (relative, ops) ->
            ops.indices.all { rom.u16le(entry + relative + it * 2) == ops[it] }
        }
        fun literal(relative: Int, register: Int, pool: Int): Long? {
            val site = entry + relative
            val delta = entry + pool - ((site + 4) and -4)
            if (rom.u16le(site) != 0x4800 or (register shl 8) or (delta / 4)) return null
            return rom.u32le(entry + pool)
        }
        fun call(relative: Int): Int? {
            val site = entry + relative
            val high = rom.u16le(site)
            val low = rom.u16le(site + 2)
            if (high and 0xF800 != 0xF000 || low and 0xF800 != 0xF800) return null
            var delta = ((high and 0x7FF) shl 12) or ((low and 0x7FF) shl 1)
            if (delta and 0x400000 != 0) delta -= 0x800000
            return (site.toLong() + 4 + delta).takeIf { it >= 0xC0 && it + 2 <= rom.size }?.toInt()
        }
    }
}
