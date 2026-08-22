package com.darkaxt.dualdex.battle

import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveSpeciesContext
import com.darkaxt.dualdex.save.BagPocket
import com.darkaxt.dualdex.save.gen3.Gen3BagAbi
import com.darkaxt.dualdex.save.gen3.Gen3BagPocketAbi
import com.darkaxt.dualdex.save.gen3.Gen3BitFlag
import com.darkaxt.dualdex.save.gen3.Gen3EventFlagAbi
import com.darkaxt.dualdex.save.gen3.Gen3SaveRuntimeAbi
import com.darkaxt.dualdex.save.gen3.Gen3TextEncoding
import com.darkaxt.dualdex.save.gen3.Gen3TrainerCardAbi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3LiveGameStateTest {
    @Test
    fun readsPointerGlobalsBeforeValidatedDependentWindows() {
        val layout = layout().copy(extendedSaveAddress = 0x02030000, extendedSaveSize = 0x2000)
        assertEquals(
            listOf(0x03001000L, 0x03001004L),
            Gen3LiveGameState.pointerWindows(layout).map { it.address },
        )

        val pointers = Gen3LiveGameState.decodePointers(
            mapOf(
                Gen3LiveGameState.SAVE_BLOCK1_POINTER_ID to pointer(0x02001000L),
                Gen3LiveGameState.SAVE_BLOCK2_POINTER_ID to pointer(0x01002000L),
            ),
            layout,
        )
        assertEquals(0x02001000L, pointers.saveBlock1Address)
        assertNull(pointers.saveBlock2Address)
        assertEquals(
            listOf(
                Gen3LiveGameState.SAVE_BLOCK1_ID,
                Gen3LiveGameState.EXTENDED_SAVE_ID,
                Gen3LiveGameState.PARTY_COUNT_ID,
                Gen3LiveGameState.PARTY_ID,
                Gen3LiveGameState.CLOCK_ID,
            ),
            Gen3LiveGameState.dependentWindows(layout, pointers).map { it.id },
        )
    }

    @Test
    fun decodesTheSourceDefinedFiveByteLiveClockAndRejectsInvalidFields() {
        val valid = Gen3LiveGameState.decode(
            romIdentity = "rom",
            regions = mapOf(Gen3LiveGameState.CLOCK_ID to byteArrayOf(0, 0, 16, 48, 12)),
            layout = layout(),
            saveContext = null,
            savedTrainer = null,
            battleActive = null,
            targetBattler = null,
            encounterKind = BattleEncounterKind.UNKNOWN,
        )
        val invalid = Gen3LiveGameState.decode(
            romIdentity = "rom",
            regions = mapOf(Gen3LiveGameState.CLOCK_ID to byteArrayOf(0, 0, 24, 0, 0)),
            layout = layout(),
            saveContext = null,
            savedTrainer = null,
            battleActive = null,
            targetBattler = null,
            encounterKind = BattleEncounterKind.UNKNOWN,
        )

        assertEquals(Gen3GameClock(16, 48), valid.clock.value)
        assertEquals(Gen3LiveSectionState.UNAVAILABLE, invalid.clock.state)
    }

    @Test
    fun invalidSaveBlock2StillPublishesLocationAndPartyAndZeroCountClearsParty() {
        val layout = layout()
        val saveBlock1 = ByteArray(0x100).apply {
            this[4] = 3
            this[5] = 12
        }
        val snapshot = Gen3LiveGameState.decode(
            romIdentity = "rom",
            regions = mapOf(
                Gen3LiveGameState.SAVE_BLOCK1_ID to saveBlock1,
                Gen3LiveGameState.PARTY_COUNT_ID to byteArrayOf(0),
                Gen3LiveGameState.PARTY_ID to ByteArray(600),
            ),
            layout = layout,
            saveContext = SaveParseContext("rom", mapOf(1 to SaveSpeciesContext(1, 1, 0))),
            savedTrainer = null,
            battleActive = false,
            targetBattler = null,
            encounterKind = BattleEncounterKind.UNKNOWN,
        )

        assertEquals(0x030C, snapshot.location.value)
        assertEquals(emptyList<Any>(), snapshot.party.value)
        assertEquals(Gen3LiveSectionState.UNAVAILABLE, snapshot.trainer.state)
        assertTrue(snapshot.bag.values.all { it.state == Gen3LiveSectionState.UNAVAILABLE })
        assertEquals(false, snapshot.battle.value?.active)
    }

    @Test
    fun liveSaveBlockPublishesSetEventFlagsFromTheTypedWindow() {
        val saveBlock1 = ByteArray(0x100).apply {
            this[0x20 + 1007 / 8] = (1 shl (1007 % 8)).toByte()
        }
        val snapshot = Gen3LiveGameState.decode(
            romIdentity = "rom",
            regions = mapOf(Gen3LiveGameState.SAVE_BLOCK1_ID to saveBlock1),
            layout = layout(),
            saveContext = SaveParseContext(
                "rom",
                mapOf(1 to SaveSpeciesContext(1, 1, 0)),
                gen3SaveRuntimeAbi = saveAbi(),
            ),
            savedTrainer = null,
            battleActive = null,
            targetBattler = null,
            encounterKind = BattleEncounterKind.UNKNOWN,
        )

        assertEquals(Gen3LiveSectionState.AVAILABLE, snapshot.eventFlags.state)
        assertEquals(setOf(1007), snapshot.eventFlags.value)
    }

    @Test
    fun liveSaveBlock2PublishesTrainerIdentityWithoutASeparateSaveSnapshot() {
        val saveBlock2 = ByteArray(0x80).apply {
            intArrayOf(0xBC, 0xCC, 0xBF, 0xC8, 0xBE, 0xBB, 0xC8, 0xFF)
                .forEachIndexed { index, value -> this[index] = value.toByte() }
            this[8] = 0
        }
        val snapshot = Gen3LiveGameState.decode(
            romIdentity = "rom",
            regions = mapOf(Gen3LiveGameState.SAVE_BLOCK2_ID to saveBlock2),
            layout = layout(),
            saveContext = SaveParseContext(
                "rom",
                mapOf(1 to SaveSpeciesContext(1, 1, 0)),
                gen3SaveRuntimeAbi = saveAbi(),
            ),
            savedTrainer = null,
            battleActive = null,
            targetBattler = null,
            encounterKind = BattleEncounterKind.UNKNOWN,
        )

        assertEquals(Gen3LiveSectionState.UNAVAILABLE, snapshot.trainer.state)
        assertEquals("BRENDAN", snapshot.trainerIdentity.value?.name)
        assertEquals(0, snapshot.trainerIdentity.value?.gender)
    }

    private fun layout() = Gen3RuntimeMemoryLayout(
        mainAddress = 0x03002000,
        inBattleAddress = 0x03002040,
        inBattleMask = 1,
        saveBlock1MapGroupOffset = 4,
        saveBlock1MapNumberOffset = 5,
        liveClockAddress = 0x030039E8,
        playerPartyCountAddress = 0x02000200,
        playerPartyAddress = 0x02000300,
        saveBlock1PointerAddress = 0x03001000,
        saveBlock2PointerAddress = 0x03001004,
        saveBlock1Size = 0x100,
        saveBlock2Size = 0x80,
    )

    private fun saveAbi() = Gen3SaveRuntimeAbi(
        saveBlock1Size = 0x100,
        saveBlock2Size = 0x80,
        textEncoding = Gen3TextEncoding.ENGLISH,
        trainer = Gen3TrainerCardAbi(
            playerNameOffset = 0,
            playerNameLength = 8,
            genderOffset = 8,
            trainerIdOffset = 10,
            playTimeHoursOffset = 14,
            playTimeMinutesOffset = 16,
            encryptionKeyOffset = 0x40,
            moneyOffset = 0,
            maximumMoney = 999_999,
            badgeFlags = listOf(Gen3BitFlag(1, 1)),
        ),
        bag = Gen3BagAbi(listOf(Gen3BagPocketAbi(BagPocket.ITEMS, 4, 1))),
        eventFlags = Gen3EventFlagAbi(0x20, 0x80),
    )

    private fun pointer(address: Long) = ByteArray(4) { index -> (address ushr (index * 8)).toByte() }
}
