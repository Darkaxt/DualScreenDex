package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.parser.catalog.*
import com.google.gson.*
import org.junit.Assert.*
import org.junit.Test

/** Fabricated metadata/labels only. No private fixture or original control is opened. */
class WesternGen3SemanticOracleTest {
    @Test fun semanticOptInAndExternalPinRejectBeforeFixtureOrInputCallbacks() {
        for (optIn in listOf(null, "", "0", "true", " 1", "1 ")) {
            var reads = 0; var inputs = 0
            assertThrows(IllegalArgumentException::class.java) {
                WesternGen3SemanticOracle.preflight(optIn, WesternGen3SemanticOracle.fixtureSha256,
                    { reads++; byteArrayOf() }, controls()) { inputs++ }
            }
            assertEquals(0, reads); assertEquals(0, inputs)
        }
        for (hash in listOf(null, "", "0".repeat(64))) {
            var reads = 0
            assertThrows(IllegalArgumentException::class.java) {
                WesternGen3SemanticOracle.preflight("1", hash, { reads++; byteArrayOf() }, controls()) { error("input") }
            }
            assertEquals(0, reads)
        }
    }

    @Test fun wrongEmptyOversizedAndProductionLookingFixturesFailBeforeInput() {
        for (bytes in listOf(byteArrayOf(), "{}".toByteArray(), ByteArray(4_194_305),
            WesternGen3BaselineCapture.json.toJson(fixture()).toByteArray())) {
            var inputs = 0
            assertThrows(IllegalArgumentException::class.java) {
                WesternGen3SemanticOracle.preflight("1", WesternGen3SemanticOracle.fixtureSha256, { bytes }, controls()) { inputs++ }
            }
            assertEquals(0, inputs)
        }
    }

    @Test fun strictReaderRejectsDuplicateTrailingAndMalformedUtf8Metadata() {
        for (bytes in listOf("{\"controls\":[],\"controls\":[]}".toByteArray(), "{} {}".toByteArray(),
            byteArrayOf(0xc3.toByte(), 0x28))) {
            assertTrue(runCatching { WesternGen1SemanticOracle.strictJson(bytes) }.isFailure)
        }
    }

    @Test fun independentlyPinnedDomainsBindAllFifteenAnd1660WithoutProductionNames() {
        val result = WesternGen3SemanticOracle.bind(fixture(), controls())
        assertEquals(WesternGen3BaselineCapture.controlHashes.values.toSet(), result.keys)
        assertEquals(1660, result.values.sumOf { it.names.size })
        assertEquals(setOf(101, 104, 127), result.values.map { it.names.size }.toSet())
        assertTrue(result.values.all { it.names.values.all { name -> name.startsWith("SYNTHETIC") } })
        assertEquals(5, result.values.count { 0 in it.names })
        assertTrue(result.values.all { 175 !in it.names })
    }

    @Test fun missingDuplicateExtraAndMisboundControlsAndRowsReject() {
        val mutations: List<(JsonObject) -> Unit> = listOf(
            { it.addProperty("status", "PRIVATE_INDEPENDENT_EXPECTATIONS_PENDING_PARENT_REVIEW") },
            { it.addProperty("productionSemanticAcceptance", true) },
            { it.addProperty("independentExpectationsReady", 1659) },
            { it.getAsJsonObject("counts").addProperty("MISSING_RECORD", 1) },
            { it.getAsJsonArray("controls").remove(0) },
            { it.getAsJsonArray("controls").add(it.getAsJsonArray("controls")[0].deepCopy()) },
            { row(it).addProperty("romSha256", "0".repeat(64)) },
            { row(it).addProperty("gameCode", "AXVJ") },
            { row(it).addProperty("key", "ja-RUBY_SAPPHIRE") },
            { row(it).getAsJsonArray("requestedIds").remove(0) },
            { row(it).getAsJsonArray("requestedIds").set(0, JsonPrimitive(0)) }, // same count, different domain
            { row(it).getAsJsonArray("requestedIds").set(0, JsonPrimitive(65536)) },
            { row(it).getAsJsonArray("requestedIds").set(0, JsonPrimitive("1")) },
            { row(it).getAsJsonArray("rows").remove(0) },
            { row(it).getAsJsonArray("rows").add(label(it).deepCopy()) },
            { label(it).addProperty("status", "MISSING_RECORD") },
            { label(it).addProperty("itemId", 65535) },
            { label(it).addProperty("rawName", " ") },
            { label(it).addProperty("rawName", "�") },
            { label(it).addProperty("expectedDisplayName", "WRONG") },
            { it.addProperty("normalization", "case folding allowed") },
        )
        mutations.forEachIndexed { index, mutate ->
            val tree = fixture(); mutate(tree)
            assertTrue("mutation $index", runCatching { WesternGen3SemanticOracle.bind(tree, controls()) }.isFailure)
        }
        val wrong = controls().toMutableList()
        wrong[0] = wrong[0].copy(identity = wrong[0].identity.deepCopy().apply { addProperty("code", "AXVJ") })
        assertTrue(runCatching { WesternGen3SemanticOracle.bind(fixture(), wrong) }.isFailure)
        assertTrue(runCatching { WesternGen3SemanticOracle.bind(fixture(), controls().drop(1)) }.isFailure)
        assertTrue(runCatching { WesternGen3SemanticOracle.bind(fixture(), controls() + controls().first()) }.isFailure)
    }

