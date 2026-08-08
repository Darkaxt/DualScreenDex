package com.enrpau.dualscreendex.parser.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomImageTest {
    @Test
    fun convertsFixedGbAddressToRomOffset() {
        val rom = RomImage(ByteArray(0x20000))

        assertEquals(0x2345, rom.gbBankAddress(bank = 0, address = 0x2345))
    }

    @Test
    fun convertsSwitchableGbAddressToRomOffset() {
        val rom = RomImage(ByteArray(0x20000))

        assertEquals(0x12345, rom.gbBankAddress(bank = 4, address = 0x6345))
    }

    @Test
    fun rejectsSwitchableAddressInBankZero() {
        val rom = RomImage(ByteArray(0x8000))

        assertNull(rom.gbBankAddress(bank = 0, address = 0x4000))
    }

    @Test
    fun rejectsAddressOutsideCartridgeWindow() {
        val rom = RomImage(ByteArray(0x8000))

        assertNull(rom.gbBankAddress(bank = 1, address = 0x8000))
    }

    @Test
    fun rejectsMappedOffsetOutsideRom() {
        val rom = RomImage(ByteArray(0x8000))

        assertNull(rom.gbBankAddress(bank = 8, address = 0x4000))
    }
}
