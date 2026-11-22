package com.darkaxt.dualdex.progress

import com.enrpau.dualscreendex.companion.semantic.GameEvent
import com.enrpau.dualscreendex.companion.semantic.PlaythroughKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaythroughJournalRegistryTest {
    @Test
    fun `isolates journals by exact ROM save identity`() {
        val first = PlaythroughKey("a".repeat(64), "b".repeat(64))
        val second = PlaythroughKey("a".repeat(64), "c".repeat(64))
        val registry = PlaythroughJournalRegistry(clock = { 10 })

        registry.accept(first, listOf(GameEvent.Captured(25)))
        registry.accept(second, listOf(GameEvent.Captured(133)))

        assertEquals(setOf(25), registry.current(first).capturedDexNumbers)
        assertEquals(setOf(133), registry.current(second).capturedDexNumbers)
        assertTrue(registry.restore(PlaythroughJournal.empty(first).copy(trackedCounts = mapOf("battles" to 4))))
        assertEquals(4L, registry.current(first).trackedCounts["battles"])
        assertEquals(setOf(133), registry.current(second).capturedDexNumbers)
    }
}
