package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.TrainerAssetCatalog
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.sprite.GbaDecodeContract
import com.enrpau.dualscreendex.parser.sprite.GbaRomCompression
import com.enrpau.dualscreendex.parser.sprite.IndexedSprite
import com.enrpau.dualscreendex.parser.sprite.TileRenderer

/** Materializes player-owned Trainer Card artwork from compiled Gen III asset roles. */
object Gen3TrainerAssetResolver {
    fun resolve(rom: RomImage, family: EngineFamily): TrainerAssetCatalog? {
        if (family !in setOf(EngineFamily.RUBY_SAPPHIRE, EngineFamily.EMERALD, EngineFamily.FIRERED_LEAFGREEN)) {
            return null
        }
        val avatars = resolvePlayerAvatars(rom)
        val overworld = resolvePlayerOverworldSprites(rom, family)
        val badgeSheet = if (family == EngineFamily.FIRERED_LEAFGREEN) null else resolveHoennBadgeSheet(rom)
        if (avatars == null && overworld == null && badgeSheet == null) return null

        val assets = linkedMapOf<String, RgbaSprite>()
        val avatarAssetKeys = if (avatars != null) {
            assets[MALE_AVATAR_KEY] = avatars.first
            assets[FEMALE_AVATAR_KEY] = avatars.second
            mapOf(0 to MALE_AVATAR_KEY, 1 to FEMALE_AVATAR_KEY)
        } else {
            emptyMap()
        }
        val overworldAssetKeys = if (overworld != null) {
            assets[MALE_OVERWORLD_KEY] = overworld.first
            assets[FEMALE_OVERWORLD_KEY] = overworld.second
            mapOf(0 to MALE_OVERWORLD_KEY, 1 to FEMALE_OVERWORLD_KEY)
        } else {
            emptyMap()
        }
        val badgeKeys = badgeSheet?.let { sheet ->
            (1..BADGE_COUNT).map { badge ->
                val key = "trainer/badge/$badge"
                assets[key] = crop(sheet, (badge - 1) * BADGE_PIXELS, 0, BADGE_PIXELS, BADGE_PIXELS)
                key
            }
        }.orEmpty()
        return TrainerAssetCatalog(
            avatarAssetKeys = avatarAssetKeys,
            overworldAssetKeys = overworldAssetKeys,
            badgeAssetKeys = badgeKeys,
            assets = assets,
        ).validate()
    }

    private fun resolvePlayerOverworldSprites(
        rom: RomImage,
        family: EngineFamily,
    ): Pair<RgbaSprite, RgbaSprite>? {
        val femaleNormalGraphicsId = when (family) {
            EngineFamily.FIRERED_LEAFGREEN -> FIRERED_FEMALE_NORMAL_GRAPHICS_ID
            EngineFamily.RUBY_SAPPHIRE,
            EngineFamily.EMERALD,
            -> HOENN_FEMALE_NORMAL_GRAPHICS_ID
            else -> return null
        }
        val rawCandidates = buildList {
            var table = 0
            while (table.toLong() + femaleNormalGraphicsId * 4L + 4L <= rom.size.toLong()) {
                val maleInfo = rom.gbaPointer(table)
                val femaleInfo = rom.gbaPointer(table + femaleNormalGraphicsId * 4)
                if (maleInfo != null && femaleInfo != null) {
                    val male = readNormalOverworldInfo(rom, maleInfo)
                    val female = readNormalOverworldInfo(rom, femaleInfo)
                    if (male != null && female != null && male.paletteTag != female.paletteTag) {
                        add(OverworldPairCandidate(table, male, female))
                    }
                }
                table += 4
            }
        }.distinctBy { candidate ->
            listOf(
                candidate.male.frameData,
                candidate.male.paletteTag,
                candidate.female.frameData,
                candidate.female.paletteTag,
            )
        }
        val legacyCandidates = rawCandidates.filter {
            it.male.width == 16 &&
                it.female.width == 16 &&
                it.male.sequentialFrames &&
                it.female.sequentialFrames
        }
        val fireRedPlayerPaletteCandidates = rawCandidates.filter {
            it.male.paletteTag == FIRERED_MALE_PLAYER_PALETTE_TAG &&
                it.female.paletteTag == FIRERED_FEMALE_PLAYER_PALETTE_TAG
        }
        val fireRedPlayerPaletteRoots = fireRedPlayerPaletteCandidates.filter { candidate ->
            fireRedPlayerPaletteCandidates.none { it.table == candidate.table - 4 }
        }
        val candidates = if (
            family == EngineFamily.FIRERED_LEAFGREEN &&
            fireRedPlayerPaletteRoots.size == 1
        ) {
            fireRedPlayerPaletteRoots
        } else if (legacyCandidates.size == 1) {
            legacyCandidates
        } else if (family == EngineFamily.FIRERED_LEAFGREEN) {
            rawCandidates.filter { matchesFireRedPlayerGraphicsBlock(rom, it.table) }
                .takeIf { it.size == 1 } ?: rawCandidates
        } else {
            rawCandidates
        }
        val selected = candidates.singleOrNull()
        val (male, female) = selected?.let { it.male to it.female }
            ?: resolvePlayerOverworldPairByStructure(rom)
            ?: return null
        val palettes = resolveObjectEventPalettes(rom, setOf(male.paletteTag, female.paletteTag)) ?: return null
        val maleSprite = renderOverworldFrame(rom, male, palettes.getValue(male.paletteTag)) ?: return null
        val femaleSprite = renderOverworldFrame(rom, female, palettes.getValue(female.paletteTag)) ?: return null
        return maleSprite to femaleSprite
    }

