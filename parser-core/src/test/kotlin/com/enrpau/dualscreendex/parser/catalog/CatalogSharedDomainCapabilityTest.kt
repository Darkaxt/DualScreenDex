package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.language.resolvedLanguageManifest
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSharedDomainCapabilityTest {
    @Test fun emptySharedDomainsCannotRetainInheritedAvailability() {
        for (capability in listOf(RomCapability.ABILITIES, RomCapability.MOVE_CATALOG)) {
            for (status in listOf(CapabilityStatus.AVAILABLE, CapabilityStatus.PARTIAL)) {
                val result = extract(capability, status).capabilities.getValue(capability)
                assertEquals("$capability/$status", CapabilityStatus.NOT_FOUND, result.status)
                assertFalse(result.compatible)
                assertEquals(0.0, result.confidence, 0.0)
                assertEquals(0, result.count)
            }
        }
    }

    @Test fun emptyAmbiguousAndInapplicableDomainsKeepTheirDispositionAndReview() {
        for (status in listOf(CapabilityStatus.AMBIGUOUS, CapabilityStatus.NOT_APPLICABLE, CapabilityStatus.NOT_FOUND)) {
            val result = extract(RomCapability.ABILITIES, status).capabilities.getValue(RomCapability.ABILITIES)
            assertEquals(status, result.status)
            assertFalse(result.compatible)
            assertEquals(0, result.count)
            assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, result.reviewStatus)
            assertTrue(result.validatorReviewRecommended)
        }
    }

    @Test fun nonemptySharedIdsRemainIndependentOfLocalizedTextAvailability() {
        val result = extract(RomCapability.ABILITIES, CapabilityStatus.NOT_FOUND,
            mapOf(42 to AbilityRecord(42, CatalogField.notFound("generated native ID without owned text"))))
        val evidence = result.capabilities.getValue(RomCapability.ABILITIES)
        assertTrue(evidence.compatible)
        assertEquals(CapabilityStatus.AVAILABLE, evidence.status)
        assertEquals(1, evidence.count)
        assertEquals(setOf(42), result.abilitiesById.keys)
        assertTrue(result.localization.overlays.values.all { it.abilityNames.isEmpty() })
    }

    @Test fun nonemptyAmbiguousDomainsCannotBecomeAvailable() {
        val result = extract(RomCapability.ABILITIES, CapabilityStatus.AMBIGUOUS,
            mapOf(42 to AbilityRecord(42, CatalogField.notFound("generated unresolved text"))))
        assertEquals(CapabilityStatus.AMBIGUOUS, result.capabilities.getValue(RomCapability.ABILITIES).status)
        assertTrue(result.localization.overlays.values.all { it.abilityNames.isEmpty() })
    }

    private fun extract(
        capability: RomCapability,
        status: CapabilityStatus,
        abilities: Map<Int, AbilityRecord> = emptyMap(),
    ): CatalogLocalizedTextExtraction = CatalogLocalizedTextExtractor.extract(
        manifest = resolvedLanguageManifest(PokemonTextCodec.gbaEnglish),
        speciesById = emptyMap(), movesById = emptyMap(), abilitiesById = abilities, naturesById = emptyMap(),
        capabilities = mapOf(capability to CapabilityEvidence(
            capability, status in listOf(CapabilityStatus.AVAILABLE, CapabilityStatus.PARTIAL), 0.9,
            count = 12, status = status, reviewStatus = CapabilityReviewStatus.MANUAL_REVIEW,
            validatorReviewRecommended = true,
        )),
    )
}
