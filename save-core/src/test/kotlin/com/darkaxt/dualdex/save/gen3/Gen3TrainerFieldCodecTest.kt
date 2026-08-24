package com.darkaxt.dualdex.save.gen3

import com.darkaxt.dualdex.save.BagPocket
import com.darkaxt.dualdex.save.TrainerPlayTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen3TrainerFieldCodecTest {
    @Test
    fun nullEncryptionKeyOffsetDecodesRubySapphireRawMoney() {
        val rawAbi = ABI.copy(trainer = ABI.trainer.copy(encryptionKeyOffset = null))
        val block1 = ByteArray(SAVE_BLOCK1_SIZE).apply { putU32le(0x490, 54_321) }
        val block2 = ByteArray(SAVE_BLOCK2_SIZE)

        val key = Gen3TrainerFieldCodec.decodeEncryptionKey(block2, rawAbi).value

        assertEquals(0L, key)
        assertEquals(54_321L, Gen3TrainerFieldCodec.decodeMoney(block1, key, rawAbi).value)
    }

    @Test
    fun decodesEachOfficialEmeraldTrainerFieldIndependently() {
        val block1 = ByteArray(SAVE_BLOCK1_SIZE)
        val block2 = ByteArray(SAVE_BLOCK2_SIZE)
        writeValidFields(block1, block2)

        assertEquals("MAY", Gen3TrainerFieldCodec.decodeIdentity(block2, ABI).value?.name)
        assertEquals(0x5678, Gen3TrainerFieldCodec.decodePublicTrainerId(block2, ABI).value)
        assertEquals(TrainerPlayTime(25, 17), Gen3TrainerFieldCodec.decodePlayTime(block2, ABI).value)
        val key = Gen3TrainerFieldCodec.decodeEncryptionKey(block2, ABI).value
        assertEquals(ENCRYPTION_KEY, key)
        assertEquals(12_345L, Gen3TrainerFieldCodec.decodeMoney(block1, key, ABI).value)
        assertEquals(0x81, Gen3TrainerFieldCodec.decodeBadgeFlags(block1, ABI).value)
    }

    @Test
    fun invalidIdentityDoesNotSuppressIdMoneyPlayTimeOrBadges() {
        val block1 = ByteArray(SAVE_BLOCK1_SIZE)
        val block2 = ByteArray(SAVE_BLOCK2_SIZE)
        writeValidFields(block1, block2)
        block2[ABI.trainer.genderOffset] = 4

        assertNull(Gen3TrainerFieldCodec.decodeIdentity(block2, ABI).value)
        assertEquals(0x5678, Gen3TrainerFieldCodec.decodePublicTrainerId(block2, ABI).value)
        assertEquals(TrainerPlayTime(25, 17), Gen3TrainerFieldCodec.decodePlayTime(block2, ABI).value)
        assertEquals(
            12_345L,
            Gen3TrainerFieldCodec.decodeMoney(
                block1,
                Gen3TrainerFieldCodec.decodeEncryptionKey(block2, ABI).value,
                ABI,
            ).value,
        )
        assertEquals(0x81, Gen3TrainerFieldCodec.decodeBadgeFlags(block1, ABI).value)
    }

    private fun writeValidFields(block1: ByteArray, block2: ByteArray) {
        intArrayOf(0xC7, 0xBB, 0xD3, 0xFF).forEachIndexed { index, value -> block2[index] = value.toByte() }
        block2[0x08] = 1
        block2.putU32le(0x0A, 0x1234_5678)
        block2.putU16le(0x0E, 25)
        block2[0x10] = 17
        block2.putU32le(0xAC, ENCRYPTION_KEY)
        block1.putU32le(0x490, 12_345L xor ENCRYPTION_KEY)
        ABI.trainer.badgeFlags.first().let { block1[it.byteOffset] = it.mask.toByte() }
        ABI.trainer.badgeFlags.last().let {
            block1[it.byteOffset] = (block1[it.byteOffset].toInt() or it.mask).toByte()
        }
    }

    private fun ByteArray.putU16le(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private fun ByteArray.putU32le(offset: Int, value: Long) {
        repeat(4) { index -> this[offset + index] = (value ushr (index * 8)).toByte() }
    }

    companion object {
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
            bag = Gen3BagAbi(
                BagPocket.entries.map { pocket -> Gen3BagPocketAbi(pocket, 0, 1) },
            ),
        )
    }
}
