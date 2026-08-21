package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.parse.Gen1CompiledDescriptionResolver
import com.enrpau.dualscreendex.parser.parse.Gen1CompiledMachineResolver
import com.enrpau.dualscreendex.parser.parse.Gen1CompiledMoveResolver
import com.enrpau.dualscreendex.parser.parse.Gen1CompiledNameResolver
import com.enrpau.dualscreendex.parser.parse.Gen1CompiledRelationshipResolver
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

    @Test
    fun novaSelectsFromUniqueCompiledGen1LineageEvidence() = assertStructuralIdentity(
        environment = "DUALDEX_NOVA_ROM",
        sha256 = "9ff825918dfb23d04dd35bfd92c6790a8b7c8596b93223ea1858758b24e7dad6",
        moveCount = 239,
        relationshipCount = 193,
        evolutionEdges = 110,
        learnsetEntries = 2134,
        machineOffset = 0x13C90,
        machineCount = 60,
        descriptionOffset = 0x4047A,
        descriptionRecords = 190,
    )

    @Test
    fun grapeSelectsFromUniqueCompiledGen1LineageEvidence() = assertStructuralIdentity(
        environment = "DUALDEX_GRAPE_ROM",
        sha256 = "082154ea8e4cc24efc0a7b460eb2e1314a0deb411749649201626e82b3c7d609",
        moveCount = null,
    )

    @Test
    fun redPlusPlusResolvesItsExpandedMoveDomainWithoutInflatingRelationships() {
        val configured = System.getenv("DUALDEX_RED_PLUS_PLUS_ROM")
        assumeTrue("set DUALDEX_RED_PLUS_PLUS_ROM to run this live-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals("f244f8c31ff3dfa907b6730fce410ba96f74bc1f920bb318c7065288fa13fc3b", rom.sha256)
        assertEquals(55, Gen1CompiledMachineResolver.resolve(rom, moveCount = 253)?.count)
        val descriptions = Gen1CompiledDescriptionResolver.resolve(
            rom = rom,
            preferredCount = 208,
            fallbackCounts = listOf(151),
        )
        assertEquals(0x40488, descriptions?.offset)
        assertEquals(208, descriptions?.count)

        val parsed = CatalogParser.parse(rom)
        assertEquals(SelectionStatus.SELECTED, parsed.analysis.status)
        val catalog = requireNotNull(parsed.catalog)
        val descriptionsCapability = catalog.capabilities.getValue(RomCapability.POKEDEX_DESCRIPTIONS)
        val moves = catalog.capabilities.getValue(RomCapability.MOVE_CATALOG)
        val machines = catalog.capabilities.getValue(RomCapability.MACHINE_MOVES)
        val evolutions = catalog.capabilities.getValue(RomCapability.EVOLUTIONS)
        val learnsets = catalog.capabilities.getValue(RomCapability.LEARNSETS)

        assertEquals(CapabilityStatus.AVAILABLE, descriptionsCapability.status)
        assertEquals(0x40488, descriptionsCapability.offset)
        assertEquals(151, descriptionsCapability.coveredRecords)
        assertEquals(CapabilityStatus.AVAILABLE, moves.status)
        assertEquals(253, moves.coveredRecords)
        assertEquals(253, moves.expectedRecords)
        assertEquals(253, catalog.movesById.size)
        assertEquals(CapabilityStatus.AVAILABLE, machines.status)
        assertEquals(0x1273D, machines.offset)
        assertTrue(machines.compatible)
        assertEquals(CapabilityStatus.PARTIAL, evolutions.status)
        assertEquals(196, evolutions.coveredRecords)
        assertEquals(208, evolutions.expectedRecords)
        assertEquals(CapabilityStatus.PARTIAL, learnsets.status)
        assertEquals(196, learnsets.coveredRecords)
        assertEquals(208, learnsets.expectedRecords)
    }

    private fun assertStructuralIdentity(
        environment: String,
        sha256: String,
        moveCount: Int?,
        relationshipCount: Int? = null,
        evolutionEdges: Int? = null,
        learnsetEntries: Int? = null,
        machineOffset: Int? = null,
        machineCount: Int? = null,
        descriptionOffset: Int? = null,
        descriptionRecords: Int? = null,
    ) {
        val configured = System.getenv(environment)
        assumeTrue("set $environment to run this live-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(sha256, rom.sha256)
        moveCount?.let { expectedMoves ->
            assertEquals(expectedMoves, Gen1CompiledMoveResolver.resolve(rom)?.moveNames?.count)
        }
        relationshipCount?.let { expectedRelationships ->
            assertEquals(expectedRelationships, Gen1CompiledNameResolver.resolve(rom, 190)?.count)
            val relationships = Gen1CompiledRelationshipResolver.resolve(
                rom = rom,
                preferredCount = expectedRelationships,
                fallbackCounts = listOf(151),
            )
            assertEquals(expectedRelationships, relationships?.count)
            assertEquals(TableRecordFormat.STANDARD, relationships?.format)
        }
        if (machineOffset != null && machineCount != null && moveCount != null) {
            val machines = Gen1CompiledMachineResolver.resolve(rom, moveCount)
            assertEquals(machineOffset, machines?.offset)
            assertEquals(machineCount, machines?.count)
        }
        if (descriptionOffset != null && relationshipCount != null) {
            val descriptions = Gen1CompiledDescriptionResolver.resolve(
                rom = rom,
                preferredCount = relationshipCount,
                fallbackCounts = listOf(151),
            )
            assertEquals(descriptionOffset, descriptions?.offset)
            assertEquals(relationshipCount, descriptions?.count)
        }

        val parsed = CatalogParser.parse(rom)
        assertEquals(SelectionStatus.SELECTED, parsed.analysis.status)
        assertEquals(EngineFamily.RED_BLUE, parsed.analysis.selectedFamily)
        val catalog = requireNotNull(parsed.catalog)
        moveCount?.let { expectedMoves ->
            assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.MOVE_CATALOG).status)
            assertEquals(expectedMoves, catalog.movesById.size)
        }
        relationshipCount?.let { expectedRelationships ->
            val speciesNames = catalog.capabilities.getValue(RomCapability.SPECIES_NAMES)
            val evolutions = catalog.capabilities.getValue(RomCapability.EVOLUTIONS)
            val learnsets = catalog.capabilities.getValue(RomCapability.LEARNSETS)
            assertEquals(CapabilityStatus.AVAILABLE, speciesNames.status)
            assertEquals(expectedRelationships, speciesNames.coveredRecords)
            assertEquals(CapabilityStatus.AVAILABLE, evolutions.status)
            assertEquals(expectedRelationships, evolutions.coveredRecords)
            assertEquals(expectedRelationships, evolutions.expectedRecords)
            assertEquals(CapabilityStatus.AVAILABLE, learnsets.status)
            assertEquals(expectedRelationships, learnsets.coveredRecords)
            assertEquals(expectedRelationships, learnsets.expectedRecords)
            assertEquals(expectedRelationships, catalog.speciesById.size)
        }
        evolutionEdges?.let { expectedEdges ->
            assertEquals(expectedEdges, catalog.speciesById.values.sumOf { it.evolutionEdges.value.orEmpty().size })
        }
        learnsetEntries?.let { expectedEntries ->
            assertEquals(expectedEntries, catalog.speciesById.values.sumOf { it.learnset.value.orEmpty().size })
        }
        if (machineOffset != null && machineCount != null) {
            val machines = catalog.capabilities.getValue(RomCapability.MACHINE_MOVES)
            assertEquals(CapabilityStatus.AVAILABLE, machines.status)
            assertEquals(machineOffset, machines.offset)
            assertTrue(
                catalog.speciesById.values.flatMap { it.moveAcquisitions.value.orEmpty() }.any {
                    it.method == MoveAcquisitionMethod.MACHINE && it.sourceId == machineCount
                },
            )
        }
        if (descriptionOffset != null && descriptionRecords != null) {
            val descriptions = catalog.capabilities.getValue(RomCapability.POKEDEX_DESCRIPTIONS)
            assertEquals(CapabilityStatus.AVAILABLE, descriptions.status)
            assertEquals(descriptionOffset, descriptions.offset)
            assertEquals(151, descriptions.coveredRecords)
            assertEquals(151, descriptions.expectedRecords)
            assertEquals(descriptionRecords, catalog.speciesById.values.count { it.description.value != null })
        }
    }

    private fun assertControl(control: Control) {
        val configured = System.getenv(control.environmentVariable)
        assumeTrue("set ${control.environmentVariable} to run this live-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(control.sha256, rom.sha256)
        assertEquals(
            control.machineCount,
            Gen1CompiledMachineResolver.resolve(rom, moveCount = control.moveCount)?.count,
        )
        val descriptions = Gen1CompiledDescriptionResolver.resolve(
            rom = rom,
            preferredCount = control.relationshipSpecies,
            fallbackCounts = listOf(151),
        )
        assertEquals(control.descriptionOffset, descriptions?.offset)
        assertEquals(control.descriptionCount, descriptions?.count)

        val parsed = CatalogParser.parse(rom)
        assertEquals(SelectionStatus.SELECTED, parsed.analysis.status)
        val catalog = requireNotNull(parsed.catalog)
        val evolutions = catalog.capabilities.getValue(RomCapability.EVOLUTIONS)
        val descriptionsCapability = catalog.capabilities.getValue(RomCapability.POKEDEX_DESCRIPTIONS)
        val machines = catalog.capabilities.getValue(RomCapability.MACHINE_MOVES)
        val moveCatalog = catalog.capabilities.getValue(RomCapability.MOVE_CATALOG)
        val sprites = catalog.capabilities.getValue(RomCapability.SPRITES)
        val typeChart = catalog.capabilities.getValue(RomCapability.TYPE_CHART)

        assertEquals(CapabilityStatus.AVAILABLE, descriptionsCapability.status)
        assertEquals(control.descriptionOffset, descriptionsCapability.offset)
        assertEquals(151, descriptionsCapability.coveredRecords)
        assertEquals(CapabilityStatus.AVAILABLE, evolutions.status)
        assertEquals(control.relationshipOffset, evolutions.offset)
        assertEquals(control.relationshipSpecies, evolutions.coveredRecords)
        assertEquals(control.relationshipSpecies, evolutions.expectedRecords)
        assertEquals(control.evolutionEdges, catalog.speciesById.values.sumOf { it.evolutionEdges.value.orEmpty().size })
        assertEquals(CapabilityStatus.AVAILABLE, typeChart.status)
        assertEquals(control.typeChartOffset, typeChart.offset)
        assertEquals(control.typeMatchups, catalog.typeChart.size)
        assertEquals(CapabilityStatus.AVAILABLE, machines.status)
        assertEquals(control.machineOffset, machines.offset)
        assertTrue(machines.compatible)
        if (control.machineCount > 55) {
            assertTrue(
                catalog.speciesById.values.flatMap { it.moveAcquisitions.value.orEmpty() }.any {
                    it.method == MoveAcquisitionMethod.MACHINE && it.sourceId == control.machineCount
                },
            )
        }
        control.spriteCount?.let { expectedSprites ->
            assertEquals(CapabilityStatus.AVAILABLE, sprites.status)
            assertEquals(expectedSprites, sprites.coveredRecords)
        }

        val expectedMoves = control.moveCount
        assertEquals(CapabilityStatus.AVAILABLE, moveCatalog.status)
        assertEquals(expectedMoves, moveCatalog.coveredRecords)
        assertEquals(expectedMoves, moveCatalog.expectedRecords)
        assertEquals(expectedMoves, catalog.movesById.size)

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
        val moveCount: Int,
        val learnsetEntries: Int?,
        val typeChartOffset: Int,
        val typeMatchups: Int,
        val machineOffset: Int,
        val machineCount: Int,
        val descriptionOffset: Int,
        val descriptionCount: Int,
        val spriteCount: Int? = null,
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
                moveCount = 165,
                learnsetEntries = 728,
                typeChartOffset = 0x3E474,
                typeMatchups = 82,
                machineOffset = 0x13773,
                machineCount = 55,
                descriptionOffset = 0x4047E,
                descriptionCount = 190,
                spriteCount = 151,
                expectsLevelAbove100 = true,
            ),
            Control(
                environmentVariable = "DUALDEX_BEYOND_RED_ROM",
                sha256 = "3640ed0493287136cd9321cb3428f44113e87354cf90402665ba60e41c8fc61a",
                relationshipOffset = 0xB2EBB,
                relationshipSpecies = 254,
                evolutionEdges = 144,
                moveCount = 200,
                learnsetEntries = 1420,
                typeChartOffset = 0x3E6AC,
                typeMatchups = 112,
                machineOffset = 0x13A85,
                machineCount = 60,
                descriptionOffset = 0x4049B,
                descriptionCount = 254,
                spriteCount = 151,
            ),
            Control(
                environmentVariable = "DUALDEX_SHIN_RED_ROM",
                sha256 = "024a1c4dab1b12d0b963c6cf756d2c1082de0ccd53fe31384787dcf34edef718",
                relationshipOffset = 0x3AEC9,
                relationshipSpecies = 190,
                evolutionEdges = 76,
                moveCount = 165,
                learnsetEntries = 831,
                typeChartOffset = 0x3E666,
                typeMatchups = 82,
                machineOffset = 0x1395B,
                machineCount = 55,
                descriptionOffset = 0x4052F,
                descriptionCount = 190,
                spriteCount = 151,
            ),
            Control(
                environmentVariable = "DUALDEX_UNOVA_RED_QOL_ROM",
                sha256 = "e3ae2e8726cdcaf8bc149ddc2c97d425c99604504b77ff93a4caa544756b4294",
                relationshipOffset = 0x3B0BC,
                relationshipSpecies = 151,
                evolutionEdges = 74,
                moveCount = 165,
                learnsetEntries = 1226,
                typeChartOffset = 0x3E4CE,
                typeMatchups = 110,
                machineOffset = 0x13783,
                machineCount = 55,
                descriptionOffset = 0x4047E,
                descriptionCount = 151,
                spriteCount = 151,
            ),
        )
    }
}
