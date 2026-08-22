package com.darkaxt.dualdex.knowledge

import com.darkaxt.dualdex.save.SaveDocumentSource
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

enum class CheckpointStorage { PORTABLE_SIDECAR, APP_PRIVATE_FALLBACK }

interface KnowledgeCheckpointStore {
    fun readExact(source: SaveDocumentSource, key: SaveCheckpointKey): KnowledgeLedger?
    fun write(source: SaveDocumentSource, checkpoint: SaveKnowledgeCheckpoint): CheckpointStorage
}

class SaveKnowledgeCheckpointStore(
    private val fallbackRoot: File,
    private val codec: SaveKnowledgeCheckpointCodec = SaveKnowledgeCheckpointCodec(),
) : KnowledgeCheckpointStore {
    override fun readExact(source: SaveDocumentSource, key: SaveCheckpointKey): KnowledgeLedger? {
        val siblingBytes = runCatching {
            source.atomicSiblingTarget?.read(sidecarName(source))
        }.getOrNull()
        codec.decodeExact(siblingBytes ?: byteArrayOf(), key)?.let { return it.ledger }
        val fallback = fallbackFile(key)
        if (!fallback.isFile) return null
        return runCatching { codec.decodeExact(fallback.readBytes(), key)?.ledger }.getOrNull()
    }

    override fun write(source: SaveDocumentSource, checkpoint: SaveKnowledgeCheckpoint): CheckpointStorage {
        val target = source.atomicSiblingTarget
        if (target != null) {
            val written = runCatching {
                target.replace(sidecarName(source), codec.encode(checkpoint.copy(portable = true)))
            }.isSuccess
            if (written) return CheckpointStorage.PORTABLE_SIDECAR
        }
        writeFallback(checkpoint.copy(portable = false))
        return CheckpointStorage.APP_PRIVATE_FALLBACK
    }

    private fun writeFallback(checkpoint: SaveKnowledgeCheckpoint) {
        check(fallbackRoot.isDirectory || fallbackRoot.mkdirs()) { "checkpoint directory could not be created" }
        val destination = fallbackFile(checkpoint.key)
        val temporary = fallbackRoot.resolve(".${destination.name}.dualdex.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(codec.encode(checkpoint))
                output.fd.sync()
            }
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            temporary.delete()
        }
    }

    private fun sidecarName(source: SaveDocumentSource) = "${source.name}.dualdex.json"

    private fun fallbackFile(key: SaveCheckpointKey) = fallbackRoot.resolve(
        "${key.romSha256.lowercase()}.${key.saveIdentity.lowercase()}.${key.saveFileSha256.lowercase()}.json",
    )
}
