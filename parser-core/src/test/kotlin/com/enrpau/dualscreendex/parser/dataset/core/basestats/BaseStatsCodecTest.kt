package com.enrpau.dualscreendex.parser.dataset.core.basestats

import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.catalog.BaseStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseStatsCodecTest {
    private val codec = BaseStatsCodec()

    @Test
    fun decodesTheExactRetailTwentyEightByteAbi() {
        val bytes = ByteArray(96)
        putRetailBaseStats(bytes, 16)

        val outcome = codec.decode(
            baseStatsSession(bytes),
            BaseStatsTableLayout(16, 1, BaseStatsAbi.RETAIL_28),
        ) as BaseStatsTableOutcome.Decoded
        val row = outcome.rows.single() as BaseStatsRowOutcome.Decoded

        assertEquals(BaseStats(45, 49, 49, 45, 65, 65), row.record.stats)
        assertEquals(listOf(12, 3), row.record.typeIds)
        assertEquals(listOf(7, 9), row.record.abilityIds)
        assertEquals(4, row.record.growthRate)
        assertEquals(45, row.record.catchRate)
        assertEquals(64, row.record.baseExperienceYield)
        assertEquals(0x056A, row.record.evYield)
        assertEquals(listOf(13, 14), row.record.heldItemIds)
        assertEquals(127, row.record.genderRatio)
        assertEquals(20, row.record.eggCycles)
        assertEquals(70, row.record.baseFriendship)
        assertEquals(listOf(1, 7), row.record.eggGroupIds)
        assertEquals(5, row.record.safariZoneFleeRate)
        assertEquals(6, row.record.bodyColor)
        assertTrue(row.record.noFlip)
    }

    @Test
    fun decodesTheBattleEngineThirtyTwoByteThreeU16AbilityAbi() {
        val bytes = ByteArray(96)
        putBattleEngineBaseStats(bytes, 16, listOf(9, 0, 145))

        val row = ((codec.decode(
            baseStatsSession(bytes),
            BaseStatsTableLayout(16, 1, BaseStatsAbi.BATTLE_ENGINE_32),
        ) as BaseStatsTableOutcome.Decoded).rows.single() as BaseStatsRowOutcome.Decoded)

        assertEquals(listOf(9, 145), row.record.abilityIds)
        assertEquals(6, row.record.safariZoneFleeRate)
        assertEquals(4, row.record.bodyColor)
    }

    @Test
    fun omitsZeroAndDuplicateAbilitySlotsWithoutChangingTheirWidth() {
        val retail = ByteArray(64)
        putRetailBaseStats(retail, 0, abilities = 7 to 7)
        val battle = ByteArray(64)
        putBattleEngineBaseStats(battle, 0, listOf(260, 0, 260))

        val retailRow = decodedRow(retail, BaseStatsAbi.RETAIL_28)
        val battleRow = decodedRow(battle, BaseStatsAbi.BATTLE_ENGINE_32)

        assertEquals(listOf(7), retailRow.record.abilityIds)
        assertEquals(listOf(260), battleRow.record.abilityIds)
    }

    @Test
    fun retainsExactZeroRowsAndNonzeroMalformedRowsAsDifferentOutcomes() {
        val bytes = ByteArray(3 * 28)
        putRetailBaseStats(bytes, 28)
        bytes[2 * 28 + 8] = 1

        val outcome = codec.decode(
            baseStatsSession(bytes),
            BaseStatsTableLayout(0, 3, BaseStatsAbi.RETAIL_28),
        ) as BaseStatsTableOutcome.Decoded

        assertTrue(outcome.rows[0] is BaseStatsRowOutcome.StructuralEmpty)
        assertTrue(outcome.rows[1] is BaseStatsRowOutcome.Decoded)
        assertTrue(outcome.rows[2] is BaseStatsRowOutcome.Malformed)
    }

    @Test
    fun reportsExtentBudgetsAndRejectsInvalidLongCardinalityWithoutReading() {
        val bytes = ByteArray(128)
        val budget = codec.decode(
            baseStatsSession(bytes, limits = ResolutionLimits(maxDatasetExtentBytes = 31)),
            BaseStatsTableLayout(0, 1, BaseStatsAbi.BATTLE_ENGINE_32),
        )
        val overflow = codec.decode(
            baseStatsSession(bytes),
            BaseStatsTableLayout(Long.MAX_VALUE - 8, Long.MAX_VALUE, BaseStatsAbi.RETAIL_28),
        )

        assertTrue(budget is BaseStatsTableOutcome.ExtentBudgetExceeded)
        assertTrue(overflow is BaseStatsTableOutcome.Rejected)
        assertTrue((overflow as BaseStatsTableOutcome.Rejected).reason.contains("overflow"))
    }

    private fun decodedRow(bytes: ByteArray, abi: BaseStatsAbi): BaseStatsRowOutcome.Decoded =
        ((codec.decode(
            baseStatsSession(bytes),
            BaseStatsTableLayout(0, 1, abi),
        ) as BaseStatsTableOutcome.Decoded).rows.single() as BaseStatsRowOutcome.Decoded)
}
