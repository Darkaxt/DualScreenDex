package com.enrpau.dualscreendex.parser.cli

import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.IndexedMapAsset
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.MapLighting
import com.enrpau.dualscreendex.parser.catalog.TimedIndexedMapAsset
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

/** Evidence-only private-input runner. No corpus identity or outcome enters production parsing. */
object GbGbcLocalMapMatrix {
    private const val MAX_DIAGNOSTICS = 8
    private const val MAX_DIAGNOSTIC_LENGTH = 240
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    @JvmStatic
    fun main(args: Array<String>) {
        val manifestPath = requiredInput("DUALDEX_GB_GBC_MANIFEST")
        val outputPath = requiredOutput("DUALDEX_GB_GBC_MAP_OUTPUT")
        val baseline = System.getenv("DUALDEX_GB_GBC_BASELINE")
            ?.let(Path::of)
            ?.takeIf(Files::isRegularFile)
            ?.let(::baselineByManifestIndex)
            .orEmpty()
        val manifest = JsonParser.parseString(Files.readString(manifestPath)).asJsonArray
        val candidates = mutableListOf<Candidate>()
        var verifiedHashes = 0

        manifest.forEachIndexed { offset, element ->
            val index = offset + 1
            val item = element.asJsonObject
            val path = Path.of(item.get("ExtractedPath").asString)
            require(Files.isRegularFile(path)) { "manifest index $index is not readable" }
            val manifestSha256 = item.get("RomSha256").asString.lowercase()
            val observedSha256 = sha256(path)
            require(observedSha256 == manifestSha256) {
                "manifest index $index hash mismatch: expected $manifestSha256, observed $observedSha256"
            }
            verifiedHashes++
            val bytes = Files.readAllBytes(path)
            val platform = runCatching { RomHeaderReader.read(RomImage(bytes)).platform }.getOrNull()
            if (platform == Platform.GB || platform == Platform.GBC) {
                candidates += Candidate(index, path, observedSha256)
            }
        }

        val rows = mutableListOf<GbGbcMatrixRow>()
        var parserErrors = 0
        candidates.forEachIndexed { candidateOffset, candidate ->
            val first = run(candidate.path)
            if (first.errorType != null) parserErrors++
            if (first.selectionStatus == "SELECTED" && first.generation in 1..2) {
                val second = run(candidate.path)
                if (second.errorType != null) parserErrors++
                val baselineProjection = baseline[candidate.index]
                val strictControl = strictControls.singleOrNull { it.sha256 == candidate.sha256 }
                rows += GbGbcMatrixRow(
                    manifestIndex = candidate.index,
                    sha256 = candidate.sha256,
                    generation = first.generation!!,
                    family = requireNotNull(first.family),
                    localCapability = first.localCapability,
                    mapCount = first.mapCount,
                    pngAssetCount = first.pngAssetCount,
                    indexedAssetCount = first.indexedAssetCount,
                    timedAssetCount = first.timedAssetCount,
                    sceneCount = first.sceneCount,
                    rasterSignature = first.rasterSignature,
                    sceneSignature = first.sceneSignature,
                    diagnostics = first.diagnostics,
                    deterministic = first.deterministicProjection() == second.deterministicProjection(),
                    baselinePreserved = baselineProjection?.let { expected ->
                        candidate.sha256 == expected.sha256 &&
                            first.generation == expected.generation &&
                            first.family == expected.family &&
                            first.localCapability == expected.localCapability &&
                            first.mapCount == expected.mapCount &&
                            first.pngAssetCount == expected.pngAssetCount &&
                            first.indexedAssetCount == expected.indexedAssetCount &&
                            first.timedAssetCount == expected.timedAssetCount &&
                            first.rasterSignature == expected.rasterSignature &&
                            first.errorType == expected.errorType
                    },
                    errorType = first.errorType ?: second.errorType,
                    strictConnectionVerified = strictControl?.connection?.let(first.sceneRelations::contains),
                )
                writeReport(
                    outputPath,
                    report(manifest.size(), verifiedHashes, candidates.size, rows, parserErrors, baseline),
                )
            }
            println(
                "GB/GBC candidate %03d/%03d selected=%d errors=%d".format(
                    candidateOffset + 1,
                    candidates.size,
                    rows.size,
                    parserErrors,
                ),
            )
        }

        val finalReport = report(
            manifestCount = manifest.size(),
            verifiedHashes = verifiedHashes,
            candidateCount = candidates.size,
            rows = rows,
            parserErrors = parserErrors,
            baseline = baseline,
        )
        writeReport(outputPath, finalReport)
        check(finalReport.summary.shaVerified == finalReport.summary.manifestCount) { "not every manifest hash was verified" }
        check(finalReport.summary.parserErrors == 0) { "GB/GBC parser errors: ${finalReport.summary.parserErrors}" }
        check(finalReport.summary.deterministic == finalReport.summary.selected) { "GB/GBC output was not deterministic" }
        check(finalReport.summary.baselineRegressions == 0) {
            "GB/GBC accepted Local raster regressions: ${finalReport.summary.baselineRegressions}"
        }
        check(finalReport.summary.strictControlFailures == 0) {
            "GB/GBC strict control failures: ${finalReport.summary.strictControlFailures}"
        }
        println("GB/GBC Local-map summary: ${gson.toJson(finalReport.summary)}")
    }

