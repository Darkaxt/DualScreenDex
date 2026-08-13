package com.enrpau.dualscreendex.parser.cli

import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

/** Evidence-only real-corpus runner. No ROM identity or outcome from this class enters production parsing. */
object MapFirst50Matrix {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    @JvmStatic
    fun main(args: Array<String>) {
        val manifestPath = requiredPath("DUALDEX_FIRST50_MANIFEST")
        val outputPath = requiredOutputPath("DUALDEX_MAP_MATRIX_OUTPUT")
        val baselinePath = Path.of(System.getenv("DUALDEX_FIRST33_BASELINE") ?: "reports/dualdex-parser-compatibility.json")
        val start = (System.getenv("DUALDEX_MAP_MATRIX_START") ?: "1").toInt()
        val end = (System.getenv("DUALDEX_MAP_MATRIX_END") ?: "50").toInt()
        require(start in 1..50 && end in start..50) { "matrix range must remain within 1..50" }

        val manifest = JsonParser.parseString(Files.readString(manifestPath)).asJsonArray
        require(manifest.size() >= 50) { "first50 manifest contained only ${manifest.size()} entries" }
        val baselineBySha = baselineBySha(baselinePath)
        val observations = mutableListOf<RomObservation>()
        val report = MatrixReport(
            generatedAt = Instant.now().toString(),
            sourceCommit = System.getenv("DUALDEX_MAP_MATRIX_COMMIT") ?: "unknown",
            manifestSha256 = sha256(Files.readAllBytes(manifestPath)),
            range = listOf(start, end),
            runCountPerRom = 2,
            controls = frozenControls,
            observations = observations,
        )

        for (index in start..end) {
            val item = manifest[index - 1].asJsonObject
            val path = Path.of(item.get("ExtractedPath").asString)
            val manifestSha = item.get("RomSha256").asString.lowercase()
            val bytes = Files.readAllBytes(path)
            val rom = RomImage(bytes)
            val header = RomHeaderReader.read(rom)
            val run1 = run(path)
            val run2 = run(path)
            val baseline = baselineBySha[rom.sha256]
            val observation = RomObservation(
                index = index,
                name = item.get("EntryPath").asString,
                bytes = bytes.size.toLong(),
                manifestSha256 = manifestSha,
                observedSha256 = rom.sha256,
                shaMatchesManifest = rom.sha256 == manifestSha,
                header = HeaderObservation(
                    platform = header.platform.name,
                    generation = headerGeneration(header.platform),
                    title = header.title,
                    gameCode = header.gameCode,
                    revision = header.revision,
                ),
                parserGeneration = run1.generation,
                parserSelectedFamily = run1.selectedFamily,
                run1 = run1,
                run2 = run2,
                deterministic = run1.deterministicProjection() == run2.deterministicProjection(),
                first33Routing = if (index <= 33) {
                    RoutingObservation(
                        baselineStatus = baseline?.status,
                        baselineFamily = baseline?.family,
                        currentStatus = run1.selectionStatus,
                        currentFamily = run1.selectedFamily,
                        routingPreserved = baseline != null && baseline.status == run1.selectionStatus && baseline.family == run1.selectedFamily,
                        baselineReferenceSignature = baseline?.references.orEmpty(),
                        currentReferenceSignature = run1.referenceSignature,
                        referencesPreserved = baseline != null && preservesReferences(baseline.references, run1.referenceSignature),
                        referencesExactlyEqual = baseline != null && baseline.references == run1.referenceSignature,
                    )
                } else null,
            )
            observations += observation
            writeReport(outputPath, report)
            println(
                "%03d/050 %-21s %-9s regions=%d stage=%s deterministic=%s".format(
                    index,
                    run1.selectedFamily ?: run1.selectionStatus,
                    run1.mapCapabilityStatus ?: "NO_CATALOG",
                    run1.regions.size,
                    run1.earliestStage,
                    observation.deterministic,
                ),
            )
        }

        val summary = MatrixSummary(
            completed = observations.size,
            shaVerified = observations.count { it.shaMatchesManifest },
            deterministic = observations.count { it.deterministic },
            first33RoutingPreserved = observations.count { it.first33Routing?.routingPreserved == true },
            first33ReferencesPreserved = observations.count { it.first33Routing?.referencesPreserved == true },
            first33ReferencesExactlyEqual = observations.count { it.first33Routing?.referencesExactlyEqual == true },
            first33RoutingChecked = observations.count { it.first33Routing != null },
            available = observations.count { it.run1.mapCapabilityStatus == CapabilityStatus.AVAILABLE.name },
            notApplicable = observations.count { it.run1.mapCapabilityStatus == CapabilityStatus.NOT_APPLICABLE.name },
            safeNoMapFallback = observations.count { it.run1.safeNoMapFallback },
            clusters = observations.groupingBy { it.run1.earliestStage }.eachCount().toSortedMap(),
        )
        writeReport(outputPath, report.copy(summary = summary))
        println("matrix summary: ${gson.toJson(summary)}")
    }

