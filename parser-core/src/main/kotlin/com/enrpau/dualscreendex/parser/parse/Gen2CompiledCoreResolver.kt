package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.validate.TableValidators

internal data class Gen2CompiledCoreResolution(
    val speciesCount: Int,
    val tables: ProfileTables,
)

/** Resolves paired fixed-name and base-data tables from their complete compiled Gen 2 consumers. */
internal object Gen2CompiledCoreResolver {
    private const val HOME_BANK_BYTES = 0x4000
    private const val MINIMUM_SPECIES_COUNT = 151
    private const val MAXIMUM_SPECIES_COUNT = 255
    private const val NAME_RECORD_SIZE = 10
    private const val NAME_CONSUMER_BYTES = 45
    private const val BASE_CONSUMER_BYTES = 70
    private const val VARIANT_BASE_TAIL_BYTES = 22
    private const val VARIANT_BASE_PROLOGUE_BYTES = 12
    private const val VARIANT_BASE_EPILOGUE_BYTES = 12
    private const val MAX_VARIANT_BASE_FUNCTION_BYTES = 1024
    private const val MINIMUM_BASE_RECORD_SIZE = 20
    private const val MAXIMUM_BASE_RECORD_SIZE = 64

    private data class NameConsumer(
        val bank: Int,
        val root: Int,
    )

    private data class BaseConsumer(
        val bank: Int,
        val root: Int,
        val recordSize: Int,
    )

    fun resolve(
        rom: RomImage,
        codec: PokemonTextCodec = PokemonTextCodec.gbEnglish,
    ): Gen2CompiledCoreResolution? {
        val scanEnd = minOf(HOME_BANK_BYTES, rom.size)
        val names = (0..scanEnd - NAME_CONSUMER_BYTES).mapNotNull { offset ->
            parseNameConsumer(rom, offset, codec)
        }
        val bases = (0..scanEnd - BASE_CONSUMER_BYTES).mapNotNull { offset ->
            parseBaseConsumer(rom, offset) ?: parseVariantBaseConsumer(rom, offset, scanEnd)
        }
        val candidates = names.flatMap { name ->
            bases.mapNotNull { base -> resolvePair(rom, name, base, codec) }
        }
        return candidates.distinct().singleOrNull()
    }

    private fun parseNameConsumer(
        rom: RomImage,
        offset: Int,
        codec: PokemonTextCodec,
    ): NameConsumer? = runCatching {
        if (
            rom.u8(offset) != LOAD_A_HIGH ||
            rom.u8(offset + 2) != PUSH_AF ||
            rom.u8(offset + 3) != PUSH_HL ||
            rom.u8(offset + 4) != LOAD_A_IMMEDIATE ||
            rom.u8(offset + 6) != RST_BANKSWITCH ||
            rom.u8(offset + 7) != LOAD_A_ABSOLUTE ||
            rom.u8(offset + 10) != DEC_A ||
            rom.u8(offset + 11) != LOAD_D_IMMEDIATE ||
            rom.u8(offset + 12) != 0 ||
            rom.u8(offset + 13) != LOAD_E_A ||
            rom.u8(offset + 14) != LOAD_H_IMMEDIATE ||
            rom.u8(offset + 15) != 0 ||
            rom.u8(offset + 16) != LOAD_L_A ||
            rom.u8(offset + 17) != ADD_HL_HL ||
            rom.u8(offset + 18) != ADD_HL_HL ||
            rom.u8(offset + 19) != ADD_HL_DE ||
            rom.u8(offset + 20) != ADD_HL_HL ||
            rom.u8(offset + 21) != LOAD_DE_IMMEDIATE ||
            rom.u8(offset + 24) != ADD_HL_DE ||
            rom.u8(offset + 25) != LOAD_DE_IMMEDIATE ||
            rom.u8(offset + 28) != PUSH_DE ||
            rom.u8(offset + 29) != LOAD_BC_IMMEDIATE ||
            rom.u16le(offset + 30) != NAME_RECORD_SIZE ||
            rom.u8(offset + 32) != CALL ||
            rom.u8(offset + 35) != LOAD_HL_IMMEDIATE ||
            rom.u8(offset + 38) != LOAD_HL_IMMEDIATE_VALUE ||
            rom.u8(offset + 39) != codec.terminator ||
            rom.u8(offset + 40) != POP_DE ||
            rom.u8(offset + 41) != POP_HL ||
            rom.u8(offset + 42) != POP_AF ||
            rom.u8(offset + 43) != RST_BANKSWITCH ||
            rom.u8(offset + 44) != RETURN
        ) return@runCatching null
        val bank = rom.u8(offset + 5)
        val root = rom.gbBankAddress(bank, rom.u16le(offset + 22)) ?: return@runCatching null
        NameConsumer(bank, root)
    }.getOrNull()

