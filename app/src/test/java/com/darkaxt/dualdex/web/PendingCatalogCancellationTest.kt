package com.darkaxt.dualdex.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PendingCatalogCancellationTest {
    @Test
    fun completeAfterReleasesTheWaiterWhenTheTransitionThrows() {
        var completions = 0
        val cancellation = PendingCatalogCancellation { completions++ }
        val failure = IllegalStateException("session transition failed")

        val thrown = assertThrows(IllegalStateException::class.java) {
            cancellation.completeAfter<Unit> { throw failure }
        }

        assertSame(failure, thrown)
        assertEquals(1, completions)
    }

    @Test
    fun completeAfterRunsTheTransitionBeforeReleasingAndReturnsItsResult() {
        val events = mutableListOf<String>()
        val cancellation = PendingCatalogCancellation { events += "complete" }

        val token = cancellation.completeAfter {
            events += "transition"
            42
        }

        assertEquals(42, token)
        assertEquals(listOf("transition", "complete"), events)
        cancellation.complete()
        assertEquals("completion is released exactly once", 2, events.size)
    }

    @Test
    fun completeAfterWithoutPendingCancellationOnlyRunsTheTransition() {
        val none: PendingCatalogCancellation? = null

        assertEquals("observed", none.completeAfter { "observed" })
    }
}
