package com.enrpau.dualscreendex.companion.api

import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.PartyMemberDetails
import com.darkaxt.dualdex.save.TrainerIdentity
import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.GameClock
import com.enrpau.dualscreendex.companion.model.GameClockPhase
import com.enrpau.dualscreendex.companion.model.BattleState
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.companion.model.LiveMapPosition
import com.enrpau.dualscreendex.companion.model.OpponentState
import com.enrpau.dualscreendex.companion.model.ResolvedPokedexProjection
import com.enrpau.dualscreendex.companion.model.TrainerCardState
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanic
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicCondition
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicConditionKind
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicKind
import com.enrpau.dualscreendex.parser.catalog.AbilityRecord
import com.enrpau.dualscreendex.parser.catalog.CatalogRuntimeMetadata
import com.enrpau.dualscreendex.parser.catalog.CatalogTheme
import com.enrpau.dualscreendex.parser.catalog.CatalogThemeAssetClass
import com.enrpau.dualscreendex.parser.catalog.CatalogThemeMethod
import com.enrpau.dualscreendex.parser.catalog.CatalogThemeTokens
import com.enrpau.dualscreendex.parser.catalog.EncounterArea
import com.enrpau.dualscreendex.parser.catalog.EncounterSlot
import com.enrpau.dualscreendex.parser.catalog.EncounterWindow
import com.enrpau.dualscreendex.parser.catalog.IndexedMapAsset
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.LocalMapLightingPolicy
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoi
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiItem
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiOrganicVisibility
import com.enrpau.dualscreendex.parser.catalog.LocalMapRasterCodec
import com.enrpau.dualscreendex.parser.catalog.LocalMapScene
import com.enrpau.dualscreendex.parser.catalog.LocalMapScenePlacement
import com.enrpau.dualscreendex.parser.catalog.MapLightingPalettes
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.PngMapAsset
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.TrainerAssetCatalog
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
import com.enrpau.dualscreendex.parser.dataset.natures.NatureFlavor
import com.enrpau.dualscreendex.parser.dataset.natures.NatureRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiViewBuilderTest {
    @Test
    fun organicPoiProjectionNeverLeaksUndiscoveredHiddenCoordinatesOrItemIdentity() {
        val map = LocalMap("local/0102", "Route", 0x0102, 160, 160, 10, 10, "local/0102/map")
        val visible = LocalMapPoi(
            "local/0102/object/0", map.key, map.baseAreaId, 2, 2,
            LocalMapPoiKind.VISIBLE_ITEM,
            item = LocalMapPoiItem(13, "Potion", 0x52),
        )
        val hidden = LocalMapPoi(
            "local/0102/bg/0", map.key, map.baseAreaId, 4, 4,
            LocalMapPoiKind.HIDDEN_ITEM,
            LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE,
            item = LocalMapPoiItem(110, "Nugget", 0x3E8),
        )
        val catalog = ParsedCatalog(
            "a".repeat(64), EngineFamily.EMERALD, Platform.GBA,
            localMaps = LocalMapCatalog(
                maps = listOf(map),
                assets = mapOf(
                    map.imageAssetKey to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)),
                ),
                pois = listOf(visible, hidden),
            ),
        )

        val initial = ApiViewBuilder.state(
            AppSnapshot(settings = CompanionSettings(knowledgeMode = KnowledgeMode.ORGANIC)),
            catalog,
        )
        val revealed = ApiViewBuilder.state(
            AppSnapshot(
                settings = CompanionSettings(knowledgeMode = KnowledgeMode.ORGANIC),
                ledger = KnowledgeLedger(proximityRevealedPoiKeys = setOf(hidden.key)),
            ),
            catalog,
        )
        val discovered = ApiViewBuilder.state(
            AppSnapshot(settings = CompanionSettings(knowledgeMode = KnowledgeMode.DISCOVERED)),
            catalog,
        )

        assertEquals(listOf(visible.key), initial.localMapPois.map { it.key })
        assertEquals("SILHOUETTE", initial.localMapPois.single().state)
        assertNull(initial.localMapPois.single().itemId)
        assertEquals(setOf(visible.key, hidden.key), revealed.localMapPois.mapTo(mutableSetOf()) { it.key })
        assertEquals("SILHOUETTE", revealed.localMapPois.single { it.key == hidden.key }.state)
        assertNull(revealed.localMapPois.single { it.key == hidden.key }.itemId)
        assertEquals(setOf(13, 110), discovered.localMapPois.mapNotNullTo(mutableSetOf()) { it.itemId })
        assertEquals(setOf("IDENTIFIED"), discovered.localMapPois.mapTo(mutableSetOf()) { it.state })
    }

    @Test
    fun identifiedEntranceUsesGenderConditionedSignContentAndNeverItsGenericTownFallback() {
        val outside = LocalMap("local/0009", "Littleroot Town", 0x0009, 320, 320, 20, 20, "local/0009/map")
        val house = LocalMap("local/0100", "Brendan's House", 0x0100, 160, 160, 10, 10, "local/0100/map")
        val entrance = LocalMapPoi(
            "local/0009/bg/2", outside.key, outside.baseAreaId, 7, 8,
            LocalMapPoiKind.PLACE,
            LocalMapPoiOrganicVisibility.ENTRANCE_PROXIMITY,
            displayNamesByTrainerGender = mapOf(
                0 to "{PLAYER}'s HOUSE",
                1 to "PROF. BIRCH'S HOUSE",
            ),
            destinationBaseAreaId = house.baseAreaId,
        )
        val catalog = ParsedCatalog(
            "a".repeat(64), EngineFamily.EMERALD, Platform.GBA,
            localMaps = LocalMapCatalog(
                maps = listOf(outside, house),
                assets = mapOf(
                    outside.imageAssetKey to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)),
                    house.imageAssetKey to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)),
                ),
                pois = listOf(entrance),
            ),
        )

        val view = ApiViewBuilder.state(
            AppSnapshot(
                settings = CompanionSettings(knowledgeMode = KnowledgeMode.ORGANIC),
                ledger = KnowledgeLedger(
                    proximityRevealedPoiKeys = setOf(entrance.key),
                    identifiedPoiKeys = setOf(entrance.key),
                ),
                trainerCardState = identityOnlyTrainerCard("BRENDAN", 0),
            ),
            catalog,
        ).localMapPois.single()

        assertEquals("IDENTIFIED", view.state)
        assertEquals("BRENDAN's HOUSE", view.displayName)
        assertEquals(house.baseAreaId, view.destinationBaseAreaId)
    }

    @Test
    fun identifiedEntranceUsesTheUnifiedResolvedTrainerIdentity() {
        val outside = LocalMap("local/0009", "Littleroot Town", 0x0009, 320, 320, 20, 20, "local/0009/map")
        val entrance = LocalMapPoi(
            "local/0009/bg/3", outside.key, outside.baseAreaId, 12, 8,
            LocalMapPoiKind.PLACE,
            LocalMapPoiOrganicVisibility.ENTRANCE_PROXIMITY,
            displayName = "PROF. BIRCH'S HOUSE",
            displayNamesByTrainerGender = mapOf(
                0 to "PROF. BIRCH'S HOUSE",
                1 to "{PLAYER}'s HOUSE",
            ),
            destinationBaseAreaId = 0x0102,
        )
        val catalog = ParsedCatalog(
            "a".repeat(64), EngineFamily.EMERALD, Platform.GBA,
            localMaps = LocalMapCatalog(
                maps = listOf(outside),
                assets = mapOf(outside.imageAssetKey to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))),
                pois = listOf(entrance),
            ),
        )

        val view = ApiViewBuilder.state(
            AppSnapshot(
                settings = CompanionSettings(knowledgeMode = KnowledgeMode.ORGANIC),
                ledger = KnowledgeLedger(
                    proximityRevealedPoiKeys = setOf(entrance.key),
                    identifiedPoiKeys = setOf(entrance.key),
                ),
                trainerCardState = identityOnlyTrainerCard("MAY", 1),
            ),
            catalog,
        ).localMapPois.single()

        assertEquals("MAY's HOUSE", view.displayName)
    }

    @Test
    fun identifiedEntranceWithoutTrainerUsesItsFirstDecodedSignHeadlineNotCombinedAlternatives() {
        val outside = LocalMap("local/0009", "Littleroot Town", 0x0009, 320, 320, 20, 20, "local/0009/map")
        val entrance = LocalMapPoi(
            "local/0009/bg/2", outside.key, outside.baseAreaId, 7, 8,
            LocalMapPoiKind.PLACE,
            LocalMapPoiOrganicVisibility.ENTRANCE_PROXIMITY,
            displayName = "{PLAYER}'s House",
            displayNamesByTrainerGender = mapOf(
                0 to "{PLAYER}'s House",
                1 to "PROF. BIRCH'S HOUSE",
            ),
            destinationBaseAreaId = 0x0100,
        )
        val catalog = ParsedCatalog(
            "a".repeat(64), EngineFamily.EMERALD, Platform.GBA,
            localMaps = LocalMapCatalog(
                maps = listOf(outside),
                assets = mapOf(outside.imageAssetKey to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))),
                pois = listOf(entrance),
            ),
        )

        val view = ApiViewBuilder.state(
            AppSnapshot(
                settings = CompanionSettings(knowledgeMode = KnowledgeMode.ORGANIC),
                ledger = KnowledgeLedger(
                    proximityRevealedPoiKeys = setOf(entrance.key),
                    identifiedPoiKeys = setOf(entrance.key),
                ),
            ),
            catalog,
        ).localMapPois.single()

        assertEquals("Your House", view.displayName)
    }

    @Test
    fun exposesTheCompletePersistedRomThemeWithoutReinterpretingTokens() {
        val theme = CatalogTheme(
            CatalogThemeMethod.MULTI_ASSET_QUANTIZATION,
            setOf(CatalogThemeAssetClass.SPECIES, CatalogThemeAssetClass.WORLD_MAP),
            true,
            CatalogThemeTokens(
                0x123456, 0x234567, 0x345678, 0x102030,
                0xE4D6A8, 0x75694B, 0xFFF7DB, 0x4D4032,
                0x1C201D, 0xFFFFFF, 0x9D302A, 0xFFFFFF,
            ),
        )
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            theme = theme,
        )

        val view = ApiViewBuilder.catalog(catalog).theme

        assertEquals("MULTI_ASSET_QUANTIZATION", view.method)
        assertEquals(listOf("WORLD_MAP", "SPECIES"), view.assetClasses)
        assertEquals(true, view.contrastCorrected)
        assertEquals("#123456", view.tokens.field)
        assertEquals("#fff7db", view.tokens.panel)
        assertEquals("#ffffff", view.tokens.accentText)
    }

    @Test
    fun exposesNormalizedGameClockPhaseWithoutReinterpretingIt() {
        val state = ApiViewBuilder.state(
            AppSnapshot(gameTime = GameClock(21, 0, GameClockPhase.NIGHT, 0.0)),
            catalog = null,
        )
        val phaseOnly = ApiViewBuilder.state(
            AppSnapshot(gameTime = GameClock(phase = GameClockPhase.DARK)),
            catalog = null,
        )

        assertEquals(21, state.gameTime?.hours)
        assertEquals(0, state.gameTime?.minutes)
        assertEquals("NIGHT", state.gameTime?.phase)
        assertEquals(0.0, requireNotNull(state.gameTime?.phaseProgress), 0.0)
        assertNull(phaseOnly.gameTime?.hours)
        assertNull(phaseOnly.gameTime?.minutes)
        assertEquals("DARK", phaseOnly.gameTime?.phase)
        assertNull(phaseOnly.gameTime?.phaseProgress)
    }

    @Test
    fun presentsGen1StaticSceneAndSoleNativeMapSpriteWithoutTrainerGender() {
        val route1 = LocalMap("local/000c", "Route 1", 0x000c, 320, 576, 20, 36, "local/000c/map")
        val palletTown = LocalMap("local/0000", "Pallet Town", 0x0000, 320, 288, 20, 18, "local/0000/map")
        val png = PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        val assetKey = "trainer/overworld/player"
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.RED_BLUE,
            platform = Platform.GB,
            localMaps = LocalMapCatalog(
                maps = listOf(route1, palletTown),
                assets = mapOf(route1.imageAssetKey to png, palletTown.imageAssetKey to png),
                scenes = listOf(
                    LocalMapScene(
                        key = "scene/0000",
                        gridWidth = 20,
                        gridHeight = 54,
                        placements = listOf(
                            LocalMapScenePlacement(route1.key, route1.baseAreaId, 0, 0),
                            LocalMapScenePlacement(palletTown.key, palletTown.baseAreaId, 0, 36),
                        ),
                    ),
                ),
            ),
            trainerAssets = TrainerAssetCatalog(
                overworldAssetKeys = mapOf(0 to assetKey),
                assets = mapOf(assetKey to RgbaSprite(16, 16, IntArray(16 * 16))),
            ),
        )

        val scene = ApiViewBuilder.catalog(catalog).mapScenes.single()
        val state = ApiViewBuilder.state(
            AppSnapshot(liveAreaBaseId = palletTown.baseAreaId, liveMapPosition = LiveMapPosition(4, 5)),
            catalog,
        )

        assertEquals(320, scene.pixelWidth)
        assertEquals(864, scene.pixelHeight)
        assertEquals(listOf(0, 576), scene.placements.map { it.pixelY })
        assertTrue(scene.placements.none { it.dynamicLighting })
        assertEquals("/api/trainer-assets/trainer%2Foverworld%2Fplayer.png", state.trainerMapSpriteUrl)
        assertEquals(16, state.trainerMapSpriteWidth)
        assertEquals(16, state.trainerMapSpriteHeight)
        assertEquals(palletTown.baseAreaId, state.currentAreaBaseId)
        assertEquals(MapPositionView(4, 5), state.currentMapPosition)
        assertNull(state.trainerAvatarUrl)
    }

    @Test
    fun presentsGen2TimedSceneWithNativeMapSprite() {
        val route29 = LocalMap("local/1803", "Route 29", 0x1803, 960, 288, 60, 18, "local/1803/map")
        val newBark = LocalMap("local/1804", "New Bark Town", 0x1804, 320, 288, 20, 18, "local/1804/map")
        val palettes = MapLightingPalettes(
            morning = IntArray(32),
            day = IntArray(32),
            night = IntArray(32),
            dark = IntArray(32),
        )
        val indexedAssets = listOf(route29, newBark).associate { map ->
            map.imageAssetKey to IndexedMapAsset(
                pixelWidth = map.pixelWidth,
                pixelHeight = map.pixelHeight,
                compressedIndices = LocalMapRasterCodec.compress(ByteArray(map.pixelWidth * map.pixelHeight)),
                lightingPolicy = LocalMapLightingPolicy.AUTO,
                palettes = palettes,
            )
        }
        val assetKey = "trainer/overworld/player"
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.GOLD_SILVER,
            platform = Platform.GBC,
            localMaps = LocalMapCatalog(
                maps = listOf(route29, newBark),
                indexedAssets = indexedAssets,
                scenes = listOf(
                    LocalMapScene(
                        key = "scene/1803",
                        gridWidth = 80,
                        gridHeight = 18,
                        placements = listOf(
                            LocalMapScenePlacement(route29.key, route29.baseAreaId, 0, 0),
                            LocalMapScenePlacement(newBark.key, newBark.baseAreaId, 60, 0),
                        ),
                    ),
                ),
            ),
            trainerAssets = TrainerAssetCatalog(
                overworldAssetKeys = mapOf(0 to assetKey),
                assets = mapOf(assetKey to RgbaSprite(16, 16, IntArray(16 * 16))),
            ),
        )

        val scene = ApiViewBuilder.catalog(catalog).mapScenes.single()
        val state = ApiViewBuilder.state(
            AppSnapshot(liveAreaBaseId = newBark.baseAreaId, liveMapPosition = LiveMapPosition(4, 5)),
            catalog,
        )

        assertEquals(1280, scene.pixelWidth)
        assertEquals(288, scene.pixelHeight)
        assertEquals(listOf(0, 960), scene.placements.map { it.pixelX })
        assertEquals(listOf(960, 320), scene.placements.map { it.pixelWidth })
        assertTrue(scene.placements.all { it.dynamicLighting })
        assertEquals("/api/trainer-assets/trainer%2Foverworld%2Fplayer.png", state.trainerMapSpriteUrl)
        assertEquals(16, state.trainerMapSpriteWidth)
        assertEquals(16, state.trainerMapSpriteHeight)
        assertEquals(newBark.baseAreaId, state.currentAreaBaseId)
        assertEquals(MapPositionView(4, 5), state.currentMapPosition)
    }

    @Test
    fun presentsTrainerAndPartyThroughCatalogLabelsAndNormalizedAssets() {
        val species = com.enrpau.dualscreendex.parser.catalog.SpeciesRecord(
            id = 25,
            dexNumber = CatalogField.available(25),
            name = CatalogField.available("PIKACHU"),
            typeIds = CatalogField.available(listOf(13)),
            baseStats = CatalogField.notFound("fixture"),
            sprite = CatalogField.available(RgbaSprite(64, 64, IntArray(64 * 64))),
            abilityIds = CatalogField.available(listOf(9)),
        )
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            speciesById = mapOf(25 to species),
            movesById = mapOf(
                85 to com.enrpau.dualscreendex.parser.catalog.MoveRecord(
                    id = 85,
                    name = CatalogField.available("Thunderbolt"),
                    typeId = CatalogField.available(13),
                    category = CatalogField.available(com.enrpau.dualscreendex.parser.catalog.MoveCategory.SPECIAL),
                    power = CatalogField.available(90),
                    accuracy = CatalogField.available(100),
                    pp = CatalogField.available(15),
                ),
            ),
            abilitiesById = mapOf(
                9 to AbilityRecord(id = 9, name = CatalogField.available("Static")),
            ),
            naturesById = mapOf(
                3 to NatureRecord(
                    id = 3,
                    name = "Resolute",
                    statModifiers = listOf(1, 0, 0, -1, 0),
                    positivePercent = 112,
                    negativePercent = 88,
                    flavorModifiers = listOf(1, -1, 0, 0, 0),
                ),
            ),
            trainerAssets = TrainerAssetCatalog(
                avatarAssetKeys = mapOf(0 to "trainer/avatar/male", 1 to "trainer/avatar/female"),
                overworldAssetKeys = mapOf(0 to "trainer/overworld/male", 1 to "trainer/overworld/female"),
                badgeAssetKeys = (1..8).map { "trainer/badge/$it" },
                assets = buildMap {
                    put("trainer/avatar/male", RgbaSprite(64, 64, IntArray(64 * 64)))
                    put("trainer/avatar/female", RgbaSprite(64, 64, IntArray(64 * 64)))
                    put("trainer/overworld/male", RgbaSprite(16, 32, IntArray(16 * 32)))
                    put("trainer/overworld/female", RgbaSprite(16, 32, IntArray(16 * 32)))
                    (1..8).forEach { put("trainer/badge/$it", RgbaSprite(16, 16, IntArray(16 * 16))) }
                },
            ),
        )
        val snapshot = AppSnapshot(
            trainerCardState = TrainerCardState(
                identity = TrainerIdentity("MAY", 1),
                publicTrainerId = 12345,
                money = 98765,
                playTimeHours = 12,
                playTimeMinutes = 34,
                badgeFlags = 0b0000_0101,
                dexSeen = 42,
                dexCaught = 7,
                stars = 2,
            ),
            party = listOf(
                OwnedIndividual(
                    stableLocation = "party-0",
                    speciesId = 25,
                    level = 18,
                    ivs = List(6) { 31 },
                    experience = 9000,
                    details = PartyMemberDetails(
                        nickname = "SPARK",
                        natureId = 3,
                        heldItemId = 999,
                        abilityId = 9,
                        currentHp = 31,
                        maximumHp = 45,
                        status = 0x40,
                        stats = listOf(45, 28, 22, 38, 30, 26),
                        moveIds = listOf(85, 999, 0, 0),
                        movePp = listOf(12, 4, 0, 0),
                        experienceProgress = 0.5,
                    ),
                ),
            ),
        )

        val state = ApiViewBuilder.state(snapshot, catalog)
        val catalogView = ApiViewBuilder.catalog(catalog)

        assertEquals("MAY", state.trainer?.name)
        assertEquals("/api/trainer-assets/trainer%2Favatar%2Ffemale.png", state.trainer?.avatarUrl)
        assertEquals("/api/trainer-assets/trainer%2Favatar%2Ffemale.png", state.trainerAvatarUrl)
        assertEquals("/api/trainer-assets/trainer%2Foverworld%2Ffemale.png", state.trainerMapSpriteUrl)
        assertEquals(16, state.trainerMapSpriteWidth)
        assertEquals(32, state.trainerMapSpriteHeight)
        assertEquals(listOf(true, false, true), state.trainer?.badges?.take(3)?.map { it.earned })
        assertEquals("/api/trainer-assets/trainer%2Fbadge%2F1.png", state.trainer?.badges?.first()?.imageUrl)
        assertEquals(6, state.party.size)
        val lead = state.party.first()
        assertEquals(true, lead.occupied)
        assertEquals("PIKACHU", lead.speciesName)
        assertEquals("/api/sprites/species/25.png", lead.spriteUrl)
        assertEquals("Static", lead.abilityName)
        assertEquals(3, lead.natureId)
        assertEquals("Resolute", lead.nature)
        assertEquals("Resolute", catalogView.natures.single().name)
        assertEquals(112, catalogView.natures.single().statMultipliers.getValue("ATTACK"))
        assertEquals(88, catalogView.natures.single().statMultipliers.getValue("SPECIAL_ATTACK"))
        assertEquals(NatureFlavor.SPICY.name, catalogView.natures.single().likedFlavor)
        assertEquals("Thunderbolt", lead.moves[0].name)
        assertNull(lead.moves[1].moveId)
        assertNull(lead.moves[1].name)
        assertNull(lead.heldItemId)
        assertNull(lead.heldItemName)
        assertEquals(true, lead.hasHeldItem)
        assertEquals("PAR", lead.status)
        assertEquals("ACE", lead.rarity?.innateTier)
        assertEquals(5.0, lead.rarity?.stars)
        assertEquals(false, state.party[1].occupied)
        assertNull(state.party[1].hasHeldItem)
    }

    @Test
    fun exposesAnUnlockedPartialTrainerCardFromLiveIdentity() {
        val catalog = ParsedCatalog(
            romSha256 = "a".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            trainerAssets = TrainerAssetCatalog(
                avatarAssetKeys = mapOf(0 to "trainer/avatar/male", 1 to "trainer/avatar/female"),
                overworldAssetKeys = mapOf(0 to "trainer/overworld/male", 1 to "trainer/overworld/female"),
                assets = mapOf(
                    "trainer/avatar/male" to RgbaSprite(64, 64, IntArray(64 * 64)),
                    "trainer/avatar/female" to RgbaSprite(64, 64, IntArray(64 * 64)),
                    "trainer/overworld/male" to RgbaSprite(16, 32, IntArray(16 * 32)),
                    "trainer/overworld/female" to RgbaSprite(16, 32, IntArray(16 * 32)),
                ),
            ),
        )

        val state = ApiViewBuilder.state(
            AppSnapshot(
                ledger = KnowledgeLedger(trainerCardUnlocked = true),
                trainerCardState = identityOnlyTrainerCard("MAY", 1),
            ),
            catalog,
        )

        assertTrue(state.trainerCardUnlocked)
        assertEquals("MAY", state.trainer?.name)
        assertEquals("FEMALE", state.trainer?.gender)
        assertNull(state.trainer?.publicTrainerId)
        assertNull(state.trainer?.money)
        assertNull(state.trainer?.playTimeHours)
        assertNull(state.trainer?.playTimeMinutes)
        assertNull(state.trainer?.dexSeen)
        assertNull(state.trainer?.dexCaught)
        assertNull(state.trainer?.stars)
        assertTrue(state.trainer!!.badges.all { it.earned == null })
        assertEquals("/api/trainer-assets/trainer%2Favatar%2Ffemale.png", state.trainerAvatarUrl)
        assertEquals(state.trainerAvatarUrl, state.trainer.avatarUrl)
        assertEquals("/api/trainer-assets/trainer%2Foverworld%2Ffemale.png", state.trainerMapSpriteUrl)
        assertEquals(16, state.trainerMapSpriteWidth)
        assertEquals(32, state.trainerMapSpriteHeight)
    }

    @Test
    fun serializedBattleStateNeverInventsUnobservedEnemyMoves() {
        val snapshot = AppSnapshot(
            battle = BattleState(
                opponents = listOf(OpponentState(25, 12, moveHistory = emptyList())),
            ),
            ledger = KnowledgeLedger(observedMoves = mapOf(25 to emptyList())),
        )

        val state = ApiViewBuilder.state(snapshot, catalog = null)

        assertEquals(emptyList<ObservedMoveView>(), state.battle?.opponents?.single()?.moves)
    }

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
            localMaps = LocalMapCatalog(
                maps = listOf(
                    LocalMap("local/0010", "Route 101", 0x10, 320, 320, 20, 20, "local/0010/map"),
                    LocalMap("local/0011", "Route 102", 0x11, 16, 16, 1, 1, "local/0011/map"),
                ),
                assets = mapOf(
                    "local/0010/map" to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)),
                ),
                indexedAssets = mapOf(
                    "local/0011/map" to IndexedMapAsset(
                        pixelWidth = 16,
                        pixelHeight = 16,
                        compressedIndices = LocalMapRasterCodec.compress(ByteArray(16 * 16)),
                        lightingPolicy = LocalMapLightingPolicy.AUTO,
                        palettes = MapLightingPalettes(
                            morning = IntArray(32),
                            day = IntArray(32),
                            night = IntArray(32),
                            dark = IntArray(32),
                        ),
                    ),
                ),
                scenes = listOf(
                    LocalMapScene(
                        key = "scene/0010",
                        gridWidth = 21,
                        gridHeight = 20,
                        placements = listOf(
                            LocalMapScenePlacement("local/0010", 0x10, 0, 0),
                            LocalMapScenePlacement("local/0011", 0x11, 20, 0),
                        ),
                    ),
                ),
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
        val localMaps = ApiViewBuilder.catalog(catalog).localMaps
        val local = localMaps.single { it.baseAreaId == 0x10 }
        assertEquals(0x10, local.baseAreaId)
        assertEquals(320, local.pixelWidth)
        assertEquals(20, local.gridWidth)
        assertEquals("/api/maps/local%2F0010%2Fmap.png", local.imageUrl)
        assertEquals(false, local.dynamicLighting)
        assertEquals(true, localMaps.single { it.baseAreaId == 0x11 }.dynamicLighting)
        val scene = ApiViewBuilder.catalog(catalog).mapScenes.single()
        assertEquals("scene/0010", scene.key)
        assertEquals(336, scene.pixelWidth)
        assertEquals(320, scene.pixelHeight)
        assertEquals(listOf(0x10, 0x11), scene.placements.map { it.baseAreaId })
        assertEquals("/api/maps/local%2F0011%2Fmap.png", scene.placements.last().imageUrl)
        assertEquals(true, scene.placements.last().dynamicLighting)
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
            liveMapPosition = LiveMapPosition(12, 7),
            ledger = KnowledgeLedger(
                visitedAreaBaseIds = setOf(0x0010, 0x0011),
                seenSpeciesByArea = mapOf(0x0011 to setOf(2), 0x0012 to setOf(1)),
            ),
        )

        val state = ApiViewBuilder.state(snapshot, catalog)

        assertEquals(0x0010, state.currentAreaBaseId)
        assertEquals("Route 101", state.currentAreaName)
        assertEquals(MapPositionView(12, 7), state.currentMapPosition)
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
            liveAreaBaseId = 0x0203,
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
    fun doesNotUseTheLedgerAreaAsAnOfflineFeatureFallback() {
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

        assertNull(state.currentAreaBaseId)
        assertEquals(emptyList<Int>(), state.currentAreaIds)
        assertEquals("AREA_UNAVAILABLE", rarity.areaOutcome)
        assertNull(rarity.currentAreaBaseId)
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
        assertFalse(state.gameAccessReady)
    }

    @Test
    fun validatedLiveGameStateMarksTheCurrentGameAsInitialized() {
        val state = ApiViewBuilder.state(
            AppSnapshot(liveAreaBaseId = 0x0101, gameAccessReady = true),
            ParsedCatalog("a".repeat(64), EngineFamily.EMERALD, Platform.GBA),
            retroArch = RetroArchView(connection = "PLAYING", resolution = "ACTIVE"),
        )

        assertTrue(state.gameAccessReady)
    }

    @Test
    fun areaSpeciesUseOrganicAreaObservationsWithAUnifiedCaughtOverride() {
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
            resolvedPokedex = ResolvedPokedexProjection(
                seenSpeciesIds = emptySet(),
                caughtSpeciesIds = setOf(4),
            ),
            ledger = KnowledgeLedger(
                seenSpecies = setOf(1, 2, 3),
                seenSpeciesByArea = mapOf(
                    0x0010 to setOf(1, 2),
                    0x0011 to setOf(3),
                ),
            ),
        )

        val state = ApiViewBuilder.state(snapshot, catalog)

        assertEquals(listOf(1, 2, 4), state.currentAreaSpeciesIds)
    }

    private fun identityOnlyTrainerCard(name: String, gender: Int) = TrainerCardState(
        identity = TrainerIdentity(name, gender),
        publicTrainerId = null,
        money = null,
        playTimeHours = null,
        playTimeMinutes = null,
        badgeFlags = null,
        dexSeen = null,
        dexCaught = null,
        stars = null,
    )
}
