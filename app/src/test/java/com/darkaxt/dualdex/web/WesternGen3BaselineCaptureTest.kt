package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.companion.api.*
import com.enrpau.dualscreendex.parser.catalog.*
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.*
import com.enrpau.dualscreendex.parser.model.*
import com.google.gson.*
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test

/** Fabricated inputs only; neither pinned metadata nor an original control is opened here. */
class WesternGen3BaselineCaptureTest {
    @Test fun invalidOptInPreventsAllMetadataAndInputCallbacks() {
        for (optIn in listOf(null, "", "0", "true", " 1", "1 ")) {
            var reads = 0; var inputs = 0
            assertThrows(IllegalArgumentException::class.java) {
                WesternGen3BaselineCapture.preflight(optIn, { reads++; byteArrayOf() }, { reads++; byteArrayOf() }) { inputs++ }
            }
            assertEquals(0, reads); assertEquals(0, inputs)
        }
    }

    @Test fun wrongEmptyAndOversizedPinsRejectBeforeParsingOrInput() {
        for (bytes in listOf(byteArrayOf(), "{}".toByteArray(), ByteArray(4_194_305))) {
            var inventoryReads = 0; var inputs = 0
            assertThrows(IllegalArgumentException::class.java) {
                WesternGen3BaselineCapture.preflight("1", { bytes }, { inventoryReads++; bytes }) { inputs++ }
            }
            assertEquals(0, inventoryReads); assertEquals(0, inputs)
            for (pin in listOf(WesternGen3BaselineCapture.manifestSha, WesternGen3BaselineCapture.inventorySha)) {
                assertThrows(IllegalArgumentException::class.java) { WesternGen3BaselineCapture.requirePin(bytes, pin) }
            }
        }
    }

    @Test fun fabricatedMetadataDecoderAcceptsArrayManifestAndRejectsMalformedShapes() {
        val (manifest, inventory) = metadata()
        val manifestBytes = WesternGen3BaselineCapture.json.toJson(manifest).toByteArray(Charsets.UTF_8)
        val inventoryBytes = WesternGen3BaselineCapture.json.toJson(mapOf("cells" to inventory)).toByteArray(Charsets.UTF_8)
        val decoded = WesternGen3BaselineCapture.decodeMetadata(manifestBytes, inventoryBytes) { it }
        assertEquals(15, decoded.size)
        val variants = listOf("{}".toByteArray() to inventoryBytes, manifestBytes to "[]".toByteArray(),
            (manifestBytes + " {}".toByteArray()) to inventoryBytes,
            (manifestBytes + ",\"extra\":[]".toByteArray()) to inventoryBytes,
            manifestBytes to "{\"cells\":[],\"cells\":[]}".toByteArray(),
            byteArrayOf(0xc3.toByte(), 0x28) to inventoryBytes, byteArrayOf() to inventoryBytes)
        variants.forEach { (m, i) ->
            var inputs = 0
            assertTrue(runCatching { WesternGen3BaselineCapture.decodeMetadata(m, i) { inputs++ } }.isFailure)
            assertEquals(0, inputs)
        }
    }

    @Test fun exactFifteenControlBindingKeepsHistoricalCountsSeparate() {
        val (manifest, inventory) = metadata()
        val selected = WesternGen3BaselineCapture.bindMetadata(manifest, inventory) { it }
        assertEquals(15, selected.size)
        assertEquals(5, selected.map { it.identity["language"].asString }.toSet().size)
        assertEquals(3, selected.map { it.identity["family"].asString }.toSet().size)
        assertEquals(WesternGen3BaselineCapture.controlHashes.values.toSet(), selected.map { it.identity["sha256"].asString }.toSet())
        assertTrue(selected.all { it.identity["size"].asLong == 16_777_216L })
        // Deliberately NOT native/historical101/104/127. Binding never promotes counts to current expectations.
        assertTrue(selected.all { it.historical.getAsJsonObject("localizedCapabilities").getAsJsonObject("ITEM_NAMES")["expectedRecords"].asInt == 7 })
        assertTrue(selected.all { !it.identity.has("currentExpectedRecords") })
    }

