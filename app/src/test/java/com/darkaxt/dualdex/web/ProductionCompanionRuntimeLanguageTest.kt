package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.battle.BattleEncounterKind
import com.darkaxt.dualdex.battle.ContentLanguageReadOutcome
import com.darkaxt.dualdex.battle.LiveBattleState
import com.darkaxt.dualdex.live.TransientGameStateContext
import com.darkaxt.dualdex.live.UnifiedGameStateDecoder
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.CatalogLanguageOverlay
import com.enrpau.dualscreendex.parser.catalog.CatalogLocalization
import com.enrpau.dualscreendex.parser.catalog.LocalizedCapabilityState
import com.enrpau.dualscreendex.parser.catalog.LocalizedTextCapability
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.language.LanguageEvidence
import com.enrpau.dualscreendex.parser.language.LanguageEvidenceKind
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.LocalizedTableLayout
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.language.RomLanguageProjection
import com.enrpau.dualscreendex.parser.language.RuntimeLanguageMemorySpace
import com.enrpau.dualscreendex.parser.language.RuntimeLanguageSelectionCandidate
import com.enrpau.dualscreendex.parser.language.RuntimeLanguageSelectionResolver
import com.enrpau.dualscreendex.parser.language.RuntimeLanguageValueMapping
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionCompanionRuntimeLanguageTest {
    @Test
    fun publishesOnlyTheExactlyBoundLiveProjection() {
        val identity = "a".repeat(64)
        val stateOwner = UnifiedGameStateDecoder()
        val runtime = ProductionCompanionRuntime(transientGameState = stateOwner)
        runtime.loadCatalog("multilingual.gb", multilingualCatalog(identity, includeRuntimeSelection = true))

        val offline = runtime.bootstrap()
        assertEquals("en", offline.language?.activeLanguage)
        assertEquals("ROM_DEFAULT", offline.language?.authority)
        assertNull(offline.language?.binding?.contextEpoch)
        assertEquals("Bulbasaur", offline.catalog?.species?.single()?.name)

        val context = requireNotNull(runtime.battleCatalogContext())
        stateOwner.beginSession(
            TransientGameStateContext(
                romIdentity = context.romIdentity,
                generation = context.generation,
                catalog = context.catalog,
                runtimeLanguageSelection = context.runtimeLanguageSelection,
            ),
        )
        stateOwner.acceptExistingGenerationSample(
            sampleId = 1,
            battle = LiveBattleState(false, null, BattleEncounterKind.UNKNOWN),
            areaBaseId = null,
            mapPosition = null,
            clock = null,
            contentLanguage = ContentLanguageReadOutcome.Live(LanguageTag.FRENCH),
        )

        val live = runtime.bootstrap()
        assertTrue(live.state.version > offline.state.version)
        val binding = requireNotNull(live.language?.binding)
        assertEquals("fr", binding.language)
        assertEquals("LIVE_RAM", binding.authority)
        assertEquals(identity, binding.romSha256)
        assertNotNull(binding.contextEpoch)
        assertNotNull(binding.stateVersion)
        assertTrue(binding.contextEpoch!! >= 0)
        assertTrue(binding.stateVersion!! > 0)
        assertEquals(8L, binding.projectionVersion)
        assertEquals(binding, live.state.activeLanguage)
        assertEquals("Bulbizarre", live.catalog?.species?.single()?.name)

        val overlay = runtime.activeLanguageOverlay()
        assertEquals(binding, overlay.binding)
        assertEquals("Bulbizarre", overlay.species.getValue(1).name)

        stateOwner.acceptExistingGenerationSample(
            sampleId = 2,
            battle = LiveBattleState(false, null, BattleEncounterKind.UNKNOWN),
            areaBaseId = null,
            mapPosition = null,
            clock = null,
            contentLanguage = ContentLanguageReadOutcome.Live(LanguageTag.ENGLISH),
        )
        val changedAgain = runtime.bootstrap()
        assertTrue(
            "state delivery version did not advance: ${live.state.version} -> ${changedAgain.state.version}",
            changedAgain.state.version > live.state.version,
        )
        assertEquals("en", changedAgain.language?.activeLanguage)
        assertEquals("LIVE_RAM", changedAgain.language?.authority)
        assertEquals("Bulbasaur", changedAgain.catalog?.species?.single()?.name)
        runtime.close()
    }

    @Test
    fun rejectsLiveProjectionWithoutPersistedRuntimeSelectorAuthority() {
        val identity = "b".repeat(64)
        val stateOwner = UnifiedGameStateDecoder()
        val runtime = ProductionCompanionRuntime(transientGameState = stateOwner)
        runtime.loadCatalog("multilingual.gb", multilingualCatalog(identity, includeRuntimeSelection = false))

        val context = requireNotNull(runtime.battleCatalogContext())
        stateOwner.beginSession(
            TransientGameStateContext(
                romIdentity = context.romIdentity,
                generation = context.generation,
                catalog = context.catalog,
            ),
        )
        stateOwner.acceptExistingGenerationSample(
            sampleId = 1,
            battle = LiveBattleState(false, null, BattleEncounterKind.UNKNOWN),
            areaBaseId = null,
            mapPosition = null,
            clock = null,
            contentLanguage = ContentLanguageReadOutcome.Live(LanguageTag.FRENCH),
        )

        val bootstrap = runtime.bootstrap()
        assertEquals("en", bootstrap.language?.activeLanguage)
        assertEquals("ROM_DEFAULT", bootstrap.language?.authority)
        assertEquals("Bulbasaur", bootstrap.catalog?.species?.single()?.name)
        assertEquals("en", runtime.activeLanguageOverlay().binding.language)
        runtime.close()
    }

    private fun multilingualCatalog(
        identity: String,
        includeRuntimeSelection: Boolean,
    ): ParsedCatalog {
        val projections = listOf(LanguageTag.ENGLISH, LanguageTag.FRENCH).map { language ->
            RomLanguageProjection(
                language = language,
                codecId = "fixture-${language.value}",
                codecVersion = 1,
                localizedTables = LocalizedTableLayout(),
                evidence = emptyList(),
                status = LanguageResolutionStatus.RESOLVED,
            )
        }
        val baseManifest = RomLanguageManifest(
            defaultLanguage = LanguageTag.ENGLISH,
            projections = projections,
            status = LanguageResolutionStatus.RESOLVED,
        )
        val selector = RuntimeLanguageSelectionResolver.resolve(
            baseManifest,
            listOf(
                RuntimeLanguageSelectionCandidate(
                    memorySpace = RuntimeLanguageMemorySpace.GB_WRAM,
                    offset = 0x123,
                    readWidthBytes = 1,
                    mask = 0xff,
                    shift = 0,
                    defaultValue = 0,
                    mappings = listOf(
                        RuntimeLanguageValueMapping(0, LanguageTag.ENGLISH),
                        RuntimeLanguageValueMapping(1, LanguageTag.FRENCH),
                    ),
                    evidence = listOf(
                        LanguageEvidence(LanguageEvidenceKind.COMPILED_CONSUMER, "fixture read", 100),
                        LanguageEvidence(LanguageEvidenceKind.TABLE_RELATIONSHIP, "fixture mapping", 100),
                    ),
                ),
            ),
        )
        val manifest = if (includeRuntimeSelection) {
            baseManifest.withRuntimeSelection(requireNotNull(selector))
        } else {
            baseManifest
        }
        return ParsedCatalog(
            romSha256 = identity,
            family = EngineFamily.RED_BLUE,
            platform = Platform.GB,
            speciesById = mapOf(
                1 to SpeciesRecord(
                    id = 1,
                    dexNumber = CatalogField.available(1),
                    name = CatalogField.notFound("localized"),
                    typeIds = CatalogField.available(emptyList()),
                    baseStats = CatalogField.notFound("fixture"),
                    sprite = CatalogField.notFound("fixture"),
                ),
            ),
            localization = CatalogLocalization(
                manifest = manifest,
                overlays = mapOf(
                    LanguageTag.ENGLISH to overlay(LanguageTag.ENGLISH, 7, "Bulbasaur"),
                    LanguageTag.FRENCH to overlay(LanguageTag.FRENCH, 8, "Bulbizarre"),
                ),
            ),
        )
    }

    private fun overlay(language: LanguageTag, version: Long, speciesName: String) = CatalogLanguageOverlay(
        language = language,
        overlayVersion = version,
        localizedCapabilities = LocalizedTextCapability.entries.associateWith { capability ->
            when (capability) {
                LocalizedTextCapability.SPECIES_NAMES -> LocalizedCapabilityState.available(1)
                LocalizedTextCapability.SPECIES_DESCRIPTIONS ->
                    LocalizedCapabilityState.notFound("fixture", expectedRecords = 1)
                else -> LocalizedCapabilityState.notApplicable("fixture")
            }
        },
        speciesNames = mapOf(1 to CatalogField.available(speciesName)),
    )
}
