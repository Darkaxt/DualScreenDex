package com.enrpau.dualscreendex.companion.model

import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.TrainerSnapshot
import com.darkaxt.dualdex.save.TrainerIdentity

enum class KnowledgeMode { DISCOVERED, ORGANIC, HIDDEN }
enum class AppScreen { POKEDEX, DETAIL, BATTLE, TRAINER, PARTY, SETTINGS, SETUP }
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
    val mapFollowSmoothingPercent: Int = 25,
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

data class LocalMapPoiPreferences(
    val showPlaces: Boolean = true,
    val showServices: Boolean = true,
    val showAvailableItems: Boolean = true,
    val showCollectedItems: Boolean = true,
    val showUnknownPois: Boolean = true,
    val iconZoomThresholdPercent: Int = 0,
    val labelZoomThresholdPercent: Int = 0,
) {
    init {
        require(iconZoomThresholdPercent in 0..100)
        require(labelZoomThresholdPercent in iconZoomThresholdPercent..100)
    }
}

enum class Effectiveness(val multiplierPercent: Int) {
    NO_EFFECT(0), RESISTED(50), NEUTRAL(100), SUPER_EFFECTIVE(200),
}

data class KnowledgeLedger(
    val seenSpecies: Set<Int> = emptySet(),
    val caughtSpecies: Set<Int> = emptySet(),
    val owned: List<OwnedPokemon> = emptyList(),
    val teamSpecies: Set<Int> = emptySet(),
    val trainerCardUnlocked: Boolean = false,
    val currentAreaBaseId: Int? = null,
    val visitedAreaBaseIds: Set<Int> = emptySet(),
    val seenSpeciesByArea: Map<Int, Set<Int>> = emptyMap(),
    val observedMoves: Map<Int, List<MoveObservation>> = emptyMap(),
    val discoveredMatchups: Map<MatchupKey, Effectiveness> = emptyMap(),
    val knownMoves: Set<Int> = emptySet(),
    val proximityRevealedPoiKeys: Set<String> = emptySet(),
    val identifiedPoiKeys: Set<String> = emptySet(),
    val enteredPoiKeys: Set<String> = emptySet(),
    val collectedPoiKeys: Set<String> = emptySet(),
    val localMapPoiPreferences: LocalMapPoiPreferences = LocalMapPoiPreferences(),
    val matchupEvidenceVersion: Int = CURRENT_MATCHUP_EVIDENCE_VERSION,
) {
    companion object {
        const val CURRENT_MATCHUP_EVIDENCE_VERSION = 1
    }
}

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
    val message: String? = null,
)

data class LiveMapPosition(val x: Int, val y: Int)

enum class GameClockPhase { MORNING, DAY, NIGHT, DARK }

data class GameClock(
    val hours: Int? = null,
    val minutes: Int? = null,
    val phase: GameClockPhase? = null,
    val phaseProgress: Double? = null,
) {
    init {
        require((hours == null) == (minutes == null))
        require(hours == null || hours in 0..23)
        require(minutes == null || minutes in 0..59)
        require(phaseProgress == null || phase != null)
        require(phaseProgress == null || phaseProgress in 0.0..1.0)
    }
}

data class TrainerCardState(
    val identity: TrainerIdentity?,
    val publicTrainerId: Int?,
    val money: Long?,
    val playTimeHours: Int?,
    val playTimeMinutes: Int?,
    val badgeFlags: Int?,
    val dexSeen: Int?,
    val dexCaught: Int?,
    val stars: Int?,
)

data class AppSnapshot(
    val version: Long = 0,
    val screen: AppScreen = AppScreen.POKEDEX,
    val priorScreen: AppScreen = AppScreen.POKEDEX,
    val navigationHistory: List<AppScreen> = emptyList(),
    val settingsReturnScreen: AppScreen = AppScreen.POKEDEX,
    val selectedSpeciesId: Int? = null,
    val selectedPartySlot: Int? = null,
    val filter: PokedexFilter = PokedexFilter.ALL,
    val selectedAreaId: Int? = null,
    val selectedAreaIds: Set<Int> = emptySet(),
    val battleTab: BattleTab = BattleTab.ENTRY,
    val battleTabExplicitlySelected: Boolean = false,
    val settings: CompanionSettings = CompanionSettings(),
    val ledger: KnowledgeLedger = KnowledgeLedger(),
    val liveAreaBaseId: Int? = null,
    val trainer: TrainerSnapshot? = null,
    val trainerIdentity: TrainerIdentity? = null,
    val trainerCardState: TrainerCardState? = null,
    val party: List<OwnedIndividual> = emptyList(),
    val liveMapPosition: LiveMapPosition? = null,
    val gameTime: GameClock? = null,
    val gameAccessReady: Boolean = false,
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
    data object OpenTrainer : CompanionAction
    data object OpenParty : CompanionAction
    data class OpenPartyMember(val slot: Int) : CompanionAction
    data object BackToPokedex : CompanionAction
    data class SetScreen(val screen: AppScreen) : CompanionAction
    data class SetFilter(val filter: PokedexFilter, val areaId: Int? = null) : CompanionAction
    data class OpenAreaPokedex(val areaIds: Set<Int>) : CompanionAction
    data class SetBattleTab(val tab: BattleTab) : CompanionAction
    data class UpdateSettings(val settings: CompanionSettings) : CompanionAction
    data class BattleStarted(val battle: BattleState) : CompanionAction
    data class BattleUpdated(val battle: BattleState) : CompanionAction
    data object BattleEnded : CompanionAction
    data class SelectTarget(val index: Int) : CompanionAction
    data class SelectMove(val moveId: Int) : CompanionAction
    data class LiveAreaChanged(
        val areaBaseId: Int?,
        val gameAccessReady: Boolean? = null,
    ) : CompanionAction
    data class LiveGameStateChanged(
        val trainer: TrainerSnapshot?,
        val party: List<OwnedIndividual>,
        val gameTime: GameClock? = null,
        val trainerIdentity: TrainerIdentity? = trainer?.let { TrainerIdentity(it.name, it.gender) },
        val gameAccessReady: Boolean = false,
    ) : CompanionAction
    data class LiveGameClockChanged(val gameTime: GameClock?) : CompanionAction
    data class ResolvedPlayerStateChanged(
        val trainerCard: TrainerCardState?,
        val seenDexNumbers: Set<Int>? = null,
        val caughtDexNumbers: Set<Int>? = null,
    ) : CompanionAction
    data class ResolvedPartyStateChanged(val party: List<OwnedIndividual>) : CompanionAction
    data class LiveMapPositionChanged(val position: LiveMapPosition?) : CompanionAction
    data class ReplaceLedger(val ledger: KnowledgeLedger) : CompanionAction
    data class Failure(val message: String) : CompanionAction
}
