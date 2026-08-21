package com.enrpau.dualscreendex.companion.knowledge

import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoi
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiItem
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiOrganicVisibility
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.PngMapAsset
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMapPoiKnowledgeMapperTest {
    @Test
    fun `same or adjacent tile reveals hidden item silhouette`() {
        val exact = LocalMapPoiKnowledgeMapper.mergeProximity(KnowledgeLedger(), CATALOG, 0x0102, 4, 4)
        val diagonal = LocalMapPoiKnowledgeMapper.mergeProximity(KnowledgeLedger(), CATALOG, 0x0102, 3, 3)

        assertEquals(setOf(HIDDEN_KEY), exact.proximityRevealedPoiKeys)
        assertEquals(setOf(HIDDEN_KEY), diagonal.proximityRevealedPoiKeys)
        assertEquals(emptySet<String>(), exact.identifiedPoiKeys)
    }

    @Test
    fun `two tiles away or another map does not reveal hidden item`() {
        val distant = LocalMapPoiKnowledgeMapper.mergeProximity(KnowledgeLedger(), CATALOG, 0x0102, 2, 4)
        val anotherMap = LocalMapPoiKnowledgeMapper.mergeProximity(KnowledgeLedger(), CATALOG, 0x0103, 4, 4)

        assertEquals(emptySet<String>(), distant.proximityRevealedPoiKeys)
        assertEquals(emptySet<String>(), anotherMap.proximityRevealedPoiKeys)
    }

    @Test
    fun `proximity discovery is monotonic and does not revive collected items`() {
        val previous = KnowledgeLedger(
            proximityRevealedPoiKeys = setOf("local/0102/bg/old"),
            collectedPoiKeys = setOf(HIDDEN_KEY),
        )

        val merged = LocalMapPoiKnowledgeMapper.mergeProximity(previous, CATALOG, 0x0102, 4, 4)

        assertEquals(setOf("local/0102/bg/old"), merged.proximityRevealedPoiKeys)
    }

    private companion object {
        const val HIDDEN_KEY = "local/0102/bg/0"
        val MAP = LocalMap("local/0102", "Route", 0x0102, 160, 160, 10, 10, "local/0102/map")
        val CATALOG = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            localMaps = LocalMapCatalog(
                maps = listOf(MAP),
                assets = mapOf(MAP.imageAssetKey to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))),
                pois = listOf(
                    LocalMapPoi(
                        HIDDEN_KEY,
                        MAP.key,
                        MAP.baseAreaId,
                        4,
                        4,
                        LocalMapPoiKind.HIDDEN_ITEM,
                        LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE,
                        item = LocalMapPoiItem(13),
                    ),
                ),
            ),
        )
    }
}
