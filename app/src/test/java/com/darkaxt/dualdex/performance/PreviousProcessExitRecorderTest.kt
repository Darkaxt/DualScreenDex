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
                    override fun write(value: String) { marker = value }
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
    fun `records no event when platform history is unavailable`() {
        val events = mutableListOf<PreviousProcessExitEvent>()
        val recorder = PreviousProcessExitRecorder(
            source = PreviousProcessExitSource { null },
            marker = object : PreviousProcessExitMarker {
                override fun read(): String? = null
                override fun write(value: String) = error("must not write")
            },
            sink = PreviousProcessExitSink(events::add),
        )

        assertNull(recorder.recordLatest())
        assertTrue(events.isEmpty())
    }
}
