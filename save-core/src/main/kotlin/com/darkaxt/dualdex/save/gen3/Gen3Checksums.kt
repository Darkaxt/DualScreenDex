package com.darkaxt.dualdex.save.gen3

object Gen3Checksums {
    const val SECTOR_SIZE = 0x1000
    const val SECTOR_DATA_SIZE = 0xFF4
    const val SECTOR_SIGNATURE = 0x08012025L

    fun sector(data: ByteArray, offset: Int = 0, size: Int = SECTOR_DATA_SIZE): Int {
        require(offset >= 0 && size >= 0 && size % 4 == 0 && offset + size <= data.size)
        var checksum = 0L
        var cursor = offset
        while (cursor < offset + size) {
            checksum = (checksum + data.u32le(cursor)) and 0xFFFF_FFFFL
            cursor += 4
        }
        return ((checksum + (checksum ushr 16)) and 0xFFFF).toInt()
    }

    fun pokemon(decrypted: ByteArray): Int {
        require(decrypted.size == 48)
        var checksum = 0
        for (offset in decrypted.indices step 2) checksum = (checksum + decrypted.u16le(offset)) and 0xFFFF
        return checksum
    }
}

internal fun ByteArray.u16le(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

internal fun ByteArray.u32le(offset: Int): Long =
    u16le(offset).toLong() or (u16le(offset + 2).toLong() shl 16)
