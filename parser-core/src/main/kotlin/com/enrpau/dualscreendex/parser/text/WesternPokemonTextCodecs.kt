package com.enrpau.dualscreendex.parser.text

import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.Platform

/** Versioned codecs for the official Western Gen I-III text dialects. */
object WesternPokemonTextCodecs {
    val gen1English by lazy { gbCodec("gb-gen1-en", LanguageTag.ENGLISH, 1, GEN1_ENGLISH) }
    val gen1French by lazy { gbCodec("gb-gen1-fr", LanguageTag.FRENCH, 1, GEN1_FRENCH_GERMAN) }
    val gen1German by lazy { gbCodec("gb-gen1-de", LanguageTag.GERMAN, 1, GEN1_FRENCH_GERMAN) }
    val gen1Italian by lazy { gbCodec("gb-gen1-it", LanguageTag.ITALIAN, 1, GEN1_ITALIAN_SPANISH) }
    val gen1Spanish by lazy { gbCodec("gb-gen1-es", LanguageTag.SPANISH, 1, GEN1_ITALIAN_SPANISH) }

    val gen2English by lazy { gbCodec("gb-gen2-en", LanguageTag.ENGLISH, 2, GEN2_ENGLISH) }
    val gen2French by lazy { gbCodec("gb-gen2-fr", LanguageTag.FRENCH, 2, GEN2_FRENCH_GERMAN) }
    val gen2German by lazy { gbCodec("gb-gen2-de", LanguageTag.GERMAN, 2, GEN2_FRENCH_GERMAN) }
    val gen2Italian by lazy { gbCodec("gb-gen2-it", LanguageTag.ITALIAN, 2, GEN2_ITALIAN_SPANISH) }
    val gen2Spanish by lazy { gbCodec("gb-gen2-es", LanguageTag.SPANISH, 2, GEN2_ITALIAN_SPANISH) }

    val gen3English by lazy { gbaCodec("gba-gen3-en", LanguageTag.ENGLISH, '“', '”') }
    val gen3French by lazy { gbaCodec("gba-gen3-fr", LanguageTag.FRENCH, '«', '»') }
    val gen3German by lazy { gbaCodec("gba-gen3-de", LanguageTag.GERMAN, '„', '“') }
    val gen3Italian by lazy { gbaCodec("gba-gen3-it", LanguageTag.ITALIAN, '“', '”') }
    val gen3Spanish by lazy { gbaCodec("gba-gen3-es", LanguageTag.SPANISH, '“', '”') }

    val all: List<PokemonTextCodec> by lazy {
        listOf(
            gen1English,
            gen1French,
            gen1German,
            gen1Italian,
            gen1Spanish,
            gen2English,
            gen2French,
            gen2German,
            gen2Italian,
            gen2Spanish,
            gen3English,
            gen3French,
            gen3German,
            gen3Italian,
            gen3Spanish,
        )
    }

    fun forLanguage(language: LanguageTag, generation: Int): PokemonTextCodec? = all.singleOrNull {
        it.language == language && generation in it.applicableGenerations
    }

    private fun gbCodec(
        id: String,
        language: LanguageTag,
        generation: Int,
        overlay: Map<Int, PokemonTextToken>,
    ): PokemonTextCodec {
        val controls = if (generation == 1) GEN1_CONTROLS else GEN2_CONTROLS
        val mappedTokens = overlay + GB_SUBSTITUTIONS
        return PokemonTextCodec(
            id = id,
            version = 1,
            language = language,
            applicableGenerations = setOf(generation),
            applicablePlatforms = setOf(Platform.GB, Platform.GBC),
            terminator = GB_TERMINATOR,
            tokenDecoder = PokemonTextTokenDecoder { rom, offset, _ ->
                val value = rom.u8(offset)
                val mappedToken = mappedTokens[value]
                when {
                    value == GB_TERMINATOR -> PokemonTextToken.Terminator()
                    value == 0x7F -> PokemonTextToken.Whitespace()
                    value in controls -> PokemonTextToken.Control()
                    mappedToken != null -> mappedToken
                    value in 0x80..0x99 -> sequentialGlyph(value, 0x80, 'A')
                    value in 0xA0..0xB9 -> sequentialGlyph(value, 0xA0, 'a')
                    value in 0xF6..0xFF -> sequentialGlyph(value, 0xF6, '0')
                    value in GB_COMMON_GLYPHS -> PokemonTextToken.Glyph(
                        requireNotNull(GB_COMMON_GLYPHS[value]).toString(),
                    )
                    else -> PokemonTextToken.Invalid()
                }
            },
        )
    }

