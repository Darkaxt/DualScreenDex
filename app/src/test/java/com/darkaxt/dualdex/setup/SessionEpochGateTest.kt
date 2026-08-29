package com.darkaxt.dualdex.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SessionEpochGateTest {
    private val first = VerifiedSessionIdentity("a".repeat(64), "file:///first.gba")
    private val second = VerifiedSessionIdentity("b".repeat(64), "file:///second.gba")

    @Test
    fun commitRunsOnlyWhileTheExpectedEpochRemainsCurrent() {
        val gate = SessionEpochGate()
        val firstToken = requireNotNull(gate.observe(first))
        var commits = 0

        assertTrue(gate.commitIfCurrent(firstToken) { commits++ })
        gate.observe(second)
        assertFalse(gate.commitIfCurrent(firstToken) { commits++ })

        assertEquals(1, commits)
    }

    @Test
    fun commitCallbackDoesNotHoldTheGateMonitorAgainstCurrentStateReaders() {
        val gate = SessionEpochGate()
        val token = requireNotNull(gate.observe(first))
        val readCompleted = CountDownLatch(1)
        var current = false

        assertTrue(
            gate.commitIfCurrent(token) {
                Thread {
                    current = gate.isCurrent(token)
                    readCompleted.countDown()
                }.start()
                assertTrue(readCompleted.await(1, TimeUnit.SECONDS))
            },
        )

        assertTrue(current)
    }

    @Test
    fun verifiedActivationDoesNotAuthorizeSameIdentityAfterReconnect() {
        val gate = SessionEpochGate()
        val activation = SessionActivationCoordinator(gate)
        val beforeLoss = requireNotNull(gate.observe(first))
        assertTrue(activation.begin(beforeLoss, first.sourceId) {})
        assertTrue(activation.finish(beforeLoss, first.sourceId) {})
        assertFalse(
            activation.requiresSourceVerification(
                beforeLoss,
                activeCatalogSha256 = first.romSha256,
                expectedSha256 = first.romSha256,
            ),
        )

        gate.observe(null)
        val reconnect = requireNotNull(gate.observe(first))
        val staleIndex = com.darkaxt.dualdex.retroarch.RomIndexEntry(
            sourceId = first.sourceId,
            sourceName = "first.gba",
            archiveEntry = null,
            platform = com.darkaxt.dualdex.retroarch.RomPlatform.GBA,
            gameBasename = "first",
            crc32 = "12345678",
            sha256 = first.romSha256,
        )

        assertTrue(
            activation.requiresSourceVerification(
                reconnect,
                activeCatalogSha256 = first.romSha256,
                expectedSha256 = first.romSha256,
            ),
        )
        assertFalse(
            com.darkaxt.dualdex.retroarch.RomSessionResolver.verifySha(
                staleIndex,
                actualSha256 = second.romSha256,
            ),
        )
        assertFalse(activation.isVerified(reconnect))
    }

    @Test
    fun reconnectingTheSameIdentityRequiresANewVerificationToken() {
        val gate = SessionEpochGate()
        val verifiedBeforeLoss = requireNotNull(gate.observe(first))
        assertNull(gate.observe(null))

        val reconnect = requireNotNull(gate.observe(first))

        assertNotEquals(verifiedBeforeLoss, reconnect)
        assertFalse(gate.commitIfCurrent(verifiedBeforeLoss) {})
        assertTrue(gate.commitIfCurrent(reconnect) {})
    }

    @Test
    fun cancelledActivationCannotPublishSuccessEvenWhileItsSessionRemainsCurrent() {
        val gate = SessionEpochGate()
        val activations = GuideActivationGate()
        val coordinator = SessionActivationCoordinator(gate, activations)
        val token = requireNotNull(gate.observe(first))
        var setupState = "IDLE"
        assertTrue(coordinator.begin(token, first.sourceId) { setupState = "LOADING" })

        activations.cancel(first.sourceId)
        val committed = coordinator.finish(token, first.sourceId) { setupState = "ACTIVE" }

        assertFalse(committed)
        assertEquals("LOADING", setupState)
        assertFalse(coordinator.isVerified(token))
    }

    @Test
    fun productionActivationCoordinatorRejectsPreparedAAfterSwitchOrClose() {
        listOf(false, true).forEach { close ->
            val gate = SessionEpochGate()
            val coordinator = SessionActivationCoordinator(gate)
            val token = requireNotNull(gate.observe(first))
            var setupState = "IDLE"
            assertTrue(coordinator.begin(token, first.sourceId) { setupState = "LOADING" })

            if (close) {
                gate.close()
                setupState = "CLOSED"
            } else {
                gate.observe(second)
                setupState = "B_ACTIVE"
            }
            val beforeCommit = setupState
            val committed = coordinator.finish(token, first.sourceId) { setupState = "ACTIVE" }

            assertFalse(committed)
            assertEquals(beforeCommit, setupState)
            assertFalse(coordinator.isVerified(token))
        }
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
