package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.LocalizedTableLayout
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.language.RomLanguageProjection
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeBlankMapLocalizationTest {
    @Test
    fun positiveBlankDispositionPreservesEveryIdentityAndOnlyPartitionsProvenNameObligations() {
        val extraction = extract()
        val catalog = catalog(extraction)
        assertEquals(setOf("one", "missing"), catalog.localMaps.staticNameRequiredMapKeys)
        assertEquals(setOf("blank"), catalog.localMaps.nativeBlankMapKeys)
        assertEquals(setOf(3), catalog.localMaps.nativeBlankBaseAreaIds)
        assertEquals(setOf(1, 2, 3), catalog.runtimeMetadata.areaBaseIds)
        assertEquals(listOf(11, 21, 31), catalog.encounterAreas.map { it.id })
        assertEquals(3, catalog.localMaps.maps.size)
        for (language in listOf(LanguageTag.ENGLISH, LanguageTag.FRENCH)) {
            val projection = requireNotNull(catalog.textProjection(language))
            for (capability in listOf(LocalizedTextCapability.LOCAL_MAP_NAMES,
                LocalizedTextCapability.AREA_NAMES, LocalizedTextCapability.ENCOUNTER_AREA_NAMES)) {
                val state = projection.localizedCapabilities.getValue(capability)
                assertEquals(2, state.expectedRecords)
                assertEquals(if (language == LanguageTag.ENGLISH) 1 else 0, state.coveredRecords)
            }
            assertNull(projection.localMapName("blank"))
            assertNull(projection.areaName(3))
            assertNull(projection.encounterAreaName(31))
        }
        assertEquals(CapabilityStatus.PARTIAL, catalog.defaultTextProjection().localizedCapabilities
            .getValue(LocalizedTextCapability.ENCOUNTER_AREA_NAMES).status)
    }

    @Test
    fun aReadableRuntimeLabelCannotOverrideNativeBlankAuthority() {
        val extraction = extract(mapOf(1 to "Town", 3 to "stale label"))
        val overlay = requireNotNull(extraction.localization.defaultOverlay())
        assertEquals(setOf(1), overlay.areaNames.keys)
        assertEquals(setOf(11), overlay.encounterAreaNames.keys)
        assertNull(catalog(extraction).defaultTextProjection().areaName(3))
    }

    @Test
    fun fixedLabelsAndForgedOverlaysCannotBeAttachedToBlankMaps() {
        assertThrows(IllegalArgumentException::class.java) {
            maps().let { source -> source.copy(maps = source.maps.map {
                if (it.key == "blank") it.copy(displayName = "fabricated") else it
            }) }
        }
        val extraction = extract()
        val original = requireNotNull(extraction.localization.defaultOverlay())
        val forged = CatalogLanguageOverlay(
            original.language, original.overlayVersion,
            original.localizedCapabilities + (LocalizedTextCapability.LOCAL_MAP_NAMES to LocalizedCapabilityState.available(2)),
            areaNames = original.areaNames, localMapNames = original.localMapNames + ("blank" to CatalogField.available("forged")),
            encounterAreaNames = original.encounterAreaNames,
        )
        assertThrows(IllegalArgumentException::class.java) {
            catalog(extraction.copy(localization = CatalogLocalization(extraction.localization.manifest,
                extraction.localization.overlays + (original.language to forged))))
        }
    }

    @Test
    fun ordinaryMissingTextAndLocalizedPlaceholderStatusesRemainRequired() {
        val ordinary = maps().let { source -> source.copy(maps = source.maps.map {
            it.copy(displayName = null, nameDisposition = LocalMapNameDisposition.STATIC_NAME_REQUIRED)
        }) }
        val extraction = extract(emptyMap(), ordinary)
        val projection = catalog(extraction).defaultTextProjection()
        for (capability in listOf(LocalizedTextCapability.LOCAL_MAP_NAMES,
            LocalizedTextCapability.AREA_NAMES, LocalizedTextCapability.ENCOUNTER_AREA_NAMES)) {
            val state = projection.localizedCapabilities.getValue(capability)
            assertEquals(3, state.expectedRecords)
            assertEquals(0, state.coveredRecords)
        }
        assertEquals(emptySet<String>(), extraction.localMaps.nativeBlankMapKeys)
    }

    private fun maps() = LocalMapCatalog(
        maps = listOf(
            LocalMap("one", "Town", 1, 16, 16, 1, 1, "map"),
            LocalMap("missing", null, 2, 16, 16, 1, 1, "map"),
            LocalMap("blank", null, 3, 16, 16, 1, 1, "map", LocalMapNameDisposition.NATIVE_BLANK),
        ),
        assets = mapOf("map" to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))),
    )

    private fun extract(names: Map<Int, String> = mapOf(1 to "Town"), maps: LocalMapCatalog = maps()) =
        CatalogLocalizedTextExtractor.extract(
            manifest = RomLanguageManifest(
                LanguageTag.ENGLISH,
                listOf(LanguageTag.ENGLISH, LanguageTag.FRENCH).map {
                    RomLanguageProjection(it, "fixture", 1, LocalizedTableLayout(), emptyList(), LanguageResolutionStatus.RESOLVED)
                },
                LanguageResolutionStatus.RESOLVED,
            ),
            speciesById = emptyMap(), movesById = emptyMap(), abilitiesById = emptyMap(), naturesById = emptyMap(),
            capabilities = emptyMap(), localMaps = maps,
            runtimeMetadata = CatalogRuntimeMetadata(areaBaseIds = setOf(1, 2, 3), areaNamesByBaseId = names),
            encounterAreas = listOf(
                EncounterArea(11, CatalogField.available("synthetic method label"), 1, emptyList()),
                EncounterArea(21, CatalogField.notApplicable("localized placeholder is not blank authority"), 1, emptyList()),
                EncounterArea(31, CatalogField.notFound("missing text is not blank authority"), 1, emptyList()),
            ),
        )

    private fun catalog(extraction: CatalogLocalizedTextExtraction) = ParsedCatalog(
        romSha256 = "b".repeat(64), family = EngineFamily.EMERALD, platform = Platform.GBA,
        localMaps = extraction.localMaps, runtimeMetadata = extraction.runtimeMetadata,
        encounterAreas = extraction.encounterAreas, capabilities = extraction.capabilities,
        localization = extraction.localization,
    )
}
