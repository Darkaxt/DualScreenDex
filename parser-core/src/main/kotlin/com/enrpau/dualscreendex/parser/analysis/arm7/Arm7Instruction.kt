package com.enrpau.dualscreendex.parser.analysis.arm7

sealed interface Arm7Instruction {
    val offset: Int
    val size: Int
    val raw: Long
    val instructionSet: Arm7InstructionSet
    val condition: Arm7Condition
    val registersRead: Set<Arm7Register>
    val registersWritten: Set<Arm7Register>
    val flagsRead: Set<Arm7Flag>
    val flagsWritten: Set<Arm7Flag>
    val memoryEffects: List<Arm7MemoryAccess>
    val controlEffect: Arm7ControlEffect
}

/** Marker with no implementations: decoders must return a typed failure instead of an unknown node. */
sealed interface Arm7Unknown : Arm7Instruction

enum class Arm7DataOperation {
    LOGICAL_SHIFT_LEFT, LOGICAL_SHIFT_RIGHT, ARITHMETIC_SHIFT_RIGHT, ROTATE_RIGHT,
    ADD, ADD_WITH_CARRY, SUBTRACT, SUBTRACT_WITH_CARRY, REVERSE_SUBTRACT,
    MOVE, MOVE_NOT, AND, EXCLUSIVE_OR, OR, BIT_CLEAR, MULTIPLY,
}

data class Arm7DataProcessing(
    override val offset: Int,
    override val size: Int,
    override val raw: Long,
    override val instructionSet: Arm7InstructionSet,
    override val condition: Arm7Condition = Arm7Condition.ALWAYS,
    val operation: Arm7DataOperation,
    val destination: Arm7Register,
    val first: Arm7Operand?,
    val second: Arm7Operand,
    override val flagsWritten: Set<Arm7Flag> = emptySet(),
    val additionalFlagsRead: Set<Arm7Flag> = emptySet(),
) : Arm7Instruction {
    override val registersRead: Set<Arm7Register> =
        (first?.registersRead ?: emptySet()) + second.registersRead
    override val registersWritten: Set<Arm7Register> = setOf(destination)
    override val flagsRead: Set<Arm7Flag> = condition.flagsRead +
        (first?.flagsRead ?: emptySet()) + second.flagsRead + additionalFlagsRead
    override val memoryEffects: List<Arm7MemoryAccess> = emptyList()
    override val controlEffect: Arm7ControlEffect = if (destination == Arm7Register.PC) {
        Arm7ControlEffect.ProgramCounterWrite(interworking = false)
    } else {
        Arm7ControlEffect.Sequential
    }
}

enum class Arm7CompareOperation { COMPARE, COMPARE_NEGATIVE, TEST, TEST_EQUIVALENCE }

data class Arm7Compare(
    override val offset: Int,
    override val size: Int,
    override val raw: Long,
    override val instructionSet: Arm7InstructionSet,
    override val condition: Arm7Condition = Arm7Condition.ALWAYS,
    val operation: Arm7CompareOperation,
    val first: Arm7Operand,
    val second: Arm7Operand,
    override val flagsWritten: Set<Arm7Flag>,
) : Arm7Instruction {
    override val registersRead: Set<Arm7Register> = first.registersRead + second.registersRead
    override val registersWritten: Set<Arm7Register> = emptySet()
    override val flagsRead: Set<Arm7Flag> = condition.flagsRead + first.flagsRead + second.flagsRead
    override val memoryEffects: List<Arm7MemoryAccess> = emptyList()
    override val controlEffect: Arm7ControlEffect = Arm7ControlEffect.Sequential
}

