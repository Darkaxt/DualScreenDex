package com.darkaxt.dualdex

import android.graphics.Color
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.app.ActivityOptions
import android.hardware.display.DisplayManager
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.Display
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.webkit.WebView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.darkaxt.dualdex.overlay.FloatingCompanionService
import com.darkaxt.dualdex.overlay.OverlayStartupAction
import com.darkaxt.dualdex.overlay.OverlayStartupPolicy
import com.darkaxt.dualdex.rom.RomDocumentPicker
import com.darkaxt.dualdex.setup.SetupDocumentPicker
import com.darkaxt.dualdex.web.DualDexWebView
import com.darkaxt.dualdex.web.NativeSetupRoute
import com.darkaxt.dualdex.display.DisplayCandidate
import com.darkaxt.dualdex.display.DisplayContinuityDecision
import com.darkaxt.dualdex.display.DisplayContinuityPolicy
import com.darkaxt.dualdex.display.DisplayEvent
import com.enrpau.dualscreendex.companion.model.DisplayMode
import com.enrpau.dualscreendex.companion.model.DisplayTarget

internal data class DisplayEnvironment(
    val target: DisplayTarget,
    val currentDisplayId: Int,
    val candidates: List<DisplayCandidate>,
    val webRouteMarker: String?,
)

internal data class DisplayLaunch(
    val displayId: Int,
    val webRouteMarker: String?,
)

internal enum class MainActivityBackAction { DISPATCH_TO_COMPANION, BACKGROUND_TASK }

internal object MainActivityBackPolicy {
    fun resolve(webViewAlive: Boolean): MainActivityBackAction =
        if (webViewAlive) MainActivityBackAction.DISPATCH_TO_COMPANION else MainActivityBackAction.BACKGROUND_TASK
}

internal object WebRouteMarker {
    private const val PREFIX = "/#dualdex="
    private const val MAX_LENGTH = 8_193

    fun normalize(candidate: String?): String? {
        if (candidate == null || candidate.length > MAX_LENGTH || !candidate.startsWith(PREFIX)) return null
        val payload = candidate.substring(PREFIX.length)
        if (payload.isEmpty()) return null
        var index = 0
        while (index < payload.length) {
            val character = payload[index]
            when {
                character == '%' -> {
                    if (index + 2 >= payload.length ||
                        !payload[index + 1].isHexDigit() ||
                        !payload[index + 2].isHexDigit()
                    ) return null
                    index += 3
                }
                character.isLetterOrDigit() || character in "-_.!~*'()" -> index += 1
                else -> return null
            }
        }
        return candidate
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}

internal interface MainActivityDisplayPort {
    fun environment(): DisplayEnvironment
    fun register(listener: (DisplayEvent) -> Unit)
    fun unregister()
    fun launch(request: DisplayLaunch)
}

/** Lifecycle shell around the pure display policy. Android details stay behind [MainActivityDisplayPort]. */
internal class MainActivityDisplayContinuity(
    private val port: MainActivityDisplayPort,
    attemptedDisplayId: Int? = null,
) {
    private var started = false
    private var attemptedDisplayId: Int? = attemptedDisplayId

    fun onStart() {
        if (started) return
        started = true
        port.register(::onDisplayEvent)
    }

    fun onStop() {
        if (!started) return
        started = false
        port.unregister()
    }

    fun onResume() = evaluate(port.environment().target, DisplayEvent.Resumed)

    fun onTargetChanged(target: DisplayTarget) {
        attemptedDisplayId = null
        evaluate(target, DisplayEvent.TargetChanged)
    }

    private fun onDisplayEvent(event: DisplayEvent) {
        if (event is DisplayEvent.Removed && event.displayId == attemptedDisplayId) {
            attemptedDisplayId = null
        }
        evaluate(port.environment().target, event)
    }

    private fun evaluate(target: DisplayTarget, event: DisplayEvent) {
        val environment = port.environment()
        if (environment.currentDisplayId == attemptedDisplayId) attemptedDisplayId = null
        when (
            val decision = DisplayContinuityPolicy.decide(
                target = target,
                currentDisplayId = environment.currentDisplayId,
                candidates = environment.candidates,
                event = event,
                attemptedDisplayId = attemptedDisplayId,
            )
        ) {
            is DisplayContinuityDecision.Move -> {
                attemptedDisplayId = decision.displayId
                port.launch(DisplayLaunch(decision.displayId, environment.webRouteMarker))
            }
            DisplayContinuityDecision.ReevaluateOnResume,
            DisplayContinuityDecision.Stay,
            -> Unit
        }
    }
}

class MainActivity : AppCompatActivity() {
    private lateinit var picker: RomDocumentPicker
    private lateinit var setupPicker: SetupDocumentPicker
    private lateinit var displayManager: DisplayManager
    private lateinit var displayContinuity: MainActivityDisplayContinuity
    private var companionWebView: DualDexWebView? = null
    private var activeDisplayId = Display.DEFAULT_DISPLAY
    private var displayEventSink: ((DisplayEvent) -> Unit)? = null
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            displayEventSink?.invoke(DisplayEvent.Added(displayId))
        }

