package com.enrpau.dualscreendex.parser.analysis.arm7

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class Arm7MachineVectorTest {
    @Test
    fun `memory reuses an immutable ROM image without cloning the full ROM`() {
        val image = com.enrpau.dualscreendex.parser.io.RomImage(ByteArray(32 * 1024 * 1024))

        val memory = Arm7Memory(image)

        assertSame(image, memory.romImage)
    }

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
    fun highRegisterAddPcAndPopPcExposeProgramCounterRulesExactly() {
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
        assertEquals(Arm7InstructionSet.THUMB, popPc.state.instructionSet)
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

    @Test
    fun cpsrModeChangesBankRegistersAndPrivilegedSpsrRoundTrips() {
        val machine = armMachine(
            0xE321_F0D3, // msr cpsr_c,#0xd3 (SVC, IRQ/FIQ disabled)
            0xE169_F000, // msr spsr_fc,r0
            0xE14F_1000, // mrs r1,spsr
            0xE321_F0DF, // msr cpsr_c,#0xdf (SYS, IRQ/FIQ disabled)
        )
        machine.state[Arm7Register.SP] = 0x1111
        machine.state[Arm7Register.R0] = 0xA000_001FL

        assertStep(machine)
        assertEquals(0L, machine.state[Arm7Register.SP])
        machine.state[Arm7Register.SP] = 0x2222
        assertStep(machine)
        assertStep(machine)
        assertEquals(0xA000_001FL, machine.state[Arm7Register.R1])
        assertStep(machine)
        assertEquals(0x1111L, machine.state[Arm7Register.SP])
    }

    @Test
    fun dataProcessingPcWithSRestoresCpsrAndRegisterBank() {
        val machine = armMachine(
            0xE329_F011, // msr cpsr_fc,#MODE_FIQ
            0xE3A0_8040, // mov r8,#64 (FIQ bank)
            0xE369_F01F, // msr spsr_fc,#MODE_SYS
            0xE25F_F004, // subs pc,pc,#4 (exception-return form)
            0xE1A0_0000, // nop
        )
        machine.state[Arm7Register.R8] = 32

        repeat(3) { assertStep(machine) }
        assertEquals(64L, machine.state[Arm7Register.R8])
        assertStep(machine)

        assertEquals(0x1FL, machine.state.cpsr() and 0x1F)
        assertEquals(32L, machine.state[Arm7Register.R8])
        assertEquals(Arm7Memory.ROM_START + 16, machine.state.pc)
    }

    @Test
    fun arm3TestClassRdPcRestoresCpsrWithoutFlushingPipeline() {
        val machine = armMachine(
            0xE329_F011, // msr cpsr_fc,#MODE_FIQ
            0xE3A0_8040, // mov r8,#64 (FIQ bank)
            0xE369_F01F, // msr spsr_fc,#MODE_SYS
            0xE15F_F000, // ARM3 test-class Rd=PC status restore
            0xE1A0_0000, // nop: execution remains sequential
        )
        machine.state[Arm7Register.R8] = 32

        repeat(4) { assertStep(machine) }

        assertEquals(0x1FL, machine.state.cpsr() and 0x1F)
        assertEquals(32L, machine.state[Arm7Register.R8])
        assertEquals(Arm7Memory.ROM_START + 16, machine.state.pc)
    }

    @Test
    fun zeroFieldMsrEncodingIsAProvenArm7NoOp() {
        val machine = armMachine(0xE320_F000)
        machine.state[Arm7Register.R0] = 0x1234
        val before = machine.state.cpsr()

        assertStep(machine)

        assertEquals(before, machine.state.cpsr())
        assertEquals(0x1234L, machine.state[Arm7Register.R0])
        assertEquals(Arm7Memory.ROM_START + 4, machine.state.pc)
    }

    @Test
    fun singleTransferBaseValueAliasUsesArm7WritebackOrdering() {
        val preStore = armMachine(0xE5A0_0004) // str r0,[r0,#4]!
        preStore.state[Arm7Register.R0] = Arm7Memory.IWRAM_START + 0x100
        assertStep(preStore)
        assertEquals(Arm7Memory.IWRAM_START + 0x104, preStore.state[Arm7Register.R0])
        assertEquals(Arm7Memory.IWRAM_START + 0x100, preStore.memory.read32(Arm7Memory.IWRAM_START + 0x104))

        val postStore = armMachine(0xE480_0004) // str r0,[r0],#4
        postStore.state[Arm7Register.R0] = Arm7Memory.IWRAM_START + 0x100
        assertStep(postStore)
        assertEquals(Arm7Memory.IWRAM_START + 0x104, postStore.state[Arm7Register.R0])
        assertEquals(Arm7Memory.IWRAM_START + 0x100, postStore.memory.read32(Arm7Memory.IWRAM_START + 0x100))

        listOf(0xE5B0_0004L to (Arm7Memory.IWRAM_START + 0x104), 0xE490_0004L to (Arm7Memory.IWRAM_START + 0x100)).forEach { (word, address) ->
            val load = armMachine(word)
            load.state[Arm7Register.R0] = Arm7Memory.IWRAM_START + 0x100
            load.memory.write32(address, 32)
            assertStep(load)
            assertEquals(32L, load.state[Arm7Register.R0])
        }
    }

    @Test
    fun signedByteTransferSignExtendsFromBitSeven() {
        val machine = armMachine(0xE191_00D2) // ldrsb r0,[r1,r2]
        machine.state[Arm7Register.R1] = Arm7Memory.IWRAM_START + 0x100
        machine.state[Arm7Register.R2] = 1
        machine.memory.write8(Arm7Memory.IWRAM_START + 0x101, 0xFF)

        assertStep(machine)

        assertEquals(0xFFFF_FFFFL, machine.state[Arm7Register.R0])
    }

    @Test
    fun blockTransferUserSuffixAccessesUserBankWithoutChangingMode() {
        val store = armMachine(
            0xE321_F011, // msr cpsr_c,#MODE_FIQ
            0xE940_0300, // stmdb r0,{r8,r9}^
        )
        store.state[Arm7Register.R0] = Arm7Memory.IWRAM_START + 0x108
        store.state[Arm7Register.R8] = 32
        assertStep(store)
        store.state[Arm7Register.R8] = 64
        assertStep(store)
        assertEquals(32L, store.memory.read32(Arm7Memory.IWRAM_START + 0x100))
        assertEquals(64L, store.state[Arm7Register.R8])
        assertEquals(0x11L, store.state.cpsr() and 0x1F)

        val load = armMachine(
            0xE321_F011, // msr cpsr_c,#MODE_FIQ
            0xE950_0300, // ldmdb r0,{r8,r9}^
            0xE321_F01F, // msr cpsr_c,#MODE_SYS
        )
        load.state[Arm7Register.R0] = Arm7Memory.IWRAM_START + 0x108
        load.state[Arm7Register.R8] = 32
        load.memory.write32(Arm7Memory.IWRAM_START + 0x100, 10)
        load.memory.write32(Arm7Memory.IWRAM_START + 0x104, 20)
        assertStep(load)
        load.state[Arm7Register.R8] = 64
        assertStep(load)
        assertEquals(64L, load.state[Arm7Register.R8])
        assertStep(load)
        assertEquals(10L, load.state[Arm7Register.R8])
    }

    @Test
    fun ldmWritebackBaseInListKeepsLoadedBaseValue() {
        listOf(0xE8B1_0006L, 0xE8B1_0003L).forEach { word ->
            val machine = armMachine(word)
            machine.state[Arm7Register.R1] = Arm7Memory.IWRAM_START + 0x100
            machine.memory.write32(Arm7Memory.IWRAM_START + 0x100, 10)
            machine.memory.write32(Arm7Memory.IWRAM_START + 0x104, 20)

            assertStep(machine)

            assertEquals(if (word == 0xE8B1_0006L) 10L else 20L, machine.state[Arm7Register.R1])
        }
    }

    @Test
    fun stmBaseInListStoresFinalBaseOnlyWhenItIsNotLowestRegister() {
        val lowest = armMachine(0xE920_0003) // stmdb r0!,{r0,r1}
        lowest.state[Arm7Register.R0] = Arm7Memory.IWRAM_START + 0x108
        lowest.state[Arm7Register.R1] = 7
        assertStep(lowest)
        assertEquals(Arm7Memory.IWRAM_START + 0x108, lowest.memory.read32(Arm7Memory.IWRAM_START + 0x100))

        val notLowest = armMachine(0xE921_000F) // stmdb r1!,{r0-r3}
        notLowest.state[Arm7Register.R0] = 1
        notLowest.state[Arm7Register.R1] = Arm7Memory.IWRAM_START + 0x110
        assertStep(notLowest)
        assertEquals(Arm7Memory.IWRAM_START + 0x100, notLowest.memory.read32(Arm7Memory.IWRAM_START + 0x104))
    }

    @Test
    fun ewramAndIwramMirrorWithinTheirGbaAddressWindows() {
        val memory = Arm7Memory(ByteArray(4))
        memory.write32(Arm7Memory.IWRAM_START + Arm7Memory.IWRAM_SIZE, 0x1234)
        memory.write32(Arm7Memory.EWRAM_START + Arm7Memory.EWRAM_SIZE, 0x5678)

        assertEquals(0x1234L, memory.read32(Arm7Memory.IWRAM_START))
        assertEquals(0x5678L, memory.read32(Arm7Memory.EWRAM_START))
    }

    @Test
    fun thumbPopPcAlwaysRemainsInThumbAndClearsBitZero() {
        listOf(Arm7Memory.ROM_START + 0x20, Arm7Memory.ROM_START + 0x21).forEach { value ->
            val machine = thumbMachine(0xBD00)
            machine.state[Arm7Register.SP] = Arm7Memory.IWRAM_START + 0x100
            machine.memory.write32(Arm7Memory.IWRAM_START + 0x100, value)

            assertStep(machine)

            assertEquals(Arm7InstructionSet.THUMB, machine.state.instructionSet)
            assertEquals(Arm7Memory.ROM_START + 0x20, machine.state.pc)
        }
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
