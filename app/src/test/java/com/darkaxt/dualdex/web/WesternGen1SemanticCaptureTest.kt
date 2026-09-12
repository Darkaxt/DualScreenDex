package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.enrpau.dualscreendex.companion.api.LanguageBootstrapView
import com.enrpau.dualscreendex.companion.api.LanguageProjectionView
import com.enrpau.dualscreendex.companion.api.LocalizedCapabilityView
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.parser.io.LoadedRom
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.*
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.catalog.*
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.google.gson.*
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test

/** Fabricated metadata/observations only. Never opens private evidence or original controls. */
class WesternGen1SemanticCaptureTest {
    @Test fun exactTenControlOracleRetainsRawAndDisplayNames() {
        val f = fixture()
        assertEquals(f.proof, WesternGen1SemanticOracle.strictJson(f.proof.toString().toByteArray(Charsets.UTF_8)))
        val result = decode(f)
        assertEquals(10, result.size)
        assertEquals(620, result.values.sumOf { it.rawNames.size })
        assertEquals(1585, result.values.sumOf { it.references.size })
        assertTrue(result.values.all { it.rawNames.getValue(1) == "SYNTHETIC  LABEL" })
        assertTrue(result.values.all { it.names.getValue(1) == "SYNTHETIC LABEL" })
    }

    @Test fun independentDisplayNormalizationIsWhitespaceOnly() {
        assertEquals("MÁS PP", WesternGen1SemanticOracle.displayName("  MÁS  PP  "))
        assertEquals("A B C", WesternGen1SemanticOracle.displayName("A\tB\nC"))
        assertEquals("É-é+", WesternGen1SemanticOracle.displayName("É-é+"))
        assertNotEquals(WesternGen1SemanticOracle.displayName("é"), WesternGen1SemanticOracle.displayName("é"))
        assertEquals("A B", WesternGen1SemanticOracle.displayName("A B"))
    }

    @Test fun invalidOptInAndExternalPinPreventAnyMetadataRead() {
        for (optIn in listOf(null, "", "0", "true", " 1", "1 ")) {
            var reads = 0
            assertThrows(IllegalArgumentException::class.java) {
                WesternGen1SemanticOracle.preflight(optIn, WesternGen1SemanticOracle.proofSha256) { reads++ }
            }
            assertEquals(0, reads)
        }
        for (hash in listOf(null, "", "f".repeat(64))) {
            var reads = 0
            assertThrows(IllegalArgumentException::class.java) {
                WesternGen1SemanticOracle.preflight("1", hash) { reads++ }
            }
            assertEquals(0, reads)
        }
    }

    @Test fun wrongHashEmptyAndOversizedProofRejectBeforeParsing() {
        val f = fixture()
        for (bytes in listOf(byteArrayOf(), f.proof.toString().toByteArray(), ByteArray(WesternGen1SemanticOracle.maxProofBytes + 1))) {
            assertTrue(runCatching { WesternGen1SemanticOracle.decodePinnedProof(bytes) }.isFailure)
        }
    }

    @Test fun strictJsonRejectsDuplicateMembersTrailingAndMalformedData() {
        for (text in listOf("", "{", "{unquoted:true}", "{\"a\":1,\"a\":2}", "{} {}", "{\"a\":NaN}", "[1,]")) {
            assertTrue(text, runCatching { WesternGen1SemanticOracle.strictJson(text.toByteArray()) }.isFailure)
        }
        assertTrue(runCatching { WesternGen1SemanticOracle.strictJson(byteArrayOf(0xc3.toByte(), 0x28)) }.isFailure)
    }

    @Test fun oracleRejectsMissingDuplicateExtraAndMisboundControls() {
        mutations(
            { it.proof.getAsJsonArray("controls").remove(0) },
            { it.proof.getAsJsonArray("controls").add(it.row().deepCopy()) },
            { it.proof.getAsJsonArray("controls").set(1, it.row().deepCopy()) },
            { it.row().getAsJsonObject("control").addProperty("sha256", "f".repeat(64)) },
            { it.row().getAsJsonObject("control").addProperty("file", "different-synthetic-path") },
            { it.row().getAsJsonObject("control").addProperty("family", "GOLD_SILVER") },
            { it.row().getAsJsonObject("control").addProperty("size", 2097152) },
            { it.manifest[0].addProperty("sha256", "f".repeat(64)) },
        )
    }

