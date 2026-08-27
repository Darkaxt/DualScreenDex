package com.enrpau.dualscreendex.parser.sprite

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

        assertArrayEquals("ABABAB".toByteArray(), GbaLz77Decoder.decode(compressed, 1_024))
    }

    @Test
    fun truncatesALegalFinalBackReferenceAtTheDeclaredOutputSize() {
        val compressed = byteArrayOf(
            0x10, 5, 0, 0,
            0x20,
            'A'.code.toByte(), 'B'.code.toByte(),
            0x10, 0x01,
        )

        assertArrayEquals("ABABA".toByteArray(), GbaLz77Decoder.decode(compressed, 1_024))
    }

    @Test
    fun rejectsDeclaredOutputBeyondTheContractBeforeReadingPackets() {
        val headerOnly = byteArrayOf(0x10, 0, 0, 1)

        val failure = assertThrows(IllegalArgumentException::class.java) {
            GbaLz77Decoder.decode(headerOnly, maximumDecodedBytes = 4_096)
        }

        assertTrue(failure.message.orEmpty().contains("decoded-size limit"))
    }
}