    private fun matchesFireRedPlayerGraphicsBlock(rom: RomImage, table: Int): Boolean =
        (0 until FIRERED_PLAYER_STATE_COUNT).all { state ->
            val maleInfo = rom.gbaPointer(table + state * 4)?.let { readOverworldShape(rom, it) }
                ?: return@all false
            val femaleInfo = rom.gbaPointer(table + (FIRERED_FEMALE_NORMAL_GRAPHICS_ID + state) * 4)
                ?.let { readOverworldShape(rom, it) }
                ?: return@all false
            maleInfo == femaleInfo
        }

    private fun readOverworldShape(rom: RomImage, info: Int): OverworldShape? = runCatching {
        require(info.toLong() + OBJECT_EVENT_INFO_BYTES <= rom.size.toLong())
        require(rom.u16le(info) == 0xFFFF)
        listOf(0x10, 0x14, 0x18, 0x1C, 0x20).forEach { requireNotNull(rom.gbaPointer(info + it)) }
        OverworldShape(
            size = rom.u16le(info + 6),
            width = rom.u16le(info + 8),
            height = rom.u16le(info + 10),
        )
    }.getOrNull()

    private fun resolvePlayerOverworldPairByStructure(rom: RomImage): Pair<NormalOverworldInfo, NormalOverworldInfo>? {
        val candidatesByInfo = linkedMapOf<Int, ReferencedOverworldInfo>()
        var reference = 0
        while (reference.toLong() + 4L <= rom.size.toLong()) {
            val infoOffset = rom.gbaPointer(reference)
            val info = infoOffset?.let { readNormalOverworldInfo(rom, it) }
            if (infoOffset != null && info != null) {
                candidatesByInfo.putIfAbsent(infoOffset, ReferencedOverworldInfo(reference, info))
            }
            reference += 4
        }
        val pairs = candidatesByInfo.values
            .groupBy { it.info.animationContract }
            .values
            .filter { group -> group.size == 2 && group.map { it.info.paletteTag }.distinct().size == 2 }
            .map { group -> group.sortedBy(ReferencedOverworldInfo::referenceOffset).map(ReferencedOverworldInfo::info) }
            .map { it[0] to it[1] }
        return pairs.singleOrNull()
    }

