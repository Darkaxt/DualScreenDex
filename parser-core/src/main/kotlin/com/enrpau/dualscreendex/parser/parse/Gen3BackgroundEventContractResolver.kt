package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage

internal object Gen3BackgroundEventContractResolver {
    data class Contract(val defaultScript: Int, val hiddenFlagStart: Int)

    fun resolve(
        rom: RomImage,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): Contract? {
        cancellation.throwIfCancellationRequested()
        if (rom.size !in CALLER_PREFIX_BYTES..MAXIMUM_ROM_BYTES) return null
        var candidates = 0
        var accepted: Consumer? = null
        for (at in 0..rom.size - CALLER_PREFIX_BYTES step 2) {
            if (at and CANCELLATION_MASK == 0) cancellation.throwIfCancellationRequested()
            if (!matches(rom, at, CALLER_PREFIX)) continue
            if (++candidates > MAXIMUM_CANDIDATES) return null
            // An independently nominated but unproved peer must not be masked by a valid one.
            val candidate = readConsumer(rom, at) ?: return null
            if (accepted != null && accepted != candidate) return null
            accepted = candidate
        }
        cancellation.throwIfCancellationRequested()
        return accepted?.contract
    }

    private fun readConsumer(rom: RomImage, at: Int): Consumer? {
        if (at and 3 != 0 || !contains(rom, at, CALLER_BYTES)) return null
        if (!matches(rom, at + 0x20, NULL_UNION_BRANCH) ||
            !matches(rom, at + 0x38, KIND_SWITCH) ||
            !matches(rom, at + 0x70, FACING_AND_HIDDEN_LOAD) ||
            !matches(rom, at + 0x92, HIDDEN_STORE) ||
            !matches(rom, at + 0xa0, FLAG_RESULT) ||
            !matches(rom, at + 0xb8, SECRET_PRELUDE) ||
            !matches(rom, at + 0xc4, SECRET_RESULT) ||
            !matches(rom, at + 0xd8, RETURN)
        ) return null
        val finder = branchTarget(rom, at + 0x1c) ?: return null
        if (!matches(rom, finder, FINDER)) return null
        val table = rom.gbaPointer(at + 0x48) ?: return null
        if (table and 3 != 0 || !contains(rom, table, SWITCH_TARGETS.size * 4)) return null
        for ((kind, target) in SWITCH_TARGETS.withIndex()) {
            if (rom.gbaPointer(table + kind * 4) != at + target) return null
        }
        val header = rom.u32le(at + 0x30)
        val flagVariable = rom.u32le(at + 0xac)
        if (!isRam(header, 8, 4) || !isRam(flagVariable, 4, 2) ||
            rom.u32le(at + 0xb0) != flagVariable + 2 || rom.u32le(at + 0xd0) != flagVariable
        ) return null
        val defaultScript = rom.gbaPointer(at + 0x34) ?: return null
        if (!contains(rom, defaultScript, 9) || rom.u8(defaultScript) != 0x0f ||
            rom.u8(defaultScript + 1) != 0 || rom.gbaPointer(defaultScript + 2) == null ||
            rom.u8(defaultScript + 6) != 9 || rom.u8(defaultScript + 7) != 3 || rom.u8(defaultScript + 8) != 2
        ) return null
        val hiddenScript = rom.gbaPointer(at + 0xb4) ?: return null
        val secretScript = rom.gbaPointer(at + 0xd4) ?: return null
        // The opaque secret helper is a boundary, never evidence for a message-free result.
        val secretHelper = branchTarget(rom, at + 0xc0) ?: return null
        val immediate = rom.u16le(at + 0x8e)
        val shift = rom.u16le(at + 0x90)
        if (immediate and 0xff00 != 0x2300 || shift and 0xf83f != 0x001b) return null
        val flagBase = (immediate and 255).toLong() shl ((shift ushr 6) and 31)
        if (flagBase !in 0L..65535L) return null
        val flagGet = branchTarget(rom, at + 0x9c) ?: return null
        val storage = readFlagStorage(rom, flagGet) ?: return null
        return Consumer(Contract(defaultScript, flagBase.toInt()), header, flagVariable, storage,
            hiddenScript, secretScript, secretHelper)
    }

    private fun readFlagStorage(rom: RomImage, flagGet: Int): FlagStorage? {
        if (!contains(rom, flagGet, 0x2c) || !matches(rom, flagGet, FLAG_GET_PREFIX) ||
            !matches(rom, flagGet + 0x0c, FLAG_GET_BODY)
        ) return null
        val pointer = branchTarget(rom, flagGet + 8) ?: return null
        if (pointer and 3 != 0 || !contains(rom, pointer, 0x54) ||
            !matches(rom, pointer, FLAG_POINTER_PREFIX) || !matches(rom, pointer + 0x30, FLAG_POINTER_SYSTEM) ||
            rom.u32le(pointer + 0x24) != 0x3fffL || rom.u32le(pointer + 0x48) != 0xffffc000L ||
            rom.u32le(pointer + 0x4c) != 0xffffc007L
        ) return null
        val savePointerCell = rom.u32le(pointer + 0x28)
        val flagsOffset = rom.u32le(pointer + 0x2c)
        val systemFlags = rom.u32le(pointer + 0x50)
        if (!isRam(savePointerCell, 4, 4) || flagsOffset !in 1L..65535L || !isRam(systemFlags, 1, 1)) return null
        return FlagStorage(savePointerCell, flagsOffset, systemFlags)
    }

