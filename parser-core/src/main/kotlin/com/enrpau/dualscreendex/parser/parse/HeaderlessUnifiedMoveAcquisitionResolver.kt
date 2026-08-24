package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.HeaderlessUnifiedMoveAcquisitionMetadata

/** Resolves compiled-authorized move-list pointers in a headerless unified species ABI. */
internal object HeaderlessUnifiedMoveAcquisitionResolver {
    private const val TEACHABLE_POINTER_OFFSET = 152
    private const val EGG_MOVE_POINTER_OFFSET = 156
    private const val LIST_TERMINATOR = 0xFFFF
    private const val MAXIMUM_MOVE_ID = 4095
    private const val MAXIMUM_LIST_ENTRIES = 1024

    fun resolve(
        session: RomAnalysisSession,
        speciesRoot: Int,
        speciesCount: Int,
        speciesRecordSize: Int,
        activePredicateOffset: Int,
    ): HeaderlessUnifiedMoveAcquisitionMetadata? {
        if (EGG_MOVE_POINTER_OFFSET + 4 > speciesRecordSize) return null
        val index = session.gbaReferenceIndex ?: return null
        if (index.overflowed) return null
        val indexed = index.targets[speciesRoot]
        val references = if (indexed?.siteEvidenceAvailable == true && indexed.instructionSites.isNotEmpty()) {
            indexed
        } else {
            session.nominatedGbaReferenceSites(speciesRoot)
        }?.takeIf { it.siteEvidenceAvailable } ?: return null

        fun resolveField(fieldOffset: Int): Int? {
            val hasGetter = references.instructionSites.any { site ->
                hasCompletePointerGetter(
                    rom = session.rom,
                    site = site,
                    speciesRoot = speciesRoot,
                    speciesCount = speciesCount,
                    speciesRecordSize = speciesRecordSize,
                    activePredicateOffset = activePredicateOffset,
                    fieldOffset = fieldOffset,
                )
            }
            return fieldOffset.takeIf {
                hasGetter && validateLists(
                    rom = session.rom,
                    speciesRoot = speciesRoot,
                    speciesCount = speciesCount,
                    speciesRecordSize = speciesRecordSize,
                    activePredicateOffset = activePredicateOffset,
                    fieldOffset = fieldOffset,
                )
            }
        }

        val teachable = resolveField(TEACHABLE_POINTER_OFFSET)
        val eggMoves = resolveField(EGG_MOVE_POINTER_OFFSET)
        if (teachable == null && eggMoves == null) return null
        return HeaderlessUnifiedMoveAcquisitionMetadata(
            teachablePointerOffset = teachable,
            eggMovePointerOffset = eggMoves,
        )
    }

    private fun validateLists(
        rom: RomImage,
        speciesRoot: Int,
        speciesCount: Int,
        speciesRecordSize: Int,
        activePredicateOffset: Int,
        fieldOffset: Int,
    ): Boolean = runCatching {
        val fallback = rom.gbaPointer(speciesRoot + fieldOffset) ?: return@runCatching false
        var populatedRows = 0
        repeat(speciesCount) { speciesId ->
            val record = speciesRoot + speciesId * speciesRecordSize
            if (rom.u8(record + activePredicateOffset) == 0) return@repeat
            val raw = rom.u32le(record + fieldOffset)
            val list = if (raw == 0L) fallback else rom.gbaPointer(record + fieldOffset) ?: return@runCatching false
            val entries = terminatedListSize(rom, list) ?: return@runCatching false
            if (entries > 0) populatedRows++
        }
        populatedRows > 0
    }.getOrDefault(false)

    private fun terminatedListSize(rom: RomImage, offset: Int): Int? {
        repeat(MAXIMUM_LIST_ENTRIES) { index ->
            val entryOffset = offset.toLong() + index * 2L
            if (entryOffset + 2 > rom.size.toLong()) return null
            val moveId = rom.u16le(entryOffset.toInt())
            if (moveId == LIST_TERMINATOR) return index
            if (moveId !in 1..MAXIMUM_MOVE_ID) return null
        }
        return null
    }

