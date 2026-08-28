package com.darkaxt.dualdex.catalog

import com.enrpau.dualscreendex.parser.catalog.AbilityRecord
import com.enrpau.dualscreendex.parser.catalog.CaptureBallRecord
import com.enrpau.dualscreendex.parser.catalog.CatalogRuntimeMetadata
import com.enrpau.dualscreendex.parser.catalog.CatalogTheme
import com.enrpau.dualscreendex.parser.catalog.EncounterArea
import com.enrpau.dualscreendex.parser.catalog.EncounterWindow
import com.enrpau.dualscreendex.parser.catalog.LearnsetRuleset
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.MoveRecord
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.catalog.TypeMatchup
import com.enrpau.dualscreendex.parser.catalog.TypeRecord
import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog
import com.enrpau.dualscreendex.parser.catalog.TrainerAssetCatalog
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.dataset.natures.NatureRecord
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.lang.reflect.Type
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class StoredCatalog(
    val catalog: ParsedCatalog,
    val source: CatalogSourceMetadata,
    val progress: CatalogWriteProgress,
    val committedSections: Set<String>,
    val writtenAtEpochMs: Long,
)

class CatalogReader(private val database: CatalogDatabase) {
    private val codec = CatalogSectionCodec()

    fun readComplete(): StoredCatalog? {
        CatalogMigration.prepare(database)
        val metadata = database.query(
            """
            SELECT schema_version, parser_schema_version, sha256, crc32, rom_size, rom_title,
                   source_name, source_kind, source_entry, family, platform, phase,
                   completed_units, total_units, is_complete, written_at_epoch_ms
            FROM catalog_metadata WHERE id = 1
            """.trimIndent(),
        ) { row ->
            Metadata(
                schemaVersion = row.requiredLong("schema_version").toInt(),
                parserSchemaVersion = row.requiredLong("parser_schema_version").toInt(),
                sha256 = row.requiredString("sha256"),
                crc32 = row.requiredString("crc32"),
                romSize = row.requiredLong("rom_size").toInt(),
                romTitle = row.requiredString("rom_title"),
                sourceName = row.requiredString("source_name"),
                sourceKind = CatalogSourceKind.valueOf(row.requiredString("source_kind")),
                sourceEntry = row.string("source_entry"),
                family = EngineFamily.valueOf(row.requiredString("family")),
                platform = Platform.valueOf(row.requiredString("platform")),
                phase = row.requiredString("phase"),
                completedUnits = row.requiredLong("completed_units").toInt(),
                totalUnits = row.requiredLong("total_units").toInt(),
                complete = row.requiredLong("is_complete") == 1L,
                writtenAt = row.requiredLong("written_at_epoch_ms"),
            )
        }.singleOrNull() ?: return null
        if (!metadata.complete || metadata.schemaVersion != CatalogSchema.version ||
            metadata.parserSchemaVersion != CatalogSchema.parserSchemaVersion
        ) return null

        val sectionLengthRows = database.query(
            "SELECT name, encoding, length(payload) AS payload_length FROM catalog_sections LIMIT ?",
            listOf(CatalogSchema.requiredSections.size + 1),
        ) { row ->
            SectionLengthMetadata(
                name = row.requiredString("name"),
                encoding = row.requiredString("encoding"),
                payloadLength = row.requiredLong("payload_length"),
            )
        }
        require(sectionLengthRows.size <= CatalogSchema.requiredSections.size) {
            "catalog contains too many sections"
        }
        val sectionMetadata = sectionLengthRows.associate { section ->
            require(section.payloadLength == SHA_256_BYTES.toLong()) {
                "catalog section digest has an invalid size"
            }
            val digest = requireNotNull(
                database.readBlob(
                    "SELECT payload AS payload FROM catalog_sections WHERE name = ? AND length(payload) = ?",
                    listOf(section.name, section.payloadLength),
                    SHA_256_BYTES,
                ),
            ) { "catalog section digest is null or changed during retrieval" }
            require(digest.size.toLong() == section.payloadLength) {
                "catalog section digest length changed during retrieval"
            }
            section.name to SectionMetadata(
                name = section.name,
                encoding = section.encoding,
                digest = digest,
            )
        }
        val sections = sectionMetadata.keys
        require(sections == CatalogSchema.requiredSections) {
            "completed catalog has missing or unknown sections"
        }
        require(sectionMetadata.values.all { it.encoding == CHUNKED_ENCODING }) {
            "unsupported catalog section encoding"
        }
        val chunkAggregates = database.query(
            """
            SELECT section_name,
                   COUNT(*) AS chunk_count,
                   COALESCE(SUM(length(payload)), 0) AS payload_bytes,
                   COALESCE(MAX(length(payload)), 0) AS maximum_payload_bytes
            FROM catalog_section_chunks
            GROUP BY section_name
            LIMIT ?
            """.trimIndent(),
            listOf(CatalogSchema.requiredSections.size + 1),
        ) { row ->
            ChunkAggregate(
                sectionName = row.requiredString("section_name"),
                chunkCount = row.requiredLong("chunk_count"),
                payloadBytes = row.requiredLong("payload_bytes"),
                maximumPayloadBytes = row.requiredLong("maximum_payload_bytes"),
            )
        }
        require(chunkAggregates.size <= CatalogSchema.requiredSections.size) {
            "catalog contains chunk aggregates for too many sections"
        }
        val chunkAggregateBySection = chunkAggregates.associateBy(ChunkAggregate::sectionName)
        var aggregateEncodedBytes = 0L
        chunkAggregateBySection.values.forEach { aggregate ->
            require(aggregate.chunkCount in 1..CatalogSchema.maximumSectionChunks.toLong()) {
                "catalog section chunk limit exceeded: ${aggregate.sectionName}"
            }
            require(aggregate.maximumPayloadBytes in 1..CatalogSchema.sectionChunkBytes.toLong()) {
                "catalog section chunk is oversized: ${aggregate.sectionName}"
            }
            require(aggregate.payloadBytes in aggregate.chunkCount..CatalogSchema.maximumSectionEncodedBytes.toLong()) {
                "catalog section encoded-byte limit exceeded: ${aggregate.sectionName}"
            }
            require(
                aggregateEncodedBytes <= CatalogSchema.maximumCatalogEncodedBytes.toLong() - aggregate.payloadBytes,
            ) {
                "catalog encoded-byte limit exceeded"
            }
            aggregateEncodedBytes += aggregate.payloadBytes
        }
        require(chunkAggregateBySection.keys == CatalogSchema.requiredSections) {
            "completed catalog has missing or unknown section chunks"
        }
        val budget = CatalogReadBudget()

        return StoredCatalog(
            catalog = codec.decode(metadata.sha256, metadata.crc32, metadata.family, metadata.platform) { name, type ->
                val chunkRows = database.query(
                    """
                    SELECT chunk_index, length(payload) AS payload_length
                    FROM catalog_section_chunks
                    WHERE section_name = ?
                    ORDER BY chunk_index
                    """.trimIndent(),
                    listOf(name),
                ) { row ->
                    ChunkLengthMetadata(
                        index = row.requiredLong("chunk_index").toInt(),
                        payloadLength = row.requiredLong("payload_length"),
                    )
                }
                require(chunkRows.size.toLong() == chunkAggregateBySection.getValue(name).chunkCount) {
                    "catalog section chunk count changed during retrieval: $name"
                }
                val chunks = chunkRows.iterator()
                val input = CatalogChunkInputStream(
                    sectionName = name,
                    maximumChunks = CatalogSchema.maximumSectionChunks,
                    maximumEncodedBytes = CatalogSchema.maximumSectionEncodedBytes,
                    onEncodedBytes = budget::claimEncoded,
                ) {
                    if (!chunks.hasNext()) {
                        null
                    } else {
                        val chunk = chunks.next()
                        require(chunk.payloadLength in 1..CatalogSchema.sectionChunkBytes.toLong()) {
                            "catalog section chunk is oversized: $name"
                        }
                        val payload = requireNotNull(
                            database.readBlob(
                                """
                                SELECT payload AS payload
                                FROM catalog_section_chunks
                                WHERE section_name = ? AND chunk_index = ? AND length(payload) = ?
                                """.trimIndent(),
                                listOf(name, chunk.index, chunk.payloadLength),
                                CatalogSchema.sectionChunkBytes,
                            ),
                        ) { "catalog section chunk payload is null or changed during retrieval: $name" }
                        require(payload.size.toLong() == chunk.payloadLength) {
                            "catalog section chunk length changed during retrieval: $name"
                        }
                        CatalogChunk(chunk.index, payload)
                    }
                }
                val digest = MessageDigest.getInstance("SHA-256")
                val encoded = DigestInputStream(input, digest)
                val decoded = codec.decodeSection(
                    NonClosingInputStream(encoded),
                    type,
                    name,
                    CatalogSchema.maximumSectionInflatedBytes,
                    budget::claimInflated,
                )
                encoded.drain()
                require(
                    MessageDigest.isEqual(
                        sectionMetadata.getValue(name).digest,
                        digest.digest(),
                    ),
                ) {
                    "catalog section digest does not match: $name"
                }
                decoded
            },
            source = CatalogSourceMetadata(
                metadata.sourceName,
                metadata.romSize,
                metadata.romTitle,
                metadata.sourceKind,
                metadata.sourceEntry,
            ),
            progress = CatalogWriteProgress(
                metadata.phase,
                metadata.completedUnits,
                metadata.totalUnits,
                metadata.complete,
            ),
            committedSections = sections,
            writtenAtEpochMs = metadata.writtenAt,
        )
    }

