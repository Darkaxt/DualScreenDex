package com.darkaxt.dualdex.progress

import com.enrpau.dualscreendex.companion.semantic.GameEvent
import com.enrpau.dualscreendex.companion.semantic.PlaythroughKey

class JournalRestoreBaseline internal constructor(
    internal val revision: Long,
    internal val journal: PlaythroughJournal,
    internal val pendingDeltas: Map<String, Long>,
)

interface PlaythroughJournalSession {
    fun restore(restored: PlaythroughJournal): Boolean
    fun current(playthrough: PlaythroughKey): PlaythroughJournal?

    fun captureForRestore(playthrough: PlaythroughKey): JournalRestoreBaseline? = null

    fun restore(restored: PlaythroughJournal, baseline: JournalRestoreBaseline?): Boolean = restore(restored)
}

class PlaythroughJournalCoordinator(
    private val playthrough: PlaythroughKey,
    private val clock: () -> Long = System::currentTimeMillis,
) : PlaythroughJournalSession {
    private var journal = PlaythroughJournal.empty(playthrough)
    private val pendingDeltas = linkedMapOf<String, Long>()
    private var revision = 0L

    @Synchronized
    override fun restore(restored: PlaythroughJournal): Boolean = restore(
        restored,
        captureForRestore(playthrough),
    )

    @Synchronized
    override fun captureForRestore(playthrough: PlaythroughKey): JournalRestoreBaseline? =
        JournalRestoreBaseline(revision, journal.sanitizedAndCompacted(), pendingDeltas.toMap())
            .takeIf { playthrough == this.playthrough }

    @Synchronized
    override fun restore(
        restored: PlaythroughJournal,
        baseline: JournalRestoreBaseline?,
    ): Boolean {
        if (restored.playthrough != playthrough) return false
        val sanitized = restored.sanitizedAndCompacted()
        if (baseline == null || baseline.journal.playthrough != playthrough) return false
        if (baseline.revision == revision) {
            journal = sanitized
            pendingDeltas.clear()
        } else {
            journal = mergeConcurrentChanges(sanitized, baseline.journal, journal)
            val concurrentPending = pendingDeltas.mapValues { (key, value) ->
                (value - baseline.pendingDeltas.getOrDefault(key, 0L)).coerceAtLeast(0L)
            }.filterValues { it > 0L }
            pendingDeltas.clear()
            pendingDeltas.putAll(concurrentPending)
        }
        revision++
        return true
    }

    @Synchronized
    fun accept(events: List<GameEvent>) {
        events.forEach(::accept)
        if (events.isNotEmpty()) revision++
    }

    @Synchronized
    fun current(): PlaythroughJournal = journal.sanitizedAndCompacted()

    @Synchronized
    override fun current(playthrough: PlaythroughKey): PlaythroughJournal? =
        current().takeIf { it.playthrough == playthrough }

    @Synchronized
    fun updatePreferences(changes: Map<String, String>) {
        journal = journal.copy(preferences = journal.preferences + changes).sanitizedAndCompacted()
        if (changes.isNotEmpty()) revision++
    }

    @Synchronized
    fun updateChallengeStates(states: Map<String, ChallengeJournalState>) {
        val newlyCompleted = states.count { (key, state) ->
            state.completedAtEpochMs != null && journal.challengeStates[key]?.completedAtEpochMs == null
        }
        repeat(newlyCompleted) { increment("challenges") }
        journal = journal.copy(challengeStates = states).sanitizedAndCompacted()
        revision++
    }

    private fun mergeConcurrentChanges(
        restored: PlaythroughJournal,
        baseline: PlaythroughJournal,
        current: PlaythroughJournal,
    ): PlaythroughJournal {
        val countKeys = baseline.trackedCounts.keys + current.trackedCounts.keys
        val counts = restored.trackedCounts.toMutableMap()
        countKeys.forEach { key ->
            val delta = current.trackedCounts.getOrDefault(key, 0L) - baseline.trackedCounts.getOrDefault(key, 0L)
            if (delta > 0L) counts[key] = counts.getOrDefault(key, 0L) + delta
        }
        val preferences = restored.preferences.toMutableMap()
        (baseline.preferences.keys + current.preferences.keys).forEach { key ->
            if (baseline.preferences[key] != current.preferences[key]) {
                current.preferences[key]?.let { preferences[key] = it } ?: preferences.remove(key)
            }
        }
        val challenges = restored.challengeStates.toMutableMap()
        (baseline.challengeStates.keys + current.challengeStates.keys).forEach { key ->
            if (baseline.challengeStates[key] != current.challengeStates[key]) {
                current.challengeStates[key]?.let { challenges[key] = it } ?: challenges.remove(key)
            }
        }
        val concurrentTimeline = current.timeline.toMutableList().also { remaining ->
            baseline.timeline.forEach { entry -> remaining.remove(entry) }
        }
        return restored.copy(
            trackedCounts = counts,
            capturedDexNumbers = restored.capturedDexNumbers + (current.capturedDexNumbers - baseline.capturedDexNumbers),
            evolvedIndividualKeys = restored.evolvedIndividualKeys +
                (current.evolvedIndividualKeys - baseline.evolvedIndividualKeys),
            visitedAreaIds = restored.visitedAreaIds + (current.visitedAreaIds - baseline.visitedAreaIds),
            discoveredPoiIds = restored.discoveredPoiIds + (current.discoveredPoiIds - baseline.discoveredPoiIds),
            challengeStates = challenges,
            timeline = restored.timeline + concurrentTimeline,
            preferences = preferences,
        ).sanitizedAndCompacted()
    }

    private fun accept(event: GameEvent) {
        when (event) {
            is GameEvent.Captured -> {
                if (event.dexNumber !in journal.capturedDexNumbers) {
                    journal = journal.copy(capturedDexNumbers = journal.capturedDexNumbers + event.dexNumber)
                    increment("captures")
                }
            }
            is GameEvent.Evolved -> {
                val occurrence = "${event.individualKey}:${event.fromSpeciesId}:${event.toSpeciesId}"
                if (occurrence !in journal.evolvedIndividualKeys) {
                    journal = journal.copy(evolvedIndividualKeys = journal.evolvedIndividualKeys + occurrence)
                    increment("evolutions")
                }
            }
            is GameEvent.AreaVisited -> {
                if (event.areaBaseId !in journal.visitedAreaIds) {
                    journal = journal.copy(visitedAreaIds = journal.visitedAreaIds + event.areaBaseId)
                    increment("areas")
                }
            }
            is GameEvent.PoiDiscovered -> {
                if (event.poiId !in journal.discoveredPoiIds) {
                    journal = journal.copy(discoveredPoiIds = journal.discoveredPoiIds + event.poiId)
                    increment("pois")
                }
            }
            is GameEvent.BattleStarted -> {
                increment("battles")
                when (event.encounterKind?.uppercase()) {
                    "WILD" -> increment("wildEncounters")
                    "TRAINER" -> increment("trainerBattles")
                }
            }
            is GameEvent.PartyChanged -> increment("partyChanges")
            is GameEvent.BattleEnded -> Unit
            is GameEvent.SaveObserved -> {
                if (journal.timeline.lastOrNull()?.saveFingerprint != event.fingerprint.lowercase()) {
                    increment("saves")
                    freezeTimeline(event.fingerprint)
                }
            }
        }
    }

    private fun increment(key: String) {
        journal = journal.copy(
            trackedCounts = journal.trackedCounts + (key to (journal.trackedCounts[key] ?: 0L) + 1L),
        )
        pendingDeltas[key] = (pendingDeltas[key] ?: 0L) + 1L
    }

    private fun freezeTimeline(fingerprint: String) {
        if (pendingDeltas.isEmpty() || journal.timeline.lastOrNull()?.saveFingerprint == fingerprint.lowercase()) return
        val entry = TimelineEntry(
            saveFingerprint = fingerprint.lowercase(),
            recordedAtEpochMs = clock(),
            deltas = pendingDeltas.toMap(),
            milestone = pendingDeltas.keys.any { it == "captures" || it == "evolutions" || it == "challenges" },
        )
        journal = journal.copy(timeline = journal.timeline + entry).sanitizedAndCompacted()
        pendingDeltas.clear()
    }
}

