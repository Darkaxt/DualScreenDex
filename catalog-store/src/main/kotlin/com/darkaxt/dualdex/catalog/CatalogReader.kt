package com.darkaxt.dualdex.catalog

import com.enrpau.dualscreendex.parser.catalog.AbilityRecord
import com.enrpau.dualscreendex.parser.catalog.CaptureBallRecord
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.CatalogLanguageOverlay
import com.enrpau.dualscreendex.parser.catalog.CatalogLocalization
import com.enrpau.dualscreendex.parser.catalog.CatalogPoiText
import com.enrpau.dualscreendex.parser.catalog.CatalogRuntimeMetadata
import com.enrpau.dualscreendex.parser.catalog.CatalogTheme
import com.enrpau.dualscreendex.parser.catalog.EncounterArea
import com.enrpau.dualscreendex.parser.catalog.EncounterWindow
import com.enrpau.dualscreendex.parser.catalog.LearnsetRuleset
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.LocalizedCapabilityState
import com.enrpau.dualscreendex.parser.catalog.LocalizedTextCapability
import com.enrpau.dualscreendex.parser.catalog.MoveRecord
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.catalog.TypeMatchup
import com.enrpau.dualscreendex.parser.catalog.TypeRecord
import com.enrpau.dualscreendex.parser.catalog.WorldLocationKey
import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog
import com.enrpau.dualscreendex.parser.catalog.TrainerAssetCatalog
import com.enrpau.dualscreendex.parser.language.LanguageEvidence
import com.enrpau.dualscreendex.parser.language.LanguageEvidenceKind
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.LocalizedTableLayout
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.language.RomLanguageProjection
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.dataset.natures.NatureRecord
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
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
            listOf(CatalogSchema.maximumCatalogSections + 1),
        ) { row ->
            SectionLengthMetadata(
                name = row.requiredString("name"),
                encoding = row.requiredString("encoding"),
                payloadLength = row.requiredLong("payload_length"),
            )
        }
        require(sectionLengthRows.size <= CatalogSchema.maximumCatalogSections) {
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
        require(sections.containsAll(CatalogSchema.requiredSections)) {
            "completed catalog has missing shared sections"
        }
        sections.filterNot(CatalogSchema.requiredSections::contains).forEach { sectionName ->
            require(CatalogSectionPlan.parseOverlaySectionName(sectionName) != null) {
                "completed catalog has an unknown section"
            }
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
            listOf(CatalogSchema.maximumCatalogSections + 1),
        ) { row ->
            ChunkAggregate(
                sectionName = row.requiredString("section_name"),
                chunkCount = row.requiredLong("chunk_count"),
                payloadBytes = row.requiredLong("payload_bytes"),
                maximumPayloadBytes = row.requiredLong("maximum_payload_bytes"),
            )
        }
        require(chunkAggregates.size <= CatalogSchema.maximumCatalogSections) {
            "catalog contains chunk aggregates for too many sections"
        }
        val chunkAggregateBySection = chunkAggregates.associateBy(ChunkAggregate::sectionName)
        var aggregateEncodedBytes = 0L
        var aggregateOverlayEncodedBytes = 0L
        chunkAggregateBySection.values.forEach { aggregate ->
            val languageOverlay = CatalogSectionPlan.parseOverlaySectionName(aggregate.sectionName) != null
            val maximumChunks = if (languageOverlay) {
                CatalogSchema.maximumLanguageOverlayChunks
            } else {
                CatalogSchema.maximumSectionChunks
            }
            val maximumEncodedBytes = if (languageOverlay) {
                CatalogSchema.maximumLanguageOverlayEncodedBytes
            } else {
                CatalogSchema.maximumSectionEncodedBytes
            }
            require(aggregate.chunkCount in 1..maximumChunks.toLong()) {
                "catalog section chunk limit exceeded: ${aggregate.sectionName}"
            }
            require(aggregate.maximumPayloadBytes in 1..CatalogSchema.sectionChunkBytes.toLong()) {
                "catalog section chunk is oversized: ${aggregate.sectionName}"
            }
            require(aggregate.payloadBytes in aggregate.chunkCount..maximumEncodedBytes.toLong()) {
                "catalog section encoded-byte limit exceeded: ${aggregate.sectionName}"
            }
            require(
                aggregateEncodedBytes <= CatalogSchema.maximumCatalogEncodedBytes.toLong() - aggregate.payloadBytes,
            ) {
                "catalog encoded-byte limit exceeded"
            }
            aggregateEncodedBytes += aggregate.payloadBytes
            if (languageOverlay) {
                require(
                    aggregateOverlayEncodedBytes <= CatalogSchema.maximumLanguageOverlaysEncodedBytes.toLong() -
                        aggregate.payloadBytes,
                ) { "catalog language-overlay encoded-byte limit exceeded" }
                aggregateOverlayEncodedBytes += aggregate.payloadBytes
            }
        }
        require(chunkAggregateBySection.keys == sections) {
            "completed catalog has missing or unknown section chunks"
        }
        val budget = CatalogReadBudget()

        val readSection: (String, Type) -> Any = { name, type ->
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
                val languageOverlay = CatalogSectionPlan.parseOverlaySectionName(name) != null
                val input = CatalogChunkInputStream(
                    sectionName = name,
                    maximumChunks = if (languageOverlay) {
                        CatalogSchema.maximumLanguageOverlayChunks
                    } else {
                        CatalogSchema.maximumSectionChunks
                    },
                    maximumEncodedBytes = if (languageOverlay) {
                        CatalogSchema.maximumLanguageOverlayEncodedBytes
                    } else {
                        CatalogSchema.maximumSectionEncodedBytes
                    },
                    onEncodedBytes = { bytes -> budget.claimEncoded(name, bytes) },
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
                    if (languageOverlay) {
                        CatalogSchema.maximumLanguageOverlayInflatedBytes
                    } else {
                        CatalogSchema.maximumSectionInflatedBytes
                    },
                    { bytes -> budget.claimInflated(name, bytes) },
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
        }
        val manifest = codec.decodeLanguageManifest(readSection)
        val plan = CatalogSectionPlan.from(manifest)
        require(sections == plan.sections) {
            "completed catalog sections do not match the language manifest"
        }

        return StoredCatalog(
            catalog = codec.decode(
                metadata.sha256,
                metadata.crc32,
                metadata.family,
                metadata.platform,
                manifest,
                readSection,
            ),
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

internal class CatalogReadBudget {
    private var encodedBytes = 0L
    private var overlayEncodedBytes = 0L
    private var inflatedBytes = 0L
    private var overlayInflatedBytes = 0L

    fun claimEncoded(sectionName: String, bytes: Int) {
        require(bytes >= 0 && encodedBytes <= CatalogSchema.maximumCatalogEncodedBytes - bytes) {
            "catalog encoded-byte limit exceeded"
        }
        encodedBytes += bytes
        if (CatalogSectionPlan.parseOverlaySectionName(sectionName) != null) {
            require(
                overlayEncodedBytes <= CatalogSchema.maximumLanguageOverlaysEncodedBytes - bytes,
            ) { "catalog language-overlay encoded-byte limit exceeded" }
            overlayEncodedBytes += bytes
        }
    }

    fun claimInflated(sectionName: String, bytes: Int) {
        require(bytes >= 0 && inflatedBytes <= CatalogSchema.maximumCatalogInflatedBytes - bytes) {
            "catalog inflated-byte limit exceeded"
        }
        inflatedBytes += bytes
        if (CatalogSectionPlan.parseOverlaySectionName(sectionName) != null) {
            require(
                overlayInflatedBytes <= CatalogSchema.maximumLanguageOverlaysInflatedBytes - bytes,
            ) { "catalog language-overlay inflated-byte limit exceeded" }
            overlayInflatedBytes += bytes
        }
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

private data class StoredLanguageEvidence(
    val kind: LanguageEvidenceKind? = null,
    val summary: String? = null,
    val confidence: Int? = null,
) {
    fun toModel() = LanguageEvidence(
        kind = requireNotNull(kind) { "persisted language evidence requires a kind" },
        summary = requireNotNull(summary) { "persisted language evidence requires a summary" },
        confidence = requireNotNull(confidence) { "persisted language evidence requires confidence" },
    )

    companion object {
        fun from(value: LanguageEvidence) = StoredLanguageEvidence(
            kind = value.kind,
            summary = value.summary,
            confidence = value.confidence,
        )
    }
}

private data class StoredLocalizedTableLayout(
    val speciesNames: TableLayout? = null,
    val moveNames: TableLayout? = null,
    val descriptions: TableLayout? = null,
    val abilities: TableLayout? = null,
    val typeNames: TableLayout? = null,
) {
    fun toModel() = LocalizedTableLayout(
        speciesNames = speciesNames,
        moveNames = moveNames,
        descriptions = descriptions,
        abilities = abilities,
        typeNames = typeNames,
    )

    companion object {
        fun from(value: LocalizedTableLayout) = StoredLocalizedTableLayout(
            speciesNames = value.speciesNames,
            moveNames = value.moveNames,
            descriptions = value.descriptions,
            abilities = value.abilities,
            typeNames = value.typeNames,
        )
    }
}

private data class StoredLanguageProjection(
    val language: String? = null,
    val codecId: String? = null,
    val codecVersion: Int? = null,
    val localizedTables: StoredLocalizedTableLayout? = null,
    val evidence: List<StoredLanguageEvidence?>? = null,
    val status: LanguageResolutionStatus? = null,
) {
    fun toModel() = RomLanguageProjection(
        language = LanguageTag.of(requireNotNull(language) { "persisted language projection requires a language" }),
        codecId = requireNotNull(codecId) { "persisted language projection requires a codec ID" },
        codecVersion = requireNotNull(codecVersion) { "persisted language projection requires a codec version" },
        localizedTables = requireNotNull(localizedTables) {
            "persisted language projection requires localized tables"
        }.toModel(),
        evidence = requireNotNull(evidence) { "persisted language projection requires evidence" }.map { item ->
            requireNotNull(item) { "persisted language projection contains null evidence" }.toModel()
        },
        status = requireNotNull(status) { "persisted language projection requires a status" },
    )

    companion object {
        fun from(value: RomLanguageProjection) = StoredLanguageProjection(
            language = value.language.value,
            codecId = value.codecId,
            codecVersion = value.codecVersion,
            localizedTables = StoredLocalizedTableLayout.from(value.localizedTables),
            evidence = value.evidence.map(StoredLanguageEvidence::from),
            status = value.status,
        )
    }
}

private data class StoredLanguageManifest(
    val defaultLanguage: String? = null,
    val projections: List<StoredLanguageProjection?>? = null,
    val status: LanguageResolutionStatus? = null,
    val diagnostics: List<String?>? = null,
) {
    fun toModel() = RomLanguageManifest(
        defaultLanguage = defaultLanguage?.let(LanguageTag::of),
        projections = requireNotNull(projections) { "persisted language manifest requires projections" }.map { item ->
            requireNotNull(item) { "persisted language manifest contains a null projection" }.toModel()
        },
        status = requireNotNull(status) { "persisted language manifest requires a status" },
        diagnostics = requireNotNull(diagnostics) { "persisted language manifest requires diagnostics" }.map { item ->
            requireNotNull(item) { "persisted language manifest contains a null diagnostic" }
        },
    )

    companion object {
        fun from(value: RomLanguageManifest) = StoredLanguageManifest(
            defaultLanguage = value.defaultLanguage?.value,
            projections = value.projections.map(StoredLanguageProjection::from),
            status = value.status,
            diagnostics = value.diagnostics,
        )
    }
}

private data class StoredLocalizedCapabilityState(
    val capability: LocalizedTextCapability? = null,
    val status: CapabilityStatus? = null,
    val confidence: Double? = null,
    val coveredRecords: Int? = null,
    val expectedRecords: Int? = null,
    val reviewStatus: CapabilityReviewStatus? = null,
    val validatorReviewRecommended: Boolean? = null,
    val reasons: List<String?>? = null,
) {
    fun toModel(): Pair<LocalizedTextCapability, LocalizedCapabilityState> {
        val key = requireNotNull(capability) { "persisted localized capability requires a key" }
        return key to LocalizedCapabilityState(
            status = requireNotNull(status) { "persisted localized capability requires a status" },
            confidence = requireNotNull(confidence) { "persisted localized capability requires confidence" },
            coveredRecords = requireNotNull(coveredRecords) {
                "persisted localized capability requires covered-record count"
            },
            expectedRecords = requireNotNull(expectedRecords) {
                "persisted localized capability requires expected-record count"
            },
            reviewStatus = requireNotNull(reviewStatus) {
                "persisted localized capability requires review status"
            },
            validatorReviewRecommended = requireNotNull(validatorReviewRecommended) {
                "persisted localized capability requires review recommendation"
            },
            reasons = requireNotNull(reasons) { "persisted localized capability requires reasons" }.map { reason ->
                requireNotNull(reason) { "persisted localized capability contains a null reason" }
            },
        )
    }

    companion object {
        fun from(
            capability: LocalizedTextCapability,
            value: LocalizedCapabilityState,
        ) = StoredLocalizedCapabilityState(
            capability = capability,
            status = value.status,
            confidence = value.confidence,
            coveredRecords = value.coveredRecords,
            expectedRecords = value.expectedRecords,
            reviewStatus = value.reviewStatus,
            validatorReviewRecommended = value.validatorReviewRecommended,
            reasons = value.reasons,
        )
    }
}

private data class StoredCatalogPoiText(
    val displayName: CatalogField<String>? = null,
    val displayNamesByTrainerGender: Map<Int, CatalogField<String>?>? = null,
    val itemDisplayName: CatalogField<String>? = null,
) {
    fun toModel() = CatalogPoiText(
        displayName = displayName,
        displayNamesByTrainerGender = requireNotNull(displayNamesByTrainerGender) {
            "persisted localized POI text requires trainer-gender names"
        }.mapValues { (_, value) ->
            requireNotNull(value) { "persisted localized POI text contains a null trainer-gender name" }
        },
        itemDisplayName = itemDisplayName,
    )

    companion object {
        fun from(value: CatalogPoiText) = StoredCatalogPoiText(
            displayName = value.displayName,
            displayNamesByTrainerGender = value.displayNamesByTrainerGender,
            itemDisplayName = value.itemDisplayName,
        )
    }
}

private data class StoredWorldLocationText(
    val regionKey: String? = null,
    val locationKey: String? = null,
    val value: CatalogField<String>? = null,
) {
    fun toModel() = WorldLocationKey(
        regionKey = requireNotNull(regionKey) { "persisted world-location text requires a region key" },
        locationKey = requireNotNull(locationKey) { "persisted world-location text requires a location key" },
    ) to requireNotNull(value) { "persisted world-location text requires a value" }

    companion object {
        fun from(key: WorldLocationKey, value: CatalogField<String>) = StoredWorldLocationText(
            regionKey = key.regionKey,
            locationKey = key.locationKey,
            value = value,
        )
    }
}

private data class StoredCatalogLanguageOverlay(
    val language: String? = null,
    val overlayVersion: Long? = null,
    val localizedCapabilities: List<StoredLocalizedCapabilityState?>? = null,
    val speciesNames: Map<Int, CatalogField<String>?>? = null,
    val speciesDescriptions: Map<Int, CatalogField<String>?>? = null,
    val moveNames: Map<Int, CatalogField<String>?>? = null,
    val moveDescriptions: Map<Int, CatalogField<String>?>? = null,
    val abilityNames: Map<Int, CatalogField<String>?>? = null,
    val abilityDescriptions: Map<Int, CatalogField<String>?>? = null,
    val typeNames: Map<Int, CatalogField<String>?>? = null,
    val natureNames: Map<Int, CatalogField<String>?>? = null,
    val itemNames: Map<Int, CatalogField<String>?>? = null,
    val areaNames: Map<Int, CatalogField<String>?>? = null,
    val localMapNames: Map<String, CatalogField<String>?>? = null,
    val worldRegionNames: Map<String, CatalogField<String>?>? = null,
    val worldLocationNames: List<StoredWorldLocationText?>? = null,
    val encounterAreaNames: Map<Int, CatalogField<String>?>? = null,
    val poiTexts: Map<String, StoredCatalogPoiText?>? = null,
) {
    fun toModel(expectedLanguage: LanguageTag): CatalogLanguageOverlay {
        val storedLanguage = LanguageTag.of(
            requireNotNull(language) { "persisted language overlay requires a language" },
        )
        require(storedLanguage == expectedLanguage) {
            "persisted language overlay does not match its section language"
        }
        val storedCapabilities = requireNotNull(localizedCapabilities) {
            "persisted language overlay requires localized capabilities"
        }.map { item ->
            requireNotNull(item) { "persisted language overlay contains a null capability" }.toModel()
        }
        require(storedCapabilities.map { it.first }.distinct().size == storedCapabilities.size) { "persisted language overlay contains duplicate capabilities" }
        val storedLocations = requireNotNull(worldLocationNames) {
            "persisted language overlay requires world-location names"
        }.map { item ->
            requireNotNull(item) { "persisted language overlay contains a null world location" }.toModel()
        }
        require(storedLocations.map { it.first }.distinct().size == storedLocations.size) { "persisted language overlay contains duplicate world locations" }
        return CatalogLanguageOverlay(
            language = storedLanguage,
            overlayVersion = requireNotNull(overlayVersion) {
                "persisted language overlay requires a version"
            },
            localizedCapabilities = storedCapabilities.toMap(linkedMapOf()),
            speciesNames = speciesNames.requiredTextMap("species names"),
            speciesDescriptions = speciesDescriptions.requiredTextMap("species descriptions"),
            moveNames = moveNames.requiredTextMap("move names"),
            moveDescriptions = moveDescriptions.requiredTextMap("move descriptions"),
            abilityNames = abilityNames.requiredTextMap("ability names"),
            abilityDescriptions = abilityDescriptions.requiredTextMap("ability descriptions"),
            typeNames = typeNames.requiredTextMap("type names"),
            natureNames = natureNames.requiredTextMap("nature names"),
            itemNames = itemNames.requiredTextMap("item names"),
            areaNames = areaNames.requiredTextMap("area names"),
            localMapNames = localMapNames.requiredTextMap("local-map names"),
            worldRegionNames = worldRegionNames.requiredTextMap("world-region names"),
            worldLocationNames = storedLocations.toMap(linkedMapOf()),
            encounterAreaNames = encounterAreaNames.requiredTextMap("encounter-area names"),
            poiTexts = requireNotNull(poiTexts) { "persisted language overlay requires POI text" }
                .mapValues { (_, value) ->
                    requireNotNull(value) { "persisted language overlay contains null POI text" }.toModel()
                },
        )
    }

    companion object {
        fun from(value: CatalogLanguageOverlay) = StoredCatalogLanguageOverlay(
            language = value.language.value,
            overlayVersion = value.overlayVersion,
            localizedCapabilities = value.localizedCapabilities.map { (capability, state) ->
                StoredLocalizedCapabilityState.from(capability, state)
            },
            speciesNames = value.speciesNames,
            speciesDescriptions = value.speciesDescriptions,
            moveNames = value.moveNames,
            moveDescriptions = value.moveDescriptions,
            abilityNames = value.abilityNames,
            abilityDescriptions = value.abilityDescriptions,
            typeNames = value.typeNames,
            natureNames = value.natureNames,
            itemNames = value.itemNames,
            areaNames = value.areaNames,
            localMapNames = value.localMapNames,
            worldRegionNames = value.worldRegionNames,
            worldLocationNames = value.worldLocationNames.map { (key, text) ->
                StoredWorldLocationText.from(key, text)
            },
            encounterAreaNames = value.encounterAreaNames,
            poiTexts = value.poiTexts.mapValues { (_, text) -> StoredCatalogPoiText.from(text) },
        )
    }
}

private fun <K> Map<K, CatalogField<String>?>?.requiredTextMap(label: String): Map<K, CatalogField<String>> =
    requireNotNull(this) { "persisted language overlay requires $label" }.mapValues { (_, value) ->
        requireNotNull(value) { "persisted language overlay contains a null $label value" }
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
    private val languageManifestType = type<StoredLanguageManifest>()
    private val languageOverlayType = type<StoredCatalogLanguageOverlay>()

    fun encode(catalog: ParsedCatalog, included: Set<String>): Map<String, ByteArray> =
        included.associateWithTo(linkedMapOf()) { name -> encodeSection(catalog, name) }

    fun encodeSection(catalog: ParsedCatalog, name: String): ByteArray =
        ByteArrayOutputStream().use { output ->
            writeSection(catalog, name, output)
            output.toByteArray()
        }

    fun writeSection(
        catalog: ParsedCatalog,
        name: String,
        output: OutputStream,
        maximumInflatedBytes: Int = if (CatalogSectionPlan.parseOverlaySectionName(name) != null) {
            CatalogSchema.maximumLanguageOverlayInflatedBytes
        } else {
            CatalogSchema.maximumSectionInflatedBytes
        },
        onInflatedBytes: (Int) -> Unit = {},
    ) {
        fun write(value: Any, type: Type) = encode(
            value,
            type,
            output,
            name,
            maximumInflatedBytes,
            onInflatedBytes,
        )
        when (name) {
            "species" -> write(catalog.speciesById, speciesType)
            "moves" -> write(catalog.movesById, movesType)
            "types" -> write(catalog.typesById, typesType)
            "abilities" -> write(catalog.abilitiesById, abilitiesType)
            "natures" -> write(catalog.naturesById, naturesType)
            "type_chart" -> write(catalog.typeChart, chartType)
            "encounters" -> write(catalog.encounterAreas, encountersType)
            "capture_balls" -> write(catalog.captureBallsById, ballsType)
            "learnset_rulesets" -> write(catalog.learnsetRulesets, rulesetsType)
            "runtime_metadata" -> write(catalog.runtimeMetadata, runtimeMetadataType)
            "world_maps" -> write(catalog.worldMaps, worldMapsType)
            "trainer_assets" -> write(catalog.trainerAssets, trainerAssetsType)
            "local_maps" -> write(catalog.localMaps, localMapsType)
            "theme" -> write(catalog.theme, themeType)
            "capabilities" -> write(catalog.capabilities, capabilitiesType)
            "diagnostics" -> write(catalog.diagnostics, diagnosticsType)
            "language_manifest" -> write(StoredLanguageManifest.from(catalog.languageManifest), languageManifestType)
            else -> {
                val language = CatalogSectionPlan.parseOverlaySectionName(name)
                    ?: error("unknown catalog section: $name")
                val overlay = requireNotNull(catalog.localizedText(language)) {
                    "catalog section has no matching language overlay: $name"
                }
                write(StoredCatalogLanguageOverlay.from(overlay), languageOverlayType)
            }
        }
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
    ): ParsedCatalog = decode(
        sha256,
        crc32,
        family,
        platform,
        decodeLanguageManifest(section),
        section,
    )

    fun decodeLanguageManifest(section: (String, Type) -> Any): RomLanguageManifest =
        (section("language_manifest", languageManifestType) as StoredLanguageManifest).toModel()

    fun decode(
        sha256: String,
        crc32: String,
        family: EngineFamily,
        platform: Platform,
        manifest: RomLanguageManifest,
        section: (String, Type) -> Any,
    ): ParsedCatalog {
        @Suppress("UNCHECKED_CAST")
        fun <T> decoded(name: String, type: Type): T = section(name, type) as T

        val plan = CatalogSectionPlan.from(manifest)
        val overlays = plan.overlaysBySection.mapValuesTo(linkedMapOf()) { (sectionName, language) ->
            decoded<StoredCatalogLanguageOverlay>(sectionName, languageOverlayType).toModel(language)
        }.entries.associateTo(linkedMapOf()) { (_, overlay) -> overlay.language to overlay }
        val localization = CatalogLocalization(manifest, overlays)
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
            localization = localization,
        )
    }

    private fun encode(
        value: Any,
        type: Type,
        output: OutputStream,
        sectionName: String,
        maximumInflatedBytes: Int,
        onInflatedBytes: (Int) -> Unit,
    ) {
        GZIPOutputStream(output).use { gzip ->
            val bounded = CatalogInflatedOutputStream(
                gzip,
                sectionName,
                maximumInflatedBytes,
                onInflatedBytes,
            )
            OutputStreamWriter(bounded, Charsets.UTF_8).use { writer -> gson.toJson(value, type, writer) }
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

internal class CatalogInflatedOutputStream(
    output: OutputStream,
    private val sectionName: String,
    private val maximumBytes: Int,
    private val onBytes: (Int) -> Unit = {},
) : FilterOutputStream(output) {
    private var writtenBytes = 0L

    init {
        require(maximumBytes > 0) { "catalog inflate limit must be positive" }
    }

    override fun write(value: Int) {
        claim(1)
        out.write(value)
    }

    override fun write(source: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= source.size - length) {
            "catalog inflate source range is invalid"
        }
        claim(length)
        out.write(source, offset, length)
    }

    private fun claim(bytes: Int) {
        require(writtenBytes <= maximumBytes.toLong() - bytes) {
            "catalog section inflate limit exceeded: $sectionName"
        }
        onBytes(bytes)
        writtenBytes += bytes
    }
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
