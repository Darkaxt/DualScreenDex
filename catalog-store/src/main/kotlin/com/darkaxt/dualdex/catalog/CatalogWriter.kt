package com.darkaxt.dualdex.catalog

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializationPhase
import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializationProgress
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import java.io.FilterOutputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest

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

    fun write(
        catalog: ParsedCatalog,
        source: CatalogSourceMetadata,
        progress: CatalogWriteProgress,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ) {
        cancellation.throwIfCancellationRequested()
        require(catalog.romSha256.matches(Regex("[0-9a-fA-F]{64}"))) { "catalog SHA-256 is invalid" }
        require(catalog.romCrc32.matches(Regex("[0-9a-fA-F]{8}"))) { "catalog CRC32 is invalid" }
        val now = clock()
        CatalogMigration.prepare(database)
        cancellation.throwIfCancellationRequested()
        database.transaction(cancellation) {
            cancellation.throwIfCancellationRequested()
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
                cancellation.throwIfCancellationRequested()
                val previousDigestLength = database.query(
                    "SELECT length(payload) AS payload_length FROM catalog_sections WHERE name = ?",
                    listOf(name),
                ) { row -> requireNotNull(row.long("payload_length")) }.singleOrNull()
                val previousDigest = previousDigestLength
                    ?.takeIf { it == SHA_256_BYTES.toLong() }
                    ?.let { payloadLength ->
                        database.readBlob(
                            "SELECT payload AS payload FROM catalog_sections WHERE name = ? AND length(payload) = ?",
                            listOf(name, payloadLength),
                            SHA_256_BYTES,
                        )
                    }
                val candidateDigest = previousDigest
                    ?.takeIf { it.size == SHA_256_BYTES }
                    ?.let { codec.encodedDigest(catalog, name, cancellation) }
                if (candidateDigest != null && previousDigest.contentEquals(candidateDigest)) {
                    database.execute(
                        "UPDATE catalog_sections SET committed_phase = ?, written_at_epoch_ms = ? WHERE name = ?",
                        listOf(progress.phase, now, name),
                    )
                    return@forEach
                }
                database.execute(
                    "DELETE FROM catalog_section_chunks WHERE section_name = ?",
                    listOf(name),
                )
                val output = CatalogChunkOutputStream(CatalogSchema.sectionChunkBytes) { index, chunk ->
                    cancellation.throwIfCancellationRequested()
                    database.execute(
                        """
                        INSERT INTO catalog_section_chunks (section_name, chunk_index, payload)
                        VALUES (?, ?, ?)
                        """.trimIndent(),
                        listOf(name, index, chunk),
                    )
                    cancellation.throwIfCancellationRequested()
                }
                val writtenDigest = codec.writeSectionAndDigest(catalog, name, output, cancellation)
                cancellation.throwIfCancellationRequested()
                database.execute(
                    """
                    INSERT OR REPLACE INTO catalog_sections
                    (name, encoding, payload, committed_phase, written_at_epoch_ms)
                    VALUES (?, 'gzip+json+chunks-v1', ?, ?, ?)
                    """.trimIndent(),
                    listOf(name, writtenDigest, progress.phase, now),
                )
            }
            cancellation.throwIfCancellationRequested()
            if (progress.complete) {
                val committed = database.query("SELECT name FROM catalog_sections") { row ->
                    requireNotNull(row.string("name"))
                }.toSet()
                require(committed == CatalogSchema.requiredSections) { "complete catalog transaction has missing sections" }
            }
            cancellation.throwIfCancellationRequested()
        }
    }
}

private fun CatalogSectionCodec.encodedDigest(
    catalog: ParsedCatalog,
    name: String,
    cancellation: ParserCancellationToken,
): ByteArray {
    val sink = object : OutputStream() {
        override fun write(value: Int) = Unit
        override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
    }
    return writeSectionAndDigest(catalog, name, sink, cancellation)
}

private fun CatalogSectionCodec.writeSectionAndDigest(
    catalog: ParsedCatalog,
    name: String,
    output: OutputStream,
    cancellation: ParserCancellationToken,
): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    val cancellableOutput = CancellationCheckingOutputStream(output, cancellation)
    DigestOutputStream(cancellableOutput, digest).use { encoded -> writeSection(catalog, name, encoded) }
    cancellation.throwIfCancellationRequested()
    return digest.digest()
}

private class CancellationCheckingOutputStream(
    output: OutputStream,
    private val cancellation: ParserCancellationToken,
) : FilterOutputStream(output) {
    override fun write(value: Int) {
        cancellation.throwIfCancellationRequested()
        out.write(value)
        cancellation.throwIfCancellationRequested()
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        cancellation.throwIfCancellationRequested()
        out.write(bytes, offset, length)
        cancellation.throwIfCancellationRequested()
    }

    override fun close() {
        cancellation.throwIfCancellationRequested()
        super.close()
        cancellation.throwIfCancellationRequested()
    }
}

internal class CatalogChunkOutputStream(
    maximumBytes: Int,
    private val writeChunk: (Int, ByteArray) -> Unit,
) : OutputStream() {
    private var buffer = ByteArray(maximumBytes)
    private var position = 0
    private var chunkIndex = 0
    private var closed = false

    init {
        require(maximumBytes > 0) { "catalog section chunk size must be positive" }
    }

    override fun write(value: Int) {
        check(!closed) { "catalog chunk output is closed" }
        if (position == buffer.size) emitFullChunk()
        buffer[position++] = value.toByte()
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        check(!closed) { "catalog chunk output is closed" }
        require(offset >= 0 && length >= 0 && offset + length <= bytes.size) {
            "catalog chunk source range is invalid"
        }
        var sourceOffset = offset
        var remaining = length
        while (remaining > 0) {
            if (position == buffer.size) emitFullChunk()
            val count = minOf(remaining, buffer.size - position)
            bytes.copyInto(buffer, position, sourceOffset, sourceOffset + count)
            position += count
            sourceOffset += count
            remaining -= count
        }
    }

    override fun close() {
        if (closed) return
        if (position > 0) {
            writeChunk(chunkIndex++, buffer.copyOf(position))
            position = 0
        }
        closed = true
    }

    private fun emitFullChunk() {
        writeChunk(chunkIndex++, buffer)
        buffer = ByteArray(buffer.size)
        position = 0
    }
}

private const val SHA_256_BYTES = 32
