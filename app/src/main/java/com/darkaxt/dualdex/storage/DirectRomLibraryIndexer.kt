package com.darkaxt.dualdex.storage

import com.darkaxt.dualdex.retroarch.RomIndexEntry
import java.io.File
import java.util.ArrayDeque

class DirectRomLibraryIndexer {
    fun index(roots: List<File>): RomLibraryIndexResult {
        val entries = mutableListOf<RomIndexEntry>()
        val warnings = mutableListOf<String>()
        discoverSources(roots).forEach { source ->
            runCatching {
                val identity = StreamingRomSourceReader.read(source)
                val entryName = identity.archiveEntry ?: identity.displayName
                entries += RomIndexEntry(
                    sourceId = source.toURI().normalize().toString(),
                    sourceName = identity.displayName,
                    archiveEntry = identity.archiveEntry,
                    platform = identity.platform,
                    gameBasename = entryName.substringBeforeLast('.', entryName),
                    crc32 = identity.crc32,
                    sha256 = identity.sha256,
                )
            }.onFailure { failure ->
                warnings += "${source.name}: ${failure.message ?: failure.javaClass.simpleName}"
            }
        }
        return RomLibraryIndexResult(entries.sortedBy { it.sourceName.lowercase() }, warnings)
    }

    private fun discoverSources(roots: List<File>): List<File> {
        val queue = ArrayDeque<File>()
        roots.forEach(queue::addLast)
        val visitedDirectories = mutableSetOf<String>()
        val visitedFiles = mutableSetOf<String>()
        val sources = mutableListOf<File>()
        while (queue.isNotEmpty()) {
            val candidate = runCatching { queue.removeFirst().canonicalFile }.getOrNull() ?: continue
            if (candidate.isDirectory) {
                if (candidate.isProtectedAndroidDirectory() || !visitedDirectories.add(candidate.path)) continue
                candidate.listFiles().orEmpty().sortedBy { it.name.lowercase() }.forEach(queue::addLast)
            } else if (
                candidate.isFile &&
                candidate.extension.lowercase() in SUPPORTED_EXTENSIONS &&
                visitedFiles.add(candidate.path)
            ) {
                sources += candidate
            }
        }
        return sources.sortedBy { it.path.lowercase() }
    }

    private fun File.isProtectedAndroidDirectory(): Boolean =
        parentFile?.name.equals("Android", ignoreCase = true) &&
            name.lowercase() in PROTECTED_ANDROID_DIRECTORIES

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("gb", "gbc", "gba", "zip")
        val PROTECTED_ANDROID_DIRECTORIES = setOf("data", "obb")
    }
}
