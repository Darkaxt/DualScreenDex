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
        assertTrue(
            ChallengePredicate.SetContainsAll("types", setOf("FIRE", "WATER"))
                .evaluate(context)
                .complete,
        )
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
    fun `catalog roles adapters and organic knowledge fail closed independently`() {
        val engine = ChallengeEngine()
        val definitions = listOf(
            definition("badge", setOf("PROGRESSION_FACTS")).copy(
                requiredCatalogEntities = setOf("BADGE_SEQUENCE"),
            ),
            definition("leader", setOf("BATTLE_EVENTS")).copy(
                requiredCatalogEntities = setOf("GYM_LEADER:leader-1"),
            ),
            definition("minigame", setOf("MINIGAME_ADAPTER")).copy(
                requiredAdapters = setOf("MINIGAME:adapter-1"),
            ),
            definition("area", setOf("COMPLETION_FACTS")).copy(
                requiredCatalogEntities = setOf("AREA_COLLECTIBLES:area-1"),
                requiredKnowledgeEntities = setOf("AREA:area-1"),
            ),
        )

        val context = ChallengeContext(
            metrics = mapOf("captures" to 1),
            capabilities = setOf("PROGRESSION_FACTS", "BATTLE_EVENTS", "MINIGAME_ADAPTER", "COMPLETION_FACTS"),
            resolvedCatalogEntities = setOf("BADGE_SEQUENCE", "AREA_COLLECTIBLES:area-1"),
            provenAdapters = emptySet(),
            knownCatalogEntities = emptySet(),
            organicMode = true,
        )

        val evaluation = engine.evaluate(definitions, context, emptyMap(), nowEpochMs = 500, saveFingerprint = null)

        assertEquals(listOf("badge"), evaluation.visible.map { it.definition.key })
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
    fun `organic disclosure keeps completed and next tiers while discovered mode exposes the applicable inventory`() {
        val engine = ChallengeEngine()
        val first = definition("first-partner", setOf("POKEDEX_FACTS")).copy(
            progressionGroup = "captured-species",
            progressionRank = 1,
            predicate = ChallengePredicate.CountAtLeast("captures", 1),
        )
        val growing = definition("growing-roster", setOf("POKEDEX_FACTS")).copy(
            progressionGroup = "captured-species",
            progressionRank = 2,
            predicate = ChallengePredicate.CountAtLeast("captures", 10),
        )
        val organicAtZero = ChallengeContext(
            metrics = mapOf("captures" to 0),
            capabilities = setOf("POKEDEX_FACTS"),
            organicMode = true,
        )

        val initial = engine.evaluate(listOf(first, growing), organicAtZero, emptyMap(), nowEpochMs = 1, saveFingerprint = null)

        assertEquals(listOf("first-partner"), initial.visible.map { it.definition.key })
        assertEquals(2, initial.applicableCount)
        assertEquals(0, initial.completedCount)

        val afterFirst = engine.evaluate(
            listOf(first, growing),
            organicAtZero.copy(metrics = mapOf("captures" to 1)),
            initial.states,
            nowEpochMs = 2,
            saveFingerprint = null,
        )

        assertEquals(listOf("first-partner", "growing-roster"), afterFirst.visible.map { it.definition.key })
        assertTrue(afterFirst.visible.first().complete)
        assertEquals(2, afterFirst.applicableCount)
        assertEquals(1, afterFirst.completedCount)

        val discovered = engine.evaluate(
            listOf(first, growing),
            organicAtZero.copy(organicMode = false),
            emptyMap(),
            nowEpochMs = 3,
            saveFingerprint = null,
        )

        assertEquals(listOf("first-partner", "growing-roster"), discovered.visible.map { it.definition.key })
        assertEquals(2, discovered.applicableCount)
    }

    @Test
    fun `organic disclosure hides untouched off-scope challenges but retains current started and completed scopes`() {
        val engine = ChallengeEngine()
        fun scoped(key: String, scope: String, metric: String) = definition(key, setOf("POI_FACTS"), metric).copy(
            requiredKnowledgeEntities = setOf(scope),
            disclosureScope = scope,
            predicate = ChallengePredicate.CountAtLeast(metric, 3),
        )
        val current = scoped("current", "AREA:base-1", "area.current")
        val started = scoped("started", "AREA:base-2", "area.started")
        val untouched = scoped("untouched", "AREA:base-3", "area.untouched")
        val completed = scoped("completed", "AREA:base-4", "area.completed")
        val context = ChallengeContext(
            metrics = mapOf(
                "area.current" to 0,
                "area.started" to 1,
                "area.untouched" to 0,
                "area.completed" to 3,
            ),
            capabilities = setOf("POI_FACTS"),
            knownCatalogEntities = setOf("AREA:base-1", "AREA:base-2", "AREA:base-3", "AREA:base-4"),
            currentCatalogEntities = setOf("AREA:base-1"),
            organicMode = true,
        )

        val evaluation = engine.evaluate(
            listOf(current, started, untouched, completed),
            context,
            emptyMap(),
            nowEpochMs = 10,
            saveFingerprint = null,
        )

        assertEquals(listOf("current", "started", "completed"), evaluation.visible.map { it.definition.key })
        assertEquals(4, evaluation.applicableCount)
        assertEquals(1, evaluation.completedCount)
    }

    @Test
    fun `incremental evaluation keeps unaffected states and first completion reference`() {
        val engine = ChallengeEngine()
        val definitions = listOf(definition("capture", setOf("POKEDEX_FACTS")), definition("battle", setOf("BATTLE_FACTS"), "battles"))
        val prior = mapOf(
            "capture" to ChallengeJournalState(
                progress = 1,
                target = 1,
                completedAtEpochMs = 100,
                completedAtSaveFingerprint = "a".repeat(64),
            ),
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
        assertEquals(1L, result.visible.single { it.definition.key == "capture" }.target)
        assertEquals(500L, result.states.getValue("battle").completedAtEpochMs)
    }

    @Test
    fun `reset pause miss and completion lifecycles remain independent`() {
        val engine = ChallengeEngine()
        val definition = definition("streak", setOf("BATTLE_FACTS"), "wins").copy(
            predicate = ChallengePredicate.CountAtLeast("wins", 3),
            resetWhen = ChallengePredicate.BooleanFact("session.reset"),
            pauseWhen = ChallengePredicate.BooleanFact("session.paused"),
            missWhen = ChallengePredicate.BooleanFact("session.failed"),
        )
        val active = ChallengeContext(
            metrics = mapOf("wins" to 2),
            capabilities = setOf("BATTLE_FACTS"),
        )

        val started = engine.evaluate(listOf(definition), active, emptyMap(), nowEpochMs = 1, saveFingerprint = null)
        assertEquals(2, started.states.getValue("streak").progress)
        assertEquals(3L, started.states.getValue("streak").target)

        val paused = engine.evaluate(
            listOf(definition),
            active.copy(metrics = mapOf("wins" to 3), booleans = mapOf("session.paused" to true)),
            started.states,
            nowEpochMs = 2,
            saveFingerprint = null,
        )
        assertEquals(2, paused.states.getValue("streak").progress)
        assertTrue(paused.states.getValue("streak").paused)

        val missed = engine.evaluate(
            listOf(definition),
            active.copy(booleans = mapOf("session.failed" to true)),
            paused.states,
            nowEpochMs = 3,
            saveFingerprint = null,
        )
        assertTrue(missed.states.getValue("streak").missed)
        assertFalse(missed.visible.single().complete)

        val stillMissed = engine.evaluate(
            listOf(definition),
            active.copy(metrics = mapOf("wins" to 3)),
            missed.states,
            nowEpochMs = 4,
            saveFingerprint = null,
        )
        assertTrue(stillMissed.states.getValue("streak").missed)

        val reset = engine.evaluate(
            listOf(definition),
            active.copy(booleans = mapOf("session.reset" to true)),
            missed.states,
            nowEpochMs = 5,
            saveFingerprint = null,
        )
        assertEquals(0, reset.states.getValue("streak").progress)
        assertFalse(reset.states.getValue("streak").paused)
        assertFalse(reset.states.getValue("streak").missed)

        val completed = engine.evaluate(
            listOf(definition),
            active.copy(metrics = mapOf("wins" to 3)),
            reset.states,
            nowEpochMs = 6,
            saveFingerprint = null,
        )
        assertTrue(completed.visible.single().complete)
        assertEquals(3L, completed.visible.single().target)
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