    private fun gbaCodec(
        id: String,
        language: LanguageTag,
        openingQuote: Char,
        closingQuote: Char,
    ): PokemonTextCodec = PokemonTextCodec(
        id = id,
        version = 1,
        language = language,
        applicableGenerations = setOf(3),
        applicablePlatforms = setOf(Platform.GBA),
        terminator = GBA_TERMINATOR,
        tokenDecoder = PokemonTextTokenDecoder { rom, offset, endExclusive ->
            val value = rom.u8(offset)
            when {
                value == GBA_TERMINATOR -> PokemonTextToken.Terminator()
                value == 0x00 -> PokemonTextToken.Whitespace()
                value in GBA_TWO_BYTE_CONTROLS -> if (offset + 1 < endExclusive) {
                    PokemonTextToken.Control(byteCount = 2)
                } else {
                    PokemonTextToken.Invalid(byteCount = 2)
                }
                value in GBA_ONE_BYTE_CONTROLS -> PokemonTextToken.Control()
                value == 0x53 -> PokemonTextToken.Substitution("PK")
                value == 0x54 -> PokemonTextToken.Substitution("MN")
                value == 0xB1 -> PokemonTextToken.Glyph(openingQuote.toString())
                value == 0xB2 -> PokemonTextToken.Glyph(closingQuote.toString())
                value in 0xA1..0xAA -> sequentialGlyph(value, 0xA1, '0')
                value in 0xBB..0xD4 -> sequentialGlyph(value, 0xBB, 'A')
                value in 0xD5..0xEE -> sequentialGlyph(value, 0xD5, 'a')
                value in GBA_COMMON_GLYPHS -> PokemonTextToken.Glyph(
                    requireNotNull(GBA_COMMON_GLYPHS[value]).toString(),
                )
                else -> PokemonTextToken.Invalid()
            }
        },
    )

    private fun sequentialGlyph(
        value: Int,
        encodedStart: Int,
        decodedStart: Char,
    ): PokemonTextToken.Glyph = PokemonTextToken.Glyph(
        (decodedStart.code + value - encodedStart).toChar().toString(),
    )

    private fun glyphs(vararg values: Pair<Int, String>): Map<Int, PokemonTextToken> =
        values.associate { (value, text) -> value to PokemonTextToken.Glyph(text) }

    private fun substitutions(vararg values: Pair<Int, String>): Map<Int, PokemonTextToken> =
        values.associate { (value, text) -> value to PokemonTextToken.Substitution(text) }

    private const val GB_TERMINATOR = 0x50
    private const val GBA_TERMINATOR = 0xFF

    private val GEN1_CONTROLS = setOf(
        0x49, 0x4B, 0x4C, 0x4E, 0x4F,
        0x51, 0x52, 0x53, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5A, 0x5B, 0x5C, 0x5D, 0x5E, 0x5F,
    )
    private val GEN2_CONTROLS = GEN1_CONTROLS + setOf(0x16, 0x1D, 0x1E, 0x1F, 0x22, 0x25)
    private val GB_SUBSTITUTIONS = substitutions(
        0x4A to "PKMN",
        0x54 to "POKé",
        0xE1 to "PK",
        0xE2 to "MN",
    )
    private val GB_COMMON_GLYPHS = mapOf(
        0x9A to '(',
        0x9B to ')',
        0x9C to ':',
        0x9D to ';',
        0x9E to '[',
        0x9F to ']',
        0xE0 to '\'',
        0xE3 to '-',
        0xE6 to '?',
        0xE7 to '!',
        0xE8 to '.',
        0xEC to '▷',
        0xED to '▶',
        0xEE to '▼',
        0xEF to '♂',
        0xF0 to '¥',
        0xF1 to '×',
        0xF2 to '.',
        0xF3 to '/',
        0xF4 to ',',
        0xF5 to '♀',
    )

