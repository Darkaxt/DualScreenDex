package com.enrpau.dualscreendex.parser.analysis.arm7

import com.enrpau.dualscreendex.parser.analysis.thumb.ThumbDecoder

class Arm7Machine(
    val memory: Arm7Memory,
    val state: Arm7State,
    private val softwareInterruptHandler: ((Int, Arm7Machine) -> Boolean)? = null,
) {
    private var executed = 0

    fun step(): Arm7ExecutionResult {
        val pc = state.pc
        val size = if (state.instructionSet == Arm7InstructionSet.ARM) 4 else 2
        val offset = memory.romOffset(pc, size)
            ?: return Arm7ExecutionResult.EscapedExecution(executed, state.copy(), pc)
        val decode = if (state.instructionSet == Arm7InstructionSet.ARM) {
            ArmDecoder.decode(memory.romImage, offset)
        } else {
            ThumbDecoder.decode(memory.romImage, offset)
        }
        val instruction = when (decode) {
            is Arm7DecodeResult.Decoded -> decode.instruction
            else -> return Arm7ExecutionResult.UnsupportedInstruction(executed, state.copy(), decode)
        }
        if (!conditionPass(instruction.condition)) {
            state.pc = pc + instruction.size
            executed++
            return Arm7ExecutionResult.Stepped(instruction, executed, state.copy())
        }

        state.pc = pc + instruction.size
        return try {
            when (instruction) {
                is Arm7DataProcessing -> executeData(instruction, pc)
                is Arm7Compare -> executeCompare(instruction, pc)
                is Arm7Multiply -> executeMultiply(instruction)
                is Arm7LongMultiply -> executeLongMultiply(instruction)
                is Arm7MemoryTransfer -> executeMemory(instruction, pc)
                is Arm7Swap -> executeSwap(instruction)
                is Arm7StackTransfer -> executeStack(instruction)
                is Arm7BlockTransfer -> executeBlock(instruction)
                is Arm7Branch -> executeBranch(instruction)
                is Arm7BranchRegister -> executeBranchRegister(instruction)
                is Arm7SoftwareInterrupt -> {
                    if (softwareInterruptHandler?.invoke(instruction.comment, this) != true) {
                        return Arm7ExecutionResult.UnsupportedInstruction(
                            executed,
                            state.copy(),
                            Arm7DecodeResult.UnsupportedArchitecture(
                                instruction.offset, instruction.size, instruction.raw,
                                "software interrupt ${instruction.comment} has no named host operation",
                            ),
                        )
                    }
                }
                is Arm7StatusTransfer -> return Arm7ExecutionResult.UnsupportedInstruction(
                    executed,
                    state.copy(),
                    Arm7DecodeResult.UnsupportedArchitecture(
                        instruction.offset, instruction.size, instruction.raw,
                        "status register execution is not available in bounded user-mode analysis",
                    ),
                )
            }
            executed++
            Arm7ExecutionResult.Stepped(instruction, executed, state.copy())
        } catch (error: Arm7MemoryAccessException) {
            state.pc = pc
            Arm7ExecutionResult.InvalidMemory(executed, state.copy(), 0, requireNotNull(error.message))
        }
    }

    fun run(budget: Arm7ExecutionBudget, stop: (Arm7Machine) -> String? = { null }): Arm7ExecutionResult {
        repeat(budget.maxInstructions) {
            stop(this)?.let { return Arm7ExecutionResult.Completed(executed, state.copy(), it) }
            when (val result = step()) {
                is Arm7ExecutionResult.Stepped -> Unit
                else -> return result
            }
        }
        return Arm7ExecutionResult.BudgetExceeded(executed, state.copy(), budget.maxInstructions)
    }

    private fun executeData(instruction: Arm7DataProcessing, pc: Long) {
        val first = instruction.first?.let { operand(it, pc) }
        val second = operand(instruction.second, pc)
        val a = first?.value ?: 0L
        val b = second.value
        val carryIn = if (state.flag(Arm7Flag.C)) 1L else 0L
        val result = when (instruction.operation) {
            Arm7DataOperation.LOGICAL_SHIFT_LEFT -> shift(a, Arm7ShiftType.LOGICAL_LEFT, (b and 0xFF).toInt(), registerSpecified = instruction.second is Arm7RegisterOperand)
            Arm7DataOperation.LOGICAL_SHIFT_RIGHT -> shift(a, Arm7ShiftType.LOGICAL_RIGHT, (b and 0xFF).toInt(), registerSpecified = instruction.second is Arm7RegisterOperand)
            Arm7DataOperation.ARITHMETIC_SHIFT_RIGHT -> shift(a, Arm7ShiftType.ARITHMETIC_RIGHT, (b and 0xFF).toInt(), registerSpecified = instruction.second is Arm7RegisterOperand)
            Arm7DataOperation.ROTATE_RIGHT -> shift(a, Arm7ShiftType.ROTATE_RIGHT, (b and 0xFF).toInt(), registerSpecified = true)
            Arm7DataOperation.ADD -> arithmeticAdd(a, b, 0)
            Arm7DataOperation.ADD_WITH_CARRY -> arithmeticAdd(a, b, carryIn)
            Arm7DataOperation.SUBTRACT -> arithmeticSubtract(a, b, 1)
            Arm7DataOperation.SUBTRACT_WITH_CARRY -> arithmeticSubtract(a, b, carryIn)
            Arm7DataOperation.REVERSE_SUBTRACT -> arithmeticSubtract(b, a, 1)
            Arm7DataOperation.REVERSE_SUBTRACT_WITH_CARRY -> arithmeticSubtract(b, a, carryIn)
            Arm7DataOperation.MOVE -> ValueWithCarry(b, second.carry)
            Arm7DataOperation.MOVE_NOT -> ValueWithCarry(b.inv() and MASK, second.carry)
            Arm7DataOperation.AND -> ValueWithCarry(a and b, second.carry)
            Arm7DataOperation.EXCLUSIVE_OR -> ValueWithCarry(a xor b, second.carry)
            Arm7DataOperation.OR -> ValueWithCarry(a or b, second.carry)
            Arm7DataOperation.BIT_CLEAR -> ValueWithCarry(a and b.inv(), second.carry)
            Arm7DataOperation.MULTIPLY -> ValueWithCarry((a * b) and MASK, null)
        }
        writeResult(instruction.destination, result.value, instruction.instructionSet)
        updateFlags(instruction.flagsWritten, result)
    }

    private fun executeCompare(instruction: Arm7Compare, pc: Long) {
        val first = operand(instruction.first, pc).value
        val second = operand(instruction.second, pc)
        val result = when (instruction.operation) {
            Arm7CompareOperation.COMPARE -> arithmeticSubtract(first, second.value, 1)
            Arm7CompareOperation.COMPARE_NEGATIVE -> arithmeticAdd(first, second.value, 0)
            Arm7CompareOperation.TEST -> ValueWithCarry(first and second.value, second.carry)
            Arm7CompareOperation.TEST_EQUIVALENCE -> ValueWithCarry(first xor second.value, second.carry)
        }
        updateFlags(instruction.flagsWritten, result)
    }

    private fun executeMultiply(instruction: Arm7Multiply) {
        var result = state[instruction.multiplicand] * state[instruction.multiplier]
        instruction.accumulator?.let { result += state[it] }
        state[instruction.destination] = result
        updateNz(instruction.flagsWritten, result)
    }

    private fun executeLongMultiply(instruction: Arm7LongMultiply) {
        val left = if (instruction.signed) state[instruction.multiplicand].toInt().toLong() else state[instruction.multiplicand]
        val right = if (instruction.signed) state[instruction.multiplier].toInt().toLong() else state[instruction.multiplier]
        var result = left * right
        if (instruction.accumulate) {
            result += (state[instruction.destinationHigh] shl 32) or state[instruction.destinationLow]
        }
        state[instruction.destinationLow] = result
        state[instruction.destinationHigh] = result ushr 32
        if (Arm7Flag.N in instruction.flagsWritten) state.setFlag(Arm7Flag.N, result < 0)
        if (Arm7Flag.Z in instruction.flagsWritten) state.setFlag(Arm7Flag.Z, result == 0L)
    }

    private fun executeMemory(instruction: Arm7MemoryTransfer, pc: Long) {
        val resolved = resolveAddress(instruction.address, pc)
        if (instruction.load) {
            val value = when (instruction.width) {
                Arm7MemoryWidth.BYTE -> memory.read8(resolved.effective)
                Arm7MemoryWidth.HALFWORD -> loadHalfword(resolved.effective, instruction.signed, instruction.unalignedPolicy)
                Arm7MemoryWidth.WORD -> loadWord(resolved.effective, instruction.unalignedPolicy)
            }
            if (instruction.valueRegister == Arm7Register.PC) writeResult(Arm7Register.PC, value, instruction.instructionSet)
            else state[instruction.valueRegister] = value
        } else {
            val value = if (instruction.valueRegister == Arm7Register.PC) pc + instruction.valueRegisterPcBias else state[instruction.valueRegister]
            when (instruction.width) {
                Arm7MemoryWidth.BYTE -> memory.write8(resolved.effective, value)
                Arm7MemoryWidth.HALFWORD -> memory.write16(resolved.effective and -2L, value)
                Arm7MemoryWidth.WORD -> memory.write32(resolved.effective and -4L, value)
            }
        }
        resolved.writeBack?.let { (register, value) -> state[register] = value }
    }

    private fun executeSwap(instruction: Arm7Swap) {
        val address = state[instruction.addressRegister]
        val loaded = if (instruction.width == Arm7MemoryWidth.BYTE) memory.read8(address) else loadWord(address, Arm7UnalignedPolicy.ROTATE_WORD_RIGHT_BY_ADDRESS)
        if (instruction.width == Arm7MemoryWidth.BYTE) memory.write8(address, state[instruction.source])
        else memory.write32(address and -4L, state[instruction.source])
        state[instruction.destination] = loaded
    }

    private fun executeStack(instruction: Arm7StackTransfer) {
        if (instruction.load) {
            var address = state[Arm7Register.SP]
            instruction.registers.forEach { register ->
                val value = memory.read32(address and -4L)
                if (register == Arm7Register.PC) {
                    state.instructionSet = if (value and 1L != 0L) Arm7InstructionSet.THUMB else Arm7InstructionSet.ARM
                    state.pc = if (state.instructionSet == Arm7InstructionSet.THUMB) value and -2L else value and -4L
                } else {
                    state[register] = value
                }
                address += 4
            }
            state[Arm7Register.SP] = address
        } else {
            var address = state[Arm7Register.SP] - instruction.registers.size * 4L
            val newSp = address
            instruction.registers.forEach { register ->
                memory.write32(address and -4L, state[register])
                address += 4
            }
            state[Arm7Register.SP] = newSp
        }
    }

    private fun executeBlock(instruction: Arm7BlockTransfer) {
        val count = if (instruction.emptyListArm7Quirk) 16 else instruction.registers.size
        val base = state[instruction.base]
        var address = when (instruction.addressing) {
            Arm7BlockAddressing.INCREMENT_AFTER -> base
            Arm7BlockAddressing.INCREMENT_BEFORE -> base + 4
            Arm7BlockAddressing.DECREMENT_AFTER -> base - count * 4L + 4
            Arm7BlockAddressing.DECREMENT_BEFORE -> base - count * 4L
        }
        instruction.registers.forEach { register ->
            if (instruction.load) {
                val value = memory.read32(address and -4L)
                if (register == Arm7Register.PC) state.pc = value and -4L else state[register] = value
            } else {
                val value = if (register == Arm7Register.PC) state.pc - 4 + instruction.pcStoreBias else state[register]
                memory.write32(address and -4L, value)
            }
            address += 4
        }
        if (instruction.writeBack) {
            state[instruction.base] = when (instruction.addressing) {
                Arm7BlockAddressing.INCREMENT_AFTER, Arm7BlockAddressing.INCREMENT_BEFORE -> base + count * 4L
                Arm7BlockAddressing.DECREMENT_AFTER, Arm7BlockAddressing.DECREMENT_BEFORE -> base - count * 4L
            }
        }
    }

    private fun executeBranch(instruction: Arm7Branch) {
        state.pc = Arm7Memory.ROM_START + instruction.target
        if (instruction.link) state[Arm7Register.LR] = Arm7Memory.ROM_START + requireNotNull(instruction.returnAddress)
        if (instruction.exchange) state.instructionSet = if (state.pc and 1L != 0L) Arm7InstructionSet.THUMB else Arm7InstructionSet.ARM
        state.pc = if (state.instructionSet == Arm7InstructionSet.THUMB) state.pc and -2L else state.pc and -4L
    }

    private fun executeBranchRegister(instruction: Arm7BranchRegister) {
        val target = state[instruction.targetRegister]
        if (instruction.link) state[Arm7Register.LR] = state.pc or 1L
        if (instruction.exchange) state.instructionSet = if (target and 1L != 0L) Arm7InstructionSet.THUMB else Arm7InstructionSet.ARM
        state.pc = if (state.instructionSet == Arm7InstructionSet.THUMB) target and -2L else target and -4L
    }

    private fun operand(operand: Arm7Operand, pc: Long): ValueWithCarry = when (operand) {
        is Arm7Immediate -> ValueWithCarry(operand.value and MASK, null)
        is Arm7RotatedImmediate -> ValueWithCarry(
            operand.value,
            when {
                operand.rotateRight != 0 -> operand.value and 0x8000_0000L != 0L
                operand.carryInWhenUnrotated -> state.flag(Arm7Flag.C)
                else -> null
            },
        )
        is Arm7RegisterOperand -> ValueWithCarry(
            if (operand.register == Arm7Register.PC) alignDown(pc + operand.pcBias, operand.alignDownTo) else state[operand.register],
            null,
        )
        is Arm7ShiftedRegister -> {
            val value = if (operand.register == Arm7Register.PC) pc + operand.pcBias else state[operand.register]
            val amount = when (val shiftAmount = operand.amount) {
                is Arm7ShiftAmount.Immediate -> shiftAmount.value
                is Arm7ShiftAmount.Register -> (state[shiftAmount.register] and 0xFF).toInt()
            }
            shift(value, operand.type, amount, operand.amount is Arm7ShiftAmount.Register)
        }
    }

    private fun resolveAddress(address: Arm7Address, pc: Long): ResolvedAddress = when (address) {
        is Arm7Address.PcRelative -> ResolvedAddress(Arm7Memory.ROM_START + address.resolvedAddress, null)
        is Arm7Address.RegisterOffset -> {
            val base = if (address.base == Arm7Register.PC) alignDown(pc + address.pcBias, address.alignBaseTo) else state[address.base]
            val offset = address.index?.let { state[it] } ?: address.immediate.toLong()
            indexedAddress(address.base, base, offset, address.add, address.preIndexed, address.writeBack)
        }
        is Arm7Address.ShiftedRegisterOffset -> {
            val base = if (address.base == Arm7Register.PC) pc + address.basePcBias else state[address.base]
            val offset = operand(address.index, pc).value
            indexedAddress(address.base, base, offset, address.add, address.preIndexed, address.writeBack)
        }
    }

    private fun indexedAddress(
        baseRegister: Arm7Register,
        base: Long,
        offset: Long,
        add: Boolean,
        preIndexed: Boolean,
        writeBack: Boolean,
    ): ResolvedAddress {
        val adjusted = if (add) base + offset else base - offset
        return ResolvedAddress(
            effective = (if (preIndexed) adjusted else base) and MASK,
            writeBack = if (writeBack) baseRegister to (adjusted and MASK) else null,
        )
    }

    private fun loadWord(address: Long, policy: Arm7UnalignedPolicy): Long {
        val aligned = memory.read32(address and -4L)
        return if (policy == Arm7UnalignedPolicy.ROTATE_WORD_RIGHT_BY_ADDRESS) {
            rotateRight(aligned, ((address and 3L) * 8).toInt())
        } else {
            aligned
        }
    }

    private fun loadHalfword(address: Long, signed: Boolean, policy: Arm7UnalignedPolicy): Long {
        if (signed && policy == Arm7UnalignedPolicy.SIGNED_BYTE_WHEN_ODD && address and 1L != 0L) {
            return signExtend(memory.read8(address), 8)
        }
        var value = memory.read16(address and -2L)
        if (!signed && policy == Arm7UnalignedPolicy.ROTATE_HALFWORD_RIGHT_8 && address and 1L != 0L) {
            value = ((value ushr 8) or (value shl 24)) and MASK
        } else if (signed) {
            value = signExtend(value, 16)
        }
        return value
    }

    private fun shift(value: Long, type: Arm7ShiftType, amount: Int, registerSpecified: Boolean): ValueWithCarry {
        val source = value and MASK
        if (amount == 0 && type != Arm7ShiftType.ROTATE_RIGHT_EXTEND) return ValueWithCarry(source, if (registerSpecified) null else state.flag(Arm7Flag.C))
        return when (type) {
            Arm7ShiftType.LOGICAL_LEFT -> when {
                amount < 32 -> ValueWithCarry((source shl amount) and MASK, source and (1L shl (32 - amount)) != 0L)
                amount == 32 -> ValueWithCarry(0, source and 1L != 0L)
                else -> ValueWithCarry(0, false)
            }
            Arm7ShiftType.LOGICAL_RIGHT -> when {
                amount < 32 -> ValueWithCarry(source ushr amount, source and (1L shl (amount - 1)) != 0L)
                amount == 32 -> ValueWithCarry(0, source and 0x8000_0000L != 0L)
                else -> ValueWithCarry(0, false)
            }
            Arm7ShiftType.ARITHMETIC_RIGHT -> {
                val signed = source.toInt()
                if (amount >= 32) ValueWithCarry(if (signed < 0) MASK else 0, signed < 0)
                else ValueWithCarry((signed shr amount).toLong() and MASK, source and (1L shl (amount - 1)) != 0L)
            }
            Arm7ShiftType.ROTATE_RIGHT -> {
                val rotation = amount and 31
                if (rotation == 0) ValueWithCarry(source, source and 0x8000_0000L != 0L)
                else rotateRight(source, rotation).let { ValueWithCarry(it, it and 0x8000_0000L != 0L) }
            }
            Arm7ShiftType.ROTATE_RIGHT_EXTEND -> {
                val result = (if (state.flag(Arm7Flag.C)) 0x8000_0000L else 0L) or (source ushr 1)
                ValueWithCarry(result, source and 1L != 0L)
            }
        }
    }

    private fun arithmeticAdd(a: Long, b: Long, carry: Long): ValueWithCarry {
        val sum = (a and MASK) + (b and MASK) + carry
        val result = sum and MASK
        val overflow = ((a xor result) and (b xor result) and 0x8000_0000L) != 0L
        return ValueWithCarry(result, sum > MASK, overflow)
    }

    private fun arithmeticSubtract(a: Long, b: Long, carry: Long): ValueWithCarry {
        val borrow = 1L - carry
        val unsignedA = a and MASK
        val subtrahend = (b and MASK) + borrow
        val result = (unsignedA - subtrahend) and MASK
        val overflow = ((a xor b) and (a xor result) and 0x8000_0000L) != 0L
        return ValueWithCarry(result, unsignedA >= subtrahend, overflow)
    }

    private fun updateFlags(flags: Set<Arm7Flag>, result: ValueWithCarry) {
        updateNz(flags, result.value)
        if (Arm7Flag.C in flags && result.carry != null) state.setFlag(Arm7Flag.C, result.carry)
        if (Arm7Flag.V in flags && result.overflow != null) state.setFlag(Arm7Flag.V, result.overflow)
    }

    private fun updateNz(flags: Set<Arm7Flag>, value: Long) {
        if (Arm7Flag.N in flags) state.setFlag(Arm7Flag.N, value and 0x8000_0000L != 0L)
        if (Arm7Flag.Z in flags) state.setFlag(Arm7Flag.Z, value and MASK == 0L)
    }

    private fun writeResult(register: Arm7Register, value: Long, instructionSet: Arm7InstructionSet) {
        if (register != Arm7Register.PC) {
            state[register] = value
            return
        }
        state.pc = if (instructionSet == Arm7InstructionSet.THUMB) value and -2L else value and -4L
    }

    private fun conditionPass(condition: Arm7Condition): Boolean = when (condition) {
        Arm7Condition.EQUAL -> state.flag(Arm7Flag.Z)
        Arm7Condition.NOT_EQUAL -> !state.flag(Arm7Flag.Z)
        Arm7Condition.CARRY_SET -> state.flag(Arm7Flag.C)
        Arm7Condition.CARRY_CLEAR -> !state.flag(Arm7Flag.C)
        Arm7Condition.NEGATIVE -> state.flag(Arm7Flag.N)
        Arm7Condition.POSITIVE_OR_ZERO -> !state.flag(Arm7Flag.N)
        Arm7Condition.OVERFLOW -> state.flag(Arm7Flag.V)
        Arm7Condition.NO_OVERFLOW -> !state.flag(Arm7Flag.V)
        Arm7Condition.UNSIGNED_HIGHER -> state.flag(Arm7Flag.C) && !state.flag(Arm7Flag.Z)
        Arm7Condition.UNSIGNED_LOWER_OR_SAME -> !state.flag(Arm7Flag.C) || state.flag(Arm7Flag.Z)
        Arm7Condition.SIGNED_GREATER_OR_EQUAL -> state.flag(Arm7Flag.N) == state.flag(Arm7Flag.V)
        Arm7Condition.SIGNED_LESS -> state.flag(Arm7Flag.N) != state.flag(Arm7Flag.V)
        Arm7Condition.SIGNED_GREATER -> !state.flag(Arm7Flag.Z) && state.flag(Arm7Flag.N) == state.flag(Arm7Flag.V)
        Arm7Condition.SIGNED_LESS_OR_EQUAL -> state.flag(Arm7Flag.Z) || state.flag(Arm7Flag.N) != state.flag(Arm7Flag.V)
        Arm7Condition.ALWAYS -> true
    }

    private fun alignDown(value: Long, alignment: Int): Long = value and -alignment.toLong()
    private fun rotateRight(value: Long, amount: Int): Long = Integer.rotateRight(value.toInt(), amount).toLong() and MASK
    private fun signExtend(value: Long, bits: Int): Long = ((value shl (64 - bits)) shr (64 - bits)) and MASK

    private data class ValueWithCarry(val value: Long, val carry: Boolean?, val overflow: Boolean? = null)
    private data class ResolvedAddress(val effective: Long, val writeBack: Pair<Arm7Register, Long>?)

    private companion object { const val MASK = 0xFFFF_FFFFL }
}
