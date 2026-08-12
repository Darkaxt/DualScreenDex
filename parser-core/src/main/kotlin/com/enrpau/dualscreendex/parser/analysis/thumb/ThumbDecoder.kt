package com.enrpau.dualscreendex.parser.analysis.thumb

import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Address
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7BlockAddressing
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7BlockTransfer
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Branch
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7BranchRegister
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Compare
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7CompareOperation
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Condition
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DataOperation
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DataProcessing
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DecodeResult
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Flag
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Immediate
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Instruction
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7InstructionSet
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7MemoryTransfer
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7MemoryWidth
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Register
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7RegisterOperand
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7SoftwareInterrupt
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7StackTransfer
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7UnalignedPolicy
import com.enrpau.dualscreendex.parser.io.RomImage

/** Strict ARMv4T Thumb-1 decoder. Unknown and newer-architecture encodings fail as typed results. */
object ThumbDecoder {
    fun decode(image: RomImage, offset: Int): Arm7DecodeResult {
        if (offset < 0 || offset and 1 != 0 || offset.toLong() + 2L > image.size.toLong()) {
            return Arm7DecodeResult.OutOfBounds(offset, 2, "Thumb fetch must be aligned and inside the image")
        }
        val high = image.u16le(offset)
        if (high and 0xF800 == 0xF000) {
            if (offset.toLong() + 4L > image.size.toLong()) {
                return Arm7DecodeResult.NeedsSecondHalf(offset, high.toLong(), "truncated Thumb BL high half")
            }
            val low = image.u16le(offset + 2)
            if (low and 0xF800 == 0xE800) {
                return unsupported(offset, 4, combinedRaw(high, low), "Thumb BLX immediate is ARMv5")
            }
            if (low and 0xF800 != 0xF800) {
                return undefined(offset, 2, high.toLong(), "unpaired Thumb BL high half")
            }
            var displacement = ((high and 0x7FF) shl 12) or ((low and 0x7FF) shl 1)
            if (displacement and 0x400000 != 0) displacement -= 0x800000
            val target = offset.toLong() + 4L + displacement.toLong()
            return decoded(
                Arm7Branch(
                    offset = offset,
                    size = 4,
                    raw = combinedRaw(high, low),
                    instructionSet = Arm7InstructionSet.THUMB,
                    target = target,
                    link = true,
                    exchange = false,
                    returnAddress = offset.toLong() + 5L,
                ),
            )
        }
        return classifyHalfword(high, offset)
    }

    fun classifyHalfword(raw: Int, offset: Int = 0): Arm7DecodeResult {
        if (raw !in 0..0xFFFF) return undefined(offset, 2, raw.toLong(), "not a 16-bit Thumb encoding")
        if (raw and 0xF800 == 0xF000) {
            return Arm7DecodeResult.NeedsSecondHalf(offset, raw.toLong(), "Thumb BL requires an atomic low half")
        }
        if (raw and 0xF800 == 0xF800) {
            return undefined(offset, 2, raw.toLong(), "unpaired Thumb BL low half")
        }
        if (raw and 0xF800 == 0xE800) {
            return unsupported(offset, 2, raw.toLong(), "Thumb BLX immediate suffix is ARMv5")
        }

        if (raw and 0xE000 == 0x0000) return decodeShiftOrAddSubtract(raw, offset)
        if (raw and 0xE000 == 0x2000) return decodeImmediateData(raw, offset)
        if (raw and 0xFC00 == 0x4000) return decodeAlu(raw, offset)
        if (raw and 0xFC00 == 0x4400) return decodeHighRegister(raw, offset)
        if (raw and 0xF800 == 0x4800) {
            val immediate = (raw and 0xFF) * 4
            val target = ((offset + 4) and -4).toLong() + immediate
            return memory(
                raw, offset, load = true, register = register((raw ushr 8) and 7),
                address = Arm7Address.PcRelative(immediate, pcBias = 4, alignBaseTo = 4, resolvedAddress = target),
                width = Arm7MemoryWidth.WORD,
                policy = Arm7UnalignedPolicy.ALIGN_DOWN,
            )
        }
        if (raw and 0xF000 == 0x5000) return decodeRegisterMemory(raw, offset)
        if (raw and 0xE000 == 0x6000) return decodeImmediateWordOrByte(raw, offset)
        if (raw and 0xF000 == 0x8000) return decodeImmediateHalfword(raw, offset)
        if (raw and 0xF000 == 0x9000) return decodeStackRelative(raw, offset)
        if (raw and 0xF000 == 0xA000) return decodeLoadAddress(raw, offset)
        if (raw and 0xFF00 == 0xB000) return decodeStackAdjust(raw, offset)
        if (raw and 0xF600 == 0xB400) return decodePushPop(raw, offset)
        if (raw and 0xFF00 == 0xBE00) return unsupported(offset, 2, raw.toLong(), "Thumb BKPT is ARMv5")
        if (raw and 0xFF00 == 0xBF00) return unsupported(offset, 2, raw.toLong(), "Thumb hints/IT are newer than ARMv4T")
        if (raw and 0xF000 == 0xC000) return decodeMultiple(raw, offset)
        if (raw and 0xF000 == 0xD000) return decodeConditionalOrSwi(raw, offset)
        if (raw and 0xF800 == 0xE000) return decodeUnconditionalBranch(raw, offset)
        return undefined(offset, 2, raw.toLong(), "undefined ARMv4T Thumb encoding")
    }

