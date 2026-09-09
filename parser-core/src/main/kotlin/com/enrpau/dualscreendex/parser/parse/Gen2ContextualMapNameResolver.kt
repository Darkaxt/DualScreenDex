package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage

/**
 * Binds the compiled current-map -> backup-map landmark selector to already selected headers.
 * A sentinel header has contextual location identity, not a fixed ROM-authored room name.
 * This does not prove region titles, arbitrary room-text absence, or live rendering behavior.
 * Missing, unsupported, or competing declarations never exempt a static-name obligation.
 */
internal object Gen2ContextualMapNameResolver {
    data class Binding(
        val contextualMapIds: Set<Int>,
        val selectorOffset: Int,
        val currentGroupAddress: Int,
        val backupGroupAddress: Int,
    )

    fun resolve(
        rom: RomImage,
        mapGroupBank: Int,
        mapGroupTable: Int,
        headers: Map<Int, Int>,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): Binding? {
        cancellation.throwIfCancellationRequested()
        if (headers.isEmpty() || headers.size > MAX_MAPS || mapGroupBank !in 1..255 ||
            mapGroupTable / BANK_BYTES != mapGroupBank || !inBank(rom, mapGroupTable, 2)
        ) return null

        var selected: Binding? = null
        var candidates = 0
        rom.visitMatches(
            byteArrayOf(0xfa.toByte()),
            onCheck = cancellation::throwIfCancellationRequested,
        ) { offset ->
            if (matches(rom, offset, SELECTOR)) {
                val field = rom.u16le(offset + 9)
                if (bindsField(rom, field, mapGroupBank, mapGroupTable)) {
                    // A source-shaped competing declaration bound to this same table cannot
                    // disappear merely because its sentinel/backup operands are unsupported.
                    candidates++
                    val currentGroup = rom.u16le(offset + 1)
                    val backupGroup = rom.u16le(offset + 15)
                    if (rom.u8(offset + 12) == 0 && rom.u8(offset + 13) == 0xc0 &&
                        field == rom.u16le(offset + 23) &&
                        currentGroup in 0xc000..0xdffe && backupGroup in 0xc000..0xdffe &&
                        rom.u16le(offset + 5) == currentGroup + 1 &&
                        rom.u16le(offset + 19) == backupGroup + 1 &&
                        kotlin.math.abs(currentGroup - backupGroup) > 1
                    ) {
                        selected = Binding(emptySet(), offset, currentGroup, backupGroup)
                    }
                }
            }
            candidates < 2
        }
        if (candidates != 1) return null

        val contextual = linkedSetOf<Int>()
        for ((id, header) in headers) {
            cancellation.throwIfCancellationRequested()
            val group = id ushr 8
            val map = id and 0xff
            if (group !in 1..63 || map !in 1..254) return null
            val slot = mapGroupTable + (group - 1) * 2
            if (!inBank(rom, slot, 2) || slot / BANK_BYTES != mapGroupBank) return null
            val root = rom.gbBankAddress(mapGroupBank, rom.u16le(slot)) ?: return null
            if (header != root + (map - 1) * 9 || header / BANK_BYTES != mapGroupBank ||
                !inBank(rom, header, 9)
            ) return null
            if (rom.u8(header + 5) == 0) contextual += id
        }
        return selected?.copy(contextualMapIds = contextual)
    }

    private fun bindsField(rom: RomImage, field: Int, bank: Int, table: Int): Boolean {
        if (field !in 0 until BANK_BYTES || !matches(rom, field, FIELD)) return false
        val member = rom.u16le(field + 7)
        if (member !in 0 until BANK_BYTES || !matches(rom, member, MEMBER) ||
            rom.u8(member + 4) != bank
        ) return false
        // Both RST $10 invocations must save/restore the same mapped ROM-bank register.
        if (!matches(rom, 0x10, BANK_SWITCH) || rom.u8(member + 1) !in 0x80..0xfe ||
            rom.u8(0x11) != rom.u8(member + 1)
        ) return false
        val pointer = rom.u16le(member + 7)
        if (pointer !in 0 until BANK_BYTES || !matches(rom, pointer, POINTER) ||
            rom.gbBankAddress(bank, rom.u16le(pointer + 6)) != table
        ) return false
        val opcode = rom.u8(pointer + 19)
        if (opcode != 0xc3 && opcode != 0xcd) return false
        if (opcode == 0xcd && (!inBank(rom, pointer, 23) || rom.u8(pointer + 22) != 0xc9)) return false
        val stride = rom.u16le(pointer + 20)
        return stride in 0 until BANK_BYTES && matches(rom, stride, ADD_N_TIMES)
    }

    private fun matches(rom: RomImage, offset: Int, pattern: IntArray): Boolean =
        inBank(rom, offset, pattern.size) && pattern.indices.all { index ->
            pattern[index] < 0 || rom.u8(offset + index) == pattern[index]
        }

    private fun inBank(rom: RomImage, offset: Int, length: Int): Boolean =
        offset >= 0 && offset.toLong() + length <= rom.size &&
            offset % BANK_BYTES + length <= BANK_BYTES

    private const val BANK_BYTES = 0x4000
    private const val MAX_MAPS = 512
    private val SELECTOR = intArrayOf(
        0xfa, -1, -1, 0x47, 0xfa, -1, -1, 0x4f, 0xcd, -1, -1, 0xfe, -1, -1,
        0xfa, -1, -1, 0x47, 0xfa, -1, -1, 0x4f, 0xcd, -1, -1, 0xc9,
    )
    private val FIELD = intArrayOf(0xe5, 0xd5, 0xc5, 0x11, 5, 0, 0xcd, -1, -1, 0x79, 0xc1, 0xd1, 0xe1, 0xc9)
    private val MEMBER = intArrayOf(0xf0, -1, 0xf5, 0x3e, -1, 0xd7, 0xcd, -1, -1, 0x19, 0x4e, 0x23, 0x46, 0xf1, 0xd7, 0xc9)
    private val POINTER = intArrayOf(
        0xc5, 5, 0x48, 6, 0, 0x21, -1, -1, 9, 9, 0x2a, 0x66, 0x6f, 0xc1, 0x0d, 6, 0, 0x3e, 9, -1, -1, -1,
    )
    private val BANK_SWITCH = intArrayOf(0xe0, -1, 0xea, 0, 0x20, 0xc9)
    private val ADD_N_TIMES = intArrayOf(0xa7, 0xc8, 9, 0x3d, 0x20, 0xfc, 0xc9)
}
