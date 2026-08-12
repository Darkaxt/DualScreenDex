package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Gen3LearnsetEncoding
import com.enrpau.dualscreendex.parser.model.GbaCompiledReferenceIndex
import com.enrpau.dualscreendex.parser.model.Gen3LearnsetSelectorEvidence
import com.enrpau.dualscreendex.parser.model.Gen3LearnsetTableLayout
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.validate.Gen3DescriptionPointerRecovery
import com.enrpau.dualscreendex.parser.validate.Gen3EvolutionValidation
import com.enrpau.dualscreendex.parser.validate.Gen3PackedLearnsetDecoder
import com.enrpau.dualscreendex.parser.validate.PokemonDatasetValidators
import kotlin.math.abs

data class Gen3LearnsetResolution(
    val evidence: ValidationEvidence,
    val tables: List<Gen3LearnsetTableLayout>,
    val selector: Gen3LearnsetSelectorEvidence? = null,
)

object DatasetResolvers {
    fun reconciledMoveCount(inferredNameCount: Int?, moveData: ValidationEvidence): Int? = when {
        !moveData.compatible || moveData.totalRecords <= 0 -> inferredNameCount
        inferredNameCount == null -> moveData.totalRecords
        else -> minOf(inferredNameCount, moveData.totalRecords)
    }

    private fun RomAnalysisSession.requireGbaReferenceIndex(): GbaReferenceIndex =
        requireNotNull(gbaReferenceIndex) {
            "Gen 3 dataset resolution requires a GBA analysis session"
        }

    private fun RomAnalysisSession.legacyGbaReferenceCounts(): GbaCompiledReferenceIndex =
        requireGbaReferenceIndex().asLegacyCounts()