    private fun report(
        manifestCount: Int,
        verifiedHashes: Int,
        candidateCount: Int,
        rows: List<GbGbcMatrixRow>,
        parserErrors: Int,
        baseline: Map<Int, BaselineProjection>,
    ): GbGbcMatrixReport {
        val strictFailures = strictControls.count { expected ->
            val row = rows.singleOrNull { it.sha256 == expected.sha256 }
            row == null ||
                row.generation != expected.generation ||
                row.family != expected.family ||
                row.mapCount != expected.mapCount ||
                row.sceneSignature != expected.sceneSignature ||
                row.strictConnectionVerified != true
        }
        val baselineRegressions = rows.count { it.baselinePreserved == false } +
            baseline.keys.count { expectedIndex -> rows.none { it.manifestIndex == expectedIndex } }
        return GbGbcMatrixReport(
            generatedAt = Instant.now().toString(),
            sourceCommit = System.getenv("DUALDEX_GB_GBC_COMMIT") ?: "unknown",
            rows = rows.toList(),
            summary = GbGbcMatrixSummary(
                manifestCount = manifestCount,
                shaVerified = verifiedHashes,
                gbGbcCandidates = candidateCount,
                selected = rows.size,
                deterministic = rows.count(GbGbcMatrixRow::deterministic),
                parserErrors = parserErrors,
                localAvailable = rows.count { it.localCapability == "AVAILABLE" },
                maps = rows.sumOf(GbGbcMatrixRow::mapCount),
                assets = rows.sumOf { it.pngAssetCount + it.indexedAssetCount + it.timedAssetCount },
                scenes = rows.sumOf(GbGbcMatrixRow::sceneCount),
                baselineCompared = if (baseline.isNotEmpty()) rows.count { it.baselinePreserved != null } else 0,
                baselineRegressions = baselineRegressions,
                strictControls = strictControls.size,
                strictControlFailures = strictFailures,
            ),
        )
    }

    private fun run(path: Path): RunProjection {
        val attempt = runCatching {
            CatalogParser.parseCatching(RomImage(Files.readAllBytes(path)))
        }.getOrElse { failure ->
            return RunProjection(errorType = failure::class.simpleName ?: "Throwable")
        }
        val failure = attempt.catalog?.exceptionOrNull()
        val catalog = attempt.catalog?.getOrNull()
        val localMaps = catalog?.localMaps ?: LocalMapCatalog()
        val localCapability = catalog?.capabilities?.get(RomCapability.LOCAL_MAP)
        return RunProjection(
            selectionStatus = attempt.analysis.status.name,
            generation = attempt.layout?.generation,
            family = attempt.analysis.selectedFamily?.name,
            localCapability = localCapability?.status?.name,
            mapCount = localMaps.maps.size,
            pngAssetCount = localMaps.assets.size,
            indexedAssetCount = localMaps.indexedAssets.size,
            timedAssetCount = localMaps.timedAssets.size,
            sceneCount = localMaps.scenes.size,
            rasterSignature = rasterSignature(localMaps),
            sceneSignature = sceneSignature(localMaps),
            sceneRelations = sceneRelations(localMaps),
            diagnostics = localCapability?.reasons.orEmpty()
                .asSequence()
                .map(::boundedDiagnostic)
                .distinct()
                .take(MAX_DIAGNOSTICS)
                .toList(),
            errorType = failure?.let { it::class.simpleName ?: "Throwable" },
        )
    }

