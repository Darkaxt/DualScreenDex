package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.MapTimeBlend
import com.enrpau.dualscreendex.parser.catalog.MapTimePaletteModel
import com.enrpau.dualscreendex.parser.io.RomImage

internal object Gen3MapLightingResolver {
    fun resolve(rom: RomImage): MapTimePaletteModel? {
        val candidates = buildList {
            var offset = 0
            while (offset <= rom.size - TABLE_PAIR_BYTES) {
                val normal = readTable(rom, offset)
                val bright = readTable(rom, offset + TABLE_BYTES)
                if (
                    normal != null && bright != null &&
                    normal.twilight == bright.twilight && normal.day == bright.day &&
                    normal.night != bright.night &&
                    pointerReferences(rom, offset) >= MIN_TABLE_REFERENCES &&
                    pointerReferences(rom, offset + TABLE_BYTES) >= MIN_TABLE_REFERENCES
                ) {
                    add(normal)
                }
                offset += Int.SIZE_BYTES
            }
        }
        return candidates.singleOrNull()
    }

    private fun readTable(rom: RomImage, offset: Int): MapTimePaletteModel? {
        val night = readBlend(rom.u32le(offset))
        val twilight = readBlend(rom.u32le(offset + BLEND_BYTES))
        val day = readBlend(rom.u32le(offset + BLEND_BYTES * 2))
        return if (
            night.tint && night.coefficient == NIGHT_COEFFICIENT && night.blendColor != 0 &&
            twilight.tint && twilight.coefficient == TWILIGHT_COEFFICIENT && twilight.blendColor != 0 &&
            !day.tint && day.coefficient == 0 && day.blendColor == 0
        ) {
            MapTimePaletteModel(night, twilight, day)
        } else {
            null
        }
    }

    private fun readBlend(raw: Long): MapTimeBlend = MapTimeBlend(
        blendColor = (raw and 0xFFFFFF).toInt(),
        tint = raw and TINT_FLAG != 0L,
        coefficient = (raw ushr COEFFICIENT_SHIFT and 0x1F).toInt(),
    )

    private fun pointerReferences(rom: RomImage, targetOffset: Int): Int {
        val pointer = GBA_ROM_BASE + targetOffset
        var references = 0
        var offset = 0
        while (offset <= rom.size - Int.SIZE_BYTES) {
            if (rom.u32le(offset) == pointer) references++
            offset += Int.SIZE_BYTES
        }
        return references
    }

    private const val GBA_ROM_BASE = 0x08000000L
    private const val BLEND_BYTES = 4
    private const val TABLE_BYTES = BLEND_BYTES * 3
    private const val TABLE_PAIR_BYTES = TABLE_BYTES * 2
    private const val TINT_FLAG = 1L shl 24
    private const val COEFFICIENT_SHIFT = 25
    private const val NIGHT_COEFFICIENT = 10
    private const val TWILIGHT_COEFFICIENT = 4
    private const val MIN_TABLE_REFERENCES = 2
}
