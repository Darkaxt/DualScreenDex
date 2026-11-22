package com.darkaxt.dualdex.storage

import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafBoundedReadTest {
    @Test
    fun `blocked SaveRAM read times out and leaves the retry execution lane free`() {
        val release = CountDownLatch(1)
        val supervisor = SafProviderOperationSupervisor(timeoutMillis = 25)
        try {
            val failure = runCatching {
                SafBoundedRead.read(supervisor, maximumBytes = 128) {
                    BoundedStorageReader.read(BlockingInputStream(release), maximumBytes = 128)
                }
            }.exceptionOrNull()

            assertTrue("expected timeout but was $failure", failure is SafProviderOperationTimeout)
            assertEquals(
                byteArrayOf(7).toList(),
                SafBoundedRead.read(supervisor, maximumBytes = 128) { byteArrayOf(7) }.toList(),
            )
        } finally {
            release.countDown()
            supervisor.close()
        }
    }

    private class BlockingInputStream(
        private val release: CountDownLatch,
    ) : InputStream() {
        override fun read(): Int {
            block()
            return -1
        }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            block()
            return -1
        }

        private fun block() {
            while (release.count > 0L) {
                try {
                    release.await(5, TimeUnit.SECONDS)
                } catch (_: InterruptedException) {
                    // A hostile provider ignores interruption until its own operation completes.
                }
            }
        }
    }
}
