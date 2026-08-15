package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout

/** Decodes the published Gen III base-stat ability ABIs. */
internal object Gen3BaseStatAbilitySlots {
    fun supportsLayout(rom: RomImage, table: TableLayout, count: Int = table.count): Boolean {
        if (table.recordSize !in SUPPORTED_RECORD_SIZES) return false
        val stride = table.stride ?: table.recordSize
        if (table.offset < 0 || table.count <= 0 || count <= 0 || count > table.count || stride < table.recordSize) {
            return false
        }
        val end = table.offset.toLong() + (count - 1L) * stride + table.recordSize
        return end <= rom.size.toLong()
    }

    fun read(rom: RomImage, table: TableLayout, index: Int, count: Int = table.count): List<Int> {
        if (!supportsLayout(rom, table, count) || index !in 0 until count) return emptyList()
        val stride = table.stride ?: table.recordSize
        return read(rom, table.offset + index * stride, table.recordSize)
    }

    fun read(rom: RomImage, recordOffset: Int, recordSize: Int): List<Int> {
        if (recordSize !in SUPPORTED_RECORD_SIZES ||
            recordOffset < 0 || recordOffset.toLong() + recordSize > rom.size.toLong()
        ) {
            return emptyList()
        }
        return when (recordSize) {
            BATTLE_ENGINE_RECORD_SIZE -> (0 until BATTLE_ENGINE_SLOT_COUNT)
                .map { slot -> rom.u16le(recordOffset + ABILITY_OFFSET + slot * 2) }
            else -> listOf(
                rom.u8(recordOffset + ABILITY_OFFSET),
                rom.u8(recordOffset + ABILITY_OFFSET + 1),
            )
        }.filter { it != 0 }.distinct()
    }

    private const val ABILITY_OFFSET = 22
    private const val STANDARD_RECORD_SIZE = 28
    private const val BATTLE_ENGINE_RECORD_SIZE = 32
    private const val MODERN_EMERALD_RECORD_SIZE = 40
    private const val BATTLE_ENGINE_SLOT_COUNT = 3
    private val SUPPORTED_RECORD_SIZES = setOf(
        STANDARD_RECORD_SIZE,
        BATTLE_ENGINE_RECORD_SIZE,
        MODERN_EMERALD_RECORD_SIZE,
    )
}
