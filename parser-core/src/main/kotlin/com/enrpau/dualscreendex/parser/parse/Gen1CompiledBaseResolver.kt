package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.validate.TableValidators

/** Resolves a Gen I base-stat table from its complete compiled copy consumer. */
internal object Gen1CompiledBaseResolver {
    fun resolve(rom: RomImage, count: Int): TableLayout? {
        if (count !in 1..MAX_BASE_COUNT) return null
        val scanEnd = minOf(BANK_BYTES, rom.size)
        val candidates = buildList {
            var offset = 0
            while (offset + INDEX_CONSUMER_BYTES <= scanEnd) {
                parseConsumerAt(rom, offset, count)?.let(::add)
                offset++
            }
        }
        return candidates.distinct().singleOrNull()
    }

    private fun parseConsumerAt(rom: RomImage, offset: Int, count: Int): TableLayout? = runCatching {
        val recordSize = rom.u16le(offset + 2)
        if (
            rom.u8(offset) != DEC_A || rom.u8(offset + 1) != LOAD_BC_IMMEDIATE ||
            recordSize !in MIN_BASE_BYTES..MAX_BASE_BYTES ||
            rom.u8(offset + 4) != LOAD_HL_IMMEDIATE || rom.u8(offset + 7) != CALL ||
            rom.u8(offset + 10) != LOAD_DE_IMMEDIATE ||
            rom.u8(offset + 13) != LOAD_BC_IMMEDIATE ||
            rom.u16le(offset + 14) != recordSize || rom.u8(offset + 16) != CALL
        ) return@runCatching null

        val authority = findBankAuthority(rom, offset) ?: return@runCatching null
        if (!hasBankRestore(rom, offset + INDEX_CONSUMER_BYTES, authority)) return@runCatching null
        val root = rom.gbBankAddress(authority.bank, rom.u16le(offset + 5)) ?: return@runCatching null
        val evidence = TableValidators.baseStats(rom, root, count, recordSize, generation = 1)
        if (!evidence.compatible) return@runCatching null
        TableLayout(root, count, recordSize)
    }.getOrNull()

    private fun findBankAuthority(rom: RomImage, consumerOffset: Int): BankAuthority? {
        val candidates = buildList {
            val start = maxOf(0, consumerOffset - MAX_PROLOGUE_DISTANCE)
            var offset = start
            while (offset + PROLOGUE_BYTES <= consumerOffset) {
                parseBankAuthorityAt(rom, offset)?.let(::add)
                offset++
            }
        }
        return candidates.distinct().singleOrNull()
    }

    private fun parseBankAuthorityAt(rom: RomImage, offset: Int): BankAuthority? {
        if (
            rom.u8(offset) != LOAD_A_HIGH || rom.u8(offset + 2) != PUSH_AF ||
            rom.u8(offset + 3) != LOAD_A_IMMEDIATE || rom.u8(offset + 5) != STORE_A_HIGH ||
            rom.u8(offset + 6) != rom.u8(offset + 1) ||
            rom.u8(offset + 7) != STORE_A_ABSOLUTE ||
            rom.u8(offset + 10) != PUSH_BC || rom.u8(offset + 11) != PUSH_DE ||
            rom.u8(offset + 12) != PUSH_HL
        ) return null
        val bank = rom.u8(offset + 4)
        val mbcAddress = rom.u16le(offset + 8)
        if (bank == 0 || mbcAddress !in MBC_BANK_ADDRESS_RANGE) return null
        return BankAuthority(bank, rom.u8(offset + 1), mbcAddress)
    }

    private fun hasBankRestore(rom: RomImage, start: Int, authority: BankAuthority): Boolean {
        val end = minOf(rom.size - RESTORE_BYTES + 1, start + MAX_RESTORE_DISTANCE)
        var offset = start
        while (offset < end) {
            if (
                rom.u8(offset) == POP_HL && rom.u8(offset + 1) == POP_DE &&
                rom.u8(offset + 2) == POP_BC && rom.u8(offset + 3) == POP_AF &&
                rom.u8(offset + 4) == STORE_A_HIGH &&
                rom.u8(offset + 5) == authority.bankState &&
                rom.u8(offset + 6) == STORE_A_ABSOLUTE &&
                rom.u16le(offset + 7) == authority.mbcAddress &&
                rom.u8(offset + 9) == RETURN
            ) return true
            offset++
        }
        return false
    }

    private data class BankAuthority(
        val bank: Int,
        val bankState: Int,
        val mbcAddress: Int,
    )

    private val MBC_BANK_ADDRESS_RANGE = 0x2000..0x3fff
    private const val BANK_BYTES = 0x4000
    private const val INDEX_CONSUMER_BYTES = 19
    private const val PROLOGUE_BYTES = 13
    private const val RESTORE_BYTES = 10
    private const val MAX_PROLOGUE_DISTANCE = 128
    private const val MAX_RESTORE_DISTANCE = 128
    private const val MIN_BASE_BYTES = 20
    private const val MAX_BASE_BYTES = 64
    private const val MAX_BASE_COUNT = 255
    private const val LOAD_BC_IMMEDIATE = 0x01
    private const val LOAD_DE_IMMEDIATE = 0x11
    private const val LOAD_HL_IMMEDIATE = 0x21
    private const val DEC_A = 0x3d
    private const val LOAD_A_IMMEDIATE = 0x3e
    private const val LOAD_A_HIGH = 0xf0
    private const val STORE_A_HIGH = 0xe0
    private const val STORE_A_ABSOLUTE = 0xea
    private const val PUSH_BC = 0xc5
    private const val PUSH_DE = 0xd5
    private const val PUSH_HL = 0xe5
    private const val PUSH_AF = 0xf5
    private const val POP_BC = 0xc1
    private const val POP_DE = 0xd1
    private const val POP_HL = 0xe1
    private const val POP_AF = 0xf1
    private const val CALL = 0xcd
    private const val RETURN = 0xc9
}
