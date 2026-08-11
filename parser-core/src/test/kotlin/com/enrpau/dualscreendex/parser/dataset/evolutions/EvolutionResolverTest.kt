package com.enrpau.dualscreendex.parser.dataset.evolutions

import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.resolution.BudgetKind
import com.enrpau.dualscreendex.parser.resolution.CandidateSource
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvolutionResolverTest {
    private val resolver = EvolutionResolver()

    @Test
    fun selectsValidatedExactProfileBeforeBuildingTheReferenceIndex() {
        val layout = EvolutionTableLayout(0x400, 10, 10, 8)
        val bytes = ByteArray(0x2000)
        putEvolution(bytes, layout.rowOffset(1), 4, 16, 2)
        var indexBuilds = 0
        val session = evolutionSession(
            bytes = bytes,
            exactLayout = layout,
            onReferenceIndexBuild = { indexBuilds++ },
            referenceIndexOverride = com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
                .budgetExceeded("reference index must not be consulted", observedTargets = 2, limitTargets = 1),
        )

        val result = resolver.resolveGen3(
            session = session,
            expectedSpeciesCount = 10,
            profileLayout = layout,
        ) as DatasetResolution.Resolved

        assertEquals(CandidateSource.EXACT_PROFILE, result.candidate.source)
        assertEquals(layout, result.candidate.layout.table)
        assertEquals(0, indexBuilds)
    }

    @Test
    fun compiledReferencedTenBySixTableBeatsInheritedLayout() {
        val bytes = ByteArray(0x3000)
        val count = 10
        val compiled = EvolutionTableLayout(0x1000, count.toLong(), 10, 6)
        val inherited = EvolutionTableLayout(0x400, count.toLong(), 5, 8)
        putEvolution(bytes, inherited.rowOffset(1), 4, 16, 2)
        putEvolution(bytes, compiled.rowOffset(2) + 6, 4, 18, 3)
        val references = mapOf(
            compiled.offset.toInt() to 1,
            compiled.endExclusive.toInt() to 1,
        )

        val result = resolver.resolveGen3(
            session = evolutionSession(bytes, references),
            expectedSpeciesCount = count,
            profileLayout = inherited,
        ) as DatasetResolution.Resolved

        assertEquals(compiled, result.candidate.layout.table)
        assertEquals(CandidateSource.COMPILED_REFERENCE, result.candidate.source)
    }

    @Test
    fun corroboratedPublishedLayoutBeatsCompiledAndInheritedCandidates() {
        val bytes = ByteArray(0x5000)
        val count = 10
        val inherited = EvolutionTableLayout(0x400, count.toLong(), 10, 8)
        val compiled = EvolutionTableLayout(0x1000, count.toLong(), 10, 8)
        val published = EvolutionTableLayout(0x2000, count.toLong(), 10, 8)
        listOf(inherited, compiled, published).forEachIndexed { index, layout ->
            putEvolution(bytes, layout.rowOffset(1), 4, 16 + index, 2)
        }
        val references = mapOf(
            compiled.offset.toInt() to 1,
            compiled.endExclusive.toInt() to 1,
            published.offset.toInt() to 1,
            published.endExclusive.toInt() to 1,
        )

        val result = resolver.resolveGen3(
            session = evolutionSession(bytes, references),
            expectedSpeciesCount = count,
            profileLayout = inherited,
            publishedLayouts = listOf(published),
        ) as DatasetResolution.Resolved

        assertEquals(published, result.candidate.layout.table)
        assertEquals(CandidateSource.PUBLISHED_HEADER, result.candidate.source)
    }

    @Test
    fun equallyStrongIndependentCompiledRootsRemainAmbiguous() {
        val bytes = ByteArray(0x4000)
        val count = 10
        val layouts = listOf(
            EvolutionTableLayout(0x1000, count.toLong(), 10, 8),
            EvolutionTableLayout(0x2000, count.toLong(), 10, 8),
        )
        layouts.forEach { putEvolution(bytes, it.rowOffset(1), 4, 16, 2) }
        val references = layouts.flatMap { layout ->
            listOf(layout.offset.toInt() to 1, layout.endExclusive.toInt() to 1)
        }.toMap()

        val result = resolver.resolveGen3(
            evolutionSession(bytes, references),
            expectedSpeciesCount = count,
        )

        assertTrue(result is DatasetResolution.Ambiguous)
        assertEquals(2, (result as DatasetResolution.Ambiguous).candidates.size)
    }

    @Test
    fun acceptsCompiledBoundaryAtRomEofAndCarriesReferenceSites() {
        val count = 10
        val layout = EvolutionTableLayout(0x1000, count.toLong(), 10, 8)
        val bytes = ByteArray(layout.endExclusive.toInt())
        putThumbLiteralReferences(
            bytes,
            instructionOffset = 0x80,
            literalOffset = 0x100,
            layout.offset.toInt(),
            layout.endExclusive.toInt(),
        )
        putEvolution(bytes, layout.rowOffset(1), 4, 16, 2)

        val result = resolver.resolveGen3(
            evolutionSession(bytes, useDefaultReferenceIndex = true),
            expectedSpeciesCount = count,
        ) as DatasetResolution.Resolved

        assertEquals(layout, result.candidate.layout.table)
        // The session index deliberately excludes one-past-ROM targets; EOF is still an exact
        // physical boundary and the compiled root site remains available as provenance.
        assertEquals(listOf(0x80), result.candidate.provenance.compiledReferenceSites.offsets)
    }

    @Test
    fun plainPointerDataIsNotCompiledEvidence() {
        val bytes = ByteArray(0x3000)
        val layout = EvolutionTableLayout(0x1000, 10, 10, 8)
        putU32(bytes, 0x100, 0x08000000 + layout.offset.toInt())
        putU32(bytes, 0x104, 0x08000000 + layout.endExclusive.toInt())
        putEvolution(bytes, layout.rowOffset(1), 4, 16, 2)

        val result = resolver.resolveGen3(
            evolutionSession(bytes, useDefaultReferenceIndex = true),
            expectedSpeciesCount = 10,
        )

        assertTrue(result is DatasetResolution.Unavailable)
    }

    @Test
    fun surfacesProbeAndExtentBudgetsAsTypedOutcomes() {
        val bytes = ByteArray(0x3000)
        val first = EvolutionTableLayout(0x1000, 10, 10, 8)
        val second = EvolutionTableLayout(0x2000, 10, 10, 8)
        listOf(first, second).forEach { putEvolution(bytes, it.rowOffset(1), 4, 16, 2) }
        val references = listOf(first, second).flatMap { layout ->
            listOf(layout.offset.toInt() to 1, layout.endExclusive.toInt() to 1)
        }.toMap()
        val probeResult = resolver.resolveGen3(
            evolutionSession(
                bytes,
                references,
                limits = ResolutionLimits(maxProbeRootsPerDataset = 1),
            ),
            expectedSpeciesCount = 10,
        ) as DatasetResolution.BudgetExceeded
        assertEquals(BudgetKind.PROBE_ROOTS, probeResult.budgetKind)

        val extentResult = resolver.resolveGen3(
            evolutionSession(
                bytes,
                references,
                limits = ResolutionLimits(maxDatasetExtentBytes = 64),
            ),
            expectedSpeciesCount = 10,
        ) as DatasetResolution.BudgetExceeded
        assertEquals(BudgetKind.EXTENT, extentResult.budgetKind)
    }

    @Test
    fun surfacesActualLayoutAttemptsAsProbeWorkBudget() {
        val bytes = ByteArray(0x4000)
        val first = EvolutionTableLayout(0x1000, 10, 10, 8)
        val second = EvolutionTableLayout(0x2000, 10, 10, 8)
        listOf(first, second).forEach { putEvolution(bytes, it.rowOffset(1), 4, 16, 2) }
        val references = listOf(first, second).flatMap { layout ->
            listOf(layout.offset.toInt() to 1, layout.endExclusive.toInt() to 1)
        }.toMap()

        val result = resolver.resolveGen3(
            evolutionSession(
                bytes,
                references,
                limits = ResolutionLimits(maxProbeWorkPerDataset = 1),
            ),
            expectedSpeciesCount = 10,
        ) as DatasetResolution.BudgetExceeded

        assertEquals(BudgetKind.PROBE_WORK, result.budgetKind)
        assertEquals(2L, result.observed)
        assertEquals(1L, result.limit)
    }

    @Test
    fun boundsRejectedShapeAttemptsBeforeTheyCanDisappearFromCandidateAccounting() {
        val bytes = ByteArray(0x4000)
        val rootWithoutBoundary = 0x1000

        val result = resolver.resolveGen3(
            evolutionSession(
                bytes,
                references = mapOf(rootWithoutBoundary to 1),
                limits = ResolutionLimits(maxProbeWorkPerDataset = 5),
            ),
            expectedSpeciesCount = 10,
        ) as DatasetResolution.BudgetExceeded

        assertEquals(BudgetKind.PROBE_WORK, result.budgetKind)
        assertEquals(6L, result.observed)
        assertEquals(5L, result.limit)
    }

    @Test
    fun structurallyEmptyTableIsUnavailableInsteadOfAFalsePositive() {
        val layout = EvolutionTableLayout(0x1000, 10, 10, 8)
        val bytes = ByteArray(0x3000)
        val references = mapOf(layout.offset.toInt() to 1, layout.endExclusive.toInt() to 1)

        val result = resolver.resolveGen3(
            evolutionSession(bytes, references),
            expectedSpeciesCount = 10,
        )

        assertTrue(result is DatasetResolution.Unavailable)
    }

    @Test
    fun resolvesLegacyLevelMethodStructuralAnchorOnlyAfterStandardEvidenceIsUnavailable() {
        val bytes = ByteArray(0x2000)
        val layout = EvolutionTableLayout(0x400, 4, slotsPerSpecies = 8, recordSize = 8)
        putEvolution(bytes, layout.rowOffset(1), 4, 16, 2)
        putEvolution(bytes, layout.rowOffset(2), 4, 32, 3)

        val result = resolver.resolveGen3(
            evolutionSession(bytes),
            expectedSpeciesCount = 4,
        ) as DatasetResolution.Resolved

        assertEquals(CandidateSource.STRUCTURAL_ANCHOR, result.candidate.source)
        assertEquals(layout, result.candidate.layout.table)
    }

    @Test
    fun rejectsIsolatedLevelMethodBytesAsStructuralDecoys() {
        val bytes = ByteArray(0x2000)
        val layout = EvolutionTableLayout(0x400, 4, slotsPerSpecies = 8, recordSize = 8)
        putEvolution(bytes, layout.rowOffset(1), 4, 16, 2)
        // No independent second-species anchor at the matching stride.

        val result = resolver.resolveGen3(
            evolutionSession(bytes),
            expectedSpeciesCount = 4,
        )

        assertTrue(result is DatasetResolution.Unavailable)
    }

    @Test
    fun boundsLegacyStructuralAnchorScanningWithTypedProbeWorkEvidence() {
        val result = resolver.resolveGen3(
            evolutionSession(
                ByteArray(0x2000),
                limits = ResolutionLimits(maxProbeWorkPerDataset = 5),
            ),
            expectedSpeciesCount = 4,
        ) as DatasetResolution.BudgetExceeded

        assertEquals(BudgetKind.PROBE_WORK, result.budgetKind)
        assertEquals(6L, result.observed)
        assertEquals(5L, result.limit)
    }
}
