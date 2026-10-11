package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiTextObligation
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.WesternPokemonTextCodecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen3BraillePoiTextTest {
    @Test
    fun relocatedBrailleSkipsOpaqueWindowHeaderAndPreservesNativeCells() {
        val names = listOf(0, 0x2000).flatMap { origin ->
            listOf(WesternPokemonTextCodecs.gen3English, JapanesePokemonTextCodecs.gen3Later).map { codec ->
                val fixture = fixture(origin)
                fixture.text(byteArrayOf(0x01, 0x05, 0x03, 0xff.toByte()))
                val poi = fixture.resolve(codec)
                assertEquals(LocalMapPoiTextObligation.DIRECT_TEXT, poi.textObligation)
                poi.displayName
            }
        }
        assertEquals(List(4) { "⠁⠃⠉" }, names)
    }

    @Test
    fun singleBrailleCellIsAValidNativeHeadline() {
        val names = listOf(0, 0x2000).map { origin ->
            fixture(origin).apply { text(byteArrayOf(0x39, 0xff.toByte())) }.resolve().displayName
        }
        assertEquals(listOf("⠵", "⠵"), names)
    }

    @Test
    fun firstLineRuntimeMarkersStayContextualButLaterLinesDoNotEraseCells() {
        for (origin in listOf(0, 0x2000)) {
            val contextual = fixture(origin).apply {
                text(byteArrayOf(0xfd.toByte(), 0x02, 0x01, 0xff.toByte()))
            }.resolve()
            assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, contextual.textObligation)
            assertNull(contextual.displayName)
            val later = fixture(origin).apply {
                text(byteArrayOf(0x01, 0x05, 0xfe.toByte(), 0xfd.toByte(), 0x02, 0xff.toByte()))
            }.resolve()
            assertEquals(LocalMapPoiTextObligation.DIRECT_TEXT, later.textObligation)
            assertEquals("⠁⠃", later.displayName)
        }
    }

    @Test
    fun incompleteWindowHeadersNeverBecomeTextOrRemovePoiIdentity() {
        for (remaining in 1..6) {
            val fixture = fixture(0)
            val target = fixture.bytes.size - remaining
            fixture.pointer(fixture.script + 2, target)
            fixture.bytes.fill(0x01, target)
            val poi = fixture.resolve()
            assertEquals("local/1/bg/0", poi.key)
            assertEquals(LocalMapPoiTextObligation.DIRECT_TEXT, poi.textObligation)
            assertNull(poi.displayName)
        }
    }

    @Test
    fun invalidCellsAndOpaqueFontControlsNeverPublishPartialHeadlines() {
        for (tail in listOf(
            byteArrayOf(0x40, 0xff.toByte()),
            byteArrayOf(0xbb.toByte(), 0xff.toByte()),
            byteArrayOf(0xfc.toByte(), 0x06, 0x01, 0xff.toByte()),
            byteArrayOf(0xfd.toByte(), 0x07, 0xff.toByte()),
        )) {
            val poi = fixture(0).apply { text(byteArrayOf(0x01, 0x05) + tail) }.resolve()
            assertEquals(LocalMapPoiTextObligation.DIRECT_TEXT, poi.textObligation)
            assertNull(poi.displayName)
        }
    }

    @Test
    fun brailleHonorsFirstHeadlineByteBudgetAndRequiresTermination() {
        val bounded = fixture(0).apply { text(ByteArray(159) { 0x01 } + byteArrayOf(0xff.toByte())) }.resolve()
        assertEquals("⠁".repeat(159), bounded.displayName)
        val outside = fixture(0).apply { text(ByteArray(160) { 0x01 } + byteArrayOf(0xff.toByte())) }.resolve()
        assertNull(outside.displayName)
        val blank = fixture(0).apply { text(byteArrayOf(0x00, 0x00, 0xff.toByte())) }.resolve()
        assertNull(blank.displayName)
    }

    @Test
    fun normalMessageKeepsItsAlphabetAndDoesNotSkipSixBytes() {
        val fixture = fixture(0)
        fixture.bytes[fixture.script + 1] = 0x67
        fixture.bytes[fixture.script + 6] = 0x66
        byteArrayOf(0x01, 0x02, 0xff.toByte()).copyInto(fixture.bytes, fixture.text)
        assertEquals("あい", fixture.resolve(JapanesePokemonTextCodecs.gen3Later).displayName)
    }

    @Test
    fun clearToSpacingControlConsumesItsParameterEvenWhenItLooksLikeAToken() {
        val names = listOf(0, 0x2000).flatMap { origin ->
            listOf(0x7f, 0xff, 0xfd).map { parameter ->
                val fixture = fixture(origin)
                fixture.bytes[fixture.script + 1] = 0x67
                fixture.bytes[fixture.script + 6] = 0x66
                byteArrayOf(0xfc.toByte(), 0x13, parameter.toByte(), 0xbb.toByte(), 0xbc.toByte(),
                    0xfc.toByte(), 0x13, parameter.toByte(), 0xbd.toByte(), 0xff.toByte())
                    .copyInto(fixture.bytes, fixture.text)
                fixture.resolve().displayName
            }
        }
        assertEquals(List(6) { "AB C" }, names)
    }

    @Test
    fun truncatedClearToControlRemainsUnavailable() {
        val fixture = fixture(0)
        fixture.bytes[fixture.script + 1] = 0x67
        fixture.bytes[fixture.script + 6] = 0x66
        val tail = fixture.bytes.size - 2
        fixture.pointer(fixture.script + 2, tail)
        byteArrayOf(0xfc.toByte(), 0x13).copyInto(fixture.bytes, tail)
        val poi = fixture.resolve()
        assertEquals(LocalMapPoiTextObligation.DIRECT_TEXT, poi.textObligation)
        assertNull(poi.displayName)
    }

    @Test
    fun allSixDotPatternsPreserveRowsAndColumns() {
        for (unicodeDots in 1..63) {
            var nativeCell = 0
            for (column in 0..1) for (row in 0..2) {
                if (unicodeDots and (1 shl (column * 3 + row)) != 0) {
                    nativeCell = nativeCell or (1 shl (row * 2 + column))
                }
            }
            val poi = fixture(0x2000).apply {
                text(byteArrayOf(nativeCell.toByte(), 0xff.toByte()))
            }.resolve()
            assertEquals((0x2800 + unicodeDots).toChar().toString(), poi.displayName)
        }
        val spaced = fixture(0).apply {
            text(byteArrayOf(0x01, 0x00, 0x05, 0xff.toByte()))
        }.resolve()
        assertEquals("⠁ ⠃", spaced.displayName)
    }

    @Test
    fun unqualifiedFamiliesDoNotBorrowEmeraldBrailleTransport() {
        for (family in listOf(EngineFamily.RUBY_SAPPHIRE, EngineFamily.FIRERED_LEAFGREEN)) {
            val fixture = fixture(0).apply { text(byteArrayOf(0x01, 0x05, 0xff.toByte())) }
            val poi = Gen3LocalMapPoiResolver.resolve(
                RomImage(fixture.bytes), mapOf(1 to fixture.header),
                listOf(LocalMap("local/1", null, 1, 160, 160, 10, 10, "asset/1")),
                family, WesternPokemonTextCodecs.gen3English,
            ).also { assertEquals(emptyList<String>(), it.skippedReasons) }.pois.single()
            assertEquals("local/1/bg/0", poi.key)
            assertEquals(LocalMapPoiTextObligation.DIRECT_TEXT, poi.textObligation)
            assertNull(poi.displayName)
        }
    }

    private fun fixture(origin: Int): Fixture {
        val fixture = Fixture(ByteArray(origin + 0x1000), origin)
        fixture.pointer(fixture.header + 4, fixture.events)
        fixture.bytes[fixture.events + 3] = 1
        fixture.pointer(fixture.events + 0x10, fixture.background)
        fixture.bytes[fixture.background] = 2
        fixture.bytes[fixture.background + 2] = 3
        fixture.pointer(fixture.background + 8, fixture.script)
        fixture.bytes[fixture.script] = 0x69
        fixture.bytes[fixture.script + 1] = 0x78
        fixture.pointer(fixture.script + 2, fixture.text)
        fixture.bytes[fixture.script + 6] = 0x6d
        return fixture
    }

    private class Fixture(val bytes: ByteArray, val origin: Int) {
        val header = origin + 0x100
        val events = origin + 0x200
        val background = origin + 0x400
        val script = origin + 0x500
        val text = origin + 0x700

        fun pointer(at: Int, target: Int) {
            val value = 0x08000000L + target
            repeat(4) { index -> bytes[at + index] = (value ushr (index * 8)).toByte() }
        }

        fun text(body: ByteArray) {
            // Six transport bytes are not glyphs, substitutions or terminators.
            byteArrayOf(0xfa.toByte(), 0xfd.toByte(), 0xff.toByte(), 0xfc.toByte(), 7, 9).copyInto(bytes, text)
            body.copyInto(bytes, text + 6)
        }

        fun resolve(codec: PokemonTextCodec = WesternPokemonTextCodecs.gen3English) =
            Gen3LocalMapPoiResolver.resolve(
                RomImage(bytes), mapOf(1 to header),
                listOf(LocalMap("local/1", null, 1, 160, 160, 10, 10, "asset/1")),
                EngineFamily.EMERALD, codec,
            ).also { assertEquals(emptyList<String>(), it.skippedReasons) }.pois.single()
    }
}
