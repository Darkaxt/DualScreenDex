package com.enrpau.dualscreendex.parser.dataset.learnsets

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndexFactory
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.RomProfile
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat

internal fun learnsetSession(
    bytes: ByteArray,
    references: Map<Int, Int> = emptyMap(),
    limits: ResolutionLimits = ResolutionLimits(),
    exactLayout: LearnsetTableLayout? = null,
    exactLegacyTable: TableLayout? = null,
    exactMoveCount: Int = 800,
    onReferenceIndexBuild: () -> Unit = {},
    useDefaultReferenceIndex: Boolean = false,
    referenceIndexOverride: GbaReferenceIndex? = null,
): RomAnalysisSession {
    val rom = RomImage(bytes)
    val header = RomHeader(Platform.GBA, "LEARNSETS", "BPEE")
    val profile = exactLayout?.let { layout ->
        RomProfile(
            name = "exact-learnset-fixture",
            sha256 = rom.sha256,
            crc32 = rom.crc32,
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            title = "LEARNSETS",
            gameCode = "BPEE",
            revision = 0,
            romSize = rom.size,
            dexSpeciesCount = layout.speciesCount,
            internalSpeciesCount = layout.speciesCount,
            moveCount = exactMoveCount,
            tables = ProfileTables(learnsets = exactLegacyTable ?: layout.toLegacyTable()),
        )
    }
    return if (useDefaultReferenceIndex) {
        RomAnalysisSession(rom, header, profile, limits)
    } else {
        RomAnalysisSession(
            rom,
            header,
            profile,
            limits,
            GbaReferenceIndexFactory { _, _ ->
                onReferenceIndexBuild()
                referenceIndexOverride ?: GbaReferenceIndex.countsOnlyForTesting(references)
            },
        )
    }
}

internal fun LearnsetTableLayout.toLegacyTable(): TableLayout = TableLayout(
    offset = offset.toInt(),
    count = speciesCount,
    recordSize = 4,
    variableLength = true,
    elementSize = format.entrySize,
    format = when (format) {
        is LearnsetFormat.PackedU16 -> TableRecordFormat.GEN3_PACKED_U16
        LearnsetFormat.LevelU8MoveU16 -> TableRecordFormat.GEN3_LEVEL_U8_MOVE_U16
        LearnsetFormat.MoveU16LevelU8 -> TableRecordFormat.GEN3_MOVE_U16_LEVEL_U8
        LearnsetFormat.MoveU16LevelU16 -> TableRecordFormat.GEN3_MOVE_U16_LEVEL_U16
    },
)

internal fun putU16(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = value.toByte()
    bytes[offset + 1] = (value ushr 8).toByte()
}

internal fun putU32(bytes: ByteArray, offset: Int, value: Int) {
    repeat(4) { byte -> bytes[offset + byte] = (value ushr (byte * 8)).toByte() }
}

internal fun putPointer(bytes: ByteArray, cell: Int, target: Int) =
    putU32(bytes, cell, 0x08000000 + target)

internal fun putPacked(
    bytes: ByteArray,
    target: Int,
    entries: List<LearnsetEntryValue>,
    moveBits: Int = 10,
) {
    entries.forEachIndexed { index, entry ->
        putU16(bytes, target + index * 2, (entry.level shl moveBits) or entry.moveId)
    }
    putU16(bytes, target + entries.size * 2, 0xFFFF)
}

internal fun putLevelMove(bytes: ByteArray, target: Int, entries: List<LearnsetEntryValue>) {
    entries.forEachIndexed { index, entry ->
        val cursor = target + index * 3
        bytes[cursor] = entry.level.toByte()
        putU16(bytes, cursor + 1, entry.moveId)
    }
    bytes[target + entries.size * 3] = 0xFE.toByte()
}

internal fun putMoveLevel(bytes: ByteArray, target: Int, entries: List<LearnsetEntryValue>) {
    entries.forEachIndexed { index, entry ->
        val cursor = target + index * 3
        putU16(bytes, cursor, entry.moveId)
        bytes[cursor + 2] = entry.level.toByte()
    }
    val terminator = target + entries.size * 3
    putU16(bytes, terminator, 0)
    bytes[terminator + 2] = 0xFF.toByte()
}

internal fun putWide(bytes: ByteArray, target: Int, entries: List<LearnsetEntryValue>) {
    entries.forEachIndexed { index, entry ->
        val cursor = target + index * 4
        putU16(bytes, cursor, entry.moveId)
        putU16(bytes, cursor + 2, entry.level)
    }
    putU16(bytes, target + entries.size * 4, 0xFFFF)
}

internal fun putThumbLiteralReference(
    bytes: ByteArray,
    instructionOffset: Int,
    literalOffset: Int,
    target: Int,
) {
    val pc = (instructionOffset + 4) and -4
    putU16(bytes, instructionOffset, 0x4800 or ((literalOffset - pc) / 4))
    putU32(bytes, literalOffset, 0x08000000 + target)
}
