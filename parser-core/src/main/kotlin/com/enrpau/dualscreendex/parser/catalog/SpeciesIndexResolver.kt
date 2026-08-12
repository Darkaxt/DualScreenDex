package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.resolution.BudgetKind
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

sealed interface SpeciesIndexResolution {
    val values: Map<Int, Int>

    data class Resolved(override val values: Map<Int, Int>) : SpeciesIndexResolution

    data class Unavailable(
        override val values: Map<Int, Int>,
        val reason: String,
        val ambiguous: Boolean = false,
    ) : SpeciesIndexResolution

    data class BudgetExceeded(
        override val values: Map<Int, Int>,
        val budgetKind: BudgetKind,
        val observed: Long,
        val limit: Long,
        val reason: String,
    ) : SpeciesIndexResolution
}

object SpeciesIndexResolver {
    /** Explicit compatibility adapter for catalog callers that still consume only the map. */
    fun resolve(rom: RomImage, layout: ResolvedRomLayout): Map<Int, Int> {
        val resolution = resolveWithEvidence(rom, layout, ResolutionLimits())
        return if (resolution is SpeciesIndexResolution.Unavailable &&
            resolution.reason == SMALL_LEGACY_IDENTITY_REASON
        ) {
            identity(0, (layout.speciesCount ?: 1) - 1)
        } else {
            resolution.values
        }
    }

    fun resolve(session: RomAnalysisSession, layout: ResolvedRomLayout): SpeciesIndexResolution =
        resolveWithEvidence(session.rom, layout, session.limits)

    fun resolveWithEvidence(
        rom: RomImage,
        layout: ResolvedRomLayout,
        limits: ResolutionLimits = ResolutionLimits(),
    ): SpeciesIndexResolution =
        when (layout.generation) {
            1 -> SpeciesIndexResolution.Resolved(resolveGen1(rom, layout))
            2 -> SpeciesIndexResolution.Resolved((1..(layout.speciesCount ?: 0)).associateWith { it })
            3 -> resolveGen3(rom, layout, limits)
            else -> SpeciesIndexResolution.Unavailable(emptyMap(), "unsupported species-index generation")
        }

    private fun resolveGen1(rom: RomImage, layout: ResolvedRomLayout): Map<Int, Int> {
        val internalCount = layout.speciesCount ?: layout.tables.speciesNames?.count ?: return emptyMap()
        val dexCount = layout.tables.baseStats?.count ?: return identity(1, internalCount)
        for (offset in 0..rom.size - internalCount) {
            val values = IntArray(internalCount) { index -> rom.u8(offset + index) }
            if (values.all { it in 0..dexCount } &&
                values.count { it != 0 } == dexCount &&
                values.filter { it != 0 }.toSet() == (1..dexCount).toSet()
            ) {
                return (1..internalCount).associateWith { id -> values[id - 1] }
            }
        }
        return identity(1, internalCount)
    }

