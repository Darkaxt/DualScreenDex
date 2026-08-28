package com.darkaxt.dualdex.retroarch

import java.security.MessageDigest
import java.util.Base64

enum class ConfigInstallTransactionState { PREPARED, APPLIED }

data class ConfigInstallTransaction(
    val originalSha256: String,
    val intendedSha256: String,
    val state: ConfigInstallTransactionState,
    val revision: Long = 0,
) {
    init {
        require(originalSha256.isSha256()) { "RetroArch transaction original hash is invalid" }
        require(intendedSha256.isSha256()) { "RetroArch transaction intended hash is invalid" }
        require(revision >= 0) { "RetroArch transaction revision is invalid" }
    }

    fun serialize(): ByteArray = listOf(
        FORMAT_VERSION,
        state.name,
        originalSha256,
        intendedSha256,
        revision.toString(),
    ).joinToString("\n", postfix = "\n").toByteArray(Charsets.US_ASCII)

    companion object {
        private const val FORMAT_VERSION = "dualdex-config-transaction-v1"

        fun latestValid(records: Iterable<ByteArray>): ConfigInstallTransaction? = records
            .mapNotNull { record -> runCatching { deserialize(record) }.getOrNull() }
            .maxWithOrNull(compareBy<ConfigInstallTransaction> { it.revision }.thenBy { it.state.ordinal })

        fun deserialize(bytes: ByteArray): ConfigInstallTransaction {
            val fields = bytes.toString(Charsets.US_ASCII).lines().filter(String::isNotEmpty)
            require(fields.size == 5 && fields[0] == FORMAT_VERSION) {
                "RetroArch config transaction is malformed"
            }
            val state = runCatching { ConfigInstallTransactionState.valueOf(fields[1]) }
                .getOrElse { throw IllegalArgumentException("RetroArch config transaction state is invalid") }
            return ConfigInstallTransaction(
                originalSha256 = fields[2],
                intendedSha256 = fields[3],
                state = state,
                revision = fields[4].toLongOrNull()
                    ?: throw IllegalArgumentException("RetroArch config transaction revision is invalid"),
            )
        }
    }
}

class ConfigRecoveryRecord(
    bytes: ByteArray,
    val revision: Long,
) {
    private val retainedBytes = bytes.copyOf()
    val bytes: ByteArray get() = retainedBytes.copyOf()

    init {
        require(revision >= 0) { "RetroArch recovery revision is invalid" }
    }

    fun serialize(): ByteArray = listOf(
        FORMAT_VERSION,
        revision.toString(),
        retainedBytes.sha256(),
        Base64.getEncoder().encodeToString(retainedBytes),
    ).joinToString("\n", postfix = "\n").toByteArray(Charsets.US_ASCII)

    companion object {
        private const val FORMAT_VERSION = "dualdex-config-recovery-v1"

        fun latestValid(records: Iterable<ByteArray>): ConfigRecoveryRecord? = records
            .mapNotNull { record -> runCatching { deserialize(record) }.getOrNull() }
            .maxByOrNull(ConfigRecoveryRecord::revision)

        fun deserialize(bytes: ByteArray): ConfigRecoveryRecord {
            val fields = bytes.toString(Charsets.US_ASCII).split('\n')
            require(fields.size == 5 && fields[0] == FORMAT_VERSION && fields[4].isEmpty()) {
                "RetroArch recovery record is malformed"
            }
            val revision = fields[1].toLongOrNull()
                ?: throw IllegalArgumentException("RetroArch recovery revision is invalid")
            require(fields[2].isSha256()) { "RetroArch recovery hash is invalid" }
            val content = runCatching { Base64.getDecoder().decode(fields[3]) }
                .getOrElse { throw IllegalArgumentException("RetroArch recovery payload is invalid") }
            require(content.sha256() == fields[2]) { "RetroArch recovery payload did not verify" }
            return ConfigRecoveryRecord(content, revision)
        }
    }
}

interface ConfigDocumentStore {
    fun readConfig(): ByteArray
    fun writeConfig(bytes: ByteArray)
    fun readRecovery(): ByteArray?
    fun writeRecovery(bytes: ByteArray)
    fun deleteRecovery()
    fun readTransaction(): ConfigInstallTransaction?
    fun writeTransaction(transaction: ConfigInstallTransaction)
    fun deleteTransaction()
}

sealed interface ConfigInstallResult {
    data class Installed(val changedKeys: Set<String>) : ConfigInstallResult
    data object AlreadyConfigured : ConfigInstallResult
    data class Failed(val message: String) : ConfigInstallResult
}

object RetroArchConfigInstaller {
    private const val FAILURE_MESSAGE = "RetroArch configuration could not be updated safely. Retry the setup action."
    private const val PROVIDER_RECOVERY_MESSAGE =
        "The selected document provider timed out or has an unfinished write. Reset or reconnect the provider, or fully restart DualDex before trying setup again."

