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
        putGbaLogoPrefix(bytes)
        "POKEMON EMER".toByteArray().copyInto(bytes, 0xA0)
        "BPEE".toByteArray().copyInto(bytes, 0xAC)
        val header = RomHeaderReader.read(RomImage(bytes))
        assertEquals(Platform.GBA, header.platform)
        assertEquals("BPEE", header.gameCode)
    }

    @Test
    fun detectsGbaHackWithPrintableNonAlphanumericGameCode() {
        val bytes = ByteArray(0xC0)
        putGbaLogoPrefix(bytes)
        "ARCOIRIS".toByteArray().copyInto(bytes, 0xA0)
        "-S01".toByteArray().copyInto(bytes, 0xAC)

        val header = RomHeaderReader.read(RomImage(bytes))

        assertEquals(Platform.GBA, header.platform)
        assertEquals("ARCOIRIS", header.title)
        assertEquals("-S01", header.gameCode)
    }

    @Test
    fun arbitraryPrintableBytesDoNotMasqueradeAsGbaHeader() {
        val bytes = ByteArray(0xC0) { 0x41 }
        val header = RomHeaderReader.read(RomImage(bytes))
        assertEquals(Platform.UNKNOWN, header.platform)
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

    private fun putGbaLogoPrefix(bytes: ByteArray) {
        byteArrayOf(
            0x24, 0xFF.toByte(), 0xAE.toByte(), 0x51, 0x69, 0x9A.toByte(), 0xA2.toByte(), 0x21,
            0x3D, 0x84.toByte(), 0x82.toByte(), 0x0A,
        ).copyInto(bytes, 0x04)
    }
}
