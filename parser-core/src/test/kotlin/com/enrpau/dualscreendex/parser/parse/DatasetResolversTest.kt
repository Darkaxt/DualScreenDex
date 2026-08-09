package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetResolversTest {
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
    fun resolvesRelocatedPackedLearnsetPointerTable() {
        val bytes = ByteArray(0x1000)
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
