package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.TrainerAssetCatalog
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.sprite.IndexedSprite
import com.enrpau.dualscreendex.parser.sprite.TileRenderer

internal object GbTrainerAssetResolver {
    fun resolve(rom: RomImage, family: EngineFamily): TrainerAssetCatalog? = when (family) {
        EngineFamily.RED_BLUE,
        EngineFamily.YELLOW,
        -> resolveGen1(rom)
        EngineFamily.GOLD_SILVER -> resolveGen2(rom, includeFemale = false)
        EngineFamily.CRYSTAL -> resolveGen2(rom, includeFemale = true)
        else -> null
    }

    private fun resolveGen1(rom: RomImage): TrainerAssetCatalog? {
        val graphics = (findGen1PrimaryGraphics(rom) + findGen1YellowGraphics(rom)).distinct().singleOrNull()
            ?: return null
        val frame = renderGen1Frame(rom, graphics) ?: return null
        return TrainerAssetCatalog(
            overworldAssetKeys = mapOf(0 to PLAYER_OVERWORLD_KEY),
            assets = mapOf(PLAYER_OVERWORLD_KEY to frame),
        ).validate()
    }

    private fun findGen1PrimaryGraphics(rom: RomImage): List<Int> = buildList {
        var site = 0
        while (site.toLong() + GEN1_PRIMARY_CONSUMER_BYTES <= rom.size.toLong()) {
            if (
                rom.u8(site) == LOAD_DE_IMMEDIATE && rom.u8(site + 3) == LOAD_HL_IMMEDIATE &&
                rom.u8(site + 6) == JR && rom.u8(site + 7) == 0x0e &&
                rom.u8(site + 8) == LOAD_DE_IMMEDIATE && rom.u8(site + 11) == LOAD_HL_IMMEDIATE &&
                rom.u8(site + 14) == JR && rom.u8(site + 15) == 0x06 &&
                rom.u8(site + 16) == LOAD_DE_IMMEDIATE && rom.u8(site + 19) == LOAD_HL_IMMEDIATE &&
                rom.u8(site + 22) == PUSH_DE && rom.u8(site + 23) == PUSH_HL &&
                rom.u8(site + 24) == LOAD_BC_IMMEDIATE && rom.u8(site + 25) == PLAYER_TILE_COUNT &&
                rom.u8(site + 27) == CALL
            ) {
                val destination = rom.u16le(site + 4)
                val graphicsBank = rom.u8(site + 26)
                val pointers = listOf(1, 9, 17).map { pointerOffset -> rom.u16le(site + pointerOffset) }
                val graphics = pointers.mapNotNull { address -> boundedGraphics(rom, graphicsBank, address) }
                if (
                    destination in VRAM_START..VRAM_PLAYER_END &&
                    rom.u16le(site + 12) == destination && rom.u16le(site + 20) == destination &&
                    pointers.distinct().size == pointers.size && graphics.size == pointers.size &&
                    validCallTarget(rom, site, rom.u16le(site + 28))
                ) {
                    add(graphics.first())
                }
            }
            site++
        }
    }

    private fun findGen1YellowGraphics(rom: RomImage): List<Int> = buildList {
        var site = 0
        while (site.toLong() + YELLOW_LOADER_BYTES <= rom.size.toLong()) {
            if (
                rom.u8(site) == XOR_A && rom.u8(site + 1) == STORE_A_ABSOLUTE &&
                rom.u16le(site + 2) in WRAM_RANGE && rom.u8(site + 4) == LOAD_B_IMMEDIATE &&
                rom.u8(site + 6) == LOAD_DE_IMMEDIATE && rom.u8(site + 9) == JR
            ) {
                val common = site + YELLOW_LOADER_BYTES + signed(rom.u8(site + 10))
                val siteBank = site / BANK_BYTES
                if (
                    common > site && common / BANK_BYTES == siteBank &&
                    isYellowCopyRoutine(rom, common, siteBank)
                ) {
                    val walking = boundedGraphics(rom, rom.u8(site + 5), rom.u16le(site + 7))
                    val bike = if (
                        common >= 5 && rom.u8(common - 5) == LOAD_B_IMMEDIATE &&
                        rom.u8(common - 3) == LOAD_DE_IMMEDIATE
                    ) {
                        boundedGraphics(rom, rom.u8(common - 4), rom.u16le(common - 2))
                    } else {
                        null
                    }
                    val jumpingStates = buildList {
                        var loader = site + YELLOW_LOADER_BYTES
                        while (loader + 7 <= common - 5) {
                            if (
                                rom.u8(loader) == LOAD_B_IMMEDIATE &&
                                rom.u8(loader + 2) == LOAD_DE_IMMEDIATE && rom.u8(loader + 5) == JR &&
                                loader + 7 + signed(rom.u8(loader + 6)) == common
                            ) {
                                boundedGraphics(rom, rom.u8(loader + 1), rom.u16le(loader + 3))?.let(::add)
                            }
                            loader++
                        }
                    }
                    if (
                        walking != null && bike != null && jumpingStates.isNotEmpty() &&
                        (listOf(walking, bike) + jumpingStates).distinct().size >= 3
                    ) {
                        add(walking)
                    }
                }
            }
            site++
        }
    }

