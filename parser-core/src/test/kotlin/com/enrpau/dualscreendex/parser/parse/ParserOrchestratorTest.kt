package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.ParserProbe
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ParserOrchestratorTest {
    @Test
    fun refusesCloseRunnerUp() {
        val result = ParserOrchestrator.select(listOf(probe(EngineFamily.EMERALD, 80), probe(EngineFamily.FIRERED_LEAFGREEN, 74)))
        assertEquals(SelectionStatus.AMBIGUOUS, result.status)
    }

    @Test
    fun selectsClearValidatedWinner() {
        val result = ParserOrchestrator.select(listOf(probe(EngineFamily.EMERALD, 88), probe(EngineFamily.FIRERED_LEAFGREEN, 50)))
        assertEquals(SelectionStatus.SELECTED, result.status)
        assertEquals(EngineFamily.EMERALD, result.winner?.family)
    }

    @Test
    fun requiresTwoIndependentAnchors() {
        val weak = probe(EngineFamily.EMERALD, 100).copy(anchors = 1)
        assertEquals(SelectionStatus.NO_FAMILY_MATCH, ParserOrchestrator.select(listOf(weak)).status)
    }

    @Test
    fun retainsIndependentCapabilitiesWithoutFamilyWinner() {
        val names = CapabilityEvidence(RomCapability.SPECIES_NAMES, true, 0.95, offset = 0x1234, count = 151)
        val candidate = probe(EngineFamily.RED_BLUE, 70).copy(capabilities = listOf(names))
        val selection = ParserOrchestrator.select(listOf(candidate))

        val capabilities = ParserOrchestrator.resolveCapabilities(selection, listOf(candidate))

        assertEquals(SelectionStatus.NO_FAMILY_MATCH, selection.status)
        assertEquals(RomCapability.entries.size, capabilities.size)
        assertEquals(true, capabilities.single { it.capability == RomCapability.SPECIES_NAMES }.compatible)
        assertEquals(false, capabilities.single { it.capability == RomCapability.BASE_STATS }.compatible)
        assertEquals(CapabilityStatus.NOT_FOUND, capabilities.single { it.capability == RomCapability.BASE_STATS }.status)
    }

    @Test
    fun conflictingIndependentCapabilityLocationsRemainUnavailable() {
        val first = CapabilityEvidence(RomCapability.SPECIES_NAMES, true, 0.95, offset = 0x1000, count = 151)
        val second = CapabilityEvidence(RomCapability.SPECIES_NAMES, true, 0.96, offset = 0x2000, count = 151)
        val probes = listOf(
            probe(EngineFamily.RED_BLUE, 70).copy(capabilities = listOf(first)),
            probe(EngineFamily.YELLOW, 69).copy(capabilities = listOf(second)),
        )

        val capability = ParserOrchestrator.resolveCapabilities(ParserOrchestrator.select(probes), probes)
            .single { it.capability == RomCapability.SPECIES_NAMES }

        assertEquals(false, capability.compatible)
        assertEquals(CapabilityStatus.NOT_FOUND, capability.status)
        assertEquals(true, capability.reasons.any { it.contains("conflicting") })
    }

    @Test
    fun colorEnhancedGenOneRomPassesYellowPlatformGate() {
        val bytes = ByteArray(0x100000)
        "POKEMON YELLOW".toByteArray().copyInto(bytes, 0x134)
        bytes[0x143] = 0x80.toByte()

        val result = ParserOrchestrator.analyze(RomImage(bytes))
        val yellow = result.probes.single { it.family == EngineFamily.YELLOW }

        assertEquals(true, yellow.hardGatePassed)
    }

    @Test
    fun fireRedFamilyUsesTheEngineTitleWhenAHackChangesTheGameCode() {
        val parser = FamilyParsers.all.single { it.family == EngineFamily.FIRERED_LEAFGREEN }

        val probe = parser.probe(
            RomImage(ByteArray(512)),
            RomHeader(Platform.GBA, "POKEMON FIRE", "GOLD"),
        )

        assertEquals(20, probe.scoreEvidence.single { it.category == "engine identity" }.points)
    }

    @Test
    fun emeraldProbeUsesRelocatedTypeChart() {
        val chartOffset = 300
        val chart = byteArrayOf(
            0, 5, 5,
            0, 8, 5,
            10, 10, 5,
            10, 11, 5,
            10, 12, 20,
            10, 15, 20,
            10, 6, 20,
            10, 5, 5,
            10, 16, 5,
            10, 8, 20,
            11, 10, 20,
            11, 11, 5,
            0xFF.toByte(), 0xFF.toByte(), 0,
        )
        val bytes = ByteArray(512) { 0x7F }
        chart.copyInto(bytes, chartOffset)
        val parser = FamilyParsers.all.single { it.family == EngineFamily.EMERALD }

        val probe = parser.probe(RomImage(bytes), RomHeader(Platform.GBA, "POKEMON EMER", "BPEE"))
        val typeChart = probe.capabilities.single { it.capability == RomCapability.TYPE_CHART }

        assertEquals(true, typeChart.compatible)
        assertEquals(chartOffset, typeChart.offset)
    }

    private fun probe(family: EngineFamily, score: Int) = ParserProbe(
        family = family,
        score = score,
        hardGatePassed = true,
        anchors = 3,
        scoreEvidence = emptyList(),
        capabilities = emptyList(),
    )
}
