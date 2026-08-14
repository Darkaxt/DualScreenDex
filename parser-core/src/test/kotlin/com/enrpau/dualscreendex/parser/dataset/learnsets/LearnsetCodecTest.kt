package com.enrpau.dualscreendex.parser.dataset.learnsets

import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnsetCodecTest {
    private val codec = LearnsetCodec()

    @Test
    fun decodesEverySupportedGenThreeAbiAndPreservesUnsortedLegalLevels() {
        val fixtures: List<Pair<LearnsetFormat, (ByteArray, Int, List<LearnsetEntryValue>) -> Unit>> = listOf(
            LearnsetFormat.PackedU16(moveBits = 10) to { bytes, target, entries ->
                putPacked(bytes, target, entries)
            },
            LearnsetFormat.LevelU8MoveU16 to { bytes, target, entries -> putLevelMove(bytes, target, entries) },
            LearnsetFormat.MoveU16LevelU8 to { bytes, target, entries -> putMoveLevel(bytes, target, entries) },
            LearnsetFormat.MoveU16LevelU16 to { bytes, target, entries -> putWide(bytes, target, entries) },
        )
        fixtures.forEachIndexed { index, (format, writer) ->
            val bytes = ByteArray(0x400)
            val target = 0x100 + index * 0x40
            val entries = listOf(LearnsetEntryValue(37, 700), LearnsetEntryValue(29, 7))
            putPointer(bytes, 0, target)
            writer(bytes, target, entries)

            val outcome = codec.decodeGen3(
                learnsetSession(bytes),
                LearnsetTableLayout(0, speciesCount = 1, format = format),
                moveCount = 800,
            ) as LearnsetTableOutcome.Decoded

            assertEquals(entries, (outcome.rows.single() as LearnsetRowOutcome.Decoded).entries)
        }
    }

    @Test
    fun aRowMustTerminateBeforeTheNextDistinctPointer() {
        val bytes = ByteArray(0x300)
        putPointer(bytes, 0, 0x100)
        putPointer(bytes, 4, 0x103)
        bytes[0x100] = 1
        putU16(bytes, 0x101, 700)
        putLevelMove(bytes, 0x103, listOf(LearnsetEntryValue(2, 701)))

        val outcome = codec.decodeGen3(
            learnsetSession(bytes),
            LearnsetTableLayout(0, 2, LearnsetFormat.LevelU8MoveU16),
            moveCount = 800,
        ) as LearnsetTableOutcome.Decoded

        assertTrue(outcome.rows[0] is LearnsetRowOutcome.Malformed)
        assertEquals(
            listOf(LearnsetEntryValue(2, 701)),
            (outcome.rows[1] as LearnsetRowOutcome.Decoded).entries,
        )
    }

    @Test
    fun decodesPointersEmbeddedAtTheSelectedRecordStride() {
        val bytes = ByteArray(0x400)
        putPointer(bytes, 0, 0x200)
        putPointer(bytes, 260, 0x240)
        putWide(bytes, 0x200, listOf(LearnsetEntryValue(1, 33)))
        putWide(bytes, 0x240, listOf(LearnsetEntryValue(7, 45)))

        val outcome = codec.decodeGen3(
            learnsetSession(bytes),
            LearnsetTableLayout(
                offset = 0,
                speciesCount = 2,
                format = LearnsetFormat.MoveU16LevelU16,
                pointerStride = 260,
            ),
            moveCount = 100,
        ) as LearnsetTableOutcome.Decoded

        assertEquals(
            listOf(LearnsetEntryValue(1, 33)),
            (outcome.rows[0] as LearnsetRowOutcome.Decoded).entries,
        )
        assertEquals(
            listOf(LearnsetEntryValue(7, 45)),
            (outcome.rows[1] as LearnsetRowOutcome.Decoded).entries,
        )
    }

    @Test
    fun packedRecoveryIsBoundedByTheAdjacentPointerAndKeepsOnlyTheValidPrefix() {
        val bytes = ByteArray(0x300)
        putPointer(bytes, 0, 0x100)
        putPointer(bytes, 4, 0x106)
        putU16(bytes, 0x100, (5 shl 10) or 33)
        putU16(bytes, 0x102, 0)
        putU16(bytes, 0x104, 0)
        putPacked(bytes, 0x106, listOf(LearnsetEntryValue(7, 44)))

        val outcome = codec.decodeGen3(
            learnsetSession(bytes),
            LearnsetTableLayout(0, 2, LearnsetFormat.PackedU16(10)),
            moveCount = 800,
        ) as LearnsetTableOutcome.Decoded
        val recovered = outcome.rows[0] as LearnsetRowOutcome.Decoded

        assertEquals(listOf(LearnsetEntryValue(5, 33)), recovered.entries)
        assertEquals(LearnsetTermination.RecoveredAtAdjacentPointer(2), recovered.termination)
    }

    @Test
    fun packedRecoveryBeforeAnExplicitTerminatorDoesNotClaimAnAdjacentPointerBoundary() {
        val bytes = ByteArray(0x200)
        putPointer(bytes, 0, 0x100)
        putU16(bytes, 0x100, (5 shl 9) or 33)
        putU16(bytes, 0x102, 0)
        putU16(bytes, 0x104, 0)
        putU16(bytes, 0x106, 0xFFFF)

        val outcome = codec.decodeGen3(
            learnsetSession(bytes),
            LearnsetTableLayout(0, 1, LearnsetFormat.PackedU16(9)),
            moveCount = 100,
        ) as LearnsetTableOutcome.Decoded
        val recovered = outcome.rows.single() as LearnsetRowOutcome.Decoded

        assertEquals(
            LearnsetTermination.RecoveredBeforeExplicitTerminator(2),
            recovered.termination,
        )
    }

    @Test
    fun lastDistinctPointerCannotTreatTheRomEofAsAnAdjacentRecoveryBoundary() {
        val bytes = ByteArray(0x108)
        putPointer(bytes, 0, 0x100)
        putU16(bytes, 0x100, (5 shl 9) or 33)
        putU16(bytes, 0x102, 0)
        putU16(bytes, 0x104, 0)
        putU16(bytes, 0x106, 0)

        val outcome = codec.decodeGen3(
            learnsetSession(bytes),
            LearnsetTableLayout(0, 1, LearnsetFormat.PackedU16(9)),
            moveCount = 100,
        ) as LearnsetTableOutcome.Decoded

        assertTrue(outcome.rows.single() is LearnsetRowOutcome.Malformed)
    }

    @Test
    fun rowTraversalConsumesTheSharedWorkBudget() {
        val bytes = ByteArray(0x200)
        putPointer(bytes, 0, 0x100)
        putPacked(
            bytes,
            0x100,
            listOf(
                LearnsetEntryValue(1, 10),
                LearnsetEntryValue(2, 11),
                LearnsetEntryValue(3, 12),
            ),
            moveBits = 9,
        )

        val outcome = codec.decodeGen3(
            learnsetSession(
                bytes,
                limits = ResolutionLimits(maxProbeWorkPerDataset = 3),
            ),
            LearnsetTableLayout(0, 1, LearnsetFormat.PackedU16(9)),
            moveCount = 100,
        )

        assertTrue(outcome is LearnsetTableOutcome.WorkBudgetExceeded)
    }

    @Test
    fun malformedOrUnterminatedRowsAreQuarantinedForEveryAbi() {
        val fixtures = listOf(
            LearnsetFormat.PackedU16(10) to byteArrayOf(0, 0, 0, 0, 0, 0),
            LearnsetFormat.LevelU8MoveU16 to byteArrayOf(101.toByte(), 1, 0),
            LearnsetFormat.MoveU16LevelU8 to byteArrayOf(1, 0, 101.toByte()),
            LearnsetFormat.MoveU16LevelU16 to byteArrayOf(1, 0, 101, 0),
        )
        fixtures.forEach { (format, payload) ->
            val bytes = ByteArray(0x110)
            putPointer(bytes, 0, 0x100)
            payload.copyInto(bytes, 0x100)

            val outcome = codec.decodeGen3(
                learnsetSession(bytes),
                LearnsetTableLayout(0, 1, format),
                moveCount = 800,
            ) as LearnsetTableOutcome.Decoded

            assertTrue("format $format must fail closed", outcome.rows.single() is LearnsetRowOutcome.Malformed)
        }
    }

    @Test
    fun surfacesThePointerTableExtentBudgetBeforeAnyRowRead() {
        val outcome = codec.decodeGen3(
            learnsetSession(
                ByteArray(40),
                limits = ResolutionLimits(maxDatasetExtentBytes = 16),
            ),
            LearnsetTableLayout(0, 10, LearnsetFormat.PackedU16(10)),
            moveCount = 800,
        )

        assertTrue(outcome is LearnsetTableOutcome.ExtentBudgetExceeded)
    }

    @Test
    fun resolvedRowsAndEntriesCannotBeMutatedThroughPublishedCollections() {
        val bytes = ByteArray(0x200)
        putPointer(bytes, 0, 0x100)
        putPacked(bytes, 0x100, listOf(LearnsetEntryValue(1, 10)))
        val outcome = codec.decodeGen3(
            learnsetSession(bytes),
            LearnsetTableLayout(0, 1, LearnsetFormat.PackedU16(9)),
            moveCount = 100,
        ) as LearnsetTableOutcome.Decoded
        val row = outcome.rows.single() as LearnsetRowOutcome.Decoded

        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (outcome.rows as MutableList<LearnsetRowOutcome>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (row.entries as MutableList<LearnsetEntryValue>).clear()
        }
    }
}
