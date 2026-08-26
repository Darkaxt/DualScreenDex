package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogCacheDecision
import com.darkaxt.dualdex.catalog.CatalogCacheLookup
import com.darkaxt.dualdex.catalog.CatalogRepository
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.darkaxt.dualdex.catalog.StoredCatalog
import com.darkaxt.dualdex.settings.SettingsRepository
import com.enrpau.dualscreendex.companion.api.RetroArchView
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.AppScreen
import com.enrpau.dualscreendex.companion.model.Density
import com.enrpau.dualscreendex.companion.model.DisplayMode
import com.enrpau.dualscreendex.companion.model.DisplayTarget
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.companion.model.Theme
import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.LevelUpRulesetDetectionFingerprint
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.save.SaveDocumentSource
import com.darkaxt.dualdex.save.SaveObservation
import com.darkaxt.dualdex.save.SaveObservationKind
import com.darkaxt.dualdex.knowledge.SaveFileFingerprint
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.darkaxt.dualdex.save.SavedArea
import com.darkaxt.dualdex.save.TrainerSnapshot
import com.darkaxt.dualdex.save.TrainerIdentity
import com.darkaxt.dualdex.performance.PerformanceEvent
import com.darkaxt.dualdex.performance.PerformanceEventKind
import com.darkaxt.dualdex.performance.PerformanceEventSink
import com.darkaxt.dualdex.performance.PerformanceMetricSampler
import com.darkaxt.dualdex.performance.PerformanceMetrics
import com.darkaxt.dualdex.performance.PerformanceRecorder
import com.darkaxt.dualdex.battle.LiveClockState
import com.darkaxt.dualdex.battle.LiveGameSnapshot
import com.darkaxt.dualdex.battle.LiveValue
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.LearnsetRuleset
import com.enrpau.dualscreendex.parser.catalog.LevelUpRulesetSelector
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializationPhase
import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializationProgress
import com.enrpau.dualscreendex.parser.catalog.CatalogWorkModule
import com.enrpau.dualscreendex.parser.catalog.CatalogWorkProgress
import com.enrpau.dualscreendex.parser.catalog.CatalogGameClockSchedule
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BattleUiAbi
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3RuntimeMemoryLayout
import com.enrpau.dualscreendex.parser.catalog.CatalogRuntimeMetadata
import com.enrpau.dualscreendex.parser.catalog.EncounterArea
import com.enrpau.dualscreendex.parser.catalog.EncounterSlot
import com.enrpau.dualscreendex.parser.catalog.MoveRecord
import com.enrpau.dualscreendex.parser.catalog.MapLighting
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoi
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiItem
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiOrganicVisibility
import com.enrpau.dualscreendex.parser.catalog.PngMapAsset
import com.enrpau.dualscreendex.parser.catalog.TypeRecord
import com.enrpau.dualscreendex.parser.catalog.TypeMatchup
import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog
import com.enrpau.dualscreendex.parser.catalog.WorldMapCell
import com.enrpau.dualscreendex.parser.catalog.WorldMapLocation
import com.enrpau.dualscreendex.parser.catalog.WorldMapRegion
import com.enrpau.dualscreendex.parser.io.LoadedRom
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Collections
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import com.darkaxt.dualdex.battle.BattleMemorySample
import com.darkaxt.dualdex.battle.BattleMatchupObservation
import com.darkaxt.dualdex.battle.BattleMonSnapshot
import com.darkaxt.dualdex.battle.BattleTarget
import com.darkaxt.dualdex.battle.BattleTrackingUpdate
import com.darkaxt.dualdex.battle.BattleEncounterKind
import com.darkaxt.dualdex.battle.ResolvedBattleLayout
import com.darkaxt.dualdex.battle.TargetMode
import com.darkaxt.dualdex.battle.RuntimeMapPosition

class ProductionCompanionRuntimeTest {
    @Test
    fun profilerDistinguishesColdParsingFromCacheReopen() {
        val bytes = ByteArray(0xC0)
        "POKEMON EMER".toByteArray().copyInto(bytes, 0xA0)
        val rom = RomImage(bytes)
        val coldEvents = mutableListOf<PerformanceEvent>()
        val coldRecorder = recordingPerformance(coldEvents)
        val coldRuntime = ProductionCompanionRuntime(
            parserWorker = ImmediateExecutorService(),
            catalogRepository = RecordingCatalogRepository(),
            parseCatalog = { source, _, work ->
                work(CatalogWorkProgress(CatalogWorkModule.ROM_IDENTITY))
                work(CatalogWorkProgress(CatalogWorkModule.MAPS))
                ParsedCatalog(source.sha256, EngineFamily.EMERALD, Platform.GBA)
            },
            performanceRecorder = coldRecorder,
        )

        coldRuntime.load(LoadedRom("Modern Emerald.gba", rom))

        assertEquals(PerformanceEventKind.LOAD_STARTED, coldEvents.first().kind)
        assertEquals("MISS_FILE_ABSENT", coldEvents.single { it.kind == PerformanceEventKind.CACHE_DECISION }.cacheDecision)
        assertEquals(
            listOf("ROM_IDENTITY", "MAPS"),
            coldEvents.filter { it.kind == PerformanceEventKind.STAGE_FINISHED }.map(PerformanceEvent::stage),
        )
        assertEquals(1, coldEvents.count { it.kind == PerformanceEventKind.CATALOG_READY })
        assertEquals(1, coldEvents.count { it.kind == PerformanceEventKind.WAITING_FOR_GAME_ACCESS })
        coldRuntime.close()

        val catalog = ParsedCatalog(rom.sha256, EngineFamily.EMERALD, Platform.GBA)
        val cachedEvents = mutableListOf<PerformanceEvent>()
        val cachedRuntime = ProductionCompanionRuntime(
            parserWorker = ImmediateExecutorService(),
            catalogRepository = FakeCatalogRepository(
                StoredCatalog(
                    catalog,
                    CatalogSourceMetadata.direct("Modern Emerald.gba", rom.size, "POKEMON EMER"),
                    CatalogWriteProgress.complete(),
                    committedSections = emptySet(),
                    writtenAtEpochMs = 1,
                ),
            ),
            performanceRecorder = recordingPerformance(cachedEvents),
        )

        cachedRuntime.load(LoadedRom("Modern Emerald.gba", rom))

        assertEquals("HIT", cachedEvents.single { it.kind == PerformanceEventKind.CACHE_DECISION }.cacheDecision)
        assertTrue(cachedEvents.none { it.kind == PerformanceEventKind.STAGE_FINISHED })
        assertEquals(1, cachedEvents.count { it.kind == PerformanceEventKind.CATALOG_READY })
        cachedRuntime.close()
    }

    @Test
    fun profilerRecordsGameAccessOnlyAfterTheOneWayReadinessGateOpens() {
        val events = mutableListOf<PerformanceEvent>()
        val hash = "d".repeat(64)
        val runtime = ProductionCompanionRuntime(performanceRecorder = recordingPerformance(events))
        runtime.loadCatalog("Modern Emerald.gba", ParsedCatalog(hash, EngineFamily.EMERALD, Platform.GBA))

        assertEquals(1, events.count { it.kind == PerformanceEventKind.WAITING_FOR_GAME_ACCESS })
        assertTrue(events.none { it.kind == PerformanceEventKind.GAME_ACCESS_READY })

        runtime.updateLiveGameState(
            liveSnapshot(
                hash,
                unavailableValue("saved Pokédex counts were unavailable"),
                LiveValue.Available(emptyList()),
                clock = LiveValue.Available(LiveClockState(0, 0, 1)),
                trainerIdentity = LiveValue.Available(TrainerIdentity("MAY", 1)),
                location = LiveValue.Available(0x0009),
            ),
        )

        assertEquals(1, events.count { it.kind == PerformanceEventKind.GAME_ACCESS_READY })
        runtime.close()
    }

    @Test
    fun plausibleGen3MapAndZeroClockDoNotUnlockBeforeLiveTrainerIdentityExists() {
        val hash = "a".repeat(64)
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog("Modern Emerald.gba", ParsedCatalog(hash, EngineFamily.EMERALD, Platform.GBA))
        runtime.updateLiveGameState(
            liveSnapshot(
                hash,
                unavailableValue("live Trainer Card fields were unavailable"),
                LiveValue.Available(emptyList()),
                clock = LiveValue.Available(LiveClockState(0, 0, 0)),
                location = LiveValue.Available(0x0009),
            ),
        )
        runtime.updateLiveArea(0x0009)
        runtime.updateLiveMapPosition(RuntimeMapPosition(8, 5))

        assertFalse(runtime.stateView().gameAccessReady)
        runtime.close()
    }

    @Test
    fun zeroClockDoesNotUnlockGen3EvenWhenTitleMemoryDecodesAnIdentity() {
        val hash = "c".repeat(64)
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog("Modern Emerald.gba", ParsedCatalog(hash, EngineFamily.EMERALD, Platform.GBA))
        runtime.updateLiveGameState(
            liveSnapshot(
                hash,
                unavailableValue("live Trainer Card fields were unavailable"),
                LiveValue.Available(emptyList()),
                clock = LiveValue.Available(LiveClockState(0, 0, 0)),
                trainerIdentity = LiveValue.Available(TrainerIdentity("EMERALD", 0)),
                location = LiveValue.Available(0x0009),
            ),
        )

        assertFalse(runtime.stateView().gameAccessReady)
        runtime.close()
    }

    @Test
    fun firstAdvancingClockSecondUnlocksGen3AfterIdentityAndLocationResolve() {
        val hash = "d".repeat(64)
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog("Modern Emerald.gba", ParsedCatalog(hash, EngineFamily.EMERALD, Platform.GBA))
        runtime.updateLiveGameState(
            liveSnapshot(
                hash,
                unavailableValue("live Trainer Card fields were unavailable"),
                LiveValue.Available(emptyList()),
                clock = LiveValue.Available(LiveClockState(0, 0, 1)),
                trainerIdentity = LiveValue.Available(TrainerIdentity("MAY", 1)),
                location = LiveValue.Available(0x0009),
            ),
        )

        assertTrue(runtime.stateView().gameAccessReady)
        runtime.close()
    }

    @Test
    fun laterUninitializedLookingSampleDoesNotHideAnInitializedGen3Session() {
        val hash = "b".repeat(64)
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog("Modern Emerald.gba", ParsedCatalog(hash, EngineFamily.EMERALD, Platform.GBA))
        runtime.updateLiveGameState(
            liveSnapshot(
                hash,
                unavailableValue("saved Pokédex counts were unavailable"),
                LiveValue.Available(emptyList()),
                clock = LiveValue.Available(LiveClockState(0, 1, 0)),
                trainerIdentity = LiveValue.Available(TrainerIdentity("MAY", 1)),
                location = LiveValue.Available(0x0009),
            ),
        )
        assertTrue(runtime.stateView().gameAccessReady)

        runtime.updateLiveGameState(
            liveSnapshot(
                hash,
                unavailableValue("live Trainer Card fields were unavailable"),
                LiveValue.Available(emptyList()),
                clock = LiveValue.Available(LiveClockState(0, 0, 0)),
                location = LiveValue.Available(0x0009),
            ),
        )

        assertTrue(runtime.stateView().gameAccessReady)
        runtime.close()
    }

    @Test
    fun rendersMapAssetsOutsideTheRuntimeStateLock() {
        var heldRuntimeLock = true
        lateinit var runtime: ProductionCompanionRuntime
        runtime = ProductionCompanionRuntime(
            mapAssetRenderer = { _, _, _, _ ->
                heldRuntimeLock = Thread.holdsLock(runtime)
                com.enrpau.dualscreendex.parser.catalog.RenderedMapAsset(byteArrayOf(1), null)
            },
        )
        runtime.loadCatalog("map.gba", ParsedCatalog("sha", EngineFamily.EMERALD, Platform.GBA))

        assertEquals(1, runtime.mapAsset("map", MapLighting.DAY)?.bytes?.size)
        assertFalse(heldRuntimeLock)
        runtime.close()
    }

    @Test
    fun projectsOnlyCatalogValidatedClockPhasesIntoCompanionState() {
        val hash = "e".repeat(64)
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog(
            "clock.gba",
            ParsedCatalog(
                hash,
                EngineFamily.EMERALD,
                Platform.GBA,
                runtimeMetadata = CatalogRuntimeMetadata(
                    gen3RuntimeMemoryLayout = CatalogGen3RuntimeMemoryLayout(
                        mainAddress = 0x03001574,
                        inBattleAddress = 0x030019AD,
                        inBattleMask = 2,
                        saveBlock1MapGroupOffset = 4,
                        saveBlock1MapNumberOffset = 5,
                        liveClockAddress = 0x030039E8,
                        liveClockSchedule = CatalogGameClockSchedule(6, 21),
                    ),
                ),
            ),
        )

        runtime.updateLiveGameState(
            liveSnapshot(
                hash,
                unavailableValue("trainer omitted"),
                LiveValue.Available(emptyList()),
                LiveValue.Available(LiveClockState(21, 0, 0)),
            ),
        )

        assertEquals("NIGHT", runtime.stateView().gameTime?.phase)
        assertEquals(0.0, requireNotNull(runtime.stateView().gameTime?.phaseProgress), 0.0)
        runtime.close()
    }

    @Test
    fun provenWildBattleOpensUsableRarityOnceWithoutLaterTabOverrides() {
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog("fixture.gba", ParsedCatalog(
            "sha", EngineFamily.EMERALD, Platform.GBA,
            speciesById = mapOf(13 to SpeciesRecord(
                id = 13, dexNumber = CatalogField.available(13), name = CatalogField.available("WEEDLE"),
                typeIds = CatalogField.available(listOf(6)), baseStats = CatalogField.notFound("fixture"),
                sprite = CatalogField.notFound("fixture"), abilityIds = CatalogField.available(emptyList()),
            )),
            encounterAreas = listOf(com.enrpau.dualscreendex.parser.catalog.EncounterArea(
                id = 0x0010 * 10 + 1,
                name = CatalogField.available("Route"),
                methodId = 1,
                slots = listOf(com.enrpau.dualscreendex.parser.catalog.EncounterSlot(13, 3, 3, 100)),
            )),
        ))
        runtime.updateLiveArea(0x0010)
        val opponent = BattleMonSnapshot(
            battlerIndex = 1, position = 1, speciesId = 13, level = 3, hp = 15, maxHp = 15,
            ivs = List(6) { 24 }, moves = listOf(40, 0, 0, 0), pp = listOf(35, 0, 0, 0),
            typeIds = listOf(6, 6), abilityId = 0, personality = 200,
        )
        val sample = BattleMemorySample(
            layout = ResolvedBattleLayout(0x143C, 0x1420, 0x142C, 0x16EE, 0x1874, 0x1878, 2),
            battlers = listOf(opponent), opponents = listOf(opponent), selectedMoveId = null,
            target = BattleTarget(0, TargetMode.AUTOMATIC), capabilities = emptyMap(),
            encounterKind = BattleEncounterKind.WILD,
        )

        runtime.applyBattleThroughState(BattleTrackingUpdate(true, sample))
        assertEquals("RARITY", runtime.gateway.bootstrap().battleTab.name)

        runtime.action("BATTLE_TAB", mapOf("tab" to "MOVES"))
        runtime.applyBattleThroughState(BattleTrackingUpdate(true, sample.copy(encounterKind = BattleEncounterKind.TRAINER)))
        assertEquals("MOVES", runtime.gateway.bootstrap().battleTab.name)

        runtime.applyBattleThroughState(BattleTrackingUpdate(false, null, ended = true))
        runtime.applyBattleThroughState(BattleTrackingUpdate(true, sample.copy(encounterKind = BattleEncounterKind.TRAINER)))
        assertEquals("ENTRY", runtime.gateway.bootstrap().battleTab.name)
        runtime.close()
    }

