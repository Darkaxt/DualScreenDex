package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen3SaveBlock1PointerResolverTest {
    @Test
    fun resolvesTheUniqueRamGlobalUsedForBothLiveLocationBytes() {
        val bytes = ByteArray(0x900)
        writeLocationConsumers(bytes, 0x030036F0, 0x100)

        assertEquals(0x030036F0L, Gen3SaveBlock1PointerResolver.resolve(RomImage(bytes)))
    }

    @Test
    fun failsClosedWhenTwoGlobalsHaveIndependentLocationAuthority() {
        val bytes = ByteArray(0x1200)
        writeLocationConsumers(bytes, 0x030036F0, 0x100)
        writeLocationConsumers(bytes, 0x03004000, 0x700)

        assertNull(Gen3SaveBlock1PointerResolver.resolve(RomImage(bytes)))
    }

    private fun writeLocationConsumers(bytes: ByteArray, global: Int, start: Int) {
        repeat(8) { index ->
            val instruction = start + index * 12
            val literal = start + 0x100 + index * 4
            putLiteralLoad(bytes, instruction, 3, literal)
            putU16(bytes, instruction + 2, 0x681B)
            putU16(bytes, instruction + 4, 0x7918)
            putU16(bytes, instruction + 6, 0x7959)
            putU32(bytes, literal, global)
        }
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
