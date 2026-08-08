package com.enrpau.dualscreendex.parser.cli

import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
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
