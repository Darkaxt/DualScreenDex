package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.GbaSiteEvidenceStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.defaultTextCodec
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.text.LanguageTextPlausibility
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec


data class MoveDescriptionResult(
    val sourceOffset: Int,
    val confidence: Double,
    val descriptions: Map<Int, String>,
)

object MoveDescriptionMaterializer {
    fun materialize(
        rom: RomImage,
        layout: ResolvedRomLayout,
        gbaReferenceIndex: GbaReferenceIndex? = null,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
        limits: ResolutionLimits = ResolutionLimits(),
    ): MoveDescriptionResult? {
        cancellation.throwIfCancellationRequested()
        val codec = layout.defaultTextCodec() ?: return null
        val budget = MoveDescriptionBudget(limits)
        return try {
            materializeBounded(rom, layout, codec, gbaReferenceIndex, cancellation, budget)
        } catch (_: MoveDescriptionBudgetExceededException) {
            null
        }
    }

    private fun materializeBounded(
        rom: RomImage,
        layout: ResolvedRomLayout,
        codec: PokemonTextCodec,
        gbaReferenceIndex: GbaReferenceIndex?,
        cancellation: ParserCancellationToken,
        budget: MoveDescriptionBudget,
    ): MoveDescriptionResult? {
        if (layout.generation == 2) {
            return materializeGen2(rom, codec, layout.moveCount ?: return null, cancellation, budget)
        }
        if (layout.generation != 3) return null
        val table = layout.tables.moveData
        val embeddedDescriptionStride = when {
            layout.pokeemeraldExpansion != null -> table?.stride ?: layout.pokeemeraldExpansion.moveRecordSize
            table?.format == TableRecordFormat.UNIFIED_MOVE_INFO_48 -> table.stride ?: return null
            else -> null
        }
        if (embeddedDescriptionStride != null) {
            val embeddedTable = table ?: return null
            val count = layout.moveCount ?: return null
            val descriptions = buildMap {
                repeat(count - 1) { index ->
                    checkCancellation(index, cancellation)
                    budget.recordWork()
                    val id = index + 1
                    val record = embeddedTable.offset + id * embeddedDescriptionStride
                    val text = rom.gbaPointer(record + 4)?.let { decodeText(rom, it, codec) } ?: return@repeat
                    put(id, text)
                }
            }
            val expected = count - 1
            val confidence = descriptions.size.toDouble() / expected.coerceAtLeast(1)
            return MoveDescriptionResult(embeddedTable.offset, confidence, descriptions).takeIf {
                descriptions.size >= maxOf(3, (expected * 0.8).toInt())
            }
        }
        val moveCount = layout.moveCount ?: return null
        if (moveCount < 4) return null
        val pointerCount = moveCount - 1
        if (codec.language !in WESTERN_POINTER_LANGUAGES) {
            // Native packed numeric records can satisfy every text-plausibility threshold.
            // All native outcomes are terminal: never reinterpret unproven pointers as prose.
            return when (val outcome = nativeDirectRecords(rom, layout, codec, gbaReferenceIndex, cancellation, budget)) {
                is DescriptionSearchOutcome.Resolved -> outcome.result
                is DescriptionSearchOutcome.Unavailable -> null
            }
        }
        return when (val outcome = referencedPointerTable(rom, codec, pointerCount, gbaReferenceIndex, cancellation, budget)) {
            is DescriptionSearchOutcome.Resolved -> outcome.result
            is DescriptionSearchOutcome.Unavailable -> if (outcome.reason == DescriptionSearchFailure.NO_AUTHORITY) {
                // Preserve the legacy Western-only pointer search, but never erase conflict/overflow.
                fallbackPointerTable(rom, codec, pointerCount, cancellation, budget)
            } else null
        }
    }

    private enum class DescriptionSearchFailure { NO_AUTHORITY, CONFLICT, INCOMPLETE_REFERENCE, BUDGET }

    private sealed interface DescriptionSearchOutcome {
        data class Resolved(val result: MoveDescriptionResult) : DescriptionSearchOutcome
        data class Unavailable(val reason: DescriptionSearchFailure) : DescriptionSearchOutcome
    }

