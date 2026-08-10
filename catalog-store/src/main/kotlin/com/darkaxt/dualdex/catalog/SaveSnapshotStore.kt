package com.darkaxt.dualdex.catalog

import com.darkaxt.dualdex.save.SaveSnapshot
import com.google.gson.Gson
import java.io.File

data class StoredSaveSnapshot(
    val snapshot: SaveSnapshot,
    val sourceLastModifiedEpochMs: Long,
    val refreshedAtEpochMs: Long,
)

interface SaveSnapshotRepository {
    fun write(snapshot: SaveSnapshot, sourceLastModifiedEpochMs: Long, refreshedAtEpochMs: Long)
    fun read(romSha256: String): StoredSaveSnapshot?
}

class SaveSnapshotStore(
    private val catalogDirectory: File,
    private val databaseFactory: CatalogDatabaseFactory,
    private val gson: Gson = Gson(),
) : SaveSnapshotRepository {
    init {
        require(catalogDirectory.exists() || catalogDirectory.mkdirs()) {
            "catalog cache directory could not be created: $catalogDirectory"
        }
        require(catalogDirectory.isDirectory) { "catalog cache path is not a directory: $catalogDirectory" }
    }

    @Synchronized
    override fun write(snapshot: SaveSnapshot, sourceLastModifiedEpochMs: Long, refreshedAtEpochMs: Long) {
        requireHash(snapshot.romIdentity)
        databaseFactory.open(fileFor(snapshot.romIdentity)).use { database ->
            CatalogMigration.prepare(database)
            database.transaction {
                database.execute(
                    """
                    INSERT OR REPLACE INTO save_snapshot (
                        id, rom_sha256, save_identity, save_schema_id, payload_json,
                        source_last_modified_epoch_ms, refreshed_at_epoch_ms
                    ) VALUES (1, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    listOf(
                        snapshot.romIdentity.lowercase(),
                        snapshot.saveIdentity,
                        snapshot.schemaId,
                        gson.toJson(snapshot),
                        sourceLastModifiedEpochMs,
                        refreshedAtEpochMs,
                    ),
                )
            }
        }
    }

    @Synchronized
    override fun read(romSha256: String): StoredSaveSnapshot? {
        requireHash(romSha256)
        val file = fileFor(romSha256)
        if (!file.isFile) return null
        return databaseFactory.open(file).use { database ->
            CatalogMigration.prepare(database)
            database.query(
                """
                SELECT rom_sha256, payload_json, source_last_modified_epoch_ms, refreshed_at_epoch_ms
                FROM save_snapshot WHERE id = 1
                """.trimIndent(),
            ) { row ->
                val storedHash = requireNotNull(row.string("rom_sha256"))
                require(storedHash.equals(romSha256, ignoreCase = true)) { "SaveRAM snapshot belongs to another ROM" }
                StoredSaveSnapshot(
                    snapshot = gson.fromJson(requireNotNull(row.string("payload_json")), SaveSnapshot::class.java),
                    sourceLastModifiedEpochMs = requireNotNull(row.long("source_last_modified_epoch_ms")),
                    refreshedAtEpochMs = requireNotNull(row.long("refreshed_at_epoch_ms")),
                )
            }.singleOrNull()
        }
    }

    private fun fileFor(sha256: String) = File(catalogDirectory, "${sha256.lowercase()}.sqlite")

    private fun requireHash(sha256: String) {
        require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "catalog SHA-256 is invalid" }
    }
}
