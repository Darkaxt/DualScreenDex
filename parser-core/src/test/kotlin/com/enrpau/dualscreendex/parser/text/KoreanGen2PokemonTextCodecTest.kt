package com.enrpau.dualscreendex.parser.text

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KoreanGen2PokemonTextCodecTest {
    @Test
    fun declaresStableKoreanGenerationTwoIdentity() {
        val codec = KoreanGen2PokemonTextCodec.codec

        assertEquals("gb-gen2-ko", codec.id)
        assertEquals(1, codec.version)
        assertEquals(LanguageTag.KOREAN, codec.language)
        assertEquals(setOf(2), codec.applicableGenerations)
        assertTrue(codec.supports(2, Platform.GBC))
        assertFalse(codec.supports(2, Platform.GB))
    }

    @Test
    fun decodesSourceBackedJamoAndHangulAdditions() {
        assertDecodes(
            "ㄱ겹겸괻읆뢔쌰쎼쓔쬬",
            0x0B, 0x00,
            0x01, 0x42,
            0x01, 0x43,
            0x01, 0x7F,
            0x07, 0x8C,
            0x03, 0xD0,
            0x06, 0x2F,
            0x06, 0x30,
            0x06, 0xA0,
            0x08, 0x30,
            0x50,
        )
    }

    @Test
    fun keepsNullControlDistinctFromTableElevenLead() {
        val decoded = KoreanGen2PokemonTextCodec.codec.decodeDetailed(
            bytes(0x00, 0x0B, 0x00, 0x50),
        )

        assertEquals("ㄱ", decoded.text)
        assertTrue(decoded.terminated)
        assertEquals(1, decoded.controlUnits)
        assertEquals(1, decoded.glyphUnits)
        assertEquals(4, decoded.consumedBytes)
    }

    @Test
    fun decodesPairAndSingleByteWhitespaceWithoutConfusingTrailWithTerminator() {
        val decoded = KoreanGen2PokemonTextCodec.codec.decodeDetailed(
            bytes(0x01, 0x01, 0x0B, 0xFF, 0x01, 0x50, 0x7F, 0x01, 0x02, 0x50),
        )

        assertEquals("가 각", decoded.text)
        assertTrue(decoded.terminated)
        assertEquals(2, decoded.glyphUnits)
        assertEquals(2, decoded.whitespaceUnits)
        assertEquals(1, decoded.invalidUnits)
        assertEquals(10, decoded.consumedBytes)
    }

    @Test
    fun decodesNativeStaticSubstitutionsAndKeepsRuntimeControlsDistinct() {
        val decoded = KoreanGen2PokemonTextCodec.codec.decodeDetailed(
            bytes(0x1F, 0x7F, 0x47, 0x7F, 0x49, 0x4A, 0x4B, 0x4D, 0x4E, 0x55, 0x5B, 0x5A, 0x50),
        )

        assertEquals("こうげき 포켓몬 컴퓨터기술머신로켓단레드그린트레이너어머니", decoded.text)
        assertTrue(decoded.terminated)
        assertEquals(9, decoded.substitutionUnits)
        assertEquals(1, decoded.controlUnits)
        assertEquals(2, decoded.whitespaceUnits)
    }

    @Test
    fun decodesCanonicalSingleByteGlyphsAfterTheLeadRange() {
        assertDecodes(
            "POKéPOKéAzÄ'd!?0",
            0x33, 0x70, 0x71, 0x80, 0xB9, 0xC0, 0xD0, 0xE7, 0xE6, 0xF6, 0x50,
        )
    }

    @Test
    fun rejectsUnmappedAndTruncatedPairsWithoutCrossingTheByteWindow() {
        val unmapped = KoreanGen2PokemonTextCodec.codec.decodeDetailed(
            bytes(0x01, 0x50, 0x50),
        )
        assertEquals("", unmapped.text)
        assertTrue(unmapped.terminated)
        assertEquals(1, unmapped.invalidUnits)
        assertEquals(3, unmapped.consumedBytes)

        val truncated = KoreanGen2PokemonTextCodec.codec.decodeDetailed(
            rom = RomImage(bytes(0x01, 0x42)),
            offset = 0,
            maximumBytes = 1,
            cancellation = ParserCancellationToken.NONE,
        )
        assertEquals("", truncated.text)
        assertFalse(truncated.terminated)
        assertEquals(1, truncated.invalidUnits)
        assertEquals(1, truncated.consumedBytes)
    }

    private fun assertDecodes(expected: String, vararg values: Int) {
        assertEquals(expected, KoreanGen2PokemonTextCodec.codec.decode(bytes(*values)))
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { index ->
        values[index].toByte()
    }
}
