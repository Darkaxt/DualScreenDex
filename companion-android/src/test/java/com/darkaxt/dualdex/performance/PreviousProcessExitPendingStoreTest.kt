package com.darkaxt.dualdex.performance

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviousProcessExitPendingStoreTest {
    private val roots = mutableListOf<File>()

    @After
    fun cleanUp() {
        roots.forEach(File::deleteRecursively)
    }

    @Test
    fun `failed pending publish is invisible to same process and restart and records one event after retry`() {
        val root = Files.createTempDirectory("dualdex-pending-exit-").toFile().also(roots::add)
        val pendingFile = File(root, "pending-exit")
        var durable = false
        val failedStore = PreviousProcessExitPendingStore(pendingFile) { target, bytes ->
            if (!durable) {
                false
            } else {
                runCatching {
                    target.writeBytes(bytes)
                    true
                }.getOrDefault(false)
            }
        }
        val pending = PreviousProcessExitPending("private-source-marker", "opaque-id")

        assertFalse(failedStore.write(pending))
        assertNull(failedStore.read())
        assertNull(PreviousProcessExitPendingStore(pendingFile).read())

        val events = mutableListOf<PreviousProcessExitEvent>()
        var completed: String? = null
        var completionDurable = false
        val source = PreviousProcessExitSource {
            PreviousProcessExitSnapshot(
                category = PreviousProcessExitCategory.CRASH,
                timestampEpochMillis = 1_725_123_456_789L,
                pssKilobytes = 100_000L,
                rssKilobytes = 100_000L,
            )
        }
        val marker = object : PreviousProcessExitMarker {
            override fun read(): String? = completed
            override fun readPending(): PreviousProcessExitPending? = failedStore.read()
            override fun writePending(value: PreviousProcessExitPending): Boolean = failedStore.write(value)
            override fun write(value: String): Boolean {
                if (!completionDurable) return false
                completed = value
                return failedStore.clear()
            }
        }
        val sink = PreviousProcessExitSink { event ->
            if (events.none { it.dedupeId == event.dedupeId }) events += event
            true
        }

        assertNull(PreviousProcessExitRecorder(source, marker, sink).recordLatest())
        assertTrue(events.isEmpty())
        durable = true
        assertNull(PreviousProcessExitRecorder(source, marker, sink).recordLatest())
        val persisted = requireNotNull(failedStore.read())
        assertEquals(1, events.size)
        assertEquals(persisted.id, events.single().dedupeId)
        completionDurable = true
        assertTrue(PreviousProcessExitRecorder(source, marker, sink).recordLatest() != null)
        assertEquals(1, events.size)

        val restartedMarker = object : PreviousProcessExitMarker {
            override fun read(): String? = completed
            override fun readPending(): PreviousProcessExitPending? = PreviousProcessExitPendingStore(pendingFile).read()
            override fun writePending(value: PreviousProcessExitPending): Boolean =
                PreviousProcessExitPendingStore(pendingFile).write(value)
            override fun write(value: String): Boolean {
                completed = value
                return PreviousProcessExitPendingStore(pendingFile).clear()
            }
        }
        assertNull(PreviousProcessExitRecorder(source, restartedMarker, sink).recordLatest())
        assertEquals(1, events.size)
    }
}
