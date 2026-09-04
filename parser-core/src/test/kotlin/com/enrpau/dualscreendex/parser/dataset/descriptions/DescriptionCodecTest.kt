package com.enrpau.dualscreendex.parser.dataset.descriptions

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test

class DescriptionCodecTest {
    @Test
    fun decodesCanonicalThirtyTwoAndThirtySixByteLayouts() {
        listOf(
            DescriptionTableLayout(0x100, 3, 32, listOf(16)),
            DescriptionTableLayout(0x100, 3, 36, listOf(16, 20)),
        ).forEach { layout ->
            val bytes = ByteArray(0x1000)
            putDescriptionTable(
                bytes,
                layout.offset.toInt(),
                layout.count.toInt(),
                layout.recordSize,
                layout.pointerOffsets,
                0x600,
            )

            val decoded = DescriptionCodec().decode(descriptionSession(bytes), layout)

            assertTrue(decoded is DescriptionTableOutcome.Decoded)
            val rows = (decoded as DescriptionTableOutcome.Decoded).rows
            assertEquals(3, rows.size)
            assertTrue(rows.all { it is DescriptionRowOutcome.Decoded })
            assertEquals(layout.pointerOffsets.size, (rows[1] as DescriptionRowOutcome.Decoded).pages.size)
        }
    }

    @Test
    fun decodesJapaneseCategoryAndPagePastTerminatorValuedControlParameters() {
        val bytes = ByteArray(0x1000)
        val layout = DescriptionTableLayout(0x100, 1, 32, listOf(16))
        putDescriptionTable(bytes, 0x100, 1, 32, listOf(16), 0x600)
        val text = byteArrayOf(0x01, 0xF7.toByte(), 0xFF.toByte(), 0x02, 0xFF.toByte())
        text.copyInto(bytes, 0x100)
        text.copyInto(bytes, 0x600)

        val rows = (DescriptionCodec(JapanesePokemonTextCodecs.gen3Later).decode(
            descriptionSession(bytes), layout,
        ) as DescriptionTableOutcome.Decoded).rows

        assertTrue(rows.single() is DescriptionRowOutcome.Decoded)
        val row = rows.single() as DescriptionRowOutcome.Decoded
        assertEquals("あ い", row.category)
        assertEquals("あ い", row.pages.single().text)
    }

    @Test
    fun doesNotBorrowControlParametersFromTheNextReferencedPage() {
        val bytes = ByteArray(0x1000)
        val layout = DescriptionTableLayout(0x100, 2, 32, listOf(16))
        putDescriptionTable(bytes, 0x100, 2, 32, listOf(16), 0x600)
        val category = byteArrayOf(0x01, 0x02, 0xFF.toByte())
        category.copyInto(bytes, 0x100)
        category.copyInto(bytes, 0x120)
        putU32(bytes, 0x120 + 16, 0x08000604)
        byteArrayOf(0x01, 0x02, 0x03, 0xF7.toByte()).copyInto(bytes, 0x600)
        category.copyInto(bytes, 0x604)

        val rows = (DescriptionCodec(JapanesePokemonTextCodecs.gen3Later).decode(
            descriptionSession(bytes), layout,
        ) as DescriptionTableOutcome.Decoded).rows

        assertTrue(rows[0] is DescriptionRowOutcome.Malformed)
        assertTrue(rows[1] is DescriptionRowOutcome.Decoded)
        assertEquals("あい", (rows[1] as DescriptionRowOutcome.Decoded).pages.single().text)
    }

    @Test(expected = ParserCancellationException::class)
    fun propagatesCancellationDuringDescriptionDecoding() {
        val bytes = ByteArray(0x1000)
        val layout = DescriptionTableLayout(0x100, 1, 32, listOf(16))
        putDescriptionTable(bytes, 0x100, 1, 32, listOf(16), 0x600)
        val base = descriptionSession(bytes)
        var checks = 0
        val session = RomAnalysisSession(
            rom = base.rom,
            header = base.header,
            cancellation = ParserCancellationToken {
                if (++checks == 6) throw ParserCancellationException()
            },
        )

        DescriptionCodec().decode(session, layout)
    }

