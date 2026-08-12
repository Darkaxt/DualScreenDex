package com.enrpau.dualscreendex.parser.dataset.core.basestats

import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.resolution.BudgetKind
import com.enrpau.dualscreendex.parser.resolution.CandidateSource
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseStatsResolverTest {
    @Test
    fun exactProfileFastPathResolvesBeforeBuildingTheReferenceIndex() {
        val bytes = ByteArray(256)
        val table = BaseStatsTableLayout(64, 3, BaseStatsAbi.RETAIL_28)
        putRetailBaseStats(bytes, 64 + 28)
        putRetailBaseStats(bytes, 64 + 56)
        var indexBuilds = 0
        val session = baseStatsSession(bytes, exactLayout = table, onReferenceIndexBuild = { indexBuilds++ })

        val result = BaseStatsResolver.resolve(session, BaseStatsSemanticDomain(3, setOf(1, 2)))

        assertTrue(result is DatasetResolution.Resolved)
        result as DatasetResolution.Resolved
        assertEquals(CandidateSource.EXACT_PROFILE, result.candidate.source)
        assertEquals(table, result.candidate.layout.table)
        assertEquals(0, indexBuilds)
    }

    @Test
    fun unsupportedExactProfileAbiFailsClosedBeforeBuildingTheReferenceIndex() {
        val bytes = ByteArray(256)
        val table = BaseStatsTableLayout(64, 3, BaseStatsAbi.RETAIL_28)
        val fallback = BaseStatsTableLayout(128, 3, BaseStatsAbi.RETAIL_28)
        putRetailBaseStats(bytes, 128 + 28)
        putRetailBaseStats(bytes, 128 + 56)
        var indexBuilds = 0
        val session = baseStatsSession(
            bytes = bytes,
            references = mapOf(128 to 3),
            exactLayout = table,
            exactRecordSize = 30,
            onReferenceIndexBuild = { indexBuilds++ },
        )

        val result = BaseStatsResolver.resolve(
            session,
            BaseStatsSemanticDomain(3, setOf(1, 2)),
            publishedLayouts = listOf(fallback),
        )

        assertTrue(result is DatasetResolution.Unavailable)
        assertEquals(0, indexBuilds)
    }

    @Test
    fun retainsAUniqueHeavilyReferencedPublishedPartialWithOnlyExactZeroGaps() {
        val count = 10
        val root = 128
        val bytes = ByteArray(640)
        (1..7).forEach { putRetailBaseStats(bytes, root + it * 28) }
        val result = BaseStatsResolver.resolve(
            session = baseStatsSession(bytes, references = mapOf(root to 3)),
            semanticDomain = BaseStatsSemanticDomain(count.toLong(), (1 until count).toSet()),
            publishedLayouts = listOf(BaseStatsTableLayout(root.toLong(), count.toLong(), BaseStatsAbi.RETAIL_28)),
        )

        assertTrue(result is DatasetResolution.Partial)
        result as DatasetResolution.Partial
        assertEquals(7, result.candidate.strength.semanticCoverage?.covered)
        assertEquals(9, result.candidate.strength.semanticCoverage?.expected)
        assertTrue(result.candidate.layout.rows[8] is BaseStatsRowOutcome.StructuralEmpty)
        assertTrue(result.candidate.provenance.validatorReviewRecommended)
    }

    @Test
    fun activeZeroRowsStayIncompleteEvenWhenOnlyOneIsMissing() {
        val root = 64
        val bytes = ByteArray(512)
        (1..9).forEach { putRetailBaseStats(bytes, root + it * 28) }
        val result = BaseStatsResolver.resolve(
            session = baseStatsSession(bytes, references = mapOf(root to 3)),
            semanticDomain = BaseStatsSemanticDomain(11, (1..10).toSet()),
            publishedLayouts = listOf(BaseStatsTableLayout(root.toLong(), 11, BaseStatsAbi.RETAIL_28)),
        )

        assertTrue(result is DatasetResolution.Partial)
        assertEquals(9, (result as DatasetResolution.Partial).candidate.strength.semanticCoverage?.covered)
    }

    @Test
    fun rejectsPartialPublishedTablesBelowSeventyPercentAuthoritativeCoverage() {
        val root = 64
        val bytes = ByteArray(512)
        (1..6).forEach { putRetailBaseStats(bytes, root + it * 28) }

        val result = BaseStatsResolver.resolve(
            baseStatsSession(bytes, references = mapOf(root to 9)),
            BaseStatsSemanticDomain(11, (1..10).toSet()),
            publishedLayouts = listOf(BaseStatsTableLayout(root.toLong(), 11, BaseStatsAbi.RETAIL_28)),
        )

        assertTrue(result is DatasetResolution.Unavailable)
    }

    @Test
    fun semanticCoverageUsesOnlyTheExplicitActiveDomain() {
        val root = 64
        val bytes = ByteArray(512)
        (1..6).forEach { putRetailBaseStats(bytes, root + it * 28) }
        (8..10).forEach { putRetailBaseStats(bytes, root + it * 28) }

        val result = BaseStatsResolver.resolve(
            baseStatsSession(bytes, references = mapOf(root to 9)),
            BaseStatsSemanticDomain(11, (1..10).toSet()),
            publishedLayouts = listOf(BaseStatsTableLayout(root.toLong(), 11, BaseStatsAbi.RETAIL_28)),
        )

        assertTrue(result is DatasetResolution.Partial)
        val coverage = (result as DatasetResolution.Partial).candidate.strength.semanticCoverage
        assertEquals(9, coverage?.covered)
        assertEquals(10, coverage?.expected)
    }

    @Test
    fun rejectsAnyNonzeroMalformedDecoyEvenWhenAggregateCoverageIsHigh() {
        val root = 64
        val bytes = ByteArray(512)
        (1..9).forEach { putRetailBaseStats(bytes, root + it * 28) }
        bytes[root + 10 * 28 + 8] = 1

        val result = BaseStatsResolver.resolve(
            baseStatsSession(bytes, references = mapOf(root to 9)),
            BaseStatsSemanticDomain(11, (1..10).toSet()),
            publishedLayouts = listOf(BaseStatsTableLayout(root.toLong(), 11, BaseStatsAbi.RETAIL_28)),
        )

        assertTrue(result is DatasetResolution.Unavailable)
    }

    @Test
    fun failsClosedWhenBothTwentyEightAndThirtyTwoByteAbisQualifyAtOneRoot() {
        val root = 128
        val bytes = ByteArray(1024)
        (root until root + 6 * 32).forEach { bytes[it] = 1 }
        val domain = BaseStatsSemanticDomain(6, (0 until 6).toSet())

        val result = BaseStatsResolver.resolve(
            baseStatsSession(bytes, references = mapOf(root to 4)),
            domain,
            publishedLayouts = listOf(
                BaseStatsTableLayout(root.toLong(), 6, BaseStatsAbi.RETAIL_28),
                BaseStatsTableLayout(root.toLong(), 6, BaseStatsAbi.BATTLE_ENGINE_32),
            ),
        )

        assertTrue(result is DatasetResolution.Ambiguous)
    }

    @Test
    fun abiConflictDiagnosticsAreIndependentOfPublishedRootEnumerationOrder() {
        val roots = listOf(128, 640)
        val bytes = ByteArray(1280)
        roots.forEach { root ->
            (root until root + 6 * 32).forEach { bytes[it] = 1 }
        }
        val layouts = roots.flatMap { root ->
            listOf(
                BaseStatsTableLayout(root.toLong(), 6, BaseStatsAbi.RETAIL_28),
                BaseStatsTableLayout(root.toLong(), 6, BaseStatsAbi.BATTLE_ENGINE_32),
            )
        }
        val domain = BaseStatsSemanticDomain(6, (0 until 6).toSet())

        fun resolve(proposals: List<BaseStatsTableLayout>) = BaseStatsResolver.resolve(
            baseStatsSession(bytes, references = roots.associateWith { 4 }),
            domain,
            publishedLayouts = proposals,
        ) as DatasetResolution.Ambiguous

        assertEquals(
            resolve(layouts).candidates.map { it.layoutIdentity },
            resolve(layouts.reversed()).candidates.map { it.layoutIdentity },
        )
    }

    @Test
    fun carriesBoundedCompiledInstructionSitesIntoCandidateProvenance() {
        val root = 256
        val bytes = ByteArray(1024)
        putRetailBaseStats(bytes, root)
        putThumbLiteralReferences(bytes, 0, 64, root, root, root)

        val result = BaseStatsResolver.resolve(
            baseStatsSession(bytes, useDefaultReferenceIndex = true),
            BaseStatsSemanticDomain(1, setOf(0)),
            publishedLayouts = listOf(BaseStatsTableLayout(root.toLong(), 1, BaseStatsAbi.RETAIL_28)),
        ) as DatasetResolution.Resolved

        assertEquals(listOf(0, 2, 4), result.candidate.provenance.compiledReferenceSites.offsets)
        assertEquals(3, result.candidate.strength.compiledReferenceCount)
    }

    @Test
    fun supportsInheritedCompiledAndPermittedStructuralAuthorityWithoutOffsetRanking() {
        val low = 64
        val high = 256
        val bytes = ByteArray(512)
        putRetailBaseStats(bytes, low)
        putRetailBaseStats(bytes, high)
        val domain = BaseStatsSemanticDomain(1, setOf(0))

        val first = BaseStatsResolver.resolve(
            baseStatsSession(bytes, references = mapOf(low to 2, high to 2)),
            domain,
            publishedLayouts = listOf(
                BaseStatsTableLayout(high.toLong(), 1, BaseStatsAbi.RETAIL_28),
                BaseStatsTableLayout(low.toLong(), 1, BaseStatsAbi.RETAIL_28),
            ),
            inheritedLayouts = listOf(BaseStatsTableLayout(low.toLong(), 1, BaseStatsAbi.RETAIL_28)),
            structuralLayouts = listOf(BaseStatsTableLayout(high.toLong(), 1, BaseStatsAbi.RETAIL_28)),
        )
        val second = BaseStatsResolver.resolve(
            baseStatsSession(bytes, references = mapOf(low to 2, high to 2)),
            domain,
            publishedLayouts = listOf(
                BaseStatsTableLayout(low.toLong(), 1, BaseStatsAbi.RETAIL_28),
                BaseStatsTableLayout(high.toLong(), 1, BaseStatsAbi.RETAIL_28),
            ),
        )

        assertTrue(first is DatasetResolution.Ambiguous)
        assertTrue(second is DatasetResolution.Ambiguous)
        assertEquals(
            (first as DatasetResolution.Ambiguous).candidates.map { it.layoutIdentity },
            (second as DatasetResolution.Ambiguous).candidates.map { it.layoutIdentity },
        )
    }

    @Test
    fun returnsTypedRootWorkCandidateAndExtentBudgetExhaustion() {
        val bytes = ByteArray(512)
        putRetailBaseStats(bytes, 64)
        putRetailBaseStats(bytes, 128)
        val domain = BaseStatsSemanticDomain(1, setOf(0))
        val layouts = listOf(
            BaseStatsTableLayout(64, 1, BaseStatsAbi.RETAIL_28),
            BaseStatsTableLayout(128, 1, BaseStatsAbi.RETAIL_28),
        )

        val root = BaseStatsResolver.resolve(
            baseStatsSession(bytes, limits = ResolutionLimits(maxProbeRootsPerDataset = 1)),
            domain,
            inheritedLayouts = layouts,
        ) as DatasetResolution.BudgetExceeded
        val work = BaseStatsResolver.resolve(
            baseStatsSession(bytes, limits = ResolutionLimits(maxProbeWorkPerDataset = 1)),
            domain,
            inheritedLayouts = layouts,
        ) as DatasetResolution.BudgetExceeded
        val candidate = BaseStatsResolver.resolve(
            baseStatsSession(bytes, limits = ResolutionLimits(maxCandidatesPerDataset = 1)),
            domain,
            inheritedLayouts = layouts,
        ) as DatasetResolution.BudgetExceeded
        val extent = BaseStatsResolver.resolve(
            baseStatsSession(bytes, limits = ResolutionLimits(maxDatasetExtentBytes = 27)),
            domain,
            inheritedLayouts = listOf(layouts.first()),
        ) as DatasetResolution.BudgetExceeded

        assertEquals(BudgetKind.PROBE_ROOTS, root.budgetKind)
        assertEquals(BudgetKind.PROBE_WORK, work.budgetKind)
        assertEquals(BudgetKind.CANDIDATES, candidate.budgetKind)
        assertEquals(BudgetKind.EXTENT, extent.budgetKind)
    }

    @Test
    fun workBudgetStopsEnumeratingCallerLayoutsAtTheOverflowWitness() {
        val layouts = listOf(
            BaseStatsTableLayout(64, 1, BaseStatsAbi.RETAIL_28),
            BaseStatsTableLayout(128, 1, BaseStatsAbi.RETAIL_28),
            BaseStatsTableLayout(192, 1, BaseStatsAbi.RETAIL_28),
        )
        var consumed = 0
        val guarded = object : AbstractCollection<BaseStatsTableLayout>() {
            override val size: Int = layouts.size

            override fun iterator(): Iterator<BaseStatsTableLayout> = object : Iterator<BaseStatsTableLayout> {
                private var index = 0
                override fun hasNext(): Boolean = index < layouts.size

                override fun next(): BaseStatsTableLayout {
                    consumed++
                    check(consumed <= 2) { "resolver enumerated beyond the work-budget overflow witness" }
                    return layouts[index++]
                }
            }
        }

        val result = BaseStatsResolver.resolve(
            baseStatsSession(
                ByteArray(256),
                limits = ResolutionLimits(maxProbeWorkPerDataset = 1),
            ),
            BaseStatsSemanticDomain(1, setOf(0)),
            inheritedLayouts = guarded,
        ) as DatasetResolution.BudgetExceeded

        assertEquals(BudgetKind.PROBE_WORK, result.budgetKind)
        assertEquals(2, consumed)
    }

    @Test
    fun sourceAuthorityPrefersPublishedOverInheritedAndCompiledCandidates() {
        val published = 256
        val inherited = 384
        val bytes = ByteArray(512)
        putRetailBaseStats(bytes, published)
        putRetailBaseStats(bytes, inherited)

        val result = BaseStatsResolver.resolve(
            baseStatsSession(bytes, references = mapOf(published to 1, inherited to 9)),
            BaseStatsSemanticDomain(1, setOf(0)),
            publishedLayouts = listOf(BaseStatsTableLayout(published.toLong(), 1, BaseStatsAbi.RETAIL_28)),
            inheritedLayouts = listOf(BaseStatsTableLayout(inherited.toLong(), 1, BaseStatsAbi.RETAIL_28)),
        ) as DatasetResolution.Resolved

        assertEquals(CandidateSource.PUBLISHED_HEADER, result.candidate.source)
        assertFalse(result.candidate.layout.table.offset == inherited.toLong())
    }

    @Test
    fun resolvesAnExplicitCompiledReferenceLayoutWithCompiledAuthority() {
        val root = 128
        val bytes = ByteArray(256)
        putRetailBaseStats(bytes, root)

        val result = BaseStatsResolver.resolve(
            baseStatsSession(bytes, references = mapOf(root to 2)),
            BaseStatsSemanticDomain(1, setOf(0)),
            compiledLayouts = listOf(BaseStatsTableLayout(root.toLong(), 1, BaseStatsAbi.RETAIL_28)),
        ) as DatasetResolution.Resolved

        assertEquals(CandidateSource.COMPILED_REFERENCE, result.candidate.source)
        assertEquals(2, result.candidate.strength.compiledReferenceCount)
    }
}
