package com.enrpau.dualscreendex.companion.knowledge

import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveKnowledgeMapperTest {
    @Test
    fun mapsRetailEmeraldSaveFlagsByNationalNumberInsteadOfRegionalDisplayNumber() {
        val catalog = ParsedCatalog(
            romSha256 = "b".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            speciesById = mapOf(
                1 to species(1, 203),
                277 to species(277, 1),
            ),
        )

        assertEquals(mapOf(1 to 1, 277 to 252), SaveKnowledgeMapper.pokedexFlagNumbersBySpeciesId(catalog))
        assertEquals(setOf(1, 277), SaveKnowledgeMapper.speciesIdsForPokedexFlags(catalog, setOf(1, 252)))
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
