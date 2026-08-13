package com.enrpau.dualscreendex.companion

import com.enrpau.dualscreendex.companion.model.AppScreen
import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.CompanionAction
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

class CompanionGateway(initial: AppSnapshot = AppSnapshot()) {
    private val current = AtomicReference(initial)
    private val listeners = CopyOnWriteArrayList<(AppSnapshot) -> Unit>()

    fun bootstrap(): AppSnapshot = current.get()

    fun dispatch(action: CompanionAction): AppSnapshot {
        while (true) {
            val before = current.get()
            val after = reduce(before, action).copy(version = before.version + 1)
            if (current.compareAndSet(before, after)) {
                listeners.forEach { it(after) }
                return after
            }
        }
    }

    fun subscribe(listener: (AppSnapshot) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }

    private fun reduce(state: AppSnapshot, action: CompanionAction): AppSnapshot = when (action) {
        is CompanionAction.CatalogLoaded -> state.copy(catalogReady = true, catalogName = action.name, error = null)
        is CompanionAction.CatalogLoadingChanged -> state.copy(
            catalogLoading = action.loading,
            catalogReady = when {
                action.name != null && action.loading.active && action.loading.completedUnits == 0 -> false
                action.loading.phase == "FAILED" -> state.catalogReady
                else -> action.loading.completedUnits > 0
            },
            catalogName = when {
                action.loading.phase == "FAILED" -> null
                action.loading.phase == "IDLE" && action.loading.totalUnits == 0 -> null
                else -> action.name ?: state.catalogName
            },
            error = if (action.loading.phase == "FAILED") state.error else null,
        )
        is CompanionAction.OpenSpecies -> state.copy(
            screen = AppScreen.DETAIL,
            priorScreen = state.screen.takeUnless { it == AppScreen.DETAIL } ?: state.priorScreen,
            selectedSpeciesId = action.speciesId,
        )
        CompanionAction.BackToPokedex -> state.copy(screen = state.priorScreen)
        is CompanionAction.SetScreen -> if (action.screen == AppScreen.SETTINGS && state.screen != AppScreen.SETTINGS) {
            state.copy(screen = action.screen, settingsReturnScreen = state.screen)
        } else {
            state.copy(screen = action.screen)
        }
        is CompanionAction.SetFilter -> state.copy(filter = action.filter, selectedAreaId = action.areaId)
        is CompanionAction.SetBattleTab -> state.copy(battleTab = action.tab)
        is CompanionAction.UpdateSettings -> state.copy(settings = action.settings)
        is CompanionAction.BattleStarted -> state.copy(
            priorScreen = state.screen.takeUnless { it == AppScreen.BATTLE } ?: state.priorScreen,
            screen = if (state.settings.autoOpenTarget) AppScreen.BATTLE else state.screen,
            battle = action.battle,
            battleReturnScreen = state.screen.takeUnless { it == AppScreen.BATTLE } ?: state.battleReturnScreen,
            selectedSpeciesId = action.battle.opponents.getOrNull(action.battle.targetIndex)?.speciesId,
        )
        is CompanionAction.BattleUpdated -> state.copy(
            battle = action.battle,
            selectedSpeciesId = action.battle.opponents.getOrNull(action.battle.targetIndex)?.speciesId,
        )
        CompanionAction.BattleEnded -> {
            val destination = state.battleReturnScreen.takeUnless { it == AppScreen.BATTLE } ?: AppScreen.POKEDEX
            state.copy(
                screen = if (state.screen == AppScreen.BATTLE) destination else state.screen,
                priorScreen = if (state.priorScreen == AppScreen.BATTLE) destination else state.priorScreen,
                battle = null,
            )
        }
        is CompanionAction.SelectTarget -> {
            val battle = state.battle
            if (battle == null || action.index !in battle.opponents.indices) state
            else state.copy(
                battle = battle.copy(targetIndex = action.index),
                selectedSpeciesId = battle.opponents[action.index].speciesId,
            )
        }
        is CompanionAction.SelectMove -> state.copy(battle = state.battle?.copy(selectedMoveId = action.moveId))
        is CompanionAction.LiveAreaChanged -> state.copy(liveAreaBaseId = action.areaBaseId)
        is CompanionAction.ReplaceLedger -> state.copy(ledger = action.ledger)
        is CompanionAction.Failure -> state.copy(error = action.message)
    }
}