    private fun isYellowCopyRoutine(rom: RomImage, offset: Int, bank: Int): Boolean = runCatching {
        require(offset + YELLOW_COPY_ROUTINE_BYTES <= bankEnd(rom, bank))
        require(rom.u8(offset) == LOAD_HL_IMMEDIATE)
        require(rom.u16le(offset + 1) in VRAM_START..VRAM_PLAYER_END)
        require(
            rom.slice(offset + 3, 5).contentEquals(
                byteArrayOf(PUSH_DE.toByte(), PUSH_HL.toByte(), PUSH_BC.toByte(), LOAD_C_IMMEDIATE.toByte(), PLAYER_TILE_COUNT.toByte()),
            ),
        )
        require(rom.u8(offset + 8) == CALL)
        require(rom.slice(offset + 11, 9).contentEquals(YELLOW_COPY_MIDDLE))
        require(rom.slice(offset + 20, 6).contentEquals(YELLOW_COPY_END))
        require(rom.u16le(offset + 9) == rom.u16le(offset + 26))
        require(validCallTarget(rom, offset, rom.u16le(offset + 9)))
        true
    }.getOrDefault(false)

    private fun renderGen1Frame(rom: RomImage, graphics: Int): RgbaSprite? = runCatching {
        val indexed = TileRenderer.gameBoy2Bpp(rom.slice(graphics, FRAME_BYTES), FRAME_TILE_EDGE, FRAME_TILE_EDGE)
        requireValidFrame(indexed)
        TileRenderer.applyArgbPalette(indexed, GEN1_OBJECT_PALETTE)
    }.getOrNull()

    private fun resolveGen2(rom: RomImage, includeFemale: Boolean): TrainerAssetCatalog? {
        val table = findGen2SpriteTables(rom).distinct().singleOrNull() ?: return null
        val palettes = findGen2PaletteTables(rom).distinct().singleOrNull() ?: return null
        val male = readGen2SpriteRow(rom, table, 0)?.let { row -> renderGen2Frame(rom, row, palettes) }
            ?: return null
        val female = if (includeFemale) {
            readGen2SpriteRow(rom, table, CRYSTAL_FEMALE_ROW)?.let { row -> renderGen2Frame(rom, row, palettes) }
        } else {
            null
        }
        val assets = linkedMapOf<String, RgbaSprite>()
        return if (female == null) {
            assets[PLAYER_OVERWORLD_KEY] = male
            TrainerAssetCatalog(
                overworldAssetKeys = mapOf(0 to PLAYER_OVERWORLD_KEY),
                assets = assets,
            ).validate()
        } else {
            assets[MALE_OVERWORLD_KEY] = male
            assets[FEMALE_OVERWORLD_KEY] = female
            TrainerAssetCatalog(
                overworldAssetKeys = mapOf(0 to MALE_OVERWORLD_KEY, 1 to FEMALE_OVERWORLD_KEY),
                assets = assets,
            ).validate()
        }
    }

