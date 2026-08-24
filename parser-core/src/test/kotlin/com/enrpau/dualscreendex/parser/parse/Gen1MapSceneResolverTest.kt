package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen1MapSceneResolverTest {
    @Test
    fun reciprocalConnectionsBuildOneNormalizedScene() {
        val bytes = ByteArray(0x8000)
        bytes[HEADER_1 + 9] = EAST.toByte()
        writeConnection(
            bytes = bytes,
            offset = HEADER_1 + 10,
            targetId = 2,
            strip = BLOCKS_2,
            targetBlockWidth = 6,
            yAlignment = -2,
            xAlignment = 0,
        )
        bytes[HEADER_2 + 9] = WEST.toByte()
        writeConnection(
            bytes = bytes,
            offset = HEADER_2 + 10,
            targetId = 1,
            strip = BLOCKS_1,
            targetBlockWidth = 5,
            yAlignment = 2,
            xAlignment = 9,
        )

        val resolution = Gen1MapSceneResolver.resolve(
            rom = RomImage(bytes),
            sources = listOf(source(1, HEADER_1, BLOCKS_1), source(2, HEADER_2, BLOCKS_2)),
            maps = listOf(localMap(1, 10, 8), localMap(2, 12, 6)),
        )

        assertTrue(resolution.skippedReasons.isEmpty())
        assertEquals("scene/0001", resolution.scenes.single().key)
        assertEquals(22, resolution.scenes.single().gridWidth)
        assertEquals(8, resolution.scenes.single().gridHeight)
        assertEquals(
            listOf(Triple(1, 0, 0), Triple(2, 10, 2)),
            resolution.scenes.single().placements.map { Triple(it.baseAreaId, it.gridX, it.gridY) },
        )
    }

    @Test
    fun setFlagsConsumeRecordsInCardinalOrder() {
        val bytes = ByteArray(0x8000)
        bytes[HEADER_1 + 9] = (NORTH or SOUTH or WEST or EAST).toByte()
        writeConnection(
            bytes = bytes,
            offset = HEADER_1 + 10,
            targetId = 2,
            strip = BLOCKS_2,
            targetBlockWidth = 5,
            yAlignment = 5,
            xAlignment = 0,
        )
        writeConnection(
            bytes = bytes,
            offset = HEADER_1 + 21,
            targetId = 3,
            strip = BLOCKS_3,
            targetBlockWidth = 5,
            yAlignment = 0,
            xAlignment = 0,
        )
        writeConnection(
            bytes = bytes,
            offset = HEADER_1 + 32,
            targetId = 4,
            strip = BLOCKS_4,
            targetBlockWidth = 3,
            yAlignment = 0,
            xAlignment = 5,
        )
        writeConnection(
            bytes = bytes,
            offset = HEADER_1 + 43,
            targetId = 5,
            strip = BLOCKS_5,
            targetBlockWidth = 3,
            yAlignment = 0,
            xAlignment = 0,
        )

        val resolution = Gen1MapSceneResolver.resolve(
            rom = RomImage(bytes),
            sources = listOf(
                source(1, HEADER_1, BLOCKS_1),
                source(2, HEADER_2, BLOCKS_2),
                source(3, HEADER_3, BLOCKS_3),
                source(4, HEADER_4, BLOCKS_4),
                source(5, HEADER_5, BLOCKS_5),
            ),
            maps = listOf(
                localMap(1, 10, 8),
                localMap(2, 10, 6),
                localMap(3, 10, 6),
                localMap(4, 6, 8),
                localMap(5, 6, 8),
            ),
        )

        assertTrue(resolution.skippedReasons.isEmpty())
        assertEquals(
            listOf(
                Triple(1, 6, 6),
                Triple(2, 6, 0),
                Triple(3, 6, 14),
                Triple(4, 0, 6),
                Triple(5, 16, 6),
            ),
            resolution.scenes.single().placements.map { Triple(it.baseAreaId, it.gridX, it.gridY) },
        )
    }

    @Test
    fun malformedConnectionDoesNotSuppressFollowingRecord() {
        val bytes = ByteArray(0x8000)
        bytes[HEADER_1 + 9] = (NORTH or EAST).toByte()
        writeConnection(
            bytes = bytes,
            offset = HEADER_1 + 10,
            targetId = 2,
            strip = BLOCKS_2,
            targetBlockWidth = 5,
            yAlignment = 5,
            xAlignment = 0,
            destination = 0,
        )
        writeConnection(
            bytes = bytes,
            offset = HEADER_1 + 21,
            targetId = 3,
            strip = BLOCKS_3,
            targetBlockWidth = 3,
            yAlignment = 0,
            xAlignment = 0,
        )

        val resolution = Gen1MapSceneResolver.resolve(
            rom = RomImage(bytes),
            sources = listOf(
                source(1, HEADER_1, BLOCKS_1),
                source(2, HEADER_2, BLOCKS_2),
                source(3, HEADER_3, BLOCKS_3),
            ),
            maps = listOf(localMap(1, 10, 8), localMap(2, 10, 6), localMap(3, 6, 8)),
        )

        assertEquals(listOf(1, 3), resolution.scenes.single().placements.map { it.baseAreaId })
        assertEquals(1, resolution.skippedReasons.size)
        assertTrue(resolution.skippedReasons.single().contains("north connection"))
    }

    @Test
    fun rejectsConnectionStripOutsideTargetBlocks() {
        val bytes = ByteArray(0x8000)
        bytes[HEADER_1 + 9] = EAST.toByte()
        writeConnection(bytes, HEADER_1 + 10, 2, BLOCKS_2 + 0x100, 6, 0, 0)

        assertRejected(resolvePair(bytes), "outside target blocks")
    }

    @Test
    fun rejectsWrongConnectedMapWidth() {
        val bytes = ByteArray(0x8000)
        bytes[HEADER_1 + 9] = EAST.toByte()
        writeConnection(bytes, HEADER_1 + 10, 2, BLOCKS_2, 5, 0, 0)

        assertRejected(resolvePair(bytes), "width does not match")
    }

    @Test
    fun rejectsUnknownTargetMap() {
        val bytes = ByteArray(0x8000)
        bytes[HEADER_1 + 9] = EAST.toByte()
        writeConnection(bytes, HEADER_1 + 10, 0x7f, BLOCKS_2, 6, 0, 0)

        assertRejected(resolvePair(bytes), "descriptor is unavailable")
    }

    @Test
    fun rejectsRecordTruncatedAtBankBoundary() {
        val bytes = ByteArray(0x8000)
        bytes[TRUNCATED_HEADER + 9] = EAST.toByte()
        val resolution = Gen1MapSceneResolver.resolve(
            rom = RomImage(bytes),
            sources = listOf(source(1, TRUNCATED_HEADER, BLOCKS_1), source(2, HEADER_2, BLOCKS_2)),
            maps = listOf(localMap(1, 10, 8), localMap(2, 12, 6)),
        )

        assertRejected(resolution, "record is truncated")
    }

    @Test
    fun rejectsOddTileAlignmentWithoutThrowing() {
        val bytes = ByteArray(0x8000)
        bytes[HEADER_1 + 9] = EAST.toByte()
        writeConnection(
            bytes = bytes,
            offset = HEADER_1 + 10,
            targetId = 2,
            strip = BLOCKS_2,
            targetBlockWidth = 6,
            yAlignment = 1,
            xAlignment = 0,
        )

        val resolution = Gen1MapSceneResolver.resolve(
            rom = RomImage(bytes),
            sources = listOf(source(1, HEADER_1, BLOCKS_1), source(2, HEADER_2, BLOCKS_2)),
            maps = listOf(localMap(1, 10, 8), localMap(2, 12, 6)),
        )

        assertTrue(resolution.scenes.isEmpty())
        assertEquals(1, resolution.skippedReasons.size)
        assertTrue(resolution.skippedReasons.single().contains("metatile grid"))
    }

    private fun resolvePair(bytes: ByteArray): Gen1MapSceneResolver.Resolution = Gen1MapSceneResolver.resolve(
        rom = RomImage(bytes),
        sources = listOf(source(1, HEADER_1, BLOCKS_1), source(2, HEADER_2, BLOCKS_2)),
        maps = listOf(localMap(1, 10, 8), localMap(2, 12, 6)),
    )

    private fun assertRejected(resolution: Gen1MapSceneResolver.Resolution, reason: String) {
        assertTrue(resolution.scenes.isEmpty())
        assertEquals(1, resolution.skippedReasons.size)
        assertTrue(resolution.skippedReasons.single().contains(reason))
    }

    private fun source(baseAreaId: Int, header: Int, blocks: Int) = Gen1MapSceneResolver.Source(
        baseAreaId = baseAreaId,
        headerBank = 1,
        header = header,
        blockBank = 1,
        blocks = blocks,
    )

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
        offset: Int,
        targetId: Int,
        strip: Int,
        targetBlockWidth: Int,
        yAlignment: Int,
        xAlignment: Int,
        destination: Int = 0xc000,
    ) {
        bytes[offset] = targetId.toByte()
        write16(bytes, offset + 1, strip)
        write16(bytes, offset + 3, destination)
        bytes[offset + 5] = 1
        bytes[offset + 6] = targetBlockWidth.toByte()
        bytes[offset + 7] = yAlignment.toByte()
        bytes[offset + 8] = xAlignment.toByte()
        write16(bytes, offset + 9, 0xc000)
    }

    private fun write16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    companion object {
        private const val NORTH = 0x08
        private const val SOUTH = 0x04
        private const val WEST = 0x02
        private const val EAST = 0x01
        private const val HEADER_1 = 0x4100
        private const val HEADER_2 = 0x4200
        private const val HEADER_3 = 0x4300
        private const val HEADER_4 = 0x4700
        private const val HEADER_5 = 0x4800
        private const val TRUNCATED_HEADER = 0x7ff0
        private const val BLOCKS_1 = 0x4400
        private const val BLOCKS_2 = 0x4500
        private const val BLOCKS_3 = 0x4600
        private const val BLOCKS_4 = 0x4900
        private const val BLOCKS_5 = 0x4a00
    }
}
