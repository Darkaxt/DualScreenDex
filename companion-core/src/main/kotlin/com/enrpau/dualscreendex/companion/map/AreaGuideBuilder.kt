package com.enrpau.dualscreendex.companion.map

import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.companion.model.LocalMapPoiPreferences
import com.enrpau.dualscreendex.parser.catalog.EncounterArea
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoi
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiOrganicVisibility
import com.enrpau.dualscreendex.parser.catalog.LocalMapScenePlacement
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog

object AreaGuideBuilder {
    fun project(catalog: ParsedCatalog, snapshot: AppSnapshot): AreaGuideProjection {
        val names = areaNames(catalog)
        val projectedPoints = projectPoints(catalog, snapshot, names)
        return AreaGuideProjection(
            points = projectedPoints,
            guide = build(catalog, snapshot, names, projectedPoints),
        )
    }

    fun build(catalog: ParsedCatalog, snapshot: AppSnapshot): AreaGuide = project(catalog, snapshot).guide

    private fun build(
        catalog: ParsedCatalog,
        snapshot: AppSnapshot,
        names: Map<Int, String>,
        projectedPoints: List<AreaGuidePoint>,
    ): AreaGuide {
        val allAreaIds = buildSet {
            addAll(names.keys)
            addAll(catalog.encounterAreas.map { it.id / 10 })
            addAll(catalog.localMaps.pois.map(LocalMapPoi::baseAreaId))
            snapshot.liveAreaBaseId?.let(::add)
        }
        val visibleAreaIds = when (snapshot.settings.knowledgeMode) {
            KnowledgeMode.DISCOVERED -> allAreaIds
            KnowledgeMode.ORGANIC -> (
                snapshot.ledger.visitedAreaBaseIds +
                    snapshot.ledger.seenSpeciesByArea.keys +
                    listOfNotNull(snapshot.liveAreaBaseId)
                ).intersect(allAreaIds)
            KnowledgeMode.HIDDEN -> setOfNotNull(snapshot.liveAreaBaseId).intersect(allAreaIds)
        }
        val mapsByKey = catalog.localMaps.maps.associateBy(LocalMap::key)
        val areas = visibleAreaIds.sorted().mapNotNull { baseAreaId ->
            val name = names[baseAreaId] ?: return@mapNotNull null
            val knownPoints = projectedPoints.filter { it.baseAreaId == baseAreaId }
            val visiblePoints = knownPoints.filter { pointEnabled(it, snapshot.ledger.localMapPoiPreferences) }
            val staticPointCount = catalog.localMaps.pois.count { it.baseAreaId == baseAreaId }
            AreaGuideArea(
                baseAreaId = baseAreaId,
                name = name,
                overview = AreaGuideOverview(
                    knownPointCount = knownPoints.size,
                    totalPointCount = staticPointCount.takeIf {
                        snapshot.settings.knowledgeMode == KnowledgeMode.DISCOVERED
                    },
                    collectedItemCount = knownPoints.count { it.state == AreaGuidePointState.COLLECTED },
                    exits = exits(
                        catalog = catalog,
                        baseAreaId = baseAreaId,
                        names = names,
                        visibleAreaIds = visibleAreaIds,
                        points = knownPoints,
                        mapsByKey = mapsByKey,
                    ),
                ),
                encounters = encounterGroups(catalog, snapshot, baseAreaId, name),
                placesAndServices = visiblePoints.filter {
                    it.category == AreaGuidePointCategory.PLACE ||
                        it.category == AreaGuidePointCategory.SERVICE ||
                        it.category == AreaGuidePointCategory.UNKNOWN
                },
                trainersAndPeople = emptyList(),
                items = visiblePoints.filter {
                    it.category == AreaGuidePointCategory.AVAILABLE_ITEM ||
                        it.category == AreaGuidePointCategory.COLLECTED_ITEM
                },
                objectives = emptyList(),
            )
        }
        return AreaGuide(snapshot.liveAreaBaseId, areas)
    }

