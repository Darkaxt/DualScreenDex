package com.enrpau.dualscreendex.parser.sprite

import com.enrpau.dualscreendex.parser.io.RomImage
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GbaSmolDecoderTest {
    @Test
    fun decodesBaseOnlyReferenceStream() {
        val compressed = Base64.getDecoder().decode("ASAEAAAAGAAAAP8HAQAAAA==")

        assertArrayEquals(ByteArray(2048), GbaSmolDecoder.decode(compressed))
    }

    @Test
    fun decodesSymbolEncodedReferenceStream() {
        val compressed = Base64.getDecoder().decode(
            "AgFMALMBYADlMAwDw0AUAAAAAADm4BbEQc3RDuKg5uAWxEHNwU4cB6cJAAAADAQEAAMFFQQZAAE=",
        )
        val expected = ByteArray(64) { index ->
            ((5 * 73 + index * 37 + (index / 13) * 19) % 9).toByte()
        }

        assertArrayEquals(expected, GbaSmolDecoder.decode(compressed))
    }

    @Test
    fun decodesDeltaSymbolEncodedReferenceStream() {
        val compressed = reference(
            "AwGAAP8BEAAXRQUAAAAAAAAAAACY9eVGNvLXteiTNJ8lZEuJCKl0wcLxK4mKSGcAACAAAA==",
        )
        val expected = reference(
            "Q1SGiIi5u/3//yFERGSX2w9CZWaoqtv9ETFkdoe63O7/H0NliJm6zO7/EDJTl5m63Q8AACBkmKrc7Q8RU3eZyg==",
        )

        assertArrayEquals(expected, GbaSmolDecoder.decode(compressed))
    }

    @Test
    fun decodesInstructionEncodedReferenceStream() {
        val compressed = reference(
            "BASAAAcCgAHakAQDRhEAAgEAAAdIAA8A3AHwiLn/YGo29y/XsGo1rFoNq1bDqtWwanUALgUGBwgAAQIDBAUGBwgBCAABAgMEBQYHCAACAAECAwQFCAABAwUGAAECBAABAwUBAgQGAgMFBwMEBggEBQcABQY=",
        )

        assertArrayEquals(formulaFixture(256, 5), GbaSmolDecoder.decode(compressed))
    }

    @Test
    fun decodesBothEncodedReferenceStream() {
        val compressed = reference(
            "BQJYAM8C8ABfUggFhAAABAAAAAJlMQwCxDAQAAAAAADdmoeVd/Eidv95AKr/XGBFsCKAdnDmgxz0walnPshBH5w9HufMS3r4MPiSLg==",
        )

        assertArrayEquals(formulaFixture(128, 5), GbaSmolDecoder.decode(compressed))
    }

    @Test
    fun decodesBothEncodedDeltaSymbolReferenceStream() {
        val compressed = reference(
            "BgrsAYcSOAPiIAQBQHEEBYAAAANJURDExRAAQENREASmmqaappqmmqaappqmmqaappqmmqaappqmmqaaphpETieQ68Q6Sj2BXCepBlDqSeR0YjuMOZ0km5KeQK4TXE3GMJNkRijHGGaSzAiGmRHKMYaZJDNCOcYwE2CYSTIjlGMMM0lmhHIDQTnGMJNkRijHGGaSbGaSzAjlGMNMkhmhHPOVxxhmkswI5RjDTJL5VJIZoRxjmEkyI5RjjGKMYSbJjFCOMcwkmREMMyOUYwwzSWaEcoxhJsAwk2RGKMcYZpLMCOUGgqQYw0ySGSEpxjCTZDOTZGZOxRhmkszMqZgvVYxhJirJnIoxzEQl2cwkmVMxhpmZJHMqxijGGAwzSeZUjMEwk2Q+lWTOxVBMTbBcXuaGmXN5mbvlA8NMkjl3y4+0W17mEgAAAA==",
        )
        val expected = ByteArray(640) { index -> (((index / 32) * 17 + index % 7) % 256).toByte() }

        assertArrayEquals(expected, GbaSmolDecoder.decode(compressed))
    }

    @Test
    fun decodesDeltaTilemapReferenceStream() {
        val compressed = byteArrayOf(
            0x88.toByte(), 0x00, 0x10, 0x00,
            0x02, 0x00, 0x00, 0x00,
            0x01, 0x00, 0x01, 0x00,
            0x01, 0x00, 0x01, 0x00,
            0x00, 0x04, 0x00, 0x00,
        )

        assertEquals(8, GbaSmolDecoder.decodedSize(compressed))
        assertEquals(compressed.size, GbaSmolDecoder.encodedLength(compressed))
        assertArrayEquals(
            byteArrayOf(1, 0, 2, 0, 3, 0, 4, 0),
            GbaSmolDecoder.decode(compressed),
        )
        assertThrows(IllegalArgumentException::class.java) {
            GbaSmolDecoder.decode(compressed.copyOf(17))
        }
    }

    @Test
    fun rejectsUnsupportedModesMalformedFrequenciesAndInvalidBackReferences() {
        val unsupported = reference("ASAEAAAAGAAAAP8HAQAAAA==").also { it[0] = 7 }
        assertThrows(IllegalArgumentException::class.java) { GbaSmolDecoder.decode(unsupported) }

        val malformedFrequencies = reference(
            "AgFMALMBYADlMAwDw0AUAAAAAADm4BbEQc3RDuKg5uAWxEHNwU4cB6cJAAAADAQEAAMFFQQZAAE=",
        ).also { bytes -> repeat(12) { bytes[8 + it] = 0 } }
        assertThrows(IllegalArgumentException::class.java) { GbaSmolDecoder.decode(malformedFrequencies) }

        val invalidBackReference = reference("ASAEAAAAGAAAAP8HAQAAAA==").also { it[12] = 0 }
        assertThrows(IllegalArgumentException::class.java) { GbaSmolDecoder.decode(invalidBackReference) }
    }

    @Test
    fun rejectsTruncatedStreamsBeforeReadingPayload() {
        val truncated = reference("BQJYAM8C8ABfUggFhAAABAAAAAJlMQwCxDAQAAAAAADdmoeVd/Eidv95AKr/XGBFsCKAdnDmgxz0walnPshBH5w9HufMS3r4MPiSLg==")
            .copyOf(40)

        assertThrows(IllegalArgumentException::class.java) { GbaSmolDecoder.decode(truncated) }
    }

    @Test
    fun romCompressionDispatchesSmolWithoutChangingGbaLz77() {
        val smol = reference("ASAIAAAAKAABAAAAAAH+BwEAAAA=")
        val lz = byteArrayOf(0x10, 3, 0, 0, 0, 1, 2, 3)
        val bytes = ByteArray(128)
        smol.copyInto(bytes, 16)
        lz.copyInto(bytes, 64)

        val rom = RomImage(bytes)
        assertArrayEquals(ByteArray(2048).also { it[0] = 1 }, GbaRomCompression.decodeAt(rom, 16))
        assertArrayEquals(byteArrayOf(1, 2, 3), GbaRomCompression.decodeAt(rom, 64))
    }

    private fun formulaFixture(size: Int, variant: Int): ByteArray = ByteArray(size) { index ->
        ((variant * 73 + index * 37 + (index / 13) * 19) % (4 + variant)).toByte()
    }

    private fun reference(value: String): ByteArray = Base64.getDecoder().decode(value)
}
