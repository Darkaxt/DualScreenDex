package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogCacheDecision
import com.darkaxt.dualdex.catalog.CatalogRepository
import com.darkaxt.dualdex.catalog.CatalogSchema
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
import com.darkaxt.dualdex.battle.RuntimeMapPosition
import com.darkaxt.dualdex.battle.Gen3BattleUiMemoryLayout
import com.darkaxt.dualdex.battle.Gen3RuntimeMemoryLayout
import com.darkaxt.dualdex.battle.TargetMode
import com.enrpau.dualscreendex.companion.CompanionGateway
import com.enrpau.dualscreendex.companion.api.ApiViewBuilder
import com.enrpau.dualscreendex.companion.api.BootstrapView
import com.enrpau.dualscreendex.companion.api.DiagnosticView
import com.enrpau.dualscreendex.companion.api.RetroArchView
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.companion.api.SpecimenCollectionView
import com.enrpau.dualscreendex.companion.api.StateView
import com.enrpau.dualscreendex.companion.analysis.PartyAnalysis
import com.enrpau.dualscreendex.companion.analysis.PartyAnalyzer
import com.enrpau.dualscreendex.companion.map.AreaGuideBuilder
import com.enrpau.dualscreendex.companion.map.AreaGuideProjection
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
import com.enrpau.dualscreendex.companion.model.OwnedIndividualLocation
import com.enrpau.dualscreendex.companion.model.OwnedIndividualLocationKind
import com.enrpau.dualscreendex.companion.model.PokedexFilter
import com.enrpau.dualscreendex.companion.model.ResolvedOwnedIndividual
import com.enrpau.dualscreendex.companion.model.Theme
import com.enrpau.dualscreendex.companion.model.TrainerCardState
import com.enrpau.dualscreendex.companion.model.ResolvedPokedexProjection
import com.enrpau.dualscreendex.companion.knowledge.SaveKnowledgeMapper
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveByteSelector
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.live.ResolvedGameSnapshot
import com.darkaxt.dualdex.live.TransientGameStateSource
import com.darkaxt.dualdex.live.ResolvedGameSection
import com.darkaxt.dualdex.live.ResolvedGameStateUpdate
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
import com.darkaxt.dualdex.performance.PerformanceRecorder
import com.darkaxt.dualdex.progress.ChallengeContext
import com.darkaxt.dualdex.progress.ChallengeDefinition
import com.darkaxt.dualdex.progress.ChallengeEngine
import com.darkaxt.dualdex.progress.ChallengeEvaluation
import com.darkaxt.dualdex.progress.ChallengeCategory
import com.darkaxt.dualdex.progress.PlaythroughJournalRegistry
import com.darkaxt.dualdex.progress.ResolvedSemanticFactProjector
import com.darkaxt.dualdex.progress.TrainerProgressProjector
import com.enrpau.dualscreendex.companion.api.TrainerProgressView
import com.enrpau.dualscreendex.companion.map.AreaGuideObjective
import com.enrpau.dualscreendex.companion.semantic.GameEvent
import com.enrpau.dualscreendex.companion.semantic.PlaythroughKey
import com.enrpau.dualscreendex.companion.semantic.SemanticFactSet
import com.enrpau.dualscreendex.companion.semantic.SnapshotTransitionEvaluator
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureNanoTime

data class ResolvedStateDispatchMetrics(
    val publications: Long,
    val recoverySections: Long,
    val playerSections: Long,
    val partySections: Long,
    val overworldSections: Long,
    val battleSections: Long,
)

