package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.*
import com.enrpau.dualscreendex.companion.api.ApiViewBuilder
import com.enrpau.dualscreendex.companion.api.LanguageBootstrapView
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.parser.catalog.*
import com.enrpau.dualscreendex.parser.io.LoadedRom
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.model.*
import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*

/** Separate opt-in semantic acceptance. The historical Gen I diagnostic remains byte-identical. */
internal object WesternGen1SemanticCapture {
    private val json = WesternGen1BaselineCapture.json

    fun run(factory: CatalogDatabaseFactory) {
        // Entire pinned oracle/baseline validation precedes all original-input access and output creation.
        val oracles = WesternGen1SemanticOracle.readConfigured()
        val root = Files.createDirectory(Path.of(requireNotNull(System.getenv("DUALDEX_TEST_TEMP_ROOT"))).resolve("gen1-semantic-controls"))
        write(root.resolve("scope.json"), mapOf("proofSha256" to WesternGen1SemanticOracle.proofSha256,
            "reviewSha256" to WesternGen1SemanticOracle.reviewSha256, "inputFreezeSha256" to WesternGen1SemanticOracle.freezeSha256,
            "displayNormalization" to WesternGen1SemanticOracle.displayNormalization, "controls" to 10, "requestedIds" to 620,
            "references" to 1585, "sourceProofRerun" to false, "historicalDiagnosticChanged" to false,
            "nonItemBaselineExclusions" to listOf("localization.overlays.*.itemNames", "localization.overlays.*.localizedCapabilities.ITEM_NAMES"),
            "parserSchemaVersion" to CatalogSchema.parserSchemaVersion, "storageSchemaVersion" to CatalogSchema.version,
            "baselineBoundary" to "All six original snapshot sections otherwise compared exactly; no capability/shared-data differences ignored"))
        val receipts = oracles.map { oracle ->
            val control = oracle.identity
            val directory = "${control["language"].asString}-${control["family"].asString}-${control["sha256"].asString}"
            Receipt(Files.createDirectory(root.resolve(directory)), control).also { receipt ->
                receipt.save()
                write(receipt.root.resolve("oracle.json"), mapOf("rawNames" to oracle.rawNames, "displayNames" to oracle.names,
                    "originalReferences" to oracle.references, "originalNumericPois" to oracle.numericPois,
                    "normalization" to WesternGen1SemanticOracle.displayNormalization))
            }
        }
        for ((oracle, receipt) in oracles.zip(receipts)) {
            try { capture(receipt, factory, oracle) }
            catch (failure: AssertionError) { receipt.failure("control", failure) }
            catch (failure: Exception) { receipt.failure("control", failure) }
            finally {
                receipt.finish()
                println("WESTERN_GEN1_SEMANTIC ${receipt.root.fileName} terminal=${receipt.values["terminal"]} " +
                    "available=${receipt.values["availableCount"]}/${receipt.values["requestedCount"]} failures=${receipt.errors.keys}")
            }
        }
        val accepted = receipts.all { it.values["semanticAcceptance"] == true }
        write(root.resolve("batch.json"), mapOf("junitMethods" to 1, "controlReceipts" to receipts.size,
            "semanticAcceptance" to accepted, "requiredSemanticCompletion" to if (accepted) "ACCEPTED" else "NOT_ACCEPTED",
            "controls" to receipts.map { it.values }, "automaticRetry" to false))
        assertTrue("mandatory Gen I semantic acceptance failed; receipts at $root", accepted)
    }

