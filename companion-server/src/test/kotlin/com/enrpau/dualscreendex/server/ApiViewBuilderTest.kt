package com.enrpau.dualscreendex.server

import com.google.gson.Gson
import com.enrpau.dualscreendex.companion.api.ApiViewBuilder
import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.model.MoveObservation
import com.enrpau.dualscreendex.companion.model.OwnedPokemon
import com.enrpau.dualscreendex.parser.catalog.AbilityRecord
import com.enrpau.dualscreendex.parser.catalog.BaseStats
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.EncounterArea
import com.enrpau.dualscreendex.parser.catalog.EncounterSlot
import com.enrpau.dualscreendex.parser.catalog.EvolutionEdge
import com.enrpau.dualscreendex.parser.catalog.LearnsetEntry
import com.enrpau.dualscreendex.parser.catalog.LearnsetRuleset
import com.enrpau.dualscreendex.parser.catalog.MoveAcquisition
import com.enrpau.dualscreendex.parser.catalog.MoveAcquisitionMethod
import com.enrpau.dualscreendex.parser.catalog.MoveCategory
import com.enrpau.dualscreendex.parser.catalog.MoveRecord
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiViewBuilderTest {
    @Test
    fun exposesPersistentObservedMoveHistoryOutsideBattle() {
        val view = ApiViewBuilder.state(
            AppSnapshot(
                ledger = KnowledgeLedger(
                    observedMoves = mapOf(
                        4 to listOf(
                            MoveObservation(52, 1),
                            MoveObservation(33, 3),
                            MoveObservation(10, 3),
                        ),
                    ),
                ),
            ),
            null,
        )

        assertEquals(listOf(10, 33, 52), view.observedMoves.getValue(4).map { it.moveId })
        assertEquals(listOf(3, 3, 1), view.observedMoves.getValue(4).map { it.frequency })
    }

    @Test
    fun exposesOnlyMoveFrequencyWithoutARecencyMetric() {
        val view = ApiViewBuilder.state(
            AppSnapshot(
                ledger = KnowledgeLedger(
                    observedMoves = mapOf(4 to listOf(MoveObservation(10, 3))),
                ),
            ),
            null,
        )

        val json = Gson().toJson(view.observedMoves.getValue(4).single())

        assertTrue(json.contains("\"frequency\":3"))
        assertFalse(json.contains("encounters"))
        assertFalse(json.contains("lastSeen"))
    }

    @Test
    fun exposesCompleteCatalogRelationshipsAndEveryResidentRuleset() {
        val species = SpeciesRecord(
            id = 1,
            dexNumber = CatalogField.available(1),
            name = CatalogField.available("BULBA"),
            typeIds = CatalogField.available(listOf(12, 3)),
            baseStats = CatalogField.available(BaseStats(45, 49, 49, 45, 65, 65)),
            sprite = CatalogField.notFound("fixture"),
            evolutionEdges = CatalogField.available(listOf(EvolutionEdge(2, 4, 16))),
            learnset = CatalogField.available(listOf(LearnsetEntry(1, 1), LearnsetEntry(7, 1))),
            moveAcquisitions = CatalogField.available(listOf(MoveAcquisition(2, MoveAcquisitionMethod.EGG))),
            abilityIds = CatalogField.available(listOf(1)),
        )
        val catalog = ParsedCatalog(
            romSha256 = "sha",
            romCrc32 = "1234ABCD",
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            speciesById = mapOf(1 to species),
            movesById = mapOf(
                1 to MoveRecord(
                    1,
                    CatalogField.available("TACKLE"),
                    CatalogField.available(0),
                    CatalogField.available(MoveCategory.PHYSICAL),
                    CatalogField.available(40),
                    CatalogField.available(100),
                    CatalogField.available(35),
                    effectId = CatalogField.available(7),
                    effectText = CatalogField.available("Damages the target."),
                ),
            ),
            abilitiesById = mapOf(
                1 to AbilityRecord(
                    1,
                    CatalogField.available("OVERGROW"),
                    CatalogField.available("Ups Grass moves in a pinch."),
                ),
            ),
            encounterAreas = listOf(
                EncounterArea(9, CatalogField.available("ROUTE 1"), 0, listOf(EncounterSlot(1, 3, 5, 20))),
            ),
            learnsetRulesets = listOf(
                LearnsetRuleset(
                    "base",
                    "Base",
                    100,
                    1.0,
                    mapOf(1 to listOf(LearnsetEntry(1, 1), LearnsetEntry(7, 1))),
                    primary = true,
                ),
                LearnsetRuleset("modern", "Expanded 1", 200, 0.99, mapOf(1 to listOf(LearnsetEntry(5, 1)))),
            ),
        )

        val view = ApiViewBuilder.catalog(catalog)
        val pokemon = view.species.single()

        assertEquals("1234ABCD", view.crc32)
        assertEquals(listOf("base", "modern"), view.rulesets.map { it.id })
        assertEquals("Initial · Lv 7", pokemon.normalizedLearnsets.getValue("base").single().label)
        assertEquals("OVERGROW", pokemon.abilities.single().name)
        assertEquals("Ups Grass moves in a pinch.", pokemon.abilities.single().description)
        assertEquals("Level 16", pokemon.evolutions.single().condition)
        assertEquals("EGG", pokemon.moveAcquisitions.single().method)
        assertEquals(3, view.areas.single().slots.single().minimumLevel)
        assertEquals(20, view.areas.single().slots.single().weight)
        assertEquals(7, view.moves.single().effectId)
        assertEquals("Damages the target.", view.moves.single().description)
        assertTrue(pokemon.learnsets.getValue("modern").isNotEmpty())
    }

    @Test
    fun exposesCaughtFlagsAndEveryEncounterMethodForTheSavedCurrentArea() {
        val species = SpeciesRecord(
            id = 25,
            dexNumber = CatalogField.available(25),
            name = CatalogField.available("PIKACHU"),
            typeIds = CatalogField.available(emptyList()),
            baseStats = CatalogField.notFound("fixture"),
            sprite = CatalogField.notFound("fixture"),
        )
        val baseId = 0x0203
        val catalog = ParsedCatalog(
            romSha256 = "sha",
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            speciesById = mapOf(25 to species),
            encounterAreas = listOf(
                EncounterArea(baseId * 10, CatalogField.available("AREA - GRASS"), 0, listOf(EncounterSlot(25, 3, 5, 20))),
                EncounterArea(baseId * 10 + 1, CatalogField.available("AREA - SURF"), 1, listOf(EncounterSlot(25, 5, 7, 20))),
            ),
        )

        val view = ApiViewBuilder.state(
            AppSnapshot(
                ledger = KnowledgeLedger(
                    caughtSpecies = setOf(25),
                    currentAreaBaseId = baseId,
                    owned = listOf(OwnedPokemon("box-0", 25, 3, 31, ivs = List(6) { 30 })),
                ),
            ),
            catalog,
        )

        assertTrue(view.speciesState.getValue(25).caught)
        assertEquals(31, view.speciesState.getValue(25).preferredLevel)
        assertEquals("ACE", view.speciesState.getValue(25).innateTier)
        assertEquals(listOf(baseId * 10, baseId * 10 + 1), view.currentAreaIds)
    }
}
