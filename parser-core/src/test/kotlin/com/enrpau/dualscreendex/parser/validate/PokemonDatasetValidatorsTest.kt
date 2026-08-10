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
    fun trimsABoundedMissingTailFromGen3EvolutionTables() {
        val stride = 5 * 6
        val bytes = ByteArray(10 * stride)
        putU16(bytes, 9 * stride, 1)

        val result = PokemonDatasetValidators.gen3Evolutions(
            RomImage(bytes), offset = 0, speciesCount = 10, slotsPerSpecies = 5,
        )

        assertTrue(result.compatible)
        assertEquals(9, result.totalRecords)
        assertTrue(result.reasons.any { it.contains("trailing") })
    }

    @Test
    fun rejectsScatteredInvalidGen3EvolutionRows() {
        val stride = 5 * 6
        val bytes = ByteArray(10 * stride)
        putU16(bytes, 5 * stride, 1)

        val result = PokemonDatasetValidators.gen3Evolutions(
            RomImage(bytes), offset = 0, speciesCount = 10, slotsPerSpecies = 5,
        )

        assertFalse(result.compatible)
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

    private fun packedLearnset(level: Int, move: Int, moveBits: Int = 9): Int = (level shl moveBits) or move
}
