package com.enrpau.dualscreendex.parser.resolution

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateProvenanceTest {
    @Test
    fun defaultsAreEmptyAndDoNotRequestManualReview() {
        val provenance = CandidateProvenance()

        assertTrue(provenance.reasons.isEmpty())
        assertTrue(provenance.compiledReferenceSites.offsets.isEmpty())
        assertFalse(provenance.validatorReviewRecommended)
    }

    @Test
    fun retainsTypedReasonsReviewIntentAndDeterministicReferenceSites() {
        val provenance = CandidateProvenance(
            reasons = listOf(
                CandidateReason(CandidateReasonKind.INFORMATION, "published root"),
                CandidateReason(CandidateReasonKind.RECOVERY, "recovered one pointer"),
                CandidateReason(CandidateReasonKind.ANOMALY, "one active row malformed"),
            ),
            validatorReviewRecommended = true,
            compiledReferenceSites = CompiledReferenceSites.of(
                offsets = listOf(0x200, 0x20, 0x200),
                maxSites = 2,
            ),
        )

        assertEquals(
            listOf(
                CandidateReasonKind.INFORMATION,
                CandidateReasonKind.RECOVERY,
                CandidateReasonKind.ANOMALY,
            ),
            provenance.reasons.map { it.kind },
        )
        assertEquals(listOf(0x20, 0x200), provenance.compiledReferenceSites.offsets)
        assertTrue(provenance.validatorReviewRecommended)
    }

    @Test
    fun provenanceAndReferenceSitesHaveValueEqualityAndStableToString() {
        val first = CandidateProvenance(
            reasons = listOf(CandidateReason(CandidateReasonKind.RECOVERY, "recovered pointer")),
            validatorReviewRecommended = true,
            compiledReferenceSites = CompiledReferenceSites.of(listOf(0x40, 0x20), maxSites = 3),
        )
        val second = CandidateProvenance(
            reasons = listOf(CandidateReason(CandidateReasonKind.RECOVERY, "recovered pointer")),
            validatorReviewRecommended = true,
            compiledReferenceSites = CompiledReferenceSites.of(listOf(0x20, 0x40), maxSites = 3),
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(first.toString(), second.toString())
        assertEquals(first.compiledReferenceSites, second.compiledReferenceSites)
        assertEquals(first.compiledReferenceSites.toString(), second.compiledReferenceSites.toString())
    }

    @Test(expected = UnsupportedOperationException::class)
    fun publishedProvenanceReasonsAreTrulyUnmodifiable() {
        val provenance = CandidateProvenance(
            reasons = listOf(CandidateReason(CandidateReasonKind.INFORMATION, "published root")),
        )

        @Suppress("UNCHECKED_CAST")
        (provenance.reasons as MutableList<CandidateReason>).add(
            CandidateReason(CandidateReasonKind.ANOMALY, "mutation"),
        )
    }

    @Test
    fun reportsReferenceSiteBudgetExhaustionWithoutThrowingOrTruncating() {
        val sites = CompiledReferenceSites.of(
            offsets = listOf(0x20, 0x40, 0x60),
            maxSites = 2,
        )

        assertTrue(sites.budgetExceeded)
        assertTrue(sites.offsets.isEmpty())
        assertEquals(3L, sites.observedSites)
        assertEquals(2, sites.limitSites)
        assertEquals("compiled reference site budget exceeded (3 > 2)", sites.overflowReason)
    }

    @Test
    fun siteConstructionStopsAtTheFirstDistinctOverflowWitness() {
        var evaluated = 0
        val sites = CompiledReferenceSites.of(
            offsets = sequence {
                repeat(10) { index ->
                    evaluated++
                    if (evaluated > 3) error("site construction consumed past overflow witness")
                    yield(index * 4)
                }
            },
            maxSites = 2,
        )

        assertTrue(sites.budgetExceeded)
        assertEquals(3, evaluated)
        assertEquals(3L, sites.observedSites)
        assertTrue(sites.offsets.isEmpty())
    }

    @Test
    fun hugeMergeLimitDoesNotAllocateABudgetSizedBufferForEmptyEvidence() {
        val merged = CompiledReferenceSites.merge(
            values = emptyList(),
            maxSites = Int.MAX_VALUE,
        )

        assertFalse(merged.budgetExceeded)
        assertTrue(merged.offsets.isEmpty())
        assertEquals(0L, merged.observedSites)
        assertEquals(Int.MAX_VALUE, merged.limitSites)
    }

    @Test
    fun siteMergeAggregatesBoundedInputsAndTruthfullyCitesPreviouslyDiscardedEvidence() {
        val previouslyOverflowed = CompiledReferenceSites.of(sequenceOf(4, 8, 12), maxSites = 2)
        val merged = CompiledReferenceSites.merge(
            values = listOf(previouslyOverflowed),
            maxSites = 16,
        )

        assertTrue(merged.budgetExceeded)
        assertTrue(merged.offsets.isEmpty())
        assertTrue(merged.overflowReason?.contains("source-limit=2") == true)
        assertFalse(merged.overflowReason?.contains("> 16") == true)
    }

    @Test
    fun siteMergeAggregatesSourceOverflowAndKnownSitesIdenticallyInEitherOrder() {
        val known = CompiledReferenceSites.of((0 until 17).map { it * 4 }, maxSites = 17)
        val sourceOverflow = CompiledReferenceSites.of(sequenceOf(0x100, 0x104, 0x108), maxSites = 2)

        val forward = CompiledReferenceSites.merge(listOf(known, sourceOverflow), maxSites = 16)
        val reverse = CompiledReferenceSites.merge(listOf(sourceOverflow, known), maxSites = 16)

        assertEquals(forward, reverse)
        assertTrue(forward.budgetExceeded)
        assertTrue(forward.offsets.isEmpty())
        assertTrue(forward.overflowReason?.contains("source evidence was previously discarded") == true)
        assertTrue(forward.observedSites >= sourceOverflow.observedSites)
        assertEquals(17L, forward.observedSites)
        assertFalse(forward.overflowReason?.contains("3 > 16") == true)
    }

    @Test
    fun diagnosticProvenanceDoesNotResolveASubstantiveTie() {
        val sharedStrength = CandidateStrength(
            semanticCoverage = EvidenceCoverage(10, 10),
            structuralCoverage = EvidenceCoverage(10, 10),
            compiledReferenceCount = 2,
        )
        val recovered = DatasetCandidate(
            identity = CandidateIdentity("evolutions:recovered"),
            kind = DatasetKind.EVOLUTIONS,
            layout = TestLayout("recovered"),
            source = CandidateSource.COMPILED_REFERENCE,
            strength = sharedStrength,
            diagnosticOffset = 0x200,
            diagnosticLabel = "recovered",
            eligibility = CandidateEligibility.validated(CandidateSource.COMPILED_REFERENCE),
            provenance = CandidateProvenance(
                reasons = listOf(CandidateReason(CandidateReasonKind.RECOVERY, "pointer recovered")),
                validatorReviewRecommended = true,
                compiledReferenceSites = CompiledReferenceSites.of(listOf(0x180), maxSites = 2),
            ),
        )
        val ordinary = DatasetCandidate(
            identity = CandidateIdentity("evolutions:ordinary"),
            kind = DatasetKind.EVOLUTIONS,
            layout = TestLayout("ordinary"),
            source = CandidateSource.COMPILED_REFERENCE,
            strength = sharedStrength,
            diagnosticOffset = 0x20,
            diagnosticLabel = "ordinary",
            eligibility = CandidateEligibility.validated(CandidateSource.COMPILED_REFERENCE),
            provenance = CandidateProvenance(
                reasons = listOf(CandidateReason(CandidateReasonKind.INFORMATION, "published root")),
                compiledReferenceSites = CompiledReferenceSites.of(listOf(0x10, 0x30), maxSites = 2),
            ),
        )

        val result = CandidateSelector.select(
            session = session(),
            kind = DatasetKind.EVOLUTIONS,
            candidates = sequenceOf(recovered, ordinary),
        )

        assertTrue(result is DatasetResolution.Ambiguous)
        assertEquals(
            listOf("ordinary", "recovered"),
            (result as DatasetResolution.Ambiguous).candidates.map { it.layout.value },
        )
    }

    private fun session(): RomAnalysisSession = RomAnalysisSession(
        rom = RomImage(ByteArray(0x200)),
        header = RomHeader(Platform.GBA, "TEST"),
    )

    private data class TestLayout(val value: String) : ImmutableDatasetLayout<TestLayout> {
        override val layoutIdentity: CandidateLayoutIdentity = CandidateLayoutIdentity("layout:$value")

        override fun immutableSnapshot(): TestLayout = this
    }
}
