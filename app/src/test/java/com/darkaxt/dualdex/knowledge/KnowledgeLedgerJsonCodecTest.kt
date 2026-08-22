package com.darkaxt.dualdex.knowledge

import com.enrpau.dualscreendex.companion.model.Effectiveness
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.model.LocalMapPoiPreferences
import com.enrpau.dualscreendex.companion.model.MatchupKey
import com.enrpau.dualscreendex.companion.model.MoveObservation
import com.enrpau.dualscreendex.companion.model.OwnedPokemon
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class KnowledgeLedgerJsonCodecTest {
    private val codec = KnowledgeLedgerJsonCodec()

    @Test
    fun roundTripsEverySchemaSixField() {
        val ledger = completeLedgerFixture()

        assertEquals(ledger, codec.decode(codec.encode(ledger)))
    }

    @Test
    fun emitsStableBytesForEquivalentLedgerOrdering() {
        assertArrayEquals(
            codec.encode(completeLedgerFixture()),
            codec.encode(completeLedgerFixture(reverseInsertionOrder = true)),
        )
    }

    private fun completeLedgerFixture(reverseInsertionOrder: Boolean = false): KnowledgeLedger {
        fun <T> orderedSet(vararg values: T): Set<T> =
            (if (reverseInsertionOrder) values.reversed() else values.asList()).toCollection(linkedSetOf())

        fun <K, V> orderedMap(vararg values: Pair<K, V>): Map<K, V> =
            (if (reverseInsertionOrder) values.reversed() else values.asList()).toMap(linkedMapOf())

        return KnowledgeLedger(
            seenSpecies = orderedSet(25, 133),
            caughtSpecies = orderedSet(25, 7),
            owned = listOf(
                OwnedPokemon(
                    stableKey = "party:0",
                    speciesId = 25,
                    generation = 3,
                    level = 42,
                    ivs = listOf(31, 30, 29, 28, 27, 26),
                    dvs = listOf(15, 14, 13, 12),
                    captureBallId = 4,
                    isEgg = false,
                    party = true,
                ),
            ),
            teamSpecies = orderedSet(25, 7),
            currentAreaBaseId = 9,
            visitedAreaBaseIds = orderedSet(2, 9),
            seenSpeciesByArea = orderedMap(
                9 to orderedSet(25, 133),
                2 to orderedSet(7),
            ),
            observedMoves = orderedMap(
                133 to listOf(MoveObservation(33, 3), MoveObservation(39, 1)),
                25 to listOf(MoveObservation(84, 2)),
            ),
            discoveredMatchups = orderedMap(
                MatchupKey(133, 84) to Effectiveness.NEUTRAL,
                MatchupKey(25, 89) to Effectiveness.SUPER_EFFECTIVE,
            ),
            knownMoves = orderedSet(33, 39, 84, 89),
            proximityRevealedPoiKeys = orderedSet("local/0009/bg/1", "local/0002/bg/0"),
            identifiedPoiKeys = orderedSet("local/0009/warp/1", "local/0002/warp/0"),
            enteredPoiKeys = orderedSet("local/0009/warp/1", "local/0002/warp/0"),
            collectedPoiKeys = orderedSet("local/0009/object/1", "local/0002/object/0"),
            localMapPoiPreferences = LocalMapPoiPreferences(
                showPlaces = false,
                showServices = true,
                showAvailableItems = false,
                showCollectedItems = true,
                showUnknownPois = false,
                iconZoomThresholdPercent = 35,
                labelZoomThresholdPercent = 65,
            ),
            matchupEvidenceVersion = KnowledgeLedger.CURRENT_MATCHUP_EVIDENCE_VERSION,
        )
    }
}
