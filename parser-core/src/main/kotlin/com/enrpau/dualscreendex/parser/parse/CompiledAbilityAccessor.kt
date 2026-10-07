package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage

/** Straight-line summary accessor data flow; callees preserve ARM ABI callee-saved registers. */
internal class CompiledAbilityAccessor(private val rom: RomImage, private val budget: CompiledAbilityProbeBudget) {
    fun entriesBefore(site: Int): List<Int> = (maxOf(0xC0, site - 256)..site step 2).filter { entry ->
        budget.step()
        val op = rom.u16le(entry)
        op and 0xFE00 == 0xB400 && op and 0x100 != 0
    }.reversed()

    fun analyze(entry: Int): List<TextAccess>? {
        val registers = MutableList<Value>(16) { Unknown }; registers[14] = ReturnAddress
        var stack = emptyList<Value>()
        var locals = 0
        var pc = entry
        var getter: Source? = null
        val intervening = mutableListOf<Int>()
        val accesses = mutableListOf<TextAccess>()
        repeat(128) {
            budget.step()
            if (pc < entry || pc.toLong() + 2 > rom.size) return null
            val op = rom.u16le(pc)
            var next = pc + 2
            when {
                (op and 0xF800) in setOf(0, 0x0800) -> {
                    val right = op and 0xF800 == 0x0800
                    val amount = (op ushr 6) and 31
                    registers[op and 7] = shifted(registers[(op ushr 3) and 7], if (right && amount == 0) 32 else amount, right)
                }
                op and 0xFE00 == 0x1800 -> registers[op and 7] =
                    added(registers[(op ushr 3) and 7], registers[(op ushr 6) and 7])
                op and 0xFE00 == 0x1C00 -> registers[op and 7] =
                    added(registers[(op ushr 3) and 7], Constant(((op ushr 6) and 7).toLong()))
                op and 0xF800 == 0x2000 -> registers[(op ushr 8) and 7] = Constant((op and 255).toLong())
                op and 0xF800 == 0x3000 -> {
                    val register = (op ushr 8) and 7
                    registers[register] = added(registers[register], Constant((op and 255).toLong()))
                }
                op and 0xFF00 == 0x4600 -> {
                    val destination = (op and 7) or ((op ushr 4) and 8)
                    if (destination in setOf(13, 15)) return null
                    registers[destination] = registers[(op ushr 3) and 15]
                }
                op and 0xF800 == 0x4800 -> {
                    val literal = ((pc.toLong() + 4) and -4L) + (op and 255) * 4L
                    if (literal !in 0..rom.size.toLong() - 4) return null
                    registers[(op ushr 8) and 7] = Constant(rom.u32le(literal.toInt()))
                }
                (op and 0xF800) in setOf(0x6800, 0x7800, 0x8800) -> {
                    val width = when (op and 0xF800) { 0x6800 -> 4; 0x7800 -> 1; else -> 2 }
                    val address = added(registers[(op ushr 3) and 7], Constant(((op ushr 6) and 31).toLong() * width))
                    registers[op and 7] = when {
                        width == 4 && address is Constant && isRam(address.value) && address.value and 3L == 0L ->
                            SummaryPointer(address.value)
                        address is SummaryPointer && address.offset % width == 0 -> Input(SummaryInput(address.global, address.offset, width))
                        width == 4 && address is IndexedAddress && address.id.scale == 4L -> IndirectText(address.root, address.id.source)
                        else -> Unknown
                    }
                }
                op and 0xF800 == 0xF000 -> {
                    if (pc.toLong() + 4 > rom.size) return null
                    val second = rom.u16le(pc + 2)
                    if (second and 0xF800 != 0xF800) return null
                    val high = (op and 2047).let { if (it and 1024 != 0) it - 2048 else it }
                    val target = pc.toLong() + 4 + (high.toLong() shl 12) + ((second and 2047) shl 1)
                    if (target !in 0..rom.size.toLong() - 2 || target and 1 != 0L) return null
                    val species = registers[0] as? Input; val slot = registers[1] as? Input
                    val newGetter = if (species?.scale == 1L && slot?.scale == 1L &&
                        species.input.width == 2 && slot.input.width == 1 && species.input.global == slot.input.global
                    ) Source(target.toInt(), species.input, slot.input) else null
                    val text = registers[1]
                    when {
                        newGetter != null -> { getter = newGetter; intervening.clear() }
                        text is IndexedAddress && text.id.source == getter -> accesses += TextAccess(
                            entry, text.root, false, text.id.scale.toInt(), text.id.source, target.toInt(), intervening.toList(),
                        )
                        text is IndirectText && text.source == getter -> accesses += TextAccess(
                            entry, text.root, true, 4, text.source, target.toInt(), intervening.toList(),
                        )
                        getter != null -> intervening += target.toInt()
                    }
                    (0..3).forEach { register -> registers[register] = Unknown }
                    registers[14] = Unknown
                    if (newGetter != null) registers[0] = Id(newGetter)
                    next += 2
                }
                op and 0xF800 == 0x9000 -> if ((op and 255) >= locals) return null
                op and 0xFF00 == 0xB000 -> {
                    val slots = op and 127
                    locals += if (op and 128 != 0) slots else -slots
                    if (locals !in 0..32) return null
                }
                op and 0xFE00 == 0xB400 -> {
                    if (locals != 0) return null
                    val saved = (0..7).filter { op and (1 shl it) != 0 }.map(registers::get) +
                        if (op and 0x100 != 0) listOf(registers[14]) else emptyList()
                    if (saved.isEmpty() || stack.size + saved.size > 16) return null
                    stack = saved + stack
                }
                op and 0xFE00 == 0xBC00 -> {
                    if (locals != 0) return null
                    val restored = (0..7).filter { op and (1 shl it) != 0 } + if (op and 0x100 != 0) listOf(15) else emptyList()
                    if (restored.isEmpty() || restored.size > stack.size) return null
                    restored.forEachIndexed { index, register -> registers[register] = stack[index] }
                    stack = stack.drop(restored.size)
                    if (15 in restored) return accesses.takeIf { registers[15] == ReturnAddress && stack.isEmpty() }
                }
                op and 0xFF87 == 0x4700 -> return accesses.takeIf {
                    registers[(op ushr 3) and 15] == ReturnAddress && stack.isEmpty() && locals == 0
                }
                else -> return null
            }
            pc = next
        }
        return null
    }

