package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.catalog.CatalogDatabase
import com.darkaxt.dualdex.catalog.CatalogDatabaseFactory
import com.darkaxt.dualdex.catalog.CatalogRow
import com.darkaxt.dualdex.catalog.CatalogSchema
import com.enrpau.dualscreendex.parser.catalog.TypeSemanticRole
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import com.enrpau.dualscreendex.companion.battle.AppliedDamageCondition
import com.enrpau.dualscreendex.companion.battle.SemanticProof
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicKind
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.CatalogLanguageOverlay
import com.enrpau.dualscreendex.parser.catalog.CatalogLocalization
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.LocalizedCapabilityState
import com.enrpau.dualscreendex.parser.catalog.LocalizedTextCapability
import com.enrpau.dualscreendex.parser.catalog.LocalMapAssetRenderer
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapNameDisposition
import com.enrpau.dualscreendex.companion.api.LocalMapView
import com.enrpau.dualscreendex.parser.catalog.MapLighting
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.catalog.defaultTextProjection
import com.enrpau.dualscreendex.parser.catalog.textProjection
import com.enrpau.dualscreendex.parser.io.LoadedRom
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.LocalizedTableLayout
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.language.RomLanguageProjection
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.util.Comparator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class WorldMapCatalogApiRealControlTest {
    @Test
    fun westernOfficialGen3DiagnosticFifteenControls() {
        // Optional Task416 semantic mode uses this SAME capture, never a second original selector.
        assumeTrue("set DUALDEX_WESTERN_GEN3_DIAGNOSTIC=1 for the fifteen-control capture; semantic mode additionally requires its pinned fixture opt-in",
            System.getenv("DUALDEX_WESTERN_GEN3_DIAGNOSTIC") != null)
        WesternGen3BaselineCapture.run(JdbcTestCatalogDatabaseFactory)
    }

    @Test
    fun westernOfficialGen1BaselineTenControls() {
        assumeTrue("set DUALDEX_WESTERN_GEN1_DIAGNOSTIC=1 for the ten-control diagnostic only",
            System.getenv("DUALDEX_WESTERN_GEN1_DIAGNOSTIC") != null)
        WesternGen1BaselineCapture.run(JdbcTestCatalogDatabaseFactory)
    }

    @Test
    fun westernOfficialGen1SemanticTenControls() {
        val optIn = System.getenv("DUALDEX_WESTERN_GEN1_SEMANTIC_ACCEPTANCE")
        assumeTrue("set DUALDEX_WESTERN_GEN1_SEMANTIC_ACCEPTANCE=1 with separately pinned private Gen I evidence", optIn != null)
        WesternGen1SemanticCapture.run(JdbcTestCatalogDatabaseFactory)
    }

    @Test
    fun westernOfficialGen2BaselineTenControls() {
        assumeTrue("set DUALDEX_WESTERN_MANIFEST for the ten-control diagnostic baseline",
            !System.getenv("DUALDEX_WESTERN_MANIFEST").isNullOrBlank())
        WesternGen2BaselineCapture.run(JdbcTestCatalogDatabaseFactory)
    }

    @Test
    fun westernOfficialGen2SemanticTenControls() {
        val optIn = System.getenv("DUALDEX_WESTERN_SEMANTIC_ACCEPTANCE")
        assumeTrue("set DUALDEX_WESTERN_SEMANTIC_ACCEPTANCE=1 for the independently ratified ten-control gate", optIn != null)
        require(optIn == "1") { "Western semantic opt-in must be exactly 1" }
        WesternGen2BaselineCapture.runSemantic(JdbcTestCatalogDatabaseFactory)
    }

    @Test
    fun westernSemanticOracleParsesSyntheticTenControlMetadata() {
        val (proof, manifest) = westernSyntheticOracle()
        val controls = WesternGen2SemanticOracle.parseMetadata(proof.toString(), manifest)
        assertEquals(10, controls.size)
        assertEquals(690, controls.values.sumOf { it.names.size })
        assertEquals(2440, controls.values.sumOf { it.references.size })
        assertTrue(controls.values.all { it.names.getValue(5) == "SYNTHETIC FIVE" })
    }

    @Test
    fun westernSemanticOracleRejectsMissingDuplicateOrWrongIdentity() {
        val mutations: List<(JsonObject) -> Unit> = listOf(
            { it.getAsJsonArray("controls").remove(0) },
            { it.getAsJsonArray("controls").set(1, it.getAsJsonArray("controls")[0].deepCopy()) },
            { it.getAsJsonArray("controls")[0].asJsonObject.addProperty("language", "ja") },
            { it.getAsJsonArray("controls")[0].asJsonObject.addProperty("family", "YELLOW") },
            { it.getAsJsonArray("controls")[0].asJsonObject.addProperty("sha256", "f".repeat(64)) },
            { it.getAsJsonArray("controls")[0].asJsonObject.addProperty("status", "NOT_PROVED") },
            { it.addProperty("status", "DIAGNOSTIC_CAPTURE_COMPLETE") },
            { it.addProperty("contractSha256", "0".repeat(64)) },
        )
        mutations.forEachIndexed { index, mutate ->
            val (proof, manifest) = westernSyntheticOracle()
            mutate(proof)
            assertTrue("identity mutation $index", runCatching { WesternGen2SemanticOracle.parseMetadata(proof.toString(), manifest) }.isFailure)
        }
        val (proof, manifest) = westernSyntheticOracle()
        assertTrue(runCatching { WesternGen2SemanticOracle.parseMetadata(proof.toString(), manifest.drop(1)) }.isFailure)
        assertTrue(runCatching { WesternGen2SemanticOracle.parseMetadata(proof.toString(), listOf(manifest[1]) + manifest.drop(1)) }.isFailure)
    }

    @Test
    fun westernSemanticOracleRejectsUnprovedBlankDuplicateAndWrongDomainLabels() {
        val mutations: List<(JsonObject) -> Unit> = listOf(
            { it.getAsJsonArray("items")[0].asJsonObject.addProperty("status", "OBSERVED") },
            { it.getAsJsonArray("items")[0].asJsonObject.remove("label") },
            { it.getAsJsonArray("items")[0].asJsonObject.addProperty("label", " ") },
            { it.getAsJsonArray("items")[0].asJsonObject.addProperty("label", 123) },
            { it.getAsJsonArray("items").add(it.getAsJsonArray("items")[0].deepCopy()) },
            { it.getAsJsonArray("items").remove(2) }, // ID 5, not an acceptable sparse oracle
            { it.getAsJsonArray("items")[0].asJsonObject.addProperty("itemId", "2") },
            { it.getAsJsonArray("requestedIds").remove(2) },
            { it.getAsJsonArray("requestedIds").add(5) },
            { it.getAsJsonArray("requestedIds").set(0, JsonPrimitive(3)) }, // same count, wrong domain
        )
        mutations.forEachIndexed { index, mutate ->
            val (proof, manifest) = westernSyntheticOracle()
            mutate(proof.getAsJsonArray("controls")[0].asJsonObject)
            assertTrue("label mutation $index", runCatching { WesternGen2SemanticOracle.parseMetadata(proof.toString(), manifest) }.isFailure)
        }
    }

    @Test
    fun westernSemanticOracleRejectsChangedOriginalReferenceDomain() {
        val mutations: List<(JsonObject) -> Unit> = listOf(
            { it.addProperty("referenceCount", 224) },
            { it.getAsJsonArray("references").remove(0) },
            { it.getAsJsonArray("references").set(1, it.getAsJsonArray("references")[0].deepCopy()) },
            { it.getAsJsonArray("references")[0].asJsonObject.getAsJsonObject("reference").addProperty("itemId", 255) },
            { it.getAsJsonArray("references")[0].asJsonObject.getAsJsonObject("reference").addProperty("quantity", -1) },
            { it.getAsJsonArray("references")[0].asJsonObject.getAsJsonObject("reference").remove("collectionFlag") },
        )
        mutations.forEachIndexed { index, mutate ->
            val (proof, manifest) = westernSyntheticOracle()
            mutate(proof.getAsJsonArray("controls")[0].asJsonObject)
            assertTrue("reference mutation $index", runCatching { WesternGen2SemanticOracle.parseMetadata(proof.toString(), manifest) }.isFailure)
        }
    }

    @Test
    fun westernSemanticOracleRejectsMalformedJsonAndWrongPinnedHash() {
        val (proof, manifest) = westernSyntheticOracle()
        for (text in listOf("", "{", proof.toString() + " trailing", "{\"controls\":[],\"controls\":[]}", "{unquoted:true}")) {
            assertTrue(runCatching { WesternGen2SemanticOracle.parseMetadata(text, manifest) }.isFailure)
        }
        // Even structurally valid metadata cannot replace the reviewed full-proof bytes.
        assertTrue(runCatching { WesternGen2SemanticOracle.decodePinned(proof.toString().toByteArray(), manifest) }.isFailure)
    }

    @Test
    fun westernSemanticOracleRejectsMissingOrOversizedEvidence() {
        val (_, manifest) = westernSyntheticOracle()
        for (path in listOf(null, "", " ", "western-synthetic-oracle-that-does-not-exist.json")) {
            assertTrue(runCatching { WesternGen2SemanticOracle.readPinned(path, manifest) }.isFailure)
        }
        for (bytes in listOf(ByteArray(0), ByteArray(WesternGen2SemanticOracle.maxProofBytes + 1))) {
            assertTrue(runCatching { WesternGen2SemanticOracle.decodePinned(bytes, manifest) }.isFailure)
        }
    }

    @Test
    fun westernRequiredNamesAcceptsExactCompleteOracle() {
        assertTrue(westernRequiredOutcomes().values.all { it.isSuccess })
    }

    @Test
    fun westernRequiredNamesRejectsMissingIdFiveAcrossBoundaries() {
        for (stage in listOf("producer", "overlay", "sqlite.overlay")) {
            val results = westernRequiredOutcomes(changedStage = stage, changedNames = mapOf(1 to CatalogField.available("SYNTHETIC ONE")))
            assertEquals(setOf("$stage.denominator", "$stage.required-names"), results.filterValues { it.isFailure }.keys)
        }
        for (stage in listOf("projection", "sqlite.projection", "api")) {
            val outcomes = linkedMapOf<String, Result<Unit>>()
            WesternGen2BaselineCapture.checkRequiredProjectedNames(mapOf(1 to "SYNTHETIC ONE", 5 to "SYNTHETIC FIVE"),
                mapOf(1 to listOf("SYNTHETIC ONE"))) { boundary, assertion ->
                outcomes["$stage.$boundary"] = runCatching(assertion)
            }
            assertEquals(setOf("$stage.denominator", "$stage.required-names"), outcomes.filterValues { it.isFailure }.keys)
        }
    }

    @Test
    fun westernRequiredNamesRejectsWrongValueAcrossBoundaries() {
        for (stage in listOf("producer", "overlay", "sqlite.overlay")) {
            val results = westernRequiredOutcomes(changedStage = stage, changedNames = mapOf(
                1 to CatalogField.available("SYNTHETIC ONE"), 5 to CatalogField.available("WRONG SYNTHETIC")))
            assertEquals(setOf("$stage.required-names"), results.filterValues { it.isFailure }.keys)
        }
        for (stage in listOf("projection", "sqlite.projection", "api")) {
            val outcomes = linkedMapOf<String, Result<Unit>>()
            WesternGen2BaselineCapture.checkRequiredProjectedNames(mapOf(1 to "SYNTHETIC ONE", 5 to "SYNTHETIC FIVE"),
                mapOf(1 to listOf("SYNTHETIC ONE"), 5 to listOf("SYNTHETIC FIVE", "WRONG SYNTHETIC"))) { boundary, assertion ->
                outcomes["$stage.$boundary"] = runCatching(assertion)
            }
            assertEquals(setOf("$stage.required-names"), outcomes.filterValues { it.isFailure }.keys)
        }
    }

    @Test
    fun westernRequiredNamesRejectsCollapsedDenominators() {
        val results = westernRequiredOutcomes(requested = setOf(1), state = LocalizedCapabilityState.available(1))
        assertEquals(setOf("requested-domain", "item-capability.expected", "item-capability.covered"),
            results.filterValues { it.isFailure }.keys)
    }

    @Test
    fun westernRequiredNamesRejectsUnavailableAndBlankValues() {
        assertTrue(runCatching {
            CatalogField(CapabilityStatus.AMBIGUOUS, "SYNTHETIC FIVE", listOf("synthetic conflict"))
        }.exceptionOrNull() is IllegalArgumentException)
        for (field in listOf(CatalogField.available(" "), CatalogField.notFound<String>("synthetic unavailable"),
            CatalogField<String>(CapabilityStatus.AMBIGUOUS, reasons = listOf("synthetic conflict")))) {
            val results = westernRequiredOutcomes(changedStage = "producer", changedNames = mapOf(1 to CatalogField.available("SYNTHETIC ONE"), 5 to field))
            assertEquals(setOf("producer.required-names"), results.filterValues { it.isFailure }.keys)
        }
        assertTrue(westernRequiredOutcomes(state = westernSparseStates().getValue(LocalizedTextCapability.ITEM_NAMES))
            .getValue("item-capability.status").isFailure)
    }

    private fun westernRequiredOutcomes(
        changedStage: String? = null,
        changedNames: Map<Int, CatalogField<String>> = emptyMap(),
        requested: Set<Int> = setOf(1, 5),
        state: LocalizedCapabilityState = LocalizedCapabilityState.available(2),
    ): Map<String, Result<Unit>> {
        val expected = mapOf(1 to "SYNTHETIC ONE", 5 to "SYNTHETIC FIVE")
        val fields = listOf("producer", "overlay", "sqlite.overlay").associateWith {
            if (it == changedStage) changedNames else expected.mapValues { entry -> CatalogField.available(entry.value) }
        }
        return linkedMapOf<String, Result<Unit>>().also { outcomes ->
            WesternGen2BaselineCapture.checkRequiredItemObservations(expected, requested, fields, state) { stage, assertion ->
                check(stage !in outcomes)
                outcomes[stage] = runCatching(assertion)
            }
        }
    }

    /** Fabricated metadata only: no ROM, private proof payload, baseline label or production decoder. */
    private fun westernSyntheticOracle(): Pair<JsonObject, List<JsonObject>> {
        val manifest = mutableListOf<JsonObject>()
        val controls = JsonArray()
        for (language in listOf("en", "fr", "de", "it", "es")) for (family in listOf("GOLD_SILVER", "CRYSTAL")) {
            val sha = (manifest.size + 1).toString(16).padStart(64, '0')
            manifest += JsonObject().apply {
                addProperty("language", language); addProperty("family", family); addProperty("sha256", sha)
                addProperty("file", "synthetic-not-a-rom"); addProperty("size", 2097152)
            }
            val ids = WesternGen2SemanticOracle.requestedIds(family).sorted()
            val referenceCount = if (family == "CRYSTAL") 263 else 225
            controls.add(JsonObject().apply {
                addProperty("language", language); addProperty("family", family); addProperty("sha256", sha)
                addProperty("status", "PROVED_REFERENCE_SCOPED_ONLY"); addProperty("referenceCount", referenceCount)
                add("requestedIds", JsonArray().apply { ids.forEach { add(it) } })
                add("items", JsonArray().apply {
                    ids.forEach { id -> add(JsonObject().apply {
                        addProperty("itemId", id); addProperty("status", "PROVED")
                        addProperty("label", if (id == 5) "SYNTHETIC FIVE" else "SYNTHETIC $id")
                    }) }
                })
                add("references", JsonArray().apply {
                    val referencedIds = ids
                    repeat(referenceCount) { index -> add(JsonObject().apply {
                        add("reference", JsonObject().apply {
                            addProperty("poiKey", "synthetic/ref/$index"); addProperty("kind", "VISIBLE_OBJECT")
                            addProperty("itemId", referencedIds[index % referencedIds.size])
                            for (field in listOf("operandOffset", "baseAreaId", "mapGroupTable", "mapGroupBank", "mapHeader",
                                "attributesBank", "attributes", "scriptsBank", "eventsRoot", "eventRow", "pointerField",
                                "tileX", "tileY", "collectionFlag")) addProperty(field, 1)
                            addProperty("quantity", 1)
                        })
                    }) }
                })
            })
        }
        return JsonObject().apply {
            addProperty("status", "PROVED_REFERENCE_SCOPED_ONLY")
            addProperty("contractSha256", WesternGen2SemanticOracle.contractSha256)
            add("controls", controls)
        } to manifest
    }

    @Test
    fun westernBaselineObserverRetainsTheNormalOriginalSession() {
        val rom = RomImage(ByteArray(0x200))
        val (context, session) = WesternGen2BaselineCapture.observe(rom)
        assertTrue(context.javaClass.simpleName == "CatalogAnalysisContext")
        assertTrue(session.rom === rom)
        assertTrue(session.gen2ItemReferences.isEmpty())
    }

    @Test
    fun westernSparseOverlayKeepsUnavailableRequestedNamesDiagnostic() {
        val names = westernSparseProducer()
        val overlay = CatalogLanguageOverlay(LanguageTag.ENGLISH, 1, westernSparseStates(),
            itemNames = mapOf(1 to CatalogField.available("SYNTHETIC")))
        val results = westernSparseOutcomes(names, overlay.localizedCapabilities, overlay.itemNames)
        assertEquals(emptySet<String>(), results.filterValues { it.isFailure }.keys)
        assertEquals(22, results.size)
        assertEquals(setOf(5), WesternGen2BaselineCapture.missingRequestedItemIds(setOf(1, 5), names))
        assertEquals(2, overlay.localizedCapabilities.getValue(LocalizedTextCapability.ITEM_NAMES).expectedRecords)
        assertEquals(CapabilityStatus.PARTIAL, overlay.localizedCapabilities.getValue(LocalizedTextCapability.ITEM_NAMES).status)
    }

    @Test
    fun westernSparseOverlayRejectsMissingProducerAndCapabilityDenominators() {
        val results = westernSparseOutcomes(westernSparseProducer() - 5, westernSparseStates(expected = 1),
            mapOf(1 to CatalogField.available("SYNTHETIC")))
        assertEquals(setOf("items.producer-denominator", "items.capability-denominator"),
            results.filterValues { it.isFailure }.keys)
        assertTrue(results.getValue("items.available-name-parity").isSuccess)
    }

    @Test
    fun westernSparseOverlayStillChecksEveryCountAndNameAfterInventoryFailure() {
        val states = westernSparseStates() - LocalizedTextCapability.SPECIES_NAMES +
            (LocalizedTextCapability.ITEM_NAMES to LocalizedCapabilityState.notFound("fault-injected coverage", 2))
        val results = westernSparseOutcomes(westernSparseProducer(), states,
            mapOf(1 to CatalogField.available("WRONG SYNTHETIC")))
        assertEquals(22, results.size)
        assertEquals(setOf("capabilities.inventory", "capability.SPECIES_NAMES.counts",
            "items.capability-covered-count", "items.available-name-parity"), results.filterValues { it.isFailure }.keys)
        assertTrue(results.getValue("capability.POI_TEXT.counts").isSuccess)
        assertTrue(results.getValue("items.capability-denominator").isSuccess)
    }

    @Test
    fun westernSparseOverlayRejectsAmbiguousUnexpectedAndUnavailableEntries() {
        val ambiguous = westernSparseProducer() + (5 to CatalogField<String>(CapabilityStatus.AMBIGUOUS, null, listOf("synthetic conflict")))
        val proper = westernSparseOutcomes(ambiguous, westernSparseStates(), mapOf(1 to CatalogField.available("SYNTHETIC")))
        assertTrue(proper.values.all { it.isSuccess })
        for (entry in listOf(5 to CatalogField.available("UNAUTHORIZED"), 7 to CatalogField.available("UNREQUESTED"),
            5 to CatalogField.available(" "), 5 to CatalogField.notFound<String>("unavailable entries are not sparse names"))) {
            val results = westernSparseOutcomes(ambiguous, westernSparseStates(expected = 2, covered = 2),
                mapOf(1 to CatalogField.available("SYNTHETIC"), entry))
            assertTrue(results.getValue("items.overlay-sparse-keys").isFailure)
            assertTrue(results.getValue("items.available-name-parity").isFailure)
            if (entry.second.value.isNullOrBlank()) assertTrue(results.getValue("items.overlay-available-fields").isFailure)
        }
        assertEquals(setOf(5), WesternGen2BaselineCapture.missingRequestedItemIds(setOf(1, 5), ambiguous))
    }

    @Test
    fun westernSparseOverlayAllowsEmptyAndAllUnavailableDiagnosticDomains() {
        for (requested in listOf(emptySet(), setOf(5))) {
            val names = requested.associateWith { CatalogField.notFound<String>("synthetic missing label") }
            val results = westernSparseOutcomes(names, westernSparseStates(expected = requested.size, covered = 0), emptyMap(), requested)
            assertTrue(results.values.all { it.isSuccess })
            assertEquals(requested, WesternGen2BaselineCapture.missingRequestedItemIds(requested, names))
        }
    }

    private fun westernSparseProducer() = mapOf(1 to CatalogField.available("SYNTHETIC"),
        5 to CatalogField.notFound<String>("synthetic requested name unavailable; not a semantic waiver"))

    private fun westernSparseStates(expected: Int = 2, covered: Int = 1) = LocalizedTextCapability.entries.associateWith { capability ->
        if (capability != LocalizedTextCapability.ITEM_NAMES) LocalizedCapabilityState.notApplicable("synthetic", 0)
        else LocalizedCapabilityState(
            when {
                expected == 0 -> CapabilityStatus.NOT_APPLICABLE
                covered == 0 -> CapabilityStatus.NOT_FOUND
                covered == expected -> CapabilityStatus.AVAILABLE
                else -> CapabilityStatus.PARTIAL
            }, if (covered == 0) 0.0 else 1.0, covered, expected)
    }

    private fun westernSparseOutcomes(
        names: Map<Int, CatalogField<String>>,
        states: Map<LocalizedTextCapability, LocalizedCapabilityState>,
        overlayNames: Map<Int, CatalogField<String>>,
        requested: Set<Int> = setOf(1, 5),
    ): Map<String, Result<Unit>> = linkedMapOf<String, Result<Unit>>().also { outcomes ->
        WesternGen2BaselineCapture.checkItemObservations(requested, names, states, overlayNames) { stage, assertion ->
            check(stage !in outcomes)
            outcomes[stage] = runCatching(assertion)
        }
    }

    @Test
    fun exactReferenceThemesSurviveCatalogStoreAndApiProjection() {
        themeControls.forEach { control ->
            val configured = System.getenv(control.environmentVariable)
            assumeTrue("set ${control.environmentVariable} to run this exact theme control", !configured.isNullOrBlank())
            val path = Path.of(requireNotNull(configured))
            assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
            val rom = RomImage(Files.readAllBytes(path))
            assertEquals(control.romSha256, rom.sha256)
            val catalog = requireNotNull(CatalogParser.parse(rom).catalog)
            val root = newRoot()
            try {
                val cache = CatalogCache(root.toFile(), JdbcTestCatalogDatabaseFactory)
                cache.write(
                    catalog,
                    CatalogSourceMetadata.direct(path.fileName.toString(), rom.size, "THEME-CONTROL"),
                    CatalogWriteProgress.complete(),
                )
                val reopened = requireNotNull(cache.readComplete(rom.sha256)).catalog
                assertEquals(catalog.theme, reopened.theme)

                val runtime = ProductionCompanionRuntime().apply { loadCatalog(path.fileName.toString(), reopened) }
                val apiTheme = requireNotNull(runtime.bootstrap().catalog).theme
                assertEquals(reopened.theme.method.name, apiTheme.method)
                assertEquals(reopened.theme.assetClasses.sortedBy { it.ordinal }.map { it.name }, apiTheme.assetClasses)
                assertEquals(reopened.theme.contrastCorrected, apiTheme.contrastCorrected)
                assertEquals("#%06x".format(reopened.theme.tokens.field), apiTheme.tokens.field)
                assertEquals("#%06x".format(reopened.theme.tokens.header), apiTheme.tokens.header)
                assertEquals("#%06x".format(reopened.theme.tokens.panel), apiTheme.tokens.panel)
                assertEquals("#%06x".format(reopened.theme.tokens.accent), apiTheme.tokens.accent)
                assertDatabaseIntegrity(cache.fileFor(rom.sha256))
            } finally {
                deleteTree(root)
            }
        }
    }

    @Test
    fun persistedLocalizedOverlaysReopenIntoBootstrapWithoutParserInvocation() {
        val rom = RomImage(ByteArray(0x200) { index -> (index * 31).toByte() })
        val languages = listOf(LanguageTag.ENGLISH, LanguageTag.FRENCH)
        val manifest = RomLanguageManifest(
            defaultLanguage = LanguageTag.ENGLISH,
            projections = languages.map { language ->
                RomLanguageProjection(
                    language = language,
                    codecId = "fixture-${language.value}",
                    codecVersion = 1,
                    localizedTables = LocalizedTableLayout(),
                    evidence = emptyList(),
                    status = LanguageResolutionStatus.RESOLVED,
                )
            },
            status = LanguageResolutionStatus.RESOLVED,
        )
        fun overlay(language: LanguageTag, version: Long, name: String) = CatalogLanguageOverlay(
            language = language,
            overlayVersion = version,
            localizedCapabilities = LocalizedTextCapability.entries.associateWith { capability ->
                when (capability) {
                    LocalizedTextCapability.SPECIES_NAMES -> LocalizedCapabilityState.available(1)
                    LocalizedTextCapability.SPECIES_DESCRIPTIONS ->
                        LocalizedCapabilityState.notFound("fixture missing", 1)
                    else -> LocalizedCapabilityState.unavailable(
                        CapabilityStatus.NOT_APPLICABLE,
                        expectedRecords = 0,
                        confidence = 1.0,
                    )
                }
            },
            speciesNames = mapOf(1 to CatalogField.available(name)),
        )
        val catalog = ParsedCatalog(
            romSha256 = rom.sha256,
            romCrc32 = rom.crc32,
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            speciesById = mapOf(
                1 to SpeciesRecord(
                    id = 1,
                    dexNumber = CatalogField.available(1),
                    name = CatalogField.notApplicable("stored in language overlay"),
                    typeIds = CatalogField.notFound("fixture"),
                    baseStats = CatalogField.notFound("fixture"),
                    sprite = CatalogField.notFound("fixture"),
                ),
            ),
            localization = CatalogLocalization(
                manifest,
                mapOf(
                    LanguageTag.ENGLISH to overlay(LanguageTag.ENGLISH, 7, "Bulbasaur"),
                    LanguageTag.FRENCH to overlay(LanguageTag.FRENCH, 8, "Bulbizarre"),
                ),
            ),
        )
        val root = newRoot()
        try {
            val cache = CatalogCache(root.toFile(), JdbcTestCatalogDatabaseFactory)
            cache.write(
                catalog,
                CatalogSourceMetadata.direct("fixture.gba", rom.size, "FIXTURE"),
                CatalogWriteProgress.complete(),
            )
            val reopened = requireNotNull(cache.readComplete(rom.sha256)).catalog
            assertEquals("Bulbasaur", reopened.defaultTextProjection().speciesName(1))
            assertEquals("Bulbizarre", reopened.textProjection(LanguageTag.FRENCH)?.speciesName(1))

            val parserInvocations = AtomicInteger()
            val completion = AtomicReference<Result<Unit>?>()
            val completed = CountDownLatch(1)
            ProductionCompanionRuntime(
                catalogRepository = cache,
                parseCatalogWithCancellation = { _, _, _, _ ->
                    parserInvocations.incrementAndGet()
                    error("persisted overlay bootstrap must not invoke the parser")
                },
            ).use { runtime ->
                runtime.load(LoadedRom("fixture.gba", rom)) { result ->
                    completion.set(result)
                    completed.countDown()
                }
                assertTrue("catalog reopen did not complete", completed.await(10, TimeUnit.SECONDS))
                requireNotNull(completion.get()).getOrThrow()

                val bootstrap = runtime.bootstrap()
                assertEquals(0, parserInvocations.get())
                assertTrue(bootstrap.state.catalogReady)
                assertEquals("CACHE_REOPEN", bootstrap.state.loading.phase)
                assertEquals(rom.sha256, bootstrap.catalog?.hash)
                assertEquals("Bulbasaur", bootstrap.catalog?.species?.single()?.name)
                assertEquals("en", bootstrap.language?.activeLanguage)
                assertEquals(7L, bootstrap.language?.activeOverlayVersion)
                assertEquals(listOf("en", "fr"), bootstrap.language?.projections?.map { it.language })
            }
        } finally {
            deleteTree(root)
        }
    }

    @Test fun nativeContextualAccountingUsesOnlyTheFourReviewedControlExpectations() {
        val scoped = nativeControls.filter { it.contextualMaps != null }
        assertEquals(listOf("ja/GOLD_SILVER", "ja/CRYSTAL", "ko/GOLD", "ko/SILVER"), scoped.map { it.folder })
        assertEquals(listOf(368, 388, 368, 368), scoped.map { it.contextualMaps!!.totalMaps })
        assertEquals(listOf(364, 382, 364, 364), scoped.map { it.contextualMaps!!.staticRequired })
        val four = setOf("local/1401", "local/1402", "local/1403", "local/1404")
        assertEquals(listOf(four, four + setOf("local/1405", "local/1406"), four, four),
            scoped.map { it.contextualMaps!!.contextualKeys })
    }

    @Test fun nativeContextualAccountingAcceptsExactStaticAndContextualPartition() {
        NativeContextualFixture().checkNames()
    }

    @Test fun nativeContextualAccountingReceiptIsScopedAndPreservesActualCounters() {
        val f = NativeContextualFixture()
        val producer = f.checkNames()
        val api = f.checkApi()
        assertEquals(JsonPrimitive(3), producer["totalMaps"])
        assertEquals(JsonPrimitive(2), producer["staticRequired"])
        assertEquals(JsonPrimitive("AVAILABLE"), producer.getAsJsonObject("localMapNames")["status"])
        assertEquals(JsonPrimitive("REOPENED_NUMERIC_KEY_JOIN"), api["dispositionSource"])
        val receipt = NativeContextualMapAssertions.receipt("synthetic", "1".repeat(64), f.expectation,
            producer, producer.deepCopy(), api, "synthetic.sqlite", 1, 0)
        assertEquals(JsonPrimitive("GEN2_CONTEXTUAL_MAP_NAME_DISPOSITION_ONLY"), receipt["scope"])
        assertEquals(JsonPrimitive("NOT_CLAIMED"), receipt["overallNativeSemanticAcceptance"])
        assertEquals(JsonPrimitive(1), receipt["originalParserInvocations"])
        assertEquals(JsonPrimitive(0), receipt["apiReparses"])
        assertEquals(producer, receipt["producer"])
        assertEquals(producer, receipt["sqlite"])
        assertEquals(api, receipt["api"])
        for ((original, reparses) in listOf(0 to 0, 2 to 0, 1 to 1)) {
            org.junit.Assert.assertThrows(AssertionError::class.java) {
                NativeContextualMapAssertions.receipt("synthetic", "1".repeat(64), f.expectation,
                    producer, producer.deepCopy(), api, "synthetic.sqlite", original, reparses)
            }
        }
        org.junit.Assert.assertThrows(AssertionError::class.java) {
            NativeContextualMapAssertions.receipt("synthetic", "1".repeat(64), f.expectation,
                producer, producer.deepCopy().apply { addProperty("totalMaps", 2) }, api, "synthetic.sqlite", 1, 0)
        }
    }

    @Test fun nativeContextualAccountingRejectsMissingOrUnavailableOrdinaryNames() {
        val f = NativeContextualFixture()
        for (badNames in listOf(f.names - "local/0101",
            f.names + ("local/0101" to CatalogField.available(" ")),
            f.names + ("local/0101" to CatalogField.notFound<String>("synthetic missing")))) {
            org.junit.Assert.assertThrows(AssertionError::class.java) { f.checkNames(names = badNames) }
        }
        org.junit.Assert.assertThrows(AssertionError::class.java) {
            f.checkNames(projected = f.projected + ("local/0101" to null))
        }
    }

    @Test fun nativeContextualAccountingRejectsCollapsedOrReplacedInventory() {
        val f = NativeContextualFixture()
        for (badMaps in listOf(f.maps.drop(1), f.maps + f.maps.first(),
            f.maps.map { if (it.key == "local/0101") it.copy(key = "local/0201") else it })) {
            org.junit.Assert.assertThrows(AssertionError::class.java) { f.checkNames(maps = badMaps) }
        }
        for (state in listOf(LocalizedCapabilityState.available(1),
            LocalizedCapabilityState.notFound("synthetic unavailable", 2))) {
            org.junit.Assert.assertThrows(AssertionError::class.java) { f.checkNames(state = state) }
        }
    }

    @Test fun nativeContextualAccountingRejectsWrongExemptionsAndFixedContextLabels() {
        val f = NativeContextualFixture()
        org.junit.Assert.assertThrows(AssertionError::class.java) {
            f.checkNames(maps = f.maps.map { it.copy(nameDisposition =
                if (it.key == "local/0101") LocalMapNameDisposition.CONTEXT_DEPENDENT
                else LocalMapNameDisposition.STATIC_NAME_REQUIRED) })
        }
        org.junit.Assert.assertThrows(AssertionError::class.java) {
            f.checkNames(maps = f.maps.map { if (it.key == "local/1401") it.copy(displayName = "SYNTHETIC FIXED") else it })
        }
        org.junit.Assert.assertThrows(AssertionError::class.java) {
            f.checkNames(names = f.names + ("local/1401" to CatalogField.available("SYNTHETIC FIXED")))
        }
        org.junit.Assert.assertThrows(AssertionError::class.java) {
            f.checkNames(projected = f.projected + ("local/1401" to "SYNTHETIC FIXED"))
        }
    }

    @Test fun nativeContextualAccountingApiPreservesExactInventoryAndOptionalNames() {
        val f = NativeContextualFixture()
        f.checkApi()
        for (badMaps in listOf(f.api.drop(1), f.api + f.api.first(),
            f.api.map { if (it.key == "local/1401") it.copy(displayName = "SYNTHETIC FIXED") else it },
            f.api.map { if (it.key == "local/0101") it.copy(displayName = null) else it },
            f.api.map { if (it.key == "local/0101") it.copy(baseAreaId = 0x202) else it },
            f.api.map { if (it.key == "local/0101") it.copy(gridWidth = 2) else it })) {
            org.junit.Assert.assertThrows(AssertionError::class.java) { f.checkApi(maps = badMaps) }
        }
        org.junit.Assert.assertThrows(AssertionError::class.java) { f.checkApi(covered = 1) }
        org.junit.Assert.assertThrows(AssertionError::class.java) { f.checkApi(expected = 3) }
        org.junit.Assert.assertThrows(AssertionError::class.java) { f.checkApi(status = "PARTIAL") }
    }

    private class NativeContextualFixture {
        val expectation = NativeContextualExpectation(3, setOf("local/1401"))
        val maps = listOf("local/0101" to 0x101, "local/0102" to 0x102, "local/1401" to 0x1401).map { (key, id) ->
            LocalMap(key, null, id, 16, 16, 1, 1, "synthetic/$key",
                if (key == "local/1401") LocalMapNameDisposition.CONTEXT_DEPENDENT else LocalMapNameDisposition.STATIC_NAME_REQUIRED)
        }
        val names = mapOf("local/0101" to CatalogField.available("SYNTHETIC ONE"),
            "local/0102" to CatalogField.available("SYNTHETIC TWO"))
        val projected = maps.associate { it.key to names[it.key]?.value }
        val api = maps.map { LocalMapView(it.key, projected[it.key], it.baseAreaId, it.pixelWidth,
            it.pixelHeight, it.gridWidth, it.gridHeight, "synthetic-image", false) }
        fun checkNames(maps: List<LocalMap> = this.maps, names: Map<String, CatalogField<String>> = this.names,
            projected: Map<String, String?> = this.projected, state: LocalizedCapabilityState = LocalizedCapabilityState.available(2)) =
            NativeContextualMapAssertions.names(expectation, maps, names, projected, state)
        fun checkApi(maps: List<LocalMapView> = api, covered: Int = 2, expected: Int = 2, status: String = "AVAILABLE") =
            NativeContextualMapAssertions.api(expectation, this.maps, projected, maps, status, covered, expected)
    }

    // Separate JUnit cases deliberately attempt all nine exact inputs even when an earlier cell is red.
    @Test fun nativeOfficialJapaneseRedBlue() = assertNativeRoundTrip(nativeControls[0], requireItemNames = true)
    @Test fun nativeOfficialJapaneseYellow() = assertNativeRoundTrip(nativeControls[1], requireItemNames = true)
    @Test fun nativeOfficialJapaneseGoldSilver() = assertNativeRoundTrip(nativeControls[2], requireItemNames = true)
    @Test fun nativeOfficialJapaneseCrystal() = assertNativeRoundTrip(nativeControls[3], requireItemNames = true,
        selectedDirectSign = japaneseCrystalSelectedSign)
    @Test fun nativeOfficialJapaneseRubySapphire() = assertNativeRoundTrip(nativeControls[4], requireItemNames = true)
    @Test fun nativeOfficialJapaneseEmerald() = assertNativeRoundTrip(nativeControls[5], requireItemNames = true)
    @Test fun nativeOfficialJapaneseFireRedLeafGreen() = assertNativeRoundTrip(nativeControls[6], requireItemNames = true)
    @Test fun nativeOfficialKoreanGold() = assertNativeRoundTrip(nativeControls[7], requireDeclaredSigns = true, requireItemNames = true)
    @Test fun nativeOfficialKoreanSilver() = assertNativeRoundTrip(nativeControls[8], requireDeclaredSigns = true, requireItemNames = true)

    @Test fun nativeDescriptionSlotsJapaneseRuby() = assertNativeDescriptionSlots(nativeControls[4], 0x1cdf94)
    @Test fun nativeDescriptionSlotsJapaneseEmerald() = assertNativeDescriptionSlots(nativeControls[5], 0x2ee2d4)
    @Test fun nativeDescriptionSlotsJapaneseFireRed() = assertNativeDescriptionSlots(nativeControls[6], 0x209ed8)

    /** Description-slot gate only; it does not replace or waive the nine-control positive gate. */
    private fun assertNativeDescriptionSlots(control: NativeControl, publicMapRoot: Int) {
        val root = Path.of(requireNotNull(System.getenv("DUALDEX_NATIVE_CONTROLS")) {
            "the exact three-control description-slot gate requires DUALDEX_NATIVE_CONTROLS"
        })
        val path = Files.list(root.resolve(control.folder)).use { paths ->
            paths.filter { Files.isRegularFile(it) }.toList().single()
        }
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(control.sha256, rom.sha256)
        val parsed = CatalogParser.parse(rom)
        assertEquals(SelectionStatus.SELECTED, parsed.analysis.status)
        val catalog = requireNotNull(parsed.catalog)
        val layout = requireNotNull(parsed.layout)
        val before = com.enrpau.dualscreendex.parser.catalog.RecordMaterializers.species(rom, layout)
        val sprites = com.enrpau.dualscreendex.parser.sprite.SpriteMaterializer.pokemon(rom, layout)
        val excluded = (252..276).toSet() // independently compiled/source-correlated exact-control oracle
        fun assertSlots(value: ParsedCatalog) {
            assertEquals((0..411).toSet(), value.speciesById.keys)
            for ((id, record) in value.speciesById) {
                assertEquals(CapabilityStatus.AVAILABLE, record.dexNumber.status)
                assertEquals(if (id == 0) 0 else rom.u16le(publicMapRoot + (id - 1) * 2), record.dexNumber.value)
                assertEquals(before.getValue(id).dexNumber, record.dexNumber)
                assertEquals(before.getValue(id).baseStats, record.baseStats)
                assertEquals(before.getValue(id).typeIds, record.typeIds)
                assertEquals(before.getValue(id).navigable, record.navigable)
                sprites[id]?.let { assertEquals(it, record.sprite.value) }
                assertTrue(record.description.value == null && record.name.value == null)
                if (id in excluded) {
                    assertEquals(CapabilityStatus.NOT_APPLICABLE, record.description.status)
                    assertEquals(CapabilityStatus.NOT_APPLICABLE, record.height.status)
                    assertEquals(CapabilityStatus.NOT_APPLICABLE, record.weight.status)
                } else if (id > 0) {
                    assertTrue(record.description.status != CapabilityStatus.NOT_APPLICABLE)
                    assertEquals(CapabilityStatus.AVAILABLE, record.height.status)
                    assertEquals(CapabilityStatus.AVAILABLE, record.weight.status)
                }
            }
            val overlay = requireNotNull(value.localizedText(LanguageTag.JAPANESE))
            assertEquals((1..411).toSet() - excluded, overlay.speciesDescriptions.keys)
            val state = overlay.localizedCapabilities.getValue(LocalizedTextCapability.SPECIES_DESCRIPTIONS)
            assertEquals(CapabilityStatus.AVAILABLE, state.status)
            assertEquals(386, state.coveredRecords)
            assertEquals(386, state.expectedRecords)
            val text = value.defaultTextProjection()
            // Independent compiled row-1 prose oracle and source-correlated post-overflow row-252 scalars.
            assertEquals("フシギダネ", text.speciesName(1))
            assertTrue(requireNotNull(text.speciesDescription(1)).contains(control.dexFragment))
            assertEquals(7, value.speciesById.getValue(1).height.value)
            assertEquals(69, value.speciesById.getValue(1).weight.value)
            assertEquals("キモリ", text.speciesName(277))
            assertEquals(5, value.speciesById.getValue(277).height.value)
            assertEquals(50, value.speciesById.getValue(277).weight.value)
            assertNativeMoveProseSamples(value, control)
        }
        assertSlots(catalog)
        val semantic = parsed.analysis.capabilities.single { it.capability == RomCapability.POKEDEX_DESCRIPTIONS }
        assertEquals(386, semantic.coveredRecords)
        assertEquals(386, semantic.expectedRecords)
        val cache = CatalogCache(newRoot().toFile(), JdbcTestCatalogDatabaseFactory)
        cache.write(catalog, CatalogSourceMetadata.direct("description-slot-control", rom.size, "NATIVE-CONTROL"), CatalogWriteProgress.complete())
        val reopened = requireNotNull(cache.readComplete(rom.sha256)).catalog
        assertEquals(catalog, reopened)
        assertSlots(reopened)
        assertDatabaseIntegrity(cache.fileFor(rom.sha256))
        val calls = AtomicInteger()
        val completion = AtomicReference<Result<Unit>?>()
        val done = CountDownLatch(1)
        ProductionCompanionRuntime(catalogRepository = cache, parseCatalogWithCancellation = { _, _, _, _ ->
            calls.incrementAndGet(); error("description-slot cache reopen must not parse")
        }).use { runtime ->
            runtime.load(LoadedRom("description-slot-control", rom)) { result -> completion.set(result); done.countDown() }
            assertTrue(done.await(30, TimeUnit.SECONDS))
            requireNotNull(completion.get()).getOrThrow()
            assertEquals(0, calls.get())
            val bootstrap = runtime.bootstrap()
            assertEquals("CACHE_REOPEN", bootstrap.state.loading.phase)
            val api = requireNotNull(bootstrap.catalog)
            assertEquals(reopened.navigableSpecies().map { it.id }.toSet(), api.species.map { it.id }.toSet())
            assertEquals(386, api.species.count { it.description != null })
            assertTrue(api.species.filter { it.id in excluded }.all { it.description == null && it.height == null && it.weight == null })
            val state = requireNotNull(bootstrap.language).projections.single().localizedCapabilities
                .getValue(LocalizedTextCapability.SPECIES_DESCRIPTIONS.name)
            assertEquals(386, state.coveredRecords)
            assertEquals(386, state.expectedRecords)
            assertEquals("AVAILABLE", state.status)
            assertEquals(354, api.moves.count { it.description != null })
            nativeMoveProseHashes(control).forEach { (id, expected) ->
                assertEquals(expected, sha256(requireNotNull(api.moves.single { it.id == id }.description)
                    .toByteArray(StandardCharsets.UTF_8)))
            }
        }
        println("NATIVE_DESCRIPTION_SLOT_GATE ${control.folder} descriptionSlots=PASS coverage=386/386 records=412 publicA=PRESERVED moveProse=354/354")
    }

    // Missing/invalid native witnesses remain synthetic negative tests in MoveDescriptionMaterializerTest.
    // The exact FireRed control is now required positive, never an applicability waiver.
    private fun nativeMoveProseHashes(control: NativeControl): Map<Int, String> =
        if (control.family == EngineFamily.FIRERED_LEAFGREEN) fireRedMoveProseHashes else rubyEmeraldMoveProseHashes

    private fun assertNativeMoveProseSamples(catalog: ParsedCatalog, control: NativeControl) {
        val overlay = requireNotNull(catalog.localizedText(LanguageTag.JAPANESE))
        val state = overlay.localizedCapabilities.getValue(LocalizedTextCapability.MOVE_DESCRIPTIONS)
        assertEquals(CapabilityStatus.AVAILABLE, state.status)
        assertEquals(354, state.expectedRecords)
        assertEquals(354, state.coveredRecords)
        assertEquals((1..354).toSet(), overlay.moveDescriptions.keys)
        nativeMoveProseHashes(control).forEach { (id, expected) ->
            val prose = requireNotNull(catalog.defaultTextProjection().moveDescription(id))
            assertEquals("independent move-prose digest for move $id", expected, sha256(prose.toByteArray(StandardCharsets.UTF_8)))
        }
    }

    private fun assertKoreanDeclaredSigns(catalog: ParsedCatalog): Map<String, String> {
        // Pinned pokegold-kr 7743877: New Bark outdoor attributes 10x9 blocks,
        // BG rows 0=(8,8,0), 2=(3,3,0); independent source text, not parser output.
        val text = catalog.defaultTextProjection()
        val map = catalog.localMaps.maps.single {
            it.gridWidth == 20 && it.gridHeight == 18 && text.localMapName(it.key) == "연두마을"
        }
        return listOf(Triple(0, 8, "이곳은 연두마을"), Triple(2, 3, "공박사 포켓몬 연구소")).associate { (index, xy, expected) ->
            val poi = catalog.localMaps.pois.single { it.key == "${map.key}/bg/$index" }
            assertEquals(xy, poi.tileX)
            assertEquals(xy, poi.tileY)
            assertEquals(map.baseAreaId, poi.baseAreaId)
            assertEquals(com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind.PLACE, poi.kind)
            assertTrue(poi.displayName == null) // prose remains exclusively in the language overlay
            assertEquals(expected, text.poiDisplayName(poi.key))
            poi.key to expected
        }
    }

    // Independent exact-control charmap digests; test oracles, never production selectors.
    private val emeraldItemNameHashes = mapOf(
        4 to "17981b51d17e17dcdf00165263590a279e1ac9102280ad78d7920282b5fb2f4c",
        13 to "c71124a78727996d3f44f4c3c02933cc1dedd48148766855fa1d1ccac6e0cc60",
        281 to "7bb69b57655d52e67befc277975ab176d274c5b4d8a1d0be2f179acfbc324785",
        289 to "1f6251027f8cadd39f481ba94f80a49dfb1287c9e7efb6fc4fa3f7c73669f48f",
        336 to "6453dce84b755437be6a19e6dc5d665d729e8829c7a356a68213ecbfad4658d3",
    )

    private data class ItemNameExpectation(
        val count: Int,
        val samples: Map<Int, String>,
        val authorityRoot: Int,
        val lastId: Int,
        val lastNameHash: String,
        val zeroReferenced: Boolean,
        val compiledOnly: Boolean = false,
        val allNames: Map<Int, String>? = null,
    )

    private fun itemNameExpectation(control: NativeControl): ItemNameExpectation = when (control.folder) {
        "ja/RED_BLUE", "ja/YELLOW" -> genOneItemOracle(control).let { names ->
            ItemNameExpectation(62, names.mapValues { sha256(it.value.toByteArray(StandardCharsets.UTF_8)) },
                0, 0, "", false, allNames = names)
        }
        "ja/GOLD_SILVER", "ja/CRYSTAL" -> genTwoItemOracle(control).let { names ->
            ItemNameExpectation(names.size, names.mapValues { sha256(it.value.toByteArray(StandardCharsets.UTF_8)) },
                0, 0, "", false, allNames = names)
        }
        "ko/GOLD", "ko/SILVER" -> koreanItemOracle().let { names ->
            ItemNameExpectation(62, names.mapValues { sha256(it.value.toByteArray(StandardCharsets.UTF_8)) },
                0, 0, "", false, allNames = names)
        }
        "ja/RUBY_SAPPHIRE" -> ItemNameExpectation(
            101, mapOf(
                4 to "17981b51d17e17dcdf00165263590a279e1ac9102280ad78d7920282b5fb2f4c",
                13 to "c71124a78727996d3f44f4c3c02933cc1dedd48148766855fa1d1ccac6e0cc60",
                289 to "1f6251027f8cadd39f481ba94f80a49dfb1287c9e7efb6fc4fa3f7c73669f48f",
                336 to "6453dce84b755437be6a19e6dc5d665d729e8829c7a356a68213ecbfad4658d3",
                346 to "b8b36facd0037ea26ff0e2ffea4c55580748bbb4df6af1f72378f1dc2eaf6caf",
            ), 0x39A648, 348,
            "19413c8b7affaeeec861dadb1a640d163f4ec6b2ae4b38feb0e9f8c835641a8f", false, true,
        )
        "ja/EMERALD" -> ItemNameExpectation(
            104, emeraldItemNameHashes, 0x55CEE8, 376,
            "285f476bfcdcacc123e141dc1cec947a029de43b6bccd14af4f927d778ef90da", false,
        )
        "ja/FIRERED_LEAFGREEN" -> ItemNameExpectation(
            127, mapOf(
                0 to "19413c8b7affaeeec861dadb1a640d163f4ec6b2ae4b38feb0e9f8c835641a8f",
                4 to "17981b51d17e17dcdf00165263590a279e1ac9102280ad78d7920282b5fb2f4c",
                13 to "c71124a78727996d3f44f4c3c02933cc1dedd48148766855fa1d1ccac6e0cc60",
                289 to "1f6251027f8cadd39f481ba94f80a49dfb1287c9e7efb6fc4fa3f7c73669f48f",
                345 to "b8fd2b23dd1d7b4acf797f820148fdb18f3e30cbbd1003d8b8640c911148c365",
                355 to "ed5b45f5017bc190305d711e5edc595725bde9260f887511d8fec73d614db203",
            ), 0x39BEB8, 374,
            "415bbe120e29e7b716547bc4f42cccfa74efec69f2632f37d886553c177f0c27", true,
        )
        else -> error("No independently ratified item-name expectation for ${control.folder}")
    }

    // Independent bounded compiled-control oracles, ratified before the producer was implemented.
    // Charmaps: pokered-jp 258d1a89/charmap.asm; pokeyellow-jp f282e72a/constants/charmap.asm
    // (full pins below). Shared glyph aliases use primary Hiragana: 3Dべ,47ぺ,CDへ,D8り.
    private fun genOneItemOracle(control: NativeControl): Map<Int, String> {
        val ordinary = """
            2 ハイパーボール
            3 スーパーボール
            4 モンスターボール
            10 つきのいし
            11 どくけし
            14 ねむけざまし
            16 かいふくのくすり
            17 まんたんのくすり
            18 すごいキズぐすり
            19 いいキズぐすり
            20 キズぐすり
            29 あなぬけのヒモ
            35 マックスアップ
            36 タウりン
            37 ブロムへキシン
            38 インドメタシン
            39 りゾチウム
            40 ふしぎなアメ
            43 ひみつのカギ
            46 ヨクアタール
            48 カードキー
            49 きんのたま
            52 なんでもなおし
            53 げんきのかけら
            54 げんきのかたまり
            55 エフェクトガード
            64 きんのいれば
            68 スぺシャルアップ
            72 シルフスコープ
            74 エレべータのカギ
            79 ポイントアップ
            80 ピーピーエイド
            81 ピーピーりカバー
            82 ピーピーエイダー
            83 ピーピーマックス
        """.trimIndent().lines().associate { it.substringBefore(' ').toInt() to it.substringAfter(' ') }
        val machines = "201:01 202:02 203:03 204:04 205:05 207:07 208:08 209:09 210:10 212:12 214:14 216:16 217:17 219:19 220:20 222:22 225:25 226:26 230:30 232:32 233:33 237:37 240:40 243:43 244:44 245:45 247:47"
            .split(' ').associate { pair ->
                val digits = pair.substringAfter(':').map { digit ->
                    if (control.family == EngineFamily.YELLOW) "０１２３４５６７８９"[digit - '0'] else digit
                }.joinToString("")
                pair.substringBefore(':').toInt() to "わざマシン$digits"
            }
        assertEquals(35, ordinary.size)
        assertEquals(27, machines.size)
        return ordinary + machines
    }

    private fun assertGenOneConsumedTokens(control: NativeControl) {
        val codec = if (control.family == EngineFamily.YELLOW)
            com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen1Yellow
            else com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen1RedBlue
        // Exactly the 89 tokens consumed in the retained per-reference payload proof; not a dialect re-proof.
        val tokens = "5:ガ 6:ギ 12:ズ 14:ゾ 15:ダ 19:ド 25:バ 27:ブ 28:ボ 39:ぎ 40:ぐ 41:げ 42:ご 43:ざ 51:で 52:ど 58:ば 61:べ 64:パ 65:ピ 66:プ 67:ポ 71:ぺ 128:ア 129:イ 130:ウ 131:エ 133:カ 134:キ 135:ク 137:コ 139:シ 140:ス 143:タ 144:チ 147:ト 153:ハ 154:ヒ 155:フ 157:マ 159:ム 160:メ 161:モ 164:ヨ 166:ル 167:レ 168:ロ 171:ン 172:ッ 173:ャ 177:あ 178:い 181:お 182:か 183:き 184:く 185:け 188:し 189:す 192:た 194:つ 197:な 199:ぬ 200:ね 201:の 203:ひ 204:ふ 205:へ 207:ま 208:み 209:む 211:も 215:ら 216:り 218:れ 220:わ 222:ん 227:ー 235:ェ"
            .split(' ').associate { it.substringBefore(':').toInt() to it.substringAfter(':') } +
            (246..255).associateWith { if (control.family == EngineFamily.YELLOW) "０１２３４５６７８９"[it - 246].toString() else (it - 246).toString() }
        assertEquals(89, tokens.size)
        tokens.forEach { (byte, glyph) ->
            val decoded = codec.decodeDetailed(byteArrayOf(byte.toByte(), 0x50))
            assertEquals("independent token $byte for ${codec.id}", glyph, decoded.text)
            assertTrue(decoded.terminated && decoded.invalidUnits == 0 && decoded.controlUnits == 0 && decoded.substitutionUnits == 0)
        }
        assertEquals(if (control.family == EngineFamily.YELLOW) "０１" else "01", codec.decode(byteArrayOf(0xf6.toByte(), 0xf7.toByte(), 0x50)))
    }

    // Preimplementation two-control proof; independent Japanese declarations at
    // f2b5db1deb0b8f2009d7e9d50b3bcb05ef8a9f53/charmap.asm. Primary Hiragana aliases,
    // fullwidth digits; no disabled English expansions or production-decoded oracle.
    private fun genTwoItemOracle(control: NativeControl): Map<Int, String> {
        val common = """
            2 ハイパーボール
            4 スーパーボール
            5 モンスターボール
            8 つきのいし
            9 どくけし
            10 やけどなおし
            11 こおりなおし
            12 ねむけざまし
            13 まひなおし
            14 かいふくのくすり
            15 まんたんのくすり
            16 すごいキズぐすり
            17 いいキズぐすり
            18 キズぐすり
            19 あなぬけのヒモ
            21 ピーピーマックス
            26 マックスアップ
            27 タウりン
            28 ブロムへキシン
            29 インドメタシン
            31 りゾチウム
            32 ふしぎなアメ
            33 ヨクアタール
            36 きんのたま
            38 なんでもなおし
            39 げんきのかけら
            40 げんきのかたまり
            41 エフェクトガード
            43 ゴールドスプレー
            44 クりティカッター
            49 プラスパワー
            51 ディフェンダー
            52 スピーダー
            53 スぺシャルアップ
            54 コインケース
            62 ポイントアップ
            63 ピーピーエイド
            64 ピーピーりカバー
            65 ピーピーエイダー
            91 おまもりこばん
            106 けむりだま
            107 とけないこおり
            119 きあいのハチマキ
            128 きかいのぶひん
            151 りゅうのウロコ
            152 はかいのいでんし
            194 わざマシン０４
            204 わざマシン１３
            209 わざマシン１８
            211 わざマシン２０
            213 わざマシン２２
            217 わざマシン２６
            219 わざマシン２８
            226 わざマシン３４
            227 わざマシン３５
            231 わざマシン３９
            232 わざマシン４０
            235 わざマシン４３
            236 わざマシン４４
            238 わざマシン４６
            239 わざマシン４７
            249 ひでんマシン０７
        """.trimIndent().lines().associate { it.substringBefore(' ').toInt() to it.substringAfter(' ') }
        val extra = """
            20 むしよけスプレー
            42 シルバースプレー
            74 どくけしのみ
            95 しんぴのしずく
            121 ちからのこな
            122 ちからのねっこ
            123 ばんのうごな
            124 ふっかつそう
            131 ほしのすな
            132 ほしのかけら
            138 もくたん
            150 ふしぎなきのみ
            173 きのみ
            174 おうごんのみ
        """.trimIndent().lines().associate { it.substringBefore(' ').toInt() to it.substringAfter(' ') }
        assertEquals(62, common.size)
        assertEquals(14, extra.size)
        return if (control.family == EngineFamily.CRYSTAL) common + extra else common
    }

    private fun assertGenTwoConsumedTokens(control: NativeControl) {
        val codec = com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen2
        val tokens = "05:ガ 09:ゴ 0c:ズ 0e:ゾ 0f:ダ 12:デ 13:ド 19:バ 1b:ブ 1c:ボ 27:ぎ 28:ぐ 29:げ 2a:ご 2b:ざ 30:だ 33:で 34:ど 3a:ば 3c:ぶ 40:パ 41:ピ 42:プ 43:ポ 47:ぺ 80:ア 81:イ 82:ウ 83:エ 85:カ 86:キ 87:ク 88:ケ 89:コ 8b:シ 8c:ス 8f:タ 90:チ 92:テ 93:ト 99:ハ 9a:ヒ 9b:フ 9d:マ 9f:ム a0:メ a1:モ a4:ヨ a5:ラ a6:ル a7:レ a8:ロ a9:ワ ab:ン ac:ッ ad:ャ b0:ィ b1:あ b2:い b3:う b5:お b6:か b7:き b8:く b9:け ba:こ bc:し bd:す c0:た c2:つ c4:と c5:な c7:ぬ c8:ね c9:の ca:は cb:ひ cc:ふ cd:へ cf:ま d1:む d3:も d4:や d7:ら d8:り dc:わ de:ん e1:ゅ e3:ー eb:ェ f6:０ f7:１ f8:２ f9:３ fa:４ fb:５ fc:６ fd:７ fe:８ ff:９"
            .split(' ').associate { it.substringBefore(':').toInt(16) to it.substringAfter(':') } +
            (if (control.family == EngineFamily.CRYSTAL) "2d:ず 45:ぴ bf:そ c1:ち ce:ほ d0:み d6:よ df:っ"
                .split(' ').associate { it.substringBefore(':').toInt(16) to it.substringAfter(':') } else emptyMap())
        assertEquals(if (control.family == EngineFamily.CRYSTAL) 108 else 100, tokens.size)
        tokens.forEach { (byte, glyph) ->
            val decoded = codec.decodeDetailed(byteArrayOf(byte.toByte(), 0x50))
            assertEquals("independent token $byte for ${codec.id}", glyph, decoded.text)
            assertTrue(decoded.terminated && decoded.invalidUnits == 0 && decoded.controlUnits == 0 && decoded.substitutionUnits == 0)
        }
    }

    // Both exact inputs independently proved before acceptance: pokegold-kr
    // 7743877dc9fa8603f4b6eaebe904a7ba03fdb9e4 constants/charmap{,/korean_table_*}.asm.
    // Literal oracle transcribed from retained raw proof, never generated by the production codec.
    private fun koreanItemOracle(): Map<Int, String> = """
        2 하이퍼볼
        4 수퍼볼
        5 몬스터볼
        8 달맞이 돌
        9 해독제
        10 화상 치료제
        11 얼음상태 치료제
        12 잠깨는 약
        13 마비 치료제
        14 회복약
        15 풀 회복약
        16 고급 상처약
        17 좋은 상처약
        18 상처약
        19 동굴탈출 로프
        21 PP 맥스
        26 맥스 업
        27 타우린
        28 사포닌
        29 알칼로이드
        31 리보플라빈
        32 이상한 사탕
        33 잘-맞히기
        36 금구슬
        38 만병통치제
        39 기력의 조각
        40 기력의 덩어리
        41 이펙트 가드
        43 골드 스프레이
        44 크리티컬 커터
        49 플러스파워
        51 디펜드 업
        52 스피드 업
        53 스페셜 업
        54 동전 케이스
        62 포인트 업
        63 PP 에이드
        64 PP 회복
        65 PP 에이더
        91 부적 금화
        106 연막탄
        107 녹지않는 얼음
        119 기합의 머리띠
        128 기계부품
        151 용의 비늘
        152 파괴의 유전자
        194 기술머신04
        204 기술머신13
        209 기술머신18
        211 기술머신20
        213 기술머신22
        217 기술머신26
        219 기술머신28
        226 기술머신34
        227 기술머신35
        231 기술머신39
        232 기술머신40
        235 기술머신43
        236 기술머신44
        238 기술머신46
        239 기술머신47
        249 비전머신07
    """.trimIndent().lines().associate { it.substringBefore(' ').toInt() to it.substringAfter(' ') }
        .also { assertEquals(62, it.size) }

    private fun assertKoreanConsumedTokens(control: NativeControl) {
        val codec = com.enrpau.dualscreendex.parser.text.KoreanGen2PokemonTextCodec.codec
        // Exactly the 129 distinct tokens consumed by each frozen all-name proof.
        val tokens = "0101:가 0102:각 0148:계 014d:고 0161:골 017b:괴 0188:구 018c:굴 01ad:금 01ae:급 01b2:기 01ca:깨 028c:녹 02c2:는 02c3:늘 02d1:닌 02de:달 02f5:더 0302:덩 0316:독 0319:돌 031f:동 0345:드 0360:디 03bc:띠 03c3:라 03df:러 03e9:레 03f2:력 03fe:로 0411:료 043e:리 0440:린 0446:마 0447:막 0448:만 0462:맞 0466:맥 0473:머 0493:몬 0514:병 0518:보 0519:복 051c:볼 052e:부 0561:비 0563:빈 05b7:사 05c3:상 05f8:셜 0626:수 062a:술 064a:스 064d:슬 0665:신 06ca:않 06cb:알 06e0:약 06ee:어 06f3:얼 06f7:업 0701:에 070c:연 074b:용 074c:우 0766:워 077f:유 078a:은 078d:음 0797:의 079c:이 079e:인 07aa:자 07af:잘 07b1:잠 07cb:적 07cc:전 07d6:제 07e6:조 07f1:좋 0826:지 08b3:처 08e2:출 0901:치 090e:칼 091f:커 0923:컬 0929:케 0979:크 0988:타 098a:탄 098b:탈 0991:탕 0992:태 099d:터 09bb:통 09de:트 09ec:티 09f4:파 0a0b:퍼 0a14:페 0a15:펙 0a16:펜 0a27:포 0a3e:풀 0a40:품 0a61:프 0a63:플 0a67:피 0a6f:하 0a71:한 0a75:합 0a78:해 0aad:화 0ab8:회 0af7:히 0b68:- 8f:P f6:0 f7:1 f8:2 f9:3 fa:4 fb:5 fc:6 fd:7 fe:8 ff:9"
            .split(' ').associate { it.substringBefore(':') to it.substringAfter(':') } + ("7f" to " ")
        assertEquals(129, tokens.size)
        tokens.forEach { (hex, glyph) ->
            val raw = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            // Preserve a whitespace token between independently declared P glyphs; decode trims edges.
            val bytes = if (glyph == " ") byteArrayOf(0x8f.toByte()) + raw + byteArrayOf(0x8f.toByte(), 0x50)
                else raw + byteArrayOf(0x50)
            val decoded = codec.decodeDetailed(bytes)
            assertEquals("independent token $hex for ${control.folder}", if (glyph == " ") "P P" else glyph, decoded.text)
            assertTrue(decoded.terminated && decoded.invalidUnits == 0 && decoded.controlUnits == 0 && decoded.substitutionUnits == 0)
        }
        println("ITEM_NAME_TOKENS ${control.folder} independentTokens=${tokens.size} PASS")
    }

    private fun assertNativeItemNames(catalog: ParsedCatalog, expected: ItemNameExpectation) {
        val overlay = requireNotNull(catalog.defaultLocalizedText())
        val state = overlay.localizedCapabilities.getValue(LocalizedTextCapability.ITEM_NAMES)
        assertEquals(expected.count, state.expectedRecords)
        assertEquals(expected.count, state.coveredRecords)
        assertEquals(CapabilityStatus.AVAILABLE, state.status)
        val ids = catalog.captureBallsById.keys + catalog.localMaps.pois.mapNotNull { it.item?.itemId }
        assertEquals(ids, overlay.itemNames.keys)
        assertTrue(overlay.itemNames.values.all { it.status == CapabilityStatus.AVAILABLE })
        if (catalog.languageManifest.defaultLanguage == LanguageTag.KOREAN) {
            // Independently measured current traversal; paired typed-evidence tests bind all 225 operands.
            assertEquals(225, catalog.localMaps.pois.count { it.item != null })
            assertEquals(62, ids.size)
        }
        expected.allNames?.let { assertEquals("all independent reference labels", it, overlay.itemNames.mapValues { entry -> entry.value.value }) }
        assertEquals(expected.zeroReferenced, 0 in ids)
        assertTrue(catalog.captureBallsById.values.all { it.name.value == null })
        assertTrue(catalog.localMaps.pois.all { it.item?.displayName == null })
        val text = catalog.defaultTextProjection()
        expected.samples.forEach { (id, hash) ->
            assertEquals("independent item-name digest $id", hash, sha256(requireNotNull(text.itemName(id)).toByteArray(StandardCharsets.UTF_8)))
        }
        catalog.localMaps.pois.forEach { poi -> poi.item?.itemId?.let { id ->
            assertEquals(text.itemName(id), text.poiItemName(poi.key, id))
        } }
        assertTrue(catalog.textProjection(LanguageTag.ENGLISH) == null)
    }

    /** Additive Local-name assertion only; JP sign and all region-title obligations remain open. */
    private fun assertNativeContextualMaps(catalog: ParsedCatalog, expected: NativeContextualExpectation): JsonObject {
        val overlay = requireNotNull(catalog.defaultLocalizedText())
        val text = catalog.defaultTextProjection()
        return NativeContextualMapAssertions.names(expected, catalog.localMaps.maps, overlay.localMapNames,
            catalog.localMaps.maps.associate { it.key to text.localMapName(it.key) },
            overlay.localizedCapabilities.getValue(LocalizedTextCapability.LOCAL_MAP_NAMES))
    }

    private fun assertNativeSelectedDirectSign(catalog: ParsedCatalog, expected: NativeSelectedDirectSignExpectation): JsonObject {
        assertEquals(expected.sha256, catalog.romSha256)
        val overlay = requireNotNull(catalog.defaultLocalizedText())
        val text = catalog.defaultTextProjection()
        val fulfilled = catalog.localMaps.pois.filter { poi ->
            if (poi.textObligation == com.enrpau.dualscreendex.parser.catalog.LocalMapPoiTextObligation.GENDERED_DIRECT_TEXT)
                (0..1).all { gender -> !text.poiLabel(poi.key, gender).isNullOrBlank() }
            else !text.poiLabel(poi.key).isNullOrBlank()
        }.mapTo(linkedSetOf()) { it.key }
        return NativeSelectedDirectSignAssertions.names(expected, catalog.localMaps.maps, catalog.localMaps.pois,
            overlay.poiTexts[expected.poiKey], text.poiDisplayName(expected.poiKey), fulfilled,
            overlay.localizedCapabilities.getValue(LocalizedTextCapability.POI_TEXT))
    }

    private fun assertNativeRoundTrip(control: NativeControl, requireDeclaredSigns: Boolean = false, requireItemNames: Boolean = false,
        selectedDirectSign: NativeSelectedDirectSignExpectation? = null) {
        val configured = System.getenv("DUALDEX_NATIVE_CONTROLS")
        if (requireDeclaredSigns || requireItemNames) require(!configured.isNullOrBlank()) { "exact native sign/item gate requires DUALDEX_NATIVE_CONTROLS" }
        assumeTrue("set DUALDEX_NATIVE_CONTROLS for the nine exact native controls", !configured.isNullOrBlank())
        val checks = NativeChecks(control)
        val originalReads = AtomicInteger()
        val originalParserInvocations = AtomicInteger()
        var selectedSignReceipt: JsonObject? = null
        val itemExpectation = if (requireItemNames) itemNameExpectation(control) else null
        try {
            val rom = checks.attempt("input.sha256") {
                val directory = Path.of(requireNotNull(configured)).resolve(control.folder)
                val path = Files.list(directory).use { paths ->
                    paths.filter { Files.isRegularFile(it) }.toList().single()
                }
                originalReads.incrementAndGet()
                RomImage(Files.readAllBytes(path)).also { assertEquals(control.sha256, it.sha256) }
            } ?: return
            val attempt = checks.attempt("parse") {
                originalParserInvocations.incrementAndGet()
                CatalogParser.parseCatching(rom)
            } ?: return
            checks.attempt("selection") {
                assertEquals(SelectionStatus.SELECTED, attempt.analysis.status)
                assertEquals(control.family, attempt.analysis.selectedFamily)
            }
            val catalog = checks.attempt("materialize") { requireNotNull(attempt.catalog).getOrThrow() } ?: return
            val contextualProducer = control.contextualMaps?.let { expected ->
                checks.attempt("contextual-maps.materialize") { assertNativeContextualMaps(catalog, expected) }
            }
            val selectedSignProducer = selectedDirectSign?.let { expected ->
                checks.attempt("selected-direct-sign.materialize") { assertNativeSelectedDirectSign(catalog, expected) }
            }
            if (itemExpectation != null) {
                checks.attempt("item-names.materialize.${itemExpectation.count}.independent-samples") { assertNativeItemNames(catalog, itemExpectation) }
                if (control.generation in 1..2) checks.attempt("item-names.exact-consumed-tokens-and-reference-gate") {
                    when {
                        control.folder.startsWith("ko/") -> assertKoreanConsumedTokens(control)
                        control.generation == 1 -> assertGenOneConsumedTokens(control)
                        else -> assertGenTwoConsumedTokens(control)
                    }
                    val session = com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession(rom, attempt.analysis.header)
                    val queried = setOf(-1, 0, 2, 196, 201, 250, 255, 256)
                    val denied = com.enrpau.dualscreendex.parser.catalog.ItemNameMaterializer(session)
                        .materialize(requireNotNull(attempt.layout), queried)
                    assertEquals(queried, denied.keys)
                    assertTrue(denied.values.all { it.value == null && it.status == CapabilityStatus.NOT_FOUND })
                }
                if (control.generation == 3) checks.attempt("item-names.direct-boundary.zero-last-dynamic") {
                    val session = com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession(rom, attempt.analysis.header)
                    val itemLayout = requireNotNull(attempt.layout)
                    assertEquals(
                        if (itemExpectation.compiledOnly) com.enrpau.dualscreendex.parser.model.GbaItemRootNomination.Absent
                        else com.enrpau.dualscreendex.parser.model.GbaItemRootNomination.Nominated(itemExpectation.authorityRoot),
                        itemLayout.itemRootNomination,
                    )
                    val authority = itemLayout.itemNameAuthority as com.enrpau.dualscreendex.parser.model.GbaItemNameAuthority.Available
                    assertEquals(itemExpectation.authorityRoot, authority.root)
                    assertEquals(if (itemExpectation.compiledOnly)
                        com.enrpau.dualscreendex.parser.model.GbaItemNameProvenance.COMPILED_CONSUMER
                        else com.enrpau.dualscreendex.parser.model.GbaItemNameProvenance.PUBLISHED_ROOT, authority.provenance)
                    val names = com.enrpau.dualscreendex.parser.catalog.ItemNameMaterializer(session)
                        .materialize(itemLayout, setOf(0, 175, itemExpectation.lastId, itemExpectation.lastId + 1, 65535))
                    for ((id, expected) in mapOf(
                        0 to "19413c8b7affaeeec861dadb1a640d163f4ec6b2ae4b38feb0e9f8c835641a8f",
                        itemExpectation.lastId to itemExpectation.lastNameHash,
                    )) assertEquals(expected, sha256(requireNotNull(names.getValue(id).value).toByteArray(StandardCharsets.UTF_8)))
                    for (id in listOf(175, itemExpectation.lastId + 1, 65535)) {
                        assertEquals(CapabilityStatus.NOT_FOUND, names.getValue(id).status)
                        assertTrue(names.getValue(id).value == null)
                    }
                    assertTrue(names.getValue(175).reasons.any { it.contains("dynamic") })
                }
            }
            if (requireDeclaredSigns) checks.attempt("declared-sign.materialize.independent-samples") { assertKoreanDeclaredSigns(catalog) }
            val language = if (control.folder.startsWith("ja/")) LanguageTag.JAPANESE else LanguageTag.KOREAN
            checks.attempt("authority.exact-overlay") {
                assertEquals(control.family, catalog.family)
                assertEquals(LanguageResolutionStatus.RESOLVED, catalog.languageManifest.status)
                assertEquals(language, catalog.languageManifest.defaultLanguage)
                val projection = catalog.languageManifest.projections.single()
                assertEquals(language, projection.language)
                assertEquals(control.codecId, projection.codecId)
                assertEquals(1, projection.codecVersion)
                assertEquals(LanguageResolutionStatus.RESOLVED, projection.status)
                assertEquals(setOf(language), catalog.localization.overlays.keys)
                assertTrue(catalog.localizedText(LanguageTag.ENGLISH) == null)
            }
            val overlay = catalog.localizedText(language)
            val text = catalog.defaultTextProjection()
            val directMoveProseControl = control.generation == 3 && language == LanguageTag.JAPANESE
            if (directMoveProseControl) checks.attempt("LNG-B002.move-prose.independent-samples") {
                assertNativeMoveProseSamples(catalog, control)
            }
            // Gen III dexNumber can be regional: source SPECIES_BULBASAUR and the compiled name row are 1.
            // Selecting dexNumber == 1 there would test Treecko, not the independently pinned Bulbasaur sample.
            val bulbasaur = if (control.generation == 3) catalog.speciesById[1]
                else catalog.speciesById.values.singleOrNull { it.dexNumber.value == 1 }
            val speciesId = bulbasaur?.id
            println("NATIVE_E2E_COUNTS ${control.folder} species=${catalog.speciesById.size} moves=${catalog.movesById.size} " +
                "types=${catalog.typesById.size} semanticTypes=${catalog.typesById.values.count { it.semanticRole.value != null }} " +
                "worldLocations=${catalog.worldMaps.regions.sumOf { it.locations.size }} localMaps=${catalog.localMaps.maps.size}")
            checks.attempt("capabilities.inventory") {
                assertEquals(15, LocalizedTextCapability.entries.size)
                assertEquals(LocalizedTextCapability.entries.toSet(), requireNotNull(overlay).localizedCapabilities.keys)
            }
            LocalizedTextCapability.entries.forEach { capability ->
                val state = overlay?.localizedCapabilities?.get(capability)
                println("NATIVE_E2E_CAPABILITY ${control.folder} $capability ${state?.status} ${state?.coveredRecords}/${state?.expectedRecords}")
                checks.attempt("capability.$capability") {
                    requireNotNull(state)
                    assertTrue(state.coveredRecords in 0..state.expectedRecords)
                    if (state.status in setOf(CapabilityStatus.NOT_FOUND, CapabilityStatus.NOT_APPLICABLE, CapabilityStatus.AMBIGUOUS)) {
                        assertEquals(0, state.coveredRecords)
                    }
                    if (control.generation == 1 && capability == LocalizedTextCapability.MOVE_DESCRIPTIONS ||
                        control.generation < 3 && capability in setOf(LocalizedTextCapability.ABILITY_NAMES, LocalizedTextCapability.ABILITY_DESCRIPTIONS)) {
                        assertEquals(CapabilityStatus.NOT_APPLICABLE, state.status)
                    }
                }
            }
            checks.attempt("sample.species-name") { assertEquals(control.speciesName, text.speciesName(requireNotNull(speciesId))) }
            checks.attempt("sample.move-name") { assertEquals(control.moveName, text.moveName(1)) }
            checks.attempt("LNG-B002.sample.species-description") {
                assertTrue(requireNotNull(text.speciesDescription(requireNotNull(speciesId))).contains(control.dexFragment))
            }
            if (control.generation >= 2) checks.attempt("LNG-B002.move-description") {
                assertTrue(!text.moveDescription(1).isNullOrBlank())
                if (language == LanguageTag.KOREAN) assertTrue(requireNotNull(text.moveDescription(1)).contains("손과 꼬리 등을 사용해서"))
            }
            if (control.generation == 3) {
                checks.attempt("LNG-B002.sample.species-dimensions") {
                    // Independently decoded native row 1 dimensions; regional row 203 is 15/415.
                    assertEquals(7, requireNotNull(bulbasaur).height.value)
                    assertEquals(69, bulbasaur.weight.value)
                }
                checks.attempt("LNG-B002.ability-description") {
                    val abilityIds = requireNotNull(bulbasaur).abilityIds.value.orEmpty().filter { it > 0 }
                    assertTrue(abilityIds.isNotEmpty())
                    assertTrue(abilityIds.all { !text.abilityName(it).isNullOrBlank() && !text.abilityDescription(it).isNullOrBlank() })
                    assertEquals("しんりょく", text.abilityName(65))
                    assertEquals("ピンチに くさの いりょくが あがる", text.abilityDescription(65))
                }
            }
            checks.attempt("LNG-D005.complete-type-semantics") {
                assertEquals(if (control.generation == 1) 15 else 18, catalog.typesById.size)
                assertTrue(catalog.typesById.values.all { it.semanticRole.status == CapabilityStatus.AVAILABLE && !text.typeName(it.id).isNullOrBlank() })
                // Resolve IDs from independently expected labels, never from presumed numeric type order.
                control.typeSamples.forEach { (name, role) ->
                    val type = catalog.typesById.values.single { text.typeName(it.id) == name }
                    assertEquals(role, type.semanticRole.value)
                }
            }
            checks.attempt("LNG-B002.sample.world-location") {
                assertTrue(requireNotNull(overlay).worldLocationNames.values.any { it.value == control.worldLocationName })
            }
            checks.attempt("LNG-B002.sample.local-location") {
                assertTrue(requireNotNull(overlay).localMapNames.values.any { it.value == control.locationName })
            }
            checks.attempt("isolation.shared-text") {
                assertTrue(catalog.speciesById.values.all { it.name.value == null && it.description.value == null })
                assertTrue(catalog.movesById.values.all { it.name.value == null && it.effectText.value == null })
                assertTrue(catalog.typesById.values.all { it.name.value == null })
                assertTrue(catalog.abilitiesById.values.all { it.name.value == null && it.description.value == null })
                assertTrue(catalog.localMaps.maps.all { it.displayName == null })
                assertTrue(catalog.worldMaps.regions.all { region -> region.locations.all { it.displayName == null } })
            }

            // This opt-in diagnostic retains its private SQLite artifacts; it never copies a ROM to the repository.
            val cache = checks.attempt("sqlite.create") {
                CatalogCache(newRoot().toFile(), JdbcTestCatalogDatabaseFactory).also {
                    if (selectedDirectSign != null) assertTrue("selected sign requires a fresh SQLite file", !it.fileFor(rom.sha256).exists())
                }
            } ?: return
            checks.attempt("sqlite.write-close") {
                cache.write(catalog, CatalogSourceMetadata.direct("native-control", rom.size, "NATIVE-CONTROL"), CatalogWriteProgress.complete())
            } ?: return
            // CatalogCache opens and closes a JDBC connection for each operation; this is not an in-memory round trip.
            val stored = checks.attempt("sqlite.reopen-close") { requireNotNull(cache.readComplete(rom.sha256)) } ?: return
            val reopened = stored.catalog
            val selectedSignMetadata = selectedDirectSign?.let { expected ->
                checks.attempt("selected-direct-sign.sqlite.actual-metadata") {
                    JdbcTestCatalogDatabaseFactory.open(cache.fileFor(rom.sha256)).use { database ->
                        NativeSelectedDirectSignAssertions.metadata(expected, database.query(
                            "SELECT sha256, parser_schema_version, schema_version, is_complete FROM catalog_metadata WHERE id = 1",
                        ) { row -> NativeSelectedSignMetadata(requireNotNull(row.string("sha256")), requireNotNull(row.long("parser_schema_version")),
                            requireNotNull(row.long("schema_version")), requireNotNull(row.long("is_complete"))) })
                    }
                }
            }
            val selectedSignSqlite = selectedDirectSign?.let { expected ->
                checks.attempt("selected-direct-sign.sqlite.reopened") {
                    assertEquals(catalog.localMaps, reopened.localMaps)
                    assertEquals(catalog.defaultLocalizedText(), reopened.defaultLocalizedText())
                    assertNativeSelectedDirectSign(reopened, expected).also { assertEquals(selectedSignProducer, it) }
                }
            }
            val contextualSqlite = control.contextualMaps?.let { expected ->
                checks.attempt("contextual-maps.sqlite-reopened") {
                    JdbcTestCatalogDatabaseFactory.open(cache.fileFor(rom.sha256)).use { database ->
                        assertEquals(listOf(CatalogSchema.parserSchemaVersion.toLong() to CatalogSchema.version.toLong()), database.query(
                            "SELECT parser_schema_version, schema_version FROM catalog_metadata WHERE id = 1",
                        ) { row -> row.long("parser_schema_version") to row.long("schema_version") })
                    }
                    assertNativeContextualMaps(reopened, expected).also {
                        assertEquals(requireNotNull(contextualProducer), it)
                    }
                }
            }
            if (itemExpectation != null) checks.attempt("item-names.sqlite.${itemExpectation.count}.independent-samples") {
                assertNativeItemNames(reopened, itemExpectation)
                assertEquals(catalog.defaultLocalizedText(), reopened.defaultLocalizedText())
                println("ITEM_NAME_CACHE ${control.folder} database=${cache.fileFor(rom.sha256).absolutePath} expected=${itemExpectation.count}")
            }
            if (requireDeclaredSigns) checks.attempt("declared-sign.sqlite.independent-samples") {
                assertKoreanDeclaredSigns(reopened)
                assertEquals(catalog.localMaps.pois, reopened.localMaps.pois)
                assertEquals(2, CatalogSchema.version)
                JdbcTestCatalogDatabaseFactory.open(cache.fileFor(rom.sha256)).use { database ->
                    assertEquals(listOf(CatalogSchema.parserSchemaVersion.toLong() to CatalogSchema.version.toLong()), database.query(
                        "SELECT parser_schema_version, schema_version FROM catalog_metadata WHERE id = 1",
                    ) { row -> row.long("parser_schema_version") to row.long("schema_version") })
                }
                println("DECLARED_SIGN_CACHE ${control.folder} sha256=${rom.sha256} database=${cache.fileFor(rom.sha256).absolutePath}")
            }
            if (directMoveProseControl) checks.attempt("sqlite.move-prose.independent-samples") {
                assertNativeMoveProseSamples(reopened, control)
                JdbcTestCatalogDatabaseFactory.open(cache.fileFor(rom.sha256)).use { database ->
                    assertEquals(listOf(CatalogSchema.parserSchemaVersion.toLong() to CatalogSchema.version.toLong()), database.query(
                        "SELECT parser_schema_version, schema_version FROM catalog_metadata WHERE id = 1",
                    ) { row -> row.long("parser_schema_version") to row.long("schema_version") })
                }
            }
            checks.attempt("sqlite.whole-catalog-equality") { assertTrue("whole catalog differs", catalog == reopened) }
            checks.attempt("sqlite.sections") {
                assertEquals(CatalogSchema.requiredSections + "language_overlay:${language.value}", stored.committedSections)
            }
            checks.attempt("sqlite.integrity") { assertDatabaseIntegrity(cache.fileFor(rom.sha256)) }
            checks.attempt(OFFICIAL_FORECAST_BOUNDARY) {
                val parsedPolicies = assertOfficialConditionalWeatherPolicies(catalog, control)
                val reopenedPolicies = assertOfficialConditionalWeatherPolicies(reopened, control)
                assertEquals(parsedPolicies, reopenedPolicies)
                println("NATIVE_E2E_FORECAST_POLICY ${control.folder} referencedMoves=${reopenedPolicies.size} " +
                    "scope=CONDITIONAL_TYPE_POLICY engineWeatherApplicability=NOT_TESTED")
            }
            checks.attempt(FAULT_INJECTED_FORECAST_BOUNDARY) {
                // Deliberately altered catalogs are negative fault injections, never official positive evidence.
                val reopenedText = reopened.defaultTextProjection()
                control.typeSamples.keys.forEach { name ->
                    val type = reopened.typesById.values.single { reopenedText.typeName(it.id) == name }
                    val moves = reopened.movesById.values.filter { it.typeId.value == type.id }
                    assertTrue("no actual move references for the fault-injected type", moves.isNotEmpty())
                    val withoutAuthority = reopened.copy(typesById = reopened.typesById + (type.id to type.copy(
                        semanticRole = CatalogField.notFound("fault-injected missing semantic authority"),
                    )))
                    assertEquals(reopened.movesById, withoutAuthority.movesById)
                    assertEquals(reopened.localization, withoutAuthority.localization)
                    moves.forEach { move ->
                        val policy = DamageForecastAssembler.conditionalWeatherPolicy(
                            withoutAuthority, requireNotNull(move.typeId.value),
                        )
                        assertTrue(policy.boundedAlternatives.isEmpty())
                        assertEquals(
                            listOf("Weather interaction for this move's type is unresolved."),
                            policy.unboundedUnknowns,
                        )
                    }
                }
            }
            checks.attempt("api.cache-bootstrap") {
                val parserInvocations = AtomicInteger()
                val completion = AtomicReference<Result<Unit>?>()
                val completed = CountDownLatch(1)
                ProductionCompanionRuntime(
                    initialSettings = if (requireDeclaredSigns || requireItemNames) com.enrpau.dualscreendex.companion.model.CompanionSettings(
                        knowledgeMode = com.enrpau.dualscreendex.companion.model.KnowledgeMode.DISCOVERED,
                    ) else com.enrpau.dualscreendex.companion.model.CompanionSettings(),
                    catalogRepository = cache,
                    parseCatalogWithCancellation = { _, _, _, _ ->
                        parserInvocations.incrementAndGet()
                        error("native cache bootstrap must not reparse")
                    },
                ).use { runtime ->
                    runtime.load(LoadedRom("native-control", rom)) { result ->
                        completion.set(result)
                        completed.countDown()
                    }
                    assertTrue("native cache reopen timed out", completed.await(30, TimeUnit.SECONDS))
                    requireNotNull(completion.get()).getOrThrow()
                    val bootstrap = runtime.bootstrap()
                    checks.attempt("api.authority") {
                        assertEquals(0, parserInvocations.get())
                        assertTrue(bootstrap.state.catalogReady)
                        assertEquals("CACHE_REOPEN", bootstrap.state.loading.phase)
                        assertEquals(rom.sha256, bootstrap.catalog?.hash)
                        assertEquals("ROM_DEFAULT", bootstrap.language?.authority)
                        assertEquals(language.value, bootstrap.language?.defaultLanguage)
                        assertEquals(language.value, bootstrap.language?.activeLanguage)
                        assertEquals(overlay?.overlayVersion, bootstrap.language?.activeOverlayVersion)
                        assertEquals(listOf(language.value), bootstrap.language?.projections?.map { it.language })
                    }
                    if (requireDeclaredSigns) checks.attempt("declared-sign.api.independent-samples") {
                        val state = runtime.stateView()
                        assertKoreanDeclaredSigns(reopened).forEach { (key, expected) ->
                            val numeric = reopened.localMaps.pois.single { it.key == key }
                            val poi = state.localMapPois.single { it.key == key }
                            assertEquals(expected, poi.displayName)
                            assertEquals(numeric.localMapKey, poi.localMapKey)
                            assertEquals(numeric.baseAreaId, poi.baseAreaId)
                            assertEquals(numeric.tileX, poi.tileX)
                            assertEquals(numeric.tileY, poi.tileY)
                            assertEquals(numeric.destinationBaseAreaId, poi.destinationBaseAreaId)
                        }
                        assertEquals(0, parserInvocations.get())
                        println("DECLARED_SIGN_API ${control.folder} samples=2 staticDeclaration=PASS zeroReparse=PASS")
                    }
                    val api = requireNotNull(bootstrap.catalog)
                    selectedDirectSign?.let { expected ->
                        checks.attempt("selected-direct-sign.api.same-capture") {
                            assertEquals("CACHE_REOPEN", bootstrap.state.loading.phase)
                            assertEquals(rom.sha256, api.hash)
                            val state = requireNotNull(bootstrap.language).projections.single().localizedCapabilities
                                .getValue(LocalizedTextCapability.POI_TEXT.name)
                            val snapshot = NativeSelectedDirectSignAssertions.api(expected, requireNotNull(selectedSignSqlite),
                                reopened.localMaps.maps, reopened.localMaps.pois, api.localMaps, runtime.stateView().localMapPois,
                                state.status, state.coveredRecords, state.expectedRecords)
                            selectedSignReceipt = NativeSelectedDirectSignAssertions.receipt(expected, control.folder, rom.sha256,
                                selectedSignProducer, selectedSignSqlite, snapshot, selectedSignMetadata,
                                originalReads.get(), originalParserInvocations.get(), parserInvocations.get())
                        }
                    }
                    control.contextualMaps?.let { expected ->
                        checks.attempt("contextual-maps.api.same-capture") {
                            assertEquals("CACHE_REOPEN", bootstrap.state.loading.phase)
                            assertEquals(rom.sha256, api.hash)
                            val state = requireNotNull(bootstrap.language).projections.single().localizedCapabilities
                                .getValue(LocalizedTextCapability.LOCAL_MAP_NAMES.name)
                            val reopenedText = reopened.defaultTextProjection()
                            val snapshot = NativeContextualMapAssertions.api(expected, reopened.localMaps.maps,
                                reopened.localMaps.maps.associate { it.key to reopenedText.localMapName(it.key) },
                                api.localMaps, state.status, state.coveredRecords, state.expectedRecords)
                            val receipt = NativeContextualMapAssertions.receipt(control.folder, rom.sha256, expected,
                                requireNotNull(contextualProducer), requireNotNull(contextualSqlite), snapshot,
                                cache.fileFor(rom.sha256).absolutePath, originalParserInvocations.get(), parserInvocations.get())
                            // Private same-capture evidence; never a claim that other NativeChecks passed.
                            println("NATIVE_CONTEXTUAL_MAP_RECEIPT $receipt")
                        }
                    }
                    if (itemExpectation != null) checks.attempt("item-names.api.${itemExpectation.count}.independent-samples") {
                        val apiItems = (api.balls.map { it.id to it.name } + runtime.stateView().localMapPois.mapNotNull { poi ->
                            poi.itemId?.let { it to poi.itemName }
                        }).groupBy({ it.first }, { it.second })
                        val nativeNames = requireNotNull(reopened.defaultLocalizedText()).itemNames.mapValues { it.value.value }
                        assertEquals(itemExpectation.count, nativeNames.size)
                        assertEquals(nativeNames.keys, apiItems.keys)
                        apiItems.forEach { (id, names) -> assertEquals(setOf(nativeNames.getValue(id)), names.toSet()) }
                        itemExpectation.samples.forEach { (id, hash) ->
                            assertEquals(hash, sha256(requireNotNull(apiItems.getValue(id).first()).toByteArray(StandardCharsets.UTF_8)))
                        }
                        if (language == LanguageTag.KOREAN) {
                            val numeric = reopened.localMaps.pois.filter { it.item != null }.associateBy { it.key }
                            val projected = runtime.stateView().localMapPois.filter { it.itemId != null }.associateBy { it.key }
                            assertEquals(225, numeric.size)
                            assertEquals(numeric.keys, projected.keys)
                            numeric.forEach { (key, poi) ->
                                val view = projected.getValue(key)
                                assertEquals(poi.localMapKey, view.localMapKey)
                                assertEquals(poi.baseAreaId, view.baseAreaId)
                                assertEquals(poi.tileX, view.tileX)
                                assertEquals(poi.tileY, view.tileY)
                                assertEquals(poi.destinationBaseAreaId, view.destinationBaseAreaId)
                                assertEquals(poi.item!!.itemId, view.itemId)
                                assertEquals(itemExpectation.allNames!!.getValue(requireNotNull(view.itemId)), view.itemName)
                            }
                            println("KOREAN_ITEM_API_REFERENCES ${control.folder} records=225 ids=62 numeric=PASS")
                        }
                        assertEquals(0, parserInvocations.get())
                        println("ITEM_NAME_API ${control.folder} names=${itemExpectation.count}/${itemExpectation.count} independentSamples=${itemExpectation.samples.size} zeroReparse=PASS")
                    }
                    if (directMoveProseControl) checks.attempt("api.move-prose.independent-samples") {
                        nativeMoveProseHashes(control).forEach { (id, expected) ->
                            val prose = requireNotNull(api.moves.single { it.id == id }.description)
                            assertEquals("independent API move-prose digest for move $id", expected,
                                sha256(prose.toByteArray(StandardCharsets.UTF_8)))
                        }
                    }
                    checks.attempt("api.sample.species") {
                        val species = api.species.single { it.id == speciesId }
                        assertEquals(control.speciesName, species.name)
                        assertTrue(requireNotNull(species.description).contains(control.dexFragment))
                        assertEquals(reopened.defaultTextProjection().speciesDescription(requireNotNull(speciesId)), species.description)
                        if (control.generation == 3) {
                            assertEquals(7, species.height)
                            assertEquals(69, species.weight)
                            val ability = species.abilities.single { it.id == 65 }
                            assertEquals("しんりょく", ability.name)
                            assertEquals("ピンチに くさの いりょくが あがる", ability.description)
                        }
                    }
                    checks.attempt("api.sample.move") {
                        val move = api.moves.single { it.id == 1 }
                        assertEquals(control.moveName, move.name)
                        assertEquals(reopened.defaultTextProjection().moveDescription(1), move.description)
                        if (control.generation >= 2) assertTrue(!move.description.isNullOrBlank())
                    }
                    checks.attempt("api.sample.types") {
                        control.typeSamples.keys.forEach { name -> assertTrue(api.types.any { it.name == name }) }
                    }
                    checks.attempt("api.LNG-B002.sample.world-location") {
                        assertTrue(api.worldMaps.flatMap { it.locations }.any { it.displayName == control.worldLocationName })
                    }
                    checks.attempt("api.LNG-B002.sample.local-location") {
                        assertTrue(api.localMaps.any { it.displayName == control.locationName })
                    }
                    checks.attempt("api.capabilities") {
                        val summary = requireNotNull(bootstrap.language).projections.single().localizedCapabilities
                        assertEquals(LocalizedTextCapability.entries.map { it.name }.toSet(), summary.keys)
                        requireNotNull(overlay).localizedCapabilities.forEach { (capability, state) ->
                            assertEquals(state.status.name, summary.getValue(capability.name).status)
                            assertEquals(state.coveredRecords, summary.getValue(capability.name).coveredRecords)
                            assertEquals(state.expectedRecords, summary.getValue(capability.name).expectedRecords)
                        }
                    }
                    // Equality checks every projected optional field, including unavailable text, against the reopened catalog.
                    checks.attempt("api.reopened-projection-parity") {
                        assertTrue(api == com.enrpau.dualscreendex.companion.api.ApiViewBuilder.catalog(reopened))
                    }
                }
            }
        } finally {
            checks.finish()
            // Emit only after EVERY same-capture NativeChecks boundary passed, including contextual maps.
            selectedSignReceipt?.let { println("NATIVE_SELECTED_DIRECT_SIGN_RECEIPT $it") }
        }
    }

    private fun assertOfficialConditionalWeatherPolicies(
        catalog: ParsedCatalog,
        control: NativeControl,
    ): Map<Int, DamageForecastAssembler.ConditionalWeatherPolicy> {
        val text = catalog.defaultTextProjection()
        return buildMap {
            control.typeSamples.forEach { (name, expectedRole) ->
                // Labels are independently pinned test oracles. IDs and move references come from this catalog.
                val type = catalog.typesById.values.single { text.typeName(it.id) == name }
                assertEquals(CapabilityStatus.AVAILABLE, type.semanticRole.status)
                assertEquals(expectedRole, type.semanticRole.value)
                val moves = catalog.movesById.values.filter { it.typeId.value == type.id }
                assertTrue("no actual move references for the independently decoded type", moves.isNotEmpty())
                moves.forEach { move ->
                    assertEquals(CapabilityStatus.AVAILABLE, move.typeId.status)
                    val policy = DamageForecastAssembler.conditionalWeatherPolicy(catalog, requireNotNull(move.typeId.value))
                    assertTrue(policy.unboundedUnknowns.isEmpty())
                    // This tests the forecast consumer's conditional policy, not weather existing in this engine.
                    when (expectedRole) {
                        TypeSemanticRole.FIRE, TypeSemanticRole.WATER -> {
                            assertEquals(1, policy.boundedAlternatives.size)
                            val modifier = policy.boundedAlternatives.single()
                            assertEquals(AppliedDamageCondition.WEATHER, modifier.kind)
                            assertEquals(1, modifier.minimumNumerator)
                            assertEquals(3, modifier.maximumNumerator)
                            assertEquals(2, modifier.denominator)
                            assertEquals(SemanticProof.SOURCE_VALIDATED, modifier.proof)
                        }
                        TypeSemanticRole.NORMAL -> assertTrue(policy.boundedAlternatives.isEmpty())
                        else -> error("native forecast sample needs an independent policy expectation")
                    }
                    put(move.id, policy)
                }
            }
        }
    }

    private class NativeChecks(private val control: NativeControl) {
        private val passed = mutableListOf<String>()
        private val failed = mutableListOf<String>()
        fun <T> attempt(boundary: String, block: () -> T): T? = try {
            block().also { passed += boundary }
        } catch (failure: AssertionError) {
            failed += "$boundary:${failure.javaClass.simpleName}"
            null
        } catch (failure: Exception) {
            failed += "$boundary:${failure.javaClass.simpleName}"
            null
        }
        fun finish() {
            val forecastStatus = when {
                failed.any { it.startsWith("$OFFICIAL_FORECAST_BOUNDARY:") || it.startsWith("$FAULT_INJECTED_FORECAST_BOUNDARY:") } -> "FAIL"
                OFFICIAL_FORECAST_BOUNDARY !in passed || FAULT_INJECTED_FORECAST_BOUNDARY !in passed -> "NOT_RUN"
                failed.isNotEmpty() -> "NOT_ACCEPTED"
                else -> "PASS"
            }
            // Only public control labels, hashes, statuses and boundary names: never paths, payloads or exception messages.
            println("NATIVE_E2E_RESULT ${control.folder} sha256=${control.sha256} passed=${passed.joinToString(",")} failed=${failed.joinToString(",")} " +
                "officialRomSemanticForecast=$forecastStatus liveBattleForecast=NOT_RUN")
            assertTrue("${control.folder}: ${failed.joinToString(",")}", failed.isEmpty())
        }
    }

    @Test
    fun redSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[0])

    @Test
    fun blueSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[1])

    @Test
    fun yellowSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[2])

    @Test
    fun goldRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[3])

    @Test
    fun silverRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[4])

    @Test
    fun crystalRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[5])

    @Test
    fun officialEmeraldSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[6])

    @Test
    fun modernEmeraldSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[7])

    @Test
    fun modernEmeraldAbilityMechanicsSurviveCatalogStoreAndApi() {
        val control = controls[7]
        val configured = System.getenv(control.environmentVariable)
        assumeTrue("set ${control.environmentVariable} to run this real-ROM control", !configured.isNullOrBlank())
        val romPath = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $romPath", Files.isRegularFile(romPath))
        val rom = RomImage(Files.readAllBytes(romPath))
        assertEquals(control.romSha256, rom.sha256)

        val catalog = requireNotNull(CatalogParser.parse(rom).catalog)
        val expectedIds = (1..81).toSet()
        assertEquals(expectedIds, catalog.abilitiesById.filterValues { it.mechanics.value?.isNotEmpty() == true }.keys)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.ABILITY_DESCRIPTIONS).status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.SPRITES).status)
        assertEquals("Helps repel wild Pokémon.", catalog.defaultTextProjection().abilityDescription(1))

        val root = newRoot()
        var server: AndroidLoopbackServer? = null
        try {
            val cache = CatalogCache(root.toFile(), JdbcTestCatalogDatabaseFactory)
            cache.write(
                catalog,
                CatalogSourceMetadata.direct(romPath.fileName.toString(), rom.size, "REAL-CONTROL"),
                CatalogWriteProgress.complete(),
            )
            assertDatabaseIntegrity(cache.fileFor(rom.sha256))
            val reopened = requireNotNull(cache.readComplete(rom.sha256)).catalog
            assertEquals(
                catalog.abilitiesById.mapValues { it.value.mechanics },
                reopened.abilitiesById.mapValues { it.value.mechanics },
            )
            assertEquals(catalog.localization, reopened.localization)
            assertEquals(CapabilityStatus.AVAILABLE, reopened.capabilities.getValue(RomCapability.SPRITES).status)

            val runtime = ProductionCompanionRuntime().apply { loadCatalog(romPath.fileName.toString(), reopened) }
            val apiCatalog = requireNotNull(runtime.bootstrap().catalog)
            assertEquals("AVAILABLE", apiCatalog.capabilities.getValue(RomCapability.SPRITES.name))
            val apiAbilities = apiCatalog.species
                .flatMap { it.abilities }
                .associateBy { it.id }
            val referencedExpectedIds = reopened.speciesById.values
                .flatMap { it.abilityIds.value.orEmpty() }
                .toSet()
                .intersect(expectedIds)
            assertEquals(expectedIds, referencedExpectedIds)
            assertEquals(referencedExpectedIds, apiAbilities.keys.intersect(expectedIds))
            assertEquals("Helps repel wild Pokémon.", apiAbilities.getValue(1).description)
            assertEquals(
                "Switch-in",
                apiAbilities.getValue(22).mechanics.single { it.kind == "STAT_STAGE" }.conditions.single().label,
            )
            assertEquals(
                "While affected by status",
                apiAbilities.getValue(62).mechanics.single { it.kind == "MULTIPLIER" }.conditions.single().label,
            )
            if (81 in referencedExpectedIds) {
                assertEquals(
                    "Damaging Normal-type moves",
                    apiAbilities.getValue(81).mechanics.single { it.kind == "TYPE_CHANGE" }.conditions.single().label,
                )
            }

            server = AndroidLoopbackServer(runtime) { null }.also { it.start() }
            val json = URI("http://127.0.0.1:${server.address.port}/api/bootstrap").toURL().readText()
            assertTrue(json.contains("Helps repel wild Pokémon."))
            assertTrue(json.contains("\"SPRITES\":\"AVAILABLE\""))
            assertTrue(json.contains("\"name\":\"Intimidate\""))
            assertTrue(json.contains("\"label\":\"Switch-in\""))
            if (81 in referencedExpectedIds) {
                assertTrue(json.contains("\"name\":\"Pixilate\""))
                assertTrue(json.contains("\"value\":\"Normal → Fairy\""))
            }
        } finally {
            server?.close()
            deleteTree(root)
        }
    }

    @Test
    fun officialGen3AbilityDescriptionsSurviveOverlayWithoutEnteringSharedMechanics() {
        val controls = listOf(
            Triple(
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - Ruby Version (USA, Europe) (Rev 2).gba",
                "0fdd36e92b75bed65d09df4635ab0b707b288c2bf1dc4c6e7a4a4f0eebe9d64c",
                "Ruby",
            ),
            Triple(
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - Sapphire Version (USA, Europe) (Rev 2).gba",
                "02ca41513580a8b780989dee428df747b52a0b1a55bec617886b4059eb1152fb",
                "Sapphire",
            ),
            Triple(
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - Emerald Version (USA, Europe).gba",
                "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af",
                "Emerald",
            ),
            Triple(
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - FireRed Version (USA, Europe) (Rev 1).gba",
                "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059",
                "FireRed",
            ),
            Triple(
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - LeafGreen Version (USA, Europe) (Rev 1).gba",
                "2f978f635b9593f6ca26ec42481c53a6b39f6cddd894ad5c062c1419fac58825",
                "LeafGreen",
            ),
        )
        controls.forEach { (rawPath, expectedSha, label) ->
            val romPath = Path.of(rawPath)
            assumeTrue("real ROM does not exist: $romPath", Files.isRegularFile(romPath))
            val rom = RomImage(Files.readAllBytes(romPath))
            assertEquals(label, expectedSha, rom.sha256)
            val catalog = requireNotNull(CatalogParser.parse(rom).catalog)
            assertEquals(label, (1..77).toSet(), catalog.abilitiesById.keys)
            val text = catalog.defaultTextProjection()
            assertTrue(
                "$label retained description-derived behavior in shared mechanics",
                catalog.abilitiesById.all { (abilityId, ability) ->
                    val description = text.abilityDescription(abilityId)
                    ability.mechanics.value.orEmpty().none { mechanic ->
                        mechanic.kind == AbilityMechanicKind.BEHAVIOR && mechanic.value == description
                    }
                },
            )
            assertEquals(
                label,
                "Defined but inactive in this engine",
                catalog.abilitiesById.getValue(76).mechanics.value.orEmpty()
                    .single { it.kind == AbilityMechanicKind.BEHAVIOR }.value,
            )

            val root = newRoot()
            try {
                val cache = CatalogCache(root.toFile(), JdbcTestCatalogDatabaseFactory)
                cache.write(
                    catalog,
                    CatalogSourceMetadata.direct(romPath.fileName.toString(), rom.size, label),
                    CatalogWriteProgress.complete(),
                )
                val reopened = requireNotNull(cache.readComplete(rom.sha256)).catalog
                assertEquals(label, catalog.abilitiesById, reopened.abilitiesById)
                assertEquals(label, catalog.localization, reopened.localization)
                assertEquals(
                    label,
                    "Defined but inactive in this engine",
                    reopened.abilitiesById.getValue(76).mechanics.value.orEmpty()
                        .single { it.kind == AbilityMechanicKind.BEHAVIOR }.value,
                )
                val api = requireNotNull(
                    ProductionCompanionRuntime().apply {
                        loadCatalog(romPath.fileName.toString(), reopened)
                    }.bootstrap().catalog,
                )
                val referencedAbilities = api.species.flatMap { it.abilities }.distinctBy { it.id }
                assertTrue(
                    "$label API omitted source-backed descriptions",
                    referencedAbilities.all { ability -> !ability.description.isNullOrBlank() },
                )
                assertTrue(
                    "$label API exposed description-derived behavior as shared mechanics",
                    referencedAbilities.none { ability ->
                        ability.mechanics.any { mechanic ->
                            mechanic.kind == "BEHAVIOR" && mechanic.value == ability.description
                        }
                    },
                )
            } finally {
                deleteTree(root)
            }
        }
    }

    @Test
    fun classicSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[8])

    @Test
    fun fireRedFourRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[9])

    @Test
    fun leafGreenFourRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[10])

    @Test
    fun darkCryDungeonBindingSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[11])

    @Test
    fun darkVioletFourRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[12])

    @Test
    fun cloverFourRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[13])

    @Test
    fun orangeRegionSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[14])

    @Test
    fun shinRedSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[15])

    @Test
    fun beyondRedSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[16])

    @Test
    fun unboundMapsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[17])

    @Test
    fun odysseyMapsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[18])

    private fun assertRoundTrip(control: Control) {
        val configured = System.getenv(control.environmentVariable)
        assumeTrue("set ${control.environmentVariable} to run this real-ROM control", !configured.isNullOrBlank())
        val romPath = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $romPath", Files.isRegularFile(romPath))
        val rom = RomImage(Files.readAllBytes(romPath))
        assertEquals(control.romSha256, rom.sha256)

        val catalog = requireNotNull(CatalogParser.parse(rom).catalog)
        assertEquals(control.pngHashes.size, catalog.worldMaps.regions.size)
        assertEquals(
            control.regionKeys,
            catalog.worldMaps.regions.map { it.key },
        )

        val root = newRoot()
        var server: AndroidLoopbackServer? = null
        try {
            val cache = CatalogCache(root.toFile(), JdbcTestCatalogDatabaseFactory)
            cache.write(
                catalog,
                CatalogSourceMetadata.direct(romPath.fileName.toString(), rom.size, "REAL-CONTROL"),
                CatalogWriteProgress.complete(),
            )
            assertDatabaseIntegrity(cache.fileFor(rom.sha256))
            val reopened = requireNotNull(cache.readComplete(rom.sha256)).catalog
            assertEquals(catalog.worldMaps, reopened.worldMaps)
            assertEquals(catalog.abilitiesById, reopened.abilitiesById)
            val parsedEvolutionEdges = catalog.navigableSpecies().associate { species ->
                species.id to species.evolutionEdges.value.orEmpty()
            }
            val reopenedEvolutionEdges = reopened.navigableSpecies().associate { species ->
                species.id to species.evolutionEdges.value.orEmpty()
            }
            assertEquals(parsedEvolutionEdges, reopenedEvolutionEdges)

            val runtime = ProductionCompanionRuntime().apply { loadCatalog(romPath.fileName.toString(), reopened) }
            val apiCatalog = requireNotNull(runtime.bootstrap().catalog)
            val referencedAbilities = apiCatalog.species.flatMap { it.abilities }.associateBy { it.id }
            val expectedReferencedAbilityIds = reopened.navigableSpecies()
                .flatMap { it.abilityIds.value.orEmpty() }
                .filter(reopened.abilitiesById::containsKey)
                .toSet()
            assertEquals(expectedReferencedAbilityIds, referencedAbilities.keys)
            if (reopened.capabilities.getValue(RomCapability.ABILITY_MECHANICS).status == CapabilityStatus.AVAILABLE) {
                assertTrue(
                    "${control.environmentVariable} omitted persisted ability mechanics from the API",
                    referencedAbilities.values.all { it.mechanics.isNotEmpty() },
                )
            }
            assertEquals(
                reopenedEvolutionEdges.values.sumOf(List<*>::size),
                apiCatalog.species.sumOf { it.evolutions.size },
            )
            server = AndroidLoopbackServer(runtime) { null }.also { it.start() }
            val base = "http://127.0.0.1:${server.address.port}"
            val actualPngHashes = reopened.worldMaps.regions.map { region ->
                val key = URLEncoder.encode(region.imageAssetKey, StandardCharsets.UTF_8)
                val response = URI("$base/api/maps/$key.png").toURL().openConnection() as HttpURLConnection
                assertEquals(region.imageAssetKey, 200, response.responseCode)
                assertEquals(region.imageAssetKey, "image/png", response.contentType)
                val bytes = response.inputStream.use { it.readBytes() }
                assertTrue(region.imageAssetKey, bytes.copyOfRange(1, 4).contentEquals("PNG".toByteArray()))
                sha256(bytes)
            }
            assertEquals(control.pngHashes, actualPngHashes)
            val localMapsToServe = buildList {
                control.localBaseAreaId?.let { baseAreaId ->
                    add(reopened.localMaps.maps.single { it.baseAreaId == baseAreaId })
                }
                reopened.localMaps.maps.firstOrNull()?.let(::add)
            }.distinctBy { it.imageAssetKey }
            localMapsToServe.forEach { localMap ->
                val key = URLEncoder.encode(localMap.imageAssetKey, StandardCharsets.UTF_8)
                val response = URI("$base/api/maps/$key.png").toURL().openConnection() as HttpURLConnection
                assertEquals(localMap.imageAssetKey, 200, response.responseCode)
                assertEquals(localMap.imageAssetKey, "image/png", response.contentType)
                val bytes = response.inputStream.use { it.readBytes() }
                val expected = requireNotNull(
                    LocalMapAssetRenderer.render(reopened.localMaps, localMap.imageAssetKey, MapLighting.DAY),
                ).bytes
                assertTrue(localMap.imageAssetKey, bytes.contentEquals(expected))
            }
        } finally {
            server?.close()
            deleteTree(root)
        }
    }

    private fun assertDatabaseIntegrity(file: File) {
        JdbcTestCatalogDatabaseFactory.open(file).use { database ->
            assertEquals(
                listOf("ok"),
                database.query("PRAGMA quick_check") { row -> row.string("quick_check") },
            )
            assertTrue(
                database.query("PRAGMA foreign_key_check") { row -> row.string("table") }.isEmpty(),
            )
        }
    }

    private fun newRoot(): Path {
        val configuredRoot = System.getenv("DUALDEX_TEST_TEMP_ROOT")?.takeIf(String::isNotBlank)
        val parent = configuredRoot?.let(Path::of)
        if (parent != null) Files.createDirectories(parent)
        return if (parent == null) Files.createTempDirectory("dualdex-map-roundtrip-")
        else Files.createTempDirectory(parent, "dualdex-map-roundtrip-")
    }

    private fun deleteTree(root: Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class Control(
        val environmentVariable: String,
        val romSha256: String,
        val regionKeys: List<String>,
        val pngHashes: List<String>,
        val localBaseAreaId: Int? = null,
    )

    private data class ThemeControl(val environmentVariable: String, val romSha256: String)

    private data class NativeControl(
        val folder: String,
        val sha256: String,
        val family: EngineFamily,
        val codecId: String,
        val generation: Int,
        val dexFragment: String,
        val locationName: String,
        val contextualMaps: NativeContextualExpectation? = null,
    ) {
        // Gen I World is an encounter-point domain: the town sample belongs to Local, not World.
        val worldLocationName = if (generation == 1) "トキワのもり" else locationName
        val speciesName = if (folder.startsWith("ko/")) "이상해씨" else "フシギダネ"
        val moveName = if (folder.startsWith("ko/")) "막치기" else "はたく"
        val typeSamples = if (folder.startsWith("ko/")) mapOf(
            "노말" to TypeSemanticRole.NORMAL, "화염" to TypeSemanticRole.FIRE, "물" to TypeSemanticRole.WATER,
        ) else mapOf(
            "ノーマル" to TypeSemanticRole.NORMAL, "ほのお" to TypeSemanticRole.FIRE, "みず" to TypeSemanticRole.WATER,
        )
    }

    private companion object {
        // Task420 independent selected structural oracle and UTF-8 headline digest, not parser output.
        // Public pokecrystal constants/charmap.asm SHA-256:
        // 417d4ff77af9bd44748dbf168d51e0103664532f7d3bae943d48b00a3146cc5a.
        // The separately reviewed static-literal declaration supplies the prefix; no opcode assumptions here.
        val japaneseCrystalSelectedSign = NativeSelectedDirectSignExpectation(
            "ja/CRYSTAL", "136ada06cb68656b7de475fa4b278d37dbeff8f5257e7dfdf7f4a4aec19a90f3",
            "local/1804", 0x1804, 20, 18, "local/1804/bg/0", 8, 8,
            "0623a1b7d00060bf32bfd45d17816e7d7fa75f88efb1076ef2e300e46639ba73",
        )

        // UTF-8 digests of independently decoded, whitespace-normalized compiled Ruby/Emerald
        // move records using the pinned pokeruby charmap below; not production-parser baselines.
        // Covers first/last moves plus short-learnset false negatives. No raw ROM prose is retained here.
        // Independently decoded 60-byte records; FireRed wording differs from Ruby/Emerald.
        // IDs 1/11/72/253/354 at first-record 0x423774 + (id-1)*60, pinned charmap below.
        val fireRedMoveProseHashes = mapOf(
            1 to "dd61cbb075f20d1d136adba5a49d464c548a95251d13fcd3603e36f97e6aa9eb",
            11 to "1185d3d939d338294c25ab93aa7fbf34969f92f9f2851cae405ff51c0c89823d",
            72 to "7145c56b3d8c848f62d615fa36dea6b214da6b24bbfbaadf3e9ccb173051dcf1",
            253 to "92212a09a5983b448e5d77ed4bd697308969c062b1238e4e069d0d893c32fb72",
            354 to "9c0f32ef838f1b80e3fec5cb8f770d90fddccd436d37ff75322547d87c3b8fae",
        )
        val rubyEmeraldMoveProseHashes = mapOf(
            1 to "6a560e56dbc81ff4d54a063ea1f3a39346aa1cd1667e62f8be962c72ec67edad",
            11 to "d67892aff9e60a4434cc332043dd0dd17490d9ab44a06d83b0ba04811c097379",
            72 to "5a90513a9d1c5eaba24fed32f8707e28538d9c8d2aee0cb642668a4bac5b1ff6",
            253 to "1b2b0799fe0bc62b6a3516352bc429acfd3f2ae2605598417a1b8975192e1f71",
            354 to "e77157a51610a80036160bd8b7c61ca486b1b705664f00c1c4240c31a746cc1c",
        )
        const val OFFICIAL_FORECAST_BOUNDARY = "LNG-D005.forecast.official-rom-semantic-policy"
        const val FAULT_INJECTED_FORECAST_BOUNDARY = "LNG-D005.forecast.fault-injected-authority-removed"

        // Task417: externally bound contextual keys from the four compiled selector/header receipts.
        // Historical pinned SQLite inventories retain GS/KO 368=364+4 and Crystal 388=382+6 maps.
        // These assert the unchanged numeric domain, never authorize native text from historical output.
        // Exact inputs match NativeOfficialLanguageLiveRomTest; hashes are test identities, never production routing.
        // Independent text oracles (not the production parser's output):
        // https://github.com/Narishma-gb/pokeyellow-jp/tree/f282e72ae26232790fdb780aa5a5db7ec8ebf572
        //   data/{pokemon/names,pokemon/dex_entries,moves/names,types/names,maps/names}.asm
        // https://github.com/Narishma-gb/pokegold-kr/tree/7743877dc9fa8603f4b6eaebe904a7ba03fdb9e4
        //   data/{pokemon/names,moves/names,moves/descriptions,types/names,maps/landmarks}.asm
        //   data/pokemon/dex_entries/{gold,silver}/bulbasaur.asm (distinct descriptions).
        // JA compiled-control snippets were independently decoded with pinned public charmaps, without parser imports:
        // https://github.com/luckytyphlosion/pokered-jp/blob/258d1a89ec49a2a0ccfbdd232ac0e5d96d00899a/charmap.asm
        // https://github.com/scr-trees/pokegold_jpcrystalvc/blob/f2b5db1deb0b8f2009d7e9d50b3bcb05ef8a9f53/charmap.asm
        // https://github.com/pret/pokeruby/blob/63a8cbf0016b351a4e68f7036fa0b77e23d2f2c1/charmap.txt
        // https://github.com/pret/pokeruby/blob/63a8cbf0016b351a4e68f7036fa0b77e23d2f2c1/include/constants/species.h
        //   SPECIES_BULBASAUR = 1, independently corroborated in all three six-byte compiled name tables.
        // Bulbasaur description starts: RB 0x421a2; Yellow 0x4061f; Gold 0x443e7; Crystal 0x44403;
        // Ruby 0x37db9c; Emerald 0x539c70; FireRed 0x404f1c. These are evidence metadata, never lookup code.
        // Native row 1 height/weight = 7/69 at roots Ruby 0x38474c, Emerald 0x54069c, FireRed 0x409c00.
        // Native ability row 65 independently decoded from paired 8-byte names / 19-byte prose tables:
        // Ruby 0x1cbc44/0x1cbeb4; Emerald 0x2ebdc4/0x2ec034; FireRed 0x207a8c/0x207cfc.
        // Gen II charmap checkout's English game text is NOT used as a Japanese description oracle.
        // Location labels: Gen I source names; JA Gen II compiled landmark names at 0x92632/0x926d7;
        // JA Gen III compiled names at Ruby 0x3becb0/Emerald 0x57c6e0 (and FireRed's native map-name table).
        // Applicable native town/description absence is LNG-B002, not waived by historical Western NOT_FOUND.
        // Forecast acceptance here is ROM-only conditional type policy through actual move references and SQLite reopen.
        // No battle sample/formula is supplied; live damage accuracy and engine weather applicability are not claimed.
        val nativeControls = listOf(
            NativeControl("ja/RED_BLUE", "3f0dc460ca8d06be1c9ac96307c939c0ea7baa366b40c2f1f4ad63242b6c4816", EngineFamily.RED_BLUE,
                "gb-gen1-ja-red-blue", 1, "うまれたときから", "マサラ"),
            NativeControl("ja/YELLOW", "1349408f328f633b33e059e654edabd19810530df9c883eda03a85d5bb10161a", EngineFamily.YELLOW,
                "gb-gen1-ja-yellow", 1, "なんにちだって", "マサラ"),
            NativeControl("ja/GOLD_SILVER", "27a07a1d3faf9c6a0b1b60d5e88ee3a4159a751a47b4c46ab09f1202d52bac3e", EngineFamily.GOLD_SILVER,
                "gb-gen2-ja", 2, "たっぷり。たねは", "ワカバタウン",
                contextualMaps = NativeContextualExpectation(368, setOf("local/1401", "local/1402", "local/1403", "local/1404"))),
            NativeControl("ja/CRYSTAL", "136ada06cb68656b7de475fa4b278d37dbeff8f5257e7dfdf7f4a4aec19a90f3", EngineFamily.CRYSTAL,
                "gb-gen2-ja", 2, "うまれて しばらく", "ワカバタウン",
                contextualMaps = NativeContextualExpectation(388, setOf("local/1401", "local/1402", "local/1403", "local/1404", "local/1405", "local/1406"))),
            NativeControl("ja/RUBY_SAPPHIRE", "a7ea012b67a27da2893bfdfcb5f64915607b26904b4fc635a1055e8e40e692ab", EngineFamily.RUBY_SAPPHIRE,
                "gba-gen3-ja-ruby-sapphire", 3, "ひなたで ひるねを", "ミシロタウン"),
            NativeControl("ja/EMERALD", "33f5610b9186b4add09fef68895deb00f552b997b3d133b5a961e5123506343c", EngineFamily.EMERALD,
                "gba-gen3-ja-emerald-frlg", 3, "ひなたで ひるねを", "ミシロタウン"),
            NativeControl("ja/FIRERED_LEAFGREEN", "cec5fc4dbe38cd8026bd6664a1a041d9dc91e8d4249bab04e7bde70c3cdf4e06", EngineFamily.FIRERED_LEAFGREEN,
                "gba-gen3-ja-emerald-frlg", 3, "うまれたときから", "マサラタウン"),
            NativeControl("ko/GOLD", "9c273e86e6120c6a038160ccb0153b8b20425b84fc08a496281c1d1bcac492f6", EngineFamily.GOLD_SILVER,
                "gb-gen2-ko", 2, "등의 씨앗 안에는", "연두마을",
                contextualMaps = NativeContextualExpectation(368, setOf("local/1401", "local/1402", "local/1403", "local/1404"))),
            NativeControl("ko/SILVER", "ebbac63c0c4309c82dbb6723e7163369784f962b4fd3e2f486075307c3008a22", EngineFamily.GOLD_SILVER,
                "gb-gen2-ko", 2, "태어날 때부터 등에 씨앗을", "연두마을",
                contextualMaps = NativeContextualExpectation(368, setOf("local/1401", "local/1402", "local/1403", "local/1404"))),
        )
        val themeControls = listOf(
            ThemeControl("DUALDEX_POKERED_ROM", "5ca7ba01642a3b27b0cc0b5349b52792795b62d3ed977e98a09390659af96b7b"),
            ThemeControl("DUALDEX_POKECRYSTAL_ROM", "fdcc3c8c43813cf8731fc037d2a6d191bac75439c34b24ba1c27526e6acdc8a2"),
            ThemeControl("DUALDEX_OFFICIAL_EMERALD_ROM", "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af"),
            ThemeControl("DUALDEX_UNBOUND_ROM", "7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7"),
            ThemeControl("DUALDEX_ODYSSEY_ROM", "44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0"),
        )
        val GEN2_PNGS = listOf(
            "23739bddf01b2c98a03ca1c4af28ade7d751623ec8063311dd2b8b366c81c516",
            "c06748683d60a89e4d2984bbcb565dc854ddd7942295d5039b80bcabe223258d",
        )
        val controls = listOf(
            Control(
                "DUALDEX_POKERED_ROM",
                "5ca7ba01642a3b27b0cc0b5349b52792795b62d3ed977e98a09390659af96b7b",
                listOf("gen1-kanto"),
                listOf("aa70952cb3c34789bc63639861d304b05b1c034dfb57e58720520de72d2ed098"),
            ),
            Control(
                "DUALDEX_POKEBLUE_ROM",
                "2a951313c2640e8c2cb21f25d1db019ae6245d9c7121f754fa61afd7bee6452d",
                listOf("gen1-kanto"),
                listOf("aa70952cb3c34789bc63639861d304b05b1c034dfb57e58720520de72d2ed098"),
            ),
            Control(
                "DUALDEX_POKEYELLOW_ROM",
                "8cbaa499397e4f1a679c992ea9382a2dd7942ab398b48c19829c2d9529de47bf",
                listOf("gen1-kanto"),
                listOf("aa70952cb3c34789bc63639861d304b05b1c034dfb57e58720520de72d2ed098"),
            ),
            Control(
                "DUALDEX_POKEGOLD_ROM",
                "fb0016d27b1e5374e1ec9fcad60e6628d8646103b5313ca683417f52b97e7e4e",
                listOf("gen2-johto", "gen2-kanto"),
                GEN2_PNGS,
            ),
            Control(
                "DUALDEX_POKESILVER_ROM",
                "72b190859a59623cbef6c49d601f8de52c1d2331b4f08a8d2acc17274fc19a8c",
                listOf("gen2-johto", "gen2-kanto"),
                GEN2_PNGS,
            ),
            Control(
                "DUALDEX_POKECRYSTAL_ROM",
                "fdcc3c8c43813cf8731fc037d2a6d191bac75439c34b24ba1c27526e6acdc8a2",
                listOf("gen2-johto", "gen2-kanto"),
                GEN2_PNGS,
            ),
            Control(
                "DUALDEX_OFFICIAL_EMERALD_ROM",
                "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af",
                listOf("gen3-region-0"),
                listOf("c9d5f2a5c77c0df16c14c73a15577f0c6f4a05794c191ebe72ed5a24724aadc6"),
            ),
            Control(
                "DUALDEX_MODERN_EMERALD_ROM",
                "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
                listOf("gen3-region-0"),
                listOf("80c4a69b9372276818768123dcd7cad09bcced88720704c8f424bc4501931ffe"),
                localBaseAreaId = 0x0009,
            ),
            Control(
                "DUALDEX_CLASSIC_ROM",
                "01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c",
                listOf("gen3-region-0"),
                listOf("0c171c9fe8175629aa47de4e2854a334a2025f21b9196ba2f4c57a8cdcbc67ec"),
            ),
            Control(
                "DUALDEX_FIRERED_ROM",
                "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059",
                (0..3).map { "gen3-region-$it" },
                listOf(
                    "c691c958253ff35595b36bf69f85d8d8940929c13deb7d0851ece717ab9d67aa",
                    "5bf5a1caf04a9bdbbbb80ea4dba5f9cdbf7d1eb046e7d29a85f6cacd392fbb70",
                    "d6f9b9aac3127691700f46e4df681ce6c1aee8a4f32c0f274b1df043dc47c160",
                    "2e1d951bf0cdf4181a43fc2e451428b067a7f9ba4307dfbc7a6eea237bf01765",
                ),
            ),
            Control(
                "DUALDEX_LEAFGREEN_ROM",
                "2f978f635b9593f6ca26ec42481c53a6b39f6cddd894ad5c062c1419fac58825",
                (0..3).map { "gen3-region-$it" },
                listOf(
                    "c691c958253ff35595b36bf69f85d8d8940929c13deb7d0851ece717ab9d67aa",
                    "5bf5a1caf04a9bdbbbb80ea4dba5f9cdbf7d1eb046e7d29a85f6cacd392fbb70",
                    "d6f9b9aac3127691700f46e4df681ce6c1aee8a4f32c0f274b1df043dc47c160",
                    "2e1d951bf0cdf4181a43fc2e451428b067a7f9ba4307dfbc7a6eea237bf01765",
                ),
            ),
            Control(
                "DUALDEX_DARK_CRY_ROM",
                "e61d4f66e2d4d39798bcd18f5abfb3db75282508fffd12401b9a1e9d0c1b08ed",
                (0..3).map { "gen3-region-$it" },
                listOf(
                    "9bc538416978211d88e36bd8440a423957517718c51c838895e0f67432ef35c0",
                    "4556ba8ff635a8a1f234c4825ed7825bfc3a50515e306bb3af1ddaf908b8b13e",
                    "6f1acba35c5bed020c07f060506bb9761bb2f8cc137fd670cf51eb3c03a580d9",
                    "cfec5c171fa388debf9fe8745be2b812589fda2cfe12ca487d7bebd5cf8e64f5",
                ),
            ),
            Control(
                "DUALDEX_DARK_VIOLET_ROM",
                "6b7e6df19c974371a4f80ea5c0f1e8d68a2cfee248faf34080a48ae3f0135e21",
                (0..3).map { "gen3-region-$it" },
                listOf(
                    "d5f07e96179d64e411ac4dec65c6b5d45fd190391b153a67c0a12927ab0a63bb",
                    "5a5da685c0211d1639f9de29c0749239db8ed22aa819b249e23bb940fa43c32c",
                    "e46976338b3b08670f1c2e846100a58bfc7f337ba92a0f989024aa357b0f8778",
                    "d7a86d7147422ba4dc09e72e14a1ba8c5bc3f2feafcb6e78cf4aac7875c7a68a",
                ),
            ),
            Control(
                "DUALDEX_CLOVER_ROM",
                "42f99abd548934d77999ac3eb563fb9bc70a34701d37a262b21b882a43a8bdd9",
                (0..3).map { "gen3-region-$it" },
                listOf(
                    "66ec72ca90e7220017cad597c5cc6be2c4901d214d467cd9c9749a1804da748a",
                    "0906e9ede556e27e166ebcd7610e3b09af40fd86cbd3066427a3d44eb95324bc",
                    "f8c5f9d281dd36609b1a62c256c777dcae3a105cb1d8eaa7052acdde728381bd",
                    "1b898a83a7cd7d677ea1aec26b6f9f5bece1dae2fc5096f5cfb2e8ab04cc34ac",
                ),
            ),
            Control(
                "DUALDEX_ORANGE_ROM",
                "037f5ba913953f2387175c5e0549347d162ef3b224d25660e8055acdac4564be",
                listOf("gen2-johto"),
                listOf("0234ec217a38e89e8c711128077bc75589327cdb3aff21de715c2b9550794244"),
            ),
            Control(
                "DUALDEX_SHIN_RED_ROM",
                "024a1c4dab1b12d0b963c6cf756d2c1082de0ccd53fe31384787dcf34edef718",
                listOf("gen1-kanto"),
                listOf("aa70952cb3c34789bc63639861d304b05b1c034dfb57e58720520de72d2ed098"),
            ),
            Control(
                "DUALDEX_BEYOND_RED_ROM",
                "3640ed0493287136cd9321cb3428f44113e87354cf90402665ba60e41c8fc61a",
                listOf("gen1-kanto"),
                listOf("8a958b8ada1dd6f1b25be40fe207f86dff88b016d443f70a20052cd6e6aa1275"),
            ),
            Control(
                "DUALDEX_UNBOUND_ROM",
                "7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7",
                listOf("gen3-region-0"),
                listOf("47e97e55526df3a85db6776d3554d84169f21d1618468fac2592238ef2e5cc7d"),
            ),
            Control(
                "DUALDEX_ODYSSEY_ROM",
                "44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0",
                (0..3).map { "gen3-region-$it" },
                listOf(
                    "70b94d44f4ee45651b3147395b7f40a65092e8774c84fd3b94c23647f1ae417a",
                    "7532f93f3c1070c8fbd341315981753cb3df60dce8d8e048f49ee6b9d76bcc33",
                    "790abca2ec290c272f3a99f678158ef14c7fa615316f823574b4b107e9a0ffa7",
                    "5ecec734a5eb76c0d59997fd95151083033cc910f03814135a4f0968805d18c5",
                ),
            ),
        )
    }
}

