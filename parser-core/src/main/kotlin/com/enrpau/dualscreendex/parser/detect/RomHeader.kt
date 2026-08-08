package com.enrpau.dualscreendex.parser.detect

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader

object RomHeaderReader {
    fun read(rom: RomImage): RomHeader {
        if (rom.size >= 0xC0 && hasGbaLogoArea(rom)) {
            val title = ascii(rom.slice(0xA0, 12))
            val gameCode = ascii(rom.slice(0xAC, 4))
            if (gameCode.length == 4 && gameCode.all { it.isLetterOrDigit() }) {
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

    private fun hasGbaLogoArea(rom: RomImage): Boolean =
        rom.size >= 0xC0 && rom.slice(0xB2, 2).all { (it.toInt() and 0xff) in 0..0xff }

    private fun ascii(bytes: ByteArray): String = bytes
        .takeWhile { it.toInt() != 0 }
        .map { (it.toInt() and 0xff).toChar() }
        .joinToString("")
        .trim()
        .filter { it.code in 0x20..0x7e }
}