    private fun decodeShiftOrAddSubtract(raw: Int, offset: Int): Arm7DecodeResult {
        if (raw and 0x1800 != 0x1800) {
            val operation = when ((raw ushr 11) and 3) {
                0 -> Arm7DataOperation.LOGICAL_SHIFT_LEFT
                1 -> Arm7DataOperation.LOGICAL_SHIFT_RIGHT
                else -> Arm7DataOperation.ARITHMETIC_SHIFT_RIGHT
            }
            val encodedAmount = (raw ushr 6) and 0x1F
            val amount = if (encodedAmount == 0 && operation != Arm7DataOperation.LOGICAL_SHIFT_LEFT) 32 else encodedAmount
            val flags = if (operation == Arm7DataOperation.LOGICAL_SHIFT_LEFT && amount == 0) NZ else NZC
            return data(
                raw, offset, operation, register(raw and 7),
                Arm7RegisterOperand(register((raw ushr 3) and 7)), Arm7Immediate(amount), flags,
            )
        }
        val immediateForm = raw and 0x0400 != 0
        return data(
            raw = raw,
            offset = offset,
            operation = if (raw and 0x0200 != 0) Arm7DataOperation.SUBTRACT else Arm7DataOperation.ADD,
            destination = register(raw and 7),
            first = Arm7RegisterOperand(register((raw ushr 3) and 7)),
            second = if (immediateForm) Arm7Immediate((raw ushr 6) and 7)
            else Arm7RegisterOperand(register((raw ushr 6) and 7)),
            flags = NZCV,
        )
    }

    private fun decodeImmediateData(raw: Int, offset: Int): Arm7DecodeResult {
        val destination = register((raw ushr 8) and 7)
        val immediate = Arm7Immediate(raw and 0xFF)
        return when ((raw ushr 11) and 3) {
            0 -> data(raw, offset, Arm7DataOperation.MOVE, destination, null, immediate, NZ)
            1 -> compare(raw, offset, Arm7CompareOperation.COMPARE, Arm7RegisterOperand(destination), immediate, NZCV)
            2 -> data(raw, offset, Arm7DataOperation.ADD, destination, Arm7RegisterOperand(destination), immediate, NZCV)
            else -> data(raw, offset, Arm7DataOperation.SUBTRACT, destination, Arm7RegisterOperand(destination), immediate, NZCV)
        }
    }

