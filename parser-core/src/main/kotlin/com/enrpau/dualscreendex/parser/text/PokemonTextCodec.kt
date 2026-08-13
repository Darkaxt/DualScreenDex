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
    internal fun decodeByte(value: Int): Char? = decoder(value and 0xff)

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
        return DecodedText(output.toString().replace(WHITESPACE, " ").trim(), terminated, valid, content)
    }

    companion object {
        val gbEnglish = PokemonTextCodec("gb-english", 0x50) { value ->
            when (value) {
                in 0x80..0x99 -> ('A'.code + value - 0x80).toChar()
                in 0xA0..0xB9 -> ('a'.code + value - 0xA0).toChar()
                in 0xF6..0xFF -> ('0'.code + value - 0xF6).toChar()
                0x7F -> ' '
                in 0x4B..0x4F -> ' '
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
                0x01 -> 'À'
                0x02 -> 'Á'
                0x03 -> 'Â'
                0x04 -> 'Ç'
                0x05 -> 'È'
                0x06 -> 'É'
                0x07 -> 'Ê'
                0x08 -> 'Ë'
                0x09 -> 'Ì'
                0x0B -> 'Î'
                0x0C -> 'Ï'
                0x0D -> 'Ò'
                0x0E -> 'Ó'
                0x0F -> 'Ô'
                0x10 -> 'Œ'
                0x11 -> 'Ù'
                0x12 -> 'Ú'
                0x13 -> 'Û'
                0x14 -> 'Ñ'
                0x15 -> 'ß'
                0x16 -> 'à'
                0x17 -> 'á'
                0x19 -> 'ç'
                0x1A -> 'è'
                0x1B -> 'é'
                0x1C -> 'ê'
                0x1D -> 'ë'
                0x1E -> 'ì'
                0x20 -> 'î'
                0x21 -> 'ï'
                0x22 -> 'ò'
                0x23 -> 'ó'
                0x24 -> 'ô'
                0x25 -> 'œ'
                0x26 -> 'ù'
                0x27 -> 'ú'
                0x28 -> 'û'
                0x29 -> 'ñ'
                0x2D -> '&'
                0x2E -> '+'
                0x35 -> '='
                0x36 -> ';'
                0x51 -> '¿'
                0x52 -> '¡'
                0x5B -> '%'
                0x5C -> '('
                0x5D -> ')'
                in 0xA1..0xAA -> ('0'.code + value - 0xA1).toChar()
                in 0xBB..0xD4 -> ('A'.code + value - 0xBB).toChar()
                in 0xD5..0xEE -> ('a'.code + value - 0xD5).toChar()
                0xAB -> '!'
                0xAC -> '?'
                0xAD -> '.'
                0xAE -> '-'
                0xAF -> '·'
                0xB0 -> '…'
                0xB4 -> '\''
                0xB5 -> '♂'
                0xB6 -> '♀'
                0xB7 -> '¥'
                0xB8 -> ','
                0xB9 -> '×'
                0xBA -> '/'
                0xFA, 0xFB, 0xFE -> ' '
                else -> null
            }
        }

        private val WHITESPACE = Regex("\\s+")
    }
}
