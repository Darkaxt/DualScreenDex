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
    fun worldMapCatalogRequiresEveryRegionToOwnAMatchingRaster() {
        val region = WorldMapRegion(
            key = "region-0",
            displayName = "Test Region",
            pixelWidth = 16,
            pixelHeight = 8,
            gridWidth = 2,
            gridHeight = 1,
            imageAssetKey = "world/region-0",
            locations = listOf(
                WorldMapLocation(
                    key = "location-0",
                    displayName = "Test Route",
                    baseAreaIds = setOf(7),
                    geometry = listOf(WorldMapCell(0, 0, 1, 1)),
                ),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            WorldMapCatalog(regions = listOf(region))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorldMapCatalog(
                regions = listOf(region),
                assets = mapOf("world/region-0" to RgbaSprite(8, 8, IntArray(64))),
            )
        }

        val raster = RgbaSprite(16, 8, IntArray(128) { 0xff123456.toInt() })
        assertEquals(
            raster,
            WorldMapCatalog(
                regions = listOf(region),
                assets = mapOf("world/region-0" to raster),
            ).assets.getValue("world/region-0"),
        )
    }

    @Test
    fun catalogDeclaresIndependentAreaPaletteAndBallCapabilities() {
        assertEquals(true, RomCapability.entries.contains(RomCapability.AREA_ENCOUNTERS))
        assertEquals(true, RomCapability.entries.contains(RomCapability.TYPE_PRESENTATION))
        assertEquals(true, RomCapability.entries.contains(RomCapability.BALL_CATALOG))
        assertEquals(true, RomCapability.entries.contains(RomCapability.WORLD_MAP))
    }

    @Test
    fun navigableSpeciesExcludesNoneAndReservedInternalSlots() {
        fun species(id: Int, dex: Int, name: String = if (dex == 0) "RESERVED" else "MON$dex") = SpeciesRecord(
            id = id,
            dexNumber = CatalogField.available(dex),
            name = CatalogField.available(name),
            typeIds = CatalogField.available(listOf(0)),
            baseStats = CatalogField.notFound("fixture"),
            sprite = CatalogField.notFound("fixture"),
        )
        val catalog = ParsedCatalog(
            romSha256 = "abc",
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            speciesById = mapOf(
                0 to species(0, 0),
                1 to species(1, 1),
                252 to species(252, 0),
                440 to species(440, 437, "?"),
            ),
        )

        assertEquals(listOf(1), catalog.navigableSpecies().map { it.id })
    }
}
