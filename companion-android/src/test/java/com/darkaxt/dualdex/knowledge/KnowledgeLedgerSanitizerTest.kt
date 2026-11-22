package com.darkaxt.dualdex.knowledge

import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.model.LocalMapPoiPreferences
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.EncounterArea
import com.enrpau.dualscreendex.parser.catalog.EncounterSlot
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoi
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiItem
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiOrganicVisibility
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.PngMapAsset
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog
import com.enrpau.dualscreendex.parser.catalog.WorldMapCell
import com.enrpau.dualscreendex.parser.catalog.WorldMapLocation
import com.enrpau.dualscreendex.parser.catalog.WorldMapRegion
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

class KnowledgeLedgerSanitizerTest {
    @Test
    fun preservesVisitedAreasKnownOnlyToLocalOrWorldMaps() {
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            encounterAreas = listOf(
                EncounterArea(
                    id = 0x0010 * 10 + 1,
                    name = CatalogField.available("Route 101 grass"),
                    methodId = 1,
                    slots = listOf(EncounterSlot(1, 2, 3, 100)),
                ),
            ),
            localMaps = LocalMapCatalog(
                maps = listOf(LocalMap("littleroot", "Littleroot Town", 0x0009, 16, 16, 1, 1, "local/littleroot")),
                assets = mapOf("local/littleroot" to PngMapAsset(PNG_SIGNATURE)),
                pois = listOf(
                    LocalMapPoi(
                        key = "littleroot/bg/0",
                        localMapKey = "littleroot",
                        baseAreaId = 0x0009,
                        tileX = 0,
                        tileY = 0,
                        kind = LocalMapPoiKind.HIDDEN_ITEM,
                        organicVisibility = LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE,
                        item = LocalMapPoiItem(itemId = 13),
                    ),
                ),
            ),
            worldMaps = WorldMapCatalog(
                regions = listOf(
                    WorldMapRegion(
                        "hoenn", "Hoenn", 8, 8, 1, 1, "world/hoenn",
                        listOf(WorldMapLocation("oldale", "Oldale Town", setOf(0x0011), listOf(WorldMapCell(0, 0, 1, 1)))),
                    ),
                ),
                assets = mapOf("world/hoenn" to RgbaSprite(8, 8, IntArray(64))),
            ),
        )
        val preferences = LocalMapPoiPreferences(
            showPlaces = false,
            showCollectedItems = false,
            iconZoomThresholdPercent = 40,
            labelZoomThresholdPercent = 65,
        )
        val ledger = KnowledgeLedger(
            currentAreaBaseId = 0x0009,
            visitedAreaBaseIds = setOf(0x0009, 0x0010, 0x0011, 0x7FFF),
            proximityRevealedPoiKeys = setOf("littleroot/bg/0", "stale/bg/7"),
            identifiedPoiKeys = setOf("littleroot/bg/0", "stale/bg/7"),
            enteredPoiKeys = setOf("stale/warp/0"),
            collectedPoiKeys = setOf("littleroot/bg/0", "stale/object/0"),
            localMapPoiPreferences = preferences,
        )

        val sanitized = KnowledgeLedgerSanitizer.sanitize(ledger, catalog)

        assertEquals(0x0009, sanitized.currentAreaBaseId)
        assertEquals(setOf(0x0009, 0x0010, 0x0011), sanitized.visitedAreaBaseIds)
        assertEquals(setOf("littleroot/bg/0"), sanitized.proximityRevealedPoiKeys)
        assertEquals(setOf("littleroot/bg/0"), sanitized.identifiedPoiKeys)
        assertEquals(emptySet<String>(), sanitized.enteredPoiKeys)
        assertEquals(setOf("littleroot/bg/0"), sanitized.collectedPoiKeys)
        assertEquals(preferences, sanitized.localMapPoiPreferences)
    }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    }
}
