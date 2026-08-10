package com.enrpau.dualscreendex.parser.sprite

import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Test

class BallSpriteMaterializerTest {
    @Test
    fun locatesGbaBallTablesAndMapsAnimationOrderToStoredItemIds() {
        val bytes = ByteArray(2048)
        val gfxRaw = ByteArray(384)
        gfxRaw[0] = 1
        val paletteRaw = ByteArray(32)
        paletteRaw[2] = 0x1F
        val gfx = gbaLiteral(gfxRaw)
        val palette = gbaLiteral(paletteRaw)
        val sheetTable = 0
        val paletteTable = 128
        repeat(12) { index ->
            putGbaPointer(bytes, sheetTable + index * 8, 512)
            putU16(bytes, sheetTable + index * 8 + 4, 384)
            putU16(bytes, sheetTable + index * 8 + 6, 55000 + index)
            putGbaPointer(bytes, paletteTable + index * 8, 1024)
            putU16(bytes, paletteTable + index * 8 + 4, 55000 + index)
        }
        gfx.copyInto(bytes, 512)
        palette.copyInto(bytes, 1024)

        val balls = BallSpriteMaterializer.captureBalls(RomImage(bytes))

        assertEquals(12, balls.size)
        assertEquals(true, balls.getValue(4).generic)
        assertEquals(16, balls.getValue(4).sprite.value?.width)
        assertEquals(0xFFFF0000.toInt(), balls.getValue(4).sprite.value?.argb?.first())
        assertEquals(true, balls.containsKey(1))
        assertEquals(true, balls.containsKey(12))
    }

    @Test
    fun acceptsAValidatedBallPaletteTableContainingAnUncompressedPalette() {
        val bytes = ByteArray(4096)
        val gfxRaw = ByteArray(384).also { it[0] = 1 }
        val compressedGfx = gbaLiteral(gfxRaw)
        val compressedPalette = gbaLiteral(ByteArray(32).also { it[2] = 0x1F })
        repeat(12) { index ->
            putGbaPointer(bytes, index * 8, 1024)
            putU16(bytes, index * 8 + 4, 384)
            putU16(bytes, index * 8 + 6, 55000 + index)
            val paletteTarget = if (index == 9) 2048 else 1536
            putGbaPointer(bytes, 128 + index * 8, paletteTarget)
            putU16(bytes, 128 + index * 8 + 4, 55000 + index)
        }
        compressedGfx.copyInto(bytes, 1024)
        compressedPalette.copyInto(bytes, 1536)
        bytes[2048 + 2] = 0x1F

        val balls = BallSpriteMaterializer.captureBalls(RomImage(bytes))

        assertEquals(12, balls.size)
        assertEquals(12, balls.values.count { it.sprite.value != null })
    }

    private fun gbaLiteral(raw: ByteArray): ByteArray {
        val output = ArrayList<Byte>()
        output += 0x10
        output += raw.size.toByte()
        output += (raw.size ushr 8).toByte()
        output += (raw.size ushr 16).toByte()
        raw.asList().chunked(8).forEach { group ->
            output += 0
            output.addAll(group)
        }
        return output.toByteArray()
    }

    private fun putU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }

    private fun putGbaPointer(target: ByteArray, offset: Int, targetOffset: Int) {
        val value = 0x08000000 + targetOffset
        repeat(4) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