    private fun resolveGen3(
        rom: RomImage,
        layout: ResolvedRomLayout,
        limits: ResolutionLimits,
    ): SpeciesIndexResolution {
        val speciesCount = layout.speciesCount ?: return SpeciesIndexResolution.Unavailable(
            emptyMap(),
            "Gen III species count is unavailable",
        )
        layout.pokeemeraldExpansion?.let { expansion ->
            val table = layout.tables.baseStats ?: return SpeciesIndexResolution.Unavailable(
                emptyMap(),
                "published expansion species table is unavailable",
            )
            val stride = table.stride ?: expansion.speciesRecordSize
            return SpeciesIndexResolution.Resolved(
                (0 until speciesCount).associateWith { id ->
                    rom.u16le(table.offset + id * stride + expansion.nationalDexOffset)
                },
            )
        }
        val storedCount = speciesCount - 1
        if (storedCount <= 0) return SpeciesIndexResolution.Resolved(mapOf(0 to 0))
        val fallback = unresolvedGen3Fallback(speciesCount)
        when (
            val extent = limits.checkTableExtent(
                offset = 0,
                count = storedCount.toLong(),
                recordSize = 2,
                romSize = rom.size.toLong(),
            )
        ) {
            is com.enrpau.dualscreendex.parser.analysis.ExtentCheck.BudgetExceeded -> {
                return SpeciesIndexResolution.BudgetExceeded(
                    fallback,
                    BudgetKind.EXTENT,
                    extent.observedBytes,
                    extent.limitBytes,
                    "Gen III species-index table extent budget exceeded " +
                        "(${extent.observedBytes} > ${extent.limitBytes})",
                )
            }
            is com.enrpau.dualscreendex.parser.analysis.ExtentCheck.Invalid -> {
                return SpeciesIndexResolution.Unavailable(fallback, extent.reason)
            }
            is com.enrpau.dualscreendex.parser.analysis.ExtentCheck.Valid -> Unit
        }
        val referenceIndex = layout.compiledGbaReferences
        if (referenceIndex?.overflowed == true) {
            return SpeciesIndexResolution.BudgetExceeded(
                fallback,
                BudgetKind.REFERENCE_TARGETS,
                limits.maxDistinctGbaReferenceTargets.toLong() + 1L,
                limits.maxDistinctGbaReferenceTargets.toLong(),
                requireNotNull(referenceIndex.overflowReason),
            )
        }
        val compiledReferences = referenceIndex?.counts.orEmpty()
        val budget = SpeciesDiscoveryBudget(limits)
        // Compiled consumers are direct role evidence, so evaluate their finite table roots before
        // broad content discovery can spend the dataset's probe budget on incidental 1,2 prefixes.
        val compiledIndexed = findCompiledIndexedGen3Map(rom, layout, storedCount, budget)
        compiledIndexed.budgetFailure?.let { return it.toResolution(fallback) }
        compiledIndexed.values?.let { values ->
            return SpeciesIndexResolution.Resolved(indexedGen3Values(values))
        }
        val candidates = mutableListOf<Gen3IndexCandidate>()
        var prefixEvidenceObserved = false
        val lastTableStart = rom.size - storedCount * 2
        var offset = 0
        while (offset <= lastTableStart) {
            val prefixMatches = rom.u16le(offset) == 1 && (storedCount == 1 || rom.u16le(offset + 2) == 2)
            if (!prefixMatches) {
                offset += 2
                continue
            }
            prefixEvidenceObserved = true
            budget.recordRoot(offset)?.let { return it.toResolution(fallback) }
            budget.recordWork("prefix table probe")?.let { return it.toResolution(fallback) }
            val summary = summarizeU16Values(rom, offset, storedCount, maxOf(2_048, speciesCount * 2))
            if (summary != null) {
                val distinctRatio = summary.distinctCount.toDouble() / summary.positiveCount.coerceAtLeast(1)
                val duplicateFormsAreCredible =
                    summary.canonicalBoundary &&
                        summary.identityPrefix >= minOf(CANONICAL_IDENTITY_PREFIX, storedCount) &&
                        distinctRatio >= MINIMUM_FORM_MAP_DISTINCT_RATIO &&
                        (compiledReferences[offset] ?: 0) > 0 &&
                        namedPositiveCoverage(rom, layout, offset, storedCount) >= MINIMUM_NAMED_POSITIVE_COVERAGE
                val highDistinctnessReorderedMapIsCredible =
                    summary.canonicalBoundary &&
                        distinctRatio >= MINIMUM_REORDERED_MAP_DISTINCT_RATIO &&
                        (compiledReferences[offset] ?: 0) > 0 &&
                        namedPositiveCoverage(rom, layout, offset, storedCount) >= MINIMUM_NAMED_POSITIVE_COVERAGE
                if (summary.identityPrefix >= minOf(2, storedCount) &&
                    (distinctRatio == 1.0 || duplicateFormsAreCredible || highDistinctnessReorderedMapIsCredible) &&
                    candidates.none { matchesValuesAt(rom, offset, it.values) }
                ) {
                    budget.recordCandidate()?.let { return it.toResolution(fallback) }
                    candidates += Gen3IndexCandidate(
                        summary.identityPrefix,
                        distinctRatio,
                        if (summary.canonicalBoundary) 1 else 0,
                        compiledReferences[offset] ?: 0,
                        readU16Values(rom, offset, storedCount),
                    )
                }
            }
            offset += 2
        }
        val contentDistinctCandidates = candidates
        val prefixRanking = compareBy<Gen3IndexCandidate> { it.canonicalBoundary }
            .thenBy { it.distinctRatio }
            .thenBy { it.prefix }
            .thenBy { it.compiledReferences }
            .thenBy { candidate -> candidate.values.count { it > 0 } }
        val prefixCandidate = contentDistinctCandidates.maxWithOrNull(prefixRanking)?.values
        val referencedPrefixCandidates = contentDistinctCandidates.filter { it.compiledReferences > 0 }
        val referencedPrefixWinner = referencedPrefixCandidates.maxWithOrNull(prefixRanking)
            ?.takeUnless { winner ->
                referencedPrefixCandidates.any { candidate ->
                    candidate !== winner && candidate.sameStrengthAs(winner)
                }
            }
            ?.values
        val values = when {
            compiledIndexed.ambiguous -> referencedPrefixWinner
            else -> prefixCandidate
        }
        if (values == null) {
            if (compiledIndexed.ambiguous) {
                return SpeciesIndexResolution.Unavailable(
                    fallback,
                    "compiled species-index consumers resolve to conflicting complete mappings",
                    ambiguous = true,
                )
            }
            val permutation = findCompleteGen3Permutation(rom, speciesCount, budget)
            permutation.budgetFailure?.let { return it.toResolution(fallback) }
            if (permutation.ambiguous) {
                return SpeciesIndexResolution.Unavailable(
                    fallback,
                    "complete Gen III species-index permutations are ambiguous",
                    ambiguous = true,
                )
            }
            permutation.values?.let { resolved ->
                return SpeciesIndexResolution.Resolved(resolved.indices.associateWith { resolved[it] })
            }
            if (!compiledIndexed.evidenceObserved && !prefixEvidenceObserved &&
                storedCount < CANONICAL_IDENTITY_PREFIX
            ) {
                return SpeciesIndexResolution.Unavailable(
                    fallback,
                    SMALL_LEGACY_IDENTITY_REASON,
                )
            }
            return SpeciesIndexResolution.Unavailable(
                fallback,
                "Gen III species-to-National-Dex mapping is unresolved",
            )
        }
        return SpeciesIndexResolution.Resolved(indexedGen3Values(values))
    }

