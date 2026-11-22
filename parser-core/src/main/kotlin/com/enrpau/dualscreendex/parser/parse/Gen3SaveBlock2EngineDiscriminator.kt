package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily

/**
 * Separates FireRed/LeafGreen from Emerald engines through the compiled `GetMoney` consumer.
 *
 * Both engines decode money as `*moneyPtr ^ gSaveBlock2Ptr->encryptionKey`, but the key sits at a
 * different source-defined `struct SaveBlock2` offset: 0xF20 in pokefirered and 0xAC in pokeemerald.
 * Only the exact Thumb shape of that function counts: load an IWRAM pointer literal, dereference it,
 * add the key offset (an immediate, or a shifted immediate in a scratch register), load the
 * encrypted word through R0, load the key, XOR into R0 and return. Every match must agree on one
 * offset; anything else gives no answer, and the caller keeps the tie.
 */
internal object Gen3SaveBlock2EngineDiscriminator {
    fun resolve(rom: RomImage, tied: Set<EngineFamily>): EngineFamily? {
        if (tied != setOf(EngineFamily.FIRERED_LEAFGREEN, EngineFamily.EMERALD)) return null
        val offsets = keyOffsets(rom)
        return when (offsets.singleOrNull()) {
            FIRERED_ENCRYPTION_KEY_OFFSET -> EngineFamily.FIRERED_LEAFGREEN
            EMERALD_ENCRYPTION_KEY_OFFSET -> EngineFamily.EMERALD
            else -> null
        }
    }

    internal fun keyOffsets(rom: RomImage): Set<Int> {
        val offsets = linkedSetOf<Int>()
        var offset = 0
        while (offset <= rom.size - MAX_SHAPE_BYTES) {
            matchGetMoney(rom, offset)?.let(offsets::add)
            offset += 2
        }
        return offsets
    }

    private fun matchGetMoney(rom: RomImage, offset: Int): Int? {
        val literalLoad = rom.u16le(offset)
        if (literalLoad and 0xF800 != 0x4800) return null
        val pointerRegister = (literalLoad ushr 8) and 7
        val literal = ((offset + 4) and 3.inv()) + (literalLoad and 0xFF) * 4
        if (literal + 4 > rom.size) return null
        val pointer = rom.u32le(literal)
        if (pointer !in IWRAM_START..IWRAM_END || pointer and 3L != 0L) return null
        // LDR Rp, [Rp, #0]
        if (rom.u16le(offset + 2) != (0x6800 or (pointerRegister shl 3) or pointerRegister)) return null

        var cursor = offset + 4
        val keyOffset: Int
        val first = rom.u16le(cursor)
        if (first and 0xF800 == 0x3000 && (first ushr 8) and 7 == pointerRegister) {
            // ADDS Rp, #imm8
            keyOffset = first and 0xFF
            cursor += 2
        } else if (first and 0xF800 == 0x2000) {
            // MOVS Rk, #imm8 ; LSLS Rk, Rk, #n ; ADDS Rp, Rp, Rk
            val scratch = (first ushr 8) and 7
            if (scratch == pointerRegister || scratch == 0) return null
            val shift = rom.u16le(cursor + 2)
            if (shift and 0xF83F != (scratch shl 3) or scratch) return null
            val amount = (shift ushr 6) and 0x1F
            val add = rom.u16le(cursor + 4)
            if (add != (0x1800 or (scratch shl 6) or (pointerRegister shl 3) or pointerRegister)) return null
            keyOffset = (first and 0xFF) shl amount
            cursor += 6
        } else {
            return null
        }
        if (pointerRegister == 0) return null
        // LDR R0, [R0, #0] ; LDR Rq, [Rp, #0] ; EORS R0, Rq ; BX LR
        if (rom.u16le(cursor) != 0x6800) return null
        val keyLoad = rom.u16le(cursor + 2)
        if (keyLoad and 0xFFF8 != (0x6800 or (pointerRegister shl 3))) return null
        val keyRegister = keyLoad and 7
        if (keyRegister == 0) return null
        if (rom.u16le(cursor + 4) != (0x4040 or (keyRegister shl 3))) return null
        if (rom.u16le(cursor + 6) != BX_LR) return null
        return keyOffset.takeIf { it in 4 until MAX_SAVE_BLOCK2_BYTES && it and 3 == 0 }
    }

    /** pokefirered `struct SaveBlock2` field `/*0xF20*/ u32 encryptionKey`. */
    private const val FIRERED_ENCRYPTION_KEY_OFFSET = 0xF20

    /** pokeemerald `struct SaveBlock2` field `/*0xAC*/ u32 encryptionKey`. */
    private const val EMERALD_ENCRYPTION_KEY_OFFSET = 0xAC
    private const val MAX_SAVE_BLOCK2_BYTES = 0x1000
    private const val BX_LR = 0x4770
    private const val MAX_SHAPE_BYTES = 18
    private const val IWRAM_START = 0x03000000L
    private const val IWRAM_END = 0x03007FFCL
}
