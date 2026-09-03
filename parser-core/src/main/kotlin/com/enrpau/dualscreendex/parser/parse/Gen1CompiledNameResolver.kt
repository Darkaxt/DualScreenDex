package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.validate.TableValidators

/** Resolves a Gen I fixed-name table from its complete compiled copy consumer. */
internal object Gen1CompiledNameResolver {
    fun resolve(
        rom: RomImage,
        count: Int,
        codec: PokemonTextCodec = PokemonTextCodec.gbEnglish,
    ): TableLayout? {
        if (count !in 1..MAX_NAME_COUNT) return null
        val scanEnd = minOf(BANK_BYTES, rom.size)
        val candidates = buildList {
            var offset = 0
            while (offset + CONSUMER_BYTES <= scanEnd) {
                parseConsumerAt(rom, offset, count, codec)?.let(::add)
                offset++
            }
        }
        return candidates.distinct().singleOrNull()
    }

    private fun parseConsumerAt(
        rom: RomImage,
        offset: Int,
        count: Int,
        codec: PokemonTextCodec,
    ): TableLayout? = runCatching {
        val recordSize = rom.u8(offset + 19)
        if (
            rom.u8(offset) != PUSH_HL || rom.u8(offset + 1) != LOAD_A_HIGH ||
            rom.u8(offset + 3) != PUSH_AF || rom.u8(offset + 4) != LOAD_A_IMMEDIATE ||
            rom.u8(offset + 6) != STORE_A_HIGH || rom.u8(offset + 7) != rom.u8(offset + 2) ||
            rom.u8(offset + 8) != STORE_A_ABSOLUTE ||
            rom.u8(offset + 11) != LOAD_A_ABSOLUTE || rom.u8(offset + 14) != DEC_A ||
            rom.u8(offset + 15) != LOAD_HL_IMMEDIATE || rom.u8(offset + 18) != LOAD_C_IMMEDIATE ||
            recordSize !in MIN_NAME_BYTES..MAX_NAME_BYTES || rom.u8(offset + 20) != LOAD_B_IMMEDIATE ||
            rom.u8(offset + 21) != 0 || rom.u8(offset + 22) != CALL ||
            rom.u8(offset + 25) != LOAD_DE_IMMEDIATE || rom.u8(offset + 28) != PUSH_DE ||
            rom.u8(offset + 29) != LOAD_BC_IMMEDIATE || rom.u16le(offset + 30) != recordSize ||
            rom.u8(offset + 32) != CALL || rom.u8(offset + 35) != LOAD_HL_IMMEDIATE ||
            rom.u16le(offset + 36) != ((rom.u16le(offset + 26) + recordSize) and 0xffff) ||
            rom.u8(offset + 38) != STORE_IMMEDIATE_HL ||
            rom.u8(offset + 39) != codec.terminator ||
            rom.u8(offset + 40) != POP_DE || rom.u8(offset + 41) != POP_AF ||
            rom.u8(offset + 42) != STORE_A_HIGH || rom.u8(offset + 43) != rom.u8(offset + 2) ||
            rom.u8(offset + 44) != STORE_A_ABSOLUTE ||
            rom.u16le(offset + 45) != rom.u16le(offset + 9) ||
            rom.u8(offset + 47) != POP_HL || rom.u8(offset + 48) != RETURN
        ) return@runCatching null

        val bank = rom.u8(offset + 5)
        val root = rom.gbBankAddress(bank, rom.u16le(offset + 16)) ?: return@runCatching null
        val resolvedCount = TableValidators.inferFixedNameCount(
            rom,
            root,
            recordSize,
            codec,
            minimumCount = count,
            maximumCount = MAX_NAME_COUNT,
        ) ?: count
        val evidence = TableValidators.fixedNames(
            rom,
            root,
            resolvedCount,
            recordSize,
            codec,
        )
        if (!evidence.compatible) return@runCatching null
        TableLayout(root, resolvedCount, recordSize)
    }.getOrNull()

    private const val BANK_BYTES = 0x4000
    private const val CONSUMER_BYTES = 49
    private const val MIN_NAME_BYTES = 5
    private const val MAX_NAME_BYTES = 16
    private const val MAX_NAME_COUNT = 254
    private const val LOAD_BC_IMMEDIATE = 0x01
    private const val LOAD_B_IMMEDIATE = 0x06
    private const val LOAD_C_IMMEDIATE = 0x0e
    private const val LOAD_DE_IMMEDIATE = 0x11
    private const val LOAD_HL_IMMEDIATE = 0x21
    private const val DEC_A = 0x3d
    private const val STORE_IMMEDIATE_HL = 0x36
    private const val LOAD_A_IMMEDIATE = 0x3e
    private const val LOAD_A_HIGH = 0xf0
    private const val LOAD_A_ABSOLUTE = 0xfa
    private const val STORE_A_HIGH = 0xe0
    private const val STORE_A_ABSOLUTE = 0xea
    private const val PUSH_DE = 0xd5
    private const val PUSH_HL = 0xe5
    private const val PUSH_AF = 0xf5
    private const val POP_DE = 0xd1
    private const val POP_HL = 0xe1
    private const val POP_AF = 0xf1
    private const val CALL = 0xcd
    private const val RETURN = 0xc9
}
