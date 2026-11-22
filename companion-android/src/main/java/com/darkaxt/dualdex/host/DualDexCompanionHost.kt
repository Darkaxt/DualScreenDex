package com.darkaxt.dualdex.host

import android.content.Context
import android.util.Log
import com.darkaxt.dualdex.catalog.AndroidCatalogDatabaseFactory
import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.catalog.CatalogCacheDecision
import com.darkaxt.dualdex.catalog.SaveSnapshotStore
import com.darkaxt.dualdex.knowledge.RecoveryPreparation
import com.darkaxt.dualdex.knowledge.SaveKnowledgeCheckpointCoordinator
import com.darkaxt.dualdex.knowledge.SaveKnowledgeCheckpointStore
import com.darkaxt.dualdex.live.ResolvedStateTraceSink
import com.darkaxt.dualdex.live.UnifiedGameStateDecoder
import com.darkaxt.dualdex.mapper.MapperSessionStore
import com.darkaxt.dualdex.mapper.MemoryMapperCoordinator
import com.darkaxt.dualdex.performance.AndroidPerformanceLog
import com.darkaxt.dualdex.performance.AndroidPerformanceSampler
import com.darkaxt.dualdex.performance.AndroidPreviousProcessExitSource
import com.darkaxt.dualdex.performance.BoundedPerformanceWorkDispatcher
import com.darkaxt.dualdex.performance.PerformanceComponentMetrics
import com.darkaxt.dualdex.performance.PerformanceEventKind
import com.darkaxt.dualdex.performance.PerformanceEventSink
import com.darkaxt.dualdex.performance.PerformanceLogExport
import com.darkaxt.dualdex.performance.PerformanceRecorder
import com.darkaxt.dualdex.performance.PreviousProcessExitPendingStore
import com.darkaxt.dualdex.performance.PreviousProcessExitRecorder
import com.darkaxt.dualdex.performance.PreviousProcessExitSink
import com.darkaxt.dualdex.performance.PrivacySafeDiagnostics
import com.darkaxt.dualdex.performance.SharedPreferencesPreviousProcessExitMarker
import com.darkaxt.dualdex.progress.PlaythroughJournalRegistry
import com.darkaxt.dualdex.progress.PortableChallengeCatalog
import com.darkaxt.dualdex.retroarch.NetworkCommandTransport
import com.darkaxt.dualdex.retroarch.UdpNetworkCommandTransport
import com.darkaxt.dualdex.settings.InterfaceLanguageSettings
import com.darkaxt.dualdex.settings.SettingsRepository
import com.darkaxt.dualdex.setup.GuideLoadFault
import com.darkaxt.dualdex.setup.HostSession
import com.darkaxt.dualdex.setup.NoGuideLoadFault
import com.darkaxt.dualdex.setup.RetroArchSetupCoordinator
import com.darkaxt.dualdex.storage.SharedStorageGateway
import com.darkaxt.dualdex.web.AndroidLoopbackServer
import com.darkaxt.dualdex.web.CompanionSurface
import com.darkaxt.dualdex.web.CompanionSurfaceOwnership
import com.darkaxt.dualdex.web.MapperHttpHandler
import com.darkaxt.dualdex.web.ProductionCompanionRuntime
import com.enrpau.dualscreendex.companion.model.DisplayMode
import com.enrpau.dualscreendex.companion.model.DisplayTarget
import com.google.gson.Gson
import java.io.File
import java.io.FileNotFoundException

/**
 * The DualDex companion graph: catalog runtime, RetroArch session coordinator, memory mapper and the loopback server
 * that serves the companion web UI. `DualDexApplication` owns one for the DualDex app; an embedding host (for example
 * an emulator frontend) creates its own with an in-process [Config.networkCommandTransportFactory] and an optional
 * [Config.hostSession].
 */
