package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.validate.TableValidators

internal data class Gen1CompiledMoveResolution(
    val moveNames: TableLayout,
    val moveData: TableLayout,
)

/** Resolves Gen I move names and details from their compiled name and copy consumers. */
internal object Gen1CompiledMoveResolver {
    fun resolve(
        rom: RomImage,
        codec: PokemonTextCodec = PokemonTextCodec.gbEnglish,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): Gen1CompiledMoveResolution? {
        cancellation.throwIfCancellationRequested()
        val nameLayouts = buildSet {
            moveNameRoots(rom, cancellation).forEach { root ->
                consecutiveNameCount(rom, root, codec, cancellation)?.let { count ->
                    add(TableLayout(root, count, 0, variableLength = true))
                }
            }
            GbCompiledBankCalls.discover(rom, cancellation).forEach { target ->
                pointerNameLayout(rom, target, codec, cancellation)?.let(::add)
            }
        }
        val dataRoots = moveDataRoots(rom, cancellation)
        if (nameLayouts.isEmpty() || dataRoots.isEmpty()) return null

        val candidates = buildSet {
            nameLayouts.forEach { names ->
                dataRoots.forEach { dataRoot ->
                    val evidence = TableValidators.moveData(
                        rom = rom,
                        offset = dataRoot.offset,
                        count = names.count,
                        recordSize = dataRoot.recordSize,
                        generation = 1,
                    )
                    if (evidence.compatible) {
                        add(
                            Gen1CompiledMoveResolution(
                                moveNames = names,
                                moveData = dataRoot.copy(count = names.count),
                            ),
                        )
                    }
                }
            }
        }
        return candidates.singleOrNull()
    }

    private fun pointerNameLayout(
        rom: RomImage,
        target: GbCompiledBankCalls.Target,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
    ): TableLayout? {
        val offset = target.offset
        val bank = offset / BANK_BYTES
        val end = minOf(rom.size, (bank + 1) * BANK_BYTES)
        if (offset + 24 > end || rom.u8(offset) != LOAD_A_ABSOLUTE ||
            rom.u8(offset + 3) != STORE_A_ABSOLUTE || rom.u8(offset + 6) != DEC_A ||
            rom.u8(offset + 7) != LOAD_HL_IMMEDIATE ||
            !byteSequence(rom, offset + 10, 0x16, 0x00, 0x5F, 0x19, 0x19, 0x2A, 0x56, 0x5F, 0x21) ||
            rom.u8(offset + 21) != 0xC3 ||
            !GbCompiledBankCalls.stringCopy(rom, rom.u16le(offset + 22), codec.terminator) ||
            !linkedMoveSelector(rom, target.callerOffset, rom.u16le(offset + 1),
                rom.u16le(offset + 4), rom.u16le(offset + 19), cancellation)
        ) return null
        val tableAddress = rom.u16le(offset + 8)
        if (tableAddress !in SWITCHABLE_ADDRESS_RANGE) return null
        val table = rom.gbBankAddress(bank, tableAddress) ?: return null
        if (table + POINTER_BYTES > end) return null
        val firstAddress = rom.u16le(table)
        if (firstAddress !in SWITCHABLE_ADDRESS_RANGE) return null
        val first = rom.gbBankAddress(bank, firstAddress) ?: return null
        val tableBytes = first - table
        val count = tableBytes / POINTER_BYTES
        if (tableBytes % POINTER_BYTES != 0 || count !in MIN_MOVE_COUNT..MAX_MOVE_COUNT) return null
        var cursor = first
        repeat(count) { index ->
            cancellation.throwIfCancellationRequested()
            val address = rom.u16le(table + index * POINTER_BYTES)
            if (address !in SWITCHABLE_ADDRESS_RANGE || rom.gbBankAddress(bank, address) != cursor || cursor >= end) return null
            val decoded = codec.decodeDetailed(rom, cursor, minOf(MAX_NAME_BYTES, end - cursor), cancellation)
            if (!decoded.terminated || decoded.text.isBlank() || decoded.validRatio < MINIMUM_NAME_RATIO ||
                decoded.controlUnits != 0 || decoded.substitutionUnits != 0
            ) return null
            cursor += decoded.consumedBytes
        }
        return TableLayout(first, count, 0, variableLength = true)
    }

