package com.darkaxt.dualdex.setup

import android.content.ContentResolver
import android.net.Uri
import com.darkaxt.dualdex.retroarch.ConfigDocumentStore
import com.darkaxt.dualdex.retroarch.ConfigInstallTransaction
import com.darkaxt.dualdex.retroarch.ConfigRecoveryRecord
import com.darkaxt.dualdex.retroarch.RetroArchSaveConfig
import com.darkaxt.dualdex.retroarch.RetroArchSaveSettings
import com.darkaxt.dualdex.storage.DocumentTreeAccess
import com.darkaxt.dualdex.storage.LocatedTreeDocument
import com.darkaxt.dualdex.storage.TreeDocument

class SafRetroArchConfigStore(
    resolver: ContentResolver,
    treeUri: Uri,
) : ConfigDocumentStore {
    private val access = DocumentTreeAccess(resolver, treeUri)
    private val config: LocatedTreeDocument by lazy {
        val matches = access.children(access.root)
            .filter { it.name == CONFIG_NAME }
            .map { LocatedTreeDocument(access.root, it) }
        require(matches.size == 1) {
            when {
                matches.isEmpty() -> "retroarch.cfg was not found at the root of the selected folder; select the folder that directly contains the active file"
                else -> "multiple retroarch.cfg files were found at the selected root"
            }
        }
        matches.single()
    }

    override fun readConfig(): ByteArray = access.read(config.document)

    override fun writeConfig(bytes: ByteArray) = access.write(config.document, bytes)

    override fun readRecovery(): ByteArray? = validRecoveries()
        .maxByOrNull { it.record.revision }
        ?.record
        ?.bytes
        ?: sidecar(RECOVERY_NAME)?.let(access::read)

    override fun writeRecovery(bytes: ByteArray) {
        val active = validRecoveries().maxByOrNull { it.record.revision }
        val targetName = when (active?.name) {
            RECOVERY_A_NAME -> RECOVERY_B_NAME
            RECOVERY_B_NAME -> RECOVERY_A_NAME
            else -> if (sidecar(RECOVERY_A_NAME) == null) RECOVERY_A_NAME else RECOVERY_B_NAME
        }
        writeSidecar(
            targetName,
            ConfigRecoveryRecord(bytes, revision = (active?.record?.revision ?: 0) + 1).serialize(),
        )
    }

    override fun deleteRecovery() {
        listOf(RECOVERY_NAME, RECOVERY_A_NAME, RECOVERY_B_NAME).forEach(::deleteSidecar)
    }

    override fun readTransaction(): ConfigInstallTransaction? = validTransactions()
        .maxWithOrNull(compareBy<StoredTransaction> { it.transaction.revision }.thenBy { it.transaction.state.ordinal })
        ?.transaction

    override fun writeTransaction(transaction: ConfigInstallTransaction) {
        val activeName = validTransactions()
            .maxWithOrNull(compareBy<StoredTransaction> { it.transaction.revision }.thenBy { it.transaction.state.ordinal })
            ?.name
        val targetName = if (activeName == TRANSACTION_A_NAME) TRANSACTION_B_NAME else TRANSACTION_A_NAME
        writeSidecar(targetName, transaction.serialize())
    }

    override fun deleteTransaction() {
        listOf(TRANSACTION_NAME, TRANSACTION_A_NAME, TRANSACTION_B_NAME).forEach(::deleteSidecar)
    }

    fun readSaveSettings(): RetroArchSaveSettings = RetroArchSaveConfig.read(readConfig())

    private fun validRecoveries(): List<StoredRecovery> =
        listOf(RECOVERY_A_NAME, RECOVERY_B_NAME).mapNotNull { name ->
            val document = sidecar(name) ?: return@mapNotNull null
            runCatching { ConfigRecoveryRecord.deserialize(access.read(document)) }
                .getOrNull()
                ?.let { StoredRecovery(name, it) }
        }

    private fun validTransactions(): List<StoredTransaction> =
        listOf(TRANSACTION_NAME, TRANSACTION_A_NAME, TRANSACTION_B_NAME).mapNotNull { name ->
            val document = sidecar(name) ?: return@mapNotNull null
            runCatching { ConfigInstallTransaction.deserialize(access.read(document)) }
                .getOrNull()
                ?.let { StoredTransaction(name, it) }
        }

    private fun writeSidecar(name: String, bytes: ByteArray) {
        val document = sidecar(name) ?: access.create(config.parent, name)
        access.write(document, bytes)
    }

    private fun deleteSidecar(name: String) {
        sidecar(name)?.let(access::delete)
    }

    private fun sidecar(name: String): TreeDocument? {
        val matches = access.children(config.parent).filter { it.name == name }
        require(matches.size <= 1) { "multiple $name documents were found at the selected root" }
        return matches.singleOrNull()
    }

    private data class StoredRecovery(
        val name: String,
        val record: ConfigRecoveryRecord,
    )

    private data class StoredTransaction(
        val name: String,
        val transaction: ConfigInstallTransaction,
    )

    companion object {
        const val CONFIG_NAME = "retroarch.cfg"
        const val RECOVERY_NAME = "retroarch.cfg.dualdex-recovery"
        const val RECOVERY_A_NAME = "retroarch.cfg.dualdex-recovery-a"
        const val RECOVERY_B_NAME = "retroarch.cfg.dualdex-recovery-b"
        const val TRANSACTION_NAME = "retroarch.cfg.dualdex-transaction"
        const val TRANSACTION_A_NAME = "retroarch.cfg.dualdex-transaction-a"
        const val TRANSACTION_B_NAME = "retroarch.cfg.dualdex-transaction-b"
    }
}
