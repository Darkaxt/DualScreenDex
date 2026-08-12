package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpriteValidatorsTest {
    @Test
    fun acceptsGen1EmbeddedSpritePointersAndCompressedStream() {
        val bytes = ByteArray(0x8000)
        bytes[10] = 0x11
        putU16(bytes, 11, 0x4100)
        putU16(bytes, 13, 0x4100)
        gen1ZeroSprite(width = 1).copyInto(bytes, 0x4100)

        val result = SpriteValidators.gen1(
            RomImage(bytes), baseStatsOffset = 0, speciesCount = 1,
            recordSize = 28, candidatePicBanks = intArrayOf(1),
        )

        assertTrue(result.compatible)
    }

    @Test
    fun rejectsTruncatedGen1CompressedStream() {
        val bytes = ByteArray(0x4101)
        bytes[10] = 0x11
        putU16(bytes, 11, 0x4100)
        putU16(bytes, 13, 0x4100)
        bytes[0x4100] = 0x11

        val result = SpriteValidators.gen1(
            RomImage(bytes), baseStatsOffset = 0, speciesCount = 1,
            recordSize = 28, candidatePicBanks = intArrayOf(1),
        )

        assertFalse(result.compatible)
    }

    @Test
    fun acceptsGen2FarPointerTableAndLzStream() {
        val bytes = ByteArray(0x8000)
        bytes[0] = 1
        putU16(bytes, 1, 0x4100)
        bytes[3] = 1
        putU16(bytes, 4, 0x4100)
        byteArrayOf(0, 0x12, 0xFF.toByte()).copyInto(bytes, 0x4100)

        val result = SpriteValidators.gen2(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 1, bankAdjustment = 0,
        )

        assertTrue(result.compatible)
    }

    @Test
    fun rejectsGen2RewriteBeforeOutputStart() {
        val bytes = ByteArray(0x8000)
        bytes[0] = 1
        putU16(bytes, 1, 0x4100)
        bytes[3] = 1
        putU16(bytes, 4, 0x4100)
        byteArrayOf(0x80.toByte(), 0x80.toByte(), 0xFF.toByte()).copyInto(bytes, 0x4100)

        val result = SpriteValidators.gen2(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 1, bankAdjustment = 0,
        )

        assertFalse(result.compatible)
    }

    @Test
    fun countsTheIndirectGen2UnownSpriteTableAsSpeciesCoverage() {
        val bytes = ByteArray(0xC000)
        repeat(201) { index ->
            val entry = index * 6
            bytes[entry] = 1
            putU16(bytes, entry + 1, 0x4100)
            bytes[entry + 3] = 1
            putU16(bytes, entry + 4, 0x4100)
        }
        repeat(6) { byte -> bytes[200 * 6 + byte] = 0xFF.toByte() }
        repeat(26) { form ->
            val entry = 0x8000 + form * 6
            bytes[entry] = 1
            putU16(bytes, entry + 1, 0x4100)
            bytes[entry + 3] = 1
            putU16(bytes, entry + 4, 0x4100)
        }
        byteArrayOf(0, 0x12, 0xFF.toByte()).copyInto(bytes, 0x8100)

        val result = SpriteValidators.gen2(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 201, bankAdjustment = 0,
            bankRemap = mapOf(1 to 2),
        )

        assertTrue(result.compatible)
        assertEquals(201, result.validRecords)
    }

    @Test
    fun rejectsIndirectGen2UnownTableWithMalformedUnsampledForm() {
        val bytes = ByteArray(0xC000)
        repeat(201) { index ->
            val entry = index * 6
            bytes[entry] = 1
            putU16(bytes, entry + 1, 0x4100)
            bytes[entry + 3] = 1
            putU16(bytes, entry + 4, 0x4100)
        }
        repeat(6) { byte -> bytes[200 * 6 + byte] = 0xFF.toByte() }
        repeat(26) { form ->
            val entry = 0x8000 + form * 6
            bytes[entry] = 1
            putU16(bytes, entry + 1, 0x4100)
            bytes[entry + 3] = 1
            putU16(bytes, entry + 4, 0x4100)
        }
        putU16(bytes, 0x8000 + 7 * 6 + 1, 0)
        byteArrayOf(0, 0x12, 0xFF.toByte()).copyInto(bytes, 0x8100)

        val result = SpriteValidators.gen2(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 201, bankAdjustment = 0,
            bankRemap = mapOf(1 to 2),
        )

        assertEquals(200, result.validRecords)
    }

    @Test
    fun rejectsUnrelatedSameLocalOffsetDataAsAnIndirectGen2UnownTable() {
        val bytes = ByteArray(0xC000)
        repeat(201) { index ->
            val entry = index * 6
            bytes[entry] = 1
            putU16(bytes, entry + 1, 0x4100)
            bytes[entry + 3] = 1
            putU16(bytes, entry + 4, 0x4100)
        }
        repeat(6) { byte -> bytes[200 * 6 + byte] = 0xFF.toByte() }
        listOf(0, 1, 12, 25).forEach { form ->
            val entry = 0x4000 + form * 6
            bytes[entry] = 1
            putU16(bytes, entry + 1, 0x4100)
            bytes[entry + 3] = 1
            putU16(bytes, entry + 4, 0x4100)
        }
        byteArrayOf(0, 0x12, 0xFF.toByte()).copyInto(bytes, 0x8100)

        val result = SpriteValidators.gen2(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 201, bankAdjustment = 0,
            bankRemap = mapOf(1 to 2),
        )

        assertEquals(200, result.validRecords)
    }

    @Test
    fun acceptsGbaSpriteTableWithValidLz77Sample() {
        val bytes = ByteArray(0x200)
        putU32(bytes, 0, 0x08000100)
        putU16(bytes, 4, 4)
        byteArrayOf(0x10, 4, 0, 0, 0, 1, 2, 3, 4).copyInto(bytes, 0x100)

        val result = SpriteValidators.gen3(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 1, recordSize = 8,
        )

        assertTrue(result.compatible)
    }

    @Test
    fun acceptsGbaSpriteWhoseCompressedSheetContainsTwoFrames() {
        val bytes = ByteArray(0x200)
        putU32(bytes, 0, 0x08000100)
        putU16(bytes, 4, 4)
        byteArrayOf(0x10, 8, 0, 0, 0, 1, 2, 3, 4, 0, 5, 6, 7, 8).copyInto(bytes, 0x100)

        val result = SpriteValidators.gen3(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 1, recordSize = 8,
        )

        assertTrue(result.compatible)
    }

    @Test
    fun acceptsGbaSpriteWithAFinalBackReferencePastTheDeclaredBoundary() {
        val bytes = ByteArray(0x200)
        putU32(bytes, 0, 0x08000100)
        putU16(bytes, 4, 5)
        byteArrayOf(0x10, 5, 0, 0, 0x20, 1, 2, 0x10, 0x01).copyInto(bytes, 0x100)

        val result = SpriteValidators.gen3(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 1, recordSize = 8,
        )

        assertTrue(result.compatible)
    }

    @Test
    fun rejectsGbaLz77BackReferenceBeforeOutputStart() {
        val bytes = ByteArray(0x200)
        putU32(bytes, 0, 0x08000100)
        putU16(bytes, 4, 4)
        byteArrayOf(0x10, 4, 0, 0, 0x80.toByte(), 0x10, 0).copyInto(bytes, 0x100)

        val result = SpriteValidators.gen3(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 1, recordSize = 8,
        )

        assertFalse(result.compatible)
    }

    @Test
    fun reportsDecodedGbaStreamsInsteadOfOnlyPlausiblePointers() {
        val bytes = ByteArray(0x300)
        repeat(2) { index ->
            putU32(bytes, index * 8, 0x08000100 + index * 0x40)
            putU16(bytes, index * 8 + 4, 4)
        }
        byteArrayOf(0x10, 4, 0, 0, 0, 1, 2, 3, 4).copyInto(bytes, 0x100)
        byteArrayOf(0x10, 4, 0, 0, 0x80.toByte(), 0x10, 0).copyInto(bytes, 0x140)

        val result = SpriteValidators.gen3(
            RomImage(bytes), pointerTableOffset = 0, speciesCount = 2, recordSize = 8,
        )

        assertFalse(result.compatible)
        assertTrue(result.validRecords == 1)
    }

    private fun gen1ZeroSprite(width: Int): ByteArray {
        val bits = mutableListOf<Int>()
        bits += 0
        repeat(2) { plane ->
            bits += 0
            val groups = width * width * 32
            val bitWidth = Integer.SIZE - Integer.numberOfLeadingZeros(groups + 1) - 1
            repeat(bitWidth - 1) { bits += 1 }
            bits += 0
            val base = (1 shl bitWidth) - 1
            val remainder = groups - base
            for (shift in bitWidth - 1 downTo 0) bits += (remainder ushr shift) and 1
            if (plane == 0) bits += 0
        }
        val bytes = ByteArray(1 + (bits.size + 7) / 8)
        bytes[0] = ((width shl 4) or width).toByte()
        bits.forEachIndexed { index, bit ->
            if (bit != 0) bytes[1 + index / 8] = (bytes[1 + index / 8].toInt() or (1 shl (7 - index % 8))).toByte()
        }
        return bytes
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
