package com.enrpau.dualscreendex.parser.text

import com.enrpau.dualscreendex.parser.language.LanguageRegistry
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen2PlainNameCodecTest {
    @Test fun decodesUppercaseLowercaseAndRemappedDigits() {
        val decoded = codec.decodeDetailed(bytes("80 99 a0 b9 e0 e9 53 50"))
        assertEquals("AZaz09", decoded.text)
        assertTrue(decoded.terminated)
        assertEquals(7, decoded.consumedBytes)
        assertEquals(0, decoded.invalidUnits)
    }

    @Test fun decodesNamePunctuationAndGenderGlyphs() {
        val decoded = codec.decodeDetailed(bytes("9a 9b 9c 9d 9e 9f 7f bc bd be bf c0 c8 53"))
        assertEquals("().,?! -:♂♀'é", decoded.text)
        assertTrue(decoded.terminated)
        assertEquals(0, decoded.invalidUnits)
    }

    @Test fun decodesSourceRatifiedPlainNameContractions() {
        val decoded = codec.decodeDetailed(bytes("85 a0 b1 a5 a4 b3 a2 a7 c1 53"))
        assertEquals("Farfetch'd", decoded.text)
        assertTrue(decoded.terminated)
        assertEquals(0, decoded.substitutionUnits)
        assertEquals(0, decoded.controlUnits)
        assertEquals(0, decoded.invalidUnits)
    }

    @Test fun rejectsRetailTerminatorAndUnratifiedProseTokens() {
        for (value in listOf(0x50, 0x54, 0x60, 0x70, 0xCB)) {
            val decoded = codec.decodeDetailed(byteArrayOf(0x80.toByte(), value.toByte(), 0x53))
            assertEquals("unsupported plain-name byte $value", 1, decoded.invalidUnits)
            assertEquals(0, decoded.controlUnits)
            assertEquals(0, decoded.substitutionUnits)
        }
        assertFalse(codec.decodeDetailed(bytes("80 50")).terminated)
    }

    @Test fun remainsScopedToGenerationTwoGbPlatforms() {
        assertTrue(codec.supports(2, Platform.GB))
        assertTrue(codec.supports(2, Platform.GBC))
        assertFalse(codec.supports(1, Platform.GB))
        assertFalse(codec.supports(3, Platform.GBA))
        assertFalse(codec.supports(2, Platform.UNKNOWN))
        assertEquals("gb-gen2-en-53-plain", codec.id)
        assertEquals(1, codec.version)
    }

    @Test fun registersByIdentityWithoutChangingOfficialEnglishCandidates() {
        assertSame(codec, LanguageRegistry.codec("gb-gen2-en-53-plain", 1))
        assertSame(Gen2PlainNameCodec.english53Ngrams, LanguageRegistry.codec("gb-gen2-en-53-ngram", 1))
        assertSame(
            WesternPokemonTextCodecs.gen2English,
            LanguageRegistry.candidateCodec(LanguageTag.ENGLISH, 2, Platform.GBC, EngineFamily.CRYSTAL),
        )
        assertFalse(codec in LanguageRegistry.candidateCodecs(2, Platform.GBC))
        assertSame(WesternPokemonTextCodecs.gen2English, WesternPokemonTextCodecs.forLanguage(LanguageTag.ENGLISH, 2))
    }

    private val codec get() = Gen2PlainNameCodec.english53

    private fun bytes(value: String): ByteArray =
        value.split(' ').map { it.toInt(16).toByte() }.toByteArray()
}
