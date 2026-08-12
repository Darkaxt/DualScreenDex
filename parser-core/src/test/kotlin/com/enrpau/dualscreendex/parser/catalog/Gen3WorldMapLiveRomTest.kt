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

class Gen3WorldMapLiveRomTest {
    @Test
    fun modernEmeraldResolvesItsRomDerivedOverview() {
        val configured = System.getenv("DUALDEX_MODERN_EMERALD_ROM")
        assumeTrue("set DUALDEX_MODERN_EMERALD_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(
            "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
            rom.sha256,
        )
        val parsed = CatalogParser.parse(rom)
        val catalog = requireNotNull(parsed.catalog)

        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.WORLD_MAP).status)
        val world = catalog.worldMaps
        val region = world.regions.single()
        assertEquals(224, region.pixelWidth)
        assertEquals(120, region.pixelHeight)
        assertTrue(region.locations.size >= 40)
        assertTrue(region.locations.all { it.baseAreaIds.isNotEmpty() })
        assertTrue(world.assets.getValue(region.imageAssetKey).argb.any { it != 0 })
    }
}
