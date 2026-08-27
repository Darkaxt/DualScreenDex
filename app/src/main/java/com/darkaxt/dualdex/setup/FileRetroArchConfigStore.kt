package com.darkaxt.dualdex.setup

import com.darkaxt.dualdex.retroarch.ConfigDocumentStore
import com.darkaxt.dualdex.retroarch.ConfigInstallTransaction
import com.darkaxt.dualdex.retroarch.RetroArchSaveConfig
import com.darkaxt.dualdex.retroarch.RetroArchSaveSettings
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class FileRetroArchConfigStore(
    private val config: File,
) : ConfigDocumentStore {
    private val recovery = File(requireNotNull(config.parentFile), SafRetroArchConfigStore.RECOVERY_NAME)
    private val transaction = File(config.parentFile, SafRetroArchConfigStore.TRANSACTION_NAME)

    override fun readConfig(): ByteArray {
        require(config.isFile) { "retroarch.cfg is not a readable file: ${config.path}" }
        return config.readBytes()
    }

    override fun writeConfig(bytes: ByteArray) = config.writeAtomicSynced(bytes)

    override fun readRecovery(): ByteArray? = recovery.takeIf(File::isFile)?.readBytes()

    override fun writeRecovery(bytes: ByteArray) = recovery.writeAtomicSynced(bytes)

    override fun deleteRecovery() {
        if (recovery.exists()) check(recovery.delete()) { "could not delete verified RetroArch recovery file" }
    }

    override fun readTransaction(): ConfigInstallTransaction? = transaction
        .takeIf(File::isFile)
        ?.readBytes()
        ?.let(ConfigInstallTransaction::deserialize)

    override fun writeTransaction(transaction: ConfigInstallTransaction) {
        this.transaction.writeAtomicSynced(transaction.serialize())
    }

    override fun deleteTransaction() {
        if (transaction.exists()) check(transaction.delete()) { "could not delete verified RetroArch transaction file" }
    }

    fun readSaveSettings(): RetroArchSaveSettings = RetroArchSaveConfig.read(readConfig())

    private fun File.writeAtomicSynced(bytes: ByteArray) {
        val directory = requireNotNull(parentFile)
        directory.mkdirs()
        val temporary = Files.createTempFile(directory.toPath(), ".$name.", ".dualdex-tmp").toFile()
        try {
            temporary.writeSynced(bytes)
            try {
                Files.move(
                    temporary.toPath(),
                    toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private fun File.writeSynced(bytes: ByteArray) {
        FileOutputStream(this, false).use { output ->
            output.write(bytes)
            output.flush()
            output.fd.sync()
        }
    }

    companion object {
        fun findPublic(roots: List<File>): File? = roots.asSequence()
            .mapNotNull { root ->
                root.listFiles().orEmpty()
                    .singleOrNull { it.isDirectory && it.name.equals("RetroArch", ignoreCase = true) }
            }
            .mapNotNull { directory ->
                directory.listFiles().orEmpty()
                    .singleOrNull { it.isFile && it.name.equals(SafRetroArchConfigStore.CONFIG_NAME, ignoreCase = true) }
            }
            .mapNotNull { runCatching { it.canonicalFile }.getOrNull() }
            .sortedBy { it.path.lowercase() }
            .firstOrNull()
    }
}
