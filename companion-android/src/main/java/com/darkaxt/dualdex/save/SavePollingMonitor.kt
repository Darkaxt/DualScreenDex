package com.darkaxt.dualdex.save

import com.darkaxt.dualdex.catalog.SaveSnapshotRepository
import com.darkaxt.dualdex.catalog.SaveSnapshotStageResult
import com.darkaxt.dualdex.catalog.StagedSaveSnapshot
import com.darkaxt.dualdex.catalog.StoredSaveSnapshot
import com.darkaxt.dualdex.knowledge.safeSaveSnapshotDigest
import com.darkaxt.dualdex.knowledge.SaveCheckpointKey
import com.darkaxt.dualdex.knowledge.SaveFileFingerprint
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveParseResult
import com.darkaxt.dualdex.save.SaveParser
import com.darkaxt.dualdex.save.SaveSnapshot
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

internal const val MAX_SUPPORTED_SAVE_BYTES = 128 * 1024
private const val SAVE_READ_BUFFER_BYTES = 8 * 1024

enum class SaveMonitorStatus { UNAVAILABLE, MATCHED, AMBIGUOUS, STALE }

enum class SaveObservationKind { INITIAL, UNCHANGED, CHANGED, SWITCHED }

data class SaveObservation(
    val kind: SaveObservationKind,
    val source: SaveDocumentSource,
    val fingerprint: SaveFileFingerprint,
) {
    fun key(snapshot: SaveSnapshot) = SaveCheckpointKey(
        romSha256 = snapshot.romIdentity.lowercase(),
        saveIdentity = snapshot.saveIdentity.lowercase(),
        saveFileSha256 = fingerprint.sha256.lowercase(),
        saveSize = fingerprint.size,
        saveLastModifiedEpochMs = fingerprint.lastModifiedEpochMs,
    )
}

data class SaveMonitorResult(
    val status: SaveMonitorStatus,
    val autosaveStatus: String,
    val source: SaveDocumentSource? = null,
    val candidates: List<SaveDocumentSource> = emptyList(),
    val snapshot: SaveSnapshot? = null,
    val retained: StoredSaveSnapshot? = null,
    val refreshedAtEpochMs: Long? = null,
    val message: String? = null,
    val observation: SaveObservation? = null,
    val acceptanceRevision: Long? = null,
)

class PreparedSavePersistence internal constructor(
    internal val stagedSnapshot: StagedSaveSnapshot?,
    internal val romSha256: String?,
    internal val sourceId: String?,
) {
    val snapshotVersionId: String? get() = stagedSnapshot?.versionId

    companion object {
        fun none() = PreparedSavePersistence(null, null, null)
    }
}

