package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage

internal data class GbCompiledFarCopyAuthority(val bankRegister: Int, val maximumBank: Int)

/** Complete home-bank far-copy/read authority, including the saved-bank restoration chain. */
internal object GbCompiledFarCopy {
    fun resolve(rom: RomImage, address: Int): GbCompiledFarCopyAuthority? {
        if (!within(rom, address, 3) || rom.u8(address) != 0xCD ||
            !GbCompiledBankCalls.byteCopy(rom, address + 3)
        ) return null
        val wrapper = rom.u16le(address + 1)
        return stackRewritten(rom, wrapper) ?: hramDispatched(rom, wrapper)
    }

    fun readByte(rom: RomImage, address: Int): GbCompiledFarCopyAuthority? {
        if (!within(rom, address, 5) || rom.u8(address) != 0xCD ||
            !homeBytes(rom, address + 3, 0x7E, 0xC9)
        ) return null
        val wrapper = rom.u16le(address + 1)
        return stackRewritten(rom, wrapper) ?: hramDispatched(rom, wrapper)
    }

    private fun stackRewritten(rom: RomImage, wrapper: Int): GbCompiledFarCopyAuthority? {
        if (!within(rom, wrapper, 27) ||
            !homeBytes(rom, wrapper, 0xE8, 0xFD, 0xD5, 0xE5, 0xF8, 0x08, 0x56, 0x5F, 0xF0) ||
            !homeBytes(rom, wrapper + 10, 0x32, 0x7B, 0x5E, 0x36) ||
            !homeBytes(rom, wrapper + 15, 0x2B, 0x36) ||
            !homeBytes(rom, wrapper + 18, 0x2B, 0x72, 0x2B, 0x73, 0xE1, 0xD1, 0xC3)
        ) return null
        val register = rom.u8(wrapper + 9).takeIf { it in 0x80..0xFE } ?: return null
        val restore = (rom.u8(wrapper + 14) shl 8) or rom.u8(wrapper + 17)
        if (!within(rom, restore, 10) ||
            !homeBytes(rom, restore, 0xF5, 0xE5, 0xF8, 0x04, 0x7E) ||
            !homeBytes(rom, restore + 6, 0xE1, 0xF1, 0x33, 0xC9)
        ) return null
        val store = bankSwitchTarget(rom, rom.u8(restore + 5)) ?: return null
        if (rom.u16le(wrapper + 25) != store || !GbCompiledBankCalls.bankStore(rom, store, register)) {
            return null
        }
        return GbCompiledFarCopyAuthority(register, 255)
    }

    private fun hramDispatched(rom: RomImage, wrapper: Int): GbCompiledFarCopyAuthority? {
        if (!within(rom, wrapper, 14) ||
            !homeBytes(rom, wrapper, 0xE0) ||
            !homeBytes(rom, wrapper + 2, 0x7C, 0xE0) ||
            !homeBytes(rom, wrapper + 5, 0x7D, 0xE0) ||
            !homeBytes(rom, wrapper + 8, 0xE1, 0xF0) ||
            !homeBytes(rom, wrapper + 11, 0xF5, 0x18)
        ) return null
        val temporaryBank = rom.u8(wrapper + 1)
        val savedHigh = rom.u8(wrapper + 4)
        val savedLow = rom.u8(wrapper + 7)
        val bankRegister = rom.u8(wrapper + 10)
        if (savedHigh != savedLow + 1) return null
        val dispatch = wrapper + 14 + rom.u8(wrapper + 13).toByte().toInt()
        if (!within(rom, dispatch, 24) ||
            !homeBytes(rom, dispatch, 0xF0, temporaryBank, 0xE6, 0x7F) ||
            rom.u8(dispatch + 5) != 0xCD
        ) return null
        val switchOpcode = rom.u8(dispatch + 4)
        val store = bankSwitchTarget(rom, switchOpcode) ?: return null
        if (!GbCompiledBankCalls.bankStore(rom, store, bankRegister)) return null

        val restore = dispatch + 8
        val savedAccumulator = rom.u8(restore + 1)
        val registers = setOf(temporaryBank, savedHigh, savedLow, bankRegister, savedAccumulator)
        if (registers.size != 5 || registers.any { it !in 0x80..0xFE } ||
            !homeBytes(
                rom, restore,
                0xE0, savedAccumulator, 0xF5, 0xE5, 0xF8, 0x02, 0x2A, 0x2C,
                0x77, 0xE1, 0xF1, 0xF1, switchOpcode, 0xF0, savedAccumulator, 0xC9,
            )
        ) return null
        val retrieve = rom.u16le(dispatch + 6)
        if (!homeBytes(
                rom, retrieve,
                0xE5, 0x21, savedLow, 0xFF, 0x2A, 0x66, 0x6F, 0xF0, savedAccumulator, 0xC9,
            )
        ) return null
        return GbCompiledFarCopyAuthority(bankRegister, 127)
    }

    private fun bankSwitchTarget(rom: RomImage, opcode: Int): Int? {
        if (opcode and 0xC7 != 0xC7) return null
        val vector = opcode and 0x38
        if (!within(rom, vector, 3)) return null
        val target = if (rom.u8(vector) == 0xC3) rom.u16le(vector + 1) else vector
        return target.takeIf { within(rom, it, 6) }
    }

    private fun homeBytes(rom: RomImage, offset: Int, vararg values: Int): Boolean =
        within(rom, offset, values.size) && values.indices.all { rom.u8(offset + it) == values[it] }

    private fun within(rom: RomImage, offset: Int, count: Int): Boolean =
        offset >= 0 && offset.toLong() + count <= minOf(0x4000, rom.size)
}
