package com.enrpau.dualscreendex.parser.detect

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader

object RomHeaderReader {
    fun read(rom: RomImage): RomHeader {
        if (rom.size >= 0xC0 && hasGbaLogoArea(rom)) {
            val title = ascii(rom.slice(0xA0, 12))
            val gameCode = ascii(rom.slice(0xAC, 4))
            if (title.isNotBlank() && gameCode.length == 4 && gameCode.all { it.code in 0x20..0x7e }) {
                return RomHeader(Platform.GBA, title, gameCode, rom.u8(0xBC))
            }
        }

        if (rom.size >= 0x150) {
            val cgbFlag = rom.u8(0x143)
            val platform = if (cgbFlag == 0x80 || cgbFlag == 0xC0) Platform.GBC else Platform.GB
            val titleLength = if (platform == Platform.GBC) 15 else 16
            val title = ascii(rom.slice(0x134, titleLength))
            if (title.isNotBlank() || hasValidatedBlankGbTitle(rom)) {
                return RomHeader(platform, title, revision = rom.u8(0x14C), cgbFlag = cgbFlag)
            }
        }

        return RomHeader(Platform.UNKNOWN, "")
    }

    private fun hasGbaLogoArea(rom: RomImage): Boolean = rom.slice(0x04, GBA_LOGO_PREFIX.size)
        .contentEquals(GBA_LOGO_PREFIX)

    private fun hasValidatedBlankGbTitle(rom: RomImage): Boolean {
        if (!rom.slice(0x104, GB_LOGO.size).contentEquals(GB_LOGO)) return false
        val checksum = rom.slice(0x134, 0x19)
            .fold(0) { value, byte -> value - (byte.toInt() and 0xFF) - 1 }
            .and(0xFF)
        return checksum == rom.u8(0x14D)
    }

    private fun ascii(bytes: ByteArray): String = bytes
        .takeWhile { it.toInt() != 0 }
        .map { (it.toInt() and 0xff).toChar() }
        .joinToString("")
        .trim()
        .filter { it.code in 0x20..0x7e }

    private val GB_LOGO = byteArrayOf(
        0xCE.toByte(), 0xED.toByte(), 0x66, 0x66, 0xCC.toByte(), 0x0D, 0x00, 0x0B,
        0x03, 0x73, 0x00, 0x83.toByte(), 0x00, 0x0C, 0x00, 0x0D,
        0x00, 0x08, 0x11, 0x1F, 0x88.toByte(), 0x89.toByte(), 0x00, 0x0E,
        0xDC.toByte(), 0xCC.toByte(), 0x6E, 0xE6.toByte(), 0xDD.toByte(), 0xDD.toByte(), 0xD9.toByte(), 0x99.toByte(),
        0xBB.toByte(), 0xBB.toByte(), 0x67, 0x63, 0x6E, 0x0E, 0xEC.toByte(), 0xCC.toByte(),
        0xDD.toByte(), 0xDC.toByte(), 0x99.toByte(), 0x9F.toByte(), 0xBB.toByte(), 0xB9.toByte(), 0x33, 0x3E,
    )

    private val GBA_LOGO_PREFIX = byteArrayOf(
        0x24, 0xFF.toByte(), 0xAE.toByte(), 0x51, 0x69, 0x9A.toByte(), 0xA2.toByte(), 0x21,
        0x3D, 0x84.toByte(), 0x82.toByte(), 0x0A,
    )
}