    private fun capture(receipt: Receipt, factory: CatalogDatabaseFactory, oracle: WesternGen1SemanticOracle.Control) {
        val control = receipt.control
        val language = control["language"].asString
        val rom = receipt.check("input.exact-size-sha") {
            val path = Path.of(control["file"].asString)
            assertEquals(1048576L, Files.size(path))
            receipt.values["inputReadAttempts"] = 1; receipt.save()
            val bytes = Files.newInputStream(path).use { it.readNBytes(1048577) }
            receipt.values["inputReads"] = 1
            assertEquals(1048576, bytes.size)
            RomImage(bytes).also { assertEquals(control["sha256"].asString, it.sha256) }
        } ?: return
        receipt.values["originalAnalysisInvocations"] = 1
        val (context, session) = receipt.check("analysis.original-context") { WesternGen1BaselineCapture.observe(rom) } ?: return
        val analysis: ParseResult = property(context, "Analysis")
        write(receipt.root.resolve("analysis.json"), analysis)
        val authority = session.gen1ItemNameAuthority
        write(receipt.root.resolve("authority.json"), authority)
        receipt.check("selection") {
            assertEquals(SelectionStatus.SELECTED, analysis.status)
            assertEquals(control["family"].asString, analysis.selectedFamily?.name)
        }
        val layout = receipt.check("layout") { requireNotNull(analysis.probes.single { it.family == analysis.selectedFamily }.resolvedLayout) } ?: return
        var calls = 0
        var requested = emptySet<Int>()
        var names = emptyMap<Int, CatalogField<String>>()
        val producer: (ResolvedRomLayout, Set<Int>) -> Map<Int, CatalogField<String>> = property(context, "ResolveItemNames")
        val catalog = try {
            receipt.check("materialize.normal-callbacks") {
                CatalogMaterializer.materialize(rom, analysis, layout,
                    resolveGen3AreaNames = property(context, "ResolveGen3AreaNames"), resolveWorldMap = property(context, "ResolveWorldMap"),
                    resolveLocalMaps = property(context, "ResolveLocalMaps"), resolveMoveDescriptions = property(context, "ResolveMoveDescriptions"),
                    resolveAbilityMechanics = property(context, "ResolveAbilityMechanics"), resolveNatures = property(context, "ResolveNatures"),
                    resolveItemNames = { selectedLayout, ids ->
                        calls++; requested = ids.toSet()
                        producer(selectedLayout, ids).also { names = it.toMap() }
                    })
            }
        } finally {
            val refs = session.gen1ItemReferences.toList()
            receipt.values["referenceCount"] = refs.size
            receipt.values["requestedCount"] = requested.size
            receipt.values["availableCount"] = availableNames(names).size
            receipt.values["itemProducerCalls"] = calls
            write(receipt.root.resolve("references.json"), refs)
            write(receipt.root.resolve("item-observations.json"), mapOf("requestedIds" to requested.sorted(), "producer" to names,
                "rawExpectedNames" to oracle.rawNames, "displayExpectedNames" to oracle.names))
            receipt.check("original-authority.identity") { assertSame(authority, session.gen1ItemNameAuthority) }
            receipt.check("original-authority.available") { assertTrue(authority is GbItemNameAuthority.Available) }
            receipt.check("original-authority.producer-calls") { assertEquals(1, calls) }
            receipt.check("original-reference-domain") { assertEquals(oracle.references, refs.map { json.toJsonTree(it).asJsonObject }) }
            receipt.check("original-requested-domain") { assertEquals(oracle.names.keys, requested) }
            receipt.check("original-producer-required-names") { assertEquals(oracle.names.keys, names.keys); assertEquals(oracle.names, availableNames(names)) }
            receipt.save()
        } ?: return
        write(receipt.root.resolve("catalog-snapshot.json"), snapshot(catalog))
        receipt.check("language.exact-projection") {
            assertEquals(LanguageResolutionStatus.RESOLVED, catalog.languageManifest.status)
            assertEquals(language, catalog.languageManifest.defaultLanguage?.value)
            val projection = catalog.languageManifest.projections.single()
            assertEquals(language, projection.language.value)
            assertEquals("gb-gen1-$language", projection.codecId)
            assertEquals(1, projection.codecVersion)
            assertEquals(LanguageResolutionStatus.RESOLVED, projection.status)
            assertEquals(setOf(projection.language), catalog.localization.overlays.keys)
        }
        val maps = catalog.localMaps.maps.associateBy { it.key }
        val items = catalog.localMaps.pois.filter { it.item != null }.associateBy { it.key }
        receipt.check("reference.raw-witness-capture") {
            Files.newBufferedWriter(receipt.root.resolve("reference-witnesses.jsonl")).use { writer ->
                session.gen1ItemReferences.forEachIndexed { index, ref ->
                    val witness = receipt.check("reference.$index.geometry-numeric-binding") {
                        val poi = items.getValue(ref.poiKey)
                        WesternGen1BaselineCapture.referenceWitness(rom, ref, poi, maps.getValue(poi.localMapKey))
                    }
                    writer.appendLine(json.toJsonTree(mapOf("reference" to ref, "witness" to witness)).toString())
                }
            }
        }
        checkCatalog(receipt, "materialize", catalog, oracle, requested, names)
        val cache = CatalogCache(receipt.root.resolve("sqlite").toFile(), factory)
        receipt.check("sqlite.write-close") {
            cache.write(catalog, CatalogSourceMetadata.direct("western-gen1-semantic", rom.size, "WESTERN-SEMANTIC"), CatalogWriteProgress.complete())
        } ?: return
        val stored = receipt.check("sqlite.reopen-close") { requireNotNull(cache.readComplete(rom.sha256)) } ?: return
        val reopened = stored.catalog
        receipt.values["database"] = cache.fileFor(rom.sha256).absolutePath
        checkCatalog(receipt, "sqlite", reopened, oracle, requested, names)
        receipt.check("sqlite.whole-catalog-parity") { assertEquals(catalog, reopened) }
        receipt.check("sqlite.sections") { assertEquals(CatalogSchema.requiredSections + "language_overlay:$language", stored.committedSections) }
        receipt.check("sqlite.database-open-close") {
            factory.open(cache.fileFor(rom.sha256)).use { db ->
                receipt.check("sqlite.quick-check") {
                    val result = db.query("PRAGMA quick_check") { it.string("quick_check") }
                    write(receipt.root.resolve("sqlite-quick-check.json"), result); assertEquals(listOf("ok"), result)
                }
                receipt.check("sqlite.foreign-key-check") {
                    val result = db.query("PRAGMA foreign_key_check") { it.string("table") }
                    write(receipt.root.resolve("sqlite-foreign-key-check.json"), result); assertTrue(result.isEmpty())
                }
                receipt.check("sqlite.schemas") {
                    val result = db.query("SELECT parser_schema_version, schema_version FROM catalog_metadata WHERE id = 1") {
                        it.long("parser_schema_version") to it.long("schema_version")
                    }
                    write(receipt.root.resolve("sqlite-schemas.json"), result)
                    assertEquals(listOf(CatalogSchema.parserSchemaVersion.toLong() to 2L), result)
                }
            }
        }
        val reparses = AtomicInteger()
        try {
            receipt.check("api.cache-only-startup-and-close") {
                val done = CountDownLatch(1)
                val completion = AtomicReference<Result<Unit>?>()
                ProductionCompanionRuntime(initialSettings = CompanionSettings(knowledgeMode = KnowledgeMode.DISCOVERED),
                    catalogRepository = cache, parseCatalogWithCancellation = { _, _, _, _ ->
                        reparses.incrementAndGet(); error("Gen I semantic cache startup must not reparse")
                    }).use { runtime ->
                    runtime.load(LoadedRom("western-gen1-semantic", rom)) { result -> completion.set(result); done.countDown() }
                    assertTrue("cache-only startup timed out", done.await(30, TimeUnit.SECONDS))
                    requireNotNull(completion.get()).getOrThrow()
                    val bootstrap = runtime.bootstrap()
                    val state = runtime.stateView()
                    write(receipt.root.resolve("api.json"), mapOf("bootstrap" to bootstrap, "pois" to state.localMapPois, "reparses" to reparses.get()))
                    receipt.check("api.cache-ready") {
                        assertTrue(bootstrap.state.catalogReady); assertEquals("CACHE_REOPEN", bootstrap.state.loading.phase)
                    }
                    checkApiLanguage(reopened, requireNotNull(bootstrap.language)) { stage, block -> receipt.check("api.$stage", block) }
                    val states = bootstrap.language?.projections?.singleOrNull()?.localizedCapabilities
                    receipt.check("api.item-capability.complete") {
                        val actual = requireNotNull(states).getValue("ITEM_NAMES")
                        assertEquals("AVAILABLE", actual.status); assertEquals(62, actual.expectedRecords); assertEquals(62, actual.coveredRecords)
                    }
                    receipt.check("api.whole-projection-parity") {
                        val actual = requireNotNull(bootstrap.catalog)
                        assertEquals(control["sha256"].asString, actual.hash); assertEquals(ApiViewBuilder.catalog(reopened), actual)
                    }
                    val projected = state.localMapPois.filter { it.itemId != null }
                    receipt.check("api.original-numeric-pois") {
                        assertEquals(oracle.numericPois.size, projected.size)
                        assertEquals(projected.size, projected.map { it.key }.toSet().size)
                        val expected = oracle.numericPois.associateBy { it["key"].asString }
                        assertEquals(expected.keys, projected.map { it.key }.toSet())
                        projected.forEach { view ->
                            val poi = expected.getValue(view.key)
                            assertEquals(poi["localMapKey"].asString, view.localMapKey); assertEquals(poi["baseAreaId"].asInt, view.baseAreaId)
                            assertEquals(poi["tileX"].asInt, view.tileX); assertEquals(poi["tileY"].asInt, view.tileY)
                            assertEquals(poi.getAsJsonObject("item")["itemId"].asInt, view.itemId)
                            val numeric = reopened.localMaps.pois.single { it.key == view.key }
                            assertEquals(numeric.destinationBaseAreaId, view.destinationBaseAreaId)
                        }
                    }
                    val apiItems = (requireNotNull(bootstrap.catalog).balls.map { it.id to it.name } +
                        projected.map { requireNotNull(it.itemId) to it.itemName }).groupBy({ it.first }, { it.second })
                    checkRequiredProjectedNames(oracle.rawNames, apiItems) { stage, block -> receipt.check("api.items.$stage", block) }
                    receipt.values["apiCheckPathComplete"] = true
                }
            }
        } finally {
            receipt.values["apiReparses"] = reparses.get()
            receipt.check("api.zero-reparse") { assertEquals(0, reparses.get()) }
        }
        receipt.values["boundaryNonExposure"] = mapOf("quantity" to "absent from GenI reference, numeric item and API",
            "collectionFlag" to "hidden coordinateIndex/numeric SQLite evidence only; API does not expose it")
        receipt.values["semanticCheckPathComplete"] = receipt.values["apiCheckPathComplete"] == true
    }

