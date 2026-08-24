package com.darkaxt.dualdex.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionSurfaceOwnershipTest {
    @Test
    fun reportsOnlyOneOwnedLiveSurface() {
        val ownership = CompanionSurfaceOwnership()
        val docked = FakeSurface()
        val overlay = FakeSurface()

        assertEquals(0, ownership.activeSurfaceCount())
        ownership.activate(docked)
        assertEquals(1, ownership.activeSurfaceCount())
        ownership.activate(overlay)
        assertEquals(1, ownership.activeSurfaceCount())
        ownership.release(overlay)
        assertEquals(0, ownership.activeSurfaceCount())
    }

    @Test
    fun `one surface owns polling while hidden and superseded surfaces release work`() {
        val ownership = CompanionSurfaceOwnership()
        val docked = FakeSurface()
        val overlay = FakeSurface()

        ownership.activate(docked)
        ownership.pause(docked)
        ownership.activate(overlay)

        assertEquals(1, docked.resumes)
        assertEquals(1, docked.pauses)
        assertEquals(1, docked.releases)
        assertTrue(docked.released)
        assertEquals(1, overlay.resumes)
        assertTrue(ownership.isOwnedBy(overlay))
        assertFalse(ownership.isOwnedBy(docked))

        ownership.release(overlay)

        assertEquals(1, overlay.releases)
        assertFalse(ownership.isOwnedBy(overlay))
    }

    @Test
    fun `resuming the same surface does not destroy it`() {
        val ownership = CompanionSurfaceOwnership()
        val surface = FakeSurface()

        ownership.activate(surface)
        ownership.pause(surface)
        ownership.activate(surface)

        assertEquals(2, surface.resumes)
        assertEquals(1, surface.pauses)
        assertEquals(0, surface.releases)
    }

    private class FakeSurface : CompanionSurface {
        var resumes = 0
        var pauses = 0
        var releases = 0
        override var released = false
            private set

        override fun resumeSurface() { resumes++ }
        override fun pauseSurface() { pauses++ }
        override fun releaseSurface() { releases++; released = true }
    }
}
