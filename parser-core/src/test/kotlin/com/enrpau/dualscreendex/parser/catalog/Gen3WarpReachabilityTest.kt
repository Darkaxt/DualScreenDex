package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Test

class Gen3WarpReachabilityTest {
    @Test
    fun followsTheMapGroupRatherThanTheDestinationWarpIndex() {
        listOf(0 to 1, 1 to 0).forEach { (warpIndex, group) ->
            val bytes = fixture(warpIndex, group)
            val expected = if (group == 1) setOf(0, 0x100) else setOf(0)
            assertEquals(expected, Gen3MapLocationResolver.resolveReachableHeaderByBaseArea(
                RomImage(bytes), setOf(0), references(), ParserCancellationToken.NONE,
            ).keys)
        }
    }

    @Test
    fun dynamicAndUnmappedWarpDestinationsDoNotInventReachableMaps() {
        listOf(0x7F to 0x7F, 0 to 2).forEach { (map, group) ->
            val bytes = fixture(1, group)
            bytes[0x586] = map.toByte()
            assertEquals(setOf(0), Gen3MapLocationResolver.resolveReachableHeaderByBaseArea(
                RomImage(bytes), setOf(0), references(), ParserCancellationToken.NONE,
            ).keys)
        }
    }

    @Test
    fun dynamicMapSentinelDoesNotFollowAReadableDecoyHeader() {
        val bytes = fixture(0, 0)
        pointer(bytes, 0x50, 0x384)
        repeat(128) { pointer(bytes, 0x180 + it * 4, 0x600) }
        pointer(bytes, 0x380, 0x620)
        pointer(bytes, 0x384, 0x180)
        pointer(bytes, 0x388, 0x380)
        listOf(0x600, 0x620).forEach { header ->
            pointer(bytes, header, 0x800)
            short(bytes, header + 0x12, 1)
        }
        pointer(bytes, 0x604, 0x700)
        bytes[0x701] = 1
        pointer(bytes, 0x708, 0x780)
        bytes[0x786] = 0x7F
        val references = GbaReferenceIndex.fromTargets(
            mapOf(0x384 to GbaTargetReferenceEvidence(1, listOf(0x40), 1, 16, null)), limitTargets = 32,
        )
        assertEquals(setOf(0), Gen3MapLocationResolver.resolveReachableHeaderByBaseArea(
            RomImage(bytes), setOf(0), references, ParserCancellationToken.NONE,
        ).keys)
    }

    private fun fixture(warpIndex: Int, group: Int): ByteArray = ByteArray(0x1000).also { bytes ->
        // Independently assembled gMapGroups[group][map] pointer-indexing consumer.
        intArrayOf(0x4B03, 0x0400, 0x0B80, 0x58C3, 0x0409, 0x0B89, 0x58C8, 0x4770)
            .forEachIndexed { index, op -> short(bytes, 0x40 + index * 2, op) }
        pointer(bytes, 0x50, 0x180)
        pointer(bytes, 0x180, 0x178)
        pointer(bytes, 0x184, 0x17C)
        pointer(bytes, 0x178, 0x300)
        pointer(bytes, 0x17C, 0x320)
        listOf(0x300, 0x320).forEach { header ->
            pointer(bytes, header, 0x400)
            short(bytes, header + 0x12, 1)
        }
        pointer(bytes, 0x304, 0x500)
        bytes[0x501] = 1
        pointer(bytes, 0x508, 0x580)
        bytes[0x585] = warpIndex.toByte()
        bytes[0x586] = 0
        bytes[0x587] = group.toByte()
    }

    private fun references() = GbaReferenceIndex.fromTargets(
        mapOf(0x180 to GbaTargetReferenceEvidence(1, listOf(0x40), 1, 16, null)),
        limitTargets = 32,
    )

    private fun pointer(bytes: ByteArray, at: Int, value: Int) {
        repeat(4) { bytes[at + it] = ((0x08000000 + value) ushr (it * 8)).toByte() }
    }

    private fun short(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = value.toByte()
        bytes[at + 1] = (value ushr 8).toByte()
    }
}
