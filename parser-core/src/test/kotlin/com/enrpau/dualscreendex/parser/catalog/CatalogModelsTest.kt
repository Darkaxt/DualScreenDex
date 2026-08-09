package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogModelsTest {
    @Test
    fun catalogPreservesRomNativeIdsAndMissingFieldState() {
        val species = SpeciesRecord(
            id = 412,
            formId = 7,
            dexNumber = CatalogField.notFound("dex map was not resolved"),
            name = CatalogField.available("TESTMON"),
            typeIds = CatalogField.available(listOf(19, 19)),
            baseStats = CatalogField.notFound("base stats missing"),
            sprite = CatalogField.notFound("sprite corrupt"),
        )
        val catalog = ParsedCatalog(
            romSha256 = "abc",
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            speciesById = mapOf(species.id to species),
        )

        assertEquals(412, catalog.speciesById.getValue(412).id)
        assertEquals(7, catalog.speciesById.getValue(412).formId)
        assertEquals(CapabilityStatus.NOT_FOUND, species.dexNumber.status)
        assertEquals(null, species.dexNumber.value)
        assertEquals("TESTMON", species.name.value)
    }

    @Test
    fun rgbaSpriteRequiresExactlyOnePixelPerCoordinate() {
        assertThrows(IllegalArgumentException::class.java) {
            RgbaSprite(width = 2, height = 2, argb = intArrayOf(0, 1, 2))
        }
    }

    @Test
    fun catalogDeclaresIndependentAreaPaletteAndBallCapabilities() {
        assertEquals(true, RomCapability.entries.contains(RomCapability.AREA_ENCOUNTERS))
        assertEquals(true, RomCapability.entries.contains(RomCapability.TYPE_PRESENTATION))
        assertEquals(true, RomCapability.entries.contains(RomCapability.BALL_CATALOG))
    }
}
