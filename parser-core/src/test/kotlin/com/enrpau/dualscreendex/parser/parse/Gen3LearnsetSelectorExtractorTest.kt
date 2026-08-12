package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen3LearnsetSelectorExtractorTest {
    @Test
    fun extractsDirectSaveBlockByteMaskBranchThatLinksTwoValidatedRoots() {
        val bytes = ByteArray(0x800)
        writeSelector(bytes, 0x20, 0x100, 0x140)
        writeSaveBlock1Anchors(bytes)

        val result = Gen3LearnsetSelectorExtractor.extract(RomImage(bytes), setOf(0x100, 0x140))

        assertEquals(0x3DA6, result?.saveBlock1ByteOffset)
        assertEquals(0x02, result?.mask)
        assertEquals(0x100, result?.zeroTableOffset)
        assertEquals(0x140, result?.nonZeroTableOffset)
    }

    @Test
    fun ignoresASelectorWhoseBranchRootWasNotStructurallyValidated() {
        val bytes = ByteArray(0x800)
        writeSelector(bytes, 0x20, 0x100, 0x140)
        writeSaveBlock1Anchors(bytes)

        assertNull(Gen3LearnsetSelectorExtractor.extract(RomImage(bytes), setOf(0x100)))
    }

    @Test
    fun rejectsAnUnrelatedRamGlobalThatMimicsTheSelectorShape() {
        val bytes = ByteArray(0x800)
        writeSelector(bytes, 0x20, 0x100, 0x140)

        assertNull(Gen3LearnsetSelectorExtractor.extract(RomImage(bytes), setOf(0x100, 0x140)))
    }

    @Test
    fun unrelatedRamGlobalsDoNotEraseACompiledSaveBlock1Selector() {
        val bytes = ByteArray(0x3000)
        writeSelector(bytes, 0x20, 0x100, 0x140)
        writeSaveBlock1Anchors(bytes)
        repeat(257) { index ->
            val instruction = 0x1000 + index * 8
            val literal = instruction + 4
            putLiteralLoad(bytes, instruction, 3, literal)
            putU16(bytes, instruction + 2, 0x681B) // ldr r3, [r3]
            putU32(bytes, literal, 0x02001000 + index * 4)
        }

        val result = Gen3LearnsetSelectorExtractor.extract(RomImage(bytes), setOf(0x100, 0x140))

        assertEquals(0x3DA6, result?.saveBlock1ByteOffset)
        assertEquals(0x02, result?.mask)
        assertEquals(0x100, result?.zeroTableOffset)
        assertEquals(0x140, result?.nonZeroTableOffset)
    }

    @Test
    fun oneSidedLocationConsumersDoNotConsumeTheQualifiedGlobalBudget() {
        val bytes = ByteArray(0x3000)
        writeSelector(bytes, 0x20, 0x100, 0x140)
        writeSaveBlock1Anchors(bytes)
        repeat(257) { index ->
            val instruction = 0x1000 + index * 12
            val literal = instruction + 8
            putLiteralLoad(bytes, instruction, 3, literal)
            putU16(bytes, instruction + 2, 0x681B) // ldr r3, [r3]
            putU16(bytes, instruction + 4, 0x7918) // ldrb r0, [r3, #4], but never map number
            putU32(bytes, literal, 0x02001000 + index * 4)
        }

        val result = Gen3LearnsetSelectorExtractor.extract(RomImage(bytes), setOf(0x100, 0x140))

        assertEquals(0x3DA6, result?.saveBlock1ByteOffset)
        assertEquals(0x02, result?.mask)
        assertEquals(0x100, result?.zeroTableOffset)
        assertEquals(0x140, result?.nonZeroTableOffset)
    }

    @Test
    fun aSecondDistinctCompiledSelectorFailsClosed() {
        val bytes = ByteArray(0x900)
        writeSelector(bytes, 0x20, 0x100, 0x140)
        writeSecondSelector(bytes, 0x100, 0x140)
        writeSaveBlock1Anchors(bytes)

        assertNull(Gen3LearnsetSelectorExtractor.extract(RomImage(bytes), setOf(0x100, 0x140)))
    }

    private fun writeSaveBlock1Anchors(bytes: ByteArray) {
        repeat(8) { index ->
            val instruction = 0x100 + index * 12
            val literal = 0x500 + index * 4
            putLiteralLoad(bytes, instruction, 3, literal)
            putU16(bytes, instruction + 2, 0x681B) // ldr r3, [r3]
            putU16(bytes, instruction + 4, 0x7918) // ldrb r0, [r3, #4]
            putU16(bytes, instruction + 6, 0x7959) // ldrb r1, [r3, #5]
            putU32(bytes, literal, 0x030036F0)
        }
    }

    private fun writeSelector(bytes: ByteArray, offset: Int, zeroRoot: Int, nonZeroRoot: Int) {
        putU16(bytes, offset, 0x2102) // movs r1, #2
        putU16(bytes, offset + 2, 0xB500) // push {lr}
        putLiteralLoad(bytes, offset + 4, 3, 0x80)
        putU16(bytes, offset + 6, 0x681A) // ldr r2, [r3]
        putLiteralLoad(bytes, offset + 8, 3, 0x84)
        putU16(bytes, offset + 10, 0x5CD2) // ldrb r2, [r2, r3]
        putU16(bytes, offset + 12, 0x4211) // tst r1, r2
        putU16(bytes, offset + 14, 0xD001) // beq zero arm at 0x34
        putU16(bytes, offset + 16, 0xE00E) // b non-zero arm at 0x50
        putLiteralLoad(bytes, 0x34, 3, 0x88)
        putLiteralLoad(bytes, 0x50, 3, 0x8C)
        putU32(bytes, 0x80, 0x030036F0)
        putU32(bytes, 0x84, 0x00003DA6)
        putU32(bytes, 0x88, 0x08000000 + zeroRoot)
        putU32(bytes, 0x8C, 0x08000000 + nonZeroRoot)
    }

    private fun writeSecondSelector(bytes: ByteArray, zeroRoot: Int, nonZeroRoot: Int) {
        val offset = 0x600
        putU16(bytes, offset, 0x2102) // movs r1, #2
        putU16(bytes, offset + 2, 0xB500) // push {lr}
        putLiteralLoad(bytes, offset + 4, 3, 0x700)
        putU16(bytes, offset + 6, 0x681A) // ldr r2, [r3]
        putLiteralLoad(bytes, offset + 8, 3, 0x704)
        putU16(bytes, offset + 10, 0x5CD2) // ldrb r2, [r2, r3]
        putU16(bytes, offset + 12, 0x4211) // tst r1, r2
        putU16(bytes, offset + 14, 0xD007) // beq zero arm at 0x620
        putU16(bytes, offset + 16, 0xE016) // b non-zero arm at 0x640
        putLiteralLoad(bytes, 0x620, 3, 0x708)
        putLiteralLoad(bytes, 0x640, 3, 0x70C)
        putU32(bytes, 0x700, 0x030036F0)
        putU32(bytes, 0x704, 0x00003DA6)
        putU32(bytes, 0x708, 0x08000000 + zeroRoot)
        putU32(bytes, 0x70C, 0x08000000 + nonZeroRoot)
    }

    private fun putLiteralLoad(bytes: ByteArray, instructionOffset: Int, register: Int, literalOffset: Int) {
        val pc = (instructionOffset + 4) and -4
        putU16(bytes, instructionOffset, 0x4800 or (register shl 8) or ((literalOffset - pc) / 4))
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
