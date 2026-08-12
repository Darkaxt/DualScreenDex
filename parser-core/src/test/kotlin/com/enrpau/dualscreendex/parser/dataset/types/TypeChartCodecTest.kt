package com.enrpau.dualscreendex.parser.dataset.types

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeChartCodecTest {
    private val codec = TypeChartCodec()

    @Test
    fun decodesTerminatedLegacyTripletsIntoTypedRowsAndMatchups() {
        val bytes = ByteArray(128)
        putLegacyTypeChart(bytes, 16)

        val result = codec.decode(
            typeChartSession(bytes),
            TypeChartTableLayout(16, TypeChartAbi.LEGACY_TRIPLETS),
        ) as TypeChartTableOutcome.Decoded

        assertEquals(12, result.rows.size)
        assertEquals(TypeChartMatchup(0, 1, 0), result.matchups.first())
    }

    @Test
    fun decodesDenseU32Q412AndRoundsOneFifthToTwentyPercent() {
        val typeCount = 18
        val bytes = ByteArray(typeCount * typeCount * 4)
        putU32Q412Matrix(bytes, 0, typeCount)
        putTypeU32(bytes, 4, 819)

        val result = codec.decode(
            typeChartSession(bytes),
            TypeChartTableLayout(0, TypeChartAbi.DENSE_U32_Q412, typeCount),
        ) as TypeChartTableOutcome.Decoded

        assertTrue(result.matchups.contains(TypeChartMatchup(0, 1, 20)))
        assertEquals(typeCount * typeCount, result.rows.size)
    }

    @Test
    fun decodesOnlyAnExactAdjacentU16InversePair() {
        val typeCount = 19
        val bytes = ByteArray(typeCount * typeCount * 4)
        putU16Q412Pair(bytes, 0, typeCount)

        val decoded = codec.decode(
            typeChartSession(bytes),
            TypeChartTableLayout(0, TypeChartAbi.DENSE_U16_Q412_WITH_INVERSE, typeCount),
        )
        putTypeU16(bytes, typeCount * typeCount * 2, 2048)
        val corrupt = codec.decode(
            typeChartSession(bytes),
            TypeChartTableLayout(0, TypeChartAbi.DENSE_U16_Q412_WITH_INVERSE, typeCount),
        )

        assertTrue(decoded is TypeChartTableOutcome.Decoded)
        assertTrue(corrupt is TypeChartTableOutcome.Rejected)
    }
}
