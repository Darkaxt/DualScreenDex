package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.*
import com.enrpau.dualscreendex.companion.api.ApiViewBuilder
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.parser.analysis.Gen2ItemReference
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

/** Opt-in diagnostic only. Observed labels never become semantic expectations. */
internal object WesternGen2BaselineCapture {
    private val json = GsonBuilder().setPrettyPrinting().serializeNulls().create()
    private const val manifestSha = "ee408ad4a5d51da8656ff336ff7c139d92fc24b9d201f6817c47ce3ef194a749"
    private val languages = setOf("en", "fr", "de", "it", "es")
    private val families = setOf("GOLD_SILVER", "CRYSTAL")

    fun run(factory: CatalogDatabaseFactory) {
        val root = Path.of(requireNotNull(System.getenv("DUALDEX_TEST_TEMP_ROOT"))).resolve("controls")
        // A second invocation cannot overwrite or silently reuse the first capture.
        Files.createDirectory(root)
        val manifestBytes = Files.readAllBytes(Path.of(requireNotNull(System.getenv("DUALDEX_WESTERN_MANIFEST"))))
        assertEquals(manifestSha, sha256(manifestBytes))
        val all = JsonParser.parseString(manifestBytes.toString(Charsets.UTF_8)).asJsonArray.map { it.asJsonObject }
        val inventoryBytes = Files.readAllBytes(Path.of(requireNotNull(System.getenv("DUALDEX_WESTERN_INVENTORY"))))
        val inventory = JsonParser.parseString(inventoryBytes.toString(Charsets.UTF_8)).asJsonObject.getAsJsonArray("cells")
            .map { it.asJsonObject }
        fun key(row: JsonObject, hash: String) = listOf(row["language"].asString, row["family"].asString, row[hash].asString)
        val privateKeys = all.map { key(it, "sha256") }
        val publicKeys = inventory.map { key(it, "romSha256") }
        write(root.resolve("inventory-binding.json"), mapOf("manifestSha256" to sha256(manifestBytes),
            "inventorySha256" to sha256(inventoryBytes), "privateKeys" to privateKeys, "publicKeys" to publicKeys,
            "historicalOutputUsedAsExpectation" to false))
        assertEquals(35, all.size)
        assertEquals(35, inventory.size)
        assertEquals(35, privateKeys.toSet().size)
        assertEquals(35, all.map { it["sha256"].asString }.toSet().size)
        assertEquals(35, all.map { it["language"].asString to it["family"].asString }.toSet().size)
        assertEquals(privateKeys.toSet(), publicKeys.toSet())
        val selected = all.filter { it["language"].asString in languages && it["family"].asString in families }
        assertEquals(10, selected.size)
        assertEquals(languages.flatMap { language -> families.map { language to it } }.toSet(),
            selected.map { it["language"].asString to it["family"].asString }.toSet())
        val receipts = selected.map { control ->
            val key = "${control["language"].asString}-${control["family"].asString}-${control["sha256"].asString}"
            Receipt(Files.createDirectory(root.resolve(key)), control).also { it.save() }
        }
        for (receipt in receipts) {
            try {
                capture(receipt, factory)
            } catch (failure: AssertionError) {
                receipt.failure("control", failure)
            } catch (failure: Exception) {
                receipt.failure("control", failure)
            } finally {
                receipt.values["terminal"] = if (receipt.errors.isEmpty()) "DIAGNOSTIC_CAPTURE_COMPLETE" else "BASELINE_INTEGRITY_BROKEN"
                receipt.save()
                println("WESTERN_GEN2_BASELINE ${receipt.root.fileName} terminal=${receipt.values["terminal"]} " +
                    "authority=${receipt.values["authorityStatus"]} references=${receipt.values["referenceCount"]} " +
                    "available=${receipt.values["availableCount"]}/${receipt.values["requestedCount"]} failures=${receipt.errors.keys}")
            }
        }
        write(root.resolve("batch.json"), mapOf("junitMethods" to 1, "controlReceipts" to receipts.size,
            "semanticAcceptance" to false, "controls" to receipts.map { it.values },
            "failedControls" to receipts.filter { it.errors.isNotEmpty() }.map { it.root.fileName.toString() }))
        assertTrue("mandatory baseline integrity failed; retained receipts at $root",
            receipts.all { it.errors.isEmpty() })
    }