    private fun rasterSignature(localMaps: LocalMapCatalog): String {
        val digest = MessageDigest.getInstance("SHA-256")
        localMaps.maps.sortedBy { it.key }.forEach { map ->
            digest.text(
                "map|${map.key}|${map.baseAreaId}|${map.pixelWidth}|${map.pixelHeight}|" +
                    "${map.gridWidth}|${map.gridHeight}|${map.imageAssetKey}\n",
            )
        }
        localMaps.assets.toSortedMap().forEach { (key, asset) ->
            digest.text("png|$key|${asset.bytes.size}\n")
            digest.update(asset.bytes)
        }
        localMaps.indexedAssets.toSortedMap().forEach { (key, asset) ->
            digestIndexedAsset(digest, key, asset)
        }
        localMaps.timedAssets.toSortedMap().forEach { (key, asset) ->
            digestTimedAsset(digest, key, asset)
        }
        return digest.hex()
    }

    private fun digestIndexedAsset(digest: MessageDigest, key: String, asset: IndexedMapAsset) {
        digest.text("indexed|$key|${asset.pixelWidth}|${asset.pixelHeight}|${asset.lightingPolicy}\n")
        digest.update(asset.compressedIndices)
        MapLighting.entries.forEach { lighting -> digest.ints(asset.palettes[lighting]) }
    }

    private fun digestTimedAsset(digest: MessageDigest, key: String, asset: TimedIndexedMapAsset) {
        digest.text(
            "timed|$key|${asset.pixelWidth}|${asset.pixelHeight}|${asset.alternatePaletteMask}|${asset.paletteModel}\n",
        )
        digest.update(asset.compressedIndices)
        digest.ints(asset.baseColors)
        digest.ints(asset.alternateColors)
    }

    private fun sceneSignature(localMaps: LocalMapCatalog): String {
        val digest = MessageDigest.getInstance("SHA-256")
        localMaps.scenes.sortedBy { it.key }.forEach { scene ->
            digest.text("scene|${scene.key}|${scene.gridWidth}|${scene.gridHeight}\n")
            scene.placements.sortedBy { it.localMapKey }.forEach { placement ->
                digest.text(
                    "placement|${placement.localMapKey}|${placement.baseAreaId}|" +
                        "${placement.gridX}|${placement.gridY}\n",
                )
            }
        }
        return digest.hex()
    }

