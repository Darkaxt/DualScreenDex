package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextToken

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
        val rom = RomImage(bytes)
        val output = StringBuilder()
        var cursor = 0
        var displayDone = false
        var terminated = false
        while (cursor < rom.size) {
            val value = rom.u8(cursor)
            if (!displayDone) {
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
                        output.append("TM")
                        cursor++
                        continue
                    }
                    TRAINER -> {
                        output.append("TRAINER")
                        cursor++
                        continue
                    }
                    ROCKET -> {
                        output.append("ROCKET")
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
                        val dialect = decodeDialectGlyph(value, encoding)
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
                is PokemonTextToken.Control -> output.append(token.replacement)
                is PokemonTextToken.Invalid -> return null
                is PokemonTextToken.Terminator -> {
                    terminated = true
                    break
                }
            }
        }
        if (!terminated) return null
        return output.toString().replace(WHITESPACE, " ").trim().takeIf(String::isNotBlank)
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
    private val WHITESPACE = Regex("\\s+")
}
