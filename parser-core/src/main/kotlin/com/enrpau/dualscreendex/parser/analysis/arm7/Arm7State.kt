package com.enrpau.dualscreendex.parser.analysis.arm7

class Arm7State(
    instructionSet: Arm7InstructionSet,
    pc: Long,
) {
    private val registers = LongArray(16)
    private val flags = BooleanArray(Arm7Flag.entries.size)

    var instructionSet: Arm7InstructionSet = instructionSet
    var pc: Long
        get() = this[Arm7Register.PC]
        set(value) { this[Arm7Register.PC] = value }

    init { this.pc = pc }

    operator fun get(register: Arm7Register): Long = registers[register.index]

    operator fun set(register: Arm7Register, value: Long) {
        registers[register.index] = value and 0xFFFF_FFFFL
    }

    fun flag(flag: Arm7Flag): Boolean = flags[flag.ordinal]

    fun setFlag(flag: Arm7Flag, value: Boolean) {
        flags[flag.ordinal] = value
    }

    fun copy(): Arm7State = Arm7State(instructionSet, pc).also { result ->
        Arm7Register.entries.forEach { result[it] = this[it] }
        Arm7Flag.entries.forEach { result.setFlag(it, flag(it)) }
    }

    fun canonicalSummary(): String = buildString {
        append(instructionSet.name).append('|')
        Arm7Register.entries.forEach { append(it.name).append('=').append(this@Arm7State[it].toString(16)).append(';') }
        Arm7Flag.entries.forEach { append(it.name).append('=').append(if (flag(it)) '1' else '0') }
    }
}
