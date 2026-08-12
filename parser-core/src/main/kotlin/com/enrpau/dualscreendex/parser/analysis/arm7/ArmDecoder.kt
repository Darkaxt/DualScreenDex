package com.enrpau.dualscreendex.parser.analysis.arm7

import com.enrpau.dualscreendex.parser.io.RomImage

/** Strict little-endian A32 decoder for the ARMv4T instruction set implemented by ARM7TDMI. */
object ArmDecoder {
    fun decode(image: RomImage, offset: Int): Arm7DecodeResult {
        if (offset < 0 || offset and 3 != 0 || offset.toLong() + 4L > image.size.toLong()) {
            return Arm7DecodeResult.OutOfBounds(offset, 4, "A32 fetch must be word-aligned and inside the image")
        }
        val raw = image.u32le(offset)
        val conditionCode = (raw ushr 28).toInt()
        if (conditionCode == 0xF) {
            val reason = if (raw and 0x0E00_0000L == 0x0A00_0000L) {
                "A32 BLX immediate is ARMv5"
            } else {
                "A32 unconditional extension space is newer than ARMv4T"
            }
            return unsupported(offset, raw, reason)
        }
        val condition = CONDITIONS[conditionCode]

        when {
            raw and 0x0FFF_FFF0L == 0x012F_FF30L -> return unsupported(offset, raw, "A32 BLX register is ARMv5")
            raw and 0x0FFF_0FF0L == 0x016F_0F10L -> return unsupported(offset, raw, "A32 CLZ is ARMv5")
            raw and 0x0FF0_00F0L == 0x0120_0070L -> return unsupported(offset, raw, "A32 BKPT is ARMv5")
            raw and 0x0FFF_FFF0L == 0x012F_FF10L -> return decodeBranchExchange(raw, offset, condition)
            raw and 0x0F00_0000L == 0x0F00_0000L -> return decoded(
                Arm7SoftwareInterrupt(
                    offset, 4, raw, Arm7InstructionSet.ARM,
                    condition = condition,
                    comment = (raw and 0x00FF_FFFFL).toInt(),
                ),
            )
            raw and 0x0E00_0000L == 0x0A00_0000L -> return decodeBranch(raw, offset, condition)
            raw and 0x0E00_0000L == 0x0800_0000L -> return decodeBlockTransfer(raw, offset, condition)
            raw and 0x0C00_0000L == 0x0400_0000L -> return decodeSingleTransfer(raw, offset, condition)
            raw and 0x0E00_0000L == 0x0C00_0000L || raw and 0x0F00_0000L == 0x0E00_0000L ->
                return unsupported(offset, raw, "ARM7TDMI has no coprocessor/VFP execution unit")
            raw and 0x0C00_0000L != 0L -> return undefined(offset, raw, "undefined ARMv4T A32 major class")
        }

        when {
            raw and 0x0FC0_00F0L == 0x0000_0090L -> return decodeMultiply(raw, offset, condition)
            raw and 0x0F80_00F0L == 0x0080_0090L -> return decodeLongMultiply(raw, offset, condition)
            raw and 0x0FB0_0FF0L == 0x0100_0090L -> return decodeSwap(raw, offset, condition)
            raw and 0x0E00_0090L == 0x0000_0090L && raw and 0x60L != 0L ->
                return decodeHalfwordTransfer(raw, offset, condition)
            raw and 0x0FBF_0FFFL == 0x010F_0000L -> return decodeMrs(raw, offset, condition)
            raw and 0x0FB0_FFF0L == 0x0120_F000L -> return decodeMsrRegister(raw, offset, condition)
            raw and 0x0FB0_F000L == 0x0320_F000L -> return decodeMsrImmediate(raw, offset, condition)
        }
        return decodeDataProcessing(raw, offset, condition)
    }

    private fun decodeBranchExchange(raw: Long, offset: Int, condition: Arm7Condition): Arm7DecodeResult {
        val register = register((raw and 0xF).toInt())
        return decoded(
            Arm7BranchRegister(
                offset, 4, raw, Arm7InstructionSet.ARM,
                condition = condition,
                targetRegister = register,
                link = false,
                exchange = true,
            ),
        )
    }

