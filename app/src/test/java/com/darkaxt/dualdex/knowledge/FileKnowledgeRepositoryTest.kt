package com.darkaxt.dualdex.knowledge

import com.enrpau.dualscreendex.companion.model.Effectiveness
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.model.LocalMapPoiPreferences
import com.enrpau.dualscreendex.companion.model.MatchupKey
import com.enrpau.dualscreendex.companion.model.MoveObservation
import com.enrpau.dualscreendex.companion.model.OwnedPokemon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileKnowledgeRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun roundTripsKnowledgeInAPerSaveDocument() {
        val identity = "a".repeat(64)
        val saveIdentity = "1".repeat(64)
        val ledger = KnowledgeLedger(
            seenSpecies = setOf(25, 133),
            caughtSpecies = setOf(25),
            owned = listOf(
                OwnedPokemon("party:0", 25, 1, 6, dvs = listOf(9, 1, 15, 11), party = true),
            ),
            teamSpecies = setOf(25),
            currentAreaBaseId = 1,
            visitedAreaBaseIds = setOf(1, 2, 4),
            seenSpeciesByArea = mapOf(1 to setOf(25, 133), 2 to setOf(7)),
            observedMoves = mapOf(133 to listOf(MoveObservation(33, 3), MoveObservation(39, 1))),
            discoveredMatchups = mapOf(MatchupKey(133, 84) to Effectiveness.NEUTRAL),
            knownMoves = setOf(33, 39, 84),
            proximityRevealedPoiKeys = setOf("local/0001/bg/0"),
            identifiedPoiKeys = setOf("local/0001/warp/0"),
            enteredPoiKeys = setOf("local/0001/warp/0"),
            collectedPoiKeys = setOf("local/0001/object/0"),
            localMapPoiPreferences = LocalMapPoiPreferences(
                showPlaces = false,
                showServices = true,
                showAvailableItems = false,
                showCollectedItems = true,
                showUnknownPois = false,
                iconZoomThresholdPercent = 35,
                labelZoomThresholdPercent = 65,
            ),
        )
        val repository = FileKnowledgeRepository(temporary.newFolder("knowledge"))

        repository.write(identity, saveIdentity, ledger)

        assertEquals(ledger, repository.read(identity.uppercase(), saveIdentity.uppercase()))
        assertNull(repository.read(identity, "2".repeat(64)))
        assertNull(repository.read("b".repeat(64), saveIdentity))
    }

    @Test
    fun invalidOrMismatchedDocumentsAreIgnored() {
        val identity = "c".repeat(64)
        val saveIdentity = "3".repeat(64)
        val root = temporary.newFolder("knowledge")
        root.resolve("$identity.$saveIdentity.json").writeText(
            """{"schema":4,"romIdentity":"${"d".repeat(64)}","saveIdentity":"$saveIdentity"}""",
        )

        assertNull(FileKnowledgeRepository(root).read(identity, saveIdentity))
    }

    @Test
    fun doesNotAutomaticallyTrustLegacyRomOnlyKnowledge() {
        val identity = "e".repeat(64)
        val saveIdentity = "5".repeat(64)
        val root = temporary.newFolder("knowledge")
        root.resolve("$identity.json").writeText(
            """{"schema":1,"romIdentity":"$identity","currentAreaBaseId":3,"seenSpeciesByArea":[{"areaBaseId":2,"speciesIds":[25]}]}""",
        )

        assertNull(FileKnowledgeRepository(root).read(identity, saveIdentity))
    }

    @Test
    fun rejectsDocumentsForAnotherSaveLineage() {
        val identity = "f".repeat(64)
        val saveIdentity = "6".repeat(64)
        val root = temporary.newFolder("knowledge")
        root.resolve("$identity.$saveIdentity.json").writeText(
            """{"schema":4,"romIdentity":"$identity","saveIdentity":"${"7".repeat(64)}","seenSpecies":[25]}""",
        )

        assertNull(FileKnowledgeRepository(root).read(identity, saveIdentity))
    }

    @Test
    fun migratesThePreviousPerSaveSchemaWithEmptyPoiKnowledge() {
        val identity = "8".repeat(64)
        val saveIdentity = "9".repeat(64)
        val root = temporary.newFolder("knowledge")
        root.resolve("$identity.$saveIdentity.json").writeText(
            """{"schema":4,"romIdentity":"$identity","saveIdentity":"$saveIdentity","seenSpecies":[25]}""",
        )

        val migrated = FileKnowledgeRepository(root).read(identity, saveIdentity)

        assertEquals(setOf(25), migrated?.seenSpecies)
        assertEquals(emptySet<String>(), migrated?.proximityRevealedPoiKeys)
        assertEquals(emptySet<String>(), migrated?.identifiedPoiKeys)
        assertEquals(emptySet<String>(), migrated?.enteredPoiKeys)
        assertEquals(emptySet<String>(), migrated?.collectedPoiKeys)
        assertEquals(LocalMapPoiPreferences(), migrated?.localMapPoiPreferences)
    }

    @Test
    fun migratesThePreviousPoiSchemaWithDefaultDisplayPreferences() {
        val identity = "a".repeat(64)
        val saveIdentity = "b".repeat(64)
        val root = temporary.newFolder("knowledge")
        root.resolve("$identity.$saveIdentity.json").writeText(
            """{"schema":5,"romIdentity":"$identity","saveIdentity":"$saveIdentity","proximityRevealedPoiKeys":["local/1/bg/0"]}""",
        )

        val migrated = FileKnowledgeRepository(root).read(identity, saveIdentity)

        assertEquals(setOf("local/1/bg/0"), migrated?.proximityRevealedPoiKeys)
        assertEquals(LocalMapPoiPreferences(), migrated?.localMapPoiPreferences)
    }
}
