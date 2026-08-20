package com.darkaxt.dualdex.save.gen3

enum class Gen3TextEncoding { ENGLISH }

internal object Gen3SaveTextCodec {
    fun decode(bytes: ByteArray, encoding: Gen3TextEncoding?): String? {
        if (encoding == null) return null
        val output = StringBuilder()
        var terminated = false
        for (raw in bytes) {
            val value = raw.toInt() and 0xFF
            if (value == TERMINATOR) {
                terminated = true
                break
            }
            output.append(decodeEnglish(value) ?: return null)
        }
        return output.toString().trim().takeIf { terminated && it.isNotEmpty() }
    }

    private fun decodeEnglish(value: Int): Char? = when (value) {
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
        0x2A -> 'º'
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
        0xAB -> '!'
        0xAC -> '?'
        0xAD -> '.'
        0xAE -> '-'
        0xAF -> '·'
        0xB0 -> '…'
        0xB1 -> '“'
        0xB2 -> '”'
        0xB4 -> '\''
        0xB5 -> '♂'
        0xB6 -> '♀'
        0xB7 -> '¥'
        0xB8 -> ','
        0xB9 -> '×'
        0xBA -> '/'
        in 0xBB..0xD4 -> ('A'.code + value - 0xBB).toChar()
        in 0xD5..0xEE -> ('a'.code + value - 0xD5).toChar()
        0xF0 -> ':'
        0xFA, 0xFB, 0xFE -> ' '
        else -> null
    }

    private const val TERMINATOR = 0xFF
}