    private fun findCompiledIndexedGen3Map(
        rom: RomImage,
        layout: ResolvedRomLayout,
        storedCount: Int,
        budget: SpeciesDiscoveryBudget,
    ): CompiledIndexSearch {
        val candidates = mutableListOf<CompiledIndexCandidate>()
        val roots = linkedSetOf<Int>()
        var evidenceObserved = false
        var instructionOffset = 0
        while (instructionOffset <= rom.size - 10) {
            val load = rom.u16le(instructionOffset)
            if (load and THUMB_LITERAL_LOAD_MASK != THUMB_LITERAL_LOAD_OPCODE) {
                instructionOffset += 2
                continue
            }
            val baseRegister = (load ushr 8) and 0x7
            val subtract = rom.u16le(instructionOffset + 2)
            val indexRegister = (subtract ushr 8) and 0x7
            val shift = rom.u16le(instructionOffset + 4)
            val add = rom.u16le(instructionOffset + 6)
            val halfwordLoad = rom.u16le(instructionOffset + 8)
            if (subtract and 0xF8FF != 0x3801 ||
                shift and 0xF800 != 0 || (shift ushr 6) and 0x1F != 1 ||
                (shift ushr 3) and 0x7 != indexRegister || shift and 0x7 != indexRegister ||
                add and 0xFE00 != 0x1800 || add and 0x7 != indexRegister ||
                !addCombinesRegisters(add, baseRegister, indexRegister) ||
                halfwordLoad and 0xF800 != 0x8800 || (halfwordLoad ushr 6) and 0x1F != 0 ||
                (halfwordLoad ushr 3) and 0x7 != indexRegister
            ) {
                instructionOffset += 2
                continue
            }
            val pc = (instructionOffset + 4) and -4
            val literalOffset = pc + (load and 0xFF) * 4
            val tableOffset = literalOffset.takeIf { it <= rom.size - 4 }
                ?.let { (rom.u32le(it) - GBA_ROM_BASE).takeIf { target -> target in 0 until rom.size.toLong() } }
                ?.toInt()
            if (tableOffset != null && tableOffset.toLong() + storedCount.toLong() * 2 <= rom.size) {
                evidenceObserved = true
                if (tableOffset !in roots) {
                    budget.recordRoot(tableOffset)?.let { return CompiledIndexSearch(budgetFailure = it) }
                    roots += tableOffset
                    budget.recordWork("compiled indexed table probe")?.let {
                        return CompiledIndexSearch(budgetFailure = it)
                    }
                    val regionalOrderConsumerRole =
                        hasRegionalOrderConsumerRole(rom, instructionOffset, budget.limits)
                    val summary = summarizeU16Values(
                        rom,
                        tableOffset,
                        storedCount,
                        maxOf(2_048, storedCount * 2),
                    )
                    if (summary != null && candidates.none { matchesValuesAt(rom, tableOffset, it.values) }) {
                        compiledIndexCandidateMetrics(
                            rom,
                            layout,
                            tableOffset,
                            storedCount,
                            summary,
                        )?.let { metrics ->
                            budget.recordCandidate()?.let {
                                return CompiledIndexSearch(budgetFailure = it)
                            }
                            candidates += CompiledIndexCandidate(
                                offset = tableOffset,
                                values = readU16Values(rom, tableOffset, storedCount),
                                regionalOrderConsumerRole = regionalOrderConsumerRole,
                                completePermutation = metrics.completePermutation,
                                publicationEligible = metrics.publicationEligible,
                                compositionEligible = metrics.compositionEligible,
                                descriptionCoverage = metrics.descriptionCoverage,
                                mappedNameCoverage = metrics.mappedNameCoverage,
                                positiveCount = summary.positiveCount,
                                distinctDexCount = summary.distinctCount,
                            )
                        }
                    }
                }
            }
            instructionOffset += 2
        }
        val allRanked = candidates.distinctBy { it.values.asList() }.sortedWith(
            compareByDescending<CompiledIndexCandidate> { it.completePermutation }
                .thenByDescending { it.descriptionCoverage }
                .thenByDescending { it.mappedNameCoverage }
                .thenByDescending { it.positiveCount }
                .thenByDescending { it.distinctDexCount },
        )
        val compositionCandidates = allRanked.filter(CompiledIndexCandidate::compositionEligible)
        val completeComposition = resolveSpeciesMapByComposition(compositionCandidates, budget)
        completeComposition.budgetFailure?.let {
            return CompiledIndexSearch(budgetFailure = it, evidenceObserved = true)
        }
        completeComposition.candidate?.let {
            return CompiledIndexSearch(values = it.values, evidenceObserved = true)
        }
        if (compositionCandidates.size >= 3) {
            return CompiledIndexSearch(ambiguous = true, evidenceObserved = true)
        }
        val ranked = allRanked.filter(CompiledIndexCandidate::publicationEligible)
        val first = ranked.firstOrNull() ?: return CompiledIndexSearch(evidenceObserved = evidenceObserved)
        val second = ranked.getOrNull(1)
        if (second == null || !first.sameStrengthAs(second)) {
            return if (first.regionalOrderConsumerRole) {
                CompiledIndexSearch(ambiguous = true, evidenceObserved = true)
            } else {
                CompiledIndexSearch(values = first.values, evidenceObserved = true)
            }
        }
        val tied = ranked.takeWhile(first::sameStrengthAs)
        val tiedComposition = resolveSpeciesMapByComposition(tied, budget)
        tiedComposition.budgetFailure?.let {
            return CompiledIndexSearch(budgetFailure = it, evidenceObserved = true)
        }
        return if (tiedComposition.candidate != null) {
            CompiledIndexSearch(values = tiedComposition.candidate.values, evidenceObserved = true)
        } else {
            CompiledIndexSearch(ambiguous = true, evidenceObserved = true)
        }
    }

