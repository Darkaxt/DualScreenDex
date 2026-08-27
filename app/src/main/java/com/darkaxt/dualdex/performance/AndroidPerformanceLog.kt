package com.darkaxt.dualdex.performance

import com.google.gson.Gson
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class AndroidPerformanceLog(
    private val directory: File,
    private val maximumSegmentBytes: Int = DEFAULT_SEGMENT_BYTES,
    private val gson: Gson = Gson(),
) : PerformanceEventSink {
    private val contractReady: Boolean

    init {
        require(maximumSegmentBytes >= MINIMUM_SEGMENT_BYTES) { "performance log segment is too small" }
        require(directory.exists() || directory.mkdirs()) { "performance log directory could not be created" }
        contractReady = prepareDiagnosticContract()
    }

    @Synchronized
    override fun append(event: PerformanceEvent) = appendEncoded(event)

    @Synchronized
    fun append(event: PreviousProcessExitEvent) = appendEncoded(event)

    private fun appendEncoded(event: Any) {
        if (!contractReady) return
        try {
            val encoded = (gson.toJson(event) + "\n").toByteArray(Charsets.UTF_8)
            if (encoded.size > maximumSegmentBytes) return
            val active = File(directory, ACTIVE_FILE_NAME)
            if (active.length() + encoded.size > maximumSegmentBytes && !rotate(active)) return
            active.appendBytes(encoded)
        } catch (_: Exception) {
            return
        }
    }

    @Synchronized
    fun export(): ByteArray {
        if (!contractReady) return ByteArray(0)
        return try {
            val previous = File(directory, PREVIOUS_FILE_NAME).takeIf(File::isFile)?.readBytes() ?: ByteArray(0)
            val active = File(directory, ACTIVE_FILE_NAME).takeIf(File::isFile)?.readBytes() ?: ByteArray(0)
            previous + active
        } catch (_: Exception) {
            ByteArray(0)
        }
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
