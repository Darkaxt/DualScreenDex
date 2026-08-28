package com.darkaxt.dualdex.knowledge

import com.darkaxt.dualdex.live.RecoveryApplication
import com.darkaxt.dualdex.live.RecoveryProjection
import com.darkaxt.dualdex.progress.JournalRestoreBaseline
import com.darkaxt.dualdex.progress.PlaythroughJournal
import com.darkaxt.dualdex.progress.PlaythroughJournalSession
import com.darkaxt.dualdex.save.PreparedSavePersistence
import com.darkaxt.dualdex.save.SaveDocumentSource
import com.darkaxt.dualdex.save.SaveMonitorResult
import com.darkaxt.dualdex.save.SaveObservationKind
import com.darkaxt.dualdex.save.SaveSnapshot
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.semantic.PlaythroughKey
import com.google.gson.Gson
import java.security.MessageDigest

class RecoveryPreparation(
    val application: RecoveryApplication,
    private val commitPrepared: (publishAuthority: () -> Boolean) -> RecoveryApplication,
) {
    fun commit(publishAuthority: () -> Boolean = { true }): RecoveryApplication =
        commitPrepared(publishAuthority)
}

class SaveKnowledgeCheckpointCoordinator(
    private val checkpoints: KnowledgeCheckpointStore,
    private val prepareRecovery: (RecoveryProjection) -> RecoveryPreparation?,
    private val journal: PlaythroughJournalSession? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val publishRecoveryStatus: (SaveRamView) -> Unit = {},
    private val snapshotDigest: (SaveSnapshot) -> String? = ::safeSaveSnapshotDigest,
) {
    private var pendingReadKey: SaveCheckpointKey? = null
    private var pendingWrite: SaveKnowledgeCheckpoint? = null

    fun readLatest(romSha256: String): CheckpointReadResult = safeReadLatest(romSha256)

    fun applyPersisted(
        result: SaveMonitorResult,
        saveView: SaveRamView,
        commitIfCurrent: ((() -> Unit) -> Boolean) = { commit -> commit(); true },
        acceptPrepared: (SaveMonitorResult) -> Boolean = { true },
        preloadedCheckpoint: SaveKnowledgeCheckpoint? = null,
    ): Boolean {
        val snapshot = result.snapshot ?: result.retained?.snapshot ?: return false
        val latest = preloadedCheckpoint?.let(CheckpointReadResult::Present) ?: safeReadLatest(snapshot.romIdentity)
        val checkpoint = when (latest) {
            is CheckpointReadResult.Present -> latest.checkpoint
            CheckpointReadResult.Absent -> return false
            is CheckpointReadResult.Corrupt -> {
                commitIfCurrent { publishCheckpointUnavailable(saveView, corrupt = true) }
                return false
            }
            is CheckpointReadResult.Unavailable -> {
                commitIfCurrent { publishCheckpointUnavailable(saveView, corrupt = false) }
                return false
            }
        }
        val sourceId = checkpoint.sourceId
        val restoredSnapshotDigest = snapshotDigest(snapshot)
        if (
            sourceId.isNullOrBlank() ||
            restoredSnapshotDigest == null ||
            checkpoint.snapshotDigestSha256 != restoredSnapshotDigest ||
            checkpoint.key.romSha256 != snapshot.romIdentity.lowercase() ||
            checkpoint.key.saveIdentity != snapshot.saveIdentity.lowercase()
        ) {
            commitIfCurrent { publishCheckpointUnavailable(saveView, corrupt = true) }
            return false
        }
        val source = SaveDocumentSource(
            id = sourceId,
            displayPath = "Persisted SaveRAM",
            name = "persisted.srm",
            size = checkpoint.key.saveSize,
            lastModifiedEpochMs = checkpoint.key.saveLastModifiedEpochMs,
            open = { error("persisted checkpoint sources cannot be reopened directly") },
        )
        val restored = result.copy(
            source = source,
            observation = com.darkaxt.dualdex.save.SaveObservation(
                SaveObservationKind.INITIAL,
                source,
                SaveFileFingerprint(
                    checkpoint.key.saveFileSha256,
                    checkpoint.key.saveSize,
                    checkpoint.key.saveLastModifiedEpochMs,
                ),
            ),
        )
        return apply(
            result = restored,
            saveView = saveView,
            commitIfCurrent = commitIfCurrent,
            stagePrepared = { PreparedSavePersistence.none() },
            commitPrepared = { _, publishAuthority -> publishAuthority() && acceptPrepared(restored) },
            completePrepared = { _, _ -> },
            preloadedCheckpoint = checkpoint,
            persistAuthorityCheckpoint = false,
        )
    }

    fun apply(
        result: SaveMonitorResult,
        saveView: SaveRamView,
        commitIfCurrent: ((() -> Unit) -> Boolean) = { commit -> commit(); true },
        stagePrepared: (snapshotDigestSha256: String) -> PreparedSavePersistence? = { PreparedSavePersistence.none() },
        commitPrepared: (PreparedSavePersistence, publishAuthority: () -> Boolean) -> Boolean =
            { _, publishAuthority -> publishAuthority() },
        completePrepared: (PreparedSavePersistence, accepted: Boolean) -> Unit = { _, _ -> },
        preloadedCheckpoint: SaveKnowledgeCheckpoint? = null,
        persistAuthorityCheckpoint: Boolean = true,
    ): Boolean {
        val snapshot = result.snapshot ?: result.retained?.snapshot ?: return false
        val observation = result.observation ?: return false
        val key = observation.key(snapshot)
        val retryingRead = synchronized(this) { pendingReadKey == key }
        val shouldRead = retryingRead ||
            observation.kind == SaveObservationKind.INITIAL ||
            observation.kind == SaveObservationKind.SWITCHED
        val checkpoint = preloadedCheckpoint ?: if (shouldRead) {
            when (val read = safeRead(observation.source, key)) {
                is CheckpointReadResult.Present -> read.checkpoint
                CheckpointReadResult.Absent -> null
                is CheckpointReadResult.Corrupt -> {
                    retainPendingRead(key, saveView, corrupt = true, commitIfCurrent)
                    return false
                }
                is CheckpointReadResult.Unavailable -> {
                    retainPendingRead(key, saveView, corrupt = false, commitIfCurrent)
                    return false
                }
            }
        } else {
            null
        }
        val playthrough = PlaythroughKey(key.romSha256, key.saveIdentity)
        val journalBaseline = try {
            journal?.captureForRestore(playthrough)
        } catch (_: OutOfMemoryError) {
            commitIfCurrent { publishCheckpointUnavailable(saveView, corrupt = false) }
            return false
        } catch (_: Exception) {
            commitIfCurrent { publishCheckpointUnavailable(saveView, corrupt = false) }
            return false
        }
        val projection = RecoveryProjection(
            snapshot = snapshot,
            saveRam = saveView,
            observation = observation,
            checkpointLedger = checkpoint?.ledger,
        )
        val preparation = try {
            prepareRecovery(projection)
        } catch (_: OutOfMemoryError) {
            commitIfCurrent { publishCheckpointUnavailable(saveView, corrupt = false) }
            return false
        } catch (_: Exception) {
            commitIfCurrent { publishCheckpointUnavailable(saveView, corrupt = false) }
            return false
        } ?: return false
        val ledgerToPersist = when (observation.kind) {
            SaveObservationKind.INITIAL, SaveObservationKind.SWITCHED ->
                if (persistAuthorityCheckpoint) checkpoint?.ledger ?: KnowledgeLedger() else null
            SaveObservationKind.CHANGED -> preparation.application.checkpointLedger
            SaveObservationKind.UNCHANGED -> null
        }
        val capturesCheckpoint = ledgerToPersist != null
        val pendingCheckpoint = if (capturesCheckpoint) null else synchronized(this) {
            pendingWrite?.takeIf { it.key == key }
        }
        val requiresAuthorityWrite = capturesCheckpoint || pendingCheckpoint != null
        val preparedSnapshotDigest = if (requiresAuthorityWrite) snapshotDigest(snapshot) else null
        if (requiresAuthorityWrite && preparedSnapshotDigest == null) {
            commitIfCurrent { publishCheckpointUnavailable(saveView, corrupt = false) }
            return false
        }
        val journalToPersist = if (capturesCheckpoint) {
            try {
                checkpoint?.journal ?: journal?.current(playthrough)
            } catch (_: OutOfMemoryError) {
                commitIfCurrent { publishCheckpointUnavailable(saveView, corrupt = false) }
                return false
            } catch (_: Exception) {
                commitIfCurrent { publishCheckpointUnavailable(saveView, corrupt = false) }
                return false
            }
        } else {
            null
        }
        val checkpointBeforeSnapshotVersion = when {
            capturesCheckpoint -> SaveKnowledgeCheckpoint(
                portable = observation.source.atomicSiblingTarget != null,
                key = key,
                capturedAtEpochMs = clock(),
                ledger = requireNotNull(ledgerToPersist),
                journal = journalToPersist,
                sourceId = observation.source.id,
                snapshotDigestSha256 = preparedSnapshotDigest,
            )
            pendingCheckpoint != null -> pendingCheckpoint.copy(
                sourceId = observation.source.id,
                snapshotDigestSha256 = preparedSnapshotDigest,
                snapshotVersionId = null,
            )
            else -> null
        }
        val savePersistence = if (requiresAuthorityWrite) {
            val digest = requireNotNull(preparedSnapshotDigest)
            stagePrepared(digest) ?: run {
                commitIfCurrent {
                    synchronized(this) { pendingWrite = checkpointBeforeSnapshotVersion }
                    publishCheckpointUnavailable(saveView, corrupt = false)
                }
                return false
            }
        } else {
            PreparedSavePersistence.none()
        }
        val checkpointToWrite = checkpointBeforeSnapshotVersion?.copy(
            snapshotVersionId = savePersistence.snapshotVersionId,
        )
        val stagedCheckpoint = if (checkpointToWrite != null) {
            when (val stage = safeStage(observation.source, checkpointToWrite)) {
                is CheckpointStageResult.Staged -> stage.checkpoint
                is CheckpointStageResult.Failed -> {
                    safeCompletePrepared(completePrepared, savePersistence, accepted = false)
                    commitIfCurrent {
                        synchronized(this) { pendingWrite = checkpointBeforeSnapshotVersion }
                        publishCheckpointUnavailable(saveView, corrupt = false)
                    }
                    return false
                }
            }
        } else {
            null
        }
        var accepted = false
        var authorityFailed = false
        val committed = commitIfCurrent {
            val application = preparation.commit {
                commitPrepared(savePersistence) {
                    if (stagedCheckpoint == null) {
                        true
                    } else {
                        (safeCommit(stagedCheckpoint) is CheckpointWriteResult.Durable).also { durable ->
                            if (!durable) authorityFailed = true
                        }
                    }
                }.also { authorityCommitted ->
                    if (requiresAuthorityWrite && !authorityCommitted) authorityFailed = true
                }
            }
            if (authorityFailed) {
                synchronized(this) { pendingWrite = checkpointBeforeSnapshotVersion }
                publishCheckpointUnavailable(saveView, corrupt = false)
                return@commitIfCurrent
            }
            if (application.accepted) {
                checkpoint?.journal?.let { restored -> safeRestoreJournal(restored, journalBaseline) }
                synchronized(this) {
                    if (pendingReadKey == key) pendingReadKey = null
                    if (pendingWrite?.key == key) pendingWrite = null
                }
                accepted = true
            }
        }
        stagedCheckpoint?.let { staged -> safeCompleteCheckpoint(staged, committed && accepted) }
        safeCompletePrepared(completePrepared, savePersistence, committed && accepted)
        return committed && accepted
    }

    private fun retainPendingRead(
        key: SaveCheckpointKey,
        saveView: SaveRamView,
        corrupt: Boolean,
        commitIfCurrent: ((() -> Unit) -> Boolean),
    ) {
        commitIfCurrent {
            synchronized(this) { pendingReadKey = key }
            publishCheckpointUnavailable(saveView, corrupt)
        }
    }

    private fun safeReadLatest(romSha256: String): CheckpointReadResult = try {
        checkpoints.readLatest(romSha256)
    } catch (_: OutOfMemoryError) {
        CheckpointReadResult.Unavailable(CheckpointStorage.APP_PRIVATE_FALLBACK)
    } catch (_: Exception) {
        CheckpointReadResult.Unavailable(CheckpointStorage.APP_PRIVATE_FALLBACK)
    }

    private fun safeRead(source: SaveDocumentSource, key: SaveCheckpointKey): CheckpointReadResult = try {
        checkpoints.read(source, key)
    } catch (_: OutOfMemoryError) {
        unavailableRead(source)
    } catch (_: Exception) {
        unavailableRead(source)
    }

    private fun unavailableRead(source: SaveDocumentSource) = CheckpointReadResult.Unavailable(
        source.atomicSiblingTarget?.let { CheckpointStorage.PORTABLE_SIDECAR }
            ?: CheckpointStorage.APP_PRIVATE_FALLBACK,
    )

    private fun safeStage(
        source: SaveDocumentSource,
        checkpoint: SaveKnowledgeCheckpoint,
    ): CheckpointStageResult = try {
        checkpoints.stage(source, checkpoint)
    } catch (_: OutOfMemoryError) {
        CheckpointStageResult.Failed(null)
    } catch (_: Exception) {
        CheckpointStageResult.Failed(null)
    }

    private fun safeCommit(checkpoint: StagedCheckpoint): CheckpointWriteResult = try {
        checkpoints.commit(checkpoint)
    } catch (_: OutOfMemoryError) {
        CheckpointWriteResult.Failed(null)
    } catch (_: Exception) {
        CheckpointWriteResult.Failed(null)
    }

    private fun safeRestoreJournal(
        restored: PlaythroughJournal,
        baseline: JournalRestoreBaseline?,
    ): Boolean = try {
        journal?.restore(restored, baseline) == true
    } catch (_: OutOfMemoryError) {
        false
    } catch (_: Exception) {
        false
    }

    private fun safeCompleteCheckpoint(checkpoint: StagedCheckpoint, accepted: Boolean) {
        try {
            checkpoints.complete(checkpoint, accepted)
        } catch (_: OutOfMemoryError) {
            // Best-effort cleanup and portable mirroring must not escape the checkpoint boundary.
        } catch (_: Exception) {
            // Best-effort cleanup and portable mirroring must not escape the checkpoint boundary.
        }
    }

    private fun safeCompletePrepared(
        completePrepared: (PreparedSavePersistence, Boolean) -> Unit,
        persistence: PreparedSavePersistence,
        accepted: Boolean,
    ) {
        try {
            completePrepared(persistence, accepted)
        } catch (_: OutOfMemoryError) {
            // Best-effort staging cleanup must not escape the checkpoint boundary.
        } catch (_: Exception) {
            // Best-effort staging cleanup must not escape the checkpoint boundary.
        }
    }

    private fun publishCheckpointUnavailable(saveView: SaveRamView, corrupt: Boolean) {
        publishRecoveryStatus(
            saveView.copy(
                status = "STALE",
                message = if (corrupt) {
                    "Saved game knowledge could not be verified; retained knowledge remains active and storage will retry."
                } else {
                    "Saved game knowledge is temporarily unavailable; retained knowledge remains active and storage will retry."
                },
            ),
        )
    }
}

private val checkpointSnapshotGson = Gson()

internal fun safeSaveSnapshotDigest(snapshot: SaveSnapshot): String? = try {
    MessageDigest.getInstance("SHA-256")
        .digest(checkpointSnapshotGson.toJson(snapshot).toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
} catch (_: OutOfMemoryError) {
    null
} catch (_: Exception) {
    null
}
