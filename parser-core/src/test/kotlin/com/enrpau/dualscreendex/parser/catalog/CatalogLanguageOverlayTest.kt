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
    fun task417OnlyExplicitContextExcludesStaticNamesAndPreservesTheSharedInventory() {
        val maps = task417Maps()
        val extraction = poiExtraction(maps)
        val catalog = poiCatalog(extraction)
        val shared = catalog.localMaps
        assertEquals(3, shared.maps.size)
        assertEquals(setOf("local/1", "local/3"), shared.staticNameRequiredMapKeys)
        assertEquals(setOf("local/2"), shared.contextDependentMapKeys)
        assertEquals(shared.maps.size, shared.staticNameRequiredMapKeys.size + shared.contextDependentMapKeys.size)
        assertEquals(maps.maps.map { it.copy(displayName = null) }, shared.maps)
        assertEquals(maps.assets, shared.assets)
        assertEquals(maps.scenes, shared.scenes)
        assertEquals(maps.pois.map { it.copy(displayName = null, displayNamesByTrainerGender = emptyMap()) }, shared.pois)
        assertEquals(LocalMapNameDisposition.STATIC_NAME_REQUIRED, shared.maps.single { it.key == "local/3" }.nameDisposition)
        for (language in listOf(LanguageTag.ENGLISH, LanguageTag.FRENCH)) {
            val text = requireNotNull(catalog.textProjection(language))
            val state = text.localizedCapabilities.getValue(LocalizedTextCapability.LOCAL_MAP_NAMES)
            assertEquals(2, state.expectedRecords)
            assertEquals(if (language == LanguageTag.ENGLISH) 1 else 0, state.coveredRecords)
            assertEquals(if (language == LanguageTag.ENGLISH) CapabilityStatus.PARTIAL else CapabilityStatus.NOT_FOUND, state.status)
            assertEquals(if (language == LanguageTag.ENGLISH) "Town" else null, text.localMapName("local/1"))
            assertNull(text.localMapName("local/2"))
            assertNull(text.localMapName("local/3"))
            assertNull(text.poiDisplayName("warp"))
            assertNull(text.poiDisplayName("missing-sign"))
            assertEquals(6, text.localizedCapabilities.getValue(LocalizedTextCapability.POI_TEXT).expectedRecords)
            assertEquals(if (language == LanguageTag.ENGLISH) 3 else 0,
                text.localizedCapabilities.getValue(LocalizedTextCapability.POI_TEXT).coveredRecords)
        }
    }

    @Test
    fun task417NamedContextualMapAndContradictoryOverlayCannotBePublished() {
        assertThrows(IllegalArgumentException::class.java) {
            poiMaps().let { source -> source.copy(maps = source.maps.map {
                if (it.key == "local/2") it.copy(nameDisposition = LocalMapNameDisposition.CONTEXT_DEPENDENT) else it
            }) }
        }
        val extraction = poiExtraction(task417Maps())
        val original = requireNotNull(extraction.localization.defaultOverlay())
        for (forgedText in listOf(false, true)) {
            val state = if (forgedText) LocalizedCapabilityState.available(2)
                else LocalizedCapabilityState(CapabilityStatus.PARTIAL, 1.0, 1, 3)
            val invalid = CatalogLanguageOverlay(
                original.language, original.overlayVersion,
                original.localizedCapabilities + (LocalizedTextCapability.LOCAL_MAP_NAMES to state),
                itemNames = original.itemNames,
                localMapNames = original.localMapNames + if (forgedText) mapOf("local/2" to CatalogField.available("Fabricated room")) else emptyMap(),
                poiTexts = original.poiTexts,
            )
            assertThrows(IllegalArgumentException::class.java) {
                poiCatalog(extraction.copy(localization = CatalogLocalization(extraction.localization.manifest,
                    extraction.localization.overlays + (original.language to invalid))))
            }
        }
    }

    @Test
    fun task417MissingOrdinaryNamesNeverBecomeContextual() {
        val source = poiMaps()
        val unnamed = source.copy(maps = source.maps.map { it.copy(displayName = null) })
        val ordinary = poiCatalog(poiExtraction(unnamed))
        val state = ordinary.defaultTextProjection().localizedCapabilities.getValue(LocalizedTextCapability.LOCAL_MAP_NAMES)
        assertEquals(2, state.expectedRecords)
        assertEquals(0, state.coveredRecords)
        assertEquals(CapabilityStatus.NOT_FOUND, state.status)
        assertEquals(emptySet<String>(), ordinary.localMaps.contextDependentMapKeys)
        val contextual = unnamed.copy(maps = unnamed.maps.map { it.copy(nameDisposition = LocalMapNameDisposition.CONTEXT_DEPENDENT) })
        val selected = poiCatalog(poiExtraction(contextual))
        val contextualState = selected.defaultTextProjection().localizedCapabilities.getValue(LocalizedTextCapability.LOCAL_MAP_NAMES)
        assertEquals(0, contextualState.expectedRecords)
        assertEquals(CapabilityStatus.NOT_APPLICABLE, contextualState.status)
        assertEquals(2, selected.localMaps.maps.size)
        assertEquals(2, selected.localMaps.contextDependentMapKeys.size)
        assertEquals(6, selected.defaultTextProjection().localizedCapabilities.getValue(LocalizedTextCapability.POI_TEXT).expectedRecords)
    }

    private fun task417Maps() = poiMaps().let { source -> source.copy(
        maps = source.maps.map {
            if (it.key == "local/2") it.copy(displayName = null, nameDisposition = LocalMapNameDisposition.CONTEXT_DEPENDENT) else it
        } + LocalMap("local/3", null, 3, 16, 16, 1, 1, "map"),
    ) }

    @Test
    fun task417GraphicsOnlyRegionsDoNotCreateStaticTitleObligations() {
        fun region(key: String, title: String?, disposition: WorldMapRegionNameDisposition) = WorldMapRegion(
            key, title, 1, 1, 1, 1, "world/$key",
            listOf(WorldMapLocation("location", null, setOf(1), listOf(WorldMapCell(0, 0, 1, 1)))),
            disposition,
        )
        val world = WorldMapCatalog(
            regions = listOf(
                region("static", "HOENN", WorldMapRegionNameDisposition.STATIC_NAME_REQUIRED),
                region("graphics", null, WorldMapRegionNameDisposition.GRAPHICS_ONLY),
            ),
            assets = mapOf(
                "world/static" to RgbaSprite(1, 1, intArrayOf(0)),
                "world/graphics" to RgbaSprite(1, 1, intArrayOf(0)),
            ),
        )
        val extraction = CatalogLocalizedTextExtractor.extract(
            manifest = englishManifest(), speciesById = emptyMap(), movesById = emptyMap(),
            abilitiesById = emptyMap(), naturesById = emptyMap(), capabilities = emptyMap(), worldMaps = world,
        )
        val overlay = requireNotNull(extraction.localization.defaultOverlay())
        assertEquals(setOf("static"), world.staticNameRequiredRegionKeys)
        assertEquals(setOf("graphics"), world.graphicsOnlyRegionKeys)
        assertEquals(setOf("static"), overlay.worldRegionNames.keys)
        assertEquals(LocalizedCapabilityState.available(1),
            overlay.localizedCapabilities.getValue(LocalizedTextCapability.WORLD_REGION_NAMES))
        assertThrows(IllegalArgumentException::class.java) {
            world.copy(regions = world.regions.map {
                if (it.key == "graphics") it.copy(displayName = "Fabricated") else it
            })
        }
    }

    @Test
    fun itemReferenceSatisfiesPoiCoverageWithoutDuplicatingItemText() {
        val localMaps = LocalMapCatalog(
            maps = listOf(LocalMap("local/1", "Town", 1, 16, 16, 1, 1, "map")),
            assets = mapOf("map" to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))),
            pois = listOf(LocalMapPoi(
                "item", "local/1", 1, 0, 0, LocalMapPoiKind.VISIBLE_ITEM,
                item = LocalMapPoiItem(itemId = 4),
                textObligation = LocalMapPoiTextObligation.ITEM_NAME,
            )),
        )
        val extraction = CatalogLocalizedTextExtractor.extract(
            manifest = resolvedManifest(), speciesById = emptyMap(), movesById = emptyMap(),
            abilitiesById = emptyMap(), naturesById = emptyMap(), capabilities = emptyMap(),
            localMaps = localMaps,
            captureBallsById = mapOf(4 to CaptureBallRecord(4, CatalogField.available("native ball"), CatalogField.notFound("sprite"))),
        )
        val overlay = requireNotNull(extraction.localization.defaultOverlay())
        assertEquals(1, overlay.localizedCapabilities.getValue(LocalizedTextCapability.POI_TEXT).coveredRecords)
        assertEquals(emptyMap<String, CatalogPoiText>(), overlay.poiTexts)
    }

    @Test
    fun task415CoverageResolvesOnlyExplicitSameOverlayObligations() {
        val extraction = poiExtraction()
        val catalog = poiCatalog(extraction)
        val overlay = requireNotNull(catalog.defaultLocalizedText())
        val state = overlay.localizedCapabilities.getValue(LocalizedTextCapability.POI_TEXT)
        assertEquals(4, state.coveredRecords)
        assertEquals(6, state.expectedRecords)
        assertEquals(setOf("direct", "gender"), overlay.poiTexts.keys)
        assertEquals(setOf(4), overlay.itemNames.keys)
        assertEquals(poiMaps().pois.map { it.textObligation }, catalog.localMaps.pois.map { it.textObligation })
        assertEquals(poiMaps().assets, catalog.localMaps.assets)
        assertEquals(poiMaps().maps.map { it.copy(displayName = null) }, catalog.localMaps.maps)
        catalog.localMaps.pois.forEach {
            assertNull(it.displayName)
            assertEquals(emptyMap<Int, String>(), it.displayNamesByTrainerGender)
            assertNull(it.item?.displayName)
        }
        val text = catalog.defaultTextProjection()
        assertEquals("Ball", text.poiItemName("item", 4))
        assertNull(text.poiItemName("item", 5))
        assertNull(text.poiItemName("direct", 4))
        assertEquals("Cave", text.poiDisplayName("warp"))
        assertEquals("Direct sign", text.poiDisplayName("direct"))
        assertNull(text.poiDisplayName("missing-sign"))
        assertNull(text.poiDisplayName("unknown"))
        assertEquals("Boy's house", text.poiDisplayName("gender", 0))
        assertEquals("Girl's house", text.poiDisplayName("gender", 1))
        assertNull(text.poiDisplayName("gender", 2))
        assertNull(text.poiDisplayName("gender"))
        val french = requireNotNull(catalog.textProjection(LanguageTag.FRENCH))
        assertNull(french.poiItemName("item", 4))
        catalog.localMaps.pois.forEach { assertNull(french.poiDisplayName(it.key, 0)) }
        assertEquals(0, french.localizedCapabilities.getValue(LocalizedTextCapability.POI_TEXT).coveredRecords)
        assertEquals(6, french.localizedCapabilities.getValue(LocalizedTextCapability.POI_TEXT).expectedRecords)
    }

    @Test
    fun task415MissingConflictingReferencesAndIncompleteGenderStayIncomplete() {
        val maps = poiMaps().let { source -> source.copy(
            maps = source.maps.map { it.copy(displayName = null) },
            pois = source.pois.map { poi -> when (poi.key) {
                "item" -> poi.copy(item = poi.item!!.copy(displayName = "Conflicting ball"))
                "gender" -> poi.copy(displayName = "Must not replace missing female", displayNamesByTrainerGender = mapOf(0 to "Boy's house"))
                else -> poi
            } },
        ) }
        val catalog = poiCatalog(poiExtraction(maps))
        val text = catalog.defaultTextProjection()
        assertEquals(1, text.localizedCapabilities.getValue(LocalizedTextCapability.POI_TEXT).coveredRecords)
        assertEquals(6, text.localizedCapabilities.getValue(LocalizedTextCapability.POI_TEXT).expectedRecords)
        assertNull(text.poiItemName("item", 4))
        assertNull(text.poiDisplayName("warp"))
        assertNull(text.poiDisplayName("gender", 0))
        assertNull(text.poiDisplayName("gender", 1))
        assertEquals("Direct sign", text.poiDisplayName("direct"))
    }

    @Test
    fun task415DeniedLocalAuthorityCannotPublishReferenceLabels() {
        val denied = CapabilityEvidence(RomCapability.LOCAL_MAP, false, 0.0, status = CapabilityStatus.AMBIGUOUS)
        val catalog = poiCatalog(poiExtraction(capabilities = mapOf(RomCapability.LOCAL_MAP to denied)))
        val text = catalog.defaultTextProjection()
        assertEquals("Ball", text.itemName(4))
        assertEquals(0, text.localizedCapabilities.getValue(LocalizedTextCapability.POI_TEXT).coveredRecords)
        assertNull(text.poiItemName("item", 4))
        assertNull(text.poiDisplayName("warp"))
        assertNull(text.poiDisplayName("direct"))
    }

    @Test
    fun task415CoverageIsRevalidatedAgainstSharedReferences() {
        val extraction = poiExtraction()
        val original = requireNotNull(extraction.localization.defaultOverlay())
        val inflated = CatalogLanguageOverlay(
            original.language, original.overlayVersion,
            original.localizedCapabilities + (LocalizedTextCapability.POI_TEXT to LocalizedCapabilityState.available(6)),
            itemNames = original.itemNames, localMapNames = original.localMapNames, poiTexts = original.poiTexts,
        )
        assertThrows(IllegalArgumentException::class.java) {
            poiCatalog(extraction.copy(localization = CatalogLocalization(extraction.localization.manifest,
                extraction.localization.overlays + (original.language to inflated))))
        }
        val missingTarget = extraction.localMaps.copy(pois = extraction.localMaps.pois.map {
            if (it.key == "warp") it.copy(destinationBaseAreaId = 999) else it
        })
        assertThrows(IllegalArgumentException::class.java) { poiCatalog(extraction.copy(localMaps = missingTarget)) }
        val misboundText = CatalogLanguageOverlay(
            original.language, original.overlayVersion, original.localizedCapabilities,
            itemNames = original.itemNames, localMapNames = original.localMapNames,
            poiTexts = original.poiTexts + ("warp" to CatalogPoiText(displayName = CatalogField.available("Decoy"))),
        )
        assertThrows(IllegalArgumentException::class.java) {
            poiCatalog(extraction.copy(localization = CatalogLocalization(extraction.localization.manifest,
                extraction.localization.overlays + (original.language to misboundText))))
        }
    }

    @Test
    fun task415AnonymousItemUsesOnlyItsExplicitPoiTextObligation() {
        val maps = poiMaps().let { source -> source.copy(pois = source.pois.map {
            if (it.key == "item") it.copy(item = LocalMapPoiItem(displayName = "Inline item")) else it
        }) }
        val catalog = poiCatalog(poiExtraction(maps))
        assertEquals("Inline item", catalog.defaultTextProjection().poiItemName("item", null))
        assertNull(catalog.defaultTextProjection().poiItemName("item", 4))
        assertEquals(4, catalog.defaultTextProjection().localizedCapabilities.getValue(LocalizedTextCapability.POI_TEXT).coveredRecords)
        assertNull(requireNotNull(catalog.textProjection(LanguageTag.FRENCH)).poiItemName("item", null))
    }

    private fun poiMaps() = LocalMapCatalog(
        maps = listOf(LocalMap("local/1", "Town", 1, 16, 16, 1, 1, "map"), LocalMap("local/2", "Cave", 2, 16, 16, 1, 1, "map")),
        assets = mapOf("map" to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))),
        pois = listOf(
            LocalMapPoi("item", "local/1", 1, 0, 0, LocalMapPoiKind.VISIBLE_ITEM, item = LocalMapPoiItem(4), textObligation = LocalMapPoiTextObligation.ITEM_NAME),
            LocalMapPoi("warp", "local/1", 1, 0, 0, LocalMapPoiKind.PLACE, destinationBaseAreaId = 2, textObligation = LocalMapPoiTextObligation.DESTINATION_NAME),
            LocalMapPoi("missing-sign", "local/1", 1, 0, 0, LocalMapPoiKind.PLACE, destinationBaseAreaId = 2, textObligation = LocalMapPoiTextObligation.DIRECT_TEXT),
            LocalMapPoi("direct", "local/1", 1, 0, 0, LocalMapPoiKind.PLACE, displayName = "Direct sign", textObligation = LocalMapPoiTextObligation.DIRECT_TEXT),
            LocalMapPoi("unknown", "local/1", 1, 0, 0, LocalMapPoiKind.UNKNOWN, displayName = "Untrusted", destinationBaseAreaId = 2),
            LocalMapPoi("gender", "local/1", 1, 0, 0, LocalMapPoiKind.PLACE, displayName = "Must not pick first", displayNamesByTrainerGender = mapOf(0 to "Boy's house", 1 to "Girl's house"), textObligation = LocalMapPoiTextObligation.GENDERED_DIRECT_TEXT),
        ),
    )

    private fun poiExtraction(maps: LocalMapCatalog = poiMaps(), capabilities: Map<RomCapability, CapabilityEvidence> = emptyMap()) =
        CatalogLocalizedTextExtractor.extract(
            manifest = resolvedManifest(), speciesById = emptyMap(), movesById = emptyMap(),
            abilitiesById = emptyMap(), naturesById = emptyMap(), capabilities = capabilities, localMaps = maps,
            captureBallsById = mapOf(4 to CaptureBallRecord(4, CatalogField.available("Ball"), CatalogField.notFound("sprite"))),
        )

    private fun poiCatalog(extraction: CatalogLocalizedTextExtraction) = ParsedCatalog(
        romSha256 = "4".repeat(64), family = EngineFamily.GOLD_SILVER, platform = Platform.GBC,
        localMaps = extraction.localMaps, captureBallsById = extraction.captureBallsById,
        capabilities = extraction.capabilities, localization = extraction.localization,
    )

    @Test
    fun resolvedSecondaryProjectionNeverBorrowsOrdinaryItemNames() {
        val extraction = CatalogLocalizedTextExtractor.extract(
            manifest = resolvedManifest(), speciesById = emptyMap(), movesById = emptyMap(),
            abilitiesById = emptyMap(), naturesById = emptyMap(), capabilities = emptyMap(),
            captureBallsById = mapOf(4 to CaptureBallRecord(4, CatalogField.available("native ball"), CatalogField.notFound("sprite"))),
        )
        val english = requireNotNull(extraction.localization.overlay(LanguageTag.ENGLISH))
        val french = requireNotNull(extraction.localization.overlay(LanguageTag.FRENCH))
        assertEquals("native ball", english.itemNames.getValue(4).value)
        assertEquals(emptyMap<Int, CatalogField<String>>(), french.itemNames)
        assertEquals(CapabilityStatus.NOT_FOUND, french.localizedCapabilities.getValue(LocalizedTextCapability.ITEM_NAMES).status)
        assertEquals(1, french.localizedCapabilities.getValue(LocalizedTextCapability.ITEM_NAMES).expectedRecords)
        assertNull(extraction.captureBallsById.getValue(4).name.value)
    }

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
