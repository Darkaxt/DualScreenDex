package com.enrpau.dualscreendex.parser.sprite

import org.junit.Assert.assertEquals
import org.junit.Test

class Gen1SpriteDecoderTest {
    @Test
    fun decodesTwoAllZeroPlanesAndDimensions() {
        val bitString = "001111000001001111000001"
        val payload = ByteArray(1 + bitString.length / 8)
        payload[0] = 0x11
        bitString.chunked(8).forEachIndexed { index, bits ->
            payload[index + 1] = bits.toInt(2).toByte()
        }

        val sprite = Gen1SpriteDecoder.decode(payload)

        assertEquals(8, sprite.width)
        assertEquals(8, sprite.height)
        assertEquals(true, sprite.indices.all { it.toInt() == 0 })
    }
}
