package com.enrpau.dualscreendex.parser.dataset.core.basestats

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

internal fun baseStatsSession(
    bytes: ByteArray,
    references: Map<Int, Int> = emptyMap(),
    limits: ResolutionLimits = ResolutionLimits(),
    exactLayout: BaseStatsTableLayout? = null,
    exactRecordSize: Int? = null,
    onReferenceIndexBuild: () -> Unit = {},
    useDefaultReferenceIndex: Boolean = false,
): RomAnalysisSession {
    val rom = RomImage(bytes)
    val exactProfile = exactLayout?.let { layout ->
        RomProfile(
            name = "exact base-stats fixture",
            sha256 = rom.sha256,
            crc32 = rom.crc32,
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            title = "BASE STATS TEST",
            revision = 0,
            romSize = rom.size,
            dexSpeciesCount = layout.count.toInt() - 1,
            internalSpeciesCount = layout.count.toInt(),
            moveCount = 1,
            tables = ProfileTables(
                baseStats = TableLayout(
                    offset = layout.offset.toInt(),
                    count = layout.count.toInt(),
                    recordSize = exactRecordSize ?: layout.abi.recordSize,
                ),
            ),
        )
    }
    return if (useDefaultReferenceIndex) {
        RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, "BASE STATS TEST"),
            exactProfile = exactProfile,
            limits = limits,
        )
    } else {
        RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, "BASE STATS TEST"),
            exactProfile = exactProfile,
            limits = limits,
            gbaReferenceIndexFactory = { _, _ ->
                onReferenceIndexBuild()
                GbaReferenceIndex.countsOnlyForTesting(references)
            },
        )
    }
}

internal fun putRetailBaseStats(
    bytes: ByteArray,
    offset: Int,
    stats: List<Int> = listOf(45, 49, 49, 45, 65, 65),
    types: Pair<Int, Int> = 12 to 3,
    abilities: Pair<Int, Int> = 7 to 9,
    growthRate: Int = 4,
) {
    require(stats.size == 6)
    stats.forEachIndexed { index, value -> bytes[offset + index] = value.toByte() }
    bytes[offset + 6] = types.first.toByte()
    bytes[offset + 7] = types.second.toByte()
    bytes[offset + 8] = 45
    bytes[offset + 9] = 64
    putU16(bytes, offset + 10, 0x056A)
    putU16(bytes, offset + 12, 13)
    putU16(bytes, offset + 14, 14)
    bytes[offset + 16] = 127
    bytes[offset + 17] = 20
    bytes[offset + 18] = 70
    bytes[offset + 19] = growthRate.toByte()
    bytes[offset + 20] = 1
    bytes[offset + 21] = 7
    bytes[offset + 22] = abilities.first.toByte()
    bytes[offset + 23] = abilities.second.toByte()
    bytes[offset + 24] = 5
    bytes[offset + 25] = (0x80 or 6).toByte()
}

internal fun putBattleEngineBaseStats(
    bytes: ByteArray,
    offset: Int,
    abilities: List<Int> = listOf(9, 31, 145),
) {
    require(abilities.size == 3)
    putRetailBaseStats(bytes, offset, abilities = 0 to 0)
    abilities.forEachIndexed { index, value -> putU16(bytes, offset + 22 + index * 2, value) }
    bytes[offset + 28] = 6
    bytes[offset + 29] = 4
}

internal fun putThumbLiteralReferences(
    bytes: ByteArray,
    instructionOffset: Int,
    literalOffset: Int,
    vararg targets: Int,
) {
    targets.forEachIndexed { index, target ->
        val instruction = instructionOffset + index * 2
        val literal = literalOffset + index * 4
        val pc = (instruction + 4) and -4
        putU16(bytes, instruction, 0x4800 or ((literal - pc) / 4))
        putU32(bytes, literal, 0x08000000 + target)
    }
}

internal fun putU16(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = value.toByte()
    bytes[offset + 1] = (value ushr 8).toByte()
}

private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
    repeat(4) { byte -> bytes[offset + byte] = (value ushr (byte * 8)).toByte() }
}
