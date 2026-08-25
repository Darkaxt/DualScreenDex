package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.battle.BattleEncounterKind
import com.darkaxt.dualdex.battle.BattleTrackingUpdate
import com.darkaxt.dualdex.battle.LiveBattleState
import com.darkaxt.dualdex.battle.LiveClockPhase
import com.darkaxt.dualdex.battle.LiveClockState
import com.darkaxt.dualdex.battle.LiveGameSnapshot
import com.darkaxt.dualdex.battle.LiveLocationState
import com.darkaxt.dualdex.battle.LivePokedexState
import com.darkaxt.dualdex.battle.LiveTrainerState
import com.darkaxt.dualdex.battle.LiveUnavailableCode
import com.darkaxt.dualdex.battle.LiveUnavailableReason
import com.darkaxt.dualdex.battle.LiveValue
import com.darkaxt.dualdex.battle.RuntimeMapPosition
import com.darkaxt.dualdex.catalog.CatalogRepository
import com.darkaxt.dualdex.live.RecoveryApplication
import com.darkaxt.dualdex.live.RecoveryProjection
import com.darkaxt.dualdex.live.TransientGameStateContext
import com.darkaxt.dualdex.live.UnifiedGameStateDecoder
import com.darkaxt.dualdex.performance.PerformanceRecorder
import com.darkaxt.dualdex.save.BagPocket
import com.darkaxt.dualdex.save.SaveObservation
import com.darkaxt.dualdex.save.SaveSnapshot
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.DisplayMode
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializationProgress
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.CatalogWorkProgress
import com.enrpau.dualscreendex.parser.catalog.LocalMapAssetRenderer
import com.enrpau.dualscreendex.parser.catalog.MapLighting
import com.enrpau.dualscreendex.parser.catalog.MapTimeOfDay
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.RenderedMapAsset
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import java.util.WeakHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Test-only factory. Production callers must explicitly inject the application-owned state source. */
internal fun ProductionCompanionRuntime(
    parserWorker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "dualdex-parser-test").apply { isDaemon = true }
    },
    catalogRepository: CatalogRepository? = null,
    onCatalogCommitted: (sha256: String, displayName: String) -> Unit = { _, _ -> },
    initialSettings: CompanionSettings = CompanionSettings(),
    onSettingsChanged: (CompanionSettings) -> Unit = {},
    settingsForRom: ((String) -> CompanionSettings)? = null,
    globalSettings: (() -> CompanionSettings)? = null,
    onRomSettingsChanged: (String?, CompanionSettings) -> Unit = { _, settings -> onSettingsChanged(settings) },
    onRomDisplayModeChanged: (DisplayMode) -> Unit = {},
    onCatalogCleared: () -> Unit = {},
    parseCatalog: (
        RomImage,
        (CatalogMaterializationProgress) -> Unit,
        (CatalogWorkProgress) -> Unit,
    ) -> ParsedCatalog? = { rom, progress, work ->
        CatalogParser.parseWithWork(rom, progress, work).catalog
    },
    mapAssetRenderer: (
        ParsedCatalog,
        String,
        MapLighting,
        MapTimeOfDay?,
    ) -> RenderedMapAsset? = { current, key, requestedLighting, time ->
        LocalMapAssetRenderer.render(current.localMaps, key, requestedLighting, time)
            ?: current.worldMaps.assets[key]?.let { RenderedMapAsset(PngEncoder.encode(it), null) }
    },
    mapAssetRenderCache: MapAssetRenderCache = MapAssetRenderCache(),
    performanceRecorder: PerformanceRecorder = PerformanceRecorder(),
): ProductionCompanionRuntime = ProductionCompanionRuntime(
    parserWorker = parserWorker,
    catalogRepository = catalogRepository,
    onCatalogCommitted = onCatalogCommitted,
    initialSettings = initialSettings,
    onSettingsChanged = onSettingsChanged,
    settingsForRom = settingsForRom,
    globalSettings = globalSettings,
    onRomSettingsChanged = onRomSettingsChanged,
    onRomDisplayModeChanged = onRomDisplayModeChanged,
    onCatalogCleared = onCatalogCleared,
    parseCatalog = parseCatalog,
    mapAssetRenderer = mapAssetRenderer,
    mapAssetRenderCache = mapAssetRenderCache,
    performanceRecorder = performanceRecorder,
    transientGameState = UnifiedGameStateDecoder(),
)

private data class RuntimeLiveFixture(
    var sampleId: Long = 0,
    var areaBaseId: Int? = null,
    var position: RuntimeMapPosition? = null,
    var clock: LiveClockState? = null,
)

private val runtimeLiveFixtures = WeakHashMap<ProductionCompanionRuntime, RuntimeLiveFixture>()

private fun ProductionCompanionRuntime.stateOwner(): UnifiedGameStateDecoder =
    transientGameState as? UnifiedGameStateDecoder
        ?: error("transient-state test scenarios require the production decoder")

private fun ProductionCompanionRuntime.beginTransientTestSession(): Pair<UnifiedGameStateDecoder, TransientGameStateContext> {
    val battleContext = requireNotNull(battleCatalogContext()) { "catalog must be loaded before transient state" }
    val context = TransientGameStateContext(
        romIdentity = battleContext.romIdentity,
        generation = battleContext.generation,
        catalog = battleContext.catalog,
        gen2TimeOfDayWramOffset = battleContext.gen2TimeOfDayWramOffset,
        gen3RuntimeMemoryLayout = battleContext.gen3RuntimeMemoryLayout,
        liveAreaMemoryLayout = battleContext.liveAreaMemoryLayout,
        saveParseContext = battleContext.saveParseContext,
    )
    return stateOwner().also { it.beginSession(context) } to context
}

