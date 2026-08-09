package com.enrpau.dualscreendex.parser.sprite

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class SpriteMaterializerTest {
    @Test
    fun decodesGbaPokemonSpriteWithItsRomPalette() {
        val bytes = ByteArray(512)
        val spriteRaw = ByteArray(32)
        spriteRaw[0] = 1
        val spriteCompressed = gbaLiteral(spriteRaw)
        val paletteRaw = ByteArray(32)
        paletteRaw[2] = 0x1F
        val paletteCompressed = gbaLiteral(paletteRaw)
        putGbaPointer(bytes, 0, 128)
        putU16(bytes, 4, 32)
        putGbaPointer(bytes, 16, 256)
        spriteCompressed.copyInto(bytes, 128)
        paletteCompressed.copyInto(bytes, 256)
        val layout = ResolvedRomLayout(
            family = EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = 1,
            moveCount = 0,
            tables = ProfileTables(sprites = TableLayout(0, 1, 8)),
        )

        val sprite = SpriteMaterializer.pokemon(RomImage(bytes), layout, gbaPaletteTableOffset = 16)
            .getValue(0)

        assertEquals(8, sprite.width)
        assertEquals(8, sprite.height)
        assertEquals(0xFFFF0000.toInt(), sprite.argb[0])
        assertEquals(0, sprite.argb[1])
    }

    @Test
    fun decodesGenTwoFrontSpriteFromBankedLz3Pointer() {
        val bytes = ByteArray(0x8000)
        bytes[0] = 1
        putU16(bytes, 1, 0x4020)
        val raw = ByteArray(16)
        raw[0] = 0x80.toByte()
        val compressed = byteArrayOf(0x0F) + raw + byteArrayOf(0xFF.toByte())
        compressed.copyInto(bytes, 0x4020)
        val layout = ResolvedRomLayout(
            family = EngineFamily.CRYSTAL,
            generation = 2,
            platform = Platform.GBC,
            speciesCount = 1,
            moveCount = 0,
            tables = ProfileTables(sprites = TableLayout(0, 1, 6)),
        )

        val sprite = SpriteMaterializer.pokemon(RomImage(bytes), layout).getValue(1)

        assertEquals(8, sprite.width)
        assertEquals(true, sprite.argb[0] != 0)
        assertEquals(0, sprite.argb[1])
    }

    @Test
    fun decodesGenOneFrontSpriteFromBaseStatsPointer() {
        val bytes = ByteArray(0x8000)
        bytes[10] = 0x11
        putU16(bytes, 11, 0x4020)
        val bitString = "001111000001001111000001"
        bytes[0x4020] = 0x11
        bitString.chunked(8).forEachIndexed { index, bits ->
            bytes[0x4021 + index] = bits.toInt(2).toByte()
        }
        val layout = ResolvedRomLayout(
            family = EngineFamily.RED_BLUE,
            generation = 1,
            platform = Platform.GB,
            speciesCount = 1,
            moveCount = 0,
            tables = ProfileTables(sprites = TableLayout(0, 1, 28, banks = listOf(1))),
        )

        val sprite = SpriteMaterializer.pokemon(RomImage(bytes), layout).getValue(1)

        assertEquals(8, sprite.width)
        assertEquals(true, sprite.argb.all { it == 0 })
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
