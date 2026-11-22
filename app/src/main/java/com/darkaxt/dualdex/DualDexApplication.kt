package com.darkaxt.dualdex

import android.app.Application
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import com.darkaxt.dualdex.host.DualDexCompanionHost
import com.darkaxt.dualdex.mapper.MemoryMapperCoordinator
import com.darkaxt.dualdex.overlay.FloatingCompanionService
import com.darkaxt.dualdex.overlay.OverlaySizeStore
import com.darkaxt.dualdex.overlay.RomDisplayModeApplicationAction
import com.darkaxt.dualdex.overlay.RomDisplayModeApplicationPolicy
import com.darkaxt.dualdex.performance.PerformanceLogExport
import com.darkaxt.dualdex.retroarch.NetworkCommandTransport
import com.darkaxt.dualdex.retroarch.UdpNetworkCommandTransport
import com.darkaxt.dualdex.setup.AndroidSetupPickerActivityResultRegistry
import com.darkaxt.dualdex.setup.GuideLoadFault
import com.darkaxt.dualdex.setup.NoGuideLoadFault
import com.darkaxt.dualdex.setup.RetroArchSetupCoordinator
import com.darkaxt.dualdex.setup.SetupPickerActivityResultRegistry
import com.darkaxt.dualdex.storage.SharedStorageGateway
import com.darkaxt.dualdex.web.AndroidLoopbackServer
import com.darkaxt.dualdex.web.CompanionSurface
import com.darkaxt.dualdex.web.ProductionCompanionRuntime
import com.enrpau.dualscreendex.companion.model.DisplayMode
import com.enrpau.dualscreendex.companion.model.DisplayTarget
import java.lang.ref.WeakReference

/** The DualDex app: owns one [DualDexCompanionHost] for the process, plus the activity and overlay wiring. */
open class DualDexApplication : Application() {
    @Volatile private var host: DualDexCompanionHost? = null
    @Volatile private var overlaySizeStore: OverlaySizeStore? = null
    @Volatile private var resumedActivity: WeakReference<MainActivity>? = null
    @Volatile private var pendingRomDisplayMode: DisplayMode? = null

    val loopbackServer: AndroidLoopbackServer?
        get() = host?.loopbackServer
    val startupFailure: Throwable?
        get() = host?.startupFailure
    val retroArchSetup: RetroArchSetupCoordinator?
        get() = host?.retroArchSetup
    val memoryMapper: MemoryMapperCoordinator?
        get() = host?.memoryMapper

    val localOrigin: String?
        get() = host?.localOrigin

    fun ballSpritePng(id: Int): ByteArray? = host?.ballSpritePng(id)

    fun exportPerformanceLog(): PerformanceLogExport = host?.exportPerformanceLog() ?: PerformanceLogExport.Unavailable

    fun exportCompatibilityReport(): ByteArray =
        requireNotNull(host) { "compatibility report is unavailable" }.exportCompatibilityReport()

    @Suppress("DEPRECATION")
    private fun packageVersionName(): String? = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull()

    fun updateDisplayMode(mode: String) {
        host?.updateDisplayMode(mode)
    }

    fun currentDisplayTarget(): DisplayTarget = host?.currentDisplayTarget() ?: DisplayTarget.AUTO

    fun currentDisplayMode(): DisplayMode = host?.currentDisplayMode() ?: DisplayMode.DOCKED

    fun currentOverlayScale(): Double = overlaySizeStore?.readScale() ?: 1.0

    fun updateOverlayScale(scale: Double) {
        overlaySizeStore?.writeScale(scale)
        host?.updateOverlayScale(scale)
    }

    fun activateCompanionSurface(surface: CompanionSurface) = requireNotNull(host).activateCompanionSurface(surface)

    fun pauseCompanionSurface(surface: CompanionSurface) = requireNotNull(host).pauseCompanionSurface(surface)

    fun releaseCompanionSurface(surface: CompanionSurface) = requireNotNull(host).releaseCompanionSurface(surface)

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

    protected open fun sharedStorageGateway(): SharedStorageGateway = SharedStorageGateway.android(this)

    protected open fun guideLoadFault(): GuideLoadFault = NoGuideLoadFault

    protected open fun networkCommandTransportFactory(): () -> NetworkCommandTransport =
        { UdpNetworkCommandTransport() }

    protected open fun additionalLoopbackGetRoutes(): Map<String, () -> Any> = emptyMap()

    internal open fun setupPickerActivityResultRegistry(activity: ComponentActivity): SetupPickerActivityResultRegistry =
        AndroidSetupPickerActivityResultRegistry(activity)

    internal open fun applyConfigTree(uri: Uri) {
        retroArchSetup?.applyConfigTree(uri)
    }

    internal open fun applyRomTree(uri: Uri) {
        retroArchSetup?.applyRomTree(uri)
    }

    protected open fun onCompanionRuntimeCreated(runtime: ProductionCompanionRuntime) = Unit

    @Synchronized
    fun startLoopback(): Boolean {
        val companion = host ?: DualDexCompanionHost(
            this,
            DualDexCompanionHost.Config(
                sharedStorage = sharedStorageGateway(),
                guideLoadFault = guideLoadFault(),
                networkCommandTransportFactory = networkCommandTransportFactory(),
                additionalLoopbackGetRoutes = additionalLoopbackGetRoutes(),
                onCompanionRuntimeCreated = ::onCompanionRuntimeCreated,
                onRomDisplayModeChanged = ::requestRomDisplayMode,
                onDisplayTargetRequested = ::requestDisplayTarget,
                appVersion = packageVersionName(),
            ),
        ).also { host = it }
        val started = companion.start()
        val settings = companion.settings
        if (overlaySizeStore == null && settings != null) {
            overlaySizeStore = OverlaySizeStore(settings::readGlobal, settings::writeGlobal)
        }
        return started
    }
}
