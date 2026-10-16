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

class Gen3SetterPoiHeadlineTest {
    @Test
    fun specialSettersBeforeExplicitMessagesPreserveClosedBranchClassification() {
        for (origin in listOf(0, 0x2000, 0x4000)) for (standard in 3..5) {
            val poi = fixture(origin, standard).resolve()
            assertEquals("local/1/bg/0", poi.key)
            assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, poi.textObligation)
            assertNull(poi.displayName)
            assertEquals(emptyMap<Int, String>(), poi.displayNamesByTrainerGender)
        }
    }

    @Test
    fun equalNormalizedFirstLinesRemainStaticWithoutSelectingVariableState() {
        val fixture = fixture()
        byteArrayOf(0, 0xbb.toByte(), 0xbc.toByte(), 0xfe.toByte(), 0xfd.toByte(), 2, 0xff.toByte())
            .copyInto(fixture.bytes, fixture.otherText)
        val poi = fixture.resolve()
        assertEquals(LocalMapPoiTextObligation.DIRECT_TEXT, poi.textObligation)
        assertEquals("AB", poi.displayName)
    }

    @Test
    fun boundedSpecialNamespaceAndLiteralOperandsUseTheSameClosedGraph() {
        for (variable in listOf(0x8000, 0x8008, 0x8009, 0x800f)) {
            for (value in listOf(0, 7, 85, 0x3fff, 0x4100, 0xffff)) {
                val fixture = fixture()
                fixture.setter(fixture.script + 12, variable, value)
                assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, fixture.resolve().textObligation)
            }
        }
    }

    @Test
    fun otherVariableNamespacesAndExcludedOperandsRemainUnavailable() {
        for (variable in listOf(0, 0x3fff, 0x4000, 0x40ff, 0x7fff, 0x8010, 0xffff)) {
            val fixture = fixture()
            fixture.setter(fixture.script + 12, variable, 7)
            assertUnavailable(fixture)
        }
        for (value in listOf(0x4000, 0x40ba, 0x40ff)) {
            val fixture = fixture()
            fixture.setter(fixture.script + 12, 0x8008, value)
            assertUnavailable(fixture)
        }
    }

    @Test
    fun truncatedSettersAndOpaqueOperationsInvalidateTheGraph() {
        for (remaining in 1..4) {
            val fixture = fixture()
            val target = fixture.bytes.size - remaining
            fixture.bytes[fixture.branch] = 5
            fixture.pointer(fixture.branch + 1, target)
            byteArrayOf(0x16, 8, 0x80.toByte(), 7, 0).copyInto(fixture.bytes, target, 0, remaining)
            assertUnavailable(fixture)
        }
        for (opcode in listOf(0x25, 0x60, 0x83, 0x97, 0xc3)) {
            val fixture = fixture()
            fixture.bytes[fixture.script + 12] = opcode.toByte()
            assertUnavailable(fixture)
        }
    }

    @Test
    fun divergentHeadlinesCannotHideALaterInvalidSetterPath() {
        for (damage in listOf("variable", "operand", "opaque", "cycle", "budget", "runtime", "condition")) {
            val fixture = threeWayFixture()
            val last = fixture.script + 12
            when (damage) {
                "variable" -> fixture.setter(last, 0x8010, 7)
                "operand" -> fixture.setter(last, 0x8008, 0x4000)
                "opaque" -> fixture.bytes[last] = 0xc3.toByte()
                "cycle" -> {
                    fixture.bytes[last] = 5
                    fixture.pointer(last + 1, fixture.script)
                }
                "budget" -> fixture.bytes.fill(0x69, last, last + 129)
                "runtime" -> {
                    fixture.message(last, fixture.text + 32, 3)
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
    fun settersConservativelyRequireAFreshExplicitMessagePointer() {
        val fixture = fixture()
        fixture.message(fixture.script + 12, fixture.text, 3)
        fixture.setter(fixture.script + 18, 0x8008, 7)
        fixture.bytes[fixture.script + 23] = 9
        fixture.bytes[fixture.script + 24] = 3
        assertUnavailable(fixture)
    }

    @Test
    fun settersMayPrecedeAnExplicitBrailleMessage() {
        val fixture = fixture()
        fixture.setter(fixture.branch, 0x8009, 1)
        fixture.bytes[fixture.branch + 5] = 0x78
        fixture.pointer(fixture.branch + 6, fixture.otherText)
        fixture.bytes[fixture.branch + 10] = 0x6d
        byteArrayOf(0xfd.toByte(), 2, 0xfe.toByte(), 0xff.toByte(), 0xfc.toByte(), 19, 1, 5, 0xff.toByte())
            .copyInto(fixture.bytes, fixture.otherText)
        assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, fixture.resolve().textObligation)
    }

    @Test
    fun otherFamiliesDoNotBorrowTheEmeraldSetterGraph() {
        for (family in listOf(EngineFamily.RUBY_SAPPHIRE, EngineFamily.FIRERED_LEAFGREEN)) {
            val poi = fixture().resolve(family)
            assertEquals(LocalMapPoiTextObligation.UNRESOLVED, poi.textObligation)
            assertNull(poi.displayName)
        }
    }

    @Test(expected = ParserCancellationException::class)
    fun cancellationAfterDivergentSetterHeadlinesStillPropagates() {
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
    fun cancellationInsideTheSetterGraphIsNotAnOptionalMapFailure() {
        var checks = 0
        fixture().resolve(cancellation = ParserCancellationToken {
            if (++checks == 6) throw ParserCancellationException()
        })
    }

    private fun assertUnavailable(fixture: Fixture) {
        val poi = fixture.resolve()
        assertEquals("local/1/bg/0", poi.key)
        assertEquals(LocalMapPoiTextObligation.UNRESOLVED, poi.textObligation)
        assertNull(poi.displayName)
    }

    private fun threeWayFixture(): Fixture = fixture().apply {
        bytes[branch] = 6
        bytes[branch + 1] = 1
        pointer(branch + 2, branch + 32)
        message(branch + 6, text, 3)
        message(branch + 32, otherText, 3)
    }

    private fun fixture(origin: Int = 0, standard: Int = 4): Fixture = Fixture(ByteArray(origin + 0x1000), origin).apply {
        pointer(header + 4, events)
        bytes[events + 3] = 1
        pointer(events + 16, background)
        bytes[background] = 2
        bytes[background + 2] = 3
        pointer(background + 8, script)
        bytes[script] = 0x69
        bytes[script + 1] = 0x21
        bytes[script + 2] = 0x85.toByte()
        bytes[script + 3] = 0x40
        bytes[script + 4] = 6
        bytes[script + 6] = 6
        bytes[script + 7] = 1
        pointer(script + 8, branch)
        setter(script + 12, 0x8008, 7)
        setter(script + 17, 0x8009, 85)
        message(script + 22, text, standard)
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

        fun setter(at: Int, variable: Int, value: Int) {
            bytes[at] = 0x16
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
