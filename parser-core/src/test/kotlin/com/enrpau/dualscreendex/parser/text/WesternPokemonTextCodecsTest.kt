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
    fun frenchGermanPlusIsALiteralGlyphNotASubstitution() {
        // Gen I French/German main-font charmaps declare "+" at $e4:
        // pokered-fr 7ddc547e22e4c84f3e492a3c04a92c26f7f2e877,
        // pokered-de f6a2c0cdc557e4c8c1886743629341848ff5bc40, constants/charmap.asm.
        for (codec in listOf(WesternPokemonTextCodecs.gen1French, WesternPokemonTextCodecs.gen1German,
            WesternPokemonTextCodecs.gen2French, WesternPokemonTextCodecs.gen2German)) {
            val decoded = codec.decodeDetailed(bytes(0x80, 0x7F, 0xE4, 0x50, 0x54))
            assertEquals(codec.id, "A +", decoded.text)
            assertTrue(codec.id, decoded.terminated)
            assertEquals(codec.id, 4, decoded.consumedBytes)
            assertEquals(codec.id, 2, decoded.glyphUnits)
            assertEquals(codec.id, 1, decoded.whitespaceUnits)
            assertEquals(codec.id, 0, decoded.substitutionUnits)
            assertEquals(codec.id, 0, decoded.controlUnits)
            assertEquals(codec.id, 0, decoded.invalidUnits)
        }
    }

    @Test
    fun plusCorrectionDoesNotChangeOtherE4DialectsOrRatifySubstitutions() {
        val english = WesternPokemonTextCodecs.gen1English.decodeDetailed(bytes(0xE4, 0x50))
        assertEquals("'r", english.text)
        assertEquals(1, english.substitutionUnits)
        assertEquals(0, english.glyphUnits)
        val genTwoEnglish = WesternPokemonTextCodecs.gen2English.decodeDetailed(bytes(0xE4, 0x50))
        assertEquals("", genTwoEnglish.text)
        assertEquals(1, genTwoEnglish.invalidUnits)
        for (codec in listOf(WesternPokemonTextCodecs.gen1Italian, WesternPokemonTextCodecs.gen1Spanish,
            WesternPokemonTextCodecs.gen2Italian, WesternPokemonTextCodecs.gen2Spanish)) {
            val decoded = codec.decodeDetailed(bytes(0xE4, 0x50))
            assertEquals(codec.id, "¿", decoded.text)
            assertEquals(codec.id, 1, decoded.glyphUnits)
            assertEquals(codec.id, 0, decoded.substitutionUnits)
        }
        for (codec in WesternPokemonTextCodecs.all.filter { it.applicableGenerations == setOf(3) }) {
            val decoded = codec.decodeDetailed(bytes(0xE4, 0xFF))
            assertEquals(codec.id, "p", decoded.text)
            assertEquals(codec.id, 1, decoded.glyphUnits)
            assertEquals(codec.id, 0, decoded.substitutionUnits)
        }
        for (codec in listOf(WesternPokemonTextCodecs.gen1French, WesternPokemonTextCodecs.gen1German)) {
            for (token in listOf(0xD4, 0xDF, 0x4A, 0x54, 0xE1, 0xE2)) {
                val decoded = codec.decodeDetailed(bytes(token, 0x50))
                assertEquals("${codec.id} raw=$token", 1, decoded.substitutionUnits)
                assertEquals("${codec.id} raw=$token", 0, decoded.glyphUnits)
            }
        }
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

    @Test
    fun staticLabelPolicyPreservesEveryGeneralCounterAndOnlyRatifiesWesternGenTwo54() {
        val codecs = WesternPokemonTextCodecs.all + JapanesePokemonTextCodecs.all +
            KoreanGen2PokemonTextCodec.codec + PokemonTextCodec.gbEnglish + PokemonTextCodec.gbaEnglish
        for (codec in codecs) {
            assertEquals(codec.id, 1, codec.version)
            for (value in 0..255) {
                val raw = bytes(value, codec.terminator)
                val decoded = codec.decodeDetailed(raw)
                val label = codec.decodeStaticLabel(com.enrpau.dualscreendex.parser.io.RomImage(raw), 0, raw.size,
                    com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken.NONE,
                    StaticLabelUse.GEN2_ORDINARY_ITEM_NAME)
                assertEquals("${codec.id} raw=$value", decoded, label.decoded)
                val ratified = codec in WesternPokemonTextCodecs.all &&
                    codec.applicableGenerations == setOf(2) && value == 0x54
                assertEquals("${codec.id} raw=$value", if (ratified) 0 else decoded.substitutionUnits,
                    label.unratifiedSubstitutionUnits)
            }
        }
        val label = WesternPokemonTextCodecs.gen2English.decodeStaticLabel(
            com.enrpau.dualscreendex.parser.io.RomImage(bytes(0x54, 0x7F, 0x80, 0x50)), 0, 4,
            com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken.NONE,
            StaticLabelUse.GEN2_ORDINARY_ITEM_NAME)
        assertEquals("POKé A", label.decoded.text)
        assertEquals(4, label.decoded.consumedBytes)
        assertEquals(3, label.decoded.validBytes)
        assertEquals(3, label.decoded.contentBytes)
        assertEquals(3, label.decoded.validUnits)
        assertEquals(3, label.decoded.contentUnits)
        assertEquals(1, label.decoded.glyphUnits)
        assertEquals(1, label.decoded.whitespaceUnits)
        assertEquals(1, label.decoded.substitutionUnits)
        assertEquals(0, label.decoded.controlUnits)
        assertEquals(0, label.decoded.invalidUnits)
        assertEquals(1.0, label.decoded.validRatio, 0.0)
        assertEquals(0, label.unratifiedSubstitutionUnits)
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
