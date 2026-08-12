package com.enrpau.dualscreendex.parser.dataset.descriptions

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

internal fun descriptionSession(
    bytes: ByteArray,
    references: Map<Int, Int> = emptyMap(),
    exact: Boolean = false,
    exactDescriptionLayout: DescriptionTableLayout? = null,
    limits: ResolutionLimits = ResolutionLimits(),
    referenceIndexOverride: GbaReferenceIndex? = null,
    onReferenceIndexBuild: () -> Unit = {},
    useDefaultReferenceIndex: Boolean = false,
): RomAnalysisSession {
    val rom = RomImage(bytes)
    val exactProfile = RomProfile(
        name = "exact fixture",
        sha256 = rom.sha256,
        crc32 = rom.crc32,
        family = EngineFamily.EMERALD,
        platform = Platform.GBA,
        title = "DESCRIPTION TEST",
        revision = 0,
        romSize = rom.size,
        dexSpeciesCount = 1,
        internalSpeciesCount = 1,
        moveCount = 1,
        tables = ProfileTables(
            descriptions = exactDescriptionLayout?.let {
                TableLayout(
                    offset = it.offset.toInt(),
                    count = it.count.toInt(),
                    recordSize = it.recordSize,
                    pointerOffsets = it.pointerOffsets,
                )
            },
        ),
    ).takeIf { exact }
    if (useDefaultReferenceIndex) {
        return RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, "DESCRIPTION TEST"),
            exactProfile = exactProfile,
            limits = limits,
        )
    }
    return RomAnalysisSession(
        rom = rom,
        header = RomHeader(Platform.GBA, "DESCRIPTION TEST"),
        exactProfile = exactProfile,
        limits = limits,
        gbaReferenceIndexFactory = { _, _ ->
            onReferenceIndexBuild()
            referenceIndexOverride ?: GbaReferenceIndex.countsOnlyForTesting(references)
        },
    )
}

internal fun putDescriptionTable(
    bytes: ByteArray,
    offset: Int,
    count: Int,
    recordSize: Int,
    pointerOffsets: List<Int>,
    textOffset: Int,
) {
    repeat(count) { row ->
        val record = offset + row * recordSize
        putGbaText(bytes, record, if (row == 0) "UNKNOWN" else "ENTRY")
        putU16(bytes, record + 12, if (row == 0) 0 else 7)
        putU16(bytes, record + 14, if (row == 0) 0 else 69)
        pointerOffsets.forEachIndexed { page, pointerOffset ->
            val target = textOffset + (row * pointerOffsets.size + page) * 0x20
            putU32(bytes, record + pointerOffset, 0x08000000 + target)
            putGbaText(bytes, target, "POKEMON TEXT")
        }
    }
}

internal fun putGbaText(bytes: ByteArray, offset: Int, value: String) {
    value.forEachIndexed { index, character ->
        bytes[offset + index] = when (character) {
            ' ' -> 0
            in 'A'..'Z' -> 0xBB + (character - 'A')
            else -> error("unsupported fixture character $character")
        }.toByte()
    }
    bytes[offset + value.length] = 0xFF.toByte()
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
