package com.darkaxt.dualdex.progress

import com.enrpau.dualscreendex.companion.semantic.PlaythroughKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaythroughJournalCodecTest {
    private val key = PlaythroughKey("a".repeat(64), "b".repeat(64))
    private val codec = PlaythroughJournalCodec()

    @Test
    fun `round trips exact identity and rejects malformed or foreign journals`() {
        val journal = PlaythroughJournal.empty(key).copy(
            trackedCounts = mapOf("captures" to 2),
            capturedDexNumbers = setOf(25, 133),
            challengeStates = mapOf(
                "active-streak" to ChallengeJournalState(progress = 2, target = 3, paused = true),
                "missed-streak" to ChallengeJournalState(progress = 1, target = 3, missed = true),
            ),
            preferences = mapOf("trainer-progress-section" to "TIMELINE"),
        )
        val bytes = codec.encode(journal)

        assertEquals(journal, codec.decodeExact(bytes, key))
        assertNull(codec.decodeExact(bytes, PlaythroughKey("c".repeat(64), "b".repeat(64))))
        assertNull(codec.decodeExact("{}".toByteArray(), key))
    }
}