    private fun shifted(value: Value, amount: Int, right: Boolean): Value {
        val scale = 1L shl amount
        fun coefficient(current: Long): Long? = if (right) {
            (current / scale).takeIf { current % scale == 0L }
        } else {
            (current * scale).takeIf { current <= 65536 && it in 1..65536 }
        }
        return when (value) {
            is Constant -> Constant(if (right) value.value ushr amount else (value.value shl amount) and 0xFFFFFFFFL)
            is Id -> coefficient(value.scale)?.let { value.copy(scale = it) } ?: Unknown
            is Input -> coefficient(value.scale)?.let { value.copy(scale = it) } ?: Unknown
            else -> Unknown
        }
    }

    private fun added(left: Value, right: Value): Value {
        if (right == Constant(0)) return left
        if (left == Constant(0)) return right
        return when {
            left is Constant && right is Constant -> Constant((left.value + right.value) and 0xFFFFFFFFL)
            left is SummaryPointer && right is Constant && right.value in 0..255 -> left.copy(offset = left.offset + right.value.toInt())
            right is SummaryPointer && left is Constant -> added(right, left)
            left is Id && right is Id && left.source == right.source -> left.copy(scale = left.scale + right.scale)
            left is Id && right is Constant && right.value in 0x08000000L until 0x08000000L + rom.size ->
                IndexedAddress((right.value - 0x08000000L).toInt(), left)
            right is Id && left is Constant -> added(right, left)
            else -> Unknown
        }
    }

    private fun isRam(address: Long): Boolean = address in 0x02000000L until 0x02040000L ||
        address in 0x03000000L until 0x03008000L

    data class SummaryInput(val global: Long, val offset: Int, val width: Int)
    data class Source(val getter: Int, val species: SummaryInput, val slot: SummaryInput)
    data class TextAccess(
        val entry: Int,
        val root: Int,
        val pointerText: Boolean,
        val scale: Int,
        val source: Source,
        val sink: Int,
        val interveningCalls: List<Int>,
    )
    private sealed interface Value
    private data class Constant(val value: Long) : Value
    private data class SummaryPointer(val global: Long, val offset: Int = 0) : Value
    private data class Input(val input: SummaryInput, val scale: Long = 1) : Value
    private data class Id(val source: Source, val scale: Long = 1) : Value
    private data class IndexedAddress(val root: Int, val id: Id) : Value
    private data class IndirectText(val root: Int, val source: Source) : Value
    private data object ReturnAddress : Value
    private data object Unknown : Value
}
