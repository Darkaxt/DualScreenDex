package com.darkaxt.dualdex.catalog

object CatalogSchema {
    const val version = 2
    const val parserSchemaVersion = 52
    const val sectionChunkBytes = 256 * 1024

    // The largest retained corpus database is 6.9 MiB; keep broad map-heavy headroom without unbounded decode.
    const val maximumSectionChunks = 128
    const val maximumSectionEncodedBytes = 32 * 1024 * 1024
    const val maximumCatalogEncodedBytes = 64 * 1024 * 1024
    const val maximumSectionInflatedBytes = 128 * 1024 * 1024
    const val maximumCatalogInflatedBytes = 256 * 1024 * 1024

    const val languageOverlayPrefix = "language_overlay:"
    const val maximumLanguageOverlays = 16
    const val maximumLanguageOverlayChunks = 32
    const val maximumLanguageOverlayEncodedBytes = 8 * 1024 * 1024
    const val maximumLanguageOverlaysEncodedBytes = 32 * 1024 * 1024
    const val maximumLanguageOverlayInflatedBytes = 32 * 1024 * 1024
    const val maximumLanguageOverlaysInflatedBytes = 128 * 1024 * 1024

    val requiredSections = linkedSetOf(
        "language_manifest",
        "species",
        "moves",
        "types",
        "abilities",
        "natures",
        "type_chart",
        "encounters",
        "capture_balls",
        "learnset_rulesets",
        "runtime_metadata",
        "world_maps",
        "trainer_assets",
        "local_maps",
        "theme",
        "capabilities",
        "diagnostics",
    )

    val maximumCatalogSections: Int = requiredSections.size + maximumLanguageOverlays

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
        """
        CREATE TABLE IF NOT EXISTS catalog_section_chunks (
            section_name TEXT NOT NULL,
            chunk_index INTEGER NOT NULL,
            payload BLOB NOT NULL,
            PRIMARY KEY (section_name, chunk_index)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS catalog_section_chunks_name ON catalog_section_chunks(section_name, chunk_index)",
        "CREATE INDEX IF NOT EXISTS catalog_metadata_crc_size ON catalog_metadata(crc32, rom_size)",
    )

    val dropStatements = listOf(
        "DROP TABLE IF EXISTS catalog_section_chunks",
        "DROP TABLE IF EXISTS catalog_sections",
        "DROP TABLE IF EXISTS catalog_metadata",
    )
}
