package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapScene
import com.enrpau.dualscreendex.parser.catalog.LocalMapScenePlacement

internal data class LocalMapSceneConstraint(
    val sourceId: Int,
    val targetId: Int,
    val deltaX: Int,
    val deltaY: Int,
)

internal object LocalMapSceneBuilder {
    fun build(
        maps: List<LocalMap>,
        constraints: List<LocalMapSceneConstraint>,
    ): List<LocalMapScene> {
        val mapsById = maps.associateBy(LocalMap::baseAreaId)
        val canonicalConstraints = constraints
            .filter { it.sourceId in mapsById && it.targetId in mapsById }
            .groupBy { constraint -> ConnectionKey.of(constraint.sourceId, constraint.targetId) }
            .mapNotNull { (key, pairConstraints) ->
                val deltas = pairConstraints.map { constraint ->
                    if (constraint.sourceId == key.firstId) {
                        Point(constraint.deltaX, constraint.deltaY)
                    } else {
                        Point(-constraint.deltaX, -constraint.deltaY)
                    }
                }.distinct()
                deltas.singleOrNull()?.let { delta -> Constraint(key.firstId, key.secondId, delta) }
            }
        if (canonicalConstraints.isEmpty()) return emptyList()

        val adjacency = mutableMapOf<Int, MutableList<Edge>>()
        canonicalConstraints.forEach { constraint ->
            adjacency.getOrPut(constraint.sourceId, ::mutableListOf) += Edge(constraint.targetId, constraint.delta)
            adjacency.getOrPut(constraint.targetId, ::mutableListOf) += Edge(
                constraint.sourceId,
                Point(-constraint.delta.x, -constraint.delta.y),
            )
        }

        val remaining = adjacency.keys.toSortedSet()
        val scenes = mutableListOf<LocalMapScene>()
        while (remaining.isNotEmpty()) {
            val root = remaining.first()
            val component = connectedComponent(root, adjacency, remaining)
            val scene = placeComponent(root, component, adjacency, mapsById)
            if (scene == null) {
                remaining.remove(root)
            } else {
                scenes += scene
                remaining.removeAll(scene.placements.map(LocalMapScenePlacement::baseAreaId).toSet())
            }
        }
        return scenes.sortedBy(LocalMapScene::key)
    }

    private fun connectedComponent(
        root: Int,
        adjacency: Map<Int, List<Edge>>,
        allowed: Set<Int>,
    ): Set<Int> {
        val reached = linkedSetOf(root)
        val pending = ArrayDeque<Int>()
        pending += root
        while (pending.isNotEmpty()) {
            adjacency[pending.removeFirst()].orEmpty().forEach { edge ->
                if (edge.targetId in allowed && reached.add(edge.targetId)) pending += edge.targetId
            }
        }
        return reached
    }

    private fun placeComponent(
        root: Int,
        component: Set<Int>,
        adjacency: Map<Int, List<Edge>>,
        mapsById: Map<Int, LocalMap>,
    ): LocalMapScene? {
        val positions = linkedMapOf(root to Point(0, 0))
        val pending = ArrayDeque<Int>()
        pending += root
        while (pending.isNotEmpty()) {
            val sourceId = pending.removeFirst()
            val source = positions.getValue(sourceId)
            adjacency[sourceId].orEmpty().sortedBy(Edge::targetId).forEach { edge ->
                val candidate = Point(source.x + edge.delta.x, source.y + edge.delta.y)
                if (
                    edge.targetId in component &&
                    edge.targetId !in positions &&
                    !overlapsPlacedMap(edge.targetId, candidate, positions, mapsById)
                ) {
                    positions[edge.targetId] = candidate
                    pending += edge.targetId
                }
            }
        }
        if (positions.size < 2) return null

        val minimumX = positions.minOf { (_, point) -> point.x }
        val minimumY = positions.minOf { (_, point) -> point.y }
        val normalized = positions.mapValues { (_, point) -> Point(point.x - minimumX, point.y - minimumY) }
        val placements = normalized.toSortedMap().map { (baseAreaId, point) ->
            LocalMapScenePlacement(
                localMapKey = mapsById.getValue(baseAreaId).key,
                baseAreaId = baseAreaId,
                gridX = point.x,
                gridY = point.y,
            )
        }
        if (hasOverlap(placements, mapsById)) return null
        val gridWidth = placements.maxOf { placement ->
            placement.gridX + mapsById.getValue(placement.baseAreaId).gridWidth
        }
        val gridHeight = placements.maxOf { placement ->
            placement.gridY + mapsById.getValue(placement.baseAreaId).gridHeight
        }
        if (gridWidth !in 1..MAX_SCENE_DIMENSION || gridHeight !in 1..MAX_SCENE_DIMENSION) return null
        return LocalMapScene(
            key = "scene/${component.min().toString(16).padStart(4, '0')}",
            gridWidth = gridWidth,
            gridHeight = gridHeight,
            placements = placements,
        )
    }

    private fun overlapsPlacedMap(
        candidateId: Int,
        candidate: Point,
        positions: Map<Int, Point>,
        mapsById: Map<Int, LocalMap>,
    ): Boolean {
        val candidateMap = mapsById.getValue(candidateId)
        return positions.any { (placedId, placed) ->
            val placedMap = mapsById.getValue(placedId)
            candidate.x < placed.x + placedMap.gridWidth &&
                placed.x < candidate.x + candidateMap.gridWidth &&
                candidate.y < placed.y + placedMap.gridHeight &&
                placed.y < candidate.y + candidateMap.gridHeight
        }
    }

    private fun hasOverlap(
        placements: List<LocalMapScenePlacement>,
        mapsById: Map<Int, LocalMap>,
    ): Boolean = placements.indices.any { index ->
        val first = placements[index]
        val firstMap = mapsById.getValue(first.baseAreaId)
        placements.drop(index + 1).any { second ->
            val secondMap = mapsById.getValue(second.baseAreaId)
            first.gridX < second.gridX + secondMap.gridWidth &&
                second.gridX < first.gridX + firstMap.gridWidth &&
                first.gridY < second.gridY + secondMap.gridHeight &&
                second.gridY < first.gridY + firstMap.gridHeight
        }
    }

    private data class Constraint(val sourceId: Int, val targetId: Int, val delta: Point)

    private data class ConnectionKey(val firstId: Int, val secondId: Int) {
        companion object {
            fun of(firstId: Int, secondId: Int): ConnectionKey =
                if (firstId < secondId) ConnectionKey(firstId, secondId) else ConnectionKey(secondId, firstId)
        }
    }

    private data class Edge(val targetId: Int, val delta: Point)
    private data class Point(val x: Int, val y: Int)

    private const val MAX_SCENE_DIMENSION = 8192
}
