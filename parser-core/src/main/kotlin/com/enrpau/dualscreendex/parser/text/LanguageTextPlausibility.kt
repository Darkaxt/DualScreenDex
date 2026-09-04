package com.enrpau.dualscreendex.parser.text

import com.enrpau.dualscreendex.parser.language.LanguageTag

/** Text-shape plausibility only; neither script nor letter case establishes language authority. */
internal object LanguageTextPlausibility {
    fun looksLikeNaturalDescription(
        value: String,
        language: LanguageTag,
        minimumLength: Int,
        minimumWords: Int,
        requireLowercase: Boolean = false,
    ): Boolean {
        if (language !in WESTERN_LANGUAGES) {
            return hasNativeScriptShape(value, language) &&
                value.count(Char::isLetterOrDigit) >= minimumLength
        }
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
        if (language !in WESTERN_LANGUAGES) return hasNativeScriptShape(value, language)
        val first = value.firstOrNull(Char::isLetterOrDigit) ?: return false
        return !first.isLetter() || first.isUpperCase()
    }

    private fun hasNativeScriptShape(value: String, language: LanguageTag): Boolean {
        val nativeScripts = when (language) {
            LanguageTag.JAPANESE -> JAPANESE_SCRIPTS
            LanguageTag.KOREAN -> KOREAN_SCRIPTS
            else -> return false
        }
        var nativeLetters = 0
        var nameCharacters = 0
        for (character in value) {
            if (!character.isLetterOrDigit()) continue
            nameCharacters++
            if (character.isDigit()) continue
            when (Character.UnicodeScript.of(character.code)) {
                in nativeScripts -> nativeLetters++
                Character.UnicodeScript.LATIN -> Unit
                Character.UnicodeScript.COMMON -> if (character != 'ー') return false
                else -> return false
            }
        }
        // Allow abbreviations such as HP, but not Latin-only text or native-letter padding.
        return nativeLetters > 0 && nativeLetters.toLong() * 2 >= nameCharacters
    }

    private val JAPANESE_SCRIPTS = setOf(
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
    )
    private val KOREAN_SCRIPTS = setOf(Character.UnicodeScript.HANGUL)
    private val WESTERN_LANGUAGES = setOf(
        LanguageTag.ENGLISH,
        LanguageTag.FRENCH,
        LanguageTag.GERMAN,
        LanguageTag.ITALIAN,
        LanguageTag.SPANISH,
    )
    private val WHITESPACE = Regex("\\s+")
}
