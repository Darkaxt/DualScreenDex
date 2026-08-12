package com.enrpau.dualscreendex.companion.api

import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.BattleState
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.model.OpponentState
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.CatalogRuntimeMetadata
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
import org.junit.Assert.assertNull
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
                    baseAreaId = 1,
                    name = CatalogField.available("Route 29 - night grass"),
                    methodId = 7,
                    slots = listOf(EncounterSlot(19, 2, 4, 30)),
                    windows = setOf(EncounterWindow.NIGHT),
                ),
            ),
        )

        val area = ApiViewBuilder.catalog(catalog).areas.single()
        assertEquals(listOf("NIGHT"), area.windows)
        assertEquals(1, area.baseAreaId)
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

    @Test
    fun projectsStructuredRarityFromMatchedCurrentAreaEvidence() {
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            encounterAreas = listOf(
                EncounterArea(
                    id = 0x0203 * 10 + 1,
                    baseAreaId = 0x0203,
                    name = CatalogField.available("Test grass"),
                    methodId = 1,
                    slots = listOf(
                        EncounterSlot(1, 14, 14, 1),
                        EncounterSlot(2, 10, 10, 1_000),
                    ),
                ),
            ),
        )
        val snapshot = AppSnapshot(
            ledger = KnowledgeLedger(currentAreaBaseId = 0x0203),
            battle = BattleState(
                opponents = listOf(
                    OpponentState(1, 14, ivs = List(6) { 24 }, moveHistory = emptyList()),
                ),
            ),
        )

        val rarity = ApiViewBuilder.state(snapshot, catalog, saveRam = SaveRamView(status = "MATCHED"))
            .battle!!.opponents.single().rarity

        assertEquals("STRONG", rarity.relativeTier)
        assertEquals("VETERAN", rarity.innateTier)
        assertEquals(3, rarity.baseStars)
        assertEquals(0.5, rarity.areaAdjustment)
        assertEquals(3.5, rarity.stars)
    }

    @Test
    fun doesNotGuessAnAreaFromTheOpponentWhenSaveRamIsNotMatched() {
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            encounterAreas = listOf(
                EncounterArea(
                    id = 0x0203 * 10 + 1,
                    baseAreaId = 0x0203,
                    name = CatalogField.available("Test grass"),
                    methodId = 1,
                    slots = listOf(EncounterSlot(1, 14, 14, 100)),
                ),
            ),
        )
        val snapshot = AppSnapshot(
            ledger = KnowledgeLedger(currentAreaBaseId = 0x0203),
            battle = BattleState(
                opponents = listOf(
                    OpponentState(1, 14, ivs = List(6) { 24 }, moveHistory = emptyList()),
                ),
            ),
        )

        val rarity = ApiViewBuilder.state(snapshot, catalog, saveRam = SaveRamView(status = "STALE"))
            .battle!!.opponents.single().rarity

        assertNull(rarity.relativeTier)
        assertNull(rarity.areaAdjustment)
        assertEquals("AREA_UNAVAILABLE", rarity.areaOutcome)
        assertEquals("VETERAN", rarity.innateTier)
        assertEquals(3.0, rarity.stars)
    }

    @Test
    fun preservesTheOfflineSaveAreaWithoutGuessingAnotherEncounterTable() {
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            encounterAreas = listOf(
                EncounterArea(
                    id = 0x0203 * 10 + 1,
                    baseAreaId = 0x0203,
                    name = CatalogField.available("Test grass"),
                    methodId = 1,
                    slots = listOf(EncounterSlot(1, 14, 14, 100)),
                ),
            ),
        )
        val snapshot = AppSnapshot(
            ledger = KnowledgeLedger(currentAreaBaseId = 0x0202),
            battle = BattleState(
                opponents = listOf(OpponentState(1, 14, ivs = List(6) { 24 }, moveHistory = emptyList())),
            ),
        )

        val state = ApiViewBuilder.state(snapshot, catalog, saveRam = SaveRamView(status = "MATCHED"))
        val rarity = state.battle!!.opponents.single().rarity

        assertEquals(0x0202, state.currentAreaBaseId)
        assertEquals(emptyList<Int>(), state.currentAreaIds)
        assertEquals("AREA_NOT_IN_CATALOG", rarity.areaOutcome)
        assertEquals(0x0202, rarity.currentAreaBaseId)
        assertEquals(0, rarity.matchingAreaCount)
        assertEquals(1, rarity.candidateAreaCount)
        assertNull(rarity.relativeTier)
    }

    @Test
    fun liveMemoryAreaOverridesTheStaleSaveAndPublishesTheRomDerivedName() {
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            encounterAreas = listOf(
                EncounterArea(
                    id = 0x0010 * 10 + 1,
                    baseAreaId = 0x0010,
                    name = CatalogField.available("Route 101 grass"),
                    methodId = 1,
                    slots = listOf(EncounterSlot(1, 10, 10, 100)),
                ),
            ),
            runtimeMetadata = CatalogRuntimeMetadata(areaNamesByBaseId = mapOf(0x0010 to "Route 101")),
        )
        val snapshot = AppSnapshot(
            ledger = KnowledgeLedger(currentAreaBaseId = 0x0202),
            liveAreaBaseId = 0x0010,
            battle = BattleState(
                opponents = listOf(OpponentState(1, 10, ivs = List(6) { 20 }, moveHistory = emptyList())),
            ),
        )

        val state = ApiViewBuilder.state(snapshot, catalog, saveRam = SaveRamView(status = "MATCHED"))
        val rarity = state.battle!!.opponents.single().rarity

        assertEquals(0x0010, state.currentAreaBaseId)
        assertEquals("Route 101", state.currentAreaName)
        assertEquals("Route 101", rarity.currentAreaName)
        assertEquals("ORDINARY", rarity.relativeTier)
        assertEquals("TRAINED", rarity.innateTier)
    }

    @Test
    fun connectedRuntimeDoesNotUseTheDiskSaveAreaWhenLiveRamHasNoLocation() {
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            encounterAreas = listOf(
                EncounterArea(
                    id = 0x0202 * 10 + 1,
                    baseAreaId = 0x0202,
                    name = CatalogField.available("Stale saved area grass"),
                    methodId = 1,
                    slots = listOf(EncounterSlot(1, 10, 10, 100)),
                ),
            ),
        )
        val snapshot = AppSnapshot(
            ledger = KnowledgeLedger(currentAreaBaseId = 0x0202),
            liveAreaBaseId = null,
        )

        val state = ApiViewBuilder.state(
            snapshot,
            catalog,
            retroArch = RetroArchView(connection = "CONNECTED"),
            saveRam = SaveRamView(status = "MATCHED"),
        )

        assertNull(state.currentAreaBaseId)
        assertNull(state.currentAreaName)
        assertEquals(emptyList<Int>(), state.currentAreaIds)
    }

    @Test
    fun caughtSpeciesWithoutALocalObservationIsAbsentFromAreaSpecies() {
        val state = ApiViewBuilder.state(
            areaSnapshot(caught = setOf(4), observedHere = setOf(1, 2)),
            areaCatalog(),
        )

        assertEquals(listOf(1, 2), state.currentAreaSpeciesIds)
        assertEquals(listOf(1, 2), state.activeAreaSpeciesIds)
    }

    @Test
    fun locallyObservedSpeciesIsPresentWithoutBeingCaught() {
        val state = ApiViewBuilder.state(
            areaSnapshot(observedHere = setOf(2)),
            areaCatalog(),
        )

        assertEquals(listOf(2), state.currentAreaSpeciesIds)
        assertEquals(listOf(2), state.activeAreaSpeciesIds)
    }

    @Test
    fun caughtAndLocallyObservedSpeciesRemainsPresentBecauseOfTheObservation() {
        val state = ApiViewBuilder.state(
            areaSnapshot(caught = setOf(2), observedHere = setOf(2)),
            areaCatalog(),
        )

        assertEquals(listOf(2), state.currentAreaSpeciesIds)
        assertEquals(listOf(2), state.activeAreaSpeciesIds)
    }

    @Test
    fun selectedAreaUsesExplicitBaseIdentityWithoutReplacingTheCurrentArea() {
        val currentBaseId = 0x0010
        val selectedBaseId = 0x0011
        val selectedAreaId = selectedBaseId * 100 + 21
        val catalog = areaCatalog().copy(
            encounterAreas = listOf(
                EncounterArea(
                    id = currentBaseId * 10 + 1,
                    baseAreaId = currentBaseId,
                    name = CatalogField.available("Route 101 - grass"),
                    methodId = 1,
                    slots = listOf(EncounterSlot(1, 2, 3, 100)),
                ),
                EncounterArea(
                    id = selectedAreaId,
                    baseAreaId = selectedBaseId,
                    name = CatalogField.available("Oldale Town - day grass"),
                    methodId = 1,
                    slots = listOf(EncounterSlot(3, 2, 3, 100)),
                ),
            ),
            runtimeMetadata = CatalogRuntimeMetadata(
                areaNamesByBaseId = mapOf(
                    currentBaseId to "Route 101",
                    selectedBaseId to "Oldale Town",
                ),
            ),
        )
        val snapshot = areaSnapshot(observedHere = setOf(1)).copy(
            selectedAreaId = selectedAreaId,
            ledger = areaSnapshot(observedHere = setOf(1)).ledger.copy(
                seenSpeciesByArea = mapOf(
                    currentBaseId to setOf(1),
                    selectedBaseId to setOf(3),
                ),
            ),
        )

        val state = ApiViewBuilder.state(snapshot, catalog)

        assertEquals(currentBaseId, state.currentAreaBaseId)
        assertEquals("Route 101", state.currentAreaName)
        assertEquals(listOf(currentBaseId * 10 + 1), state.currentAreaIds)
        assertEquals(selectedBaseId, state.activeAreaBaseId)
        assertEquals("Oldale Town", state.activeAreaName)
        assertEquals(listOf(selectedAreaId), state.activeAreaIds)
        assertEquals(listOf(3), state.activeAreaSpeciesIds)
        assertEquals(false, state.activeAreaIsCurrent)
    }

    @Test
    fun selectedEncounterMethodInTheCurrentAreaRetainsTheCurrentMarker() {
        val catalog = areaCatalog()
        val selectedAreaId = catalog.encounterAreas.single().id
        val state = ApiViewBuilder.state(
            areaSnapshot(observedHere = setOf(1)).copy(selectedAreaId = selectedAreaId),
            catalog,
        )

        assertEquals(true, state.activeAreaIsCurrent)
    }

    @Test
    fun invalidSelectedAreaFallsBackToTheCurrentArea() {
        val state = ApiViewBuilder.state(
            areaSnapshot(observedHere = setOf(1)).copy(selectedAreaId = Int.MAX_VALUE),
            areaCatalog(),
        )

        assertEquals(0x0010, state.activeAreaBaseId)
        assertEquals("Route 101", state.activeAreaName)
        assertEquals(true, state.activeAreaIsCurrent)
    }

    private fun areaCatalog(): ParsedCatalog {
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            speciesById = listOf(1, 2, 3, 4).associateWith { speciesId ->
                com.enrpau.dualscreendex.parser.catalog.SpeciesRecord(
                    id = speciesId,
                    dexNumber = CatalogField.available(speciesId),
                    name = CatalogField.available("SPECIES $speciesId"),
                    typeIds = CatalogField.available(emptyList()),
                    baseStats = CatalogField.notFound("fixture"),
                    sprite = CatalogField.notFound("fixture"),
                    abilityIds = CatalogField.available(emptyList()),
                )
            },
            encounterAreas = listOf(
                EncounterArea(
                    id = 0x0010 * 10 + 1,
                    baseAreaId = 0x0010,
                    name = CatalogField.available("Route 101 grass"),
                    methodId = 1,
                    slots = listOf(EncounterSlot(1, 2, 3, 100)),
                ),
            ),
            runtimeMetadata = CatalogRuntimeMetadata(areaNamesByBaseId = mapOf(0x0010 to "Route 101")),
        )
        return catalog
    }

    private fun areaSnapshot(
        caught: Set<Int> = emptySet(),
        observedHere: Set<Int> = emptySet(),
    ) = AppSnapshot(
        liveAreaBaseId = 0x0010,
        ledger = KnowledgeLedger(
            seenSpecies = observedHere + caught,
            caughtSpecies = caught,
            seenSpeciesByArea = mapOf(
                0x0010 to observedHere,
            ),
        ),
    )
}