        override fun onDisplayChanged(displayId: Int) {
            displayEventSink?.invoke(DisplayEvent.Changed(displayId))
        }

        override fun onDisplayRemoved(displayId: Int) {
            displayEventSink?.invoke(DisplayEvent.Removed(displayId))
        }
    }
    private val mapperExportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = requireNotNull((application as DualDexApplication).memoryMapper) { "memory mapper is unavailable" }.exportRaw()
                requireNotNull(contentResolver.openOutputStream(uri, "wt")) { "selected export document is not writable" }
                    .use { it.write(bytes) }
            }.onSuccess {
                Toast.makeText(this, "Memory session exported", Toast.LENGTH_SHORT).show()
            }.onFailure { failure ->
                Toast.makeText(this, failure.message ?: "Memory session export failed", Toast.LENGTH_LONG).show()
            }
        }
    }
    private val performanceExportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-ndjson")) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = (application as DualDexApplication).exportPerformanceLog()
                requireNotNull(contentResolver.openOutputStream(uri, "wt")) { "selected export document is not writable" }
                    .use { it.write(bytes) }
            }.onSuccess {
                Toast.makeText(this, "Performance log exported", Toast.LENGTH_SHORT).show()
            }.onFailure { failure ->
                Toast.makeText(this, failure.message ?: "Performance log export failed", Toast.LENGTH_LONG).show()
            }
        }
    }
    private val compatibilityExportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = (application as DualDexApplication).exportCompatibilityReport()
                requireNotNull(contentResolver.openOutputStream(uri, "wt")) { "selected export document is not writable" }
                    .use { it.write(bytes) }
            }.onSuccess {
                Toast.makeText(this, "Compatibility report exported", Toast.LENGTH_SHORT).show()
            }.onFailure { failure ->
                Toast.makeText(this, failure.message ?: "Compatibility report export failed", Toast.LENGTH_LONG).show()
            }
        }
    }
    private val overlayPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val application = application as DualDexApplication
        if (Settings.canDrawOverlays(this)) {
            application.updateDisplayMode("OVERLAY")
            FloatingCompanionService.show(this)
            moveTaskToBack(true)
        } else {
            application.updateDisplayMode("DOCKED")
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayManager = getSystemService(DisplayManager::class.java)
        activeDisplayId = display?.displayId ?: Display.DEFAULT_DISPLAY
        displayContinuity = MainActivityDisplayContinuity(
            port = object : MainActivityDisplayPort {
                override fun environment(): DisplayEnvironment = displayEnvironment()

                override fun register(listener: (DisplayEvent) -> Unit) {
                    displayEventSink = listener
                    displayManager.registerDisplayListener(displayListener, null)
                }

                override fun unregister() {
                    displayManager.unregisterDisplayListener(displayListener)
                    displayEventSink = null
                }

                override fun launch(request: DisplayLaunch) = launchOnDisplay(request)
            },
            attemptedDisplayId = intent.displayAttempt(),
        )
        WebView.setWebContentsDebuggingEnabled((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        picker = RomDocumentPicker(this)
        setupPicker = SetupDocumentPicker(
            this,
            onConfigTree = { uri -> (application as DualDexApplication).retroArchSetup?.applyConfigTree(uri) },
            onRomTree = { uri -> (application as DualDexApplication).retroArchSetup?.applyRomTree(uri) },
        )
        showCompanionOrRecovery()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (MainActivityBackPolicy.resolve(companionWebView != null)) {
                    MainActivityBackAction.DISPATCH_TO_COMPANION -> companionWebView?.dispatchCompanionBack()
                    MainActivityBackAction.BACKGROUND_TASK -> moveTaskToBack(true)
                }
            }
        })
        if (intent.getBooleanExtra(EXTRA_EXPORT_MAPPER, false)) {
            exportMapper()
        } else if (intent.getBooleanExtra(EXTRA_EXPORT_PERFORMANCE, false)) {
            exportPerformanceLog()
        } else if (intent.getBooleanExtra(EXTRA_EXPORT_COMPATIBILITY, false)) {
            exportCompatibilityReport()
        } else {
            restoreOverlayMode()
        }
    }

    override fun onStart() {
        super.onStart()
        displayContinuity.onStart()
    }

    override fun onStop() {
        displayContinuity.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        picker.cancel()
        companionWebView?.let { (application as DualDexApplication).releaseCompanionSurface(it) }
        companionWebView = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        activeDisplayId = display?.displayId ?: activeDisplayId
        val application = application as DualDexApplication
        application.activityResumed(this)
        val currentSurface = companionWebView
        if (currentSurface == null || currentSurface.released) {
            showCompanionOrRecovery()
        } else {
            application.activateCompanionSurface(currentSurface)
        }
        application.retroArchSetup?.refreshStorageAccess()
        displayContinuity.onResume()
    }

    override fun onPause() {
        val application = application as DualDexApplication
        companionWebView?.let(application::pauseCompanionSurface)
        application.activityPaused(this)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_EXPORT_MAPPER, false)) exportMapper()
        if (intent.getBooleanExtra(EXTRA_EXPORT_PERFORMANCE, false)) exportPerformanceLog()
        if (intent.getBooleanExtra(EXTRA_EXPORT_COMPATIBILITY, false)) exportCompatibilityReport()
    }

    private fun showCompanionOrRecovery() {
        val application = application as DualDexApplication
        companionWebView?.let(application::releaseCompanionSurface)
        companionWebView = null
        val origin = application.localOrigin
        if (origin == null) {
            showRecovery(application.startupFailure)
            return
        }
        val webView = DualDexWebView(
            this,
            origin,
            picker,
            onNativeSetupRoute = { route ->
                when (route) {
                    NativeSetupRoute.GRANT_ALL_FILES -> startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName"),
                        ),
                    )
                    NativeSetupRoute.GRANT_RETROARCH -> setupPicker.openConfigTree()
                    NativeSetupRoute.GRANT_ROMS -> setupPicker.openRomTree()
                    NativeSetupRoute.OPEN_RETROARCH -> application.retroArchSetup?.launchRetroArch()
                    NativeSetupRoute.EXPORT_MAPPER -> exportMapper()
                    NativeSetupRoute.EXPORT_PERFORMANCE -> exportPerformanceLog()
                    NativeSetupRoute.EXPORT_COMPATIBILITY -> exportCompatibilityReport()
                    NativeSetupRoute.RETRY_GUIDE -> application.retroArchSetup?.retryGuideLoad()
                    NativeSetupRoute.SHOW_OVERLAY -> showOverlay()
                    NativeSetupRoute.DOCK_OVERLAY -> {
                        FloatingCompanionService.dock(this)
                        application.updateDisplayMode("DOCKED")
                    }
                }
            },
            onMainFrameFailure = { reason -> showRecovery(IllegalStateException(reason)) },
        )
        companionWebView = webView
        val host = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(7, 30, 24))
            addView(
                webView,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }
        setContentView(host)
        keepContentInsideSystemBars(host)
        application.activateCompanionSurface(webView)
        val routeMarker = WebRouteMarker.normalize(intent.getStringExtra(EXTRA_WEB_ROUTE))
        if (routeMarker == null) webView.open() else webView.loadUrl("$origin$routeMarker")
    }

    private fun showOverlay() {
        if (Settings.canDrawOverlays(this)) {
            (application as DualDexApplication).updateDisplayMode("OVERLAY")
            FloatingCompanionService.show(this)
            moveTaskToBack(true)
            return
        }
        overlayPermission.launch(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
        )
    }

    fun applyRomDisplayMode(mode: DisplayMode) {
        when (mode) {
            DisplayMode.OVERLAY -> showOverlay()
            DisplayMode.DOCKED -> {
                FloatingCompanionService.dock(this)
            }
        }
    }

    private fun restoreOverlayMode() {
        if (FloatingCompanionService.running) return
        val application = application as DualDexApplication
        when (OverlayStartupPolicy.resolve(application.currentDisplayMode(), Settings.canDrawOverlays(this))) {
            OverlayStartupAction.STAY_DOCKED -> Unit
            OverlayStartupAction.START_OVERLAY -> {
                FloatingCompanionService.show(this)
                moveTaskToBack(true)
            }
            OverlayStartupAction.REVERT_TO_DOCKED -> application.updateDisplayMode("DOCKED")
        }
    }

    private fun exportMapper() {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        mapperExportPicker.launch("dualdex-memory-$timestamp.json")
    }

    private fun exportPerformanceLog() {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        performanceExportPicker.launch("dualdex-performance-$timestamp.ndjson")
    }

    private fun exportCompatibilityReport() {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        compatibilityExportPicker.launch("dualdex-compatibility-$timestamp.json")
    }

    fun moveToDisplayTarget(target: DisplayTarget) {
        displayContinuity.onTargetChanged(target)
    }

    private fun displayEnvironment(): DisplayEnvironment {
        val presentationIds = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .map { it.displayId }
            .toSet()
        val candidates = displayManager.displays.map { candidate ->
            DisplayCandidate(
                id = candidate.displayId,
                isDefault = candidate.displayId == Display.DEFAULT_DISPLAY,
                isPresentation = candidate.displayId in presentationIds,
            )
        }
        return DisplayEnvironment(
            target = (application as DualDexApplication).currentDisplayTarget(),
            currentDisplayId = activeDisplayId,
            candidates = candidates,
            webRouteMarker = currentWebRouteMarker(),
        )
    }

    private fun launchOnDisplay(request: DisplayLaunch) {
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(request.displayId)
        val launchIntent = Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_DISPLAY_ATTEMPT, request.displayId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        request.webRouteMarker?.let { launchIntent.putExtra(EXTRA_WEB_ROUTE, it) }
        startActivity(
            launchIntent,
            options.toBundle(),
        )
        finish()
    }

    private fun currentWebRouteMarker(): String? {
        val origin = (application as DualDexApplication).localOrigin ?: return null
        val currentUrl = companionWebView?.url ?: return null
        if (!currentUrl.startsWith(origin)) return null
        return WebRouteMarker.normalize(currentUrl.removePrefix(origin))
    }

    private fun showRecovery(failure: Throwable?) {
        companionWebView?.let { (application as DualDexApplication).releaseCompanionSurface(it) }
        companionWebView = null
        val application = application as DualDexApplication
        val scale = resources.displayMetrics.density
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((24 * scale).toInt(), (24 * scale).toInt(), (24 * scale).toInt(), (24 * scale).toInt())
            setBackgroundColor(Color.rgb(7, 30, 24))
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.recovery_title)
            textSize = 22f
            setTextColor(Color.rgb(217, 244, 122))
            gravity = Gravity.CENTER
        })
        content.addView(Button(this).apply {
            text = getString(R.string.recovery_retry)
            setOnClickListener {
                if (application.startLoopback()) showCompanionOrRecovery() else showRecovery(application.startupFailure)
            }
        })
        content.addView(Button(this).apply {
            text = getString(R.string.recovery_details)
            setOnClickListener {
                content.addView(TextView(this@MainActivity).apply {
                    text = failure?.stackTraceToString() ?: "No startup exception was retained."
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    setTextIsSelectable(true)
                })
                isEnabled = false
            }
        })
        setContentView(ScrollView(this).apply {
            addView(content)
            keepContentInsideSystemBars(this)
        })
    }

    private fun keepContentInsideSystemBars(view: android.view.View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            target.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    companion object {
        const val EXTRA_EXPORT_MAPPER = "com.darkaxt.dualdex.EXPORT_MAPPER"
        const val EXTRA_EXPORT_PERFORMANCE = "com.darkaxt.dualdex.EXPORT_PERFORMANCE"
        const val EXTRA_EXPORT_COMPATIBILITY = "com.darkaxt.dualdex.EXPORT_COMPATIBILITY"
        private const val EXTRA_DISPLAY_ATTEMPT = "com.darkaxt.dualdex.DISPLAY_ATTEMPT"
        private const val EXTRA_WEB_ROUTE = "com.darkaxt.dualdex.WEB_ROUTE"

        private fun Intent.displayAttempt(): Int? = getIntExtra(EXTRA_DISPLAY_ATTEMPT, Int.MIN_VALUE)
            .takeUnless { it == Int.MIN_VALUE }
    }
}
