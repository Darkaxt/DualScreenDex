package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import com.enrpau.dualscreendex.parser.text.KoreanGen2PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.LanguageTextPlausibility
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextToken
import com.enrpau.dualscreendex.parser.text.WesternPokemonTextCodecs

internal enum class Gen2LandmarkNameEncoding {
    STANDARD,
    EXPANDED,
}

/** Decodes the copied Gen II Town Map name buffer with its actual PlaceString controls. */
internal object Gen2LandmarkNameCodec {
    fun decode(bytes: ByteArray, codec: PokemonTextCodec): String? =
        decode(bytes, Gen2LandmarkNameEncoding.STANDARD, codec)

    fun decode(
        bytes: ByteArray,
        encoding: Gen2LandmarkNameEncoding,
        codec: PokemonTextCodec,
    ): String? {
        val usesWesternDialect = codec in WESTERN_PLACE_STRING_CODECS
        val usesEnglishDialect = codec === PokemonTextCodec.gbEnglish || codec === WesternPokemonTextCodecs.gen2English
        val usesJapaneseDialect = codec === JapanesePokemonTextCodecs.gen2
        val usesKoreanDialect = codec === KoreanGen2PokemonTextCodec.codec
        if (encoding == Gen2LandmarkNameEncoding.EXPANDED && !usesEnglishDialect) return null
        val doneControl = when {
            usesJapaneseDialect -> DONE
            usesKoreanDialect -> KOREAN_DONE
            else -> null
        }
        val nativeLineControls = when {
            usesJapaneseDialect -> JAPANESE_STATIC_LINE_CONTROLS
            usesKoreanDialect -> KOREAN_STATIC_LINE_CONTROLS
            else -> emptySet()
        }
        val rom = RomImage(bytes)
        val output = StringBuilder()
        var cursor = 0
        var displayDone = false
        var terminated = false
        while (cursor < rom.size) {
            val value = rom.u8(cursor)
            if (!displayDone && usesWesternDialect) {
                when (value) {
                    codec.terminator -> {
                        terminated = true
                        break
                    }
                    DONE -> {
                        displayDone = true
                        cursor++
                        continue
                    }
                    NULL -> return null // PlaceString substitutes runtime debug text; no stable semantic name.
                    BSP, LF, WBR, NEXT, LINE -> {
                        output.append(' ')
                        cursor++
                        continue
                    }
                    POKE, POKE_GLYPH -> {
                        output.append("POKé")
                        cursor++
                        continue
                    }
                    PKMN -> {
                        output.append("PKMN")
                        cursor++
                        continue
                    }
                    SIX_DOTS -> {
                        output.append("……")
                        cursor++
                        continue
                    }
                    PC -> {
                        output.append("PC")
                        cursor++
                        continue
                    }
                    TM -> {
                        output.append(TECHNICAL_MACHINE_SUBSTITUTIONS[codec.language] ?: return null)
                        cursor++
                        continue
                    }
                    TRAINER, ROCKET -> {
                        if (!usesEnglishDialect) return null
                        output.append(if (value == TRAINER) "TRAINER" else "ROCKET")
                        cursor++
                        continue
                    }
                    0x9a -> {
                        output.append('(')
                        cursor++
                        continue
                    }
                    0x9b -> {
                        output.append(')')
                        cursor++
                        continue
                    }
                    0x9c -> {
                        output.append(':')
                        cursor++
                        continue
                    }
                    0x9d -> {
                        output.append(';')
                        cursor++
                        continue
                    }
                    0x9e -> {
                        output.append('[')
                        cursor++
                        continue
                    }
                    0x9f -> {
                        output.append(']')
                        cursor++
                        continue
                    }
                    in 0xba..0xff -> {
                        val dialect = if (usesEnglishDialect) {
                            decodeDialectGlyph(value, encoding)
                        } else {
                            null
                        }
                        if (dialect != null) {
                            output.append(dialect)
                            cursor++
                            continue
                        }
                        if (encoding == Gen2LandmarkNameEncoding.EXPANDED) return null
                    }
                    in 0 until FIRST_FONT_GLYPH -> return null
                }
            }

            if (!displayDone && !usesWesternDialect) {
                // Exact native dispatch only, at a token boundary: a Korean trail is never inspected here.
                if (usesKoreanDialect && value in KOREAN_RUNTIME_NAMES) return null
                when {
                    value == doneControl -> {
                        displayDone = true
                        cursor++
                        continue
                    }
                    value in nativeLineControls -> {
                        output.append(' ')
                        cursor++
                        continue
                    }
                }
            }

            val token = codec.decodeToken(rom, cursor, rom.size)
            cursor += token.byteCount
            if (displayDone) {
                if (token is PokemonTextToken.Terminator) {
                    terminated = true
                    break
                }
                continue
            }
            when (token) {
                is PokemonTextToken.Glyph -> output.append(token.text)
                is PokemonTextToken.Whitespace -> output.append(token.text)
                is PokemonTextToken.Substitution -> output.append(token.text)
                // Only the exact static line and DONE controls above have landmark display authority.
                is PokemonTextToken.Control -> return null
                is PokemonTextToken.Invalid -> return null
                is PokemonTextToken.Terminator -> {
                    terminated = true
                    break
                }
            }
        }
        if (!terminated) return null
        val name = output.toString().replace(WHITESPACE, " ").trim().takeIf(String::isNotBlank) ?: return null
        if (codec.language in NATIVE_LANGUAGES &&
            !LanguageTextPlausibility.looksLikeStandaloneFixedName(name, codec.language)
        ) return null
        return name
    }

