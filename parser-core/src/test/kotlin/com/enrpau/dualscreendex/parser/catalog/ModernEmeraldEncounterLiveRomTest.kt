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

class ModernEmeraldEncounterLiveRomTest {
    @Test
    fun selectsTheMainOverworldTableInsteadOfTheBattlePyramidFacilityTable() {
        val configured = System.getenv("DUALDEX_MODERN_EMERALD_ROM")
        assumeTrue("set DUALDEX_MODERN_EMERALD_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val parsed = CatalogParser.parse(RomImage(Files.readAllBytes(path)))
        assertEquals(
            "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
            parsed.analysis.sha256,
        )
        val catalog = requireNotNull(parsed.catalog)
        val capability = catalog.capabilities.getValue(RomCapability.AREA_ENCOUNTERS)

        assertEquals(CapabilityStatus.AVAILABLE, capability.status)
        assertEquals(0xBD34E0, capability.offset)
        assertEquals(20, capability.recordSize)
        assertEquals(231, catalog.encounterAreas.size)
        assertEquals(133, catalog.encounterAreas.map { it.id / 10 }.distinct().size)
        val capableAreas = catalog.encounterAreas.filter { area ->
            area.slots.any { slot ->
                slot.speciesId == 290 && 2 in slot.minimumLevel..slot.maximumLevel
            }
        }
        assertEquals(listOf(161), capableAreas.map { it.id })
        assertEquals(listOf(16), capableAreas.map { it.id / 10 })
        assertTrue(requireNotNull(capableAreas.single().name.value).startsWith("Map ").not())
        assertTrue(requireNotNull(catalog.runtimeMetadata.gen3SaveBlock1PointerAddress) in 0x02000000L..0x03FFFFFFL)
        assertEquals("Oldale Town", catalog.runtimeMetadata.areaNamesByBaseId[0x0202])
        assertTrue(capability.reasons.single().contains("headers=272"))
        assertTrue(capability.reasons.single().contains("references=11"))
        assertTrue(capability.reasons.single().contains("candidates="))
    }

    @Test
    fun selectsTheReferencedBlazedGlazedOverworldTableInsteadOfFacilityTables() {
        val configured = System.getenv("DUALDEX_BLAZED_GLAZED_ROM")
        assumeTrue("set DUALDEX_BLAZED_GLAZED_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val parsed = CatalogParser.parse(RomImage(Files.readAllBytes(path)))
        assertEquals(
            "0b55d44bfd32a350202c0878754cfcacbbaee128de3b59297ee669b69269199f",
            parsed.analysis.sha256,
        )
        val catalog = requireNotNull(parsed.catalog)
        val capability = catalog.capabilities.getValue(RomCapability.AREA_ENCOUNTERS)

        assertEquals(CapabilityStatus.AVAILABLE, capability.status)
        assertEquals(0x11DE94C, capability.offset)
        assertEquals(20, capability.recordSize)
        assertEquals(328, catalog.encounterAreas.size)
        assertEquals(187, catalog.encounterAreas.map { it.id / 10 }.distinct().size)
        assertTrue(capability.reasons.single().contains("headers=195"))
        assertTrue(capability.reasons.single().contains("populatedMethods=336"))
        assertTrue(capability.reasons.single().contains("references=13"))
        assertTrue(capability.reasons.single().contains("authority=compiled-reference-and-structural-dominance"))
    }
}