    private data class SectionLengthMetadata(
        val name: String,
        val encoding: String,
        val payloadLength: Long,
    )

    private data class SectionMetadata(
        val name: String,
        val encoding: String,
        val digest: ByteArray,
    )

    private data class ChunkLengthMetadata(
        val index: Int,
        val payloadLength: Long,
    )

    private data class ChunkAggregate(
        val sectionName: String,
        val chunkCount: Long,
        val payloadBytes: Long,
        val maximumPayloadBytes: Long,
    )

    private data class Metadata(
        val schemaVersion: Int,
        val parserSchemaVersion: Int,
        val sha256: String,
        val crc32: String,
        val romSize: Int,
        val romTitle: String,
        val sourceName: String,
        val sourceKind: CatalogSourceKind,
        val sourceEntry: String?,
        val family: EngineFamily,
        val platform: Platform,
        val phase: String,
        val completedUnits: Int,
        val totalUnits: Int,
        val complete: Boolean,
        val writtenAt: Long,
    )

    private companion object {
        const val CHUNKED_ENCODING = "gzip+json+chunks-v1"
        const val SHA_256_BYTES = 32
    }
}

private class CatalogReadBudget {
    private var encodedBytes = 0L
    private var inflatedBytes = 0L

    fun claimEncoded(bytes: Int) {
        require(bytes >= 0 && encodedBytes <= CatalogSchema.maximumCatalogEncodedBytes - bytes) {
            "catalog encoded-byte limit exceeded"
        }
        encodedBytes += bytes
    }