    @Test
    fun unifiedBattleIsSoleUiAuthorityAndKeepsRawOpponentMovesPrivate() {
        val hash = "8".repeat(64)
        val source = com.darkaxt.dualdex.live.UnifiedGameStateDecoder()
        val runtime = ProductionCompanionRuntime(transientGameState = source)
        runtime.loadCatalog(
            "battle.gba",
            ParsedCatalog(
                hash,
                EngineFamily.EMERALD,
                Platform.GBA,
                speciesById = mapOf(13 to saveSpecies(13)),
            ),
        )
        source.beginSession(
            com.darkaxt.dualdex.live.TransientGameStateContext(
                romIdentity = hash,
                generation = 3,
                catalog = com.darkaxt.dualdex.battle.BattleCatalogView(emptyMap(), emptyMap(), emptySet()),
            ),
        )
        val opponent = BattleMonSnapshot(
            battlerIndex = 1,
            position = 1,
            speciesId = 13,
            level = 7,
            hp = 20,
            maxHp = 20,
            ivs = List(6) { 24 },
            moves = listOf(40, 81, 0, 0),
            pp = listOf(35, 40, 0, 0),
            typeIds = listOf(6, 3),
            abilityId = 19,
            personality = 200,
        )
        val wild = BattleMemorySample(
            layout = ResolvedBattleLayout(0, 0, 0, 0, 0, 0, 2),
            battlers = listOf(opponent),
            opponents = listOf(opponent),
            selectedMoveId = null,
            target = BattleTarget(0, TargetMode.AUTOMATIC),
            capabilities = emptyMap(),
            encounterKind = BattleEncounterKind.WILD,
        )

        source.acceptDecodedLive(unifiedBattleSnapshot(hash, 1, true, wild))

        assertEquals(AppScreen.BATTLE, runtime.gateway.bootstrap().screen)
        assertEquals("RARITY", runtime.gateway.bootstrap().battleTab.name)
        assertTrue(requireNotNull(runtime.gateway.bootstrap().battle).rarityUsable)
        assertTrue(runtime.stateView().battle!!.opponents.single().moves.isEmpty())

        source.acceptBattleTracking(
            BattleTrackingUpdate(true, wild.copy(encounterKind = BattleEncounterKind.TRAINER)),
        )
        assertEquals(
            com.enrpau.dualscreendex.companion.model.BattleEncounterKind.WILD,
            runtime.gateway.bootstrap().battle?.encounterKind,
        )

        runtime.action("BATTLE_TAB", mapOf("tab" to "ENTRY"))
        source.acceptDecodedLive(
            unifiedBattleSnapshot(hash, 2, true, wild.copy(opponents = listOf(opponent.copy(hp = 19)))),
        )
        assertEquals("ENTRY", runtime.gateway.bootstrap().battleTab.name)

        source.acceptDecodedLive(unifiedBattleSnapshot(hash, 3, false, null))
        assertNull(runtime.gateway.bootstrap().battle)
        runtime.close()
    }

