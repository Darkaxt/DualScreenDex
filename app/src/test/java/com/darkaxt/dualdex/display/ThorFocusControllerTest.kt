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

    @Test
    fun readFailureReturnsWriteFailedInsteadOfThrowing() {
        val backend = FakeBackend(
            current = ThorFocusMode.AUTO,
            readFailure = secureSettingsFailure(),
        )

        assertEquals(
            ThorFocusResult.WRITE_FAILED,
            ThorFocusController(backend).sync(enabled = true, docked = true, secondaryDisplay = true),
        )
        assertFalse(backend.owned)
    }

    @Test
    fun writeFailureReturnsWriteFailedInsteadOfThrowing() {
        val backend = FakeBackend(
            current = ThorFocusMode.AUTO,
            writeFailure = secureSettingsFailure(),
        )

        assertEquals(
            ThorFocusResult.WRITE_FAILED,
            ThorFocusController(backend).sync(enabled = true, docked = true, secondaryDisplay = true),
        )
        assertFalse(backend.owned)
    }

    @Test
    fun failedRestorationRetainsOwnershipAndPreviousMode() {
        val backend = FakeBackend(
            current = ThorFocusMode.TOP,
            writeFailure = secureSettingsFailure(),
        ).apply {
            previous = ThorFocusMode.BOTTOM
            owned = true
        }

        assertEquals(
            ThorFocusResult.WRITE_FAILED,
            ThorFocusController(backend).sync(enabled = false, docked = true, secondaryDisplay = true),
        )
        assertTrue(backend.owned)
        assertEquals(ThorFocusMode.BOTTOM, backend.previous)
    }

    private fun secureSettingsFailure() =
        IllegalArgumentException("You cannot keep your settings in the secure settings.")

    private class FakeBackend(
        current: Int,
        override val supported: Boolean = true,
        override val writable: Boolean = true,
        private val readFailure: IllegalArgumentException? = null,
        private val writeFailure: IllegalArgumentException? = null,
    ) : ThorFocusBackend {
        private var storedCurrent = current

        override var current: Int
            get() = readFailure?.let { throw it } ?: storedCurrent
            set(value) {
                storedCurrent = value
            }

        override var previous: Int? = null
        override var owned: Boolean = false
        val writes = mutableListOf<Int>()

        override fun write(mode: Int): Boolean {
            writes += mode
            writeFailure?.let { throw it }
            current = mode
            return true
        }
    }
}
