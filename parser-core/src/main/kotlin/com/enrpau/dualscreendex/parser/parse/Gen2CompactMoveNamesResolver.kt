package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

internal data class Gen2CompactMovesResolution(val moveNames: TableLayout, val moveData: TableLayout)

/** Independent byte-ID move namespace, with complete registry, string, copy, and bank consumers. */
internal object Gen2CompactMoveNamesResolver {
    private const val MOVE_COUNT = 255
    private const val COPY_BYTES = 13
    private data class Root(val bank: Int, val address: Int, val reservedString: Boolean)

    fun resolve(session: RomAnalysisSession, codec: PokemonTextCodec, typeIds: Set<Int>): Gen2CompactMovesResolution? {
        if (codec.terminator != 0x53 || 2 !in codec.applicableGenerations) return null
        val rom = session.rom
        // Require all nonzero values of the compiled byte input; never trim an adjacent data prefix.
        val data = Gen2CompactMoveResolver.resolve(rom, MOVE_COUNT, typeIds) ?: return null
        val roots = mutableListOf<Root>()
        for (offset in 0..minOf(0x4000, rom.size) - 20) {
            if (offset and 0x3FF == 0) session.cancellation.throwIfCancellationRequested()
            fourByteRegistry(rom, offset)?.let(roots::add)
            threeByteRegistry(rom, offset)?.let(roots::add)
        }
        val names = roots.distinct().mapNotNull { root -> decodeNames(session, root, codec) }
            .distinct().singleOrNull() ?: return null
        return Gen2CompactMovesResolution(names, data)
    }

    private fun fourByteRegistry(rom: RomImage, getter: Int): Root? {
        if (!homeBytes(rom, getter, 0x3E, 0x04, 0x18)) return null
        val dispatch = getter + 4 + rom.u8(getter + 3).toByte().toInt()
        if (!within(rom, dispatch, 42) ||
            !homeBytes(rom, dispatch, 0xE5, 0x11) ||
            !homeBytes(rom, dispatch + 4, 0xD5, 0xC5, 0xC6) ||
            !homeBytes(rom, dispatch + 8, 0x6F, 0xCE) ||
            !homeBytes(rom, dispatch + 11, 0x95, 0x67, 0xF0) ||
            !homeBytes(rom, dispatch + 15, 0xF5, 0x2A, 0x47, 0x2A) ||
            !homeBytes(rom, dispatch + 20, 0x2A, 0x66, 0x6F, 0xFA) ||
            !homeBytes(rom, dispatch + 26, 0xEA) ||
            !homeBytes(rom, dispatch + 29, 0x80, 0xCD) ||
            !homeBytes(rom, dispatch + 33, 0x01, COPY_BYTES, 0x00) ||
            !homeBytes(rom, dispatch + 37, 0xF1) || !homeBytes(rom, dispatch + 39, 0xC3)
        ) return null
        val destination = rom.u16le(dispatch + 2)
        val register = rom.u8(dispatch + 14)
        val switch = rom.u8(dispatch + 19)
        val table = rom.u8(dispatch + 7) or (rom.u8(dispatch + 10) shl 8)
        if (destination !in 0xC000..0xDFF3 || rom.u16le(dispatch + 24) !in 0xC000..0xDFFF ||
            rom.u16le(dispatch + 27) !in 0xC000..0xDFFF || register !in 0x80..0xFE ||
            switch != rom.u8(dispatch + 38) || !bankSwitch(rom, switch, register) ||
            !GbCompiledBankCalls.restartCopy(rom, rom.u8(dispatch + 36)) ||
            !nthString(rom, rom.u16le(dispatch + 31)) ||
            !homeBytes(rom, rom.u16le(dispatch + 40), 0xC1, 0xD1, 0xE1, 0xC9) ||
            table !in 1..0x3FF8 || rom.u8(table + 4) != 0xFF
        ) return null
        return Root(rom.u8(table + 5), rom.u16le(table + 6), false)
    }

