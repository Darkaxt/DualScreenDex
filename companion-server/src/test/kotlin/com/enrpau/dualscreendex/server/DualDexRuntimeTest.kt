package com.enrpau.dualscreendex.server

import com.enrpau.dualscreendex.parser.catalog.LearnsetRuleset
import com.enrpau.dualscreendex.parser.catalog.BaseStats
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.LearnsetEntry
import com.enrpau.dualscreendex.parser.catalog.MoveCategory
import com.enrpau.dualscreendex.parser.catalog.MoveRecord
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DualDexRuntimeTest {
    @Test
    fun autoRemainsUnresolvedWithoutSaveDetectionAndManualSelectionOnlyChangesActiveId() {
        val runtime = DualDexRuntime()
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog(
                "sha",
                EngineFamily.EMERALD,
                Platform.GBA,
                learnsetRulesets = listOf(
                    LearnsetRuleset("base", "Base", 100, 1.0, emptyMap(), primary = true),
                    LearnsetRuleset("modern", "Expanded", 200, 1.0, emptyMap()),
                ),
            ),
        )

        assertEquals(null, runtime.stateView().activeRulesetId)
        assertTrue(runtime.stateView().rulesetAssumed)

        val selected = runtime.action("SETTINGS", mapOf("ruleset" to "modern"))
        assertEquals("modern", selected.activeRulesetId)
        assertEquals(false, selected.rulesetAssumed)

        assertThrows(IllegalArgumentException::class.java) {
            runtime.action("SETTINGS", mapOf("ruleset" to "missing"))
        }
        assertEquals("modern", runtime.stateView().activeRulesetId)
    }

    @Test
    fun generateFailsClosedForUnresolvedMultiTableAutoButAllowsManualSingleAndNoRulesetCases() {
        val runtime = DualDexRuntime()
        runtime.loadCatalog("multi.gba", simulationCatalog(multiTable = true))

        val unresolved = assertThrows(IllegalArgumentException::class.java) {
            runtime.action("GENERATE", mapOf("seed" to "1", "minimumLevel" to "10", "maximumLevel" to "10"))
        }
        assertEquals("level-up ruleset is unresolved for this multi-table catalog", unresolved.message)

        runtime.action("SETTINGS", mapOf("ruleset" to "modern"))
        val manual = runtime.action("GENERATE", mapOf("seed" to "1", "minimumLevel" to "10", "maximumLevel" to "10"))
        assertEquals(listOf(2), manual.battle?.opponents?.single()?.moves?.map { it.moveId })

        runtime.loadCatalog("changed.gba", simulationCatalog(multiTable = false))
        val staleManual = assertThrows(IllegalArgumentException::class.java) {
            runtime.action("GENERATE", mapOf("seed" to "1"))
        }
        assertEquals("unknown catalog ruleset: modern", staleManual.message)
        runtime.close()

        val single = DualDexRuntime()
        single.loadCatalog("single.gba", simulationCatalog(multiTable = false))
        assertTrue(single.action("GENERATE", mapOf("seed" to "1")).battle != null)
        single.close()

        val none = DualDexRuntime()
        none.loadCatalog("none.gba", simulationCatalog(multiTable = false).copy(learnsetRulesets = emptyList()))
        assertTrue(none.action("GENERATE", mapOf("seed" to "1")).battle != null)
        none.close()
    }

    private fun simulationCatalog(multiTable: Boolean): ParsedCatalog {
        val sprite = RgbaSprite(1, 1, intArrayOf(0xFFFFFFFF.toInt()))
        val species = SpeciesRecord(
            id = 1,
            dexNumber = CatalogField.available(1),
            name = CatalogField.available("SPECIES"),
            typeIds = CatalogField.available(listOf(1)),
            baseStats = CatalogField.available(BaseStats(50, 50, 50, 50, 50, 50)),
            sprite = CatalogField.available(sprite),
            learnset = CatalogField.available(listOf(LearnsetEntry(5, 1))),
        )
        val moves = (1..2).associateWith { id ->
            MoveRecord(
                id,
                CatalogField.available("MOVE$id"),
                CatalogField.available(1),
                CatalogField.available(MoveCategory.PHYSICAL),
                CatalogField.available(40),
                CatalogField.available(100),
                CatalogField.available(35),
            )
        }
        val rulesets = listOf(
            LearnsetRuleset("original", "Original", 1, 1.0, mapOf(1 to listOf(LearnsetEntry(5, 1))), primary = true),
            LearnsetRuleset("modern", "Modern", 2, 1.0, mapOf(1 to listOf(LearnsetEntry(5, 2)))),
        ).let { if (multiTable) it else it.take(1) }
        return ParsedCatalog(
            "hash",
            EngineFamily.EMERALD,
            Platform.GBA,
            speciesById = mapOf(1 to species),
            movesById = moves,
            learnsetRulesets = rulesets,
        )
    }
}