    /** Same production context/callbacks as CatalogParser, with no second analysis or resolver invocation. */
    internal fun observe(rom: RomImage): Pair<Any, RomAnalysisSession> {
        val method = ParserOrchestrator.javaClass.methods.single {
            it.name.startsWith("analyzeForCatalog") && it.parameterCount == 3
        }
        val context = method.invoke(ParserOrchestrator, rom, null, ParserCancellationToken.NONE)
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
            assertEquals(control["size"].asLong, Files.size(path))
            RomImage(Files.readAllBytes(path)).also { assertEquals(control["sha256"].asString, it.sha256) }
        } ?: return
        receipt.values["inputReads"] = 1
        receipt.values["originalAnalysisInvocations"] = 1
        val (context, session) = receipt.check("analysis.original-context") { observe(rom) } ?: return
        val analysis: ParseResult = property(context, "Analysis")
        write(receipt.root.resolve("analysis.json"), analysis)
        val frozen = session.gen2ItemNameAuthority
        receipt.values["authorityStatus"] = frozen.javaClass.simpleName
        write(receipt.root.resolve("authority.json"), mapOf("status" to frozen.javaClass.simpleName, "authority" to frozen,
            "productionLimits" to session.limits, "independentCompiledProof" to "NOT_RUN"))
        receipt.check("selection") {
            assertEquals(SelectionStatus.SELECTED, analysis.status)
            assertEquals(control["family"].asString, analysis.selectedFamily?.name)
        }
        val layout = receipt.check("layout") {
            requireNotNull(analysis.probes.single { it.family == analysis.selectedFamily }.resolvedLayout)
        } ?: return
        write(receipt.root.resolve("layout-language.json"), layout.languageManifest)
        var calls = 0
        var requested = emptySet<Int>()
        var names = emptyMap<Int, CatalogField<String>>()
        val producer: (ResolvedRomLayout, Set<Int>) -> Map<Int, CatalogField<String>> = property(context, "ResolveItemNames")
        val catalog = try {
            receipt.check("materialize.normal-callbacks") {
                CatalogMaterializer.materialize(rom, analysis, layout,
                    resolveGen3AreaNames = property(context, "ResolveGen3AreaNames"),
                    resolveWorldMap = property(context, "ResolveWorldMap"),
                    resolveLocalMaps = property(context, "ResolveLocalMaps"),
                    resolveMoveDescriptions = property(context, "ResolveMoveDescriptions"),
                    resolveAbilityMechanics = property(context, "ResolveAbilityMechanics"),
                    resolveNatures = property(context, "ResolveNatures"),
                    resolveItemNames = { selectedLayout, ids ->
                        calls++
                        requested = ids.toSet()
                        producer(selectedLayout, ids).also { names = it.toMap() }
                    })
            }
        } finally {
            val refs = session.gen2ItemReferences.toList()
            receipt.values["referenceCount"] = refs.size
            receipt.values["requestedCount"] = requested.size
            receipt.values["availableCount"] = availableItemNames(names).size
            receipt.values["unavailableRequestedItemIds"] = missingRequestedItemIds(requested, names).sorted()
            receipt.values["requiredSemanticCompletion"] = "NOT_ACCEPTED"
            receipt.values["itemProducerCalls"] = calls
            val authorized = refs.filter { it.mapGroupTable != null && it.mapGroupBank != null && it.mapHeader != null }
                .map { it.itemId }.filter { it in 1..255 && it in requested }.toSet()
            write(receipt.root.resolve("references.json"), refs)
            write(receipt.root.resolve("item-observations.json"), mapOf("requestedIds" to requested.sorted(),
                "authorizedIntersection" to authorized.sorted(), "results" to names, "semanticAcceptance" to false))
            receipt.check("reference.raw-witness-capture") { writeWitnesses(receipt.root, rom, refs) }
            receipt.check("original-authority.identity") { assertSame(frozen, session.gen2ItemNameAuthority) }
            receipt.check("original-authority.producer-calls") { assertEquals(1, calls) }
            receipt.check("original-authority.producer-denominator") { assertEquals(requested, names.keys) }
            receipt.check("original-authority.authorized-names") {
                assertTrue(names.filterValues { it.value != null }.keys.all { it in authorized })
            }
            receipt.save()
        } ?: return
        val refs = session.gen2ItemReferences
        val overlay = catalog.defaultLocalizedText()
        write(receipt.root.resolve("catalog-snapshot.json"), mapOf("languageManifest" to catalog.languageManifest,
            "localization" to catalog.localization, "capabilities" to catalog.capabilities, "diagnostics" to catalog.diagnostics,
            "balls" to catalog.captureBallsById, "pois" to catalog.localMaps.pois))
        val items = catalog.localMaps.pois.filter { it.item != null }.associateBy { it.key }
        receipt.check("items.catalog-numeric-denominator") {
            assertEquals(requested, catalog.captureBallsById.keys + items.values.mapNotNull { it.item?.itemId })
        }
        receipt.check("current-reference-and-numeric-parity") {
            assertEquals(refs.size, refs.map { it.poiKey }.toSet().size)
            assertEquals(items.keys, refs.map { it.poiKey }.toSet())
            for (ref in refs) {
                val poi = items.getValue(ref.poiKey)
                assertEquals(ref.itemId, poi.item!!.itemId)
                assertEquals(ref.collectionFlag, poi.item!!.collectionFlagId)
                assertEquals(ref.tileX, poi.tileX)
                assertEquals(ref.tileY, poi.tileY)
                assertEquals(ref.baseAreaId, poi.baseAreaId)
                assertEquals(ref.itemId, rom.u8(ref.operandOffset))
            }
        }
        receipt.check("language.exact-projection") {
            assertEquals(LanguageResolutionStatus.RESOLVED, catalog.languageManifest.status)
            assertEquals(control["language"].asString, catalog.languageManifest.defaultLanguage?.value)
            val projection = catalog.languageManifest.projections.single()
            assertEquals(control["language"].asString, projection.language.value)
            assertEquals("gb-gen2-${control["language"].asString}", projection.codecId)
            assertEquals(1, projection.codecVersion)
            assertEquals(LanguageResolutionStatus.RESOLVED, projection.status)
            assertEquals(setOf(projection.language), catalog.localization.overlays.keys)
        }
        checkItemObservations(requested, names, overlay?.localizedCapabilities, overlay?.itemNames) { stage, assertion ->
            receipt.check(stage, assertion)
        }
        val cache = CatalogCache(receipt.root.resolve("sqlite").toFile(), factory)
        receipt.check("sqlite.write-close") {
            cache.write(catalog, CatalogSourceMetadata.direct("western-gen2-baseline", rom.size, "WESTERN-DIAGNOSTIC"), CatalogWriteProgress.complete())
        } ?: return
        val stored = receipt.check("sqlite.reopen-close") { requireNotNull(cache.readComplete(rom.sha256)) } ?: return
        val reopened = stored.catalog
        receipt.values["database"] = cache.fileFor(rom.sha256).absolutePath
        receipt.check("sqlite.whole-catalog-parity") { assertTrue("whole catalog differs", catalog == reopened) }
        receipt.check("sqlite.poi-parity") { assertEquals(catalog.localMaps.pois, reopened.localMaps.pois) }
        receipt.check("sqlite.ball-parity") { assertEquals(catalog.captureBallsById, reopened.captureBallsById) }
        receipt.check("sqlite.localized-name-parity") { assertEquals(catalog.localization, reopened.localization) }
        receipt.check("sqlite.sections-schema-integrity") {
            assertEquals(CatalogSchema.requiredSections + "language_overlay:${control["language"].asString}", stored.committedSections)
            factory.open(cache.fileFor(rom.sha256)).use { db ->
                val quick = db.query("PRAGMA quick_check") { it.string("quick_check") }
                val foreign = db.query("PRAGMA foreign_key_check") { it.string("table") }
                val schemas = db.query("SELECT parser_schema_version, schema_version FROM catalog_metadata WHERE id = 1") {
                    it.long("parser_schema_version") to it.long("schema_version")
                }
                write(receipt.root.resolve("sqlite-checks.json"), mapOf("quickCheck" to quick,
                    "foreignKeyCheck" to foreign, "schemas" to schemas, "sections" to stored.committedSections))
                assertEquals(listOf("ok"), quick)
                assertTrue(foreign.isEmpty())
                assertEquals(listOf(58L to 2L), schemas)
            }
        }
        receipt.values["boundaryNonExposure"] = mapOf("quantity" to "typed-reference/raw only; absent from LocalMapPoiItem and API",
            "collectionFlag" to "typed-reference/SQLite; absent from LocalMapPoiView API")
        receipt.check("api.cache-only") {
            val reparses = AtomicInteger()
            val done = CountDownLatch(1)
            val completion = AtomicReference<Result<Unit>?>()
            ProductionCompanionRuntime(initialSettings = CompanionSettings(knowledgeMode = KnowledgeMode.DISCOVERED),
                catalogRepository = cache, parseCatalogWithCancellation = { _, _, _, _ ->
                    reparses.incrementAndGet(); error("Western baseline cache bootstrap must not reparse")
                }).use { runtime ->
                runtime.load(LoadedRom("western-gen2-baseline", rom)) { result -> completion.set(result); done.countDown() }
                assertTrue("cache-only startup timed out", done.await(30, TimeUnit.SECONDS))
                requireNotNull(completion.get()).getOrThrow()
                val bootstrap = runtime.bootstrap()
                val state = runtime.stateView()
                receipt.values["apiReparses"] = reparses.get()
                write(receipt.root.resolve("api.json"), mapOf("bootstrap" to bootstrap, "pois" to state.localMapPois, "reparses" to reparses.get()))
                receipt.check("api.authority-projection") {
                    assertEquals(0, reparses.get())
                    assertTrue(bootstrap.state.catalogReady)
                    assertEquals("CACHE_REOPEN", bootstrap.state.loading.phase)
                    assertEquals("ROM_DEFAULT", bootstrap.language?.authority)
                    assertEquals(control["language"].asString, bootstrap.language?.defaultLanguage)
                    assertEquals(control["language"].asString, bootstrap.language?.activeLanguage)
                    assertEquals(overlay?.overlayVersion, bootstrap.language?.activeOverlayVersion)
                    assertEquals(listOf(control["language"].asString), bootstrap.language?.projections?.map { it.language })
                }
                val states = bootstrap.language?.projections?.singleOrNull()?.localizedCapabilities
                receipt.check("api.capabilities.inventory") {
                    assertEquals(LocalizedTextCapability.entries.map { it.name }.toSet(), requireNotNull(states).keys)
                    assertEquals(15, states.size)
                }
                for (capability in LocalizedTextCapability.entries) {
                    receipt.check("api.capability.$capability.status") {
                        assertEquals(requireNotNull(overlay).localizedCapabilities.getValue(capability).status.name,
                            requireNotNull(states).getValue(capability.name).status)
                    }
                    receipt.check("api.capability.$capability.counts") {
                        val expected = requireNotNull(overlay).localizedCapabilities.getValue(capability)
                        val actual = requireNotNull(states).getValue(capability.name)
                        assertEquals(expected.expectedRecords, actual.expectedRecords)
                        assertEquals(expected.coveredRecords, actual.coveredRecords)
                    }
                }
                receipt.check("api.whole-projection-parity") {
                    val api = requireNotNull(bootstrap.catalog)
                    assertEquals(rom.sha256, api.hash)
                    assertTrue(api == ApiViewBuilder.catalog(reopened))
                }
                val projected = state.localMapPois.filter { it.itemId != null }.associateBy { it.key }
                val numeric = reopened.localMaps.pois.filter { it.item?.itemId != null }.associateBy { it.key }
                receipt.check("api.exposed-numeric-parity") {
                    assertEquals(numeric.keys, projected.keys)
                    numeric.forEach { (key, poi) ->
                        val view = projected.getValue(key)
                        assertEquals(poi.localMapKey, view.localMapKey)
                        assertEquals(poi.baseAreaId, view.baseAreaId)
                        assertEquals(poi.tileX, view.tileX)
                        assertEquals(poi.tileY, view.tileY)
                        assertEquals(poi.destinationBaseAreaId, view.destinationBaseAreaId)
                        assertEquals(poi.item!!.itemId, view.itemId)
                    }
                }
                receipt.check("api.poi-item-name-parity") {
                    numeric.forEach { (key, poi) ->
                        assertEquals(reopened.defaultTextProjection().itemName(requireNotNull(poi.item?.itemId)),
                            projected.getValue(key).itemName)
                    }
                }
                fun apiItems() = (requireNotNull(bootstrap.catalog).balls.map { it.id to it.name } +
                    projected.values.map { requireNotNull(it.itemId) to it.itemName }).groupBy({ it.first }, { it.second })
                receipt.check("api.items.denominator") { assertEquals(requested, apiItems().keys) }
                receipt.check("api.items.name-parity") {
                    val apiItems = apiItems()
                    requested.forEach { id ->
                        assertEquals(setOf(reopened.defaultTextProjection().itemName(id)), apiItems.getValue(id).toSet())
                    }
                }
                receipt.check("api.zero-reparse") { assertEquals(0, reparses.get()) }
            }
        }
    }

    /** Independent diagnostic boundaries; the observer records failures and continues every check. */
    internal fun checkItemObservations(
        requested: Set<Int>,
        producerNames: Map<Int, CatalogField<String>>,
        states: Map<LocalizedTextCapability, LocalizedCapabilityState>?,
        overlayNames: Map<Int, CatalogField<String>>?,
        check: (String, () -> Unit) -> Unit,
    ) {
        check("capabilities.inventory") {
            assertEquals(LocalizedTextCapability.entries.toSet(), requireNotNull(states).keys)
            assertEquals(15, states.size)
        }
        for (capability in LocalizedTextCapability.entries) {
            check("capability.$capability.counts") {
                val state = requireNotNull(states?.get(capability))
                assertTrue(state.coveredRecords in 0..state.expectedRecords)
                assertEquals(state.expectedRecords - state.coveredRecords, state.incompleteRecords)
            }
        }
        check("items.producer-denominator") { assertEquals(requested, producerNames.keys) }
        check("items.capability-denominator") {
            assertEquals(requested.size, requireNotNull(states?.get(LocalizedTextCapability.ITEM_NAMES)).expectedRecords)
        }
        check("items.capability-covered-count") {
            assertEquals(requireNotNull(overlayNames).size, requireNotNull(states?.get(LocalizedTextCapability.ITEM_NAMES)).coveredRecords)
        }
        check("items.overlay-available-fields") {
            assertTrue(requireNotNull(overlayNames).values.all { it.status == CapabilityStatus.AVAILABLE && !it.value.isNullOrBlank() })
        }
        check("items.overlay-sparse-keys") {
            assertEquals(availableItemNames(producerNames).keys, requireNotNull(overlayNames).keys)
        }
        check("items.available-name-parity") {
            assertEquals(availableItemNames(producerNames), requireNotNull(overlayNames).mapValues { it.value.value })
        }
    }

    internal fun missingRequestedItemIds(requested: Set<Int>, names: Map<Int, CatalogField<String>>): Set<Int> =
        requested - availableItemNames(names).keys

    private fun availableItemNames(names: Map<Int, CatalogField<String>>): Map<Int, String> = names.mapNotNull { (id, field) ->
        field.value?.takeIf { field.status == CapabilityStatus.AVAILABLE && it.isNotBlank() }?.let { id to it }
    }.toMap()

    private fun writeWitnesses(root: Path, rom: RomImage, refs: List<Gen2ItemReference>) {
        require(refs.size <= 32768)
        Files.newBufferedWriter(root.resolve("reference-witnesses.tsv")).use { writer ->
            writer.appendLine("PoiKey\tRole\tOffset\tHex")
            fun witness(ref: Gen2ItemReference, role: String, at: Int, count: Int) {
                require(at >= 0 && at.toLong() + count <= rom.size)
                writer.appendLine("${ref.poiKey}\t$role\t$at\t" + (0 until count).joinToString("") { "%02x".format(rom.u8(at + it)) })
            }
            refs.forEach { ref ->
                val hidden = ref.kind == Gen2ItemReference.Kind.HIDDEN_EVENT
                witness(ref, "groupPointer", requireNotNull(ref.mapGroupTable) + ((ref.baseAreaId ushr 8) - 1) * 2, 2)
                witness(ref, "mapHeader", requireNotNull(ref.mapHeader), 9)
                witness(ref, "attributes", ref.attributes, 11)
                witness(ref, "eventRow", ref.eventRow, if (hidden) 5 else 13)
                witness(ref, "itemData", ref.operandOffset - if (hidden) 2 else 0, if (hidden) 3 else 2)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> property(value: Any, name: String): T = value.javaClass.getMethod("get$name").invoke(value) as T
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun write(path: Path, value: Any?) = Files.write(path, json.toJson(value).toByteArray(Charsets.UTF_8))

    private class Receipt(val root: Path, val control: JsonObject) {
        val errors = linkedMapOf<String, String>()
        val checks = linkedMapOf<String, String>()
        val values = linkedMapOf<String, Any?>("control" to control, "terminal" to "NOT_STARTED", "semanticAcceptance" to false,
            "checks" to checks, "errors" to errors)
        fun save() { write(root.resolve("receipt.json"), values) }
        fun failure(stage: String, failure: Throwable) {
            checks[stage] = "FAIL"
            errors[stage] = failure.stackTraceToString()
            save()
        }
        fun <T> check(stage: String, block: () -> T): T? {
            values["lastStage"] = stage
            save()
            return try { block().also { checks[stage] = "PASS"; save() } }
            catch (failure: AssertionError) { this.failure(stage, failure); null }
            catch (failure: Exception) { this.failure(stage, failure); null }
        }
    }
}