    private fun checkCatalog(receipt: Receipt, stage: String, catalog: ParsedCatalog, oracle: WesternGen1SemanticOracle.Control,
        requested: Set<Int>, producer: Map<Int, CatalogField<String>>) {
        receipt.check("$stage.original-identity") {
            assertEquals(oracle.identity["sha256"].asString, catalog.romSha256)
            assertEquals(oracle.identity["family"].asString, catalog.family.name)
            assertEquals(oracle.identity["language"].asString, catalog.languageManifest.defaultLanguage?.value)
        }
        receipt.check("$stage.original-numeric-pois") { assertEquals(oracle.numericPois, numericPois(catalog)) }
        receipt.check("$stage.numeric-names-null") {
            assertTrue(catalog.captureBallsById.values.all { it.name.value == null })
            assertTrue(catalog.localMaps.pois.all { it.item?.displayName == null })
        }
        val numericIds = catalog.captureBallsById.keys + catalog.localMaps.pois.mapNotNull { it.item?.itemId }
        receipt.check("$stage.numeric-denominator") { assertEquals(oracle.names.keys, numericIds) }
        val overlay = catalog.defaultLocalizedText()
        WesternGen1BaselineCapture.checkItemObservations(requested, producer, overlay?.localizedCapabilities, overlay?.itemNames) {
            boundary, block -> receipt.check("$stage.$boundary", block)
        }
        checkRequiredItemObservations(oracle.rawNames, requested, mapOf("producer" to producer, "overlay" to overlay?.itemNames),
            overlay?.localizedCapabilities?.get(LocalizedTextCapability.ITEM_NAMES)) { boundary, block -> receipt.check("$stage.required.$boundary", block) }
        val projection = catalog.defaultTextProjection()
        checkRequiredProjectedNames(oracle.rawNames, numericIds.associateWith { listOf(projection.itemName(it)) }) {
            boundary, block -> receipt.check("$stage.projection.$boundary", block)
        }
        receipt.check("$stage.poi-projection.required-names") {
            catalog.localMaps.pois.forEach { poi -> poi.item?.itemId?.let { id -> assertEquals(oracle.names.getValue(id), projection.poiItemName(poi.key, id)) } }
        }
        receipt.check("$stage.non-item-baseline") { checkUnchangedBaseline(oracle.baselineSnapshot, snapshot(catalog)) }
    }

