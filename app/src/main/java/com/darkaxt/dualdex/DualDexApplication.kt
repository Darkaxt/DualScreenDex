package com.darkaxt.dualdex

import android.app.Application
import android.provider.Settings
import android.util.Log
import com.darkaxt.dualdex.catalog.AndroidCatalogDatabaseFactory
import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.catalog.CatalogCacheDecision
import com.darkaxt.dualdex.catalog.SaveSnapshotStore
import com.darkaxt.dualdex.knowledge.SaveKnowledgeCheckpointCoordinator
import com.darkaxt.dualdex.knowledge.SaveKnowledgeCheckpointStore
import com.darkaxt.dualdex.progress.PlaythroughJournalRegistry
import com.darkaxt.dualdex.progress.PortableChallengeCatalog
import com.darkaxt.dualdex.live.UnifiedGameStateDecoder
import com.darkaxt.dualdex.live.ResolvedStateTraceSink
import com.darkaxt.dualdex.performance.AndroidPerformanceLog
import com.darkaxt.dualdex.performance.AndroidPerformanceSampler
import com.darkaxt.dualdex.performance.BoundedPerformanceWorkDispatcher
import com.darkaxt.dualdex.performance.PerformanceComponentMetrics
import com.darkaxt.dualdex.performance.PerformanceEventSink
import com.darkaxt.dualdex.performance.PerformanceEventKind
import com.darkaxt.dualdex.performance.PerformanceRecorder
import com.darkaxt.dualdex.web.AndroidLoopbackServer
import com.darkaxt.dualdex.web.ProductionCompanionRuntime
import com.darkaxt.dualdex.setup.RetroArchSetupCoordinator
import com.darkaxt.dualdex.settings.SettingsRepository
import com.darkaxt.dualdex.mapper.MapperSessionStore
import com.darkaxt.dualdex.mapper.MemoryMapperCoordinator
import com.darkaxt.dualdex.overlay.OverlaySizeStore
import com.darkaxt.dualdex.overlay.FloatingCompanionService
import com.darkaxt.dualdex.overlay.RomDisplayModeApplicationAction
import com.darkaxt.dualdex.overlay.RomDisplayModeApplicationPolicy
import com.darkaxt.dualdex.web.MapperHttpHandler
import com.darkaxt.dualdex.web.CompanionSurface
import com.darkaxt.dualdex.web.CompanionSurfaceOwnership
import com.enrpau.dualscreendex.companion.model.DisplayMode
import com.enrpau.dualscreendex.companion.model.DisplayTarget
import com.google.gson.Gson
import java.io.FileNotFoundException
import java.io.File
import java.lang.ref.WeakReference

class DualDexApplication : Application() {
    @Volatile var loopbackServer: AndroidLoopbackServer? = null
        private set
    @Volatile var startupFailure: Throwable? = null
        private set
    @Volatile var retroArchSetup: RetroArchSetupCoordinator? = null
        private set
    @Volatile var memoryMapper: MemoryMapperCoordinator? = null
        private set
    @Volatile private var performanceLog: AndroidPerformanceLog? = null
    @Volatile private var performanceDispatcher: BoundedPerformanceWorkDispatcher? = null
    @Volatile private var settingsStore: SettingsRepository? = null
    @Volatile private var overlaySizeStore: OverlaySizeStore? = null
    @Volatile private var activeCatalogSha256: String? = null
    @Volatile private var resumedActivity: WeakReference<MainActivity>? = null
    @Volatile private var pendingRomDisplayMode: DisplayMode? = null
    private val companionSurfaceOwnership = CompanionSurfaceOwnership()

    val localOrigin: String?
        get() = loopbackServer?.address?.let { "http://${AndroidLoopbackServer.LOOPBACK_HOST}:${it.port}" }

    fun ballSpritePng(id: Int): ByteArray? = loopbackServer?.ballSpritePng(id)

    fun exportPerformanceLog(): ByteArray {
        performanceDispatcher?.flush()
        return performanceLog?.export() ?: ByteArray(0)
    }

    fun exportCompatibilityReport(): ByteArray =
        requireNotNull(loopbackServer) { "compatibility report is unavailable" }.exportCompatibilityReport()

    @Suppress("DEPRECATION")
    private fun packageVersionName(): String? = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull()

    fun updateDisplayMode(mode: String) {
        loopbackServer?.updateDisplayMode(mode)
    }

    fun currentDisplayTarget(): DisplayTarget = settingsStore?.readGlobal()?.displayTarget ?: DisplayTarget.AUTO

    fun currentDisplayMode(): DisplayMode = settingsStore?.readForRom(activeCatalogSha256)?.displayMode ?: DisplayMode.DOCKED

    fun currentOverlayScale(): Double = overlaySizeStore?.readScale() ?: 1.0

    fun updateOverlayScale(scale: Double) {
        overlaySizeStore?.writeScale(scale)
        loopbackServer?.updateOverlayScale(scale)
    }

    fun activateCompanionSurface(surface: CompanionSurface) = companionSurfaceOwnership.activate(surface)

    fun pauseCompanionSurface(surface: CompanionSurface) = companionSurfaceOwnership.pause(surface)

    fun releaseCompanionSurface(surface: CompanionSurface) = companionSurfaceOwnership.release(surface)

    fun activityResumed(activity: MainActivity) {
        resumedActivity = WeakReference(activity)
        pendingRomDisplayMode?.let(::requestRomDisplayMode)
    }