    @Test fun missingDuplicateExtraAndMisboundMetadataNeverReachInput() {
        val mutations: List<(MutableList<JsonObject>, MutableList<JsonObject>) -> Unit> = listOf(
            { m, _ -> m.removeAt(0) }, { _, i -> i.removeAt(0) },
            { m, _ -> m.add(m[0].deepCopy()) }, { _, i -> i[1] = i[0].deepCopy() },
            { m, _ -> m[1] = m[0].deepCopy() },
            { m, _ -> m[0].addProperty("sha256", "f".repeat(64)) },
            { _, i -> i[0].addProperty("romSha256", "f".repeat(64)) },
            { m, i -> m[0].addProperty("sha256", "f".repeat(64)); i[0].addProperty("romSha256", "f".repeat(64)) },
            { m, _ -> m[0].addProperty("size", 1_048_576) },
            { m, _ -> m[0].addProperty("size", 16_777_216.5) },
            { m, _ -> m[0].addProperty("file", " ") },
            { m, _ -> m[0].addProperty("language", "ja") },
            { _, i -> i[0].addProperty("family", "EMERALD") },
            { m, _ -> m[0].remove("code") },
            { m, _ -> m[0].addProperty("code", "AXVJ") },
            { _, i -> i[0].remove("localizedCapabilities") },
            { _, i -> i[0].getAsJsonObject("localizedCapabilities").remove("ITEM_NAMES") },
        )
        mutations.forEachIndexed { index, mutation ->
            val (m, i) = metadata(); mutation(m, i); var inputs = 0
            assertTrue("mutation $index", runCatching { WesternGen3BaselineCapture.bindMetadata(m, i) { inputs++ } }.isFailure)
            assertEquals(0, inputs)
        }
    }

    @Test fun outputCollisionRejectsBeforeAnyInputCallback() {
        val parent = temp("collision")
        val root = WesternGen3BaselineCapture.createOutput(parent)
        Files.write(root.resolve("sentinel"), "retain".toByteArray(Charsets.UTF_8))
        var inputs = 0
        assertTrue(runCatching { WesternGen3BaselineCapture.createOutput(parent); inputs++ }.isFailure)
        assertEquals(0, inputs); assertEquals("retain", Files.readAllBytes(root.resolve("sentinel")).toString(Charsets.UTF_8))
    }

    @Test fun uninitializedRouteInspectionNeverInvokesInitializer() {
        var initializations = 0
        val delegate = lazy { initializations++; FakeResolver(linkedMapOf()) }
        assertThrows(IllegalArgumentException::class.java) { WesternGen3BaselineCapture.inspectOriginal(delegate) }
        assertEquals(0, initializations); assertFalse(delegate.isInitialized())
    }

