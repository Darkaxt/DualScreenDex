package com.enrpau.dualscreendex.parser.dataset.moves

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
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
import com.enrpau.dualscreendex.parser.resolution.exactProfileEligibility

/** Resolves one typed Gen III move-details table without offset or discovery-order heuristics. */
class MoveDetailsResolver(
    private val codec: MoveDetailsTableDecoder = MoveDetailsCodec(),
) {
    fun resolve(
        session: RomAnalysisSession,
        semanticDomain: MoveDetailsSemanticDomain,
        selectedLayout: MoveDetailsTableLayout? = null,
        directCompiledConsumerLayouts: Collection<MoveDetailsTableLayout> = emptyList(),
        publishedLayouts: Collection<MoveDetailsTableLayout> = emptyList(),
        compiledLayouts: Collection<MoveDetailsTableLayout> = emptyList(),
        inheritedLayouts: Collection<MoveDetailsTableLayout> = emptyList(),
    ): DatasetResolution<ResolvedMoveDetailsLayout> {
        if (selectedLayout != null) {
            if (selectedLayout.count != semanticDomain.tableRowCount) {
                return unavailable(
                    observed = 1,
                    reason = "selected move-details cardinality ${selectedLayout.count} does not match semantic domain " +
                        semanticDomain.tableRowCount,
                )
            }
            val source = if (selectedLayout == exactProfileLayout(session)) {
                CandidateSource.EXACT_PROFILE
            } else {
                CandidateSource.INHERITED_FAMILY_LAYOUT
            }
            return when (
                val assessment = assess(
                    session = session,
                    semanticDomain = semanticDomain,
                    proposal = Proposal(selectedLayout, source),
                    referenceIndex = null,
                )
            ) {
                is Assessment.Candidate -> CandidateSelector.select(
                    session = session,
                    kind = DatasetKind.MOVE_DATA,
                    candidates = sequenceOf(assessment.value),
                )
                is Assessment.ExtentBudget -> budgetExceeded(
                    BudgetKind.EXTENT,
                    assessment.observed,
                    assessment.limit,
                    complete = true,
                    reason = assessment.reason,
                )
                Assessment.Rejected -> unavailable(1, "selected move-details layout failed its typed codec")
            }
        }
        val exactSnapshot = session.exactProfileSnapshot?.tables?.moveData
        if (exactSnapshot != null) {
            val exact = exactProfileLayout(session) ?: return unavailable(
                observed = 1,
                reason = "matching exact profile move-details layout uses an unsupported or inconsistent ABI",
            )
            if (exact.count != semanticDomain.tableRowCount) {
                return unavailable(
                    observed = 1,
                    reason = "exact move-details cardinality ${exact.count} does not match semantic domain " +
                        semanticDomain.tableRowCount,
                )
            }
            return when (
                val assessment = assess(
                    session = session,
                    semanticDomain = semanticDomain,
                    proposal = Proposal(exact, CandidateSource.EXACT_PROFILE),
                    referenceIndex = null,
                )
            ) {
                is Assessment.Candidate -> CandidateSelector.select(
                    session = session,
                    kind = DatasetKind.MOVE_DATA,
                    candidates = sequenceOf(assessment.value),
                )
                is Assessment.ExtentBudget -> budgetExceeded(
                    BudgetKind.EXTENT,
                    assessment.observed,
                    assessment.limit,
                    complete = true,
                    reason = assessment.reason,
                )
                Assessment.Rejected -> unavailable(1, "matching exact profile move-details bytes are not plausible")
            }
        }

        val proposals = sequence {
            directCompiledConsumerLayouts.forEach {
                yield(Proposal(it, CandidateSource.DIRECT_COMPILED_CONSUMER))
            }
            publishedLayouts.forEach { yield(Proposal(it, CandidateSource.PUBLISHED_HEADER)) }
            compiledLayouts.forEach { yield(Proposal(it, CandidateSource.COMPILED_REFERENCE)) }
            inheritedLayouts.forEach { yield(Proposal(it, CandidateSource.INHERITED_FAMILY_LAYOUT)) }
        }
        val iterator = proposals.iterator()
        if (!iterator.hasNext()) return unavailable(0, "no Gen III move-details candidates were proposed")

        val referenceRequired = directCompiledConsumerLayouts.isNotEmpty() ||
            publishedLayouts.isNotEmpty() || compiledLayouts.isNotEmpty()
        val referenceIndex = if (referenceRequired) {
            session.gbaReferenceIndex
                ?: return unavailable(0, "Gen III move-details resolution requires a GBA analysis session")
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

        val candidates = mutableListOf<DatasetCandidate<ResolvedMoveDetailsLayout>>()
        val roots = linkedSetOf<Long>()
        var consumedWork = 0L
        while (iterator.hasNext()) {
            val proposal = iterator.next()
            if (consumedWork == session.limits.maxProbeWorkPerDataset.toLong()) {
                return budgetExceeded(
                    BudgetKind.PROBE_WORK,
                    consumedWork + 1L,
                    session.limits.maxProbeWorkPerDataset.toLong(),
                    complete = false,
                    reason = "Gen III move-details probe-work budget exceeded " +
                        "(${consumedWork + 1L} > ${session.limits.maxProbeWorkPerDataset})",
                )
            }
            consumedWork++

            if (roots.add(proposal.layout.offset) && roots.size > session.limits.maxProbeRootsPerDataset) {
                return budgetExceeded(
                    BudgetKind.PROBE_ROOTS,
                    roots.size.toLong(),
                    session.limits.maxProbeRootsPerDataset.toLong(),
                    complete = false,
                    reason = "Gen III move-details probe-root budget exceeded " +
                        "(${roots.size} > ${session.limits.maxProbeRootsPerDataset})",
                )
            }
            if (proposal.layout.count != semanticDomain.tableRowCount) continue

            when (val assessment = assess(session, semanticDomain, proposal, referenceIndex)) {
                is Assessment.Candidate -> {
                    if (candidates.size == session.limits.maxCandidatesPerDataset) {
                        return budgetExceeded(
                            BudgetKind.CANDIDATES,
                            (candidates.size + 1).toLong(),
                            session.limits.maxCandidatesPerDataset.toLong(),
                            complete = false,
                            reason = "Gen III move-details candidate budget exceeded " +
                                "(${candidates.size + 1} > ${session.limits.maxCandidatesPerDataset})",
                        )
                    }
                    candidates += assessment.value
                }
                is Assessment.ExtentBudget -> return budgetExceeded(
                    BudgetKind.EXTENT,
                    assessment.observed,
                    assessment.limit,
                    complete = true,
                    reason = assessment.reason,
                )
                Assessment.Rejected -> Unit
            }
        }

        if (candidates.isEmpty()) {
            return unavailable(
                observed = consumedWork.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                reason = "no proposed Gen III move-details layout passed bounded semantic validation",
            )
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
                    compareBy<DatasetCandidate<ResolvedMoveDetailsLayout>>(
                        { it.layoutIdentity.value },
                        { it.source.name },
                        { it.identity.value },
                    ),
                )
                .distinctBy { it.identity to it.source }
            return DatasetResolution.Ambiguous(
                kind = DatasetKind.MOVE_DATA,
                candidates = diagnostics,
            )
        }
        return CandidateSelector.select(
            session = session,
            kind = DatasetKind.MOVE_DATA,
            candidates = candidates.asSequence(),
        )
    }

    private fun assess(
        session: RomAnalysisSession,
        semanticDomain: MoveDetailsSemanticDomain,
        proposal: Proposal,
        referenceIndex: GbaReferenceIndex?,
    ): Assessment {
        val decoded = when (val outcome = codec.decode(session, proposal.layout)) {
            is MoveDetailsTableOutcome.Decoded -> outcome
            is MoveDetailsTableOutcome.Rejected -> return Assessment.Rejected
            is MoveDetailsTableOutcome.ExtentBudgetExceeded -> return Assessment.ExtentBudget(
                observed = outcome.observedBytes,
                limit = outcome.limitBytes,
                reason = outcome.reason,
            )
        }
        val activeRows = semanticDomain.activeRowIndices.map(decoded.rows::get)
        val activeCovered = activeRows.count { it is MoveDetailsRowOutcome.Decoded }
        if (activeCovered != semanticDomain.expectedRecords) return Assessment.Rejected

        val root = proposal.layout.offset.toInt()
        val reference = referenceIndex?.target(root)
        val compiledSites = compiledSites(session, reference)
        val malformed = decoded.rows.filterIsInstance<MoveDetailsRowOutcome.Malformed>()
        val evidenceUnavailable = reference?.siteEvidenceUnavailableReason
        val reasons = buildList {
            if (malformed.isNotEmpty()) {
                add(
                    CandidateReason(
                        CandidateReasonKind.ANOMALY,
                        "${malformed.size} inactive move-detail row(s) are structurally malformed",
                    ),
                )
            }
            if (evidenceUnavailable != null) {
                add(CandidateReason(CandidateReasonKind.ANOMALY, evidenceUnavailable))
            }
            compiledSites.overflowReason?.let {
                add(CandidateReason(CandidateReasonKind.ANOMALY, it))
            }
        }
        val resolved = ResolvedMoveDetailsLayout(proposal.layout, decoded.rows)
        val eligibility = if (proposal.source == CandidateSource.EXACT_PROFILE) {
            session.exactProfileEligibility()
        } else {
            CandidateEligibility.validated(proposal.source)
        }
        return Assessment.Candidate(
            DatasetCandidate(
                identity = CandidateIdentity(proposal.layout.layoutIdentity.value),
                kind = DatasetKind.MOVE_DATA,
                layout = resolved,
                source = proposal.source,
                strength = CandidateStrength(
                    semanticCoverage = EvidenceCoverage(activeCovered, semanticDomain.expectedRecords),
                    structuralCoverage = EvidenceCoverage(
                        covered = decoded.rows.size - malformed.size,
                        expected = decoded.rows.size,
                    ),
                    compiledReferenceCount = reference?.count ?: 0,
                    datasetQuality = decoded.rows.count { it is MoveDetailsRowOutcome.Decoded },
                ),
                diagnosticOffset = proposal.layout.offset.toInt(),
                diagnosticLabel = proposal.layout.layoutIdentity.value,
                eligibility = eligibility,
                provenance = CandidateProvenance(
                    reasons = reasons,
                    validatorReviewRecommended = malformed.isNotEmpty() ||
                        evidenceUnavailable != null || compiledSites.budgetExceeded,
                    compiledReferenceSites = compiledSites,
                ),
            ),
        )
    }

    private fun exactProfileLayout(session: RomAnalysisSession): MoveDetailsTableLayout? {
        val exact = session.exactProfileSnapshot?.tables?.moveData ?: return null
        val abi = MoveDetailsAbi.entries.singleOrNull {
            it.recordSize == exact.recordSize && it.tableRecordFormat == exact.format
        } ?: return null
        if (exact.stride != null && exact.stride != exact.recordSize) return null
        if (exact.variableLength || exact.valuesArePointers) return null
        return MoveDetailsTableLayout(
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
    ): DatasetResolution.Unavailable<ResolvedMoveDetailsLayout> = DatasetResolution.Unavailable(
        kind = DatasetKind.MOVE_DATA,
        observedCandidates = observed,
        reason = reason,
    )

    private fun budgetExceeded(
        kind: BudgetKind,
        observed: Long,
        limit: Long,
        complete: Boolean,
        reason: String,
    ): DatasetResolution.BudgetExceeded<ResolvedMoveDetailsLayout> = DatasetResolution.BudgetExceeded(
        kind = DatasetKind.MOVE_DATA,
        budgetKind = kind,
        observed = observed,
        limit = limit,
        observationComplete = complete,
        reason = reason,
    )

    private data class Proposal(
        val layout: MoveDetailsTableLayout,
        val source: CandidateSource,
    )

    private sealed interface Assessment {
        data class Candidate(val value: DatasetCandidate<ResolvedMoveDetailsLayout>) : Assessment
        data class ExtentBudget(val observed: Long, val limit: Long, val reason: String) : Assessment
        data object Rejected : Assessment
    }

    companion object {
        private val DEFAULT = MoveDetailsResolver()

        fun resolve(
            session: RomAnalysisSession,
            semanticDomain: MoveDetailsSemanticDomain,
            selectedLayout: MoveDetailsTableLayout? = null,
            directCompiledConsumerLayouts: Collection<MoveDetailsTableLayout> = emptyList(),
            publishedLayouts: Collection<MoveDetailsTableLayout> = emptyList(),
            compiledLayouts: Collection<MoveDetailsTableLayout> = emptyList(),
            inheritedLayouts: Collection<MoveDetailsTableLayout> = emptyList(),
        ): DatasetResolution<ResolvedMoveDetailsLayout> = DEFAULT.resolve(
            session = session,
            semanticDomain = semanticDomain,
            selectedLayout = selectedLayout,
            directCompiledConsumerLayouts = directCompiledConsumerLayouts,
            publishedLayouts = publishedLayouts,
            compiledLayouts = compiledLayouts,
            inheritedLayouts = inheritedLayouts,
        )
    }
}
