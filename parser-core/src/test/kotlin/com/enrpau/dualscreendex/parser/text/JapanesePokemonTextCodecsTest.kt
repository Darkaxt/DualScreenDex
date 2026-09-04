package com.enrpau.dualscreendex.parser.text

import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JapanesePokemonTextCodecsTest {
    @Test
    fun registersStableCodecIdentitiesForEachJapaneseDialect() {
        assertEquals(5, JapanesePokemonTextCodecs.all.size)
        assertEquals(5, JapanesePokemonTextCodecs.all.map { it.id to it.version }.toSet().size)

        JapanesePokemonTextCodecs.all.forEach { codec ->
            assertEquals(LanguageTag.JAPANESE, codec.language)
            assertEquals(1, codec.version)
        }
        assertEquals(setOf(1), JapanesePokemonTextCodecs.gen1RedBlue.applicableGenerations)
        assertEquals(setOf(1), JapanesePokemonTextCodecs.gen1Yellow.applicableGenerations)
        assertEquals(setOf(2), JapanesePokemonTextCodecs.gen2.applicableGenerations)
        assertEquals(setOf(3), JapanesePokemonTextCodecs.gen3RubySapphire.applicableGenerations)
        assertEquals(setOf(3), JapanesePokemonTextCodecs.gen3Later.applicableGenerations)
        assertEquals("gb-gen1-ja-red-blue", JapanesePokemonTextCodecs.gen1RedBlue.id)
        assertEquals("gb-gen1-ja-yellow", JapanesePokemonTextCodecs.gen1Yellow.id)
        assertEquals("gba-gen3-ja-ruby-sapphire", JapanesePokemonTextCodecs.gen3RubySapphire.id)
        assertEquals("gba-gen3-ja-emerald-frlg", JapanesePokemonTextCodecs.gen3Later.id)
    }

    @Test
    fun decodesRedBlueKanaPunctuationDigitsAndSubstitutions() {
        assertDecodes(
            "アガあがァ0",
            JapanesePokemonTextCodecs.gen1RedBlue,
            0x80, 0x05, 0xB1, 0x26, 0xE9, 0xF6, 0x50,
        )

        val decoded = JapanesePokemonTextCodecs.gen1RedBlue.decodeDetailed(
            bytes(0x80, 0x54, 0x4E, 0x5C, 0xB1, 0x50, 0x81),
        )
        assertEquals("アポケモン わざマシンあ", decoded.text)
        assertTrue(decoded.terminated)
        assertEquals(2, decoded.substitutionUnits)
        assertEquals(1, decoded.controlUnits)
        assertEquals(6, decoded.consumedBytes)
        assertDecodes("ロケットだん", JapanesePokemonTextCodecs.gen1RedBlue, 0x5E, 0x50)
        assertDecodes("⠄/,0", JapanesePokemonTextCodecs.gen1RedBlue, 0xF2, 0xF3, 0xF4, 0xF6, 0x50)
    }

    @Test
    fun decodesYellowSpecificSubstitutionPunctuationKanaAndFullWidthDigits() {
        assertDecodes(
            "が ゜゛．／ォ０",
            JapanesePokemonTextCodecs.gen1Yellow,
            0x4A, 0xE4, 0xE5, 0xF2, 0xF3, 0xF4, 0xF6, 0x50,
        )
    }

    @Test
    fun preservesYellowPunctuationWithoutChangingRedBlue() {
        assertDecodes("·⋯⋯⋯", JapanesePokemonTextCodecs.gen1Yellow, 0x74, 0x75, 0x56, 0x50)
        assertDecodes("・………", JapanesePokemonTextCodecs.gen1RedBlue, 0x74, 0x75, 0x56, 0x50)
    }

    @Test
    fun decodesGenerationTwoKanaDictionaryTokensAndFullWidthDigits() {
        assertDecodes(
            "ガがアあ０",
            JapanesePokemonTextCodecs.gen2,
            0x05, 0x26, 0x80, 0xB1, 0xF6, 0x50,
        )

        val decoded = JapanesePokemonTextCodecs.gen2.decodeDetailed(
            bytes(0x23, 0x7F, 0x35, 0x50),
        )
        assertEquals("こうげき ばん どうろ", decoded.text)
        assertTrue(decoded.terminated)
        assertEquals(2, decoded.substitutionUnits)
        assertEquals(1, decoded.whitespaceUnits)
        assertDecodes(
            "ここは ア⋯⋯",
            JapanesePokemonTextCodecs.gen2,
            0x37, 0x80, 0x56, 0x50,
        )
    }

    @Test
    fun decodesGenerationThreeHiraganaKatakanaAndPunctuation() {
        assertDecodes(
            "あがアガ0！？。ー·⋯",
            JapanesePokemonTextCodecs.gen3Later,
            0x01, 0x37, 0x51, 0x87, 0xA1, 0xAB, 0xAC, 0xAD, 0xAE, 0xAF, 0xB0, 0xFF,
        )
        assertTrue(JapanesePokemonTextCodecs.gen3Later.supports(3, Platform.GBA))
        assertFalse(JapanesePokemonTextCodecs.gen3Later.supports(3, Platform.GBC))
    }

    @Test
    fun preservesSourceMiddleDotInBothGenerationThreeDialects() {
        for (codec in listOf(JapanesePokemonTextCodecs.gen3RubySapphire, JapanesePokemonTextCodecs.gen3Later)) {
            assertDecodes("·", codec, 0xAF, 0xFF)
        }
    }

    @Test
    fun distinguishesRubySapphireGlyphsFromLaterGenerationThreeControls() {
        val rubySapphire = JapanesePokemonTextCodecs.gen3RubySapphire.decodeDetailed(
            bytes(0xF7, 0xF8, 0xF9, 0xFF),
        )
        assertEquals("↑↓←", rubySapphire.text)
        assertTrue(rubySapphire.terminated)
        assertEquals(3, rubySapphire.glyphUnits)
        assertEquals(4, rubySapphire.consumedBytes)

        val later = JapanesePokemonTextCodecs.gen3Later.decodeDetailed(
            bytes(0xF7, 0x01, 0xF8, 0x02, 0xF9, 0x03, 0xFF),
        )
        assertEquals("", later.text)
        assertTrue(later.terminated)
        assertEquals(3, later.controlUnits)
        assertEquals(7, later.consumedBytes)
    }

    @Test
    fun appliesDialectSpecificExtendedControlArityWithinTheByteWindow() {
        val rubySapphire = JapanesePokemonTextCodecs.gen3RubySapphire.decodeDetailed(
            bytes(0xFC, 0x11, 0x80, 0xFF),
        )
        assertEquals("", rubySapphire.text)
        assertTrue(rubySapphire.terminated)
        assertEquals(1, rubySapphire.controlUnits)
        assertEquals(4, rubySapphire.consumedBytes)

        val truncatedRubySapphire = JapanesePokemonTextCodecs.gen3RubySapphire.decodeDetailed(
            bytes(0xFC, 0x11),
        )
        assertEquals("", truncatedRubySapphire.text)
        assertFalse(truncatedRubySapphire.terminated)
        assertEquals(1, truncatedRubySapphire.invalidUnits)
        assertEquals(2, truncatedRubySapphire.consumedBytes)

        val later = JapanesePokemonTextCodecs.gen3Later.decodeDetailed(
            bytes(0xFC, 0x11, 0x80, 0xFF),
        )
        assertEquals("", later.text)
        assertTrue(later.terminated)
        assertEquals(1, later.controlUnits)
        assertEquals(4, later.consumedBytes)
    }

    @Test
    fun rejectsLaterOnlyExtendedControlsInRubySapphire() {
        for (selector in listOf(0x17, 0x18)) {
            val rubySapphire = JapanesePokemonTextCodecs.gen3RubySapphire.decodeDetailed(
                bytes(0xFC, selector, 0xFF),
            )
            assertTrue(rubySapphire.terminated)
            assertEquals(1, rubySapphire.invalidUnits)
            assertEquals(0, rubySapphire.controlUnits)

            val later = JapanesePokemonTextCodecs.gen3Later.decodeDetailed(
                bytes(0xFC, selector, 0xFF),
            )
            assertTrue(later.terminated)
            assertEquals(0, later.invalidUnits)
            assertEquals(1, later.controlUnits)
        }
    }

    @Test
    fun consumesGenerationThreeControlsAtTheirExactArity() {
        val decoded = JapanesePokemonTextCodecs.gen3Later.decodeDetailed(
            bytes(
                0x01,
                0xF7, 0x01,
                0xF8, 0x0C,
                0xF9, 0x17,
                0xFC, 0x04, 0x01, 0x02, 0x03,
                0xFD, 0x0D,
                0xFE,
                0x51,
                0xFF,
            ),
        )

        assertEquals("あ ア", decoded.text)
        assertTrue(decoded.terminated)
        assertEquals(6, decoded.controlUnits)
        assertEquals(17, decoded.consumedBytes)
        assertEquals(0, decoded.invalidUnits)
    }

    @Test
    fun rejectsUnknownAndTruncatedGenerationThreeControlsWithinTheByteWindow() {
        val unknown = JapanesePokemonTextCodecs.gen3Later.decodeDetailed(
            bytes(0xFC, 0x19, 0xFF),
        )
        assertEquals("", unknown.text)
        assertTrue(unknown.terminated)
        assertEquals(1, unknown.invalidUnits)
        assertEquals(3, unknown.consumedBytes)

        val truncated = JapanesePokemonTextCodecs.gen3Later.decodeDetailed(
            bytes(0xFC, 0x04, 0x01),
        )
        assertEquals("", truncated.text)
        assertFalse(truncated.terminated)
        assertEquals(1, truncated.invalidUnits)
        assertEquals(3, truncated.consumedBytes)

        val truncatedDynamic = JapanesePokemonTextCodecs.gen3Later.decodeDetailed(bytes(0xF7))
        assertEquals("", truncatedDynamic.text)
        assertFalse(truncatedDynamic.terminated)
        assertEquals(1, truncatedDynamic.invalidUnits)
        assertEquals(1, truncatedDynamic.consumedBytes)
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
}