    private fun decodeBranch(raw: Long, offset: Int, condition: Arm7Condition): Arm7DecodeResult {
        val link = raw and 0x0100_0000L != 0L
        var displacement = ((raw and 0x00FF_FFFFL) shl 2).toInt()
        if (displacement and 0x0200_0000 != 0) displacement = displacement or -0x0400_0000
        val target = offset.toLong() + 8L + displacement.toLong()
        return decoded(
            Arm7Branch(
                offset, 4, raw, Arm7InstructionSet.ARM,
                condition = condition,
                target = target,
                link = link,
                exchange = false,
                returnAddress = if (link) offset.toLong() + 4L else null,
            ),
        )
    }

    private fun decodeMultiply(raw: Long, offset: Int, condition: Arm7Condition): Arm7DecodeResult {
        val accumulate = raw and 0x0020_0000L != 0L
        val setFlags = raw and 0x0010_0000L != 0L
        val destination = register(bits(raw, 16, 4))
        val accumulator = register(bits(raw, 12, 4))
        val multiplier = register(bits(raw, 8, 4))
        val multiplicand = register(bits(raw, 0, 4))
        if (!accumulate && accumulator != Arm7Register.R0) {
            return undefined(offset, raw, "MUL reserved accumulator field must be zero")
        }
        if (Arm7Register.PC in setOf(destination, multiplier, multiplicand) ||
            (accumulate && accumulator == Arm7Register.PC)
        ) {
            return undefined(offset, raw, "ARM7 multiply cannot use PC")
        }
        if (destination == multiplicand) {
            return undefined(offset, raw, "ARM7 multiply destination cannot alias Rm")
        }
        return decoded(
            Arm7Multiply(
                offset, 4, raw, Arm7InstructionSet.ARM, condition,
                destination = destination,
                multiplicand = multiplicand,
                multiplier = multiplier,
                accumulator = accumulator.takeIf { accumulate },
                flagsWritten = if (setFlags) NZ else emptySet(),
            ),
        )
    }

    private fun decodeLongMultiply(raw: Long, offset: Int, condition: Arm7Condition): Arm7DecodeResult {
        val signed = raw and 0x0040_0000L != 0L
        val accumulate = raw and 0x0020_0000L != 0L
        val setFlags = raw and 0x0010_0000L != 0L
        val high = register(bits(raw, 16, 4))
        val low = register(bits(raw, 12, 4))
        val multiplier = register(bits(raw, 8, 4))
        val multiplicand = register(bits(raw, 0, 4))
        if (Arm7Register.PC in setOf(high, low, multiplier, multiplicand)) {
            return undefined(offset, raw, "ARM7 long multiply cannot use PC")
        }
        if (high == low || multiplicand == high || multiplicand == low) {
            return undefined(offset, raw, "ARM7 long multiply has an unpredictable destination alias")
        }
        return decoded(
            Arm7LongMultiply(
                offset, 4, raw, Arm7InstructionSet.ARM, condition,
                destinationLow = low,
                destinationHigh = high,
                multiplicand = multiplicand,
                multiplier = multiplier,
                signed = signed,
                accumulate = accumulate,
                flagsWritten = if (setFlags) NZ else emptySet(),
            ),
        )
    }

    private fun decodeSwap(raw: Long, offset: Int, condition: Arm7Condition): Arm7DecodeResult {
        val address = register(bits(raw, 16, 4))
        val destination = register(bits(raw, 12, 4))
        val source = register(bits(raw, 0, 4))
        if (Arm7Register.PC in setOf(address, destination, source)) {
            return undefined(offset, raw, "SWP cannot use PC")
        }
        if (address == destination || address == source) {
            return undefined(offset, raw, "SWP base register alias is architecturally unpredictable")
        }
        return decoded(
            Arm7Swap(
                offset, 4, raw, Arm7InstructionSet.ARM, condition,
                destination, source, address,
                if (raw and 0x0040_0000L != 0L) Arm7MemoryWidth.BYTE else Arm7MemoryWidth.WORD,
            ),
        )
    }

