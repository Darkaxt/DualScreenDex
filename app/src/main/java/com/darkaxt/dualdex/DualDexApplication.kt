package com.darkaxt.dualdex

import android.app.Application
import android.provider.Settings
import com.darkaxt.dualdex.catalog.AndroidCatalogDatabaseFactory
import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.knowledge.FileKnowledgeRepository
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
import com.enrpau.dualscreendex.companion.model.DisplayMode
import com.enrpau.dualscreendex.companion.model.DisplayTarget
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
    @Volatile private var settingsStore: SettingsRepository? = null
    @Volatile private var overlaySizeStore: OverlaySizeStore? = null
    @Volatile private var activeCatalogSha256: String? = null
    @Volatile private var resumedActivity: WeakReference<MainActivity>? = null
    @Volatile private var pendingRomDisplayMode: DisplayMode? = null

    val localOrigin: String?
        get() = loopbackServer?.address?.let { "http://${AndroidLoopbackServer.LOOPBACK_HOST}:${it.port}" }

    fun ballSpritePng(id: Int): ByteArray? = loopbackServer?.ballSpritePng(id)

    fun updateDisplayMode(mode: String) {
        loopbackServer?.updateDisplayMode(mode)
    }

    fun currentDisplayTarget(): DisplayTarget = settingsStore?.readGlobal()?.displayTarget ?: DisplayTarget.AUTO

    fun currentDisplayMode(): DisplayMode = settingsStore?.readForRom(activeCatalogSha256)?.displayMode ?: DisplayMode.DOCKED

    fun currentThorTopScreenFocus(): Boolean = settingsStore?.readGlobal()?.thorTopScreenFocus ?: false

    fun currentOverlayScale(): Double = overlaySizeStore?.readScale() ?: 1.0

    fun updateOverlayScale(scale: Double) {
        overlaySizeStore?.writeScale(scale)
        loopbackServer?.updateOverlayScale(scale)
    }

    fun activityResumed(activity: MainActivity) {
        resumedActivity = WeakReference(activity)
        pendingRomDisplayMode?.let(::requestRomDisplayMode)
    }

    fun activityPaused(activity: MainActivity) {
        if (resumedActivity?.get() === activity) resumedActivity = null
    }

    fun requestThorFocusSync(requestPermission: Boolean) {
        resumedActivity?.get()?.runOnUiThread {
            resumedActivity?.get()?.syncThorFocus(requestPermission)
        }
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
        val cache = CatalogCache(File(filesDir, "catalogs"), AndroidCatalogDatabaseFactory)
        val runtime = ProductionCompanionRuntime(
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
            knowledgeRepository = FileKnowledgeRepository(File(filesDir, "knowledge")),
            onCatalogCommitted = { sha256, displayName ->
                settingsRepository.migrateLegacyRuleset(sha256)
                activeCatalogSha256 = sha256
                preferences.edit()
                    .putString(LAST_CATALOG_HASH, sha256)
                    .putString(LAST_CATALOG_NAME, displayName)
                    .apply()
            },
        )
        val candidate = AndroidLoopbackServer(runtime, assetLoader = ::loadWebAsset)
        var setupCandidate: RetroArchSetupCoordinator? = null
        var mapperCandidate: MemoryMapperCoordinator? = null
        return try {
            candidate.start()
            lastCatalogSha256?.let(runtime::restoreCatalogAsync)
            setupCandidate = RetroArchSetupCoordinator(this, runtime)
            mapperCandidate = MemoryMapperCoordinator(
                MapperSessionStore(File(filesDir, "memory-mapper")), runtime::retroArchState,
            )
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
                    type.equals("SETTINGS", ignoreCase = true) &&
                        (values["displayTarget"] != null || values["thorTopScreenFocus"] != null) -> {
                        runtime.action(type, values)
                        values["displayTarget"]?.let(::requestDisplayTarget)
                        if (values["thorTopScreenFocus"] != null) requestThorFocusSync(requestPermission = true)
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
        const val PREFERENCES_NAME = "dualdex-runtime"
        const val LAST_CATALOG_HASH = "last-catalog-sha256"
        const val LAST_CATALOG_NAME = "last-catalog-name"
    }
}
