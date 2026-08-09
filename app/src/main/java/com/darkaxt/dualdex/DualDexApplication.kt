package com.darkaxt.dualdex

import android.app.Application
import com.darkaxt.dualdex.catalog.AndroidCatalogDatabaseFactory
import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.web.AndroidLoopbackServer
import com.darkaxt.dualdex.web.ProductionCompanionRuntime
import com.darkaxt.dualdex.setup.RetroArchSetupCoordinator
import java.io.FileNotFoundException
import java.io.File

class DualDexApplication : Application() {
    @Volatile var loopbackServer: AndroidLoopbackServer? = null
        private set
    @Volatile var startupFailure: Throwable? = null
        private set
    @Volatile var retroArchSetup: RetroArchSetupCoordinator? = null
        private set

    val localOrigin: String?
        get() = loopbackServer?.address?.let { "http://${AndroidLoopbackServer.LOOPBACK_HOST}:${it.port}" }

    fun ballSpritePng(id: Int): ByteArray? = loopbackServer?.ballSpritePng(id)

    fun updateDisplayMode(mode: String) {
        loopbackServer?.updateDisplayMode(mode)
    }

    override fun onCreate() {
        super.onCreate()
        startLoopback()
    }

    @Synchronized
    fun startLoopback(): Boolean {
        if (loopbackServer != null) return true
        val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        val cache = CatalogCache(File(filesDir, "catalogs"), AndroidCatalogDatabaseFactory)
        val runtime = ProductionCompanionRuntime(
            catalogRepository = cache,
            onCatalogCommitted = { sha256, displayName ->
                preferences.edit()
                    .putString(LAST_CATALOG_HASH, sha256)
                    .putString(LAST_CATALOG_NAME, displayName)
                    .apply()
            },
        )
        val candidate = AndroidLoopbackServer(runtime, assetLoader = ::loadWebAsset)
        var setupCandidate: RetroArchSetupCoordinator? = null
        return try {
            candidate.start()
            preferences.getString(LAST_CATALOG_HASH, null)?.let(runtime::restoreCatalogAsync)
            setupCandidate = RetroArchSetupCoordinator(this, runtime)
            candidate.setNativeActionHandler { type, values ->
                type.equals("SELECT_SAVE", ignoreCase = true) &&
                    values["documentId"]?.let(setupCandidate::selectSave) == true
            }
            loopbackServer = candidate
            retroArchSetup = setupCandidate
            startupFailure = null
            true
        } catch (failure: Throwable) {
            setupCandidate?.close()
            candidate.close()
            retroArchSetup = null
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
