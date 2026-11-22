package com.darkaxt.dualdex.performance

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviousProcessExitRecorderTest {
    @Test
    fun `records crash ANR and low-memory exits with only coarse local fields`() {
        val categories = listOf(
            PreviousProcessExitCategory.CRASH,
            PreviousProcessExitCategory.ANR,
            PreviousProcessExitCategory.LOW_MEMORY,
        )

        categories.forEach { category ->
            var marker: String? = null
            val events = mutableListOf<PreviousProcessExitEvent>()
            val recorder = PreviousProcessExitRecorder(
                source = PreviousProcessExitSource {
                    PreviousProcessExitSnapshot(
                        category = category,
                        timestampEpochMillis = 1_725_123_456_789L,
                        pssKilobytes = 123_456L,
                        rssKilobytes = 234_567L,
                        description = "ROM D:/private/game.gba sha256=${"a".repeat(64)}",
                        trace = "trainer=12345 money=3000 x=8 y=9 flags=255",
                    )
                },
                marker = object : PreviousProcessExitMarker {
                    override fun read(): String? = marker
                    override fun readPending(): PreviousProcessExitPending? = null
                    override fun writePending(value: PreviousProcessExitPending): Boolean = true
                    override fun write(value: String): Boolean {
                        marker = value
                        return true
                    }
                },
                sink = PreviousProcessExitSink(events::add),
            )

            val recorded = recorder.recordLatest()
            val encoded = Gson().toJson(recorded)

            assertEquals(category, recorded?.category)
            assertEquals("128_TO_255_MIB", recorded?.memoryBucket)
            assertFalse(encoded.contains("1725123456789"))
            assertFalse(encoded.contains("D:/private"))
            assertFalse(encoded.contains("a".repeat(64)))
            assertFalse(encoded.contains("12345"))
            assertFalse(encoded.contains("3000"))
            assertEquals(1, events.size)
            assertNull(recorder.recordLatest())
            assertEquals(1, events.size)
        }
    }

    @Test
    fun `distinct exits in the same coarse bucket are each recorded once`() {
        var timestamp = 1_725_123_456_789L
        var marker: String? = null
        val events = mutableListOf<PreviousProcessExitEvent>()
        val recorder = PreviousProcessExitRecorder(
            source = PreviousProcessExitSource {
                PreviousProcessExitSnapshot(
                    category = PreviousProcessExitCategory.CRASH,
                    timestampEpochMillis = timestamp,
                    pssKilobytes = 100_000L,
                    rssKilobytes = 100_000L,
                )
            },
            marker = object : PreviousProcessExitMarker {
                override fun read(): String? = marker
                override fun readPending(): PreviousProcessExitPending? = null
                override fun writePending(value: PreviousProcessExitPending): Boolean = true
                override fun write(value: String): Boolean {
                    marker = value
                    return true
                }
            },
            sink = PreviousProcessExitSink(events::add),
        )

        val first = requireNotNull(recorder.recordLatest())
        assertNull(recorder.recordLatest())
        timestamp += 1_000L
        val second = requireNotNull(recorder.recordLatest())
        assertNull(recorder.recordLatest())

        assertEquals(first.timestampBucket, second.timestampBucket)
        assertEquals(2, events.size)
        val exported = Gson().toJson(events)
        assertFalse(exported.contains("1725123456789"))
        assertFalse(exported.contains("1725123457789"))
    }

    @Test
    fun `retries an exit after append was not durable`() {
        var marker: String? = null
        var durable = false
        var appendAttempts = 0
        val recorder = PreviousProcessExitRecorder(
            source = PreviousProcessExitSource {
                PreviousProcessExitSnapshot(
                    category = PreviousProcessExitCategory.CRASH,
                    timestampEpochMillis = 1_725_123_456_789L,
                    pssKilobytes = 100_000L,
                    rssKilobytes = 100_000L,
                )
            },
            marker = object : PreviousProcessExitMarker {
                override fun read(): String? = marker
                override fun readPending(): PreviousProcessExitPending? = null
                override fun writePending(value: PreviousProcessExitPending): Boolean = true
                override fun write(value: String): Boolean {
                    marker = value
                    return true
                }
            },
            sink = PreviousProcessExitSink {
                appendAttempts++
                durable
            },
        )

        assertNull(recorder.recordLatest())
        assertNull(marker)
        durable = true
        assertTrue(recorder.recordLatest() != null)
        assertEquals(2, appendAttempts)
        assertTrue(marker != null)
    }

    @Test
    fun `replaying after a marker write interruption does not duplicate the durable event`() {
        var marker: String? = null
        var pending: PreviousProcessExitPending? = null
        var markerWrites = 0
        val events = mutableListOf<PreviousProcessExitEvent>()
        val recorder = PreviousProcessExitRecorder(
            source = PreviousProcessExitSource {
                PreviousProcessExitSnapshot(
                    category = PreviousProcessExitCategory.ANR,
                    timestampEpochMillis = 1_725_123_456_789L,
                    pssKilobytes = 100_000L,
                    rssKilobytes = 100_000L,
                )
            },
            marker = object : PreviousProcessExitMarker {
                override fun read(): String? = marker
                override fun readPending(): PreviousProcessExitPending? = pending
                override fun writePending(value: PreviousProcessExitPending): Boolean {
                    pending = value
                    return true
                }
                override fun write(value: String): Boolean {
                    markerWrites++
                    if (markerWrites == 1) return false
                    marker = value
                    pending = null
                    return true
                }
            },
            sink = PreviousProcessExitSink { event ->
                if (events.none { it.dedupeId == event.dedupeId }) events += event
                true
            },
        )

        assertNull(recorder.recordLatest())
        assertNull(marker)
        assertEquals(1, events.size)
        assertTrue(events.single().dedupeId.isNotBlank())
        assertTrue(recorder.recordLatest() != null)
        assertTrue(marker != null)
        assertEquals(1, events.size)
    }

    @Test
    fun `opaque pending crash ID is persisted before append and reused after marker interruption`() {
        var marker: String? = null
        var pending: PreviousProcessExitPending? = null
        var markerWrites = 0
        val events = mutableListOf<PreviousProcessExitEvent>()
        val recorder = PreviousProcessExitRecorder(
            source = PreviousProcessExitSource {
                PreviousProcessExitSnapshot(
                    category = PreviousProcessExitCategory.CRASH,
                    timestampEpochMillis = 1_725_123_456_789L,
                    pssKilobytes = 100_000L,
                    rssKilobytes = 100_000L,
                )
            },
            marker = object : PreviousProcessExitMarker {
                override fun read(): String? = marker
                override fun readPending(): PreviousProcessExitPending? = pending
                override fun writePending(value: PreviousProcessExitPending): Boolean {
                    pending = value
                    return true
                }
                override fun write(value: String): Boolean {
                    markerWrites++
                    if (markerWrites == 1) return false
                    marker = value
                    pending = null
                    return true
                }
            },
            sink = PreviousProcessExitSink { event ->
                if (events.none { it.dedupeId == event.dedupeId }) events += event
                true
            },
        )

        assertNull(recorder.recordLatest())
        val persisted = requireNotNull(pending)
        assertEquals(1, events.size)
        assertEquals(persisted.id, events.single().dedupeId)
        assertFalse(events.single().dedupeId.contains("1725123456789"))
        assertTrue(recorder.recordLatest() != null)
        assertEquals(1, events.size)
        assertNull(pending)
    }

    @Test
    fun `records no event when platform history is unavailable`() {
        val events = mutableListOf<PreviousProcessExitEvent>()
        val recorder = PreviousProcessExitRecorder(
            source = PreviousProcessExitSource { null },
            marker = object : PreviousProcessExitMarker {
                override fun read(): String? = null
                override fun readPending(): PreviousProcessExitPending? = error("must not read")
                override fun writePending(value: PreviousProcessExitPending): Boolean = error("must not write")
                override fun write(value: String) = error("must not write")
            },
            sink = PreviousProcessExitSink(events::add),
        )

        assertNull(recorder.recordLatest())
        assertTrue(events.isEmpty())
    }
}
