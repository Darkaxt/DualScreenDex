package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GbaReferenceIndexTest {
    @Test
    fun countsCompiledLiteralReferencesThroughOneBoundedIndex() {
        val bytes = ByteArray(0x200)
        putReference(bytes, 0x20, 0x80, 0x100)
        putReference(bytes, 0x24, 0x84, 0x100)
        putReference(bytes, 0x28, 0x88, 0x140)

        val result = GbaReferenceIndexBuilder.build(RomImage(bytes))

        assertTrue(!result.overflowed)
        assertEquals(2, result.counts[0x100])
        assertEquals(1, result.counts[0x140])
    }

    @Test
    fun failsClosedBeforeRetainingMoreDistinctTargetsThanTheBudget() {
        val bytes = ByteArray(0x200)
        putReference(bytes, 0x20, 0x80, 0x100)
        putReference(bytes, 0x24, 0x84, 0x120)
        putReference(bytes, 0x28, 0x88, 0x140)

        val result = GbaReferenceIndexBuilder.build(RomImage(bytes), maxDistinctTargets = 2)

        assertTrue(result.overflowed)
        assertTrue(result.counts.isEmpty())
        assertTrue(result.overflowReason?.contains("budget exceeded") == true)
    }

    private fun putReference(bytes: ByteArray, instructionOffset: Int, literalOffset: Int, target: Int) {
        val pc = (instructionOffset + 4) and -4
        putU16(bytes, instructionOffset, 0x4800 or ((literalOffset - pc) / 4))
        putU32(bytes, literalOffset, 0x08000000 + target)
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
