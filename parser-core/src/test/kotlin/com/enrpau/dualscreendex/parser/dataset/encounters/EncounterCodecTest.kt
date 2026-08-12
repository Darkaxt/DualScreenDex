package com.enrpau.dualscreendex.parser.dataset.encounters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncounterCodecTest {
    private val codec = Gen3EncounterCodec()

    @Test
    fun decodesStandardTwentyByteHeadersAndKeepsBoundedStructuralEmptyRows() {
        val bytes = ByteArray(0x3000)
        putStandardEncounterTable(bytes, 0x100, maps = 1..4, emptyRows = setOf(1))

        val result = codec.decode(
            encounterSession(bytes),
            Gen3EncounterTableLayout(0x100, Gen3EncounterAbi.STANDARD_20, speciesCount = 100),
        ) as EncounterTableOutcome.Decoded

        assertEquals(4, result.rows.size)
        assertTrue(result.rows[1] is EncounterHeaderOutcome.StructuralEmpty)
        val grass = (result.rows[0] as EncounterHeaderOutcome.Decoded).methods.single()
        assertEquals(1, grass.methodId)
        assertEquals(setOf(EncounterTimeWindow.ANY), grass.windows)
        assertEquals(12, grass.slots.size)
        assertEquals(DecodedEncounterSlot(10, 5, 7, 20), grass.slots.first())
    }

    @Test
    fun decodesClassicTwentyFourHiddenEnvironmentAndSixtyThirtyTenWeights() {
        val bytes = ByteArray(0x4000)
        putClassicEncounterTable(bytes, 0x100, hiddenEnvironment = 1)

        val result = codec.decode(
            encounterSession(bytes),
            Gen3EncounterTableLayout(0x100, Gen3EncounterAbi.CLASSIC_24, speciesCount = 100),
        ) as EncounterTableOutcome.Decoded

        val hidden = result.rows
            .filterIsInstance<EncounterHeaderOutcome.Decoded>()
            .flatMap { it.methods }
            .single { it.methodId == 8 }
        assertEquals(EncounterEnvironment.WATER, hidden.environment)
        assertEquals(setOf(EncounterTimeWindow.ANY), hidden.windows)
        assertEquals(listOf(60, 30, 10), hidden.slots.map { it.weight })
    }

    @Test
    fun preservesMalformedRowsInsteadOfChangingTheAbiDuringDecode() {
        val bytes = ByteArray(0x3000)
        putStandardEncounterTable(bytes, 0x100, maps = 1..4, malformedRows = setOf(1))

        val result = codec.decode(
            encounterSession(bytes),
            Gen3EncounterTableLayout(0x100, Gen3EncounterAbi.STANDARD_20, speciesCount = 100),
        ) as EncounterTableOutcome.Decoded

        val malformed = result.rows[1] as EncounterHeaderOutcome.Malformed
        assertTrue(malformed.reasons.single().contains("pointer"))
        assertEquals(3, result.rows.count { it is EncounterHeaderOutcome.Decoded })
    }

    @Test
    fun rejectsAHeaderRunWithoutABoundedSentinel() {
        val bytes = ByteArray(0x3000)
        putStandardEncounterTable(bytes, 0x100, maps = 1..4)
        bytes[0x100 + 4 * 20] = 1
        bytes[0x100 + 4 * 20 + 1] = 5

        val result = codec.decode(
            encounterSession(bytes),
            Gen3EncounterTableLayout(
                0x100,
                Gen3EncounterAbi.STANDARD_20,
                speciesCount = 100,
                maxHeaders = 4,
            ),
        )

        assertTrue(result is EncounterTableOutcome.Rejected)
        assertTrue((result as EncounterTableOutcome.Rejected).reason.contains("sentinel"))
    }

    @Test
    fun checkedLongExtentsRejectUnindexableRootsAndTypeExtentBudgets() {
        val invalid = codec.decode(
            encounterSession(ByteArray(128)),
            Gen3EncounterTableLayout(
                Long.MAX_VALUE - 8,
                Gen3EncounterAbi.STANDARD_20,
                speciesCount = 100,
            ),
        )
        assertTrue(invalid is EncounterTableOutcome.Rejected)

        val bytes = ByteArray(0x3000)
        putStandardEncounterTable(bytes, 0x100, maps = 1..4)
        val budget = codec.decode(
            encounterSession(
                bytes,
                limits = com.enrpau.dualscreendex.parser.analysis.ResolutionLimits(maxDatasetExtentBytes = 64),
            ),
            Gen3EncounterTableLayout(0x100, Gen3EncounterAbi.STANDARD_20, speciesCount = 100),
        )
        assertTrue(budget is EncounterTableOutcome.BudgetExceeded)
        assertEquals(EncounterBudgetKind.EXTENT, (budget as EncounterTableOutcome.BudgetExceeded).budgetKind)
    }

    @Test
    fun extentBudgetIncludesTheTerminatingSentinelBytes() {
        val bytes = ByteArray(0x3000)
        putStandardEncounterTable(bytes, 0x100, maps = 1..4)

        val result = codec.decode(
            encounterSession(
                bytes,
                limits = com.enrpau.dualscreendex.parser.analysis.ResolutionLimits(maxDatasetExtentBytes = 81),
            ),
            Gen3EncounterTableLayout(0x100, Gen3EncounterAbi.STANDARD_20, speciesCount = 100),
        )

        assertTrue(result is EncounterTableOutcome.BudgetExceeded)
        assertEquals(82L, (result as EncounterTableOutcome.BudgetExceeded).observed)
    }

    @Test
    fun decodedCollectionsAreImmutableValueSnapshots() {
        val bytes = ByteArray(0x3000)
        putStandardEncounterTable(bytes, 0x100)
        val decoded = codec.decode(
            encounterSession(bytes),
            Gen3EncounterTableLayout(0x100, Gen3EncounterAbi.STANDARD_20, speciesCount = 100),
        ) as EncounterTableOutcome.Decoded

        @Suppress("UNCHECKED_CAST")
        val mutableRows = decoded.rows as MutableList<EncounterHeaderOutcome>
        val failure = runCatching { mutableRows.clear() }.exceptionOrNull()

        assertTrue(failure is UnsupportedOperationException)
        assertEquals(decoded.rows, decoded.rows.toList())
    }
}
