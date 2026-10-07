package com.enrpau.dualscreendex.companion.api

import com.enrpau.dualscreendex.parser.catalog.*
import com.enrpau.dualscreendex.parser.language.*
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.*
import org.junit.Test

class SpeciesCategoryApiTest {
    @Test fun speciesCategoryUsesSelectedOverlayAndNeverSharedOrOtherLocaleText() {
        val species = SpeciesRecord(1, dexNumber = CatalogField.available(1), name = CatalogField.available("PARTNER"),
            typeIds = CatalogField.notFound("absent"), baseStats = CatalogField.notFound("absent"),
            sprite = CatalogField.notFound("absent"), category = CatalogField.available("untrusted shared category"))
        val unknown = ParsedCatalog("0".repeat(64), EngineFamily.EMERALD, Platform.GBA,
            speciesById = mapOf(1 to species), movesById = emptyMap())
        assertNull(ApiViewBuilder.catalog(unknown).species.single().category)
        val languages = listOf(LanguageTag.ENGLISH, LanguageTag.FRENCH)
        val manifest = RomLanguageManifest(LanguageTag.ENGLISH, languages.map {
            RomLanguageProjection(it, "synthetic-${it.value}", 1, LocalizedTableLayout(), emptyList(), LanguageResolutionStatus.RESOLVED)
        }, LanguageResolutionStatus.RESOLVED)
        fun overlay(language: LanguageTag, category: String?): CatalogLanguageOverlay {
            val states = LocalizedTextCapability.entries.associateWith { LocalizedCapabilityState.notFound("absent") }.toMutableMap()
            for (key in listOf(LocalizedTextCapability.SPECIES_NAMES, LocalizedTextCapability.SPECIES_DESCRIPTIONS,
                LocalizedTextCapability.SPECIES_CATEGORIES)) states[key] = LocalizedCapabilityState.notFound("absent", 1)
            if (category != null) states[LocalizedTextCapability.SPECIES_CATEGORIES] = LocalizedCapabilityState.available(1)
            return CatalogLanguageOverlay(language, 2, states,
                speciesCategories = category?.let { mapOf(1 to CatalogField.available(it)) }.orEmpty())
        }
        val catalog = unknown.copy(localization = CatalogLocalization(manifest, mapOf(
            LanguageTag.ENGLISH to overlay(LanguageTag.ENGLISH, "SEED"), LanguageTag.FRENCH to overlay(LanguageTag.FRENCH, null))))
        assertEquals("SEED", ApiViewBuilder.catalog(catalog).species.single().category)
        val french = ActiveLanguageBindingView(catalog.romSha256, 1, 1, "fr", "LIVE_RAM", 2)
        assertNull(ApiViewBuilder.catalog(catalog, french).species.single().category)
        assertNull(catalog.textProjection(LanguageTag.FRENCH)!!.speciesCategory(1))
        assertNull(ApiViewBuilder.languageOverlay(catalog, french)!!.species.getValue(1).category)
        assertEquals("SEED", ApiViewBuilder.languageOverlay(catalog, french.copy(language = "en"))!!.species.getValue(1).category)
    }
}