    private fun standaloneGbaReferenceCounts(rom: RomImage): GbaCompiledReferenceIndex =
        RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, ""),
        ).legacyGbaReferenceCounts()

    fun gen3Descriptions(
        session: RomAnalysisSession,
        speciesCount: Int,
        inherited: TableLayout?,
        codec: PokemonTextCodec,
    ): ValidationEvidence = resolveGen3Descriptions(
        rom = session.rom,
        speciesCount = speciesCount,
        inherited = inherited,
        codec = codec,
        referenceCounts = session.requireGbaReferenceIndex().counts,
        referenceSites = session.requireGbaReferenceIndex(),
        limits = session.limits,
    )

    fun gen3Descriptions(
        rom: RomImage,
        speciesCount: Int,
        inherited: TableLayout?,
        codec: PokemonTextCodec,
        referenceIndex: GbaCompiledReferenceIndex? = null,
    ): ValidationEvidence {
        val discoveredReferences = if (referenceIndex == null) {
            RomAnalysisSession(rom, RomHeader(Platform.GBA, "")).requireGbaReferenceIndex()
        } else {
            null
        }
        return resolveGen3Descriptions(
            rom = rom,
            speciesCount = speciesCount,
            inherited = inherited,
            codec = codec,
            referenceCounts = referenceIndex?.counts ?: requireNotNull(discoveredReferences).counts,
            referenceSites = discoveredReferences,
            referenceOverflowReason = referenceIndex?.overflowReason ?: discoveredReferences?.overflowReason,
            limits = ResolutionLimits(),
        )
    }

    private fun resolveGen3Descriptions(
        rom: RomImage,
        speciesCount: Int,
        inherited: TableLayout?,
        codec: PokemonTextCodec,
        referenceCounts: Map<Int, Int>,
        referenceSites: GbaReferenceIndex?,
        referenceOverflowReason: String? = referenceSites?.overflowReason,
        limits: ResolutionLimits,
    ): ValidationEvidence {
        if (speciesCount <= 0 || DESCRIPTION_LAYOUTS.none { speciesCount.toLong() * it.recordSize <= rom.size.toLong() }) {
            return missing("Gen 3 Pokédex description count cannot fit in the ROM")
        }
        referenceOverflowReason?.let { return missing(it, reviewRecommended = true) }
        val candidates = mutableListOf<DescriptionCandidate>()
        val discoveryBudget = LegacyDescriptionDiscoveryBudget(limits)
        inherited?.let { layout ->
            resolvedDescriptionCoverage(rom, speciesCount, layout, codec)?.let { evidence ->
                discoveryBudget.recordCandidate()?.let {
                    return missing(it, reviewRecommended = true)
                }
                candidates += DescriptionCandidate(
                    evidence = evidence,
                    referenceCount = verifiedDescriptionReferenceCount(
                        rom,
                        referenceSites?.target(evidence.offset ?: -1),
                        evidence.recordSize,
                    ),
                    inherited = true,
                )
            }
        }
        val referenced = boundedReferencedDescriptionCandidates(
            rom = rom,
            speciesCount = speciesCount,
            codec = codec,
            referenceCounts = referenceCounts,
            referenceSites = referenceSites,
            discoveryBudget = discoveryBudget,
        )
        referenced.overflowReason?.let { return missing(it, reviewRecommended = true) }
        candidates += referenced.candidates
        DESCRIPTION_LAYOUTS.forEach { layout ->
            DESCRIPTION_ANCHORS.forEach { anchor ->
                patternOffsets(rom, anchor).forEach { seedOffset ->
                    val maximumAnchorIndex = minOf(speciesCount - 1, seedOffset / layout.recordSize)
                    for (anchorIndex in 1..maximumAnchorIndex) {
                        val tableOffset = seedOffset - anchorIndex * layout.recordSize
                        if (tableOffset % 4 != 0 || !validDescriptionStart(rom, tableOffset, codec)) continue
                        discoveryBudget.recordRoot(tableOffset)?.let {
                            return missing(it, reviewRecommended = true)
                        }
                        discoveryBudget.recordWork("structural anchor validation")?.let {
                            return missing(it, reviewRecommended = true)
                        }
                        val candidateLayout = TableLayout(
                            tableOffset,
                            speciesCount,
                            layout.recordSize,
                            pointerOffsets = layout.pointerOffsets,
                        )
                        val prefix = validateDescription(rom, anchorIndex + 1, candidateLayout, codec)
                        val anchorRecord = validateDescription(
                            rom,
                            1,
                            candidateLayout.copy(offset = seedOffset, count = 1),
                            codec,
                        )
                        if (!prefix.compatible || !anchorRecord.compatible) continue

                        val evidence = resolvedDescriptionCoverage(
                            rom,
                            speciesCount,
                            candidateLayout,
                            codec,
                        )
                        evidence?.takeIf { anchorIndex < it.totalRecords }?.let {
                            if (candidates.any { candidate ->
                                    candidate.evidence.offset == it.offset &&
                                        candidate.evidence.recordSize == it.recordSize
                                }
                            ) {
                                return@let
                            }
                            discoveryBudget.recordCandidate()?.let { reason ->
                                return missing(reason, reviewRecommended = true)
                            }
                            candidates += DescriptionCandidate(
                                evidence = it,
                                referenceCount = verifiedDescriptionReferenceCount(
                                    rom,
                                    referenceSites?.target(it.offset ?: -1),
                                    it.recordSize,
                                ),
                                inherited = false,
                            )
                        }
                    }
                }
            }
        }
        return chooseDescriptions(candidates, speciesCount)
    }

    private fun boundedReferencedDescriptionCandidates(
        rom: RomImage,
        speciesCount: Int,
        codec: PokemonTextCodec,
        referenceCounts: Map<Int, Int>,
        referenceSites: GbaReferenceIndex?,
        discoveryBudget: LegacyDescriptionDiscoveryBudget,
    ): ReferencedDescriptionCandidates {
        val candidates = mutableListOf<DescriptionCandidate>()
        referenceCounts.forEach { (tableOffset, _) ->
            if (tableOffset % 4 != 0 || !validDescriptionStart(rom, tableOffset, codec)) return@forEach
            discoveryBudget.recordRoot(tableOffset)?.let {
                return ReferencedDescriptionCandidates(candidates, it)
            }
            DESCRIPTION_LAYOUTS.forEach { shape ->
                discoveryBudget.recordWork("compiled-reference layout validation")?.let {
                    return ReferencedDescriptionCandidates(candidates, it)
                }
                val extent = tableOffset.toLong() + speciesCount.toLong() * shape.recordSize
                if (extent !in 0..rom.size.toLong()) return@forEach
                val layout = TableLayout(
                    offset = tableOffset,
                    count = speciesCount,
                    recordSize = shape.recordSize,
                    pointerOffsets = shape.pointerOffsets,
                )
                resolvedDescriptionCoverage(rom, speciesCount, layout, codec)?.let { evidence ->
                    discoveryBudget.recordCandidate()?.let {
                        return ReferencedDescriptionCandidates(candidates, it)
                    }
                    candidates += DescriptionCandidate(
                        evidence = evidence,
                        referenceCount = verifiedDescriptionReferenceCount(
                            rom,
                            referenceSites?.target(tableOffset),
                            shape.recordSize,
                        ),
                        inherited = false,
                    )
                }
            }
        }
        return ReferencedDescriptionCandidates(candidates)
    }

    private fun chooseDescriptions(
        candidates: List<DescriptionCandidate>,
        speciesCount: Int,
    ): ValidationEvidence {
        val credible = candidates.filter { candidate ->
            hasCredibleDescriptionCoverage(candidate.evidence, speciesCount)
        }
        if (credible.isEmpty() && candidates.isNotEmpty()) {
            return missing(
                "Gen 3 Pokédex description candidates do not cover enough of the species semantic domain",
                reviewRecommended = true,
            )
        }
        val unique = credible
            .groupBy { it.evidence.offset to it.evidence.recordSize }
            .values
            .map { duplicates ->
                duplicates.maxWithOrNull(
                    compareBy<DescriptionCandidate> { it.referenceCount }
                        .thenBy { it.inherited }
                        .thenBy { it.evidence.validRecords }
                        .thenBy { it.evidence.confidence },
                ) ?: error("description candidate group cannot be empty")
            }
        if (unique.isEmpty()) {
            return missing("Gen 3 Pokédex description table not resolved by structural validation")
        }
        val ranked = unique.sortedWith(
            compareByDescending<DescriptionCandidate> { it.referenceCount }
                .thenByDescending { it.inherited }
                .thenByDescending { it.evidence.validRecords }
                .thenByDescending { it.evidence.confidence },
        )
        val first = ranked.first()
        val second = ranked.getOrNull(1)
        if (second != null && first.evidence.offset != second.evidence.offset && first.sameStrengthAs(second)) {
            return missing(
                "Gen 3 Pokédex description table has conflicting compiled-referenced structural candidates",
                ambiguous = true,
                reviewRecommended = true,
            )
        }
        return first.evidence
    }

    private fun hasCredibleDescriptionCoverage(
        evidence: ValidationEvidence,
        speciesCount: Int,
    ): Boolean {
        val minimumRecords = maxOf(
            MINIMUM_DESCRIPTION_PREFIX_RECORDS,
            speciesCount / 2,
        ).coerceAtMost(speciesCount)
        return evidence.totalRecords >= minimumRecords
    }

    private fun verifiedDescriptionReferenceCount(
        rom: RomImage,
        references: GbaTargetReferenceEvidence?,
        recordSize: Int?,
    ): Int {
        if (references == null || !references.siteEvidenceAvailable || recordSize !in setOf(32, 36)) {
            return 0
        }
        return references.instructionSites.count { instructionSite ->
            hasCompiledDescriptionRowAddressFormation(rom, instructionSite, requireNotNull(recordSize))
        }
    }

    /**
     * Recognizes only the bounded straight-line address shapes emitted for 32- and 36-byte
     * Pokédex rows. This deliberately does not interpret branches or general Thumb data flow.
     */
    private fun hasCompiledDescriptionRowAddressFormation(
        rom: RomImage,
        instructionSite: Int,
        recordSize: Int,
    ): Boolean {
        if (instructionSite !in 0..rom.size - 2) return false
        val literalLoad = rom.u16le(instructionSite)
        if (literalLoad and THUMB_LITERAL_LOAD_MASK != THUMB_LITERAL_LOAD_OPCODE) return false
        val baseRegister = (literalLoad ushr 8) and 0x7
        val start = maxOf(0, instructionSite - DESCRIPTION_CONSUMER_WINDOW_BYTES)
        val end = minOf(rom.size - 2, instructionSite + DESCRIPTION_CONSUMER_WINDOW_BYTES)
        return when (recordSize) {
            32 -> (start..end step 2).any { shiftOffset ->
                val shift = thumbImmediateShift(rom.u16le(shiftOffset), amount = 5) ?: return@any false
                val addStart = maxOf(instructionSite, shiftOffset) + 2
                if (addStart > end) return@any false
                (addStart..end step 2).any { addOffset ->
                    thumbAddCombines(
                        instruction = rom.u16le(addOffset),
                        first = baseRegister,
                        second = shift.first,
                    )
                }
            }

            36 -> (start..end - 4 step 2).any { scaleOffset ->
                val timesEight = thumbImmediateShift(rom.u16le(scaleOffset), amount = 3)
                    ?: return@any false
                val addNine = rom.u16le(scaleOffset + 2)
                if (!thumbAddCombines(addNine, timesEight.first, timesEight.second, timesEight.first)) {
                    return@any false
                }
                val timesThirtySix = thumbImmediateShift(rom.u16le(scaleOffset + 4), amount = 2)
                    ?: return@any false
                if (timesThirtySix.first != timesEight.first || timesThirtySix.second != timesEight.first) {
                    return@any false
                }
                val addStart = maxOf(instructionSite, scaleOffset + 4) + 2
                if (addStart > end) return@any false
                (addStart..end step 2).any { addOffset ->
                    thumbAddCombines(
                        instruction = rom.u16le(addOffset),
                        first = baseRegister,
                        second = timesEight.first,
                    )
                }
            }

            else -> false
        }
    }

    private fun thumbImmediateShift(instruction: Int, amount: Int): Pair<Int, Int>? {
        if (instruction and 0xF800 != 0 || (instruction ushr 6) and 0x1F != amount) return null
        return (instruction and 0x7) to ((instruction ushr 3) and 0x7)
    }

    private fun thumbAddCombines(
        instruction: Int,
        first: Int,
        second: Int,
        destination: Int? = null,
    ): Boolean {
        if (instruction and 0xFE00 != 0x1800 || destination != null && instruction and 0x7 != destination) {
            return false
        }
        val left = (instruction ushr 3) and 0x7
        val right = (instruction ushr 6) and 0x7
        return left == first && right == second || left == second && right == first
    }

    private data class DescriptionCandidate(
        val evidence: ValidationEvidence,
        val referenceCount: Int,
        val inherited: Boolean,
    ) {
        fun sameStrengthAs(other: DescriptionCandidate): Boolean =
            referenceCount == other.referenceCount &&
                evidence.validRecords == other.evidence.validRecords &&
                evidence.confidence == other.evidence.confidence
    }

    private data class ReferencedDescriptionCandidates(
        val candidates: List<DescriptionCandidate>,
        val overflowReason: String? = null,
    )

    private class LegacyDescriptionDiscoveryBudget(private val limits: ResolutionLimits) {
        private val roots = linkedSetOf<Int>()
        private val candidateLimit = minOf(
            MAX_REFERENCED_DESCRIPTION_CANDIDATES,
            limits.maxCandidatesPerDataset,
        )
        private var work = 0L
        private var candidates = 0

        fun recordRoot(root: Int): String? {
            if (root in roots) return null
            if (roots.size == limits.maxProbeRootsPerDataset) {
                val observed = roots.size + 1L
                return "Gen 3 Pokédex description probe-root budget exceeded " +
                    "($observed > ${limits.maxProbeRootsPerDataset}); automatic resolution requires review"
            }
            roots += root
            return null
        }

        fun recordWork(activity: String): String? {
            if (work == limits.maxProbeWorkPerDataset.toLong()) {
                val observed = work + 1L
                return "Gen 3 Pokédex description probe-work budget exceeded while evaluating $activity " +
                    "($observed > ${limits.maxProbeWorkPerDataset}); automatic resolution requires review"
            }
            work++
            return null
        }

        fun recordCandidate(): String? {
            if (candidates < candidateLimit) {
                candidates++
                return null
            }
            val observed = candidates + 1
            return if (candidateLimit == MAX_REFERENCED_DESCRIPTION_CANDIDATES) {
                "Gen 3 Pokédex description candidate budget exceeded " +
                    "($MAX_REFERENCED_DESCRIPTION_CANDIDATES); automatic resolution requires review"
            } else {
                "Gen 3 Pokédex description candidate budget exceeded " +
                    "($observed > $candidateLimit); automatic resolution requires review"
            }
        }
    }

    private fun patternOffsets(rom: RomImage, pattern: ByteArray): Sequence<Int> = sequence {
        if (pattern.isEmpty() || pattern.size > rom.size) return@sequence
        var offset = 0
        val last = rom.size - pattern.size
        while (offset <= last) {
            var matches = true
            for (index in pattern.indices) {
                if (rom.u8(offset + index) != (pattern[index].toInt() and 0xFF)) {
                    matches = false
                    break
                }
            }
            if (matches) yield(offset)
            offset++
        }
    }

    private fun resolvedDescriptionCoverage(
        rom: RomImage,
        maximumCount: Int,
        layout: TableLayout,
        codec: PokemonTextCodec,
    ): ValidationEvidence? {
        val full = validateDescription(rom, maximumCount, layout, codec)
        val selected = if (full.compatible && full.validRecords == full.totalRecords) {
            full
        } else {
            val prefix = inferDescription(rom, maximumCount, layout, codec)
            if (prefix.compatible && prefix.totalRecords < maximumCount && prefix.confidence > full.confidence) {
                prefix.copy(
                    reasons = prefix.reasons +
                        "trimmed adjacent non-description data after a structurally stronger Gen 3 Pokédex prefix",
                )
            } else {
                full.takeIf { it.compatible } ?: prefix.takeIf { it.compatible }
            }
        } ?: return null
        val recovered = Gen3DescriptionPointerRecovery.recover(
            rom,
            layout.copy(count = selected.totalRecords),
            codec,
        ).size
        if (recovered == 0) return selected
        val valid = minOf(selected.totalRecords, selected.validRecords + recovered)
        return selected.copy(
            validRecords = valid,
            confidence = valid.toDouble() / selected.totalRecords.coerceAtLeast(1),
            coveredRecords = minOf(
                selected.totalRecords,
                (selected.coveredRecords ?: selected.validRecords) + recovered,
            ),
            incompleteRecords = (selected.totalRecords - valid).coerceAtLeast(0),
            reviewRecommended = true,
            reasons = selected.reasons +
                "recovered $recovered unique off-by-one description pointer(s) within referenced text boundaries",
        )
    }

    fun gen3Evolutions(
        session: RomAnalysisSession,
        speciesCount: Int,
        inherited: TableLayout?,
    ): ValidationEvidence = gen3Evolutions(
        rom = session.rom,
        speciesCount = speciesCount,
        inherited = inherited,
        referenceIndex = session.legacyGbaReferenceCounts(),
    )

    fun gen3Evolutions(
        rom: RomImage,
        speciesCount: Int,
        inherited: TableLayout?,
        referenceIndex: GbaCompiledReferenceIndex = standaloneGbaReferenceCounts(rom),
    ): ValidationEvidence {
        val minimumExtent = speciesCount.toLong() * MIN_EVOLUTION_SLOTS * (EVOLUTION_ELEMENT_SIZES.minOrNull() ?: 0)
        if (speciesCount <= 0 || minimumExtent > rom.size.toLong()) {
            return missing("Gen 3 evolution species count cannot fit in the ROM")
        }
        referenceIndex.overflowReason?.let { return missing(it, reviewRecommended = true) }
        val inheritedValidation = inherited?.let { validateEvolutionDetailed(rom, speciesCount, it) }
        val inheritedCandidate = inheritedValidation?.takeIf { it.evidence.compatible }?.let {
            EvolutionCandidate(it, referenceCount = referenceIndex.counts[it.evidence.offset] ?: 0, inherited = true)
        }

        val referenced = boundedReferencedEvolutionCandidates(rom, speciesCount, referenceIndex.counts)
        referenced.overflowReason?.let { return missing(it, reviewRecommended = true) }
        val referencedCandidates = referenced.candidates.mapNotNull { candidate ->
            val validation = PokemonDatasetValidators.gen3EvolutionValidation(
                rom = rom,
                offset = candidate.tableOffset,
                speciesCount = speciesCount,
                slotsPerSpecies = candidate.slotsPerSpecies,
                recordSize = candidate.elementSize,
            )
            validation.takeIf { it.evidence.compatible }?.let {
                EvolutionCandidate(it, candidate.referenceCount, inherited = false)
            }
        }
        if (referencedCandidates.isNotEmpty()) {
            return chooseEvolutions(listOfNotNull(inheritedCandidate) + referencedCandidates)
        }

        val candidates = mutableListOf<ValidationEvidence>()
        val levelEvolutionMethods = rom.findAll(byteArrayOf(4, 0))
        for (recordSize in listOf(8, 6)) {
            for (slots in listOf(5, 8, 16, 32)) {
                val stride = slots * recordSize
                levelEvolutionMethods.asSequence().map { firstSpeciesOffset -> firstSpeciesOffset - stride }
                    .filter { tableOffset -> tableOffset >= 0 && tableOffset % 2 == 0 }
                    .filter { tableOffset ->
                        val first = tableOffset + stride
                        val second = tableOffset + stride * 2
                        second + 6 <= rom.size &&
                            rom.u16le(first + 2) in 1..100 && rom.u16le(first + 4) == 2 &&
                            rom.u16le(second) == 4 && rom.u16le(second + 2) in 1..100 && rom.u16le(second + 4) == 3
                    }
                    .distinct()
                    .forEach { tableOffset ->
                    validateEvolution(
                        rom,
                        speciesCount,
                        TableLayout(tableOffset, speciesCount, stride, elementSize = recordSize),
                    ).takeIf { it.compatible }?.let(candidates::add)
                    }
            }
        }
        inheritedCandidate?.validation?.evidence?.let(candidates::add)
        return choose(candidates, inherited?.offset, "Gen 3 evolution table", coverageFirst = true)
    }

    private fun boundedReferencedEvolutionCandidates(
        rom: RomImage,
        speciesCount: Int,
        referenceCounts: Map<Int, Int>,
    ): ReferencedEvolutionCandidates {
        if (speciesCount <= 0) return ReferencedEvolutionCandidates(emptyList())

        val candidates = mutableListOf<ReferencedEvolutionCandidate>()
        var eligibleShapes = 0
        referenceCounts.forEach { (tableOffset, rootReferences) ->
            if (tableOffset % 2 != 0) return@forEach
            for (elementSize in EVOLUTION_ELEMENT_SIZES) {
                for (slots in MIN_EVOLUTION_SLOTS..MAX_EVOLUTION_SLOTS) {
                    val stride = elementSize.toLong() * slots
                    val end = tableOffset.toLong() + speciesCount.toLong() * stride
                    if (end !in 0..rom.size.toLong()) continue
                    val endOffset = end.toInt()
                    val endReferences = if (endOffset == rom.size) {
                        0
                    } else {
                        referenceCounts[endOffset] ?: continue
                    }
                    eligibleShapes++
                    if (eligibleShapes > MAX_REFERENCED_EVOLUTION_PREFILTER_SHAPES) {
                        return ReferencedEvolutionCandidates(
                            candidates,
                            overflowReason = "Gen 3 evolution table prefilter shape budget exceeded " +
                                "($MAX_REFERENCED_EVOLUTION_PREFILTER_SHAPES); " +
                                "automatic resolution requires review",
                        )
                    }
                    if (!looksLikeEvolutionTableSample(
                            rom, tableOffset, speciesCount, slots, elementSize,
                        )
                    ) continue
                    if (candidates.size >= MAX_REFERENCED_EVOLUTION_CANDIDATES) {
                        return ReferencedEvolutionCandidates(
                            candidates,
                            overflowReason = "Gen 3 evolution table candidate budget exceeded " +
                                "($MAX_REFERENCED_EVOLUTION_CANDIDATES); " +
                                "automatic resolution requires review",
                        )
                    }
                    candidates += ReferencedEvolutionCandidate(
                        tableOffset = tableOffset,
                        slotsPerSpecies = slots,
                        elementSize = elementSize,
                        referenceCount = rootReferences + endReferences,
                    )
                }
            }
        }
        return ReferencedEvolutionCandidates(candidates)
    }

    private fun looksLikeEvolutionTableSample(
        rom: RomImage,
        tableOffset: Int,
        speciesCount: Int,
        slotsPerSpecies: Int,
        elementSize: Int,
    ): Boolean {
        val stride = slotsPerSpecies * elementSize
        val sampleCount = minOf(speciesCount, MAX_EVOLUTION_ROOT_SAMPLES)
        var validRows = 0
        var invalidRows = 0
        repeat(sampleCount) { sample ->
            val species = if (sampleCount == 1) 0 else sample * (speciesCount - 1) / (sampleCount - 1)
            if (species == 0) {
                validRows++
            } else {
                val row = PokemonDatasetValidators.decodeGen3EvolutionRow(
                    rom = rom,
                    offset = tableOffset + species * stride,
                    slotsPerSpecies = slotsPerSpecies,
                    recordSize = elementSize,
                    speciesCount = speciesCount,
                )
                if (row.invalidSlots == 0) validRows++ else invalidRows++
            }
        }
        return validRows > invalidRows
    }

    private fun chooseEvolutions(candidates: List<EvolutionCandidate>): ValidationEvidence {
        val unique = candidates.distinctBy {
            Triple(it.validation.evidence.offset, it.validation.evidence.recordSize, it.validation.evidence.elementSize)
        }
        if (unique.isEmpty()) return missing("Gen 3 evolution table not resolved by structural validation")
        val ranked = unique.sortedWith(
            compareByDescending<EvolutionCandidate> { it.validation.evidence.validRecords }
                .thenByDescending { it.validation.evidence.confidence }
                .thenByDescending { it.validation.structuralQuality }
                .thenByDescending { it.validation.activeEdges }
                .thenByDescending { it.referenceCount }
                .thenByDescending { it.inherited },
        )
        if (ranked.size > 1 && ranked[0].sameStrengthAs(ranked[1])) {
            return missing(
                "Gen 3 evolution table has conflicting structural candidates",
                ambiguous = true,
                reviewRecommended = true,
            )
        }
        return ranked.first().validation.evidence
    }

    private fun EvolutionCandidate.sameStrengthAs(other: EvolutionCandidate): Boolean =
        validation.evidence.validRecords == other.validation.evidence.validRecords &&
            validation.evidence.confidence == other.validation.evidence.confidence &&
            validation.structuralQuality == other.validation.structuralQuality &&
            validation.activeEdges == other.validation.activeEdges &&
            referenceCount == other.referenceCount &&
            inherited == other.inherited

    private data class EvolutionCandidate(
        val validation: Gen3EvolutionValidation,
        val referenceCount: Int,
        val inherited: Boolean,
    )

    private data class ReferencedEvolutionCandidate(
        val tableOffset: Int,
        val slotsPerSpecies: Int,
        val elementSize: Int,
        val referenceCount: Int,
    )

    private data class ReferencedEvolutionCandidates(
        val candidates: List<ReferencedEvolutionCandidate>,
        val overflowReason: String? = null,
    )

    fun gen3Learnsets(
        session: RomAnalysisSession,
        speciesCount: Int,
        moveCount: Int,
        inherited: TableLayout?,
    ): ValidationEvidence = gen3LearnsetResolution(
        session, speciesCount, moveCount, inherited,
    ).evidence

    fun gen3Learnsets(
        rom: RomImage,
        speciesCount: Int,
        moveCount: Int,
        inherited: TableLayout?,
        referenceIndex: GbaCompiledReferenceIndex = standaloneGbaReferenceCounts(rom),
    ): ValidationEvidence = gen3LearnsetResolution(
        rom, speciesCount, moveCount, inherited, referenceIndex,
    ).evidence

    fun gen3LearnsetResolution(
        session: RomAnalysisSession,
        speciesCount: Int,
        moveCount: Int,
        inherited: TableLayout?,
    ): Gen3LearnsetResolution = gen3LearnsetResolution(
        rom = session.rom,
        speciesCount = speciesCount,
        moveCount = moveCount,
        inherited = inherited,
        referenceIndex = session.legacyGbaReferenceCounts(),
    )

    fun gen3LearnsetResolution(
        rom: RomImage,
        speciesCount: Int,
        moveCount: Int,
        inherited: TableLayout?,
        referenceIndex: GbaCompiledReferenceIndex = standaloneGbaReferenceCounts(rom),
    ): Gen3LearnsetResolution {
        if (speciesCount <= 0 || speciesCount.toLong() * 4 > rom.size.toLong()) {
            return Gen3LearnsetResolution(
                missing("Gen 3 learnset species count cannot fit in the ROM"), emptyList(),
            )
        }
        referenceIndex.overflowReason?.let {
            return Gen3LearnsetResolution(missing(it, reviewRecommended = true), emptyList())
        }
        val moveBits = Gen3LearnsetEncoding.packedMoveBits(moveCount)
        val compiledReferenceCounts = referenceIndex.counts
        val candidates = linkedMapOf<Pair<Int, TableRecordFormat>, LearnsetCandidate>()
        var candidateOverflow = false
        fun addCandidate(
            evidence: ValidationEvidence,
            inheritedCandidate: Boolean = false,
            structuralQuality: Double = evidence.confidence,
        ) {
            val offset = evidence.offset ?: return
            val format = evidence.format ?: return
            if (!evidence.compatible) return
            val key = offset to format
            val candidate = LearnsetCandidate(
                evidence = evidence,
                references = compiledReferenceCounts[offset] ?: 0,
                inherited = inheritedCandidate,
                structuralQuality = structuralQuality,
            )
            val current = candidates[key]
            if (current != null) {
                candidates[key] = listOf(current, candidate).sortedWith(learnsetComparator).first()
            } else if (candidates.size >= MAX_DISCOVERED_LEARNSET_CANDIDATES) {
                candidateOverflow = true
            } else {
                candidates[key] = candidate
            }
        }

        inherited?.let { layout ->
            validateInheritedLearnset(rom, layout, speciesCount, moveCount, moveBits)?.let { evidence ->
                addCandidate(evidence, inheritedCandidate = true)
            }
        }

        val tableBytes = speciesCount.toLong() * 4
        var eligibleRoots = 0
        compiledReferenceCounts.keys.forEach { tableOffset ->
            if (candidateOverflow || tableOffset % 4 != 0 || tableOffset < 0 ||
                tableOffset.toLong() + tableBytes > rom.size.toLong()
            ) return@forEach
            val firstTarget = runCatching { rom.gbaPointer(tableOffset) }.getOrNull()
            val none = rom.u32le(tableOffset)
            val firstIsPackedEmpty = firstTarget != null && firstTarget <= rom.size - 2 && rom.u16le(firstTarget) == 0xFFFF
            val noneReusesFirstSpecies = speciesCount > 1 &&
                none == rom.u32le(tableOffset + 4) && none in 0x08000000L..0x09FFFFFFL
            val expandedSample = firstTarget?.let { looksLikeExpandedLearnset(rom, it, moveCount) } == true
            val levelMoveSample = looksLikeLevelMoveLearnsetTableSample(
                rom, tableOffset, speciesCount, moveCount,
            )
            val wideSample = looksLikeLearnsetPointerTableSample(
                rom, tableOffset, speciesCount, moveCount,
            )
            if (!firstIsPackedEmpty && !noneReusesFirstSpecies && !expandedSample && !levelMoveSample && !wideSample) {
                return@forEach
            }
            eligibleRoots++
            if (eligibleRoots > MAX_REFERENCED_LEARNSET_ROOTS) {
                candidateOverflow = true
                return@forEach
            }
            if (firstIsPackedEmpty || noneReusesFirstSpecies) {
                addCandidate(
                    PokemonDatasetValidators.gen3Learnsets(
                        rom, tableOffset, speciesCount, moveCount, moveBits,
                    ),
                )
            }
            if (expandedSample) {
                addCandidate(PokemonDatasetValidators.gen3ExpandedLearnsets(rom, tableOffset, speciesCount, moveCount))
            }
            if (levelMoveSample) {
                addCandidate(PokemonDatasetValidators.gen3LevelMoveLearnsets(rom, tableOffset, speciesCount, moveCount))
            }
            if (wideSample) {
                val validation = PokemonDatasetValidators.gen3WideLearnsetValidation(
                    rom, tableOffset, speciesCount, moveCount,
                )
                if (validation.evidence.compatible) {
                    addCandidate(validation.evidence, structuralQuality = validation.structuralQuality)
                }
            }
        }

        if (candidateOverflow) {
            return Gen3LearnsetResolution(
                missing(
                    "Gen 3 learnset structural candidate budget exceeded " +
                        "($MAX_DISCOVERED_LEARNSET_CANDIDATES); automatic resolution requires review",
                    reviewRecommended = true,
                ),
                emptyList(),
            )
        }
        return resolveLearnsetCandidates(rom, speciesCount, moveCount, candidates.values.toList())
    }

    private fun validateInheritedLearnset(
        rom: RomImage,
        layout: TableLayout,
        speciesCount: Int,
        moveCount: Int,
        moveBits: Int,
    ): ValidationEvidence? = when (layout.format) {
        TableRecordFormat.GEN3_LEVEL_U8_MOVE_U16 ->
            PokemonDatasetValidators.gen3LevelMoveLearnsets(rom, layout.offset, speciesCount, moveCount)
        TableRecordFormat.GEN3_MOVE_U16_LEVEL_U8 ->
            PokemonDatasetValidators.gen3ExpandedLearnsets(rom, layout.offset, speciesCount, moveCount)
        TableRecordFormat.GEN3_MOVE_U16_LEVEL_U16 ->
            PokemonDatasetValidators.gen3WideLearnsets(rom, layout.offset, speciesCount, moveCount)
        TableRecordFormat.GEN3_PACKED_U16 ->
            PokemonDatasetValidators.gen3Learnsets(rom, layout.offset, speciesCount, moveCount, moveBits)
        else -> when (layout.elementSize) {
            4 -> PokemonDatasetValidators.gen3WideLearnsets(rom, layout.offset, speciesCount, moveCount)
            3 -> PokemonDatasetValidators.gen3ExpandedLearnsets(rom, layout.offset, speciesCount, moveCount)
            else -> PokemonDatasetValidators.gen3Learnsets(rom, layout.offset, speciesCount, moveCount, moveBits)
        }
    }

    private fun resolveLearnsetCandidates(
        rom: RomImage,
        speciesCount: Int,
        moveCount: Int,
        candidates: List<LearnsetCandidate>,
    ): Gen3LearnsetResolution {
        if (candidates.isEmpty()) {
            return Gen3LearnsetResolution(
                missing("Gen 3 learnset pointer table not resolved by structural validation"), emptyList(),
            )
        }
        val ranked = removeSameRootPackedAliases(rom, speciesCount, moveCount, candidates)
            .sortedWith(learnsetComparator)
        val selector = Gen3LearnsetSelectorExtractor.extract(
            rom,
            ranked.mapNotNullTo(linkedSetOf()) { it.evidence.offset },
        )
        if (selector != null) {
            val zero = uniquelyRankedCandidateAt(selector.zeroTableOffset, ranked)
            val nonZero = uniquelyRankedCandidateAt(selector.nonZeroTableOffset, ranked)
            val strongest = ranked.first()
            if (zero != null && nonZero != null &&
                zero.sameStructuralStrengthAs(nonZero) && strongest.sameStructuralStrengthAs(zero)
            ) {
                return Gen3LearnsetResolution(
                    evidence = zero.evidence,
                    tables = listOf(zero, nonZero).map { it.toLayout(speciesCount) },
                    selector = selector,
                )
            }
        }
        val first = ranked.first()
        val tied = ranked.takeWhile { it.sameStrengthAs(first) }
        return if (tied.mapNotNull { it.evidence.offset }.distinct().size > 1 ||
            tied.mapNotNull { it.evidence.format }.distinct().size > 1
        ) {
            Gen3LearnsetResolution(
                evidence = missing(
                    "Gen 3 learnset pointer table has conflicting structural candidates",
                    ambiguous = true,
                    reviewRecommended = true,
                ),
                tables = tied.map { it.toLayout(speciesCount) },
            )
        } else {
            Gen3LearnsetResolution(first.evidence, listOf(first.toLayout(speciesCount)))
        }
    }

    private fun removeSameRootPackedAliases(
        rom: RomImage,
        speciesCount: Int,
        moveCount: Int,
        candidates: List<LearnsetCandidate>,
    ): List<LearnsetCandidate> {
        val aliases = candidates
            .groupBy { candidate -> candidate.evidence.offset }
            .filterKeys { offset -> offset != null }
            .values
            .flatMapTo(linkedSetOf()) { atRoot ->
                val root = requireNotNull(atRoot.first().evidence.offset)
                val wideHasPositiveLevels = atRoot.any { candidate ->
                    candidate.evidence.format == TableRecordFormat.GEN3_MOVE_U16_LEVEL_U16
                } && hasPositiveWideLevels(rom, root, speciesCount, moveCount)
                if (!wideHasPositiveLevels) return@flatMapTo emptyList()
                atRoot.filter { candidate ->
                    candidate.evidence.format == TableRecordFormat.GEN3_PACKED_U16 &&
                        hasOnlyZeroPackedLevels(rom, root, speciesCount, moveCount)
                }
            }
        return candidates.filterNot(aliases::contains)
    }

    internal fun hasOnlyZeroPackedLevels(
        rom: RomImage,
        pointerTableOffset: Int,
        speciesCount: Int,
        moveCount: Int,
    ): Boolean {
        val offsets = List(speciesCount) { index ->
            rom.gbaPointer(pointerTableOffset + index * 4)
        }
        if (offsets.any { it == null }) return false
        val boundaries = Gen3PackedLearnsetDecoder.adjacentPointerBoundaries(offsets)
        val moveBits = Gen3LearnsetEncoding.packedMoveBits(moveCount)
        var decodedEntries = 0
        offsets.forEach { candidateOffset ->
            val offset = requireNotNull(candidateOffset)
            val decoded = Gen3PackedLearnsetDecoder.decode(
                rom,
                offset,
                moveCount,
                moveBits,
                boundaries[offset],
            )
            if (!decoded.usable) return false
            decodedEntries += decoded.records.size
            if (decoded.records.any { record -> record.level > 0 }) return false
        }
        return decodedEntries > 0
    }

    private fun hasPositiveWideLevels(
        rom: RomImage,
        pointerTableOffset: Int,
        speciesCount: Int,
        moveCount: Int,
    ): Boolean = (0 until speciesCount).any { index ->
        rom.gbaPointer(pointerTableOffset + index * 4)?.let { offset ->
            PokemonDatasetValidators.decodeGen3WideLearnset(rom, offset, moveCount)
                ?.any { record -> record.level > 0 }
        } == true
    }

    private fun uniquelyRankedCandidateAt(offset: Int, ranked: List<LearnsetCandidate>): LearnsetCandidate? {
        val atRoot = ranked.filter { it.evidence.offset == offset }
        val first = atRoot.firstOrNull() ?: return null
        return first.takeIf { atRoot.getOrNull(1)?.sameStrengthAs(first) != true }
    }

    private fun LearnsetCandidate.toLayout(speciesCount: Int) = Gen3LearnsetTableLayout(
        table = TableLayout(
            offset = requireNotNull(evidence.offset),
            count = speciesCount,
            recordSize = evidence.recordSize ?: 4,
            variableLength = true,
            elementSize = evidence.elementSize,
            format = requireNotNull(evidence.format),
        ),
        confidence = evidence.confidence,
        referenceCount = references,
    )

    private val learnsetComparator =
        compareByDescending<LearnsetCandidate> { it.references }
            .thenByDescending { it.evidence.validRecords }
            .thenByDescending { it.evidence.confidence }
            .thenByDescending { it.structuralQuality }
            .thenByDescending { it.inherited }
            .thenBy { it.evidence.offset ?: Int.MAX_VALUE }

    private fun LearnsetCandidate.sameStrengthAs(other: LearnsetCandidate): Boolean =
        sameStructuralStrengthAs(other)

    private fun LearnsetCandidate.sameStructuralStrengthAs(other: LearnsetCandidate): Boolean =
        references == other.references &&
            evidence.validRecords == other.evidence.validRecords &&
            evidence.confidence == other.evidence.confidence &&
            structuralQuality == other.structuralQuality

    private data class LearnsetCandidate(
        val evidence: ValidationEvidence,
        val references: Int,
        val inherited: Boolean,
        val structuralQuality: Double,
    )

    private fun looksLikeLevelMoveLearnsetTableSample(
        rom: RomImage,
        tableOffset: Int,
        speciesCount: Int,
        moveCount: Int,
    ): Boolean {
        val offsets = List(speciesCount) { index -> rom.gbaPointer(tableOffset + index * 4) }
        val boundaries = Gen3PackedLearnsetDecoder.adjacentPointerBoundaries(offsets)
        val sampleCount = minOf(speciesCount, MAX_LEARNSET_ROOT_SAMPLES)
        var valid = 0
        var invalid = 0
        repeat(sampleCount) { sample ->
            val species = if (sampleCount == 1) 0 else sample * (speciesCount - 1) / (sampleCount - 1)
            val offset = offsets[species]
            if (offset != null && PokemonDatasetValidators.decodeGen3LevelMoveLearnset(
                    rom, offset, moveCount, boundaries[offset],
                ) != null
            ) {
                valid++
            } else {
                invalid++
            }
        }
        return valid > 0
    }

    private fun looksLikeLearnsetPointerTableSample(
        rom: RomImage,
        tableOffset: Int,
        speciesCount: Int,
        moveCount: Int,
    ): Boolean {
        val sampleCount = minOf(speciesCount, MAX_LEARNSET_ROOT_SAMPLES)
        var plausible = 0
        var implausible = 0
        repeat(sampleCount) { sample ->
            val species = if (sampleCount == 1) {
                0
            } else {
                sample * (speciesCount - 1) / (sampleCount - 1)
            }
            val cell = tableOffset + species * 4
            val raw = rom.u32le(cell)
            if (raw != 0L) {
                val learnset = rom.gbaPointer(cell) ?: return false
                if (looksLikeWideLearnsetPrefix(rom, learnset, moveCount) ||
                    looksLikeExpandedLearnsetPrefix(rom, learnset, moveCount)
                ) {
                    plausible++
                } else {
                    implausible++
                }
            }
        }
        return plausible > 0
    }

    private fun looksLikeWideLearnsetPrefix(rom: RomImage, start: Int, moveCount: Int): Boolean {
        var offset = start
        repeat(MAX_LEARNSET_PREFIX_ENTRIES) {
            if (offset + 2 > rom.size) return false
            val move = rom.u16le(offset)
            if (move == 0xFFFF) return true
            if (offset + 4 > rom.size) return false
            val level = rom.u16le(offset + 2)
            if (move !in 1..moveCount || level !in 0..100) return false
            offset += 4
        }
        return true
    }

    private fun looksLikeExpandedLearnsetPrefix(rom: RomImage, start: Int, moveCount: Int): Boolean {
        var offset = start
        repeat(MAX_LEARNSET_PREFIX_ENTRIES) {
            if (offset + 3 > rom.size) return false
            val move = rom.u16le(offset)
            val level = rom.u8(offset + 2)
            if (move == 0 && level == 0xFF) return true
            if (move !in 1..moveCount || level !in 0..100) return false
            offset += 3
        }
        return true
    }

    private fun looksLikeExpandedLearnset(rom: RomImage, start: Int, moveCount: Int): Boolean {
        var offset = start
        var previousLevel = 0
        repeat(256) {
            if (offset + 3 > rom.size) return false
            val move = rom.u16le(offset)
            val level = rom.u8(offset + 2)
            if (move == 0 && level == 0xFF) return true
            if (move !in 1..moveCount || level !in 0..100) return false
            if (level > 0) {
                if (level < previousLevel.coerceAtLeast(1)) return false
                previousLevel = level
            }
            offset += 3
        }
        return false
    }

    private fun validateDescription(
        rom: RomImage,
        count: Int,
        layout: TableLayout,
        codec: PokemonTextCodec,
    ): ValidationEvidence {
        val pointerOffsets = layout.pointerOffsets.ifEmpty {
            if (layout.recordSize >= 36) listOf(16, 20) else listOf(16)
        }
        return PokemonDatasetValidators.gen3Descriptions(
            rom, layout.offset, count, layout.recordSize, pointerOffsets.toIntArray(), codec,
        )
    }

    private fun inferDescription(
        rom: RomImage,
        maximumCount: Int,
        layout: TableLayout,
        codec: PokemonTextCodec,
    ): ValidationEvidence {
        val pointerOffsets = layout.pointerOffsets.ifEmpty {
            if (layout.recordSize >= 36) listOf(16, 20) else listOf(16)
        }
        return PokemonDatasetValidators.inferGen3DescriptionCount(
            rom = rom,
            offset = layout.offset,
            maximumCount = maximumCount,
            minimumCount = minOf(300, maxOf(2, maximumCount / 4)),
            recordSize = layout.recordSize,
            descriptionPointerOffsets = pointerOffsets.toIntArray(),
            codec = codec,
        )
    }

    private fun validDescriptionStart(rom: RomImage, offset: Int, codec: PokemonTextCodec): Boolean = runCatching {
        val categoryBytes = rom.slice(offset, 12)
        val category = codec.decode(categoryBytes)
        categoryBytes.any { it == codec.terminator.toByte() } &&
            category.any(Char::isLetterOrDigit) &&
            rom.u16le(offset + 12) == 0 && rom.u16le(offset + 14) == 0
    }.getOrDefault(false)

    private fun validateEvolution(rom: RomImage, count: Int, layout: TableLayout): ValidationEvidence {
        return validateEvolutionDetailed(rom, count, layout).evidence
    }

    private fun validateEvolutionDetailed(
        rom: RomImage,
        count: Int,
        layout: TableLayout,
    ): Gen3EvolutionValidation {
        val elementSize = layout.elementSize ?: 6
        if (elementSize !in EVOLUTION_ELEMENT_SIZES || layout.recordSize <= 0 ||
            layout.recordSize % elementSize != 0
        ) {
            return Gen3EvolutionValidation(
                missing("invalid Gen 3 evolution layout metadata"),
                structuralQuality = 0.0,
                activeEdges = 0,
            )
        }
        return PokemonDatasetValidators.gen3EvolutionValidation(
            rom,
            layout.offset,
            count,
            slotsPerSpecies = layout.recordSize / elementSize,
            recordSize = elementSize,
        )
    }

    private fun choose(
        candidates: List<ValidationEvidence>,
        inheritedOffset: Int?,
        label: String,
        coverageFirst: Boolean = false,
        referenceCounts: Map<Int, Int> = emptyMap(),
        failOnReferencedTie: Boolean = false,
        parallelTableBytes: Int? = null,
    ): ValidationEvidence {
        val unique = candidates.distinctBy { it.offset to it.recordSize }
        if (unique.isEmpty()) return missing("$label not resolved by structural validation")
        val distance: (ValidationEvidence) -> Int = { evidence ->
            inheritedOffset?.let { abs((evidence.offset ?: 0) - it) } ?: (evidence.offset ?: 0)
        }
        val structuralComparator = if (coverageFirst) {
            compareByDescending<ValidationEvidence> { it.validRecords }
                .thenByDescending { it.confidence }
                .thenBy(distance)
        } else {
            compareByDescending<ValidationEvidence> { it.confidence }
                .thenByDescending { it.validRecords }
                .thenBy(distance)
        }
        val ranked = if (referenceCounts.isNotEmpty()) {
            unique.sortedWith(
                compareByDescending<ValidationEvidence> { referenceCounts[it.offset] ?: 0 }
                    .then(structuralComparator),
            )
        } else if (coverageFirst) {
            unique.sortedWith(
                compareByDescending<ValidationEvidence> { it.validRecords }
                    .thenByDescending { it.confidence }
                    .thenBy(distance),
            )
        } else {
            unique.sortedWith(
                compareByDescending<ValidationEvidence> { it.confidence }
                    .thenByDescending { it.validRecords }
                    .thenBy(distance),
            )
        }
        if (ranked.size > 1) {
            val first = ranked[0]
            val second = ranked[1]
            val firstDistance = distance(first)
            val secondDistance = distance(second)
            val firstReferences = referenceCounts[first.offset] ?: 0
            val secondReferences = referenceCounts[second.offset] ?: 0
            val parallelRulesets = parallelTableBytes?.takeIf { it > 0 }?.let { tableBytes ->
                val firstOffset = first.offset ?: return@let false
                val secondOffset = second.offset ?: return@let false
                abs(firstOffset - secondOffset) == tableBytes
            } ?: false
            val referencedTie = failOnReferencedTie && firstReferences > 0 &&
                firstReferences == secondReferences && first.offset != second.offset
            if (first.confidence == second.confidence && first.validRecords == second.validRecords &&
                !parallelRulesets && (referencedTie || firstDistance == secondDistance)
            ) {
                return missing(
                    "$label has conflicting structural candidates",
                    ambiguous = true,
                    reviewRecommended = true,
                )
            }
        }
        return ranked.first()
    }

    private fun gbaText(value: String): ByteArray = ByteArray(value.length + 1).also { bytes ->
        value.forEachIndexed { index, character ->
            bytes[index] = when (character) {
                ' ' -> 0
                in 'A'..'Z' -> 0xBB + (character - 'A')
                in 'a'..'z' -> 0xD5 + (character - 'a')
                else -> error("unsupported anchor character")
            }.toByte()
        }
        bytes[value.length] = 0xFF.toByte()
    }

    private fun missing(
        reason: String,
        ambiguous: Boolean = false,
        reviewRecommended: Boolean = false,
    ) = ValidationEvidence(
        compatible = false,
        validRecords = 0,
        totalRecords = 0,
        confidence = 0.0,
        reasons = listOf(reason),
        ambiguous = ambiguous,
        reviewRecommended = reviewRecommended,
    )

    private val DESCRIPTION_LAYOUTS = listOf(
        TableLayout(0, 0, 32, pointerOffsets = listOf(16)),
        TableLayout(0, 0, 36, pointerOffsets = listOf(16)),
        TableLayout(0, 0, 36, pointerOffsets = listOf(16, 20)),
    )
    private val DESCRIPTION_ANCHORS = listOf(gbaText("SEED"), gbaText("Seed"))
    private const val MINIMUM_DESCRIPTION_PREFIX_RECORDS = 2
    private const val DESCRIPTION_CONSUMER_WINDOW_BYTES = 8
    private const val THUMB_LITERAL_LOAD_MASK = 0xF800
    private const val THUMB_LITERAL_LOAD_OPCODE = 0x4800
    private const val MAX_LEARNSET_ROOT_SAMPLES = 16
    private const val MAX_LEARNSET_PREFIX_ENTRIES = 4
    private const val MAX_REFERENCED_LEARNSET_ROOTS = 256
    private const val MAX_DISCOVERED_LEARNSET_CANDIDATES = 256
    private const val MAX_REFERENCED_DESCRIPTION_CANDIDATES = 256
    private val EVOLUTION_ELEMENT_SIZES = intArrayOf(8, 6)
    private const val MIN_EVOLUTION_SLOTS = 1
    private const val MAX_EVOLUTION_SLOTS = 32
    private const val MAX_EVOLUTION_ROOT_SAMPLES = 16
    private const val MAX_REFERENCED_EVOLUTION_CANDIDATES = 256
    private const val MAX_REFERENCED_EVOLUTION_PREFILTER_SHAPES = 4_096
}
