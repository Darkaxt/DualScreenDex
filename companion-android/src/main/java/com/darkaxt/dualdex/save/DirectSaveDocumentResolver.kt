package com.darkaxt.dualdex.save

import com.darkaxt.dualdex.retroarch.RomIndexEntry
import com.darkaxt.dualdex.storage.BoundedStorageReader
import com.darkaxt.dualdex.storage.DirectFileTraversal
import com.darkaxt.dualdex.storage.StorageTraversalPolicy
import com.darkaxt.dualdex.storage.StorageTraversalQuota
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object DirectSaveDocumentResolver {
    fun discover(
        entry: RomIndexEntry,
        directories: List<File>,
        activeGameBasename: String? = null,
        traversalQuota: StorageTraversalQuota = StorageTraversalPolicy.DEFAULT,
    ): List<SaveDocumentSource> {
        val documents = mutableListOf<SaveDocumentSource>()
        DirectFileTraversal.visitFiles(directories, traversalQuota) { candidate, budget ->
            if (candidate.extension.lowercase() in EXTENSIONS) {
                budget.retainResult()
                documents += candidate.toSource()
            }
        }
        return SaveDocumentResolver.matching(entry, documents, activeGameBasename)
    }

    fun refresh(sources: List<SaveDocumentSource>): List<SaveDocumentSource> = sources.mapNotNull { source ->
        val uri = runCatching { URI(source.id) }.getOrNull()
        if (uri?.scheme?.equals("file", ignoreCase = true) != true) return@mapNotNull source
        runCatching { File(uri).canonicalFile }
            .getOrNull()
            ?.takeIf(File::isFile)
            ?.toSource()
    }

    private fun File.toSource(): SaveDocumentSource {
        val save = this
        return SaveDocumentSource(
            id = toURI().normalize().toString(),
            displayPath = path,
            name = name,
            size = length(),
            lastModifiedEpochMs = lastModified(),
            open = { inputStream() },
            atomicSiblingTarget = FileAtomicSiblingTarget(requireNotNull(save.parentFile)),
        )
    }

    private class FileAtomicSiblingTarget(parent: File) : AtomicSiblingTarget {
        private val directory = parent.canonicalFile

        override fun read(name: String): ByteArray? = resolve(name).takeIf(File::isFile)?.readBytes()

        override fun read(name: String, maximumBytes: Int): ByteArray? {
            require(maximumBytes > 0) { "sibling byte limit must be positive" }
            val source = resolve(name).takeIf(File::isFile) ?: return null
            require(source.length() in 0..maximumBytes.toLong()) { "sibling document exceeds the byte limit" }
            return source.inputStream().use { input ->
                BoundedStorageReader.read(input, maximumBytes, source.length())
            }
        }

        override fun replace(name: String, bytes: ByteArray) {
            val destination = resolve(name)
            val temporary = resolve(".$name.dualdex.tmp")
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(bytes)
                    output.fd.sync()
                }
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } finally {
                temporary.delete()
            }
        }

        private fun resolve(name: String): File {
            require(name.isNotBlank() && File(name).name == name && '/' !in name && '\\' !in name) {
                "sibling name must not contain a path"
            }
            return File(directory, name)
        }
    }

    private val EXTENSIONS = setOf("srm", "sav")
}
