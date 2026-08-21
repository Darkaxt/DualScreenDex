package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3MapSceneResolverTest {
    @Test
    fun placesConnectedMapsInOneNormalizedScene() {
        val bytes = ByteArray(0x1000)
        writeConnection(bytes, header = 0x100, connections = 0x300, entries = 0x380, direction = EAST, offset = 2, destination = 0x0002)
        writeConnection(bytes, header = 0x200, connections = 0x320, entries = 0x3A0, direction = WEST, offset = -2, destination = 0x0001)
        val maps = listOf(localMap(0x0001, width = 10, height = 8), localMap(0x0002, width = 12, height = 6))

        val scenes = Gen3MapSceneResolver.resolve(
            rom = RomImage(bytes),
            headers = mapOf(0x0001 to 0x100, 0x0002 to 0x200),
            maps = maps,
        )

        assertEquals(1, scenes.size)
        val scene = scenes.single()
        assertEquals(22, scene.gridWidth)
        assertEquals(8, scene.gridHeight)
        assertEquals(352, scene.pixelWidth)
        assertEquals(128, scene.pixelHeight)
        assertEquals(
            listOf(
                Triple(0x0001, 0, 0),
                Triple(0x0002, 10, 2),
            ),
            scene.placements.map { Triple(it.baseAreaId, it.gridX, it.gridY) },
        )
    }

    @Test
    fun rejectsAComponentWhoseRepeatedPathsDisagree() {
        val bytes = ByteArray(0x1000)
        writeConnection(bytes, header = 0x100, connections = 0x300, entries = 0x380, direction = EAST, offset = 2, destination = 0x0002)
        writeConnection(bytes, header = 0x200, connections = 0x320, entries = 0x3A0, direction = WEST, offset = 0, destination = 0x0001)
        val maps = listOf(localMap(0x0001, width = 10, height = 8), localMap(0x0002, width = 12, height = 6))

        val scenes = Gen3MapSceneResolver.resolve(
            rom = RomImage(bytes),
            headers = mapOf(0x0001 to 0x100, 0x0002 to 0x200),
            maps = maps,
        )

        assertTrue(scenes.isEmpty())
    }

    @Test
    fun excludesOnlyAnOverlappingBranchFromAnOtherwiseValidScene() {
        val bytes = ByteArray(0x1000)
        writeConnections(
            bytes,
            header = 0x100,
            connections = 0x300,
            entries = 0x380,
            connectionsToWrite = listOf(
                TestConnection(EAST, 0, 0x0002),
                TestConnection(SOUTH, 0, 0x0003),
            ),
        )
        val maps = listOf(
            localMap(0x0001, width = 10, height = 10),
            localMap(0x0002, width = 10, height = 20),
            localMap(0x0003, width = 20, height = 10),
        )

        val scenes = Gen3MapSceneResolver.resolve(
            rom = RomImage(bytes),
            headers = mapOf(0x0001 to 0x100, 0x0002 to 0x200, 0x0003 to 0x280),
            maps = maps,
        )

        assertEquals(listOf(0x0001, 0x0002), scenes.single().placements.map { it.baseAreaId })
    }

    @Test
    fun leavesWarpOnlyAndIsolatedMapsOutsideGeneratedScenes() {
        val maps = listOf(localMap(0x0001, width = 10, height = 8), localMap(0x0002, width = 12, height = 6))

        val scenes = Gen3MapSceneResolver.resolve(
            rom = RomImage(ByteArray(0x400)),
            headers = mapOf(0x0001 to 0x100, 0x0002 to 0x200),
            maps = maps,
        )

        assertTrue(scenes.isEmpty())
    }

    private fun localMap(baseAreaId: Int, width: Int, height: Int) = LocalMap(
        key = "local/${baseAreaId.toString(16).padStart(4, '0')}",
        displayName = null,
        baseAreaId = baseAreaId,
        pixelWidth = width * 16,
        pixelHeight = height * 16,
        gridWidth = width,
        gridHeight = height,
        imageAssetKey = "local/${baseAreaId.toString(16).padStart(4, '0')}/map",
    )

    private fun writeConnection(
        bytes: ByteArray,
        header: Int,
        connections: Int,
        entries: Int,
        direction: Int,
        offset: Int,
        destination: Int,
    ) = writeConnections(
        bytes,
        header,
        connections,
        entries,
        listOf(TestConnection(direction, offset, destination)),
    )

    private fun writeConnections(
        bytes: ByteArray,
        header: Int,
        connections: Int,
        entries: Int,
        connectionsToWrite: List<TestConnection>,
    ) {
        putPointer(bytes, header + 12, connections)
        putU32(bytes, connections, connectionsToWrite.size)
        putPointer(bytes, connections + 4, entries)
        connectionsToWrite.forEachIndexed { index, connection ->
            val entry = entries + index * 12
            putU32(bytes, entry, connection.direction)
            putU32(bytes, entry + 4, connection.offset)
            bytes[entry + 8] = (connection.destination ushr 8).toByte()
            bytes[entry + 9] = connection.destination.toByte()
        }
    }

    private fun putPointer(bytes: ByteArray, offset: Int, target: Int) = putU32(bytes, offset, 0x08000000 + target)

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private data class TestConnection(val direction: Int, val offset: Int, val destination: Int)

    private companion object {
        const val SOUTH = 1
        const val EAST = 4
        const val WEST = 3
    }
}
