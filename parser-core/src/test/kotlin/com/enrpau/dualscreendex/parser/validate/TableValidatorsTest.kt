package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TableValidatorsTest {
    @Test
    fun rejectsNamesWithInvalidCharacterRatio() {
        val result = TableValidators.fixedNames(
            RomImage(ByteArray(110) { 0x01 }),
            offset = 0,
            count = 10,
            width = 11,
            codec = PokemonTextCodec.gbaEnglish,
        )
        assertFalse(result.compatible)
    }

    @Test
    fun acceptsPlausibleGen3Stats() {
        val record = ByteArray(28)
        record[0] = 45
        record[1] = 49
        record[2] = 49
        record[3] = 45
        record[4] = 65
        record[5] = 65
        record[6] = 12
        record[7] = 4
        val result = TableValidators.baseStats(RomImage(record), 0, 1, 28, generation = 3)
        assertTrue(result.compatible)
    }

    @Test
    fun acceptsGen2StatsWithLeadingSpeciesId() {
        val record = ByteArray(32)
        record[0] = 1
        record[1] = 45
        record[2] = 49
        record[3] = 49
        record[4] = 45
        record[5] = 65
        record[6] = 65
        record[7] = 22
        record[8] = 3

        val result = TableValidators.baseStats(RomImage(record), 0, 1, 32, generation = 2)

        assertTrue(result.compatible)
    }

    @Test
    fun acceptsGbaPointerTable() {
        val bytes = ByteArray(64)
        repeat(4) { index ->
            val pointer = 0x08000020 + index
            repeat(4) { byte -> bytes[index * 8 + byte] = (pointer ushr (byte * 8)).toByte() }
        }
        val result = TableValidators.gbaPointerTable(RomImage(bytes), 0, 4, 8)
        assertTrue(result.compatible)
    }

    @Test
    fun infersFixedNameCount() {
        val bytes = ByteArray(44)
        repeat(3) { index ->
            bytes[index * 11] = (0xBB + index).toByte()
            bytes[index * 11 + 1] = 0xFF.toByte()
        }
        val count = TableValidators.inferFixedNameCount(
            RomImage(bytes), 0, 11, PokemonTextCodec.gbaEnglish, minimumCount = 3, maximumCount = 4,
        )
        assertTrue(count == 3)
    }
}
