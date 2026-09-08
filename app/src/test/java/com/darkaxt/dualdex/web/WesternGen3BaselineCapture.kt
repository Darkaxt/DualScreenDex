package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.*
import com.enrpau.dualscreendex.companion.api.*
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.*
import com.enrpau.dualscreendex.parser.io.LoadedRom
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.model.*
import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*

/** Current Western GBA diagnostic parity only. Observed output is NEVER an independent name oracle. */
internal object WesternGen3BaselineCapture {
    internal val json = WesternGen1BaselineCapture.json
    internal const val manifestSha = "ee408ad4a5d51da8656ff336ff7c139d92fc24b9d201f6817c47ce3ef194a749"
    internal const val inventorySha = "70c2ac3c87c871cb9b2d92d798977afb0f26db8e2f9ba9f8380184a098834837"
    private const val inputBytes = 16_777_216
    internal val controlHashes = linkedMapOf(
        ("en" to "RUBY_SAPPHIRE") to "0fdd36e92b75bed65d09df4635ab0b707b288c2bf1dc4c6e7a4a4f0eebe9d64c",
        ("en" to "EMERALD") to "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af",
        ("en" to "FIRERED_LEAFGREEN") to "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059",
        ("fr" to "RUBY_SAPPHIRE") to "9678645ca67932a42c92b1d6bac118925a6be9f67135ae8fe7747df85cdabdd0",
        ("fr" to "EMERALD") to "e79b40e6189550b4870b06918a5c59e04d3a2e1d7c92718aeda92181201f51e4",
        ("fr" to "FIRERED_LEAFGREEN") to "245866842349cedbe2a034c6d20438d26d18b627b3d0909d7cb795fc87d7675a",
        ("de" to "RUBY_SAPPHIRE") to "e0b620d576bc503e083bb9d2af1f5ffc40127b84e6ca734e3c45534d0d008043",
        ("de" to "EMERALD") to "7c599c56849efeebeb93bd71f714932ae4cdf980db51c9c1016b4431057f71d4",
        ("de" to "FIRERED_LEAFGREEN") to "eed4fb0242bcbb6e0b0b80b197b47e3aec99ef4c5562cc45bae50ae86b970507",
        ("it" to "RUBY_SAPPHIRE") to "be116b6fa8d8c12d7ccff3faefd5defd886faa427ff2c85c06d245cbe4e531b2",
        ("it" to "EMERALD") to "63cbff3500b657cb6966568beb0780de3655d3a0b2e5ac6e0ec33d5d01a916ad",
        ("it" to "FIRERED_LEAFGREEN") to "ad52b593ef0c7439e1409d3f0a1ec3ff865a494ca46c2184fe65573759838f3a",
        ("es" to "RUBY_SAPPHIRE") to "0db39958c19abcedc7670fb16fd3274a5b205df794eb7491bea0580aa752e095",
        ("es" to "EMERALD") to "e32c82bd10f174cf4019123b36f3ef7729105fb6634d9aa6b61413ee5101a55e",
        ("es" to "FIRERED_LEAFGREEN") to "ab696c065639e4d73c3c3ed9d4fbf9c7fed593761f3b6746256ff82465596554",
    )
    internal val controlCodes = controlHashes.keys.associateWith { (language, family) ->
        val prefix = when (family) { "RUBY_SAPPHIRE" -> "AXV"; "EMERALD" -> "BPE"; else -> "BPR" }
        prefix + mapOf("en" to "E", "fr" to "F", "de" to "D", "it" to "I", "es" to "S").getValue(language)
    }
    internal val requiredPaths = setOf("input", "original-analysis", "original-route", "materialize", "numeric", "current-snapshot", "historical", "sqlite", "api")
    internal data class Control(val identity: JsonObject, val historical: JsonObject)