    fun claimInflated(bytes: Int) {
        require(bytes >= 0 && inflatedBytes <= CatalogSchema.maximumCatalogInflatedBytes - bytes) {
            "catalog inflated-byte limit exceeded"
        }
        inflatedBytes += bytes
    }
}

private class NonClosingInputStream(input: InputStream) : FilterInputStream(input) {
    override fun close() = Unit
}

private fun InputStream.drain() {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (read(buffer) >= 0) {
        // Drain the encoded section so its complete digest and byte budget are verified.
    }
}

internal class CatalogSectionCodec {
    private val gson: Gson = GsonBuilder().serializeNulls().create()
    private val speciesType = type<Map<Int, SpeciesRecord>>()
    private val movesType = type<Map<Int, MoveRecord>>()
    private val typesType = type<Map<Int, TypeRecord>>()
    private val abilitiesType = type<Map<Int, AbilityRecord>>()
    private val naturesType = type<Map<Int, NatureRecord>>()
    private val chartType = type<List<TypeMatchup>>()
    private val encountersType = type<List<EncounterArea>>()
    private val ballsType = type<Map<Int, CaptureBallRecord>>()
    private val rulesetsType = type<List<LearnsetRuleset>>()
    private val runtimeMetadataType = type<CatalogRuntimeMetadata>()
    private val worldMapsType = type<WorldMapCatalog>()
    private val trainerAssetsType = type<TrainerAssetCatalog>()
    private val localMapsType = type<LocalMapCatalog>()
    private val themeType = type<CatalogTheme>()
    private val capabilitiesType = type<Map<RomCapability, CapabilityEvidence>>()
    private val diagnosticsType = type<List<String>>()

    fun encode(catalog: ParsedCatalog, included: Set<String>): Map<String, ByteArray> =
        included.associateWithTo(linkedMapOf()) { name -> encodeSection(catalog, name) }

