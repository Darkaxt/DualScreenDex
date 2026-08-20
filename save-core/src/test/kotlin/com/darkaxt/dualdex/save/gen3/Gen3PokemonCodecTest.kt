package com.darkaxt.dualdex.save.gen3

import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveSpeciesContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3PokemonCodecTest {
    private val context = SaveParseContext(
        romIdentity = "a".repeat(64),
        speciesById = mapOf(
            1 to SaveSpeciesContext(
                speciesId = 1,
                dexNumber = 1,
                growthRate = 0,
                genderRatio = 31,
                abilityIds = listOf(65, 66),
            ),
        ),
        captureBallIds = setOf(4),
        movePpById = mapOf(1 to 35, 2 to 25, 3 to 15, 4 to 5),
        gen3TextEncoding = Gen3TextEncoding.ENGLISH,
    )

    @Test
    fun decodesACompleteChecksumValidEncryptedPartyRecord() {
        val halfway = Gen3Experience.required(0, 10) +
            (Gen3Experience.required(0, 11) - Gen3Experience.required(0, 10)) / 2
        val decoded = requireNotNull(
            Gen3PokemonCodec.decode(
                detailedRecord(experience = halfway),
                offset = 0,
                stableLocation = "party-0",
                context = context,
                partyLevel = 10,
            ),
        )
        val details = requireNotNull(decoded.details)

        assertEquals("TREECKO", details.nickname)
        assertEquals(17L, details.personality)
        assertEquals(1, details.gender)
        assertEquals(17, details.natureId)
        assertEquals(4, details.heldItemId)
        assertEquals(70, details.friendship)
        assertEquals(1, details.abilitySlot)
        assertEquals(66, details.abilityId)
        assertEquals(19, details.currentHp)
        assertEquals(20, details.maximumHp)
        assertEquals(0L, details.status)
        assertEquals(listOf(20, 12, 11, 14, 15, 13), details.stats)
        assertEquals(listOf(1, 2, 3, 4), details.moveIds)
        assertEquals(listOf(35, 25, 15, 5), details.movePp)
        assertEquals(listOf(0, 1, 2, 3), details.movePpBonuses)
        assertTrue(details.experienceProgress!! in 0.49..0.51)
    }

    @Test
    fun rejectsCorruptEncryptedPayloadAndIllegalPp() {
        val corrupt = detailedRecord().also { it[32] = (it[32].toInt() xor 1).toByte() }
        assertNull(Gen3PokemonCodec.decode(corrupt, 0, "party-0", context))

        val illegalPp = detailedRecord(pp = intArrayOf(36, 25, 15, 5))
        assertNull(Gen3PokemonCodec.decode(illegalPp, 0, "party-0", context))
    }

    @Test
    fun keepsNumericPartyDataWhenNoTextEncodingWasSelected() {
        val decoded = requireNotNull(
            Gen3PokemonCodec.decode(
                detailedRecord(),
                0,
                "party-0",
                context.copy(gen3TextEncoding = null),
            ),
        )

        assertNull(decoded.details?.nickname)
        assertEquals(listOf(1, 2, 3, 4), decoded.details?.moveIds)
    }

    private fun detailedRecord(
        experience: Long = Gen3Experience.required(0, 10),
        pp: IntArray = intArrayOf(35, 25, 15, 5),
    ): ByteArray {
        val personality = 17L
        val otId = 0x1020_3040L
        val record = ByteArray(Gen3PokemonCodec.PARTY_RECORD_SIZE)
        record.putU32le(0, personality)
        record.putU32le(4, otId)
        intArrayOf(0xCE, 0xCC, 0xBF, 0xBF, 0xBD, 0xC5, 0xC9, 0xFF).forEachIndexed { index, value ->
            record[8 + index] = value.toByte()
        }
        record[18] = 2
        record[19] = 0x02

        val logical = Array(4) { ByteArray(12) }
        logical[0].putU16le(0, 1)
        logical[0].putU16le(2, 4)
        logical[0].putU32le(4, experience)
        logical[0][8] = 0xE4.toByte()
        logical[0][9] = 70
        intArrayOf(1, 2, 3, 4).forEachIndexed { index, move -> logical[1].putU16le(index * 2, move) }
        pp.forEachIndexed { index, value -> logical[1][8 + index] = value.toByte() }
        logical[3].putU16le(2, 4 shl 11)
        var ivWord = 1L shl 31
        listOf(31, 30, 29, 28, 27, 26).forEachIndexed { index, iv ->
            ivWord = ivWord or (iv.toLong() shl (index * 5))
        }
        logical[3].putU32le(4, ivWord)

        val decrypted = ByteArray(48)
        val order = ORDERS[(personality % 24).toInt()]
        repeat(4) { logicalIndex -> logical[logicalIndex].copyInto(decrypted, order[logicalIndex] * 12) }
        record.putU16le(28, Gen3Checksums.pokemon(decrypted))
        val key = personality xor otId
        repeat(12) { index -> record.putU32le(32 + index * 4, decrypted.u32le(index * 4) xor key) }

        record.putU32le(80, 0)
        record[84] = 10
        record.putU16le(86, 19)
        intArrayOf(20, 12, 11, 14, 15, 13).forEachIndexed { index, value ->
            record.putU16le(88 + index * 2, value)
        }
        return record
    }

    private fun ByteArray.putU16le(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private fun ByteArray.putU32le(offset: Int, value: Long) {
        repeat(4) { index -> this[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private companion object {
        val ORDERS = arrayOf(
            intArrayOf(0, 1, 2, 3), intArrayOf(0, 1, 3, 2), intArrayOf(0, 2, 1, 3),
            intArrayOf(0, 3, 1, 2), intArrayOf(0, 2, 3, 1), intArrayOf(0, 3, 2, 1),
            intArrayOf(1, 0, 2, 3), intArrayOf(1, 0, 3, 2), intArrayOf(2, 0, 1, 3),
            intArrayOf(3, 0, 1, 2), intArrayOf(2, 0, 3, 1), intArrayOf(3, 0, 2, 1),
            intArrayOf(1, 2, 0, 3), intArrayOf(1, 3, 0, 2), intArrayOf(2, 1, 0, 3),
            intArrayOf(3, 1, 0, 2), intArrayOf(2, 3, 0, 1), intArrayOf(3, 2, 0, 1),
            intArrayOf(1, 2, 3, 0), intArrayOf(1, 3, 2, 0), intArrayOf(2, 1, 3, 0),
            intArrayOf(3, 1, 2, 0), intArrayOf(2, 3, 1, 0), intArrayOf(3, 2, 1, 0),
        )
    }
}
