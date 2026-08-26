package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.dataset.evolutions.EvolutionRowOutcome
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class PublishedUnifiedSpeciesLiveRomTest {
    @Test
    fun resolvesPublishedSparseUnifiedSpeciesAndRelationships() {
        val configured = System.getenv("DUALDEX_ROGUE_EX_ROM")
        assumeTrue("set DUALDEX_ROGUE_EX_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals("111b0008bcec519c59a02de57895a924fee5d3633c8b8fbb394a497153778ca3", rom.sha256)

        val parsed = CatalogParser.parse(rom)
        assertEquals(SelectionStatus.SELECTED, parsed.analysis.status)
        assertEquals(EngineFamily.EMERALD, parsed.analysis.selectedFamily)
        val layout = requireNotNull(parsed.layout)
        val unified = requireNotNull(layout.headerlessUnifiedSpecies)
        assertEquals(1_574, layout.speciesCount)
        assertEquals(0x106F25C, unified.speciesTableOffset)
        assertEquals(152, unified.speciesRecordSize)
        assertEquals(44, unified.speciesNameOffset)
        assertEquals(58, unified.nationalDexOffset)
        assertEquals(31, unified.categoryOffset)
        assertEquals(60, unified.heightOffset)
        assertEquals(62, unified.weightOffset)
        assertEquals(72, unified.descriptionPointerOffset)
        assertEquals(84, unified.frontSpritePointerOffset)
        assertEquals(100, unified.normalPalettePointerOffset)

        val evolutions = requireNotNull(layout.resolvedDatasets.evolutions)
        assertEquals(0x106F25C + 140L, evolutions.table.offset)
        assertEquals(1_574L, evolutions.table.count)
        assertEquals(1, evolutions.table.slotsPerSpecies)
        assertEquals(8, evolutions.table.recordSize)
        val bulbasaurEvolutions = evolutions.rows[1] as EvolutionRowOutcome.Decoded
        assertEquals(listOf(2), bulbasaurEvolutions.edges.map { it.targetSpeciesId })
        assertEquals(listOf(4), bulbasaurEvolutions.edges.map { it.methodId })

        val catalog = requireNotNull(parsed.catalog)
        assertEquals(1_494, catalog.speciesById.size)
        assertTrue(catalog.speciesById.containsKey(1_434))
        assertFalse(catalog.speciesById.containsKey(1_435))
        assertTrue(catalog.speciesById.containsKey(1_489))
        assertTrue(catalog.speciesById.containsKey(1_573))
        assertEquals(1_494, catalog.speciesById.values.count { it.sprite.status == CapabilityStatus.AVAILABLE })
        assertEquals(193, catalog.speciesById.values.count { it.description.value != null })
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.SPECIES_CATALOG).status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.SPRITES).status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.POKEDEX_DESCRIPTIONS).status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.EVOLUTIONS).status)
        assertTrue(
            catalog.speciesById.values.flatMap { it.evolutionEdges.value.orEmpty() }
                .all { it.targetSpeciesId in catalog.speciesById },
        )
    }
}
