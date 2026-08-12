package com.enrpau.dualscreendex.parser.dataset.descriptions

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.resolution.BudgetKind
import com.enrpau.dualscreendex.parser.resolution.CandidateSource
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DescriptionResolverTest {
    @Test
    fun exactProfileLayoutHasAuthorityOverAnEquallyValidCompiledCandidate() {
        val bytes = ByteArray(0x3000)
        val exact = DescriptionTableLayout(0x200, 4, 36, listOf(16, 20))
        var referenceIndexBuilds = 0
        putDescriptionTable(bytes, 0x200, 4, 36, listOf(16, 20), 0x1800)
        putDescriptionTable(bytes, 0x500, 4, 36, listOf(16, 20), 0x2000)

        val result = DescriptionResolver().resolve(
            session = descriptionSession(
                bytes,
                references = mapOf(0x500 to 3),
                exact = true,
                exactDescriptionLayout = exact,
                onReferenceIndexBuild = { referenceIndexBuilds++ },
            ),
            expectedSpeciesCount = 4,
            profileLayout = exact,
        ) as DatasetResolution.Resolved<ResolvedDescriptionLayout>

        assertEquals(exact, result.candidate.layout.table)
        assertEquals(CandidateSource.EXACT_PROFILE, result.candidate.source)
        assertEquals(0, referenceIndexBuilds)
    }

    @Test
    fun anArbitraryLayoutCannotBorrowAuthorityFromAnOtherwiseExactSession() {
        val bytes = ByteArray(0x3000)
        val actualExact = DescriptionTableLayout(0x200, 4, 36, listOf(16, 20))
        val untrustedArgument = DescriptionTableLayout(0x500, 4, 36, listOf(16, 20))
        putDescriptionTable(bytes, 0x200, 4, 36, listOf(16, 20), 0x1800)
        putDescriptionTable(bytes, 0x500, 4, 36, listOf(16, 20), 0x2000)

        val result = DescriptionResolver().resolve(
            session = descriptionSession(
                bytes,
                references = mapOf(0x200 to 1),
                exact = true,
                exactDescriptionLayout = actualExact,
            ),
            expectedSpeciesCount = 4,
            profileLayout = untrustedArgument,
        ) as DatasetResolution.Resolved<ResolvedDescriptionLayout>

        assertEquals(actualExact, result.candidate.layout.table)
        assertEquals(CandidateSource.COMPILED_REFERENCE, result.candidate.source)
    }

    @Test
    fun aCompiledRootBeatsAnUnreferencedStructuralDecoy() {
        val bytes = ByteArray(0x3000)
        val compiled = DescriptionTableLayout(0x200, 4, 36, listOf(16, 20))
        val decoy = DescriptionTableLayout(0x500, 4, 36, listOf(16, 20))
        putDescriptionTable(bytes, 0x200, 4, 36, listOf(16, 20), 0x1800)
        putDescriptionTable(bytes, 0x500, 4, 36, listOf(16, 20), 0x2000)

        val result = DescriptionResolver().resolve(
            session = descriptionSession(bytes, references = mapOf(0x200 to 1)),
            expectedSpeciesCount = 4,
            structuralCandidates = listOf(decoy),
        ) as DatasetResolution.Resolved<ResolvedDescriptionLayout>

        assertEquals(compiled, result.candidate.layout.table)
        assertEquals(CandidateSource.COMPILED_REFERENCE, result.candidate.source)
    }

    @Test
    fun equalIndependentEvidenceIsAmbiguousRegardlessOfEnumerationAndOffset() {
        val bytes = ByteArray(0x4000)
        val lower = DescriptionTableLayout(0x200, 4, 36, listOf(16, 20))
        val higher = DescriptionTableLayout(0x700, 4, 36, listOf(16, 20))
        putDescriptionTable(bytes, 0x200, 4, 36, listOf(16, 20), 0x1800)
        putDescriptionTable(bytes, 0x700, 4, 36, listOf(16, 20), 0x2800)
        val session = descriptionSession(bytes, references = linkedMapOf(0x700 to 2, 0x200 to 2))

        val first = DescriptionResolver().resolve(session, expectedSpeciesCount = 4)
        val second = DescriptionResolver().resolve(
            session,
            expectedSpeciesCount = 4,
            structuralCandidates = listOf(higher, lower),
        )

        assertTrue(first is DatasetResolution.Ambiguous)
        assertTrue(second is DatasetResolution.Ambiguous)
        assertEquals(
            setOf(lower, higher),
            (first as DatasetResolution.Ambiguous<ResolvedDescriptionLayout>)
                .candidates.map { it.layout.table }.toSet(),
        )
        assertEquals(
            setOf(lower, higher),
            (second as DatasetResolution.Ambiguous<ResolvedDescriptionLayout>)
                .candidates.map { it.layout.table }.toSet(),
        )
    }

    @Test
    fun pointerOffsetsParticipateInLayoutIdentityDuringSelection() {
        val bytes = ByteArray(0x2000)
        val onePage = DescriptionTableLayout(0x200, 3, 36, listOf(16))
        val twoPage = DescriptionTableLayout(0x200, 3, 36, listOf(16, 20))
        putDescriptionTable(bytes, 0x200, 3, 36, listOf(16, 20), 0x1000)

        val result = DescriptionResolver().resolve(
            session = descriptionSession(bytes, references = mapOf(0x200 to 1)),
            expectedSpeciesCount = 3,
            structuralCandidates = listOf(onePage, twoPage),
        )

        assertTrue(result is DatasetResolution.Resolved)
        assertEquals(
            twoPage,
            (result as DatasetResolution.Resolved<ResolvedDescriptionLayout>).candidate.layout.table,
        )
    }

    @Test
    fun referenceIndexOverflowIsATypedBudgetOutcomeWithoutAnExactProfileBypass() {
        val result = DescriptionResolver().resolve(
            session = descriptionSession(
                bytes = ByteArray(0x1000),
                referenceIndexOverride = GbaReferenceIndex.budgetExceeded(
                    reason = "fixture reference target overflow",
                    observedTargets = 4,
                    limitTargets = 3,
                ),
            ),
            expectedSpeciesCount = 4,
        )

        assertEquals(
            DatasetResolution.BudgetExceeded<ResolvedDescriptionLayout>(
                kind = com.enrpau.dualscreendex.parser.resolution.DatasetKind.POKEDEX_DESCRIPTIONS,
                budgetKind = BudgetKind.REFERENCE_TARGETS,
                observed = 4,
                limit = 3,
                observationComplete = false,
                reason = "fixture reference target overflow",
            ),
            result,
        )
    }

    @Test
    fun probeBudgetStopsBeforeTheOverflowRootIsDecoded() {
        var decodeAttempts = 0
        val decoder = DescriptionTableDecoder { _, layout ->
            decodeAttempts++
            DescriptionTableOutcome.Rejected(layout, "fixture invalid root")
        }
        val references = (0 until 128).associate { index -> 0x400 + index * 4 to 1 }

        val result = DescriptionResolver(decoder).resolve(
            session = descriptionSession(
                bytes = ByteArray(0x1000),
                references = references,
                limits = ResolutionLimits(maxProbeRootsPerDataset = 3),
            ),
            expectedSpeciesCount = 4,
        )

        assertTrue(result is DatasetResolution.BudgetExceeded)
        assertEquals(9, decodeAttempts)
        assertEquals(BudgetKind.PROBE_ROOTS, (result as DatasetResolution.BudgetExceeded).budgetKind)
        assertEquals(4, result.observed)
        assertEquals(3, result.limit)
        assertEquals(false, result.observationComplete)
    }
}
