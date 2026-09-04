package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetCodec
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetFormat
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetTableLayout
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetTableOutcome
import com.enrpau.dualscreendex.parser.dataset.learnsets.ResolvedLearnsetLayout
import com.enrpau.dualscreendex.parser.dataset.learnsets.ResolvedLearnsetSet
import com.enrpau.dualscreendex.parser.dataset.learnsets.ResolvedSelectedLearnsetTable
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.resolvedEnglishLayout
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.ParserProbe
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedDatasetLayouts
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ParserOrchestratorTest {
    @Test
    fun capabilityEvidencePreservesPartialCoverageAndAmbiguity() {
        val partial = capabilityEvidence(
            RomCapability.LEARNSETS,
            ValidationEvidence(true, 7, 10, 0.70, emptyList(), offset = 0x100, recordSize = 4, elementSize = 4),
        )
        val ambiguous = capabilityEvidence(
            RomCapability.EVOLUTIONS,
            ValidationEvidence(
                false, 0, 2, 0.0, emptyList(), ambiguous = true, reviewRecommended = true,
            ),
        )

        assertEquals(CapabilityStatus.PARTIAL, partial.status)
        assertEquals(7, partial.validRecords)
        assertEquals(10, partial.totalRecords)
        assertEquals(4, partial.elementSize)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, partial.reviewStatus)
        assertEquals(false, partial.validatorReviewRecommended)
        assertEquals(CapabilityStatus.AMBIGUOUS, ambiguous.status)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, ambiguous.reviewStatus)
        assertEquals(true, ambiguous.validatorReviewRecommended)
    }

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
    fun selectsExactScoreTieWithSingleCompleteTypeChart() {
        val completeTypeChart = CapabilityEvidence(
            capability = RomCapability.TYPE_CHART,
            compatible = true,
            confidence = 1.0,
            count = 108,
            status = CapabilityStatus.AVAILABLE,
            validRecords = 108,
            totalRecords = 108,
        )
        val result = ParserOrchestrator.select(
            listOf(
                probe(EngineFamily.GOLD_SILVER, 80),
                probe(EngineFamily.CRYSTAL, 80).copy(capabilities = listOf(completeTypeChart)),
            ),
        )

        assertEquals(SelectionStatus.SELECTED, result.status)
        assertEquals(EngineFamily.CRYSTAL, result.winner?.family)
        assertEquals(0, result.margin)
    }

    @Test
    fun refusesExactScoreTieWhenBothTypeChartsAreComplete() {
        val completeTypeChart = CapabilityEvidence(
            capability = RomCapability.TYPE_CHART,
            compatible = true,
            confidence = 1.0,
            count = 108,
            status = CapabilityStatus.AVAILABLE,
            validRecords = 108,
            totalRecords = 108,
        )
        val result = ParserOrchestrator.select(
            listOf(
                probe(EngineFamily.EMERALD, 80).copy(capabilities = listOf(completeTypeChart)),
                probe(EngineFamily.FIRERED_LEAFGREEN, 80).copy(capabilities = listOf(completeTypeChart)),
            ),
        )

        assertEquals(SelectionStatus.AMBIGUOUS, result.status)
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
        val first = CapabilityEvidence(
            RomCapability.SPECIES_NAMES,
            true,
            0.95,
            offset = 0x1000,
            count = 151,
            reasons = listOf("first validator recovery"),
            reviewStatus = CapabilityReviewStatus.MANUAL_REVIEW,
            validatorReviewRecommended = true,
        )
        val second = CapabilityEvidence(
            RomCapability.SPECIES_NAMES,
            true,
            0.96,
            offset = 0x2000,
            count = 151,
            reasons = listOf("second validated locator"),
        )
        val probes = listOf(
            probe(EngineFamily.RED_BLUE, 70).copy(capabilities = listOf(first)),
            probe(EngineFamily.YELLOW, 69).copy(capabilities = listOf(second)),
        )
        val selection = ParserOrchestrator.select(probes)

        val capability = ParserOrchestrator.resolveCapabilities(selection, probes)
            .single { it.capability == RomCapability.SPECIES_NAMES }

        assertEquals(SelectionStatus.NO_FAMILY_MATCH, selection.status)
        assertEquals(false, capability.compatible)
        assertEquals(CapabilityStatus.AMBIGUOUS, capability.status)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, capability.reviewStatus)
        assertEquals(true, capability.validatorReviewRecommended)
        assertEquals(true, capability.reasons.any { it.contains("conflicting") })
        assertEquals(true, capability.reasons.any { it.contains("first validator recovery") })
        assertEquals(true, capability.reasons.any { it.contains("second validated locator") })
    }

    @Test
    fun sameLocationEvidenceMergesReviewAndAmbiguityIntoStrongestStructuralCandidate() {
        val strongest = CapabilityEvidence(
            capability = RomCapability.SPECIES_NAMES,
            compatible = true,
            confidence = 0.98,
            offset = 0x1000,
            count = 151,
            recordSize = 11,
            reasons = listOf("strongest structural candidate"),
            status = CapabilityStatus.AVAILABLE,
            validRecords = 151,
            totalRecords = 151,
            elementSize = 1,
        )
        val reviewable = strongest.copy(
            confidence = 0.91,
            reasons = listOf("lower-confidence validator recovery"),
            status = CapabilityStatus.AMBIGUOUS,
            validRecords = 149,
            elementSize = 2,
            reviewStatus = CapabilityReviewStatus.MANUAL_REVIEW,
            validatorReviewRecommended = true,
        )
        val probes = listOf(
            probe(EngineFamily.RED_BLUE, 70).copy(capabilities = listOf(strongest)),
            probe(EngineFamily.YELLOW, 69).copy(capabilities = listOf(reviewable)),
        )

        val merged = ParserOrchestrator.resolveCapabilities(ParserOrchestrator.select(probes), probes)
            .single { it.capability == RomCapability.SPECIES_NAMES }

        assertEquals(0.98, merged.confidence, 0.0)
        assertEquals(151, merged.validRecords)
        assertEquals(1, merged.elementSize)
        assertEquals(CapabilityStatus.AMBIGUOUS, merged.status)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, merged.reviewStatus)
        assertEquals(true, merged.validatorReviewRecommended)
        assertEquals(
            listOf("strongest structural candidate", "lower-confidence validator recovery"),
            merged.reasons,
        )
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
        val resolvedTypeChart = requireNotNull(probe.resolvedLayout?.resolvedDatasets?.typeChart)
        assertEquals(chartOffset.toLong(), resolvedTypeChart.table.offset)
        assertEquals(
            listOf(com.enrpau.dualscreendex.parser.catalog.TypeMatchup(0, 5, 50)),
            resolvedTypeChart.catalogMatchups().take(1),
        )
    }

    @Test
    fun selectedGen3LearnsetEvidenceUsesSemanticSpeciesIds() {
        val speciesCount = 6
        val namesOffset = 0x100
        val statsOffset = 0x200
        val dexMapOffset = 0x400
        val learnsetTableOffset = 0x500
        val malformedOffset = 0x700
        val bytes = ByteArray(0x800)
        putFixedGbaName(bytes, namesOffset, 0, "NONE")
        putFixedGbaName(bytes, namesOffset, 1, "ALPHA")
        bytes[namesOffset + 2 * 11] = 0xFF.toByte()
        putFixedGbaName(bytes, namesOffset, 3, "RESERVED")
        putFixedGbaName(bytes, namesOffset, 4, "FALLEN")
        putFixedGbaName(bytes, namesOffset, 5, "ACTIVE")
        putValidGen3Stats(bytes, statsOffset + 1 * 28)
        putValidGen3Stats(bytes, statsOffset + 2 * 28)
        putValidGen3Stats(bytes, statsOffset + 5 * 28)
        listOf(1, 2, 0, 3, 4).forEachIndexed { index, dex ->
            putU16(bytes, dexMapOffset + index * 2, dex)
        }
        repeat(speciesCount) { speciesId ->
            val target = if (speciesId == 4) malformedOffset else 0x740 + speciesId * 2
            putPointer(bytes, learnsetTableOffset + speciesId * 4, target)
            if (speciesId != 4) putU16(bytes, target, 0xFFFF)
        }
        val rawLayout = resolvedEnglishLayout(
            family = EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 32,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                baseStats = TableLayout(statsOffset, speciesCount, 28),
                learnsets = TableLayout(learnsetTableOffset, speciesCount, 4, elementSize = 2),
            ),
        )
        val rom = RomImage(bytes)
        val typedTable = LearnsetTableLayout(
            learnsetTableOffset.toLong(),
            speciesCount,
            LearnsetFormat.PackedU16(9),
        )
        val decoded = LearnsetCodec().decodeGen3(
            RomAnalysisSession(rom, RomHeaderReader.read(rom)),
            typedTable,
            moveCount = 32,
        ) as LearnsetTableOutcome.Decoded
        val typed = ResolvedSelectedLearnsetTable(
            ResolvedLearnsetLayout(typedTable, decoded.rows),
            confidence = 5.0 / 6.0,
            referenceCount = 1,
        )
        val layout = rawLayout.copy(
            resolvedDatasets = ResolvedDatasetLayouts(
                learnsets = ResolvedLearnsetSet(
                    listOf(typed),
                    primaryOffset = learnsetTableOffset.toLong(),
                    selector = null,
                ),
            ),
        )
        val rawCapabilities = listOf(
            capabilityEvidence(
                RomCapability.SPECIES_NAMES,
                ValidationEvidence(true, 5, 6, 5.0 / 6.0, emptyList()),
            ),
            capabilityEvidence(
                RomCapability.BASE_STATS,
                ValidationEvidence(true, 3, 6, 0.5, emptyList()),
            ),
            capabilityEvidence(
                RomCapability.LEARNSETS,
                ValidationEvidence(
                    compatible = true,
                    validRecords = 5,
                    totalRecords = 6,
                    confidence = 5.0 / 6.0,
                    reasons = listOf("quarantined 1 malformed row"),
                    elementSize = 2,
                    incompleteRecords = 1,
                    reviewRecommended = true,
                ),
            ),
        )

        val learnsets = ParserOrchestrator.applySpeciesSemanticDomain(
            rom,
            layout,
            rawCapabilities,
        ).single { it.capability == RomCapability.LEARNSETS }

        assertEquals(5, learnsets.validRecords)
        assertEquals(6, learnsets.totalRecords)
        assertEquals(2, learnsets.coveredRecords)
        assertEquals(3, learnsets.expectedRecords)
        assertEquals(1, learnsets.incompleteRecords)
        assertEquals(CapabilityStatus.PARTIAL, learnsets.status)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, learnsets.reviewStatus)
        assertEquals(true, learnsets.reasons.any { it.contains("semantic learnset coverage 2/3") })
    }

    private fun probe(family: EngineFamily, score: Int) = ParserProbe(
        family = family,
        score = score,
        hardGatePassed = true,
        anchors = 3,
        scoreEvidence = emptyList(),
        capabilities = emptyList(),
    )

    private fun putPointer(bytes: ByteArray, offset: Int, target: Int) {
        val value = 0x08000000L + target
        repeat(4) { index -> bytes[offset + index] = ((value ushr (index * 8)) and 0xFF).toByte() }
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun putFixedGbaName(bytes: ByteArray, tableOffset: Int, index: Int, text: String) {
        text.forEachIndexed { characterIndex, character ->
            bytes[tableOffset + index * 11 + characterIndex] =
                (0xBB + character.code - 'A'.code).toByte()
        }
        bytes[tableOffset + index * 11 + text.length] = 0xFF.toByte()
    }

    private fun putValidGen3Stats(bytes: ByteArray, offset: Int) {
        repeat(6) { stat -> bytes[offset + stat] = (40 + stat).toByte() }
        bytes[offset + 6] = 1
        bytes[offset + 7] = 2
    }
}