    private fun linkedMoveSelector(
        rom: RomImage,
        caller: Int,
        sourceIndex: Int,
        namedObject: Int,
        destination: Int,
        cancellation: ParserCancellationToken,
    ): Boolean {
        val end = minOf(BANK_BYTES, rom.size)
        if (sourceIndex !in 0xC000..0xDFFF || namedObject !in 0xC000..0xDFFF ||
            sourceIndex == namedObject || destination !in 0xC000..0xDFFF ||
            caller < 3 || caller + 10 > end ||
            !byteSequence(rom, caller - 3, 0xE5, 0xC5, 0xD5) ||
            !byteSequence(rom, caller + 6, 0xD1, 0xC1, 0xE1, 0xC9)
        ) return false
        for (offset in 0..end - 13) {
            cancellation.throwIfCancellationRequested()
            if (rom.u8(offset) == LOAD_A_ABSOLUTE && rom.u16le(offset + 1) == namedObject &&
                rom.u8(offset + 3) == STORE_A_ABSOLUTE && rom.u16le(offset + 4) == sourceIndex &&
                rom.u8(offset + 6) == CALL && rom.u16le(offset + 7) == caller - 3 &&
                rom.u8(offset + 9) == LOAD_DE_IMMEDIATE && rom.u16le(offset + 10) == destination &&
                rom.u8(offset + 12) == RETURN
            ) return true
        }
        return false
    }

    private fun byteSequence(rom: RomImage, offset: Int, vararg values: Int): Boolean =
        offset >= 0 && offset.toLong() + values.size <= rom.size &&
            values.indices.all { rom.u8(offset + it) == values[it] }

    private fun moveNameRoots(rom: RomImage, cancellation: ParserCancellationToken): Set<Int> {
        val banks = moveNameBanks(rom, cancellation)
        val addresses = moveNameAddresses(rom, cancellation)
        return buildSet {
            banks.forEach { bank ->
                addresses.forEach { address ->
                    rom.gbBankAddress(bank, address)?.let(::add)
                }
            }
        }
    }

    private fun moveNameBanks(rom: RomImage, cancellation: ParserCancellationToken): Set<Int> = buildSet {
        val scanEnd = minOf(BANK_BYTES, rom.size)
        var offset = 0
        while (offset + MOVE_NAME_CONSUMER_BYTES <= scanEnd) {
            cancellation.throwIfCancellationRequested()
            if (
                rom.u8(offset) == PUSH_HL &&
                rom.u8(offset + 1) == LOAD_A_IMMEDIATE &&
                rom.u8(offset + 2) == MOVE_NAME_LIST_TYPE &&
                rom.u8(offset + 3) == STORE_A_ABSOLUTE &&
                rom.u8(offset + 6) == LOAD_A_ABSOLUTE &&
                rom.u8(offset + 9) == STORE_A_ABSOLUTE &&
                rom.u8(offset + 12) == LOAD_A_IMMEDIATE &&
                rom.u8(offset + 14) == STORE_A_ABSOLUTE &&
                rom.u8(offset + 17) == CALL &&
                rom.u8(offset + 20) == LOAD_DE_IMMEDIATE &&
                rom.u8(offset + 23) == POP_HL &&
                rom.u8(offset + 24) == RETURN
            ) {
                rom.u8(offset + 13).takeIf { it > 0 }?.let(::add)
            }
            offset++
        }
    }

    private fun moveNameAddresses(rom: RomImage, cancellation: ParserCancellationToken): Set<Int> = buildSet {
        val scanEnd = minOf(BANK_BYTES, rom.size)
        var offset = 0
        while (offset + NAME_POINTER_CONSUMER_BYTES <= scanEnd) {
            cancellation.throwIfCancellationRequested()
            if (
                rom.u8(offset) == LOAD_HL_IMMEDIATE &&
                rom.u8(offset + 3) == ADD_HL_DE &&
                rom.u8(offset + 4) == LOAD_A_INCREMENT_HL &&
                rom.u8(offset + 5) == STORE_A_HIGH &&
                rom.u8(offset + 7) == LOAD_A_HL &&
                rom.u8(offset + 8) == STORE_A_HIGH &&
                rom.u8(offset + 10) == LOAD_A_HIGH &&
                rom.u8(offset + 11) == rom.u8(offset + 9) &&
                rom.u8(offset + 12) == LOAD_H_FROM_A &&
                rom.u8(offset + 13) == LOAD_A_HIGH &&
                rom.u8(offset + 14) == rom.u8(offset + 6) &&
                rom.u8(offset + 15) == LOAD_L_FROM_A
            ) {
                val pointerTable = rom.u16le(offset + 1)
                if (pointerTable + MOVE_NAME_POINTER_OFFSET + POINTER_BYTES <= scanEnd) {
                    rom.u16le(pointerTable + MOVE_NAME_POINTER_OFFSET)
                        .takeIf { it in SWITCHABLE_ADDRESS_RANGE }
                        ?.let(::add)
                }
            }
            offset++
        }
    }

