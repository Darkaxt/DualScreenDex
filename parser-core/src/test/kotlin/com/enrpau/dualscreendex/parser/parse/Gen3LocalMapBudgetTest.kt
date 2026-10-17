package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.Gen3MapLocationResolver
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3LocalMapBudgetTest {
    @Test fun retainedPixelLimitKeepsValidMapsAndLaterAliasesWithoutInventingCompleteCoverage() =
        assertRetainedMaps(mixedLighting = false)

    @Test fun staticAndTimedAssetsShareTheSameRetainedBudgets() = assertRetainedMaps(mixedLighting = true)

    @Test fun encounterAndConnectedMapsTakePriorityOverGraphOnlyRasters() =
        assertRetainedMaps(mixedLighting = false, prioritizeNavigation = true)

    @Test fun navigationPriorityAlsoAppliesToMixedStaticAndTimedRasters() =
        assertRetainedMaps(mixedLighting = true, prioritizeNavigation = true)

    @Test
    fun nonOversubscribedMapsKeepNativeOrderAndAliasOwnership() {
        val bytes = fixture(mixedLighting = false, connectedMaps = true)
        repeat(MAP_COUNT) { index ->
            word(bytes, 0x4000 + index * 0x20, 1)
            word(bytes, 0x4004 + index * 0x20, 1)
        }
        val session = RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "SYNTHETIC MAP BUDGET"))
        val result = Gen3LocalMapResolver.resolve(session, setOf(63), EngineFamily.EMERALD, null)
            as LocalMapResolution.Resolved
        val catalog = result.catalog.validate()

        assertEquals((0 until MAP_COUNT).toList(), catalog.maps.map { it.baseAreaId })
        assertEquals(0, result.skippedMaps)
        assertEquals("local/0000/map", catalog.maps.first().imageAssetKey)
        assertEquals(catalog.maps.first().imageAssetKey, catalog.maps.last().imageAssetKey)
    }

    private fun assertRetainedMaps(mixedLighting: Boolean, prioritizeNavigation: Boolean = false) {
        val session = RomAnalysisSession(RomImage(fixture(mixedLighting, prioritizeNavigation)), RomHeader(Platform.GBA, "SYNTHETIC MAP BUDGET"))
        val reached = Gen3MapLocationResolver.resolveReachableHeaderByBaseArea(
            session.rom, setOf(0), requireNotNull(session.gbaReferenceIndex), ParserCancellationToken.NONE,
        )
        assertEquals((0 until MAP_COUNT).toSet(), reached.keys)

        val encounters = if (prioritizeNavigation) setOf(0, 62) else setOf(0)
        val outcome = Gen3LocalMapResolver.resolve(session, encounters, EngineFamily.EMERALD, null)
        assertTrue(outcome.toString(), outcome is LocalMapResolution.Resolved)
        val result = outcome as LocalMapResolution.Resolved
        val catalog = result.catalog.validate()
        val retained = if (prioritizeNavigation) (0 until 48).toList() + (60..63) else (0 until 51).toList() + 63
        assertEquals(retained, catalog.maps.map { it.baseAreaId })
        assertEquals(12, result.skippedMaps)
        assertEquals(12, result.reasons.count { it.contains("raster-pixels") })
        assertTrue(result.reasons.any { it.contains("omitted localized") })
        assertEquals(mixedLighting, result.reasons.any { it.contains("indexed timed rasters") })
        assertEquals(51, catalog.assets.size + catalog.timedAssets.size)
        assertEquals(mixedLighting, catalog.timedAssets.isNotEmpty())
        assertTrue(catalog.assets.isNotEmpty())
        assertTrue(catalog.maps.sumOf { it.pixelWidth.toLong() * it.pixelHeight } > 100_000_000L)
        assertTrue(catalog.maps.distinctBy { it.imageAssetKey }
            .sumOf { it.pixelWidth.toLong() * it.pixelHeight } <= 100_000_000L)
        val encodedBytes = catalog.assets.values.sumOf { it.bytes.size.toLong() } +
            catalog.timedAssets.values.sumOf { it.compressedIndices.size.toLong() +
                (it.baseColors.size + it.alternateColors.size) * Int.SIZE_BYTES }
        assertTrue(encodedBytes <= 64L * 1024 * 1024)
        assertEquals(catalog.maps.first().imageAssetKey, catalog.maps.last().imageAssetKey)
        assertEquals(catalog.assets.keys + catalog.timedAssets.keys, catalog.maps.map { it.imageAssetKey }.toSet())
        assertEquals(retained.size, catalog.pois.size)
        assertEquals(catalog.maps.map { it.key }.toSet(), catalog.pois.map { it.localMapKey }.toSet())
    }

    /** Generated pointer graph, canonical tilesets and fabricated pixels only; no external inputs. */
    private fun fixture(mixedLighting: Boolean, connectedMaps: Boolean = false): ByteArray = ByteArray(0x140000).also { bytes ->
        intArrayOf(0x4B03, 0x0400, 0x0B80, 0x58C3, 0x0409, 0x0B89, 0x58C8, 0x4770)
            .forEachIndexed { index, op -> half(bytes, 0x400 + index * 2, op) }
        pointer(bytes, 0x410, 0x900)
        pointer(bytes, 0x900, 0x800)
        repeat(MAP_COUNT) { index ->
            val header = 0x1000 + index * 0x40
            val layout = 0x4000 + index * 0x20
            val events = 0x6000 + index * 0x20
            val warp = 0x8000 + index * 8
            val cells = 0x20000 + index * GRID * GRID * 2
            pointer(bytes, 0x800 + index * 4, header)
            pointer(bytes, header, layout)
            pointer(bytes, header + 4, events)
            half(bytes, header + 0x12, 1)
            if (mixedLighting && (index % 2 == 0 || index == 63)) bytes[header + 0x17] = 1
            word(bytes, layout, GRID); word(bytes, layout + 4, GRID)
            pointer(bytes, layout + 12, cells)
            pointer(bytes, layout + 16, 0xA000); pointer(bytes, layout + 20, 0xA020)
            bytes[events + 1] = 1
            pointer(bytes, events + 8, warp)
            bytes[warp + 5] = 13 // Destination warp index is not the destination map group.
            bytes[warp + 6] = ((index + 1) % MAP_COUNT).toByte()
            half(bytes, cells, if (index == 63) 1 else index + 1)
        }
        if (connectedMaps) {
            for (index in 60..61) {
                val connections = 0xA200 + index * 0x20
                val entry = connections + 8
                pointer(bytes, 0x1000 + index * 0x40 + 12, connections)
                word(bytes, connections, 1)
                pointer(bytes, connections + 4, entry)
                word(bytes, entry, if (index == 60) 4 else 3)
                bytes[entry + 9] = (if (index == 60) 61 else 60).toByte()
            }
        }
        listOf(0xA000, 0xA020).forEachIndexed { index, tileset ->
            bytes[tileset + 1] = index.toByte()
            pointer(bytes, tileset + 4, 0x10000 + index * 0x4000)
            pointer(bytes, tileset + 8, 0x18000)
            pointer(bytes, tileset + 12, 0x18400)
        }
        repeat(16) { color -> half(bytes, 0x18000 + color * 2, color * 0x421) }
        if (mixedLighting) {
            word(bytes, 0xA100, 0x9D7474 or 0x15000000)
            word(bytes, 0xA104, 0xA8B0E0 or 0x09000000)
            word(bytes, 0xA10C, 0xBB9E9E or 0x15000000)
            word(bytes, 0xA110, 0xA8B0E0 or 0x09000000)
            repeat(2) { index ->
                pointer(bytes, 0xA140 + index * 4, 0xA100)
                pointer(bytes, 0xA148 + index * 4, 0xA10C)
            }
        }
        repeat(63) { index ->
            val tile = index + 1
            bytes[0x10000 + tile * 32] = tile.toByte()
            half(bytes, 0x18400 + tile * 16, tile)
        }
    }

    private fun pointer(bytes: ByteArray, at: Int, target: Int) = word(bytes, at, 0x08000000 + target)
    private fun word(bytes: ByteArray, at: Int, value: Int) {
        repeat(4) { bytes[at + it] = (value ushr (it * 8)).toByte() }
    }
    private fun half(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = value.toByte(); bytes[at + 1] = (value ushr 8).toByte()
    }

    private companion object {
        const val GRID = 87
        const val MAP_COUNT = 64
    }
}