    @Test
    fun classifiesAnAllZeroRowAsStructuralEmpty() {
        val bytes = ByteArray(0x800)
        val layout = DescriptionTableLayout(0x100, 2, 32, listOf(16))
        putDescriptionTable(bytes, 0x100, 1, 32, listOf(16), 0x500)

        val rows = (DescriptionCodec().decode(
            descriptionSession(bytes),
            layout,
        ) as DescriptionTableOutcome.Decoded).rows

        assertTrue(rows[0] is DescriptionRowOutcome.Decoded)
        assertEquals(DescriptionRowOutcome.StructuralEmpty(1), rows[1])
    }

    @Test
    fun oneValidAndOneMalformedPageMakesTheWholeTwoPageRowMalformed() {
        val bytes = ByteArray(0x1000)
        val layout = DescriptionTableLayout(0x100, 2, 36, listOf(16, 20))
        putDescriptionTable(bytes, 0x100, 2, 36, listOf(16, 20), 0x600)
        // Remove page one terminator. Its decode must stop at the independently referenced page
        // two boundary rather than borrowing page two's terminator.
        (0 until 0x20).forEach { bytes[0x640 + it] = 0 }

        val rows = (DescriptionCodec().decode(
            descriptionSession(bytes),
            layout,
        ) as DescriptionTableOutcome.Decoded).rows

        val malformed = rows[1] as DescriptionRowOutcome.Malformed
        assertEquals(1, malformed.rowIndex)
        assertTrue(malformed.reasons.any { "page 1" in it })
    }

    @Test
    fun rejectsUnsupportedNegativeAndOutOfRowPointerFields() {
        val codec = DescriptionCodec()
        val session = descriptionSession(ByteArray(0x1000))
        val invalid = listOf(
            DescriptionTableLayout(0x100, 2, 28, listOf(16)),
            DescriptionTableLayout(0x100, 2, 32, listOf(-4)),
            DescriptionTableLayout(0x100, 2, 32, listOf(30)),
            DescriptionTableLayout(0x100, 2, 36, listOf(20, 16)),
            DescriptionTableLayout(0x100, 2, 36, listOf(16, 16)),
        )

        invalid.forEach { layout ->
            assertTrue(codec.decode(session, layout) is DescriptionTableOutcome.Rejected)
        }
    }

    @Test
    fun publicModelCollectionsAreValueStableCopies() {
        val pointerFields = mutableListOf(16)
        val layout = DescriptionTableLayout(0x100, 1, 32, pointerFields)
        val originalIdentity = layout.layoutIdentity
        pointerFields[0] = 20
        val bytes = ByteArray(0x800)
        putDescriptionTable(bytes, 0x100, 1, 32, listOf(16), 0x500)

        val decoded = DescriptionCodec().decode(
            descriptionSession(bytes),
            layout,
        ) as DescriptionTableOutcome.Decoded

        assertEquals(listOf(16), layout.pointerOffsets)
        assertEquals(layout, DescriptionTableLayout(0x100, 1, 32, listOf(16)))
        assertEquals(originalIdentity, layout.layoutIdentity)
        assertSame(layout, layout.immutableSnapshot())
        assertEquals(1, decoded.rows.size)
        assertEquals(decoded, DescriptionCodec().decode(descriptionSession(bytes), layout))
        assertUnmodifiable(layout.pointerOffsets)
        assertUnmodifiable(decoded.rows)
        assertUnmodifiable((decoded.rows.single() as DescriptionRowOutcome.Decoded).pages)
    }

    @Test
    fun rejectsNegativeAndCheckedLongTableExtentsBeforeReadingRows() {
        val codec = DescriptionCodec()
        val session = descriptionSession(
            ByteArray(0x1000),
            limits = ResolutionLimits(maxDatasetExtentBytes = 0x800),
        )
        val structurallyInvalid = listOf(
            DescriptionTableLayout(-1, 1, 32, listOf(16)),
            DescriptionTableLayout(Long.MAX_VALUE - 8, 2, 32, listOf(16)),
            DescriptionTableLayout(0, Long.MAX_VALUE, 32, listOf(16)),
        )

        structurallyInvalid.forEach { layout ->
            assertTrue(codec.decode(session, layout) is DescriptionTableOutcome.Rejected)
        }

        val overBudget = codec.decode(
            session,
            DescriptionTableLayout(0, 0x100, 32, listOf(16)),
        ) as DescriptionTableOutcome.ExtentBudgetExceeded
        assertEquals(0x2000L, overBudget.observedBytes)
        assertEquals(0x800L, overBudget.limitBytes)
    }