    private fun findGen2SpriteTables(rom: RomImage): List<Int> = buildList {
        var site = 0
        while (site.toLong() + GEN2_NEW_SPRITE_CONSUMER_BYTES <= rom.size.toLong()) {
            if (
                rom.u8(site) == LOAD_HL_IMMEDIATE &&
                rom.slice(site + 3, 6).contentEquals(GEN2_SPRITE_INDEX_PREFIX) &&
                rom.u8(site + 9) == CALL && rom.slice(site + 12, 13).contentEquals(GEN2_NEW_SPRITE_SUFFIX) &&
                validCallTarget(rom, site, rom.u16le(site + 10))
            ) {
                rom.gbBankAddress(site / BANK_BYTES, rom.u16le(site + 1))?.let(::add)
            }
            site++
        }
        site = 0
        while (site.toLong() + GEN2_OLD_SPRITE_CONSUMER_BYTES <= rom.size.toLong()) {
            if (
                rom.u8(site) == PUSH_HL && rom.u8(site + 1) == LOAD_HL_IMMEDIATE &&
                rom.slice(site + 4, 6).contentEquals(GEN2_SPRITE_INDEX_PREFIX) &&
                rom.u8(site + 10) == CALL && rom.slice(site + 13, 9).contentEquals(GEN2_OLD_SPRITE_SUFFIX) &&
                rom.u8(site + 22) == POP_HL && rom.u8(site + 23) == RETURN &&
                validCallTarget(rom, site, rom.u16le(site + 11))
            ) {
                rom.gbBankAddress(site / BANK_BYTES, rom.u16le(site + 2))?.let(::add)
            }
            site++
        }
    }.filter { table -> table + GEN2_SPRITE_ROW_BYTES <= bankEnd(rom, table / BANK_BYTES) }

    private fun readGen2SpriteRow(rom: RomImage, table: Int, index: Int): Gen2SpriteRow? = runCatching {
        val tableBank = table / BANK_BYTES
        val row = table + index * GEN2_SPRITE_ROW_BYTES
        require(row + GEN2_SPRITE_ROW_BYTES <= bankEnd(rom, tableBank))
        require(rom.u8(row + 2) == ENCODED_PLAYER_TILE_LENGTH)
        require(rom.u8(row + 4) == WALKING_SPRITE_TYPE)
        val palette = rom.u8(row + 5)
        require(palette in 0 until GEN2_PALETTE_COUNT)
        val graphicsBank = rom.u8(row + 3)
        val graphics = requireNotNull(boundedGraphics(rom, graphicsBank, rom.u16le(row)))
        Gen2SpriteRow(graphics, palette)
    }.getOrNull()

    private fun findGen2PaletteTables(rom: RomImage): List<Int> = buildList {
        var site = 0
        while (site.toLong() + GEN2_OLD_PALETTE_CONSUMER_BYTES <= rom.size.toLong()) {
            if (isGen2PalettePrefix(rom, site) && rom.u8(site + 20) == CALL &&
                validCallTarget(rom, site, rom.u16le(site + 12)) &&
                validCallTarget(rom, site, rom.u16le(site + 21))
            ) {
                paletteTable(rom, site)?.let(::add)
            }
            site++
        }
        site = 0
        while (site.toLong() + GEN2_NEW_PALETTE_CONSUMER_BYTES <= rom.size.toLong()) {
            if (
                isGen2PalettePrefix(rom, site) && rom.u8(site + 20) == LOAD_A_IMMEDIATE &&
                rom.u8(site + 21) in 1..7 && rom.u8(site + 22) == CALL &&
                validCallTarget(rom, site, rom.u16le(site + 12)) &&
                validCallTarget(rom, site, rom.u16le(site + 23))
            ) {
                paletteTable(rom, site)?.let(::add)
            }
            site++
        }
    }

    private fun isGen2PalettePrefix(rom: RomImage, site: Int): Boolean =
        rom.u8(site) == LOAD_A_ABSOLUTE && rom.u16le(site + 1) in WRAM_RANGE &&
            rom.u8(site + 3) == AND_IMMEDIATE && rom.u8(site + 4) == 3 &&
            rom.u8(site + 5) == LOAD_BC_IMMEDIATE && rom.u16le(site + 6) == GEN2_TIME_BLOCK_BYTES &&
            rom.u8(site + 8) == LOAD_HL_IMMEDIATE && rom.u8(site + 11) == CALL &&
            rom.u8(site + 14) == LOAD_DE_IMMEDIATE && rom.u16le(site + 15) in WRAM_RANGE &&
            rom.u8(site + 17) == LOAD_BC_IMMEDIATE && rom.u16le(site + 18) == GEN2_TIME_BLOCK_BYTES

