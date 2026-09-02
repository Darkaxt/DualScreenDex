package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen1LocalMapPoiResolverTest {
    @Test
    fun isolatesMalformedMapsAndOmitsOutOfBoundsEvents() {
        val bytes = ByteArray(0x8000)
        writeHeader(bytes, HEADER_1, OBJECT_ROOT_1_ADDRESS)
        writeHeader(bytes, HEADER_2, 0x7FFF)
        byteArrayOf(
            0,
            2,
            1, 2, 1, 2,
            20, 20, 1, 2,
            0,
            0,
        ).copyInto(bytes, OBJECT_ROOT_1)

        val resolution = Gen1LocalMapPoiResolver.resolve(
            rom = RomImage(bytes),
            sources = listOf(
                Gen1LocalMapPoiResolver.Source(1, 1, HEADER_1),
                Gen1LocalMapPoiResolver.Source(2, 1, HEADER_2),
            ),
            maps = listOf(localMap(1), localMap(2)),
            codec = null,
        )

        val poi = resolution.pois.single()
        assertEquals(LocalMapPoiKind.PLACE, poi.kind)
        assertEquals(1, poi.baseAreaId)
        assertEquals(2, poi.tileX)
        assertEquals(1, poi.tileY)
        assertEquals(2, poi.destinationBaseAreaId)
        assertTrue(resolution.skippedReasons.any { it.startsWith("map 0x0002 POIs:") })
    }

    private fun writeHeader(bytes: ByteArray, header: Int, objectAddress: Int) {
        bytes[header + 9] = 0
        putU16(bytes, header + 10, objectAddress)
    }

    private fun localMap(baseAreaId: Int) = LocalMap(
        key = "local/$baseAreaId",
        displayName = null,
        baseAreaId = baseAreaId,
        pixelWidth = 160,
        pixelHeight = 160,
        gridWidth = 10,
        gridHeight = 10,
        imageAssetKey = "asset/$baseAreaId",
    )

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private companion object {
        const val HEADER_1 = 0x4000
        const val HEADER_2 = 0x4020
        const val OBJECT_ROOT_1 = 0x4100
        const val OBJECT_ROOT_1_ADDRESS = 0x4100
    }
}
