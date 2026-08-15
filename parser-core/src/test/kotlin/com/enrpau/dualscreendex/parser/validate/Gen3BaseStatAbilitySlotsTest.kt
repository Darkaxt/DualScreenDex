package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3BaseStatAbilitySlotsTest {
    @Test
    fun rejectsUnsupportedRecordWidths() {
        val bytes = ByteArray(32)
        bytes[22] = 7
        bytes[23] = 9

        listOf(24, 26, 30, 36).forEach { recordSize ->
            assertTrue("recordSize=$recordSize", Gen3BaseStatAbilitySlots.read(RomImage(bytes), 0, recordSize).isEmpty())
        }
    }

    @Test
    fun rejectsNegativeOutOfRangeAndTruncatedRecordsWithoutThrowing() {
        val rom = RomImage(ByteArray(27))

        assertTrue(Gen3BaseStatAbilitySlots.read(rom, -1, 28).isEmpty())
        assertTrue(Gen3BaseStatAbilitySlots.read(rom, 28, 28).isEmpty())
        assertTrue(Gen3BaseStatAbilitySlots.read(rom, 0, 28).isEmpty())
    }

    @Test
    fun preservesSupportedRecordsWhileOmittingZeroesAndDuplicates() {
        val legacy = ByteArray(28)
        legacy[22] = 7
        legacy[23] = 7
        val battleEngine = ByteArray(32)
        writeU16(battleEngine, 22, 9)
        writeU16(battleEngine, 24, 0)
        writeU16(battleEngine, 26, 145)
        val modernEmerald = ByteArray(40)
        modernEmerald[22] = 37
        modernEmerald[23] = 74

        assertEquals(listOf(7), Gen3BaseStatAbilitySlots.read(RomImage(legacy), 0, 28))
        assertEquals(listOf(9, 145), Gen3BaseStatAbilitySlots.read(RomImage(battleEngine), 0, 32))
        assertEquals(listOf(37, 74), Gen3BaseStatAbilitySlots.read(RomImage(modernEmerald), 0, 40))
    }

    private fun writeU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }
}
