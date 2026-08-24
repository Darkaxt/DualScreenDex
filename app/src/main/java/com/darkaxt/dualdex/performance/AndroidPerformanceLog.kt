package com.darkaxt.dualdex.performance

import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class AndroidPerformanceLog(
    private val directory: File,
    private val maximumSegmentBytes: Int = DEFAULT_SEGMENT_BYTES,
    private val gson: Gson = Gson(),
) : PerformanceEventSink {
    init {
        require(maximumSegmentBytes >= MINIMUM_SEGMENT_BYTES) { "performance log segment is too small" }
        require(directory.exists() || directory.mkdirs()) { "performance log directory could not be created" }
    }

    @Synchronized
    override fun append(event: PerformanceEvent) {
        val encoded = (gson.toJson(event) + "\n").toByteArray(Charsets.UTF_8)
        if (encoded.size > maximumSegmentBytes) return
        val active = File(directory, ACTIVE_FILE_NAME)
        if (active.length() + encoded.size > maximumSegmentBytes && !rotate(active)) return
        active.appendBytes(encoded)
    }

    @Synchronized
    fun export(): ByteArray {
        val previous = File(directory, PREVIOUS_FILE_NAME).takeIf(File::isFile)?.readBytes() ?: ByteArray(0)
        val active = File(directory, ACTIVE_FILE_NAME).takeIf(File::isFile)?.readBytes() ?: ByteArray(0)
        return previous + active
    }

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
        const val DEFAULT_SEGMENT_BYTES = 512 * 1024
        private const val MINIMUM_SEGMENT_BYTES = 512
    }
}