    fun run(factory: CatalogDatabaseFactory) {
        lateinit var manifest: ByteArray
        lateinit var inventory: ByteArray
        // No original path is inspected, and no output is created, until BOTH original metadata pins bind.
        preflight(System.getenv("DUALDEX_WESTERN_GEN3_DIAGNOSTIC"),
            { readMetadata("DUALDEX_WESTERN_MANIFEST").also { manifest = it } },
            { readMetadata("DUALDEX_WESTERN_INVENTORY").also { inventory = it } }) { controls ->
            require(CatalogSchema.parserSchemaVersion == 60 && CatalogSchema.version == 2)
            val root = createOutput(Path.of(requireNotNull(System.getenv("DUALDEX_TEST_TEMP_ROOT"))))
            Files.write(root.resolve("manifest.json"), manifest); Files.write(root.resolve("inventory.json"), inventory)
            write(root.resolve("scope.json"), mapOf("manifestSha256" to manifestSha, "inventorySha256" to inventorySha,
                "controls" to controls.map { it.identity }, "parserSchemaVersion" to 60, "storageSchemaVersion" to 2,
                "historicalParserSchemaVersion" to 49, "historicalRequestedNameSlots" to controls.sumOf {
                    it.historical.getAsJsonObject("localizedCapabilities").getAsJsonObject("ITEM_NAMES")["expectedRecords"].asInt },
                "currentExpectedDomain" to "OBSERVE_NOT_PRESET", "semanticAcceptance" to false, "acceptedNames" to 0,
                "independentCompiledProof" to "NOT_RUN", "tokenPolicyProof" to "NOT_RUN", "automaticRetry" to false,
                "rawRomRetention" to "NONE", "historicalCachesOpened" to false, "nonItemEquality49To60" to "NOT_ESTABLISHED"))
            // Reserve every directory before the first original read; collisions are not retried or merged.
            val receipts = controls.map { control ->
                val c = control.identity
                Receipt(Files.createDirectory(root.resolve("${c["language"].asString}-${c["family"].asString}-${c["sha256"].asString}")), c).also {
                    it.save(); write(it.root.resolve("historical-parser49-cell.json"), control.historical)
                }
            }
            for ((control, receipt) in controls.zip(receipts)) {
                try { capture(receipt, factory, control.historical) }
                catch (failure: AssertionError) { receipt.failure("control", failure) }
                catch (failure: Exception) { receipt.failure("control", failure) }
                finally {
                    receipt.finish()
                    println("WESTERN_GEN3_DIAGNOSTIC ${receipt.root.fileName} terminal=${receipt.values["terminal"]} " +
                        "available=${receipt.values["availableCount"]}/${receipt.values["requestedCount"]} failures=${receipt.errors.keys}")
                }
            }
            val complete = receipts.all { it.values["terminal"] == "DIAGNOSTIC_CAPTURE_COMPLETE" }
            write(root.resolve("batch.json"), mapOf("junitMethods" to 1, "controlReceipts" to receipts.size,
                "diagnosticComplete" to complete, "semanticAcceptance" to false, "acceptedNames" to 0,
                "requiredSemanticCompletion" to "NOT_ACCEPTED", "automaticRetry" to false, "controls" to receipts.map { it.values }))
            assertTrue("Western GBA diagnostic integrity failed; retained receipts at $root", complete)
        }
    }

