package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.language.LanguageTag

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
        cancellation.throwIfCancellationRequested()
        if (speciesCount <= 0 || moveCount <= 0) return null
        val scanEnd = minOf(HOME_BANK_BYTES, rom.size)
        val compiledNames = GbCompiledNameConsumer.discover(rom, 2, cancellation)
        if (compiledNames.isEmpty() && codec.language in setOf(LanguageTag.JAPANESE, LanguageTag.KOREAN)) return null
        val referencedPairs = if (compiledNames.isNotEmpty()) referencedPairs(rom, scanEnd, cancellation) else null
        val candidates = buildSet {
            for (offset in 0..scanEnd - POINTER_PAIR_BYTES) {
                cancellation.throwIfCancellationRequested()
                if (referencedPairs != null && offset !in referencedPairs) continue
                val speciesRoot = farPointer(rom, offset) ?: continue
                val moveRoot = farPointer(rom, offset + FAR_POINTER_BYTES) ?: continue
                val width = if (compiledNames.isEmpty()) SPECIES_NAME_BYTES else {
                    compiledNames.filter { it.offset == speciesRoot }.map { it.width }.distinct().singleOrNull()
                        ?: continue
                }
                if (speciesRoot == moveRoot || speciesRoot.toLong() + speciesCount.toLong() * width >
                    minOf(rom.size, (speciesRoot / HOME_BANK_BYTES + 1) * HOME_BANK_BYTES)
                ) continue

                if (!validNames(rom, speciesRoot, speciesCount, width, codec, cancellation)) continue
                if (!validNames(rom, moveRoot, moveCount, null, codec, cancellation)) continue

                add(
                    Gen2CompiledNamePairResolution(
                        speciesNames = TableLayout(speciesRoot, speciesCount, width),
                        moveNames = TableLayout(moveRoot, moveCount, 0, variableLength = true),
                    ),
                )
            }
        }
        return candidates.singleOrNull()
    }

    private fun validNames(rom: RomImage, root: Int, count: Int, width: Int?, codec: PokemonTextCodec,
        cancellation: ParserCancellationToken): Boolean {
        var cursor = root
        var valid = 0
        val end = minOf(rom.size.toLong(), (root / HOME_BANK_BYTES + 1L) * HOME_BANK_BYTES).toInt()
        for (index in 0 until count) {
            cancellation.throwIfCancellationRequested()
            if (cursor !in root until end || width != null && cursor.toLong() + width > end) return false
            val decoded = codec.decodeDetailed(rom, cursor, width ?: minOf(24, end - cursor), cancellation)
            if (!decoded.terminated && !(width != null && decoded.contentBytes == width && decoded.validBytes == width &&
                    decoded.controlUnits == 0 && decoded.substitutionUnits == 0)) return false
            if (decoded.invalidUnits == 0 && decoded.controlUnits == 0 && decoded.substitutionUnits == 0 && decoded.text.isNotBlank()) valid++
            cursor += width ?: decoded.consumedBytes
        }
        return valid.toDouble() / count >= MINIMUM_SPECIES_NAME_RATIO
    }

    private fun referencedPairs(rom: RomImage, end: Int, cancellation: ParserCancellationToken): Set<Int> = buildSet {
        val nthString = intArrayOf(0xa7, 0xc8, 0xc5, 0x47, 0x0e, 0x50, 0x2a, 0xb9, 0x20, 0xfc, 0x05, 0x20, 0xf9, 0xc1, 0xc9)
        for (offset in 0..end - 27) {
            cancellation.throwIfCancellationRequested()
            if (rom.u8(offset) != 0x21 || (3..5).any { rom.u8(offset + it) != 0x19 } ||
                rom.u8(offset + 6) != 0x2a || rom.u8(offset + 7) != 0xd7 ||
                rom.u8(offset + 8) != 0x2a || rom.u8(offset + 9) != 0x66 || rom.u8(offset + 10) != 0x6f ||
                rom.u8(offset + 11) != 0xfa || rom.u16le(offset + 12) !in 0xc000..0xdfff ||
                rom.u8(offset + 14) != 0x3d || rom.u8(offset + 15) != 0xcd ||
                rom.u8(offset + 18) != 0x11 || rom.u16le(offset + 19) !in 0xc000..0xdfff ||
                rom.u8(offset + 21) != 0x01 || rom.u16le(offset + 22) !in 1..24 || rom.u8(offset + 24) != 0xcd
            ) continue
            val helper = rom.u16le(offset + 16)
            val table = rom.u16le(offset + 1)
            if (helper + nthString.size > end || table + POINTER_PAIR_BYTES > end ||
                nthString.indices.any { rom.u8(helper + it) != nthString[it] }
            ) continue
            add(table)
        }
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
