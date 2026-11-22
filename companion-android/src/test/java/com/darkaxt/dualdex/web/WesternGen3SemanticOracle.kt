package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.parser.catalog.*
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.junit.Assert.*

/** Test-only consumer of the separately ratified Task416 proof; production names never supply expectations. */
internal object WesternGen3SemanticOracle {
    const val fixtureSha256 = "3de6d6c19ab7212674a0dda32c31969bb788ef8f7b0035fd46486447062d7a47"
    const val displayNormalization = "replace ASCII Regex(\\s+) runs with U+0020, trim; no case/accent/Unicode normalization"
    val requiredPaths = setOf("semantic-producer", "semantic-materialize", "semantic-sqlite", "semantic-api")
    val requiredBoundaries = setOf("producer", "materialize-overlay", "materialize-projection", "sqlite-overlay", "sqlite-projection", "api")
    val requestedDomains = mapOf(
        "RUBY_SAPPHIRE" to "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,18,19,20,21,22,23,24,25,34,35,36,37,48,49,50,51,63,64,65,66,67,68,69,70,73,74,75,76,77,78,79,83,84,85,86,94,95,96,97,98,103,106,107,108,109,110,111,121,122,123,124,125,126,127,128,129,195,200,206,212,220,221,278,281,282,283,284,285,289,290,294,295,299,301,306,310,311,314,317,318,320,325,336,345,346",
        "EMERALD" to "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,18,19,20,21,22,23,24,25,30,32,33,34,35,36,37,45,48,49,50,51,63,64,65,66,67,68,69,70,71,73,74,75,76,77,78,79,83,84,85,86,94,95,96,97,98,103,106,107,108,109,110,111,121,122,123,124,125,126,127,128,129,195,200,206,212,220,221,278,281,282,283,284,285,289,290,294,295,299,301,306,310,311,314,317,318,320,325,336",
        "FIRERED_LEAFGREEN" to "0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,34,35,36,37,38,45,63,64,65,66,67,68,69,70,71,73,74,75,76,77,78,79,84,85,86,93,94,95,96,97,98,103,104,106,107,108,109,110,111,133,134,135,136,137,138,139,140,141,142,148,149,150,151,152,181,183,184,187,190,199,200,201,206,212,217,218,220,221,222,289,290,293,295,296,297,299,300,302,305,306,309,310,313,319,320,324,325,328,329,331,332,333,334,335,336,337,338,345,351,353,355",
    ).mapValues { (_, csv) -> csv.split(',').map(String::toInt) }

    data class Control(val identity: JsonObject, val rawNames: Map<Int, String>) {
        val names = rawNames.mapValues { displayName(it.value) }
    }
    private val asciiWhitespace = Regex("[ \\t\\n\\x0B\\f\\r]+")
    internal fun displayName(raw: String): String = raw.replace(asciiWhitespace, " ").trim(' ')

    internal fun <T> preflight(optIn: String?, externalSha: String?, fixture: () -> ByteArray,
        controls: List<WesternGen3BaselineCapture.Control>, ready: (Map<String, Control>) -> T): T {
        require(optIn == "1") { "DUALDEX_WESTERN_GEN3_SEMANTIC_ACCEPTANCE must be exactly 1" }
        require(externalSha == fixtureSha256) { "externally supplied SHA must match the approved independent Task416 fixture" }
        val bytes = fixture()
        WesternGen3BaselineCapture.requirePin(bytes, fixtureSha256)
        return ready(bind(WesternGen1SemanticOracle.strictJson(bytes), controls))
    }

    /** Structural seam for fabricated tests. The real capture can enter only through pinned preflight. */
    internal fun bind(tree: JsonObject, controls: List<WesternGen3BaselineCapture.Control>): Map<String, Control> {
        require(integer(tree["task"]) == 416)
        require(text(tree, "status") == "INDEPENDENT_FIXTURE_COMPLETE")
        require(text(tree, "normalization") == displayNormalization)
        val acceptance = requireNotNull(tree["productionSemanticAcceptance"])
        require(acceptance.isJsonPrimitive && acceptance.asJsonPrimitive.isBoolean && !acceptance.asBoolean)
        require(integer(tree["independentExpectationsReady"]) == 1660)
        val counts = requireNotNull(tree.getAsJsonObject("counts"))
        require(counts.keySet() == setOf("INDEPENDENT_EXPECTATION_READY"))
        require(integer(counts["INDEPENDENT_EXPECTATION_READY"]) == 1660)

        require(controls.size == 15)
        val identities = linkedMapOf<String, JsonObject>()
        val cells = linkedSetOf<Pair<String, String>>()
        for (control in controls) {
            val identity = control.identity
            val cell = text(identity, "language") to text(identity, "family")
            val hash = text(identity, "sha256")
            require(cells.add(cell) && WesternGen3BaselineCapture.controlHashes[cell] == hash)
            require(text(identity, "code") == WesternGen3BaselineCapture.controlCodes.getValue(cell))
            require(integer(identity["size"]) == 16_777_216)
            text(identity, "file") // Binding only: never inspect or open an input path here.
            require(identities.put(hash, identity) == null)
        }
        require(cells == WesternGen3BaselineCapture.controlHashes.keys)
        val rows = requireNotNull(tree.getAsJsonArray("controls")); require(rows.size() == 15)
        val result = linkedMapOf<String, Control>()
        for (element in rows) {
            val row = element.asJsonObject
            val hash = text(row, "romSha256")
            val identity = requireNotNull(identities[hash])
            val family = text(identity, "family")
            require(text(row, "key") == "${text(identity, "language")}-$family")
            require(text(row, "gameCode") == text(identity, "code"))
            val domain = requestedDomains.getValue(family)
            val ids = requireNotNull(row.getAsJsonArray("requestedIds")).map { integer(it) }
            require(ids == domain) { "exact original requested domain required for $hash" }
            val labels = requireNotNull(row.getAsJsonArray("rows")); require(labels.size() == domain.size)
            val names = linkedMapOf<Int, String>()
            for (label in labels) {
                val item = label.asJsonObject
                val id = integer(item["itemId"]); require(id in domain)
                require(text(item, "status") == "INDEPENDENT_EXPECTATION_READY")
                val raw = text(item, "rawName"); val display = displayName(raw)
                require(display.isNotEmpty() && '�' !in raw)
                require(text(item, "expectedDisplayName") == display)
                require(names.put(id, raw) == null) { "duplicate independent item $id" }
            }
            require(names.keys == domain.toSet())
            require(result.put(hash, Control(identity.deepCopy(), names.toMap())) == null)
        }
        require(result.keys == identities.keys && result.values.sumOf { it.names.size } == 1660)
        return result
    }

