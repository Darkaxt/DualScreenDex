package com.darkaxt.dualdex.catalog

import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal object CanonicalDatabaseWriteCoordinator {
    private val writers = ConcurrentHashMap<String, Writer>()

    fun <T> write(
        databaseFile: File,
        operation: () -> T,
    ): T {
        val key = canonicalKey(databaseFile)
        val writer = writers.compute(key) { _, existing ->
            (existing ?: Writer()).also { it.users++ }
        } ?: error("canonical database writer was unavailable")
        return try {
            writer.lock.withLock {
                runWithTransientLockRetry(operation)
            }
        } finally {
            writers.computeIfPresent(key) { _, current ->
                check(current === writer) { "canonical database writer changed while in use" }
                current.users--
                current.takeIf { it.users > 0 }
            }
        }
    }

    private fun <T> runWithTransientLockRetry(operation: () -> T): T {
        var retry = 0
        while (true) {
            try {
                return operation()
            } catch (failure: Exception) {
                if (!failure.isTransientSqliteLock() || retry == RETRY_DELAYS_MS.size) {
                    throw failure
                }
                try {
                    Thread.sleep(RETRY_DELAYS_MS[retry++])
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw interrupted
                }
            }
        }
    }

    private fun Exception.isTransientSqliteLock(): Boolean {
        var current: Throwable? = this
        repeat(MAX_CAUSE_DEPTH) {
            val failure = current ?: return false
            val className = failure.javaClass.simpleName
            val message = failure.message.orEmpty().lowercase(Locale.ROOT)
            if (
                className == "SQLiteDatabaseLockedException" ||
                className == "SQLiteBusyException" ||
                "sqlite_busy" in message ||
                "database is locked" in message ||
                "database table is locked" in message
            ) {
                return true
            }
            current = failure.cause
        }
        return false
    }

    private fun canonicalKey(file: File): String {
        val path = file.canonicalFile.path
        return if (File.separatorChar == '\\') path.lowercase(Locale.ROOT) else path
    }

    private class Writer(
        val lock: ReentrantLock = ReentrantLock(),
        var users: Int = 0,
    )

    private val RETRY_DELAYS_MS = longArrayOf(10, 25, 50)
    private const val MAX_CAUSE_DEPTH = 8
}
