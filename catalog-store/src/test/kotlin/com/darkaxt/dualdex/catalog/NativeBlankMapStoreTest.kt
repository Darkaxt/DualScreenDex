package com.darkaxt.dualdex.catalog

import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.CatalogLanguageOverlay
import com.enrpau.dualscreendex.parser.catalog.CatalogLocalization
import com.enrpau.dualscreendex.parser.catalog.CatalogRuntimeMetadata
import com.enrpau.dualscreendex.parser.catalog.EncounterArea
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.LocalMapNameDisposition
import com.enrpau.dualscreendex.parser.catalog.LocalizedCapabilityState
import com.enrpau.dualscreendex.parser.catalog.LocalizedTextCapability
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.PngMapAsset
import com.enrpau.dualscreendex.parser.catalog.textProjection
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeBlankMapStoreTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun completeSqliteAndFreshCachePreserveBlankAuthorityAndAllIdentities() {
        val catalog = catalog()
        val root = temporary.newFolder()
        val file = CatalogCache(root, JdbcCatalogDatabaseFactory).fileFor(catalog.romSha256)
        JdbcCatalogDatabaseFactory.open(file).use { database ->
            CatalogWriter(database).write(catalog, CatalogSourceMetadata.direct("Synthetic.gba", 65536, "SYNTHETIC"),
                CatalogWriteProgress.complete())
        }
        JdbcCatalogDatabaseFactory.open(file).use { database ->
            assertEquals(catalog, requireNotNull(CatalogReader(database).readComplete()).catalog)
            assertEquals(CatalogSchema.requiredSections + setOf("language_overlay:en", "language_overlay:fr"),
                database.query("SELECT name FROM catalog_sections") { it.string("name") }.toSet())
            assertEquals(listOf("ok"), database.query("PRAGMA integrity_check") { it.string("integrity_check") })
        }
        val reopened = requireNotNull(CatalogCache(root, JdbcCatalogDatabaseFactory).readComplete(catalog.romSha256)).catalog
        assertEquals(catalog, reopened)
        assertEquals(setOf("blank"), reopened.localMaps.nativeBlankMapKeys)
        assertEquals(setOf("one", "missing"), reopened.localMaps.staticNameRequiredMapKeys)
        assertEquals(setOf(1, 2, 3), reopened.runtimeMetadata.areaBaseIds)
        assertEquals(listOf(11, 21, 31), reopened.encounterAreas.map { it.id })
        for (language in listOf(LanguageTag.ENGLISH, LanguageTag.FRENCH)) {
            val text = requireNotNull(reopened.textProjection(language))
            assertNull(text.localMapName("blank"))
            assertNull(text.areaName(3))
            assertNull(text.encounterAreaName(31))
            assertNull(text.localMapName("missing"))
            for (capability in listOf(LocalizedTextCapability.LOCAL_MAP_NAMES,
                LocalizedTextCapability.AREA_NAMES, LocalizedTextCapability.ENCOUNTER_AREA_NAMES)) {
                val state = text.localizedCapabilities.getValue(capability)
                assertEquals(2, state.expectedRecords)
                assertEquals(if (language == LanguageTag.ENGLISH) 1 else 0, state.coveredRecords)
            }
            assertEquals(if (language == LanguageTag.ENGLISH) "Town" else null, text.encounterAreaName(11))
        }
    }

    private fun catalog(): ParsedCatalog {
        val languages = listOf(LanguageTag.ENGLISH, LanguageTag.FRENCH)
        val manifest = RomLanguageManifest(LanguageTag.ENGLISH, languages.map {
            RomLanguageProjection(it, "fixture", 1, LocalizedTableLayout(), emptyList(), LanguageResolutionStatus.RESOLVED)
        }, LanguageResolutionStatus.RESOLVED)
        val names = setOf(LocalizedTextCapability.LOCAL_MAP_NAMES, LocalizedTextCapability.AREA_NAMES,
            LocalizedTextCapability.ENCOUNTER_AREA_NAMES)
        val overlays = languages.associateWith { language ->
            val english = language == LanguageTag.ENGLISH
            CatalogLanguageOverlay(language, 2, LocalizedTextCapability.entries.associateWith { capability ->
                if (capability in names) LocalizedCapabilityState(
                    if (english) CapabilityStatus.PARTIAL else CapabilityStatus.NOT_FOUND,
                    if (english) 1.0 else 0.0, if (english) 1 else 0, 2,
                ) else LocalizedCapabilityState.notApplicable("empty generated domain")
            }, areaNames = if (english) mapOf(1 to CatalogField.available("Town")) else emptyMap(),
                localMapNames = if (english) mapOf("one" to CatalogField.available("Town")) else emptyMap(),
                encounterAreaNames = if (english) mapOf(11 to CatalogField.available("Town")) else emptyMap())
        }
        return ParsedCatalog(romSha256 = "d".repeat(64), romCrc32 = "1234ABCD",
            family = EngineFamily.EMERALD, platform = Platform.GBA,
            localMaps = LocalMapCatalog(maps = listOf(
                LocalMap("one", null, 1, 16, 16, 1, 1, "map"),
                LocalMap("missing", null, 2, 16, 16, 1, 1, "map"),
                LocalMap("blank", null, 3, 16, 16, 1, 1, "map", LocalMapNameDisposition.NATIVE_BLANK),
            ), assets = mapOf("map" to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)))),
            runtimeMetadata = CatalogRuntimeMetadata(areaBaseIds = setOf(1, 2, 3)),
            encounterAreas = listOf(11, 21, 31).map { EncounterArea(it, CatalogField.notApplicable("localized"), 1, emptyList()) },
            localization = CatalogLocalization(manifest, overlays))
    }
}
