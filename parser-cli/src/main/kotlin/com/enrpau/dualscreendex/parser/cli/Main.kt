package com.enrpau.dualscreendex.parser.cli

import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializer
import com.enrpau.dualscreendex.parser.parse.ParserOrchestrator
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess
import kotlin.time.measureTimedValue

private const val USAGE = "parser-cli <root> [<root> ...] --json <path> --markdown <path>"

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

    val scanner = CorpusScanner()
    val inputs = scanner.scan(options.roots)
    val results = inputs.map { input ->
        if (input.error != null || input.rom == null) {
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
                CorpusResult(
                    input.displayName,
                    input.source,
                    input.archiveEntry,
                    measured.duration.inWholeMilliseconds,
                    result = measured.value.analysis,
                    catalog = measured.value.catalog?.getOrNull()?.let(CatalogMetrics.Companion::from),
                    catalogError = measured.value.catalog?.exceptionOrNull()?.let(::readableFailure),
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

private fun readableFailure(failure: Throwable): String =
    "${failure.javaClass.simpleName}: ${failure.message ?: "catalog materialization failure"}"

private fun write(path: Path, content: String) {
    path.toAbsolutePath().parent?.let(Files::createDirectories)
    Files.writeString(path, content)
}

private data class CliOptions(
    val roots: List<Path>,
    val json: Path,
    val markdown: Path,
) {
    companion object {
        fun parse(arguments: Array<String>): CliOptions {
            val roots = mutableListOf<Path>()
            var json: Path? = null
            var markdown: Path? = null
            var index = 0
            while (index < arguments.size) {
                when (val argument = arguments[index]) {
                    "--json" -> json = valueAfter(arguments, ++index, argument)
                    "--markdown" -> markdown = valueAfter(arguments, ++index, argument)
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
            )
        }

        private fun valueAfter(arguments: Array<String>, index: Int, option: String): Path {
            require(index < arguments.size) { "$option requires a path" }
            return Path.of(arguments[index])
        }
    }
}
