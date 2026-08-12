package com.enrpau.dualscreendex.parser.dataset.moves

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.resolution.BudgetKind
import com.enrpau.dualscreendex.parser.resolution.CandidateSource
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveDetailsResolverTest {
    @Test
    fun selectedLayoutIsTheOnlyCandidateAndDoesNotBuildDiscoveryEvidence() {
        val bytes = ByteArray(256)
        val exact = MoveDetailsTableLayout(32, 2, MoveDetailsAbi.RETAIL_12)
        val selected = MoveDetailsTableLayout(128, 2, MoveDetailsAbi.CFRU_16)
        putRetailMove(bytes, 32 + 12)
        putCfruMove(bytes, 128 + 16)
        var indexBuilds = 0

        val result = MoveDetailsResolver.resolve(
            session = moveDetailsSession(bytes, exactLayout = exact, onReferenceIndexBuild = { indexBuilds++ }),
            semanticDomain = MoveDetailsSemanticDomain(2, setOf(1)),
            selectedLayout = selected,
        ) as DatasetResolution.Resolved

        assertEquals(selected, result.candidate.layout.table)
        assertEquals(CandidateSource.INHERITED_FAMILY_LAYOUT, result.candidate.source)
        assertEquals(0, indexBuilds)
    }

    @Test
    fun exactProfileFastPathResolvesBeforeBuildingTheReferenceIndex() {
        val bytes = ByteArray(128)
        val layout = MoveDetailsTableLayout(64, 2, MoveDetailsAbi.RETAIL_12)
        putRetailMove(bytes, 64 + 12)
        var indexBuilds = 0
        val result = MoveDetailsResolver.resolve(
            session = moveDetailsSession(bytes, exactLayout = layout, onReferenceIndexBuild = { indexBuilds++ }),
            semanticDomain = MoveDetailsSemanticDomain(2, setOf(1)),
        ) as DatasetResolution.Resolved

        assertEquals(CandidateSource.EXACT_PROFILE, result.candidate.source)
        assertEquals(layout, result.candidate.layout.table)
        assertEquals(0, indexBuilds)
    }

    @Test
    fun unsupportedExactProfileFormatFailsClosedWithoutTryingFallbackCandidates() {
        val bytes = ByteArray(256)
        val exact = MoveDetailsTableLayout(32, 2, MoveDetailsAbi.RETAIL_12)
        val fallback = MoveDetailsTableLayout(128, 2, MoveDetailsAbi.RETAIL_12)
        putRetailMove(bytes, 128 + 12)
        var indexBuilds = 0

        val result = MoveDetailsResolver.resolve(
            session = moveDetailsSession(
                bytes,
                exactLayout = exact,
                exactRecordSize = 12,
                exactFormat = com.enrpau.dualscreendex.parser.model.TableRecordFormat.BATTLE_ENGINE_MOVE_20,
                onReferenceIndexBuild = { indexBuilds++ },
            ),
            semanticDomain = MoveDetailsSemanticDomain(2, setOf(1)),
            inheritedLayouts = listOf(fallback),
        )

        assertTrue(result is DatasetResolution.Unavailable)
        assertEquals(0, indexBuilds)
    }

    @Test
    fun competesAllThreeAbisOnceAndSelectsTheOnlyPlausibleBattleEngineLayout() {
        val root = 128
        val count = 5
        val bytes = ByteArray(512)
        (1 until count).forEach { putBattleEngineMove(bytes, root + it * 20) }
        val layouts = MoveDetailsAbi.entries.map { MoveDetailsTableLayout(root.toLong(), count.toLong(), it) }

        val result = MoveDetailsResolver.resolve(
            moveDetailsSession(bytes, references = mapOf(root to 4)),
            MoveDetailsSemanticDomain(count.toLong(), (1 until count).toSet()),
            compiledLayouts = layouts,
        ) as DatasetResolution.Resolved

        assertEquals(MoveDetailsAbi.BATTLE_ENGINE_20, result.candidate.layout.table.abi)
    }

    @Test
    fun failsClosedWhenTwoDimensionsAtTheSameRootAreEquallyValid() {
        val bytes = ByteArray(128)
        putCfruMove(
            bytes = bytes,
            offset = 32,
            effect = 1,
            power = 1,
            type = 1,
            accuracy = 100,
            pp = 35,
            secondaryChance = 7,
            target = 4,
            priority = 0,
            split = 1,
            flags = 0,
        )
        // These bytes are deliberately plausible under both the 12-byte and 16-byte dimensions.
        val domain = MoveDetailsSemanticDomain(1, setOf(0))
        val result = MoveDetailsResolver.resolve(
            moveDetailsSession(bytes),
            domain,
            inheritedLayouts = listOf(
                MoveDetailsTableLayout(32, 1, MoveDetailsAbi.RETAIL_12),
                MoveDetailsTableLayout(32, 1, MoveDetailsAbi.CFRU_16),
            ),
        )

        assertTrue(result is DatasetResolution.Ambiguous)
    }

    @Test
    fun sameRootAbiConflictFailsClosedEvenWhenInactiveRowsMakeOneDimensionLookCleaner() {
        val bytes = ByteArray(160)
        val root = 32
        putCfruMove(
            bytes = bytes,
            offset = root,
            effect = 1,
            power = 1,
            type = 1,
            accuracy = 100,
            pp = 35,
            secondaryChance = 7,
            target = 4,
            priority = 0,
            split = 1,
            flags = 0,
        )
        putRetailMove(bytes, root + 12)

        val result = MoveDetailsResolver.resolve(
            moveDetailsSession(bytes),
            MoveDetailsSemanticDomain(2, setOf(0)),
            inheritedLayouts = listOf(
                MoveDetailsTableLayout(root.toLong(), 2, MoveDetailsAbi.RETAIL_12),
                MoveDetailsTableLayout(root.toLong(), 2, MoveDetailsAbi.CFRU_16),
            ),
        )

        assertTrue(result is DatasetResolution.Ambiguous)
    }

    @Test
    fun equalIndependentRootsRemainAmbiguousRegardlessOfEnumerationOrder() {
        val bytes = ByteArray(256)
        putRetailMove(bytes, 64)
        putRetailMove(bytes, 128)
        val layouts = listOf(
            MoveDetailsTableLayout(64, 1, MoveDetailsAbi.RETAIL_12),
            MoveDetailsTableLayout(128, 1, MoveDetailsAbi.RETAIL_12),
        )

        fun resolve(values: List<MoveDetailsTableLayout>) = MoveDetailsResolver.resolve(
            moveDetailsSession(bytes, references = mapOf(64 to 2, 128 to 2)),
            MoveDetailsSemanticDomain(1, setOf(0)),
            compiledLayouts = values,
        ) as DatasetResolution.Ambiguous

        assertEquals(
            resolve(layouts).candidates.map { it.layoutIdentity },
            resolve(layouts.reversed()).candidates.map { it.layoutIdentity },
        )
    }

    @Test
    fun independentCompiledReferenceStrengthBreaksAWithinAuthorityTieWithoutOffsetRanking() {
        val bytes = ByteArray(256)
        putRetailMove(bytes, 64)
        putRetailMove(bytes, 128)
        val result = MoveDetailsResolver.resolve(
            moveDetailsSession(bytes, references = mapOf(64 to 2, 128 to 5)),
            MoveDetailsSemanticDomain(1, setOf(0)),
            compiledLayouts = listOf(
                MoveDetailsTableLayout(64, 1, MoveDetailsAbi.RETAIL_12),
                MoveDetailsTableLayout(128, 1, MoveDetailsAbi.RETAIL_12),
            ),
        ) as DatasetResolution.Resolved

        assertEquals(128L, result.candidate.layout.table.offset)
        assertEquals(5, result.candidate.strength.compiledReferenceCount)
    }

    @Test
    fun sourceAuthorityIsPublishedThenCompiledThenInherited() {
        val bytes = ByteArray(512)
        putRetailMove(bytes, 64)
        putRetailMove(bytes, 128)
        putRetailMove(bytes, 192)
        val result = MoveDetailsResolver.resolve(
            moveDetailsSession(bytes, references = mapOf(64 to 1, 128 to 20)),
            MoveDetailsSemanticDomain(1, setOf(0)),
            publishedLayouts = listOf(MoveDetailsTableLayout(64, 1, MoveDetailsAbi.RETAIL_12)),
            compiledLayouts = listOf(MoveDetailsTableLayout(128, 1, MoveDetailsAbi.RETAIL_12)),
            inheritedLayouts = listOf(MoveDetailsTableLayout(192, 1, MoveDetailsAbi.RETAIL_12)),
        ) as DatasetResolution.Resolved

        assertEquals(CandidateSource.PUBLISHED_HEADER, result.candidate.source)
        assertEquals(64L, result.candidate.layout.table.offset)
    }

    @Test
    fun publishedRootsWithoutIndependentReferencesAreNotEligibleButInheritedRootsAre() {
        val bytes = ByteArray(128)
        putRetailMove(bytes, 64)
        val table = MoveDetailsTableLayout(64, 1, MoveDetailsAbi.RETAIL_12)
        val published = MoveDetailsResolver.resolve(
            moveDetailsSession(bytes),
            MoveDetailsSemanticDomain(1, setOf(0)),
            publishedLayouts = listOf(table),
        )
        val inherited = MoveDetailsResolver.resolve(
            moveDetailsSession(bytes),
            MoveDetailsSemanticDomain(1, setOf(0)),
            inheritedLayouts = listOf(table),
        )

        assertTrue(published is DatasetResolution.Unavailable)
        assertTrue(inherited is DatasetResolution.Resolved)
    }

    @Test
    fun semanticCoverageProjectsOnlyTheDeclaredActiveMoveRows() {
        val bytes = ByteArray(160)
        putRetailMove(bytes, 48 + 12)
        bytes[48 + 24 + 2] = 99
        val result = MoveDetailsResolver.resolve(
            moveDetailsSession(bytes),
            MoveDetailsSemanticDomain(3, setOf(1)),
            inheritedLayouts = listOf(MoveDetailsTableLayout(48, 3, MoveDetailsAbi.RETAIL_12)),
        ) as DatasetResolution.Partial

        assertEquals(1, result.candidate.strength.semanticCoverage?.covered)
        assertEquals(1, result.candidate.strength.semanticCoverage?.expected)
        assertTrue(result.candidate.layout.rows[2] is MoveDetailsRowOutcome.Malformed)
        assertTrue(result.candidate.provenance.validatorReviewRecommended)
    }

    @Test
    fun rejectsNonplausibleActiveDecoysInsteadOfRankingTheirOffsets() {
        val bytes = ByteArray(256)
        val root = 64
        putRetailMove(bytes, root, type = 99, accuracy = 4, pp = 90)
        val result = MoveDetailsResolver.resolve(
            moveDetailsSession(bytes, references = mapOf(root to 50)),
            MoveDetailsSemanticDomain(1, setOf(0)),
            compiledLayouts = listOf(MoveDetailsTableLayout(root.toLong(), 1, MoveDetailsAbi.RETAIL_12)),
        )

        assertTrue(result is DatasetResolution.Unavailable)
    }

    @Test
    fun carriesBoundedCompiledInstructionSitesIntoProvenance() {
        val root = 256
        val bytes = ByteArray(512)
        putRetailMove(bytes, root)
        putMoveThumbLiteralReferences(bytes, 0, 64, root, root, root)
        val result = MoveDetailsResolver.resolve(
            moveDetailsSession(bytes, useDefaultReferenceIndex = true),
            MoveDetailsSemanticDomain(1, setOf(0)),
            directCompiledConsumerLayouts = listOf(MoveDetailsTableLayout(root.toLong(), 1, MoveDetailsAbi.RETAIL_12)),
        ) as DatasetResolution.Resolved

        assertEquals(listOf(0, 2, 4), result.candidate.provenance.compiledReferenceSites.offsets)
        assertEquals(3, result.candidate.strength.compiledReferenceCount)
    }

    @Test
    fun returnsTypedReferenceRootWorkCandidateAndExtentBudgetExhaustion() {
        val bytes = ByteArray(512)
        putRetailMove(bytes, 64)
        putRetailMove(bytes, 128)
        val layouts = listOf(
            MoveDetailsTableLayout(64, 1, MoveDetailsAbi.RETAIL_12),
            MoveDetailsTableLayout(128, 1, MoveDetailsAbi.RETAIL_12),
        )
        val domain = MoveDetailsSemanticDomain(1, setOf(0))
        val reference = MoveDetailsResolver.resolve(
            moveDetailsSession(
                bytes,
                referenceIndex = GbaReferenceIndex.budgetExceeded(
                    "target budget",
                    observedTargets = 2,
                    limitTargets = 1,
                ),
            ),
            domain,
            publishedLayouts = listOf(layouts.first()),
        ) as DatasetResolution.BudgetExceeded
        val root = MoveDetailsResolver.resolve(
            moveDetailsSession(bytes, limits = ResolutionLimits(maxProbeRootsPerDataset = 1)),
            domain,
            inheritedLayouts = layouts,
        ) as DatasetResolution.BudgetExceeded
        val work = MoveDetailsResolver.resolve(
            moveDetailsSession(bytes, limits = ResolutionLimits(maxProbeWorkPerDataset = 1)),
            domain,
            inheritedLayouts = layouts,
        ) as DatasetResolution.BudgetExceeded
        val candidate = MoveDetailsResolver.resolve(
            moveDetailsSession(bytes, limits = ResolutionLimits(maxCandidatesPerDataset = 1)),
            domain,
            inheritedLayouts = layouts,
        ) as DatasetResolution.BudgetExceeded
        val extent = MoveDetailsResolver.resolve(
            moveDetailsSession(bytes, limits = ResolutionLimits(maxDatasetExtentBytes = 11)),
            domain,
            inheritedLayouts = listOf(layouts.first()),
        ) as DatasetResolution.BudgetExceeded

        assertEquals(BudgetKind.REFERENCE_TARGETS, reference.budgetKind)
        assertEquals(BudgetKind.PROBE_ROOTS, root.budgetKind)
        assertEquals(BudgetKind.PROBE_WORK, work.budgetKind)
        assertEquals(BudgetKind.CANDIDATES, candidate.budgetKind)
        assertEquals(BudgetKind.EXTENT, extent.budgetKind)
    }

    @Test
    fun workBudgetStopsEnumerationAtTheFirstOverflowWitness() {
        val values = listOf(
            MoveDetailsTableLayout(64, 1, MoveDetailsAbi.RETAIL_12),
            MoveDetailsTableLayout(128, 1, MoveDetailsAbi.CFRU_16),
            MoveDetailsTableLayout(192, 1, MoveDetailsAbi.BATTLE_ENGINE_20),
        )
        var consumed = 0
        val guarded = object : AbstractCollection<MoveDetailsTableLayout>() {
            override val size: Int = values.size
            override fun iterator(): Iterator<MoveDetailsTableLayout> = object : Iterator<MoveDetailsTableLayout> {
                private var index = 0
                override fun hasNext(): Boolean = index < values.size
                override fun next(): MoveDetailsTableLayout {
                    consumed++
                    check(consumed <= 2) { "enumerated beyond probe-work overflow witness" }
                    return values[index++]
                }
            }
        }
        val result = MoveDetailsResolver.resolve(
            moveDetailsSession(ByteArray(256), limits = ResolutionLimits(maxProbeWorkPerDataset = 1)),
            MoveDetailsSemanticDomain(1, setOf(0)),
            inheritedLayouts = guarded,
        ) as DatasetResolution.BudgetExceeded

        assertEquals(BudgetKind.PROBE_WORK, result.budgetKind)
        assertEquals(2, consumed)
    }

    @Test
    fun oneByteTruncationNearEofFailsClosedForEveryAbi() {
        MoveDetailsAbi.entries.forEach { abi ->
            val bytes = ByteArray(abi.recordSize + 6)
            val result = MoveDetailsResolver.resolve(
                moveDetailsSession(bytes),
                MoveDetailsSemanticDomain(1, setOf(0)),
                inheritedLayouts = listOf(MoveDetailsTableLayout(7, 1, abi)),
            )
            assertTrue("$abi should reject its one-byte-truncated extent", result is DatasetResolution.Unavailable)
        }
    }

    @Test
    fun directCompiledConsumerOutranksPublishedAndDoesNotDependOnOffset() {
        val bytes = ByteArray(256)
        putRetailMove(bytes, 64)
        putRetailMove(bytes, 128)
        val result = MoveDetailsResolver.resolve(
            moveDetailsSession(bytes, references = mapOf(64 to 1, 128 to 1)),
            MoveDetailsSemanticDomain(1, setOf(0)),
            directCompiledConsumerLayouts = listOf(MoveDetailsTableLayout(128, 1, MoveDetailsAbi.RETAIL_12)),
            publishedLayouts = listOf(MoveDetailsTableLayout(64, 1, MoveDetailsAbi.RETAIL_12)),
        ) as DatasetResolution.Resolved

        assertEquals(CandidateSource.DIRECT_COMPILED_CONSUMER, result.candidate.source)
        assertFalse(result.candidate.layout.table.offset == 64L)
    }
}
