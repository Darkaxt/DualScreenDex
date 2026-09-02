package com.enrpau.dualscreendex.parser.dataset.abilities

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.RomProfile
import com.enrpau.dualscreendex.parser.model.TableLayout

internal fun abilitySession(
    bytes: ByteArray,
    references: Map<Int, Int> = emptyMap(),
    limits: ResolutionLimits = ResolutionLimits(),
    exactLayout: AbilityNameTableLayout? = null,
    exactTableOverride: TableLayout? = null,
    referenceIndexOverride: GbaReferenceIndex? = null,
    onReferenceIndexBuild: () -> Unit = {},
    useDefaultReferenceIndex: Boolean = false,
    cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
): RomAnalysisSession {
    val rom = RomImage(bytes)
    val exactTable = exactTableOverride ?: exactLayout?.let { layout ->
        TableLayout(
            offset = layout.offset.toInt(),
            count = layout.count.toInt(),
            recordSize = layout.nameWidth,
            stride = layout.stride.takeIf { it != layout.nameWidth },
        )
    }
    val exactProfile = exactTable?.let { table ->
        RomProfile(
            name = "exact ability fixture",
            sha256 = rom.sha256,
            crc32 = rom.crc32,
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            title = "ABILITY TEST",
            revision = 0,
            romSize = rom.size,
            dexSpeciesCount = 1,
            internalSpeciesCount = 2,
            moveCount = 1,
            tables = ProfileTables(
                abilities = table,
            ),
        )
    }
    return if (useDefaultReferenceIndex) {
        RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, "ABILITY TEST"),
            exactProfile = exactProfile,
            limits = limits,
            cancellation = cancellation,
        )
    } else {
        RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, "ABILITY TEST"),
            exactProfile = exactProfile,
            limits = limits,
            gbaReferenceIndexFactory = { _, _ ->
                onReferenceIndexBuild()
                referenceIndexOverride ?: GbaReferenceIndex.countsOnlyForTesting(references)
            },
            cancellation = cancellation,
        )
    }
}

internal fun putAbilityNames(
    bytes: ByteArray,
    layout: AbilityNameTableLayout,
    names: List<String>,
) {
    require(names.size == layout.count.toInt())
    names.forEachIndexed { index, name ->
        putGbaText(
            bytes,
            layout.offset.toInt() + index * layout.stride + layout.nameOffset,
            name,
            layout.nameWidth,
        )
    }
}

internal fun ordinaryAbilityNames(abilityCount: Int): List<String> = buildList {
    add("-------")
    repeat(abilityCount) { index -> add("ABILITY ${index + 1}") }
}

internal fun putAbilityDescriptions(
    bytes: ByteArray,
    layout: AbilityDescriptionTableLayout,
    descriptions: List<String?>,
    textBase: Int,
) {
    require(descriptions.size == layout.count.toInt())
    descriptions.forEachIndexed { index, description ->
        if (description == null) return@forEachIndexed
        val textOffset = textBase + index * 0x40
        putGbaPointer(
            bytes,
            layout.offset.toInt() + index * layout.recordStride + layout.pointerOffset,
            textOffset,
        )
        putGbaText(bytes, textOffset, description)
    }
}

internal fun putGbaText(
    bytes: ByteArray,
    offset: Int,
    value: String,
    width: Int? = null,
) {
    val required = width ?: (value.length + 1)
    require(offset >= 0 && offset + required <= bytes.size)
    if (width != null) repeat(width) { bytes[offset + it] = 0 }
    value.forEachIndexed { index, character ->
        require(index < required) { "fixture text does not fit" }
        bytes[offset + index] = when (character) {
            ' ' -> 0
            '-' -> 0xAE.toByte()
            '.' -> 0xAD.toByte()
            in '0'..'9' -> (0xA1 + character.code - '0'.code).toByte()
            in 'A'..'Z' -> (0xBB + character.code - 'A'.code).toByte()
            in 'a'..'z' -> (0xD5 + character.code - 'a'.code).toByte()
            else -> error("unsupported fixture character $character")
        }
    }
    if (value.length < required) bytes[offset + value.length] = 0xFF.toByte()
}

internal fun putGbaPointer(bytes: ByteArray, offset: Int, targetOffset: Int) {
    val value = 0x08000000 + targetOffset
    repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
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
        val encoded = 0x4800 or ((literal - pc) / 4)
        bytes[instruction] = encoded.toByte()
        bytes[instruction + 1] = (encoded ushr 8).toByte()
        putGbaPointer(bytes, literal, target)
    }
}
