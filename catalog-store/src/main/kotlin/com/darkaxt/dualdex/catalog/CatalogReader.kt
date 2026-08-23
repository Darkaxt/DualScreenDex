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
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.lang.reflect.Type
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

        val sectionEncodings = database.query(
            "SELECT name, encoding FROM catalog_sections",
        ) { row ->
            row.requiredString("name") to row.requiredString("encoding")
        }.toMap()
        val sections = sectionEncodings.keys
        require(sections == CatalogSchema.requiredSections) { "completed catalog has missing or unknown sections" }
        require(sectionEncodings.values.all { it == CHUNKED_ENCODING }) {
            "unsupported catalog section encoding"
        }

        return StoredCatalog(
            catalog = codec.decode(metadata.sha256, metadata.crc32, metadata.family, metadata.platform) { name, type ->
                database.streamQuery(
                    """
                    SELECT chunk_index, payload
                    FROM catalog_section_chunks
                    WHERE section_name = ?
                    ORDER BY chunk_index
                    """.trimIndent(),
                    listOf(name),
                ) { rows ->
                    val input = CatalogChunkInputStream(name) {
                        rows.next()?.let { row ->
                            CatalogChunk(
                                requireNotNull(row.long("chunk_index")).toInt(),
                                requireNotNull(row.bytes("payload")) { "catalog section chunk payload is null" },
                            )
                        }
                    }
                    codec.decodeSection(input, type)
                }
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

    fun decodeSection(payload: InputStream, type: Type): Any {
        return GZIPInputStream(payload).use { gzip ->
            InputStreamReader(gzip, Charsets.UTF_8).use { reader -> gson.fromJson(reader, type) }
        }
    }

    private inline fun <reified T> type(): Type = object : TypeToken<T>() {}.type
}

internal data class CatalogChunk(
    val index: Int,
    val payload: ByteArray,
)

internal class CatalogChunkInputStream(
    private val sectionName: String,
    private val nextChunk: () -> CatalogChunk?,
) : InputStream() {
    private var current = ByteArray(0)
    private var offset = 0
    private var expectedIndex = 0
    private var exhausted = false

    override fun read(): Int {
        if (!ensureAvailable()) return -1
        return current[offset++].toInt() and 0xff
    }

    override fun read(destination: ByteArray, destinationOffset: Int, length: Int): Int {
        require(destinationOffset >= 0 && length >= 0 && destinationOffset + length <= destination.size) {
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
            require(chunk.payload.isNotEmpty()) { "catalog section chunk is empty: $sectionName" }
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
