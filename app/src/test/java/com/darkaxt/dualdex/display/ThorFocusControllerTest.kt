package com.darkaxt.dualdex.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThorFocusControllerTest {
    @Test
    fun enforcesTopFocusAndRestoresTheModeThatPrecededDualDex() {
        val backend = FakeBackend(current = ThorFocusMode.AUTO)
        val controller = ThorFocusController(backend)

        assertEquals(
            ThorFocusResult.ENFORCED,
            controller.sync(enabled = true, docked = true, secondaryDisplay = true),
        )
        assertEquals(ThorFocusMode.TOP, backend.current)
        assertEquals(ThorFocusMode.AUTO, backend.previous)
        assertTrue(backend.owned)

        assertEquals(
            ThorFocusResult.RESTORED,
            controller.sync(enabled = false, docked = true, secondaryDisplay = true),
        )
        assertEquals(ThorFocusMode.AUTO, backend.current)
        assertFalse(backend.owned)
    }

    @Test
    fun repeatedEnforcementDoesNotReplaceTheOriginalMode() {
        val backend = FakeBackend(current = ThorFocusMode.BOTTOM)
        val controller = ThorFocusController(backend)

        controller.sync(enabled = true, docked = true, secondaryDisplay = true)
        controller.sync(enabled = true, docked = true, secondaryDisplay = true)

        assertEquals(ThorFocusMode.BOTTOM, backend.previous)
        assertEquals(1, backend.writes.count { it == ThorFocusMode.TOP })
    }

    @Test
    fun restorationDoesNotOverrideAChoiceMadeAfterDualDex() {
        val backend = FakeBackend(current = ThorFocusMode.AUTO)
        val controller = ThorFocusController(backend)
        controller.sync(enabled = true, docked = true, secondaryDisplay = true)
        backend.current = ThorFocusMode.BOTTOM

        assertEquals(
            ThorFocusResult.RELEASED,
            controller.sync(enabled = false, docked = true, secondaryDisplay = true),
        )
        assertEquals(ThorFocusMode.BOTTOM, backend.current)
        assertFalse(backend.owned)
    }

    @Test
    fun requestsPermissionWithoutChangingTheSystemSetting() {
        val backend = FakeBackend(current = ThorFocusMode.AUTO, writable = false)

        assertEquals(
            ThorFocusResult.PERMISSION_REQUIRED,
            ThorFocusController(backend).sync(enabled = true, docked = true, secondaryDisplay = true),
        )
        assertTrue(backend.writes.isEmpty())
        assertFalse(backend.owned)
    }

    @Test
    fun leavingDockedSecondaryModeRestoresOwnedFocus() {
        val backend = FakeBackend(current = ThorFocusMode.AUTO)
        val controller = ThorFocusController(backend)
        controller.sync(enabled = true, docked = true, secondaryDisplay = true)

        assertEquals(
            ThorFocusResult.RESTORED,
            controller.sync(enabled = true, docked = false, secondaryDisplay = true),
        )
        assertEquals(ThorFocusMode.AUTO, backend.current)
    }

    private class FakeBackend(
        override var current: Int,
        override val supported: Boolean = true,
        override val writable: Boolean = true,
    ) : ThorFocusBackend {
        override var previous: Int? = null
        override var owned: Boolean = false
        val writes = mutableListOf<Int>()

        override fun write(mode: Int): Boolean {
            writes += mode
            current = mode
            return true
        }
    }
}
