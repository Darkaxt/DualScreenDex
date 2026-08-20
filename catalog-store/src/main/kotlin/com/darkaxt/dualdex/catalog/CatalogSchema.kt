package com.darkaxt.dualdex.catalog

object CatalogSchema {
    const val version = 1
    const val parserSchemaVersion = 19

    val requiredSections = linkedSetOf(
        "species",
        "moves",
        "types",
        "abilities",
        "type_chart",
        "encounters",
        "capture_balls",
        "learnset_rulesets",
        "runtime_metadata",
        "world_maps",
        "trainer_assets",
        "local_maps",
        "capabilities",
        "diagnostics",
    )

    val createStatements = listOf(
        """
        CREATE TABLE IF NOT EXISTS catalog_metadata (
            id INTEGER PRIMARY KEY CHECK (id = 1),
            schema_version INTEGER NOT NULL,
            parser_schema_version INTEGER NOT NULL,
            sha256 TEXT NOT NULL,
            crc32 TEXT NOT NULL,
            rom_size INTEGER NOT NULL,
            rom_title TEXT NOT NULL,
            source_name TEXT NOT NULL,
            source_kind TEXT NOT NULL,
            source_entry TEXT,
            family TEXT NOT NULL,
            platform TEXT NOT NULL,
            phase TEXT NOT NULL,
            completed_units INTEGER NOT NULL,
            total_units INTEGER NOT NULL,
            is_complete INTEGER NOT NULL,
            written_at_epoch_ms INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS catalog_sections (
            name TEXT PRIMARY KEY,
            encoding TEXT NOT NULL,
            payload BLOB NOT NULL,
            committed_phase TEXT NOT NULL,
            written_at_epoch_ms INTEGER NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS catalog_metadata_crc_size ON catalog_metadata(crc32, rom_size)",
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

    val dropStatements = listOf(
        "DROP TABLE IF EXISTS save_snapshot",
        "DROP TABLE IF EXISTS catalog_sections",
        "DROP TABLE IF EXISTS catalog_metadata",
    )
}
