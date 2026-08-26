package com.darkaxt.dualdex.progress

import com.enrpau.dualscreendex.companion.api.ChallengeView
import com.enrpau.dualscreendex.companion.api.ProgressMetricView
import com.enrpau.dualscreendex.companion.api.TimelineEntryView
import com.enrpau.dualscreendex.companion.api.TrainerProgressView
import com.enrpau.dualscreendex.companion.model.AppSnapshot

object TrainerProgressProjector {
    fun project(
        snapshot: AppSnapshot,
        journal: PlaythroughJournal,
        challenges: ChallengeEvaluation,
    ): TrainerProgressView {
        val trainer = snapshot.trainerCardState
        val gameTotals = listOf(
            ProgressMetricView("play-time", "Play time", trainer?.let { state ->
                val hours = state.playTimeHours ?: return@let null
                val minutes = state.playTimeMinutes ?: return@let null
                hours.toLong() * 60L + minutes
            }),
            ProgressMetricView("badges", "Badges", trainer?.badgeFlags?.countOneBits()?.toLong()),
            ProgressMetricView("seen", "Pokédex seen", trainer?.dexSeen?.toLong()),
            ProgressMetricView("caught", "Pokédex caught", trainer?.dexCaught?.toLong()),
            ProgressMetricView("money", "Money", trainer?.money),
        )
        val trackedLabels = linkedMapOf(
            "battles" to "Battles",
            "captures" to "Captures",
            "evolutions" to "Evolutions",
            "areas" to "Areas visited",
            "pois" to "Points discovered",
            "saves" to "Saves observed",
            "challenges" to "Challenges completed",
        )
        val trackedJourney = trackedLabels.map { (key, label) ->
            ProgressMetricView(key, label, journal.trackedCounts[key] ?: 0L)
        }
        return TrainerProgressView(
            selectedDestination = journal.preferences["trainer-destination"]
                ?.takeIf { it == "CARD" || it == "PROGRESS" }
                ?: "CARD",
            selectedSection = journal.preferences["trainer-progress-section"]
                ?.takeIf { it == "METRICS" || it == "CHALLENGES" || it == "TIMELINE" }
                ?: "METRICS",
            gameTotals = gameTotals,
            trackedJourney = trackedJourney,
            challenges = challenges.visible.map { result ->
                ChallengeView(
                    key = result.definition.key,
                    title = result.definition.title,
                    description = result.definition.description,
                    category = result.definition.category.name,
                    progress = result.progress,
                    target = result.target,
                    complete = result.complete,
                )
            },
            timeline = journal.timeline.asReversed().map { entry ->
                TimelineEntryView(
                    recordedAtEpochMs = entry.recordedAtEpochMs,
                    changes = entry.deltas.mapNotNull { (key, amount) ->
                        trackedLabels[key]?.let { label -> "$label +$amount" }
                    },
                    milestone = entry.milestone,
                )
            }.filter { it.changes.isNotEmpty() },
        )
    }
}
