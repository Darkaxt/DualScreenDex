package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionCodec
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionTableLayout
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionTableOutcome
import com.enrpau.dualscreendex.parser.dataset.descriptions.ResolvedDescriptionLayout
import com.enrpau.dualscreendex.parser.dataset.evolutions.EvolutionCodec
import com.enrpau.dualscreendex.parser.dataset.evolutions.EvolutionTableLayout
import com.enrpau.dualscreendex.parser.dataset.evolutions.EvolutionTableOutcome
import com.enrpau.dualscreendex.parser.dataset.evolutions.ResolvedEvolutionLayout
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetCodec
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetFormat
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetTableLayout
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetTableOutcome
import com.enrpau.dualscreendex.parser.dataset.learnsets.ResolvedLearnsetLayout
import com.enrpau.dualscreendex.parser.dataset.learnsets.ResolvedLearnsetSet
import com.enrpau.dualscreendex.parser.dataset.learnsets.ResolvedSelectedLearnsetTable
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.PokeemeraldExpansionMetadata
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedDatasetLayouts
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.validate.PokemonDatasetValidators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelationshipMaterializersTest {
    @Test
    fun ordinaryGenThreeEvolutionsFailClosedWithoutTheTypedPhaseResult() {
        val bytes = ByteArray(128)
        putU16(bytes, 16, 4)
        putU16(bytes, 18, 16)
        putU16(bytes, 20, 2)
        val layout = layout(
            evolutions = TableLayout(0, 3, 16, elementSize = 8),
        )

        assertTrue(RelationshipMaterializers.evolutions(RomImage(bytes), layout).isEmpty())
    }

    @Test
    fun ordinaryGenThreeTypedEvolutionProjectionNeverPublishesNonzeroSpeciesZeroPayload() {
        val bytes = ByteArray(128)
        putU16(bytes, 0, 0xFFFF)
        putU16(bytes, 2, 2)
        putU16(bytes, 4, 2)
        putU16(bytes, 6, 4)
        putU16(bytes, 16, 4)
        putU16(bytes, 18, 16)
        putU16(bytes, 20, 2)
        val layout = layout(
            evolutions = TableLayout(0, 3, 16, elementSize = 8),
        ).withTypedEvolutions(bytes)

        val result = RelationshipMaterializers.evolutions(RomImage(bytes), layout)

        assertTrue(result.getValue(0).isEmpty())
        assertEquals(listOf(EvolutionEdge(2, 4, 16, bytes.copyOfRange(16, 24), 0)), result.getValue(1))
    }

    @Test
    fun ordinaryGenThreeDescriptionsFailClosedWithoutTheTypedPhaseResult() {
        val bytes = ByteArray(256)
        encodeGbaText(bytes, 0, "SEED")
        putU16(bytes, 12, 7)
        putU16(bytes, 14, 69)
        putGbaPointer(bytes, 16, 128)
        encodeGbaText(bytes, 128, "A STRANGE SEED")
        val layout = layout(
            descriptions = TableLayout(0, 1, 32, pointerOffsets = listOf(16)),
        )

        assertTrue(RelationshipMaterializers.descriptions(RomImage(bytes), layout).isEmpty())
    }

    @Test
    fun materializesGbaDescriptionDimensionsAndTextPointer() {
        val bytes = ByteArray(256)
        encodeGbaText(bytes, 0, "SEED")
        putU16(bytes, 12, 7)
        putU16(bytes, 14, 69)
        putGbaPointer(bytes, 16, 128)
        encodeGbaText(bytes, 128, "A STRANGE SEED")
        val layout = layout(
            descriptions = TableLayout(0, 1, 32, pointerOffsets = listOf(16)),
        ).withTypedDescriptions(bytes)

        val result = RelationshipMaterializers.descriptions(RomImage(bytes), layout).getValue(0)

        assertEquals("A STRANGE SEED", result.text)
        assertEquals(7, result.height)
        assertEquals(69, result.weight)
    }

    @Test
    fun materializesAUniqueBoundedOffByOneGbaDescriptionPointer() {
        val bytes = ByteArray(256)
        repeat(2) { index ->
            val base = index * 32
            encodeGbaText(bytes, base, if (index == 0) "FIRST" else "SECOND")
            putU16(bytes, base + 12, 7)
            putU16(bytes, base + 14, 69)
            putGbaPointer(bytes, base + 16, 128 + index * 32)
        }
        bytes[128] = 0xFF.toByte()
        encodeGbaText(bytes, 129, "RECOVERED TEXT")
        encodeGbaText(bytes, 160, "NEXT TEXT")
        val layout = layout(
            descriptions = TableLayout(0, 2, 32, pointerOffsets = listOf(16)),
        ).withTypedDescriptions(bytes)

        val result = RelationshipMaterializers.descriptions(RomImage(bytes), layout).getValue(0)

        assertEquals("RECOVERED TEXT", result.text)
    }

    @Test
    fun materializesGbaEvolutionSlotsAndPreservesRawMethod() {
        val bytes = ByteArray(128)
        putU16(bytes, 16, 42)
        putU16(bytes, 18, 99)
        putU16(bytes, 20, 2)
        val layout = layout(
            evolutions = TableLayout(0, 3, 16, elementSize = 8),
        ).withTypedEvolutions(bytes)

        val edges = RelationshipMaterializers.evolutions(RomImage(bytes), layout).getValue(1)

        assertEquals(1, edges.size)
        assertEquals(
            EvolutionEdge(2, 42, 99, bytes.copyOfRange(16, 24), conditionValue = 0),
            edges.single(),
        )
    }

    @Test
    fun materializesCfruEvolutionConditionMetadataInsteadOfDroppingTheEdge() {
        val bytes = ByteArray(128)
        putU16(bytes, 16, 34)
        putU16(bytes, 18, 96)
        putU16(bytes, 20, 2)
        putU16(bytes, 22, 23)
        val layout = layout(
            evolutions = TableLayout(0, 3, 16, elementSize = 8),
        ).withTypedEvolutions(bytes)

        val edge = RelationshipMaterializers.evolutions(RomImage(bytes), layout).getValue(1).single()

        assertEquals(2, edge.targetSpeciesId)
        assertEquals(34, edge.methodId)
        assertEquals(96, edge.parameter)
        assertEquals(23, edge.conditionValue)
        assertTrue(edge.raw.contentEquals(bytes.copyOfRange(16, 24)))
    }

    @Test
    fun materializesReservedGen3BattleTransformationEvolutionMethods() {
        val bytes = ByteArray(4 * 10 * 8)
        val stride = 10 * 8
        listOf(0xFFFF, 0xFFFE, 0xFFFD).forEachIndexed { slot, method ->
            val edge = stride + slot * 8
            putU16(bytes, edge, method)
            putU16(bytes, edge + 2, 100 + slot)
            putU16(bytes, edge + 4, slot + 1)
        }
        val layout = layout(
            evolutions = TableLayout(0, 4, stride, elementSize = 8),
        ).withTypedEvolutions(bytes)

        val edges = RelationshipMaterializers.evolutions(RomImage(bytes), layout).getValue(1)

        assertEquals(listOf(0xFFFF, 0xFFFE, 0xFFFD), edges.map { it.methodId })
        assertEquals(listOf(1, 2, 3), edges.map { it.targetSpeciesId })
    }

    @Test
    fun skipsMalformedAndOutOfBoundsGbaEvolutionSlotsWhileKeepingValidEdges() {
        val bytes = ByteArray(48)
        putU16(bytes, 0, 4)
        putU16(bytes, 2, 16)
        putU16(bytes, 4, 2)
        putU16(bytes, 16, 4)
        putU16(bytes, 18, 20)
        putU16(bytes, 20, 3)
        putU16(bytes, 22, 1)
        putU16(bytes, 24, 4)
        putU16(bytes, 26, 32)
        putU16(bytes, 28, 2)
        val layout = layout(
            evolutions = TableLayout(0, 3, 16, elementSize = 8),
        ).withTypedEvolutions(bytes)

        val result = RelationshipMaterializers.evolutions(RomImage(bytes), layout)

        assertTrue(result.getValue(0).isEmpty())
        assertEquals(
            listOf(EvolutionEdge(2, 4, 32, bytes.copyOfRange(24, 32), conditionValue = 0)),
            result.getValue(1),
        )
        assertTrue(result.getValue(2).isEmpty())
    }

    @Test
    fun rejectsMalformedGbaEvolutionLayoutMetadataWithoutThrowing() {
        val bytes = RomImage(ByteArray(64))
        val zeroElement = layout(
            evolutions = TableLayout(0, 2, 16, elementSize = 0),
        )
        val negativeCount = layout(
            evolutions = TableLayout(0, -1, 16, elementSize = 8),
        )
        val indivisibleStride = layout(
            evolutions = TableLayout(0, 2, 15, elementSize = 8),
        )

        assertTrue(RelationshipMaterializers.evolutions(bytes, zeroElement).isEmpty())
        assertTrue(RelationshipMaterializers.evolutions(bytes, negativeCount).isEmpty())
        assertTrue(RelationshipMaterializers.evolutions(bytes, indivisibleStride).isEmpty())
    }

    @Test
    fun materializesPackedGbaLevelUpLearnset() {
        val bytes = ByteArray(256)
        putGbaPointer(bytes, 0, 128)
        putU16(bytes, 128, (5 shl 9) or 33)
        putU16(bytes, 130, (10 shl 9) or 45)
        putU16(bytes, 132, 0xFFFF)
        val layout = layout(
            learnsets = TableLayout(0, 1, 4),
        ).copy(moveCount = 100).withTypedLearnsets(bytes)

        val entries = RelationshipMaterializers.learnsets(RomImage(bytes), layout).getValue(0)

        assertEquals(listOf(LearnsetEntry(5, 33), LearnsetEntry(10, 45)), entries)
    }

    @Test
    fun ordinaryGenThreeLearnsetsFailClosedWhenTypedStateIsMissing() {
        val bytes = ByteArray(256)
        putGbaPointer(bytes, 0, 128)
        putU16(bytes, 128, (5 shl 9) or 33)
        putU16(bytes, 130, 0xFFFF)
        val legacyOnly = layout(
            learnsets = TableLayout(
                0,
                1,
                4,
                elementSize = 2,
                format = TableRecordFormat.GEN3_PACKED_U16,
            ),
        ).copy(moveCount = 100)

        assertTrue(RelationshipMaterializers.learnsets(RomImage(bytes), legacyOnly).isEmpty())
    }

    @Test
    fun materializesNineBitPackedLearnsetWithExactly512MoveDefinitions() {
        val bytes = ByteArray(256)
        putGbaPointer(bytes, 0, 128)
        putU16(bytes, 128, (20 shl 9) or 489)
        putU16(bytes, 130, 0xFFFF)
        val layout = layout(
            learnsets = TableLayout(0, 1, 4),
        ).copy(moveCount = 512).withTypedLearnsets(bytes)

        val entries = RelationshipMaterializers.learnsets(RomImage(bytes), layout).getValue(0)

        assertEquals(listOf(LearnsetEntry(20, 489)), entries)
    }

    @Test
    fun materializesPackedLearnsetPrefixBoundedByTheAdjacentSpeciesPointer() {
        val bytes = ByteArray(0x300)
        putGbaPointer(bytes, 0, 0x100)
        putGbaPointer(bytes, 4, 0x106)
        putU16(bytes, 0x100, (5 shl 9) or 33)
        putU16(bytes, 0x102, (10 shl 9) or 45)
        putU16(bytes, 0x104, 0xFF00)
        repeat(6) { index ->
            putU16(bytes, 0x106 + index * 2, ((index + 1) shl 9) or (50 + index))
        }
        putU16(bytes, 0x112, 0xFFFF)
        val layout = layout(
            learnsets = TableLayout(0, 2, 4),
        ).copy(speciesCount = 2, moveCount = 355).withTypedLearnsets(bytes)

        val entries = RelationshipMaterializers.learnsets(RomImage(bytes), layout)

        assertEquals(
            listOf(LearnsetEntry(5, 33), LearnsetEntry(10, 45)),
            entries.getValue(0),
        )
        assertEquals(6, entries.getValue(1).size)
    }

    @Test
    fun sharesPackedLearnsetRecoveryWithoutMaterializingMalformedTailData() {
        val bytes = ByteArray(0x800)

        putGbaPointer(bytes, 0, 0x100)
        putU16(bytes, 0x100, 73)
        putU16(bytes, 0x102, (37 shl 9) or 76)
        putU16(bytes, 0x104, 0xFFFF)

        putGbaPointer(bytes, 4, 0x200)
        putU16(bytes, 0x200, (1 shl 9) or 161)
        repeat(4) { index -> putU16(bytes, 0x202 + index * 2, 0xCA00) }
        putU16(bytes, 0x20A, 0xFFFF)

        putGbaPointer(bytes, 8, 0x300)
        putU16(bytes, 0x300, (1 shl 9) or 86)
        putU16(bytes, 0x302, 0)
        putU16(bytes, 0x304, 0)
        putU16(bytes, 0x306, 0xFFFF)

        putGbaPointer(bytes, 12, 0x400)
        putU16(bytes, 0x400, 255)
        repeat(5) { index -> putU16(bytes, 0x402 + index * 2, 0) }
        putU16(bytes, 0x40C, 0xFFFF)

        val layout = layout(
            learnsets = TableLayout(0, 4, 4),
        ).copy(speciesCount = 4, moveCount = 512).withTypedLearnsets(bytes)

        val entries = RelationshipMaterializers.learnsets(RomImage(bytes), layout)

        assertEquals(listOf(LearnsetEntry(0, 73), LearnsetEntry(37, 76)), entries.getValue(0))
        assertEquals(listOf(LearnsetEntry(1, 161)), entries.getValue(1))
        assertEquals(listOf(LearnsetEntry(1, 86)), entries.getValue(2))
        assertFalse(entries.containsKey(3))
        assertTrue(entries.values.flatten().none { it.moveId == 0 || it.level > 100 })
    }

    @Test
    fun materializesExpandedGbaLevelUpLearnset() {
        val bytes = ByteArray(256)
        putGbaPointer(bytes, 0, 128)
        putU16(bytes, 128, 600)
        bytes[130] = 5
        putU16(bytes, 131, 700)
        bytes[133] = 10
        putU16(bytes, 134, 0)
        bytes[136] = 0xFF.toByte()
        val layout = layout(
            learnsets = TableLayout(0, 1, 4, elementSize = 3),
        ).copy(moveCount = 800).withTypedLearnsets(bytes)

        val entries = RelationshipMaterializers.learnsets(RomImage(bytes), layout).getValue(0)

        assertEquals(listOf(LearnsetEntry(5, 600), LearnsetEntry(10, 700)), entries)
    }

    @Test
    fun materializesCompiledLevelByteMoveHalfwordLearnset() {
        val bytes = ByteArray(1024)
        putGbaPointer(bytes, 0, 128)
        bytes[128] = 1
        putU16(bytes, 129, 33)
        bytes[131] = 10
        putU16(bytes, 132, 45)
        bytes[134] = 0xFE.toByte()
        val layout = layout(
            learnsets = TableLayout(
                offset = 0,
                count = 1,
                recordSize = 4,
                elementSize = 3,
                format = TableRecordFormat.GEN3_LEVEL_U8_MOVE_U16,
            ),
        ).copy(moveCount = 100).withTypedLearnsets(bytes)

        val entries = RelationshipMaterializers.learnsets(RomImage(bytes), layout).getValue(0)

        assertEquals(listOf(LearnsetEntry(1, 33), LearnsetEntry(10, 45)), entries)
    }

    @Test
    fun materializesCompiledLevelByteMoveHalfwordLearnsetWithUnorderedLevels() {
        val bytes = ByteArray(1024)
        putGbaPointer(bytes, 0, 128)
        bytes[128] = 37
        putU16(bytes, 129, 700)
        bytes[131] = 29
        putU16(bytes, 132, 7)
        bytes[134] = 0xFE.toByte()
        val layout = layout(
            learnsets = TableLayout(
                offset = 0,
                count = 1,
                recordSize = 4,
                elementSize = 3,
                format = TableRecordFormat.GEN3_LEVEL_U8_MOVE_U16,
            ),
        ).copy(moveCount = 800).withTypedLearnsets(bytes)

        val entries = RelationshipMaterializers.learnsets(RomImage(bytes), layout).getValue(0)

        assertEquals(listOf(LearnsetEntry(37, 700), LearnsetEntry(29, 7)), entries)
    }

    @Test
    fun materializesWideGbaLearnsetIncludingLevelZero() {
        val bytes = ByteArray(256)
        putGbaPointer(bytes, 0, 128)
        putU16(bytes, 128, 600)
        putU16(bytes, 130, 0)
        putU16(bytes, 132, 700)
        putU16(bytes, 134, 100)
        putU16(bytes, 136, 0xFFFF)
        putU16(bytes, 138, 0xBEEF)
        val layout = layout(
            learnsets = TableLayout(0, 1, 4, elementSize = 4),
        ).copy(moveCount = 800).withTypedLearnsets(bytes)

        val entries = RelationshipMaterializers.learnsets(RomImage(bytes), layout).getValue(0)

        assertEquals(listOf(LearnsetEntry(0, 600), LearnsetEntry(100, 700)), entries)
    }

    @Test
    fun skipsMalformedSpeciesWhenMaterializingAcceptedWideTable() {
        val bytes = ByteArray(0x2400)
        repeat(7) { species ->
            val target = 0x800 + species * 0x20
            putGbaPointer(bytes, species * 4, target)
            putU16(bytes, target, 700 + species)
            putU16(bytes, target + 2, if (species == 0) 0 else species)
            putU16(bytes, target + 4, 0xFFFF)
        }

        val outOfBoundsTarget = bytes.size - 2
        putGbaPointer(bytes, 7 * 4, outOfBoundsTarget)
        putU16(bytes, outOfBoundsTarget, 700)

        val unterminatedTarget = 0x1800
        putGbaPointer(bytes, 8 * 4, unterminatedTarget)
        repeat(128) { entry ->
            putU16(bytes, unterminatedTarget + entry * 4, 700 + entry % 50)
            putU16(bytes, unterminatedTarget + entry * 4 + 2, entry % 100)
        }

        val invalidMoveTarget = 0x1400
        putGbaPointer(bytes, 9 * 4, invalidMoveTarget)
        putU16(bytes, invalidMoveTarget, 801)
        putU16(bytes, invalidMoveTarget + 2, 1)
        putU16(bytes, invalidMoveTarget + 4, 0xFFFF)

        val rom = RomImage(bytes)
        val validation = PokemonDatasetValidators.gen3WideLearnsets(
            rom, pointerTableOffset = 0, speciesCount = 10, moveCount = 800,
        )
        val layout = layout(
            learnsets = TableLayout(0, 10, 4, elementSize = 4),
        ).copy(speciesCount = 10, moveCount = 800).withTypedLearnsets(bytes)

        assertTrue(validation.compatible)
        assertEquals(7, validation.validRecords)
        assertEquals(0.7, validation.confidence, 0.0)

        val entries = RelationshipMaterializers.learnsets(rom, layout)

        assertEquals(7, entries.size)
        assertEquals(listOf(LearnsetEntry(0, 700)), entries.getValue(0))
        assertFalse(entries.containsKey(7))
        assertFalse(entries.containsKey(8))
        assertFalse(entries.containsKey(9))
    }

    @Test
    fun materializesIntegratedPokeemeraldExpansionRelationships() {
        val bytes = ByteArray(1024)
        val species = 32
        val stride = 180
        val first = species + stride
        encodeGbaText(bytes, first + 31, "SEED")
        putU16(bytes, first + 62, 7)
        putU16(bytes, first + 64, 69)
        putGbaPointer(bytes, first + 76, 700)
        encodeGbaText(bytes, 700, "A STRANGE SEED")
        putGbaPointer(bytes, first + 148, 760)
        putU16(bytes, 760, 33)
        putU16(bytes, 762, 5)
        putU16(bytes, 764, 0xFFFF)
        putGbaPointer(bytes, first + 160, 800)
        putU16(bytes, 800, 1)
        putU16(bytes, 802, 16)
        putU16(bytes, 804, 2)
        putU16(bytes, 812, 0xFFFF)
        val metadata = expansionMetadata(stride)
        val layout = ResolvedRomLayout(
            family = EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = 2,
            moveCount = 100,
            tables = ProfileTables(
                descriptions = TableLayout(species, 2, stride, stride = stride, pointerOffsets = listOf(76)),
                evolutions = TableLayout(species + 160, 2, 4, stride = stride, valuesArePointers = true, elementSize = 12),
                learnsets = TableLayout(species + 148, 2, 4, stride = stride, valuesArePointers = true, elementSize = 4),
            ),
            pokeemeraldExpansion = metadata,
        )

        val description = RelationshipMaterializers.descriptions(RomImage(bytes), layout).getValue(1)
        val learnsets = RelationshipMaterializers.learnsets(RomImage(bytes), layout).getValue(1)
        val evolutions = RelationshipMaterializers.evolutions(RomImage(bytes), layout).getValue(1)

        assertEquals("SEED", description.category)
        assertEquals("A STRANGE SEED", description.text)
        assertEquals(7, description.height)
        assertEquals(69, description.weight)
        assertEquals(listOf(LearnsetEntry(5, 33)), learnsets)
        assertEquals(2, evolutions.single().targetSpeciesId)
        assertEquals(1, evolutions.single().methodId)
        assertEquals(16, evolutions.single().parameter)
    }

    @Test
    fun materializesCombinedGenTwoEvolutionAndLearnsetStream() {
        val bytes = ByteArray(0x8000)
        putU16(bytes, 0, 0x4020)
        val record = 0x4020
        bytes[record] = 1
        bytes[record + 1] = 16
        bytes[record + 2] = 2
        bytes[record + 3] = 0
        bytes[record + 4] = 5
        bytes[record + 5] = 33
        bytes[record + 6] = 0
        val table = TableLayout(0, 1, 2, variableLength = true, bank = 1)
        val layout = ResolvedRomLayout(
            family = EngineFamily.CRYSTAL,
            generation = 2,
            platform = Platform.GBC,
            speciesCount = 1,
            moveCount = 100,
            tables = ProfileTables(evolutions = table, learnsets = table),
        )

        assertEquals(
            listOf(EvolutionEdge(2, 1, 16, byteArrayOf(1, 16, 2))),
            RelationshipMaterializers.evolutions(RomImage(bytes), layout).getValue(1),
        )
        assertEquals(
            listOf(LearnsetEntry(5, 33)),
            RelationshipMaterializers.learnsets(RomImage(bytes), layout).getValue(1),
        )
    }

    @Test
    fun followsGenOneFarTextPointerForDescription() {
        val bytes = ByteArray(0x8000)
        putU16(bytes, 0, 0x4020)
        encodeGbText(bytes, 0x4020, "SEED")
        val metadata = 0x4020 + 5
        bytes[metadata + 4] = 0x17
        putU16(bytes, metadata + 5, 0x4060)
        bytes[metadata + 7] = 1
        encodeGbText(bytes, 0x4060, "A SEED")
        val layout = ResolvedRomLayout(
            family = EngineFamily.RED_BLUE,
            generation = 1,
            platform = Platform.GB,
            speciesCount = 1,
            moveCount = 1,
            tables = ProfileTables(descriptions = TableLayout(0, 1, 2, bank = 1)),
        )

        val description = RelationshipMaterializers.descriptions(RomImage(bytes), layout).getValue(1)

        assertEquals("SEED", description.category)
        assertEquals("A SEED", description.text)
    }

    private fun layout(
        descriptions: TableLayout? = null,
        evolutions: TableLayout? = null,
        learnsets: TableLayout? = null,
    ) = ResolvedRomLayout(
        family = EngineFamily.EMERALD,
        generation = 3,
        platform = Platform.GBA,
        speciesCount = 2,
        moveCount = 100,
        tables = ProfileTables(
            descriptions = descriptions,
            evolutions = evolutions,
            learnsets = learnsets,
        ),
    )

    private fun ResolvedRomLayout.withTypedDescriptions(bytes: ByteArray): ResolvedRomLayout {
        val selected = requireNotNull(tables.descriptions)
        val table = DescriptionTableLayout(
            offset = selected.offset.toLong(),
            count = selected.count.toLong(),
            recordSize = selected.recordSize,
            pointerOffsets = selected.pointerOffsets,
        )
        val rom = RomImage(bytes)
        val decoded = DescriptionCodec().decode(
            RomAnalysisSession(rom, RomHeaderReader.read(rom)),
            table,
        ) as DescriptionTableOutcome.Decoded
        return copy(
            resolvedDatasets = ResolvedDatasetLayouts(
                descriptions = ResolvedDescriptionLayout(table, decoded.rows),
            ),
        )
    }

    private fun ResolvedRomLayout.withTypedEvolutions(bytes: ByteArray): ResolvedRomLayout {
        val selected = requireNotNull(tables.evolutions)
        val elementSize = requireNotNull(selected.elementSize)
        val table = EvolutionTableLayout(
            offset = selected.offset.toLong(),
            count = selected.count.toLong(),
            slotsPerSpecies = selected.recordSize / elementSize,
            recordSize = elementSize,
        )
        val rom = RomImage(bytes)
        val decoded = EvolutionCodec().decodeGen3(
            RomAnalysisSession(rom, RomHeaderReader.read(rom)),
            table,
        ) as EvolutionTableOutcome.Decoded
        return copy(
            resolvedDatasets = ResolvedDatasetLayouts(
                evolutions = ResolvedEvolutionLayout(table, decoded.rows),
            ),
        )
    }

    private fun ResolvedRomLayout.withTypedLearnsets(bytes: ByteArray): ResolvedRomLayout {
        val selected = requireNotNull(tables.learnsets)
        val format = when (selected.format) {
            TableRecordFormat.GEN3_LEVEL_U8_MOVE_U16 -> LearnsetFormat.LevelU8MoveU16
            TableRecordFormat.GEN3_MOVE_U16_LEVEL_U8 -> LearnsetFormat.MoveU16LevelU8
            TableRecordFormat.GEN3_MOVE_U16_LEVEL_U16 -> LearnsetFormat.MoveU16LevelU16
            else -> when (selected.elementSize) {
                4 -> LearnsetFormat.MoveU16LevelU16
                3 -> LearnsetFormat.MoveU16LevelU8
                else -> LearnsetFormat.PackedU16(
                    com.enrpau.dualscreendex.parser.model.Gen3LearnsetEncoding.packedMoveBits(
                        requireNotNull(moveCount),
                    ),
                )
            }
        }
        val table = LearnsetTableLayout(selected.offset.toLong(), selected.count, format)
        val rom = RomImage(bytes)
        val decoded = LearnsetCodec().decodeGen3(
            RomAnalysisSession(rom, RomHeaderReader.read(rom)),
            table,
            requireNotNull(moveCount),
        ) as LearnsetTableOutcome.Decoded
        val resolved = ResolvedSelectedLearnsetTable(
            ResolvedLearnsetLayout(table, decoded.rows),
            confidence = 1.0,
            referenceCount = 1,
        )
        return copy(
            resolvedDatasets = ResolvedDatasetLayouts(
                typeChart = resolvedDatasets.typeChart,
                descriptions = resolvedDatasets.descriptions,
                evolutions = resolvedDatasets.evolutions,
                learnsets = ResolvedLearnsetSet(listOf(resolved), table.offset, selector = null),
            ),
        )
    }

    private fun encodeGbaText(target: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char ->
            target[offset + index] = when (char) {
                ' ' -> 0
                in 'A'..'Z' -> (0xBB + char.code - 'A'.code).toByte()
                else -> error("unsupported fixture character")
            }
        }
        target[offset + value.length] = 0xFF.toByte()
    }

    private fun encodeGbText(target: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char ->
            target[offset + index] = when (char) {
                ' ' -> 0x7F
                in 'A'..'Z' -> (0x80 + char.code - 'A'.code).toByte()
                else -> error("unsupported fixture character")
            }
        }
        target[offset + value.length] = 0x50
    }

    private fun putU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }

    private fun putGbaPointer(target: ByteArray, offset: Int, targetOffset: Int) {
        val value = 0x08000000 + targetOffset
        repeat(4) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun expansionMetadata(speciesStride: Int) = PokeemeraldExpansionMetadata(
        headerOffset = 0x204,
        versionMajor = 1,
        versionMinor = 15,
        versionPatch = 3,
        speciesRecordSize = speciesStride,
        speciesNameOffset = 44,
        speciesNameWidth = 13,
        categoryOffset = 31,
        nationalDexOffset = 60,
        heightOffset = 62,
        weightOffset = 64,
        descriptionPointerOffset = 76,
        frontSpritePointerOffset = 88,
        normalPalettePointerOffset = 96,
        abilitiesOffset = 24,
        growthRateOffset = 21,
        levelUpPointerOffset = 148,
        teachablePointerOffset = 152,
        eggMovePointerOffset = 156,
        evolutionPointerOffset = 160,
        moveRecordSize = 64,
        abilityRecordSize = 28,
        abilityNameWidth = 20,
        abilityDescriptionPointerOffset = 20,
    )
}