internal fun ProductionCompanionRuntime.applySaveSnapshot(snapshot: SaveSnapshot, state: SaveRamView): Boolean {
    val (owner, _) = beginTransientTestSession()
    return owner.acceptRecovery(RecoveryProjection(snapshot = snapshot, saveRam = state)).accepted
}

internal fun ProductionCompanionRuntime.applySaveObservation(
    observation: SaveObservation,
    snapshot: SaveSnapshot,
    state: SaveRamView,
    checkpoint: KnowledgeLedger? = null,
): RecoveryApplication {
    val (owner, _) = beginTransientTestSession()
    val before = gateway.bootstrap().ledger
    val application = owner.acceptRecovery(
        RecoveryProjection(
            snapshot = snapshot,
            saveRam = state,
            observation = observation,
            checkpointLedger = checkpoint,
        ),
    )
    return if (
        observation.kind == com.darkaxt.dualdex.save.SaveObservationKind.CHANGED &&
        application.accepted
    ) {
        application.copy(checkpointLedger = before)
    } else {
        application
    }
}

internal fun ProductionCompanionRuntime.applyBattleThroughState(update: BattleTrackingUpdate) {
    val (owner, context) = beginTransientTestSession()
    owner.acceptBattleTracking(update)
    val fixture = runtimeLiveFixtures.getOrPut(this) { RuntimeLiveFixture() }
    owner.acceptDecodedLive(
        LiveGameSnapshot(
            romIdentity = context.romIdentity,
            generation = context.generation,
            sampleId = ++fixture.sampleId,
            trainer = unavailableTrainer(),
            pokedex = LivePokedexState(unavailable("seen unavailable"), unavailable("caught unavailable")),
            party = unavailable("party unavailable"),
            battle = LiveValue.Available(
                LiveBattleState(
                    active = update.active && !update.ended,
                    sample = update.sample,
                    encounterKind = update.sample?.encounterKind ?: BattleEncounterKind.UNKNOWN,
                ),
            ),
            location = LiveLocationState(
                fixture.areaBaseId?.let { LiveValue.Available(it) } ?: unavailable("area unavailable"),
                fixture.position?.let { LiveValue.Available(it) } ?: unavailable("position unavailable"),
            ),
            clock = fixture.clock?.let { LiveValue.Available(it) } ?: unavailable("clock unavailable"),
            bag = BagPocket.entries.associateWith { unavailable("bag unavailable") },
            eventFlags = unavailable("flags unavailable"),
        ),
    )
}

internal fun ProductionCompanionRuntime.updateLiveArea(areaBaseId: Int?) {
    runtimeLiveFixtures.getOrPut(this) { RuntimeLiveFixture() }.areaBaseId = areaBaseId
    publishFixtureState()
}

internal fun ProductionCompanionRuntime.updateLiveMapPosition(position: RuntimeMapPosition?) {
    runtimeLiveFixtures.getOrPut(this) { RuntimeLiveFixture() }.position = position
    publishFixtureState()
}

internal fun ProductionCompanionRuntime.updateGen2GameClock(lighting: MapLighting?) {
    runtimeLiveFixtures.getOrPut(this) { RuntimeLiveFixture() }.clock = lighting?.let {
        LiveClockState(phase = LiveClockPhase.valueOf(it.name))
    }
    publishFixtureState()
}

internal fun ProductionCompanionRuntime.updateLiveGameState(snapshot: LiveGameSnapshot?) {
    val owner = stateOwner()
    if (snapshot == null) {
        owner.suspendLive()
        return
    }
    beginTransientTestSession()
    val fixture = runtimeLiveFixtures.getOrPut(this) { RuntimeLiveFixture() }
    owner.acceptDecodedLive(snapshot.copy(sampleId = ++fixture.sampleId))
}

private fun ProductionCompanionRuntime.publishFixtureState() {
    val (owner, context) = beginTransientTestSession()
    val fixture = runtimeLiveFixtures.getOrPut(this) { RuntimeLiveFixture() }
    val battle = LiveBattleState(false, null, BattleEncounterKind.UNKNOWN)
    if (context.generation in 1..2) {
        owner.acceptExistingGenerationSample(
            sampleId = ++fixture.sampleId,
            battle = battle,
            areaBaseId = fixture.areaBaseId,
            mapPosition = fixture.position,
            clock = fixture.clock,
        )
        return
    }
    owner.acceptDecodedLive(
        LiveGameSnapshot(
            romIdentity = context.romIdentity,
            generation = 3,
            sampleId = ++fixture.sampleId,
            trainer = unavailableTrainer(),
            pokedex = LivePokedexState(unavailable("seen unavailable"), unavailable("caught unavailable")),
            party = unavailable("party unavailable"),
            battle = LiveValue.Available(battle),
            location = LiveLocationState(
                fixture.areaBaseId?.let { LiveValue.Available(it) } ?: unavailable("area unavailable"),
                fixture.position?.let { LiveValue.Available(it) } ?: unavailable("position unavailable"),
            ),
            clock = fixture.clock?.let { LiveValue.Available(it) } ?: unavailable("clock unavailable"),
            bag = BagPocket.entries.associateWith { unavailable("bag unavailable") },
            eventFlags = unavailable("flags unavailable"),
        ),
    )
}

private fun unavailableTrainer() = LiveTrainerState(
    identity = unavailable("identity unavailable"),
    publicTrainerId = unavailable("trainer ID unavailable"),
    money = unavailable("money unavailable"),
    playTime = unavailable("play time unavailable"),
    badgeFlags = unavailable("badges unavailable"),
    stars = unavailable("stars unavailable"),
)

private fun <T> unavailable(detail: String): LiveValue<T> = LiveValue.Unavailable(
    LiveUnavailableReason(LiveUnavailableCode.MISSING_REGION, detail),
)
