package com.enrpau.dualscreendex.parser.analysis.arm7

import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmDecoderTest {
    @Test
    fun decodesConditionsDataProcessingAndBothShiftForms() {
        val equalAdd = decode(0x00810002).instructionAs<Arm7DataProcessing>() // addeq r0,r1,r2
        val immediateShift = decode(0xE09101A2).instructionAs<Arm7DataProcessing>() // adds r0,r1,r2,lsr #3
        val registerShift = decode(0xE0810312).instructionAs<Arm7DataProcessing>() // add r0,r1,r2,lsl r3
        val rotatedImmediate = decode(0xE3A034FF).instructionAs<Arm7DataProcessing>() // mov r3,#0xff000000

        assertEquals(Arm7Condition.EQUAL, equalAdd.condition)
        assertEquals(setOf(Arm7Flag.Z), equalAdd.flagsRead)
        assertEquals(Arm7DataOperation.ADD, equalAdd.operation)
        assertEquals(emptySet<Arm7Flag>(), equalAdd.flagsWritten)

        assertEquals(
            Arm7ShiftedRegister(
                Arm7Register.R2,
                Arm7ShiftType.LOGICAL_RIGHT,
                Arm7ShiftAmount.Immediate(3),
            ),
            immediateShift.second,
        )
        assertEquals(setOf(Arm7Flag.N, Arm7Flag.Z, Arm7Flag.C, Arm7Flag.V), immediateShift.flagsWritten)

        assertEquals(
            Arm7ShiftedRegister(
                Arm7Register.R2,
                Arm7ShiftType.LOGICAL_LEFT,
                Arm7ShiftAmount.Register(Arm7Register.R3),
            ),
            registerShift.second,
        )
        assertEquals(setOf(Arm7Register.R1, Arm7Register.R2, Arm7Register.R3), registerShift.registersRead)

        assertEquals(Arm7RotatedImmediate(encoded = 0xFF, rotateRight = 8, value = 0xFF000000L), rotatedImmediate.second)
    }

    @Test
    fun recordsArmPipelinePcBiasForDataAndStoreOperands() {
        val ordinaryPc = decode(0xE08F0001).instructionAs<Arm7DataProcessing>() // add r0,pc,r1
        val shiftedPc = decode(0xE08F0211).instructionAs<Arm7DataProcessing>() // add r0,pc,r1,lsl r2
        val storePc = decode(0xE580F000).instructionAs<Arm7MemoryTransfer>() // str pc,[r0]
        val pcBasedLoad = decode(0xE79F0001).instructionAs<Arm7MemoryTransfer>() // ldr r0,[pc,r1]

        assertEquals(Arm7RegisterOperand(Arm7Register.PC, pcBias = 8), ordinaryPc.first)
        assertEquals(Arm7RegisterOperand(Arm7Register.PC, pcBias = 12), shiftedPc.first)
        assertEquals(
            Arm7ShiftedRegister(
                Arm7Register.R1,
                Arm7ShiftType.LOGICAL_LEFT,
                Arm7ShiftAmount.Register(Arm7Register.R2),
            ),
            shiftedPc.second,
        )
        assertEquals(12, storePc.valueRegisterPcBias)
        assertEquals(setOf(Arm7Register.R0, Arm7Register.PC), storePc.registersRead)
        assertEquals(8, (pcBasedLoad.address as Arm7Address.ShiftedRegisterOffset).basePcBias)
    }

    @Test
    fun decodesMultiplyAndLongMultiplyWithCompleteRegisterEffects() {
        val multiply = decode(0xE0000291).instructionAs<Arm7Multiply>() // mul r0,r1,r2
        val accumulate = decode(0xE0243291).instructionAs<Arm7Multiply>() // mla r4,r1,r2,r3
        val long = decode(0xE0D10293).instructionAs<Arm7LongMultiply>() // smulls r0,r1,r3,r2

        assertEquals(Arm7Register.R0, multiply.destination)
        assertEquals(setOf(Arm7Register.R1, Arm7Register.R2), multiply.registersRead)
        assertEquals(emptySet<Arm7Flag>(), multiply.flagsWritten)

        assertEquals(Arm7Register.R3, accumulate.accumulator)
        assertEquals(setOf(Arm7Register.R1, Arm7Register.R2, Arm7Register.R3), accumulate.registersRead)
        assertEquals(setOf(Arm7Register.R4), accumulate.registersWritten)

        assertTrue(long.signed)
        assertEquals(Arm7Register.R0, long.destinationLow)
        assertEquals(Arm7Register.R1, long.destinationHigh)
        assertEquals(setOf(Arm7Flag.N, Arm7Flag.Z), long.flagsWritten)
    }

    @Test
    fun decodesSingleHalfwordAndBlockTransfersWithAddressSemantics() {
        val load = decode(0xE5910004).instructionAs<Arm7MemoryTransfer>() // ldr r0,[r1,#4]
        val storeByte = decode(0xE7E32104).instructionAs<Arm7MemoryTransfer>() // strb r2,[r3,r4,lsl#2]!
        val signedHalf = decode(0xE1D320F6).instructionAs<Arm7MemoryTransfer>() // ldrsh r2,[r3,#6]
        val swap = decode(0xE1020091).instructionAs<Arm7Swap>() // swp r0,r1,[r2]
        val push = decode(0xE92D4010).instructionAs<Arm7BlockTransfer>() // stmdb sp!,{r4,lr}
        val pop = decode(0xE8BD8010).instructionAs<Arm7BlockTransfer>() // ldmia sp!,{r4,pc}

        assertTrue(load.load)
        assertEquals(Arm7Address.RegisterOffset(Arm7Register.R1, immediate = 4), load.address)
        assertEquals(Arm7UnalignedPolicy.ROTATE_WORD_RIGHT_BY_ADDRESS, load.unalignedPolicy)

        assertTrue(!storeByte.load)
        assertEquals(
            Arm7Address.ShiftedRegisterOffset(
                base = Arm7Register.R3,
                index = Arm7ShiftedRegister(Arm7Register.R4, Arm7ShiftType.LOGICAL_LEFT, Arm7ShiftAmount.Immediate(2)),
                add = true,
                preIndexed = true,
                writeBack = true,
            ),
            storeByte.address,
        )
        assertEquals(Arm7MemoryWidth.BYTE, storeByte.width)

        assertTrue(signedHalf.signed)
        assertEquals(Arm7MemoryWidth.HALFWORD, signedHalf.width)
        assertEquals(Arm7UnalignedPolicy.SIGNED_BYTE_WHEN_ODD, signedHalf.unalignedPolicy)

        assertEquals(listOf(Arm7MemoryDirection.READ, Arm7MemoryDirection.WRITE), swap.memoryEffects.map { it.direction })
        assertEquals(setOf(Arm7Register.R1, Arm7Register.R2), swap.registersRead)
        assertEquals(setOf(Arm7Register.R0), swap.registersWritten)

        assertEquals(Arm7BlockAddressing.DECREMENT_BEFORE, push.addressing)
        assertEquals(listOf(Arm7Register.R4, Arm7Register.LR), push.registers)
        assertTrue(push.writeBack)
        assertEquals(Arm7ControlEffect.Sequential, push.controlEffect)

        assertEquals(listOf(Arm7Register.R4, Arm7Register.PC), pop.registers)
        assertEquals(Arm7ControlEffect.ProgramCounterWrite(interworking = false), pop.controlEffect)
    }

    @Test
    fun declaresCarryStatusRestoreAndPcStoreEffectsThatDriveControlFlow() {
        val reverseCarry = decode(0xE0F10002).instructionAs<Arm7DataProcessing>() // rscs r0,r1,r2
        val rrxAddress = decode(0xE7910062).instructionAs<Arm7MemoryTransfer>() // ldr r0,[r1,r2,rrx]
        val exceptionReturn = decode(0xE8FD8000).instructionAs<Arm7BlockTransfer>() // ldmia sp!,{pc}^
        val storePc = decode(0xE8A08000).instructionAs<Arm7BlockTransfer>() // stmia r0!,{pc}
        val readStatus = decode(0xE10F0000).instructionAs<Arm7StatusTransfer>() // mrs r0,cpsr
        val writeControl = decode(0xE121F000).instructionAs<Arm7StatusTransfer>() // msr cpsr_c,r0

        assertEquals(Arm7DataOperation.REVERSE_SUBTRACT_WITH_CARRY, reverseCarry.operation)
        assertTrue(Arm7Flag.C in reverseCarry.flagsRead)

        val address = rrxAddress.address as Arm7Address.ShiftedRegisterOffset
        assertEquals(Arm7ShiftType.ROTATE_RIGHT_EXTEND, address.index.type)
        assertEquals(Arm7ShiftAmount.Immediate(1), address.index.amount)
        assertEquals(setOf(Arm7Flag.C), rrxAddress.flagsRead)

        assertTrue(exceptionReturn.restoresStatusFromSpsr)
        assertEquals(Arm7Flag.entries.toSet(), exceptionReturn.flagsWritten)
        assertEquals(Arm7ControlEffect.ProgramCounterWrite(interworking = false), exceptionReturn.controlEffect)

        assertEquals(12, storePc.pcStoreBias)
        assertEquals(Arm7Flag.entries.toSet(), readStatus.flagsRead)
        assertEquals(Arm7ControlEffect.StatusWrite(mayChangeInstructionSet = true), writeControl.controlEffect)
    }

    @Test
    fun decodesBranchesInterworkingAndSoftwareInterrupt() {
        val image = armImage(0xEB000002, 0xE12FFF1E, 0xEFA1B2C3)

        val call = ArmDecoder.decode(image, 0).instructionAs<Arm7Branch>()
        val bx = ArmDecoder.decode(image, 4).instructionAs<Arm7BranchRegister>()
        val swi = ArmDecoder.decode(image, 8).instructionAs<Arm7SoftwareInterrupt>()

        assertEquals(16L, call.target)
        assertEquals(4L, call.returnAddress)
        assertEquals(Arm7ControlEffect.Call(16L, 4L, exchange = false), call.controlEffect)

        assertEquals(Arm7Register.LR, bx.targetRegister)
        assertEquals(Arm7ControlEffect.Return(interworking = true), bx.controlEffect)

        assertEquals(0xA1B2C3, swi.comment)
        assertEquals(Arm7ControlEffect.SupervisorCall(0xA1B2C3), swi.controlEffect)
    }

    @Test
    fun failsClosedForNewerArchitectureCoprocessorAndUnpredictableForms() {
        assertUnsupported(decode(0xFA000000), "BLX")
        assertUnsupported(decode(0xE12FFF30), "BLX")
        assertUnsupported(decode(0xE16F0F10), "CLZ")
        assertUnsupported(decode(0xE1200070), "BKPT")
        assertUnsupported(decode(0xEE000A10), "coprocessor")

        assertUndefined(decode(0xE00F0291), "PC") // MUL writes PC
        assertUndefined(decode(0xE0810F12), "shift") // register shift using PC as Rs
        assertUndefined(decode(0xE3A10002 or (1L shl 16)), "reserved Rn") // MOV with nonzero Rn field
        assertTrue(decode(0xE4900004) is Arm7DecodeResult.Decoded) // ARM7 keeps the loaded value.
        assertTrue(decode(0xE8B00003) is Arm7DecodeResult.Decoded) // ARM7 keeps the loaded base value.
    }

    @Test
    fun returnsTypedBoundsFailuresAndNeverReadsPartialWords() {
        val image = armImage(0xE1A00000)

        assertTrue(ArmDecoder.decode(image, -4) is Arm7DecodeResult.OutOfBounds)
        assertTrue(ArmDecoder.decode(image, 2) is Arm7DecodeResult.OutOfBounds)
        assertTrue(ArmDecoder.decode(image, 4) is Arm7DecodeResult.OutOfBounds)
    }

    private fun decode(word: Long): Arm7DecodeResult = ArmDecoder.decode(armImage(word), 0)

    private fun armImage(vararg words: Long): RomImage {
        val bytes = ByteArray(words.size * 4)
        words.forEachIndexed { index, value ->
            for (byte in 0..3) bytes[index * 4 + byte] = (value ushr (byte * 8)).toByte()
        }
        return RomImage(bytes)
    }

    private inline fun <reified T : Arm7Instruction> Arm7DecodeResult.instructionAs(): T {
        assertTrue("expected Decoded, got $this", this is Arm7DecodeResult.Decoded)
        val instruction = (this as Arm7DecodeResult.Decoded).instruction
        assertTrue("expected ${T::class.simpleName}, got $instruction", instruction is T)
        return instruction as T
    }

    private fun assertUndefined(result: Arm7DecodeResult, reasonFragment: String) {
        assertTrue("expected Undefined, got $result", result is Arm7DecodeResult.Undefined)
        assertTrue((result as Arm7DecodeResult.Undefined).reason.contains(reasonFragment, ignoreCase = true))
    }

    private fun assertUnsupported(result: Arm7DecodeResult, reasonFragment: String) {
        assertTrue("expected UnsupportedArchitecture, got $result", result is Arm7DecodeResult.UnsupportedArchitecture)
        assertTrue((result as Arm7DecodeResult.UnsupportedArchitecture).reason.contains(reasonFragment, ignoreCase = true))
    }
}