internal data class NativeContextualExpectation(val totalMaps: Int, val contextualKeys: Set<String>) {
    val staticRequired: Int get() = totalMaps - contextualKeys.size
}

/** Test-only accounting for the four externally bound controls; never a producer or ROM selector. */
internal object NativeContextualMapAssertions {
    fun names(expected: NativeContextualExpectation, maps: List<LocalMap>,
        names: Map<String, CatalogField<String>>, projected: Map<String, String?>,
        state: LocalizedCapabilityState): JsonObject {
        val keys = inventory(expected, maps)
        val staticKeys = keys - expected.contextualKeys
        assertEquals("ordinary names cannot disappear from the obligation domain", staticKeys, names.keys)
        assertEquals("projection must retain every numeric map key", keys, projected.keys)
        assertTrue("materialized shared map labels must remain isolated", maps.all { it.displayName == null })
        staticKeys.forEach { key ->
            val name = names.getValue(key)
            assertEquals("ordinary map $key", CapabilityStatus.AVAILABLE, name.status)
            assertTrue("ordinary map $key lacks a fixed native name", !name.value.isNullOrBlank())
            assertEquals("ordinary projection $key", name.value, projected.getValue(key))
        }
        expected.contextualKeys.forEach { key ->
            assertTrue("contextual map $key acquired a fixed projected name", projected.getValue(key) == null)
        }
        coverage(expected, state.status.name, state.coveredRecords, state.expectedRecords)
        return snapshot(expected, JsonArray().apply {
            maps.sortedBy { it.key }.forEach { map ->
                add(mapRow(map.key, map.baseAreaId, map.pixelWidth, map.pixelHeight, map.gridWidth,
                    map.gridHeight, projected.getValue(map.key)).apply {
                    addProperty("sharedDisplayName", map.displayName)
                    addProperty("nameDisposition", map.nameDisposition.name)
                })
            }
        }, staticKeys, state.status.name, state.coveredRecords, state.expectedRecords)
    }

