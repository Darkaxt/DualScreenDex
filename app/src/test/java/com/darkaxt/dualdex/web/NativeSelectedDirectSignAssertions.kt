package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogSchema
import com.enrpau.dualscreendex.companion.api.LocalMapPoiView
import com.enrpau.dualscreendex.companion.api.LocalMapView
import com.enrpau.dualscreendex.parser.catalog.CatalogPoiText
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoi
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiTextObligation
import com.enrpau.dualscreendex.parser.catalog.LocalizedCapabilityState
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.google.gson.JsonObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

internal data class NativeSelectedDirectSignExpectation(
    val control: String, val sha256: String, val mapKey: String, val baseAreaId: Int,
    val gridWidth: Int, val gridHeight: Int, val poiKey: String, val tileX: Int, val tileY: Int,
    val headlineSha256: String,
)

internal data class NativeSelectedSignMetadata(
    val sha256: String, val parserSchemaVersion: Long, val sqlSchemaVersion: Long, val complete: Long,
)

/** Test-only independent selected-sign oracle, never a production route or decoder. */
internal object NativeSelectedDirectSignAssertions {
    fun digest(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun names(expected: NativeSelectedDirectSignExpectation, maps: List<LocalMap>, pois: List<LocalMapPoi>,
        direct: CatalogPoiText?, projected: String?, fulfilled: Set<String>, state: LocalizedCapabilityState): JsonObject {
        assertEquals("unique numeric map keys", maps.size, maps.map { it.key }.toSet().size)
        assertEquals("unique numeric map IDs", maps.size, maps.map { it.baseAreaId }.toSet().size)
        assertEquals("unique numeric POI keys", pois.size, pois.map { it.key }.toSet().size)
        val selectedMaps = maps.filter { it.key == expected.mapKey }
        assertEquals("selected map must exist exactly once", 1, selectedMaps.size)
        val map = selectedMaps.single()
        assertEquals("independent selected map geometry", listOf(expected.baseAreaId, expected.gridWidth * 16,
            expected.gridHeight * 16, expected.gridWidth, expected.gridHeight),
            listOf(map.baseAreaId, map.pixelWidth, map.pixelHeight, map.gridWidth, map.gridHeight))
        assertTrue("shared map label must remain isolated", map.displayName == null)
        val selectedPois = pois.filter { it.key == expected.poiKey }
        assertEquals("selected sign must exist exactly once", 1, selectedPois.size)
        val poi = selectedPois.single()
        assertEquals(expected.mapKey, poi.localMapKey)
        assertEquals(expected.baseAreaId, poi.baseAreaId)
        assertEquals(expected.tileX, poi.tileX)
        assertEquals(expected.tileY, poi.tileY)
        assertEquals(LocalMapPoiKind.PLACE, poi.kind)
        assertEquals("destination labels cannot satisfy a direct sign", LocalMapPoiTextObligation.DIRECT_TEXT, poi.textObligation)
        assertTrue("shared sign label must remain isolated", poi.displayName == null && poi.displayNamesByTrainerGender.isEmpty())
        assertTrue("selected direct sign is not an item or service", poi.item == null && poi.service == null)
        assertNotNull("selected overlay direct text is required", direct)
        assertTrue("selected text cannot be an item/gender alias", direct!!.itemDisplayName == null && direct.displayNamesByTrainerGender.isEmpty())
        assertNotNull("selected direct headline is required", direct.displayName)
        val field = direct.displayName!!
        assertEquals(CapabilityStatus.AVAILABLE, field.status)
        val headline = headline(expected, field.value)
        assertEquals("projection must independently match the selected headline", headline, headline(expected, projected))
        assertTrue("coverage keys must belong to numeric POIs", pois.map { it.key }.toSet().containsAll(fulfilled))
        assertTrue("selected sign must fulfill its own obligation", expected.poiKey in fulfilled)
        assertEquals("do not collapse POI obligations to this one selected sign", pois.size, state.expectedRecords)
        assertEquals("covered POIs must equal fulfilled obligations", fulfilled.size, state.coveredRecords)
        assertEquals(if (fulfilled.size == pois.size) CapabilityStatus.AVAILABLE else CapabilityStatus.PARTIAL, state.status)
        return JsonObject().apply {
            addProperty("totalMaps", maps.size)
            addProperty("totalPois", pois.size)
            addProperty("selectedExpected", 1)
            addProperty("selectedCovered", 1)
            addProperty("headlineSha256", headline)
            // Numeric inventory is same-capture parity, not a semantic oracle for other signs.
            addProperty("numericInventorySha256", digest(maps.sortedBy { it.key }.toString() + pois.sortedBy { it.key }.toString()))
            add("map", JsonObject().apply {
                addProperty("key", map.key); addProperty("baseAreaId", map.baseAreaId)
                addProperty("pixelWidth", map.pixelWidth); addProperty("pixelHeight", map.pixelHeight)
                addProperty("gridWidth", map.gridWidth); addProperty("gridHeight", map.gridHeight)
                addProperty("imageAssetKey", map.imageAssetKey)
            })
            add("selected", JsonObject().apply {
                addProperty("key", poi.key); addProperty("localMapKey", poi.localMapKey)
                addProperty("baseAreaId", poi.baseAreaId); addProperty("tileX", poi.tileX); addProperty("tileY", poi.tileY)
                addProperty("kind", poi.kind.name); addProperty("textObligation", poi.textObligation.name)
                addProperty("organicVisibility", poi.organicVisibility.name)
                addProperty("destinationBaseAreaId", poi.destinationBaseAreaId)
                addProperty("sharedDisplayName", poi.displayName)
            })
            add("poiText", coverage(state.status.name, state.coveredRecords, state.expectedRecords))
        }
    }

    fun api(expected: NativeSelectedDirectSignExpectation, reopened: JsonObject,
        numericMaps: List<LocalMap>, numericPois: List<LocalMapPoi>, maps: List<LocalMapView>, pois: List<LocalMapPoiView>,
        status: String, covered: Int, expectedRecords: Int): JsonObject {
        assertEquals("API map denominator", numericMaps.size, maps.size)
        assertEquals("API POI denominator", numericPois.size, pois.size)
        assertEquals("API duplicate map keys", maps.size, maps.map { it.key }.toSet().size)
        assertEquals("API duplicate POI keys", pois.size, pois.map { it.key }.toSet().size)
        assertEquals(numericMaps.map { it.key }.toSet(), maps.map { it.key }.toSet())
        assertEquals(numericPois.map { it.key }.toSet(), pois.map { it.key }.toSet())
        val mapByKey = numericMaps.associateBy { it.key }
        maps.forEach { map ->
            val source = mapByKey.getValue(map.key)
            assertEquals("API map geometry ${map.key}",
                listOf(source.baseAreaId, source.pixelWidth, source.pixelHeight, source.gridWidth, source.gridHeight),
                listOf(map.baseAreaId, map.pixelWidth, map.pixelHeight, map.gridWidth, map.gridHeight))
        }
        val poiByKey = numericPois.associateBy { it.key }
        pois.forEach { poi ->
            val source = poiByKey.getValue(poi.key)
            assertEquals(source.localMapKey, poi.localMapKey)
            assertEquals(source.baseAreaId, poi.baseAreaId)
            assertEquals(source.tileX, poi.tileX)
            assertEquals(source.tileY, poi.tileY)
            assertEquals(source.destinationBaseAreaId, poi.destinationBaseAreaId)
        }
        val selected = pois.filter { it.key == expected.poiKey }
        assertEquals("API selected sign must exist exactly once", 1, selected.size)
        val poi = selected.single()
        assertEquals("PLACE", poi.category)
        assertTrue("API selected sign cannot borrow item/service text", poi.itemId == null && poi.itemName == null && poi.service == null)
        val headline = headline(expected, poi.displayName)
        assertEquals(reopened["headlineSha256"].asString, headline)
        assertEquals("API must retain full POI coverage", reopened.getAsJsonObject("poiText"), coverage(status, covered, expectedRecords))
        assertEquals(reopened["totalMaps"].asInt, maps.size)
        assertEquals(reopened["totalPois"].asInt, pois.size)
        // Obligation/visibility are not API fields: their authority is the exact reopened numeric-key join above.
        return reopened.deepCopy().apply {
            addProperty("headlineSha256", headline)
            addProperty("totalMaps", maps.size)
            addProperty("totalPois", pois.size)
            add("poiText", coverage(status, covered, expectedRecords))
        }
    }

    fun metadata(expected: NativeSelectedDirectSignExpectation, rows: List<NativeSelectedSignMetadata>): JsonObject {
        assertEquals("fresh actual SQLite metadata must match this control and current constants",
            listOf(NativeSelectedSignMetadata(expected.sha256, CatalogSchema.parserSchemaVersion.toLong(), CatalogSchema.version.toLong(), 1)), rows)
        val row = rows.single()
        return JsonObject().apply {
            addProperty("sha256", row.sha256)
            addProperty("parserSchemaVersion", row.parserSchemaVersion)
            addProperty("sqlSchemaVersion", row.sqlSchemaVersion)
            addProperty("isComplete", row.complete)
        }
    }

    fun receipt(expected: NativeSelectedDirectSignExpectation, control: String, sha256: String,
        producer: JsonObject?, sqlite: JsonObject?, api: JsonObject?, metadata: JsonObject?,
        originalReads: Int, originalParserInvocations: Int, apiReparses: Int): JsonObject {
        assertEquals(expected.control, control)
        assertEquals(expected.sha256, sha256)
        assertEquals("one original read in this capture", 1, originalReads)
        assertEquals("one original parser invocation in this capture", 1, originalParserInvocations)
        assertEquals("cache-only API must not reparse", 0, apiReparses)
        assertNotNull("producer boundary must pass", producer)
        assertNotNull("reopen boundary must pass", sqlite)
        assertNotNull("API boundary must pass", api)
        assertNotNull("actual SQLite metadata boundary must pass", metadata)
        assertEquals("same-capture SQLite preserves every selected observation", producer, sqlite)
        assertEquals("same-capture API preserves every selected observation", sqlite, api)
        assertEquals(expected.headlineSha256, producer!!["headlineSha256"].asString)
        assertEquals(1, producer["selectedExpected"].asInt)
        assertEquals(1, producer["selectedCovered"].asInt)
        return JsonObject().apply {
            addProperty("scope", "SELECTED_DIRECT_SIGN_ONLY")
            addProperty("overallNativeSemanticAcceptance", "NOT_CLAIMED")
            addProperty("control", control); addProperty("sha256", sha256)
            addProperty("parserSchemaVersion", CatalogSchema.parserSchemaVersion)
            addProperty("sqlSchemaVersion", CatalogSchema.version)
            add("producer", producer); add("sqlite", sqlite); add("api", api); add("sqliteMetadata", metadata)
            addProperty("apiObligationAuthority", "REOPENED_NUMERIC_KEY_JOIN")
            addProperty("originalReads", originalReads)
            addProperty("originalParserInvocations", originalParserInvocations)
            addProperty("apiReparses", apiReparses)
        }
    }

    private fun headline(expected: NativeSelectedDirectSignExpectation, text: String?): String {
        assertTrue("selected headline must be present and nonblank", !text.isNullOrBlank())
        return digest(text!!).also { assertEquals("independent UTF-8 selected headline", expected.headlineSha256, it) }
    }

    private fun coverage(status: String, covered: Int, expected: Int) = JsonObject().apply {
        addProperty("status", status); addProperty("coveredRecords", covered); addProperty("expectedRecords", expected)
    }
}
