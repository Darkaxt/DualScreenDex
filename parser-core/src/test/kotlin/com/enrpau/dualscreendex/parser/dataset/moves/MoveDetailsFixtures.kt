package com.enrpau.dualscreendex.parser.dataset.moves

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
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

internal fun moveDetailsSession(
    bytes: ByteArray,
    references: Map<Int, Int> = emptyMap(),
    limits: ResolutionLimits = ResolutionLimits(),
    exactLayout: MoveDetailsTableLayout? = null,
    exactRecordSize: Int? = null,
    exactFormat: TableRecordFormat? = null,
    referenceIndex: GbaReferenceIndex? = null,
    onReferenceIndexBuild: () -> Unit = {},
    useDefaultReferenceIndex: Boolean = false,
): RomAnalysisSession {
    val rom = RomImage(bytes)
    val exactProfile = exactLayout?.let { layout ->
        RomProfile(
            name = "exact move-details fixture",
            sha256 = rom.sha256,
            crc32 = rom.crc32,
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            title = "MOVE DETAILS TEST",
            revision = 0,
            romSize = rom.size,
            dexSpeciesCount = 1,
            internalSpeciesCount = 1,
            moveCount = layout.count.toInt(),
            tables = ProfileTables(
                moveData = TableLayout(
                    offset = layout.offset.toInt(),
                    count = layout.count.toInt(),
                    recordSize = exactRecordSize ?: layout.abi.recordSize,
                    format = exactFormat ?: layout.abi.tableRecordFormat,
                ),
            ),
        )
    }
    return if (useDefaultReferenceIndex) {
        RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, "MOVE DETAILS TEST"),
            exactProfile = exactProfile,
            limits = limits,
        )
    } else {
        RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, "MOVE DETAILS TEST"),
            exactProfile = exactProfile,
            limits = limits,
            gbaReferenceIndexFactory = { _, _ ->
                onReferenceIndexBuild()
                referenceIndex ?: GbaReferenceIndex.countsOnlyForTesting(references)
            },
        )
    }
}

internal fun putRetailMove(
    bytes: ByteArray,
    offset: Int,
    effect: Int = 43,
    power: Int = 60,
    type: Int = 1,
    accuracy: Int = 100,
    pp: Int = 35,
    secondaryChance: Int = 10,
    target: Int = 4,
    priority: Int = -1,
    flags: Long = 0xA1B2C3D4L,
) {
    bytes[offset] = effect.toByte()
    bytes[offset + 1] = power.toByte()
    bytes[offset + 2] = type.toByte()
    bytes[offset + 3] = accuracy.toByte()
    bytes[offset + 4] = pp.toByte()
    bytes[offset + 5] = secondaryChance.toByte()
    bytes[offset + 6] = target.toByte()
    bytes[offset + 7] = priority.toByte()
    putMoveU32(bytes, offset + 8, flags)
}

internal fun putCfruMove(
    bytes: ByteArray,
    offset: Int,
    effect: Int = 300,
    power: Int = 450,
    type: Int = 18,
    accuracy: Int = 0xFF,
    pp: Int = 5,
    secondaryChance: Int = 100,
    target: Int = 7,
    priority: Int = -2,
    split: Int = 1,
    flags: Long = 0x01020304L,
) {
    putMoveU16(bytes, offset, effect)
    putMoveU16(bytes, offset + 2, power)
    bytes[offset + 4] = type.toByte()
    bytes[offset + 5] = accuracy.toByte()
    bytes[offset + 6] = pp.toByte()
    bytes[offset + 7] = secondaryChance.toByte()
    bytes[offset + 8] = target.toByte()
    bytes[offset + 9] = priority.toByte()
    bytes[offset + 10] = split.toByte()
    bytes[offset + 11] = 0
    putMoveU32(bytes, offset + 12, flags)
}

