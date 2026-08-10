package com.darkaxt.dualdex.retroarch

interface ConfigDocumentStore {
    fun readConfig(): ByteArray
    fun writeConfig(bytes: ByteArray)
    fun readRecovery(): ByteArray?
    fun writeRecovery(bytes: ByteArray)
    fun deleteRecovery()
}

sealed interface ConfigInstallResult {
    data class Installed(val changedKeys: Set<String>) : ConfigInstallResult
    data object AlreadyConfigured : ConfigInstallResult
    data class Failed(val message: String) : ConfigInstallResult
}

object RetroArchConfigInstaller {
    fun install(store: ConfigDocumentStore, port: Int): ConfigInstallResult = try {
        val original = store.readConfig()
        val patch = ConfigDocumentEditor.patchNetworkCommands(original, port)
        if (patch.changedKeys.isEmpty()) {
            if (store.readRecovery() != null) store.deleteRecovery()
            ConfigInstallResult.AlreadyConfigured
        } else {
            store.writeRecovery(original)
            val recovery = store.readRecovery()
            require(recovery != null && recovery.contentEquals(original)) {
                "RetroArch recovery document did not verify"
            }
            store.writeConfig(patch.updated)
            val readback = store.readConfig()
            require(readback.contentEquals(patch.updated)) {
                "RetroArch config readback differs from the requested patch"
            }
            val verification = ConfigDocumentEditor.verifyNetworkCommands(readback, port)
            require(verification.valid) { verification.errors.joinToString("; ") }
            store.deleteRecovery()
            ConfigInstallResult.Installed(patch.changedKeys)
        }
    } catch (failure: Exception) {
        ConfigInstallResult.Failed(failure.message ?: failure.javaClass.simpleName)
    }
}
