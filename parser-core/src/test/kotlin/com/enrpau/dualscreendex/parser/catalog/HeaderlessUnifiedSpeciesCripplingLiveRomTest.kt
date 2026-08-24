package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
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

class HeaderlessUnifiedSpeciesCripplingLiveRomTest {
    @Test
    fun recognizesRootRelativeConsumersAndPreservesTheActiveTerminalMiniDomain() {
        val configured = System.getenv("DUALDEX_CRIPPLING_ROM")
        assumeTrue("set DUALDEX_CRIPPLING_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals("79882b5e276f6c0386fe7c4d5cce122c56ff969d694ffc530b1a534ab57d25cb", rom.sha256)

        val analysis = ParserOrchestrator.analyze(rom)
        assertEquals(75, ParserOrchestrator.minimumScore)
        assertEquals(10, ParserOrchestrator.minimumMargin)
        assertEquals(SelectionStatus.SELECTED, analysis.status)
        assertEquals(EngineFamily.EMERALD, analysis.selectedFamily)
        val selectedProbe = analysis.probes.single { it.family == analysis.selectedFamily }
        assertEquals(85, selectedProbe.score)
        assertEquals(100, selectedProbe.scoreEvidence.sumOf { it.maximum })
        assertEquals(
            15,
            selectedProbe.scoreEvidence.single { it.category == "cross-table integrity" }.points,
        )
        assertTrue(
            analysis.probes.filter { it.family != EngineFamily.EMERALD && it.hardGatePassed }.all { probe ->
                probe.scoreEvidence
                    .single { it.category == "cross-table integrity" }
                    .reason
                    .contains("compiledUnified=false")
            },
        )
        val layout = selectedProbe.resolvedLayout
        assertNotNull(layout)
        requireNotNull(layout)
        assertEquals(0x5F8, layout.speciesCount)
        assertEquals(58, layout.headerlessUnifiedSpecies?.nationalDexOffset)
        assertEquals(31, layout.headerlessUnifiedSpecies?.categoryOffset)
        assertEquals(62, layout.headerlessUnifiedSpecies?.heightOffset)
        assertEquals(64, layout.headerlessUnifiedSpecies?.weightOffset)
        assertEquals(76, layout.headerlessUnifiedSpecies?.descriptionPointerOffset)
        assertEquals(88, layout.headerlessUnifiedSpecies?.frontSpritePointerOffset)
        assertEquals(96, layout.headerlessUnifiedSpecies?.normalPalettePointerOffset)
        assertEquals(0xD95918, layout.tables.descriptions?.offset)
        assertEquals(260, layout.tables.descriptions?.stride)
        assertEquals(0xD95918 + 88, layout.tables.sprites?.offset)
        assertEquals(260, layout.tables.sprites?.stride)
        val unifiedAbilities = requireNotNull(layout.headerlessUnifiedSpecies?.abilities)
        assertEquals(24, unifiedAbilities.speciesAbilityOffset)
        assertEquals(3, unifiedAbilities.speciesAbilitySlotCount)
        assertEquals(2, unifiedAbilities.speciesAbilityElementSize)
        assertEquals(28, unifiedAbilities.abilityRecordSize)
        assertEquals(17, unifiedAbilities.abilityNameWidth)
        assertEquals(20, unifiedAbilities.abilityDescriptionPointerOffset)
        assertEquals(24, unifiedAbilities.abilityRatingOffset)
        assertEquals(25, unifiedAbilities.abilityFlagsOffset)
        assertEquals(0x7437A4, layout.tables.abilities?.offset)
        assertEquals(311, layout.tables.abilities?.count)
        assertEquals(28, layout.tables.abilities?.stride)
        val unifiedMoves = requireNotNull(layout.headerlessUnifiedSpecies.moveAcquisitions)
        assertEquals(152, unifiedMoves.teachablePointerOffset)
        assertEquals(156, unifiedMoves.eggMovePointerOffset)

        val catalog = CatalogParser.parse(rom).catalog
        assertNotNull(catalog)
        requireNotNull(catalog)
        assertEquals(1_525, catalog.speciesById.size)
        assertTrue(catalog.speciesById.containsKey(1))
        assertTrue(catalog.speciesById.containsKey(0x5F4))
        assertTrue(catalog.speciesById.containsKey(0x5F5))
        assertTrue(catalog.speciesById.containsKey(0x5F6))
        assertFalse(catalog.speciesById.containsKey(0x59B))
        assertFalse(catalog.speciesById.containsKey(0x5F7))
        assertEquals("Bulbasaur", catalog.speciesById.getValue(1).name.value)
        assertEquals(1, catalog.speciesById.getValue(1).dexNumber.value)
        assertEquals(0x45A, catalog.speciesById.getValue(0x5F4).dexNumber.value)
        assertEquals(0x45B, catalog.speciesById.getValue(0x5F5).dexNumber.value)
        assertEquals(0x45C, catalog.speciesById.getValue(0x5F6).dexNumber.value)
        assertEquals(45, catalog.speciesById.getValue(1).baseStats.value?.hp)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.SPRITES).status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.POKEDEX_DESCRIPTIONS).status)
        val bulbasaur = catalog.speciesById.getValue(1)
        assertTrue(bulbasaur.description.value?.contains("Bulbasaur can be seen napping") == true)
        assertEquals(7, bulbasaur.height.value)
        assertEquals(69, bulbasaur.weight.value)
        assertEquals(64, bulbasaur.sprite.value?.width)
        assertEquals(64, bulbasaur.sprite.value?.height)
        assertTrue(bulbasaur.sprite.value?.argb?.any { it != 0 } == true)
        assertEquals(listOf(65, 34), bulbasaur.abilityIds.value)
        assertEquals(310, catalog.abilitiesById.size)
        assertEquals("Overgrow", catalog.abilitiesById.getValue(65).name.value)
        assertEquals("Ups Grass moves in a pinch.", catalog.abilitiesById.getValue(65).description.value)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.ABILITIES).status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.ABILITY_DESCRIPTIONS).status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.ABILITY_MECHANICS).status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.MACHINE_MOVES).status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.EGG_MOVES).status)
        assertEquals(CapabilityStatus.NOT_FOUND, catalog.capabilities.getValue(RomCapability.TUTOR_MOVES).status)
        val machineMoves = catalog.speciesById.values.flatMap { species ->
            species.moveAcquisitions.value.orEmpty().filter { it.method == MoveAcquisitionMethod.MACHINE }
        }
        assertEquals(88_104, machineMoves.size)
        assertEquals(
            1_489,
            catalog.speciesById.values.count { species ->
                species.moveAcquisitions.value.orEmpty().any { it.method == MoveAcquisitionMethod.MACHINE }
            },
        )
        assertEquals(362, machineMoves.mapTo(linkedSetOf()) { it.moveId }.size)
        assertEquals(846, machineMoves.maxOf { it.moveId })
        val eggMoves = catalog.speciesById.values.flatMap { species ->
            species.moveAcquisitions.value.orEmpty().filter { it.method == MoveAcquisitionMethod.EGG }
        }
        assertEquals(4_671, eggMoves.size)
        assertEquals(
            503,
            catalog.speciesById.values.count { species ->
                species.moveAcquisitions.value.orEmpty().any { it.method == MoveAcquisitionMethod.EGG }
            },
        )
        assertEquals(458, eggMoves.mapTo(linkedSetOf()) { it.moveId }.size)
        assertEquals(821, eggMoves.maxOf { it.moveId })
    }
}
