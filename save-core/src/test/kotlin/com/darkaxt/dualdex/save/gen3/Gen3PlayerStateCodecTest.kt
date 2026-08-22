package com.darkaxt.dualdex.save.gen3

import com.darkaxt.dualdex.save.BagEntry
import com.darkaxt.dualdex.save.BagPocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Gen3PlayerStateCodecTest {
    @Test
    fun decodesLiveTrainerIdentityWithoutRequiringPokedexCounts() {
        val saveBlock2 = ByteArray(EMERALD_SAVE_BLOCK2_SIZE)
        intArrayOf(0xC7, 0xBB, 0xD3, 0xFF).forEachIndexed { index, value -> saveBlock2[index] = value.toByte() }
        saveBlock2[EMERALD_SAVE_ABI.trainer.genderOffset] = 1

        val identity = requireNotNull(
            Gen3PlayerStateCodec.decodeIdentity(saveBlock2, EMERALD_SAVE_ABI).value,
        )

        assertEquals("MAY", identity.name)
        assertEquals(1, identity.gender)
    }

    @Test
    fun decodesOfficialEmeraldTrainerAndEveryEncryptedBagPocket() {
        val saveBlock1 = ByteArray(EMERALD_SAVE_BLOCK1_SIZE)
        val saveBlock2 = ByteArray(EMERALD_SAVE_BLOCK2_SIZE)
        writeTrainer(saveBlock1, saveBlock2)
        writeBagEntry(saveBlock1, offset = 0x650, itemId = 4, quantity = 12)

        val result = Gen3PlayerStateCodec.decode(
            saveBlock1 = saveBlock1,
            saveBlock2 = saveBlock2,
            abi = EMERALD_SAVE_ABI,
            dexSeen = 15,
            dexCaught = 8,
        )

        val trainer = requireNotNull(result.trainer.value)
        assertEquals("MAY", trainer.name)
        assertEquals(1, trainer.gender)
        assertEquals(0x5678, trainer.publicTrainerId)
        assertEquals(12_345L, trainer.money)
        assertEquals(25, trainer.playTimeHours)
        assertEquals(17, trainer.playTimeMinutes)
        assertEquals(0x81, trainer.badgeFlags)
        assertEquals(15, trainer.dexSeen)
        assertEquals(8, trainer.dexCaught)
        assertNull(trainer.stars)
        assertEquals(
            BagEntry(itemId = 4, quantity = 12),
            result.bag.getValue(BagPocket.BALLS).value?.entries?.single(),
        )
        assertEquals(BagPocket.entries.toSet(), result.bag.keys)
        assertEquals(4, result.bag.values.count { it.value?.entries?.isEmpty() == true })
    }

    @Test
    fun isolatesMalformedPocketAndTrainerBoundsFailures() {
        val saveBlock1 = ByteArray(EMERALD_SAVE_BLOCK1_SIZE)
        val saveBlock2 = ByteArray(EMERALD_SAVE_BLOCK2_SIZE)
        writeTrainer(saveBlock1, saveBlock2)
        writeBagEntry(saveBlock1, offset = 0x650, itemId = 4, quantity = 0)

        val malformedPocket = Gen3PlayerStateCodec.decode(saveBlock1, saveBlock2, EMERALD_SAVE_ABI, 2, 1)

        assertNotNull(malformedPocket.trainer.value)
        assertNull(malformedPocket.bag.getValue(BagPocket.BALLS).value)
        assertNotNull(malformedPocket.bag.getValue(BagPocket.ITEMS).value)

        saveBlock2[EMERALD_SAVE_ABI.trainer.genderOffset] = 4
        val malformedTrainer = Gen3PlayerStateCodec.decode(saveBlock1, saveBlock2, EMERALD_SAVE_ABI, 2, 1)

        assertNull(malformedTrainer.trainer.value)
        assertNotNull(malformedTrainer.bag.getValue(BagPocket.ITEMS).value)
    }

    private fun writeTrainer(saveBlock1: ByteArray, saveBlock2: ByteArray) {
        intArrayOf(0xC7, 0xBB, 0xD3, 0xFF).forEachIndexed { index, value -> saveBlock2[index] = value.toByte() }
        saveBlock2[0x08] = 1
        saveBlock2.putU32le(0x0A, 0x1234_5678)
        saveBlock2.putU16le(0x0E, 25)
        saveBlock2[0x10] = 17
        saveBlock2.putU32le(0xAC, ENCRYPTION_KEY)
        saveBlock1.putU32le(0x490, 12_345L xor ENCRYPTION_KEY)
        EMERALD_SAVE_ABI.trainer.badgeFlags.first().let { saveBlock1[it.byteOffset] = it.mask.toByte() }
        EMERALD_SAVE_ABI.trainer.badgeFlags.last().let {
            saveBlock1[it.byteOffset] = (saveBlock1[it.byteOffset].toInt() or it.mask).toByte()
        }
    }

    private fun writeBagEntry(saveBlock1: ByteArray, offset: Int, itemId: Int, quantity: Int) {
        saveBlock1.putU16le(offset, itemId)
        saveBlock1.putU16le(offset + 2, quantity xor ENCRYPTION_KEY.toInt())
    }

    private fun ByteArray.putU16le(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private fun ByteArray.putU32le(offset: Int, value: Long) {
        repeat(4) { index -> this[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private companion object {
        const val EMERALD_SAVE_BLOCK1_SIZE = 0x3D88
        const val EMERALD_SAVE_BLOCK2_SIZE = 0xF2C
        const val ENCRYPTION_KEY = 0x1357_2468L

        val EMERALD_SAVE_ABI = Gen3SaveRuntimeAbi(
            saveBlock1Size = EMERALD_SAVE_BLOCK1_SIZE,
            saveBlock2Size = EMERALD_SAVE_BLOCK2_SIZE,
            textEncoding = Gen3TextEncoding.ENGLISH,
            trainer = Gen3TrainerCardAbi(
                playerNameOffset = 0x00,
                playerNameLength = 8,
                genderOffset = 0x08,
                trainerIdOffset = 0x0A,
                playTimeHoursOffset = 0x0E,
                playTimeMinutesOffset = 0x10,
                encryptionKeyOffset = 0xAC,
                moneyOffset = 0x490,
                maximumMoney = 999_999,
                badgeFlags = (0x867..0x86E).map { flag ->
                    Gen3BitFlag(byteOffset = 0x1270 + flag / 8, mask = 1 shl (flag % 8))
                },
            ),
            bag = Gen3BagAbi(
                pockets = listOf(
                    Gen3BagPocketAbi(BagPocket.ITEMS, 0x560, 30),
                    Gen3BagPocketAbi(BagPocket.KEY_ITEMS, 0x5D8, 30),
                    Gen3BagPocketAbi(BagPocket.BALLS, 0x650, 16),
                    Gen3BagPocketAbi(BagPocket.TM_HM, 0x690, 64),
                    Gen3BagPocketAbi(BagPocket.BERRIES, 0x790, 46),
                ),
            ),
        )
    }
}