    private fun paletteTable(rom: RomImage, site: Int): Int? {
        val table = rom.gbBankAddress(site / BANK_BYTES, rom.u16le(site + 9)) ?: return null
        return table.takeIf { it + GEN2_PALETTE_TABLE_BYTES <= bankEnd(rom, site / BANK_BYTES) }
    }

    private fun renderGen2Frame(rom: RomImage, row: Gen2SpriteRow, palettes: Int): RgbaSprite? = runCatching {
        val palette = ShortArray(COLORS_PER_PALETTE) { color ->
            val value = rom.u16le(
                palettes + DAY_PALETTE_OFFSET + row.palette * PALETTE_BYTES + color * COLOR_BYTES,
            )
            require(value <= MAX_BGR555)
            value.toShort()
        }
        val indexed = TileRenderer.gameBoy2Bpp(
            rom.slice(row.graphics, FRAME_BYTES),
            FRAME_TILE_EDGE,
            FRAME_TILE_EDGE,
        )
        requireValidFrame(indexed)
        TileRenderer.applyBgr555Palette(indexed, palette)
    }.getOrNull()

    private fun requireValidFrame(indexed: IndexedSprite) {
        val occupied = indexed.indices.count { (it.toInt() and 0xff) != 0 }
        require(occupied in MIN_OCCUPIED_PIXELS..MAX_OCCUPIED_PIXELS)
        require(indexed.indices.map { it.toInt() and 0xff }.distinct().size >= MIN_FRAME_COLORS)
    }

    private fun boundedGraphics(rom: RomImage, bank: Int, address: Int): Int? {
        val graphics = rom.gbBankAddress(bank, address) ?: return null
        return graphics.takeIf { it.toLong() + PLAYER_GRAPHICS_BYTES <= bankEnd(rom, bank).toLong() }
    }

    private fun validCallTarget(rom: RomImage, site: Int, address: Int): Boolean {
        val targetBank = if (address < BANK_BYTES) 0 else site / BANK_BYTES
        return rom.gbBankAddress(targetBank, address) != null
    }

    private fun bankEnd(rom: RomImage, bank: Int): Int =
        minOf(rom.size.toLong(), (bank.toLong() + 1L) * BANK_BYTES).toInt()

    private fun signed(value: Int): Int = value.toByte().toInt()

    private data class Gen2SpriteRow(val graphics: Int, val palette: Int)

    private val WRAM_RANGE = 0xc000..0xdfff
    private val GEN1_OBJECT_PALETTE = intArrayOf(
        0x00000000,
        0xffaaaaaa.toInt(),
        0xff555555.toInt(),
        0xff000000.toInt(),
    )
    private val YELLOW_COPY_MIDDLE = byteArrayOf(
        POP_BC.toByte(), POP_HL.toByte(), POP_DE.toByte(), LOAD_A_IMMEDIATE.toByte(),
        0xc0.toByte(), ADD_E.toByte(), LOAD_E_A.toByte(), JR_NC.toByte(), 0x01,
    )
    private val YELLOW_COPY_END = byteArrayOf(
        INC_D.toByte(), PREFIX.toByte(), SET_3_H.toByte(), LOAD_C_IMMEDIATE.toByte(),
        PLAYER_TILE_COUNT.toByte(), JUMP.toByte(),
    )
    private val GEN2_SPRITE_INDEX_PREFIX = byteArrayOf(
        DEC_A.toByte(), LOAD_C_A.toByte(), LOAD_B_IMMEDIATE.toByte(), 0x00,
        LOAD_A_IMMEDIATE.toByte(), GEN2_SPRITE_ROW_BYTES.toByte(),
    )
    private val GEN2_NEW_SPRITE_SUFFIX = byteArrayOf(
        LOAD_A_HL_INCREMENT.toByte(), LOAD_E_A.toByte(), LOAD_A_HL_INCREMENT.toByte(), LOAD_D_A.toByte(),
        LOAD_A_HL_INCREMENT.toByte(), PREFIX.toByte(), SWAP_A.toByte(), LOAD_C_A.toByte(), LOAD_B_HL.toByte(),
        LOAD_A_HL_INCREMENT.toByte(), LOAD_L_HL.toByte(), LOAD_H_A.toByte(), RETURN.toByte(),
    )
    private val GEN2_OLD_SPRITE_SUFFIX = byteArrayOf(
        LOAD_A_HL_INCREMENT.toByte(), LOAD_E_A.toByte(), LOAD_A_HL_INCREMENT.toByte(), LOAD_D_A.toByte(),
        LOAD_A_HL_INCREMENT.toByte(), PREFIX.toByte(), SWAP_A.toByte(), LOAD_C_A.toByte(), LOAD_B_HL.toByte(),
    )