    private fun parseBaseConsumer(rom: RomImage, offset: Int): BaseConsumer? = runCatching {
        if (
            rom.u8(offset) != PUSH_BC ||
            rom.u8(offset + 1) != PUSH_DE ||
            rom.u8(offset + 2) != PUSH_HL ||
            rom.u8(offset + 3) != LOAD_A_HIGH ||
            rom.u8(offset + 5) != PUSH_AF ||
            rom.u8(offset + 6) != LOAD_A_IMMEDIATE ||
            rom.u8(offset + 8) != RST_BANKSWITCH ||
            rom.u8(offset + 9) != LOAD_A_ABSOLUTE ||
            rom.u8(offset + 12) != COMPARE_IMMEDIATE ||
            rom.u8(offset + 14) != JR_Z ||
            rom.u8(offset + 16) != DEC_A ||
            rom.u8(offset + 17) != LOAD_BC_IMMEDIATE ||
            rom.u8(offset + 19) != 0 ||
            rom.u8(offset + 20) != LOAD_HL_IMMEDIATE ||
            rom.u8(offset + 23) != CALL ||
            rom.u8(offset + 26) != LOAD_DE_IMMEDIATE ||
            rom.u8(offset + 29) != LOAD_BC_IMMEDIATE ||
            rom.u8(offset + 32) != CALL ||
            rom.u8(offset + 35) != JR ||
            offset + 16 + rom.u8(offset + 15).toByte().toInt() != offset + 37 ||
            offset + 37 + rom.u8(offset + 36).toByte().toInt() != offset + 58 ||
            rom.u8(offset + 58) != LOAD_A_ABSOLUTE ||
            rom.u16le(offset + 59) != rom.u16le(offset + 10) ||
            rom.u8(offset + 61) != LOAD_ABSOLUTE_A ||
            rom.u16le(offset + 62) != rom.u16le(offset + 27) ||
            rom.u8(offset + 64) != POP_AF ||
            rom.u8(offset + 65) != RST_BANKSWITCH ||
            rom.u8(offset + 66) != POP_HL ||
            rom.u8(offset + 67) != POP_DE ||
            rom.u8(offset + 68) != POP_BC ||
            rom.u8(offset + 69) != RETURN
        ) return@runCatching null
        val recordSize = rom.u8(offset + 18)
        if (recordSize !in 28..64 || recordSize % 2 != 0 || rom.u16le(offset + 30) != recordSize) {
            return@runCatching null
        }
        val bank = rom.u8(offset + 7)
        val root = rom.gbBankAddress(bank, rom.u16le(offset + 21)) ?: return@runCatching null
        BaseConsumer(bank, root, recordSize)
    }.getOrNull()

    private fun parseVariantBaseConsumer(
        rom: RomImage,
        offset: Int,
        scanEnd: Int,
    ): BaseConsumer? = runCatching {
        if (offset + VARIANT_BASE_TAIL_BYTES > scanEnd) return@runCatching null
        val recordSize = rom.u16le(offset + 2)
        if (
            rom.u8(offset) != DEC_A ||
            rom.u8(offset + 1) != LOAD_BC_IMMEDIATE ||
            recordSize !in MINIMUM_BASE_RECORD_SIZE..MAXIMUM_BASE_RECORD_SIZE ||
            rom.u8(offset + 4) != LOAD_HL_IMMEDIATE ||
            rom.u8(offset + 7) != CALL ||
            rom.u8(offset + 10) != LOAD_DE_IMMEDIATE ||
            rom.u8(offset + 13) != LOAD_BC_IMMEDIATE ||
            rom.u16le(offset + 14) != recordSize ||
            rom.u8(offset + 16) != CALL ||
            rom.u8(offset + 19) != JUMP
        ) return@runCatching null

        val epilogue = rom.u16le(offset + 20)
        if (
            epilogue + VARIANT_BASE_EPILOGUE_BYTES > scanEnd ||
            rom.u8(epilogue) != LOAD_A_ABSOLUTE ||
            rom.u8(epilogue + 3) != LOAD_ABSOLUTE_A ||
            rom.u16le(epilogue + 4) != rom.u16le(offset + 11) ||
            rom.u8(epilogue + 6) != POP_AF ||
            rom.u8(epilogue + 7) != RST_BANKSWITCH ||
            rom.u8(epilogue + 8) != POP_HL ||
            rom.u8(epilogue + 9) != POP_DE ||
            rom.u8(epilogue + 10) != POP_BC ||
            rom.u8(epilogue + 11) != RETURN
        ) return@runCatching null

        val speciesAddress = rom.u16le(epilogue + 1)
        val prologueStart = maxOf(0, offset - MAX_VARIANT_BASE_FUNCTION_BYTES)
        val banks = (prologueStart until offset).mapNotNull { candidate ->
            if (
                candidate + VARIANT_BASE_PROLOGUE_BYTES <= offset &&
                rom.u8(candidate) == PUSH_BC &&
                rom.u8(candidate + 1) == PUSH_DE &&
                rom.u8(candidate + 2) == PUSH_HL &&
                rom.u8(candidate + 3) == LOAD_A_HIGH &&
                rom.u8(candidate + 5) == PUSH_AF &&
                rom.u8(candidate + 6) == LOAD_A_IMMEDIATE &&
                rom.u8(candidate + 8) == RST_BANKSWITCH &&
                rom.u8(candidate + 9) == LOAD_A_ABSOLUTE &&
                rom.u16le(candidate + 10) == speciesAddress
            ) {
                rom.u8(candidate + 7).takeIf { it > 0 }
            } else {
                null
            }
        }.distinct()
        val bank = banks.singleOrNull() ?: return@runCatching null
        val root = rom.gbBankAddress(bank, rom.u16le(offset + 5)) ?: return@runCatching null
        BaseConsumer(bank, root, recordSize)
    }.getOrNull()

