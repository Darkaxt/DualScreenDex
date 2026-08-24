package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen2LocalMapPoiResolverTest {
    @Test
    fun isolatesMalformedMapsAndIgnoresUnrelatedObjectPointers() {
        val bytes = ByteArray(0x8000)
        writeAttributes(bytes, ATTRIBUTES_1, EVENTS_1_ADDRESS)
        writeAttributes(bytes, ATTRIBUTES_2, 0x7FFF)
        byteArrayOf(
            0, 0,
            2,
            1, 2, 1, 0, 2,
            20, 20, 1, 0, 2,
            0,
            0,
            1,
            0, 5, 6, 0, 0, -1, -1, 0, 0, 0x12, 0x28, -1, -1,
        ).copyInto(bytes, EVENTS_1)

        val resolution = Gen2LocalMapPoiResolver.resolve(
            rom = RomImage(bytes),
            sources = listOf(
                Gen2LocalMapPoiResolver.Source(1, 1, ATTRIBUTES_1),
                Gen2LocalMapPoiResolver.Source(2, 1, ATTRIBUTES_2),
            ),
            maps = listOf(localMap(1), localMap(2)),
            family = EngineFamily.GOLD_SILVER,
        )

        val poi = resolution.pois.single()
        assertEquals(LocalMapPoiKind.PLACE, poi.kind)
        assertEquals(1, poi.baseAreaId)
        assertEquals(2, poi.tileX)
        assertEquals(1, poi.tileY)
        assertEquals(2, poi.destinationBaseAreaId)
        assertTrue(resolution.skippedReasons.any { it.startsWith("map 0x0002 POIs:") })
    }

    private fun writeAttributes(bytes: ByteArray, attributes: Int, eventsAddress: Int) {
        bytes[attributes + 6] = 1
        putU16(bytes, attributes + 9, eventsAddress)
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
        const val ATTRIBUTES_1 = 0x4000
        const val ATTRIBUTES_2 = 0x4020
        const val EVENTS_1 = 0x4100
        const val EVENTS_1_ADDRESS = 0x4100
    }
}
