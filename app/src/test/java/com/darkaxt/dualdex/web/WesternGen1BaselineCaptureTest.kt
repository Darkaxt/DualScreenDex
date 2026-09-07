package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.LocalizedCapabilityState
import com.enrpau.dualscreendex.parser.catalog.LocalizedTextCapability
import com.enrpau.dualscreendex.parser.analysis.Gen1ItemReference
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoi
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiItem
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.GbItemNameAuthority
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test

class WesternGen1BaselineCaptureTest {
    @Test
    fun sparseOverlayRetainsRequestedDenominatorsAndEveryCheck() {
        val names = mapOf(1 to CatalogField.available("SYNTHETIC"),
            5 to CatalogField.notFound<String>("synthetic unavailable; not a semantic waiver"))
        val states = states(2, 1)
        val outcomes = outcomes(setOf(1, 5), names, states, mapOf(1 to CatalogField.available("SYNTHETIC")))
        assertEquals(22, outcomes.size)
        assertEquals(emptySet<String>(), outcomes.filterValues { it.isFailure }.keys)
        assertEquals(2, states.getValue(LocalizedTextCapability.ITEM_NAMES).expectedRecords)
    }

    @Test
    fun originalObserverRetainsNormalSessionAndImmutableAuthority() {
        val rom = RomImage(ByteArray(0x200))
        val (context, session) = WesternGen1BaselineCapture.observe(rom)
        assertEquals("CatalogAnalysisContext", context.javaClass.simpleName)
        assertSame(rom, session.rom)
        assertTrue(session.gen1ItemReferences.isEmpty())
        val frozen = session.gen1ItemNameAuthority
        assertTrue(frozen is GbItemNameAuthority.Unavailable)
        assertSame(frozen, session.gen1ItemNameAuthority)
    }

    @Test
    fun emptyUnavailableAndAllAvailableDomainsNeverBecomeSemanticAcceptance() {
        for (requested in listOf(emptySet(), setOf(5))) {
            val names = requested.associateWith { CatalogField.notFound<String>("synthetic") }
            assertTrue(outcomes(requested, names, states(requested.size, 0), emptyMap()).values.all { it.isSuccess })
            assertEquals(requested, WesternGen1BaselineCapture.missingRequestedItemIds(requested, names))
        }
        val root = newRoot("receipt-")
        val receipt = WesternGen1BaselineCapture.Receipt(root, JsonObject())
        val names = mapOf(5 to CatalogField.available("OBSERVED ONLY"))
        WesternGen1BaselineCapture.checkItemObservations(setOf(5), names, states(1, 1), names) { stage, assertion ->
            receipt.check(stage, assertion)
        }
        receipt.finish()
        assertEquals("DIAGNOSTIC_CAPTURE_COMPLETE", receipt.values["terminal"])
        assertEquals(false, receipt.values["semanticAcceptance"])
        assertEquals("NOT_ACCEPTED", receipt.values["requiredSemanticCompletion"])
        val saved = JsonParser.parseString(Files.readAllBytes(root.resolve("receipt.json")).toString(Charsets.UTF_8)).asJsonObject
        assertFalse(saved["semanticAcceptance"].asBoolean)
        assertEquals("NOT_ACCEPTED", saved["requiredSemanticCompletion"].asString)
    }

    @Test
    fun denominatorAndInventoryFailuresContinueThroughLaterChecks() {
        val names = mapOf(1 to CatalogField.available("SYNTHETIC"))
        val results = outcomes(setOf(1, 5), names, states(1, 1) - LocalizedTextCapability.SPECIES_NAMES,
            mapOf(1 to CatalogField.available("WRONG SYNTHETIC")))
        assertEquals(22, results.size)
        assertEquals(setOf("capabilities.inventory", "capability.SPECIES_NAMES.counts", "items.producer-denominator",
            "items.capability-denominator", "items.available-name-parity"), results.filterValues { it.isFailure }.keys)
        assertTrue(results.getValue("capability.POI_TEXT.counts").isSuccess)
        val root = newRoot("failure-")
        val receipt = WesternGen1BaselineCapture.Receipt(root, JsonObject())
        receipt.check("materialize.synthetic-failure") { assertEquals("expected", "wrong") }
        receipt.check("sqlite.synthetic-continuation") { assertTrue(true) }
        receipt.check("api.synthetic-continuation") { assertTrue(true) }
        receipt.finish()
        assertEquals("FAIL", receipt.checks["materialize.synthetic-failure"])
        assertEquals("PASS", receipt.checks["sqlite.synthetic-continuation"])
        assertEquals("PASS", receipt.checks["api.synthetic-continuation"])
        assertEquals("BASELINE_INTEGRITY_BROKEN", receipt.values["terminal"])
        assertEquals(false, receipt.values["semanticAcceptance"])
    }

