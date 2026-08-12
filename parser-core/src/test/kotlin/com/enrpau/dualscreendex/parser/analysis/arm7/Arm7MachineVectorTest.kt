package com.enrpau.dualscreendex.parser.analysis.arm7

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Arm7MachineVectorTest {
    @Test
    fun executesUpstreamArmArithmeticAndFlagVectors() {
        val machine = armMachine(0xE3A0002A, 0xE0802001, 0xE0500001)
        machine.state[Arm7Register.R1] = 23

        assertStep(machine)
        assertEquals(42L, machine.state[Arm7Register.R0])
        assertStep(machine)
        assertEquals(65L, machine.state[Arm7Register.R2])

        machine.state[Arm7Register.R1] = 42
        assertStep(machine)
        assertEquals(0L, machine.state[Arm7Register.R0])
        assertTrue(machine.state.flag(Arm7Flag.Z))
        assertTrue(machine.state.flag(Arm7Flag.C))
    }

    @Test
    fun executesUpstreamMultiplyAndLongMultiplyVectors() {
        val machine = armMachine(0xE0000291, 0xE0810392)
        machine.state[Arm7Register.R1] = 7
        machine.state[Arm7Register.R2] = 6
        assertStep(machine)
        assertEquals(42L, machine.state[Arm7Register.R0])

        machine.state[Arm7Register.R2] = 0xFFFF_FFFFL
        machine.state[Arm7Register.R3] = 2
        assertStep(machine)
        assertEquals(0xFFFF_FFFEL, machine.state[Arm7Register.R0])
        assertEquals(1L, machine.state[Arm7Register.R1])
    }

    @Test
    fun rotatesUnalignedWordsAndUsesArm7OddHalfwordRules() {
        val arm = armMachine(0xE5910000)
        arm.memory.write32(Arm7Memory.EWRAM_START + 0x100, 0xAABB_CCDDL)
        arm.state[Arm7Register.R1] = Arm7Memory.EWRAM_START + 0x101
        assertStep(arm)
        assertEquals(0xDDAA_BBCCL, arm.state[Arm7Register.R0])

        val thumb = thumbMachine(0x8808, 0x5E88) // ldrh r0,[r1]; ldrsh r0,[r1,r2]
        thumb.memory.write16(Arm7Memory.EWRAM_START + 0x100, 0x80FEL)
        thumb.state[Arm7Register.R1] = Arm7Memory.EWRAM_START + 0x101
        assertStep(thumb)
        // ARM7 LDRH odd rotation is (0x80fe >>> 8) | (0x80fe << 24).
        assertEquals(0xFE00_0080L, thumb.state[Arm7Register.R0])

        thumb.state[Arm7Register.R2] = 0
        assertStep(thumb)
        assertEquals(0xFFFF_FF80L, thumb.state[Arm7Register.R0])
    }

    @Test
    fun highRegisterAddPcAndPopPcExposeInterworkingExactly() {
        val addPc = thumbMachine(0x4487) // add pc,r0
        addPc.state[Arm7Register.R0] = 4
        assertStep(addPc)
        assertEquals(Arm7Memory.ROM_START + 8L, addPc.state.pc)
        assertEquals(Arm7InstructionSet.THUMB, addPc.state.instructionSet)

        val popPc = thumbMachine(0xBD00) // pop {pc}
        popPc.state[Arm7Register.SP] = Arm7Memory.IWRAM_START + 0x100
        popPc.memory.write32(Arm7Memory.IWRAM_START + 0x100, Arm7Memory.ROM_START + 0x20)
        assertStep(popPc)
        assertEquals(Arm7Memory.ROM_START + 0x20L, popPc.state.pc)
        assertEquals(Arm7InstructionSet.ARM, popPc.state.instructionSet)
        assertEquals(Arm7Memory.IWRAM_START + 0x104L, popPc.state[Arm7Register.SP])
    }

    @Test
    fun unknownMalformedMemoryAndBudgetOutcomesNeverBecomeSuccess() {
        val malformedBl = thumbMachine(0xF000)
        assertTrue(malformedBl.step() is Arm7ExecutionResult.UnsupportedInstruction)

        val badMemory = armMachine(0xE5910000)
        badMemory.state[Arm7Register.R1] = 0x0400_0000
        assertTrue(badMemory.step() is Arm7ExecutionResult.InvalidMemory)

        val loop = armMachine(0xEAFF_FFFE)
        val first = loop.run(Arm7ExecutionBudget(maxInstructions = 8))
        val second = armMachine(0xEAFF_FFFE).run(Arm7ExecutionBudget(maxInstructions = 8))
        assertTrue(first is Arm7ExecutionResult.BudgetExceeded)
        assertEquals(first.canonicalSummary(), second.canonicalSummary())
    }

    @Test
    fun memoryRejectsRomWritesAndEscapedExecutionDeterministically() {
        val memory = Arm7Memory(ByteArray(4))
        val write = runCatching { memory.write32(Arm7Memory.ROM_START, 1) }.exceptionOrNull()
        assertTrue(write is Arm7MemoryAccessException)

        val branch = armMachine(0xEA3F_FFFE) // leaves the mapped ROM image
        assertStep(branch)
        assertTrue(branch.step() is Arm7ExecutionResult.EscapedExecution)
    }

    private fun armMachine(vararg words: Long): Arm7Machine = Arm7Machine(
        Arm7Memory(wordsToBytes(words)),
        Arm7State(instructionSet = Arm7InstructionSet.ARM, pc = Arm7Memory.ROM_START),
    )

    private fun thumbMachine(vararg halfwords: Int): Arm7Machine = Arm7Machine(
        Arm7Memory(halfwordsToBytes(halfwords)),
        Arm7State(instructionSet = Arm7InstructionSet.THUMB, pc = Arm7Memory.ROM_START),
    )

    private fun assertStep(machine: Arm7Machine) {
        assertTrue(machine.step() is Arm7ExecutionResult.Stepped)
    }

    private fun wordsToBytes(words: LongArray): ByteArray = ByteArray(words.size * 4).also { bytes ->
        words.forEachIndexed { index, value ->
            for (byte in 0..3) bytes[index * 4 + byte] = (value ushr (byte * 8)).toByte()
        }
    }

    private fun halfwordsToBytes(halfwords: IntArray): ByteArray = ByteArray(halfwords.size * 2).also { bytes ->
        halfwords.forEachIndexed { index, value ->
            bytes[index * 2] = value.toByte()
            bytes[index * 2 + 1] = (value ushr 8).toByte()
        }
    }
}
