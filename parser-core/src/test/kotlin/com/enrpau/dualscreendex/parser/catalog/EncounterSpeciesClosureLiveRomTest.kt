package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.RomCapability
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class EncounterSpeciesClosureLiveRomTest {
    @Test
    fun namelessPublishesOnlyEncounterSpeciesInItsAuthoritativeSpeciesDomain() {
        assertClosure(
            "DUALDEX_NAMELESS_ROM",
            "483acdef9eacbd0f98ac1ad1bfe22e9f07240a98dac14f0772d6a50465dd777d",
            EngineFamily.FIRERED_LEAFGREEN,
        )
    }

    @Test fun emeraldExClosesEveryPositiveEncounterSpecies() = assertClosure(
        "DUALDEX_EMERALD_EX_ROM",
        "431451885f665865860bc769b9343b3d682eb10d4978d06ca0b83c4542221fa3",
        EngineFamily.EMERALD,
    )

    @Test fun fusedDimensionsClosesEveryPositiveEncounterSpecies() = assertClosure(
        "DUALDEX_FUSED_DIMENSIONS_ROM",
        "d89e02430fbca8fa5792076ae18da18af8bd5e2bbd77f6e70c16327dde2a8ed2",
        EngineFamily.FIRERED_LEAFGREEN,
    )

    @Test fun rubyDestinyClosesEveryPositiveEncounterSpecies() = assertClosure(
        "DUALDEX_RUBY_DESTINY_ROM",
        "4481166c8d326e7fca3633e8bf7fcc50b8952d589f458fe6398d859983d59a6f",
        EngineFamily.RUBY_SAPPHIRE,
    )

    @Test fun saffronClosesEveryPositiveEncounterSpecies() = assertClosure(
        "DUALDEX_SAFFRON_ROM",
        "9d450338116b2e1acaae2441db13a3eff41beeccbbf08b17674f4beb2d6e41b7",
        EngineFamily.FIRERED_LEAFGREEN,
    )

    @Test fun siennaClosesEveryPositiveEncounterSpecies() = assertClosure(
        "DUALDEX_SIENNA_ROM",
        "f959eecbfe8e5dcfcfc5d505d65e6cbb1dbc724fea0c4fe1364b9903c47b66e4",
        EngineFamily.FIRERED_LEAFGREEN,
    )

    private fun assertClosure(environmentVariable: String, sha256: String, family: EngineFamily) {
        val configured = System.getenv(environmentVariable)
        assumeTrue("set $environmentVariable to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val parsed = CatalogParser.parse(RomImage(Files.readAllBytes(path)))

        assertEquals(sha256, parsed.analysis.sha256)
        assertEquals(family, parsed.analysis.selectedFamily)
        val catalog = requireNotNull(parsed.catalog)
        val missingSpeciesIds = catalog.encounterAreas.asSequence()
            .flatMap { it.slots.asSequence() }
            .map { it.speciesId }
            .filterNot { it in catalog.speciesById }
            .toSortedSet()

        assertTrue(catalog.encounterAreas.isNotEmpty())
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.AREA_ENCOUNTERS).status)
        assertEquals(emptySet<Int>(), missingSpeciesIds)
    }
}
