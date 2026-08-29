package com.enrpau.dualscreendex.companion.map

import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.io.RomImage
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class AreaGuideBuilderRealControlTest {
    @Test
    fun `Modern Emerald keeps the Littleroot town sign on the map without duplicating the guide heading`() {
        val configured = System.getenv("DUALDEX_MODERN_EMERALD_ROM")
        assumeTrue("set DUALDEX_MODERN_EMERALD_ROM to run this real-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(MODERN_EMERALD_SHA, rom.sha256)
        val catalog = requireNotNull(CatalogParser.parseCatching(rom).catalog).getOrThrow()

        val projection = AreaGuideBuilder.project(
            catalog,
            AppSnapshot(
                liveAreaBaseId = LITTLEROOT_TOWN,
                settings = CompanionSettings(knowledgeMode = KnowledgeMode.DISCOVERED),
            ),
        )
        val sign = projection.points.single {
            it.baseAreaId == LITTLEROOT_TOWN && it.tileX == 15 && it.tileY == 13
        }

        assertEquals("Littleroot Town", sign.label)
        assertNull(
            projection.guide.areas.single { it.baseAreaId == LITTLEROOT_TOWN }
                .placesAndServices.single { it.key == sign.key }.label,
        )
    }

    private companion object {
        const val LITTLEROOT_TOWN = 0x0009
        const val MODERN_EMERALD_SHA = "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895"
    }
}
