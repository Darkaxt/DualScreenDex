package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ExpandedSplitCaptureBallMetadata

/** Resolves expanded split capture-ball assets and their item IDs from compiled consumers. */
internal object ExpandedSplitCaptureBallResolver {
    fun resolve(session: RomAnalysisSession): ExpandedSplitCaptureBallMetadata? {
        val index = session.gbaReferenceIndex ?: return null
        if (index.overflowed) return null

        val splitTables = index.targets.keys.mapNotNull { sheetRoot ->
            val ballCount = sheetTableCount(session.rom, sheetRoot) ?: return@mapNotNull null
            referenceSites(session, sheetRoot).mapNotNull { site ->
                val paletteRoot = matchingPaletteRoot(session.rom, site, sheetRoot) ?: return@mapNotNull null
                if (!validPaletteTable(session.rom, paletteRoot, sheetRoot, ballCount)) return@mapNotNull null
                SplitTables(sheetRoot, paletteRoot, ballCount)
            }.singleOrNull()
        }.distinct()
        if (splitTables.isEmpty()) return null

        val itemTables = index.targets.keys.mapNotNull { root ->
            val itemCount = referenceSites(session, root).mapNotNull { site ->
                matchingSecondaryIdGetter(session.rom, site, root)
            }.distinct().singleOrNull() ?: return@mapNotNull null
            if (!hasByteFieldGetter(session, root, itemCount, ITEM_POCKET_OFFSET) ||
                !hasByteFieldGetter(session, root, itemCount, ITEM_TYPE_OFFSET)
            ) {
                return@mapNotNull null
            }
            ItemTable(root, itemCount)
        }.distinct()

        return splitTables.flatMap { split ->
            itemTables.mapNotNull { items ->
                val itemIds = resolveItemIds(session.rom, items, split.ballCount) ?: return@mapNotNull null
                ExpandedSplitCaptureBallMetadata(
                    sheetTableOffset = split.sheetRoot,
                    paletteTableOffset = split.paletteRoot,
                    ballCount = split.ballCount,
                    itemIdsByBallIndex = itemIds,
                )
            }
        }.distinct().singleOrNull()
    }

    private fun sheetTableCount(rom: RomImage, root: Int): Int? {
        if (root !in 0..rom.size - SPLIT_ENTRY_SIZE) return null
        val tagBase = rom.u16le(root + 6)
        var count = 0
        while (count < MAXIMUM_BALL_COUNT) {
            val entry = root.toLong() + count * SPLIT_ENTRY_SIZE.toLong()
            if (entry + SPLIT_ENTRY_SIZE > rom.size.toLong()) break
            val offset = entry.toInt()
            if (rom.u16le(offset + 4) != SHEET_SIZE || rom.u16le(offset + 6) != tagBase + count) break
            count++
        }
        if (count !in MINIMUM_EXPANDED_BALL_COUNT until MAXIMUM_BALL_COUNT) return null
        return count
    }

    private fun validPaletteTable(rom: RomImage, root: Int, sheetRoot: Int, ballCount: Int): Boolean {
        if (root !in 0..rom.size - ballCount * SPLIT_ENTRY_SIZE) return false
        val tagBase = rom.u16le(sheetRoot + 6)
        return (0 until ballCount).all { index ->
            rom.u16le(root + index * SPLIT_ENTRY_SIZE + 4) == tagBase + index
        }
    }

    /**
     * Complete Thumb leaf: normalize the ball index to u8, multiply it by eight, load the sheet
     * tag, then use the same index to load the palette tag before returning.
     */
    private fun matchingPaletteRoot(rom: RomImage, site: Int, sheetRoot: Int): Int? {
        if (site !in 2 until rom.size - 30 || literalValue(rom, site) != GBA_ROM_BASE + sheetRoot) return null
        if (rom.u16le(site - 2) != 0xB510 ||
            rom.u16le(site + 2) != 0x0604 ||
            rom.u16le(site + 4) != 0x0D64 ||
            rom.u16le(site + 6) != 0x191B ||
            rom.u16le(site + 8) != 0x88D8 ||
            !isThumbCall(rom, site + 10) ||
            rom.u16le(site + 14) and 0xF800 != 0x4800 ||
            rom.u16le(site + 16) != 0x191B ||
            rom.u16le(site + 18) != 0x8898 ||
            !isThumbCall(rom, site + 20) ||
            rom.u16le(site + 24) != 0xBC10 ||
            rom.u16le(site + 26) != 0xBC01 ||
            rom.u16le(site + 28) != 0x4700
        ) {
            return null
        }
        val raw = literalValue(rom, site + 14) ?: return null
        return (raw - GBA_ROM_BASE).takeIf { it in 0 until rom.size.toLong() }?.toInt()
    }

