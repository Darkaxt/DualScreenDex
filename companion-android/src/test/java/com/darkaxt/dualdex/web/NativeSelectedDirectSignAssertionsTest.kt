package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogSchema
import com.enrpau.dualscreendex.companion.api.LocalMapPoiView
import com.enrpau.dualscreendex.companion.api.LocalMapView
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.CatalogPoiText
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoi
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiTextObligation
import com.enrpau.dualscreendex.parser.catalog.LocalizedCapabilityState
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Fabricated DTOs only. No ROM paths, captured bytes, parser invocation or real-control selectors. */
class NativeSelectedDirectSignAssertionsTest {
    @Test fun acceptsExactDirectSignAcrossAllThreeBoundaries() {
        val f = Fixture()
        assertEquals(f.observe(), f.observe())
        assertEquals(f.observe(), f.api())
        assertEquals(JsonPrimitive(1), f.observe()["selectedCovered"])
        assertEquals(JsonPrimitive(2), f.observe()["totalPois"])
        // A structural destination may coexist with direct prose; it must not become the label authority.
        val withDestination = f.observe(pois = listOf(f.sign.copy(destinationBaseAreaId = 0x102), f.other))
        assertEquals(JsonPrimitive(0x102), withDestination.getAsJsonObject("selected")["destinationBaseAreaId"])
    }

    @Test fun rejectsMissingWrongAndBorrowedNames() {
        val f = Fixture()
        f.observe()
        for (direct in listOf(null, CatalogPoiText(displayName = CatalogField.available("WRONG SYNTHETIC")),
            CatalogPoiText(itemDisplayName = CatalogField.available(f.headline)),
            CatalogPoiText(displayNamesByTrainerGender = mapOf(0 to CatalogField.available(f.headline))))) {
            rejected { f.observe(direct = direct) }
        }
        for (projected in listOf(null, "", " ", "WRONG SYNTHETIC", "SYNTHETIC DESTINATION")) {
            rejected { f.observe(projected = projected) }
        }
        rejected { f.observe(pois = f.pois.map { if (it.key == f.sign.key) it.copy(displayName = f.headline) else it }) }
        rejected { f.observe(pois = f.pois.map { if (it.key == f.sign.key) it.copy(
            destinationBaseAreaId = 0x102, textObligation = LocalMapPoiTextObligation.DESTINATION_NAME) else it }) }
        rejected { f.observe(pois = f.pois.map { if (it.key == f.sign.key) it.copy(
            textObligation = LocalMapPoiTextObligation.UNRESOLVED) else it }) }
    }

    @Test fun rejectsNumericIdentityAndGeometryChanges() {
        val f = Fixture()
        f.observe()
        for (sign in listOf(f.sign.copy(key = "synthetic/other/bg/0"), f.sign.copy(localMapKey = "synthetic/other"),
            f.sign.copy(baseAreaId = 0x102), f.sign.copy(tileX = 0), f.sign.copy(tileY = 0),
            f.sign.copy(kind = LocalMapPoiKind.UNKNOWN))) {
            rejected { f.observe(pois = listOf(sign, f.other)) }
        }
        val map = f.maps.single()
        for (changed in listOf(map.copy(key = "synthetic/other"), map.copy(baseAreaId = 0x102),
            map.copy(pixelWidth = 64), map.copy(pixelHeight = 64), map.copy(gridWidth = 4), map.copy(gridHeight = 4))) {
            rejected { f.observe(maps = listOf(changed)) }
        }
    }

    @Test fun rejectsCollapsedCountsAndInventories() {
        val f = Fixture()
        f.observe()
        for (pois in listOf(f.pois.drop(1), f.pois + f.sign, listOf(f.other))) rejected { f.observe(pois = pois) }
        for (maps in listOf(emptyList(), f.maps + f.maps.single())) rejected { f.observe(maps = maps) }
        for (state in listOf(LocalizedCapabilityState.available(1), LocalizedCapabilityState.available(2),
            LocalizedCapabilityState.notFound("synthetic", 2))) rejected { f.observe(state = state) }
        for (fulfilled in listOf(emptySet(), setOf(f.sign.key, "synthetic/unknown"))) rejected { f.observe(fulfilled = fulfilled) }
    }

    @Test fun rejectsApiNamesIdentityGeometryAndCounts() {
        val f = Fixture()
        f.api()
        val selected = f.views.first()
        for (changed in listOf(selected.copy(displayName = null), selected.copy(displayName = "WRONG SYNTHETIC"),
            selected.copy(key = "synthetic/other/bg/0"), selected.copy(localMapKey = "synthetic/other"),
            selected.copy(baseAreaId = 0x102), selected.copy(tileX = 0), selected.copy(tileY = 0),
            selected.copy(category = "UNKNOWN"), selected.copy(destinationBaseAreaId = 0x102),
            selected.copy(itemId = 5), selected.copy(itemName = f.headline), selected.copy(service = "BUILDING"))) {
            rejected { f.api(views = listOf(changed, f.views.last())) }
        }
        for (views in listOf(f.views.drop(1), f.views + selected)) rejected { f.api(views = views) }
        for (maps in listOf(f.mapViews.drop(1), f.mapViews + f.mapViews.single(),
            listOf(f.mapViews.single().copy(gridHeight = 4)))) rejected { f.api(maps = maps) }
        rejected { f.api(covered = 0) }
        rejected { f.api(expected = 1) }
        rejected { f.api(status = "AVAILABLE") }
    }

