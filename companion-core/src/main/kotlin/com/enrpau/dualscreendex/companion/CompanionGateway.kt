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
        is CompanionAction.OpenSpecies -> state.copy(
            screen = AppScreen.DETAIL,
            priorScreen = state.screen.takeUnless { it == AppScreen.DETAIL } ?: state.priorScreen,
            selectedSpeciesId = action.speciesId,
        )
        CompanionAction.OpenTrainer -> state.copy(
            screen = AppScreen.TRAINER,
            priorScreen = state.screen.takeUnless { it == AppScreen.TRAINER } ?: state.priorScreen,
        )
        CompanionAction.OpenParty -> state.copy(
            screen = AppScreen.PARTY,
            priorScreen = state.screen.takeUnless { it == AppScreen.PARTY } ?: state.priorScreen,
        )
        is CompanionAction.OpenPartyMember -> state.copy(
            screen = AppScreen.PARTY,
            priorScreen = state.screen.takeUnless { it == AppScreen.PARTY } ?: state.priorScreen,
            selectedPartySlot = action.slot.takeIf { it in 0 until PARTY_SLOT_COUNT },
        )
        CompanionAction.BackToPokedex -> state.copy(screen = state.priorScreen)
        is CompanionAction.SetScreen -> if (action.screen == AppScreen.SETTINGS && state.screen != AppScreen.SETTINGS) {
            state.copy(screen = action.screen, settingsReturnScreen = state.screen)
        } else {
            state.copy(screen = action.screen)
        }
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
        is CompanionAction.SetBattleTab -> state.copy(battleTab = action.tab)
        is CompanionAction.UpdateSettings -> state.copy(settings = action.settings)
        is CompanionAction.BattleStarted -> state.copy(
            priorScreen = state.screen.takeUnless { it == AppScreen.BATTLE } ?: state.priorScreen,
            screen = if (state.settings.autoOpenTarget) AppScreen.BATTLE else state.screen,
            battle = action.battle,
            battleTab = initialBattleTab(
                action.battle.encounterKind,
                state.settings.rarityEnabled,
                action.battle.rarityUsable,
            ),
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
        is CompanionAction.LiveAreaChanged -> state.copy(
            liveAreaBaseId = action.areaBaseId,
            liveMapPosition = state.liveMapPosition.takeIf { action.areaBaseId == state.liveAreaBaseId },
        )
        is CompanionAction.LiveMapPositionChanged -> state.copy(liveMapPosition = action.position)
        is CompanionAction.LiveGameStateChanged -> state.copy(trainer = action.trainer, party = action.party)
        is CompanionAction.ReplaceLedger -> state.copy(ledger = action.ledger)
        is CompanionAction.Failure -> state.copy(error = action.message)
    }
}

private const val PARTY_SLOT_COUNT = 6

fun initialBattleTab(
    encounterKind: BattleEncounterKind,
    rarityEnabled: Boolean,
    rarityUsable: Boolean,
): BattleTab = if (encounterKind == BattleEncounterKind.WILD && rarityEnabled && rarityUsable) {
    BattleTab.RARITY
} else {
    BattleTab.ENTRY
}
