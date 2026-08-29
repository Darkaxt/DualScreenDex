package com.darkaxt.dualdex

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuideFailureOrderIndependenceInstrumentedTest {
    @Test
    fun guideFailureInjectionIsUnarmedOutsideItsExplicitScenario() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as QaDualDexApplication

        assertFalse(application.guideFailureArmed())
    }
}
