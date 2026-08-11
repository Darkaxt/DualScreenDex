package com.enrpau.dualscreendex.parser.resolution

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.RomProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateAuthorityProofTest {
    @Test
    fun sessionDerivedExactProofWinsOnlyInItsMatchingSelectionContext() {
        val exactSession = exactSession(ByteArray(0x200))
        val otherSession = RomAnalysisSession(
            rom = RomImage(ByteArray(0x201)),
            header = RomHeader(Platform.GBA, "OTHER"),
        )
        val exact = candidate(
            identity = CandidateIdentity("base-stats:exact"),
            source = CandidateSource.EXACT_PROFILE,
            eligibility = exactSession.exactProfileEligibility(),
            coverage = EvidenceCoverage(1, 10),
        )
        val direct = candidate(
            identity = CandidateIdentity("base-stats:direct"),
            source = CandidateSource.DIRECT_COMPILED_CONSUMER,
            eligibility = CandidateEligibility.validated(CandidateSource.DIRECT_COMPILED_CONSUMER),
            coverage = EvidenceCoverage(10, 10),
        )

        val matching = CandidateSelector.select(
            exactSession,
            DatasetKind.BASE_STATS,
            sequenceOf(direct, exact),
        )
        val foreign = CandidateSelector.select(
            otherSession,
            DatasetKind.BASE_STATS,
            sequenceOf(exact),
        )

        assertEquals(CandidateSource.EXACT_PROFILE, selected(matching).source)
        assertTrue(foreign is DatasetResolution.Unavailable)
    }

    @Test(expected = IllegalArgumentException::class)
    fun exactAuthorityCannotBeCreatedFromTheSourceEnumAlone() {
        CandidateEligibility.validated(CandidateSource.EXACT_PROFILE)
    }

    private fun exactSession(bytes: ByteArray): RomAnalysisSession {
        val rom = RomImage(bytes)
        return RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, "TEST"),
            exactProfile = profile(rom.sha256, rom.size),
        )
    }

    private fun profile(sha256: String, romSize: Int): RomProfile = RomProfile(
        name = "Exact test profile",
        sha256 = sha256,
        crc32 = "00000000",
        family = EngineFamily.EMERALD,
        platform = Platform.GBA,
        title = "TEST",
        revision = 0,
        romSize = romSize,
        dexSpeciesCount = 1,
        internalSpeciesCount = 1,
        moveCount = 1,
        tables = ProfileTables(),
    )

    private fun candidate(
        identity: CandidateIdentity,
        source: CandidateSource,
        eligibility: CandidateEligibility,
        coverage: EvidenceCoverage,
    ): DatasetCandidate<TestLayout> = DatasetCandidate(
        identity = identity,
        kind = DatasetKind.BASE_STATS,
        layout = TestLayout(identity.value),
        source = source,
        strength = CandidateStrength(
            semanticCoverage = coverage,
            structuralCoverage = coverage,
        ),
        diagnosticLabel = identity.value,
        eligibility = eligibility,
    )

    private fun selected(result: DatasetResolution<TestLayout>): DatasetCandidate<TestLayout> = when (result) {
        is DatasetResolution.Resolved -> result.candidate
        is DatasetResolution.Partial -> result.candidate
        else -> error("expected selected candidate, got $result")
    }

    private data class TestLayout(val value: String) : ImmutableDatasetLayout<TestLayout> {
        override val layoutIdentity: CandidateLayoutIdentity = CandidateLayoutIdentity("layout:$value")

        override fun immutableSnapshot(): TestLayout = this
    }
}
