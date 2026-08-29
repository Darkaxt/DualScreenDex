package com.darkaxt.dualdex

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.darkaxt.dualdex.overlay.OverlayActivityStarter
import com.darkaxt.dualdex.overlay.OverlaySetupRouteHandler
import com.darkaxt.dualdex.setup.SetupPickerRequest
import com.darkaxt.dualdex.web.NativeSetupRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayPickerDeliveryInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val application = context.applicationContext as QaDualDexApplication

    @Test
    fun overlayNativeRoutesCreateAndDeliverMatchingPickerRequestsThroughProductionRegistry() {
        listOf(
            NativeSetupRoute.GRANT_RETROARCH to PickerExpectation(
                request = SetupPickerRequest.RETROARCH,
                callback = "config",
                initialUri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:RetroArch",
                ),
            ),
            NativeSetupRoute.GRANT_ROMS to PickerExpectation(
                request = SetupPickerRequest.ROMS,
                callback = "rom",
                initialUri = null,
            ),
        ).forEach { (route, expectation) ->
            application.resetPickerDispatches()
            val captured = mutableListOf<Intent>()
            val handler = OverlaySetupRouteHandler(context, OverlayActivityStarter { intent -> captured += intent })

            assertTrue(handler.handleNativeRoute(route))
            val coldIntent = captured.single()
            assertEquals(expectation.request.encoded, coldIntent.getStringExtra(SetupPickerRequest.EXTRA))

            ActivityScenario.launch<MainActivity>(coldIntent).use { scenario ->
                assertForegroundFlags(coldIntent)
                assertEquals(2, application.pickerRegistrationCount())
                assertEquals(listOf(expectation.initialUri), application.pickerLaunches())
                application.deliverLatestPickerResult(Uri.parse("content://qa/cold"))
                assertEquals(listOf(expectation.callback), application.pickerCallbacks())

                assertTrue(handler.handleNativeRoute(route))
                val newIntent = captured.last()
                assertForegroundFlags(newIntent)
                scenario.onActivity { activity -> instrumentation.callActivityOnNewIntent(activity, newIntent) }
                assertEquals(listOf(expectation.initialUri, expectation.initialUri), application.pickerLaunches())
                application.deliverLatestPickerResult(Uri.parse("content://qa/new"))
                assertEquals(listOf(expectation.callback, expectation.callback), application.pickerCallbacks())

                newIntent.removeExtra(SetupPickerRequest.EXTRA)
                scenario.onActivity { activity -> instrumentation.callActivityOnNewIntent(activity, newIntent) }
                assertEquals(listOf(expectation.initialUri, expectation.initialUri), application.pickerLaunches())
            }
        }
    }

    @Test
    fun missingPickerExtraDoesNotOpenEitherDocumentTree() {
        application.resetPickerDispatches()

        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            assertEquals(emptyList<Uri?>(), application.pickerLaunches())
        }
    }

    private fun assertForegroundFlags(intent: Intent) {
        val expected = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        assertEquals(expected, intent.flags and expected)
    }

    private data class PickerExpectation(
        val request: SetupPickerRequest,
        val callback: String,
        val initialUri: Uri?,
    )
}