    /**
     * The stock Gen III engine compiles three equally shaped permutations: species -> regional,
     * species -> National, and regional -> National. The species -> regional map is the unique A
     * for which C[A[species]] == B[species]. This role proof is independent of ROM/table order and
     * tolerates one malformed row only when every competing role assignment stays below threshold.
     */
    private fun resolveSpeciesMapByComposition(
        candidates: List<CompiledIndexCandidate>,
        budget: SpeciesDiscoveryBudget,
    ): CompositionSearch {
        if (candidates.size < 3) return CompositionSearch()
        val winners = linkedMapOf<CompiledIndexCandidate, Int>()
        candidates.forEach { speciesToRegional ->
            if (!speciesToRegional.completePermutation || !speciesToRegional.publicationEligible) {
                return@forEach
            }
            candidates.forEach { speciesToNational ->
                if (speciesToNational !== speciesToRegional) {
                    candidates.forEach { regionalToNational ->
                        if (regionalToNational !== speciesToRegional &&
                            regionalToNational !== speciesToNational
                        ) {
                            budget.recordWork("species-index composition assignment")?.let {
                                return CompositionSearch(budgetFailure = it)
                            }
                            var matches = 0
                            speciesToRegional.values.indices.forEach { index ->
                                budget.recordWork("species-index composition row")?.let {
                                    return CompositionSearch(budgetFailure = it)
                                }
                                val regionalId = speciesToRegional.values[index]
                                if (regionalId in 1..regionalToNational.values.size &&
                                    regionalToNational.values[regionalId - 1] == speciesToNational.values[index]
                                ) {
                                    matches++
                                }
                            }
                            val count = speciesToRegional.values.size
                            if (matches >= count - MAXIMUM_COMPOSITION_MISMATCHES &&
                                matches.toDouble() / count >= MINIMUM_COMPOSITION_AGREEMENT
                            ) {
                                winners[speciesToRegional] = maxOf(winners[speciesToRegional] ?: 0, matches)
                            }
                        }
                    }
                }
            }
        }
        val rankedWinners = winners.entries
            .sortedByDescending(Map.Entry<CompiledIndexCandidate, Int>::value)
        val first = rankedWinners.firstOrNull() ?: return CompositionSearch()
        val second = rankedWinners.getOrNull(1)
        return CompositionSearch(
            candidate = first.key.takeUnless { second != null && first.value == second.value },
        )
    }

