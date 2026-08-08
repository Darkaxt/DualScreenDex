package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
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
}