    fun activityPaused(activity: MainActivity) {
        if (resumedActivity?.get() === activity) resumedActivity = null
    }

    private fun requestDisplayTarget(target: String) {
        val resolved = DisplayTarget.valueOf(target.uppercase())
        resumedActivity?.get()?.runOnUiThread { resumedActivity?.get()?.moveToDisplayTarget(resolved) }
    }

    private fun requestRomDisplayMode(mode: DisplayMode) {
        val activity = resumedActivity?.get()
        when (RomDisplayModeApplicationPolicy.resolve(mode, activity != null, Settings.canDrawOverlays(this))) {
            RomDisplayModeApplicationAction.APPLY_IN_ACTIVITY -> {
                pendingRomDisplayMode = null
                activity?.runOnUiThread { activity.applyRomDisplayMode(mode) }
            }
            RomDisplayModeApplicationAction.SHOW_OVERLAY -> {
                pendingRomDisplayMode = null
                FloatingCompanionService.show(this)
            }
            RomDisplayModeApplicationAction.DOCK_OVERLAY -> {
                pendingRomDisplayMode = null
                FloatingCompanionService.dockAndSurface(this)
            }
            RomDisplayModeApplicationAction.WAIT_FOR_ACTIVITY -> pendingRomDisplayMode = mode
        }
    }

    override fun onCreate() {
        super.onCreate()
        startLoopback()
    }

    @Synchronized
    fun startLoopback(): Boolean {
        if (loopbackServer != null) return true
        val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        val settingsRepository = SettingsRepository(preferences)
        val lastCatalogSha256 = preferences.getString(LAST_CATALOG_HASH, null)
        settingsRepository.migrateLegacyRuleset(lastCatalogSha256)
        activeCatalogSha256 = lastCatalogSha256
        settingsStore = settingsRepository
        overlaySizeStore = OverlaySizeStore(settingsRepository::readGlobal, settingsRepository::writeGlobal)
        val profilerLog = performanceLog ?: AndroidPerformanceLog(File(filesDir, "diagnostics")).also {
            performanceLog = it
        }
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
                },
            ),
        )
        val catalogDirectory = File(filesDir, "catalogs")
        val saveSnapshots = SaveSnapshotStore(catalogDirectory, AndroidCatalogDatabaseFactory)
        val cache = CatalogCache(catalogDirectory, AndroidCatalogDatabaseFactory) { event ->
            val message = buildString {
                append(event.decision.name)
                append(" sha256=")
                append(event.sha256)
                event.failure?.let { failure ->
                    append(" failure=")
                    append(failure.javaClass.simpleName)
                    failure.message?.takeIf(String::isNotBlank)?.let { append(": ").append(it) }
                }
            }
            if (event.decision == CatalogCacheDecision.REJECTED_EXCEPTION) {
                Log.w(CACHE_LOG_TAG, message, event.failure)
            } else {
                Log.i(CACHE_LOG_TAG, message)
            }
        }
        lateinit var runtime: ProductionCompanionRuntime
        val playthroughJournals = PlaythroughJournalRegistry()
        val portableChallenges = runCatching {
            assets.open("challenges/portable-baseline.json").use { PortableChallengeCatalog.decode(it.readBytes()) }
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
            onRomSettingsChanged = settingsRepository::writeForRom,
            onRomDisplayModeChanged = ::requestRomDisplayMode,
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
            appVersion = packageVersionName(),
            journalRegistry = playthroughJournals,
            challengeDefinitions = portableChallenges,
            transientGameState = transientGameState,
        )
        metricsRuntime = runtime
        val candidate = AndroidLoopbackServer(
            runtime,
            requestBodySpoolFactory = {
                val directory = File(cacheDir, "request-bodies").apply { mkdirs() }
                File.createTempFile("request-", ".body", directory).toPath()
            },
            assetLoader = ::loadWebAsset,
        )
        var setupCandidate: RetroArchSetupCoordinator? = null
        var mapperCandidate: MemoryMapperCoordinator? = null
        return try {
            candidate.start()
            metricsServer = candidate
            setupCandidate = RetroArchSetupCoordinator(
                this,
                runtime,
                transientGameState,
                SaveKnowledgeCheckpointCoordinator(
                    SaveKnowledgeCheckpointStore(File(filesDir, "knowledge-checkpoints")),
                    transientGameState::acceptRecovery,
                    playthroughJournals,
                ),
                saveSnapshotRepository = saveSnapshots,
            )
            mapperCandidate = MemoryMapperCoordinator(
                MapperSessionStore(File(filesDir, "memory-mapper")), runtime::retroArchState,
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
                        values["displayTarget"]?.let(::requestDisplayTarget)
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

    private fun loadWebAsset(path: String): ByteArray? = try {
        assets.open("dualdex-web/$path").use { it.readBytes() }
    } catch (_: FileNotFoundException) {
        null
    }

    private companion object {
        const val CACHE_LOG_TAG = "DualDexCache"
        const val PERFORMANCE_LOG_TAG = "DualDexPerf"
        const val STATE_LOG_TAG = "DualDexState"
        const val PREFERENCES_NAME = "dualdex-runtime"
        const val LAST_CATALOG_HASH = "last-catalog-sha256"
        const val LAST_CATALOG_NAME = "last-catalog-name"
    }
}
