package com.enrpau.dualscreendex.parser.dataset.moves

import com.enrpau.dualscreendex.parser.analysis.ExtentCheck
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession

fun interface MoveDetailsTableDecoder {
    fun decode(
        session: RomAnalysisSession,
        layout: MoveDetailsTableLayout,
    ): MoveDetailsTableOutcome
}

/** Sole byte-level interpreter used by both move-detail validation and later materialization. */
class MoveDetailsCodec : MoveDetailsTableDecoder {
    override fun decode(
        session: RomAnalysisSession,
        layout: MoveDetailsTableLayout,
    ): MoveDetailsTableOutcome {
        val checked = when (
            val extent = session.limits.checkTableExtent(
                offset = layout.offset,
                count = layout.count,
                recordSize = layout.abi.recordSize.toLong(),
                romSize = session.rom.size.toLong(),
            )
        ) {
            is ExtentCheck.Valid -> extent.extent
            is ExtentCheck.Invalid -> return MoveDetailsTableOutcome.Rejected(layout, extent.reason)
            is ExtentCheck.BudgetExceeded -> return MoveDetailsTableOutcome.ExtentBudgetExceeded(
                layout = layout,
                observedBytes = extent.observedBytes,
                limitBytes = extent.limitBytes,
                reason = "move-detail table extent ${extent.observedBytes} exceeds deterministic budget " +
                    extent.limitBytes,
            )
        }
        val rows = List(layout.count.toInt()) { rowIndex ->
            decodeRow(
                session = session,
                abi = layout.abi,
                rowIndex = rowIndex,
                recordOffset = checked.offset + rowIndex * layout.abi.recordSize,
            )
        }
        return MoveDetailsTableOutcome.Decoded(layout, rows)
    }

