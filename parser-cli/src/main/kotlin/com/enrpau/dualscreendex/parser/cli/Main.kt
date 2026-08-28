package com.enrpau.dualscreendex.parser.cli

import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile
import kotlin.system.exitProcess
import kotlin.time.measureTimedValue
import kotlin.time.measureTime

private const val USAGE =
    "parser-cli <root> [<root> ...] --json <path> --markdown <path> " +
        "--execution-receipt <path> --source-commit <40-char commit> " +
        "[--cache-dir <path>] [--jobs <1..8>] [--all-roms] (maximum 10000 inputs)"
internal const val MAX_CLI_JOBS = 8
internal const val MAX_CLI_INPUTS = 10_000
private const val QUEUED_TASKS_PER_WORKER = 1
private val DEFAULT_JOBS = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)

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

    val generatorArtifact = runningGeneratorArtifact()
    val generatorArtifacts = runningGeneratorArtifacts(generatorArtifact)
    val embeddedSourceCommit = embeddedSourceCommit(generatorArtifact)
    require(options.sourceCommit == embeddedSourceCommit) {
        "--source-commit does not match the parser CLI build source"
    }
    val executionIdentity = CorpusExecutionIdentity(
        sourceCommit = embeddedSourceCommit,
        generatorSha256 = runtimeClasspathSha256(generatorArtifacts),
    )
    val scanner = CorpusScanner(includeAllRomNames = options.includeAllRomNames)
    val inputs = boundedCorpusInputs(scanner.scan(options.roots))
    val cache = options.cacheDirectory?.let { CatalogCache(it.toFile(), JdbcCatalogDatabaseFactory) }
    println("Evaluating ${inputs.size} inputs with up to ${options.jobs} workers")
    val results = mapConcurrentlyOrdered(inputs, options.jobs) { index, input ->
        println("[${index + 1}] ${input.displayName}")
        val result = if (input.error != null) {
            CorpusResult(input.displayName, input.source, input.archiveEntry, 0, error = input.error)
        } else {
            try {
                val rom = input.loadRom()
                val measured = measureTimedValue { CatalogParser.parseCatching(rom) }
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
            "[${index + 1}] -> ${result.result?.status ?: "ERROR"}" +
                (result.result?.selectedFamily?.let { " / $it" } ?: "") +
                (result.persistence?.let { " / SQLite ${it.bytes} bytes, reopen ${it.reopenMillis} ms" } ?: ""),
        )
        result
    }
    val report = CorpusReport(
        execution = executionIdentity,
        roots = options.roots.map { it.toString().replace('\\', '/') },
        results = results,
    )
    writeAtomically(options.json) { ReportWriter.json(report, it) }
    writeAtomically(options.markdown) { ReportWriter.markdown(report, it) }
    val receipt = CorpusExecutionReceipt.fromFiles(
        rawReport = options.json,
        generatorArtifacts = generatorArtifacts,
        identity = executionIdentity,
        inputCount = results.size,
    )
    writeAtomically(options.executionReceipt) { it.write(ReportWriter.executionReceiptJson(receipt)) }

    val selected = results.count { it.result?.status?.name == "SELECTED" }
    val noFamilyMatch = results.count { it.result?.status?.name == "NO_FAMILY_MATCH" }
    val ambiguous = results.count { it.result?.status?.name == "AMBIGUOUS" }
    val errors = results.count { it.error != null }
    println("Evaluated ${results.size} inputs: $selected selected, $ambiguous ambiguous, $noFamilyMatch with no mainline-family match, $errors errors")
    println("JSON: ${options.json.toAbsolutePath()}")
    println("Markdown: ${options.markdown.toAbsolutePath()}")
    println("Execution receipt: ${options.executionReceipt.toAbsolutePath()}")
}

private fun runningGeneratorArtifact(): Path {
    val location = Class.forName("com.enrpau.dualscreendex.parser.cli.MainKt")
        .protectionDomain
        .codeSource
        ?.location
        ?: error("parser CLI generator location is unavailable")
    val path = Path.of(location.toURI())
    require(Files.isRegularFile(path)) { "parser CLI must run from a packaged generator artifact" }
    return path
}

private fun runningGeneratorArtifacts(generatorArtifact: Path): List<Path> {
    val directory = requireNotNull(generatorArtifact.parent) { "parser CLI distribution directory is unavailable" }
    val artifacts = Files.list(directory).use { paths ->
        paths.filter { path ->
            Files.isRegularFile(path) && path.fileName.toString().endsWith(".jar", ignoreCase = true)
        }.toList()
    }
    require(generatorArtifact in artifacts) { "parser CLI artifact is absent from its runtime classpath" }
    return artifacts
}

private fun embeddedSourceCommit(generatorArtifact: Path): String = JarFile(generatorArtifact.toFile()).use { jar ->
    val sourceCommit = jar.manifest?.mainAttributes?.getValue("DualDex-Source-Commit")
    require(sourceCommit?.matches(Regex("[0-9a-f]{40}")) == true) {
        "parser CLI build has no valid embedded source commit"
    }
    sourceCommit
}

