package com.enrpau.dualscreendex.parser.detect

import com.enrpau.dualscreendex.parser.io.RomBoundsException
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

class RomHeaderTest {
    @Test
    fun detectsGbaHeader() {
        val bytes = ByteArray(0xC0)
        "POKEMON EMER".toByteArray().copyInto(bytes, 0xA0)
        "BPEE".toByteArray().copyInto(bytes, 0xAC)
        val header = RomHeaderReader.read(RomImage(bytes))
        assertEquals(Platform.GBA, header.platform)
        assertEquals("BPEE", header.gameCode)
    }

    @Test
    fun detectsGbcHeader() {
        val bytes = ByteArray(0x150)
        "POKEMON_GLDAAUE".toByteArray().copyInto(bytes, 0x134)
        bytes[0x143] = 0x80.toByte()
        val header = RomHeaderReader.read(RomImage(bytes))
        assertEquals(Platform.GBC, header.platform)
    }

    @Test(expected = RomBoundsException::class)
    fun boundedReadsRejectOverflow() {
        RomImage(ByteArray(16)).u32le(14)
    }

    @Test
    fun hashesAreStable() {
        val rom = RomImage("abc".toByteArray())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", rom.sha256)
        assertEquals("352441C2", rom.crc32)
    }
}
