package com.enrpau.dualscreendex.parser.dataset.evolutions

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.RomProfile
import com.enrpau.dualscreendex.parser.model.TableLayout

internal fun evolutionSession(
    bytes: ByteArray,
    references: Map<Int, Int> = emptyMap(),
    limits: ResolutionLimits = ResolutionLimits(),
    useDefaultReferenceIndex: Boolean = false,
    exactLayout: EvolutionTableLayout? = null,
    onReferenceIndexBuild: () -> Unit = {},
    referenceIndexOverride: GbaReferenceIndex? = null,
): RomAnalysisSession {
    val rom = RomImage(bytes)
    val exactProfile = exactLayout?.let { layout ->
        RomProfile(
            name = "exact evolution fixture",
            sha256 = rom.sha256,
            crc32 = rom.crc32,
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            title = "EVOLUTION TEST",
            revision = 0,
            romSize = rom.size,
            dexSpeciesCount = layout.count.toInt(),
            internalSpeciesCount = layout.count.toInt(),
            moveCount = 1,
            tables = ProfileTables(
                evolutions = TableLayout(
                    offset = layout.offset.toInt(),
                    count = layout.count.toInt(),
                    recordSize = layout.rowStride.toInt(),
                    elementSize = layout.recordSize,
                ),
            ),
        )
    }
    return if (useDefaultReferenceIndex) {
        RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, "EVOLUTION TEST"),
            exactProfile = exactProfile,
            limits = limits,
        )
    } else {
        RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, "EVOLUTION TEST"),
            exactProfile = exactProfile,
            limits = limits,
            gbaReferenceIndexFactory = { _, _ ->
                onReferenceIndexBuild()
                referenceIndexOverride ?: GbaReferenceIndex.countsOnlyForTesting(references)
            },
        )
    }
}

internal fun putEvolution(
    bytes: ByteArray,
    offset: Int,
    method: Int,
    parameter: Int,
    target: Int,
    condition: Int? = null,
) {
    putU16(bytes, offset, method)
    putU16(bytes, offset + 2, parameter)
    putU16(bytes, offset + 4, target)
    condition?.let { putU16(bytes, offset + 6, it) }
}

internal fun putU16(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = value.toByte()
    bytes[offset + 1] = (value ushr 8).toByte()
}

internal fun putU32(bytes: ByteArray, offset: Int, value: Int) {
    repeat(4) { byte -> bytes[offset + byte] = (value ushr (byte * 8)).toByte() }
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