    fun projectPoints(
        catalog: ParsedCatalog,
        snapshot: AppSnapshot,
        names: Map<Int, String> = areaNames(catalog),
    ): List<AreaGuidePoint> = catalog.localMaps.pois.mapNotNull { poi ->
        val resolvedCollected = poi.item?.collectionFlagId in snapshot.resolvedEventFlags.orEmpty()
        val collected = resolvedCollected || poi.key in snapshot.ledger.collectedPoiKeys
        val explicitlyIdentified = resolvedCollected ||
            poi.key in snapshot.ledger.identifiedPoiKeys ||
            poi.key in snapshot.ledger.enteredPoiKeys
        val proximityRevealed = poi.key in snapshot.ledger.proximityRevealedPoiKeys
        val identified = snapshot.settings.knowledgeMode == KnowledgeMode.DISCOVERED || collected || explicitlyIdentified
        val visibleWithoutDiscovery = poi.organicVisibility == LocalMapPoiOrganicVisibility.VISIBLE
        val included = when (snapshot.settings.knowledgeMode) {
            KnowledgeMode.HIDDEN -> false
            KnowledgeMode.DISCOVERED -> true
            KnowledgeMode.ORGANIC -> visibleWithoutDiscovery || proximityRevealed || identified
        }
        if (!included) return@mapNotNull null
        val state = when {
            collected -> AreaGuidePointState.COLLECTED
            identified -> AreaGuidePointState.IDENTIFIED
            else -> AreaGuidePointState.SILHOUETTE
        }
        val category = when {
            collected -> AreaGuidePointCategory.COLLECTED_ITEM
            poi.kind == LocalMapPoiKind.VISIBLE_ITEM || poi.kind == LocalMapPoiKind.HIDDEN_ITEM ->
                AreaGuidePointCategory.AVAILABLE_ITEM
            poi.kind == LocalMapPoiKind.SERVICE -> AreaGuidePointCategory.SERVICE
            poi.kind == LocalMapPoiKind.PLACE -> AreaGuidePointCategory.PLACE
            else -> AreaGuidePointCategory.UNKNOWN
        }
        val trainer = snapshot.trainerCardState?.identity
        val label = when {
            !identified -> null
            category == AreaGuidePointCategory.AVAILABLE_ITEM || category == AreaGuidePointCategory.COLLECTED_ITEM ->
                normalizeText(poi.item?.displayName, trainer?.name, names[poi.baseAreaId])
            else -> normalizeText(poiName(poi, trainer?.gender), trainer?.name, names[poi.baseAreaId])
        }
        AreaGuidePoint(
            key = poi.key,
            localMapKey = poi.localMapKey,
            baseAreaId = poi.baseAreaId,
            tileX = poi.tileX,
            tileY = poi.tileY,
            category = category,
            state = state,
            label = label,
            service = poi.service?.name,
            itemId = poi.item?.itemId.takeIf { identified },
            destinationBaseAreaId = poi.destinationBaseAreaId.takeIf { identified },
        )
    }

    private fun encounterGroups(
        catalog: ParsedCatalog,
        snapshot: AppSnapshot,
        baseAreaId: Int,
        areaName: String,
    ): List<AreaGuideEncounterGroup> {
        val permittedSpecies = when (snapshot.settings.knowledgeMode) {
            KnowledgeMode.DISCOVERED -> null
            KnowledgeMode.ORGANIC -> snapshot.ledger.seenSpeciesByArea[baseAreaId].orEmpty()
            KnowledgeMode.HIDDEN -> emptySet()
        }
        return catalog.encounterAreas
            .filter { it.id / 10 == baseAreaId }
            .sortedBy(EncounterArea::id)
            .mapNotNull { encounterArea ->
                val species = encounterArea.slots
                    .groupBy { it.speciesId }
                    .mapNotNull { (speciesId, slots) ->
                        if (permittedSpecies != null && speciesId !in permittedSpecies) return@mapNotNull null
                        val speciesName = catalog.speciesById[speciesId]?.name?.value
                            ?.let { normalizeText(it, null, null) }
                            ?: return@mapNotNull null
                        AreaGuideEncounterSpecies(
                            speciesId = speciesId,
                            name = speciesName,
                            minimumLevel = slots.minOf { it.minimumLevel },
                            maximumLevel = slots.maxOf { it.maximumLevel },
                            ratePercent = slots.map { it.weight }.takeIf { weights -> weights.all { it != null } }
                                ?.sumOf { requireNotNull(it) }
                                ?.coerceIn(0, 100),
                        )
                    }
                    .sortedWith(compareBy(AreaGuideEncounterSpecies::name, AreaGuideEncounterSpecies::speciesId))
                if (species.isEmpty()) return@mapNotNull null
                val sourceName = encounterArea.name.value?.let { normalizeText(it, null, null) }
                val qualifier = sourceName
                    ?.removePrefix(areaName)
                    ?.trim(' ', '-', ':')
                    ?.takeIf(String::isNotBlank)
                AreaGuideEncounterGroup(
                    name = qualifier,
                    windows = encounterArea.windows.map { it.name }.sorted(),
                    species = species,
                )
            }
    }