    fun api(expected: NativeContextualExpectation, numeric: List<LocalMap>,
        projected: Map<String, String?>, maps: List<LocalMapView>,
        status: String, covered: Int, expectedRecords: Int): JsonObject {
        val keys = inventory(expected, numeric)
        assertEquals("API numeric map count", expected.totalMaps, maps.size)
        assertEquals("API must not collapse duplicate map keys", maps.size, maps.map { it.key }.toSet().size)
        assertEquals("API numeric map keys", keys, maps.mapTo(linkedSetOf()) { it.key })
        assertEquals("reopened projection keys", keys, projected.keys)
        val source = numeric.associateBy { it.key }
        maps.forEach { map ->
            val original = source.getValue(map.key)
            assertEquals("API numeric identity/dimensions ${map.key}",
                listOf(original.baseAreaId, original.pixelWidth, original.pixelHeight, original.gridWidth, original.gridHeight),
                listOf(map.baseAreaId, map.pixelWidth, map.pixelHeight, map.gridWidth, map.gridHeight))
            if (map.key in expected.contextualKeys) {
                assertTrue("contextual API name ${map.key}", map.displayName == null)
                assertTrue("contextual reopened name ${map.key}", projected.getValue(map.key) == null)
            } else {
                assertTrue("ordinary API name ${map.key}", !map.displayName.isNullOrBlank())
                assertEquals("ordinary API projection ${map.key}", projected.getValue(map.key), map.displayName)
            }
        }
        coverage(expected, status, covered, expectedRecords)
        return snapshot(expected, JsonArray().apply {
            maps.sortedBy { it.key }.forEach { map -> add(mapRow(map.key, map.baseAreaId, map.pixelWidth,
                map.pixelHeight, map.gridWidth, map.gridHeight, map.displayName)) }
        }, keys - expected.contextualKeys, status, covered, expectedRecords).apply {
            addProperty("dispositionSource", "REOPENED_NUMERIC_KEY_JOIN")
        }
    }

