package com.enrpau.dualscreendex.parser.dataset.acquisition

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
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import com.enrpau.dualscreendex.parser.resolution.EvidenceCoverage

class MoveAcquisitionResolver(
    private val codec: MoveAcquisitionCodec = MoveAcquisitionCodec(),
) {
    fun resolve(
        session: RomAnalysisSession,
        method: AcquisitionMethod,
        domain: AcquisitionSemanticDomain,
        probes: Collection<AcquisitionProbe>,
    ): DatasetResolution<ResolvedAcquisitionLayout> {
        val kind = method.datasetKind()
        if (probes.isEmpty()) return DatasetResolution.Unavailable(kind, 0, "no acquisition layouts were supplied")
        val inputProposalCount = probes.size.toLong()
        if (inputProposalCount > session.limits.maxProbeWorkPerDataset.toLong()) {
            return budget(
                kind,
                BudgetKind.PROBE_WORK,
                inputProposalCount,
                session.limits.maxProbeWorkPerDataset.toLong(),
                true,
                "acquisition input-proposal work budget exceeded " +
                    "($inputProposalCount > ${session.limits.maxProbeWorkPerDataset})",
            )
        }
        val ledger = AcquisitionResolutionLedger(session.limits)
        ledger.work(inputProposalCount)

        val normalized = probes
            .distinctBy { Triple(it.layout.layoutIdentity, it.source, it.directSitesByRoot) }
            .sortedWith(compareBy({ it.layout.layoutIdentity.value }, { it.source.ordinal }))
        val roots = linkedSetOf<Long>()
        normalized.forEach { probe ->
            probe.layout.abi.physicalRoots.forEach { root ->
                if (roots.add(root) && roots.size > session.limits.maxProbeRootsPerDataset) {
                    return budget(
                        kind,
                        BudgetKind.PROBE_ROOTS,
                        roots.size.toLong(),
                        session.limits.maxProbeRootsPerDataset.toLong(),
                        false,
                        "acquisition probe-root budget exceeded " +
                            "(${roots.size} > ${session.limits.maxProbeRootsPerDataset})",
                    )
                }
            }
        }

        val needsIndex = normalized.any { probe ->
            probe.source == CandidateSource.COMPILED_REFERENCE ||
                probe.source == CandidateSource.PUBLISHED_HEADER
        }
        val index = if (needsIndex) {
            session.gbaReferenceIndex ?: return DatasetResolution.Unavailable(
                kind,
                0,
                "compiled acquisition roots require a GBA analysis session",
            )
        } else {
            null
        }
        index?.overflowReason?.let { reason ->
            return budget(
                kind,
                BudgetKind.REFERENCE_TARGETS,
                maxOf(index.observedTargets.toLong(), index.limitTargets.toLong() + 1L),
                index.limitTargets.toLong(),
                false,
                reason,
            )
        }

        val candidates = mutableListOf<DatasetCandidate<ResolvedAcquisitionLayout>>()
        var observedCandidates = 0L
        normalized.forEach { probe ->
            if (probe.layout.method != method) return@forEach
            val evidence = when (val result = referenceEvidence(session, probe, index)) {
                is ReferenceAssessment.Rejected -> return@forEach
                is ReferenceAssessment.Budget -> return budget(
                    kind,
                    BudgetKind.REFERENCE_SITES,
                    result.observed,
                    result.limit,
                    false,
                    result.reason,
                )
                is ReferenceAssessment.Accepted -> result
            }
            when (val decoded = codec.decode(session, probe.layout, domain, ledger)) {
                is AcquisitionTableOutcome.Rejected -> Unit
                is AcquisitionTableOutcome.ExtentBudgetExceeded -> return budget(
                    kind,
                    BudgetKind.EXTENT,
                    decoded.observedBytes,
                    decoded.limitBytes,
                    true,
                    decoded.reason,
                )
                is AcquisitionTableOutcome.WorkBudgetExceeded -> return budget(
                    kind,
                    BudgetKind.PROBE_WORK,
                    decoded.observedWork,
                    decoded.limitWork,
                    false,
                    decoded.reason,
                )
                is AcquisitionTableOutcome.Decoded -> {
                    val candidate = assess(session, method, domain, probe, decoded.resolved, evidence)
                        ?: return@forEach
                    observedCandidates++
                    if (observedCandidates > session.limits.maxCandidatesPerDataset.toLong()) {
                        return budget(
                            kind,
                            BudgetKind.CANDIDATES,
                            observedCandidates,
                            session.limits.maxCandidatesPerDataset.toLong(),
                            false,
                            "acquisition candidate budget exceeded " +
                                "($observedCandidates > ${session.limits.maxCandidatesPerDataset})",
                        )
                    }
                    candidates += candidate
                }
            }
        }

        return CandidateSelector.select(session, kind, candidates.asSequence())
    }

    private fun assess(
        session: RomAnalysisSession,
        method: AcquisitionMethod,
        domain: AcquisitionSemanticDomain,
        probe: AcquisitionProbe,
        resolved: ResolvedAcquisitionLayout,
        evidence: ReferenceAssessment.Accepted,
    ): DatasetCandidate<ResolvedAcquisitionLayout>? {
        val rowsBySpecies = resolved.rows.associateBy(AcquisitionRowOutcome::speciesId)
        val semanticRows = domain.speciesIds.mapNotNull(rowsBySpecies::get)
        val semanticValid = semanticRows.count { it !is AcquisitionRowOutcome.Malformed }
        if (semanticRows.size != domain.speciesIds.size || semanticValid == 0) return null
        val structuralValid = resolved.rows.count { it !is AcquisitionRowOutcome.Malformed }
        if (structuralValid == 0) return null
        val malformed = resolved.rows.filterIsInstance<AcquisitionRowOutcome.Malformed>()
        val unavailableReferenceReasons = evidence.references
            .mapNotNull(GbaTargetReferenceEvidence::siteEvidenceUnavailableReason)
            .distinct()
            .sorted()
        val reasons = buildList {
            malformed.forEach { row ->
                add(
                    CandidateReason(
                        CandidateReasonKind.ANOMALY,
                        "species ${row.speciesId}: ${row.reasons.joinToString()}",
                    ),
                )
            }
            unavailableReferenceReasons.forEach {
                add(CandidateReason(CandidateReasonKind.ANOMALY, it))
            }
        }
        val linkCount = resolved.acquisitionsBySpecies.values.sumOf { it.size.toLong() }
        val quality = minOf(Int.MAX_VALUE.toLong(), structuralValid.toLong() + linkCount).toInt()
        return DatasetCandidate(
            identity = CandidateIdentity(resolved.layoutIdentity.value),
            kind = method.datasetKind(),
            layout = resolved,
            source = probe.source,
            strength = CandidateStrength(
                semanticCoverage = EvidenceCoverage(semanticValid, domain.speciesIds.size),
                structuralCoverage = EvidenceCoverage(structuralValid, resolved.rows.size),
                compiledReferenceCount = evidence.referenceCount,
                datasetQuality = quality,
            ),
            diagnosticOffset = probe.layout.abi.physicalRoots.firstOrNull()?.takeIf {
                it in 0..Int.MAX_VALUE.toLong()
            }?.toInt(),
            diagnosticLabel = resolved.layoutIdentity.value,
            eligibility = eligibility(session, probe.source),
            provenance = CandidateProvenance(
                reasons = reasons,
                validatorReviewRecommended = malformed.isNotEmpty() || unavailableReferenceReasons.isNotEmpty(),
                compiledReferenceSites = evidence.compiledSites,
            ),
        )
    }

    private fun referenceEvidence(
        session: RomAnalysisSession,
        probe: AcquisitionProbe,
        index: com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex?,
    ): ReferenceAssessment = when (probe.source) {
        CandidateSource.DIRECT_COMPILED_CONSUMER -> {
            val proof = probe.directProof
            if (proof == null || !proof.matches(session, probe.layout)) {
                return ReferenceAssessment.Rejected
            }
            val sites = CompiledReferenceSites.of(
                probe.directInstructionSites,
                session.limits.maxCompiledReferenceSitesPerCandidate,
            )
            if (sites.budgetExceeded) {
                ReferenceAssessment.Budget(
                    sites.observedSites,
                    sites.limitSites.toLong(),
                    requireNotNull(sites.overflowReason),
                )
            } else {
                ReferenceAssessment.Accepted(
                    references = emptyList(),
                    compiledSites = sites,
                    referenceCount = probe.directInstructionSites.size,
                )
            }
        }
        CandidateSource.COMPILED_REFERENCE,
        CandidateSource.PUBLISHED_HEADER,
        -> {
            val references = probe.layout.abi.physicalRoots.map { root ->
                if (root !in 0..Int.MAX_VALUE.toLong()) return ReferenceAssessment.Rejected
                requireNotNull(index).target(root.toInt()) ?: return ReferenceAssessment.Rejected
            }
            if (references.any { it.count <= 0 }) return ReferenceAssessment.Rejected
            val overflow = references.filter(GbaTargetReferenceEvidence::siteBudgetExceeded)
            if (overflow.isNotEmpty()) {
                ReferenceAssessment.Budget(
                    overflow.maxOf { it.observedSites.toLong() },
                    overflow.minOf { it.limitSites.toLong() },
                    overflow.mapNotNull { it.overflowReason }.distinct().sorted().joinToString("; "),
                )
            } else {
                val rootBoundSites = mutableSetOf<Int>()
                references.forEach { reference ->
                    reference.instructionSites.forEach { site ->
                        if (!rootBoundSites.add(site)) return ReferenceAssessment.Rejected
                    }
                }
                val sites = CompiledReferenceSites.of(
                    references.asSequence().flatMap { it.instructionSites.asSequence() },
                    session.limits.maxCompiledReferenceSitesPerCandidate,
                )
                if (sites.budgetExceeded) {
                    ReferenceAssessment.Budget(
                        sites.observedSites,
                        sites.limitSites.toLong(),
                        requireNotNull(sites.overflowReason),
                    )
                } else {
                    ReferenceAssessment.Accepted(
                        references,
                        sites,
                        references.fold(0) { total, item ->
                            (total.toLong() + item.count.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        },
                    )
                }
            }
        }
        CandidateSource.INHERITED_FAMILY_LAYOUT -> ReferenceAssessment.Accepted(
            emptyList(),
            CompiledReferenceSites.of(emptyList(), session.limits.maxCompiledReferenceSitesPerCandidate),
            0,
        )
        CandidateSource.EXACT_PROFILE -> ReferenceAssessment.Rejected
        CandidateSource.STRUCTURAL_ANCHOR -> ReferenceAssessment.Rejected
    }

    private fun eligibility(session: RomAnalysisSession, source: CandidateSource): CandidateEligibility =
        CandidateEligibility.validated(source)

    private sealed interface ReferenceAssessment {
        data class Accepted(
            val references: List<GbaTargetReferenceEvidence>,
            val compiledSites: CompiledReferenceSites,
            val referenceCount: Int,
        ) : ReferenceAssessment

        data class Budget(val observed: Long, val limit: Long, val reason: String) : ReferenceAssessment
        data object Rejected : ReferenceAssessment
    }

    private companion object {
        fun budget(
            kind: com.enrpau.dualscreendex.parser.resolution.DatasetKind,
            budgetKind: BudgetKind,
            observed: Long,
            limit: Long,
            complete: Boolean,
            reason: String,
        ): DatasetResolution.BudgetExceeded<ResolvedAcquisitionLayout> = DatasetResolution.BudgetExceeded(
            kind,
            budgetKind,
            observed,
            limit,
            complete,
            reason,
        )
    }
}
