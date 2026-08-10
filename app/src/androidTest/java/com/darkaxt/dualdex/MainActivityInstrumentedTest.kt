package com.darkaxt.dualdex

import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {
    @Test
    fun productionShellUsesLoopbackAndRespectsSystemBars() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val app = activity.application as DualDexApplication
                assertTrue(app.localOrigin?.startsWith("http://127.0.0.1:") == true)

                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                val host = content.getChildAt(0) as ViewGroup
                val webView = host.getChildAt(0) as? WebView
                assertNotNull(webView)
                assertTrue(host.paddingTop > 0)
                assertTrue(host.paddingBottom > 0)
            }
        }
    }
}
