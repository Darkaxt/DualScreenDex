package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.EvolutionEdge
import com.enrpau.dualscreendex.parser.catalog.LearnsetEntry
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Gen2CompactCoreMetadata
import com.enrpau.dualscreendex.parser.model.Gen2CompactRelationshipContract

/**
 * Compact-engine evolution/learnset authority.
 *
 * The pointer table is proven by its sole `GetEvosAttacksPointer` consumer: `call <base index helper>;
 * [dec bc]; ld hl, table; add hl, bc; add hl, bc; ld a, [hli]; ld h, [hl]; ld l, a; ret`, using the same
 * helper (and decrement) as the compact base-stat consumer, so row N is base-stat row N.
 *
 * Two row ABIs are recognized, each from its own compiled consumer of the table bank:
 * - `$FF`-terminated rows with `species, form` targets: a width consumer (in any bank) that reads the method with
 *   `ld a, BANK; call GetFarByte`, then skips three bytes for exactly the three compared methods and two
 *   otherwise before reading the target word from the same bank.
 * - Zero-terminated rows with species-byte targets: the pokecrystal pre-evolution walker over this table
 *   (stat method 4 bytes, every other method 3).
 *
 * The evolution dispatcher must also prove the trade vocabulary: either `ld b, a; cp 3; jr z, .trade`
 * before the link-mode rejection, or a link-mode rejection before any method comparison (no trade method).
 */
internal object Gen2CompactRelationshipResolver {
    data class Decoded(
        val evolutions: Map<Int, List<EvolutionEdge>>,
        val evolutionFailures: Map<Int, String>,
        val learnsets: Map<Int, List<LearnsetEntry>>,
        val learnsetFailures: Map<Int, String>,
    )

    fun resolve(
        session: RomAnalysisSession,
        core: Gen2CompactCoreResolution,
        moveCount: Int,
    ): Gen2CompactRelationshipContract? {
        val rom = session.rom
        val rows = core.tables.baseStats?.count ?: return null
        if (core.baseIndexHelper !in 0x0001..0x3FFF || moveCount !in 1..255) return null
        val consumers = mutableSetOf<Pair<Int, Int>>()
        for (offset in 0..rom.size - POINTER_TAIL.size - 4) {
            if (offset % 4096 == 0) session.cancellation.throwIfCancellationRequested()
            if (rom.u8(offset) != 0xCD || rom.u16le(offset + 1) != core.baseIndexHelper) continue
            val cursor = offset + 3 + if (core.baseDecrements) 1 else 0
            if (core.baseDecrements && rom.u8(offset + 3) != 0x0B) continue
            if (rom.u8(cursor) != 0x21 || !matches(rom, cursor + 3, POINTER_TAIL)) continue
            val bank = offset / BANK_BYTES
            val address = rom.u16le(cursor + 1)
            if (bank == 0 || address !in 0x4000..0x7FFF) return null
            consumers += bank to bank * BANK_BYTES + address - 0x4000
        }
        val (bank, table) = consumers.singleOrNull() ?: return null
        if (table + rows * 2 > minOf((bank + 1) * BANK_BYTES, rom.size)) return null
        val wide = widthConsumerMethods(session, bank)
        val walkers = Gen2CompiledRelationshipResolver.preEvolutionWalkers(rom, bank, session.cancellation)
        val byteRows = walkers.isNotEmpty() && walkers.all { (root, bound) -> root == table && bound <= rows }
        val contract = when {
            wide != null && walkers.isEmpty() -> Gen2CompactRelationshipContract(
                table, bank, rows, terminator = 0xFF, formTargets = true, wideMethods = wide,
                tradeMethodPresent = trade(rom, bank) ?: return null,
            )
            wide == null && byteRows -> Gen2CompactRelationshipContract(
                table, bank, rows, terminator = 0x00, formTargets = false, wideMethods = setOf(STAT_METHOD),
                tradeMethodPresent = trade(rom, bank) ?: return null,
            )
            else -> return null
        }
        val metadata = core.metadata.withRelationships(contract)
        if (metadata.slots.any { it.baseIndex >= rows }) return null
        // Admission is all-or-nothing: every physical row must be structurally sound.
        val structural = (0 until rows).map { decodeRow(rom, contract, metadata, moveCount, it) }
        return contract.takeIf { structural.none { row -> row.structuralFailure != null } }
    }