internal fun <T> boundedCorpusInputs(
    inputs: Sequence<T>,
    maximumInputs: Int = MAX_CLI_INPUTS,
): List<T> {
    require(maximumInputs > 0) { "maximum inputs must be positive" }
    val iterator = inputs.iterator()
    val retained = ArrayList<T>(maximumInputs)
    while (iterator.hasNext()) {
        require(retained.size < maximumInputs) {
            "parser-cli accepts at most $maximumInputs inputs; narrow the supplied roots"
        }
        retained += iterator.next()
    }
    return retained
}

internal fun <T, R> mapConcurrentlyOrdered(
    inputs: Iterable<T>,
    jobs: Int,
    transform: (Int, T) -> R,
): List<R> {
    require(jobs > 0) { "jobs must be positive" }
    val effectiveJobs = jobs.coerceAtMost(MAX_CLI_JOBS)
    if (effectiveJobs == 1) return inputs.mapIndexed(transform)

    val queueCapacity = effectiveJobs * QUEUED_TASKS_PER_WORKER
    val maximumInFlight = effectiveJobs + queueCapacity
    val executor = ThreadPoolExecutor(
        effectiveJobs,
        effectiveJobs,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(queueCapacity),
    ) { task, target ->
        if (target.isShutdown) {
            throw RejectedExecutionException("parser worker queue is shut down")
        }
        target.queue.put(task)
    }
    val iterator = inputs.iterator()
    val completion = ExecutorCompletionService<IndexedResult<R>>(executor)
    val results = mutableListOf<IndexedResult<R>>()
    var exhausted = false
    var inFlight = 0
    var nextIndex = 0
    return try {
        while (!exhausted || inFlight > 0) {
            while (!exhausted && inFlight < maximumInFlight) {
                if (iterator.hasNext()) {
                    val index = nextIndex++
                    val input = iterator.next()
                    completion.submit(Callable { IndexedResult(index, transform(index, input)) })
                    inFlight++
                } else {
                    exhausted = true
                }
            }
            if (inFlight > 0) {
                results += completion.take().get()
                inFlight--
            }
        }
        results.sortedBy { it.index }.map { it.value }
    } finally {
        executor.shutdownNow()
    }
}

private data class IndexedResult<R>(val index: Int, val value: R)

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

internal fun writeAtomically(path: Path, content: (Writer) -> Unit) {
    val absolute = path.toAbsolutePath()
    val parent = absolute.parent ?: throw IllegalArgumentException("report path has no parent: $absolute")
    Files.createDirectories(parent)
    val temporary = Files.createTempFile(parent, ".${absolute.fileName}.", ".tmp")
    try {
        Files.newBufferedWriter(temporary, StandardCharsets.UTF_8).use(content)
        try {
            Files.move(
                temporary,
                absolute,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temporary)
    }
}

internal data class CliOptions(
    val roots: List<Path>,
    val json: Path,
    val markdown: Path,
    val executionReceipt: Path,
    val sourceCommit: String,
    val cacheDirectory: Path?,
    val includeAllRomNames: Boolean,
    val jobs: Int,
) {
    companion object {
        fun parse(arguments: Array<String>): CliOptions {
            val roots = mutableListOf<Path>()
            var json: Path? = null
            var markdown: Path? = null
            var executionReceipt: Path? = null
            var sourceCommit: String? = null
            var cacheDirectory: Path? = null
            var includeAllRomNames = false
            var jobs = DEFAULT_JOBS
            var index = 0
            while (index < arguments.size) {
                when (val argument = arguments[index]) {
                    "--json" -> json = valueAfter(arguments, ++index, argument)
                    "--markdown" -> markdown = valueAfter(arguments, ++index, argument)
                    "--execution-receipt" -> executionReceipt = valueAfter(arguments, ++index, argument)
                    "--source-commit" -> sourceCommit = stringAfter(arguments, ++index, argument)
                    "--cache-dir" -> cacheDirectory = valueAfter(arguments, ++index, argument)
                    "--jobs" -> jobs = jobCountAfter(arguments, ++index)
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
                executionReceipt = requireNotNull(executionReceipt) { "--execution-receipt is required" },
                sourceCommit = requireNotNull(sourceCommit) { "--source-commit is required" }.also {
                    require(it.matches(Regex("[0-9a-f]{40}"))) {
                        "--source-commit requires a full lowercase commit"
                    }
                },
                cacheDirectory = cacheDirectory,
                includeAllRomNames = includeAllRomNames,
                jobs = jobs,
            )
        }

        private fun jobCountAfter(arguments: Array<String>, index: Int): Int =
            arguments.getOrNull(index)?.toIntOrNull()?.takeIf { it > 0 }
                ?.coerceAtMost(MAX_CLI_JOBS)
                ?: throw IllegalArgumentException("--jobs requires a positive integer")

        private fun stringAfter(arguments: Array<String>, index: Int, option: String): String {
            require(index < arguments.size) { "$option requires a value" }
            return arguments[index]
        }

        private fun valueAfter(arguments: Array<String>, index: Int, option: String): Path {
            require(index < arguments.size) { "$option requires a path" }
            return Path.of(arguments[index])
        }
    }
}
