package com.darkaxt.dualdex.live

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedTransientArchitectureTest {
    @Test
    fun productionHasOneTransientProjectionAndNoLegacyMergePath() {
        val productionSources = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val runtime = File("src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt").readText()
        val coordinator = File("src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt").readText()
        val models = File("../companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt").readText()
        val api = File("../companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt").readText()

        assertTrue(runtime.contains("CompanionAction.ResolvedGameStateChanged"))
        listOf(
            "ResolvedPlayerStateChanged",
            "ResolvedPartyStateChanged",
            "ResolvedOverworldStateChanged",
            "CompanionAction.BattleStarted",
            "CompanionAction.BattleUpdated",
            "CompanionAction.BattleEnded",
        ).forEach { forbidden -> assertFalse("legacy runtime route: $forbidden", runtime.contains(forbidden)) }

        assertFalse(coordinator.contains("publisher"))
        assertTrue(coordinator.contains("UnifiedGameStateDecoder"))
        assertEquals(
            1,
            productionSources
                .filterNot { it.name == "UnifiedGameStateDecoder.kt" }
                .sumOf { source ->
                    Regex("\\bUnifiedGameStateDecoder\\s*(?:\\(|\\{)").findAll(source.readText()).count()
                },
        )
        assertEquals(
            1,
            productionSources.sumOf { source ->
                Regex("\\btransientGameState\\.subscribe\\s*\\{").findAll(source.readText()).count()
            },
        )

        listOf(
            "ResolvedPlayerStateChanged",
            "ResolvedPartyStateChanged",
            "ResolvedOverworldStateChanged",
            "data class BattleStarted",
            "data class BattleUpdated",
            "data object BattleEnded",
            "val trainer: TrainerSnapshot",
            "val trainerIdentity: TrainerIdentity",
        ).forEach { forbidden -> assertFalse("legacy model route: $forbidden", models.contains(forbidden)) }

        listOf(
            "snapshot.ledger.caughtSpecies",
            "snapshot.ledger.owned",
            "snapshot.trainerIdentity",
            "snapshot.trainer?",
        ).forEach { forbidden -> assertFalse("normal UI fallback: $forbidden", api.contains(forbidden)) }

        assertFalse(File("../companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/knowledge/LivePartyKnowledgeMapper.kt").exists())
    }
}
