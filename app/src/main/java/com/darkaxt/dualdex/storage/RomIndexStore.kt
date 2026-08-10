package com.darkaxt.dualdex.storage

import com.darkaxt.dualdex.retroarch.RomIndexEntry
import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class StoredRomLibraryIndex(
    val rootUri: String,
    val entries: List<RomIndexEntry>,
)

class RomIndexStore(
    private val file: File,
    private val gson: Gson = Gson(),
) {
    fun read(rootUri: String): List<RomIndexEntry> = runCatching {
        gson.fromJson(file.readText(), StoredRomLibraryIndex::class.java)
            ?.takeIf { it.rootUri == rootUri }
            ?.entries
            .orEmpty()
    }.getOrDefault(emptyList())

    fun write(rootUri: String, entries: List<RomIndexEntry>) {
        file.parentFile?.mkdirs()
        val pending = File(file.parentFile, "${file.name}.pending")
        pending.writeText(gson.toJson(StoredRomLibraryIndex(rootUri, entries)))
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
    }
}
