package com.enrpau.dualscreendex.parser.cli

import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.ParseResult
import com.enrpau.dualscreendex.parser.model.ParserProbe
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportWriterTest {
    @Test
    fun markdownIncludesCandidatesAndCapabilitiesWithoutExtractedText() {
        val report = CorpusReport(
            roots = listOf("test"),
            results = listOf(
                CorpusResult(
                    displayName = "Pokemon Test.gba",
                    source = "Pokemon Test.gba",
                    durationMillis = 3,
                    result = sampleResult(),
                ),
            ),
        )

        val markdown = ReportWriter.markdown(report)

        assertTrue(markdown.contains("FIRERED_LEAFGREEN"))
        assertTrue(markdown.contains("SPECIES_NAMES"))
        assertFalse(markdown.contains("BULBASAUR"))
    }

    @Test
    fun jsonIsDeterministicForSameReport() {
        val report = CorpusReport(roots = emptyList(), results = emptyList())
        assertEquals(ReportWriter.json(report), ReportWriter.json(report))
        assertTrue(ReportWriter.json(report).contains("\"schemaVersion\": 3"))
        assertFalse(ReportWriter.markdown(report).contains("No mainline-family match"))
    }

    @Test
    fun markdownDistinguishesNotFoundFromNotApplicable() {
        val unavailable = CapabilityEvidence(
            RomCapability.TYPE_CHART,
            compatible = false,
            confidence = 0.0,
            status = CapabilityStatus.NOT_FOUND,
        )
        val notApplicable = CapabilityEvidence(
            RomCapability.ABILITIES,
            compatible = false,
            confidence = 0.0,
            status = CapabilityStatus.NOT_APPLICABLE,
        )
        val result = sampleResult().copy(capabilities = sampleResult().capabilities + unavailable + notApplicable)
        val report = CorpusReport(
            roots = listOf("test"),
            results = listOf(CorpusResult("Pokemon Test.gba", "Pokemon Test.gba", durationMillis = 1, result = result)),
        )

        val markdown = ReportWriter.markdown(report)

        assertTrue(markdown.contains("Ancestry score"))
        assertTrue(markdown.contains("`N/F` = applicable but not found or validated"))
        assertTrue(markdown.contains("`N/A` = not applicable to that engine"))
        assertTrue(markdown.contains("| N/F |"))
        assertTrue(markdown.contains("| N/A |"))
    }

    @Test
    fun markdownNamesEveryNoFamilyMatchInput() {
        val result = sampleResult().copy(
            status = SelectionStatus.NO_FAMILY_MATCH,
            selectedFamily = null,
            selectedProfile = null,
            capabilities = RomCapability.entries.map { CapabilityEvidence(it, false, 0.0) },
        )
        val report = CorpusReport(
            roots = listOf("test"),
            results = listOf(CorpusResult("Pokemon Pinball.gbc", "Pokemon Pinball.gbc", durationMillis = 1, result = result)),
        )

        val markdown = ReportWriter.markdown(report)

        assertTrue(markdown.contains("No mainline-family match (1)"))
        assertTrue(markdown.contains("Pokemon Pinball.gbc"))
        assertFalse(markdown.contains("Unsupported"))
    }

    private fun sampleResult(): ParseResult {
        val capability = CapabilityEvidence(RomCapability.SPECIES_NAMES, true, 1.0, 0x100, 411, 11, listOf("validated"))
        val probe = ParserProbe(
            family = EngineFamily.FIRERED_LEAFGREEN,
            score = 92,
            hardGatePassed = true,
            anchors = 4,
            scoreEvidence = emptyList(),
            capabilities = listOf(capability),
            profileName = "test profile",
        )
        return ParseResult(
            header = RomHeader(Platform.GBA, "POKEMON FIRE", "BPRE", 1),
            sha256 = "0".repeat(64),
            crc32 = "00000000",
            size = 1024,
            status = SelectionStatus.SELECTED,
            selectedFamily = EngineFamily.FIRERED_LEAFGREEN,
            selectedProfile = "test profile",
            runnerUpMargin = 20,
            probes = listOf(probe),
            capabilities = listOf(capability),
        )
    }
}