    private fun addCombinesRegisters(instruction: Int, first: Int, second: Int): Boolean {
        val left = (instruction ushr 3) and 0x7
        val right = (instruction ushr 6) and 0x7
        return (left == first && right == second) || (left == second && right == first)
    }

    /**
     * Recognizes the finite caller role used by the Hoenn-order wrapper: callers form a one-based
     * ordinal (`counter + 1`) and immediately consume the returned value as a Pokédex number.
     * Unknown or over-budget call graphs remain eligible. Even when recognized, this weak negative
     * signal may only make the marked winner abstain; it never promotes a competing table.
     */
    private fun hasRegionalOrderConsumerRole(
        rom: RomImage,
        lookupInstructionOffset: Int,
        limits: ResolutionLimits,
    ): Boolean {
        val wrapperOffset = lookupInstructionOffset - INDEXED_LOOKUP_PROLOG_BYTES
        if (!hasIndexedLookupWrapperProlog(rom, wrapperOffset)) return false
        var callers = 0
        var ordinalCallers = 0
        var ordinalDexConsumers = 0
        var callsite = 0
        while (callsite <= rom.size - 4) {
            if (decodeThumbBlTarget(rom, callsite) == wrapperOffset) {
                callers++
                if (callers > limits.maxCompiledReferenceSitesPerCandidate) return false
                if (hasOneBasedOrdinalArgument(rom, callsite)) {
                    ordinalCallers++
                    if (hasImmediateDexNumberConsumer(rom, callsite)) ordinalDexConsumers++
                }
            }
            callsite += 2
        }
        return callers >= MINIMUM_ROLE_PROOF_CALLERS &&
            ordinalCallers == callers &&
            ordinalDexConsumers >= MINIMUM_ROLE_PROOF_CALLERS
    }

    private fun hasIndexedLookupWrapperProlog(rom: RomImage, wrapperOffset: Int): Boolean =
        wrapperOffset in 0..rom.size - INDEXED_LOOKUP_PROLOG_BYTES &&
            rom.u16le(wrapperOffset) and 0xFF00 == 0xB500 &&
            rom.u16le(wrapperOffset + 2) == 0x0400 &&
            rom.u16le(wrapperOffset + 4) == 0x0C01 &&
            rom.u16le(wrapperOffset + 6) == 0x2900

    private fun hasOneBasedOrdinalArgument(rom: RomImage, callsite: Int): Boolean {
        var requiredRegister = 0
        var offset = callsite - 2
        val start = maxOf(0, callsite - ORDINAL_ARGUMENT_WINDOW_BYTES)
        while (offset >= start) {
            val instruction = rom.u16le(offset)
            val shiftOpcode = instruction and 0xF800
            if (shiftOpcode == 0x0000 || shiftOpcode == 0x0800) {
                val destination = instruction and 0x7
                if (destination == requiredRegister) {
                    requiredRegister = (instruction ushr 3) and 0x7
                }
                offset -= 2
                continue
            }
            if (instruction and 0xFE00 == 0x1C00) {
                val destination = instruction and 0x7
                if (destination == requiredRegister) {
                    val source = (instruction ushr 3) and 0x7
                    val immediate = (instruction ushr 6) and 0x7
                    if (immediate == 1) return true
                    if (immediate == 0) requiredRegister = source else return false
                }
                offset -= 2
                continue
            }
            if (instruction and 0xF800 == 0x3000) {
                val destination = (instruction ushr 8) and 0x7
                if (destination == requiredRegister) return (instruction and 0xFF) == 1
            }
            offset -= 2
        }
        return false
    }

    private fun hasImmediateDexNumberConsumer(rom: RomImage, callsite: Int): Boolean {
        val afterCall = callsite + 4
        if (afterCall > rom.size - 10) return false
        return rom.u16le(afterCall) == 0x0400 &&
            rom.u16le(afterCall + 2) == 0x0C00 &&
            rom.u16le(afterCall + 4) and 0xFF00 == 0x2100 &&
            decodeThumbBlTarget(rom, afterCall + 6) != null
    }

