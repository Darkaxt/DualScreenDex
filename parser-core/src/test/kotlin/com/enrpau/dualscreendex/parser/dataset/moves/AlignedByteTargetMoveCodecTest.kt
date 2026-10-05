package com.enrpau.dualscreendex.parser.dataset.moves

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlignedByteTargetMoveCodecTest {
    @Test
    fun keepsTwentyAndFiftySixByteContractsDistinctFromTheExistingHybridAbi() {
        val twenty = abi(20)
        val fiftySix = abi(56)
        assertEquals(20, twenty.recordSize)
        assertEquals(56, fiftySix.recordSize)
        assertTrue(twenty != MoveDetailsAbi.HYBRID_BATTLE_MOVE_20)
        assertTrue(twenty != MoveDetailsAbi.BATTLE_ENGINE_20)
        assertTrue(twenty.tableRecordFormat != fiftySix.tableRecordFormat)
    }

    @Test
    fun decodesAllCompiledPrefixFieldsIncludingByteTargetAndSignedPriority() {
        for (width in listOf(20, 56)) {
            val bytes = ByteArray(width)
            putRow(bytes, 0)
            val record = (decode(bytes, width).rows.single() as MoveDetailsRowOutcome.Decoded).record
            assertEquals(513, record.effectId)
            assertEquals(200, record.power)
            assertEquals(18, record.typeId)
            assertEquals(90, record.accuracy)
            assertEquals(64, record.pp)
            assertEquals(255, record.secondaryEffectChance)
            assertEquals(129, record.targetMask)
            assertEquals(-5, record.priority)
            assertEquals(0x81234567L, record.flags)
            assertEquals(MoveSplit.STATUS, record.split)
            assertEquals(250, record.argument)
            assertNull(record.zMovePower)
            assertNull(record.zMoveEffect)
        }
    }

    @Test
    fun doesNotInterpretTheUnknownFiftySixByteExtensionAsZMoveOrOtherMechanics() {
        val bytes = ByteArray(56)
        putRow(bytes, 0)
        bytes.fill(0x7f, 20, 56)
        val record = (decode(bytes, 56).rows.single() as MoveDetailsRowOutcome.Decoded).record
        assertEquals(200, record.power)
        assertEquals(-5, record.priority)
        assertNull(record.zMovePower)
        assertNull(record.zMoveEffect)
    }

    @Test
    fun advancesByTheAdmittedPhysicalStrideAndRetainsExactZeroSentinels() {
        for (width in listOf(20, 56)) {
            val bytes = ByteArray(width * 3)
            putRow(bytes, width)
            putRow(bytes, width * 2)
            bytes[width * 2 + 2] = 60
            val rows = decode(bytes, width, 3).rows
            assertTrue(rows[0] is MoveDetailsRowOutcome.StructuralEmpty)
            assertEquals(200, (rows[1] as MoveDetailsRowOutcome.Decoded).record.power)
            assertEquals(60, (rows[2] as MoveDetailsRowOutcome.Decoded).record.power)
        }
    }

    @Test
    fun rejectsMalformedKnownTypeWithoutGuessingAnEngineDefinedSplitCategory() {
        for (width in listOf(20, 56)) {
            val bytes = ByteArray(width * 2)
            putRow(bytes, 0)
            putRow(bytes, width)
            bytes[3] = 32
            bytes[width + 16] = 3
            val decoded = decode(bytes, width, 2)
            assertTrue(decoded.rows[0] is MoveDetailsRowOutcome.Malformed)
            val record = (decoded.rows[1] as MoveDetailsRowOutcome.Decoded).record
            assertNull(record.split)
            assertEquals(3, record.nativeSplitId)
            val category = ResolvedMoveDetailsLayout(decoded.layout, decoded.rows).catalogDetails().getValue(1).category
            assertEquals(com.enrpau.dualscreendex.parser.catalog.MoveCategory.UNKNOWN, category)
        }
    }

    @Test
    fun checksTheFullStrideAgainstTruncationOverflowAndTheSharedExtentBudget() {
        for (width in listOf(20, 56)) {
            val codec = MoveDetailsCodec()
            val layout = MoveDetailsTableLayout(0, 1, abi(width))
            assertTrue(codec.decode(session(ByteArray(width - 1)), layout) is MoveDetailsTableOutcome.Rejected)
            assertTrue(codec.decode(session(ByteArray(width), ResolutionLimits(maxDatasetExtentBytes = width - 1L)), layout)
                is MoveDetailsTableOutcome.ExtentBudgetExceeded)
            assertTrue(codec.decode(session(ByteArray(width)), MoveDetailsTableLayout(Long.MAX_VALUE, Long.MAX_VALUE, abi(width)))
                is MoveDetailsTableOutcome.Rejected)
        }
    }

    @Test(expected = ParserCancellationException::class)
    fun checksCancellationBeforeRejectingAnInvalidExtent() {
        MoveDetailsCodec().decode(
            session(ByteArray(1), cancellation = ParserCancellationToken { throw ParserCancellationException() }),
            MoveDetailsTableLayout(0, 1, MoveDetailsAbi.RETAIL_12),
        )
    }

    @Test(expected = ParserCancellationException::class)
    fun checksCancellationDuringFixedStrideRowDecoding() {
        var checks = 0
        MoveDetailsCodec().decode(
            session(ByteArray(112), cancellation = ParserCancellationToken { if (++checks == 3) throw ParserCancellationException() }),
            MoveDetailsTableLayout(0, 2, abi(56)),
        )
    }

    private fun abi(width: Int): MoveDetailsAbi {
        val selected = MoveDetailsAbi.entries.singleOrNull { it.name == "ALIGNED_BYTE_TARGET_MOVE_$width" }
        assertNotNull("a distinct compiled byte-target ABI is required for stride $width", selected)
        return requireNotNull(selected)
    }

    private fun decode(bytes: ByteArray, width: Int, count: Long = 1) =
        MoveDetailsCodec().decode(session(bytes), MoveDetailsTableLayout(0, count, abi(width))) as MoveDetailsTableOutcome.Decoded

    private fun session(
        bytes: ByteArray,
        limits: ResolutionLimits = ResolutionLimits(),
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ) = RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "SYNTHETIC"), limits = limits, cancellation = cancellation)

    private fun putRow(bytes: ByteArray, at: Int) {
        bytes[at] = 1
        bytes[at + 1] = 2
        bytes[at + 2] = 200.toByte()
        bytes[at + 3] = 18
        bytes[at + 4] = 90
        bytes[at + 5] = 64
        bytes[at + 6] = 255.toByte()
        bytes[at + 7] = 129.toByte()
        bytes[at + 8] = (-5).toByte()
        repeat(4) { bytes[at + 12 + it] = (0x81234567L ushr (it * 8)).toByte() }
        bytes[at + 16] = 2
        bytes[at + 17] = 250.toByte()
    }
}
