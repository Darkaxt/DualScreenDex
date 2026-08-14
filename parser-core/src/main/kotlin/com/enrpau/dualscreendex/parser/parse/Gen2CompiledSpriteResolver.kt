package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.validate.SpriteValidators

/** Resolves a Gen 2 sprite table from a complete compiled picture-table consumer. */
internal object Gen2CompiledSpriteResolver {
    private const val RECORD_SIZE = 6
    private const val UNOWN_FORM_COUNT = 26
    private const val NORMAL_CONSUMER_BYTES = 41
    private const val VARIANT_CONSUMER_BYTES = 16
    private const val VARIANT_ROW_BYTES = 4
    private const val MAX_VARIANT_ROWS = 64

    fun resolve(rom: RomImage, speciesCount: Int): TableLayout? {
        if (speciesCount !in 1..255) return null
        val candidates = buildList {
            rom.findAll(byteArrayOf(LOAD_A_ABSOLUTE.toByte())).mapNotNullTo(this) { offset ->
                parseNormalConsumer(rom, offset, speciesCount)
            }
            rom.findAll(byteArrayOf(LOAD_HL_IMMEDIATE.toByte())).mapNotNullTo(this) { offset ->
                parseVariantConsumer(rom, offset, speciesCount)
            }
        }
        return candidates.distinct().singleOrNull()
    }

    private fun parseNormalConsumer(
        rom: RomImage,
        offset: Int,
        speciesCount: Int,
    ): TableLayout? = runCatching {
        if (
            offset + NORMAL_CONSUMER_BYTES > rom.size ||
            rom.u8(offset + 3) != COMPARE_IMMEDIATE ||
            rom.u8(offset + 4) !in 1..speciesCount ||
            rom.u8(offset + 5) != JR_Z ||
            branchTarget(offset + 5, rom.u8(offset + 6)) != offset + 14 ||
            rom.u8(offset + 7) != LOAD_A_ABSOLUTE ||
            rom.u16le(offset + 8) != rom.u16le(offset + 1) ||
            rom.u8(offset + 10) != LOAD_D_IMMEDIATE ||
            rom.u8(offset + 12) != JR ||
            branchTarget(offset + 12, rom.u8(offset + 13)) != offset + 19 ||
            rom.u8(offset + 14) != LOAD_A_ABSOLUTE ||
            rom.u8(offset + 17) != LOAD_D_IMMEDIATE ||
            rom.u8(offset + 19) != LOAD_HL_IMMEDIATE ||
            rom.u8(offset + 22) != DEC_A ||
            rom.u8(offset + 23) != LOAD_BC_IMMEDIATE ||
            rom.u16le(offset + 24) != RECORD_SIZE ||
            rom.u8(offset + 26) != CALL ||
            rom.u8(offset + 29) != LOAD_A_D ||
            rom.u8(offset + 30) != CALL ||
            rom.u8(offset + 33) != PUSH_AF ||
            rom.u8(offset + 34) != INC_HL ||
            rom.u8(offset + 35) != LOAD_A_D ||
            rom.u8(offset + 36) != CALL ||
            rom.u8(offset + 39) != POP_BC ||
            rom.u8(offset + 40) != RETURN
        ) return@runCatching null

        val pointer = rom.u16le(offset + 20)
        val normalRoot = rom.gbBankAddress(rom.u8(offset + 11), pointer)
            ?: return@runCatching null
        val unownRoot = rom.gbBankAddress(rom.u8(offset + 18), pointer)
            ?: return@runCatching null
        if (normalRoot == unownRoot) return@runCatching null
        if (!SpriteValidators.gen2(rom, normalRoot, speciesCount, 0).compatible) {
            return@runCatching null
        }
        if (!SpriteValidators.gen2(rom, unownRoot, UNOWN_FORM_COUNT, 0).compatible) {
            return@runCatching null
        }
        TableLayout(normalRoot, speciesCount, RECORD_SIZE)
    }.getOrNull()

    private fun parseVariantConsumer(
        rom: RomImage,
        offset: Int,
        speciesCount: Int,
    ): TableLayout? = runCatching {
        if (
            offset + VARIANT_CONSUMER_BYTES > rom.size ||
            rom.u8(offset) != LOAD_HL_IMMEDIATE ||
            rom.u8(offset + 3) != LOAD_DE_IMMEDIATE ||
            rom.u16le(offset + 4) != VARIANT_ROW_BYTES ||
            rom.u8(offset + 6) != CALL ||
            rom.u8(offset + 9) != INC_HL ||
            rom.u8(offset + 10) != LOAD_A_HL_INCREMENT ||
            rom.u8(offset + 11) != LOAD_D_A ||
            rom.u8(offset + 12) != LOAD_A_HL_INCREMENT ||
            rom.u8(offset + 13) != LOAD_H_HL ||
            rom.u8(offset + 14) != LOAD_L_A ||
            rom.u8(offset + 15) != RETURN
        ) return@runCatching null

        val consumerBank = offset / BANK_BYTES
        val table = rom.gbBankAddress(consumerBank, rom.u16le(offset + 1)) ?: return@runCatching null
        val species = linkedSetOf<Int>()
        var cursor = table
        repeat(MAX_VARIANT_ROWS) {
            if (cursor + VARIANT_ROW_BYTES > rom.size) return@runCatching null
            val id = rom.u8(cursor)
            val root = rom.gbBankAddress(rom.u8(cursor + 1), rom.u16le(cursor + 2))
                ?: return@runCatching null
            if (id == END_MARKER) {
                if (species.isEmpty()) return@runCatching null
                if (!SpriteValidators.gen2(rom, root, speciesCount, 0).compatible) {
                    return@runCatching null
                }
                return@runCatching TableLayout(root, speciesCount, RECORD_SIZE)
            }
            if (id !in 1..speciesCount || !species.add(id)) return@runCatching null
            if (!SpriteValidators.gen2(rom, root, 1, 0).compatible) return@runCatching null
            cursor += VARIANT_ROW_BYTES
        }
        null
    }.getOrNull()

    private fun branchTarget(opcodeOffset: Int, encodedDelta: Int): Int =
        opcodeOffset + 2 + encodedDelta.toByte().toInt()

    private const val BANK_BYTES = 0x4000
    private const val END_MARKER = 0xff
    private const val LOAD_BC_IMMEDIATE = 0x01
    private const val LOAD_DE_IMMEDIATE = 0x11
    private const val LOAD_HL_IMMEDIATE = 0x21
    private const val LOAD_D_IMMEDIATE = 0x16
    private const val LOAD_A_D = 0x7A
    private const val LOAD_D_A = 0x57
    private const val LOAD_H_HL = 0x66
    private const val LOAD_L_A = 0x6F
    private const val LOAD_A_ABSOLUTE = 0xFA
    private const val LOAD_A_HL_INCREMENT = 0x2A
    private const val COMPARE_IMMEDIATE = 0xFE
    private const val DEC_A = 0x3D
    private const val INC_HL = 0x23
    private const val PUSH_AF = 0xF5
    private const val POP_BC = 0xC1
    private const val CALL = 0xCD
    private const val RETURN = 0xC9
    private const val JR = 0x18
    private const val JR_Z = 0x28
}
