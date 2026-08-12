package com.enrpau.dualscreendex.parser.resolution

import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability

/** The sole policy boundary from typed dataset resolution into the public capability model. */
object CapabilityEvidenceAdapter {
    fun <TLayout : ImmutableDatasetLayout<TLayout>> adapt(
        capability: RomCapability,
        resolution: DatasetResolution<TLayout>,
    ): CapabilityEvidence = when (resolution) {
        is DatasetResolution.Resolved -> selected(
            capability = capability,
            candidate = resolution.candidate,
            status = CapabilityStatus.AVAILABLE,
            resolutionReasons = emptyList(),
        )

        is DatasetResolution.Partial -> selected(
            capability = capability,
            candidate = resolution.candidate,
            status = CapabilityStatus.PARTIAL,
            resolutionReasons = resolution.reasons,
        )

        is DatasetResolution.Ambiguous -> ambiguous(capability, resolution.candidates)
        is DatasetResolution.Unavailable -> unavailable(capability, resolution)
        is DatasetResolution.BudgetExceeded -> budgetExceeded(capability, resolution)
    }

    private fun <TLayout : ImmutableDatasetLayout<TLayout>> selected(
        capability: RomCapability,
        candidate: DatasetCandidate<TLayout>,
        status: CapabilityStatus,
        resolutionReasons: List<String>,
    ): CapabilityEvidence {
        val structural = candidate.strength.structuralCoverage
        val semantic = candidate.strength.semanticCoverage
        val provenance = candidate.provenance
        val explicitReview = provenance.validatorReviewRecommended ||
            provenance.reasons.any { it.kind != CandidateReasonKind.INFORMATION } ||
            provenance.compiledReferenceSites.budgetExceeded
        val incompleteCoverageReview = when {
            semantic != null -> semantic.covered < semantic.expected
            else -> structural.covered < structural.expected
        }
        return CapabilityEvidence(
            capability = capability,
            compatible = true,
            confidence = confidence(candidate.strength),
            offset = candidate.diagnosticOffset,
            count = structural.expected,
            reasons = selectedReasons(candidate, resolutionReasons),
            status = status,
            validRecords = structural.covered,
            totalRecords = structural.expected,
            reviewStatus = if (explicitReview || incompleteCoverageReview) {
                CapabilityReviewStatus.MANUAL_REVIEW
            } else {
                CapabilityReviewStatus.NONE
            },
            coveredRecords = semantic?.covered,
            expectedRecords = semantic?.expected,
            incompleteRecords = semantic?.let { it.expected - it.covered },
            validatorReviewRecommended = provenance.validatorReviewRecommended,
        )
    }

    private fun <TLayout : ImmutableDatasetLayout<TLayout>> ambiguous(
        capability: RomCapability,
        candidates: List<DatasetCandidate<TLayout>>,
    ): CapabilityEvidence {
        val ordered = candidates.sortedWith(
            compareBy<DatasetCandidate<TLayout>>(
                { it.identity.value },
                { it.layoutIdentity.value },
                { it.diagnosticOffset ?: Int.MAX_VALUE },
            ),
        )
        val strengths = ordered.map { it.strength }
        val structural = consensus(strengths.map { it.structuralCoverage })
        val semantic = consensus(strengths.map { it.semanticCoverage })
        val candidateReasons = ordered.flatMap(::ambiguousCandidateReasons)
        return CapabilityEvidence(
            capability = capability,
            compatible = false,
            confidence = strengths.maxOfOrNull(::confidence) ?: 0.0,
            count = structural?.expected,
            reasons = listOf("multiple equally supported dataset candidates") + candidateReasons,
            status = CapabilityStatus.AMBIGUOUS,
            validRecords = structural?.covered,
            totalRecords = structural?.expected,
            reviewStatus = CapabilityReviewStatus.MANUAL_REVIEW,
            coveredRecords = semantic?.covered,
            expectedRecords = semantic?.expected,
            incompleteRecords = semantic?.let { it.expected - it.covered },
            validatorReviewRecommended = ordered.any { it.provenance.validatorReviewRecommended },
        )
    }