    fun receipt(control: String, sha256: String, expected: NativeContextualExpectation,
        producer: JsonObject, sqlite: JsonObject, api: JsonObject, sqliteDatabase: String,
        originalParserInvocations: Int, apiReparses: Int): JsonObject {
        assertEquals("same capture must use exactly one original parse invocation", 1, originalParserInvocations)
        assertEquals("cache-only API must never reparse", 0, apiReparses)
        assertEquals("fresh SQLite must preserve complete contextual/ordinary observations", producer, sqlite)
        return JsonObject().apply {
            addProperty("schemaVersion", 1)
            addProperty("scope", "GEN2_CONTEXTUAL_MAP_NAME_DISPOSITION_ONLY")
            // Other NativeChecks can still fail; this receipt is deliberately not overall acceptance.
            addProperty("overallNativeSemanticAcceptance", "NOT_CLAIMED")
            addProperty("control", control)
            addProperty("sha256", sha256)
            addProperty("parserSchemaVersion", CatalogSchema.parserSchemaVersion)
            addProperty("sqlSchemaVersion", CatalogSchema.version)
            add("expectation", JsonObject().apply {
                addProperty("totalMaps", expected.totalMaps)
                addProperty("staticRequired", expected.staticRequired)
                add("contextualKeys", stringArray(expected.contextualKeys))
            })
            add("producer", producer)
            add("sqlite", sqlite)
            add("api", api)
            addProperty("sqliteDatabase", sqliteDatabase)
            addProperty("originalParserInvocations", originalParserInvocations)
            addProperty("apiReparses", apiReparses)
        }
    }