data class Arm7MemoryTransfer(
    override val offset: Int,
    override val size: Int,
    override val raw: Long,
    override val instructionSet: Arm7InstructionSet,
    override val condition: Arm7Condition = Arm7Condition.ALWAYS,
    val load: Boolean,
    val valueRegister: Arm7Register,
    val address: Arm7Address,
    val width: Arm7MemoryWidth,
    val signed: Boolean = false,
    val unalignedPolicy: Arm7UnalignedPolicy,
) : Arm7Instruction {
    override val registersRead: Set<Arm7Register> = address.registersRead +
        if (load) emptySet() else setOf(valueRegister)
    override val registersWritten: Set<Arm7Register> = buildSet {
        if (load) add(valueRegister)
        when (address) {
            is Arm7Address.RegisterOffset -> if (address.writeBack) add(address.base)
            is Arm7Address.ShiftedRegisterOffset -> if (address.writeBack) add(address.base)
            is Arm7Address.PcRelative -> Unit
        }
    }
    override val flagsRead: Set<Arm7Flag> = condition.flagsRead
    override val flagsWritten: Set<Arm7Flag> = emptySet()
    override val memoryEffects: List<Arm7MemoryAccess> = listOf(
        Arm7MemoryAccess(
            direction = if (load) Arm7MemoryDirection.READ else Arm7MemoryDirection.WRITE,
            width = width,
            address = address,
            signed = signed,
            unalignedPolicy = unalignedPolicy,
        ),
    )
    override val controlEffect: Arm7ControlEffect = if (load && valueRegister == Arm7Register.PC) {
        Arm7ControlEffect.ProgramCounterWrite(interworking = false)
    } else {
        Arm7ControlEffect.Sequential
    }
}

enum class Arm7BlockAddressing { INCREMENT_AFTER, INCREMENT_BEFORE, DECREMENT_AFTER, DECREMENT_BEFORE }

data class Arm7BlockTransfer(
    override val offset: Int,
    override val size: Int,
    override val raw: Long,
    override val instructionSet: Arm7InstructionSet,
    override val condition: Arm7Condition = Arm7Condition.ALWAYS,
    val load: Boolean,
    val base: Arm7Register,
    val registers: List<Arm7Register>,
    val addressing: Arm7BlockAddressing,
    val writeBack: Boolean,
    val userModeRegisters: Boolean = false,
    val emptyListArm7Quirk: Boolean = false,
    /** True only for encodings whose PC load can select ARM/Thumb from bit zero. */
    val pcInterworking: Boolean = false,
) : Arm7Instruction {
    override val registersRead: Set<Arm7Register> = setOf(base) +
        if (load) emptySet() else registers.toSet()
    override val registersWritten: Set<Arm7Register> = buildSet {
        if (load) addAll(registers)
        if (writeBack) add(base)
    }
    override val flagsRead: Set<Arm7Flag> = condition.flagsRead
    override val flagsWritten: Set<Arm7Flag> = emptySet()
    override val memoryEffects: List<Arm7MemoryAccess> = listOf(
        Arm7MemoryAccess(
            direction = if (load) Arm7MemoryDirection.READ else Arm7MemoryDirection.WRITE,
            width = Arm7MemoryWidth.WORD,
            address = Arm7Address.RegisterOffset(base),
            signed = false,
            unalignedPolicy = Arm7UnalignedPolicy.ALIGN_DOWN,
            registerCount = if (emptyListArm7Quirk) 1 else registers.size,
        ),
    )
    override val controlEffect: Arm7ControlEffect = if (load && Arm7Register.PC in registers) {
        Arm7ControlEffect.ProgramCounterWrite(interworking = pcInterworking)
    } else {
        Arm7ControlEffect.Sequential
    }
}

