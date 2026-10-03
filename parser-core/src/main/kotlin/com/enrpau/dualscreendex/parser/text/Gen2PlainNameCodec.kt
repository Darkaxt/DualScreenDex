package com.enrpau.dualscreendex.parser.text

import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.Platform

/** Explicit plain-name dialect; it is not a Huffman prose decoder. */
internal object Gen2PlainNameCodec {
    val english53 = PokemonTextCodec(
        id = "gb-gen2-en-53-plain",
        version = 1,
        language = LanguageTag.ENGLISH,
        applicableGenerations = setOf(2),
        applicablePlatforms = setOf(Platform.GB, Platform.GBC),
        terminator = 0x53,
    ) { rom, offset, _ ->
        when (val value = rom.u8(offset)) {
            0x53 -> PokemonTextToken.Terminator()
            0x7F -> PokemonTextToken.Whitespace()
            in 0x80..0x99 -> PokemonTextToken.Glyph(('A' + value - 0x80).toString())
            in 0xA0..0xB9 -> PokemonTextToken.Glyph(('a' + value - 0xA0).toString())
            in 0xE0..0xE9 -> PokemonTextToken.Glyph(('0' + value - 0xE0).toString())
            else -> glyphs[value]?.let { PokemonTextToken.Glyph(it) } ?: PokemonTextToken.Invalid()
        }
    }

    private val glyphs = mapOf(
        0x9A to "(", 0x9B to ")", 0x9C to ".", 0x9D to ",", 0x9E to "?", 0x9F to "!",
        0xBA to "“", 0xBB to "”", 0xBC to "-", 0xBD to ":", 0xBE to "♂", 0xBF to "♀",
        0xC0 to "'", 0xC1 to "'d", 0xC2 to "'l", 0xC3 to "'m", 0xC4 to "'r",
        0xC5 to "'s", 0xC6 to "'t", 0xC7 to "'v", 0xC8 to "é", 0xC9 to "É", 0xCA to "á",
        0xDB to "×", 0xDC to "/", 0xDD to "%", 0xDE to "+",
    )
}