    private fun run(path: Path): RunObservation {
        val started = System.nanoTime()
        return try {
            val result = CatalogParser.parse(RomImage(Files.readAllBytes(path)))
            val catalog = result.catalog
            val mapCapability = catalog?.capabilities?.get(RomCapability.WORLD_MAP)
            val reasons = mapCapability?.reasons.orEmpty()
            val regions = catalog?.worldMaps?.regions.orEmpty().map { region ->
                val raster = requireNotNull(catalog?.worldMaps?.assets?.get(region.imageAssetKey))
                RegionObservation(
                    key = region.key,
                    displayName = region.displayName,
                    pixelWidth = region.pixelWidth,
                    pixelHeight = region.pixelHeight,
                    gridWidth = region.gridWidth,
                    gridHeight = region.gridHeight,
                    rasterArgbSha256 = sha256(raster),
                    locations = region.locations.map { location ->
                        LocationObservation(
                            key = location.key,
                            displayName = location.displayName,
                            baseAreaIds = location.baseAreaIds.sorted(),
                            geometry = location.geometry.map { listOf(it.x, it.y, it.width, it.height) },
                        )
                    },
                )
            }
            val status = mapCapability?.status?.name
            RunObservation(
                durationMillis = elapsedMillis(started),
                selectionStatus = result.analysis.status.name,
                selectedFamily = result.analysis.selectedFamily?.name,
                selectedProfile = result.analysis.selectedProfile,
                generation = result.layout?.generation,
                catalogMaterialized = catalog != null,
                mapCapabilityStatus = status,
                reasons = reasons,
                earliestStage = earliestStage(result.analysis.status.name, result.layout?.generation, catalog != null, status, reasons),
                ambiguous = status == CapabilityStatus.AMBIGUOUS.name || reasons.any { "ambigu" in it.lowercase() },
                budget = reasons.any { "budget" in it.lowercase() || "limit" in it.lowercase() },
                error = null,
                regions = regions,
                safeNoMapFallback = status != CapabilityStatus.AVAILABLE.name && regions.isEmpty(),
                referenceSignature = (catalog?.capabilities?.values ?: result.analysis.capabilities)
                    .filter { it.capability != RomCapability.WORLD_MAP }
                    .map(::reference)
                    .sortedBy(ReferenceObservation::capability),
            )
        } catch (failure: Throwable) {
            RunObservation(
                durationMillis = elapsedMillis(started),
                selectionStatus = "ERROR",
                selectedFamily = null,
                selectedProfile = null,
                generation = null,
                catalogMaterialized = false,
                mapCapabilityStatus = null,
                reasons = emptyList(),
                earliestStage = "ERROR",
                ambiguous = false,
                budget = false,
                error = "${failure::class.qualifiedName}: ${failure.message}",
                regions = emptyList(),
                safeNoMapFallback = true,
                referenceSignature = emptyList(),
            )
        }
    }

    private fun earliestStage(
        selectionStatus: String,
        generation: Int?,
        catalogMaterialized: Boolean,
        mapStatus: String?,
        reasons: List<String>,
    ): String {
        if (selectionStatus != "SELECTED") return "FAMILY_SELECTION_$selectionStatus"
        if (!catalogMaterialized) return "CATALOG_MATERIALIZATION"
        if (generation !in 1..3 || mapStatus == CapabilityStatus.NOT_APPLICABLE.name) return "NOT_APPLICABLE"
        if (mapStatus == CapabilityStatus.AVAILABLE.name) return "RESOLVED"
        val reason = reasons.joinToString(" ").lowercase()
        return when {
            "budget" in reason || "limit" in reason -> "REFERENCE_BUDGET"
            "world-map stage: landmark-join" in reason -> "LANDMARK_JOIN"
            "world-map stage: entry-table" in reason -> "ENTRY_TABLE"
            "world-map stage: map-header-join" in reason -> "SEMANTIC_REGION_JOIN"
            "world-map stage: encounter-binding" in reason -> "LOCATION_BINDING"
            "world-map stage: map-plane" in reason -> "SEMANTIC_REGION_PLANES"
            "world-map stage: palette" in reason -> "PALETTE"
            "world-map stage: asset-loader" in reason && mapStatus == CapabilityStatus.AMBIGUOUS.name -> "LOADER_AUTHORITY"
            "world-map stage: asset-loader" in reason -> "LOADER_ASSET_CLUSTER"
            "compiled gba references are unavailable" in reason -> "REFERENCE_INDEX"
            "equally authoritative" in reason || "multiple proven world-map loader formats" in reason -> "LOADER_AUTHORITY"
            "tile, tilemap, and bgr555 palette cluster" in reason -> "LOADER_ASSET_CLUSTER"
            "loader cluster exposed" in reason -> "REGION_COUNT"
            "semantic text-map section planes" in reason -> "SEMANTIC_REGION_PLANES"
            "encounter map headers" in reason || "region entries did not resolve" in reason -> "SEMANTIC_REGION_JOIN"
            "encounter binding" in reason -> "LOCATION_BINDING"
            mapStatus == CapabilityStatus.AMBIGUOUS.name -> "AMBIGUITY"
            else -> "UNAVAILABLE_OTHER"
        }
    }

