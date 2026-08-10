package com.darkaxt.dualdex.overlay

import com.enrpau.dualscreendex.companion.model.CompanionSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlaySizeStoreTest {
    @Test
    fun persistsOnlyTheClampedNormalizedScale() {
        var settings = CompanionSettings()
        val store = OverlaySizeStore({ settings }, { settings = it })

        store.writeScale(0.62)
        assertEquals(0.62, store.readScale(), 0.0)

        store.writeScale(0.1)
        assertEquals(0.45, store.readScale(), 0.0)
    }
}
