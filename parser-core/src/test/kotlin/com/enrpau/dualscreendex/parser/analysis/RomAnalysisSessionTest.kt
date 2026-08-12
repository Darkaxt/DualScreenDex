package com.enrpau.dualscreendex.parser.analysis

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.RomProfile
import com.enrpau.dualscreendex.parser.model.TableLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RomAnalysisSessionTest {
    @Test
    fun buildsTheBoundedGbaReferenceIndexLazilyOncePerSession() {
        var buildCount = 0
        var observedTargetBudget = -1
        val limits = ResolutionLimits(maxDistinctGbaReferenceTargets = 7)
        val session = RomAnalysisSession(
            rom = RomImage(ByteArray(0x200)),
            header = RomHeader(Platform.GBA, "TEST"),
            limits = limits,
            gbaReferenceIndexFactory = GbaReferenceIndexFactory { _, observedLimits ->
                buildCount++
                observedTargetBudget = observedLimits.maxDistinctGbaReferenceTargets
                GbaReferenceIndex.countsOnlyForTesting(mapOf(0x100 to 2))
            },
        )

        assertEquals(0, buildCount)

        val first = session.gbaReferenceIndex
        val second = session.gbaReferenceIndex

        assertSame(first, second)
        assertEquals(1, buildCount)
        assertEquals(7, observedTargetBudget)
        assertEquals(2, first?.referenceCount(0x100))
    }

    @Test
    fun doesNotBuildAGbaIndexForANonGbaSession() {
        var buildCount = 0
        val session = RomAnalysisSession(
            rom = RomImage(ByteArray(0x200)),
            header = RomHeader(Platform.GBC, "TEST"),
            gbaReferenceIndexFactory = GbaReferenceIndexFactory { _, _ ->
                buildCount++
                GbaReferenceIndex.countsOnlyForTesting(emptyMap())
            },
        )

        assertEquals(null, session.gbaReferenceIndex)
        assertEquals(0, buildCount)
    }

    @Test
    fun checksTableExtentsWithLongArithmeticBeforeReturningIntOffsets() {
        val limits = ResolutionLimits(maxDatasetExtentBytes = 64)

        val result = limits.checkTableExtent(
            offset = 8,
            count = 3,
            recordSize = 4,
            romSize = 20,
        )

        assertEquals(
            ExtentCheck.Valid(CheckedRomExtent(offset = 8, length = 12, endExclusive = 20)),
            result,
        )
    }

    @Test
    fun rejectsNegativeOverflowingTruncatedAndOverBudgetExtents() {
        val limits = ResolutionLimits(maxDatasetExtentBytes = 16)

        val negative = limits.checkTableExtent(-1, count = 1, recordSize = 1, romSize = 32)
        val overflowing = limits.checkTableExtent(
            offset = Long.MAX_VALUE - 1,
            count = 2,
            recordSize = 2,
            romSize = Long.MAX_VALUE,
        )
        val truncated = limits.checkTableExtent(24, count = 3, recordSize = 4, romSize = 32)
        val overBudget = limits.checkTableExtent(0, count = 5, recordSize = 4, romSize = 32)

        assertTrue(negative is ExtentCheck.Invalid)
        assertTrue(overflowing is ExtentCheck.Invalid)
        assertTrue(truncated is ExtentCheck.Invalid)
        assertEquals(
            ExtentCheck.BudgetExceeded(observedBytes = 20, limitBytes = 16),
            overBudget,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveDeterministicBudgets() {
        ResolutionLimits(maxCandidatesPerDataset = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveDatasetProbeBudget() {
        ResolutionLimits(maxProbeRootsPerDataset = 0)
    }

    @Test
    fun datasetProbeBudgetIsIndependentFromValidatedCandidateBudget() {
        val limits = ResolutionLimits(
            maxProbeRootsPerDataset = 40,
            maxCandidatesPerDataset = 3,
        )

        assertEquals(40, limits.maxProbeRootsPerDataset)
        assertEquals(3, limits.maxCandidatesPerDataset)
    }

    @Test
    fun derivesExactIdentityOnlyWhenTheProfileMatchesTheSessionRomDigest() {
        val rom = RomImage(ByteArray(0x200))
        val matching = RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, "TEST"),
            exactProfile = profile(rom.sha256, rom.size),
        )
        val mismatched = RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, "TEST"),
            exactProfile = profile("0".repeat(64), rom.size),
        )

        assertEquals(rom.sha256, matching.exactProfileIdentity?.sha256)
        assertNull(mismatched.exactProfileIdentity)
    }

    @Test
    fun rejectsUnicodeDigitsFromExactProfileHexIdentity() {
        val rom = RomImage(ByteArray(0x200))
        val session = RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, "TEST"),
            exactProfile = profile("\u0660".repeat(64), rom.size),
        )

        assertNull(session.exactProfileIdentity)
    }

    @Test
    fun rejectsExactProfileIdentityWhenRomSizeOrParsedPlatformDoesNotMatch() {
        val rom = RomImage(ByteArray(0x200))
        val wrongSize = RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, "TEST"),
            exactProfile = profile(rom.sha256, rom.size + 1),
        )
        val wrongPlatform = RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, "TEST"),
            exactProfile = profile(rom.sha256, rom.size, platform = Platform.GBC),
        )

        assertNull(wrongSize.exactProfileIdentity)
        assertNull(wrongSize.exactProfileSnapshot)
        assertNull(wrongPlatform.exactProfileIdentity)
        assertNull(wrongPlatform.exactProfileSnapshot)
    }

    @Test
    fun exactProfileAuthoritativeTablesAreDeeplySnapshottedAtSessionCreation() {
        val rom = RomImage(ByteArray(0x200))
        val pointerOffsets = mutableListOf(16, 20)
        val bankRemap = mutableMapOf(1 to 2)
        val profile = profile(
            sha256 = rom.sha256,
            romSize = rom.size,
            tables = ProfileTables(
                descriptions = TableLayout(
                    offset = 0x100,
                    count = 4,
                    recordSize = 36,
                    pointerOffsets = pointerOffsets,
                    bankRemap = bankRemap,
                ),
            ),
        )

        val session = RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, "TEST"),
            exactProfile = profile,
        )
        pointerOffsets[0] = 24
        bankRemap[1] = 9

        val descriptions = requireNotNull(session.exactProfileSnapshot?.tables?.descriptions)
        assertEquals(listOf(16, 20), descriptions.pointerOffsets)
        assertEquals(mapOf(1 to 2), descriptions.bankRemap)
        assertEquals(1, requireNotNull(session.exactProfileSnapshot).internalSpeciesCount)

        try {
            @Suppress("UNCHECKED_CAST")
            (descriptions.pointerOffsets as MutableList<Int>).add(24)
            throw AssertionError("snapshot pointer offsets must be unmodifiable")
        } catch (_: UnsupportedOperationException) {
            // Expected.
        }
        try {
            @Suppress("UNCHECKED_CAST")
            (descriptions.bankRemap as MutableMap<Int, Int>)[3] = 4
            throw AssertionError("snapshot bank remap must be unmodifiable")
        } catch (_: UnsupportedOperationException) {
            // Expected.
        }
    }

    @Test
    fun defaultIndexRetainsBoundedInstructionSitesAndExcludesTheRomEnd() {
        val bytes = ByteArray(0x200)
        putReference(bytes, 0x20, 0x80, 0x100)
        putReference(bytes, 0x24, 0x84, 0x100)
        putReference(bytes, 0x28, 0x88, bytes.size)
        val session = RomAnalysisSession(
            rom = RomImage(bytes),
            header = RomHeader(Platform.GBA, "TEST"),
            limits = ResolutionLimits(maxCompiledReferenceSitesPerCandidate = 2),
        )

        val index = requireNotNull(session.gbaReferenceIndex)

        assertFalse(index.overflowed)
        assertEquals(GbaSiteEvidenceStatus.COMPLETE, index.siteEvidenceStatus)
        assertEquals(2, index.referenceCount(0x100))
        assertEquals(listOf(0x20, 0x24), index.target(0x100)?.instructionSites)
        assertEquals(0, index.referenceCount(bytes.size))
    }

    @Test
    fun referenceSiteOverflowIsTypedAndFailClosedPerTarget() {
        val bytes = ByteArray(0x200)
        putReference(bytes, 0x20, 0x80, 0x100)
        putReference(bytes, 0x24, 0x84, 0x100)
        val session = RomAnalysisSession(
            rom = RomImage(bytes),
            header = RomHeader(Platform.GBA, "TEST"),
            limits = ResolutionLimits(maxCompiledReferenceSitesPerCandidate = 1),
        )

        val index = requireNotNull(session.gbaReferenceIndex)
        val target = requireNotNull(index.target(0x100))

        assertEquals(2, target.count)
        assertEquals(GbaSiteEvidenceStatus.REFERENCE_SITES_INCOMPLETE, index.siteEvidenceStatus)
        assertFalse(index.siteEvidenceComplete)
        assertTrue(target.siteBudgetExceeded)
        assertTrue(target.instructionSites.isEmpty())
        assertEquals(2, target.observedSites)
        assertEquals(1, target.limitSites)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun publishedReferenceMapsAreTrulyUnmodifiable() {
        val index = GbaReferenceIndex.countsOnlyForTesting(mapOf(0x100 to 2))

        @Suppress("UNCHECKED_CAST")
        (index.counts as MutableMap<Int, Int>)[0x120] = 1
    }

    @Test
    fun countsOnlyFixtureIndexCannotBeMistakenForCompleteSiteEvidence() {
        val index = GbaReferenceIndex.countsOnlyForTesting(mapOf(0x100 to 2))
        val target = requireNotNull(index.target(0x100))

        assertEquals(GbaSiteEvidenceStatus.COUNTS_ONLY_INCOMPLETE, index.siteEvidenceStatus)
        assertFalse(index.siteEvidenceComplete)
        assertFalse(target.siteEvidenceAvailable)
        assertTrue(target.siteEvidenceUnavailableReason?.contains("counts-only") == true)
    }

    private fun profile(
        sha256: String,
        romSize: Int,
        platform: Platform = Platform.GBA,
        tables: ProfileTables = ProfileTables(),
    ): RomProfile = RomProfile(
        name = "Test profile",
        sha256 = sha256,
        crc32 = "00000000",
        family = EngineFamily.EMERALD,
        platform = platform,
        title = "TEST",
        revision = 0,
        romSize = romSize,
        dexSpeciesCount = 1,
        internalSpeciesCount = 1,
        moveCount = 1,
        tables = tables,
    )

    private fun putReference(bytes: ByteArray, instructionOffset: Int, literalOffset: Int, target: Int) {
        val pc = (instructionOffset + 4) and -4
        putU16(bytes, instructionOffset, 0x4800 or ((literalOffset - pc) / 4))
        putU32(bytes, literalOffset, 0x08000000 + target)
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