    private fun decodeDialectGlyph(
        value: Int,
        encoding: Gen2LandmarkNameEncoding,
    ): String? =
        when (encoding) {
            Gen2LandmarkNameEncoding.STANDARD -> when (value) {
                0xc0 -> "Ä"
                0xc1 -> "Ö"
                0xc2 -> "Ü"
                0xc3 -> "ä"
                0xc4 -> "ö"
                0xc5 -> "ü"
                in 0xd0..0xd6 -> CONTRACTIONS[value - 0xd0]
                0xdf -> "←"
                0xe1 -> "PK"
                0xe2 -> "MN"
                0xeb -> "→"
                0xec -> "▷"
                0xed -> "▶"
                0xee -> "▼"
                0xef -> "♂"
                0xf0 -> "¥"
                0xf1 -> "×"
                0xf3 -> "/"
                0xf4 -> ","
                0xf5 -> "♀"
                else -> null
            }
            Gen2LandmarkNameEncoding.EXPANDED -> when (value) {
                0xba -> "′"
                0xbb -> "″"
                0xbc -> "PHONE"
                0xbd -> "SHINY"
                in 0xc0..0xc6 -> CONTRACTIONS[value - 0xc0]
                0xc7 -> "↕"
                0xc8 -> "PO"
                0xc9 -> "KE"
                0xca -> "“"
                0xcb -> "”"
                0xcc -> "ID"
                0xcd -> "№"
                0xce -> "…"
                0xcf -> "←"
                0xd0 -> "'"
                0xd1 -> "PK"
                0xd2 -> "MN"
                0xd3 -> "-"
                0xd4 -> "◀"
                0xd5 -> "▲"
                0xd6 -> "?"
                0xd7 -> "!"
                0xd8 -> "."
                0xd9 -> "&"
                0xda -> "é"
                0xdb -> "→"
                0xdc -> "▷"
                0xdd -> "▶"
                0xde -> "▼"
                0xdf -> "♂"
                0xe0 -> "¥"
                0xe1 -> "×"
                0xe2 -> "·"
                0xe3 -> "/"
                0xe4 -> ","
                0xe5 -> "♀"
                in 0xe6..0xef -> ('0'.code + value - 0xe6).toChar().toString()
                0xfa -> "┌"
                0xfb -> "─"
                0xfc -> "┐"
                0xfd -> "│"
                0xfe -> "└"
                0xff -> "┘"
                else -> null
            }
        }

    private const val NULL = 0x00
    private const val BSP = 0x1f
    private const val LF = 0x22
    private const val POKE = 0x24
    private const val WBR = 0x25
    private const val PKMN = 0x4a
    private const val NEXT = 0x4e
    private const val LINE = 0x4f
    private const val POKE_GLYPH = 0x54
    private const val SIX_DOTS = 0x56
    private const val DONE = 0x57
    private const val PC = 0x5b
    private const val TM = 0x5c
    private const val TRAINER = 0x5d
    private const val ROCKET = 0x5e
    private const val FIRST_FONT_GLYPH = 0x60
    private val CONTRACTIONS = arrayOf("'d", "'l", "'m", "'r", "'s", "'t", "'v")
    private val TECHNICAL_MACHINE_SUBSTITUTIONS = mapOf(
        LanguageTag.ENGLISH to "TM",
        LanguageTag.FRENCH to "CT",
        LanguageTag.GERMAN to "TM",
        LanguageTag.ITALIAN to "MT",
        LanguageTag.SPANISH to "MT",
    )
    // Object identity fences these overrides to the registered, versioned dialect implementations.
    private val WESTERN_PLACE_STRING_CODECS = setOf(
        PokemonTextCodec.gbEnglish,
        WesternPokemonTextCodecs.gen2English,
        WesternPokemonTextCodecs.gen2French,
        WesternPokemonTextCodecs.gen2German,
        WesternPokemonTextCodecs.gen2Italian,
        WesternPokemonTextCodecs.gen2Spanish,
    )
    private val NATIVE_LANGUAGES = setOf(LanguageTag.JAPANESE, LanguageTag.KOREAN)
    private val JAPANESE_STATIC_LINE_CONTROLS = setOf(NEXT, LINE)
    // pokegold-kr 7743877dc9fa8603f4b6eaebe904a7ba03fdb9e4: constants/charmap.asm and home/text.asm.
    // RED, GREEN, and MOM read WRAM in PlaceString even though the general codec has default labels.
    private val KOREAN_RUNTIME_NAMES = setOf(0x4d, 0x4e, 0x5b)
    private val KOREAN_STATIC_LINE_CONTROLS = setOf(0x1d, 0x1e, 0x34, 0x59, 0x5a)
    private const val KOREAN_DONE = 0x5e
    private val WHITESPACE = Regex("\\s+")
}
