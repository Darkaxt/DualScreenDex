package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.TrainerAssetCatalog
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.sprite.GbaRomCompression
import com.enrpau.dualscreendex.parser.sprite.IndexedSprite
import com.enrpau.dualscreendex.parser.sprite.TileRenderer

/** Materializes player-owned Trainer Card artwork from compiled Gen III asset roles. */
object Gen3TrainerAssetResolver {
    fun resolve(rom: RomImage, family: EngineFamily): TrainerAssetCatalog? {
        if (family !in setOf(EngineFamily.RUBY_SAPPHIRE, EngineFamily.EMERALD)) return null
        val avatars = resolvePlayerAvatars(rom) ?: return null
        val badgeSheet = resolveHoennBadgeSheet(rom) ?: return null
        val assets = linkedMapOf(
            MALE_AVATAR_KEY to avatars.first,
            FEMALE_AVATAR_KEY to avatars.second,
        )
        val badgeKeys = (1..BADGE_COUNT).map { badge ->
            val key = "trainer/badge/$badge"
            assets[key] = crop(badgeSheet, (badge - 1) * BADGE_PIXELS, 0, BADGE_PIXELS, BADGE_PIXELS)
            key
        }
        return TrainerAssetCatalog(
            avatarAssetKeys = mapOf(0 to MALE_AVATAR_KEY, 1 to FEMALE_AVATAR_KEY),
            badgeAssetKeys = badgeKeys,
            assets = assets,
        ).validate()
    }

    private fun resolvePlayerAvatars(rom: RomImage): Pair<RgbaSprite, RgbaSprite>? {
        val candidates = buildList {
            var table = 0
            val playerRowsEnd = (FEMALE_PLAYER_PIC_INDEX + 1) * TABLE_RECORD_BYTES
            while (table.toLong() + playerRowsEnd <= rom.size.toLong()) {
                if (
                    rom.u32le(table + MALE_PLAYER_PIC_INDEX * TABLE_RECORD_BYTES + 4) ==
                    packedSheetMetadata(MALE_PLAYER_PIC_INDEX) &&
                    rom.u32le(table + FEMALE_PLAYER_PIC_INDEX * TABLE_RECORD_BYTES + 4) ==
                    packedSheetMetadata(FEMALE_PLAYER_PIC_INDEX)
                ) {
                    findTableCountAndPalette(rom, table)?.let { countAndPalette ->
                        val (count, paletteTable) = countAndPalette
                        val male = renderTrainerPic(rom, table, paletteTable, count, MALE_PLAYER_PIC_INDEX)
                        val female = renderTrainerPic(rom, table, paletteTable, count, FEMALE_PLAYER_PIC_INDEX)
                        if (male != null && female != null) add(male to female)
                    }
                }
                table += 4
            }
        }
        return candidates.singleOrNull()
    }

    private fun findTableCountAndPalette(rom: RomImage, table: Int): Pair<Int, Int>? {
        var count = FEMALE_PLAYER_PIC_INDEX + 1
        while (count < MAX_TRAINER_PICS && isTrainerSheetRecord(rom, table, count)) count++
        if (count <= FEMALE_PLAYER_PIC_INDEX + 1) return null
        val paletteTable = table + count * TABLE_RECORD_BYTES
        if (paletteTable.toLong() + count.toLong() * TABLE_RECORD_BYTES > rom.size.toLong()) return null
        val samples = linkedSetOf(0, 1, MALE_PLAYER_PIC_INDEX, FEMALE_PLAYER_PIC_INDEX, count - 1)
        if (samples.any { !isTrainerPaletteRecord(rom, paletteTable, it) }) return null
        return count to paletteTable
    }