    private fun nativeDirectRecords(
        rom: RomImage,
        layout: ResolvedRomLayout,
        codec: PokemonTextCodec,
        references: GbaReferenceIndex?,
        cancellation: ParserCancellationToken,
        budget: MoveDescriptionBudget,
    ): DescriptionSearchOutcome {
        fun unavailable(reason: DescriptionSearchFailure) = DescriptionSearchOutcome.Unavailable(reason)
        if (references == null) return unavailable(DescriptionSearchFailure.NO_AUTHORITY)
        if (references.overflowed || references.siteEvidenceStatus == GbaSiteEvidenceStatus.COUNTS_ONLY_INCOMPLETE) {
            return unavailable(DescriptionSearchFailure.INCOMPLETE_REFERENCE)
        }
        val moves = layout.tables.moveData ?: return unavailable(DescriptionSearchFailure.NO_AUTHORITY)
        val count = (layout.moveCount ?: return unavailable(DescriptionSearchFailure.NO_AUTHORITY)) - 1
        if (moves.format != TableRecordFormat.STANDARD || (moves.stride ?: moves.recordSize) != 12 ||
            moves.offset < 0 || moves.offset.toLong() + (count.toLong() + 1) * 12 > rom.size
        ) return unavailable(DescriptionSearchFailure.NO_AUTHORITY)
        return try {
            val consumer = DirectMoveConsumer(rom, moves.offset, budget, cancellation)
            val recoveredSites = recoverIncompleteDirectSites(rom, references, cancellation, budget)
            var selectedBase: Int? = null
            for ((base, evidence) in references.targets) {
                cancellation.throwIfCancellationRequested()
                budget.recordWork()
                val sites = if (evidence.siteEvidenceAvailable) evidence.instructionSites
                    else recoveredSites[base] ?: return unavailable(DescriptionSearchFailure.INCOMPLETE_REFERENCE)
                for (site in sites) {
                    budget.recordWork()
                    if (!consumer.proves(site, base)) continue
                    budget.recordRoot(base)
                    budget.recordCandidate()
                    if (selectedBase != null && selectedBase != base) {
                        return unavailable(DescriptionSearchFailure.CONFLICT)
                    }
                    selectedBase = base
                }
            }
            val base = selectedBase ?: return unavailable(DescriptionSearchFailure.NO_AUTHORITY)
            val result = decodeDirectRecords(rom, codec, base, count, cancellation, budget)
                ?: return unavailable(DescriptionSearchFailure.NO_AUTHORITY)
            DescriptionSearchOutcome.Resolved(result)
        } catch (_: IncompleteMoveDescriptionReferencesException) {
            unavailable(DescriptionSearchFailure.INCOMPLETE_REFERENCE)
        } catch (_: MoveDescriptionBudgetExceededException) {
            unavailable(DescriptionSearchFailure.BUDGET)
        }
    }

    /**
     * Recover all missing-site targets together, never once per root. Count EVERY matching literal
     * site using the original index's halfword alignment and inclusion rules. Only the shared
     * supported-prefix query is retained; it is not role authority. No candidate may be published
     * until every original count reconciles, even for roots whose records are unreadable.
     */
    private fun recoverIncompleteDirectSites(
        rom: RomImage,
        references: GbaReferenceIndex,
        cancellation: ParserCancellationToken,
        budget: MoveDescriptionBudget,
    ): Map<Int, List<Int>> {
        val incomplete = references.targets.filterValues { !it.siteEvidenceAvailable }
        if (incomplete.isEmpty()) return emptyMap()
        val observed = linkedMapOf<Int, Int>()
        val sites = linkedMapOf<Int, MutableList<Int>>()
        for ((root, evidence) in incomplete) {
            cancellation.throwIfCancellationRequested()
            budget.recordRoot(root)
            if (evidence.count <= 0 || evidence.observedSites != evidence.count) {
                throw IncompleteMoveDescriptionReferencesException()
            }
            observed[root] = 0
            sites[root] = mutableListOf()
        }
        var site = 0
        while (site <= rom.size - 2) {
            if (site % RomImage.DEFAULT_SCAN_CHECK_INTERVAL_BYTES == 0) cancellation.throwIfCancellationRequested()
            budget.recordScanBytes(2)
            val instruction = rom.u16le(site)
            if (instruction and 0xF800 == 0x4800) {
                val literal = ((site.toLong() + 4) and -4L) + (instruction and 0xFF) * 4L
                if (literal >= 0 && literal + 4 <= rom.size) {
                    val target = rom.u32le(literal.toInt()) - 0x08000000L
                    if (target >= 0 && target < rom.size) {
                        val root = target.toInt()
                        val expected = incomplete[root]
                        if (expected != null) {
                            val count = observed.getValue(root) + 1
                            budget.recordRecoveredReference(count)
                            if (count > expected.count) throw IncompleteMoveDescriptionReferencesException()
                            observed[root] = count
                            if (directConsumerShape(rom, site) != null) {
                                budget.recordRetainedReference()
                                sites.getValue(root) += site
                            }
                        }
                    }
                }
            }
            site += 2
        }
        cancellation.throwIfCancellationRequested()
        if (incomplete.any { (root, evidence) -> observed[root] != evidence.count }) {
            throw IncompleteMoveDescriptionReferencesException()
        }
        return sites
    }