private fun AreaGuideProjection.retainedItemCount(): Int = guide.areas.sumOf { area ->
    area.overview.exits.size +
        area.encounters.sumOf { it.species.size } +
        area.placesAndServices.size +
        area.trainersAndPeople.size +
        area.items.size +
        area.objectives.size
}

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
    private val performanceRecorder: PerformanceRecorder = PerformanceRecorder(),
    private val appVersion: String? = null,
    private val journalRegistry: PlaythroughJournalRegistry = PlaythroughJournalRegistry(),
    private val challengeDefinitions: List<ChallengeDefinition> = emptyList(),
    internal val transientGameState: TransientGameStateSource,
) : AutoCloseable {
    private var catalog: ParsedCatalog? = null
    @Volatile private var settingsRomSha256: String? = null
    @Volatile private var settingsWritesEnabled = true
    @Volatile private var retroArch = RetroArchView()
    @Volatile private var saveRam = SaveRamView()
    @Volatile private var catalogLoadingMessage: String? = null
    private var detectedLevelUpRulesetId: String? = null
    private var levelUpRulesetDetectionResolved = false
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
    private var observedGatewayVersion = gateway.bootstrap().version
    private var deliveryVersion = observedGatewayVersion
    @Volatile private var resolvedGameState: ResolvedGameSnapshot? = null
    private var lastRecoveryApplicationId: Long? = null
    private val resolvedPublications = AtomicLong()
    private val resolvedRecoverySections = AtomicLong()
    private val resolvedPlayerSections = AtomicLong()
    private val resolvedPartySections = AtomicLong()
    private val resolvedOverworldSections = AtomicLong()
    private val resolvedBattleSections = AtomicLong()
    private val partyAnalysisRecomputations = AtomicLong()
    private val partyAnalysisCpuNanos = AtomicLong()
    private val areaGuideProjections = AtomicLong()
    private val areaGuideProjectionCpuNanos = AtomicLong()
    private val areaGuideRetainedItems = AtomicLong()
    private val progressSemanticEvaluations = AtomicLong()
    private val progressSemanticCpuNanos = AtomicLong()
    private val progressEvents = AtomicLong()
    private val progressChallengeEvaluations = AtomicLong()
    private val progressChallengeCpuNanos = AtomicLong()
    private val challengeEngine = ChallengeEngine()
    private val challengeEvaluations = linkedMapOf<PlaythroughKey, ChallengeEvaluation>()
    private val challengeCapabilities = linkedMapOf<PlaythroughKey, Set<String>>()
    private val pendingProgressPreferences = linkedMapOf<String, String>()
    private var semanticBaseline: SemanticFactSet? = null
    private var battleEpoch = 0L
    private var battleWasActive = false
    private val transientGameStateSubscription = transientGameState.subscribe { update ->
        applyResolvedGameState(update)
    }

    @Synchronized
    private fun applyResolvedGameState(update: ResolvedGameStateUpdate) {
        val snapshot = update.snapshot
        val changed = update.changedSections
        resolvedPublications.incrementAndGet()
        resolvedGameState = snapshot
        if (ResolvedGameSection.RECOVERY in changed) resolvedRecoverySections.incrementAndGet()
        if (ResolvedGameSection.PLAYER in changed) resolvedPlayerSections.incrementAndGet()
        if (ResolvedGameSection.PARTY in changed) resolvedPartySections.incrementAndGet()
        if (ResolvedGameSection.OVERWORLD in changed) resolvedOverworldSections.incrementAndGet()
        if (ResolvedGameSection.BATTLE in changed) resolvedBattleSections.incrementAndGet()

        val currentCatalog = catalog ?: return
        val matching = snapshot?.takeIf { state ->
            currentCatalog.romSha256.equals(state.romIdentity, ignoreCase = true)
        }
        val before = gateway.bootstrap()
        var ledger = resolvedRecoveryLedger(matching, currentCatalog, before.ledger)
        val player = resolvedPlayerProjection(matching)
        val party = resolvedPartyProjection(matching, currentCatalog)
        if (!ledger.trainerCardUnlocked && party.party.any { !it.isEgg }) {
            ledger = ledger.copy(trainerCardUnlocked = true)
        }
        ledger = resolvedBattleKnowledge(matching, currentCatalog, ledger)
        val overworld = resolvedOverworldProjection(matching, currentCatalog, ledger)
        val liveBattle = matching?.battle?.value
        if (liveBattle != null) {
            if (liveBattle.active && !battleWasActive) battleEpoch++
            battleWasActive = liveBattle.active
        }
        val battle = liveBattle?.sample
            ?.takeIf { liveBattle.active }
            ?.let { sample -> battleState(sample, overworld.ledger, overworld.areaBaseId, currentCatalog) }

        matching?.let { resolved ->
            var projected: Pair<PlaythroughKey, List<GameEvent>>? = null
            progressSemanticCpuNanos.addAndGet(measureNanoTime {
                ResolvedSemanticFactProjector.project(resolved, overworld.ledger, battleEpoch)?.let { facts ->
                    val transition = SnapshotTransitionEvaluator.evaluate(semanticBaseline, facts)
                    semanticBaseline = transition.baseline
                    projected = facts.playthrough to transition.events
                }
            })
            projected?.let { (playthrough, events) ->
                progressSemanticEvaluations.incrementAndGet()
                progressEvents.addAndGet(events.size.toLong())
                val saveEvents = events.filterIsInstance<GameEvent.SaveObserved>()
                journalRegistry.accept(playthrough, events - saveEvents.toSet())
                updateChallenges(playthrough, resolved, events)
                journalRegistry.accept(playthrough, saveEvents)
            }
        }

        gateway.dispatch(
            CompanionAction.ResolvedGameStateChanged(
                trainerCard = player.trainerCard,
                pokedex = player.pokedex,
                party = party.party,
                owned = party.owned,
                bag = party.bag,
                eventFlags = party.eventFlags,
                areaBaseId = overworld.areaBaseId,
                position = overworld.position,
                gameTime = overworld.gameTime,
                gameAccessReady = overworld.gameAccessReady,
                battle = battle,
                ledger = overworld.ledger,
                ownedIndividuals = party.ownedIndividuals,
                saveIdentity = matching?.recovery?.saveIdentity,
            ),
        )
    }

    private fun resolvedRecoveryLedger(
        matching: ResolvedGameSnapshot?,
        currentCatalog: ParsedCatalog,
        currentLedger: KnowledgeLedger,
    ): KnowledgeLedger {
        matching ?: return currentLedger
        matching.recovery.saveRam?.let { state ->
            if (saveRam != state) {
                saveRam = state
                cachedState = null
            }
        }
        val applicationId = matching.recovery.applicationId ?: return currentLedger
        if (applicationId == lastRecoveryApplicationId) return currentLedger
        val seed = if (matching.recovery.resetKnowledge) {
            KnowledgeLedgerSanitizer.sanitize(
                matching.recovery.checkpointLedger ?: KnowledgeLedger(),
                currentCatalog,
            )
        } else {
            currentLedger
        }
        detectedLevelUpRulesetId = matching.levelUpRulesetId.value
        levelUpRulesetDetectionResolved = matching.levelUpRulesetId.value != null
        cachedBattleCatalogContext = null
        cachedState = null
        lastRecoveryApplicationId = applicationId
        return KnowledgeLedgerSanitizer.sanitize(seed, currentCatalog)
    }

    private fun resolvedPlayerProjection(matching: ResolvedGameSnapshot?): ResolvedPlayerProjection {
        val playTime = matching?.trainer?.playTime?.value
        val trainerCard = matching?.let { state ->
            TrainerCardState(
                identity = state.trainer.identity.value,
                publicTrainerId = state.trainer.publicTrainerId.value,
                money = state.trainer.money.value,
                playTimeHours = playTime?.hours,
                playTimeMinutes = playTime?.minutes,
                badgeFlags = state.trainer.badgeFlags.value,
                dexSeen = state.pokedex.seenSpeciesIds.value?.size,
                dexCaught = state.pokedex.caughtSpeciesIds.value?.size,
                stars = state.trainer.stars.value,
            )
        }
        val pokedex = ResolvedPokedexProjection(
            seenSpeciesIds = matching?.pokedex?.seenSpeciesIds?.value,
            caughtSpeciesIds = matching?.pokedex?.caughtSpeciesIds?.value,
        )
        return ResolvedPlayerProjection(trainerCard, pokedex)
    }

    private fun resolvedBattleKnowledge(
        matching: ResolvedGameSnapshot?,
        currentCatalog: ParsedCatalog,
        ledger: KnowledgeLedger,
    ): KnowledgeLedger {
        val knowledge = matching?.battleKnowledge ?: return ledger
        val observed = knowledge.observedMoves.mapValues { (_, frequencies) ->
            frequencies.entries
                .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
                .map { MoveObservation(it.key, it.value) }
        }
        val discoveredMatchups = knowledge.recoveredMatchups.toMutableMap()
        knowledge.discoveredMatchups.forEach { observation ->
            effectivenessFor(currentCatalog, observation.moveId, observation.defendingTypeIds)?.let { effectiveness ->
                discoveredMatchups[MatchupKey(observation.speciesId, observation.moveId)] = effectiveness
            }
        }
        return ledger.copy(
            seenSpecies = knowledge.seenSpeciesIds,
            seenSpeciesByArea = knowledge.seenSpeciesByArea,
            observedMoves = observed,
            discoveredMatchups = discoveredMatchups,
        )
    }

    private fun resolvedPartyProjection(
        matching: ResolvedGameSnapshot?,
        currentCatalog: ParsedCatalog,
    ): ResolvedPartyProjection {
        val party = matching?.party?.value.orEmpty()
        val ownedIndividuals = buildList {
            party.forEachIndexed { slotIndex, individual ->
                add(
                    ResolvedOwnedIndividual(
                        individual,
                        OwnedIndividualLocation(OwnedIndividualLocationKind.PARTY, slotIndex = slotIndex),
                    ),
                )
            }
            matching?.ownedStorage?.boxes?.value.orEmpty().forEach { box ->
                box.slots.forEach { slot ->
                    add(
                        ResolvedOwnedIndividual(
                            slot.individual,
                            OwnedIndividualLocation(
                                OwnedIndividualLocationKind.BOX,
                                boxIndex = box.index,
                                slotIndex = slot.index,
                            ),
                        ),
                    )
                }
            }
        }
        val partyKeys = party.mapTo(mutableSetOf(), OwnedIndividual::stableLocation)
        val owned = (matching?.storedIndividuals?.value.orEmpty() + party)
            .distinctBy(OwnedIndividual::stableLocation)
            .mapNotNull { individual ->
                if (individual.speciesId !in currentCatalog.speciesById) return@mapNotNull null
                com.enrpau.dualscreendex.companion.model.OwnedPokemon(
                    stableKey = individual.stableLocation,
                    speciesId = individual.speciesId,
                    generation = matching?.generation ?: 0,
                    level = individual.level ?: 0,
                    ivs = individual.ivs.orEmpty(),
                    dvs = individual.dvs.orEmpty(),
                    captureBallId = individual.captureBallId,
                    isEgg = individual.isEgg,
                    party = individual.stableLocation in partyKeys,
                )
            }
        val bag = matching?.bag.orEmpty().mapNotNull { (pocket, value) ->
            value.value?.let { pocket to it }
        }.toMap()
        val eventFlags = matching?.eventFlags?.value
        return ResolvedPartyProjection(party, owned, ownedIndividuals, bag, eventFlags)
    }

    private fun resolvedOverworldProjection(
        matching: ResolvedGameSnapshot?,
        currentCatalog: ParsedCatalog,
        initialLedger: KnowledgeLedger,
    ): ResolvedOverworldProjection {
        val areaBaseId = matching?.location?.areaBaseId?.value
        val position = matching?.location?.position?.value?.let { value -> LiveMapPosition(value.x, value.y) }
        val clock = matching?.clock?.value
        val hours = clock?.hours
        val minutes = clock?.minutes
        val phase = clock?.phase
        val schedule = currentCatalog.runtimeMetadata.gen3RuntimeMemoryLayout?.liveClockSchedule
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
        if (ready) performanceRecorder.gameAccessReady()
        val validAreaBaseId = areaBaseId?.takeIf { candidate ->
            candidate in currentCatalog.discoverableAreaBaseIds()
        }
        var ledger = initialLedger
        if (validAreaBaseId != null && validAreaBaseId !in ledger.visitedAreaBaseIds) {
            ledger = ledger.copy(visitedAreaBaseIds = ledger.visitedAreaBaseIds + validAreaBaseId)
        }
        if (validAreaBaseId != null && position != null) {
            ledger = LocalMapPoiKnowledgeMapper.mergeProximity(
                previous = ledger,
                catalog = currentCatalog,
                baseAreaId = validAreaBaseId,
                tileX = position.x,
                tileY = position.y,
            )
        }
        return ResolvedOverworldProjection(areaBaseId, position, gameTime, ready, ledger)
    }

    private data class ResolvedPlayerProjection(
        val trainerCard: TrainerCardState?,
        val pokedex: ResolvedPokedexProjection,
    )

    private data class ResolvedPartyProjection(
        val party: List<OwnedIndividual>,
        val owned: List<com.enrpau.dualscreendex.companion.model.OwnedPokemon>,
        val ownedIndividuals: List<ResolvedOwnedIndividual>,
        val bag: Map<BagPocket, com.darkaxt.dualdex.save.BagPocketSnapshot>,
        val eventFlags: Set<Int>?,
    )

    private data class ResolvedOverworldProjection(
        val areaBaseId: Int?,
        val position: LiveMapPosition?,
        val gameTime: GameClock?,
        val gameAccessReady: Boolean,
        val ledger: KnowledgeLedger,
    )

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

    fun recordRomSourceLoadFailure(romSha256: String, failure: Throwable) {
        performanceRecorder.beginLoad(romSha256, generation = null)
        performanceRecorder.transitionStage("ROM_SOURCE")
        performanceRecorder.loadFailed(failure)
    }

    @Synchronized
    fun cancelPendingCatalogLoad() {
        if (!gateway.bootstrap().catalogLoading.active) return
        val generation = loadGeneration.incrementAndGet()
        publishTransitionFailure(generation, "IDLE")
    }

    private fun loadInternal(name: String, rom: RomImage, onComplete: ((Result<Unit>) -> Unit)?) {
        if (activeCatalogMatches(rom.sha256)) {
            notifyCompletion(onComplete, Result.success(Unit))
            return
        }
        val header = RomHeaderReader.read(rom)
        val source = CatalogSourceMetadata.fromDisplayName(name, rom.size, header.title)
        performanceRecorder.beginLoad(rom.sha256, generationFor(header.platform))
        val generation = beginCatalogTransition(rom.sha256, name, "CACHE_REOPEN")
        parserWorker.execute {
            try {
                val lookup = catalogRepository?.lookupComplete(rom.sha256)
                val cached = lookup?.stored?.takeIf { stored ->
                    stored.catalog.matchesIdentity(rom.sha256)
                }
                val cacheDecision = if (lookup?.stored != null && cached == null) {
                    CatalogCacheDecision.REJECTED_EXCEPTION
                } else {
                    lookup?.decision ?: CatalogCacheDecision.MISS_FILE_ABSENT
                }
                performanceRecorder.cacheDecision(cacheDecision.name)
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
                    cacheRefreshMessage(cacheDecision),
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
            } catch (failure: OutOfMemoryError) {
                failCatalogLoad(generation, onComplete, failure)
            } catch (failure: Exception) {
                failCatalogLoad(generation, onComplete, failure)
            }
        }
    }

    private fun failCatalogLoad(
        generation: Long,
        onComplete: ((Result<Unit>) -> Unit)?,
        failure: Throwable,
    ) {
        val publicFailure = GuideLoadFailure.from(failure)
        runCatching { performanceRecorder.loadFailed(failure) }
        publishTransitionFailure(generation, "FAILED", publicFailure.message)
        notifyCompletion(onComplete, Result.failure(publicFailure))
    }

    /** Test and cache-reopen seam; Stage 2 will use this for persisted catalogs. */
    @Synchronized
    fun loadCatalog(name: String, parsed: ParsedCatalog) {
        performanceRecorder.beginLoad(parsed.romSha256, generationFor(parsed.family))
        performanceRecorder.cacheDecision(CatalogCacheDecision.HIT.name)
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
        performanceRecorder.catalogReady()
        performanceRecorder.waitingForGameAccess()
    }

    @Synchronized
    fun restoreCatalog(sha256: String): Boolean {
        performanceRecorder.beginLoad(sha256, null)
        val stored = catalogRepository?.readComplete(sha256)?.takeIf { candidate ->
            candidate.catalog.matchesIdentity(sha256)
        }
        if (stored == null) {
            performanceRecorder.cacheDecision(CatalogCacheDecision.MISS_FILE_ABSENT.name)
            performanceRecorder.loadFailed(IllegalStateException("stored catalog was unavailable"))
            val generation = beginCatalogTransition(null, phase = "CACHE_REOPEN")
            publishTransitionFailure(generation, "IDLE")
            return false
        }
        performanceRecorder.cacheDecision(CatalogCacheDecision.HIT.name)
        val generation = beginCatalogTransition(stored.catalog.romSha256, stored.source.displayName, "CACHE_REOPEN")
        publishReopened(generation, stored.source.displayName, stored.catalog)
        return true
    }

    fun restoreCatalogAsync(sha256: String) {
        performanceRecorder.beginLoad(sha256, null)
        val generation = beginCatalogTransition(sha256, null, "CACHE_REOPEN")
        parserWorker.execute {
            try {
                val stored = catalogRepository?.readComplete(sha256)?.takeIf { candidate ->
                    candidate.catalog.matchesIdentity(sha256)
                }
                if (stored == null) {
                    performanceRecorder.cacheDecision(CatalogCacheDecision.MISS_FILE_ABSENT.name)
                    performanceRecorder.loadFailed(IllegalStateException("stored catalog was unavailable"))
                    publishTransitionFailure(generation, "IDLE")
                } else {
                    performanceRecorder.cacheDecision(CatalogCacheDecision.HIT.name)
                    publishReopened(generation, stored.source.displayName, stored.catalog)
                }
            } catch (failure: OutOfMemoryError) {
                failCatalogRestore(generation, failure)
            } catch (failure: Exception) {
                failCatalogRestore(generation, failure)
            }
        }
    }

    private fun ParsedCatalog.matchesIdentity(sha256: String): Boolean =
        romSha256.equals(sha256, ignoreCase = true)

    private fun failCatalogRestore(generation: Long, failure: Throwable) {
        val publicFailure = GuideLoadFailure.from(failure)
        runCatching { performanceRecorder.loadFailed(failure) }
        publishTransitionFailure(generation, "FAILED", publicFailure.message)
    }

    @Synchronized
    fun bootstrap(): BootstrapView {
        val snapshot = gateway.bootstrap()
        return BootstrapView(catalog?.let(ApiViewBuilder::catalog), stateView(snapshot))
    }

    @Synchronized
    fun stateView(snapshot: AppSnapshot = gateway.bootstrap()): StateView {
        observeGatewayVersion(snapshot.version)
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
        var partyAnalysis: PartyAnalysis? = null
        var areaGuideProjection: AreaGuideProjection? = null
        val trainerProgress = trainerProgress(snapshot)
        if (currentCatalog != null) {
            partyAnalysisCpuNanos.addAndGet(
                measureNanoTime {
                    partyAnalysis = PartyAnalyzer.analyze(snapshot.party, currentCatalog, active?.id)
                },
            )
            partyAnalysisRecomputations.incrementAndGet()
            areaGuideProjectionCpuNanos.addAndGet(
                measureNanoTime {
                    areaGuideProjection = AreaGuideBuilder.project(
                        currentCatalog,
                        snapshot,
                        objectivesByArea = progressObjectives(snapshot, trainerProgress),
                    )
                },
            )
            areaGuideProjections.incrementAndGet()
            areaGuideRetainedItems.set(areaGuideProjection?.retainedItemCount()?.toLong() ?: 0L)
        }
        return ApiViewBuilder.state(
            snapshot,
            currentCatalog,
            truth = battleTruth(snapshot, currentCatalog),
            activeRulesetId = active?.id,
            rulesetAssumed = snapshot.settings.ruleset == "AUTO" && !levelUpRulesetDetectionResolved,
            retroArch = retroArch,
            saveRam = saveRam,
            partyAnalysis = partyAnalysis,
            areaGuideProjection = areaGuideProjection,
            trainerProgress = trainerProgress,
            version = deliveryVersion,
        ).also { view -> cachedState = CachedState(snapshot.version, currentCatalog, retroArch, saveRam, view) }
    }

    @Synchronized
    fun specimens(speciesId: Int): SpecimenCollectionView = ApiViewBuilder.specimens(
        snapshot = gateway.bootstrap(),
        catalog = requireNotNull(catalog) { "game guide is unavailable" },
        speciesId = speciesId,
    )

    @Synchronized
    fun updateRetroArch(state: RetroArchView) {
        if (retroArch == state) return
        retroArch = state
        advanceDeliveryVersion()
    }

    fun retroArchState(): RetroArchView = retroArch

    @Synchronized
    fun updateSaveRam(state: SaveRamView) {
        if (saveRam == state) return
        saveRam = state
        advanceDeliveryVersion()
    }

    private fun observeGatewayVersion(version: Long) {
        if (version <= observedGatewayVersion) return
        deliveryVersion += version - observedGatewayVersion
        observedGatewayVersion = version
        cachedState = null
    }

    private fun advanceDeliveryVersion() {
        deliveryVersion++
        cachedState = null
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
        val pokedexFlagNumbers = SaveKnowledgeMapper.pokedexFlagNumbersBySpeciesId(current)
        return SaveParseContext(
        romIdentity = current.romSha256,
        speciesById = current.speciesById.mapValues { (id, species) ->
            SaveSpeciesContext(
                speciesId = id,
                dexNumber = species.dexNumber.value,
                growthRate = species.growthRate.value,
                pokedexFlagNumber = pokedexFlagNumbers[id],
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
        cachedBattleCatalogContext?.let { cached ->
            if (cached.catalog === current) return cached.value
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
        val value = BattleCatalogContext(
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
                    saveBlock1Address = layout.saveBlock1Address,
                    saveBlock2Address = layout.saveBlock2Address,
                    saveBlock1PointerAddress = layout.saveBlock1PointerAddress,
                    saveBlock2PointerAddress = layout.saveBlock2PointerAddress,
                    pokemonStorageAddress = layout.pokemonStorageAddress,
                    pokemonStoragePointerAddress = layout.pokemonStoragePointerAddress,
                    pokemonStorageBoxCount = layout.pokemonStorageBoxCount,
                    pokemonStorageBoxCapacity = layout.pokemonStorageBoxCapacity,
                    pokemonStorageRecordSize = layout.pokemonStorageRecordSize,
                    pokemonStorageRecordsOffset = layout.pokemonStorageRecordsOffset,
                    saveBlock1Size = layout.saveRuntimeAbi?.saveBlock1Size,
                    saveBlock2Size = layout.saveRuntimeAbi?.saveBlock2Size,
                    extendedSaveAddress = layout.extendedSaveAddress,
                    extendedSaveSize = layout.saveRuntimeAbi?.extendedSaveDataSize?.takeIf { it > 0 },
                )
            },
            liveAreaMemoryLayout = liveAreaMemoryLayout(current.family),
            saveParseContext = saveParseContext(current),
        )
        cachedBattleCatalogContext = CachedBattleCatalogContext(current, value)
        return value
    }

    private fun battleState(
        sample: BattleMemorySample,
        ledger: KnowledgeLedger,
        areaBaseId: Int?,
        currentCatalog: ParsedCatalog,
    ): BattleState {
        val opponents = sample.opponents.map { opponent ->
            OpponentState(
                speciesId = opponent.speciesId,
                level = opponent.level,
                typeIds = opponent.typeIds,
                ivs = opponent.ivs,
                dvs = opponent.dvs,
                moveHistory = ledger.observedMoves[opponent.speciesId].orEmpty(),
            )
        }
        return BattleState(
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
                val generation = when (currentCatalog.family) {
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
                    currentAreaBaseId = areaBaseId,
                    encounterAreas = currentCatalog.encounterAreas,
                )
                assessment.innateTier != null && assessment.stars != null
            } ?: false,
        )
    }

    fun battlePollingIntervalMs(): Int = gateway.bootstrap().settings.battlePollingIntervalMs.coerceIn(1, 20)

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
            "TRAINER_DESTINATION" -> updateProgressPreference("trainer-destination", values, setOf("CARD", "PROGRESS"))
            "PROGRESS_SECTION" -> updateProgressPreference(
                "trainer-progress-section",
                values,
                setOf("METRICS", "CHALLENGES", "TIMELINE"),
            )
            "SETTINGS" -> updateSettings(values)
            "TAB", "BATTLE_TAB" -> gateway.dispatch(CompanionAction.SetBattleTab(BattleTab.valueOf(requireNotNull(values["tab"]).uppercase())))
            "TARGET", "SELECT_TARGET" -> gateway.dispatch(CompanionAction.SelectTarget(requireInt(values, "index")))
            else -> throw IllegalArgumentException("unknown production action: $type")
        }
        return stateView()
    }

    private fun updateProgressPreference(key: String, values: Map<String, String?>, accepted: Set<String>) {
        val value = requireNotNull(values["value"]).uppercase()
        require(value in accepted) { "invalid progress selection" }
        val playthrough = activePlaythrough()
        if (playthrough == null) pendingProgressPreferences[key] = value
        else journalRegistry.updatePreferences(playthrough, mapOf(key to value))
        cachedState = null
    }

    private fun activePlaythrough(): PlaythroughKey? {
        val resolved = resolvedGameState ?: return null
        val saveIdentity = resolved.recovery.saveIdentity ?: return null
        return PlaythroughKey(resolved.romIdentity.lowercase(), saveIdentity.lowercase())
    }

    private fun updateChallenges(
        playthrough: PlaythroughKey,
        resolved: ResolvedGameSnapshot,
        events: List<GameEvent>,
    ) {
        if (challengeDefinitions.isEmpty()) return
        val journal = journalRegistry.current(playthrough)
        val capabilities = progressCapabilities(resolved)
        val priorCapabilities = challengeCapabilities.put(playthrough, capabilities)
        val changedDependencies = if (priorCapabilities != capabilities || challengeEvaluations[playthrough] == null) {
            null
        } else {
            events.flatMapTo(linkedSetOf()) { event ->
                when (event) {
                    is GameEvent.Captured -> listOf("metric:captures")
                    is GameEvent.Evolved -> listOf("metric:evolutions")
                    is GameEvent.AreaVisited -> listOf("metric:areas")
                    is GameEvent.PoiDiscovered -> listOf("metric:pois")
                    is GameEvent.BattleStarted -> listOf("metric:battles")
                    is GameEvent.SaveObserved -> challengeDefinitions.flatMap { it.predicate.dependencies() }
                    is GameEvent.BattleEnded,
                    is GameEvent.PartyChanged,
                    -> emptyList()
                }
            }
        }
        if (changedDependencies != null && changedDependencies.isEmpty()) return
        val saveFingerprint = events.filterIsInstance<GameEvent.SaveObserved>().lastOrNull()?.fingerprint
        lateinit var evaluation: ChallengeEvaluation
        progressChallengeCpuNanos.addAndGet(measureNanoTime {
            evaluation = challengeEngine.evaluate(
                definitions = challengeDefinitions,
                context = ChallengeContext(
                    metrics = journal.trackedCounts,
                    capabilities = capabilities,
                    organicMode = gateway.bootstrap().settings.knowledgeMode == KnowledgeMode.ORGANIC,
                ),
                priorStates = journal.challengeStates,
                changedDependencies = changedDependencies,
                nowEpochMs = System.currentTimeMillis(),
                saveFingerprint = saveFingerprint,
            )
        })
        progressChallengeEvaluations.incrementAndGet()
        journalRegistry.updateChallengeStates(playthrough, evaluation.states)
        challengeEvaluations[playthrough] = evaluation
        cachedState = null
    }

    private fun progressCapabilities(resolved: ResolvedGameSnapshot): Set<String> = buildSet {
        if (resolved.pokedex.caughtSpeciesIds.value != null) add("POKEDEX_FACTS")
        if (resolved.party.value != null && resolved.storedIndividuals.value != null) add("OWNED_INDIVIDUALS")
        if (resolved.location.areaBaseId.value != null) add("LOCATION_FACTS")
        if (catalog?.localMaps?.pois?.isNotEmpty() == true) add("POI_FACTS")
        if (resolved.battle.value != null) add("BATTLE_FACTS")
    }

    private fun trainerProgress(snapshot: AppSnapshot): TrainerProgressView? {
        val playthrough = activePlaythrough()
        if (playthrough == null) {
            if (!snapshot.ledger.trainerCardUnlocked) return null
            val rom = catalog?.romSha256 ?: return null
            return TrainerProgressProjector.project(
                snapshot,
                com.darkaxt.dualdex.progress.PlaythroughJournal.empty(PlaythroughKey(rom, "0".repeat(64))).copy(
                    preferences = pendingProgressPreferences.toMap(),
                ),
                ChallengeEvaluation(emptyList(), emptyMap()),
            )
        }
        if (pendingProgressPreferences.isNotEmpty()) {
            journalRegistry.updatePreferences(playthrough, pendingProgressPreferences.toMap())
            pendingProgressPreferences.clear()
        }
        val journal = journalRegistry.current(playthrough)
        val evaluation = challengeEvaluations[playthrough] ?: ChallengeEvaluation(emptyList(), journal.challengeStates)
        return TrainerProgressProjector.project(snapshot, journal, evaluation)
    }

    private fun progressObjectives(
        snapshot: AppSnapshot,
        progress: TrainerProgressView?,
    ): Map<Int, List<AreaGuideObjective>> {
        val area = snapshot.liveAreaBaseId ?: return emptyMap()
        val exploration = progress?.challenges.orEmpty()
            .filter { it.category == ChallengeCategory.EXPLORATION.name && !it.complete }
            .map { AreaGuideObjective(it.key, it.title) }
        return if (exploration.isEmpty()) emptyMap() else mapOf(area to exploration)
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

    fun resolvedStateDispatchMetrics() = ResolvedStateDispatchMetrics(
        publications = resolvedPublications.get(),
        recoverySections = resolvedRecoverySections.get(),
        playerSections = resolvedPlayerSections.get(),
        partySections = resolvedPartySections.get(),
        overworldSections = resolvedOverworldSections.get(),
        battleSections = resolvedBattleSections.get(),
    )

    fun performanceCounters(): Map<String, Long> = mapAssetRenderCache.stats().let { stats ->
        val state = resolvedStateDispatchMetrics()
        val dispatch = gateway.metrics()
        val journal = activePlaythrough()?.let(journalRegistry::current)
        mapOf(
            "mapCache.entries" to stats.entries.toLong(),
            "mapCache.encodedBytes" to stats.encodedBytes.toLong(),
            "mapCache.hits" to stats.hits,
            "mapCache.renders" to stats.renders,
            "mapCache.evictions" to stats.evictions,
            "state.publications" to state.publications,
            "state.sections.recovery" to state.recoverySections,
            "state.sections.player" to state.playerSections,
            "state.sections.party" to state.partySections,
            "state.sections.overworld" to state.overworldSections,
            "state.sections.battle" to state.battleSections,
            "state.dispatch.attempts" to dispatch.dispatchAttempts,
            "state.dispatch.applied" to dispatch.appliedDispatches,
            "state.dispatch.noOps" to dispatch.noOpDispatches,
            "analysis.party.recomputations" to partyAnalysisRecomputations.get(),
            "analysis.party.cpuNanos" to partyAnalysisCpuNanos.get(),
            "areaGuide.projections" to areaGuideProjections.get(),
            "areaGuide.projectionCpuNanos" to areaGuideProjectionCpuNanos.get(),
            "areaGuide.retainedItems" to areaGuideRetainedItems.get(),
            "progress.semanticEvaluations" to progressSemanticEvaluations.get(),
            "progress.semanticCpuNanos" to progressSemanticCpuNanos.get(),
            "progress.events" to progressEvents.get(),
            "progress.challengeEvaluations" to progressChallengeEvaluations.get(),
            "progress.challengeCpuNanos" to progressChallengeCpuNanos.get(),
            "progress.journalEntries" to (journal?.timeline?.size?.toLong() ?: 0L),
            "progress.journalRetainedItems" to (journal?.retainedItemCount()?.toLong() ?: 0L),
        ) + transientGameState.performanceCounters()
    }

    fun runtimePerformanceHeartbeat() = performanceRecorder.runtimeHeartbeat()

    @Synchronized
    fun trainerAsset(key: String) = catalog?.trainerAssets?.assets?.get(key)

    @Synchronized
    fun catalogHash(): String? = catalog?.romSha256

    @Synchronized
    fun diagnostics(speciesId: Int?, moveId: Int?): DiagnosticView {
        val current = requireNotNull(catalog) { "load a ROM before requesting diagnostics" }
        val snapshot = gateway.bootstrap()
        val active = resolveRuleset(snapshot.settings.ruleset)
        val base = ApiViewBuilder.diagnostics(
            current,
            snapshot.catalogName,
            active?.id,
            snapshot.settings.ruleset == "AUTO" && !levelUpRulesetDetectionResolved,
            speciesId,
            moveId,
        )
        return CompatibilityReportBuilder.build(
            base = base,
            catalog = current,
            state = stateView(snapshot),
            cacheStats = mapAssetRenderCache.stats(),
            appVersion = appVersion,
            catalogSchemaVersion = CatalogSchema.version,
            parserSchemaVersion = CatalogSchema.parserSchemaVersion,
        )
    }

    fun exportCompatibilityReport(): ByteArray =
        CompatibilityReportSerializer.toBytes(diagnostics(speciesId = null, moveId = null))

    override fun close() {
        transientGameStateSubscription.close()
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
        performanceRecorder.transitionStage(work.module.name)
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
            performanceRecorder.catalogReady()
            performanceRecorder.waitingForGameAccess()
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
        lastRecoveryApplicationId = null
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
        saveRam = SaveRamView()
        gateway.dispatch(CompanionAction.ReplaceLedger(KnowledgeLedger()))
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
        performanceRecorder.catalogReady()
        performanceRecorder.waitingForGameAccess()
    }

    private fun generationFor(platform: Platform): Int? = when (platform) {
        Platform.GB -> 1
        Platform.GBC -> null
        Platform.GBA -> 3
        Platform.UNKNOWN -> null
    }

    private fun generationFor(family: EngineFamily): Int = when (family) {
        EngineFamily.RED_BLUE, EngineFamily.YELLOW -> 1
        EngineFamily.GOLD_SILVER, EngineFamily.CRYSTAL -> 2
        else -> 3
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

    private data class CachedSaveParseContext(
        val catalog: ParsedCatalog,
        val value: SaveParseContext,
    )

    private data class CachedBattleCatalogContext(
        val catalog: ParsedCatalog,
        val value: BattleCatalogContext?,
    )

    private fun clearCatalogProjectionCaches() {
        cachedSaveParseContext = null
        cachedBattleCatalogContext = null
    }

}
