package com.enrpau.dualscreendex.companion.model

enum class KnowledgeMode { DISCOVERED, ORGANIC, HIDDEN }
enum class AppScreen { POKEDEX, DETAIL, BATTLE, SETTINGS, SETUP }
enum class PokedexFilter { ALL, CAUGHT, SEEN, TEAM, AREA }
enum class BattleTab { ENTRY, ATTACK, RARITY, MOVES }
enum class BattleEncounterKind { WILD, TRAINER, UNKNOWN }
enum class Density { AUTO, COMFORTABLE, COMPACT }
enum class DisplayMode { DOCKED, OVERLAY }
enum class Theme { GAME, DARK, LIGHT }
enum class DisplayTarget { AUTO, HANDHELD, EXTERNAL }

data class CompanionSettings(
    val knowledgeMode: KnowledgeMode = KnowledgeMode.ORGANIC,
    val attackEnabled: Boolean = true,
    val rarityEnabled: Boolean = true,
    val movesEnabled: Boolean = true,
    val fontScale: Double = 1.0,
    val density: Density = Density.AUTO,
    val highContrast: Boolean = false,
    val autoOpenTarget: Boolean = true,
    val ruleset: String = "AUTO",
    val displayMode: DisplayMode = DisplayMode.DOCKED,
    val theme: Theme = Theme.GAME,
    val displayTarget: DisplayTarget = DisplayTarget.AUTO,
    val overlayScale: Double = 1.0,
    val battlePollingIntervalMs: Int = 5,
)

data class OwnedPokemon(
    val stableKey: String,
    val speciesId: Int,
    val generation: Int,
    val level: Int,
    val ivs: List<Int> = emptyList(),
    val dvs: List<Int> = emptyList(),
    val captureBallId: Int? = null,
    val isEgg: Boolean = false,
    val party: Boolean = false,
)

data class MoveObservation(
    val moveId: Int,
    val frequency: Int,
)

data class MatchupKey(val speciesId: Int, val moveId: Int)

enum class Effectiveness(val multiplierPercent: Int) {
    NO_EFFECT(0), RESISTED(50), NEUTRAL(100), SUPER_EFFECTIVE(200),
}

data class KnowledgeLedger(
    val seenSpecies: Set<Int> = emptySet(),
    val caughtSpecies: Set<Int> = emptySet(),
    val owned: List<OwnedPokemon> = emptyList(),
    val teamSpecies: Set<Int> = emptySet(),
    val currentAreaBaseId: Int? = null,
    val seenSpeciesByArea: Map<Int, Set<Int>> = emptyMap(),
    val observedMoves: Map<Int, List<MoveObservation>> = emptyMap(),
    val discoveredMatchups: Map<MatchupKey, Effectiveness> = emptyMap(),
    val knownMoves: Set<Int> = emptySet(),
)

data class OpponentState(
    val speciesId: Int,
    val level: Int,
    val typeIds: List<Int> = emptyList(),
    val ivs: List<Int> = emptyList(),
    val dvs: List<Int> = emptyList(),
    val moveHistory: List<MoveObservation>,
    val capturable: Boolean = true,
)

enum class BattleTargetMode { AUTOMATIC, MANUAL_TARGET_FALLBACK }

data class BattleState(
    val opponents: List<OpponentState>,
    val targetIndex: Int = 0,
    val selectedMoveId: Int? = null,
    val playerReferenceLevel: Int? = null,
    val targetMode: BattleTargetMode = BattleTargetMode.AUTOMATIC,
    val capabilities: Map<String, String> = emptyMap(),
    val encounterKind: BattleEncounterKind = BattleEncounterKind.UNKNOWN,
    val rarityUsable: Boolean = false,
)

data class CatalogLoadingState(
    val active: Boolean = false,
    val phase: String = "IDLE",
    val completedUnits: Int = 0,
    val totalUnits: Int = 0,
)

data class AppSnapshot(
    val version: Long = 0,
    val screen: AppScreen = AppScreen.POKEDEX,
    val priorScreen: AppScreen = AppScreen.POKEDEX,
    val settingsReturnScreen: AppScreen = AppScreen.POKEDEX,
    val selectedSpeciesId: Int? = null,
    val filter: PokedexFilter = PokedexFilter.ALL,
    val selectedAreaId: Int? = null,
    val battleTab: BattleTab = BattleTab.ENTRY,
    val settings: CompanionSettings = CompanionSettings(),
    val ledger: KnowledgeLedger = KnowledgeLedger(),
    val liveAreaBaseId: Int? = null,
    val battle: BattleState? = null,
    val battleReturnScreen: AppScreen = AppScreen.POKEDEX,
    val catalogReady: Boolean = false,
    val catalogName: String? = null,
    val error: String? = null,
    val catalogLoading: CatalogLoadingState = CatalogLoadingState(),
)

sealed interface CompanionAction {
    data class CatalogLoaded(val name: String) : CompanionAction
    data class CatalogLoadingChanged(val loading: CatalogLoadingState, val name: String? = null) : CompanionAction
    data class OpenSpecies(val speciesId: Int) : CompanionAction
    data object BackToPokedex : CompanionAction
    data class SetScreen(val screen: AppScreen) : CompanionAction
    data class SetFilter(val filter: PokedexFilter, val areaId: Int? = null) : CompanionAction
    data class SetBattleTab(val tab: BattleTab) : CompanionAction
    data class UpdateSettings(val settings: CompanionSettings) : CompanionAction
    data class BattleStarted(val battle: BattleState) : CompanionAction
    data class BattleUpdated(val battle: BattleState) : CompanionAction
    data object BattleEnded : CompanionAction
    data class SelectTarget(val index: Int) : CompanionAction
    data class SelectMove(val moveId: Int) : CompanionAction
    data class LiveAreaChanged(val areaBaseId: Int?) : CompanionAction
    data class ReplaceLedger(val ledger: KnowledgeLedger) : CompanionAction
    data class Failure(val message: String) : CompanionAction
}
