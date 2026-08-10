package com.darkaxt.dualdex.battle

data class BattleSpecies(
    val id: Int,
    val typeIds: List<Int>,
    val abilityIds: Set<Int> = emptySet(),
)

data class BattleMove(
    val id: Int,
    val basePp: Int,
)

data class BattleCatalogView(
    val species: Map<Int, BattleSpecies>,
    val moves: Map<Int, BattleMove>,
    val typeIds: Set<Int>,
)

enum class BattleCapability {
    BATTLE_LAYOUT,
    MULTIPLE_OPPONENTS,
    SELECTED_TARGET,
    SELECTED_MOVE,
    OPPONENT_IVS,
    OPPONENT_PP,
}

enum class CapabilityState { AVAILABLE, NOT_FOUND, NOT_APPLICABLE }

enum class TargetMode { AUTOMATIC, MANUAL_TARGET_FALLBACK }

data class BattleTarget(
    val opponentIndex: Int,
    val mode: TargetMode,
)

data class ResolvedBattleLayout(
    val battleMonsOffset: Int,
    val battlerCountOffset: Int,
    val battlerPositionsOffset: Int,
    val outcomeOffset: Int,
    val moveCursorOffset: Int,
    val targetCursorOffset: Int,
    val battlerCount: Int,
)

data class BattleMonSnapshot(
    val battlerIndex: Int,
    val position: Int,
    val speciesId: Int,
    val level: Int,
    val hp: Int,
    val maxHp: Int,
    val ivs: List<Int>,
    val moves: List<Int>,
    val pp: List<Int>,
    val typeIds: List<Int>,
    val abilityId: Int,
    val personality: Long,
)

data class BattleMemorySample(
    val layout: ResolvedBattleLayout,
    val battlers: List<BattleMonSnapshot>,
    val opponents: List<BattleMonSnapshot>,
    val selectedMoveId: Int?,
    val target: BattleTarget,
    val capabilities: Map<BattleCapability, CapabilityState>,
)

sealed interface LayoutResolution {
    data class Resolved(val sample: BattleMemorySample) : LayoutResolution
    data object NotFound : LayoutResolution
    data class Ambiguous(val candidates: Int) : LayoutResolution
}
