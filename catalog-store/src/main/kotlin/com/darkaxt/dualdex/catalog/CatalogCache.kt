package com.darkaxt.dualdex.catalog

import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import java.io.File

interface CatalogRepository {
    fun write(catalog: ParsedCatalog, source: CatalogSourceMetadata, progress: CatalogWriteProgress)
    fun readComplete(sha256: String): StoredCatalog?
    fun findCompleted(crc32: String, romSize: Int, romTitle: String? = null): List<StoredCatalog>
}

class CatalogCache(
    private val directory: File,
    private val databaseFactory: CatalogDatabaseFactory,
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
    override fun readComplete(sha256: String): StoredCatalog? {
        val file = fileFor(sha256)
        if (!file.isFile) return null
        return try {
            databaseFactory.open(file).use { database -> CatalogReader(database).readComplete() }
        } catch (_: Exception) {
            check(file.parentFile.canonicalFile == directory.canonicalFile) { "refusing to invalidate a cache outside its directory" }
            check(file.delete() || !file.exists()) { "corrupt catalog cache could not be removed: $file" }
            null
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
}
