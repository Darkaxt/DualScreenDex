package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.parse.Gen3WorldMapResolution
import com.enrpau.dualscreendex.parser.parse.Gen3WorldMapResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3WorldMapResolverTest {
    @Test
    fun resolvesReferencedRomRasterAndEncounterBoundGeometry() {
        val fixture = fixture()

        val outcome = Gen3WorldMapResolver.resolve(
            RomAnalysisSession(RomImage(fixture.bytes), RomHeader(Platform.GBA, "")),
            fixture.encounterBaseIds,
        )

        val resolved = outcome as Gen3WorldMapResolution.Resolved
        val region = resolved.catalog.regions.single()
        assertEquals(2, region.locations.size)
        assertEquals(setOf(0x0000), region.locations.first { it.displayName == "Oldale Town" }.baseAreaIds)
        assertEquals(listOf(WorldMapCell(0, 0, 1, 1)), region.locations.first().geometry)
        assertEquals(16, region.pixelWidth)
        assertEquals(8, region.pixelHeight)
        assertEquals(0xff0000ff.toInt(), resolved.catalog.assets.getValue(region.imageAssetKey).argb[0])
    }

    @Test
    fun ignoresAValidButUnreferencedAssetCluster() {
        val fixture = fixture(includeDecoy = true)

        val outcome = Gen3WorldMapResolver.resolve(
            RomAnalysisSession(RomImage(fixture.bytes), RomHeader(Platform.GBA, "")),
            fixture.encounterBaseIds,
        )

        assertTrue(outcome is Gen3WorldMapResolution.Resolved)
    }

    @Test
    fun failsClosedWhenTwoReferencedClustersAreAuthoritative() {
        val fixture = fixture(referenceDecoy = true)

        val outcome = Gen3WorldMapResolver.resolve(
            RomAnalysisSession(RomImage(fixture.bytes), RomHeader(Platform.GBA, "")),
            fixture.encounterBaseIds,
        )

        assertTrue(outcome is Gen3WorldMapResolution.Ambiguous)
    }

    @Test
    fun failsClosedWhenTheReferenceIndexCannotPublishCompleteSites() {
        val fixture = fixture()
        val session = RomAnalysisSession(
            RomImage(fixture.bytes),
            RomHeader(Platform.GBA, ""),
            gbaReferenceIndexFactory = { _, _ ->
                GbaReferenceIndex.budgetExceeded("fixture reference budget exceeded")
            },
        )

        val outcome = Gen3WorldMapResolver.resolve(session, fixture.encounterBaseIds)

        assertTrue(outcome is Gen3WorldMapResolution.BudgetExceeded)
    }

    private fun fixture(includeDecoy: Boolean = false, referenceDecoy: Boolean = false): Fixture {
        val bytes = ByteArray(0x3000)
        putPointer(bytes, 0x20C, 0x200)
        putPointer(bytes, 0x210, 0x208)
        putPointer(bytes, 0x200, 0x300)
        putPointer(bytes, 0x204, 0x31C)
        putPointer(bytes, 0x208, 0x338)
        writeMapHeader(bytes, 0x300, 0)
        writeMapHeader(bytes, 0x31C, 1)
        writeMapHeader(bytes, 0x338, 1)
        writeRegionEntry(bytes, 0x600, 0, 0x700, "Oldale Town", x = 0)
        writeRegionEntry(bytes, 0x600, 1, 0x720, "Route 101", x = 1)
        putPointer(bytes, 0x900, 0x600)

        writeCluster(bytes, 0x1000, color = 0x7C00)
        writeLiteralReference(bytes, 0x80, 0x100, 0x1000)
        writeLiteralReference(bytes, 0x84, 0x104, 0x1100)
        writeLiteralReference(bytes, 0x88, 0x108, 0x1200)
        if (includeDecoy || referenceDecoy) {
            writeCluster(bytes, 0x1800, color = 0x03E0)
        }
        if (referenceDecoy) {
            writeLiteralReference(bytes, 0x180, 0x280, 0x1800)
            writeLiteralReference(bytes, 0x184, 0x284, 0x1900)
            writeLiteralReference(bytes, 0x188, 0x288, 0x1A00)
        }
        return Fixture(bytes, setOf(0x0000, 0x0001, 0x0100))
    }

    private fun writeCluster(bytes: ByteArray, root: Int, color: Int) {
        val tiles = ByteArray(128) { index -> if (index < 64) 1 else 2 }
        val tilemap = byteArrayOf(0, 0, 1, 0)
        writeLiteralLz(bytes, root, tiles)
        writeLiteralLz(bytes, root + 0x100, tilemap)
        putU16(bytes, root + 0x200, 0)
        putU16(bytes, root + 0x202, color)
        putU16(bytes, root + 0x204, 0x001F)
    }

    private fun writeLiteralLz(bytes: ByteArray, offset: Int, decoded: ByteArray) {
        bytes[offset] = 0x10
        bytes[offset + 1] = decoded.size.toByte()
        bytes[offset + 2] = (decoded.size ushr 8).toByte()
        bytes[offset + 3] = (decoded.size ushr 16).toByte()
        var source = 0
        var destination = offset + 4
        while (source < decoded.size) {
            bytes[destination++] = 0
            repeat(minOf(8, decoded.size - source)) { bytes[destination++] = decoded[source++] }
        }
    }

    private fun writeLiteralReference(bytes: ByteArray, instruction: Int, literal: Int, target: Int) {
        val pc = (instruction + 4) and -4
        putU16(bytes, instruction, 0x4800 or ((literal - pc) / 4))
        putPointer(bytes, literal, target)
    }

    private fun writeMapHeader(bytes: ByteArray, offset: Int, regionSection: Int) {
        putPointer(bytes, offset, 0x500)
        putPointer(bytes, offset + 4, 0x520)
        putPointer(bytes, offset + 8, 0x540)
        putU16(bytes, offset + 0x12, 1)
        bytes[offset + 0x14] = regionSection.toByte()
    }

    private fun writeRegionEntry(
        bytes: ByteArray,
        root: Int,
        index: Int,
        textOffset: Int,
        text: String,
        x: Int,
    ) {
        val offset = root + index * 8
        bytes[offset] = x.toByte()
        bytes[offset + 1] = 0
        bytes[offset + 2] = 1
        bytes[offset + 3] = 1
        putPointer(bytes, offset + 4, textOffset)
        text.forEachIndexed { characterIndex, character ->
            bytes[textOffset + characterIndex] = when (character) {
                ' ' -> 0
                in 'A'..'Z' -> (0xBB + character.code - 'A'.code).toByte()
                in 'a'..'z' -> (0xD5 + character.code - 'a'.code).toByte()
                in '0'..'9' -> (0xA1 + character.code - '0'.code).toByte()
                else -> error("unsupported fixture character $character")
            }
        }
        bytes[textOffset + text.length] = 0xFF.toByte()
    }

    private fun putPointer(bytes: ByteArray, offset: Int, target: Int) = putU32(bytes, offset, 0x08000000 + target)

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private data class Fixture(val bytes: ByteArray, val encounterBaseIds: Set<Int>)
}
