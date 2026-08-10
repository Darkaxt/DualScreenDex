package com.darkaxt.dualdex.mapper

import com.google.gson.GsonBuilder
import java.io.File
import java.nio.file.StandardCopyOption

class MapperSessionStore(private val directory: File) {
    private val gson = GsonBuilder().serializeNulls().create()

    init {
        require(directory.exists() || directory.mkdirs()) { "mapper session directory could not be created" }
        require(directory.isDirectory) { "mapper session path is not a directory" }
    }

    @Synchronized
    fun write(record: MapperSessionRecord) {
        val target = fileFor(record.id)
        val temporary = File(directory, ".${record.id}.tmp")
        temporary.writeText(gson.toJson(MapperExport.create(record, includeRaw = true, privacyAcknowledged = true)))
        java.nio.file.Files.move(
            temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
        )
    }

    @Synchronized
    fun clear(): Int {
        val root = directory.canonicalFile
        val files = directory.listFiles { file -> SESSION_FILE.matches(file.name) }.orEmpty()
        files.forEach { file ->
            check(file.canonicalFile.parentFile == root) { "refusing to clear a mapper session outside its namespace" }
            check(file.delete() || !file.exists()) { "mapper session could not be removed" }
        }
        return files.count { !it.exists() }
    }

    private fun fileFor(id: String): File {
        require(id.matches(ID)) { "mapper session id is invalid" }
        return File(directory, "$id.mapper.json")
    }

    private companion object {
        val ID = Regex("[A-Za-z0-9_-]{1,80}")
        val SESSION_FILE = Regex("(?:[A-Za-z0-9_-]{1,80}\\.mapper\\.json|\\.[A-Za-z0-9_-]{1,80}\\.tmp)")
    }
}
