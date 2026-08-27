package com.enrpau.dualscreendex.parser.resolution

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession

object CandidateSelector {
    fun <TLayout : ImmutableDatasetLayout<TLayout>> select(
        session: RomAnalysisSession,
        kind: DatasetKind,
        candidates: Sequence<DatasetCandidate<TLayout>>,
        structuralAnchorPolicy: StructuralAnchorPolicy = StructuralAnchorPolicy.denyAll(),
    ): DatasetResolution<TLayout> {
        session.cancellation.throwIfCancellationRequested()
        val retained = ArrayList<DatasetCandidate<TLayout>>()
        val iterator = candidates.iterator()
        while (iterator.hasNext()) {
            session.cancellation.throwIfCancellationRequested()
            val candidate = iterator.next()
            if (retained.size == session.limits.maxCandidatesPerDataset) {
                return DatasetResolution.BudgetExceeded(
                    kind = kind,
                    budgetKind = BudgetKind.CANDIDATES,
                    observed = (retained.size + 1).toLong(),
                    limit = session.limits.maxCandidatesPerDataset.toLong(),
                    observationComplete = false,
                    reason = "dataset candidate budget exceeded (observed at least " +
                        "${retained.size + 1}, limit ${session.limits.maxCandidatesPerDataset})",
                )
            }
            require(candidate.kind == kind) {
                "candidate dataset kind ${candidate.kind} does not match requested kind $kind"
            }
            retained += candidate
        }

        session.cancellation.throwIfCancellationRequested()
        val candidatesByIdentity = retained.groupBy { it.identity }
        val conflictingIdentity = candidatesByIdentity
            .asSequence()
            .filter { (_, duplicates) -> duplicates.map { it.layoutIdentity }.distinct().size > 1 }
            .map { it.key }
            .minByOrNull { it.value }
        if (conflictingIdentity != null) {
            return DatasetResolution.Unavailable(
                kind = kind,
                observedCandidates = retained.size,
                reason = "candidate identity $conflictingIdentity maps to conflicting layouts",
            )
        }

        val deduplicated = candidatesByIdentity
            .values
            .map { duplicates ->
                mergeDuplicates(session, structuralAnchorPolicy, duplicates)
            }
        val eligible = deduplicated.filter { candidate ->
            authority(session, structuralAnchorPolicy, candidate) != null
        }
        if (eligible.isEmpty()) {
            val rejectionReasons = deduplicated.map { candidate ->
                rejectionReason(session, structuralAnchorPolicy, candidate)
            }
            return DatasetResolution.Unavailable(
                kind = kind,
                observedCandidates = retained.size,
                reasons = if (retained.isEmpty()) {
                    listOf("no candidates discovered")
                } else {
                    rejectionReasons.ifEmpty {
                        listOf("no candidate has validated evidence for automatic selection")
                    }
                },
            )
        }

        val best = eligible.maxWithOrNull { left, right ->
            compareSubstantive(session, structuralAnchorPolicy, left, right)
        }!!
        val tied = eligible
            .filter { compareSubstantive(session, structuralAnchorPolicy, it, best) == 0 }
            .sortedWith(diagnosticComparator())

        return if (tied.size > 1) {
            DatasetResolution.Ambiguous(
                kind,
                tied,
            )
        } else {
            val winner = tied.single()
            val partialReasons = partialReasons(winner.strength)
            if (partialReasons.isEmpty()) {
                DatasetResolution.Resolved(kind, winner)
            } else {
                DatasetResolution.Partial(
                    kind,
                    winner,
                    partialReasons,
                )
            }
        }
    }

    private fun <TLayout : ImmutableDatasetLayout<TLayout>> mergeDuplicates(
        session: RomAnalysisSession,
        structuralAnchorPolicy: StructuralAnchorPolicy,
        candidates: List<DatasetCandidate<TLayout>>,
    ): DatasetCandidate<TLayout> {
        val representative = candidates.reduce { current, candidate ->
            when {
                compareSubstantive(session, structuralAnchorPolicy, candidate, current) > 0 -> candidate
                compareSubstantive(session, structuralAnchorPolicy, candidate, current) < 0 -> current
                representativeComparator<TLayout>().compare(candidate, current) < 0 -> candidate
                else -> current
            }
        }
        return representative.withProvenance(
            CandidateProvenance.merge(
                candidates.map { it.provenance },
                session.limits.maxCompiledReferenceSitesPerCandidate,
            ),
        )
    }

