package com.darkaxt.dualdex.knowledge

import com.darkaxt.dualdex.save.SaveDocumentSource
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

enum class CheckpointStorage { PORTABLE_SIDECAR, APP_PRIVATE_FALLBACK }

sealed interface CheckpointReadResult {
    data class Present(val checkpoint: SaveKnowledgeCheckpoint) : CheckpointReadResult
    data object Absent : CheckpointReadResult
    data class Corrupt(val storage: CheckpointStorage) : CheckpointReadResult
    data class Unavailable(val storage: CheckpointStorage) : CheckpointReadResult
}

sealed interface CheckpointWriteResult {
    data class Durable(val storage: CheckpointStorage) : CheckpointWriteResult
    data class Failed(val storage: CheckpointStorage?) : CheckpointWriteResult
}

interface StagedCheckpoint {
    val value: SaveKnowledgeCheckpoint
}

sealed interface CheckpointStageResult {
    data class Staged(val checkpoint: StagedCheckpoint) : CheckpointStageResult
    data class Failed(val storage: CheckpointStorage?) : CheckpointStageResult
}

private data class DeferredCheckpoint(
    val source: SaveDocumentSource,
    override val value: SaveKnowledgeCheckpoint,
) : StagedCheckpoint

interface KnowledgeCheckpointStore {
    fun read(source: SaveDocumentSource, key: SaveCheckpointKey): CheckpointReadResult
    fun write(source: SaveDocumentSource, checkpoint: SaveKnowledgeCheckpoint): CheckpointWriteResult
    fun readLatest(romSha256: String): CheckpointReadResult = CheckpointReadResult.Absent

    fun stage(source: SaveDocumentSource, checkpoint: SaveKnowledgeCheckpoint): CheckpointStageResult =
        CheckpointStageResult.Staged(DeferredCheckpoint(source, checkpoint))

    fun commit(checkpoint: StagedCheckpoint): CheckpointWriteResult {
        val deferred = checkpoint as? DeferredCheckpoint ?: return CheckpointWriteResult.Failed(null)
        return write(deferred.source, deferred.value)
    }

    fun discard(checkpoint: StagedCheckpoint) = Unit

    fun complete(checkpoint: StagedCheckpoint, accepted: Boolean) {
        discard(checkpoint)
    }
}