    private fun decodeThumbBlTarget(rom: RomImage, instructionOffset: Int): Int? {
        if (instructionOffset !in 0..rom.size - 4) return null
        val high = rom.u16le(instructionOffset)
        val low = rom.u16le(instructionOffset + 2)
        if (high and 0xF800 != 0xF000 || low and 0xF800 != 0xF800) return null
        var displacement = ((high and 0x7FF) shl 12) or ((low and 0x7FF) shl 1)
        if (displacement and 0x400000 != 0) displacement -= 0x800000
        return instructionOffset + 4 + displacement
    }

    private fun compiledIndexCandidateMetrics(
        rom: RomImage,
        layout: ResolvedRomLayout,
        offset: Int,
        count: Int,
        summary: U16ValueSummary,
    ): CompiledIndexCandidateMetrics? {
        val completePermutation =
            summary.positiveCount == count &&
                summary.distinctCount == count &&
                summary.maximum == count
        val oneDefectCompositionSupport =
            summary.positiveCount == count &&
                summary.distinctCount == count - 1 &&
                summary.maximum == count
        val descriptionCount = layout.tables.descriptions?.count
        val descriptionCoverage = if (completePermutation && descriptionCount != null && descriptionCount > 1) {
            // An exact 1..N permutation necessarily covers every installed description ID even
            // when the ROM intentionally has fewer description rows than physical species rows.
            minOf(count, descriptionCount - 1).toDouble() / (descriptionCount - 1)
        } else if (oneDefectCompositionSupport) {
            summary.distinctCount.toDouble() / count
        } else if (descriptionCount != null && descriptionCount > 1) {
            if (summary.maximum >= descriptionCount) return null
            summary.distinctCount.toDouble() / (descriptionCount - 1)
        } else {
            summary.distinctCount.toDouble() / summary.maximum
        }
        val nameCoverage = mappedNameCoverage(rom, layout, offset, count)
        val publicationEligible = !oneDefectCompositionSupport &&
            (completePermutation || descriptionCoverage >= MINIMUM_COMPILED_DEX_DENSITY) &&
            nameCoverage >= MINIMUM_MAPPED_NAME_COVERAGE
        return CompiledIndexCandidateMetrics(
            completePermutation = completePermutation,
            publicationEligible = publicationEligible,
            compositionEligible = completePermutation || oneDefectCompositionSupport,
            descriptionCoverage = descriptionCoverage,
            mappedNameCoverage = nameCoverage,
        ).takeIf {
            (it.publicationEligible || it.compositionEligible) &&
                it.mappedNameCoverage >= MINIMUM_MAPPED_NAME_COVERAGE
        }
    }

    private fun mappedNameCoverage(
        rom: RomImage,
        layout: ResolvedRomLayout,
        tableOffset: Int,
        count: Int,
    ): Double {
        val names = layout.tables.speciesNames
            ?.takeIf { !it.variableLength && !it.valuesArePointers }
            ?: return 0.0
        val stride = names.stride ?: names.recordSize
        val codec = PokemonTextCodec.gbaEnglish
        var mapped = 0
        var named = 0
        for (index in 0 until count) {
            if (rom.u16le(tableOffset + index * 2) <= 0) continue
            mapped++
            val nameIndex = index + 1
            val nameOffset = names.offset.toLong() + nameIndex.toLong() * stride
            if (nameIndex < names.count && nameOffset in 0..(rom.size - names.recordSize).toLong()) {
                val decoded = codec.decode(rom.slice(nameOffset.toInt(), names.recordSize))
                if (decoded.any(Char::isLetterOrDigit)) named++
            }
        }
        return named.toDouble() / mapped.coerceAtLeast(1)
    }

    /**
     * Expanded Gen III projects commonly replace the historical Hoenn-era index layout with a
     * complete internal-species -> Pokédex-number permutation. Unlike the stock table, its first
     * live entry does not have to be Dex #1, so it cannot be found from a 1,2 prefix signature.
     */
    private fun findCompleteGen3Permutation(
        rom: RomImage,
        speciesCount: Int,
        budget: SpeciesDiscoveryBudget,
    ): CompletePermutationSearch {
        if (speciesCount <= 1 || speciesCount.toLong() * 2 > rom.size) return CompletePermutationSearch()
        val byteLength = speciesCount * 2
        var selected: IntArray? = null
        for (offset in 0..rom.size - byteLength step 2) {
            if (rom.u16le(offset) != 0) continue
            budget.recordRoot(offset)?.let { return CompletePermutationSearch(budgetFailure = it) }
            budget.recordWork("complete permutation probe")?.let {
                return CompletePermutationSearch(budgetFailure = it)
            }
            val seen = BooleanArray(speciesCount)
            var compatible = true
            for (index in 0 until speciesCount) {
                val value = rom.u16le(offset + index * 2)
                if (value !in 0 until speciesCount || seen[value]) {
                    compatible = false
                    break
                }
                seen[value] = true
            }
            if (!compatible || selected != null && matchesValuesAt(rom, offset, selected)) continue
            budget.recordCandidate()?.let { return CompletePermutationSearch(budgetFailure = it) }
            val values = readU16Values(rom, offset, speciesCount)
            if (selected != null) return CompletePermutationSearch(ambiguous = true)
            selected = values
        }
        return CompletePermutationSearch(values = selected)
    }

