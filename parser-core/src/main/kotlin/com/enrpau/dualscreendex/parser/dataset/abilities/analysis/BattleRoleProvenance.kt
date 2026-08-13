package com.enrpau.dualscreendex.parser.dataset.abilities.analysis

import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Address
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Branch
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7BranchRegister
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Compare
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7ControlEffect
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DataOperation
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DataProcessing
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DecodeResult
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Immediate
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Instruction
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7InstructionSet
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7MemoryTransfer
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7MemoryWidth
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Multiply
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Operand
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Register
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7RegisterOperand
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7StackTransfer
import com.enrpau.dualscreendex.parser.analysis.arm7.ArmDecoder
import com.enrpau.dualscreendex.parser.analysis.thumb.ThumbDecoder
import com.enrpau.dualscreendex.parser.io.RomImage
import java.util.ArrayDeque

enum class BattleRecordRole { ATTACKER, DEFENDER }
enum class BattleRecordOrigin { DIRECT_PARAMETER, INDEXED_ARRAY }

data class BattleRecordPointerEvidence(
    val role: BattleRecordRole,
    val origin: BattleRecordOrigin,
    val instructionOffset: Int,
)

data class FieldReadEvidence(
    val role: BattleRecordRole,
    val field: ScalarField,
    val instructionOffset: Int,
)

data class BattleRoleProvenanceResult(
    val recordPointers: List<BattleRecordPointerEvidence>,
    val fieldReads: List<FieldReadEvidence>,
    val decodedInstructions: Int,
    val incompletePaths: Int,
)

/**
 * Bounded ARMv4T use-def analysis for parser-supplied battle-record roles.
 *
 * This intentionally proves only record-pointer and field-read provenance. It does not infer an
 * ABI, locate a routine, or assign mechanics. Unknown values and unsupported paths fail closed.
 */
object BattleRoleProvenance {
    fun analyze(
        image: RomImage,
        entry: Int,
        instructionSet: Arm7InstructionSet,
        abi: BattleMechanicsAbi,
        maxDecodedInstructions: Int,
    ): BattleRoleProvenanceResult {
        require(entry >= 0)
        require(maxDecodedInstructions > 0)
        val initial = SymbolicState.initial(abi.roleContract)
        val queue = ArrayDeque<WorkItem>()
        queue += WorkItem(entry, instructionSet, initial)
        val visited = mutableSetOf<StateKey>()
        val pointers = linkedSetOf<BattleRecordPointerEvidence>()
        val fields = linkedSetOf<FieldReadEvidence>()
        if (abi.roleContract is BattleRoleContract.DirectPointers) {
            pointers += BattleRecordPointerEvidence(
                BattleRecordRole.ATTACKER,
                BattleRecordOrigin.DIRECT_PARAMETER,
                entry,
            )
            pointers += BattleRecordPointerEvidence(
                BattleRecordRole.DEFENDER,
                BattleRecordOrigin.DIRECT_PARAMETER,
                entry,
            )
        }
        var decoded = 0
        var incomplete = 0

        while (queue.isNotEmpty() && decoded < maxDecodedInstructions) {
            val item = queue.removeFirst()
            if (!isAlignedAndInBounds(image, item.offset, item.instructionSet)) {
                incomplete++
                continue
            }
            val key = StateKey(item.offset, item.instructionSet, item.state.signature())
            if (!visited.add(key)) continue
            val result = decode(image, item.offset, item.instructionSet)
            val instruction = (result as? Arm7DecodeResult.Decoded)?.instruction
            if (instruction == null) {
                incomplete++
                continue
            }
            decoded++
            val state = item.state.copy()
            recordMemoryEvidence(instruction, state, abi.record, fields)
            execute(image, instruction, state, abi, pointers)

            when (val effect = instruction.controlEffect) {
                is Arm7ControlEffect.Sequential -> queue += WorkItem(item.offset + instruction.size, item.instructionSet, state)
                is Arm7ControlEffect.DirectBranch -> {
                    if (effect.conditional) queue += WorkItem(item.offset + instruction.size, item.instructionSet, state.copy())
                    enqueueTarget(queue, image, effect.target, item.instructionSet, state)
                }
                is Arm7ControlEffect.Call -> {
                    // Interprocedural helper summaries are separate semantic evidence. A call is
                    // opaque here: continue at the return with caller-saved values invalidated.
                    state.clobberCallerSaved()
                    queue += WorkItem(item.offset + instruction.size, item.instructionSet, state)
                }
                is Arm7ControlEffect.Return -> Unit
                else -> incomplete++
            }
        }
        if (queue.isNotEmpty()) incomplete += queue.size
        return BattleRoleProvenanceResult(pointers.toList(), fields.toList(), decoded, incomplete)
    }