    fun encodeSection(catalog: ParsedCatalog, name: String): ByteArray =
        ByteArrayOutputStream().use { output ->
            writeSection(catalog, name, output)
            output.toByteArray()
        }

    fun writeSection(catalog: ParsedCatalog, name: String, output: OutputStream) = when (name) {
        "species" -> encode(catalog.speciesById, speciesType, output)
        "moves" -> encode(catalog.movesById, movesType, output)
        "types" -> encode(catalog.typesById, typesType, output)
        "abilities" -> encode(catalog.abilitiesById, abilitiesType, output)
        "natures" -> encode(catalog.naturesById, naturesType, output)
        "type_chart" -> encode(catalog.typeChart, chartType, output)
        "encounters" -> encode(catalog.encounterAreas, encountersType, output)
        "capture_balls" -> encode(catalog.captureBallsById, ballsType, output)
        "learnset_rulesets" -> encode(catalog.learnsetRulesets, rulesetsType, output)
        "runtime_metadata" -> encode(catalog.runtimeMetadata, runtimeMetadataType, output)
        "world_maps" -> encode(catalog.worldMaps, worldMapsType, output)
        "trainer_assets" -> encode(catalog.trainerAssets, trainerAssetsType, output)
        "local_maps" -> encode(catalog.localMaps, localMapsType, output)
        "theme" -> encode(catalog.theme, themeType, output)
        "capabilities" -> encode(catalog.capabilities, capabilitiesType, output)
        "diagnostics" -> encode(catalog.diagnostics, diagnosticsType, output)
        else -> error("unknown catalog section: $name")
    }

    fun decode(
        sha256: String,
        crc32: String,
        family: EngineFamily,
        platform: Platform,
        sections: Map<String, ByteArray>,
    ): ParsedCatalog = decode(sha256, crc32, family, platform) { name, type ->
        decodeSection(ByteArrayInputStream(sections.getValue(name)), type)
    }

    fun decode(
        sha256: String,
        crc32: String,
        family: EngineFamily,
        platform: Platform,
        section: (String, Type) -> Any,
    ): ParsedCatalog {
        @Suppress("UNCHECKED_CAST")
        fun <T> decoded(name: String, type: Type): T = section(name, type) as T

        val encounterAreas = decoded<List<EncounterArea>>("encounters", encountersType)
            .map { area ->
                val windows = runCatching { area.windows }.getOrNull()
                if (windows.isNullOrEmpty()) area.copy(windows = setOf(EncounterWindow.ANY)) else area
            }
        return ParsedCatalog(
        romSha256 = sha256,
        romCrc32 = crc32,
        family = family,
        platform = platform,
        speciesById = decoded("species", speciesType),
        movesById = decoded("moves", movesType),
        typesById = decoded("types", typesType),
        abilitiesById = decoded("abilities", abilitiesType),
        naturesById = decoded("natures", naturesType),
        typeChart = decoded("type_chart", chartType),
        encounterAreas = encounterAreas,
        captureBallsById = decoded("capture_balls", ballsType),
        learnsetRulesets = decoded("learnset_rulesets", rulesetsType),
        runtimeMetadata = decoded<CatalogRuntimeMetadata>("runtime_metadata", runtimeMetadataType).validate(),
        worldMaps = decoded<WorldMapCatalog>("world_maps", worldMapsType).validate(),
        trainerAssets = decoded<TrainerAssetCatalog>("trainer_assets", trainerAssetsType).validate(),
        localMaps = decoded<LocalMapCatalog>("local_maps", localMapsType).validate(),
        theme = decoded<CatalogTheme>("theme", themeType).validate(),
        capabilities = decoded("capabilities", capabilitiesType),
        diagnostics = decoded("diagnostics", diagnosticsType),
        )
    }

    private fun encode(value: Any, type: Type, output: OutputStream) {
        GZIPOutputStream(output).use { gzip ->
            OutputStreamWriter(gzip, Charsets.UTF_8).use { writer -> gson.toJson(value, type, writer) }
        }
    }

