package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage

/** Compiled caller envelopes. These nominate declarations; they do not authorize catalog text. */
internal object CompiledGbaFieldMapTitle {
    data class Declaration(
        val owner: Int,
        val loader: Int,
        val source: Int,
        val window: Int,
        val printer: Int,
        val frame: Int,
        val windowFlow: CompiledGbaTitleWindowFlow? = null,
    )

    sealed interface Result {
        data class Complete(val declarations: List<Declaration>) : Result
        data class Incomplete(val reason: String) : Result
    }

    fun nominate(
        rom: RomImage,
        selectedLoader: Int,
        cancellation: ParserCancellationToken,
        maximumOwners: Int = 32,
    ): Result {
        cancellation.throwIfCancellationRequested()
        if (selectedLoader < 0xC0 || selectedLoader and 1 != 0 ||
            selectedLoader.toLong() + 2 > rom.size || maximumOwners !in 1..32
        ) return Result.Incomplete("invalid selected loader or owner budget")
        val reader = Reader(rom)
        val declarations = mutableListOf<Declaration>()
        var observed = 0
        var owner = 0xC0
        while (owner.toLong() + 32 <= rom.size) {
            if (owner % 4096 == 0) cancellation.throwIfCancellationRequested()
            if (reader.ops(owner, 0xB530, 0xB083)) {
                // Nominate by the dispatch table and selected call edge before checking title bytes.
                // A damaged connected title remains a contender; decoding cannot manufacture uniqueness.
                val table = reader.literal(owner + 0x18, 1)?.let(reader::romOffset)
                val initial = table?.let(reader::pointer)
                val wrapper = initial?.let { reader.call(it + 6) }
                if (wrapper != null && reader.call(wrapper + 12) == selectedLoader) {
                    observed++
                    if (observed > maximumOwners) return Result.Incomplete("field-map owner budget exceeded")
                    val declaration = declaration(reader, owner, selectedLoader, cancellation)
                        ?: return Result.Incomplete("connected field-map declaration is unproved")
                    declarations += declaration
                }
            }
            owner += 2
        }
        cancellation.throwIfCancellationRequested()
        return Result.Complete(declarations.toList())
    }

    private fun declaration(r: Reader, owner: Int, loader: Int, cancellation: ParserCancellationToken): Declaration? {
        if (!r.ops(owner, 0xB530, 0xB083) || !r.ops(owner + 6, 0x6808) ||
            !r.ops(owner + 0xA, 0x1880, 0x8800, 0x1C0C, 0x2806, 0xD900) ||
            !r.ops(owner + 0x16, 0x0080) || !r.ops(owner + 0x1A, 0x1840, 0x6800, 0x4687)
        ) return null
        val root = r.literal(owner + 4, 1) ?: return null
        if (!r.ramWord(root)) return null
        val stateOffset = r.literal(owner + 8, 2) ?: return null
        if (stateOffset !in 0..0xFFFF || stateOffset and 1L != 0L) return null
        val table = r.literal(owner + 0x18, 1)?.let(r::romOffset) ?: return null
        if (table and 3 != 0 || table < owner + 32 || !r.range(table, 28)) return null
        val arms = (0..6).map { r.pointer(table + it * 4) ?: return null }
        if (arms.distinct().size != 7 || arms.any { it < table + 28 || it.toLong() > owner.toLong() + 512 }) return null
        val end = r.branch(owner + 0x14) ?: return null
        if (end <= arms.max() || end.toLong() > owner.toLong() + 512 ||
            !r.ops(end, 0xB003, 0xBC30, 0xBC01, 0x4700)
        ) return null
        val initial = arms[0]
        if (!r.ops(initial, 0x6820, 0x3008, 0x2100)) return null
        val wrapper = r.call(initial + 6) ?: return null
        if (!r.ops(wrapper, 0xB500, 0x060A, 0x0E12, 0x2100) ||
            r.call(wrapper + 8) == null || r.call(wrapper + 12) != loader ||
            !r.ops(wrapper + 16, 0x0600, 0x2800, 0xD1FA, 0xBC01, 0x4700)
        ) return null
        if (!r.ops(initial + 10, 0x2000, 0x2100) || r.call(initial + 14) == null ||
            !r.ops(initial + 18, 0x2001, 0x2101) || r.call(initial + 22) == null ||
            !r.ops(initial + 26, 0x6821) || r.literal(initial + 28, 0) != stateOffset ||
            !r.ops(initial + 30, 0x1809)
        ) return null
        val increment = r.branch(initial + 32) ?: return null
        if (increment <= arms[1] || increment >= end || !r.ops(increment, 0x8808, 0x3001, 0x8008) ||
            r.branch(increment + 6) != end
        ) return null

        val title = arms[1]
        if (title < initial + 34 || title + 34 > arms[2]) return null
        val window = r.immediate(title, 0) ?: return null
        if (window == 0 || !r.ops(title + 2, 0x2100) || r.immediate(title + 4, 2) == null ||
            r.immediate(title + 6, 3) == null
        ) return null
        val frame = r.call(title + 8) ?: return null
        val source = r.literal(title + 12, 2)?.let(r::romOffset) ?: return null
        if (!r.ops(title + 14, 0x2002, 0x9000, 0x2400, 0x9401, 0x9402) ||
            r.immediate(title + 24, 0) != window || !r.ops(title + 26, 0x2101, 0x2300)
        ) return null
        val printer = r.call(title + 30) ?: return null
        CompiledGbaTextPrinter.resolve(r.rom, printer, cancellation) ?: return null
        // Missing consumer flow must not remove a contender or turn a nomination into title authority.
        val flow = CompiledGbaTitleWindowFlow.resolve(r.rom, printer, frame, window, cancellation)
        return Declaration(owner, loader, source, window, printer, frame, flow)
    }