class PlaythroughJournalRegistry(
    private val clock: () -> Long = System::currentTimeMillis,
) : PlaythroughJournalSession {
    private val coordinators = linkedMapOf<PlaythroughKey, PlaythroughJournalCoordinator>()

    @Synchronized
    fun accept(playthrough: PlaythroughKey, events: List<GameEvent>) {
        coordinator(playthrough).accept(events)
    }

    @Synchronized
    fun updatePreferences(playthrough: PlaythroughKey, changes: Map<String, String>) {
        coordinator(playthrough).updatePreferences(changes)
    }

    @Synchronized
    fun updateChallengeStates(playthrough: PlaythroughKey, states: Map<String, ChallengeJournalState>) {
        coordinator(playthrough).updateChallengeStates(states)
    }

    @Synchronized
    override fun restore(restored: PlaythroughJournal): Boolean = coordinator(restored.playthrough).restore(restored)

    @Synchronized
    override fun captureForRestore(playthrough: PlaythroughKey): JournalRestoreBaseline =
        requireNotNull(coordinator(playthrough).captureForRestore(playthrough))

    @Synchronized
    override fun restore(restored: PlaythroughJournal, baseline: JournalRestoreBaseline?): Boolean =
        coordinator(restored.playthrough).restore(restored, baseline)

    @Synchronized
    override fun current(playthrough: PlaythroughKey): PlaythroughJournal = coordinator(playthrough).current()

    private fun coordinator(playthrough: PlaythroughKey) = coordinators.getOrPut(playthrough) {
        PlaythroughJournalCoordinator(playthrough, clock)
    }
}
