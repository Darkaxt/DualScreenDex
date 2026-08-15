package com.darkaxt.dualdex.save

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SaveModelsTest {
    @Test(expected = IllegalArgumentException::class)
    fun partyDetailRequiresFourAlignedMoveAndPpSlots() {
        PartyMemberDetails(
            moveIds = listOf(1, 2),
            movePp = listOf(10),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun bagPocketRejectsNonpositiveItemIdsAndQuantities() {
        BagPocketSnapshot(
            pocket = BagPocket.ITEMS,
            entries = listOf(BagEntry(itemId = 0, quantity = 1)),
        )
    }

    @Test
    fun detailedPartyAndTrainerFieldsRemainOptionalForExistingSnapshots() {
        val individual = OwnedIndividual(stableLocation = "party-0", speciesId = 1)
        val snapshot = SaveSnapshot(
            romIdentity = "a".repeat(64),
            saveIdentity = "save",
            saveGeneration = 3,
            saveCounter = 1,
            currentArea = null,
            seenDexNumbers = emptySet(),
            caughtDexNumbers = emptySet(),
            party = listOf(individual),
            storedIndividuals = emptyList(),
            capabilities = emptyMap(),
        )

        assertNull(individual.details)
        assertNull(snapshot.trainer)
        assertEquals(emptyList<BagPocketSnapshot>(), snapshot.bag)
    }

    @Test
    fun acceptsACompleteNormalizedPlayerState() {
        val details = PartyMemberDetails(
            nickname = "TREECKO",
            personality = 17,
            gender = 0,
            natureId = 17,
            heldItemId = 4,
            friendship = 70,
            abilitySlot = 1,
            abilityId = 65,
            currentHp = 19,
            maximumHp = 20,
            status = 0,
            stats = listOf(20, 12, 11, 14, 15, 13),
            moveIds = listOf(1, 2, 3, 4),
            movePp = listOf(35, 25, 15, 5),
            movePpBonuses = listOf(0, 1, 2, 3),
            experienceProgress = 0.5,
        )
        val trainer = TrainerSnapshot(
            name = "MAY",
            gender = 1,
            publicTrainerId = 0x1234,
            money = 12_345,
            playTimeHours = 25,
            playTimeMinutes = 17,
            badgeFlags = 0x55,
            dexSeen = 15,
            dexCaught = 8,
            stars = 2,
        )

        assertEquals(4, details.moveIds.size)
        assertEquals(8, trainer.dexCaught)
        assertEquals(BagPocket.BALLS, BagPocketSnapshot(BagPocket.BALLS, listOf(BagEntry(4, 12))).pocket)
    }
}
