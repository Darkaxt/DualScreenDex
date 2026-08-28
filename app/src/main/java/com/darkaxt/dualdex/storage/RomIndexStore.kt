package com.darkaxt.dualdex.storage

import com.darkaxt.dualdex.retroarch.RomIndexEntry
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class StoredRomLibraryIndex(
    val rootUri: String,
    val entries: List<RomIndexEntry>,
    val revision: Long = 0L,
)

class RomIndexStore(
    private val file: File,
    private val gson: Gson = Gson(),
) {
    @Synchronized
    fun read(rootUri: String): List<RomIndexEntry> = readActive()
        ?.takeIf { it.rootUri == rootUri }
        ?.entries
        .orEmpty()

    @Synchronized
    fun readActive(): StoredRomLibraryIndex? = runCatching {
        gson.fromJson(file.readText(), StoredRomLibraryIndex::class.java)
    }.getOrNull()

    @Synchronized
    fun write(rootUri: String, entries: List<RomIndexEntry>): StoredRomLibraryIndex {
        val previousRevision = readActive()?.revision ?: 0L
        require(previousRevision < Long.MAX_VALUE) { "ROM index revision overflowed" }
        val snapshot = StoredRomLibraryIndex(
            rootUri = rootUri,
            entries = entries,
            revision = previousRevision + 1L,
        )
        file.parentFile?.mkdirs()
        val pending = File(file.parentFile, "${file.name}.pending")
        FileOutputStream(pending).use { output ->
            output.write(gson.toJson(snapshot).toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        try {
            Files.move(
                pending.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: Exception) {
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return snapshot
    }
}
