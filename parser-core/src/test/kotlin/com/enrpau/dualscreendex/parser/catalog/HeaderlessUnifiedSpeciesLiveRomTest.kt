package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetRowOutcome
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import com.enrpau.dualscreendex.parser.parse.ParserOrchestrator
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
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
        assertEquals(31, layout.headerlessUnifiedSpecies?.categoryOffset)
        assertEquals(62, layout.headerlessUnifiedSpecies?.heightOffset)
        assertEquals(64, layout.headerlessUnifiedSpecies?.weightOffset)
        assertEquals(76, layout.headerlessUnifiedSpecies?.descriptionPointerOffset)
        assertEquals(88, layout.headerlessUnifiedSpecies?.frontSpritePointerOffset)
        assertEquals(96, layout.headerlessUnifiedSpecies?.normalPalettePointerOffset)
        assertEquals(0x7B0160, layout.tables.descriptions?.offset)
        assertEquals(260, layout.tables.descriptions?.stride)
        assertEquals(0x7B0160 + 88, layout.tables.sprites?.offset)
        assertEquals(260, layout.tables.sprites?.stride)
        assertEquals(listOf(8), layout.tables.sprites?.pointerOffsets)
        val unifiedAbilities = requireNotNull(layout.headerlessUnifiedSpecies?.abilities)
        assertEquals(24, unifiedAbilities.speciesAbilityOffset)
        assertEquals(3, unifiedAbilities.speciesAbilitySlotCount)
        assertEquals(2, unifiedAbilities.speciesAbilityElementSize)
        assertEquals(28, unifiedAbilities.abilityRecordSize)
        assertEquals(17, unifiedAbilities.abilityNameWidth)
        assertEquals(20, unifiedAbilities.abilityDescriptionPointerOffset)
        assertEquals(24, unifiedAbilities.abilityRatingOffset)
        assertEquals(25, unifiedAbilities.abilityFlagsOffset)
        assertEquals(0xE65E98, layout.tables.abilities?.offset)
        assertEquals(311, layout.tables.abilities?.count)
        assertEquals(28, layout.tables.abilities?.stride)
        val unifiedMoves = requireNotNull(layout.headerlessUnifiedSpecies.moveAcquisitions)
        assertEquals(152, unifiedMoves.teachablePointerOffset)
        assertEquals(156, unifiedMoves.eggMovePointerOffset)
        val expandedBalls = requireNotNull(layout.expandedSplitCaptureBalls)
        assertEquals(0x7A40F4, expandedBalls.sheetTableOffset)
        assertEquals(0x7A4014, expandedBalls.paletteTableOffset)
        assertEquals(28, expandedBalls.ballCount)
        assertEquals(828, expandedBalls.itemIdsByBallIndex.first())
        assertEquals((1..27).toList(), expandedBalls.itemIdsByBallIndex.drop(1))
        assertEquals(21, layout.resolvedDatasets.typeChart?.table?.typeCount)
        assertEquals(848, layout.moveCount)
        assertEquals(0x759858, layout.tables.moveNames?.offset)
        assertEquals(848, layout.tables.moveNames?.count)
        assertEquals(48, layout.tables.moveNames?.stride)
        assertTrue(layout.tables.moveNames?.valuesArePointers == true)
        assertEquals(0x759858, layout.tables.moveData?.offset)
        assertEquals(848, layout.tables.moveData?.count)
        assertEquals(48, layout.tables.moveData?.recordSize)
        assertEquals(48, layout.tables.moveData?.stride)
        assertEquals(848, layout.resolvedDatasets.moveDetails?.rows?.size)
        assertNotNull("moveCount=${layout.moveCount}", layout.tables.learnsets)
        val resolvedLearnsets = requireNotNull(layout.resolvedDatasets.learnsets)
        val primaryLearnsets = requireNotNull(resolvedLearnsets.primary)
        assertEquals(0x7B0160 + 148, layout.tables.learnsets?.offset)
        assertEquals(260, layout.tables.learnsets?.stride)
        assertEquals(260, primaryLearnsets.layout.table.pointerStride)
        assertEquals(0x5F5, primaryLearnsets.layout.rows.size)
        assertTrue(primaryLearnsets.layout.rows.none { it is LearnsetRowOutcome.Malformed })
        val decodedLearnsets = primaryLearnsets.layout.rows.filterIsInstance<LearnsetRowOutcome.Decoded>()
        assertEquals(1_522, decodedLearnsets.size)
        assertEquals(22_164, decodedLearnsets.sumOf { it.entries.size })
        assertEquals(847, decodedLearnsets.flatMap { it.entries }.maxOf { it.moveId })
        assertEquals(
            "5e80424ae344770807f1729338e69d61885ecb3af7867b8265252b2f79b093de",
            learnsetSha256(decodedLearnsets),
        )

        val catalog = CatalogParser.parse(rom).catalog
        assertNotNull(catalog)
        requireNotNull(catalog)
        val text = catalog.defaultTextProjection()
        assertEquals(1_522, catalog.speciesById.size)
        assertTrue(catalog.speciesById.containsKey(1))
        assertTrue(catalog.speciesById.containsKey(0x5F3))
        assertFalse(catalog.speciesById.containsKey(0x59B))
        assertFalse(catalog.speciesById.containsKey(0x5F4))
        assertEquals("Bulbasaur", text.speciesName(1))
        assertEquals(45, catalog.speciesById.getValue(1).baseStats.value?.hp)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.SPRITES).status)
        assertEquals(
            CapabilityStatus.AVAILABLE,
            text.localizedCapabilities.getValue(LocalizedTextCapability.SPECIES_DESCRIPTIONS).status,
        )
        val bulbasaur = catalog.speciesById.getValue(1)
        assertTrue(text.speciesDescription(1)?.contains("Bulbasaur can be seen napping") == true)
        assertEquals(7, bulbasaur.height.value)
        assertEquals(69, bulbasaur.weight.value)
        assertEquals(64, bulbasaur.sprite.value?.width)
        assertEquals(64, bulbasaur.sprite.value?.height)
        assertTrue(bulbasaur.sprite.value?.argb?.any { it != 0 } == true)
        assertEquals(listOf(65, 34), bulbasaur.abilityIds.value)
        assertEquals(310, catalog.abilitiesById.size)
        assertEquals("Overgrow", text.abilityName(65))
        assertEquals("Ups Grass moves in a pinch.", text.abilityDescription(65))
        assertTrue(
            catalog.abilitiesById.getValue(65).mechanics.value.orEmpty().any {
                it.kind == AbilityMechanicKind.AI_RATING && it.value == "5"
            },
        )
        assertTrue(
            catalog.abilitiesById.getValue(5).mechanics.value.orEmpty().any {
                it.kind == AbilityMechanicKind.FLAG && it.label == "Breakable"
            },
        )
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.ABILITIES).status)
        assertEquals(
            CapabilityStatus.AVAILABLE,
            text.localizedCapabilities.getValue(LocalizedTextCapability.ABILITY_DESCRIPTIONS).status,
        )
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.ABILITY_MECHANICS).status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.BALL_CATALOG).status)
        assertEquals(28, catalog.captureBallsById.size)
        assertEquals(27, catalog.captureBallsById.values.count { it.sprite.status == CapabilityStatus.AVAILABLE })
        assertEquals(CapabilityStatus.NOT_FOUND, catalog.captureBallsById.getValue(828).sprite.status)
        assertTrue(catalog.captureBallsById.getValue(1).generic)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.captureBallsById.getValue(1).sprite.status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.captureBallsById.getValue(27).sprite.status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.LEARNSETS).status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.MACHINE_MOVES).status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.EGG_MOVES).status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.TUTOR_MOVES).status)
        val machineMoves = catalog.speciesById.values.flatMap { species ->
            species.moveAcquisitions.value.orEmpty().filter { it.method == MoveAcquisitionMethod.MACHINE }
        }
        assertEquals(47_108, machineMoves.size)
        assertEquals(
            1_489,
            catalog.speciesById.values.count { species ->
                species.moveAcquisitions.value.orEmpty().any { it.method == MoveAcquisitionMethod.MACHINE }
            },
        )
        assertEquals(134, machineMoves.mapTo(linkedSetOf()) { it.moveId }.size)
        assertEquals(811, machineMoves.maxOf { it.moveId })
        val eggMoves = catalog.speciesById.values.flatMap { species ->
            species.moveAcquisitions.value.orEmpty().filter { it.method == MoveAcquisitionMethod.EGG }
        }
        assertEquals(4_321, eggMoves.size)
        assertEquals(
            487,
            catalog.speciesById.values.count { species ->
                species.moveAcquisitions.value.orEmpty().any { it.method == MoveAcquisitionMethod.EGG }
            },
        )
        assertEquals(433, eggMoves.mapTo(linkedSetOf()) { it.moveId }.size)
        assertEquals(734, eggMoves.maxOf { it.moveId })
        assertTrue(catalog.navigableSpecies().all { it.learnset.status == CapabilityStatus.AVAILABLE })
        assertEquals(
            listOf(LearnsetEntry(1, 33), LearnsetEntry(1, 45)),
            catalog.speciesById.getValue(1).learnset.value?.take(2),
        )
        val referencedMoves = catalog.speciesById.values
            .flatMap { it.learnset.value.orEmpty() }
            .mapTo(linkedSetOf()) { it.moveId }
        assertTrue(referencedMoves.all(catalog.movesById::containsKey))
        assertTrue(catalog.movesById.containsKey(847))
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
        assertFalse(catalog.typesById.containsKey(0))
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.MOVE_CATALOG).status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.MOVE_DETAILS).status)
        val moveDescriptionCapability =
            text.localizedCapabilities.getValue(LocalizedTextCapability.MOVE_DESCRIPTIONS)
        assertEquals(CapabilityStatus.PARTIAL, moveDescriptionCapability.status)
        assertEquals(842, moveDescriptionCapability.coveredRecords)
        assertEquals(847, moveDescriptionCapability.expectedRecords)
        assertEquals(847, catalog.movesById.size)
        assertEquals("Pound", text.moveName(1))
        assertEquals(1, catalog.movesById.getValue(1).typeId.value)
        assertEquals(MoveCategory.PHYSICAL, catalog.movesById.getValue(1).category.value)
        assertEquals(40, catalog.movesById.getValue(1).power.value)
        assertEquals(100, catalog.movesById.getValue(1).accuracy.value)
        assertEquals(35, catalog.movesById.getValue(1).pp.value)
        assertEquals(0, catalog.movesById.getValue(1).priority.value)
        assertEquals(1, catalog.movesById.getValue(1).effectId.value)
        assertEquals("Pounds the foe with forelegs or tail.", text.moveDescription(1))
        assertEquals("Malignant Chain", text.moveName(847))

        val second = CatalogParser.parse(RomImage(Files.readAllBytes(path)))
        val secondRows = requireNotNull(second.layout?.resolvedDatasets?.learnsets?.primary)
            .layout.rows.filterIsInstance<LearnsetRowOutcome.Decoded>()
        assertEquals(learnsetSha256(decodedLearnsets), learnsetSha256(secondRows))
        assertEquals(
            catalog.speciesById.mapValues { it.value.learnset },
            requireNotNull(second.catalog).speciesById.mapValues { it.value.learnset },
        )
        assertEquals(catalog.movesById, requireNotNull(second.catalog).movesById)
    }

    @Test
    fun malformedEmbeddedDescriptionsDisableOnlyThatOptionalModule() {
        val configured = System.getenv("DUALDEX_DREAMSTONE_ROM")
        assumeTrue("set DUALDEX_DREAMSTONE_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val bytes = Files.readAllBytes(path)
        repeat(400) { id ->
            val field = 0x7B0160 + (id + 1) * 260 + 76
            bytes.fill(0, field, field + 4)
        }

        val analysis = ParserOrchestrator.analyze(RomImage(bytes))
        assertEquals(SelectionStatus.SELECTED, analysis.status)
        assertEquals(EngineFamily.EMERALD, analysis.selectedFamily)
        val probe = analysis.probes.single { it.family == analysis.selectedFamily }
        val layout = requireNotNull(probe.resolvedLayout)
        assertNotNull(layout.tables.speciesNames)
        assertNotNull(layout.tables.baseStats)
        assertEquals(null, layout.tables.descriptions)
        assertEquals(null, layout.headerlessUnifiedSpecies?.descriptionPointerOffset)
        assertNotNull(layout.tables.sprites)
        assertEquals(88, layout.headerlessUnifiedSpecies?.frontSpritePointerOffset)
        assertEquals(
            CapabilityStatus.NOT_FOUND,
            probe.capabilities.single { it.capability == RomCapability.POKEDEX_DESCRIPTIONS }.status,
        )
        assertEquals(
            CapabilityStatus.AVAILABLE,
            probe.capabilities.single { it.capability == RomCapability.SPRITES }.status,
        )
    }

    @Test
    fun malformedEmbeddedAbilityDescriptionsDisableOnlyThatOptionalModule() {
        val configured = System.getenv("DUALDEX_DREAMSTONE_ROM")
        assumeTrue("set DUALDEX_DREAMSTONE_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val bytes = Files.readAllBytes(path)
        repeat(80) { index ->
            val field = 0xE65E98 + (index + 1) * 28 + 20
            bytes.fill(0, field, field + 4)
        }

        val result = CatalogParser.parse(RomImage(bytes))
        assertEquals(SelectionStatus.SELECTED, result.analysis.status)
        val layout = requireNotNull(result.layout)
        assertNotNull(layout.tables.abilities)
        assertEquals(null, layout.headerlessUnifiedSpecies?.abilities?.abilityDescriptionPointerOffset)
        assertEquals(24, layout.headerlessUnifiedSpecies?.abilities?.abilityRatingOffset)
        val catalog = requireNotNull(result.catalog)
        val text = catalog.defaultTextProjection()
        assertEquals(listOf(65, 34), catalog.speciesById.getValue(1).abilityIds.value)
        assertEquals("Overgrow", text.abilityName(65))
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.ABILITIES).status)
        assertEquals(
            CapabilityStatus.NOT_FOUND,
            text.localizedCapabilities.getValue(LocalizedTextCapability.ABILITY_DESCRIPTIONS).status,
        )
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.ABILITY_MECHANICS).status)
    }

    @Test
    fun malformedEmbeddedMovePointersDisableOnlyTheirOwnModules() {
        val configured = System.getenv("DUALDEX_DREAMSTONE_ROM")
        assumeTrue("set DUALDEX_DREAMSTONE_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val original = Files.readAllBytes(path)

        val malformedTeachable = original.copyOf().apply {
            fill(0xFF.toByte(), 0x7B0160 + 260 + 152, 0x7B0160 + 260 + 156)
        }
        val teachableResult = CatalogParser.parse(RomImage(malformedTeachable))
        assertEquals(SelectionStatus.SELECTED, teachableResult.analysis.status)
        val teachableLayout = requireNotNull(teachableResult.layout)
        assertEquals(null, teachableLayout.headerlessUnifiedSpecies?.moveAcquisitions?.teachablePointerOffset)
        assertEquals(156, teachableLayout.headerlessUnifiedSpecies?.moveAcquisitions?.eggMovePointerOffset)
        val teachableCatalog = requireNotNull(teachableResult.catalog)
        assertEquals(CapabilityStatus.NOT_FOUND, teachableCatalog.capabilities.getValue(RomCapability.MACHINE_MOVES).status)
        assertEquals(CapabilityStatus.AVAILABLE, teachableCatalog.capabilities.getValue(RomCapability.EGG_MOVES).status)
        assertEquals(CapabilityStatus.AVAILABLE, teachableCatalog.capabilities.getValue(RomCapability.TUTOR_MOVES).status)
        assertEquals(CapabilityStatus.AVAILABLE, teachableCatalog.capabilities.getValue(RomCapability.ABILITIES).status)

        val malformedEgg = original.copyOf().apply {
            fill(0xFF.toByte(), 0x7B0160 + 260 + 156, 0x7B0160 + 260 + 160)
        }
        val eggResult = CatalogParser.parse(RomImage(malformedEgg))
        assertEquals(SelectionStatus.SELECTED, eggResult.analysis.status)
        val eggLayout = requireNotNull(eggResult.layout)
        assertEquals(152, eggLayout.headerlessUnifiedSpecies?.moveAcquisitions?.teachablePointerOffset)
        assertEquals(null, eggLayout.headerlessUnifiedSpecies?.moveAcquisitions?.eggMovePointerOffset)
        val eggCatalog = requireNotNull(eggResult.catalog)
        assertEquals(CapabilityStatus.AVAILABLE, eggCatalog.capabilities.getValue(RomCapability.MACHINE_MOVES).status)
        assertEquals(CapabilityStatus.NOT_FOUND, eggCatalog.capabilities.getValue(RomCapability.EGG_MOVES).status)
        assertEquals(CapabilityStatus.AVAILABLE, eggCatalog.capabilities.getValue(RomCapability.TUTOR_MOVES).status)
        assertEquals(CapabilityStatus.AVAILABLE, eggCatalog.capabilities.getValue(RomCapability.ABILITIES).status)
    }

    private fun learnsetSha256(rows: List<LearnsetRowOutcome.Decoded>): String {
        val bytes = rows.joinToString("\u001e") { row ->
            "${row.rowIndex}:" + row.entries.joinToString(";") { entry -> "${entry.level},${entry.moveId}" }
        }.toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
    }
}