    private enum class DirectConsumerShape { MENU, WINDOW }

    private fun directConsumerShape(rom: RomImage, site: Int): DirectConsumerShape? {
        if (site < 6 || site > rom.size) return null
        return when (rom.u16le(site - 6)) {
            0x00C8 -> if (rom.u16le(site - 4) == 0x1A40 && rom.u16le(site - 2) == 0x00C0) DirectConsumerShape.MENU else null
            0x00E1 -> if (rom.u16le(site - 4) == 0x1B09 && rom.u16le(site - 2) == 0x00C9) DirectConsumerShape.WINDOW else null
            else -> null
        }
    }

    private fun decodeDirectRecords(
        rom: RomImage,
        codec: PokemonTextCodec,
        biasedBase: Int,
        count: Int,
        cancellation: ParserCancellationToken,
        budget: MoveDescriptionBudget,
    ): MoveDescriptionResult? {
        val first = biasedBase.toLong() + DIRECT_RECORD_BYTES
        val length = count.toLong() * DIRECT_RECORD_BYTES
        if (count < 3 || biasedBase < 0 || first + length > rom.size) return null
        budget.recordScanBytes(length)
        val descriptions = linkedMapOf<Int, String>()
        repeat(count) { index ->
            checkCancellation(index, cancellation)
            budget.recordWork()
            val record = rom.slice((first + index.toLong() * DIRECT_RECORD_BYTES).toInt(), DIRECT_RECORD_BYTES)
            val decoded = codec.decodeDetailed(record)
            val text = decoded.text.replace(Regex("\\s+"), " ").trim()
            // decodeDetailed owns token boundaries; FF occurring inside a control is not a terminator.
            if (!decoded.terminated || decoded.validRatio < 0.85 || text.length < 5) return null
            descriptions[index + 1] = text
        }
        return MoveDescriptionResult(first.toInt(), 1.0, descriptions)
    }

