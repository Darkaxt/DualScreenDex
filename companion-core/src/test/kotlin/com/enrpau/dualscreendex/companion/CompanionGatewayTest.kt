package com.enrpau.dualscreendex.companion

import com.enrpau.dualscreendex.companion.model.AppScreen
import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.BattleEncounterKind
import com.enrpau.dualscreendex.companion.model.BattleState
import com.enrpau.dualscreendex.companion.model.BattleTab
import com.enrpau.dualscreendex.companion.model.CompanionAction
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.CatalogLoadingState
import com.enrpau.dualscreendex.companion.model.GameClock
import com.enrpau.dualscreendex.companion.model.GameClockPhase
import com.enrpau.dualscreendex.companion.model.LiveMapPosition
import com.enrpau.dualscreendex.companion.model.OpponentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class CompanionGatewayTest {
    @Test
    fun semanticNoOpPreservesSnapshotVersionAndListenerSilence() {
        val gateway = CompanionGateway()
        val before = gateway.bootstrap()
        var publications = 0
        gateway.subscribe { publications += 1 }

        val after = gateway.dispatch(CompanionAction.SetScreen(AppScreen.POKEDEX))

        assertSame(before, after)
        assertEquals(before.version, after.version)
        assertEquals(0, publications)
        assertEquals(1L, gateway.metrics().dispatchAttempts)
        assertEquals(0L, gateway.metrics().appliedDispatches)
        assertEquals(1L, gateway.metrics().noOpDispatches)
    }

    @Test
    fun partialCatalogProgressNeverPublishesCatalogReady() {
        val gateway = CompanionGateway(AppSnapshot(catalogReady = true))

        val progress = gateway.dispatch(
            CompanionAction.CatalogLoadingChanged(
                CatalogLoadingState(active = true, phase = "RELATIONSHIPS", completedUnits = 3, totalUnits = 5),
                "incoming.gba",
            ),
        )

        assertFalse(progress.catalogReady)
        assertEquals("RELATIONSHIPS", progress.catalogLoading.phase)
        assertEquals(3, progress.catalogLoading.completedUnits)
    }

    @Test
    fun initialBattleTabRequiresOnlyProvenWildAndEnabledRarity() {
        BattleEncounterKind.entries.forEach { kind ->
            listOf(false, true).forEach { enabled ->
                val expected = if (kind == BattleEncounterKind.WILD && enabled) {
                    BattleTab.RARITY
                } else {
                    BattleTab.ENTRY
                }

                assertEquals(expected, initialBattleTab(kind, enabled))
            }
        }
    }

    @Test
    fun battleStartAppliesInitialPolicyOnceAndUpdatesNeverStealManualTab() {
        val gateway = CompanionGateway(AppSnapshot(settings = CompanionSettings(rarityEnabled = true)))
        val first = BattleState(
            opponents = listOf(opponent(1), opponent(2)),
            encounterKind = BattleEncounterKind.WILD,
            rarityUsable = true,
        )

        assertEquals(BattleTab.RARITY, gateway.dispatchBattle(first).battleTab)
        gateway.dispatch(CompanionAction.SetBattleTab(BattleTab.MOVES))

        val secondTarget = first.copy(targetIndex = 1, encounterKind = BattleEncounterKind.TRAINER)
        assertEquals(BattleTab.MOVES, gateway.dispatchBattle(secondTarget).battleTab)
        assertEquals(BattleTab.MOVES, gateway.dispatch(CompanionAction.SelectTarget(1)).battleTab)
    }

    @Test
    fun lateWildClassificationPromotesUntouchedEntryTabToRarity() {
        val gateway = CompanionGateway(AppSnapshot(settings = CompanionSettings(rarityEnabled = true)))
        val pending = BattleState(
            opponents = listOf(opponent(1)),
            encounterKind = BattleEncounterKind.UNKNOWN,
            rarityUsable = true,
        )

        assertEquals(BattleTab.ENTRY, gateway.dispatchBattle(pending).battleTab)

        val classified = pending.copy(encounterKind = BattleEncounterKind.WILD)

        assertEquals(BattleTab.RARITY, gateway.dispatchBattle(classified).battleTab)
    }

    @Test
    fun lateWildClassificationDoesNotOverrideAnExplicitEntrySelection() {
        val gateway = CompanionGateway(AppSnapshot(settings = CompanionSettings(rarityEnabled = true)))
        val pending = BattleState(
            opponents = listOf(opponent(1)),
            encounterKind = BattleEncounterKind.UNKNOWN,
            rarityUsable = true,
        )
        gateway.dispatchBattle(pending)
        gateway.dispatch(CompanionAction.SetBattleTab(BattleTab.ENTRY))

        val classified = pending.copy(encounterKind = BattleEncounterKind.WILD)

        assertEquals(BattleTab.ENTRY, gateway.dispatchBattle(classified).battleTab)
    }

    @Test
    fun eachNewBattleLifecycleReappliesTheFailClosedInitialPolicy() {
        val gateway = CompanionGateway(AppSnapshot(settings = CompanionSettings(rarityEnabled = true)))
        val wild = BattleState(emptyList(), encounterKind = BattleEncounterKind.WILD, rarityUsable = true)
        gateway.dispatchBattle(wild)
        gateway.dispatch(CompanionAction.SetBattleTab(BattleTab.ATTACK))
        gateway.dispatchBattle(null)

        val trainer = wild.copy(encounterKind = BattleEncounterKind.TRAINER)

        assertEquals(BattleTab.ENTRY, gateway.dispatchBattle(trainer).battleTab)
    }

    private fun opponent(speciesId: Int) = OpponentState(
        speciesId = speciesId,
        level = 5,
        moveHistory = emptyList(),
    )

    @Test
    fun areaPokedexNavigationLeavesDetailAndPreservesEverySelectedEncounterArea() {
        val gateway = CompanionGateway()
        gateway.dispatch(CompanionAction.OpenSpecies(25))

        val browse = gateway.dispatch(CompanionAction.OpenAreaPokedex(setOf(172, 111, 171)))

        assertEquals(AppScreen.POKEDEX, browse.screen)
        assertEquals(com.enrpau.dualscreendex.companion.model.PokedexFilter.AREA, browse.filter)
        assertEquals(111, browse.selectedAreaId)
        assertEquals(setOf(111, 171, 172), browse.selectedAreaIds)
        assertEquals(25, browse.selectedSpeciesId)
    }

    @Test
    fun snapshotsAdvanceMonotonicallyAndReturnToBrowse() {
        val gateway = CompanionGateway()
        val detail = gateway.dispatch(CompanionAction.OpenSpecies(25))
        val browse = gateway.dispatch(CompanionAction.BackToPokedex)

        assertEquals(1, detail.version)
        assertEquals(2, browse.version)
        assertEquals(AppScreen.POKEDEX, browse.screen)
        assertEquals(25, browse.selectedSpeciesId)
    }

    @Test
    fun backWalksEveryVisitedScreenWithoutCreatingSelfLoops() {
        val gateway = CompanionGateway()
        gateway.dispatch(CompanionAction.OpenSpecies(25))
        gateway.dispatch(CompanionAction.OpenTrainer)
        gateway.dispatch(CompanionAction.OpenTrainer)
        gateway.dispatch(CompanionAction.OpenParty)

        assertEquals(AppScreen.TRAINER, gateway.dispatch(CompanionAction.BackToPokedex).screen)
        assertEquals(AppScreen.DETAIL, gateway.dispatch(CompanionAction.BackToPokedex).screen)
        assertEquals(AppScreen.POKEDEX, gateway.dispatch(CompanionAction.BackToPokedex).screen)
        assertEquals(AppScreen.POKEDEX, gateway.dispatch(CompanionAction.BackToPokedex).screen)
    }

    @Test
    fun battleInterruptionUsesItsReturnScreenWithoutPollutingOrdinaryHistory() {
        val gateway = CompanionGateway()
        gateway.dispatch(CompanionAction.OpenSpecies(25))
        gateway.dispatch(CompanionAction.OpenTrainer)
        gateway.dispatchBattle(BattleState(emptyList()))

        assertEquals(AppScreen.TRAINER, gateway.dispatchBattle(null).screen)
        assertEquals(AppScreen.DETAIL, gateway.dispatch(CompanionAction.BackToPokedex).screen)
        assertEquals(AppScreen.POKEDEX, gateway.dispatch(CompanionAction.BackToPokedex).screen)
    }

    @Test
    fun trainerAndPartyShortcutsPreserveTheirReturnScreenAndSelectedSlot() {
        val gateway = CompanionGateway()
        gateway.dispatch(CompanionAction.OpenSpecies(25))

        val trainer = gateway.dispatch(CompanionAction.OpenTrainer)
        val returned = gateway.dispatch(CompanionAction.BackToPokedex)
        val party = gateway.dispatch(CompanionAction.OpenPartyMember(4))

        assertEquals(AppScreen.TRAINER, trainer.screen)
        assertEquals(AppScreen.DETAIL, trainer.priorScreen)
        assertEquals(AppScreen.DETAIL, returned.screen)
        assertEquals(AppScreen.PARTY, party.screen)
        assertEquals(4, party.selectedPartySlot)
    }

    @Test
    fun settingsReturnToBattleWithoutLosingItsOutOfBattleDestination() {
        val gateway = CompanionGateway()
        gateway.dispatchBattle(BattleState(emptyList()))

        val settings = gateway.dispatch(CompanionAction.SetScreen(AppScreen.SETTINGS))
        val returned = gateway.dispatch(CompanionAction.SetScreen(AppScreen.BATTLE))
        val ended = gateway.dispatchBattle(null)

        assertEquals(AppScreen.POKEDEX, settings.priorScreen)
        assertEquals(AppScreen.BATTLE, settings.settingsReturnScreen)
        assertEquals(AppScreen.BATTLE, returned.screen)
        assertEquals(AppScreen.POKEDEX, ended.screen)
    }

    @Test
    fun detailShortcutReturnsToTheActiveBattle() {
        val gateway = CompanionGateway()
        gateway.dispatchBattle(BattleState(emptyList()))
        val detail = gateway.dispatch(CompanionAction.OpenSpecies(25))

        val returned = gateway.dispatch(CompanionAction.BackToPokedex)

        assertEquals(AppScreen.BATTLE, detail.priorScreen)
        assertEquals(AppScreen.BATTLE, returned.screen)
    }

    @Test
    fun liveBattleUpdatesAndBattleEndPreserveAManuallyOpenedPokedexDetail() {
        val gateway = CompanionGateway()
        gateway.dispatchBattle(BattleState(emptyList()))
        gateway.dispatch(CompanionAction.OpenSpecies(25))

        val updated = gateway.dispatchBattle(BattleState(emptyList()))
        val ended = gateway.dispatchBattle(null)
        val returned = gateway.dispatch(CompanionAction.BackToPokedex)

        assertEquals(AppScreen.DETAIL, updated.screen)
        assertEquals(AppScreen.DETAIL, ended.screen)
        assertEquals(AppScreen.POKEDEX, returned.screen)
    }

    @Test
    fun resolvedOverworldAtomicallyReplacesAreaAndPosition() {
        val gateway = CompanionGateway()
        gateway.dispatchResolved(
            areaBaseId = 0x0010,
            position = LiveMapPosition(12, 7),
            gameTime = null,
            gameAccessReady = false,
        )

        val sameArea = gateway.bootstrap()
        val nextArea = gateway.dispatchResolved(
            areaBaseId = 0x0011,
            position = null,
            gameTime = null,
            gameAccessReady = false,
        )

        assertEquals(LiveMapPosition(12, 7), sameArea.liveMapPosition)
        assertEquals(null, nextArea.liveMapPosition)
    }

    @Test
    fun startingACatalogTransitionClearsTheResolvedOverworldSession() {
        val gateway = CompanionGateway()
        gateway.dispatchResolved(
            areaBaseId = 0x0010,
            position = LiveMapPosition(12, 7),
            gameTime = GameClock(9, 30, GameClockPhase.DAY),
            gameAccessReady = true,
        )

        val loading = gateway.dispatch(
            CompanionAction.CatalogLoadingChanged(
                CatalogLoadingState(active = true, phase = "CACHE_REOPEN", completedUnits = 0, totalUnits = 1),
            ),
        )

        assertEquals(null, loading.liveAreaBaseId)
        assertEquals(null, loading.liveMapPosition)
        assertEquals(null, loading.gameTime)
        assertFalse(loading.gameAccessReady)
    }

    @Test
    fun phaseOnlyGameClockChangesWithoutClearingMapState() {
        val gateway = CompanionGateway()
        gateway.dispatchResolved(
            areaBaseId = 0x0010,
            position = LiveMapPosition(12, 7),
            gameTime = null,
            gameAccessReady = false,
        )

        val night = gateway.dispatchResolved(
            areaBaseId = 0x0010,
            position = LiveMapPosition(12, 7),
            gameTime = GameClock(phase = GameClockPhase.NIGHT),
            gameAccessReady = false,
        )
        val disconnected = gateway.dispatchResolved(
            areaBaseId = 0x0010,
            position = LiveMapPosition(12, 7),
            gameTime = null,
            gameAccessReady = false,
        )

        assertEquals(GameClockPhase.NIGHT, night.gameTime?.phase)
        assertEquals(null, night.gameTime?.hours)
        assertEquals(0x0010, night.liveAreaBaseId)
        assertEquals(LiveMapPosition(12, 7), night.liveMapPosition)
        assertEquals(null, disconnected.gameTime)
        assertEquals(0x0010, disconnected.liveAreaBaseId)
    }

    @Test
    fun battleEndDoesNotDiscardTheContinuouslySampledLiveArea() {
        val gateway = CompanionGateway()
        gateway.dispatchResolved(
            areaBaseId = 0x0010,
            position = null,
            gameTime = null,
            gameAccessReady = false,
        )
        gateway.dispatchBattle(BattleState(emptyList()))

        val ended = gateway.dispatchBattle(null)

        assertEquals(0x0010, ended.liveAreaBaseId)
    }

    private fun CompanionGateway.dispatchBattle(battle: BattleState?): AppSnapshot =
        dispatchResolved(battle = battle)

    private fun CompanionGateway.dispatchResolved(
        areaBaseId: Int? = bootstrap().liveAreaBaseId,
        position: LiveMapPosition? = bootstrap().liveMapPosition,
        gameTime: GameClock? = bootstrap().gameTime,
        gameAccessReady: Boolean = bootstrap().gameAccessReady,
        battle: BattleState? = bootstrap().battle,
    ): AppSnapshot {
        val current = bootstrap()
        return dispatch(
            CompanionAction.ResolvedGameStateChanged(
                trainerCard = current.trainerCardState,
                pokedex = current.resolvedPokedex
                    ?: com.enrpau.dualscreendex.companion.model.ResolvedPokedexProjection(null, null),
                party = current.party,
                owned = current.resolvedOwned.orEmpty(),
                bag = current.resolvedBag,
                eventFlags = current.resolvedEventFlags,
                areaBaseId = areaBaseId,
                position = position,
                gameTime = gameTime,
                gameAccessReady = gameAccessReady,
                battle = battle,
                ledger = current.ledger,
            ),
        )
    }
}
