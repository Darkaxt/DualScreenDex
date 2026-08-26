package com.darkaxt.dualdex.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeEngineTest {
    @Test
    fun `bounded predicate vocabulary evaluates boolean count order set epoch and previous values`() {
        val context = ChallengeContext(
            metrics = mapOf("captures" to 10, "money" to 3000),
            booleans = mapOf("battle.won" to true),
            sets = mapOf("types" to setOf("FIRE", "WATER")),
            sequences = mapOf("areas" to listOf("HOME", "ROUTE", "TOWN")),
            epochs = mapOf("battle" to 4),
            previousValues = mapOf("party.size" to 1),
            currentValues = mapOf("party.size" to 2),
        )

        assertTrue(ChallengePredicate.BooleanFact("battle.won").evaluate(context).complete)
        assertTrue(ChallengePredicate.CountAtLeast("captures", 10).evaluate(context).complete)
        assertTrue(ChallengePredicate.Compare("money", NumericComparison.GREATER_THAN, 100).evaluate(context).complete)
        assertTrue(ChallengePredicate.SetContains("types", "FIRE").evaluate(context).complete)
        assertTrue(ChallengePredicate.SetSizeAtLeast("types", 2).evaluate(context).complete)
        assertTrue(ChallengePredicate.Ordered("areas", listOf("HOME", "TOWN")).evaluate(context).complete)
        assertTrue(ChallengePredicate.EpochAtLeast("battle", 4).evaluate(context).complete)
        assertTrue(ChallengePredicate.PreviousCompare("party.size", NumericComparison.GREATER_THAN).evaluate(context).complete)
        assertTrue(
            ChallengePredicate.All(
                listOf(
                    ChallengePredicate.BooleanFact("battle.won"),
                    ChallengePredicate.Not(ChallengePredicate.BooleanFact("battle.lost")),
                ),
            ).evaluate(context).complete,
        )
    }

    @Test
    fun `inapplicable and organic unsafe challenges are absent rather than labeled unsupported`() {
        val engine = ChallengeEngine()
        val definitions = listOf(
            definition("safe", setOf("POKEDEX_FACTS")),
            definition("missing", setOf("POI_FACTS")),
            definition("spoiler", setOf("POKEDEX_FACTS"), organicSafe = false),
        )
        val context = ChallengeContext(
            metrics = mapOf("captures" to 1),
            capabilities = setOf("POKEDEX_FACTS"),
            organicMode = true,
        )

        val evaluation = engine.evaluate(definitions, context, emptyMap(), nowEpochMs = 500, saveFingerprint = "a".repeat(64))

        assertEquals(listOf("safe"), evaluation.visible.map { it.definition.key })
        assertTrue(evaluation.visible.single().complete)
        assertEquals(500L, evaluation.states.getValue("safe").completedAtEpochMs)
        assertFalse(evaluation.states.containsKey("missing"))
        assertFalse(evaluation.states.containsKey("spoiler"))
    }

    @Test
    fun `incremental evaluation keeps unaffected states and first completion reference`() {
        val engine = ChallengeEngine()
        val definitions = listOf(definition("capture", setOf("POKEDEX_FACTS")), definition("battle", setOf("BATTLE_FACTS"), "battles"))
        val prior = mapOf(
            "capture" to ChallengeJournalState(1, completedAtEpochMs = 100, completedAtSaveFingerprint = "a".repeat(64)),
            "battle" to ChallengeJournalState(0),
        )
        val context = ChallengeContext(
            metrics = mapOf("captures" to 2, "battles" to 1),
            capabilities = setOf("POKEDEX_FACTS", "BATTLE_FACTS"),
        )

        val result = engine.evaluate(
            definitions,
            context,
            prior,
            changedDependencies = setOf("metric:battles"),
            nowEpochMs = 500,
            saveFingerprint = "b".repeat(64),
        )

        assertEquals(prior.getValue("capture"), result.states.getValue("capture"))
        assertEquals(500L, result.states.getValue("battle").completedAtEpochMs)
    }

    private fun definition(
        key: String,
        capabilities: Set<String>,
        metric: String = "captures",
        organicSafe: Boolean = true,
    ) = ChallengeDefinition(
        key = key,
        title = key,
        description = "A player-facing objective.",
        category = ChallengeCategory.COLLECTION,
        requiredCapabilities = capabilities,
        organicSafe = organicSafe,
        predicate = ChallengePredicate.CountAtLeast(metric, 1),
    )
}