    /**
     * Two source-defined summary-screen ABIs, recognized through relocated instructions, not ROM
     * identities. A u16 move is shared with the selected 12-byte numeric table's power consumer;
     * its printer must be the same source-defined text wrapper as the direct prose printer.
     * Exact supported instruction paths deliberately reject unknown/clobbering variants.
     * Role oracles: pokeruby@63a8cbf0016b351a4e68f7036fa0b77e23d2f2c1
     * and pokeemerald@5eff78649e7170a877b961ef0b3da13b81a16038, pokemon_summary_screen.c.
     * Those sources establish the summary/numeric/text roles; compiled consumers establish stride.
     */
    private class DirectMoveConsumer(
        private val rom: RomImage,
        private val numericRoot: Int,
        private val budget: MoveDescriptionBudget,
        private val cancellation: ParserCancellationToken,
    ) {
        fun proves(site: Int, base: Int): Boolean {
            if (site < 6 || site % 2 != 0 || literalPointer(site) != base) return false
            return when (directConsumerShape(rom, site)) {
                DirectConsumerShape.MENU -> menuConsumer(site)
                DirectConsumerShape.WINDOW -> windowConsumer(site)
                null -> false
            }
        }

        private fun menuConsumer(site: Int): Boolean {
            val entry = site - 0x12
            if (!words(entry, 0xB500, 0x0400, 0x0C01) ||
                !literalLoad(entry + 6, 0) || literalValue(entry + 6) != 0xFFFFL ||
                !words(entry + 8, 0x4281, 0xD008) || !literalLoad(site, 1) ||
                !words(site + 2, 0x1840, 0x210B, 0x220F) ||
                !words(entry + 0x1E, 0xBC01, 0x4700)
            ) return false
            val sink = call(entry + 0x1A) ?: return false
            if (!menuTextSink(sink)) return false
            // The caller's battle-page basic block forwards the same callee-saved register twice.
            for (at in maxOf(0, entry - 128) until entry - 10 step 2) {
                cancellation.throwIfCancellationRequested()
                if (word(at) != 0x2802 || !words(at + 2, 0xD10D, 0x1C20) ||
                    call(at + 6) != entry || word(at + 10) != 0x1C20
                ) continue
                val helper = call(at + 12) ?: continue
                if (menuNumericHelper(helper, sink)) return true
            }
            return false
        }

        private fun menuNumericHelper(entry: Int, sink: Int): Boolean =
            words(entry, 0xB530, 0xB082, 0x0400, 0x0C04) &&
                literalLoad(entry + 8, 0) && literalValue(entry + 8) == 0xFFFFL &&
                words(entry + 0x0A, 0x4284, 0xD03A) &&
                literalLoad(entry + 0x0E, 2) && literalPointer(entry + 0x0E) == numericRoot &&
                words(entry + 0x10, 0x0061, 0x1908, 0x0080, 0x1882, 0x7850, 0x1C0D, 0x2801, 0xD80B) &&
                literalLoad(entry + 0x20, 0) && literalPointer(entry + 0x20) != null &&
                words(entry + 0x22, 0x2107, 0x220F) && call(entry + 0x26) == sink &&
                word(entry + 0x2A) == 0xE00F

        private fun windowConsumer(site: Int): Boolean {
            val entry = site - 0x3C
            if (!words(entry, 0xB570, 0xB082, 0x0400, 0x0C04, 0x1C26) ||
                !literalLoad(entry + 0x0A, 0) || word(entry + 0x0C) != 0x2102 || call(entry + 0x0E) == null ||
                !words(entry + 0x12, 0x0600, 0x0E05, 0x1C28, 0x2100) || call(entry + 0x1A) == null ||
                !words(entry + 0x1E, 0x2C00, 0xD038) || !literalLoad(entry + 0x22, 0) ||
                word(entry + 0x24) != 0x6800 || !literalLoad(entry + 0x26, 1) ||
                !words(entry + 0x28, 0x1840, 0x7800, 0x2802, 0xD119, 0x1C20) ||
                !literalLoad(site, 0) ||
                !words(site + 2, 0x1809, 0x2000, 0x9000, 0x9001, 0x1C28, 0x2200, 0x2302) ||
                word(entry + 0x50) != 0xE018 ||
                !words(entry + 0xA0, 0xB002, 0xBC70, 0xBC01, 0x4700)
            ) return false
            val sink = call(entry + 0x4C) ?: return false
            val helper = call(entry + 0x32) ?: return false
            // The alternate contest path receives the retained copy of the same move, dereferences
            // its effect-description pointer, and forwards it to the identical text wrapper.
            if (!literalLoad(entry + 0x64, 2) || !literalLoad(entry + 0x66, 1) ||
                !words(entry + 0x68, 0x00F0, 0x1840, 0x7800, 0x0080, 0x1880, 0x6801,
                    0x2000, 0x9000, 0x9001, 0x1C28, 0x2200, 0x2302) ||
                call(entry + 0x80) != sink || word(entry + 0x8A) != 0xE006
            ) return false
            return windowNumericHelper(helper, sink) && windowTextSink(sink)
        }

        private fun windowNumericHelper(entry: Int, sink: Int): Boolean =
            words(entry, 0xB570, 0xB082, 0x0400, 0x0C05, 0x2D00, 0xD049,
                0x2018, 0x9000, 0x2020, 0x9001, 0x200E, 0x2100, 0x2228, 0x2300) &&
                call(entry + 0x1C) != null && literalLoad(entry + 0x20, 2) &&
                literalPointer(entry + 0x20) == numericRoot &&
                words(entry + 0x22, 0x0069, 0x1948, 0x0080, 0x1882, 0x7850, 0x1C0E, 0x2801, 0xD806) &&
                literalLoad(entry + 0x32, 1) && literalPointer(entry + 0x32) != null &&
                word(entry + 0x34) == 0xE00C &&
                words(entry + 0x50, 0x2000, 0x9000, 0x9001, 0x200E, 0x2228, 0x2302) &&
                call(entry + 0x5C) == sink

        private fun menuTextSink(entry: Int): Boolean =
            words(entry, 0xB530, 0xB081, 0x1C05, 0x1C0B, 0x061B, 0x0E1B, 0x0612, 0x0E12) &&
                literalLoad(entry + 0x10, 0) && isRam(literalValue(entry + 0x10)) &&
                word(entry + 0x12) == 0x6800 && literalLoad(entry + 0x14, 1) &&
                isRam(literalValue(entry + 0x14)) &&
                words(entry + 0x16, 0x880C, 0x9200, 0x1C29, 0x1C22) && call(entry + 0x1E) != null &&
                words(entry + 0x22, 0xB001, 0xBC30, 0xBC01, 0x4700)

        private fun windowTextSink(entry: Int): Boolean =
            words(entry, 0xB570, 0xB085, 0x9C09, 0x9D0A, 0x0600, 0x0E00, 0x0612, 0x0E12,
                0x061B, 0x0E1B, 0x0624, 0x0E24, 0x062D, 0x0E2D, 0x2600, 0x9600, 0x9401,
                0x006C, 0x1964) && literalLoad(entry + 0x26, 5) && literalPointer(entry + 0x26) != null &&
                words(entry + 0x28, 0x1964, 0x9402, 0x9603, 0x9104, 0x2101) && call(entry + 0x32) != null &&
                words(entry + 0x36, 0xB005, 0xBC70, 0xBC01, 0x4700)

        private fun isRam(value: Long?): Boolean = value != null &&
            (value in 0x02000000L..0x0203FFFFL || value in 0x03000000L..0x03007FFFL)

        private fun words(offset: Int, vararg expected: Int): Boolean =
            expected.indices.all { word(offset + it * 2) == expected[it] }

        private fun word(offset: Int): Int? {
            budget.recordWork()
            if (offset < 0 || offset.toLong() + 2 > rom.size) return null
            return rom.u16le(offset)
        }

        private fun literalLoad(site: Int, register: Int): Boolean =
            word(site)?.and(0xFF00) == (0x4800 or (register shl 8))

        private fun literalValue(site: Int): Long? {
            val instruction = word(site) ?: return null
            if (instruction and 0xF800 != 0x4800) return null
            val literal = ((site.toLong() + 4) and -4L) + (instruction and 0xFF) * 4
            if (literal < 0 || literal + 4 > rom.size) return null
            return rom.u32le(literal.toInt())
        }

        private fun literalPointer(site: Int): Int? = literalValue(site)?.let { value ->
            (value - 0x08000000L).takeIf { it >= 0 && it < rom.size }?.toInt()
        }

        private fun call(site: Int): Int? {
            val high = word(site) ?: return null
            val low = word(site + 2) ?: return null
            if (high and 0xF800 != 0xF000 || low and 0xF800 != 0xF800) return null
            val signedHigh = ((high and 0x7FF) shl 21) shr 9
            val target = site.toLong() + 4 + signedHigh + ((low and 0x7FF) shl 1)
            return target.takeIf { it >= 0 && it + 2 <= rom.size && it % 2 == 0L }?.toInt()
        }
    }

