package com.enrpau.dualscreendex.parser.dataset.evolutions

import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
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

/** Bounded Gen III evolution discovery and evidence selection; never materializes catalog records. */
class EvolutionResolver(
    private val codec: EvolutionCodec = EvolutionCodec(),
) {
    fun resolveGen3(
        session: RomAnalysisSession,
        expectedSpeciesCount: Int,
        profileLayout: EvolutionTableLayout? = null,
        publishedLayouts: Collection<EvolutionTableLayout> = emptyList(),
    ): DatasetResolution<ResolvedEvolutionLayout> {
        if (expectedSpeciesCount <= 1) {
            return unavailable(0, "Gen III evolution species count must include at least one usable species")
        }
        val exactProbe = profileLayout
            ?.takeIf { matchesExactProfileLayout(session, it) }
            ?.let { EvolutionProbe(it, CandidateSource.EXACT_PROFILE, ReferenceEvidence(emptyList())) }
        if (exactProbe != null) {
            when (val exact = assess(session, expectedSpeciesCount, exactProbe)) {
                is Assessment.Candidate -> {
                    val selected = CandidateSelector.select(
                        session = session,
                        kind = DatasetKind.EVOLUTIONS,
                        candidates = sequenceOf(exact.value),
                    )
                    if (selected is DatasetResolution.Resolved || selected is DatasetResolution.Partial) {
                        return selected
                    }
                }
                is Assessment.ExtentBudget -> return budgetExceeded(
                    kind = BudgetKind.EXTENT,
                    observed = exact.observed,
                    limit = exact.limit,
                    complete = true,
                    reason = exact.reason,
                )
                Assessment.Rejected -> Unit
            }
        }
        val referenceIndex = session.gbaReferenceIndex
            ?: return unavailable(0, "Gen III evolution resolution requires a GBA analysis session")
        referenceIndex.overflowReason?.let { reason ->
            return budgetExceeded(
                BudgetKind.REFERENCE_TARGETS,
                observed = referenceIndex.observedTargets.toLong(),
                limit = referenceIndex.limitTargets.toLong(),
                complete = false,
                reason = reason,
            )
        }

        val workBudget = ProbeWorkBudget(session.limits.maxProbeWorkPerDataset.toLong())
        val proposals: Sequence<EvolutionProposal> = sequence {
            profileLayout?.let { layout ->
                if (!workBudget.tryConsume()) {
                    yield(EvolutionProposal.ProbeWorkExceeded(workBudget.overflowWitness))
                    return@sequence
                }
                yield(
                    EvolutionProposal.Probe(
                        EvolutionProbe(
                            layout = layout,
                            source = if (matchesExactProfileLayout(session, layout)) {
                                CandidateSource.EXACT_PROFILE
                            } else {
                                CandidateSource.INHERITED_FAMILY_LAYOUT
                            },
                            references = referenceEvidence(session, layout, requireBoundary = false),
                        ),
                    ),
                )
            }
            publishedLayouts.forEach { layout ->
                if (!workBudget.tryConsume()) {
                    yield(EvolutionProposal.ProbeWorkExceeded(workBudget.overflowWitness))
                    return@sequence
                }
                yield(
                    EvolutionProposal.Probe(
                        EvolutionProbe(
                            layout = layout,
                            source = CandidateSource.PUBLISHED_HEADER,
                            references = referenceEvidence(session, layout, requireBoundary = false),
                        ),
                    ),
                )
            }
            referenceIndex.targets.keys.asSequence().sorted().forEach { root ->
                RECORD_SIZES.forEach { recordSize ->
                    for (slots in MIN_SLOTS..MAX_SLOTS) {
                        if (!workBudget.tryConsume()) {
                            yield(EvolutionProposal.ProbeWorkExceeded(workBudget.overflowWitness))
                            return@sequence
                        }
                        val layout = EvolutionTableLayout(
                            offset = root.toLong(),
                            count = expectedSpeciesCount.toLong(),
                            slotsPerSpecies = slots,
                            recordSize = recordSize,
                        )
                        val end = runCatching { layout.endExclusive }.getOrNull() ?: continue
                        if (end > session.rom.size.toLong()) continue
                        val boundary = if (end == session.rom.size.toLong()) {
                            null
                        } else {
                            referenceIndex.target(end.toInt()) ?: continue
                        }
                        yield(
                            EvolutionProposal.Probe(
                                EvolutionProbe(
                                    layout = layout,
                                    source = CandidateSource.COMPILED_REFERENCE,
                                    references = ReferenceEvidence(
                                        listOfNotNull(referenceIndex.target(root), boundary),
                                    ),
                                ),
                            ),
                        )
                    }
                }
            }
        }

        val candidates = mutableListOf<DatasetCandidate<ResolvedEvolutionLayout>>()
        val roots = linkedSetOf<Long>()
        val iterator = proposals.iterator()
        while (iterator.hasNext()) {
            val proposal = iterator.next()
            if (proposal is EvolutionProposal.ProbeWorkExceeded) {
                return budgetExceeded(
                    BudgetKind.PROBE_WORK,
                    observed = proposal.observed,
                    limit = session.limits.maxProbeWorkPerDataset.toLong(),
                    complete = false,
                    reason = "Gen III evolution probe-work budget exceeded " +
                        "(${proposal.observed} > ${session.limits.maxProbeWorkPerDataset})",
                )
            }
            val probe = (proposal as EvolutionProposal.Probe).value
            if (probe.layout.offset !in roots) {
                if (roots.size == session.limits.maxProbeRootsPerDataset) {
                    return budgetExceeded(
                        BudgetKind.PROBE_ROOTS,
                        observed = roots.size.toLong() + 1L,
                        limit = session.limits.maxProbeRootsPerDataset.toLong(),
                        complete = false,
                        reason = "Gen III evolution probe-root budget exceeded " +
                            "(${roots.size + 1} > ${session.limits.maxProbeRootsPerDataset})",
                    )
                }
                roots += probe.layout.offset
            }
            when (val assessment = assess(session, expectedSpeciesCount, probe)) {
                is Assessment.Candidate -> candidates += assessment.value
                is Assessment.ExtentBudget -> return budgetExceeded(
                    kind = BudgetKind.EXTENT,
                    observed = assessment.observed,
                    limit = assessment.limit,
                    complete = true,
                    reason = assessment.reason,
                )
                Assessment.Rejected -> Unit
            }
            if (candidates.size > session.limits.maxCandidatesPerDataset) {
                return budgetExceeded(
                    BudgetKind.CANDIDATES,
                    candidates.size.toLong(),
                    session.limits.maxCandidatesPerDataset.toLong(),
                    complete = false,
                    "Gen III evolution candidate budget exceeded " +
                        "(${candidates.size} > ${session.limits.maxCandidatesPerDataset})",
                )
            }
        }
        val standard = CandidateSelector.select(
            session = session,
            kind = DatasetKind.EVOLUTIONS,
            candidates = candidates.asSequence(),
        )
        if (standard !is DatasetResolution.Unavailable) return standard

        val structural = internalStructuralProposals(session, expectedSpeciesCount, workBudget).iterator()
        while (structural.hasNext()) {
            val proposal = structural.next()
            if (proposal is EvolutionProposal.ProbeWorkExceeded) {
                return budgetExceeded(
                    BudgetKind.PROBE_WORK,
                    observed = proposal.observed,
                    limit = session.limits.maxProbeWorkPerDataset.toLong(),
                    complete = false,
                    reason = "Gen III evolution structural-anchor probe-work budget exceeded " +
                        "(${proposal.observed} > ${session.limits.maxProbeWorkPerDataset})",
                )
            }
            val probe = (proposal as EvolutionProposal.Probe).value
            if (probe.layout.offset !in roots) {
                if (roots.size == session.limits.maxProbeRootsPerDataset) {
                    return budgetExceeded(
                        BudgetKind.PROBE_ROOTS,
                        roots.size.toLong() + 1L,
                        session.limits.maxProbeRootsPerDataset.toLong(),
                        complete = false,
                        "Gen III evolution structural-anchor root budget exceeded " +
                            "(${roots.size + 1} > ${session.limits.maxProbeRootsPerDataset})",
                    )
                }
                roots += probe.layout.offset
            }
            when (val assessment = assess(session, expectedSpeciesCount, probe)) {
                is Assessment.Candidate -> candidates += assessment.value
                is Assessment.ExtentBudget -> return budgetExceeded(
                    BudgetKind.EXTENT,
                    assessment.observed,
                    assessment.limit,
                    complete = true,
                    assessment.reason,
                )
                Assessment.Rejected -> Unit
            }
            if (candidates.size > session.limits.maxCandidatesPerDataset) {
                return budgetExceeded(
                    BudgetKind.CANDIDATES,
                    candidates.size.toLong(),
                    session.limits.maxCandidatesPerDataset.toLong(),
                    complete = false,
                    "Gen III evolution structural-anchor candidate budget exceeded " +
                        "(${candidates.size} > ${session.limits.maxCandidatesPerDataset})",
                )
            }
        }
        return CandidateSelector.select(
            session = session,
            kind = DatasetKind.EVOLUTIONS,
            candidates = candidates.asSequence(),
            structuralAnchorPolicy = StructuralAnchorPolicy.allow(DatasetKind.EVOLUTIONS),
        )
    }

    private fun internalStructuralProposals(
        session: RomAnalysisSession,
        expectedSpeciesCount: Int,
        workBudget: ProbeWorkBudget,
    ): Sequence<EvolutionProposal> = sequence {
        if (session.rom.size < 2) return@sequence
        for (anchor in 0..session.rom.size - 2) {
            if (!workBudget.tryConsume()) {
                yield(EvolutionProposal.ProbeWorkExceeded(workBudget.overflowWitness))
                return@sequence
            }
            if (session.rom.u16le(anchor) != LEVEL_METHOD) continue
            RECORD_SIZES.forEach { recordSize ->
                STRUCTURAL_SLOT_SHAPES.forEach { slots ->
                    if (!workBudget.tryConsume()) {
                        yield(EvolutionProposal.ProbeWorkExceeded(workBudget.overflowWitness))
                        return@sequence
                    }
                    val stride = slots * recordSize
                    val root = anchor - stride
                    if (root < 0 || root % 2 != 0) return@forEach
                    val second = root.toLong() + 2L * stride.toLong()
                    if (second < 0 || second + 6L > session.rom.size.toLong()) return@forEach
                    if (
                        session.rom.u16le(anchor + 2) !in 1..100 ||
                        session.rom.u16le(anchor + 4) != 2 ||
                        session.rom.u16le(second.toInt()) != LEVEL_METHOD ||
                        session.rom.u16le(second.toInt() + 2) !in 1..100 ||
                        session.rom.u16le(second.toInt() + 4) != 3
                    ) {
                        return@forEach
                    }
                    yield(
                        EvolutionProposal.Probe(
                            EvolutionProbe(
                                layout = EvolutionTableLayout(
                                    offset = root.toLong(),
                                    count = expectedSpeciesCount.toLong(),
                                    slotsPerSpecies = slots,
                                    recordSize = recordSize,
                                ),
                                source = CandidateSource.STRUCTURAL_ANCHOR,
                                references = ReferenceEvidence(emptyList()),
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun assess(
        session: RomAnalysisSession,
        expectedSpeciesCount: Int,
        probe: EvolutionProbe,
    ): Assessment {
        if (probe.layout.count != expectedSpeciesCount.toLong()) return Assessment.Rejected
        val decoded = when (val outcome = codec.decodeGen3(session, probe.layout)) {
            is EvolutionTableOutcome.Decoded -> outcome
            is EvolutionTableOutcome.ExtentBudgetExceeded -> return Assessment.ExtentBudget(
                outcome.observedBytes,
                outcome.limitBytes,
                outcome.reason,
            )
            is EvolutionTableOutcome.Rejected -> return Assessment.Rejected
        }
        if (decoded.activeEdges == 0) return Assessment.Rejected
        val malformedRows = decoded.rows.count { it is EvolutionRowOutcome.Malformed }
        val validRows = decoded.rows.size - malformedRows
        if (validRows.toLong() * 2L <= decoded.rows.size.toLong()) return Assessment.Rejected
        val validSlots = decoded.rows.sumOf { row ->
            when (row) {
                is EvolutionRowOutcome.Decoded -> probe.layout.slotsPerSpecies
                is EvolutionRowOutcome.StructuralEmpty -> probe.layout.slotsPerSpecies
                is EvolutionRowOutcome.Malformed ->
                    (probe.layout.slotsPerSpecies - row.reasons.size).coerceAtLeast(0)
            }
        }
        val resolved = ResolvedEvolutionLayout(probe.layout, decoded.rows)
        val compiledSites = probe.references.compiledSites(session)
        val unavailableSites = probe.references.evidence
            .mapNotNull { it.siteEvidenceUnavailableReason }
            .distinct()
            .sorted()
        val reasons = buildList {
            if (malformedRows > 0) {
                add(
                    CandidateReason(
                        CandidateReasonKind.ANOMALY,
                        "$malformedRows evolution row(s) contain malformed slots",
                    ),
                )
            }
            unavailableSites.forEach { reason ->
                add(CandidateReason(CandidateReasonKind.ANOMALY, reason))
            }
            compiledSites.overflowReason?.let { reason ->
                add(CandidateReason(CandidateReasonKind.ANOMALY, reason))
            }
        }
        val sourceEligibility = if (probe.source == CandidateSource.EXACT_PROFILE) {
            session.exactProfileEligibility()
        } else {
            CandidateEligibility.validated(probe.source)
        }
        return Assessment.Candidate(
            DatasetCandidate(
                identity = CandidateIdentity(resolved.layoutIdentity.value),
                kind = DatasetKind.EVOLUTIONS,
                layout = resolved,
                source = probe.source,
                strength = CandidateStrength(
                    semanticCoverage = EvidenceCoverage(validRows, expectedSpeciesCount),
                    structuralCoverage = EvidenceCoverage(
                        covered = validSlots,
                        expected = Math.multiplyExact(expectedSpeciesCount, probe.layout.slotsPerSpecies),
                    ),
                    compiledReferenceCount = probe.references.count,
                    datasetQuality = decoded.activeEdges,
                ),
                diagnosticOffset = probe.layout.offset.toInt(),
                diagnosticLabel = resolved.layoutIdentity.value,
                eligibility = sourceEligibility,
                provenance = CandidateProvenance(
                    reasons = reasons,
                    validatorReviewRecommended = malformedRows > 0 || unavailableSites.isNotEmpty() ||
                        compiledSites.budgetExceeded,
                    compiledReferenceSites = compiledSites,
                ),
            ),
        )
    }

    private fun referenceEvidence(
        session: RomAnalysisSession,
        layout: EvolutionTableLayout,
        requireBoundary: Boolean,
    ): ReferenceEvidence {
        val index = session.gbaReferenceIndex ?: return ReferenceEvidence(emptyList())
        val root = layout.offset.takeIf { it in 0..Int.MAX_VALUE.toLong() }
            ?.toInt()
            ?.let(index::target)
        val end = runCatching { layout.endExclusive }.getOrNull()
        val boundary = end
            ?.takeIf { it in 0 until session.rom.size.toLong() }
            ?.toInt()
            ?.let(index::target)
        return if (requireBoundary && boundary == null && end != session.rom.size.toLong()) {
            ReferenceEvidence(emptyList())
        } else {
            ReferenceEvidence(listOfNotNull(root, boundary))
        }
    }

    private fun matchesExactProfileLayout(
        session: RomAnalysisSession,
        layout: EvolutionTableLayout,
    ): Boolean {
        val exact = session.exactProfileSnapshot?.tables?.evolutions ?: return false
        return exact.offset.toLong() == layout.offset &&
            exact.count.toLong() == layout.count &&
            exact.recordSize.toLong() == runCatching { layout.rowStride }.getOrNull() &&
            exact.elementSize == layout.recordSize
    }

    private fun unavailable(
        observed: Int,
        reason: String,
    ): DatasetResolution.Unavailable<ResolvedEvolutionLayout> = DatasetResolution.Unavailable(
        DatasetKind.EVOLUTIONS,
        observed,
        reason,
    )

    private fun budgetExceeded(
        kind: BudgetKind,
        observed: Long,
        limit: Long,
        complete: Boolean,
        reason: String,
    ): DatasetResolution.BudgetExceeded<ResolvedEvolutionLayout> = DatasetResolution.BudgetExceeded(
        DatasetKind.EVOLUTIONS,
        kind,
        observed,
        limit,
        complete,
        reason,
    )

    private data class EvolutionProbe(
        val layout: EvolutionTableLayout,
        val source: CandidateSource,
        val references: ReferenceEvidence,
    )

    private sealed interface EvolutionProposal {
        data class Probe(val value: EvolutionProbe) : EvolutionProposal
        data class ProbeWorkExceeded(val observed: Long) : EvolutionProposal
    }

    private class ProbeWorkBudget(val limit: Long) {
        private var consumed = 0L
        val overflowWitness: Long get() = consumed + 1L

        fun tryConsume(): Boolean {
            if (consumed == limit) return false
            consumed++
            return true
        }
    }

    private data class ReferenceEvidence(
        val evidence: List<GbaTargetReferenceEvidence>,
    ) {
        val count: Int = evidence.fold(0) { total, item ->
            (total.toLong() + item.count.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        fun compiledSites(session: RomAnalysisSession): CompiledReferenceSites {
            val overflowed = evidence.filter { it.siteBudgetExceeded }
            if (overflowed.isNotEmpty()) {
                val observed = overflowed.maxOf { it.observedSites.toLong() }
                return CompiledReferenceSites.overflowed(
                    observedSites = observed,
                    limitSites = session.limits.maxCompiledReferenceSitesPerCandidate,
                    reason = overflowed.mapNotNull { it.overflowReason }.distinct().sorted().joinToString("; "),
                )
            }
            return CompiledReferenceSites.of(
                evidence.asSequence().flatMap { it.instructionSites.asSequence() },
                session.limits.maxCompiledReferenceSitesPerCandidate,
            )
        }
    }

    private sealed interface Assessment {
        data class Candidate(val value: DatasetCandidate<ResolvedEvolutionLayout>) : Assessment
        data class ExtentBudget(val observed: Long, val limit: Long, val reason: String) : Assessment
        data object Rejected : Assessment
    }

    private companion object {
        const val MIN_SLOTS = 1
        const val MAX_SLOTS = 32
        const val LEVEL_METHOD = 4
        val RECORD_SIZES = listOf(8, 6)
        val STRUCTURAL_SLOT_SHAPES = listOf(5, 8, 16, 32)
    }
}
