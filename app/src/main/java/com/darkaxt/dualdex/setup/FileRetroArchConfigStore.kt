package com.darkaxt.dualdex.setup

import com.darkaxt.dualdex.retroarch.ConfigDocumentStore
import com.darkaxt.dualdex.retroarch.RetroArchSaveConfig
import com.darkaxt.dualdex.retroarch.RetroArchSaveSettings
import java.io.File
import java.io.FileOutputStream

class FileRetroArchConfigStore(
    private val config: File,
) : ConfigDocumentStore {
    private val recovery = File(requireNotNull(config.parentFile), SafRetroArchConfigStore.RECOVERY_NAME)

    override fun readConfig(): ByteArray {
        require(config.isFile) { "retroarch.cfg is not a readable file: ${config.path}" }
        return config.readBytes()
    }

    override fun writeConfig(bytes: ByteArray) = config.writeSynced(bytes)

    override fun readRecovery(): ByteArray? = recovery.takeIf(File::isFile)?.readBytes()

    override fun writeRecovery(bytes: ByteArray) = recovery.writeSynced(bytes)

    override fun deleteRecovery() {
        if (recovery.exists()) check(recovery.delete()) { "could not delete verified RetroArch recovery file" }
    }

    fun readSaveSettings(): RetroArchSaveSettings = RetroArchSaveConfig.read(readConfig())

    private fun File.writeSynced(bytes: ByteArray) {
        parentFile?.mkdirs()
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