    @Test fun allOriginalRouteAndAuthorityStatesRemainDistinct() {
        val routes = listOf(GbaItemPublishedRoute.NotEvaluated, GbaItemPublishedRoute.NotInvoked,
            GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Absent), GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Ambiguous),
            GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Nominated(100)))
        val map = routes.associateWith { GbaItemNameAuthority.Unavailable("synthetic $it") as GbaItemNameAuthority }.toMutableMap()
        val delegate = lazyOf(FakeResolver(map))
        val snapshot = WesternGen3BaselineCapture.inspectOriginal(delegate)
        assertEquals(listOf("NotEvaluated", "NotInvoked", "InvokedAbsent", "InvokedAmbiguous", "InvokedNominated"), snapshot.map { it.routeState })
        routes.forEach { route -> assertSame(map.getValue(route), WesternGen3BaselineCapture.bindOriginal(snapshot, map.getValue(route)).authority) }
        val compiled = GbaItemNameAuthority.Available(100, 44, 349, 14, 175, GbaItemNameProvenance.COMPILED_CONSUMER)
        val published = compiled.copy(provenance = GbaItemNameProvenance.PUBLISHED_ROOT)
        val available = WesternGen3BaselineCapture.inspectOriginal(lazyOf(FakeResolver(linkedMapOf(
            GbaItemPublishedRoute.NotInvoked to compiled, routes.last() to published))))
        assertEquals(listOf(compiled, published), available.map { it.authority })
    }

    @Test fun equalButDistinctAuthorityCannotBindOriginalRoute() {
        val original = GbaItemNameAuthority.Unavailable("same")
        val equal = GbaItemNameAuthority.Unavailable("same")
        assertEquals(original, equal); assertNotSame(original, equal)
        val snapshot = WesternGen3BaselineCapture.inspectOriginal(lazyOf(FakeResolver(linkedMapOf(GbaItemPublishedRoute.NotInvoked to original))))
        assertThrows(IllegalArgumentException::class.java) { WesternGen3BaselineCapture.bindOriginal(snapshot, equal) }
        assertThrows(IllegalArgumentException::class.java) { WesternGen3BaselineCapture.bindOriginal(emptyList(), original) }
    }

    @Test fun duplicateAuthorityIdentityMatchesFailClosed() {
        val authority = GbaItemNameAuthority.Unavailable("same instance")
        val snapshot = WesternGen3BaselineCapture.inspectOriginal(lazyOf(FakeResolver(linkedMapOf(
            GbaItemPublishedRoute.NotEvaluated to authority, GbaItemPublishedRoute.NotInvoked to authority))))
        assertThrows(IllegalArgumentException::class.java) { WesternGen3BaselineCapture.bindOriginal(snapshot, authority) }
    }

    @Test fun laterMapMutationCannotRewriteFrozenOriginalSnapshot() {
        val authority = GbaItemNameAuthority.Unavailable("original")
        val map = linkedMapOf<GbaItemPublishedRoute, GbaItemNameAuthority>(GbaItemPublishedRoute.NotInvoked to authority)
        val delegate = lazyOf(FakeResolver(map))
        val snapshot = WesternGen3BaselineCapture.inspectOriginal(delegate)
        val serialized = WesternGen3BaselineCapture.json.toJson(snapshot)
        map.clear(); map[GbaItemPublishedRoute.NotEvaluated] = GbaItemNameAuthority.Unavailable("later")
        assertEquals(serialized, WesternGen3BaselineCapture.json.toJson(snapshot))
        assertSame(authority, WesternGen3BaselineCapture.bindOriginal(snapshot, authority).authority)
        assertThrows(AssertionError::class.java) { WesternGen3BaselineCapture.checkFrozenOriginal(snapshot, WesternGen3BaselineCapture.inspectOriginal(delegate)) }
    }

    @Test fun producerWrapperRetainsAllFieldsAndRejectsSecondInvocation() {
        val f = fixture(); var calls = 0
        val observer = WesternGen3BaselineCapture.ProducerObservation { _, ids -> calls++; assertEquals(setOf(0, 175, 65535), ids); f.fields }
        observer.invoke(null, setOf(0, 175, 65535))
        assertEquals(f.fields, observer.fields); assertEquals(setOf(0, 175, 65535), observer.requested)
        assertEquals(listOf("synthetic dynamic name"), observer.fields.getValue(175).reasons)
        assertThrows(IllegalArgumentException::class.java) { observer.invoke(null, setOf(0)) }
        assertEquals(1, calls); assertEquals(2, observer.attempts); assertEquals(f.fields, observer.fields)
    }

    @Test fun producerThrowRetainsAttemptAndRequestedDomain() {
        val observer = WesternGen3BaselineCapture.ProducerObservation { _, _ -> error("synthetic producer failure") }
        assertThrows(IllegalStateException::class.java) { observer.invoke(null, setOf(0, 65535)) }
        assertEquals(1, observer.attempts); assertEquals(setOf(0, 65535), observer.requested)
        assertTrue(observer.fields.isEmpty()); assertFalse(observer.completed)
    }

    @Test fun zeroAndU16DomainsPreserveCompleteProducerSparseOverlayAndDenominators() {
        val f = fixture()
        WesternGen3BaselineCapture.checkCatalogItems(f.catalog, f.fields.keys, f.fields) { _, block -> block() }
        assertEquals(setOf(0, 175, 65535), WesternGen3BaselineCapture.numericIds(f.catalog))
        assertEquals(setOf(0, 65535), f.catalog.defaultLocalizedText()!!.itemNames.keys)
    }

    @Test fun unavailableNamesAreDiagnosticObservationsNotAdapterFailures() {
        val f = fixture(allUnavailable = true)
        WesternGen3BaselineCapture.checkCatalogItems(f.catalog, f.fields.keys, f.fields) { _, block -> block() }
        assertEquals(3, f.catalog.defaultLocalizedText()!!.localizedCapabilities.getValue(LocalizedTextCapability.ITEM_NAMES).expectedRecords)
        assertTrue(f.catalog.defaultLocalizedText()!!.itemNames.isEmpty())
    }

    @Test fun invalidDomainMissingExtraProducerAndCollapsedDenominatorsReject() {
        val f = fixture()
        val variants = listOf((f.fields.keys - 175) to f.fields, (f.fields.keys + -1) to f.fields,
            (f.fields.keys + 65536) to f.fields, f.fields.keys to (f.fields - 175),
            f.fields.keys to (f.fields + (123 to CatalogField.notFound<String>("extra"))),
            f.fields.keys to (f.fields + (175 to CatalogField.available("not in overlay"))))
        variants.forEach { (requested, fields) ->
            assertTrue(outcomes { check -> WesternGen3BaselineCapture.checkCatalogItems(f.catalog, requested, fields, check) }.any { it.isFailure })
        }
        val states = f.catalog.defaultLocalizedText()!!.localizedCapabilities + (LocalizedTextCapability.ITEM_NAMES to LocalizedCapabilityState.available(2))
        assertTrue(outcomes { check -> WesternGen1BaselineCapture.checkItemObservations(f.fields.keys, f.fields, states,
            f.catalog.defaultLocalizedText()!!.itemNames, check) }.any { it.isFailure })
    }

    @Test fun apiLanguageRejectsEveryManifestProjectionAndMissingCapabilityMutation() {
        val f = fixture(); val valid = languageView(f.catalog); val p = valid.projections.single()
        WesternGen1SemanticCapture.checkApiLanguage(f.catalog, valid) { _, block -> block() }
        val mutants = listOf(valid.copy(manifestStatus = "WRONG"), valid.copy(defaultLanguage = "fr"), valid.copy(activeLanguage = "fr"),
            valid.copy(authority = "SAVE"), valid.copy(activeOverlayVersion = 99), valid.copy(projections = emptyList()),
            valid.copy(projections = listOf(p, p))) + listOf(p.copy(language = "fr"), p.copy(status = "WRONG"), p.copy(codecId = "WRONG"),
            p.copy(codecVersion = 99), p.copy(overlayVersion = 99)).map { valid.copy(projections = listOf(it)) } +
            p.localizedCapabilities.keys.map { key -> valid.copy(projections = listOf(p.copy(localizedCapabilities = p.localizedCapabilities - key))) }
        mutants.forEachIndexed { index, actual ->
            assertTrue("mutation $index", outcomes { check -> WesternGen1SemanticCapture.checkApiLanguage(f.catalog, actual, check) }.any { it.isFailure })
        }
    }

    @Test fun apiLanguageRejectsEveryExposedCapabilityFieldMutation() {
        val f = fixture(); val valid = languageView(f.catalog); val p = valid.projections.single()
        for ((key, state) in p.localizedCapabilities) {
            val mutants = listOf(state.copy(status = "WRONG"), state.copy(confidence = 0.25), state.copy(coveredRecords = 99),
                state.copy(expectedRecords = 99), state.copy(incompleteRecords = 99), state.copy(reviewStatus = "WRONG"),
                state.copy(validatorReviewRecommended = !state.validatorReviewRecommended))
            mutants.forEach { mutated ->
                val actual = valid.copy(projections = listOf(p.copy(localizedCapabilities = p.localizedCapabilities + (key to mutated))))
                assertTrue(outcomes { check -> WesternGen1SemanticCapture.checkApiLanguage(f.catalog, actual, check) }.any { it.isFailure })
            }
        }
    }

    @Test fun apiItemsRejectMissingDuplicateWrongNumericAndWrongNameOccurrences() {
        val f = fixture(); val catalog = ApiViewBuilder.catalog(f.catalog); val pois = poiViews(f.catalog)
        WesternGen3BaselineCapture.checkApiItems(f.catalog, catalog, pois) { _, block -> block() }
        val variants = listOf(pois.drop(1), pois + pois.first(), pois.mapIndexed { i, p -> if (i == 0) p.copy(itemId = 1) else p },
            pois.mapIndexed { i, p -> if (i == 1) p.copy(tileX = 99) else p },
            pois.mapIndexed { i, p -> if (i == 2) p.copy(itemName = "WRONG") else p })
        variants.forEach { actual -> assertTrue(outcomes { check -> WesternGen3BaselineCapture.checkApiItems(f.catalog, catalog, actual, check) }.any { it.isFailure }) }
        val balls = listOf(catalog.copy(balls = emptyList()), catalog.copy(balls = catalog.balls + catalog.balls.first()),
            catalog.copy(balls = catalog.balls.map { it.copy(name = "WRONG") }))
        balls.forEach { actual -> assertTrue(outcomes { check -> WesternGen3BaselineCapture.checkApiItems(f.catalog, actual, pois, check) }.any { it.isFailure }) }
    }

    @Test fun realSyntheticSqliteAndCacheOnlyRuntimeKeepZeroReparses() {
        for (missing in listOf(false, true)) {
            val f = fixture(missing)
            val receipt = WesternGen3BaselineCapture.Receipt(temp("sqlite"), JsonObject())
            WesternGen3BaselineCapture.persistAndCheck(receipt, JdbcTestCatalogDatabaseFactory, f.rom, f.catalog, f.fields.keys, f.fields)
            assertEquals(receipt.errors.toString(), emptyMap<String, String>(), receipt.errors)
            assertEquals(0, receipt.values["apiReparses"])
            assertTrue(receipt.completed.containsAll(setOf("sqlite", "api")))
            assertEquals("PASS", receipt.checks["sqlite.schemas"])
            assertEquals("PASS", receipt.checks["sqlite.whole-catalog-parity"])
            assertEquals("PASS", receipt.checks["api.zero-reparse"])
            receipt.finish(); assertEquals(false, receipt.values["semanticAcceptance"]); assertEquals(0, receipt.values["acceptedNames"])
        }
    }

    @Test fun historicalChangesAreRecordedWithoutEqualityOrCurrentCountAssumptions() {
        val f = fixture(); val historical = metadata().second.first()
        val comparison = WesternGen3BaselineCapture.historicalComparison(historical, f.catalog)
        assertEquals(49, comparison["historicalParserSchema"])
        assertEquals(60, comparison["currentParserSchema"])
        assertEquals("PENDING_CLASSIFICATION_NOT_EQUALITY_GATE", comparison["status"])
        assertEquals(false, comparison["nonItemEqualityEstablished"])
        assertTrue(WesternGen3BaselineCapture.json.toJson(comparison).contains("expectedRecords"))
        assertEquals(7, historical.getAsJsonObject("localizedCapabilities").getAsJsonObject("ITEM_NAMES")["expectedRecords"].asInt)
    }

    @Test fun terminalRequiresEveryPathAndNeverGrantsSemanticAcceptance() {
        for (missing in WesternGen3BaselineCapture.requiredPaths) {
            val receipt = WesternGen3BaselineCapture.Receipt(temp("terminal-missing"), JsonObject())
            receipt.completed += WesternGen3BaselineCapture.requiredPaths - missing
            receipt.finish(); assertEquals("DIAGNOSTIC_CAPTURE_FAILED", receipt.values["terminal"])
            assertEquals(false, receipt.values["semanticAcceptance"]); assertEquals(0, receipt.values["acceptedNames"])
        }
        val complete = WesternGen3BaselineCapture.Receipt(temp("terminal-complete"), JsonObject())
        complete.completed += WesternGen3BaselineCapture.requiredPaths
        complete.finish(); assertEquals("DIAGNOSTIC_CAPTURE_COMPLETE", complete.values["terminal"])
        assertEquals(false, complete.values["semanticAcceptance"]); assertEquals(0, complete.values["acceptedNames"])
    }

    @Test fun earlierFailuresAndDuplicateChecksCannotBeOverwrittenByLaterSuccess() {
        val receipt = WesternGen3BaselineCapture.Receipt(temp("failures"), JsonObject())
        receipt.check("producer") { error("first failure") }
        receipt.check("sqlite") { assertEquals(1, 2) }
        receipt.check("api") { Unit }
        assertThrows(IllegalArgumentException::class.java) { receipt.check("producer") { Unit } }
        receipt.completed += WesternGen3BaselineCapture.requiredPaths
        receipt.finish()
        assertEquals(setOf("producer", "sqlite"), receipt.errors.keys)
        assertEquals("PASS", receipt.checks["api"]); assertEquals("FAIL", receipt.checks["producer"])
        assertEquals("DIAGNOSTIC_CAPTURE_FAILED", receipt.values["terminal"])
        val saved = JsonParser.parseString(Files.readAllBytes(receipt.root.resolve("receipt.json")).toString(Charsets.UTF_8)).asJsonObject
        assertEquals(setOf("producer", "sqlite"), saved.getAsJsonObject("errors").keySet())
    }

    private class FakeResolver(private val originalResults: MutableMap<GbaItemPublishedRoute, GbaItemNameAuthority>)
    private fun temp(label: String): Path {
        val parent = System.getenv("DUALDEX_TEST_TEMP_ROOT")?.takeIf(String::isNotBlank)?.let(Path::of)
        if (parent != null) Files.createDirectories(parent)
        return if (parent == null) Files.createTempDirectory("gen3-$label-") else Files.createTempDirectory(parent, "gen3-$label-")
    }
    private fun outcomes(run: ((String, () -> Unit) -> Unit) -> Unit): List<Result<Unit>> {
        val results = linkedMapOf<String, Result<Unit>>()
        run { key, block -> require(key !in results); results[key] = runCatching(block) }
        return results.values.toList()
    }
    private data class Fixture(val rom: RomImage, val catalog: ParsedCatalog, val fields: Map<Int, CatalogField<String>>)
    private fun fixture(allUnavailable: Boolean = false): Fixture {
        val rom = RomImage(ByteArray(512) { (it * 11).toByte() }); val language = LanguageTag.ENGLISH
        val manifest = RomLanguageManifest(language, listOf(RomLanguageProjection(language, "gba-gen3-en", 1,
            LocalizedTableLayout(), emptyList(), LanguageResolutionStatus.RESOLVED)), LanguageResolutionStatus.RESOLVED)
        val fields = linkedMapOf(0 to CatalogField.available("ZERO"), 175 to CatalogField.notFound<String>("synthetic dynamic name"),
            65535 to CatalogField.available("MAXIMUM"))
        if (allUnavailable) fields.keys.toList().forEach { fields[it] = CatalogField.notFound("synthetic unavailable $it") }
        val available = fields.filterValues { it.status == CapabilityStatus.AVAILABLE }
        val states = LocalizedTextCapability.entries.associateWith {
            when (it) {
                LocalizedTextCapability.ITEM_NAMES -> if (allUnavailable) LocalizedCapabilityState.notFound("synthetic", 3)
                    else LocalizedCapabilityState(CapabilityStatus.PARTIAL, 1.0, 2, 3)
                LocalizedTextCapability.LOCAL_MAP_NAMES -> LocalizedCapabilityState.notFound("synthetic", 1)
                LocalizedTextCapability.POI_TEXT -> LocalizedCapabilityState.notFound("synthetic", 3)
                else -> LocalizedCapabilityState.notApplicable("synthetic", 0)
            }
        }
        val catalog = ParsedCatalog(romSha256 = rom.sha256, romCrc32 = rom.crc32, family = EngineFamily.RUBY_SAPPHIRE, platform = Platform.GBA,
            captureBallsById = mapOf(0 to CaptureBallRecord(0, CatalogField.notApplicable("overlay"), CatalogField.notApplicable("synthetic"))),
            localMaps = LocalMapCatalog(maps = listOf(LocalMap("synthetic", null, 1, 16, 16, 1, 1, "synthetic/map")),
                assets = mapOf("synthetic/map" to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))),
                pois = listOf(175, 65535, 65535).mapIndexed { index, id -> LocalMapPoi("synthetic/hidden/$index", "synthetic", 1, 0, 0,
                    LocalMapPoiKind.HIDDEN_ITEM, organicVisibility = LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE,
                    item = LocalMapPoiItem(id, collectionFlagId = index)) }),
            localization = CatalogLocalization(manifest, mapOf(language to CatalogLanguageOverlay(language, 1, states, itemNames = available))))
        return Fixture(rom, catalog, fields)
    }
    private fun languageView(catalog: ParsedCatalog): LanguageBootstrapView {
        val states = catalog.defaultLocalizedText()!!.localizedCapabilities.mapKeys { it.key.name }.mapValues { (_, s) ->
            LocalizedCapabilityView(s.status.name, s.confidence, s.coveredRecords, s.expectedRecords, s.incompleteRecords, s.reviewStatus.name, s.validatorReviewRecommended) }
        return LanguageBootstrapView("RESOLVED", "en", "en", "ROM_DEFAULT", 1, listOf(LanguageProjectionView("en", "RESOLVED", "gba-gen3-en", 1, 1, states)))
    }
    private fun poiViews(catalog: ParsedCatalog) = catalog.localMaps.pois.map { p -> LocalMapPoiView(p.key, p.localMapKey, p.baseAreaId,
        p.tileX, p.tileY, "ITEM", "UNKNOWN", null, null, p.item!!.itemId, catalog.defaultTextProjection().poiItemName(p.key, p.item!!.itemId), p.destinationBaseAreaId) }
    private fun metadata(): Pair<MutableList<JsonObject>, MutableList<JsonObject>> {
        val manifest = mutableListOf<JsonObject>(); val inventory = mutableListOf<JsonObject>()
        val cells = WesternGen3BaselineCapture.controlHashes.toList() + (0 until 20).map { ("other" to "OTHER_$it") to "%064x".format(it + 1) }
        cells.forEach { (cell, hash) ->
            manifest += JsonObject().apply {
                addProperty("language", cell.first); addProperty("family", cell.second); addProperty("sha256", hash)
                addProperty("size", 16_777_216); addProperty("file", "synthetic-not-a-rom-$hash")
                addProperty("code", WesternGen3BaselineCapture.controlCodes[cell] ?: "NONE")
            }
            inventory += JsonObject().apply {
                addProperty("language", cell.first); addProperty("family", cell.second); addProperty("romSha256", hash)
                add("localizedCapabilities", JsonObject().apply { LocalizedTextCapability.entries.forEach { capability ->
                    add(capability.name, JsonObject().apply { addProperty("status", "NOT_FOUND"); addProperty("confidence", 0.0)
                        addProperty("coveredRecords", 0); addProperty("expectedRecords", 7); addProperty("reviewStatus", "NONE"); addProperty("validatorReviewRecommended", false) })
                } })
            }
        }
        return manifest to inventory
    }
}