    private fun partialReasons(strength: CandidateStrength): List<String> = buildList {
        strength.semanticCoverage
            ?.takeIf { it.covered < it.expected }
            ?.let { add("semantic coverage is ${it.covered}/${it.expected}") }
        strength.structuralCoverage
            .takeIf { it.covered < it.expected }
            ?.let { add("structural coverage is ${it.covered}/${it.expected}") }
    }

    private fun <TLayout : ImmutableDatasetLayout<TLayout>> compareSubstantive(
        session: RomAnalysisSession,
        structuralAnchorPolicy: StructuralAnchorPolicy,
        left: DatasetCandidate<TLayout>,
        right: DatasetCandidate<TLayout>,
    ): Int {
        compareValues(
            authority(session, structuralAnchorPolicy, left),
            authority(session, structuralAnchorPolicy, right),
        ).takeIf { it != 0 }?.let { return it }
        compareNullableCoverage(left.strength.semanticCoverage, right.strength.semanticCoverage)
            .takeIf { it != 0 }
            ?.let { return it }
        left.strength.structuralCoverage.compareTo(right.strength.structuralCoverage)
            .takeIf { it != 0 }
            ?.let { return it }
        left.strength.compiledReferenceCount.compareTo(right.strength.compiledReferenceCount)
            .takeIf { it != 0 }
            ?.let { return it }
        return left.strength.datasetQuality.compareTo(right.strength.datasetQuality)
    }

    private fun compareNullableCoverage(
        left: EvidenceCoverage?,
        right: EvidenceCoverage?,
    ): Int = when {
        left == null && right == null -> 0
        left == null -> -1
        right == null -> 1
        else -> left.compareTo(right)
    }

    private fun <TLayout : ImmutableDatasetLayout<TLayout>> authority(
        session: RomAnalysisSession,
        structuralAnchorPolicy: StructuralAnchorPolicy,
        candidate: DatasetCandidate<TLayout>,
    ): Int? {
        val validated = candidate.eligibility as? CandidateEligibility.Validated ?: return null
        return when (candidate.source) {
            CandidateSource.EXACT_PROFILE -> 6.takeIf {
                validated.exactProfileIdentity != null &&
                    validated.exactProfileIdentity == session.exactProfileIdentity
            }
            CandidateSource.DIRECT_COMPILED_CONSUMER -> 5
            CandidateSource.PUBLISHED_HEADER -> 4.takeIf {
                candidate.strength.compiledReferenceCount > 0
            }
            CandidateSource.COMPILED_REFERENCE -> 3.takeIf {
                candidate.strength.compiledReferenceCount > 0
            }
            CandidateSource.INHERITED_FAMILY_LAYOUT -> 2
            CandidateSource.STRUCTURAL_ANCHOR -> 1.takeIf {
                structuralAnchorPolicy.permits(candidate.kind)
            }
        }
    }

    private fun <TLayout : ImmutableDatasetLayout<TLayout>> rejectionReason(
        session: RomAnalysisSession,
        structuralAnchorPolicy: StructuralAnchorPolicy,
        candidate: DatasetCandidate<TLayout>,
    ): String = when (val eligibility = candidate.eligibility) {
        is CandidateEligibility.Ineligible -> eligibility.reason
        is CandidateEligibility.Validated -> when (candidate.source) {
            CandidateSource.EXACT_PROFILE ->
                "exact-profile evidence does not match this ROM analysis session"
            CandidateSource.PUBLISHED_HEADER ->
                "published-header evidence lacks compiled-reference corroboration"
            CandidateSource.COMPILED_REFERENCE ->
                "compiled-reference evidence has no independent reference"
            CandidateSource.STRUCTURAL_ANCHOR -> if (!structuralAnchorPolicy.permits(candidate.kind)) {
                "structural-anchor evidence is not permitted for ${candidate.kind}"
            } else {
                "structural-anchor evidence was not eligible"
            }
            CandidateSource.DIRECT_COMPILED_CONSUMER,
            CandidateSource.INHERITED_FAMILY_LAYOUT,
            -> "validated ${candidate.source} evidence was not eligible"
        }
    }

    private fun <TLayout : ImmutableDatasetLayout<TLayout>> representativeComparator():
        Comparator<DatasetCandidate<TLayout>> =
        compareBy<DatasetCandidate<TLayout>>(
            { it.diagnosticOffset ?: Int.MAX_VALUE },
            { it.diagnosticLabel },
            { it.source.name },
            { it.layoutIdentity.value },
            { it.identity.value },
        )

    private fun <TLayout : ImmutableDatasetLayout<TLayout>> diagnosticComparator():
        Comparator<DatasetCandidate<TLayout>> =
        representativeComparator()
}
