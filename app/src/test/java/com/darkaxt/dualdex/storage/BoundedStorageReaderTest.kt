package com.darkaxt.dualdex.storage

import com.darkaxt.dualdex.retroarch.ConfigRecoveryRecord
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedStorageReaderTest {
    @Test
    fun `stops an endless stream one byte past the configured limit`() {
        val stream = EndlessInputStream()

        val result = runCatching { BoundedStorageReader.read(stream, maximumBytes = 64, reportedSize = null) }

        assertTrue(result.exceptionOrNull() is StorageReadLimitExceeded)
        assertEquals(65, stream.bytesServed)
    }

    @Test
    fun `rejects content above the limit when provider metadata understates its size`() {
        val result = runCatching {
            BoundedStorageReader.read(
                ByteArrayInputStream(ByteArray(65)),
                maximumBytes = 64,
                reportedSize = 1,
            )
        }

        assertTrue(result.exceptionOrNull() is StorageReadLimitExceeded)
    }

    @Test
    fun `accepts a maximum config recovery record through its serialized SAF bound`() {
        val record = ConfigRecoveryRecord(
            bytes = ByteArray(ConfigDocumentReadPolicy.MAXIMUM_BYTES),
            revision = Long.MAX_VALUE,
        ).serialize()

        assertTrue(record.size > ConfigDocumentReadPolicy.MAXIMUM_BYTES)
        assertTrue(record.size <= ConfigDocumentReadPolicy.MAXIMUM_RECOVERY_RECORD_BYTES)
        assertEquals(
            record.size,
            BoundedStorageReader.read(
                input = ByteArrayInputStream(record),
                maximumBytes = ConfigDocumentReadPolicy.MAXIMUM_RECOVERY_RECORD_BYTES,
                reportedSize = record.size.toLong(),
            ).size,
        )
    }

    private class EndlessInputStream : InputStream() {
        var bytesServed = 0
            private set

        override fun read(): Int {
            bytesServed++
            return 0
        }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            bytesServed += length
            bytes.fill(0, offset, offset + length)
            return length
        }
    }
}
