package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.dataset.natures.NatureRecord
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.LocalizedTableLayout
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.language.RomLanguageProjection
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogLanguageOverlayTest {
    @Test
    fun snapshotsLocalizedTextAndNeverFallsBackAcrossLanguages() {
        val englishNames = linkedMapOf(1 to CatalogField.available("Bulbasaur"))
        val english = overlay(LanguageTag.ENGLISH, 11, englishNames)
        val french = overlay(
            LanguageTag.FRENCH,
            12,
            linkedMapOf(1 to CatalogField.available("Bulbizarre")),
        )
        val overlays = linkedMapOf(LanguageTag.ENGLISH to english, LanguageTag.FRENCH to french)
        val catalog = catalog(resolvedManifest(), overlays)

        englishNames[1] = CatalogField.available("mutated")
        englishNames[2] = CatalogField.available("Ivysaur")
        overlays.clear()

        assertEquals("Bulbasaur", catalog.localizedText(LanguageTag.ENGLISH)?.speciesNames?.get(1)?.value)
        assertNull(catalog.localizedText(LanguageTag.ENGLISH)?.speciesNames?.get(2))
        assertEquals("Bulbizarre", catalog.localizedText(LanguageTag.FRENCH)?.speciesNames?.get(1)?.value)
        assertNull(catalog.localizedText(LanguageTag.GERMAN))
        assertEquals(LanguageTag.ENGLISH, catalog.defaultLocalizedText()?.language)
    }

    @Test
    fun requiresExactResolvedProjectionCoverageAndTheDefaultOverlay() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            CatalogLocalization(
                manifest = resolvedManifest(),
                overlays = mapOf(LanguageTag.FRENCH to overlay(LanguageTag.FRENCH, 12)),
            )
        }

        assertEquals("catalog overlays must exactly cover resolved language projections", failure.message)
    }

    @Test
    fun ambiguousManifestKeepsIndividuallyResolvedProjectionOverlays() {
        val manifest = RomLanguageManifest(
            defaultLanguage = null,
            projections = listOf(
                projection(LanguageTag.ENGLISH, "gb-english", LanguageResolutionStatus.RESOLVED),
                projection(LanguageTag.FRENCH, "gb-french", LanguageResolutionStatus.AMBIGUOUS),
            ),
            status = LanguageResolutionStatus.AMBIGUOUS,
        )
        val localization = CatalogLocalization(
            manifest = manifest,
            overlays = mapOf(LanguageTag.ENGLISH to overlay(LanguageTag.ENGLISH, 1)),
        )

        assertEquals(setOf(LanguageTag.ENGLISH), localization.overlays.keys)
        assertNull(localization.defaultOverlay())
    }

    @Test
    fun rejectsUnknownManifestWithLocalizedOverlays() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            CatalogLocalization(
                manifest = RomLanguageManifest.UNKNOWN,
                overlays = mapOf(LanguageTag.ENGLISH to overlay(LanguageTag.ENGLISH, 1)),
            )
        }

        assertEquals("catalog overlays must exactly cover resolved language projections", failure.message)
    }

    @Test
    fun rejectsMismatchedKeysBlankTextAndNonPositiveVersions() {
        assertThrows(IllegalArgumentException::class.java) {
            CatalogLocalization(
                manifest = resolvedManifest(),
                overlays = mapOf(
                    LanguageTag.ENGLISH to overlay(LanguageTag.FRENCH, 1),
                    LanguageTag.FRENCH to overlay(LanguageTag.FRENCH, 2),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            overlay(LanguageTag.ENGLISH, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            overlay(
                LanguageTag.ENGLISH,
                1,
                mapOf(1 to CatalogField.available(" ")),
            )
        }
    }

    @Test
    fun snapshotsIndependentCapabilityStateForEveryLocalizedDomain() {
        val reasons = mutableListOf("fixture")
        val capabilityState = LocalizedCapabilityState(
            status = CapabilityStatus.PARTIAL,
            confidence = 0.75,
            coveredRecords = 1,
            expectedRecords = 2,
            reasons = reasons,
        )
        val capabilities = allCapabilityStates().toMutableMap().apply {
            put(LocalizedTextCapability.SPECIES_NAMES, capabilityState)
            put(
                LocalizedTextCapability.MOVE_NAMES,
                LocalizedCapabilityState.notFound("fixture missing"),
            )
            put(
                LocalizedTextCapability.AREA_NAMES,
                LocalizedCapabilityState.notApplicable("no map table"),
            )
            put(
                LocalizedTextCapability.POI_TEXT,
                LocalizedCapabilityState.ambiguous("fixture ambiguity"),
            )
        }
        val english = CatalogLanguageOverlay(
            language = LanguageTag.ENGLISH,
            overlayVersion = 1,
            localizedCapabilities = capabilities,
            speciesNames = mapOf(1 to CatalogField.available("Bulbasaur")),
        )

        reasons += "mutated"
        capabilities.clear()

        assertEquals(capabilityState, english.localizedCapabilities[LocalizedTextCapability.SPECIES_NAMES])
        assertEquals(listOf("fixture"), english.localizedCapabilities.getValue(LocalizedTextCapability.SPECIES_NAMES).reasons)
        assertEquals(
            CapabilityStatus.NOT_FOUND,
            english.localizedCapabilities.getValue(LocalizedTextCapability.MOVE_NAMES).status,
        )
        assertEquals(
            CapabilityStatus.NOT_APPLICABLE,
            english.localizedCapabilities.getValue(LocalizedTextCapability.AREA_NAMES).status,
        )
        assertEquals(
            CapabilityStatus.AMBIGUOUS,
            english.localizedCapabilities.getValue(LocalizedTextCapability.POI_TEXT).status,
        )
    }

    @Test
    fun extractsDefaultTextAndLeavesOneLanguageNeutralSharedGraph() {
        val evidence = mapOf(
            RomCapability.SPECIES_NAMES to evidence(RomCapability.SPECIES_NAMES),
            RomCapability.POKEDEX_DESCRIPTIONS to evidence(RomCapability.POKEDEX_DESCRIPTIONS),
            RomCapability.MOVE_CATALOG to evidence(RomCapability.MOVE_CATALOG),
            RomCapability.MOVE_DESCRIPTIONS to evidence(RomCapability.MOVE_DESCRIPTIONS),
            RomCapability.ABILITIES to evidence(RomCapability.ABILITIES),
            RomCapability.ABILITY_DESCRIPTIONS to evidence(RomCapability.ABILITY_DESCRIPTIONS),
            RomCapability.NATURES to evidence(RomCapability.NATURES),
            RomCapability.BASE_STATS to evidence(RomCapability.BASE_STATS),
        )
        val extraction = CatalogLocalizedTextExtractor.extract(
            manifest = englishManifest(),
            speciesById = mapOf(
                1 to species(
                    name = CatalogField.available("Bulbasaur"),
                    description = CatalogField.available("A strange seed was planted on its back."),
                ),
            ),
            movesById = mapOf(
                1 to MoveRecord(
                    id = 1,
                    name = CatalogField.available("Pound"),
                    typeId = CatalogField.available(0),
                    category = CatalogField.available(MoveCategory.PHYSICAL),
                    power = CatalogField.available(40),
                    accuracy = CatalogField.available(100),
                    pp = CatalogField.available(35),
                    effectText = CatalogField.available("Pounds with forelegs or tail."),
                ),
            ),
            typesById = mapOf(0 to TypeRecord(0, CatalogField.available("Normal"))),
            abilitiesById = mapOf(
                1 to AbilityRecord(
                    id = 1,
                    name = CatalogField.available("Stench"),
                    description = CatalogField.available("Helps repel wild Pokémon."),
                ),
            ),
            naturesById = mapOf(
                0 to NatureRecord(
                    id = 0,
                    name = "Hardy",
                    statModifiers = listOf(0, 0, 0, 0, 0),
                    positivePercent = 110,
                    negativePercent = 90,
                ),
            ),
            capabilities = evidence,
        )
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            romCrc32 = "1234ABCD",
            family = EngineFamily.RED_BLUE,
            platform = Platform.GB,
            speciesById = extraction.speciesById,
            movesById = extraction.movesById,
            typesById = extraction.typesById,
            abilitiesById = extraction.abilitiesById,
            naturesById = extraction.naturesById,
            capabilities = extraction.capabilities,
            localization = extraction.localization,
        )
        val overlay = catalog.defaultLocalizedText()!!

        assertNull(catalog.speciesById.getValue(1).name.value)
        assertNull(catalog.speciesById.getValue(1).description.value)
        assertNull(catalog.movesById.getValue(1).name.value)
        assertNull(catalog.movesById.getValue(1).effectText.value)
        assertNull(catalog.typesById.getValue(0).name.value)
        assertNull(catalog.abilitiesById.getValue(1).name.value)
        assertNull(catalog.abilitiesById.getValue(1).description.value)
        assertNull(catalog.naturesById.getValue(0).name)
        assertEquals("Bulbasaur", overlay.speciesNames.getValue(1).value)
        assertEquals("A strange seed was planted on its back.", overlay.speciesDescriptions.getValue(1).value)
        assertEquals("Pound", overlay.moveNames.getValue(1).value)
        assertEquals("Pounds with forelegs or tail.", overlay.moveDescriptions.getValue(1).value)
        assertEquals("Normal", overlay.typeNames.getValue(0).value)
        assertEquals("Stench", overlay.abilityNames.getValue(1).value)
        assertEquals("Helps repel wild Pokémon.", overlay.abilityDescriptions.getValue(1).value)
        assertEquals("Hardy", overlay.natureNames.getValue(0).value)
        assertNull(catalog.capabilities[RomCapability.SPECIES_NAMES])
        assertNull(catalog.capabilities[RomCapability.POKEDEX_DESCRIPTIONS])
        assertNull(catalog.capabilities[RomCapability.MOVE_DESCRIPTIONS])
        assertNull(catalog.capabilities[RomCapability.ABILITY_DESCRIPTIONS])
        assertEquals(evidence.getValue(RomCapability.BASE_STATS), catalog.capabilities[RomCapability.BASE_STATS])
    }

    @Test
    fun descriptionDerivedAbilityBehaviorDoesNotRemainInSharedMechanics() {
        fun ability(id: Int, description: String, mechanics: List<AbilityMechanic>) = AbilityRecord(
            id = id,
            name = CatalogField.available("Ability $id"),
            description = CatalogField.available(description),
            mechanics = CatalogField.available(mechanics),
        )
        fun abilityEvidence(capability: RomCapability) = CapabilityEvidence(
            capability = capability,
            compatible = true,
            confidence = 1.0,
            count = 2,
            coveredRecords = 2,
            expectedRecords = 2,
            status = CapabilityStatus.AVAILABLE,
        )
        val duplicated = AbilityMechanic(AbilityMechanicKind.BEHAVIOR, "Effect", "ROM description", 1, 1)
        val numeric = AbilityMechanic(AbilityMechanicKind.MULTIPLIER, "Attack", "Attack ×1.5", 3, 2)
        val extraction = CatalogLocalizedTextExtractor.extract(
            manifest = englishManifest(),
            speciesById = emptyMap(),
            movesById = emptyMap(),
            abilitiesById = mapOf(
                1 to ability(1, "ROM description", listOf(duplicated)),
                2 to ability(2, "Another description", listOf(numeric)),
            ),
            naturesById = emptyMap(),
            capabilities = mapOf(
                RomCapability.ABILITIES to abilityEvidence(RomCapability.ABILITIES),
                RomCapability.ABILITY_DESCRIPTIONS to abilityEvidence(RomCapability.ABILITY_DESCRIPTIONS),
                RomCapability.ABILITY_MECHANICS to abilityEvidence(RomCapability.ABILITY_MECHANICS),
            ),
        )

        assertNull(extraction.abilitiesById.getValue(1).mechanics.value)
        assertEquals(listOf(numeric), extraction.abilitiesById.getValue(2).mechanics.value)
        assertEquals("ROM description", extraction.localization.defaultOverlay()?.abilityDescriptions?.get(1)?.value)
        val sharedEvidence = requireNotNull(extraction.capabilities[RomCapability.ABILITY_MECHANICS])
        assertEquals(CapabilityStatus.PARTIAL, sharedEvidence.status)
        assertEquals(1, sharedEvidence.coveredRecords)
        assertEquals(2, sharedEvidence.expectedRecords)
    }

    @Test
    fun recoveredRuntimeAreaNamesDoNotDependOnWorldMapAvailability() {
        val unavailableWorldMap = CapabilityEvidence(
            capability = RomCapability.WORLD_MAP,
            compatible = false,
            confidence = 0.0,
            status = CapabilityStatus.NOT_FOUND,
            reasons = listOf("atlas geometry was unavailable"),
        )
        val extraction = CatalogLocalizedTextExtractor.extract(
            manifest = englishManifest(),
            speciesById = emptyMap(),
            movesById = emptyMap(),
            abilitiesById = emptyMap(),
            naturesById = emptyMap(),
            runtimeMetadata = CatalogRuntimeMetadata(areaNamesByBaseId = mapOf(0x10 to "Route 101")),
            capabilities = mapOf(RomCapability.WORLD_MAP to unavailableWorldMap),
        )

        val overlay = requireNotNull(extraction.localization.defaultOverlay())
        assertEquals("Route 101", overlay.areaNames.getValue(0x10).value)
        assertEquals(
            CapabilityStatus.AVAILABLE,
            overlay.localizedCapabilities.getValue(LocalizedTextCapability.AREA_NAMES).status,
        )
        assertEquals(
            CapabilityStatus.NOT_FOUND,
            overlay.localizedCapabilities.getValue(LocalizedTextCapability.WORLD_LOCATION_NAMES).status,
        )
        assertEquals(setOf(0x10), extraction.runtimeMetadata.areaBaseIds)
        assertEquals(emptyMap<Int, String>(), extraction.runtimeMetadata.areaNamesByBaseId)
    }

    @Test
    fun unknownLanguageStripsLocalizedDraftTextWithoutPublishingAnOverlay() {
        val extraction = CatalogLocalizedTextExtractor.extract(
            manifest = RomLanguageManifest.UNKNOWN,
            speciesById = mapOf(1 to species(name = CatalogField.available("Untrusted"))),
            movesById = emptyMap(),
            abilitiesById = emptyMap(),
            naturesById = emptyMap(),
            capabilities = mapOf(RomCapability.SPECIES_NAMES to evidence(RomCapability.SPECIES_NAMES)),
        )

        assertNull(extraction.speciesById.getValue(1).name.value)
        assertEquals(emptyMap<LanguageTag, CatalogLanguageOverlay>(), extraction.localization.overlays)
        assertNull(extraction.capabilities[RomCapability.SPECIES_NAMES])
    }

    @Test
    fun resolvedSecondaryProjectionGetsExplicitUnavailableStateUntilMaterialized() {
        val extraction = CatalogLocalizedTextExtractor.extract(
            manifest = resolvedManifest(),
            speciesById = mapOf(1 to species(name = CatalogField.available("Bulbasaur"))),
            movesById = emptyMap(),
            abilitiesById = emptyMap(),
            naturesById = emptyMap(),
            capabilities = mapOf(RomCapability.SPECIES_NAMES to evidence(RomCapability.SPECIES_NAMES)),
        )

        val english = requireNotNull(extraction.localization.overlay(LanguageTag.ENGLISH))
        val french = requireNotNull(extraction.localization.overlay(LanguageTag.FRENCH))
        assertEquals("Bulbasaur", english.speciesNames.getValue(1).value)
        assertEquals(emptyMap<Int, CatalogField<String>>(), french.speciesNames)
        assertEquals(
            CapabilityStatus.NOT_FOUND,
            french.localizedCapabilities.getValue(LocalizedTextCapability.SPECIES_NAMES).status,
        )
        assertEquals(
            1,
            french.localizedCapabilities.getValue(LocalizedTextCapability.SPECIES_NAMES).expectedRecords,
        )
    }

    @Test
    fun unavailableCapabilityEvidenceCannotPublishDraftText() {
        val ambiguous = CapabilityEvidence(
            capability = RomCapability.SPECIES_NAMES,
            compatible = false,
            confidence = 0.5,
            reasons = listOf("conflicting bounded tables"),
            status = CapabilityStatus.AMBIGUOUS,
        )
        val extraction = CatalogLocalizedTextExtractor.extract(
            manifest = englishManifest(),
            speciesById = mapOf(1 to species(name = CatalogField.available("Untrusted"))),
            movesById = emptyMap(),
            abilitiesById = emptyMap(),
            naturesById = emptyMap(),
            capabilities = mapOf(RomCapability.SPECIES_NAMES to ambiguous),
        )

        val overlay = requireNotNull(extraction.localization.defaultOverlay())
        assertEquals(emptyMap<Int, CatalogField<String>>(), overlay.speciesNames)
        assertEquals(
            CapabilityStatus.AMBIGUOUS,
            overlay.localizedCapabilities.getValue(LocalizedTextCapability.SPECIES_NAMES).status,
        )
        assertNull(extraction.speciesById.getValue(1).name.value)
    }

    @Test
    fun rejectsLocalizedKeysWithoutSharedEntities() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            ParsedCatalog(
                romSha256 = "a".repeat(64),
                romCrc32 = "1234ABCD",
                family = EngineFamily.RED_BLUE,
                platform = Platform.GB,
                speciesById = mapOf(1 to species()),
                localization = CatalogLocalization(
                    englishManifest(),
                    mapOf(
                        LanguageTag.ENGLISH to overlay(
                            LanguageTag.ENGLISH,
                            1,
                            mapOf(2 to CatalogField.available("Ivysaur")),
                        ),
                    ),
                ),
            )
        }

        assertEquals("localized species name references an unknown shared species", failure.message)
    }

    private fun catalog(
        manifest: RomLanguageManifest,
        overlays: Map<LanguageTag, CatalogLanguageOverlay>,
    ): ParsedCatalog = ParsedCatalog(
        romSha256 = "a".repeat(64),
        romCrc32 = "1234ABCD",
        family = EngineFamily.RED_BLUE,
        platform = Platform.GB,
        speciesById = mapOf(
            1 to SpeciesRecord(
                id = 1,
                dexNumber = CatalogField.available(1),
                name = CatalogField.notApplicable("localized"),
                typeIds = CatalogField.available(listOf(12)),
                baseStats = CatalogField.available(BaseStats(45, 49, 49, 45, 65, 65)),
                sprite = CatalogField.notFound("fixture"),
            ),
        ),
        localization = CatalogLocalization(manifest, overlays),
    )

    private fun englishManifest(): RomLanguageManifest = RomLanguageManifest(
        defaultLanguage = LanguageTag.ENGLISH,
        projections = listOf(projection(LanguageTag.ENGLISH, "gb-english")),
        status = LanguageResolutionStatus.RESOLVED,
    )

    private fun species(
        name: CatalogField<String> = CatalogField.notApplicable("localized"),
        description: CatalogField<String> = CatalogField.notFound("fixture"),
    ) = SpeciesRecord(
        id = 1,
        dexNumber = CatalogField.available(1),
        name = name,
        typeIds = CatalogField.available(listOf(12)),
        baseStats = CatalogField.available(BaseStats(45, 49, 49, 45, 65, 65)),
        sprite = CatalogField.notFound("fixture"),
        description = description,
    )

    private fun evidence(capability: RomCapability) = CapabilityEvidence(
        capability = capability,
        compatible = true,
        confidence = 1.0,
        coveredRecords = 1,
        expectedRecords = 1,
    )

    private fun resolvedManifest(): RomLanguageManifest = RomLanguageManifest(
        defaultLanguage = LanguageTag.ENGLISH,
        projections = listOf(
            projection(LanguageTag.ENGLISH, "gb-english"),
            projection(LanguageTag.FRENCH, "gb-french"),
        ),
        status = LanguageResolutionStatus.RESOLVED,
    )

    private fun overlay(
        language: LanguageTag,
        version: Long,
        speciesNames: Map<Int, CatalogField<String>> = emptyMap(),
    ): CatalogLanguageOverlay = CatalogLanguageOverlay(
        language = language,
        overlayVersion = version,
        localizedCapabilities = allCapabilityStates(
            expectedRecords = mapOf(
                LocalizedTextCapability.SPECIES_NAMES to 1,
                LocalizedTextCapability.SPECIES_DESCRIPTIONS to 1,
            ),
            coveredRecords = mapOf(
                LocalizedTextCapability.SPECIES_NAMES to speciesNames.size,
            ),
        ),
        speciesNames = speciesNames,
    )

    private fun allCapabilityStates(
        expectedRecords: Map<LocalizedTextCapability, Int> = emptyMap(),
        coveredRecords: Map<LocalizedTextCapability, Int> = emptyMap(),
    ): Map<LocalizedTextCapability, LocalizedCapabilityState> =
        LocalizedTextCapability.entries.associateWith { capability ->
            val expected = expectedRecords[capability] ?: 0
            val covered = coveredRecords[capability] ?: 0
            when {
                expected > 0 && covered == expected -> LocalizedCapabilityState.available(expected)
                covered > 0 -> LocalizedCapabilityState(
                    status = CapabilityStatus.PARTIAL,
                    confidence = 1.0,
                    coveredRecords = covered,
                    expectedRecords = expected,
                )
                expected > 0 -> LocalizedCapabilityState.notFound("fixture missing", expected)
                else -> LocalizedCapabilityState.notApplicable("empty fixture domain")
            }
        }

    private fun projection(
        language: LanguageTag,
        codecId: String,
        status: LanguageResolutionStatus = LanguageResolutionStatus.RESOLVED,
    ): RomLanguageProjection = RomLanguageProjection(
        language = language,
        codecId = codecId,
        codecVersion = 1,
        localizedTables = LocalizedTableLayout(),
        evidence = emptyList(),
        status = status,
    )
}
