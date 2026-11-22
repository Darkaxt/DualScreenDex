package com.darkaxt.dualdex.progress

import com.enrpau.dualscreendex.companion.api.ChallengeSummaryView
import com.enrpau.dualscreendex.companion.api.ChallengeView
import com.enrpau.dualscreendex.companion.api.PresentationMessages
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
        val gameTotals = listOfNotNull(
            metric("play-time", trainer?.let { state ->
                val hours = state.playTimeHours ?: return@let null
                val minutes = state.playTimeMinutes ?: return@let null
                hours.toLong() * 60L + minutes
            }),
            metric("badges", trainer?.badgeFlags?.countOneBits()?.toLong()),
            metric("seen", trainer?.dexSeen?.toLong()),
            metric("caught", trainer?.dexCaught?.toLong()),
            metric("money", trainer?.money),
        )
        val trackedKeys = listOf(
            "battles",
            "wildEncounters",
            "trainerBattles",
            "captures",
            "evolutions",
            "areas",
            "pois",
            "partyChanges",
            "saves",
            "challenges",
        )
        val trackedJourney = trackedKeys.mapNotNull { key ->
            metric(key, journal.trackedCounts[key] ?: 0L)
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
            challengeSummary = ChallengeSummaryView(
                completed = challenges.completedCount,
                applicable = challenges.applicableCount,
                completionPercent = percentage(
                    current = challenges.completedCount.toLong(),
                    target = challenges.applicableCount.toLong(),
                    complete = challenges.applicableCount > 0 && challenges.completedCount >= challenges.applicableCount,
                ),
            ),
            challenges = challenges.visible.mapNotNull { result ->
                val definition = result.definition
                val title = PresentationMessages.challengeTitle(
                    definition.presentationKey,
                    definition.presentationSubject,
                ) ?: return@mapNotNull null
                val description = PresentationMessages.challengeDescription(
                    definition.presentationKey,
                    definition.presentationSubject,
                ) ?: return@mapNotNull null
                ChallengeView(
                    key = definition.key,
                    title = title,
                    description = description,
                    category = definition.category.name,
                    progress = result.progress,
                    target = result.target,
                    completionPercent = percentage(result.progress, result.target, result.complete),
                    complete = result.complete,
                )
            },
            timeline = journal.timeline.asReversed().map { entry ->
                TimelineEntryView(
                    recordedAtEpochMs = entry.recordedAtEpochMs,
                    changes = entry.deltas.mapNotNull { (key, amount) ->
                        PresentationMessages.timelineChange(key, amount)
                    },
                    milestone = entry.milestone,
                )
            }.filter { it.changes.isNotEmpty() },
        )
    }

    private fun metric(key: String, value: Long?) = PresentationMessages.progressMetric(key)?.let {
        ProgressMetricView(key, it, value)
    }

    private fun percentage(current: Long?, target: Long?, complete: Boolean): Int? {
        if (complete) return 100
        if (current == null || target == null || target <= 0L) return null
        return ((current.coerceIn(0L, target).toDouble() / target.toDouble()) * 100.0).toInt()
    }
}