    private fun decodeRow(
        session: RomAnalysisSession,
        abi: MoveDetailsAbi,
        rowIndex: Int,
        recordOffset: Int,
    ): MoveDetailsRowOutcome {
        val bytes = session.rom.slice(recordOffset, abi.recordSize)
        if (bytes.all { it == 0.toByte() }) return MoveDetailsRowOutcome.StructuralEmpty(rowIndex)
        if (abi == MoveDetailsAbi.UNIFIED_MOVE_INFO_48) {
            return decodeUnifiedMoveInfoRow(session, rowIndex, recordOffset)
        }

        val widened = abi != MoveDetailsAbi.RETAIL_12
        val typeId = session.rom.u8(recordOffset + if (widened) EXTENDED_TYPE_OFFSET else RETAIL_TYPE_OFFSET)
        val accuracy = session.rom.u8(
            recordOffset + if (widened) EXTENDED_ACCURACY_OFFSET else RETAIL_ACCURACY_OFFSET,
        )
        val pp = session.rom.u8(recordOffset + if (widened) EXTENDED_PP_OFFSET else RETAIL_PP_OFFSET)
        val secondaryChance = session.rom.u8(
            recordOffset + if (widened) EXTENDED_SECONDARY_CHANCE_OFFSET else RETAIL_SECONDARY_CHANCE_OFFSET,
        )
        val priorityOffset = when (abi) {
            MoveDetailsAbi.RETAIL_12 -> RETAIL_PRIORITY_OFFSET
            MoveDetailsAbi.CFRU_16 -> CFRU_PRIORITY_OFFSET
            MoveDetailsAbi.BATTLE_ENGINE_20 -> BATTLE_ENGINE_PRIORITY_OFFSET
            MoveDetailsAbi.UNIFIED_MOVE_INFO_48 -> error("unified MoveInfo rows decode through their packed ABI")
        }
        val priority = session.rom.u8(recordOffset + priorityOffset).toByte().toInt()
        val splitRaw = when (abi) {
            MoveDetailsAbi.RETAIL_12 -> null
            MoveDetailsAbi.CFRU_16 -> session.rom.u8(recordOffset + CFRU_SPLIT_OFFSET)
            MoveDetailsAbi.BATTLE_ENGINE_20 -> session.rom.u8(recordOffset + BATTLE_ENGINE_SPLIT_OFFSET)
            MoveDetailsAbi.UNIFIED_MOVE_INFO_48 -> error("unified MoveInfo rows decode through their packed ABI")
        }
        val reasons = buildList {
            if (typeId !in 0..MAX_TYPE_ID) add("type value $typeId exceeds $MAX_TYPE_ID")
            if (
                accuracy != ACCURACY_ALWAYS &&
                (!widened || accuracy != ACCURACY_ENGINE_DEFINED) &&
                accuracy !in MIN_PERCENT_ACCURACY..MAX_PERCENT
            ) {
                add("accuracy value $accuracy is neither always-hit nor $MIN_PERCENT_ACCURACY..$MAX_PERCENT")
            }
            if (pp !in 0..MAX_PP) add("pp value $pp exceeds $MAX_PP")
            if (secondaryChance !in 0..MAX_PERCENT && secondaryChance != CHANCE_ENGINE_DEFINED) {
                add("secondary-effect chance $secondaryChance is outside 0..$MAX_PERCENT")
            }
            if (priority !in MIN_PRIORITY..MAX_PRIORITY) {
                add("priority value $priority is outside $MIN_PRIORITY..$MAX_PRIORITY")
            }
            if (splitRaw != null && MoveSplit.fromRaw(splitRaw) == null) {
                add("split value $splitRaw is not physical, special, or status")
            }
            if (abi != MoveDetailsAbi.RETAIL_12 && session.rom.u8(recordOffset + EXTENDED_PADDING_OFFSET) != 0) {
                add("extended ABI padding byte is nonzero")
            }
            if (
                abi != MoveDetailsAbi.RETAIL_12 &&
                session.rom.u16le(recordOffset + POWER_OFFSET) > MAX_WIDENED_POWER
            ) {
                add("widened power exceeds $MAX_WIDENED_POWER")
            }
        }
        if (reasons.isNotEmpty()) return MoveDetailsRowOutcome.Malformed(rowIndex, reasons)

        val record = when (abi) {
            MoveDetailsAbi.RETAIL_12 -> Gen3MoveDetailsRecord(
                effectId = session.rom.u8(recordOffset),
                power = session.rom.u8(recordOffset + 1),
                typeId = typeId,
                accuracy = accuracy,
                pp = pp,
                secondaryEffectChance = secondaryChance,
                targetMask = session.rom.u8(recordOffset + RETAIL_TARGET_OFFSET),
                priority = priority,
                flags = session.rom.u32le(recordOffset + RETAIL_FLAGS_OFFSET),
                split = null,
                argument = null,
                zMovePower = null,
                zMoveEffect = null,
            )
            MoveDetailsAbi.CFRU_16 -> Gen3MoveDetailsRecord(
                effectId = session.rom.u16le(recordOffset),
                power = session.rom.u16le(recordOffset + POWER_OFFSET),
                typeId = typeId,
                accuracy = accuracy,
                pp = pp,
                secondaryEffectChance = secondaryChance,
                targetMask = session.rom.u8(recordOffset + CFRU_TARGET_OFFSET),
                priority = priority,
                flags = session.rom.u32le(recordOffset + CFRU_FLAGS_OFFSET),
                split = requireNotNull(MoveSplit.fromRaw(requireNotNull(splitRaw))),
                argument = null,
                zMovePower = null,
                zMoveEffect = null,
            )
            MoveDetailsAbi.BATTLE_ENGINE_20 -> Gen3MoveDetailsRecord(
                effectId = session.rom.u16le(recordOffset),
                power = session.rom.u16le(recordOffset + POWER_OFFSET),
                typeId = typeId,
                accuracy = accuracy,
                pp = pp,
                secondaryEffectChance = secondaryChance,
                targetMask = session.rom.u16le(recordOffset + BATTLE_ENGINE_TARGET_OFFSET),
                priority = priority,
                flags = session.rom.u32le(recordOffset + BATTLE_ENGINE_FLAGS_OFFSET),
                split = requireNotNull(MoveSplit.fromRaw(requireNotNull(splitRaw))),
                argument = session.rom.u8(recordOffset + BATTLE_ENGINE_ARGUMENT_OFFSET),
                zMovePower = session.rom.u8(recordOffset + BATTLE_ENGINE_Z_POWER_OFFSET),
                zMoveEffect = session.rom.u8(recordOffset + BATTLE_ENGINE_Z_EFFECT_OFFSET),
            )
            MoveDetailsAbi.UNIFIED_MOVE_INFO_48 -> error("unified MoveInfo rows decode through their packed ABI")
        }
        return MoveDetailsRowOutcome.Decoded(rowIndex, record)
    }

