package com.enrpau.dualscreendex.parser.sprite

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class Lz3DecoderTest {
    @Test
    fun decodesLiteralRepeatFlipAndReverseCommands() {
        val compressed = byteArrayOf(
            0x03, 1, 2, 3, 4,
            0x83.toByte(), 0x83.toByte(),
            0xA0.toByte(), 0x80.toByte(),
            0xC3.toByte(), 0, 3,
            0xFF.toByte(),
        )

        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4, 1, 2, 3, 4, 0x20, 4, 3, 2, 1),
            Lz3Decoder.decode(compressed),
        )
    }
}
