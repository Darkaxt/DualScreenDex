package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.validate.TableValidators

/** Resolves the standard Gen 2 move table from a complete compiled move-type print consumer. */
internal object Gen2CompiledMoveResolver {
    private const val RECORD_SIZE = 7
    private const val TYPE_FIELD_OFFSET = 3
    private const val CONSUMER_BYTES = 42

    fun resolve(rom: RomImage, moveCount: Int): TableLayout? {
        if (moveCount !in 1..255) return null
        val prefix = byteArrayOf(
            PUSH_HL.toByte(),
            LOAD_A_B.toByte(),
            DEC_A.toByte(),
            LOAD_BC_IMMEDIATE.toByte(),
            RECORD_SIZE.toByte(),
            0,
            LOAD_HL_IMMEDIATE.toByte(),
        )
        val candidates = rom.findAll(prefix).mapNotNull { offset ->
            parseConsumer(rom, offset, moveCount)
        }
        return candidates.distinct().singleOrNull()
    }

    private fun parseConsumer(
        rom: RomImage,
        offset: Int,
        moveCount: Int,
    ): TableLayout? = runCatching {
        if (
            offset + CONSUMER_BYTES > rom.size ||
            rom.u8(offset + 9) != CALL ||
            rom.u8(offset + 12) != LOAD_DE_IMMEDIATE ||
            rom.u8(offset + 15) != LOAD_A_IMMEDIATE ||
            rom.u8(offset + 17) != CALL ||
            rom.u8(offset + 20) != LOAD_A_ABSOLUTE ||
            rom.u16le(offset + 21) != rom.u16le(offset + 13) + TYPE_FIELD_OFFSET ||
            rom.u8(offset + 23) != POP_HL ||
            rom.u8(offset + 24) != LOAD_B_A ||
            rom.u8(offset + 25) != LOAD_A_B ||
            rom.u8(offset + 26) != PUSH_HL ||
            rom.u8(offset + 27) != ADD_A ||
            rom.u8(offset + 28) != LOAD_HL_IMMEDIATE ||
            rom.u8(offset + 31) != LOAD_E_A ||
            rom.u8(offset + 32) != LOAD_D_IMMEDIATE ||
            rom.u8(offset + 33) != 0 ||
            rom.u8(offset + 34) != ADD_HL_DE ||
            rom.u8(offset + 35) != LOAD_A_HL_INCREMENT ||
            rom.u8(offset + 36) != LOAD_E_A ||
            rom.u8(offset + 37) != LOAD_D_HL ||
            rom.u8(offset + 38) != POP_HL ||
            rom.u8(offset + 39) != JUMP
        ) return@runCatching null
        val root = rom.gbBankAddress(
            rom.u8(offset + 16),
            rom.u16le(offset + 7),
        ) ?: return@runCatching null
        val evidence = TableValidators.moveData(rom, root, moveCount, RECORD_SIZE, 2)
        if (!evidence.compatible) return@runCatching null
        TableLayout(root, moveCount, RECORD_SIZE)
    }.getOrNull()

    private const val LOAD_BC_IMMEDIATE = 0x01
    private const val LOAD_DE_IMMEDIATE = 0x11
    private const val LOAD_HL_IMMEDIATE = 0x21
    private const val LOAD_D_IMMEDIATE = 0x16
    private const val LOAD_A_B = 0x78
    private const val LOAD_B_A = 0x47
    private const val LOAD_E_A = 0x5F
    private const val LOAD_D_HL = 0x56
    private const val LOAD_A_HL_INCREMENT = 0x2A
    private const val LOAD_A_IMMEDIATE = 0x3E
    private const val LOAD_A_ABSOLUTE = 0xFA
    private const val ADD_A = 0x87
    private const val ADD_HL_DE = 0x19
    private const val DEC_A = 0x3D
    private const val PUSH_HL = 0xE5
    private const val POP_HL = 0xE1
    private const val CALL = 0xCD
    private const val JUMP = 0xC3
}
