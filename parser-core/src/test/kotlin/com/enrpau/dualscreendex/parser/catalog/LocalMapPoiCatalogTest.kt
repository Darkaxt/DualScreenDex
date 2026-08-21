package com.enrpau.dualscreendex.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalMapPoiCatalogTest {
    @Test
    fun `mixed POIs validate against their owning map`() {
        val catalog = catalog(
            listOf(
                LocalMapPoi(
                    key = "local/0102/object/3",
                    localMapKey = MAP.key,
                    baseAreaId = MAP.baseAreaId,
                    tileX = 2,
                    tileY = 1,
                    kind = LocalMapPoiKind.VISIBLE_ITEM,
                    item = LocalMapPoiItem(itemId = 13, displayName = "Potion", collectionFlagId = 0x52),
                ),
                LocalMapPoi(
                    key = "local/0102/bg/1",
                    localMapKey = MAP.key,
                    baseAreaId = MAP.baseAreaId,
                    tileX = 3,
                    tileY = 2,
                    kind = LocalMapPoiKind.HIDDEN_ITEM,
                    organicVisibility = LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE,
                    item = LocalMapPoiItem(itemId = 17, displayName = "Antidote", collectionFlagId = 0x5A),
                ),
                LocalMapPoi(
                    key = "local/0102/warp/0",
                    localMapKey = MAP.key,
                    baseAreaId = MAP.baseAreaId,
                    tileX = 1,
                    tileY = 3,
                    kind = LocalMapPoiKind.SERVICE,
                    organicVisibility = LocalMapPoiOrganicVisibility.ENTRANCE_PROXIMITY,
                    service = LocalMapPoiService.MART,
                    destinationBaseAreaId = 0x0201,
                ),
            ),
        )

        assertEquals(3, catalog.pois.size)
    }

    @Test
    fun `POI keys must be globally unique`() {
        val poi = unknownPoi("local/0102/event/0", 0, 0)

        assertThrows(IllegalArgumentException::class.java) {
            catalog(listOf(poi, poi.copy(tileX = 1)))
        }
    }

    @Test
    fun `POIs must remain inside their owning map`() {
        assertThrows(IllegalArgumentException::class.java) {
            catalog(listOf(unknownPoi("local/0102/event/0", MAP.gridWidth, 0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            catalog(listOf(unknownPoi("local/0102/event/1", 0, MAP.gridHeight)))
        }
    }

    @Test
    fun `hidden items require proximity discovery and item metadata`() {
        assertThrows(IllegalArgumentException::class.java) {
            catalog(listOf(
                unknownPoi("local/0102/bg/0", 1, 1).copy(kind = LocalMapPoiKind.HIDDEN_ITEM),
            ))
        }
    }

    @Test
    fun `service and item roles cannot be attached to unrelated POI kinds`() {
        assertThrows(IllegalArgumentException::class.java) {
            catalog(listOf(unknownPoi("local/0102/event/0", 1, 1).copy(service = LocalMapPoiService.GYM)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            catalog(listOf(
                unknownPoi("local/0102/event/1", 1, 1).copy(item = LocalMapPoiItem(itemId = 1)),
            ))
        }
    }

    private fun catalog(pois: List<LocalMapPoi>) = LocalMapCatalog(
        maps = listOf(MAP),
        assets = mapOf(MAP.imageAssetKey to PngMapAsset(PNG_SIGNATURE)),
        pois = pois,
    ).validate()

    private fun unknownPoi(key: String, x: Int, y: Int) = LocalMapPoi(
        key = key,
        localMapKey = MAP.key,
        baseAreaId = MAP.baseAreaId,
        tileX = x,
        tileY = y,
        kind = LocalMapPoiKind.UNKNOWN,
    )

    private companion object {
        val MAP = LocalMap("local/0102", "Route Test", 0x0102, 64, 64, 4, 4, "local/0102/map")
        val PNG_SIGNATURE = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    }
}