    @Test
    fun recoversOnlyAgainstAnIndependentlyDecodedNextTextBoundaryAndPublishesProvenance() {
        val bytes = ByteArray(0x1200)
        val layout = DescriptionTableLayout(0x100, 3, 32, listOf(16))
        putDescriptionTable(bytes, 0x100, 3, 32, listOf(16), 0x800)
        bytes[0x800 + 0x20] = 0xFF.toByte()
        putGbaText(bytes, 0x800 + 0x21, "RECOVERED TEXT")

        val rows = (DescriptionCodec().decode(
            descriptionSession(bytes),
            layout,
        ) as DescriptionTableOutcome.Decoded).rows

        val recovered = (rows[1] as DescriptionRowOutcome.Decoded).pages.single()
        assertEquals("RECOVERED TEXT", recovered.text)
        assertEquals(
            DescriptionRecoveryProvenance.OffByOneWithinNextReferencedBoundary(
                originalPointer = 0x820,
                recoveredPointer = 0x821,
                nextReferencedBoundary = 0x840,
            ),
            recovered.provenance,
        )
    }

    @Test
    fun doesNotRecoverPastTheDescriptionByteBudget() {
        val bytes = ByteArray(0x1200)
        val layout = DescriptionTableLayout(0x100, 2, 32, listOf(16))
        putDescriptionTable(bytes, 0x100, 2, 32, listOf(16), 0x600)
        putU32(bytes, 0x120 + 16, 0x08000900)
        bytes[0x600] = 0xFF.toByte()
        putGbaText(bytes, 0x601, "A".repeat(520))
        putGbaText(bytes, 0x900, "NEXT DESCRIPTION")

        val rows = (DescriptionCodec().decode(
            descriptionSession(bytes), layout,
        ) as DescriptionTableOutcome.Decoded).rows

        assertTrue(rows[0] is DescriptionRowOutcome.Malformed)
        assertTrue(rows[1] is DescriptionRowOutcome.Decoded)
    }

    @Test
    fun doesNotRecoverWithoutAnIndependentlyDecodedNextBoundary() {
        val bytes = ByteArray(0x1200)
        val layout = DescriptionTableLayout(0x100, 2, 32, listOf(16))
        putDescriptionTable(bytes, 0x100, 2, 32, listOf(16), 0x800)
        bytes[0x820] = 0xFF.toByte()
        putGbaText(bytes, 0x821, "RECOVERED TEXT")

        val rows = (DescriptionCodec().decode(
            descriptionSession(bytes),
            layout,
        ) as DescriptionTableOutcome.Decoded).rows

        assertTrue(rows[1] is DescriptionRowOutcome.Malformed)
    }

    @Test
    fun doesNotGuessBetweenTwoDistinctBoundedRecoveryAlternativesInOneRow() {
        val bytes = ByteArray(0x1800)
        val layout = DescriptionTableLayout(0x100, 3, 36, listOf(16, 20))
        putDescriptionTable(bytes, 0x100, 3, 36, listOf(16, 20), 0x1000)
        val row = 0x100 + 36
        val first = 0x1040
        val second = 0x1060
        putU32(bytes, row + 16, 0x08000000 + first)
        putU32(bytes, row + 20, 0x08000000 + second)
        bytes[first] = 0xFF.toByte()
        putGbaText(bytes, first + 1, "FIRST RECOVERY")
        bytes[second] = 0xFF.toByte()
        putGbaText(bytes, second + 1, "SECOND RECOVERY")

        val rows = (DescriptionCodec().decode(
            descriptionSession(bytes),
            layout,
        ) as DescriptionTableOutcome.Decoded).rows

        val malformed = rows[1] as DescriptionRowOutcome.Malformed
        assertTrue(malformed.reasons.any { "multiple bounded recovery alternatives" in it })
    }

    private fun assertUnmodifiable(values: List<*>) {
        try {
            @Suppress("UNCHECKED_CAST")
            (values as MutableList<Any?>).add(null)
            fail("public model collection must not be mutable")
        } catch (_: UnsupportedOperationException) {
            // Expected: the public value is a stable snapshot.
        }
    }
}
