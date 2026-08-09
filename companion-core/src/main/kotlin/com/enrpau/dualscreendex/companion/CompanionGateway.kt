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
        is CompanionAction.OpenSpecies -> state.copy(
            screen = AppScreen.DETAIL,
            priorScreen = state.screen.takeUnless { it == AppScreen.DETAIL } ?: state.priorScreen,
            selectedSpeciesId = action.speciesId,
        )
        CompanionAction.BackToPokedex -> state.copy(screen = AppScreen.POKEDEX)
        is CompanionAction.SetScreen -> state.copy(
            screen = action.screen,
            priorScreen = if (action.screen == AppScreen.BATTLE) state.screen else state.priorScreen,
        )
        is CompanionAction.SetFilter -> state.copy(filter = action.filter, selectedAreaId = action.areaId)
        is CompanionAction.SetBattleTab -> state.copy(battleTab = action.tab)
        is CompanionAction.UpdateSettings -> state.copy(settings = action.settings)
        is CompanionAction.BattleStarted -> state.copy(
            priorScreen = state.screen.takeUnless { it == AppScreen.BATTLE } ?: state.priorScreen,
            screen = if (state.settings.autoOpenTarget) AppScreen.BATTLE else state.screen,
            battle = action.battle,
            selectedSpeciesId = action.battle.opponents.getOrNull(action.battle.targetIndex)?.speciesId,
        )
        CompanionAction.BattleEnded -> state.copy(screen = state.priorScreen, battle = null)
        is CompanionAction.SelectTarget -> {
            val battle = state.battle
            if (battle == null || action.index !in battle.opponents.indices) state
            else state.copy(
                battle = battle.copy(targetIndex = action.index),
                selectedSpeciesId = battle.opponents[action.index].speciesId,
            )
        }
        is CompanionAction.SelectMove -> state.copy(battle = state.battle?.copy(selectedMoveId = action.moveId))
        is CompanionAction.ReplaceLedger -> state.copy(ledger = action.ledger)
        is CompanionAction.Failure -> state.copy(error = action.message)
    }
}
