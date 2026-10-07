package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.language.*
import com.enrpau.dualscreendex.parser.model.*
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.*
import org.junit.Test

class SpeciesCategoryOverlayTest {
    @Test fun strictCategoryIsLocalizedIndependentOfUnavailableProse() {
        val records = mapOf(1 to species().copy(category = CatalogField.available("SEED")))
        val extraction = CatalogLocalizedTextExtractor.extract(resolvedLanguageManifest(PokemonTextCodec.gbaEnglish),
            records, emptyMap(), abilitiesById = emptyMap(), naturesById = emptyMap(), capabilities = mapOf(
                RomCapability.POKEDEX_DESCRIPTIONS to CapabilityEvidence(RomCapability.POKEDEX_DESCRIPTIONS,
                    false, 0.0, status = CapabilityStatus.NOT_FOUND)))
        assertNull(extraction.speciesById.getValue(1).category.value)
        val overlay = extraction.localization.defaultOverlay()!!
        assertEquals("SEED", overlay.speciesCategories.getValue(1).value)
        assertEquals(LocalizedCapabilityState.available(1), overlay.localizedCapabilities[LocalizedTextCapability.SPECIES_CATEGORIES])
        assertTrue(overlay.speciesDescriptions.isEmpty())
        assertEquals(2L, overlay.overlayVersion)
    }

    @Test fun categoryMapSnapshotsEqualityCoverageAndBoundedValues() {
        val input = linkedMapOf(1 to CatalogField.available("SEED"))
        val selected = overlay(input)
        input[1] = CatalogField.available("MOUSE")
        assertEquals("SEED", selected.speciesCategories.getValue(1).value)
        assertEquals(overlay(mapOf(1 to CatalogField.available("SEED"))), selected)
        assertEquals(overlay(mapOf(1 to CatalogField.available("SEED"))).hashCode(), selected.hashCode())
        assertNotEquals(overlay(mapOf(1 to CatalogField.available("MOUSE"))), selected)
        assertThrows(UnsupportedOperationException::class.java) { (selected.speciesCategories as MutableMap)[2] = CatalogField.available("MOUSE") }
        for (value in listOf("", " ", "A".repeat(4097))) assertThrows(IllegalArgumentException::class.java) {
            overlay(mapOf(1 to CatalogField.available(value)))
        }
        assertThrows(IllegalArgumentException::class.java) { overlay(mapOf(1 to CatalogField.notFound("unvalidated"))) }
        assertThrows(IllegalArgumentException::class.java) { overlay(mapOf(0 to CatalogField.available("NONE"))) }
    }

    @Test fun categoryDomainExcludesOnlyProvedNotApplicableAndRejectsUnknownKeys() {
        val manifest = resolvedLanguageManifest(PokemonTextCodec.gbaEnglish)
        val selected = overlay(mapOf(1 to CatalogField.available("SEED")))
        val catalog = ParsedCatalog("0".repeat(64), EngineFamily.EMERALD, Platform.GBA,
            speciesById = mapOf(1 to species()), movesById = emptyMap(),
            localization = CatalogLocalization(manifest, mapOf(selected.language to selected)))
        assertEquals("SEED", catalog.defaultTextProjection().speciesCategory(1))
        assertThrows(IllegalArgumentException::class.java) { catalog.copy(speciesById = mapOf(1 to species().copy(
            category = CatalogField.notApplicable("native excluded domain")))) }
        assertThrows(IllegalArgumentException::class.java) { catalog.copy(speciesById = mapOf(2 to species().copy(id = 2))) }
        val excluded = SpeciesRecord(0, dexNumber = CatalogField.notApplicable("native sentinel"),
            name = CatalogField.notFound("none"), typeIds = CatalogField.notFound("none"), baseStats = CatalogField.notFound("none"),
            sprite = CatalogField.notFound("none"))
        val extraction = CatalogLocalizedTextExtractor.extract(manifest,
            mapOf(0 to excluded, 1 to species().copy(description = CatalogField.notApplicable("native exclusion"))),
            emptyMap(), abilitiesById = emptyMap(), naturesById = emptyMap(), capabilities = emptyMap())
        assertEquals(0, extraction.localization.defaultOverlay()!!.localizedCapabilities
            .getValue(LocalizedTextCapability.SPECIES_CATEGORIES).expectedRecords)
    }

    private fun species() = SpeciesRecord(1, dexNumber = CatalogField.available(1), name = CatalogField.notFound("localized"),
        typeIds = CatalogField.notFound("none"), baseStats = CatalogField.notFound("none"), sprite = CatalogField.notFound("none"))

    private fun overlay(categories: Map<Int, CatalogField<String>>): CatalogLanguageOverlay {
        val states = LocalizedTextCapability.entries.associateWith { LocalizedCapabilityState.notFound("none") }.toMutableMap()
        states[LocalizedTextCapability.SPECIES_NAMES] = LocalizedCapabilityState.notFound("none", 1)
        states[LocalizedTextCapability.SPECIES_DESCRIPTIONS] = LocalizedCapabilityState.notFound("none", 1)
        states[LocalizedTextCapability.SPECIES_CATEGORIES] = LocalizedCapabilityState.available(1)
        return CatalogLanguageOverlay(LanguageTag.ENGLISH, 2, states, speciesCategories = categories)
    }
}
