package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Gen2CompactCoreMetadata
import com.enrpau.dualscreendex.parser.model.Gen2CompactSpeciesSlot

internal data class Gen2CompactIndexResolution(
    val metadata: Gen2CompactCoreMetadata,
    val nameSlots: Int,
    val baseSlots: Int,
)

/** Complete index transforms, with separate zero-based and one-based variant arithmetic. */
internal object Gen2CompactIndexResolver {
    fun zeroBased(rom: RomImage, baseHelper: Int, nameConverter: Int): Gen2CompactIndexResolution? {
        if (!speciesDomain(rom) || !within(rom, baseHelper, 32) ||
            !homeBytes(rom, baseHelper, 0x21) ||
            !homeBytes(rom, baseHelper + 3, 0xD5, 0x7C, 0x2F, 0x57, 0x7D, 0x2F, 0x5F, 0xCD) ||
            !homeBytes(rom, baseHelper + 13, 0x30, 0x03, 0x3F, 0xD1, 0xC9, 0x19, 0xCB, 0x3C, 0xCB, 0x1D, 0x11) ||
            !homeBytes(rom, baseHelper + 26, 0x19, 0x44, 0x4D, 0x37, 0xD1, 0xC9)
        ) return null
        val limit = rom.u16le(baseHelper + 24).takeIf { it in 257..510 } ?: return null
        val helper = rom.u16le(baseHelper + 11)
        if (!within(rom, helper, 33) ||
            !homeBytes(rom, helper, 0x0C, 0x28, 0x13, 0x0D, 0x78, 0xE6, 0x3F, 0x47,
                0x2A, 0xA7, 0x28, 0x0D, 0xB9, 0x2A, 0x20, 0xF8, 0xCD) ||
            !homeBytes(rom, helper + 19, 0x20, 0xF3, 0xC9, 0x06, 0x00, 0x0D, 0x78, 0xCD) ||
            rom.u16le(helper + 27) != nameConverter ||
            !homeBytes(rom, helper + 29, 0x47, 0x0D, 0x37, 0xC9) ||
            !homeBytes(rom, nameConverter, 0xE6, 0x20, 0xCB, 0x37, 0x1F, 0xC9)
        ) return null
        val compare = rom.u16le(helper + 17)
        if (!homeBytes(rom, compare, 0xC5, 0x4F, 0x87, 0x79, 0x38, 0x02, 0xCB, 0xB8,
                0xA8, 0x28, 0x07, 0xFE, 0x20, 0x30, 0x03, 0xA8, 0xE6, 0x1F, 0xA7, 0x79, 0xC1, 0xC9)
        ) return null
        val variants = pairs(rom, rom.u16le(baseHelper + 1)) ?: return null
        val slots = (1..limit).filter { validSpeciesByte(it and 0xFF) }.map { id ->
            val form = if (id >= 256) 0x20 else 0
            val matched = variants.indexOfFirst { (species, target) ->
                species == (id and 0xFF) && zeroFormMatches(target, form)
            }
            Gen2CompactSpeciesSlot(id, id, if (matched >= 0) limit + matched else id - 1)
        }
        return Gen2CompactIndexResolution(Gen2CompactCoreMetadata(slots), limit + 1, limit + variants.size)
    }