    internal fun <T> preflight(optIn: String?, manifest: () -> ByteArray, inventory: () -> ByteArray, ready: (List<Control>) -> T): T {
        require(optIn == "1") { "DUALDEX_WESTERN_GEN3_DIAGNOSTIC must be exactly 1" }
        val manifestBytes = manifest(); requirePin(manifestBytes, manifestSha)
        val inventoryBytes = inventory(); requirePin(inventoryBytes, inventorySha)
        return decodeMetadata(manifestBytes, inventoryBytes, ready)
    }
    internal fun <T> decodeMetadata(manifest: ByteArray, inventory: ByteArray, ready: (List<Control>) -> T): T {
        require(manifest.size in 1..4_194_304 && inventory.size in 1..4_194_304)
        // The neutral strict reader returns an object. Envelope the manifest's ARRAY root only
        // after pin verification in preflight; never apply a Gen I oracle/control/reference contract.
        val envelope = "{\"manifest\":".toByteArray(Charsets.UTF_8) + manifest + "}".toByteArray(Charsets.UTF_8)
        val tree = WesternGen1SemanticOracle.strictJson(envelope)
        require(tree.keySet() == setOf("manifest")) { "manifest must be one complete array document" }
        val all = tree.getAsJsonArray("manifest").map { it.asJsonObject }
        val public = WesternGen1SemanticOracle.strictJson(inventory)
        return bindMetadata(all, public.getAsJsonArray("cells").map { it.asJsonObject }, ready)
    }
    internal fun requirePin(bytes: ByteArray, expected: String) {
        require(bytes.size in 1..4_194_304 && sha256(bytes) == expected) { "exact original metadata SHA-256 required" }
    }
    internal fun <T> bindMetadata(all: List<JsonObject>, inventory: List<JsonObject>, ready: (List<Control>) -> T): T {
        fun text(row: JsonObject, field: String): String {
            val value = requireNotNull(row[field]); require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
            return value.asString.also { require(it.isNotBlank()) }
        }
        fun keys(rows: List<JsonObject>, hash: String): Set<Triple<String, String, String>> {
            require(rows.size == 35)
            val result = rows.map { Triple(text(it, "language"), text(it, "family"), text(it, hash)) }
            require(result.toSet().size == 35 && result.map { it.third }.toSet().size == 35)
            require(result.map { it.first to it.second }.toSet().size == 35)
            return result.toSet()
        }
        require(keys(all, "sha256") == keys(inventory, "romSha256")) { "original manifest/inventory identities differ" }
        val selected = all.filter { (text(it, "language") to text(it, "family")) in controlHashes }
        require(selected.size == 15)
        val byHash = inventory.associateBy { text(it, "romSha256") }
        val result = selected.map { row ->
            val cell = text(row, "language") to text(row, "family")
            require(text(row, "sha256") == controlHashes.getValue(cell))
            val size = requireNotNull(row["size"])
            require(size.isJsonPrimitive && size.asJsonPrimitive.isNumber && size.asString == inputBytes.toString())
            text(row, "file"); require(text(row, "code") == controlCodes.getValue(cell))
            val historical = byHash.getValue(text(row, "sha256"))
            require(historical.getAsJsonObject("localizedCapabilities").keySet() == LocalizedTextCapability.entries.map { it.name }.toSet())
            Control(row.deepCopy(), historical.deepCopy())
        }
        return ready(result)
    }
    internal fun createOutput(parent: Path): Path = Files.createDirectory(parent.resolve("gen3-controls"))
    private fun readMetadata(variable: String): ByteArray {
        val path = Path.of(requireNotNull(System.getenv(variable))); require(Files.size(path) in 1..4_194_304)
        return Files.newInputStream(path).use { it.readNBytes(4_194_305) }
    }

    internal data class OriginalEntry(val route: GbaItemPublishedRoute, val authority: GbaItemNameAuthority) {
        val routeState: String = when (route) {
            GbaItemPublishedRoute.NotEvaluated -> "NotEvaluated"
            GbaItemPublishedRoute.NotInvoked -> "NotInvoked"
            is GbaItemPublishedRoute.Invoked -> when (route.nomination) {
                GbaItemRootNomination.Absent -> "InvokedAbsent"
                GbaItemRootNomination.Ambiguous -> "InvokedAmbiguous"
                is GbaItemRootNomination.Nominated -> "InvokedNominated"
            }
        }
        val authorityState: String = when (authority) { is GbaItemNameAuthority.Available -> "Available"; is GbaItemNameAuthority.Unavailable -> "Unavailable" }
    }
    /** Never call a resolver getter/method. Reading value is permitted only after isInitialized succeeds. */
    internal fun inspectOriginal(delegate: Lazy<*>): List<OriginalEntry> {
        require(delegate.isInitialized()) { "original item resolver was not initialized; route is NOT inferred" }
        val resolver = requireNotNull(delegate.value)
        val raw = resolver.javaClass.getDeclaredField("originalResults").apply { isAccessible = true }.get(resolver) as Map<*, *>
        return raw.entries.map { OriginalEntry(it.key as GbaItemPublishedRoute, it.value as GbaItemNameAuthority) }
    }
    internal fun bindOriginal(entries: List<OriginalEntry>, authority: GbaItemNameAuthority): OriginalEntry {
        val matches = entries.filter { it.authority === authority }
        require(matches.size == 1) { "original authority identity binding missing/nonunique: ${matches.size}" }
        return matches.single()
    }
    internal fun checkFrozenOriginal(before: List<OriginalEntry>, after: List<OriginalEntry>) {
        assertEquals(before.map { it.route }, after.map { it.route })
        before.zip(after).forEach { (a, b) -> assertSame(a.authority, b.authority) }
    }
    private fun originalDelegate(session: RomAnalysisSession): Lazy<*> = RomAnalysisSession::class.java
        .getDeclaredField("itemNameResolver\$delegate").apply { isAccessible = true }.get(session) as Lazy<*>

