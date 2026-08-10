package com.darkaxt.dualdex.battle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleObservationTrackerTest {
    @Test
    fun recordsOnlyOpponentPpDropsAsCountDeltas() {
        val tracker = BattleObservationTracker()

        assertTrue(tracker.update("rom-a", sample(pp = listOf(35, 40))).observations.isEmpty())
        assertEquals(
            mapOf(13 to mapOf(40 to 1, 81 to 2)),
            tracker.update("rom-a", sample(pp = listOf(34, 38))).observations,
        )
        assertTrue(tracker.update("rom-a", sample(pp = listOf(34, 38))).observations.isEmpty())
    }

    @Test
    fun ppIncreasesAndOpponentSwitchesResetBaselinesWithoutInventingUses() {
        val tracker = BattleObservationTracker()
        tracker.update("rom-a", sample(pp = listOf(30, 30)))

        assertTrue(tracker.update("rom-a", sample(pp = listOf(35, 40))).observations.isEmpty())
        assertTrue(tracker.update("rom-a", sample(pp = listOf(34, 39), personality = 999)).observations.isEmpty())
        assertEquals(
            mapOf(13 to mapOf(40 to 1)),
            tracker.update("rom-a", sample(pp = listOf(33, 39), personality = 999)).observations,
        )
    }

    @Test
    fun missedReadsRetainBattleButTwoValidatedNonBattleSamplesCloseIt() {
        val tracker = BattleObservationTracker()
        tracker.update("rom-a", sample(pp = listOf(35, 40)))

        assertTrue(tracker.missed().active)
        assertTrue(tracker.validatedNoBattle("rom-a").active)
        val ended = tracker.validatedNoBattle("rom-a")
        assertFalse(ended.active)
        assertTrue(ended.ended)
        assertNull(ended.sample)
    }

    @Test
    fun changingRomsClearsBattleAndPpBaselines() {
        val tracker = BattleObservationTracker()
        tracker.update("rom-a", sample(pp = listOf(35, 40)))
        tracker.update("rom-a", sample(pp = listOf(34, 40)))

        val firstOtherRom = tracker.update("rom-b", sample(pp = listOf(33, 40)))

        assertTrue(firstOtherRom.observations.isEmpty())
        assertTrue(firstOtherRom.active)
    }

    @Test
    fun recordsThePlayerMoveAndTargetTypesAfterPpConsumption() {
        val tracker = BattleObservationTracker()
        tracker.update("rom-a", sample(pp = listOf(35, 40), playerPp = 35))

        val update = tracker.update("rom-a", sample(pp = listOf(35, 40), playerPp = 34))

        assertEquals(
            setOf(BattleMatchupObservation(speciesId = 13, moveId = 10, defendingTypeIds = listOf(6, 3))),
            update.discoveredMatchups,
        )
    }

    @Test
    fun countsGen1ExecutedMoveLatchEdgesWhenEnemyPpDoesNotDecrease() {
        val tracker = BattleObservationTracker()
        tracker.update("rom-a", sample(pp = listOf(35, 40)))

        assertEquals(
            mapOf(13 to mapOf(40 to 1)),
            tracker.update("rom-a", sample(pp = listOf(35, 40), opponentExecutedMove = 40)).observations,
        )
        assertTrue(tracker.update("rom-a", sample(pp = listOf(35, 40), opponentExecutedMove = 40)).observations.isEmpty())
        tracker.update("rom-a", sample(pp = listOf(35, 40)))
        assertEquals(
            mapOf(13 to mapOf(40 to 1)),
            tracker.update("rom-a", sample(pp = listOf(35, 40), opponentExecutedMove = 40)).observations,
        )
    }

    private fun sample(
        pp: List<Int>,
        personality: Long = 200,
        playerPp: Int? = null,
        opponentExecutedMove: Int? = null,
    ): BattleMemorySample {
        val opponent = BattleMonSnapshot(
            battlerIndex = 1,
            position = 1,
            speciesId = 13,
            level = 3,
            hp = 15,
            maxHp = 15,
            ivs = List(6) { 15 },
            moves = listOf(40, 81, 0, 0),
            pp = pp + listOf(0, 0),
            typeIds = listOf(6, 3),
            abilityId = 19,
            personality = personality,
        )
        val player = playerPp?.let {
            BattleMonSnapshot(
                battlerIndex = 0, position = 0, speciesId = 252, level = 7, hp = 22, maxHp = 22,
                ivs = List(6) { 15 }, moves = listOf(10, 0, 0, 0), pp = listOf(it, 0, 0, 0),
                typeIds = listOf(11, 11), abilityId = 65, personality = 100,
            )
        }
        return BattleMemorySample(
            layout = ResolvedBattleLayout(0x1000, 0x0FE4, 0x0FF0, 0x12B2, 0x1438, 0x143C, 2),
            battlers = listOfNotNull(player, opponent),
            opponents = listOf(opponent),
            selectedMoveId = 10,
            target = BattleTarget(0, TargetMode.AUTOMATIC),
            capabilities = emptyMap(),
            opponentExecutedMoveId = opponentExecutedMove,
        )
    }
}