    private fun referencedPointerTable(
        rom: RomImage,
        codec: PokemonTextCodec,
        pointerCount: Int,
        references: GbaReferenceIndex?,
        cancellation: ParserCancellationToken,
        budget: MoveDescriptionBudget,
    ): DescriptionSearchOutcome {
        if (references == null) return DescriptionSearchOutcome.Unavailable(DescriptionSearchFailure.NO_AUTHORITY)
        if (references.overflowed) return DescriptionSearchOutcome.Unavailable(DescriptionSearchFailure.INCOMPLETE_REFERENCE)
        val tableBytes = pointerCount.toLong() * 4L
        var selected: MoveDescriptionResult? = null
        var inspected = 0
        for ((offset, evidence) in references.targets) {
            checkCancellation(inspected++, cancellation)
            if (evidence.count <= 0 || offset % 4 != 0 || offset < 0 ||
                offset.toLong() + tableBytes > rom.size.toLong()
            ) continue
            budget.recordRoot(offset)
            var pointersValid = true
            for (index in 0 until pointerCount) {
                checkCancellation(index, cancellation)
                budget.recordWork()
                if (rom.gbaPointer(offset + index * 4) == null) {
                    pointersValid = false
                    break
                }
            }
            if (!pointersValid) continue
            budget.recordCandidate()
            val candidate = decodeCandidate(
                rom,
                codec,
                offset,
                pointerCount,
                cancellation,
                budget,
                allowExplicitPlaceholders = true,
            )?.takeIf { it.descriptions.size == pointerCount } ?: continue
            if (selected != null) return DescriptionSearchOutcome.Unavailable(DescriptionSearchFailure.CONFLICT)
            selected = candidate
        }
        return selected?.let { DescriptionSearchOutcome.Resolved(it) }
            ?: DescriptionSearchOutcome.Unavailable(DescriptionSearchFailure.NO_AUTHORITY)
    }

