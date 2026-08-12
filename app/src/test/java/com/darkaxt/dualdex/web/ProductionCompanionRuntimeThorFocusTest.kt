package com.darkaxt.dualdex.web

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductionCompanionRuntimeThorFocusTest {
    @Test
    fun nativeFocusStatusInvalidatesAndRebuildsThePublishedState() {
        ProductionCompanionRuntime().use { runtime ->
            val before = runtime.stateView()

            runtime.updateThorFocusStatus("ACTIVE")

            val after = runtime.stateView()
            assertEquals("UNAVAILABLE", before.thorFocusStatus)
            assertEquals("ACTIVE", after.thorFocusStatus)
        }
    }
}
