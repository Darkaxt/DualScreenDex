package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.validate.TableValidators

internal data class Gen1CompiledBaseResolution(val table: TableLayout, val bankLocal: Boolean)

/** Resolves a Gen I base-stat table from its complete compiled copy consumer. */
internal object Gen1CompiledBaseResolver {
    fun resolve(
        rom: RomImage,
        count: Int,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): TableLayout? = resolveWithAuthority(rom, count, cancellation)?.table

    fun resolveWithAuthority(
        rom: RomImage,
        count: Int,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): Gen1CompiledBaseResolution? {
        cancellation.throwIfCancellationRequested()
        if (count !in 1..MAX_BASE_COUNT) return null
        val scanEnd = minOf(BANK_BYTES, rom.size)
        val bankLocalCandidates = buildList {
            GbCompiledBankCalls.discover(rom, cancellation).forEach { target ->
                val end = minOf(rom.size, (target.offset / BANK_BYTES + 1) * BANK_BYTES, target.offset + MAX_PROLOGUE_DISTANCE)
                for (offset in target.offset..end - BANK_LOCAL_CONSUMER_BYTES) {
                    cancellation.throwIfCancellationRequested()
                    bankLocalConsumerAt(rom, offset, count)?.let(::add)
                }
            }
        }
        val candidates = buildList {
            addAll(bankLocalCandidates)
            var offset = 0
            while (offset + MIN_INDEX_CONSUMER_BYTES <= scanEnd) {
                cancellation.throwIfCancellationRequested()
                parseConsumerAt(rom, offset, count)?.let(::add)
                offset++
            }
        }
        val table = candidates.distinct().singleOrNull() ?: return null
        return Gen1CompiledBaseResolution(table, table in bankLocalCandidates)
    }

    internal fun bankLocalConsumerAt(rom: RomImage, offset: Int, count: Int): TableLayout? {
        if (rom.u8(offset) != DEC_A || rom.u8(offset + 1) != LOAD_BC_IMMEDIATE ||
            rom.u8(offset + 4) != LOAD_HL_IMMEDIATE || rom.u8(offset + 7) != CALL ||
            !GbCompiledBankCalls.repeatedAdd(rom, rom.u16le(offset + 8)) ||
            rom.u8(offset + 10) != LOAD_BC_IMMEDIATE || rom.u8(offset + 13) != LOAD_DE_IMMEDIATE ||
            rom.u16le(offset + 14) !in 0xC000..0xDFFF ||
            !GbCompiledBankCalls.restartCopy(rom, rom.u8(offset + 16))
        ) return null
        val stride = rom.u16le(offset + 2)
        val address = rom.u16le(offset + 5)
        if (stride !in MIN_BASE_BYTES..MAX_BASE_BYTES || rom.u16le(offset + 11) != stride ||
            address !in 0x4000..0x7FFF || rom.u16le(offset + 14) + stride > 0xE000
        ) return null
        val bank = offset / BANK_BYTES
        val root = rom.gbBankAddress(bank, address) ?: return null
        if (root.toLong() + count.toLong() * stride > minOf(rom.size, (bank + 1) * BANK_BYTES) ||
            (0 until count).any { rom.u8(root + it * stride) != it + 1 }
        ) return null
        val evidence = TableValidators.baseStats(rom, root, count, stride, generation = 1)
        return TableLayout(root, count, stride).takeIf { evidence.compatible }
    }

