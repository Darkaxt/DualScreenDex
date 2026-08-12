package com.enrpau.dualscreendex.parser.dataset.types

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.resolution.BudgetKind
import com.enrpau.dualscreendex.parser.resolution.CandidateSource
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeChartResolverTest {
    @Test
    fun exactLegacyFastPathDoesNotBuildTheReferenceIndex() {
        val bytes = ByteArray(128)
        putLegacyTypeChart(bytes, 32)
        var builds = 0

        val result = TypeChartResolver.resolve(
            session = typeChartSession(
                bytes,
                exactTable = TableLayout(32, 0, 3, variableLength = true),
                onReferenceIndexBuild = { builds++ },
            ),
            activeTypeIds = (0 until 18).toSet(),
        ) as DatasetResolution.Resolved

        assertEquals(CandidateSource.EXACT_PROFILE, result.candidate.source)
        assertEquals(TypeChartAbi.LEGACY_TRIPLETS, result.candidate.layout.table.abi)
        assertEquals(0, builds)
    }

    @Test
    fun exactDenseU32UsesPublishedTableLayoutSemanticsWithoutBuildingTheReferenceIndex() {
        val root = 64
        val typeCount = 18
        val bytes = ByteArray(root + typeCount * typeCount * 4)
        putU32Q412Matrix(bytes, root, typeCount)
        var builds = 0

        val result = TypeChartResolver.resolve(
            session = typeChartSession(
                bytes,
                exactTable = TableLayout(
                    offset = root,
                    count = typeCount * typeCount,
                    recordSize = typeCount * 4,
                    elementSize = 4,
                ),
                onReferenceIndexBuild = { builds++ },
            ),
            activeTypeIds = (0 until typeCount).toSet(),
        ) as DatasetResolution.Resolved

        assertEquals(CandidateSource.EXACT_PROFILE, result.candidate.source)
        assertEquals(TypeChartAbi.DENSE_U32_Q412, result.candidate.layout.table.abi)
        assertEquals(typeCount, result.candidate.layout.table.typeCount)
        assertEquals(0, builds)
    }

    @Test
    fun exactVariableLengthU16UsesElementAndRowWidthSemanticsWithoutBuildingTheReferenceIndex() {
        val root = 66
        val typeCount = 19
        val bytes = ByteArray(root + typeCount * typeCount * 4)
        putU16Q412Pair(bytes, root, typeCount)
        var builds = 0

        val result = TypeChartResolver.resolve(
            session = typeChartSession(
                bytes,
                exactTable = TableLayout(
                    offset = root,
                    count = typeCount * typeCount,
                    recordSize = typeCount * 2,
                    variableLength = true,
                    elementSize = 2,
                ),
                onReferenceIndexBuild = { builds++ },
            ),
            activeTypeIds = (0 until typeCount).toSet(),
        ) as DatasetResolution.Resolved

        assertEquals(CandidateSource.EXACT_PROFILE, result.candidate.source)
        assertEquals(TypeChartAbi.DENSE_U16_Q412_WITH_INVERSE, result.candidate.layout.table.abi)
        assertEquals(typeCount, result.candidate.layout.table.typeCount)
        assertEquals(0, builds)
    }

    @Test
    fun unsupportedExactTypeChartFailsClosedWithoutFallingBackToReferencedDiscovery() {
        val root = 64
        val typeCount = 18
        val end = root + typeCount * typeCount * 4
        val bytes = ByteArray(end + 8)
        putU32Q412Matrix(bytes, root, typeCount)
        var builds = 0

        val result = TypeChartResolver.resolve(
            session = typeChartSession(
                bytes,
                references = mapOf(root to 1, end to 1),
                exactTable = TableLayout(
                    offset = root,
                    count = typeCount * typeCount,
                    recordSize = typeCount * 3,
                    variableLength = true,
                    elementSize = 3,
                ),
                onReferenceIndexBuild = { builds++ },
            ),
            activeTypeIds = (0 until typeCount).toSet(),
            compiledRoots = listOf(root),
        )

        assertTrue(result is DatasetResolution.Unavailable)
        assertEquals(0, builds)
    }

    @Test
    fun resolvesReferencedDenseU32ByScanningTheActiveLowerBoundThroughSixtyFour() {
        val root = 64
        val typeCount = 20
        val end = root + typeCount * typeCount * 4
        val bytes = ByteArray(end + 8)
        putU32Q412Matrix(bytes, root, typeCount)

        val result = TypeChartResolver.resolve(
            typeChartSession(bytes, references = mapOf(root to 2, end to 1)),
            activeTypeIds = (0 until 19).toSet(),
            compiledRoots = listOf(root.toLong()),
        ) as DatasetResolution.Resolved

        assertEquals(TypeChartAbi.DENSE_U32_Q412, result.candidate.layout.table.abi)
        assertEquals(typeCount, result.candidate.layout.table.typeCount)
    }

    @Test
    fun sameRootMultipleReferencedDimensionsRemainAmbiguous() {
        val root = 64
        val small = 20
        val large = 21
        val smallEnd = root + small * small * 4
        val largeEnd = root + large * large * 4
        val bytes = ByteArray(largeEnd + 8)
        putU32Q412Matrix(bytes, root, small)
        val padding = longArrayOf(0, 819, 2048, 4096, 8192)
        repeat(large * large - small * small) { index ->
            putTypeU32(bytes, smallEnd + index * 4, padding[index % padding.size])
        }

        val result = TypeChartResolver.resolve(
            typeChartSession(bytes, references = mapOf(root to 1, smallEnd to 1, largeEnd to 1)),
            activeTypeIds = (0 until 19).toSet(),
            compiledRoots = listOf(root.toLong()),
        )

        assertTrue(result is DatasetResolution.Ambiguous)
    }

    @Test
    fun doesNotInflateToNPlusOneWithoutAnExactlyReferencedEnd() {
        val root = 64
        val exact = 20
        val padded = 21
        val exactEnd = root + exact * exact * 4
        val bytes = ByteArray(root + padded * padded * 4 + 8)
        putU32Q412Matrix(bytes, root, exact)
        repeat(padded * padded - exact * exact) { index ->
            putTypeU32(bytes, exactEnd + index * 4, longArrayOf(0, 819, 2048, 4096, 8192)[index % 5])
        }

        val result = TypeChartResolver.resolve(
            typeChartSession(bytes, references = mapOf(root to 1, exactEnd to 1)),
            activeTypeIds = (0 until 19).toSet(),
            compiledRoots = listOf(root.toLong()),
        ) as DatasetResolution.Resolved

        assertEquals(exact, result.candidate.layout.table.typeCount)
    }

    @Test
    fun fullPairInteriorPruningIsNonTransitiveAndKeepsTheLaterIndependentRootAmbiguous() {
        val root = 66
        val n = 19
        val matrixBytes = n * n * 2
        val pairBytes = matrixBytes * 2
        val interior = root + matrixBytes
        val independent = root + pairBytes
        val bytes = ByteArray(root + matrixBytes * 4 + 8)
        putConsecutiveInverseU16Matrices(bytes, root, n, 4)
        val references = mapOf(
            root to 1,
            interior to 1,
            independent to 1,
            independent + matrixBytes to 1,
            independent + pairBytes to 1,
        )

        val result = TypeChartResolver.resolve(
            typeChartSession(bytes, references = references),
            activeTypeIds = (0 until n).toSet(),
            compiledRoots = listOf(root.toLong(), interior.toLong(), independent.toLong()),
        ) as DatasetResolution.Ambiguous

        assertEquals(listOf(root.toLong(), independent.toLong()), result.candidates.map { it.layout.table.offset })
    }

    @Test
    fun rejectsUiOnlyUnreferencedAndCorruptInverseDecoys() {
        val root = 66
        val n = 19
        val pairEnd = root + n * n * 4
        val bytes = ByteArray(pairEnd + 8)
        putU16Q412Pair(bytes, root, n)
        putTypeU16(bytes, root + n * n * 2, 2048)

        val result = TypeChartResolver.resolve(
            typeChartSession(bytes, references = mapOf(root to 1, pairEnd to 1)),
            activeTypeIds = (0 until n).toSet(),
            compiledRoots = listOf(root.toLong()),
        )

        assertTrue(result is DatasetResolution.Unavailable)
    }

    @Test
    fun legacySparseEncodingWinsAnEqualAuthorityTieWithoutOffsetOrOrderRanking() {
        val sparse = 64
        val dense = 256
        val n = 18
        val denseEnd = dense + n * n * 4
        val bytes = ByteArray(denseEnd + 8)
        putLegacyTypeChart(bytes, sparse)
        putU32Q412Matrix(bytes, dense, n)
        val references = mapOf(sparse to 1, dense to 1, denseEnd to 1)

        fun resolve(roots: List<Long>) = TypeChartResolver.resolve(
            typeChartSession(bytes, references = references),
            activeTypeIds = (0 until n).toSet(),
            inheritedRoots = roots,
        ) as DatasetResolution.Resolved

        assertEquals(TypeChartAbi.LEGACY_TRIPLETS, resolve(listOf(sparse.toLong(), dense.toLong())).candidate.layout.table.abi)
        assertEquals(TypeChartAbi.LEGACY_TRIPLETS, resolve(listOf(dense.toLong(), sparse.toLong())).candidate.layout.table.abi)
    }

    @Test
    fun sourceAuthorityIsDirectThenPublishedThenCompiledThenInherited() {
        val roots = listOf(64, 128, 192, 256)
        val bytes = ByteArray(512)
        roots.forEach { putLegacyTypeChart(bytes, it) }
        val references = roots.associateWith { 1 }

        val result = TypeChartResolver.resolve(
            typeChartSession(bytes, references = references),
            activeTypeIds = (0 until 18).toSet(),
            directCompiledConsumerRoots = listOf(256),
            publishedRoots = listOf(192),
            compiledRoots = listOf(128),
            inheritedRoots = listOf(64),
        ) as DatasetResolution.Resolved

        assertEquals(CandidateSource.DIRECT_COMPILED_CONSUMER, result.candidate.source)
        assertEquals(256L, result.candidate.layout.table.offset)
    }

    @Test
    fun allResolverBudgetsFailClosedWithTypedEvidence() {
        val root = 64
        val n = 18
        val end = root + n * n * 4
        val bytes = ByteArray(end + 8)
        putU32Q412Matrix(bytes, root, n)
        val roots = listOf(root.toLong(), (root + 4).toLong())
        val active = (0 until n).toSet()

        val reference = TypeChartResolver.resolve(
            typeChartSession(
                bytes,
                referenceIndex = GbaReferenceIndex.budgetExceeded("targets", 2, 1),
            ),
            active,
            compiledRoots = listOf(root.toLong()),
        ) as DatasetResolution.BudgetExceeded
        val rootBudget = TypeChartResolver.resolve(
            typeChartSession(bytes, limits = ResolutionLimits(maxProbeRootsPerDataset = 1)),
            active,
            inheritedRoots = roots,
        ) as DatasetResolution.BudgetExceeded
        val work = TypeChartResolver.resolve(
            typeChartSession(bytes, references = mapOf(root to 1, end to 1), limits = ResolutionLimits(maxProbeWorkPerDataset = 1)),
            active,
            compiledRoots = listOf(root.toLong()),
        ) as DatasetResolution.BudgetExceeded
        val extent = TypeChartResolver.resolve(
            typeChartSession(bytes, references = mapOf(root to 1, end to 1), limits = ResolutionLimits(maxDatasetExtentBytes = 100)),
            active,
            compiledRoots = listOf(root.toLong()),
        ) as DatasetResolution.BudgetExceeded

        assertEquals(BudgetKind.REFERENCE_TARGETS, reference.budgetKind)
        assertEquals(BudgetKind.PROBE_ROOTS, rootBudget.budgetKind)
        assertEquals(BudgetKind.PROBE_WORK, work.budgetKind)
        assertEquals(BudgetKind.EXTENT, extent.budgetKind)
    }

    @Test
    fun candidateBudgetIsEnforcedBeforePairInteriorPruningCanDiscardTheOverflow() {
        val root = 66
        val typeCount = 19
        val matrixBytes = typeCount * typeCount * 2
        val interior = root + matrixBytes
        val pairEnd = root + matrixBytes * 2
        val interiorPairEnd = root + matrixBytes * 3
        val bytes = ByteArray(interiorPairEnd + 8)
        putConsecutiveInverseU16Matrices(bytes, root, typeCount, matrixCount = 3)

        val result = TypeChartResolver.resolve(
            session = typeChartSession(
                bytes,
                references = mapOf(root to 1, interior to 1, pairEnd to 1, interiorPairEnd to 1),
                limits = ResolutionLimits(maxCandidatesPerDataset = 1),
            ),
            activeTypeIds = (0 until typeCount).toSet(),
            compiledRoots = listOf(root, interior),
        ) as DatasetResolution.BudgetExceeded

        assertEquals(BudgetKind.CANDIDATES, result.budgetKind)
        assertEquals(2L, result.observed)
        assertEquals(1L, result.limit)
    }

    @Test
    fun compiledReferenceSiteUnionOverflowFailsClosedWithTypedEvidence() {
        val root = 64
        val typeCount = 18
        val end = root + typeCount * typeCount * 4
        val bytes = ByteArray(end + 8)
        putU32Q412Matrix(bytes, root, typeCount)
        val referenceIndex = GbaReferenceIndex.fromTargets(
            targets = mapOf(
                root to completeReferenceEvidence(site = 4),
                end to completeReferenceEvidence(site = 8),
            ),
            limitTargets = 8,
        )

        val result = TypeChartResolver.resolve(
            session = typeChartSession(
                bytes,
                limits = ResolutionLimits(maxCompiledReferenceSitesPerCandidate = 1),
                referenceIndex = referenceIndex,
            ),
            activeTypeIds = (0 until typeCount).toSet(),
            compiledRoots = listOf(root),
        ) as DatasetResolution.BudgetExceeded

        assertEquals(BudgetKind.REFERENCE_SITES, result.budgetKind)
        assertEquals(2L, result.observed)
        assertEquals(1L, result.limit)
    }

    @Test
    fun activeTypeDomainRejectsIdsBeyondTheBoundedMaximum() {
        val result = TypeChartResolver.resolve(
            typeChartSession(ByteArray(64)),
            activeTypeIds = setOf(64),
            inheritedRoots = listOf(0),
        )

        assertTrue(result is DatasetResolution.Unavailable)
        assertFalse((result as DatasetResolution.Unavailable).reason.isBlank())
    }

    private fun completeReferenceEvidence(site: Int) = GbaTargetReferenceEvidence(
        count = 1,
        instructionSites = listOf(site),
        observedSites = 1,
        limitSites = 1,
        overflowReason = null,
        siteEvidenceUnavailableReason = null,
    )
}