    private fun baselineBySha(path: Path): Map<String, BaselineRouting> {
        if (!Files.isRegularFile(path)) return emptyMap()
        val root = JsonParser.parseString(Files.readString(path)).asJsonObject
        return root.getAsJsonArray("results").associate { row ->
            val result = row.asJsonObject.getAsJsonObject("result")
            result.get("sha256").asString to BaselineRouting(
                status = result.get("status").asString,
                family = result.get("selectedFamily")?.takeUnless { it.isJsonNull }?.asString,
                references = result.getAsJsonArray("capabilities").map { capability ->
                    val item = capability.asJsonObject
                    ReferenceObservation(
                        capability = item.get("capability").asString,
                        status = item.get("status").asString,
                        offset = item.get("offset")?.takeUnless { it.isJsonNull }?.asInt,
                        count = item.get("count")?.takeUnless { it.isJsonNull }?.asInt,
                        recordSize = item.get("recordSize")?.takeUnless { it.isJsonNull }?.asInt,
                        elementSize = item.get("elementSize")?.takeUnless { it.isJsonNull }?.asInt,
                    )
                }.sortedBy(ReferenceObservation::capability),
            )
        }
    }

    private fun reference(evidence: com.enrpau.dualscreendex.parser.model.CapabilityEvidence) = ReferenceObservation(
        capability = evidence.capability.name,
        status = evidence.status.name,
        offset = evidence.offset,
        count = evidence.count,
        recordSize = evidence.recordSize,
        elementSize = evidence.elementSize,
    )

    private fun preservesReferences(baseline: List<ReferenceObservation>, current: List<ReferenceObservation>): Boolean {
        val currentByCapability = current.associateBy(ReferenceObservation::capability)
        return baseline.all { expected ->
            val actual = currentByCapability[expected.capability] ?: return@all false
            when (expected.capability) {
                RomCapability.AREA_ENCOUNTERS.name ->
                    capabilityRank(actual.status) >= capabilityRank(expected.status) &&
                        (expected.offset == null || actual.offset == expected.offset) &&
                        (expected.count == null || actual.count != null && actual.count >= expected.count) &&
                        (expected.recordSize == null || actual.recordSize == expected.recordSize) &&
                        (expected.elementSize == null || actual.elementSize == expected.elementSize)
                else -> actual == expected
            }
        }
    }

    private fun capabilityRank(status: String): Int = when (status) {
        CapabilityStatus.AVAILABLE.name -> 4
        CapabilityStatus.PARTIAL.name -> 3
        CapabilityStatus.AMBIGUOUS.name -> 2
        CapabilityStatus.NOT_FOUND.name -> 1
        CapabilityStatus.NOT_APPLICABLE.name -> 0
        else -> -1
    }

    private fun headerGeneration(platform: Platform): Int? = when (platform) {
        Platform.GB -> 1
        Platform.GBC -> 2
        Platform.GBA -> 3
        Platform.UNKNOWN -> null
    }

    private fun RunObservation.deterministicProjection() = copy(durationMillis = 0)

    private fun writeReport(path: Path, report: MatrixReport) {
        path.parent?.let(Files::createDirectories)
        Files.writeString(path, gson.toJson(report) + System.lineSeparator())
    }

    private fun requiredPath(name: String): Path {
        val value = requireNotNull(System.getenv(name)) { "set $name" }
        return Path.of(value).also { require(Files.isRegularFile(it)) { "$name is not a file: $it" } }
    }

