package com.darkaxt.dualdex.storage

import android.content.ContentResolver
import android.net.Uri
import com.darkaxt.dualdex.retroarch.RomIndexEntry
import com.darkaxt.dualdex.retroarch.RomPlatform
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.model.Platform

data class RomLibraryIndexResult(
    val entries: List<RomIndexEntry>,
    val warnings: List<String>,
)

class AndroidRomLibraryIndexer(
    private val resolver: ContentResolver,
) {
    fun index(treeUri: Uri, previousEntries: List<RomIndexEntry> = emptyList()): RomLibraryIndexResult {
        val entries = mutableListOf<RomIndexEntry>()
        val warnings = mutableListOf<String>()
        val previousBySource = previousEntries.associateBy(RomIndexEntry::sourceId)
        DocumentTreeAccess(resolver, treeUri).filesRecursively()
            .filter { it.name.substringAfterLast('.', "").lowercase() in SUPPORTED_EXTENSIONS }
            .forEach { document ->
                runCatching {
                    val sourceId = document.uri.toString()
                    val previous = previousBySource[sourceId]
                        ?.takeIf {
                            it.sourceSize == document.size &&
                                it.sourceLastModifiedEpochMs == document.lastModifiedEpochMs
                        }
                    if (previous != null) {
                        entries += previous
                        return@runCatching
                    }
                    val loaded = AndroidRomSourceLoader.load(resolver, document.uri, document.name)
                    val header = RomHeaderReader.read(loaded.rom)
                    val platform = when (header.platform) {
                        Platform.GB -> RomPlatform.GB
                        Platform.GBC -> RomPlatform.GBC
                        Platform.GBA -> RomPlatform.GBA
                        Platform.UNKNOWN -> error("ROM header platform was not recognized")
                    }
                    val entryName = loaded.displayName.substringAfter('!', loaded.displayName)
                    entries += RomIndexEntry(
                        sourceId = sourceId,
                        sourceName = loaded.displayName,
                        archiveEntry = loaded.displayName.substringAfter('!', "").ifBlank { null },
                        platform = platform,
                        gameBasename = entryName.substringBeforeLast('.', entryName),
                        crc32 = loaded.rom.crc32,
                        sha256 = loaded.rom.sha256,
                        sourceSize = document.size,
                        sourceLastModifiedEpochMs = document.lastModifiedEpochMs,
                    )
                }.onFailure { warnings += "${document.name}: ${it.message ?: it.javaClass.simpleName}" }
            }
        return RomLibraryIndexResult(entries.sortedBy { it.sourceName.lowercase() }, warnings)
    }

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("gb", "gbc", "gba", "zip", "7z")
    }
}
