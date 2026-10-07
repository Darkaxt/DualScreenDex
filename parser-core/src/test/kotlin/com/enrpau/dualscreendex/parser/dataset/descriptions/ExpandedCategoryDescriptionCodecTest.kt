package com.enrpau.dualscreendex.parser.dataset.descriptions

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedCategoryDescriptionCodecTest {
    @Test
    fun relocatedRowsDecodeDistinctCategoryDimensionsAndSinglePointer() {
        listOf(0x40, 0x180).forEach { root ->
            val bytes = ByteArray(0x1000)
            putExpandedDescription(bytes, root, 0x800, "ABCDEFGHIJKLM", 700, 6900)
            val row = decoded(bytes, root).rows.single() as DescriptionRowOutcome.Decoded
            assertEquals("ABCDEFGHIJKLM", row.category)
            assertEquals(700, row.height)
            assertEquals(6900, row.weight)
            assertEquals("INDEPENDENT DESCRIPTION", row.pages.single().text)
            assertEquals(DescriptionRecoveryProvenance.Direct(0x800), row.pages.single().provenance)
        }
    }

    @Test
    fun expandedAndLegacyThirtySixByteShapesAreNotAliases() {
        val bytes = ByteArray(0x1000)
        putExpandedDescription(bytes, 0x100, 0x800)
        val expanded = DescriptionTableLayout(0x100, 1, 36, listOf(20))
        val legacy = DescriptionTableLayout(0x100, 1, 36, listOf(16, 20))
        assertTrue(expanded.layoutIdentity != legacy.layoutIdentity)
        assertTrue(decoded(bytes, 0x100).rows.single() is DescriptionRowOutcome.Decoded)
        val old = DescriptionCodec().decode(descriptionSession(bytes), legacy) as DescriptionTableOutcome.Decoded
        assertTrue(old.rows.single() is DescriptionRowOutcome.Malformed)
    }

    @Test
    fun categoryCannotBorrowItsTerminatorFromTheHeightField() {
        val bytes = ByteArray(0x1000)
        putExpandedDescription(bytes, 0x100, 0x800)
        repeat(14) { bytes[0x100 + it] = 0xBB.toByte() }
        putU16(bytes, 0x100 + 14, 255)
        val row = decoded(bytes, 0x100).rows.single() as DescriptionRowOutcome.Malformed
        assertTrue(row.reasons.any { "category" in it })
    }

    @Test
    fun malformedDimensionsPointerAndTextRemainMalformed() {
        listOf<(ByteArray) -> Unit>(
            { putU16(it, 0x100 + 14, 10001) },
            { putU32(it, 0x100 + 20, 0x02000000) },
            { repeat(512) { index -> it[0x800 + index] = 0xBB.toByte() } },
        ).forEach { mutate ->
            val bytes = ByteArray(0x1000)
            putExpandedDescription(bytes, 0x100, 0x800)
            mutate(bytes)
            assertTrue(decoded(bytes, 0x100).rows.single() is DescriptionRowOutcome.Malformed)
        }
    }

    @Test
    fun zeroRowsExtentsAndBudgetsRemainDistinct() {
        val bytes = ByteArray(0x1000)
        putExpandedDescription(bytes, 0x100, 0x800)
        val layout = DescriptionTableLayout(0x100, 2, 36, listOf(20))
        val rows = (DescriptionCodec().decode(descriptionSession(bytes), layout) as DescriptionTableOutcome.Decoded).rows
        assertEquals(DescriptionRowOutcome.StructuralEmpty(1), rows[1])
        assertTrue(DescriptionCodec().decode(descriptionSession(bytes.copyOf(0x100 + 35)),
            DescriptionTableLayout(0x100, 1, 36, listOf(20))) is DescriptionTableOutcome.Rejected)
        assertTrue(DescriptionCodec().decode(descriptionSession(bytes,
            limits = ResolutionLimits(maxDatasetExtentBytes = 35)),
            DescriptionTableLayout(0x100, 1, 36, listOf(20))) is DescriptionTableOutcome.ExtentBudgetExceeded)
    }

    @Test(expected = ParserCancellationException::class)
    fun cancellationPropagatesDuringExpandedTextDecoding() {
        val bytes = ByteArray(0x1000)
        putExpandedDescription(bytes, 0x100, 0x800)
        val base = descriptionSession(bytes)
        var checks = 0
        DescriptionCodec().decode(RomAnalysisSession(base.rom, base.header,
            cancellation = ParserCancellationToken { if (++checks == 6) throw ParserCancellationException() }),
            DescriptionTableLayout(0x100, 1, 36, listOf(20)))
    }

    private fun decoded(bytes: ByteArray, root: Int): DescriptionTableOutcome.Decoded {
        val outcome = DescriptionCodec().decode(descriptionSession(bytes),
            DescriptionTableLayout(root.toLong(), 1, 36, listOf(20)))
        assertTrue("distinct expanded-category description ABI is unavailable", outcome is DescriptionTableOutcome.Decoded)
        return outcome as DescriptionTableOutcome.Decoded
    }
}

internal fun putExpandedDescription(
    bytes: ByteArray,
    root: Int,
    text: Int,
    category: String = "EXPANDED",
    height: Int = 700,
    weight: Int = 6900,
) {
    putGbaText(bytes, root, category)
    putU16(bytes, root + 14, height)
    putU16(bytes, root + 16, weight)
    putU32(bytes, root + 20, 0x08000000 + text)
    putGbaText(bytes, text, "INDEPENDENT DESCRIPTION")
}
