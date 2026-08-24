package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen2MapSceneResolverTest {
    @Test
    fun reciprocalConnectionsBuildOneNormalizedScene() {
        val bytes = ByteArray(0xc000)
        bytes[ATTRIBUTES_1 + 11] = EAST.toByte()
        writeConnection(bytes, ATTRIBUTES_1 + 12, 0x0102, BLOCK_ADDRESS_2, 6, -2, 0)
        bytes[ATTRIBUTES_2 + 11] = WEST.toByte()
        writeConnection(bytes, ATTRIBUTES_2 + 12, 0x0101, BLOCK_ADDRESS_1, 5, 2, 9)

        val resolution = Gen2MapSceneResolver.resolve(
            rom = RomImage(bytes),
            sources = listOf(source(0x0101, ATTRIBUTES_1, BLOCKS_1), source(0x0102, ATTRIBUTES_2, BLOCKS_2)),
            maps = listOf(localMap(0x0101, 10, 8), localMap(0x0102, 12, 6)),
        )

        assertTrue(resolution.skippedReasons.isEmpty())
        assertEquals("scene/0101", resolution.scenes.single().key)
        assertEquals(22, resolution.scenes.single().gridWidth)
        assertEquals(8, resolution.scenes.single().gridHeight)
        assertEquals(
            listOf(Triple(0x0101, 0, 0), Triple(0x0102, 10, 2)),
            resolution.scenes.single().placements.map { Triple(it.baseAreaId, it.gridX, it.gridY) },
        )
    }

    @Test
    fun setFlagsConsumeRecordsInCardinalOrder() {
        val bytes = ByteArray(0xc000)
        bytes[ATTRIBUTES_1 + 11] = (NORTH or SOUTH or WEST or EAST).toByte()
        writeConnection(bytes, ATTRIBUTES_1 + 12, 0x0102, BLOCK_ADDRESS_2, 5, 5, 0)
        writeConnection(bytes, ATTRIBUTES_1 + 24, 0x0103, BLOCK_ADDRESS_3, 5, 0, 0)
        writeConnection(bytes, ATTRIBUTES_1 + 36, 0x0104, BLOCK_ADDRESS_4, 3, 0, 5)
        writeConnection(bytes, ATTRIBUTES_1 + 48, 0x0105, BLOCK_ADDRESS_5, 3, 0, 0)

        val resolution = Gen2MapSceneResolver.resolve(
            rom = RomImage(bytes),
            sources = listOf(
                source(0x0101, ATTRIBUTES_1, BLOCKS_1),
                source(0x0102, ATTRIBUTES_2, BLOCKS_2),
                source(0x0103, ATTRIBUTES_3, BLOCKS_3),
                source(0x0104, ATTRIBUTES_4, BLOCKS_4),
                source(0x0105, ATTRIBUTES_5, BLOCKS_5),
            ),
            maps = listOf(
                localMap(0x0101, 10, 8),
                localMap(0x0102, 10, 6),
                localMap(0x0103, 10, 6),
                localMap(0x0104, 6, 8),
                localMap(0x0105, 6, 8),
            ),
        )

        assertTrue(resolution.skippedReasons.isEmpty())
        assertEquals(
            listOf(
                Triple(0x0101, 6, 6),
                Triple(0x0102, 6, 0),
                Triple(0x0103, 6, 14),
                Triple(0x0104, 0, 6),
                Triple(0x0105, 16, 6),
            ),
            resolution.scenes.single().placements.map { Triple(it.baseAreaId, it.gridX, it.gridY) },
        )
    }

    @Test
    fun conflictingReciprocalEvidenceIsDiscarded() {
        val bytes = ByteArray(0xc000)
        bytes[ATTRIBUTES_1 + 11] = EAST.toByte()
        writeConnection(bytes, ATTRIBUTES_1 + 12, 0x0102, BLOCK_ADDRESS_2, 6, -2, 0)
        bytes[ATTRIBUTES_2 + 11] = WEST.toByte()
        writeConnection(bytes, ATTRIBUTES_2 + 12, 0x0101, BLOCK_ADDRESS_1, 5, 4, 9)

        val resolution = resolvePair(bytes)

        assertTrue(resolution.skippedReasons.isEmpty())
        assertTrue(resolution.scenes.isEmpty())
    }

    @Test
    fun malformedConnectionDoesNotSuppressFollowingRecord() {
        val bytes = ByteArray(0xc000)
        bytes[ATTRIBUTES_1 + 11] = (NORTH or EAST).toByte()
        writeConnection(
            bytes,
            ATTRIBUTES_1 + 12,
            0x0102,
            BLOCK_ADDRESS_2,
            5,
            5,
            0,
            destination = 0,
        )
        writeConnection(bytes, ATTRIBUTES_1 + 24, 0x0103, BLOCK_ADDRESS_3, 3, 0, 0)

        val resolution = Gen2MapSceneResolver.resolve(
            rom = RomImage(bytes),
            sources = listOf(
                source(0x0101, ATTRIBUTES_1, BLOCKS_1),
                source(0x0102, ATTRIBUTES_2, BLOCKS_2),
                source(0x0103, ATTRIBUTES_3, BLOCKS_3),
            ),
            maps = listOf(localMap(0x0101, 10, 8), localMap(0x0102, 10, 6), localMap(0x0103, 6, 8)),
        )

        assertEquals(listOf(0x0101, 0x0103), resolution.scenes.single().placements.map { it.baseAreaId })
        assertEquals(1, resolution.skippedReasons.size)
        assertTrue(resolution.skippedReasons.single().contains("north connection"))
    }

    @Test
    fun rejectsConnectionStripOutsideTargetBlocks() {
        val bytes = ByteArray(0xc000)
        bytes[ATTRIBUTES_1 + 11] = EAST.toByte()
        writeConnection(bytes, ATTRIBUTES_1 + 12, 0x0102, BLOCK_ADDRESS_2 + 0x100, 6, 0, 0)

        assertRejected(resolvePair(bytes), "outside target blocks")
    }

    @Test
    fun rejectsWrongConnectedMapWidth() {
        val bytes = ByteArray(0xc000)
        bytes[ATTRIBUTES_1 + 11] = EAST.toByte()
        writeConnection(bytes, ATTRIBUTES_1 + 12, 0x0102, BLOCK_ADDRESS_2, 5, 0, 0)

        assertRejected(resolvePair(bytes), "width does not match")
    }

    @Test
    fun rejectsUnknownTargetMap() {
        val bytes = ByteArray(0xc000)
        bytes[ATTRIBUTES_1 + 11] = EAST.toByte()
        writeConnection(bytes, ATTRIBUTES_1 + 12, 0x7f7f, BLOCK_ADDRESS_2, 6, 0, 0)

        assertRejected(resolvePair(bytes), "descriptor is unavailable")
    }

    @Test
    fun rejectsRecordTruncatedAtBankBoundary() {
        val bytes = ByteArray(0xc000)
        bytes[TRUNCATED_ATTRIBUTES + 11] = EAST.toByte()
        val resolution = Gen2MapSceneResolver.resolve(
            rom = RomImage(bytes),
            sources = listOf(
                source(0x0101, TRUNCATED_ATTRIBUTES, BLOCKS_1),
                source(0x0102, ATTRIBUTES_2, BLOCKS_2),
            ),
            maps = listOf(localMap(0x0101, 10, 8), localMap(0x0102, 12, 6)),
        )

        assertRejected(resolution, "record is truncated")
    }

    @Test
    fun rejectsOddTileAlignmentWithoutThrowing() {
        val bytes = ByteArray(0xc000)
        bytes[ATTRIBUTES_1 + 11] = EAST.toByte()
        writeConnection(bytes, ATTRIBUTES_1 + 12, 0x0102, BLOCK_ADDRESS_2, 6, 1, 0)

        assertRejected(resolvePair(bytes), "metatile grid")
    }

    private fun resolvePair(bytes: ByteArray): Gen2MapSceneResolver.Resolution = Gen2MapSceneResolver.resolve(
        rom = RomImage(bytes),
        sources = listOf(source(0x0101, ATTRIBUTES_1, BLOCKS_1), source(0x0102, ATTRIBUTES_2, BLOCKS_2)),
        maps = listOf(localMap(0x0101, 10, 8), localMap(0x0102, 12, 6)),
    )

    private fun assertRejected(resolution: Gen2MapSceneResolver.Resolution, reason: String) {
        assertTrue(resolution.scenes.isEmpty())
        assertEquals(1, resolution.skippedReasons.size)
        assertTrue(resolution.skippedReasons.single().contains(reason))
    }

    private fun source(baseAreaId: Int, attributes: Int, blocks: Int) = Gen2MapSceneResolver.Source(
        baseAreaId = baseAreaId,
        attributesBank = 1,
        attributes = attributes,
        blockBank = 2,
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
        bytes[offset] = (targetId ushr 8).toByte()
        bytes[offset + 1] = targetId.toByte()
        write16(bytes, offset + 2, strip)
        write16(bytes, offset + 4, destination)
        bytes[offset + 6] = 1
        bytes[offset + 7] = targetBlockWidth.toByte()
        bytes[offset + 8] = yAlignment.toByte()
        bytes[offset + 9] = xAlignment.toByte()
        write16(bytes, offset + 10, 0xc000)
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
        private const val ATTRIBUTES_1 = 0x4100
        private const val ATTRIBUTES_2 = 0x4200
        private const val ATTRIBUTES_3 = 0x4300
        private const val ATTRIBUTES_4 = 0x4700
        private const val ATTRIBUTES_5 = 0x4800
        private const val TRUNCATED_ATTRIBUTES = 0x7ff0
        private const val BLOCKS_1 = 0x8400
        private const val BLOCKS_2 = 0x8500
        private const val BLOCKS_3 = 0x8600
        private const val BLOCKS_4 = 0x8900
        private const val BLOCKS_5 = 0x8a00
        private const val BLOCK_ADDRESS_1 = 0x4400
        private const val BLOCK_ADDRESS_2 = 0x4500
        private const val BLOCK_ADDRESS_3 = 0x4600
        private const val BLOCK_ADDRESS_4 = 0x4900
        private const val BLOCK_ADDRESS_5 = 0x4a00
    }
}
