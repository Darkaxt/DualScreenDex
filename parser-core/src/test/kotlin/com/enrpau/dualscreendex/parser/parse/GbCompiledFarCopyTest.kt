package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GbCompiledFarCopyTest {
    @Test fun resolvesCompleteStackRewrittenFarCopy() {
        assertEquals(GbCompiledFarCopyAuthority(STATE, 255), resolve(stackFixture()))
    }

    @Test fun resolvesCompleteHramDispatchedFarCopy() {
        assertEquals(GbCompiledFarCopyAuthority(STATE, 127), resolve(hramFixture()))
    }

    @Test fun followsInlineOrJumpedBankSwitchVectors() {
        val bytes = stackFixture()
        bytes.word(STACK + 25, BANK_SWITCH)
        bytes.put(BANK_SWITCH, "e0 $STATE_HEX ea 00 20 c9")
        bytes.put(0x08, "c3 00 03")
        assertEquals(GbCompiledFarCopyAuthority(STATE, 255), resolve(bytes))
    }

    @Test fun derivesRelocatedWrappersAndSavedBankRegisters() {
        val bytes = stackFixture()
        bytes.word(COPY + 1, STACK + 0x200)
        bytes.copyInto(bytes, STACK + 0x200, STACK, STACK + 27)
        bytes[STACK + 0x200 + 9] = 0x9A.toByte()
        bytes[0x09] = 0x9A.toByte()
        assertEquals(GbCompiledFarCopyAuthority(0x9A, 255), resolve(bytes))
    }

    @Test fun rejectsIncompleteCopyAndStackBodies() {
        val original = stackFixture()
        val instructionOffsets = listOf(COPY, COPY + 3, COPY + 10, STACK, STACK + 5, STACK + 10,
            STACK + 15, STACK + 19, STACK + 21, STACK + 22, STACK + 24, RESTORE, RESTORE + 4,
            RESTORE + 6, RESTORE + 8, RESTORE + 9)
        instructionOffsets.forEach { offset ->
            val altered = original.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() }
            assertNull("changed stack/copy instruction at $offset", resolve(altered))
        }
    }

    @Test fun rejectsWrongBankRestorationAndStoreTargets() {
        val original = stackFixture()
        assertNull(resolve(original.copyOf().also { it[STACK + 9] = 0x9A.toByte() }))
        assertNull(resolve(original.copyOf().also { it[RESTORE + 5] = 0xD7.toByte() }))
        assertNull(resolve(original.copyOf().also { it.word(0x0B, 0xC000) }))
        assertNull(resolve(original.copyOf().also { it[STACK + 14] = 0x40 }))
        assertNull(resolve(original.copyOf().also { it[STACK + 9] = 0x70 }))
    }

    @Test fun rejectsIncompleteHramDispatcherAndRestoration() {
        val original = hramFixture()
        val offsets = listOf(STACK, STACK + 2, STACK + 4, STACK + 8, STACK + 11,
            STACK + 13, DISPATCH, DISPATCH + 2, DISPATCH + 4, DISPATCH + 5, DISPATCH + 8,
            RETURN_CALL, RETURN_CALL + 3, RETURN_CALL + 6, RETURN_CALL + 7,
            RETURN_CALL + 11, RETURN_CALL + 12, RETURN_CALL + 15,
            RETRIEVE, RETRIEVE + 4, RETRIEVE + 6, RETRIEVE + 9)
        offsets.forEach { offset ->
            val altered = original.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() }
            assertNull("changed HRAM dispatch instruction at $offset", resolve(altered))
        }
    }

    @Test fun rejectsHramRegisterAliasingAndMismatchedSavedHl() {
        val original = hramFixture()
        assertNull(resolve(original.copyOf().also { it[STACK + 1] = STATE.toByte() }))
        assertNull(resolve(original.copyOf().also { it[STACK + 7] = (SAVED_L + 1).toByte() }))
        assertNull(resolve(original.copyOf().also { it.word(RETRIEVE + 2, 0xFF00 + SAVED_L + 1) }))
        assertNull(resolve(original.copyOf().also { it[RETURN_CALL + 14] = TEMP.toByte() }))
    }

    @Test fun rejectsOutOfHomeLinkedTargetsAndTruncatedRom() {
        listOf(stackFixture(), hramFixture()).forEach { original ->
            assertNull(resolve(original.copyOf().also { it.word(COPY + 1, 0x4000) }))
            assertNull(GbCompiledFarCopy.resolve(RomImage(original.copyOf(COPY + 2)), COPY))
            assertNull(GbCompiledFarCopy.resolve(RomImage(original), -1))
            assertNull(GbCompiledFarCopy.resolve(RomImage(original), 0x4000))
        }
    }

    private fun resolve(bytes: ByteArray): GbCompiledFarCopyAuthority? =
        GbCompiledFarCopy.resolve(RomImage(bytes), COPY)

    private fun stackFixture(): ByteArray = ByteArray(0x4000).also { bytes ->
        bytes.put(COPY, "cd 80 01 04 0c 18 03 2a 12 13 0d 20 fa 05 20 f7 c9")
        bytes.put(STACK, "e8 fd d5 e5 f8 08 56 5f f0 $STATE_HEX 32 7b 5e 36 02 2b 36 80 2b 72 2b 73 e1 d1 c3 08 00")
        bytes.put(RESTORE, "f5 e5 f8 04 7e cf e1 f1 33 c9")
        bytes.put(0x08, "e0 $STATE_HEX ea 00 20 c9")
    }

    private fun hramFixture(): ByteArray = ByteArray(0x4000).also { bytes ->
        bytes.put(COPY, "cd 80 01 04 0c 18 03 2a 12 13 0d 20 fa 05 20 f7 c9")
        bytes.put(STACK, "e0 88 7c e0 91 7d e0 90 e1 f0 $STATE_HEX f5 18 12")
        bytes.put(DISPATCH, "f0 88 e6 7f d7 cd 40 02")
        bytes.put(RETURN_CALL, "e0 89 f5 e5 f8 02 2a 2c 77 e1 f1 f1 d7 f0 89 c9")
        bytes.put(RETRIEVE, "e5 21 90 ff 2a 66 6f f0 89 c9")
        bytes.put(0x10, "e0 $STATE_HEX ea 00 20 c9")
    }

    private fun ByteArray.put(offset: Int, value: String) {
        value.split(' ').map { it.toInt(16).toByte() }.toByteArray().copyInto(this, offset)
    }

    private fun ByteArray.word(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private companion object {
        const val COPY = 0x100
        const val STACK = 0x180
        const val DISPATCH = 0x1A0
        const val RETURN_CALL = DISPATCH + 8
        const val RETRIEVE = 0x240
        const val RESTORE = 0x280
        const val BANK_SWITCH = 0x300
        const val STATE = 0x87
        const val STATE_HEX = "87"
        const val TEMP = 0x88
        const val SAVED_L = 0x90
    }
}