    internal class ProducerObservation(private val producer: (ResolvedRomLayout?, Set<Int>) -> Map<Int, CatalogField<String>>) {
        var attempts = 0; private set
        var requested: Set<Int> = emptySet(); private set
        var fields: Map<Int, CatalogField<String>> = emptyMap(); private set
        var completed = false; private set
        fun invoke(layout: ResolvedRomLayout?, ids: Set<Int>): Map<Int, CatalogField<String>> {
            attempts++
            require(attempts == 1) { "original item producer must not be retried" }
            requested = ids.toSet()
            return producer(layout, ids).also { fields = it.toMap(); completed = true }
        }
    }

    private fun capture(receipt: Receipt, factory: CatalogDatabaseFactory, historical: JsonObject) {
        val control = receipt.control
        val rom = receipt.check("input.exact-size-sha") {
            val path = Path.of(control["file"].asString); assertEquals(inputBytes.toLong(), Files.size(path))
            receipt.values["inputReadAttempts"] = 1; receipt.save()
            val bytes = Files.newInputStream(path).use { it.readNBytes(inputBytes + 1) }
            receipt.values["inputReads"] = 1; assertEquals(inputBytes, bytes.size)
            RomImage(bytes).also { assertEquals(control["sha256"].asString, it.sha256) }
        } ?: return
        receipt.completed += "input"
        receipt.values["originalAnalysisInvocations"] = 1
        // This neutral helper returns the ordinary bound callback's session, not a fresh session.
        val (context, session) = receipt.check("analysis.original-context") { WesternGen1BaselineCapture.observe(rom) } ?: return
        receipt.completed += "original-analysis"
        // Snapshot immediately after original analysis, before invoking ANY materializer callback.
        val delegate = receipt.check("original-route.delegate") { originalDelegate(session) } ?: return
        receipt.values["originalResolverInitialized"] = delegate.isInitialized(); receipt.save()
        val frozen = receipt.check("original-route.snapshot") { inspectOriginal(delegate) } ?: return
        write(receipt.root.resolve("original-route-snapshot.json"), frozen)
        val analysis: ParseResult = property(context, "Analysis")
        write(receipt.root.resolve("analysis.json"), analysis)
        receipt.check("selection") {
            assertEquals(SelectionStatus.SELECTED, analysis.status); assertEquals(control["family"].asString, analysis.selectedFamily?.name)
        }
        val layout = receipt.check("layout") { requireNotNull(analysis.probes.single { it.family == analysis.selectedFamily }.resolvedLayout) } ?: return
        write(receipt.root.resolve("original-layout.json"), layout)
        val authority = layout.itemNameAuthority
        val original = receipt.check("original-route.selected-identity") { bindOriginal(frozen, authority) }
        write(receipt.root.resolve("authority.json"), mapOf("originalEntries" to frozen, "selectedOriginalEntry" to original,
            "layoutNomination" to layout.itemRootNomination, "immutableAuthority" to authority,
            "productionLimits" to session.limits, "independentCompiledProof" to "NOT_RUN", "tokenPolicyProof" to "NOT_RUN"))
        if (original != null) {
            receipt.completed += "original-route"
            receipt.check("original-route.nomination-parity") {
                val invoked = original.route as? GbaItemPublishedRoute.Invoked
                assertEquals(invoked?.nomination ?: GbaItemRootNomination.Absent, layout.itemRootNomination)
            }
        }
        val producer: (ResolvedRomLayout, Set<Int>) -> Map<Int, CatalogField<String>> = property(context, "ResolveItemNames")
        val observation = ProducerObservation { selected, ids ->
            assertSame(layout, selected); assertSame(authority, requireNotNull(selected).itemNameAuthority)
            producer(selected, ids)
        }
        val catalog = try {
            receipt.check("materialize.normal-callbacks") {
                CatalogMaterializer.materialize(rom, analysis, layout,
                    resolveGen3AreaNames = property(context, "ResolveGen3AreaNames"), resolveWorldMap = property(context, "ResolveWorldMap"),
                    resolveLocalMaps = property(context, "ResolveLocalMaps"), resolveMoveDescriptions = property(context, "ResolveMoveDescriptions"),
                    resolveAbilityMechanics = property(context, "ResolveAbilityMechanics"), resolveNatures = property(context, "ResolveNatures"),
                    resolveItemNames = { selected, ids -> observation.invoke(selected, ids) })
            }
        } finally {
            receipt.values["requestedCount"] = observation.requested.size
            receipt.values["availableCount"] = availableNames(observation.fields).size
            receipt.values["unavailableRequestedIds"] = (observation.requested - availableNames(observation.fields).keys).sorted()
            receipt.values["itemProducerCalls"] = observation.attempts
            write(receipt.root.resolve("item-observations.json"), mapOf("requestedIds" to observation.requested.sorted(),
                "producerCompleteFields" to observation.fields, "producerCompleted" to observation.completed,
                "semanticAcceptance" to false, "acceptedNames" to 0))
            receipt.check("original-route.frozen-after-materialization") { checkFrozenOriginal(frozen, inspectOriginal(delegate)) }
            receipt.check("original-authority.unchanged") { assertSame(authority, layout.itemNameAuthority) }
            receipt.check("producer.once-complete") { assertEquals(1, observation.attempts); assertTrue(observation.completed) }
            receipt.check("producer.complete-keys") { assertEquals(observation.requested, observation.fields.keys) }
        } ?: return
        receipt.completed += "materialize"
        receipt.check("current.snapshot") {
            write(receipt.root.resolve("catalog-snapshot.json"), mapOf("languageManifest" to catalog.languageManifest,
                "localization" to catalog.localization, "capabilities" to catalog.capabilities, "diagnostics" to catalog.diagnostics,
                "balls" to catalog.captureBallsById, "pois" to catalog.localMaps.pois))
            write(receipt.root.resolve("numeric-bindings.json"), mapOf("requestedIds" to numericIds(catalog).sorted(),
                "balls" to catalog.captureBallsById, "pois" to catalog.localMaps.pois,
                "mapBindings" to catalog.localMaps.maps.map { mapOf("key" to it.key, "baseAreaId" to it.baseAreaId,
                    "gridWidth" to it.gridWidth, "gridHeight" to it.gridHeight) },
                "quantity" to "NOT_EXPOSED_BY_NUMERIC_ITEM_MODEL", "collectionFlag" to "RETAINED_IN_POI_ITEM_AND_SQLITE_NOT_API"))
            receipt.completed += "current-snapshot"
        }
        receipt.check("language.exact-projection") {
            assertEquals(control["sha256"].asString, catalog.romSha256); assertEquals(control["family"].asString, catalog.family.name)
            assertEquals(Platform.GBA, catalog.platform); assertEquals(LanguageResolutionStatus.RESOLVED, catalog.languageManifest.status)
            val language = control["language"].asString; assertEquals(language, catalog.languageManifest.defaultLanguage?.value)
            val projection = catalog.languageManifest.projections.single()
            assertEquals(language, projection.language.value); assertEquals("gba-gen3-$language", projection.codecId)
            assertEquals(1, projection.codecVersion); assertEquals(LanguageResolutionStatus.RESOLVED, projection.status)
            assertEquals(setOf(projection.language), catalog.localization.overlays.keys)
        }
        checkCatalogItems(catalog, observation.requested, observation.fields) { stage, block -> receipt.check("materialize.$stage", block) }
        receipt.completed += "numeric"
        receipt.check("historical.record-comparison") {
            write(receipt.root.resolve("historical-current-comparison.json"), historicalComparison(historical, catalog))
            receipt.completed += "historical"
        }
        persistAndCheck(receipt, factory, rom, catalog, observation.requested, observation.fields)
    }