class SaveKnowledgeCheckpointStore(
    private val fallbackRoot: File,
    private val codec: SaveKnowledgeCheckpointCodec = SaveKnowledgeCheckpointCodec(),
    private val encodeCheckpoint: (SaveKnowledgeCheckpoint) -> ByteArray = { checkpoint -> codec.encode(checkpoint) },
    private val decodeCheckpoint: (ByteArray) -> SaveKnowledgeCheckpoint? = codec::decode,
    private val acceptedPointerPublisher: (pending: File, accepted: File) -> Unit = { pending, accepted ->
        Files.move(
            pending.toPath(),
            accepted.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    },
) : KnowledgeCheckpointStore {
    private val pointerGson = Gson()

    override fun read(source: SaveDocumentSource, key: SaveCheckpointKey): CheckpointReadResult {
        when (val fallbackRead = readFallback(fallbackFile(key.romSha256))) {
            is FallbackRead.Present -> if (fallbackRead.checkpoint.key == normalizedKey(key)) {
                return CheckpointReadResult.Present(fallbackRead.checkpoint)
            }
            FallbackRead.Corrupt -> return CheckpointReadResult.Corrupt(CheckpointStorage.APP_PRIVATE_FALLBACK)
            FallbackRead.Unavailable ->
                return CheckpointReadResult.Unavailable(CheckpointStorage.APP_PRIVATE_FALLBACK)
            FallbackRead.Absent -> Unit
        }

        source.atomicSiblingTarget?.let { target ->
            val siblingBytes = try {
                target.read(sidecarName(source), SaveKnowledgeCheckpointCodec.MAXIMUM_ENCODED_BYTES)
            } catch (_: OutOfMemoryError) {
                return CheckpointReadResult.Unavailable(CheckpointStorage.PORTABLE_SIDECAR)
            } catch (_: Exception) {
                return CheckpointReadResult.Unavailable(CheckpointStorage.PORTABLE_SIDECAR)
            }
            if (siblingBytes != null) {
                if (siblingBytes.size > SaveKnowledgeCheckpointCodec.MAXIMUM_ENCODED_BYTES) {
                    return CheckpointReadResult.Unavailable(CheckpointStorage.PORTABLE_SIDECAR)
                }
                val decoded = try {
                    decodeCheckpoint(siblingBytes)
                } catch (_: OutOfMemoryError) {
                    return CheckpointReadResult.Unavailable(CheckpointStorage.PORTABLE_SIDECAR)
                }
                decoded?.takeIf { it.key == normalizedKey(key) }
                    ?.let { return CheckpointReadResult.Present(it) }
                if (decoded == null) {
                    return CheckpointReadResult.Corrupt(CheckpointStorage.PORTABLE_SIDECAR)
                }
            }
        }
        return CheckpointReadResult.Absent
    }

    override fun readLatest(romSha256: String): CheckpointReadResult {
        if (!romSha256.matches(SHA256)) return CheckpointReadResult.Corrupt(CheckpointStorage.APP_PRIVATE_FALLBACK)
        return when (val fallbackRead = readFallback(fallbackFile(romSha256))) {
            is FallbackRead.Present -> if (fallbackRead.checkpoint.key.romSha256.equals(romSha256, ignoreCase = true)) {
                CheckpointReadResult.Present(fallbackRead.checkpoint)
            } else {
                CheckpointReadResult.Corrupt(CheckpointStorage.APP_PRIVATE_FALLBACK)
            }
            FallbackRead.Absent -> CheckpointReadResult.Absent
            FallbackRead.Corrupt -> CheckpointReadResult.Corrupt(CheckpointStorage.APP_PRIVATE_FALLBACK)
            FallbackRead.Unavailable -> CheckpointReadResult.Unavailable(CheckpointStorage.APP_PRIVATE_FALLBACK)
        }
    }

    override fun stage(
        source: SaveDocumentSource,
        checkpoint: SaveKnowledgeCheckpoint,
    ): CheckpointStageResult {
        val normalized = checkpoint.copy(
            portable = false,
            key = normalizedKey(checkpoint.key),
        )
        val bytes = try {
            encodeCheckpoint(normalized)
        } catch (_: OutOfMemoryError) {
            return CheckpointStageResult.Failed(CheckpointStorage.APP_PRIVATE_FALLBACK)
        } catch (_: Exception) {
            return CheckpointStageResult.Failed(CheckpointStorage.APP_PRIVATE_FALLBACK)
        }
        if (bytes.size > SaveKnowledgeCheckpointCodec.MAXIMUM_ENCODED_BYTES) {
            return CheckpointStageResult.Failed(CheckpointStorage.APP_PRIVATE_FALLBACK)
        }
        val checkpointDigest = sha256(bytes)
            ?: return CheckpointStageResult.Failed(CheckpointStorage.APP_PRIVATE_FALLBACK)
        val stageId = UUID.randomUUID().toString().lowercase()
        val versionId = "$stageId.$checkpointDigest"
        val versionDirectory = versionDirectory(normalized.key.romSha256)
        val versionFile = versionDirectory.resolve("$versionId.json")
        val pendingVersion = versionDirectory.resolve(".$versionId.tmp")
        val pendingPointer = fallbackRoot.resolve(".${normalized.key.romSha256}.$stageId.accepted.pending")
        return try {
            ensureDirectories(versionDirectory)
            writeDurable(pendingVersion, bytes)
            Files.move(
                pendingVersion.toPath(),
                versionFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
            val pointer = AcceptedCheckpointPointer(
                checkpointVersionId = versionId,
                checkpointDigestSha256 = checkpointDigest,
            )
            writeDurable(pendingPointer, pointerGson.toJson(pointer).toByteArray(Charsets.UTF_8))
            CheckpointStageResult.Staged(
                FileStagedCheckpoint(
                    value = normalized,
                    source = source,
                    versionFile = versionFile,
                    pendingPointer = pendingPointer,
                ),
            )
        } catch (_: OutOfMemoryError) {
            pendingVersion.delete()
            pendingPointer.delete()
            versionFile.delete()
            CheckpointStageResult.Failed(CheckpointStorage.APP_PRIVATE_FALLBACK)
        } catch (_: Exception) {
            pendingVersion.delete()
            pendingPointer.delete()
            versionFile.delete()
            CheckpointStageResult.Failed(CheckpointStorage.APP_PRIVATE_FALLBACK)
        }
    }

    override fun commit(checkpoint: StagedCheckpoint): CheckpointWriteResult {
        val staged = checkpoint as? FileStagedCheckpoint ?: return super.commit(checkpoint)
        return try {
            acceptedPointerPublisher(staged.pendingPointer, fallbackFile(staged.value.key.romSha256))
            staged.accepted = true
            CheckpointWriteResult.Durable(CheckpointStorage.APP_PRIVATE_FALLBACK)
        } catch (_: OutOfMemoryError) {
            CheckpointWriteResult.Failed(CheckpointStorage.APP_PRIVATE_FALLBACK)
        } catch (_: Exception) {
            CheckpointWriteResult.Failed(CheckpointStorage.APP_PRIVATE_FALLBACK)
        }
    }

    override fun discard(checkpoint: StagedCheckpoint) {
        val staged = checkpoint as? FileStagedCheckpoint ?: return super.discard(checkpoint)
        staged.pendingPointer.delete()
        if (!staged.accepted) staged.versionFile.delete()
    }

    override fun complete(checkpoint: StagedCheckpoint, accepted: Boolean) {
        val staged = checkpoint as? FileStagedCheckpoint ?: return super.complete(checkpoint, accepted)
        if (accepted && staged.accepted) {
            staged.source.atomicSiblingTarget?.let { target ->
                try {
                    target.replace(
                        sidecarName(staged.source),
                        encodeCheckpoint(staged.value.copy(portable = true)),
                    )
                } catch (_: OutOfMemoryError) {
                    // The app-private pointer remains the accepted authority when its optional mirror fails.
                } catch (_: Exception) {
                    // The app-private pointer remains the accepted authority when its optional mirror fails.
                }
            }
        }
        discard(staged)
    }

    override fun write(
        source: SaveDocumentSource,
        checkpoint: SaveKnowledgeCheckpoint,
    ): CheckpointWriteResult {
        val staged = when (val stage = stage(source, checkpoint)) {
            is CheckpointStageResult.Staged -> stage.checkpoint
            is CheckpointStageResult.Failed -> return CheckpointWriteResult.Failed(stage.storage)
        }
        val committed = commit(staged)
        if (committed !is CheckpointWriteResult.Durable) {
            discard(staged)
            return committed
        }
        val portableWritten = source.atomicSiblingTarget?.let { target ->
            try {
                target.replace(sidecarName(source), encodeCheckpoint(checkpoint.copy(portable = true)))
                true
            } catch (_: OutOfMemoryError) {
                false
            } catch (_: Exception) {
                false
            }
        } ?: false
        discard(staged)
        return if (portableWritten) {
            CheckpointWriteResult.Durable(CheckpointStorage.PORTABLE_SIDECAR)
        } else {
            committed
        }
    }

    private fun readFallback(file: File): FallbackRead {
        val bytes = when (val read = readBounded(file, SaveKnowledgeCheckpointCodec.MAXIMUM_ENCODED_BYTES)) {
            is BoundedRead.Present -> read.bytes
            BoundedRead.Absent -> return FallbackRead.Absent
            BoundedRead.Unavailable -> return FallbackRead.Unavailable
        }
        val legacy = try {
            decodeCheckpoint(bytes)
        } catch (_: OutOfMemoryError) {
            return FallbackRead.Unavailable
        }
        if (legacy != null) return FallbackRead.Present(legacy)
        val pointer = try {
            pointerGson.fromJson(bytes.toString(Charsets.UTF_8), AcceptedCheckpointPointer::class.java)
        } catch (_: OutOfMemoryError) {
            return FallbackRead.Unavailable
        } catch (_: Exception) {
            null
        } ?: return FallbackRead.Corrupt
        if (
            pointer.schema != ACCEPTED_POINTER_SCHEMA ||
            !pointer.checkpointDigestSha256.matches(SHA256) ||
            !pointer.checkpointVersionId.matches(VERSION_ID)
        ) {
            return FallbackRead.Corrupt
        }
        val romSha256 = file.name.substringBefore(".accepted.json")
        if (!romSha256.matches(SHA256)) return FallbackRead.Corrupt
        val versionFile = versionDirectory(romSha256).resolve("${pointer.checkpointVersionId}.json")
        val versionBytes = when (val read = readBounded(versionFile, SaveKnowledgeCheckpointCodec.MAXIMUM_ENCODED_BYTES)) {
            is BoundedRead.Present -> read.bytes
            BoundedRead.Absent -> return FallbackRead.Corrupt
            BoundedRead.Unavailable -> return FallbackRead.Unavailable
        }
        if (sha256(versionBytes) != pointer.checkpointDigestSha256.lowercase()) return FallbackRead.Corrupt
        val decoded = try {
            decodeCheckpoint(versionBytes)
        } catch (_: OutOfMemoryError) {
            return FallbackRead.Unavailable
        } ?: return FallbackRead.Corrupt
        return if (decoded.key.romSha256.equals(romSha256, ignoreCase = true)) {
            FallbackRead.Present(decoded)
        } else {
            FallbackRead.Corrupt
        }
    }

    private fun readBounded(file: File, maximumBytes: Int): BoundedRead {
        if (!file.isFile) return BoundedRead.Absent
        if (file.length() !in 0..maximumBytes.toLong()) return BoundedRead.Unavailable
        return try {
            val bytes = file.inputStream().use { input -> input.readNBytes(maximumBytes + 1) }
            if (bytes.size > maximumBytes) BoundedRead.Unavailable else BoundedRead.Present(bytes)
        } catch (_: OutOfMemoryError) {
            BoundedRead.Unavailable
        } catch (_: Exception) {
            BoundedRead.Unavailable
        }
    }

    private fun ensureDirectories(versionDirectory: File) {
        check(fallbackRoot.isDirectory || fallbackRoot.mkdirs()) { "checkpoint directory could not be created" }
        check(versionDirectory.isDirectory || versionDirectory.mkdirs()) { "checkpoint version directory could not be created" }
    }

    private fun writeDurable(file: File, bytes: ByteArray) {
        FileOutputStream(file).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
    }

    private fun normalizedKey(key: SaveCheckpointKey) = key.copy(
        romSha256 = key.romSha256.lowercase(),
        saveIdentity = key.saveIdentity.lowercase(),
        saveFileSha256 = key.saveFileSha256.lowercase(),
    )

    private fun sha256(bytes: ByteArray): String? = try {
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte -> "%02x".format(byte) }
    } catch (_: OutOfMemoryError) {
        null
    } catch (_: Exception) {
        null
    }

    private fun sidecarName(source: SaveDocumentSource) = "${source.name}.dualdex.json"

    private fun fallbackFile(romSha256: String) = fallbackRoot.resolve("${romSha256.lowercase()}.accepted.json")

    private fun versionDirectory(romSha256: String) = fallbackRoot.resolve("versions/${romSha256.lowercase()}")

    private class FileStagedCheckpoint(
        override val value: SaveKnowledgeCheckpoint,
        val source: SaveDocumentSource,
        val versionFile: File,
        val pendingPointer: File,
    ) : StagedCheckpoint {
        @Volatile var accepted: Boolean = false
    }

    private data class AcceptedCheckpointPointer(
        val schema: Int = ACCEPTED_POINTER_SCHEMA,
        val checkpointVersionId: String = "",
        val checkpointDigestSha256: String = "",
    )

    private sealed interface FallbackRead {
        data class Present(val checkpoint: SaveKnowledgeCheckpoint) : FallbackRead
        data object Absent : FallbackRead
        data object Corrupt : FallbackRead
        data object Unavailable : FallbackRead
    }

    private sealed interface BoundedRead {
        data class Present(val bytes: ByteArray) : BoundedRead
        data object Absent : BoundedRead
        data object Unavailable : BoundedRead
    }

    private companion object {
        const val ACCEPTED_POINTER_SCHEMA = 1
        val SHA256 = Regex("[0-9a-fA-F]{64}")
        val VERSION_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.[0-9a-f]{64}")
    }
}
