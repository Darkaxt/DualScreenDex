package com.enrpau.dualscreendex.parser.dataset.learnsets

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalLearnsetCodecTest {
    @Test
    fun decodesOnlyIndependentlyAdmittedNativeRowsWithoutShiftingStoredLevels() {
        val bytes = fixture()
        val result = decode(bytes) as LearnsetTableOutcome.Decoded
        assertTrue(result.rows[0] is LearnsetRowOutcome.StructuralEmpty)
        assertTrue(result.rows[3] is LearnsetRowOutcome.StructuralEmpty)
        assertEquals(listOf(LearnsetEntryValue(0, 2), LearnsetEntryValue(100, 7)),
            (result.rows[1] as LearnsetRowOutcome.Decoded).entries)
        assertEquals(listOf(LearnsetEntryValue(17, 4)), (result.rows[2] as LearnsetRowOutcome.Decoded).entries)
        assertEquals(LearnsetTermination.Explicit, (result.rows[4] as LearnsetRowOutcome.Decoded).termination)
        assertEquals(0, result.malformedRows)
    }

    @Test
    fun rejectsAnEmptyOrOutOfBoundsCanonicalDomainInsteadOfUsingAllRows() {
        for (ids in listOf(emptySet(), setOf(0, 1), setOf(1, 5), setOf(-1, 1))) {
            assertTrue(decode(fixture(), ids) is LearnsetTableOutcome.Rejected)
        }
    }

    @Test
    fun canonicalPointersAndLevelsMustStillDecodeCompletely() {
        val badPointer = fixture().apply { put32(this, 8, 0x02000000) }
        assertTrue((decode(badPointer) as LearnsetTableOutcome.Decoded).rows[2] is LearnsetRowOutcome.Malformed)
        val badLevel = fixture().apply { put16(this, 66, 101) }
        assertTrue((decode(badLevel) as LearnsetTableOutcome.Decoded).rows[1] is LearnsetRowOutcome.Malformed)
    }

    @Test
    fun validatesTheFullPhysicalPointerSpanAndSharedWorkBudget() {
        assertTrue(decode(ByteArray(19)) is LearnsetTableOutcome.Rejected)
        assertTrue(decode(fixture(), limits = ResolutionLimits(maxDatasetExtentBytes = 19))
            is LearnsetTableOutcome.ExtentBudgetExceeded)
        assertTrue(decode(fixture(), limits = ResolutionLimits(maxProbeWorkPerDataset = 1))
            is LearnsetTableOutcome.WorkBudgetExceeded)
    }

    @Test(expected = ParserCancellationException::class)
    fun checksCancellationBeforeInvalidExtent() {
        decode(ByteArray(1), cancellation = ParserCancellationToken { throw ParserCancellationException() })
    }

    @Test(expected = ParserCancellationException::class)
    fun checksCancellationDuringCanonicalDecoding() {
        var checks = 0
        decode(fixture(), cancellation = ParserCancellationToken { if (++checks == 4) throw ParserCancellationException() })
    }

    private fun decode(
        bytes: ByteArray,
        ids: Set<Int> = setOf(1, 2, 4),
        limits: ResolutionLimits = ResolutionLimits(),
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ) = LearnsetCodec().decodeCanonicalGen3(
        RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "SYNTHETIC"), limits = limits, cancellation = cancellation),
        LearnsetTableLayout(0, 5, LearnsetFormat.MoveU16LevelU16), ids, 8,
    )

    private fun fixture() = ByteArray(112).apply {
        put32(this, 4, 0x08000040)
        put32(this, 8, 0x08000050)
        put32(this, 12, 0x02000000)
        put32(this, 16, 0x08000060)
        put16(this, 64, 2)
        put16(this, 66, 0)
        put16(this, 68, 7)
        put16(this, 70, 100)
        put16(this, 72, 65535)
        put16(this, 80, 4)
        put16(this, 82, 17)
        put16(this, 84, 65535)
        put16(this, 96, 65535)
    }

    private fun put16(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = value.toByte()
        bytes[at + 1] = (value ushr 8).toByte()
    }

    private fun put32(bytes: ByteArray, at: Int, value: Int) {
        repeat(4) { bytes[at + it] = (value ushr (it * 8)).toByte() }
    }
}
