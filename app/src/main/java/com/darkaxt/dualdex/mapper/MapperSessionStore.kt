package com.darkaxt.dualdex.mapper

import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileOutputStream
import java.nio.file.StandardCopyOption

data class MapperStoreWriteResult(
    val appendedSnapshots: Int,
    val compacted: Boolean,
    val bytesWritten: Long,
)

class MapperSessionStore(private val directory: File) {
    private val gson = GsonBuilder().serializeNulls().create()
    private val persistedSnapshotIds = mutableMapOf<String, List<String>>()

    init {
        require(directory.exists() || directory.mkdirs()) { "mapper session directory could not be created" }
        require(directory.isDirectory) { "mapper session path is not a directory" }
    }

    @Synchronized
    fun write(record: MapperSessionRecord): MapperStoreWriteResult {
        require(record.snapshots.size <= MemoryMapperLab.MAX_SNAPSHOT_COUNT) { "mapper snapshot count exceeds the store limit" }
        require(record.snapshots.sumOf { snapshot -> snapshot.regions.sumOf { it.bytes.size.toLong() } } <= MemoryMapperLab.MAX_HISTORY_BYTES) {
            "mapper raw history exceeds the store byte limit"
        }
        val target = fileFor(record.id)
        val currentIds = record.snapshots.map(MemorySnapshot::id)
        val previousIds = persistedSnapshotIds[record.id]
        val compact = (target.exists() && previousIds == null) || previousIds?.any { it !in currentIds } == true
        if (compact) {
            val temporary = File(directory, ".${record.id}.tmp")
            writeSnapshots(temporary, record, record.snapshots, append = false)
            try {
                java.nio.file.Files.move(
                    temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                java.nio.file.Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            persistedSnapshotIds[record.id] = currentIds
            return MapperStoreWriteResult(appendedSnapshots = 0, compacted = true, bytesWritten = target.length())
        }

        val previousSet = previousIds.orEmpty().toHashSet()
        val additions = record.snapshots.filterNot { it.id in previousSet }
        val beforeBytes = target.length()
        writeSnapshots(target, record, additions, append = true)
        persistedSnapshotIds[record.id] = currentIds
        return MapperStoreWriteResult(
            appendedSnapshots = additions.size,
            compacted = false,
            bytesWritten = target.length() - beforeBytes,
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
        persistedSnapshotIds.clear()
        return files.count { !it.exists() }
    }

    private fun writeSnapshots(
        target: File,
        record: MapperSessionRecord,
        snapshots: List<MemorySnapshot>,
        append: Boolean,
    ) {
        FileOutputStream(target, append).bufferedWriter(Charsets.UTF_8).use { writer ->
            snapshots.forEach { snapshot ->
                val entry = MapperExport.create(
                    record.copy(snapshots = listOf(snapshot)),
                    includeRaw = true,
                    privacyAcknowledged = true,
                )
                gson.toJson(entry, writer)
                writer.newLine()
            }
        }
    }

    private fun fileFor(id: String): File {
        require(id.matches(ID)) { "mapper session id is invalid" }
        return File(directory, "$id.mapper.jsonl")
    }

    private companion object {
        val ID = Regex("[A-Za-z0-9_-]{1,80}")
        val SESSION_FILE = Regex("(?:[A-Za-z0-9_-]{1,80}\\.mapper\\.(?:json|jsonl)|\\.[A-Za-z0-9_-]{1,80}\\.tmp)")
    }
}
