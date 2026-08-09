package com.darkaxt.dualdex

import android.graphics.Color
import android.os.Bundle
import android.content.pm.ApplicationInfo
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.darkaxt.dualdex.rom.RomDocumentPicker
import com.darkaxt.dualdex.web.DualDexWebView

class MainActivity : AppCompatActivity() {
    private lateinit var picker: RomDocumentPicker
    private var companionWebView: DualDexWebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        picker = RomDocumentPicker(this)
        showCompanionOrRecovery()
    }

    override fun onDestroy() {
        picker.cancel()
        companionWebView?.destroy()
        companionWebView = null
        super.onDestroy()
    }

    private fun showCompanionOrRecovery() {
        val application = application as DualDexApplication
        val origin = application.localOrigin
        if (origin == null) {
            showRecovery(application.startupFailure)
            return
        }
        val webView = DualDexWebView(this, origin, picker) { reason -> showRecovery(IllegalStateException(reason)) }
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
}