    fun decodeSection(
        payload: InputStream,
        type: Type,
        sectionName: String = "catalog section",
        maximumInflatedBytes: Int = CatalogSchema.maximumSectionInflatedBytes,
        onInflatedBytes: (Int) -> Unit = {},
    ): Any {
        return GZIPInputStream(payload).use { gzip ->
            val bounded = CatalogInflatedInputStream(
                gzip,
                sectionName,
                maximumInflatedBytes,
                onInflatedBytes,
            )
            InputStreamReader(bounded, Charsets.UTF_8).use { reader ->
                val decoded = gson.fromJson<Any>(reader, type)
                val remainder = CharArray(DEFAULT_BUFFER_SIZE)
                while (reader.read(remainder) >= 0) {
                    // Verify the complete gzip stream, including bounded trailing whitespace.
                }
                decoded
            }
        }
    }

    private inline fun <reified T> type(): Type = object : TypeToken<T>() {}.type
}

internal class CatalogInflatedInputStream(
    input: InputStream,
    private val sectionName: String,
    private val maximumBytes: Int,
    private val onBytes: (Int) -> Unit = {},
) : FilterInputStream(input) {
    private var consumedBytes = 0L

    init {
        require(maximumBytes > 0) { "catalog inflate limit must be positive" }
    }

    override fun read(): Int = super.read().also { value ->
        if (value >= 0) claim(1)
    }

    override fun read(destination: ByteArray, destinationOffset: Int, length: Int): Int =
        super.read(destination, destinationOffset, length).also { count ->
            if (count > 0) claim(count)
        }

    private fun claim(bytes: Int) {
        require(consumedBytes <= maximumBytes.toLong() - bytes) {
            "catalog section inflate limit exceeded: $sectionName"
        }
        onBytes(bytes)
        consumedBytes += bytes
    }
}

internal data class CatalogChunk(
    val index: Int,
    val payload: ByteArray,
)

internal class CatalogChunkInputStream(
    private val sectionName: String,
    private val maximumChunks: Int = CatalogSchema.maximumSectionChunks,
    private val maximumEncodedBytes: Int = CatalogSchema.maximumSectionEncodedBytes,
    private val onEncodedBytes: (Int) -> Unit = {},
    private val nextChunk: () -> CatalogChunk?,
) : InputStream() {
    private var current = ByteArray(0)
    private var offset = 0
    private var expectedIndex = 0
    private var encodedBytes = 0L
    private var exhausted = false

    init {
        require(maximumChunks > 0) { "catalog section chunk limit must be positive" }
        require(maximumEncodedBytes > 0) { "catalog section encoded-byte limit must be positive" }
    }

    override fun read(): Int {
        if (!ensureAvailable()) return -1
        return current[offset++].toInt() and 0xff
    }

    override fun read(destination: ByteArray, destinationOffset: Int, length: Int): Int {
        require(
            destinationOffset >= 0 &&
                length >= 0 &&
                destinationOffset <= destination.size - length,
        ) {
            "catalog chunk destination range is invalid"
        }
        if (length == 0) return 0
        if (!ensureAvailable()) return -1
        val count = minOf(length, current.size - offset)
        current.copyInto(destination, destinationOffset, offset, offset + count)
        offset += count
        return count
    }

    private fun ensureAvailable(): Boolean {
        while (offset >= current.size && !exhausted) {
            val chunk = nextChunk()
            if (chunk == null) {
                exhausted = true
                require(expectedIndex > 0) { "catalog section has no chunks: $sectionName" }
                return false
            }
            require(chunk.index == expectedIndex) {
                "catalog section chunks are not contiguous: $sectionName"
            }
            require(expectedIndex < maximumChunks) {
                "catalog section chunk limit exceeded: $sectionName"
            }
            require(chunk.payload.isNotEmpty()) {
                "catalog section chunk is empty: $sectionName"
            }
            require(chunk.payload.size <= CatalogSchema.sectionChunkBytes) {
                "catalog section chunk is oversized: $sectionName"
            }
            require(encodedBytes <= maximumEncodedBytes.toLong() - chunk.payload.size) {
                "catalog section encoded-byte limit exceeded: $sectionName"
            }
            onEncodedBytes(chunk.payload.size)
            encodedBytes += chunk.payload.size
            expectedIndex++
            current = chunk.payload
            offset = 0
        }
        return offset < current.size
    }
}

private fun CatalogRow.requiredString(column: String): String =
    requireNotNull(string(column)) { "catalog column $column is null" }

private fun CatalogRow.requiredLong(column: String): Long =
    requireNotNull(long(column)) { "catalog column $column is null" }
