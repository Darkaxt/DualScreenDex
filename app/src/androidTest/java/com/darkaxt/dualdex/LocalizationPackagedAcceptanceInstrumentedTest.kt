package com.darkaxt.dualdex

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizationPackagedAcceptanceInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun packagedWebViewAndNativeRecoveryUseThePersistedInterfaceLocale() {
        val application = instrumentation.targetContext.applicationContext as QaDualDexApplication
        val origin = waitForValue("loopback origin") { application.localOrigin }

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertEquals(
                    200,
                    request(
                        origin,
                        "/api/actions",
                        "{\"type\":\"SETTINGS\",\"interfaceLanguage\":\"ES\"}",
                    ),
                )
                waitFor("Spanish Android resources") {
                    application.resources.configuration.locales[0].language == "es"
                }

                val webView = waitForValue("localized packaged WebView") { webView(scenario) }
                waitForJavascript(
                    webView,
                    "document.documentElement.lang === 'es' && " +
                        "document.querySelector('input[aria-label=\"CARGAR ROM O ZIP\"]') !== null",
                )

                scenario.onActivity { activity ->
                    MainActivity::class.java
                        .getDeclaredMethod("showRecovery", Throwable::class.java)
                        .apply { isAccessible = true }
                        .invoke(activity, null as Any?)
                }
                waitFor("Spanish native recovery") {
                    text(scenario, application.getString(R.string.recovery_title)) != null
                }
                assertNotNull(text(scenario, "DualDex no pudo iniciar su interfaz local"))

                assertEquals(
                    200,
                    request(
                        origin,
                        "/api/actions",
                        "{\"type\":\"SETTINGS\",\"interfaceLanguage\":\"AUTO\"}",
                    ),
                )
                waitFor("automatic Android resources") {
                    application.resources.configuration.locales[0].language != "es"
                }
            }
        } finally {
            request(
                origin,
                "/api/actions",
                "{\"type\":\"SETTINGS\",\"interfaceLanguage\":\"AUTO\"}",
            )
        }
    }

    private fun request(origin: String, path: String, body: String): Int {
        val connection = URL("$origin$path").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = HTTP_TIMEOUT_MS.toInt()
            connection.readTimeout = HTTP_TIMEOUT_MS.toInt()
            connection.doOutput = true
            val bytes = body.toByteArray()
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            connection.responseCode.also { status ->
                (if (status >= 400) connection.errorStream else connection.inputStream)?.use { it.readBytes() }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun webView(scenario: ActivityScenario<MainActivity>): WebView? {
        val result = AtomicReference<WebView?>()
        scenario.onActivity { activity -> result.set(findWebView(activity.findViewById(android.R.id.content))) }
        return result.get()
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun text(scenario: ActivityScenario<MainActivity>, expected: String): TextView? {
        val result = AtomicReference<TextView?>()
        scenario.onActivity { activity ->
            result.set(findText(activity.findViewById(android.R.id.content), expected))
        }
        return result.get()
    }

    private fun findText(view: View, expected: String): TextView? {
        if (view is TextView && view.text.toString() == expected) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findText(view.getChildAt(index), expected)?.let { return it }
        }
        return null
    }

    private fun waitForJavascript(webView: WebView, expression: String) {
        waitFor("JavaScript condition") { evaluateJavascript(webView, expression) == "true" }
    }

    private fun evaluateJavascript(webView: WebView, expression: String): String {
        val result = AtomicReference<String>()
        val completed = CountDownLatch(1)
        instrumentation.runOnMainSync {
            webView.evaluateJavascript(expression) { value ->
                result.set(value)
                completed.countDown()
            }
        }
        assertTrue(completed.await(HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        return result.get()
    }

    private fun waitFor(label: String, condition: () -> Boolean) {
        waitForValue(label) { true.takeIf { condition() } }
    }

    private fun <T : Any> waitForValue(label: String, supplier: () -> T?): T {
        val deadline = SystemClock.elapsedRealtime() + DEFAULT_TIMEOUT_MS
        var value = supplier()
        while (value == null && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(POLL_INTERVAL_MS)
            value = supplier()
        }
        return requireNotNull(value) { "$label did not become available within ${DEFAULT_TIMEOUT_MS}ms" }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 100L
        const val HTTP_TIMEOUT_MS = 5_000L
        const val DEFAULT_TIMEOUT_MS = 20_000L
    }
}
