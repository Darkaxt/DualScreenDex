package com.enrpau.dualscreendex.parser.dataset.core.basestats

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.BaseStats
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WideBaseStatsCodecTest {
    @Test
    fun decodesEveryWidenedFieldWithoutRetailOffsetOrByteTruncation() {
        val bytes = ByteArray(160)
        putWideRow(bytes, 32)
        val row = decode(bytes, 32, 1).rows.single() as BaseStatsRowOutcome.Decoded

        assertEquals(BaseStats(345, 49, 299, 50, 512, 65535), row.record.stats)
        assertEquals(listOf(12, 18), row.record.typeIds)
        assertEquals(78, row.record.catchRate)
        assertEquals(0x6789, row.record.baseExperienceYield)
        assertEquals(0x056A, row.record.evYield)
        assertEquals(listOf(513, 1024), row.record.heldItemIds)
        assertEquals(127, row.record.genderRatio)
        assertEquals(20, row.record.eggCycles)
        assertEquals(70, row.record.baseFriendship)
        assertEquals(4, row.record.growthRate)
        assertEquals(listOf(1, 7), row.record.eggGroupIds)
        assertEquals(listOf(260, 65, 353, 34), row.record.abilityIds)
        assertEquals(9, row.record.safariZoneFleeRate)
        assertEquals(15, row.record.bodyColor)
        assertTrue(row.record.noFlip)
    }

    @Test
    fun omitsZeroAndDuplicateAbilitiesAcrossAllFourUnsignedSlots() {
        val bytes = ByteArray(64)
        putWideRow(bytes, 0)
        listOf(65535, 0, 65535, 513).forEachIndexed { slot, value -> putU16(bytes, 30 + slot * 2, value) }
        val row = decode(bytes, 0, 1).rows.single() as BaseStatsRowOutcome.Decoded

        assertEquals(listOf(65535, 513), row.record.abilityIds)
    }

    @Test
    fun distinguishesFullZeroRowsFromMalformedWideStatsAndTypes() {
        val bytes = ByteArray(4 * 64)
        putWideRow(bytes, 64)
        putWideRow(bytes, 128)
        putU16(bytes, 128 + 4, 0)
        putWideRow(bytes, 192)
        bytes[192 + 12] = 32
        val rows = decode(bytes, 0, 4).rows

        assertTrue(rows[0] is BaseStatsRowOutcome.StructuralEmpty)
        assertTrue(rows[1] is BaseStatsRowOutcome.Decoded)
        assertTrue(rows[2] is BaseStatsRowOutcome.Malformed)
        assertTrue(rows[3] is BaseStatsRowOutcome.Malformed)
    }

    @Test
    fun usesTheFullSixtyFourByteExtentAndRejectsOverflowBeforeRows() {
        val abi = wideAbi()
        val codec = BaseStatsCodec()
        val session = baseStatsSession(ByteArray(128), limits = ResolutionLimits(maxDatasetExtentBytes = 63))
        assertTrue(codec.decode(session, BaseStatsTableLayout(0, 1, abi)) is BaseStatsTableOutcome.ExtentBudgetExceeded)
        assertTrue(codec.decode(baseStatsSession(ByteArray(63)), BaseStatsTableLayout(0, 1, abi)) is BaseStatsTableOutcome.Rejected)
        assertTrue(codec.decode(session, BaseStatsTableLayout(Long.MAX_VALUE, Long.MAX_VALUE, abi)) is BaseStatsTableOutcome.Rejected)
    }

    @Test(expected = ParserCancellationException::class)
    fun propagatesCancellationBeforeExtentValidation() {
        val session = RomAnalysisSession(
            RomImage(ByteArray(64)), RomHeader(Platform.GBA, "SYNTHETIC"),
            cancellation = ParserCancellationToken { throw ParserCancellationException() },
        )
        BaseStatsCodec().decode(session, BaseStatsTableLayout(0, 1, BaseStatsAbi.RETAIL_28))
    }

    @Test(expected = ParserCancellationException::class)
    fun checksCancellationDuringWideRowDecoding() {
        val bytes = ByteArray(3 * 64)
        repeat(3) { putWideRow(bytes, it * 64) }
        var checks = 0
        val session = RomAnalysisSession(
            RomImage(bytes), RomHeader(Platform.GBA, "SYNTHETIC"),
            cancellation = ParserCancellationToken { if (++checks == 3) throw ParserCancellationException() },
        )
        BaseStatsCodec().decode(session, BaseStatsTableLayout(0, 3, wideAbi()))
    }

    private fun wideAbi(): BaseStatsAbi {
        val abi = BaseStatsAbi.entries.singleOrNull { it.recordSize == 64 }
        assertNotNull("compiled widened 64-byte stat ABI must be distinct", abi)
        return requireNotNull(abi)
    }

    private fun decode(bytes: ByteArray, offset: Long, count: Long): BaseStatsTableOutcome.Decoded =
        BaseStatsCodec().decode(baseStatsSession(bytes), BaseStatsTableLayout(offset, count, wideAbi())) as BaseStatsTableOutcome.Decoded

    private fun putWideRow(bytes: ByteArray, offset: Int) {
        listOf(345, 49, 299, 50, 512, 65535).forEachIndexed { field, value -> putU16(bytes, offset + field * 2, value) }
        bytes[offset + 12] = 12
        bytes[offset + 13] = 18
        bytes[offset + 14] = 78
        putU16(bytes, offset + 16, 0x6789)
        putU16(bytes, offset + 18, 0x056A)
        putU16(bytes, offset + 20, 513)
        putU16(bytes, offset + 22, 1024)
        bytes[offset + 24] = 127
        bytes[offset + 25] = 20
        bytes[offset + 26] = 70
        bytes[offset + 27] = 4
        bytes[offset + 28] = 1
        bytes[offset + 29] = 7
        listOf(260, 65, 353, 34).forEachIndexed { slot, value -> putU16(bytes, offset + 30 + slot * 2, value) }
        bytes[offset + 38] = 9
        bytes[offset + 39] = (0x80 or 15).toByte()
    }
}
