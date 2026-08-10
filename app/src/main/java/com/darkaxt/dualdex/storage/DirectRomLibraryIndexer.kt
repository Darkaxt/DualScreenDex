package com.darkaxt.dualdex.storage

import com.darkaxt.dualdex.retroarch.RomIndexEntry
import com.darkaxt.dualdex.retroarch.RomPlatform
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import com.enrpau.dualscreendex.parser.model.Platform
import java.io.File
import java.util.ArrayDeque

class DirectRomLibraryIndexer {
    fun index(roots: List<File>): RomLibraryIndexResult {
        val entries = mutableListOf<RomIndexEntry>()
        val warnings = mutableListOf<String>()
        discoverSources(roots).forEach { source ->
            runCatching {
                val loaded = RomSourceLoader.load(source.toPath())
                val header = RomHeaderReader.read(loaded.rom)
                val platform = when (header.platform) {
                    Platform.GB -> RomPlatform.GB
                    Platform.GBC -> RomPlatform.GBC
                    Platform.GBA -> RomPlatform.GBA
                    Platform.UNKNOWN -> error("ROM header platform was not recognized")
                }
                val entryName = loaded.displayName.substringAfter('!', loaded.displayName)
                entries += RomIndexEntry(
                    sourceId = source.toURI().normalize().toString(),
                    sourceName = loaded.displayName,
                    archiveEntry = loaded.displayName.substringAfter('!', "").ifBlank { null },
                    platform = platform,
                    gameBasename = entryName.substringBeforeLast('.', entryName),
                    crc32 = loaded.rom.crc32,
                    sha256 = loaded.rom.sha256,
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
