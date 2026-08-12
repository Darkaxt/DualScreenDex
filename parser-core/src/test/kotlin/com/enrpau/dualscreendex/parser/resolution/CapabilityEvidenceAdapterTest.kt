package com.enrpau.dualscreendex.parser.resolution

import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityEvidenceAdapterTest {
    @Test
    fun resolvedPublishesRawCoverageWithoutInventingSemanticCoverage() {
        val evidence = CapabilityEvidenceAdapter.adapt(
            capability = RomCapability.EVOLUTIONS,
            resolution = DatasetResolution.Resolved(
                DatasetKind.EVOLUTIONS,
                candidate(
                    value = "resolved",
                    structural = EvidenceCoverage(12, 12),
                    semantic = null,
                    diagnosticOffset = 0x120,
                    provenance = CandidateProvenance(
                        reasons = listOf(
                            CandidateReason(CandidateReasonKind.INFORMATION, "published table root"),
                        ),
                        compiledReferenceSites = CompiledReferenceSites.of(
                            offsets = listOf(0x40, 0x20),
                            maxSites = 4,
                        ),
                    ),
                ),
            ),
        )

        assertTrue(evidence.compatible)
        assertEquals(CapabilityStatus.AVAILABLE, evidence.status)
        assertEquals(1.0, evidence.confidence, 0.0)
        assertEquals(0x120, evidence.offset)
        assertEquals(12, evidence.count)
        assertEquals(12, evidence.validRecords)
        assertEquals(12, evidence.totalRecords)
        assertNull(evidence.coveredRecords)
        assertNull(evidence.expectedRecords)
        assertNull(evidence.incompleteRecords)
        assertEquals(
            listOf(
                "candidate identity: EVOLUTIONS:resolved",
                "candidate source: COMPILED_REFERENCE",
                "compiled reference count: 1",
                "provenance information: published table root",
                "compiled reference sites: 0x20, 0x40",
            ),
            evidence.reasons,
        )
        assertEquals(CapabilityReviewStatus.NONE, evidence.reviewStatus)
        assertFalse(evidence.validatorReviewRecommended)
    }

    @Test
    fun semanticPartialPreservesBothCoverageDomainsAndValidatorIntent() {
        val evidence = CapabilityEvidenceAdapter.adapt(
            capability = RomCapability.POKEDEX_DESCRIPTIONS,
            resolution = DatasetResolution.Partial(
                DatasetKind.POKEDEX_DESCRIPTIONS,
                candidate(
                    kind = DatasetKind.POKEDEX_DESCRIPTIONS,
                    value = "semantic-partial",
                    structural = EvidenceCoverage(10, 12),
                    semantic = EvidenceCoverage(7, 9),
                    provenance = CandidateProvenance(
                        reasons = listOf(
                            CandidateReason(CandidateReasonKind.RECOVERY, "recovered a bounded pointer"),
                            CandidateReason(CandidateReasonKind.INFORMATION, "active species domain applied"),
                        ),
                        validatorReviewRecommended = true,
                    ),
                ),
                reasons = listOf("two active rows malformed"),
            ),
        )

        assertTrue(evidence.compatible)
        assertEquals(CapabilityStatus.PARTIAL, evidence.status)
        assertEquals(7.0 / 9.0, evidence.confidence, 0.0)
        assertEquals(10, evidence.validRecords)
        assertEquals(12, evidence.totalRecords)
        assertEquals(7, evidence.coveredRecords)
        assertEquals(9, evidence.expectedRecords)
        assertEquals(2, evidence.incompleteRecords)
        assertEquals(
            listOf(
                "candidate identity: POKEDEX_DESCRIPTIONS:semantic-partial",
                "candidate source: COMPILED_REFERENCE",
                "compiled reference count: 1",
                "provenance information: active species domain applied",
                "provenance recovery: recovered a bounded pointer",
                "two active rows malformed",
            ),
            evidence.reasons,
        )
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, evidence.reviewStatus)
        assertTrue(evidence.validatorReviewRecommended)
    }

    @Test
    fun structuralPartialIsReviewableButSemanticCompleteTrimmingIsNot() {
        val physicalPartial = CapabilityEvidenceAdapter.adapt(
            capability = RomCapability.EVOLUTIONS,
            resolution = DatasetResolution.Partial(
                DatasetKind.EVOLUTIONS,
                candidate(
                    value = "physical-partial",
                    structural = EvidenceCoverage(6, 8),
                    semantic = null,
                ),
                reasons = listOf("two structural slots malformed"),
            ),
        )
        val inactiveTrim = CapabilityEvidenceAdapter.adapt(
            capability = RomCapability.EVOLUTIONS,
            resolution = DatasetResolution.Partial(
                DatasetKind.EVOLUTIONS,
                candidate(
                    value = "inactive-trim",
                    structural = EvidenceCoverage(6, 8),
                    semantic = EvidenceCoverage(5, 5),
                    provenance = CandidateProvenance(
                        reasons = listOf(
                            CandidateReason(CandidateReasonKind.INFORMATION, "inactive structural slots trimmed"),
                        ),
                    ),
                ),
                reasons = listOf("raw structural coverage is incomplete"),
            ),
        )

        assertEquals(CapabilityStatus.PARTIAL, physicalPartial.status)
        assertEquals(0.75, physicalPartial.confidence, 0.0)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, physicalPartial.reviewStatus)
        assertFalse(physicalPartial.validatorReviewRecommended)

        assertEquals(CapabilityStatus.PARTIAL, inactiveTrim.status)
        assertEquals(1.0, inactiveTrim.confidence, 0.0)
        assertEquals(6, inactiveTrim.validRecords)
        assertEquals(8, inactiveTrim.totalRecords)
        assertEquals(5, inactiveTrim.coveredRecords)
        assertEquals(5, inactiveTrim.expectedRecords)
        assertEquals(0, inactiveTrim.incompleteRecords)
        assertEquals(CapabilityReviewStatus.NONE, inactiveTrim.reviewStatus)
        assertFalse(inactiveTrim.validatorReviewRecommended)
    }

    @Test
    fun typedRecoveryAndReferenceBudgetRemainReviewableWithoutMasqueradingAsValidatorIntent() {
        val evidence = CapabilityEvidenceAdapter.adapt(
            capability = RomCapability.LEARNSETS,
            resolution = DatasetResolution.Resolved(
                DatasetKind.LEVEL_UP_LEARNSETS,
                candidate(
                    kind = DatasetKind.LEVEL_UP_LEARNSETS,
                    value = "reviewable",
                    structural = EvidenceCoverage(10, 10),
                    semantic = EvidenceCoverage(10, 10),
                    provenance = CandidateProvenance(
                        reasons = listOf(
                            CandidateReason(CandidateReasonKind.ANOMALY, "selector consumer was unusual"),
                        ),
                        compiledReferenceSites = CompiledReferenceSites.overflowed(
                            observedSites = 5,
                            limitSites = 4,
                            reason = "compiled-site budget exhausted",
                        ),
                    ),
                ),
            ),
        )

        assertEquals(CapabilityStatus.AVAILABLE, evidence.status)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, evidence.reviewStatus)
        assertFalse(evidence.validatorReviewRecommended)
        assertEquals(
            listOf(
                "candidate identity: LEVEL_UP_LEARNSETS:reviewable",
                "candidate source: COMPILED_REFERENCE",
                "compiled reference count: 1",
                "provenance anomaly: selector consumer was unusual",
                "compiled reference site budget: observed 5, limit 4",
                "compiled-site budget exhausted",
            ),
            evidence.reasons,
        )
    }

    @Test
    fun ambiguityIsDeterministicAndDoesNotSelectAPhysicalLocation() {
        val first = candidate(
            value = "first",
            structural = EvidenceCoverage(4, 5),
            semantic = EvidenceCoverage(3, 4),
            diagnosticOffset = 0x400,
            provenance = CandidateProvenance(
                reasons = listOf(CandidateReason(CandidateReasonKind.INFORMATION, "first proof")),
            ),
        )
        val second = candidate(
            value = "second",
            structural = EvidenceCoverage(4, 5),
            semantic = EvidenceCoverage(3, 4),
            diagnosticOffset = 0x200,
            provenance = CandidateProvenance(
                reasons = listOf(CandidateReason(CandidateReasonKind.RECOVERY, "second proof")),
                validatorReviewRecommended = true,
            ),
        )

        val forward = CapabilityEvidenceAdapter.adapt(
            RomCapability.EVOLUTIONS,
            DatasetResolution.Ambiguous(DatasetKind.EVOLUTIONS, listOf(first, second)),
        )
        val reverse = CapabilityEvidenceAdapter.adapt(
            RomCapability.EVOLUTIONS,
            DatasetResolution.Ambiguous(DatasetKind.EVOLUTIONS, listOf(second, first)),
        )

        assertEquals(forward, reverse)
        assertFalse(forward.compatible)
        assertEquals(CapabilityStatus.AMBIGUOUS, forward.status)
        assertEquals(0.75, forward.confidence, 0.0)
        assertNull(forward.offset)
        assertEquals(5, forward.count)
        assertEquals(4, forward.validRecords)
        assertEquals(5, forward.totalRecords)
        assertEquals(3, forward.coveredRecords)
        assertEquals(4, forward.expectedRecords)
        assertEquals(1, forward.incompleteRecords)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, forward.reviewStatus)
        assertTrue(forward.validatorReviewRecommended)
        assertEquals(
            listOf(
                "multiple equally supported dataset candidates",
                "candidate EVOLUTIONS:first source COMPILED_REFERENCE at 0x400",
                "candidate EVOLUTIONS:first compiled reference count: 1",
                "provenance information: first proof",
                "candidate EVOLUTIONS:second source COMPILED_REFERENCE at 0x200",
                "candidate EVOLUTIONS:second compiled reference count: 1",
                "provenance recovery: second proof",
            ),
            forward.reasons,
        )
    }

    @Test
    fun unavailableAndBudgetExceededStayDistinctTypedBranches() {
        val unavailable = CapabilityEvidenceAdapter.adapt<TestLayout>(
            RomCapability.EVOLUTIONS,
            DatasetResolution.Unavailable(
                DatasetKind.EVOLUTIONS,
                observedCandidates = 3,
                reasons = listOf("published root failed validation", "no eligible layout"),
            ),
        )
        val budget = CapabilityEvidenceAdapter.adapt<TestLayout>(
            RomCapability.EVOLUTIONS,
            DatasetResolution.BudgetExceeded(
                kind = DatasetKind.EVOLUTIONS,
                budgetKind = BudgetKind.PROBE_WORK,
                observed = 17,
                limit = 16,
                observationComplete = false,
                reason = "layout attempt budget exceeded",
            ),
        )

        assertEquals(CapabilityStatus.NOT_FOUND, unavailable.status)
        assertEquals(0.0, unavailable.confidence, 0.0)
        assertEquals(
            listOf("no eligible layout", "published root failed validation", "observed candidates: 3"),
            unavailable.reasons,
        )
        assertEquals(CapabilityReviewStatus.NONE, unavailable.reviewStatus)

        assertEquals(CapabilityStatus.NOT_FOUND, budget.status)
        assertEquals(0.0, budget.confidence, 0.0)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, budget.reviewStatus)
        assertEquals(
            listOf(
                "budget kind: PROBE_WORK",
                "budget observation: at least 17 units (limit 16)",
                "layout attempt budget exceeded",
            ),
            budget.reasons,
        )
    }

    private fun candidate(
        kind: DatasetKind = DatasetKind.EVOLUTIONS,
        value: String,
        structural: EvidenceCoverage,
        semantic: EvidenceCoverage?,
        diagnosticOffset: Int? = null,
        provenance: CandidateProvenance = CandidateProvenance(),
    ): DatasetCandidate<TestLayout> = DatasetCandidate(
        identity = CandidateIdentity("${kind.name}:$value"),
        kind = kind,
        layout = TestLayout(value),
        source = CandidateSource.COMPILED_REFERENCE,
        strength = CandidateStrength(
            semanticCoverage = semantic,
            structuralCoverage = structural,
            compiledReferenceCount = 1,
        ),
        diagnosticOffset = diagnosticOffset,
        diagnosticLabel = value,
        eligibility = CandidateEligibility.validated(CandidateSource.COMPILED_REFERENCE),
        provenance = provenance,
    )

    private data class TestLayout(val value: String) : ImmutableDatasetLayout<TestLayout> {
        override val layoutIdentity: CandidateLayoutIdentity = CandidateLayoutIdentity("adapter-test:$value")

        override fun immutableSnapshot(): TestLayout = this
    }
}
