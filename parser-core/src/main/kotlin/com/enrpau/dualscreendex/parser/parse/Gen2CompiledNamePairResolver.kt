package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.validate.TableValidators

internal data class Gen2CompiledNamePairResolution(
    val speciesNames: TableLayout,
    val moveNames: TableLayout,
)

/** Resolves adjacent Gen II species/move far pointers by both complete table shapes. */
internal object Gen2CompiledNamePairResolver {
    fun resolve(
        rom: RomImage,
        speciesCount: Int,
        moveCount: Int,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
    ): Gen2CompiledNamePairResolution? {
        if (speciesCount <= 0 || moveCount <= 0) return null
        val scanEnd = minOf(HOME_BANK_BYTES, rom.size)
        val candidates = buildSet {
            for (offset in 0..scanEnd - POINTER_PAIR_BYTES) {
                cancellation.throwIfCancellationRequested()
                val speciesRoot = farPointer(rom, offset) ?: continue
                val moveRoot = farPointer(rom, offset + FAR_POINTER_BYTES) ?: continue
                if (speciesRoot == moveRoot || speciesRoot + speciesCount * SPECIES_NAME_BYTES > rom.size) continue

                val speciesEvidence = TableValidators.fixedNames(
                    rom = rom,
                    offset = speciesRoot,
                    count = speciesCount,
                    width = SPECIES_NAME_BYTES,
                    codec = codec,
                    minimumRatio = MINIMUM_SPECIES_NAME_RATIO,
                )
                if (!speciesEvidence.compatible) continue
                val moveEvidence = TableValidators.variableNames(
                    rom = rom,
                    offset = moveRoot,
                    count = moveCount,
                    codec = codec,
                )
                if (!moveEvidence.compatible) continue

                add(
                    Gen2CompiledNamePairResolution(
                        speciesNames = TableLayout(speciesRoot, speciesCount, SPECIES_NAME_BYTES),
                        moveNames = TableLayout(moveRoot, moveCount, 0, variableLength = true),
                    ),
                )
            }
        }
        return candidates.singleOrNull()
    }

    private fun farPointer(rom: RomImage, offset: Int): Int? {
        val bank = rom.u8(offset)
        val address = rom.u16le(offset + 1)
        if (bank == 0 || address !in SWITCHABLE_ADDRESS_RANGE) return null
        return rom.gbBankAddress(bank, address)
    }

    private val SWITCHABLE_ADDRESS_RANGE = 0x4000..0x7FFF
    private const val HOME_BANK_BYTES = 0x4000
    private const val FAR_POINTER_BYTES = 3
    private const val POINTER_PAIR_BYTES = FAR_POINTER_BYTES * 2
    private const val SPECIES_NAME_BYTES = 10
    private const val MINIMUM_SPECIES_NAME_RATIO = 0.80
}
