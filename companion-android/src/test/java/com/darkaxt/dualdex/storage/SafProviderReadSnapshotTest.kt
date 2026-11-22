package com.darkaxt.dualdex.storage

import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import com.enrpau.dualscreendex.parser.model.Platform
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class SafProviderReadSnapshotTest {
    @Test
    fun `uncached ROM reads can use the same provider after its directory cursor closes`() {
        SafProviderOperationSupervisor(timeoutMillis = 1_000).use { provider ->
            val caller = Thread.currentThread()
            val events = mutableListOf<String>()
            val platforms = mutableListOf<Platform>()
            var cursorOpen = false
            provider.visitReadSnapshot(
                snapshot = {
                    assertNotSame(caller, Thread.currentThread())
                    cursorOpen = true
                    try {
                        events += "query"
                        listOf("game.gba", "other.gba")
                    } finally {
                        cursorOpen = false
                        events += "close"
                    }
                },
            ) { name ->
                // This is the uncached indexer's query -> callback -> open-file call chain.
                val loaded = provider.await {
                    events += "open:$name"
                    RomSourceLoader.load(name, ByteArrayInputStream(syntheticGba()))
                }
                assertSame(caller, Thread.currentThread())
                assertFalse(cursorOpen)
                platforms += RomHeaderReader.read(loaded.rom).platform
            }
            assertEquals(listOf(Platform.GBA, Platform.GBA), platforms)
            assertEquals(listOf("query", "close", "open:game.gba", "open:other.gba"), events)
            assertEquals("healthy", provider.await { "healthy" })
        }
    }

    @Test
    fun `empty directory finishes without invoking a visitor`() {
        SafProviderOperationSupervisor().use { provider ->
            provider.visitReadSnapshot(snapshot = { emptyList<String>() }) { fail("empty directory") }
            assertEquals(7, provider.await { 7 })
        }
    }

    @Test
    fun `failed snapshot does not publish partial files or poison a healthy provider`() {
        SafProviderOperationSupervisor().use { provider ->
            val failure = SafProviderFailure("directory listing failed")
            var visits = 0
            val result = runCatching {
                provider.visitReadSnapshot<String>(snapshot = { throw failure }) { visits++ }
            }
            assertSame(failure, result.exceptionOrNull())
            assertEquals(0, visits)
            assertEquals("retry", provider.await { "retry" })
        }
    }

    @Test
    fun `visitor failure stops later callbacks without retiring the provider worker`() {
        SafProviderOperationSupervisor().use { provider ->
            val failure = IllegalArgumentException("invalid source")
            val visited = mutableListOf<Int>()
            val result = runCatching {
                provider.visitReadSnapshot(snapshot = { listOf(1, 2) }) {
                    visited += it
                    throw failure
                }
            }
            assertSame(failure, result.exceptionOrNull())
            assertEquals(listOf(1), visited)
            assertEquals("retry", provider.await { "retry" })
        }
    }

    @Test
    fun `file quota is enforced while constructing the snapshot before any callback`() {
        SafProviderOperationSupervisor().use { provider ->
            val budget = StorageTraversalBudget(StorageTraversalQuota(maximumFiles = 1))
            var visits = 0
            val result = runCatching {
                provider.visitReadSnapshot(
                    snapshot = {
                        buildList {
                            repeat(2) {
                                budget.visitFile()
                                add(it)
                            }
                        }
                    },
                ) { visits++ }
            }
            assertTrue(result.exceptionOrNull() is StorageTraversalLimitExceeded)
            assertEquals(0, visits)
        }
    }

    @Test
    fun `directory quota is enforced while constructing the snapshot before any callback`() {
        SafProviderOperationSupervisor().use { provider ->
            val budget = StorageTraversalBudget(StorageTraversalQuota(maximumNodes = 1))
            var visits = 0
            val result = runCatching {
                provider.visitReadSnapshot(
                    snapshot = {
                        buildList {
                            repeat(2) {
                                budget.enqueueNode()
                                add(it)
                            }
                        }
                    },
                ) { visits++ }
            }
            assertTrue(result.exceptionOrNull() is StorageTraversalLimitExceeded)
            assertEquals(0, visits)
        }
    }

    @Test
    fun `timed-out snapshot cannot invoke visitors after its provider finally returns`() {
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val visits = AtomicInteger()
        var cancelled = false
        SafProviderOperationSupervisor(timeoutMillis = 25).use { provider ->
            try {
                val result = runCatching {
                    provider.visitReadSnapshot(
                        onTimeout = { cancelled = true },
                        snapshot = {
                            try {
                                while (release.count > 0L) {
                                    try {
                                        release.await(5, TimeUnit.SECONDS)
                                    } catch (_: InterruptedException) {
                                        // A provider may ignore cancellation until its read completes.
                                    }
                                }
                                listOf("late.gba")
                            } finally {
                                finished.countDown()
                            }
                        },
                    ) { visits.incrementAndGet() }
                }
                assertTrue(result.exceptionOrNull() is SafProviderOperationTimeout)
                assertTrue(cancelled)
                release.countDown()
                assertTrue(finished.await(1, TimeUnit.SECONDS))
                assertEquals(0, visits.get())
                assertEquals("retry", provider.await { "retry" })
            } finally {
                release.countDown()
            }
        }
    }

    private fun syntheticGba() = ByteArray(0xc0).also { bytes ->
        byteArrayOf(0x24, 0xff.toByte(), 0xae.toByte(), 0x51, 0x69, 0x9a.toByte(),
            0xa2.toByte(), 0x21, 0x3d, 0x84.toByte(), 0x82.toByte(), 0x0a).copyInto(bytes, 4)
        "INDEX TEST".toByteArray().copyInto(bytes, 0xa0)
        "BPRS".toByteArray().copyInto(bytes, 0xac)
    }
}
