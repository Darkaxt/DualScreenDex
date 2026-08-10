package com.darkaxt.dualdex.retroarch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroArchConfigInstallerTest {
    @Test
    fun verifiedInstallWritesRecoveryFirstAndCleansItAfterReadback() {
        val store = FakeStore("video_driver = \"vulkan\"\r\n".toByteArray())

        val result = RetroArchConfigInstaller.install(store, 55355)

        assertTrue(result is ConfigInstallResult.Installed)
        assertEquals(listOf("read-config", "write-recovery", "read-recovery", "write-config", "read-config", "delete-recovery"), store.events)
        assertFalse(store.recoveryPresent)
        assertTrue(ConfigDocumentEditor.verifyNetworkCommands(store.config, 55355).valid)
    }

    @Test
    fun failedConfigReadbackKeepsTheContextualRecoveryDocument() {
        val store = FakeStore("network_cmd_enable = \"false\"\n".toByteArray()).apply { corruptConfigWrites = true }

        val result = RetroArchConfigInstaller.install(store, 55355)

        assertTrue(result is ConfigInstallResult.Failed)
        assertTrue(store.recoveryPresent)
        assertEquals("network_cmd_enable = \"false\"\n", store.recovery.toString(Charsets.UTF_8))
        assertFalse(store.events.contains("delete-recovery"))
    }

    private class FakeStore(initial: ByteArray) : ConfigDocumentStore {
        var config = initial
        var recovery = byteArrayOf()
        var recoveryPresent = false
        var corruptConfigWrites = false
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
    }
}
