package com.darkaxt.dualdex.save

import com.darkaxt.dualdex.retroarch.RomIndexEntry
import java.io.File
import java.net.URI
import java.util.ArrayDeque

object DirectSaveDocumentResolver {
    fun discover(entry: RomIndexEntry, directories: List<File>): List<SaveDocumentSource> {
        val documents = mutableListOf<SaveDocumentSource>()
        val queue = ArrayDeque<File>().apply { directories.forEach(::addLast) }
        val visitedDirectories = mutableSetOf<String>()
        val visitedFiles = mutableSetOf<String>()
        while (queue.isNotEmpty()) {
            val candidate = runCatching { queue.removeFirst().canonicalFile }.getOrNull() ?: continue
            when {
                candidate.isDirectory && visitedDirectories.add(candidate.path) ->
                    candidate.listFiles().orEmpty().forEach(queue::addLast)
                candidate.isFile && candidate.extension.lowercase() in EXTENSIONS && visitedFiles.add(candidate.path) ->
                    documents += candidate.toSource()
            }
        }
        return SaveDocumentResolver.matching(entry, documents)
    }

    fun refresh(sources: List<SaveDocumentSource>): List<SaveDocumentSource> = sources.mapNotNull { source ->
        val uri = runCatching { URI(source.id) }.getOrNull()
        if (uri?.scheme?.equals("file", ignoreCase = true) != true) return@mapNotNull source
        runCatching { File(uri).canonicalFile }
            .getOrNull()
            ?.takeIf(File::isFile)
            ?.toSource()
    }

    private fun File.toSource() = SaveDocumentSource(
        id = toURI().normalize().toString(),
        displayPath = path,
        name = name,
        size = length(),
        lastModifiedEpochMs = lastModified(),
        read = { readBytes() },
    )

    private val EXTENSIONS = setOf("srm", "sav")
}