    internal fun checkApiLanguage(catalog: ParsedCatalog, actual: LanguageBootstrapView, check: (String, () -> Unit) -> Unit) {
        val language = requireNotNull(catalog.languageManifest.defaultLanguage).value
        val overlay = requireNotNull(catalog.defaultLocalizedText())
        check("authority-projection") {
            assertEquals(catalog.languageManifest.status.name, actual.manifestStatus)
            assertEquals("ROM_DEFAULT", actual.authority)
            assertEquals(language, actual.defaultLanguage); assertEquals(language, actual.activeLanguage)
            assertEquals(overlay.overlayVersion, actual.activeOverlayVersion)
            assertEquals(listOf(language), actual.projections.map { it.language })
        }
        check("projection.metadata") {
            val expected = catalog.languageManifest.projections.single()
            val projection = actual.projections.single()
            assertEquals(expected.language.value, projection.language)
            assertEquals(expected.status.name, projection.status)
            assertEquals(expected.codecId, projection.codecId)
            assertEquals(expected.codecVersion, projection.codecVersion)
            assertEquals(overlay.overlayVersion, projection.overlayVersion)
        }
        val states = actual.projections.singleOrNull()?.localizedCapabilities
        check("capabilities.inventory") {
            assertEquals(LocalizedTextCapability.entries.map { it.name }.toSet(), requireNotNull(states).keys); assertEquals(15, states.size)
        }
        for (capability in LocalizedTextCapability.entries) check("capability.$capability") {
            val expected = overlay.localizedCapabilities.getValue(capability)
            val state = requireNotNull(states).getValue(capability.name)
            assertEquals(expected.status.name, state.status)
            assertEquals(expected.confidence, state.confidence, 0.0)
            assertEquals(expected.expectedRecords, state.expectedRecords); assertEquals(expected.coveredRecords, state.coveredRecords)
            assertEquals(expected.incompleteRecords, state.incompleteRecords)
            assertEquals(expected.reviewStatus.name, state.reviewStatus)
            assertEquals(expected.validatorReviewRecommended, state.validatorReviewRecommended)
        }
    }

