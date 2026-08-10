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
    fun acceptsFullWidthGen2NamesWithoutATerminator() {
        val width = 10
        val bytes = ByteArray(width * 3)
        repeat(3) { index ->
            repeat(width) { character ->
                bytes[index * width + character] = (0x80 + (index + character) % 26).toByte()
            }
        }

        val result = TableValidators.fixedNames(
            RomImage(bytes),
            offset = 0,
            count = 3,
            width = width,
            codec = PokemonTextCodec.gbEnglish,
        )

        assertTrue(result.compatible)
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
    fun acceptsExpandedGen3TypeIdsInBaseStats() {
        val record = ByteArray(28)
        repeat(6) { record[it] = 50 }
        record[6] = 20
        record[7] = 31

        assertTrue(TableValidators.baseStats(RomImage(record), 0, 1, 28, generation = 3).compatible)
    }

    @Test
    fun trimsGen3MoveDataBeforeAnAdjacentNonMoveTable() {
        val recordSize = 12
        val moveCount = 16
        val realMoveCount = 8
        val bytes = ByteArray(moveCount * recordSize)
        repeat(realMoveCount) { index ->
            val base = index * recordSize
            bytes[base] = index.toByte()
            bytes[base + 1] = if (index == 0) 0 else 40
            bytes[base + 2] = if (index == 3) 23 else 0
            bytes[base + 3] = 100
            bytes[base + 4] = 35
        }
        repeat(moveCount - realMoveCount) { index ->
            val base = (realMoveCount + index) * recordSize
            bytes[base] = 3
            bytes[base + 1] = 3
            bytes[base + 2] = 6
            bytes[base + 3] = 6
            bytes[base + 4] = 3
        }

        val result = TableValidators.moveData(
            RomImage(bytes), offset = 0, count = moveCount, recordSize = recordSize, generation = 3,
        )

        assertTrue(result.compatible)
        assertEquals(realMoveCount, result.validRecords)
        assertEquals(realMoveCount, result.totalRecords)
    }

    @Test
    fun acceptsGen3InternalAndCustomMovesWithZeroPpOrSingleDigitPower() {
        val recordSize = 12
        val bytes = ByteArray(recordSize * 3)
        fun move(index: Int, power: Int, type: Int, accuracy: Int, pp: Int) {
            val base = index * recordSize
            bytes[base] = index.toByte()
            bytes[base + 1] = power.toByte()
            bytes[base + 2] = type.toByte()
            bytes[base + 3] = accuracy.toByte()
            bytes[base + 4] = pp.toByte()
        }
        move(0, 50, 9, 0, 0) // Struggle-like engine move with non-depleting PP.
        move(1, 5, 17, 0, 30) // Custom low-power move whose effect supplies the real behavior.
        move(2, 0, 0, 0, 0) // Named internal/scripting move.

        val result = TableValidators.moveData(
            RomImage(bytes), offset = 0, count = 3, recordSize = recordSize, generation = 3,
        )

        assertTrue(result.compatible)
        assertEquals(3, result.validRecords)
    }

    @Test
    fun acceptsGen3VariablePowerSentinelAndLowAccuracyMoves() {
        val recordSize = 12
        val bytes = ByteArray(recordSize * 2)
        bytes[recordSize + 1] = 0xFF.toByte()
        bytes[recordSize + 2] = 9
        bytes[recordSize + 3] = 10
        bytes[recordSize + 4] = 4

        val result = TableValidators.moveData(
            RomImage(bytes), offset = 0, count = 2, recordSize = recordSize, generation = 3,
        )

        assertTrue(result.compatible)
        assertEquals(2, result.validRecords)
    }

    @Test
    fun trimsAdjacentDataEvenWhenAChanceRecordInsideTheInvalidWindowLooksPlausible() {
        val recordSize = 12
        val realMoves = 20
        val bytes = ByteArray(recordSize * 40)
        repeat(realMoves) { index ->
            val base = index * recordSize
            bytes[base + 1] = 40
            bytes[base + 2] = 9
            bytes[base + 3] = 100
            bytes[base + 4] = 35
        }
        repeat(20) { index ->
            val base = (realMoves + index) * recordSize
            bytes[base] = 3
            bytes[base + 1] = 3
            bytes[base + 2] = 6
            bytes[base + 3] = 3
            bytes[base + 4] = 3
        }
        bytes[(realMoves + 2) * recordSize + 3] = 10

        val result = TableValidators.moveData(
            RomImage(bytes), offset = 0, count = 40, recordSize = recordSize, generation = 3,
        )

        assertTrue(result.compatible)
        assertEquals(realMoves, result.totalRecords)
    }

    @Test
    fun trimsAMostlyInvalidTrailingSuffixAfterACompleteMovePrefix() {
        val recordSize = 12
        val realMoves = 20
        val totalRecords = 40
        val bytes = ByteArray(recordSize * totalRecords)
        repeat(realMoves) { index ->
            val base = index * recordSize
            bytes[base + 1] = 40
            bytes[base + 2] = 9
            bytes[base + 3] = 100
            bytes[base + 4] = 35
        }

        // Mirrors a ROM where the inferred name count runs into readable menu
        // labels. Their aligned bytes accidentally resemble several moves, so
        // neither an eight-record run nor the first 16-row window is enough.
        val invalidSuffixRows = setOf(0, 3, 4, 6, 7, 8, 16, 17, 18, 19)
        repeat(totalRecords - realMoves) { suffixIndex ->
            val base = (realMoves + suffixIndex) * recordSize
            bytes[base + 1] = 40
            bytes[base + 2] = 9
            bytes[base + 3] = if (suffixIndex in invalidSuffixRows) 3 else 100
            bytes[base + 4] = 35
        }

        val result = TableValidators.moveData(
            RomImage(bytes), offset = 0, count = totalRecords, recordSize = recordSize, generation = 3,
        )

        assertTrue(result.compatible)
        assertEquals(realMoves, result.totalRecords)
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
    fun toleratesAReservedTailInLargeExpandedBaseStatTables() {
        val count = 1_000
        val width = 28
        val bytes = ByteArray(count * width)
        repeat(895) { index ->
            val base = index * width
            repeat(6) { stat -> bytes[base + stat] = 50 }
            bytes[base + 6] = 1
            bytes[base + 7] = 2
        }

        assertTrue(TableValidators.baseStats(RomImage(bytes), 0, count, width, generation = 3).compatible)
        bytes[(894 * width)] = 0
        assertFalse(TableValidators.baseStats(RomImage(bytes), 0, count, width, generation = 3).compatible)
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
    fun locatesARelocatedFixedNameTableByItsCompleteShape() {
        val count = 24
        val width = 10
        val offset = 1_237
        val bytes = ByteArray(4_096)
        repeat(count) { index ->
            val base = offset + index * width
            bytes[base] = (0x80 + index % 26).toByte()
            bytes[base + 1] = 0x50
        }

        val located = TableValidators.locateFixedNameTable(
            RomImage(bytes), count, 10..12, PokemonTextCodec.gbEnglish,
        )

        assertEquals(offset, located?.offset)
        assertEquals(width, located?.recordSize)
    }

    @Test
    fun prefersTheCompleteFixedNameTableNearestTheInheritedLayout() {
        val count = 24
        val width = 10
        val bytes = ByteArray(4_096)
        listOf(500, 2_000).forEach { offset ->
            repeat(count) { index ->
                bytes[offset + index * width] = (0x80 + index % 26).toByte()
                bytes[offset + index * width + 1] = 0x50
            }
        }

        val located = TableValidators.locateFixedNameTable(
            RomImage(bytes), count, 10..10, PokemonTextCodec.gbEnglish, preferredOffset = 450,
        )

        assertEquals(500, located?.offset)
    }

    @Test
    fun locatesGen2NameTablesFromLeadingCanonicalRecords() {
        val bytes = ByteArray(4_096)
        val speciesOffset = 500
        listOf("BULBASAUR", "IVYSAUR", "VENUSAUR").forEachIndexed { index, name ->
            writeGbName(bytes, speciesOffset + index * 10, name, width = 10)
        }
        val moveOffset = 1_500
        bytes[moveOffset - 1] = 0x50
        var cursor = moveOffset
        listOf("POUND", "KARATE CHOP", "DOUBLESLAP").forEach { name ->
            cursor = writeGbName(bytes, cursor, name, width = name.length + 1)
        }

        assertEquals(
            speciesOffset,
            TableValidators.locateFixedNameSequenceNear(
                RomImage(bytes), speciesOffset + 20, 10..10, PokemonTextCodec.gbEnglish,
                listOf("BULBASAUR", "IVYSAUR", "VENUSAUR"), searchRadius = 64,
            )?.offset,
        )
        assertEquals(
            moveOffset,
            TableValidators.locateVariableNameSequenceNear(
                RomImage(bytes), moveOffset - 20, PokemonTextCodec.gbEnglish,
                listOf("POUND", "KARATE CHOP", "DOUBLESLAP"), searchRadius = 64,
            ),
        )
    }

    @Test
    fun locatesARelocatedGen2BaseStatTableByItsCompleteShape() {
        val count = 32
        val width = 36
        val offset = 773
        val bytes = ByteArray(4_096)
        repeat(count) { index ->
            val base = offset + index * width
            bytes[base] = (index + 1).toByte()
            repeat(6) { stat -> bytes[base + 1 + stat] = (40 + stat).toByte() }
            bytes[base + 7] = 2
            bytes[base + 8] = 3
        }

        val located = TableValidators.locateBaseStatTable(
            RomImage(bytes), count, 32..40, generation = 2,
        )

        assertEquals(offset, located?.offset)
        assertEquals(width, located?.recordSize)
    }

    private fun writeGbName(bytes: ByteArray, offset: Int, name: String, width: Int): Int {
        name.forEachIndexed { index, character ->
            bytes[offset + index] = if (character == ' ') 0x7F else (0x80 + character.code - 'A'.code).toByte()
        }
        bytes[offset + name.length] = 0x50
        return offset + width
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
