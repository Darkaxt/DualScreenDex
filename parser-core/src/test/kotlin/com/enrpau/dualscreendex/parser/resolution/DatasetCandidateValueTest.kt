package com.enrpau.dualscreendex.parser.resolution

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class DatasetCandidateValueTest {
    @Test
    fun candidateSnapshotsPrimitiveArrayLayoutAndHasStableValueEqualityInEitherOrder() {
        val callerOwned = intArrayOf(1, 2, 3)
        val first = candidate(PrimitiveArrayLayout(callerOwned), "first")
        val second = candidate(PrimitiveArrayLayout(intArrayOf(1, 2, 3)), "second")
        callerOwned[0] = 99

        val published = first.layout.values
        published[1] = 88

        assertEquals(listOf(1, 2, 3), first.layout.values.toList())
        assertNotSame(callerOwned, first.layout.values)
        assertEquals(first, second)
        assertEquals(second, first)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(first.toString(), second.toString())

        val forward = CandidateSelector.select(session(), DatasetKind.EVOLUTIONS, sequenceOf(first, second))
        val reverse = CandidateSelector.select(session(), DatasetKind.EVOLUTIONS, sequenceOf(second, first))

        assertEquals(forward, reverse)
        assertEquals(forward.hashCode(), reverse.hashCode())
    }

    private fun candidate(
        layout: PrimitiveArrayLayout,
        diagnosticLabel: String,
    ): DatasetCandidate<PrimitiveArrayLayout> = DatasetCandidate(
        identity = CandidateIdentity("evolutions:array-layout"),
        kind = DatasetKind.EVOLUTIONS,
        layout = layout,
        source = CandidateSource.COMPILED_REFERENCE,
        strength = CandidateStrength(
            semanticCoverage = EvidenceCoverage(3, 3),
            structuralCoverage = EvidenceCoverage(3, 3),
            compiledReferenceCount = 2,
        ),
        diagnosticLabel = diagnosticLabel,
        eligibility = CandidateEligibility.validated(CandidateSource.COMPILED_REFERENCE),
    )

    private fun session(): RomAnalysisSession = RomAnalysisSession(
        rom = RomImage(ByteArray(0x200)),
        header = RomHeader(Platform.GBA, "TEST"),
    )

    private class PrimitiveArrayLayout(source: IntArray) : ImmutableDatasetLayout<PrimitiveArrayLayout> {
        private val snapshot = source.copyOf()

        val values: IntArray get() = snapshot.copyOf()

        override val layoutIdentity: CandidateLayoutIdentity = CandidateLayoutIdentity(
            "primitive-array:${snapshot.joinToString(",")}",
        )

        override fun immutableSnapshot(): PrimitiveArrayLayout = PrimitiveArrayLayout(snapshot)

        override fun equals(other: Any?): Boolean =
            other is PrimitiveArrayLayout && snapshot.contentEquals(other.snapshot)

        override fun hashCode(): Int = snapshot.contentHashCode()

        override fun toString(): String = "PrimitiveArrayLayout(values=${snapshot.toList()})"
    }
}