    @Test fun oracleRejectsPartialProofAndImportedMembershipOnly() {
        mutations(
            { it.proof.addProperty("status", "SUPPLEMENTAL_MEMBERSHIP_ONLY") },
            { it.row().addProperty("staticProofComplete", false) },
            { it.row().getAsJsonArray("errors").add("REFERENCE_GATE") },
            { it.row().getAsJsonObject("counts").addProperty("nominationPasses", 0) },
            { it.row().getAsJsonObject("counts").addProperty("nominationScanBytes", 1) },
            { it.row().getAsJsonObject("counts").addProperty("wrapperCandidates", 2) },
            { it.row().addProperty("inputReads", 2) },
            { it.row().addProperty("inputSha256", "f".repeat(64)) },
            { it.row().getAsJsonArray("references")[0].asJsonObject.addProperty("status", "UNPROVED") },
        )
    }

    @Test fun oracleRejectsMissingExtraDuplicateAndWrongRequestedDomains() {
        mutations(
            { it.row().getAsJsonArray("requestedIds").remove(0) },
            { it.row().getAsJsonArray("requestedIds").add(1) },
            { it.row().getAsJsonArray("requestedIds").set(0, JsonPrimitive(255)) },
            { it.row().getAsJsonObject("derivedLabels").remove("1") },
            { it.row().getAsJsonObject("derivedLabels").addProperty("255", "EXTRA") },
            { it.row().getAsJsonObject("derivedLabels").addProperty("01", "ALIAS") },
            { it.row().getAsJsonObject("fields").remove("1") },
            { it.row().getAsJsonObject("fields")["1"].asJsonObject.addProperty("status", "UNAVAILABLE") },
            { it.row().getAsJsonObject("fields")["1"].asJsonObject.addProperty("label", "WRONG") },
            { it.row().getAsJsonObject("derivedLabels").addProperty("1", " ") },
            { it.row().getAsJsonObject("derivedLabels").addProperty("1", 123) },
        )
    }

    @Test fun oracleBindsEveryOriginalGenOneReferenceFieldAndOrder() {
        mutations(
            { it.row().getAsJsonArray("originalReferences").remove(0) },
            { it.row().getAsJsonArray("originalReferences").set(1, it.ref().deepCopy()) },
            { it.ref().addProperty("quantity", 1) },
            { it.ref().remove("mapPointerTable") },
            { it.ref().addProperty("operandOffset", 100) },
            { it.ref().addProperty("sourceBank", 255) },
            { it.ref().addProperty("mapHeader", 100) },
            { it.row().getAsJsonArray("references")[0].asJsonObject.addProperty("itemId", 2) },
        )
    }

    @Test fun oracleRejectsNumericPoiFlagGeometryAndIdentityMutations() {
        mutations(
            { it.poi().addProperty("tileX", 99) },
            { it.poi().addProperty("baseAreaId", 99) },
            { it.poi().addProperty("localMapKey", "wrong-map") },
            { it.poi().getAsJsonObject("item").addProperty("itemId", 2) },
            { it.poi().getAsJsonObject("item").addProperty("collectionFlagId", 3) },
            { it.row().getAsJsonArray("originalNumericPois").set(1, it.poi().deepCopy()) },
        )
    }

    @Test fun requiredNamesRejectMissingWrongUnavailableAndExtraAtEveryFieldBoundary() {
        val raw = mapOf(1 to "SYNTHETIC  LABEL", 2 to "SECOND")
        val correct = mapOf(1 to CatalogField.available("SYNTHETIC LABEL"), 2 to CatalogField.available("SECOND"))
        for (stage in listOf("producer", "overlay", "sqlite.overlay")) {
            for (bad in listOf(correct - 2, correct + (2 to CatalogField.available("WRONG")),
                correct + (2 to CatalogField.notFound<String>("missing")), correct + (3 to CatalogField.available("EXTRA")))) {
                val out = linkedMapOf<String, Result<Unit>>()
                WesternGen1SemanticCapture.checkRequiredItemObservations(raw, setOf(1, 2), mapOf(stage to bad),
                    LocalizedCapabilityState.available(2)) { key, block -> out[key] = runCatching(block) }
                assertTrue(out.values.any { it.isFailure })
                assertTrue(out.getValue("item-capability.covered").isSuccess)
            }
        }
    }

