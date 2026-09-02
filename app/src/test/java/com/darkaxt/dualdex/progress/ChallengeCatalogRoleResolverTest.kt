package com.darkaxt.dualdex.progress

import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoi
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiItem
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiOrganicVisibility
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.PngMapAsset
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog
import com.enrpau.dualscreendex.parser.catalog.WorldMapCell
import com.enrpau.dualscreendex.parser.catalog.WorldMapLocation
import com.enrpau.dualscreendex.parser.catalog.WorldMapRegion
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeCatalogRoleResolverTest {
    @Test
    fun `catalog structures resolve regional and observable area collectible roles`() {
        val bindings = ChallengeCatalogRoleResolver.resolve(catalog(areaName = "Green Path"))

        assertEquals(setOf(1, 4, 7), bindings.regionalSpeciesIds)
        assertEquals(
            listOf(AreaCollectibleBinding("base-7", "Green Path", setOf("item-a", "item-b"), 7)),
            bindings.areaCollectibles,
        )
        assertTrue(bindings.gymLeaders.isEmpty())
        assertTrue(bindings.provenAdapters.isEmpty())
    }

    @Test
    fun `ambiguous area names and unobservable collection flags fail closed`() {
        val ambiguous = catalog(areaName = "Green Path", runtimeAreaName = "Different Name")
        val bindings = ChallengeCatalogRoleResolver.resolve(ambiguous)

        assertTrue(bindings.areaCollectibles.isEmpty())
    }

    @Test
    fun `unnamed world location does not suppress a structurally named collectible area`() {
        val bindings = ChallengeCatalogRoleResolver.resolve(
            catalog(areaName = "Green Path", includeUnnamedWorldLocation = true),
        )

        assertEquals(
            listOf(AreaCollectibleBinding("base-7", "Green Path", setOf("item-a", "item-b"), 7)),
            bindings.areaCollectibles,
        )
    }

    @Test
    fun `unnamed structural world location retains the existing interface fallback label`() {
        val bindings = ChallengeCatalogRoleResolver.resolve(
            catalog(
                areaName = null,
                runtimeAreaName = null,
                includeUnnamedWorldLocation = true,
            ),
        )

        assertEquals(
            listOf(AreaCollectibleBinding("base-7", "Map section 7", setOf("item-a", "item-b"), 7)),
            bindings.areaCollectibles,
        )
    }

    @Test
    fun `regional role retains numeric species whose localized name is unavailable`() {
        val base = catalog(areaName = "Green Path")
        val bindings = ChallengeCatalogRoleResolver.resolve(
            base.copy(
                speciesById = base.speciesById +
                    (8 to species(8).copy(name = CatalogField.notFound("localized name unavailable"))),
            ),
        )

        assertEquals(setOf(1, 4, 7, 8), bindings.regionalSpeciesIds)
    }

    private fun catalog(
        areaName: String?,
        runtimeAreaName: String? = areaName,
        includeUnnamedWorldLocation: Boolean = false,
    ): ParsedCatalog {
        val map = LocalMap("map-7", areaName, 7, 16, 16, 1, 1, "asset-7")
        return ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            speciesById = listOf(1, 4, 7).associateWith(::species),
            runtimeMetadata = com.enrpau.dualscreendex.parser.catalog.CatalogRuntimeMetadata(
                areaNamesByBaseId = runtimeAreaName?.let { mapOf(7 to it) }.orEmpty(),
            ),
            localMaps = LocalMapCatalog(
                maps = listOf(map),
                assets = mapOf("asset-7" to PngMapAsset(PNG_SIGNATURE)),
                pois = listOf(
                    item("item-a", hidden = false, flag = 10),
                    item("item-b", hidden = true, flag = 11),
                    item("unobservable", hidden = false, flag = null),
                ),
            ),
            worldMaps = if (includeUnnamedWorldLocation) {
                WorldMapCatalog(
                    regions = listOf(
                        WorldMapRegion(
                            "region-0",
                            null,
                            8,
                            8,
                            1,
                            1,
                            "world/region-0",
                            listOf(WorldMapLocation("section-7", null, setOf(7), listOf(WorldMapCell(0, 0, 1, 1)))),
                        ),
                    ),
                    assets = mapOf("world/region-0" to RgbaSprite(8, 8, IntArray(64))),
                )
            } else {
                WorldMapCatalog()
            },
        )
    }

    private fun item(key: String, hidden: Boolean, flag: Int?) = LocalMapPoi(
        key = key,
        localMapKey = "map-7",
        baseAreaId = 7,
        tileX = 0,
        tileY = 0,
        kind = if (hidden) LocalMapPoiKind.HIDDEN_ITEM else LocalMapPoiKind.VISIBLE_ITEM,
        organicVisibility = if (hidden) {
            LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE
        } else {
            LocalMapPoiOrganicVisibility.VISIBLE
        },
        item = LocalMapPoiItem(itemId = 1, collectionFlagId = flag),
    )

    private fun species(id: Int) = SpeciesRecord(
        id = id,
        dexNumber = CatalogField.available(id),
        name = CatalogField.available("Species $id"),
        typeIds = CatalogField.available(emptyList()),
        baseStats = CatalogField.notFound("fixture"),
        sprite = CatalogField.notFound("fixture"),
    )

    companion object {
        private val PNG_SIGNATURE = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    }
}
