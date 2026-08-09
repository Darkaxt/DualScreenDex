package com.darkaxt.dualdex

import android.app.Application
import com.darkaxt.dualdex.catalog.AndroidCatalogDatabaseFactory
import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.web.AndroidLoopbackServer
import com.darkaxt.dualdex.web.ProductionCompanionRuntime
import java.io.FileNotFoundException
import java.io.File

class DualDexApplication : Application() {
    @Volatile var loopbackServer: AndroidLoopbackServer? = null
        private set
    @Volatile var startupFailure: Throwable? = null
        private set

    val localOrigin: String?
        get() = loopbackServer?.address?.let { "http://${AndroidLoopbackServer.LOOPBACK_HOST}:${it.port}" }

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
        return try {
            candidate.start()
            loopbackServer = candidate
            startupFailure = null
            preferences.getString(LAST_CATALOG_HASH, null)?.let(runtime::restoreCatalogAsync)
            true
        } catch (failure: Throwable) {
            candidate.close()
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
