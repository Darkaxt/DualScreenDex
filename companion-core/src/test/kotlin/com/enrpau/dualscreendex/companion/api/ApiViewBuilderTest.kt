package com.enrpau.dualscreendex.companion.api

import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.EncounterArea
import com.enrpau.dualscreendex.parser.catalog.EncounterSlot
import com.enrpau.dualscreendex.parser.catalog.EncounterWindow
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiViewBuilderTest {
    @Test
    fun exposesParsedEncounterWindows() {
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.CRYSTAL,
            platform = Platform.GBC,
            encounterAreas = listOf(
                EncounterArea(
                    id = 17,
                    name = CatalogField.available("Route 29 - night grass"),
                    methodId = 7,
                    slots = listOf(EncounterSlot(19, 2, 4, 30)),
                    windows = setOf(EncounterWindow.NIGHT),
                ),
            ),
        )

        assertEquals(listOf("NIGHT"), ApiViewBuilder.catalog(catalog).areas.single().windows)
    }
}