    internal fun checkRequiredItemObservations(rawExpected: Map<Int, String>, requested: Set<Int>, fields: Map<String, Map<Int, CatalogField<String>>?>,
        state: LocalizedCapabilityState?, check: (String, () -> Unit) -> Unit) {
        val expected = rawExpected.mapValues { WesternGen1SemanticOracle.displayName(it.value) }
        check("requested-domain") { assertTrue(expected.isNotEmpty() && expected.values.all { it.isNotBlank() }); assertEquals(expected.keys, requested) }
        for ((stage, names) in fields) {
            check("$stage.denominator") { assertEquals(expected.keys, requireNotNull(names).keys) }
            check("$stage.required-names") { assertEquals(expected, availableNames(requireNotNull(names))) }
        }
        check("item-capability.status") { assertEquals(CapabilityStatus.AVAILABLE, requireNotNull(state).status) }
        check("item-capability.expected") { assertEquals(expected.size, requireNotNull(state).expectedRecords) }
        check("item-capability.covered") { assertEquals(expected.size, requireNotNull(state).coveredRecords) }
    }

    internal fun checkRequiredProjectedNames(rawExpected: Map<Int, String>, actual: Map<Int, List<String?>>, check: (String, () -> Unit) -> Unit) {
        val expected = rawExpected.mapValues { WesternGen1SemanticOracle.displayName(it.value) }
        check("denominator") { assertEquals(expected.keys, actual.keys) }
        check("required-names") {
            assertTrue(expected.isNotEmpty() && expected.values.all { it.isNotBlank() })
            for ((id, label) in expected) {
                val occurrences = requireNotNull(actual[id])
                assertTrue(occurrences.isNotEmpty()); assertEquals("every projected occurrence of $id", setOf(label), occurrences.toSet())
            }
        }
    }

