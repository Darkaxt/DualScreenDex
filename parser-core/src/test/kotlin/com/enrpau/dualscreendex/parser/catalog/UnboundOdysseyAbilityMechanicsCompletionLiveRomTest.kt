package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class UnboundOdysseyAbilityMechanicsCompletionLiveRomTest {
    @Test
    fun `Unbound publishes the complete compiled ability rating domain`() {
        val first = parse(
            "D:/Temp/PokemonHacks/corpus/expanded/roms/0199-a275be0f927e/Unbound (v2.1.1.1).gba",
            "7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7",
        )
        val second = parse(
            "D:/Temp/PokemonHacks/corpus/expanded/roms/0199-a275be0f927e/Unbound (v2.1.1.1).gba",
            "7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7",
        )

        assertCompleteRatings(first, 254)
        assertCompleteParser(first)
        assertDeterministic(first, second)
        assertEquals("1", first.rating(1))
        assertEquals("9", first.rating(2))
        assertEquals("9", first.rating(3))
        assertEquals("10", first.rating(37))
        assertEquals("-2", first.rating(54))
    }

    @Test
    fun `Odyssey publishes the complete localized ability description domain`() {
        val first = parse(
            "D:/Temp/PokemonHacks/corpus/expanded/roms/0123-5e7ce46db2ce/Odyssey (v4.1.1).gba",
            "44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0",
        )
        val second = parse(
            "D:/Temp/PokemonHacks/corpus/expanded/roms/0123-5e7ce46db2ce/Odyssey (v4.1.1).gba",
            "44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0",
        )

        assertCompleteDescriptions(first, 129)
        assertCompleteParser(
            first,
            expectedNonAvailableShared = mapOf(
                RomCapability.ABILITY_MECHANICS to CapabilityStatus.PARTIAL,
            ),
        )
        assertDeterministic(first, second)
        assertEquals("Boosts team accuracy by 10%.", first.description(163))
        assertEquals("KOing a foe ups highest stat.", first.description(234))
        assertEquals("Normal moves become Aether.", first.description(235))
        assertEquals("Boosts punching moves.", first.behavior(90))
    }

    private fun assertCompleteParser(
        catalog: ParsedCatalog,
        expectedNonAvailableShared: Map<RomCapability, CapabilityStatus> = emptyMap(),
    ) {
        val localizedCapabilities = setOf(
            RomCapability.SPECIES_NAMES,
            RomCapability.POKEDEX_DESCRIPTIONS,
            RomCapability.MOVE_DESCRIPTIONS,
            RomCapability.ABILITY_DESCRIPTIONS,
        )
        assertEquals(RomCapability.entries.toSet() - localizedCapabilities, catalog.capabilities.keys)
        assertEquals(
            expectedNonAvailableShared,
            catalog.capabilities.mapValues { (_, evidence) -> evidence.status }
                .filterValues { it != CapabilityStatus.AVAILABLE },
        )
        val overlay = requireNotNull(catalog.defaultLocalizedText())
        setOf(
            LocalizedTextCapability.SPECIES_NAMES,
            LocalizedTextCapability.SPECIES_DESCRIPTIONS,
            LocalizedTextCapability.MOVE_NAMES,
            LocalizedTextCapability.MOVE_DESCRIPTIONS,
            LocalizedTextCapability.ABILITY_NAMES,
            LocalizedTextCapability.ABILITY_DESCRIPTIONS,
        ).forEach { capability ->
            val state = overlay.localizedCapabilities.getValue(capability)
            assertTrue(
                capability.name,
                state.status == CapabilityStatus.AVAILABLE || state.status == CapabilityStatus.PARTIAL,
            )
            assertTrue(capability.name, state.coveredRecords > 0)
            assertTrue(capability.name, state.expectedRecords >= state.coveredRecords)
        }
    }

    private fun assertDeterministic(first: ParsedCatalog, second: ParsedCatalog) {
        assertEquals(first.capabilities, second.capabilities)
        assertEquals(
            first.abilitiesById.mapValues { (_, ability) -> ability.mechanics.value },
            second.abilitiesById.mapValues { (_, ability) -> ability.mechanics.value },
        )
    }

    private fun assertCompleteDescriptions(catalog: ParsedCatalog, expected: Int) {
        val overlay = requireNotNull(catalog.defaultLocalizedText())
        val capability = overlay.localizedCapabilities.getValue(
            LocalizedTextCapability.ABILITY_DESCRIPTIONS,
        )
        assertEquals(CapabilityStatus.AVAILABLE, capability.status)
        assertEquals(expected, capability.coveredRecords)
        assertEquals(expected, capability.expectedRecords)
        assertEquals(expected, catalog.abilitiesById.size)
        assertEquals(catalog.abilitiesById.keys, overlay.abilityDescriptions.keys)
    }

    private fun assertCompleteRatings(catalog: ParsedCatalog, expected: Int) {
        val capability = catalog.capabilities.getValue(RomCapability.ABILITY_MECHANICS)
        assertEquals(CapabilityStatus.AVAILABLE, capability.status)
        assertEquals(expected, capability.coveredRecords)
        assertEquals(expected, capability.expectedRecords)
        assertEquals(expected, catalog.abilitiesById.size)
        assertEquals(
            expected,
            catalog.abilitiesById.values.count { ability ->
                ability.mechanics.value.orEmpty().any { it.kind == AbilityMechanicKind.AI_RATING }
            },
        )
    }

    private fun ParsedCatalog.rating(id: Int): String = abilitiesById.getValue(id).mechanics.value
        ?.single { it.kind == AbilityMechanicKind.AI_RATING }
        ?.value
        .orEmpty()

    private fun ParsedCatalog.description(id: Int): String =
        defaultTextProjection().abilityDescription(id).orEmpty()

    private fun ParsedCatalog.behavior(id: Int): String = abilitiesById.getValue(id).mechanics.value
        ?.single { it.kind == AbilityMechanicKind.BEHAVIOR }
        ?.value
        .orEmpty()

    private fun parse(path: String, sha256: String): ParsedCatalog {
        val romPath = Path.of(path)
        assumeTrue("live ROM does not exist: $romPath", Files.isRegularFile(romPath))
        val rom = RomImage(Files.readAllBytes(romPath))
        assertEquals(sha256, rom.sha256)
        return requireNotNull(CatalogParser.parse(rom).catalog)
    }
}