    @Test
    fun sparseOverlayRejectsUnexpectedBlankUnavailableAndAmbiguousFields() {
        val names = mapOf(1 to CatalogField.available("SYNTHETIC"),
            5 to CatalogField<String>(com.enrpau.dualscreendex.parser.model.CapabilityStatus.AMBIGUOUS, reasons = listOf("synthetic")))
        val proper = mapOf(1 to CatalogField.available("SYNTHETIC"))
        assertTrue(outcomes(setOf(1, 5), names, states(2, 1), proper).values.all { it.isSuccess })
        for (extra in listOf(5 to CatalogField.available("UNAUTHORIZED"), 7 to CatalogField.available("UNREQUESTED"),
            5 to CatalogField.available(" "), 5 to CatalogField.notFound<String>("synthetic"))) {
            val results = outcomes(setOf(1, 5), names, states(2, 2), proper + extra)
            assertTrue(results.getValue("items.overlay-sparse-keys").isFailure)
            assertTrue(results.getValue("items.available-name-parity").isFailure)
        }
        assertEquals(22, outcomes(setOf(1, 5), names, null, null).size)
    }

    @Test
    fun invalidOptInAndManifestStopBeforeFurtherInputAccess() {
        for (optIn in listOf(null, "", " ", "0", "true", " 1", "1 ")) {
            var reads = 0
            assertThrows(IllegalArgumentException::class.java) {
                WesternGen1BaselineCapture.preflight(optIn, { reads++; byteArrayOf() }, { reads++; byteArrayOf() })
            }
            assertEquals(0, reads)
        }
        var inventoryReads = 0
        assertThrows(IllegalArgumentException::class.java) {
            WesternGen1BaselineCapture.preflight("1", { "[]".toByteArray() }, { inventoryReads++; byteArrayOf() })
        }
        assertEquals(0, inventoryReads)
    }

    @Test
    fun metadataRequiresAllOriginalIdentitiesSizesAndPublicBindings() {
        val (manifest, inventory) = inventory()
        assertEquals(10, WesternGen1BaselineCapture.selectControls(manifest, inventory).size)
        val mutations: List<(MutableList<JsonObject>, MutableList<JsonObject>) -> Unit> = listOf(
            { m, _ -> m.removeAt(0) },
            { m, _ -> m[0].addProperty("size", 2097152) },
            { m, _ -> m[0].addProperty("sha256", "f".repeat(64)) },
            { m, _ -> m[1] = m[0].deepCopy() },
            { m, _ -> m[0].addProperty("language", "ja") },
            { m, _ -> m[0].addProperty("family", "GOLD_SILVER") },
            { m, _ -> m[0].addProperty("file", " ") },
            { _, i -> i[0].addProperty("romSha256", "f".repeat(64)) },
            { _, i -> i[1] = i[0].deepCopy() },
        )
        mutations.forEachIndexed { index, mutation ->
            val (m, i) = inventory()
            mutation(m, i)
            assertTrue("mutation $index", runCatching { WesternGen1BaselineCapture.selectControls(m, i) }.isFailure)
        }
        // Matching mutated public/private metadata still cannot replace a pinned Gen I identity.
        manifest[0].addProperty("sha256", "f".repeat(64)); inventory[0].addProperty("romSha256", "f".repeat(64))
        assertTrue(runCatching { WesternGen1BaselineCapture.selectControls(manifest, inventory) }.isFailure)
    }