    private fun mapRow(key: String, baseAreaId: Int, pixelWidth: Int, pixelHeight: Int,
        gridWidth: Int, gridHeight: Int, displayName: String?) = JsonObject().apply {
        addProperty("key", key)
        addProperty("baseAreaId", baseAreaId)
        addProperty("pixelWidth", pixelWidth)
        addProperty("pixelHeight", pixelHeight)
        addProperty("gridWidth", gridWidth)
        addProperty("gridHeight", gridHeight)
        addProperty("displayName", displayName)
    }

    private fun snapshot(expected: NativeContextualExpectation, rows: JsonArray, staticKeys: Set<String>,
        status: String, covered: Int, expectedRecords: Int) = JsonObject().apply {
        addProperty("totalMaps", rows.size())
        addProperty("staticRequired", staticKeys.size)
        add("staticNameRequiredMapKeys", stringArray(staticKeys))
        add("contextualKeys", stringArray(rows.map { it.asJsonObject["key"].asString }.toSet() - staticKeys))
        add("localMapNames", JsonObject().apply {
            addProperty("status", status)
            addProperty("coveredRecords", covered)
            addProperty("expectedRecords", expectedRecords)
        })
        add("maps", rows)
        assertEquals(expected.totalMaps, rows.size())
    }

    private fun stringArray(values: Set<String>) = JsonArray().apply { values.sorted().forEach { add(it) } }

