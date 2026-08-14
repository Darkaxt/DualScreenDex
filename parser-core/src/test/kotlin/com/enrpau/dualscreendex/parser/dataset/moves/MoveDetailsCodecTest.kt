package com.enrpau.dualscreendex.parser.dataset.moves

import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveDetailsCodecTest {
    @Test
    fun decodesEveryRetailFieldAtItsTwelveByteAbiOffset() {
        val bytes = ByteArray(64)
        putRetailMove(bytes, 8)
        val outcome = MoveDetailsCodec().decode(
            moveDetailsSession(bytes),
            MoveDetailsTableLayout(8, 1, MoveDetailsAbi.RETAIL_12),
        ) as MoveDetailsTableOutcome.Decoded
        val record = (outcome.rows.single() as MoveDetailsRowOutcome.Decoded).record

        assertEquals(43, record.effectId)
        assertEquals(60, record.power)
        assertEquals(1, record.typeId)
        assertEquals(100, record.accuracy)
        assertEquals(35, record.pp)
        assertEquals(10, record.secondaryEffectChance)
        assertEquals(4, record.targetMask)
        assertEquals(-1, record.priority)
        assertEquals(0xA1B2C3D4L, record.flags)
        assertNull(record.split)
        assertNull(record.argument)
        assertNull(record.zMovePower)
        assertNull(record.zMoveEffect)
    }

    @Test
    fun decodesEveryWidenedCfruFieldAtItsSixteenByteAbiOffset() {
        val bytes = ByteArray(64)
        putCfruMove(bytes, 8)
        val row = decodedRow(bytes, MoveDetailsTableLayout(8, 1, MoveDetailsAbi.CFRU_16))

        assertEquals(300, row.effectId)
        assertEquals(450, row.power)
        assertEquals(18, row.typeId)
        assertEquals(0xFF, row.accuracy)
        assertEquals(5, row.pp)
        assertEquals(100, row.secondaryEffectChance)
        assertEquals(7, row.targetMask)
        assertEquals(-2, row.priority)
        assertEquals(0x01020304L, row.flags)
        assertEquals(MoveSplit.SPECIAL, row.split)
        assertNull(row.argument)
        assertNull(row.zMovePower)
        assertNull(row.zMoveEffect)
    }

    @Test
    fun decodesClassicBattleEngineTwentyByteExtensionsWithoutNarrowing() {
        val bytes = ByteArray(64)
        putBattleEngineMove(bytes, 8)
        val row = decodedRow(bytes, MoveDetailsTableLayout(8, 1, MoveDetailsAbi.BATTLE_ENGINE_20))

        assertEquals(700, row.effectId)
        assertEquals(500, row.power)
        assertEquals(0x1234, row.targetMask)
        assertEquals(-3, row.priority)
        assertEquals(0x89ABCDEFL, row.flags)
        assertEquals(MoveSplit.STATUS, row.split)
        assertEquals(19, row.argument)
        assertEquals(200, row.zMovePower)
        assertEquals(0x56, row.zMoveEffect)
    }

    @Test
    fun decodesUnifiedMoveInfoPackedFieldsAtItsFortyEightByteAbiOffsets() {
        val bytes = ByteArray(64)
        putUnifiedMoveInfo(bytes, 8)
        val row = decodedRow(bytes, MoveDetailsTableLayout(8, 1, MoveDetailsAbi.UNIFIED_MOVE_INFO_48))

        assertEquals(700, row.effectId)
        assertEquals(450, row.power)
        assertEquals(18, row.typeId)
        assertEquals(100, row.accuracy)
        assertEquals(5, row.pp)
        assertEquals(0, row.secondaryEffectChance)
        assertEquals(0x123, row.targetMask)
        assertEquals(-3, row.priority)
        assertEquals(0x89ABCDEDL, row.flags)
        assertEquals(MoveSplit.SPECIAL, row.split)
        assertEquals(0x01020304, row.argument)
        assertNull(row.zMovePower)
        assertNull(row.zMoveEffect)
    }

    @Test
    fun classifiesAllZeroSlotsAsStructuralEmptyAndNonzeroInvalidSlotsAsMalformed() {
        val bytes = ByteArray(64)
        bytes[16 + 4] = 99
        bytes[16 + 5] = 4
        bytes[16 + 6] = 80.toByte()
        bytes[16 + 10] = 7
        val result = MoveDetailsCodec().decode(
            moveDetailsSession(bytes),
            MoveDetailsTableLayout(0, 2, MoveDetailsAbi.CFRU_16),
        ) as MoveDetailsTableOutcome.Decoded

        assertTrue(result.rows[0] is MoveDetailsRowOutcome.StructuralEmpty)
        val malformed = result.rows[1] as MoveDetailsRowOutcome.Malformed
        assertTrue(malformed.reasons.any { it.contains("type") })
        assertTrue(malformed.reasons.any { it.contains("accuracy") })
        assertTrue(malformed.reasons.any { it.contains("pp") })
        assertTrue(malformed.reasons.any { it.contains("split") })
    }

    @Test
    fun validationAndMaterializationShareTheExactDecodedRowValue() {
        val bytes = ByteArray(64)
        putBattleEngineMove(bytes, 20)
        val result = MoveDetailsCodec().decode(
            moveDetailsSession(bytes),
            MoveDetailsTableLayout(20, 1, MoveDetailsAbi.BATTLE_ENGINE_20),
        ) as MoveDetailsTableOutcome.Decoded
        val resolved = ResolvedMoveDetailsLayout(result.layout, result.rows)
        val validationRecord = (result.rows.single() as MoveDetailsRowOutcome.Decoded).record

        assertTrue(validationRecord === resolved.materializedRecords.getValue(0))
        assertEquals(result.rows, resolved.rows)
    }

    @Test
    fun layoutAndResolvedRowsAreImmutableValueSnapshots() {
        val layout = MoveDetailsTableLayout(32, 2, MoveDetailsAbi.RETAIL_12)
        val rows = mutableListOf<MoveDetailsRowOutcome>(
            MoveDetailsRowOutcome.StructuralEmpty(0),
            MoveDetailsRowOutcome.StructuralEmpty(1),
        )
        val resolved = ResolvedMoveDetailsLayout(layout, rows)
        rows.clear()

        assertEquals(2, resolved.rows.size)
        assertEquals(layout, layout.immutableSnapshot())
        assertEquals("move-details:20:2:RETAIL_12", layout.layoutIdentity.value)
        assertNotSame(rows, resolved.rows)
    }

    @Test
    fun checkedLongExtentsRejectNegativeOverflowTruncationAndDeterministicBudgetExhaustion() {
        val codec = MoveDetailsCodec()
        val bytes = ByteArray(64)
        val negative = codec.decode(
            moveDetailsSession(bytes),
            MoveDetailsTableLayout(-1, 1, MoveDetailsAbi.RETAIL_12),
        )
        val overflow = codec.decode(
            moveDetailsSession(bytes, limits = ResolutionLimits(maxDatasetExtentBytes = Long.MAX_VALUE)),
            MoveDetailsTableLayout(Long.MAX_VALUE - 4, 2, MoveDetailsAbi.RETAIL_12),
        )
        val truncated = codec.decode(
            moveDetailsSession(bytes),
            MoveDetailsTableLayout(53, 1, MoveDetailsAbi.RETAIL_12),
        )
        val budget = codec.decode(
            moveDetailsSession(bytes, limits = ResolutionLimits(maxDatasetExtentBytes = 11)),
            MoveDetailsTableLayout(0, 1, MoveDetailsAbi.RETAIL_12),
        )

        assertTrue(negative is MoveDetailsTableOutcome.Rejected)
        assertTrue(overflow is MoveDetailsTableOutcome.Rejected)
        assertTrue(truncated is MoveDetailsTableOutcome.Rejected)
        assertTrue(budget is MoveDetailsTableOutcome.ExtentBudgetExceeded)
    }

    @Test
    fun eachAbiAcceptsAnExactNearEofExtent() {
        MoveDetailsAbi.entries.forEach { abi ->
            val bytes = ByteArray(abi.recordSize + 7)
            when (abi) {
                MoveDetailsAbi.RETAIL_12 -> putRetailMove(bytes, 7)
                MoveDetailsAbi.CFRU_16 -> putCfruMove(bytes, 7)
                MoveDetailsAbi.WIDENED_RETAIL_16 -> putWidenedRetailMove(bytes, 7)
                MoveDetailsAbi.BATTLE_ENGINE_20 -> putBattleEngineMove(bytes, 7)
                MoveDetailsAbi.UNIFIED_MOVE_INFO_48 -> putUnifiedMoveInfo(bytes, 7)
            }
            assertTrue(
                MoveDetailsCodec().decode(
                    moveDetailsSession(bytes),
                    MoveDetailsTableLayout(7, 1, abi),
                ) is MoveDetailsTableOutcome.Decoded,
            )
        }
    }

    private fun putWidenedRetailMove(bytes: ByteArray, offset: Int) {
        bytes[offset] = 43
        bytes[offset + 2] = 60
        bytes[offset + 4] = 33
        bytes[offset + 5] = 100
        bytes[offset + 6] = 35
        bytes[offset + 8] = 0x10
        bytes[offset + 9] = 0x01
        bytes[offset + 10] = 0
        bytes[offset + 11] = 0x33
        bytes[offset + 13] = 1
    }

    private fun decodedRow(bytes: ByteArray, layout: MoveDetailsTableLayout): Gen3MoveDetailsRecord {
        val result = MoveDetailsCodec().decode(moveDetailsSession(bytes), layout) as MoveDetailsTableOutcome.Decoded
        return (result.rows.single() as MoveDetailsRowOutcome.Decoded).record
    }
}
