package com.enrpau.dualscreendex.companion.map

import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.companion.model.LocalMapPoiPreferences
import com.enrpau.dualscreendex.parser.catalog.EncounterArea
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoi
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiOrganicVisibility
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog

object AreaGuideBuilder {
    fun project(
        catalog: ParsedCatalog,
        snapshot: AppSnapshot,
        objectivesByArea: Map<Int, List<AreaGuideObjective>> = emptyMap(),
    ): AreaGuideProjection {
        requireBoundedInput(catalog, objectivesByArea)
        val outputBudget = OutputBudget()
        val names = areaNames(catalog)
        requireAtMost("area-count", names.size.toLong(), MAX_AREA_COUNT)
        val projectedPoints = projectPoints(catalog, snapshot, names, outputBudget)
        val guide = build(catalog, snapshot, names, projectedPoints, objectivesByArea, outputBudget)
        return AreaGuideProjection(
            points = projectedPoints,
            guide = guide,
        )
    }

    fun build(catalog: ParsedCatalog, snapshot: AppSnapshot): AreaGuide = project(catalog, snapshot).guide

    private fun build(
        catalog: ParsedCatalog,
        snapshot: AppSnapshot,
        names: Map<Int, String>,
        projectedPoints: List<AreaGuidePoint>,
        objectivesByArea: Map<Int, List<AreaGuideObjective>>,
        outputBudget: OutputBudget,
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
        val pointsByArea = projectedPoints.groupBy(AreaGuidePoint::baseAreaId)
        val staticPointCounts = catalog.localMaps.pois.groupingBy(LocalMapPoi::baseAreaId).eachCount()
        val sceneAdjacency = sceneAdjacency(catalog, mapsByKey)
        val areas = ArrayList<AreaGuideArea>(visibleAreaIds.size)
        visibleAreaIds.sorted().forEach { baseAreaId ->
            val name = names[baseAreaId] ?: return@forEach
            val knownPoints = pointsByArea[baseAreaId].orEmpty()
            val placesAndServices = ArrayList<AreaGuidePoint>()
            val items = ArrayList<AreaGuidePoint>()
            knownPoints.forEach { point ->
                if (!pointEnabled(point, snapshot.ledger.localMapPoiPreferences)) return@forEach
                when (point.category) {
                    AreaGuidePointCategory.PLACE,
                    AreaGuidePointCategory.SERVICE,
                    AreaGuidePointCategory.UNKNOWN -> {
                        outputBudget.retain()
                        placesAndServices += point
                    }
                    AreaGuidePointCategory.AVAILABLE_ITEM,
                    AreaGuidePointCategory.COLLECTED_ITEM -> {
                        outputBudget.retain()
                        items += point
                    }
                }
            }
            val objectives = objectivesByArea[baseAreaId].orEmpty()
            outputBudget.retain(objectives.size)
            val overview = AreaGuideOverview(
                knownPointCount = knownPoints.size,
                totalPointCount = staticPointCounts[baseAreaId]?.takeIf {
                    snapshot.settings.knowledgeMode == KnowledgeMode.DISCOVERED
                },
                collectedItemCount = knownPoints.count { it.state == AreaGuidePointState.COLLECTED },
                exits = exits(
                    baseAreaId = baseAreaId,
                    names = names,
                    visibleAreaIds = visibleAreaIds,
                    points = knownPoints,
                    sceneAdjacency = sceneAdjacency,
                    outputBudget = outputBudget,
                ),
            )
            val encounters = encounterGroups(catalog, snapshot, baseAreaId, name, outputBudget)
            outputBudget.retain()
            areas += AreaGuideArea(
                baseAreaId = baseAreaId,
                name = name,
                overview = overview,
                encounters = encounters,
                placesAndServices = placesAndServices,
                trainersAndPeople = emptyList(),
                items = items,
                objectives = objectives,
            )
        }
        return AreaGuide(snapshot.liveAreaBaseId, areas)
    }

    private fun projectPoints(
        catalog: ParsedCatalog,
        snapshot: AppSnapshot,
        names: Map<Int, String> = areaNames(catalog),
        outputBudget: OutputBudget? = null,
    ): List<AreaGuidePoint> = buildList {
        catalog.localMaps.pois.forEach { poi ->
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
        if (!included) return@forEach
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
        outputBudget?.retain()
        add(AreaGuidePoint(
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
        ))
        }
    }

