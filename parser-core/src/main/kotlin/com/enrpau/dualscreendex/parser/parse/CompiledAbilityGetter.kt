package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.dataset.abilities.CompiledSpeciesAbilitySlots
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import java.util.ArrayDeque

/** Bounded reachable getter proof. Unknown instructions, foreign fields and non-native returns reject. */
internal class CompiledAbilityGetter(
    private val rom: RomImage,
    private val core: TableLayout,
    private val budget: CompiledAbilityProbeBudget,
) {
    fun resolve(entry: Int): CompiledSpeciesAbilitySlots? {
        val stride = core.stride ?: core.recordSize
        if (entry < 0 || entry and 1 != 0 || core.offset < 0 || core.count !in 1..65536 ||
            stride < core.recordSize || core.recordSize <= 0 ||
            core.offset.toLong() + (core.count - 1L) * stride + core.recordSize > rom.size
        ) return null
        budget.extent((core.count - 1L) * stride + core.recordSize)
        val initial = Frame(entry, List(16) { register -> when (register) {
            0 -> Affine(species = 1)
            1 -> Affine(slot = 1)
            14 -> ReturnAddress
            else -> Unknown
        } })
        var primary = initial
        var slots: CompiledSpeciesAbilitySlots? = null
        for (instructionIndex in 0 until 64) {
            if (slots != null) break
            budget.step()
            val op = instruction(primary.pc, entry) ?: return null
            val width = loadWidth(op)
            if (width != null) {
                val address = address(primary, op, width) as? Affine ?: return null
                slots = primarySlots(address, width, primary, stride) ?: return null
            } else {
                val next = step(primary, entry, null) ?: return null
                primary = next.frames.singleOrNull { it.pc == primary.pc + 2 } ?: return null
            }
        }
        val layout = slots ?: return null
        val queue = ArrayDeque<Frame>(); queue += initial
        val seen = mutableSetOf<Frame>()
        val successors = mutableMapOf<Frame, List<Frame>>()
        var nativeReturn = false
        while (queue.isNotEmpty()) {
            val frame = queue.removeFirst()
            if (!seen.add(frame)) continue
            budget.step()
            val result = step(frame, entry, layout) ?: return null
            if (result.returned != null) {
                when (result.returned) {
                    NativeId -> nativeReturn = true
                    Affine() -> Unit
                    else -> return null
                }
            }
            successors[frame] = result.frames.distinct()
            queue.addAll(result.frames)
        }
        return layout.takeIf { nativeReturn && isAcyclic(successors) }
    }

    // Constant-counter loops unroll into distinct states; a cycle in the state graph is unproved termination.
    private fun isAcyclic(successors: Map<Frame, List<Frame>>): Boolean {
        val incoming = successors.keys.associateWith { 0 }.toMutableMap()
        successors.values.flatten().forEach { incoming[it] = requireNotNull(incoming[it]) + 1 }
        val ready = ArrayDeque<Frame>()
        incoming.filterValues { it == 0 }.keys.forEach(ready::addLast)
        var removed = 0
        while (ready.isNotEmpty()) {
            budget.step()
            val frame = ready.removeFirst()
            removed++
            successors.getValue(frame).forEach { target ->
                val remaining = incoming.getValue(target) - 1
                incoming[target] = remaining
                if (remaining == 0) ready.addLast(target)
            }
        }
        return removed == successors.size
    }

    private fun primarySlots(value: Affine, width: Int, frame: Frame, stride: Int): CompiledSpeciesAbilitySlots? {
        val offset = value.constant - 0x08000000L - core.offset
        if (value.root != core.offset || value.species != stride.toLong() || value.slot != width.toLong() ||
            frame.minimumSlot != 0 || frame.maximumSlot !in 0..7 || offset !in 0..Int.MAX_VALUE.toLong() ||
            (core.offset.toLong() + offset) % width != 0L || stride % width != 0
        ) return null
        val count = frame.maximumSlot + 1
        if (offset + count.toLong() * width > core.recordSize) return null
        return CompiledSpeciesAbilitySlots(core.offset, core.count, stride, offset.toInt(), width, count, core.recordSize)
    }

    private fun instruction(pc: Int, entry: Int): Int? =
        pc.takeIf { it >= entry && it.toLong() + 2 <= minOf(rom.size.toLong(), entry.toLong() + 512) && it and 1 == 0 }
            ?.let(rom::u16le)

    private fun step(frame: Frame, entry: Int, slots: CompiledSpeciesAbilitySlots?): Step? {
        val op = instruction(frame.pc, entry) ?: return null
        val registers = frame.registers.toMutableList()
        var memory = frame.memory
        var stack = frame.stack
        var comparison = frame.comparison
        var next = frame.pc + 2
        fun put(register: Int, value: Value): Boolean {
            if (value is Affine && (value.species !in 0..0xFFFFFFFFL || value.slot !in 0..0xFFFFFFFFL ||
                range(value, frame)?.let { it.first < 0 || it.last > 0xFFFFFFFFL } != false)
            ) return false
            registers[register] = value
            return true
        }
        when {
            op and 0xF800 in setOf(0, 0x0800) -> {
                val amount = (op ushr 6) and 31
                val value = registers[(op ushr 3) and 7] as? Affine ?: return null
                val scale = 1L shl (if (op and 0xF800 == 0x0800 && amount == 0) 32 else amount)
                val shifted = if (op and 0xF800 == 0) {
                    Affine(value.species * scale, value.slot * scale, value.constant * scale, value.root)
                } else {
                    if (value.species % scale != 0L || value.slot % scale != 0L) return null
                    Affine(value.species / scale, value.slot / scale, value.constant / scale, value.root)
                }
                if (!put(op and 7, shifted)) return null
                comparison = null
            }
            op and 0xFE00 == 0x1800 -> {
                val left = registers[(op ushr 3) and 7] as? Affine ?: return null
                val right = registers[(op ushr 6) and 7] as? Affine ?: return null
                if (!put(op and 7, left + right)) return null
                comparison = null
            }
            op and 0xFE00 == 0x1C00 -> {
                val source = registers[(op ushr 3) and 7]
                val immediate = (op ushr 6) and 7
                val value = if (immediate == 0) source else (source as? Affine)?.plus(Affine(constant = immediate.toLong())) ?: return null
                if (!put(op and 7, value)) return null
                comparison = null
            }
            op and 0xF800 == 0x2000 -> {
                if (!put((op ushr 8) and 7, Affine(constant = (op and 255).toLong()))) return null
                comparison = null
            }
            op and 0xF800 == 0x2800 -> comparison = Comparison(registers[(op ushr 8) and 7], op and 255)
            op and 0xF800 == 0x3000 -> {
                val register = (op ushr 8) and 7
                val value = registers[register] as? Affine ?: return null
                if (!put(register, value + Affine(constant = (op and 255).toLong()))) return null
                comparison = null
            }
            op and 0xF800 == 0x4800 -> {
                val literal = ((frame.pc.toLong() + 4) and -4L) + (op and 255) * 4L
                if (literal !in 0..rom.size.toLong() - 4) return null
                val value = rom.u32le(literal.toInt())
                val root = (value - 0x08000000L).takeIf { it in 0 until rom.size.toLong() }?.toInt()
                if (!put((op ushr 8) and 7, Affine(constant = value, root = root))) return null
            }
            loadWidth(op) != null -> {
                val width = requireNotNull(loadWidth(op))
                val value = address(frame, op, width) as? Affine ?: return null
                val known = slots ?: return null
                val relative = value.constant - 0x08000000L - known.coreOffset
                val offsets = if (value.root == known.coreOffset && value.species == known.recordStride.toLong() && width == known.elementSize) {
                    if (value.slot == 0L) listOf(relative) else if (value.slot == known.elementSize.toLong()) {
                        (frame.minimumSlot..frame.maximumSlot).map { relative + it * value.slot }
                    } else return null
                } else null
                val loaded = if (offsets != null && offsets.all { offset ->
                    offset >= known.fieldOffset && offset < known.fieldOffset + known.slotCount * known.elementSize &&
                        (offset - known.fieldOffset) % known.elementSize == 0L
                }) NativeId else {
                    if (value.species != 0L || value.slot != 0L || !isRam(value.constant) || width != 2 || value.constant and 1 != 0L) return null
                    memory[value.constant] ?: return null
                }
                if (!put(op and 7, loaded)) return null
            }
            op and 0xF800 == 0x8000 -> {
                val value = address(frame, op, 2) as? Affine ?: return null
                val stored = registers[op and 7]
                if (value.species != 0L || value.slot != 0L || !isRam(value.constant) || value.constant and 1 != 0L ||
                    stored != NativeId && stored != Affine()
                ) return null
                memory = memory + (value.constant to stored)
            }
            op and 0xF000 == 0xD000 -> {
                val compared = comparison ?: return null
                val condition = (op ushr 8) and 15
                if (condition !in setOf(0, 1, 8, 9, 12)) return null
                val delta = (op and 255).let { if (it and 128 != 0) it - 256 else it } * 2
                val target = frame.pc + 4 + delta
                val frames = listOf(true, false).mapNotNull { taken ->
                    branchFrame(frame.copy(registers = registers, memory = memory, stack = stack), compared, condition, taken)
                        ?.copy(pc = if (taken) target else next, comparison = null)
                }
                if (frames.isEmpty()) return null
                return Step(frames)
            }
            op and 0xF800 == 0xE000 -> {
                val delta = (op and 2047).let { if (it and 1024 != 0) it - 2048 else it } * 2
                next = frame.pc + 4 + delta
            }
            op and 0xFE00 == 0xB400 -> {
                val saved = (0..7).filter { op and (1 shl it) != 0 }.map(registers::get) +
                    if (op and 0x100 != 0) listOf(registers[14]) else emptyList()
                if (saved.isEmpty() || stack.size + saved.size > 32) return null
                stack = saved + stack
            }
            op and 0xFE00 == 0xBC00 -> {
                val restored = (0..7).filter { op and (1 shl it) != 0 } + if (op and 0x100 != 0) listOf(15) else emptyList()
                if (restored.isEmpty() || restored.size > stack.size) return null
                restored.forEachIndexed { index, register -> registers[register] = stack[index] }
                stack = stack.drop(restored.size)
                if (15 in restored) {
                    if (registers[15] != ReturnAddress || stack.isNotEmpty()) return null
                    return Step(emptyList(), registers[0])
                }
            }
            op and 0xFF87 == 0x4700 -> {
                if (registers[(op ushr 3) and 15] != ReturnAddress || stack.isNotEmpty()) return null
                return Step(emptyList(), registers[0])
            }
            else -> return null
        }
        return Step(listOf(frame.copy(pc = next, registers = registers, memory = memory, stack = stack, comparison = comparison)))
    }

    private fun address(frame: Frame, op: Int, width: Int): Value? {
        val base = frame.registers[(op ushr 3) and 7] as? Affine ?: return null
        return base + Affine(constant = ((op ushr 6) and 31).toLong() * width)
    }

    private fun loadWidth(op: Int): Int? = when (op and 0xF800) {
        0x7800 -> 1
        0x8800 -> 2
        else -> null
    }

    private fun branchFrame(frame: Frame, comparison: Comparison, condition: Int, taken: Boolean): Frame? {
        val bounds = range(comparison.value, frame) ?: return null
        if (condition == 12 && bounds.last > Int.MAX_VALUE.toLong()) return null
        val immediate = comparison.immediate.toLong()
        val possible = when (condition) {
            0 -> if (taken) immediate in bounds else bounds.first != immediate || bounds.last != immediate
            1 -> if (taken) bounds.first != immediate || bounds.last != immediate else immediate in bounds
            8, 12 -> if (taken) bounds.last > immediate else bounds.first <= immediate
            9 -> if (taken) bounds.first <= immediate else bounds.last > immediate
            else -> false
        }
        if (!possible) return null
        if (comparison.value != Affine(slot = 1)) return frame
        val lower: Int; val upper: Int
        when (condition) {
            8, 12 -> if (taken) { lower = comparison.immediate + 1; upper = frame.maximumSlot }
                else { lower = frame.minimumSlot; upper = comparison.immediate }
            9 -> if (taken) { lower = frame.minimumSlot; upper = comparison.immediate }
                else { lower = comparison.immediate + 1; upper = frame.maximumSlot }
            0, 1 -> if ((condition == 0) == taken) { lower = comparison.immediate; upper = comparison.immediate }
                else return frame
            else -> return null
        }
        val minimum = maxOf(frame.minimumSlot, lower); val maximum = minOf(frame.maximumSlot, upper)
        return frame.copy(minimumSlot = minimum, maximumSlot = maximum).takeIf { minimum <= maximum }
    }

    private fun range(value: Value, frame: Frame): LongRange? = when (value) {
        is Affine -> if (value.species >= 0 && value.slot >= 0) {
            (value.constant + value.slot * frame.minimumSlot)..
                (value.constant + value.species * (core.count - 1L) + value.slot * frame.maximumSlot)
        } else null
        NativeId -> 0L..65535L
        else -> null
    }

    private fun isRam(address: Long): Boolean = address in 0x02000000L until 0x02040000L ||
        address in 0x03000000L until 0x03008000L

    private sealed interface Value
    private data class Affine(
        val species: Long = 0,
        val slot: Long = 0,
        val constant: Long = 0,
        val root: Int? = null,
    ) : Value {
        operator fun plus(other: Affine) = Affine(species + other.species, slot + other.slot, constant + other.constant,
            when {
                root == null -> other.root
                other.root == null || root == other.root -> root
                else -> -1
            })
    }
    private data object NativeId : Value
    private data object ReturnAddress : Value
    private data object Unknown : Value
    private data class Comparison(val value: Value, val immediate: Int)
    private data class Frame(
        val pc: Int,
        val registers: List<Value>,
        val memory: Map<Long, Value> = emptyMap(),
        val stack: List<Value> = emptyList(),
        val comparison: Comparison? = null,
        val minimumSlot: Int = 0,
        val maximumSlot: Int = 255,
    )
    private data class Step(val frames: List<Frame>, val returned: Value? = null)
}
