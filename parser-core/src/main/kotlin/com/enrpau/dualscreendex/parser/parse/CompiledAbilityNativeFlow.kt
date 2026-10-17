package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.arm7.*
import com.enrpau.dualscreendex.parser.analysis.thumb.ThumbDecoder
import com.enrpau.dualscreendex.parser.io.RomImage
import java.util.ArrayDeque

/** Static expressions for the cached-ability proof, never an ARM execution environment. */
internal class CompiledAbilityNativeFlow(
    private val rom: RomImage,
    private val budget: CompiledAbilityProbeBudget,
) {
    sealed interface Atom {
        data class Input(val register: Int, val bits: Int = 32) : Atom
        data class Read(val bytes: Int, val address: Expression) : Atom
        data class Call(val entry: Int, val arguments: List<Expression>) : Atom
        data class Narrow(val value: Expression, val bits: Int) : Atom
        data class ShiftLeft(val value: Expression, val amount: Int) : Atom
        data object Stack : Atom
        data object Return : Atom
    }

    data class Expression(val terms: Map<Atom, Long> = emptyMap(), val constant: Long = 0, val known: Boolean = true) {
        val number: Long? get() = constant.takeIf { known && terms.isEmpty() }
        val atom: Atom? get() = terms.entries.singleOrNull()?.takeIf { known && constant == 0L && it.value == 1L }?.key
        operator fun plus(other: Expression): Expression {
            if (!known || !other.known) return UNKNOWN
            val result = terms.toMutableMap()
            other.terms.forEach { (atom, coefficient) -> result[atom] = result.getOrDefault(atom, 0L) + coefficient }
            return Expression(result.filterValues { it != 0L }, constant + other.constant).bounded()
        }
        operator fun minus(other: Expression): Expression = this + other.scaled(-1)
        fun scaled(scale: Long): Expression {
            if (!known || scale !in -0xFFFFFFFFL..0xFFFFFFFFL) return UNKNOWN
            return try {
                if (scale == 0L) constant(0) else Expression(
                    terms.mapValues { Math.multiplyExact(it.value, scale) }, Math.multiplyExact(constant, scale),
                ).bounded()
            } catch (_: ArithmeticException) { UNKNOWN }
        }
        private fun bounded(): Expression = takeIf {
            terms.size <= 8 && constant in -0xFFFFFFFFL..0xFFFFFFFFL && terms.values.all { it in -0xFFFFFFFFL..0xFFFFFFFFL }
        } ?: UNKNOWN
        companion object {
            val UNKNOWN = Expression(known = false)
            fun constant(value: Long) = Expression(constant = value)
            fun value(atom: Atom): Expression {
                // Opaque calls and indirect reads can otherwise nest on an untrusted
                // back edge before the instruction-work budget is reached.
                val pending = ArrayDeque<Pair<Atom, Int>>()
                pending += atom to 0
                var nodes = 0
                while (pending.isNotEmpty()) {
                    val (current, depth) = pending.removeFirst()
                    if (++nodes > 64 || depth > 12) return UNKNOWN
                    val children = when (current) {
                        is Atom.Read -> listOf(current.address)
                        is Atom.Call -> current.arguments
                        is Atom.Narrow -> listOf(current.value)
                        is Atom.ShiftLeft -> listOf(current.value)
                        else -> emptyList()
                    }
                    for (expression in children) for (child in expression.terms.keys) pending += child to depth + 1
                }
                return Expression(mapOf(atom to 1L))
            }
        }
    }

    data class Cell(val address: Expression, val bytes: Int)
    data class Comparison(val left: Expression, val right: Expression)
    data class State(
        val pc: Int,
        val registers: List<Expression>,
        val memory: Map<Cell, Expression> = emptyMap(),
        val bounds: Map<Atom, LongRange> = emptyMap(),
        val comparison: Comparison? = null,
    )
    data class Invocation(val site: Int, val entry: Int, val arguments: List<Expression>, val state: State)
    data class Store(val site: Int, val cell: Cell, val value: Expression, val state: State)
    data class Returned(val value: Expression, val state: State)
    data class Outcome(
        val returns: List<Returned>, val calls: List<Invocation>, val stores: List<Store>,
        val exits: List<State>, val complete: Boolean, val excludedCalls: List<Invocation> = emptyList(),
        val visitedSites: Set<Int> = emptySet(),
    )

    fun initial(entry: Int, arguments: List<Expression> = emptyList()): State = State(entry, List(16) { register ->
        when {
            register < arguments.size -> arguments[register]
            register == 13 -> value(Atom.Stack)
            register == 14 -> value(Atom.Return)
            else -> value(Atom.Input(register))
        }
    })

    fun analyze(
        initial: State,
        span: IntRange,
        stopAtCalls: Boolean = false,
        summarize: ((Invocation) -> Expression?)? = null,
    ): Outcome {
        val queue = ArrayDeque<State>(); queue += initial
        val seen = mutableSetOf<State>()
        val edges = mutableMapOf<State, List<State>>()
        val returned = mutableListOf<Returned>()
        val calls = mutableListOf<Invocation>()
        val stores = mutableListOf<Store>()
        val exits = mutableListOf<State>()
        val excludedCalls = mutableListOf<Invocation>()
        var complete = true
        while (queue.isNotEmpty()) {
            budget.step()
            val state = queue.removeFirst()
            if (state.pc !in span) { exits += state; continue }
            if (!seen.add(state)) continue
            val decoded = ThumbDecoder.decode(rom, state.pc) as? Arm7DecodeResult.Decoded
            if (decoded == null) { complete = false; edges[state] = emptyList(); continue }
            val instruction = decoded.instruction
            val registers = state.registers.toMutableList()
            var memory = state.memory
            var comparison = state.comparison
            var next = state.pc + instruction.size
            var successors: List<State>? = null
            var terminal = false
            fun read(address: Expression, bytes: Int): Expression = read(state.copy(memory = memory), address, bytes)
            fun store(address: Expression, bytes: Int, expression: Expression) {
                if (!address.known) { complete = false; return }
                val cell = Cell(address, bytes)
                if (cell !in memory && memory.size >= 128) { complete = false; terminal = true; return }
                val stored = narrow(expression, bytes * 8)
                // A differently sized alias is not independently known after this store.
                memory = memory.filterKeys { !mayOverlap(it, cell) } + (cell to stored)
                stores += Store(state.pc, cell, stored, state)
            }
            fun destination(register: Arm7Register, expression: Expression) {
                if (register == Arm7Register.PC) {
                    when {
                        expression == value(Atom.Return) -> {
                            if (registers[13] != initial.registers[13] || (4..11).any { registers[it] != initial.registers[it] }) complete = false
                            else returned += Returned(registers[0], state.copy(registers = registers.toList(), memory = memory))
                            terminal = true
                        }
                        expression.number?.let { it in 0x08000000L until 0x08000000L + rom.size } == true ->
                            next = (requireNotNull(expression.number) - 0x08000000L).toInt() and -2
                        else -> { complete = false; terminal = true }
                    }
                } else registers[register.index] = expression
            }
            when (instruction) {
                is Arm7DataProcessing -> {
                    val left = instruction.first?.let { operand(state, it) } ?: constant(0)
                    val right = operand(state, instruction.second)
                    val expression = when (instruction.operation) {
                        Arm7DataOperation.MOVE -> right
                        Arm7DataOperation.ADD -> left + right
                        Arm7DataOperation.SUBTRACT -> left - right
                        Arm7DataOperation.MULTIPLY -> left.number?.let { right.scaled(it) }
                            ?: right.number?.let { left.scaled(it) } ?: UNKNOWN
                        Arm7DataOperation.LOGICAL_SHIFT_LEFT -> right.number?.takeIf { it in 0..31 }?.let { shiftLeft(left, it.toInt()) } ?: UNKNOWN
                        Arm7DataOperation.LOGICAL_SHIFT_RIGHT -> right.number?.takeIf { it in 0..32 }?.let { shiftRight(left, it.toInt()) } ?: UNKNOWN
                        Arm7DataOperation.AND -> when {
                            left.number != null && right.number != null -> constant(requireNotNull(left.number) and requireNotNull(right.number))
                            right.number == 255L -> narrow(left, 8)
                            right.number == 65535L -> narrow(left, 16)
                            else -> UNKNOWN
                        }
                        Arm7DataOperation.OR -> if (left.number != null && right.number != null) {
                            constant(requireNotNull(left.number) or requireNotNull(right.number))
                        } else UNKNOWN
                        else -> UNKNOWN
                    }
                    destination(instruction.destination, mergeBytes(expression))
                    if (instruction.flagsWritten.isNotEmpty()) comparison = null
                }
                is Arm7Compare -> comparison = if (instruction.operation == Arm7CompareOperation.COMPARE) {
                    Comparison(operand(state, instruction.first), operand(state, instruction.second))
                } else null
                is Arm7MemoryTransfer -> {
                    if (instruction.signed || instruction.address.flagsRead.isNotEmpty()) {
                        // Signed values cannot grant unsigned native-ID ownership.
                        if (instruction.load) destination(instruction.valueRegister, UNKNOWN) else complete = false
                    } else {
                        val address = address(state, instruction.address)
                        if (instruction.load) destination(instruction.valueRegister, read(address, instruction.width.bytes))
                        else store(address, instruction.width.bytes, registers[instruction.valueRegister.index])
                    }
                }
                is Arm7StackTransfer -> {
                    var stack = registers[13]
                    if (!instruction.load) stack -= constant(instruction.registers.size * 4L)
                    registers[13] = if (instruction.load) stack + constant(instruction.registers.size * 4L) else stack
                    instruction.registers.forEachIndexed { index, register ->
                        val address = stack + constant(index * 4L)
                        if (instruction.load) destination(register, read(address, 4)) else store(address, 4, registers[register.index])
                    }
                }
                is Arm7Branch -> {
                    if (instruction.target !in 0 until rom.size.toLong() || instruction.target and 1L != 0L) {
                        complete = false; terminal = true
                    } else if (instruction.link) {
                        val invocation = Invocation(state.pc, instruction.target.toInt(), registers.take(4), state.copy(memory = memory))
                        calls += invocation
                        if (stopAtCalls) {
                            excludedCalls += invocation
                            terminal = true
                        } else {
                            val result = summarize?.invoke(invocation) ?: value(Atom.Call(invocation.entry, invocation.arguments))
                            for (register in listOf(0, 1, 2, 3, 12, 14)) registers[register] = UNKNOWN
                            registers[0] = result
                            comparison = null
                        }
                    } else if (instruction.condition == Arm7Condition.ALWAYS) next = instruction.target.toInt()
                    else {
                        successors = listOf(true, false).mapNotNull { taken ->
                            refine(state.copy(registers = registers, memory = memory), comparison, instruction.condition, taken)
                                ?.copy(pc = if (taken) instruction.target.toInt() else next, comparison = null)
                        }
                    }
                }
                is Arm7BranchRegister -> {
                    if (instruction.link) { complete = false; terminal = true }
                    else destination(Arm7Register.PC, registers[instruction.targetRegister.index])
                }
                else -> { complete = false; terminal = true }
            }
            val targets = if (terminal) emptyList() else successors ?: listOf(state.copy(
                pc = next, registers = registers, memory = memory, comparison = comparison,
            ))
            edges[state] = targets.filter { it.pc in span }
            queue.addAll(targets)
        }
        if (!acyclic(edges)) complete = false
        return Outcome(returned.distinct(), calls.distinct(), stores.distinct(), exits.distinct(), complete,
            excludedCalls.distinct(), seen.mapTo(linkedSetOf()) { it.pc })
    }

    fun read(state: State, address: Expression, bytes: Int): Expression {
        if (!address.known || bytes !in setOf(1, 2, 4)) return UNKNOWN
        state.memory[Cell(address, bytes)]?.let { return it }
        state.memory[Cell(address, 4)]?.let { return narrow(it, bytes * 8) }
        val offset = address.number?.minus(0x08000000L)
        if (offset != null && offset >= 0 && offset + bytes <= rom.size && offset % bytes == 0L) {
            return constant(when (bytes) { 1 -> rom.u8(offset.toInt()).toLong(); 2 -> rom.u16le(offset.toInt()).toLong(); else -> rom.u32le(offset.toInt()) })
        }
        return value(Atom.Read(bytes, address))
    }

    fun range(expression: Expression, bounds: Map<Atom, LongRange> = emptyMap()): LongRange? {
        if (!expression.known) return null
        var minimum = expression.constant; var maximum = expression.constant
        for ((atom, coefficient) in expression.terms) {
            val domain = bounds[atom] ?: when (atom) {
                is Atom.Input -> 0L..((1L shl atom.bits) - 1L)
                is Atom.Read -> 0L..((1L shl (atom.bytes * 8)) - 1L)
                is Atom.Narrow -> 0L..((1L shl atom.bits) - 1L)
                is Atom.Call -> 0L..0xFFFFFFFFL
                else -> return null
            }
            try {
                minimum = Math.addExact(minimum, Math.multiplyExact(coefficient, if (coefficient >= 0) domain.first else domain.last))
                maximum = Math.addExact(maximum, Math.multiplyExact(coefficient, if (coefficient >= 0) domain.last else domain.first))
            } catch (_: ArithmeticException) { return null }
        }
        return minimum..maximum
    }

    private fun operand(state: State, operand: Arm7Operand): Expression = when (operand) {
        is Arm7Immediate -> constant(operand.value)
        is Arm7RegisterOperand -> if (operand.register == Arm7Register.PC) {
            constant((state.pc.toLong() + operand.pcBias) and -operand.alignDownTo.toLong())
        } else state.registers[operand.register.index]
        else -> UNKNOWN
    }

    private fun address(state: State, address: Arm7Address): Expression = when (address) {
        is Arm7Address.PcRelative -> constant(0x08000000L + address.resolvedAddress)
        is Arm7Address.RegisterOffset -> if (!address.preIndexed || address.writeBack || address.alignBaseTo != 1) UNKNOWN else {
            val base = state.registers[address.base.index]
            val offset = address.index?.let { state.registers[it.index] } ?: constant(address.immediate.toLong())
            if (address.add) base + offset else base - offset
        }
        else -> UNKNOWN
    }

    private fun narrow(expression: Expression, bits: Int): Expression {
        if (!expression.known || bits !in setOf(8, 16, 32)) return UNKNOWN
        if (bits == 32) return expression
        val mask = (1L shl bits) - 1L
        expression.number?.let { return constant(it and mask) }
        range(expression)?.let { if (it.first >= 0 && it.last <= mask) return expression }
        return value(Atom.Narrow(expression, bits))
    }

    private fun shiftLeft(expression: Expression, amount: Int): Expression {
        expression.number?.let { return constant((it shl amount) and 0xFFFFFFFFL) }
        if (amount == 0) return expression
        val scaled = expression.scaled(1L shl amount)
        range(scaled)?.let { if (it.first >= 0 && it.last <= 0xFFFFFFFFL) return scaled }
        return if (expression.known) value(Atom.ShiftLeft(expression, amount)) else UNKNOWN
    }

    private fun shiftRight(expression: Expression, amount: Int): Expression {
        if (amount == 32) return constant(0)
        expression.number?.let { return constant((it and 0xFFFFFFFFL) ushr amount) }
        if (amount == 0) return expression
        val shifted = expression.atom as? Atom.ShiftLeft
        if (shifted?.amount == amount) return narrow(shifted.value, 32 - amount)
        val scale = 1L shl amount
        if (expression.known && expression.constant >= 0 && expression.constant % scale == 0L &&
            expression.terms.values.all { it >= 0 && it % scale == 0L }
        ) return Expression(expression.terms.mapValues { it.value / scale }, expression.constant / scale)
        return UNKNOWN
    }

    private fun mergeBytes(expression: Expression): Expression {
        if (expression.constant != 0L || expression.terms.size != 2) return expression
        val low = expression.terms.entries.singleOrNull { it.value == 1L }?.key as? Atom.Read ?: return expression
        val high = expression.terms.entries.singleOrNull { it.value == 256L }?.key as? Atom.Read ?: return expression
        return if (low.bytes == 1 && high.bytes == 1 && high.address == low.address + constant(1)) {
            value(Atom.Read(2, low.address))
        } else expression
    }

    private fun mayOverlap(left: Cell, right: Cell): Boolean {
        if (left.address.terms != right.address.terms) return false
        return left.address.constant < right.address.constant + right.bytes && right.address.constant < left.address.constant + left.bytes
    }

    private fun refine(state: State, comparison: Comparison?, condition: Arm7Condition, taken: Boolean): State? {
        if (comparison == null) return state
        val left = comparison.left.number; val right = comparison.right.number
        if (left != null && right != null) {
            val unsignedLeft = left and 0xFFFFFFFFL; val unsignedRight = right and 0xFFFFFFFFL
            val selected = when (condition) {
                Arm7Condition.EQUAL -> unsignedLeft == unsignedRight
                Arm7Condition.NOT_EQUAL -> unsignedLeft != unsignedRight
                Arm7Condition.UNSIGNED_HIGHER -> unsignedLeft > unsignedRight
                Arm7Condition.UNSIGNED_LOWER_OR_SAME -> unsignedLeft <= unsignedRight
                Arm7Condition.CARRY_SET -> unsignedLeft >= unsignedRight
                Arm7Condition.CARRY_CLEAR -> unsignedLeft < unsignedRight
                Arm7Condition.SIGNED_GREATER -> unsignedLeft.toInt() > unsignedRight.toInt()
                Arm7Condition.SIGNED_LESS_OR_EQUAL -> unsignedLeft.toInt() <= unsignedRight.toInt()
                Arm7Condition.SIGNED_GREATER_OR_EQUAL -> unsignedLeft.toInt() >= unsignedRight.toInt()
                Arm7Condition.SIGNED_LESS -> unsignedLeft.toInt() < unsignedRight.toInt()
                else -> return state
            }
            return state.takeIf { selected == taken }
        }
        val atom = comparison.left.atom ?: return state
        val immediate = right ?: return state
        val current = state.bounds[atom] ?: range(comparison.left) ?: return state
        val selected = when (condition) {
            Arm7Condition.UNSIGNED_HIGHER -> if (taken) (immediate + 1)..current.last else current.first..immediate
            Arm7Condition.UNSIGNED_LOWER_OR_SAME -> if (taken) current.first..immediate else (immediate + 1)..current.last
            Arm7Condition.EQUAL -> if (taken) immediate..immediate else return state.takeUnless { current.first == immediate && current.last == immediate }
            Arm7Condition.NOT_EQUAL -> if (!taken) immediate..immediate else return state.takeUnless { current.first == immediate && current.last == immediate }
            else -> return state
        }
        val minimum = maxOf(current.first, selected.first); val maximum = minOf(current.last, selected.last)
        return state.copy(bounds = state.bounds + (atom to minimum..maximum)).takeIf { minimum <= maximum }
    }

    private fun acyclic(edges: Map<State, List<State>>): Boolean {
        val incoming = edges.keys.associateWith { 0 }.toMutableMap()
        edges.values.flatten().forEach { incoming[it] = incoming.getValue(it) + 1 }
        val queue = ArrayDeque<State>(); incoming.filterValues { it == 0 }.keys.forEach(queue::addLast)
        var removed = 0
        while (queue.isNotEmpty()) {
            budget.step(); val state = queue.removeFirst(); removed++
            edges.getValue(state).forEach { target ->
                val remaining = incoming.getValue(target) - 1; incoming[target] = remaining
                if (remaining == 0) queue.addLast(target)
            }
        }
        return removed == edges.size
    }

    companion object {
        val UNKNOWN = Expression.UNKNOWN
        fun constant(value: Long) = Expression.constant(value)
        fun value(atom: Atom) = Expression.value(atom)
    }
}