    private fun inventory(expected: NativeContextualExpectation, maps: List<LocalMap>): Set<String> {
        assertTrue(expected.totalMaps > expected.contextualKeys.size && expected.contextualKeys.isNotEmpty())
        assertEquals("original numeric map denominator", expected.totalMaps, maps.size)
        val keys = maps.mapTo(linkedSetOf()) { it.key }
        assertEquals("numeric map keys must remain unique", maps.size, keys.size)
        assertEquals("numeric base-area IDs must remain unique", maps.size, maps.map { it.baseAreaId }.toSet().size)
        assertEquals("exact externally bound contextual keys", expected.contextualKeys,
            maps.filter { it.nameDisposition == LocalMapNameDisposition.CONTEXT_DEPENDENT }.mapTo(linkedSetOf()) { it.key })
        val staticKeys = maps.filter { it.nameDisposition == LocalMapNameDisposition.STATIC_NAME_REQUIRED }.mapTo(linkedSetOf()) { it.key }
        assertEquals("no ordinary map may be excluded", keys - expected.contextualKeys, staticKeys)
        assertEquals("static fixed-name denominator", expected.staticRequired, staticKeys.size)
        return keys
    }

    private fun coverage(expected: NativeContextualExpectation, status: String, covered: Int, expectedRecords: Int) {
        assertEquals("LOCAL_MAP_NAMES status", CapabilityStatus.AVAILABLE.name, status)
        assertEquals("LOCAL_MAP_NAMES expected ordinary domain", expected.staticRequired, expectedRecords)
        assertEquals("LOCAL_MAP_NAMES every ordinary name covered", expected.staticRequired, covered)
    }
}