    private fun decodeUnifiedMoveInfoRow(
        session: RomAnalysisSession,
        rowIndex: Int,
        recordOffset: Int,
    ): MoveDetailsRowOutcome {
        val packedMove = session.rom.u16le(recordOffset + UNIFIED_PACKED_MOVE_OFFSET)
        val packedAccuracy = session.rom.u16le(recordOffset + UNIFIED_PACKED_ACCURACY_OFFSET)
        val typeId = packedMove and 0x1F
        val splitRaw = (packedMove ushr 5) and 0x3
        val power = packedMove ushr 7
        val accuracy = packedAccuracy and 0x7F
        val target = packedAccuracy ushr 7
        val pp = session.rom.u8(recordOffset + UNIFIED_PP_OFFSET)
        val priorityBits = session.rom.u32le(recordOffset + UNIFIED_FLAGS_OFFSET).toInt() and 0xF
        val priority = if (priorityBits >= 8) priorityBits - 16 else priorityBits
        val split = MoveSplit.fromRaw(splitRaw)
        val reasons = buildList {
            if (typeId !in 0..MAX_TYPE_ID) add("type value $typeId exceeds $MAX_TYPE_ID")
            if (split == null) add("split value $splitRaw is not physical, special, or status")
            if (accuracy !in 0..MAX_PERCENT) add("accuracy value $accuracy is outside 0..$MAX_PERCENT")
            if (pp !in 0..MAX_PP) add("pp value $pp exceeds $MAX_PP")
        }
        if (reasons.isNotEmpty()) return MoveDetailsRowOutcome.Malformed(rowIndex, reasons)
        return MoveDetailsRowOutcome.Decoded(
            rowIndex = rowIndex,
            record = Gen3MoveDetailsRecord(
                effectId = session.rom.u16le(recordOffset + UNIFIED_EFFECT_OFFSET),
                power = power,
                typeId = typeId,
                accuracy = accuracy,
                pp = pp,
                secondaryEffectChance = 0,
                targetMask = target,
                priority = priority,
                flags = session.rom.u32le(recordOffset + UNIFIED_FLAGS_OFFSET),
                split = requireNotNull(split),
                argument = session.rom.u32le(recordOffset + UNIFIED_ARGUMENT_OFFSET).toInt(),
                zMovePower = null,
                zMoveEffect = null,
            ),
        )
    }

    private companion object {
        const val RETAIL_TYPE_OFFSET = 2
        const val RETAIL_ACCURACY_OFFSET = 3
        const val RETAIL_PP_OFFSET = 4
        const val RETAIL_SECONDARY_CHANCE_OFFSET = 5
        const val EXTENDED_TYPE_OFFSET = 4
        const val EXTENDED_ACCURACY_OFFSET = 5
        const val EXTENDED_PP_OFFSET = 6
        const val EXTENDED_SECONDARY_CHANCE_OFFSET = 7
        const val POWER_OFFSET = 2
        const val RETAIL_TARGET_OFFSET = 6
        const val RETAIL_PRIORITY_OFFSET = 7
        const val RETAIL_FLAGS_OFFSET = 8
        const val CFRU_TARGET_OFFSET = 8
        const val CFRU_PRIORITY_OFFSET = 9
        const val CFRU_SPLIT_OFFSET = 10
        const val CFRU_FLAGS_OFFSET = 12
        const val BATTLE_ENGINE_TARGET_OFFSET = 8
        const val BATTLE_ENGINE_PRIORITY_OFFSET = 10
        const val BATTLE_ENGINE_FLAGS_OFFSET = 12
        const val BATTLE_ENGINE_SPLIT_OFFSET = 16
        const val BATTLE_ENGINE_ARGUMENT_OFFSET = 17
        const val BATTLE_ENGINE_Z_POWER_OFFSET = 18
        const val BATTLE_ENGINE_Z_EFFECT_OFFSET = 19
        const val UNIFIED_EFFECT_OFFSET = 8
        const val UNIFIED_PACKED_MOVE_OFFSET = 10
        const val UNIFIED_PACKED_ACCURACY_OFFSET = 12
        const val UNIFIED_PP_OFFSET = 14
        const val UNIFIED_FLAGS_OFFSET = 16
        const val UNIFIED_ARGUMENT_OFFSET = 24
        const val EXTENDED_PADDING_OFFSET = 11
        const val MAX_TYPE_ID = 31
        const val MAX_PP = 64
        const val MAX_PERCENT = 100
        const val MIN_PERCENT_ACCURACY = 10
        const val ACCURACY_ALWAYS = 0
        const val ACCURACY_ENGINE_DEFINED = 0xFF
        const val CHANCE_ENGINE_DEFINED = 0xFF
        const val MIN_PRIORITY = -8
        const val MAX_PRIORITY = 7
        const val MAX_WIDENED_POWER = 2048
    }
}