    /** Complete bounded Thumb getter for `items[itemId].secondaryId`. */
    private fun matchingSecondaryIdGetter(rom: RomImage, site: Int, itemRoot: Int): Int? {
        if (site !in 16 until rom.size - 10 || literalValue(rom, site) != GBA_ROM_BASE + itemRoot) return null
        if (rom.u16le(site - 2) != 0x0099 ||
            rom.u16le(site + 2) != 0x18CB ||
            rom.u16le(site + 4) != 0x011B ||
            rom.u16le(site + 6) != 0x18D2 ||
            rom.u16le(site + 8) != 0x8890 ||
            rom.u16le(site + 10) != 0x4770
        ) {
            return null
        }
        val itemCount = matchingItemBound(rom, site) ?: return null
        val end = itemRoot.toLong() + itemCount * ITEM_RECORD_SIZE.toLong()
        return itemCount.takeIf { it in MINIMUM_ITEM_COUNT..MAXIMUM_ITEM_COUNT && end <= rom.size.toLong() }
    }

    private fun matchingItemBound(rom: RomImage, rootLoadSite: Int): Int? {
        val immediateBound = rom.u16le(rootLoadSite - 16)
        if (immediateBound and 0xF800 == 0x2000 && immediateBound ushr 8 and 0x7 == 2 &&
            rom.u16le(rootLoadSite - 14) == 0x0403 &&
            rom.u16le(rootLoadSite - 12) and 0xF800 == 0x0000 &&
            rom.u16le(rootLoadSite - 10) == 0x2000 &&
            rom.u16le(rootLoadSite - 8) == 0x0C1B &&
            rom.u16le(rootLoadSite - 6) == 0x4293 &&
            rom.u16le(rootLoadSite - 4) and 0xFF00 == 0xD200
        ) {
            val boundRegister = immediateBound ushr 8 and 0x7
            val shift = rom.u16le(rootLoadSite - 12)
            if (shift and 0x7 == boundRegister && shift ushr 3 and 0x7 == boundRegister) {
                return (immediateBound and 0xFF) shl (shift ushr 6 and 0x1F)
            }
        }

        val literalBound = rom.u16le(rootLoadSite - 14)
        if (literalBound and 0xF800 == 0x4800 && literalBound ushr 8 and 0x7 == 2 &&
            rom.u16le(rootLoadSite - 12) == 0x0403 &&
            rom.u16le(rootLoadSite - 10) == 0x0C1B &&
            rom.u16le(rootLoadSite - 8) == 0x2000 &&
            rom.u16le(rootLoadSite - 6) == 0x4293 &&
            rom.u16le(rootLoadSite - 4) and 0xFF00 == 0xD800
        ) {
            val inclusiveBound = literalValue(rom, rootLoadSite - 14) ?: return null
            return (inclusiveBound + 1).takeIf { it <= Int.MAX_VALUE }?.toInt()
        }
        return null
    }

    private fun hasByteFieldGetter(
        session: RomAnalysisSession,
        itemRoot: Int,
        itemCount: Int,
        fieldOffset: Int,
    ): Boolean {
        val target = itemRoot + fieldOffset
        return referenceSites(session, target).any { site ->
            matchingByteFieldGetter(session.rom, site, target, itemCount)
        }
    }

