package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen1CompiledTablesLiveRomTest {
    @Test
    fun intenseIndigoResolvesShiftedRelationshipsAndFullByteLevels() = assertControl(controls[0])

    @Test
    fun beyondResolvesExpandedRelationshipsAndTypes() = assertControl(controls[1])

    @Test
    fun shinResolvesItsRefactoredTypeConsumer() = assertControl(controls[2])

    @Test
    fun unovaQolFallsBackToItsComplete151RowRelationshipDomain() = assertControl(controls[3])

    private fun assertControl(control: Control) {
        val configured = System.getenv(control.environmentVariable)
        assumeTrue("set ${control.environmentVariable} to run this live-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(control.sha256, rom.sha256)

        val parsed = CatalogParser.parse(rom)
        assertEquals(SelectionStatus.SELECTED, parsed.analysis.status)
        val catalog = requireNotNull(parsed.catalog)
        val evolutions = catalog.capabilities.getValue(RomCapability.EVOLUTIONS)
        val typeChart = catalog.capabilities.getValue(RomCapability.TYPE_CHART)

        assertEquals(CapabilityStatus.AVAILABLE, evolutions.status)
        assertEquals(control.relationshipOffset, evolutions.offset)
        assertEquals(control.relationshipSpecies, evolutions.coveredRecords)
        assertEquals(control.relationshipSpecies, evolutions.expectedRecords)
        assertEquals(control.evolutionEdges, catalog.speciesById.values.sumOf { it.evolutionEdges.value.orEmpty().size })
        assertEquals(CapabilityStatus.AVAILABLE, typeChart.status)
        assertEquals(control.typeChartOffset, typeChart.offset)
        assertEquals(control.typeMatchups, catalog.typeChart.size)

        control.learnsetEntries?.let { expectedEntries ->
            val learnsets = catalog.capabilities.getValue(RomCapability.LEARNSETS)
            assertEquals(CapabilityStatus.AVAILABLE, learnsets.status)
            assertEquals(control.relationshipOffset, learnsets.offset)
            assertEquals(control.relationshipSpecies, learnsets.coveredRecords)
            assertEquals(control.relationshipSpecies, learnsets.expectedRecords)
            assertEquals(expectedEntries, catalog.speciesById.values.sumOf { it.learnset.value.orEmpty().size })
            if (control.expectsLevelAbove100) {
                assertTrue(catalog.speciesById.values.flatMap { it.learnset.value.orEmpty() }.any { it.level > 100 })
            }
        }
    }

    private data class Control(
        val environmentVariable: String,
        val sha256: String,
        val relationshipOffset: Int,
        val relationshipSpecies: Int,
        val evolutionEdges: Int,
        val learnsetEntries: Int?,
        val typeChartOffset: Int,
        val typeMatchups: Int,
        val expectsLevelAbove100: Boolean = false,
    )

    private companion object {
        val controls = listOf(
            Control(
                environmentVariable = "DUALDEX_INTENSE_INDIGO_ROM",
                sha256 = "c64428f740559383711a2db229acf29d66ce2f5844e8574aa909f17e77378465",
                relationshipOffset = 0x3B062,
                relationshipSpecies = 190,
                evolutionEdges = 72,
                learnsetEntries = 728,
                typeChartOffset = 0x3E474,
                typeMatchups = 82,
                expectsLevelAbove100 = true,
            ),
            Control(
                environmentVariable = "DUALDEX_BEYOND_RED_ROM",
                sha256 = "3640ed0493287136cd9321cb3428f44113e87354cf90402665ba60e41c8fc61a",
                relationshipOffset = 0xB2EBB,
                relationshipSpecies = 254,
                evolutionEdges = 144,
                learnsetEntries = null,
                typeChartOffset = 0x3E6AC,
                typeMatchups = 112,
            ),
            Control(
                environmentVariable = "DUALDEX_SHIN_RED_ROM",
                sha256 = "024a1c4dab1b12d0b963c6cf756d2c1082de0ccd53fe31384787dcf34edef718",
                relationshipOffset = 0x3AEC9,
                relationshipSpecies = 190,
                evolutionEdges = 76,
                learnsetEntries = 831,
                typeChartOffset = 0x3E666,
                typeMatchups = 82,
            ),
            Control(
                environmentVariable = "DUALDEX_UNOVA_RED_QOL_ROM",
                sha256 = "e3ae2e8726cdcaf8bc149ddc2c97d425c99604504b77ff93a4caa544756b4294",
                relationshipOffset = 0x3B0BC,
                relationshipSpecies = 151,
                evolutionEdges = 74,
                learnsetEntries = 1226,
                typeChartOffset = 0x3E4CE,
                typeMatchups = 110,
            ),
        )
    }
}
