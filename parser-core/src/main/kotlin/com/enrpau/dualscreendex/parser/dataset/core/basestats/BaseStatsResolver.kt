package com.enrpau.dualscreendex.parser.dataset.core.basestats

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

/**
 * Resolves one ordinary Gen III base-stat table from typed authoritative proposals.
 *
 * Structural proposals are explicit because their anchors belong to family-specific discovery.
 * This unit owns ABI interpretation, candidate qualification, reference provenance, budgets, and
 * ranking; it never infers row liveness from names, offsets, or discovery order.
 */
class BaseStatsResolver(
    private val codec: BaseStatsTableDecoder = BaseStatsCodec(),
) {
    fun resolve(
        session: RomAnalysisSession,
        semanticDomain: BaseStatsSemanticDomain,
        directCompiledConsumerLayouts: Collection<BaseStatsTableLayout> = emptyList(),
        publishedLayouts: Collection<BaseStatsTableLayout> = emptyList(),
        compiledLayouts: Collection<BaseStatsTableLayout> = emptyList(),
        inheritedLayouts: Collection<BaseStatsTableLayout> = emptyList(),
        structuralLayouts: Collection<BaseStatsTableLayout> = emptyList(),
    ): DatasetResolution<ResolvedBaseStatsLayout> {
        if (session.exactProfileSnapshot?.tables?.baseStats != null) {
            val exact = exactProfileLayout(session) ?: return unavailable(
                observed = 1,
                reason = "matching exact profile base-stat layout uses an unsupported ABI",
            )
            return resolveExact(session, semanticDomain, exact)
        }

        val proposals = sequence {
            directCompiledConsumerLayouts.forEach {
                yield(Proposal(it, CandidateSource.DIRECT_COMPILED_CONSUMER))
            }
            publishedLayouts.forEach { yield(Proposal(it, CandidateSource.PUBLISHED_HEADER)) }
            compiledLayouts.forEach { yield(Proposal(it, CandidateSource.COMPILED_REFERENCE)) }
            inheritedLayouts.forEach { yield(Proposal(it, CandidateSource.INHERITED_FAMILY_LAYOUT)) }
            structuralLayouts.forEach { yield(Proposal(it, CandidateSource.STRUCTURAL_ANCHOR)) }
        }
        val proposalIterator = proposals.iterator()
        if (!proposalIterator.hasNext()) {
            return unavailable(0, "no Gen III base-stat candidates were proposed")
        }

        val referenceIndexRequired = directCompiledConsumerLayouts.isNotEmpty() ||
            publishedLayouts.isNotEmpty() || compiledLayouts.isNotEmpty()
        val referenceIndex = if (referenceIndexRequired) {
            session.gbaReferenceIndex
                ?: return unavailable(0, "Gen III base-stat resolution requires a GBA analysis session")
        } else {
            null
        }
        referenceIndex?.overflowReason?.let { reason ->
            return budgetExceeded(
                kind = BudgetKind.REFERENCE_TARGETS,
                observed = referenceIndex.observedTargets.toLong(),
                limit = referenceIndex.limitTargets.toLong(),
                complete = false,
                reason = reason,
            )
        }

        val candidates = mutableListOf<DatasetCandidate<ResolvedBaseStatsLayout>>()
        val roots = linkedSetOf<Long>()
        var consumedWork = 0L
        while (proposalIterator.hasNext()) {
            val proposal = proposalIterator.next()
            if (consumedWork == session.limits.maxProbeWorkPerDataset.toLong()) {
                return budgetExceeded(
                    BudgetKind.PROBE_WORK,
                    consumedWork + 1L,
                    session.limits.maxProbeWorkPerDataset.toLong(),
                    complete = false,
                    reason = "Gen III base-stat probe-work budget exceeded " +
                        "(${consumedWork + 1L} > ${session.limits.maxProbeWorkPerDataset})",
                )
            }
            consumedWork++

            if (proposal.layout.offset !in roots) {
                if (roots.size == session.limits.maxProbeRootsPerDataset) {
                    return budgetExceeded(
                        BudgetKind.PROBE_ROOTS,
                        roots.size.toLong() + 1L,
                        session.limits.maxProbeRootsPerDataset.toLong(),
                        complete = false,
                        reason = "Gen III base-stat probe-root budget exceeded " +
                            "(${roots.size + 1} > ${session.limits.maxProbeRootsPerDataset})",
                    )
                }
                roots += proposal.layout.offset
            }

            val reference = proposal.layout.offset
                .takeIf { it in 0..Int.MAX_VALUE.toLong() }
                ?.toInt()
                ?.let { referenceIndex?.target(it) }
            when (val assessment = assess(session, semanticDomain, proposal, reference)) {
                is Assessment.Candidate -> candidates += assessment.value
                is Assessment.ExtentBudget -> return budgetExceeded(
                    BudgetKind.EXTENT,
                    assessment.observed,
                    assessment.limit,
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
                    reason = "Gen III base-stat candidate budget exceeded " +
                        "(${candidates.size} > ${session.limits.maxCandidatesPerDataset})",
                )
            }
        }

        val abiConflicts = candidates
            .groupBy { it.layout.table.offset }
            .toSortedMap()
            .values
            .filter { atRoot -> atRoot.map { it.layout.table.abi }.distinct().size > 1 }
        if (abiConflicts.isNotEmpty()) {
            val diagnostics = abiConflicts
                .flatten()
                .sortedWith(
                    compareBy<DatasetCandidate<ResolvedBaseStatsLayout>>(
                        { it.layoutIdentity.value },
                        { it.source.name },
                        { it.identity.value },
                    ),
                )
                .distinctBy { it.identity to it.source }
            return DatasetResolution.Ambiguous(
                kind = DatasetKind.BASE_STATS,
                candidates = diagnostics,
            )
        }

        return CandidateSelector.select(
            session = session,
            kind = DatasetKind.BASE_STATS,
            candidates = candidates.asSequence(),
            structuralAnchorPolicy = StructuralAnchorPolicy.allow(DatasetKind.BASE_STATS),
        )
    }

    private fun resolveExact(
        session: RomAnalysisSession,
        semanticDomain: BaseStatsSemanticDomain,
        layout: BaseStatsTableLayout,
    ): DatasetResolution<ResolvedBaseStatsLayout> {
        val assessment = assess(
            session = session,
            semanticDomain = semanticDomain,
            proposal = Proposal(layout, CandidateSource.EXACT_PROFILE),
            reference = null,
        )
        return when (assessment) {
            is Assessment.Candidate -> CandidateSelector.select(
                session = session,
                kind = DatasetKind.BASE_STATS,
                candidates = sequenceOf(assessment.value),
            )
            is Assessment.ExtentBudget -> budgetExceeded(
                BudgetKind.EXTENT,
                assessment.observed,
                assessment.limit,
                complete = true,
                reason = assessment.reason,
            )
            Assessment.Rejected -> unavailable(
                observed = 1,
                reason = "matching exact profile base-stat layout failed the Gen III ABI contract",
            )
        }
    }

    private fun assess(
        session: RomAnalysisSession,
        semanticDomain: BaseStatsSemanticDomain,
        proposal: Proposal,
        reference: GbaTargetReferenceEvidence?,
    ): Assessment {
        val layout = proposal.layout
        if (layout.count != semanticDomain.tableRowCount || layout.count !in 1..Int.MAX_VALUE.toLong()) {
            return Assessment.Rejected
        }
        if (
            proposal.source == CandidateSource.COMPILED_REFERENCE && reference == null ||
            proposal.source == CandidateSource.PUBLISHED_HEADER && reference == null
        ) {
            return Assessment.Rejected
        }
        val decoded = when (val outcome = codec.decode(session, layout)) {
            is BaseStatsTableOutcome.Decoded -> outcome
            is BaseStatsTableOutcome.ExtentBudgetExceeded -> return Assessment.ExtentBudget(
                outcome.observedBytes,
                outcome.limitBytes,
                outcome.reason,
            )
            is BaseStatsTableOutcome.Rejected -> return Assessment.Rejected
        }
        val malformed = decoded.rows.filterIsInstance<BaseStatsRowOutcome.Malformed>()
        if (malformed.isNotEmpty()) return Assessment.Rejected

        val activeRows = semanticDomain.activeRowIndices.map(decoded.rows::get)
        val activeCovered = activeRows.count { it is BaseStatsRowOutcome.Decoded }
        val activeExpected = semanticDomain.expectedRecords
        val activeIncomplete = activeExpected - activeCovered
        if (activeIncomplete > 0) {
            val coverageQualifies = activeCovered.toLong() * COVERAGE_DENOMINATOR >=
                activeExpected.toLong() * MINIMUM_PARTIAL_PERCENT
            val sourceQualifies = proposal.source == CandidateSource.EXACT_PROFILE ||
                proposal.source == CandidateSource.DIRECT_COMPILED_CONSUMER ||
                proposal.source == CandidateSource.PUBLISHED_HEADER
            val publishedReferencesQualify = proposal.source != CandidateSource.PUBLISHED_HEADER ||
                (reference?.count ?: 0) >= MINIMUM_PARTIAL_COMPILED_REFERENCES
            if (!coverageQualifies || !sourceQualifies || !publishedReferencesQualify) {
                return Assessment.Rejected
            }
        }

        val compiledSites = compiledSites(session, reference)
        val evidenceUnavailable = reference?.siteEvidenceUnavailableReason
        val structuralEmpty = decoded.rows.count { it is BaseStatsRowOutcome.StructuralEmpty }
        val reasons = buildList {
            if (activeIncomplete > 0) {
                add(
                    CandidateReason(
                        CandidateReasonKind.ANOMALY,
                        "$activeIncomplete active base-stat row(s) are exact-zero and incomplete",
                    ),
                )
            }
            if (structuralEmpty > 0) {
                add(
                    CandidateReason(
                        CandidateReasonKind.INFORMATION,
                        "$structuralEmpty base-stat row(s) are exact structural zeroes",
                    ),
                )
            }
            evidenceUnavailable?.let {
                add(CandidateReason(CandidateReasonKind.ANOMALY, it))
            }
            compiledSites.overflowReason?.let {
                add(CandidateReason(CandidateReasonKind.ANOMALY, it))
            }
        }
        val resolved = ResolvedBaseStatsLayout(layout, decoded.rows)
        val eligibility = if (proposal.source == CandidateSource.EXACT_PROFILE) {
            session.exactProfileEligibility()
        } else {
            CandidateEligibility.validated(proposal.source)
        }
        return Assessment.Candidate(
            DatasetCandidate(
                identity = CandidateIdentity(layout.layoutIdentity.value),
                kind = DatasetKind.BASE_STATS,
                layout = resolved,
                source = proposal.source,
                strength = CandidateStrength(
                    semanticCoverage = EvidenceCoverage(activeCovered, activeExpected),
                    structuralCoverage = EvidenceCoverage(
                        covered = decoded.rows.size,
                        expected = decoded.rows.size,
                    ),
                    compiledReferenceCount = reference?.count ?: 0,
                    datasetQuality = decoded.rows.count { it is BaseStatsRowOutcome.Decoded },
                ),
                diagnosticOffset = layout.offset.toInt(),
                diagnosticLabel = layout.layoutIdentity.value,
                eligibility = eligibility,
                provenance = CandidateProvenance(
                    reasons = reasons,
                    validatorReviewRecommended = activeIncomplete > 0 ||
                        evidenceUnavailable != null || compiledSites.budgetExceeded,
                    compiledReferenceSites = compiledSites,
                ),
            ),
        )
    }

    private fun exactProfileLayout(session: RomAnalysisSession): BaseStatsTableLayout? {
        val exact = session.exactProfileSnapshot?.tables?.baseStats ?: return null
        val abi = BaseStatsAbi.entries.singleOrNull { it.recordSize == exact.recordSize } ?: return null
        if (exact.stride != null && exact.stride != exact.recordSize) return null
        if (exact.variableLength || exact.valuesArePointers) return null
        return BaseStatsTableLayout(
            offset = exact.offset.toLong(),
            count = exact.count.toLong(),
            abi = abi,
        )
    }

    private fun compiledSites(
        session: RomAnalysisSession,
        reference: GbaTargetReferenceEvidence?,
    ): CompiledReferenceSites {
        reference ?: return CompiledReferenceSites.empty()
        if (reference.siteBudgetExceeded) {
            return CompiledReferenceSites.overflowed(
                observedSites = reference.observedSites.toLong(),
                limitSites = session.limits.maxCompiledReferenceSitesPerCandidate,
                reason = requireNotNull(reference.overflowReason),
            )
        }
        return CompiledReferenceSites.of(
            reference.instructionSites,
            session.limits.maxCompiledReferenceSitesPerCandidate,
        )
    }

    private fun unavailable(
        observed: Int,
        reason: String,
    ): DatasetResolution.Unavailable<ResolvedBaseStatsLayout> = DatasetResolution.Unavailable(
        kind = DatasetKind.BASE_STATS,
        observedCandidates = observed,
        reason = reason,
    )

    private fun budgetExceeded(
        kind: BudgetKind,
        observed: Long,
        limit: Long,
        complete: Boolean,
        reason: String,
    ): DatasetResolution.BudgetExceeded<ResolvedBaseStatsLayout> = DatasetResolution.BudgetExceeded(
        kind = DatasetKind.BASE_STATS,
        budgetKind = kind,
        observed = observed,
        limit = limit,
        observationComplete = complete,
        reason = reason,
    )

    private data class Proposal(
        val layout: BaseStatsTableLayout,
        val source: CandidateSource,
    )

    private sealed interface Assessment {
        data class Candidate(val value: DatasetCandidate<ResolvedBaseStatsLayout>) : Assessment
        data class ExtentBudget(val observed: Long, val limit: Long, val reason: String) : Assessment
        data object Rejected : Assessment
    }

    companion object {
        private const val MINIMUM_PARTIAL_PERCENT = 70L
        private const val COVERAGE_DENOMINATOR = 100L
        private const val MINIMUM_PARTIAL_COMPILED_REFERENCES = 3
        private val DEFAULT = BaseStatsResolver()

        fun resolve(
            session: RomAnalysisSession,
            semanticDomain: BaseStatsSemanticDomain,
            directCompiledConsumerLayouts: Collection<BaseStatsTableLayout> = emptyList(),
            publishedLayouts: Collection<BaseStatsTableLayout> = emptyList(),
            compiledLayouts: Collection<BaseStatsTableLayout> = emptyList(),
            inheritedLayouts: Collection<BaseStatsTableLayout> = emptyList(),
            structuralLayouts: Collection<BaseStatsTableLayout> = emptyList(),
        ): DatasetResolution<ResolvedBaseStatsLayout> = DEFAULT.resolve(
            session = session,
            semanticDomain = semanticDomain,
            directCompiledConsumerLayouts = directCompiledConsumerLayouts,
            publishedLayouts = publishedLayouts,
            compiledLayouts = compiledLayouts,
            inheritedLayouts = inheritedLayouts,
            structuralLayouts = structuralLayouts,
        )
    }
}