    private fun text(row: JsonObject, field: String): String {
        val value = requireNotNull(row[field])
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString.also { require(it.isNotEmpty()) }
    }
    private fun integer(value: JsonElement?): Int {
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        require(value.asString.matches(Regex("0|[1-9][0-9]*")))
        return requireNotNull(value.asString.toIntOrNull())
    }

    /** The producer has no capability object yet; compare its complete fields without inventing one. */
    internal fun checkProducer(rawExpected: Map<Int, String>, requested: Set<Int>, fields: Map<Int, CatalogField<String>>,
        check: (String, () -> Unit) -> Unit) = checkFieldMaps(rawExpected, requested, mapOf("producer" to fields), check)

    private fun checkFieldMaps(rawExpected: Map<Int, String>, requested: Set<Int>, fields: Map<String, Map<Int, CatalogField<String>>?>,
        check: (String, () -> Unit) -> Unit) {
        val expected = rawExpected.mapValues { displayName(it.value) }
        check("requested-domain") {
            assertTrue(expected.isNotEmpty() && expected.keys.all { it in 0..65535 } && expected.values.all { it.isNotEmpty() })
            assertEquals(expected.keys, requested); assertTrue(fields.isNotEmpty())
        }
        for ((stage, names) in fields) {
            check("$stage.denominator") { assertEquals(expected.keys, requireNotNull(names).keys) }
            check("$stage.required-names") {
                val actual = requireNotNull(names)
                assertTrue(actual.values.all { it.status == CapabilityStatus.AVAILABLE && it.value != null })
                assertEquals(expected, actual.mapValues { displayName(requireNotNull(it.value.value)) })
            }
        }
    }
    internal fun checkFields(rawExpected: Map<Int, String>, requested: Set<Int>, fields: Map<String, Map<Int, CatalogField<String>>?>,
        state: LocalizedCapabilityState?, check: (String, () -> Unit) -> Unit) {
        checkFieldMaps(rawExpected, requested, fields, check)
        check("item-capability.complete") {
            val actual = requireNotNull(state)
            assertEquals(CapabilityStatus.AVAILABLE, actual.status)
            assertEquals(rawExpected.size, actual.expectedRecords); assertEquals(rawExpected.size, actual.coveredRecords)
            assertEquals(0, actual.incompleteRecords)
        }
    }
    internal fun checkNames(rawExpected: Map<Int, String>, actual: Map<Int, List<String?>>, check: (String, () -> Unit) -> Unit) {
        val expected = rawExpected.mapValues { displayName(it.value) }
        check("denominator") { assertEquals(expected.keys, actual.keys) }
        check("required-names") {
            assertTrue(expected.isNotEmpty() && expected.values.all { it.isNotEmpty() })
            for ((id, label) in expected) {
                val occurrences = requireNotNull(actual[id]); assertTrue("missing occurrence $id", occurrences.isNotEmpty())
                occurrences.forEachIndexed { index, name ->
                    assertEquals("independent item $id occurrence $index", label, name?.let(::displayName))
                }
            }
        }
    }
    internal fun checkCatalog(oracle: Control, catalog: ParsedCatalog, requested: Set<Int>, fields: Map<Int, CatalogField<String>>,
        check: (String, () -> Unit) -> Unit) {
        val overlay = catalog.defaultLocalizedText()
        check("identity") {
            assertEquals(oracle.identity["sha256"].asString, catalog.romSha256)
            assertEquals(oracle.identity["family"].asString, catalog.family.name)
            assertEquals(oracle.identity["language"].asString, catalog.languageManifest.defaultLanguage?.value)
        }
        checkFields(oracle.rawNames, requested, mapOf("producer" to fields, "overlay" to overlay?.itemNames),
            overlay?.localizedCapabilities?.get(LocalizedTextCapability.ITEM_NAMES), check)
        val ids = WesternGen3BaselineCapture.numericIds(catalog)
        val projection = catalog.defaultTextProjection()
        // One direct observation per numeric ID, plus every POI override occurrence, without deduplication.
        val projected = (ids.map { it to projection.itemName(it) } + catalog.localMaps.pois.mapNotNull { poi ->
            poi.item?.itemId?.let { it to projection.poiItemName(poi.key, it) }
        }).groupBy({ it.first }, { it.second })
        checkNames(oracle.rawNames, projected) { stage, block -> check("projection.$stage", block) }
    }
}
