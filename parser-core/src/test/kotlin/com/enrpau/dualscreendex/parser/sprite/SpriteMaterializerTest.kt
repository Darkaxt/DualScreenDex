package com.enrpau.dualscreendex.parser.sprite

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.PokeemeraldExpansionMetadata
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class SpriteMaterializerTest {
    @Test
    fun decodesExpansionSmolSpriteAndKeepsRawPaletteWhoseFirstByteLooksCompressed() {
        val bytes = ByteArray(512)
        putGbaPointer(bytes, 0, 128)
        putGbaPointer(bytes, 8, 256)
        Base64.getDecoder().decode("ASAIAAAAKAABAAAAAAH+BwEAAAA=").copyInto(bytes, 128)
        bytes[256] = 0x10
        bytes[257] = 0x20
        bytes[258] = 0x1F
        val metadata = PokeemeraldExpansionMetadata(
            headerOffset = 0x204,
            versionMajor = 1,
            versionMinor = 15,
            versionPatch = 3,
            speciesRecordSize = 128,
            speciesNameOffset = 44,
            speciesNameWidth = 13,
            categoryOffset = 31,
            nationalDexOffset = 60,
            heightOffset = 62,
            weightOffset = 64,
            descriptionPointerOffset = 76,
            frontSpritePointerOffset = 88,
            normalPalettePointerOffset = 96,
            abilitiesOffset = 24,
            growthRateOffset = 21,
            levelUpPointerOffset = 100,
            teachablePointerOffset = 104,
            eggMovePointerOffset = 108,
            evolutionPointerOffset = 112,
            moveRecordSize = 64,
            abilityRecordSize = 28,
            abilityNameWidth = 20,
            abilityDescriptionPointerOffset = 20,
        )
        val layout = ResolvedRomLayout(
            family = EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = 1,
            moveCount = 1,
            tables = ProfileTables(sprites = TableLayout(0, 1, 4, stride = 128)),
            pokeemeraldExpansion = metadata,
        )

        val sprite = SpriteMaterializer.pokemon(RomImage(bytes), layout).getValue(0)

        assertEquals(64, sprite.width)
        assertEquals(64, sprite.height)
        assertEquals(0xFFFF0000.toInt(), sprite.argb[0])
    }

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
    fun genTwoUsesRomDimensionsAndOnlyTheFirstAnimationFrame() {
        val bytes = ByteArray(0x8000)
        bytes[0] = 1
        putU16(bytes, 1, 0x4020)
        bytes[0x40 + 17] = 0x11
        val raw = ByteArray(32)
        raw[0] = 0x80.toByte()
        val compressed = byteArrayOf(0x1F) + raw + byteArrayOf(0xFF.toByte())
        compressed.copyInto(bytes, 0x4020)
        val layout = ResolvedRomLayout(
            family = EngineFamily.CRYSTAL,
            generation = 2,
            platform = Platform.GBC,
            speciesCount = 1,
            moveCount = 0,
            tables = ProfileTables(
                baseStats = TableLayout(0x40, 1, 32),
                sprites = TableLayout(0, 1, 6),
            ),
        )

        val sprite = SpriteMaterializer.pokemon(RomImage(bytes), layout).getValue(1)

        assertEquals(8, sprite.width)
        assertEquals(8, sprite.height)
        assertEquals(true, sprite.argb[0] != 0)
    }

    @Test
    fun genTwoAppliesRomFamilySpriteBankRemapping() {
        val bytes = ByteArray(0xC000)
        bytes[0] = 1
        putU16(bytes, 1, 0x4020)
        val raw = ByteArray(16).also { it[0] = 0x80.toByte() }
        (byteArrayOf(0x0F) + raw + byteArrayOf(0xFF.toByte())).copyInto(bytes, 0x8020)
        val layout = ResolvedRomLayout(
            family = EngineFamily.GOLD_SILVER,
            generation = 2,
            platform = Platform.GBC,
            speciesCount = 1,
            moveCount = 0,
            tables = ProfileTables(sprites = TableLayout(0, 1, 6, bankRemap = mapOf(1 to 2))),
        )

        val sprite = SpriteMaterializer.pokemon(RomImage(bytes), layout).getValue(1)

        assertEquals(8, sprite.width)
        assertEquals(true, sprite.argb[0] != 0)
    }

    @Test
    fun genTwoFindsUnownFormTableWhenMainSpeciesPointerIsEmpty() {
        val bytes = ByteArray(0xC000)
        val spriteTable = 0x100
        val unownEntry = spriteTable + 200 * 6
        bytes[unownEntry] = 0xFF.toByte()
        putU16(bytes, unownEntry + 1, 0xFFFF)
        val unownTable = 0x4100
        repeat(26) { form ->
            val entry = unownTable + form * 6
            bytes[entry] = 2
            putU16(bytes, entry + 1, 0x4200)
            bytes[entry + 3] = 2
            putU16(bytes, entry + 4, 0x4200)
        }
        val raw = ByteArray(16).also { it[0] = 0x80.toByte() }
        (byteArrayOf(0x0F) + raw + byteArrayOf(0xFF.toByte())).copyInto(bytes, 0x8200)
        val stats = 0x1000
        bytes[stats + 200 * 32 + 17] = 0x11
        val layout = ResolvedRomLayout(
            family = EngineFamily.CRYSTAL,
            generation = 2,
            platform = Platform.GBC,
            speciesCount = 251,
            moveCount = 0,
            tables = ProfileTables(
                baseStats = TableLayout(stats, 251, 32),
                sprites = TableLayout(spriteTable, 251, 6),
            ),
        )

        val sprite = SpriteMaterializer.pokemon(RomImage(bytes), layout).getValue(201)

        assertEquals(8, sprite.width)
        assertEquals(true, sprite.argb[0] != 0)
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
