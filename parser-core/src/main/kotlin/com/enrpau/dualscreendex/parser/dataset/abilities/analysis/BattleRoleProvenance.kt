package com.enrpau.dualscreendex.parser.dataset.abilities.analysis

import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Address
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Branch
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7BranchRegister
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Compare
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7ControlEffect
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DataOperation
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DataProcessing
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DecodeResult
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7ExecutionBudget
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7ExecutionResult
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Machine
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Memory
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7MemoryDirection
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
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7State
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

sealed interface MechanicPredicate {
    data class AttackerAbility(val abilityId: Int) : MechanicPredicate
    data class MoveSplit(val splitId: Int) : MechanicPredicate
}

data class MultiplyAttack(val numerator: Int, val denominator: Int) {
    init {
        require(numerator > 0)
        require(denominator > 0)
    }
}

data class AttackMechanic(
    val abilityId: Int,
    val predicates: Set<MechanicPredicate>,
    val effect: MultiplyAttack,
)

data class BattleRoleProvenanceResult(
    val recordPointers: List<BattleRecordPointerEvidence>,
    val fieldReads: List<FieldReadEvidence>,
    val decodedInstructions: Int,
    val incompletePaths: Int,
    val attackMechanics: List<AttackMechanic>,
)

/**
 * Bounded ARMv4T use-def analysis for parser-supplied battle-record roles.
 *
 * The ABI and routine entry are supplied by the parser. This analysis proves record-pointer and
 * field-read provenance and emits only ability-guarded attack transforms supported by the decoded
 * control/data flow. Unknown values and unsupported paths fail closed.
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
        val initial = SymbolicState.initial(abi)
        val queue = ArrayDeque<WorkItem>()
        queue += WorkItem(entry, instructionSet, initial)
        val visited = mutableSetOf<StateKey>()
        val pointers = linkedSetOf<BattleRecordPointerEvidence>()
        val fields = linkedSetOf<FieldReadEvidence>()
        val attackMechanics = linkedSetOf<AttackMechanic>()
        val joinCache = mutableMapOf<JoinKey, Int?>()
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
            state.leaveJoinedAbilityRegion(item.offset)
            recordMemoryEvidence(instruction, state, abi.record, fields)
            execute(image, instruction, state, abi, pointers, attackMechanics)

            when (val effect = instruction.controlEffect) {
                is Arm7ControlEffect.Sequential -> queue += WorkItem(item.offset + instruction.size, item.instructionSet, state)
                is Arm7ControlEffect.DirectBranch -> {
                    if (effect.conditional) {
                        val join = if (state.pendingAbilityTest == null && state.pendingMoveSplitTest == null) null else {
                            val joinKey = JoinKey(item.offset, item.instructionSet)
                            if (joinCache.containsKey(joinKey)) joinCache[joinKey] else {
                                immediatePostDominator(image, item.offset, item.instructionSet, maxDecodedInstructions)
                                    .also { joinCache[joinKey] = it }
                            }
                        }
                        val fallthrough = state.copy().also { it.applyBranch(instruction.condition, taken = false, join) }
                        val taken = state.copy().also { it.applyBranch(instruction.condition, taken = true, join) }
                        queue += WorkItem(item.offset + instruction.size, item.instructionSet, fallthrough)
                        enqueueTarget(queue, image, effect.target, item.instructionSet, taken)
                    } else {
                        enqueueTarget(queue, image, effect.target, item.instructionSet, state)
                    }
                }
                is Arm7ControlEffect.Call -> {
                    val ability = proveAbilityAccessor(
                        image,
                        effect.target,
                        if (effect.exchange) item.instructionSet.opposite() else item.instructionSet,
                        abi,
                        state[Arm7Register.R0],
                    )
                    val moveSplit = proveMoveSplitAccessor(
                        image,
                        effect.target,
                        if (effect.exchange) item.instructionSet.opposite() else item.instructionSet,
                        abi,
                        state[Arm7Register.R0],
                    )
                    val q412Effect = applyQ412Modifier(
                        image,
                        effect.target,
                        if (effect.exchange) item.instructionSet.opposite() else item.instructionSet,
                        state,
                    )
                    if (q412Effect != null) {
                        val abilityFact = state.positiveAttackerAbility()
                        val splitFact = state.positiveMoveSplit()
                        if (abilityFact != null &&
                            abilityFact.abilityId !in abi.withheldAbilityIds &&
                            splitFact != null
                        ) {
                            attackMechanics += AttackMechanic(
                                abilityId = abilityFact.abilityId,
                                predicates = setOf(
                                    MechanicPredicate.AttackerAbility(abilityFact.abilityId),
                                    MechanicPredicate.MoveSplit(splitFact.splitId),
                                ),
                                effect = q412Effect,
                            )
                        }
                    }
                    state.clobberCallerSaved()
                    if (ability != null) state[Arm7Register.R0] = ability
                    if (moveSplit != null) state[Arm7Register.R0] = moveSplit
                    queue += WorkItem(item.offset + instruction.size, item.instructionSet, state)
                }
                is Arm7ControlEffect.Return -> Unit
                is Arm7ControlEffect.IndirectBranch -> {
                    val branch = instruction as? Arm7BranchRegister
                    if (branch == null || state[branch.targetRegister] !is Value.ReturnAddress) incomplete++
                }
                else -> incomplete++
            }
        }
        if (queue.isNotEmpty()) incomplete += queue.size
        return BattleRoleProvenanceResult(
            pointers.toList(),
            fields.toList(),
            decoded,
            incomplete,
            attackMechanics.toList(),
        )
    }

    private fun execute(
        image: RomImage,
        instruction: Arm7Instruction,
        state: SymbolicState,
        abi: BattleMechanicsAbi,
        pointers: MutableSet<BattleRecordPointerEvidence>,
        attackMechanics: MutableSet<AttackMechanic>,
    ) {
        when (instruction) {
            is Arm7DataProcessing -> {
                if (instruction.flagsWritten.isNotEmpty()) {
                    state.pendingAbilityTest = null
                    state.pendingMoveSplitTest = null
                }
                state[instruction.destination] = evaluateData(instruction, state)
                val attack = state[instruction.destination] as? Value.Stat
                if (attack != null &&
                    attack.role == BattleRecordRole.ATTACKER &&
                    attack.field == abi.record.attack &&
                    (attack.numerator != 1 || attack.denominator != 1)
                ) {
                    state.abilityFacts.filterValues { it }.keys.forEach { fact ->
                        if (fact.role == BattleRecordRole.ATTACKER) {
                            attackMechanics += AttackMechanic(
                                abilityId = fact.abilityId,
                                predicates = setOf(MechanicPredicate.AttackerAbility(fact.abilityId)),
                                effect = MultiplyAttack(attack.numerator, attack.denominator),
                            )
                        }
                    }
                }
            }
            is Arm7Multiply -> {
                val left = state[instruction.multiplicand]
                val right = state[instruction.multiplier]
                state[instruction.destination] = evaluateProduct(left, right)
            }
            is Arm7MemoryTransfer -> {
                if (instruction.load) {
                    state[instruction.valueRegister] = evaluateLoad(image, instruction, state, abi)
                } else {
                    evaluateStore(instruction, state)
                }
                updateWriteBack(instruction.address, state)
            }
            is Arm7StackTransfer -> {
                if (instruction.load) state.pop(instruction.registers) else state.push(instruction.registers)
            }
            is Arm7Branch -> if (instruction.link) state[Arm7Register.LR] = Value.Constant(instruction.returnAddress!!)
            is Arm7Compare -> {
                val first = evaluateOperand(instruction.first, state)
                val second = evaluateOperand(instruction.second, state)
                state.pendingAbilityTest = abilityTest(first, second, abi)
                state.pendingMoveSplitTest = moveSplitTest(first, second)
            }
            is Arm7BranchRegister -> Unit
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
        val field = fieldAt(record, offset, width) ?: return
        fields += FieldReadEvidence(base.role, field, instruction.offset)
    }

    private fun fieldAt(record: BattleRecordAbi, offset: Int, width: ScalarWidth): ScalarField? =
        listOfNotNull(
            record.attack,
            record.defense,
            record.specialAttack,
            record.specialDefense,
            record.ability,
            record.hp,
            record.maxHp,
            record.status,
        ).singleOrNull { it.offset == offset && it.width == width }

    private fun abilityTest(first: Value, second: Value, abi: BattleMechanicsAbi): AbilityFact? {
        val field = when {
            first is Value.Field && second is Value.Constant -> first to second
            first is Value.AbilityOrNone && second is Value.Constant -> first.toField() to second
            second is Value.Field && first is Value.Constant -> second to first
            second is Value.AbilityOrNone && first is Value.Constant -> second.toField() to first
            else -> return null
        }
        if (field.first.field != abi.record.ability) return null
        val abilityId = field.second.value.toInt()
        if (abilityId !in abi.activeAbilityIds) return null
        return AbilityFact(field.first.role, abilityId)
    }

    private fun moveSplitTest(first: Value, second: Value): MoveSplitFact? {
        val splitId = when {
            first is Value.EffectiveMoveSplit && second is Value.Constant -> second.value.toInt()
            first is Value.ShiftedEffectiveMoveSplit && second is Value.Constant && second.value == 0L -> 0
            second is Value.EffectiveMoveSplit && first is Value.Constant -> first.value.toInt()
            second is Value.ShiftedEffectiveMoveSplit && first is Value.Constant && first.value == 0L -> 0
            else -> return null
        }
        return splitId.takeIf { it in 0..2 }?.let(::MoveSplitFact)
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
        left is Value.MoveId && right is Value.Constant -> Value.ScaledMoveId(right.value.toInt())
        right is Value.MoveId && left is Value.Constant -> Value.ScaledMoveId(left.value.toInt())
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
        first is Value.StackPointer && second is Value.Constant -> first.copy(byteOffset = first.byteOffset + second.value.toInt())
        second is Value.StackPointer && first is Value.Constant -> second.copy(byteOffset = second.byteOffset + first.value.toInt())
        first is Value.EffectiveSplitContext && second is Value.Constant -> first.copy(byteOffset = first.byteOffset + second.value.toInt())
        second is Value.EffectiveSplitContext && first is Value.Constant -> second.copy(byteOffset = second.byteOffset + first.value.toInt())
        first is Value.ScaledMoveId && second is Value.MoveId -> first.copy(stride = first.stride + 1)
        second is Value.ScaledMoveId && first is Value.MoveId -> second.copy(stride = second.stride + 1)
        first is Value.ScaledMoveId && second is Value.Constant -> Value.MoveRecordPointer(second.value.toInt(), first.stride)
        second is Value.ScaledMoveId && first is Value.Constant -> Value.MoveRecordPointer(first.value.toInt(), second.stride)
        first is Value.Constant && second is Value.Constant -> Value.Constant((first.value + second.value) and 0xFFFF_FFFFL)
        else -> Value.Unknown
    }

    private fun subtract(first: Value?, second: Value): Value = when {
        second is Value.Constant && second.value == 0L -> first ?: Value.Unknown
        first is Value.RecordPointer && second is Value.Constant -> first.copy(byteOffset = first.byteOffset - second.value.toInt())
        first is Value.StackPointer && second is Value.Constant -> first.copy(byteOffset = first.byteOffset - second.value.toInt())
        first is Value.Constant && second is Value.Constant -> Value.Constant((first.value - second.value) and 0xFFFF_FFFFL)
        else -> Value.Unknown
    }

    private fun shiftLeft(first: Value?, second: Value): Value = when {
        second is Value.Constant && second.value == 0L -> first ?: Value.Unknown
        first is Value.Index && second is Value.Constant -> Value.ShiftedIndex(first.role, second.value.toInt())
        first is Value.MoveId && second is Value.Constant -> Value.ScaledMoveId(1 shl second.value.toInt())
        first is Value.ScaledMoveId && second is Value.Constant -> first.copy(stride = first.stride shl second.value.toInt())
        first is Value.PackedEffectiveSplit && second is Value.Constant &&
            second.value.toInt() == 32 - (Integer.numberOfTrailingZeros(first.mask) + Integer.bitCount(first.mask)) ->
            Value.ShiftedPackedEffectiveSplit(first.mask, second.value.toInt())
        first is Value.EffectiveMoveSplit && second is Value.Constant ->
            Value.ShiftedEffectiveMoveSplit(second.value.toInt())
        first is Value.Stat && second is Value.Constant -> Value.ShiftedStat(first, second.value.toInt())
        first is Value.Constant && second is Value.Constant -> Value.Constant((first.value shl second.value.toInt()) and 0xFFFF_FFFFL)
        else -> Value.Unknown
    }

    private fun shiftRight(first: Value?, second: Value): Value = when {
        first is Value.ShiftedIndex && second is Value.Constant && first.leftShift == second.value.toInt() ->
            Value.Index(first.role)
        first is Value.ScaledMoveId && second is Value.Constant -> {
            val divisor = 1 shl second.value.toInt()
            when {
                first.stride == divisor -> Value.MoveId
                first.stride % divisor == 0 -> first.copy(stride = first.stride / divisor)
                else -> Value.Unknown
            }
        }
        first is Value.ShiftedPackedEffectiveSplit && second is Value.Constant &&
            second.value.toInt() == 32 - Integer.bitCount(first.mask) -> Value.EffectiveMoveSplit
        first is Value.ShiftedEffectiveMoveSplit && second is Value.Constant &&
            first.leftShift == second.value.toInt() -> Value.EffectiveMoveSplit
        first is Value.ShiftedStat && second is Value.Constant -> {
            val rightShift = second.value.toInt()
            val delta = first.leftShift - rightShift
            when {
                delta >= 0 -> first.stat.copy(numerator = first.stat.numerator shl delta)
                else -> first.stat.copy(denominator = first.stat.denominator shl -delta)
            }
        }
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
                    } else if (literal == abi.move.effectiveSplitContextPointer) {
                        Value.EffectiveSplitContextSlot
                    } else {
                        Value.Constant(literal.toLong() and 0xFFFF_FFFFL)
                    }
                }
            }
            is Arm7Address.RegisterOffset -> {
                val base = state[address.base]
                if (base is Value.StackPointer) {
                    val indexOffset = address.index?.let { (state[it] as? Value.Constant)?.value?.toInt() } ?: 0
                    val immediate = if (address.add) address.immediate else -address.immediate
                    state.stackMemory[base.byteOffset + indexOffset + immediate] ?: Value.Unknown
                } else if (base is Value.EffectiveSplitContextSlot &&
                    address.index == null && instruction.width == Arm7MemoryWidth.WORD && address.immediate == 0
                ) {
                    Value.EffectiveSplitContext()
                } else if (base is Value.EffectiveSplitContext) {
                    val indexOffset = address.index?.let { (state[it] as? Value.Constant)?.value?.toInt() } ?: 0
                    val immediate = if (address.add) address.immediate else -address.immediate
                    val offset = base.byteOffset + indexOffset + immediate
                    val packed = abi.move.effectiveSplitPackedField
                    if (packed != null && packed.offset == offset && packed.width == instruction.width.toScalarWidth()) {
                        Value.PackedEffectiveSplit(requireNotNull(abi.move.effectiveSplitMask))
                    } else Value.Unknown
                } else if (base is Value.MoveRecordPointer &&
                    base.root == abi.move.tableRoot &&
                    base.stride == abi.move.stride
                ) {
                    val indexOffset = address.index?.let { (state[it] as? Value.Constant)?.value?.toInt() } ?: 0
                    val immediate = if (address.add) address.immediate else -address.immediate
                    val offset = base.byteOffset + indexOffset + immediate
                    if (abi.move.category?.let { it.offset == offset && it.width == instruction.width.toScalarWidth() } == true) {
                        Value.EffectiveMoveSplit
                    } else Value.Unknown
                } else if (base is Value.RecordPointer) {
                    val indexOffset = address.index?.let { (state[it] as? Value.Constant)?.value?.toInt() } ?: 0
                    val immediate = if (address.add) address.immediate else -address.immediate
                    val field = fieldAt(abi.record, base.byteOffset + indexOffset + immediate, instruction.width.toScalarWidth() ?: return Value.Unknown)
                    if (field == null) Value.Unknown else if (field == abi.record.attack) {
                        Value.Stat(base.role, field)
                    } else {
                        Value.Field(base.role, field)
                    }
                } else if (base is Value.Constant && address.index == null && instruction.width == Arm7MemoryWidth.WORD) {
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

    private fun evaluateStore(instruction: Arm7MemoryTransfer, state: SymbolicState) {
        val address = instruction.address as? Arm7Address.RegisterOffset ?: return
        val base = state[address.base] as? Value.StackPointer ?: return
        val indexOffset = address.index?.let { (state[it] as? Value.Constant)?.value?.toInt() } ?: 0
        val immediate = if (address.add) address.immediate else -address.immediate
        state.stackMemory[base.byteOffset + indexOffset + immediate] = state[instruction.valueRegister]
    }

    private fun proveAbilityAccessor(
        image: RomImage,
        rawTarget: Long,
        instructionSet: Arm7InstructionSet,
        abi: BattleMechanicsAbi,
        argument: Value,
    ): Value.AbilityOrNone? {
        val index = argument as? Value.Index ?: return null
        val entry = rawTarget.toInt() and -2
        if (!isAlignedAndInBounds(image, entry, instructionSet)) return null
        val state = SymbolicState.helper(index)
        val queue = ArrayDeque<WorkItem>().apply { add(WorkItem(entry, instructionSet, state)) }
        val visited = mutableSetOf<StateKey>()
        val returns = mutableListOf<Value>()
        var decoded = 0
        var incomplete = 0
        while (queue.isNotEmpty() && decoded < MAX_HELPER_INSTRUCTIONS) {
            val item = queue.removeFirst()
            if (!isAlignedAndInBounds(image, item.offset, item.instructionSet)) {
                incomplete++
                continue
            }
            if (!visited.add(StateKey(item.offset, item.instructionSet, item.state.signature()))) continue
            val instruction = (decode(image, item.offset, item.instructionSet) as? Arm7DecodeResult.Decoded)?.instruction
            if (instruction == null) {
                incomplete++
                continue
            }
            decoded++
            val next = item.state.copy()
            execute(image, instruction, next, abi, linkedSetOf(), linkedSetOf())
            when (val effect = instruction.controlEffect) {
                is Arm7ControlEffect.Sequential -> queue += WorkItem(item.offset + instruction.size, item.instructionSet, next)
                is Arm7ControlEffect.DirectBranch -> {
                    if (effect.conditional) queue += WorkItem(item.offset + instruction.size, item.instructionSet, next.copy())
                    enqueueTarget(queue, image, effect.target, item.instructionSet, next)
                }
                is Arm7ControlEffect.Call -> {
                    next.clobberCallerSaved()
                    queue += WorkItem(item.offset + instruction.size, item.instructionSet, next)
                }
                is Arm7ControlEffect.Return -> returns += next[Arm7Register.R0]
                is Arm7ControlEffect.IndirectBranch -> {
                    val branch = instruction as? Arm7BranchRegister
                    if (branch != null && next[branch.targetRegister] is Value.ReturnAddress) {
                        returns += next[Arm7Register.R0]
                    } else incomplete++
                }
                else -> incomplete++
            }
        }
        if (queue.isNotEmpty() || incomplete != 0 || returns.isEmpty()) return null
        val fieldReturns = returns.filterIsInstance<Value.Field>()
        if (fieldReturns.isEmpty() || fieldReturns.any { it.role != index.role || it.field != abi.record.ability }) return null
        if (returns.any { it !is Value.Field && (it !is Value.Constant || it.value != 0L) }) return null
        return Value.AbilityOrNone(index.role, abi.record.ability)
    }

    private fun proveMoveSplitAccessor(
        image: RomImage,
        rawTarget: Long,
        instructionSet: Arm7InstructionSet,
        abi: BattleMechanicsAbi,
        argument: Value,
    ): Value.EffectiveMoveSplit? {
        if (argument !is Value.MoveId) return null
        val entry = rawTarget.toInt() and -2
        if (!isAlignedAndInBounds(image, entry, instructionSet)) return null
        val state = SymbolicState.moveHelper()
        val queue = ArrayDeque<WorkItem>().apply { add(WorkItem(entry, instructionSet, state)) }
        val visited = mutableSetOf<StateKey>()
        val returns = mutableListOf<Value>()
        var decoded = 0
        var incomplete = 0
        while (queue.isNotEmpty() && decoded < MAX_HELPER_INSTRUCTIONS) {
            val item = queue.removeFirst()
            if (!isAlignedAndInBounds(image, item.offset, item.instructionSet)) {
                incomplete++
                continue
            }
            if (!visited.add(StateKey(item.offset, item.instructionSet, item.state.signature()))) continue
            val instruction = (decode(image, item.offset, item.instructionSet) as? Arm7DecodeResult.Decoded)?.instruction
            if (instruction == null) {
                incomplete++
                continue
            }
            decoded++
            val next = item.state.copy()
            execute(image, instruction, next, abi, linkedSetOf(), linkedSetOf())
            when (val effect = instruction.controlEffect) {
                is Arm7ControlEffect.Sequential -> queue += WorkItem(item.offset + instruction.size, item.instructionSet, next)
                is Arm7ControlEffect.DirectBranch -> {
                    if (effect.conditional) queue += WorkItem(item.offset + instruction.size, item.instructionSet, next.copy())
                    enqueueTarget(queue, image, effect.target, item.instructionSet, next)
                }
                is Arm7ControlEffect.Call -> {
                    next.clobberCallerSaved()
                    queue += WorkItem(item.offset + instruction.size, item.instructionSet, next)
                }
                is Arm7ControlEffect.Return -> returns += next[Arm7Register.R0]
                is Arm7ControlEffect.IndirectBranch -> {
                    val branch = instruction as? Arm7BranchRegister
                    if (branch != null && next[branch.targetRegister] is Value.ReturnAddress) {
                        returns += next[Arm7Register.R0]
                    } else incomplete++
                }
                else -> incomplete++
            }
        }
        if (queue.isNotEmpty() || incomplete != 0 || returns.isEmpty()) return null
        if (Value.EffectiveMoveSplit !in returns) return null
        if (returns.any { it != Value.EffectiveMoveSplit && (it !is Value.Constant || it.value !in 0L..2L) }) return null
        return Value.EffectiveMoveSplit
    }

    private fun applyQ412Modifier(
        image: RomImage,
        rawTarget: Long,
        instructionSet: Arm7InstructionSet,
        state: SymbolicState,
    ): MultiplyAttack? {
        val pointer = state[Arm7Register.R0] as? Value.StackPointer ?: return null
        val current = (state.stackMemory[pointer.byteOffset] as? Value.Constant)?.value?.toInt() ?: return null
        val factor = (state[Arm7Register.R1] as? Value.Constant)?.value?.toInt() ?: return null
        if (current != Q412_ONE || factor <= 0 || factor > 0xFFFF) return null
        val entry = rawTarget.toInt() and -2
        if (!proveQ412MulWriteback(image, entry, instructionSet)) return null
        val updated = ((current.toLong() * factor + Q412_ROUND) shr Q412_SHIFT).toInt()
        state.stackMemory[pointer.byteOffset] = Value.Constant(updated.toLong())
        val divisor = gcd(factor, Q412_ONE)
        return MultiplyAttack(factor / divisor, Q412_ONE / divisor)
    }

    private fun proveQ412MulWriteback(
        image: RomImage,
        entry: Int,
        instructionSet: Arm7InstructionSet,
    ): Boolean {
        if (!isAlignedAndInBounds(image, entry, instructionSet)) return false
        val probes = listOf(
            Q412_ONE to Q412_ONE * 2,
            Q412_ONE to Q412_ONE * 3 / 2,
            Q412_ONE * 2 to Q412_ONE / 2,
        )
        return probes.all { (current, factor) ->
            val memory = Arm7Memory(image.slice(0, image.size))
            val state = Arm7State(instructionSet, Arm7Memory.ROM_START + entry).apply {
                this[Arm7Register.SP] = HELPER_STACK
                this[Arm7Register.LR] = HELPER_RETURN or 1L
                this[Arm7Register.R0] = HELPER_VALUE
                this[Arm7Register.R1] = factor.toLong()
            }
            memory.write16(HELPER_VALUE, current.toLong())
            memory.clearTrace()
            val result = Arm7Machine(memory, state).run(Arm7ExecutionBudget(MAX_Q412_HELPER_INSTRUCTIONS)) {
                if (it.state.pc == HELPER_RETURN) "returned" else null
            }
            val expected = ((current.toLong() * factor + Q412_ROUND) shr Q412_SHIFT) and 0xFFFF
            val writes = memory.traces().filter { it.direction == Arm7MemoryDirection.WRITE }
            result is Arm7ExecutionResult.Completed &&
                memory.read16(HELPER_VALUE) == expected &&
                writes.count { it.address == HELPER_VALUE && it.width == Arm7MemoryWidth.HALFWORD } == 1 &&
                writes.none { it.address == HELPER_VALUE && it.width != Arm7MemoryWidth.HALFWORD }
        }
    }

    private fun gcd(first: Int, second: Int): Int {
        var left = first
        var right = second
        while (right != 0) {
            val remainder = left % right
            left = right
            right = remainder
        }
        return left
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

    private fun immediatePostDominator(
        image: RomImage,
        branchOffset: Int,
        instructionSet: Arm7InstructionSet,
        instructionBudget: Int,
    ): Int? {
        val entry = CfgNode(branchOffset, instructionSet)
        val queue = ArrayDeque<CfgNode>().apply { add(entry) }
        val successors = linkedMapOf<CfgNode, Set<CfgNode>>()
        while (queue.isNotEmpty() && successors.size < instructionBudget) {
            val node = queue.removeFirst()
            if (node in successors) continue
            if (!isAlignedAndInBounds(image, node.offset, node.instructionSet)) return null
            val instruction = (decode(image, node.offset, node.instructionSet) as? Arm7DecodeResult.Decoded)?.instruction
                ?: return null
            val next = CfgNode(node.offset + instruction.size, node.instructionSet)
            val edges = when (val effect = instruction.controlEffect) {
                is Arm7ControlEffect.Sequential, is Arm7ControlEffect.Call -> setOf(next)
                is Arm7ControlEffect.DirectBranch -> buildSet {
                    add(CfgNode(effect.target.toInt() and -2, node.instructionSet))
                    if (effect.conditional) add(next)
                }
                else -> emptySet()
            }
            successors[node] = edges
            edges.filterNot { it in successors }.forEach(queue::addLast)
        }
        if (queue.isNotEmpty() || entry !in successors) return null

        val exit = CfgNode(-1, instructionSet)
        val nodes = successors.keys + exit
        val postDominators = nodes.associateWithTo(mutableMapOf()) { node ->
            if (node == exit) mutableSetOf(exit) else nodes.toMutableSet()
        }
        var changed: Boolean
        do {
            changed = false
            successors.entries.reversed().forEach { (node, rawEdges) ->
                val edges = rawEdges.ifEmpty { setOf(exit) }
                val intersection = edges
                    .map { postDominators[it] ?: mutableSetOf(exit) }
                    .reduce { left, right -> left.intersect(right).toMutableSet() }
                val updated = (intersection + node).toMutableSet()
                if (postDominators[node] != updated) {
                    postDominators[node] = updated
                    changed = true
                }
            }
        } while (changed)

        return postDominators[entry]
            ?.filter { it != entry && it != exit }
            ?.maxByOrNull { postDominators[it]?.size ?: 0 }
            ?.offset
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
    private data class JoinKey(val offset: Int, val instructionSet: Arm7InstructionSet)
    private data class CfgNode(val offset: Int, val instructionSet: Arm7InstructionSet)

    private class SymbolicState private constructor(
        private val registers: Array<Value>,
        var pendingAbilityTest: AbilityFact? = null,
        val abilityFacts: MutableMap<AbilityFact, Boolean> = mutableMapOf(),
        var pendingMoveSplitTest: MoveSplitFact? = null,
        val moveSplitFacts: MutableMap<MoveSplitFact, Boolean> = mutableMapOf(),
        private val stack: MutableList<Value> = mutableListOf(),
        val stackMemory: MutableMap<Int, Value> = mutableMapOf(),
        val abilityFactJoins: MutableMap<AbilityFact, Int> = mutableMapOf(),
        val moveSplitFactJoins: MutableMap<MoveSplitFact, Int> = mutableMapOf(),
    ) {
        operator fun get(register: Arm7Register): Value = registers[register.index]
        operator fun set(register: Arm7Register, value: Value) { registers[register.index] = value }
        fun copy(): SymbolicState = SymbolicState(
            registers.copyOf(),
            pendingAbilityTest,
            abilityFacts.toMutableMap(),
            pendingMoveSplitTest,
            moveSplitFacts.toMutableMap(),
            stack.toMutableList(),
            stackMemory.toMutableMap(),
            abilityFactJoins.toMutableMap(),
            moveSplitFactJoins.toMutableMap(),
        )
        fun signature(): List<Value> = registers.toList() +
            Value.Facts(
                pendingAbilityTest,
                abilityFacts.toMap(),
                abilityFactJoins.toMap(),
                pendingMoveSplitTest,
                moveSplitFacts.toMap(),
                moveSplitFactJoins.toMap(),
            ) +
            Value.Stack(stack.toList(), stackMemory.toMap())
        fun push(registers: Collection<Arm7Register>) {
            val ordered = registers.sortedBy { it.index }.map { this[it] }
            stack.addAll(0, ordered)
            this[Arm7Register.SP] = (this[Arm7Register.SP] as? Value.StackPointer)
                ?.let { it.copy(byteOffset = it.byteOffset - ordered.size * 4) } ?: Value.Unknown
        }
        fun pop(registers: Collection<Arm7Register>) {
            registers.sortedBy { it.index }.forEach { register ->
                this[register] = if (stack.isEmpty()) Value.Unknown else stack.removeAt(0)
            }
            this[Arm7Register.SP] = (this[Arm7Register.SP] as? Value.StackPointer)
                ?.let { it.copy(byteOffset = it.byteOffset + registers.size * 4) } ?: Value.Unknown
        }
        fun applyBranch(
            condition: com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Condition,
            taken: Boolean,
            join: Int?,
        ) {
            val equals = when (condition) {
                com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Condition.EQUAL -> taken
                com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Condition.NOT_EQUAL -> !taken
                else -> {
                    pendingAbilityTest = null
                    pendingMoveSplitTest = null
                    return
                }
            }
            pendingAbilityTest?.let { fact ->
                abilityFacts[fact] = equals
                if (equals && join != null) abilityFactJoins[fact] = join
            }
            pendingMoveSplitTest?.let { fact ->
                moveSplitFacts[fact] = equals
                if (equals && join != null) moveSplitFactJoins[fact] = join
            }
            pendingAbilityTest = null
            pendingMoveSplitTest = null
        }
        fun leaveJoinedAbilityRegion(offset: Int) {
            abilityFactJoins.filterValues { it == offset }.keys.forEach { fact ->
                abilityFactJoins.remove(fact)
                abilityFacts.remove(fact)
            }
            moveSplitFactJoins.filterValues { it == offset }.keys.forEach { fact ->
                moveSplitFactJoins.remove(fact)
                moveSplitFacts.remove(fact)
            }
        }
        fun clobberCallerSaved() {
            listOf(Arm7Register.R0, Arm7Register.R1, Arm7Register.R2, Arm7Register.R3, Arm7Register.R12).forEach {
                this[it] = Value.Unknown
            }
        }
        fun positiveAttackerAbility(): AbilityFact? = abilityFacts
            .filter { (fact, equals) -> equals && fact.role == BattleRecordRole.ATTACKER }
            .keys
            .singleOrNull()
        fun positiveMoveSplit(): MoveSplitFact? = moveSplitFacts
            .filterValues { it }
            .keys
            .singleOrNull()

        companion object {
            fun initial(abi: BattleMechanicsAbi): SymbolicState {
                val state = SymbolicState(Array(16) { Value.Unknown })
                state[Arm7Register.SP] = Value.StackPointer(0)
                state[Arm7Register.LR] = Value.ReturnAddress
                abi.moveParameterRegister?.let { state[Arm7Register.fromIndex(it)] = Value.MoveId }
                when (val contract = abi.roleContract) {
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

            fun helper(argument: Value.Index): SymbolicState = SymbolicState(Array(16) { Value.Unknown }).also { state ->
                state[Arm7Register.R0] = argument
                state[Arm7Register.SP] = Value.StackPointer(0)
                state[Arm7Register.LR] = Value.ReturnAddress
            }

            fun moveHelper(): SymbolicState = SymbolicState(Array(16) { Value.Unknown }).also { state ->
                state[Arm7Register.R0] = Value.MoveId
                state[Arm7Register.SP] = Value.StackPointer(0)
                state[Arm7Register.LR] = Value.ReturnAddress
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
        data object MoveId : Value
        data class ScaledMoveId(val stride: Int) : Value
        data class MoveRecordPointer(val root: Int, val stride: Int, val byteOffset: Int = 0) : Value
        data object EffectiveMoveSplit : Value
        data class ShiftedEffectiveMoveSplit(val leftShift: Int) : Value
        data object EffectiveSplitContextSlot : Value
        data class EffectiveSplitContext(val byteOffset: Int = 0) : Value
        data class PackedEffectiveSplit(val mask: Int) : Value
        data class ShiftedPackedEffectiveSplit(val mask: Int, val leftShift: Int) : Value
        data class RecordPointer(
            val role: BattleRecordRole,
            val origin: BattleRecordOrigin,
            val byteOffset: Int = 0,
            val announced: Boolean = false,
        ) : Value
        data class Field(val role: BattleRecordRole, val field: ScalarField) : Value
        data class AbilityOrNone(val role: BattleRecordRole, val field: ScalarField) : Value {
            fun toField() = Field(role, field)
        }
        data class Stat(
            val role: BattleRecordRole,
            val field: ScalarField,
            val numerator: Int = 1,
            val denominator: Int = 1,
        ) : Value
        data class ShiftedStat(val stat: Stat, val leftShift: Int) : Value
        data class Facts(
            val pending: AbilityFact?,
            val facts: Map<AbilityFact, Boolean>,
            val joins: Map<AbilityFact, Int>,
            val pendingMoveSplit: MoveSplitFact?,
            val moveSplitFacts: Map<MoveSplitFact, Boolean>,
            val moveSplitJoins: Map<MoveSplitFact, Int>,
        ) : Value
        data class Stack(val values: List<Value>, val memory: Map<Int, Value>) : Value
        data class StackPointer(val byteOffset: Int) : Value
        data object ReturnAddress : Value
    }

    private data class AbilityFact(val role: BattleRecordRole, val abilityId: Int)
    private data class MoveSplitFact(val splitId: Int)

    private const val GBA_ROM_BASE = 0x0800_0000L
    private const val MAX_HELPER_INSTRUCTIONS = 1_024
    private const val MAX_Q412_HELPER_INSTRUCTIONS = 128
    private const val Q412_SHIFT = 12
    private const val Q412_ONE = 1 shl Q412_SHIFT
    private const val Q412_ROUND = 1 shl (Q412_SHIFT - 1)
    private const val HELPER_STACK = 0x0300_7F00L
    private const val HELPER_VALUE = 0x0300_7000L
    private const val HELPER_RETURN = 0x0203_FFF0L

    private fun Arm7InstructionSet.opposite(): Arm7InstructionSet =
        if (this == Arm7InstructionSet.THUMB) Arm7InstructionSet.ARM else Arm7InstructionSet.THUMB
}
