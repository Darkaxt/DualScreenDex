package com.darkaxt.dualdex.live

import com.darkaxt.dualdex.save.BagPocket
import com.darkaxt.dualdex.save.SaveObservationKind

enum class ResolvedStateTraceTrigger {
    SESSION_BEGIN,
    LIVE_SAMPLE,
    RECOVERY_APPLIED,
    RECOVERY_STATUS,
    RECOVERY_CLEARED,
    LIVE_SUSPENDED,
    BATTLE_TRACKING,
    SESSION_END,
}

data class ResolvedStateFieldTrace(
    val source: ResolvedValueSource?,
    val available: Boolean,
    val count: Int? = null,
)

data class ResolvedStateFieldChange(
    val field: String,
    val before: ResolvedStateFieldTrace?,
    val after: ResolvedStateFieldTrace?,
)

data class ResolvedStateTraceEvent(
    val schemaVersion: Int = RESOLVED_STATE_TRACE_SCHEMA_VERSION,
    val revision: Long,
    val trigger: ResolvedStateTraceTrigger,
    val generation: Int?,
    val sampleId: Long?,
    val recoveryApplicationId: Long?,
    val recoveryObservationKind: SaveObservationKind?,
    val changedSections: Set<ResolvedGameSection>,
    val fields: List<ResolvedStateFieldChange>,
)

fun interface ResolvedStateTraceSink {
    fun append(event: ResolvedStateTraceEvent)
}

internal fun resolvedStateTraceEvent(
    revision: Long,
    trigger: ResolvedStateTraceTrigger,
    previous: ResolvedGameSnapshot?,
    next: ResolvedGameSnapshot?,
    changedSections: Set<ResolvedGameSection>,
): ResolvedStateTraceEvent {
    val before = previous.traceFields()
    val after = next.traceFields()
    val reference = next ?: previous
    return ResolvedStateTraceEvent(
        revision = revision,
        trigger = trigger,
        generation = reference?.generation,
        sampleId = next?.sampleId,
        recoveryApplicationId = next?.recovery?.applicationId,
        recoveryObservationKind = next?.recovery?.observationKind,
        changedSections = changedSections,
        fields = (before.keys + after.keys)
            .toSortedSet()
            .mapNotNull { field ->
                val old = before[field]
                val new = after[field]
                ResolvedStateFieldChange(field, old, new).takeIf { old != new }
            },
    )
}

private fun ResolvedGameSnapshot?.traceFields(): Map<String, ResolvedStateFieldTrace> {
    val snapshot = this ?: return emptyMap()
    return buildMap {
        put("trainer.identity", snapshot.trainer.identity.traceValue())
        put("trainer.publicId", snapshot.trainer.publicTrainerId.traceValue())
        put("trainer.money", snapshot.trainer.money.traceValue())
        put("trainer.playTime", snapshot.trainer.playTime.traceValue())
        put("trainer.badges", snapshot.trainer.badgeFlags.traceValue())
        put("trainer.stars", snapshot.trainer.stars.traceValue())
        put("pokedex.seen", snapshot.pokedex.seenSpeciesIds.traceValue { it.size })
        put("pokedex.caught", snapshot.pokedex.caughtSpeciesIds.traceValue { it.size })
        put("owned.party", snapshot.ownedStorage.party.traceValue { it.size })
        put(
            "owned.boxes",
            snapshot.ownedStorage.boxes.traceValue { boxes -> boxes.sumOf { box -> box.slots.size } },
        )
        put("battle", snapshot.battle.traceValue())
        put(
            "battle.knowledge",
            directTraceValue(
                source = ResolvedValueSource.LIVE,
                count = snapshot.battleKnowledge.observedMoves.values.sumOf { it.size } +
                    snapshot.battleKnowledge.seenSpeciesIds.size +
                    snapshot.battleKnowledge.discoveredMatchups.size,
            ),
        )
        put("location.area", snapshot.location.areaBaseId.traceValue())
        put("location.position", snapshot.location.position.traceValue())
        put("clock", snapshot.clock.traceValue())
        BagPocket.entries.forEach { pocket ->
            put("bag.${pocket.name.lowercase()}", snapshot.bag.getValue(pocket).traceValue { it.entries.size })
        }
        put("eventFlags", snapshot.eventFlags.traceValue { it.size })
        put("levelUpRuleset", snapshot.levelUpRulesetId.traceValue())
        put(
            "recovery",
            snapshot.recovery.applicationId?.let {
                directTraceValue(source = ResolvedValueSource.RECOVERY)
            } ?: ResolvedStateFieldTrace(
                source = ResolvedValueSource.UNAVAILABLE,
                available = false,
            ),
        )
        put(
            "session.gameAccessReady",
            directTraceValue(ResolvedValueSource.LIVE),
        )
    }
}

private fun <T> ResolvedValue<T>.traceValue(
    count: ((T) -> Int)? = null,
): ResolvedStateFieldTrace {
    val resolved = value
    if (resolved == null || source == ResolvedValueSource.UNAVAILABLE) {
        return ResolvedStateFieldTrace(source, available = false)
    }
    return ResolvedStateFieldTrace(
        source = source,
        available = true,
        count = count?.invoke(resolved),
    )
}

private fun directTraceValue(
    source: ResolvedValueSource,
    count: Int? = null,
): ResolvedStateFieldTrace = ResolvedStateFieldTrace(
    source = source,
    available = true,
    count = count,
)

private const val RESOLVED_STATE_TRACE_SCHEMA_VERSION = 2