    fun install(store: ConfigDocumentStore, port: Int): ConfigInstallResult = try {
        val transaction = store.readTransaction()
        if (transaction == null) {
            installFresh(store, port)
        } else {
            resume(store, port, transaction)
        }
    } catch (_: OutOfMemoryError) {
        ConfigInstallResult.Failed(FAILURE_MESSAGE)
    } catch (failure: Exception) {
        ConfigInstallResult.Failed(
            if (failure.message?.contains("needs reset or app restart") == true) {
                PROVIDER_RECOVERY_MESSAGE
            } else {
                FAILURE_MESSAGE
            },
        )
    }

    private fun installFresh(store: ConfigDocumentStore, port: Int): ConfigInstallResult {
        val original = store.readConfig()
        val patch = ConfigDocumentEditor.patchNetworkCommands(original, port)
        if (patch.changedKeys.isEmpty()) return ConfigInstallResult.AlreadyConfigured

        val originalSha256 = original.sha256()
        val existingRecovery = store.readRecovery()
        if (existingRecovery == null) {
            store.writeRecovery(original)
        } else {
            require(existingRecovery.sha256() == originalSha256) {
                "An existing RetroArch recovery document belongs to another installation"
            }
        }
        require(store.readRecovery()?.sha256() == originalSha256) {
            "RetroArch recovery document did not verify"
        }

        val transaction = ConfigInstallTransaction(
            originalSha256 = originalSha256,
            intendedSha256 = patch.updated.sha256(),
            state = ConfigInstallTransactionState.PREPARED,
            revision = 1,
        )
        writeAndVerifyTransaction(store, transaction)
        return applyPrepared(store, port, transaction, patch.updated, patch.changedKeys)
    }

    private fun resume(
        store: ConfigDocumentStore,
        port: Int,
        transaction: ConfigInstallTransaction,
    ): ConfigInstallResult {
        val current = store.readConfig()
        if (current.sha256() == transaction.intendedSha256) {
            verifyConfigured(current, port)
            if (transaction.state != ConfigInstallTransactionState.APPLIED) {
                writeAndVerifyTransaction(store, transaction.copy(state = ConfigInstallTransactionState.APPLIED, revision = transaction.revision + 1))
            }
            finish(store)
            return ConfigInstallResult.Installed(emptySet())
        }

        val recovery = requireNotNull(store.readRecovery()) {
            "RetroArch recovery document is missing for an unfinished installation"
        }
        require(recovery.sha256() == transaction.originalSha256) {
            "RetroArch recovery document does not match the unfinished installation"
        }

        if (current.sha256() != transaction.originalSha256) {
            store.writeConfig(recovery)
            require(store.readConfig().sha256() == transaction.originalSha256) {
                "RetroArch config could not be restored before retry"
            }
        }

        val patch = ConfigDocumentEditor.patchNetworkCommands(recovery, port)
        if (patch.updated.sha256() != transaction.intendedSha256) {
            finish(store)
            return installFresh(store, port)
        }
        val prepared = if (transaction.state == ConfigInstallTransactionState.PREPARED) {
            transaction
        } else {
            transaction.copy(
                state = ConfigInstallTransactionState.PREPARED,
                revision = transaction.revision + 1,
            )
        }
        if (transaction.state != ConfigInstallTransactionState.PREPARED) {
            writeAndVerifyTransaction(store, prepared)
        }
        return applyPrepared(store, port, prepared, patch.updated, patch.changedKeys)
    }

    private fun applyPrepared(
        store: ConfigDocumentStore,
        port: Int,
        transaction: ConfigInstallTransaction,
        intended: ByteArray,
        changedKeys: Set<String>,
    ): ConfigInstallResult {
        store.writeConfig(intended)
        val readback = store.readConfig()
        require(readback.sha256() == transaction.intendedSha256 && readback.contentEquals(intended)) {
            "RetroArch config readback differs from the requested patch"
        }
        verifyConfigured(readback, port)
        writeAndVerifyTransaction(store, transaction.copy(state = ConfigInstallTransactionState.APPLIED, revision = transaction.revision + 1))
        finish(store)
        return ConfigInstallResult.Installed(changedKeys)
    }

    private fun verifyConfigured(bytes: ByteArray, port: Int) {
        val verification = ConfigDocumentEditor.verifyNetworkCommands(bytes, port)
        require(verification.valid) { verification.errors.joinToString("; ") }
    }

    private fun writeAndVerifyTransaction(
        store: ConfigDocumentStore,
        transaction: ConfigInstallTransaction,
    ) {
        store.writeTransaction(transaction)
        require(store.readTransaction() == transaction) {
            "RetroArch config transaction did not verify"
        }
    }

    private fun finish(store: ConfigDocumentStore) {
        store.deleteRecovery()
        store.deleteTransaction()
    }
}

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { byte -> "%02x".format(byte) }

private fun String.isSha256(): Boolean = length == 64 && all { it in '0'..'9' || it in 'a'..'f' }
