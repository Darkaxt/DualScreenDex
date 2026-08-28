package com.darkaxt.dualdex.performance

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

sealed interface PerformanceLogExport {
    data class Available(val bytes: ByteArray) : PerformanceLogExport
    data object Unavailable : PerformanceLogExport
}

class AndroidPerformanceLog(
    private val directory: File,
    private val maximumSegmentBytes: Int = DEFAULT_SEGMENT_BYTES,
    private val gson: Gson = Gson(),
) : PerformanceEventSink {
    private val contractReady: Boolean

    init {
        require(maximumSegmentBytes >= MINIMUM_SEGMENT_BYTES) { "performance log segment is too small" }
        contractReady = runCatching {
            require(directory.isDirectory || (!directory.exists() && directory.mkdirs())) {
                "performance log directory could not be created"
            }
            prepareDiagnosticContract()
        }.getOrDefault(false)
    }

    @Synchronized
    override fun append(event: PerformanceEvent): Boolean = appendEncoded(event)

    @Synchronized
    fun append(event: PreviousProcessExitEvent): Boolean = appendEncoded(event)

    private fun appendEncoded(event: Any): Boolean {
        if (!contractReady) return false
        return try {
            val encoded = (gson.toJson(event) + "\n").toByteArray(Charsets.UTF_8)
            if (encoded.size > maximumSegmentBytes) return false
            if (event is PreviousProcessExitEvent && event.dedupeId.isNotBlank() && contains(event.dedupeId)) return true
            val active = File(directory, ACTIVE_FILE_NAME)
            if (active.length() + encoded.size > maximumSegmentBytes && !rotate(active)) return false
            FileOutputStream(active, true).use { output ->
                output.write(encoded)
                output.flush()
                output.fd.sync()
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun contains(dedupeId: String): Boolean =
        listOf(PREVIOUS_FILE_NAME, ACTIVE_FILE_NAME).any { name ->
            completeLines(File(directory, name)).any { line ->
                previousExitDedupeId(line) == dedupeId
            }
        }

    private fun completeLines(segment: File): List<String> {
        if (!segment.exists()) return emptyList()
        require(segment.isFile) { "diagnostic segment is not a file" }
        val bytes = segment.readBytes()
        val lastNewline = bytes.indexOfLast { byte -> byte == '\n'.code.toByte() }
        val completeBytes = if (lastNewline == bytes.lastIndex) bytes else bytes.copyOf(lastNewline + 1)
        if (completeBytes.size != bytes.size) {
            FileOutputStream(segment, false).use { output ->
                output.write(completeBytes)
                output.flush()
                output.fd.sync()
            }
        }
        return completeBytes.toString(Charsets.UTF_8).lineSequence().filter(String::isNotEmpty).toList()
    }

    private fun previousExitDedupeId(line: String): String? = runCatching {
        val objectRecord = JsonParser.parseString(line).asJsonObject
        val requiredFields = listOf("schemaVersion", "category", "timestampBucket", "memoryBucket", "dedupeId")
        require(requiredFields.all { name -> objectRecord.has(name) && !objectRecord[name].isJsonNull }) {
            "not a previous-exit record"
        }
        gson.fromJson(line, PreviousProcessExitEvent::class.java).dedupeId.takeIf(String::isNotBlank)
    }.getOrNull()

    @Synchronized
    fun export(): PerformanceLogExport {
        if (!contractReady) return PerformanceLogExport.Unavailable
        return try {
            PerformanceLogExport.Available(readSegment(PREVIOUS_FILE_NAME) + readSegment(ACTIVE_FILE_NAME))
        } catch (_: Throwable) {
            PerformanceLogExport.Unavailable
        }
    }

    private fun readSegment(name: String): ByteArray {
        val segment = File(directory, name)
        if (!segment.exists()) return ByteArray(0)
        require(segment.isFile) { "diagnostic segment is not a file" }
        return segment.readBytes()
    }

    private fun prepareDiagnosticContract(): Boolean = runCatching {
        val marker = File(directory, CONTRACT_FILE_NAME)
        if (marker.isFile && marker.readText() == DIAGNOSTIC_CONTRACT_VERSION.toString()) {
            return@runCatching true
        }
        listOf(ACTIVE_FILE_NAME, PREVIOUS_FILE_NAME).forEach { name ->
            val legacy = File(directory, name)
            check(!legacy.exists() || legacy.delete()) { "legacy diagnostic segment could not be removed" }
        }
        val temporary = File(directory, "$CONTRACT_FILE_NAME.tmp")
        temporary.writeText(DIAGNOSTIC_CONTRACT_VERSION.toString())
        try {
            Files.move(
                temporary.toPath(),
                marker.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), marker.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        true
    }.getOrDefault(false)

    private fun rotate(active: File): Boolean {
        val previous = File(directory, PREVIOUS_FILE_NAME)
        if (previous.exists() && !previous.delete()) return false
        if (active.isFile) {
            Files.move(active.toPath(), previous.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return true
    }

    companion object {
        const val ACTIVE_FILE_NAME = "performance.ndjson"
        const val PREVIOUS_FILE_NAME = "performance.previous.ndjson"
        const val CONTRACT_FILE_NAME = "diagnostics.contract"
        const val DEFAULT_SEGMENT_BYTES = 512 * 1024
        private const val DIAGNOSTIC_CONTRACT_VERSION = 3
        private const val MINIMUM_SEGMENT_BYTES = 512
    }
}
