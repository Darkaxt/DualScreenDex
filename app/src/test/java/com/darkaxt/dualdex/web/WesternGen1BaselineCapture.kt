package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.*
import com.enrpau.dualscreendex.companion.api.ApiViewBuilder
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.parser.analysis.Gen1ItemReference
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.*
import com.enrpau.dualscreendex.parser.io.LoadedRom
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.model.*
import com.enrpau.dualscreendex.parser.parse.ParserOrchestrator
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.internal.CallableReference
import org.junit.Assert.*

/** Diagnostic only. Names/counts observed here are never independent semantic expectations. */
internal object WesternGen1BaselineCapture {
    internal val json = GsonBuilder().setPrettyPrinting().serializeNulls().create()
    private const val manifestSha = "ee408ad4a5d51da8656ff336ff7c139d92fc24b9d201f6817c47ce3ef194a749"
    internal val controlHashes = mapOf(
        ("en" to "RED_BLUE") to "5ca7ba01642a3b27b0cc0b5349b52792795b62d3ed977e98a09390659af96b7b",
        ("en" to "YELLOW") to "8cbaa499397e4f1a679c992ea9382a2dd7942ab398b48c19829c2d9529de47bf",
        ("fr" to "RED_BLUE") to "23766290f3b2347f815f1e8977c3b84047ed880cadda8c4f1a3595a633daa303",
        ("fr" to "YELLOW") to "77b31f874fd877fbf48757f315ffc3144e06fc7222aba6ac4eda2045ebf5d3fa",
        ("de" to "RED_BLUE") to "9cd186b288dbcd52413d561ae449f1f700c32b45af56dbf849095d4a0c8637a6",
        ("de" to "YELLOW") to "aed41ea3e785ac9c06fd641270ff30b59de5fc64fed4349e81a9aaa4ba7d31f9",
        ("it" to "RED_BLUE") to "e805d00b0002156d38b96efd57c823b0db3a3ef4cd32f98bd9bb4779bda3dd5b",
        ("it" to "YELLOW") to "e3f0663e16ac240bc1c6681cdb95ca7230d86fca23eb931ea58f4abce0002856",
        ("es" to "RED_BLUE") to "a756cf7ad888aa46de4b9699a177a0edf1775e59eb6330014c6f5c139be9c45d",
        ("es" to "YELLOW") to "55d56d6f73225886bfdc0db1ddb754d2f4dfc6ef5ae74ddfa079c23b6ec80039",
    )

    fun run(factory: CatalogDatabaseFactory) {
        // All metadata/opt-in validation completes before creating or reading any ROM path.
        lateinit var manifestBytes: ByteArray
        lateinit var inventoryBytes: ByteArray
        val selected = preflight(System.getenv("DUALDEX_WESTERN_GEN1_DIAGNOSTIC"),
            { readMetadata("DUALDEX_WESTERN_MANIFEST").also { manifestBytes = it } },
            { readMetadata("DUALDEX_WESTERN_INVENTORY").also { inventoryBytes = it } })
        val root = Files.createDirectory(Path.of(requireNotNull(System.getenv("DUALDEX_TEST_TEMP_ROOT"))).resolve("gen1-controls"))
        Files.write(root.resolve("manifest.json"), manifestBytes)
        Files.write(root.resolve("inventory.json"), inventoryBytes)
        write(root.resolve("scope.json"), mapOf("manifestSha256" to manifestSha, "inventorySha256" to sha256(inventoryBytes), "controls" to selected,
            "historicalOutputUsedAsExpectation" to false, "semanticAcceptance" to false,
            "requiredSemanticCompletion" to "NOT_ACCEPTED", "independentCompiledProof" to "NOT_RUN",
            "witnessLimits" to mapOf("references" to 32768, "bytesEach" to 2048, "eventsPerKind" to 64, "coordinateIndexExclusive" to 256)))
        // Create every control directory before the first ROM read. Repeated invocations cannot reuse evidence.
        val receipts = selected.map { control ->
            val key = "${control["language"].asString}-${control["family"].asString}-${control["sha256"].asString}"
            Receipt(Files.createDirectory(root.resolve(key)), control).also { it.save() }
        }
        for (receipt in receipts) {
            try { capture(receipt, factory) }
            catch (failure: AssertionError) { receipt.failure("control", failure) }
            catch (failure: Exception) { receipt.failure("control", failure) }
            finally {
                receipt.finish()
                println("WESTERN_GEN1_DIAGNOSTIC ${receipt.root.fileName} terminal=${receipt.values["terminal"]} " +
                    "authority=${receipt.values["authorityStatus"]} references=${receipt.values["referenceCount"]} " +
                    "available=${receipt.values["availableCount"]}/${receipt.values["requestedCount"]} failures=${receipt.errors.keys}")
            }
        }
        write(root.resolve("batch.json"), mapOf("junitMethods" to 1, "controlReceipts" to receipts.size,
            "semanticAcceptance" to false, "requiredSemanticCompletion" to "NOT_ACCEPTED", "controls" to receipts.map { it.values },
            "failedControls" to receipts.filter { it.errors.isNotEmpty() }.map { it.root.fileName.toString() }))
        assertTrue("Western Gen I diagnostic integrity failed; retained evidence at $root", receipts.all { it.errors.isEmpty() })
    }

