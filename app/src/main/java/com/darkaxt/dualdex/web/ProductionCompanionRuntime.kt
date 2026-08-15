package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogRepository
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.darkaxt.dualdex.knowledge.KnowledgeRepository
import com.darkaxt.dualdex.battle.BattleCatalogContext
import com.darkaxt.dualdex.battle.liveAreaMemoryLayout
import com.darkaxt.dualdex.battle.BattleCatalogView
import com.darkaxt.dualdex.battle.BattleMove
import com.darkaxt.dualdex.battle.BattleSpecies
import com.darkaxt.dualdex.battle.BattleTrackingUpdate
import com.darkaxt.dualdex.battle.Gen3MapPosition
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
import com.enrpau.dualscreendex.companion.model.MatchupKey
import com.enrpau.dualscreendex.companion.model.MoveObservation
import com.enrpau.dualscreendex.companion.model.OpponentState
import com.enrpau.dualscreendex.companion.model.PokedexFilter
import com.enrpau.dualscreendex.companion.model.Theme
import com.enrpau.dualscreendex.companion.knowledge.SaveKnowledgeMapper
import com.enrpau.dualscreendex.companion.knowledge.LivePartyKnowledgeMapper
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveByteSelector
import com.darkaxt.dualdex.save.LevelUpRulesetDetectionFingerprint
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.save.SaveSpeciesContext
import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.BagPocket
import com.darkaxt.dualdex.save.gen3.Gen3BagAbi
import com.darkaxt.dualdex.save.gen3.Gen3BagPocketAbi
import com.darkaxt.dualdex.save.gen3.Gen3BitFlag
import com.darkaxt.dualdex.save.gen3.Gen3SaveRuntimeAbi
import com.darkaxt.dualdex.save.gen3.Gen3TrainerCardAbi
import com.darkaxt.dualdex.save.gen3.Gen3TextEncoding
import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializationProgress
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
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
    private val knowledgeRepository: KnowledgeRepository? = null,
    private val parseCatalog: (RomImage, (CatalogMaterializationProgress) -> Unit) -> ParsedCatalog? = { rom, progress ->
        CatalogParser.parse(rom, progress).catalog
    },
) : AutoCloseable {
    private var catalog: ParsedCatalog? = null
    @Volatile private var settingsRomSha256: String? = null
    @Volatile private var settingsWritesEnabled = true
    @Volatile private var retroArch = RetroArchView()
    @Volatile private var saveRam = SaveRamView()
    private var detectedLevelUpRulesetId: String? = null
    private var levelUpRulesetDetectionResolved = false
    private var liveParty: List<OwnedIndividual>? = null
    private var liveGameState: Gen3LiveGameSnapshot? = null
    private var savedPlayerState: SaveSnapshot? = null
    private var catalogPublicationInProgress = false
    private var cachedState: CachedState? = null
    private val loadGeneration = AtomicLong()
    val gateway = CompanionGateway(
        AppSnapshot(
            settings = initialSettings,
        ),
    )

    fun load(name: String, input: InputStream): BootstrapView = load(RomSourceLoader.load(name, input))

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
        val header = RomHeaderReader.read(rom)
        val source = CatalogSourceMetadata.fromDisplayName(name, rom.size, header.title)
        val generation = beginCatalogTransition(rom.sha256, name, "IDENTIFYING")
        parserWorker.execute {
            try {
                val cached = catalogRepository?.readComplete(rom.sha256)
                if (cached != null) {
                    if (generation != loadGeneration.get()) {
                        notifyCompletion(onComplete, Result.failure(IllegalStateException("catalog load was superseded")))
                        return@execute
                    }
                    catalogRepository.write(cached.catalog, source, CatalogWriteProgress.complete())
                    publishReopened(generation, name, cached.catalog)
                    notifyCompletion(onComplete, Result.success(Unit))
                    return@execute
                }
                val parsed = parseCatalog(rom) { progress -> publishProgress(generation, progress, source) }
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
        gateway.dispatch(CompanionAction.ReplaceLedger(readKnowledge(parsed.romSha256)))
        gateway.dispatch(
            CompanionAction.CatalogLoadingChanged(
                CatalogLoadingState(active = false, phase = "CACHE_REOPEN", completedUnits = 5, totalUnits = 5),
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

    private fun saveParseContext(current: ParsedCatalog): SaveParseContext = SaveParseContext(
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
                        )
                    },
                ),
            )
        },
    )

    @Synchronized
    fun battleCatalogContext(): BattleCatalogContext? {
        if (catalogPublicationInProgress) return null
        val current = catalog ?: return null
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
        if (species.isEmpty() || moves.isEmpty() || current.typesById.isEmpty()) return null
        return BattleCatalogContext(
            romIdentity = current.romSha256,
            generation = generation,
            catalog = BattleCatalogView(species, moves, current.typesById.keys),
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
                    multiUsePlayerCursorAddress = layout.multiUsePlayerCursorAddress,
                    playerPartyCountAddress = layout.partyAbi?.countAddress ?: layout.playerPartyCountAddress,
                    playerPartyAddress = layout.partyAbi?.partyAddress ?: layout.playerPartyAddress,
                    playerPartyCapacity = layout.partyAbi?.capacity ?: layout.playerPartyAddress?.let { 6 },
                    playerPartyRecordSize = layout.partyAbi?.recordSize ?: layout.playerPartyAddress?.let { 100 },
                    battleMonsAddress = layout.battleMonsAddress,
                    battleTypeFlagsAddress = layout.battleTypeFlagsAddress,
                    trainerBattleMask = layout.trainerBattleMask,
                    nonWildBattleMask = layout.nonWildBattleMask,
                    saveBlock1PointerAddress = layout.saveBlock1PointerAddress,
                    saveBlock2PointerAddress = layout.saveBlock2PointerAddress,
                    saveBlock1Size = layout.saveRuntimeAbi?.saveBlock1Size,
                    saveBlock2Size = layout.saveRuntimeAbi?.saveBlock2Size,
                )
            },
            liveAreaMemoryLayout = liveAreaMemoryLayout(current.family),
            saveParseContext = saveParseContext(current),
            savedTrainer = savedPlayerState?.trainer,
        )
    }

    @Synchronized
    fun updateLiveParty(party: List<OwnedIndividual>?) {
        liveParty = party
        publishSelectedPlayerState()
    }

    @Synchronized
    fun updateLiveGameState(snapshot: Gen3LiveGameSnapshot?) {
        val current = catalog
        if (snapshot != null && (current == null || !snapshot.romIdentity.equals(current.romSha256, true))) return
        liveGameState = snapshot
        if (snapshot == null) liveParty = null
        publishSelectedPlayerState()
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
            persistKnowledge(mergedLedger)
        }

        if (update.ended) {
            clearLiveBattle()
            return
        }
        val sample = update.sample ?: return
        if (!update.active) return

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
                assessment.innateTier != null && assessment.relativeTier != null && assessment.stars != null
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
    fun updateLiveMapPosition(position: Gen3MapPosition?) {
        val mapped = position?.let { LiveMapPosition(it.x, it.y) }
        if (gateway.bootstrap().liveMapPosition != mapped) {
            gateway.dispatch(CompanionAction.LiveMapPositionChanged(mapped))
        }
    }

    fun battlePollingIntervalMs(): Int = gateway.bootstrap().settings.battlePollingIntervalMs.coerceIn(1, 20)

    @Synchronized
    fun updateLiveArea(areaBaseId: Int?) {
        if (gateway.bootstrap().liveAreaBaseId != areaBaseId) {
            gateway.dispatch(CompanionAction.LiveAreaChanged(areaBaseId))
        }
        val validAreaBaseId = areaBaseId?.takeIf { candidate ->
            catalog?.encounterAreas?.any { it.id / 10 == candidate } == true
        } ?: return
        val before = gateway.bootstrap().ledger
        if (validAreaBaseId !in before.visitedAreaBaseIds) {
            val updated = before.copy(visitedAreaBaseIds = before.visitedAreaBaseIds + validAreaBaseId)
            gateway.dispatch(CompanionAction.ReplaceLedger(updated))
            persistKnowledge(updated)
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
        val current = catalog ?: return false
        if (!snapshot.romIdentity.equals(current.romSha256, ignoreCase = true)) return false
        savedPlayerState = snapshot
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
        persistKnowledge(merged)
        return true
    }

    private fun selectedParty(): List<OwnedIndividual> = liveGameState
        ?.party
        ?.takeIf { it.state == Gen3LiveSectionState.AVAILABLE }
        ?.value
        ?: liveParty
        ?: savedPlayerState?.party
        ?: emptyList()

    private fun selectedTrainer() = liveGameState
        ?.trainer
        ?.takeIf { it.state == Gen3LiveSectionState.AVAILABLE }
        ?.value
        ?: savedPlayerState?.trainer

    private fun mergedPlayerKnowledge(current: ParsedCatalog): KnowledgeLedger {
        val before = gateway.bootstrap().ledger
        val fromSave = savedPlayerState
            ?.takeIf { it.romIdentity.equals(current.romSha256, true) }
            ?.let { SaveKnowledgeMapper.merge(before, current, it) }
            ?: before
        val livePartySelection = when {
            liveGameState?.party?.state == Gen3LiveSectionState.AVAILABLE -> liveGameState?.party?.value
            liveParty != null -> liveParty
            savedPlayerState != null -> null
            else -> emptyList()
        }
        return livePartySelection?.let { party ->
            LivePartyKnowledgeMapper.merge(fromSave, current, party, generation = 3)
        } ?: fromSave
    }

    private fun publishSelectedPlayerSnapshot() {
        gateway.dispatch(CompanionAction.LiveGameStateChanged(selectedTrainer(), selectedParty()))
    }

    private fun publishSelectedPlayerState() {
        val current = catalog
        if (current != null) {
            val merged = mergedPlayerKnowledge(current)
            if (merged != gateway.bootstrap().ledger) {
                gateway.dispatch(CompanionAction.ReplaceLedger(merged))
                persistKnowledge(merged)
            }
        }
        publishSelectedPlayerSnapshot()
    }

    fun action(type: String, values: Map<String, String?>): StateView {
        when (type.uppercase()) {
            "OPEN_SPECIES" -> gateway.dispatch(CompanionAction.OpenSpecies(requireInt(values, "speciesId")))
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

    @Synchronized
    fun mapAsset(key: String): ByteArray? = catalog?.let { current ->
        current.localMaps.assets[key]?.bytes ?: current.worldMaps.assets[key]?.let(PngEncoder::encode)
    }

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
        synchronized(this) { loadGeneration.incrementAndGet() }
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
                )
        gateway.dispatch(
            CompanionAction.UpdateSettings(updated),
        )
        if (settingsWritesEnabled) onRomSettingsChanged(settingsRomSha256, updated)
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
    private fun publishProgress(
        generation: Long,
        progress: CatalogMaterializationProgress,
        source: CatalogSourceMetadata,
    ) {
        if (generation != loadGeneration.get()) return
        gateway.dispatch(
            CompanionAction.CatalogLoadingChanged(
                CatalogLoadingState(
                    active = true,
                    phase = progress.phase.name,
                    completedUnits = progress.completedUnits,
                    totalUnits = progress.totalUnits,
                ),
                source.displayName,
            ),
        )
        catalogRepository?.write(
            progress.catalog,
            source,
            CatalogWriteProgress(
                phase = progress.phase.name,
                completedUnits = progress.completedUnits,
                totalUnits = progress.totalUnits,
                complete = progress.completedUnits == progress.totalUnits,
                changedSections = changedCatalogSections(progress.phase.name),
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
            gateway.dispatch(CompanionAction.ReplaceLedger(readKnowledge(reopened.romSha256)))
            gateway.dispatch(CompanionAction.SetScreen(AppScreen.POKEDEX))
            catalog = reopened
            settingsWritesEnabled = true
            onCatalogCommitted(reopened.romSha256, name)
            gateway.dispatch(
                CompanionAction.CatalogLoadingChanged(
                    CatalogLoadingState(active = false, phase = "CACHE_REOPEN", completedUnits = 5, totalUnits = 5),
                    name,
                ),
            )
            gateway.dispatch(CompanionAction.CatalogLoaded(name))
        } finally {
            catalogPublicationInProgress = false
        }
    }

    private fun changedCatalogSections(phase: String): Set<String> = when (phase) {
        "ESSENTIAL" -> com.darkaxt.dualdex.catalog.CatalogSchema.requiredSections
        "SPECIES_MEDIA" -> setOf("species")
        "RELATIONSHIPS" -> setOf("species", "encounters", "runtime_metadata")
        "EXTENDED" -> setOf(
            "species",
            "moves",
            "abilities",
            "capture_balls",
            "learnset_rulesets",
            "capabilities",
            "diagnostics",
        )
        "COMPLETE" -> emptySet()
        else -> com.darkaxt.dualdex.catalog.CatalogSchema.requiredSections
    }

    private fun requireInt(values: Map<String, String?>, key: String): Int =
        requireNotNull(values[key]?.toIntOrNull()) { "$key is required" }

    private fun restoreKnowledge(romIdentity: String) {
        gateway.dispatch(CompanionAction.ReplaceLedger(readKnowledge(romIdentity)))
    }

    private fun readKnowledge(romIdentity: String): KnowledgeLedger =
        runCatching { knowledgeRepository?.read(romIdentity) }.getOrNull() ?: KnowledgeLedger()

    private fun persistKnowledge(ledger: KnowledgeLedger) {
        val romIdentity = catalog?.romSha256 ?: return
        runCatching { knowledgeRepository?.write(romIdentity, ledger) }
    }

    private fun notifyCompletion(callback: ((Result<Unit>) -> Unit)?, result: Result<Unit>) {
        if (callback != null) runCatching { callback(result) }
    }

    @Synchronized
    private fun beginCatalogTransition(romSha256: String?, name: String? = null, phase: String): Long {
        val generation = loadGeneration.incrementAndGet()
        catalog = null
        liveParty = null
        liveGameState = null
        savedPlayerState = null
        settingsRomSha256 = null
        settingsWritesEnabled = false
        clearLevelUpRulesetDetection()
        gateway.dispatch(
            CompanionAction.CatalogLoadingChanged(
                CatalogLoadingState(active = true, phase = phase, completedUnits = 0, totalUnits = 5),
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
                    totalUnits = if (phase == "IDLE") 0 else 5,
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
        restoreKnowledge(parsed.romSha256)
        onCatalogCommitted(parsed.romSha256, name)
        gateway.dispatch(
            CompanionAction.CatalogLoadingChanged(
                CatalogLoadingState(active = false, phase = "COMPLETE", completedUnits = 5, totalUnits = 5),
                name,
            ),
        )
        gateway.dispatch(CompanionAction.CatalogLoaded(name))
    }

    private fun applySettingsForRom(romSha256: String) {
        settingsForRom?.invoke(romSha256)?.let(::applySettings)
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

}
