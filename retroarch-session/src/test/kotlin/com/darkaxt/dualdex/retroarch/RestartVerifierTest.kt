package com.darkaxt.dualdex.retroarch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestartVerifierTest {
    @Test
    fun aLiveListenerCannotVerifyANewConfigWithoutDisconnectingFirst() {
        val verifier = RestartVerifier()

        verifier.requireRestart(RetroArchConnection.PLAYING)

        assertFalse(verifier.observe(RetroArchConnection.PLAYING))
        assertFalse(verifier.observe(RetroArchConnection.DISCONNECTED))
        assertTrue(verifier.observe(RetroArchConnection.CONTENTLESS))
        assertFalse(verifier.restartRequired)
    }

    @Test
    fun startingFromAStoppedRetroArchVerifiesOnTheFirstLiveReply() {
        val verifier = RestartVerifier()

        verifier.requireRestart(RetroArchConnection.DISCONNECTED)

        assertTrue(verifier.observe(RetroArchConnection.CONTENTLESS))
        assertFalse(verifier.restartRequired)
    }

    @Test
    fun malformedReplyCannotVerifyAListenerOrCompleteARestart() {
        val verifier = RestartVerifier()

        assertFalse(verifier.observe(RetroArchConnection.MALFORMED))
        verifier.requireRestart(RetroArchConnection.DISCONNECTED)
        assertFalse(verifier.observe(RetroArchConnection.MALFORMED))
        assertTrue(verifier.restartRequired)
        assertTrue(verifier.observe(RetroArchConnection.PAUSED))
    }

    @Test
    fun malformedReplyDoesNotEstablishTheRequiredDisconnect() {
        val verifier = RestartVerifier()

        verifier.requireRestart(RetroArchConnection.PLAYING)
        assertFalse(verifier.observe(RetroArchConnection.MALFORMED))
        assertFalse(verifier.observe(RetroArchConnection.PAUSED))
        assertTrue(verifier.restartRequired)
    }

    @Test
    fun ordinaryConnectivityNeedsNoRestartHandshake() {
        val verifier = RestartVerifier()

        assertTrue(verifier.observe(RetroArchConnection.PLAYING))
        assertFalse(verifier.observe(RetroArchConnection.DISCONNECTED))
    }
}
