package com.enrpau.dualscreendex.parser.dataset.moves

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PackedFlagsMoveCodecTest {
    @Test
    fun packedSplitArgumentAndFortyBitTailKeepTheirIndependentOffsets() {
        listOf(0x40, 0x180).forEach { root ->
            val bytes = ByteArray(0x300)
            putPackedMove(bytes, root)
            val row = (decode(bytes, root, 1).rows.single() as MoveDetailsRowOutcome.Decoded).record
            assertEquals(0x345, row.effectId)
            assertEquals(231, row.power)
            assertEquals(18, row.typeId)
            assertEquals(95, row.accuracy)
            assertEquals(10, row.pp)
            assertEquals(40, row.secondaryEffectChance)
            assertEquals(0x100, row.targetMask)
            assertEquals(-7, row.priority)
            assertEquals(MoveSplit.SPECIAL, row.split)
            assertEquals(0xBEEF, row.argument)
            assertEquals(4, row.zMoveEffect)
            assertEquals(0x89123456ABL, row.flags)
            assertNull(row.zMovePower)
        }
    }

    @Test
    fun reservedZeroRowAndStatusRowsRemainDistinct() {
        val bytes = ByteArray(80)
        putPackedMove(bytes, 20)
        putPackedMove(bytes, 40)
        bytes[42] = 0
        bytes[51] = 2
        val result = decode(bytes, 0, 3)
        assertTrue(result.rows[0] is MoveDetailsRowOutcome.StructuralEmpty)
        assertEquals(MoveSplit.STATUS, (result.rows[2] as MoveDetailsRowOutcome.Decoded).record.split)
        val projection = ResolvedMoveDetailsLayout(MoveDetailsTableLayout(0, 3, abi()), result.rows).catalogDetails()
        assertEquals(com.enrpau.dualscreendex.parser.catalog.MoveCategory.STATUS, projection.getValue(2).category)
    }

    @Test
    fun invalidScalarsSplitAndPaddingAreNeverRecoveredByChangingTheStride() {
        listOf(3 to 32, 4 to 9, 5 to 65, 6 to 101, 7 to 1, 10 to 8, 11 to 3).forEach { (field, value) ->
            val bytes = ByteArray(20)
            putPackedMove(bytes, 0)
            bytes[field] = value.toByte()
            assertTrue("field $field", decode(bytes, 0, 1).rows.single() is MoveDetailsRowOutcome.Malformed)
        }
    }

    @Test
    fun packedRowsDoNotMasqueradeAsEitherExistingTwentyByteAbi() {
        val bytes = ByteArray(20)
        putPackedMove(bytes, 0)
        listOf(MoveDetailsAbi.HYBRID_BATTLE_MOVE_20, MoveDetailsAbi.BATTLE_ENGINE_20).forEach { legacy ->
            val result = MoveDetailsCodec().decode(moveDetailsSession(bytes), MoveDetailsTableLayout(0, 1, legacy))
                as MoveDetailsTableOutcome.Decoded
            assertTrue(result.rows.single() is MoveDetailsRowOutcome.Malformed)
        }
        assertTrue(decode(bytes, 0, 1).rows.single() is MoveDetailsRowOutcome.Decoded)
    }

    @Test
    fun packedExtentAndCancellationUseTheSharedCodecGuards() {
        val bytes = ByteArray(40)
        putPackedMove(bytes, 0)
        val layout = MoveDetailsTableLayout(0, 2, abi())
        assertTrue(MoveDetailsCodec().decode(moveDetailsSession(bytes.copyOf(39)), layout) is MoveDetailsTableOutcome.Rejected)
        assertTrue(MoveDetailsCodec().decode(
            moveDetailsSession(bytes, limits = ResolutionLimits(maxDatasetExtentBytes = 39)), layout,
        ) is MoveDetailsTableOutcome.ExtentBudgetExceeded)
        val session = RomAnalysisSession(
            RomImage(bytes), RomHeader(Platform.GBA, "PACKED MOVE TEST"),
            cancellation = ParserCancellationToken { throw ParserCancellationException() },
        )
        assertThrows(ParserCancellationException::class.java) { MoveDetailsCodec().decode(session, layout) }
    }

    private fun abi() = MoveDetailsAbi.entries.singleOrNull { it.name == "PACKED_FLAGS_MOVE_20" }
        ?: throw AssertionError("compiled packed-flag BattleMove ABI is missing")

    private fun decode(bytes: ByteArray, root: Int, count: Long) = MoveDetailsCodec().decode(
        moveDetailsSession(bytes), MoveDetailsTableLayout(root.toLong(), count, abi()),
    ) as MoveDetailsTableOutcome.Decoded
}

internal fun putPackedMove(bytes: ByteArray, at: Int) {
    bytes[at] = 0x45
    bytes[at + 1] = 3
    bytes[at + 2] = 231.toByte()
    bytes[at + 3] = 18
    bytes[at + 4] = 95
    bytes[at + 5] = 10
    bytes[at + 6] = 40
    bytes[at + 7] = 0
    bytes[at + 8] = 0
    bytes[at + 9] = 1
    bytes[at + 10] = (-7).toByte()
    bytes[at + 11] = 1
    bytes[at + 12] = 0xEF.toByte()
    bytes[at + 13] = 0xBE.toByte()
    bytes[at + 14] = 4
    val flags = 0x89123456ABL
    repeat(5) { bytes[at + 15 + it] = (flags ushr (it * 8)).toByte() }
}
