package com.darkaxt.dualdex.storage

import java.io.ByteArrayOutputStream
import java.io.InputStream

class StorageReadLimitExceeded(message: String) : IllegalArgumentException(message)

object BoundedStorageReader {
    fun read(
        input: InputStream,
        maximumBytes: Int,
        reportedSize: Long? = null,
    ): ByteArray {
        require(maximumBytes >= 0) { "storage byte limit must not be negative" }
        if (reportedSize != null && reportedSize > maximumBytes) {
            throw StorageReadLimitExceeded("storage document exceeds the byte limit")
        }
        val output = ByteArrayOutputStream(minOf(maximumBytes, BUFFER_BYTES))
        val buffer = ByteArray(minOf(BUFFER_BYTES, maximumBytes + 1))
        var remaining = maximumBytes
        while (true) {
            val read = input.read(buffer, 0, minOf(buffer.size, remaining + 1))
            when {
                read < 0 -> return output.toByteArray()
                read == 0 -> {
                    val single = input.read()
                    if (single < 0) return output.toByteArray()
                    if (remaining == 0) throw StorageReadLimitExceeded("storage document exceeds the byte limit")
                    output.write(single)
                    remaining--
                }
                read > remaining -> throw StorageReadLimitExceeded("storage document exceeds the byte limit")
                else -> {
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
        }
    }

    private const val BUFFER_BYTES = 8 * 1024
}

object SafBoundedRead {
    fun read(
        supervisor: SafProviderOperationSupervisor,
        maximumBytes: Int,
        onTimeout: () -> Unit = {},
        operation: () -> ByteArray,
    ): ByteArray {
        require(maximumBytes >= 0) { "storage byte limit must not be negative" }
        return supervisor.await(
            kind = SafProviderOperationKind.READ_ONLY,
            onTimeout = onTimeout,
        ) {
            operation().also { bytes ->
                require(bytes.size <= maximumBytes) { "storage document exceeds the byte limit" }
            }
        }
    }
}

object ConfigDocumentReadPolicy {
    const val MAXIMUM_BYTES = 1 * 1024 * 1024
    private const val MAXIMUM_RECOVERY_FRAMING_BYTES = 512

    val MAXIMUM_RECOVERY_RECORD_BYTES: Int = recoveryRecordBytes(MAXIMUM_BYTES)

    private fun recoveryRecordBytes(contentBytes: Int): Int {
        val base64Bytes = ((contentBytes.toLong() + 2L) / 3L) * 4L
        val total = base64Bytes + MAXIMUM_RECOVERY_FRAMING_BYTES
        require(total <= Int.MAX_VALUE) { "recovery record byte limit overflows" }
        return total.toInt()
    }
}