    private const val BANK_BYTES = 0x4000
    private const val GEN1_PRIMARY_CONSUMER_BYTES = 30
    private const val YELLOW_LOADER_BYTES = 11
    private const val YELLOW_COPY_ROUTINE_BYTES = 28
    private const val GEN2_NEW_SPRITE_CONSUMER_BYTES = 25
    private const val GEN2_OLD_SPRITE_CONSUMER_BYTES = 24
    private const val GEN2_OLD_PALETTE_CONSUMER_BYTES = 23
    private const val GEN2_NEW_PALETTE_CONSUMER_BYTES = 25
    private const val GEN2_SPRITE_ROW_BYTES = 6
    private const val CRYSTAL_FEMALE_ROW = 0x5f
    private const val PLAYER_TILE_COUNT = 12
    private const val PLAYER_GRAPHICS_BYTES = PLAYER_TILE_COUNT * 16
    private const val FRAME_TILE_EDGE = 2
    private const val FRAME_BYTES = FRAME_TILE_EDGE * FRAME_TILE_EDGE * 16
    private const val ENCODED_PLAYER_TILE_LENGTH = 0xc0
    private const val WALKING_SPRITE_TYPE = 1
    private const val GEN2_PALETTE_COUNT = 8
    private const val GEN2_TIME_BLOCK_BYTES = GEN2_PALETTE_COUNT * 4 * 2
    private const val GEN2_PALETTE_TABLE_BYTES = 4 * GEN2_TIME_BLOCK_BYTES
    private const val DAY_PALETTE_OFFSET = GEN2_TIME_BLOCK_BYTES
    private const val COLORS_PER_PALETTE = 4
    private const val PALETTE_BYTES = COLORS_PER_PALETTE * 2
    private const val COLOR_BYTES = 2
    private const val MAX_BGR555 = 0x7fff
    private const val VRAM_START = 0x8000
    private const val VRAM_PLAYER_END = 0x9740
    private const val MIN_OCCUPIED_PIXELS = 32
    private const val MAX_OCCUPIED_PIXELS = 240
    private const val MIN_FRAME_COLORS = 3

    private const val LOAD_BC_IMMEDIATE = 0x01
    private const val LOAD_B_IMMEDIATE = 0x06
    private const val LOAD_C_IMMEDIATE = 0x0e
    private const val LOAD_DE_IMMEDIATE = 0x11
    private const val INC_D = 0x14
    private const val JR = 0x18
    private const val LOAD_HL_IMMEDIATE = 0x21
    private const val LOAD_A_HL_INCREMENT = 0x2a
    private const val JR_NC = 0x30
    private const val SWAP_A = 0x37
    private const val DEC_A = 0x3d
    private const val LOAD_A_IMMEDIATE = 0x3e
    private const val LOAD_B_HL = 0x46
    private const val LOAD_C_A = 0x4f
    private const val LOAD_D_A = 0x57
    private const val LOAD_E_A = 0x5f
    private const val LOAD_H_A = 0x67
    private const val LOAD_L_HL = 0x6e
    private const val ADD_E = 0x83
    private const val XOR_A = 0xaf
    private const val POP_BC = 0xc1
    private const val JUMP = 0xc3
    private const val PUSH_BC = 0xc5
    private const val RETURN = 0xc9
    private const val PREFIX = 0xcb
    private const val CALL = 0xcd
    private const val POP_DE = 0xd1
    private const val PUSH_DE = 0xd5
    private const val SET_3_H = 0xdc
    private const val POP_HL = 0xe1
    private const val PUSH_HL = 0xe5
    private const val AND_IMMEDIATE = 0xe6
    private const val STORE_A_ABSOLUTE = 0xea
    private const val LOAD_A_ABSOLUTE = 0xfa

    private const val PLAYER_OVERWORLD_KEY = "trainer/overworld/player"
    private const val MALE_OVERWORLD_KEY = "trainer/overworld/male"
    private const val FEMALE_OVERWORLD_KEY = "trainer/overworld/female"
}
