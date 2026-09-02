package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionCodec
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionTableLayout
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionTableOutcome
import com.enrpau.dualscreendex.parser.dataset.descriptions.ResolvedDescriptionLayout
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.resolvedEnglishLayout
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.PokeemeraldExpansionMetadata
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedDatasetLayouts
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.validate.TableValidators
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticCoverageTest {
    private fun availableCapability(capability: RomCapability, count: Int) = CapabilityEvidence(
        capability = capability,
        compatible = true,
        confidence = 1.0,
        count = count,
        validRecords = count,
        totalRecords = count,
        status = CapabilityStatus.AVAILABLE,
    )

    @Test
    fun unresolvedExpandedSpeciesIndexCannotBecomeASuccessfulEmptySemanticDomain() {
        val speciesCount = 420
        val namesOffset = 0x100
        val statsOffset = 0x1400
        val bytes = ByteArray(0x5000) { 0x7F }
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        repeat(speciesCount) { id -> putValidGen3Stats(bytes, statsOffset + id * 28) }
        val layout = resolvedEnglishLayout(
            EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                baseStats = TableLayout(statsOffset, speciesCount, 28),
            ),
        )
        val raw = listOf(
            availableCapability(RomCapability.SPECIES_CATALOG, speciesCount),
            availableCapability(RomCapability.SPECIES_NAMES, speciesCount),
            availableCapability(RomCapability.SPECIES_TYPES, speciesCount),
            availableCapability(RomCapability.BASE_STATS, speciesCount),
        )

        val result = ParserOrchestrator.applySpeciesSemanticDomain(RomImage(bytes), layout, raw)
        val byCapability = result.associateBy { it.capability }

        val speciesCatalog = byCapability.getValue(RomCapability.SPECIES_CATALOG)
        assertEquals(
            speciesCatalog.reasons.joinToString("; "),
            CapabilityStatus.NOT_FOUND,
            speciesCatalog.status,
        )
        assertTrue(
            speciesCatalog.reasons.any { it.contains("species-to-Dex") },
        )
        assertEquals(CapabilityStatus.AVAILABLE, byCapability.getValue(RomCapability.SPECIES_NAMES).status)
        assertFalse(result.flatMap { it.reasons }.any { it.contains("excluded 420") })
    }

    @Test
    fun expansionMediaCoverageExcludesOnlyUnnamedZeroDexStructuralSlots() {
        val fixture = expansionSemanticFixture(includeSecondMedia = true)
        val raw = listOf(
            rawReviewCapability(RomCapability.SPECIES_NAMES, 2, 3),
            rawReviewCapability(RomCapability.BASE_STATS, 2, 3),
            rawReviewCapability(RomCapability.LEARNSETS, 2, 3),
            rawReviewCapability(RomCapability.SPRITES, 2, 3),
            rawReviewCapability(RomCapability.POKEDEX_DESCRIPTIONS, 2, 3),
        )

        val capabilities = ParserOrchestrator.applySpeciesSemanticDomain(
            RomImage(fixture.bytes), fixture.layout, raw,
        ).associateBy { it.capability }

        listOf(
            RomCapability.SPECIES_NAMES,
            RomCapability.BASE_STATS,
            RomCapability.LEARNSETS,
            RomCapability.SPRITES,
            RomCapability.POKEDEX_DESCRIPTIONS,
        ).forEach { capability ->
            val evidence = capabilities.getValue(capability)
            assertEquals(2, evidence.coveredRecords)
            assertEquals(2, evidence.expectedRecords)
            assertEquals(0, evidence.incompleteRecords)
            assertEquals(CapabilityStatus.AVAILABLE, evidence.status)
            assertEquals(CapabilityReviewStatus.NONE, evidence.reviewStatus)
        }
    }

    @Test
    fun expansionPositiveDexRowWithStatsButMissingNameRemainsActiveAndReviewable() {
        val fixture = expansionSemanticFixture(includeSecondMedia = true, blankSecondName = true)
        val raw = listOf(
            rawReviewCapability(RomCapability.SPECIES_NAMES, 1, 3),
            rawReviewCapability(RomCapability.BASE_STATS, 2, 3),
        )

        val names = ParserOrchestrator.applySpeciesSemanticDomain(
            RomImage(fixture.bytes), fixture.layout, raw,
        ).single { it.capability == RomCapability.SPECIES_NAMES }

        assertEquals(1, names.coveredRecords)
        assertEquals(2, names.expectedRecords)
        assertEquals(1, names.incompleteRecords)
        assertEquals(CapabilityStatus.PARTIAL, names.status)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, names.reviewStatus)
    }

    @Test
    fun expansionMediaCoverageKeepsANamedActiveMissingRowPartialAndReviewable() {
        val fixture = expansionSemanticFixture(includeSecondMedia = false)
        val raw = listOf(
            rawReviewCapability(RomCapability.SPECIES_NAMES, 2, 3),
            rawReviewCapability(RomCapability.BASE_STATS, 2, 3),
            rawReviewCapability(RomCapability.SPRITES, 1, 3),
            rawReviewCapability(RomCapability.POKEDEX_DESCRIPTIONS, 1, 3),
        )

        val capabilities = ParserOrchestrator.applySpeciesSemanticDomain(
            RomImage(fixture.bytes), fixture.layout, raw,
        ).associateBy { it.capability }

        listOf(RomCapability.SPRITES, RomCapability.POKEDEX_DESCRIPTIONS).forEach { capability ->
            val evidence = capabilities.getValue(capability)
            assertEquals(1, evidence.coveredRecords)
            assertEquals(2, evidence.expectedRecords)
            assertEquals(1, evidence.incompleteRecords)
            assertEquals(CapabilityStatus.PARTIAL, evidence.status)
            assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, evidence.reviewStatus)
        }
    }

    @Test
    fun compiledRegionalDexOrderDefinesTheActiveSpeciesDomain() {
        val speciesCount = 8
        val namesOffset = 0x200
        val statsOffset = 0x300
        val regionalCountOffset = 0x100
        val bytes = ByteArray(0x500)
        repeat(speciesCount) { id ->
            putFixedGbaName(bytes, namesOffset, id, if (id == 0) "NONE" else "MON")
            if (id > 0) putValidGen3Stats(bytes, statsOffset + id * 28)
        }
        (1 until speciesCount).forEach { speciesId ->
            putU16(bytes, 0x180 + (speciesId - 1) * 2, speciesId)
        }
        val activeSpecies = listOf(1, 3, 5, 7)
        putU16(bytes, regionalCountOffset, activeSpecies.size)
        activeSpecies.forEachIndexed { index, speciesId ->
            putU16(bytes, regionalCountOffset + 2 + index * 2, speciesId)
        }
        putRepeatedThumbLiteralReferences(bytes, regionalCountOffset, regionalCountOffset + 2)
        val layout = resolvedEnglishLayout(
            family = EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = null,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                baseStats = TableLayout(statsOffset, speciesCount, 28),
            ),
        )

        val domain = SpeciesSemanticDomainResolver.resolve(RomImage(bytes), layout)

        assertEquals(activeSpecies.toSet(), domain.expectedSpeciesIds)
        assertEquals(activeSpecies.size, domain.expectedRecords)
        assertEquals(activeSpecies.size, domain.coveredStatRecords)
        assertEquals(SpeciesSemanticDomainSource.STRONGLY_REFERENCED_REGIONAL_ORDER, domain.source)
        assertTrue(domain.applyToNames(rawEvidence(speciesCount)).reasons.any {
            it.contains("compiled-referenced regional Pokédex order")
        })
    }

    @Test
    fun weakAdjacentReferencedListDoesNotReplaceTheSpeciesDomain() {
        val speciesCount = 8
        val namesOffset = 0x200
        val statsOffset = 0x300
        val listCountOffset = 0x100
        val bytes = ByteArray(0x500)
        repeat(speciesCount) { id ->
            putFixedGbaName(bytes, namesOffset, id, if (id == 0) "NONE" else "MON")
            if (id > 0) putValidGen3Stats(bytes, statsOffset + id * 28)
        }
        (1 until speciesCount).forEach { speciesId ->
            putU16(bytes, 0x180 + (speciesId - 1) * 2, speciesId)
        }
        putU16(bytes, listCountOffset, 4)
        listOf(1, 3, 5, 7).forEachIndexed { index, speciesId ->
            putU16(bytes, listCountOffset + 2 + index * 2, speciesId)
        }
        putThumbLiteralReference(bytes, 0x00, 0x40, listCountOffset)
        putThumbLiteralReference(bytes, 0x02, 0x44, listCountOffset + 2)
        val layout = resolvedEnglishLayout(
            family = EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = null,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                baseStats = TableLayout(statsOffset, speciesCount, 28),
            ),
        )

        val domain = SpeciesSemanticDomainResolver.resolve(RomImage(bytes), layout)

        assertEquals((1 until speciesCount).toSet(), domain.expectedSpeciesIds)
        assertEquals(SpeciesSemanticDomainSource.NAVIGABLE_SPECIES_FALLBACK, domain.source)
    }

    @Test
    fun unreferencedOrderedListDoesNotReplaceTheSpeciesDomain() {
        val fixture = semanticDomainFixture(listOf(1, 3, 5, 7), referenceBase = null)

        val domain = SpeciesSemanticDomainResolver.resolve(RomImage(fixture.bytes), fixture.layout)

        assertEquals((1 until fixture.speciesCount).toSet(), domain.expectedSpeciesIds)
    }

    @Test
    fun stronglyReferencedAlphabeticalStyleOrderDoesNotReplaceTheSpeciesDomain() {
        val fixture = semanticDomainFixture(listOf(1, 4, 2, 5), referenceBase = 0x00)

        val domain = SpeciesSemanticDomainResolver.resolve(RomImage(fixture.bytes), fixture.layout)

        assertEquals((1 until fixture.speciesCount).toSet(), domain.expectedSpeciesIds)
    }

    @Test
    fun stronglyReferencedRandomOrderDoesNotReplaceTheSpeciesDomain() {
        val fixture = semanticDomainFixture(listOf(7, 2, 6, 1), referenceBase = 0x00)

        val domain = SpeciesSemanticDomainResolver.resolve(RomImage(fixture.bytes), fixture.layout)

        assertEquals((1 until fixture.speciesCount).toSet(), domain.expectedSpeciesIds)
    }

    @Test
    fun equallyStrongCredibleRegionalRootsAreAmbiguousRegardlessOfLength() {
        val speciesCount = 8
        val namesOffset = 0x300
        val statsOffset = 0x400
        val bytes = ByteArray(0x600)
        repeat(speciesCount) { id ->
            putFixedGbaName(bytes, namesOffset, id, if (id == 0) "NONE" else "MON")
            if (id > 0) putValidGen3Stats(bytes, statsOffset + id * 28)
        }
        putRegionalOrder(bytes, 0x100, listOf(1, 2, 3, 4))
        putRegionalOrder(bytes, 0x120, listOf(1, 2, 3, 4, 5))
        repeat(speciesCount - 1) { index -> putU16(bytes, 0x500 + index * 2, index + 1) }
        putRepeatedThumbLiteralReferences(bytes, 0x100, 0x102, instructionBase = 0x00, literalBase = 0x40)
        putRepeatedThumbLiteralReferences(bytes, 0x120, 0x122, instructionBase = 0x10, literalBase = 0x60)
        val layout = resolvedEnglishLayout(
            family = EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = null,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                baseStats = TableLayout(statsOffset, speciesCount, 28),
            ),
        )

        val domain = SpeciesSemanticDomainResolver.resolve(RomImage(bytes), layout)

        assertEquals((1 until speciesCount).toSet(), domain.expectedSpeciesIds)
    }

    @Test
    fun gen3SpeciesDomainExcludesStructuralSlotsButRetainsNamedDexStubs() {
        val recordSize = 28
        val speciesCount = 6
        val namesOffset = 0x100
        val statsOffset = 0x200
        val dexMapOffset = 0x400
        val bytes = ByteArray(0x500)
        putFixedGbaName(bytes, namesOffset, 0, "NONE")
        putFixedGbaName(bytes, namesOffset, 1, "ALPHA")
        // A terminated blank name with a positive Dex number is structural, not semantic.
        bytes[namesOffset + 2 * 11] = 0xFF.toByte()
        putFixedGbaName(bytes, namesOffset, 3, "RESERVED")
        putFixedGbaName(bytes, namesOffset, 4, "STUB")
        putFixedGbaName(bytes, namesOffset, 5, "ACTIVE")
        putValidGen3Stats(bytes, statsOffset + 1 * recordSize)
        putValidGen3Stats(bytes, statsOffset + 2 * recordSize)
        putValidGen3Stats(bytes, statsOffset + 5 * recordSize)
        // Internal IDs 1..5 map to Dex 1, 2, reserved, 3, 4.
        listOf(1, 2, 0, 3, 4).forEachIndexed { index, dex ->
            putU16(bytes, dexMapOffset + index * 2, dex)
        }
        val layout = resolvedEnglishLayout(
            family = EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = null,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                baseStats = TableLayout(statsOffset, speciesCount, recordSize),
            ),
        )

        val domain = SpeciesSemanticDomainResolver.resolve(RomImage(bytes), layout)
        val rawStats = ValidationEvidence(
            compatible = true,
            validRecords = 3,
            totalRecords = speciesCount,
            confidence = 0.5,
            reasons = emptyList(),
        )
        val semanticStats = domain.applyToStats(rawStats)
        val capability = capabilityEvidence(RomCapability.BASE_STATS, semanticStats)

        assertEquals(3, domain.expectedRecords)
        assertEquals(setOf(1, 4, 5), domain.expectedSpeciesIds)
        assertEquals(2, domain.coveredStatRecords)
        assertEquals(3, domain.excludedStructuralRecords)
        assertEquals(1, domain.incompleteRecords)
        assertEquals(3, semanticStats.validRecords)
        assertEquals(6, semanticStats.totalRecords)
        assertEquals(2, semanticStats.coveredRecords)
        assertEquals(3, semanticStats.expectedRecords)
        assertEquals(1, semanticStats.incompleteRecords)
        assertTrue(semanticStats.reviewRecommended)
        assertTrue(semanticStats.reasons.any { it.contains("1 named positive-Dex") })
        assertTrue(semanticStats.reasons.any { it.contains("excluded 3") })
        assertEquals(CapabilityStatus.PARTIAL, capability.status)
        assertEquals(3, capability.validRecords)
        assertEquals(6, capability.totalRecords)
        assertEquals(2, capability.coveredRecords)
        assertEquals(3, capability.expectedRecords)
        assertEquals(1, capability.incompleteRecords)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, capability.reviewStatus)
    }

    @Test
    fun gen3LearnsetsUseTheSharedSpeciesDomainAndRetainNamedDexStubs() {
        val domain = SpeciesSemanticDomain(
            expectedSpeciesIds = setOf(1, 4, 5),
            coveredStatRecords = 2,
            excludedStructuralRecords = 3,
        )
        val rawLearnsets = ValidationEvidence(
            compatible = true,
            validRecords = 5,
            totalRecords = 6,
            confidence = 5.0 / 6.0,
            reasons = listOf("quarantined 1 malformed row"),
            incompleteRecords = 1,
            reviewRecommended = true,
        )

        val semanticLearnsets = domain.applyToLearnsets(rawLearnsets, coveredSpeciesIds = setOf(0, 1, 2, 3, 5))
        val capability = capabilityEvidence(RomCapability.LEARNSETS, semanticLearnsets)

        assertEquals(5, semanticLearnsets.validRecords)
        assertEquals(6, semanticLearnsets.totalRecords)
        assertEquals(2, semanticLearnsets.coveredRecords)
        assertEquals(3, semanticLearnsets.expectedRecords)
        assertEquals(1, semanticLearnsets.incompleteRecords)
        assertTrue(semanticLearnsets.reviewRecommended)
        assertTrue(semanticLearnsets.reasons.any { it.contains("quarantined 1") })
        assertTrue(semanticLearnsets.reasons.any { it.contains("excluded 3") })
        assertTrue(semanticLearnsets.reasons.any { it.contains("semantic learnset coverage 2/3") })
        assertEquals(CapabilityStatus.PARTIAL, capability.status)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, capability.reviewStatus)
    }

    @Test
    fun completeStrongActiveDomainClearsReviewCausedOnlyByInactiveRawSlots() {
        val domain = SpeciesSemanticDomain(
            expectedSpeciesIds = setOf(1, 2),
            coveredStatRecords = 2,
            excludedStructuralRecords = 8,
            source = SpeciesSemanticDomainSource.STRONGLY_REFERENCED_REGIONAL_ORDER,
        )
        val raw = ValidationEvidence(
            compatible = true,
            validRecords = 8,
            totalRecords = 10,
            confidence = 0.8,
            reasons = emptyList(),
        )

        assertFalse(domain.applyToStats(raw).reviewRecommended)
        assertFalse(domain.applyToDescriptions(raw, coveredSpeciesIds = setOf(1, 2)).reviewRecommended)
    }

    @Test
    fun completeCompiledDexMapDomainClearsReviewCausedOnlyByInactiveRawSlots() {
        val domain = SpeciesSemanticDomain(
            expectedSpeciesIds = setOf(1, 2),
            coveredStatRecords = 2,
            excludedStructuralRecords = 8,
            source = SpeciesSemanticDomainSource.COMPILED_SPECIES_TO_DEX_MAP,
        )
        val raw = ValidationEvidence(
            compatible = true,
            validRecords = 8,
            totalRecords = 10,
            confidence = 0.8,
            reasons = emptyList(),
        )

        assertFalse(domain.applyToStats(raw).reviewRecommended)
        assertFalse(domain.applyToDescriptions(raw, coveredSpeciesIds = setOf(1, 2)).reviewRecommended)
    }

    @Test
    fun completeCompiledDexMapDomainRetainsExplicitDescriptionRecoveryReview() {
        val domain = SpeciesSemanticDomain(
            expectedSpeciesIds = setOf(1, 2),
            coveredStatRecords = 2,
            excludedStructuralRecords = 8,
            source = SpeciesSemanticDomainSource.COMPILED_SPECIES_TO_DEX_MAP,
        )
        val recoveryReason = "recovered 1 unique off-by-one description pointer within referenced text boundaries"
        val recovered = ValidationEvidence(
            compatible = true,
            validRecords = 10,
            totalRecords = 10,
            confidence = 1.0,
            reasons = listOf(recoveryReason),
            reviewRecommended = true,
        )

        val descriptions = domain.applyToDescriptions(recovered, coveredSpeciesIds = setOf(1, 2))

        assertEquals(0, descriptions.incompleteRecords)
        assertTrue(descriptions.reviewRecommended)
        assertTrue(recoveryReason in descriptions.reasons)
    }

    @Test
    fun authoritativeDomainClearsPhysicalOnlyDescriptionReviewWithInformationalReasons() {
        val fixture = duplicateCompiledDexMapFixture(firstReferences = 3, secondReferences = 2)
        val trimReason = "trimmed adjacent non-description data after a structurally stronger Gen 3 prefix"
        val raw = listOf(
            rawCapability(RomCapability.SPECIES_NAMES, fixture.speciesCount, fixture.speciesCount),
            rawCapability(RomCapability.BASE_STATS, fixture.speciesCount - 1, fixture.speciesCount),
            CapabilityEvidence(
                capability = RomCapability.POKEDEX_DESCRIPTIONS,
                compatible = true,
                confidence = (fixture.speciesCount - 1).toDouble() / fixture.speciesCount,
                reasons = listOf(trimReason),
                status = CapabilityStatus.PARTIAL,
                validRecords = fixture.speciesCount - 1,
                totalRecords = fixture.speciesCount,
                reviewStatus = CapabilityReviewStatus.MANUAL_REVIEW,
                coveredRecords = fixture.speciesCount - 1,
                expectedRecords = fixture.speciesCount,
                incompleteRecords = 1,
            ),
        )

        val descriptions = ParserOrchestrator.applySpeciesSemanticDomain(
            RomImage(fixture.bytes), fixture.layout, raw,
        ).single { it.capability == RomCapability.POKEDEX_DESCRIPTIONS }

        assertEquals(CapabilityStatus.AVAILABLE, descriptions.status)
        assertEquals(fixture.speciesCount - 1, descriptions.coveredRecords)
        assertEquals(fixture.speciesCount - 1, descriptions.expectedRecords)
        assertEquals(0, descriptions.incompleteRecords)
        assertEquals(CapabilityReviewStatus.NONE, descriptions.reviewStatus)
        assertTrue(trimReason in descriptions.reasons)
    }

    @Test
    fun authoritativeDomainClearsPhysicalOnlyBaseStatReviewWithoutReasons() {
        val fixture = duplicateCompiledDexMapFixture(firstReferences = 3, secondReferences = 2)
        val raw = listOf(
            rawCapability(RomCapability.SPECIES_NAMES, fixture.speciesCount, fixture.speciesCount),
            rawReviewCapability(RomCapability.BASE_STATS, fixture.speciesCount - 1, fixture.speciesCount),
        )

        val stats = ParserOrchestrator.applySpeciesSemanticDomain(
            RomImage(fixture.bytes), fixture.layout, raw,
        ).single { it.capability == RomCapability.BASE_STATS }

        assertEquals(CapabilityStatus.AVAILABLE, stats.status)
        assertEquals(fixture.speciesCount - 1, stats.coveredRecords)
        assertEquals(fixture.speciesCount - 1, stats.expectedRecords)
        assertEquals(CapabilityReviewStatus.NONE, stats.reviewStatus)
    }

    @Test
    fun authoritativeDomainRetainsExplicitValidatorReviewAlongsidePhysicalGap() {
        val fixture = duplicateCompiledDexMapFixture(firstReferences = 3, secondReferences = 2)
        val recoveryReason = "recovered 1 unique off-by-one description pointer within referenced text boundaries"
        val raw = listOf(
            rawCapability(RomCapability.SPECIES_NAMES, fixture.speciesCount, fixture.speciesCount),
            rawCapability(RomCapability.BASE_STATS, fixture.speciesCount - 1, fixture.speciesCount),
            CapabilityEvidence(
                capability = RomCapability.POKEDEX_DESCRIPTIONS,
                compatible = true,
                confidence = (fixture.speciesCount - 1).toDouble() / fixture.speciesCount,
                reasons = listOf(recoveryReason),
                status = CapabilityStatus.PARTIAL,
                validRecords = fixture.speciesCount - 1,
                totalRecords = fixture.speciesCount,
                reviewStatus = CapabilityReviewStatus.MANUAL_REVIEW,
                coveredRecords = fixture.speciesCount - 1,
                expectedRecords = fixture.speciesCount,
                incompleteRecords = 1,
                validatorReviewRecommended = true,
            ),
        )

        val descriptions = ParserOrchestrator.applySpeciesSemanticDomain(
            RomImage(fixture.bytes), fixture.layout, raw,
        ).single { it.capability == RomCapability.POKEDEX_DESCRIPTIONS }

        assertEquals(CapabilityStatus.AVAILABLE, descriptions.status)
        assertEquals(fixture.speciesCount - 1, descriptions.coveredRecords)
        assertEquals(fixture.speciesCount - 1, descriptions.expectedRecords)
        assertEquals(0, descriptions.incompleteRecords)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, descriptions.reviewStatus)
        assertTrue(descriptions.validatorReviewRecommended)
        assertTrue(recoveryReason in descriptions.reasons)
    }

    @Test
    fun authoritativeDomainRetainsAmbiguousOrIncompleteSemanticEvidence() {
        val completeDomain = SpeciesSemanticDomain(
            expectedSpeciesIds = setOf(1, 2),
            coveredStatRecords = 2,
            excludedStructuralRecords = 1,
            source = SpeciesSemanticDomainSource.COMPILED_SPECIES_TO_DEX_MAP,
        )
        val ambiguous = ValidationEvidence(
            compatible = true,
            validRecords = 3,
            totalRecords = 3,
            confidence = 1.0,
            reasons = emptyList(),
            ambiguous = true,
        )
        val incompleteDomain = completeDomain.copy(coveredStatRecords = 1)

        assertTrue(completeDomain.applyToStats(ambiguous).reviewRecommended)
        assertTrue(incompleteDomain.applyToStats(ambiguous.copy(ambiguous = false)).reviewRecommended)
    }

    @Test
    fun equallyReferencedCompiledDexMapCopiesDoNotBecomeAnAuthoritativeDomain() {
        val fixture = duplicateCompiledDexMapFixture(firstReferences = 2, secondReferences = 2)
        val domain = SpeciesSemanticDomainResolver.resolve(RomImage(fixture.bytes), fixture.layout)
        val raw = ValidationEvidence(
            compatible = true,
            validRecords = fixture.speciesCount - 1,
            totalRecords = fixture.speciesCount,
            confidence = (fixture.speciesCount - 1).toDouble() / fixture.speciesCount,
            reasons = emptyList(),
        )

        assertEquals(SpeciesSemanticDomainSource.NAVIGABLE_SPECIES_FALLBACK, domain.source)
        assertTrue(domain.applyToStats(raw).reviewRecommended)
        assertFalse(domain.applyToStats(raw).reasons.any { it.contains("compiled-referenced species-to-Dex map") })
    }

    @Test
    fun uniquelyStrongerCompiledDexMapCopyRemainsAuthoritative() {
        val fixture = duplicateCompiledDexMapFixture(firstReferences = 3, secondReferences = 2)
        val domain = SpeciesSemanticDomainResolver.resolve(RomImage(fixture.bytes), fixture.layout)
        val raw = ValidationEvidence(
            compatible = true,
            validRecords = fixture.speciesCount - 1,
            totalRecords = fixture.speciesCount,
            confidence = (fixture.speciesCount - 1).toDouble() / fixture.speciesCount,
            reasons = emptyList(),
        )

        assertEquals(SpeciesSemanticDomainSource.COMPILED_SPECIES_TO_DEX_MAP, domain.source)
        assertFalse(domain.applyToStats(raw).reviewRecommended)
        assertTrue(domain.applyToStats(raw).reasons.any { it.contains("compiled-referenced species-to-Dex map") })
    }

    @Test
    fun strongActiveDomainRetainsLearnsetRecoveryReview() {
        val domain = SpeciesSemanticDomain(
            expectedSpeciesIds = setOf(1, 2),
            coveredStatRecords = 2,
            excludedStructuralRecords = 8,
            source = SpeciesSemanticDomainSource.STRONGLY_REFERENCED_REGIONAL_ORDER,
        )
        val recovered = ValidationEvidence(
            compatible = true,
            validRecords = 8,
            totalRecords = 10,
            confidence = 0.8,
            reasons = listOf("recovered a malformed learnset tail"),
            reviewRecommended = true,
        )

        assertTrue(domain.applyToLearnsets(recovered, coveredSpeciesIds = setOf(1, 2)).reviewRecommended)
    }

    @Test
    fun orchestratorReportsDescriptionCoverageAcrossTheSemanticSpeciesDomain() {
        val recordSize = 28
        val speciesCount = 6
        val namesOffset = 0x100
        val statsOffset = 0x200
        val descriptionsOffset = 0x300
        val dexMapOffset = 0x500
        val textOffset = 0x600
        val bytes = ByteArray(0x700)
        putFixedGbaName(bytes, namesOffset, 0, "NONE")
        putFixedGbaName(bytes, namesOffset, 1, "ALPHA")
        bytes[namesOffset + 2 * 11] = 0xFF.toByte()
        putFixedGbaName(bytes, namesOffset, 3, "RESERVED")
        putFixedGbaName(bytes, namesOffset, 4, "STUB")
        putFixedGbaName(bytes, namesOffset, 5, "ACTIVE")
        putValidGen3Stats(bytes, statsOffset + 1 * recordSize)
        putValidGen3Stats(bytes, statsOffset + 4 * recordSize)
        putValidGen3Stats(bytes, statsOffset + 5 * recordSize)
        listOf(1, 2, 0, 3, 4).forEachIndexed { index, dex ->
            putU16(bytes, dexMapOffset + index * 2, dex)
        }
        val regionalCountOffset = 0x80
        putRegionalOrder(bytes, regionalCountOffset, listOf(1, 4, 5))
        putRepeatedThumbLiteralReferences(bytes, regionalCountOffset, regionalCountOffset + 2)
        repeat(4) { dex ->
            val base = descriptionsOffset + dex * 32
            putGbaText(bytes, base, if (dex == 1) "FIRST" else "ENTRY")
            putPointer(bytes, base + 16, textOffset)
        }
        putGbaText(bytes, textOffset, "DESCRIPTION")
        val layout = resolvedEnglishLayout(
            family = EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = null,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                baseStats = TableLayout(statsOffset, speciesCount, recordSize),
                descriptions = TableLayout(descriptionsOffset, 4, 32, pointerOffsets = listOf(16)),
            ),
        )
        val raw = listOf(
            rawCapability(RomCapability.SPECIES_NAMES, speciesCount, speciesCount),
            rawCapability(RomCapability.BASE_STATS, 3, speciesCount),
            rawCapability(RomCapability.POKEDEX_DESCRIPTIONS, 4, 4),
        )

        val descriptions = ParserOrchestrator.applySpeciesSemanticDomain(
            RomImage(bytes), layout.withTypedDescriptions(bytes), raw,
        )
            .single { it.capability == RomCapability.POKEDEX_DESCRIPTIONS }

        assertEquals(4, descriptions.validRecords)
        assertEquals(4, descriptions.totalRecords)
        assertEquals(2, descriptions.coveredRecords)
        assertEquals(3, descriptions.expectedRecords)
        assertEquals(1, descriptions.incompleteRecords)
        assertEquals(CapabilityStatus.PARTIAL, descriptions.status)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, descriptions.reviewStatus)
        assertTrue(descriptions.reasons.any { it.contains("semantic Pokédex description coverage 2/3") })
    }

    @Test
    fun orchestratorPreservesPhysicalDescriptionCoverageWhenActiveSpeciesDomainIsUnresolved() {
        val recordSize = 28
        val speciesCount = 6
        val namesOffset = 0x100
        val statsOffset = 0x200
        val descriptionsOffset = 0x300
        val dexMapOffset = 0x500
        val textOffset = 0x600
        val bytes = ByteArray(0x700)
        putFixedGbaName(bytes, namesOffset, 0, "NONE")
        putFixedGbaName(bytes, namesOffset, 1, "ALPHA")
        bytes[namesOffset + 2 * 11] = 0xFF.toByte()
        putFixedGbaName(bytes, namesOffset, 3, "RESERVED")
        putFixedGbaName(bytes, namesOffset, 4, "STUB")
        putFixedGbaName(bytes, namesOffset, 5, "ACTIVE")
        putValidGen3Stats(bytes, statsOffset + 1 * recordSize)
        putValidGen3Stats(bytes, statsOffset + 4 * recordSize)
        putValidGen3Stats(bytes, statsOffset + 5 * recordSize)
        listOf(1, 2, 0, 3, 4).forEachIndexed { index, dex ->
            putU16(bytes, dexMapOffset + index * 2, dex)
        }
        repeat(4) { dex ->
            val base = descriptionsOffset + dex * 32
            putGbaText(bytes, base, if (dex == 1) "FIRST" else "ENTRY")
            putPointer(bytes, base + 16, textOffset)
        }
        putGbaText(bytes, textOffset, "DESCRIPTION")
        val layout = resolvedEnglishLayout(
            family = EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = null,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                baseStats = TableLayout(statsOffset, speciesCount, recordSize),
                descriptions = TableLayout(descriptionsOffset, 5, 32, pointerOffsets = listOf(16)),
            ),
        )
        val rawDescriptions = CapabilityEvidence(
            capability = RomCapability.POKEDEX_DESCRIPTIONS,
            compatible = true,
            confidence = 4.0 / 5.0,
            status = CapabilityStatus.PARTIAL,
            validRecords = 4,
            totalRecords = 5,
            coveredRecords = 4,
            expectedRecords = 5,
            incompleteRecords = 1,
            reviewStatus = CapabilityReviewStatus.MANUAL_REVIEW,
        )
        val raw = listOf(
            rawCapability(RomCapability.SPECIES_NAMES, speciesCount, speciesCount),
            rawCapability(RomCapability.BASE_STATS, 3, speciesCount),
            rawDescriptions,
        )

        val descriptions = ParserOrchestrator.applySpeciesSemanticDomain(
            RomImage(bytes), layout.withTypedDescriptions(bytes), raw,
        )
            .single { it.capability == RomCapability.POKEDEX_DESCRIPTIONS }

        assertEquals(rawDescriptions, descriptions)
    }

    @Test
    fun orchestratorProjectsDescriptionsThroughACompleteCompiledSpeciesToDexMap() {
        val recordSize = 28
        val speciesCount = 6
        val namesOffset = 0x100
        val statsOffset = 0x200
        val descriptionsOffset = 0x400
        val dexMapOffset = 0x700
        val textOffset = 0x900
        val bytes = ByteArray(0x1000)
        repeat(speciesCount) { id ->
            putFixedGbaName(bytes, namesOffset, id, if (id == 0) "NONE" else "MON")
            if (id > 0) putValidGen3Stats(bytes, statsOffset + id * recordSize)
        }
        putU16(bytes, dexMapOffset, 0)
        listOf(2, 1, 4, 3, 5).forEachIndexed { index, dex ->
            putU16(bytes, dexMapOffset + 2 + index * 2, dex)
        }
        putRepeatedThumbLiteralReferences(bytes, dexMapOffset + 2, dexMapOffset + 2)
        repeat(4) { dex ->
            val base = descriptionsOffset + dex * 32
            putGbaText(bytes, base, if (dex == 0) "UNKNOWN" else "ENTRY")
            putPointer(bytes, base + 16, textOffset)
        }
        putGbaText(bytes, textOffset, "DESCRIPTION")
        val layout = resolvedEnglishLayout(
            family = EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = null,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                baseStats = TableLayout(statsOffset, speciesCount, recordSize),
                descriptions = TableLayout(descriptionsOffset, 4, 32, pointerOffsets = listOf(16)),
            ),
        )
        val raw = listOf(
            rawCapability(RomCapability.SPECIES_NAMES, speciesCount, speciesCount),
            rawCapability(RomCapability.BASE_STATS, speciesCount, speciesCount),
            rawCapability(RomCapability.POKEDEX_DESCRIPTIONS, 4, 4),
        )

        val descriptions = ParserOrchestrator.applySpeciesSemanticDomain(
            RomImage(bytes), layout.withTypedDescriptions(bytes), raw,
        )
            .single { it.capability == RomCapability.POKEDEX_DESCRIPTIONS }

        assertEquals(3, descriptions.coveredRecords)
        assertEquals(5, descriptions.expectedRecords)
        assertEquals(2, descriptions.incompleteRecords)
        assertEquals(CapabilityStatus.PARTIAL, descriptions.status)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, descriptions.reviewStatus)
        assertTrue(descriptions.reasons.any { it.contains("compiled-referenced species-to-Dex map") })
    }

    @Test
    fun compiledDexMapExcludesAReservedOverflowBlockBeforeThePokedexOrderResumes() {
        val recordSize = 28
        val speciesCount = 10
        val namesOffset = 0x100
        val statsOffset = 0x200
        val descriptionsOffset = 0x400
        val dexMapOffset = 0x700
        val textOffset = 0x900
        val bytes = ByteArray(0x1000)
        repeat(speciesCount) { id ->
            putFixedGbaName(bytes, namesOffset, id, if (id == 0) "NONE" else "MON")
            if (id > 0) putValidGen3Stats(bytes, statsOffset + id * recordSize)
        }
        // IDs 1..3 are the identity prefix. IDs 4..6 occupy a reserved Dex tail,
        // then ID 7 resumes the displaced active order at Dex 4.
        listOf(1, 2, 3, 7, 8, 9, 4, 5, 6).forEachIndexed { index, dex ->
            putU16(bytes, dexMapOffset + index * 2, dex)
        }
        putRepeatedThumbLiteralReferences(bytes, dexMapOffset, dexMapOffset)
        repeat(7) { dex ->
            val base = descriptionsOffset + dex * 32
            putGbaText(bytes, base, if (dex == 0) "UNKNOWN" else "ENTRY")
            putPointer(bytes, base + 16, textOffset)
        }
        putGbaText(bytes, textOffset, "DESCRIPTION")
        val layout = resolvedEnglishLayout(
            family = EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = null,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                baseStats = TableLayout(statsOffset, speciesCount, recordSize),
                descriptions = TableLayout(descriptionsOffset, 7, 32, pointerOffsets = listOf(16)),
            ),
        )
        val raw = listOf(
            rawCapability(RomCapability.SPECIES_NAMES, speciesCount, speciesCount),
            rawCapability(RomCapability.BASE_STATS, speciesCount, speciesCount),
            rawCapability(RomCapability.POKEDEX_DESCRIPTIONS, 7, 7),
        )

        val descriptions = ParserOrchestrator.applySpeciesSemanticDomain(
            RomImage(bytes), layout.withTypedDescriptions(bytes), raw,
        )
            .single { it.capability == RomCapability.POKEDEX_DESCRIPTIONS }

        assertEquals(6, descriptions.coveredRecords)
        assertEquals(6, descriptions.expectedRecords)
        assertEquals(0, descriptions.incompleteRecords)
        assertEquals(CapabilityStatus.AVAILABLE, descriptions.status)
        assertEquals(CapabilityReviewStatus.NONE, descriptions.reviewStatus)
        assertTrue(descriptions.reasons.any { it.contains("reserved Dex overflow block") })
    }

    @Test
    fun singleResumedDexValueDoesNotProveAReservedOverflowBlock() {
        val descriptions = compiledOverflowDescriptions(
            dexValues = listOf(1, 2, 3, 7, 8, 9, 4, 42, 43),
            descriptionCount = 7,
        )

        assertEquals(4, descriptions.coveredRecords)
        assertEquals(9, descriptions.expectedRecords)
        assertEquals(5, descriptions.incompleteRecords)
        assertEquals(CapabilityStatus.PARTIAL, descriptions.status)
        assertFalse(descriptions.reasons.any { it.contains("reserved Dex overflow block") })
    }

    @Test
    fun oneEntryOverflowAfterAShortIdentityPrefixRemainsExpected() {
        val descriptions = compiledOverflowDescriptions(
            dexValues = listOf(1, 2, 4, 3),
            descriptionCount = 4,
        )

        assertEquals(3, descriptions.coveredRecords)
        assertEquals(4, descriptions.expectedRecords)
        assertEquals(1, descriptions.incompleteRecords)
        assertEquals(CapabilityStatus.PARTIAL, descriptions.status)
        assertFalse(descriptions.reasons.any { it.contains("reserved Dex overflow block") })
    }

    @Test
    fun trailingExpandedSpeciesRemainExpectedAfterAReservedOverflowBlock() {
        val descriptions = compiledOverflowDescriptions(
            dexValues = listOf(1, 2, 3, 7, 8, 9, 4, 5, 6, 10, 11),
            descriptionCount = 7,
        )

        assertEquals(6, descriptions.coveredRecords)
        assertEquals(8, descriptions.expectedRecords)
        assertEquals(2, descriptions.incompleteRecords)
        assertEquals(CapabilityStatus.PARTIAL, descriptions.status)
        assertTrue(descriptions.reasons.any { it.contains("reserved Dex overflow block") })
    }

    @Test
    fun descriptionBoundaryOverlappingTheIdentityPrefixDoesNotProveReservedOverflow() {
        val reservedIds = classifiedReservedOverflowIds(
            values = (1..40).toList() + listOf(38, 39, 40, 41, 42, 43),
            descriptionCount = 38,
        )

        assertTrue(reservedIds.isEmpty())
    }

    @Test
    fun repeatedJoinCannotMakeAnOverlappingDescriptionBoundaryLookCanonical() {
        val reservedIds = classifiedReservedOverflowIds(
            values = (1..40).toList() + listOf(38, 39, 40, 41, 41, 42, 43),
            descriptionCount = 38,
        )

        assertTrue(reservedIds.isEmpty())
    }

    @Test
    fun twoEntryIdentityPrefixDoesNotProveReservedOverflow() {
        val reservedIds = classifiedReservedOverflowIds(
            values = listOf(1, 2, 7, 8, 9, 3, 4, 5),
            descriptionCount = 7,
        )

        assertTrue(reservedIds.isEmpty())
    }

    @Test
    fun twoEntryOverflowRunDoesNotProveReservedOverflow() {
        val reservedIds = classifiedReservedOverflowIds(
            values = listOf(1, 2, 3, 7, 8, 4, 5, 6),
            descriptionCount = 7,
        )

        assertTrue(reservedIds.isEmpty())
    }

    @Test
    fun twoEntryResumedRunDoesNotProveReservedOverflow() {
        val reservedIds = classifiedReservedOverflowIds(
            values = listOf(1, 2, 3, 7, 8, 9, 4, 5, 42),
            descriptionCount = 7,
        )

        assertTrue(reservedIds.isEmpty())
    }

    @Test
    fun publishedExpansionDomainKeepsItsProvenanceWhenACompiledDexMapIsPresent() {
        val fixture = expansionSemanticFixtureWithCompiledDexMap()

        val domain = SpeciesSemanticDomainResolver.resolve(RomImage(fixture.bytes), fixture.layout)
        val reasons = domain.applyToNames(rawEvidence(fixture.speciesCount)).reasons

        assertEquals((1 until fixture.speciesCount).toSet(), domain.expectedSpeciesIds)
        assertEquals(SpeciesSemanticDomainSource.PUBLISHED_EXPANSION_SPECIES_TABLE, domain.source)
        assertTrue(reasons.any { it.contains("published pokeemerald-expansion gSpeciesInfo table") })
        assertFalse(reasons.any { it.contains("compiled-referenced species-to-Dex map") })
        assertFalse(reasons.any { it.contains("reserved Dex overflow block") })
    }

    @Test
    fun rawReservedSlotDoesNotMakeACompleteFeaturePartial() {
        val capability = capabilityEvidence(
            RomCapability.BASE_STATS,
            ValidationEvidence(
                compatible = true,
                validRecords = 411,
                totalRecords = 412,
                confidence = 411.0 / 412.0,
                reasons = emptyList(),
                coveredRecords = 411,
                expectedRecords = 411,
            ),
        )

        assertEquals(CapabilityStatus.AVAILABLE, capability.status)
        assertEquals(411, capability.validRecords)
        assertEquals(412, capability.totalRecords)
        assertEquals(411, capability.coveredRecords)
        assertEquals(411, capability.expectedRecords)
        assertEquals(CapabilityReviewStatus.NONE, capability.reviewStatus)
    }

    @Test
    fun credibleIncompleteSemanticCoverageRemainsPartialAndReviewable() {
        val capability = capabilityEvidence(
            RomCapability.LEARNSETS,
            ValidationEvidence(
                compatible = true,
                validRecords = 7,
                totalRecords = 10,
                confidence = 0.70,
                reasons = emptyList(),
                coveredRecords = 7,
                expectedRecords = 10,
            ),
        )

        assertEquals(CapabilityStatus.PARTIAL, capability.status)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, capability.reviewStatus)
    }

    @Test
    fun speciesCatalogUsesTheSharedSemanticDomainInsteadOfRawInternalSlots() {
        val domainReason = "excluded reserved structural slots"
        val names = ValidationEvidence(
            compatible = true,
            validRecords = 412,
            totalRecords = 412,
            confidence = 1.0,
            reasons = listOf(domainReason),
        )
        val stats = ValidationEvidence(
            compatible = true,
            validRecords = 411,
            totalRecords = 412,
            confidence = 411.0 / 412.0,
            reasons = listOf(domainReason),
            coveredRecords = 411,
            expectedRecords = 411,
        )

        val catalog = capabilityEvidence(
            RomCapability.SPECIES_CATALOG,
            speciesCatalogEvidence(names, stats),
        )

        assertEquals(CapabilityStatus.AVAILABLE, catalog.status)
        assertEquals(411, catalog.validRecords)
        assertEquals(412, catalog.totalRecords)
        assertEquals(411, catalog.coveredRecords)
        assertEquals(411, catalog.expectedRecords)
        assertEquals(listOf(domainReason), catalog.reasons)
    }

    @Test
    fun gen3BaseStatsExcludeTheReservedZeroIndexFromSemanticCoverage() {
        val count = 412
        val recordSize = 28
        val bytes = ByteArray(count * recordSize)
        repeat(count - 1) { offsetIndex ->
            val index = offsetIndex + 1
            val base = index * recordSize
            repeat(6) { stat -> bytes[base + stat] = (40 + stat).toByte() }
            bytes[base + 6] = 1
            bytes[base + 7] = 2
        }

        val evidence = TableValidators.baseStats(RomImage(bytes), 0, count, recordSize, generation = 3)

        assertEquals(411, evidence.validRecords)
        assertEquals(412, evidence.totalRecords)
        assertEquals(411, evidence.coveredRecords)
        assertEquals(411, evidence.expectedRecords)
    }

    @Test
    fun derivedGen3DescriptionTableTrimsAdjacentNonTableSuffix() {
        val recordSize = 36
        val validRecords = 36
        val inheritedCount = 40
        val tableOffset = 0x100
        val textOffset = tableOffset + inheritedCount * recordSize + 0x100
        val bytes = ByteArray(textOffset + 0x100)
        repeat(validRecords) { index ->
            val base = tableOffset + index * recordSize
            putGbaText(
                bytes,
                base,
                when (index) {
                    0 -> "UNKNOWN"
                    1 -> "SEED"
                    else -> "ENTRY"
                },
            )
            putU16(bytes, base + 12, index)
            putU16(bytes, base + 14, index)
            putPointer(bytes, base + 16, textOffset)
            putPointer(bytes, base + 20, textOffset)
        }
        putGbaText(bytes, textOffset, "DESCRIPTION")

        val evidence = DatasetResolvers.gen3Descriptions(
            rom = RomImage(bytes),
            speciesCount = inheritedCount,
            inherited = TableLayout(
                offset = tableOffset - 0x40,
                count = inheritedCount,
                recordSize = recordSize,
                pointerOffsets = listOf(16, 20),
            ),
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertTrue(evidence.compatible)
        assertEquals(validRecords, evidence.validRecords)
        assertEquals(validRecords, evidence.totalRecords)
        assertEquals(validRecords, evidence.coveredRecords)
        assertEquals(validRecords, evidence.expectedRecords)
        assertFalse(evidence.reviewRecommended)
    }

    private fun putPointer(bytes: ByteArray, offset: Int, target: Int) {
        val value = 0x08000000L + target
        repeat(4) { index -> bytes[offset + index] = ((value ushr (index * 8)) and 0xFF).toByte() }
    }

    private fun compiledOverflowDescriptions(
        dexValues: List<Int>,
        descriptionCount: Int,
    ): CapabilityEvidence {
        val recordSize = 28
        val speciesCount = dexValues.size + 1
        val namesOffset = 0x100
        val statsOffset = (namesOffset + speciesCount * 11 + 0x103) and -4
        val descriptionsOffset = (statsOffset + speciesCount * recordSize + 0x103) and -4
        val dexMapOffset = (descriptionsOffset + descriptionCount * 32 + 0x103) and -4
        val textOffset = (dexMapOffset + dexValues.size * 2 + 0x103) and -4
        val bytes = ByteArray(textOffset + 0x100)
        repeat(speciesCount) { id ->
            putFixedGbaName(bytes, namesOffset, id, if (id == 0) "NONE" else "MON")
            if (id > 0) putValidGen3Stats(bytes, statsOffset + id * recordSize)
        }
        dexValues.forEachIndexed { index, dex -> putU16(bytes, dexMapOffset + index * 2, dex) }
        putRepeatedThumbLiteralReferences(bytes, dexMapOffset, dexMapOffset)
        repeat(descriptionCount) { dex ->
            val base = descriptionsOffset + dex * 32
            putGbaText(bytes, base, if (dex == 0) "UNKNOWN" else "ENTRY")
            putPointer(bytes, base + 16, textOffset)
        }
        putGbaText(bytes, textOffset, "DESCRIPTION")
        val layout = resolvedEnglishLayout(
            family = EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = null,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                baseStats = TableLayout(statsOffset, speciesCount, recordSize),
                descriptions = TableLayout(
                    descriptionsOffset,
                    descriptionCount,
                    32,
                    pointerOffsets = listOf(16),
                ),
            ),
        )
        val raw = listOf(
            rawCapability(RomCapability.SPECIES_NAMES, speciesCount, speciesCount),
            rawCapability(RomCapability.BASE_STATS, speciesCount, speciesCount),
            rawCapability(RomCapability.POKEDEX_DESCRIPTIONS, descriptionCount, descriptionCount),
        )
        return ParserOrchestrator.applySpeciesSemanticDomain(
            RomImage(bytes), layout.withTypedDescriptions(bytes), raw,
        )
            .single { it.capability == RomCapability.POKEDEX_DESCRIPTIONS }
    }

    @Suppress("UNCHECKED_CAST")
    private fun classifiedReservedOverflowIds(values: List<Int>, descriptionCount: Int): Set<Int> {
        val method = SpeciesSemanticDomainResolver::class.java.declaredMethods.single {
            it.name == "reservedDexOverflowSpeciesIds"
        }
        method.isAccessible = true
        return method.invoke(SpeciesSemanticDomainResolver, values, descriptionCount) as Set<Int>
    }

    private fun expansionSemanticFixtureWithCompiledDexMap(): SemanticDomainFixture {
        val stride = 180
        val dexValues = listOf(1, 2, 3, 7, 8, 9, 4, 5, 6)
        val speciesCount = dexValues.size + 1
        val dexMapOffset = speciesCount * stride + 0x100
        val bytes = ByteArray(dexMapOffset + 0x200)
        dexValues.forEachIndexed { index, dex ->
            val id = index + 1
            val base = id * stride
            repeat(6) { stat -> bytes[base + stat] = (40 + stat).toByte() }
            bytes[base + 6] = 1
            bytes[base + 7] = 2
            putGbaText(bytes, base + 31, "SPECIES")
            putGbaText(bytes, base + 44, "ACTIVE")
            putU16(bytes, base + 60, dex)
            putU16(bytes, base + 62, 10)
            putU16(bytes, base + 64, 100)
            putU16(bytes, dexMapOffset + index * 2, dex)
        }
        putRepeatedThumbLiteralReferences(bytes, dexMapOffset, dexMapOffset)
        val metadata = PokeemeraldExpansionMetadata(
            0x204, 1, 15, 3, stride, 44, 13, 31, 60, 62, 64, 76, 88, 96,
            24, 21, 148, 152, 156, 160, 64, 28, 20, 20,
        )
        val layout = resolvedEnglishLayout(
            family = EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(44, speciesCount, 13, stride = stride),
                baseStats = TableLayout(0, speciesCount, stride, stride = stride),
                descriptions = TableLayout(0, 7, stride, stride = stride, pointerOffsets = listOf(76)),
            ),
            pokeemeraldExpansion = metadata,
        )
        return SemanticDomainFixture(bytes, layout, speciesCount)
    }

    private fun ResolvedRomLayout.withTypedDescriptions(bytes: ByteArray): ResolvedRomLayout {
        val selected = requireNotNull(tables.descriptions)
        val typedTable = DescriptionTableLayout(
            offset = selected.offset.toLong(),
            count = selected.count.toLong(),
            recordSize = selected.recordSize,
            pointerOffsets = selected.pointerOffsets.ifEmpty {
                if (selected.recordSize >= 36) listOf(16, 20) else listOf(16)
            },
        )
        val rom = RomImage(bytes)
        val decoded = DescriptionCodec().decode(
            RomAnalysisSession(rom, RomHeaderReader.read(rom)),
            typedTable,
        ) as DescriptionTableOutcome.Decoded
        val resolved = ResolvedDescriptionLayout(
            typedTable,
            decoded.rows,
        )
        return copy(
            resolvedDatasets = ResolvedDatasetLayouts(
                typeChart = resolvedDatasets.typeChart,
                descriptions = resolved,
            ),
        )
    }

    private fun duplicateCompiledDexMapFixture(
        firstReferences: Int,
        secondReferences: Int,
    ): SemanticDomainFixture {
        require(firstReferences >= 1)
        require(secondReferences >= 0)
        val speciesCount = 6
        val namesOffset = 0x200
        val statsOffset = 0x300
        val descriptionsOffset = 0x400
        val descriptionTextOffset = 0x600
        val firstMapOffset = 0x700
        val secondMapOffset = 0x720
        val values = listOf(2, 1, 4, 3, 5)
        val bytes = ByteArray(0x800)
        repeat(speciesCount) { id ->
            putFixedGbaName(bytes, namesOffset, id, if (id == 0) "NONE" else "MON")
            if (id > 0) putValidGen3Stats(bytes, statsOffset + id * 28)
            val descriptionBase = descriptionsOffset + id * 32
            putGbaText(bytes, descriptionBase, if (id == 0) "UNKNOWN" else "ENTRY")
            putPointer(bytes, descriptionBase + 16, descriptionTextOffset)
        }
        putGbaText(bytes, descriptionTextOffset, "DESCRIPTION")
        values.forEachIndexed { index, dex ->
            putU16(bytes, firstMapOffset + index * 2, dex)
            putU16(bytes, secondMapOffset + index * 2, dex)
        }

        putThumbLiteralReference(bytes, 0x00, 0x100, firstMapOffset)
        putU16(bytes, 0x02, 0x3901)
        putU16(bytes, 0x04, 0x0049)
        putU16(bytes, 0x06, 0x1809)
        putU16(bytes, 0x08, 0x8808)
        repeat(firstReferences - 1) { index ->
            putThumbLiteralReference(bytes, 0x20 + index * 2, 0x110 + index * 4, firstMapOffset)
        }
        repeat(secondReferences) { index ->
            putThumbLiteralReference(bytes, 0x40 + index * 2, 0x130 + index * 4, secondMapOffset)
        }

        val layout = resolvedEnglishLayout(
            family = EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = null,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                baseStats = TableLayout(statsOffset, speciesCount, 28),
                descriptions = TableLayout(descriptionsOffset, speciesCount, 32, pointerOffsets = listOf(16)),
            ),
        )
        return SemanticDomainFixture(bytes, layout.withTypedDescriptions(bytes), speciesCount)
    }

    private fun putRepeatedThumbLiteralReferences(
        bytes: ByteArray,
        firstTarget: Int,
        secondTarget: Int,
        instructionBase: Int = 0,
        literalBase: Int = 0x40,
    ) {
        listOf(firstTarget, secondTarget, firstTarget, secondTarget, firstTarget, secondTarget).forEachIndexed {
                index,
                target,
            ->
            putThumbLiteralReference(
                bytes = bytes,
                instructionOffset = instructionBase + index * 2,
                literalOffset = literalBase + index * 4,
                target = target,
            )
        }
    }

    private fun semanticDomainFixture(
        order: List<Int>,
        referenceBase: Int?,
    ): SemanticDomainFixture {
        val speciesCount = 8
        val namesOffset = 0x200
        val statsOffset = 0x300
        val countOffset = 0x100
        val bytes = ByteArray(0x500)
        repeat(speciesCount) { id ->
            putFixedGbaName(bytes, namesOffset, id, if (id == 0) "NONE" else "MON")
            if (id > 0) putValidGen3Stats(bytes, statsOffset + id * 28)
        }
        (1 until speciesCount).forEach { speciesId ->
            putU16(bytes, 0x180 + (speciesId - 1) * 2, speciesId)
        }
        putRegionalOrder(bytes, countOffset, order)
        referenceBase?.let {
            putRepeatedThumbLiteralReferences(
                bytes,
                countOffset,
                countOffset + 2,
                instructionBase = it,
                literalBase = 0x40,
            )
        }
        val layout = resolvedEnglishLayout(
            family = EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = null,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                baseStats = TableLayout(statsOffset, speciesCount, 28),
            ),
        )
        return SemanticDomainFixture(bytes, layout, speciesCount)
    }

    private fun putRegionalOrder(bytes: ByteArray, countOffset: Int, order: List<Int>) {
        putU16(bytes, countOffset, order.size)
        order.forEachIndexed { index, speciesId -> putU16(bytes, countOffset + 2 + index * 2, speciesId) }
    }

    private fun putThumbLiteralReference(
        bytes: ByteArray,
        instructionOffset: Int,
        literalOffset: Int,
        target: Int,
    ) {
        val pc = (instructionOffset + 4) and -4
        putU16(bytes, instructionOffset, 0x4800 or ((literalOffset - pc) / 4))
        putPointer(bytes, literalOffset, target)
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun putGbaText(bytes: ByteArray, offset: Int, text: String) {
        text.forEachIndexed { index, character ->
            bytes[offset + index] = when (character) {
                in 'A'..'Z' -> (0xBB + character.code - 'A'.code).toByte()
                ' ' -> 0
                else -> error("unsupported test character $character")
            }
        }
        bytes[offset + text.length] = 0xFF.toByte()
    }

    private fun putFixedGbaName(bytes: ByteArray, tableOffset: Int, index: Int, text: String) {
        putGbaText(bytes, tableOffset + index * 11, text)
    }

    private fun putValidGen3Stats(bytes: ByteArray, offset: Int) {
        repeat(6) { stat -> bytes[offset + stat] = (40 + stat).toByte() }
        bytes[offset + 6] = 1
        bytes[offset + 7] = 2
    }

    private fun rawCapability(capability: RomCapability, valid: Int, total: Int) = CapabilityEvidence(
        capability = capability,
        compatible = true,
        confidence = valid.toDouble() / total,
        status = CapabilityStatus.AVAILABLE,
        validRecords = valid,
        totalRecords = total,
    )

    private fun rawReviewCapability(capability: RomCapability, valid: Int, total: Int) = CapabilityEvidence(
        capability = capability,
        compatible = true,
        confidence = valid.toDouble() / total,
        status = CapabilityStatus.PARTIAL,
        validRecords = valid,
        totalRecords = total,
        reviewStatus = CapabilityReviewStatus.MANUAL_REVIEW,
    )

    private fun expansionSemanticFixture(
        includeSecondMedia: Boolean,
        blankSecondName: Boolean = false,
    ): SemanticDomainFixture {
        val stride = 180
        val bytes = ByteArray(0x500)
        repeat(2) { offsetIndex ->
            val id = offsetIndex + 1
            val base = id * stride
            repeat(6) { stat -> bytes[base + stat] = (40 + stat).toByte() }
            bytes[base + 6] = 1
            bytes[base + 7] = 2
            putGbaText(bytes, base + 31, "SPECIES")
            if (id != 2 || !blankSecondName) {
                putGbaText(bytes, base + 44, if (id == 1) "ALPHA" else "BETA")
            } else {
                bytes[base + 44] = 0xFF.toByte()
            }
            putU16(bytes, base + 60, id)
            putU16(bytes, base + 62, 10)
            putU16(bytes, base + 64, 100)
            if (id == 1 || includeSecondMedia) {
                putPointer(bytes, base + 76, 0x3A0 + id * 16)
                putPointer(bytes, base + 88, 0x400)
                putPointer(bytes, base + 96, 0x440)
                putGbaText(bytes, 0x3A0 + id * 16, "DESCRIPTION")
            }
            putPointer(bytes, base + 148, 0x460 + id * 8)
            putU16(bytes, 0x460 + id * 8, 1)
            putU16(bytes, 0x462 + id * 8, 5)
            putU16(bytes, 0x464 + id * 8, 0xFFFF)
        }
        Base64.getDecoder().decode("ASAIAAAAKAABAAAAAAH+BwEAAAA=").copyInto(bytes, 0x400)
        bytes[0x440 + 2] = 0x1F
        val metadata = PokeemeraldExpansionMetadata(
            0x204, 1, 15, 3, stride, 44, 13, 31, 60, 62, 64, 76, 88, 96,
            24, 21, 148, 152, 156, 160, 64, 28, 20, 20,
        )
        val layout = resolvedEnglishLayout(
            family = EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = 3,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(44, 3, 13, stride = stride),
                baseStats = TableLayout(0, 3, stride, stride = stride),
                descriptions = TableLayout(0, 3, stride, stride = stride, pointerOffsets = listOf(76)),
                sprites = TableLayout(88, 3, 4, stride = stride, pointerOffsets = listOf(8)),
                learnsets = TableLayout(148, 3, 4, stride = stride, valuesArePointers = true, elementSize = 4),
            ),
            pokeemeraldExpansion = metadata,
        )
        return SemanticDomainFixture(bytes, layout, 3)
    }

    private fun rawEvidence(total: Int) = ValidationEvidence(
        compatible = true,
        validRecords = total,
        totalRecords = total,
        confidence = 1.0,
        reasons = emptyList(),
    )

    private data class SemanticDomainFixture(
        val bytes: ByteArray,
        val layout: ResolvedRomLayout,
        val speciesCount: Int,
    )
}
