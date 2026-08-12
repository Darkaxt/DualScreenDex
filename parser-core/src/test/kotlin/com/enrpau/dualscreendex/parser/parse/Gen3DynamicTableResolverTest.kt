package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.GbaCompiledReferenceIndex
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class Gen3DynamicTableResolverTest {
    @Test
    fun trimsDarkVioletAdjacentMoveNamesFromInferredSpeciesExtent() {
        val proposedCount = 420
        val expectedCount = 412
        val names = 0x4000
        val stats = 0x6000
        val map = 0xA000
        val moves = 0xC000
        val bytes = ByteArray(0x10000)
        repeat(expectedCount) { id ->
            writeName(bytes, names + id * 11, "MON")
            writeValidStats(bytes, stats + id * 28, id)
        }
        Base64.getDecoder().decode(DARK_VIOLET_ADJACENT_MOVE_NAMES_BASE64).copyInto(bytes, names + expectedCount * 11)
        bytes.copyInto(
            destination = bytes,
            destinationOffset = moves,
            startIndex = names + expectedCount * 11,
            endIndex = names + proposedCount * 11,
        )
        repeat(proposedCount - 1) { index ->
            val speciesId = index + 1
            val dex = when (speciesId) {
                in 1..251 -> speciesId
                in 252..276 -> speciesId + 135
                in 277 until expectedCount -> speciesId - 25
                else -> speciesId - 160
            }
            writeU16(bytes, map + index * 2, dex)
        }
        val inherited = ProfileTables(
            speciesNames = TableLayout(names, proposedCount, 11),
            baseStats = TableLayout(stats, proposedCount, 28),
            moveNames = TableLayout(moves, 469, 13),
        )
        val references = GbaCompiledReferenceIndex(mapOf(map to 2))

        val resolved = Gen3DynamicTableResolver.reconcileSpeciesExtent(
            RomImage(bytes),
            inherited,
            proposedCount,
            references,
        )

        assertEquals(expectedCount, resolved.speciesCount)
        assertEquals(expectedCount, resolved.tables.speciesNames?.count)
        assertEquals(expectedCount, resolved.tables.baseStats?.count)
    }

    @Test
    fun preservesValidDuplicateTailForms() {
        val speciesCount = 420
        val names = 0x4000
        val stats = 0x6000
        val map = 0xA000
        val bytes = ByteArray(0x10000)
        repeat(speciesCount) { id ->
            writeName(bytes, names + id * 11, "FORM")
            writeValidStats(bytes, stats + id * 28, id)
        }
        repeat(speciesCount - 1) { index ->
            val speciesId = index + 1
            val dex = when (speciesId) {
                in 1..251 -> speciesId
                in 252..276 -> speciesId + 135
                in 277..411 -> speciesId - 25
                else -> speciesId - 160
            }
            writeU16(bytes, map + index * 2, dex)
        }
        val inherited = ProfileTables(
            speciesNames = TableLayout(names, speciesCount, 11),
            baseStats = TableLayout(stats, speciesCount, 28),
        )
        val references = GbaCompiledReferenceIndex(mapOf(map to 2))

        val resolved = Gen3DynamicTableResolver.reconcileSpeciesExtent(
            RomImage(bytes),
            inherited,
            speciesCount,
            references,
        )

        assertEquals(speciesCount, resolved.speciesCount)
        assertEquals(speciesCount, resolved.tables.speciesNames?.count)
        assertEquals(speciesCount, resolved.tables.baseStats?.count)
    }

    @Test
    fun staleInheritedStatsCannotTrimDuplicateTailBeforeValidRelocatedStatsAreResolved() {
        val speciesCount = 420
        val names = 0x4000
        val staleStats = 0x6000
        val relocatedStats = 0x9000
        val map = 0xC000
        val moves = 0xE000
        val bytes = ByteArray(0x14000)
        repeat(412) { id -> writeName(bytes, names + id * 11, "MON") }
        Base64.getDecoder().decode(DARK_VIOLET_ADJACENT_MOVE_NAMES_BASE64).copyInto(bytes, names + 412 * 11)
        bytes.copyInto(
            destination = bytes,
            destinationOffset = moves,
            startIndex = names + 412 * 11,
            endIndex = names + speciesCount * 11,
        )
        repeat(speciesCount) { id -> writeValidStats(bytes, relocatedStats + id * 28, id) }
        repeat(speciesCount - 1) { index ->
            val speciesId = index + 1
            val dex = when (speciesId) {
                in 1..251 -> speciesId
                in 252..276 -> speciesId + 135
                in 277..411 -> speciesId - 25
                else -> speciesId - 160
            }
            writeU16(bytes, map + index * 2, dex)
        }
        writePointer(bytes, 0x200, relocatedStats)
        writePointer(bytes, 0x204, relocatedStats)
        val inherited = ProfileTables(
            speciesNames = TableLayout(names, speciesCount, 11),
            baseStats = TableLayout(staleStats, speciesCount, 28),
            moveNames = TableLayout(moves, 469, 13),
        )
        val references = GbaCompiledReferenceIndex(mapOf(map to 2))

        val extent = Gen3DynamicTableResolver.reconcileSpeciesExtent(
            RomImage(bytes),
            inherited,
            speciesCount,
            references,
        )
        val relocated = Gen3DynamicTableResolver.resolveWithEvidence(
            RomImage(bytes),
            extent.tables,
            extent.speciesCount,
            moveCount = 469,
        )

        assertEquals(speciesCount, extent.speciesCount)
        assertEquals(speciesCount, extent.tables.speciesNames?.count)
        assertEquals(relocatedStats, relocated.tables.baseStats?.offset)
        assertEquals(speciesCount, relocated.tables.baseStats?.count)
    }

    @Test
    fun relocatesReferencedDpeStatsAndCfruMoveRecordsWhenPublishedSlotsChangedMeaning() {
        val bytes = ByteArray(0x12000)
        val wrongStats = 0x2000
        val wrongMoves = 0x3000
        val stats = 0x5000
        val moves = 0x8000
        val speciesCount = 80
        val moveCount = 70

        repeat(speciesCount) { id ->
            val base = stats + id * 28
            if (id > 0) {
                repeat(6) { field -> bytes[base + field] = (35 + id % 80 + field).toByte() }
                bytes[base + 6] = (id % 24).toByte()
                bytes[base + 7] = ((id + 1) % 24).toByte()
            }
        }
        repeat(moveCount) { id ->
            val base = moves + id * 16
            if (id > 0) {
                writeU16(bytes, base, id % 300)
                writeU16(bytes, base + 2, 20 + id % 150)
                bytes[base + 4] = (id % 24).toByte()
                bytes[base + 5] = 100
                bytes[base + 6] = 20
                bytes[base + 7] = 10
                bytes[base + 9] = if (id % 2 == 0) 1 else 0
                bytes[base + 10] = (id % 3).toByte()
            }
        }
        repeat(5) { writePointer(bytes, 0x100 + it * 4, stats) }
        repeat(4) { writePointer(bytes, 0x140 + it * 4, moves) }

        val resolved = Gen3DynamicTableResolver.resolve(
            RomImage(bytes),
            ProfileTables(
                baseStats = TableLayout(wrongStats, speciesCount, 28),
                moveData = TableLayout(wrongMoves, moveCount, 12),
            ),
            speciesCount,
            moveCount,
        )

        assertEquals(stats, resolved.baseStats?.offset)
        assertEquals(28, resolved.baseStats?.recordSize)
        assertEquals(moves, resolved.moveData?.offset)
        assertEquals(16, resolved.moveData?.recordSize)
        assertEquals(TableRecordFormat.CFRU_MOVE_16, resolved.moveData?.format)
    }

    @Test
    fun acceptsStructurallyValidCfruMovesWithSparseCustomValues() {
        val bytes = ByteArray(0x14000)
        val wrongMoves = 0x3000
        val moves = 0x9000
        val speciesCount = 80
        val moveCount = 100

        repeat(moveCount) { id ->
            val base = moves + id * 16
            if (id > 0) {
                writeU16(bytes, base, id % 300)
                writeU16(bytes, base + 2, 20 + id % 150)
                bytes[base + 4] = (id % 24).toByte()
                bytes[base + 5] = 100
                bytes[base + 6] = 20
                bytes[base + 10] = (id % 3).toByte()
            }
        }
        // Expansion hacks legitimately use otherwise reserved values for a small custom subset.
        listOf(14, 48, 78, 80).forEachIndexed { index, id ->
            val base = moves + id * 16
            bytes[base + 4] = (32 + index).toByte()
            bytes[base + 5] = (200 + index).toByte()
            bytes[base + 12] = (index + 1).toByte()
        }
        repeat(4) { writePointer(bytes, 0x200 + it * 4, moves) }

        val resolved = Gen3DynamicTableResolver.resolve(
            RomImage(bytes),
            ProfileTables(moveData = TableLayout(wrongMoves, moveCount, 12)),
            speciesCount,
            moveCount,
        )

        assertEquals(moves, resolved.moveData?.offset)
        assertEquals(16, resolved.moveData?.recordSize)
        assertEquals(TableRecordFormat.CFRU_MOVE_16, resolved.moveData?.format)
    }

    @Test
    fun resolvesExpandedBattleEngineMoveRecordsWithFlagsAndZMoveFields() {
        val bytes = ByteArray(0x16000)
        val wrongMoves = 0x3000
        val moves = 0xA000
        val speciesCount = 80
        val moveCount = 100

        repeat(moveCount) { id ->
            val base = moves + id * 20
            if (id > 0) {
                writeU16(bytes, base, id % 400)
                writeU16(bytes, base + 2, 20 + id % 180)
                bytes[base + 4] = (id % 19).toByte()
                bytes[base + 5] = 100
                bytes[base + 6] = 20
                bytes[base + 7] = 10
                writeU16(bytes, base + 8, 1 shl (id % 8))
                bytes[base + 10] = (if (id % 2 == 0) 1 else 0xFF).toByte()
                bytes[base + 12] = 0x33
                bytes[base + 16] = (id % 3).toByte()
                bytes[base + 18] = 100
            }
        }
        writePointer(bytes, 0x1C4, moves)

        val resolved = Gen3DynamicTableResolver.resolve(
            RomImage(bytes),
            ProfileTables(moveData = TableLayout(wrongMoves, moveCount, 12)),
            speciesCount,
            moveCount,
        )

        assertEquals(moves, resolved.moveData?.offset)
        assertEquals(20, resolved.moveData?.recordSize)
    }

    @Test
    fun rejectsEquallyCredibleExpandedMoveRootsAsAmbiguous() {
        val bytes = ByteArray(0x18000)
        val firstMoves = 0x8000
        val secondMoves = 0xC000
        val moveCount = 100
        fillBattleEngineMoves(bytes, firstMoves, moveCount)
        fillBattleEngineMoves(bytes, secondMoves, moveCount)
        repeat(2) { writePointer(bytes, 0x200 + it * 4, firstMoves) }
        repeat(2) { writePointer(bytes, 0x220 + it * 4, secondMoves) }

        val resolved = Gen3DynamicTableResolver.resolveWithEvidence(
            RomImage(bytes),
            ProfileTables(moveData = TableLayout(0x3000, moveCount, 12)),
            speciesCount = 80,
            moveCount = moveCount,
        )

        assertNull(resolved.tables.moveData)
        val evidence = requireNotNull(resolved.moveDataEvidence)
        assertTrue(evidence.ambiguous)
        assertTrue(evidence.reviewRecommended)
        val capability = capabilityEvidence(RomCapability.MOVE_DETAILS, evidence)
        assertEquals(CapabilityStatus.AMBIGUOUS, capability.status)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, capability.reviewStatus)
    }

    @Test
    fun prefersStructurallyStrongerExpandedMoveRoot() {
        val bytes = ByteArray(0x18000)
        val weakerMoves = 0x8000
        val strongerMoves = 0xC000
        val moveCount = 100
        fillBattleEngineMoves(bytes, weakerMoves, moveCount, invalidRecords = setOf(99))
        fillBattleEngineMoves(bytes, strongerMoves, moveCount)
        repeat(2) { writePointer(bytes, 0x200 + it * 4, weakerMoves) }
        repeat(2) { writePointer(bytes, 0x220 + it * 4, strongerMoves) }

        val resolved = Gen3DynamicTableResolver.resolve(
            RomImage(bytes),
            ProfileTables(moveData = TableLayout(0x3000, moveCount, 12)),
            speciesCount = 80,
            moveCount = moveCount,
        )

        assertEquals(strongerMoves, resolved.moveData?.offset)
    }

    @Test
    fun prefersMoreReferencedExpandedMoveRoot() {
        val bytes = ByteArray(0x18000)
        val lessReferencedMoves = 0x8000
        val moreReferencedMoves = 0xC000
        val moveCount = 100
        fillBattleEngineMoves(bytes, lessReferencedMoves, moveCount)
        fillBattleEngineMoves(bytes, moreReferencedMoves, moveCount)
        repeat(2) { writePointer(bytes, 0x200 + it * 4, lessReferencedMoves) }
        repeat(3) { writePointer(bytes, 0x220 + it * 4, moreReferencedMoves) }

        val resolved = Gen3DynamicTableResolver.resolve(
            RomImage(bytes),
            ProfileTables(moveData = TableLayout(0x3000, moveCount, 12)),
            speciesCount = 80,
            moveCount = moveCount,
        )

        assertEquals(moreReferencedMoves, resolved.moveData?.offset)
    }

    @Test
    fun rejectsPlausibleCandidateBudgetOverflowForManualReview() {
        val moveCount = 20
        val candidateCount = 257
        val bytes = ByteArray(0x30000)
        repeat(candidateCount) { candidate ->
            val moves = 0x2000 + candidate * 0x200
            fillBattleEngineMoves(bytes, moves, moveCount)
            repeat(2) { reference -> writePointer(bytes, 0x400 + (candidate * 2 + reference) * 4, moves) }
        }

        val resolved = Gen3DynamicTableResolver.resolveWithEvidence(
            RomImage(bytes),
            ProfileTables(moveData = TableLayout(0x1800, moveCount, 12)),
            speciesCount = 20,
            moveCount = moveCount,
        )

        assertNull(resolved.tables.moveData)
        val evidence = requireNotNull(resolved.moveDataEvidence)
        assertTrue(evidence.ambiguous)
        assertTrue(evidence.reviewRecommended)
        assertTrue(evidence.reasons.any { it.contains("candidate budget exceeded") })
    }

    @Test
    fun nonPlausiblePointerTargetsDoNotHideLateExpandedMoveRoot() {
        val moveCount = 20
        val bytes = ByteArray(0x50000)
        repeat(400) { decoy ->
            val target = 0x10000 + decoy * 0x100
            bytes.fill(0x7F, target, target + 160)
            repeat(2) { reference -> writePointer(bytes, 0x400 + (decoy * 2 + reference) * 4, target) }
        }
        val moves = 0x40000
        fillBattleEngineMoves(bytes, moves, moveCount)
        repeat(2) { reference -> writePointer(bytes, 0x1000 + reference * 4, moves) }

        val resolved = Gen3DynamicTableResolver.resolveWithEvidence(
            RomImage(bytes),
            ProfileTables(moveData = TableLayout(0x1800, moveCount, 12)),
            speciesCount = 20,
            moveCount = moveCount,
        )

        assertEquals(moves, resolved.tables.moveData?.offset)
        assertEquals(true, resolved.moveDataEvidence?.compatible)
    }

    @Test
    fun singlyReferencedPlausibleShapesDoNotConsumeCandidateBudget() {
        val moveCount = 20
        val bytes = ByteArray(0x40000)
        repeat(300) { decoy ->
            val target = 0x3000 + decoy * 0x200
            fillBattleEngineMoves(bytes, target, moveCount)
            writePointer(bytes, 0x400 + decoy * 4, target)
        }
        val moves = 0x30000
        fillBattleEngineMoves(bytes, moves, moveCount)
        repeat(2) { reference -> writePointer(bytes, 0x1000 + reference * 4, moves) }

        val resolved = Gen3DynamicTableResolver.resolveWithEvidence(
            RomImage(bytes),
            ProfileTables(moveData = TableLayout(0x1800, moveCount, 12)),
            speciesCount = 20,
            moveCount = moveCount,
        )

        assertEquals(moves, resolved.tables.moveData?.offset)
        assertEquals(true, resolved.moveDataEvidence?.compatible)
    }

    @Test
    fun resolvesReferencedTwentyByteMovesNearEndWhenStatsSpanDoesNotFit() {
        val moveCount = 20
        val bytes = ByteArray(0x10000)
        val moves = bytes.size - moveCount * 20
        fillBattleEngineMoves(bytes, moves, moveCount)
        repeat(2) { reference -> writePointer(bytes, 0x200 + reference * 4, moves) }

        val resolved = Gen3DynamicTableResolver.resolve(
            RomImage(bytes),
            ProfileTables(moveData = TableLayout(0x1800, moveCount, 12)),
            speciesCount = 100,
            moveCount = moveCount,
        )

        assertEquals(moves, resolved.moveData?.offset)
        assertEquals(TableRecordFormat.BATTLE_ENGINE_MOVE_20, resolved.moveData?.format)
    }

    @Test
    fun resolvesReferencedSixteenByteMovesNearEndWhenTwentyByteSpanDoesNotFit() {
        val moveCount = 20
        val bytes = ByteArray(0x10000)
        val moves = bytes.size - moveCount * 16
        fillCfruMoves(bytes, moves, moveCount)
        repeat(2) { reference -> writePointer(bytes, 0x200 + reference * 4, moves) }

        val resolved = Gen3DynamicTableResolver.resolve(
            RomImage(bytes),
            ProfileTables(moveData = TableLayout(0x1800, moveCount, 12)),
            speciesCount = 10,
            moveCount = moveCount,
        )

        assertEquals(moves, resolved.moveData?.offset)
        assertEquals(TableRecordFormat.CFRU_MOVE_16, resolved.moveData?.format)
    }

    private fun fillBattleEngineMoves(
        bytes: ByteArray,
        offset: Int,
        count: Int,
        invalidRecords: Set<Int> = emptySet(),
    ) {
        repeat(count) { id ->
            val base = offset + id * 20
            if (id > 0) {
                writeU16(bytes, base, id % 400)
                writeU16(bytes, base + 2, 20 + id % 180)
                bytes[base + 4] = (if (id in invalidRecords) 100 else id % 19).toByte()
                bytes[base + 5] = 100
                bytes[base + 6] = 20
                bytes[base + 7] = 10
                writeU16(bytes, base + 8, 1 shl (id % 8))
                bytes[base + 10] = (if (id % 2 == 0) 1 else 0xFF).toByte()
                bytes[base + 12] = 0x33
                bytes[base + 16] = (id % 3).toByte()
                bytes[base + 18] = 100
            }
        }
    }

    private fun fillCfruMoves(bytes: ByteArray, offset: Int, count: Int) {
        repeat(count) { id ->
            val base = offset + id * 16
            if (id > 0) {
                writeU16(bytes, base, id % 300)
                writeU16(bytes, base + 2, 20 + id % 150)
                bytes[base + 4] = (id % 19).toByte()
                bytes[base + 5] = 100
                bytes[base + 6] = 20
                bytes[base + 7] = 10
                bytes[base + 9] = (if (id % 2 == 0) 1 else 0xFF).toByte()
                bytes[base + 10] = (id % 3).toByte()
            }
        }
    }

    private fun writePointer(bytes: ByteArray, offset: Int, target: Int) {
        val value = 0x08000000 + target
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun writeName(bytes: ByteArray, offset: Int, name: String) {
        name.forEachIndexed { index, character ->
            bytes[offset + index] = (0xBB + character.code - 'A'.code).toByte()
        }
        bytes[offset + name.length] = 0xFF.toByte()
    }

    private fun writeValidStats(bytes: ByteArray, offset: Int, id: Int) {
        repeat(6) { field -> bytes[offset + field] = (40 + (id + field) % 100).toByte() }
        bytes[offset + 6] = (id % 18).toByte()
        bytes[offset + 7] = ((id + 1) % 18).toByte()
        bytes[offset + 22] = (1 + id % 100).toByte()
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private companion object {
        // Exact 8-record Dark Violet suffix: species-name inference crossed into gMoveNames.
        const val DARK_VIOLET_ADJACENT_MOVE_NAMES_BASE64 =
            "rv8AAAAAAAAAAAAAAMrj6eLY/wAAAAAAAADF1ebV6NkAvdzj5P8AvuPp1uDZ5+DV5P8AAL3j4dnoAMrp4tfc/wDH2dvVAMrp4tfc/wAAytXtAL7V7f8AAA=="
    }
}
