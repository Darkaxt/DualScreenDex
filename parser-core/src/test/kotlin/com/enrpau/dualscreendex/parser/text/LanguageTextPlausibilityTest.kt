package com.enrpau.dualscreendex.parser.text

import com.enrpau.dualscreendex.parser.language.LanguageTag
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageTextPlausibilityTest {
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
