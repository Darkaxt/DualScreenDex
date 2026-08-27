package com.darkaxt.dualdex.progress

import com.darkaxt.dualdex.battle.BattleEncounterKind
import com.darkaxt.dualdex.battle.LiveBattleState
import com.darkaxt.dualdex.battle.LiveClockState
import com.darkaxt.dualdex.live.RecoveryState
import com.darkaxt.dualdex.live.ResolvedBattleKnowledge
import com.darkaxt.dualdex.live.ResolvedGameSnapshot
import com.darkaxt.dualdex.live.ResolvedLocationState
import com.darkaxt.dualdex.live.ResolvedOwnedStorageState
import com.darkaxt.dualdex.live.ResolvedPokedexState
import com.darkaxt.dualdex.live.ResolvedTrainerState
import com.darkaxt.dualdex.live.ResolvedValue
import com.darkaxt.dualdex.save.BagPocket
import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveObservationKind
import com.darkaxt.dualdex.save.TrainerIdentity
import com.darkaxt.dualdex.save.TrainerPlayTime
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ResolvedSemanticFactProjectorTest {
    @Test
    fun `semantic facts ignore whether accepted values came from live memory or recovery`() {
        val live = snapshot(live = true)
        val recovery = snapshot(live = false)
        val ledger = KnowledgeLedger(identifiedPoiKeys = setOf("sign:1"))

        assertEquals(
            ResolvedSemanticFactProjector.project(live, ledger, 3),
            ResolvedSemanticFactProjector.project(recovery, ledger, 3),
        )
        assertNotNull(ResolvedSemanticFactProjector.project(live, ledger, 3))
    }

    private fun snapshot(live: Boolean): ResolvedGameSnapshot {
        fun <T> resolved(value: T) = if (live) ResolvedValue.live(value) else ResolvedValue.recovery(value)
        val unavailable = ResolvedValue.unavailable<String>()
        return ResolvedGameSnapshot(
            romIdentity = "a".repeat(64), generation = 3, sampleId = 1,
            trainer = ResolvedTrainerState(
                resolved(TrainerIdentity("MAY", 1)), resolved(1), resolved(3000L),
                resolved(TrainerPlayTime(1, 2)), resolved(0), resolved(0),
            ),
            pokedex = ResolvedPokedexState(resolved(setOf(25)), resolved(setOf(25))),
            ownedStorage = ResolvedOwnedStorageState(
                party = resolved(listOf(OwnedIndividual("party:0", 25))),
                boxes = resolved(emptyList()),
            ),
            battle = resolved(LiveBattleState(false, null, BattleEncounterKind.UNKNOWN)),
            battleKnowledge = ResolvedBattleKnowledge(),
            location = ResolvedLocationState(resolved(0x0101), ResolvedValue.unavailable()),
            clock = resolved(LiveClockState(1, 2, 3)),
            bag = BagPocket.entries.associateWith { ResolvedValue.unavailable() },
            eventFlags = resolved(emptySet()),
            levelUpRulesetId = unavailable,
            recovery = RecoveryState(
                saveIdentity = "b".repeat(64),
                observationKind = SaveObservationKind.UNCHANGED,
                saveFileFingerprint = "c".repeat(64),
            ),
        )
    }
}
