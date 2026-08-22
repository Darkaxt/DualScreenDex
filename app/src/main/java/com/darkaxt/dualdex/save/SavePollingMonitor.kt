package com.darkaxt.dualdex.save

import com.darkaxt.dualdex.catalog.SaveSnapshotRepository
import com.darkaxt.dualdex.catalog.StoredSaveSnapshot
import com.darkaxt.dualdex.knowledge.SaveCheckpointKey
import com.darkaxt.dualdex.knowledge.SaveFileFingerprint
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveParseResult
import com.darkaxt.dualdex.save.SaveParser
import com.darkaxt.dualdex.save.SaveSnapshot
import java.security.MessageDigest

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
)

class SavePollingMonitor(
    private val associations: SaveAssociationRepository,
    private val snapshots: SaveSnapshotRepository,
    private val parser: (ByteArray, SaveParseContext) -> SaveParseResult = SaveParser::parse,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lastAccepted = mutableMapOf<String, AcceptedSave>()

    @Synchronized
    fun restore(context: SaveParseContext, autosaveStatus: String): SaveMonitorResult? {
        val stored = snapshots.read(context.romIdentity) ?: return null
        if (!stored.snapshot.romIdentity.equals(context.romIdentity, ignoreCase = true)) return null
        return SaveMonitorResult(
            status = SaveMonitorStatus.MATCHED,
            autosaveStatus = autosaveStatus,
            snapshot = stored.snapshot,
            retained = stored,
            refreshedAtEpochMs = stored.refreshedAtEpochMs,
            message = "Restored the last checksum-valid SaveRAM snapshot.",
        )
    }

    @Synchronized
    fun poll(
        context: SaveParseContext,
        candidates: List<SaveDocumentSource>,
        autosaveStatus: String,
    ): SaveMonitorResult {
        val rom = context.romIdentity.lowercase()
        val retained = snapshots.read(rom)
        if (candidates.isEmpty()) {
            return SaveMonitorResult(
                status = if (retained == null) SaveMonitorStatus.UNAVAILABLE else SaveMonitorStatus.STALE,
                autosaveStatus = autosaveStatus,
                retained = retained,
                message = if (retained == null) "No accessible SaveRAM matched the active content."
                else "The last valid SaveRAM snapshot is retained; its source is not currently accessible.",
            )
        }

        val remembered = associations.selectedFor(rom)
        val preferred = candidates.singleOrNull { it.id == remembered }
        val previous = lastAccepted[rom]
        if (preferred != null && previous?.documentFingerprint == preferred.documentFingerprint() && retained != null) {
            return matched(preferred, retained, autosaveStatus, previous.fileFingerprint)
        }

        val preferredAttempt = preferred?.let { source ->
            attempt(source, context)
        }
        val attempts = if (preferredAttempt != null) {
            listOf(preferredAttempt)
        } else {
            candidates.filterNot { it.id == preferred?.id }.mapNotNull { source -> attempt(source, context) }
        }
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
        val snapshot = accepted.snapshot
        val observationKind = when {
            previous == null -> SaveObservationKind.INITIAL
            previous.sourceId != source.id || !previous.saveIdentity.equals(snapshot.saveIdentity, ignoreCase = true) ->
                SaveObservationKind.SWITCHED
            previous.fileFingerprint != accepted.fileFingerprint -> SaveObservationKind.CHANGED
            else -> SaveObservationKind.UNCHANGED
        }
        val refreshed = clock()
        snapshots.write(snapshot, source.lastModifiedEpochMs, refreshed)
        associations.remember(rom, source.id)
        lastAccepted[rom] = AcceptedSave(
            sourceId = source.id,
            saveIdentity = snapshot.saveIdentity,
            documentFingerprint = source.documentFingerprint(),
            fileFingerprint = accepted.fileFingerprint,
        )
        return SaveMonitorResult(
            status = SaveMonitorStatus.MATCHED,
            autosaveStatus = autosaveStatus,
            source = source,
            snapshot = snapshot,
            retained = StoredSaveSnapshot(snapshot, source.lastModifiedEpochMs, refreshed),
            refreshedAtEpochMs = refreshed,
            message = "SaveRAM matched and refreshed.",
            observation = SaveObservation(observationKind, source, accepted.fileFingerprint),
        )
    }

    @Synchronized
    fun select(romSha256: String, documentId: String) {
        associations.remember(romSha256, documentId)
        lastAccepted.remove(romSha256.lowercase())
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

    private fun attempt(source: SaveDocumentSource, context: SaveParseContext): AcceptedAttempt? {
        val bytes = runCatching(source.read).getOrNull() ?: return null
        val parsed = runCatching { parser(bytes, context) }.getOrNull() as? SaveParseResult.Parsed ?: return null
        return AcceptedAttempt(
            source = source,
            snapshot = parsed.snapshot,
            fileFingerprint = SaveFileFingerprint(
                sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).toHex(),
                size = bytes.size.toLong(),
                lastModifiedEpochMs = source.lastModifiedEpochMs,
            ),
        )
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun SaveDocumentSource.documentFingerprint() = DocumentFingerprint(id, size, lastModifiedEpochMs)

    private data class DocumentFingerprint(val id: String, val size: Long, val modified: Long)

    private data class AcceptedAttempt(
        val source: SaveDocumentSource,
        val snapshot: SaveSnapshot,
        val fileFingerprint: SaveFileFingerprint,
    )

    private data class AcceptedSave(
        val sourceId: String,
        val saveIdentity: String,
        val documentFingerprint: DocumentFingerprint,
        val fileFingerprint: SaveFileFingerprint,
    )
}
