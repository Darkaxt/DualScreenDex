package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogCacheDecision
import com.darkaxt.dualdex.catalog.CatalogRepository
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.darkaxt.dualdex.catalog.catalogWriteProgress
import com.darkaxt.dualdex.knowledge.KnowledgeLedgerSanitizer
import com.darkaxt.dualdex.knowledge.discoverableAreaBaseIds
import com.darkaxt.dualdex.battle.BattleCatalogContext
import com.darkaxt.dualdex.battle.BattleMemorySample
import com.darkaxt.dualdex.battle.liveAreaMemoryLayout
import com.darkaxt.dualdex.battle.BattleCatalogView
import com.darkaxt.dualdex.battle.BattleMove
import com.darkaxt.dualdex.battle.BattleSpecies
import com.darkaxt.dualdex.battle.BattleTrackingUpdate
import com.darkaxt.dualdex.battle.RuntimeMapPosition
import com.darkaxt.dualdex.battle.Gen3BattleUiMemoryLayout
import com.darkaxt.dualdex.battle.Gen3RuntimeMemoryLayout
import com.darkaxt.dualdex.battle.Gen3LiveGameSnapshot
import com.darkaxt.dualdex.battle.Gen3LiveSectionState
import com.darkaxt.dualdex.battle.TargetMode
import com.enrpau.dualscreendex.companion.CompanionGateway
import com.enrpau.dualscreendex.companion.api.ApiViewBuilder
import com.enrpau.dualscreendex.companion.api.BootstrapView
import com.enrpau.dualscreendex.companion.api.DiagnosticView
import com.enrpau.dualscreendex.companion.api.RetroArchView
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.companion.api.StateView
import com.enrpau.dualscreendex.companion.model.AppScreen
import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.GameClock
import com.enrpau.dualscreendex.companion.model.GameClockPhase
import com.enrpau.dualscreendex.companion.model.projectGameClock
import com.enrpau.dualscreendex.companion.model.CatalogLoadingState
import com.enrpau.dualscreendex.companion.model.BattleState
import com.enrpau.dualscreendex.companion.model.BattleEncounterKind as CompanionBattleEncounterKind
import com.enrpau.dualscreendex.companion.battle.RarityEvaluator
import com.enrpau.dualscreendex.companion.model.BattleTab
import com.enrpau.dualscreendex.companion.model.BattleTargetMode
import com.enrpau.dualscreendex.companion.model.CompanionAction
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.Density
import com.enrpau.dualscreendex.companion.model.DisplayMode
import com.enrpau.dualscreendex.companion.model.DisplayTarget
import com.enrpau.dualscreendex.companion.model.Effectiveness
import com.enrpau.dualscreendex.companion.model.LiveMapPosition
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.knowledge.LocalMapPoiKnowledgeMapper
import com.enrpau.dualscreendex.companion.model.MatchupKey
import com.enrpau.dualscreendex.companion.model.MoveObservation
import com.enrpau.dualscreendex.companion.model.OpponentState
import com.enrpau.dualscreendex.companion.model.PokedexFilter
import com.enrpau.dualscreendex.companion.model.Theme
import com.enrpau.dualscreendex.companion.model.TrainerCardState
import com.enrpau.dualscreendex.companion.knowledge.SaveKnowledgeMapper
import com.enrpau.dualscreendex.companion.knowledge.LivePartyKnowledgeMapper
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveByteSelector
import com.darkaxt.dualdex.save.LevelUpRulesetDetectionFingerprint
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.live.ResolvedGameSnapshot
import com.darkaxt.dualdex.live.TransientGameStateSource
import com.darkaxt.dualdex.live.gameAccessReady
import com.darkaxt.dualdex.save.SaveObservation
import com.darkaxt.dualdex.save.SaveObservationKind
import com.darkaxt.dualdex.save.SaveSpeciesContext
import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.TrainerIdentity
import com.darkaxt.dualdex.save.TrainerSnapshot
import com.darkaxt.dualdex.save.BagPocket
import com.darkaxt.dualdex.save.gen3.Gen3BagAbi
import com.darkaxt.dualdex.save.gen3.Gen3BagDataSource
import com.darkaxt.dualdex.save.gen3.Gen3BagPocketAbi
import com.darkaxt.dualdex.save.gen3.Gen3BitFlag
import com.darkaxt.dualdex.save.gen3.Gen3SaveRuntimeAbi
import com.darkaxt.dualdex.save.gen3.Gen3EventFlagAbi
import com.darkaxt.dualdex.save.gen3.Gen3TrainerCardAbi
import com.darkaxt.dualdex.save.gen3.Gen3TextEncoding
import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializationProgress
import com.enrpau.dualscreendex.parser.catalog.CatalogWorkModule
import com.enrpau.dualscreendex.parser.catalog.CatalogWorkProgress
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.LocalMapAssetRenderer
import com.enrpau.dualscreendex.parser.catalog.MapLighting
import com.enrpau.dualscreendex.parser.catalog.MapTimeOfDay
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.RenderedMapAsset
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.LoadedRom
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

data class SaveKnowledgeApplication(
    val accepted: Boolean,
    val checkpointLedger: KnowledgeLedger? = null,
)

