package com.enrpau.dualscreendex.parser.parse

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
    ): Gen1CompiledMoveResolution? {
        val nameRoots = moveNameRoots(rom)
        val dataRoots = moveDataRoots(rom)
        if (nameRoots.isEmpty() || dataRoots.isEmpty()) return null

        val candidates = buildSet {
            nameRoots.forEach { nameRoot ->
                val count = consecutiveNameCount(rom, nameRoot, codec) ?: return@forEach
                dataRoots.forEach { dataRoot ->
                    val evidence = TableValidators.moveData(
                        rom = rom,
                        offset = dataRoot.offset,
                        count = count,
                        recordSize = dataRoot.recordSize,
                        generation = 1,
                    )
                    if (evidence.compatible) {
                        add(
                            Gen1CompiledMoveResolution(
                                moveNames = TableLayout(nameRoot, count, 0, variableLength = true),
                                moveData = dataRoot.copy(count = count),
                            ),
                        )
                    }
                }
            }
        }
        return candidates.singleOrNull()
    }

    private fun moveNameRoots(rom: RomImage): Set<Int> {
        val banks = moveNameBanks(rom)
        val addresses = moveNameAddresses(rom)
        return buildSet {
            banks.forEach { bank ->
                addresses.forEach { address ->
                    rom.gbBankAddress(bank, address)?.let(::add)
                }
            }
        }
    }

    private fun moveNameBanks(rom: RomImage): Set<Int> = buildSet {
        val scanEnd = minOf(BANK_BYTES, rom.size)
        var offset = 0
        while (offset + MOVE_NAME_CONSUMER_BYTES <= scanEnd) {
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

    private fun moveNameAddresses(rom: RomImage): Set<Int> = buildSet {
        val scanEnd = minOf(BANK_BYTES, rom.size)
        var offset = 0
        while (offset + NAME_POINTER_CONSUMER_BYTES <= scanEnd) {
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

    private fun moveDataRoots(rom: RomImage): Set<TableLayout> = buildSet {
        var offset = 0
        while (offset + MOVE_DATA_CONSUMER_BYTES <= rom.size) {
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
    ): Int? {
        var cursor = root
        var count = 0
        while (count < MAX_MOVE_COUNT) {
            val bytes = ArrayList<Byte>()
            var terminated = false
            repeat(MAX_NAME_BYTES) {
                if (!terminated && cursor < rom.size) {
                    val value = rom.u8(cursor++)
                    bytes += value.toByte()
                    terminated = value == codec.terminator
                }
            }
            if (!terminated) break
            val decoded = codec.decodeDetailed(bytes.toByteArray())
            if (decoded.text.isBlank() || decoded.validRatio < MINIMUM_NAME_RATIO) break
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
