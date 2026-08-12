package com.enrpau.dualscreendex.parser.dataset.media

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
import java.util.Base64

internal fun spriteSession(
    bytes: ByteArray,
    platform: Platform = Platform.GBA,
    references: Map<Int, Int> = emptyMap(),
    limits: ResolutionLimits = ResolutionLimits(),
    exactLayout: SpriteTableLayout? = null,
    onReferenceIndexBuild: () -> Unit = {},
): RomAnalysisSession {
    val rom = RomImage(bytes)
    val exactProfile = exactLayout?.let { layout ->
        RomProfile(
            name = "exact sprite fixture",
            sha256 = rom.sha256,
            crc32 = rom.crc32,
            family = EngineFamily.EMERALD,
            platform = platform,
            title = "SPRITE TEST",
            revision = 0,
            romSize = rom.size,
            dexSpeciesCount = layout.count.toInt(),
            internalSpeciesCount = layout.count.toInt(),
            moveCount = 1,
            tables = ProfileTables(
                sprites = TableLayout(
                    offset = layout.tableOffset.toInt(),
                    count = layout.count.toInt(),
                    recordSize = layout.recordStride,
                ),
            ),
        )
    }
    return RomAnalysisSession(
        rom = rom,
        header = RomHeader(platform, "SPRITE TEST"),
        exactProfile = exactProfile,
        limits = limits,
        gbaReferenceIndexFactory = { _, _ ->
            onReferenceIndexBuild()
            GbaReferenceIndex.countsOnlyForTesting(references)
        },
    )
}

internal fun gbaLiteral(raw: ByteArray): ByteArray {
    val output = ArrayList<Byte>()
    output += 0x10
    output += raw.size.toByte()
    output += (raw.size ushr 8).toByte()
    output += (raw.size ushr 16).toByte()
    raw.asList().chunked(8).forEach { group ->
        output += 0
        output.addAll(group)
    }
    return output.toByteArray()
}

internal fun smolZero2048(): ByteArray = Base64.getDecoder().decode("ASAIAAAAKAABAAAAAAH+BwEAAAA=")

internal fun smolRawPalette(): ByteArray {
    val bytes = ByteArray(44)
    putU32(bytes, 0, 1L or (8L shl 4) or (16L shl 18))
    putU32(bytes, 4, 2L shl 19)
    bytes[10] = 0x1F
    bytes[40] = 0
    bytes[41] = 16
    return bytes
}

internal fun gen1ZeroSprite(width: Int = 1): ByteArray {
    val bits = mutableListOf<Int>()
    bits += 0
    repeat(2) { plane ->
        bits += 0
        val groups = width * width * 32
        val bitWidth = Integer.SIZE - Integer.numberOfLeadingZeros(groups + 1) - 1
        repeat(bitWidth - 1) { bits += 1 }
        bits += 0
        val base = (1 shl bitWidth) - 1
        val remainder = groups - base
        for (shift in bitWidth - 1 downTo 0) bits += (remainder ushr shift) and 1
        if (plane == 0) bits += 0
    }
    val bytes = ByteArray(1 + (bits.size + 7) / 8)
    bytes[0] = ((width shl 4) or width).toByte()
    bits.forEachIndexed { index, bit ->
        if (bit != 0) {
            bytes[1 + index / 8] = (bytes[1 + index / 8].toInt() or (1 shl (7 - index % 8))).toByte()
        }
    }
    return bytes
}

internal fun putU16(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = value.toByte()
    bytes[offset + 1] = (value ushr 8).toByte()
}

internal fun putGen2CompiledBankedReference(bytes: ByteArray, site: Int, target: Int) {
    require(target >= 0x4000)
    val bank = target / 0x4000
    val local = 0x4000 + target % 0x4000
    bytes[site] = 0x3E // LD A, bank
    bytes[site + 1] = bank.toByte()
    bytes[site + 2] = 0x21 // LD HL, local address
    putU16(bytes, site + 3, local)
}

internal fun putGbaPointer(bytes: ByteArray, offset: Int, target: Int) {
    putU32(bytes, offset, 0x08000000L + target)
}

internal fun putU32(bytes: ByteArray, offset: Int, value: Long) {
    repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
}