internal object JdbcTestCatalogDatabaseFactory : CatalogDatabaseFactory {
    override fun open(file: File): CatalogDatabase {
        Class.forName("org.sqlite.JDBC")
        return JdbcTestCatalogDatabase(DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}"))
    }
}

private class JdbcTestCatalogDatabase(private val connection: Connection) : CatalogDatabase {
    override fun <T> transaction(block: () -> T): T {
        val original = connection.autoCommit
        connection.autoCommit = false
        return try {
            block().also { connection.commit() }
        } catch (failure: Throwable) {
            connection.rollback()
            throw failure
        } finally {
            connection.autoCommit = original
        }
    }

    override fun execute(sql: String, arguments: List<Any?>) {
        connection.prepareStatement(sql).use { statement ->
            arguments.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }
    }

    override fun <T> query(sql: String, arguments: List<Any?>, map: (CatalogRow) -> T): List<T> =
        connection.prepareStatement(sql).use { statement ->
            arguments.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(map(object : CatalogRow {
                            override fun string(column: String): String? = result.getString(column)
                            override fun long(column: String): Long? = result.getLong(column).takeUnless { result.wasNull() }
                            override fun bytes(column: String): ByteArray? = result.getBytes(column)
                        }))
                    }
                }
            }
        }

    override fun <T> streamQuery(
        sql: String,
        arguments: List<Any?>,
        consume: (com.darkaxt.dualdex.catalog.CatalogRows) -> T,
    ): T = connection.prepareStatement(sql).use { statement ->
        arguments.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeQuery().use { result ->
            consume(com.darkaxt.dualdex.catalog.CatalogRows {
                if (!result.next()) null else object : CatalogRow {
                    override fun string(column: String): String? = result.getString(column)
                    override fun long(column: String): Long? = result.getLong(column).takeUnless { result.wasNull() }
                    override fun bytes(column: String): ByteArray? = result.getBytes(column)
                }
            })
        }
    }

    override fun close() = connection.close()
}
