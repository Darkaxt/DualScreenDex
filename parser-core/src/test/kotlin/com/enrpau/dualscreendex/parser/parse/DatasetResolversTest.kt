package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetResolversTest {
    @Test
    fun reconcilesReadableMoveNamesToTheValidatedMoveDataPrefix() {
        val data = ValidationEvidence(
            compatible = true,
            validRecords = 793,
            totalRecords = 793,
            confidence = 1.0,
            reasons = emptyList(),
        )

        assertEquals(793, DatasetResolvers.reconciledMoveCount(923, data))
    }

    @Test
    fun resolvesRelocatedGen3DescriptionArrayFromRecordShape() {
        val bytes = ByteArray(0x1000)
        repeat(3) { index ->
            val base = 0x200 + index * 32
            putGbaText(bytes, base, if (index == 0) "UNKNOWN" else "SEED")
            putU16(bytes, base + 12, if (index == 0) 0 else 7)
            putU16(bytes, base + 14, if (index == 0) 0 else 69)
            putU32(bytes, base + 16, 0x08000800 + index * 0x20)
            putGbaText(bytes, 0x800 + index * 0x20, "POKEMON TEXT")
        }

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes), speciesCount = 3, inherited = TableLayout(0x600, 3, 32),
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertTrue(result.compatible)
        assertEquals(0x200, result.offset)
    }

    @Test
    fun resolvesPartialPokedexDescriptionArrayForExpandedSpeciesCatalog() {
        val bytes = ByteArray(0x1000)
        repeat(4) { index ->
            val base = 0x200 + index * 32
            putGbaText(bytes, base, if (index == 0) "UNKNOWN" else "SEED")
            putU16(bytes, base + 12, if (index == 0) 0 else 7)
            putU16(bytes, base + 14, if (index == 0) 0 else 69)
            putU32(bytes, base + 16, 0x08000800 + index * 0x20)
            putGbaText(bytes, 0x800 + index * 0x20, "POKEMON TEXT")
        }
        repeat(6) { index ->
            val base = 0x400 + index * 32
            putGbaText(bytes, base, if (index == 1) "SEED" else "OTHER")
            putU16(bytes, base + 12, 7)
            putU16(bytes, base + 14, 69)
            putU32(bytes, base + 16, 0x08000900 + index * 0x10)
            putGbaText(bytes, 0x900 + index * 0x10, "DECOY TEXT")
        }

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes), speciesCount = 8, inherited = null,
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertTrue(result.compatible)
        assertEquals(4, result.totalRecords)
        assertEquals(0x200, result.offset)
    }

    @Test
    fun resolvesExpandedEightSlotEvolutionArray() {
        val bytes = ByteArray(0x1000)
        val stride = 8 * 8
        putU16(bytes, 0x200 + stride, 4)
        putU16(bytes, 0x200 + stride + 2, 16)
        putU16(bytes, 0x200 + stride + 4, 2)
        putU16(bytes, 0x200 + stride * 2, 4)
        putU16(bytes, 0x200 + stride * 2 + 2, 32)
        putU16(bytes, 0x200 + stride * 2 + 4, 3)

        val result = DatasetResolvers.gen3Evolutions(
            RomImage(bytes), speciesCount = 4, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(0x200, result.offset)
        assertEquals(stride, result.recordSize)
    }

    @Test
    fun resolvesCfruThirtyTwoSlotEvolutionArray() {
        val bytes = ByteArray(0x2000)
        val stride = 32 * 8
        putU16(bytes, 0x200 + stride, 4)
        putU16(bytes, 0x200 + stride + 2, 21)
        putU16(bytes, 0x200 + stride + 4, 2)
        putU16(bytes, 0x200 + stride * 2, 4)
        putU16(bytes, 0x200 + stride * 2 + 2, 32)
        putU16(bytes, 0x200 + stride * 2 + 4, 3)

        val result = DatasetResolvers.gen3Evolutions(
            RomImage(bytes), speciesCount = 4, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(0x200, result.offset)
        assertEquals(stride, result.recordSize)
    }

    @Test
    fun resolvesRelocatedPackedLearnsetPointerTable() {
        val bytes = ByteArray(0x1000)
        // A valid pointer may legally target the final ROM byte, but it cannot be dereferenced as
        // a u16 learnset marker. It must not terminate the scan before the real table is reached.
        putU32(bytes, 0, 0x08000FFF)
        repeat(3) { index ->
            val target = if (index == 0) 0x800 else 0x800 + (index - 1) * 0x20
            putU32(bytes, 0x200 + index * 4, 0x08000000 + target)
            putU16(bytes, target, (1 shl 9) or (10 + index))
            putU16(bytes, target + 2, 0xFFFF)
        }
        // Species NONE conventionally reuses the first real species learnset.
        putU32(bytes, 0x204, 0x08000800)

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = 3, moveCount = 50, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(0x200, result.offset)
    }

    @Test
    fun deterministicallySelectsTheLowestCompletePackedRulesetWithoutAnInheritedOffset() {
        val bytes = ByteArray(0x1400)
        listOf(0x200 to 0x800, 0x400 to 0xA00).forEach { (table, learnsets) ->
            repeat(3) { index ->
                val target = learnsets + index * 0x20
                putU32(bytes, table + index * 4, 0x08000000 + target)
                putU16(bytes, target, (1 shl 9) or (10 + index))
                putU16(bytes, target + 2, 0xFFFF)
            }
            putU32(bytes, table + 4, 0x08000000 + learnsets)
        }

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = 3, moveCount = 50, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(0x200, result.offset)
    }

    @Test
    fun keepsNineBitPackedLearnsetsWhenMoveCatalogContainsExactly512Moves() {
        val bytes = ByteArray(0x1000)
        repeat(3) { index ->
            val target = 0x800 + index * 0x20
            putU32(bytes, 0x200 + index * 4, 0x08000000 + target)
            if (index == 0) {
                putU16(bytes, target, 0xFFFF)
            } else {
                putU16(bytes, target, (1 shl 9) or (489 + index))
                putU16(bytes, target + 2, 0xFFFF)
            }
        }

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = 3, moveCount = 512, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(0x200, result.offset)
        assertEquals(2, result.elementSize)
    }

    @Test
    fun resolvesCfruExpandedLearnsetPointerTable() {
        val bytes = ByteArray(0x1000)
        repeat(3) { index ->
            val target = 0x800 + index * 0x20
            putU32(bytes, 0x200 + index * 4, 0x08000000 + target)
            if (index == 0) {
                putU16(bytes, target, 0)
                bytes[target + 2] = 0xFF.toByte()
            } else {
                putU16(bytes, target, 600 + index)
                bytes[target + 2] = (10 + index).toByte()
                putU16(bytes, target + 3, 0)
                bytes[target + 5] = 0xFF.toByte()
            }
        }

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = 3, moveCount = 800, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(0x200, result.offset)
        assertEquals(3, result.elementSize)
    }

    @Test
    fun resolvesReferencedExpandedLearnsetTableWithoutAnEmptySpeciesZeroRecord() {
        val bytes = ByteArray(0x1000)
        putU32(bytes, 0x100, 0x08000200)
        repeat(3) { index ->
            val target = 0x800 + index * 0x20
            putU32(bytes, 0x200 + index * 4, 0x08000000 + target)
            putU16(bytes, target, 600 + index)
            bytes[target + 2] = (1 + index * 6).toByte()
            putU16(bytes, target + 3, 0)
            bytes[target + 5] = 0xFF.toByte()
        }

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = 3, moveCount = 800, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(0x200, result.offset)
        assertEquals(3, result.elementSize)
    }

    private fun putGbaText(bytes: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, character ->
            bytes[offset + index] = when (character) {
                ' ' -> 0
                in 'A'..'Z' -> 0xBB + (character - 'A')
                else -> error("unsupported fixture character $character")
            }.toByte()
        }
        bytes[offset + value.length] = 0xFF.toByte()
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
