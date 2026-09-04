package com.enrpau.dualscreendex.parser.text

import com.enrpau.dualscreendex.parser.language.LanguageTag

/** Latin word-shape checks restricted to the official Western languages ratified in Stage 3. */
internal object LanguageTextPlausibility {
    fun looksLikeNaturalDescription(
        value: String,
        language: LanguageTag,
        minimumLength: Int,
        minimumWords: Int,
        requireLowercase: Boolean = false,
    ): Boolean {
        if (language !in WESTERN_LANGUAGES) return false
        val words = value.split(WHITESPACE).count { it.any(Char::isLetter) }
        return value.length >= minimumLength &&
            words >= minimumWords &&
            (!requireLowercase || value.any(Char::isLowerCase))
    }

    fun startsWithLowercaseName(value: String, language: LanguageTag): Boolean {
        if (language !in WESTERN_LANGUAGES) return false
        return value.firstOrNull(Char::isLetterOrDigit)?.isLowerCase() == true
    }

    fun looksLikeStandaloneFixedName(value: String, language: LanguageTag): Boolean {
        if (language !in WESTERN_LANGUAGES) return false
        val first = value.firstOrNull(Char::isLetterOrDigit) ?: return false
        return !first.isLetter() || first.isUpperCase()
    }

    private val WESTERN_LANGUAGES = setOf(
        LanguageTag.ENGLISH,
        LanguageTag.FRENCH,
        LanguageTag.GERMAN,
        LanguageTag.ITALIAN,
        LanguageTag.SPANISH,
    )
    private val WHITESPACE = Regex("\\s+")
}
