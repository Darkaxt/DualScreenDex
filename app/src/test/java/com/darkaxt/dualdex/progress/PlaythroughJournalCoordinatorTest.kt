package com.darkaxt.dualdex.progress

import com.enrpau.dualscreendex.companion.semantic.GameEvent
import com.enrpau.dualscreendex.companion.semantic.IndividualFact
import com.enrpau.dualscreendex.companion.semantic.PlaythroughKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaythroughJournalCoordinatorTest {
    private val key = PlaythroughKey("a".repeat(64), "b".repeat(64))

    @Test
    fun `records only durable historical facts and one entry per changed save`() {
        val coordinator = PlaythroughJournalCoordinator(key, clock = { 1234 })
        coordinator.accept(
            listOf(
                GameEvent.Captured(25),
                GameEvent.Evolved("party:0", 172, 25),
                GameEvent.AreaVisited(0x0102),
                GameEvent.PoiDiscovered("item:7"),
                GameEvent.BattleStarted(7, "WILD"),
                GameEvent.PartyChanged(listOf(IndividualFact("party:0", 25))),
                GameEvent.SaveObserved("c".repeat(64)),
            ),
        )

        val journal = coordinator.current()
        assertEquals(setOf(25), journal.capturedDexNumbers)
        assertEquals(setOf(0x0102), journal.visitedAreaIds)
        assertEquals(setOf("item:7"), journal.discoveredPoiIds)
        assertEquals(1L, journal.trackedCounts["captures"])
        assertEquals(1L, journal.trackedCounts["evolutions"])
        assertEquals(1L, journal.trackedCounts["battles"])
        assertEquals(1, journal.timeline.size)
        assertEquals("c".repeat(64), journal.timeline.single().saveFingerprint)
        assertEquals(1L, journal.timeline.single().deltas["captures"])

        coordinator.accept(listOf(GameEvent.SaveObserved("c".repeat(64))))
        assertEquals(1, coordinator.current().timeline.size)
    }

    @Test
    fun `restore rejects another playthrough and sanitizes malformed historical values`() {
        val coordinator = PlaythroughJournalCoordinator(key)
        val foreign = PlaythroughJournal(
            playthrough = PlaythroughKey("d".repeat(64), "e".repeat(64)),
            trackedCounts = mapOf("captures" to -4),
            capturedDexNumbers = setOf(-1, 25),
            discoveredPoiIds = setOf("", "poi"),
        )

        assertTrue(!coordinator.restore(foreign))
        assertEquals(PlaythroughJournal.empty(key), coordinator.current())

        val local = foreign.copy(playthrough = key)
        assertTrue(coordinator.restore(local))
        assertEquals(mapOf("captures" to 0L), coordinator.current().trackedCounts)
        assertEquals(setOf(25), coordinator.current().capturedDexNumbers)
        assertEquals(setOf("poi"), coordinator.current().discoveredPoiIds)
    }

    @Test
    fun `timeline compaction is deterministic bounded and milestone preserving`() {
        val entries = (0..599).map { index ->
            TimelineEntry(
                saveFingerprint = index.toString(16).padStart(64, '0'),
                recordedAtEpochMs = index.toLong(),
                deltas = mapOf("captures" to 1L),
                milestone = index == 100 || index == 200,
            )
        }
        val journal = PlaythroughJournal.empty(key).copy(timeline = entries)

        val compacted = journal.sanitizedAndCompacted()

        assertEquals(512, compacted.timeline.size)
        assertEquals(entries.first(), compacted.timeline.first())
        assertTrue(compacted.timeline.contains(entries[100]))
        assertTrue(compacted.timeline.contains(entries[200]))
        assertEquals(compacted, journal.sanitizedAndCompacted())
    }
}
