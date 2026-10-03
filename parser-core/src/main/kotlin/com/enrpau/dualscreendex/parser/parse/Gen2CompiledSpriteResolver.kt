package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
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

    fun resolve(
        rom: RomImage,
        speciesCount: Int,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
        limits: ResolutionLimits = ResolutionLimits(),
    ): TableLayout? {
        if (speciesCount !in 1..255) return null
        cancellation.throwIfCancellationRequested()
        return try {
            resolveBounded(rom, speciesCount, cancellation, CompiledSpriteBudget(limits))
        } catch (_: CompiledSpriteBudgetExceededException) {
            null
        }
    }

    private fun resolveBounded(
        rom: RomImage,
        speciesCount: Int,
        cancellation: ParserCancellationToken,
        budget: CompiledSpriteBudget,
    ): TableLayout? {
        val candidates = linkedSetOf<TableLayout>()

        fun scan(opcode: Int, parser: (Int) -> TableLayout?): Boolean = rom.visitMatches(
            pattern = byteArrayOf(opcode.toByte()),
            onCheck = cancellation::throwIfCancellationRequested,
        ) { offset ->
            budget.recordMatch()
            val candidate = parser(offset) ?: return@visitMatches true
            candidates += candidate
            candidates.size <= 1
        }

        if (!scan(LOAD_A_ABSOLUTE) { offset ->
                parseNormalConsumer(rom, offset, speciesCount, cancellation, budget)
            }
        ) return null
        if (!scan(LOAD_HL_IMMEDIATE) { offset ->
                parseVariantConsumer(rom, offset, speciesCount, cancellation, budget)
            }
        ) return null
        cancellation.throwIfCancellationRequested()
        return candidates.singleOrNull()
    }

    private fun parseNormalConsumer(
        rom: RomImage,
        offset: Int,
        speciesCount: Int,
        cancellation: ParserCancellationToken,
        budget: CompiledSpriteBudget,
    ): TableLayout? {
        cancellation.throwIfCancellationRequested()
        budget.recordWork()
        val remapped = offset + NORMAL_CONSUMER_BYTES + 3 <= rom.size && rom.u8(offset + 33) == CALL
        val tail = if (remapped) 3 else 0
        if (
            offset + NORMAL_CONSUMER_BYTES + tail > rom.size ||
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
            rom.u8(offset + 33 + tail) != PUSH_AF ||
            rom.u8(offset + 34 + tail) != INC_HL ||
            rom.u8(offset + 35 + tail) != LOAD_A_D ||
            rom.u8(offset + 36 + tail) != CALL ||
            rom.u8(offset + 39 + tail) != POP_BC ||
            rom.u8(offset + 40 + tail) != RETURN
        ) return null

        val pointer = rom.u16le(offset + 20)
        val normalRoot = rom.gbBankAddress(rom.u8(offset + 11), pointer) ?: return null
        val unownRoot = rom.gbBankAddress(rom.u8(offset + 18), pointer) ?: return null
        if (normalRoot == unownRoot) return null
        val remap = if (remapped) bankRemap(rom, offset, normalRoot, unownRoot, speciesCount, cancellation, budget)
            ?: return null else emptyMap()
        budget.recordRoot(normalRoot)
        budget.recordRoot(unownRoot)
        budget.recordCandidate()
        if (!SpriteValidators.gen2(
                rom,
                normalRoot,
                speciesCount,
                0,
                bankRemap = remap,
                cancellation = cancellation,
                consumeWork = budget::recordWork,
            ).compatible
        ) return null
        if (!SpriteValidators.gen2(
                rom,
                unownRoot,
                UNOWN_FORM_COUNT,
                0,
                bankRemap = remap,
                cancellation = cancellation,
                consumeWork = budget::recordWork,
            ).compatible
        ) return null
        return TableLayout(normalRoot, speciesCount, RECORD_SIZE, bankRemap = remap)
    }

    private fun bankRemap(
        rom: RomImage,
        consumer: Int,
        normalRoot: Int,
        unownRoot: Int,
        count: Int,
        cancellation: ParserCancellationToken,
        budget: CompiledSpriteBudget,
    ): Map<Int, Int>? {
        if (!GbCompiledBankCalls.repeatedAdd(rom, rom.u16le(consumer + 27))) return null
        val byteRead = rom.u16le(consumer + 31)
        val wordRead = rom.u16le(consumer + 40)
        if (byteRead < 0 || byteRead + 16 > minOf(BANK_BYTES, rom.size) ||
            wordRead < 0 || wordRead + 14 > minOf(BANK_BYTES, rom.size)
        ) return null
        val scratch = rom.u8(byteRead + 1)
        val saved = rom.u8(byteRead + 3)
        if (scratch !in 0x80..0xFE || saved !in 0x80..0xFE || scratch == saved ||
            !bytesAt(rom, byteRead, 0xE0, scratch, 0xF0, saved, 0xF5, 0xF0, scratch, 0xD7,
                0x7E, 0xE0, scratch, 0xF1, 0xD7, 0xF0, scratch, 0xC9) ||
            !bytesAt(rom, wordRead, 0xE0, scratch, 0xF0, saved, 0xF5, 0xF0, scratch, 0xD7,
                0x2A, 0x66, 0x6F, 0xF1, 0xD7, 0xC9)
        ) return null
        val switch = if (rom.u8(0x10) == 0xC3) rom.u16le(0x11) else 0x10
        if (!GbCompiledBankCalls.bankStore(rom, switch, saved)) return null
        val fix = rom.gbBankAddress(consumer / BANK_BYTES, rom.u16le(consumer + 34)) ?: return null
        if (!bytesAt(rom, fix, 0xE5, 0xC5, 0xD6) || !bytesAt(rom, fix + 4, 0x4F, 0x06, 0x00, 0x21) ||
            !bytesAt(rom, fix + 10, 0x09, 0x7E, 0xC1, 0xE1, 0xC9)
        ) return null
        val bias = rom.u8(fix + 3)
        val table = rom.gbBankAddress(fix / BANK_BYTES, rom.u16le(fix + 8)) ?: return null
        val remap = linkedMapOf<Int, Int>()
        for ((root, rows) in listOf(normalRoot to count, unownRoot to UNOWN_FORM_COUNT)) {
            if (root.toLong() + rows * RECORD_SIZE > rom.size || root % BANK_BYTES + rows * RECORD_SIZE > BANK_BYTES) return null
            repeat(rows) { index ->
                cancellation.throwIfCancellationRequested()
                budget.recordWork()
                val row = root + index * RECORD_SIZE
                if ((0 until RECORD_SIZE).all { rom.u8(row + it) == END_MARKER }) return@repeat
                for (field in listOf(0, 3)) {
                    val raw = rom.u8(row + field)
                    val cell = table + raw - bias
                    if (raw < bias || cell >= rom.size || cell / BANK_BYTES != table / BANK_BYTES) return null
                    val bank = rom.u8(cell)
                    if (bank == 0 || bank.toLong() * BANK_BYTES >= rom.size) return null
                    remap[raw] = bank
                }
            }
        }
        return remap.takeIf { it.isNotEmpty() }
    }

    private fun bytesAt(rom: RomImage, offset: Int, vararg values: Int): Boolean =
        offset >= 0 && offset.toLong() + values.size <= rom.size && offset % BANK_BYTES + values.size <= BANK_BYTES &&
            values.indices.all { rom.u8(offset + it) == values[it] }

    private fun parseVariantConsumer(
        rom: RomImage,
        offset: Int,
        speciesCount: Int,
        cancellation: ParserCancellationToken,
        budget: CompiledSpriteBudget,
    ): TableLayout? {
        cancellation.throwIfCancellationRequested()
        budget.recordWork()
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
        ) return null

        val consumerBank = offset / BANK_BYTES
        val table = rom.gbBankAddress(consumerBank, rom.u16le(offset + 1)) ?: return null
        val species = linkedSetOf<Int>()
        var cursor = table
        repeat(MAX_VARIANT_ROWS) {
            cancellation.throwIfCancellationRequested()
            budget.recordWork()
            if (cursor + VARIANT_ROW_BYTES > rom.size) return null
            val id = rom.u8(cursor)
            val root = rom.gbBankAddress(rom.u8(cursor + 1), rom.u16le(cursor + 2)) ?: return null
            budget.recordRoot(root)
            budget.recordCandidate()
            if (id == END_MARKER) {
                if (species.isEmpty()) return null
                if (!SpriteValidators.gen2(
                        rom,
                        root,
                        speciesCount,
                        0,
                        cancellation = cancellation,
                        consumeWork = budget::recordWork,
                    ).compatible
                ) return null
                return TableLayout(root, speciesCount, RECORD_SIZE)
            }
            if (id !in 1..speciesCount || !species.add(id)) return null
            if (!SpriteValidators.gen2(
                    rom,
                    root,
                    1,
                    0,
                    cancellation = cancellation,
                    consumeWork = budget::recordWork,
                ).compatible
            ) return null
            cursor += VARIANT_ROW_BYTES
        }
        return null
    }

    private fun branchTarget(opcodeOffset: Int, encodedDelta: Int): Int =
        opcodeOffset + 2 + encodedDelta.toByte().toInt()

    private class CompiledSpriteBudget(private val limits: ResolutionLimits) {
        private val roots = linkedSetOf<Int>()
        private var matches = 0
        private var candidates = 0
        private var work = 0

        fun recordMatch() {
            if (matches == limits.maxProbeWorkPerDataset) throw CompiledSpriteBudgetExceededException()
            matches++
            recordWork()
        }

        fun recordRoot(root: Int) {
            if (root in roots) return
            if (roots.size == limits.maxProbeRootsPerDataset) throw CompiledSpriteBudgetExceededException()
            roots += root
        }

        fun recordCandidate() {
            if (candidates == limits.maxCandidatesPerDataset) throw CompiledSpriteBudgetExceededException()
            candidates++
        }

        fun recordWork() {
            if (work == limits.maxProbeWorkPerDataset) throw CompiledSpriteBudgetExceededException()
            work++
        }
    }

    private class CompiledSpriteBudgetExceededException : RuntimeException(null, null, false, false)

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
