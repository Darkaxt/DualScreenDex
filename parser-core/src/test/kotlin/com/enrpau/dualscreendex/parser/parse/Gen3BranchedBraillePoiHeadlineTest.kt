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

class Gen3BranchedBraillePoiHeadlineTest {
    @Test
    fun classifiesMixedBrailleAndOrdinaryBranchesWithoutSelectingALabel() {
        for (origin in listOf(0, 0x2000)) for (standard in 3..5) {
            val fixture = fixture(origin, standard)
            val poi = fixture.resolve()
            assertEquals("local/1/bg/0", poi.key)
            assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, poi.textObligation)
            assertNull(poi.displayName)
            assertEquals(emptyMap<Int, String>(), poi.displayNamesByTrainerGender)
        }
    }

    @Test
    fun matchingBrailleBranchesUseTheTransportSkipAndSeparateCellAlphabet() {
        for (origin in listOf(0, 0x2000)) {
            val fixture = fixture(origin)
            fixture.braille(fixture.branch, fixture.otherText)
            fixture.bytes.copyInto(fixture.bytes, fixture.otherText, fixture.text, fixture.text + 9)
            val poi = fixture.resolve()
            assertEquals(LocalMapPoiTextObligation.DIRECT_TEXT, poi.textObligation)
            assertEquals("⠁⠃", poi.displayName)
        }
    }

    @Test
    fun brailleFirstLinesIgnoreLaterLinesButNotInvalidFirstLineCells() {
        val fixture = fixture(0)
        fixture.braille(fixture.branch, fixture.otherText)
        byteArrayOf(1, 5, 0xfe.toByte(), 0xfd.toByte(), 2, 0xff.toByte())
            .copyInto(fixture.bytes, fixture.otherText + 6)
        assertEquals("⠁⠃", fixture.resolve().displayName)
        fixture.bytes[fixture.otherText + 7] = 64
        assertUnavailable(fixture)
    }

    @Test
    fun malformedBraillePathsInvalidateTheWholeGraph() {
        for (damage in listOf("wait", "pointer", "instruction", "transport", "cell", "runtime", "blank", "unterminated")) {
            val fixture = fixture(0)
            when (damage) {
                "wait" -> fixture.bytes[fixture.script + 15] = 0x66
                "pointer" -> fixture.bytes.fill(0, fixture.script + 11, fixture.script + 15)
                "instruction" -> {
                    fixture.pointer(fixture.script + 6, fixture.bytes.size - 5)
                    fixture.bytes[fixture.bytes.size - 5] = 0x78
                }
                "transport" -> fixture.pointer(fixture.script + 11, fixture.bytes.size - 6)
                "cell" -> fixture.bytes[fixture.text + 6] = 64
                "runtime" -> byteArrayOf(0xfd.toByte(), 2, 0xff.toByte()).copyInto(fixture.bytes, fixture.text + 6)
                "blank" -> fixture.bytes[fixture.text + 6] = 0xff.toByte()
                "unterminated" -> fixture.bytes.fill(1, fixture.text + 6, fixture.text + 166)
            }
            assertUnavailable(fixture)
        }
    }

    @Test
    fun divergentBrailleHeadlinesDoNotHideALaterOpaqueCyclicOrExhaustedPath() {
        for (damage in listOf("opaque", "cycle", "budget", "text")) {
            val fixture = fixture(0)
            fixture.bytes[fixture.branch] = 6
            fixture.bytes[fixture.branch + 1] = 1
            fixture.pointer(fixture.branch + 2, fixture.branch + 32)
            fixture.braille(fixture.branch + 6, fixture.text)
            fixture.braille(fixture.branch + 32, fixture.otherText)
            fixture.bytes.copyInto(fixture.bytes, fixture.otherText, fixture.text, fixture.text + 9)
            fixture.bytes[fixture.otherText + 6] = 2
            val last = fixture.script + 10
            when (damage) {
                "opaque" -> fixture.bytes[last] = 0x60
                "cycle" -> {
                    fixture.bytes[last] = 5
                    fixture.pointer(last + 1, fixture.script)
                }
                "budget" -> fixture.bytes.fill(0x69, last, last + 129)
                "text" -> fixture.pointer(last + 1, fixture.bytes.size - 6)
            }
            assertUnavailable(fixture)
        }
    }

    @Test
    fun otherFamiliesDoNotBorrowTheEmeraldBranchedBrailleContract() {
        for (family in listOf(EngineFamily.RUBY_SAPPHIRE, EngineFamily.FIRERED_LEAFGREEN)) {
            val poi = fixture(0).resolve(family)
            assertEquals(LocalMapPoiTextObligation.UNRESOLVED, poi.textObligation)
            assertNull(poi.displayName)
        }
    }

    @Test(expected = ParserCancellationException::class)
    fun cancellationStillPropagatesThroughTheBranchedBrailleRoute() {
        var checks = 0
        fixture(0).resolve(cancellation = ParserCancellationToken {
            if (++checks == 5) throw ParserCancellationException()
        })
    }

    @Test(expected = ParserCancellationException::class)
    fun cancellationAfterDivergentBrailleHeadlinesCannotReturnAContextualResult() {
        val fixture = fixture(0)
        fixture.bytes[fixture.branch] = 6
        fixture.bytes[fixture.branch + 1] = 1
        fixture.pointer(fixture.branch + 2, fixture.branch + 32)
        fixture.braille(fixture.branch + 6, fixture.text)
        fixture.braille(fixture.branch + 32, fixture.otherText)
        var reads = 0
        Gen3InvariantSignHeadlineResolver.resolve(
            RomImage(fixture.bytes), fixture.script,
            ParserCancellationToken { if (reads == 2) throw ParserCancellationException() },
        ) { text, braille ->
            assertEquals(true, braille)
            reads++
            if (text == fixture.text) "⠁" else "⠃"
        }
    }

    private fun assertUnavailable(fixture: Fixture) {
        val poi = fixture.resolve()
        assertEquals("local/1/bg/0", poi.key)
        assertEquals(LocalMapPoiTextObligation.UNRESOLVED, poi.textObligation)
        assertNull(poi.displayName)
    }

    private fun fixture(origin: Int, standard: Int = 4): Fixture = Fixture(ByteArray(origin + 0x1000), origin).apply {
        pointer(header + 4, events)
        bytes[events + 3] = 1
        pointer(events + 16, background)
        bytes[background] = 2
        bytes[background + 2] = 3
        pointer(background + 8, script)
        byteArrayOf(0x69, 0x2b, 0x23, 0x01, 0x06, 0x01).copyInto(bytes, script)
        pointer(script + 6, branch)
        braille(script + 10, text)
        bytes[branch] = 15
        pointer(branch + 2, otherText)
        bytes[branch + 6] = 9
        bytes[branch + 7] = standard.toByte()
        byteArrayOf(0xfd.toByte(), 2, 0xfe.toByte(), 0xff.toByte(), 0xfc.toByte(), 19, 1, 5, 0xff.toByte())
            .copyInto(bytes, text)
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

        fun braille(at: Int, target: Int) {
            bytes[at] = 0x78
            pointer(at + 1, target)
            bytes[at + 5] = 0x6d
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
