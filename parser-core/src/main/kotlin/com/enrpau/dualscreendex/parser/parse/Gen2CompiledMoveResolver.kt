package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
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
        val extended = rom.findAll(byteArrayOf(PUSH_HL.toByte(), LOAD_A_B.toByte())).mapNotNull { offset ->
            parseExtendedConsumer(rom, offset, moveCount)
        }
        return (candidates + extended).distinct().singleOrNull()
    }

    fun maskedEvidence(rom: RomImage, table: TableLayout): ValidationEvidence? {
        if (table.format != TableRecordFormat.GEN2_MASKED_MOVE_7 || table.recordSize != RECORD_SIZE ||
            table.count !in 1..255 || table.offset < 0 ||
            table.offset.toLong() + table.count * RECORD_SIZE > rom.size ||
            table.offset % BANK_BYTES + table.count * RECORD_SIZE > BANK_BYTES ||
            (0 until table.count).any { index ->
                val row = table.offset + index * RECORD_SIZE
                rom.u8(row) != index + 1 || (rom.u8(row + TYPE_FIELD_OFFSET) and 0x3F) !in 0..27 ||
                    rom.u8(row + 5) !in 1..64
            }
        ) return null
        return ValidationEvidence(true, table.count, table.count, 1.0,
            listOf("complete compiled seven-byte moves with masked native type field"), table.offset, RECORD_SIZE)
    }

    private fun parseExtendedConsumer(rom: RomImage, site: Int, count: Int): TableLayout? {
        if (site < 0 || site.toLong() + 44 > rom.size || site % BANK_BYTES + 44 > BANK_BYTES) return null
        val masked = bytesAt(rom, site + 2, 0x3D, 0x01, RECORD_SIZE, 0, 0x21)
        val branched = bytesAt(rom, site + 2, 0xFE) && rom.u8(site + 3) in 1..count &&
            bytesAt(rom, site + 4, 0x28) && bytesAt(rom, site + 6, 0x3D, 0x01, RECORD_SIZE, 0, 0x21)
        if (!masked && !branched) return null
        val shift = if (branched) 4 else 0
        val copy = site + shift
        if (!bytesAt(rom, copy + 9, 0xCD) || !bytesAt(rom, copy + 12, 0x11) ||
            !bytesAt(rom, copy + 15, 0x3E) || !bytesAt(rom, copy + 17, 0xCD) ||
            !bytesAt(rom, copy + 20, 0xFA) ||
            rom.u16le(copy + 21) != rom.u16le(copy + 13) + TYPE_FIELD_OFFSET ||
            rom.u16le(copy + 13) !in 0xC000..0xDFFF - RECORD_SIZE + 1 ||
            !GbCompiledBankCalls.repeatedAdd(rom, rom.u16le(copy + 10)) ||
            !classicFarCopy(rom, rom.u16le(copy + 18))
        ) return null
        val printer: Int
        if (masked) {
            if (!bytesAt(rom, copy + 23, 0xE6, 0x3F, 0xE1, 0x47)) return null
            printer = copy + 27
        } else {
            if (!bytesAt(rom, copy + 23, 0xE1, 0x47, 0x18, 0x05, 0xCD) ||
                !bytesAt(rom, copy + 30, 0xE1, 0x47) ||
                site + 6 + rom.u8(site + 5).toByte().toInt() != copy + 27 ||
                rom.gbBankAddress(site / BANK_BYTES, rom.u16le(copy + 28)) == null
            ) return null
            printer = copy + 32
        }
        if (!bytesAt(rom, printer, 0x78, 0xE5, 0x87, 0x21) ||
            !bytesAt(rom, printer + 6, 0x5F, 0x16, 0, 0x19, 0x2A, 0x5F, 0x56, 0xE1, 0xC3) ||
            site / BANK_BYTES != (printer + 16) / BANK_BYTES
        ) return null
        val bank = rom.u8(copy + 16)
        val address = rom.u16le(copy + 7)
        if (bank <= 0 || address !in 0x4000..0x7FFF) return null
        val root = rom.gbBankAddress(bank, address) ?: return null
        val table = TableLayout(root, count, RECORD_SIZE,
            format = if (masked) TableRecordFormat.GEN2_MASKED_MOVE_7 else TableRecordFormat.STANDARD)
        // Full record validation also bounds the unmasked branch without trimming a requested name domain.
        if (maskedEvidence(rom, table.copy(format = TableRecordFormat.GEN2_MASKED_MOVE_7)) == null ||
            !masked && (0 until count).any { rom.u8(root + it * RECORD_SIZE + TYPE_FIELD_OFFSET) !in 0..27 }
        ) return null
        return table
    }

    private fun classicFarCopy(rom: RomImage, address: Int): Boolean {
        if (address < 0 || address + 14 > minOf(BANK_BYTES, rom.size) ||
            !bytesAt(rom, address, 0xE0) || !bytesAt(rom, address + 2, 0xF0)
        ) return false
        val scratch = rom.u8(address + 1)
        val saved = rom.u8(address + 3)
        if (scratch !in 0x80..0xFE || saved !in 0x80..0xFE || scratch == saved ||
            !bytesAt(rom, address + 4, 0xF5, 0xF0, scratch, 0xD7, 0xCD) ||
            !bytesAt(rom, address + 11, 0xF1, 0xD7, 0xC9) ||
            !GbCompiledBankCalls.byteCopy(rom, rom.u16le(address + 9))
        ) return false
        val switch = if (rom.u8(0x10) == 0xC3) rom.u16le(0x11) else 0x10
        return GbCompiledBankCalls.bankStore(rom, switch, saved)
    }

    private fun bytesAt(rom: RomImage, offset: Int, vararg values: Int): Boolean =
        offset >= 0 && offset.toLong() + values.size <= rom.size &&
            offset % BANK_BYTES + values.size <= BANK_BYTES &&
            values.indices.all { rom.u8(offset + it) == values[it] }

    private const val BANK_BYTES = 0x4000

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
