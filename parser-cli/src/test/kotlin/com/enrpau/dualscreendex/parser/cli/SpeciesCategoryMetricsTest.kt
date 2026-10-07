package com.enrpau.dualscreendex.parser.cli

import com.enrpau.dualscreendex.parser.catalog.*
import com.enrpau.dualscreendex.parser.language.*
import com.enrpau.dualscreendex.parser.model.*
import org.junit.Assert.*
import org.junit.Test

class SpeciesCategoryMetricsTest {
    @Test fun categoryCounterUsesSelectedLocalizedNativeSpeciesNotSharedText() {
        val species = SpeciesRecord(1, dexNumber = CatalogField.available(1), name = CatalogField.available("PARTNER"),
            typeIds = CatalogField.notFound("none"), baseStats = CatalogField.notFound("none"), sprite = CatalogField.notFound("none"),
            category = CatalogField.available("untrusted shared category"))
        val unknown = ParsedCatalog("0".repeat(64), EngineFamily.EMERALD, Platform.GBA,
            speciesById = mapOf(1 to species), movesById = emptyMap())
        assertEquals(0, CatalogMetrics.from(unknown).speciesWithCategories)
        val manifest = RomLanguageManifest(LanguageTag.ENGLISH, listOf(RomLanguageProjection(LanguageTag.ENGLISH,
            "synthetic", 1, LocalizedTableLayout(), emptyList(), LanguageResolutionStatus.RESOLVED)), LanguageResolutionStatus.RESOLVED)
        val states = LocalizedTextCapability.entries.associateWith { LocalizedCapabilityState.notFound("none") }.toMutableMap()
        states[LocalizedTextCapability.SPECIES_NAMES] = LocalizedCapabilityState.notFound("none", 1)
        states[LocalizedTextCapability.SPECIES_DESCRIPTIONS] = LocalizedCapabilityState.notFound("none", 1)
        states[LocalizedTextCapability.SPECIES_CATEGORIES] = LocalizedCapabilityState.available(1)
        val overlay = CatalogLanguageOverlay(LanguageTag.ENGLISH, 2, states,
            speciesCategories = mapOf(1 to CatalogField.available("SEED")))
        val selected = unknown.copy(localization = CatalogLocalization(manifest, mapOf(LanguageTag.ENGLISH to overlay)))
        val metrics = CatalogMetrics.from(selected)
        assertEquals(1, metrics.speciesWithCategories)
        assertEquals(1, metrics.localizedCapabilities.getValue("SPECIES_CATEGORIES").coveredRecords)
        assertEquals(1, metrics.localizedCapabilities.getValue("SPECIES_CATEGORIES").expectedRecords)
        assertEquals(0, metrics.speciesWithDescriptions)
    }
}
