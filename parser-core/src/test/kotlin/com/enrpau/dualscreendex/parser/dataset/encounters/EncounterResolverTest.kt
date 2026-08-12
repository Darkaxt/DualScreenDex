package com.enrpau.dualscreendex.parser.dataset.encounters

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.resolution.BudgetKind
import com.enrpau.dualscreendex.parser.resolution.CandidateSource
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncounterResolverTest {
    private val resolver = EncounterResolver()

    @Test
    fun exactAndDirectFastPathsDoNotBuildTheReferenceIndex() {
        val bytes = ByteArray(0x3000)
        putStandardEncounterTable(bytes, 0x100)
        var exactBuilds = 0
        var directBuilds = 0
        val layout = Gen3EncounterTableLayout(0x100, Gen3EncounterAbi.STANDARD_20, 100)

        val exact = resolver.resolve(
            encounterSession(bytes, exact = true, onReferenceIndexBuild = { exactBuilds++ }),
            exactLayouts = listOf(layout),
        ) as DatasetResolution.Resolved
        val direct = resolver.resolve(
            encounterSession(bytes, onReferenceIndexBuild = { directBuilds++ }),
            directCompiledConsumerLayouts = listOf(layout),
        ) as DatasetResolution.Resolved

        assertEquals(CandidateSource.EXACT_PROFILE, exact.candidate.source)
        assertEquals(CandidateSource.DIRECT_COMPILED_CONSUMER, direct.candidate.source)
        assertEquals(0, exactBuilds)
        assertEquals(0, directBuilds)
    }

    @Test
    fun classicTwentyFourRequiresReferenceAuthorityButStandardInheritedDoesNot() {
        val bytes = ByteArray(0x5000)
        putClassicEncounterTable(bytes, 0x100)
        putStandardEncounterTable(bytes, 0x1000)

        val classic = resolver.resolve(
            encounterSession(bytes),
            inheritedLayouts = listOf(Gen3EncounterTableLayout(0x100, Gen3EncounterAbi.CLASSIC_24, 100)),
        )
        val standard = resolver.resolve(
            encounterSession(bytes),
            inheritedLayouts = listOf(Gen3EncounterTableLayout(0x1000, Gen3EncounterAbi.STANDARD_20, 100)),
        ) as DatasetResolution.Resolved

        assertTrue(classic is DatasetResolution.Unavailable)
        assertEquals(Gen3EncounterAbi.STANDARD_20, standard.candidate.layout.table.abi)
    }

    @Test
    fun sameReferencedRootThatValidatesUnderBothAbisIsAmbiguous() {
        val bytes = ByteArray(0x6000)
        val root = 0x100
        putDualAbiEncounterTable(bytes, root)
        val session = encounterSession(bytes, references = mapOf(root to 2))
        val classic = Gen3EncounterCodec().decode(
            session,
            Gen3EncounterTableLayout(root.toLong(), Gen3EncounterAbi.CLASSIC_24, 100),
        )
        assertTrue(classic.toString(), classic is EncounterTableOutcome.Decoded)

        val resolution = resolver.resolve(
            session,
            speciesCount = 100,
            compiledReferenceRoots = listOf(root),
        )
        assertTrue(resolution.toString(), resolution is DatasetResolution.Ambiguous)
        val result = resolution as DatasetResolution.Ambiguous

        assertEquals(
            setOf(Gen3EncounterAbi.STANDARD_20, Gen3EncounterAbi.CLASSIC_24),
            result.candidates.map { it.layout.table.abi }.toSet(),
        )
        assertEquals(setOf(root.toLong()), result.candidates.map { it.layout.table.offset }.toSet())
    }

    @Test
    fun equallyReferencedIndependentRootsRemainAmbiguousInEitherInputOrder() {
        val bytes = ByteArray(0x6000)
        val first = 0x100
        val second = 0x1000
        putStandardEncounterTable(bytes, first)
        putStandardEncounterTable(bytes, second)
        val session = encounterSession(bytes, references = mapOf(first to 1, second to 1))

        val forward = resolver.resolve(session, compiledReferenceLayouts = listOf(
            Gen3EncounterTableLayout(first.toLong(), Gen3EncounterAbi.STANDARD_20, 100),
            Gen3EncounterTableLayout(second.toLong(), Gen3EncounterAbi.STANDARD_20, 100),
        )) as DatasetResolution.Ambiguous
        val reverse = resolver.resolve(session, compiledReferenceLayouts = listOf(
            Gen3EncounterTableLayout(second.toLong(), Gen3EncounterAbi.STANDARD_20, 100),
            Gen3EncounterTableLayout(first.toLong(), Gen3EncounterAbi.STANDARD_20, 100),
        )) as DatasetResolution.Ambiguous

        assertEquals(forward.candidates.map { it.layout.table.offset }, reverse.candidates.map { it.layout.table.offset })
    }

    @Test
    fun higherCompiledReferenceCountRanksAuthorityWithoutOffsetProximity() {
        val bytes = ByteArray(0x6000)
        val low = 0x100
        val high = 0x1000
        putStandardEncounterTable(bytes, low)
        putStandardEncounterTable(bytes, high)

        val result = resolver.resolve(
            encounterSession(bytes, references = mapOf(low to 1, high to 3)),
            compiledReferenceLayouts = listOf(
                Gen3EncounterTableLayout(low.toLong(), Gen3EncounterAbi.STANDARD_20, 100),
                Gen3EncounterTableLayout(high.toLong(), Gen3EncounterAbi.STANDARD_20, 100),
            ),
        ) as DatasetResolution.Resolved

        assertEquals(high.toLong(), result.candidate.layout.table.offset)
        assertEquals(3, result.candidate.strength.compiledReferenceCount)
    }

    @Test
    fun referencedClassicEmptyFirstShellResolvesAndUnreferencedShellFailsClosed() {
        val bytes = ByteArray(0x5000)
        val root = 0x100
        putClassicEncounterTable(bytes, root, emptyFirst = true)
        val layout = Gen3EncounterTableLayout(root.toLong(), Gen3EncounterAbi.CLASSIC_24, 100)

        val referenced = resolver.resolve(
            encounterSession(bytes, references = mapOf(root to 2)),
            compiledReferenceLayouts = listOf(layout),
        ) as DatasetResolution.Resolved
        val unreferenced = resolver.resolve(
            encounterSession(bytes),
            inheritedLayouts = listOf(layout),
        )

        assertTrue(referenced.candidate.layout.rows.first() is EncounterHeaderOutcome.StructuralEmpty)
        assertTrue(unreferenced is DatasetResolution.Unavailable)
    }

    @Test
    fun standardTwentyEmptyFirstIsRejectedEvenWhenTheRootIsReferenced() {
        val bytes = ByteArray(0x4000)
        val root = 0x100
        putStandardEncounterTable(bytes, root, maps = 1..4, emptyRows = setOf(0))

        val result = resolver.resolve(
            encounterSession(bytes, references = mapOf(root to 1)),
            compiledReferenceLayouts = listOf(
                Gen3EncounterTableLayout(root.toLong(), Gen3EncounterAbi.STANDARD_20, 100),
            ),
        )

        assertTrue(result is DatasetResolution.Unavailable)
    }

    @Test
    fun malformedActiveHeaderProducesTypedPartialResolution() {
        val bytes = ByteArray(0x4000)
        val root = 0x100
        putStandardEncounterTable(bytes, root, maps = 1..4, malformedRows = setOf(1))

        val result = resolver.resolve(
            encounterSession(bytes, references = mapOf(root to 1)),
            compiledReferenceLayouts = listOf(
                Gen3EncounterTableLayout(root.toLong(), Gen3EncounterAbi.STANDARD_20, 100),
            ),
        ) as DatasetResolution.Partial

        assertTrue(result.reasons.any { it.contains("coverage") })
        assertTrue(result.candidate.provenance.validatorReviewRecommended)
    }

    @Test
    fun referenceSitesAreRetainedAndOverflowFailsClosed() {
        val bytes = ByteArray(0x4000)
        val root = 0x100
        putStandardEncounterTable(bytes, root)
        val resolved = resolver.resolve(
            encounterSession(
                bytes,
                references = mapOf(root to 2),
                referenceSites = mapOf(root to listOf(4, 12)),
            ),
            compiledReferenceLayouts = listOf(
                Gen3EncounterTableLayout(root.toLong(), Gen3EncounterAbi.STANDARD_20, 100),
            ),
        ) as DatasetResolution.Resolved
        assertEquals(listOf(4, 12), resolved.candidate.provenance.compiledReferenceSites.offsets)

        val overflowIndex = GbaReferenceIndex.fromTargets(
            targets = mapOf(
                root to GbaTargetReferenceEvidence(
                    count = 2,
                    instructionSites = emptyList(),
                    observedSites = 2,
                    limitSites = 1,
                    overflowReason = "sites",
                ),
            ),
            limitTargets = 8,
        )
        val overflow = resolver.resolve(
            encounterSession(
                bytes,
                limits = ResolutionLimits(maxCompiledReferenceSitesPerCandidate = 1),
                referenceIndex = overflowIndex,
            ),
            compiledReferenceLayouts = listOf(
                Gen3EncounterTableLayout(root.toLong(), Gen3EncounterAbi.STANDARD_20, 100),
            ),
        ) as DatasetResolution.BudgetExceeded
        assertEquals(BudgetKind.REFERENCE_SITES, overflow.budgetKind)
    }

    @Test
    fun sessionReferenceSiteLimitRejectsCompleteUpstreamEvidenceThatUsesALargerLimit() {
        val bytes = ByteArray(0x4000)
        val root = 0x100
        putStandardEncounterTable(bytes, root)
        val index = GbaReferenceIndex.fromTargets(
            targets = mapOf(
                root to GbaTargetReferenceEvidence(
                    count = 2,
                    instructionSites = listOf(4, 8),
                    observedSites = 2,
                    limitSites = 4,
                    overflowReason = null,
                    siteEvidenceUnavailableReason = null,
                ),
            ),
            limitTargets = 8,
        )

        val result = resolver.resolve(
            encounterSession(
                bytes,
                limits = ResolutionLimits(maxCompiledReferenceSitesPerCandidate = 1),
                referenceIndex = index,
            ),
            compiledReferenceLayouts = listOf(
                Gen3EncounterTableLayout(root.toLong(), Gen3EncounterAbi.STANDARD_20, 100),
            ),
        ) as DatasetResolution.BudgetExceeded

        assertEquals(BudgetKind.REFERENCE_SITES, result.budgetKind)
        assertEquals(2L, result.observed)
        assertEquals(1L, result.limit)
    }

    @Test
    fun unverifiedExactLayoutsAndDisallowedStructuralAnchorsCannotCreateAbiAmbiguity() {
        val bytes = ByteArray(0x6000)
        val root = 0x100
        putDualAbiEncounterTable(bytes, root)
        val standard = Gen3EncounterTableLayout(root.toLong(), Gen3EncounterAbi.STANDARD_20, 100)
        val classic = Gen3EncounterTableLayout(root.toLong(), Gen3EncounterAbi.CLASSIC_24, 100)

        val unverifiedExact = resolver.resolve(
            encounterSession(bytes),
            exactLayouts = listOf(standard, classic),
        )
        val structuralConflict = resolver.resolve(
            encounterSession(bytes, references = mapOf(root to 1)),
            compiledReferenceLayouts = listOf(standard),
            structuralLayouts = listOf(classic),
        )

        assertTrue(unverifiedExact is DatasetResolution.Unavailable)
        assertTrue(structuralConflict is DatasetResolution.Resolved)
        assertEquals(
            Gen3EncounterAbi.STANDARD_20,
            (structuralConflict as DatasetResolution.Resolved).candidate.layout.table.abi,
        )
    }

    @Test
    fun workBudgetChargesEveryRawRootAndUnreferencedLayoutBeforeFilteringOrDeduplication() {
        val rawRoots = resolver.resolve(
            encounterSession(
                ByteArray(256),
                limits = ResolutionLimits(maxProbeWorkPerDataset = 3),
            ),
            speciesCount = 100,
            compiledReferenceRoots = List(4) { -1 },
        ) as DatasetResolution.BudgetExceeded
        assertEquals(BudgetKind.PROBE_WORK, rawRoots.budgetKind)
        assertEquals(4L, rawRoots.observed)
        assertEquals(3L, rawRoots.limit)

        val bytes = ByteArray(0x4000)
        val root = 0x100
        putClassicEncounterTable(bytes, root)
        val unreferenced = Gen3EncounterTableLayout(root.toLong(), Gen3EncounterAbi.CLASSIC_24, 100)
        val layouts = resolver.resolve(
            encounterSession(
                bytes,
                limits = ResolutionLimits(maxProbeWorkPerDataset = 3),
            ),
            compiledReferenceLayouts = List(4) { unreferenced },
        ) as DatasetResolution.BudgetExceeded
        assertEquals(BudgetKind.PROBE_WORK, layouts.budgetKind)
        assertEquals(4L, layouts.observed)
        assertEquals(3L, layouts.limit)
    }

    @Test
    fun rejectionDiagnosticsRemainBoundedForManyDistinctInvalidLayouts() {
        val invalid = List(100) { index ->
            Gen3EncounterTableLayout(
                Int.MAX_VALUE.toLong() + 1L + index,
                Gen3EncounterAbi.STANDARD_20,
                100,
            )
        }

        val result = resolver.resolve(
            encounterSession(
                ByteArray(256),
                limits = ResolutionLimits(maxProbeWorkPerDataset = 1_000),
            ),
            inheritedLayouts = invalid,
        ) as DatasetResolution.Unavailable

        assertTrue(result.reasons.size <= 65)
        assertTrue(result.reasons.any { it.contains("omitted") })
    }

    @Test
    fun classicExactLayoutStillRequiresCompiledEvidenceAndCannotManufactureDualAbiConflict() {
        val bytes = ByteArray(0x6000)
        val classicRoot = 0x100
        putClassicEncounterTable(bytes, classicRoot)
        val classic = Gen3EncounterTableLayout(classicRoot.toLong(), Gen3EncounterAbi.CLASSIC_24, 100)
        var unreferencedBuilds = 0

        val rejectedExact = resolver.resolve(
            encounterSession(
                bytes,
                exact = true,
                onReferenceIndexBuild = { unreferencedBuilds++ },
            ),
            exactLayouts = listOf(classic),
        )
        assertTrue(rejectedExact is DatasetResolution.Unavailable)
        assertEquals(1, unreferencedBuilds)

        val direct = resolver.resolve(
            encounterSession(bytes),
            directCompiledConsumerLayouts = listOf(classic),
        ) as DatasetResolution.Resolved
        assertEquals(CandidateSource.DIRECT_COMPILED_CONSUMER, direct.candidate.source)

        val exactReferenced = resolver.resolve(
            encounterSession(bytes, references = mapOf(classicRoot to 2), exact = true),
            exactLayouts = listOf(classic),
        ) as DatasetResolution.Resolved
        assertEquals(CandidateSource.EXACT_PROFILE, exactReferenced.candidate.source)
        assertEquals(2, exactReferenced.candidate.strength.compiledReferenceCount)

        val dualBytes = ByteArray(0x6000)
        val dualRoot = 0x100
        putDualAbiEncounterTable(dualBytes, dualRoot)
        val dual = resolver.resolve(
            encounterSession(dualBytes, exact = true),
            exactLayouts = listOf(
                Gen3EncounterTableLayout(dualRoot.toLong(), Gen3EncounterAbi.STANDARD_20, 100),
                Gen3EncounterTableLayout(dualRoot.toLong(), Gen3EncounterAbi.CLASSIC_24, 100),
            ),
        )
        assertTrue(dual is DatasetResolution.Resolved || dual is DatasetResolution.Partial)
        val selected = when (dual) {
            is DatasetResolution.Resolved -> dual.candidate
            is DatasetResolution.Partial -> dual.candidate
            else -> error("exact Standard20 should remain selectable")
        }
        assertEquals(Gen3EncounterAbi.STANDARD_20, selected.layout.table.abi)
    }

    @Test
    fun classicExactLayoutRejectsZeroCountTargetReferenceEvidence() {
        val bytes = ByteArray(0x6000)
        val classicRoot = 0x100
        putClassicEncounterTable(bytes, classicRoot)
        val zeroCountIndex = GbaReferenceIndex.fromTargets(
            targets = mapOf(
                classicRoot to GbaTargetReferenceEvidence(
                    count = 0,
                    instructionSites = emptyList(),
                    observedSites = 0,
                    limitSites = 16,
                    overflowReason = null,
                    siteEvidenceUnavailableReason = null,
                ),
            ),
            limitTargets = 8,
        )

        val result = resolver.resolve(
            encounterSession(bytes, exact = true, referenceIndex = zeroCountIndex),
            exactLayouts = listOf(
                Gen3EncounterTableLayout(classicRoot.toLong(), Gen3EncounterAbi.CLASSIC_24, 100),
            ),
        )

        assertTrue(result is DatasetResolution.Unavailable)
    }

    @Test
    fun everyResolverBudgetFailsClosedWithTypedEvidence() {
        val bytes = ByteArray(0x6000)
        val first = 0x100
        val second = 0x1000
        putStandardEncounterTable(bytes, first)
        putStandardEncounterTable(bytes, second)
        val layouts = listOf(
            Gen3EncounterTableLayout(first.toLong(), Gen3EncounterAbi.STANDARD_20, 100),
            Gen3EncounterTableLayout(second.toLong(), Gen3EncounterAbi.STANDARD_20, 100),
        )

        val reference = resolver.resolve(
            encounterSession(bytes, referenceIndex = GbaReferenceIndex.budgetExceeded("targets", 2, 1)),
            compiledReferenceLayouts = listOf(layouts.first()),
        ) as DatasetResolution.BudgetExceeded
        val roots = resolver.resolve(
            encounterSession(bytes, limits = ResolutionLimits(maxProbeRootsPerDataset = 1)),
            inheritedLayouts = layouts,
        ) as DatasetResolution.BudgetExceeded
        val work = resolver.resolve(
            encounterSession(bytes, limits = ResolutionLimits(maxProbeWorkPerDataset = 1)),
            inheritedLayouts = listOf(layouts.first()),
        ) as DatasetResolution.BudgetExceeded
        val candidates = resolver.resolve(
            encounterSession(
                bytes,
                references = mapOf(first to 1, second to 1),
                limits = ResolutionLimits(maxCandidatesPerDataset = 1),
            ),
            compiledReferenceLayouts = layouts,
        ) as DatasetResolution.BudgetExceeded
        val retained = resolver.resolve(
            encounterSession(bytes),
            inheritedLayouts = listOf(layouts.first()),
            decodeLimits = EncounterDecodeLimits(maxRetainedBytesPerResolution = 64),
        ) as DatasetResolution.BudgetExceeded

        assertEquals(BudgetKind.REFERENCE_TARGETS, reference.budgetKind)
        assertEquals(BudgetKind.PROBE_ROOTS, roots.budgetKind)
        assertEquals(BudgetKind.PROBE_WORK, work.budgetKind)
        assertEquals(BudgetKind.CANDIDATES, candidates.budgetKind)
        assertEquals(BudgetKind.EXTENT, retained.budgetKind)
    }

    @Test
    fun boundedNegativeCacheAvoidsRepeatedShellWalksButFailsAtTheAggregateCapAfterEviction() {
        val oneRootBytes = ByteArray(0x1000)
        val oneRoot = 0x100
        putEmptyClassicDecoy(oneRootBytes, oneRoot)
        val repeated = resolver.resolve(
            encounterSession(oneRootBytes, references = mapOf(oneRoot to 1)),
            speciesCount = 100,
            compiledReferenceRoots = List(20_000) { oneRoot },
        )
        assertTrue(repeated is DatasetResolution.Unavailable)

        val distinct = 4_097
        val roots = List(distinct) { 0x100 + it * 48 }
        val bytes = ByteArray(roots.last() + 64)
        roots.forEach { putEmptyClassicDecoy(bytes, it) }
        val overflow = resolver.resolve(
            encounterSession(
                bytes,
                references = roots.associateWith { 1 },
                limits = ResolutionLimits(maxProbeWorkPerDataset = 100_000),
            ),
            speciesCount = 100,
            compiledReferenceRoots = List(5) { roots }.flatten(),
        ) as DatasetResolution.BudgetExceeded

        assertEquals(BudgetKind.PROBE_WORK, overflow.budgetKind)
        assertEquals(16_385L, overflow.observed)
        assertEquals(16_384L, overflow.limit)
        assertFalse(overflow.observationComplete)
    }

    private fun putDualAbiEncounterTable(bytes: ByteArray, root: Int) {
        // Five Standard20 rows and four Classic24 rows share a 120-byte body and sentinel.
        val pointerFields = listOf(
            4 to 12, 8 to 5, 12 to 5, 16 to 10,
            28 to 12, 32 to 5, 36 to 10, 44 to 12,
            52 to 12, 56 to 10, 64 to 12, 68 to 10,
            76 to 12, 84 to 12, 88 to 5, 92 to 10,
            104 to 12, 108 to 5, 112 to 5, 116 to 10,
        )
        // Zero group/map bytes are valid and, unlike artificial non-zero map IDs at the other
        // ABI's row boundaries, do not fabricate invalid non-null pointer fields.
        pointerFields.forEachIndexed { index, (field, slots) ->
            val info = 0x800 + index * 8
            val slotRoot = 0x1800 + index * 0x40
            putEncounterGbaPointer(bytes, root + field, info)
            putEncounterInfo(bytes, info, if (field in setOf(16, 64, 88, 112)) 1 else 20, slotRoot, slots, 10)
        }
        bytes[root + 120] = 0xFF.toByte()
        bytes[root + 121] = 0xFF.toByte()
    }
}
