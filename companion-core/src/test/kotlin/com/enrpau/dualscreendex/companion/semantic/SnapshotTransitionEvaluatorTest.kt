package com.enrpau.dualscreendex.companion.semantic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotTransitionEvaluatorTest {
    private val key = PlaythroughKey("rom-a", "save-a")

    @Test
    fun `emits each supported transition once`() {
        val initial = facts(
            caught = setOf(1),
            individuals = listOf(IndividualFact("party:0", 1)),
            party = listOf(IndividualFact("party:0", 1)),
            area = 0x0101,
            pois = setOf("sign:1"),
            battle = BattleFact(active = false, epoch = 4),
            save = SaveObservationFact(SaveObservationType.UNCHANGED, "save-1"),
        )
        val current = facts(
            caught = setOf(1, 4),
            individuals = listOf(IndividualFact("party:0", 2), IndividualFact("party:1", 4)),
            party = listOf(IndividualFact("party:0", 2), IndividualFact("party:1", 4)),
            area = 0x0102,
            pois = setOf("sign:1", "item:7"),
            battle = BattleFact(active = true, epoch = 5, encounterKind = "WILD"),
            save = SaveObservationFact(SaveObservationType.CHANGED, "save-2"),
        )

        val result = SnapshotTransitionEvaluator.evaluate(initial, current)

        assertEquals(
            listOf(
                GameEvent.Captured(4),
                GameEvent.Evolved("party:0", 1, 2),
                GameEvent.AreaVisited(0x0102),
                GameEvent.PoiDiscovered("item:7"),
                GameEvent.BattleStarted(5, "WILD"),
                GameEvent.PartyChanged(listOf(IndividualFact("party:0", 2), IndividualFact("party:1", 4))),
                GameEvent.SaveObserved("save-2"),
            ),
            result.events,
        )

        assertTrue(SnapshotTransitionEvaluator.evaluate(result.baseline, current).events.isEmpty())
        val ended = SnapshotTransitionEvaluator.evaluate(
            result.baseline,
            current.copy(battle = SemanticValue.Available(BattleFact(active = false, epoch = 5))),
        )
        assertEquals(listOf(GameEvent.BattleEnded(5)), ended.events)
    }

    @Test
    fun `does not turn transient instability reconnects or authority changes into events`() {
        val accepted = facts(
            caught = (1..48).toSet(),
            individuals = listOf(IndividualFact("party:0", 1)),
            party = listOf(IndividualFact("party:0", 1)),
            area = 0x0101,
            battle = BattleFact(active = false, epoch = 1),
        )
        val transient = accepted.copy(
            caughtDexNumbers = SemanticValue.Unavailable,
            individuals = SemanticValue.Unavailable,
            party = SemanticValue.Unavailable,
            areaBaseId = SemanticValue.Unavailable,
            battle = SemanticValue.Unavailable,
        )

        val unavailable = SnapshotTransitionEvaluator.evaluate(accepted, transient)
        assertTrue(unavailable.events.isEmpty())
        val recovered = SnapshotTransitionEvaluator.evaluate(unavailable.baseline, accepted)
        assertTrue(recovered.events.isEmpty())

        // The previously observed 48/48 -> 1/1 -> 1/2 instability must not be
        // counted when the intermediate candidate has not been accepted.
        val rejectedCandidate = accepted.copy(caughtDexNumbers = SemanticValue.Unavailable)
        val held = SnapshotTransitionEvaluator.evaluate(recovered.baseline, rejectedCandidate)
        val acceptedNext = accepted.copy(caughtDexNumbers = SemanticValue.Available((1..48).toSet() + 49))
        assertEquals(
            listOf(GameEvent.Captured(49)),
            SnapshotTransitionEvaluator.evaluate(held.baseline, acceptedNext).events,
        )
    }

    @Test
    fun `resets baselines when ROM or save identity changes`() {
        val previous = facts(caught = setOf(1), area = 1)
        val switched = facts(caught = setOf(1, 2, 3), area = 4).copy(
            playthrough = PlaythroughKey("rom-a", "save-b"),
        )

        val result = SnapshotTransitionEvaluator.evaluate(previous, switched)

        assertTrue(result.events.isEmpty())
        assertEquals(switched, result.baseline)
    }

    @Test
    fun `changed save fingerprints are deduplicated and other observations are ignored`() {
        val initial = facts(save = SaveObservationFact(SaveObservationType.INITIAL, "a"))
        val unchanged = facts(save = SaveObservationFact(SaveObservationType.UNCHANGED, "a"))
        val changed = facts(save = SaveObservationFact(SaveObservationType.CHANGED, "b"))

        assertTrue(SnapshotTransitionEvaluator.evaluate(initial, unchanged).events.isEmpty())
        val first = SnapshotTransitionEvaluator.evaluate(unchanged, changed)
        assertEquals(listOf(GameEvent.SaveObserved("b")), first.events)
        assertTrue(SnapshotTransitionEvaluator.evaluate(first.baseline, changed).events.isEmpty())
    }

    private fun facts(
        caught: Set<Int> = emptySet(),
        individuals: List<IndividualFact> = emptyList(),
        party: List<IndividualFact> = emptyList(),
        area: Int? = null,
        pois: Set<String> = emptySet(),
        battle: BattleFact? = null,
        save: SaveObservationFact? = null,
    ) = SemanticFactSet(
        playthrough = key,
        caughtDexNumbers = SemanticValue.Available(caught),
        individuals = SemanticValue.Available(individuals),
        party = SemanticValue.Available(party),
        areaBaseId = area?.let { SemanticValue.Available(it) } ?: SemanticValue.Unavailable,
        discoveredPoiIds = SemanticValue.Available(pois),
        battle = battle?.let { SemanticValue.Available(it) } ?: SemanticValue.Unavailable,
        saveObservation = save?.let { SemanticValue.Available(it) } ?: SemanticValue.Unavailable,
    )
}