    private fun decodeAlu(raw: Int, offset: Int): Arm7DecodeResult {
        val destination = register(raw and 7)
        val source = register((raw ushr 3) and 7)
        val first = Arm7RegisterOperand(destination)
        val second = Arm7RegisterOperand(source)
        return when ((raw ushr 6) and 0xF) {
            0 -> data(raw, offset, Arm7DataOperation.AND, destination, first, second, NZ)
            1 -> data(raw, offset, Arm7DataOperation.EXCLUSIVE_OR, destination, first, second, NZ)
            2 -> data(raw, offset, Arm7DataOperation.LOGICAL_SHIFT_LEFT, destination, first, second, NZC, CARRY)
            3 -> data(raw, offset, Arm7DataOperation.LOGICAL_SHIFT_RIGHT, destination, first, second, NZC, CARRY)
            4 -> data(raw, offset, Arm7DataOperation.ARITHMETIC_SHIFT_RIGHT, destination, first, second, NZC, CARRY)
            5 -> data(raw, offset, Arm7DataOperation.ADD_WITH_CARRY, destination, first, second, NZCV, CARRY)
            6 -> data(raw, offset, Arm7DataOperation.SUBTRACT_WITH_CARRY, destination, first, second, NZCV, CARRY)
            7 -> data(raw, offset, Arm7DataOperation.ROTATE_RIGHT, destination, first, second, NZC, CARRY)
            8 -> compare(raw, offset, Arm7CompareOperation.TEST, first, second, NZ)
            9 -> data(raw, offset, Arm7DataOperation.SUBTRACT, destination, Arm7Immediate(0), second, NZCV)
            10 -> compare(raw, offset, Arm7CompareOperation.COMPARE, first, second, NZCV)
            11 -> compare(raw, offset, Arm7CompareOperation.COMPARE_NEGATIVE, first, second, NZCV)
            12 -> data(raw, offset, Arm7DataOperation.OR, destination, first, second, NZ)
            13 -> data(raw, offset, Arm7DataOperation.MULTIPLY, destination, first, second, NZ)
            14 -> data(raw, offset, Arm7DataOperation.BIT_CLEAR, destination, first, second, NZ)
            else -> data(raw, offset, Arm7DataOperation.MOVE_NOT, destination, null, second, NZ)
        }
    }

    private fun decodeHighRegister(raw: Int, offset: Int): Arm7DecodeResult {
        val op = (raw ushr 8) and 3
        val source = register((raw ushr 3) and 0xF)
        if (op == 3) {
            if (raw and 7 != 0) return undefined(offset, 2, raw.toLong(), "reserved Thumb BX destination bits")
            if (raw and 0x80 != 0) return unsupported(offset, 2, raw.toLong(), "Thumb BLX register is ARMv5")
            return decoded(
                Arm7BranchRegister(
                    offset, 2, raw.toLong(), Arm7InstructionSet.THUMB,
                    targetRegister = source, link = false, exchange = true,
                ),
            )
        }
        val destination = register((raw and 7) or ((raw ushr 4) and 8))
        val sourceOperand = registerOperand(source, offset)
        return when (op) {
            0 -> data(raw, offset, Arm7DataOperation.ADD, destination, registerOperand(destination, offset), sourceOperand, emptySet())
            1 -> compare(raw, offset, Arm7CompareOperation.COMPARE, registerOperand(destination, offset), sourceOperand, NZCV)
            else -> data(raw, offset, Arm7DataOperation.MOVE, destination, null, sourceOperand, emptySet())
        }
    }

    private fun decodeRegisterMemory(raw: Int, offset: Int): Arm7DecodeResult {
        val register = register(raw and 7)
        val address = Arm7Address.RegisterOffset(
            base = register((raw ushr 3) and 7),
            index = register((raw ushr 6) and 7),
        )
        return when ((raw ushr 9) and 7) {
            0 -> memory(raw, offset, false, register, address, Arm7MemoryWidth.WORD, policy = Arm7UnalignedPolicy.ALIGN_DOWN)
            1 -> memory(raw, offset, false, register, address, Arm7MemoryWidth.HALFWORD, policy = Arm7UnalignedPolicy.ALIGN_DOWN)
            2 -> memory(raw, offset, false, register, address, Arm7MemoryWidth.BYTE, policy = Arm7UnalignedPolicy.BYTE_ADDRESSABLE)
            3 -> memory(raw, offset, true, register, address, Arm7MemoryWidth.BYTE, signed = true, policy = Arm7UnalignedPolicy.BYTE_ADDRESSABLE)
            4 -> memory(raw, offset, true, register, address, Arm7MemoryWidth.WORD, policy = Arm7UnalignedPolicy.ROTATE_WORD_RIGHT_BY_ADDRESS)
            5 -> memory(raw, offset, true, register, address, Arm7MemoryWidth.HALFWORD, policy = Arm7UnalignedPolicy.ROTATE_HALFWORD_RIGHT_8)
            6 -> memory(raw, offset, true, register, address, Arm7MemoryWidth.BYTE, policy = Arm7UnalignedPolicy.BYTE_ADDRESSABLE)
            else -> memory(raw, offset, true, register, address, Arm7MemoryWidth.HALFWORD, signed = true, policy = Arm7UnalignedPolicy.SIGNED_BYTE_WHEN_ODD)
        }
    }

