package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

internal enum class Gen2LandmarkNameEncoding {
    STANDARD,
    EXPANDED,
}

/** Decodes the copied Gen II Town Map name buffer with its actual PlaceString controls. */
internal object Gen2LandmarkNameCodec {
    fun decode(bytes: ByteArray): String? = decode(bytes, Gen2LandmarkNameEncoding.STANDARD)

    fun decode(bytes: ByteArray, encoding: Gen2LandmarkNameEncoding): String? {
        val copiedTerminator = bytes.indexOfFirst { (it.toInt() and 0xff) == STRING_TERMINATOR }
        if (copiedTerminator < 0) return null

        val output = StringBuilder()
        for (index in 0 until copiedTerminator) {
            when (val value = bytes[index].toInt() and 0xff) {
                DONE -> break
                NULL -> return null // PlaceString substitutes runtime debug text; no stable semantic name.
                BSP, LF, WBR, NEXT, LINE -> output.append(' ')
                POKE, POKE_GLYPH -> output.append("POKé")
                PKMN -> output.append("PKMN")
                SIX_DOTS -> output.append("……")
                PC -> output.append("PC")
                TM -> output.append("TM")
                TRAINER -> output.append("TRAINER")
                ROCKET -> output.append("ROCKET")
                0x9a -> output.append('(')
                0x9b -> output.append(')')
                0x9c -> output.append(':')
                0x9d -> output.append(';')
                0x9e -> output.append('[')
                0x9f -> output.append(']')
                in 0xba..0xff -> output.append(decodeDialectGlyph(value, encoding) ?: return null)
                in 0 until FIRST_FONT_GLYPH -> return null
                else -> output.append(PokemonTextCodec.gbEnglish.decodeByte(value) ?: return null)
            }
        }
        return output.toString().replace(WHITESPACE, " ").trim().takeIf(String::isNotBlank)
    }

    private fun decodeDialectGlyph(value: Int, encoding: Gen2LandmarkNameEncoding): String? =
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
                else -> PokemonTextCodec.gbEnglish.decodeByte(value)?.toString()
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
    private const val STRING_TERMINATOR = 0x50
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
