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
        "01".toByteArray().copyInto(bytes, 0xB0)
        bytes[0xB3] = 0x02
        val header = RomHeaderReader.read(RomImage(bytes))
        assertEquals(Platform.GBA, header.platform)
        assertEquals("BPEE", header.gameCode)
        assertEquals("POKEMON EMER".toByteArray().map { it.toInt() and 0xFF }, header.rawTitleBytes)
        assertEquals("BPEE".toByteArray().map { it.toInt() and 0xFF }, header.rawGameCodeBytes)
        assertEquals("01", header.gbaMakerCode)
        assertEquals(2, header.gbaUnitCode)
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
        "POKEMON_GLD".toByteArray().copyInto(bytes, 0x134)
        "01PX".toByteArray().copyInto(bytes, 0x13F)
        bytes[0x143] = 0x80.toByte()
        bytes[0x14A] = 0x01
        val header = RomHeaderReader.read(RomImage(bytes))
        assertEquals(Platform.GBC, header.platform)
        assertEquals("POKEMON_GLD01PX", header.title)
        assertEquals("01PX", header.gbManufacturerCode)
        assertEquals(1, header.gbDestinationCode)
        assertEquals(16, header.rawTitleBytes.size)
        assertEquals(0x80, header.rawTitleBytes.last())
    }

    @Test
    fun detectsChecksumValidGbcHeaderWithoutATitle() {
        val bytes = ByteArray(0x150)
        putGbLogo(bytes)
        bytes[0x143] = 0x80.toByte()
        bytes[0x147] = 0x13
        updateGbHeaderChecksum(bytes)

        val header = RomHeaderReader.read(RomImage(bytes))

        assertEquals(Platform.GBC, header.platform)
        assertEquals("", header.title)
    }

    @Test
    fun blankTitleWithoutAValidGbHeaderRemainsUnknown() {
        val bytes = ByteArray(0x150)
        bytes[0x143] = 0x80.toByte()
        bytes[0x147] = 0x13
        updateGbHeaderChecksum(bytes)

        val header = RomHeaderReader.read(RomImage(bytes))

        assertEquals(Platform.UNKNOWN, header.platform)
    }

    @Test
    fun blankTitleWithAnInvalidChecksumRemainsUnknown() {
        val bytes = ByteArray(0x150)
        putGbLogo(bytes)
        bytes[0x143] = 0x80.toByte()
        bytes[0x147] = 0x13
        updateGbHeaderChecksum(bytes)
        bytes[0x14D] = (bytes[0x14D].toInt() xor 0xFF).toByte()

        val header = RomHeaderReader.read(RomImage(bytes))

        assertEquals(Platform.UNKNOWN, header.platform)
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

    private fun putGbLogo(bytes: ByteArray) {
        byteArrayOf(
            0xCE.toByte(), 0xED.toByte(), 0x66, 0x66, 0xCC.toByte(), 0x0D, 0x00, 0x0B,
            0x03, 0x73, 0x00, 0x83.toByte(), 0x00, 0x0C, 0x00, 0x0D,
            0x00, 0x08, 0x11, 0x1F, 0x88.toByte(), 0x89.toByte(), 0x00, 0x0E,
            0xDC.toByte(), 0xCC.toByte(), 0x6E, 0xE6.toByte(), 0xDD.toByte(), 0xDD.toByte(), 0xD9.toByte(), 0x99.toByte(),
            0xBB.toByte(), 0xBB.toByte(), 0x67, 0x63, 0x6E, 0x0E, 0xEC.toByte(), 0xCC.toByte(),
            0xDD.toByte(), 0xDC.toByte(), 0x99.toByte(), 0x9F.toByte(), 0xBB.toByte(), 0xB9.toByte(), 0x33, 0x3E,
        ).copyInto(bytes, 0x104)
    }

    private fun updateGbHeaderChecksum(bytes: ByteArray) {
        bytes[0x14D] = bytes.sliceArray(0x134 until 0x14D)
            .fold(0) { checksum, value -> checksum - (value.toInt() and 0xFF) - 1 }
            .toByte()
    }

    private fun putGbaLogoPrefix(bytes: ByteArray) {
        byteArrayOf(
            0x24, 0xFF.toByte(), 0xAE.toByte(), 0x51, 0x69, 0x9A.toByte(), 0xA2.toByte(), 0x21,
            0x3D, 0x84.toByte(), 0x82.toByte(), 0x0A,
        ).copyInto(bytes, 0x04)
    }
}
