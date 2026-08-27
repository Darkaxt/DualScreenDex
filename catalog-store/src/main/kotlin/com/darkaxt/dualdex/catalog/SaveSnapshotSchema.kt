package com.darkaxt.dualdex.catalog

object SaveSnapshotSchema {
    const val version = 1

    val createStatements = listOf(
        """
        CREATE TABLE IF NOT EXISTS save_snapshot (
            id INTEGER PRIMARY KEY CHECK (id = 1),
            rom_sha256 TEXT NOT NULL,
            save_identity TEXT NOT NULL,
            save_schema_id TEXT NOT NULL,
            payload_json TEXT NOT NULL,
            source_last_modified_epoch_ms INTEGER NOT NULL,
            refreshed_at_epoch_ms INTEGER NOT NULL
        )
        """.trimIndent(),
    )
}

object SaveSnapshotMigration {
    fun prepare(database: CatalogDatabase) {
        val version = database.query("PRAGMA user_version") { row -> row.long("user_version")?.toInt() ?: 0 }
            .singleOrNull() ?: 0
        require(version == 0 || version == SaveSnapshotSchema.version) {
            "unsupported recovery snapshot schema: $version"
        }
        database.transaction {
            SaveSnapshotSchema.createStatements.forEach(database::execute)
            database.execute("PRAGMA user_version = ${SaveSnapshotSchema.version}")
        }
    }
}