    private fun moveDataRoots(rom: RomImage, cancellation: ParserCancellationToken): Set<TableLayout> = buildSet {
        var offset = 0
        while (offset + MOVE_DATA_CONSUMER_BYTES <= rom.size) {
            cancellation.throwIfCancellationRequested()
            val recordSize = rom.u16le(offset + 5)
            if (
                rom.u8(offset) == DEC_A &&
                rom.u8(offset + 1) == LOAD_HL_IMMEDIATE &&
                rom.u8(offset + 4) == LOAD_BC_IMMEDIATE &&
                recordSize == MOVE_RECORD_BYTES &&
                rom.u8(offset + 7) == CALL &&
                rom.u8(offset + 10) == LOAD_DE_IMMEDIATE &&
                rom.u8(offset + 13) == LOAD_A_IMMEDIATE &&
                rom.u8(offset + 15) == CALL
            ) {
                val bank = rom.u8(offset + 14)
                val address = rom.u16le(offset + 2)
                if (bank > 0 && address in SWITCHABLE_ADDRESS_RANGE) {
                    rom.gbBankAddress(bank, address)?.let { root ->
                        add(TableLayout(root, 0, recordSize))
                    }
                }
            }
            offset++
        }
    }

    private fun consecutiveNameCount(
        rom: RomImage,
        root: Int,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
    ): Int? {
        var cursor = root
        var count = 0
        val end = minOf(rom.size.toLong(), (root / BANK_BYTES + 1L) * BANK_BYTES).toInt()
        while (count < MAX_MOVE_COUNT && cursor < end) {
            cancellation.throwIfCancellationRequested()
            val decoded = codec.decodeDetailed(rom, cursor, minOf(MAX_NAME_BYTES, end - cursor), cancellation)
            if (!decoded.terminated || decoded.text.isBlank() || decoded.validRatio < MINIMUM_NAME_RATIO) break
            cursor += decoded.consumedBytes
            count++
        }
        return count.takeIf { it >= MIN_MOVE_COUNT }
    }

    private val SWITCHABLE_ADDRESS_RANGE = 0x4000..0x7FFF
    private const val BANK_BYTES = 0x4000
    private const val MOVE_NAME_CONSUMER_BYTES = 25
    private const val NAME_POINTER_CONSUMER_BYTES = 16
    private const val MOVE_DATA_CONSUMER_BYTES = 18
    private const val MOVE_NAME_POINTER_OFFSET = 2
    private const val POINTER_BYTES = 2
    private const val MOVE_RECORD_BYTES = 6
    private const val MIN_MOVE_COUNT = 100
    private const val MAX_MOVE_COUNT = 255
    private const val MAX_NAME_BYTES = 24
    private const val MINIMUM_NAME_RATIO = 0.80
    private const val MOVE_NAME_LIST_TYPE = 2
    private const val LOAD_BC_IMMEDIATE = 0x01
    private const val LOAD_DE_IMMEDIATE = 0x11
    private const val LOAD_HL_IMMEDIATE = 0x21
    private const val DEC_A = 0x3D
    private const val LOAD_A_IMMEDIATE = 0x3E
    private const val LOAD_H_FROM_A = 0x67
    private const val LOAD_L_FROM_A = 0x6F
    private const val LOAD_A_HL = 0x7E
    private const val LOAD_A_INCREMENT_HL = 0x2A
    private const val ADD_HL_DE = 0x19
    private const val LOAD_A_HIGH = 0xF0
    private const val STORE_A_HIGH = 0xE0
    private const val LOAD_A_ABSOLUTE = 0xFA
    private const val STORE_A_ABSOLUTE = 0xEA
    private const val PUSH_HL = 0xE5
    private const val POP_HL = 0xE1
    private const val CALL = 0xCD
    private const val RETURN = 0xC9
}
