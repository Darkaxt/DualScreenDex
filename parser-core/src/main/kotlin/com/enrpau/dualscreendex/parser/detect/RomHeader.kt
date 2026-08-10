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
            if (title.isNotBlank()) {
                return RomHeader(platform, title, revision = rom.u8(0x14C), cgbFlag = cgbFlag)
            }
        }

        return RomHeader(Platform.UNKNOWN, "")
    }

    private fun hasGbaLogoArea(rom: RomImage): Boolean = rom.slice(0x04, GBA_LOGO_PREFIX.size)
        .contentEquals(GBA_LOGO_PREFIX)

    private fun ascii(bytes: ByteArray): String = bytes
        .takeWhile { it.toInt() != 0 }
        .map { (it.toInt() and 0xff).toChar() }
        .joinToString("")
        .trim()
        .filter { it.code in 0x20..0x7e }

    private val GBA_LOGO_PREFIX = byteArrayOf(
        0x24, 0xFF.toByte(), 0xAE.toByte(), 0x51, 0x69, 0x9A.toByte(), 0xA2.toByte(), 0x21,
        0x3D, 0x84.toByte(), 0x82.toByte(), 0x0A,
    )
}
