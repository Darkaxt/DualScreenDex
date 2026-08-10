package com.enrpau.dualscreendex.parser.sprite

import com.enrpau.dualscreendex.parser.io.RomImage

internal object GbaRomCompression {
    fun decodeAt(rom: RomImage, offset: Int): ByteArray =
        GbaLz77Decoder.decode(rom.slice(offset, compressedLength(rom, offset)))

    private fun compressedLength(rom: RomImage, offset: Int): Int {
        require(rom.u8(offset) == 0x10)
        val declared = rom.u24le(offset + 1)
        var input = offset + 4
        var output = 0
        while (output < declared) {
            val flags = rom.u8(input++)
            for (bit in 7 downTo 0) {
                if (output == declared) break
                if (flags and (1 shl bit) == 0) {
                    input++
                    output++
                } else {
                    val first = rom.u8(input)
                    input += 2
                    output += (first ushr 4) + 3
                }
            }
        }
        require(output >= declared)
        return input - offset
    }
}
