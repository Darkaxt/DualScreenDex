package com.darkaxt.dualdex.save

import com.darkaxt.dualdex.catalog.SaveSnapshotRepository
import com.darkaxt.dualdex.catalog.StoredSaveSnapshot
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveParseResult
import com.darkaxt.dualdex.save.SaveParser
import com.darkaxt.dualdex.save.SaveSnapshot

enum class SaveMonitorStatus { UNAVAILABLE, MATCHED, AMBIGUOUS, STALE }

data class SaveMonitorResult(
    val status: SaveMonitorStatus,
    val autosaveStatus: String,
    val source: SaveDocumentSource? = null,
    val candidates: List<SaveDocumentSource> = emptyList(),
    val snapshot: SaveSnapshot? = null,
    val retained: StoredSaveSnapshot? = null,
    val refreshedAtEpochMs: Long? = null,
    val message: String? = null,
)

class SavePollingMonitor(
    private val associations: SaveAssociationRepository,
    private val snapshots: SaveSnapshotRepository,
    private val parser: (ByteArray, SaveParseContext) -> SaveParseResult = SaveParser::parse,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lastMatchedFingerprint = mutableMapOf<String, DocumentFingerprint>()

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
        if (preferred != null && lastMatchedFingerprint[rom] == preferred.fingerprint() && retained != null) {
            return matched(preferred, retained, autosaveStatus)
        }

        val preferredAttempt = preferred?.let { source ->
            val result = runCatching { parser(source.read().copyOf(), context) }.getOrNull()
            (result as? SaveParseResult.Parsed)?.let { source to it.snapshot }
        }
        val attempts = if (preferredAttempt != null) {
            listOf(preferredAttempt)
        } else {
            candidates.filterNot { it.id == preferred?.id }.mapNotNull { source ->
                val result = runCatching { parser(source.read().copyOf(), context) }.getOrNull()
                (result as? SaveParseResult.Parsed)?.let { source to it.snapshot }
            }
        }
        if (attempts.size > 1) {
            return SaveMonitorResult(
                status = SaveMonitorStatus.AMBIGUOUS,
                autosaveStatus = autosaveStatus,
                candidates = attempts.map { it.first },
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

        val (source, snapshot) = accepted
        val refreshed = clock()
        snapshots.write(snapshot, source.lastModifiedEpochMs, refreshed)
        associations.remember(rom, source.id)
        lastMatchedFingerprint[rom] = source.fingerprint()
        return SaveMonitorResult(
            status = SaveMonitorStatus.MATCHED,
            autosaveStatus = autosaveStatus,
            source = source,
            snapshot = snapshot,
            retained = StoredSaveSnapshot(snapshot, source.lastModifiedEpochMs, refreshed),
            refreshedAtEpochMs = refreshed,
            message = "SaveRAM matched and refreshed.",
        )
    }

    @Synchronized
    fun select(romSha256: String, documentId: String) {
        associations.remember(romSha256, documentId)
        lastMatchedFingerprint.remove(romSha256.lowercase())
    }

    private fun matched(
        source: SaveDocumentSource,
        retained: StoredSaveSnapshot,
        autosaveStatus: String,
    ) = SaveMonitorResult(
        status = SaveMonitorStatus.MATCHED,
        autosaveStatus = autosaveStatus,
        source = source,
        retained = retained,
        refreshedAtEpochMs = retained.refreshedAtEpochMs,
        message = "SaveRAM matched; no save-file change was detected.",
    )

    private fun SaveDocumentSource.fingerprint() = DocumentFingerprint(id, size, lastModifiedEpochMs)

    private data class DocumentFingerprint(val id: String, val size: Long, val modified: Long)
}