    private fun parseConsumerAt(rom: RomImage, offset: Int, count: Int): TableLayout? = runCatching {
        val recordSize = rom.u16le(offset + 2)
        val restartCopy = rom.u8(offset + 16) != CALL
        val copyBytes = if (restartCopy) 1 else 3
        if (offset + 16 + copyBytes > minOf(BANK_BYTES, rom.size)) return@runCatching null
        if (
            rom.u8(offset) != DEC_A || rom.u8(offset + 1) != LOAD_BC_IMMEDIATE ||
            recordSize !in MIN_BASE_BYTES..MAX_BASE_BYTES ||
            rom.u8(offset + 4) != LOAD_HL_IMMEDIATE || rom.u8(offset + 7) != CALL ||
            rom.u8(offset + 10) != LOAD_DE_IMMEDIATE ||
            rom.u8(offset + 13) != LOAD_BC_IMMEDIATE ||
            rom.u16le(offset + 14) != recordSize ||
            restartCopy && !GbCompiledBankCalls.restartCopy(rom, rom.u8(offset + 16))
        ) return@runCatching null

        val authority = findBankAuthority(rom, offset) ?: return@runCatching null
        if (!hasBankRestore(rom, offset + 16 + copyBytes, authority)) return@runCatching null
        if ((restartCopy || authority.storeCall != null) && (
            !GbCompiledBankCalls.repeatedAdd(rom, rom.u16le(offset + 8)) ||
            !restartCopy && !GbCompiledBankCalls.byteCopy(rom, rom.u16le(offset + 17)) ||
            rom.u16le(offset + 11) !in 0xC000..0xDFFF ||
            rom.u16le(offset + 11) + recordSize > 0xE000 ||
            rom.u16le(offset + 5) !in 0x4000..0x7FFF
        )) return@runCatching null
        val root = rom.gbBankAddress(authority.bank, rom.u16le(offset + 5)) ?: return@runCatching null
        if ((restartCopy || authority.storeCall != null) &&
            root.toLong() + count.toLong() * recordSize > minOf(rom.size, (authority.bank + 1) * BANK_BYTES)
        ) return@runCatching null
        val evidence = TableValidators.baseStats(rom, root, count, recordSize, generation = 1)
        if (!evidence.compatible) return@runCatching null
        TableLayout(root, count, recordSize)
    }.getOrNull()

    private fun findBankAuthority(rom: RomImage, consumerOffset: Int): BankAuthority? {
        val candidates = buildList {
            val start = maxOf(0, consumerOffset - MAX_PROLOGUE_DISTANCE)
            var offset = start
            while (offset + HELPER_PROLOGUE_BYTES <= consumerOffset) {
                parseBankAuthorityAt(rom, offset, consumerOffset)?.let(::add)
                offset++
            }
        }
        return candidates.distinct().singleOrNull()
    }

    private fun parseBankAuthorityAt(rom: RomImage, offset: Int, consumerOffset: Int): BankAuthority? {
        if (rom.u8(offset) != LOAD_A_HIGH || rom.u8(offset + 2) != PUSH_AF ||
            rom.u8(offset + 3) != LOAD_A_IMMEDIATE
        ) return null
        if (rom.u8(offset + 5) == CALL && rom.u8(offset + 8) == PUSH_BC &&
            rom.u8(offset + 9) == PUSH_DE && rom.u8(offset + 10) == PUSH_HL
        ) {
            val bank = rom.u8(offset + 4)
            val register = rom.u8(offset + 1)
            val setBank = rom.u16le(offset + 6)
            if (bank <= 0 || register !in 0x80..0xFE ||
                !GbCompiledBankCalls.bankStore(rom, setBank, register)
            ) return null
            return BankAuthority(bank, register, rom.u16le(setBank + 3), setBank)
        }
        if (offset + PROLOGUE_BYTES > consumerOffset) return null
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
        val restoreBytes = if (authority.storeCall != null) HELPER_RESTORE_BYTES else RESTORE_BYTES
        val end = minOf(minOf(BANK_BYTES, rom.size) - restoreBytes + 1, start + MAX_RESTORE_DISTANCE)
        var offset = start
        while (offset < end) {
            if (authority.storeCall != null &&
                rom.u8(offset) == POP_HL && rom.u8(offset + 1) == POP_DE &&
                rom.u8(offset + 2) == POP_BC && rom.u8(offset + 3) == POP_AF &&
                rom.u8(offset + 4) == CALL && rom.u16le(offset + 5) == authority.storeCall &&
                rom.u8(offset + 7) == RETURN
            ) return true
            if (authority.storeCall == null &&
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
        val storeCall: Int? = null,
    )

    private val MBC_BANK_ADDRESS_RANGE = 0x2000..0x3fff
    private const val BANK_BYTES = 0x4000
    private const val MIN_INDEX_CONSUMER_BYTES = 17
    private const val BANK_LOCAL_CONSUMER_BYTES = 17
    private const val PROLOGUE_BYTES = 13
    private const val HELPER_PROLOGUE_BYTES = 11
    private const val RESTORE_BYTES = 10
    private const val HELPER_RESTORE_BYTES = 8
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