    private fun namedPositiveCoverage(
        rom: RomImage,
        layout: ResolvedRomLayout,
        tableOffset: Int,
        count: Int,
    ): Double {
        val names = layout.tables.speciesNames
            ?.takeIf { !it.variableLength && !it.valuesArePointers }
            ?: return 0.0
        val stride = names.stride ?: names.recordSize
        val codec = PokemonTextCodec.gbaEnglish
        var named = 0
        var namedPositive = 0
        for (index in 0 until count) {
            val nameIndex = index + 1
            if (nameIndex >= names.count) continue
            val nameOffset = names.offset.toLong() + nameIndex.toLong() * stride
            if (nameOffset !in 0..(rom.size - names.recordSize).toLong()) continue
            val decoded = codec.decode(rom.slice(nameOffset.toInt(), names.recordSize))
            if (decoded.any(Char::isLetterOrDigit)) {
                named++
                if (rom.u16le(tableOffset + index * 2) > 0) namedPositive++
            }
        }
        return namedPositive.toDouble() / named.coerceAtLeast(1)
    }

    private fun summarizeU16Values(
        rom: RomImage,
        offset: Int,
        count: Int,
        maximumPlausible: Int,
    ): U16ValueSummary? {
        val distinct = linkedSetOf<Int>()
        var positiveCount = 0
        var maximum = 0
        var identityPrefix = 0
        var prefixOpen = true
        for (index in 0 until count) {
            val value = rom.u16le(offset + index * 2)
            if (value !in 0..maximumPlausible) return null
            if (prefixOpen && value == index + 1) identityPrefix++ else prefixOpen = false
            if (value > 0) {
                positiveCount++
                distinct += value
                maximum = maxOf(maximum, value)
            }
        }
        if (positiveCount == 0) return null
        return U16ValueSummary(
            positiveCount = positiveCount,
            distinctCount = distinct.size,
            maximum = maximum,
            identityPrefix = identityPrefix,
            canonicalBoundary = count >= 277 && rom.u16le(offset + 276 * 2) == 252,
        )
    }

    private fun readU16Values(rom: RomImage, offset: Int, count: Int): IntArray =
        IntArray(count) { index -> rom.u16le(offset + index * 2) }

    private fun matchesValuesAt(rom: RomImage, offset: Int, values: IntArray): Boolean {
        if (offset.toLong() + values.size.toLong() * 2 > rom.size) return false
        return values.indices.all { index -> rom.u16le(offset + index * 2) == values[index] }
    }

    private fun unresolvedGen3Fallback(speciesCount: Int): Map<Int, Int> =
        (0 until speciesCount).associateWith { 0 }

    private fun indexedGen3Values(values: IntArray): Map<Int, Int> = buildMap {
        put(0, 0)
        values.forEachIndexed { index, dex -> put(index + 1, dex) }
    }

    private fun identity(first: Int, last: Int): Map<Int, Int> = (first..last).associateWith { it }

    private data class Gen3IndexCandidate(
        val prefix: Int,
        val distinctRatio: Double,
        val canonicalBoundary: Int,
        val compiledReferences: Int,
        val values: IntArray,
    ) {
        fun sameStrengthAs(other: Gen3IndexCandidate): Boolean =
            canonicalBoundary == other.canonicalBoundary &&
                distinctRatio == other.distinctRatio &&
                prefix == other.prefix &&
                compiledReferences == other.compiledReferences &&
                values.count { it > 0 } == other.values.count { it > 0 }
    }

    private data class CompiledIndexCandidate(
        val offset: Int,
        val values: IntArray,
        val regionalOrderConsumerRole: Boolean,
        val completePermutation: Boolean,
        val publicationEligible: Boolean,
        val compositionEligible: Boolean,
        val descriptionCoverage: Double,
        val mappedNameCoverage: Double,
        val positiveCount: Int,
        val distinctDexCount: Int,
    ) {
        fun sameStrengthAs(other: CompiledIndexCandidate): Boolean =
            completePermutation == other.completePermutation &&
                descriptionCoverage == other.descriptionCoverage &&
                mappedNameCoverage == other.mappedNameCoverage &&
                positiveCount == other.positiveCount &&
                distinctDexCount == other.distinctDexCount
    }

