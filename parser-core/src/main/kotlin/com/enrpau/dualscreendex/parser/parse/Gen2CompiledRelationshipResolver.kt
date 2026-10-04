package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout

/** Local-bank byte-species relationship authority, independent of inherited retail roots. */
internal object Gen2CompiledRelationshipResolver {
    data class Resolution(val table: TableLayout?, val rejection: String? = null)
    private data class Consumer(val root: Int, val speciesInput: Int)

    fun resolve(session: RomAnalysisSession, speciesCount: Int, moveCount: Int): Resolution? {
        if (speciesCount !in 1..254 || moveCount !in 1..255) return null
        val rom = session.rom
        val work = Work(session)
        return try {
            val preEvolutions = linkedSetOf<Consumer>()
            val learnsets = linkedSetOf<Consumer>()
            val discoveredRoots = linkedSetOf<Int>()
            fun addConsumer(consumer: Consumer?, destination: MutableSet<Consumer>) {
                consumer ?: return
                destination += consumer
                discoveredRoots += consumer.root
                if (discoveredRoots.size > session.limits.maxProbeRootsPerDataset) {
                    throw BudgetExceeded("Gen II relationship root budget exceeded")
                }
            }
            for (offset in 0 until rom.size) {
                if (offset % 256 == 0) work.claim()
                if (rom.u8(offset) == 0x0E) {
                    work.claim()
                    addConsumer(preEvolutionConsumer(rom, offset, speciesCount), preEvolutions)
                }
                if (rom.u8(offset) == 0xFA) {
                    work.claim()
                    addConsumer(learnsetConsumer(rom, offset), learnsets)
                }
            }
            if (preEvolutions.isEmpty() && learnsets.isEmpty()) return null
            val roots = (preEvolutions + learnsets).map { it.root }.distinct()
            // Never discard a competing complete consumer just because its data is malformed.
            if (roots.size != 1 || preEvolutions.size != 1 || learnsets.size != 1 || preEvolutions != learnsets) {
                return Resolution(null, "Gen II relationship consumers are incomplete or conflicting")
            }
            val root = roots.single()
            if (!validRows(session, root, speciesCount, moveCount, work)) {
                return Resolution(null, "compiled Gen II relationship rows are malformed or out of domain")
            }
            Resolution(TableLayout(root, speciesCount, 2, variableLength = true, bank = root / BANK_BYTES))
        } catch (failure: BudgetExceeded) {
            Resolution(null, failure.message)
        }
    }

    private fun preEvolutionConsumer(rom: RomImage, offset: Int, count: Int): Consumer? {
        if (!matches(rom, offset, PRE_EVOLUTION)) return null
        if (rom.u8(offset + 36) != count) return null
        val species = rom.u16le(offset + 23)
        if (species !in WRAM || rom.u16le(offset + 44) != species) return null
        val root = localRoot(rom, offset, 3, PRE_EVOLUTION.size) ?: return null
        return Consumer(root, species)
    }

    private fun learnsetConsumer(rom: RomImage, offset: Int): Consumer? {
        if (!matches(rom, offset, LEARN_LEVEL)) return null
        val source = rom.u16le(offset + 1)
        val partySpecies = rom.u16le(offset + 4)
        val level = rom.u16le(offset + 28)
        if (source !in WRAM || partySpecies !in WRAM || level !in WRAM || setOf(source, partySpecies, level).size != 3) return null
        val done = offset + 26 + rom.u8(offset + 25).toByte().toInt()
        if (done < offset + LEARN_LEVEL.size || (done + RESTORE_SPECIES.size - 1) / BANK_BYTES != offset / BANK_BYTES ||
            !matches(rom, done, RESTORE_SPECIES) || rom.u16le(done + 1) != partySpecies ||
            rom.u16le(done + 4) != source
        ) return null
        val root = localRoot(rom, offset, 11, LEARN_LEVEL.size) ?: return null
        return Consumer(root, partySpecies)
    }

    private fun localRoot(rom: RomImage, offset: Int, operand: Int, size: Int): Int? {
        val bank = offset / BANK_BYTES
        if (bank == 0 || (offset + size - 1) / BANK_BYTES != bank) return null
        val address = rom.u16le(offset + operand)
        if (address !in 0x4000..0x7FFF) return null
        return rom.gbBankAddress(bank, address)
    }

