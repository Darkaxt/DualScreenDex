package com.enrpau.dualscreendex.companion.api

import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.BattleState
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.model.OpponentState
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanic
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicCondition
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicConditionKind
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicKind
import com.enrpau.dualscreendex.parser.catalog.AbilityRecord
import com.enrpau.dualscreendex.parser.catalog.CatalogRuntimeMetadata
import com.enrpau.dualscreendex.parser.catalog.EncounterArea
import com.enrpau.dualscreendex.parser.catalog.EncounterSlot
import com.enrpau.dualscreendex.parser.catalog.EncounterWindow
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog
import com.enrpau.dualscreendex.parser.catalog.WorldMapCell
import com.enrpau.dualscreendex.parser.catalog.WorldMapLocation
import com.enrpau.dualscreendex.parser.catalog.WorldMapRegion
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
    fun exposesOnlyNormalizedWorldMapPresentationData() {
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            worldMaps = WorldMapCatalog(
                regions = listOf(
                    WorldMapRegion(
                        key = "gen3-region-0",
                        displayName = "Hoenn",
                        pixelWidth = 224,
                        pixelHeight = 120,
                        gridWidth = 28,
                        gridHeight = 15,
                        imageAssetKey = "world/gen3-region-0",
                        locations = listOf(
                            WorldMapLocation(
                                key = "section-16",
                                displayName = "Route 101",
                                baseAreaIds = setOf(0x10, 0x11),
                                geometry = listOf(WorldMapCell(3, 11, 2, 1)),
                            ),
                        ),
                    ),
                ),
                assets = mapOf("world/gen3-region-0" to RgbaSprite(224, 120, IntArray(224 * 120))),
            ),
        )

        val map = ApiViewBuilder.catalog(catalog).worldMaps.single()

        assertEquals("gen3-region-0", map.key)
        assertEquals("Hoenn", map.displayName)
        assertEquals(224, map.pixelWidth)
        assertEquals(120, map.pixelHeight)
        assertEquals(28, map.gridWidth)
        assertEquals(15, map.gridHeight)
        assertEquals("/api/maps/world%2Fgen3-region-0.png", map.imageUrl)
        assertEquals(listOf(0x10, 0x11), map.locations.single().baseAreaIds)
        assertEquals(WorldMapCellView(3, 11, 2, 1), map.locations.single().geometry.single())
    }

    @Test
    fun exposesOnlyCatalogPublishedSemanticAbilityMechanics() {
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            speciesById = mapOf(
                1 to com.enrpau.dualscreendex.parser.catalog.SpeciesRecord(
                    id = 1,
                    dexNumber = CatalogField.available(1),
                    name = CatalogField.available("TESTMON"),
                    typeIds = CatalogField.available(emptyList()),
                    baseStats = CatalogField.notFound("fixture"),
                    sprite = CatalogField.notFound("fixture"),
                    abilityIds = CatalogField.available(listOf(37)),
                ),
            ),
            abilitiesById = mapOf(
                37 to AbilityRecord(
                    id = 37,
                    name = CatalogField.available("Huge Power"),
                    mechanics = CatalogField.available(
                        listOf(AbilityMechanic(
                            AbilityMechanicKind.MULTIPLIER,
                            "Attack",
                            "Attack ×2",
                            2,
                            1,
                            listOf(AbilityMechanicCondition(
                                AbilityMechanicConditionKind.MOVE_SPLIT,
                                0,
                                "Physical moves",
                            )),
                        )),
                    ),
                ),
            ),
        )

        val mechanic = ApiViewBuilder.catalog(catalog).species.single().abilities.single().mechanics.single()

        assertEquals("MULTIPLIER", mechanic.kind)
        assertEquals("Attack", mechanic.label)
        assertEquals("Attack ×2", mechanic.value)
        assertEquals(2, mechanic.numerator)
        assertEquals(1, mechanic.denominator)
        assertEquals("Physical moves", mechanic.conditions.single().label)
    }

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
    fun selectedMapAreaDrivesAreaDexWhilePhysicalCurrentLocationStaysTruthful() {
        val route101 = 0x0010 * 10 + 1
        val oldale = 0x0011 * 10 + 1
        val oldaleWater = 0x0012 * 10 + 1
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            speciesById = (1..2).associateWith { speciesId ->
                com.enrpau.dualscreendex.parser.catalog.SpeciesRecord(
                    id = speciesId,
                    dexNumber = CatalogField.available(speciesId),
                    name = CatalogField.available("Species $speciesId"),
                    typeIds = CatalogField.available(emptyList()),
                    baseStats = CatalogField.notFound("fixture"),
                    sprite = CatalogField.notFound("fixture"),
                    abilityIds = CatalogField.available(emptyList()),
                )
            },
            encounterAreas = listOf(
                EncounterArea(route101, CatalogField.available("Route 101 grass"), 1, listOf(EncounterSlot(1, 2, 3, 100))),
                EncounterArea(oldale, CatalogField.available("Oldale water"), 2, listOf(EncounterSlot(2, 3, 4, 100))),
                EncounterArea(oldaleWater, CatalogField.available("Oldale pond"), 3, listOf(EncounterSlot(1, 3, 4, 100))),
            ),
            runtimeMetadata = CatalogRuntimeMetadata(areaNamesByBaseId = mapOf(0x0010 to "Route 101")),
        )
        val snapshot = AppSnapshot(
            filter = com.enrpau.dualscreendex.companion.model.PokedexFilter.AREA,
            selectedAreaId = oldale,
            selectedAreaIds = setOf(oldaleWater, oldale),
            liveAreaBaseId = 0x0010,
            ledger = KnowledgeLedger(
                visitedAreaBaseIds = setOf(0x0010, 0x0011),
                seenSpeciesByArea = mapOf(0x0011 to setOf(2), 0x0012 to setOf(1)),
            ),
        )

        val state = ApiViewBuilder.state(snapshot, catalog)

        assertEquals(0x0010, state.currentAreaBaseId)
        assertEquals("Route 101", state.currentAreaName)
        assertEquals(listOf(oldale, oldaleWater), state.selectedAreaIds)
        assertEquals(listOf(oldale, oldaleWater), state.currentAreaIds)
        assertEquals(listOf(1, 2), state.currentAreaSpeciesIds)
        assertEquals(listOf(0x0010, 0x0011, 0x0012), state.revealedAreaBaseIds)
        assertEquals(listOf(0x0011), state.observedAreaBaseIdsBySpecies.getValue(2))
        assertEquals(0x0011, ApiViewBuilder.catalog(catalog).areas.single { it.id == oldale }.baseAreaId)
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
    fun areaSpeciesAreObservedLocallyWithACapturedSpeciesOverride() {
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
                    name = CatalogField.available("Route 101 grass"),
                    methodId = 1,
                    slots = listOf(EncounterSlot(1, 2, 3, 100)),
                ),
            ),
        )
        val snapshot = AppSnapshot(
            liveAreaBaseId = 0x0010,
            ledger = KnowledgeLedger(
                seenSpecies = setOf(1, 2, 3),
                caughtSpecies = setOf(4),
                seenSpeciesByArea = mapOf(
                    0x0010 to setOf(1, 2),
                    0x0011 to setOf(3),
                ),
            ),
        )

        val state = ApiViewBuilder.state(snapshot, catalog)

        assertEquals(listOf(1, 2, 4), state.currentAreaSpeciesIds)
    }
}
