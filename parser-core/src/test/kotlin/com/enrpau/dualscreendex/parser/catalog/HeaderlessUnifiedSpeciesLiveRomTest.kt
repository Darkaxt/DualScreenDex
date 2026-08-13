package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import com.enrpau.dualscreendex.parser.parse.ParserOrchestrator
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class HeaderlessUnifiedSpeciesLiveRomTest {
    @Test
    fun preservesSparseCompiledSpeciesIdsWithoutPublishedExpansionHeader() {
        val configured = System.getenv("DUALDEX_DREAMSTONE_ROM")
        assumeTrue("set DUALDEX_DREAMSTONE_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals("ac31df9cc158823861294b17bd4e66857deab2a53dd81620ddcf6fc03a6a4220", rom.sha256)

        val analysis = ParserOrchestrator.analyze(rom)
        assertEquals(SelectionStatus.SELECTED, analysis.status)
        assertEquals(EngineFamily.EMERALD, analysis.selectedFamily)
        val layout = analysis.probes.single { it.family == analysis.selectedFamily }.resolvedLayout
        assertNotNull(layout)
        requireNotNull(layout)
        assertEquals(0x5F5, layout.speciesCount)
        assertEquals(0x7B0160, layout.tables.baseStats?.offset)
        assertEquals(260, layout.tables.baseStats?.stride)
        assertEquals(0x7B0160 + 44, layout.tables.speciesNames?.offset)
        assertEquals(260, layout.tables.speciesNames?.stride)
        assertEquals(21, layout.resolvedDatasets.typeChart?.table?.typeCount)

        val catalog = CatalogParser.parse(rom).catalog
        assertNotNull(catalog)
        requireNotNull(catalog)
        assertEquals(1_522, catalog.speciesById.size)
        assertTrue(catalog.speciesById.containsKey(1))
        assertTrue(catalog.speciesById.containsKey(0x5F3))
        assertFalse(catalog.speciesById.containsKey(0x59B))
        assertFalse(catalog.speciesById.containsKey(0x5F4))
        assertEquals("Bulbasaur", catalog.speciesById.getValue(1).name.value)
        assertEquals(45, catalog.speciesById.getValue(1).baseStats.value?.hp)
        val closureErrors = buildList {
            catalog.encounterAreas.flatMap { it.slots }.forEach { slot ->
                if (slot.speciesId !in catalog.speciesById) {
                    add("encounter references missing species ${slot.speciesId}")
                }
            }
            catalog.movesById.values.filter { it.id > 0 }.forEach { move ->
                move.typeId.value?.let { typeId ->
                    if (typeId !in catalog.typesById) add("move ${move.id} references missing type $typeId")
                }
            }
        }.distinct().sorted()
        assertEquals(emptyList<String>(), closureErrors)
        assertEquals(CapabilityStatus.NOT_FOUND, catalog.typesById.getValue(0).name.status)
        assertEquals(CapabilityStatus.NOT_FOUND, catalog.typesById.getValue(0).presentation.status)
        assertEquals(CapabilityStatus.NOT_FOUND, catalog.capabilities.getValue(RomCapability.MOVE_CATALOG).status)
    }
}
