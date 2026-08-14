package com.enrpau.dualscreendex.parser.cli

import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.EvolutionEdge
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.dataset.evolutions.EvolutionRowOutcome
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.GZIPInputStream

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
            val evolutionLayout = requireNotNull(first.layout?.tables?.evolutions) {
                "row $index did not select an evolution table"
            }
            val typed = first.layout?.resolvedDatasets?.evolutions
            val malformedRows = typed?.rows?.count { it is EvolutionRowOutcome.Malformed }
            val decodedRows = typed?.rows?.size ?: evolutionLayout.count
            val firstHash = evolutionSha256(firstCatalog)
            val secondHash = evolutionSha256(secondCatalog)
            val capability = firstCatalog.capabilities.getValue(RomCapability.EVOLUTIONS)
            val catalogFieldsAvailable = firstCatalog.navigableSpecies().all {
                it.evolutionEdges.status == CapabilityStatus.AVAILABLE
            } && secondCatalog.navigableSpecies().all {
                it.evolutionEdges.status == CapabilityStatus.AVAILABLE
            }

            val baselineFile = baselineDirectory.resolve("${rom.sha256}.sqlite").toFile()
            require(baselineFile.isFile) { "row $index baseline cache is missing" }
            val baseline = readBaselineEvolution(baselineFile)
            val baselineAvailable = baseline.status == CapabilityStatus.AVAILABLE
            val baselineHash = evolutionSha256(baseline.species)

            val cache = CatalogCache(currentCacheDirectory.toFile(), JdbcCatalogDatabaseFactory)
            cache.write(
                firstCatalog,
                CatalogSourceMetadata.direct(item.get("EntryPath").asString, rom.size, first.analysis.header.title),
                CatalogWriteProgress.complete(),
            )
            val reopened = requireNotNull(cache.readComplete(rom.sha256)) { "row $index cache did not reopen" }.catalog
            val reopenedHash = evolutionSha256(reopened)

            val complete = capability.status == CapabilityStatus.AVAILABLE && catalogFieldsAvailable &&
                (typed == null || typed.rows.size == evolutionLayout.count && malformedRows == 0)
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
                selectedRows = decodedRows,
                malformedRows = malformedRows,
                resolutionPath = if (typed == null) "INTEGRATED_SPECIES_RECORD" else "TYPED_ROWS",
                complete = complete,
                navigableRows = firstCatalog.navigableSpecies().size,
                edges = edgeCount(firstCatalog),
                semanticSha256 = firstHash,
                baselineStatus = baseline.status.name,
                baselineCompleteHashPreserved = preservedBaseline,
                deterministic = deterministic,
                sqliteReopenExact = persistenceExact,
            )
            write(outputPath, Report(Instant.now().toString(), sourceCommit, manifestSha256(manifestPath), rows))
            println("%02d/50 complete rows=%d edges=%d sha=%s".format(index, decodedRows, edgeCount(firstCatalog), firstHash.take(12)))
        }

        val summary = Summary(
            identities = rows.size,
            completeTables = rows.count(Row::complete),
            malformedRows = rows.sumOf { it.malformedRows ?: 0 },
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
        return evolutionSha256(catalog.navigableSpecies().associateBy(SpeciesRecord::id))
    }

    private fun evolutionSha256(speciesById: Map<Int, SpeciesRecord>): String {
        val values = speciesById.values.filter { species ->
            (species.dexNumber.value ?: 0) > 0 && species.name.value?.any(Char::isLetterOrDigit) == true
        }.associate { species ->
            species.id to species.evolutionEdges.value.orEmpty()
        }
        val bytes = values.toSortedMap().entries.joinToString("\u001e") { (id, edges) ->
            "$id\u001f" + edges.joinToString("\u001d") { edge -> edge.semanticRecord() }
        }.toByteArray()
        return sha256(bytes)
    }

    /** Reads only the two schema-stable sections needed for the RC24 comparison. */
    private fun readBaselineEvolution(file: java.io.File): BaselineEvolution =
        JdbcCatalogDatabaseFactory.open(file).use { database ->
            val sections = database.query(
                "SELECT name, encoding, payload FROM catalog_sections WHERE name IN ('species', 'capabilities')",
            ) { row ->
                require(row.string("encoding") == "gzip+json") { "unsupported baseline section encoding" }
                requireNotNull(row.string("name")) to requireNotNull(row.bytes("payload"))
            }.toMap()
            require(sections.keys == setOf("species", "capabilities")) {
                "baseline cache omitted evolution comparison sections"
            }
            val speciesType = object : TypeToken<Map<Int, SpeciesRecord>>() {}.type
            val capabilitiesType = object : TypeToken<Map<RomCapability, CapabilityEvidence>>() {}.type
            val species = gson.fromJson<Map<Int, SpeciesRecord>>(decodeSection(sections.getValue("species")), speciesType)
            val capabilities = gson.fromJson<Map<RomCapability, CapabilityEvidence>>(
                decodeSection(sections.getValue("capabilities")),
                capabilitiesType,
            )
            BaselineEvolution(
                status = capabilities.getValue(RomCapability.EVOLUTIONS).status,
                species = species,
            )
        }

    private fun decodeSection(payload: ByteArray): String =
        GZIPInputStream(ByteArrayInputStream(payload)).use { it.readBytes().toString(Charsets.UTF_8) }

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

    private data class BaselineEvolution(
        val status: CapabilityStatus,
        val species: Map<Int, SpeciesRecord>,
    )

    private data class Row(
        val manifestRow: Int,
        val entry: String,
        val sha256: String,
        val family: String?,
        val selectedRows: Int,
        val malformedRows: Int?,
        val resolutionPath: String,
        val complete: Boolean,
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
