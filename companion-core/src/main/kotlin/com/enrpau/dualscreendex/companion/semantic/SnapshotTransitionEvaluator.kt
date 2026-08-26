package com.enrpau.dualscreendex.companion.semantic

object SnapshotTransitionEvaluator {
    fun evaluate(previous: SemanticFactSet?, current: SemanticFactSet): SemanticTransitionResult {
        if (previous == null || previous.playthrough != current.playthrough) {
            return SemanticTransitionResult(current, emptyList())
        }

        val accepted = current.stabilizedAgainst(previous)
        val events = buildList {
            setDelta(previous.caughtDexNumbers, accepted.caughtDexNumbers)
                .sorted()
                .forEach { add(GameEvent.Captured(it)) }

            val oldIndividuals = previous.individuals.availableOrNull().orEmpty().associateBy { it.stableKey }
            val newIndividuals = accepted.individuals.availableOrNull().orEmpty().associateBy { it.stableKey }
            oldIndividuals.keys.intersect(newIndividuals.keys).sorted().forEach { stableKey ->
                val old = oldIndividuals.getValue(stableKey)
                val new = newIndividuals.getValue(stableKey)
                if (old.speciesId != new.speciesId) {
                    add(GameEvent.Evolved(stableKey, old.speciesId, new.speciesId))
                }
            }

            val oldArea = previous.areaBaseId.availableOrNull()
            val newArea = accepted.areaBaseId.availableOrNull()
            if (oldArea != null && newArea != null && oldArea != newArea) add(GameEvent.AreaVisited(newArea))

            setDelta(previous.discoveredPoiIds, accepted.discoveredPoiIds)
                .sorted()
                .forEach { add(GameEvent.PoiDiscovered(it)) }

            val oldBattle = previous.battle.availableOrNull()
            val newBattle = accepted.battle.availableOrNull()
            if (oldBattle != null && newBattle != null) {
                if (newBattle.active && (!oldBattle.active || oldBattle.epoch != newBattle.epoch)) {
                    add(GameEvent.BattleStarted(newBattle.epoch, newBattle.encounterKind))
                } else if (oldBattle.active && !newBattle.active) {
                    add(GameEvent.BattleEnded(oldBattle.epoch))
                }
            }

            val oldParty = previous.party.availableOrNull()
            val newParty = accepted.party.availableOrNull()
            if (oldParty != null && newParty != null && oldParty != newParty) {
                add(GameEvent.PartyChanged(newParty))
            }

            val oldSave = previous.saveObservation.availableOrNull()
            val newSave = accepted.saveObservation.availableOrNull()
            if (
                newSave?.type == SaveObservationType.CHANGED &&
                newSave.fingerprint != oldSave?.fingerprint
            ) {
                add(GameEvent.SaveObserved(newSave.fingerprint))
            }
        }
        return SemanticTransitionResult(accepted, events)
    }

    private fun SemanticFactSet.stabilizedAgainst(previous: SemanticFactSet) = copy(
        caughtDexNumbers = caughtDexNumbers.orPrevious(previous.caughtDexNumbers),
        individuals = individuals.orPrevious(previous.individuals),
        party = party.orPrevious(previous.party),
        areaBaseId = areaBaseId.orPrevious(previous.areaBaseId),
        discoveredPoiIds = discoveredPoiIds.orPrevious(previous.discoveredPoiIds),
        battle = battle.orPrevious(previous.battle),
        saveObservation = saveObservation.orPrevious(previous.saveObservation),
    )

    private fun <T> SemanticValue<T>.orPrevious(previous: SemanticValue<T>): SemanticValue<T> =
        if (this is SemanticValue.Available) this else previous

    private fun <T> SemanticValue<T>.availableOrNull(): T? =
        (this as? SemanticValue.Available<T>)?.value

    private fun <T : Comparable<T>> setDelta(
        previous: SemanticValue<Set<T>>,
        current: SemanticValue<Set<T>>,
    ): Set<T> {
        val old = previous.availableOrNull() ?: return emptySet()
        val new = current.availableOrNull() ?: return emptySet()
        return new - old
    }
}