    fun oneBased(rom: RomImage, baseHelper: Int, nameHelper: Int): Gen2CompactIndexResolution? {
        if (!speciesDomain(rom)) return null
        val base = oneEntry(rom, baseHelper) ?: return null
        val names = oneEntry(rom, nameHelper) ?: return null
        if (base.helper != names.helper || base.final != names.final ||
            !homeBytes(rom, base.final, 0x09, 0xCB, 0x3C, 0xCB, 0x1D, 0x2B, 0x24, 0x44, 0x4D, 0xC9) ||
            !homeBytes(rom, base.helper, 0x78, 0xE6, 0x3F, 0x28, 0x23, 0xFE, 0x01, 0x28, 0x1F,
                0x47, 0x23, 0x2A, 0xA7, 0x28, 0x19, 0xB9, 0x20, 0xF8, 0x3E, 0x3F, 0xA6,
                0x28, 0xF3, 0xFE, 0x20, 0x20, 0x06, 0xCB, 0x68, 0x28, 0xEB, 0x23, 0xC9,
                0x2A, 0xCB, 0x6F, 0xB8, 0x20, 0xE4, 0xC9, 0x06, 0x00, 0x37, 0xC9)
        ) return null
        val variants = pairs(rom, base.table) ?: return null
        val nameVariants = pairs(rom, names.table) ?: return null
        val extended = nameVariants.filter { it.second and 0x3F == 0x20 }
        if (extended.map { it.first }.distinct().size != extended.size) return null
        val slots = (1..254).map { Gen2CompactSpeciesSlot(it, it, it - 1) }.toMutableList()
        nameVariants.forEachIndexed { index, (species, form) ->
            if (form and 0x3F == 0x20) {
                val matched = variants.indexOfFirst { (targetSpecies, targetForm) ->
                    targetSpecies == species && oneFormMatches(targetForm, form)
                }
                slots += Gen2CompactSpeciesSlot(256 + species, 256 + index,
                    if (matched >= 0) 255 + matched else species - 1)
            }
        }
        return Gen2CompactIndexResolution(Gen2CompactCoreMetadata(slots.sortedBy { it.id }),
            256 + nameVariants.size, 255 + variants.size)
    }

    private data class OneEntry(val table: Int, val helper: Int, val final: Int)

    private fun oneEntry(rom: RomImage, offset: Int): OneEntry? {
        if (!within(rom, offset, 12) || !homeBytes(rom, offset, 0x21) ||
            !homeBytes(rom, offset + 3, 0xCD) || !homeBytes(rom, offset + 6, 0xD8, 0x01)
        ) return null
        val table = rom.u16le(offset + 1) + 1
        if (table !in 1..0x3FFF || rom.u16le(offset + 8) != (-table and 0xFFFF)) return null
        val final = if (rom.u8(offset + 10) == 0x18) {
            offset + 12 + rom.u8(offset + 11).toByte().toInt()
        } else {
            offset + 10
        }
        return OneEntry(table, rom.u16le(offset + 4), final)
    }

    private fun zeroFormMatches(target: Int, input: Int): Boolean {
        val adjusted = if (target and 0x80 == 0) input and 0x7F else input
        val difference = target xor adjusted
        return difference == 0 || (difference < 0x20 && target and 0x1F == 0)
    }

    private fun oneFormMatches(target: Int, input: Int): Boolean = when (target and 0x3F) {
        0 -> false
        0x20 -> input and 0x20 != 0
        else -> target == input
    }

    private fun pairs(rom: RomImage, root: Int): List<Pair<Int, Int>>? {
        if (root !in 1..0x3FFF) return null
        val result = mutableListOf<Pair<Int, Int>>()
        for (index in 0..255) {
            val offset = root + index * 2
            if (!within(rom, offset, 1)) return null
            val species = rom.u8(offset)
            if (species == 0) return result
            if (!validSpeciesByte(species) || !within(rom, offset, 2) || index == 255) return null
            val pair = species to rom.u8(offset + 1)
            if (pair in result) return null
            result += pair
        }
        return null
    }

    private fun speciesDomain(rom: RomImage): Boolean =
        (0..minOf(0x4000, rom.size) - 5).any { homeBytes(rom, it, 0x3C, 0xFE, 0x02, 0x3D, 0xC9) }

    private fun validSpeciesByte(species: Int): Boolean = species in 1..254

    private fun homeBytes(rom: RomImage, offset: Int, vararg values: Int): Boolean =
        within(rom, offset, values.size) && values.indices.all { rom.u8(offset + it) == values[it] }

    private fun within(rom: RomImage, offset: Int, count: Int): Boolean =
        offset >= 0 && offset.toLong() + count <= minOf(0x4000, rom.size)
}