    private fun isTrainerSheetRecord(rom: RomImage, table: Int, index: Int): Boolean {
        val entry = table + index * TABLE_RECORD_BYTES
        if (entry.toLong() + TABLE_RECORD_BYTES > rom.size.toLong()) return false
        val size = rom.u16le(entry + 4)
        if (size !in setOf(TRAINER_PIC_BYTES, TRAINER_PIC_BYTES * 2)) return false
        if (rom.u16le(entry + 6) != index) return false
        val pointer = rom.gbaPointer(entry) ?: return false
        val decodedSize = GbaRomCompression.decodedSizeAtOrNull(rom, pointer) ?: return false
        return decodedSize in setOf(TRAINER_PIC_BYTES, TRAINER_PIC_BYTES * 2) && decodedSize <= size
    }

    private fun isTrainerPaletteRecord(rom: RomImage, table: Int, index: Int): Boolean {
        val entry = table + index * TABLE_RECORD_BYTES
        if (rom.u16le(entry + 4) != index || rom.u16le(entry + 6) != 0) return false
        val pointer = rom.gbaPointer(entry) ?: return false
        return GbaRomCompression.decodedSizeAtOrNull(rom, pointer) == PALETTE_BYTES
    }

    private fun renderTrainerPic(
        rom: RomImage,
        sheetTable: Int,
        paletteTable: Int,
        count: Int,
        index: Int,
    ): RgbaSprite? = runCatching {
        require(index in 0 until count)
        val graphicsPointer = requireNotNull(rom.gbaPointer(sheetTable + index * TABLE_RECORD_BYTES))
        val graphics = GbaRomCompression.decodeAt(rom, graphicsPointer)
        require(graphics.size >= TRAINER_PIC_BYTES)
        val palettePointer = requireNotNull(rom.gbaPointer(paletteTable + index * TABLE_RECORD_BYTES))
        val paletteBytes = GbaRomCompression.decodeAt(rom, palettePointer)
        require(paletteBytes.size == PALETTE_BYTES)
        TileRenderer.applyBgr555Palette(
            TileRenderer.gba4Bpp(graphics.copyOf(TRAINER_PIC_BYTES), 8, 8),
            readPalette(paletteBytes),
        )
    }.getOrNull()

    private fun resolveHoennBadgeSheet(rom: RomImage): RgbaSprite? {
        val graphics = resolveBadgeGraphicsBranches(rom) ?: return null
        val palette = resolveBadgePalette(rom) ?: return null
        val indexed = TileRenderer.gba4Bpp(graphics, 16, 2)
        if (!validBadgeGrid(indexed)) return null
        return TileRenderer.applyBgr555Palette(indexed, palette)
    }

    private fun resolveBadgeGraphicsBranches(rom: RomImage): ByteArray? {
        val branches = buildList {
            var offset = 0
            while (offset <= rom.size - 6) {
                val loadGraphics = rom.u16le(offset)
                val loadDestination = rom.u16le(offset + 2)
                val branch = rom.u16le(offset + 4)
                if (
                    loadGraphics and 0xF800 == 0x4800 && (loadGraphics ushr 8) and 7 == 0 &&
                    loadDestination and 0xF800 == 0x4800 && (loadDestination ushr 8) and 7 == 2 &&
                    branch and 0xF800 == 0xE000
                ) {
                    val pointer = literalRomPointer(rom, offset, loadGraphics)
                    val decoded = pointer?.let { candidate ->
                        if (GbaRomCompression.decodedSizeAtOrNull(rom, candidate) == BADGE_SHEET_BYTES) {
                            runCatching { GbaRomCompression.decodeAt(rom, candidate) }.getOrNull()
                        } else null
                    }
                    if (decoded != null && validBadgeGrid(TileRenderer.gba4Bpp(decoded, 16, 2))) {
                        add(BadgeGraphicsBranch(offset, thumbBranchTarget(offset + 4, branch), decoded))
                    }
                }
                offset += 2
            }
        }
        val alternatives = branches.groupBy(BadgeGraphicsBranch::target)
            .values
            .filter { group -> group.size == 2 && group.map(BadgeGraphicsBranch::graphics).distinctBy(ByteArray::contentHashCode).size == 2 }
            .singleOrNull()
            ?: return null
        return alternatives.minBy(BadgeGraphicsBranch::site).graphics
    }

