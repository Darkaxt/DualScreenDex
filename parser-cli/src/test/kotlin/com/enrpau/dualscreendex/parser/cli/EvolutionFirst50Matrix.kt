package com.enrpau.dualscreendex.parser.cli

import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.catalog.CatalogReader
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.EvolutionEdge
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.dataset.evolutions.EvolutionRowOutcome
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

/** Evidence-only exact first-50 evolution matrix. Nothing here participates in production selection. */
object EvolutionFirst50Matrix {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    @JvmStatic
    fun main(args: Array<String>) {
        val manifestPath = requiredFile("DUALDEX_FIRST50_MANIFEST")
        val outputPath = requiredOutput("DUALDEX_EVOLUTION_MATRIX_OUTPUT")
        val baselineDirectory = requiredDirectory("DUALDEX_EVOLUTION_BASELINE_CACHE")
        val currentCacheDirectory = requiredDirectory("DUALDEX_EVOLUTION_MATRIX_CACHE", create = true)
        val sourceCommit = System.getenv("DUALDEX_EVOLUTION_MATRIX_COMMIT") ?: "working-tree"
        val manifest = JsonParser.parseString(Files.readString(manifestPath)).asJsonArray
        require(manifest.size() >= 50) { "first50 manifest contained only ${manifest.size()} entries" }
        val rows = mutableListOf<Row>()

        for (index in 1..50) {
            val item = manifest[index - 1].asJsonObject
            val romPath = Path.of(item.get("ExtractedPath").asString)
            val expectedSha = item.get("RomSha256").asString.lowercase()
            val bytes = Files.readAllBytes(romPath)
            val rom = RomImage(bytes)
            require(rom.sha256 == expectedSha) { "row $index manifest SHA mismatch" }

            val first = CatalogParser.parse(rom)
            val second = CatalogParser.parse(RomImage(Files.readAllBytes(romPath)))
            val firstCatalog = requireNotNull(first.catalog) { "row $index did not materialize a catalog" }
            val secondCatalog = requireNotNull(second.catalog) { "row $index second run did not materialize a catalog" }
            val typed = requireNotNull(first.layout?.resolvedDatasets?.evolutions) {
                "row $index did not resolve typed evolutions"
            }
            val malformedRows = typed.rows.count { it is EvolutionRowOutcome.Malformed }
            val firstHash = evolutionSha256(firstCatalog)
            val secondHash = evolutionSha256(secondCatalog)
            val capability = firstCatalog.capabilities.getValue(RomCapability.EVOLUTIONS)

            val baselineFile = baselineDirectory.resolve("${rom.sha256}.sqlite").toFile()
            val baselineCatalog = require(baselineFile.isFile) { "row $index baseline cache is missing" }.let {
                JdbcCatalogDatabaseFactory.open(baselineFile).use { database -> CatalogReader(database).readComplete() }
            }?.catalog ?: error("row $index baseline catalog did not reopen")
            val baselineAvailable = baselineCatalog.capabilities[RomCapability.EVOLUTIONS]?.status ==
                CapabilityStatus.AVAILABLE
            val baselineHash = evolutionSha256(baselineCatalog)

            val cache = CatalogCache(currentCacheDirectory.toFile(), JdbcCatalogDatabaseFactory)
            cache.write(
                firstCatalog,
                CatalogSourceMetadata.direct(item.get("EntryPath").asString, rom.size, first.analysis.header.title),
                CatalogWriteProgress.complete(),
            )
            val reopened = requireNotNull(cache.readComplete(rom.sha256)) { "row $index cache did not reopen" }.catalog
            val reopenedHash = evolutionSha256(reopened)

            val complete = capability.status == CapabilityStatus.AVAILABLE &&
                typed.rows.size == first.layout?.tables?.evolutions?.count && malformedRows == 0
            val deterministic = firstHash == secondHash
            val persistenceExact = firstHash == reopenedHash
            val preservedBaseline = !baselineAvailable || firstHash == baselineHash
            check(complete) { "row $index evolution table is incomplete" }
            check(deterministic) { "row $index evolution edge hash is nondeterministic" }
            check(persistenceExact) { "row $index evolution edge hash changed after SQLite reopen" }
            check(preservedBaseline) { "row $index changed an existing complete evolution edge set" }

            rows += Row(
                manifestRow = index,
                entry = item.get("EntryPath").asString,
                sha256 = rom.sha256,
                family = first.analysis.selectedFamily?.name,
                selectedRows = typed.rows.size,
                malformedRows = malformedRows,
                navigableRows = firstCatalog.navigableSpecies().size,
                edges = edgeCount(firstCatalog),
                semanticSha256 = firstHash,
                baselineStatus = baselineCatalog.capabilities[RomCapability.EVOLUTIONS]?.status?.name,
                baselineCompleteHashPreserved = preservedBaseline,
                deterministic = deterministic,
                sqliteReopenExact = persistenceExact,
            )
            write(outputPath, Report(Instant.now().toString(), sourceCommit, manifestSha256(manifestPath), rows))
            println("%02d/50 complete rows=%d edges=%d sha=%s".format(index, typed.rows.size, edgeCount(firstCatalog), firstHash.take(12)))
        }

        val summary = Summary(
            identities = rows.size,
            completeTables = rows.count { it.malformedRows == 0 },
            malformedRows = rows.sumOf(Row::malformedRows),
            deterministicHashes = rows.count(Row::deterministic),
            sqliteReopenExact = rows.count(Row::sqliteReopenExact),
            originalCompleteTablesPreserved = rows.count {
                it.baselineStatus == CapabilityStatus.AVAILABLE.name && it.baselineCompleteHashPreserved
            },
            originalCompleteTables = rows.count { it.baselineStatus == CapabilityStatus.AVAILABLE.name },
        )
        check(summary.completeTables == 50)
        check(summary.originalCompleteTables == 44)
        check(summary.originalCompleteTablesPreserved == 44)
        write(outputPath, Report(Instant.now().toString(), sourceCommit, manifestSha256(manifestPath), rows, summary))
        println(gson.toJson(summary))
    }