    internal fun numericIds(catalog: ParsedCatalog): Set<Int> = catalog.captureBallsById.keys + catalog.localMaps.pois.mapNotNull { it.item?.itemId }
    internal fun checkCatalogItems(catalog: ParsedCatalog, requested: Set<Int>, fields: Map<Int, CatalogField<String>>, check: (String, () -> Unit) -> Unit) {
        check("numeric.u16-domain") { assertTrue(requested.all { it in 0..65535 }); assertEquals(numericIds(catalog), requested) }
        check("numeric.ball-key-identity") { catalog.captureBallsById.forEach { (id, record) -> assertEquals(id, record.id) } }
        check("numeric.poi-key-uniqueness") { assertEquals(catalog.localMaps.pois.size, catalog.localMaps.pois.map { it.key }.toSet().size) }
        check("numeric.shared-names-null") {
            assertTrue(catalog.captureBallsById.values.all { it.name.value == null }); assertTrue(catalog.localMaps.pois.all { it.item?.displayName == null })
        }
        val overlay = catalog.defaultLocalizedText()
        // Neutral sparse-overlay contract, including unavailable producer fields and all15 denominators.
        WesternGen1BaselineCapture.checkItemObservations(requested, fields, overlay?.localizedCapabilities, overlay?.itemNames, check)
        check("projection.item-name-parity") {
            val names = availableNames(fields)
            requested.forEach { id -> assertEquals(names[id], catalog.defaultTextProjection().itemName(id)) }
        }
    }

