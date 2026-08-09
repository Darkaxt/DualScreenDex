package com.enrpau.dualscreendex.simulator

import com.enrpau.dualscreendex.parser.catalog.BaseStats
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.LearnsetEntry
import com.enrpau.dualscreendex.parser.catalog.LearnsetRuleset
import com.enrpau.dualscreendex.parser.catalog.MoveCategory
import com.enrpau.dualscreendex.parser.catalog.MoveRecord
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncounterSimulatorTest {
    @Test
    fun isDeterministicAndUsesOnlyLevelEligibleMoves() {
        val simulator = EncounterSimulator(catalog())
        val request = SimulationRequest(seed = 44, opponentCount = 2, minimumLevel = 10, maximumLevel = 10)

        val first = simulator.generate(request)
        val second = simulator.generate(request)

        assertEquals(first, second)
        assertEquals(2, first.battle.opponents.size)
        assertTrue(first.battle.opponents.all { opponent -> opponent.moveHistory.all { it.moveId == 1 } })
    }

    @Test
    fun usesGenerationSpecificInnateValues() {
        val generationOne = EncounterSimulator(catalog(Platform.GB)).generate(
            SimulationRequest(seed = 8, captured = true),
        )
        val generationThree = EncounterSimulator(catalog(Platform.GBA)).generate(
            SimulationRequest(seed = 8, captured = true),
        )

        assertEquals(4, generationOne.battle.opponents.single().dvs.size)
        assertTrue(generationOne.battle.opponents.single().ivs.isEmpty())
        assertEquals(6, generationThree.battle.opponents.single().ivs.size)
        assertTrue(generationThree.battle.opponents.single().dvs.isEmpty())
        assertEquals(generationOne.battle.opponents.single().dvs, generationOne.ledger.owned.first().dvs)
        assertEquals(generationThree.battle.opponents.single().ivs, generationThree.ledger.owned.first().ivs)
    }

    @Test
    fun usesTheSelectedResidentRulesetWithoutRebuildingTheCatalog() {
        val base = catalog()
        val variants = listOf(
            LearnsetRuleset(
                id = "base",
                label = "Base",
                sourceOffset = 100,
                confidence = 1.0,
                entriesBySpecies = base.speciesById.mapValues { listOf(LearnsetEntry(5, 1)) },
                primary = true,
            ),
            LearnsetRuleset(
                id = "expanded",
                label = "Expanded",
                sourceOffset = 200,
                confidence = 1.0,
                entriesBySpecies = base.speciesById.mapValues { listOf(LearnsetEntry(5, 2)) },
            ),
        )
        val simulator = EncounterSimulator(base.copy(learnsetRulesets = variants))
        val request = SimulationRequest(seed = 44, minimumLevel = 10, maximumLevel = 10)

        val original = simulator.generate(request, activeRulesetId = "base")
        val expanded = simulator.generate(request, activeRulesetId = "expanded")

        assertTrue(original.battle.opponents.single().moveHistory.all { it.moveId == 1 })
        assertTrue(expanded.battle.opponents.single().moveHistory.all { it.moveId == 2 })
    }

    private fun catalog(platform: Platform = Platform.GBA): ParsedCatalog {
        val sprite = RgbaSprite(1, 1, intArrayOf(0xFFFFFFFF.toInt()))
        val species = (1..3).associateWith { id ->
            SpeciesRecord(
                id = id,
                dexNumber = CatalogField.available(id),
                name = CatalogField.available("SPECIES$id"),
                typeIds = CatalogField.available(listOf(1)),
                baseStats = CatalogField.available(BaseStats(50, 50, 50, 50, 50, 50)),
                sprite = CatalogField.available(sprite),
                learnset = CatalogField.available(listOf(LearnsetEntry(5, 1), LearnsetEntry(20, 2))),
            )
        }
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
        return ParsedCatalog("hash", EngineFamily.EMERALD, platform, speciesById = species, movesById = moves)
    }
}
