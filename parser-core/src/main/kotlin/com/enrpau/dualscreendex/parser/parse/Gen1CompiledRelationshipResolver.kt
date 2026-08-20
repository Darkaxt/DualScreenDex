package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.validate.PokemonDatasetValidators

/** Resolves the Gen I combined evolution/learnset table from compiled pointer consumers. */
internal object Gen1CompiledRelationshipResolver {
    fun resolve(
        rom: RomImage,
        preferredCount: Int,
        fallbackCounts: Collection<Int>,
    ): TableLayout? {
        if (preferredCount !in 1..MAX_SPECIES) return null
        val roots = compiledRoots(rom)
        if (roots.isEmpty()) return null
        val counts = listOf(preferredCount) + fallbackCounts
            .filter { it in 1..MAX_SPECIES && it != preferredCount }
            .distinct()
        counts.forEach { count ->
            val candidates = roots.mapNotNull { root -> validatedLayout(rom, root, count) }
                .distinct()
            if (candidates.size > 1) return null
            candidates.singleOrNull()?.let { return it }
        }
        return null
    }

    private fun compiledRoots(rom: RomImage): Set<Int> = buildSet {
        var offset = 0
        while (offset + MIN_CONSUMER_BYTES <= rom.size) {
            if (rom.u8(offset) == LOAD_HL_IMMEDIATE && hasPointerConsumer(rom, offset + 3)) {
                val bank = offset / BANK_BYTES
                val address = rom.u16le(offset + 1)
                if (bank > 0 && address in SWITCHABLE_ADDRESS_RANGE) {
                    rom.gbBankAddress(bank, address)?.let(::add)
                }
            }
            offset++
        }
    }

    private fun hasPointerConsumer(rom: RomImage, start: Int): Boolean {
        val endExclusive = minOf(rom.size, start + CONSUMER_WINDOW_BYTES)
        return contains(rom, start, endExclusive, SCALED_INDEX_CONSUMER) ||
            contains(rom, start, endExclusive, DOUBLE_ADD_INDEX_CONSUMER)
    }

    private fun validatedLayout(
        rom: RomImage,
        root: Int,
        count: Int,
    ): TableLayout? {
        val bank = root / BANK_BYTES
        val evidence = PokemonDatasetValidators.gen12EvolutionsAndLearnsets(
            rom = rom,
            pointerTableOffset = root,
            speciesCount = count,
            tableBank = bank,
            moveCount = MAX_MOVES,
            generation = 1,
        )
        if (!evidence.evolutions.compatible || !evidence.learnsets.compatible) return null
        return TableLayout(
            offset = root,
            count = count,
            recordSize = POINTER_BYTES,
            variableLength = true,
            bank = bank,
        )
    }

    private fun contains(rom: RomImage, start: Int, endExclusive: Int, pattern: IntArray): Boolean {
        var candidate = start
        while (candidate + pattern.size <= endExclusive) {
            if (pattern.indices.all { index -> rom.u8(candidate + index) == pattern[index] }) return true
            candidate++
        }
        return false
    }

    private val SWITCHABLE_ADDRESS_RANGE = 0x4000..0x7FFF
    private val SCALED_INDEX_CONSUMER = intArrayOf(0x87, 0xCB, 0x10, 0x4F, 0x09, 0x2A, 0x66, 0x6F)
    private val DOUBLE_ADD_INDEX_CONSUMER = intArrayOf(0x4F, 0x09, 0x09, 0x2A, 0x66, 0x6F)
    private const val BANK_BYTES = 0x4000
    private const val POINTER_BYTES = 2
    private const val CONSUMER_WINDOW_BYTES = 29
    private const val MIN_CONSUMER_BYTES = 11
    private const val MAX_SPECIES = 254
    private const val MAX_MOVES = 255
    private const val LOAD_HL_IMMEDIATE = 0x21
}
