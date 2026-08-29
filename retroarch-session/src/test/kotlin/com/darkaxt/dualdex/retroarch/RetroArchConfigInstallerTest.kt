package com.darkaxt.dualdex.retroarch

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroArchConfigInstallerTest {
    @Test
    fun recoveryJournalRetainsTheLastValidGenerationAcrossAPartialSidecarWrite() {
        val first = ConfigRecoveryRecord("original config".toByteArray(), revision = 1)
        val second = ConfigRecoveryRecord("new config".toByteArray(), revision = 2)
        val truncatedSecond = second.serialize().copyOf(24)

        val retained = requireNotNull(ConfigRecoveryRecord.latestValid(listOf(first.serialize(), truncatedSecond)))
        assertEquals(1L, retained.revision)
        assertArrayEquals("original config".toByteArray(), retained.bytes)

        val latest = requireNotNull(ConfigRecoveryRecord.latestValid(listOf(first.serialize(), second.serialize())))
        assertEquals(2L, latest.revision)
        assertArrayEquals("new config".toByteArray(), latest.bytes)
    }

    @Test
    fun journalRetainsTheLastValidGenerationAcrossAPartialSidecarWrite() {
        val prepared = ConfigInstallTransaction(
            originalSha256 = "a".repeat(64),
            intendedSha256 = "b".repeat(64),
            state = ConfigInstallTransactionState.PREPARED,
            revision = 1,
        )
        val applied = prepared.copy(state = ConfigInstallTransactionState.APPLIED, revision = 2)
        val truncatedApplied = applied.serialize().copyOf(24)

        assertEquals(prepared, ConfigInstallTransaction.latestValid(listOf(prepared.serialize(), truncatedApplied)))
        assertEquals(applied, ConfigInstallTransaction.latestValid(listOf(prepared.serialize(), applied.serialize())))
    }

    @Test
    fun verifiedInstallPersistsRecoveryAndTransactionBeforeReplacingConfig() {
        val store = FakeStore("video_driver = \"vulkan\"\r\n".toByteArray())

        val result = RetroArchConfigInstaller.install(store, 55355)

        assertTrue(result is ConfigInstallResult.Installed)
        assertEquals(
            listOf(
                "read-transaction",
                "read-config",
                "read-recovery",
                "write-recovery",
                "read-recovery",
                "write-transaction-PREPARED",
                "read-transaction",
                "write-config",
                "read-config",
                "write-transaction-APPLIED",
                "read-transaction",
                "delete-recovery",
                "delete-transaction",
            ),
            store.events,
        )
        assertFalse(store.recoveryPresent)
        assertNull(store.transaction)
        assertTrue(ConfigDocumentEditor.verifyNetworkCommands(store.config, 55355).valid)
    }

    @Test
    fun failedConfigReadbackKeepsTheContextualRecoveryAndTransaction() {
        val store = FakeStore("network_cmd_enable = \"false\"\n".toByteArray()).apply {
            corruptConfigWrites = true
        }

        val result = RetroArchConfigInstaller.install(store, 55355)

        assertTrue(result is ConfigInstallResult.Failed)
        assertTrue(store.recoveryPresent)
        assertEquals("network_cmd_enable = \"false\"\n", store.recovery.toString(Charsets.UTF_8))
        assertEquals(ConfigInstallTransactionState.PREPARED, store.transaction?.state)
        assertFalse(store.events.contains("delete-recovery"))
        assertFalse(store.events.contains("delete-transaction"))
    }

    @Test
    fun retryRestoresTheVerifiedFirstBackupWithoutOverwritingIt() {
        val original = "network_cmd_enable = \"false\"\n".toByteArray()
        val store = FakeStore(original).apply { corruptConfigWrites = true }
        assertTrue(RetroArchConfigInstaller.install(store, 55355) is ConfigInstallResult.Failed)
        assertEquals(1, store.events.count { it == "write-recovery" })

        store.corruptConfigWrites = false
        val result = RetroArchConfigInstaller.install(store, 55355)

        assertTrue(result is ConfigInstallResult.Installed)
        assertEquals(1, store.events.count { it == "write-recovery" })
        assertEquals(3, store.events.count { it == "write-config" })
        assertFalse(store.recoveryPresent)
        assertNull(store.transaction)
        assertTrue(ConfigDocumentEditor.verifyNetworkCommands(store.config, 55355).valid)
    }

    @Test
    fun processRestartFinalizesAnAlreadyVerifiedPreparedWrite() {
        val store = FakeStore("video_driver = \"vulkan\"\n".toByteArray()).apply {
            failAppliedTransactionWrites = true
        }
        assertTrue(RetroArchConfigInstaller.install(store, 55355) is ConfigInstallResult.Failed)
        assertTrue(ConfigDocumentEditor.verifyNetworkCommands(store.config, 55355).valid)
        assertEquals(ConfigInstallTransactionState.PREPARED, store.transaction?.state)

        store.failAppliedTransactionWrites = false
        store.events.clear()
        val result = RetroArchConfigInstaller.install(store, 55355)

        assertTrue(result is ConfigInstallResult.Installed)
        assertFalse(store.events.contains("write-config"))
        assertEquals("write-transaction-APPLIED", store.events[2])
        assertFalse(store.recoveryPresent)
        assertNull(store.transaction)
    }

    @Test
    fun alreadyConfiguredDoesNotDeleteAnUnownedRecoveryDocument() {
        val configured = ConfigDocumentEditor.patchNetworkCommands(byteArrayOf(), 55355).updated
        val store = FakeStore(configured).apply {
            recovery = "unowned recovery".toByteArray()
            recoveryPresent = true
        }

        assertEquals(ConfigInstallResult.AlreadyConfigured, RetroArchConfigInstaller.install(store, 55355))
        assertTrue(store.recoveryPresent)
        assertFalse(store.events.contains("delete-recovery"))
    }

    @Test
    fun `allocation failure becomes a terminal recoverable result`() {
        val store = object : ConfigDocumentStore by FakeStore(byteArrayOf()) {
            override fun readConfig(): ByteArray = throw OutOfMemoryError("injected")
        }

        assertTrue(RetroArchConfigInstaller.install(store, 55355) is ConfigInstallResult.Failed)
    }

    @Test
    fun `first provider timeout retains ordinary retry guidance`() {
        val store = object : ConfigDocumentStore by FakeStore(byteArrayOf()) {
            override fun readConfig(): ByteArray =
                throw IllegalStateException("SAF provider operation timed out; retry is available")
        }

        assertEquals(
            ConfigInstallResult.Failed("RetroArch configuration could not be updated safely. Retry the setup action."),
            RetroArchConfigInstaller.install(store, 55355),
        )
    }

    @Test
    fun `provider reset requirement is terminal guidance rather than a generic retry`() {
        val store = object : ConfigDocumentStore by FakeStore(byteArrayOf()) {
            override fun readConfig(): ByteArray =
                throw IllegalStateException("SAF provider needs reset or app restart after repeated timed-out operations")
        }

        assertEquals(
            ConfigInstallResult.Failed(
                "The selected document provider timed out or has an unfinished write. Reset or reconnect the provider, or fully restart DualDex before trying setup again.",
            ),
            RetroArchConfigInstaller.install(store, 55355),
        )
    }

    @Test
    fun `sanitizes storage failure detail`() {
        val store = object : ConfigDocumentStore by FakeStore(byteArrayOf()) {
            override fun readConfig(): ByteArray = throw IllegalStateException("/private/RetroArch/retroarch.cfg")
        }

        assertEquals(
            ConfigInstallResult.Failed("RetroArch configuration could not be updated safely. Retry the setup action."),
            RetroArchConfigInstaller.install(store, 55355),
        )
    }

    private class FakeStore(initial: ByteArray) : ConfigDocumentStore {
        var config = initial
        var recovery = byteArrayOf()
        var recoveryPresent = false
        var transaction: ConfigInstallTransaction? = null
        var corruptConfigWrites = false
        var failAppliedTransactionWrites = false
        val events = mutableListOf<String>()

        override fun readConfig(): ByteArray {
            events += "read-config"
            return config.copyOf()
        }

        override fun writeConfig(bytes: ByteArray) {
            events += "write-config"
            config = if (corruptConfigWrites) byteArrayOf() else bytes.copyOf()
        }

        override fun readRecovery(): ByteArray? {
            events += "read-recovery"
            return recovery.takeIf { recoveryPresent }?.copyOf()
        }

        override fun writeRecovery(bytes: ByteArray) {
            events += "write-recovery"
            recovery = bytes.copyOf()
            recoveryPresent = true
        }

        override fun deleteRecovery() {
            events += "delete-recovery"
            recoveryPresent = false
        }

        override fun readTransaction(): ConfigInstallTransaction? {
            events += "read-transaction"
            return transaction
        }

        override fun writeTransaction(transaction: ConfigInstallTransaction) {
            events += "write-transaction-${transaction.state}"
            if (failAppliedTransactionWrites && transaction.state == ConfigInstallTransactionState.APPLIED) {
                error("injected transaction failure")
            }
            this.transaction = transaction
        }

        override fun deleteTransaction() {
            events += "delete-transaction"
            transaction = null
        }
    }
}
