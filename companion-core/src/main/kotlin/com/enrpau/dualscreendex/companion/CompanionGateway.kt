package com.enrpau.dualscreendex.companion

import com.enrpau.dualscreendex.companion.model.AppScreen
import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.BattleEncounterKind
import com.enrpau.dualscreendex.companion.model.BattleTab
import com.enrpau.dualscreendex.companion.model.CompanionAction
import com.enrpau.dualscreendex.companion.model.PokedexFilter
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
        is CompanionAction.CatalogLoaded -> state.copy(
            catalogReady = true,
            catalogName = action.name,
            selectedPartySlot = state.selectedPartySlot.takeIf { action.name == state.catalogName },
            error = null,
        )
        is CompanionAction.CatalogLoadingChanged -> state.copy(
            catalogLoading = action.loading,
            catalogReady = when {
                action.loading.active -> false
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
        is CompanionAction.OpenSpecies -> navigate(state, AppScreen.DETAIL).copy(
            selectedSpeciesId = action.speciesId,
        )
        CompanionAction.OpenTrainer -> navigate(state, AppScreen.TRAINER)
        CompanionAction.OpenParty -> navigate(state, AppScreen.PARTY)
        is CompanionAction.OpenPartyMember -> navigate(state, AppScreen.PARTY).copy(
            selectedPartySlot = action.slot.takeIf { it in 0 until PARTY_SLOT_COUNT },
        )
        CompanionAction.BackToPokedex -> goBack(state)
        is CompanionAction.SetScreen -> setScreen(state, action.screen)
        is CompanionAction.SetFilter -> state.copy(
            filter = action.filter,
            selectedAreaId = action.areaId,
            selectedAreaIds = if (action.filter == PokedexFilter.AREA) setOfNotNull(action.areaId) else emptySet(),
        )
        is CompanionAction.OpenAreaPokedex -> state.copy(
            screen = AppScreen.POKEDEX,
            filter = PokedexFilter.AREA,
            selectedAreaId = action.areaIds.minOrNull(),
            selectedAreaIds = action.areaIds,
        )
        is CompanionAction.SetBattleTab -> state.copy(
            battleTab = action.tab,
            battleTabExplicitlySelected = true,
        )
        is CompanionAction.UpdateSettings -> state.copy(settings = action.settings)
        is CompanionAction.BattleStarted -> state.copy(
            priorScreen = state.screen.takeUnless { it == AppScreen.BATTLE } ?: state.priorScreen,
            screen = if (state.settings.autoOpenTarget) AppScreen.BATTLE else state.screen,
            battle = action.battle,
            battleTab = initialBattleTab(
                action.battle.encounterKind,
                state.settings.rarityEnabled,
            ),
            battleTabExplicitlySelected = false,
            battleReturnScreen = state.screen.takeUnless { it == AppScreen.BATTLE } ?: state.battleReturnScreen,
            selectedSpeciesId = action.battle.opponents.getOrNull(action.battle.targetIndex)?.speciesId,
        )
        is CompanionAction.BattleUpdated -> state.copy(
            battle = action.battle,
            battleTab = if (
                !state.battleTabExplicitlySelected &&
                state.battle?.encounterKind != BattleEncounterKind.WILD &&
                action.battle.encounterKind == BattleEncounterKind.WILD &&
                state.settings.rarityEnabled
            ) {
                BattleTab.RARITY
            } else {
                state.battleTab
            },
            selectedSpeciesId = action.battle.opponents.getOrNull(action.battle.targetIndex)?.speciesId,
        )
        CompanionAction.BattleEnded -> {
            val destination = state.battleReturnScreen.takeUnless { it == AppScreen.BATTLE } ?: AppScreen.POKEDEX
            val history = state.navigationHistory.filterNot { it == AppScreen.BATTLE }
            state.copy(
                screen = if (state.screen == AppScreen.BATTLE) destination else state.screen,
                priorScreen = if (state.priorScreen == AppScreen.BATTLE) history.lastOrNull() ?: destination else state.priorScreen,
                navigationHistory = history,
                battle = null,
                battleTabExplicitlySelected = false,
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
        is CompanionAction.LiveAreaChanged -> state.copy(
            liveAreaBaseId = action.areaBaseId,
            liveMapPosition = state.liveMapPosition.takeIf { action.areaBaseId == state.liveAreaBaseId },
        )
        is CompanionAction.LiveMapPositionChanged -> state.copy(liveMapPosition = action.position)
        is CompanionAction.LiveGameStateChanged -> state.copy(
            trainer = action.trainer,
            trainerIdentity = action.trainerIdentity,
            party = action.party,
            gameTime = action.gameTime,
        )
        is CompanionAction.LiveGameClockChanged -> state.copy(gameTime = action.gameTime)
        is CompanionAction.ReplaceLedger -> state.copy(ledger = action.ledger)
        is CompanionAction.Failure -> state.copy(error = action.message)
    }

    private fun navigate(state: AppSnapshot, destination: AppScreen): AppSnapshot {
        if (destination == state.screen) return state
        val history = (state.navigationHistory + state.screen)
            .fold(emptyList<AppScreen>()) { result, screen ->
                if (result.lastOrNull() == screen) result else result + screen
            }
            .takeLast(MAX_NAVIGATION_HISTORY)
        return state.copy(
            screen = destination,
            priorScreen = state.screen,
            navigationHistory = history,
        )
    }

    private fun goBack(state: AppSnapshot): AppSnapshot {
        val destination = state.navigationHistory.lastOrNull()
            ?: state.priorScreen.takeUnless { it == state.screen }
            ?: AppScreen.POKEDEX
        val history = if (state.navigationHistory.isEmpty()) emptyList() else state.navigationHistory.dropLast(1)
        return state.copy(
            screen = destination,
            priorScreen = history.lastOrNull() ?: AppScreen.POKEDEX,
            navigationHistory = history,
        )
    }

    private fun setScreen(state: AppSnapshot, destination: AppScreen): AppSnapshot {
        if (destination == state.screen) return state
        val next = if (state.navigationHistory.lastOrNull() == destination) goBack(state) else navigate(state, destination)
        return if (destination == AppScreen.SETTINGS) {
            next.copy(settingsReturnScreen = state.screen, priorScreen = state.priorScreen)
        } else {
            next
        }
    }
}

private const val PARTY_SLOT_COUNT = 6
private const val MAX_NAVIGATION_HISTORY = 16

fun initialBattleTab(
    encounterKind: BattleEncounterKind,
    rarityEnabled: Boolean,
): BattleTab = if (encounterKind == BattleEncounterKind.WILD && rarityEnabled) {
    BattleTab.RARITY
} else {
    BattleTab.ENTRY
}
