package com.darkaxt.dualdex

import android.app.Application
import com.darkaxt.dualdex.catalog.AndroidCatalogDatabaseFactory
import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.web.AndroidLoopbackServer
import com.darkaxt.dualdex.web.ProductionCompanionRuntime
import com.darkaxt.dualdex.setup.RetroArchSetupCoordinator
import com.darkaxt.dualdex.settings.SettingsRepository
import com.darkaxt.dualdex.mapper.MapperSessionStore
import com.darkaxt.dualdex.mapper.MemoryMapperCoordinator
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
    @Volatile private var resumedActivity: WeakReference<MainActivity>? = null

    val localOrigin: String?
        get() = loopbackServer?.address?.let { "http://${AndroidLoopbackServer.LOOPBACK_HOST}:${it.port}" }

    fun ballSpritePng(id: Int): ByteArray? = loopbackServer?.ballSpritePng(id)

    fun updateDisplayMode(mode: String) {
        loopbackServer?.updateDisplayMode(mode)
    }

    fun currentDisplayTarget(): DisplayTarget = settingsStore?.read()?.displayTarget ?: DisplayTarget.AUTO

    fun currentDisplayMode(): DisplayMode = settingsStore?.read()?.displayMode ?: DisplayMode.DOCKED

    fun activityResumed(activity: MainActivity) {
        resumedActivity = WeakReference(activity)
    }

    fun activityPaused(activity: MainActivity) {
        if (resumedActivity?.get() === activity) resumedActivity = null
    }

    private fun requestDisplayTarget(target: String) {
        val resolved = DisplayTarget.valueOf(target.uppercase())
        resumedActivity?.get()?.runOnUiThread { resumedActivity?.get()?.moveToDisplayTarget(resolved) }
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
        settingsStore = settingsRepository
        val cache = CatalogCache(File(filesDir, "catalogs"), AndroidCatalogDatabaseFactory)
        val runtime = ProductionCompanionRuntime(
            catalogRepository = cache,
            initialSettings = settingsRepository.read(),
            onSettingsChanged = settingsRepository::write,
            onCatalogCommitted = { sha256, displayName ->
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
            preferences.getString(LAST_CATALOG_HASH, null)?.let(runtime::restoreCatalogAsync)
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
                    type.equals("SETTINGS", ignoreCase = true) && values["displayTarget"] != null -> {
                        runtime.action(type, values)
                        requestDisplayTarget(requireNotNull(values["displayTarget"]))
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