    private fun materializeGen2(
        rom: RomImage,
        codec: PokemonTextCodec,
        moveCount: Int,
        cancellation: ParserCancellationToken,
        budget: MoveDescriptionBudget,
    ): MoveDescriptionResult? {
        if (moveCount < 4) return null
        val tableBytesLong = moveCount.toLong() * 2L
        if (tableBytesLong > GEN2_BANK_SIZE || tableBytesLong > rom.size.toLong()) return null
        val tableBytes = tableBytesLong.toInt()
        val referencedTables = gen2DescriptionTableConsumers(rom, cancellation, budget)
        if (referencedTables.isEmpty()) return null
        var selected: MoveDescriptionResult? = null
        for (reference in referencedTables) {
            cancellation.throwIfCancellationRequested()
            val offset = rom.gbBankAddress(reference.bank, reference.address) ?: continue
            val bankEnd = minOf(rom.size, (reference.bank + 1) * GEN2_BANK_SIZE)
            if (offset.toLong() + tableBytes > bankEnd.toLong()) continue
            var pointersValid = true
            for (index in 0 until moveCount) {
                checkCancellation(index, cancellation)
                budget.recordWork()
                if (!validGen2Pointer(rom.u16le(offset + index * 2))) {
                    pointersValid = false
                    break
                }
            }
            if (!pointersValid) continue
            budget.recordCandidate()
            val candidate = decodeGen2Candidate(
                rom,
                codec,
                offset,
                reference.bank,
                moveCount,
                cancellation,
                budget,
            ) ?: continue
            if (selected != null) return null
            selected = candidate
        }
        return selected
    }

    /** Finds source-defined `MoveDescriptions[(moveId - 1)]` consumers. */
    private fun gen2DescriptionTableConsumers(
        rom: RomImage,
        cancellation: ParserCancellationToken,
        budget: MoveDescriptionBudget,
    ): Set<Gen2TableReference> {
        val references = linkedSetOf<Gen2TableReference>()
        for (offset in 0..rom.size - GEN2_CONSUMER_BYTES) {
            if (offset % RomImage.DEFAULT_SCAN_CHECK_INTERVAL_BYTES == 0) {
                cancellation.throwIfCancellationRequested()
            }
            budget.recordScanBytes(1)
            if (rom.u8(offset) != 0x21) continue
            budget.recordMatch()
            if (rom.u8(offset + 3) != 0xFA ||
                rom.u8(offset + 6) != 0x3D ||
                rom.u8(offset + 7) != 0x4F ||
                rom.u8(offset + 8) != 0x06 || rom.u8(offset + 9) != 0 ||
                rom.u8(offset + 10) != 0x09 || rom.u8(offset + 11) != 0x09
            ) continue

            val address = rom.u16le(offset + 1)
            val reference = when {
                rom.u8(offset + 12) == 0x3E && rom.u8(offset + 14) == 0xCD -> {
                    Gen2TableReference(rom.u8(offset + 13), address)
                }
                rom.u8(offset + 12) == 0x2A &&
                    rom.u8(offset + 13) == 0x5F &&
                    rom.u8(offset + 14) == 0x56 -> {
                    Gen2TableReference(offset / GEN2_BANK_SIZE, address)
                }
                else -> null
            } ?: continue
            val root = rom.gbBankAddress(reference.bank, reference.address) ?: continue
            budget.recordRoot(root)
            references += reference
        }
        return references
    }

