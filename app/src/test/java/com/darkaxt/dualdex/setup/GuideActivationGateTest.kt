package com.darkaxt.dualdex.setup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideActivationGateTest {
    @Test
    fun `failure blocks automatic activation until explicit retry`() {
        val gate = GuideActivationGate()

        assertTrue(gate.tryBegin("source-a"))
        gate.finishFailure("source-a")

        assertTrue(gate.isFailed("source-a"))
        assertFalse(gate.tryBegin("source-a"))
        assertTrue(gate.retry("source-a"))
        assertTrue(gate.tryBegin("source-a"))
    }

    @Test
    fun `failed source never blocks a different source`() {
        val gate = GuideActivationGate()
        assertTrue(gate.tryBegin("source-a"))
        gate.finishFailure("source-a")

        assertTrue(gate.tryBegin("source-b"))
        assertTrue(gate.isLoading("source-b"))
        assertFalse(gate.isFailed("source-b"))
    }

    @Test
    fun `success and reindex clear retained failure state`() {
        val gate = GuideActivationGate()
        assertTrue(gate.tryBegin("source-a"))
        gate.finishFailure("source-a")
        gate.clearFailure()
        assertFalse(gate.isFailed("source-a"))

        assertTrue(gate.tryBegin("source-a"))
        gate.finishSuccess("source-a")
        assertFalse(gate.isLoading("source-a"))
        assertFalse(gate.isFailed("source-a"))
    }

    @Test
    fun `cancelled stale activation releases loading without latching failure`() {
        val gate = GuideActivationGate()

        assertTrue(gate.tryBegin("source-a"))
        gate.cancel("source-a")

        assertFalse(gate.isLoading("source-a"))
        assertFalse(gate.isFailed("source-a"))
        assertTrue(gate.tryBegin("source-b"))
    }

    @Test
    fun staleAttemptCannotCancelOrCompleteTheReconnectAttempt() {
        val gate = GuideActivationGate()
        val identity = VerifiedSessionIdentity("a".repeat(64), "source-a")
        val beforeLoss = SessionWorkToken(1, identity)
        val reconnect = SessionWorkToken(3, identity)

        assertTrue(gate.tryBegin("source-a", beforeLoss))
        assertTrue(gate.tryBegin("source-a", reconnect))
        gate.cancel("source-a", beforeLoss)
        gate.finishSuccess("source-a", beforeLoss)

        assertTrue(gate.isLoading("source-a", reconnect))
        gate.finishSuccess("source-a", reconnect)
        assertFalse(gate.isLoading("source-a", reconnect))
    }

    @Test
    fun `only one activation can be in flight`() {
        val gate = GuideActivationGate()

        assertTrue(gate.tryBegin("source-a"))
        assertFalse(gate.tryBegin("source-b"))
    }
}
