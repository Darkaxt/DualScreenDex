package com.enrpau.dualscreendex.parser.cli

import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializer
import com.enrpau.dualscreendex.parser.parse.ParserOrchestrator
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess
import kotlin.time.measureTimedValue
import kotlin.time.measureTime

private const val USAGE = "parser-cli <root> [<root> ...] --json <path> --markdown <path> [--cache-dir <path>] [--all-roms]"

fun main(arguments: Array<String>) {
    if (arguments.any { it == "--help" || it == "-h" }) {
        println(USAGE)
        return
    }

    val options = try {
        CliOptions.parse(arguments)
    } catch (failure: IllegalArgumentException) {
        System.err.println("${failure.message}\n$USAGE")
        exitProcess(2)
    }

    val scanner = CorpusScanner(includeAllRomNames = options.includeAllRomNames)
    val inputs = scanner.scan(options.roots)
    val cache = options.cacheDirectory?.let { CatalogCache(it.toFile(), JdbcCatalogDatabaseFactory) }
    val results = inputs.mapIndexed { index, input ->
        println("[${index + 1}/${inputs.size}] ${input.displayName}")
        val result = if (input.error != null || input.rom == null) {
            CorpusResult(input.displayName, input.source, input.archiveEntry, 0, error = input.error ?: "input has no ROM image")
        } else {
            try {
                val measured = measureTimedValue {
                    val analysis = ParserOrchestrator.analyze(input.rom)
                    val layout = analysis.probes.singleOrNull { it.family == analysis.selectedFamily }?.resolvedLayout
                    val catalog = if (analysis.status == SelectionStatus.SELECTED && layout != null) {
                        runCatching { CatalogMaterializer.materialize(input.rom, analysis, layout) }
                    } else {
                        null
                    }
                    CatalogAttempt(analysis, catalog)
                }
                val materialized = measured.value.catalog?.getOrNull()
                val persisted = if (cache != null && materialized != null) {
                    runCatching { persistCatalog(cache, input, measured.value.analysis, materialized) }
                } else {
                    null
                }
                CorpusResult(
                    input.displayName,
                    input.source,
                    input.archiveEntry,
                    measured.duration.inWholeMilliseconds,
                    result = materialized?.let { catalog ->
                        measured.value.analysis.copy(
                            capabilities = catalog.capabilities.values.sortedBy { it.capability.ordinal },
                        )
                    } ?: measured.value.analysis,
                    catalog = materialized?.let(CatalogMetrics.Companion::from),
                    samples = materialized?.let(CatalogSamples.Companion::from),
                    catalogError = measured.value.catalog?.exceptionOrNull()?.let(::readableFailure),
                    persistence = persisted?.getOrNull(),
                    persistenceError = persisted?.exceptionOrNull()?.let(::readableFailure),
                )
            } catch (failure: Exception) {
                CorpusResult(
                    input.displayName,
                    input.source,
                    input.archiveEntry,
                    0,
                    error = "${failure.javaClass.simpleName}: ${failure.message ?: "parser failure"}",
                )
            }
        }
        println(
            "  -> ${result.result?.status ?: "ERROR"}" +
                (result.result?.selectedFamily?.let { " / $it" } ?: "") +
                (result.persistence?.let { " / SQLite ${it.bytes} bytes, reopen ${it.reopenMillis} ms" } ?: ""),
        )
        result
    }
    val report = CorpusReport(
        roots = options.roots.map { it.toString().replace('\\', '/') },
        results = results,
    )
    write(options.json, ReportWriter.json(report))
    write(options.markdown, ReportWriter.markdown(report))

    val selected = results.count { it.result?.status?.name == "SELECTED" }
    val noFamilyMatch = results.count { it.result?.status?.name == "NO_FAMILY_MATCH" }
    val ambiguous = results.count { it.result?.status?.name == "AMBIGUOUS" }
    val errors = results.count { it.error != null }
    println("Evaluated ${results.size} inputs: $selected selected, $ambiguous ambiguous, $noFamilyMatch with no mainline-family match, $errors errors")
    println("JSON: ${options.json.toAbsolutePath()}")
    println("Markdown: ${options.markdown.toAbsolutePath()}")
}

private data class CatalogAttempt(
    val analysis: com.enrpau.dualscreendex.parser.model.ParseResult,
    val catalog: Result<com.enrpau.dualscreendex.parser.catalog.ParsedCatalog>?,
)

private fun persistCatalog(
    cache: CatalogCache,
    input: CorpusInput,
    analysis: com.enrpau.dualscreendex.parser.model.ParseResult,
    catalog: com.enrpau.dualscreendex.parser.catalog.ParsedCatalog,
): CatalogPersistenceMetrics {
    val source = CatalogSourceMetadata.fromDisplayName(
        input.displayName,
        analysis.size,
        analysis.header.title,
    )
    val writeDuration = measureTime { cache.write(catalog, source, CatalogWriteProgress.complete()) }
    var reopened: com.darkaxt.dualdex.catalog.StoredCatalog? = null
    val reopenDuration = measureTime { reopened = cache.readComplete(catalog.romSha256) }
    val stored = requireNotNull(reopened) { "completed SQLite catalog did not reopen" }
    require(stored.catalog == catalog) { "reopened SQLite catalog differs from parsed catalog" }
    return CatalogPersistenceMetrics(
        fileName = cache.fileFor(catalog.romSha256).name,
        bytes = cache.fileFor(catalog.romSha256).length(),
        writeMillis = writeDuration.inWholeMilliseconds,
        reopenMillis = reopenDuration.inWholeMilliseconds,
        sections = stored.committedSections.size,
    )
}

private fun readableFailure(failure: Throwable): String =
    "${failure.javaClass.simpleName}: ${failure.message ?: "catalog materialization failure"}"

private fun write(path: Path, content: String) {
    path.toAbsolutePath().parent?.let(Files::createDirectories)
    Files.writeString(path, content)
}

internal data class CliOptions(
    val roots: List<Path>,
    val json: Path,
    val markdown: Path,
    val cacheDirectory: Path?,
    val includeAllRomNames: Boolean,
) {
    companion object {
        fun parse(arguments: Array<String>): CliOptions {
            val roots = mutableListOf<Path>()
            var json: Path? = null
            var markdown: Path? = null
            var cacheDirectory: Path? = null
            var includeAllRomNames = false
            var index = 0
            while (index < arguments.size) {
                when (val argument = arguments[index]) {
                    "--json" -> json = valueAfter(arguments, ++index, argument)
                    "--markdown" -> markdown = valueAfter(arguments, ++index, argument)
                    "--cache-dir" -> cacheDirectory = valueAfter(arguments, ++index, argument)
                    "--all-roms" -> includeAllRomNames = true
                    else -> {
                        require(!argument.startsWith("--")) { "unknown option: $argument" }
                        roots.add(Path.of(argument))
                    }
                }
                index++
            }
            require(roots.isNotEmpty()) { "at least one root is required" }
            return CliOptions(
                roots = roots,
                json = requireNotNull(json) { "--json is required" },
                markdown = requireNotNull(markdown) { "--markdown is required" },
                cacheDirectory = cacheDirectory,
                includeAllRomNames = includeAllRomNames,
            )
        }

        private fun valueAfter(arguments: Array<String>, index: Int, option: String): Path {
            require(index < arguments.size) { "$option requires a path" }
            return Path.of(arguments[index])
        }
    }
}