    private fun decodeGen2Candidate(
        rom: RomImage,
        codec: PokemonTextCodec,
        offset: Int,
        bank: Int,
        moveCount: Int,
        cancellation: ParserCancellationToken,
        budget: MoveDescriptionBudget,
    ): MoveDescriptionResult? {
        val descriptions = linkedMapOf<Int, String>()
        repeat(moveCount) { index ->
            checkCancellation(index, cancellation)
            budget.recordWork()
            val target = rom.gbBankAddress(bank, rom.u16le(offset + index * 2)) ?: return@repeat
            val bankEnd = minOf(rom.size, (bank + 1) * GEN2_BANK_SIZE)
            val length = minOf(MAX_GEN2_DESCRIPTION_BYTES, bankEnd - target)
            if (length <= 0) return@repeat
            val decoded = codec.decodeDetailed(rom.slice(target, length))
            val normalized = decoded.text.replace(Regex("\\s+"), " ").trim()
            if (
                decoded.terminated && decoded.validRatio >= 0.70 &&
                looksLikeNaturalDescription(normalized, codec)
            ) {
                descriptions[index + 1] = normalized
            }
        }
        val minimum = maxOf(3, (moveCount * 0.8).toInt())
        if (descriptions.size < minimum) return null
        return MoveDescriptionResult(
            sourceOffset = offset,
            confidence = descriptions.size.toDouble() / moveCount,
            descriptions = descriptions,
        )
    }

    private fun validGen2Pointer(value: Int): Boolean = value in 0x4000..0x7FFF

    private fun fallbackPointerTable(
        rom: RomImage,
        codec: PokemonTextCodec,
        pointerCount: Int,
        cancellation: ParserCancellationToken,
        budget: MoveDescriptionBudget,
    ): MoveDescriptionResult? {
        val tableBytesLong = pointerCount.toLong() * 4L
        if (tableBytesLong > rom.size.toLong()) return null
        val minimumPrefixBytes = (((pointerCount.toLong() + 1L) / 2L) * 4L).toInt()
        val tableBytes = tableBytesLong.toInt()
        var best: MoveDescriptionResult? = null
        var cursor = 0

        fun inspectCandidate(offset: Int) {
            budget.recordRoot(offset)
            budget.recordCandidate()
            val candidate = decodeCandidate(rom, codec, offset, pointerCount, cancellation, budget) ?: return
            val current = best
            if (current == null || MOVE_DESCRIPTION_ORDER.compare(candidate, current) > 0) best = candidate
        }

        while (cursor + 4 <= rom.size) {
            if (cursor % RomImage.DEFAULT_SCAN_CHECK_INTERVAL_BYTES == 0) {
                cancellation.throwIfCancellationRequested()
            }
            budget.recordScanBytes(4)
            if (rom.gbaPointer(cursor) == null) {
                cursor += 4
                continue
            }
            val runStart = cursor
            var runLength = 0
            while (cursor + 4 <= rom.size && rom.gbaPointer(cursor) != null) {
                if (cursor % RomImage.DEFAULT_SCAN_CHECK_INTERVAL_BYTES == 0) {
                    cancellation.throwIfCancellationRequested()
                }
                budget.recordScanBytes(4)
                cursor += 4
                runLength += 4
                if (runLength >= tableBytes && runLength % tableBytes == 0) {
                    inspectCandidate(runStart + runLength - tableBytes)
                }
            }
            if (runLength in minimumPrefixBytes until tableBytes) inspectCandidate(runStart)
        }
        cancellation.throwIfCancellationRequested()
        return best
    }

    private fun decodeCandidate(
        rom: RomImage,
        codec: PokemonTextCodec,
        offset: Int,
        pointerCount: Int,
        cancellation: ParserCancellationToken,
        budget: MoveDescriptionBudget,
        allowExplicitPlaceholders: Boolean = false,
    ): MoveDescriptionResult? {
        val descriptions = linkedMapOf<Int, String>()
        repeat(pointerCount) { index ->
            checkCancellation(index, cancellation)
            budget.recordWork()
            val textOffset = runCatching { rom.gbaPointer(offset + index * 4) }.getOrNull() ?: return@repeat
            val length = minOf(192, rom.size - textOffset)
            val decoded = runCatching { codec.decodeDetailed(rom.slice(textOffset, length)) }.getOrNull() ?: return@repeat
            val normalized = decoded.text.replace(Regex("\\s+"), " ").trim()
            if (
                decoded.terminated && decoded.validRatio >= 0.85 &&
                (normalized.length >= 5 || allowExplicitPlaceholders && isExplicitPlaceholder(normalized))
            ) {
                descriptions[index + 1] = normalized
            }
        }
        val decodedRatio = descriptions.size.toDouble() / pointerCount
        val naturalDescriptionCount = descriptions.values.count { looksLikeNaturalDescription(it, codec) }
        val naturalLanguageRatio = naturalDescriptionCount.toDouble() / descriptions.size.coerceAtLeast(1)
        val confidence = minOf(decodedRatio, naturalLanguageRatio)
        val minimum = maxOf(3, (pointerCount * 0.8).toInt())
        return if (descriptions.size >= minimum && naturalLanguageRatio >= 0.75) {
            MoveDescriptionResult(offset, confidence, descriptions)
        } else {
            null
        }
    }

