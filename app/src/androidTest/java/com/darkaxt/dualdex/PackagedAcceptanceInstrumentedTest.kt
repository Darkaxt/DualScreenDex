package com.darkaxt.dualdex

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import com.darkaxt.dualdex.catalog.AndroidCatalogDatabaseFactory
import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.darkaxt.dualdex.web.NativeSetupRoute
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackagedAcceptanceInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun installedPackageDeliversWebLoopbackRecoveryAndCacheReopen() {
        val application = instrumentation.targetContext.applicationContext as QaDualDexApplication
        val origin = waitForValue("loopback origin") { application.localOrigin }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val webView = waitForValue("packaged WebView") { webView(scenario) }
            waitForJavascript(webView, "document.readyState === 'complete' && document.body.innerText.includes('DUALDEX')")
            screenshot("01-packaged-bootstrap.png")

            val health = request(origin, "/api/health")
            assertEquals(200, health.status)
            assertTrue(health.text.contains("\"ok\":true"))
            verifyBundledJavascript(origin)

            val initial = state(origin)
            val initialVersion = initial["version"].asLong
            application.updateOverlayScale(0.75)
            val changed = waitForValue("native state revision") {
                request(origin, "/api/state?sinceVersion=$initialVersion")
                    .takeIf { it.status == 200 }
            }
            val changedVersion = JsonParser.parseString(changed.text).asJsonObject["version"].asLong
            assertTrue(changedVersion > initialVersion)
            assertEquals(204, request(origin, "/api/state?sinceVersion=$changedVersion").status)

            application.setSharedStorage(granted = true, rootsAvailable = false)
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitFor("failed index projection") {
                application.retroArchSetup?.snapshot()?.message ==
                    "Game discovery could not finish. The folder fallback remains available."
            }
            val openSetup = request(
                origin,
                "/api/actions",
                method = "POST",
                body = "{\"type\":\"SCREEN\",\"screen\":\"SETUP\"}".toByteArray(),
            )
            assertEquals(200, openSetup.status)
            waitForJavascript(
                webView,
                "document.body.innerText.includes('Games could not be indexed')",
            )
            screenshot("02-index-failure.png")

            application.setSharedStorage(granted = true)
            application.retroArchSetup?.refreshStorageAccess()
            waitFor("indexed storage projection", STORAGE_TIMEOUT_MS) {
                val view = application.retroArchSetup?.snapshot()
                view?.storageGrant == "GRANTED" &&
                    view.message != "Game discovery could not finish. The folder fallback remains available."
            }
            assertTrue(state(origin).getAsJsonObject("retroArch")["storageGrant"].asString == "GRANTED")

            val missingAsset = request(origin, "/api/sprites/species/999999.png")
            assertEquals(404, missingAsset.status)

            val brokenLoad = request(
                origin,
                "/api/load?name=broken.zip",
                method = "POST",
                body = "not-a-zip".toByteArray(),
            )
            assertEquals(400, brokenLoad.status)
            assertFalse(application.guideFailureArmed())
            application.prepareGuideFixture()
            waitFor("exact guide fixture indexed") { application.isGuideFixtureIndexed() }
            assertFalse(application.guideFailureArmed())
            application.armGuideFailure()
            try {
                waitFor("sanitized guide failure") {
                    state(origin).getAsJsonObject("retroArch").let { retroArch ->
                        retroArch["resolution"].asString == "FAILED" &&
                            retroArch["message"].asString ==
                            "This game guide could not be opened. You can try again."
                    }
                }
                waitForJavascript(
                    webView,
                    "document.querySelector('[role=alert]')?.textContent.includes('could not be opened') === true && " +
                        "document.querySelector('a[href=\"dualdex://guide/retry\"]') !== null",
                )
                screenshot("03-guide-failure.png")

                val retryHref = javascriptString(
                    evaluateJavascript(
                        webView,
                        "document.querySelector('a[href=\"dualdex://guide/retry\"]')?.getAttribute('href') ?? null",
                    ),
                )
                assertEquals(NativeSetupRoute.RETRY_GUIDE, NativeSetupRoute.parse(retryHref))
                assertEquals(
                    "true",
                    evaluateJavascript(
                        webView,
                        "document.querySelector('a[href=\"dualdex://guide/retry\"]')?.click(); true",
                    ),
                )
                assertTrue(webViewUrl(webView)?.startsWith(origin) == true)
                val retryTransition = mutableListOf("FAILED")
                waitFor("production retry loading") {
                    state(origin).getAsJsonObject("retroArch")["resolution"].asString == "LOADING"
                }
                retryTransition += "LOADING"
                application.releaseGuideRetryTerminal()
                waitFor("production retry terminal") {
                    state(origin).getAsJsonObject("retroArch")["resolution"].asString == "FAILED"
                }
                retryTransition += "FAILED"
                assertEquals(listOf("FAILED", "LOADING", "FAILED"), retryTransition) // FAILED → LOADING → terminal
                assertEquals(2, application.guideLoadAttempts())
            } finally {
                application.resetGuideFailure()
            }
            assertFalse(application.guideFailureArmed())

            val cachedRom = byteArrayOf(1, 3, 3, 7, 9, 2, 6, 5)
            val loaded = RomSourceLoader.load("qa-cache.gba", cachedRom)
            CatalogCache(
                application.filesDir.resolve("catalogs"),
                AndroidCatalogDatabaseFactory,
            ).write(
                ParsedCatalog(
                    romSha256 = loaded.rom.sha256,
                    family = EngineFamily.EMERALD,
                    platform = Platform.GBA,
                    romCrc32 = loaded.rom.crc32,
                ),
                CatalogSourceMetadata.direct("qa-cache.gba", loaded.rom.size, "QA CACHE"),
                CatalogWriteProgress.complete(),
            )

            val cachedLoad = request(
                origin,
                "/api/load?name=qa-cache.gba",
                method = "POST",
                body = cachedRom,
            )
            assertEquals(200, cachedLoad.status)
            waitFor("catalog cache reopen") { state(origin)["catalogReady"].asBoolean }
            val reopened = JsonParser.parseString(request(origin, "/api/bootstrap").text).asJsonObject
            assertEquals(loaded.rom.sha256, reopened.getAsJsonObject("catalog")["hash"].asString)
            waitForJavascript(webView, "document.querySelector('[role=alert]') === null")
            screenshot("04-cache-reopen.png")
        }
    }

    private fun verifyBundledJavascript(origin: String) {
        val index = request(origin, "/")
        assertEquals(200, index.status)
        val source = requireNotNull(
            Regex("""<script[^>]+src="([^"]+\.js)"""").find(index.text)?.groupValues?.get(1),
        ) { "packaged index did not name its JavaScript bundle" }
        val script = request(origin, source)
        assertEquals(200, script.status)
        assertTrue(script.contentType?.contains("javascript") == true)
        assertFalse(script.text.contains("<!doctype", ignoreCase = true))
        assertTrue(script.body.isNotEmpty())
    }

    private fun state(origin: String): JsonObject {
        val response = request(origin, "/api/state")
        assertEquals(200, response.status)
        return JsonParser.parseString(response.text).asJsonObject
    }

    private fun request(
        origin: String,
        path: String,
        method: String = "GET",
        body: ByteArray? = null,
    ): HttpResponse {
        val connection = URL("$origin$path").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = HTTP_TIMEOUT_MS.toInt()
            connection.readTimeout = HTTP_TIMEOUT_MS.toInt()
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val contentType = connection.contentType
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            HttpResponse(status, contentType, bytes)
        } finally {
            connection.disconnect()
        }
    }

    private fun webView(scenario: ActivityScenario<MainActivity>): WebView? {
        val found = AtomicReference<WebView?>()
        scenario.onActivity { activity ->
            found.set(findWebView(activity.findViewById(android.R.id.content)))
        }
        return found.get()
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun waitForJavascript(webView: WebView, expression: String) {
        waitFor("JavaScript condition: $expression", WEB_TIMEOUT_MS) {
            evaluateJavascript(webView, expression) == "true"
        }
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
        assertTrue("JavaScript evaluation timed out", completed.await(HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        return result.get()
    }

    private fun javascriptString(result: String): String {
        assertFalse("JavaScript returned null", result == "null")
        return JsonParser.parseString(result).asString
    }

    private fun webViewUrl(webView: WebView): String? {
        val value = AtomicReference<String?>()
        instrumentation.runOnMainSync { value.set(webView.url) }
        return value.get()
    }

    private fun screenshot(name: String) {
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        PlatformTestStorageRegistry.getInstance().openOutputFile(name).use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
    }

    private fun waitFor(label: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS, condition: () -> Boolean) {
        waitForValue(label, timeoutMs) { true.takeIf { condition() } }
    }

    private fun <T : Any> waitForValue(
        label: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        supplier: () -> T?,
    ): T {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var value = supplier()
        while (value == null && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(POLL_INTERVAL_MS)
            value = supplier()
        }
        return requireNotNull(value) { "$label did not become available within ${timeoutMs}ms" }
    }

    private data class HttpResponse(
        val status: Int,
        val contentType: String?,
        val body: ByteArray,
    ) {
        val text: String get() = body.toString(Charsets.UTF_8)
    }

    private companion object {
        const val POLL_INTERVAL_MS = 100L
        const val HTTP_TIMEOUT_MS = 5_000L
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val WEB_TIMEOUT_MS = 20_000L
        const val STORAGE_TIMEOUT_MS = 30_000L
    }
}
