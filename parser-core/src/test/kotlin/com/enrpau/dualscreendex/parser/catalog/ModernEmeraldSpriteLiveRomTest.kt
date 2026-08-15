package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.sprite.SpriteMaterializer
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ModernEmeraldSpriteLiveRomTest {
    @Test
    fun excludesSourceDeclaredUnusedRowsFromCompleteSpriteCoverage() {
        val configured = System.getenv("DUALDEX_MODERN_EMERALD_ROM")
        assumeTrue("set DUALDEX_MODERN_EMERALD_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        val parsed = CatalogParser.parse(rom)

        assertEquals(
            "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
            parsed.analysis.sha256,
        )
        val layout = requireNotNull(parsed.layout)
        val spriteTable = requireNotNull(layout.tables.sprites)
        val decodedIds = SpriteMaterializer.pokemon(rom, layout).keys
        val missingPhysicalIds = (0 until spriteTable.count).filterNot(decodedIds::contains).toSet()

        assertEquals(462, spriteTable.count)
        assertEquals(setOf(456, 457, 459, 460, 461), missingPhysicalIds)

        val catalog = requireNotNull(parsed.catalog)
        val navigable = catalog.navigableSpecies()
        assertEquals(428, navigable.size)
        assertTrue(navigable.none { it.id in missingPhysicalIds })
        assertTrue(navigable.all { it.sprite.status == CapabilityStatus.AVAILABLE })

        val capability = catalog.capabilities.getValue(RomCapability.SPRITES)
        assertEquals(CapabilityStatus.AVAILABLE, capability.status)
        assertEquals(428, capability.coveredRecords)
        assertEquals(428, capability.expectedRecords)
        assertEquals(0, capability.incompleteRecords)
        assertTrue(capability.reasons.any { "compiled-referenced species-to-Dex map" in it })
    }
}