    @Test
    fun unifiedOverworldPublishesCoherentAreaPositionClockAndOneWayReadiness() {
        val hash = "9".repeat(64)
        val firstMap = LocalMap("local/0010", "Route", 0x0010, 160, 160, 10, 10, "local/0010/map")
        val secondMap = LocalMap("local/0011", "Town", 0x0011, 160, 160, 10, 10, "local/0011/map")
        val hiddenPoi = LocalMapPoi(
            key = "local/0010/bg/0",
            localMapKey = firstMap.key,
            baseAreaId = firstMap.baseAreaId,
            tileX = 8,
            tileY = 6,
            kind = LocalMapPoiKind.HIDDEN_ITEM,
            organicVisibility = LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE,
            item = LocalMapPoiItem(13),
        )
        val unresolvedOriginPoi = LocalMapPoi(
            key = "local/0011/bg/0",
            localMapKey = secondMap.key,
            baseAreaId = secondMap.baseAreaId,
            tileX = 0,
            tileY = 0,
            kind = LocalMapPoiKind.HIDDEN_ITEM,
            organicVisibility = LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE,
            item = LocalMapPoiItem(14),
        )
        val source = com.darkaxt.dualdex.live.UnifiedGameStateDecoder()
        val runtime = ProductionCompanionRuntime(transientGameState = source)
        runtime.loadCatalog(
            "overworld.gba",
            ParsedCatalog(
                hash,
                EngineFamily.EMERALD,
                Platform.GBA,
                localMaps = LocalMapCatalog(
                    maps = listOf(firstMap, secondMap),
                    assets = mapOf(
                        firstMap.imageAssetKey to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)),
                        secondMap.imageAssetKey to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)),
                    ),
                    pois = listOf(hiddenPoi, unresolvedOriginPoi),
                ),
                runtimeMetadata = CatalogRuntimeMetadata(
                    gen3RuntimeMemoryLayout = CatalogGen3RuntimeMemoryLayout(
                        mainAddress = 0x03001574,
                        inBattleAddress = 0x030019AD,
                        inBattleMask = 2,
                        saveBlock1MapGroupOffset = 4,
                        saveBlock1MapNumberOffset = 5,
                        liveClockAddress = 0x030039E8,
                        liveClockSchedule = CatalogGameClockSchedule(6, 21),
                    ),
                ),
            ),
        )
        source.beginSession(
            com.darkaxt.dualdex.live.TransientGameStateContext(
                romIdentity = hash,
                generation = 3,
                catalog = com.darkaxt.dualdex.battle.BattleCatalogView(emptyMap(), emptyMap(), emptySet()),
            ),
        )
        val unavailable = com.darkaxt.dualdex.battle.LiveValue.Unavailable(
            com.darkaxt.dualdex.battle.LiveUnavailableReason(
                com.darkaxt.dualdex.battle.LiveUnavailableCode.MISSING_REGION,
                "fixture region omitted",
            ),
        )
        val trainer = com.darkaxt.dualdex.battle.LiveTrainerState(
            identity = com.darkaxt.dualdex.battle.LiveValue.Available(TrainerIdentity("MAY", 1)),
            publicTrainerId = unavailable,
            money = unavailable,
            playTime = unavailable,
            badgeFlags = unavailable,
            stars = unavailable,
        )
        val publications = mutableListOf<com.enrpau.dualscreendex.companion.model.AppSnapshot>()
        runtime.gateway.subscribe(publications::add)

        fun sample(id: Long, area: Int, x: Int, y: Int, seconds: Int) = unifiedBattleSnapshot(
            hash,
            id,
            false,
            null,
        ).copy(
            trainer = trainer,
            location = com.darkaxt.dualdex.battle.LiveLocationState(
                com.darkaxt.dualdex.battle.LiveValue.Available(area),
                com.darkaxt.dualdex.battle.LiveValue.Available(RuntimeMapPosition(x, y)),
            ),
            clock = com.darkaxt.dualdex.battle.LiveValue.Available(
                com.darkaxt.dualdex.battle.LiveClockState(6, 0, seconds),
            ),
        )

        source.acceptDecodedLive(sample(1, 0x0010, 8, 5, 1))
        source.acceptDecodedLive(sample(2, 0x0011, 9, 6, 2))

        val state = runtime.gateway.bootstrap()
        assertEquals(0x0011, state.liveAreaBaseId)
        assertEquals(com.enrpau.dualscreendex.companion.model.LiveMapPosition(9, 6), state.liveMapPosition)
        assertEquals(6, state.gameTime?.hours)
        assertEquals("DAY", state.gameTime?.phase?.name)
        assertTrue(state.gameAccessReady)
        assertEquals(setOf(0x0010, 0x0011), state.ledger.visitedAreaBaseIds)
        assertEquals(setOf(hiddenPoi.key), state.ledger.proximityRevealedPoiKeys)
        assertTrue(publications.none { publication ->
            publication.liveAreaBaseId == 0x0011 && (
                publication.liveMapPosition != com.enrpau.dualscreendex.companion.model.LiveMapPosition(9, 6) ||
                    0x0011 !in publication.ledger.visitedAreaBaseIds
                )
        })

        source.acceptDecodedLive(sample(3, 0x0011, 9, 6, 0).copy(
            clock = com.darkaxt.dualdex.battle.LiveValue.Available(
                com.darkaxt.dualdex.battle.LiveClockState(0, 0, 0),
            ),
        ))
        assertTrue(runtime.gateway.bootstrap().gameAccessReady)

        publications.clear()
        source.acceptDecodedLive(
            sample(4, 0x0011, 0, 0, 3).copy(
                location = com.darkaxt.dualdex.battle.LiveLocationState(
                    com.darkaxt.dualdex.battle.LiveValue.Available(0x0011),
                    unavailable,
                ),
            ),
        )
        val unavailablePositionState = runtime.gateway.bootstrap()
        assertEquals(0x0011, unavailablePositionState.liveAreaBaseId)
        assertNull(unavailablePositionState.liveMapPosition)
        assertEquals(0x0011, publications.last().liveAreaBaseId)
        assertNull(publications.last().liveMapPosition)
        assertTrue(0x0011 in publications.last().ledger.visitedAreaBaseIds)
        assertFalse(unresolvedOriginPoi.key in unavailablePositionState.ledger.proximityRevealedPoiKeys)
        runtime.close()
    }

    @Test
    fun secondsOnlySampleRoutesOnlyOverworldAndDoesNotRepublishAnUnchangedBattle() {
        val hash = "4".repeat(64)
        val source = com.darkaxt.dualdex.live.UnifiedGameStateDecoder()
        val runtime = ProductionCompanionRuntime(transientGameState = source)
        runtime.loadCatalog("seconds.gba", ParsedCatalog(hash, EngineFamily.EMERALD, Platform.GBA))
        source.beginSession(
            com.darkaxt.dualdex.live.TransientGameStateContext(
                romIdentity = hash,
                generation = 3,
                catalog = com.darkaxt.dualdex.battle.BattleCatalogView(emptyMap(), emptyMap(), emptySet()),
            ),
        )
        val opponent = BattleMonSnapshot(
            battlerIndex = 1,
            position = 1,
            speciesId = 13,
            level = 3,
            hp = 15,
            maxHp = 15,
            ivs = List(6) { 24 },
            moves = listOf(40, 0, 0, 0),
            pp = listOf(35, 0, 0, 0),
            typeIds = listOf(6, 6),
            abilityId = 0,
            personality = 200,
        )
        val battle = BattleMemorySample(
            layout = ResolvedBattleLayout(0, 0, 0, 0, 0, 0, 2),
            battlers = listOf(opponent),
            opponents = listOf(opponent),
            selectedMoveId = null,
            target = BattleTarget(0, TargetMode.AUTOMATIC),
            capabilities = emptyMap(),
            encounterKind = BattleEncounterKind.WILD,
        )
        val unavailable = com.darkaxt.dualdex.battle.LiveValue.Unavailable(
            com.darkaxt.dualdex.battle.LiveUnavailableReason(
                com.darkaxt.dualdex.battle.LiveUnavailableCode.MISSING_REGION,
                "fixture region omitted",
            ),
        )
        val trainer = com.darkaxt.dualdex.battle.LiveTrainerState(
            com.darkaxt.dualdex.battle.LiveValue.Available(TrainerIdentity("MAY", 1)),
            unavailable,
            unavailable,
            unavailable,
            unavailable,
            unavailable,
        )
        fun sample(id: Long, seconds: Int, battleSample: BattleMemorySample = battle) =
            unifiedBattleSnapshot(hash, id, true, battleSample).copy(
            trainer = trainer,
            location = com.darkaxt.dualdex.battle.LiveLocationState(
                com.darkaxt.dualdex.battle.LiveValue.Available(0x0010),
                com.darkaxt.dualdex.battle.LiveValue.Available(RuntimeMapPosition(8, 5)),
            ),
            clock = com.darkaxt.dualdex.battle.LiveValue.Available(
                com.darkaxt.dualdex.battle.LiveClockState(6, 0, seconds),
            ),
        )

        source.acceptDecodedLive(sample(1, 1))
        val version = runtime.gateway.bootstrap().version
        val before = runtime.resolvedStateDispatchMetrics()

        source.acceptDecodedLive(sample(2, 2))

        val after = runtime.resolvedStateDispatchMetrics()
        assertEquals(version, runtime.gateway.bootstrap().version)
        assertEquals(before.publications + 1, after.publications)
        assertEquals(before.overworldSections + 1, after.overworldSections)
        assertEquals(before.playerSections, after.playerSections)
        assertEquals(before.partySections, after.partySections)
        assertEquals(before.battleSections, after.battleSections)

        val beforeRawBattleChange = runtime.resolvedStateDispatchMetrics()
        val dispatchBeforeRawBattleChange = runtime.gateway.metrics()
        val versionBeforeRawBattleChange = runtime.gateway.bootstrap().version
        val hpOnlyChange = battle.copy(
            battlers = listOf(opponent.copy(hp = 14)),
            opponents = listOf(opponent.copy(hp = 14)),
        )
        source.acceptDecodedLive(sample(3, 2, hpOnlyChange))

        assertEquals(versionBeforeRawBattleChange, runtime.gateway.bootstrap().version)
        assertEquals(
            dispatchBeforeRawBattleChange.dispatchAttempts + 1,
            runtime.gateway.metrics().dispatchAttempts,
        )
        assertEquals(
            dispatchBeforeRawBattleChange.noOpDispatches + 1,
            runtime.gateway.metrics().noOpDispatches,
        )
        assertEquals(
            beforeRawBattleChange.battleSections + 1,
            runtime.resolvedStateDispatchMetrics().battleSections,
        )
        runtime.close()
    }

    @Test
    fun organicEffectivenessUnlocksAfterThePlayerConsumesMovePpAgainstTheTarget() {
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog("fixture.gba", ParsedCatalog(
            "sha", EngineFamily.EMERALD, Platform.GBA,
            speciesById = mapOf(13 to SpeciesRecord(
                id = 13, dexNumber = CatalogField.available(13), name = CatalogField.available("WEEDLE"),
                typeIds = CatalogField.available(listOf(6)), baseStats = CatalogField.notFound("fixture"),
                sprite = CatalogField.notFound("fixture"), abilityIds = CatalogField.available(emptyList()),
            )),
            movesById = mapOf(10 to MoveRecord(
                10, CatalogField.available("THUNDER WAVE"), CatalogField.available(13), CatalogField.notFound("fixture"),
                CatalogField.available(0), CatalogField.available(100), CatalogField.available(20),
            )),
            typesById = mapOf(
                6 to TypeRecord(6, CatalogField.available("BUG")),
                13 to TypeRecord(13, CatalogField.available("ELECTRIC")),
            ),
            typeChart = listOf(TypeMatchup(13, 6, 0)),
        ))
        val opponent = BattleMonSnapshot(
            battlerIndex = 1, position = 1, speciesId = 13, level = 3, hp = 15, maxHp = 15,
            ivs = List(6) { 15 }, moves = listOf(40, 81, 0, 0), pp = listOf(35, 40, 0, 0),
            typeIds = listOf(6, 6), abilityId = 19, personality = 200,
        )
        val sample = BattleMemorySample(
            layout = ResolvedBattleLayout(0x143C, 0x1420, 0x142C, 0x16EE, 0x1874, 0x1878, 2),
            battlers = listOf(opponent), opponents = listOf(opponent), selectedMoveId = 10,
            target = BattleTarget(0, TargetMode.AUTOMATIC), capabilities = emptyMap(),
        )

        runtime.applyBattleThroughState(BattleTrackingUpdate(true, sample))
        assertFalse(runtime.stateView().battle!!.effectivenessKnown)

        runtime.applyBattleThroughState(BattleTrackingUpdate(
            true,
            sample,
            discoveredMatchups = setOf(BattleMatchupObservation(13, 10, listOf(6, 6))),
        ))

        assertTrue(runtime.stateView().battle!!.effectivenessKnown)
        assertEquals("NO_EFFECT", runtime.stateView().battle!!.effectiveness)
        runtime.close()
    }

    @Test
    fun publishesLiveBattleObservationsAndAcceptsProductionBattleActions() {
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog("fixture.gba", ParsedCatalog(
            "sha", EngineFamily.EMERALD, Platform.GBA,
            speciesById = mapOf(1 to SpeciesRecord(
                id = 1, dexNumber = CatalogField.available(1), name = CatalogField.available("A"),
                typeIds = CatalogField.available(listOf(0)), baseStats = CatalogField.notFound("fixture"),
                sprite = CatalogField.notFound("fixture"), abilityIds = CatalogField.available(emptyList()),
            )),
            movesById = mapOf(1 to MoveRecord(
                1, CatalogField.available("MOVE"), CatalogField.available(0), CatalogField.notFound("fixture"),
                CatalogField.available(40), CatalogField.available(100), CatalogField.available(35),
            )),
            typesById = mapOf(0 to TypeRecord(0, CatalogField.available("NORMAL"))),
        ))
        val opponent = BattleMonSnapshot(
            battlerIndex = 1, position = 1, speciesId = 13, level = 3, hp = 15, maxHp = 15,
            ivs = listOf(10, 11, 12, 13, 14, 15), moves = listOf(40, 81, 0, 0), pp = listOf(34, 40, 0, 0),
            typeIds = listOf(6, 3), abilityId = 19, personality = 200,
        )
        val sample = BattleMemorySample(
            layout = ResolvedBattleLayout(0x143C, 0x1420, 0x142C, 0x16EE, 0x1874, 0x1878, 2),
            battlers = listOf(opponent), opponents = listOf(opponent), selectedMoveId = 10,
            target = BattleTarget(0, TargetMode.MANUAL_TARGET_FALLBACK), capabilities = emptyMap(),
        )

        runtime.applyBattleThroughState(
            BattleTrackingUpdate(true, sample, observations = mapOf(13 to mapOf(40 to 2))),
        )

        var snapshot = runtime.gateway.bootstrap()
        assertEquals(13, snapshot.battle?.opponents?.single()?.speciesId)
        assertEquals(2, snapshot.ledger.observedMoves.getValue(13).single().frequency)
        assertEquals("MANUAL_TARGET_FALLBACK", snapshot.battle?.targetMode?.name)
        runtime.action("BATTLE_TAB", mapOf("tab" to "MOVES"))
        runtime.action("SELECT_TARGET", mapOf("index" to "0"))
        snapshot = runtime.gateway.bootstrap()
        assertEquals("MOVES", snapshot.battleTab.name)

        runtime.action("OPEN_SPECIES", mapOf("speciesId" to "13"))
        runtime.applyBattleThroughState(BattleTrackingUpdate(true, sample))
        assertEquals(AppScreen.DETAIL, runtime.gateway.bootstrap().screen)

        runtime.applyBattleThroughState(BattleTrackingUpdate(false, null, ended = true))
        assertNull(runtime.gateway.bootstrap().battle)
        assertEquals(AppScreen.DETAIL, runtime.gateway.bootstrap().screen)
        assertEquals(AppScreen.POKEDEX.name, runtime.action("BACK", emptyMap()).screen)
        runtime.close()
    }

    @Test
    fun exposesGen1Gen2AndGen3CatalogsAsProductionBattleContexts() {
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog("fixture.gba", ParsedCatalog(
            "sha", EngineFamily.EMERALD, Platform.GBA,
            speciesById = mapOf(1 to SpeciesRecord(
                id = 1, dexNumber = CatalogField.available(1), name = CatalogField.available("A"),
                typeIds = CatalogField.available(listOf(0)), baseStats = CatalogField.notFound("fixture"),
                sprite = CatalogField.notFound("fixture"), abilityIds = CatalogField.available(emptyList()),
            )),
            movesById = mapOf(1 to MoveRecord(
                1, CatalogField.available("MOVE"), CatalogField.available(0), CatalogField.notFound("fixture"),
                CatalogField.available(40), CatalogField.available(100), CatalogField.available(35),
            )),
            typesById = mapOf(0 to TypeRecord(0, CatalogField.available("NORMAL"))),
            runtimeMetadata = CatalogRuntimeMetadata(
                gen3RuntimeMemoryLayout = CatalogGen3RuntimeMemoryLayout(
                    mainAddress = 0x03001574,
                    inBattleAddress = 0x030019AD,
                    inBattleMask = 2,
                    saveBlock1MapGroupOffset = 4,
                    saveBlock1MapNumberOffset = 5,
                    battleUiAbi = CatalogGen3BattleUiAbi(
                        activeBattlerAddress = 0x02024064,
                        actionCursorAddress = 0x020244AC,
                        moveCursorAddress = 0x020244B0,
                        targetCursorAddress = 0x0202420C,
                    ),
                ),
            ),
        ))
        assertEquals("sha", runtime.battleCatalogContext()?.romIdentity)
        assertEquals(3, runtime.battleCatalogContext()?.generation)
        assertEquals(0x02024064L, runtime.battleCatalogContext()?.gen3RuntimeMemoryLayout?.battleUi?.activeBattlerAddress)

        runtime.loadCatalog("fixture.gbc", ParsedCatalog(
            "yellow", EngineFamily.YELLOW, Platform.GBC,
            speciesById = mapOf(0x54 to SpeciesRecord(
                id = 0x54, dexNumber = CatalogField.available(25), name = CatalogField.available("PIKACHU"),
                typeIds = CatalogField.available(listOf(0x17)), baseStats = CatalogField.notFound("fixture"),
                sprite = CatalogField.notFound("fixture"), abilityIds = CatalogField.notApplicable("Gen 1"),
            )),
            movesById = mapOf(0x54 to MoveRecord(
                0x54, CatalogField.available("THUNDERSHOCK"), CatalogField.available(0x17), CatalogField.notFound("fixture"),
                CatalogField.available(40), CatalogField.available(100), CatalogField.available(30),
            )),
            typesById = mapOf(0x17 to TypeRecord(0x17, CatalogField.available("ELECTRIC"))),
        ))
        assertEquals("yellow", runtime.battleCatalogContext()?.romIdentity)
        assertEquals(1, runtime.battleCatalogContext()?.generation)

        runtime.loadCatalog("fixture.gbc", ParsedCatalog(
            "crystal", EngineFamily.CRYSTAL, Platform.GBC,
            speciesById = mapOf(19 to SpeciesRecord(
                id = 19, dexNumber = CatalogField.available(19), name = CatalogField.available("RATTATA"),
                typeIds = CatalogField.available(listOf(0)), baseStats = CatalogField.notFound("fixture"),
                sprite = CatalogField.notFound("fixture"), abilityIds = CatalogField.notApplicable("Gen 2"),
            )),
            movesById = mapOf(33 to MoveRecord(
                33, CatalogField.available("TACKLE"), CatalogField.available(0), CatalogField.notFound("fixture"),
                CatalogField.available(35), CatalogField.available(95), CatalogField.available(35),
            )),
            typesById = mapOf(0 to TypeRecord(0, CatalogField.available("NORMAL"))),
            runtimeMetadata = CatalogRuntimeMetadata(gen2TimeOfDayWramOffset = 0x1841),
        ))
        assertEquals("crystal", runtime.battleCatalogContext()?.romIdentity)
        assertEquals(2, runtime.battleCatalogContext()?.generation)
        assertEquals(0x1841, runtime.battleCatalogContext()?.gen2TimeOfDayWramOffset)

        runtime.updateGen2GameClock(MapLighting.NIGHT)
        assertEquals("NIGHT", runtime.stateView().gameTime?.phase)
        assertNull(runtime.stateView().gameTime?.hours)
        assertNull(runtime.stateView().gameTime?.minutes)
        runtime.updateGen2GameClock(null)
        assertNull(runtime.stateView().gameTime)
        runtime.close()
    }

    @Test
    fun reusesCatalogDerivedContextsUntilTheirInputsChange() {
        val runtime = ProductionCompanionRuntime()
        val firstCatalog = battleReadyCatalog("first")
        runtime.loadCatalog("first.gba", firstCatalog)

        val firstSaveContext = requireNotNull(runtime.saveParseContext())
        val firstBattleContext = requireNotNull(runtime.battleCatalogContext())
        assertSame(firstSaveContext, runtime.saveParseContext())
        assertSame(firstBattleContext, runtime.battleCatalogContext())

        val trainer = TrainerSnapshot(
            name = "RED",
            gender = 0,
            publicTrainerId = 7,
            money = 3_000,
            playTimeHours = 1,
            playTimeMinutes = 2,
            badgeFlags = 0,
            dexSeen = 1,
            dexCaught = 1,
        )
        assertTrue(
            runtime.applySaveSnapshot(
                emptySave("first", "save-first").copy(trainer = trainer),
                SaveRamView(status = "MATCHED"),
            ),
        )

        val trainerBattleContext = requireNotNull(runtime.battleCatalogContext())
        assertSame(firstSaveContext, runtime.saveParseContext())
        assertNotSame(firstBattleContext, trainerBattleContext)
        assertSame(trainerBattleContext, runtime.battleCatalogContext())

        runtime.loadCatalog("second.gba", battleReadyCatalog("second"))
        assertNotSame(firstSaveContext, runtime.saveParseContext())
        assertNotSame(trainerBattleContext, runtime.battleCatalogContext())
        runtime.close()
    }

    @Test
    fun samePlaythroughSaveChangeFreezesCurrentLiveKnowledge() {
        val identity = "e".repeat(64)
        val saveIdentity = "1".repeat(64)
        val catalog = ParsedCatalog(
            identity,
            EngineFamily.YELLOW,
            Platform.GB,
            speciesById = mapOf(0x66 to saveSpecies(0x66)),
            movesById = mapOf(
                0x21 to MoveRecord(
                    0x21,
                    CatalogField.available("MOVE"),
                    CatalogField.notFound("fixture"),
                    CatalogField.notFound("fixture"),
                    CatalogField.notFound("fixture"),
                    CatalogField.notFound("fixture"),
                    CatalogField.notFound("fixture"),
                ),
            ),
        )
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog("yellow.gb", catalog)
        val initial = runtime.applySaveObservation(
            saveObservation(SaveObservationKind.INITIAL, "save", 1),
            emptySave(identity, saveIdentity).copy(saveGeneration = 1),
            SaveRamView(status = "MATCHED"),
        )
        assertTrue(initial.accepted)
        runtime.applyBattleThroughState(
            BattleTrackingUpdate(
                active = false,
                sample = null,
                observations = mapOf(0x66 to mapOf(0x21 to 2)),
                ended = true,
            ),
        )
        assertEquals(2, runtime.gateway.bootstrap().ledger.observedMoves.getValue(0x66).single().frequency)
        val changed = runtime.applySaveObservation(
            saveObservation(SaveObservationKind.CHANGED, "save", 2),
            emptySave(identity, saveIdentity).copy(saveGeneration = 1, saveCounter = 2),
            SaveRamView(status = "MATCHED"),
        )

        assertEquals(2, changed.checkpointLedger?.observedMoves?.getValue(0x66)?.single()?.frequency)
        runtime.close()
    }

    @Test
    fun switchingSaveIdentityCannotInheritPriorLiveDiscoveries() {
        val identity = "e".repeat(64)
        val firstSave = "1".repeat(64)
        val secondSave = "2".repeat(64)
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog(
                identity,
                EngineFamily.EMERALD,
                Platform.GBA,
                speciesById = mapOf(25 to saveSpecies(25)),
            ),
        )
        runtime.applySaveObservation(
            saveObservation(SaveObservationKind.INITIAL, "first", 1),
            emptySave(identity, firstSave),
            SaveRamView(status = "MATCHED"),
        )
        runtime.applyBattleThroughState(
            BattleTrackingUpdate(
                active = false,
                sample = null,
                observations = mapOf(25 to mapOf(33 to 1)),
                ended = true,
            ),
        )

        runtime.applySaveObservation(
            saveObservation(SaveObservationKind.SWITCHED, "second", 2),
            emptySave(identity, secondSave),
            SaveRamView(status = "MATCHED"),
            checkpoint = null,
        )

        assertTrue(runtime.gateway.bootstrap().ledger.observedMoves.isEmpty())
        runtime.close()
    }

    @Test
    fun initialCheckpointIsSanitizedAgainstTheActiveCatalog() {
        val identity = "e".repeat(64)
        val saveIdentity = "3".repeat(64)
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog(
                identity,
                EngineFamily.EMERALD,
                Platform.GBA,
                speciesById = mapOf(25 to saveSpecies(25)),
            ),
        )

        runtime.applySaveObservation(
            saveObservation(SaveObservationKind.INITIAL, "save", 1),
            emptySave(identity, saveIdentity),
            SaveRamView(status = "MATCHED"),
            checkpoint = KnowledgeLedger(seenSpecies = setOf(25, 999), caughtSpecies = setOf(999)),
        )

        assertTrue(runtime.gateway.bootstrap().ledger.seenSpecies.isEmpty())
        assertTrue(runtime.gateway.bootstrap().ledger.caughtSpecies.isEmpty())
        runtime.close()
    }

    @Test
    fun recordsObservedOpponentsAgainstTheCurrentLiveAreaInMemory() {
        val identity = "f".repeat(64)
        val saveIdentity = "2".repeat(64)
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog(
                identity,
                EngineFamily.EMERALD,
                Platform.GBA,
                speciesById = mapOf(13 to saveSpecies(13), 16 to saveSpecies(16)),
                encounterAreas = listOf(EncounterArea(0x0010 * 10 + 1, CatalogField.available("Area"), 1, emptyList())),
            ),
        )
        assertTrue(runtime.applySaveSnapshot(emptySave(identity, saveIdentity), SaveRamView(status = "MATCHED")))
        runtime.updateLiveArea(0x0010)
        val first = BattleMonSnapshot(
            battlerIndex = 1, position = 1, speciesId = 13, level = 3, hp = 15, maxHp = 15,
            ivs = List(6) { 10 }, moves = listOf(40, 0, 0, 0), pp = listOf(35, 0, 0, 0),
            typeIds = listOf(6, 3), abilityId = 19, personality = 200,
        )
        val second = first.copy(battlerIndex = 3, position = 3, speciesId = 16, personality = 201)
        val sample = BattleMemorySample(
            layout = ResolvedBattleLayout(0x143C, 0x1420, 0x142C, 0x16EE, 0x1874, 0x1878, 4),
            battlers = listOf(first, second), opponents = listOf(first, second), selectedMoveId = null,
            target = BattleTarget(0, TargetMode.MANUAL_TARGET_FALLBACK), capabilities = emptyMap(),
        )

        runtime.applyBattleThroughState(BattleTrackingUpdate(true, sample))
        runtime.updateLiveArea(0x0011)
        runtime.applyBattleThroughState(BattleTrackingUpdate(true, sample.copy(opponents = listOf(second))))

        val ledger = runtime.gateway.bootstrap().ledger
        assertEquals(setOf(13, 16), ledger.seenSpeciesByArea.getValue(0x0010))
        assertEquals(setOf(16), ledger.seenSpeciesByArea.getValue(0x0011))
        runtime.close()
    }

    @Test
    fun retainsVisitedMapsWithoutEncountersAndRoutesTheSelectedMapLocationIntoAreaDex() {
        val identity = "9".repeat(64)
        val saveIdentity = "3".repeat(64)
        val selectedAreaId = 0x0011 * 10 + 1
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog(
                identity,
                EngineFamily.EMERALD,
                Platform.GBA,
                encounterAreas = listOf(
                    EncounterArea(
                        selectedAreaId,
                        CatalogField.available("Oldale grass"),
                        1,
                        listOf(EncounterSlot(1, 2, 3, 100)),
                    ),
                    EncounterArea(
                        0x0012 * 10 + 1,
                        CatalogField.available("Decoy grass"),
                        1,
                        listOf(EncounterSlot(2, 2, 3, 100)),
                    ),
                ),
                worldMaps = WorldMapCatalog(
                    regions = listOf(
                        WorldMapRegion(
                            "hoenn", "Hoenn", 8, 8, 1, 1, "world/hoenn",
                            listOf(
                                WorldMapLocation("littleroot", "Littleroot Town", setOf(0x0009), listOf(WorldMapCell(0, 0, 1, 1))),
                                WorldMapLocation("oldale", "Oldale Town", setOf(0x0011, 0x0012), listOf(WorldMapCell(0, 0, 1, 1))),
                            ),
                        ),
                        WorldMapRegion(
                            "decoy", "Decoy", 8, 8, 1, 1, "world/decoy",
                            listOf(WorldMapLocation("oldale", "Decoy Town", setOf(0x0012), listOf(WorldMapCell(0, 0, 1, 1)))),
                        ),
                    ),
                    assets = mapOf(
                        "world/hoenn" to RgbaSprite(8, 8, IntArray(64)),
                        "world/decoy" to RgbaSprite(8, 8, IntArray(64)),
                    ),
                ),
            ),
        )
        assertTrue(runtime.applySaveSnapshot(emptySave(identity, saveIdentity), SaveRamView(status = "MATCHED")))

        runtime.action("OPEN_SPECIES", mapOf("speciesId" to "1"))
        runtime.updateLiveArea(0x0009)
        runtime.updateLiveArea(0x0011)
        val state = runtime.action("MAP_AREA", mapOf("regionKey" to "hoenn", "locationKey" to "oldale"))

        assertEquals(setOf(0x0009, 0x0011), runtime.gateway.bootstrap().ledger.visitedAreaBaseIds)
        assertEquals("AREA", state.filter)
        assertEquals(AppScreen.POKEDEX.name, state.screen)
        assertEquals(selectedAreaId, state.selectedAreaId)
        assertEquals(listOf(selectedAreaId, 0x0012 * 10 + 1), state.currentAreaIds)
        assertEquals(listOf(0x0009, 0x0011), state.revealedAreaBaseIds)
        runtime.close()
    }

    @Test
    fun validatedSaveStartsWithoutLegacyKnowledge() {
        val identity = "8".repeat(64)
        val saveIdentity = "4".repeat(64)
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog(
                identity,
                EngineFamily.EMERALD,
                Platform.GBA,
                speciesById = mapOf(25 to saveSpecies(25)),
                movesById = mapOf(
                    33 to MoveRecord(
                        33,
                        CatalogField.available("TACKLE"),
                        CatalogField.notFound("fixture"),
                        CatalogField.notFound("fixture"),
                        CatalogField.notFound("fixture"),
                        CatalogField.notFound("fixture"),
                        CatalogField.notFound("fixture"),
                    ),
                ),
            ),
        )

        assertTrue(runtime.gateway.bootstrap().ledger.seenSpecies.isEmpty())
        assertTrue(runtime.gateway.bootstrap().ledger.caughtSpecies.isEmpty())

        assertTrue(runtime.applySaveSnapshot(emptySave(identity, saveIdentity), SaveRamView(status = "MATCHED")))

        val restored = runtime.gateway.bootstrap().ledger
        assertTrue(restored.seenSpecies.isEmpty())
        assertTrue(restored.caughtSpecies.isEmpty())
        assertTrue(restored.observedMoves.isEmpty())
        runtime.close()
    }

    @Test
    fun liveMapPositionRetainsAdjacentHiddenItemDiscoveryInMemory() {
        val identity = "d".repeat(64)
        val saveIdentity = "4".repeat(64)
        val map = LocalMap("local/0102", "Route", 0x0102, 160, 160, 10, 10, "local/0102/map")
        val poi = LocalMapPoi(
            key = "local/0102/bg/0",
            localMapKey = map.key,
            baseAreaId = map.baseAreaId,
            tileX = 4,
            tileY = 4,
            kind = LocalMapPoiKind.HIDDEN_ITEM,
            organicVisibility = LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE,
            item = LocalMapPoiItem(13),
        )
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog(
                identity,
                EngineFamily.EMERALD,
                Platform.GBA,
                localMaps = LocalMapCatalog(
                    maps = listOf(map),
                    assets = mapOf(
                        map.imageAssetKey to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)),
                    ),
                    pois = listOf(poi),
                ),
            ),
        )
        assertTrue(runtime.applySaveSnapshot(emptySave(identity, saveIdentity), SaveRamView(status = "MATCHED")))
        runtime.updateLiveArea(map.baseAreaId)

        runtime.updateLiveMapPosition(RuntimeMapPosition(3, 3))

        assertEquals(setOf(poi.key), runtime.gateway.bootstrap().ledger.proximityRevealedPoiKeys)
        runtime.close()
    }

    @Test
    fun saveEventFlagsRetainCollectedLocalMapItemsInMemory() {
        val identity = "f".repeat(64)
        val saveIdentity = "6".repeat(64)
        val map = LocalMap("local/0102", "Route", 0x0102, 160, 160, 10, 10, "local/0102/map")
        val poi = LocalMapPoi(
            key = "local/0102/bg/0",
            localMapKey = map.key,
            baseAreaId = map.baseAreaId,
            tileX = 4,
            tileY = 4,
            kind = LocalMapPoiKind.HIDDEN_ITEM,
            organicVisibility = LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE,
            item = LocalMapPoiItem(13, collectionFlagId = 1007),
        )
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog(
                identity,
                EngineFamily.EMERALD,
                Platform.GBA,
                localMaps = LocalMapCatalog(
                    maps = listOf(map),
                    assets = mapOf(
                        map.imageAssetKey to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)),
                    ),
                    pois = listOf(poi),
                ),
            ),
        )

        assertTrue(
            runtime.applySaveSnapshot(
                emptySave(identity, saveIdentity).copy(eventFlagIds = setOf(1007)),
                SaveRamView(status = "MATCHED"),
            ),
        )

        val ledger = runtime.gateway.bootstrap().ledger
        assertTrue(ledger.collectedPoiKeys.isEmpty())
        assertTrue(ledger.identifiedPoiKeys.isEmpty())
        assertEquals("COLLECTED", runtime.stateView().localMapPois.single().state)
        runtime.close()
    }

    @Test
    fun liveEventFlagsRetainCollectedLocalMapItemsInMemory() {
        val identity = "9".repeat(64)
        val saveIdentity = "7".repeat(64)
        val map = LocalMap("local/0102", "Route", 0x0102, 160, 160, 10, 10, "local/0102/map")
        val poi = LocalMapPoi(
            key = "local/0102/bg/0",
            localMapKey = map.key,
            baseAreaId = map.baseAreaId,
            tileX = 4,
            tileY = 4,
            kind = LocalMapPoiKind.HIDDEN_ITEM,
            organicVisibility = LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE,
            item = LocalMapPoiItem(13, collectionFlagId = 1007),
        )
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog(
                identity,
                EngineFamily.EMERALD,
                Platform.GBA,
                localMaps = LocalMapCatalog(
                    maps = listOf(map),
                    assets = mapOf(
                        map.imageAssetKey to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)),
                    ),
                    pois = listOf(poi),
                ),
            ),
        )
        assertTrue(runtime.applySaveSnapshot(emptySave(identity, saveIdentity), SaveRamView(status = "MATCHED")))

        runtime.updateLiveGameState(
            liveSnapshot(
                identity,
                unavailableValue("trainer omitted"),
                unavailableValue("party omitted"),
                eventFlags = LiveValue.Available(setOf(1007)),
            ),
        )

        assertTrue(runtime.gateway.bootstrap().ledger.collectedPoiKeys.isEmpty())
        assertEquals("COLLECTED", runtime.stateView().localMapPois.single().state)
        runtime.close()
    }

    @Test
    fun mapPoiPreferencesDefaultToShowingEverythingAndRemainInMemory() {
        val identity = "e".repeat(64)
        val saveIdentity = "5".repeat(64)
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog("fixture.gba", ParsedCatalog(identity, EngineFamily.EMERALD, Platform.GBA))
        assertTrue(runtime.applySaveSnapshot(emptySave(identity, saveIdentity), SaveRamView(status = "MATCHED")))

        val defaults = runtime.stateView().localMapPoiPreferences
        assertTrue(defaults.showPlaces)
        assertTrue(defaults.showServices)
        assertTrue(defaults.showAvailableItems)
        assertTrue(defaults.showCollectedItems)
        assertTrue(defaults.showUnknownPois)
        assertEquals(0, defaults.iconZoomThresholdPercent)
        assertEquals(0, defaults.labelZoomThresholdPercent)

        val changed = runtime.action(
            "MAP_POI_SETTINGS",
            mapOf(
                "showPlaces" to "false",
                "showUnknownPois" to "false",
                "iconZoomThresholdPercent" to "40",
                "labelZoomThresholdPercent" to "65",
            ),
        ).localMapPoiPreferences

        assertFalse(changed.showPlaces)
        assertFalse(changed.showUnknownPois)
        assertEquals(40, changed.iconZoomThresholdPercent)
        assertEquals(65, changed.labelZoomThresholdPercent)
        runtime.close()
    }
    @Test
    fun reusesAnUnchangedPresentationSnapshotForPollingClients() {
        val runtime = ProductionCompanionRuntime(parserWorker = ImmediateExecutorService())
        runtime.loadCatalog("fixture.gba", ParsedCatalog("sha", EngineFamily.EMERALD, Platform.GBA))

        val first = runtime.stateView()
        val second = runtime.stateView()

        assertSame(first, second)
        assertEquals(1L, runtime.performanceCounters().getValue("analysis.party.recomputations"))
        assertTrue(runtime.performanceCounters().getValue("analysis.party.cpuNanos") > 0L)
        runtime.close()
    }
    @Test
    fun exposesRealCatalogWithoutSimulatorActions() {
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog("sha", EngineFamily.EMERALD, Platform.GBA, romCrc32 = "1234ABCD"),
        )

        val bootstrap = runtime.bootstrap()

        assertEquals("1234ABCD", bootstrap.catalog?.crc32)
        assertEquals("fixture.gba", bootstrap.state.catalogName)
        assertNull(bootstrap.state.battle)
        assertThrows(IllegalArgumentException::class.java) {
            runtime.action("GENERATE", emptyMap())
        }
        runtime.close()
    }

    @Test
    fun restoresACompletedCatalogWithoutReadingTheRomAgain() {
        val catalog = ParsedCatalog(
            "a".repeat(64),
            EngineFamily.EMERALD,
            Platform.GBA,
            romCrc32 = "89ABCDEF",
        )
        val source = CatalogSourceMetadata.direct("Modern Emerald.gba", 16_777_216, "POKEMON EMER")
        val repository = FakeCatalogRepository(
            StoredCatalog(
                catalog,
                source,
                CatalogWriteProgress.complete(),
                committedSections = emptySet(),
                writtenAtEpochMs = 1,
            ),
        )
        val runtime = ProductionCompanionRuntime(catalogRepository = repository)

        assertTrue(runtime.restoreCatalog(catalog.romSha256))
        val restored = runtime.bootstrap()

        assertEquals("Modern Emerald.gba", restored.state.catalogName)
        assertEquals("CACHE_REOPEN", restored.state.loading.phase)
        assertEquals(1, restored.state.loading.completedUnits)
        assertEquals(1, restored.state.loading.totalUnits)
        assertEquals(catalog.romSha256, restored.catalog?.hash)
        assertFalse(runtime.restoreCatalog("b".repeat(64)))
        assertNull(runtime.bootstrap().catalog)
        assertFalse(runtime.bootstrap().state.catalogReady)
        runtime.close()
    }

    @Test
    fun reopeningAnAlreadyActiveCatalogDoesNotRestartSetupOrRewriteTheCache() {
        val bytes = ByteArray(0xC0)
        "POKEMON EMER".toByteArray().copyInto(bytes, 0xA0)
        val rom = RomImage(bytes)
        val catalog = ParsedCatalog(rom.sha256, EngineFamily.EMERALD, Platform.GBA)
        val repository = FakeCatalogRepository(
            StoredCatalog(
                catalog,
                CatalogSourceMetadata.direct("Modern Emerald.gba", rom.size, "POKEMON EMER"),
                CatalogWriteProgress.complete(),
                committedSections = emptySet(),
                writtenAtEpochMs = 1,
            ),
        )
        val runtime = ProductionCompanionRuntime(
            parserWorker = ImmediateExecutorService(),
            catalogRepository = repository,
        )

        assertTrue(runtime.restoreCatalog(rom.sha256))
        val version = runtime.bootstrap().state.version
        var completion: Result<Unit>? = null
        runtime.load(LoadedRom("Modern Emerald.gba", rom)) { completion = it }

        assertTrue(requireNotNull(completion).isSuccess)
        assertEquals(0, repository.writeCalls)
        assertEquals(version, runtime.bootstrap().state.version)
        runtime.close()
    }

    @Test
    fun doesNotExposeSaveParsingContextWhileAStoredCatalogIsStillBeingPublished() {
        val hash = "a".repeat(64)
        val catalog = ParsedCatalog(hash, EngineFamily.EMERALD, Platform.GBA)
        val runtime = ProductionCompanionRuntime(
            catalogRepository = FakeCatalogRepository(
                StoredCatalog(
                    catalog,
                    CatalogSourceMetadata.direct("fixture.gba", 1, "FIXTURE"),
                    CatalogWriteProgress.complete(),
                    committedSections = emptySet(),
                    writtenAtEpochMs = 1,
                ),
            ),
        )
        var observedPublication = false
        var contextDuringPublication: com.darkaxt.dualdex.save.SaveParseContext? = null
        val subscription = runtime.gateway.subscribe {
            if (!observedPublication) {
                observedPublication = true
                contextDuringPublication = runtime.saveParseContext()
            }
        }

        assertTrue(runtime.restoreCatalog(hash))

        assertTrue(observedPublication)
        assertNull(contextDuringPublication)
        assertEquals(hash, runtime.saveParseContext()?.romIdentity)
        subscription.close()
        runtime.close()
    }

    @Test
    fun exposesRetroArchSetupAndSessionStateWithoutRequiringACatalog() {
        val runtime = ProductionCompanionRuntime()
        runtime.updateRetroArch(
            RetroArchView(
                storageGrant = "GRANTED",
                configGrant = "GRANTED",
                romGrant = "GRANTED",
                configState = "RESTART_REQUIRED",
                restartRequired = true,
                connection = "DISCONNECTED",
                indexedRoms = 14,
                message = "Restart RetroArch to verify Network Commands.",
            ),
        )

        val state = runtime.stateView()

        assertEquals("GRANTED", state.retroArch.storageGrant)
        assertEquals("GRANTED", state.retroArch.configGrant)
        assertEquals(14, state.retroArch.indexedRoms)
        assertTrue(state.retroArch.restartRequired)
        runtime.close()
    }

    @Test
    fun updatesTheOptionalDisplayModeThroughTheNormalSettingsContract() {
        val runtime = ProductionCompanionRuntime()

        val state = runtime.action(
            "SETTINGS",
            mapOf("displayMode" to "OVERLAY"),
        )

        assertEquals("OVERLAY", state.settings.let { it as com.enrpau.dualscreendex.companion.model.CompanionSettings }.displayMode.name)
        runtime.close()
    }

    @Test
    fun parsesAndClampsTheLiveBattlePollingSetting() {
        val runtime = ProductionCompanionRuntime()

        var settings = runtime.action("SETTINGS", mapOf("battlePollingIntervalMs" to "0")).settings as CompanionSettings
        assertEquals(1, settings.battlePollingIntervalMs)
        assertEquals(1, runtime.battlePollingIntervalMs())

        settings = runtime.action("SETTINGS", mapOf("battlePollingIntervalMs" to "99")).settings as CompanionSettings
        assertEquals(20, settings.battlePollingIntervalMs)
        assertEquals(20, runtime.battlePollingIntervalMs())
        runtime.close()
    }

    @Test
    fun startsFromAndPersistsTheCompleteSettingsDocument() {
        var persisted: CompanionSettings? = null
        val runtime = ProductionCompanionRuntime(
            initialSettings = CompanionSettings(knowledgeMode = KnowledgeMode.DISCOVERED, theme = Theme.DARK),
            onSettingsChanged = { persisted = it },
        )

        val state = runtime.action(
            "SETTINGS",
            mapOf("displayTarget" to "EXTERNAL", "theme" to "LIGHT", "fontScale" to "1.2"),
        )

        val settings = state.settings as CompanionSettings
        assertEquals(KnowledgeMode.DISCOVERED, settings.knowledgeMode)
        assertEquals(DisplayTarget.EXTERNAL, settings.displayTarget)
        assertEquals(Theme.LIGHT, settings.theme)
        assertEquals(1.2, settings.fontScale, 0.0)
        assertEquals(settings, persisted)
        runtime.close()
    }

    @Test
    fun switchingRulesetsReusesTheOpenCatalogAndSaveSnapshotWithoutDatabaseWrites() {
        val repository = RecordingCatalogRepository()
        val runtime = ProductionCompanionRuntime(catalogRepository = repository)
        val hash = "a".repeat(64)
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog(
                hash,
                EngineFamily.EMERALD,
                Platform.GBA,
                learnsetRulesets = listOf(
                    LearnsetRuleset("base", "Base", 1, 1.0, emptyMap(), primary = true),
                    LearnsetRuleset("alternate", "Alternate", 2, 0.9, emptyMap()),
                ),
            ),
        )
        runtime.updateSaveRam(SaveRamView(status = "MATCHED", sourceName = "fixture.srm"))

        val state = runtime.action("SETTINGS", mapOf("ruleset" to "alternate"))

        assertEquals(hash, runtime.catalogHash())
        assertEquals("alternate", state.activeRulesetId)
        assertEquals("MATCHED", state.saveRam.status)
        assertEquals("fixture.srm", state.saveRam.sourceName)
        assertEquals(0, repository.writeCalls)
        runtime.close()
    }

    @Test
    fun catalogSwitchesPublishAndPersistEachRomsEffectiveSettingsBeforeReady() {
        val hashA = "a".repeat(64)
        val hashB = "b".repeat(64)
        val persisted = mutableMapOf(
            hashA to CompanionSettings(ruleset = "original", theme = Theme.DARK),
            hashB to CompanionSettings(ruleset = "modern", theme = Theme.LIGHT),
        )
        val writes = mutableListOf<Pair<String?, CompanionSettings>>()
        val runtime = ProductionCompanionRuntime(
            initialSettings = persisted.getValue(hashA),
            settingsForRom = { hash -> persisted.getValue(hash) },
            onRomSettingsChanged = { hash, settings ->
                writes += hash to settings
                if (hash != null) persisted[hash] = settings
            },
        )
        val publications = mutableListOf<com.enrpau.dualscreendex.companion.model.AppSnapshot>()
        val subscription = runtime.gateway.subscribe(publications::add)

        runtime.loadCatalog("a.gba", levelUpRulesetCatalog(hashA))
        assertEquals(Theme.DARK, runtime.gateway.bootstrap().settings.theme)
        assertEquals("original", runtime.gateway.bootstrap().settings.ruleset)
        assertEquals("original", runtime.stateView().activeRulesetId)
        runtime.action("SETTINGS", mapOf("theme" to "GAME", "ruleset" to "modern"))

        publications.clear()
        runtime.loadCatalog("b.gba", levelUpRulesetCatalog(hashB))
        assertEquals(Theme.LIGHT, runtime.gateway.bootstrap().settings.theme)
        assertEquals("modern", runtime.gateway.bootstrap().settings.ruleset)
        assertEquals(Theme.LIGHT, publications.first { it.catalogReady }.settings.theme)
        runtime.action("SETTINGS", mapOf("theme" to "DARK", "ruleset" to "original"))

        runtime.loadCatalog("a.gba", levelUpRulesetCatalog(hashA))
        assertEquals(Theme.GAME, runtime.gateway.bootstrap().settings.theme)
        assertEquals("modern", runtime.gateway.bootstrap().settings.ruleset)
        assertEquals(listOf(hashA, hashB), writes.map { it.first })
        subscription.close()
        runtime.close()
    }

    @Test
    fun unavailableStoredManualRulesetFailsClosedWithoutBeingErased() {
        val hash = "c".repeat(64)
        val stored = CompanionSettings(ruleset = "temporarily-missing", theme = Theme.DARK)
        val writes = mutableListOf<Pair<String?, CompanionSettings>>()
        val runtime = ProductionCompanionRuntime(
            settingsForRom = { stored },
            onRomSettingsChanged = { rom, settings -> writes += rom to settings },
        )

        runtime.loadCatalog("missing.gba", levelUpRulesetCatalog(hash))

        assertEquals("temporarily-missing", runtime.gateway.bootstrap().settings.ruleset)
        assertNull(runtime.stateView().activeRulesetId)
        assertTrue(writes.isEmpty())
        runtime.close()
    }

    @Test
    fun runtimeWritesKeepDeviceOwnedFieldsSharedAcrossRomProfiles() {
        val hashA = "d".repeat(64)
        val hashB = "e".repeat(64)
        var document: String? = null
        val repository = SettingsRepository({ document }, { document = it })
        repository.writeGlobal(CompanionSettings(displayTarget = DisplayTarget.EXTERNAL, overlayScale = 0.7))
        repository.writeForRom(hashA, repository.readForRom(hashA).copy(theme = Theme.DARK))
        repository.writeForRom(hashB, repository.readForRom(hashB).copy(theme = Theme.LIGHT))
        val runtime = ProductionCompanionRuntime(
            initialSettings = repository.readForRom(hashA),
            settingsForRom = repository::readForRom,
            onRomSettingsChanged = repository::writeForRom,
        )

        runtime.loadCatalog("a.gba", levelUpRulesetCatalog(hashA))
        runtime.action("SETTINGS", mapOf("displayTarget" to "HANDHELD"))
        runtime.loadCatalog("b.gba", levelUpRulesetCatalog(hashB))

        assertEquals(DisplayTarget.HANDHELD, runtime.gateway.bootstrap().settings.displayTarget)
        assertEquals(0.7, runtime.gateway.bootstrap().settings.overlayScale, 0.0)
        assertEquals(Theme.LIGHT, runtime.gateway.bootstrap().settings.theme)
        runtime.close()
    }

    @Test
    fun asyncCatalogTransitionDoesNotApplyOrPersistIncomingSettingsUntilItWins() {
        val hashA = "a".repeat(64)
        val bytes = ByteArray(0xC0)
        "POKEMON EMER".toByteArray().copyInto(bytes, 0xA0)
        "BPEE".toByteArray().copyInto(bytes, 0xAC)
        val incomingRom = RomImage(bytes)
        val writes = mutableListOf<Pair<String?, CompanionSettings>>()
        val executor = HoldingExecutorService()
        val runtime = ProductionCompanionRuntime(
            parserWorker = executor,
            initialSettings = CompanionSettings(theme = Theme.DARK),
            settingsForRom = { sha ->
                if (sha == incomingRom.sha256) CompanionSettings(theme = Theme.LIGHT)
                else CompanionSettings(theme = Theme.DARK)
            },
            onRomSettingsChanged = { sha, settings -> writes += sha to settings },
        )
        runtime.loadCatalog("a.gba", ParsedCatalog(hashA, EngineFamily.EMERALD, Platform.GBA))
        val publications = mutableListOf<com.enrpau.dualscreendex.companion.model.AppSnapshot>()
        val subscription = runtime.gateway.subscribe(publications::add)

        runtime.load("b.gba", incomingRom)

        assertTrue(publications.isNotEmpty())
        assertTrue(publications.none { it.catalogReady })
        assertEquals(Theme.DARK, runtime.gateway.bootstrap().settings.theme)
        runtime.action("SETTINGS", mapOf("theme" to "GAME"))
        assertTrue(writes.isEmpty())
        assertEquals(1, executor.pendingCount)
        subscription.close()
        runtime.close()
    }

    @Test
    fun progressiveParseFailureNeverPublishesOrLeavesAPartialCatalogReady() {
        val hashA = "a".repeat(64)
        val incoming = ParsedCatalog("b".repeat(64), EngineFamily.EMERALD, Platform.GBA)
        val writes = RecordingCatalogRepository()
        val executor = HoldingExecutorService()
        val runtime = ProductionCompanionRuntime(
            parserWorker = executor,
            catalogRepository = writes,
            parseCatalog = { _, progress, _ ->
                progress(CatalogMaterializationProgress(CatalogMaterializationPhase.ESSENTIAL, 1, 5, incoming))
                error("synthetic parse failure")
            },
        )
        runtime.loadCatalog("a.gba", ParsedCatalog(hashA, EngineFamily.EMERALD, Platform.GBA))
        val publications = mutableListOf<Pair<Boolean, String?>>()
        val subscription = runtime.gateway.subscribe { snapshot ->
            publications += snapshot.catalogReady to runtime.catalogHash()
        }

        runtime.load("incoming.gba", RomImage(ByteArray(0xC0)))
        executor.runNext()

        assertEquals(1, writes.writeCalls)
        assertTrue(publications.none { (ready, hash) -> ready || hash == incoming.romSha256 })
        assertNull(runtime.catalogHash())
        assertFalse(runtime.gateway.bootstrap().catalogReady)
        subscription.close()
        runtime.close()
    }

    @Test
    fun progressiveParsePublishesTheCurrentCatalogModule() {
        val incoming = ParsedCatalog("b".repeat(64), EngineFamily.EMERALD, Platform.GBA)
        val executor = HoldingExecutorService()
        val runtime = ProductionCompanionRuntime(
            parserWorker = executor,
            parseCatalog = { _, checkpoint, work ->
                checkpoint(CatalogMaterializationProgress(CatalogMaterializationPhase.ESSENTIAL, 1, 5, incoming))
                work(CatalogWorkProgress(CatalogWorkModule.EVOLUTIONS_AND_LEARNSETS, 4, 11))
                incoming
            },
        )
        val phases = mutableListOf<com.enrpau.dualscreendex.companion.model.CatalogLoadingState>()
        val subscription = runtime.gateway.subscribe { snapshot -> phases += snapshot.catalogLoading }

        runtime.load("incoming.gba", RomImage(ByteArray(0xC0)))
        executor.runNext()

        assertTrue(phases.any {
            it.active && it.phase == "EVOLUTIONS_AND_LEARNSETS" && it.completedUnits == 4 && it.totalUnits == 11
        })
        assertTrue(phases.none { it.active && it.phase == "ESSENTIAL" })
        subscription.close()
        runtime.close()
    }

    @Test
    fun mapLocationWithoutEncounterAreasIsASilentNoOp() {
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog(
                "sha",
                EngineFamily.EMERALD,
                Platform.GBA,
                worldMaps = WorldMapCatalog(
                    regions = listOf(
                        WorldMapRegion(
                            "hoenn", "Hoenn", 8, 8, 1, 1, "world/hoenn",
                            listOf(WorldMapLocation("empty", "Empty Point", setOf(0x0011), listOf(WorldMapCell(0, 0, 1, 1)))),
                        ),
                    ),
                    assets = mapOf("world/hoenn" to RgbaSprite(8, 8, IntArray(64))),
                ),
            ),
        )
        val before = runtime.stateView()

        val after = runtime.action("MAP_AREA", mapOf("regionKey" to "hoenn", "locationKey" to "empty"))

        assertSame(before, after)
        runtime.close()
    }

    @Test
    fun supersededProgressNeverReplacesTheWinningCatalogOrItsSettings() {
        val hashA = "a".repeat(64)
        val winner = ParsedCatalog("b".repeat(64), EngineFamily.EMERALD, Platform.GBA)
        val stale = ParsedCatalog("c".repeat(64), EngineFamily.EMERALD, Platform.GBA)
        val executor = HoldingExecutorService()
        lateinit var runtime: ProductionCompanionRuntime
        runtime = ProductionCompanionRuntime(
            parserWorker = executor,
            settingsForRom = { sha -> CompanionSettings(theme = if (sha == winner.romSha256) Theme.LIGHT else Theme.DARK) },
            parseCatalog = { _, progress, _ ->
                progress(CatalogMaterializationProgress(CatalogMaterializationPhase.ESSENTIAL, 1, 5, stale))
                runtime.loadCatalog("winner.gba", winner)
                stale
            },
        )
        runtime.loadCatalog("a.gba", ParsedCatalog(hashA, EngineFamily.EMERALD, Platform.GBA))
        val publications = mutableListOf<Pair<Boolean, String?>>()
        val subscription = runtime.gateway.subscribe { snapshot -> publications += snapshot.catalogReady to runtime.catalogHash() }

        runtime.load("stale.gba", RomImage(ByteArray(0xC0)))
        executor.runNext()

        assertEquals(winner.romSha256, runtime.catalogHash())
        assertEquals(Theme.LIGHT, runtime.gateway.bootstrap().settings.theme)
        assertTrue(publications.none { (ready, hash) -> ready && hash == stale.romSha256 })
        subscription.close()
        runtime.close()
    }

    @Test
    fun directCacheReopenNeverPublishesReadyWithThePreviousCatalogIdentity() {
        val hashA = "a".repeat(64)
        val hashB = "b".repeat(64)
        val reopened = ParsedCatalog(hashB, EngineFamily.EMERALD, Platform.GBA)
        val runtime = ProductionCompanionRuntime(
            catalogRepository = FakeCatalogRepository(
                StoredCatalog(
                    reopened,
                    CatalogSourceMetadata.direct("b.gba", 1, "B"),
                    CatalogWriteProgress.complete(),
                    committedSections = emptySet(),
                    writtenAtEpochMs = 1,
                ),
            ),
            initialSettings = CompanionSettings(theme = Theme.DARK),
            settingsForRom = { sha ->
                if (sha == hashB) CompanionSettings(theme = Theme.LIGHT)
                else CompanionSettings(theme = Theme.DARK)
            },
        )
        runtime.loadCatalog("a.gba", ParsedCatalog(hashA, EngineFamily.EMERALD, Platform.GBA))
        val readyPublications = mutableListOf<Pair<com.enrpau.dualscreendex.companion.model.AppSnapshot, String?>>()
        val subscription = runtime.gateway.subscribe { snapshot ->
            if (snapshot.catalogReady) readyPublications += snapshot to runtime.catalogHash()
        }

        assertTrue(runtime.restoreCatalog(hashB))

        assertTrue(readyPublications.isNotEmpty())
        assertTrue(readyPublications.all { (snapshot, hash) ->
            hash == hashB && snapshot.settings.theme == Theme.LIGHT
        })
        subscription.close()
        runtime.close()
    }

    @Test
    fun verifiedRomCacheHitNeverPublishesAFromScratchPhase() {
        val rom = RomImage(ByteArray(0xC0))
        val parsed = ParsedCatalog(rom.sha256, EngineFamily.EMERALD, Platform.GBA)
        var parseCalls = 0
        val phases = mutableListOf<String>()
        val runtime = ProductionCompanionRuntime(
            parserWorker = ImmediateExecutorService(),
            catalogRepository = FakeCatalogRepository(
                StoredCatalog(
                    parsed,
                    CatalogSourceMetadata.direct("cached.gba", rom.size, "CACHED"),
                    CatalogWriteProgress.complete(),
                    committedSections = emptySet(),
                    writtenAtEpochMs = 1,
                ),
            ),
            parseCatalog = { _, _, _ ->
                parseCalls++
                parsed
            },
        )
        val subscription = runtime.gateway.subscribe { snapshot ->
            if (snapshot.catalogLoading.active) phases += snapshot.catalogLoading.phase
        }

        runtime.load("cached.gba", rom)

        assertEquals(listOf("CACHE_REOPEN"), phases.distinct())
        assertEquals(0, parseCalls)
        assertEquals("CACHE_REOPEN", runtime.gateway.bootstrap().catalogLoading.phase)
        subscription.close()
        runtime.close()
    }

    @Test
    fun verifiedRomCacheMissChecksCacheBeforePublishingParserWork() {
        val rom = RomImage(ByteArray(0xC0))
        val parsed = ParsedCatalog(rom.sha256, EngineFamily.EMERALD, Platform.GBA)
        var parseCalls = 0
        val phases = mutableListOf<String>()
        val runtime = ProductionCompanionRuntime(
            parserWorker = ImmediateExecutorService(),
            catalogRepository = RecordingCatalogRepository(),
            parseCatalog = { _, _, work ->
                parseCalls++
                work(CatalogWorkProgress(CatalogWorkModule.ROM_IDENTITY))
                parsed
            },
        )
        val subscription = runtime.gateway.subscribe { snapshot ->
            if (snapshot.catalogLoading.active) phases += snapshot.catalogLoading.phase
        }

        runtime.load("uncached.gba", rom)

        assertEquals("CACHE_REOPEN", phases.first())
        assertTrue(phases.indexOf("ROM_IDENTITY") > phases.indexOf("CACHE_REOPEN"))
        assertEquals(1, parseCalls)
        assertEquals("COMPLETE", runtime.gateway.bootstrap().catalogLoading.phase)
        subscription.close()
        runtime.close()
    }

    @Test
    fun incompatibleStoredCatalogExplainsWhyTheGameGuideIsRefreshed() {
        val rom = RomImage(ByteArray(0xC0))
        val parsed = ParsedCatalog(rom.sha256, EngineFamily.EMERALD, Platform.GBA)
        val repository = RecordingCatalogRepository(CatalogCacheDecision.MISS_INCOMPLETE_OR_INCOMPATIBLE)
        val messages = mutableListOf<String?>()
        val runtime = ProductionCompanionRuntime(
            parserWorker = ImmediateExecutorService(),
            catalogRepository = repository,
            parseCatalog = { _, _, work ->
                work(CatalogWorkProgress(CatalogWorkModule.ROM_IDENTITY))
                parsed
            },
        )
        val subscription = runtime.gateway.subscribe { snapshot ->
            if (snapshot.catalogLoading.phase == "ROM_IDENTITY") messages += snapshot.catalogLoading.message
        }

        runtime.load("outdated.gba", rom)

        assertTrue(messages.contains("Saved guide data needs to be refreshed for this version."))
        subscription.close()
        runtime.close()
    }

    @Test
    fun supersededAsyncRestoreNeverCommitsTheStaleCatalog() {
        val hashA = "a".repeat(64)
        val hashB = "b".repeat(64)
        val executor = HoldingExecutorService()
        val committed = mutableListOf<String>()
        val runtime = ProductionCompanionRuntime(
            parserWorker = executor,
            catalogRepository = FakeCatalogRepository(
                StoredCatalog(
                    ParsedCatalog(hashA, EngineFamily.EMERALD, Platform.GBA),
                    CatalogSourceMetadata.direct("a.gba", 1, "A"),
                    CatalogWriteProgress.complete(),
                    committedSections = emptySet(),
                    writtenAtEpochMs = 1,
                ),
            ),
            onCatalogCommitted = { sha, _ -> committed += sha },
        )

        runtime.restoreCatalogAsync(hashA)
        runtime.loadCatalog("b.gba", ParsedCatalog(hashB, EngineFamily.EMERALD, Platform.GBA))
        executor.runNext()

        assertTrue(committed.isEmpty())
        assertEquals(hashB, runtime.catalogHash())
        runtime.close()
    }

    @Test
    fun missingAsyncRestoreRestoresGlobalSettingsBeforeGlobalEdits() {
        val hashA = "a".repeat(64)
        val hashB = "b".repeat(64)
        var document: String? = null
        val repository = SettingsRepository({ document }, { document = it })
        val globals = CompanionSettings(theme = Theme.DARK)
        repository.writeGlobal(globals)
        repository.writeForRom(hashA, globals.copy(theme = Theme.LIGHT, attackEnabled = false))
        val executor = HoldingExecutorService()
        var cleared = 0
        val runtime = ProductionCompanionRuntime(
            parserWorker = executor,
            catalogRepository = FakeCatalogRepository(
                StoredCatalog(
                    ParsedCatalog(hashB, EngineFamily.EMERALD, Platform.GBA),
                    CatalogSourceMetadata.direct("b.gba", 1, "B"),
                    CatalogWriteProgress.complete(),
                    committedSections = emptySet(),
                    writtenAtEpochMs = 1,
                ),
            ),
            initialSettings = repository.readGlobal(),
            settingsForRom = repository::readForRom,
            globalSettings = repository::readGlobal,
            onRomSettingsChanged = repository::writeForRom,
            onCatalogCleared = { cleared++ },
        )

        runtime.restoreCatalogAsync(hashA)
        assertEquals(Theme.DARK, runtime.gateway.bootstrap().settings.theme)
        executor.runNext()
        assertEquals(Theme.DARK, runtime.gateway.bootstrap().settings.theme)
        assertEquals(1, cleared)
        runtime.action("SETTINGS", mapOf("density" to "COMPACT"))

        assertEquals(Theme.DARK, repository.readGlobal().theme)
        assertEquals(Density.COMPACT, repository.readGlobal().density)
        assertEquals(Theme.LIGHT, repository.readForRom(hashA).theme)
        assertFalse(repository.readForRom(hashA).attackEnabled)
        runtime.close()
    }

    @Test
    fun failedRomParseRestoresGlobalSettingsBeforeGlobalEdits() {
        val rom = RomImage(ByteArray(0xC0))
        var document: String? = null
        val repository = SettingsRepository({ document }, { document = it })
        val globals = CompanionSettings(theme = Theme.DARK)
        repository.writeGlobal(globals)
        repository.writeForRom(rom.sha256, globals.copy(theme = Theme.LIGHT, attackEnabled = false))
        var cleared = 0
        val runtime = ProductionCompanionRuntime(
            parserWorker = ImmediateExecutorService(),
            initialSettings = repository.readGlobal(),
            settingsForRom = repository::readForRom,
            globalSettings = repository::readGlobal,
            onRomSettingsChanged = repository::writeForRom,
            onCatalogCleared = { cleared++ },
        )

        runtime.load("unsupported.gba", rom)
        assertEquals(Theme.DARK, runtime.gateway.bootstrap().settings.theme)
        assertNull(runtime.gateway.bootstrap().catalogName)
        assertEquals(1, cleared)
        runtime.action("SETTINGS", mapOf("density" to "COMPACT"))

        assertEquals(Theme.DARK, repository.readGlobal().theme)
        assertEquals(Density.COMPACT, repository.readGlobal().density)
        assertEquals(Theme.LIGHT, repository.readForRom(rom.sha256).theme)
        assertFalse(repository.readForRom(rom.sha256).attackEnabled)
        runtime.close()
    }

    @Test
    fun synchronousMissingRestoreClearsCatalogProfileAndPublishesNoRomState() {
        val hashA = "a".repeat(64)
        val hashMissing = "b".repeat(64)
        var document: String? = null
        val repository = SettingsRepository({ document }, { document = it })
        val globals = CompanionSettings(theme = Theme.DARK)
        repository.writeGlobal(globals)
        repository.writeForRom(hashA, globals.copy(theme = Theme.LIGHT))
        var cleared = 0
        val runtime = ProductionCompanionRuntime(
            catalogRepository = FakeCatalogRepository(
                StoredCatalog(
                    ParsedCatalog(hashA, EngineFamily.EMERALD, Platform.GBA),
                    CatalogSourceMetadata.direct("a.gba", 1, "A"),
                    CatalogWriteProgress.complete(),
                    committedSections = emptySet(),
                    writtenAtEpochMs = 1,
                ),
            ),
            initialSettings = repository.readGlobal(),
            settingsForRom = repository::readForRom,
            globalSettings = repository::readGlobal,
            onRomSettingsChanged = repository::writeForRom,
            onCatalogCleared = { cleared++ },
        )
        runtime.loadCatalog("a.gba", ParsedCatalog(hashA, EngineFamily.EMERALD, Platform.GBA))

        assertFalse(runtime.restoreCatalog(hashMissing))

        assertNull(runtime.catalogHash())
        assertFalse(runtime.gateway.bootstrap().catalogReady)
        assertNull(runtime.gateway.bootstrap().catalogName)
        assertEquals(Theme.DARK, runtime.gateway.bootstrap().settings.theme)
        assertEquals(1, cleared)
        runtime.action("SETTINGS", mapOf("density" to "COMPACT"))
        assertEquals(Density.COMPACT, repository.readGlobal().density)
        assertEquals(Theme.LIGHT, repository.readForRom(hashA).theme)
        runtime.close()
    }

    @Test
    fun romDisplayModeSwitchInvokesNativeCallbackWithoutLoopingOnUserActions() {
        val hashA = "a".repeat(64)
        val hashB = "b".repeat(64)
        val nativeModes = mutableListOf<DisplayMode>()
        val runtime = ProductionCompanionRuntime(
            settingsForRom = { sha ->
                CompanionSettings(displayMode = if (sha == hashA) DisplayMode.OVERLAY else DisplayMode.DOCKED)
            },
            onRomDisplayModeChanged = nativeModes::add,
        )

        runtime.loadCatalog("a.gba", ParsedCatalog(hashA, EngineFamily.EMERALD, Platform.GBA))
        runtime.loadCatalog("b.gba", ParsedCatalog(hashB, EngineFamily.EMERALD, Platform.GBA))
        runtime.action("SETTINGS", mapOf("displayMode" to "OVERLAY"))

        assertEquals(listOf(DisplayMode.OVERLAY, DisplayMode.DOCKED), nativeModes)
        runtime.close()
    }

    @Test
    fun externalOverlayResizeCannotBeOverwrittenByAnUnrelatedRomSetting() {
        val hash = "a".repeat(64)
        var document: String? = null
        val repository = SettingsRepository({ document }, { document = it })
        val runtime = ProductionCompanionRuntime(
            settingsForRom = repository::readForRom,
            onRomSettingsChanged = repository::writeForRom,
        )
        runtime.loadCatalog("a.gba", ParsedCatalog(hash, EngineFamily.EMERALD, Platform.GBA))
        repository.writeGlobal(repository.readGlobal().copy(overlayScale = 0.7))

        runtime.updateOverlayScale(0.7)
        runtime.action("SETTINGS", mapOf("theme" to "DARK"))

        assertEquals(0.7, repository.readGlobal().overlayScale, 0.0)
        assertEquals(Theme.DARK, repository.readForRom(hash).theme)
        runtime.close()
    }

    @Test
    fun firstCatalogWriteAfterStartupWithoutLastHashKeepsLegacyRulesetRomLocal() {
        val bytes = ByteArray(0xC0)
        "POKEMON EMER".toByteArray().copyInto(bytes, 0xA0)
        "BPEE".toByteArray().copyInto(bytes, 0xAC)
        val rom = RomImage(bytes)
        val hash = rom.sha256
        val stored = StoredCatalog(
            levelUpRulesetCatalog(hash),
            CatalogSourceMetadata.direct("first.gba", bytes.size, "POKEMON EMER"),
            CatalogWriteProgress.complete(),
            committedSections = emptySet(),
            writtenAtEpochMs = 1,
        )
        var document: String? = """{"schema":1,"ruleset":"modern","theme":"DARK"}"""
        val repository = SettingsRepository({ document }, { document = it })
        var lastCatalogSha: String? = null
        repository.migrateLegacyRuleset(null)
        val runtime = ProductionCompanionRuntime(
            parserWorker = ImmediateExecutorService(),
            catalogRepository = FakeCatalogRepository(stored),
            initialSettings = repository.readForRom(null),
            settingsForRom = repository::readForRom,
            onRomSettingsChanged = repository::writeForRom,
            onCatalogCommitted = { committedSha, _ ->
                repository.migrateLegacyRuleset(committedSha)
                lastCatalogSha = committedSha
            },
        )

        runtime.load("first.gba", rom)
        runtime.action("SETTINGS", mapOf("theme" to "LIGHT"))

        assertEquals(hash, lastCatalogSha)
        assertEquals("AUTO", repository.readGlobal().ruleset)
        assertEquals("modern", repository.readForRom(hash).ruleset)
        assertEquals(Theme.LIGHT, repository.readForRom(hash).theme)
        assertEquals("AUTO", repository.readForRom("b".repeat(64)).ruleset)
        runtime.close()
    }

    @Test
    fun autoUsesOnlyTheLevelUpRulesetDetectedFromTheCurrentSave() {
        val hash = "b".repeat(64)
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog(
            "modern.gba",
            ParsedCatalog(
                hash,
                EngineFamily.EMERALD,
                Platform.GBA,
                learnsetRulesets = listOf(
                    LearnsetRuleset(
                        "original", "Original", 1, 1.0, emptyMap(),
                        levelUpSelector = LevelUpRulesetSelector(0x3DA6, 0x02, 0x00),
                    ),
                    LearnsetRuleset(
                        "modern", "Modern", 2, 1.0, emptyMap(),
                        levelUpSelector = LevelUpRulesetSelector(0x3DA6, 0x02, 0x02),
                    ),
                ),
            ),
        )

        assertEquals(setOf("original", "modern"), requireNotNull(runtime.saveParseContext()).levelUpRulesetSelectors.map { it.rulesetId }.toSet())
        assertNull(runtime.stateView().activeRulesetId)
        assertTrue(runtime.stateView().rulesetAssumed)

        assertTrue(
            runtime.applySaveSnapshot(
                SaveSnapshot(
                    romIdentity = hash,
                    saveIdentity = "save",
                    saveGeneration = 3,
                    saveCounter = 3,
                    currentArea = null,
                    seenDexNumbers = emptySet(),
                    caughtDexNumbers = emptySet(),
                    party = emptyList(),
                    storedIndividuals = emptyList(),
                    capabilities = emptyMap(),
                    detectedLevelUpRulesetId = "modern",
                    levelUpRulesetDetectionResolved = true,
                    levelUpRulesetDetectionFingerprint = LevelUpRulesetDetectionFingerprint.create(
                        requireNotNull(runtime.saveParseContext()).levelUpRulesetSelectors,
                        "modern",
                    ),
                ),
                SaveRamView(status = "MATCHED", sourceName = "modern.srm"),
            ),
        )

        assertEquals("modern", runtime.stateView().activeRulesetId)
        assertFalse(runtime.stateView().rulesetAssumed)
        assertEquals("original", runtime.action("SETTINGS", mapOf("ruleset" to "original")).activeRulesetId)
        assertFalse(runtime.stateView().rulesetAssumed)
        runtime.close()
    }

    @Test
    fun autoRejectsForgedAndLegacyDetectionProvenanceButManualRecoveryStillWorks() {
        val hash = "d".repeat(64)
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog("modern.gba", levelUpRulesetCatalog(hash))

        listOf("0".repeat(64), null).forEachIndexed { index, fingerprint ->
            assertTrue(
                runtime.applySaveSnapshot(
                    levelUpSnapshot(hash, "modern", fingerprint, counter = index.toLong() + 1),
                    SaveRamView(status = "MATCHED", sourceName = "modern.srm"),
                ),
            )
            assertNull(runtime.stateView().activeRulesetId)
            assertTrue(runtime.stateView().rulesetAssumed)
        }

        assertEquals("modern", runtime.action("SETTINGS", mapOf("ruleset" to "modern")).activeRulesetId)
        assertFalse(runtime.stateView().rulesetAssumed)
        runtime.close()
    }

    @Test
    fun autoRejectsDetectionFingerprintFromPreviousSelectorDescriptors() {
        val hash = "e".repeat(64)
        val oldCatalog = levelUpRulesetCatalog(hash, selectorOffset = 0x3DA6)
        val oldSelectors = oldCatalog.learnsetRulesets.map { ruleset ->
            val selector = requireNotNull(ruleset.levelUpSelector)
            com.darkaxt.dualdex.save.SaveByteSelector(
                ruleset.id,
                selector.saveBlock1ByteOffset,
                selector.mask,
                selector.expectedValue,
            )
        }
        val staleFingerprint = requireNotNull(LevelUpRulesetDetectionFingerprint.create(oldSelectors, "modern"))
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog("modern-changed.gba", levelUpRulesetCatalog(hash, selectorOffset = 0x3DA7))

        assertTrue(
            runtime.applySaveSnapshot(
                levelUpSnapshot(hash, "modern", staleFingerprint),
                SaveRamView(status = "MATCHED", sourceName = "modern.srm"),
            ),
        )

        assertNull(runtime.stateView().activeRulesetId)
        assertTrue(runtime.stateView().rulesetAssumed)
        runtime.close()
    }

    @Test
    fun autoRejectsPersistedDetectionWhenCatalogSelectorCoverageIsIncomplete() {
        val hash = "c".repeat(64)
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog(
            "partial-selector.gba",
            ParsedCatalog(
                hash,
                EngineFamily.EMERALD,
                Platform.GBA,
                learnsetRulesets = listOf(
                    LearnsetRuleset("original", "Original", 1, 1.0, emptyMap()),
                    LearnsetRuleset(
                        "modern", "Modern", 2, 1.0, emptyMap(),
                        levelUpSelector = LevelUpRulesetSelector(0x3DA6, 0x02, 0x02),
                    ),
                ),
            ),
        )

        assertTrue(requireNotNull(runtime.saveParseContext()).levelUpRulesetSelectors.isEmpty())
        assertTrue(
            runtime.applySaveSnapshot(
                SaveSnapshot(
                    romIdentity = hash,
                    saveIdentity = "save",
                    saveGeneration = 3,
                    saveCounter = 3,
                    currentArea = null,
                    seenDexNumbers = emptySet(),
                    caughtDexNumbers = emptySet(),
                    party = emptyList(),
                    storedIndividuals = emptyList(),
                    capabilities = emptyMap(),
                    detectedLevelUpRulesetId = "modern",
                    levelUpRulesetDetectionResolved = true,
                ),
                SaveRamView(status = "MATCHED", sourceName = "partial-selector.srm"),
            ),
        )

        assertNull(runtime.stateView().activeRulesetId)
        assertTrue(runtime.stateView().rulesetAssumed)
        runtime.close()
    }

    @Test
    fun reportsAutomaticCatalogActivationOnlyAfterTheVerifiedCatalogIsOpen() {
        val bytes = ByteArray(0xC0)
        "POKEMON EMER".toByteArray().copyInto(bytes, 0xA0)
        "BPEE".toByteArray().copyInto(bytes, 0xAC)
        val rom = RomImage(bytes)
        val catalog = ParsedCatalog(
            rom.sha256,
            EngineFamily.EMERALD,
            Platform.GBA,
            romCrc32 = rom.crc32,
        )
        val repository = FakeCatalogRepository(
            StoredCatalog(
                catalog,
                CatalogSourceMetadata.direct("Modern Emerald.gba", rom.size, "POKEMON EMER"),
                CatalogWriteProgress.complete(),
                committedSections = emptySet(),
                writtenAtEpochMs = 1,
            ),
        )
        val runtime = ProductionCompanionRuntime(
            parserWorker = ImmediateExecutorService(),
            catalogRepository = repository,
        )
        var completion: Result<Unit>? = null

        runtime.load(LoadedRom("Modern Emerald.gba", rom)) { completion = it }

        assertTrue(requireNotNull(completion).isSuccess)
        assertEquals(rom.sha256, runtime.catalogHash())
        assertEquals("Modern Emerald.gba", runtime.bootstrap().state.catalogName)
        runtime.close()
    }

    @Test
    fun exposesCatalogCoupledSaveContextAndPublishesOneValidatedSnapshot() {
        val hash = "a".repeat(64)
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog(
                hash,
                EngineFamily.EMERALD,
                Platform.GBA,
                speciesById = mapOf(
                    25 to SpeciesRecord(
                        id = 25,
                        dexNumber = CatalogField.available(25),
                        name = CatalogField.available("PIKACHU"),
                        typeIds = CatalogField.available(emptyList()),
                        baseStats = CatalogField.notFound("fixture"),
                        sprite = CatalogField.notFound("fixture"),
                        abilityIds = CatalogField.available(listOf(9, 31)),
                        growthRate = CatalogField.available(0),
                    ),
                ),
                movesById = mapOf(
                    33 to MoveRecord(
                        id = 33,
                        name = CatalogField.available("TACKLE"),
                        typeId = CatalogField.available(0),
                        category = CatalogField.notFound("fixture"),
                        power = CatalogField.available(40),
                        accuracy = CatalogField.available(100),
                        pp = CatalogField.available(35),
                    ),
                ),
                runtimeMetadata = com.enrpau.dualscreendex.parser.catalog.CatalogRuntimeMetadata(
                    gen3RuntimeMemoryLayout = com.enrpau.dualscreendex.parser.catalog.CatalogGen3RuntimeMemoryLayout(
                        mainAddress = 0x030022C0,
                        inBattleAddress = 0x03002748,
                        inBattleMask = 2,
                        saveBlock1MapGroupOffset = 4,
                        saveBlock1MapNumberOffset = 5,
                        saveBlock1PointerAddress = 0x03005D8C,
                        saveBlock2PointerAddress = 0x03005D90,
                        saveRuntimeAbi = com.enrpau.dualscreendex.parser.catalog.CatalogGen3SaveRuntimeAbi(
                            saveBlock1Size = 0x3D88,
                            saveBlock2Size = 0x0F2C,
                            textEncoding = com.enrpau.dualscreendex.parser.catalog.CatalogGen3TextEncoding.ENGLISH,
                            trainer = com.enrpau.dualscreendex.parser.catalog.CatalogGen3TrainerCardAbi(
                                0, 8, 8, 0x0A, 0x0E, 0x10, 0xAC, 0x490, 999_999,
                                listOf(com.enrpau.dualscreendex.parser.catalog.CatalogGen3BitFlag(0x1270, 1)),
                            ),
                            bag = com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagAbi(
                                listOf(
                                    com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagPocketAbi(
                                        com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagPocket.ITEMS,
                                        0x560,
                                        30,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val context = requireNotNull(runtime.saveParseContext())
        assertEquals(25, context.speciesById.getValue(25).dexNumber)
        assertEquals(listOf(9, 31), context.speciesById.getValue(25).abilityIds)
        assertEquals(35, context.movePpById.getValue(33))
        assertEquals(com.darkaxt.dualdex.save.gen3.Gen3TextEncoding.ENGLISH, context.gen3TextEncoding)
        val saveAbi = requireNotNull(context.gen3SaveRuntimeAbi)
        assertEquals(0x3D88, saveAbi.saveBlock1Size)
        assertEquals(0x0F2C, saveAbi.saveBlock2Size)
        assertEquals(0xAC, saveAbi.trainer.encryptionKeyOffset)
        assertEquals(com.darkaxt.dualdex.save.BagPocket.ITEMS, saveAbi.bag.pockets.single().pocket)
        val snapshot = SaveSnapshot(
            romIdentity = hash,
            saveIdentity = "save",
            saveGeneration = 3,
            saveCounter = 2,
            currentArea = SavedArea(2, 3),
            seenDexNumbers = setOf(25),
            caughtDexNumbers = setOf(25),
            party = listOf(OwnedIndividual("party-0", 25, level = 12, ivs = List(6) { 31 }, captureBallId = 4)),
            storedIndividuals = emptyList(),
            capabilities = emptyMap(),
        )

        assertTrue(runtime.applySaveSnapshot(snapshot, SaveRamView(status = "MATCHED", sourceName = "fixture.srm")))

        val state = runtime.stateView()
        assertFalse(state.speciesState.getValue(25).caught)
        assertEquals("MATCHED", state.saveRam.status)
        assertEquals("fixture.srm", state.saveRam.sourceName)
        runtime.close()
    }

    @Test
    fun liveTrainerAndValidatedPartyOverrideSaveAndDisconnectRestoresIt() {
        val hash = "b".repeat(64)
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog(
                hash,
                EngineFamily.EMERALD,
                Platform.GBA,
                speciesById = mapOf(25 to saveSpecies(25), 277 to saveSpecies(277)),
            ),
        )
        val savedTrainer = trainer("SAVE", money = 100)
        val liveTrainer = trainer("LIVE", money = 999)
        val saved = SaveSnapshot(
            romIdentity = hash,
            saveIdentity = "save",
            saveGeneration = 3,
            saveCounter = 1,
            currentArea = SavedArea(0, 0),
            seenDexNumbers = setOf(25),
            caughtDexNumbers = setOf(25),
            party = listOf(OwnedIndividual("party-0", 25, level = 9)),
            storedIndividuals = emptyList(),
            capabilities = emptyMap(),
            trainer = savedTrainer,
        )
        assertTrue(runtime.applySaveSnapshot(saved, SaveRamView(status = "MATCHED")))

        runtime.updateLiveGameState(
            liveSnapshot(
                hash,
                LiveValue.Available(liveTrainer),
                LiveValue.Available(listOf(OwnedIndividual("party-0", 277, level = 5))),
            ),
        )
        assertEquals("LIVE", runtime.stateView().trainer?.name)
        assertEquals(listOf(277), runtime.gateway.bootstrap().party.map { it.speciesId })
        assertTrue(runtime.stateView().speciesState.getValue(277).team)

        runtime.updateLiveGameState(
            liveSnapshot(hash, unavailableValue("trainer bytes invalid"), LiveValue.Available(emptyList())),
        )
        assertEquals("SAVE", runtime.stateView().trainer?.name)
        assertTrue(runtime.gateway.bootstrap().party.isEmpty())
        assertTrue(runtime.gateway.bootstrap().ledger.teamSpecies.isEmpty())

        runtime.updateLiveGameState(null)
        assertEquals("SAVE", runtime.stateView().trainer?.name)
        assertEquals(listOf(25), runtime.gateway.bootstrap().party.map { it.speciesId })
        assertTrue(runtime.stateView().speciesState.getValue(25).team)
        runtime.close()
    }

    @Test
    fun unifiedPlayerFieldsPopulateApiIndependentlyWithoutSaveRam() {
        val hash = "9".repeat(64)
        val source = com.darkaxt.dualdex.live.UnifiedGameStateDecoder()
        val runtime = ProductionCompanionRuntime(transientGameState = source)
        val catalog = ParsedCatalog(
            hash,
            EngineFamily.EMERALD,
            Platform.GBA,
            speciesById = mapOf(
                84 to saveSpecies(84).copy(dexNumber = CatalogField.available(25)),
                300 to saveSpecies(300).copy(dexNumber = CatalogField.available(277)),
            ),
        )
        runtime.loadCatalog("live-only.gba", catalog)
        source.beginSession(
            com.darkaxt.dualdex.live.TransientGameStateContext(
                romIdentity = hash,
                generation = 3,
                catalog = com.darkaxt.dualdex.battle.BattleCatalogView(emptyMap(), emptyMap(), emptySet()),
                saveParseContext = requireNotNull(runtime.saveParseContext()),
            ),
        )
        val unavailable = com.darkaxt.dualdex.battle.LiveValue.Unavailable(
            com.darkaxt.dualdex.battle.LiveUnavailableReason(
                com.darkaxt.dualdex.battle.LiveUnavailableCode.MISSING_REGION,
                "money bytes missing in fixture",
            ),
        )
        source.acceptDecodedLive(
            com.darkaxt.dualdex.battle.LiveGameSnapshot(
                romIdentity = hash,
                generation = 3,
                sampleId = 1,
                trainer = com.darkaxt.dualdex.battle.LiveTrainerState(
                    identity = com.darkaxt.dualdex.battle.LiveValue.Available(TrainerIdentity("MAY", 1)),
                    publicTrainerId = com.darkaxt.dualdex.battle.LiveValue.Available(54_321),
                    money = unavailable,
                    playTime = com.darkaxt.dualdex.battle.LiveValue.Available(
                        com.darkaxt.dualdex.save.TrainerPlayTime(3, 21),
                    ),
                    badgeFlags = com.darkaxt.dualdex.battle.LiveValue.Available(3),
                    stars = unavailable,
                ),
                pokedex = com.darkaxt.dualdex.battle.LivePokedexState(
                    seenDexNumbers = com.darkaxt.dualdex.battle.LiveValue.Available(setOf(25, 277)),
                    caughtDexNumbers = com.darkaxt.dualdex.battle.LiveValue.Available(setOf(25)),
                ),
                party = com.darkaxt.dualdex.battle.LiveValue.Available(emptyList()),
                battle = unavailable,
                location = com.darkaxt.dualdex.battle.LiveLocationState(unavailable, unavailable),
                clock = unavailable,
                bag = emptyMap(),
                eventFlags = unavailable,
            ),
        )
        val state = runtime.stateView()
        assertEquals("MAY", state.trainer?.name)
        assertEquals(54_321, state.trainer?.publicTrainerId)
        assertNull(state.trainer?.money)
        assertEquals(3, state.trainer?.playTimeHours)
        assertEquals(2, state.trainer?.dexSeen)
        assertEquals(1, state.trainer?.dexCaught)
        assertTrue(state.speciesState.getValue(84).seen)
        assertTrue(state.speciesState.getValue(84).caught)
        assertTrue(state.speciesState.getValue(300).seen)
        assertFalse(state.speciesState.getValue(300).caught)
        assertFalse(state.speciesState.containsKey(25))
        runtime.close()
    }

    @Test
    fun livePokedexReplacesPreviouslyExposedRecoveryStateForEveryConsumer() {
        val hash = "6".repeat(64)
        val source = com.darkaxt.dualdex.live.UnifiedGameStateDecoder()
        val runtime = ProductionCompanionRuntime(transientGameState = source)
        val species = (1..52).associateWith { speciesId -> saveSpecies(speciesId) } +
            (101..151).associateWith { speciesId ->
                saveSpecies(speciesId).copy(
                    formId = 1,
                    dexNumber = CatalogField.available(1),
                )
            }
        runtime.loadCatalog(
            "replacement.gba",
            ParsedCatalog(
                hash,
                EngineFamily.EMERALD,
                Platform.GBA,
                speciesById = species,
            ),
        )
        source.beginSession(
            com.darkaxt.dualdex.live.TransientGameStateContext(
                romIdentity = hash,
                generation = 3,
                catalog = com.darkaxt.dualdex.battle.BattleCatalogView(emptyMap(), emptyMap(), emptySet()),
                saveParseContext = requireNotNull(runtime.saveParseContext()),
            ),
        )
        source.acceptRecovery(
            com.darkaxt.dualdex.live.RecoveryProjection(
                snapshot = emptySave(hash, "stale-recovery").copy(
                    seenDexNumbers = (1..52).toSet(),
                    caughtDexNumbers = (1..52).toSet(),
                    trainer = trainer("RECOVERY", money = 0),
                ),
                saveRam = SaveRamView(status = "MATCHED"),
            ),
        )
        assertEquals(0, runtime.stateView().speciesState.values.count { it.caught })

        val unavailable = com.darkaxt.dualdex.battle.LiveValue.Unavailable(
            com.darkaxt.dualdex.battle.LiveUnavailableReason(
                com.darkaxt.dualdex.battle.LiveUnavailableCode.MISSING_REGION,
                "not relevant to the Pokedex replacement fixture",
            ),
        )
        val liveState = com.darkaxt.dualdex.battle.LiveGameSnapshot(
                romIdentity = hash,
                generation = 3,
                sampleId = 1,
                trainer = com.darkaxt.dualdex.battle.LiveTrainerState(
                    identity = unavailable,
                    publicTrainerId = unavailable,
                    money = unavailable,
                    playTime = unavailable,
                    badgeFlags = unavailable,
                    stars = unavailable,
                ),
                pokedex = com.darkaxt.dualdex.battle.LivePokedexState(
                    seenDexNumbers = com.darkaxt.dualdex.battle.LiveValue.Available(setOf(1, 2)),
                    caughtDexNumbers = com.darkaxt.dualdex.battle.LiveValue.Available(setOf(1)),
                ),
                party = unavailable,
                battle = unavailable,
                location = com.darkaxt.dualdex.battle.LiveLocationState(unavailable, unavailable),
                clock = unavailable,
                bag = emptyMap(),
                eventFlags = unavailable,
            )
        source.acceptDecodedLive(liveState)

        val state = runtime.stateView()
        assertEquals(2, state.trainer?.dexSeen)
        assertEquals(1, state.trainer?.dexCaught)
        assertEquals(setOf(1, 2), state.speciesState.filterValues { it.seen }.keys)
        assertEquals(setOf(1), state.speciesState.filterValues { it.caught }.keys)

        source.acceptDecodedLive(
            liveState.copy(
                sampleId = 2,
                pokedex = com.darkaxt.dualdex.battle.LivePokedexState(
                    seenDexNumbers = com.darkaxt.dualdex.battle.LiveValue.Available(emptySet()),
                    caughtDexNumbers = com.darkaxt.dualdex.battle.LiveValue.Available(emptySet()),
                ),
            ),
        )
        assertEquals(0, runtime.stateView().trainer?.dexSeen)
        assertEquals(0, runtime.stateView().trainer?.dexCaught)
        assertTrue(runtime.stateView().speciesState.values.none { it.seen || it.caught })

        source.suspendLive()
        assertEquals(52, runtime.stateView().trainer?.dexSeen)
        assertEquals(52, runtime.stateView().trainer?.dexCaught)
        assertEquals(52, runtime.stateView().speciesState.values.count { it.caught })
        runtime.close()
    }

    @Test
    fun unifiedPartyUsesLiveThenRecoveryAndValidZeroClearsEveryConsumer() {
        val hash = "7".repeat(64)
        val source = com.darkaxt.dualdex.live.UnifiedGameStateDecoder()
        val runtime = ProductionCompanionRuntime(transientGameState = source)
        runtime.loadCatalog(
            "party.gba",
            ParsedCatalog(
                hash,
                EngineFamily.EMERALD,
                Platform.GBA,
                speciesById = mapOf(25 to saveSpecies(25), 277 to saveSpecies(277), 300 to saveSpecies(300)),
            ),
        )
        source.beginSession(
            com.darkaxt.dualdex.live.TransientGameStateContext(
                romIdentity = hash,
                generation = 3,
                catalog = com.darkaxt.dualdex.battle.BattleCatalogView(emptyMap(), emptyMap(), emptySet()),
            ),
        )
        source.acceptRecovery(
            com.darkaxt.dualdex.live.RecoveryProjection(
                snapshot = emptySave(hash, "save-party").copy(
                    party = listOf(OwnedIndividual("party-0", 25, level = 9, ivs = List(6) { 18 })),
                    storedIndividuals = listOf(OwnedIndividual("box-0", 300, level = 7, ivs = List(6) { 12 })),
                ),
                saveRam = SaveRamView(status = "MATCHED"),
            ),
        )
        val unavailable = com.darkaxt.dualdex.battle.LiveValue.Unavailable(
            com.darkaxt.dualdex.battle.LiveUnavailableReason(
                com.darkaxt.dualdex.battle.LiveUnavailableCode.INVALID_VALUE,
                "live Party validation failed",
            ),
        )
        val liveTrainer = com.darkaxt.dualdex.battle.LiveTrainerState(
            identity = com.darkaxt.dualdex.battle.LiveValue.Available(TrainerIdentity("MAY", 1)),
            publicTrainerId = com.darkaxt.dualdex.battle.LiveValue.Available(12_345),
            money = unavailable,
            playTime = unavailable,
            badgeFlags = com.darkaxt.dualdex.battle.LiveValue.Available(1),
            stars = unavailable,
        )
        val battle = unifiedBattleSnapshot(hash, 1, true, null).battle
        source.acceptDecodedLive(
            unifiedBattleSnapshot(hash, 1, true, null).copy(
                trainer = liveTrainer,
                party = unavailable,
                battle = battle,
            ),
        )

        assertEquals(listOf(25), runtime.gateway.bootstrap().party.map { it.speciesId })
        assertEquals(12_345, runtime.stateView().trainer?.publicTrainerId)
        assertTrue(runtime.stateView().speciesState.getValue(300).caught)
        assertFalse(runtime.stateView().speciesState.getValue(300).team)
        assertTrue(runtime.gateway.bootstrap().ledger.owned.isEmpty())

        source.acceptDecodedLive(
            unifiedBattleSnapshot(hash, 2, true, null).copy(
                trainer = liveTrainer,
                party = com.darkaxt.dualdex.battle.LiveValue.Available(
                    listOf(
                        OwnedIndividual("party-0", 277, level = 5, ivs = List(6) { 24 }),
                        OwnedIndividual("party-1", 25, level = 9, ivs = List(6) { 18 }),
                    ),
                ),
            ),
        )
        assertEquals(listOf(277, 25), runtime.gateway.bootstrap().party.map { it.speciesId })
        assertEquals(setOf(277, 25), runtime.stateView().speciesState.filterValues { it.team }.keys)
        assertTrue(runtime.gateway.bootstrap().ledger.teamSpecies.isEmpty())
        assertEquals(277, runtime.stateView().party.first().speciesId)
        assertTrue(runtime.stateView().party.first().rarity != null)

        source.acceptDecodedLive(
            unifiedBattleSnapshot(hash, 3, false, null).copy(
                trainer = liveTrainer,
                party = com.darkaxt.dualdex.battle.LiveValue.Available(emptyList()),
            ),
        )
        assertTrue(runtime.gateway.bootstrap().party.isEmpty())
        assertTrue(runtime.gateway.bootstrap().ledger.teamSpecies.isEmpty())
        assertTrue(runtime.stateView().speciesState.values.none { it.team })
        assertTrue(runtime.stateView().trainerCardUnlocked)
        runtime.close()
    }

    @Test
    fun endingUnifiedSessionClearsPartyAndCurrentTeamProjection() {
        val hash = "3".repeat(64)
        val source = com.darkaxt.dualdex.live.UnifiedGameStateDecoder()
        val runtime = ProductionCompanionRuntime(transientGameState = source)
        runtime.loadCatalog(
            "session-clear.gba",
            ParsedCatalog(
                hash,
                EngineFamily.EMERALD,
                Platform.GBA,
                speciesById = mapOf(25 to saveSpecies(25)),
            ),
        )
        source.beginSession(
            com.darkaxt.dualdex.live.TransientGameStateContext(
                romIdentity = hash,
                generation = 3,
                catalog = com.darkaxt.dualdex.battle.BattleCatalogView(emptyMap(), emptyMap(), emptySet()),
            ),
        )
        source.acceptDecodedLive(
            unifiedBattleSnapshot(hash, 1, false, null).copy(
                party = com.darkaxt.dualdex.battle.LiveValue.Available(
                    listOf(OwnedIndividual("party-0", 25, level = 5)),
                ),
            ),
        )
        assertEquals(listOf(25), runtime.gateway.bootstrap().party.map { it.speciesId })
        assertTrue(runtime.gateway.bootstrap().ledger.teamSpecies.isEmpty())
        assertEquals(setOf(25), runtime.stateView().speciesState.filterValues { it.team }.keys)

        source.endSession()

        assertTrue(runtime.gateway.bootstrap().party.isEmpty())
        assertTrue(runtime.gateway.bootstrap().ledger.teamSpecies.isEmpty())
        runtime.close()
    }

    @Test
    fun unifiedRecoveryRejectsMirroredPokedexFieldsFromCheckpointButKeepsTransientPreferences() {
        val hash = "6".repeat(64)
        lateinit var runtime: ProductionCompanionRuntime
        val source = com.darkaxt.dualdex.live.UnifiedGameStateDecoder { runtime.gateway.bootstrap().ledger }
        runtime = ProductionCompanionRuntime(transientGameState = source)
        runtime.loadCatalog(
            "recovery.gba",
            ParsedCatalog(
                hash,
                EngineFamily.EMERALD,
                Platform.GBA,
                speciesById = (1..52).associateWith(::saveSpecies),
            ),
        )
        source.beginSession(
            com.darkaxt.dualdex.live.TransientGameStateContext(
                romIdentity = hash,
                generation = 3,
                catalog = com.darkaxt.dualdex.battle.BattleCatalogView(emptyMap(), emptyMap(), emptySet()),
                saveParseContext = requireNotNull(runtime.saveParseContext()),
            ),
        )
        val checkpoint = KnowledgeLedger(
            seenSpecies = (1..52).toSet(),
            caughtSpecies = (1..52).toSet(),
            localMapPoiPreferences = com.enrpau.dualscreendex.companion.model.LocalMapPoiPreferences(showPlaces = false),
        )

        source.acceptRecovery(
            com.darkaxt.dualdex.live.RecoveryProjection(
                snapshot = emptySave(hash, "save-recovery"),
                saveRam = SaveRamView(status = "MATCHED", autosaveStatus = "ON"),
                observation = saveObservation(SaveObservationKind.INITIAL, "save", 1),
                checkpointLedger = checkpoint,
            ),
        )

        val state = runtime.stateView()
        assertEquals(0, state.speciesState.values.count { it.seen })
        assertEquals(0, state.speciesState.values.count { it.caught })
        assertFalse(state.localMapPoiPreferences.showPlaces)
        assertEquals("MATCHED", state.saveRam.status)
        assertEquals("ON", state.saveRam.autosaveStatus)
        runtime.close()
    }

    @Test
    fun ignoresAnotherRomsLiveSnapshotAndDropsPriorLiveStateWhenCatalogSwitches() {
        val first = "c".repeat(64)
        val second = "d".repeat(64)
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog(
            "first.gba",
            ParsedCatalog(first, EngineFamily.EMERALD, Platform.GBA, speciesById = mapOf(25 to saveSpecies(25))),
        )
        runtime.updateLiveGameState(
            liveSnapshot(
                first,
                LiveValue.Available(trainer("FIRST", 1)),
                LiveValue.Available(listOf(OwnedIndividual("party-0", 25, level = 5))),
            ),
        )
        runtime.updateLiveGameState(
            liveSnapshot(
                second,
                LiveValue.Available(trainer("WRONG", 2)),
                LiveValue.Available(emptyList()),
            ),
        )
        assertEquals("FIRST", runtime.stateView().trainer?.name)

        runtime.loadCatalog(
            "second.gba",
            ParsedCatalog(second, EngineFamily.EMERALD, Platform.GBA, speciesById = mapOf(25 to saveSpecies(25))),
        )
        assertNull(runtime.stateView().trainer)
        assertTrue(runtime.gateway.bootstrap().party.isEmpty())
        runtime.close()
    }

    @Test
    fun publishesMinimalLiveTrainerIdentityWithoutInventingATrainerCard() {
        val hash = "e".repeat(64)
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog("identity.gba", ParsedCatalog(hash, EngineFamily.EMERALD, Platform.GBA))

        runtime.updateLiveGameState(
            liveSnapshot(
                hash,
                unavailableValue("Trainer Card unavailable"),
                LiveValue.Available(emptyList()),
                trainerIdentity = LiveValue.Available(TrainerIdentity("MAY", 1)),
            ),
        )

        assertNull(runtime.gateway.bootstrap().trainerCardState?.publicTrainerId)
        assertEquals(TrainerIdentity("MAY", 1), runtime.gateway.bootstrap().trainerCardState?.identity)
        runtime.close()
    }

    private fun unifiedBattleSnapshot(
        romIdentity: String,
        sampleId: Long,
        active: Boolean,
        sample: BattleMemorySample?,
    ): com.darkaxt.dualdex.battle.LiveGameSnapshot {
        val unavailable = com.darkaxt.dualdex.battle.LiveValue.Unavailable(
            com.darkaxt.dualdex.battle.LiveUnavailableReason(
                com.darkaxt.dualdex.battle.LiveUnavailableCode.MISSING_REGION,
                "fixture region omitted",
            ),
        )
        return com.darkaxt.dualdex.battle.LiveGameSnapshot(
            romIdentity = romIdentity,
            generation = 3,
            sampleId = sampleId,
            trainer = com.darkaxt.dualdex.battle.LiveTrainerState(
                unavailable,
                unavailable,
                unavailable,
                unavailable,
                unavailable,
                unavailable,
            ),
            pokedex = com.darkaxt.dualdex.battle.LivePokedexState(unavailable, unavailable),
            party = unavailable,
            battle = com.darkaxt.dualdex.battle.LiveValue.Available(
                com.darkaxt.dualdex.battle.LiveBattleState(
                    active = active,
                    sample = sample,
                    encounterKind = sample?.encounterKind ?: BattleEncounterKind.UNKNOWN,
                ),
            ),
            location = com.darkaxt.dualdex.battle.LiveLocationState(unavailable, unavailable),
            clock = unavailable,
            bag = emptyMap(),
            eventFlags = unavailable,
        )
    }

    private fun trainer(name: String, money: Long) = TrainerSnapshot(
        name = name,
        gender = 0,
        publicTrainerId = 7,
        money = money,
        playTimeHours = 2,
        playTimeMinutes = 3,
        badgeFlags = 1,
        dexSeen = 1,
        dexCaught = 1,
    )

    private fun liveSnapshot(
        romIdentity: String,
        trainer: LiveValue<TrainerSnapshot>,
        party: LiveValue<List<OwnedIndividual>>,
        clock: LiveValue<LiveClockState> = unavailableValue("clock omitted"),
        eventFlags: LiveValue<Set<Int>> = unavailableValue("event flags omitted"),
        trainerIdentity: LiveValue<TrainerIdentity> = unavailableValue("identity omitted"),
        location: LiveValue<Int> = unavailableValue("location omitted"),
    ): LiveGameSnapshot {
        val trainerSnapshot = (trainer as? LiveValue.Available)?.value
        val identity = (trainerIdentity as? LiveValue.Available)?.value
            ?: trainerSnapshot?.let { TrainerIdentity(it.name, it.gender) }
        val unavailable = unavailableValue<Nothing>("fixture field omitted")
        return LiveGameSnapshot(
        romIdentity = romIdentity,
        generation = 3,
        sampleId = System.nanoTime() and Long.MAX_VALUE,
        trainer = com.darkaxt.dualdex.battle.LiveTrainerState(
            identity = identity?.let { LiveValue.Available(it) } ?: unavailable,
            publicTrainerId = trainerSnapshot?.publicTrainerId?.let { LiveValue.Available(it) } ?: unavailable,
            money = trainerSnapshot?.money?.let { LiveValue.Available(it) } ?: unavailable,
            playTime = trainerSnapshot?.let {
                LiveValue.Available(com.darkaxt.dualdex.save.TrainerPlayTime(it.playTimeHours, it.playTimeMinutes))
            } ?: unavailable,
            badgeFlags = trainerSnapshot?.badgeFlags?.let { LiveValue.Available(it) } ?: unavailable,
            stars = trainerSnapshot?.stars?.let { LiveValue.Available(it) } ?: unavailable,
        ),
        pokedex = com.darkaxt.dualdex.battle.LivePokedexState(
            seenDexNumbers = trainerSnapshot?.let { LiveValue.Available((1..it.dexSeen).toSet()) } ?: unavailable,
            caughtDexNumbers = trainerSnapshot?.let { LiveValue.Available((1..it.dexCaught).toSet()) } ?: unavailable,
        ),
        party = party,
        bag = com.darkaxt.dualdex.save.BagPocket.entries.associateWith { unavailable },
        battle = LiveValue.Available(
            com.darkaxt.dualdex.battle.LiveBattleState(
                false,
                null,
                com.darkaxt.dualdex.battle.BattleEncounterKind.UNKNOWN,
            ),
        ),
        location = com.darkaxt.dualdex.battle.LiveLocationState(location, unavailable),
        clock = clock,
        eventFlags = eventFlags,
    )
    }

    private fun <T> unavailableValue(detail: String): LiveValue<T> = LiveValue.Unavailable(
        com.darkaxt.dualdex.battle.LiveUnavailableReason(
            com.darkaxt.dualdex.battle.LiveUnavailableCode.MISSING_REGION,
            detail,
        ),
    )

    private fun saveSpecies(id: Int) = SpeciesRecord(
        id = id,
        dexNumber = CatalogField.available(id),
        name = CatalogField.available("Species $id"),
        typeIds = CatalogField.available(emptyList()),
        baseStats = CatalogField.notFound("fixture"),
        sprite = CatalogField.notFound("fixture"),
        growthRate = CatalogField.available(0),
    )

    private class FakeCatalogRepository(private val stored: StoredCatalog) : CatalogRepository {
        var writeCalls = 0

        override fun write(
            catalog: ParsedCatalog,
            source: CatalogSourceMetadata,
            progress: CatalogWriteProgress,
        ) {
            writeCalls++
        }

        override fun readComplete(sha256: String): StoredCatalog? = stored.takeIf { it.catalog.romSha256 == sha256 }

        override fun findCompleted(crc32: String, romSize: Int, romTitle: String?): List<StoredCatalog> = emptyList()
    }

    private class RecordingCatalogRepository(
        private val decision: CatalogCacheDecision = CatalogCacheDecision.MISS_FILE_ABSENT,
    ) : CatalogRepository {
        var writeCalls = 0

        override fun write(
            catalog: ParsedCatalog,
            source: CatalogSourceMetadata,
            progress: CatalogWriteProgress,
        ) {
            writeCalls++
        }

        override fun readComplete(sha256: String): StoredCatalog? = null

        override fun lookupComplete(sha256: String): CatalogCacheLookup = CatalogCacheLookup(null, decision)

        override fun findCompleted(crc32: String, romSize: Int, romTitle: String?): List<StoredCatalog> = emptyList()
    }

    private class ImmediateExecutorService : AbstractExecutorService() {
        private var closed = false

        override fun execute(command: Runnable) = command.run()

        override fun shutdown() {
            closed = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            closed = true
            return Collections.emptyList()
        }

        override fun isShutdown(): Boolean = closed

        override fun isTerminated(): Boolean = closed

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = closed
    }

    private fun recordingPerformance(events: MutableList<PerformanceEvent>) = PerformanceRecorder(
        monotonicNanos = { 0L },
        wallClockMillis = { 0L },
        sessionIdFactory = { "runtime-session" },
        sampler = PerformanceMetricSampler { PerformanceMetrics() },
        sinks = listOf(PerformanceEventSink(events::add)),
    )

    private class HoldingExecutorService : AbstractExecutorService() {
        private var closed = false
        private val pending = mutableListOf<Runnable>()
        val pendingCount: Int get() = pending.size

        override fun execute(command: Runnable) {
            pending += command
        }

        fun runNext() {
            pending.removeAt(0).run()
        }

        override fun shutdown() {
            closed = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            closed = true
            return pending.toMutableList().also { pending.clear() }
        }

        override fun isShutdown(): Boolean = closed

        override fun isTerminated(): Boolean = closed

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = closed
    }

    private fun levelUpRulesetCatalog(hash: String, selectorOffset: Int = 0x3DA6) = ParsedCatalog(
        hash,
        EngineFamily.EMERALD,
        Platform.GBA,
        learnsetRulesets = listOf(
            LearnsetRuleset(
                "original", "Original", 1, 1.0, emptyMap(),
                levelUpSelector = LevelUpRulesetSelector(selectorOffset, 0x02, 0x00),
            ),
            LearnsetRuleset(
                "modern", "Modern", 2, 1.0, emptyMap(),
                levelUpSelector = LevelUpRulesetSelector(selectorOffset, 0x02, 0x02),
            ),
        ),
    )

    private fun levelUpSnapshot(
        hash: String,
        detectedId: String,
        fingerprint: String?,
        counter: Long = 1,
    ) = SaveSnapshot(
        romIdentity = hash,
        saveIdentity = "save-$counter",
        saveGeneration = 3,
        saveCounter = counter,
        currentArea = null,
        seenDexNumbers = emptySet(),
        caughtDexNumbers = emptySet(),
        party = emptyList(),
        storedIndividuals = emptyList(),
        capabilities = emptyMap(),
        detectedLevelUpRulesetId = detectedId,
        levelUpRulesetDetectionResolved = true,
        levelUpRulesetDetectionFingerprint = fingerprint,
    )

    private fun emptySave(romIdentity: String, saveIdentity: String) = SaveSnapshot(
        romIdentity = romIdentity,
        saveIdentity = saveIdentity,
        saveGeneration = 3,
        saveCounter = 1,
        currentArea = null,
        seenDexNumbers = emptySet(),
        caughtDexNumbers = emptySet(),
        party = emptyList(),
        storedIndividuals = emptyList(),
        capabilities = emptyMap(),
    )

    private fun battleReadyCatalog(identity: String) = ParsedCatalog(
        identity,
        EngineFamily.EMERALD,
        Platform.GBA,
        speciesById = mapOf(
            1 to SpeciesRecord(
                id = 1,
                dexNumber = CatalogField.available(1),
                name = CatalogField.available("BULBASAUR"),
                typeIds = CatalogField.available(listOf(12)),
                baseStats = CatalogField.notFound("fixture"),
                sprite = CatalogField.notFound("fixture"),
                abilityIds = CatalogField.available(listOf(65)),
            ),
        ),
        movesById = mapOf(
            1 to MoveRecord(
                1,
                CatalogField.available("POUND"),
                CatalogField.available(0),
                CatalogField.notFound("fixture"),
                CatalogField.available(40),
                CatalogField.available(100),
                CatalogField.available(35),
            ),
        ),
        typesById = mapOf(0 to TypeRecord(0, CatalogField.available("NORMAL"))),
    )

    private fun saveObservation(kind: SaveObservationKind, sourceId: String, version: Int) = SaveObservation(
        kind = kind,
        source = SaveDocumentSource(
            id = sourceId,
            displayPath = "$sourceId.srm",
            name = "$sourceId.srm",
            size = 1,
            lastModifiedEpochMs = version.toLong(),
            read = { byteArrayOf(version.toByte()) },
        ),
        fingerprint = SaveFileFingerprint(
            sha256 = version.toString(16).padStart(64, '0'),
            size = 1,
            lastModifiedEpochMs = version.toLong(),
        ),
    )
}
