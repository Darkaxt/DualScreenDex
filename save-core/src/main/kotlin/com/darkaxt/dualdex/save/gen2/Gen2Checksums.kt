package com.darkaxt.dualdex.save.gen2

object Gen2Checksums {
    fun byteSum16(bytes: ByteArray, start: Int = 0, endExclusive: Int = bytes.size): Int {
        require(start >= 0 && endExclusive in start..bytes.size)
        var sum = 0
        for (offset in start until endExclusive) sum = (sum + (bytes[offset].toInt() and 0xFF)) and 0xFFFF
        return sum
    }

    fun byteSum16(parts: Iterable<ByteArray>): Int =
        parts.fold(0) { sum, bytes -> (sum + byteSum16(bytes)) and 0xFFFF }

    fun matches(bytes: ByteArray, start: Int, endExclusive: Int, checksumOffset: Int): Boolean =
        checksumOffset + 1 < bytes.size && byteSum16(bytes, start, endExclusive) == bytes.u16le(checksumOffset)
}

internal fun ByteArray.u16le(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