    internal fun preflight(optIn: String?, manifest: () -> ByteArray, inventory: () -> ByteArray): List<JsonObject> {
        require(optIn == "1") { "DUALDEX_WESTERN_GEN1_DIAGNOSTIC must be exactly 1" }
        val bytes = manifest()
        require(bytes.size in 1..4_194_304 && sha256(bytes) == manifestSha) { "original 35-control manifest hash required" }
        val all = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonArray.map { it.asJsonObject }
        val publicBytes = inventory()
        require(publicBytes.size in 1..4_194_304)
        val public = JsonParser.parseString(publicBytes.toString(Charsets.UTF_8)).asJsonObject.getAsJsonArray("cells").map { it.asJsonObject }
        return selectControls(all, public)
    }

    internal fun selectControls(all: List<JsonObject>, inventory: List<JsonObject>): List<JsonObject> {
        fun key(row: JsonObject, hash: String) = Triple(row["language"].asString, row["family"].asString, row[hash].asString)
        fun keys(rows: List<JsonObject>, hash: String): Set<Triple<String, String, String>> {
            require(rows.size == 35)
            val keys = rows.map { key(it, hash) }
            require(keys.toSet().size == 35 && keys.map { it.third }.toSet().size == 35)
            require(keys.map { it.first to it.second }.toSet().size == 35)
            return keys.toSet()
        }
        require(keys(all, "sha256") == keys(inventory, "romSha256")) { "public/private identity mismatch" }
        val selected = all.filter { (it["language"].asString to it["family"].asString) in controlHashes }
        require(selected.size == 10)
        selected.forEach {
            require(it["sha256"].asString == controlHashes.getValue(it["language"].asString to it["family"].asString))
            require(it["size"].asLong == 1048576L)
            require(it["file"].asString.isNotBlank())
        }
        return selected
    }

    private fun readMetadata(variable: String): ByteArray {
        val path = Path.of(requireNotNull(System.getenv(variable)))
        require(Files.size(path) in 1..4_194_304)
        return Files.readAllBytes(path)
    }

    /** Retain the ordinary CatalogParser context and bound materializer, without rediscovery. */
    internal fun observe(rom: RomImage): Pair<Any, RomAnalysisSession> {
        val method = ParserOrchestrator.javaClass.methods.single { it.name.startsWith("analyzeForCatalog") && it.parameterCount == 3 }
        val context = requireNotNull(method.invoke(ParserOrchestrator, rom, null, ParserCancellationToken.NONE))
        val callback: Any = property(context, "ResolveItemNames")
        val materializer = (callback as CallableReference).boundReceiver as ItemNameMaterializer
        val field = ItemNameMaterializer::class.java.getDeclaredField("session").apply { isAccessible = true }
        val session = field.get(materializer) as RomAnalysisSession
        assertSame(rom, session.rom)
        return context to session
    }