    /**
     * Complete Thumb leaf: bound and normalize species, compute `id * recordSize`, test the
     * active byte, load the field, substitute row zero for null, and return.
     */
    private fun hasCompletePointerGetter(
        rom: RomImage,
        site: Int,
        speciesRoot: Int,
        speciesCount: Int,
        speciesRecordSize: Int,
        activePredicateOffset: Int,
        fieldOffset: Int,
    ): Boolean {
        if (speciesRecordSize != 260 || activePredicateOffset != 0 || site !in 14 until rom.size - 28) {
            return false
        }
        val rootLoad = rom.u16le(site)
        if (rootLoad and 0xF800 != 0x4800 || literalValue(rom, site) != 0x08000000L + speciesRoot) {
            return false
        }
        val rootRegister = rootLoad ushr 8 and 0x7
        val indexShift = rom.u16le(site - 2)
        val speciesRegister = indexShift ushr 3 and 0x7
        val indexRegister = indexShift and 0x7
        if (!isShiftLeft(indexShift, indexRegister, speciesRegister, 6)) return false
        if (!hasSpeciesBound(rom, site, speciesRegister, speciesCount)) return false

        val addIndex = rom.u16le(site + 2)
        if (!isAddRegisters(addIndex, indexRegister, indexRegister, speciesRegister)) return false
        if (!isShiftLeft(rom.u16le(site + 4), indexRegister, indexRegister, 2)) return false

        val activeLoad = rom.u16le(site + 6)
        if (activeLoad and 0xFE00 != 0x5C00) return false
        val activeRegister = activeLoad and 0x7
        val activeBase = activeLoad ushr 3 and 0x7
        val activeOffset = activeLoad ushr 6 and 0x7
        if (setOf(activeBase, activeOffset) != setOf(rootRegister, indexRegister)) return false
        if (!isCompareImmediate(rom.u16le(site + 8), activeRegister, 0)) return false
        if (rom.u16le(site + 10) and 0xFF00 != 0xD100) return false

        val fallbackLoad = rom.u16le(site + 12)
        val returnRegister = fallbackLoad ushr 8 and 0x7
        if (fallbackLoad and 0xF800 != 0x4800 || returnRegister != 0) return false
        if (literalValue(rom, site + 12) != rom.u32le(speciesRoot + fieldOffset)) return false
        if (rom.u16le(site + 14) != 0x4770) return false

        if (!isAddRegisters(rom.u16le(site + 16), rootRegister, rootRegister, indexRegister)) return false
        val fieldAdd = rom.u16le(site + 18)
        if (fieldAdd and 0xF800 != 0x3000 || fieldAdd ushr 8 and 0x7 != rootRegister) return false
        if (fieldAdd and 0xFF != fieldOffset) return false

        val pointerLoad = rom.u16le(site + 20)
        if (pointerLoad and 0xF800 != 0x6800) return false
        if (pointerLoad ushr 6 and 0x1F != 0) return false
        if (pointerLoad ushr 3 and 0x7 != rootRegister || pointerLoad and 0x7 != returnRegister) return false
        if (!isCompareImmediate(rom.u16le(site + 22), returnRegister, 0)) return false
        if (rom.u16le(site + 24) and 0xFF00 != 0xD100) return false
        return rom.u16le(site + 26) and 0xF800 == 0xE000
    }

    private fun hasSpeciesBound(rom: RomImage, site: Int, speciesRegister: Int, speciesCount: Int): Boolean {
        val compare = rom.u16le(site - 6)
        val boundRegister = compare ushr 3 and 0x7
        if (compare and 0xFFC0 != 0x4280 || compare and 0x7 != speciesRegister) return false
        val branch = rom.u16le(site - 4)

        val inclusiveLoad = rom.u16le(site - 12)
        if (inclusiveLoad and 0xF800 == 0x4800 && inclusiveLoad ushr 8 and 0x7 == boundRegister) {
            val normalizeLeft = rom.u16le(site - 10)
            val normalizeRight = rom.u16le(site - 8)
            return isShiftLeft(normalizeLeft, speciesRegister, speciesRegister, 16) &&
                isShiftRight(normalizeRight, speciesRegister, speciesRegister, 16) &&
                branch and 0xFF00 == 0xD800 &&
                literalValue(rom, site - 12) == speciesCount.toLong() - 1
        }

        val boundMove = rom.u16le(site - 14)
        val normalizeLeft = rom.u16le(site - 12)
        val normalizeRight = rom.u16le(site - 10)
        val boundShift = rom.u16le(site - 8)
        if (!isShiftLeft(normalizeLeft, speciesRegister, speciesRegister, 16) ||
            !isShiftRight(normalizeRight, speciesRegister, speciesRegister, 16)
        ) {
            return false
        }
        if (boundMove and 0xF800 != 0x2000 || boundMove ushr 8 and 0x7 != boundRegister) return false
        val shiftAmount = boundShift ushr 6 and 0x1F
        if (!isShiftLeft(boundShift, boundRegister, boundRegister, shiftAmount)) return false
        val encodedCount = (boundMove and 0xFF) shl shiftAmount
        return branch and 0xFF00 == 0xD200 && encodedCount == speciesCount
    }

    private fun isShiftLeft(instruction: Int, destination: Int, source: Int, amount: Int): Boolean =
        instruction and 0xF800 == 0x0000 &&
            instruction and 0x7 == destination &&
            instruction ushr 3 and 0x7 == source &&
            instruction ushr 6 and 0x1F == amount

    private fun isShiftRight(instruction: Int, destination: Int, source: Int, amount: Int): Boolean =
        instruction and 0xF800 == 0x0800 &&
            instruction and 0x7 == destination &&
            instruction ushr 3 and 0x7 == source &&
            instruction ushr 6 and 0x1F == amount

    private fun isAddRegisters(instruction: Int, destination: Int, left: Int, right: Int): Boolean {
        if (instruction and 0xFE00 != 0x1800 || instruction and 0x7 != destination) return false
        val operands = setOf(instruction ushr 3 and 0x7, instruction ushr 6 and 0x7)
        return operands == setOf(left, right)
    }

    private fun isCompareImmediate(instruction: Int, register: Int, value: Int): Boolean =
        instruction and 0xF800 == 0x2800 && instruction ushr 8 and 0x7 == register && instruction and 0xFF == value

    private fun literalValue(rom: RomImage, site: Int): Long? {
        val instruction = rom.u16le(site)
        if (instruction and 0xF800 != 0x4800) return null
        val literal = ((site + 4) and 3.inv()) + (instruction and 0xFF) * 4
        return literal.takeIf { it in 0..rom.size - 4 }?.let(rom::u32le)
    }
}