    private fun readNormalOverworldInfo(rom: RomImage, info: Int): NormalOverworldInfo? = runCatching {
        require(info.toLong() + OBJECT_EVENT_INFO_BYTES <= rom.size.toLong())
        require(rom.u16le(info) == 0xFFFF)
        val paletteTag = rom.u16le(info + 2)
        require(paletteTag != 0xFFFF)
        require(rom.u16le(info + 6) == NORMAL_OVERWORLD_SHEET_BYTES)
        val width = rom.u16le(info + 8)
        val height = rom.u16le(info + 10)
        require(width in OVERWORLD_WIDTHS)
        require(height == OVERWORLD_HEIGHT)
        val frameBytes = width / 8 * (height / 8) * GBA_4BPP_TILE_BYTES
        val oam = requireNotNull(rom.gbaPointer(info + 0x10))
        val subsprites = requireNotNull(rom.gbaPointer(info + 0x14))
        val anims = requireNotNull(rom.gbaPointer(info + 0x18))
        val images = requireNotNull(rom.gbaPointer(info + 0x1C))
        val affineAnims = requireNotNull(rom.gbaPointer(info + 0x20))
        val firstFrame = requireNotNull(rom.gbaPointer(images))
        val frameOffsets = mutableListOf<Int>()
        repeat(NORMAL_WALKING_FRAME_COUNT) { frame ->
            val entry = images + frame * SPRITE_FRAME_IMAGE_BYTES
            require(entry.toLong() + SPRITE_FRAME_IMAGE_BYTES <= rom.size.toLong())
            val frameData = requireNotNull(rom.gbaPointer(entry))
            frameOffsets += frameData
            require(frameData.toLong() + frameBytes <= rom.size.toLong())
            require(rom.u16le(entry + 4) == frameBytes)
        }
        NormalOverworldInfo(
            paletteTag = paletteTag,
            frameData = firstFrame,
            width = width,
            height = height,
            frameBytes = frameBytes,
            sequentialFrames = frameOffsets.withIndex().all { (index, offset) ->
                offset == firstFrame + index * frameBytes
            },
            animationContract = OverworldAnimationContract(oam, subsprites, anims, affineAnims),
        )
    }.getOrNull()

    private fun resolveObjectEventPalettes(rom: RomImage, tags: Set<Int>): Map<Int, ShortArray>? {
        val candidates = tags.associateWith { mutableListOf<PaletteRecord>() }
        var offset = 0
        while (offset <= rom.size - SPRITE_PALETTE_BYTES) {
            val tag = rom.u16le(offset + 4)
            if (tag in tags && rom.u16le(offset + 6) == 0) {
                rom.gbaPointer(offset)?.takeIf { it.toLong() + PALETTE_BYTES <= rom.size.toLong() }?.let { pointer ->
                    val colors = ShortArray(16) { index -> rom.u16le(pointer + index * 2).toShort() }
                    if (colors.map { it.toInt() and 0x7FFF }.distinct().size >= MIN_OVERWORLD_PALETTE_COLORS) {
                        candidates.getValue(tag).add(PaletteRecord(offset, pointer))
                    }
                }
            }
            offset += 4
        }
        return candidates.mapValues { (_, records) ->
            val scored = records.map { it to paletteTableRunLength(rom, it.record) }
            val bestScore = scored.maxOfOrNull { it.second } ?: return null
            if (bestScore < MIN_OBJECT_EVENT_PALETTE_TABLE_RECORDS) return null
            val pointer = scored.filter { it.second == bestScore }.map { it.first.pointer }.distinct().singleOrNull()
                ?: return null
            ShortArray(16) { index -> rom.u16le(pointer + index * 2).toShort() }
        }
    }

    private fun paletteTableRunLength(rom: RomImage, record: Int): Int {
        var first = record
        while (isObjectEventPaletteRecord(rom, first - SPRITE_PALETTE_BYTES)) {
            first -= SPRITE_PALETTE_BYTES
        }
        var count = 0
        var cursor = first
        while (isObjectEventPaletteRecord(rom, cursor)) {
            count++
            cursor += SPRITE_PALETTE_BYTES
        }
        return count
    }

    private fun isObjectEventPaletteRecord(rom: RomImage, record: Int): Boolean {
        if (record < 0 || record.toLong() + SPRITE_PALETTE_BYTES > rom.size.toLong()) return false
        val tag = rom.u16le(record + 4)
        if (tag !in OBJECT_EVENT_PALETTE_TAG_RANGE || rom.u16le(record + 6) != 0) return false
        val pointer = rom.gbaPointer(record) ?: return false
        return pointer.toLong() + PALETTE_BYTES <= rom.size.toLong()
    }

