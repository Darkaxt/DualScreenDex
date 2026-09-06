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
            val recoveredSites = recoverIncompleteDirectSites(rom, references, cancellation, budget)
            val numericEvidence = references.targets[moves.offset]
            val numericSites = if (numericEvidence?.siteEvidenceAvailable == true) numericEvidence.instructionSites
                else recoveredSites[moves.offset].orEmpty()
            val consumer = DirectMoveConsumer(rom, moves.offset, budget, cancellation, numericSites)
            var selected: DirectRecordWitness? = null
            for ((base, evidence) in references.targets) {
                cancellation.throwIfCancellationRequested()
                budget.recordWork()
                val sites = if (evidence.siteEvidenceAvailable) evidence.instructionSites
                    else recoveredSites[base] ?: return unavailable(DescriptionSearchFailure.INCOMPLETE_REFERENCE)
                for (site in sites) {
                    budget.recordWork()
                    val witness = consumer.proves(site, base) ?: continue
                    budget.recordRoot(base)
                    budget.recordCandidate()
                    if (selected != null && selected != witness) {
                        return unavailable(DescriptionSearchFailure.CONFLICT)
                    }
                    selected = witness
                }
            }
            val witness = selected ?: return unavailable(DescriptionSearchFailure.NO_AUTHORITY)
            val result = decodeDirectRecords(rom, codec, witness, count, cancellation, budget)
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
                            if (directConsumerShape(rom, site) != null || stateNumericShape(rom, site)) {
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

    private data class DirectRecordWitness(val biasedBase: Int, val stride: Int)

    private enum class DirectConsumerShape { MENU, WINDOW, STATE_BUFFER }

    /** Locator only, shared with incomplete-site recovery; numeric role is proved separately. */
    private fun stateNumericShape(rom: RomImage, site: Int): Boolean =
        site >= 4 && site <= rom.size - 2 && rom.u16le(site - 4) == 0x1844 && rom.u16le(site - 2) == 0x444C

    private fun directConsumerShape(rom: RomImage, site: Int): DirectConsumerShape? {
        if (site < 6 || site > rom.size) return null
        return when (rom.u16le(site - 6)) {
            0x00C8 -> if (rom.u16le(site - 4) == 0x1A40 && rom.u16le(site - 2) == 0x00C0) DirectConsumerShape.MENU else null
            0x00E1 -> if (rom.u16le(site - 4) == 0x1B09 && rom.u16le(site - 2) == 0x00C9) DirectConsumerShape.WINDOW else null
            0x0111 -> if (rom.u16le(site - 4) == 0x1A89 && rom.u16le(site - 2) == 0x0089) DirectConsumerShape.STATE_BUFFER else null
            else -> null
        }
    }

    private fun decodeDirectRecords(
        rom: RomImage,
        codec: PokemonTextCodec,
        witness: DirectRecordWitness,
        count: Int,
        cancellation: ParserCancellationToken,
        budget: MoveDescriptionBudget,
    ): MoveDescriptionResult? {
        val (biasedBase, stride) = witness
        val first = biasedBase.toLong() + stride
        val length = count.toLong() * stride
        if (count < 3 || biasedBase < 0 || first + length > rom.size) return null
        budget.recordScanBytes(length)
        val descriptions = linkedMapOf<Int, String>()
        repeat(count) { index ->
            checkCancellation(index, cancellation)
            budget.recordWork()
            val record = rom.slice((first + index.toLong() * stride).toInt(), stride)
            val decoded = codec.decodeDetailed(record)
            val text = decoded.text.replace(Regex("\\s+"), " ").trim()
            // decodeDetailed owns token boundaries; FF occurring inside a control is not a terminator.
            // An unknown/truncated token cannot be dropped merely because surrounding prose is
            // readable. Keep this strict rule local to structurally proven native direct records.
            if (!decoded.terminated || decoded.invalidUnits != 0 || text.length < 5) return null
            descriptions[index + 1] = text
        }
        return MoveDescriptionResult(first.toInt(), 1.0, descriptions)
    }

    /**
     * Source-defined summary-screen ABIs, recognized through relocated instructions, not ROM
     * identities. A u16 move is shared with the selected 12-byte numeric table's consumer;
     * its text role must be proved separately from stride and record readability.
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
        private val numericSites: List<Int>,
    ) {
        fun proves(site: Int, base: Int): DirectRecordWitness? {
            if (site < 6 || site % 2 != 0 || literalPointer(site) != base) return null
            val stride = when (directConsumerShape(rom, site)) {
                DirectConsumerShape.MENU -> if (menuConsumer(site)) 56 else null
                DirectConsumerShape.WINDOW -> if (windowConsumer(site)) 56 else null
                DirectConsumerShape.STATE_BUFFER -> if (stateBufferConsumer(site)) 60 else null
                null -> null
            } ?: return null
            return DirectRecordWitness(base, stride)
        }

        /**
         * Static source-role proof for the state-buffer summary ABI (pokefirered
         * c75f352304d529f6ba92d4f74b9cf8b5c3810788, pokemon_summary_screen.c/menu2.c/text_printer.c).
         * The compiled arithmetic, not the Western source's pointer-array declaration, proves 60.
         * The same RAM pointer declaration + u16 slot is used by the selected numeric type consumer.
         * Only its call-free nonzero branch is needed: PP/formatting calls are not interpreted and
         * no live RAM values or graphics execution are claimed. The numeric text wrapper's two
         * pre-template font calls separately require the bounded read-only leaf proof below.
         */
        private fun stateBufferConsumer(site: Int): Boolean {
            val e = site - 0x98
            if (!words(e, 0xB5F0, 0x4647, 0xB480, 0xB085) ||
                !literalLoad(e + 8, 0) || !isRam(literalValue(e + 8)) ||
                !words(e + 0x0A, 0x4680, 0x7801, 0x2904, 0xD84A) ||
                !literalLoad(e + 0x12, 7) || word(e + 0x14) != 0x683B ||
                !literalLoad(e + 0x16, 2) || literalValue(e + 0x16) != 0x31B4L ||
                !words(e + 0x18, 0x1898, 0x7800, 0x2802, 0xD001, 0x2904, 0xD041) ||
                !literalLoad(e + 0x24, 4) || literalValue(e + 0x24) != 0x3004L ||
                !words(e + 0x26, 0x1918, 0x7800) ||
                !literalLoad(e + 0x2A, 6) || literalPointer(e + 0x2A) == null ||
                !words(e + 0x2C, 0x9600, 0x2501, 0x426D, 0x9501, 0x4641, 0x780A,
                    0x0091, 0x1889, 0x22C5, 0x0192, 0x1889, 0x1859, 0x9102, 0x2102, 0x2232, 0x2301) ||
                !words(e + 0x50, 0x683B, 0x1918, 0x7800, 0x9600, 0x9501, 0x4641,
                    0x780A, 0x0091, 0x1889) ||
                !literalLoad(e + 0x62, 2) || literalValue(e + 0x62) != 0x315CL ||
                !words(e + 0x64, 0x1889, 0x185B, 0x9302, 0x2102, 0x2232, 0x230F) ||
                !words(e + 0x74, 0x683A, 0x1914, 0x7820, 0x2100, 0x9100, 0x9101,
                    0x9602, 0x9503, 0x4643, 0x7819, 0x0049) ||
                !literalLoad(e + 0x8A, 3) ||
                !words(e + 0x8C, 0x18D2, 0x1852, 0x8812, 0x0111, 0x1A89, 0x0089) ||
                !literalLoad(site, 2) ||
                !words(e + 0x9A, 0x1889, 0x9104, 0x2102, 0x2205, 0x2327) ||
                !words(e + 0xA8, 0xB005, 0xBC08, 0x4698, 0xBCF0, 0xBC01, 0x4700)
            ) return false
            val state = literalValue(e + 0x12) ?: return false
            val field = literalValue(e + 0x8A) ?: return false
            if (!isRam(state) || state % 4 != 0L || state == literalValue(e + 8) ||
                field !in 10L..0xFFE0L || field % 2 != 0L
            ) return false
            val proseWrapper = call(e + 0xA4) ?: return false
            val printer = stateProseTextSink(proseWrapper) ?: return false
            val numericWrapper = call(e + 0x4C) ?: return false
            if (call(e + 0x70) != numericWrapper || !stateNumericTextSink(numericWrapper, printer) ||
                !templatePrinter(printer)
            ) return false
            for (numericSite in numericSites) {
                cancellation.throwIfCancellationRequested()
                budget.recordWork()
                if (stateNumericConsumer(numericSite, state, field)) return true
            }
            return false
        }

        private fun stateNumericConsumer(site: Int, state: Long, field: Long): Boolean {
            if (!stateNumericShape(rom, site) || !literalLoad(site, 5) || literalPointer(site) != numericRoot) return false
            val e = site - 0xD6
            // At this block's entry r7 is a slot parameter. It is doubled once and used both for
            // the nonzero test and the subsequent selected-table type lookup; no call intervenes.
            return literalLoad(e + 0x2A, 6) && literalValue(e + 0x2A) == state &&
                words(e + 0x2C, 0x6832, 0x0078) &&
                literalLoad(e + 0x30, 1) && literalValue(e + 0x30) == field &&
                words(e + 0x32, 0x4688, 0x1851, 0x1809, 0x8809, 0x4681, 0x2900, 0xD141) &&
                literalLoad(e + 0xC4, 0) && literalValue(e + 0xC4) == field + 12 &&
                words(e + 0xC6, 0x1811, 0x7808, 0x3001, 0x7008, 0x6830) &&
                literalLoad(e + 0xD0, 1) && literalValue(e + 0xD0) == field - 10 &&
                words(e + 0xD2, 0x1844, 0x444C) &&
                literalLoad(e + 0xD8, 2) && literalValue(e + 0xD8) == field &&
                words(e + 0xDA, 0x1883, 0x444B, 0x881A, 0x0051, 0x1889, 0x0089,
                    0x1949, 0x7889, 0x8021)
        }

        private fun stateProseTextSink(e: Int): Int? {
            if (!words(e, 0xB570, 0x464E, 0x4645, 0xB460, 0xB084, 0x1C0D,
                    0x990A, 0x4688, 0x990B, 0x4689, 0x9E0C, 0x990D, 0x9C0E, 0x9400,
                    0x466C, 0x7120, 0x4668, 0x7145, 0x7182, 0x71C3, 0x466A, 0x7980,
                    0x7210, 0x4668, 0x79C0, 0x7250, 0x4668, 0x4642, 0x7282, 0x464A,
                    0x72C2, 0x7B23, 0x2210, 0x4252, 0x1C10, 0x4018, 0x7320, 0x466B,
                    0x7870, 0x0100, 0x250F, 0x7318, 0x7833, 0x1C28, 0x4018, 0x7B63,
                    0x401A, 0x4302, 0x7362, 0x466B, 0x78B0, 0x0100, 0x402A, 0x4302,
                    0x735A, 0x0609, 0x0E09, 0x4668, 0x2200) ||
                !words(e + 0x7A, 0xB004, 0xBC18, 0x4698, 0x46A1, 0xBC70, 0xBC01, 0x4700)
            ) return null
            return call(e + 0x76)
        }

        private fun stateNumericTextSink(e: Int, printer: Int): Boolean {
            if (!words(e, 0xB570, 0xB084, 0x1C0C, 0x9E08, 0x9D09, 0x990A, 0x0624,
                    0x0E24, 0x062D, 0x0E2D, 0x9100, 0x4669, 0x7108, 0x4668, 0x7144,
                    0x7182, 0x71C3, 0x7980, 0x7208, 0x4668, 0x79C0, 0x7248, 0x1C20, 0x2102) ||
                !words(e + 0x34, 0x4669, 0x7288, 0x1C20, 0x2103) ||
                !words(e + 0x40, 0x4669, 0x72C8, 0x466B, 0x7B1A, 0x2110, 0x4249,
                    0x1C08, 0x4010, 0x7318, 0x466A, 0x7870, 0x0100, 0x240F, 0x7310,
                    0x7832, 0x1C20, 0x4010, 0x7B5A, 0x4011, 0x4301, 0x7359, 0x466A,
                    0x78B0, 0x0100, 0x4021, 0x4301, 0x7351, 0x4668, 0x1C29, 0x2200) ||
                !words(e + 0x80, 0xB004, 0xBC70, 0xBC01, 0x4700)
            ) return false
            // currentChar is already stored at sp0: equal BL targets alone do not preserve it.
            val fontAttributes = call(e + 0x30) ?: return false
            return call(e + 0x3C) == fontAttributes && call(e + 0x7C) == printer &&
                stateFontSpacingLeaf(fontAttributes)
        }

        /**
         * GetFontAttribute's supported compiled leaf (pokefirered c75f352, new_menu_helpers.c).
         * The checked caller passes exactly attributes 2 and 3. Bind their jump-table entries,
         * entire read-only paths and return; other switch arms are unreachable for these calls.
         * PUSH LR writes below caller SP; no selected instruction can overwrite currentChar,
         * change callee-saved registers or call an unchecked descendant. POP/BX restores SP/LR.
         * Fixed relative instruction/literal extents stay within 226 bytes; all ROM roots relocate.
         */
        private fun stateFontSpacingLeaf(e: Int): Boolean {
            if (e < 0 || e % 4 != 0 || e.toLong() + 0xE2 > rom.size ||
                !words(e, 0xB500, 0x0600, 0x0E02, 0x0609, 0x0E09, 0x2000,
                    0x2907, 0xD866, 0x0088, 0x4902, 0x1840, 0x6800, 0x4687) ||
                literalPointer(e + 0x12) != e + 0x20 ||
                !words(e + 0x68, 0x4903, 0x0050, 0x1880, 0x0080, 0x1840, 0x7980, 0xE033) ||
                !words(e + 0x7C, 0x4903, 0x0050, 0x1880, 0x0080, 0x1840, 0x79C0, 0xE029) ||
                !words(e + 0xDE, 0xBC02, 0x4708)
            ) return false
            budget.recordWork()
            if (rom.u32le(e + 0x28) != 0x08000000L + e + 0x68) return false
            budget.recordWork()
            if (rom.u32le(e + 0x2C) != 0x08000000L + e + 0x7C) return false
            val fonts = literalPointer(e + 0x68) ?: return false
            return literalPointer(e + 0x7C) == fonts
        }

        private fun templatePrinter(e: Int): Boolean =
            words(e, 0xB5F0, 0x1C06, 0x4694, 0x0609, 0x0E0D) &&
                literalLoad(e + 0x0A, 0) && isRam(literalValue(e + 0x0A)) &&
                words(e + 0x0C, 0x6800, 0x2800, 0xD104, 0x2000, 0xE05C) &&
                literalLoad(e + 0x1C, 0) && isRam(literalValue(e + 0x1C)) &&
                words(e + 0x1E, 0x2200, 0x2101, 0x76C1, 0x7702, 0x7745, 0x7782,
                    0x77C2, 0x1C04, 0x2106, 0x301A, 0x7002, 0x3801, 0x3901, 0x2900,
                    0xDAFA, 0x1C21, 0x1C30, 0xC88C, 0xC18C, 0x6800, 0x6008, 0x4660, 0x6120) &&
                words(e + 0xD0, 0xBCF0, 0xBC02, 0x4708)

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
