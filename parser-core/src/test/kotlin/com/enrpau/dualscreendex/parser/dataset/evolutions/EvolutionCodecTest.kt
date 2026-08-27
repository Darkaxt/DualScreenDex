package com.enrpau.dualscreendex.parser.dataset.evolutions

import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EvolutionCodecTest {
    private val codec = EvolutionCodec()

    @Test
    fun decodesTenSixByteSlotsAndClassifiesEmptyAndMalformedRows() {
        val layout = EvolutionTableLayout(offset = 0, count = 4, slotsPerSpecies = 10, recordSize = 6)
        val bytes = ByteArray(4 * 10 * 6)
        putEvolution(bytes, 10 * 6, method = 4, parameter = 16, target = 2)
        putEvolution(bytes, 3 * 10 * 6, method = 999, parameter = 1, target = 2)

        val outcome = codec.decodeGen3(evolutionSession(bytes), layout) as EvolutionTableOutcome.Decoded

        assertTrue(outcome.rows[0] is EvolutionRowOutcome.StructuralEmpty)
        val decoded = outcome.rows[1] as EvolutionRowOutcome.Decoded
        assertEquals(listOf(EvolutionEdgeValue(2, 4, 16, null, byteArrayOf(4, 0, 16, 0, 2, 0))), decoded.edges)
        assertTrue(outcome.rows[2] is EvolutionRowOutcome.StructuralEmpty)
        assertTrue(outcome.rows[3] is EvolutionRowOutcome.Malformed)
    }

    @Test
    fun preservesEightByteConditionAndReservedTransformationMethods() {
        val stride = 10 * 8
        val bytes = ByteArray(5 * stride)
        listOf(0xFFFD, 0xFFFE, 0xFFFF).forEachIndexed { slot, method ->
            putEvolution(
                bytes,
                stride + slot * 8,
                method = method,
                parameter = 100 + slot,
                target = 2 + slot,
                condition = 20 + slot,
            )
        }
        putEvolution(bytes, stride + 3 * 8, method = 0xFFFF, parameter = 0, target = 0, condition = 0)
        putEvolution(bytes, stride + 4 * 8, method = 0xFFFF, parameter = 250, target = 900, condition = 0)

        val outcome = codec.decodeGen3(
            evolutionSession(bytes),
            EvolutionTableLayout(0, 5, slotsPerSpecies = 10, recordSize = 8),
        ) as EvolutionTableOutcome.Decoded
        val edges = (outcome.rows[1] as EvolutionRowOutcome.Decoded).edges

        assertEquals(listOf(0xFFFD, 0xFFFE, 0xFFFF), edges.map { it.methodId })
        assertEquals(listOf(20, 21, 22), edges.map { it.conditionValue })
    }

    @Test
    fun speciesZeroIsStructuralSentinelAndDisabledSlotPayloadIsIgnored() {
        val stride = 10 * 8
        val bytes = ByteArray(3 * stride)
        putEvolution(bytes, 0, method = 999, parameter = 99, target = 99, condition = 99)
        putEvolution(bytes, stride, method = 4, parameter = 16, target = 2)
        putEvolution(bytes, stride + 8, method = 0, parameter = 999, target = 999, condition = 999)

        val outcome = codec.decodeGen3(
            evolutionSession(bytes),
            EvolutionTableLayout(0, 3, slotsPerSpecies = 10, recordSize = 8),
        ) as EvolutionTableOutcome.Decoded

        assertTrue(outcome.rows[0] is EvolutionRowOutcome.StructuralEmpty)
        assertTrue(outcome.rows[1] is EvolutionRowOutcome.Decoded)
    }

    @Test
    fun returnsTypedExtentBudgetWithoutReadingTheTable() {
        val layout = EvolutionTableLayout(0, 10, slotsPerSpecies = 10, recordSize = 8)
        val outcome = codec.decodeGen3(
            evolutionSession(
                ByteArray(800),
                limits = ResolutionLimits(maxDatasetExtentBytes = 64),
            ),
            layout,
        )

        assertTrue(outcome is EvolutionTableOutcome.ExtentBudgetExceeded)
    }

    @Test
    fun characterizesGenOneAndGenTwoCombinedStreamsAtTheAdapterBoundary() {
        listOf(
            1 to byteArrayOf(1, 16, 1, 0, 5, 20, 0),
            2 to byteArrayOf(5, 20, 1, 1, 0, 5, 20, 0),
        ).forEach { (generation, stream) ->
            val bytes = ByteArray(0x10000)
            putU16(bytes, 0x100, 0x4200)
            stream.copyInto(bytes, 0x8200)
            val outcome = codec.characterizeGen12Combined(
                evolutionSession(bytes),
                Gen12CombinedStreamLayout(
                    pointerTableOffset = 0x100,
                    count = 1,
                    tableBank = 2,
                    generation = generation,
                    moveCount = 30,
                ),
            ) as Gen12CombinedStreamOutcome.Decoded

            val row = outcome.rows.single()
            assertTrue(row.evolutions is EvolutionRowOutcome.Decoded)
            assertEquals(1, row.learnsetEntries)
            assertTrue(row.learnsetValid)
        }
    }

    @Test
    fun combinedStreamQuarantinesAnEofRowWithoutDiscardingValidRows() {
        val bytes = ByteArray(0x8000)
        putU16(bytes, 0x100, 0x4200)
        putU16(bytes, 0x102, 0x7FFE)
        byteArrayOf(0, 5, 20, 0).copyInto(bytes, 0x4200)
        bytes[0x7FFE] = 0
        bytes[0x7FFF] = 5

        val outcome = codec.characterizeGen12Combined(
            evolutionSession(bytes),
            Gen12CombinedStreamLayout(
                pointerTableOffset = 0x100,
                count = 2,
                tableBank = 1,
                generation = 2,
                moveCount = 30,
            ),
        ) as Gen12CombinedStreamOutcome.Decoded

        assertTrue(outcome.rows[0].evolutions is EvolutionRowOutcome.StructuralEmpty)
        assertTrue(outcome.rows[0].learnsetValid)
        assertTrue(outcome.rows[1].evolutions is EvolutionRowOutcome.StructuralEmpty)
        assertEquals(false, outcome.rows[1].learnsetValid)
    }

    @Test
    fun combinedStreamOutcomeDoesNotExposeAMutableRowList() {
        val bytes = ByteArray(0x10000)
        putU16(bytes, 0x100, 0x4200)
        byteArrayOf(1, 16, 1, 0, 5, 20, 0).copyInto(bytes, 0x8200)
        val outcome = codec.characterizeGen12Combined(
            evolutionSession(bytes),
            Gen12CombinedStreamLayout(0x100, 1, 2, generation = 1, moveCount = 30),
        ) as Gen12CombinedStreamOutcome.Decoded

        try {
            @Suppress("UNCHECKED_CAST")
            (outcome.rows as MutableList<Gen12CombinedRowCharacterization>).clear()
            fail("combined-stream rows must be immutable")
        } catch (_: UnsupportedOperationException) {
            // Expected: codec outcomes own immutable evidence snapshots.
        }
        assertEquals(1, outcome.rows.size)
    }

    @Test
    fun decodedTableOutcomeHasImmutableValueSemanticsAndEnforcesCompleteOrderedRows() {
        val layout = EvolutionTableLayout(0, 3, slotsPerSpecies = 10, recordSize = 8)
        val bytes = ByteArray(3 * 10 * 8)
        putEvolution(bytes, layout.rowOffset(1), 4, 16, 2)

        val first = codec.decodeGen3(evolutionSession(bytes), layout) as EvolutionTableOutcome.Decoded
        val second = codec.decodeGen3(evolutionSession(bytes), layout) as EvolutionTableOutcome.Decoded
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())

        assertThrows(IllegalArgumentException::class.java) {
            EvolutionTableOutcome.Decoded(layout, first.rows.dropLast(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EvolutionTableOutcome.Decoded(layout, first.rows.reversed())
        }
    }
}
