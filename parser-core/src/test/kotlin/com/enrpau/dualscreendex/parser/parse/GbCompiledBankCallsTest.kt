package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GbCompiledBankCallsTest {
    @Test fun recognizesCompleteSixteenBitShiftMultiply() {
        assertTrue(GbCompiledBankCalls.repeatedAdd(rom(SHIFT_MULTIPLY), ROOT))
    }

    @Test fun recognizesCompleteIncrementedCounterCopy() {
        assertTrue(GbCompiledBankCalls.byteCopy(rom(COUNTER_COPY), ROOT))
    }

    @Test fun recognizesCompleteFourByteUnrolledCopy() {
        assertTrue(GbCompiledBankCalls.byteCopy(rom(UNROLLED_COPY), ROOT))
    }

    @Test fun rejectsEveryAlteredInstructionInNewHelperBodies() {
        listOf(SHIFT_MULTIPLY, COUNTER_COPY, UNROLLED_COPY).forEach { body ->
            body.indices.forEach { index ->
                val altered = body.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
                val rom = rom(altered)
                val accepted = if (body === SHIFT_MULTIPLY) {
                    GbCompiledBankCalls.repeatedAdd(rom, ROOT)
                } else {
                    GbCompiledBankCalls.byteCopy(rom, ROOT)
                }
                assertFalse("changed instruction at $index in ${body.size}-byte body", accepted)
            }
        }
    }

    @Test fun rejectsTruncatedAndBankCrossingHelpers() {
        listOf(SHIFT_MULTIPLY, COUNTER_COPY, UNROLLED_COPY).forEach { body ->
            for (missing in 1..body.size) {
                val truncated = RomImage(ByteArray(ROOT + body.size - missing).also { bytes ->
                    body.copyInto(bytes, ROOT, endIndex = body.size - missing)
                })
                assertFalse(GbCompiledBankCalls.byteCopy(truncated, ROOT))
                assertFalse(GbCompiledBankCalls.repeatedAdd(truncated, ROOT))
            }
            val crossingRoot = 0x4000 - body.size + 1
            val crossing = RomImage(ByteArray(0x8000).also { body.copyInto(it, crossingRoot) })
            assertFalse(GbCompiledBankCalls.byteCopy(crossing, crossingRoot))
            assertFalse(GbCompiledBankCalls.repeatedAdd(crossing, crossingRoot))
        }
    }

    @Test fun rejectsIdenticalHelperBodiesOutsideHomeBank() {
        listOf(SHIFT_MULTIPLY, COUNTER_COPY, UNROLLED_COPY).forEach { body ->
            val bankedRoot = 0x4100
            val banked = RomImage(ByteArray(0x8000).also { body.copyInto(it, bankedRoot) })
            assertFalse(GbCompiledBankCalls.byteCopy(banked, bankedRoot))
            assertFalse(GbCompiledBankCalls.repeatedAdd(banked, bankedRoot))
        }
    }

    @Test fun preservesClassicMultiplyAndCopyAuthority() {
        assertTrue(GbCompiledBankCalls.repeatedAdd(rom(bytes("a7 c8 09 3d 20 fc c9")), ROOT))
        assertTrue(GbCompiledBankCalls.byteCopy(rom(bytes("2a 12 13 0b 79 b0 20 f8 c9")), ROOT))
        val optimized = bytes("78 a7 28 0c 79 a7 28 01 04 cd 10 01 05 20 fa c9 2a 12 13 0d 20 fa c9")
        assertTrue(GbCompiledBankCalls.byteCopy(rom(optimized), ROOT))
        optimized[10] = 0x11
        assertFalse(GbCompiledBankCalls.byteCopy(rom(optimized), ROOT))
    }

    private fun rom(body: ByteArray): RomImage =
        RomImage(ByteArray(0x4000).also { body.copyInto(it, ROOT) })

    private companion object {
        const val ROOT = 0x100
        val SHIFT_MULTIPLY = bytes("a7 c8 c5 1f 30 01 09 cb 21 cb 10 a7 20 f5 c1 c9")
        val COUNTER_COPY = bytes("04 0c 18 03 2a 12 13 0d 20 fa 05 20 f7 c9")
        val UNROLLED_COPY = bytes(
            "04 cb 39 30 03 2a 12 13 cb 39 30 06 2a 12 13 2a 12 13 28 0f " +
                "2a 12 13 2a 12 13 2a 12 13 2a 12 13 0d 20 f1 05 c8 0e 40 18 eb",
        )

        fun bytes(value: String): ByteArray =
            value.split(' ').map { it.toInt(16).toByte() }.toByteArray()
    }
}
