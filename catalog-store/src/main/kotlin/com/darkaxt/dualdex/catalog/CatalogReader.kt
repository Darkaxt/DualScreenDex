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
import java.io.InputStreamReader
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

        val sections = database.query(
            "SELECT name, encoding FROM catalog_sections",
        ) { row ->
            require(row.requiredString("encoding") == "gzip+json") { "unsupported catalog section encoding" }
            row.requiredString("name")
        }.toSet()
        require(sections == CatalogSchema.requiredSections) { "completed catalog has missing or unknown sections" }

        return StoredCatalog(
            catalog = codec.decode(metadata.sha256, metadata.crc32, metadata.family, metadata.platform) { name ->
                database.query(
                    "SELECT payload FROM catalog_sections WHERE name = ?",
                    listOf(name),
                ) { row ->
                    requireNotNull(row.bytes("payload")) { "catalog section payload is null" }
                }.single()
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

    fun encodeSection(catalog: ParsedCatalog, name: String): ByteArray = when (name) {
        "species" -> encode(catalog.speciesById, speciesType)
        "moves" -> encode(catalog.movesById, movesType)
        "types" -> encode(catalog.typesById, typesType)
        "abilities" -> encode(catalog.abilitiesById, abilitiesType)
        "natures" -> encode(catalog.naturesById, naturesType)
        "type_chart" -> encode(catalog.typeChart, chartType)
        "encounters" -> encode(catalog.encounterAreas, encountersType)
        "capture_balls" -> encode(catalog.captureBallsById, ballsType)
        "learnset_rulesets" -> encode(catalog.learnsetRulesets, rulesetsType)
        "runtime_metadata" -> encode(catalog.runtimeMetadata, runtimeMetadataType)
        "world_maps" -> encode(catalog.worldMaps, worldMapsType)
        "trainer_assets" -> encode(catalog.trainerAssets, trainerAssetsType)
        "local_maps" -> encode(catalog.localMaps, localMapsType)
        "theme" -> encode(catalog.theme, themeType)
        "capabilities" -> encode(catalog.capabilities, capabilitiesType)
        "diagnostics" -> encode(catalog.diagnostics, diagnosticsType)
        else -> error("unknown catalog section: $name")
    }

    fun decode(
        sha256: String,
        crc32: String,
        family: EngineFamily,
        platform: Platform,
        sections: Map<String, ByteArray>,
    ): ParsedCatalog = decode(sha256, crc32, family, platform, sections::getValue)

    fun decode(
        sha256: String,
        crc32: String,
        family: EngineFamily,
        platform: Platform,
        section: (String) -> ByteArray,
    ): ParsedCatalog {
        val encounterAreas = decode<List<EncounterArea>>(section("encounters"), encountersType)
            .map { area ->
                val windows = runCatching { area.windows }.getOrNull()
                if (windows.isNullOrEmpty()) area.copy(windows = setOf(EncounterWindow.ANY)) else area
            }
        return ParsedCatalog(
        romSha256 = sha256,
        romCrc32 = crc32,
        family = family,
        platform = platform,
        speciesById = decode(section("species"), speciesType),
        movesById = decode(section("moves"), movesType),
        typesById = decode(section("types"), typesType),
        abilitiesById = decode(section("abilities"), abilitiesType),
        naturesById = decode(section("natures"), naturesType),
        typeChart = decode(section("type_chart"), chartType),
        encounterAreas = encounterAreas,
        captureBallsById = decode(section("capture_balls"), ballsType),
        learnsetRulesets = decode(section("learnset_rulesets"), rulesetsType),
        runtimeMetadata = decode<CatalogRuntimeMetadata>(
            section("runtime_metadata"),
            runtimeMetadataType,
        ).validate(),
        worldMaps = decode<WorldMapCatalog>(section("world_maps"), worldMapsType).validate(),
        trainerAssets = decode<TrainerAssetCatalog>(section("trainer_assets"), trainerAssetsType).validate(),
        localMaps = decode<LocalMapCatalog>(section("local_maps"), localMapsType).validate(),
        theme = decode<CatalogTheme>(section("theme"), themeType).validate(),
        capabilities = decode(section("capabilities"), capabilitiesType),
        diagnostics = decode(section("diagnostics"), diagnosticsType),
        )
    }

    private fun encode(value: Any, type: Type): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip ->
            OutputStreamWriter(gzip, Charsets.UTF_8).use { writer -> gson.toJson(value, type, writer) }
        }
        return output.toByteArray()
    }

    private fun <T> decode(payload: ByteArray, type: Type): T {
        return GZIPInputStream(ByteArrayInputStream(payload)).use { gzip ->
            InputStreamReader(gzip, Charsets.UTF_8).use { reader -> gson.fromJson(reader, type) }
        }
    }

    private inline fun <reified T> type(): Type = object : TypeToken<T>() {}.type
}

private fun CatalogRow.requiredString(column: String): String =
    requireNotNull(string(column)) { "catalog column $column is null" }

private fun CatalogRow.requiredLong(column: String): Long =
    requireNotNull(long(column)) { "catalog column $column is null" }
