package com.enrpau.dualscreendex.server

import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PngEncoderTest {
    @Test
    fun writesDeterministicRgbaPng() {
        val sprite = RgbaSprite(2, 1, intArrayOf(0xFFFF0000.toInt(), 0x00000000))
        val first = PngEncoder.encode(sprite)
        val second = PngEncoder.encode(sprite)

        assertArrayEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10), first.copyOf(8))
        assertArrayEquals(first, second)
        assertEquals(2, readInt(first, 16))
        assertEquals(1, readInt(first, 20))
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF shl 24) or
            (bytes[offset + 1].toInt() and 0xFF shl 16) or
            (bytes[offset + 2].toInt() and 0xFF shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}
