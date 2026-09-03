package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.validate.PokemonDatasetValidators

/** Resolves the Gen I Pokédex-entry pointer table from its compiled index consumer. */
internal object Gen1CompiledDescriptionResolver {
    fun resolve(
        rom: RomImage,
        preferredCount: Int,
        fallbackCounts: Collection<Int>,
        codec: PokemonTextCodec = PokemonTextCodec.gbEnglish,
    ): TableLayout? {
        if (preferredCount !in 1..MAX_SPECIES) return null
        val roots = compiledRoots(rom)
        if (roots.isEmpty()) return null
        val counts = listOf(preferredCount) + fallbackCounts
            .filter { it in 1..MAX_SPECIES && it != preferredCount }
            .distinct()
        counts.forEach { count ->
            val candidates = roots.mapNotNull { root -> validatedLayout(rom, root, count, codec) }
                .distinct()
            if (candidates.size > 1) return null
            candidates.singleOrNull()?.let { return it }
        }
        return null
    }

    private fun compiledRoots(rom: RomImage): Set<Int> = buildSet {
        var offset = 0
        while (offset + CONSUMER_BYTES <= rom.size) {
            if (
                rom.u8(offset) == LOAD_HL_IMMEDIATE &&
                rom.u8(offset + 3) == LOAD_A_ABSOLUTE &&
                rom.u16le(offset + 4) in WRAM_ADDRESS_RANGE &&
                rom.u8(offset + 6) == DEC_A &&
                rom.u8(offset + 7) == LOAD_E_FROM_A &&
                rom.u8(offset + 8) == LOAD_D_IMMEDIATE &&
                rom.u8(offset + 9) == 0 &&
                rom.u8(offset + 10) == ADD_HL_DE &&
                rom.u8(offset + 11) == ADD_HL_DE &&
                rom.u8(offset + 12) == LOAD_A_INCREMENT_HL &&
                rom.u8(offset + 13) == LOAD_E_FROM_A &&
                rom.u8(offset + 14) == LOAD_D_HL
            ) {
                val bank = offset / BANK_BYTES
                val address = rom.u16le(offset + 1)
                if (bank > 0 && address in SWITCHABLE_ADDRESS_RANGE) {
                    rom.gbBankAddress(bank, address)?.let(::add)
                }
            }
            offset++
        }
    }

    private fun validatedLayout(
        rom: RomImage,
        root: Int,
        count: Int,
        codec: PokemonTextCodec,
    ): TableLayout? {
        val bank = root / BANK_BYTES
        val evidence = PokemonDatasetValidators.gen1Descriptions(
            rom = rom,
            pointerTableOffset = root,
            count = count,
            entryBank = bank,
            codec = codec,
            expectedDexCount = count,
        )
        if (!evidence.compatible) return null
        return TableLayout(root, count, POINTER_BYTES, bank = bank)
    }

    private val SWITCHABLE_ADDRESS_RANGE = 0x4000..0x7FFF
    private val WRAM_ADDRESS_RANGE = 0xC000..0xDFFF
    private const val BANK_BYTES = 0x4000
    private const val POINTER_BYTES = 2
    private const val CONSUMER_BYTES = 15
    private const val MAX_SPECIES = 255
    private const val LOAD_D_IMMEDIATE = 0x16
    private const val ADD_HL_DE = 0x19
    private const val LOAD_HL_IMMEDIATE = 0x21
    private const val LOAD_A_INCREMENT_HL = 0x2A
    private const val DEC_A = 0x3D
    private const val LOAD_E_FROM_A = 0x5F
    private const val LOAD_D_HL = 0x56
    private const val LOAD_A_ABSOLUTE = 0xFA
}
