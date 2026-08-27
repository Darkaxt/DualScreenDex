package com.darkaxt.dualdex.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEpochGateTest {
    private val first = VerifiedSessionIdentity("a".repeat(64), "file:///first.gba")
    private val second = VerifiedSessionIdentity("b".repeat(64), "file:///second.gba")

    @Test
    fun commitRunsOnlyWhileTheExpectedEpochRemainsCurrent() {
        val gate = SessionEpochGate()
        val firstToken = requireNotNull(gate.observe(first))
        var commits = 0

        assertTrue(gate.commitIfCurrent(firstToken.epoch) { commits++ })
        gate.observe(second)
        assertFalse(gate.commitIfCurrent(firstToken.epoch) { commits++ })

        assertEquals(1, commits)
    }

    @Test
    fun identityChangesAndCloseInvalidateEveryOlderWorkToken() {
        val gate = SessionEpochGate()
        val firstToken = requireNotNull(gate.observe(first))

        assertTrue(gate.isCurrent(firstToken))
        assertTrue(gate.isCurrent(requireNotNull(gate.capture(first))))

        val secondToken = requireNotNull(gate.observe(second))
        assertNotEquals(firstToken.epoch, secondToken.epoch)
        assertFalse(gate.isCurrent(firstToken))
        assertNull(gate.capture(first))
        assertTrue(gate.isCurrent(secondToken))

        assertNull(gate.observe(null))
        assertFalse(gate.isCurrent(secondToken))
        assertNotNull(gate.observe(first))

        val finalToken = requireNotNull(gate.capture(first))
        gate.close()
        assertFalse(gate.isCurrent(finalToken))
        assertNull(gate.observe(first))
        assertNull(gate.capture(first))
    }
}