    private fun exits(
        catalog: ParsedCatalog,
        baseAreaId: Int,
        names: Map<Int, String>,
        visibleAreaIds: Set<Int>,
        points: List<AreaGuidePoint>,
        mapsByKey: Map<String, LocalMap>,
    ): List<AreaGuideExit> {
        val destinationIds = buildSet {
            addAll(points.mapNotNull(AreaGuidePoint::destinationBaseAreaId))
            catalog.localMaps.scenes.forEach { scene ->
                val anchors = scene.placements.filter { it.baseAreaId == baseAreaId }
                anchors.forEach { anchor ->
                    scene.placements
                        .filter { it !== anchor && shareEdge(anchor, it, mapsByKey) }
                        .mapTo(this) { it.baseAreaId }
                }
            }
        }
        return destinationIds
            .asSequence()
            .filter { it != baseAreaId && it in visibleAreaIds }
            .mapNotNull { destination -> names[destination]?.let { AreaGuideExit(destination, it) } }
            .distinctBy(AreaGuideExit::baseAreaId)
            .sortedWith(compareBy(AreaGuideExit::name, AreaGuideExit::baseAreaId))
            .toList()
    }

    private fun shareEdge(
        left: LocalMapScenePlacement,
        right: LocalMapScenePlacement,
        mapsByKey: Map<String, LocalMap>,
    ): Boolean {
        val leftMap = mapsByKey[left.localMapKey] ?: return false
        val rightMap = mapsByKey[right.localMapKey] ?: return false
        val horizontalEdge = left.gridX + leftMap.gridWidth == right.gridX ||
            right.gridX + rightMap.gridWidth == left.gridX
        val verticalOverlap = minOf(left.gridY + leftMap.gridHeight, right.gridY + rightMap.gridHeight) -
            maxOf(left.gridY, right.gridY)
        val verticalEdge = left.gridY + leftMap.gridHeight == right.gridY ||
            right.gridY + rightMap.gridHeight == left.gridY
        val horizontalOverlap = minOf(left.gridX + leftMap.gridWidth, right.gridX + rightMap.gridWidth) -
            maxOf(left.gridX, right.gridX)
        return horizontalEdge && verticalOverlap > 0 || verticalEdge && horizontalOverlap > 0
    }

    private fun pointEnabled(point: AreaGuidePoint, preferences: LocalMapPoiPreferences): Boolean = when (point.category) {
        AreaGuidePointCategory.PLACE -> preferences.showPlaces
        AreaGuidePointCategory.SERVICE -> preferences.showServices
        AreaGuidePointCategory.AVAILABLE_ITEM -> preferences.showAvailableItems
        AreaGuidePointCategory.COLLECTED_ITEM -> preferences.showCollectedItems
        AreaGuidePointCategory.UNKNOWN -> preferences.showUnknownPois
    }

    private fun poiName(poi: LocalMapPoi, trainerGender: Int?): String? =
        trainerGender?.let(poi.displayNamesByTrainerGender::get)
            ?: poi.displayName
            ?: poi.displayNamesByTrainerGender.toSortedMap().values.firstOrNull()

    private fun areaNames(catalog: ParsedCatalog): Map<Int, String> = buildMap {
        catalog.encounterAreas.forEach { area ->
            area.name.value?.let { normalizeText(it, null, null) }?.let { putIfAbsent(area.id / 10, it) }
        }
        catalog.worldMaps.regions.forEach { region ->
            region.locations.forEach { location ->
                location.baseAreaIds.forEach { put(it, location.displayName) }
            }
        }
        catalog.localMaps.maps.forEach { map ->
            map.displayName?.let { normalizeText(it, null, null) }?.let { put(map.baseAreaId, it) }
        }
        catalog.runtimeMetadata.areaNamesByBaseId.forEach { (baseAreaId, name) ->
            normalizeText(name, null, null)?.let { put(baseAreaId, it) }
        }
    }

    private fun normalizeText(template: String?, trainerName: String?, areaName: String?): String? {
        val meaningful = template
            ?.replace("\r\n", "\n")
            ?.replace('\r', '\n')
            ?.lineSequence()
            ?.map { it.trim().replace(Regex("\\s+"), " ") }
            ?.firstOrNull(String::isNotBlank)
            ?: return null
        val resolvedName = trainerName?.trim()?.takeIf(String::isNotBlank)
        val substituted = if (resolvedName != null) {
            meaningful.replace("{PLAYER}", resolvedName, ignoreCase = true)
        } else {
            meaningful
                .replace("{PLAYER}'s", "Your", ignoreCase = true)
                .replace("{PLAYER}’s", "Your", ignoreCase = true)
                .replace("{PLAYER}", "You", ignoreCase = true)
        }
        return substituted.takeUnless {
            it.equals("Place", ignoreCase = true) || areaName?.let { area -> it.equals(area, ignoreCase = true) } == true
        }
    }
}