class SavePollingMonitor(
    private val associations: SaveAssociationRepository,
    private val snapshots: SaveSnapshotRepository,
    private val parser: (ByteArray, SaveParseContext) -> SaveParseResult = SaveParser::parse,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lastAccepted = mutableMapOf<String, AcceptedSave>()
    private var authorityRevision = 0L

    fun restore(context: SaveParseContext, autosaveStatus: String): SaveMonitorResult? =
        restore(context, autosaveStatus) { true }

    fun restore(
        context: SaveParseContext,
        autosaveStatus: String,
        isCurrent: () -> Boolean,
    ): SaveMonitorResult? {
        if (!isCurrent()) return null
        val stored = snapshots.read(context.romIdentity) ?: return null
        return restored(context, autosaveStatus, stored, isCurrent)
    }

    fun restore(
        context: SaveParseContext,
        autosaveStatus: String,
        snapshotVersionId: String,
        snapshotDigestSha256: String,
        isCurrent: () -> Boolean,
    ): SaveMonitorResult? {
        if (!isCurrent()) return null
        val stored = snapshots.readVersion(
            context.romIdentity,
            snapshotVersionId,
            snapshotDigestSha256,
        ) ?: return null
        return restored(context, autosaveStatus, stored, isCurrent)
    }

    private fun restored(
        context: SaveParseContext,
        autosaveStatus: String,
        stored: StoredSaveSnapshot,
        isCurrent: () -> Boolean,
    ): SaveMonitorResult? {
        if (!isCurrent() || !stored.snapshot.romIdentity.equals(context.romIdentity, ignoreCase = true)) return null
        return SaveMonitorResult(
            status = SaveMonitorStatus.MATCHED,
            autosaveStatus = autosaveStatus,
            snapshot = stored.snapshot,
            retained = stored,
            refreshedAtEpochMs = stored.refreshedAtEpochMs,
            message = "Restored the last checksum-valid SaveRAM snapshot.",
        )
    }

    fun poll(
        context: SaveParseContext,
        candidates: List<SaveDocumentSource>,
        autosaveStatus: String,
    ): SaveMonitorResult = requireNotNull(
        poll(context, candidates, autosaveStatus, isCurrent = { true }),
    )

    fun poll(
        context: SaveParseContext,
        candidates: List<SaveDocumentSource>,
        autosaveStatus: String,
        isCurrent: () -> Boolean,
        commitIfCurrent: ((() -> Unit) -> Boolean) = { commit ->
            if (isCurrent()) {
                commit()
                true
            } else {
                false
            }
        },
        persistAcceptance: Boolean = true,
    ): SaveMonitorResult? {
        if (!isCurrent()) return null
        val rom = context.romIdentity.lowercase()
        val previous = synchronized(this) { lastAccepted[rom] }

        if (!isCurrent()) return null
        val retained = previous?.retained ?: snapshots.read(rom)
        if (!isCurrent()) return null
        if (candidates.isEmpty()) {
            return SaveMonitorResult(
                status = if (retained == null) SaveMonitorStatus.UNAVAILABLE else SaveMonitorStatus.STALE,
                autosaveStatus = autosaveStatus,
                retained = retained,
                message = if (retained == null) "No accessible SaveRAM matched the active content."
                else "The last valid SaveRAM snapshot is retained; its source is not currently accessible.",
            )
        }

        if (!isCurrent()) return null
        val remembered = previous?.sourceId?.takeIf { sourceId -> candidates.any { it.id == sourceId } }
            ?: associations.selectedFor(rom)
        val preferred = candidates.singleOrNull { it.id == remembered }

        val preferredAttempt = preferred?.let { source ->
            attempt(source, context, previous, isCurrent)
        }
        val attempts = if (preferredAttempt != null) {
            listOf(preferredAttempt)
        } else {
            candidates.filterNot { it.id == preferred?.id }.mapNotNull { source ->
                attempt(source, context, previous, isCurrent)
            }
        }
        if (!isCurrent()) return null
        if (attempts.size > 1) {
            return SaveMonitorResult(
                status = SaveMonitorStatus.AMBIGUOUS,
                autosaveStatus = autosaveStatus,
                candidates = attempts.map { it.source },
                retained = retained,
                message = "Multiple checksum-valid SaveRAM files match this ROM. Select one in Settings.",
            )
        }
        val accepted = attempts.singleOrNull()
        if (accepted == null) {
            return SaveMonitorResult(
                status = if (retained == null) SaveMonitorStatus.UNAVAILABLE else SaveMonitorStatus.STALE,
                autosaveStatus = autosaveStatus,
                candidates = candidates,
                retained = retained,
                message = if (retained == null) "No checksum-valid SaveRAM matched the active ROM."
                else "A new SaveRAM read did not validate; the last good snapshot remains active.",
            )
        }

        val source = accepted.source
        val reusedRetained = accepted.reusedRetained
        if (reusedRetained != null) {
            if (!isCurrent()) return null
            return matched(source, reusedRetained, autosaveStatus, accepted.fileFingerprint)
        }
        val snapshot = accepted.snapshot
        val observationKind = when {
            previous == null -> SaveObservationKind.INITIAL
            previous.sourceId != source.id || !previous.saveIdentity.equals(snapshot.saveIdentity, ignoreCase = true) ->
                SaveObservationKind.SWITCHED
            previous.fileFingerprint != accepted.fileFingerprint -> SaveObservationKind.CHANGED
            else -> SaveObservationKind.UNCHANGED
        }
        val refreshed = clock()
        val stored = StoredSaveSnapshot(snapshot, source.lastModifiedEpochMs, refreshed)
        val result = SaveMonitorResult(
            status = SaveMonitorStatus.MATCHED,
            autosaveStatus = autosaveStatus,
            source = source,
            snapshot = snapshot,
            retained = stored,
            refreshedAtEpochMs = refreshed,
            message = "SaveRAM matched and refreshed.",
            observation = SaveObservation(observationKind, source, accepted.fileFingerprint),
            acceptanceRevision = synchronized(this) { authorityRevision },
        )
        if (!persistAcceptance) return result
        val digest = safeSaveSnapshotDigest(snapshot) ?: return null
        val persistence = stagePrepared(result, digest, isCurrent) ?: return null
        var acceptedPrepared = false
        val committed = commitIfCurrent {
            acceptedPrepared = commitPrepared(result, persistence) { true }
        }
        completePrepared(persistence, committed && acceptedPrepared)
        return result.takeIf { committed && acceptedPrepared }
    }

    fun stagePrepared(
        result: SaveMonitorResult,
        snapshotDigestSha256: String,
        isCurrent: () -> Boolean = { true },
    ): PreparedSavePersistence? {
        val observation = result.observation ?: return null
        val snapshot = result.snapshot
        if (snapshot == null) {
            return PreparedSavePersistence(null, null, null)
                .takeIf { observation.kind == SaveObservationKind.UNCHANGED && isCurrent() }
        }
        val stored = result.retained ?: return null
        val expectedRevision = result.acceptanceRevision ?: return null
        if (!isCurrent() || !canAcceptPrepared(expectedRevision)) return null
        val staged = try {
            snapshots.stage(
                snapshot,
                observation.source.lastModifiedEpochMs,
                stored.refreshedAtEpochMs,
                snapshotDigestSha256,
            )
        } catch (_: OutOfMemoryError) {
            SaveSnapshotStageResult.Failed
        } catch (_: Exception) {
            SaveSnapshotStageResult.Failed
        }
        val stagedSnapshot = (staged as? SaveSnapshotStageResult.Staged)?.snapshot ?: return null
        if (!isCurrent() || !canAcceptPrepared(expectedRevision)) {
            runCatching { snapshots.discard(stagedSnapshot) }
            return null
        }
        return PreparedSavePersistence(stagedSnapshot, snapshot.romIdentity, observation.source.id)
    }

    @Synchronized
    fun commitPrepared(
        result: SaveMonitorResult,
        persistence: PreparedSavePersistence,
        publishAuthority: () -> Boolean,
    ): Boolean {
        val observation = result.observation ?: return false
        if (result.snapshot == null && observation.kind == SaveObservationKind.UNCHANGED) {
            return publishAuthority()
        }
        val snapshot = result.snapshot ?: return false
        val stored = result.retained ?: return false
        val expectedRevision = result.acceptanceRevision ?: return false
        if (!canAcceptPrepared(expectedRevision)) return false
        val stagedSnapshot = persistence.stagedSnapshot ?: return false
        val snapshotReady = try {
            snapshots.prepareForAcceptance(stagedSnapshot)
        } catch (_: OutOfMemoryError) {
            false
        } catch (_: Exception) {
            false
        }
        if (!snapshotReady || !publishAuthority()) return false
        snapshots.accept(stagedSnapshot)
        acceptPreparedLocked(snapshot, stored, observation)
        return true
    }

    fun completePrepared(persistence: PreparedSavePersistence, accepted: Boolean) {
        val stagedSnapshot = persistence.stagedSnapshot
        if (accepted) {
            val romSha256 = persistence.romSha256
            val sourceId = persistence.sourceId
            if (romSha256 != null && sourceId != null) {
                try {
                    associations.remember(romSha256, sourceId)
                } catch (_: OutOfMemoryError) {
                    // Accepted in-memory authority remains valid when preference persistence fails.
                } catch (_: Exception) {
                    // Accepted in-memory authority remains valid when preference persistence fails.
                }
            }
        }
        if (stagedSnapshot != null) {
            try {
                snapshots.discard(stagedSnapshot)
            } catch (_: OutOfMemoryError) {
                // Best-effort staging cleanup must not escape the SaveRAM boundary.
            } catch (_: Exception) {
                // Best-effort staging cleanup must not escape the SaveRAM boundary.
            }
        }
    }

    fun persistPrepared(
        result: SaveMonitorResult,
        isCurrent: () -> Boolean = { true },
    ): Boolean {
        val observation = result.observation ?: return false
        val snapshot = result.snapshot ?: return true
        val stored = result.retained ?: return false
        val expectedRevision = result.acceptanceRevision ?: return false
        if (!isCurrent() || !canAcceptPrepared(expectedRevision)) return false
        return try {
            snapshots.write(snapshot, observation.source.lastModifiedEpochMs, stored.refreshedAtEpochMs)
            if (!isCurrent() || !canAcceptPrepared(expectedRevision)) return false
            associations.remember(snapshot.romIdentity, observation.source.id)
            isCurrent() && canAcceptPrepared(expectedRevision)
        } catch (_: OutOfMemoryError) {
            false
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    fun canAcceptPrepared(result: SaveMonitorResult): Boolean =
        result.acceptanceRevision?.let(::canAcceptPrepared) ?: result.observation?.kind == SaveObservationKind.UNCHANGED

    @Synchronized
    fun acceptPrepared(result: SaveMonitorResult): Boolean {
        val observation = result.observation ?: return false
        if (result.snapshot == null && observation.kind == SaveObservationKind.UNCHANGED) return true
        val snapshot = result.snapshot ?: return false
        val stored = result.retained ?: return false
        val expectedRevision = result.acceptanceRevision ?: return false
        if (!canAcceptPrepared(expectedRevision)) return false
        acceptPreparedLocked(snapshot, stored, observation)
        return true
    }

    private fun acceptPreparedLocked(
        snapshot: SaveSnapshot,
        stored: StoredSaveSnapshot,
        observation: SaveObservation,
    ) {
        lastAccepted[snapshot.romIdentity.lowercase()] = AcceptedSave(
            sourceId = observation.source.id,
            saveIdentity = snapshot.saveIdentity,
            documentFingerprint = observation.source.documentFingerprint(),
            fileFingerprint = observation.fingerprint,
            retained = stored,
        )
        authorityRevision++
    }

    @Synchronized
    fun restoreAccepted(result: SaveMonitorResult): Boolean {
        val observation = result.observation ?: return false
        val snapshot = result.snapshot ?: result.retained?.snapshot ?: return false
        val retained = result.retained ?: return false
        lastAccepted[snapshot.romIdentity.lowercase()] = AcceptedSave(
            sourceId = observation.source.id,
            saveIdentity = snapshot.saveIdentity,
            documentFingerprint = observation.source.documentFingerprint(),
            fileFingerprint = observation.fingerprint,
            retained = retained,
        )
        authorityRevision++
        return true
    }

    @Synchronized
    private fun canAcceptPrepared(expectedRevision: Long): Boolean = authorityRevision == expectedRevision

    fun select(romSha256: String, documentId: String) {
        select(romSha256, documentId) { commit ->
            commit()
            true
        }
    }

    fun select(
        romSha256: String,
        documentId: String,
        commitIfCurrent: ((() -> Unit) -> Boolean),
    ): Boolean {
        val committed = commitIfCurrent {
            synchronized(this) {
                lastAccepted.remove(romSha256.lowercase())
            }
        }
        if (committed) {
            try {
                associations.remember(romSha256, documentId)
            } catch (_: OutOfMemoryError) {
                // Selection remains valid for this process even when its preference cannot be persisted.
            } catch (_: Exception) {
                // Selection remains valid for this process even when its preference cannot be persisted.
            }
        }
        return committed
    }

    private fun matched(
        source: SaveDocumentSource,
        retained: StoredSaveSnapshot,
        autosaveStatus: String,
        fileFingerprint: SaveFileFingerprint,
    ) = SaveMonitorResult(
        status = SaveMonitorStatus.MATCHED,
        autosaveStatus = autosaveStatus,
        source = source,
        retained = retained,
        refreshedAtEpochMs = retained.refreshedAtEpochMs,
        message = "SaveRAM matched; no save-file change was detected.",
        observation = SaveObservation(SaveObservationKind.UNCHANGED, source, fileFingerprint),
    )

    private fun attempt(
        source: SaveDocumentSource,
        context: SaveParseContext,
        previous: AcceptedSave?,
        isCurrent: () -> Boolean,
    ): AcceptedAttempt? {
        if (!isCurrent()) return null
        val bytes = try {
            readBounded(source)
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        } ?: return null
        if (!isCurrent()) return null
        val fileFingerprint = SaveFileFingerprint(
            sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).toHex(),
            size = bytes.size.toLong(),
            lastModifiedEpochMs = source.lastModifiedEpochMs,
        )
        if (
            previous != null &&
            source.id == previous.sourceId &&
            source.documentFingerprint() == previous.documentFingerprint &&
            fileFingerprint.sha256 == previous.fileFingerprint.sha256 &&
            fileFingerprint.size == previous.fileFingerprint.size
        ) {
            return AcceptedAttempt(
                source = source,
                snapshot = previous.retained.snapshot,
                fileFingerprint = fileFingerprint,
                reusedRetained = previous.retained,
            )
        }
        val parsed = runCatching { parser(bytes, context) }.getOrNull() as? SaveParseResult.Parsed ?: return null
        if (!isCurrent()) return null
        return AcceptedAttempt(
            source = source,
            snapshot = parsed.snapshot,
            fileFingerprint = fileFingerprint,
        )
    }

    private fun readBounded(source: SaveDocumentSource): ByteArray? {
        if (source.size > MAX_SUPPORTED_SAVE_BYTES) return null
        return source.open().use { input ->
            val output = ByteArrayOutputStream(
                source.size.takeIf { it in 1..MAX_SUPPORTED_SAVE_BYTES.toLong() }?.toInt() ?: SAVE_READ_BUFFER_BYTES,
            )
            val buffer = ByteArray(SAVE_READ_BUFFER_BYTES)
            var total = 0
            while (total <= MAX_SUPPORTED_SAVE_BYTES) {
                val remaining = MAX_SUPPORTED_SAVE_BYTES + 1 - total
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read < 0) return@use output.toByteArray()
                if (read == 0) {
                    val single = input.read()
                    if (single < 0) return@use output.toByteArray()
                    output.write(single)
                    total++
                    continue
                }
                output.write(buffer, 0, read)
                total += read
            }
            null
        }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun SaveDocumentSource.documentFingerprint() = DocumentFingerprint(id, size, lastModifiedEpochMs)

    private data class DocumentFingerprint(val id: String, val size: Long, val modified: Long)

    private data class AcceptedAttempt(
        val source: SaveDocumentSource,
        val snapshot: SaveSnapshot,
        val fileFingerprint: SaveFileFingerprint,
        val reusedRetained: StoredSaveSnapshot? = null,
    )

    private data class AcceptedSave(
        val sourceId: String,
        val saveIdentity: String,
        val documentFingerprint: DocumentFingerprint,
        val fileFingerprint: SaveFileFingerprint,
        val retained: StoredSaveSnapshot,
    )
}