    private fun decodeImmediateWordOrByte(raw: Int, offset: Int): Arm7DecodeResult {
        val byte = raw and 0x1000 != 0
        val load = raw and 0x0800 != 0
        val encoded = (raw ushr 6) and 0x1F
        return memory(
            raw, offset, load, register(raw and 7),
            Arm7Address.RegisterOffset(register((raw ushr 3) and 7), immediate = if (byte) encoded else encoded * 4),
            width = if (byte) Arm7MemoryWidth.BYTE else Arm7MemoryWidth.WORD,
            policy = when {
                byte -> Arm7UnalignedPolicy.BYTE_ADDRESSABLE
                load -> Arm7UnalignedPolicy.ROTATE_WORD_RIGHT_BY_ADDRESS
                else -> Arm7UnalignedPolicy.ALIGN_DOWN
            },
        )
    }

    private fun decodeImmediateHalfword(raw: Int, offset: Int): Arm7DecodeResult {
        val load = raw and 0x0800 != 0
        return memory(
            raw, offset, load, register(raw and 7),
            Arm7Address.RegisterOffset(register((raw ushr 3) and 7), immediate = ((raw ushr 6) and 0x1F) * 2),
            Arm7MemoryWidth.HALFWORD,
            policy = if (load) Arm7UnalignedPolicy.ROTATE_HALFWORD_RIGHT_8 else Arm7UnalignedPolicy.ALIGN_DOWN,
        )
    }

    private fun decodeStackRelative(raw: Int, offset: Int): Arm7DecodeResult {
        val load = raw and 0x0800 != 0
        return memory(
            raw, offset, load, register((raw ushr 8) and 7),
            Arm7Address.RegisterOffset(Arm7Register.SP, immediate = (raw and 0xFF) * 4),
            Arm7MemoryWidth.WORD,
            policy = if (load) Arm7UnalignedPolicy.ROTATE_WORD_RIGHT_BY_ADDRESS else Arm7UnalignedPolicy.ALIGN_DOWN,
        )
    }

    private fun decodeLoadAddress(raw: Int, offset: Int): Arm7DecodeResult {
        val base = if (raw and 0x0800 != 0) Arm7Register.SP else Arm7Register.PC
        return data(
            raw, offset, Arm7DataOperation.ADD, register((raw ushr 8) and 7),
            registerOperand(base, offset, alignPcTo = 4), Arm7Immediate((raw and 0xFF) * 4), emptySet(),
        )
    }

    private fun decodeStackAdjust(raw: Int, offset: Int): Arm7DecodeResult {
        val amount = (raw and 0x7F) * 4
        return data(
            raw, offset,
            if (raw and 0x80 != 0) Arm7DataOperation.SUBTRACT else Arm7DataOperation.ADD,
            Arm7Register.SP, Arm7RegisterOperand(Arm7Register.SP), Arm7Immediate(amount), emptySet(),
        )
    }

    private fun decodePushPop(raw: Int, offset: Int): Arm7DecodeResult {
        val pop = raw and 0x0800 != 0
        val registers = buildList {
            for (index in 0..7) if (raw and (1 shl index) != 0) add(register(index))
            if (raw and 0x0100 != 0) add(if (pop) Arm7Register.PC else Arm7Register.LR)
        }
        if (registers.isEmpty()) return undefined(offset, 2, raw.toLong(), "empty Thumb PUSH/POP register list")
        return decoded(Arm7StackTransfer(offset, 2, raw.toLong(), Arm7InstructionSet.THUMB, load = pop, registers = registers))
    }

    private fun decodeMultiple(raw: Int, offset: Int): Arm7DecodeResult {
        val load = raw and 0x0800 != 0
        val base = register((raw ushr 8) and 7)
        val encodedRegisters = (0..7).filter { raw and (1 shl it) != 0 }.map(::register)
        val empty = encodedRegisters.isEmpty()
        val effectiveRegisters = if (empty) listOf(Arm7Register.PC) else encodedRegisters
        return decoded(
            Arm7BlockTransfer(
                offset, 2, raw.toLong(), Arm7InstructionSet.THUMB,
                load = load,
                base = base,
                registers = effectiveRegisters,
                addressing = Arm7BlockAddressing.INCREMENT_AFTER,
                writeBack = !(load && base in encodedRegisters),
                emptyListArm7Quirk = empty,
            ),
        )
    }