data class Arm7StackTransfer(
    override val offset: Int,
    override val size: Int,
    override val raw: Long,
    override val instructionSet: Arm7InstructionSet,
    override val condition: Arm7Condition = Arm7Condition.ALWAYS,
    val load: Boolean,
    val registers: List<Arm7Register>,
) : Arm7Instruction {
    init { require(registers.isNotEmpty()) }

    override val registersRead: Set<Arm7Register> = setOf(Arm7Register.SP) +
        if (load) emptySet() else registers.toSet()
    override val registersWritten: Set<Arm7Register> = buildSet {
        add(Arm7Register.SP)
        if (load) addAll(registers)
    }
    override val flagsRead: Set<Arm7Flag> = condition.flagsRead
    override val flagsWritten: Set<Arm7Flag> = emptySet()
    override val memoryEffects: List<Arm7MemoryAccess> = listOf(
        Arm7MemoryAccess(
            direction = if (load) Arm7MemoryDirection.READ else Arm7MemoryDirection.WRITE,
            width = Arm7MemoryWidth.WORD,
            address = Arm7Address.RegisterOffset(Arm7Register.SP),
            signed = false,
            unalignedPolicy = Arm7UnalignedPolicy.ALIGN_DOWN,
            registerCount = registers.size,
        ),
    )
    override val controlEffect: Arm7ControlEffect = if (load && Arm7Register.PC in registers) {
        Arm7ControlEffect.Return(interworking = true)
    } else {
        Arm7ControlEffect.Sequential
    }
}

data class Arm7Branch(
    override val offset: Int,
    override val size: Int,
    override val raw: Long,
    override val instructionSet: Arm7InstructionSet,
    override val condition: Arm7Condition = Arm7Condition.ALWAYS,
    val target: Long,
    val link: Boolean,
    val exchange: Boolean,
    val returnAddress: Long? = null,
) : Arm7Instruction {
    init { require(link == (returnAddress != null)) }

    override val registersRead: Set<Arm7Register> = emptySet()
    override val registersWritten: Set<Arm7Register> = buildSet {
        add(Arm7Register.PC)
        if (link) add(Arm7Register.LR)
    }
    override val flagsRead: Set<Arm7Flag> = condition.flagsRead
    override val flagsWritten: Set<Arm7Flag> = emptySet()
    override val memoryEffects: List<Arm7MemoryAccess> = emptyList()
    override val controlEffect: Arm7ControlEffect = if (link) {
        Arm7ControlEffect.Call(target, requireNotNull(returnAddress), exchange)
    } else {
        Arm7ControlEffect.DirectBranch(target, condition != Arm7Condition.ALWAYS)
    }
}

data class Arm7BranchRegister(
    override val offset: Int,
    override val size: Int,
    override val raw: Long,
    override val instructionSet: Arm7InstructionSet,
    override val condition: Arm7Condition = Arm7Condition.ALWAYS,
    val targetRegister: Arm7Register,
    val link: Boolean,
    val exchange: Boolean,
) : Arm7Instruction {
    override val registersRead: Set<Arm7Register> = setOf(targetRegister)
    override val registersWritten: Set<Arm7Register> = buildSet {
        add(Arm7Register.PC)
        if (link) add(Arm7Register.LR)
    }
    override val flagsRead: Set<Arm7Flag> = condition.flagsRead
    override val flagsWritten: Set<Arm7Flag> = emptySet()
    override val memoryEffects: List<Arm7MemoryAccess> = emptyList()
    override val controlEffect: Arm7ControlEffect = when {
        !link && targetRegister == Arm7Register.LR -> Arm7ControlEffect.Return(interworking = exchange)
        else -> Arm7ControlEffect.IndirectBranch(targetRegister, interworking = exchange)
    }
}

data class Arm7SoftwareInterrupt(
    override val offset: Int,
    override val size: Int,
    override val raw: Long,
    override val instructionSet: Arm7InstructionSet,
    override val condition: Arm7Condition = Arm7Condition.ALWAYS,
    val comment: Int,
) : Arm7Instruction {
    override val registersRead: Set<Arm7Register> = emptySet()
    override val registersWritten: Set<Arm7Register> = setOf(Arm7Register.LR, Arm7Register.PC)
    override val flagsRead: Set<Arm7Flag> = condition.flagsRead
    override val flagsWritten: Set<Arm7Flag> = emptySet()
    override val memoryEffects: List<Arm7MemoryAccess> = emptyList()
    override val controlEffect: Arm7ControlEffect = Arm7ControlEffect.SupervisorCall(comment)
}
