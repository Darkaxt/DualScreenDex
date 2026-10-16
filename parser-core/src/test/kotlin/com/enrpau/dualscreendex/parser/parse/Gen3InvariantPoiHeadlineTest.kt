package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiTextObligation
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.text.WesternPokemonTextCodecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen3InvariantPoiHeadlineTest {
    @Test
    fun flagBranchesWithTheSameFirstHeadlineRecoverAStaticNameAfterRelocation() {
        for (origin in listOf(0, 0x2000)) for (standard in 3..5) {
            val fixture = fixture(origin, standard)
            val poi = fixture.resolve()
            assertEquals("local/1/bg/0", poi.key)
            assertEquals(LocalMapPoiTextObligation.DIRECT_TEXT, poi.textObligation)
            assertEquals("AB", poi.displayName)
        }
    }

    @Test
    fun laterLinesAndDifferentMessagePointersDoNotMakeTheFirstHeadlineContextual() {
        val fixture = fixture(0)
        byteArrayOf(0xbb.toByte(), 0xbc.toByte(), 0xfe.toByte(), 0xbd.toByte(), 0xff.toByte())
            .copyInto(fixture.bytes, fixture.text)
        byteArrayOf(0xbb.toByte(), 0xbc.toByte(), 0xfe.toByte(), 0xfd.toByte(), 2, 0xff.toByte())
            .copyInto(fixture.bytes, fixture.otherText)
        assertEquals("AB", fixture.resolve().displayName)
    }

    @Test
    fun differentFirstHeadlinesAreClassifiedAsContextualWithoutChoosingABranchLabel() {
        for (origin in listOf(0, 0x2000)) for (standard in 3..5) {
            val fixture = fixture(origin, standard)
            fixture.bytes[fixture.otherText + 1] = 0xbd.toByte()
            val poi = fixture.resolve()
            assertEquals("local/1/bg/0", poi.key)
            assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, poi.textObligation)
            assertNull(poi.displayName)
        }
    }

    @Test
    fun differingHeadlinesDoNotHideALaterInvalidPath() {
        for (damage in listOf("opaque", "cycle", "instruction", "text", "runtime", "budget", "buffer", "comparison")) {
            val fixture = differingThreeWayFixture()
            val last = fixture.script + 10
            when (damage) {
                "opaque" -> fixture.bytes[last] = 0x25
                "cycle" -> {
                    fixture.bytes[last] = 5
                    fixture.pointer(last + 1, fixture.script)
                }
                "instruction" -> {
                    fixture.bytes[last] = 5
                    fixture.pointer(last + 1, fixture.bytes.size - 3)
                    fixture.bytes[fixture.bytes.size - 3] = 15
                }
                "text" -> {
                    fixture.pointer(last + 2, fixture.bytes.lastIndex)
                    fixture.bytes[fixture.bytes.lastIndex] = 0xbb.toByte()
                }
                "runtime" -> {
                    fixture.pointer(last + 2, fixture.text + 32)
                    byteArrayOf(0xfd.toByte(), 2, 0xff.toByte()).copyInto(fixture.bytes, fixture.text + 32)
                }
                "budget" -> fixture.bytes.fill(0x69, last, last + 129)
                "buffer" -> {
                    fixture.bytes[last] = 9
                    fixture.bytes[last + 1] = 3
                }
                "comparison" -> {
                    fixture.bytes[last] = 6
                    fixture.bytes[last + 1] = 6
                }
            }
            assertUnavailable(fixture)
        }
    }

    @Test(expected = ParserCancellationException::class)
    fun cancellationAfterDifferingHeadlinesIsNotTurnedIntoAContextualResult() {
        val fixture = differingThreeWayFixture()
        var reads = 0
        Gen3InvariantSignHeadlineResolver.resolve(
            RomImage(fixture.bytes), fixture.script,
            ParserCancellationToken { if (reads == 2) throw ParserCancellationException() },
        ) { text, _ ->
            reads++
            if (text == fixture.text) "AB" else "AC"
        }
    }

    @Test
    fun anOpaqueOrMessageFreePathInvalidatesTheWholeHeadline() {
        for (opcode in listOf(2, 3, 4, 0x25, 0x1a, 0x2d)) {
            val fixture = fixture(0)
            fixture.bytes[fixture.branch] = opcode.toByte()
            assertUnavailable(fixture)
        }
    }

    @Test
    fun cyclicBranchesNeverUseTheOtherPathsName() {
        val fixture = fixture(0)
        fixture.bytes[fixture.branch] = 5
        fixture.pointer(fixture.branch + 1, fixture.script)
        assertUnavailable(fixture)
    }

    @Test
    fun badBranchPointersAndUnknownComparisonBytesFailClosed() {
        for (damage in listOf("pointer", "comparison")) {
            val fixture = fixture(0)
            if (damage == "pointer") repeat(4) { fixture.bytes[fixture.script + 6 + it] = 0 }
            else fixture.bytes[fixture.script + 5] = 6
            assertUnavailable(fixture)
        }
    }

    @Test
    fun otherLoadWordSlotsAndUnqualifiedStandardScriptsDoNotBorrowTheContract() {
        for (standard in listOf(0, 1, 2, 6, 7, 8, 9, 10, 255)) {
            assertUnavailable(fixture(0, standard))
        }
        val fixture = fixture(0)
        fixture.bytes[fixture.branch + 1] = 1
        assertUnavailable(fixture)
    }

    @Test
    fun runtimeFirstLineTokensRemainUnavailableEvenWhenTheyMatchAcrossBranches() {
        val fixture = fixture(0)
        val text = byteArrayOf(0xbb.toByte(), 0xbc.toByte(), 0xfd.toByte(), 2, 0xff.toByte())
        text.copyInto(fixture.bytes, fixture.text)
        text.copyInto(fixture.bytes, fixture.otherText)
        assertUnavailable(fixture)
    }

    @Test
    fun truncatedBranchInstructionsPreserveThePoiAndItsUnresolvedObligation() {
        for (remaining in 1..5) {
            val fixture = fixture(0)
            val target = fixture.bytes.size - remaining
            fixture.pointer(fixture.script + 6, target)
            fixture.bytes.fill(15, target)
            assertUnavailable(fixture)
        }
    }

    @Test
    fun truncatedAndUnterminatedHeadlinesDoNotPublishAPartialName() {
        val fixture = fixture(0)
        fixture.bytes.fill(0xbb.toByte(), fixture.otherText, fixture.otherText + 160)
        assertUnavailable(fixture)
        val truncated = fixture(0)
        truncated.pointer(truncated.branch + 2, truncated.bytes.size - 1)
        truncated.bytes[truncated.bytes.lastIndex] = 0xbb.toByte()
        assertUnavailable(truncated)
    }

    @Test
    fun otherFamiliesRetainTheirExistingFailClosedRoute() {
        for (family in listOf(EngineFamily.RUBY_SAPPHIRE, EngineFamily.FIRERED_LEAFGREEN)) {
            val poi = fixture(0).resolve(family)
            assertNull(poi.displayName)
            assertEquals(LocalMapPoiTextObligation.UNRESOLVED, poi.textObligation)
        }
    }

    @Test
    fun normalizedFirstLinesCanAgreeWithoutSharingRawBytes() {
        val fixture = fixture(0)
        byteArrayOf(0, 0xbb.toByte(), 0xbc.toByte(), 0xfc.toByte(), 0x13, 0xfd.toByte(), 0xff.toByte())
            .copyInto(fixture.bytes, fixture.otherText)
        assertEquals("AB", fixture.resolve().displayName)
    }

    @Test
    fun unconditionalJumpsMayConvergeOnTheSameHeadlineWithoutBecomingCycles() {
        val fixture = fixture(0)
        fixture.bytes[fixture.branch] = 5
        fixture.pointer(fixture.branch + 1, fixture.script + 10)
        assertEquals("AB", fixture.resolve().displayName)
    }

    @Test
    fun excessiveStateWorkNeverPublishesTheLaterMessage() {
        val fixture = fixture(0)
        fixture.bytes.fill(0x69, fixture.script, fixture.script + 129)
        fixture.message(fixture.script + 129, fixture.text, 3)
        assertUnavailable(fixture)
    }

    @Test(expected = ParserCancellationException::class)
    fun cancellationInsideTheGraphIsNotSwallowedAsAnOptionalMapFailure() {
        var checks = 0
        fixture(0).resolve(cancellation = ParserCancellationToken {
            if (++checks == 5) throw ParserCancellationException()
        })
    }

    @Test
    fun aMissingBufferedWordOrInvalidRootNeverInvokesTheHeadlineReader() {
        val bytes = byteArrayOf(9, 3)
        for (root in listOf(-1, 0, bytes.size)) {
            var reads = 0
            assertNull(Gen3InvariantSignHeadlineResolver.resolve(RomImage(bytes), root) { _, _ ->
                reads++
                "AB"
            })
            assertEquals(0, reads)
        }
    }

    private fun assertUnavailable(fixture: Fixture) {
        val poi = fixture.resolve()
        assertEquals("local/1/bg/0", poi.key)
        assertEquals(LocalMapPoiTextObligation.UNRESOLVED, poi.textObligation)
        assertNull(poi.displayName)
    }

    private fun differingThreeWayFixture(): Fixture = fixture(0).apply {
        bytes[otherText + 1] = 0xbd.toByte()
        bytes[branch] = 6
        bytes[branch + 1] = 1
        pointer(branch + 2, branch + 32)
        message(branch + 6, text, 3)
        message(branch + 32, otherText, 3)
    }

    private fun fixture(origin: Int, standard: Int = 3): Fixture = Fixture(ByteArray(origin + 0x1000), origin).apply {
        pointer(header + 4, events)
        bytes[events + 3] = 1
        pointer(events + 16, background)
        bytes[background] = 2
        bytes[background + 2] = 3
        pointer(background + 8, script)
        byteArrayOf(0x69, 0x2b, 0x23, 0x01, 0x06, 0x01).copyInto(bytes, script)
        pointer(script + 6, branch)
        message(script + 10, text, standard)
        message(branch, otherText, standard)
        byteArrayOf(0xbb.toByte(), 0xbc.toByte(), 0xff.toByte()).copyInto(bytes, text)
        byteArrayOf(0xbb.toByte(), 0xbc.toByte(), 0xff.toByte()).copyInto(bytes, otherText)
    }

    private class Fixture(val bytes: ByteArray, origin: Int) {
        val header = origin + 0x100
        val events = origin + 0x200
        val background = origin + 0x400
        val script = origin + 0x500
        val branch = origin + 0x600
        val text = origin + 0x700
        val otherText = origin + 0x800

        fun pointer(at: Int, target: Int) {
            val value = 0x08000000L + target
            repeat(4) { index -> bytes[at + index] = (value ushr (index * 8)).toByte() }
        }

        fun message(at: Int, target: Int, standard: Int) {
            bytes[at] = 15
            bytes[at + 1] = 0
            pointer(at + 2, target)
            bytes[at + 6] = 9
            bytes[at + 7] = standard.toByte()
            bytes[at + 8] = 2
        }

        fun resolve(
            family: EngineFamily = EngineFamily.EMERALD,
            cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
        ) = Gen3LocalMapPoiResolver.resolve(
            RomImage(bytes), mapOf(1 to header),
            listOf(LocalMap("local/1", null, 1, 160, 160, 10, 10, "asset/1")),
            family, WesternPokemonTextCodecs.gen3English, cancellation,
        ).also { assertEquals(emptyList<String>(), it.skippedReasons) }.pois.single()
    }
}