    @Test
    fun visibleWitnessUsesListTraversalAndSevenByteItemRows() {
        val f = fixture(false)
        val witness = WesternGen1BaselineCapture.referenceWitness(RomImage(f.bytes), f.reference(), f.poi, f.map)
        assertEquals(0x8100, f.reference().recordRoot)
        assertEquals(0x8119, witness.eventRow)
        assertEquals(3, witness.tileX); assertEquals(2, witness.tileY)
        assertNull(witness.collectionFlagId)
        assertEquals(7, witness.windows.single { it.role == "itemRow" }.hex.length / 2)
        assertTrue(witness.windows.any { it.role == "objectListTraversal" })
        assertTrue(witness.windows.any { it.role == "mapBankEntry" })
        assertTrue(witness.windows.any { it.role == "objectPointer" && it.offset == 0x8015 })
    }

    @Test
    fun hiddenWitnessUsesSixByteRowsAndCoordinateFlagWithoutQuantity() {
        for (branch in listOf(false, true)) {
            val f = fixture(true, branch)
            val witness = WesternGen1BaselineCapture.referenceWitness(RomImage(f.bytes), f.reference(), f.poi, f.map)
            assertEquals(0x8206, witness.eventRow)
            assertEquals(1, witness.collectionFlagId)
            assertEquals(6, witness.windows.single { it.role == "hiddenRow" }.hex.length / 2)
            assertEquals(0x8503, witness.windows.single { it.role == "coordinateRow" }.offset)
            assertEquals(branch, witness.windows.any { it.role == "hiddenContinuation" })
            val tree = WesternGen1BaselineCapture.json.toJsonTree(f.reference()).asJsonObject
            assertEquals(14, tree.size())
            for (key in listOf("mapHeader", "objectPointerField", "mapBankTable", "mapPointerTable")) assertTrue(tree[key].isJsonNull)
            assertFalse(tree.has("quantity")); assertFalse(tree.has("collectionFlag")); assertFalse(tree.has("tileX"))
        }
    }

    @Test
    fun witnessRejectsMalformedBoundsAndCurrentBindings() {
        val visibleMutations: List<(Fixture) -> Unit> = listOf(
            { it.fields.addProperty("operandOffset", Int.MAX_VALUE) },
            { it.fields.addProperty("recordRoot", -1) },
            { it.fields.addProperty("mapBankTable", Int.MAX_VALUE) },
            { it.fields.addProperty("sourceBank", 1) },
            { it.fields.addProperty("objectPointerField", 0x800a) },
            { it.fields.addProperty("hiddenHandler", 0x8400) },
            { it.bytes[0x102] = 1 },
            { it.bytes[0x204] = 1 },
            { it.bytes[0x8101] = 65 },
            { it.bytes[0x810a] = 65 },
            { it.bytes[0x8110] = 0xc0.toByte() },
            { it.bytes[0x811f] = 6 },
            { it.bytes[0x811a] = 0 },
            { it.poi = it.poi.copy(tileX = 4) },
            { it.poi = it.poi.copy(key = "synthetic/object/1") },
            { it.poi = it.poi.copy(item = LocalMapPoiItem(itemId = 5, collectionFlagId = 1)) },
            { it.map = it.map.copy(gridWidth = 1) },
        )
        visibleMutations.forEachIndexed { index, mutate ->
            val f = fixture(false); mutate(f)
            assertTrue("visible $index", runCatching { WesternGen1BaselineCapture.referenceWitness(RomImage(f.bytes), f.reference(), f.poi, f.map) }.isFailure)
        }
        val hiddenMutations: List<(Fixture) -> Unit> = listOf(
            { it.fields.addProperty("operandOffset", 0x8209) },
            { it.fields.addProperty("coordinateIndex", 256) },
            { it.fields.addProperty("coordinateRoot", Int.MAX_VALUE) },
            { it.fields.addProperty("mapHeader", 0x8000) },
            { it.bytes[0x8200] = 0xff.toByte() },
            { it.bytes[0x8209] = 1 },
            { it.bytes[0x8503] = 3 },
            { it.bytes[0x8500] = 0xff.toByte() },
            { it.bytes[0x8401] = 1 },
            { it.bytes[0x8419] = 0 },
            { it.bytes[0x841a] = 0x7f },
            { it.poi = it.poi.copy(item = LocalMapPoiItem(itemId = 5, collectionFlagId = 2)) },
        )
        hiddenMutations.forEachIndexed { index, mutate ->
            val f = fixture(true, true); mutate(f)
            assertTrue("hidden $index", runCatching { WesternGen1BaselineCapture.referenceWitness(RomImage(f.bytes), f.reference(), f.poi, f.map) }.isFailure)
        }
        val f = fixture(false)
        assertTrue(runCatching { WesternGen1BaselineCapture.referenceWitness(RomImage(f.bytes.copyOf(0x811f)), f.reference(), f.poi, f.map) }.isFailure)
    }

