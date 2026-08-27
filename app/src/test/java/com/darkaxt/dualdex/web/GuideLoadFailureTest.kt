package com.darkaxt.dualdex.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GuideLoadFailureTest {
    @Test
    fun `memory exhaustion receives a stable player-facing explanation`() {
        val cause = OutOfMemoryError("synthetic allocator detail")

        val failure = GuideLoadFailure.from(cause)

        assertEquals(
            "There was not enough free memory to open this game guide. Close other apps and try again.",
            failure.message,
        )
        assertSame(cause, failure.cause)
    }

    @Test
    fun `ordinary failures never expose their implementation message`() {
        val cause = IllegalStateException("pointer 0x1234 rejected at MAP_HEADERS")

        val failure = GuideLoadFailure.from(cause)

        assertEquals("This game guide could not be opened. You can try again.", failure.message)
        assertSame(cause, failure.cause)
    }

    @Test
    fun `an already sanitized failure is not wrapped again`() {
        val failure = GuideLoadFailure.from(OutOfMemoryError("synthetic allocator detail"))

        assertSame(failure, GuideLoadFailure.from(failure))
    }
}