    private fun resolvePair(
        rom: RomImage,
        names: NameConsumer,
        base: BaseConsumer,
        codec: PokemonTextCodec,
    ): Gen2CompiledCoreResolution? {
        if (names.bank != base.bank || names.root <= base.root) return null
        val distance = names.root - base.root
        val adjacentCount = if (distance % base.recordSize == 0) distance / base.recordSize else null
        val speciesCount = adjacentCount?.takeIf { it in MINIMUM_SPECIES_COUNT..MAXIMUM_SPECIES_COUNT }
            ?: inferSequentialSpeciesCount(rom, base, names.root)
            ?: return null
        if ((0 until speciesCount).any { index -> rom.u8(base.root + index * base.recordSize) != index + 1 }) {
            return null
        }
        val nameLayout = TableLayout(names.root, speciesCount, NAME_RECORD_SIZE)
        val baseLayout = TableLayout(base.root, speciesCount, base.recordSize)
        if (!TableValidators.names(rom, nameLayout, speciesCount, codec, 0.85).compatible) {
            return null
        }
        if (!TableValidators.baseStats(rom, base.root, speciesCount, base.recordSize, 2).compatible) {
            return null
        }
        return Gen2CompiledCoreResolution(
            speciesCount = speciesCount,
            tables = ProfileTables(speciesNames = nameLayout, baseStats = baseLayout),
        )
    }

    private fun inferSequentialSpeciesCount(
        rom: RomImage,
        base: BaseConsumer,
        upperBound: Int,
    ): Int? {
        var count = 0
        while (
            count < MAXIMUM_SPECIES_COUNT &&
            base.root + (count + 1) * base.recordSize <= upperBound &&
            rom.u8(base.root + count * base.recordSize) == count + 1
        ) {
            count++
        }
        return count.takeIf { it in MINIMUM_SPECIES_COUNT..MAXIMUM_SPECIES_COUNT }
    }

    private const val LOAD_A_HIGH = 0xF0
    private const val LOAD_A_IMMEDIATE = 0x3E
    private const val LOAD_A_ABSOLUTE = 0xFA
    private const val LOAD_ABSOLUTE_A = 0xEA
    private const val LOAD_BC_IMMEDIATE = 0x01
    private const val LOAD_DE_IMMEDIATE = 0x11
    private const val LOAD_HL_IMMEDIATE = 0x21
    private const val LOAD_D_IMMEDIATE = 0x16
    private const val LOAD_H_IMMEDIATE = 0x26
    private const val LOAD_E_A = 0x5F
    private const val LOAD_L_A = 0x6F
    private const val LOAD_HL_IMMEDIATE_VALUE = 0x36
    private const val ADD_HL_DE = 0x19
    private const val ADD_HL_HL = 0x29
    private const val DEC_A = 0x3D
    private const val COMPARE_IMMEDIATE = 0xFE
    private const val JR = 0x18
    private const val JR_Z = 0x28
    private const val JUMP = 0xC3
    private const val CALL = 0xCD
    private const val RETURN = 0xC9
    private const val PUSH_BC = 0xC5
    private const val PUSH_DE = 0xD5
    private const val PUSH_HL = 0xE5
    private const val PUSH_AF = 0xF5
    private const val POP_BC = 0xC1
    private const val POP_DE = 0xD1
    private const val POP_HL = 0xE1
    private const val POP_AF = 0xF1
    private const val RST_BANKSWITCH = 0xD7
}
