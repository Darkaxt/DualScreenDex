package com.enrpau.dualscreendex.parser.analysis.arm7

import com.enrpau.dualscreendex.parser.analysis.thumb.ThumbDecoder
import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbIrDecoderTest {
    @Test
    fun decodesTypedArithmeticCompareAndDeclaredFlagEffects() {
        val image = thumbImage(0x00C8, 0x18CA, 0x2C07) // lsls r0,r1,#3; adds r2,r1,r3; cmp r4,#7

        val shift = ThumbDecoder.decode(image, 0).instructionAs<Arm7DataProcessing>()
        val add = ThumbDecoder.decode(image, 2).instructionAs<Arm7DataProcessing>()
        val compare = ThumbDecoder.decode(image, 4).instructionAs<Arm7Compare>()

        assertEquals(Arm7DataOperation.LOGICAL_SHIFT_LEFT, shift.operation)
        assertEquals(Arm7Register.R0, shift.destination)
        assertEquals(Arm7RegisterOperand(Arm7Register.R1), shift.first)
        assertEquals(Arm7Immediate(3), shift.second)
        assertEquals(setOf(Arm7Register.R1), shift.registersRead)
        assertEquals(setOf(Arm7Register.R0), shift.registersWritten)
        assertEquals(setOf(Arm7Flag.N, Arm7Flag.Z, Arm7Flag.C), shift.flagsWritten)

        assertEquals(Arm7DataOperation.ADD, add.operation)
        assertEquals(Arm7RegisterOperand(Arm7Register.R1), add.first)
        assertEquals(Arm7RegisterOperand(Arm7Register.R3), add.second)
        assertEquals(setOf(Arm7Flag.N, Arm7Flag.Z, Arm7Flag.C, Arm7Flag.V), add.flagsWritten)

        assertEquals(Arm7CompareOperation.COMPARE, compare.operation)
        assertEquals(Arm7RegisterOperand(Arm7Register.R4), compare.first)
        assertEquals(Arm7Immediate(7), compare.second)
        assertEquals(emptySet<Arm7Register>(), compare.registersWritten)
        assertEquals(setOf(Arm7Flag.N, Arm7Flag.Z, Arm7Flag.C, Arm7Flag.V), compare.flagsWritten)
    }

    @Test
    fun decodesTypedMemoryAndPreservesArm7OddHalfwordSemantics() {
        val image = thumbImage(
            0x5CAB, // ldrb r3,[r5,r2]
            0x80EA, // strh r2,[r5,#6]
            0x8808, // ldrh r0,[r1,#0]
            0x5E88, // ldrsh r0,[r1,r2]
        )

        val byteLoad = ThumbDecoder.decode(image, 0).instructionAs<Arm7MemoryTransfer>()
        val halfStore = ThumbDecoder.decode(image, 2).instructionAs<Arm7MemoryTransfer>()
        val halfLoad = ThumbDecoder.decode(image, 4).instructionAs<Arm7MemoryTransfer>()
        val signedHalfLoad = ThumbDecoder.decode(image, 6).instructionAs<Arm7MemoryTransfer>()

        assertTrue(byteLoad.load)
        assertEquals(Arm7MemoryWidth.BYTE, byteLoad.width)
        assertEquals(Arm7Register.R3, byteLoad.valueRegister)
        assertEquals(Arm7Address.RegisterOffset(Arm7Register.R5, Arm7Register.R2), byteLoad.address)
        assertEquals(Arm7UnalignedPolicy.BYTE_ADDRESSABLE, byteLoad.unalignedPolicy)
        assertEquals(Arm7MemoryDirection.READ, byteLoad.memoryEffects.single().direction)

        assertTrue(!halfStore.load)
        assertEquals(Arm7MemoryWidth.HALFWORD, halfStore.width)
        assertEquals(Arm7Address.RegisterOffset(Arm7Register.R5, immediate = 6), halfStore.address)
        assertEquals(Arm7UnalignedPolicy.ALIGN_DOWN, halfStore.unalignedPolicy)

        assertEquals(Arm7UnalignedPolicy.ROTATE_HALFWORD_RIGHT_8, halfLoad.unalignedPolicy)
        assertTrue(signedHalfLoad.signed)
        assertEquals(Arm7UnalignedPolicy.SIGNED_BYTE_WHEN_ODD, signedHalfLoad.unalignedPolicy)
    }

    @Test
    fun decodesStackTransfersReturnsAndPcWritesWithoutHidingControlFlow() {
        val image = thumbImage(
            0xB510, // push {r4,lr}
            0xBD10, // pop {r4,pc}
            0x4487, // add pc,r0
            0x4770, // bx lr
        )

        val push = ThumbDecoder.decode(image, 0).instructionAs<Arm7StackTransfer>()
        val pop = ThumbDecoder.decode(image, 2).instructionAs<Arm7StackTransfer>()
        val addPc = ThumbDecoder.decode(image, 4).instructionAs<Arm7DataProcessing>()
        val bxLr = ThumbDecoder.decode(image, 6).instructionAs<Arm7BranchRegister>()

        assertTrue(!push.load)
        assertEquals(listOf(Arm7Register.R4, Arm7Register.LR), push.registers)
        assertEquals(Arm7MemoryDirection.WRITE, push.memoryEffects.single().direction)
        assertEquals(Arm7ControlEffect.Sequential, push.controlEffect)

        assertTrue(pop.load)
        assertEquals(listOf(Arm7Register.R4, Arm7Register.PC), pop.registers)
        assertEquals(Arm7ControlEffect.Return(interworking = true), pop.controlEffect)

        assertEquals(Arm7Register.PC, addPc.destination)
        assertEquals(Arm7ControlEffect.ProgramCounterWrite(interworking = false), addPc.controlEffect)

        assertEquals(Arm7Register.LR, bxLr.targetRegister)
        assertEquals(Arm7ControlEffect.Return(interworking = true), bxLr.controlEffect)
    }

    @Test
    fun decodesConditionalBranchAtomicCallAndSoftwareInterrupt() {
        val image = thumbImage(
            0xD1FE, // bne back to this instruction
            0xF000, 0xF802, // bl +4
            0xDF12, // swi 0x12
        )

        val branch = ThumbDecoder.decode(image, 0).instructionAs<Arm7Branch>()
        val call = ThumbDecoder.decode(image, 2).instructionAs<Arm7Branch>()
        val softwareInterrupt = ThumbDecoder.decode(image, 6).instructionAs<Arm7SoftwareInterrupt>()

        assertEquals(Arm7Condition.NOT_EQUAL, branch.condition)
        assertEquals(0L, branch.target)
        assertEquals(setOf(Arm7Flag.Z), branch.flagsRead)
        assertEquals(Arm7ControlEffect.DirectBranch(0L, conditional = true), branch.controlEffect)

        assertTrue(call.link)
        assertEquals(10L, call.target)
        assertEquals(4, call.size)
        assertEquals(setOf(Arm7Register.LR, Arm7Register.PC), call.registersWritten)
        assertEquals(Arm7ControlEffect.Call(10L, returnAddress = 7L, exchange = false), call.controlEffect)

        assertEquals(0x12, softwareInterrupt.comment)
        assertEquals(Arm7ControlEffect.SupervisorCall(0x12), softwareInterrupt.controlEffect)
    }

    @Test
    fun failsClosedForMalformedPairsArmV5AndReservedEncodings() {
        assertTrue(ThumbDecoder.decode(thumbImage(0xF000), 0) is Arm7DecodeResult.NeedsSecondHalf)
        assertUndefined(ThumbDecoder.decode(thumbImage(0xF000, 0x2000), 0), "unpaired")
        assertUnsupported(ThumbDecoder.decode(thumbImage(0xF000, 0xE800), 0), "BLX")
        assertUndefined(ThumbDecoder.decode(thumbImage(0xF800), 0), "low half")

        assertUndefined(ThumbDecoder.decode(thumbImage(0x4771), 0), "reserved")
        assertUnsupported(ThumbDecoder.decode(thumbImage(0x4780), 0), "BLX")
        assertUndefined(ThumbDecoder.decode(thumbImage(0xDE00), 0), "condition")
        assertUnsupported(ThumbDecoder.decode(thumbImage(0xBE00), 0), "BKPT")
    }

    @Test
    fun rejectsMisalignedAndOutOfBoundsFetches() {
        val image = thumbImage(0x46C0)

        assertTrue(ThumbDecoder.decode(image, -2) is Arm7DecodeResult.OutOfBounds)
        assertTrue(ThumbDecoder.decode(image, 1) is Arm7DecodeResult.OutOfBounds)
        assertTrue(ThumbDecoder.decode(image, 2) is Arm7DecodeResult.OutOfBounds)
    }

    private fun thumbImage(vararg halfwords: Int): RomImage {
        val bytes = ByteArray(halfwords.size * 2)
        halfwords.forEachIndexed { index, value ->
            bytes[index * 2] = value.toByte()
            bytes[index * 2 + 1] = (value ushr 8).toByte()
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
