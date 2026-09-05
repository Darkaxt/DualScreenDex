package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionTableLayout
import com.enrpau.dualscreendex.parser.io.RomImage

/** Positive adjacent-object consumers, never padding or pointer occurrence alone. */
internal object CompiledDescriptionExtentBinding {
    fun matches(
        rom: RomImage,
        table: DescriptionTableLayout,
        limits: ResolutionLimits,
        work: () -> Boolean,
    ): Boolean {
        if (table.recordSize != 28 || table.count < 2) return false
        val end = table.offset + table.count * table.recordSize
        if (end !in 0..(rom.size - 16).toLong() || end % 4 != 0L) return false
        var nominated = 0
        val objectSizes = linkedSetOf<Int>()
        for (site in 0..rom.size - 16 step 2) {
            if (site and 0xfff == 0 && !work()) return false
            val argumentLoad = rom.u16le(site) and 0xff00
            if (argumentLoad != 0x4800 && argumentLoad != 0x4900) continue
            if (literal(rom, site) != 0x08000000L + end) continue
            if (!work() || ++nominated > minOf(limits.maxNominatedGbaReferenceSites,
                    limits.maxCompiledReferenceSitesPerCandidate)) return false
            if (argumentLoad == 0x4800 && end + 32 <= rom.size && palette(rom, site)) objectSizes += 32
            if (argumentLoad == 0x4900 && CompiledBackgroundDescriptionNeighbor.matches(rom, site)) objectSizes += 16
        }
        return objectSizes.size == 1
    }

    private fun palette(rom: RomImage, site: Int): Boolean {
        if (!instructions(rom, site + 2, intArrayOf(0x7961, 0x0909, 0x3110, 0x0109, 0x2220))) return false
        val callee = call(rom, site + 12) ?: return false
        if (!instructions(rom, callee, intArrayOf(0xb570, 0x1c06, 0x1c0c, 0x1c15, 0x0424, 0x042d,
                0x0be4, 0x4908, 0x1861, 0x0c6d, 0x1c2a)) ||
            !instructions(rom, callee + 26, intArrayOf(0x4806, 0x1824, 0x1c30, 0x1c21, 0x1c2a)) ||
            !instructions(rom, callee + 40, intArrayOf(0xbc70, 0xbc01, 0x4700))) return false
        val copy = call(rom, callee + 22) ?: return false
        if (call(rom, callee + 36) != copy || !instructions(rom, copy, intArrayOf(0xdf0b, 0x4770))) return false
        val first = literal(rom, callee + 14) ?: return false
        val second = literal(rom, callee + 26) ?: return false
        // Two nonoverlapping 512-halfword RAM buffers; offset and length are narrowed in the body.
        return first in 0x02000000L..0x0203f800L && first % 2 == 0L && second - first == 0x400L
    }

    private fun instructions(rom: RomImage, start: Int, expected: IntArray): Boolean =
        start >= 0 && start.toLong() + expected.size * 2 <= rom.size &&
            expected.indices.all { rom.u16le(start + it * 2) == expected[it] }

    private fun literal(rom: RomImage, site: Int): Long? {
        if (site !in 0..rom.size - 2) return null
        val op = rom.u16le(site)
        if (op and 0xf800 != 0x4800) return null
        val address = ((site + 4) and -4) + (op and 255) * 4
        return address.takeIf { it in 0..rom.size - 4 }?.let(rom::u32le)
    }

    private fun call(rom: RomImage, site: Int): Int? {
        if (site !in 0..rom.size - 4) return null
        val high = rom.u16le(site)
        val low = rom.u16le(site + 2)
        if (high and 0xf800 != 0xf000 || low and 0xf800 != 0xf800) return null
        var displacement = ((high and 0x7ff) shl 12) or ((low and 0x7ff) shl 1)
        if (displacement and 0x400000 != 0) displacement -= 0x800000
        return (site + 4 + displacement).takeIf { it in 0..rom.size - 2 }
    }
}
