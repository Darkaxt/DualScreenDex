package com.darkaxt.dualdex.progress

import com.darkaxt.dualdex.save.TrainerIdentity
import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.TrainerCardState
import com.enrpau.dualscreendex.companion.semantic.PlaythroughKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TrainerProgressProjectorTest {
    private val key = PlaythroughKey("a".repeat(64), "b".repeat(64))

    @Test
    fun `projects current game totals separately from tracked journey`() {
        val snapshot = AppSnapshot(
            trainerCardState = TrainerCardState(
                identity = TrainerIdentity("MAY", 1),
                publicTrainerId = 12345,
                money = 3000,
                playTimeHours = 2,
                playTimeMinutes = 15,
                badgeFlags = 0b101,
                dexSeen = 12,
                dexCaught = 4,
                stars = 1,
            ),
        )
        val journal = PlaythroughJournal.empty(key).copy(
            trackedCounts = mapOf("captures" to 2, "battles" to 7, "areas" to 3),
            preferences = mapOf(
                "trainer-destination" to "PROGRESS",
                "trainer-progress-section" to "TIMELINE",
            ),
            timeline = listOf(
                TimelineEntry("c".repeat(64), 500, mapOf("captures" to 1, "areas" to 1, "challenges" to 1)),
            ),
        )
        val definition = ChallengeDefinition(
            key = "capture",
            title = "A New Partner",
            description = "Catch your first Pokémon on this journey.",
            category = ChallengeCategory.COLLECTION,
            requiredCapabilities = setOf("POKEDEX_FACTS"),
            organicSafe = true,
            predicate = ChallengePredicate.CountAtLeast("captures", 1),
        )
        val evaluation = ChallengeEvaluation(
            visible = listOf(ChallengeResult(definition, 2, 1, true)),
            states = mapOf("capture" to ChallengeJournalState(2, 400, "c".repeat(64))),
        )

        val view = TrainerProgressProjector.project(snapshot, journal, evaluation)

        assertEquals("PROGRESS", view.selectedDestination)
        assertEquals("TIMELINE", view.selectedSection)
        assertEquals(3000L, view.gameTotals.single { it.key == "money" }.value)
        assertEquals(12L, view.gameTotals.single { it.key == "seen" }.value)
        assertEquals(4L, view.gameTotals.single { it.key == "caught" }.value)
        assertEquals(2L, view.trackedJourney.single { it.key == "captures" }.value)
        assertEquals(1, view.timeline.size)
        assertEquals(
            listOf("Captures +1", "Areas visited +1", "Challenges completed +1"),
            view.timeline.single().changes,
        )
        assertEquals("A New Partner", view.challenges.single().title)
        assertFalse(view.toString().contains("parser", ignoreCase = true))
        assertFalse(view.toString().contains("capability", ignoreCase = true))
    }
}