    private fun decodeConditionalOrSwi(raw: Int, offset: Int): Arm7DecodeResult {
        val conditionCode = (raw ushr 8) and 0xF
        if (conditionCode == 0xF) {
            return decoded(Arm7SoftwareInterrupt(offset, 2, raw.toLong(), Arm7InstructionSet.THUMB, comment = raw and 0xFF))
        }
        if (conditionCode == 0xE) return undefined(offset, 2, raw.toLong(), "reserved Thumb condition 0xE")
        val displacement = (raw and 0xFF).toByte().toInt() shl 1
        return decoded(
            Arm7Branch(
                offset, 2, raw.toLong(), Arm7InstructionSet.THUMB,
                condition = CONDITIONS[conditionCode],
                target = offset.toLong() + 4L + displacement,
                link = false,
                exchange = false,
            ),
        )
    }

    private fun decodeUnconditionalBranch(raw: Int, offset: Int): Arm7DecodeResult {
        var displacement = (raw and 0x7FF) shl 1
        if (displacement and 0x800 != 0) displacement -= 0x1000
        return decoded(
            Arm7Branch(
                offset, 2, raw.toLong(), Arm7InstructionSet.THUMB,
                target = offset.toLong() + 4L + displacement,
                link = false,
                exchange = false,
            ),
        )
    }

    private fun data(
        raw: Int,
        offset: Int,
        operation: Arm7DataOperation,
        destination: Arm7Register,
        first: com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Operand?,
        second: com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Operand,
        flags: Set<Arm7Flag>,
        additionalFlagsRead: Set<Arm7Flag> = emptySet(),
    ): Arm7DecodeResult = decoded(
        Arm7DataProcessing(
            offset, 2, raw.toLong(), Arm7InstructionSet.THUMB,
            operation = operation,
            destination = destination,
            first = first,
            second = second,
            flagsWritten = flags,
            additionalFlagsRead = additionalFlagsRead,
        ),
    )

    private fun compare(
        raw: Int,
        offset: Int,
        operation: Arm7CompareOperation,
        first: com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Operand,
        second: com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Operand,
        flags: Set<Arm7Flag>,
    ): Arm7DecodeResult = decoded(
        Arm7Compare(offset, 2, raw.toLong(), Arm7InstructionSet.THUMB, operation = operation, first = first, second = second, flagsWritten = flags),
    )

    private fun memory(
        raw: Int,
        offset: Int,
        load: Boolean,
        register: Arm7Register,
        address: Arm7Address,
        width: Arm7MemoryWidth,
        signed: Boolean = false,
        policy: Arm7UnalignedPolicy,
    ): Arm7DecodeResult = decoded(
        Arm7MemoryTransfer(
            offset, 2, raw.toLong(), Arm7InstructionSet.THUMB,
            load = load,
            valueRegister = register,
            address = address,
            width = width,
            signed = signed,
            unalignedPolicy = policy,
        ),
    )

    private fun register(index: Int): Arm7Register = Arm7Register.fromIndex(index)

    private fun registerOperand(register: Arm7Register, offset: Int, alignPcTo: Int = 1): Arm7RegisterOperand =
        if (register == Arm7Register.PC) Arm7RegisterOperand(register, pcBias = 4, alignDownTo = alignPcTo)
        else Arm7RegisterOperand(register)

    private fun decoded(instruction: Arm7Instruction): Arm7DecodeResult = Arm7DecodeResult.Decoded(instruction)
    private fun undefined(offset: Int, size: Int, raw: Long, reason: String) = Arm7DecodeResult.Undefined(offset, size, raw, reason)
    private fun unsupported(offset: Int, size: Int, raw: Long, reason: String) = Arm7DecodeResult.UnsupportedArchitecture(offset, size, raw, reason)
    private fun combinedRaw(high: Int, low: Int): Long = high.toLong() or (low.toLong() shl 16)

    private val NZ = setOf(Arm7Flag.N, Arm7Flag.Z)
    private val NZC = NZ + Arm7Flag.C
    private val NZCV = NZC + Arm7Flag.V
    private val CARRY = setOf(Arm7Flag.C)
    private val CONDITIONS = Arm7Condition.entries.take(14)
}