    private fun matchingByteFieldGetter(rom: RomImage, site: Int, target: Int, itemCount: Int): Boolean {
        if (site !in 18 until rom.size - 6 || literalValue(rom, site) != GBA_ROM_BASE + target) return false
        if (rom.u16le(site - 4) != 0x009A ||
            rom.u16le(site - 2) != 0x18D3 ||
            rom.u16le(site + 2) != 0x011B ||
            rom.u16le(site + 4) != 0x5C98 ||
            rom.u16le(site + 6) != 0x4770
        ) {
            return false
        }

        val immediateBound = rom.u16le(site - 18)
        if (immediateBound and 0xF800 == 0x2000 && immediateBound ushr 8 and 0x7 == 2 &&
            rom.u16le(site - 16) == 0x0403 &&
            rom.u16le(site - 14) == 0x0C1B &&
            rom.u16le(site - 10) and 0xF800 == 0x0000 &&
            rom.u16le(site - 8) == 0x4293 &&
            rom.u16le(site - 6) and 0xFF00 == 0xD200
        ) {
            val boundRegister = immediateBound ushr 8 and 0x7
            val shift = rom.u16le(site - 10)
            val encoded = (immediateBound and 0xFF) shl (shift ushr 6 and 0x1F)
            if (shift and 0x7 == boundRegister && shift ushr 3 and 0x7 == boundRegister && encoded == itemCount) {
                return true
            }
        }

        val literalBound = rom.u16le(site - 16)
        return literalBound and 0xF800 == 0x4800 && literalBound ushr 8 and 0x7 == 2 &&
            rom.u16le(site - 14) == 0x0403 &&
            rom.u16le(site - 12) == 0x0C1B &&
            rom.u16le(site - 8) == 0x4293 &&
            rom.u16le(site - 6) and 0xFF00 == 0xD800 &&
            literalValue(rom, site - 16) == itemCount.toLong() - 1
    }

    private fun resolveItemIds(rom: RomImage, table: ItemTable, ballCount: Int): List<Int>? {
        val groups = (1 until table.itemCount).groupBy { itemId ->
            val record = table.root + itemId * ITEM_RECORD_SIZE
            rom.u8(record + ITEM_POCKET_OFFSET) to rom.u8(record + ITEM_TYPE_OFFSET)
        }
        return groups.filterKeys { (pocket, type) -> pocket != 0 && type != 0 }
            .values
            .mapNotNull { itemIds ->
                if (itemIds.size != ballCount) return@mapNotNull null
                val byBallIndex = itemIds.groupBy { itemId ->
                    rom.u16le(table.root + itemId * ITEM_RECORD_SIZE + ITEM_SECONDARY_ID_OFFSET)
                }
                if (byBallIndex.keys != (0 until ballCount).toSet() || byBallIndex.values.any { it.size != 1 }) {
                    return@mapNotNull null
                }
                (0 until ballCount).map { index -> byBallIndex.getValue(index).single() }
            }
            .distinct()
            .singleOrNull()
    }

    private fun referenceSites(session: RomAnalysisSession, target: Int): List<Int> {
        val indexed = session.gbaReferenceIndex?.target(target) ?: return emptyList()
        val evidence: GbaTargetReferenceEvidence = if (indexed.siteEvidenceAvailable) {
            indexed
        } else {
            session.nominatedGbaReferenceSites(target)
        } ?: return emptyList()
        return evidence.instructionSites.takeIf { evidence.siteEvidenceAvailable }.orEmpty()
    }

    private fun isThumbCall(rom: RomImage, site: Int): Boolean =
        rom.u16le(site) and 0xF800 == 0xF000 && rom.u16le(site + 2) and 0xF800 == 0xF800

    private fun literalValue(rom: RomImage, site: Int): Long? {
        val instruction = rom.u16le(site)
        if (instruction and 0xF800 != 0x4800) return null
        val literal = ((site + 4) and 3.inv()) + (instruction and 0xFF) * 4
        return literal.takeIf { it in 0..rom.size - 4 }?.let(rom::u32le)
    }

    private data class SplitTables(val sheetRoot: Int, val paletteRoot: Int, val ballCount: Int)
    private data class ItemTable(val root: Int, val itemCount: Int)

    private const val GBA_ROM_BASE = 0x08000000L
    private const val SPLIT_ENTRY_SIZE = 8
    private const val SHEET_SIZE = 384
    private const val MINIMUM_EXPANDED_BALL_COUNT = 13
    private const val MAXIMUM_BALL_COUNT = 64
    private const val ITEM_RECORD_SIZE = 80
    private const val ITEM_SECONDARY_ID_OFFSET = 4
    private const val ITEM_POCKET_OFFSET = 65
    private const val ITEM_TYPE_OFFSET = 66
    private const val MINIMUM_ITEM_COUNT = 64
    private const val MAXIMUM_ITEM_COUNT = 4096
}