    private fun decodeDataProcessing(raw: Long, offset: Int, condition: Arm7Condition): Arm7DecodeResult {
        val immediateForm = raw and 0x0200_0000L != 0L
        val opcode = bits(raw, 21, 4)
        val setFlags = raw and 0x0010_0000L != 0L
        val rn = register(bits(raw, 16, 4))
        val rd = register(bits(raw, 12, 4))
        val compare = opcode in 8..11
        if (compare && (!setFlags || rd != Arm7Register.R0)) {
            return undefined(offset, raw, "test/compare encoding requires S=1 and reserved Rd=0")
        }
        if (opcode in setOf(13, 15) && rn != Arm7Register.R0) {
            return undefined(offset, raw, "MOV/MVN encoding requires reserved Rn=0")
        }
        val registerShift = !immediateForm && raw and 0x10L != 0L
        if (registerShift && raw and 0x80L != 0L) {
            return undefined(offset, raw, "register-shift encoding requires bit 7 to be zero")
        }
        val operand2 = if (immediateForm) {
            rotatedImmediate(raw, carryInWhenUnrotated = setFlags && opcode in LOGICAL_OPCODES)
        } else {
            val rm = register(bits(raw, 0, 4))
            val type = SHIFT_TYPES[bits(raw, 5, 2)]
            if (registerShift) {
                val rs = register(bits(raw, 8, 4))
                if (rs == Arm7Register.PC) return undefined(offset, raw, "register shift amount cannot use PC")
                Arm7ShiftedRegister(
                    rm,
                    type,
                    Arm7ShiftAmount.Register(rs),
                    carryInWhenZero = setFlags && opcode in LOGICAL_OPCODES,
                    pcBias = if (rm == Arm7Register.PC) 12 else 0,
                )
            } else {
                val encodedAmount = bits(raw, 7, 5)
                val (effectiveType, amount, carryIn) = when {
                    type == Arm7ShiftType.LOGICAL_LEFT && encodedAmount == 0 -> Triple(type, 0, setFlags && opcode in LOGICAL_OPCODES)
                    type == Arm7ShiftType.LOGICAL_RIGHT && encodedAmount == 0 -> Triple(type, 32, false)
                    type == Arm7ShiftType.ARITHMETIC_RIGHT && encodedAmount == 0 -> Triple(type, 32, false)
                    type == Arm7ShiftType.ROTATE_RIGHT && encodedAmount == 0 -> Triple(Arm7ShiftType.ROTATE_RIGHT_EXTEND, 1, true)
                    else -> Triple(type, encodedAmount, false)
                }
                Arm7ShiftedRegister(
                    rm,
                    effectiveType,
                    Arm7ShiftAmount.Immediate(amount),
                    carryInWhenZero = carryIn,
                    pcBias = if (rm == Arm7Register.PC) 8 else 0,
                )
            }
        }

        if (compare) {
            val operation = when (opcode) {
                8 -> Arm7CompareOperation.TEST
                9 -> Arm7CompareOperation.TEST_EQUIVALENCE
                10 -> Arm7CompareOperation.COMPARE
                else -> Arm7CompareOperation.COMPARE_NEGATIVE
            }
            val flags = if (opcode in 8..9) NZC else NZCV
            return decoded(
                Arm7Compare(
                    offset, 4, raw, Arm7InstructionSet.ARM,
                    condition = condition,
                    operation = operation,
                    first = registerOperand(rn, if (registerShift) 12 else 8),
                    second = operand2,
                    flagsWritten = flags,
                ),
            )
        }

        val operation = DATA_OPERATIONS[opcode]
        val hasFirst = opcode !in setOf(13, 15)
        val arithmetic = opcode in 2..7
        val additionalFlagsRead = if (opcode in setOf(5, 6, 7)) setOf(Arm7Flag.C) else emptySet()
        val flags = when {
            !setFlags -> emptySet()
            rd == Arm7Register.PC -> Arm7Flag.entries.toSet()
            arithmetic -> NZCV
            else -> NZC
        }
        return decoded(
            Arm7DataProcessing(
                offset, 4, raw, Arm7InstructionSet.ARM,
                condition = condition,
                operation = operation,
                destination = rd,
                first = if (hasFirst) registerOperand(rn, if (registerShift) 12 else 8) else null,
                second = operand2,
                flagsWritten = flags,
                additionalFlagsRead = additionalFlagsRead,
            ),
        )
    }

