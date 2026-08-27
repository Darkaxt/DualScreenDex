package com.darkaxt.dualdex.catalog

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalDatabaseWriteCoordinatorTest {
    @Test
    fun canonicalAliasesShareOneWriterLock() {
        val directory = Files.createTempDirectory("dualdex-db-coordinator").toFile()
        val database = directory.resolve("catalog.sqlite")
        val alias = directory.resolve("nested/../catalog.sqlite")
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit {
                CanonicalDatabaseWriteCoordinator.write(database) {
                    firstEntered.countDown()
                    assertTrue(releaseFirst.await(2, TimeUnit.SECONDS))
                }
            }
            assertTrue(firstEntered.await(2, TimeUnit.SECONDS))
            val second = executor.submit {
                CanonicalDatabaseWriteCoordinator.write(alias) {
                    secondEntered.countDown()
                }
            }

            assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()
            first.get(2, TimeUnit.SECONDS)
            second.get(2, TimeUnit.SECONDS)
            assertTrue(secondEntered.await(0, TimeUnit.MILLISECONDS))
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun transientSqliteLockFailuresRetryWithinABound() {
        val database = Files.createTempFile("dualdex-db-retry", ".sqlite").toFile()
        val attempts = AtomicInteger()
        try {
            val result = CanonicalDatabaseWriteCoordinator.write(database) {
                if (attempts.incrementAndGet() < 3) {
                    throw IllegalStateException("[SQLITE_BUSY] database is locked")
                }
                "written"
            }

            assertEquals("written", result)
            assertEquals(3, attempts.get())
        } finally {
            database.delete()
        }
    }

    @Test
    fun nonLockFailuresAreNeverRetried() {
        val database = Files.createTempFile("dualdex-db-failure", ".sqlite").toFile()
        val attempts = AtomicInteger()
        val completed = AtomicBoolean()
        try {
            assertThrows(IllegalStateException::class.java) {
                CanonicalDatabaseWriteCoordinator.write(database) {
                    attempts.incrementAndGet()
                    throw IllegalStateException("disk full")
                }
                completed.set(true)
            }

            assertEquals(1, attempts.get())
            assertFalse(completed.get())
        } finally {
            database.delete()
        }
    }
}
