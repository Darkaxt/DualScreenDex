package com.enrpau.dualscreendex.companion.semantic

data class PlaythroughKey(
    val romSha256: String,
    val saveIdentity: String,
) {
    init {
        require(romSha256.isNotBlank())
        require(saveIdentity.isNotBlank())
    }
}

sealed interface SemanticValue<out T> {
    data class Available<T>(val value: T) : SemanticValue<T>
    data object Unavailable : SemanticValue<Nothing>
}

data class IndividualFact(
    val stableKey: String,
    val speciesId: Int,
) {
    init {
        require(stableKey.isNotBlank())
        require(speciesId > 0)
    }
}

data class BattleFact(
    val active: Boolean,
    val epoch: Long,
    val encounterKind: String? = null,
) {
    init {
        require(epoch >= 0)
    }
}

enum class SaveObservationType { INITIAL, UNCHANGED, CHANGED, SWITCHED }

data class SaveObservationFact(
    val type: SaveObservationType,
    val fingerprint: String,
) {
    init {
        require(fingerprint.isNotBlank())
    }
}

data class SemanticFactSet(
    val playthrough: PlaythroughKey,
    val caughtDexNumbers: SemanticValue<Set<Int>> = SemanticValue.Unavailable,
    val individuals: SemanticValue<List<IndividualFact>> = SemanticValue.Unavailable,
    val party: SemanticValue<List<IndividualFact>> = SemanticValue.Unavailable,
    val areaBaseId: SemanticValue<Int> = SemanticValue.Unavailable,
    val discoveredPoiIds: SemanticValue<Set<String>> = SemanticValue.Unavailable,
    val battle: SemanticValue<BattleFact> = SemanticValue.Unavailable,
    val saveObservation: SemanticValue<SaveObservationFact> = SemanticValue.Unavailable,
)

