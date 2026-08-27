package com.enrpau.dualscreendex.parser.resolution

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationSource
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateSelectorTest {
    @Test
    fun higherAuthorityWinsBeforeCoverageOrDiagnosticLocation() {
        val inherited = candidate(
            kind = DatasetKind.LEVEL_UP_LEARNSETS,
            layout = "inherited",
            source = CandidateSource.INHERITED_FAMILY_LAYOUT,
            semantic = EvidenceCoverage(100, 100),
            structural = EvidenceCoverage(100, 100),
            diagnosticOffset = 0x20,
        )
        val directConsumer = candidate(
            kind = DatasetKind.LEVEL_UP_LEARNSETS,
            layout = "direct",
            source = CandidateSource.DIRECT_COMPILED_CONSUMER,
            semantic = EvidenceCoverage(1, 100),
            structural = EvidenceCoverage(1, 100),
            diagnosticOffset = 0x2000,
        )

        val result = select(DatasetKind.LEVEL_UP_LEARNSETS, inherited, directConsumer)

        assertEquals("direct", selectedCandidate(result).layout.value)
    }

    @Test
    fun equalSubstantiveEvidenceRemainsAmbiguousRegardlessOfEnumerationOrder() {
        val lowOffset = candidate(
            kind = DatasetKind.POKEDEX_DESCRIPTIONS,
            layout = "low-offset",
            diagnosticOffset = 0x20,
            diagnosticLabel = "low",
        )
        val highOffset = candidate(
            kind = DatasetKind.POKEDEX_DESCRIPTIONS,
            layout = "high-offset",
            diagnosticOffset = 0x2000,
            diagnosticLabel = "high",
        )

        val forward = select(DatasetKind.POKEDEX_DESCRIPTIONS, lowOffset, highOffset)
        val reverse = select(DatasetKind.POKEDEX_DESCRIPTIONS, highOffset, lowOffset)

        assertTrue(forward is DatasetResolution.Ambiguous)
        assertTrue(reverse is DatasetResolution.Ambiguous)
        assertEquals(
            listOf(0x20, 0x2000),
            (forward as DatasetResolution.Ambiguous).candidates.map { it.diagnosticOffset },
        )
        assertEquals(
            forward.candidates.map { it.identity },
            (reverse as DatasetResolution.Ambiguous).candidates.map { it.identity },
        )
    }

    @Test
    fun comparesSemanticThenStructuralThenReferenceThenDatasetQuality() {
        val semanticWinner = candidate(
            layout = "semantic",
            semantic = EvidenceCoverage(9, 10),
            structural = EvidenceCoverage(8, 10),
        )
        val structuralOnly = candidate(
            layout = "structural",
            semantic = EvidenceCoverage(8, 10),
            structural = EvidenceCoverage(10, 10),
            referenceCount = 100,
            quality = 100,
        )
        assertEquals(
            "semantic",
            selectedCandidate(select(DatasetKind.EVOLUTIONS, structuralOnly, semanticWinner)).layout.value,
        )

        val structuralWinner = candidate(layout = "structural-winner")
        val referenceOnly = candidate(
            layout = "reference-only",
            structural = EvidenceCoverage(9, 10),
            referenceCount = 100,
            quality = 100,
        )
        assertEquals(
            "structural-winner",
            selectedCandidate(select(DatasetKind.EVOLUTIONS, referenceOnly, structuralWinner)).layout.value,
        )

        val referenceWinner = candidate(layout = "reference", referenceCount = 3)
        val qualityOnly = candidate(layout = "quality-only", referenceCount = 2, quality = 100)
        assertEquals(
            "reference",
            selectedCandidate(select(DatasetKind.EVOLUTIONS, qualityOnly, referenceWinner)).layout.value,
        )

        val qualityWinner = candidate(layout = "quality", quality = 2)
        val lowerQuality = candidate(layout = "lower-quality", quality = 1)
        assertEquals(
            "quality",
            selectedCandidate(select(DatasetKind.EVOLUTIONS, lowerQuality, qualityWinner)).layout.value,
        )
    }

    @Test
    fun incompleteWinnersArePartialWhileACompleteWinnerIsResolved() {
        val semanticPartial = candidate(
            layout = "semantic-partial",
            semantic = EvidenceCoverage(9, 10),
        )
        val structuralPartial = candidate(
            layout = "structural-partial",
            structural = EvidenceCoverage(8, 10),
        )
        val complete = candidate(layout = "complete")

        val semanticResult = select(DatasetKind.EVOLUTIONS, semanticPartial)
        val structuralResult = select(DatasetKind.EVOLUTIONS, structuralPartial)
        val completeResult = select(DatasetKind.EVOLUTIONS, complete)

        assertEquals(
            listOf("semantic coverage is 9/10"),
            (semanticResult as DatasetResolution.Partial).reasons,
        )
        assertEquals(
            listOf("structural coverage is 8/10"),
            (structuralResult as DatasetResolution.Partial).reasons,
        )
        assertTrue(completeResult is DatasetResolution.Resolved)
    }

    @Test
    fun noCandidateIsUnavailable() {
        val result = CandidateSelector.select<TestLayout>(
            session = session(),
            kind = DatasetKind.EVOLUTIONS,
            candidates = emptySequence(),
        )

        assertEquals(
            DatasetResolution.Unavailable<TestLayout>(
                kind = DatasetKind.EVOLUTIONS,
                observedCandidates = 0,
                reason = "no candidates discovered",
            ),
            result,
        )
    }

    @Test
    fun hugeCandidateLimitDoesNotAllocateABudgetSizedBufferForEmptyEvidence() {
        val result = CandidateSelector.select<TestLayout>(
            session = session(ResolutionLimits(maxCandidatesPerDataset = Int.MAX_VALUE)),
            kind = DatasetKind.EVOLUTIONS,
            candidates = emptySequence(),
        )

        assertEquals(
            DatasetResolution.Unavailable<TestLayout>(
                kind = DatasetKind.EVOLUTIONS,
                observedCandidates = 0,
                reason = "no candidates discovered",
            ),
            result,
        )
    }

    @Test
    fun boundedSequenceStopsImmediatelyAfterProvingCandidateBudgetExhaustion() {
        var yielded = 0
        val candidates = sequence {
            repeat(10) { index ->
                yielded++
                if (yielded > 3) error("selector consumed past the proof of overflow")
                yield(candidate(DatasetKind.BASE_STATS, "candidate-$index"))
            }
        }
        val analysis = session(ResolutionLimits(maxCandidatesPerDataset = 2))

        val result = CandidateSelector.select(analysis, DatasetKind.BASE_STATS, candidates)

        assertEquals(3, yielded)
        assertEquals(
            DatasetResolution.BudgetExceeded<TestLayout>(
                kind = DatasetKind.BASE_STATS,
                budgetKind = BudgetKind.CANDIDATES,
                observed = 3,
                limit = 2,
                observationComplete = false,
                reason = "dataset candidate budget exceeded (observed at least 3, limit 2)",
            ),
            result,
        )
    }

    @Test
    fun duplicateIdentityMergesProvenanceAndKeepsTheStrongestRepresentative() {
        val identity = CandidateIdentity("species-names:root-100")
        val inherited = candidate(
            kind = DatasetKind.SPECIES_NAMES,
            layout = "same-layout",
            source = CandidateSource.INHERITED_FAMILY_LAYOUT,
            identity = identity,
            provenance = CandidateProvenance(
                reasons = listOf(CandidateReason(CandidateReasonKind.INFORMATION, "inherited root")),
                compiledReferenceSites = CompiledReferenceSites.of(listOf(0x40), maxSites = 4),
            ),
        )
        val direct = candidate(
            kind = DatasetKind.SPECIES_NAMES,
            layout = "same-layout",
            source = CandidateSource.DIRECT_COMPILED_CONSUMER,
            identity = identity,
            provenance = CandidateProvenance(
                reasons = listOf(CandidateReason(CandidateReasonKind.RECOVERY, "consumer recovered")),
                validatorReviewRecommended = true,
                compiledReferenceSites = CompiledReferenceSites.of(listOf(0x20), maxSites = 4),
            ),
        )

        val result = select(DatasetKind.SPECIES_NAMES, inherited, direct)
        val selected = (result as DatasetResolution.Resolved).candidate

        assertEquals(CandidateSource.DIRECT_COMPILED_CONSUMER, selected.source)
        assertEquals(
            listOf(CandidateReasonKind.INFORMATION, CandidateReasonKind.RECOVERY),
            selected.provenance.reasons.map { it.kind },
        )
        assertTrue(selected.provenance.validatorReviewRecommended)
        assertEquals(listOf(0x20, 0x40), selected.provenance.compiledReferenceSites.offsets)
    }

    @Test
    fun identicalLayoutsWithDifferentStableIdentitiesRemainAmbiguous() {
        val first = candidate(layout = "same-layout", identity = CandidateIdentity("evolutions:first"))
        val second = candidate(layout = "same-layout", identity = CandidateIdentity("evolutions:second"))

        val result = select(DatasetKind.EVOLUTIONS, first, second)

        assertTrue(result is DatasetResolution.Ambiguous)
    }

    @Test
    fun duplicateIdentityWithConflictingLayoutsFailsClosedRegardlessOfEnumerationOrder() {
        val identity = CandidateIdentity("evolutions:conflict")
        val first = candidate(layout = "layout-a", identity = identity)
        val second = candidate(layout = "layout-b", identity = identity)

        val forward = select(DatasetKind.EVOLUTIONS, first, second)
        val reverse = select(DatasetKind.EVOLUTIONS, second, first)

        assertEquals(
            DatasetResolution.Unavailable<TestLayout>(
                kind = DatasetKind.EVOLUTIONS,
                observedCandidates = 2,
                reason = "candidate identity evolutions:conflict maps to conflicting layouts",
            ),
            forward,
        )
        assertEquals(forward, reverse)
    }

    @Test
    fun stableIdentityIsTheFinalAmbiguityDiagnosticKey() {
        val second = candidate(
            layout = "second",
            identity = CandidateIdentity("evolutions:b"),
            diagnosticOffset = 0x20,
            diagnosticLabel = "same",
        )
        val first = candidate(
            layout = "first",
            identity = CandidateIdentity("evolutions:a"),
            diagnosticOffset = 0x20,
            diagnosticLabel = "same",
        )

        val result = select(DatasetKind.EVOLUTIONS, second, first) as DatasetResolution.Ambiguous

        assertEquals(
            listOf("evolutions:a", "evolutions:b"),
            result.candidates.map { it.identity.value },
        )
    }

    @Test
    fun structuralAnchorIsDeniedByDefaultAndEnabledOnlyPerDatasetPolicy() {
        val structural = candidate(
            kind = DatasetKind.BASE_STATS,
            layout = "structural",
            source = CandidateSource.STRUCTURAL_ANCHOR,
        )

        val denied = CandidateSelector.select(session(), DatasetKind.BASE_STATS, sequenceOf(structural))
        val permitted = CandidateSelector.select(
            session = session(),
            kind = DatasetKind.BASE_STATS,
            candidates = sequenceOf(structural),
            structuralAnchorPolicy = StructuralAnchorPolicy.allow(DatasetKind.BASE_STATS),
        )

        assertTrue(denied is DatasetResolution.Unavailable)
        assertTrue(permitted is DatasetResolution.Resolved)
    }

    @Test
    fun typedIneligibleEvidenceCannotEnterRanking() {
        val first = candidate(
            layout = "rejected-a",
            eligibility = CandidateEligibility.Ineligible("validator rejected active row"),
        )
        val second = candidate(
            layout = "rejected-b",
            eligibility = CandidateEligibility.Ineligible("published root failed boundary proof"),
        )

        val forward = select(DatasetKind.EVOLUTIONS, first, second)
        val reverse = select(DatasetKind.EVOLUTIONS, second, first)

        assertEquals(
            listOf("published root failed boundary proof", "validator rejected active row"),
            (forward as DatasetResolution.Unavailable).reasons,
        )
        assertEquals(forward, reverse)
    }

    @Test
    fun cancellationStopsLazyCandidateEnumerationBeforeTheNextCandidateIsConsumed() {
        val cancellation = ParserCancellationSource()
        val visited = mutableListOf<String>()
        val candidates = sequence {
            visited += "first"
            yield(candidate(layout = "first"))
            cancellation.cancel()
            visited += "second"
            yield(candidate(layout = "second"))
        }
        val session = RomAnalysisSession(
            rom = RomImage(ByteArray(0x200)),
            header = RomHeader(Platform.GBA, "TEST"),
            cancellation = cancellation.token,
        )

        assertThrows(ParserCancellationException::class.java) {
            CandidateSelector.select(session, DatasetKind.EVOLUTIONS, candidates)
        }

        assertEquals(listOf("first", "second"), visited)
    }

    private fun select(
        kind: DatasetKind,
        vararg candidates: DatasetCandidate<TestLayout>,
    ): DatasetResolution<TestLayout> = CandidateSelector.select(
        session = session(),
        kind = kind,
        candidates = candidates.asSequence(),
    )

    private fun session(limits: ResolutionLimits = ResolutionLimits()): RomAnalysisSession = RomAnalysisSession(
        rom = RomImage(ByteArray(0x200)),
        header = RomHeader(Platform.GBA, "TEST"),
        limits = limits,
    )

    private fun candidate(
        kind: DatasetKind = DatasetKind.EVOLUTIONS,
        layout: String,
        source: CandidateSource = CandidateSource.COMPILED_REFERENCE,
        identity: CandidateIdentity = CandidateIdentity("${kind.name}:$layout"),
        semantic: EvidenceCoverage? = EvidenceCoverage(10, 10),
        structural: EvidenceCoverage = EvidenceCoverage(10, 10),
        referenceCount: Int = if (
            source == CandidateSource.COMPILED_REFERENCE || source == CandidateSource.PUBLISHED_HEADER
        ) 1 else 0,
        quality: Int = 0,
        diagnosticOffset: Int? = null,
        diagnosticLabel: String = layout,
        eligibility: CandidateEligibility = CandidateEligibility.validated(source),
        provenance: CandidateProvenance = CandidateProvenance(),
    ): DatasetCandidate<TestLayout> = DatasetCandidate(
        identity = identity,
        kind = kind,
        layout = TestLayout(layout),
        source = source,
        strength = CandidateStrength(
            semanticCoverage = semantic,
            structuralCoverage = structural,
            compiledReferenceCount = referenceCount,
            datasetQuality = quality,
        ),
        diagnosticOffset = diagnosticOffset,
        diagnosticLabel = diagnosticLabel,
        eligibility = eligibility,
        provenance = provenance,
    )

    private fun selectedCandidate(
        result: DatasetResolution<TestLayout>,
    ): DatasetCandidate<TestLayout> = when (result) {
        is DatasetResolution.Resolved -> result.candidate
        is DatasetResolution.Partial -> result.candidate
        else -> error("expected a selected candidate, got $result")
    }

    private data class TestLayout(val value: String) : ImmutableDatasetLayout<TestLayout> {
        override val layoutIdentity: CandidateLayoutIdentity = CandidateLayoutIdentity("test-layout:$value")

        override fun immutableSnapshot(): TestLayout = this
    }
}
