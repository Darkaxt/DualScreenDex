package com.enrpau.dualscreendex.parser.dataset.abilities.analysis

import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Address
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7BranchRegister
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Condition
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7ControlEffect
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DataOperation
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DataProcessing
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DecodeResult
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Instruction
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7InstructionSet
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7MemoryTransfer
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7MemoryWidth
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Register
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7RegisterOperand
import com.enrpau.dualscreendex.parser.analysis.thumb.ThumbDecoder
import com.enrpau.dualscreendex.parser.io.RomImage

internal data class LiteralThumbVeneer(
    val loadOffset: Int,
    val literalOffset: Int,
    val rawTarget: Long,
    val targetOffset: Int,
    val targetRegister: Arm7Register,
)

/**
 * Normalizes only a unique two-instruction Thumb veneer:
 * `LDR target,[PC,#literal] ; BX target` (or the equivalent high-register `MOV PC,target`).
 */
internal fun literalThumbVeneer(
    image: RomImage,
    transfer: Arm7Instruction,
): LiteralThumbVeneer? {
    if (transfer.instructionSet != Arm7InstructionSet.THUMB || transfer.condition != Arm7Condition.ALWAYS) {
        return null
    }
    val embeddedEntry = transfer is Arm7BranchRegister
    val targetRegister = when (transfer) {
        is Arm7BranchRegister -> {
            if (transfer.controlEffect !is Arm7ControlEffect.IndirectBranch || !transfer.exchange || transfer.link) {
                return null
            }
            transfer.targetRegister
        }
        is Arm7DataProcessing -> {
            val operand = transfer.second as? Arm7RegisterOperand ?: return null
            if (transfer.controlEffect !is Arm7ControlEffect.ProgramCounterWrite ||
                transfer.operation != Arm7DataOperation.MOVE ||
                transfer.destination != Arm7Register.PC ||
                transfer.first != null ||
                transfer.flagsWritten.isNotEmpty()
            ) {
                return null
            }
            operand.register
        }
        else -> return null
    }
    val loadOffset = transfer.offset - 2
    if (loadOffset < 0) return null
    val load = (ThumbDecoder.decode(image, loadOffset) as? Arm7DecodeResult.Decoded)
        ?.instruction as? Arm7MemoryTransfer ?: return null
    val address = load.address as? Arm7Address.PcRelative ?: return null
    if (load.size != 2 ||
        load.condition != Arm7Condition.ALWAYS ||
        load.controlEffect !is Arm7ControlEffect.Sequential ||
        !load.load ||
        load.width != Arm7MemoryWidth.WORD ||
        load.valueRegister != targetRegister ||
        load.offset + load.size != transfer.offset
    ) {
        return null
    }
    val literalOffset = address.resolvedAddress.toInt()
    if (literalOffset !in 0..image.size - 4) return null
    if (embeddedEntry && literalOffset != transfer.offset + transfer.size) return null
    val rawTarget = image.u32le(literalOffset)
    if (rawTarget and 1L == 0L) return null
    val targetOffset = image.gbaPointer(literalOffset)?.and(-2) ?: return null
    if (targetOffset and 1 != 0) return null
    if (ThumbDecoder.decode(image, targetOffset) !is Arm7DecodeResult.Decoded) return null
    if (!embeddedEntry && !isReturnToEmbeddedVeneerContinuation(image, targetOffset)) return null
    return LiteralThumbVeneer(loadOffset, literalOffset, rawTarget, targetOffset, targetRegister)
}

private fun isReturnToEmbeddedVeneerContinuation(image: RomImage, continuation: Int): Boolean {
    val entryLoadOffset = continuation - EMBEDDED_VENEER_BYTES
    if (entryLoadOffset < 0) return false
    val entryLoad = (ThumbDecoder.decode(image, entryLoadOffset) as? Arm7DecodeResult.Decoded)
        ?.instruction as? Arm7MemoryTransfer ?: return false
    val entryTransfer = (ThumbDecoder.decode(image, entryLoadOffset + 2) as? Arm7DecodeResult.Decoded)
        ?.instruction as? Arm7BranchRegister ?: return false
    val entryLiteral = entryLoad.address as? Arm7Address.PcRelative ?: return false
    if (!entryLoad.load ||
        entryLoad.width != Arm7MemoryWidth.WORD ||
        entryLoad.valueRegister != entryTransfer.targetRegister ||
        !entryTransfer.exchange ||
        entryTransfer.link ||
        entryLiteral.resolvedAddress.toInt() != continuation - 4
    ) {
        return false
    }
    val patchTarget = image.u32le(continuation - 4)
    if (patchTarget and 1L == 0L) return false
    val patchOffset = image.gbaPointer(continuation - 4)?.and(-2) ?: return false
    if (ThumbDecoder.decode(image, patchOffset) !is Arm7DecodeResult.Decoded) return false
    return true
}

private const val EMBEDDED_VENEER_BYTES = 8