    private fun execute(
        image: RomImage,
        instruction: Arm7Instruction,
        state: SymbolicState,
        abi: BattleMechanicsAbi,
        pointers: MutableSet<BattleRecordPointerEvidence>,
    ) {
        when (instruction) {
            is Arm7DataProcessing -> {
                state[instruction.destination] = evaluateData(instruction, state)
            }
            is Arm7Multiply -> {
                val left = state[instruction.multiplicand]
                val right = state[instruction.multiplier]
                state[instruction.destination] = evaluateProduct(left, right)
            }
            is Arm7MemoryTransfer -> {
                if (instruction.load) {
                    state[instruction.valueRegister] = evaluateLoad(image, instruction, state, abi)
                }
                updateWriteBack(instruction.address, state)
            }
            is Arm7StackTransfer -> {
                // Stack spills are not needed by the initial role proof. POP-loaded registers are
                // unknown; PUSH preserves register origins.
                if (instruction.load) instruction.registers.forEach { state[it] = Value.Unknown }
            }
            is Arm7Branch -> if (instruction.link) state[Arm7Register.LR] = Value.Constant(instruction.returnAddress!!)
            is Arm7BranchRegister, is Arm7Compare -> Unit
            else -> instruction.registersWritten.forEach { state[it] = Value.Unknown }
        }
        if (abi.roleContract !is BattleRoleContract.IndexedArray) return
        for (register in Arm7Register.entries) {
            val value = state[register]
            val record = value as? Value.RecordPointer ?: continue
            if (record.origin != BattleRecordOrigin.INDEXED_ARRAY || record.announced) continue
            state[register] = record.copy(announced = true)
            pointers += BattleRecordPointerEvidence(record.role, record.origin, instruction.offset)
        }
        // An indexed pointer is admissible only when its expression used the supplied root and
        // exact supplied stride. evaluateData/evaluateProduct enforce both relationships.
    }

    private fun recordMemoryEvidence(
        instruction: Arm7Instruction,
        state: SymbolicState,
        record: BattleRecordAbi,
        fields: MutableSet<FieldReadEvidence>,
    ) {
        val transfer = instruction as? Arm7MemoryTransfer ?: return
        if (!transfer.load) return
        val address = transfer.address as? Arm7Address.RegisterOffset ?: return
        val base = state[address.base] as? Value.RecordPointer ?: return
        val indexOffset = address.index?.let { (state[it] as? Value.Constant)?.value?.toInt() } ?: 0
        val signedImmediate = if (address.add) address.immediate else -address.immediate
        val offset = base.byteOffset + indexOffset + signedImmediate
        val width = transfer.width.toScalarWidth() ?: return
        val field = listOfNotNull(
            record.attack,
            record.defense,
            record.specialAttack,
            record.specialDefense,
            record.ability,
            record.hp,
            record.maxHp,
            record.status,
        ).singleOrNull { it.offset == offset && it.width == width } ?: return
        fields += FieldReadEvidence(base.role, field, instruction.offset)
    }

    private fun evaluateData(instruction: Arm7DataProcessing, state: SymbolicState): Value {
        val first = instruction.first?.let { evaluateOperand(it, state) }
        val second = evaluateOperand(instruction.second, state)
        return when (instruction.operation) {
            Arm7DataOperation.MOVE -> second
            Arm7DataOperation.ADD -> add(first, second)
            Arm7DataOperation.SUBTRACT -> subtract(first, second)
            Arm7DataOperation.LOGICAL_SHIFT_LEFT -> shiftLeft(first, second)
            Arm7DataOperation.LOGICAL_SHIFT_RIGHT -> shiftRight(first, second)
            Arm7DataOperation.MULTIPLY -> evaluateProduct(first ?: Value.Unknown, second)
            else -> Value.Unknown
        }
    }