    private fun looksLikeNaturalDescription(value: String, codec: PokemonTextCodec): Boolean =
        LanguageTextPlausibility.looksLikeNaturalDescription(
            value = value,
            language = codec.language,
            minimumLength = 12,
            minimumWords = 3,
            requireLowercase = true,
        )

    private fun isExplicitPlaceholder(value: String): Boolean = value == "-" || value == "—"

    private fun decodeText(rom: RomImage, offset: Int, codec: PokemonTextCodec): String? {
        val length = minOf(256, rom.size - offset)
        val decoded = runCatching { codec.decodeDetailed(rom.slice(offset, length)) }.getOrNull()
            ?: return null
        val normalized = decoded.text.replace(Regex("\\s+"), " ").trim()
        return normalized.takeIf {
            decoded.terminated && decoded.validRatio >= 0.85 && looksLikeNaturalDescription(it, codec)
        }
    }

    private fun checkCancellation(index: Int, cancellation: ParserCancellationToken) {
        if (index % CANCELLATION_CHECK_RECORD_INTERVAL == 0) cancellation.throwIfCancellationRequested()
    }

    private class MoveDescriptionBudget(private val limits: ResolutionLimits) {
        private val roots = linkedSetOf<Int>()
        private var matches = 0
        private var candidates = 0
        private var work = 0L
        private var scanBytes = 0L
        private var retainedReferences = 0

        fun recordRecoveredReference(observedForTarget: Int) {
            recordWork()
            if (observedForTarget > limits.maxNominatedGbaReferenceSites) throw MoveDescriptionBudgetExceededException()
        }

        fun recordRetainedReference() {
            if (retainedReferences == limits.maxNominatedGbaReferenceSites) throw MoveDescriptionBudgetExceededException()
            retainedReferences++
        }

        fun recordRoot(root: Int) {
            if (root in roots) return
            if (roots.size == limits.maxProbeRootsPerDataset) throw MoveDescriptionBudgetExceededException()
            roots += root
        }

        fun recordMatch() {
            if (matches == limits.maxProbeWorkPerDataset) throw MoveDescriptionBudgetExceededException()
            matches++
        }

        fun recordCandidate() {
            if (candidates == limits.maxCandidatesPerDataset) throw MoveDescriptionBudgetExceededException()
            candidates++
        }

        fun recordWork() {
            if (work == limits.maxProbeWorkPerDataset.toLong()) throw MoveDescriptionBudgetExceededException()
            work++
        }

        fun recordScanBytes(bytes: Long) {
            if (scanBytes > limits.maxDatasetExtentBytes - bytes) throw MoveDescriptionBudgetExceededException()
            scanBytes += bytes
        }
    }

    private class IncompleteMoveDescriptionReferencesException : RuntimeException()
    private class MoveDescriptionBudgetExceededException : RuntimeException()

    private data class Gen2TableReference(val bank: Int, val address: Int)

    private const val DIRECT_RECORD_BYTES = 56
    private val WESTERN_POINTER_LANGUAGES = setOf(
        LanguageTag.ENGLISH, LanguageTag.FRENCH, LanguageTag.GERMAN, LanguageTag.ITALIAN, LanguageTag.SPANISH,
    )
    private const val GEN2_BANK_SIZE = 0x4000
    private const val GEN2_CONSUMER_BYTES = 15
    private const val MAX_GEN2_DESCRIPTION_BYTES = 192
    private const val CANCELLATION_CHECK_RECORD_INTERVAL = 64
    private val MOVE_DESCRIPTION_ORDER =
        compareBy<MoveDescriptionResult> { it.confidence }.thenBy { it.descriptions.size }
}
