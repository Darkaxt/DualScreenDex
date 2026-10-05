package com.enrpau.dualscreendex.parser.dataset.core.basestats

import com.enrpau.dualscreendex.parser.analysis.ExtentCheck
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.BaseStats

fun interface BaseStatsTableDecoder {
    fun decode(
        session: RomAnalysisSession,
        layout: BaseStatsTableLayout,
    ): BaseStatsTableOutcome
}

/** Sole byte-level interpreter for ordinary fixed-width Gen III base-stat records. */
class BaseStatsCodec : BaseStatsTableDecoder {
    override fun decode(
        session: RomAnalysisSession,
        layout: BaseStatsTableLayout,
    ): BaseStatsTableOutcome {
        session.cancellation.throwIfCancellationRequested()
        val checked = when (
            val extent = session.limits.checkTableExtent(
                offset = layout.offset,
                count = layout.count,
                recordSize = layout.abi.recordSize.toLong(),
                romSize = session.rom.size.toLong(),
            )
        ) {
            is ExtentCheck.Valid -> extent.extent
            is ExtentCheck.Invalid -> return BaseStatsTableOutcome.Rejected(layout, extent.reason)
            is ExtentCheck.BudgetExceeded -> return BaseStatsTableOutcome.ExtentBudgetExceeded(
                layout = layout,
                observedBytes = extent.observedBytes,
                limitBytes = extent.limitBytes,
                reason = "base-stat table extent ${extent.observedBytes} exceeds deterministic budget " +
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
        return BaseStatsTableOutcome.Decoded(layout, rows)
    }

    private fun decodeRow(
        session: RomAnalysisSession,
        abi: BaseStatsAbi,
        rowIndex: Int,
        recordOffset: Int,
    ): BaseStatsRowOutcome {
        session.cancellation.throwIfCancellationRequested()
        val bytes = session.rom.slice(recordOffset, abi.recordSize)
        if (bytes.all { it == 0.toByte() }) {
            return BaseStatsRowOutcome.StructuralEmpty(rowIndex)
        }

        val wide = abi == BaseStatsAbi.WIDE_STATS_64
        val typeAdjustment = if (wide) WIDE_TYPE_ADJUSTMENT else 0
        val tailAdjustment = if (wide) WIDE_TAIL_ADJUSTMENT else 0
        val stats = (0 until STAT_COUNT).map {
            if (wide) session.rom.u16le(recordOffset + it * 2) else session.rom.u8(recordOffset + it)
        }
        val types = listOf(
            session.rom.u8(recordOffset + PRIMARY_TYPE_OFFSET + typeAdjustment),
            session.rom.u8(recordOffset + SECONDARY_TYPE_OFFSET + typeAdjustment),
        )
        val reasons = buildList {
            stats.forEachIndexed { index, value ->
                if (value == 0) add("base-stat field $index is zero")
            }
            types.forEachIndexed { index, value ->
                if (value !in 0..MAX_TYPE_ID) add("type field $index value $value exceeds $MAX_TYPE_ID")
            }
        }
        if (reasons.isNotEmpty()) return BaseStatsRowOutcome.Malformed(rowIndex, reasons)

        val abilityIds = when (abi) {
            BaseStatsAbi.RETAIL_28 -> listOf(
                session.rom.u8(recordOffset + ABILITY_OFFSET),
                session.rom.u8(recordOffset + ABILITY_OFFSET + 1),
            )
            BaseStatsAbi.BATTLE_ENGINE_32 -> (0 until BATTLE_ENGINE_ABILITY_SLOTS).map { slot ->
                session.rom.u16le(recordOffset + ABILITY_OFFSET + slot * 2)
            }
            BaseStatsAbi.WIDE_STATS_64 -> (0 until WIDE_ABILITY_SLOTS).map { slot ->
                session.rom.u16le(recordOffset + ABILITY_OFFSET + tailAdjustment + slot * 2)
            }
        }.filter { it != 0 }.distinct()
        val safariOffset = when (abi) {
            BaseStatsAbi.RETAIL_28 -> RETAIL_SAFARI_FLEE_OFFSET
            BaseStatsAbi.BATTLE_ENGINE_32 -> BATTLE_ENGINE_SAFARI_FLEE_OFFSET
            BaseStatsAbi.WIDE_STATS_64 -> WIDE_SAFARI_FLEE_OFFSET
        }
        val bodyOffset = safariOffset + 1
        val packedBody = session.rom.u8(recordOffset + bodyOffset)
        return BaseStatsRowOutcome.Decoded(
            rowIndex = rowIndex,
            record = Gen3BaseStatsRecord(
                stats = BaseStats(
                    hp = stats[0],
                    attack = stats[1],
                    defense = stats[2],
                    speed = stats[3],
                    specialAttack = stats[4],
                    specialDefense = stats[5],
                ),
                typeIds = types,
                catchRate = session.rom.u8(recordOffset + CATCH_RATE_OFFSET + typeAdjustment),
                baseExperienceYield = if (wide) {
                    session.rom.u16le(recordOffset + WIDE_EXP_YIELD_OFFSET)
                } else {
                    session.rom.u8(recordOffset + BASE_EXP_YIELD_OFFSET)
                },
                evYield = session.rom.u16le(recordOffset + EV_YIELD_OFFSET + tailAdjustment),
                heldItemIds = listOf(
                    session.rom.u16le(recordOffset + COMMON_ITEM_OFFSET + tailAdjustment),
                    session.rom.u16le(recordOffset + RARE_ITEM_OFFSET + tailAdjustment),
                ),
                genderRatio = session.rom.u8(recordOffset + GENDER_RATIO_OFFSET + tailAdjustment),
                eggCycles = session.rom.u8(recordOffset + EGG_CYCLES_OFFSET + tailAdjustment),
                baseFriendship = session.rom.u8(recordOffset + BASE_FRIENDSHIP_OFFSET + tailAdjustment),
                growthRate = session.rom.u8(recordOffset + GROWTH_RATE_OFFSET + tailAdjustment),
                eggGroupIds = listOf(
                    session.rom.u8(recordOffset + EGG_GROUP_1_OFFSET + tailAdjustment),
                    session.rom.u8(recordOffset + EGG_GROUP_2_OFFSET + tailAdjustment),
                ),
                abilityIds = abilityIds,
                safariZoneFleeRate = session.rom.u8(recordOffset + safariOffset),
                bodyColor = packedBody and BODY_COLOR_MASK,
                noFlip = packedBody and NO_FLIP_MASK != 0,
            ),
        )
    }

    private companion object {
        const val STAT_COUNT = 6
        const val PRIMARY_TYPE_OFFSET = 6
        const val SECONDARY_TYPE_OFFSET = 7
        const val MAX_TYPE_ID = 31
        const val CATCH_RATE_OFFSET = 8
        const val BASE_EXP_YIELD_OFFSET = 9
        const val EV_YIELD_OFFSET = 10
        const val COMMON_ITEM_OFFSET = 12
        const val RARE_ITEM_OFFSET = 14
        const val GENDER_RATIO_OFFSET = 16
        const val EGG_CYCLES_OFFSET = 17
        const val BASE_FRIENDSHIP_OFFSET = 18
        const val GROWTH_RATE_OFFSET = 19
        const val EGG_GROUP_1_OFFSET = 20
        const val EGG_GROUP_2_OFFSET = 21
        const val ABILITY_OFFSET = 22
        const val BATTLE_ENGINE_ABILITY_SLOTS = 3
        const val RETAIL_SAFARI_FLEE_OFFSET = 24
        const val BATTLE_ENGINE_SAFARI_FLEE_OFFSET = 28
        const val WIDE_TYPE_ADJUSTMENT = 6
        const val WIDE_TAIL_ADJUSTMENT = 8
        const val WIDE_EXP_YIELD_OFFSET = 16
        const val WIDE_ABILITY_SLOTS = 4
        const val WIDE_SAFARI_FLEE_OFFSET = 38
        const val BODY_COLOR_MASK = 0x7F
        const val NO_FLIP_MASK = 0x80
    }
}