    private fun evaluateOperand(operand: Arm7Operand, state: SymbolicState): Value = when (operand) {
        is Arm7Immediate -> Value.Constant(operand.value)
        is Arm7RegisterOperand -> state[operand.register]
        else -> Value.Unknown
    }

    private fun evaluateProduct(left: Value, right: Value): Value = when {
        left is Value.Index && right is Value.Constant -> Value.ScaledIndex(left.role, right.value.toInt())
        right is Value.Index && left is Value.Constant -> Value.ScaledIndex(right.role, left.value.toInt())
        left is Value.Constant && right is Value.Constant -> Value.Constant((left.value * right.value) and 0xFFFF_FFFFL)
        else -> Value.Unknown
    }

    private fun add(first: Value?, second: Value): Value = when {
        first is Value.Constant && first.value == 0L -> second
        second is Value.Constant && second.value == 0L -> first ?: Value.Unknown
        first is Value.ScaledIndex && second is Value.ArrayRoot && first.stride == second.stride ->
            Value.RecordPointer(first.role, BattleRecordOrigin.INDEXED_ARRAY)
        second is Value.ScaledIndex && first is Value.ArrayRoot && second.stride == first.stride ->
            Value.RecordPointer(second.role, BattleRecordOrigin.INDEXED_ARRAY)
        first is Value.RecordPointer && second is Value.Constant -> first.copy(byteOffset = first.byteOffset + second.value.toInt())
        second is Value.RecordPointer && first is Value.Constant -> second.copy(byteOffset = second.byteOffset + first.value.toInt())
        first is Value.Constant && second is Value.Constant -> Value.Constant((first.value + second.value) and 0xFFFF_FFFFL)
        else -> Value.Unknown
    }

    private fun subtract(first: Value?, second: Value): Value = when {
        second is Value.Constant && second.value == 0L -> first ?: Value.Unknown
        first is Value.RecordPointer && second is Value.Constant -> first.copy(byteOffset = first.byteOffset - second.value.toInt())
        first is Value.Constant && second is Value.Constant -> Value.Constant((first.value - second.value) and 0xFFFF_FFFFL)
        else -> Value.Unknown
    }

    private fun shiftLeft(first: Value?, second: Value): Value = when {
        first is Value.Index && second is Value.Constant -> Value.ShiftedIndex(first.role, second.value.toInt())
        first is Value.Constant && second is Value.Constant -> Value.Constant((first.value shl second.value.toInt()) and 0xFFFF_FFFFL)
        else -> Value.Unknown
    }

    private fun shiftRight(first: Value?, second: Value): Value = when {
        first is Value.ShiftedIndex && second is Value.Constant && first.leftShift == second.value.toInt() ->
            Value.Index(first.role)
        first is Value.Constant && second is Value.Constant -> Value.Constant(first.value ushr second.value.toInt())
        else -> Value.Unknown
    }

    private fun evaluateLoad(
        image: RomImage,
        instruction: Arm7MemoryTransfer,
        state: SymbolicState,
        abi: BattleMechanicsAbi,
    ): Value {
        val address = instruction.address
        return when (address) {
            is Arm7Address.PcRelative -> {
                if (instruction.width != Arm7MemoryWidth.WORD || address.resolvedAddress !in 0..image.size - 4L) {
                    Value.Unknown
                } else {
                    val literal = image.u32le(address.resolvedAddress.toInt()).toInt()
                    val indexed = abi.roleContract as? BattleRoleContract.IndexedArray
                    if (indexed != null && literal == indexed.battleArrayRoot) {
                        Value.ArrayRoot(literal, abi.record.stride)
                    } else {
                        Value.Constant(literal.toLong() and 0xFFFF_FFFFL)
                    }
                }
            }
            is Arm7Address.RegisterOffset -> {
                val base = state[address.base]
                if (base is Value.Constant && address.index == null && instruction.width == Arm7MemoryWidth.WORD) {
                    val mappedAddress = base.value + if (address.add) address.immediate else -address.immediate
                    val romOffset = mappedAddress - GBA_ROM_BASE
                    if (romOffset in 0..image.size - 4L) Value.Constant(image.u32le(romOffset.toInt())) else Value.Unknown
                } else Value.Unknown
            }
            else -> Value.Unknown
        }
    }

    private fun updateWriteBack(address: Arm7Address, state: SymbolicState) {
        val registerOffset = address as? Arm7Address.RegisterOffset ?: return
        if (registerOffset.writeBack) state[registerOffset.base] = Value.Unknown
    }

