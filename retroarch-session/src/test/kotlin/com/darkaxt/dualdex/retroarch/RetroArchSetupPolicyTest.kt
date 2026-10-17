package com.darkaxt.dualdex.retroarch

import org.junit.Assert.*
import org.junit.Test

class RetroArchSetupPolicyTest {
    @Test
    fun writingAnInactiveCopyProvesOnlyThatDocument() {
        val active = "network_cmd_enable = \"false\"\nvideo_fullscreen = \"true\"\n".toByteArray()
        val publicCopy = Store(active)
        val installed = RetroArchConfigInstaller.install(publicCopy, 55355)
        assertTrue(installed is ConfigInstallResult.Installed)
        assertTrue(ConfigDocumentEditor.verifyNetworkCommands(publicCopy.readConfig(), 55355).valid)
        assertEquals("VERIFIED", RetroArchSaveConfig.read(publicCopy.readConfig()).autosaveStatus)
        assertEquals("UNVERIFIED", RetroArchSetupPolicy.RUNTIME_AUTOSAVE_STATUS)
        assertTrue(publicCopy.readConfig().toString(Charsets.UTF_8).contains("video_fullscreen = \"true\""))
        assertTrue(active.toString(Charsets.UTF_8).contains("network_cmd_enable = \"false\""))

        val written = RetroArchSetupPolicy.afterInstall(installed, RetroArchConnection.DISCONNECTED, true)
        assertEquals("RESTART_REQUIRED", written.configState)
        assertTrue(written.restartRequired)
        assertTrue(written.message.contains("does not prove RetroArch loads that file"))

        val already = RetroArchConfigInstaller.install(publicCopy, 55355)
        assertEquals(ConfigInstallResult.AlreadyConfigured, already)
        val afterReopen = RetroArchSetupPolicy.afterInstall(already, RetroArchConnection.DISCONNECTED, false)
        assertEquals("UNVERIFIED", afterReopen.configState)
        assertFalse(afterReopen.restartRequired)
        assertTrue(afterReopen.message.contains("inactive copy"))
    }

    @Test
    fun aConfiguredFileCannotVerifyMalformedOrMissingReplies() {
        for (connection in listOf(RetroArchConnection.DISCONNECTED, RetroArchConnection.MALFORMED)) {
            val result = RetroArchSetupPolicy.afterInstall(ConfigInstallResult.AlreadyConfigured, connection, false)
            assertEquals("UNVERIFIED", result.configState)
            assertFalse(result.restartRequired)
        }
    }

    @Test
    fun onlyWellFormedStatusesVerifyTheInterfaceWithoutClaimingTheLoadedPath() {
        val accepted = setOf(RetroArchConnection.CONTENTLESS, RetroArchConnection.PLAYING, RetroArchConnection.PAUSED)
        for (connection in RetroArchConnection.entries) {
            assertEquals(connection in accepted, connection.hasValidCommandStatus)
            val result = RetroArchSetupPolicy.afterInstall(ConfigInstallResult.AlreadyConfigured, connection, false)
            assertEquals(connection in accepted, result.configState == "VERIFIED")
            if (connection in accepted) assertTrue(result.message.contains("does not identify which config file"))
        }
    }

    @Test
    fun fileReadbackCannotClearAnOutstandingRestartEvenWithALiveOldListener() {
        for (connection in RetroArchConnection.entries) {
            val result = RetroArchSetupPolicy.afterInstall(ConfigInstallResult.AlreadyConfigured, connection, true)
            assertEquals("RESTART_REQUIRED", result.configState)
            assertTrue(result.restartRequired)
        }
    }

    @Test
    fun installerAndRestartHandshakeRemainSeparateEndToEnd() {
        val store = Store(ByteArray(0))
        val verifier = RestartVerifier()
        val installed = RetroArchConfigInstaller.install(store, 55355)
        assertTrue(installed is ConfigInstallResult.Installed)
        verifier.requireRestart(RetroArchConnection.PLAYING)
        assertEquals("RESTART_REQUIRED", RetroArchSetupPolicy.afterInstall(installed, RetroArchConnection.PLAYING, verifier.restartRequired).configState)
        assertFalse(verifier.observe(RetroArchConnection.PLAYING))
        assertFalse(verifier.observe(RetroArchConnection.DISCONNECTED))
        assertFalse(verifier.observe(RetroArchConnection.MALFORMED))
        assertTrue(verifier.restartRequired)
        assertTrue(verifier.observe(RetroArchConnection.CONTENTLESS))
        val unchanged = RetroArchConfigInstaller.install(store, 55355)
        assertEquals("VERIFIED", RetroArchSetupPolicy.afterInstall(unchanged, RetroArchConnection.CONTENTLESS, verifier.restartRequired).configState)
        assertNull(store.readRecovery())
        assertNull(store.readTransaction())
    }

    @Test
    fun failurePreservesThePendingRestartAndItsRecoveryMessage() {
        val failure = ConfigInstallResult.Failed("provider needs reset")
        for (pending in listOf(false, true)) {
            val result = RetroArchSetupPolicy.afterInstall(failure, RetroArchConnection.PLAYING, pending)
            assertEquals("FAILED", result.configState)
            assertEquals(pending, result.restartRequired)
            assertEquals(failure.message, result.message)
        }
    }

    private class Store(bytes: ByteArray) : ConfigDocumentStore {
        private var config = bytes.copyOf()
        private var recovery: ByteArray? = null
        private var transaction: ConfigInstallTransaction? = null
        override fun readConfig() = config.copyOf()
        override fun writeConfig(bytes: ByteArray) { config = bytes.copyOf() }
        override fun readRecovery() = recovery?.copyOf()
        override fun writeRecovery(bytes: ByteArray) { recovery = bytes.copyOf() }
        override fun deleteRecovery() { recovery = null }
        override fun readTransaction() = transaction
        override fun writeTransaction(transaction: ConfigInstallTransaction) { this.transaction = transaction }
        override fun deleteTransaction() { transaction = null }
    }
}
