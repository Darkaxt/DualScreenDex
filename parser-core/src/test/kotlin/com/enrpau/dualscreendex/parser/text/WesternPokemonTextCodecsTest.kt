package com.enrpau.dualscreendex.parser.text

import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WesternPokemonTextCodecsTest {
    @Test
    fun registersOneStableCodecIdentityPerWesternLanguageAndGeneration() {
        assertEquals(15, WesternPokemonTextCodecs.all.size)
        assertEquals(15, WesternPokemonTextCodecs.all.map { it.id to it.version }.toSet().size)

        for (generation in 1..3) {
            for (language in WESTERN_LANGUAGES) {
                val codec = WesternPokemonTextCodecs.forLanguage(language, generation)
                assertEquals(language, codec?.language)
                assertEquals(setOf(generation), codec?.applicableGenerations)
                assertEquals(1, codec?.version)
            }
        }

        assertNull(WesternPokemonTextCodecs.forLanguage(LanguageTag.JAPANESE, 1))
        assertNull(WesternPokemonTextCodecs.forLanguage(LanguageTag.ENGLISH, 4))
    }

    @Test
    fun decodesDistinctGenOneWesternOverlays() {
        assertDecodes(
            "é'd'r'm",
            WesternPokemonTextCodecs.gen1English,
            0xBA, 0xBB, 0xE4, 0xE5, 0x50,
        )
        assertDecodes(
            "àßîc'y'",
            WesternPokemonTextCodecs.gen1French,
            0xBA, 0xBE, 0xCC, 0xD4, 0xDF, 0x50,
        )
        assertDecodes(
            "ÀÈÑñó¿¡",
            WesternPokemonTextCodecs.gen1Italian,
            0xBE, 0xC6, 0xCA, 0xD2, 0xD4, 0xE4, 0xE5, 0x50,
        )
        assertDecodes(
            "ÍÑÓíñ",
            WesternPokemonTextCodecs.gen1Spanish,
            0xC9, 0xCA, 0xCC, 0xD1, 0xD2, 0x50,
        )
    }

    @Test
    fun decodesDistinctGenTwoWesternOverlaysWithoutReusingGenOneSemantics() {
        assertDecodes(
            "Ä'd'v←é",
            WesternPokemonTextCodecs.gen2English,
            0xC0, 0xD0, 0xD6, 0xDF, 0xEA, 0x50,
        )
        assertDecodes(
            "àîc'y'é",
            WesternPokemonTextCodecs.gen2German,
            0xBA, 0xCC, 0xD4, 0xDF, 0xEA, 0x50,
        )
        assertDecodes(
            "ÀÈÍÑó¿¡é",
            WesternPokemonTextCodecs.gen2Italian,
            0xBE, 0xC6, 0xC9, 0xCA, 0xD4, 0xE4, 0xE5, 0xEA, 0x50,
        )
        assertDecodes("'r", WesternPokemonTextCodecs.gen1English, 0xE4, 0x50)
        assertDecodes("", WesternPokemonTextCodecs.gen2English, 0xE4, 0x50)
    }

    @Test
    fun decodesTheCommonGenThreeCoreAndLocaleSpecificQuotes() {
        assertDecodes(
            "ÍíñºªÄö",
            WesternPokemonTextCodecs.gen3Spanish,
            0x5A, 0x6F, 0x29, 0x2A, 0x2B, 0xF1, 0xF5, 0xFF,
        )
        assertDecodes("“”", WesternPokemonTextCodecs.gen3English, 0xB1, 0xB2, 0xFF)
        assertDecodes("«»", WesternPokemonTextCodecs.gen3French, 0xB1, 0xB2, 0xFF)
        assertDecodes("„“", WesternPokemonTextCodecs.gen3German, 0xB1, 0xB2, 0xFF)
    }

    @Test
    fun classifiesWesternControlsSubstitutionsInvalidBytesAndTermination() {
        val genOne = WesternPokemonTextCodecs.gen1French.decodeDetailed(
            bytes(0x8F, 0x54, 0x4E, 0x80, 0x50, 0x81),
        )
        assertEquals("PPOKé A", genOne.text)
        assertTrue(genOne.terminated)
        assertEquals(1, genOne.substitutionUnits)
        assertEquals(1, genOne.controlUnits)
        assertEquals(5, genOne.consumedBytes)

        val genThree = WesternPokemonTextCodecs.gen3English.decodeDetailed(
            bytes(0xBB, 0xFC, 0x0C, 0xFD, 0x01, 0xBC, 0x0A, 0xFF),
        )
        assertEquals("A B", genThree.text)
        assertTrue(genThree.terminated)
        assertEquals(2, genThree.controlUnits)
        assertEquals(1, genThree.invalidUnits)
        assertEquals(8, genThree.consumedBytes)
    }

    @Test
    fun keepsInvalidAndTruncatedEscapeSequencesBounded() {
        val invalid = WesternPokemonTextCodecs.gen3English.decodeDetailed(bytes(0x0A, 0xFF))
        assertEquals("", invalid.text)
        assertEquals(1, invalid.invalidUnits)
        assertTrue(invalid.terminated)

        val truncated = WesternPokemonTextCodecs.gen3English.decodeDetailed(bytes(0xFC))
        assertEquals("", truncated.text)
        assertEquals(1, truncated.invalidUnits)
        assertEquals(1, truncated.consumedBytes)
        assertFalse(truncated.terminated)
    }

    @Test
    fun declaresExactPlatformApplicability() {
        assertTrue(WesternPokemonTextCodecs.gen1English.supports(1, Platform.GB))
        assertTrue(WesternPokemonTextCodecs.gen1English.supports(1, Platform.GBC))
        assertFalse(WesternPokemonTextCodecs.gen1English.supports(2, Platform.GBC))
        assertTrue(WesternPokemonTextCodecs.gen2French.supports(2, Platform.GBC))
        assertFalse(WesternPokemonTextCodecs.gen2French.supports(2, Platform.GBA))
        assertTrue(WesternPokemonTextCodecs.gen3German.supports(3, Platform.GBA))
        assertFalse(WesternPokemonTextCodecs.gen3German.supports(3, Platform.GBC))
    }

    private fun assertDecodes(
        expected: String,
        codec: PokemonTextCodec,
        vararg values: Int,
    ) {
        assertEquals(expected, codec.decode(bytes(*values)))
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { index ->
        values[index].toByte()
    }

    private companion object {
        val WESTERN_LANGUAGES = listOf(
            LanguageTag.ENGLISH,
            LanguageTag.FRENCH,
            LanguageTag.GERMAN,
            LanguageTag.ITALIAN,
            LanguageTag.SPANISH,
        )
    }
}