    @Test fun projectedNamesRequireEveryOccurrenceAndExactDisplayForm() {
        val raw = mapOf(1 to "SYNTHETIC  LABEL")
        for (bad in listOf(emptyMap(), mapOf(1 to emptyList()), mapOf(1 to listOf<String?>(null)),
            mapOf(1 to listOf("SYNTHETIC LABEL", "WRONG")), mapOf(1 to listOf("SYNTHETIC  LABEL")),
            mapOf(1 to listOf("SYNTHETIC LABEL"), 2 to listOf("EXTRA")))) {
            val out = linkedMapOf<String, Result<Unit>>()
            WesternGen1SemanticCapture.checkRequiredProjectedNames(raw, bad) { key, block -> out[key] = runCatching(block) }
            assertTrue(out.values.any { it.isFailure })
        }
        WesternGen1SemanticCapture.checkRequiredProjectedNames(raw, mapOf(1 to listOf("SYNTHETIC LABEL", "SYNTHETIC LABEL"))) { _, block -> block() }
    }

    @Test fun requiredCapabilitiesDoNotCollapseDenominators() {
        val out = linkedMapOf<String, Result<Unit>>()
        WesternGen1SemanticCapture.checkRequiredItemObservations(mapOf(1 to "ONE", 2 to "TWO"), setOf(1),
            mapOf("producer" to mapOf(1 to CatalogField.available("ONE"))), LocalizedCapabilityState.available(1)) {
            key, block -> out[key] = runCatching(block)
        }
        for (key in listOf("requested-domain", "producer.denominator", "item-capability.expected", "item-capability.covered")) assertTrue(out.getValue(key).isFailure)
    }

    @Test fun nonItemBaselineComparisonRetainsAllOtherCapabilitiesAndSharedData() {
        val baseline = snapshot()
        val itemOnly = baseline.deepCopy()
        val overlay = itemOnly.getAsJsonObject("localization").getAsJsonObject("overlays")["synthetic"].asJsonObject
        overlay.getAsJsonObject("itemNames").addProperty("1", "NEW ITEM NAME")
        overlay.getAsJsonObject("localizedCapabilities").add("ITEM_NAMES", JsonObject())
        WesternGen1SemanticCapture.checkUnchangedBaseline(baseline, itemOnly)
        val mutations: List<(JsonObject) -> Unit> = listOf(
            { it.getAsJsonObject("capabilities").addProperty("SPECIES_CATALOG", "changed") },
            { it.getAsJsonArray("pois").add("changed shared POI") },
            { it.getAsJsonObject("balls").addProperty("1", "changed shared ball") },
            { it.getAsJsonArray("diagnostics").add("changed diagnostic") },
            { it.getAsJsonObject("localization").getAsJsonObject("overlays")["synthetic"].asJsonObject.getAsJsonObject("localizedCapabilities").remove("SPECIES_NAMES") },
            { it.getAsJsonObject("localization").getAsJsonObject("overlays")["synthetic"].asJsonObject.addProperty("speciesNames", "changed non-item text") },
        )
        mutations.forEach { mutation ->
            val changed = baseline.deepCopy(); mutation(changed)
            assertTrue(runCatching { WesternGen1SemanticCapture.checkUnchangedBaseline(baseline, changed) }.isFailure)
        }
    }

