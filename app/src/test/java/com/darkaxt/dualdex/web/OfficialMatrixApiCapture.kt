package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.catalog.CatalogDatabaseFactory
import com.darkaxt.dualdex.catalog.CatalogSchema
import com.darkaxt.dualdex.catalog.CatalogLogicalDigest
import com.darkaxt.dualdex.catalog.CatalogRepository
import com.darkaxt.dualdex.catalog.StoredCatalog
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

internal data class MatrixCacheControl(
    val romSha256: String,
    val cacheSha256: String,
    val family: EngineFamily,
    val language: String,
    val codecId: String,
    val codecVersion: Int,
)

internal data class MatrixG3Binding(
    val sourceCommit: String,
    val sourceSha256: String,
    val reportSha256: String,
    val receiptSha256: String,
    val generatorSha256: String,
)

/** Captures the unchanged production bootstrap and exact restored catalog; never asserts acceptance. */
internal object OfficialMatrixApiCapture {
    private const val MAX_CACHE_BYTES = 256L * 1024 * 1024
    private val sha256 = Regex("[0-9a-f]{64}")
    private val gson = GsonBuilder().serializeNulls().create()

    fun capture(
        source: Path,
        workingDirectory: Path,
        expected: MatrixCacheControl,
        databaseFactory: CatalogDatabaseFactory,
        binding: MatrixG3Binding,
    ): JsonObject {
        require(binding.sourceCommit.matches(Regex("[0-9a-f]{40}")) && listOf(
            binding.sourceSha256, binding.reportSha256, binding.receiptSha256, binding.generatorSha256,
        ).all(sha256::matches)) { "invalid capture binding" }
        require(sha256.matches(expected.romSha256) && sha256.matches(expected.cacheSha256)) { "invalid capture identity" }
        require(expected.language.matches(Regex("[a-z]{2}")) && expected.codecId.isNotBlank() && expected.codecVersion > 0) {
            "invalid codec identity"
        }
        val input = source.toAbsolutePath().normalize()
        val working = workingDirectory.toAbsolutePath().normalize()
        require(Files.isRegularFile(input, NOFOLLOW_LINKS) && input.toRealPath() == input) { "source must be a regular unaliased cache" }
        require(input.fileName.toString() == "${expected.romSha256}.sqlite") { "source cache key mismatch" }
        require(Files.size(input) in 1..MAX_CACHE_BYTES) { "source cache byte bound exceeded" }
        require(Files.notExists(working, NOFOLLOW_LINKS)) { "working directory already exists or is inaccessible" }
        require(Files.isDirectory(working.parent, NOFOLLOW_LINKS) && working.parent.toRealPath() == working.parent) {
            "working parent must be an existing unaliased directory"
        }
        requireNoSidecars(input)
        require(digest(input) == expected.cacheSha256) { "source cache digest mismatch" }
        Files.createDirectory(working)
        val copy = working.resolve("${expected.romSha256}.sqlite")
        try {
            // Production cache reads can migrate or invalidate files. Only this private copy enters them.
            Files.newInputStream(input, NOFOLLOW_LINKS).use { stream ->
                Files.newOutputStream(copy, CREATE_NEW, WRITE).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_CACHE_BYTES) { "source cache grew beyond byte bound" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(digest(copy) == expected.cacheSha256 && digest(input) == expected.cacheSha256) { "cache changed while copying" }
            requireNoSidecars(input)
            databaseFactory.open(copy.toFile()).use { database ->
                require(database.query("PRAGMA user_version") { it.long("user_version") }.singleOrNull() == CatalogSchema.version.toLong()) {
                    "cache SQL schema mismatch"
                }
                require(database.query("SELECT parser_schema_version FROM catalog_metadata") { it.long("parser_schema_version") }
                    .singleOrNull() == CatalogSchema.parserSchemaVersion.toLong()) { "cache parser schema mismatch" }
            }
            val parserInvocations = AtomicInteger()
            val cache = CatalogCache(working.toFile(), databaseFactory)
            var restored: ParsedCatalog? = null
            var repositoryReads = 0
            val recording = object : CatalogRepository by cache {
                override fun readComplete(sha256: String): StoredCatalog? {
                    check(++repositoryReads == 1) { "capture must consume exactly one repository result" }
                    return cache.readComplete(sha256).also { restored = it?.catalog }
                }
            }
            return ProductionCompanionRuntime(
                catalogRepository = recording,
                initialSettings = CompanionSettings(knowledgeMode = KnowledgeMode.DISCOVERED),
                parseCatalogWithCancellation = { _, _, _, _ ->
                    parserInvocations.incrementAndGet()
                    error("matrix cache restore must not invoke the parser")
                },
            ).use { runtime ->
                require(runtime.restoreCatalog(expected.romSha256)) { "pinned catalog did not reopen" }
                val restoredCatalog = requireNotNull(restored) { "restore did not consume the repository result" }
                val response = runtime.bootstrap()
                val catalog = requireNotNull(response.catalog) { "missing bootstrap catalog" }
                val language = requireNotNull(response.language) { "missing bootstrap language" }
                require(catalog.hash == expected.romSha256 && catalog.family == expected.family.name) { "bootstrap catalog identity mismatch" }
                require(language.manifestStatus == "RESOLVED" && language.activeLanguage == expected.language &&
                    language.defaultLanguage == expected.language && language.authority == "ROM_DEFAULT") { "bootstrap language authority mismatch" }
                val projection = requireNotNull(language.projections.singleOrNull { it.language == expected.language }) {
                    "missing or ambiguous language projection"
                }
                require(projection.status == "RESOLVED" && projection.codecId == expected.codecId && projection.codecVersion == expected.codecVersion) {
                    "bootstrap codec identity mismatch"
                }
                require(parserInvocations.get() == 0 && response.state.loading.phase == "CACHE_REOPEN") { "bootstrap was not cache-only" }
                JsonObject().apply {
                    addProperty("acceptance", false)
                    addProperty("scope", "CACHE_ONLY_OBSERVATION")
                    addProperty("cacheSha256", expected.cacheSha256)
                    add("catalogLogicalDigest", JsonObject().apply {
                        addProperty("version", CatalogLogicalDigest.version)
                        addProperty("sha256", CatalogLogicalDigest.sha256(restoredCatalog))
                    })
                    add("bootstrap", JsonObject().apply {
                        add("response", gson.toJsonTree(response))
                        addProperty("parserInvocations", parserInvocations.get())
                        add("captureProvenance", JsonObject().apply {
                            addProperty("method", "RESTORE_CATALOG_BY_SHA")
                            addProperty("sourceCacheSha256", expected.cacheSha256)
                            addProperty("parserSchemaVersion", CatalogSchema.parserSchemaVersion)
                            addProperty("sqlSchemaVersion", CatalogSchema.version)
                            addProperty("catalogLogicalDigestVersion", CatalogLogicalDigest.version)
                            addProperty("knowledgeMode", KnowledgeMode.DISCOVERED.name)
                            add("binding", gson.toJsonTree(binding))
                        })
                    })
                }
            }
        } finally {
            requireNoSidecars(input)
            require(digest(input) == expected.cacheSha256) { "retained source cache changed during capture" }
        }
    }

    private fun requireNoSidecars(source: Path) {
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            require(Files.notExists(source.resolveSibling(source.fileName.toString() + suffix), NOFOLLOW_LINKS)) {
                "source cache has a sidecar or inaccessible sidecar path"
            }
        }
    }

    private fun digest(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path, NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_CACHE_BYTES) { "cache hash byte bound exceeded" }
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
