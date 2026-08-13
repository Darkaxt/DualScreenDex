package com.enrpau.dualscreendex.companion

import com.enrpau.dualscreendex.companion.model.AppScreen
import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.BattleEncounterKind
import com.enrpau.dualscreendex.companion.model.BattleState
import com.enrpau.dualscreendex.companion.model.BattleTab
import com.enrpau.dualscreendex.companion.model.CompanionAction
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.OpponentState
import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionGatewayTest {
    @Test
    fun initialBattleTabRequiresProvenWildEnabledAndUsableRarity() {
        BattleEncounterKind.entries.forEach { kind ->
            listOf(false, true).forEach { enabled ->
                listOf(false, true).forEach { usable ->
                    val expected = if (kind == BattleEncounterKind.WILD && enabled && usable) {
                        BattleTab.RARITY
                    } else {
                        BattleTab.ENTRY
                    }

                    assertEquals(expected, initialBattleTab(kind, enabled, usable))
                }
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

        assertEquals(BattleTab.RARITY, gateway.dispatch(CompanionAction.BattleStarted(first)).battleTab)
        gateway.dispatch(CompanionAction.SetBattleTab(BattleTab.MOVES))

        val secondTarget = first.copy(targetIndex = 1, encounterKind = BattleEncounterKind.TRAINER)
        assertEquals(BattleTab.MOVES, gateway.dispatch(CompanionAction.BattleUpdated(secondTarget)).battleTab)
        assertEquals(BattleTab.MOVES, gateway.dispatch(CompanionAction.SelectTarget(1)).battleTab)
    }

    @Test
    fun eachNewBattleLifecycleReappliesTheFailClosedInitialPolicy() {
        val gateway = CompanionGateway(AppSnapshot(settings = CompanionSettings(rarityEnabled = true)))
        val wild = BattleState(emptyList(), encounterKind = BattleEncounterKind.WILD, rarityUsable = true)
        gateway.dispatch(CompanionAction.BattleStarted(wild))
        gateway.dispatch(CompanionAction.SetBattleTab(BattleTab.ATTACK))
        gateway.dispatch(CompanionAction.BattleEnded)

        val trainer = wild.copy(encounterKind = BattleEncounterKind.TRAINER)

        assertEquals(BattleTab.ENTRY, gateway.dispatch(CompanionAction.BattleStarted(trainer)).battleTab)
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
    fun settingsReturnToBattleWithoutLosingItsOutOfBattleDestination() {
        val gateway = CompanionGateway()
        gateway.dispatch(CompanionAction.BattleStarted(BattleState(emptyList())))

        val settings = gateway.dispatch(CompanionAction.SetScreen(AppScreen.SETTINGS))
        val returned = gateway.dispatch(CompanionAction.SetScreen(AppScreen.BATTLE))
        val ended = gateway.dispatch(CompanionAction.BattleEnded)

        assertEquals(AppScreen.POKEDEX, settings.priorScreen)
        assertEquals(AppScreen.BATTLE, settings.settingsReturnScreen)
        assertEquals(AppScreen.BATTLE, returned.screen)
        assertEquals(AppScreen.POKEDEX, ended.screen)
    }

    @Test
    fun detailShortcutReturnsToTheActiveBattle() {
        val gateway = CompanionGateway()
        gateway.dispatch(CompanionAction.BattleStarted(BattleState(emptyList())))
        val detail = gateway.dispatch(CompanionAction.OpenSpecies(25))

        val returned = gateway.dispatch(CompanionAction.BackToPokedex)

        assertEquals(AppScreen.BATTLE, detail.priorScreen)
        assertEquals(AppScreen.BATTLE, returned.screen)
    }

    @Test
    fun liveBattleUpdatesAndBattleEndPreserveAManuallyOpenedPokedexDetail() {
        val gateway = CompanionGateway()
        gateway.dispatch(CompanionAction.BattleStarted(BattleState(emptyList())))
        gateway.dispatch(CompanionAction.OpenSpecies(25))

        val updated = gateway.dispatch(CompanionAction.BattleUpdated(BattleState(emptyList())))
        val ended = gateway.dispatch(CompanionAction.BattleEnded)
        val returned = gateway.dispatch(CompanionAction.BackToPokedex)

        assertEquals(AppScreen.DETAIL, updated.screen)
        assertEquals(AppScreen.DETAIL, ended.screen)
        assertEquals(AppScreen.POKEDEX, returned.screen)
    }

    @Test
    fun battleEndDoesNotDiscardTheContinuouslySampledLiveArea() {
        val gateway = CompanionGateway()
        gateway.dispatch(CompanionAction.LiveAreaChanged(0x0010))
        gateway.dispatch(CompanionAction.BattleStarted(BattleState(emptyList())))

        val ended = gateway.dispatch(CompanionAction.BattleEnded)

        assertEquals(0x0010, ended.liveAreaBaseId)
    }
}