    private fun edgeCount(catalog: ParsedCatalog): Int = catalog.navigableSpecies()
        .sumOf { it.evolutionEdges.value.orEmpty().size }

    private fun evolutionSha256(catalog: ParsedCatalog): String {
        val values = catalog.navigableSpecies().associate { species ->
            species.id to species.evolutionEdges.value.orEmpty()
        }
        val bytes = values.toSortedMap().entries.joinToString("\u001e") { (id, edges) ->
            "$id\u001f" + edges.joinToString("\u001d") { edge -> edge.semanticRecord() }
        }.toByteArray()
        return sha256(bytes)
    }

    private fun EvolutionEdge.semanticRecord(): String =
        "$targetSpeciesId,$methodId,$parameter,$conditionValue," +
            raw.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun manifestSha256(path: Path) = sha256(Files.readAllBytes(path))

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun write(path: Path, report: Report) {
        path.parent?.let(Files::createDirectories)
        Files.writeString(path, gson.toJson(report) + System.lineSeparator())
    }

    private fun requiredFile(name: String): Path = Path.of(requireNotNull(System.getenv(name)) { "set $name" })
        .also { require(Files.isRegularFile(it)) { "$name is not a file: $it" } }

    private fun requiredOutput(name: String): Path = Path.of(requireNotNull(System.getenv(name)) { "set $name" })

    private fun requiredDirectory(name: String, create: Boolean = false): Path =
        Path.of(requireNotNull(System.getenv(name)) { "set $name" }).also { path ->
            if (create) Files.createDirectories(path)
            require(Files.isDirectory(path)) { "$name is not a directory: $path" }
        }

    private data class Report(
        val generatedAt: String,
        val sourceCommit: String,
        val manifestSha256: String,
        val rows: List<Row>,
        val summary: Summary? = null,
    )

    private data class Row(
        val manifestRow: Int,
        val entry: String,
        val sha256: String,
        val family: String?,
        val selectedRows: Int,
        val malformedRows: Int,
        val navigableRows: Int,
        val edges: Int,
        val semanticSha256: String,
        val baselineStatus: String?,
        val baselineCompleteHashPreserved: Boolean,
        val deterministic: Boolean,
        val sqliteReopenExact: Boolean,
    )

    private data class Summary(
        val identities: Int,
        val completeTables: Int,
        val malformedRows: Int,
        val deterministicHashes: Int,
        val sqliteReopenExact: Int,
        val originalCompleteTablesPreserved: Int,
        val originalCompleteTables: Int,
    )
}
