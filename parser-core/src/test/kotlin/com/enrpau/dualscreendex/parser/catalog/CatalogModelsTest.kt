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

    @Test
    fun worldMapCatalogPreservesFourIndependentRegionIdentitiesAndAssets() {
        val regions = listOf("kanto", "sevii-123", "sevii-45", "sevii-67").mapIndexed { index, key ->
            WorldMapRegion(
                key = key,
                displayName = null,
                pixelWidth = 176,
                pixelHeight = 120,
                gridWidth = 22,
                gridHeight = 15,
                imageAssetKey = "world/$key",
                locations = listOf(
                    WorldMapLocation(
                        key = "section-$index",
                        displayName = "Region $index",
                        baseAreaIds = setOf(index),
                        geometry = listOf(WorldMapCell(index, 0, 1, 1)),
                    ),
                ),
            )
        }
        val assets = regions.associate { region ->
            region.imageAssetKey to RgbaSprite(176, 120, IntArray(176 * 120) { 0xff000000.toInt() })
        }

        val catalog = WorldMapCatalog(regions, assets)

        assertEquals(listOf("kanto", "sevii-123", "sevii-45", "sevii-67"), catalog.regions.map { it.key })
        assertEquals(regions.map { it.imageAssetKey }.toSet(), catalog.assets.keys)
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
