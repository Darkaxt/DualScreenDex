package com.enrpau.dualscreendex.parser.sprite

object GbaLz77Decoder {
    fun decode(
        source: ByteArray,
        maximumDecodedBytes: Int,
    ): ByteArray {
        require(maximumDecodedBytes > 0) { "GBA LZ77 decoded-size limit must be positive" }
        require(source.size >= 4 && source[0].toInt() and 0xFF == 0x10) { "invalid GBA LZ77 header" }
        val size = (source[1].toInt() and 0xFF) or
            ((source[2].toInt() and 0xFF) shl 8) or
            ((source[3].toInt() and 0xFF) shl 16)
        require(size > 0) { "GBA LZ77 output is empty" }
        require(size <= maximumDecodedBytes) { "GBA LZ77 decoded-size limit exceeded" }
        val output = ByteArray(size)
        var input = 4
        var written = 0
        while (written < size) {
            require(input < source.size) { "truncated GBA LZ77 flags" }
            val flags = source[input++].toInt() and 0xFF
            for (bit in 7 downTo 0) {
                if (written == size) break
                if (flags and (1 shl bit) == 0) {
                    require(input < source.size) { "truncated GBA LZ77 literal" }
                    output[written++] = source[input++]
                } else {
                    require(input + 1 < source.size) { "truncated GBA LZ77 reference" }
                    val first = source[input++].toInt() and 0xFF
                    val second = source[input++].toInt() and 0xFF
                    val length = (first ushr 4) + 3
                    val distance = ((first and 0x0F) shl 8 or second) + 1
                    require(distance <= written) { "invalid GBA LZ77 reference" }
                    repeat(minOf(length, size - written)) {
                        output[written] = output[written - distance]
                        written++
                    }
                }
            }
        }
        return output
    }
}
