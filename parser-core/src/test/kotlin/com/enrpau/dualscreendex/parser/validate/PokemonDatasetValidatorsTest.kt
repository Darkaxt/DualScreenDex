package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PokemonDatasetValidatorsTest {
    @Test
    fun acceptsGen1BankRelativeDexEntryPointers() {
        val bytes = ByteArray(0x10000)
        putU16(bytes, 0x100, 0x4200)
        putGbText(bytes, 0x8200, "SEED")
        var cursor = 0x8205
        bytes[cursor++] = 2
        bytes[cursor++] = 4
        putU16(bytes, cursor, 150)
        cursor += 2
        bytes[cursor++] = 0x17
        putU16(bytes, cursor, 0x4300)
        cursor += 2
        bytes[cursor++] = 2
        bytes[cursor] = 0x50
        bytes[0x8300] = 0
        putGbText(bytes, 0x8301, "A SEED GROWS")

        val result = PokemonDatasetValidators.gen1Descriptions(
            RomImage(bytes), pointerTableOffset = 0x100, count = 1, entryBank = 2,
            codec = PokemonTextCodec.gbEnglish,
        )

        assertTrue(result.compatible)
    }

    @Test
    fun acceptsGen1PokedexTableWithInternalMissingNoSlots() {
        val bytes = ByteArray(0x10000)
        repeat(4) { index -> putU16(bytes, 0x100 + index * 2, 0x4200 + index * 0x20) }
        repeat(3) { index ->
            val entry = 0x8200 + index * 0x20
            putGbText(bytes, entry, "SEED")
            var cursor = entry + 5
            repeat(4) { bytes[cursor++] = 1 }
            bytes[cursor++] = 0x17
            putU16(bytes, cursor, 0x4300 + index * 0x20)
            cursor += 2
            bytes[cursor] = 2
            bytes[0x8300 + index * 0x20] = 0
            putGbText(bytes, 0x8301 + index * 0x20, "A SEED GROWS")
        }

        val result = PokemonDatasetValidators.gen1Descriptions(
            RomImage(bytes), pointerTableOffset = 0x100, count = 4, entryBank = 2,
            codec = PokemonTextCodec.gbEnglish,
        )

        assertTrue(result.compatible)
        assertEquals(3, result.validRecords)
    }

    @Test
    fun acceptsGen2MultiBankDexEntryPointers() {
        val bytes = ByteArray(0x14000)
        putU16(bytes, 0x100, 0x4200)
        putU16(bytes, 0x102, 0x4300)
        putGen2DexEntry(bytes, 0x8200, "SEED", "FIRST PAGE", "SECOND PAGE")
        putGen2DexEntry(bytes, 0x10300, "HERB", "GREEN LEAVES", "STRONG ROOTS")

        val result = PokemonDatasetValidators.gen2Descriptions(
            RomImage(bytes), pointerTableOffset = 0x100, count = 2,
            entryBanks = intArrayOf(2, 4), entriesPerBank = 1,
            codec = PokemonTextCodec.gbEnglish,
        )

        assertTrue(result.compatible)
    }

    @Test
    fun acceptsGen3SinglePagePokedexEntries() {
        val bytes = ByteArray(0x800)
        putGen3DexEntry(bytes, 0x100, recordSize = 32, textOffsets = intArrayOf(0x500))
        putGbaText(bytes, 0x500, "A SEED GROWS")

        val result = PokemonDatasetValidators.gen3Descriptions(
            RomImage(bytes), offset = 0x100, count = 1, recordSize = 32,
            descriptionPointerOffsets = intArrayOf(16), codec = PokemonTextCodec.gbaEnglish,
        )

        assertTrue(result.compatible)
    }

    @Test
    fun acceptsGen3TwoPagePokedexEntries() {
        val bytes = ByteArray(0x900)
        putGen3DexEntry(bytes, 0x100, recordSize = 36, textOffsets = intArrayOf(0x500, 0x600))
        putGbaText(bytes, 0x500, "FIRST PAGE")
        putGbaText(bytes, 0x600, "SECOND PAGE")

        val result = PokemonDatasetValidators.gen3Descriptions(
            RomImage(bytes), offset = 0x100, count = 1, recordSize = 36,
            descriptionPointerOffsets = intArrayOf(16, 20), codec = PokemonTextCodec.gbaEnglish,
        )

        assertTrue(result.compatible)
    }

    @Test
    fun rejectsGen3EntriesWhoseTextPointersEscapeRom() {
        val bytes = ByteArray(0x400)
        putGen3DexEntry(bytes, 0x100, recordSize = 32, textOffsets = intArrayOf(0x700))

        val result = PokemonDatasetValidators.gen3Descriptions(
            RomImage(bytes), offset = 0x100, count = 1, recordSize = 32,
            descriptionPointerOffsets = intArrayOf(16), codec = PokemonTextCodec.gbaEnglish,
        )

        assertFalse(result.compatible)
    }

    @Test
    fun acceptsGen1CombinedEvolutionAndLearnsetRecords() {
        val bytes = ByteArray(0x10000)
        putU16(bytes, 0x100, 0x4200)
        putU16(bytes, 0x102, 0x4210)
        byteArrayOf(1, 16, 2, 0, 1, 10, 7, 20, 0).copyInto(bytes, 0x8200)
        byteArrayOf(0, 1, 10, 0).copyInto(bytes, 0x8210)

        val result = PokemonDatasetValidators.gen12EvolutionsAndLearnsets(
            RomImage(bytes), pointerTableOffset = 0x100, speciesCount = 2,
            tableBank = 2, moveCount = 30, generation = 1,
        )

        assertTrue(result.evolutions.compatible)
        assertTrue(result.learnsets.compatible)
        assertEquals(2, result.evolutions.validRecords)
    }

    @Test
    fun acceptsGen2VariableWidthEvolutionRecords() {
        val bytes = ByteArray(0x10000)
        putU16(bytes, 0x100, 0x4200)
        putU16(bytes, 0x102, 0x4210)
        byteArrayOf(5, 20, 1, 2, 0, 1, 10, 0).copyInto(bytes, 0x8200)
        byteArrayOf(0, 1, 10, 0).copyInto(bytes, 0x8210)

        val result = PokemonDatasetValidators.gen12EvolutionsAndLearnsets(
            RomImage(bytes), pointerTableOffset = 0x100, speciesCount = 2,
            tableBank = 2, moveCount = 30, generation = 2,
        )

        assertTrue(result.evolutions.compatible)
        assertTrue(result.learnsets.compatible)
    }

    @Test
    fun acceptsGen2LearnsetsWhoseTerminatedRecordsAreNotLevelSorted() {
        val bytes = ByteArray(0x10000)
        putU16(bytes, 0x100, 0x4200)
        byteArrayOf(0, 1, 10, 45, 20, 23, 30, 0).copyInto(bytes, 0x8200)

        val result = PokemonDatasetValidators.gen12EvolutionsAndLearnsets(
            RomImage(bytes), pointerTableOffset = 0x100, speciesCount = 1,
            tableBank = 2, moveCount = 30, generation = 2,
        )

        assertTrue(result.learnsets.compatible)
        assertEquals(1, result.learnsets.validRecords)
    }

    @Test
    fun rejectsGen12LearnsetsWithUnknownMoveIds() {
        val bytes = ByteArray(0x10000)
        putU16(bytes, 0x100, 0x4200)
        byteArrayOf(0, 1, 31, 0).copyInto(bytes, 0x8200)

        val result = PokemonDatasetValidators.gen12EvolutionsAndLearnsets(
            RomImage(bytes), pointerTableOffset = 0x100, speciesCount = 1,
            tableBank = 2, moveCount = 30, generation = 1,
        )

        assertFalse(result.learnsets.compatible)
    }

    @Test
    fun rejectsGen1AndGen2LearnsetsWithIllegalLevels() {
        listOf(1, 2).forEach { generation ->
            val bytes = ByteArray(0x10000)
            putU16(bytes, 0x100, 0x4200)
            byteArrayOf(0, 101, 1, 0).copyInto(bytes, 0x8200)

            val result = PokemonDatasetValidators.gen12EvolutionsAndLearnsets(
                RomImage(bytes), pointerTableOffset = 0x100, speciesCount = 1,
                tableBank = 2, moveCount = 30, generation = generation,
            )

            assertFalse("Gen $generation illegal level", result.learnsets.compatible)
        }
    }

    @Test
    fun rejectsGen1AndGen2LearnsetsWithoutATerminatorWithinTheEntryCap() {
        listOf(1, 2).forEach { generation ->
            val bytes = ByteArray(0x10000)
            putU16(bytes, 0x100, 0x4200)
            bytes[0x8200] = 0
            repeat(128) { entry ->
                bytes[0x8201 + entry * 2] = 1
                bytes[0x8202 + entry * 2] = 1
            }
            bytes[0x8301] = 1

            val result = PokemonDatasetValidators.gen12EvolutionsAndLearnsets(
                RomImage(bytes), pointerTableOffset = 0x100, speciesCount = 1,
                tableBank = 2, moveCount = 30, generation = generation,
            )

            assertFalse("Gen $generation unterminated list", result.learnsets.compatible)
        }
    }

    @Test
    fun rejectsGen1AndGen2MalformedLearnsetPairs() {
        listOf(1, 2).forEach { generation ->
            val bytes = ByteArray(0x10000)
            putU16(bytes, 0x100, 0x4200)
            byteArrayOf(0, 1, 0, 0).copyInto(bytes, 0x8200)

            val result = PokemonDatasetValidators.gen12EvolutionsAndLearnsets(
                RomImage(bytes), pointerTableOffset = 0x100, speciesCount = 1,
                tableBank = 2, moveCount = 30, generation = generation,
            )

            assertFalse("Gen $generation malformed pair", result.learnsets.compatible)
        }
    }

    @Test
    fun acceptsGen3FixedEvolutionSlots() {
        val bytes = ByteArray(3 * 5 * 6)
        putU16(bytes, 5 * 6, 4)
        putU16(bytes, 5 * 6 + 2, 16)
        putU16(bytes, 5 * 6 + 4, 2)

        val result = PokemonDatasetValidators.gen3Evolutions(
            RomImage(bytes), offset = 0, speciesCount = 3, slotsPerSpecies = 5,
        )

        assertTrue(result.compatible)
    }

    @Test
    fun acceptsGen3EvolutionSlotsWithAbiPadding() {
        val bytes = ByteArray(3 * 5 * 8)
        putU16(bytes, 5 * 8, 4)
        putU16(bytes, 5 * 8 + 2, 16)
        putU16(bytes, 5 * 8 + 4, 2)

        val result = PokemonDatasetValidators.gen3Evolutions(
            RomImage(bytes), offset = 0, speciesCount = 3, slotsPerSpecies = 5,
            recordSize = 8,
        )

        assertTrue(result.compatible)
        assertEquals(40, result.recordSize)
    }

    @Test
    fun treatsTheFourthCfruEvolutionWordAsOpaqueConditionMetadata() {
        val stride = 5 * 8
        val bytes = ByteArray(3 * stride)
        putU16(bytes, stride, 34)
        putU16(bytes, stride + 2, 96)
        putU16(bytes, stride + 4, 2)
        putU16(bytes, stride + 6, 23)

        val result = PokemonDatasetValidators.gen3EvolutionValidation(
            RomImage(bytes), offset = 0, speciesCount = 3, slotsPerSpecies = 5,
            recordSize = 8,
        )

        assertTrue(result.evidence.compatible)
        assertEquals(3, result.evidence.validRecords)
        assertEquals(1, result.activeEdges)
        val record = PokemonDatasetValidators.decodeGen3EvolutionRow(
            RomImage(bytes), offset = stride, slotsPerSpecies = 5,
            recordSize = 8, speciesCount = 3,
        ).records.single()
        assertEquals(23, record.conditionValue)
        assertEquals(23, record.raw[6].toInt() and 0xFF)
        assertEquals(0, record.raw[7].toInt() and 0xFF)
    }

    @Test
    fun acceptsReservedGen3BattleTransformationEvolutionMethods() {
        val slots = 10
        val stride = slots * 8
        val bytes = ByteArray(4 * stride)
        listOf(0xFFFF, 0xFFFE, 0xFFFD).forEachIndexed { slot, method ->
            val edge = stride + slot * 8
            putU16(bytes, edge, method)
            putU16(bytes, edge + 2, 100 + slot)
            putU16(bytes, edge + 4, slot + 1)
        }

        val result = PokemonDatasetValidators.gen3EvolutionValidation(
            RomImage(bytes), offset = 0, speciesCount = 4,
            slotsPerSpecies = slots, recordSize = 8,
        )

        assertTrue(result.evidence.compatible)
        assertEquals(4, result.evidence.validRecords)
        assertEquals(3, result.activeEdges)
        assertEquals(1.0, result.structuralQuality, 0.0)
    }

    @Test
    fun acceptsIgnoredPayloadInDisabledGen3EvolutionSlots() {
        val bytes = ByteArray(3 * 5 * 8)
        putU16(bytes, 5 * 8, 4)
        putU16(bytes, 5 * 8 + 2, 16)
        putU16(bytes, 5 * 8 + 4, 2)
        putU16(bytes, 5 * 8 + 8 + 2, 99)
        putU16(bytes, 5 * 8 + 8 + 4, 2)

        val result = PokemonDatasetValidators.gen3Evolutions(
            RomImage(bytes), offset = 0, speciesCount = 3, slotsPerSpecies = 5,
            recordSize = 8,
        )

        assertTrue(result.compatible)
    }

    @Test
    fun acceptsCustomSentinelDataInTheUnusedGen3SpeciesZeroRow() {
        val bytes = ByteArray(3 * 5 * 8)
        putU16(bytes, 0, 0xFF00)
        putU16(bytes, 2, 16)
        putU16(bytes, 4, 30)
        putU16(bytes, 5 * 8, 4)
        putU16(bytes, 5 * 8 + 2, 16)
        putU16(bytes, 5 * 8 + 4, 2)

        val result = PokemonDatasetValidators.gen3Evolutions(
            RomImage(bytes), offset = 0, speciesCount = 3, slotsPerSpecies = 5,
            recordSize = 8,
        )

        assertTrue(result.compatible)
    }

    @Test
    fun keepsPartialGen3EvolutionCoverageAvailableForManualReview() {
        val stride = 5 * 6
        val bytes = ByteArray(10 * stride)
        putU16(bytes, stride, 4)
        putU16(bytes, stride + 2, 16)
        putU16(bytes, stride + 4, 2)
        repeat(3) { index ->
            putU16(bytes, (7 + index) * stride, 1)
        }

        val result = PokemonDatasetValidators.gen3Evolutions(
            RomImage(bytes), offset = 0, speciesCount = 10, slotsPerSpecies = 5,
        )

        assertTrue(result.compatible)
        assertEquals(7, result.validRecords)
        assertEquals(10, result.totalRecords)
        assertTrue(result.reasons.any { it.contains("manual review") })
    }

    @Test
    fun acceptsScatteredInvalidGen3EvolutionRowsWhenAValidMajorityRemains() {
        val stride = 5 * 6
        val bytes = ByteArray(10 * stride)
        putU16(bytes, stride, 4)
        putU16(bytes, stride + 2, 16)
        putU16(bytes, stride + 4, 2)
        repeat(3) { index ->
            putU16(bytes, (2 + index * 2) * stride, 1)
        }

        val result = PokemonDatasetValidators.gen3Evolutions(
            RomImage(bytes), offset = 0, speciesCount = 10, slotsPerSpecies = 5,
        )

        assertTrue(result.compatible)
        assertEquals(7, result.validRecords)
        assertEquals(10, result.totalRecords)
    }

    @Test
    fun rejectsGen3EvolutionTableWithoutAnyActiveEdges() {
        val result = PokemonDatasetValidators.gen3Evolutions(
            RomImage(ByteArray(10 * 5 * 8)), offset = 0, speciesCount = 10,
            slotsPerSpecies = 5, recordSize = 8,
        )

        assertFalse(result.compatible)
        assertTrue(result.reasons.any { it.contains("active evolution") })
    }

    @Test
    fun rejectsGen3EvolutionTableWithoutAStrictValidRowMajority() {
        val stride = 5 * 8
        val bytes = ByteArray(10 * stride)
        putU16(bytes, stride, 4)
        putU16(bytes, stride + 2, 16)
        putU16(bytes, stride + 4, 2)
        repeat(5) { index ->
            putU16(bytes, (5 + index) * stride, 1)
        }

        val result = PokemonDatasetValidators.gen3Evolutions(
            RomImage(bytes), offset = 0, speciesCount = 10, slotsPerSpecies = 5,
            recordSize = 8,
        )

        assertFalse(result.compatible)
        assertEquals(5, result.validRecords)
        assertEquals(10, result.totalRecords)
    }

    @Test
    fun rejectsDeterministicRandomLookingGen3EvolutionBytes() {
        val bytes = ByteArray(10 * 5 * 8) { index -> (index * 73 + 41).toByte() }

        val result = PokemonDatasetValidators.gen3Evolutions(
            RomImage(bytes), offset = 0, speciesCount = 10, slotsPerSpecies = 5,
            recordSize = 8,
        )

        assertFalse(result.compatible)
        assertTrue(result.validRecords < result.totalRecords)
    }

    @Test
    fun acceptsGen3PackedLearnsetPointerTable() {
        val bytes = ByteArray(0x400)
        repeat(3) { index ->
            putU32(bytes, index * 4, 0x08000100 + index * 0x20)
            putU16(bytes, 0x100 + index * 0x20, packedLearnset(level = 1, move = 10 + index))
            putU16(bytes, 0x102 + index * 0x20, 0xFFFF)
        }

        val result = PokemonDatasetValidators.gen3Learnsets(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 3, moveCount = 50,
        )

        assertTrue(result.compatible)
    }

    @Test
    fun acceptsGen3PackedLearnsetsWithUnorderedLevels() {
        val bytes = ByteArray(0x200)
        putU32(bytes, 0, 0x08000100)
        putU16(bytes, 0x100, packedLearnset(level = 37, move = 87))
        putU16(bytes, 0x102, packedLearnset(level = 29, move = 7))
        putU16(bytes, 0x104, 0xFFFF)

        val result = PokemonDatasetValidators.gen3Learnsets(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 1, moveCount = 355,
        )

        assertTrue(result.compatible)
        assertEquals(1, result.validRecords)
    }

    @Test
    fun recoversPackedLearnsetTailOnlyAtTheAdjacentPointerBoundary() {
        val bytes = ByteArray(0x300)
        putU32(bytes, 0, 0x08000100)
        putU32(bytes, 4, 0x08000106)
        putU32(bytes, 8, 0x08000120)
        putU16(bytes, 0x100, packedLearnset(level = 5, move = 33))
        putU16(bytes, 0x102, packedLearnset(level = 10, move = 45))
        putU16(bytes, 0x104, 0xFF00)
        repeat(6) { index ->
            putU16(bytes, 0x106 + index * 2, packedLearnset(level = index + 1, move = 50 + index))
        }
        putU16(bytes, 0x112, 0xFFFF)
        putU16(bytes, 0x120, packedLearnset(level = 20, move = 80))
        putU16(bytes, 0x122, 0xFFFF)

        val result = PokemonDatasetValidators.gen3Learnsets(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 3, moveCount = 355,
        )

        assertTrue(result.compatible)
        assertEquals(3, result.validRecords)
        assertEquals(3, result.totalRecords)
        assertTrue(result.reviewRecommended)
        assertTrue(result.reasons.any { "recovered 1 short malformed tails" in it })
        assertTrue(result.reasons.none { "quarantined" in it })
    }

    @Test
    fun acceptsLevelZeroAndRecoversOnlyShortPackedLearnsetTails() {
        val bytes = ByteArray(0x800)

        putU32(bytes, 0, 0x08000100)
        putU16(bytes, 0x100, packedLearnset(level = 0, move = 73))
        putU16(bytes, 0x102, packedLearnset(level = 37, move = 76))
        putU16(bytes, 0x104, 0xFFFF)

        putU32(bytes, 4, 0x08000200)
        putU16(bytes, 0x200, packedLearnset(level = 1, move = 161))
        repeat(4) { index -> putU16(bytes, 0x202 + index * 2, 0xCA00) }
        putU16(bytes, 0x20A, 0xFFFF)

        putU32(bytes, 8, 0x08000300)
        putU16(bytes, 0x300, packedLearnset(level = 1, move = 86))
        putU16(bytes, 0x302, 0)
        putU16(bytes, 0x304, 0)
        putU16(bytes, 0x306, 0xFFFF)

        putU32(bytes, 12, 0x08000400)
        putU16(bytes, 0x400, packedLearnset(level = 0, move = 255))
        repeat(5) { index -> putU16(bytes, 0x402 + index * 2, 0) }
        putU16(bytes, 0x40C, 0xFFFF)

        repeat(6) { index ->
            val target = 0x500 + index * 0x20
            putU32(bytes, 16 + index * 4, 0x08000000 + target)
            putU16(bytes, target, packedLearnset(level = index + 1, move = 33 + index))
            putU16(bytes, target + 2, 0xFFFF)
        }

        val result = PokemonDatasetValidators.gen3Learnsets(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 10, moveCount = 512,
        )

        assertTrue(result.compatible)
        assertEquals(9, result.validRecords)
        assertEquals(10, result.totalRecords)
        assertEquals(9, result.coveredRecords)
        assertEquals(10, result.expectedRecords)
        assertEquals(1, result.incompleteRecords)
        assertTrue(result.reviewRecommended)
        assertTrue(result.reasons.any { "recovered 2 short malformed tails" in it })
        assertTrue(result.reasons.any { "quarantined 1 malformed rows" in it })
    }

    @Test
    fun rejectsGen3LearnsetsWithUnknownMoveIds() {
        val bytes = ByteArray(0x200)
        putU32(bytes, 0, 0x08000100)
        putU16(bytes, 0x100, packedLearnset(level = 1, move = 51))
        putU16(bytes, 0x102, 0xFFFF)

        val result = PokemonDatasetValidators.gen3Learnsets(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 1, moveCount = 50,
        )

        assertFalse(result.compatible)
    }

    @Test
    fun acceptsExpandedGen3TenBitMoveLearnsets() {
        val bytes = ByteArray(0x200)
        putU32(bytes, 0, 0x08000100)
        putU16(bytes, 0x100, packedLearnset(level = 12, move = 700, moveBits = 10))
        putU16(bytes, 0x102, 0xFFFF)

        val result = PokemonDatasetValidators.gen3Learnsets(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 1, moveCount = 800,
            moveBits = 10,
        )

        assertTrue(result.compatible)
    }

    @Test
    fun acceptsCfruThreeByteLevelUpMoveRecords() {
        val bytes = ByteArray(0x200)
        putU32(bytes, 0, 0x08000100)
        putU16(bytes, 0x100, 700)
        bytes[0x102] = 12
        putU16(bytes, 0x103, 0)
        bytes[0x105] = 0xFF.toByte()

        val result = PokemonDatasetValidators.gen3ExpandedLearnsets(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 1, moveCount = 800,
        )

        assertTrue(result.compatible)
    }

    @Test
    fun acceptsCfruThreeByteLearnsetsWithUnorderedLevels() {
        val bytes = ByteArray(0x200)
        putU32(bytes, 0, 0x08000100)
        putU16(bytes, 0x100, 700)
        bytes[0x102] = 37
        putU16(bytes, 0x103, 7)
        bytes[0x105] = 29
        putU16(bytes, 0x106, 0)
        bytes[0x108] = 0xFF.toByte()

        val result = PokemonDatasetValidators.gen3ExpandedLearnsets(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 1, moveCount = 800,
        )

        assertTrue(result.compatible)
        assertEquals(1, result.validRecords)
    }

    @Test
    fun acceptsCompiledLevelByteMoveHalfwordLearnsetsWithUnorderedLevels() {
        val bytes = ByteArray(0x200)
        putU32(bytes, 0, 0x08000100)
        bytes[0x100] = 37
        putU16(bytes, 0x101, 700)
        bytes[0x103] = 29
        putU16(bytes, 0x104, 7)
        bytes[0x106] = 0xFE.toByte()

        val result = PokemonDatasetValidators.gen3LevelMoveLearnsets(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 1, moveCount = 800,
        )

        assertTrue(result.compatible)
        assertEquals(1, result.validRecords)
    }

    @Test
    fun rejectsMalformedCompiledLevelByteMoveHalfwordLearnsets() {
        fun decode(record: ByteArray, boundary: Int = record.size) =
            PokemonDatasetValidators.decodeGen3LevelMoveLearnset(
                RomImage(record), offset = 0, moveCount = 800, endExclusive = boundary,
            )

        assertEquals(null, decode(byteArrayOf(101, 33, 0, 0xFE.toByte())))
        assertEquals(null, decode(byteArrayOf(1, 0, 0, 0xFE.toByte())))
        assertEquals(null, decode(byteArrayOf(1, 0x20, 0x03, 0xFE.toByte())))
        assertEquals(null, decode(byteArrayOf(1, 33, 0), boundary = 3))
        assertEquals(null, decode(byteArrayOf(1, 33, 0), boundary = 2))
    }

    @Test
    fun acceptsWideGen3LearnsetsWithOneSparseNullPointer() {
        val bytes = ByteArray(0x800)
        repeat(10) { index ->
            if (index == 6) return@repeat
            val target = 0x200 + index * 0x20
            putU32(bytes, index * 4, 0x08000000 + target)
            putU16(bytes, target, 700 + index)
            putU16(bytes, target + 2, if (index == 0) 0 else index)
            if (index == 0) {
                putU16(bytes, target + 4, 750)
                putU16(bytes, target + 6, 13)
                putU16(bytes, target + 8, 751)
                putU16(bytes, target + 10, 1)
                putU16(bytes, target + 12, 0xFFFF)
                putU16(bytes, target + 14, 0xBEEF)
            } else {
                putU16(bytes, target + 4, 0xFFFF)
                putU16(bytes, target + 6, 0xBEEF)
            }
        }

        val result = PokemonDatasetValidators.gen3WideLearnsets(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 10, moveCount = 800,
        )

        assertTrue(result.compatible)
        assertEquals(9, result.validRecords)
        assertEquals(10, result.totalRecords)
        assertEquals(4, result.elementSize)
    }

    @Test
    fun acceptsWideGen3LearnsetsAtNinetyPercentStructuralQuality() {
        val bytes = ByteArray(0x800)
        repeat(10) { index ->
            val target = 0x200 + index * 0x20
            putU32(bytes, index * 4, 0x08000000 + target)
            putU16(bytes, target, if (index == 6) 801 else 700 + index)
            putU16(bytes, target + 2, index)
            putU16(bytes, target + 4, 0xFFFF)
        }

        val result = PokemonDatasetValidators.gen3WideLearnsets(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 10, moveCount = 800,
        )

        assertTrue(result.compatible)
        assertEquals(9, result.validRecords)
        assertTrue(result.reasons.any { "structural quality 9/10 (90.00%)" in it })
    }

    @Test
    fun acceptsWideGen3LearnsetsWithABoundedNullTailAndReportsCoverage() {
        val bytes = ByteArray(0x1000)
        repeat(4) { index ->
            putWideLearnset(bytes, pointerCell = index * 4, target = 0x400 + index * 0x20, move = 700 + index)
        }

        val result = PokemonDatasetValidators.gen3WideLearnsets(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 10, moveCount = 800,
        )

        assertTrue(result.compatible)
        assertEquals(4, result.validRecords)
        assertEquals(10, result.totalRecords)
        assertEquals(0.4, result.confidence, 0.0)
        assertTrue(result.reasons.any { "coverage 4/10" in it })
    }

    @Test
    fun acceptsWideGen3LearnsetsWithScatteredNullGaps() {
        val bytes = ByteArray(0x1000)
        listOf(0, 2, 5, 8).forEachIndexed { entry, species ->
            putWideLearnset(bytes, pointerCell = species * 4, target = 0x400 + entry * 0x20, move = 700 + entry)
        }

        val result = PokemonDatasetValidators.gen3WideLearnsets(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 10, moveCount = 800,
        )

        assertTrue(result.compatible)
        assertEquals(4, result.validRecords)
        assertEquals(0.4, result.confidence, 0.0)
    }

    @Test
    fun acceptsWideGen3LearnsetsAtNinetyFivePercentStructuralQuality() {
        val bytes = ByteArray(0x1000)
        repeat(20) { index ->
            putWideLearnset(
                bytes,
                pointerCell = index * 4,
                target = 0x400 + index * 0x20,
                move = if (index == 19) 801 else 700 + index,
            )
        }

        val result = PokemonDatasetValidators.gen3WideLearnsets(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 20, moveCount = 800,
        )

        assertTrue(result.compatible)
        assertEquals(19, result.validRecords)
        assertEquals(0.95, result.confidence, 0.0)
    }

    @Test
    fun acceptsWideGen3LearnsetsAtSeventyPercentStructuralQuality() {
        val bytes = ByteArray(0x1000)
        repeat(10) { index ->
            putWideLearnset(
                bytes,
                pointerCell = index * 4,
                target = 0x400 + index * 0x20,
                move = if (index >= 7) 801 else 700 + index,
            )
        }

        val result = PokemonDatasetValidators.gen3WideLearnsets(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 10, moveCount = 800,
        )

        assertTrue(result.compatible)
        assertEquals(7, result.validRecords)
        assertEquals(0.7, result.confidence, 0.0)
        assertTrue(result.reasons.any { "coverage 7/10 (70.00%)" in it })
        assertTrue(result.reasons.any { "structural quality 7/10 (70.00%)" in it })
    }

    @Test
    fun rejectsWideGen3LearnsetsWithoutAStrictNonNullMajority() {
        val bytes = ByteArray(0x1000)
        repeat(10) { index ->
            putWideLearnset(
                bytes,
                pointerCell = index * 4,
                target = 0x400 + index * 0x20,
                move = if (index >= 5) 801 else 700 + index,
            )
        }

        val result = PokemonDatasetValidators.gen3WideLearnsets(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 10, moveCount = 800,
        )

        assertFalse(result.compatible)
        assertEquals(5, result.validRecords)
    }

    @Test
    fun rejectsAllNullWideGen3PointerTable() {
        val result = PokemonDatasetValidators.gen3WideLearnsets(
            RomImage(ByteArray(0x200)), pointerTableOffset = 0, speciesCount = 10, moveCount = 800,
        )

        assertFalse(result.compatible)
        assertEquals(0, result.validRecords)
    }

    @Test
    fun rejectsWideGen3TableWithoutThreeValidRecordsOfEvidence() {
        val bytes = ByteArray(0x1000)
        repeat(2) { index ->
            putWideLearnset(bytes, pointerCell = index * 4, target = 0x400 + index * 0x20, move = 700 + index)
        }

        val result = PokemonDatasetValidators.gen3WideLearnsets(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 10, moveCount = 800,
        )

        assertFalse(result.compatible)
        assertEquals(2, result.validRecords)
        assertEquals(0.2, result.confidence, 0.0)
    }

    @Test
    fun rejectsRandomNonPointerWideGen3Table() {
        val bytes = ByteArray(0x200)
        repeat(10) { index -> putU32(bytes, index * 4, 0x12345678 + index) }

        val result = PokemonDatasetValidators.gen3WideLearnsets(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 10, moveCount = 800,
        )

        assertFalse(result.compatible)
        assertEquals(0, result.validRecords)
    }

    private fun putGen2DexEntry(bytes: ByteArray, offset: Int, category: String, first: String, second: String) {
        var cursor = putGbText(bytes, offset, category)
        repeat(4) { bytes[cursor++] = 1 }
        cursor = putGbText(bytes, cursor, first)
        putGbText(bytes, cursor, second)
    }

    private fun putGen3DexEntry(bytes: ByteArray, offset: Int, recordSize: Int, textOffsets: IntArray) {
        putGbaText(bytes, offset, "SEED")
        putU16(bytes, offset + 12, 7)
        putU16(bytes, offset + 14, 69)
        textOffsets.forEachIndexed { index, target -> putU32(bytes, offset + 16 + index * 4, 0x08000000 + target) }
        bytes[offset + recordSize - 1] = 0
    }

    private fun putGbText(bytes: ByteArray, offset: Int, value: String): Int {
        value.forEachIndexed { index, character ->
            bytes[offset + index] = when (character) {
                ' ' -> 0x7F
                in 'A'..'Z' -> 0x80 + (character - 'A')
                else -> error("unsupported fixture character $character")
            }.toByte()
        }
        bytes[offset + value.length] = 0x50
        return offset + value.length + 1
    }

    private fun putGbaText(bytes: ByteArray, offset: Int, value: String): Int {
        value.forEachIndexed { index, character ->
            bytes[offset + index] = when (character) {
                ' ' -> 0
                in 'A'..'Z' -> 0xBB + (character - 'A')
                else -> error("unsupported fixture character $character")
            }.toByte()
        }
        bytes[offset + value.length] = 0xFF.toByte()
        return offset + value.length + 1
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun putWideLearnset(bytes: ByteArray, pointerCell: Int, target: Int, move: Int, level: Int = 1) {
        putU32(bytes, pointerCell, 0x08000000 + target)
        putU16(bytes, target, move)
        putU16(bytes, target + 2, level)
        putU16(bytes, target + 4, 0xFFFF)
    }

    private fun packedLearnset(level: Int, move: Int, moveBits: Int = 9): Int = (level shl moveBits) or move
}
