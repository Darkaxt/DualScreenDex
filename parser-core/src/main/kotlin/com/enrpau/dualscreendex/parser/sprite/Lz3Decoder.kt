package com.enrpau.dualscreendex.parser.sprite

object Lz3Decoder {
    fun decode(source: ByteArray): ByteArray {
        val output = ArrayList<Byte>()
        var cursor = 0
        while (true) {
            require(cursor < source.size) { "LZ3 stream has no terminator" }
            val control = source[cursor++].toInt() and 0xFF
            if (control == 0xFF) return output.toByteArray()
            var command = control ushr 5
            val length = if (command == 7) {
                command = (control ushr 2) and 0x07
                require(command != 7 && cursor < source.size) { "invalid long LZ3 command" }
                (((control and 0x03) shl 8) or (source[cursor++].toInt() and 0xFF)) + 1
            } else {
                (control and 0x1F) + 1
            }
            when (command) {
                0 -> repeat(length) {
                    require(cursor < source.size) { "truncated LZ3 literal" }
                    output += source[cursor++]
                }
                1 -> {
                    require(cursor < source.size) { "truncated LZ3 iterate command" }
                    val value = source[cursor++]
                    repeat(length) { output += value }
                }
                2 -> {
                    require(cursor + 1 < source.size) { "truncated LZ3 alternate command" }
                    val first = source[cursor++]
                    val second = source[cursor++]
                    repeat(length) { output += if (it and 1 == 0) first else second }
                }
                3 -> repeat(length) { output += 0 }
                in 4..6 -> {
                    val sourceIndex = readSourceIndex(source, output.size, cursor)
                    cursor = sourceIndex.nextCursor
                    repeat(length) { index ->
                        val position = if (command == 6) sourceIndex.offset - index else sourceIndex.offset + index
                        require(position in output.indices) { "invalid LZ3 copy source" }
                        val value = output[position]
                        output += if (command == 5) reverseBits(value) else value
                    }
                }
                else -> error("unsupported LZ3 command $command")
            }
        }
    }

    private fun readSourceIndex(source: ByteArray, outputSize: Int, cursor: Int): SourceIndex {
        require(cursor < source.size) { "truncated LZ3 copy source" }
        val first = source[cursor].toInt() and 0xFF
        return if (first and 0x80 != 0) {
            SourceIndex(outputSize - ((first and 0x7F) + 1), cursor + 1)
        } else {
            require(cursor + 1 < source.size) { "truncated LZ3 absolute source" }
            SourceIndex(((first and 0x7F) shl 8) or (source[cursor + 1].toInt() and 0xFF), cursor + 2)
        }
    }

    private fun reverseBits(value: Byte): Byte = Integer.reverse(value.toInt() and 0xFF).ushr(24).toByte()

    private data class SourceIndex(val offset: Int, val nextCursor: Int)
}