    fun decode(rom: RomImage, metadata: Gen2CompactCoreMetadata, moveCount: Int): Decoded? {
        val contract = metadata.relationships ?: return null
        val rows = (0 until contract.rows).map { decodeRow(rom, contract, metadata, moveCount, it) }
        val evolutions = linkedMapOf<Int, List<EvolutionEdge>>()
        val evolutionFailures = linkedMapOf<Int, String>()
        val learnsets = linkedMapOf<Int, List<LearnsetEntry>>()
        val learnsetFailures = linkedMapOf<Int, String>()
        for (slot in metadata.slots) {
            val row = rows.getOrNull(slot.baseIndex)
            if (row == null || row.structuralFailure != null) {
                val reason = row?.structuralFailure ?: "compact relationship row is outside the pointer table"
                evolutionFailures[slot.id] = reason
                learnsetFailures[slot.id] = reason
                continue
            }
            if (row.semanticFailure != null) evolutionFailures[slot.id] = row.semanticFailure
            else evolutions[slot.id] = row.evolutions
            learnsets[slot.id] = row.learnset
        }
        return Decoded(evolutions, evolutionFailures, learnsets, learnsetFailures)
    }

    private class Row(
        val evolutions: List<EvolutionEdge> = emptyList(),
        val learnset: List<LearnsetEntry> = emptyList(),
        val structuralFailure: String? = null,
        val semanticFailure: String? = null,
    )

    private fun decodeRow(
        rom: RomImage,
        contract: Gen2CompactRelationshipContract,
        metadata: Gen2CompactCoreMetadata,
        moveCount: Int,
        index: Int,
    ): Row {
        val bankStart = contract.bank * BANK_BYTES
        val bankEnd = minOf(bankStart + BANK_BYTES, rom.size)
        fun inside(cursor: Int, width: Int) = cursor >= bankStart && cursor + width <= bankEnd
        val address = rom.u16le(contract.tableOffset + index * 2)
        if (address !in 0x4000..0x7FFF) return Row(structuralFailure = "compact relationship pointer leaves its bank")
        var cursor = bankStart + address - 0x4000
        val ids = metadata.slots.mapTo(hashSetOf()) { it.id }
        val edges = mutableListOf<EvolutionEdge>()
        var semantic: String? = null
        var terminated = false
        for (entry in 0..MAX_EVOLUTIONS) {
            if (!inside(cursor, 1)) return Row(structuralFailure = "compact evolution row leaves its bank")
            val method = rom.u8(cursor)
            if (method == contract.terminator) { cursor++; terminated = true; break }
            if (method == 0) return Row(structuralFailure = "compact evolution row has method 0")
            val width = if (contract.formTargets) {
                if (method in contract.wideMethods) 5 else 4
            } else {
                Gen2CompiledRelationshipResolver.evolutionWidth(method)
            }
            if (!inside(cursor, width)) return Row(structuralFailure = "compact evolution record leaves its bank")
            val raw = rom.slice(cursor, width)
            val target = if (contract.formTargets) {
                val species = raw[width - 2].toInt() and 0xFF
                val form = raw[width - 1].toInt() and 0xFF
                if (species !in 1..254) -1 else species + if (form and EXTENDED_FORM != 0) 256 else 0
            } else {
                raw[width - 1].toInt() and 0xFF
            }
            if (target !in ids) return Row(structuralFailure = "compact evolution target is outside the species domain")
            if (method == TRADE_METHOD && !contract.tradeMethodPresent) {
                semantic = "method 3 is not a trade evolution in this engine; its compiled meaning is unclassified"
            }
            edges += EvolutionEdge(target, method, raw[1].toInt() and 0xFF, raw)
            cursor += width
        }
        if (!terminated) return Row(structuralFailure = "compact evolution row exceeds the entry budget")
        val learnset = mutableListOf<LearnsetEntry>()
        val maxLevel = if (contract.terminator == 0xFF) 254 else Gen2CompiledRelationshipResolver.MAX_LEARN_LEVEL
        for (entry in 0..MAX_LEARNSET) {
            if (!inside(cursor, 1)) return Row(structuralFailure = "compact learnset leaves its bank")
            val level = rom.u8(cursor)
            if (level == contract.terminator) {
                return Row(edges, learnset, semanticFailure = semantic)
            }
            if (!inside(cursor, 2) || level !in 1..maxLevel || rom.u8(cursor + 1) !in 1..moveCount) {
                return Row(structuralFailure = "compact learnset entry is outside its level/move domain")
            }
            learnset += LearnsetEntry(level, rom.u8(cursor + 1))
            cursor += 2
        }
        return Row(structuralFailure = "compact learnset exceeds the entry budget")
    }