    internal fun persistAndCheck(receipt: Receipt, factory: CatalogDatabaseFactory, rom: RomImage, catalog: ParsedCatalog,
        requested: Set<Int>, fields: Map<Int, CatalogField<String>>) {
        val cache = CatalogCache(receipt.root.resolve("sqlite").toFile(), factory)
        receipt.check("sqlite.write-close") {
            cache.write(catalog, CatalogSourceMetadata.direct("western-gen3-diagnostic", rom.size, "WESTERN-DIAGNOSTIC"), CatalogWriteProgress.complete())
        } ?: return
        val stored = receipt.check("sqlite.reopen-close") { requireNotNull(cache.readComplete(rom.sha256)) } ?: return
        val reopened = stored.catalog
        receipt.values["database"] = cache.fileFor(rom.sha256).absolutePath
        receipt.check("sqlite.whole-catalog-parity") { assertEquals(catalog, reopened) }
        receipt.check("sqlite.numeric-ball-poi-parity") { assertEquals(catalog.captureBallsById, reopened.captureBallsById); assertEquals(catalog.localMaps.pois, reopened.localMaps.pois) }
        receipt.check("sqlite.sections") {
            write(receipt.root.resolve("sqlite-sections.json"), stored.committedSections)
            assertEquals(CatalogSchema.requiredSections + "language_overlay:${catalog.languageManifest.defaultLanguage!!.value}", stored.committedSections)
        }
        checkCatalogItems(reopened, requested, fields) { stage, block -> receipt.check("sqlite.$stage", block) }
        for (capability in LocalizedTextCapability.entries) receipt.check("sqlite.capability-parity.$capability") {
            assertEquals(catalog.defaultLocalizedText()!!.localizedCapabilities.getValue(capability), reopened.defaultLocalizedText()!!.localizedCapabilities.getValue(capability))
        }
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
                    val result = db.query("SELECT parser_schema_version, schema_version FROM catalog_metadata WHERE id = 1") { it.long("parser_schema_version") to it.long("schema_version") }
                    write(receipt.root.resolve("sqlite-schemas.json"), result); assertEquals(listOf(60L to 2L), result)
                }
            }
        }
        receipt.completed += "sqlite"
        val reparses = AtomicInteger()
        try {
            receipt.check("api.cache-only-startup-and-close") {
                val done = CountDownLatch(1); val completion = AtomicReference<Result<Unit>?>()
                ProductionCompanionRuntime(initialSettings = CompanionSettings(knowledgeMode = KnowledgeMode.DISCOVERED), catalogRepository = cache,
                    parseCatalogWithCancellation = { _, _, _, _ -> reparses.incrementAndGet(); error("Western GBA diagnostic must not reparse") }).use { runtime ->
                    runtime.load(LoadedRom("western-gen3-diagnostic", rom)) { result -> completion.set(result); done.countDown() }
                    assertTrue("cache-only startup timed out", done.await(30, TimeUnit.SECONDS)); requireNotNull(completion.get()).getOrThrow()
                    val bootstrap = runtime.bootstrap(); val pois = runtime.stateView().localMapPois
                    write(receipt.root.resolve("api.json"), mapOf("bootstrap" to bootstrap, "pois" to pois, "reparses" to reparses.get()))
                    receipt.check("api.cache-ready") { assertTrue(bootstrap.state.catalogReady); assertEquals("CACHE_REOPEN", bootstrap.state.loading.phase) }
                    WesternGen1SemanticCapture.checkApiLanguage(reopened, requireNotNull(bootstrap.language)) { stage, block -> receipt.check("api.$stage", block) }
                    receipt.check("api.whole-projection-parity") { assertEquals(ApiViewBuilder.catalog(reopened), bootstrap.catalog) }
                    checkApiItems(reopened, requireNotNull(bootstrap.catalog), pois) { stage, block -> receipt.check("api.$stage", block) }
                }
                // Only reached after the runtime close and every API boundary was visited.
                receipt.completed += "api"
            }
        } finally {
            receipt.values["apiReparses"] = reparses.get()
            receipt.check("api.zero-reparse") { assertEquals(0, reparses.get()) }
        }
    }

    internal fun checkApiItems(catalog: ParsedCatalog, actual: CatalogView, views: List<LocalMapPoiView>, check: (String, () -> Unit) -> Unit) {
        val expectedBalls = ApiViewBuilder.catalog(catalog).balls
        check("items.ball-domain") { assertEquals(catalog.captureBallsById.keys, actual.balls.map { it.id }.toSet()); assertEquals(actual.balls.size, actual.balls.map { it.id }.toSet().size) }
        // API's explicit "Ball <id>" fallback is diagnostic presentation, NOT an available/accepted name.
        for (ball in expectedBalls) check("items.ball.${ball.id}") { assertEquals(ball, actual.balls.single { it.id == ball.id }) }
        val numeric = catalog.localMaps.pois.filter { it.item != null }
        val items = views.filter { it.itemId != null }
        check("items.poi-domain") { assertEquals(numeric.map { it.key }.toSet(), items.map { it.key }.toSet()); assertEquals(items.size, items.map { it.key }.toSet().size) }
        for ((index, poi) in numeric.withIndex()) {
            check("items.poi.$index.numeric") {
                val view = items.single { it.key == poi.key }
                assertEquals(poi.localMapKey, view.localMapKey); assertEquals(poi.baseAreaId, view.baseAreaId)
                assertEquals(poi.tileX, view.tileX); assertEquals(poi.tileY, view.tileY)
                assertEquals(poi.destinationBaseAreaId, view.destinationBaseAreaId); assertEquals(poi.item!!.itemId, view.itemId)
            }
            check("items.poi.$index.name") {
                assertEquals(catalog.defaultTextProjection().poiItemName(poi.key, poi.item!!.itemId), items.single { it.key == poi.key }.itemName)
            }
        }
        check("items.total-domain") { assertEquals(numericIds(catalog), actual.balls.map { it.id }.toSet() + items.map { requireNotNull(it.itemId) }) }
    }

    /** Retains common-field deltas only; unrepresented historical data is explicitly not compared. */
    internal fun historicalComparison(historical: JsonObject, catalog: ParsedCatalog): Map<String, Any?> {
        val projection = catalog.languageManifest.projections.single()
        val current = json.toJsonTree(mapOf("manifestStatus" to catalog.languageManifest.status.name,
            "projectionStatus" to projection.status.name, "codecId" to projection.codecId, "codecVersion" to projection.codecVersion,
            "localizedCapabilities" to catalog.defaultLocalizedText()!!.localizedCapabilities)).asJsonObject
        val differences = linkedMapOf<String, Any?>()
        fun compare(prefix: String, old: JsonObject, now: JsonObject) {
            for ((key, value) in old.entrySet()) {
                if (!now.has(key)) continue
                val next = now[key]; val path = if (prefix.isEmpty()) key else "$prefix.$key"
                if (value.isJsonObject && next.isJsonObject) compare(path, value.asJsonObject, next.asJsonObject)
                else if (value != next) differences[path] = mapOf("historical49" to value, "current60" to next)
            }
        }
        compare("", historical, current)
        return mapOf("historicalParserSchema" to 49, "currentParserSchema" to 60, "status" to "PENDING_CLASSIFICATION_NOT_EQUALITY_GATE",
            "historicalCell" to historical, "currentComparableObservation" to current, "commonFieldChanges" to differences,
            "nonItemEqualityEstablished" to false, "uncomparedScope" to "historical catalog payloads/shared sections and fields absent from either summary; no old caches opened")
    }
    private fun availableNames(fields: Map<Int, CatalogField<String>>) = fields.mapNotNull { (id, field) ->
        field.value?.takeIf { field.status == CapabilityStatus.AVAILABLE && it.isNotBlank() }?.let { id to it }
    }.toMap()
    @Suppress("UNCHECKED_CAST")
    private fun <T> property(value: Any, name: String): T = value.javaClass.getMethod("get$name").invoke(value) as T
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun write(path: Path, value: Any?) { Files.write(path, json.toJson(value).toByteArray(Charsets.UTF_8)) }

    internal class Receipt(val root: Path, val control: JsonObject) {
        val checks = linkedMapOf<String, String>(); val errors = linkedMapOf<String, String>(); val completed = linkedSetOf<String>()
        val values = linkedMapOf<String, Any?>("control" to control, "terminal" to "NOT_STARTED", "semanticAcceptance" to false,
            "acceptedNames" to 0, "requiredSemanticCompletion" to "NOT_ACCEPTED", "inputReadAttempts" to 0, "inputReads" to 0,
            "originalAnalysisInvocations" to 0, "checks" to checks, "errors" to errors, "completedPaths" to completed)
        fun save() { write(root.resolve("receipt.json"), values) }
        fun finish() {
            values["semanticAcceptance"] = false; values["acceptedNames"] = 0; values["requiredSemanticCompletion"] = "NOT_ACCEPTED"
            values["missingPaths"] = requiredPaths - completed
            values["terminal"] = if (errors.isEmpty() && completed.containsAll(requiredPaths)) "DIAGNOSTIC_CAPTURE_COMPLETE" else "DIAGNOSTIC_CAPTURE_FAILED"
            save()
        }
        fun failure(stage: String, failure: Throwable) {
            require(stage !in checks) { "duplicate check $stage" }
            checks[stage] = "FAIL"; errors[stage] = failure.stackTraceToString(); save()
        }
        fun <T> check(stage: String, block: () -> T): T? {
            require(stage !in checks) { "duplicate check $stage" }
            values["lastStage"] = stage; save()
            return try { block().also { checks[stage] = "PASS"; save() } }
            catch (failure: AssertionError) { failure(stage, failure); null }
            catch (failure: Exception) { failure(stage, failure); null }
        }
    }
}
