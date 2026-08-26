package com.enrpau.dualscreendex.companion.semantic

sealed interface GameEvent {
    data class Captured(val dexNumber: Int) : GameEvent
    data class Evolved(val individualKey: String, val fromSpeciesId: Int, val toSpeciesId: Int) : GameEvent
    data class AreaVisited(val areaBaseId: Int) : GameEvent
    data class PoiDiscovered(val poiId: String) : GameEvent
    data class BattleStarted(val epoch: Long, val encounterKind: String?) : GameEvent
    data class BattleEnded(val epoch: Long) : GameEvent
    data class PartyChanged(val members: List<IndividualFact>) : GameEvent
    data class SaveObserved(val fingerprint: String) : GameEvent
}

data class SemanticTransitionResult(
    val baseline: SemanticFactSet,
    val events: List<GameEvent>,
)

