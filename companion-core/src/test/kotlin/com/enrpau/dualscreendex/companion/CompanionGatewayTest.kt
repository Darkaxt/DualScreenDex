package com.enrpau.dualscreendex.companion

import com.enrpau.dualscreendex.companion.model.AppScreen
import com.enrpau.dualscreendex.companion.model.BattleState
import com.enrpau.dualscreendex.companion.model.CompanionAction
import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionGatewayTest {
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

    @Test
    fun areaDexRoundTripReturnsToPokemonDetailAndRestoresNormalDetailBack() {
        val gateway = CompanionGateway()
        gateway.dispatch(CompanionAction.OpenSpecies(25))

        val browse = gateway.dispatch(CompanionAction.OpenAreaPokedex(101))
        val detail = gateway.dispatch(CompanionAction.BackToPokedex)
        val returned = gateway.dispatch(CompanionAction.BackToPokedex)

        assertEquals(AppScreen.POKEDEX, browse.screen)
        assertEquals(AppScreen.DETAIL, browse.priorScreen)
        assertEquals(101, browse.selectedAreaId)
        assertEquals(25, browse.selectedSpeciesId)
        assertEquals(AppScreen.DETAIL, detail.screen)
        assertEquals(AppScreen.POKEDEX, detail.priorScreen)
        assertEquals(AppScreen.POKEDEX, returned.screen)
    }

    @Test
    fun areaDexRoundTripReturnsToMapWithoutDroppingAreaFilter() {
        val gateway = CompanionGateway()
        gateway.dispatch(CompanionAction.SetScreen(AppScreen.MAP))

        val browse = gateway.dispatch(CompanionAction.OpenAreaPokedex(202))
        val map = gateway.dispatch(CompanionAction.BackToPokedex)

        assertEquals(AppScreen.POKEDEX, browse.screen)
        assertEquals(AppScreen.MAP, browse.priorScreen)
        assertEquals(202, browse.selectedAreaId)
        assertEquals(AppScreen.MAP, map.screen)
        assertEquals(202, map.selectedAreaId)
    }
}