    private fun validRows(session: RomAnalysisSession, root: Int, count: Int, moves: Int, work: Work): Boolean {
        val rom = session.rom
        val bankStart = root / BANK_BYTES * BANK_BYTES
        val bankEnd = minOf(bankStart + BANK_BYTES, rom.size)
        val tableEnd = root + count * 2
        if (tableEnd > bankEnd) return false
        var bytes = count * 2L
        var active = 0
        fun claim(width: Int) {
            work.claim()
            bytes += width
            if (bytes > session.limits.maxDatasetExtentBytes) throw BudgetExceeded("Gen II relationship extent budget exceeded")
        }
        if (bytes > session.limits.maxDatasetExtentBytes) throw BudgetExceeded("Gen II relationship extent budget exceeded")
        fun contains(cursor: Int, width: Int) = cursor >= bankStart && cursor + width <= bankEnd &&
            (cursor + width <= root || cursor >= tableEnd)
        repeat(count) { index ->
            work.claim()
            val address = rom.u16le(root + index * 2)
            if (address !in 0x4000..0x7FFF) return false
            var cursor = rom.gbBankAddress(root / BANK_BYTES, address) ?: return false
            var evolutionEntries = 0
            while (contains(cursor, 1) && rom.u8(cursor) != 0) {
                if (evolutionEntries++ >= 16) return false
                val method = rom.u8(cursor)
                val width = when (method) { in 1..4 -> 3; 5 -> 4; else -> return false }
                if (!contains(cursor, width) || rom.u8(cursor + width - 1) !in 1..count) return false
                claim(width)
                active++
                cursor += width
            }
            if (!contains(cursor, 1) || rom.u8(cursor) != 0) return false
            claim(1)
            cursor++
            var learnsetEntries = 0
            while (contains(cursor, 1) && rom.u8(cursor) != 0) {
                if (learnsetEntries++ >= 128 || !contains(cursor, 2) || rom.u8(cursor) !in 1..100 ||
                    rom.u8(cursor + 1) !in 1..moves
                ) return false
                claim(2)
                active++
                cursor += 2
            }
            if (!contains(cursor, 1) || rom.u8(cursor) != 0) return false
            claim(1)
        }
        return active > 0
    }

    private fun matches(rom: RomImage, offset: Int, pattern: IntArray): Boolean =
        offset >= 0 && offset.toLong() + pattern.size <= rom.size.toLong() &&
            pattern.indices.all { pattern[it] < 0 || rom.u8(offset + it) == pattern[it] }

    private class BudgetExceeded(message: String) : RuntimeException(message)
    private class Work(private val session: RomAnalysisSession) {
        private var used = 0
        fun claim() {
            session.cancellation.throwIfCancellationRequested()
            if (++used > session.limits.maxProbeWorkPerDataset) throw BudgetExceeded("Gen II relationship work budget exceeded")
        }
    }

    private const val BANK_BYTES = 0x4000
    private val WRAM = 0xC000..0xDFFF
    // Complete sequential pre-evolution traversal, including the extra stat parameter and return.
    private val PRE_EVOLUTION = intArrayOf(
        0x0E, 0, 0x21, -1, -1, 0x06, 0, 0x09, 0x09, 0x2A, 0x66, 0x6F,
        0x2A, 0xA7, 0x28, 0x11, 0xFE, 5, 0x20, 1, 0x23, 0x23, 0xFA, -1, -1,
        0xBE, 0x28, 0x0D, 0x23, 0x7E, 0xA7, 0x20, 0xEB, 0x0C, 0x79, 0xFE, -1,
        0x38, 0xDB, 0xA7, 0xC9, 0x0C, 0x79, 0xEA, -1, -1, 0x37, 0xC9,
    )
    // Source/party input linkage, one-based word indexing, zero evolution delimiter and level/move pairs.
    private val LEARN_LEVEL = intArrayOf(
        0xFA, -1, -1, 0xEA, -1, -1, 0x3D, 0x06, 0, 0x4F, 0x21, -1, -1,
        0x09, 0x09, 0x2A, 0x66, 0x6F, 0x2A, 0xA7, 0x20, 0xFC,
        0x2A, 0xA7, 0x28, -1, 0x47, 0xFA, -1, -1, 0xB8, 0x2A, 0x20, 0xF4,
    )
    private val RESTORE_SPECIES = intArrayOf(0xFA, -1, -1, 0xEA, -1, -1, 0xC9)
}
