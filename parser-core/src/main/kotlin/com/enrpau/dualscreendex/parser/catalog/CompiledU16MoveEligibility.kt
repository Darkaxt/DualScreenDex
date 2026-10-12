package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage

/** Closed scalar predicate and its linked pointer/u16-list ABI; no inferred source method. */
internal class CompiledU16MoveEligibility private constructor(
    val table: Int,
    val rejectedSpecies: Int,
    val overriddenSpecies: Int,
    private val override: Map<Int, Operation>,
) {
    private sealed interface Operation {
        data class Set(val value: Int) : Operation
        data class Shift(val bits: Int) : Operation
        data class Change(val value: Int) : Operation
        data object Compare : Operation
        data class Conditional(val condition: Int, val target: Int) : Operation
        data class Jump(val target: Int) : Operation
    }

    fun sameContract(other: CompiledU16MoveEligibility): Boolean =
        table == other.table && rejectedSpecies == other.rejectedSpecies &&
            overriddenSpecies == other.overriddenSpecies && override == other.override

    fun overrideAllows(move: Int): Boolean {
        var pc = 20
        var constant = rejectedSpecies
        var comparison: Int? = null
        repeat(64) {
            if (pc == 132) return true
            if (pc == 180) return false
            when (val op = override.getValue(pc)) {
                is Operation.Set -> { constant = op.value; comparison = null; pc += 2 }
                is Operation.Shift -> { constant = constant shl op.bits; comparison = null; pc += 2 }
                is Operation.Change -> { constant += op.value; comparison = null; pc += 2 }
                Operation.Compare -> { comparison = (move and 0xFFFF).compareTo(constant); pc += 2 }
                is Operation.Conditional -> {
                    val cmp = checkNotNull(comparison)
                    val taken = when (op.condition) {
                        0 -> cmp == 0
                        1 -> cmp != 0
                        10 -> cmp >= 0
                        11 -> cmp < 0
                        12 -> cmp > 0
                        13 -> cmp <= 0
                        else -> error("unqualified scalar branch")
                    }
                    pc = if (taken) op.target else pc + 2
                }
                is Operation.Jump -> pc = op.target
            }
        }
        error("qualified scalar predicate exceeded its DAG")
    }

    companion object {
        fun resolve(rom: RomImage, entry: Int, cancellation: ParserCancellationToken): CompiledU16MoveEligibility? {
            if (entry < 0 || entry and 1 != 0 || entry > rom.size - 196) return null
            fun word(relative: Int) = rom.u16le(entry + relative)
            fun matches(relative: Int, vararg ops: Int) = ops.indices.all { word(relative + it * 2) == ops[it] }
            fun branch(relative: Int): Int? {
                val op = word(relative)
                val delta = when {
                    op and 0xF800 == 0xE000 -> (op and 2047) shl 21 shr 20
                    op and 0xF000 == 0xD000 && op ushr 8 and 15 < 14 -> (op and 255) shl 24 shr 23
                    else -> return null
                }
                return relative + 4 + delta
            }
            val pools = linkedSetOf<Int>()
            fun literal(relative: Int, register: Int): Long? {
                val op = word(relative)
                if (op and 0xFF00 != 0x4800 or (register shl 8)) return null
                val slot = ((entry + relative + 4) and -4).toLong() + (op and 255) * 4L
                if (slot !in 0..rom.size.toLong() - 4) return null
                pools += slot.toInt() - entry
                return rom.u32le(slot.toInt())
            }
            if (!matches(0, 0xB510, 0x0400, 0x0C03, 0x0409, 0x0C0A) || word(12) != 0x4283 ||
                word(14) and 0xFF00 != 0xD000 || branch(14) != 180 || word(16) and 0xFF00 != 0x2B00 ||
                word(18) and 0xFF00 != 0xD100 || branch(18) != 136 ||
                !matches(132, 0x2001) || branch(134) != 182 || word(134) and 0xF800 != 0xE000 ||
                word(136) != 0x2400 || !matches(140, 0x0099, 0x1809, 0x6809, 0x8808) ||
                word(150) != 0x4298 || word(152) and 0xFF00 != 0xD000 || branch(152) != 180 ||
                !matches(154, 0x0060, 0x1840, 0x8800, 0x4290) ||
                word(162) and 0xFF00 != 0xD000 || branch(162) != 132 ||
                !matches(164, 0x1C60, 0x0600, 0x0E04, 0x0060, 0x1840, 0x8800, 0x4298) ||
                word(178) and 0xFF00 != 0xD100 || branch(178) != 154 ||
                !matches(180, 0x2000, 0xBC10, 0xBC02, 0x4708)) return null
            val rejected = literal(10, 0)?.takeIf { it in 0..65535 }?.toInt() ?: return null
            val rootValue = literal(138, 0) ?: return null
            val root = (rootValue - 0x08000000L).takeIf { it in 0 until rom.size.toLong() && it and 3L == 0L }
                ?.toInt() ?: return null
            if (literal(148, 3) != 65535L) return null
            val special = word(16) and 255
            if (special == rejected) return null

            val program = linkedMapOf<Int, Operation>()
            val queue = ArrayDeque<Pair<Int, Boolean>>()
            queue += 20 to false
            val seen = hashSetOf<Pair<Int, Boolean>>()
            while (queue.isNotEmpty()) {
                cancellation.throwIfCancellationRequested()
                val state = queue.removeLast()
                if (!seen.add(state)) continue
                val (pc, compared) = state
                if (pc == 132 || pc == 180) continue
                if (pc !in 20 until 132 || pc and 1 != 0 || seen.size > 128) return null
                val op = word(pc)
                val operation: Operation
                var nextCompared = compared
                val targets: List<Int>
                when {
                    op and 0xFF00 == 0x2000 -> {
                        operation = Operation.Set(op and 255); nextCompared = false; targets = listOf(pc + 2)
                    }
                    op and 0xFF00 == 0x4800 -> {
                        val value = literal(pc, 0) ?: return null
                        operation = Operation.Set(value.toInt()); nextCompared = false; targets = listOf(pc + 2)
                    }
                    op and 0xF83F == 0 -> {
                        operation = Operation.Shift(op ushr 6 and 31); nextCompared = false; targets = listOf(pc + 2)
                    }
                    op and 0xFF00 in listOf(0x3000, 0x3800) -> {
                        operation = Operation.Change((op and 255) * if (op and 0x800 != 0) -1 else 1)
                        nextCompared = false; targets = listOf(pc + 2)
                    }
                    op == 0x4282 -> { operation = Operation.Compare; nextCompared = true; targets = listOf(pc + 2) }
                    op and 0xF000 == 0xD000 && op ushr 8 and 15 in listOf(0, 1, 10, 11, 12, 13) -> {
                        if (!compared) return null
                        val target = branch(pc) ?: return null
                        operation = Operation.Conditional(op ushr 8 and 15, target); targets = listOf(pc + 2, target)
                    }
                    op and 0xF800 == 0xE000 -> {
                        val target = branch(pc) ?: return null
                        operation = Operation.Jump(target); targets = listOf(target)
                    }
                    else -> return null
                }
                if (targets.any { it <= pc || it !in 20 until 132 && it != 132 && it != 180 }) return null
                program[pc] = operation
                for (target in targets) queue += target to nextCompared
            }
            val code = (0 until 20 step 2).toSet() + program.keys + (132 until 188 step 2).toSet()
            if (pools.any { it in code || it + 2 in code }) return null
            return CompiledU16MoveEligibility(root, rejected, special, program.toMap())
        }
    }
}