    private fun enqueueTarget(
        queue: ArrayDeque<WorkItem>,
        image: RomImage,
        rawTarget: Long,
        currentSet: Arm7InstructionSet,
        state: SymbolicState,
    ) {
        val target = rawTarget.toInt()
        val targetSet = if (rawTarget and 1L != 0L) Arm7InstructionSet.THUMB else currentSet
        val offset = target and -2
        if (isAlignedAndInBounds(image, offset, targetSet)) queue += WorkItem(offset, targetSet, state.copy())
    }

    private fun decode(image: RomImage, offset: Int, set: Arm7InstructionSet): Arm7DecodeResult = when (set) {
        Arm7InstructionSet.THUMB -> ThumbDecoder.decode(image, offset)
        Arm7InstructionSet.ARM -> ArmDecoder.decode(image, offset)
    }

    private fun isAlignedAndInBounds(image: RomImage, offset: Int, set: Arm7InstructionSet): Boolean {
        val size = if (set == Arm7InstructionSet.THUMB) 2 else 4
        return offset >= 0 && offset % size == 0 && offset.toLong() + size <= image.size.toLong()
    }

    private fun Arm7MemoryWidth.toScalarWidth(): ScalarWidth? = when (this) {
        Arm7MemoryWidth.BYTE -> ScalarWidth.U8
        Arm7MemoryWidth.HALFWORD -> ScalarWidth.U16
        Arm7MemoryWidth.WORD -> ScalarWidth.U32
    }

    private data class WorkItem(val offset: Int, val instructionSet: Arm7InstructionSet, val state: SymbolicState)
    private data class StateKey(val offset: Int, val instructionSet: Arm7InstructionSet, val signature: List<Value>)

    private class SymbolicState private constructor(private val registers: Array<Value>) {
        operator fun get(register: Arm7Register): Value = registers[register.index]
        operator fun set(register: Arm7Register, value: Value) { registers[register.index] = value }
        fun copy(): SymbolicState = SymbolicState(registers.copyOf())
        fun signature(): List<Value> = registers.toList()
        fun clobberCallerSaved() {
            listOf(Arm7Register.R0, Arm7Register.R1, Arm7Register.R2, Arm7Register.R3, Arm7Register.R12).forEach {
                this[it] = Value.Unknown
            }
        }

        companion object {
            fun initial(contract: BattleRoleContract): SymbolicState {
                val state = SymbolicState(Array(16) { Value.Unknown })
                when (contract) {
                    is BattleRoleContract.DirectPointers -> {
                        state[Arm7Register.fromIndex(contract.attackerParameterRegister)] =
                            Value.RecordPointer(BattleRecordRole.ATTACKER, BattleRecordOrigin.DIRECT_PARAMETER, announced = true)
                        state[Arm7Register.fromIndex(contract.defenderParameterRegister)] =
                            Value.RecordPointer(BattleRecordRole.DEFENDER, BattleRecordOrigin.DIRECT_PARAMETER, announced = true)
                    }
                    is BattleRoleContract.IndexedArray -> {
                        state[Arm7Register.fromIndex(contract.attackerIndexParameterRegister)] = Value.Index(BattleRecordRole.ATTACKER)
                        state[Arm7Register.fromIndex(contract.defenderIndexParameterRegister)] = Value.Index(BattleRecordRole.DEFENDER)
                        // Literal loads are first represented as ROM addresses. Once dereferenced,
                        // only the exact typed array root is promoted by normalizeLiteralLoad.
                    }
                }
                return state
            }
        }
    }

    private sealed interface Value {
        data object Unknown : Value
        data class Constant(val value: Long) : Value
        data class ArrayRoot(val value: Int, val stride: Int) : Value
        data class Index(val role: BattleRecordRole) : Value
        data class ShiftedIndex(val role: BattleRecordRole, val leftShift: Int) : Value
        data class ScaledIndex(val role: BattleRecordRole, val stride: Int) : Value
        data class RecordPointer(
            val role: BattleRecordRole,
            val origin: BattleRecordOrigin,
            val byteOffset: Int = 0,
            val announced: Boolean = false,
        ) : Value
    }

    private const val GBA_ROM_BASE = 0x0800_0000L
}