    private fun decodeSingleTransfer(raw: Long, offset: Int, condition: Arm7Condition): Arm7DecodeResult {
        val registerOffset = raw and 0x0200_0000L != 0L
        val preIndexed = raw and 0x0100_0000L != 0L
        val add = raw and 0x0080_0000L != 0L
        val byte = raw and 0x0040_0000L != 0L
        val explicitWriteBack = raw and 0x0020_0000L != 0L
        val load = raw and 0x0010_0000L != 0L
        val base = register(bits(raw, 16, 4))
        val value = register(bits(raw, 12, 4))
        val writeBack = explicitWriteBack || !preIndexed
        if (load && writeBack && base == value) {
            return undefined(offset, raw, "load writeback cannot use the same base and destination")
        }
        if (!load && writeBack && base == value) {
            return undefined(offset, raw, "store writeback base/value alias is architecturally unpredictable")
        }
        if (byte && value == Arm7Register.PC) {
            return undefined(offset, raw, "byte transfer cannot use PC as the value register")
        }
        val address = if (registerOffset) {
            if (raw and 0x10L != 0L) return undefined(offset, raw, "single-transfer register offset cannot use register-specified shift")
            val index = register(bits(raw, 0, 4))
            val encodedShift = bits(raw, 7, 5)
            val encodedType = SHIFT_TYPES[bits(raw, 5, 2)]
            val (shiftType, shiftAmount, carryIn) = when {
                encodedType == Arm7ShiftType.LOGICAL_RIGHT && encodedShift == 0 -> Triple(encodedType, 32, false)
                encodedType == Arm7ShiftType.ARITHMETIC_RIGHT && encodedShift == 0 -> Triple(encodedType, 32, false)
                encodedType == Arm7ShiftType.ROTATE_RIGHT && encodedShift == 0 ->
                    Triple(Arm7ShiftType.ROTATE_RIGHT_EXTEND, 1, true)
                else -> Triple(encodedType, encodedShift, false)
            }
            Arm7Address.ShiftedRegisterOffset(
                base,
                Arm7ShiftedRegister(
                    index,
                    shiftType,
                    Arm7ShiftAmount.Immediate(shiftAmount),
                    carryInWhenZero = carryIn,
                    pcBias = if (index == Arm7Register.PC) 8 else 0,
                ),
                add = add,
                preIndexed = preIndexed,
                writeBack = writeBack,
                basePcBias = if (base == Arm7Register.PC) 8 else 0,
            )
        } else {
            Arm7Address.RegisterOffset(
                base = base,
                immediate = bits(raw, 0, 12),
                add = add,
                preIndexed = preIndexed,
                writeBack = writeBack,
                pcBias = if (base == Arm7Register.PC) 8 else 0,
            )
        }
        val width = if (byte) Arm7MemoryWidth.BYTE else Arm7MemoryWidth.WORD
        return decoded(
            Arm7MemoryTransfer(
                offset, 4, raw, Arm7InstructionSet.ARM,
                condition = condition,
                load = load,
                valueRegister = value,
                address = address,
                width = width,
                unalignedPolicy = when {
                    byte -> Arm7UnalignedPolicy.BYTE_ADDRESSABLE
                    load -> Arm7UnalignedPolicy.ROTATE_WORD_RIGHT_BY_ADDRESS
                    else -> Arm7UnalignedPolicy.ALIGN_DOWN
                },
                valueRegisterPcBias = if (!load && value == Arm7Register.PC) 12 else 0,
            ),
        )
    }

