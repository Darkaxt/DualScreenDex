package com.enrpau.dualscreendex.parser.analysis.arm7

import com.enrpau.dualscreendex.parser.analysis.thumb.ThumbDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbDecodeExhaustiveTest {
    @Test
    fun everyHalfwordHasOneDeterministicNonThrowingClassification() {
        val first = classifyAll()
        val second = classifyAll()

        assertEquals(first, second)
        assertEquals(
            linkedMapOf(
                "Decoded" to 56_078,
                "Undefined" to 4_834,
                "UnsupportedArchitecture" to 2_576,
                "NeedsSecondHalf" to 2_048,
            ),
            first,
        )
        assertEquals(65_536, first.values.sum())
    }

    @Test
    fun everyDecodedHalfwordDeclaresItsEffectsAndNoUnknownBecomesANop() {
        for (raw in 0..0xFFFF) {
            when (val result = ThumbDecoder.classifyHalfword(raw)) {
                is Arm7DecodeResult.Decoded -> {
                    val instruction = result.instruction
                    assertEquals(2, instruction.size)
                    assertEquals(raw.toLong(), instruction.raw)
                    assertTrue(instruction.registersRead.all { it in Arm7Register.entries })
                    assertTrue(instruction.registersWritten.all { it in Arm7Register.entries })
                    assertTrue(instruction.flagsRead.all { it in Arm7Flag.entries })
                    assertTrue(instruction.flagsWritten.all { it in Arm7Flag.entries })
                    assertFalse("decoder must never manufacture an unknown/NOP node", instruction is Arm7Unknown)
                }
                is Arm7DecodeResult.Undefined,
                is Arm7DecodeResult.UnsupportedArchitecture,
                is Arm7DecodeResult.NeedsSecondHalf -> Unit
                is Arm7DecodeResult.OutOfBounds -> throw AssertionError("single halfword classification cannot be out of bounds: $raw")
            }
        }
    }

    private fun classifyAll(): LinkedHashMap<String, Int> {
        val counts = linkedMapOf(
            "Decoded" to 0,
            "Undefined" to 0,
            "UnsupportedArchitecture" to 0,
            "NeedsSecondHalf" to 0,
        )
        for (raw in 0..0xFFFF) {
            val key = when (ThumbDecoder.classifyHalfword(raw)) {
                is Arm7DecodeResult.Decoded -> "Decoded"
                is Arm7DecodeResult.Undefined -> "Undefined"
                is Arm7DecodeResult.UnsupportedArchitecture -> "UnsupportedArchitecture"
                is Arm7DecodeResult.NeedsSecondHalf -> "NeedsSecondHalf"
                is Arm7DecodeResult.OutOfBounds -> throw AssertionError("unexpected out-of-bounds classification for $raw")
            }
            counts[key] = requireNotNull(counts[key]) + 1
        }
        return counts
    }
}
