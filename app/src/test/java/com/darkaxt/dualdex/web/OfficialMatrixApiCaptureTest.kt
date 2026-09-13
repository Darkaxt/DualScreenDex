package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.catalog.CatalogDatabaseFactory
import com.darkaxt.dualdex.catalog.CatalogLogicalDigest
import com.darkaxt.dualdex.catalog.CatalogSchema
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.enrpau.dualscreendex.companion.api.AbilityView
import com.enrpau.dualscreendex.companion.api.AreaGuideAreaView
import com.enrpau.dualscreendex.companion.api.AreaGuideOverviewView
import com.enrpau.dualscreendex.companion.api.AreaGuideView
import com.enrpau.dualscreendex.companion.api.AreaView
import com.enrpau.dualscreendex.companion.api.BallView
import com.enrpau.dualscreendex.companion.api.BootstrapView
import com.enrpau.dualscreendex.companion.api.LocalMapPoiView
import com.enrpau.dualscreendex.companion.api.LocalMapView
import com.enrpau.dualscreendex.companion.api.MoveView
import com.enrpau.dualscreendex.companion.api.NatureView
import com.enrpau.dualscreendex.companion.api.TypeView
import com.enrpau.dualscreendex.companion.api.WorldMapLocationView
import com.enrpau.dualscreendex.companion.api.WorldMapRegionView
import com.enrpau.dualscreendex.parser.catalog.AbilityRecord
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.CatalogLanguageOverlay
import com.enrpau.dualscreendex.parser.catalog.CatalogLocalization
import com.enrpau.dualscreendex.parser.catalog.CatalogPoiText
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoi
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiTextObligation
import com.enrpau.dualscreendex.parser.catalog.PngMapAsset
import com.enrpau.dualscreendex.parser.catalog.TypeRecord
import com.enrpau.dualscreendex.parser.catalog.TypeSemanticRole
import com.enrpau.dualscreendex.parser.catalog.LocalizedCapabilityState
import com.enrpau.dualscreendex.parser.catalog.LocalizedTextCapability
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.LocalizedTableLayout
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.language.RomLanguageProjection
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

class OfficialMatrixApiCaptureTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun capturesUnchangedBootstrapInsideMeasuredEnvelopeWithoutRomOrParser() {
        val fixture = fixture()
        val openedPaths = mutableListOf<Path>()
        val observed = capture(fixture, CatalogDatabaseFactory { file ->
            openedPaths.add(file.toPath().toAbsolutePath().normalize())
            JdbcTestCatalogDatabaseFactory.open(file)
        })
        val privateCopy = fixture.working.resolve("$SHA.sqlite").toAbsolutePath().normalize()
        assertTrue(openedPaths.size >= 2)
        assertTrue(openedPaths.all { it == privateCopy })
        assertFalse(openedPaths.contains(fixture.source.toAbsolutePath().normalize()))
        assertFalse(observed.get("acceptance").asBoolean)
        assertEquals("CACHE_ONLY_OBSERVATION", observed.get("scope").asString)
        assertEquals(fixture.expected.cacheSha256, observed.get("cacheSha256").asString)
        val envelope = observed.getAsJsonObject("bootstrap")
        assertEquals(0, envelope.get("parserInvocations").asInt)
        val response = envelope.getAsJsonObject("response")
        assertFalse(response.has("parserInvocations"))
        assertEquals(SHA, response.getAsJsonObject("catalog").get("hash").asString)
        assertEquals("Bulbizarre", response.getAsJsonObject("catalog").getAsJsonArray("species")[0]
            .asJsonObject.get("name").asString)
        assertEquals("fr", response.getAsJsonObject("language").get("activeLanguage").asString)
        assertEquals("ROM_DEFAULT", response.getAsJsonObject("language").get("authority").asString)
        assertEquals("CACHE_REOPEN", response.getAsJsonObject("state").getAsJsonObject("loading").get("phase").asString)
        assertEquals("RESTORE_CATALOG_BY_SHA", envelope.getAsJsonObject("captureProvenance").get("method").asString)
        assertEquals(fixture.expected.cacheSha256, digest(fixture.source))
        assertTrue(Files.isRegularFile(fixture.working.resolve("$SHA.sqlite")))
    }

    @Test fun capturesAllFifteenApiGroupsWithoutOverlayBackfill() {
        val observed = capture(fixture())
        val fields = observed.getAsJsonObject("fields")
        assertNotNull("actual API field observations are required", fields)
        assertEquals(LocalizedTextCapability.entries.map { it.name }.toSet(), fields.keySet())
        assertEquals("Bulbizarre", fields.getAsJsonObject("SPECIES_NAMES").get("1").asString)
        assertEquals(0, fields.getAsJsonObject("SPECIES_DESCRIPTIONS").size())
        assertEquals(0, fields.getAsJsonObject("ITEM_NAMES").size())
    }

    @Test fun measuresInspectedGroupsAndActualTypeDomain() {
        val observed = capture(fixture())
        val measurements = observed.getAsJsonObject("measurements")
        assertNotNull("projection and type observations must be measured", measurements)
        val isolation = measurements.getAsJsonObject("projectionIsolation")
        assertEquals(15, isolation.get("fieldsChecked").asInt)
        for (key in listOf("mixedFields", "fallbackFields", "sharedTextFields")) assertEquals(0, isolation.get(key).asInt)
        val types = measurements.getAsJsonObject("typeSemantics")
        assertEquals(0, types.get("typesChecked").asInt)
        assertEquals(0, types.get("unresolvedTypes").asInt)
        assertEquals(0, types.get("mismatchedTypes").asInt)
    }

    @Test fun normalizesEveryGroupOnlyFromApiValuesAndKeepsCompoundKeys() {
        val fixture = fixture()
        val response = response(fixture)
        val api = requireNotNull(response.catalog)
        val changed = response.copy(catalog = api.copy(
            species = api.species.map { it.copy(name = "API species", description = "API species description",
                abilities = listOf(AbilityView(3, "API ability", "API ability description", emptyList()))) },
            moves = listOf(MoveView(7, "API move", null, null, null, null, null, null, null, "API move description")),
            types = listOf(TypeView(2, "API type", null, null, null)),
            natures = listOf(NatureView(4, "API nature", emptyMap(), null, null, 10, 10, null, null)),
            balls = listOf(BallView(13, "API item", false, false)),
            areas = listOf(AreaView(6, 9, "API encounter", 0, emptyList(), emptyList(), emptyList())),
            localMaps = listOf(LocalMapView("map", "API map", 9, 16, 16, 1, 1, "", false)),
            worldMaps = listOf(
                WorldMapRegionView("a/b", "API region", 16, 16, 1, 1, "",
                    listOf(WorldMapLocationView("c", "API location one", emptyList(), emptyList()))),
                WorldMapRegionView("a", "API region two", 16, 16, 1, 1, "",
                    listOf(WorldMapLocationView("b/c", "API location two", emptyList(), emptyList()))),
            ),
        ), state = response.state.copy(
            areaGuide = AreaGuideView(null, listOf(AreaGuideAreaView(9, "API area",
                AreaGuideOverviewView(0, null, 0, emptyList()), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))),
            localMapPois = listOf(
                LocalMapPoiView("item", "map", 9, 0, 0, "AVAILABLE_ITEM", "AVAILABLE", "wrong display channel", null, 14, "API POI item", null),
                LocalMapPoiView("sign", "map", 9, 0, 0, "PERSON", "KNOWN", "API sign", null, null, "wrong item channel", null),
            ),
        ))
        val observed = OfficialMatrixApiObservations.observe(fixture.catalog, changed)
        val fields = observed.getAsJsonObject("fields")
        val expected = mapOf(
            "SPECIES_NAMES" to ("1" to "API species"), "SPECIES_DESCRIPTIONS" to ("1" to "API species description"),
            "MOVE_NAMES" to ("7" to "API move"), "MOVE_DESCRIPTIONS" to ("7" to "API move description"),
            "ABILITY_NAMES" to ("3" to "API ability"), "ABILITY_DESCRIPTIONS" to ("3" to "API ability description"),
            "TYPE_NAMES" to ("2" to "API type"), "NATURE_NAMES" to ("4" to "API nature"),
            "ITEM_NAMES" to ("13" to "API item"),
            "LOCAL_MAP_NAMES" to ("map" to "API map"), "WORLD_REGION_NAMES" to ("a/b" to "API region"),
            "ENCOUNTER_AREA_NAMES" to ("6" to "API encounter"), "POI_TEXT" to ("sign" to "API sign"),
        )
        expected.forEach { (cap, value) -> assertEquals(cap, value.second, fields.getAsJsonObject(cap).get(value.first).asString) }
        assertEquals(0, fields.getAsJsonObject("AREA_NAMES").size())
        assertEquals("API POI item", fields.getAsJsonObject("ITEM_NAMES").get("14").asString)
        assertEquals("API POI item", fields.getAsJsonObject("POI_TEXT").get("item").asString)
        val locations = fields.getAsJsonObject("WORLD_LOCATION_NAMES")
        assertEquals("API location one", locations.getAsJsonObject("a/b").get("c").asString)
        assertEquals("API location two", locations.getAsJsonObject("a").get("b/c").asString)
        assertTrue(observed.getAsJsonObject("measurements").getAsJsonObject("projectionIsolation").get("mixedFields").asInt > 0)
        assertEquals("Bulbizarre", fixture.catalog.localizedTextByLanguage.getValue(LanguageTag.FRENCH).speciesNames.getValue(1).value)
    }

    @Test fun measuresSharedBackingEvenWhenSelectedOverlayWins() {
        val fixture = fixture()
        val response = response(fixture)
        val species = fixture.catalog.speciesById.getValue(1).copy(name = CatalogField.available("forbidden shared name"))
        val contaminated = fixture.catalog.copy(speciesById = mapOf(1 to species))
        val observed = OfficialMatrixApiObservations.observe(contaminated, response)
        val isolation = observed.getAsJsonObject("measurements").getAsJsonObject("projectionIsolation")
        assertEquals(1, isolation.get("sharedTextFields").asInt)
        assertEquals(0, isolation.get("fallbackFields").asInt)
        assertEquals("Bulbizarre", observed.getAsJsonObject("fields").getAsJsonObject("SPECIES_NAMES").get("1").asString)
    }

    @Test fun missingApiTextIsMeasuredRatherThanFilledFromTheOverlay() {
        val fixture = fixture()
        val response = response(fixture)
        val api = requireNotNull(response.catalog)
        val observed = OfficialMatrixApiObservations.observe(fixture.catalog, response.copy(catalog = api.copy(species = emptyList())))
        assertEquals(0, observed.getAsJsonObject("fields").getAsJsonObject("SPECIES_NAMES").size())
        assertEquals(1, observed.getAsJsonObject("measurements").getAsJsonObject("projectionIsolation").get("mixedFields").asInt)
    }

    @Test fun excludesReservedCatalogRowsOutsideThePublicApiDomain() {
        val fixture = fixture()
        val response = response(fixture)
        val reserved = SpeciesRecord(
            id = 31, dexNumber = CatalogField.available(0), name = CatalogField.notApplicable("overlay"),
            typeIds = CatalogField.notFound("fixture"), baseStats = CatalogField.notFound("fixture"),
            sprite = CatalogField.notFound("fixture"),
        )
        val capabilities = LocalizedTextCapability.entries.associateWith {
            when (it) {
                LocalizedTextCapability.SPECIES_NAMES -> LocalizedCapabilityState.available(2)
                LocalizedTextCapability.SPECIES_DESCRIPTIONS ->
                    LocalizedCapabilityState.unavailable(CapabilityStatus.NOT_FOUND, 2)
                LocalizedTextCapability.ABILITY_NAMES,
                LocalizedTextCapability.ABILITY_DESCRIPTIONS,
                -> LocalizedCapabilityState.available(1)
                else -> LocalizedCapabilityState.unavailable(CapabilityStatus.NOT_APPLICABLE, 0, 1.0)
            }
        }
        val overlay = CatalogLanguageOverlay(
            language = LanguageTag.FRENCH, overlayVersion = 7, localizedCapabilities = capabilities,
            speciesNames = mapOf(
                1 to CatalogField.available("Bulbizarre"),
                31 to CatalogField.available("MISSINGNO."),
            ),
            abilityNames = mapOf(76 to CatalogField.available("CACOPHONY")),
            abilityDescriptions = mapOf(76 to CatalogField.available("Avoids sound-based moves.")),
        )
        val catalog = fixture.catalog.copy(
            speciesById = fixture.catalog.speciesById + (31 to reserved),
            abilitiesById = mapOf(76 to AbilityRecord(
                76, CatalogField.notApplicable("overlay"), CatalogField.notApplicable("overlay"),
            )),
            localization = CatalogLocalization(fixture.catalog.languageManifest, mapOf(LanguageTag.FRENCH to overlay)),
        )

        val isolation = OfficialMatrixApiObservations.observe(catalog, response)
            .getAsJsonObject("measurements").getAsJsonObject("projectionIsolation")
        assertEquals(0, isolation.get("mixedFields").asInt)
        assertEquals(0, isolation.get("fallbackFields").asInt)
    }

    @Test fun doesNotChargeLocalMapAreaGuideLabelsToAreaNames() {
        val fixture = fixture()
        val response = response(fixture)
        val map = LocalMap("map", null, 9, 16, 16, 1, 1, "map.png")
        val localMaps = LocalMapCatalog(listOf(map), mapOf("map.png" to pngAsset()))
        val capabilities = LocalizedTextCapability.entries.associateWith {
            when (it) {
                LocalizedTextCapability.SPECIES_NAMES,
                LocalizedTextCapability.LOCAL_MAP_NAMES,
                -> LocalizedCapabilityState.available(1)
                LocalizedTextCapability.SPECIES_DESCRIPTIONS ->
                    LocalizedCapabilityState.unavailable(CapabilityStatus.NOT_FOUND, 1)
                else -> LocalizedCapabilityState.unavailable(CapabilityStatus.NOT_APPLICABLE, 0, 1.0)
            }
        }
        val overlay = CatalogLanguageOverlay(
            language = LanguageTag.FRENCH, overlayVersion = 7, localizedCapabilities = capabilities,
            speciesNames = mapOf(1 to CatalogField.available("Bulbizarre")),
            localMapNames = mapOf("map" to CatalogField.available("Jadielle")),
        )
        val catalog = fixture.catalog.copy(
            localMaps = localMaps,
            localization = CatalogLocalization(fixture.catalog.languageManifest, mapOf(LanguageTag.FRENCH to overlay)),
        )
        val api = requireNotNull(response.catalog).copy(
            localMaps = listOf(LocalMapView("map", "Jadielle", 9, 16, 16, 1, 1, "", false)),
        )
        val areaGuide = AreaGuideView(null, listOf(AreaGuideAreaView(
            9, "Jadielle", AreaGuideOverviewView(0, null, 0, emptyList()),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
        )))

        val observed = OfficialMatrixApiObservations.observe(catalog, response.copy(
            catalog = api, state = response.state.copy(areaGuide = areaGuide),
        ))
        assertEquals(0, observed.getAsJsonObject("fields").getAsJsonObject("AREA_NAMES").size())
        val isolation = observed.getAsJsonObject("measurements").getAsJsonObject("projectionIsolation")
        assertEquals(0, isolation.get("mixedFields").asInt)
        assertEquals(0, isolation.get("fallbackFields").asInt)
    }

    @Test fun comparesPoiTemplatesAfterAnonymousPlayerRendering() {
        val fixture = fixture()
        val response = response(fixture)
        val map = LocalMap("map", null, 9, 16, 16, 1, 1, "map.png")
        val pois = listOf(
            LocalMapPoi("house", "map", 9, 0, 0, LocalMapPoiKind.PLACE,
                textObligation = LocalMapPoiTextObligation.DIRECT_TEXT),
            LocalMapPoi("notebook", "map", 9, 0, 0, LocalMapPoiKind.PLACE,
                textObligation = LocalMapPoiTextObligation.DIRECT_TEXT),
        )
        val localMaps = LocalMapCatalog(listOf(map), mapOf("map.png" to pngAsset()), pois = pois)
        val capabilities = LocalizedTextCapability.entries.associateWith {
            when (it) {
                LocalizedTextCapability.SPECIES_NAMES,
                LocalizedTextCapability.LOCAL_MAP_NAMES,
                -> LocalizedCapabilityState.available(1)
                LocalizedTextCapability.POI_TEXT -> LocalizedCapabilityState.available(2)
                LocalizedTextCapability.SPECIES_DESCRIPTIONS ->
                    LocalizedCapabilityState.unavailable(CapabilityStatus.NOT_FOUND, 1)
                else -> LocalizedCapabilityState.unavailable(CapabilityStatus.NOT_APPLICABLE, 0, 1.0)
            }
        }
        val overlay = CatalogLanguageOverlay(
            language = LanguageTag.FRENCH, overlayVersion = 7, localizedCapabilities = capabilities,
            speciesNames = mapOf(1 to CatalogField.available("Bulbizarre")),
            localMapNames = mapOf("map" to CatalogField.available("Jadielle")),
            poiTexts = mapOf(
                "house" to CatalogPoiText(displayName = CatalogField.available("{PLAYER}'s house")),
                "notebook" to CatalogPoiText(displayName = CatalogField.available("{PLAYER} flipped open the notebook.")),
            ),
        )
        val catalog = fixture.catalog.copy(
            localMaps = localMaps,
            localization = CatalogLocalization(fixture.catalog.languageManifest, mapOf(LanguageTag.FRENCH to overlay)),
        )
        val api = requireNotNull(response.catalog).copy(
            localMaps = listOf(LocalMapView("map", "Jadielle", 9, 16, 16, 1, 1, "", false)),
        )
        val observed = OfficialMatrixApiObservations.observe(catalog, response.copy(
            catalog = api,
            state = response.state.copy(localMapPois = listOf(
                LocalMapPoiView("house", "map", 9, 0, 0, "PLACE", "KNOWN", "Your house", null, null, null, null),
                LocalMapPoiView("notebook", "map", 9, 0, 0, "PLACE", "KNOWN",
                    "You flipped open the notebook.", null, null, null, null),
            )),
        ))
        val isolation = observed.getAsJsonObject("measurements").getAsJsonObject("projectionIsolation")
        assertEquals(0, isolation.get("mixedFields").asInt)
        assertEquals(0, isolation.get("fallbackFields").asInt)
    }

    @Test fun countsUnresolvedTypesAndDuplicateApiTypeRows() {
        val fixture = fixture()
        val response = response(fixture)
        val api = requireNotNull(response.catalog)
        val overlay = fixture.catalog.localizedTextByLanguage.getValue(LanguageTag.FRENCH).let {
            CatalogLanguageOverlay(language = it.language, overlayVersion = it.overlayVersion, speciesNames = it.speciesNames,
                localizedCapabilities = it.localizedCapabilities + (LocalizedTextCapability.TYPE_NAMES to
                    LocalizedCapabilityState.unavailable(CapabilityStatus.NOT_FOUND, 1)))
        }
        val unresolved = fixture.catalog.copy(typesById = mapOf(2 to TypeRecord(2, CatalogField.notFound("fixture"))),
            localization = CatalogLocalization(fixture.catalog.languageManifest, mapOf(LanguageTag.FRENCH to overlay)))
        val type = TypeView(2, "unexpected API type", null, null, null)
        val observed = OfficialMatrixApiObservations.observe(unresolved, response.copy(catalog = api.copy(types = listOf(type, type))))
        val measurements = observed.getAsJsonObject("measurements")
        val types = measurements.getAsJsonObject("typeSemantics")
        assertEquals(1, types.get("typesChecked").asInt)
        assertEquals(1, types.get("unresolvedTypes").asInt)
        assertEquals(1, types.get("mismatchedTypes").asInt)
        assertEquals(1, measurements.getAsJsonObject("projectionIsolation").get("fallbackFields").asInt)
    }

    @Test fun comparesTypesAgainstProjectionInsteadOfAgainstThemselves() {
        val fixture = fixture()
        val response = response(fixture)
        val api = requireNotNull(response.catalog)
        val overlay = fixture.catalog.localizedTextByLanguage.getValue(LanguageTag.FRENCH).let {
            CatalogLanguageOverlay(language = it.language, overlayVersion = it.overlayVersion, speciesNames = it.speciesNames,
                typeNames = mapOf(2 to CatalogField.available("fixture type")),
                localizedCapabilities = it.localizedCapabilities + (LocalizedTextCapability.TYPE_NAMES to LocalizedCapabilityState.available(1)))
        }
        val catalog = fixture.catalog.copy(typesById = mapOf(2 to TypeRecord(2, CatalogField.notApplicable("overlay"),
            semanticRole = CatalogField.available(TypeSemanticRole.NORMAL))),
            localization = CatalogLocalization(fixture.catalog.languageManifest, mapOf(LanguageTag.FRENCH to overlay)))
        val type = TypeView(2, "fixture type", null, null, null)
        val cases = listOf(listOf(type) to 0, listOf(type, type) to 1, emptyList<TypeView>() to 1,
            listOf(type.copy(name = "different API value")) to 1, listOf(type.copy(name = "")) to 1)
        for ((rows, expected) in cases) {
            val observed = OfficialMatrixApiObservations.observe(catalog, response.copy(catalog = api.copy(types = rows)))
            val types = observed.getAsJsonObject("measurements").getAsJsonObject("typeSemantics")
            assertEquals(1, types.get("typesChecked").asInt)
            assertEquals(0, types.get("unresolvedTypes").asInt)
            assertEquals(expected, types.get("mismatchedTypes").asInt)
        }
    }

    private fun response(fixture: Fixture): BootstrapView = Gson().fromJson(
        capture(fixture).getAsJsonObject("bootstrap").get("response"), BootstrapView::class.java,
    )

    @Test fun bindsLogicalDigestToTheCatalogActuallyReturnedByRestore() {
        val fixture = fixture()
        val changed = fixture.catalog.copy(diagnostics = listOf("changed private working copy"))
        var opens = 0
        val observed = capture(fixture, CatalogDatabaseFactory { file ->
            opens++
            if (opens == 2) {
                CatalogCache(file.parentFile, JdbcTestCatalogDatabaseFactory).write(
                    changed, CatalogSourceMetadata.direct("synthetic", 512, "FIXTURE"), CatalogWriteProgress.complete(),
                )
            }
            JdbcTestCatalogDatabaseFactory.open(file)
        })
        val logical = observed.getAsJsonObject("catalogLogicalDigest")
        assertNotNull("capture must include the exact restored catalog digest", logical)
        assertEquals(CatalogLogicalDigest.version, logical.get("version").asInt)
        assertEquals(CatalogLogicalDigest.sha256(changed), logical.get("sha256").asString)
        assertNotEquals(CatalogLogicalDigest.sha256(fixture.catalog), logical.get("sha256").asString)
        assertEquals("capture must not read the repository a second time", 2, opens)
        assertEquals(fixture.expected.cacheSha256, digest(fixture.source))
    }

    @Test fun explicitlyCapturesDiscoveredKnowledgeMode() {
        val envelope = capture(fixture()).getAsJsonObject("bootstrap")
        val provenance = envelope.getAsJsonObject("captureProvenance")
        assertTrue("knowledge mode must be recorded", provenance.has("knowledgeMode"))
        assertEquals("DISCOVERED", provenance.get("knowledgeMode").asString)
        assertEquals("DISCOVERED", envelope.getAsJsonObject("response")
            .getAsJsonObject("state").getAsJsonObject("settings").get("knowledgeMode").asString)
        assertEquals(CatalogLogicalDigest.version, provenance.get("catalogLogicalDigestVersion").asInt)
    }

    @Test fun refusesWrongDigestBeforeOpeningDatabase() {
        val fixture = fixture()
        val opens = AtomicInteger()
        val factory = CatalogDatabaseFactory { file -> opens.incrementAndGet(); JdbcTestCatalogDatabaseFactory.open(file) }
        assertThrows(IllegalArgumentException::class.java) {
            capture(fixture.copy(expected = fixture.expected.copy(cacheSha256 = "0".repeat(64))), factory)
        }
        assertEquals(0, opens.get())
        assertEquals(fixture.expected.cacheSha256, digest(fixture.source))
    }

    @Test fun rejectsWrongEmbeddedIdentityWithoutInvalidatingSourceCache() {
        val fixture = fixture()
        val other = "2".repeat(64)
        val renamed = fixture.source.resolveSibling("$other.sqlite")
        Files.copy(fixture.source, renamed)
        assertThrows(IllegalArgumentException::class.java) {
            capture(fixture.copy(source = renamed, expected = fixture.expected.copy(romSha256 = other)))
        }
        assertEquals(fixture.expected.cacheSha256, digest(fixture.source))
        assertEquals(fixture.expected.cacheSha256, digest(renamed))
    }

    @Test fun rejectsStaleSchemaWithoutMigratingOrDeletingSourceCache() {
        val fixture = fixture()
        JdbcTestCatalogDatabaseFactory.open(fixture.source.toFile()).use { db ->
            db.execute("UPDATE catalog_metadata SET parser_schema_version = ?", listOf(CatalogSchema.parserSchemaVersion - 1))
        }
        val stale = fixture.copy(expected = fixture.expected.copy(cacheSha256 = digest(fixture.source)))
        assertThrows(IllegalArgumentException::class.java) { capture(stale) }
        assertEquals(stale.expected.cacheSha256, digest(stale.source))
    }

    @Test fun rejectsFamilyLanguageCodecAndVersionMismatch() {
        val fixture = fixture()
        val mismatches = listOf(
            fixture.expected.copy(family = EngineFamily.CRYSTAL),
            fixture.expected.copy(language = "en"),
            fixture.expected.copy(codecId = "other-codec"),
            fixture.expected.copy(codecVersion = 2),
        )
        mismatches.forEachIndexed { index, expected ->
            assertThrows(IllegalArgumentException::class.java) {
                capture(fixture.copy(working = fixture.working.resolveSibling("mismatch-$index"), expected = expected))
            }
        }
        assertEquals(fixture.expected.cacheSha256, digest(fixture.source))
    }

    @Test fun rejectsMissingSourceWithoutCreatingDatabase() {
        val fixture = fixture()
        val missing = fixture.source.resolveSibling("3".repeat(64) + ".sqlite")
        assertThrows(IllegalArgumentException::class.java) { capture(fixture.copy(source = missing)) }
        assertFalse(Files.exists(missing))
        assertFalse(Files.exists(fixture.working))
    }

    @Test fun rejectsSidecarsBeforeOpeningDatabase() {
        val fixture = fixture()
        val sidecar = fixture.source.resolveSibling(fixture.source.fileName.toString() + "-wal")
        Files.write(sidecar, byteArrayOf(1, 2, 3))
        val opens = AtomicInteger()
        val factory = CatalogDatabaseFactory { file -> opens.incrementAndGet(); JdbcTestCatalogDatabaseFactory.open(file) }
        assertThrows(IllegalArgumentException::class.java) { capture(fixture, factory) }
        assertEquals(0, opens.get())
        assertArrayEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(sidecar))
        assertEquals(fixture.expected.cacheSha256, digest(fixture.source))
    }

    @Test fun rejectsExistingWorkingDirectoryWithoutOverwrite() {
        val fixture = fixture()
        Files.createDirectory(fixture.working)
        val marker = fixture.working.resolve("owned-by-other.txt")
        Files.write(marker, "preserve".toByteArray(Charsets.UTF_8))
        assertThrows(IllegalArgumentException::class.java) { capture(fixture) }
        assertEquals("preserve", String(Files.readAllBytes(marker), Charsets.UTF_8))
        assertEquals(fixture.expected.cacheSha256, digest(fixture.source))
    }

    @Test fun rejectsInvalidIdentitiesBeforeFilesystemAccess() {
        val fixture = fixture()
        for (identity in listOf("../outside", SHA.uppercase().replace('1', 'A'), "")) {
            assertThrows(IllegalArgumentException::class.java) {
                capture(fixture.copy(expected = fixture.expected.copy(romSha256 = identity)))
            }
        }
        assertFalse(Files.exists(fixture.working))
    }

    @Test fun embedsEveryRunBindingInsideTheHashedBootstrapEnvelope() {
        val observed = capture(fixture())
        val envelope = observed.getAsJsonObject("bootstrap")
        val binding = envelope.getAsJsonObject("captureProvenance").getAsJsonObject("binding")
        assertEquals(setOf("sourceCommit", "sourceSha256", "reportSha256", "receiptSha256", "generatorSha256"), binding.keySet())
        assertEquals(BINDING.sourceCommit, binding.get("sourceCommit").asString)
        assertEquals(BINDING.sourceSha256, binding.get("sourceSha256").asString)
        assertEquals(BINDING.reportSha256, binding.get("reportSha256").asString)
        assertEquals(BINDING.receiptSha256, binding.get("receiptSha256").asString)
        assertEquals(BINDING.generatorSha256, binding.get("generatorSha256").asString)
        assertFalse(envelope.has("binding"))
        assertFalse(envelope.getAsJsonObject("response").has("captureProvenance"))
    }

    @Test fun rejectsMalformedRunBindingsBeforeCopyOrDatabaseAccess() {
        val fixture = fixture()
        val bad = listOf(
            BINDING.copy(sourceCommit = "a".repeat(39)),
            BINDING.copy(sourceSha256 = "A".repeat(64)),
            BINDING.copy(reportSha256 = ""),
            BINDING.copy(receiptSha256 = "f".repeat(63)),
            BINDING.copy(generatorSha256 = "not-a-digest"),
        )
        bad.forEach { binding ->
            assertThrows(IllegalArgumentException::class.java) {
                OfficialMatrixApiCapture.capture(fixture.source, fixture.working, fixture.expected,
                    CatalogDatabaseFactory { error("invalid binding must not open a database") }, binding)
            }
        }
        assertFalse(Files.exists(fixture.working))
        assertEquals(fixture.expected.cacheSha256, digest(fixture.source))
    }

    private fun capture(fixture: Fixture, factory: CatalogDatabaseFactory = JdbcTestCatalogDatabaseFactory): JsonObject =
        OfficialMatrixApiCapture.capture(fixture.source, fixture.working, fixture.expected, factory, BINDING)

    private fun fixture(): Fixture {
        val root = temporary.newFolder().toPath().toRealPath()
        val language = LanguageTag.FRENCH
        val manifest = RomLanguageManifest(
            defaultLanguage = language,
            projections = listOf(RomLanguageProjection(language, "fixture-fr", 1,
                LocalizedTableLayout(), emptyList(), LanguageResolutionStatus.RESOLVED)),
            status = LanguageResolutionStatus.RESOLVED,
        )
        val overlay = CatalogLanguageOverlay(
            language = language,
            overlayVersion = 7,
            localizedCapabilities = LocalizedTextCapability.entries.associateWith {
                when (it) {
                    LocalizedTextCapability.SPECIES_NAMES -> LocalizedCapabilityState.available(1)
                    LocalizedTextCapability.SPECIES_DESCRIPTIONS -> LocalizedCapabilityState.unavailable(CapabilityStatus.NOT_FOUND, 1)
                    else -> LocalizedCapabilityState.unavailable(CapabilityStatus.NOT_APPLICABLE, 0, 1.0)
                }
            },
            speciesNames = mapOf(1 to CatalogField.available("Bulbizarre")),
        )
        val catalog = ParsedCatalog(
            romSha256 = SHA, romCrc32 = "12345678", family = EngineFamily.EMERALD, platform = Platform.GBA,
            speciesById = mapOf(1 to SpeciesRecord(
                id = 1, dexNumber = CatalogField.available(1), name = CatalogField.notApplicable("overlay"),
                typeIds = CatalogField.notFound("fixture"), baseStats = CatalogField.notFound("fixture"),
                sprite = CatalogField.notFound("fixture"),
            )),
            localization = CatalogLocalization(manifest, mapOf(language to overlay)),
        )
        val cache = CatalogCache(root.resolve("input").toFile(), JdbcTestCatalogDatabaseFactory)
        cache.write(catalog, CatalogSourceMetadata.direct("synthetic", 512, "FIXTURE"), CatalogWriteProgress.complete())
        val source = cache.fileFor(SHA).toPath()
        return Fixture(source, root.resolve("capture"), MatrixCacheControl(SHA, digest(source), EngineFamily.EMERALD, "fr", "fixture-fr", 1), catalog)
    }

    private data class Fixture(val source: Path, val working: Path, val expected: MatrixCacheControl, val catalog: ParsedCatalog)
    private fun digest(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(it) }
    private companion object {
        fun pngAsset() = PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        const val SHA = "1111111111111111111111111111111111111111111111111111111111111111"
        val BINDING = MatrixG3Binding("a".repeat(40), "b".repeat(64), "c".repeat(64), "d".repeat(64), "e".repeat(64))
    }
}