    @Test
    fun authoritySerializationKeepsEqualWidthBranchesAndFullCopyGeometry() {
        val fields = mapOf("root" to 0x9000, "bankEnd" to 0xc000, "copyBytes" to 20, "machineThreshold" to 196,
            "machineSplit" to 201, "lowerAdjustment" to 5, "numberSubtract" to 200, "digitOrigin" to 0xf6,
            "terminator" to 0x50, "lowerPrefix" to 0x1000, "lowerPrefixBytes" to 2, "upperPrefix" to 0x1100, "upperPrefixBytes" to 2)
        val authority = WesternGen1BaselineCapture.json.fromJson(WesternGen1BaselineCapture.json.toJson(fields), GbItemNameAuthority.Available::class.java)
        val tree = WesternGen1BaselineCapture.json.toJsonTree(authority).asJsonObject
        assertEquals(fields.keys, tree.keySet())
        assertEquals(20, tree["copyBytes"].asInt)
        assertEquals(2, tree["lowerPrefixBytes"].asInt); assertEquals(2, tree["upperPrefixBytes"].asInt)
        assertNotEquals(tree["lowerPrefix"], tree["upperPrefix"])
        assertFalse(tree.has("wrapper")); assertFalse(tree.has("codeOffsets"))
    }

    private fun newRoot(prefix: String): Path {
        val parent = System.getenv("DUALDEX_TEST_TEMP_ROOT")?.takeIf(String::isNotBlank)?.let(Path::of)
        if (parent != null) Files.createDirectories(parent)
        return if (parent == null) Files.createTempDirectory(prefix) else Files.createTempDirectory(parent, prefix)
    }

    private fun inventory(): Pair<MutableList<JsonObject>, MutableList<JsonObject>> {
        val all = WesternGen1BaselineCapture.controlHashes.map { (key, hash) -> JsonObject().apply {
            addProperty("language", key.first); addProperty("family", key.second); addProperty("sha256", hash)
            addProperty("size", 1048576); addProperty("file", "synthetic-not-a-rom")
        } }.toMutableList()
        repeat(25) { index -> all += JsonObject().apply {
            addProperty("language", "synthetic-$index"); addProperty("family", "OTHER")
            addProperty("sha256", index.toString(16).padStart(64, '0')); addProperty("size", 1); addProperty("file", "synthetic-not-a-rom")
        } }
        return all to all.map { row -> JsonObject().apply {
            add("language", row["language"]); add("family", row["family"]); add("romSha256", row["sha256"])
        } }.toMutableList()
    }

    private class Fixture(val bytes: ByteArray, val fields: JsonObject, var poi: LocalMapPoi, var map: LocalMap) {
        fun reference(): Gen1ItemReference = WesternGen1BaselineCapture.json.fromJson(fields, Gen1ItemReference::class.java)
    }