    private fun threeByteRegistry(rom: RomImage, getter: Int): Root? {
        if (!homeBytes(rom, getter, 0xE5, 0x3E, 0x02, 0xEA) ||
            !homeBytes(rom, getter + 6, 0xFA) || !homeBytes(rom, getter + 9, 0xEA) ||
            !homeBytes(rom, getter + 12, 0xCD) || !homeBytes(rom, getter + 15, 0x11) ||
            !homeBytes(rom, getter + 18, 0xE1, 0xC9)
        ) return null
        val dispatch = rom.u16le(getter + 13)
        if (!within(rom, dispatch, 64) || !homeBytes(rom, dispatch, 0xF0) ||
            !homeBytes(rom, dispatch + 2, 0xE5, 0xD5, 0xC5, 0xF5, 0xFA) ||
            !homeBytes(rom, dispatch + 9, 0xFE, 0x01, 0x20, 0x11, 0xFA) ||
            !homeBytes(rom, dispatch + 16, 0xEA) || !homeBytes(rom, dispatch + 19, 0xCD) ||
            !homeBytes(rom, dispatch + 22, 0x21, 0x0B, 0x00, 0x19, 0x5D, 0x54, 0x18, 0x1C,
                0x3D, 0x5F, 0x16, 0x00, 0x21) ||
            !homeBytes(rom, dispatch + 37, 0x19, 0x19, 0x19, 0x2A) ||
            !homeBytes(rom, dispatch + 42, 0x2A, 0x66, 0x6F, 0xFA) ||
            !homeBytes(rom, dispatch + 48, 0xCD) || !homeBytes(rom, dispatch + 51, 0x11) ||
            !homeBytes(rom, dispatch + 54, 0x01, COPY_BYTES, 0x00) ||
            !homeBytes(rom, dispatch + 58, 0xF1, 0xC1, 0xD1, 0xE1) || rom.u8(dispatch + 63) != 0xC9
        ) return null
        val register = rom.u8(dispatch + 1)
        val destination = rom.u16le(dispatch + 52)
        val species = rom.u16le(dispatch + 14)
        val named = rom.u16le(dispatch + 17)
        val type = rom.u16le(dispatch + 7)
        val switch = rom.u8(dispatch + 41)
        val table = rom.u16le(dispatch + 35)
        if (type !in 0xC000..0xDFFF || species !in 0xC000..0xDFFF || named !in 0xC000..0xDFFF ||
            setOf(type, species, named).size != 3 ||
            destination !in 0xC000..0xDFF3 || register !in 0x80..0xFE ||
            type != rom.u16le(getter + 4) || named != rom.u16le(getter + 7) ||
            species != rom.u16le(getter + 10) || species != rom.u16le(dispatch + 46) ||
            destination != rom.u16le(getter + 16) ||
            switch != rom.u8(dispatch + 62) || !bankSwitch(rom, switch, register) ||
            !GbCompiledBankCalls.restartCopy(rom, rom.u8(dispatch + 57)) ||
            !nthString(rom, rom.u16le(dispatch + 49)) || table !in 1..0x3FFA
        ) return null
        return Root(rom.u8(table + 3), rom.u16le(table + 4), true)
    }

    private fun decodeNames(session: RomAnalysisSession, root: Root, codec: PokemonTextCodec): TableLayout? {
        val rom = session.rom
        if (root.bank !in 1..255 || root.address !in 0x4000..0x7FFF) return null
        var cursor = rom.gbBankAddress(root.bank, root.address) ?: return null
        val bankEnd = minOf((root.bank + 1) * 0x4000, rom.size)
        fun nextString(named: Boolean): Boolean {
            if (cursor + COPY_BYTES > bankEnd) return false
            val decoded = codec.decodeDetailed(rom, cursor, COPY_BYTES, session.cancellation)
            if (!decoded.terminated || decoded.invalidUnits != 0 || decoded.controlUnits != 0 ||
                decoded.substitutionUnits != 0 || (named && decoded.text.none(Char::isLetterOrDigit))
            ) return false
            cursor += decoded.consumedBytes
            return true
        }
        if (root.reservedString && !nextString(false)) return null
        val first = cursor
        repeat(MOVE_COUNT) { if (!nextString(true)) return null }
        return TableLayout(first, MOVE_COUNT, 1, variableLength = true, bank = root.bank)
    }

    private fun nthString(rom: RomImage, offset: Int): Boolean = homeBytes(rom, offset,
        0xA7, 0xC8, 0xC5, 0x47, 0x2A, 0xFE, 0x53, 0x20, 0xFB, 0x05, 0x20, 0xF8, 0xC1, 0xC9)

    private fun bankSwitch(rom: RomImage, opcode: Int, register: Int): Boolean {
        if (opcode and 0xC7 != 0xC7) return false
        val vector = opcode and 0x38
        if (!within(rom, vector, 3)) return false
        val target = if (rom.u8(vector) == 0xC3) rom.u16le(vector + 1) else vector
        return GbCompiledBankCalls.bankStore(rom, target, register)
    }

    private fun homeBytes(rom: RomImage, offset: Int, vararg values: Int): Boolean =
        within(rom, offset, values.size) && values.indices.all { rom.u8(offset + it) == values[it] }

    private fun within(rom: RomImage, offset: Int, count: Int): Boolean =
        offset >= 0 && offset.toLong() + count <= minOf(0x4000, rom.size)
}