    /** Item names/state are separately required above. No other baseline fields are discarded. */
    internal fun checkUnchangedBaseline(expected: JsonObject, actual: JsonObject) {
        fun nonItem(snapshot: JsonObject) = snapshot.deepCopy().apply {
            require(keySet() == setOf("languageManifest", "localization", "capabilities", "diagnostics", "balls", "pois"))
            val overlays = getAsJsonObject("localization").getAsJsonObject("overlays")
            require(overlays.size() == 1)
            for (entry in overlays.entrySet()) {
                val overlay = entry.value.asJsonObject
                require(overlay.has("itemNames")); overlay.remove("itemNames")
                val states = overlay.getAsJsonObject("localizedCapabilities")
                require(states.keySet() == LocalizedTextCapability.entries.map { it.name }.toSet())
                states.remove("ITEM_NAMES")
            }
        }
        assertEquals("non-item text, full remaining capabilities, diagnostics and shared evidence", nonItem(expected), nonItem(actual))
    }

    private fun numericPois(catalog: ParsedCatalog): List<JsonObject> = catalog.localMaps.pois.filter { it.item != null }.map { poi ->
        json.toJsonTree(mapOf("key" to poi.key, "localMapKey" to poi.localMapKey, "baseAreaId" to poi.baseAreaId,
            "kind" to poi.kind.name, "tileX" to poi.tileX, "tileY" to poi.tileY,
            "item" to mapOf("itemId" to poi.item!!.itemId, "collectionFlagId" to poi.item!!.collectionFlagId))).asJsonObject
    }
    private fun snapshot(catalog: ParsedCatalog): JsonObject = json.toJsonTree(mapOf("languageManifest" to catalog.languageManifest,
        "localization" to catalog.localization, "capabilities" to catalog.capabilities, "diagnostics" to catalog.diagnostics,
        "balls" to catalog.captureBallsById, "pois" to catalog.localMaps.pois)).asJsonObject
    private fun availableNames(fields: Map<Int, CatalogField<String>>) = fields.mapNotNull { (id, field) ->
        field.value?.takeIf { field.status == CapabilityStatus.AVAILABLE && it.isNotBlank() }?.let { id to it }
    }.toMap()
    @Suppress("UNCHECKED_CAST")
    private fun <T> property(value: Any, name: String): T = value.javaClass.getMethod("get$name").invoke(value) as T
    private fun write(path: Path, value: Any?) { Files.write(path, json.toJson(value).toByteArray(Charsets.UTF_8)) }

    internal class Receipt(val root: Path, val control: JsonObject) {
        val errors = linkedMapOf<String, String>()
        val checks = linkedMapOf<String, String>()
        val values = linkedMapOf<String, Any?>("control" to control, "terminal" to "NOT_STARTED", "semanticAcceptance" to false,
            "requiredSemanticCompletion" to "NOT_ACCEPTED", "inputReadAttempts" to 0, "inputReads" to 0,
            "originalAnalysisInvocations" to 0, "checks" to checks, "errors" to errors)
        fun save() { write(root.resolve("receipt.json"), values) }
        fun finish() {
            val accepted = errors.isEmpty() && values["semanticCheckPathComplete"] == true
            values["semanticAcceptance"] = accepted
            values["requiredSemanticCompletion"] = if (accepted) "ACCEPTED" else "NOT_ACCEPTED"
            values["terminal"] = if (accepted) "SEMANTIC_ACCEPTANCE_COMPLETE" else "SEMANTIC_ACCEPTANCE_FAILED"
            save()
        }
        fun failure(stage: String, failure: Throwable) { checks[stage] = "FAIL"; errors[stage] = failure.stackTraceToString(); save() }
        fun <T> check(stage: String, block: () -> T): T? {
            require(stage !in checks) { "duplicate check $stage" }
            values["lastStage"] = stage; save()
            return try { block().also { checks[stage] = "PASS"; save() } }
            catch (failure: AssertionError) { this.failure(stage, failure); null }
            catch (failure: Exception) { this.failure(stage, failure); null }
        }
    }
}