    private fun decodeHalfwordTransfer(raw: Long, offset: Int, condition: Arm7Condition): Arm7DecodeResult {
        val preIndexed = raw and 0x0100_0000L != 0L
        val add = raw and 0x0080_0000L != 0L
        val immediateForm = raw and 0x0040_0000L != 0L
        val explicitWriteBack = raw and 0x0020_0000L != 0L
        val load = raw and 0x0010_0000L != 0L
        val base = register(bits(raw, 16, 4))
        val value = register(bits(raw, 12, 4))
        val sh = bits(raw, 5, 2)
        val writeBack = explicitWriteBack || !preIndexed
        if (value == Arm7Register.PC) return undefined(offset, raw, "halfword/signed transfer cannot use PC as destination/source")
        if (load && writeBack && base == value) return undefined(offset, raw, "load writeback cannot use the same base and destination")
        if (!load && writeBack && base == value) return undefined(offset, raw, "store writeback base/value alias is architecturally unpredictable")
        if (!load && sh != 1) return undefined(offset, raw, "signed halfword/byte store encoding is undefined")
        val signed = load && sh in 2..3
        val width = if (sh == 2) Arm7MemoryWidth.BYTE else Arm7MemoryWidth.HALFWORD
        val address = if (immediateForm) {
            val immediate = (bits(raw, 8, 4) shl 4) or bits(raw, 0, 4)
            Arm7Address.RegisterOffset(
                base, immediate = immediate, add = add,
                preIndexed = preIndexed, writeBack = writeBack,
                pcBias = if (base == Arm7Register.PC) 8 else 0,
            )
        } else {
            val index = register(bits(raw, 0, 4))
            if (index == Arm7Register.PC) return undefined(offset, raw, "halfword register offset cannot use PC")
            Arm7Address.RegisterOffset(
                base, index = index, add = add,
                preIndexed = preIndexed, writeBack = writeBack,
                pcBias = if (base == Arm7Register.PC) 8 else 0,
            )
        }
        return decoded(
            Arm7MemoryTransfer(
                offset, 4, raw, Arm7InstructionSet.ARM,
                condition = condition,
                load = load,
                valueRegister = value,
                address = address,
                width = width,
                signed = signed,
                unalignedPolicy = when {
                    !load -> Arm7UnalignedPolicy.ALIGN_DOWN
                    sh == 1 -> Arm7UnalignedPolicy.ROTATE_HALFWORD_RIGHT_8
                    sh == 2 -> Arm7UnalignedPolicy.BYTE_ADDRESSABLE
                    else -> Arm7UnalignedPolicy.SIGNED_BYTE_WHEN_ODD
                },
            ),
        )
    }

    private fun decodeBlockTransfer(raw: Long, offset: Int, condition: Arm7Condition): Arm7DecodeResult {
        val preIndexed = raw and 0x0100_0000L != 0L
        val add = raw and 0x0080_0000L != 0L
        val userMode = raw and 0x0040_0000L != 0L
        val writeBack = raw and 0x0020_0000L != 0L
        val load = raw and 0x0010_0000L != 0L
        val base = register(bits(raw, 16, 4))
        if (base == Arm7Register.PC) return undefined(offset, raw, "block transfer cannot use PC as base")
        val mask = bits(raw, 0, 16)
        val encodedRegisters = (0..15).filter { mask and (1 shl it) != 0 }.map(::register)
        if (load && writeBack && base in encodedRegisters) {
            return undefined(offset, raw, "LDM writeback with base in register list is architecturally unpredictable")
        }
        val empty = encodedRegisters.isEmpty()
        return decoded(
            Arm7BlockTransfer(
                offset, 4, raw, Arm7InstructionSet.ARM,
                condition = condition,
                load = load,
                base = base,
                registers = if (empty) listOf(Arm7Register.PC) else encodedRegisters,
                addressing = when {
                    add && preIndexed -> Arm7BlockAddressing.INCREMENT_BEFORE
                    add -> Arm7BlockAddressing.INCREMENT_AFTER
                    preIndexed -> Arm7BlockAddressing.DECREMENT_BEFORE
                    else -> Arm7BlockAddressing.DECREMENT_AFTER
                },
                writeBack = writeBack,
                userModeRegisters = userMode,
                emptyListArm7Quirk = empty,
                pcInterworking = false,
                restoresStatusFromSpsr = userMode && load && (empty || Arm7Register.PC in encodedRegisters),
                pcStoreBias = if (!load && (empty || Arm7Register.PC in encodedRegisters)) 12 else 0,
            ),
        )
    }

    private fun decodeMrs(raw: Long, offset: Int, condition: Arm7Condition): Arm7DecodeResult {
        val destination = register(bits(raw, 12, 4))
        if (destination == Arm7Register.PC) return undefined(offset, raw, "MRS cannot write PC")
        return decoded(
            Arm7StatusTransfer(
                offset, 4, raw, Arm7InstructionSet.ARM, condition,
                statusRegister = if (raw and 0x0040_0000L != 0L) Arm7StatusRegister.SPSR else Arm7StatusRegister.CPSR,
                toStatus = false,
                valueRegister = destination,
                immediate = null,
                fieldMask = 0,
            ),
        )
    }

