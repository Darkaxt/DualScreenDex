package com.enrpau.dualscreendex.parser.sprite

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class GbaLz77DecoderTest {
    @Test
    fun decodesLiteralAndBackReferencePackets() {
        val compressed = byteArrayOf(
            0x10, 6, 0, 0,
            0x20,
            'A'.code.toByte(), 'B'.code.toByte(),
            0x10, 0x01,
        )

        assertArrayEquals("ABABAB".toByteArray(), GbaLz77Decoder.decode(compressed))
    }
}
