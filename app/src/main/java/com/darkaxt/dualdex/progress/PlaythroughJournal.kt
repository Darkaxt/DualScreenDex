package com.darkaxt.dualdex.progress

import com.enrpau.dualscreendex.companion.semantic.PlaythroughKey

data class ChallengeJournalState(
    val progress: Long = 0,
    val completedAtEpochMs: Long? = null,
    val completedAtSaveFingerprint: String? = null,
)

data class TimelineEntry(
    val saveFingerprint: String,
    val recordedAtEpochMs: Long,
    val deltas: Map<String, Long>,
    val milestone: Boolean = false,
)

data class PlaythroughJournal(
    val schema: Int = SCHEMA,
    val playthrough: PlaythroughKey,
    val trackedCounts: Map<String, Long> = emptyMap(),
    val capturedDexNumbers: Set<Int> = emptySet(),
    val evolvedIndividualKeys: Set<String> = emptySet(),
    val visitedAreaIds: Set<Int> = emptySet(),
    val discoveredPoiIds: Set<String> = emptySet(),
    val challengeStates: Map<String, ChallengeJournalState> = emptyMap(),
    val timeline: List<TimelineEntry> = emptyList(),
    val preferences: Map<String, String> = emptyMap(),
) {
    fun retainedItemCount(): Int =
        trackedCounts.size +
            capturedDexNumbers.size +
            evolvedIndividualKeys.size +
            visitedAreaIds.size +
            discoveredPoiIds.size +
            challengeStates.size +
            timeline.sumOf { 1 + it.deltas.size } +
            preferences.size

    fun sanitizedAndCompacted(limit: Int = MAX_TIMELINE_ENTRIES): PlaythroughJournal {
        require(limit > 0)
        val cleanTimeline = timeline.mapNotNull { entry ->
            val fingerprint = entry.saveFingerprint.lowercase()
            val deltas = entry.deltas.cleanCounts()
            if (!fingerprint.matches(SHA256) || entry.recordedAtEpochMs < 0 || deltas.isEmpty()) null
            else entry.copy(saveFingerprint = fingerprint, deltas = deltas)
        }
        return copy(
            schema = SCHEMA,
            trackedCounts = trackedCounts.cleanCounts(allowZero = true),
            capturedDexNumbers = capturedDexNumbers.filterTo(sortedSetOf()) { it > 0 },
            evolvedIndividualKeys = evolvedIndividualKeys.cleanStrings(),
            visitedAreaIds = visitedAreaIds.filterTo(sortedSetOf()) { it in 0..0xffff },
            discoveredPoiIds = discoveredPoiIds.cleanStrings(),
            challengeStates = challengeStates.entries
                .mapNotNull { (key, value) -> cleanToken(key)?.let { it to value.sanitized() } }
                .toMap(),
            timeline = compactTimeline(cleanTimeline, limit),
            preferences = preferences.entries.mapNotNull { (key, value) ->
                val cleanKey = cleanToken(key)
                val cleanValue = value.trim().take(MAX_TOKEN_LENGTH).takeIf { it.isNotEmpty() }
                if (cleanKey == null || cleanValue == null) null else cleanKey to cleanValue
            }.toMap(),
        )
    }

    private fun ChallengeJournalState.sanitized() = copy(
        progress = progress.coerceAtLeast(0),
        completedAtEpochMs = completedAtEpochMs?.takeIf { it >= 0 },
        completedAtSaveFingerprint = completedAtSaveFingerprint
            ?.lowercase()
            ?.takeIf { it.matches(SHA256) },
    )

    private fun Map<String, Long>.cleanCounts(allowZero: Boolean = false) = entries.mapNotNull { (key, value) ->
        val cleanKey = cleanToken(key) ?: return@mapNotNull null
        val cleanValue = value.coerceAtLeast(0)
        if (!allowZero && cleanValue == 0L) null else cleanKey to cleanValue
    }.toMap()

    private fun Set<String>.cleanStrings() = mapNotNull(::cleanToken).toSortedSet()

    private fun compactTimeline(entries: List<TimelineEntry>, limit: Int): List<TimelineEntry> {
        if (entries.size <= limit) return entries
        val selected = linkedSetOf(0)
        entries.indices
            .filter { it != 0 && entries[it].milestone }
            .asReversed()
            .take(limit - selected.size)
            .forEach(selected::add)
        entries.indices
            .filter { it !in selected }
            .asReversed()
            .take(limit - selected.size)
            .forEach(selected::add)
        return selected.sorted().map(entries::get)
    }

    companion object {
        const val SCHEMA = 1
        const val MAX_TIMELINE_ENTRIES = 512
        private const val MAX_TOKEN_LENGTH = 128
        private val SHA256 = Regex("[0-9a-f]{64}")

        fun empty(playthrough: PlaythroughKey) = PlaythroughJournal(playthrough = playthrough)

        private fun cleanToken(value: String) = value.trim().take(MAX_TOKEN_LENGTH).takeIf { it.isNotEmpty() }
    }
}