    private fun encounterGroups(
        catalog: ParsedCatalog,
        snapshot: AppSnapshot,
        baseAreaId: Int,
        areaName: String,
        outputBudget: OutputBudget,
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
                        outputBudget.retain()
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
                outputBudget.retain()
                AreaGuideEncounterGroup(
                    name = qualifier,
                    windows = encounterArea.windows.map { it.name }.sorted(),
                    species = species,
                )
            }
    }

    private fun exits(
        baseAreaId: Int,
        names: Map<Int, String>,
        visibleAreaIds: Set<Int>,
        points: List<AreaGuidePoint>,
        sceneAdjacency: Map<Int, Set<Int>>,
        outputBudget: OutputBudget,
    ): List<AreaGuideExit> {
        val destinationIds = buildSet {
            addAll(points.mapNotNull(AreaGuidePoint::destinationBaseAreaId))
            addAll(sceneAdjacency[baseAreaId].orEmpty())
        }
        return destinationIds
            .asSequence()
            .filter { it != baseAreaId && it in visibleAreaIds }
            .mapNotNull { destination -> names[destination]?.let { destination to it } }
            .distinctBy { it.first }
            .sortedWith(compareBy({ it.second }, { it.first }))
            .map { (destination, name) ->
                outputBudget.retain()
                AreaGuideExit(destination, name)
            }
            .toList()
    }

    private data class SceneEdge(
        val baseAreaId: Int,
        val start: Int,
        val end: Int,
    )

    private fun sceneAdjacency(
        catalog: ParsedCatalog,
        mapsByKey: Map<String, LocalMap>,
    ): Map<Int, Set<Int>> {
        val leftEdges = mutableMapOf<Int, MutableList<SceneEdge>>()
        val rightEdges = mutableMapOf<Int, MutableList<SceneEdge>>()
        val topEdges = mutableMapOf<Int, MutableList<SceneEdge>>()
        val bottomEdges = mutableMapOf<Int, MutableList<SceneEdge>>()
        catalog.localMaps.scenes.forEach { scene ->
            scene.placements.forEach { placement ->
                val map = mapsByKey[placement.localMapKey] ?: return@forEach
                val horizontal = SceneEdge(placement.baseAreaId, placement.gridY, placement.gridY + map.gridHeight)
                val vertical = SceneEdge(placement.baseAreaId, placement.gridX, placement.gridX + map.gridWidth)
                leftEdges.getOrPut(placement.gridX, ::mutableListOf).add(horizontal)
                rightEdges.getOrPut(placement.gridX + map.gridWidth, ::mutableListOf).add(horizontal)
                topEdges.getOrPut(placement.gridY, ::mutableListOf).add(vertical)
                bottomEdges.getOrPut(placement.gridY + map.gridHeight, ::mutableListOf).add(vertical)
            }
        }
        val adjacency = mutableMapOf<Int, MutableSet<Int>>()
        var relationships = 0L
        rightEdges.forEach { (coordinate, right) ->
            relationships = connectAdjacentEdges(right, leftEdges[coordinate].orEmpty(), adjacency, relationships)
        }
        bottomEdges.forEach { (coordinate, bottom) ->
            relationships = connectAdjacentEdges(bottom, topEdges[coordinate].orEmpty(), adjacency, relationships)
        }
        return adjacency.mapValues { (_, destinations) -> destinations.toSet() }
    }

    private fun connectAdjacentEdges(
        first: List<SceneEdge>,
        second: List<SceneEdge>,
        adjacency: MutableMap<Int, MutableSet<Int>>,
        relationships: Long,
    ): Long {
        if (first.isEmpty() || second.isEmpty()) return relationships
        val active = ArrayList<SceneEdge>()
        val orderedFirst = first.sortedBy(SceneEdge::start)
        val orderedSecond = second.sortedBy(SceneEdge::start)
        var nextSecond = 0
        var retainedRelationships = relationships
        orderedFirst.forEach { source ->
            while (nextSecond < orderedSecond.size && orderedSecond[nextSecond].start < source.end) {
                active += orderedSecond[nextSecond]
                nextSecond += 1
            }
            active.removeAll { candidate -> candidate.end <= source.start }
            active.forEach { candidate ->
                if (source.baseAreaId == candidate.baseAreaId || candidate.baseAreaId in adjacency[source.baseAreaId].orEmpty()) {
                    return@forEach
                }
                requireAtMost("scene-adjacency", retainedRelationships + 1, MAX_SCENE_ADJACENCIES)
                adjacency.getOrPut(source.baseAreaId, ::linkedSetOf).add(candidate.baseAreaId)
                adjacency.getOrPut(candidate.baseAreaId, ::linkedSetOf).add(source.baseAreaId)
                retainedRelationships += 1
            }
        }
        return retainedRelationships
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

    private fun requireBoundedInput(
        catalog: ParsedCatalog,
        objectivesByArea: Map<Int, List<AreaGuideObjective>>,
    ) {
        requireAtMost("point-input", catalog.localMaps.pois.size.toLong(), MAX_POINT_COUNT)
        requireAtMost("encounter-input", catalog.encounterAreas.size.toLong(), MAX_ENCOUNTER_AREA_COUNT)
        requireAtMost(
            "encounter-slot-input",
            catalog.encounterAreas.sumOf { it.slots.size.toLong() },
            MAX_ENCOUNTER_SLOT_COUNT,
        )
        requireAtMost(
            "scene-placement-input",
            catalog.localMaps.scenes.sumOf { it.placements.size.toLong() },
            MAX_SCENE_PLACEMENT_COUNT,
        )
        requireAtMost(
            "objective-input",
            objectivesByArea.values.sumOf { it.size.toLong() },
            MAX_OBJECTIVE_COUNT,
        )
    }

    private class OutputBudget {
        private var retained = 0L

        fun retain(count: Int = 1) {
            val additional = count.toLong()
            requireAtMost("retained-output", retained + additional, MAX_RETAINED_ITEMS)
            retained += additional
        }
    }

    private fun requireAtMost(stage: String, observed: Long, limit: Long) {
        if (observed > limit) throw AreaGuideProjectionLimitException(stage, observed, limit)
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

    private const val MAX_AREA_COUNT = 2_048L
    private const val MAX_POINT_COUNT = 8_192L
    private const val MAX_ENCOUNTER_AREA_COUNT = 8_192L
    private const val MAX_ENCOUNTER_SLOT_COUNT = 32_768L
    private const val MAX_SCENE_PLACEMENT_COUNT = 16_384L
    private const val MAX_SCENE_ADJACENCIES = 65_536L
    private const val MAX_OBJECTIVE_COUNT = 8_192L
    private const val MAX_RETAINED_ITEMS = 65_536L
}
