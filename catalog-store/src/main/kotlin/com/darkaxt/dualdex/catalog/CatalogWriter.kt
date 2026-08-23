package com.darkaxt.dualdex.catalog

import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializationPhase
import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializationProgress
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog

enum class CatalogSourceKind { DIRECT, ARCHIVE }

data class CatalogSourceMetadata(
    val displayName: String,
    val romSize: Int,
    val romTitle: String,
    val kind: CatalogSourceKind,
    val archiveEntry: String? = null,
) {
    init {
        require(displayName.isNotBlank()) { "catalog source name cannot be blank" }
        require(romSize > 0) { "catalog ROM size must be positive" }
        require(kind == CatalogSourceKind.ARCHIVE || archiveEntry == null) {
            "only archive sources can have an entry name"
        }
    }

    companion object {
        fun direct(displayName: String, romSize: Int, romTitle: String) =
            CatalogSourceMetadata(displayName, romSize, romTitle, CatalogSourceKind.DIRECT)

        fun archive(displayName: String, romSize: Int, romTitle: String): CatalogSourceMetadata {
            val entry = displayName.substringAfter('!', "").takeIf(String::isNotBlank)
            require(entry != null) { "archive source must include its selected entry after !" }
            return CatalogSourceMetadata(displayName, romSize, romTitle, CatalogSourceKind.ARCHIVE, entry)
        }

        fun fromDisplayName(displayName: String, romSize: Int, romTitle: String): CatalogSourceMetadata =
            if ('!' in displayName) archive(displayName, romSize, romTitle) else direct(displayName, romSize, romTitle)
    }
}

data class CatalogWriteProgress(
    val phase: String,
    val completedUnits: Int,
    val totalUnits: Int,
    val complete: Boolean,
    val changedSections: Set<String> = CatalogSchema.requiredSections,
) {
    init {
        require(completedUnits in 0..totalUnits) { "catalog progress must remain within its total" }
        require(!complete || completedUnits == totalUnits) { "complete catalog progress must reach its total" }
        require(CatalogSchema.requiredSections.containsAll(changedSections)) { "catalog progress names an unknown section" }
    }

    companion object {
        fun complete(totalUnits: Int = 5) = CatalogWriteProgress("COMPLETE", totalUnits, totalUnits, complete = true)
    }
}

fun catalogWriteProgress(progress: CatalogMaterializationProgress): CatalogWriteProgress = CatalogWriteProgress(
    phase = progress.phase.name,
    completedUnits = progress.completedUnits,
    totalUnits = progress.totalUnits,
    complete = progress.completedUnits == progress.totalUnits,
    changedSections = when (progress.phase) {
        CatalogMaterializationPhase.ESSENTIAL -> CatalogSchema.requiredSections
        CatalogMaterializationPhase.SPECIES_MEDIA -> setOf("species")
        CatalogMaterializationPhase.RELATIONSHIPS -> setOf("species", "encounters", "runtime_metadata")
        // EXTENDED is the final coherent snapshot; COMPLETE only commits its metadata.
        CatalogMaterializationPhase.EXTENDED -> CatalogSchema.requiredSections
        CatalogMaterializationPhase.COMPLETE -> emptySet()
    },
)

class CatalogWriter(
    private val database: CatalogDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val codec = CatalogSectionCodec()

    fun write(catalog: ParsedCatalog, source: CatalogSourceMetadata, progress: CatalogWriteProgress) {
        require(catalog.romSha256.matches(Regex("[0-9a-fA-F]{64}"))) { "catalog SHA-256 is invalid" }
        require(catalog.romCrc32.matches(Regex("[0-9a-fA-F]{8}"))) { "catalog CRC32 is invalid" }
        val now = clock()
        CatalogMigration.prepare(database)
        database.transaction {
            database.execute(
                """
                INSERT OR REPLACE INTO catalog_metadata (
                    id, schema_version, parser_schema_version, sha256, crc32, rom_size, rom_title,
                    source_name, source_kind, source_entry, family, platform, phase,
                    completed_units, total_units, is_complete, written_at_epoch_ms
                ) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                listOf(
                    CatalogSchema.version,
                    CatalogSchema.parserSchemaVersion,
                    catalog.romSha256.lowercase(),
                    catalog.romCrc32.uppercase(),
                    source.romSize,
                    source.romTitle,
                    source.displayName,
                    source.kind.name,
                    source.archiveEntry,
                    catalog.family.name,
                    catalog.platform.name,
                    progress.phase,
                    progress.completedUnits,
                    progress.totalUnits,
                    if (progress.complete) 1 else 0,
                    now,
                ),
            )
            progress.changedSections.forEach { name ->
                val payload = codec.encodeSection(catalog, name)
                database.execute(
                    "DELETE FROM catalog_section_chunks WHERE section_name = ?",
                    listOf(name),
                )
                payload.asSequenceChunks(CatalogSchema.sectionChunkBytes).forEachIndexed { index, chunk ->
                    database.execute(
                        """
                        INSERT INTO catalog_section_chunks (section_name, chunk_index, payload)
                        VALUES (?, ?, ?)
                        """.trimIndent(),
                        listOf(name, index, chunk),
                    )
                }
                database.execute(
                    """
                    INSERT OR REPLACE INTO catalog_sections
                    (name, encoding, payload, committed_phase, written_at_epoch_ms)
                    VALUES (?, 'gzip+json+chunks-v1', ?, ?, ?)
                    """.trimIndent(),
                    listOf(name, ByteArray(0), progress.phase, now),
                )
            }
            if (progress.complete) {
                val committed = database.query("SELECT name FROM catalog_sections") { row ->
                    requireNotNull(row.string("name"))
                }.toSet()
                require(committed == CatalogSchema.requiredSections) { "complete catalog transaction has missing sections" }
            }
        }
    }
}

private fun ByteArray.asSequenceChunks(maximumBytes: Int): Sequence<ByteArray> {
    require(maximumBytes > 0) { "catalog section chunk size must be positive" }
    return sequence {
        var start = 0
        while (start < size) {
            val end = minOf(start + maximumBytes, size)
            yield(copyOfRange(start, end))
            start = end
        }
    }
}
