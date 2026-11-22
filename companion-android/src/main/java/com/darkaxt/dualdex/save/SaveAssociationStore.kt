package com.darkaxt.dualdex.save

import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

interface SaveAssociationRepository {
    fun selectedFor(romSha256: String): String?
    fun remember(romSha256: String, documentId: String)
}

class SaveAssociationStore(
    private val file: File,
    private val gson: Gson = Gson(),
) : SaveAssociationRepository {
    private var cached: MutableMap<String, String>? = null

    @Synchronized
    override fun selectedFor(romSha256: String): String? = values()[romSha256.lowercase()]

    @Synchronized
    override fun remember(romSha256: String, documentId: String) {
        require(romSha256.matches(Regex("[0-9a-fA-F]{64}"))) { "ROM SHA-256 is invalid" }
        val updated = values().toMutableMap().apply { put(romSha256.lowercase(), documentId) }
        file.parentFile?.mkdirs()
        val pending = File(file.parentFile, "${file.name}.pending")
        pending.writeText(gson.toJson(updated), Charsets.UTF_8)
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
        cached = updated
    }

    private fun values(): MutableMap<String, String> = cached ?: readAll().toMutableMap().also { cached = it }

    private fun readAll(): Map<String, String> = runCatching {
        @Suppress("UNCHECKED_CAST")
        gson.fromJson(file.readText(Charsets.UTF_8), Map::class.java)
            ?.entries
            ?.associate { it.key.toString().lowercase() to it.value.toString() }
            .orEmpty()
    }.getOrDefault(emptyMap())
}
