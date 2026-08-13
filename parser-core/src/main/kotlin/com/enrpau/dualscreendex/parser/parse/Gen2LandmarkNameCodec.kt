package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

/** Decodes the copied Gen II Town Map name buffer with its actual PlaceString controls. */
internal object Gen2LandmarkNameCodec {
    fun decode(bytes: ByteArray): String? {
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
                0xc0 -> output.append('Ä')
                0xc1 -> output.append('Ö')
                0xc2 -> output.append('Ü')
                0xc3 -> output.append('ä')
                0xc4 -> output.append('ö')
                0xc5 -> output.append('ü')
                in 0xd0..0xd6 -> output.append(CONTRACTIONS[value - 0xd0])
                0xdf -> output.append('←')
                0xe1 -> output.append("PK")
                0xe2 -> output.append("MN")
                0xeb -> output.append('→')
                0xec -> output.append('▷')
                0xed -> output.append('▶')
                0xee -> output.append('▼')
                0xef -> output.append('♂')
                0xf0 -> output.append('¥')
                0xf1 -> output.append('×')
                0xf3 -> output.append('/')
                0xf4 -> output.append(',')
                0xf5 -> output.append('♀')
                in 0 until FIRST_FONT_GLYPH -> return null
                else -> output.append(PokemonTextCodec.gbEnglish.decodeByte(value) ?: return null)
            }
        }
        return output.toString().replace(WHITESPACE, " ").trim().takeIf(String::isNotBlank)
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
