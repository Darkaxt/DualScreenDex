package com.enrpau.dualscreendex.parser.analysis.arm7

data class Arm7ExecutionBudget(val maxInstructions: Int) {
    init { require(maxInstructions > 0) }
}

sealed interface Arm7ExecutionResult {
    val instructionsExecuted: Int
    val state: Arm7State

    data class Stepped(
        val instruction: Arm7Instruction,
        override val instructionsExecuted: Int,
        override val state: Arm7State,
    ) : Arm7ExecutionResult

    data class Completed(
        override val instructionsExecuted: Int,
        override val state: Arm7State,
        val reason: String,
    ) : Arm7ExecutionResult

    data class UnsupportedInstruction(
        override val instructionsExecuted: Int,
        override val state: Arm7State,
        val decodeResult: Arm7DecodeResult,
    ) : Arm7ExecutionResult

    data class InvalidMemory(
        override val instructionsExecuted: Int,
        override val state: Arm7State,
        val address: Long,
        val reason: String,
    ) : Arm7ExecutionResult

    data class EscapedExecution(
        override val instructionsExecuted: Int,
        override val state: Arm7State,
        val pc: Long,
    ) : Arm7ExecutionResult

    data class BudgetExceeded(
        override val instructionsExecuted: Int,
        override val state: Arm7State,
        val maximum: Int,
    ) : Arm7ExecutionResult
}

fun Arm7ExecutionResult.canonicalSummary(): String = buildString {
    append(this@canonicalSummary::class.simpleName)
    append('|').append(instructionsExecuted)
    append('|').append(state.canonicalSummary())
    when (this@canonicalSummary) {
        is Arm7ExecutionResult.Stepped -> append('|').append(instruction.offset).append(':').append(instruction.raw.toString(16))
        is Arm7ExecutionResult.Completed -> append('|').append(reason)
        is Arm7ExecutionResult.UnsupportedInstruction -> append('|').append(decodeResult)
        is Arm7ExecutionResult.InvalidMemory -> append('|').append(address.toString(16)).append('|').append(reason)
        is Arm7ExecutionResult.EscapedExecution -> append('|').append(pc.toString(16))
        is Arm7ExecutionResult.BudgetExceeded -> append('|').append(maximum)
    }
}
