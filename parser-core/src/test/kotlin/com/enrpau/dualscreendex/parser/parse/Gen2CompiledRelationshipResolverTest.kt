package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Gen2CompiledRelationshipResolverTest {
    @Test fun recoversRelocatedSameBankCombinedTable() {
        listOf(1, 3).forEach { bank ->
            val result = requireNotNull(resolve(fixture(bank)))
            assertEquals(bank * BANK + 0x1000, requireNotNull(result.table).offset)
            assertEquals(251, result.table.count)
            assertEquals(bank, result.table.bank)
        }
    }

    @Test fun acceptsIndependentlyEnumeratedExpandedByteSpecies() {
        assertEquals(253, requireNotNull(resolve(fixture(count = 253), 253)?.table).count)
    }

    @Test fun acceptsEmptyRowsButNotAnEntireEmptyDataset() {
        val bytes = fixture()
        bytes.put(TABLE + 251 * 2, "00 00")
        assertNull(resolve(bytes)?.table)
    }

    @Test fun rejectsChangedIndexBranchesStatWidthAndSpeciesInput() {
        val original = fixture()
        listOf(PRE + 7, PRE + 17, PRE + 19, PRE + 20, PRE + 23,
            PRE + 32, PRE + 36, PRE + 38, PRE + 44,
            LEARN + 6, LEARN + 8, LEARN + 13, LEARN + 21, LEARN + 33,
            DONE + 1, DONE + 4).forEach { offset ->
            val bytes = original.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() }
            assertNull("changed linked contract $offset", resolve(bytes)?.table)
        }
    }

    @Test fun rejectsMalformedRecordsReferencesAndTerminators() {
        val original = fixture()
        val row = TABLE + 251 * 2
        listOf(row to 6, row + 3 to 252, row + 5 to 101, row + 6 to 252,
            row + 7 to 1).forEach { (offset, value) ->
            assertNull("malformed row $offset", resolve(original.copyOf().also { it[offset] = value.toByte() })?.table)
        }
    }

    @Test fun rejectsPointerToHomeBankAndTableOrRecordCrossingBank() {
        val original = fixture()
        assertNull(resolve(original.copyOf().also { it.word(TABLE, 0x200) })?.table)
        assertNull(resolve(original.copyOf().also { it.word(PRE + 3, 0x7FF0); it.word(LEARN + 11, 0x7FF0) })?.table)
        assertNull(resolve(original.copyOf().also { it.word(TABLE, 0x7FFF); it[0x7FFF] = 1 })?.table)
        assertNull(resolve(original.copyOf(TABLE + 251 * 2 + 5))?.table)
    }

    @Test fun competingCompleteRootsFailClosedEvenIfOneTableIsMalformed() {
        val original = fixture()
        val other = fixture(3)
        other.copyInto(original, 3 * BANK, 3 * BANK, 4 * BANK)
        assertNotNull(resolve(original)?.rejection)
        assertNull(resolve(original)?.table)
        original[3 * BANK + 0x1000 + 251 * 2] = 6
        assertNull(resolve(original)?.table)
    }

    @Test fun missingContractsDoNotAuthorizePlausibleRows() {
        val bytes = fixture()
        bytes[PRE] = 0
        bytes[LEARN] = 0
        assertNull(resolve(bytes))
    }

    @Test fun exhaustedDiscoveryAndExtentBudgetsDoNotPublishEarlyWinner() {
        assertNull(resolve(fixture(), limits = ResolutionLimits(maxProbeWorkPerDataset = 1))?.table)
        assertNotNull(resolve(fixture(), limits = ResolutionLimits(maxProbeWorkPerDataset = 1))?.rejection)
        assertNull(resolve(fixture(), limits = ResolutionLimits(maxDatasetExtentBytes = 10))?.table)
    }

    @Test fun preservesCancellationInsteadOfTurningItIntoUnavailableData() {
        val source = com.enrpau.dualscreendex.parser.analysis.ParserCancellationSource()
        source.cancel()
        org.junit.Assert.assertThrows(com.enrpau.dualscreendex.parser.analysis.ParserCancellationException::class.java) {
            Gen2CompiledRelationshipResolver.resolve(RomAnalysisSession(
                RomImage(fixture()), RomHeader(Platform.GBC, "FABRICATED"), cancellation = source.token,
            ), 251, 251)
        }
    }

    @Test fun acceptsStructurallyEmptySpeciesAlongsideActiveRows() {
        val bytes = fixture()
        bytes.word(TABLE, 0x6000)
        assertNotNull(resolve(bytes)?.table)
    }

    @Test fun rejectsCompetingInputAliasesAndLevelRestorationBankCrossing() {
        val original = fixture()
        original.copyOfRange(PRE, PRE + 48).copyInto(original, PRE + 0x50)
        original.word(PRE + 0x50 + 23, 0xC130)
        original.word(PRE + 0x50 + 44, 0xC130)
        assertNull(resolve(original)?.table)
        assertNull(resolve(fixture().also { it.word(LEARN + 28, 0xC100) })?.table)
        val crossing = fixture()
        crossing.copyOfRange(LEARN, LEARN + 34).copyInto(crossing, 0x7FC0)
        crossing[LEARN] = 0
        crossing[0x7FC0 + 25] = (0x7FFD - (0x7FC0 + 26)).toByte()
        crossing.put(0x7FFD, "fa 00 c1 ea 10 c1 c9")
        assertNull(resolve(crossing)?.table)
    }

    private fun resolve(bytes: ByteArray, count: Int = 251, limits: ResolutionLimits = ResolutionLimits()) =
        Gen2CompiledRelationshipResolver.resolve(
            RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBC, "FABRICATED"), limits = limits), count, 251,
        )

    internal fun fixture(bank: Int = 1, count: Int = 251): ByteArray = ByteArray(4 * BANK).also { bytes ->
        val base = bank * BANK
        bytes.put(base + 0x100, "0e 00 21 00 50 06 00 09 09 2a 66 6f 2a a7 28 11 fe 05 20 01 23 23 fa 00 c1 be 28 0d 23 7e a7 20 eb 0c 79 fe fb 38 db a7 c9 0c 79 ea 00 c1 37 c9")
        bytes[base + 0x100 + 36] = count.toByte()
        bytes.put(base + 0x200, "fa 10 c1 ea 00 c1 3d 06 00 4f 21 00 50 09 09 2a 66 6f 2a a7 20 fc 2a a7 28 28 47 fa 20 c1 b8 2a 20 f4")
        bytes.put(base + 0x242, "fa 00 c1 ea 10 c1 c9")
        val table = base + 0x1000
        val row = table + count * 2
        repeat(count) { bytes.word(table + it * 2, 0x4000 + row % BANK) }
        bytes.put(row, "05 14 01 02 00 01 01 00")
    }

    private fun ByteArray.put(offset: Int, value: String) = value.split(' ')
        .map { it.toInt(16).toByte() }.toByteArray().copyInto(this, offset)
    private fun ByteArray.word(offset: Int, value: Int) {
        this[offset] = value.toByte(); this[offset + 1] = (value ushr 8).toByte()
    }
    private companion object {
        const val BANK = 0x4000
        const val PRE = BANK + 0x100
        const val LEARN = BANK + 0x200
        const val DONE = BANK + 0x242
        const val TABLE = BANK + 0x1000
    }
}
