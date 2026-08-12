package com.enrpau.dualscreendex.parser.analysis.arm7

import java.security.MessageDigest

sealed interface Arm7ConformanceResult {
    data class Verdict(
        val value: Long,
        val instructionsExecuted: Int,
        val verdictPc: Long,
        val traceDigest: String,
    ) : Arm7ConformanceResult

    data class Incomplete(val execution: Arm7ExecutionResult) : Arm7ConformanceResult
}

fun Arm7ConformanceResult.canonicalSummary(): String = when (this) {
    is Arm7ConformanceResult.Verdict -> "VERDICT|$value|$instructionsExecuted|${verdictPc.toString(16)}|$traceDigest"
    is Arm7ConformanceResult.Incomplete -> "INCOMPLETE|${execution.canonicalSummary()}"
}

object Arm7ConformanceRunner {
    private val ARM_EVAL_MARKER = byteArrayOf(
        0xFF.toByte(), 0x1F, 0x2D, 0xE9.toByte(),
        0x0C, 0xC0.toByte(), 0xB0.toByte(), 0xE1.toByte(),
    )

    fun run(rom: ByteArray, budget: Arm7ExecutionBudget): Arm7ConformanceResult {
        val evalMarkerOffset = find(rom, ARM_EVAL_MARKER)?.plus(4)
        val verdictOffset = if (evalMarkerOffset != null) {
            findVsyncEntryBefore(rom, evalMarkerOffset) ?: evalMarkerOffset
        } else {
            findThumbVsyncCallBeforeArmHelper(rom)
        }
        val machine = Arm7Machine(
            Arm7Memory(
                rom,
                hostRegions = listOf(
                    Arm7HostMemoryRegion(
                        name = "GBA CPU fixture MMIO scratch",
                        start = 0x0400_0000L,
                        bytes = ByteArray(0x400),
                        writable = true,
                    ),
                    Arm7HostMemoryRegion(
                        name = "GBA CPU fixture palette RAM",
                        start = 0x0500_0000L,
                        bytes = ByteArray(0x400),
                        writable = true,
                    ),
                    Arm7HostMemoryRegion(
                        name = "GBA CPU fixture VRAM",
                        start = 0x0600_0000L,
                        bytes = ByteArray(0x18000),
                        writable = true,
                    ),
                    Arm7HostMemoryRegion(
                        name = "GBA CPU fixture OAM",
                        start = 0x0700_0000L,
                        bytes = ByteArray(0x400),
                        writable = true,
                    ),
                    Arm7HostMemoryRegion(
                        // single_transfer:t362 deliberately forms 0x80000000 via RRX; the loaded
                        // value is irrelevant, while the effective address/writeback is the oracle.
                        name = "GBA CPU fixture RRX address probe",
                        start = 0x8000_0000L,
                        bytes = ByteArray(4),
                        writable = true,
                    ),
                ),
            ),
            Arm7State(Arm7InstructionSet.ARM, Arm7Memory.ROM_START),
            softwareInterruptHandler = ::handleHostOperation,
        )
        machine.state.writeCpsr(0xDF, 1) // Documented ARM7 boot state: SYS mode, IRQ/FIQ masked.
        machine.state[Arm7Register.SP] = Arm7Memory.IWRAM_START + Arm7Memory.IWRAM_SIZE - 4L
        var previousPc = -1L
        var samePcCount = 0
        val digest = MessageDigest.getInstance("SHA-256")

        repeat(budget.maxInstructions) { count ->
            val pc = machine.state.pc
            if (verdictOffset != null && pc == Arm7Memory.ROM_START + verdictOffset) {
                return verdict(machine, count, pc, digest)
            }
            if (pc == previousPc) samePcCount++ else {
                previousPc = pc
                samePcCount = 0
            }
            if (verdictOffset == null && samePcCount >= IDLE_CONFIRMATIONS) {
                return verdict(machine, count, pc, digest)
            }
            digest.update(machine.state.canonicalSummary().toByteArray())
            digest.update(0)
            when (val execution = machine.step()) {
                is Arm7ExecutionResult.Stepped -> Unit
                else -> return Arm7ConformanceResult.Incomplete(execution)
            }
        }
        return Arm7ConformanceResult.Incomplete(
            Arm7ExecutionResult.BudgetExceeded(
                budget.maxInstructions,
                machine.state.copy(),
                budget.maxInstructions,
            ),
        )
    }

    private fun verdict(machine: Arm7Machine, instructions: Int, pc: Long, digest: MessageDigest) =
        Arm7ConformanceResult.Verdict(
            value = machine.state[Arm7Register.R12],
            instructionsExecuted = instructions,
            verdictPc = pc,
            traceDigest = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) },
        )

    private fun handleHostOperation(comment: Int, machine: Arm7Machine): Boolean = when (comment) {
        // CpuFastSet/CpuSet and BIOS math SWIs are deliberately not guessed here. The CPU fixtures
        // are expected to remain self-contained; an encountered SWI fails closed in Arm7Machine.
        else -> false
    }

    private fun find(bytes: ByteArray, pattern: ByteArray): Int? {
        for (offset in 0..bytes.size - pattern.size) {
            if (pattern.indices.all { bytes[offset + it] == pattern[it] }) return offset
        }
        return null
    }

    private fun findVsyncEntryBefore(bytes: ByteArray, evalMarkerOffset: Int): Int? {
        val searchStart = (evalMarkerOffset - 64).coerceAtLeast(0)
        for (offset in evalMarkerOffset - 4 downTo searchStart step 4) {
            if (read32(bytes, offset) == 0xE92D_0003L && read32(bytes, offset + 4) == 0xE3A0_0301L) return offset
        }
        return null
    }

    private fun findThumbVsyncCallBeforeArmHelper(bytes: ByteArray): Int? {
        for (armHelper in 0..bytes.size - 8 step 4) {
            if (read32(bytes, armHelper) != 0xE92D_0003L || read32(bytes, armHelper + 4) != 0xE3A0_0301L) continue
            val bxOffset = armHelper - 2
            if (bxOffset < 6 || read16(bytes, bxOffset) != 0x4700) continue
            // Fixture transition is `adr r0,armHelper; bx r0`; stop before that transition.
            if (read16(bytes, bxOffset - 2) and 0xF800 != 0xA000) continue
            return bxOffset - 2
        }
        return null
    }

    private fun read32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)

    private fun read16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private const val IDLE_CONFIRMATIONS = 8
}