    private val GEN1_ENGLISH = glyphs(0xBA to "é") + substitutions(
        0xBB to "'d",
        0xBC to "'l",
        0xBD to "'s",
        0xBE to "'t",
        0xBF to "'v",
        0xE4 to "'r",
        0xE5 to "'m",
    )
    private val GEN1_FRENCH_GERMAN = glyphs(
        0xBA to "à", 0xBB to "è", 0xBC to "é", 0xBD to "ù", 0xBE to "ß", 0xBF to "ç",
        0xC0 to "Ä", 0xC1 to "Ö", 0xC2 to "Ü", 0xC3 to "ä", 0xC4 to "ö", 0xC5 to "ü",
        0xC6 to "ë", 0xC7 to "ï", 0xC8 to "â", 0xC9 to "ô", 0xCA to "û", 0xCB to "ê", 0xCC to "î",
    ) + substitutions(
        0xD4 to "c'", 0xD5 to "d'", 0xD6 to "j'", 0xD7 to "l'",
        0xD8 to "m'", 0xD9 to "n'", 0xDA to "p'", 0xDB to "s'",
        0xDC to "'s", 0xDD to "t'", 0xDE to "u'", 0xDF to "y'",
        0xE4 to "+",
    )
    private val GEN1_ITALIAN_SPANISH = glyphs(
        0xBA to "à", 0xBB to "è", 0xBC to "é", 0xBD to "ù", 0xBE to "À", 0xBF to "Á",
        0xC0 to "Ä", 0xC1 to "Ö", 0xC2 to "Ü", 0xC3 to "ä", 0xC4 to "ö", 0xC5 to "ü",
        0xC6 to "È", 0xC7 to "É", 0xC8 to "Ì", 0xC9 to "Í", 0xCA to "Ñ", 0xCB to "Ò", 0xCC to "Ó",
        0xCD to "Ù", 0xCE to "Ú", 0xCF to "á", 0xD0 to "ì", 0xD1 to "í", 0xD2 to "ñ", 0xD3 to "ò",
        0xD4 to "ó", 0xD5 to "ú", 0xD6 to "º", 0xD7 to "&", 0xE4 to "¿", 0xE5 to "¡",
    ) + substitutions(
        0xD8 to "'d", 0xD9 to "'l", 0xDA to "'m", 0xDB to "'r",
        0xDC to "'s", 0xDD to "'t", 0xDE to "'v",
    )

    private val GEN2_ENGLISH = glyphs(
        0xC0 to "Ä", 0xC1 to "Ö", 0xC2 to "Ü", 0xC3 to "ä", 0xC4 to "ö", 0xC5 to "ü",
        0xDF to "←", 0xE9 to "&", 0xEA to "é", 0xEB to "→",
    ) + substitutions(
        0xD0 to "'d", 0xD1 to "'l", 0xD2 to "'m", 0xD3 to "'r",
        0xD4 to "'s", 0xD5 to "'t", 0xD6 to "'v",
    )
    private val GEN2_FRENCH_GERMAN = GEN1_FRENCH_GERMAN + glyphs(
        0xCF to "←",
        0xE4 to "+",
        0xE9 to "&",
        0xEA to "é",
        0xEB to "→",
    )
    private val GEN2_ITALIAN_SPANISH = GEN1_ITALIAN_SPANISH + glyphs(
        0xDF to "←",
        0xE9 to "&",
        0xEA to "é",
        0xEB to "→",
    )

    private val GBA_ONE_BYTE_CONTROLS = setOf(0xFA, 0xFB, 0xFE)
    private val GBA_TWO_BYTE_CONTROLS = setOf(0xF7, 0xF8, 0xF9, 0xFC, 0xFD)
    private val GBA_COMMON_GLYPHS = mapOf(
        0x01 to 'À', 0x02 to 'Á', 0x03 to 'Â', 0x04 to 'Ç', 0x05 to 'È', 0x06 to 'É',
        0x07 to 'Ê', 0x08 to 'Ë', 0x09 to 'Ì', 0x0B to 'Î', 0x0C to 'Ï', 0x0D to 'Ò',
        0x0E to 'Ó', 0x0F to 'Ô', 0x10 to 'Œ', 0x11 to 'Ù', 0x12 to 'Ú', 0x13 to 'Û',
        0x14 to 'Ñ', 0x15 to 'ß', 0x16 to 'à', 0x17 to 'á', 0x19 to 'ç', 0x1A to 'è',
        0x1B to 'é', 0x1C to 'ê', 0x1D to 'ë', 0x1E to 'ì', 0x20 to 'î', 0x21 to 'ï',
        0x22 to 'ò', 0x23 to 'ó', 0x24 to 'ô', 0x25 to 'œ', 0x26 to 'ù', 0x27 to 'ú',
        0x28 to 'û', 0x29 to 'ñ', 0x2A to 'º', 0x2B to 'ª', 0x2D to '&', 0x2E to '+',
        0x35 to '=', 0x36 to ';', 0x51 to '¿', 0x52 to '¡', 0x5A to 'Í', 0x5B to '%',
        0x5C to '(', 0x5D to ')', 0x68 to 'â', 0x6F to 'í', 0x79 to '↑', 0x7A to '↓',
        0x7B to '←', 0x7C to '→', 0x85 to '<', 0x86 to '>', 0xAB to '!', 0xAC to '?',
        0xAD to '.', 0xAE to '-', 0xAF to '·', 0xB0 to '…', 0xB3 to '‘', 0xB4 to '\'',
        0xB5 to '♂', 0xB6 to '♀', 0xB7 to '¥', 0xB8 to ',', 0xB9 to '×', 0xBA to '/',
        0xEF to '▶', 0xF0 to ':', 0xF1 to 'Ä', 0xF2 to 'Ö', 0xF3 to 'Ü', 0xF4 to 'ä',
        0xF5 to 'ö', 0xF6 to 'ü',
    )
}
