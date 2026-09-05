package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage

/** Generated instructions/data only; no retail bytes, header identity, or external ROM dependency. */
internal class Gen2CompiledMapFixture {
    val bytes = ByteArray(0x10000)

    init {
        // Home-bank GetMapPointer and bank-switching map-header-member caller (9-byte records).
        put(0x200, 0xc5, 0x05, 0x48, 0x06, 0, 0x21, 0, 0x50, 0x09, 0x09,
            0x2a, 0x66, 0x6f, 0xc1, 0x0d, 0x06, 0, 0x3e, 9, 0xc3, 0, 3, 0xc9)
        put(0x240, 0xf0, 0x80, 0xf5, 0x3e, 2, 0xd7, 0xcd, 0, 2,
            0x19, 0x4e, 0x23, 0x46, 0xf1, 0xd7, 0xc9)
        word(GROUP_TABLE, 0x5002)
        put(HEADERS, 3, 1, 3, 0, 0x50, 1, 0, 0, 0)
        put(HEADERS + 9, 3, 1, 3, 0x10, 0x50, 2, 0, 0, 0)

        // Current/backup map-location calls, dynamic-special retry, threshold 2, common returns.
        mapLocationCall(0x400)
        put(0x40b, 0xfe, 0, 0x20, 11)
        mapLocationCall(0x40f)
        put(0x41a, 0xfe, 2, 0x30, 2, 0xaf, 0xc9, 0x3e, 1, 0xc9)
        landmarkConsumer(LANDMARK_CONSUMER, LANDMARK_TABLE)
        landmark(1, intArrayOf(0x80, 0x50))
        landmark(2, intArrayOf(0x81, 0x50))

        // Two direct Town Map planes and their shared copy loop/palette-map lookup.
        put(0x4100, 0x11, 0, 0x48, 0x18, 3, 0x11, 0, 0x4a, 0x21, 0, 0x98,
            0x1a, 0xfe, 0xff, 0xc8, 0x1a, 0x22, 0x13, 0x18, 0xf7,
            0xfe, 48, 0x30, 0, 0x21, 0, 0x4c)
        bytes[0x4800 + 360] = 0xff.toByte()
        bytes[0x4a00 + 360] = 0xff.toByte()
        repeat(24) { bytes[0x4c00 + it] = 0x10 }
        put(0x4200, 0x21, 0, 0x51, 0x11, 0, 0x90, 0x01, 48, 1, 0xc3, 0, 3, 0xc9)
        put(0x5100, 0xee, 0xff, 0xff) // LZ3 zero-fill 768 bytes, end.
        put(0x4300, 0x06, 2, 0xcd, 0, 3)
        // Palette layout dispatcher: third jump-table entry owns a six-palette copy.
        put(0x4380, 0x11, 0xa0, 0x43, 0x19, 0x2a, 0xe9)
        word(0x43a4, 0x4400)
        put(0x4400, 0x21, 0, 0x4d, 0x11, 0, 0xc2, 0x01, 48, 0)
        repeat(6) { palette ->
            word(0x4d00 + palette * 8, 0x7fff)
            word(0x4d02 + palette * 8, palette + 1)
            word(0x4d04 + palette * 8, palette + 10)
        }
    }

    fun session(cancellation: ParserCancellationToken = ParserCancellationToken.NONE): RomAnalysisSession {
        val rom = RomImage(bytes.copyOf())
        return RomAnalysisSession(rom, RomHeaderReader.read(rom), cancellation = cancellation)
    }

    fun landmark(id: Int, name: IntArray, table: Int = LANDMARK_TABLE, names: Int = NAMES) {
        val row = table + id * 4
        put(row, 8 + id * 8, 16 + id * 8)
        word(row + 2, 0x4000 + (names + id * 32) % 0x4000)
        bytes.fill(0, names + id * 32, names + (id + 1) * 32)
        put(names + id * 32, *name)
    }