    private fun resolveBadgePalette(rom: RomImage): ShortArray? {
        val candidates = buildList {
            var offset = 0
            while (offset <= rom.size - 8) {
                val loadPalette = rom.u16le(offset)
                if (
                    loadPalette and 0xF800 == 0x4800 && (loadPalette ushr 8) and 7 == 0 &&
                    rom.u16le(offset + 2) == 0x2130 &&
                    rom.u16le(offset + 4) == 0x2220 &&
                    rom.u16le(offset + 6) and 0xF800 == 0xF000
                ) {
                    literalRomPointer(rom, offset, loadPalette)?.let { pointer ->
                        if (pointer.toLong() + PALETTE_BYTES <= rom.size.toLong()) {
                            add(pointer to rom.slice(pointer, PALETTE_BYTES))
                        }
                    }
                }
                offset += 2
            }
        }
        val matched = candidates.groupBy { it.second.toList() }
            .values
            .filter { group -> group.map { it.first }.distinct().size == 2 }
            .singleOrNull()
            ?: return null
        return readPalette(matched.first().second)
    }

    private fun literalRomPointer(rom: RomImage, instructionOffset: Int, instruction: Int): Int? {
        val literalOffset = ((instructionOffset + 4) and -4) + (instruction and 0xFF) * 4
        return if (literalOffset <= rom.size - 4) rom.gbaPointer(literalOffset) else null
    }

    private fun thumbBranchTarget(instructionOffset: Int, instruction: Int): Int {
        var displacement = (instruction and 0x7FF) shl 1
        if (displacement and 0x800 != 0) displacement -= 0x1000
        return instructionOffset + 4 + displacement
    }

    private fun validBadgeGrid(indexed: IndexedSprite): Boolean = (0 until BADGE_COUNT).all { badge ->
        val values = linkedSetOf<Int>()
        var occupied = 0
        repeat(BADGE_PIXELS) { y ->
            repeat(BADGE_PIXELS) { x ->
                val value = indexed.indexAt(badge * BADGE_PIXELS + x, y)
                values += value
                if (value != 0) occupied++
            }
        }
        occupied >= MIN_BADGE_OCCUPIED_PIXELS && values.size >= MIN_BADGE_COLORS
    }

    private fun readPalette(bytes: ByteArray): ShortArray = ShortArray(16) { index ->
        ((bytes[index * 2].toInt() and 0xFF) or ((bytes[index * 2 + 1].toInt() and 0xFF) shl 8)).toShort()
    }

    private fun crop(source: RgbaSprite, left: Int, top: Int, width: Int, height: Int): RgbaSprite =
        RgbaSprite(
            width,
            height,
            IntArray(width * height) { position ->
                val x = position % width
                val y = position / width
                source.argb[(top + y) * source.width + left + x]
            },
        )

    private fun packedSheetMetadata(index: Int): Long =
        (index.toLong() shl 16) or TRAINER_PIC_BYTES.toLong()

    private const val TABLE_RECORD_BYTES = 8
    private const val TRAINER_PIC_BYTES = 2048
    private const val PALETTE_BYTES = 32
    private const val MALE_PLAYER_PIC_INDEX = 71
    private const val FEMALE_PLAYER_PIC_INDEX = 72
    private const val MAX_TRAINER_PICS = 512
    private const val BADGE_COUNT = 8
    private const val BADGE_PIXELS = 16
    private const val BADGE_SHEET_BYTES = 1024
    private const val MIN_BADGE_OCCUPIED_PIXELS = 32
    private const val MIN_BADGE_COLORS = 3
    private const val MALE_AVATAR_KEY = "trainer/avatar/male"
    private const val FEMALE_AVATAR_KEY = "trainer/avatar/female"

    private data class BadgeGraphicsBranch(val site: Int, val target: Int, val graphics: ByteArray)
}
