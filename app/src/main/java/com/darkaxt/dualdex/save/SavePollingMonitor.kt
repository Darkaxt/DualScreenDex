package com.darkaxt.dualdex.save

import com.darkaxt.dualdex.catalog.SaveSnapshotRepository
import com.darkaxt.dualdex.catalog.StoredSaveSnapshot
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
)

class SavePollingMonitor(
    private val associations: SaveAssociationRepository,
    private val snapshots: SaveSnapshotRepository,
    private val parser: (ByteArray, SaveParseContext) -> SaveParseResult = SaveParser::parse,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lastAccepted = mutableMapOf<String, AcceptedSave>()

    fun restore(context: SaveParseContext, autosaveStatus: String): SaveMonitorResult? =
        restore(context, autosaveStatus) { true }

    @Synchronized
    fun restore(
        context: SaveParseContext,
        autosaveStatus: String,
        isCurrent: () -> Boolean,
    ): SaveMonitorResult? {
        if (!isCurrent()) return null
        val stored = snapshots.read(context.romIdentity) ?: return null
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
    ): SaveMonitorResult = requireNotNull(poll(context, candidates, autosaveStatus) { true })

    @Synchronized
    fun poll(
        context: SaveParseContext,
        candidates: List<SaveDocumentSource>,
        autosaveStatus: String,
        isCurrent: () -> Boolean,
    ): SaveMonitorResult? {
        if (!isCurrent()) return null
        val rom = context.romIdentity.lowercase()
        val previous = lastAccepted[rom]

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
        if (!isCurrent()) return null
        snapshots.write(snapshot, source.lastModifiedEpochMs, refreshed)
        if (!isCurrent()) return null
        associations.remember(rom, source.id)
        if (!isCurrent()) return null
        lastAccepted[rom] = AcceptedSave(
            sourceId = source.id,
            saveIdentity = snapshot.saveIdentity,
            documentFingerprint = source.documentFingerprint(),
            fileFingerprint = accepted.fileFingerprint,
            retained = stored,
        )
        return SaveMonitorResult(
            status = SaveMonitorStatus.MATCHED,
            autosaveStatus = autosaveStatus,
            source = source,
            snapshot = snapshot,
            retained = stored,
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
