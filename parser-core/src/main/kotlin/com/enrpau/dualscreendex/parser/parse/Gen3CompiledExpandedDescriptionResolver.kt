package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ExtentCheck
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.CompiledDescriptionExtentBinding
import com.enrpau.dualscreendex.parser.catalog.CompiledDescriptionIndexBinding
import com.enrpau.dualscreendex.parser.dataset.descriptions.CompiledDescriptionRowBinding
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionCodec
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionRowOutcome
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionTableLayout
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionTableOutcome
import com.enrpau.dualscreendex.parser.dataset.descriptions.ResolvedDescriptionLayout
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

/** Complete local consumer, positive boundary and native alias contracts; no identity profiles. */
internal object Gen3CompiledExpandedDescriptionResolver {
    fun resolve(
        session: RomAnalysisSession,
        speciesCount: Int,
        codec: PokemonTextCodec,
    ): ResolvedDescriptionLayout? {
        session.cancellation.throwIfCancellationRequested()
        if (speciesCount < 2) return null
        val rom = session.rom
        val roots = linkedMapOf<Int, MutableSet<Int>>()
        var work = 0L
        fun consume(amount: Int = 1) {
            session.cancellation.throwIfCancellationRequested()
            if (amount > session.limits.maxProbeWorkPerDataset.toLong() - work) throw BudgetStop()
            work += amount
        }
        try {
            for (entry in 0..rom.size - 58 step 2) {
                if (entry and 0xfff == 0) consume()
                if (rom.u16le(entry) != 0xb500 || rom.u16le(entry + 2) != 0x0400 ||
                    rom.u16le(entry + 4) != 0x0c02 || rom.u16le(entry + 6) != 0x0609 ||
                    rom.u16le(entry + 8) != 0x0e09 || rom.u16le(entry + 10) != 0x2900 ||
                    rom.u16le(entry + 12) and 0xff00 != 0xd000
                ) continue
                consume(32)
                val heightStart = branchTarget(rom, entry + 12) ?: continue
                val root = literalRoot(rom, heightStart) ?: continue
                if (root % 4 != 0) continue
                val table = DescriptionTableLayout(root.toLong(), speciesCount.toLong(), 36, listOf(20))
                if (!CompiledDescriptionIndexBinding.matchesAccessor(rom, entry, table)) continue
                if (root !in roots && roots.size == session.limits.maxProbeRootsPerDataset) throw BudgetStop()
                roots.getOrPut(root) { linkedSetOf() }.add(entry)
            }
            val complete = mutableListOf<ResolvedDescriptionLayout>()
            for ((root, accessors) in roots) {
                consume(speciesCount)
                val proposed = DescriptionTableLayout(root.toLong(), speciesCount.toLong(), 36, listOf(20))
                if (session.limits.checkTableExtent(proposed.offset, proposed.count, 36, rom.size.toLong()) !is ExtentCheck.Valid) continue
                val decoded = DescriptionCodec(codec).decode(session, proposed) as? DescriptionTableOutcome.Decoded ?: continue
                val count = decoded.rows.indexOfFirst { it !is DescriptionRowOutcome.Decoded }
                    .let { if (it < 0) decoded.rows.size else it }
                if (count < 2) continue
                val selected = DescriptionTableLayout(root.toLong(), count.toLong(), 36, listOf(20))
                if (!hasPointerConsumer(session, root) { consume() }) continue
                if (!CompiledDescriptionExtentBinding.matches(rom, selected, session.limits,
                        onBudgetExceeded = { throw BudgetStop() }) {
                        consume()
                        true
                    }) continue
                val joins = boundSpeciesRows(session, accessors, count, speciesCount) { consume() } ?: continue
                complete += ResolvedDescriptionLayout(selected, decoded.rows.take(count), CompiledDescriptionRowBinding(joins))
                if (complete.size > session.limits.maxCandidatesPerDataset) throw BudgetStop()
            }
            return complete.singleOrNull()
        } catch (_: BudgetStop) {
            return null
        }
    }

