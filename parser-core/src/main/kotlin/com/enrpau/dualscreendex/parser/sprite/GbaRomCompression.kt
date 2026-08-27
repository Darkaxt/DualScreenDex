package com.enrpau.dualscreendex.parser.sprite

import com.enrpau.dualscreendex.parser.io.RomImage

internal enum class GbaDecodeContract(
    val maximumDecodedBytes: Int,
) {
    SPECIES_SPRITE(64 * 1024),
    PALETTE(4 * 1024),
    BALL_SPRITE(16 * 1024),
    TRAINER_SPRITE(64 * 1024),
    LOCAL_MAP(4 * 1024 * 1024),
    WORLD_MAP(4 * 1024 * 1024),
}

internal object GbaRomCompression {
    fun decodedSizeAtOrNull(rom: RomImage, offset: Int): Int? {
        if (offset < 0 || offset.toLong() + 4 > rom.size.toLong()) return null
        if (rom.u8(offset) == GBA_LZ77_HEADER) return rom.u24le(offset + 1).takeIf { it > 0 }
        if (offset.toLong() + 8 > rom.size.toLong()) return null
        return runCatching {
            val header = rom.slice(offset, 8)
            val encodedLength = GbaSmolDecoder.encodedLengthFromHeader(header)
            require(encodedLength <= MAX_SMOL_ENCODED_BYTES)
            require(offset.toLong() + encodedLength <= rom.size.toLong())
            GbaSmolDecoder.decodedSize(header)
        }.getOrNull()
    }

    fun decodeAt(
        rom: RomImage,
        offset: Int,
        contract: GbaDecodeContract,
    ): ByteArray = if (rom.u8(offset) == GBA_LZ77_HEADER) {
        val decodedSize = rom.u24le(offset + 1)
        require(decodedSize in 1..contract.maximumDecodedBytes) {
            "GBA LZ77 decoded-size limit exceeded for ${contract.name}"
        }
        GbaLz77Decoder.decode(
            rom.slice(offset, compressedLength(rom, offset)),
            contract.maximumDecodedBytes,
        )
    } else {
        require(offset >= 0 && offset.toLong() + 8 <= rom.size.toLong()) {
            "truncated SMOL stream"
        }
        val header = rom.slice(offset, 8)
        val encodedLength = GbaSmolDecoder.encodedLengthFromHeader(header)
        require(encodedLength <= MAX_SMOL_ENCODED_BYTES) { "SMOL stream exceeds encoded-size bound" }
        require(GbaSmolDecoder.decodedSize(header) <= contract.maximumDecodedBytes) {
            "SMOL decoded-size limit exceeded for ${contract.name}"
        }
        require(offset.toLong() + encodedLength <= rom.size.toLong()) { "truncated SMOL stream" }
        GbaSmolDecoder.decode(rom.slice(offset, encodedLength))
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