    @Test fun normalizationChangesAsciiWhitespaceOnly() {
        assertEquals("POKé BALL", WesternGen3SemanticOracle.displayName(" \tPOKé  BALL\r\n"))
        for (text in listOf("Poké Ball", "POKÉ BALL", "POKé BALL", "POKé BALL", " POKé BALL ")) {
            assertEquals(text, WesternGen3SemanticOracle.displayName(text))
            assertNotEquals("POKé BALL", WesternGen3SemanticOracle.displayName(text))
        }
    }

    @Test fun availableFieldsNeedEveryExactIndependentNameAndFullCapability() {
        val expected = linkedMapOf(0 to "????????", 1 to " POKé  BALL ")
        val fields = mapOf(0 to CatalogField.available("????????"), 1 to CatalogField.available("POKé BALL"))
        WesternGen3SemanticOracle.checkFields(expected, expected.keys, mapOf("producer" to fields, "overlay" to fields),
            LocalizedCapabilityState.available(2)) { _, block -> block() }
        for (wrong in listOf("Poké Ball", "POKÉ BALL", "POKé BALL", "Ball 1", "POKé BALL")) {
            val result = outcomes { check -> WesternGen3SemanticOracle.checkFields(expected, expected.keys,
                mapOf("producer" to (fields + (1 to CatalogField.available(wrong)))), LocalizedCapabilityState.available(2), check) }
            assertTrue(wrong, result.any { it.isFailure })
        }
        for (actual in listOf(fields - 1, fields + (2 to CatalogField.available("extra")),
            fields + (1 to CatalogField.notFound<String>("not available")))) {
            assertTrue(outcomes { check -> WesternGen3SemanticOracle.checkFields(expected, expected.keys,
                mapOf("producer" to actual), LocalizedCapabilityState.available(2), check) }.any { it.isFailure })
        }
        assertTrue(outcomes { check -> WesternGen3SemanticOracle.checkFields(expected, setOf(0), mapOf("overlay" to fields),
            LocalizedCapabilityState.available(1), check) }.any { it.isFailure })
        for (state in listOf(null, LocalizedCapabilityState.available(1), LocalizedCapabilityState.notFound("synthetic", 2))) {
            assertTrue(outcomes { check -> WesternGen3SemanticOracle.checkFields(expected, expected.keys, mapOf("overlay" to fields),
                state, check) }.any { it.isFailure })
        }
    }

    @Test fun projectedComparisonRequiresAllOccurrencesAndRejectsFallbacks() {
        val expected = mapOf(0 to "????????", 1 to "POKé BALL")
        val valid = mapOf(0 to listOf("????????"), 1 to listOf("POKé BALL", "POKé BALL"))
        WesternGen3SemanticOracle.checkNames(expected, valid) { _, block -> block() }
        for (actual in listOf(valid - 0, valid + (2 to listOf("extra")), valid + (1 to emptyList()),
            valid + (1 to listOf("POKé BALL", "WRONG")), valid + (0 to listOf("Ball 0")), valid + (1 to listOf(null)))) {
            assertTrue(outcomes { check -> WesternGen3SemanticOracle.checkNames(expected, actual, check) }.any { it.isFailure })
        }
    }

    private fun row(tree: JsonObject) = tree.getAsJsonArray("controls")[0].asJsonObject
    private fun label(tree: JsonObject) = row(tree).getAsJsonArray("rows")[0].asJsonObject
    private fun outcomes(run: ((String, () -> Unit) -> Unit) -> Unit): List<Result<Unit>> {
        val result = linkedMapOf<String, Result<Unit>>()
        run { stage, block -> require(stage !in result); result[stage] = runCatching(block) }
        return result.values.toList()
    }
    private fun controls() = WesternGen3BaselineCapture.controlHashes.map { (cell, hash) ->
        WesternGen3BaselineCapture.Control(JsonObject().apply {
            addProperty("language", cell.first); addProperty("family", cell.second); addProperty("sha256", hash)
            addProperty("code", WesternGen3BaselineCapture.controlCodes.getValue(cell)); addProperty("size", 16_777_216)
            addProperty("file", "synthetic-not-opened")
        }, JsonObject())
    }
    private fun fixture(): JsonObject = JsonObject().apply {
        addProperty("task", 416); addProperty("status", "INDEPENDENT_FIXTURE_COMPLETE")
        addProperty("normalization", WesternGen3SemanticOracle.displayNormalization)
        addProperty("productionSemanticAcceptance", false); addProperty("independentExpectationsReady", 1660)
        add("counts", JsonObject().apply { addProperty("INDEPENDENT_EXPECTATION_READY", 1660) })
        add("controls", JsonArray().apply { controls().forEach { control ->
            val identity = control.identity; val family = identity["family"].asString
            val ids = WesternGen3SemanticOracle.requestedDomains.getValue(family)
            add(JsonObject().apply {
                addProperty("key", identity["language"].asString + "-" + family)
                add("gameCode", identity["code"]); add("romSha256", identity["sha256"])
                add("requestedIds", JsonArray().apply { ids.forEach { add(it) } })
                add("rows", JsonArray().apply { ids.forEach { id -> add(JsonObject().apply {
                    addProperty("itemId", id); addProperty("rawName", "SYNTHETIC $id")
                    addProperty("expectedDisplayName", "SYNTHETIC $id"); addProperty("status", "INDEPENDENT_EXPECTATION_READY")
                }) } })
            })
        } })
    }
}
