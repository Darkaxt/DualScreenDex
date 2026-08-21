package com.darkaxt.dualdex.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.darkaxt.dualdex.rom.RomDocumentPicker

@SuppressLint("SetJavaScriptEnabled")
class DualDexWebView(
    context: Context,
    private val origin: String,
    picker: RomDocumentPicker?,
    private val onNativeSetupRoute: (NativeSetupRoute) -> Unit,
    onMainFrameFailure: (String) -> Unit,
) : WebView(context) {
    private val trustedOrigin = Uri.parse(origin)

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?,
            ): Boolean = if (picker != null && filePathCallback != null) {
                picker.open(filePathCallback)
                true
            } else {
                false
            }

            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                val rendered = "${message.sourceId()}:${message.lineNumber()} ${message.message()}"
                when (message.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> Log.e(CONSOLE_TAG, rendered)
                    ConsoleMessage.MessageLevel.WARNING -> Log.w(CONSOLE_TAG, rendered)
                    else -> Log.d(CONSOLE_TAG, rendered)
                }
                return true
            }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (isTrusted(request.url)) return false
                NativeSetupRoute.parse(request.url.toString())?.let(onNativeSetupRoute)
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                if (request.isForMainFrame) onMainFrameFailure(error.description.toString())
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (url != null && !isTrusted(Uri.parse(url))) stopLoading()
            }
        }
    }

    fun open() = loadUrl("$origin/")

    fun dispatchCompanionBack() = evaluateJavascript(
        "window.dispatchEvent(new Event('dualdexback',{cancelable:true}))",
        null,
    )

    private fun isTrusted(candidate: Uri): Boolean =
        candidate.scheme == trustedOrigin.scheme &&
            candidate.host == trustedOrigin.host &&
            candidate.port == trustedOrigin.port

    private companion object {
        const val CONSOLE_TAG = "DualDexConsole"
    }
}
