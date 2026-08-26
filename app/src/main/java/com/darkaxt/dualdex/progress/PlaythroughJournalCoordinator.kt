package com.darkaxt.dualdex.progress

import com.enrpau.dualscreendex.companion.semantic.GameEvent
import com.enrpau.dualscreendex.companion.semantic.PlaythroughKey

interface PlaythroughJournalSession {
    fun restore(restored: PlaythroughJournal): Boolean
    fun current(playthrough: PlaythroughKey): PlaythroughJournal?
}

class PlaythroughJournalCoordinator(
    private val playthrough: PlaythroughKey,
    private val clock: () -> Long = System::currentTimeMillis,
) : PlaythroughJournalSession {
    private var journal = PlaythroughJournal.empty(playthrough)
    private val pendingDeltas = linkedMapOf<String, Long>()

    @Synchronized
    override fun restore(restored: PlaythroughJournal): Boolean {
        if (restored.playthrough != playthrough) return false
        journal = restored.sanitizedAndCompacted()
        pendingDeltas.clear()
        return true
    }

    @Synchronized
    fun accept(events: List<GameEvent>) {
        events.forEach(::accept)
    }

    @Synchronized
    fun current(): PlaythroughJournal = journal.sanitizedAndCompacted()

    @Synchronized
    override fun current(playthrough: PlaythroughKey): PlaythroughJournal? =
        current().takeIf { it.playthrough == playthrough }

    @Synchronized
    fun updatePreferences(changes: Map<String, String>) {
        journal = journal.copy(preferences = journal.preferences + changes).sanitizedAndCompacted()
    }

    @Synchronized
    fun updateChallengeStates(states: Map<String, ChallengeJournalState>) {
        val newlyCompleted = states.count { (key, state) ->
            state.completedAtEpochMs != null && journal.challengeStates[key]?.completedAtEpochMs == null
        }
        repeat(newlyCompleted) { increment("challenges") }
        journal = journal.copy(challengeStates = states).sanitizedAndCompacted()
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
            is GameEvent.BattleStarted -> increment("battles")
            is GameEvent.BattleEnded,
            is GameEvent.PartyChanged,
            -> Unit
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
    override fun current(playthrough: PlaythroughKey): PlaythroughJournal = coordinator(playthrough).current()

    private fun coordinator(playthrough: PlaythroughKey) = coordinators.getOrPut(playthrough) {
        PlaythroughJournalCoordinator(playthrough, clock)
    }
}
