package com.enrpau.dualscreendex.parser.sprite

import com.enrpau.dualscreendex.parser.io.RomImage

object GbaRomCompression {
    fun decodeAt(rom: RomImage, offset: Int): ByteArray = if (rom.u8(offset) == GBA_LZ77_HEADER) {
        GbaLz77Decoder.decode(rom.slice(offset, compressedLength(rom, offset)))
    } else {
        val available = minOf(rom.size - offset, MAX_SMOL_ENCODED_BYTES)
        require(available >= 8) { "truncated SMOL stream" }
        val candidate = rom.slice(offset, available)
        GbaSmolDecoder.decode(candidate.copyOf(GbaSmolDecoder.encodedLength(candidate)))
    }

    private fun compressedLength(rom: RomImage, offset: Int): Int {
        require(rom.u8(offset) == GBA_LZ77_HEADER)
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

    private const val GBA_LZ77_HEADER = 0x10
    private const val MAX_SMOL_ENCODED_BYTES = 73_756
}
