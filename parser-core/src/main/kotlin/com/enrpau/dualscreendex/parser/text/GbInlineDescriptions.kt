package com.enrpau.dualscreendex.parser.text

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.GbInlineDescriptionLayout

/** Native GB dex fields: terminated category, u8 height, u16 weight, inline prose. */
internal object GbInlineDescriptions {
    data class Entry(val offset: Int, val endExclusive: Int)
    data class Row(val category: String, val height: Int, val weight: Int, val text: String)

    fun entries(rom: RomImage, layout: GbInlineDescriptionLayout): List<Entry?> {
        val segments = listOfNotNull(layout.first, layout.second)
        if (segments.any { it.count !in 1..255 || it.entriesPerBank !in 1..255 ||
                it.entryBank <= 0 || it.pointerTableOffset < 0 ||
                it.pointerTableOffset.toLong() + it.count * 2L > rom.size ||
                it.pointerTableOffset / 0x4000 != (it.pointerTableOffset + it.count * 2 - 1) / 0x4000 } ||
            segments.sumOf { it.count } > 255) return emptyList()
        val offsets = segments.flatMap { segment ->
            (0 until segment.count).map { index ->
                val address = rom.u16le(segment.pointerTableOffset + index * 2)
                if (address !in 0x4000..0x7fff) null else
                    rom.gbBankAddress(segment.entryBank + index / segment.entriesPerBank, address)
            }
        }
        val boundaries = (offsets.filterNotNull() + segments.map { it.pointerTableOffset }).distinct().sorted()
        return offsets.map { offset -> offset?.let {
            Entry(it, minOf(rom.size, (it / 0x4000 + 1) * 0x4000,
                boundaries.firstOrNull { boundary -> boundary > it } ?: rom.size))
        } }
    }

    fun decode(
        rom: RomImage,
        entry: Entry,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): Row? {
        cancellation.throwIfCancellationRequested()
        if (codec.language !in setOf(LanguageTag.JAPANESE, LanguageTag.KOREAN) ||
            entry.offset < 0 || entry.endExclusive !in entry.offset + 1..rom.size) return null
        val category = codec.decodeDetailed(rom, entry.offset, minOf(24, entry.endExclusive - entry.offset), cancellation)
        if (!category.terminated || category.invalidUnits != 0 || category.text.isBlank()) return null
        val metadata = entry.offset + category.consumedBytes
        if (metadata + 3 >= entry.endExclusive) return null
        // This context changes DEXEND only at token boundaries, never inside a Korean pair.
        val proseCodec = if (codec.language == LanguageTag.JAPANESE) PokemonTextCodec(
            id = "${codec.id}-dex-inline", version = codec.version, language = codec.language,
            applicableGenerations = codec.applicableGenerations, applicablePlatforms = codec.applicablePlatforms,
            terminator = codec.terminator,
            tokenDecoder = PokemonTextTokenDecoder { image, offset, end ->
                if (image.u8(offset) == 0x5f) PokemonTextToken.Terminator() else codec.decodeToken(image, offset, end)
            },
        ) else codec
        val text = proseCodec.decodeDetailed(rom, metadata + 3,
            minOf(512, entry.endExclusive - metadata - 3), cancellation)
        if (!text.terminated || text.invalidUnits != 0 || text.text.isBlank()) return null
        return Row(category.text, rom.u8(metadata), rom.u16le(metadata + 1), text.text)
    }
}
