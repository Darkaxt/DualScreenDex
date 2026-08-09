package com.enrpau.dualscreendex.parser.sprite

object Gen1SpriteDecoder {
    fun decode(source: ByteArray): IndexedSprite {
        require(source.isNotEmpty()) { "empty Gen 1 sprite stream" }
        val tileWidth = source[0].toInt() ushr 4 and 0x0F
        val tileHeight = source[0].toInt() and 0x0F
        require(tileWidth > 0 && tileHeight > 0) { "invalid Gen 1 sprite dimensions" }
        val reader = BitReader(source, 8)
        val firstBuffer = reader.readBit()
        val buffers = arrayOf(ByteArray(tileWidth * tileHeight * 8), ByteArray(tileWidth * tileHeight * 8))
        buffers[firstBuffer] = decodePlane(reader, tileWidth, tileHeight)
        val mode = if (reader.readBit() == 0) 0 else 1 + reader.readBit()
        val secondBuffer = 1 - firstBuffer
        buffers[secondBuffer] = decodePlane(reader, tileWidth, tileHeight)
        when (mode) {
            0 -> {
                differentialDecode(buffers[0], tileWidth, tileHeight)
                differentialDecode(buffers[1], tileWidth, tileHeight)
            }
            1 -> {
                differentialDecode(buffers[firstBuffer], tileWidth, tileHeight)
                xorInto(buffers[firstBuffer], buffers[secondBuffer])
            }
            2 -> {
                differentialDecode(buffers[secondBuffer], tileWidth, tileHeight)
                differentialDecode(buffers[firstBuffer], tileWidth, tileHeight)
                xorInto(buffers[firstBuffer], buffers[secondBuffer])
            }
        }
        val width = tileWidth * 8
        val height = tileHeight * 8
        val pixels = ByteArray(width * height)
        repeat(height) { y ->
            repeat(width) { x ->
                val offset = (x / 8) * height + y
                val bit = 7 - x % 8
                val msb = buffers[0][offset].toInt() ushr bit and 1
                val lsb = buffers[1][offset].toInt() ushr bit and 1
                pixels[y * width + x] = (lsb or (msb shl 1)).toByte()
            }
        }
        return IndexedSprite(width, height, pixels)
    }

    private fun decodePlane(reader: BitReader, tileWidth: Int, tileHeight: Int): ByteArray {
        val height = tileHeight * 8
        val groups = tileWidth * tileHeight * 32
        val values = IntArray(groups)
        var position = 0
        var zeroMode = reader.readBit() == 0
        while (position < groups) {
            if (zeroMode) {
                var encodedWidth = 0
                while (reader.readBit() != 0) encodedWidth++
                val run = (1 shl (encodedWidth + 1)) - 1 + reader.readBits(encodedWidth + 1)
                require(position + run <= groups) { "Gen 1 zero run exceeds sprite plane" }
                position += run
            } else {
                while (position < groups) {
                    val value = reader.readBits(2)
                    if (value == 0) break
                    values[position++] = value
                }
            }
            zeroMode = !zeroMode
        }
        val output = ByteArray(tileWidth * height)
        position = 0
        repeat(tileWidth) { tileX ->
            for (pairOffset in 3 downTo 0) {
                repeat(height) { y ->
                    output[tileX * height + y] =
                        (output[tileX * height + y].toInt() or (values[position++] shl (pairOffset * 2))).toByte()
                }
            }
        }
        return output
    }

    private fun differentialDecode(buffer: ByteArray, tileWidth: Int, tileHeight: Int) {
        val height = tileHeight * 8
        repeat(height) { y ->
            var previous = 0
            repeat(tileWidth) { tileX ->
                val offset = tileX * height + y
                val encoded = buffer[offset].toInt() and 0xFF
                var decoded = 0
                for (bit in 7 downTo 0) {
                    val value = previous xor (encoded ushr bit and 1)
                    decoded = decoded or (value shl bit)
                    previous = value
                }
                buffer[offset] = decoded.toByte()
            }
        }
    }

    private fun xorInto(source: ByteArray, destination: ByteArray) {
        require(source.size == destination.size)
        source.indices.forEach { destination[it] = (destination[it].toInt() xor source[it].toInt()).toByte() }
    }

    private class BitReader(private val bytes: ByteArray, startBit: Int) {
        private var bit = startBit

        fun readBit(): Int {
            require(bit < bytes.size * 8) { "truncated Gen 1 sprite stream" }
            val value = bytes[bit / 8].toInt() ushr (7 - bit % 8) and 1
            bit++
            return value
        }

        fun readBits(count: Int): Int {
            var value = 0
            repeat(count) { value = value shl 1 or readBit() }
            return value
        }
    }
}