    fun landmarkConsumer(offset: Int, table: Int) {
        put(offset, 0xe5, 0x6b, 0x26, 0, 0x29, 0x29, 0x11,
            table and 0xff, 0x40 + (table % 0x4000 ushr 8), 0x19, 0x2a, 0x5f, 0x56, 0xe1, 0xc9)
    }

    fun addCompetingLandmarks(differentCoordinates: Boolean, malformed: Boolean = false) {
        landmarkConsumer(0x8100, 0x9800)
        landmark(1, if (malformed) intArrayOf(0, 0x50) else intArrayOf(0x82, 0x50), 0x9800, 0xa400)
        landmark(2, intArrayOf(0x83, 0x50), 0x9800, 0xa400)
        if (differentCoordinates) bytes[0x9804] = 40
    }

    /** Minimal complete local-map chain sharing the world fixture's group/header/landmark authority. */
    fun withLocalMaps(): Gen2CompiledMapFixture = apply {
        put(0x600, 0xe5, 0xc5, 0x21, 0, 0x54, 0x01, 15, 0, 0xfa, 0, 0xc0,
            0xcd, 0, 3, 0x11, 0, 0xc1, 0x01, 15, 0, 0x3e, 3, 0xcd, 0, 3)
        put(0xc100, 0xfa, 0, 0xc0, 0x5f, 0x16, 0, 0x21, 0, 0x55, 0x19, 0x7e,
            0xfe, 0xff, 0xc8, 0x21, 0, 0x56, 0x01, 144, 0, 0xcd, 0, 3,
            0x11, 0, 0x90, 0x01, 144, 0, 0xcd, 0, 3)
        put(0xd500, 0xff, 0xff)
        put(0xc200, 0xcb, 0x3f, 0x38, 14, 0x21, 0, 0xc0, 0x86, 0x6f,
            0xfa, 1, 0xc0, 0xce, 0, 0x67, 0x7e, 0xe6, 15,
            0x21, 0, 0xc0, 0x86, 0x6f, 0xfa, 1, 0xc0, 0xce, 0, 0x67, 0x7e, 0xcb, 0x37, 0xe6, 15)
        put(0xc300, 0xfa, 0, 0xc0, 0xe6, 7, 0x5f, 0x16, 0, 0x21, 0, 0x57,
            0x19, 0x19, 0x2a, 0x66, 0x6f, 0xfa, 2, 0xc0, 0xe6, 3,
            0x87, 0x87, 0x87, 0x5f, 0x16, 0, 0x19, 0x5d, 0x54,
            0x6f, 0x26, 0, 0x29, 0x29, 0x29, 0x11, 0, 0x59, 0x19, 0x5d, 0x54,
            0x6f, 0x26, 0, 0x29, 0x29, 0x29, 0x11, 0, 0x5a, 0x19,
            0xfa, 2, 0xc0, 0xe6, 3, 0xfe, 2)
        repeat(8) { word(0xd700 + it * 2, 0x5800) }
        repeat(2) { map ->
            put(0xd000 + map * 16, 0, 1, 1, 3, 0, 0x51, 3, 0, 0x52, 0, 0x53, 0)
        }
        put(0xd40f, 3, 0, 0x5b, 3, 0, 0x5c, 3, 0, 0x5d, 0, 0, 0, 0, 0, 0x5e)
        put(0xdb00, 0xef, 0xff, 0xed, 0xff, 0xff) // 1024 + 512 zero graphics bytes.
    }

    fun put(offset: Int, vararg values: Int) {
        values.forEachIndexed { index, value -> bytes[offset + index] = value.toByte() }
    }

    fun word(offset: Int, value: Int) = put(offset, value and 0xff, value ushr 8)

    private fun mapLocationCall(offset: Int) =
        put(offset, 0xfa, 0, 0xc0, 0x47, 0xfa, 1, 0xc0, 0x4f, 0xcd, 0, 3)

    companion object {
        val MAP_IDS = setOf(0x101, 0x102)
        const val GROUP_TABLE = 0x9000
        const val HEADERS = 0x9002
        const val LANDMARK_CONSUMER = 0x8000
        const val LANDMARK_TABLE = 0x9400
        const val NAMES = 0xa000
    }
}