class DualDexCompanionHost(
    private val context: Context,
    private val config: Config,
) : AutoCloseable {
    class Config(
        val sharedStorage: SharedStorageGateway,
        val guideLoadFault: GuideLoadFault = NoGuideLoadFault,
        val networkCommandTransportFactory: () -> NetworkCommandTransport = { UdpNetworkCommandTransport() },
        val additionalLoopbackGetRoutes: Map<String, () -> Any> = emptyMap(),
        val onCompanionRuntimeCreated: (ProductionCompanionRuntime) -> Unit = {},
        /** A ROM display-mode change requested by the companion UI. */
        val onRomDisplayModeChanged: (DisplayMode) -> Unit = {},
        /** A display-target change (`DisplayTarget` name) requested by the companion UI. */
        val onDisplayTargetRequested: (String) -> Unit = {},
        /**
         * The game the host is running, when the host knows it. Used ahead of the ROM index and save discovery.
         * `null` (DualDex app): sessions are resolved from the indexed ROM library and RetroArch's save folders.
         */
        val hostSession: (() -> HostSession?)? = null,
        val appVersion: String? = null,
    )

    @Volatile var loopbackServer: AndroidLoopbackServer? = null
        private set
    @Volatile var startupFailure: Throwable? = null
        private set
    @Volatile var retroArchSetup: RetroArchSetupCoordinator? = null
        private set
    @Volatile var memoryMapper: MemoryMapperCoordinator? = null
        private set
    @Volatile var settings: SettingsRepository? = null
        private set
    @Volatile var activeCatalogSha256: String? = null
        private set
    @Volatile private var performanceLog: AndroidPerformanceLog? = null
    @Volatile private var performanceDispatcher: BoundedPerformanceWorkDispatcher? = null
    private val companionSurfaceOwnership = CompanionSurfaceOwnership()

    val localOrigin: String?
        get() = loopbackServer?.address?.let { "http://${AndroidLoopbackServer.LOOPBACK_HOST}:${it.port}" }

    fun ballSpritePng(id: Int): ByteArray? = loopbackServer?.ballSpritePng(id)

    fun exportPerformanceLog(): PerformanceLogExport {
        performanceDispatcher?.flush()
        return performanceLog?.export() ?: PerformanceLogExport.Unavailable
    }

    fun exportCompatibilityReport(): ByteArray =
        requireNotNull(loopbackServer) { "compatibility report is unavailable" }.exportCompatibilityReport()

    fun updateDisplayMode(mode: String) {
        loopbackServer?.updateDisplayMode(mode)
    }

    fun updateOverlayScale(scale: Double) {
        loopbackServer?.updateOverlayScale(scale)
    }

    fun currentDisplayTarget(): DisplayTarget = settings?.readGlobal()?.displayTarget ?: DisplayTarget.AUTO

    fun currentDisplayMode(): DisplayMode = settings?.readForRom(activeCatalogSha256)?.displayMode ?: DisplayMode.DOCKED

    fun activateCompanionSurface(surface: CompanionSurface) = companionSurfaceOwnership.activate(surface)

    fun pauseCompanionSurface(surface: CompanionSurface) = companionSurfaceOwnership.pause(surface)

    fun releaseCompanionSurface(surface: CompanionSurface) = companionSurfaceOwnership.release(surface)

    /** Starts the graph once; returns `false` (and records [startupFailure]) if it could not start. Retryable. */
    @Synchronized
    fun start(): Boolean {
        if (loopbackServer != null) return true
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val settingsRepository = SettingsRepository(preferences)
        val lastCatalogSha256 = preferences.getString(LAST_CATALOG_HASH, null)
        settingsRepository.migrateLegacyRuleset(lastCatalogSha256)
        InterfaceLanguageSettings.apply(settingsRepository.readGlobal().interfaceLanguage)
        activeCatalogSha256 = lastCatalogSha256
        settings = settingsRepository
        val filesDir = context.filesDir
        val profilerLog = performanceLog ?: AndroidPerformanceLog(File(filesDir, "diagnostics")).also {
            performanceLog = it
        }
        PreviousProcessExitRecorder(
            source = AndroidPreviousProcessExitSource(context),
            marker = SharedPreferencesPreviousProcessExitMarker(
                preferences,
                PreviousProcessExitPendingStore(File(filesDir, "diagnostics/previous-process-exit.pending")),
            ),
            sink = PreviousProcessExitSink(profilerLog::append),
        ).recordLatest()
        val profilerDispatcher = performanceDispatcher ?: BoundedPerformanceWorkDispatcher().also {
            performanceDispatcher = it
        }
        var metricsRuntime: ProductionCompanionRuntime? = null
        var metricsServer: AndroidLoopbackServer? = null
        var metricsMapper: MemoryMapperCoordinator? = null
        val performanceGson = Gson()
        val performanceRecorder = PerformanceRecorder(
            sampler = AndroidPerformanceSampler(),
            workDispatcher = profilerDispatcher,
            componentCounters = {
                val map = metricsRuntime?.mapAssetCacheStats()
                val loopback = metricsServer?.capacitySnapshot()
                val mapper = metricsMapper?.snapshot()
                val runtime = metricsRuntime?.performanceCounters().orEmpty()
                PerformanceComponentMetrics(
                    mapCacheEntries = map?.entries,
                    mapCacheEncodedBytes = map?.encodedBytes,
                    mapCacheHits = map?.hits,
                    mapCacheRenders = map?.renders,
                    mapCacheEvictions = map?.evictions,
                    activeWebViewSurfaces = companionSurfaceOwnership.activeSurfaceCount(),
                    loopbackWorkerThreads = loopback?.workerThreads,
                    loopbackActiveWorkers = loopback?.activeWorkers,
                    loopbackQueuedConnections = loopback?.queuedConnections,
                    loopbackActiveConnections = loopback?.activeConnections,
                    mapperSnapshots = mapper?.snapshots?.size,
                    mapperRetainedBytes = mapper?.snapshots?.sumOf { snapshot -> snapshot.bytes.toLong() },
                    areaGuideProjections = runtime["areaGuide.projections"],
                    areaGuideProjectionCpuNanos = runtime["areaGuide.projectionCpuNanos"],
                    areaGuideRetainedItems = runtime["areaGuide.retainedItems"],
                    progressSemanticEvaluations = runtime["progress.semanticEvaluations"],
                    progressSemanticCpuNanos = runtime["progress.semanticCpuNanos"],
                    progressEvents = runtime["progress.events"],
                    progressChallengeEvaluations = runtime["progress.challengeEvaluations"],
                    progressChallengeCpuNanos = runtime["progress.challengeCpuNanos"],
                    progressJournalEntries = runtime["progress.journalEntries"],
                    progressJournalRetainedItems = runtime["progress.journalRetainedItems"],
                ).counters() +
                    runtime +
                    metricsServer?.performanceCounters().orEmpty()
            },
            sinks = listOf(
                profilerLog,
                PerformanceEventSink { event ->
                    if (event.kind != PerformanceEventKind.STATE_CHANGED) {
                        Log.i(PERFORMANCE_LOG_TAG, performanceGson.toJson(event))
                    }
                    true
                },
            ),
        )
        val catalogDirectory = File(filesDir, "catalogs")
        val saveSnapshots = SaveSnapshotStore(
            catalogDirectory,
            AndroidCatalogDatabaseFactory,
            onCorruptSnapshot = {
                Log.w(
                    SAVE_SNAPSHOT_LOG_TAG,
                    PrivacySafeDiagnostics.message(
                        category = "SAVE_SNAPSHOT",
                        outcome = "QUARANTINED",
                    ),
                )
            },
        )
        val cache = CatalogCache(catalogDirectory, AndroidCatalogDatabaseFactory) { event ->
            val message = PrivacySafeDiagnostics.message(
                category = "CATALOG_CACHE",
                outcome = event.decision.name,
                failure = event.failure,
            )
            if (event.decision == CatalogCacheDecision.REJECTED_EXCEPTION) {
                Log.w(CACHE_LOG_TAG, message)
            } else {
                Log.i(CACHE_LOG_TAG, message)
            }
        }
        lateinit var runtime: ProductionCompanionRuntime
        val playthroughJournals = PlaythroughJournalRegistry()
        val portableChallenges = runCatching {
            context.assets.open("challenges/portable-baseline.json").use { PortableChallengeCatalog.decode(it.readBytes()) }
        }.getOrDefault(emptyList())
        val portableChallengeTemplates = runCatching {
            context.assets.open("challenges/portable-extended.json").use {
                PortableChallengeCatalog.decodeTemplates(it.readBytes())
            }
        }.getOrDefault(emptyList())
        val transientGameState = UnifiedGameStateDecoder(
            stateTraceSink = ResolvedStateTraceSink { trace ->
                runCatching { Log.i(STATE_LOG_TAG, performanceGson.toJson(trace)) }
                performanceRecorder.stateChanged(trace)
            },
            knowledgeLedgerSnapshot = { runtime.gateway.bootstrap().ledger },
        )
        runtime = ProductionCompanionRuntime(
            catalogRepository = cache,
            initialSettings = settingsRepository.readForRom(lastCatalogSha256),
            settingsForRom = settingsRepository::readForRom,
            globalSettings = settingsRepository::readGlobal,
            onRomSettingsChanged = { sha256, settings ->
                settingsRepository.writeForRom(sha256, settings)
                InterfaceLanguageSettings.apply(settingsRepository.readGlobal().interfaceLanguage)
            },
            onRomDisplayModeChanged = config.onRomDisplayModeChanged,
            onCatalogCleared = {
                activeCatalogSha256 = null
                preferences.edit()
                    .remove(LAST_CATALOG_HASH)
                    .remove(LAST_CATALOG_NAME)
                    .apply()
            },
            onCatalogCommitted = { sha256, displayName ->
                settingsRepository.migrateLegacyRuleset(sha256)
                activeCatalogSha256 = sha256
                preferences.edit()
                    .putString(LAST_CATALOG_HASH, sha256)
                    .putString(LAST_CATALOG_NAME, displayName)
                    .apply()
            },
            performanceRecorder = performanceRecorder,
            appVersion = config.appVersion,
            journalRegistry = playthroughJournals,
            challengeDefinitions = portableChallenges,
            challengeTemplates = portableChallengeTemplates,
            transientGameState = transientGameState,
        )
        config.onCompanionRuntimeCreated(runtime)
        metricsRuntime = runtime
        val candidate = AndroidLoopbackServer(
            runtime,
            requestBodySpoolFactory = {
                val directory = File(context.cacheDir, "request-bodies").apply { mkdirs() }
                File.createTempFile("request-", ".body", directory).toPath()
            },
            additionalGetRoutes = config.additionalLoopbackGetRoutes,
            assetLoader = ::loadWebAsset,
        )
        val networkCommandTransports = config.networkCommandTransportFactory
        var setupCandidate: RetroArchSetupCoordinator? = null
        var mapperCandidate: MemoryMapperCoordinator? = null
        return try {
            candidate.start()
            metricsServer = candidate
            setupCandidate = RetroArchSetupCoordinator(
                context,
                runtime,
                transientGameState,
                SaveKnowledgeCheckpointCoordinator(
                    SaveKnowledgeCheckpointStore(File(filesDir, "knowledge-checkpoints")),
                    prepareRecovery = { projection ->
                        transientGameState.prepareRecovery(projection)?.let { prepared ->
                            RecoveryPreparation(prepared.application) { publishAuthority ->
                                transientGameState.commitPreparedRecovery(prepared, publishAuthority)
                            }
                        }
                    },
                    journal = playthroughJournals,
                    publishRecoveryStatus = { status -> transientGameState.acceptRecoveryStatus(status) },
                ),
                saveSnapshotRepository = saveSnapshots,
                sharedStorage = config.sharedStorage,
                guideLoadFault = config.guideLoadFault,
                networkCommandTransportFactory = networkCommandTransports,
                hostSession = config.hostSession,
            )
            mapperCandidate = MemoryMapperCoordinator(
                MapperSessionStore(File(filesDir, "memory-mapper")),
                runtime::retroArchState,
                requireNotNull(setupCandidate)::commitMapperIfCurrent,
                transportFactory = networkCommandTransports,
            )
            metricsMapper = mapperCandidate
            candidate.setMapperHandler(object : MapperHttpHandler {
                override fun state(): Any = mapperCandidate.snapshot()
                override fun action(type: String, values: Map<String, String?>): Any = mapperCandidate.action(type, values)
                override fun exportRaw(): ByteArray = mapperCandidate.exportRaw()
            })
            candidate.setNativeActionHandler { type, values ->
                when {
                    type.equals("SELECT_SAVE", ignoreCase = true) -> values["documentId"]?.let(setupCandidate::selectSave) == true
                    type.equals("CLEAR_INACTIVE_CATALOGS", ignoreCase = true) -> {
                        cache.clearInactive(runtime.catalogHash())
                        true
                    }
                    type.equals("SETTINGS", ignoreCase = true) && values["displayTarget"] != null -> {
                        runtime.action(type, values)
                        values["displayTarget"]?.let(config.onDisplayTargetRequested)
                        true
                    }
                    else -> false
                }
            }
            loopbackServer = candidate
            retroArchSetup = setupCandidate
            memoryMapper = mapperCandidate
            startupFailure = null
            true
        } catch (failure: Throwable) {
            mapperCandidate?.close()
            setupCandidate?.close()
            candidate.close()
            retroArchSetup = null
            memoryMapper = null
            startupFailure = failure
            false
        }
    }

    /** Stops the graph. The DualDex app never calls this (it lives as long as its process); embedding hosts do. */
    @Synchronized
    override fun close() {
        memoryMapper?.close()
        retroArchSetup?.close()
        loopbackServer?.close()
        memoryMapper = null
        retroArchSetup = null
        loopbackServer = null
        performanceDispatcher?.flush()
    }

    private fun loadWebAsset(path: String): ByteArray? = try {
        context.assets.open("dualdex-web/$path").use { it.readBytes() }
    } catch (_: FileNotFoundException) {
        null
    }

    private companion object {
        const val CACHE_LOG_TAG = "DualDexCache"
        const val SAVE_SNAPSHOT_LOG_TAG = "DualDexSave"
        const val PERFORMANCE_LOG_TAG = "DualDexPerf"
        const val STATE_LOG_TAG = "DualDexState"
        const val PREFERENCES_NAME = "dualdex-runtime"
        const val LAST_CATALOG_HASH = "last-catalog-sha256"
        const val LAST_CATALOG_NAME = "last-catalog-name"
    }
}
