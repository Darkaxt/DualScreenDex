package com.enrpau.dualscreendex.parser.dataset.descriptions

import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.resolution.BudgetKind
import com.enrpau.dualscreendex.parser.resolution.CandidateReasonKind
import com.enrpau.dualscreendex.parser.resolution.CandidateSource
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import com.enrpau.dualscreendex.parser.text.WesternPokemonTextCodecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DescriptionResolverCompatibilityTest {
    @Test
    fun selectedLayoutIsDecodedDirectlyWithoutRunningCandidateDiscovery() {
        val bytes = ByteArray(0x1200)
        val selected = DescriptionTableLayout(0x200, 3, 32, listOf(16))
        putDescriptionTable(bytes, 0x200, 3, 32, listOf(16), 0x900)

        val result = DescriptionResolver().resolve(
            session = descriptionSession(bytes),
            expectedSpeciesCount = 3,
            selectedLayout = selected,
        ) as DatasetResolution.Resolved<ResolvedDescriptionLayout>

        assertEquals(selected, result.candidate.layout.table)
        assertEquals(CandidateSource.INHERITED_FAMILY_LAYOUT, result.candidate.source)
    }

    @Test
    fun discoversARelocatedThirtyTwoByteTableFromItsInternalSeedAnchor() {
        val bytes = ByteArray(0x1200)
        putDescriptionTable(bytes, 0x200, 3, 32, listOf(16), 0x900)
        putGbaText(bytes, 0x200 + 32, "SEED")

        val result = DescriptionResolver().resolve(
            descriptionSession(bytes),
            expectedSpeciesCount = 3,
        ) as DatasetResolution.Resolved<ResolvedDescriptionLayout>

        assertEquals(0x200L, result.candidate.layout.table.offset)
        assertEquals(32, result.candidate.layout.table.recordSize)
        assertEquals(CandidateSource.STRUCTURAL_ANCHOR, result.candidate.source)
    }

    @Test
    fun englishSeedAnchorCannotAuthorizeFrenchDescriptionDiscovery() {
        val bytes = ByteArray(0x1200)
        putDescriptionTable(bytes, 0x200, 3, 32, listOf(16), 0x900)
        putGbaText(bytes, 0x200 + 32, "SEED")

        val result = DescriptionResolver(textCodec = WesternPokemonTextCodecs.gen3French).resolve(
            descriptionSession(bytes),
            expectedSpeciesCount = 3,
        )

        assertTrue(result is DatasetResolution.Unavailable)
    }

    @Test
    fun discoversAndTrimsAPartialRelocatedTable() {
        val bytes = ByteArray(0x1200)
        putDescriptionTable(bytes, 0x200, 4, 32, listOf(16), 0x900)
        putGbaText(bytes, 0x200 + 32, "SEED")

        val result = DescriptionResolver().resolve(
            descriptionSession(bytes),
            expectedSpeciesCount = 8,
        ) as DatasetResolution.Partial<ResolvedDescriptionLayout>

        assertEquals(0x200L, result.candidate.layout.table.offset)
        assertEquals(4L, result.candidate.layout.table.count)
        assertEquals(4, result.candidate.layout.rows.size)
    }

    @Test
    fun sparseThirtySixByteDiscoveryRetainsTheStructuralEmptyRowOutcome() {
        val bytes = ByteArray(0x2200)
        val table = 0x200
        val physicalCount = 12
        putDescriptionTable(bytes, table, physicalCount, 36, listOf(16, 20), 0x1200)
        (0 until 36).forEach { bytes[table + 3 * 36 + it] = 0 }
        putGbaText(bytes, table + 8 * 36, "SEED")

        val result = DescriptionResolver().resolve(
            descriptionSession(bytes),
            expectedSpeciesCount = 16,
        ) as DatasetResolution.Partial<ResolvedDescriptionLayout>

        assertEquals(12L, result.candidate.layout.table.count)
        assertEquals(12, result.candidate.layout.rows.size)
        assertEquals(DescriptionRowOutcome.StructuralEmpty(3), result.candidate.layout.rows[3])
        assertTrue(result.candidate.layout.rows[4] is DescriptionRowOutcome.Decoded)
    }

    @Test
    fun resolvedLayoutRetainsAnActiveMalformedRowDistinctFromStructuralEmpty() {
        val bytes = ByteArray(0x2200)
        val table = 0x200
        putDescriptionTable(bytes, table, 10, 36, listOf(16, 20), 0x1200)
        putU32(bytes, table + 5 * 36 + 16, 0)

        val result = DescriptionResolver().resolve(
            session = descriptionSession(bytes),
            expectedSpeciesCount = 10,
            structuralCandidates = listOf(
                DescriptionTableLayout(table.toLong(), 10, 36, listOf(16, 20)),
            ),
        ) as DatasetResolution.Partial<ResolvedDescriptionLayout>

        assertTrue(result.candidate.layout.rows[5] is DescriptionRowOutcome.Malformed)
        assertFalse(result.candidate.layout.rows[5] is DescriptionRowOutcome.StructuralEmpty)
    }

    @Test
    fun extentBudgetExhaustionIsTypedAndNeverDroppedAsAnInvalidCandidate() {
        val bytes = ByteArray(0x1000)
        val layout = DescriptionTableLayout(0x200, 4, 32, listOf(16))
        putDescriptionTable(bytes, 0x200, 4, 32, listOf(16), 0x800)

        val result = DescriptionResolver().resolve(
            session = descriptionSession(
                bytes,
                limits = ResolutionLimits(maxDatasetExtentBytes = 64),
            ),
            expectedSpeciesCount = 4,
            structuralCandidates = listOf(layout),
        )

        assertTrue(result is DatasetResolution.BudgetExceeded)
        result as DatasetResolution.BudgetExceeded
        assertEquals(BudgetKind.EXTENT, result.budgetKind)
        assertEquals(128L, result.observed)
        assertEquals(64L, result.limit)
    }

    @Test
    fun exactProfileAuthorityNeverPublishesAPrefixTrimmedLayout() {
        val bytes = ByteArray(0x5000)
        val exact = DescriptionTableLayout(0x200, 30, 32, listOf(16))
        putDescriptionTable(bytes, 0x200, 26, 32, listOf(16), 0x2000)

        val result = DescriptionResolver().resolve(
            session = descriptionSession(
                bytes,
                exact = true,
                exactDescriptionLayout = exact,
            ),
            expectedSpeciesCount = 30,
            profileLayout = exact,
        ) as DatasetResolution.Partial<ResolvedDescriptionLayout>

        assertEquals(CandidateSource.EXACT_PROFILE, result.candidate.source)
        assertEquals(30L, result.candidate.layout.table.count)
        assertEquals(30, result.candidate.layout.rows.size)
        assertEquals(DescriptionRowOutcome.StructuralEmpty(29), result.candidate.layout.rows[29])
    }

    @Test
    fun sameRootCallerLayoutsHitProbeWorkBudgetBeforeTheOverflowWitnessIsDecoded() {
        var decodeAttempts = 0
        val decoder = DescriptionTableDecoder { _, layout ->
            decodeAttempts++
            DescriptionTableOutcome.Rejected(layout, "fixture invalid layout")
        }
        val sameRootLayouts = (1L..12L).map { count ->
            DescriptionTableLayout(0x200, count, 32, listOf(16))
        }

        val result = DescriptionResolver(decoder).resolve(
            session = descriptionSession(
                ByteArray(0x1000),
                limits = ResolutionLimits(
                    maxProbeRootsPerDataset = 2,
                    maxProbeWorkPerDataset = 3,
                ),
            ),
            expectedSpeciesCount = 12,
            structuralCandidates = sameRootLayouts,
        )

        assertTrue(result is DatasetResolution.BudgetExceeded)
        result as DatasetResolution.BudgetExceeded
        assertEquals(BudgetKind.PROBE_WORK, result.budgetKind)
        assertEquals(4L, result.observed)
        assertEquals(3L, result.limit)
        assertEquals(3, decodeAttempts)
        assertFalse(result.observationComplete)
    }

    @Test
    fun internalDiscoveryChargesAnchorScanWorkAndStopsBeforeTheWitnessCheck() {
        var decodeAttempts = 0
        val decoder = DescriptionTableDecoder { _, layout ->
            decodeAttempts++
            DescriptionTableOutcome.Rejected(layout, "fixture should not reach the codec")
        }

        val result = DescriptionResolver(decoder).resolve(
            session = descriptionSession(
                ByteArray(0x200),
                limits = ResolutionLimits(maxProbeWorkPerDataset = 3),
            ),
            expectedSpeciesCount = 4,
        )

        assertTrue(result is DatasetResolution.BudgetExceeded)
        result as DatasetResolution.BudgetExceeded
        assertEquals(BudgetKind.PROBE_WORK, result.budgetKind)
        assertEquals(4L, result.observed)
        assertEquals(3L, result.limit)
        assertEquals(0, decodeAttempts)
        assertFalse(result.observationComplete)
    }

    @Test
    fun aValidatedCompiledCandidateDoesNotForceOptionalFullRomDiscovery() {
        val bytes = ByteArray(0x3000)
        val table = 0x400
        putDescriptionTable(bytes, table, 4, 32, listOf(16), 0x1800)
        putThumbLiteralReferences(bytes, 0x80, 0x100, table)

        val result = DescriptionResolver().resolve(
            descriptionSession(
                bytes,
                limits = ResolutionLimits(maxProbeWorkPerDataset = 3),
                useDefaultReferenceIndex = true,
            ),
            expectedSpeciesCount = 4,
        )

        assertTrue(result is DatasetResolution.Resolved)
    }

    @Test
    fun anIneligibleCompiledDecoyDoesNotSuppressAValidInternalAnchor() {
        val bytes = ByteArray(0x2400)
        val decoy = 0x100
        val valid = 0x400
        putDescriptionTable(bytes, decoy, 4, 32, listOf(16), 0x1800)
        repeat(3) { index ->
            putU32(bytes, decoy + (index + 1) * 32 + 16, 0)
        }
        putDescriptionTable(bytes, valid, 4, 32, listOf(16), 0x1C00)
        putGbaText(bytes, valid + 32, "SEED")

        val result = DescriptionResolver().resolve(
            session = descriptionSession(bytes, references = mapOf(decoy to 1)),
            expectedSpeciesCount = 4,
        ) as DatasetResolution.Resolved<ResolvedDescriptionLayout>

        assertEquals(valid.toLong(), result.candidate.layout.table.offset)
        assertEquals(CandidateSource.STRUCTURAL_ANCHOR, result.candidate.source)
    }

    @Test
    fun compiledSitesAndUnavailableSiteEvidenceReachCandidateProvenance() {
        val bytes = ByteArray(0x3000)
        val table = 0x400
        putDescriptionTable(bytes, table, 4, 36, listOf(16, 20), 0x1800)
        putThumbLiteralReferences(bytes, 0x80, 0x100, table, table)

        val complete = DescriptionResolver().resolve(
            descriptionSession(bytes, useDefaultReferenceIndex = true),
            expectedSpeciesCount = 4,
        ) as DatasetResolution.Resolved<ResolvedDescriptionLayout>

        assertEquals(listOf(0x80, 0x82), complete.candidate.provenance.compiledReferenceSites.offsets)

        val overflow = DescriptionResolver().resolve(
            descriptionSession(
                bytes,
                limits = ResolutionLimits(maxCompiledReferenceSitesPerCandidate = 1),
                useDefaultReferenceIndex = true,
            ),
            expectedSpeciesCount = 4,
        ) as DatasetResolution.Resolved<ResolvedDescriptionLayout>

        assertTrue(overflow.candidate.provenance.validatorReviewRecommended)
        val overflowSites = overflow.candidate.provenance.compiledReferenceSites
        assertTrue(overflowSites.budgetExceeded)
        assertTrue(overflowSites.offsets.isEmpty())
        assertEquals(2L, overflowSites.observedSites)
        assertEquals(1, overflowSites.limitSites)
        assertEquals(
            "compiled reference site budget exceeded (2 > 1)",
            overflowSites.overflowReason,
        )
        assertTrue(
            overflow.candidate.provenance.reasons.any {
                it.kind == CandidateReasonKind.ANOMALY && "site budget exceeded" in it.message
            },
        )

        val unavailable = DescriptionResolver().resolve(
            descriptionSession(bytes, references = mapOf(table to 2)),
            expectedSpeciesCount = 4,
        ) as DatasetResolution.Resolved<ResolvedDescriptionLayout>

        assertTrue(unavailable.candidate.provenance.validatorReviewRecommended)
        assertTrue(
            unavailable.candidate.provenance.reasons.any {
                it.kind == CandidateReasonKind.ANOMALY && "counts-only fixture index" in it.message
            },
        )
    }
}