    private fun capture(receipt: Receipt, factory: CatalogDatabaseFactory) {
        val control = receipt.control
        val path = Path.of(control["file"].asString)
        val rom = receipt.check("input.exact-size-sha") {
            assertEquals(1048576L, Files.size(path))
            receipt.values["inputReadAttempts"] = 1; receipt.save()
            val bytes = Files.readAllBytes(path)
            receipt.values["inputReads"] = 1
            assertEquals(1048576, bytes.size)
            RomImage(bytes).also { assertEquals(control["sha256"].asString, it.sha256) }
        } ?: return
        receipt.values["originalAnalysisInvocations"] = 1
        val (context, session) = receipt.check("analysis.original-context") { observe(rom) } ?: return
        val analysis: ParseResult = property(context, "Analysis")
        write(receipt.root.resolve("analysis.json"), analysis)
        val frozen = session.gen1ItemNameAuthority
        receipt.values["authorityStatus"] = frozen.javaClass.simpleName
        write(receipt.root.resolve("authority.json"), mapOf("status" to frozen.javaClass.simpleName, "authority" to frozen,
            "productionLimits" to session.limits, "independentCompiledProof" to "NOT_RUN",
            "rawNominationAndCodeWindows" to "NOT_IN_DTO; INDEPENDENT_PROOF_REQUIRED", "tokenDomainEvidence" to "NOT_PROVED"))
        receipt.check("selection") {
            assertEquals(SelectionStatus.SELECTED, analysis.status)
            assertEquals(control["family"].asString, analysis.selectedFamily?.name)
        }
        val layout = receipt.check("layout") { requireNotNull(analysis.probes.single { it.family == analysis.selectedFamily }.resolvedLayout) } ?: return
        write(receipt.root.resolve("layout-language.json"), layout.languageManifest)
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
            val authorized = refs.map { it.itemId }.filter { it in 1..255 && it in requested }.toSet()
            receipt.values["referenceCount"] = refs.size
            receipt.values["requestedCount"] = requested.size
            receipt.values["availableCount"] = availableItemNames(names).size
            receipt.values["unavailableRequestedItemIds"] = missingRequestedItemIds(requested, names).sorted()
            receipt.values["itemProducerCalls"] = calls
            write(receipt.root.resolve("references.json"), refs)
            write(receipt.root.resolve("item-observations.json"), mapOf("requestedIds" to requested.sorted(),
                "authorizedIntersection" to authorized.sorted(), "results" to names, "semanticAcceptance" to false,
                "requiredSemanticCompletion" to "NOT_ACCEPTED"))
            receipt.check("original-authority.identity") { assertSame(frozen, session.gen1ItemNameAuthority) }
            receipt.check("original-authority.producer-calls") { assertEquals(1, calls) }
            receipt.check("original-authority.producer-denominator") { assertEquals(requested, names.keys) }
            receipt.check("original-authority.authorized-names") { assertTrue(availableItemNames(names).keys.all { it in authorized }) }
            receipt.save()
        } ?: return
        val refs = session.gen1ItemReferences
        val overlay = catalog.defaultLocalizedText()
        write(receipt.root.resolve("catalog-snapshot.json"), mapOf("languageManifest" to catalog.languageManifest,
            "localization" to catalog.localization, "capabilities" to catalog.capabilities, "diagnostics" to catalog.diagnostics,
            "balls" to catalog.captureBallsById, "pois" to catalog.localMaps.pois))
        val items = catalog.localMaps.pois.filter { it.item != null }.associateBy { it.key }
        receipt.check("items.catalog-numeric-denominator") { assertEquals(requested, catalog.captureBallsById.keys + items.values.mapNotNull { it.item?.itemId }) }
        receipt.check("current-reference-and-numeric-domain") {
            assertEquals(refs.size, refs.map { it.poiKey }.toSet().size)
            assertEquals(items.size, catalog.localMaps.pois.count { it.item != null })
            assertEquals(items.keys, refs.map { it.poiKey }.toSet())
        }
        receipt.check("reference.raw-witness-capture") {
            require(refs.size <= 32768)
            val maps = catalog.localMaps.maps.associateBy { it.key }
            Files.newBufferedWriter(receipt.root.resolve("reference-witnesses.jsonl")).use { writer ->
                refs.forEachIndexed { index, ref ->
                    val witness = receipt.check("reference.$index.geometry-and-numeric-binding") {
                        val poi = items.getValue(ref.poiKey)
                        referenceWitness(rom, ref, poi, maps.getValue(poi.localMapKey))
                    }
                    // Preserve failed rows and their typed input too; later references still execute.
                    writer.appendLine(json.toJsonTree(mapOf("reference" to ref, "witness" to witness,
                        "status" to if (witness == null) "FAILED" else "OBSERVED_NOT_PROVED")).toString())
                }
            }
        }
        receipt.check("language.exact-projection") {
            assertEquals(LanguageResolutionStatus.RESOLVED, catalog.languageManifest.status)
            assertEquals(control["language"].asString, catalog.languageManifest.defaultLanguage?.value)
            val projection = catalog.languageManifest.projections.single()
            assertEquals(control["language"].asString, projection.language.value)
            assertEquals("gb-gen1-${control["language"].asString}", projection.codecId)
            assertEquals(1, projection.codecVersion)
            assertEquals(LanguageResolutionStatus.RESOLVED, projection.status)
            assertEquals(setOf(projection.language), catalog.localization.overlays.keys)
        }
        checkItemObservations(requested, names, overlay?.localizedCapabilities, overlay?.itemNames) { stage, assertion -> receipt.check(stage, assertion) }
        checkNumericNames(receipt, "materialize", catalog)
        val cache = CatalogCache(receipt.root.resolve("sqlite").toFile(), factory)
        receipt.check("sqlite.write-close") {
            cache.write(catalog, CatalogSourceMetadata.direct("western-gen1-diagnostic", rom.size, "WESTERN-DIAGNOSTIC"), CatalogWriteProgress.complete())
        }
        val stored = receipt.check("sqlite.reopen-close") { requireNotNull(cache.readComplete(rom.sha256)) } ?: return
        val reopened = stored.catalog
        receipt.values["database"] = cache.fileFor(rom.sha256).absolutePath
        receipt.check("sqlite.whole-catalog-parity") { assertTrue("whole catalog differs", catalog == reopened) }
        receipt.check("sqlite.poi-parity-including-flags") { assertEquals(catalog.localMaps.pois, reopened.localMaps.pois) }
        receipt.check("sqlite.ball-parity") { assertEquals(catalog.captureBallsById, reopened.captureBallsById) }
        receipt.check("sqlite.localized-name-parity") { assertEquals(catalog.localization, reopened.localization) }
        checkNumericNames(receipt, "sqlite", reopened)
        val storedOverlay = reopened.defaultLocalizedText()
        checkItemObservations(requested, names, storedOverlay?.localizedCapabilities, storedOverlay?.itemNames) { stage, assertion -> receipt.check("sqlite.$stage", assertion) }
        for (capability in LocalizedTextCapability.entries) receipt.check("sqlite.capability.$capability.parity") {
            assertEquals(requireNotNull(overlay).localizedCapabilities.getValue(capability), requireNotNull(storedOverlay).localizedCapabilities.getValue(capability))
        }
        receipt.check("sqlite.sections") { assertEquals(CatalogSchema.requiredSections + "language_overlay:${control["language"].asString}", stored.committedSections) }
        receipt.check("sqlite.database-open-close") {
            factory.open(cache.fileFor(rom.sha256)).use { db ->
                receipt.check("sqlite.quick-check") {
                    val value = db.query("PRAGMA quick_check") { it.string("quick_check") }
                    write(receipt.root.resolve("sqlite-quick-check.json"), value); assertEquals(listOf("ok"), value)
                }
                receipt.check("sqlite.foreign-key-check") {
                    val value = db.query("PRAGMA foreign_key_check") { it.string("table") }
                    write(receipt.root.resolve("sqlite-foreign-key-check.json"), value); assertTrue(value.isEmpty())
                }
                receipt.check("sqlite.schemas") {
                    val value = db.query("SELECT parser_schema_version, schema_version FROM catalog_metadata WHERE id = 1") { it.long("parser_schema_version") to it.long("schema_version") }
                    write(receipt.root.resolve("sqlite-schemas.json"), value); assertEquals(listOf(59L to 2L), value)
                }
            }
        }
        receipt.values["boundaryNonExposure"] = mapOf("quantity" to "absent from Gen1ItemReference, LocalMapPoiItem and LocalMapPoiView",
            "collectionFlag" to "hidden coordinateIndex binds numeric collectionFlagId and SQLite; absent from LocalMapPoiView")
        val reparses = AtomicInteger()
        try {
            receipt.check("api.cache-only-startup-and-close") {
                val done = CountDownLatch(1)
                val completion = AtomicReference<Result<Unit>?>()
                ProductionCompanionRuntime(initialSettings = CompanionSettings(knowledgeMode = KnowledgeMode.DISCOVERED),
                    catalogRepository = cache, parseCatalogWithCancellation = { _, _, _, _ -> reparses.incrementAndGet(); error("Gen I diagnostic cache startup must not reparse") }).use { runtime ->
                    runtime.load(LoadedRom("western-gen1-diagnostic", rom)) { result -> completion.set(result); done.countDown() }
                    assertTrue("cache-only startup timed out", done.await(30, TimeUnit.SECONDS))
                    requireNotNull(completion.get()).getOrThrow()
                    val bootstrap = runtime.bootstrap()
                    val state = runtime.stateView()
                    write(receipt.root.resolve("api.json"), mapOf("bootstrap" to bootstrap, "pois" to state.localMapPois, "reparses" to reparses.get()))
                    receipt.check("api.authority-projection") {
                        assertTrue(bootstrap.state.catalogReady); assertEquals("CACHE_REOPEN", bootstrap.state.loading.phase)
                        assertEquals("ROM_DEFAULT", bootstrap.language?.authority)
                        assertEquals(control["language"].asString, bootstrap.language?.defaultLanguage)
                        assertEquals(control["language"].asString, bootstrap.language?.activeLanguage)
                        assertEquals(storedOverlay?.overlayVersion, bootstrap.language?.activeOverlayVersion)
                        assertEquals(listOf(control["language"].asString), bootstrap.language?.projections?.map { it.language })
                    }
                    val states = bootstrap.language?.projections?.singleOrNull()?.localizedCapabilities
                    receipt.check("api.capabilities.inventory") {
                        assertEquals(LocalizedTextCapability.entries.map { it.name }.toSet(), requireNotNull(states).keys); assertEquals(15, states.size)
                    }
                    for (capability in LocalizedTextCapability.entries) {
                        receipt.check("api.capability.$capability.status") { assertEquals(requireNotNull(storedOverlay).localizedCapabilities.getValue(capability).status.name, requireNotNull(states).getValue(capability.name).status) }
                        receipt.check("api.capability.$capability.counts") {
                            val expected = requireNotNull(storedOverlay).localizedCapabilities.getValue(capability)
                            val actual = requireNotNull(states).getValue(capability.name)
                            assertEquals(expected.expectedRecords, actual.expectedRecords); assertEquals(expected.coveredRecords, actual.coveredRecords)
                        }
                    }
                    receipt.check("api.whole-projection-parity") {
                        val api = requireNotNull(bootstrap.catalog); assertEquals(rom.sha256, api.hash); assertTrue(api == ApiViewBuilder.catalog(reopened))
                    }
                    val projected = state.localMapPois.filter { it.itemId != null }.associateBy { it.key }
                    val numeric = reopened.localMaps.pois.filter { it.item?.itemId != null }.associateBy { it.key }
                    receipt.check("api.exposed-numeric-parity") {
                        assertEquals(numeric.keys, projected.keys)
                        numeric.forEach { (key, poi) ->
                            val view = projected.getValue(key)
                            assertEquals(poi.localMapKey, view.localMapKey); assertEquals(poi.baseAreaId, view.baseAreaId)
                            assertEquals(poi.tileX, view.tileX); assertEquals(poi.tileY, view.tileY)
                            assertEquals(poi.destinationBaseAreaId, view.destinationBaseAreaId); assertEquals(poi.item!!.itemId, view.itemId)
                        }
                    }
                    receipt.check("api.poi-item-name-parity") {
                        numeric.forEach { (key, poi) -> assertEquals(reopened.defaultTextProjection().itemName(requireNotNull(poi.item?.itemId)), projected.getValue(key).itemName) }
                    }
                    fun apiItems() = (requireNotNull(bootstrap.catalog).balls.map { it.id to it.name } +
                        projected.values.map { requireNotNull(it.itemId) to it.itemName }).groupBy({ it.first }, { it.second })
                    receipt.check("api.items.denominator") { assertEquals(requested, apiItems().keys) }
                    receipt.check("api.items.name-parity") {
                        val values = apiItems()
                        requested.forEach { id -> assertEquals(setOf(reopened.defaultTextProjection().itemName(id)), values.getValue(id).toSet()) }
                    }
                }
            }
        } finally {
            receipt.values["apiReparses"] = reparses.get()
            receipt.check("api.zero-reparse") { assertEquals(0, reparses.get()) }
        }
    }

    private fun checkNumericNames(receipt: Receipt, stage: String, catalog: ParsedCatalog) {
        receipt.check("$stage.shared-numeric-names-null") {
            assertTrue(catalog.captureBallsById.values.all { it.name.value == null })
            assertTrue(catalog.localMaps.pois.all { it.item?.displayName == null })
        }
    }

    /** Missing/unavailable labels retain the entire producer/capability denominator, not overlay entries. */
    internal fun checkItemObservations(
        requested: Set<Int>, producerNames: Map<Int, CatalogField<String>>,
        states: Map<LocalizedTextCapability, LocalizedCapabilityState>?, overlayNames: Map<Int, CatalogField<String>>?,
        check: (String, () -> Unit) -> Unit,
    ) {
        check("capabilities.inventory") { assertEquals(LocalizedTextCapability.entries.toSet(), requireNotNull(states).keys); assertEquals(15, states.size) }
        for (capability in LocalizedTextCapability.entries) check("capability.$capability.counts") {
            val state = requireNotNull(states?.get(capability))
            assertTrue(state.coveredRecords in 0..state.expectedRecords)
            assertEquals(state.expectedRecords - state.coveredRecords, state.incompleteRecords)
        }
        check("items.producer-denominator") { assertEquals(requested, producerNames.keys) }
        check("items.capability-denominator") { assertEquals(requested.size, requireNotNull(states?.get(LocalizedTextCapability.ITEM_NAMES)).expectedRecords) }
        check("items.capability-covered-count") { assertEquals(requireNotNull(overlayNames).size, requireNotNull(states?.get(LocalizedTextCapability.ITEM_NAMES)).coveredRecords) }
        check("items.overlay-available-fields") { assertTrue(requireNotNull(overlayNames).values.all { it.status == CapabilityStatus.AVAILABLE && !it.value.isNullOrBlank() }) }
        check("items.overlay-sparse-keys") { assertEquals(availableItemNames(producerNames).keys, requireNotNull(overlayNames).keys) }
        check("items.available-name-parity") { assertEquals(availableItemNames(producerNames), requireNotNull(overlayNames).mapValues { it.value.value }) }
    }

    internal fun missingRequestedItemIds(requested: Set<Int>, names: Map<Int, CatalogField<String>>) = requested - availableItemNames(names).keys
    private fun availableItemNames(names: Map<Int, CatalogField<String>>) = names.mapNotNull { (id, field) ->
        field.value?.takeIf { field.status == CapabilityStatus.AVAILABLE && it.isNotBlank() }?.let { id to it }
    }.toMap()

    internal data class RawWindow(val role: String, val offset: Int, val hex: String)
    internal data class ReferenceWitness(val eventRow: Int, val tileX: Int, val tileY: Int, val collectionFlagId: Int?, val windows: List<RawWindow>)

    /** Source-shaped bounded reconciliation, NOT nomination/uniqueness or item-name semantic proof. */
    internal fun referenceWitness(rom: RomImage, ref: Gen1ItemReference, poi: LocalMapPoi, map: LocalMap): ReferenceWitness {
        val windows = mutableListOf<RawWindow>()
        var captured = 0
        fun span(at: Int, count: Int, bank: Int? = null) {
            require(count >= 0 && at >= 0 && at.toLong() + count <= rom.size)
            if (bank != null) {
                val physicalBank = at / 0x4000
                require(bank >= 0 && (physicalBank == 0 || physicalBank == bank))
                require(at.toLong() + count <= (physicalBank.toLong() + 1) * 0x4000)
            }
        }
        fun raw(role: String, at: Int, count: Int, bank: Int? = null) {
            span(at, count, bank); require(count <= 2048 - captured); captured += count
            windows += RawWindow(role, at, (0 until count).joinToString("") { "%02x".format(rom.u8(at + it)) })
        }
        fun u8(at: Int, bank: Int? = null): Int { span(at, 1, bank); return rom.u8(at) }
        fun word(at: Int): Int { span(at, 2); return rom.u16le(at) }
        fun add(at: Int, delta: Int): Int = Math.addExact(at, delta)
        fun mapped(bank: Int, address: Int): Int = requireNotNull(rom.gbBankAddress(bank, address))
        require(ref.itemId in 1..255 && ref.baseAreaId in 0..255 && ref.sourceBank in 0..255)
        require(ref.poiKey == poi.key && poi.localMapKey == map.key && ref.baseAreaId == poi.baseAreaId && poi.baseAreaId == map.baseAreaId)
        require(poi.item?.itemId == ref.itemId && u8(ref.operandOffset) == ref.itemId)
        span(ref.recordRoot, 1, ref.sourceBank)
        val row: Int
        val x: Int
        val y: Int
        val flag: Int?
        if (ref.kind == Gen1ItemReference.Kind.VISIBLE_OBJECT) {
            require(ref.hiddenHandler == null && ref.coordinateRoot == null && ref.coordinateIndex == null)
            val header = requireNotNull(ref.mapHeader)
            val bankEntry = add(requireNotNull(ref.mapBankTable), ref.baseAreaId)
            val pointerEntry = add(requireNotNull(ref.mapPointerTable), ref.baseAreaId * 2)
            raw("mapBankEntry", bankEntry, 1, requireNotNull(ref.mapBankTable) / 0x4000)
            raw("mapPointerEntry", pointerEntry, 2, requireNotNull(ref.mapPointerTable) / 0x4000)
            require(u8(bankEntry) == ref.sourceBank && mapped(ref.sourceBank, word(pointerEntry)) == header)
            raw("mapHeader", header, 10, ref.sourceBank)
            val connectionFlags = u8(header + 9)
            require(connectionFlags and 0xf0 == 0)
            val pointer = add(header, 10 + Integer.bitCount(connectionFlags and 0x0f) * 11)
            require(pointer == ref.objectPointerField)
            raw("objectPointer", pointer, 2, ref.sourceBank)
            require(mapped(ref.sourceBank, word(pointer)) == ref.recordRoot)
            var cursor = add(ref.recordRoot, 1)
            fun skipRecords(width: Int) {
                val count = u8(cursor, ref.sourceBank); require(count <= 64)
                cursor++; span(cursor, count * width, ref.sourceBank); cursor += count * width
            }
            skipRecords(4); skipRecords(3)
            val count = u8(cursor++, ref.sourceBank); require(count <= 64)
            var matched: Pair<Int, Int>? = null
            repeat(count) { index ->
                span(cursor, 6, ref.sourceBank)
                val type = u8(cursor + 5) and 0xc0
                val width = when (type) { 0 -> 6; 0x40 -> 8; 0x80 -> 7; else -> error("conflicting object type") }
                span(cursor, width, ref.sourceBank)
                if (cursor + 6 == ref.operandOffset) { require(type == 0x80); require(matched == null); matched = cursor to index }
                cursor += width
            }
            val match = requireNotNull(matched) { "operand is not an item in the bound object list" }
            row = match.first
            require(ref.poiKey == "${map.key}/object/${match.second}" && poi.kind == LocalMapPoiKind.VISIBLE_ITEM)
            raw("objectListTraversal", ref.recordRoot, cursor - ref.recordRoot, ref.sourceBank)
            raw("itemRow", row, 7, ref.sourceBank)
            x = u8(row + 2) - 4; y = u8(row + 1) - 4; flag = null
        } else {
            require(ref.mapHeader == null && ref.objectPointerField == null && ref.mapBankTable == null && ref.mapPointerTable == null)
            row = Math.subtractExact(ref.operandOffset, 2)
            val distance = row.toLong() - ref.recordRoot
            require(distance >= 0 && distance % 6 == 0L && distance / 6 < 64)
            for (index in 0..(distance / 6).toInt()) require(u8(ref.recordRoot + index * 6, ref.sourceBank) != 0xff)
            raw("hiddenListTraversal", ref.recordRoot, distance.toInt() + 6, ref.sourceBank)
            raw("hiddenRow", row, 6, ref.sourceBank)
            val handler = requireNotNull(ref.hiddenHandler)
            require(mapped(u8(row + 3), word(row + 4)) == handler)
            val handlerBank = handler / 0x4000
            span(handler, 27, handlerBank)
            val handlerBytes = if (u8(handler + 25) == 0xc0) 29 else 30
            raw("hiddenHandler", handler, handlerBytes, handlerBank)
            // Only the nominated handler is inspected; no independent nomination scan is implied.
            for ((offset, opcode) in mapOf(0 to 0x21, 3 to 0xcd, 6 to 0xea, 9 to 0x21, 12 to 0xfa,
                15 to 0x4f, 16 to 6, 18 to 0x3e, 20 to 0xcd, 23 to 0x79, 24 to 0xa7)) require(u8(handler + offset) == opcode)
            require(word(handler + 7) == word(handler + 13))
            when (u8(handler + 25)) {
                0xc0 -> require(u8(handler + 26) == 0xcd)
                0x20 -> {
                    require(u8(handler + 27) == 0xcd)
                    val target = handler + 27 + u8(handler + 26).toByte().toInt()
                    require(target > handler + 27)
                    raw("hiddenContinuation", target, 5, handlerBank)
                    require(u8(target) == 0x3e && u8(target + 1) == 0xff && u8(target + 2) == 0xe0 && u8(target + 4) == 0xc9)
                }
                else -> error("unsupported hidden-item continuation")
            }
            val coordinateRoot = requireNotNull(ref.coordinateRoot)
            require(mapped(handlerBank, word(handler + 1)) == coordinateRoot)
            flag = requireNotNull(ref.coordinateIndex); require(flag in 0 until 256)
            val coordinate = add(coordinateRoot, flag * 3)
            raw("coordinateTraversal", coordinateRoot, flag * 3 + 3, coordinateRoot / 0x4000)
            for (index in 0..flag) require(u8(coordinateRoot + index * 3) != 0xff)
            raw("coordinateRow", coordinate, 3)
            x = u8(row + 1); y = u8(row)
            require(u8(coordinate) == ref.baseAreaId && u8(coordinate + 1) == y && u8(coordinate + 2) == x)
            require(ref.poiKey == "${map.key}/hidden/$flag" && poi.kind == LocalMapPoiKind.HIDDEN_ITEM)
        }
        require(x in 0 until map.gridWidth && y in 0 until map.gridHeight)
        require(x == poi.tileX && y == poi.tileY && flag == poi.item?.collectionFlagId)
        return ReferenceWitness(row, x, y, flag, windows)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> property(value: Any, name: String): T = value.javaClass.getMethod("get$name").invoke(value) as T
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun write(path: Path, value: Any?) { Files.write(path, json.toJson(value).toByteArray(Charsets.UTF_8)) }

    internal class Receipt(val root: Path, val control: JsonObject) {
        val errors = linkedMapOf<String, String>()
        val checks = linkedMapOf<String, String>()
        val values = linkedMapOf<String, Any?>("control" to control, "terminal" to "NOT_STARTED",
            "semanticAcceptance" to false, "requiredSemanticCompletion" to "NOT_ACCEPTED",
            "inputReads" to 0, "inputReadAttempts" to 0, "originalAnalysisInvocations" to 0, "checks" to checks, "errors" to errors)
        fun save() { write(root.resolve("receipt.json"), values) }
        fun finish() {
            values["semanticAcceptance"] = false; values["requiredSemanticCompletion"] = "NOT_ACCEPTED"
            values["terminal"] = if (errors.isEmpty()) "DIAGNOSTIC_CAPTURE_COMPLETE" else "BASELINE_INTEGRITY_BROKEN"
            save()
        }
        fun failure(stage: String, failure: Throwable) { checks[stage] = "FAIL"; errors[stage] = failure.stackTraceToString(); save() }
        fun <T> check(stage: String, block: () -> T): T? {
            values["lastStage"] = stage; save()
            return try { block().also { checks[stage] = "PASS"; save() } }
            catch (failure: AssertionError) { this.failure(stage, failure); null }
            catch (failure: Exception) { this.failure(stage, failure); null }
        }
    }
}