    private fun <TLayout : ImmutableDatasetLayout<TLayout>> unavailable(
        capability: RomCapability,
        resolution: DatasetResolution.Unavailable<TLayout>,
    ): CapabilityEvidence = CapabilityEvidence(
        capability = capability,
        compatible = false,
        confidence = 0.0,
        reasons = resolution.reasons + "observed candidates: ${resolution.observedCandidates}",
        status = CapabilityStatus.NOT_FOUND,
    )

    private fun <TLayout : ImmutableDatasetLayout<TLayout>> budgetExceeded(
        capability: RomCapability,
        resolution: DatasetResolution.BudgetExceeded<TLayout>,
    ): CapabilityEvidence {
        val observation = if (resolution.observationComplete) "exactly" else "at least"
        return CapabilityEvidence(
            capability = capability,
            compatible = false,
            confidence = 0.0,
            reasons = listOf(
                "budget kind: ${resolution.budgetKind.name}",
                "budget observation: $observation ${resolution.observed} units " +
                    "(limit ${resolution.limit})",
                resolution.reason,
            ),
            status = CapabilityStatus.NOT_FOUND,
            reviewStatus = CapabilityReviewStatus.MANUAL_REVIEW,
        )
    }

    private fun confidence(strength: CandidateStrength): Double {
        val coverage = strength.semanticCoverage ?: strength.structuralCoverage
        return coverage.covered.toDouble() / coverage.expected.toDouble()
    }

    private fun <TLayout : ImmutableDatasetLayout<TLayout>> selectedReasons(
        candidate: DatasetCandidate<TLayout>,
        resolutionReasons: List<String>,
    ): List<String> = buildList {
        add("candidate identity: ${candidate.identity.value}")
        add("candidate source: ${candidate.source.name}")
        if (candidate.strength.compiledReferenceCount > 0) {
            add("compiled reference count: ${candidate.strength.compiledReferenceCount}")
        }
        addAll(orderedProvenanceReasons(candidate.provenance))
        addAll(resolutionReasons.sorted())
    }.distinct()

    private fun <TLayout : ImmutableDatasetLayout<TLayout>> ambiguousCandidateReasons(
        candidate: DatasetCandidate<TLayout>,
    ): List<String> = buildList {
        val location = candidate.diagnosticOffset?.let { " at 0x${it.toString(16)}" }.orEmpty()
        add("candidate ${candidate.identity.value} source ${candidate.source.name}$location")
        if (candidate.strength.compiledReferenceCount > 0) {
            add(
                "candidate ${candidate.identity.value} compiled reference count: " +
                    candidate.strength.compiledReferenceCount,
            )
        }
        addAll(orderedProvenanceReasons(candidate.provenance))
    }

    private fun orderedProvenanceReasons(provenance: CandidateProvenance): List<String> = buildList {
        provenance.reasons
            .sortedWith(compareBy<CandidateReason>({ it.kind.ordinal }, { it.message }))
            .mapTo(this) { reason ->
                "provenance ${reason.kind.name.lowercase()}: ${reason.message}"
            }
        provenance.compiledReferenceSites.let { sites ->
            if (sites.budgetExceeded) {
                add(
                    "compiled reference site budget: observed ${sites.observedSites}, " +
                        "limit ${sites.limitSites}",
                )
                add(requireNotNull(sites.overflowReason))
            } else if (sites.offsets.isNotEmpty()) {
                add(
                    "compiled reference sites: " +
                        sites.offsets.joinToString { offset -> "0x${offset.toString(16)}" },
                )
            }
        }
    }

    private fun <T> consensus(values: List<T>): T? = values
        .firstOrNull()
        ?.takeIf { first -> values.all { it == first } }
}
