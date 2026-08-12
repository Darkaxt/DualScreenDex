package com.enrpau.dualscreendex.parser.dataset.types

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

internal fun typeChartSession(
    bytes: ByteArray,
    references: Map<Int, Int> = emptyMap(),
    limits: ResolutionLimits = ResolutionLimits(),
    exactTable: TableLayout? = null,
    referenceIndex: GbaReferenceIndex? = null,
    onReferenceIndexBuild: () -> Unit = {},
): RomAnalysisSession {
    val rom = RomImage(bytes)
    val exactProfile = exactTable?.let { table ->
        RomProfile(
            name = "exact type-chart fixture",
            sha256 = rom.sha256,
            crc32 = rom.crc32,
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            title = "TYPE CHART TEST",
            revision = 0,
            romSize = rom.size,
            dexSpeciesCount = 1,
            internalSpeciesCount = 1,
            moveCount = 1,
            tables = ProfileTables(typeChart = table),
        )
    }
    return RomAnalysisSession(
        rom = rom,
        header = RomHeader(Platform.GBA, "TYPE CHART TEST"),
        exactProfile = exactProfile,
        limits = limits,
        gbaReferenceIndexFactory = { _, _ ->
            onReferenceIndexBuild()
            referenceIndex ?: GbaReferenceIndex.countsOnlyForTesting(references)
        },
    )
}

internal fun putLegacyTypeChart(bytes: ByteArray, offset: Int, records: Int = 12) {
    repeat(records) { index ->
        bytes[offset + index * 3] = (index % 18).toByte()
        bytes[offset + index * 3 + 1] = ((index * 5 + 1) % 18).toByte()
        bytes[offset + index * 3 + 2] = intArrayOf(0, 5, 10, 20)[index % 4].toByte()
    }
    bytes[offset + records * 3] = 0xFF.toByte()
    bytes[offset + records * 3 + 1] = 0xFF.toByte()
    bytes[offset + records * 3 + 2] = 0
}

internal fun putU32Q412Matrix(bytes: ByteArray, offset: Int, typeCount: Int) {
    repeat(typeCount * typeCount) { index -> putTypeU32(bytes, offset + index * 4, 4096) }
    val nonNeutral = longArrayOf(0, 819, 2048, 8192, 20480)
    repeat(typeCount) { row ->
        repeat(4) { variant ->
            putTypeU32(
                bytes,
                offset + (row * typeCount + (row + variant + 1) % typeCount) * 4,
                nonNeutral[(row + variant) % nonNeutral.size],
            )
        }
    }
}

internal fun putU16Q412Pair(bytes: ByteArray, offset: Int, typeCount: Int) {
    val values = typeCount * typeCount
    repeat(values) { index -> putTypeU16(bytes, offset + index * 2, 4096) }
    repeat(typeCount) { row ->
        putTypeU16(bytes, offset + (row * typeCount + (row + 1) % typeCount) * 2, 0)
        putTypeU16(bytes, offset + (row * typeCount + (row + 2) % typeCount) * 2, 819)
        putTypeU16(bytes, offset + (row * typeCount + (row + 3) % typeCount) * 2, 8192)
    }
    putInverseU16Matrix(bytes, offset, offset + values * 2, values)
}

internal fun putConsecutiveInverseU16Matrices(
    bytes: ByteArray,
    offset: Int,
    typeCount: Int,
    matrixCount: Int,
) {
    val values = typeCount * typeCount
    val matrixBytes = values * 2
    repeat(values) { index -> putTypeU16(bytes, offset + index * 2, 4096) }
    repeat(typeCount) { row ->
        putTypeU16(bytes, offset + (row * typeCount + (row + 1) % typeCount) * 2, 0)
        putTypeU16(bytes, offset + (row * typeCount + (row + 2) % typeCount) * 2, 2048)
        putTypeU16(bytes, offset + (row * typeCount + (row + 3) % typeCount) * 2, 8192)
    }
    repeat(matrixCount - 1) { index ->
        putInverseU16Matrix(bytes, offset + index * matrixBytes, offset + (index + 1) * matrixBytes, values)
    }
}

private fun putInverseU16Matrix(bytes: ByteArray, source: Int, inverse: Int, values: Int) {
    repeat(values) { index ->
        val value = (bytes[source + index * 2].toInt() and 0xFF) or
            ((bytes[source + index * 2 + 1].toInt() and 0xFF) shl 8)
        putTypeU16(
            bytes,
            inverse + index * 2,
            when {
                value < 4096 -> 8192
                value > 4096 -> 2048
                else -> 4096
            },
        )
    }
}

internal fun putTypeU16(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = value.toByte()
    bytes[offset + 1] = (value ushr 8).toByte()
}

internal fun putTypeU32(bytes: ByteArray, offset: Int, value: Long) {
    repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
}