    @Test fun rejectsMetadataIdentityRevisionAndCounters() {
        val f = Fixture()
        val valid = f.metadata()
        for (rows in listOf(emptyList(), listOf(valid, valid), listOf(valid.copy(sha256 = "2".repeat(64))),
            listOf(valid.copy(parserSchemaVersion = valid.parserSchemaVersion - 1)),
            listOf(valid.copy(sqlSchemaVersion = valid.sqlSchemaVersion + 1)), listOf(valid.copy(complete = 0)))) {
            rejected { NativeSelectedDirectSignAssertions.metadata(f.expected, rows) }
        }
        for ((reads, parses, reparses) in listOf(Triple(0, 1, 0), Triple(2, 1, 0),
            Triple(1, 0, 0), Triple(1, 2, 0), Triple(1, 1, 1))) {
            rejected { f.receipt(originalReads = reads, originalParses = parses, apiReparses = reparses) }
        }
        rejected { f.receipt(control = "synthetic-wrong") }
        rejected { f.receipt(sha256 = "2".repeat(64)) }
    }

    @Test fun receiptIsScopedAndRequiresEveryBoundary() {
        val f = Fixture()
        val receipt = f.receipt()
        assertEquals(JsonPrimitive("SELECTED_DIRECT_SIGN_ONLY"), receipt["scope"])
        assertEquals(JsonPrimitive("NOT_CLAIMED"), receipt["overallNativeSemanticAcceptance"])
        assertEquals(JsonPrimitive(f.expected.control), receipt["control"])
        assertEquals(JsonPrimitive(f.expected.sha256), receipt["sha256"])
        assertEquals(JsonPrimitive(CatalogSchema.parserSchemaVersion), receipt["parserSchemaVersion"])
        assertEquals(JsonPrimitive(CatalogSchema.version), receipt["sqlSchemaVersion"])
        assertEquals(JsonPrimitive(1), receipt["originalReads"])
        assertEquals(JsonPrimitive(1), receipt["originalParserInvocations"])
        assertEquals(JsonPrimitive(0), receipt["apiReparses"])
        assertEquals(f.observe(), receipt["producer"])
        assertEquals(f.observe(), receipt["sqlite"])
        assertEquals(f.api(), receipt["api"])
        for (boundary in listOf("producer", "sqlite", "api", "metadata")) {
            rejected { f.receipt(missing = boundary) }
        }
        rejected { f.receipt(sqlite = f.observe().apply { addProperty("totalPois", 1) }) }
        rejected { f.receipt(api = f.api().apply { addProperty("headlineSha256", "0".repeat(64)) }) }
    }

    private fun rejected(block: () -> Unit) { assertThrows(AssertionError::class.java, block) }

    private class Fixture {
        val headline = "SYNTHETIC DIRECT SIGN"
        val expected = NativeSelectedDirectSignExpectation("synthetic", "1".repeat(64), "synthetic/map", 0x101,
            3, 2, "synthetic/map/bg/0", 2, 1, NativeSelectedDirectSignAssertions.digest(headline))
        val maps = listOf(LocalMap(expected.mapKey, null, expected.baseAreaId, 48, 32, 3, 2, "synthetic/image"))
        val sign = LocalMapPoi(expected.poiKey, expected.mapKey, expected.baseAreaId, 2, 1, LocalMapPoiKind.PLACE,
            textObligation = LocalMapPoiTextObligation.DIRECT_TEXT)
        val other = sign.copy(key = "synthetic/map/bg/1", tileX = 0, textObligation = LocalMapPoiTextObligation.UNRESOLVED)
        val pois = listOf(sign, other)
        val direct = CatalogPoiText(displayName = CatalogField.available(headline))
        val state = LocalizedCapabilityState(com.enrpau.dualscreendex.parser.model.CapabilityStatus.PARTIAL, 1.0, 1, 2)
        val mapViews = maps.map { LocalMapView(it.key, "SYNTHETIC DESTINATION", it.baseAreaId, it.pixelWidth,
            it.pixelHeight, it.gridWidth, it.gridHeight, "synthetic-image", false) }
        val views = pois.map { LocalMapPoiView(it.key, it.localMapKey, it.baseAreaId, it.tileX, it.tileY,
            "PLACE", "IDENTIFIED", if (it.key == sign.key) headline else null, null, null, null, null) }
        fun observe(maps: List<LocalMap> = this.maps, pois: List<LocalMapPoi> = this.pois,
            direct: CatalogPoiText? = this.direct, projected: String? = headline,
            state: LocalizedCapabilityState = this.state, fulfilled: Set<String> = setOf(sign.key)) =
            NativeSelectedDirectSignAssertions.names(expected, maps, pois, direct, projected, fulfilled, state)
        fun api(views: List<LocalMapPoiView> = this.views, maps: List<LocalMapView> = mapViews,
            status: String = "PARTIAL", covered: Int = 1, expected: Int = 2) =
            NativeSelectedDirectSignAssertions.api(this.expected, observe(), this.maps, pois, maps, views, status, covered, expected)
        fun metadata() = NativeSelectedSignMetadata(expected.sha256, CatalogSchema.parserSchemaVersion.toLong(), CatalogSchema.version.toLong(), 1)
        fun receipt(control: String = expected.control, sha256: String = expected.sha256, missing: String? = null,
            sqlite: JsonObject = observe(), api: JsonObject = api(), originalReads: Int = 1,
            originalParses: Int = 1, apiReparses: Int = 0) = NativeSelectedDirectSignAssertions.receipt(
                expected, control, sha256, if (missing == "producer") null else observe(),
                if (missing == "sqlite") null else sqlite, if (missing == "api") null else api,
                if (missing == "metadata") null else NativeSelectedDirectSignAssertions.metadata(expected, listOf(metadata())),
                originalReads, originalParses, apiReparses)
    }
}