    private fun sceneRelations(localMaps: LocalMapCatalog): Set<SceneRelation> = buildSet {
        localMaps.scenes.forEach { scene ->
            scene.placements.forEach { source ->
                scene.placements.forEach { target ->
                    if (source.localMapKey != target.localMapKey) {
                        add(
                            SceneRelation(
                                sourceAreaId = source.baseAreaId,
                                targetAreaId = target.baseAreaId,
                                deltaX = target.gridX - source.gridX,
                                deltaY = target.gridY - source.gridY,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun baselineByManifestIndex(path: Path): Map<Int, BaselineProjection> {
        val root = JsonParser.parseString(Files.readString(path)).asJsonObject
        return root.getAsJsonArray("rows").associate { element ->
            val row = element.asJsonObject
            row.get("manifestIndex").asInt to BaselineProjection(
                sha256 = row.get("sha256").asString,
                generation = row.get("generation").asInt,
                family = row.get("family").asString,
                localCapability = row.get("localCapability")?.takeUnless { it.isJsonNull }?.asString,
                mapCount = row.get("mapCount").asInt,
                pngAssetCount = row.get("pngAssetCount").asInt,
                indexedAssetCount = row.get("indexedAssetCount").asInt,
                timedAssetCount = row.get("timedAssetCount").asInt,
                rasterSignature = row.get("rasterSignature").asString,
                errorType = row.get("errorType")?.takeUnless { it.isJsonNull }?.asString,
            )
        }
    }

    private fun boundedDiagnostic(value: String): String = value
        .replace('\r', ' ')
        .replace('\n', ' ')
        .take(MAX_DIAGNOSTIC_LENGTH)

    private fun writeReport(path: Path, report: GbGbcMatrixReport) {
        path.parent?.let(Files::createDirectories)
        Files.writeString(path, gson.toJson(report) + System.lineSeparator())
    }

    private fun requiredInput(name: String): Path {
        val value = requireNotNull(System.getenv(name)) { "set $name" }
        return Path.of(value).also { require(Files.isRegularFile(it)) { "$name is not a regular file" } }
    }

    private fun requiredOutput(name: String): Path {
        val value = requireNotNull(System.getenv(name)) { "set $name" }
        return Path.of(value)
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.hex()
    }

    private fun MessageDigest.text(value: String) = update(value.toByteArray(Charsets.UTF_8))

    private fun MessageDigest.ints(values: IntArray) {
        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES)
        values.forEach { value ->
            buffer.clear()
            buffer.putInt(value)
            update(buffer.array())
        }
    }

    private fun MessageDigest.hex(): String = digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private val strictControls = listOf(
        StrictControl(
            sha256 = "25e39e5ef5ef0de0f7faf481827927a4033ac1d31782a2b9be9a8412d8fd1158",
            generation = 1,
            family = "RED_BLUE",
            mapCount = 226,
            sceneSignature = "9a4c4968a2f45d91228d8febb94ee84e80b9bb27c1a0f78167f0dd071663864c",
            connection = SceneRelation(0x00, 0x0c, 0, -36),
        ),
        StrictControl(
            sha256 = "c99d737043ae5cbb60f1dd90c2376098a13a7abe393c61c10f8b2204a0cce85b",
            generation = 1,
            family = "RED_BLUE",
            mapCount = 226,
            sceneSignature = "9a4c4968a2f45d91228d8febb94ee84e80b9bb27c1a0f78167f0dd071663864c",
            connection = SceneRelation(0x00, 0x0c, 0, -36),
        ),
        StrictControl(
            sha256 = "024a1c4dab1b12d0b963c6cf756d2c1082de0ccd53fe31384787dcf34edef718",
            generation = 1,
            family = "RED_BLUE",
            mapCount = 226,
            sceneSignature = "9a4c4968a2f45d91228d8febb94ee84e80b9bb27c1a0f78167f0dd071663864c",
            connection = SceneRelation(0x00, 0x0c, 0, -36),
        ),
    )
}

private data class Candidate(val index: Int, val path: Path, val sha256: String)

private data class RunProjection(
    val selectionStatus: String = "ERROR",
    val generation: Int? = null,
    val family: String? = null,
    val localCapability: String? = null,
    val mapCount: Int = 0,
    val pngAssetCount: Int = 0,
    val indexedAssetCount: Int = 0,
    val timedAssetCount: Int = 0,
    val sceneCount: Int = 0,
    val rasterSignature: String = "",
    val sceneSignature: String = "",
    val sceneRelations: Set<SceneRelation> = emptySet(),
    val diagnostics: List<String> = emptyList(),
    val errorType: String? = null,
) {
    fun deterministicProjection() = copy()
}

private data class GbGbcMatrixRow(
    val manifestIndex: Int,
    val sha256: String,
    val generation: Int,
    val family: String,
    val localCapability: String?,
    val mapCount: Int,
    val pngAssetCount: Int,
    val indexedAssetCount: Int,
    val timedAssetCount: Int,
    val sceneCount: Int,
    val rasterSignature: String,
    val sceneSignature: String,
    val diagnostics: List<String>,
    val deterministic: Boolean,
    val baselinePreserved: Boolean?,
    val errorType: String?,
    @Transient val strictConnectionVerified: Boolean?,
)

private data class GbGbcMatrixReport(
    val generatedAt: String,
    val sourceCommit: String,
    val rows: List<GbGbcMatrixRow>,
    val summary: GbGbcMatrixSummary,
)

private data class GbGbcMatrixSummary(
    val manifestCount: Int,
    val shaVerified: Int,
    val gbGbcCandidates: Int,
    val selected: Int,
    val deterministic: Int,
    val parserErrors: Int,
    val localAvailable: Int,
    val maps: Int,
    val assets: Int,
    val scenes: Int,
    val baselineCompared: Int,
    val baselineRegressions: Int,
    val strictControls: Int,
    val strictControlFailures: Int,
)

private data class BaselineProjection(
    val sha256: String,
    val generation: Int,
    val family: String,
    val localCapability: String?,
    val mapCount: Int,
    val pngAssetCount: Int,
    val indexedAssetCount: Int,
    val timedAssetCount: Int,
    val rasterSignature: String,
    val errorType: String?,
)

private data class SceneRelation(
    val sourceAreaId: Int,
    val targetAreaId: Int,
    val deltaX: Int,
    val deltaY: Int,
)

private data class StrictControl(
    val sha256: String,
    val generation: Int,
    val family: String,
    val mapCount: Int,
    val sceneSignature: String,
    val connection: SceneRelation,
)
