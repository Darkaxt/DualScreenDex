package com.darkaxt.dualdex.save.gen1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen1ChecksumsTest {
    @Test
    fun complementsTheUnsignedByteSum() {
        val bytes = byteArrayOf(0xFE.toByte(), 0x03, 0x04)

        assertEquals(0xFA, Gen1Checksums.complementedByteSum(bytes))
    }

    @Test
    fun validatesAStoredChecksum() {
        val bytes = byteArrayOf(1, 2, 3, 0xF9.toByte())

        assertTrue(Gen1Checksums.matches(bytes, 0, 3, 3))
    }
}
