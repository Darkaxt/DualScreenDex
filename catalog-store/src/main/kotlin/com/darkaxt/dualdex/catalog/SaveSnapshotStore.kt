package com.darkaxt.dualdex.catalog

import com.darkaxt.dualdex.save.SaveSnapshot
import com.google.gson.Gson
import java.io.File

data class StoredSaveSnapshot(
    val snapshot: SaveSnapshot,
    val sourceLastModifiedEpochMs: Long,
    val refreshedAtEpochMs: Long,
)

data class SaveSnapshotCorruption(
    val reason: String,
)

interface SaveSnapshotRepository {
    fun write(snapshot: SaveSnapshot, sourceLastModifiedEpochMs: Long, refreshedAtEpochMs: Long)
    fun read(romSha256: String): StoredSaveSnapshot?
}

class SaveSnapshotStore(
    private val catalogDirectory: File,
    private val databaseFactory: CatalogDatabaseFactory,
    private val gson: Gson = Gson(),
    private val onCorruptSnapshot: (SaveSnapshotCorruption) -> Unit = {},
) : SaveSnapshotRepository {
    private val snapshotDirectory = File(catalogDirectory, SNAPSHOT_DIRECTORY)

    init {
        require(catalogDirectory.exists() || catalogDirectory.mkdirs()) {
            "catalog cache directory could not be created: $catalogDirectory"
        }
        require(catalogDirectory.isDirectory) { "catalog cache path is not a directory: $catalogDirectory" }
        require(snapshotDirectory.exists() || snapshotDirectory.mkdirs()) {
            "recovery snapshot directory could not be created: $snapshotDirectory"
        }
        require(snapshotDirectory.isDirectory) { "recovery snapshot path is not a directory: $snapshotDirectory" }
        migrateLegacySnapshots()
    }

    @Synchronized
    override fun write(snapshot: SaveSnapshot, sourceLastModifiedEpochMs: Long, refreshedAtEpochMs: Long) {
        writeRecord(
            SnapshotRecord(
                romSha256 = snapshot.romIdentity.lowercase(),
                saveIdentity = snapshot.saveIdentity,
                saveSchemaId = snapshot.schemaId,
                payloadJson = gson.toJson(snapshot),
                sourceLastModifiedEpochMs = sourceLastModifiedEpochMs,
                refreshedAtEpochMs = refreshedAtEpochMs,
            ),
        )
    }

    @Synchronized
    override fun read(romSha256: String): StoredSaveSnapshot? {
        requireHash(romSha256)
        val normalizedSha = romSha256.lowercase()
        var file = fileFor(normalizedSha)
        if (!file.isFile) {
            migrateLegacySnapshot(legacyFileFor(normalizedSha), normalizedSha)
            file = fileFor(normalizedSha)
        }
        if (!file.isFile) return null
        return databaseFactory.open(file).use { database ->
            SaveSnapshotMigration.prepare(database)
            val record = database.query(
                """
                SELECT rom_sha256, save_identity, save_schema_id, payload_json,
                       source_last_modified_epoch_ms, refreshed_at_epoch_ms
                FROM save_snapshot WHERE id = 1
                """.trimIndent(),
            ) { row ->
                SnapshotRecord(
                    romSha256 = requireNotNull(row.string("rom_sha256")),
                    saveIdentity = requireNotNull(row.string("save_identity")),
                    saveSchemaId = requireNotNull(row.string("save_schema_id")),
                    payloadJson = requireNotNull(row.string("payload_json")),
                    sourceLastModifiedEpochMs = requireNotNull(row.long("source_last_modified_epoch_ms")),
                    refreshedAtEpochMs = requireNotNull(row.long("refreshed_at_epoch_ms")),
                )
            }.singleOrNull() ?: return@use null
            try {
                decode(normalizedSha, record)
            } catch (failure: CorruptSnapshotPayloadException) {
                database.transaction {
                    database.execute("DELETE FROM save_snapshot WHERE id = 1")
                }
                reportCorruption(failure)
                null
            }
        }
    }

    private fun decode(
        requestedSha: String,
        record: SnapshotRecord,
    ): StoredSaveSnapshot = try {
        require(record.romSha256.equals(requestedSha, ignoreCase = true)) {
            "SaveRAM snapshot belongs to another ROM"
        }
        val snapshot = requireNotNull(
            gson.fromJson(record.payloadJson, SaveSnapshot::class.java),
        ) { "SaveRAM snapshot payload is null" }
        require(snapshot.romIdentity.equals(requestedSha, ignoreCase = true)) {
            "SaveRAM snapshot payload belongs to another ROM"
        }
        require(snapshot.saveIdentity == record.saveIdentity) {
            "SaveRAM snapshot identity metadata does not match its payload"
        }
        require(snapshot.schemaId == record.saveSchemaId) {
            "SaveRAM snapshot schema metadata does not match its payload"
        }
        StoredSaveSnapshot(
            snapshot = snapshot,
            sourceLastModifiedEpochMs = record.sourceLastModifiedEpochMs,
            refreshedAtEpochMs = record.refreshedAtEpochMs,
        )
    } catch (failure: Exception) {
        throw CorruptSnapshotPayloadException(failure)
    }

    private fun reportCorruption(failure: CorruptSnapshotPayloadException) {
        val reason = failure.cause?.javaClass?.simpleName
            ?.take(MAX_DIAGNOSTIC_REASON_LENGTH)
            .orEmpty()
        runCatching {
            onCorruptSnapshot(SaveSnapshotCorruption(reason = reason))
        }
    }

    private fun migrateLegacySnapshots() {
        catalogDirectory.listFiles { file -> file.isFile && LEGACY_CATALOG_FILE.matches(file.name) }.orEmpty()
            .forEach { file -> migrateLegacySnapshot(file, file.nameWithoutExtension.lowercase()) }
    }

    private fun migrateLegacySnapshot(legacyFile: File, romSha256: String) {
        if (!legacyFile.isFile || fileFor(romSha256).isFile) return
        val record = try {
            databaseFactory.open(legacyFile).use { database ->
                val hasSnapshot = database.query(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'save_snapshot'",
                ) { row -> row.string("name") }.isNotEmpty()
                if (!hasSnapshot) return@use null
                database.query(
                    """
                    SELECT rom_sha256, save_identity, save_schema_id, payload_json,
                           source_last_modified_epoch_ms, refreshed_at_epoch_ms
                    FROM save_snapshot WHERE id = 1
                    """.trimIndent(),
                ) { row ->
                    SnapshotRecord(
                        romSha256 = requireNotNull(row.string("rom_sha256")),
                        saveIdentity = requireNotNull(row.string("save_identity")),
                        saveSchemaId = requireNotNull(row.string("save_schema_id")),
                        payloadJson = requireNotNull(row.string("payload_json")),
                        sourceLastModifiedEpochMs = requireNotNull(row.long("source_last_modified_epoch_ms")),
                        refreshedAtEpochMs = requireNotNull(row.long("refreshed_at_epoch_ms")),
                    )
                }.singleOrNull()
            }
        } catch (_: Exception) {
            null
        } ?: return
        require(record.romSha256.equals(romSha256, ignoreCase = true)) { "SaveRAM snapshot belongs to another ROM" }
        writeRecord(record.copy(romSha256 = romSha256))
    }

    private fun writeRecord(record: SnapshotRecord) {
        requireHash(record.romSha256)
        val file = fileFor(record.romSha256)
        CanonicalDatabaseWriteCoordinator.write(file) {
            databaseFactory.open(file).use { database ->
                SaveSnapshotMigration.prepare(database)
                database.transaction {
                    database.execute(
                        """
                        INSERT OR REPLACE INTO save_snapshot (
                            id, rom_sha256, save_identity, save_schema_id, payload_json,
                            source_last_modified_epoch_ms, refreshed_at_epoch_ms
                        ) VALUES (1, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        listOf(
                            record.romSha256.lowercase(),
                            record.saveIdentity,
                            record.saveSchemaId,
                            record.payloadJson,
                            record.sourceLastModifiedEpochMs,
                            record.refreshedAtEpochMs,
                        ),
                    )
                }
            }
        }
    }

    private fun fileFor(sha256: String) = File(snapshotDirectory, "${sha256.lowercase()}.sqlite")

    private fun legacyFileFor(sha256: String) = File(catalogDirectory, "${sha256.lowercase()}.sqlite")

    private fun requireHash(sha256: String) {
        require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "catalog SHA-256 is invalid" }
    }

    private data class SnapshotRecord(
        val romSha256: String,
        val saveIdentity: String,
        val saveSchemaId: String,
        val payloadJson: String,
        val sourceLastModifiedEpochMs: Long,
        val refreshedAtEpochMs: Long,
    )

    private class CorruptSnapshotPayloadException(
        cause: Exception,
    ) : Exception(cause)

    private companion object {
        const val SNAPSHOT_DIRECTORY = "save-snapshots"
        const val MAX_DIAGNOSTIC_REASON_LENGTH = 64
        val LEGACY_CATALOG_FILE = Regex("[0-9a-fA-F]{64}\\.sqlite")
    }
}
