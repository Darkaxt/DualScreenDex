package com.enrpau.dualscreendex.parser.dataset.descriptions

import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.resolution.BudgetKind
import com.enrpau.dualscreendex.parser.resolution.CandidateEligibility
import com.enrpau.dualscreendex.parser.resolution.CandidateIdentity
import com.enrpau.dualscreendex.parser.resolution.CandidateProvenance
import com.enrpau.dualscreendex.parser.resolution.CandidateReason
import com.enrpau.dualscreendex.parser.resolution.CandidateReasonKind
import com.enrpau.dualscreendex.parser.resolution.CandidateSelector
import com.enrpau.dualscreendex.parser.resolution.CandidateSource
import com.enrpau.dualscreendex.parser.resolution.CandidateStrength
import com.enrpau.dualscreendex.parser.resolution.CompiledReferenceSites
import com.enrpau.dualscreendex.parser.resolution.DatasetCandidate
import com.enrpau.dualscreendex.parser.resolution.DatasetKind
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import com.enrpau.dualscreendex.parser.resolution.EvidenceCoverage
import com.enrpau.dualscreendex.parser.resolution.StructuralAnchorPolicy
import com.enrpau.dualscreendex.parser.resolution.exactProfileEligibility
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

/** Discovery, validation, and evidence selection for ordinary/dynamic Gen III descriptions. */
class DescriptionResolver(
    private val decoder: DescriptionTableDecoder = DescriptionCodec(),
) {
    fun resolve(
        session: RomAnalysisSession,
        expectedSpeciesCount: Int,
        profileLayout: DescriptionTableLayout? = null,
        structuralCandidates: Collection<DescriptionTableLayout> = emptyList(),
    ): DatasetResolution<ResolvedDescriptionLayout> {
        if (expectedSpeciesCount <= 0) {
            return DatasetResolution.Unavailable(
                kind = DatasetKind.POKEDEX_DESCRIPTIONS,
                observedCandidates = 0,
                reason = "Gen III description resolution requires a positive species count",
            )
        }

        val exactProbe = profileLayout
            ?.takeIf { matchesExactProfileLayout(session, it) }
            ?.let { DescriptionProbe(it, CandidateSource.EXACT_PROFILE) }
        if (exactProbe != null) {
            when (
                val exact = assess(
                    session = session,
                    expectedSpeciesCount = expectedSpeciesCount,
                    probe = exactProbe,
                    allowPrefixTrim = false,
                )
            ) {
                is Assessment.Candidate -> {
                    val selected = CandidateSelector.select(
                        session = session,
                        kind = DatasetKind.POKEDEX_DESCRIPTIONS,
                        candidates = sequenceOf(exact.value),
                    )
                    if (selected is DatasetResolution.Resolved || selected is DatasetResolution.Partial) {
                        return selected
                    }
                }
                is Assessment.ExtentBudget -> return exact.toResolution()
                Assessment.Rejected -> Unit
            }
        }

        val referenceIndex = session.gbaReferenceIndex
        if (referenceIndex?.overflowed == true) {
            val observed = maxOf(referenceIndex.observedTargets, referenceIndex.limitTargets + 1)
            return budgetExceeded(
                kind = BudgetKind.REFERENCE_TARGETS,
                observed = observed.toLong(),
                limit = referenceIndex.limitTargets.toLong(),
                complete = false,
                reason = requireNotNull(referenceIndex.overflowReason),
            )
        }

        val candidates = mutableListOf<DatasetCandidate<ResolvedDescriptionLayout>>()
        val workBudget = ProbeWorkBudget(session.limits.maxProbeWorkPerDataset.toLong())
        val proposals: Sequence<DescriptionProposal> = sequence {
            profileLayout?.let { layout ->
                yield(
                    DescriptionProposal.Probe(
                        DescriptionProbe(
                            layout = layout,
                            source = if (matchesExactProfileLayout(session, layout)) {
                                CandidateSource.EXACT_PROFILE
                            } else {
                                CandidateSource.INHERITED_FAMILY_LAYOUT
                            },
                            referenceEvidence = referenceEvidence(referenceIndex, layout.offset),
                        ),
                    ),
                )
            }
            structuralCandidates.forEach { layout ->
                yield(
                    DescriptionProposal.Probe(
                        DescriptionProbe(
                            layout = layout,
                            source = CandidateSource.STRUCTURAL_ANCHOR,
                            referenceEvidence = referenceEvidence(referenceIndex, layout.offset),
                        ),
                    ),
                )
            }
            referenceIndex?.targets?.forEach { (root, evidence) ->
                canonicalLayouts(root, expectedSpeciesCount).forEach { layout ->
                    yield(
                        DescriptionProposal.Probe(
                            DescriptionProbe(
                                layout = layout,
                                source = CandidateSource.COMPILED_REFERENCE,
                                referenceEvidence = evidence,
                            ),
                        ),
                    )
                }
            }
            if (candidates.isEmpty()) {
                yieldAll(
                    internalStructuralProposals(
                        session,
                        expectedSpeciesCount,
                        referenceIndex?.targets.orEmpty(),
                        workBudget,
                    ),
                )
            }
        }

        val roots = linkedSetOf<Long>()
        val iterator = proposals.iterator()
        while (iterator.hasNext()) {
            val proposal = iterator.next()
            if (proposal is DescriptionProposal.ProbeWorkExceeded) {
                return probeWorkBudgetExceeded(workBudget, proposal.activity)
            }
            val probe = (proposal as DescriptionProposal.Probe).value
            if (probe.layout.offset !in roots) {
                if (roots.size == session.limits.maxProbeRootsPerDataset) {
                    return budgetExceeded(
                        kind = BudgetKind.PROBE_ROOTS,
                        observed = roots.size.toLong() + 1L,
                        limit = session.limits.maxProbeRootsPerDataset.toLong(),
                        complete = false,
                        reason = "Gen III description probe-root budget exceeded " +
                            "(${roots.size + 1} > ${session.limits.maxProbeRootsPerDataset})",
                    )
                }
                roots += probe.layout.offset
            }
            if (!workBudget.tryConsume()) {
                return probeWorkBudgetExceeded(workBudget, "layout decode")
            }
            when (
                val assessment = assess(
                    session = session,
                    expectedSpeciesCount = expectedSpeciesCount,
                    probe = probe,
                    allowPrefixTrim = probe.source != CandidateSource.EXACT_PROFILE,
                )
            ) {
                is Assessment.Candidate -> {
                    candidates += assessment.value
                    if (candidates.size > session.limits.maxCandidatesPerDataset) {
                        return budgetExceeded(
                            kind = BudgetKind.CANDIDATES,
                            observed = candidates.size.toLong(),
                            limit = session.limits.maxCandidatesPerDataset.toLong(),
                            complete = false,
                            reason = "Gen III description candidate budget exceeded " +
                                "(${candidates.size} > ${session.limits.maxCandidatesPerDataset})",
                        )
                    }
                }
                is Assessment.ExtentBudget -> return assessment.toResolution()
                Assessment.Rejected -> Unit
            }
        }
        return CandidateSelector.select(
            session = session,
            kind = DatasetKind.POKEDEX_DESCRIPTIONS,
            candidates = candidates.asSequence(),
            structuralAnchorPolicy = StructuralAnchorPolicy.allow(DatasetKind.POKEDEX_DESCRIPTIONS),
        )
    }

    private fun assess(
        session: RomAnalysisSession,
        expectedSpeciesCount: Int,
        probe: DescriptionProbe,
        allowPrefixTrim: Boolean,
    ): Assessment {
        val decoded = when (val outcome = decoder.decode(session, probe.layout)) {
            is DescriptionTableOutcome.Decoded -> outcome
            is DescriptionTableOutcome.ExtentBudgetExceeded -> return Assessment.ExtentBudget(
                outcome.observedBytes,
                outcome.limitBytes,
                outcome.reason,
            )
            is DescriptionTableOutcome.Rejected -> return Assessment.Rejected
        }
        val inferredPrefix = inferredPrefixEnd(decoded.rows)
        val prefixEnd = if (allowPrefixTrim) inferredPrefix else decoded.rows.size
        if (probe.anchorRowIndex != null && probe.anchorRowIndex !in 0 until prefixEnd) {
            return Assessment.Rejected
        }
        if (
            probe.anchorRowIndex != null &&
            decoded.rows[probe.anchorRowIndex] !is DescriptionRowOutcome.Decoded
        ) {
            return Assessment.Rejected
        }
        val rows = decoded.rows.subList(0, prefixEnd)
        val table = if (prefixEnd.toLong() == probe.layout.count) {
            probe.layout
        } else {
            DescriptionTableLayout(
                offset = probe.layout.offset,
                count = prefixEnd.toLong(),
                recordSize = probe.layout.recordSize,
                pointerOffsets = probe.layout.pointerOffsets,
            )
        }
        val resolvedLayout = ResolvedDescriptionLayout(table, rows)
        val decodedRows = rows.count { it is DescriptionRowOutcome.Decoded }
        val recoveredPages = rows.sumOf { row ->
            (row as? DescriptionRowOutcome.Decoded)?.pages.orEmpty().count {
                it.provenance is DescriptionRecoveryProvenance.OffByOneWithinNextReferencedBoundary
            }
        }
        val directPages = rows.sumOf { row ->
            (row as? DescriptionRowOutcome.Decoded)?.pages.orEmpty().count {
                it.provenance is DescriptionRecoveryProvenance.Direct
            }
        }
        val malformedRows = rows.count { it is DescriptionRowOutcome.Malformed }
        val structuralEmptyRows = rows.count { it is DescriptionRowOutcome.StructuralEmpty }
        val structurallyEligible = decodedRows > 0 &&
            decodedRows.toLong() * 100 >= rows.size.toLong() * MINIMUM_STRUCTURAL_PERCENT
        if (!structurallyEligible) return Assessment.Rejected
        val eligibility = when {
            probe.source == CandidateSource.EXACT_PROFILE -> session.exactProfileEligibility()
            else -> CandidateEligibility.validated(probe.source)
        }
        val siteUnavailableReason = probe.referenceEvidence?.siteEvidenceUnavailableReason
        val compiledSites = when {
            probe.referenceEvidence == null -> CompiledReferenceSites.empty()
            probe.referenceEvidence.siteBudgetExceeded -> CompiledReferenceSites.overflowed(
                observedSites = probe.referenceEvidence.observedSites.toLong(),
                limitSites = probe.referenceEvidence.limitSites,
                reason = requireNotNull(probe.referenceEvidence.overflowReason),
            )
            probe.referenceEvidence.siteEvidenceAvailable -> CompiledReferenceSites.of(
                probe.referenceEvidence.instructionSites.asSequence(),
                session.limits.maxCompiledReferenceSitesPerCandidate,
            )
            else -> CompiledReferenceSites.empty()
        }
        val reasons = buildList {
            if (prefixEnd < decoded.rows.size) {
                add(
                    CandidateReason(
                        CandidateReasonKind.INFORMATION,
                        "trimmed adjacent non-description data after a structurally stronger prefix",
                    ),
                )
            }
            if (recoveredPages > 0) {
                add(
                    CandidateReason(
                        CandidateReasonKind.RECOVERY,
                        "recovered $recoveredPages off-by-one description pointer(s) within " +
                            "independently referenced text boundaries",
                    ),
                )
            }
            if (malformedRows > 0) {
                add(CandidateReason(CandidateReasonKind.ANOMALY, "$malformedRows description row(s) are malformed"))
            }
            if (structuralEmptyRows > 0) {
                add(
                    CandidateReason(
                        CandidateReasonKind.INFORMATION,
                        "$structuralEmptyRows description row(s) are structurally empty",
                    ),
                )
            }
            siteUnavailableReason?.let { reason ->
                add(CandidateReason(CandidateReasonKind.ANOMALY, reason))
            }
            compiledSites.overflowReason?.let { reason ->
                add(CandidateReason(CandidateReasonKind.ANOMALY, reason))
            }
        }
        return Assessment.Candidate(
            DatasetCandidate(
                identity = CandidateIdentity(resolvedLayout.layoutIdentity.value),
                kind = DatasetKind.POKEDEX_DESCRIPTIONS,
                layout = resolvedLayout,
                source = probe.source,
                strength = CandidateStrength(
                    semanticCoverage = EvidenceCoverage(
                        covered = minOf(decodedRows, expectedSpeciesCount),
                        expected = expectedSpeciesCount,
                    ),
                    structuralCoverage = EvidenceCoverage(decodedRows, rows.size),
                    compiledReferenceCount = probe.referenceEvidence?.count ?: 0,
                    datasetQuality = directPages * 2 + recoveredPages,
                ),
                diagnosticOffset = table.offset.toInt(),
                diagnosticLabel = resolvedLayout.layoutIdentity.value,
                eligibility = eligibility,
                provenance = CandidateProvenance(
                    reasons = reasons,
                    validatorReviewRecommended = recoveredPages > 0 ||
                        malformedRows > 0 ||
                        siteUnavailableReason != null ||
                        compiledSites.budgetExceeded,
                    compiledReferenceSites = compiledSites,
                ),
            ),
        )
    }

    private fun internalStructuralProposals(
        session: RomAnalysisSession,
        expectedSpeciesCount: Int,
        referenceEvidence: Map<Int, GbaTargetReferenceEvidence>,
        workBudget: ProbeWorkBudget,
    ): Sequence<DescriptionProposal> = sequence {
        if (expectedSpeciesCount <= 1) return@sequence
        val longestAnchor = DESCRIPTION_ANCHORS.maxOf(ByteArray::size)
        if (longestAnchor > session.rom.size) return@sequence
        val lastOffset = session.rom.size - longestAnchor
        for (seedOffset in 0..lastOffset) {
            if (!workBudget.tryConsume()) {
                yield(DescriptionProposal.ProbeWorkExceeded("internal anchor scan"))
                return@sequence
            }
            if (DESCRIPTION_ANCHORS.none { anchor -> matchesAt(session.rom, seedOffset, anchor) }) continue
            canonicalShapes().forEach { shape ->
                    val maximumAnchorIndex = minOf(
                        expectedSpeciesCount - 1,
                        seedOffset / shape.recordSize,
                    )
                    for (anchorIndex in 1..maximumAnchorIndex) {
                        val root = seedOffset - anchorIndex * shape.recordSize
                        if (!workBudget.tryConsume()) {
                            yield(DescriptionProposal.ProbeWorkExceeded("internal root-start check"))
                            return@sequence
                        }
                        if (root % 4 != 0 || !looksLikeDescriptionStart(session.rom, root)) continue
                        val layout = DescriptionTableLayout(
                            offset = root.toLong(),
                            count = expectedSpeciesCount.toLong(),
                            recordSize = shape.recordSize,
                            pointerOffsets = shape.pointerOffsets,
                        )
                        yield(
                            DescriptionProposal.Probe(
                                DescriptionProbe(
                                    layout = layout,
                                    source = CandidateSource.STRUCTURAL_ANCHOR,
                                    referenceEvidence = referenceEvidence[root],
                                    anchorRowIndex = anchorIndex,
                                ),
                            ),
                        )
                    }
            }
        }
    }

    private fun inferredPrefixEnd(rows: List<DescriptionRowOutcome>): Int {
        val minimumCount = minOf(300, maxOf(2, rows.size / 4))
        var lastDecoded = 0
        var consecutiveNonDecoded = 0
        rows.forEachIndexed { index, row ->
            if (row is DescriptionRowOutcome.Decoded) {
                lastDecoded = index + 1
                consecutiveNonDecoded = 0
            } else {
                consecutiveNonDecoded++
                if (lastDecoded >= minimumCount && consecutiveNonDecoded >= PREFIX_STOP_RUN) {
                    return lastDecoded
                }
            }
        }
        return rows.size
    }

    private fun canonicalLayouts(root: Int, count: Int): List<DescriptionTableLayout> =
        canonicalShapes().map { shape ->
            DescriptionTableLayout(
                root.toLong(),
                count.toLong(),
                shape.recordSize,
                shape.pointerOffsets,
            )
        }

    private fun canonicalShapes(): List<DescriptionShape> = listOf(
        DescriptionShape(32, listOf(16)),
        DescriptionShape(36, listOf(16)),
        DescriptionShape(36, listOf(16, 20)),
    )

    private fun referenceEvidence(
        index: com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex?,
        offset: Long,
    ): GbaTargetReferenceEvidence? = offset
        .takeIf { it in 0..Int.MAX_VALUE.toLong() }
        ?.toInt()
        ?.let { index?.target(it) }

    private fun matchesExactProfileLayout(
        session: RomAnalysisSession,
        layout: DescriptionTableLayout,
    ): Boolean {
        val exact = session.exactProfileSnapshot?.tables?.descriptions ?: return false
        return layout.offset == exact.offset.toLong() &&
            layout.count == exact.count.toLong() &&
            layout.recordSize == exact.recordSize &&
            layout.pointerOffsets == exact.pointerOffsets
    }

    private fun matchesAt(rom: RomImage, offset: Int, pattern: ByteArray): Boolean =
        pattern.indices.all { index ->
            rom.u8(offset + index) == (pattern[index].toInt() and 0xFF)
        }

    private fun looksLikeDescriptionStart(rom: RomImage, offset: Int): Boolean = runCatching {
        if (offset < 0 || offset.toLong() + MIN_DESCRIPTION_RECORD_BYTES > rom.size.toLong()) {
            return@runCatching false
        }
        val category = rom.slice(offset, CATEGORY_BYTES)
        val terminator = category.indexOf(PokemonTextCodec.gbaEnglish.terminator.toByte())
        terminator >= 0 &&
            PokemonTextCodec.gbaEnglish.decode(category.copyOfRange(0, terminator + 1))
                .any(Char::isLetterOrDigit) &&
            rom.u16le(offset + 12) == 0 &&
            rom.u16le(offset + 14) == 0
    }.getOrDefault(false)

    private fun budgetExceeded(
        kind: BudgetKind,
        observed: Long,
        limit: Long,
        complete: Boolean,
        reason: String,
    ): DatasetResolution.BudgetExceeded<ResolvedDescriptionLayout> = DatasetResolution.BudgetExceeded(
        kind = DatasetKind.POKEDEX_DESCRIPTIONS,
        budgetKind = kind,
        observed = observed,
        limit = limit,
        observationComplete = complete,
        reason = reason,
    )

    private fun probeWorkBudgetExceeded(
        budget: ProbeWorkBudget,
        activity: String,
    ): DatasetResolution.BudgetExceeded<ResolvedDescriptionLayout> = budgetExceeded(
        kind = BudgetKind.PROBE_WORK,
        observed = budget.overflowWitness,
        limit = budget.limit,
        complete = false,
        reason = "Gen III description probe-work budget exceeded during $activity " +
            "(${budget.overflowWitness} > ${budget.limit})",
    )

    private data class DescriptionShape(
        val recordSize: Int,
        val pointerOffsets: List<Int>,
    )

    private data class DescriptionProbe(
        val layout: DescriptionTableLayout,
        val source: CandidateSource,
        val referenceEvidence: GbaTargetReferenceEvidence? = null,
        val anchorRowIndex: Int? = null,
    )

    private sealed interface DescriptionProposal {
        data class Probe(val value: DescriptionProbe) : DescriptionProposal

        data class ProbeWorkExceeded(val activity: String) : DescriptionProposal
    }

    private class ProbeWorkBudget(
        val limit: Long,
    ) {
        private var consumed: Long = 0L
        val overflowWitness: Long get() = consumed + 1L

        fun tryConsume(): Boolean {
            if (consumed == limit) return false
            consumed++
            return true
        }
    }

    private sealed interface Assessment {
        data class Candidate(
            val value: DatasetCandidate<ResolvedDescriptionLayout>,
        ) : Assessment

        data class ExtentBudget(
            val observed: Long,
            val limit: Long,
            val reason: String,
        ) : Assessment {
            fun toResolution(): DatasetResolution.BudgetExceeded<ResolvedDescriptionLayout> =
                DatasetResolution.BudgetExceeded(
                    kind = DatasetKind.POKEDEX_DESCRIPTIONS,
                    budgetKind = BudgetKind.EXTENT,
                    observed = observed,
                    limit = limit,
                    observationComplete = true,
                    reason = reason,
                )
        }

        data object Rejected : Assessment
    }

    private companion object {
        const val MINIMUM_STRUCTURAL_PERCENT = 85L
        const val PREFIX_STOP_RUN = 4
        const val CATEGORY_BYTES = 12
        const val MIN_DESCRIPTION_RECORD_BYTES = 16L
        val DESCRIPTION_ANCHORS = listOf(gbaText("SEED"), gbaText("Seed"))

        fun gbaText(value: String): ByteArray = ByteArray(value.length + 1).also { bytes ->
            value.forEachIndexed { index, character ->
                bytes[index] = when (character) {
                    ' ' -> 0
                    in 'A'..'Z' -> 0xBB + (character - 'A')
                    in 'a'..'z' -> 0xD5 + (character - 'a')
                    else -> error("unsupported description anchor character")
                }.toByte()
            }
            bytes[value.length] = 0xFF.toByte()
        }
    }
}
