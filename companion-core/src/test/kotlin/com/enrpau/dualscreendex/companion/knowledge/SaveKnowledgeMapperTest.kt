package com.enrpau.dualscreendex.companion.knowledge

import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.save.SavedArea
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.model.MoveObservation
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveKnowledgeMapperTest {
    @Test
    fun replacesOnlySaveDerivedKnowledgeAndMapsDexFlagsToCatalogSpecies() {
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            speciesById = mapOf(
                6 to species(6, 6),
                25 to species(25, 25),
            ),
        )
        val prior = KnowledgeLedger(observedMoves = mapOf(6 to listOf(MoveObservation(52, 2))))
        val snapshot = SaveSnapshot(
            romIdentity = catalog.romSha256,
            saveIdentity = "save",
            saveGeneration = 3,
            saveCounter = 4,
            currentArea = SavedArea(2, 3),
            seenDexNumbers = setOf(6, 25),
            caughtDexNumbers = setOf(6),
            party = listOf(
                OwnedIndividual("party-0", 6, level = 20, isEgg = false, ivs = List(6) { 20 }, captureBallId = 4),
                OwnedIndividual("party-1", 25, level = 5, isEgg = true, ivs = List(6) { 31 }, captureBallId = 2),
            ),
            storedIndividuals = listOf(
                OwnedIndividual("box-0", 6, level = 10, ivs = List(6) { 31 }, captureBallId = 3),
            ),
            capabilities = emptyMap(),
        )

        val ledger = SaveKnowledgeMapper.merge(prior, catalog, snapshot)

        assertEquals(setOf(6, 25), ledger.seenSpecies)
        assertEquals(setOf(6), ledger.caughtSpecies)
        assertEquals(setOf(6), ledger.teamSpecies)
        assertEquals(0x0203, ledger.currentAreaBaseId)
        assertEquals(3, ledger.owned.size)
        assertTrue(ledger.owned.single { it.stableKey == "party-1" }.isEgg)
        assertFalse(ledger.owned.single { it.stableKey == "box-0" }.party)
        assertEquals(prior.observedMoves, ledger.observedMoves)
    }

    private fun species(id: Int, dex: Int) = SpeciesRecord(
        id = id,
        dexNumber = CatalogField.available(dex),
        name = CatalogField.available("Species $id"),
        typeIds = CatalogField.available(emptyList()),
        baseStats = CatalogField.notFound("fixture"),
        sprite = CatalogField.notFound("fixture"),
    )
}
