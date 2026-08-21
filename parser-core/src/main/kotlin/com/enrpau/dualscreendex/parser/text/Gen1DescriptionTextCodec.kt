package com.enrpau.dualscreendex.parser.text

import com.enrpau.dualscreendex.parser.io.RomImage

/** Decodes Gen I far-text payloads terminated by either text terminator or the `done` command. */
internal object Gen1DescriptionTextCodec {
    fun decodeDetailed(
        rom: RomImage,
        offset: Int,
        maximumLength: Int,
    ): DecodedText? {
        if (offset !in 0 until rom.size || maximumLength <= 0) return null
        val available = minOf(maximumLength, rom.size - offset)
        val end = (0 until available).firstOrNull { index ->
            rom.u8(offset + index) in TERMINATORS
        } ?: return null
        val normalized = ByteArray(end + 1)
        var length = 0
        for (index in 0..end) {
            val value = rom.u8(offset + index)
            when {
                index == 0 && value == TEXT_COMMAND -> Unit
                value == PAGE_COMMAND -> normalized[length++] = SPACE
                value == DONE_COMMAND -> normalized[length++] = PokemonTextCodec.gbEnglish.terminator.toByte()
                else -> normalized[length++] = value.toByte()
            }
        }
        return PokemonTextCodec.gbEnglish.decodeDetailed(normalized.copyOf(length))
            .takeIf { it.terminated && it.text.isNotBlank() }
    }

    fun decode(rom: RomImage, offset: Int, maximumLength: Int): String? =
        decodeDetailed(rom, offset, maximumLength)?.text

    private val TERMINATORS = setOf(PokemonTextCodec.gbEnglish.terminator, DONE_COMMAND)
    private const val TEXT_COMMAND = 0x00
    private const val PAGE_COMMAND = 0x49
    private const val DONE_COMMAND = 0x57
    private const val SPACE: Byte = 0x7F
}