    private fun renderOverworldFrame(
        rom: RomImage,
        info: NormalOverworldInfo,
        palette: ShortArray,
    ): RgbaSprite? = runCatching {
        val indexed = TileRenderer.gba4Bpp(
            rom.slice(info.frameData, info.frameBytes),
            info.width / 8,
            info.height / 8,
        )
        require(indexed.indices.count { (it.toInt() and 0xFF) != 0 } >= MIN_OVERWORLD_OCCUPIED_PIXELS)
        TileRenderer.applyBgr555Palette(indexed, palette)
    }.getOrNull()

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
        val graphics = GbaRomCompression.decodeAt(
            rom,
            graphicsPointer,
            GbaDecodeContract.TRAINER_SPRITE,
        )
        require(graphics.size >= TRAINER_PIC_BYTES)
        val palettePointer = requireNotNull(rom.gbaPointer(paletteTable + index * TABLE_RECORD_BYTES))
        val paletteBytes = GbaRomCompression.decodeAt(rom, palettePointer, GbaDecodeContract.PALETTE)
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
                            runCatching {
                                GbaRomCompression.decodeAt(
                                    rom,
                                    candidate,
                                    GbaDecodeContract.TRAINER_SPRITE,
                                )
                            }.getOrNull()
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
    private const val HOENN_FEMALE_NORMAL_GRAPHICS_ID = 89
    private const val FIRERED_FEMALE_NORMAL_GRAPHICS_ID = 7
    private const val FIRERED_PLAYER_STATE_COUNT = 7
    private const val FIRERED_MALE_PLAYER_PALETTE_TAG = 0x1100
    private const val FIRERED_FEMALE_PLAYER_PALETTE_TAG = 0x1110
    private const val OBJECT_EVENT_INFO_BYTES = 0x24
    private const val SPRITE_FRAME_IMAGE_BYTES = 8
    private const val SPRITE_PALETTE_BYTES = 8
    private const val NORMAL_OVERWORLD_SHEET_BYTES = 512
    private const val OVERWORLD_HEIGHT = 32
    private const val GBA_4BPP_TILE_BYTES = 32
    private const val NORMAL_WALKING_FRAME_COUNT = 9
    private const val MIN_OVERWORLD_PALETTE_COLORS = 4
    private const val MIN_OVERWORLD_OCCUPIED_PIXELS = 48
    private const val MIN_OBJECT_EVENT_PALETTE_TABLE_RECORDS = 8
    private const val MALE_AVATAR_KEY = "trainer/avatar/male"
    private const val FEMALE_AVATAR_KEY = "trainer/avatar/female"
    private const val MALE_OVERWORLD_KEY = "trainer/overworld/male"
    private const val FEMALE_OVERWORLD_KEY = "trainer/overworld/female"

    private data class BadgeGraphicsBranch(val site: Int, val target: Int, val graphics: ByteArray)
    private data class NormalOverworldInfo(
        val paletteTag: Int,
        val frameData: Int,
        val width: Int,
        val height: Int,
        val frameBytes: Int,
        val sequentialFrames: Boolean,
        val animationContract: OverworldAnimationContract,
    )
    private data class OverworldAnimationContract(
        val oam: Int,
        val subsprites: Int,
        val anims: Int,
        val affineAnims: Int,
    )
    private data class ReferencedOverworldInfo(val referenceOffset: Int, val info: NormalOverworldInfo)
    private data class OverworldPairCandidate(
        val table: Int,
        val male: NormalOverworldInfo,
        val female: NormalOverworldInfo,
    )
    private data class OverworldShape(val size: Int, val width: Int, val height: Int)
    private data class PaletteRecord(val record: Int, val pointer: Int)

    private val OBJECT_EVENT_PALETTE_TAG_RANGE = 0x1100..0x11FF
    private val OVERWORLD_WIDTHS = setOf(16, 32)
}
