package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
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
    fun acceptsFullWidthGbaNamesWithoutATerminatorWhenEveryByteDecodes() {
        val width = 13
        val bytes = ByteArray(width) { index ->
            if (index == 4) 0 else (0xBB + index % 26).toByte()
        }

        val result = TableValidators.fixedNames(
            RomImage(bytes),
            offset = 0,
            count = 1,
            width = width,
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertTrue(result.compatible)
        assertEquals(1, result.validRecords)
    }

    @Test
    fun rejectsUnterminatedFullWidthGbaNamesContainingAnInvalidByte() {
        val width = 13
        val bytes = ByteArray(width) { (0xBB + it % 26).toByte() }
        bytes[7] = 0x7F

        val result = TableValidators.fixedNames(
            RomImage(bytes),
            offset = 0,
            count = 1,
            width = width,
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertFalse(result.compatible)
        assertEquals(0, result.validRecords)
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
    fun retainsTrailingRunOfFullWidthGbNamesAtBinaryBoundary() {
        val width = 10
        val bytes = ByteArray(width * 12)
        listOf("ALPHA", "BRAVO", "CHARLIE", "DELTA", "ECHO")
            .forEachIndexed { index, name -> writeGbName(bytes, index * width, name, width) }
        listOf("BELLSPROUT", "WEEPINBELL", "VICTREEBEL", "SCREAMTAIL", "PERRSERKER")
            .forEachIndexed { index, name -> writeGbName(bytes, (index + 5) * width, name, width) }

        val count = TableValidators.inferFixedNameCount(
            RomImage(bytes), 0, width, PokemonTextCodec.gbEnglish, minimumCount = 5, maximumCount = 12,
        )

        assertEquals(10, count)
    }

    @Test
    fun rejectsAnUnprovenSingleFullWidthGbNameAtBinaryBoundary() {
        val width = 10
        val bytes = ByteArray(width * 8)
        listOf("ALPHA", "BRAVO", "CHARLIE", "DELTA", "ECHO")
            .forEachIndexed { index, name -> writeGbName(bytes, index * width, name, width) }
        writeGbName(bytes, width * 5, "BELLSPROUT", width)

        val count = TableValidators.inferFixedNameCount(
            RomImage(bytes), 0, width, PokemonTextCodec.gbEnglish, minimumCount = 5, maximumCount = 8,
        )

        assertEquals(5, count)
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
            TableValidators.locateGbEnglishVariableNameSequenceNear(
                RomImage(bytes), moveOffset - 20,
                listOf("POUND", "KARATE CHOP", "DOUBLESLAP"), searchRadius = 64,
            ),
        )
    }

    @Test
    fun legacyGbEnglishLocatorRejectsCanonicalSuffixInsideALongerName() {
        val bytes = ByteArray(128)
        bytes[9] = 0x50
        var cursor = 10
        listOf("XPOUND", "KARATE CHOP", "DOUBLESLAP").forEach { name ->
            cursor = writeGbName(bytes, cursor, name, width = name.length + 1)
        }
        assertEquals(null, TableValidators.locateGbEnglishVariableNameSequenceNear(
            RomImage(bytes), 11, listOf("POUND", "KARATE CHOP", "DOUBLESLAP"), searchRadius = 64,
        ))
    }

    @Test
    fun legacyGbEnglishLocatorPreservesCaseInsensitiveNearestAndTieSelection() {
        val bytes = ByteArray(64)
        for (offset in listOf(10, 30)) {
            bytes[offset - 1] = 0x50
            writeGbName(bytes, offset, "POUND", width = 6)
        }
        assertEquals(10, TableValidators.locateGbEnglishVariableNameSequenceNear(
            RomImage(bytes), 20, listOf("pound"), searchRadius = 10,
        ))
        assertEquals(30, TableValidators.locateGbEnglishVariableNameSequenceNear(
            RomImage(bytes), 25, listOf("POUND"), searchRadius = 10,
        ))
        assertEquals(null, TableValidators.locateGbEnglishVariableNameSequenceNear(
            RomImage(bytes), 20, listOf("POUND"), searchRadius = 9,
        ))
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
        if (name.length < width) bytes[offset + name.length] = 0x50
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
    fun stopsFixedNameCountBeforeMixedCaseAdjacentProse() {
        val width = 13
        val bytes = ByteArray(width * 10) { 0xFF.toByte() }
        listOf("Stench", "Drizzle", "Speed Boost", "Overgrow", "Pastel Veil")
            .forEachIndexed { index, name -> writeGbaFixedName(bytes, index * width, name, width) }

        // Aesthetic Red places battle-message prose immediately after its
        // ability names. These first two chunks are valid, full-width text,
        // while the third is a terminated continuation of the same sentence.
        writeGbaFixedName(bytes, width * 5, "The effects  ", width, terminate = false)
        writeGbaFixedName(bytes, width * 6, "of weather di", width, terminate = false)
        writeGbaFixedName(bytes, width * 7, "sappeared.", width)

        val count = TableValidators.inferFixedNameCount(
            RomImage(bytes), 0, width, PokemonTextCodec.gbaEnglish, minimumCount = 5, maximumCount = 10,
        )

        assertEquals(5, count)
    }

    @Test
    fun stopsAbilityNamesBeforeFullWidthProseAndPunctuation() {
        val width = 17
        val bytes = ByteArray(width * 12) { 0xFF.toByte() }
        listOf("Stench", "Drizzle", "Speed Boost", "Pastel Veil", "Zero to Hero")
            .forEachIndexed { index, name -> writeGbaFixedName(bytes, index * width, name, width) }

        writeGbaFixedName(bytes, width * 5, "The effects of we", width, terminate = false)
        writeGbaFixedName(bytes, width * 6, "ather disappeared", width, terminate = false)
        writeGbaFixedName(bytes, width * 7, ".", width)
        writeGbaFixedName(bytes, width * 8, "its pressure.", width)

        val count = TableValidators.inferFixedNameCount(
            RomImage(bytes), 0, width, PokemonTextCodec.gbaEnglish, minimumCount = 5, maximumCount = 12,
        )

        assertEquals(5, count)
    }

    @Test
    fun retainsInternalFullWidthNameWhenFollowedByTerminatedName() {
        val width = 13
        val bytes = ByteArray(width * 5) { 0xFF.toByte() }
        writeGbaFixedName(bytes, 0, "Stench", width)
        writeGbaFixedName(bytes, width, "Mega Launcher", width, terminate = false)
        writeGbaFixedName(bytes, width * 2, "Overgrow", width)

        val count = TableValidators.inferFixedNameCount(
            RomImage(bytes), 0, width, PokemonTextCodec.gbaEnglish, minimumCount = 3, maximumCount = 5,
        )

        assertEquals(3, count)
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

    private fun writeGbaFixedName(
        bytes: ByteArray,
        offset: Int,
        name: String,
        width: Int,
        terminate: Boolean = true,
    ) {
        require(name.length <= width)
        name.forEachIndexed { index, character ->
            bytes[offset + index] = when (character) {
                ' ' -> 0x00
                in 'A'..'Z' -> 0xBB + character.code - 'A'.code
                in 'a'..'z' -> 0xD5 + character.code - 'a'.code
                '.' -> 0xAD
                else -> error("unsupported test character $character")
            }.toByte()
        }
        if (terminate && name.length < width) bytes[offset + name.length] = 0xFF.toByte()
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
    fun validatesExtendedGen1TypeIdsUsedByCompiledConsumers() {
        val records = mutableListOf<Byte>()
        repeat(10) { index ->
            records += (28 + index % 2).toByte()
            records += (index % 29).toByte()
            records += if (index % 2 == 0) 5 else 20
        }
        records += 0xFF.toByte()

        val result = TableValidators.typeChart(RomImage(records.toByteArray()), 0, generation = 1)

        assertTrue(result.compatible)
        assertEquals(10, result.validRecords)
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

    @Test
    fun resolvesReferencedQ412SquareTypeChartWithoutExpansionMetadata() {
        val pointerOffset = 4
        val chartOffset = 64
        val typeCount = 20
        val bytes = ByteArray(chartOffset + typeCount * typeCount * 4 + 4) { 0x7F }
        writeU32le(bytes, pointerOffset, 0x08000000L + chartOffset)
        writeU32le(bytes, 8, 0x08000000L + chartOffset + typeCount * typeCount * 4)
        repeat(typeCount * typeCount) { index -> writeU32le(bytes, chartOffset + index * 4, 4096) }
        val nonNeutral = listOf(0L, 819L, 2048L, 8192L, 20480L)
        repeat(typeCount) { index ->
            val multiplier = nonNeutral[index % nonNeutral.size]
            writeU32le(bytes, chartOffset + (index * typeCount + (index + 1) % typeCount) * 4, multiplier)
        }

        val result = TableValidators.resolveGen3TypeChart(RomImage(bytes), inheritedOffset = null, activeTypeLowerBound = 19)

        assertTrue(result.compatible)
        assertEquals(chartOffset, result.offset)
        assertEquals(typeCount * typeCount, result.totalRecords)
        assertEquals(typeCount * 4, result.recordSize)
        assertEquals(4, result.elementSize)
    }

    @Test
    fun doesNotTreatUnreferencedQ412ValuesAsATypeChart() {
        val chartOffset = 64
        val typeCount = 20
        val bytes = ByteArray(chartOffset + typeCount * typeCount * 4 + 4) { 0x7F }
        repeat(typeCount * typeCount) { index -> writeU32le(bytes, chartOffset + index * 4, 4096) }
        writeU32le(bytes, 4, 0x08000000L + chartOffset + typeCount * typeCount * 4)
        writeU32le(bytes, chartOffset, 0)
        writeU32le(bytes, chartOffset + 4, 2048)
        writeU32le(bytes, chartOffset + 8, 8192)

        val result = TableValidators.resolveGen3TypeChart(RomImage(bytes), inheritedOffset = null, activeTypeLowerBound = typeCount)

        assertFalse(result.compatible)
    }

    @Test
    fun rejectsPlausibleUnreferencedQ412MatrixEvenWhenInherited() {
        val chartOffset = 64
        val typeCount = 20
        val bytes = ByteArray(chartOffset + typeCount * typeCount * 4 + 4) { 0x7F }
        writeU32le(bytes, 4, 0x08000000L + chartOffset + typeCount * typeCount * 4)
        repeat(typeCount * typeCount) { index -> writeU32le(bytes, chartOffset + index * 4, 4096) }
        val nonNeutral = listOf(0L, 819L, 2048L, 8192L, 20480L)
        repeat(typeCount) { index ->
            val multiplier = nonNeutral[index % nonNeutral.size]
            writeU32le(bytes, chartOffset + (index * typeCount + (index + 1) % typeCount) * 4, multiplier)
        }

        val result = TableValidators.resolveGen3TypeChart(RomImage(bytes), inheritedOffset = chartOffset, activeTypeLowerBound = typeCount)

        assertFalse(result.compatible)
        assertEquals(chartOffset, result.offset)
        assertEquals(3, result.recordSize)
    }

    @Test
    fun resolvesQ412SquareBeforeTrailingLowPaddingWord() {
        val pointerOffset = 4
        val chartOffset = 64
        val typeCount = 20
        val bytes = ByteArray(chartOffset + typeCount * typeCount * 4 + 8) { 0x7F }
        writeU32le(bytes, pointerOffset, 0x08000000L + chartOffset)
        writeU32le(bytes, 8, 0x08000000L + chartOffset + typeCount * typeCount * 4)
        writePlausibleQ412Matrix(bytes, chartOffset, typeCount)
        writeU32le(bytes, chartOffset + typeCount * typeCount * 4, 0)

        val result = TableValidators.resolveGen3TypeChart(RomImage(bytes), inheritedOffset = null, activeTypeLowerBound = 19)

        assertTrue(result.compatible)
        assertEquals(chartOffset, result.offset)
        assertEquals(typeCount * typeCount, result.totalRecords)
    }

    @Test
    fun doesNotInflateQ412SquareWhenLowPaddingReachesNextSquare() {
        val pointerOffset = 4
        val chartOffset = 64
        val typeCount = 20
        val paddedTypeCount = typeCount + 1
        val bytes = ByteArray(chartOffset + paddedTypeCount * paddedTypeCount * 4 + 4) { 0x7F }
        writeU32le(bytes, pointerOffset, 0x08000000L + chartOffset)
        writeU32le(bytes, 8, 0x08000000L + chartOffset + typeCount * typeCount * 4)
        writePlausibleQ412Matrix(bytes, chartOffset, typeCount)
        val padding = listOf(0L, 819L, 2048L, 4096L, 8192L)
        repeat(paddedTypeCount * paddedTypeCount - typeCount * typeCount) { index ->
            writeU32le(bytes, chartOffset + (typeCount * typeCount + index) * 4, padding[index % padding.size])
        }

        val result = TableValidators.resolveGen3TypeChart(RomImage(bytes), inheritedOffset = null, activeTypeLowerBound = 19)

        assertTrue(result.compatible)
        assertEquals(typeCount * typeCount, result.totalRecords)
        assertEquals(typeCount * 4, result.recordSize)
    }

    @Test
    fun resolvesMaximumSupportedQ412SquareWithInvalidFollower() {
        val pointerOffset = 4
        val chartOffset = 64
        val typeCount = 64
        val bytes = ByteArray(chartOffset + typeCount * typeCount * 4 + 4) { 0x7F }
        writeU32le(bytes, pointerOffset, 0x08000000L + chartOffset)
        writeU32le(bytes, 8, 0x08000000L + chartOffset + typeCount * typeCount * 4)
        writePlausibleQ412Matrix(bytes, chartOffset, typeCount)

        val result = TableValidators.resolveGen3TypeChart(RomImage(bytes), inheritedOffset = null, activeTypeLowerBound = typeCount)

        assertTrue(result.compatible)
        assertEquals(typeCount * typeCount, result.totalRecords)
        assertEquals(typeCount * 4, result.recordSize)
    }

    @Test
    fun enclosingQ412MatrixOutranksMoreFrequentlyReferencedInteriorSuffix() {
        val rootPointer = 4
        val interiorPointers = listOf(8, 12, 16)
        val chartOffset = 64
        val typeCount = 32
        val interiorOffset = chartOffset + 14 * typeCount * 4
        val bytes = ByteArray(chartOffset + typeCount * typeCount * 4 + 4) { 0x7F }
        writeU32le(bytes, rootPointer, 0x08000000L + chartOffset)
        interiorPointers.forEach { writeU32le(bytes, it, 0x08000000L + interiorOffset) }
        writeU32le(bytes, 20, 0x08000000L + chartOffset + typeCount * typeCount * 4)
        writePlausibleQ412Matrix(bytes, chartOffset, typeCount)

        val result = TableValidators.resolveGen3TypeChart(RomImage(bytes), inheritedOffset = null, activeTypeLowerBound = 18)

        assertTrue(result.compatible)
        assertEquals(chartOffset, result.offset)
        assertEquals(typeCount * typeCount, result.totalRecords)
    }

    @Test
    fun acceptsQ412MatrixWithDenseNonNeutralColumnWhenTypeCountIsKnown() {
        val pointerOffset = 4
        val chartOffset = 64
        val typeCount = 20
        val bytes = ByteArray(chartOffset + typeCount * typeCount * 4 + 4) { 0x7F }
        writeU32le(bytes, pointerOffset, 0x08000000L + chartOffset)
        writeU32le(bytes, 8, 0x08000000L + chartOffset + typeCount * typeCount * 4)
        writePlausibleQ412Matrix(bytes, chartOffset, typeCount)
        repeat(typeCount) { row -> writeU32le(bytes, chartOffset + row * typeCount * 4, 2048) }

        val result = TableValidators.resolveGen3TypeChart(
            RomImage(bytes),
            inheritedOffset = null,
            activeTypeLowerBound = 19,
        )

        assertTrue(result.compatible)
        assertEquals(typeCount * typeCount, result.totalRecords)
    }

    @Test
    fun infersQ412DimensionFromReferencedBoundaryWithoutActiveTypeLowerBound() {
        val pointerOffset = 4
        val chartOffset = 64
        val typeCount = 20
        val bytes = ByteArray(chartOffset + typeCount * typeCount * 4 + 4) { 0x7F }
        writeU32le(bytes, pointerOffset, 0x08000000L + chartOffset)
        writeU32le(bytes, 8, 0x08000000L + chartOffset + typeCount * typeCount * 4)
        writePlausibleQ412Matrix(bytes, chartOffset, typeCount)

        val result = TableValidators.resolveGen3TypeChart(RomImage(bytes), inheritedOffset = null)

        assertTrue(result.compatible)
        assertEquals(typeCount * typeCount, result.totalRecords)
    }

    @Test
    fun rejectsReferencedQ412RootWithoutReferencedEndBoundary() {
        val chartOffset = 64
        val typeCount = 20
        val bytes = ByteArray(chartOffset + typeCount * typeCount * 4 + 4) { 0x7F }
        writeU32le(bytes, 4, 0x08000000L + chartOffset)
        writePlausibleQ412Matrix(bytes, chartOffset, typeCount)

        val result = TableValidators.resolveGen3TypeChart(RomImage(bytes), inheritedOffset = null, activeTypeLowerBound = 19)

        assertFalse(result.compatible)
    }

    @Test
    fun rejectsAmbiguousNonOverlappingReferencedQ412Roots() {
        val typeCount = 20
        val first = 64
        val second = first + typeCount * typeCount * 4 + 128
        val secondEnd = second + typeCount * typeCount * 4
        val bytes = ByteArray(secondEnd + 4) { 0x7F }
        writeU32le(bytes, 4, 0x08000000L + first)
        writeU32le(bytes, 8, 0x08000000L + first + typeCount * typeCount * 4)
        writeU32le(bytes, 12, 0x08000000L + second)
        writeU32le(bytes, 16, 0x08000000L + secondEnd)
        writePlausibleQ412Matrix(bytes, first, typeCount)
        writePlausibleQ412Matrix(bytes, second, typeCount)

        val result = TableValidators.resolveGen3TypeChart(RomImage(bytes), inheritedOffset = null, activeTypeLowerBound = 19)

        assertFalse(result.compatible)
        assertTrue(result.reasons.single().contains("ambiguous"))
        assertTrue(result.ambiguous)
        assertTrue(result.reviewRecommended)
    }

    @Test
    fun rejectsAmbiguousReferencedBoundariesForTheSameQ412Root() {
        val chartOffset = 64
        val smallerTypeCount = 20
        val largerTypeCount = 21
        val smallerEnd = chartOffset + smallerTypeCount * smallerTypeCount * 4
        val largerEnd = chartOffset + largerTypeCount * largerTypeCount * 4
        val bytes = ByteArray(largerEnd + 4) { 0x7F }
        writeU32le(bytes, 4, 0x08000000L + chartOffset)
        writeU32le(bytes, 8, 0x08000000L + smallerEnd)
        writeU32le(bytes, 12, 0x08000000L + largerEnd)
        writePlausibleQ412Matrix(bytes, chartOffset, smallerTypeCount)
        val padding = listOf(0L, 819L, 2048L, 4096L, 8192L)
        repeat(largerTypeCount * largerTypeCount - smallerTypeCount * smallerTypeCount) { index ->
            writeU32le(bytes, smallerEnd + index * 4, padding[index % padding.size])
        }

        val result = TableValidators.resolveGen3TypeChart(
            RomImage(bytes),
            inheritedOffset = null,
            activeTypeLowerBound = 19,
        )

        assertFalse(result.compatible)
        assertTrue(result.reasons.single().contains("ambiguous"))
        assertTrue(result.ambiguous)
        assertTrue(result.reviewRecommended)
    }

    @Test
    fun resolvesReferencedU16Q412TypeChartFromInverseCrossTableBoundary() {
        val chartOffset = 66
        val typeCount = 19
        val pairEnd = chartOffset + typeCount * typeCount * 4
        val bytes = ByteArray(pairEnd + 8) { 0x7F }
        writeU32le(bytes, 4, 0x08000000L + chartOffset)
        writeU32le(bytes, 8, 0x08000000L + pairEnd)
        writePlausibleU16Q412TypeChartPair(bytes, chartOffset, typeCount)

        val result = TableValidators.resolveGen3TypeChart(
            RomImage(bytes),
            inheritedOffset = null,
            activeTypeLowerBound = typeCount,
        )

        assertTrue(result.compatible)
        assertEquals(chartOffset, result.offset)
        assertEquals(typeCount * typeCount, result.totalRecords)
        assertEquals(typeCount * 2, result.recordSize)
        assertEquals(2, result.elementSize)
    }

    @Test
    fun rejectsUnreferencedU16Q412TypeChartPair() {
        val chartOffset = 66
        val typeCount = 19
        val pairEnd = chartOffset + typeCount * typeCount * 4
        val bytes = ByteArray(pairEnd + 8) { 0x7F }
        writePlausibleU16Q412TypeChartPair(bytes, chartOffset, typeCount)

        val result = TableValidators.resolveGen3TypeChart(
            RomImage(bytes),
            inheritedOffset = null,
            activeTypeLowerBound = typeCount,
        )

        assertFalse(result.compatible)
    }

    @Test
    fun rejectsReferencedU16Q412DisplayDecoyWithoutCrossTableBoundary() {
        val chartOffset = 66
        val typeCount = 19
        val bytes = ByteArray(chartOffset + typeCount * typeCount * 2 + 8) { 0x7F }
        writeU32le(bytes, 4, 0x08000000L + chartOffset)
        writePlausibleU16Q412Matrix(bytes, chartOffset, typeCount)

        val result = TableValidators.resolveGen3TypeChart(
            RomImage(bytes),
            inheritedOffset = null,
            activeTypeLowerBound = typeCount,
        )

        assertFalse(result.compatible)
    }

    @Test
    fun enclosingU16Q412PairOutranksShiftedInteriorPair() {
        val chartOffset = 66
        val typeCount = 19
        val pairSize = typeCount * typeCount * 4
        val bytes = ByteArray(chartOffset + pairSize + 8) { 0x7F }
        writeU32le(bytes, 4, 0x08000000L + chartOffset)
        writeU32le(bytes, 8, 0x08000000L + chartOffset + pairSize)
        writeU32le(bytes, 12, 0x08000000L + chartOffset + 2)
        writeU32le(bytes, 16, 0x08000000L + chartOffset + pairSize + 2)
        writePlausibleU16Q412TypeChartPair(bytes, chartOffset, typeCount)
        writeU16le(bytes, chartOffset + pairSize, 4096)

        val result = TableValidators.resolveGen3TypeChart(
            RomImage(bytes),
            inheritedOffset = null,
            activeTypeLowerBound = typeCount,
        )

        assertTrue(result.compatible)
        assertEquals(chartOffset, result.offset)
    }

    @Test
    fun resolvesU16Q412DimensionAboveActiveTypeLowerBound() {
        val chartOffset = 66
        val activeTypeCount = 19
        val chartTypeCount = 20
        val matrixWords = chartTypeCount * chartTypeCount
        val pairEnd = chartOffset + matrixWords * 4
        val bytes = ByteArray(pairEnd + 8) { 0x7F }
        writeU32le(bytes, 4, 0x08000000L + chartOffset)
        writeU32le(bytes, 8, 0x08000000L + pairEnd)
        writePlausibleU16Q412TypeChartPair(bytes, chartOffset, chartTypeCount)

        val result = TableValidators.resolveGen3TypeChart(
            RomImage(bytes),
            inheritedOffset = null,
            activeTypeLowerBound = activeTypeCount,
        )

        assertTrue(result.compatible)
        assertEquals(chartTypeCount * chartTypeCount, result.totalRecords)
        assertEquals(chartTypeCount * 2, result.recordSize)
    }

    @Test
    fun doesNotInflateU16Q412PairWithoutReferencedLargerBoundary() {
        val chartOffset = 66
        val typeCount = 19
        val largerTypeCount = 20
        val pairEnd = chartOffset + typeCount * typeCount * 4
        val bytes = ByteArray(chartOffset + largerTypeCount * largerTypeCount * 4 + 8) { 0 }
        writeU32le(bytes, 4, 0x08000000L + chartOffset)
        writeU32le(bytes, 8, 0x08000000L + pairEnd)
        writePlausibleU16Q412TypeChartPair(bytes, chartOffset, typeCount)

        val result = TableValidators.resolveGen3TypeChart(
            RomImage(bytes),
            inheritedOffset = null,
            activeTypeLowerBound = typeCount,
        )

        assertTrue(result.compatible)
        assertEquals(typeCount * typeCount, result.totalRecords)
    }

    @Test
    fun rejectsAmbiguousNonOverlappingReferencedU16Q412Pairs() {
        val typeCount = 19
        val pairSize = typeCount * typeCount * 4
        val first = 66
        val second = first + pairSize + 64
        val bytes = ByteArray(second + pairSize + 8) { 0x7F }
        writeU32le(bytes, 4, 0x08000000L + first)
        writeU32le(bytes, 8, 0x08000000L + first + pairSize)
        writeU32le(bytes, 12, 0x08000000L + second)
        writeU32le(bytes, 16, 0x08000000L + second + pairSize)
        writePlausibleU16Q412TypeChartPair(bytes, first, typeCount)
        writePlausibleU16Q412TypeChartPair(bytes, second, typeCount)

        val result = TableValidators.resolveGen3TypeChart(
            RomImage(bytes),
            inheritedOffset = null,
            activeTypeLowerBound = typeCount,
        )

        assertFalse(result.compatible)
        assertTrue(result.ambiguous)
        assertTrue(result.reviewRecommended)
    }

    @Test
    fun rejectsAmbiguousReferencedU16Q412DimensionsAtTheSameRoot() {
        val chartOffset = 66
        val smallerTypeCount = 18
        val largerTypeCount = 22
        val largerPairEnd = chartOffset + largerTypeCount * largerTypeCount * 4
        val bytes = ByteArray(largerPairEnd + 8) { 0x7F }
        writeU32le(bytes, 4, 0x08000000L + chartOffset)
        writeU32le(bytes, 8, 0x08000000L + chartOffset + smallerTypeCount * smallerTypeCount * 4)
        writeU32le(bytes, 12, 0x08000000L + largerPairEnd)
        writeAmbiguousU16Q412Pairs(bytes, chartOffset, smallerTypeCount, largerTypeCount)

        val result = TableValidators.resolveGen3TypeChart(
            RomImage(bytes),
            inheritedOffset = null,
            activeTypeLowerBound = smallerTypeCount,
        )

        assertFalse(result.compatible)
        assertEquals(2, result.totalRecords)
        assertTrue(result.ambiguous)
        assertTrue(result.reviewRecommended)
    }

    @Test
    fun enclosingU16Q412PairPrunesCandidateBeginningAtInverseMatrix() {
        val chartOffset = 66
        val typeCount = 19
        val matrixBytes = typeCount * typeCount * 2
        val pairBytes = matrixBytes * 2
        val interiorOffset = chartOffset + matrixBytes
        val bytes = ByteArray(chartOffset + matrixBytes * 3 + 8) { 0x7F }
        writeU32le(bytes, 4, 0x08000000L + chartOffset)
        writeU32le(bytes, 8, 0x08000000L + chartOffset + pairBytes)
        writeU32le(bytes, 12, 0x08000000L + interiorOffset)
        writeU32le(bytes, 16, 0x08000000L + interiorOffset + pairBytes)
        writeConsecutiveInverseU16Q412Matrices(bytes, chartOffset, typeCount, matrixCount = 3)

        val result = TableValidators.resolveGen3TypeChart(
            RomImage(bytes),
            inheritedOffset = null,
            activeTypeLowerBound = typeCount,
        )

        assertTrue(result.compatible)
        assertEquals(chartOffset, result.offset)
    }

    @Test
    fun prunedInteriorU16Q412PairDoesNotHideLaterIndependentRoot() {
        val chartOffset = 66
        val typeCount = 19
        val matrixBytes = typeCount * typeCount * 2
        val pairBytes = matrixBytes * 2
        val interiorOffset = chartOffset + matrixBytes
        val independentOffset = chartOffset + pairBytes
        val bytes = ByteArray(chartOffset + matrixBytes * 4 + 8) { 0x7F }
        writeU32le(bytes, 4, 0x08000000L + chartOffset)
        writeU32le(bytes, 8, 0x08000000L + interiorOffset)
        writeU32le(bytes, 12, 0x08000000L + independentOffset)
        writeU32le(bytes, 16, 0x08000000L + independentOffset + matrixBytes)
        writeU32le(bytes, 20, 0x08000000L + independentOffset + pairBytes)
        writeConsecutiveInverseU16Q412Matrices(bytes, chartOffset, typeCount, matrixCount = 4)

        val result = TableValidators.resolveGen3TypeChart(
            RomImage(bytes),
            inheritedOffset = null,
            activeTypeLowerBound = typeCount,
        )

        assertFalse(result.compatible)
        assertEquals(2, result.totalRecords)
        assertTrue(result.ambiguous)
        assertTrue(result.reasons.single().contains("0x${chartOffset.toString(16)}"))
        assertFalse(result.reasons.single().contains("0x${interiorOffset.toString(16)}"))
        assertTrue(result.reasons.single().contains("0x${independentOffset.toString(16)}"))
    }

    @Test
    fun rejectsReferencedU16Q412PairWithOneCorruptInverseWord() {
        val chartOffset = 66
        val typeCount = 19
        val matrixBytes = typeCount * typeCount * 2
        val pairEnd = chartOffset + matrixBytes * 2
        val bytes = ByteArray(pairEnd + 8) { 0x7F }
        writeU32le(bytes, 4, 0x08000000L + chartOffset)
        writeU32le(bytes, 8, 0x08000000L + pairEnd)
        writePlausibleU16Q412TypeChartPair(bytes, chartOffset, typeCount)
        writeU16le(bytes, chartOffset + matrixBytes, 2048)

        val result = TableValidators.resolveGen3TypeChart(
            RomImage(bytes),
            inheritedOffset = null,
            activeTypeLowerBound = typeCount,
        )

        assertFalse(result.compatible)
    }

    @Test
    fun derivesOfficialGen3TypeCountFromPlausibleBaseStats() {
        val typeCount = 18
        val bytes = ByteArray(typeCount * 28)
        repeat(typeCount) { index -> writePlausibleBaseStats(bytes, index * 28, index, (index + 1) % typeCount) }

        val result = TableValidators.inferGen3ActiveTypeCount(
            RomImage(bytes),
            TableLayout(0, typeCount, 28),
            typeCount,
        )

        assertEquals(18, result)
    }

    @Test
    fun derivesExpandedGen3TypeCountFromPlausibleBaseStats() {
        val typeCount = 34
        val bytes = ByteArray(typeCount * 28)
        repeat(typeCount) { index -> writePlausibleBaseStats(bytes, index * 28, index, (index + 1) % typeCount) }

        val result = TableValidators.inferGen3ActiveTypeCount(
            RomImage(bytes),
            TableLayout(0, typeCount, 28),
            typeCount,
        )

        assertEquals(34, result)
    }

    @Test
    fun derivesTypesAbove31EvenWhenLegacyBaseStatsValidatorRejectsThem() {
        val count = 20
        val bytes = ByteArray(count * 28)
        repeat(count) { index -> writePlausibleBaseStats(bytes, index * 28, 33, 33) }
        val rom = RomImage(bytes)

        val legacyValidation = TableValidators.baseStats(rom, 0, count, 28, generation = 3)
        val activeLowerBound = TableValidators.inferGen3ActiveTypeCount(
            rom,
            TableLayout(0, count, 28),
            count,
        )

        assertFalse(legacyValidation.compatible)
        assertEquals(34, activeLowerBound)
    }

    @Test
    fun ignoresCorruptPlaceholderTypeByteWhenBaseStatsAreImplausible() {
        val count = 19
        val bytes = ByteArray(count * 28)
        repeat(18) { index -> writePlausibleBaseStats(bytes, index * 28, index, (index + 1) % 18) }
        bytes[18 * 28 + 6] = 63
        bytes[18 * 28 + 7] = 63

        val result = TableValidators.inferGen3ActiveTypeCount(
            RomImage(bytes),
            TableLayout(0, count, 28),
            count,
        )

        assertEquals(18, result)
    }

    private fun writePlausibleQ412Matrix(bytes: ByteArray, offset: Int, typeCount: Int) {
        repeat(typeCount * typeCount) { index -> writeU32le(bytes, offset + index * 4, 4096) }
        val nonNeutral = listOf(0L, 819L, 2048L, 8192L, 20480L)
        repeat(typeCount) { row ->
            repeat(4) { variant ->
                writeU32le(
                    bytes,
                    offset + (row * typeCount + (row + variant + 1) % typeCount) * 4,
                    nonNeutral[(row + variant) % nonNeutral.size],
                )
            }
        }
    }

    private fun writePlausibleU16Q412TypeChartPair(bytes: ByteArray, offset: Int, typeCount: Int) {
        writePlausibleU16Q412Matrix(bytes, offset, typeCount)
        val values = typeCount * typeCount
        writeInverseU16Q412Matrix(bytes, offset, offset + values * 2, values)
    }

    private fun writeConsecutiveInverseU16Q412Matrices(
        bytes: ByteArray,
        offset: Int,
        typeCount: Int,
        matrixCount: Int,
    ) {
        val values = typeCount * typeCount
        val matrixBytes = values * 2
        writePlausibleU16Q412Matrix(bytes, offset, typeCount)
        repeat(matrixCount - 1) { index ->
            val sourceOffset = offset + index * matrixBytes
            writeInverseU16Q412Matrix(bytes, sourceOffset, sourceOffset + matrixBytes, values)
        }
    }

    private fun writeAmbiguousU16Q412Pairs(
        bytes: ByteArray,
        offset: Int,
        smallerTypeCount: Int,
        largerTypeCount: Int,
    ) {
        val smallerValues = smallerTypeCount * smallerTypeCount
        val largerValues = largerTypeCount * largerTypeCount
        val words = IntArray(largerValues * 2) { 4096 }
        val assigned = BooleanArray(words.size)

        fun paintComponent(seed: Int) {
            val pending = ArrayDeque<Pair<Int, Int>>()
            pending.add(seed to 2048)
            while (pending.isNotEmpty()) {
                val (index, value) = pending.removeFirst()
                if (index !in words.indices) continue
                if (assigned[index]) {
                    check(words[index] == value)
                    continue
                }
                assigned[index] = true
                words[index] = value
                val inverse = if (value == 2048) 8192 else 2048
                intArrayOf(
                    index - smallerValues,
                    index + smallerValues,
                    index - largerValues,
                    index + largerValues,
                ).forEach { neighbor ->
                    if (neighbor in words.indices) pending.add(neighbor to inverse)
                }
            }
        }

        var seed = 0
        while (
            words.take(smallerValues).count { it != 4096 } < smallerTypeCount ||
            words.take(largerValues).count { it != 4096 } < largerTypeCount
        ) {
            while (assigned[seed]) seed++
            paintComponent(seed)
        }
        check(words.take(smallerValues).count { it == 4096 } * 5 >= smallerValues * 2)
        check(words.take(largerValues).count { it == 4096 } * 5 >= largerValues * 2)
        words.forEachIndexed { index, value -> writeU16le(bytes, offset + index * 2, value) }
    }

    private fun writePlausibleU16Q412Matrix(bytes: ByteArray, offset: Int, typeCount: Int) {
        repeat(typeCount * typeCount) { index -> writeU16le(bytes, offset + index * 2, 4096) }
        repeat(typeCount) { row ->
            writeU16le(bytes, offset + (row * typeCount + (row + 1) % typeCount) * 2, 0)
            writeU16le(bytes, offset + (row * typeCount + (row + 2) % typeCount) * 2, 2048)
            writeU16le(bytes, offset + (row * typeCount + (row + 3) % typeCount) * 2, 8192)
        }
    }

    private fun writeInverseU16Q412Matrix(
        bytes: ByteArray,
        sourceOffset: Int,
        inverseOffset: Int,
        values: Int,
    ) {
        repeat(values) { index ->
            val source = bytes[sourceOffset + index * 2].toInt() and 0xFF or
                ((bytes[sourceOffset + index * 2 + 1].toInt() and 0xFF) shl 8)
            val inverse = when {
                source < 4096 -> 8192
                source > 4096 -> 2048
                else -> 4096
            }
            writeU16le(bytes, inverseOffset + index * 2, inverse)
        }
    }

    private fun writePlausibleBaseStats(
        bytes: ByteArray,
        offset: Int,
        primaryType: Int,
        secondaryType: Int,
    ) {
        repeat(6) { bytes[offset + it] = 50 }
        bytes[offset + 6] = primaryType.toByte()
        bytes[offset + 7] = secondaryType.toByte()
    }

    private fun writeU32le(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun writeU16le(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
}
