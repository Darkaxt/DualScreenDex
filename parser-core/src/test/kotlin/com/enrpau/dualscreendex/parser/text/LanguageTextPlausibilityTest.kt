package com.enrpau.dualscreendex.parser.text

import com.enrpau.dualscreendex.parser.language.LanguageTag
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageTextPlausibilityTest {
    @Test
    fun acceptsNativeDescriptionsWithoutLatinCaseOrWordBoundaries() {
        for ((language, text) in listOf(
            LanguageTag.JAPANESE to "あいてをこうげきするわざ。",
            LanguageTag.KOREAN to "상대방을공격하는기술입니다.",
        )) {
            assertTrue(
                LanguageTextPlausibility.looksLikeNaturalDescription(
                    text, language, minimumLength = 8, minimumWords = 3, requireLowercase = true,
                ),
            )
            assertTrue(LanguageTextPlausibility.looksLikeStandaloneFixedName(text, language))
            assertFalse(LanguageTextPlausibility.startsWithLowercaseName(text, language))
        }
    }

    @Test
    fun rejectsWrongScriptsAndPunctuationOnlyNonLatinDescriptions() {
        for ((language, text) in listOf(
            LanguageTag.JAPANESE to "상대방을공격하는기술입니다.",
            LanguageTag.KOREAN to "あいてをこうげきするわざ。",
            LanguageTag.JAPANESE to "あ................",
            LanguageTag.JAPANESE to "あ1234567",
            LanguageTag.KOREAN to "가1234567",
            LanguageTag.KOREAN to "가                ",
            LanguageTag.JAPANESE to "あいうえおかきくけこЖ",
        )) {
            assertFalse(
                LanguageTextPlausibility.looksLikeNaturalDescription(
                    text, language, minimumLength = 8, minimumWords = 3,
                ),
            )
        }
    }

    @Test
    fun acceptsUncasedNamesWithNeutralDigitsAndLatinAbbreviations() {
        assertTrue(LanguageTextPlausibility.looksLikeStandaloneFixedName("ポリゴン２", LanguageTag.JAPANESE))
        assertTrue(LanguageTextPlausibility.looksLikeStandaloneFixedName("HP회복", LanguageTag.KOREAN))
        assertFalse(LanguageTextPlausibility.startsWithLowercaseName("hp회복", LanguageTag.KOREAN))
        assertFalse(LanguageTextPlausibility.looksLikeStandaloneFixedName("HP123", LanguageTag.KOREAN))
        assertFalse(LanguageTextPlausibility.looksLikeStandaloneFixedName("???", LanguageTag.JAPANESE))
        assertFalse(LanguageTextPlausibility.looksLikeStandaloneFixedName("ピ카", LanguageTag.JAPANESE))
    }

    @Test
    fun latinWordShapeIsRestrictedToRatifiedWesternLanguages() {
        listOf(
            LanguageTag.ENGLISH,
            LanguageTag.FRENCH,
            LanguageTag.GERMAN,
            LanguageTag.ITALIAN,
            LanguageTag.SPANISH,
        ).forEach { language ->
            assertTrue(
                LanguageTextPlausibility.looksLikeNaturalDescription(
                    value = "Une attaque très puissante.",
                    language = language,
                    minimumLength = 12,
                    minimumWords = 3,
                    requireLowercase = true,
                ),
            )
            assertTrue(LanguageTextPlausibility.looksLikeStandaloneFixedName("ÉCLAIR", language))
            assertTrue(LanguageTextPlausibility.startsWithLowercaseName("éclair", language))
        }

        listOf(LanguageTag.JAPANESE, LanguageTag.KOREAN, LanguageTag.of("pt")).forEach { language ->
            assertFalse(
                LanguageTextPlausibility.looksLikeNaturalDescription(
                    value = "Une attaque très puissante.",
                    language = language,
                    minimumLength = 12,
                    minimumWords = 3,
                    requireLowercase = true,
                ),
            )
            assertFalse(LanguageTextPlausibility.looksLikeStandaloneFixedName("ÉCLAIR", language))
            assertFalse(LanguageTextPlausibility.startsWithLowercaseName("éclair", language))
        }
    }
}
