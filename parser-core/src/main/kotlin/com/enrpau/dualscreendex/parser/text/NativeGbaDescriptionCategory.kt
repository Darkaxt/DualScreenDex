package com.enrpau.dualscreendex.parser.text

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageTag

/** The native 28-byte dex ABI's six-byte category is zero-terminated, not GBA EOS text. */
internal object NativeGbaDescriptionCategory {
    fun decode(rom: RomImage, offset: Int, codec: PokemonTextCodec,
               cancellation: ParserCancellationToken = ParserCancellationToken.NONE): String? {
        if (codec.language != LanguageTag.JAPANESE || offset < 0 || offset.toLong() + 6 > rom.size) return null
        val text = StringBuilder()
        for (index in 0..5) {
            cancellation.throwIfCancellationRequested()
            if (rom.u8(offset + index) == 0) return text.toString().takeIf { it.isNotBlank() }
            if (index == 5) return null
            val token = codec.decodeToken(rom, offset + index, offset + 6)
            if (token !is PokemonTextToken.Glyph || token.byteCount != 1) return null
            text.append(token.text)
        }
        return null
    }
}