internal fun putHybridBattleMove(
    bytes: ByteArray,
    offset: Int,
    effect: Int = 700,
    power: Int = 250,
    type: Int = 18,
    accuracy: Int = 100,
    pp: Int = 5,
    secondaryChance: Int = 30,
    target: Int = 0x1234,
    priority: Int = -3,
    flags: Long = 0x89ABCDEFL,
    split: Int = 2,
    argument: Int = 19,
) {
    putMoveU16(bytes, offset, effect)
    bytes[offset + 2] = power.toByte()
    bytes[offset + 3] = type.toByte()
    bytes[offset + 4] = accuracy.toByte()
    bytes[offset + 5] = pp.toByte()
    bytes[offset + 6] = secondaryChance.toByte()
    bytes[offset + 7] = 0
    putMoveU16(bytes, offset + 8, target)
    bytes[offset + 10] = priority.toByte()
    bytes[offset + 11] = 0
    putMoveU32(bytes, offset + 12, flags)
    bytes[offset + 16] = split.toByte()
    bytes[offset + 17] = argument.toByte()
    bytes[offset + 18] = 0
    bytes[offset + 19] = 0
}

internal fun putBattleEngineMove(
    bytes: ByteArray,
    offset: Int,
    effect: Int = 700,
    power: Int = 500,
    type: Int = 18,
    accuracy: Int = 100,
    pp: Int = 5,
    secondaryChance: Int = 30,
    target: Int = 0x1234,
    priority: Int = -3,
    flags: Long = 0x89ABCDEFL,
    split: Int = 2,
    argument: Int = 19,
    zMovePower: Int = 200,
    zMoveEffect: Int = 0x56,
) {
    putMoveU16(bytes, offset, effect)
    putMoveU16(bytes, offset + 2, power)
    bytes[offset + 4] = type.toByte()
    bytes[offset + 5] = accuracy.toByte()
    bytes[offset + 6] = pp.toByte()
    bytes[offset + 7] = secondaryChance.toByte()
    putMoveU16(bytes, offset + 8, target)
    bytes[offset + 10] = priority.toByte()
    bytes[offset + 11] = 0
    putMoveU32(bytes, offset + 12, flags)
    bytes[offset + 16] = split.toByte()
    bytes[offset + 17] = argument.toByte()
    bytes[offset + 18] = zMovePower.toByte()
    bytes[offset + 19] = zMoveEffect.toByte()
}

internal fun putUnifiedMoveInfo(
    bytes: ByteArray,
    offset: Int,
    effect: Int = 700,
    power: Int = 450,
    type: Int = 18,
    category: Int = 1,
    accuracy: Int = 100,
    target: Int = 0x123,
    pp: Int = 5,
    priority: Int = -3,
    flags: Long = 0x89ABCDEFL,
    argument: Long = 0x01020304L,
) {
    putMoveU16(bytes, offset + 8, effect)
    putMoveU16(bytes, offset + 10, type or (category shl 5) or (power shl 7))
    putMoveU16(bytes, offset + 12, accuracy or (target shl 7))
    bytes[offset + 14] = pp.toByte()
    putMoveU32(bytes, offset + 16, (flags and -16L) or (priority and 0xF).toLong())
    putMoveU32(bytes, offset + 24, argument)
}

internal fun putMoveThumbLiteralReferences(
    bytes: ByteArray,
    instructionOffset: Int,
    literalOffset: Int,
    vararg targets: Int,
) {
    targets.forEachIndexed { index, target ->
        val instruction = instructionOffset + index * 2
        val literal = literalOffset + index * 4
        val pc = (instruction + 4) and -4
        putMoveU16(bytes, instruction, 0x4800 or ((literal - pc) / 4))
        putMoveU32(bytes, literal, 0x08000000L + target)
    }
}

internal fun putMoveU16(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = value.toByte()
    bytes[offset + 1] = (value ushr 8).toByte()
}

private fun putMoveU32(bytes: ByteArray, offset: Int, value: Long) {
    repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
}