    @Test fun fabricatedNamesSurviveRealSqliteAndCacheOnlyRuntimeWithoutParser() {
        val rom = RomImage(ByteArray(512) { (it * 7).toByte() })
        val language = LanguageTag.ENGLISH
        val manifest = RomLanguageManifest(language, listOf(RomLanguageProjection(language, "gb-gen1-en", 1,
            LocalizedTableLayout(), emptyList(), LanguageResolutionStatus.RESOLVED)), LanguageResolutionStatus.RESOLVED)
        val fields = mapOf(1 to CatalogField.available("SYNTHETIC LABEL"), 2 to CatalogField.available("SECOND"))
        val states = LocalizedTextCapability.entries.associateWith {
            when (it) {
                LocalizedTextCapability.ITEM_NAMES -> LocalizedCapabilityState.available(2)
                LocalizedTextCapability.POI_TEXT -> LocalizedCapabilityState.available(1)
                LocalizedTextCapability.LOCAL_MAP_NAMES -> LocalizedCapabilityState.notFound("synthetic", 1)
                else -> LocalizedCapabilityState.notApplicable("synthetic", 0)
            }
        }
        val raw = mapOf(1 to "SYNTHETIC  LABEL", 2 to "SECOND")
        val catalog = ParsedCatalog(romSha256 = rom.sha256, romCrc32 = rom.crc32, family = EngineFamily.RED_BLUE, platform = Platform.GB,
            captureBallsById = mapOf(1 to CaptureBallRecord(1, CatalogField.notApplicable("overlay"), CatalogField.notApplicable("synthetic"))),
            localMaps = LocalMapCatalog(maps = listOf(LocalMap("synthetic", null, 1, 16, 16, 1, 1, "synthetic/map")),
                assets = mapOf("synthetic/map" to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))),
                pois = listOf(LocalMapPoi("synthetic/hidden/0", "synthetic", 1, 0, 0, LocalMapPoiKind.HIDDEN_ITEM,
                    organicVisibility = LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE,
                    textObligation = LocalMapPoiTextObligation.ITEM_NAME,
                    item = LocalMapPoiItem(itemId = 2, collectionFlagId = 0)))),
            localization = CatalogLocalization(manifest, mapOf(language to CatalogLanguageOverlay(language, 1, states, itemNames = fields))))
        val configured = System.getenv("DUALDEX_TEST_TEMP_ROOT")?.let(Path::of)
        val root = if (configured == null) Files.createTempDirectory("gen1-semantic-sqlite-") else Files.createTempDirectory(configured, "gen1-semantic-sqlite-")
        val cache = CatalogCache(root.toFile(), JdbcTestCatalogDatabaseFactory)
        cache.write(catalog, CatalogSourceMetadata.direct("fabricated", rom.size, "SYNTHETIC"), CatalogWriteProgress.complete())
        val reopened = requireNotNull(cache.readComplete(rom.sha256)).catalog
        assertEquals(catalog, reopened)
        WesternGen1SemanticCapture.checkRequiredItemObservations(raw, setOf(1, 2), mapOf("sqlite.overlay" to reopened.defaultLocalizedText()?.itemNames),
            reopened.defaultLocalizedText()?.localizedCapabilities?.get(LocalizedTextCapability.ITEM_NAMES)) { _, block -> block() }
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val done = java.util.concurrent.CountDownLatch(1)
        val completion = java.util.concurrent.atomic.AtomicReference<Result<Unit>?>()
        ProductionCompanionRuntime(initialSettings = CompanionSettings(knowledgeMode = KnowledgeMode.DISCOVERED), catalogRepository = cache,
            parseCatalogWithCancellation = { _, _, _, _ -> calls.incrementAndGet(); error("synthetic cache gate must not parse") }).use { runtime ->
            runtime.load(LoadedRom("fabricated", rom)) { result -> completion.set(result); done.countDown() }
            assertTrue(done.await(30, java.util.concurrent.TimeUnit.SECONDS)); requireNotNull(completion.get()).getOrThrow()
            val bootstrap = runtime.bootstrap()
            WesternGen1SemanticCapture.checkApiLanguage(reopened, requireNotNull(bootstrap.language)) { _, block -> block() }
            assertEquals("CACHE_REOPEN", bootstrap.state.loading.phase)
            val actual = (requireNotNull(bootstrap.catalog).balls.map { it.id to it.name } + runtime.stateView().localMapPois.mapNotNull { poi ->
                poi.itemId?.let { it to poi.itemName }
            }).groupBy({ it.first }, { it.second })
            WesternGen1SemanticCapture.checkRequiredProjectedNames(raw, actual) { _, block -> block() }
            val apiStates = requireNotNull(bootstrap.language).projections.single().localizedCapabilities
            assertEquals(15, apiStates.size)
            states.forEach { (capability, expected) ->
                val actualState = apiStates.getValue(capability.name)
                assertEquals(expected.status.name, actualState.status)
                assertEquals(expected.expectedRecords, actualState.expectedRecords); assertEquals(expected.coveredRecords, actualState.coveredRecords)
            }
        }
        assertEquals(0, calls.get())
    }

    @Test fun apiLanguageRejectsEveryManifestAndProjectionMutation() {
        val (catalog, valid) = languageFixture()
        WesternGen1SemanticCapture.checkApiLanguage(catalog, valid) { _, block -> block() }
        val projection = valid.projections.single()
        val mutations = listOf(valid.copy(manifestStatus = "NOT_FOUND"),
            valid.copy(defaultLanguage = "fr"), valid.copy(activeLanguage = "fr"), valid.copy(authority = "SAVE"),
            valid.copy(activeOverlayVersion = 99), valid.copy(projections = emptyList()),
            valid.copy(projections = listOf(projection, projection))) + listOf(
            projection.copy(language = "fr"), projection.copy(status = "NOT_FOUND"),
            projection.copy(codecId = "gb-gen2-en"), projection.copy(codecVersion = 99), projection.copy(overlayVersion = 99)
        ).map { valid.copy(projections = listOf(it)) }
        val escaped = mutations.mapIndexedNotNull { index, mutated ->
            val outcomes = mutableListOf<Result<Unit>>()
            WesternGen1SemanticCapture.checkApiLanguage(catalog, mutated) { _, block -> outcomes += runCatching(block) }
            index.takeIf { outcomes.all { it.isSuccess } }
        }
        assertEquals("manifest/projection mutations escaped", emptyList<Int>(), escaped)
    }

    @Test fun apiLanguageRejectsEveryCapabilityFieldMutation() {
        val (catalog, valid) = languageFixture()
        val projection = valid.projections.single()
        val escaped = mutableListOf<String>()
        for ((key, state) in projection.localizedCapabilities) {
            val mutants = mapOf("status" to state.copy(status = "WRONG"), "confidence" to state.copy(confidence = 0.25),
                "coveredRecords" to state.copy(coveredRecords = 99), "expectedRecords" to state.copy(expectedRecords = 99),
                "incompleteRecords" to state.copy(incompleteRecords = 99), "reviewStatus" to state.copy(reviewStatus = "WRONG"),
                "validatorReviewRecommended" to state.copy(validatorReviewRecommended = !state.validatorReviewRecommended))
            for ((field, mutant) in mutants) {
                val view = valid.copy(projections = listOf(projection.copy(localizedCapabilities = projection.localizedCapabilities + (key to mutant))))
                val outcomes = mutableListOf<Result<Unit>>()
                WesternGen1SemanticCapture.checkApiLanguage(catalog, view) { _, block -> outcomes += runCatching(block) }
                if (outcomes.all { it.isSuccess }) escaped += "$key.$field"
            }
        }
        assertEquals("capability field mutations escaped", emptyList<String>(), escaped)
    }

    private fun languageFixture(): Pair<ParsedCatalog, LanguageBootstrapView> {
        val language = LanguageTag.ENGLISH
        val manifest = RomLanguageManifest(language, listOf(RomLanguageProjection(language, "gb-gen1-en", 1,
            LocalizedTableLayout(), emptyList(), LanguageResolutionStatus.RESOLVED)), LanguageResolutionStatus.RESOLVED)
        val states = LocalizedTextCapability.entries.associateWith {
            if (it == LocalizedTextCapability.ITEM_NAMES) LocalizedCapabilityState.available(2)
            else LocalizedCapabilityState.notApplicable("synthetic", 0)
        }
        val catalog = ParsedCatalog(romSha256 = "0".repeat(64), romCrc32 = "00000000", family = EngineFamily.RED_BLUE, platform = Platform.GB,
            captureBallsById = (1..2).associateWith { CaptureBallRecord(it, CatalogField.notApplicable("overlay"), CatalogField.notApplicable("synthetic")) },
            localization = CatalogLocalization(manifest, mapOf(language to CatalogLanguageOverlay(language, 1, states,
                itemNames = mapOf(1 to CatalogField.available("ONE"), 2 to CatalogField.available("TWO"))))))
        val views = states.mapKeys { it.key.name }.mapValues { (_, state) -> LocalizedCapabilityView(state.status.name, state.confidence,
            state.coveredRecords, state.expectedRecords, state.incompleteRecords, state.reviewStatus.name, state.validatorReviewRecommended) }
        return catalog to LanguageBootstrapView("RESOLVED", "en", "en", "ROM_DEFAULT", 1,
            listOf(LanguageProjectionView("en", "RESOLVED", "gb-gen1-en", 1, 1, views)))
    }

    @Test fun terminalReceiptRequiresEntirePathAndRetainsEarlierFailures() {
        val configured = System.getenv("DUALDEX_TEST_TEMP_ROOT")?.let(Path::of)
        val root = if (configured == null) Files.createTempDirectory("gen1-semantic-synthetic-") else Files.createTempDirectory(configured, "gen1-semantic-synthetic-")
        val receipt = WesternGen1SemanticCapture.Receipt(root, JsonObject())
        receipt.finish()
        assertEquals(false, receipt.values["semanticAcceptance"])
        receipt.check("producer") { assertEquals("expected", "wrong") }
        receipt.check("sqlite") { assertTrue(true) }
        receipt.check("api") { assertTrue(true) }
        receipt.values["semanticCheckPathComplete"] = true
        receipt.finish()
        assertEquals(false, receipt.values["semanticAcceptance"])
        assertEquals("PASS", receipt.checks["api"])
        assertEquals("FAIL", receipt.checks["producer"])
    }

    private data class Fixture(val proof: JsonObject, val manifest: List<JsonObject>, val bundles: List<JsonObject>, val snapshots: Map<String, JsonObject>) {
        fun row() = proof.getAsJsonArray("controls")[0].asJsonObject
        fun ref() = row().getAsJsonArray("originalReferences")[0].asJsonObject
        fun poi() = row().getAsJsonArray("originalNumericPois")[0].asJsonObject
    }
    private fun decode(f: Fixture) = WesternGen1SemanticOracle.bind(f.proof, f.manifest, f.bundles, f.snapshots)
    private fun mutations(vararg changes: (Fixture) -> Unit) = changes.forEachIndexed { index, change ->
        val f = fixture(); change(f)
        assertTrue("mutation $index", runCatching { decode(f) }.isFailure)
    }
    private fun snapshot() = JsonObject().apply {
        add("languageManifest", JsonObject()); add("capabilities", JsonObject()); add("diagnostics", JsonArray())
        add("balls", JsonObject()); add("pois", JsonArray())
        add("localization", JsonObject().apply {
            add("overlays", JsonObject().apply { add("synthetic", JsonObject().apply {
                add("itemNames", JsonObject()); add("speciesNames", JsonObject())
                add("localizedCapabilities", JsonObject().apply { LocalizedTextCapability.entries.forEach { add(it.name, JsonObject()) } })
            }) })
        })
    }
    private fun fixture(): Fixture {
        val manifest = mutableListOf<JsonObject>(); val bundles = mutableListOf<JsonObject>(); val snapshots = linkedMapOf<String, JsonObject>(); val rows = JsonArray()
        WesternGen1BaselineCapture.controlHashes.forEach { (cell, hash) ->
            val control = JsonObject().apply {
                addProperty("language", cell.first); addProperty("family", cell.second); addProperty("sha256", hash)
                addProperty("file", "synthetic-not-a-rom-$hash"); addProperty("size", 1048576)
            }
            manifest += control.deepCopy()
            val ids = JsonArray().apply { (1..62).forEach { add(it) } }; val refs = JsonArray(); val pois = JsonArray(); val provedRefs = JsonArray()
            val visible = if (cell.second == "YELLOW") 108 else 104
            repeat(if (cell.second == "YELLOW") 161 else 156) { index ->
                val hidden = index >= visible; val id = index % 62 + 1; val key = "local/0001/${if (hidden) "hidden" else "object"}/$index"
                refs.add(JsonObject().apply {
                    addProperty("poiKey", key); addProperty("itemId", id); addProperty("kind", if (hidden) "HIDDEN_EVENT" else "VISIBLE_OBJECT")
                    addProperty("baseAreaId", 1); addProperty("sourceBank", 2); addProperty("recordRoot", 0x8100); addProperty("operandOffset", 0x8200 + index)
                    for (field in listOf("mapHeader", "objectPointerField", "mapBankTable", "mapPointerTable")) add(field, if (hidden) JsonNull.INSTANCE else JsonPrimitive(0x8000))
                    for (field in listOf("hiddenHandler", "coordinateRoot", "coordinateIndex")) add(field, if (!hidden) JsonNull.INSTANCE else JsonPrimitive(if (field == "coordinateIndex") index else 0x8400))
                })
                pois.add(JsonObject().apply {
                    addProperty("key", key); addProperty("localMapKey", "local/0001"); addProperty("baseAreaId", 1); addProperty("tileX", 2); addProperty("tileY", 3)
                    addProperty("kind", if (hidden) "HIDDEN_ITEM" else "VISIBLE_ITEM")
                    add("item", JsonObject().apply { addProperty("itemId", id); add("collectionFlagId", if (hidden) JsonPrimitive(index) else JsonNull.INSTANCE) })
                })
                provedRefs.add(JsonObject().apply { addProperty("poiKey", key); addProperty("itemId", id); addProperty("status", "REFERENCE_SCOPED_MEMBERSHIP") })
            }
            bundles += JsonObject().apply { add("control", control.deepCopy()); add("requestedIds", ids.deepCopy()); add("references", refs.deepCopy()); add("pois", pois.deepCopy()); add("observedAuthority", JsonObject()) }
            snapshots[hash] = snapshot()
            rows.add(JsonObject().apply {
                add("control", control); add("requestedIds", ids); add("originalReferences", refs); add("originalNumericPois", pois); add("originalAuthorityBundle", JsonObject()); add("references", provedRefs)
                addProperty("status", "STATIC_PROOF_COMPLETE_NOT_SEMANTIC_ACCEPTANCE"); addProperty("staticProofComplete", true); addProperty("semanticAcceptance", false)
                addProperty("requiredSemanticCompletion", "NOT_ACCEPTED"); add("acceptedLabels", JsonObject()); add("errors", JsonArray()); addProperty("inputReads", 1); addProperty("inputReadAttempts", 1); addProperty("inputSha256", hash)
                add("counts", JsonObject().apply { addProperty("nominationPasses", 1); addProperty("nominationScanBytes", 1048576); addProperty("wrapperCandidates", 1); addProperty("hiddenCandidates", 1) })
                add("derivedLabels", JsonObject().apply { (1..62).forEach { addProperty(it.toString(), if (it == 1) "SYNTHETIC  LABEL" else "SYNTHETIC $it") } })
                add("fields", JsonObject().apply { (1..62).forEach { id -> add(id.toString(), JsonObject().apply {
                    addProperty("status", "STATIC_DERIVED_NOT_PRODUCTION_ACCEPTED"); addProperty("label", if (id == 1) "SYNTHETIC  LABEL" else "SYNTHETIC $id")
                }) } })
            })
        }
        return Fixture(JsonObject().apply { addProperty("status", "STATIC_PROOF_COMPLETE_NOT_SEMANTIC_ACCEPTANCE"); addProperty("semanticAcceptance", false); addProperty("requiredSemanticCompletion", "NOT_ACCEPTED"); add("acceptedLabels", JsonObject()); add("controls", rows) }, manifest, bundles, snapshots)
    }
}
