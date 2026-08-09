package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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

    @Test
    fun stopsFixedNameCountBeforeReadableAdjacentProseFragments() {
        val width = 13
        val bytes = ByteArray(width * 10)
        repeat(5) { index ->
            bytes[index * width] = (0xBB + index).toByte()
            bytes[index * width + 1] = 0xFF.toByte()
        }
        // The real Modern Emerald move-name table is followed by prose. Two
        // unterminated chunks precede another readable, terminated fragment.
        // A fixed-record resolver must not resume after that boundary.
        repeat(width * 2) { index -> bytes[width * 5 + index] = 0xD5.toByte() }
        bytes[width * 7] = 0xD5.toByte()
        bytes[width * 7 + 1] = 0xFF.toByte()

        val count = TableValidators.inferFixedNameCount(
            RomImage(bytes), 0, width, PokemonTextCodec.gbaEnglish, minimumCount = 5, maximumCount = 10,
        )

        assertEquals(5, count)
    }

    @Test
    fun infersCountFromNextAlignedTable() {
        val count = TableValidators.inferCountFromFollowingTable(
            offset = 0x1000,
            recordSize = 11,
            followingOffsets = listOf(0x1000 + 462 * 11 + 2, 0x9000),
            minimumCount = 300,
            maximumCount = 2048,
        )

        assertEquals(462, count)
    }

    @Test
    fun infersExtendedGen3BaseStatRecordWidth() {
        val offset = 32
        val count = 462
        val width = 40
        val bytes = ByteArray(offset + count * width)
        for (index in 1 until count) {
            val base = offset + index * width
            bytes[base] = 45
            bytes[base + 1] = 49
            bytes[base + 2] = 49
            bytes[base + 3] = 45
            bytes[base + 4] = 65
            bytes[base + 5] = 65
            bytes[base + 6] = if (index == 100) 18 else 12
            bytes[base + 7] = 4
        }
        val rom = RomImage(bytes)

        val inferred = TableValidators.inferBaseStatsRecordSize(rom, offset, count, generation = 3)

        assertEquals(width, inferred)
        assertTrue(TableValidators.baseStats(rom, offset, count, inferred!!, generation = 3).compatible)
    }

    @Test
    fun locatesRelocatedGen3TypeCharts() {
        val firstOffset = 37
        val secondOffset = 200
        val chart = byteArrayOf(
            0, 5, 5,
            0, 8, 5,
            10, 10, 5,
            10, 11, 5,
            10, 12, 20,
            10, 15, 20,
            10, 6, 20,
            10, 5, 5,
            10, 16, 5,
            10, 8, 20,
            11, 10, 20,
            11, 11, 5,
            0xFF.toByte(), 0xFF.toByte(), 0,
        )
        val bytes = ByteArray(300) { 0x7F }
        chart.copyInto(bytes, firstOffset)
        chart.copyInto(bytes, secondOffset)

        val offsets = TableValidators.locateGen3TypeCharts(RomImage(bytes)).mapNotNull { it.offset }

        assertEquals(listOf(firstOffset, secondOffset), offsets)
    }

    @Test
    fun validatesFairyEntriesInGen3TypeCharts() {
        val records = mutableListOf<Byte>()
        repeat(10) { index ->
            records += (index % 18).toByte()
            records += ((index + 1) % 18).toByte()
            records += if (index % 2 == 0) 5 else 20
        }
        records += 18
        records += 1
        records += 20
        records += 16
        records += 18
        records += 0
        records += 0xFF.toByte()
        records += 0xFF.toByte()
        records += 0

        val result = TableValidators.typeChart(RomImage(records.toByteArray()), 0, generation = 3)

        assertTrue(result.compatible)
        assertEquals(12, result.validRecords)
        assertEquals(12, result.totalRecords)
    }

    @Test
    fun resolvesRelocatedGen3TypeChartWhenInheritedOffsetIsInvalid() {
        val relocatedOffset = 37
        val chart = byteArrayOf(
            0, 5, 5,
            0, 8, 5,
            10, 10, 5,
            10, 11, 5,
            10, 12, 20,
            10, 15, 20,
            10, 6, 20,
            10, 5, 5,
            10, 16, 5,
            10, 8, 20,
            11, 10, 20,
            11, 11, 5,
            0xFF.toByte(), 0xFF.toByte(), 0,
        )
        val bytes = ByteArray(128) { 0x7F }
        chart.copyInto(bytes, relocatedOffset)

        val result = TableValidators.resolveGen3TypeChart(RomImage(bytes), inheritedOffset = 0)

        assertTrue(result.compatible)
        assertEquals(relocatedOffset, result.offset)
    }

    @Test
    fun locatesNonCanonicalExtendedGen3TypeChart() {
        val chartOffset = 41
        val records = mutableListOf<Byte>()
        repeat(96) { index ->
            records += (index % 24).toByte()
            records += ((index * 7 + 3) % 24).toByte()
            records += when (index % 4) {
                0 -> 0
                1 -> 5
                2 -> 10
                else -> 20
            }
        }
        records += 0xFE.toByte()
        records += 0xFE.toByte()
        records += 0
        val bytes = ByteArray(400) { 0x7F }
        records.toByteArray().copyInto(bytes, chartOffset)

        val charts = TableValidators.locateGen3TypeCharts(RomImage(bytes))

        assertEquals(1, charts.size)
        assertEquals(chartOffset, charts.single().offset)
        assertEquals(96, charts.single().validRecords)
    }
}
