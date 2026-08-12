package com.enrpau.dualscreendex.parser.analysis.arm7

class Arm7State(
    instructionSet: Arm7InstructionSet,
    pc: Long,
) {
    private val registers = LongArray(16)
    private val bankUsr = LongArray(7)
    private val bankFiq = LongArray(7)
    private val bankIrq = LongArray(2)
    private val bankSvc = LongArray(2)
    private val bankAbt = LongArray(2)
    private val bankUnd = LongArray(2)
    private val savedStatus = LongArray(Arm7Mode.entries.size)
    private var currentMode = Arm7Mode.SYSTEM
    private var cpsrValue = currentMode.bits or if (instructionSet == Arm7InstructionSet.THUMB) THUMB_BIT else 0L

    var instructionSet: Arm7InstructionSet
        get() = if (cpsrValue and THUMB_BIT != 0L) Arm7InstructionSet.THUMB else Arm7InstructionSet.ARM
        set(value) {
            cpsrValue = if (value == Arm7InstructionSet.THUMB) cpsrValue or THUMB_BIT else cpsrValue and THUMB_BIT.inv()
        }

    var pc: Long
        get() = this[Arm7Register.PC]
        set(value) { this[Arm7Register.PC] = value }

    init { this.pc = pc }

    operator fun get(register: Arm7Register): Long = registers[register.index]

    operator fun set(register: Arm7Register, value: Long) {
        registers[register.index] = value and MASK
    }

    fun userRegister(register: Arm7Register): Long = when (register.index) {
        in 0..7, 15 -> this[register]
        else -> bankUsr[register.index - 8]
    }

    fun setUserRegister(register: Arm7Register, value: Long) {
        when (register.index) {
            in 0..7, 15 -> this[register] = value
            else -> bankUsr[register.index - 8] = value and MASK
        }
    }

    fun flag(flag: Arm7Flag): Boolean = cpsrValue and flag.mask != 0L

    fun setFlag(flag: Arm7Flag, value: Boolean) {
        cpsrValue = if (value) cpsrValue or flag.mask else cpsrValue and flag.mask.inv()
    }

    fun cpsr(): Long = cpsrValue and MASK

    fun spsr(): Long = if (currentMode.hasSavedStatus) savedStatus[currentMode.ordinal] else cpsr()

    fun writeCpsr(value: Long, byteFieldMask: Int) {
        var mask = statusMask(byteFieldMask)
        if (currentMode == Arm7Mode.USER) mask = mask and FLAGS_BYTE
        applyCpsr((cpsrValue and mask.inv()) or (value and mask))
    }

    fun writeSpsr(value: Long, byteFieldMask: Int) {
        if (!currentMode.hasSavedStatus) return
        val mask = statusMask(byteFieldMask)
        savedStatus[currentMode.ordinal] = (savedStatus[currentMode.ordinal] and mask.inv()) or (value and mask)
    }

    fun restoreCpsrFromSpsr() {
        if (currentMode.hasSavedStatus) applyCpsr(spsr())
    }

    fun copy(): Arm7State = Arm7State(instructionSet, pc).also { result ->
        registers.copyInto(result.registers)
        bankUsr.copyInto(result.bankUsr)
        bankFiq.copyInto(result.bankFiq)
        bankIrq.copyInto(result.bankIrq)
        bankSvc.copyInto(result.bankSvc)
        bankAbt.copyInto(result.bankAbt)
        bankUnd.copyInto(result.bankUnd)
        savedStatus.copyInto(result.savedStatus)
        result.currentMode = currentMode
        result.cpsrValue = cpsrValue
    }

    fun canonicalSummary(): String = buildString {
        append(instructionSet.name).append('|')
        append("CPSR=").append(cpsr().toString(16)).append(';')
        Arm7Register.entries.forEach { append(it.name).append('=').append(this@Arm7State[it].toString(16)).append(';') }
        Arm7Mode.entries.filter { it.hasSavedStatus }.forEach {
            append("SPSR_").append(it.name).append('=').append(savedStatus[it.ordinal].toString(16)).append(';')
        }
    }

    private fun applyCpsr(value: Long) {
        val requestedMode = Arm7Mode.fromBits(value and MODE_MASK)
        if (requestedMode != null && requestedMode != currentMode) switchMode(requestedMode)
        cpsrValue = (value and MODE_MASK.inv()) or currentMode.bits
    }

    private fun switchMode(next: Arm7Mode) {
        saveBank(currentMode)
        loadBank(next)
        currentMode = next
    }

    private fun saveBank(mode: Arm7Mode) {
        when (mode) {
            Arm7Mode.FIQ -> copyRegistersTo(bankFiq, 8)
            Arm7Mode.IRQ -> { copyRegistersTo(bankIrq, 13); saveUsrHigh() }
            Arm7Mode.SUPERVISOR -> { copyRegistersTo(bankSvc, 13); saveUsrHigh() }
            Arm7Mode.ABORT -> { copyRegistersTo(bankAbt, 13); saveUsrHigh() }
            Arm7Mode.UNDEFINED -> { copyRegistersTo(bankUnd, 13); saveUsrHigh() }
            Arm7Mode.USER, Arm7Mode.SYSTEM -> copyRegistersTo(bankUsr, 8)
        }
    }

    private fun loadBank(mode: Arm7Mode) {
        when (mode) {
            Arm7Mode.FIQ -> copyRegistersFrom(bankFiq, 8)
            Arm7Mode.IRQ -> { loadUsrHigh(); copyRegistersFrom(bankIrq, 13) }
            Arm7Mode.SUPERVISOR -> { loadUsrHigh(); copyRegistersFrom(bankSvc, 13) }
            Arm7Mode.ABORT -> { loadUsrHigh(); copyRegistersFrom(bankAbt, 13) }
            Arm7Mode.UNDEFINED -> { loadUsrHigh(); copyRegistersFrom(bankUnd, 13) }
            Arm7Mode.USER, Arm7Mode.SYSTEM -> copyRegistersFrom(bankUsr, 8)
        }
    }

    private fun saveUsrHigh() {
        for (index in 0 until 5) bankUsr[index] = registers[8 + index]
    }

    private fun loadUsrHigh() {
        for (index in 0 until 5) registers[8 + index] = bankUsr[index]
    }

    private fun copyRegistersTo(bank: LongArray, firstRegister: Int) {
        bank.indices.forEach { bank[it] = registers[firstRegister + it] }
    }

    private fun copyRegistersFrom(bank: LongArray, firstRegister: Int) {
        bank.indices.forEach { registers[firstRegister + it] = bank[it] }
    }

    private fun statusMask(byteFieldMask: Int): Long =
        (if (byteFieldMask and 8 != 0) 0xFF00_0000L else 0L) or
            (if (byteFieldMask and 4 != 0) 0x00FF_0000L else 0L) or
            (if (byteFieldMask and 2 != 0) 0x0000_FF00L else 0L) or
            (if (byteFieldMask and 1 != 0) 0x0000_00FFL else 0L)

    private val Arm7Flag.mask: Long
        get() = when (this) {
            Arm7Flag.N -> 0x8000_0000L
            Arm7Flag.Z -> 0x4000_0000L
            Arm7Flag.C -> 0x2000_0000L
            Arm7Flag.V -> 0x1000_0000L
        }

    private companion object {
        const val MASK = 0xFFFF_FFFFL
        const val FLAGS_BYTE = 0xFF00_0000L
        const val MODE_MASK = 0x1FL
        const val THUMB_BIT = 0x20L
    }
}

private enum class Arm7Mode(val bits: Long, val hasSavedStatus: Boolean) {
    USER(0x10, false),
    FIQ(0x11, true),
    IRQ(0x12, true),
    SUPERVISOR(0x13, true),
    ABORT(0x17, true),
    UNDEFINED(0x1B, true),
    SYSTEM(0x1F, false),
    ;

    companion object {
        fun fromBits(bits: Long): Arm7Mode? = entries.firstOrNull { it.bits == bits }
    }
}
