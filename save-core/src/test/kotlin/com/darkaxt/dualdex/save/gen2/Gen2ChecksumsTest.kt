package com.darkaxt.dualdex.save.gen2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen2ChecksumsTest {
    @Test
    fun sumsUnsignedBytesIntoSixteenBits() {
        val bytes = byteArrayOf(0xFF.toByte(), 2, 3)

        assertEquals(260, Gen2Checksums.byteSum16(bytes))
    }

    @Test
    fun validatesTheStoredLittleEndianSum() {
        val bytes = byteArrayOf(0xFF.toByte(), 2, 1, 1)

        assertTrue(Gen2Checksums.matches(bytes, 0, 2, 2))
    }
}
