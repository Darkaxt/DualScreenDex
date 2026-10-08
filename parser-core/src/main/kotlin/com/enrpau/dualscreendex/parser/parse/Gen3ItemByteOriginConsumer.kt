package com.enrpau.dualscreendex.parser.parse

/** Complete bounded caller frame and byte-only root-origin disposition; opaque callees grant no effects. */
internal class Gen3ItemByteOriginConsumer(
    private val word: (Int) -> Int,
    private val literalSlot: (Int) -> Int?,
    private val literalRoot: (Int) -> Int?,
    private val call: (Int) -> Int?,
    private val cancellation: () -> Unit
) {
    data class Proof(val stride: Int, val field: Int, val pools: Set<Int>)
    private sealed interface Value {
        data object Unknown : Value
        data object Return : Value
        data object Origin : Value
        data class Saved(val register: Int) : Value
    }
    private data class State(val pc: Int, val registers: List<Value>, val stack: List<Value>)

    fun resolve(site: Int, root: Int): Proof? {
        val words = mutableMapOf<Int, Int>()
        fun read(at: Int): Int {
            cancellation()
            return words.getOrPut(at) { word(at) }
        }
        fun matches(at: Int, vararg ops: Int) = ops.indices.all { read(at + it * 2) == ops[it] }
        fun shift(at: Int, source: Int, destination: Int): Int? {
            val op = read(at)
            return if (op >= 0 && op and 0xF83F == (source shl 3 or destination)) op ushr 6 and 31 else null
        }
        if (read(site) and 0xFF00 != 0x4C00 || literalRoot(site) != root ||
            !matches(site + 2, 0x1C30) || read(site + 4) and 0xFF00 != 0x2100 ||
            read(site + 12) != 0x1809 || read(site + 16) != 0x1909) return null
        val first = shift(site + 10, 0, 1) ?: return null
        val last = shift(site + 14, 1, 1) ?: return null
        val stride = (((1L shl first) + 1) shl last).takeIf { it in 2..256 }?.toInt() ?: return null
        val load = read(site + 18)
        if (load < 0 || load and 0xF83F != 0x780C) return null
        val field = load ushr 6 and 31
        if (field >= stride) return null
        val entry = (20..MAX_BYTES step 2).map { site - it }.firstOrNull {
            it >= 0 && matches(it, 0xB5F0, 0x4657, 0x464E, 0x4645, 0xB4E0) &&
                read(it + 10) and 0xFF80 == 0xB080 && read(it + 10) and 127 in 1..8
        } ?: return null
        val slots = read(entry + 10) and 127
        val restore = (site + 20..site + MAX_BYTES step 2).firstOrNull {
            matches(it, 0xB000 or slots, 0xBC38, 0x4698, 0x46A1, 0x46AA, 0xBCF0, 0xBC02, 0x4708)
        } ?: return null
        val end = restore + 16
        if (end - entry > MAX_BYTES) return null
        val initial = (0..15).map { if (it == 14) Value.Return else Value.Saved(it) }
        val queue = ArrayDeque<State>()
        queue.add(State(entry, initial, emptyList()))
        val seen = hashSetOf<State>()
        val code = hashSetOf<Int>()
        val pools = linkedSetOf<Int>()
        val reads = hashSetOf<Int>()
        val returns = hashSetOf<Int>()
        while (queue.isNotEmpty()) {
            cancellation()
            val state = queue.removeLast()
            if (!seen.add(state)) continue
            val pc = state.pc
            if (seen.size > MAX_STATES || pc !in entry until end || pc and 1 != 0) return null
            val op = read(pc)
            if (op < 0) return null
            code += pc
            val group = op and 0xF800
            val dst = op and 7
            val src = op ushr 3 and 7
            val regs = state.registers.toMutableList()
            val stack = state.stack.toMutableList()
            var next = listOf(pc + 2)
            fun origin(vararg registers: Int) = registers.any { state.registers[it] == Value.Origin }
            when {
                op and 0xFE00 == 0xB400 -> {
                    val saved = (0..7).filter { op and (1 shl it) != 0 }.map { regs[it] }.toMutableList()
                    if (op and 256 != 0) saved += regs[14]
                    if (saved.isEmpty() || Value.Origin in saved) return null
                    stack.addAll(0, saved)
                }
                op and 0xFE00 == 0xBC00 -> {
                    if (op and 256 != 0) return null
                    val selected = (0..7).filter { op and (1 shl it) != 0 }
                    if (selected.isEmpty() || stack.size < selected.size) return null
                    for (r in selected) regs[r] = stack.removeAt(0)
                }
                op and 0xFF00 == 0xB000 -> {
                    val count = op and 127
                    if (count !in 1..16) return null
                    if (op and 128 != 0) stack.addAll(0, List(count) { Value.Unknown })
                    else {
                        if (stack.size < count) return null
                        repeat(count) { stack.removeAt(0) }
                    }
                }
                op and 0xFC00 == 0x4400 -> {
                    val operation = op ushr 8 and 3
                    val source = op ushr 3 and 15
                    val target = dst or (op ushr 4 and 8)
                    if (source in 13..15 || target in 13..15) return null
                    when (operation) {
                        0 -> regs[target] = if (origin(target, source)) Value.Origin else Value.Unknown
                        1 -> if (origin(target, source)) return null
                        2 -> regs[target] = state.registers[source]
                        else -> {
                            if (op and 7 != 0 || state.registers[source] != Value.Return || stack.isNotEmpty() ||
                                (4..11).any { regs[it] != initial[it] } || Value.Origin in regs || pc != end - 2) return null
                            returns += pc
                            continue
                        }
                    }
                }
                group in listOf(0, 0x0800, 0x1000) -> {
                    regs[dst] = when {
                        origin(src) -> Value.Origin
                        group == 0 && op ushr 6 and 31 == 0 -> state.registers[src]
                        else -> Value.Unknown
                    }
                }
                group == 0x1800 -> {
                    val immediate = op and 0x400 != 0
                    val other = op ushr 6 and 7
                    regs[dst] = when {
                        origin(src) || !immediate && origin(other) -> Value.Origin
                        immediate && other == 0 && op and 0x200 == 0 -> state.registers[src]
                        else -> Value.Unknown
                    }
                }
                group == 0x2000 -> regs[op ushr 8 and 7] = Value.Unknown
                group in listOf(0x3000, 0x3800) -> {
                    val target = op ushr 8 and 7
                    if (!origin(target)) regs[target] = Value.Unknown
                }
                group == 0x2800 -> if (origin(op ushr 8 and 7)) return null
                op and 0xFC00 == 0x4000 -> {
                    if (origin(src, dst)) return null
                    if (op ushr 6 and 15 !in listOf(8, 10, 11)) regs[dst] = Value.Unknown
                }
                group == 0x4800 -> {
                    val slot = literalSlot(pc) ?: return null
                    if (slot < entry || slot > end - 4) return null
                    pools += slot
                    val isRoot = literalRoot(pc) == root
                    if (isRoot && pc != site) return null
                    regs[op ushr 8 and 7] = if (isRoot) Value.Origin else Value.Unknown
                }
                op and 0xF000 == 0x9000 -> {
                    val slot = op and 255
                    val target = op ushr 8 and 7
                    if (slot >= stack.size) return null
                    if (op and 0x800 != 0) regs[target] = stack[slot]
                    else {
                        if (origin(target)) return null
                        stack[slot] = state.registers[target]
                    }
                }
                op and 0xE000 == 0x6000 || op and 0xF000 == 0x8000 -> {
                    if (op and 0x800 == 0) {
                        if (origin(src, dst)) return null
                    } else {
                        if (origin(src)) {
                            if (pc != site + 18 || op != load) return null
                            reads += pc
                        }
                        regs[dst] = Value.Unknown
                    }
                }
                group == 0xF000 -> {
                    val target = call(pc) ?: return null
                    if (target in entry until end || listOf(0, 1, 2, 3, 12, 13).any { origin(it) }) return null
                    code += pc + 2
                    for (r in listOf(0, 1, 2, 3, 12, 14)) regs[r] = Value.Unknown
                    next = listOf(pc + 4)
                }
                op and 0xF000 == 0xD000 && op ushr 8 and 15 < 14 -> {
                    val delta = (op and 255) shl 24 shr 23
                    val target = pc + 4 + delta
                    // The independently proved caller is acyclic; a seen-state hit is not alias closure.
                    if (target <= pc || target in site + 2 until site + 20) return null
                    next = listOf(pc + 2, target)
                }
                group == 0xE000 -> {
                    val delta = (op and 2047) shl 21 shr 20
                    val target = pc + 4 + delta
                    // The independently proved caller is acyclic; a seen-state hit is not alias closure.
                    if (target <= pc || target in site + 2 until site + 20) return null
                    next = listOf(target)
                }
                else -> return null
            }
            if (stack.size > 16) return null
            for (target in next) queue.add(State(target, regs.toList(), stack.toList()))
        }
        if (returns != setOf(end - 2) || reads != setOf(site + 18) || site !in code ||
            pools.any { it in code || it + 2 in code }) return null
        return Proof(stride, field, pools)
    }

    private companion object {
        const val MAX_BYTES = 3072
        const val MAX_STATES = 1024
    }
}
