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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.darkaxt.dualdex.overlay.FloatingCompanionService
import com.darkaxt.dualdex.rom.RomDocumentPicker
import com.darkaxt.dualdex.setup.SetupDocumentPicker
import com.darkaxt.dualdex.web.DualDexWebView
import com.darkaxt.dualdex.web.NativeSetupRoute
import com.darkaxt.dualdex.display.CompanionDisplay
import com.darkaxt.dualdex.display.DisplayTargetController
import com.enrpau.dualscreendex.companion.model.DisplayTarget

class MainActivity : AppCompatActivity() {
    private lateinit var picker: RomDocumentPicker
    private lateinit var setupPicker: SetupDocumentPicker
    private var companionWebView: DualDexWebView? = null
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
        WebView.setWebContentsDebuggingEnabled((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        picker = RomDocumentPicker(this)
        setupPicker = SetupDocumentPicker(
            this,
            onConfigTree = { uri -> (application as DualDexApplication).retroArchSetup?.applyConfigTree(uri) },
            onRomTree = { uri -> (application as DualDexApplication).retroArchSetup?.applyRomTree(uri) },
        )
        showCompanionOrRecovery()
        if (intent.getBooleanExtra(EXTRA_EXPORT_MAPPER, false)) exportMapper()
    }

    override fun onDestroy() {
        picker.cancel()
        companionWebView?.destroy()
        companionWebView = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        val application = application as DualDexApplication
        application.activityResumed(this)
        moveToDisplayTarget(application.currentDisplayTarget())
    }

    override fun onPause() {
        (application as DualDexApplication).activityPaused(this)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_EXPORT_MAPPER, false)) exportMapper()
    }

    private fun showCompanionOrRecovery() {
        val application = application as DualDexApplication
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
                    NativeSetupRoute.GRANT_RETROARCH -> setupPicker.openConfigTree()
                    NativeSetupRoute.GRANT_ROMS -> setupPicker.openRomTree()
                    NativeSetupRoute.OPEN_RETROARCH -> application.retroArchSetup?.launchRetroArch()
                    NativeSetupRoute.EXPORT_MAPPER -> exportMapper()
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
        webView.open()
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

    private fun exportMapper() {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        mapperExportPicker.launch("dualdex-memory-$timestamp.json")
    }

    fun moveToDisplayTarget(target: DisplayTarget) {
        val manager = getSystemService(DisplayManager::class.java)
        val presentationIds = manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).map { it.displayId }.toSet()
        val displays = manager.displays.map { candidate ->
            CompanionDisplay(
                id = candidate.displayId,
                isDefault = candidate.displayId == Display.DEFAULT_DISPLAY,
                isPresentation = candidate.displayId in presentationIds,
            )
        }
        val currentId = display?.displayId ?: Display.DEFAULT_DISPLAY
        val targetId = DisplayTargetController.resolve(target, currentId, displays)
        if (targetId == currentId) return
        if (intent.getIntExtra(EXTRA_DISPLAY_ATTEMPT, Int.MIN_VALUE) == targetId) return
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(targetId)
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(EXTRA_DISPLAY_ATTEMPT, targetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            options.toBundle(),
        )
        finish()
    }

    private fun showRecovery(failure: Throwable?) {
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
        private const val EXTRA_DISPLAY_ATTEMPT = "com.darkaxt.dualdex.DISPLAY_ATTEMPT"
    }
}
