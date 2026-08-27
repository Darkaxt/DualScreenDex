package com.enrpau.dualscreendex.companion.map

import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.companion.model.LocalMapPoiPreferences
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.CatalogRuntimeMetadata
import com.enrpau.dualscreendex.parser.catalog.EncounterArea
import com.enrpau.dualscreendex.parser.catalog.EncounterSlot
import com.enrpau.dualscreendex.parser.catalog.EncounterWindow
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoi
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiItem
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiOrganicVisibility
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiService
import com.enrpau.dualscreendex.parser.catalog.LocalMapScene
import com.enrpau.dualscreendex.parser.catalog.LocalMapScenePlacement
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.PngMapAsset
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AreaGuideBuilderTest {
    @Test
    fun oversizedPointInputFailsBeforeProjectionAllocation() {
        val base = catalog()
        val point = base.localMaps.pois.first()
        val oversized = base.copy(
            localMaps = base.localMaps.copy(
                pois = List(8_193) { index -> point.copy(key = "point-$index") },
            ),
        )

        val failure = assertThrows(AreaGuideProjectionLimitException::class.java) {
            AreaGuideBuilder.project(oversized, AppSnapshot())
        }

        assertEquals("point-input", failure.stage)
        assertEquals(8_193L, failure.observed)
        assertEquals(8_192L, failure.limit)
    }

    @Test
    fun trackedAndManuallySelectableAreasShareOneImmutableProjection() {
        val guide = AreaGuideBuilder.build(
            catalog(),
            organicSnapshot(
                visitedAreaBaseIds = setOf(ROUTE, TOWN),
                seenSpeciesByArea = mapOf(ROUTE to setOf(1), TOWN to setOf(2)),
            ),
        )

        assertEquals(ROUTE, guide.trackedAreaBaseId)
        assertEquals(listOf(ROUTE, TOWN), guide.areas.map { it.baseAreaId })
        assertEquals("Route 101", guide.areas.single { it.baseAreaId == ROUTE }.name)
        assertEquals("Oldale Town", guide.areas.single { it.baseAreaId == TOWN }.name)
        assertEquals(
            listOf(AreaGuideExit(TOWN, "Oldale Town")),
            guide.areas.single { it.baseAreaId == ROUTE }.overview.exits,
        )
    }

    @Test
    fun organicProjectionContainsOnlyAreaObservedEncountersAndKnowledgeVisiblePoints() {
        val guide = AreaGuideBuilder.build(
            catalog(),
            organicSnapshot(
                visitedAreaBaseIds = setOf(ROUTE),
                seenSpeciesByArea = mapOf(ROUTE to setOf(1)),
                identifiedPoiKeys = setOf(HOUSE),
                proximityRevealedPoiKeys = setOf(HOUSE),
            ),
        )

        val area = guide.areas.single()
        assertEquals(listOf(1), area.encounters.flatMap { it.species }.map { it.speciesId })
        assertEquals("Your House", area.placesAndServices.single().label)
        assertEquals(emptyList<AreaGuidePoint>(), area.items)
        assertEquals(1, area.overview.knownPointCount)
        assertNull(area.overview.totalPointCount)
    }

    @Test
    fun discoveredProjectionPublishesAllResolvedFactsWithoutInventingUnsupportedSections() {
        val guide = AreaGuideBuilder.build(
            catalog(),
            AppSnapshot(
                liveAreaBaseId = ROUTE,
                settings = CompanionSettings(knowledgeMode = KnowledgeMode.DISCOVERED),
            ),
        )

        val area = guide.areas.single { it.baseAreaId == ROUTE }
        assertEquals(listOf(1, 2), area.encounters.flatMap { it.species }.map { it.speciesId })
        assertEquals("Your House", area.placesAndServices.single().label)
        assertEquals("Potion", area.items.single().label)
        assertEquals(2, area.overview.totalPointCount)
        assertEquals(emptyList<AreaGuidePoint>(), area.trainersAndPeople)
        assertEquals(emptyList<AreaGuideObjective>(), area.objectives)
    }

    @Test
    fun poiPreferencesFilterGuideRowsWithoutChangingDiscoveryCounts() {
        val guide = AreaGuideBuilder.build(
            catalog(),
            AppSnapshot(
                liveAreaBaseId = ROUTE,
                settings = CompanionSettings(knowledgeMode = KnowledgeMode.DISCOVERED),
                ledger = KnowledgeLedger(
                    localMapPoiPreferences = LocalMapPoiPreferences(showAvailableItems = false),
                ),
            ),
        )

        val area = guide.areas.single { it.baseAreaId == ROUTE }
        assertEquals(emptyList<AreaGuidePoint>(), area.items)
        assertEquals(2, area.overview.knownPointCount)
        assertEquals(2, area.overview.totalPointCount)
    }

    @Test
    fun genericPlaceAndMultilineSignTextNeverReachTheGuideAsPlayerFacingLabels() {
        val guide = AreaGuideBuilder.build(
            catalog(),
            AppSnapshot(
                liveAreaBaseId = ROUTE,
                settings = CompanionSettings(knowledgeMode = KnowledgeMode.DISCOVERED),
            ),
        )

        val route = guide.areas.single { it.baseAreaId == ROUTE }
        assertEquals("Your House", route.placesAndServices.single().label)
        val unnamed = guide.areas.single { it.baseAreaId == EMPTY }.placesAndServices.single()
        assertNull(unnamed.label)
    }

    @Test
    fun objectivesAreAttachedOnlyToTheirKnowledgeVisibleArea() {
        val objective = AreaGuideObjective("open-road", "Open Road")
        val guide = AreaGuideBuilder.project(
            catalog(),
            organicSnapshot(
                visitedAreaBaseIds = setOf(ROUTE, TOWN),
                seenSpeciesByArea = emptyMap(),
            ),
            objectivesByArea = mapOf(ROUTE to listOf(objective)),
        ).guide

        assertEquals(listOf(objective), guide.areas.single { it.baseAreaId == ROUTE }.objectives)
        assertEquals(emptyList<AreaGuideObjective>(), guide.areas.single { it.baseAreaId == TOWN }.objectives)
    }

    private fun organicSnapshot(
        visitedAreaBaseIds: Set<Int>,
        seenSpeciesByArea: Map<Int, Set<Int>>,
        identifiedPoiKeys: Set<String> = emptySet(),
        proximityRevealedPoiKeys: Set<String> = emptySet(),
    ) = AppSnapshot(
        liveAreaBaseId = ROUTE,
        settings = CompanionSettings(knowledgeMode = KnowledgeMode.ORGANIC),
        ledger = KnowledgeLedger(
            visitedAreaBaseIds = visitedAreaBaseIds,
            seenSpeciesByArea = seenSpeciesByArea,
            identifiedPoiKeys = identifiedPoiKeys,
            proximityRevealedPoiKeys = proximityRevealedPoiKeys,
        ),
    )

    private fun catalog(): ParsedCatalog {
        val maps = listOf(
            localMap("route", "Route 101", ROUTE),
            localMap("town", "Oldale Town", TOWN),
            localMap("empty", "Quiet Corner", EMPTY),
        )
        return ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            speciesById = mapOf(1 to species(1, "Poochyena"), 2 to species(2, "Zigzagoon")),
            encounterAreas = listOf(
                EncounterArea(
                    id = ROUTE * 10 + 1,
                    name = CatalogField.available("Route 101 Grass"),
                    methodId = 1,
                    slots = listOf(
                        EncounterSlot(1, 2, 3, 60),
                        EncounterSlot(2, 2, 4, 40),
                    ),
                    windows = setOf(EncounterWindow.DAY, EncounterWindow.NIGHT),
                ),
                EncounterArea(
                    id = TOWN * 10 + 1,
                    name = CatalogField.available("Oldale Town Water"),
                    methodId = 2,
                    slots = listOf(EncounterSlot(2, 3, 5, 100)),
                ),
            ),
            runtimeMetadata = CatalogRuntimeMetadata(
                areaNamesByBaseId = mapOf(ROUTE to "Route 101", TOWN to "Oldale Town", EMPTY to "Quiet Corner"),
            ),
            localMaps = LocalMapCatalog(
                maps = maps,
                assets = maps.associate { it.imageAssetKey to PNG },
                scenes = listOf(
                    LocalMapScene(
                        key = "route-town",
                        gridWidth = 20,
                        gridHeight = 10,
                        placements = listOf(
                            LocalMapScenePlacement("route", ROUTE, 0, 0),
                            LocalMapScenePlacement("town", TOWN, 10, 0),
                        ),
                    ),
                ),
                pois = listOf(
                    LocalMapPoi(
                        key = HOUSE,
                        localMapKey = "route",
                        baseAreaId = ROUTE,
                        tileX = 4,
                        tileY = 4,
                        kind = LocalMapPoiKind.SERVICE,
                        organicVisibility = LocalMapPoiOrganicVisibility.ENTRANCE_PROXIMITY,
                        displayName = "\n{PLAYER}'s House\nA second line that must not appear",
                        service = LocalMapPoiService.BUILDING,
                        destinationBaseAreaId = TOWN,
                    ),
                    LocalMapPoi(
                        key = ITEM,
                        localMapKey = "route",
                        baseAreaId = ROUTE,
                        tileX = 6,
                        tileY = 5,
                        kind = LocalMapPoiKind.HIDDEN_ITEM,
                        organicVisibility = LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE,
                        item = LocalMapPoiItem(itemId = 10, displayName = "Potion", collectionFlagId = 100),
                    ),
                    LocalMapPoi(
                        key = GENERIC,
                        localMapKey = "empty",
                        baseAreaId = EMPTY,
                        tileX = 2,
                        tileY = 2,
                        kind = LocalMapPoiKind.PLACE,
                        displayName = "Place\nUnused details",
                    ),
                ),
            ),
        )
    }

    private fun localMap(key: String, name: String, baseAreaId: Int) = LocalMap(
        key = key,
        displayName = name,
        baseAreaId = baseAreaId,
        pixelWidth = 160,
        pixelHeight = 160,
        gridWidth = 10,
        gridHeight = 10,
        imageAssetKey = "$key.png",
    )

    private fun species(id: Int, name: String) = SpeciesRecord(
        id = id,
        dexNumber = CatalogField.available(id),
        name = CatalogField.available(name),
        typeIds = CatalogField.notFound("fixture"),
        baseStats = CatalogField.notFound("fixture"),
        sprite = CatalogField.notFound("fixture"),
    )

    private companion object {
        const val ROUTE = 0x10
        const val TOWN = 0x11
        const val EMPTY = 0x12
        const val HOUSE = "route/house"
        const val ITEM = "route/hidden-item"
        const val GENERIC = "empty/place"
        val PNG = PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
    }
}
