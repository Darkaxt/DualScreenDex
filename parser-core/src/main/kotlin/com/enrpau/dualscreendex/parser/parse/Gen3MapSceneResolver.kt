package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapScene
import com.enrpau.dualscreendex.parser.catalog.LocalMapScenePlacement
import com.enrpau.dualscreendex.parser.io.RomImage

/** Builds fail-closed Local-map scenes from compiled Gen III cardinal connections. */
internal object Gen3MapSceneResolver {
    fun resolve(
        rom: RomImage,
        headers: Map<Int, Int>,
        maps: List<LocalMap>,
    ): List<LocalMapScene> {
        val mapsById = maps.associateBy(LocalMap::baseAreaId)
        val invalidSources = mutableSetOf<Int>()
        val constraints = buildList {
            headers.toSortedMap().forEach { (sourceId, header) ->
                if (sourceId !in mapsById) return@forEach
                val connections = readConnections(rom, header)
                if (connections == null) {
                    invalidSources += sourceId
                } else {
                    connections.filter { it.destinationId in mapsById }.forEach { connection ->
                        val source = mapsById.getValue(sourceId)
                        val target = mapsById.getValue(connection.destinationId)
                        delta(source, target, connection)?.let { delta ->
                            add(Constraint(sourceId, connection.destinationId, delta))
                        }
                    }
                }
            }
        }.filter { it.sourceId !in invalidSources && it.targetId !in invalidSources }
        val canonicalConstraints = constraints
            .groupBy { constraint -> ConnectionKey.of(constraint.sourceId, constraint.targetId) }
            .mapNotNull { (key, pairConstraints) ->
                val deltas = pairConstraints.map { constraint ->
                    if (constraint.sourceId == key.firstId) constraint.delta
                    else Point(-constraint.delta.x, -constraint.delta.y)
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

    private fun readConnections(rom: RomImage, header: Int): List<MapConnection>? = runCatching {
        if (header < 0 || header.toLong() + MAP_HEADER_BYTES > rom.size.toLong()) return@runCatching null
        val rawConnections = rom.u32le(header + MAP_CONNECTIONS_OFFSET)
        if (rawConnections == 0L) return@runCatching emptyList<MapConnection>()
        val connections = rom.gbaPointer(header + MAP_CONNECTIONS_OFFSET) ?: return@runCatching null
        if (connections.toLong() + MAP_CONNECTIONS_BYTES > rom.size.toLong()) return@runCatching null
        val count = rom.u32le(connections)
        if (count > MAX_MAP_CONNECTIONS.toLong()) return@runCatching null
        if (count == 0L) return@runCatching emptyList<MapConnection>()
        val entries = rom.gbaPointer(connections + MAP_CONNECTION_ENTRIES_OFFSET) ?: return@runCatching null
        if (entries.toLong() + count * MAP_CONNECTION_BYTES > rom.size.toLong()) return@runCatching null
        buildList<MapConnection> {
            repeat(count.toInt()) { index ->
                val entry = entries + index * MAP_CONNECTION_BYTES
                val direction = rom.u32le(entry).toInt()
                if (direction !in CARDINAL_DIRECTIONS) return@repeat
                val offset = rom.u32le(entry + MAP_CONNECTION_OFFSET_OFFSET).toInt()
                if (offset !in -MAX_CONNECTION_OFFSET..MAX_CONNECTION_OFFSET) return@repeat
                add(
                    MapConnection(
                        direction = direction,
                        offset = offset,
                        destinationId = (rom.u8(entry + MAP_CONNECTION_GROUP_OFFSET) shl 8) or
                            rom.u8(entry + MAP_CONNECTION_MAP_OFFSET),
                    ),
                )
            }
        }
    }.getOrNull()

    private fun delta(source: LocalMap, target: LocalMap, connection: MapConnection): Point? = when (connection.direction) {
        SOUTH -> Point(connection.offset, source.gridHeight)
        NORTH -> Point(connection.offset, -target.gridHeight)
        WEST -> Point(-target.gridWidth, connection.offset)
        EAST -> Point(source.gridWidth, connection.offset)
        else -> null
    }

    private data class MapConnection(val direction: Int, val offset: Int, val destinationId: Int)
    private data class Constraint(val sourceId: Int, val targetId: Int, val delta: Point)
    private data class ConnectionKey(val firstId: Int, val secondId: Int) {
        companion object {
            fun of(firstId: Int, secondId: Int): ConnectionKey =
                if (firstId < secondId) ConnectionKey(firstId, secondId) else ConnectionKey(secondId, firstId)
        }
    }
    private data class Edge(val targetId: Int, val delta: Point)
    private data class Point(val x: Int, val y: Int)

    private val CARDINAL_DIRECTIONS = setOf(SOUTH, NORTH, WEST, EAST)
    private const val SOUTH = 1
    private const val NORTH = 2
    private const val WEST = 3
    private const val EAST = 4
    private const val MAP_HEADER_BYTES = 28
    private const val MAP_CONNECTIONS_OFFSET = 12
    private const val MAP_CONNECTIONS_BYTES = 8
    private const val MAP_CONNECTION_ENTRIES_OFFSET = 4
    private const val MAP_CONNECTION_BYTES = 12
    private const val MAP_CONNECTION_OFFSET_OFFSET = 4
    private const val MAP_CONNECTION_GROUP_OFFSET = 8
    private const val MAP_CONNECTION_MAP_OFFSET = 9
    private const val MAX_MAP_CONNECTIONS = 32
    private const val MAX_CONNECTION_OFFSET = 4096
    private const val MAX_SCENE_DIMENSION = 8192
}
