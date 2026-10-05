package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader

/** Independently assembled consumers and invented catalogs, never bytes copied from a game. */
internal class Gen3CompiledWideCoreFixture(val relocation: Int = 0) {
    val bytes = ByteArray(0x6000 + relocation)
    val typeConsumer = 0x100 + relocation
    val nameConsumer = 0x200 + relocation
    val inverseConsumer = 0x300 + relocation
    val forwardConsumer = 0x400 + relocation
    val statConsumer = 0x600 + relocation
    val namesRoot = 0x2000 + relocation
    val mapRoot = 0x2600 + relocation
    val formRoot = 0x2800 + relocation
    val defaultStatsRoot = 0x3000 + relocation
    val variantStatsRoot = 0x3800 + relocation
    val mapCount = 5

    init {
        putTypeConsumer(typeConsumer, defaultStatsRoot, variantStatsRoot)
        putNameConsumer(nameConsumer)
        putInverseConsumer(inverseConsumer)
        putForwardConsumer(forwardConsumer)
        putStatConsumer(statConsumer, defaultStatsRoot)
        putWords(0x500 + relocation, 0x2000, 0x4770)
        listOf(44, 7, 44, 9, 0).forEachIndexed { index, number -> putU16(mapRoot + index * 2, number) }
        putU32(formRoot + 4, 0x08000000 + 0x2900 + relocation)
        putU16(0x2900 + relocation, 44)
        putName(0, "??????")
        for (index in 1..4) {
            putName(index, "NATIVE$index")
            putWideStats(defaultStatsRoot + index * 64, 345)
            putWideStats(variantStatsRoot + index * 64, 200)
        }
    }

    fun session(
        limits: ResolutionLimits = ResolutionLimits(),
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ) = RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "SYNTHETIC"), limits = limits, cancellation = cancellation)

    fun putTypeConsumer(at: Int, normal: Int, variant: Int) {
        putWords(at,
            0xb5f0, 0x0400, 0x0c04, 0x1c27, 0x0609, 0x0e0d, 0x1c2e, 0x2c00,
            0xd020, 0x202a, 0xf000, 0xf800, 0x0600, 0x2800, 0xd10c, 0x4805,
            0x01a1, 0x1809, 0x7b08, 0x42a8, 0xd00f, 0x7b48, 0x42a8, 0xd00c,
            0xe010, 0, 0, 0, 0x4805, 0x01b9, 0x1809, 0x7b08, 0x42b0, 0xd002,
            0x7b48, 0x42b0, 0xd104, 0x2001, 0xe003, 0, 0, 0, 0x2000, 0xbcf0, 0xbc02, 0x4708)
        putBl(at + 20, 0x500 + relocation)
        putU32(at + 52, 0x08000000 + normal)
        putU32(at + 80, 0x08000000 + variant)
    }

    private fun putNameConsumer(at: Int) {
        putWords(at,
            0xb5f0, 0x1c06, 0x0409, 0x0c0d, 0x2100, 0x4804, 0x4684, 0x4f04,
            0x200d, 0x4368, 0x19c3, 0x1c32, 0xe007, 0, 0, 0, 0, 0,
            0x3301, 0x3201, 0x3101, 0x1874, 0x290c, 0xdc09, 0x4565, 0xd902,
            0x19c8, 0x7800, 0xe000, 0x7818, 0x7010, 0x7820, 0x28ff, 0xd1ef,
            0x20ff, 0x7020, 0xbcf0, 0xbc01, 0x4700)
        putU32(at + 28, mapCount + 1)
        putU32(at + 32, 0x08000000 + namesRoot)
    }

    private fun putInverseConsumer(at: Int) {
        putWords(at,
            0xb510, 0x0400, 0x0c02, 0x2a00, 0xd01c, 0x2100, 0x4b0a,
            0x8818, 0x4290, 0xd00a, 0x4c09, 0x1c48, 0x0400, 0x0c01,
            0x42a1, 0xd804, 0x0048, 0x18c0, 0x8800, 0x4290, 0xd1f5,
            0x4805, 0x4281, 0xd009, 0x1c48, 0x0400, 0x0c00, 0xe006,
            0, 0, 0, 0, 0, 0, 0x2000, 0xbc10, 0xbc02, 0x4708)
        putU32(at + 56, 0x08000000 + mapRoot)
        putU32(at + 60, mapCount - 1)
        putU32(at + 64, mapCount)
    }

    private fun putForwardConsumer(at: Int) {
        putWords(at,
            0xb500, 0x0400, 0x0c02, 0x2a00, 0xd101, 0x2000, 0xe011,
            0x4906, 0x0090, 0x1840, 0x6800, 0x2800, 0xd10a, 0x4804,
            0x1e51, 0x0049, 0x1809, 0x8808, 0xe005, 0, 0, 0, 0, 0,
            0x8800, 0xbc02, 0x4708)
        putU32(at + 40, 0x08000000 + formRoot)
        putU32(at + 44, 0x08000000 + mapRoot)
    }

    fun putStatConsumer(at: Int, root: Int) {
        putWords(at, 0x4907, 0x01a8, 0x1840, 0x8842, 0x4694,
            0x8901, 0x8887, 0x8944, 0x8803, 0x88c0, 0x4770, 0, 0, 0, 0, 0)
        putU32(at + 32, 0x08000000 + root)
    }

    fun putName(index: Int, value: String) {
        val start = namesRoot + index * 13
        value.forEachIndexed { position, character ->
            bytes[start + position] = when (character) {
                in 'A'..'Z' -> (0xbb + character.code - 'A'.code).toByte()
                in '0'..'9' -> (0xa1 + character.code - '0'.code).toByte()
                '?' -> 0xac.toByte()
                else -> error("synthetic character unsupported")
            }
        }
        bytes[start + value.length] = 0xff.toByte()
    }

    fun putWideStats(at: Int, hp: Int) {
        listOf(hp, 49, 299, 50, 512, 65).forEachIndexed { field, value -> putU16(at + field * 2, value) }
        bytes[at + 12] = 12
        bytes[at + 13] = 3
        bytes[at + 14] = 45
        putU16(at + 16, 64)
        bytes[at + 24] = 127
        bytes[at + 25] = 20
        bytes[at + 26] = 70
        bytes[at + 27] = 4
        bytes[at + 28] = 1
        bytes[at + 29] = 7
        listOf(260, 65, 0, 34).forEachIndexed { slot, value -> putU16(at + 30 + slot * 2, value) }
    }

    fun putU16(at: Int, value: Int) {
        bytes[at] = value.toByte()
        bytes[at + 1] = (value ushr 8).toByte()
    }

    fun putU32(at: Int, value: Int) {
        repeat(4) { bytes[at + it] = (value ushr (it * 8)).toByte() }
    }

    fun putWords(at: Int, vararg words: Int) = words.forEachIndexed { index, word -> putU16(at + index * 2, word) }

    private fun putBl(at: Int, target: Int) {
        val displacement = target - (at + 4)
        putU16(at, 0xf000 or ((displacement shr 12) and 0x7ff))
        putU16(at + 2, 0xf800 or ((displacement shr 1) and 0x7ff))
    }
}