    /** Fabricated Gen I records; neither external ROMs nor expected Western labels are read. */
    private fun fixture(hidden: Boolean, branch: Boolean = false): Fixture {
        val bytes = ByteArray(0x10000)
        fun put(at: Int, vararg values: Int) { values.forEachIndexed { i, value -> bytes[at + i] = value.toByte() } }
        val map = LocalMap("synthetic", null, 2, 160, 160, 10, 10, "synthetic-image")
        val fields = JsonObject().apply {
            addProperty("poiKey", if (hidden) "synthetic/hidden/1" else "synthetic/object/2")
            addProperty("itemId", 5); addProperty("baseAreaId", 2); addProperty("sourceBank", 2)
            addProperty("kind", if (hidden) "HIDDEN_EVENT" else "VISIBLE_OBJECT")
        }
        if (hidden) {
            put(0x8200, 7, 8, 9, 2, 0, 0x44, 2, 3, 5, 2, 0, 0x44, 0xff)
            put(0x8400, 0x21, 0, 0x45, 0xcd, 0, 1, 0xea, 0, 0xc0, 0x21, 1, 0xc0,
                0xfa, 0, 0xc0, 0x4f, 6, 1, 0x3e, 2, 0xcd, 0, 2, 0x79, 0xa7)
            if (branch) { put(0x8419, 0x20, 5, 0xcd, 0, 3); put(0x8420, 0x3e, 0xff, 0xe0, 1, 0xc9) }
            else put(0x8419, 0xc0, 0xcd, 0, 3)
            put(0x8500, 1, 8, 7, 2, 2, 3, 0xff)
            fields.addProperty("recordRoot", 0x8200); fields.addProperty("operandOffset", 0x8208)
            fields.addProperty("hiddenHandler", 0x8400); fields.addProperty("coordinateRoot", 0x8500); fields.addProperty("coordinateIndex", 1)
        } else {
            put(0x102, 2); put(0x204, 0, 0x40)
            put(0x8000, 0, 5, 5, 0, 0, 0, 0, 0, 0, 1); put(0x8015, 0, 0x41)
            // One warp, one background, normal six-byte row, trainer eight-byte row, item seven-byte row.
            put(0x8100, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 3)
            put(0x810b, 0, 4, 4, 0, 0, 0)
            put(0x8111, 0, 4, 4, 0, 0, 0x40, 0, 0)
            put(0x8119, 0, 6, 7, 0, 0, 0x80, 5)
            fields.addProperty("recordRoot", 0x8100); fields.addProperty("operandOffset", 0x811f)
            fields.addProperty("mapHeader", 0x8000); fields.addProperty("objectPointerField", 0x8015)
            fields.addProperty("mapBankTable", 0x100); fields.addProperty("mapPointerTable", 0x200)
        }
        val poi = LocalMapPoi(fields["poiKey"].asString, map.key, 2, 3, 2,
            if (hidden) LocalMapPoiKind.HIDDEN_ITEM else LocalMapPoiKind.VISIBLE_ITEM,
            item = LocalMapPoiItem(itemId = 5, collectionFlagId = if (hidden) 1 else null))
        return Fixture(bytes, fields, poi, map)
    }

    private fun states(expected: Int, covered: Int) = LocalizedTextCapability.entries.associateWith {
        if (it != LocalizedTextCapability.ITEM_NAMES) LocalizedCapabilityState.notApplicable("synthetic", 0)
        else LocalizedCapabilityState(
            when {
                expected == 0 -> com.enrpau.dualscreendex.parser.model.CapabilityStatus.NOT_APPLICABLE
                covered == 0 -> com.enrpau.dualscreendex.parser.model.CapabilityStatus.NOT_FOUND
                covered == expected -> com.enrpau.dualscreendex.parser.model.CapabilityStatus.AVAILABLE
                else -> com.enrpau.dualscreendex.parser.model.CapabilityStatus.PARTIAL
            }, if (covered == 0) 0.0 else 1.0, covered, expected)
    }

    private fun outcomes(
        requested: Set<Int>, names: Map<Int, CatalogField<String>>,
        states: Map<LocalizedTextCapability, LocalizedCapabilityState>?, overlay: Map<Int, CatalogField<String>>?,
    ) = linkedMapOf<String, Result<Unit>>().also { results ->
        WesternGen1BaselineCapture.checkItemObservations(requested, names, states, overlay) { stage, assertion ->
            check(stage !in results)
            results[stage] = runCatching(assertion)
        }
    }
}
