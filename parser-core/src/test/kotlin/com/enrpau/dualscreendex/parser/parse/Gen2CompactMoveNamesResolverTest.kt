package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.text.Gen2PlainNameCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen2CompactMoveNamesResolverTest {
    @Test fun resolvesFourByteRegistryWithCompiledNegativeOneIndexBias() {
        val result = requireNotNull(resolve(fixture(false)))
        assertEquals(NAMES, result.moveNames.offset)
        assertEquals(255, result.moveNames.count)
        assertEquals(true, result.moveNames.variableLength)
        assertEquals(DATA, result.moveData.offset)
        assertEquals(8, result.moveData.recordSize)
    }

    @Test fun resolvesThreeByteRegistryWithIndependentReservedStringSlot() {
        val result = requireNotNull(resolve(fixture(true)))
        assertEquals(NAMES + 2, result.moveNames.offset)
        assertEquals(255, result.moveNames.count)
        assertEquals(DATA, result.moveData.offset)
    }

    @Test fun derivesRelocatedRegistryAndStringIterator() {
        val bytes = fixture(false)
        bytes.copyInto(bytes, REGISTRY + 0x600, REGISTRY, REGISTRY + 8)
        bytes[DISPATCH + 7] = ((REGISTRY + 0x600) and 0xFF).toByte()
        bytes[DISPATCH + 10] = ((REGISTRY + 0x600) ushr 8).toByte()
        bytes.copyInto(bytes, NTH + 0x100, NTH, NTH + 14)
        bytes.word(DISPATCH + 31, NTH + 0x100)
        assertEquals(NAMES, requireNotNull(resolve(bytes)).moveNames.offset)
    }

    @Test fun rejectsBrokenDispatcherIterationCopyAndBankRestoration() {
        for (threeByte in listOf(false, true)) {
            val original = fixture(threeByte)
            val offsets = if (threeByte) listOf(DISPATCH, DISPATCH + 2, DISPATCH + 5,
                DISPATCH + 10, DISPATCH + 12, DISPATCH + 22, DISPATCH + 29,
                DISPATCH + 30, DISPATCH + 33, DISPATCH + 37, DISPATCH + 39,
                DISPATCH + 41, DISPATCH + 43, DISPATCH + 44, DISPATCH + 48,
                DISPATCH + 55, DISPATCH + 57, DISPATCH + 58, DISPATCH + 59,
                DISPATCH + 60, DISPATCH + 61, DISPATCH + 62, DISPATCH + 63)
            else listOf(DISPATCH, DISPATCH + 4, DISPATCH + 5, DISPATCH + 6,
                DISPATCH + 8, DISPATCH + 9, DISPATCH + 11, DISPATCH + 12,
                DISPATCH + 13, DISPATCH + 15, DISPATCH + 17, DISPATCH + 19,
                DISPATCH + 21, DISPATCH + 22, DISPATCH + 26, DISPATCH + 29,
                DISPATCH + 30, DISPATCH + 34, DISPATCH + 36, DISPATCH + 37,
                DISPATCH + 38, DISPATCH + 39)
            (offsets + listOf(NTH + 6, NTH + 8, NTH + 9, NTH + 11, NTH + 13,
                TYPE_COPY + 7, 0x09, 0x0A)).forEach { offset ->
                assertNull("name dispatcher instruction $offset", resolve(original.copyOf().also {
                    it[offset] = (it[offset].toInt() xor 1).toByte()
                }))
            }
        }
    }

    @Test fun rejectsUnlinkedTagsWrongIndexBiasAndMismatchedInputBuffers() {
        val four = fixture(false)
        assertNull(resolve(four.copyOf().also { it[GETTER + 1] = 8 }))
        assertNull(resolve(four.copyOf().also { it[REGISTRY + 4] = 0 }))
        assertNull(resolve(four.copyOf().also { it[DISPATCH + 14] = 0x88.toByte() }))
        val three = fixture(true)
        assertNull(resolve(three.copyOf().also { it.word(GETTER + 4, 0xC301) }))
        assertNull(resolve(three.copyOf().also { it.word(GETTER + 10, 0xC201) }))
        assertNull(resolve(three.copyOf().also { it.word(GETTER + 16, 0xC801) }))
    }

    @Test fun rejectsTypeTagAliasingTheNamedMoveInput() {
        val bytes = fixture(true)
        bytes.word(GETTER + 7, 0xC300)
        bytes.word(DISPATCH + 17, 0xC300)
        assertNull(resolve(bytes))
    }

    @Test fun requiresCompleteByteIdMoveDetailsAndEveryCopiedName() {
        for (threeByte in listOf(false, true)) {
            val bytes = fixture(threeByte)
            assertNull(resolve(bytes.copyOf().also { it[DATA + 254 * 8] = 0 }))
            val first = NAMES + if (threeByte) 2 else 0
            assertNull(resolve(bytes.copyOf().also { it[first] = 0x50 }))
            assertNull(resolve(bytes.copyOf().also { it.fill(0x7F, first, first + 14) }))
            assertNull(resolve(bytes.copyOf().also { it.word(REGISTRY + if (threeByte) 4 else 6, 0x7FFB) }))
        }
    }

    @Test fun rejectsCompetingCompleteNameRoots() {
        val bytes = fixture(false)
        bytes.copyInto(bytes, GETTER + 0x600, GETTER, GETTER + 4)
        bytes.copyInto(bytes, DISPATCH + 0x600, DISPATCH, DISPATCH + 42)
        bytes.copyInto(bytes, REGISTRY + 0x600, REGISTRY, REGISTRY + 8)
        bytes[DISPATCH + 0x600 + 7] = ((REGISTRY + 0x600) and 0xFF).toByte()
        bytes[DISPATCH + 0x600 + 10] = ((REGISTRY + 0x600) ushr 8).toByte()
        bytes.copyInto(bytes, NAMES + 0x1000, NAMES, NAMES + 255 * 6)
        bytes.word(REGISTRY + 0x600 + 6, 0x5200)
        assertNull(resolve(bytes))
    }

    private fun resolve(bytes: ByteArray): Gen2CompactMovesResolution? = Gen2CompactMoveNamesResolver.resolve(
        RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBC, "CRYSTAL")),
        Gen2PlainNameCodec.english53, (0..18).toSet(),
    )

    private fun fixture(threeByte: Boolean): ByteArray = ByteArray(0x10000).also { bytes ->
        bytes.put(0x08, "e0 87 ea 00 20 c9")
        bytes.put(0x18, "c3 00 06")
        bytes.put(0x20, "c3 00 07")
        bytes.put(0x40, "c1 d1 e1 c9")
        bytes.put(0x400, "e8 fd d5 e5 f8 08 56 5f f0 87 32 7b 5e 36 04 2b 36 80 2b 72 2b 73 e1 d1 c3 08 00")
        bytes.put(0x480, "f5 e5 f8 04 7e cf e1 f1 33 c9")
        bytes.put(0x500, "cd 00 04 04 0c 18 03 2a 12 13 0d 20 fa 05 20 f7 c9")
        bytes.put(0x520, "cd 00 04 7e c9")
        bytes.put(0x600, "a7 c8 c5 1f 30 01 09 cb 21 cb 10 a7 20 f5 c1 c9")
        bytes.put(TYPE_COPY, "2a 12 13 0b 79 b0 20 f8 c9")
        bytes.put(0xB00, "3d 21 00 42 01 08 00 df 3e 02 d5 cd 00 05 e1 cd 80 0b 01 07 00 09 77 c9")
        bytes.put(0xB80, "e5 01 07 00 09 3e 02 cd 20 05 e1 fe 02 c8 47 fa 00 c2 cb 7f 78 c0 e5 01 03 00 09 3e 02 cd 20 05 e1 fe 09 3e 00 d8 3c c9")
        bytes.put(NTH, "a7 c8 c5 47 2a fe 53 20 fb 05 20 f8 c1 c9")
        if (threeByte) {
            bytes.put(GETTER, "e5 3e 02 ea 00 c3 fa 00 c1 ea 00 c2 cd 00 09 11 00 c8 e1 c9")
            bytes.put(DISPATCH, "f0 87 e5 d5 c5 f5 fa 00 c3 fe 01 20 11 fa 00 c2 ea 00 c1 cd 00 0d 21 0b 00 19 5d 54 18 1c 3d 5f 16 00 21 00 0a 19 19 19 2a cf 2a 66 6f fa 00 c2 cd 80 0c 11 00 c8 01 0d 00 e7 f1 c1 d1 e1 cf c9")
            bytes.put(REGISTRY, "01 00 60 01 00 42")
            bytes.put(NAMES, "9e 53")
        } else {
            bytes.put(GETTER, "3e 04 18 7c")
            bytes.put(DISPATCH, "e5 11 00 c8 d5 c5 c6 00 6f ce 0a 95 67 f0 87 f5 2a 47 2a cf 2a 66 6f fa 00 c1 ea 00 c2 80 cd 80 0c 01 0d 00 e7 f1 cf c3 40 00")
            bytes.put(REGISTRY, "ff 01 00 60 ff 01 00 42")
        }
        val first = NAMES + if (threeByte) 2 else 0
        repeat(255) { index ->
            bytes.put(first + index * 6, "92 a7 a8 a5 b3 53")
            bytes.put(DATA + index * 8, "00 06 50 09 ff 0a 00 01")
            bytes[DATA + index * 8] = (index + 1).toByte()
        }
    }

    private fun ByteArray.put(offset: Int, value: String) {
        value.split(' ').map { it.toInt(16).toByte() }.toByteArray().copyInto(this, offset)
    }

    private fun ByteArray.word(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private companion object {
        const val TYPE_COPY = 0x700
        const val GETTER = 0x880
        const val DISPATCH = 0x900
        const val REGISTRY = 0xA00
        const val NTH = 0xC80
        const val NAMES = 0x4200
        const val DATA = 0x8200
    }
}
