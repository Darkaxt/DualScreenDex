package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapScene
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
                            add(
                                LocalMapSceneConstraint(
                                    sourceId = sourceId,
                                    targetId = connection.destinationId,
                                    deltaX = delta.x,
                                    deltaY = delta.y,
                                ),
                            )
                        }
                    }
                }
            }
        }.filter { it.sourceId !in invalidSources && it.targetId !in invalidSources }
        return LocalMapSceneBuilder.build(maps, constraints)
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
        buildList {
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
}
