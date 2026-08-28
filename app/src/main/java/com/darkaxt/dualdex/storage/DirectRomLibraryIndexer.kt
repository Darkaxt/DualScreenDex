package com.darkaxt.dualdex.storage

import com.darkaxt.dualdex.retroarch.RomIndexEntry
import java.io.File

class DirectRomLibraryIndexer internal constructor(
    private val traversalQuota: StorageTraversalQuota = StorageTraversalPolicy.DEFAULT,
    private val identityReader: (File) -> StreamingRomSourceIdentity = StreamingRomSourceReader::read,
) {
    fun index(
        roots: List<File>,
        previousEntries: List<RomIndexEntry> = emptyList(),
        forceRefresh: Boolean = false,
    ): RomLibraryIndexResult {
        val entries = mutableListOf<RomIndexEntry>()
        val warnings = mutableListOf<String>()
        val previousBySource = previousEntries.associateBy(RomIndexEntry::sourceId)
        discoverSources(roots).forEach { source ->
            runCatching {
                val sourceId = source.toURI().normalize().toString()
                val sourceSize = source.length()
                val sourceModified = source.lastModified()
                val previous = previousBySource[sourceId]
                    ?.takeUnless { forceRefresh }
                    ?.takeIf {
                        it.sourceSize == sourceSize &&
                            it.sourceLastModifiedEpochMs == sourceModified
                    }
                if (previous != null) {
                    entries += previous
                    return@runCatching
                }
                val identity = identityReader(source)
                val entryName = identity.archiveEntry ?: identity.displayName
                entries += RomIndexEntry(
                    sourceId = sourceId,
                    sourceName = identity.displayName,
                    archiveEntry = identity.archiveEntry,
                    platform = identity.platform,
                    gameBasename = entryName.substringBeforeLast('.', entryName),
                    crc32 = identity.crc32,
                    sha256 = identity.sha256,
                    sourceSize = sourceSize,
                    sourceLastModifiedEpochMs = sourceModified,
                )
            }.onFailure { failure ->
                warnings += "${source.name}: ${failure.message ?: failure.javaClass.simpleName}"
            }
        }
        return RomLibraryIndexResult(entries.sortedBy { it.sourceName.lowercase() }, warnings)
    }

    private fun discoverSources(roots: List<File>): List<File> {
        val sources = mutableListOf<File>()
        DirectFileTraversal.visitFiles(
            roots = roots,
            quota = traversalQuota,
            skipDirectory = { directory -> directory.isProtectedAndroidDirectory() },
        ) { candidate, budget ->
            if (candidate.extension.lowercase() in SUPPORTED_EXTENSIONS) {
                budget.retainResult()
                sources += candidate
            }
        }
        return sources.sortedBy { it.path.lowercase() }
    }

    private fun File.isProtectedAndroidDirectory(): Boolean =
        parentFile?.name.equals("Android", ignoreCase = true) &&
            name.lowercase() in PROTECTED_ANDROID_DIRECTORIES

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("gb", "gbc", "gba", "zip", "7z")
        val PROTECTED_ANDROID_DIRECTORIES = setOf("data", "obb")
    }
}
