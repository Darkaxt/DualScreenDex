package com.darkaxt.dualdex.save.gen3

import org.junit.Assert.assertEquals
import org.junit.Test

class Gen3ChecksumsTest {
    @Test
    fun foldsTheUnsignedSectorWordSum() {
        val data = ByteArray(Gen3Checksums.SECTOR_DATA_SIZE)
        data.putU32le(0, 0xFFFF_FFFFL)
        data.putU32le(4, 2)

        assertEquals(1, Gen3Checksums.sector(data))
    }

    @Test
    fun sumsPokemonWordsModuloSixteenBits() {
        val data = ByteArray(48)
        data.putU16le(0, 0xFFFF)
        data.putU16le(2, 2)

        assertEquals(1, Gen3Checksums.pokemon(data))
    }

    private fun ByteArray.putU16le(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private fun ByteArray.putU32le(offset: Int, value: Long) {
        repeat(4) { index -> this[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
