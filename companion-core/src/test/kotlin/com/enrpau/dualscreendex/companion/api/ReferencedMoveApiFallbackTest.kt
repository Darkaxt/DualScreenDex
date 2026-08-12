package com.enrpau.dualscreendex.companion.api

import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.MoveRecord
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

class ReferencedMoveApiFallbackTest {
    @Test
    fun exposesTheEstablishedIdFallbackWithoutFabricatingAParserName() {
        val move = MoveRecord(
            id = 729,
            name = CatalogField.notFound("referenced move has no decoded ROM name"),
            typeId = CatalogField.available(9),
            category = CatalogField.notFound("category is irrelevant to this API boundary test"),
            power = CatalogField.available(127),
            accuracy = CatalogField.available(0),
            pp = CatalogField.available(8),
        )
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            movesById = mapOf(move.id to move),
        )

        assertEquals(null, move.name.value)
        assertEquals("#729", ApiViewBuilder.catalog(catalog).moves.single().name)
    }
}
