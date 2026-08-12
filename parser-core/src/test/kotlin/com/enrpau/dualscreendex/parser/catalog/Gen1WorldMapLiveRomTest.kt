package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen1WorldMapLiveRomTest {
    @Test
    fun officialRedResolvesDeterministicallyFromExactSourceBuiltRom() {
        val configured = System.getenv("DUALDEX_GEN1_OFFICIAL_ROM")
        assumeTrue("set DUALDEX_GEN1_OFFICIAL_ROM to run the official Red control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("official ROM does not exist: $path", Files.isRegularFile(path))
        val bytes = Files.readAllBytes(path)
        val rom = RomImage(bytes)
        assertEquals(OFFICIAL_RED_SHA256, rom.sha256)

        val first = requireNotNull(CatalogParser.parse(rom).catalog)
        val second = requireNotNull(CatalogParser.parse(RomImage(bytes)).catalog)
        assertEquals(CapabilityStatus.AVAILABLE, first.capabilities.getValue(RomCapability.WORLD_MAP).status)
        val region = first.worldMaps.regions.single()
        assertEquals(160, region.pixelWidth)
        assertEquals(144, region.pixelHeight)
        assertTrue(region.locations.any { it.baseAreaIds.isNotEmpty() })
        assertEquals(worldMapFingerprint(first.worldMaps), worldMapFingerprint(second.worldMaps))
    }

    private fun worldMapFingerprint(catalog: WorldMapCatalog): List<Any> = buildList {
        catalog.regions.forEach { region ->
            add(region.copy(locations = region.locations.map { it.copy(baseAreaIds = it.baseAreaIds.toSortedSet()) }))
            add(catalog.assets.getValue(region.imageAssetKey).argb.contentHashCode())
        }
    }

    companion object {
        private const val OFFICIAL_RED_SHA256 =
            "5ca7ba01642a3b27b0cc0b5349b52792795b62d3ed977e98a09390659af96b7b"
    }
}
