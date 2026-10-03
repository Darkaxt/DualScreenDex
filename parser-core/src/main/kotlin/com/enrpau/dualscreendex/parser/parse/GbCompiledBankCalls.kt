package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage

/** Home-bank call authority for bank-local consumers; no routine addresses or bank numbers are inherited. */
internal object GbCompiledBankCalls {
    data class Target(val callerOffset: Int, val offset: Int)

    fun discover(
        rom: RomImage,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): List<Target> {
        cancellation.throwIfCancellationRequested()
        val end = minOf(BANK_BYTES, rom.size)
        return buildList {
            for (offset in 0 until end) {
                cancellation.throwIfCancellationRequested()
                farCallAt(rom, offset, end)?.let(::add)
                bankedCallAt(rom, offset, end)?.let(::add)
            }
        }.distinct()
    }

    fun repeatedAdd(rom: RomImage, address: Int): Boolean =
        homeBytes(rom, address, 0xA7, 0xC8, 0x09, 0x3D, 0x20, 0xFC, 0xC9)

    fun restartCopy(rom: RomImage, opcode: Int): Boolean = restartTarget(rom, opcode)?.let { address ->
        byteCopy(rom, address)
    } == true

    fun byteCopy(rom: RomImage, address: Int): Boolean =
        homeBytes(rom, address, 0x2A, 0x12, 0x13, 0x0B, 0x79, 0xB0, 0x20, 0xF8, 0xC9) ||
            homeBytes(rom, address, 0x78, 0xA7, 0x28, 0x0C, 0x79, 0xA7, 0x28, 0x01, 0x04, 0xCD) &&
            homeBytes(rom, address + 12, 0x05, 0x20, 0xFA, 0xC9, 0x2A, 0x12, 0x13, 0x0D, 0x20, 0xFA, 0xC9) &&
            rom.u16le(address + 10) == address + 16

    fun stringCopy(rom: RomImage, address: Int, terminator: Int): Boolean =
        homeBytes(rom, address, 0x1A, 0x13, 0x22, 0xFE, terminator, 0x20, 0xF9, 0xC9)

    private fun farCallAt(rom: RomImage, offset: Int, end: Int): Target? {
        if (offset + 6 > end) return null
        val address: Int
        val bank: Int
        when {
            rom.u8(offset) == 0x21 && rom.u8(offset + 3) == 0x06 -> {
                address = rom.u16le(offset + 1)
                bank = rom.u8(offset + 4)
            }
            rom.u8(offset) == 0x06 && rom.u8(offset + 2) == 0x21 -> {
                bank = rom.u8(offset + 1)
                address = rom.u16le(offset + 3)
            }
            else -> return null
        }
        val routine = restartTarget(rom, rom.u8(offset + 5)) ?: return null
        if (routine + 18 > end || !homeBytes(rom, routine, 0xF0)) return null
        val register = rom.u8(routine + 1)
        val setBank = rom.u16le(routine + 5)
        val hlCaller = rom.u16le(routine + 8)
        if (
            register !in 0x80..0xFE ||
            !homeBytes(rom, routine + 2, 0xF5, 0x78, 0xCD) ||
            rom.u8(routine + 7) != 0xCD ||
            !homeBytes(rom, hlCaller, 0xE9) ||
            !homeBytes(rom, routine + 10, 0xC1, 0x78, 0xE0, register, 0xEA) ||
            rom.u8(routine + 17) != 0xC9 ||
            !bankStore(rom, setBank, register) ||
            rom.u16le(routine + 15) != rom.u16le(setBank + 3)
        ) return null
        return target(rom, offset, bank, address)
    }

    private fun bankedCallAt(rom: RomImage, offset: Int, end: Int): Target? {
        if (offset + 15 > end || rom.u8(offset) != 0xF0) return null
        val register = rom.u8(offset + 1)
        if (register !in 0x80..0xFE || !homeBytes(rom, offset + 2, 0xF5, 0x3E) ||
            rom.u8(offset + 5) != 0xCD || rom.u8(offset + 8) != 0xCD ||
            rom.u8(offset + 11) != 0xF1 ||
            !(rom.u8(offset + 12) == 0xC3 ||
                rom.u8(offset + 12) == 0xCD && homeBytes(rom, offset + 15, 0xC9))
        ) return null
        val setBank = rom.u16le(offset + 6)
        if (rom.u16le(offset + 13) != setBank || !bankStore(rom, setBank, register)) return null
        return target(rom, offset, rom.u8(offset + 4), rom.u16le(offset + 9))
    }

    internal fun bankStore(rom: RomImage, address: Int, register: Int): Boolean =
        address >= 0 && address + 6 <= minOf(BANK_BYTES, rom.size) &&
            homeBytes(rom, address, 0xE0, register, 0xEA) &&
            rom.u16le(address + 3) in 0x2000..0x3FFF && rom.u8(address + 5) == 0xC9

    private fun target(rom: RomImage, caller: Int, bank: Int, address: Int): Target? {
        if (bank <= 0 || address !in 0x4000..0x7FFF) return null
        return rom.gbBankAddress(bank, address)?.let { Target(caller, it) }
    }

    private fun restartTarget(rom: RomImage, opcode: Int): Int? {
        if (opcode and 0xC7 != 0xC7) return null
        val vector = opcode and 0x38
        if (vector + 3 > minOf(BANK_BYTES, rom.size)) return null
        val address = if (rom.u8(vector) == 0xC3) rom.u16le(vector + 1) else vector
        return address.takeIf { it in 0 until minOf(BANK_BYTES, rom.size) }
    }

    private fun homeBytes(rom: RomImage, offset: Int, vararg values: Int): Boolean =
        offset >= 0 && offset.toLong() + values.size <= minOf(BANK_BYTES, rom.size) &&
            values.indices.all { rom.u8(offset + it) == values[it] }

    private const val BANK_BYTES = 0x4000
}
