package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class UnboundOdysseyMapCompletionLiveRomTest {
    @Test
    fun unboundResolvesDeterministicWorldAndLocalMaps() = assertCompleteMaps(
        environmentVariable = "DUALDEX_UNBOUND_ROM",
        expectedSha256 = "7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7",
        expectedWorldRegions = 1,
        expectedLocalMaps = 294,
        expectedLocalAssets = 258,
        expectedWorldProjectionSha256 = "2faad5f33b96a4401b119317bc76ce966fc69fc5add5c3caa714a9fef03bc97c",
        expectedLocalProjectionSha256 = "46e9af4157e5f738fd771c9767e749756c432b1bc4225243b08f97d974c75e26",
    )

    @Test
    fun odysseyResolvesDeterministicWorldAndLocalMaps() = assertCompleteMaps(
        environmentVariable = "DUALDEX_ODYSSEY_ROM",
        expectedSha256 = "44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0",
        expectedWorldRegions = 4,
        expectedLocalMaps = 168,
        expectedLocalAssets = 147,
        expectedWorldProjectionSha256 = "68f9954e1038db0dd7b2c88bb157c945680c98d773b9b90830089936587796ae",
        expectedLocalProjectionSha256 = "77def226bbffab306160553f3a24d53a093002ea2647978849b920edcc89cb90",
    )

    private fun assertCompleteMaps(
        environmentVariable: String,
        expectedSha256: String,
        expectedWorldRegions: Int,
        expectedLocalMaps: Int,
        expectedLocalAssets: Int,
        expectedWorldProjectionSha256: String,
        expectedLocalProjectionSha256: String,
    ) {
        val configured = System.getenv(environmentVariable)
        assumeTrue("set $environmentVariable to run this real-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
        val firstRom = RomImage(Files.readAllBytes(path))
        val secondRom = RomImage(Files.readAllBytes(path))
        assertEquals(expectedSha256, firstRom.sha256)
        assertEquals(firstRom.sha256, secondRom.sha256)

        val first = requireNotNull(CatalogParser.parse(firstRom).catalog)
        val second = requireNotNull(CatalogParser.parse(secondRom).catalog)
        val firstWorld = first.capabilities.getValue(RomCapability.WORLD_MAP)
        val firstLocal = first.capabilities.getValue(RomCapability.LOCAL_MAP)

        assertEquals(firstWorld.reasons.joinToString("; "), CapabilityStatus.AVAILABLE, firstWorld.status)
        assertEquals(firstLocal.reasons.joinToString("; "), CapabilityStatus.AVAILABLE, firstLocal.status)
        assertTrue("world map catalog is empty", first.worldMaps.regions.isNotEmpty())
        assertTrue("local map catalog is empty", first.localMaps.maps.isNotEmpty())
        val firstWorldProjection = worldProjection(first)
        val firstLocalProjection = localProjection(first)
        assertEquals(expectedWorldRegions, first.worldMaps.regions.size)
        assertEquals(expectedWorldRegions, first.worldMaps.assets.size)
        assertEquals(expectedLocalMaps, first.localMaps.maps.size)
        assertEquals(expectedLocalAssets, first.localMaps.assets.size)
        assertEquals(expectedWorldProjectionSha256, sha256(firstWorldProjection.joinToString("\n").toByteArray()))
        assertEquals(expectedLocalProjectionSha256, sha256(firstLocalProjection.joinToString("\n").toByteArray()))
        assertEquals(firstWorldProjection, worldProjection(second))
        assertEquals(firstLocalProjection, localProjection(second))
    }

    private fun worldProjection(catalog: ParsedCatalog): List<String> = catalog.worldMaps.regions.map { region ->
        val raster = catalog.worldMaps.assets.getValue(region.imageAssetKey)
        val bindings = region.locations.joinToString("|") { location ->
            "${location.key}:${location.baseAreaIds.sorted()}:" +
                location.geometry.joinToString(",") { "${it.x}:${it.y}:${it.width}:${it.height}" }
        }
        "${region.key}:${region.pixelWidth}x${region.pixelHeight}:" +
            "${region.gridWidth}x${region.gridHeight}:${sha256Ints(raster.argb)}:$bindings"
    }

    private fun localProjection(catalog: ParsedCatalog): List<String> = catalog.localMaps.maps
        .sortedBy { it.baseAreaId }
        .map { map ->
            val png = catalog.localMaps.assets.getValue(map.imageAssetKey).bytes
            "${map.baseAreaId}:${map.gridWidth}x${map.gridHeight}:${sha256(png)}"
        }

    private fun sha256Ints(values: IntArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES)
        values.forEach { value ->
            buffer.clear()
            buffer.putInt(value)
            digest.update(buffer.array())
        }
        return digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