    private fun decodeMsrRegister(raw: Long, offset: Int, condition: Arm7Condition): Arm7DecodeResult {
        val source = register(bits(raw, 0, 4))
        if (source == Arm7Register.PC) return undefined(offset, raw, "MSR register source cannot be PC")
        return statusWrite(raw, offset, condition, source, null)
    }

    private fun decodeMsrImmediate(raw: Long, offset: Int, condition: Arm7Condition): Arm7DecodeResult =
        statusWrite(raw, offset, condition, null, rotatedImmediate(raw, carryInWhenUnrotated = false))

    private fun statusWrite(
        raw: Long,
        offset: Int,
        condition: Arm7Condition,
        source: Arm7Register?,
        immediate: Arm7RotatedImmediate?,
    ): Arm7DecodeResult {
        val fieldMask = bits(raw, 16, 4)
        if (fieldMask == 0) return undefined(offset, raw, "MSR requires at least one status field")
        return decoded(
            Arm7StatusTransfer(
                offset, 4, raw, Arm7InstructionSet.ARM, condition,
                statusRegister = if (raw and 0x0040_0000L != 0L) Arm7StatusRegister.SPSR else Arm7StatusRegister.CPSR,
                toStatus = true,
                valueRegister = source,
                immediate = immediate,
                fieldMask = fieldMask,
            ),
        )
    }

    private fun rotatedImmediate(raw: Long, carryInWhenUnrotated: Boolean): Arm7RotatedImmediate {
        val encoded = bits(raw, 0, 8)
        val rotate = bits(raw, 8, 4) * 2
        val value = Integer.rotateRight(encoded, rotate).toLong() and 0xFFFF_FFFFL
        return Arm7RotatedImmediate(encoded, rotate, value, carryInWhenUnrotated)
    }

    private fun registerOperand(register: Arm7Register, pcBias: Int): Arm7RegisterOperand =
        Arm7RegisterOperand(register, pcBias = if (register == Arm7Register.PC) pcBias else 0)

    private fun bits(raw: Long, shift: Int, width: Int): Int = ((raw ushr shift) and ((1L shl width) - 1L)).toInt()
    private fun register(index: Int): Arm7Register = Arm7Register.fromIndex(index)
    private fun decoded(instruction: Arm7Instruction): Arm7DecodeResult = Arm7DecodeResult.Decoded(instruction)
    private fun undefined(offset: Int, raw: Long, reason: String) = Arm7DecodeResult.Undefined(offset, 4, raw, reason)
    private fun unsupported(offset: Int, raw: Long, reason: String) = Arm7DecodeResult.UnsupportedArchitecture(offset, 4, raw, reason)

    private val NZ = setOf(Arm7Flag.N, Arm7Flag.Z)
    private val NZC = NZ + Arm7Flag.C
    private val NZCV = NZC + Arm7Flag.V
    private val CONDITIONS = Arm7Condition.entries.take(15)
    private val SHIFT_TYPES = listOf(
        Arm7ShiftType.LOGICAL_LEFT,
        Arm7ShiftType.LOGICAL_RIGHT,
        Arm7ShiftType.ARITHMETIC_RIGHT,
        Arm7ShiftType.ROTATE_RIGHT,
    )
    private val LOGICAL_OPCODES = setOf(0, 1, 8, 9, 12, 13, 14, 15)
    private val DATA_OPERATIONS = listOf(
        Arm7DataOperation.AND,
        Arm7DataOperation.EXCLUSIVE_OR,
        Arm7DataOperation.SUBTRACT,
        Arm7DataOperation.REVERSE_SUBTRACT,
        Arm7DataOperation.ADD,
        Arm7DataOperation.ADD_WITH_CARRY,
        Arm7DataOperation.SUBTRACT_WITH_CARRY,
        Arm7DataOperation.REVERSE_SUBTRACT_WITH_CARRY,
        Arm7DataOperation.AND,
        Arm7DataOperation.EXCLUSIVE_OR,
        Arm7DataOperation.SUBTRACT,
        Arm7DataOperation.ADD,
        Arm7DataOperation.OR,
        Arm7DataOperation.MOVE,
        Arm7DataOperation.BIT_CLEAR,
        Arm7DataOperation.MOVE_NOT,
    )
}