    private fun branchTarget(rom: RomImage, at: Int): Int? {
        if (!contains(rom, at, 4)) return null
        val high = rom.u16le(at)
        val low = rom.u16le(at + 2)
        if (high and 0xf800 != 0xf000 || low and 0xf800 != 0xf800) return null
        val upper = (high and 0x7ff).let { if (it and 0x400 != 0) it - 0x800 else it }
        val target = at.toLong() + 4 + (upper.toLong() shl 12) + ((low and 0x7ff) shl 1)
        return target.takeIf { it >= 0 && it + 2 <= rom.size }?.toInt()
    }

    private fun matches(rom: RomImage, at: Int, pattern: IntArray): Boolean =
        contains(rom, at, pattern.size * 2) && pattern.indices.all { rom.u16le(at + it * 2) == pattern[it] }

    private fun contains(rom: RomImage, at: Int, bytes: Int): Boolean =
        at >= 0 && at.toLong() + bytes <= rom.size

    private fun isRam(value: Long, bytes: Int, alignment: Int): Boolean = value % alignment == 0L &&
        ((value >= 0x02000000L && value + bytes <= 0x02040000L) ||
            (value >= 0x03000000L && value + bytes <= 0x03008000L))

    private data class FlagStorage(val savePointerCell: Long, val flagsOffset: Long, val systemFlags: Long)
    private data class Consumer(
        val contract: Contract,
        val header: Long,
        val flagVariable: Long,
        val storage: FlagStorage,
        val hiddenScript: Int,
        val secretScript: Int,
        val secretHelper: Int,
    )

    private const val MAXIMUM_ROM_BYTES = 32 * 1024 * 1024
    private const val MAXIMUM_CANDIDATES = 32
    private const val CALLER_BYTES = 0xe0
    private const val CALLER_PREFIX_BYTES = 0x1c
    private const val CANCELLATION_MASK = 0x3fff
    private val CALLER_PREFIX = intArrayOf(
        0xb530, 0x0612, 0x0e15, 0x4c0a, 0x8801, 0x3907, 0x0409, 0x0c09,
        0x8842, 0x3a07, 0x0412, 0x0c12, 0x7903, 0x1c20,
    )
    private val NULL_UNION_BRANCH = intArrayOf(0x2800, 0xd027, 0x6882, 0x2a00, 0xd106, 0x4802, 0xe055)
    private val KIND_SWITCH = intArrayOf(0x7940, 0x2808, 0xd84c, 0x0080, 0x4901, 0x1840, 0x6800, 0x4687)
    private val SWITCH_TARGETS = intArrayOf(0xd8, 0x70, 0x78, 0x7e, 0x84, 0x8a, 0x8a, 0x8a, 0xb8)
    private val FACING_AND_HIDDEN_LOAD = intArrayOf(
        0x2d02, 0xd031, 0x2000, 0xe030, 0x2d01, 0xd02d, 0xe7fa,
        0x2d04, 0xd02a, 0xe7f7, 0x2d03, 0xd027, 0xe7f4, 0x4908, 0x0c10,
    )
    private val HIDDEN_STORE = intArrayOf(0x18c0, 0x8008, 0x4806, 0x8002, 0x8808)
    private val FLAG_RESULT = intArrayOf(0x0600, 0x0e00, 0x2801, 0xd0e5, 0x4802, 0xe016)
    private val SECRET_PRELUDE = intArrayOf(0x2d02, 0xd1db, 0x4804, 0x8002)
    private val SECRET_RESULT = intArrayOf(0x0600, 0x2800, 0xd0d4, 0x4802, 0xe005)
    private val RETURN = intArrayOf(0x1c10, 0xbc30, 0xbc02, 0x4708)
    private val FINDER = intArrayOf(
        0xb5f0, 0x0409, 0x0c0f, 0x0412, 0x0c16, 0x061b, 0x0e1d, 0x6840,
        0x6904, 0x78c3, 0x2200, 0x429a, 0xd215, 0x0050, 0x1880, 0x0080,
        0x1901, 0x8808, 0x42b8, 0xd109, 0x8848, 0x42b0, 0xd106, 0x7908,
        0x42a8, 0xd001, 0x2800, 0xd101, 0x1c08, 0xe005, 0x1c50, 0x0600,
        0x0e02, 0x429a, 0xd3e9, 0x2000, 0xbcf0, 0xbc02, 0x4708,
    )
    private val FLAG_GET_PREFIX = intArrayOf(0xb510, 0x0400, 0x0c04, 0x1c20)
    private val FLAG_GET_BODY = intArrayOf(
        0x2800, 0xd009, 0x7800, 0x2107, 0x4021, 0x4108, 0x2101, 0x4008,
        0x2800, 0xd001, 0x2001, 0xe000, 0x2000, 0xbc10, 0xbc02, 0x4708,
    )
    private val FLAG_POINTER_PREFIX = intArrayOf(
        0xb500, 0x0401, 0x0c0a, 0x1c13, 0x2a00, 0xd101, 0x2000, 0xe018,
        0x4804, 0x4282, 0xd80c, 0x4804, 0x0cc9, 0x4a04, 0x1889, 0x6800, 0xe00e,
    )
    private val FLAG_POINTER_SYSTEM = intArrayOf(
        0x4905, 0x1858, 0x2800, 0xda01, 0x4a04, 0x1898, 0x10c0, 0x4904, 0x1840, 0xbc02, 0x4708,
    )
}
