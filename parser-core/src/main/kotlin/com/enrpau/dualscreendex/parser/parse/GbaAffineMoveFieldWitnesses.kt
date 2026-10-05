package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession

internal data class GbaMoveFieldWitness(val root: Int, val stride: Int, val field: Int, val width: Int, val signed: Boolean)

/** Bounded straight-line field witnesses only; this is not a whole-function or global CFG verifier. */
internal object GbaAffineMoveFieldWitnesses {
    fun collect(session: RomAnalysisSession, codeEnd: Int, consumeWork: () -> Unit): Set<GbaMoveFieldWitness> {
        val rom = session.rom
        val witnesses = linkedSetOf<GbaMoveFieldWitness>()
        val roots = linkedSetOf<Int>()
        for (start in 0 until codeEnd - 2 step 2) {
            if (start % 4096 == 0) session.cancellation.throwIfCancellationRequested()
            if (rom.u16le(start) and 0xf800 != 0x4800) continue
            val firstRoot = literalRoot(session, start) ?: continue
            consumeWork()
            roots += firstRoot
            if (roots.size > session.limits.maxProbeRootsPerDataset) throw WideMoveBudgetStop()
            val registers = Array<Expression?>(16) { Expression(source = it, scale = 1) }
            var at = start
            while (at + 2 <= codeEnd && at < start + 48) {
                session.cancellation.throwIfCancellationRequested()
                consumeWork()
                val word = rom.u16le(at)
                val destination = word and 7
                when {
                    word and 0xf800 == 0x4800 -> {
                        val register = (word ushr 8) and 7
                        registers[register] = literalRoot(session, at)?.let { Expression(root = it) }
                    }
                    word and 0xf800 == 0x0000 -> {
                        val source = (word ushr 3) and 7
                        val shift = (word ushr 6) and 31
                        registers[destination] = registers[source]?.shift(shift)
                    }
                    word and 0xf800 == 0x1800 -> {
                        val left = registers[(word ushr 3) and 7]
                        val right = if (word and 0x400 != 0) Expression(constant = ((word ushr 6) and 7).toLong())
                            else registers[(word ushr 6) and 7]
                        registers[destination] = combine(left, right, word and 0x200 != 0)
                    }
                    word and 0xff00 == 0x4600 -> {
                        registers[destination or ((word ushr 4) and 8)] = registers[(word ushr 3) and 15]
                    }
                    word and 0xf800 == 0x2000 -> registers[(word ushr 8) and 7] = Expression(constant = (word and 255).toLong())
                    word and 0xf800 == 0x3000 || word and 0xf800 == 0x3800 -> {
                        val register = (word ushr 8) and 7
                        registers[register] = combine(registers[register], Expression(constant = (word and 255).toLong()),
                            word and 0xf800 == 0x3800)
                    }
                    word and 0xf800 in listOf(0x6800, 0x7800, 0x8800) -> {
                        val width = when (word and 0xf800) { 0x6800 -> 4; 0x8800 -> 2; else -> 1 }
                        val address = combine(registers[(word ushr 3) and 7],
                            Expression(constant = (((word ushr 6) and 31) * width).toLong()), false)
                        witness(address, width, false)?.let { witnesses += it }
                        if (address?.root != null) break
                        registers[destination] = Expression(source = destination, scale = 1)
                    }
                    word and 0xfe00 in listOf(0x5800, 0x5a00, 0x5c00, 0x5600, 0x5e00) -> {
                        val kind = word and 0xfe00
                        val width = when (kind) { 0x5800 -> 4; 0x5a00, 0x5e00 -> 2; else -> 1 }
                        val address = combine(registers[(word ushr 3) and 7], registers[(word ushr 6) and 7], false)
                        witness(address, width, kind == 0x5600 || kind == 0x5e00)?.let { witnesses += it }
                        if (address?.root != null) break
                        registers[destination] = Expression(source = destination, scale = 1)
                    }
                    word and 0xf800 == 0x9800 -> registers[(word ushr 8) and 7] =
                        Expression(source = (word ushr 8) and 7, scale = 1)
                    else -> break
                }
                if (witnesses.size > session.limits.maxCandidatesPerDataset) throw WideMoveBudgetStop()
                at += 2
            }
        }
        if (witnesses.size > session.limits.maxCandidatesPerDataset) throw WideMoveBudgetStop()
        return witnesses
    }

    private fun literalRoot(session: RomAnalysisSession, at: Int): Int? {
        val word = session.rom.u16le(at)
        val slot = ((at + 4) and -4) + (word and 255) * 4
        if (slot.toLong() + 4 > session.rom.size) return null
        val value = session.rom.u32le(slot)
        return value.takeIf { it in 0x08000000L..0x09ffffffL }?.minus(0x08000000)?.toInt()
            ?.takeIf { it in 0 until session.rom.size }
    }

    private fun witness(expression: Expression?, width: Int, signed: Boolean): GbaMoveFieldWitness? {
        val value = expression ?: return null
        val root = value.root ?: return null
        if (value.source == null || value.scale !in listOf(20L, 56L) || value.constant !in 0..17) return null
        return GbaMoveFieldWitness(root, value.scale.toInt(), value.constant.toInt(), width, signed)
    }

    private data class Expression(val root: Int? = null, val source: Int? = null, val scale: Long = 0, val constant: Long = 0) {
        fun shift(bits: Int): Expression? {
            if (root != null || bits > 16) return null
            return copy(scale = scale shl bits, constant = constant shl bits)
        }
    }

    private fun combine(left: Expression?, right: Expression?, subtract: Boolean): Expression? {
        left ?: return null
        right ?: return null
        if (left.root != null && right.root != null || subtract && right.root != null ||
            left.source != null && right.source != null && left.source != right.source
        ) return null
        val sign = if (subtract) -1 else 1
        val scale = left.scale + sign * right.scale
        val constant = left.constant + sign * right.constant
        if (scale !in -65536L..65536L || constant !in -65536L..65536L) return null
        return Expression(left.root ?: right.root, left.source ?: right.source, scale, constant)
    }
}

internal class WideMoveBudgetStop : RuntimeException(null, null, false, false)
