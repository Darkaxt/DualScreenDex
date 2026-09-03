package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.LocalizedTableLayout
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.language.RomLanguageProjection
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogTextProjectionTest {
    @Test
    fun defaultProjectionUsesOnlyTheManifestSelectedOverlay() {
        val catalog = catalog(
            manifest = manifest(listOf(LanguageTag.ENGLISH, LanguageTag.FRENCH)),
            overlays = mapOf(
                LanguageTag.ENGLISH to overlay(LanguageTag.ENGLISH, emptyMap()),
                LanguageTag.FRENCH to overlay(
                    LanguageTag.FRENCH,
                    mapOf(1 to CatalogField.available("Bulbizarre")),
                ),
            ),
        )

        val projection = catalog.defaultTextProjection()

        assertEquals(LanguageTag.ENGLISH, projection.language)
        assertEquals(1L, projection.overlayVersion)
        assertNull(projection.speciesName(1))
    }

    @Test
    fun selectedDefaultOverlayPublishesItsExactText() {
        val catalog = catalog(
            manifest = manifest(listOf(LanguageTag.ENGLISH)),
            overlays = mapOf(
                LanguageTag.ENGLISH to overlay(
                    LanguageTag.ENGLISH,
                    mapOf(1 to CatalogField.available("Bulbasaur")),
                ),
            ),
        )

        assertEquals("Bulbasaur", catalog.defaultTextProjection().speciesName(1))
    }

    @Test
    fun unknownLegacyFixtureMayUseSharedTextWithoutCreatingLanguageAuthority() {
        val catalog = catalog(RomLanguageManifest.UNKNOWN, emptyMap())

        val projection = catalog.defaultTextProjection()

        assertNull(projection.language)
        assertNull(projection.overlayVersion)
        assertEquals("Shared fixture", projection.speciesName(1))
    }

    private fun catalog(
        manifest: RomLanguageManifest,
        overlays: Map<LanguageTag, CatalogLanguageOverlay>,
    ) = ParsedCatalog(
        romSha256 = "a".repeat(64),
        romCrc32 = "1234ABCD",
        family = EngineFamily.RED_BLUE,
        platform = Platform.GB,
        speciesById = mapOf(
            1 to SpeciesRecord(
                id = 1,
                dexNumber = CatalogField.available(1),
                name = CatalogField.available("Shared fixture"),
                typeIds = CatalogField.available(listOf(12)),
                baseStats = CatalogField.available(BaseStats(45, 49, 49, 45, 65, 65)),
                sprite = CatalogField.notFound("fixture"),
            ),
        ),
        localization = CatalogLocalization(manifest, overlays),
    )

    private fun manifest(languages: List<LanguageTag>) = RomLanguageManifest(
        defaultLanguage = languages.first(),
        projections = languages.map { language ->
            RomLanguageProjection(
                language = language,
                codecId = "fixture-${language.value}",
                codecVersion = 1,
                localizedTables = LocalizedTableLayout(),
                evidence = emptyList(),
                status = LanguageResolutionStatus.RESOLVED,
            )
        },
        status = LanguageResolutionStatus.RESOLVED,
    )

    private fun overlay(
        language: LanguageTag,
        speciesNames: Map<Int, CatalogField<String>>,
    ) = CatalogLanguageOverlay(
        language = language,
        overlayVersion = 1,
        localizedCapabilities = LocalizedTextCapability.entries.associateWith { capability ->
            val expected = when (capability) {
                LocalizedTextCapability.SPECIES_NAMES,
                LocalizedTextCapability.SPECIES_DESCRIPTIONS,
                -> 1
                else -> 0
            }
            val covered = if (capability == LocalizedTextCapability.SPECIES_NAMES) speciesNames.size else 0
            when {
                covered == expected && expected > 0 -> LocalizedCapabilityState.available(expected)
                expected > 0 -> LocalizedCapabilityState.notFound("fixture missing", expected)
                else -> LocalizedCapabilityState.unavailable(
                    CapabilityStatus.NOT_APPLICABLE,
                    expectedRecords = 0,
                    confidence = 1.0,
                )
            }
        },
        speciesNames = speciesNames,
    )
}
