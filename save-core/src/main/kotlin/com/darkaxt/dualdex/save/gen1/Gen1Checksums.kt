package com.darkaxt.dualdex.save.gen1

object Gen1Checksums {
    fun complementedByteSum(bytes: ByteArray, start: Int = 0, endExclusive: Int = bytes.size): Int {
        require(start >= 0 && endExclusive in start..bytes.size)
        var sum = 0
        for (offset in start until endExclusive) sum = (sum + (bytes[offset].toInt() and 0xFF)) and 0xFF
        return sum xor 0xFF
    }

    fun matches(bytes: ByteArray, start: Int, endExclusive: Int, checksumOffset: Int): Boolean =
        checksumOffset in bytes.indices &&
            complementedByteSum(bytes, start, endExclusive) == bytes[checksumOffset].toInt() and 0xFF
}