    private fun requiredOutputPath(name: String): Path {
        val value = requireNotNull(System.getenv(name)) { "set $name" }
        return Path.of(value)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun sha256(sprite: RgbaSprite): String {
        val digest = MessageDigest.getInstance("SHA-256")
        sprite.argb.forEach { digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(it).array()) }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun elapsedMillis(started: Long) = (System.nanoTime() - started) / 1_000_000

    private val frozenControls = listOf(
        Control("official-emerald", "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af", 1, listOf("1c3a1bf13c851dcc707f1f3f71c8f90e703a0faf0832917a0195618952a77aab")),
        Control("modern-emerald", "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895", 1, listOf("0163d9b5e747d788db925776c25a087a1cc4bbfa34fd3e021580aa8756717fb0")),
        Control("classic", "01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c", 1, listOf("dc326776034d066f0b2691e14f2325e78d6761b40db6da52c8454ab8fe46a46f")),
        Control("official-fire-red", "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059", 4, listOf("250195a226d642147bb594e30cb03596ef94dd88237204f761fb164286d53654", "8e1d6f588bf4bd24913a559e70f6af8f42c32d484f523ee197a09b73c03b4135", "eebdbb58c4d7fbbc875d6fbc465751625c26baf2a2c728c06fa8331d92fd7e4a", "b96065661b1848860cc69db7e9370194df740568e4352d7288e2b4ee17640a3b")),
        Control("official-leaf-green", "2f978f635b9593f6ca26ec42481c53a6b39f6cddd894ad5c062c1419fac58825", 4, listOf("250195a226d642147bb594e30cb03596ef94dd88237204f761fb164286d53654", "8e1d6f588bf4bd24913a559e70f6af8f42c32d484f523ee197a09b73c03b4135", "eebdbb58c4d7fbbc875d6fbc465751625c26baf2a2c728c06fa8331d92fd7e4a", "b96065661b1848860cc69db7e9370194df740568e4352d7288e2b4ee17640a3b")),
    )
}

private data class BaselineRouting(val status: String, val family: String?, val references: List<ReferenceObservation>)
private data class MatrixReport(
    val generatedAt: String,
    val sourceCommit: String,
    val manifestSha256: String,
    val range: List<Int>,
    val runCountPerRom: Int,
    val controls: List<Control>,
    val observations: List<RomObservation>,
    val summary: MatrixSummary? = null,
)
private data class MatrixSummary(
    val completed: Int,
    val shaVerified: Int,
    val deterministic: Int,
    val first33RoutingPreserved: Int,
    val first33ReferencesPreserved: Int,
    val first33ReferencesExactlyEqual: Int,
    val first33RoutingChecked: Int,
    val available: Int,
    val notApplicable: Int,
    val safeNoMapFallback: Int,
    val clusters: Map<String, Int>,
)
private data class RomObservation(
    val index: Int,
    val name: String,
    val bytes: Long,
    val manifestSha256: String,
    val observedSha256: String,
    val shaMatchesManifest: Boolean,
    val header: HeaderObservation,
    val parserGeneration: Int?,
    val parserSelectedFamily: String?,
    val run1: RunObservation,
    val run2: RunObservation,
    val deterministic: Boolean,
    val first33Routing: RoutingObservation?,
)
private data class HeaderObservation(val platform: String, val generation: Int?, val title: String, val gameCode: String?, val revision: Int)
private data class RoutingObservation(
    val baselineStatus: String?,
    val baselineFamily: String?,
    val currentStatus: String,
    val currentFamily: String?,
    val routingPreserved: Boolean,
    val baselineReferenceSignature: List<ReferenceObservation>,
    val currentReferenceSignature: List<ReferenceObservation>,
    val referencesPreserved: Boolean,
    val referencesExactlyEqual: Boolean,
)
private data class RunObservation(
    val durationMillis: Long,
    val selectionStatus: String,
    val selectedFamily: String?,
    val selectedProfile: String?,
    val generation: Int?,
    val catalogMaterialized: Boolean,
    val mapCapabilityStatus: String?,
    val reasons: List<String>,
    val earliestStage: String,
    val ambiguous: Boolean,
    val budget: Boolean,
    val error: String?,
    val regions: List<RegionObservation>,
    val safeNoMapFallback: Boolean,
    val referenceSignature: List<ReferenceObservation>,
)
private data class ReferenceObservation(val capability: String, val status: String, val offset: Int?, val count: Int?, val recordSize: Int?, val elementSize: Int?)
private data class RegionObservation(
    val key: String,
    val displayName: String?,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val gridWidth: Int,
    val gridHeight: Int,
    val rasterArgbSha256: String,
    val locations: List<LocationObservation>,
)
private data class LocationObservation(val key: String, val displayName: String, val baseAreaIds: List<Int>, val geometry: List<List<Int>>)
private data class Control(val identity: String, val romSha256: String, val regionCount: Int, val rasterArgbSha256: List<String>)
