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

class Gen3ComparisonPoiHeadlineTest {
    @Test
    fun comparisonBranchesClassifyDifferentHeadlinesWithoutSelectingALiveValue() {
        for (origin in listOf(0, 0x2000, 0x4000)) for (standard in 3..5) {
            val poi = fixture(origin, standard).resolve()
            assertEquals("local/1/bg/0", poi.key)
            assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, poi.textObligation)
            assertNull(poi.displayName)
            assertEquals(emptyMap<Int, String>(), poi.displayNamesByTrainerGender)
        }
    }

    @Test
    fun equalNormalizedFirstLinesRemainStaticDespiteDifferentLaterLines() {
        val fixture = fixture(0)
        byteArrayOf(0, 0xbb.toByte(), 0xbc.toByte(), 0xfe.toByte(), 0xfd.toByte(), 2, 0xff.toByte())
            .copyInto(fixture.bytes, fixture.otherText)
        val poi = fixture.resolve()
        assertEquals(LocalMapPoiTextObligation.DIRECT_TEXT, poi.textObligation)
        assertEquals("AB", poi.displayName)
    }

    @Test
    fun ordinaryVariableBoundsAndAllImmediateValuesUseTheSameClosedGraph() {
        for (variable in listOf(0x4000, 0x4001, 0x40ba, 0x40ff)) {
            for (value in listOf(0, 2, 0x4000, 0xffff)) {
                val fixture = fixture(0)
                fixture.comparison(fixture.script + 1, variable, value)
                assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, fixture.resolve().textObligation)
            }
        }
    }

    @Test
    fun nonordinaryVariableIdsDoNotBorrowTheReadOnlyContract() {
        for (variable in listOf(0, 0x3fff, 0x4100, 0x7fff, 0x8000, 0xffff)) {
            val fixture = fixture(0)
            fixture.comparison(fixture.script + 1, variable, 2)
            assertUnavailable(fixture)
        }
    }

    @Test
    fun truncatedComparisonsAndOpaqueOperationsRemainUnavailable() {
        for (remaining in 1..4) {
            val fixture = fixture(0)
            val target = fixture.bytes.size - remaining
            fixture.bytes[fixture.branch] = 5
            fixture.pointer(fixture.branch + 1, target)
            byteArrayOf(0x21, 0, 0x40, 2, 0).copyInto(fixture.bytes, target, 0, remaining)
            assertUnavailable(fixture)
        }
        for (opcode in listOf(0x16, 0x25, 0x60, 0x83, 0x97, 0xc3)) {
            val fixture = fixture(0)
            fixture.bytes[fixture.script + 1] = opcode.toByte()
            assertUnavailable(fixture)
        }
    }

    @Test
    fun differingHeadlinesCannotHideALaterInvalidComparisonPath() {
        for (damage in listOf("variable", "opaque", "cycle", "budget", "runtime", "condition")) {
            val fixture = threeWayFixture()
            val last = fixture.script + 12
            when (damage) {
                "variable" -> fixture.comparison(last, 0x8000, 2)
                "opaque" -> fixture.bytes[last] = 0xc3.toByte()
                "cycle" -> {
                    fixture.bytes[last] = 5
                    fixture.pointer(last + 1, fixture.script)
                }
                "budget" -> fixture.bytes.fill(0x69, last, last + 129)
                "runtime" -> {
                    fixture.pointer(last + 2, fixture.text + 32)
                    byteArrayOf(0xfd.toByte(), 2, 0xff.toByte()).copyInto(fixture.bytes, fixture.text + 32)
                }
                "condition" -> {
                    fixture.bytes[last] = 6
                    fixture.bytes[last + 1] = 6
                }
            }
            assertUnavailable(fixture)
        }
    }

    @Test
    fun comparisonsPreserveTheBufferedTextAndMayPrecedeBrailleTerminals() {
        val fixture = fixture(0)
        fixture.message(fixture.script + 1, fixture.text, 3)
        fixture.comparison(fixture.script + 7, 0x4000, 0xffff)
        fixture.bytes[fixture.script + 12] = 9
        fixture.bytes[fixture.script + 13] = 3
        assertEquals("AB", fixture.resolve().displayName)

        val mixed = fixture(0)
        mixed.bytes[mixed.branch] = 0x78
        mixed.pointer(mixed.branch + 1, mixed.otherText)
        mixed.bytes[mixed.branch + 5] = 0x6d
        byteArrayOf(0xfd.toByte(), 2, 0xfe.toByte(), 0xff.toByte(), 0xfc.toByte(), 19, 1, 5, 0xff.toByte())
            .copyInto(mixed.bytes, mixed.otherText)
        assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, mixed.resolve().textObligation)
    }

    @Test
    fun otherFamiliesDoNotBorrowTheEmeraldComparisonGrammar() {
        for (family in listOf(EngineFamily.RUBY_SAPPHIRE, EngineFamily.FIRERED_LEAFGREEN)) {
            val poi = fixture(0).resolve(family)
            assertEquals(LocalMapPoiTextObligation.UNRESOLVED, poi.textObligation)
            assertNull(poi.displayName)
        }
    }

    @Test(expected = ParserCancellationException::class)
    fun cancellationAfterDivergentHeadlinesStillPropagates() {
        val fixture = threeWayFixture()
        var reads = 0
        Gen3InvariantSignHeadlineResolver.resolve(
            RomImage(fixture.bytes), fixture.script,
            ParserCancellationToken { if (reads == 2) throw ParserCancellationException() },
        ) { text, _ ->
            reads++
            if (text == fixture.text) "AB" else "CD"
        }
    }

    @Test(expected = ParserCancellationException::class)
    fun cancellationInsideTheComparisonGraphIsNotAnOptionalMapFailure() {
        var checks = 0
        fixture(0).resolve(cancellation = ParserCancellationToken {
            if (++checks == 5) throw ParserCancellationException()
        })
    }

    private fun assertUnavailable(fixture: Fixture) {
        val poi = fixture.resolve()
        assertEquals("local/1/bg/0", poi.key)
        assertEquals(LocalMapPoiTextObligation.UNRESOLVED, poi.textObligation)
        assertNull(poi.displayName)
    }

    private fun threeWayFixture(): Fixture = fixture(0).apply {
        bytes[branch] = 6
        bytes[branch + 1] = 1
        pointer(branch + 2, branch + 32)
        message(branch + 6, text, 3)
        message(branch + 32, otherText, 3)
    }

    private fun fixture(origin: Int, standard: Int = 4): Fixture = Fixture(ByteArray(origin + 0x1000), origin).apply {
        pointer(header + 4, events)
        bytes[events + 3] = 1
        pointer(events + 16, background)
        bytes[background] = 2
        bytes[background + 2] = 3
        pointer(background + 8, script)
        bytes[script] = 0x69
        comparison(script + 1, 0x40ba, 2)
        bytes[script + 6] = 6
        bytes[script + 7] = 1
        pointer(script + 8, branch)
        message(script + 12, text, standard)
        message(branch, otherText, standard)
        byteArrayOf(0xbb.toByte(), 0xbc.toByte(), 0xff.toByte()).copyInto(bytes, text)
        byteArrayOf(0xbd.toByte(), 0xbe.toByte(), 0xff.toByte()).copyInto(bytes, otherText)
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

        fun comparison(at: Int, variable: Int, value: Int) {
            bytes[at] = 0x21
            bytes[at + 1] = variable.toByte()
            bytes[at + 2] = (variable ushr 8).toByte()
            bytes[at + 3] = value.toByte()
            bytes[at + 4] = (value ushr 8).toByte()
        }

        fun message(at: Int, target: Int, standard: Int) {
            bytes[at] = 15
            bytes[at + 1] = 0
            pointer(at + 2, target)
            bytes[at + 6] = 9
            bytes[at + 7] = standard.toByte()
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