/** Production ROM catalog runtime. It deliberately has no simulator dependency or battle generator. */
class ProductionCompanionRuntime(
    private val parserWorker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "dualdex-parser").apply { isDaemon = true }
    },
    private val catalogRepository: CatalogRepository? = null,
    private val onCatalogCommitted: (sha256: String, displayName: String) -> Unit = { _, _ -> },
    initialSettings: CompanionSettings = CompanionSettings(),
    private val onSettingsChanged: (CompanionSettings) -> Unit = {},
    private val settingsForRom: ((String) -> CompanionSettings)? = null,
    private val globalSettings: (() -> CompanionSettings)? = null,
    private val onRomSettingsChanged: (String?, CompanionSettings) -> Unit = { _, settings -> onSettingsChanged(settings) },
    private val onRomDisplayModeChanged: (DisplayMode) -> Unit = {},
    private val onCatalogCleared: () -> Unit = {},
    private val parseCatalog: (
        RomImage,
        (CatalogMaterializationProgress) -> Unit,
        (CatalogWorkProgress) -> Unit,
    ) -> ParsedCatalog? = { rom, progress, work ->
        CatalogParser.parseWithWork(rom, progress, work).catalog
    },
    private val mapAssetRenderer: (
        ParsedCatalog,
        String,
        MapLighting,
        MapTimeOfDay?,
    ) -> RenderedMapAsset? = { current, key, requestedLighting, time ->
        LocalMapAssetRenderer.render(current.localMaps, key, requestedLighting, time)
            ?: current.worldMaps.assets[key]?.let { RenderedMapAsset(PngEncoder.encode(it), null) }
    },
    private val mapAssetRenderCache: MapAssetRenderCache = MapAssetRenderCache(),
    private val transientGameState: TransientGameStateSource? = null,
) : AutoCloseable {
    private var catalog: ParsedCatalog? = null
    @Volatile private var settingsRomSha256: String? = null
    @Volatile private var settingsWritesEnabled = true
    @Volatile private var retroArch = RetroArchView()
    @Volatile private var saveRam = SaveRamView()
    @Volatile private var catalogLoadingMessage: String? = null
    private var detectedLevelUpRulesetId: String? = null
    private var levelUpRulesetDetectionResolved = false
    private var liveGameState: Gen3LiveGameSnapshot? = null
    private var savedPlayerState: SaveSnapshot? = null
    private var activePlaythrough: ActivePlaythrough? = null
    private var catalogPublicationInProgress = false
    private var cachedState: CachedState? = null
    private var cachedSaveParseContext: CachedSaveParseContext? = null
    private var cachedBattleCatalogContext: CachedBattleCatalogContext? = null
    private val loadGeneration = AtomicLong()
    val gateway = CompanionGateway(
        AppSnapshot(
            settings = initialSettings,
        ),
    )
    @Volatile private var resolvedGameState: ResolvedGameSnapshot? = null
    private val transientGameStateSubscription = transientGameState?.subscribe { snapshot ->
        applyResolvedGameState(snapshot)
    }

    @Synchronized
    private fun applyResolvedGameState(snapshot: ResolvedGameSnapshot?) {
        resolvedGameState = snapshot
        applyResolvedPlayerState(snapshot)
        applyResolvedPartyAndProgression(snapshot)
        applyResolvedOverworldState(snapshot)
        applyResolvedBattleState(snapshot)
    }

    private fun applyResolvedPlayerState(snapshot: ResolvedGameSnapshot?) {
        val matching = snapshot?.takeIf { state ->
            catalog?.romSha256.equals(state.romIdentity, ignoreCase = true)
        }
        val playTime = matching?.trainer?.playTime?.value
        val trainerCard = matching?.let { state ->
            TrainerCardState(
                identity = state.trainer.identity.value,
                publicTrainerId = state.trainer.publicTrainerId.value,
                money = state.trainer.money.value,
                playTimeHours = playTime?.hours,
                playTimeMinutes = playTime?.minutes,
                badgeFlags = state.trainer.badgeFlags.value,
                dexSeen = state.pokedex.seenDexNumbers.value?.size,
                dexCaught = state.pokedex.caughtDexNumbers.value?.size,
                stars = state.trainer.stars.value,
            )
        }
        val current = gateway.bootstrap()
        if (
            current.trainerCardState != trainerCard ||
            matching?.pokedex?.seenDexNumbers?.value != null ||
            matching?.pokedex?.caughtDexNumbers?.value != null
        ) {
            gateway.dispatch(
                CompanionAction.ResolvedPlayerStateChanged(
                    trainerCard = trainerCard,
                    seenDexNumbers = matching?.pokedex?.seenDexNumbers?.value,
                    caughtDexNumbers = matching?.pokedex?.caughtDexNumbers?.value,
                ),
            )
        }
    }

    private fun applyResolvedBattleState(snapshot: ResolvedGameSnapshot?) {
        val battle = snapshot
            ?.takeIf { state -> catalog?.romSha256.equals(state.romIdentity, ignoreCase = true) }
            ?.battle
            ?.value
        val sample = battle?.sample
        if (battle?.active == true && sample != null) {
            publishBattleSample(sample)
        } else if (gateway.bootstrap().battle != null) {
            clearLiveBattle()
        }
    }

    private fun applyResolvedPartyAndProgression(snapshot: ResolvedGameSnapshot?) {
        val currentCatalog = catalog ?: return
        val matching = snapshot?.takeIf { state ->
            currentCatalog.romSha256.equals(state.romIdentity, ignoreCase = true)
        } ?: return
        val before = gateway.bootstrap()
        val party = matching.party.value
        if (party != null && before.party != party) {
            gateway.dispatch(CompanionAction.ResolvedPartyStateChanged(party))
        }
        var ledger = party?.let { availableParty ->
            LivePartyKnowledgeMapper.merge(
                previous = gateway.bootstrap().ledger,
                catalog = currentCatalog,
                party = availableParty,
                generation = matching.generation,
            )
        } ?: gateway.bootstrap().ledger
        matching.eventFlags.value?.let { flags ->
            ledger = LocalMapPoiKnowledgeMapper.mergeEventFlags(
                previous = ledger,
                catalog = currentCatalog,
                setFlagIds = flags,
            )
        }
        if (ledger != gateway.bootstrap().ledger) {
            gateway.dispatch(CompanionAction.ReplaceLedger(ledger))
        }
    }

    private fun applyResolvedOverworldState(snapshot: ResolvedGameSnapshot?) {
        val currentCatalog = catalog
        val matching = snapshot?.takeIf { state ->
            currentCatalog?.romSha256.equals(state.romIdentity, ignoreCase = true)
        }
        val areaBaseId = matching?.location?.areaBaseId?.value
        val position = matching?.location?.position?.value?.let { value -> LiveMapPosition(value.x, value.y) }
        val clock = matching?.clock?.value
        val hours = clock?.hours
        val minutes = clock?.minutes
        val phase = clock?.phase
        val schedule = currentCatalog?.runtimeMetadata?.gen3RuntimeMemoryLayout?.liveClockSchedule
        val gameTime = when {
            hours != null && minutes != null -> projectGameClock(
                hours,
                minutes,
                schedule?.dayStartHour,
                schedule?.nightStartHour,
            )
            phase != null -> GameClock(phase = GameClockPhase.valueOf(phase.name))
            else -> null
        }
        val ready = matching?.gameAccessReady() == true
        val before = gateway.bootstrap()
        if (
            before.liveAreaBaseId != areaBaseId ||
            before.liveMapPosition != position ||
            before.gameTime != gameTime ||
            (ready && !before.gameAccessReady)
        ) {
            gateway.dispatch(
                CompanionAction.ResolvedOverworldStateChanged(
                    areaBaseId = areaBaseId,
                    position = position,
                    gameTime = gameTime,
                    gameAccessReady = ready,
                ),
            )
        }
        val validAreaBaseId = areaBaseId?.takeIf { candidate ->
            candidate in (currentCatalog?.discoverableAreaBaseIds() ?: emptySet())
        } ?: return
        val after = gateway.bootstrap()
        var ledger = after.ledger
        if (validAreaBaseId !in ledger.visitedAreaBaseIds) {
            ledger = ledger.copy(visitedAreaBaseIds = ledger.visitedAreaBaseIds + validAreaBaseId)
        }
        if (position != null && currentCatalog != null) {
            ledger = LocalMapPoiKnowledgeMapper.mergeProximity(
                previous = ledger,
                catalog = currentCatalog,
                baseAreaId = validAreaBaseId,
                tileX = position.x,
                tileY = position.y,
            )
        }
        if (ledger != gateway.bootstrap().ledger) {
            gateway.dispatch(CompanionAction.ReplaceLedger(ledger))
        }
    }

    fun load(name: String, input: InputStream): BootstrapView = load(RomSourceLoader.load(name, input))

    fun load(name: String, source: ByteArray): BootstrapView = load(RomSourceLoader.load(name, source))

    fun load(source: LoadedRom): BootstrapView {
        load(source.displayName, source.rom)
        return bootstrap()
    }

    fun load(source: LoadedRom, onComplete: (Result<Unit>) -> Unit) {
        loadInternal(source.displayName, source.rom, onComplete)
    }

    fun load(name: String, rom: RomImage) {
        loadInternal(name, rom, null)
    }

    private fun loadInternal(name: String, rom: RomImage, onComplete: ((Result<Unit>) -> Unit)?) {
        if (activeCatalogMatches(rom.sha256)) {
            notifyCompletion(onComplete, Result.success(Unit))
            return
        }
        val header = RomHeaderReader.read(rom)
        val source = CatalogSourceMetadata.fromDisplayName(name, rom.size, header.title)
        val generation = beginCatalogTransition(rom.sha256, name, "CACHE_REOPEN")
        parserWorker.execute {
            try {
                val lookup = catalogRepository?.lookupComplete(rom.sha256)
                val cached = lookup?.stored
                if (cached != null) {
                    if (generation != loadGeneration.get()) {
                        notifyCompletion(onComplete, Result.failure(IllegalStateException("catalog load was superseded")))
                        return@execute
                    }
                    publishReopened(generation, name, cached.catalog)
                    notifyCompletion(onComplete, Result.success(Unit))
                    return@execute
                }
                setCatalogLoadingMessage(
                    generation,
                    cacheRefreshMessage(lookup?.decision ?: CatalogCacheDecision.MISS_FILE_ABSENT),
                )
                publishWork(generation, CatalogWorkProgress(CatalogWorkModule.ROM_IDENTITY), source.displayName)
                val parsed = parseCatalog(
                    rom,
                    { progress -> publishCheckpoint(generation, progress, source) },
                    { work -> publishWork(generation, work, source.displayName) },
                )
                    ?: error("ROM did not produce a supported mainline-family catalog")
                if (generation != loadGeneration.get()) {
                    notifyCompletion(onComplete, Result.failure(IllegalStateException("catalog load was superseded")))
                    return@execute
                }
                publishParsed(generation, name, parsed)
                notifyCompletion(onComplete, Result.success(Unit))
            } catch (failure: Exception) {
                publishTransitionFailure(generation, "FAILED", failure.message ?: failure.javaClass.simpleName)
                notifyCompletion(onComplete, Result.failure(failure))
            }
        }
    }

    /** Test and cache-reopen seam; Stage 2 will use this for persisted catalogs. */
    @Synchronized
    fun loadCatalog(name: String, parsed: ParsedCatalog) {
        beginCatalogTransition(parsed.romSha256, name, "CACHE_REOPEN")
        applyWinningCatalogSettings(parsed.romSha256)
        catalog = parsed
        settingsWritesEnabled = true
        gateway.dispatch(CompanionAction.ReplaceLedger(KnowledgeLedger()))
        gateway.dispatch(
            CompanionAction.CatalogLoadingChanged(
                CatalogLoadingState(active = false, phase = "CACHE_REOPEN", completedUnits = 1, totalUnits = 1),
                name,
            ),
        )
        gateway.dispatch(CompanionAction.CatalogLoaded(name))
    }

    @Synchronized
    fun restoreCatalog(sha256: String): Boolean {
        val stored = catalogRepository?.readComplete(sha256)
        if (stored == null) {
            val generation = beginCatalogTransition(null, phase = "CACHE_REOPEN")
            publishTransitionFailure(generation, "IDLE")
            return false
        }
        val generation = beginCatalogTransition(stored.catalog.romSha256, stored.source.displayName, "CACHE_REOPEN")
        publishReopened(generation, stored.source.displayName, stored.catalog)
        return true
    }

    fun restoreCatalogAsync(sha256: String) {
        val generation = beginCatalogTransition(sha256, null, "CACHE_REOPEN")
        parserWorker.execute {
            val stored = catalogRepository?.readComplete(sha256)
            if (stored == null) {
                publishTransitionFailure(generation, "IDLE")
            } else {
                publishReopened(generation, stored.source.displayName, stored.catalog)
            }
        }
    }

    @Synchronized
    fun bootstrap(): BootstrapView {
        val snapshot = gateway.bootstrap()
        return BootstrapView(catalog?.let(ApiViewBuilder::catalog), stateView(snapshot))
    }

    @Synchronized
    fun stateView(snapshot: AppSnapshot = gateway.bootstrap()): StateView {
        val currentCatalog = catalog
        cachedState?.let { cached ->
            if (
                cached.snapshotVersion == snapshot.version &&
                cached.catalog === currentCatalog &&
                cached.retroArch == retroArch &&
                cached.saveRam == saveRam
            ) return cached.view
        }
        val active = resolveRuleset(snapshot.settings.ruleset)
        return ApiViewBuilder.state(
            snapshot,
            currentCatalog,
            truth = battleTruth(snapshot, currentCatalog),
            activeRulesetId = active?.id,
            rulesetAssumed = snapshot.settings.ruleset == "AUTO" && !levelUpRulesetDetectionResolved,
            retroArch = retroArch,
            saveRam = saveRam,
        ).also { view -> cachedState = CachedState(snapshot.version, currentCatalog, retroArch, saveRam, view) }
    }

    fun updateRetroArch(state: RetroArchView) {
        retroArch = state
    }

    fun retroArchState(): RetroArchView = retroArch

    fun updateSaveRam(state: SaveRamView) {
        saveRam = state
    }

    fun updateOverlayScale(scale: Double) {
        val current = gateway.bootstrap().settings
        gateway.dispatch(
            CompanionAction.UpdateSettings(
                current.copy(overlayScale = scale.takeIf(Double::isFinite)?.coerceIn(0.45, 1.0) ?: 1.0),
            ),
        )
    }

    @Synchronized
    fun saveParseContext(): SaveParseContext? {
        if (catalogPublicationInProgress) return null
        return catalog?.let(::saveParseContext)
    }

    private fun saveParseContext(current: ParsedCatalog): SaveParseContext {
        cachedSaveParseContext?.let { cached ->
            if (cached.catalog === current) return cached.value
        }
        return SaveParseContext(
        romIdentity = current.romSha256,
        speciesById = current.speciesById.mapValues { (id, species) ->
            SaveSpeciesContext(
                speciesId = id,
                dexNumber = species.dexNumber.value,
                growthRate = species.growthRate.value,
                formId = species.formId,
                abilityIds = species.abilityIds.value.orEmpty().filter { it > 0 },
            )
        },
        captureBallIds = current.captureBallsById.keys.ifEmpty { (1..15).toSet() },
        levelUpRulesetSelectors = completeLevelUpRulesetSelectors(current),
        movePpById = current.movesById.mapNotNull { (id, move) ->
            move.pp.value?.takeIf { it > 0 }?.let { id to it }
        }.toMap(),
        gen3TextEncoding = Gen3TextEncoding.ENGLISH.takeIf { current.platform == Platform.GBA },
        gen3SaveRuntimeAbi = current.runtimeMetadata.gen3RuntimeMemoryLayout?.saveRuntimeAbi?.let { abi ->
            Gen3SaveRuntimeAbi(
                saveBlock1Size = abi.saveBlock1Size,
                saveBlock2Size = abi.saveBlock2Size,
                extendedSaveDataSize = abi.extendedSaveDataSize,
                textEncoding = when (abi.textEncoding) {
                    com.enrpau.dualscreendex.parser.catalog.CatalogGen3TextEncoding.ENGLISH ->
                        Gen3TextEncoding.ENGLISH
                },
                trainer = Gen3TrainerCardAbi(
                    playerNameOffset = abi.trainer.playerNameOffset,
                    playerNameLength = abi.trainer.playerNameLength,
                    genderOffset = abi.trainer.genderOffset,
                    trainerIdOffset = abi.trainer.trainerIdOffset,
                    playTimeHoursOffset = abi.trainer.playTimeHoursOffset,
                    playTimeMinutesOffset = abi.trainer.playTimeMinutesOffset,
                    encryptionKeyOffset = abi.trainer.encryptionKeyOffset,
                    moneyOffset = abi.trainer.moneyOffset,
                    maximumMoney = abi.trainer.maximumMoney,
                    badgeFlags = abi.trainer.badgeFlags.map { Gen3BitFlag(it.byteOffset, it.mask) },
                ),
                bag = Gen3BagAbi(
                    abi.bag.pockets.map { pocket ->
                        Gen3BagPocketAbi(
                            pocket = when (pocket.pocket) {
                                com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagPocket.ITEMS -> BagPocket.ITEMS
                                com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagPocket.KEY_ITEMS -> BagPocket.KEY_ITEMS
                                com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagPocket.BALLS -> BagPocket.BALLS
                                com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagPocket.TM_HM -> BagPocket.TM_HM
                                com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagPocket.BERRIES -> BagPocket.BERRIES
                            },
                            byteOffset = pocket.byteOffset,
                            capacity = pocket.capacity,
                            slotSize = pocket.slotSize,
                            dataSource = when (pocket.dataSource) {
                                com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagDataSource.SAVE_BLOCK1 ->
                                    Gen3BagDataSource.SAVE_BLOCK1
                                com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagDataSource.EXTENDED_SAVE ->
                                    Gen3BagDataSource.EXTENDED_SAVE
                            },
                        )
                    },
                ),
                eventFlags = abi.eventFlags?.let { Gen3EventFlagAbi(it.byteOffset, it.byteCount) },
            )
        },
        ).also { value -> cachedSaveParseContext = CachedSaveParseContext(current, value) }
    }

    @Synchronized
    fun battleCatalogContext(): BattleCatalogContext? {
        if (catalogPublicationInProgress) return null
        val current = catalog ?: return null
        val savedTrainer = savedPlayerState?.trainer
        cachedBattleCatalogContext?.let { cached ->
            if (cached.catalog === current && cached.trainer == savedTrainer) return cached.value
        }
        val generation = when (current.family) {
            EngineFamily.RED_BLUE, EngineFamily.YELLOW -> 1
            EngineFamily.GOLD_SILVER, EngineFamily.CRYSTAL -> 2
            EngineFamily.RUBY_SAPPHIRE, EngineFamily.EMERALD, EngineFamily.FIRERED_LEAFGREEN -> 3
        }
        val species = current.speciesById.mapNotNull { (id, record) ->
            val types = record.typeIds.value ?: return@mapNotNull null
            id to BattleSpecies(
                id = id,
                typeIds = types,
                abilityIds = record.abilityIds.value.orEmpty().filterTo(mutableSetOf()) { it > 0 },
            )
        }.toMap()
        val moves = current.movesById.mapNotNull { (id, record) ->
            val pp = record.pp.value?.takeIf { it > 0 } ?: return@mapNotNull null
            id to BattleMove(id, pp)
        }.toMap()
        val value = if (species.isEmpty() || moves.isEmpty() || current.typesById.isEmpty()) null else BattleCatalogContext(
            romIdentity = current.romSha256,
            generation = generation,
            catalog = BattleCatalogView(species, moves, current.typesById.keys),
            gen2TimeOfDayWramOffset = current.runtimeMetadata.gen2TimeOfDayWramOffset,
            gen3SaveBlock1PointerAddress = current.runtimeMetadata.gen3SaveBlock1PointerAddress,
            gen3RuntimeMemoryLayout = current.runtimeMetadata.gen3RuntimeMemoryLayout?.let { layout ->
                Gen3RuntimeMemoryLayout(
                    mainAddress = layout.mainAddress,
                    inBattleAddress = layout.inBattleAddress,
                    inBattleMask = layout.inBattleMask,
                    saveBlock1MapGroupOffset = layout.saveBlock1MapGroupOffset,
                    saveBlock1MapNumberOffset = layout.saveBlock1MapNumberOffset,
                    saveBlock1PositionXOffset = layout.saveBlock1PositionXOffset,
                    saveBlock1PositionYOffset = layout.saveBlock1PositionYOffset,
                    liveClockAddress = layout.liveClockAddress,
                    multiUsePlayerCursorAddress = layout.multiUsePlayerCursorAddress,
                    playerPartyCountAddress = layout.partyAbi?.countAddress ?: layout.playerPartyCountAddress,
                    playerPartyAddress = layout.partyAbi?.partyAddress ?: layout.playerPartyAddress,
                    playerPartyCapacity = layout.partyAbi?.capacity ?: layout.playerPartyAddress?.let { 6 },
                    playerPartyRecordSize = layout.partyAbi?.recordSize ?: layout.playerPartyAddress?.let { 100 },
                    battleMonsAddress = layout.battleMonsAddress,
                    battleTypeFlagsAddress = layout.battleTypeFlagsAddress,
                    battleUi = layout.battleUiAbi?.let { battleUi ->
                        Gen3BattleUiMemoryLayout(
                            activeBattlerAddress = battleUi.activeBattlerAddress,
                            actionCursorAddress = battleUi.actionCursorAddress,
                            moveCursorAddress = battleUi.moveCursorAddress,
                        )
                    },
                    trainerBattleMask = layout.trainerBattleMask,
                    nonWildBattleMask = layout.nonWildBattleMask,
                    saveBlock1PointerAddress = layout.saveBlock1PointerAddress,
                    saveBlock2PointerAddress = layout.saveBlock2PointerAddress,
                    saveBlock1Size = layout.saveRuntimeAbi?.saveBlock1Size,
                    saveBlock2Size = layout.saveRuntimeAbi?.saveBlock2Size,
                    extendedSaveAddress = layout.extendedSaveAddress,
                    extendedSaveSize = layout.saveRuntimeAbi?.extendedSaveDataSize?.takeIf { it > 0 },
                )
            },
            liveAreaMemoryLayout = liveAreaMemoryLayout(current.family),
            saveParseContext = saveParseContext(current),
            savedTrainer = savedTrainer,
        )
        cachedBattleCatalogContext = CachedBattleCatalogContext(current, savedTrainer, value)
        return value
    }

    @Synchronized
    fun updateLiveGameState(snapshot: Gen3LiveGameSnapshot?) {
        val current = catalog
        if (snapshot != null && (current == null || !snapshot.romIdentity.equals(current.romSha256, true))) return
        liveGameState = snapshot
        publishSelectedPlayerState()
    }

    @Synchronized
    fun updateGen2GameClock(lighting: MapLighting?) {
        val family = catalog?.family
        if (family != EngineFamily.GOLD_SILVER && family != EngineFamily.CRYSTAL) return
        val gameTime = lighting?.let {
            GameClock(phase = GameClockPhase.valueOf(it.name))
        }
        if (gateway.bootstrap().gameTime != gameTime) {
            gateway.dispatch(CompanionAction.LiveGameClockChanged(gameTime))
        }
    }

    @Synchronized
    fun applyBattleTracking(update: BattleTrackingUpdate) {
        val before = gateway.bootstrap()
        val observed = before.ledger.observedMoves.toMutableMap()
        update.observations.forEach { (speciesId, increments) ->
            val frequencies = observed[speciesId].orEmpty().associate { it.moveId to it.frequency }.toMutableMap()
            increments.forEach { (moveId, count) -> frequencies.merge(moveId, count, Int::plus) }
            observed[speciesId] = frequencies.entries
                .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
                .map { MoveObservation(it.key, it.value) }
        }
        val seen = before.ledger.seenSpecies + update.sample?.opponents.orEmpty().map { it.speciesId }
        val seenSpeciesByArea = before.ledger.seenSpeciesByArea.toMutableMap()
        before.liveAreaBaseId?.let { areaBaseId ->
            val observedHere = update.sample?.opponents.orEmpty().mapTo(mutableSetOf()) { it.speciesId }
            if (observedHere.isNotEmpty()) {
                seenSpeciesByArea[areaBaseId] = seenSpeciesByArea[areaBaseId].orEmpty() + observedHere
            }
        }
        val discoveredMatchups = before.ledger.discoveredMatchups.toMutableMap()
        val currentCatalog = catalog
        update.discoveredMatchups.forEach { observation ->
            effectivenessFor(currentCatalog, observation.moveId, observation.defendingTypeIds)?.let { effectiveness ->
                discoveredMatchups[MatchupKey(observation.speciesId, observation.moveId)] = effectiveness
            }
        }
        val mergedLedger = before.ledger.copy(
            seenSpecies = seen,
            seenSpeciesByArea = seenSpeciesByArea,
            observedMoves = observed,
            discoveredMatchups = discoveredMatchups,
        )
        if (mergedLedger != before.ledger) {
            gateway.dispatch(CompanionAction.ReplaceLedger(mergedLedger))
        }

        if (transientGameState != null) return

        if (update.ended) {
            clearLiveBattle()
            return
        }
        val sample = update.sample ?: return
        if (!update.active) return

        publishBattleSample(sample)
    }

    private fun publishBattleSample(sample: BattleMemorySample) {
        val latestLedger = gateway.bootstrap().ledger
        val opponents = sample.opponents.map { opponent ->
            OpponentState(
                speciesId = opponent.speciesId,
                level = opponent.level,
                typeIds = opponent.typeIds,
                ivs = opponent.ivs,
                dvs = opponent.dvs,
                moveHistory = latestLedger.observedMoves[opponent.speciesId].orEmpty(),
            )
        }
        val battle = BattleState(
            opponents = opponents,
            targetIndex = sample.target.opponentIndex.coerceIn(0, (opponents.size - 1).coerceAtLeast(0)),
            selectedMoveId = sample.selectedMoveId,
            playerReferenceLevel = sample.battlers.filter { it.position and 1 == 0 }.maxOfOrNull { it.level },
            targetMode = when (sample.target.mode) {
                TargetMode.AUTOMATIC -> BattleTargetMode.AUTOMATIC
                TargetMode.MANUAL_TARGET_FALLBACK -> BattleTargetMode.MANUAL_TARGET_FALLBACK
            },
            capabilities = sample.capabilities.mapKeys { it.key.name }.mapValues { it.value.name },
            encounterKind = when (sample.encounterKind) {
                com.darkaxt.dualdex.battle.BattleEncounterKind.WILD -> CompanionBattleEncounterKind.WILD
                com.darkaxt.dualdex.battle.BattleEncounterKind.TRAINER -> CompanionBattleEncounterKind.TRAINER
                com.darkaxt.dualdex.battle.BattleEncounterKind.UNKNOWN -> CompanionBattleEncounterKind.UNKNOWN
            },
            rarityUsable = opponents.getOrNull(
                sample.target.opponentIndex.coerceIn(0, (opponents.size - 1).coerceAtLeast(0)),
            )?.let { opponent ->
                val current = catalog ?: return@let false
                val generation = when (current.family) {
                    EngineFamily.RED_BLUE, EngineFamily.YELLOW -> 1
                    EngineFamily.GOLD_SILVER, EngineFamily.CRYSTAL -> 2
                    else -> 3
                }
                val assessment = RarityEvaluator.evaluate(
                    individual = com.enrpau.dualscreendex.companion.model.OwnedPokemon(
                        stableKey = "battle",
                        speciesId = opponent.speciesId,
                        generation = generation,
                        level = opponent.level,
                        ivs = opponent.ivs,
                        dvs = opponent.dvs,
                    ),
                    currentAreaBaseId = gateway.bootstrap().liveAreaBaseId,
                    encounterAreas = current.encounterAreas,
                )
                assessment.innateTier != null && assessment.stars != null
            } ?: false,
        )
        gateway.dispatch(
            if (gateway.bootstrap().battle == null) CompanionAction.BattleStarted(battle)
            else CompanionAction.BattleUpdated(battle),
        )
    }

    @Synchronized
    fun clearLiveBattle() {
        if (gateway.bootstrap().battle != null) gateway.dispatch(CompanionAction.BattleEnded)
    }

    @Synchronized
    fun updateLiveMapPosition(position: RuntimeMapPosition?) {
        val mapped = position?.let { LiveMapPosition(it.x, it.y) }
        if (gateway.bootstrap().liveMapPosition != mapped) {
            gateway.dispatch(CompanionAction.LiveMapPositionChanged(mapped))
        }
        mergeLivePoiProximity(gateway.bootstrap().liveAreaBaseId, mapped)
    }

    fun battlePollingIntervalMs(): Int = gateway.bootstrap().settings.battlePollingIntervalMs.coerceIn(1, 20)

    @Synchronized
    fun updateLiveArea(areaBaseId: Int?) {
        if (gateway.bootstrap().liveAreaBaseId != areaBaseId) {
            gateway.dispatch(
                CompanionAction.LiveAreaChanged(
                    areaBaseId,
                    gameAccessReady = when {
                        areaBaseId == null -> false
                        catalog?.platform == Platform.GB || catalog?.platform == Platform.GBC -> true
                        else -> null
                    },
                ),
            )
        }
        val validAreaBaseId = areaBaseId?.takeIf { candidate ->
            candidate in (catalog?.discoverableAreaBaseIds() ?: emptySet())
        } ?: return
        val before = gateway.bootstrap().ledger
        if (validAreaBaseId !in before.visitedAreaBaseIds) {
            val updated = before.copy(visitedAreaBaseIds = before.visitedAreaBaseIds + validAreaBaseId)
            gateway.dispatch(CompanionAction.ReplaceLedger(updated))
        }
        mergeLivePoiProximity(validAreaBaseId, gateway.bootstrap().liveMapPosition)
    }

    private fun mergeLivePoiProximity(areaBaseId: Int?, position: LiveMapPosition?) {
        val currentCatalog = catalog ?: return
        val validAreaBaseId = areaBaseId ?: return
        val validPosition = position ?: return
        val before = gateway.bootstrap().ledger
        val updated = LocalMapPoiKnowledgeMapper.mergeProximity(
            previous = before,
            catalog = currentCatalog,
            baseAreaId = validAreaBaseId,
            tileX = validPosition.x,
            tileY = validPosition.y,
        )
        if (updated != before) {
            gateway.dispatch(CompanionAction.ReplaceLedger(updated))
        }
    }

    private fun battleTruth(snapshot: AppSnapshot, currentCatalog: ParsedCatalog?): Effectiveness? {
        val battle = snapshot.battle ?: return null
        val target = battle.opponents.getOrNull(battle.targetIndex) ?: return null
        val moveId = battle.selectedMoveId ?: return null
        return effectivenessFor(currentCatalog, moveId, target.typeIds)
    }

    private fun effectivenessFor(
        currentCatalog: ParsedCatalog?,
        moveId: Int,
        defendingTypeIds: List<Int>,
    ): Effectiveness? {
        val current = currentCatalog ?: return null
        if (current.typeChart.isEmpty() || defendingTypeIds.isEmpty()) return null
        val attackingTypeId = current.movesById[moveId]?.typeId?.value ?: return null
        var multiplier = 100L
        defendingTypeIds.distinct().forEach { defendingTypeId ->
            val factor = current.typeChart.lastOrNull {
                it.attackingTypeId == attackingTypeId && it.defendingTypeId == defendingTypeId
            }?.multiplierPercent ?: 100
            multiplier = multiplier * factor / 100
        }
        return when {
            multiplier == 0L -> Effectiveness.NO_EFFECT
            multiplier < 100L -> Effectiveness.RESISTED
            multiplier == 100L -> Effectiveness.NEUTRAL
            else -> Effectiveness.SUPER_EFFECTIVE
        }
    }

    @Synchronized
    fun applySaveSnapshot(snapshot: SaveSnapshot, state: SaveRamView): Boolean {
        return applySaveState(snapshot, state, KnowledgeLedger(), null).accepted
    }

    @Synchronized
    fun applySaveObservation(
        observation: SaveObservation,
        snapshot: SaveSnapshot,
        state: SaveRamView,
        checkpoint: KnowledgeLedger? = null,
    ): SaveKnowledgeApplication {
        val current = catalog ?: return SaveKnowledgeApplication(false)
        if (!snapshot.romIdentity.equals(current.romSha256, ignoreCase = true)) return SaveKnowledgeApplication(false)
        val incoming = ActivePlaythrough(current.romSha256, snapshot.saveIdentity, observation.source.id)
        val samePlaythrough = activePlaythrough?.matches(incoming) == true
        val seed = when {
            observation.kind == SaveObservationKind.INITIAL || observation.kind == SaveObservationKind.SWITCHED ->
                KnowledgeLedgerSanitizer.sanitize(checkpoint ?: KnowledgeLedger(), current)
            samePlaythrough -> gateway.bootstrap().ledger
            else -> KnowledgeLedger()
        }
        val applied = applySaveState(snapshot, state, seed, incoming)
        val frozen = if (observation.kind == SaveObservationKind.CHANGED && samePlaythrough && applied.accepted) {
            gateway.bootstrap().ledger
        } else {
            null
        }
        return applied.copy(checkpointLedger = frozen)
    }

    private fun applySaveState(
        snapshot: SaveSnapshot,
        state: SaveRamView,
        seed: KnowledgeLedger,
        playthrough: ActivePlaythrough?,
    ): SaveKnowledgeApplication {
        val current = catalog ?: return SaveKnowledgeApplication(false)
        if (!snapshot.romIdentity.equals(current.romSha256, ignoreCase = true)) return SaveKnowledgeApplication(false)
        activePlaythrough = playthrough
        gateway.dispatch(CompanionAction.ReplaceLedger(KnowledgeLedgerSanitizer.sanitize(seed, current)))
        savedPlayerState = snapshot
        cachedBattleCatalogContext = null
        val merged = mergedPlayerKnowledge(current)
        val selectors = completeLevelUpRulesetSelectors(current)
        val detected = snapshot.detectedLevelUpRulesetId?.takeIf { id ->
            val expectedFingerprint = LevelUpRulesetDetectionFingerprint.create(selectors, id)
            snapshot.levelUpRulesetDetectionResolved &&
                selectors.size == current.learnsetRulesets.size &&
                snapshot.levelUpRulesetDetectionFingerprint != null &&
                snapshot.levelUpRulesetDetectionFingerprint == expectedFingerprint
        }
        detectedLevelUpRulesetId = detected
        levelUpRulesetDetectionResolved = detected != null
        saveRam = state
        cachedState = null
        gateway.dispatch(CompanionAction.ReplaceLedger(merged))
        publishSelectedPlayerSnapshot()
        resolvedGameState?.let(::applyResolvedPartyAndProgression)
        return SaveKnowledgeApplication(true)
    }

    private fun compatibilityParty(): List<OwnedIndividual> = if (transientGameState == null) {
        liveGameState
            ?.party
            ?.takeIf { it.state == Gen3LiveSectionState.AVAILABLE }
            ?.value
            ?: savedPlayerState?.party
            ?: emptyList()
    } else {
        gateway.bootstrap().party
    }

    private fun compatibilityTrainer(): TrainerSnapshot? = if (transientGameState == null) {
        liveGameState
            ?.trainer
            ?.takeIf { it.state == Gen3LiveSectionState.AVAILABLE }
            ?.value
            ?: savedPlayerState?.trainer
    } else {
        savedPlayerState?.trainer
    }

    private fun compatibilityTrainerIdentity(): TrainerIdentity? = if (transientGameState == null) {
        liveGameState
            ?.trainerIdentity
            ?.takeIf { it.state == Gen3LiveSectionState.AVAILABLE }
            ?.value
            ?: compatibilityTrainer()?.let { TrainerIdentity(it.name, it.gender) }
    } else {
        savedPlayerState?.trainer?.let { TrainerIdentity(it.name, it.gender) }
    }

    private fun mergedPlayerKnowledge(current: ParsedCatalog): KnowledgeLedger {
        val before = gateway.bootstrap().ledger
        val fromSave = savedPlayerState
            ?.takeIf { it.romIdentity.equals(current.romSha256, true) }
            ?.let { SaveKnowledgeMapper.merge(before, current, it) }
            ?: before
        val withSavedFlags = LocalMapPoiKnowledgeMapper.mergeEventFlags(
            previous = fromSave,
            catalog = current,
            setFlagIds = savedPlayerState
                ?.takeIf { it.romIdentity.equals(current.romSha256, true) }
                ?.eventFlagIds,
        )
        val withLiveFlags = if (transientGameState == null) {
            LocalMapPoiKnowledgeMapper.mergeEventFlags(
                previous = withSavedFlags,
                catalog = current,
                setFlagIds = liveGameState?.eventFlags
                    ?.takeIf { it.state == Gen3LiveSectionState.AVAILABLE }
                    ?.value,
            )
        } else {
            withSavedFlags
        }
        val livePartySelection = if (transientGameState == null) {
            when {
                liveGameState?.party?.state == Gen3LiveSectionState.AVAILABLE -> liveGameState?.party?.value
                savedPlayerState != null -> null
                else -> emptyList()
            }
        } else {
            null
        }
        return livePartySelection?.let { party ->
            LivePartyKnowledgeMapper.merge(withLiveFlags, current, party, generation = 3)
        } ?: withLiveFlags
    }

    private fun publishSelectedPlayerSnapshot() {
        val schedule = catalog?.runtimeMetadata?.gen3RuntimeMemoryLayout?.liveClockSchedule
        val gameTime = liveGameState?.clock
            ?.takeIf { it.state == Gen3LiveSectionState.AVAILABLE }
            ?.value
            ?.let {
                projectGameClock(
                    it.hours,
                    it.minutes,
                    schedule?.dayStartHour,
                    schedule?.nightStartHour,
                )
            }
        gateway.dispatch(
            CompanionAction.LiveGameStateChanged(
                trainer = compatibilityTrainer(),
                party = compatibilityParty(),
                gameTime = gameTime,
                trainerIdentity = compatibilityTrainerIdentity(),
                gameAccessReady = when (catalog?.platform) {
                    Platform.GBA -> liveGameState?.let { snapshot ->
                        snapshot.location.state == Gen3LiveSectionState.AVAILABLE &&
                            snapshot.trainerIdentity.state == Gen3LiveSectionState.AVAILABLE &&
                            snapshot.clock.value?.let { clock ->
                                clock.hours != 0 || clock.minutes != 0 || clock.seconds != 0
                            } != false
                    } == true
                    Platform.GB, Platform.GBC -> gateway.bootstrap().gameAccessReady
                    else -> false
                },
            ),
        )
    }

    private fun publishSelectedPlayerState() {
        val current = catalog
        if (current != null) {
            val merged = mergedPlayerKnowledge(current)
            if (merged != gateway.bootstrap().ledger) {
                gateway.dispatch(CompanionAction.ReplaceLedger(merged))
            }
        }
        publishSelectedPlayerSnapshot()
    }

    fun action(type: String, values: Map<String, String?>): StateView {
        when (type.uppercase()) {
            "OPEN_SPECIES" -> gateway.dispatch(CompanionAction.OpenSpecies(requireInt(values, "speciesId")))
            "OPEN_TRAINER" -> gateway.dispatch(CompanionAction.OpenTrainer)
            "OPEN_PARTY" -> gateway.dispatch(CompanionAction.OpenParty)
            "OPEN_PARTY_MEMBER" -> gateway.dispatch(CompanionAction.OpenPartyMember(requireInt(values, "slot")))
            "BACK" -> gateway.dispatch(CompanionAction.BackToPokedex)
            "SCREEN" -> gateway.dispatch(CompanionAction.SetScreen(AppScreen.valueOf(requireNotNull(values["screen"]).uppercase())))
            "FILTER" -> gateway.dispatch(
                CompanionAction.SetFilter(
                    PokedexFilter.valueOf(requireNotNull(values["filter"]).uppercase()),
                    values["areaId"]?.toIntOrNull(),
                ),
            )
            "MAP_AREA" -> {
                val regionKey = requireNotNull(values["regionKey"]) { "regionKey is required" }
                val locationKey = requireNotNull(values["locationKey"]) { "locationKey is required" }
                val region = catalog?.worldMaps?.regions?.singleOrNull { it.key == regionKey }
                    ?: error("map region is unavailable or ambiguous")
                val location = region.locations.asSequence()
                    .singleOrNull { it.key == locationKey }
                    ?: error("map location is unavailable or ambiguous")
                val areaIds = catalog?.encounterAreas
                    ?.filter { it.id / 10 in location.baseAreaIds }
                    ?.mapTo(sortedSetOf()) { it.id }
                    .orEmpty()
                if (areaIds.isEmpty()) return stateView()
                gateway.dispatch(
                    CompanionAction.OpenAreaPokedex(areaIds),
                )
            }
            "MAP_POI_SETTINGS" -> updateLocalMapPoiPreferences(values)
            "SETTINGS" -> updateSettings(values)
            "TAB", "BATTLE_TAB" -> gateway.dispatch(CompanionAction.SetBattleTab(BattleTab.valueOf(requireNotNull(values["tab"]).uppercase())))
            "TARGET", "SELECT_TARGET" -> gateway.dispatch(CompanionAction.SelectTarget(requireInt(values, "index")))
            else -> throw IllegalArgumentException("unknown production action: $type")
        }
        return stateView()
    }

    @Synchronized
    fun speciesSprite(id: Int) = catalog?.speciesById?.get(id)?.sprite?.value

    @Synchronized
    fun ballSprite(id: Int) = catalog?.captureBallsById?.get(id)?.sprite?.value

    fun mapAsset(
        key: String,
        requestedLighting: MapLighting,
        time: MapTimeOfDay? = null,
    ): RenderedMapAsset? {
        val current = synchronized(this) { catalog } ?: return null
        val variant = when {
            key in current.localMaps.timedAssets -> "TIME-${(time ?: MapTimeOfDay(12, 0)).minuteOfDay}"
            key in current.localMaps.indexedAssets -> "LIGHTING-${requestedLighting.name}"
            else -> "STATIC"
        }
        return mapAssetRenderCache.getOrRender(MapAssetRenderKey(current.romSha256, key, variant)) {
            mapAssetRenderer(current, key, requestedLighting, time)
        }
    }

    fun mapAssetCacheStats(): MapAssetRenderCacheStats = mapAssetRenderCache.stats()

    @Synchronized
    fun trainerAsset(key: String) = catalog?.trainerAssets?.assets?.get(key)

    @Synchronized
    fun catalogHash(): String? = catalog?.romSha256

    @Synchronized
    fun diagnostics(speciesId: Int?, moveId: Int?): DiagnosticView {
        val current = requireNotNull(catalog) { "load a ROM before requesting diagnostics" }
        val snapshot = gateway.bootstrap()
        val active = resolveRuleset(snapshot.settings.ruleset)
        return ApiViewBuilder.diagnostics(
            current,
            snapshot.catalogName,
            active?.id,
            snapshot.settings.ruleset == "AUTO" && !levelUpRulesetDetectionResolved,
            speciesId,
            moveId,
        )
    }

    override fun close() {
        transientGameStateSubscription?.close()
        synchronized(this) {
            loadGeneration.incrementAndGet()
            clearCatalogProjectionCaches()
        }
        mapAssetRenderCache.clear()
        parserWorker.shutdown()
    }

    private fun updateSettings(values: Map<String, String?>) {
        val current = gateway.bootstrap().settings
        val ruleset = values["ruleset"]?.let { requested ->
            if (requested.equals("AUTO", ignoreCase = true)) {
                "AUTO"
            } else {
                requireNotNull(catalog?.learnsetRulesets?.firstOrNull { it.id == requested }) {
                    "unknown catalog ruleset: $requested"
                }.id
            }
        } ?: current.ruleset
        val updated = CompanionSettings(
                    knowledgeMode = values["knowledgeMode"]?.let { KnowledgeMode.valueOf(it.uppercase()) } ?: current.knowledgeMode,
                    attackEnabled = values["attackEnabled"]?.toBooleanStrictOrNull() ?: current.attackEnabled,
                    rarityEnabled = values["rarityEnabled"]?.toBooleanStrictOrNull() ?: current.rarityEnabled,
                    movesEnabled = values["movesEnabled"]?.toBooleanStrictOrNull() ?: current.movesEnabled,
                    fontScale = values["fontScale"]?.toDoubleOrNull()?.coerceIn(0.85, 1.35) ?: current.fontScale,
                    density = values["density"]?.let { Density.valueOf(it.uppercase()) } ?: current.density,
                    highContrast = values["highContrast"]?.toBooleanStrictOrNull() ?: current.highContrast,
                    autoOpenTarget = values["autoOpenTarget"]?.toBooleanStrictOrNull() ?: current.autoOpenTarget,
                    ruleset = ruleset,
                    displayMode = values["displayMode"]?.let { DisplayMode.valueOf(it.uppercase()) } ?: current.displayMode,
                    theme = values["theme"]?.let { Theme.valueOf(it.uppercase()) } ?: current.theme,
                    displayTarget = values["displayTarget"]?.let { DisplayTarget.valueOf(it.uppercase()) } ?: current.displayTarget,
                    overlayScale = current.overlayScale,
                    battlePollingIntervalMs = values["battlePollingIntervalMs"]?.toIntOrNull()?.coerceIn(1, 20)
                        ?: current.battlePollingIntervalMs,
                    mapFollowSmoothingPercent = values["mapFollowSmoothingPercent"]?.toIntOrNull()?.coerceIn(0, 100)
                        ?: current.mapFollowSmoothingPercent,
                )
        gateway.dispatch(
            CompanionAction.UpdateSettings(updated),
        )
        if (settingsWritesEnabled) onRomSettingsChanged(settingsRomSha256, updated)
    }

    private fun updateLocalMapPoiPreferences(values: Map<String, String?>) {
        val before = gateway.bootstrap().ledger
        val current = before.localMapPoiPreferences
        val iconThreshold = values["iconZoomThresholdPercent"]
            ?.toIntOrNull()
            ?.coerceIn(0, 100)
            ?: current.iconZoomThresholdPercent
        val labelThreshold = values["labelZoomThresholdPercent"]
            ?.toIntOrNull()
            ?.coerceIn(iconThreshold, 100)
            ?: current.labelZoomThresholdPercent.coerceAtLeast(iconThreshold)
        val updated = current.copy(
            showPlaces = values["showPlaces"]?.toBooleanStrictOrNull() ?: current.showPlaces,
            showServices = values["showServices"]?.toBooleanStrictOrNull() ?: current.showServices,
            showAvailableItems = values["showAvailableItems"]?.toBooleanStrictOrNull() ?: current.showAvailableItems,
            showCollectedItems = values["showCollectedItems"]?.toBooleanStrictOrNull() ?: current.showCollectedItems,
            showUnknownPois = values["showUnknownPois"]?.toBooleanStrictOrNull() ?: current.showUnknownPois,
            iconZoomThresholdPercent = iconThreshold,
            labelZoomThresholdPercent = labelThreshold,
        )
        if (updated == current) return
        val ledger = before.copy(localMapPoiPreferences = updated)
        gateway.dispatch(CompanionAction.ReplaceLedger(ledger))
    }

    private fun resolveRuleset(selection: String) = catalog?.learnsetRulesets?.let { rulesets ->
        if (selection == "AUTO") {
            detectedLevelUpRulesetId
                ?.takeIf { levelUpRulesetDetectionResolved }
                ?.let { detected -> rulesets.firstOrNull { it.id == detected } }
                ?: rulesets.singleOrNull()
        } else rulesets.firstOrNull { it.id == selection }
    }

    @Synchronized
    private fun publishCheckpoint(
        generation: Long,
        progress: CatalogMaterializationProgress,
        source: CatalogSourceMetadata,
    ) {
        if (generation != loadGeneration.get()) return
        catalogRepository?.write(
            progress.catalog,
            source,
            catalogWriteProgress(progress),
        )
    }

    @Synchronized
    private fun publishWork(generation: Long, work: CatalogWorkProgress, name: String) {
        if (generation != loadGeneration.get()) return
        gateway.dispatch(
            CompanionAction.CatalogLoadingChanged(
                CatalogLoadingState(
                    active = true,
                    phase = work.module.name,
                    completedUnits = work.completedUnits,
                    totalUnits = work.totalUnits,
                    message = catalogLoadingMessage,
                ),
                name,
            ),
        )
    }

    @Synchronized
    private fun publishReopened(generation: Long, name: String, reopened: ParsedCatalog) {
        if (generation != loadGeneration.get()) return
        catalogPublicationInProgress = true
        try {
            saveRam = SaveRamView()
            clearLevelUpRulesetDetection()
            clearLiveBattle()
            applyWinningCatalogSettings(reopened.romSha256)
            gateway.dispatch(CompanionAction.ReplaceLedger(KnowledgeLedger()))
            gateway.dispatch(CompanionAction.SetScreen(AppScreen.POKEDEX))
            catalog = reopened
            settingsWritesEnabled = true
            onCatalogCommitted(reopened.romSha256, name)
            gateway.dispatch(
                CompanionAction.CatalogLoadingChanged(
                    CatalogLoadingState(active = false, phase = "CACHE_REOPEN", completedUnits = 1, totalUnits = 1),
                    name,
                ),
            )
            gateway.dispatch(CompanionAction.CatalogLoaded(name))
        } finally {
            catalogPublicationInProgress = false
        }
    }

    private fun requireInt(values: Map<String, String?>, key: String): Int =
        requireNotNull(values[key]?.toIntOrNull()) { "$key is required" }

    private fun notifyCompletion(callback: ((Result<Unit>) -> Unit)?, result: Result<Unit>) {
        if (callback != null) runCatching { callback(result) }
    }

    @Synchronized
    private fun activeCatalogMatches(sha256: String): Boolean =
        catalog?.romSha256.equals(sha256, ignoreCase = true) && gateway.bootstrap().catalogReady

    private fun catalogTransitionUnits(phase: String): Int =
        if (phase == "CACHE_REOPEN") 1 else CatalogWorkModule.entries.size

    private fun cacheRefreshMessage(decision: CatalogCacheDecision): String? = when (decision) {
        CatalogCacheDecision.MISS_FILE_ABSENT -> "Preparing your game guide for the first time."
        CatalogCacheDecision.MISS_INCOMPLETE_OR_INCOMPATIBLE ->
            "Saved guide data needs to be refreshed for this version."
        CatalogCacheDecision.REJECTED_EXCEPTION ->
            "Saved guide data could not be reopened, so it is being prepared again."
        CatalogCacheDecision.HIT -> null
    }

    @Synchronized
    private fun setCatalogLoadingMessage(generation: Long, message: String?) {
        if (generation == loadGeneration.get()) catalogLoadingMessage = message
    }

    @Synchronized
    private fun beginCatalogTransition(romSha256: String?, name: String? = null, phase: String): Long {
        val generation = loadGeneration.incrementAndGet()
        catalogLoadingMessage = null
        catalog = null
        clearCatalogProjectionCaches()
        mapAssetRenderCache.clear()
        liveGameState = null
        savedPlayerState = null
        activePlaythrough = null
        settingsRomSha256 = null
        settingsWritesEnabled = false
        clearLevelUpRulesetDetection()
        gateway.dispatch(
            CompanionAction.CatalogLoadingChanged(
                CatalogLoadingState(
                    active = true,
                    phase = phase,
                    completedUnits = 0,
                    totalUnits = catalogTransitionUnits(phase),
                ),
                name,
            ),
        )
        clearLiveBattle()
        saveRam = SaveRamView()
        gateway.dispatch(CompanionAction.ReplaceLedger(KnowledgeLedger()))
        gateway.dispatch(CompanionAction.LiveGameStateChanged(null, emptyList()))
        gateway.dispatch(CompanionAction.SetScreen(AppScreen.POKEDEX))
        return generation
    }

    private fun restoreGlobalSettings() {
        settingsRomSha256 = null
        globalSettings?.invoke()?.let(::applySettings)
        settingsWritesEnabled = true
    }

    @Synchronized
    private fun publishTransitionFailure(generation: Long, phase: String, failure: String? = null) {
        if (generation != loadGeneration.get()) return
        restoreGlobalSettings()
        onCatalogCleared()
        if (failure != null) gateway.dispatch(CompanionAction.Failure(failure))
        gateway.dispatch(
            CompanionAction.CatalogLoadingChanged(
                CatalogLoadingState(
                    active = false,
                    phase = phase,
                    completedUnits = 0,
                    totalUnits = if (phase == "IDLE") 0 else catalogTransitionUnits(phase),
                ),
            ),
        )
    }

    @Synchronized
    private fun publishParsed(generation: Long, name: String, parsed: ParsedCatalog) {
        if (generation != loadGeneration.get()) return
        applyWinningCatalogSettings(parsed.romSha256)
        catalog = parsed
        settingsWritesEnabled = true
        gateway.dispatch(CompanionAction.ReplaceLedger(KnowledgeLedger()))
        onCatalogCommitted(parsed.romSha256, name)
        gateway.dispatch(
            CompanionAction.CatalogLoadingChanged(
                CatalogLoadingState(
                    active = false,
                    phase = "COMPLETE",
                    completedUnits = CatalogWorkModule.entries.size,
                    totalUnits = CatalogWorkModule.entries.size,
                ),
                name,
            ),
        )
        gateway.dispatch(CompanionAction.CatalogLoaded(name))
    }

    private fun applySettingsForRom(romSha256: String) {
        settingsForRom?.invoke(romSha256)?.let(::applySettings)
    }

    private data class ActivePlaythrough(
        val romSha256: String,
        val saveIdentity: String,
        val sourceId: String,
    ) {
        fun matches(other: ActivePlaythrough): Boolean =
            romSha256.equals(other.romSha256, ignoreCase = true) &&
                saveIdentity.equals(other.saveIdentity, ignoreCase = true) &&
                sourceId == other.sourceId
    }

    private fun applyWinningCatalogSettings(romSha256: String) {
        settingsRomSha256 = romSha256
        applySettingsForRom(romSha256)
    }

    private fun applySettings(settings: CompanionSettings) {
        val previousMode = gateway.bootstrap().settings.displayMode
        gateway.dispatch(CompanionAction.UpdateSettings(settings))
        if (settings.displayMode != previousMode) onRomDisplayModeChanged(settings.displayMode)
    }

    private fun clearLevelUpRulesetDetection() {
        detectedLevelUpRulesetId = null
        levelUpRulesetDetectionResolved = false
        cachedState = null
    }

    private fun completeLevelUpRulesetSelectors(current: ParsedCatalog): List<SaveByteSelector> {
        val rulesets = current.learnsetRulesets
        if (rulesets.isEmpty() || rulesets.map { it.id }.distinct().size != rulesets.size) return emptyList()
        return rulesets.map { ruleset ->
            val selector = ruleset.levelUpSelector ?: return emptyList()
            SaveByteSelector(
                rulesetId = ruleset.id,
                saveBlock1ByteOffset = selector.saveBlock1ByteOffset,
                mask = selector.mask,
                expectedValue = selector.expectedValue,
            )
        }
    }

    private data class CachedState(
        val snapshotVersion: Long,
        val catalog: ParsedCatalog?,
        val retroArch: RetroArchView,
        val saveRam: SaveRamView,
        val view: StateView,
    )

    private data class CachedSaveParseContext(
        val catalog: ParsedCatalog,
        val value: SaveParseContext,
    )

    private data class CachedBattleCatalogContext(
        val catalog: ParsedCatalog,
        val trainer: TrainerSnapshot?,
        val value: BattleCatalogContext?,
    )

    private fun clearCatalogProjectionCaches() {
        cachedSaveParseContext = null
        cachedBattleCatalogContext = null
    }

}
