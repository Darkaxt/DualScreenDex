package com.enrpau.dualscreendex.companion.knowledge

import com.darkaxt.dualdex.save.OwnedIndividual
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.model.OwnedPokemon
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePartyKnowledgeMapperTest {
    @Test
    fun replacesTheCurrentPartyWhilePreservingBoxesAndAccumulatedKnowledge() {
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            speciesById = mapOf(25 to species(25), 277 to species(277)),
        )
        val previous = KnowledgeLedger(
            seenSpecies = setOf(25),
            caughtSpecies = setOf(25),
            owned = listOf(
                OwnedPokemon("party-0", 25, 3, 10, party = true),
                OwnedPokemon("box-0", 25, 3, 8, party = false),
            ),
            teamSpecies = setOf(25),
        )

        val merged = LivePartyKnowledgeMapper.merge(
            previous,
            catalog,
            listOf(OwnedIndividual("party-0", 277, level = 5, ivs = List(6) { 20 }, captureBallId = 4)),
            generation = 3,
        )

        assertEquals(setOf(277), merged.teamSpecies)
        assertEquals(setOf(25, 277), merged.caughtSpecies)
        assertEquals(setOf(25, 277), merged.seenSpecies)
        assertEquals(listOf("box-0", "party-0"), merged.owned.map { it.stableKey })
        assertFalse(merged.owned.first().party)
        assertTrue(merged.owned.last().party)
    }

    @Test
    fun aValidatedEmptyLivePartyClearsOnlyTheTeam() {
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            speciesById = mapOf(25 to species(25)),
        )
        val previous = KnowledgeLedger(
            seenSpecies = setOf(25),
            caughtSpecies = setOf(25),
            owned = listOf(OwnedPokemon("party-0", 25, 3, 10, party = true)),
            teamSpecies = setOf(25),
        )

        val merged = LivePartyKnowledgeMapper.merge(previous, catalog, emptyList(), generation = 3)

        assertEquals(emptySet<Int>(), merged.teamSpecies)
        assertTrue(merged.owned.isEmpty())
        assertEquals(setOf(25), merged.caughtSpecies)
    }

    private fun species(id: Int) = SpeciesRecord(
        id = id,
        dexNumber = CatalogField.available(id),
        name = CatalogField.available("Species $id"),
        typeIds = CatalogField.available(emptyList()),
        baseStats = CatalogField.notFound("fixture"),
        sprite = CatalogField.notFound("fixture"),
    )
}
