package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackedMoveFieldWitnessTest {
    @Test
    fun multipleIndependentReadsRemainWithinTheirActualRowExtent() {
        val fields = fields(listOf(0x4b0f, 0x0088, 0x1840, 0x0080, 0x18c2,
            0x8815, 0x7ad6, 0x7cd5, 0x8cd5, 0x4770))
        assertTrue(GbaMoveFieldWitness(ROOT, 20, 0, 2, false) in fields)
        assertTrue(GbaMoveFieldWitness(ROOT, 20, 11, 1, false) in fields)
        assertTrue(GbaMoveFieldWitness(ROOT, 20, 19, 1, false) in fields)
        assertFalse(fields.any { it.field == 18 && it.width == 2 }) // encoded halfword read is outside the row at 38
        assertFalse(fields.any { it.field + it.width > 20 })
    }

    @Test
    fun loadedRegisterGetsFreshIdentityInsteadOfAliasingItsOldIndexCopy() {
        val fields = fields(listOf(0x4b0f, 0x460c, 0x0088, 0x1840, 0x0080, 0x18c2,
            0x7811, 0x0120, 0x0089, 0x1840, 0x18c2, 0x7ad5, 0x4770))
        assertTrue(fields.any { it.field == 0 })
        assertFalse(fields.any { it.field == 11 })
    }

    @Test
    fun exactUnsignedHalfwordIndexNormalizationIsNotArbitraryAffineShifting() {
        val prefix = listOf(0x4b0f, 0x0408, 0x0c01, 0x0088, 0x1840, 0x0080, 0x18c2, 0x7ad5, 0x4770)
        assertTrue(fields(prefix).any { it.field == 11 })
        assertFalse(fields(prefix.toMutableList().also { it[2] = 0x0801 }).any { it.field == 11 })
        assertFalse(fields(prefix.toMutableList().also { it[1] = 0x0448 }).any { it.field == 11 })
    }

    @Test
    fun programCounterTransfersAndBranchingAddressFormationStopTheEnvelope() {
        listOf(0x46f8, 0x44f8, 0xe000, 0xf000).forEach { opcode ->
            assertFalse(fields(listOf(0x4b0f, opcode, 0x0088, 0x1840, 0x0080, 0x18c2, 0x7ad5))
                .any { it.field == 11 })
        }
    }

    @Test
    fun signedByteProofDoesNotSurviveWrongShiftClobberBackwardBranchOrCall() {
        val prefix = listOf(0x4b0f, 0x0088, 0x1840, 0x0080, 0x18c2, 0x7a95)
        assertTrue(fields(prefix + listOf(0x0628, 0x1600, 0x4770)).any { it.field == 10 && it.signed })
        listOf(
            listOf(0x2500, 0x0628, 0x1600),
            listOf(0x0628, 0x1000),
            listOf(0xe7fd, 0x0628, 0x1600),
            listOf(0xf000, 0x0628, 0x1600),
            listOf(0x4780, 0x0628, 0x1600),
        ).forEach { suffix -> assertFalse(fields(prefix + suffix).any { it.field == 10 && it.signed }) }
    }

    private fun fields(words: List<Int>): Set<GbaMoveFieldWitness> {
        val bytes = ByteArray(0x800)
        words.forEachIndexed { index, word ->
            bytes[SITE + index * 2] = word.toByte()
            bytes[SITE + index * 2 + 1] = (word ushr 8).toByte()
        }
        repeat(4) { bytes[SITE + 0x40 + it] = ((ROOT + 0x08000000) ushr (it * 8)).toByte() }
        val session = RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "PACKED WITNESS TEST"))
        return GbaAffineMoveFieldWitnesses.collectPacked(session, bytes.size, { it == ROOT }, {})
    }

    private companion object {
        const val SITE = 0x100
        const val ROOT = 0x600
    }
}
