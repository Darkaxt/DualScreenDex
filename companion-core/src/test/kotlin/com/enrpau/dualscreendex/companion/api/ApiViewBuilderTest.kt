package com.enrpau.dualscreendex.companion.api

import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.EncounterArea
import com.enrpau.dualscreendex.parser.catalog.EncounterSlot
import com.enrpau.dualscreendex.parser.catalog.EncounterWindow
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
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

    @Test
    fun exposesStructuredPartialCapabilityEvidence() {
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            romCrc32 = "1234ABCD",
            family = EngineFamily.FIRERED_LEAFGREEN,
            platform = Platform.GBA,
            capabilities = mapOf(
                RomCapability.LEARNSETS to CapabilityEvidence(
                    capability = RomCapability.LEARNSETS,
                    compatible = true,
                    confidence = 0.70,
                    offset = 0x7713F8,
                    count = 10,
                    recordSize = 4,
                    elementSize = 4,
                    validRecords = 7,
                    totalRecords = 10,
                    reviewStatus = CapabilityReviewStatus.MANUAL_REVIEW,
                    status = CapabilityStatus.AVAILABLE,
                ),
            ),
        )

        val capability = ApiViewBuilder.diagnostics(
            catalog = catalog,
            romName = "partial.gba",
            activeRulesetId = null,
            rulesetAssumed = true,
            speciesId = null,
            moveId = null,
        ).capabilities.single()

        assertEquals("PARTIAL", capability.status)
        assertEquals(7, capability.validRecords)
        assertEquals(10, capability.totalRecords)
        assertEquals(4, capability.elementSize)
        assertEquals("MANUAL_REVIEW", capability.reviewStatus)
    }
}
