package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.defaultTextCodec
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat

/** A requested native prefix is not a whole-game type-count declaration. */
internal object CompiledReferencedTypeNames {
    fun resolve(
        rom: RomImage,
        layout: ResolvedRomLayout,
        referencedIds: Set<Int>,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
        limits: ResolutionLimits = ResolutionLimits(),
    ): TableLayout? {
        cancellation.throwIfCancellationRequested()
        if (layout.generation != 3 || layout.platform != Platform.GBA ||
            referencedIds.isEmpty() || referencedIds.any { it !in 0..255 }
        ) return null
        val names = layout.languageManifest.defaultProjection()?.localizedTables?.typeNames ?: return null
        val numeric = layout.tables.moveData ?: return null
        val codec = layout.defaultTextCodec() ?: return null
        if (names.format != TableRecordFormat.STANDARD || names.count != 18 || names.recordSize != 7 ||
            names.variableLength || names.valuesArePointers || (names.stride ?: names.recordSize) != 7 ||
            numeric.format != TableRecordFormat.PACKED_FLAGS_MOVE_20 || numeric.recordSize != 20 ||
            numeric.variableLength || numeric.valuesArePointers || (numeric.stride ?: numeric.recordSize) != 20 ||
            numeric.count != layout.moveCount || numeric.count !in 2..65536 || numeric.offset < 0 ||
            numeric.offset.toLong() + numeric.count.toLong() * 20 > rom.size
        ) return null
        val requestedCount = referencedIds.max() + 1
        if (requestedCount <= names.count) return null
        val budget = Budget(limits)
        return try {
            budget.bytes(rom.size.toLong() + numeric.count.toLong() * 20 + requestedCount.toLong() * 7)
            val nativeTypes = linkedSetOf<Int>()
            for (id in 1 until numeric.count) {
                cancellation.throwIfCancellationRequested()
                budget.work()
                nativeTypes += rom.u8(numeric.offset + id * 20 + 3)
            }
            // An extra species/chart ID cannot authorize a neighboring move-type name.
            if (!nativeTypes.containsAll(referencedIds.filter { it >= names.count })) return null
            val roots = linkedSetOf<Int>()
            for (site in 0..rom.size - 4 step 2) {
                if (site % RomImage.DEFAULT_SCAN_CHECK_INTERVAL_BYTES == 0) cancellation.throwIfCancellationRequested()
                if (rom.u16le(site) and 0xff00 != 0x4a00 ||
                    (rom.u16le(site + 2) != 0x1889 && (site < 2 || rom.u16le(site - 2) != 0x1a89))
                ) continue
                budget.nomination()
                val root = consumer(rom, site, numeric.offset, budget::work) ?: continue
                budget.reference(root)
                roots += root
                if (roots.size > 1) return null
            }
            cancellation.throwIfCancellationRequested()
            // Competing complete roots reject before any lexical preference or selected-root ranking.
            if (roots.singleOrNull() != names.offset) return null
            repeat(names.count + requestedCount) { budget.work() }
            if (CompiledTypeNameResolver.decode(rom, 3, names, codec, cancellation) == null) return null
            val prefix = names.copy(count = requestedCount, format = TableRecordFormat.GBA_REFERENCED_TYPE_NAMES)
            prefix.takeIf { CompiledTypeNameResolver.decode(rom, 3, it, codec, cancellation) != null }
        } catch (_: BudgetExceeded) {
            null
        }
    }

    /** Call-free u16 move -> numeric20 type byte +3 -> type*7 -> r1 -> complete copy leaf. */
    private fun consumer(rom: RomImage, site: Int, numericRoot: Int, work: () -> Unit): Int? {
        if (site < 28 || site.toLong() + 8 > rom.size) return null
        fun words(start: Int, vararg expected: Int): Boolean =
            start >= 0 && start.toLong() + expected.size * 2 <= rom.size && expected.indices.all {
                work()
                rom.u16le(start + it * 2) == expected[it]
            }
        fun slot(at: Int, register: Int): Int? {
            work()
            val op = rom.u16le(at)
            if (op and 0xff00 != (0x4800 or (register shl 8))) return null
            val address = ((at.toLong() + 4) and -4L) + (op and 255) * 4L
            return address.takeIf { it in 0..rom.size.toLong() - 4 }?.toInt()
        }
        if (slot(site - 28, 3)?.let(rom::gbaPointer) != numericRoot || slot(site - 26, 1) == null ||
            !words(site - 24, 0x186d, 0x7829, 0x0049, 0x1864, 0x8822, 0x0091,
                0x1889, 0x0089, 0x18c9, 0x78ca, 0x00d1, 0x1a89) ||
            !words(site + 2, 0x1889)
        ) return null
        val root = slot(site, 2)?.let(rom::gbaPointer) ?: return null
        work()
        val high = rom.u16le(site + 4)
        val low = rom.u16le(site + 6)
        if (high and 0xf800 != 0xf000 || low and 0xf800 != 0xf800) return null
        var displacement = ((high and 0x7ff) shl 12) or ((low and 0x7ff) shl 1)
        if (displacement and 0x400000 != 0) displacement -= 0x800000
        val copy = site + 8 + displacement
        // The leaf copies bytes up to FF, includes the terminator, and returns without unchecked calls.
        if (!words(copy, 0xb500, 0x1c03, 0xe002, 0x701a, 0x3301, 0x3101, 0x780a,
                0x1c10, 0x28ff, 0xd1f8, 0x20ff, 0x7018, 0x1c18, 0xbc02, 0x4708)
        ) return null
        return root
    }

    private class Budget(private val limits: ResolutionLimits) {
        private var work = 0
        private var nominations = 0
        private val roots = linkedMapOf<Int, Int>()
        fun bytes(count: Long) {
            if (count > limits.maxDatasetExtentBytes) throw BudgetExceeded()
        }
        fun work() {
            if (++work > limits.maxProbeWorkPerDataset) throw BudgetExceeded()
        }
        fun nomination() {
            work()
            if (++nominations > limits.maxNominatedGbaReferenceSites) throw BudgetExceeded()
        }
        fun reference(root: Int) {
            val count = (roots[root] ?: 0) + 1
            roots[root] = count
            if (count > limits.maxCompiledReferenceSitesPerCandidate ||
                roots.size > limits.maxProbeRootsPerDataset || roots.size > limits.maxCandidatesPerDataset
            ) throw BudgetExceeded()
        }
    }
    private class BudgetExceeded : RuntimeException()
}