    private data class CompiledIndexCandidateMetrics(
        val completePermutation: Boolean,
        val publicationEligible: Boolean,
        val compositionEligible: Boolean,
        val descriptionCoverage: Double,
        val mappedNameCoverage: Double,
    )

    private data class CompositionSearch(
        val candidate: CompiledIndexCandidate? = null,
        val budgetFailure: SpeciesBudgetFailure? = null,
    )

    private data class CompiledIndexSearch(
        val values: IntArray? = null,
        val ambiguous: Boolean = false,
        val budgetFailure: SpeciesBudgetFailure? = null,
        val evidenceObserved: Boolean = false,
    )

    private data class CompletePermutationSearch(
        val values: IntArray? = null,
        val ambiguous: Boolean = false,
        val budgetFailure: SpeciesBudgetFailure? = null,
    )

    private data class U16ValueSummary(
        val positiveCount: Int,
        val distinctCount: Int,
        val maximum: Int,
        val identityPrefix: Int,
        val canonicalBoundary: Boolean,
    )

    private class SpeciesDiscoveryBudget(val limits: ResolutionLimits) {
        private val roots = linkedSetOf<Int>()
        private var work = 0L
        private var candidates = 0L

        fun recordRoot(root: Int): SpeciesBudgetFailure? {
            if (root in roots) return null
            if (roots.size == limits.maxProbeRootsPerDataset) {
                val observed = roots.size + 1L
                return SpeciesBudgetFailure(
                    BudgetKind.PROBE_ROOTS,
                    observed,
                    limits.maxProbeRootsPerDataset.toLong(),
                    "Gen III species-index probe-root budget exceeded " +
                        "($observed > ${limits.maxProbeRootsPerDataset})",
                )
            }
            roots += root
            return null
        }

        fun recordWork(activity: String): SpeciesBudgetFailure? {
            if (work == limits.maxProbeWorkPerDataset.toLong()) {
                val observed = work + 1L
                return SpeciesBudgetFailure(
                    BudgetKind.PROBE_WORK,
                    observed,
                    limits.maxProbeWorkPerDataset.toLong(),
                    "Gen III species-index probe-work budget exceeded while evaluating $activity " +
                        "($observed > ${limits.maxProbeWorkPerDataset})",
                )
            }
            work++
            return null
        }

        fun recordCandidate(): SpeciesBudgetFailure? {
            if (candidates == limits.maxCandidatesPerDataset.toLong()) {
                val observed = candidates + 1L
                return SpeciesBudgetFailure(
                    BudgetKind.CANDIDATES,
                    observed,
                    limits.maxCandidatesPerDataset.toLong(),
                    "Gen III species-index candidate budget exceeded " +
                        "($observed > ${limits.maxCandidatesPerDataset})",
                )
            }
            candidates++
            return null
        }
    }

    private data class SpeciesBudgetFailure(
        val kind: BudgetKind,
        val observed: Long,
        val limit: Long,
        val reason: String,
    ) {
        fun toResolution(fallback: Map<Int, Int>) = SpeciesIndexResolution.BudgetExceeded(
            fallback,
            kind,
            observed,
            limit,
            reason,
        )
    }

    private const val CANONICAL_IDENTITY_PREFIX = 251
    private const val MINIMUM_FORM_MAP_DISTINCT_RATIO = 0.50
    private const val MINIMUM_REORDERED_MAP_DISTINCT_RATIO = 0.95
    private const val MINIMUM_NAMED_POSITIVE_COVERAGE = 0.90
    private const val MINIMUM_COMPILED_DEX_DENSITY = 0.90
    private const val MINIMUM_MAPPED_NAME_COVERAGE = 0.90
    private const val MINIMUM_COMPOSITION_AGREEMENT = 0.995
    private const val MAXIMUM_COMPOSITION_MISMATCHES = 1
    private const val SMALL_LEGACY_IDENTITY_REASON =
        "small legacy Gen III layout has no species-index evidence; " +
            "identity values are compatibility-only and not authoritative"
    private const val INDEXED_LOOKUP_PROLOG_BYTES = 10
    private const val ORDINAL_ARGUMENT_WINDOW_BYTES = 16
    private const val MINIMUM_ROLE_PROOF_CALLERS = 2
    private const val THUMB_LITERAL_LOAD_MASK = 0xF800
    private const val THUMB_LITERAL_LOAD_OPCODE = 0x4800
    private const val GBA_ROM_BASE = 0x08000000L
}
