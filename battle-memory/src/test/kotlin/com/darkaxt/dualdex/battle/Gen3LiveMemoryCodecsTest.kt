package com.darkaxt.dualdex.battle

import com.darkaxt.dualdex.save.BagPocket
import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveSpeciesContext
import com.darkaxt.dualdex.save.TrainerPlayTime
import com.darkaxt.dualdex.save.gen3.Gen3BagAbi
import com.darkaxt.dualdex.save.gen3.Gen3BagPocketAbi
import com.darkaxt.dualdex.save.gen3.Gen3BitFlag
import com.darkaxt.dualdex.save.gen3.Gen3SaveRuntimeAbi
import com.darkaxt.dualdex.save.gen3.Gen3TextEncoding
import com.darkaxt.dualdex.save.gen3.Gen3TrainerCardAbi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen3LiveMemoryCodecsTest {
    @Test
    fun unavailablePartyDoesNotResolvePokedexFromUnanchoredBytes() {
        val block2 = ByteArray(SAVE_BLOCK2_SIZE)
        val context = SaveParseContext(
            romIdentity = "b".repeat(64),
            speciesById = (1..386).associateWith { SaveSpeciesContext(it, it, null) },
            gen3SaveRuntimeAbi = ABI,
        )
        val flagBytes = (context.internalSpeciesCount + 7) / 8
        block2[0x28 - 0x10 + 2] = 0xDA.toByte()
        setFlag(block2, 0x28, 25)
        setFlag(block2, 0x28 + flagBytes, 25)
        val unavailableParty = LiveValue.Unavailable(
            LiveUnavailableReason(LiveUnavailableCode.MISSING_REGION, "party region missing"),
        )

        val live = Gen3LiveMemoryCodecs.decodePlayerOverview(
            saveBlock1 = null,
            saveBlock2 = block2,
            context = context,
            liveParty = unavailableParty,
        )

        assertNull(live.pokedex.seenDexNumbers.valueOrNull())
        assertNull(live.pokedex.caughtDexNumbers.valueOrNull())
    }

    @Test
    fun decodesTrainerAndPokedexWithoutSavedTrainerOrSaveFile() {
        val block1 = ByteArray(SAVE_BLOCK1_SIZE)
        val block2 = ByteArray(SAVE_BLOCK2_SIZE)
        writePlayer(block1, block2)
        val context = SaveParseContext(
            romIdentity = "a".repeat(64),
            speciesById = (1..386).associateWith { SaveSpeciesContext(it, it, null) },
            gen3SaveRuntimeAbi = ABI,
        )
        val flagBytes = (context.internalSpeciesCount + 7) / 8
        setFlag(block2, 0x28, 6)
        setFlag(block2, 0x28 + flagBytes, 6)
        setFlag(block2, 0x28 + flagBytes, 25)

        val live = Gen3LiveMemoryCodecs.decodePlayer(
            saveBlock1 = block1,
            saveBlock2 = block2,
            extendedSave = null,
            context = context,
            liveParty = LiveValue.Available(listOf(OwnedIndividual("party-0", speciesId = 6))),
        )

        assertEquals(0x5678, live.trainer.publicTrainerId.valueOrNull())
        assertEquals(3_000L, live.trainer.money.valueOrNull())
        assertEquals(TrainerPlayTime(2, 17), live.trainer.playTime.valueOrNull())
        assertEquals(setOf(6, 25), live.pokedex.seenDexNumbers.valueOrNull())
        assertEquals(setOf(6), live.pokedex.caughtDexNumbers.valueOrNull())
        assertEquals(0x28, live.pokedex.ownedFlagOffset)
        assertEquals(13, live.bag.getValue(BagPocket.ITEMS).valueOrNull()?.entries?.single()?.itemId)
        assertEquals(2, live.bag.getValue(BagPocket.ITEMS).valueOrNull()?.entries?.single()?.quantity)
    }

    private fun writePlayer(block1: ByteArray, block2: ByteArray) {
        intArrayOf(0xC7, 0xBB, 0xD3, 0xFF).forEachIndexed { index, value -> block2[index] = value.toByte() }
        block2[0x08] = 1
        block2.putU32le(0x0A, 0x1234_5678)
        block2.putU16le(0x0E, 2)
        block2[0x10] = 17
        block2.putU32le(0xAC, ENCRYPTION_KEY)
        block1.putU32le(0x490, 3_000L xor ENCRYPTION_KEY)
        block1.putU16le(0x500, 13)
        block1.putU16le(0x502, 2 xor (ENCRYPTION_KEY.toInt() and 0xFFFF))
    }

    private fun setFlag(bytes: ByteArray, offset: Int, dexNumber: Int) {
        val index = dexNumber - 1
        bytes[offset + index / 8] = (bytes[offset + index / 8].toInt() or (1 shl (index % 8))).toByte()
    }

    private fun ByteArray.putU16le(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private fun ByteArray.putU32le(offset: Int, value: Long) {
        repeat(4) { index -> this[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private companion object {
        const val SAVE_BLOCK1_SIZE = 0x3D88
        const val SAVE_BLOCK2_SIZE = 0xF2C
        const val ENCRYPTION_KEY = 0x1357_2468L

        val ABI = Gen3SaveRuntimeAbi(
            saveBlock1Size = SAVE_BLOCK1_SIZE,
            saveBlock2Size = SAVE_BLOCK2_SIZE,
            textEncoding = Gen3TextEncoding.ENGLISH,
            trainer = Gen3TrainerCardAbi(
                playerNameOffset = 0,
                playerNameLength = 8,
                genderOffset = 0x08,
                trainerIdOffset = 0x0A,
                playTimeHoursOffset = 0x0E,
                playTimeMinutesOffset = 0x10,
                encryptionKeyOffset = 0xAC,
                moneyOffset = 0x490,
                maximumMoney = 999_999,
                badgeFlags = (0x867..0x86E).map { flag ->
                    Gen3BitFlag(0x1270 + flag / 8, 1 shl (flag % 8))
                },
            ),
            bag = Gen3BagAbi(listOf(Gen3BagPocketAbi(BagPocket.ITEMS, 0x500, 1, 4))),
        )
    }
}
