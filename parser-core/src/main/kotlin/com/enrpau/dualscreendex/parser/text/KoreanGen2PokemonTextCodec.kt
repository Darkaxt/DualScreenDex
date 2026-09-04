package com.enrpau.dualscreendex.parser.text

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.Platform

/** Variable-width codec for the official Korean Gold and Silver text format. */
object KoreanGen2PokemonTextCodec {
    val codec by lazy {
        PokemonTextCodec(
            id = "gb-gen2-ko",
            version = 1,
            language = LanguageTag.KOREAN,
            applicableGenerations = setOf(2),
            applicablePlatforms = setOf(Platform.GBC),
            terminator = TERMINATOR,
            tokenDecoder = PokemonTextTokenDecoder { rom, offset, endExclusive ->
                val value = rom.u8(offset)
                when {
                    value in LEAD_BYTES -> decodePair(rom, offset, endExclusive)
                    value == TERMINATOR -> PokemonTextToken.Terminator()
                    value == WHITESPACE -> PokemonTextToken.Whitespace()
                    value in SUBSTITUTIONS -> PokemonTextToken.Substitution(
                        requireNotNull(SUBSTITUTIONS[value]),
                    )
                    value in CONTROLS -> PokemonTextToken.Control()
                    value in SINGLE_BYTE_GLYPHS -> PokemonTextToken.Glyph(
                        requireNotNull(SINGLE_BYTE_GLYPHS[value]),
                    )
                    else -> PokemonTextToken.Invalid()
                }
            },
        )
    }

    private fun decodePair(
        rom: RomImage,
        offset: Int,
        endExclusive: Int,
    ): PokemonTextToken {
        if (endExclusive - offset < PAIR_BYTE_COUNT) return PokemonTextToken.Invalid()
        val decoded = KoreanGen2CharacterTables.decode(
            lead = rom.u8(offset),
            trail = rom.u8(offset + 1),
        ) ?: return PokemonTextToken.Invalid(byteCount = PAIR_BYTE_COUNT)
        return if (decoded.isBlank()) {
            PokemonTextToken.Whitespace(byteCount = PAIR_BYTE_COUNT)
        } else {
            PokemonTextToken.Glyph(decoded, byteCount = PAIR_BYTE_COUNT)
        }
    }

    private fun glyphRange(start: Int, value: String): Map<Int, String> =
        value.mapIndexed { index, character -> start + index to character.toString() }.toMap()

    private const val TERMINATOR = 0x50
    private const val WHITESPACE = 0x7F
    private const val PAIR_BYTE_COUNT = 2
    private val LEAD_BYTES = 0x01..0x0B

    private val SUBSTITUTIONS = mapOf(
        0x1F to "こうげき",
        0x33 to "POKé",
        0x46 to "PKMN",
        0x47 to "포켓몬",
        0x48 to "……",
        0x49 to "컴퓨터",
        0x4A to "기술머신",
        0x4B to "로켓단",
        0x4D to "레드",
        0x4E to "그린",
        0x55 to "트레이너",
        0x5B to "어머니",
        0xE1 to "PK",
        0xE2 to "MN",
    ) + (0xD0..0xD6).zip(listOf("'d", "'l", "'m", "'r", "'s", "'t", "'v"))

    private val CONTROLS = setOf(
        0x00, 0x1D, 0x1E, 0x34, 0x35, 0x36, 0x37,
        0x4C, 0x4F, 0x51, 0x52, 0x53, 0x54, 0x56, 0x57,
        0x59, 0x5A, 0x5C, 0x5D, 0x5E, 0x5F,
    )

    private val SINGLE_BYTE_GLYPHS =
        glyphRange(0x80, "ABCDEFGHIJKLMNOPQRSTUVWXYZ") +
            glyphRange(0xA0, "abcdefghijklmnopqrstuvwxyz") +
            glyphRange(0xC0, "ÄÖÜäöü") +
            mapOf(
                0x60 to "■",
                0x61 to "▲",
                0x62 to "☎",
                0x6D to ":",
                0x6E to "ぃ",
                0x6F to "ぅ",
                0x70 to "PO",
                0x71 to "Ké",
                0x72 to "『",
                0x73 to "』",
                0x74 to "·",
                0x75 to "…",
                0x76 to "ぁ",
                0x77 to "ぇ",
                0x78 to "ぉ",
                0x79 to "┌",
                0x7A to "─",
                0x7B to "┐",
                0x7C to "│",
                0x7D to "└",
                0x7E to "┘",
                0x9A to "(",
                0x9B to ")",
                0x9C to ":",
                0x9D to ";",
                0x9E to "[",
                0x9F to "]",
                0xE0 to "'",
                0xE3 to "-",
                0xE6 to "?",
                0xE7 to "!",
                0xE8 to ".",
                0xE9 to "&",
                0xEA to "é",
                0xEC to "▷",
                0xED to "▶",
                0xEE to "▼",
                0xEF to "♂",
                0xF0 to "₩",
                0xF1 to "×",
                0xF2 to ".",
                0xF3 to "/",
                0xF4 to ",",
                0xF5 to "♀",
            ) + glyphRange(0xF6, "0123456789")
}
