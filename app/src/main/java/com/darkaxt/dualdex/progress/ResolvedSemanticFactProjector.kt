package com.darkaxt.dualdex.progress

import com.darkaxt.dualdex.live.ResolvedGameSnapshot
import com.darkaxt.dualdex.live.ResolvedValue
import com.darkaxt.dualdex.save.SaveObservationKind
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.semantic.BattleFact
import com.enrpau.dualscreendex.companion.semantic.IndividualFact
import com.enrpau.dualscreendex.companion.semantic.PlaythroughKey
import com.enrpau.dualscreendex.companion.semantic.SaveObservationFact
import com.enrpau.dualscreendex.companion.semantic.SaveObservationType
import com.enrpau.dualscreendex.companion.semantic.SemanticFactSet
import com.enrpau.dualscreendex.companion.semantic.SemanticValue

object ResolvedSemanticFactProjector {
    fun project(
        snapshot: ResolvedGameSnapshot,
        ledger: KnowledgeLedger,
        battleEpoch: Long,
    ): SemanticFactSet? {
        val saveIdentity = snapshot.recovery.saveIdentity ?: return null
        val playthrough = PlaythroughKey(snapshot.romIdentity.lowercase(), saveIdentity.lowercase())
        val allIndividuals = if (snapshot.party.value != null && snapshot.storedIndividuals.value != null) {
            SemanticValue.Available((snapshot.party.value.orEmpty() + snapshot.storedIndividuals.value.orEmpty()).toFacts())
        } else SemanticValue.Unavailable
        return SemanticFactSet(
            playthrough = playthrough,
            caughtDexNumbers = snapshot.pokedex.caughtSpeciesIds.toSemantic(),
            individuals = allIndividuals,
            party = snapshot.party.mapSemantic { it.toFacts() },
            areaBaseId = snapshot.location.areaBaseId.toSemantic(),
            discoveredPoiIds = SemanticValue.Available(
                ledger.proximityRevealedPoiKeys + ledger.identifiedPoiKeys + ledger.enteredPoiKeys + ledger.collectedPoiKeys,
            ),
            battle = snapshot.battle.mapSemantic { battle ->
                BattleFact(
                    active = battle.active,
                    epoch = battleEpoch,
                    encounterKind = battle.encounterKind.name,
                )
            },
            saveObservation = saveObservation(snapshot),
        )
    }

    private fun saveObservation(snapshot: ResolvedGameSnapshot): SemanticValue<SaveObservationFact> {
        val fingerprint = snapshot.recovery.saveFileFingerprint ?: return SemanticValue.Unavailable
        val type = when (snapshot.recovery.observationKind) {
            SaveObservationKind.INITIAL -> SaveObservationType.INITIAL
            SaveObservationKind.UNCHANGED -> SaveObservationType.UNCHANGED
            SaveObservationKind.CHANGED -> SaveObservationType.CHANGED
            SaveObservationKind.SWITCHED -> SaveObservationType.SWITCHED
            null -> return SemanticValue.Unavailable
        }
        return SemanticValue.Available(SaveObservationFact(type, fingerprint.lowercase()))
    }

    private fun List<com.darkaxt.dualdex.save.OwnedIndividual>.toFacts() = mapNotNull { individual ->
        individual.speciesId.takeIf { it > 0 }?.let { speciesId ->
            val stableKey = individual.details?.personality
                ?.let { personality -> "personality:${personality.toString(16).padStart(8, '0')}" }
                ?: individual.stableLocation
            IndividualFact(stableKey, speciesId)
        }
    }

    private fun <T> ResolvedValue<T>.toSemantic(): SemanticValue<T> =
        value?.let { SemanticValue.Available(it) } ?: SemanticValue.Unavailable

    private fun <T, R> ResolvedValue<T>.mapSemantic(transform: (T) -> R): SemanticValue<R> =
        value?.let { SemanticValue.Available(transform(it)) } ?: SemanticValue.Unavailable
}