    private class Reader(val rom: RomImage) {
        fun range(at: Int, size: Int): Boolean = at >= 0 && at.toLong() + size <= rom.size
        fun ops(at: Int, vararg values: Int): Boolean = range(at, values.size * 2) &&
            values.indices.all { rom.u16le(at + it * 2) == values[it] }
        fun romOffset(raw: Long): Int? = (raw - 0x08000000L).takeIf { it in 0 until rom.size.toLong() }?.toInt()
        fun pointer(at: Int): Int? = if (range(at, 4)) romOffset(rom.u32le(at))?.takeIf { it and 1 == 0 } else null
        fun ramWord(raw: Long): Boolean = raw and 3L == 0L &&
            (raw in 0x02000000L..0x0203FFFCL || raw in 0x03000000L..0x03007FFCL)
        fun literal(site: Int, register: Int): Long? {
            if (!range(site, 2)) return null
            val op = rom.u16le(site)
            if (op and 0xFF00 != 0x4800 or (register shl 8)) return null
            val pool = ((site.toLong() + 4) and -4L) + (op and 255) * 4L
            if (pool + 4 > rom.size) return null
            return rom.u32le(pool.toInt())
        }
        fun immediate(site: Int, register: Int): Int? {
            if (!range(site, 2)) return null
            return rom.u16le(site).takeIf { it and 0xFF00 == 0x2000 or (register shl 8) }?.and(255)
        }
        fun call(site: Int): Int? {
            if (!range(site, 4)) return null
            val high = rom.u16le(site)
            val low = rom.u16le(site + 2)
            if (high and 0xF800 != 0xF000 || low and 0xF800 != 0xF800) return null
            var delta = ((high and 0x7FF) shl 12) or ((low and 0x7FF) shl 1)
            if (delta and 0x400000 != 0) delta -= 0x800000
            return (site.toLong() + 4 + delta).takeIf { it >= 0xC0 && it + 2 <= rom.size }?.toInt()
        }
        fun branch(site: Int): Int? {
            if (!range(site, 2)) return null
            val op = rom.u16le(site)
            if (op and 0xF800 != 0xE000) return null
            var delta = (op and 0x7FF) shl 1
            if (delta and 0x800 != 0) delta -= 0x1000
            return (site.toLong() + 4 + delta).takeIf { it >= 0xC0 && it + 2 <= rom.size }?.toInt()
        }
    }
}
