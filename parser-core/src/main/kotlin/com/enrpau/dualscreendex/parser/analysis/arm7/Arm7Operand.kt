package com.enrpau.dualscreendex.parser.analysis.arm7

enum class Arm7InstructionSet { ARM, THUMB }

enum class Arm7Register(val index: Int) {
    R0(0), R1(1), R2(2), R3(3), R4(4), R5(5), R6(6), R7(7),
    R8(8), R9(9), R10(10), R11(11), R12(12), SP(13), LR(14), PC(15);

    companion object {
        fun fromIndex(index: Int): Arm7Register = entries.firstOrNull { it.index == index }
            ?: throw IllegalArgumentException("ARM register index must be in 0..15: $index")
    }
}

enum class Arm7Flag { N, Z, C, V }

enum class Arm7Condition(val flagsRead: Set<Arm7Flag>) {
    EQUAL(setOf(Arm7Flag.Z)),
    NOT_EQUAL(setOf(Arm7Flag.Z)),
    CARRY_SET(setOf(Arm7Flag.C)),
    CARRY_CLEAR(setOf(Arm7Flag.C)),
    NEGATIVE(setOf(Arm7Flag.N)),
    POSITIVE_OR_ZERO(setOf(Arm7Flag.N)),
    OVERFLOW(setOf(Arm7Flag.V)),
    NO_OVERFLOW(setOf(Arm7Flag.V)),
    UNSIGNED_HIGHER(setOf(Arm7Flag.C, Arm7Flag.Z)),
    UNSIGNED_LOWER_OR_SAME(setOf(Arm7Flag.C, Arm7Flag.Z)),
    SIGNED_GREATER_OR_EQUAL(setOf(Arm7Flag.N, Arm7Flag.V)),
    SIGNED_LESS(setOf(Arm7Flag.N, Arm7Flag.V)),
    SIGNED_GREATER(setOf(Arm7Flag.N, Arm7Flag.Z, Arm7Flag.V)),
    SIGNED_LESS_OR_EQUAL(setOf(Arm7Flag.N, Arm7Flag.Z, Arm7Flag.V)),
    ALWAYS(emptySet()),
}

sealed interface Arm7Operand {
    val registersRead: Set<Arm7Register>
    val flagsRead: Set<Arm7Flag> get() = emptySet()
}

data class Arm7RegisterOperand(
    val register: Arm7Register,
    /** Architectural pipeline bias applied when this operand reads PC. */
    val pcBias: Int = 0,
    /** Alignment applied after [pcBias], normally 1 or 4. */
    val alignDownTo: Int = 1,
) : Arm7Operand {
    init {
        require(pcBias == 0 || register == Arm7Register.PC)
        require(alignDownTo > 0 && alignDownTo.countOneBits() == 1)
    }

    override val registersRead: Set<Arm7Register> = setOf(register)
}

data class Arm7Immediate(val value: Long) : Arm7Operand {
    constructor(value: Int) : this(value.toLong())
    override val registersRead: Set<Arm7Register> = emptySet()
}

enum class Arm7ShiftType { LOGICAL_LEFT, LOGICAL_RIGHT, ARITHMETIC_RIGHT, ROTATE_RIGHT, ROTATE_RIGHT_EXTEND }

sealed interface Arm7ShiftAmount {
    val registersRead: Set<Arm7Register>

    data class Immediate(val value: Int) : Arm7ShiftAmount {
        init { require(value in 0..32) }
        override val registersRead: Set<Arm7Register> = emptySet()
    }

    data class Register(val register: Arm7Register) : Arm7ShiftAmount {
        override val registersRead: Set<Arm7Register> = setOf(register)
    }
}

data class Arm7ShiftedRegister(
    val register: Arm7Register,
    val type: Arm7ShiftType,
    val amount: Arm7ShiftAmount,
    val carryInWhenZero: Boolean = false,
) : Arm7Operand {
    override val registersRead: Set<Arm7Register> = setOf(register) + amount.registersRead
    override val flagsRead: Set<Arm7Flag> = if (carryInWhenZero) setOf(Arm7Flag.C) else emptySet()
}

sealed interface Arm7Address {
    val registersRead: Set<Arm7Register>

    data class RegisterOffset(
        val base: Arm7Register,
        val index: Arm7Register? = null,
        val immediate: Int = 0,
        val add: Boolean = true,
        val preIndexed: Boolean = true,
        val writeBack: Boolean = false,
        val pcBias: Int = 0,
        val alignBaseTo: Int = 1,
    ) : Arm7Address {
        init {
            require(pcBias == 0 || base == Arm7Register.PC)
            require(alignBaseTo > 0 && alignBaseTo.countOneBits() == 1)
        }

        override val registersRead: Set<Arm7Register> = buildSet {
            add(base)
            index?.let(::add)
        }
    }

    data class ShiftedRegisterOffset(
        val base: Arm7Register,
        val index: Arm7ShiftedRegister,
        val add: Boolean,
        val preIndexed: Boolean,
        val writeBack: Boolean,
    ) : Arm7Address {
        override val registersRead: Set<Arm7Register> = setOf(base) + index.registersRead
    }

    data class PcRelative(
        val immediate: Int,
        val pcBias: Int,
        val alignBaseTo: Int,
        val resolvedAddress: Long,
    ) : Arm7Address {
        override val registersRead: Set<Arm7Register> = setOf(Arm7Register.PC)
    }
}

enum class Arm7MemoryDirection { READ, WRITE }
enum class Arm7MemoryWidth(val bytes: Int) { BYTE(1), HALFWORD(2), WORD(4) }

enum class Arm7UnalignedPolicy {
    BYTE_ADDRESSABLE,
    ALIGN_DOWN,
    ROTATE_WORD_RIGHT_BY_ADDRESS,
    ROTATE_HALFWORD_RIGHT_8,
    SIGNED_BYTE_WHEN_ODD,
}

data class Arm7MemoryAccess(
    val direction: Arm7MemoryDirection,
    val width: Arm7MemoryWidth,
    val address: Arm7Address,
    val signed: Boolean,
    val unalignedPolicy: Arm7UnalignedPolicy,
    val registerCount: Int = 1,
)

sealed interface Arm7ControlEffect {
    data object Sequential : Arm7ControlEffect
    data class DirectBranch(val target: Long, val conditional: Boolean) : Arm7ControlEffect
    data class Call(val target: Long, val returnAddress: Long, val exchange: Boolean) : Arm7ControlEffect
    data class IndirectBranch(val register: Arm7Register, val interworking: Boolean) : Arm7ControlEffect
    data class Return(val interworking: Boolean) : Arm7ControlEffect
    data class ProgramCounterWrite(val interworking: Boolean) : Arm7ControlEffect
    data class SupervisorCall(val comment: Int) : Arm7ControlEffect
}
