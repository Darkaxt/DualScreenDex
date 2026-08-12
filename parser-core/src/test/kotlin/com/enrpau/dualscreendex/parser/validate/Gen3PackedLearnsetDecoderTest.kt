package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3PackedLearnsetDecoderTest {
    @Test
    fun doesNotRecoverAnEmptyPrefixFromMoveZeroPadding() {
        val bytes = ByteArray(8)
        putU16(bytes, 0, 0)
        putU16(bytes, 2, 0xFFFF)

        val result = Gen3PackedLearnsetDecoder.decode(
            RomImage(bytes), offset = 0, moveCount = 512, moveBits = 9,
        )

        assertEquals(Gen3PackedLearnsetDisposition.QUARANTINED, result.disposition)
        assertFalse(result.usable)
        assertEquals(emptyList<Gen3PackedLearnsetRecord>(), result.records)
    }

    @Test
    fun recoversANonemptyLegalPrefixWithAShortInvalidSuffixAtTheNextPointerBoundary() {
        val bytes = ByteArray(16)
        putU16(bytes, 0, packed(level = 5, move = 33))
        putU16(bytes, 2, packed(level = 10, move = 45))
        putU16(bytes, 4, 0xFF00)
        putU16(bytes, 6, packed(level = 1, move = 1))

        val result = Gen3PackedLearnsetDecoder.decode(
            RomImage(bytes), offset = 0, moveCount = 355, moveBits = 9, endExclusive = 6,
        )

        assertEquals(Gen3PackedLearnsetDisposition.RECOVERED_SHORT_TAIL, result.disposition)
        assertTrue(result.usable)
        assertEquals(
            listOf(Gen3PackedLearnsetRecord(5, 33), Gen3PackedLearnsetRecord(10, 45)),
            result.records,
        )
        assertEquals(1, result.discardedTailWords)
    }

    @Test
    fun doesNotRecoverAcrossMoreThanFourInvalidWordsBeforeTheNextPointerBoundary() {
        val bytes = ByteArray(24)
        putU16(bytes, 0, packed(level = 5, move = 33))
        repeat(5) { index -> putU16(bytes, 2 + index * 2, 0xFF00) }
        putU16(bytes, 12, 0xFFFF)

        val result = Gen3PackedLearnsetDecoder.decode(
            RomImage(bytes), offset = 0, moveCount = 355, moveBits = 9, endExclusive = 12,
        )

        assertEquals(Gen3PackedLearnsetDisposition.QUARANTINED, result.disposition)
        assertFalse(result.usable)
    }

    @Test
    fun doesNotRecoverABoundedInvalidSuffixWithoutALegalPrefix() {
        val bytes = ByteArray(8)
        putU16(bytes, 0, 0xFF00)
        putU16(bytes, 2, packed(level = 1, move = 1))

        val result = Gen3PackedLearnsetDecoder.decode(
            RomImage(bytes), offset = 0, moveCount = 355, moveBits = 9, endExclusive = 2,
        )

        assertEquals(Gen3PackedLearnsetDisposition.QUARANTINED, result.disposition)
        assertFalse(result.usable)
    }

    private fun packed(level: Int, move: Int): Int = (level shl 9) or move

    private fun putU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }
}
