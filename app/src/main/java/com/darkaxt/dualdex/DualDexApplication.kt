package com.darkaxt.dualdex

import android.app.Application
import com.darkaxt.dualdex.web.AndroidLoopbackServer
import com.darkaxt.dualdex.web.ProductionCompanionRuntime
import java.io.FileNotFoundException

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
        val runtime = ProductionCompanionRuntime()
        val candidate = AndroidLoopbackServer(runtime, assetLoader = ::loadWebAsset)
        return try {
            candidate.start()
            loopbackServer = candidate
            startupFailure = null
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
}
