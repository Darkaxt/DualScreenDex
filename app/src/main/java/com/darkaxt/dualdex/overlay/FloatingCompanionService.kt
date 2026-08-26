package com.darkaxt.dualdex.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.darkaxt.dualdex.DualDexApplication
import com.darkaxt.dualdex.MainActivity
import com.darkaxt.dualdex.R
import com.darkaxt.dualdex.web.DualDexWebView
import com.darkaxt.dualdex.web.NativeSetupRoute
import kotlin.math.abs

class FloatingCompanionService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: PokeBallBubbleView? = null
    private var panel: FrameLayout? = null
    private var panelWebView: DualDexWebView? = null
    private var panelLayout: WindowManager.LayoutParams? = null
    private var panelVisible = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DOCK) {
            returnToDockedActivity()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        showBubble()
        (application as DualDexApplication).updateDisplayMode("OVERLAY")
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (panelVisible) refitPanel()
    }

    override fun onDestroy() {
        hidePanel()
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        running = false
        (application as? DualDexApplication)?.updateDisplayMode("DOCKED")
        super.onDestroy()
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "DualDex floating companion", NotificationManager.IMPORTANCE_LOW),
        )
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dockIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingCompanionService::class.java).setAction(ACTION_DOCK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dualdex_ball)
            .setContentTitle("DualDex overlay")
            .setContentText("Tap the floating Poké Ball to show or hide the 4:3 companion.")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Dock", dockIntent)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        startForeground(NOTIFICATION_ID, notification, type)
    }

    private fun showBubble() {
        val existing = bubble
        if (existing != null) {
            existing.setRomSprite((application as DualDexApplication).ballSpritePng(POKE_BALL_ID))
            return
        }
        val size = dp(BUBBLE_DP)
        val bounds = windowManager.currentWindowMetrics.bounds
        val layout = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (bounds.width() - size - dp(12)).coerceAtLeast(0)
            y = ((bounds.height() - size) / 2).coerceAtLeast(0)
        }
        val view = PokeBallBubbleView(this).apply {
            setRomSprite((application as DualDexApplication).ballSpritePng(POKE_BALL_ID))
            setOnClickListener { togglePanel() }
            setOnTouchListener(BubbleDragListener(layout))
        }
        windowManager.addView(view, layout)
        bubble = view
    }

    private fun togglePanel() {
        if (panelVisible) hidePanel() else showPanel()
    }

    private fun showPanel() {
        val origin = (application as DualDexApplication).localOrigin ?: return
        val host = panel ?: createPanel(origin).also { panel = it }
        if (host.parent == null) {
            val placement = fittedPanel()
            val layout = WindowManager.LayoutParams(
                    placement.width,
                    placement.height,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = placement.x
                    y = placement.y
                }
            panelLayout = layout
            windowManager.addView(host, layout)
        }
        panelVisible = true
        panelWebView?.let { webView ->
            (application as DualDexApplication).activateCompanionSurface(webView)
            webView.open()
        }
        bubble?.setRomSprite((application as DualDexApplication).ballSpritePng(POKE_BALL_ID))
    }

    private fun hidePanel() {
        val host = panel
        host?.takeIf { it.parent != null }?.let { windowManager.removeView(it) }
        panelWebView?.let { (application as DualDexApplication).releaseCompanionSurface(it) }
        panelWebView = null
        host?.removeAllViews()
        panel = null
        panelLayout = null
        panelVisible = false
    }

    private fun createPanel(origin: String): FrameLayout {
        val webView = DualDexWebView(
            this,
            origin,
            picker = null,
            onNativeSetupRoute = ::handleNativeRoute,
            onMainFrameFailure = { hidePanel() },
        )
        panelWebView = webView
        return FrameLayout(this).apply {
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.rgb(17, 29, 26))
                setStroke(dp(2), Color.rgb(112, 133, 91))
                cornerRadius = dp(10).toFloat()
            }
            addView(webView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(
                OverlayResizeHandle(this@FloatingCompanionService).apply {
                    setOnTouchListener(PanelResizeListener())
                },
                FrameLayout.LayoutParams(dp(40), dp(40), Gravity.END or Gravity.BOTTOM),
            )
        }
    }

    private fun fittedPanel(scale: Double = (application as DualDexApplication).currentOverlayScale()): OverlayPanelPlacement {
        val metrics = windowManager.currentWindowMetrics
        val bounds = metrics.bounds
        val systemInsets = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
        )
        return OverlayPanelSizer.fit(
            screenWidth = bounds.width(),
            screenHeight = bounds.height(),
            insets = OverlayInsets(systemInsets.left, systemInsets.top, systemInsets.right, systemInsets.bottom),
            scale = scale,
            minimumWidth = dp(MINIMUM_PANEL_WIDTH_DP),
        )
    }

    private fun refitPanel(scale: Double = (application as DualDexApplication).currentOverlayScale()) {
        val host = panel?.takeIf { it.parent != null } ?: return
        val layout = panelLayout ?: return
        val placement = fittedPanel(scale)
        layout.width = placement.width
        layout.height = placement.height
        layout.x = placement.x
        layout.y = placement.y
        windowManager.updateViewLayout(host, layout)
    }

    private inner class PanelResizeListener : View.OnTouchListener {
        private var startWidth = 0
        private var downX = 0f
        private var downY = 0f
        private var pendingScale = 1.0

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startWidth = panelLayout?.width ?: return false
                    downX = event.rawX
                    downY = event.rawY
                    pendingScale = (application as DualDexApplication).currentOverlayScale()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val maximum = fittedPanel(OverlayPanelSizer.MAX_SCALE).width.toDouble()
                    val diagonalDelta = maxOf(event.rawX - downX, (event.rawY - downY) * 4f / 3f)
                    pendingScale = OverlayPanelSizer.clampScale((startWidth + diagonalDelta) / maximum)
                    refitPanel(pendingScale)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    (application as DualDexApplication).updateOverlayScale(pendingScale)
                    view.performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    refitPanel()
                    return true
                }
            }
            return false
        }
    }

    private fun handleNativeRoute(route: NativeSetupRoute) {
        when (route) {
            NativeSetupRoute.SHOW_OVERLAY -> Unit
            NativeSetupRoute.DOCK_OVERLAY -> returnToDockedActivity()
            NativeSetupRoute.GRANT_ALL_FILES -> startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            NativeSetupRoute.OPEN_RETROARCH -> (application as DualDexApplication).retroArchSetup?.launchRetroArch()
            NativeSetupRoute.EXPORT_MAPPER -> startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_EXPORT_MAPPER, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            NativeSetupRoute.EXPORT_PERFORMANCE -> startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_EXPORT_PERFORMANCE, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            NativeSetupRoute.EXPORT_COMPATIBILITY -> startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_EXPORT_COMPATIBILITY, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            NativeSetupRoute.GRANT_RETROARCH,
            NativeSetupRoute.GRANT_ROMS -> startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    private inner class BubbleDragListener(
        private val layout: WindowManager.LayoutParams,
    ) : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var downX = 0f
        private var downY = 0f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layout.x
                    startY = layout.y
                    downX = event.rawX
                    downY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val bounds = windowManager.currentWindowMetrics.bounds
                    layout.x = (startX + (event.rawX - downX).toInt()).coerceIn(0, (bounds.width() - view.width).coerceAtLeast(0))
                    layout.y = (startY + (event.rawY - downY).toInt()).coerceIn(0, (bounds.height() - view.height).coerceAtLeast(0))
                    windowManager.updateViewLayout(view, layout)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val clickDistance = dp(8)
                    if (abs(event.rawX - downX) < clickDistance && abs(event.rawY - downY) < clickDistance) view.performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> return true
            }
            return false
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun returnToDockedActivity() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        )
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "dualdex-overlay"
        private const val NOTIFICATION_ID = 41
        private const val BUBBLE_DP = 64
        private const val MINIMUM_PANEL_WIDTH_DP = 320
        private const val POKE_BALL_ID = 4
        private const val ACTION_SHOW = "com.darkaxt.dualdex.overlay.SHOW"
        private const val ACTION_DOCK = "com.darkaxt.dualdex.overlay.DOCK"

        @Volatile
        var running: Boolean = false
            private set

        fun show(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, FloatingCompanionService::class.java).setAction(ACTION_SHOW),
            )
        }

        fun dock(context: Context) {
            context.stopService(Intent(context, FloatingCompanionService::class.java))
        }

        fun dockAndSurface(context: Context) {
            when (HeadlessDockPolicy.resolve(running)) {
                HeadlessDockAction.REQUEST_SERVICE_DOCK_AND_SURFACE -> ContextCompat.startForegroundService(
                    context,
                    Intent(context, FloatingCompanionService::class.java).setAction(ACTION_DOCK),
                )
                HeadlessDockAction.NO_ACTION -> Unit
            }
        }
    }
}