    private fun hasPointerConsumer(session: RomAnalysisSession, root: Int, consume: () -> Unit): Boolean {
        val rom = session.rom
        var found = false
        var nominated = 0
        // Straight-line (index * 8 + index) * 4, independently rooted pointer + 20, word read.
        for (site in 0..rom.size - 14 step 2) {
            if (site and 0xfff == 0) consume()
            if (literalRoot(rom, site) != root) continue
            consume()
            if (++nominated > minOf(session.limits.maxNominatedGbaReferenceSites,
                    session.limits.maxCompiledReferenceSitesPerCandidate)) throw BudgetStop()
            val base = (rom.u16le(site) ushr 8) and 7
            val shift = rom.u16le(site + 2)
            val index = (shift ushr 3) and 7
            val scaled = shift and 7
            if (shift and 0xf800 != 0 || (shift ushr 6) and 31 != 3 ||
                base == index || base == scaled || index == scaled
            ) continue
            if (!adds(rom.u16le(site + 4), scaled, scaled, index) ||
                rom.u16le(site + 6) != ((2 shl 6) or (scaled shl 3) or scaled) ||
                rom.u16le(site + 8) != (0x3000 or (base shl 8) or 20) ||
                !adds(rom.u16le(site + 10), scaled, scaled, base)
            ) continue
            val load = rom.u16le(site + 12)
            if (load and 0xf800 == 0x6800 && (load ushr 6) and 31 == 0 &&
                (load ushr 3) and 7 == scaled
            ) found = true
        }
        return found
    }

    private fun boundSpeciesRows(
        session: RomAnalysisSession,
        accessors: Set<Int>,
        descriptionCount: Int,
        speciesCount: Int,
        consume: () -> Unit,
    ): Map<Int, Int>? {
        val rom = session.rom
        val maps = linkedSetOf<List<Int>>()
        val decodedRoots = hashMapOf<Int, List<Int>?>()
        var nominated = 0
        for (site in 10..rom.size - 4 step 2) {
            if ((site - 10) and 0xfff == 0) consume()
            if (callTarget(rom, site) !in accessors) continue
            consume()
            if (++nominated > minOf(session.limits.maxNominatedGbaReferenceSites,
                    session.limits.maxCompiledReferenceSitesPerCandidate)) throw BudgetStop()
            if (rom.u16le(site - 6) != 0x0400 || rom.u16le(site - 4) != 0x0c00 ||
                rom.u16le(site - 2) !in 0x2100..0x2101
            ) continue
            val wrapper = callTarget(rom, site - 10) ?: continue
            if (!CompiledDescriptionIndexBinding.mappingWrapperReturnsRow(rom, wrapper) ||
                rom.u16le(wrapper + 2) != 0x0400 || rom.u16le(wrapper + 4) != 0x0c01 ||
                rom.u16le(wrapper + 6) != 0x2900 || rom.u16le(wrapper + 10) and 0xff00 != 0x4800 ||
                rom.u16le(wrapper + 14) != 0x0049 || rom.u16le(wrapper + 16) != 0x1809 ||
                rom.u16le(wrapper + 18) != 0x8808
            ) continue
            val root = literalRoot(rom, wrapper + 10) ?: continue
            val values = decodedRoots.getOrPut(root) {
                consume()
                if (session.limits.checkTableExtent(root.toLong(), speciesCount - 1L, 2, rom.size.toLong()) !is ExtentCheck.Valid) {
                    null
                } else {
                    List(speciesCount - 1) { index ->
                        session.cancellation.throwIfCancellationRequested()
                        consume()
                        rom.u16le(root + index * 2)
                    }
                }
            } ?: return null
            if (values.any { it !in 1 until descriptionCount } || values.toSet() != (1 until descriptionCount).toSet()) return null
            maps += values
            if (maps.size > 1) return null
        }
        val values = maps.singleOrNull() ?: return null
        return values.withIndex().associate { (index, row) -> index + 1 to row }
    }

    private fun adds(word: Int, destination: Int, left: Int, right: Int): Boolean =
        word and 0xfe00 == 0x1800 && word and 7 == destination &&
            setOf((word ushr 3) and 7, (word ushr 6) and 7) == setOf(left, right)

    private fun literalRoot(rom: RomImage, site: Int): Int? {
        if (site !in 0..rom.size - 2) return null
        val word = rom.u16le(site)
        if (word and 0xf800 != 0x4800) return null
        val slot = ((site + 4) and -4) + (word and 255) * 4
        return slot.takeIf { it in 0..rom.size - 4 }?.let(rom::gbaPointer)
    }

    private fun branchTarget(rom: RomImage, site: Int): Int? {
        var displacement = rom.u16le(site) and 255
        if (displacement and 128 != 0) displacement -= 256
        return (site + 4 + displacement * 2).takeIf { it in 0..rom.size - 2 }
    }

    private fun callTarget(rom: RomImage, site: Int): Int? {
        if (site !in 0..rom.size - 4) return null
        val high = rom.u16le(site)
        val low = rom.u16le(site + 2)
        if (high and 0xf800 != 0xf000 || low and 0xf800 != 0xf800) return null
        var displacement = ((high and 0x7ff) shl 12) or ((low and 0x7ff) shl 1)
        if (displacement and 0x400000 != 0) displacement -= 0x800000
        return (site + 4 + displacement).takeIf { it in 0..rom.size - 2 }
    }

    private class BudgetStop : RuntimeException()
}
