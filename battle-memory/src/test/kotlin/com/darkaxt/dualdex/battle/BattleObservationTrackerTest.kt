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
        tracker.update(
            "rom-a",
            sample(pp = listOf(35, 40), playerPp = 35).copy(commandOwnerBattlerIndex = null),
        )

        val update = tracker.update(
            "rom-a",
            sample(pp = listOf(35, 40), playerPp = 34).copy(commandOwnerBattlerIndex = null),
        )

        assertEquals(
            setOf(BattleMatchupObservation(speciesId = 13, moveId = 10, defendingTypeIds = listOf(6, 3))),
            update.discoveredMatchups,
        )
    }

    @Test
    fun bindsADoubleBattlePpDropToThePriorCommandOwnerAndTarget() {
        val tracker = BattleObservationTracker()
        val first = doubleSample(leftPp = 35, rightPp = 25).copy(
            commandOwnerBattlerIndex = 2,
            selectedMoveId = 11,
            target = BattleTarget(1, TargetMode.AUTOMATIC),
        )
        tracker.update("rom-a", first)

        val update = tracker.update("rom-a", doubleSample(leftPp = 35, rightPp = 24))

        assertEquals(
            setOf(BattleMatchupObservation(speciesId = 16, moveId = 11, defendingTypeIds = listOf(0, 2))),
            update.discoveredMatchups,
        )
    }

    @Test
    fun withholdsDoubleBattleMatchupsWithoutAPriorOwnedCommandPair() {
        val tracker = BattleObservationTracker()
        tracker.update("rom-a", doubleSample(leftPp = 35, rightPp = 25))

        val update = tracker.update("rom-a", doubleSample(leftPp = 34, rightPp = 24))

        assertTrue(update.discoveredMatchups.isEmpty())
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
            commandOwnerBattlerIndex = player?.battlerIndex,
            target = BattleTarget(0, TargetMode.AUTOMATIC),
            capabilities = emptyMap(),
            opponentExecutedMoveId = opponentExecutedMove,
        )
    }

    private fun doubleSample(leftPp: Int, rightPp: Int): BattleMemorySample {
        fun battler(index: Int, position: Int, species: Int, move: Int, pp: Int, types: List<Int>) =
            BattleMonSnapshot(
                battlerIndex = index,
                position = position,
                speciesId = species,
                level = 20,
                hp = 50,
                maxHp = 50,
                ivs = List(6) { 15 },
                moves = listOf(move, 0, 0, 0),
                pp = listOf(pp, 0, 0, 0),
                typeIds = types,
                abilityId = 65,
                personality = (100 + index).toLong(),
            )
        val left = battler(0, 0, 252, 10, leftPp, listOf(11, 11))
        val firstOpponent = battler(1, 1, 13, 40, 35, listOf(6, 3))
        val right = battler(2, 2, 1, 11, rightPp, listOf(11, 11))
        val secondOpponent = battler(3, 3, 16, 40, 35, listOf(0, 2))
        return BattleMemorySample(
            layout = ResolvedBattleLayout(0x1000, 0x0FE4, 0x0FF0, 0x12B2, 0x1438, 0x143C, 4),
            battlers = listOf(left, firstOpponent, right, secondOpponent),
            opponents = listOf(firstOpponent, secondOpponent),
            selectedMoveId = null,
            target = BattleTarget(0, TargetMode.MANUAL_TARGET_FALLBACK),
            capabilities = emptyMap(),
        )
    }
}