    /**
     * The three methods given an extra parameter byte by the width consumer that far-reads [bank], or null.
     * The consumer may live in any bank (Polished Crystal keeps it outside the table bank); both of its
     * far-read bank operands must name the table bank, and every such consumer must agree.
     */
    private fun widthConsumerMethods(session: RomAnalysisSession, bank: Int): Set<Int>? {
        val rom = session.rom
        val found = mutableSetOf<Set<Int>>()
        for (offset in 0..rom.size - WIDTH.size) {
            if (offset % 4096 == 0) session.cancellation.throwIfCancellationRequested()
            if (rom.u8(offset) != 0x3E || !matches(rom, offset, WIDTH)) continue
            if (rom.u8(offset + 1) != bank || rom.u8(offset + 29) != bank) continue
            val methods = setOf(rom.u8(offset + 13), rom.u8(offset + 17), rom.u8(offset + 21))
            if (methods.size != 3 || methods.any { it !in 1..0xFE }) return null
            found += methods
        }
        return found.singleOrNull()
    }

    /** True when method 3 is the compiled trade branch; false when linked evolution is always rejected. */
    private fun trade(rom: RomImage, bank: Int): Boolean? {
        val start = bank * BANK_BYTES
        val end = minOf(start + BANK_BYTES, rom.size)
        val proofs = mutableListOf<Boolean>()
        for (offset in start until end - NO_TRADE.size) {
            if (rom.u8(offset) != 0x47) continue
            if (matches(rom, offset, TRADE)) {
                if (rom.u8(offset + 2) != TRADE_METHOD) return null
                proofs += true
            }
            if (matches(rom, offset, NO_TRADE)) proofs += false
        }
        return proofs.singleOrNull()
    }

    private fun matches(rom: RomImage, offset: Int, pattern: IntArray): Boolean =
        offset >= 0 && offset.toLong() + pattern.size <= rom.size &&
            pattern.indices.all { pattern[it] < 0 || rom.u8(offset + it) == pattern[it] }

    private const val BANK_BYTES = 0x4000
    private const val STAT_METHOD = 5
    private const val TRADE_METHOD = 3
    private const val EXTENDED_FORM = 0x20
    private const val MAX_EVOLUTIONS = 16
    private const val MAX_LEARNSET = 128
    // add hl, bc; add hl, bc; ld a, [hli]; ld h, [hl]; ld l, a; ret
    private val POINTER_TAIL = intArrayOf(0x09, 0x09, 0x2A, 0x66, 0x6F, 0xC9)
    // ld a, BANK; call GetFarByte; ld d, a; inc a; ld a, ?; jr z, ?; ld a, d; cp A; jr z, +8; cp B; jr z, +4;
    // cp C; jr nz, +1; inc hl; inc hl; inc hl; push hl; ld a, BANK; call GetFarWord
    private val WIDTH = intArrayOf(
        0x3E, -1, 0xCD, -1, -1, 0x57, 0x3C, 0x3E, -1, 0x28, -1, 0x7A, 0xFE, -1, 0x28, 0x08,
        0xFE, -1, 0x28, 0x04, 0xFE, -1, 0x20, 0x01, 0x23, 0x23, 0x23, 0xE5, 0x3E, -1, 0xCD, -1, -1,
    )
    // ld b, a; cp TRADE; jr z, .trade; ld a, [wLinkMode]; and a; jp nz, .dont_evolve
    private val TRADE = intArrayOf(0x47, 0xFE, -1, 0x28, -1, 0xFA, -1, -1, 0xA7, 0xC2)
    // ld b, a; ld a, [wLinkMode]; and a; jp nz, .dont_evolve; ld a, b; cp
    private val NO_TRADE = intArrayOf(0x47, 0xFA, -1, -1, 0xA7, 0xC2, -1, -1, 0x78, 0xFE)
}
