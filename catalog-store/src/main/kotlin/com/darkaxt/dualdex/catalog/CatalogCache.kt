package com.darkaxt.dualdex.catalog

import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import java.io.File

interface CatalogRepository {
    fun write(catalog: ParsedCatalog, source: CatalogSourceMetadata, progress: CatalogWriteProgress)
    fun readComplete(sha256: String): StoredCatalog?
    fun lookupComplete(sha256: String): CatalogCacheLookup = readComplete(sha256).let { stored ->
        CatalogCacheLookup(
            stored = stored,
            decision = if (stored == null) CatalogCacheDecision.MISS_FILE_ABSENT else CatalogCacheDecision.HIT,
        )
    }
    fun findCompleted(crc32: String, romSize: Int, romTitle: String? = null): List<StoredCatalog>
}

enum class CatalogCacheDecision {
    MISS_FILE_ABSENT,
    MISS_INCOMPLETE_OR_INCOMPATIBLE,
    HIT,
    REJECTED_EXCEPTION,
}

data class CatalogCacheEvent(
    val decision: CatalogCacheDecision,
    val sha256: String,
    val failure: Exception? = null,
)

data class CatalogCacheLookup(
    val stored: StoredCatalog?,
    val decision: CatalogCacheDecision,
)

class CatalogCache(
    private val directory: File,
    private val databaseFactory: CatalogDatabaseFactory,
    private val onDecision: (CatalogCacheEvent) -> Unit = {},
) : CatalogRepository {
    init {
        require(directory.exists() || directory.mkdirs()) { "catalog cache directory could not be created: $directory" }
        require(directory.isDirectory) { "catalog cache path is not a directory: $directory" }
    }

    @Synchronized
    override fun write(catalog: ParsedCatalog, source: CatalogSourceMetadata, progress: CatalogWriteProgress) {
        databaseFactory.open(fileFor(catalog.romSha256)).use { database ->
            CatalogWriter(database).write(catalog, source, progress)
        }
    }

    @Synchronized
    override fun readComplete(sha256: String): StoredCatalog? = lookupComplete(sha256).stored

    @Synchronized
    override fun lookupComplete(sha256: String): CatalogCacheLookup {
        val file = fileFor(sha256)
        val normalizedSha = file.nameWithoutExtension
        if (!file.isFile) {
            return lookup(normalizedSha, CatalogCacheDecision.MISS_FILE_ABSENT)
        }
        return try {
            val stored = databaseFactory.open(file).use { database -> CatalogReader(database).readComplete() }
            require(stored == null || stored.catalog.romSha256.equals(normalizedSha, ignoreCase = true)) {
                "embedded catalog identity does not match the requested cache key"
            }
            lookup(
                normalizedSha,
                if (stored == null) CatalogCacheDecision.MISS_INCOMPLETE_OR_INCOMPATIBLE else CatalogCacheDecision.HIT,
                stored,
            )
        } catch (failure: Exception) {
            report(CatalogCacheEvent(CatalogCacheDecision.REJECTED_EXCEPTION, normalizedSha, failure))
            check(file.parentFile.canonicalFile == directory.canonicalFile) { "refusing to invalidate a cache outside its directory" }
            check(file.delete() || !file.exists()) { "corrupt catalog cache could not be removed: $file" }
            CatalogCacheLookup(null, CatalogCacheDecision.REJECTED_EXCEPTION)
        }
    }

    @Synchronized
    override fun findCompleted(crc32: String, romSize: Int, romTitle: String?): List<StoredCatalog> =
        directory.listFiles { file -> file.isFile && file.extension == "sqlite" }.orEmpty()
            .mapNotNull { file -> readComplete(file.nameWithoutExtension) }
            .filter { stored ->
                stored.catalog.romCrc32.equals(crc32, ignoreCase = true) &&
                    stored.source.romSize == romSize &&
                    (romTitle == null || stored.source.romTitle == romTitle)
            }
            .sortedByDescending(StoredCatalog::writtenAtEpochMs)

    fun fileFor(sha256: String): File {
        require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "catalog SHA-256 is invalid" }
        return File(directory, "${sha256.lowercase()}.sqlite")
    }

    /** Removes only inactive parser databases. SaveRAM snapshots and knowledge records are not in this namespace. */
    @Synchronized
    fun clearInactive(activeSha256: String?): Int {
        val active = activeSha256?.also {
            require(it.matches(Regex("[0-9a-fA-F]{64}"))) { "active catalog SHA-256 is invalid" }
        }?.lowercase()
        val root = directory.canonicalFile
        val candidates = directory.listFiles().orEmpty().filter { file ->
            CACHE_FILE.matches(file.name) && file.name.substringBefore(".sqlite").lowercase() != active
        }
        candidates.forEach { file ->
            check(file.canonicalFile.parentFile == root) { "refusing to clear a cache outside its directory" }
            check(file.delete() || !file.exists()) { "inactive catalog cache could not be removed: $file" }
        }
        return candidates.count { !it.exists() }
    }

    private fun report(event: CatalogCacheEvent) {
        runCatching { onDecision(event) }
    }

    private fun lookup(
        sha256: String,
        decision: CatalogCacheDecision,
        stored: StoredCatalog? = null,
    ): CatalogCacheLookup {
        report(CatalogCacheEvent(decision, sha256))
        return CatalogCacheLookup(stored, decision)
    }

    private companion object {
        val CACHE_FILE = Regex("[0-9a-fA-F]{64}\\.sqlite(?:-wal|-shm)?")
    }
}
