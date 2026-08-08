package com.enrpau.dualscreendex.parser.text

data class DecodedText(
    val text: String,
    val terminated: Boolean,
    val validBytes: Int,
    val contentBytes: Int,
) {
    val validRatio: Double get() = if (contentBytes == 0) 0.0 else validBytes.toDouble() / contentBytes
}

class PokemonTextCodec private constructor(
    val name: String,
    val terminator: Int,
    private val decoder: (Int) -> Char?,
) {
    fun decode(bytes: ByteArray): String = decodeDetailed(bytes).text

    fun decodeDetailed(bytes: ByteArray): DecodedText {
        val output = StringBuilder()
        var valid = 0
        var content = 0
        var terminated = false
        for (raw in bytes) {
            val value = raw.toInt() and 0xff
            if (value == terminator) {
                terminated = true
                break
            }
            content++
            val character = decoder(value)
            if (character != null) {
                output.append(character)
                valid++
            }
        }
        return DecodedText(output.toString().trim(), terminated, valid, content)
    }

    companion object {
        val gbEnglish = PokemonTextCodec("gb-english", 0x50) { value ->
            when (value) {
                in 0x80..0x99 -> ('A'.code + value - 0x80).toChar()
                in 0xA0..0xB9 -> ('a'.code + value - 0xA0).toChar()
                in 0xF6..0xFF -> ('0'.code + value - 0xF6).toChar()
                0x7F -> ' '
                0xE0 -> '\''
                0xE3 -> '-'
                0xE6 -> '?'
                0xE7 -> '!'
                0xE8 -> '.'
                0xE9 -> '&'
                0xEA -> 'é'
                0xF2 -> '.'
                else -> null
            }
        }

        val gbaEnglish = PokemonTextCodec("gba-english", 0xFF) { value ->
            when (value) {
                0x00 -> ' '
                in 0xA1..0xAA -> ('0'.code + value - 0xA1).toChar()
                in 0xBB..0xD4 -> ('A'.code + value - 0xBB).toChar()
                in 0xD5..0xEE -> ('a'.code + value - 0xD5).toChar()
                0xAB -> '!'
                0xAC -> '?'
                0xAD -> '.'
                0xAE -> '-'
                0xB0 -> '.'
                0xB4 -> '\''
                0xB5 -> '\''
                0xB8 -> ','
                0xB9 -> '×'
                0xBA -> '/'
                else -> null
            }
        }
    }
}
