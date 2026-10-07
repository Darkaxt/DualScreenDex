package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.ExtentCheck
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionRowOutcome
import com.enrpau.dualscreendex.parser.text.NativeGbaDescriptionCategory
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.defaultTextCodec
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextToken
import java.util.Collections

/** Strict category cells from selected physical rows; species aliases remain a separate join. */
internal object SpeciesCategoryMaterializer {
    fun materialize(
        rom: RomImage,
        layout: ResolvedRomLayout,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
        limits: ResolutionLimits = ResolutionLimits(),
    ): Map<Int, String> {
        cancellation.throwIfCancellationRequested()
        if (layout.generation != 3 || layout.platform != Platform.GBA ||
            layout.pokeemeraldExpansion != null || layout.headerlessUnifiedSpecies != null) return emptyMap()
        val resolved = layout.resolvedDatasets.descriptions ?: return emptyMap()
        val selected = layout.tables.descriptions ?: return emptyMap()
        val table = resolved.table
        if (table.offset != selected.offset.toLong() || table.count != selected.count.toLong() ||
            table.recordSize != selected.recordSize || table.pointerOffsets != selected.pointerOffsets ||
            selected.variableLength || selected.valuesArePointers ||
            (selected.stride ?: selected.recordSize) != selected.recordSize) return emptyMap()
        val width = when {
            table.recordSize == 28 && table.pointerOffsets == listOf(12) -> 6
            table.recordSize == 32 && table.pointerOffsets == listOf(16) -> 12
            table.recordSize == 36 && table.pointerOffsets in listOf(listOf(16), listOf(16, 20)) -> 12
            table.expandedCategory -> 14
            else -> return emptyMap()
        }
        if (table.count * width > limits.maxProbeWorkPerDataset.toLong() ||
            limits.checkTableExtent(table.offset, table.count, table.recordSize.toLong(), rom.size.toLong()) !is ExtentCheck.Valid) {
            return emptyMap()
        }
        val codec = layout.defaultTextCodec()?.takeIf { it.supports(3, Platform.GBA) } ?: return emptyMap()
        val categories = linkedMapOf<Int, String>()
        resolved.rows.forEach { row ->
            cancellation.throwIfCancellationRequested()
            if (row !is DescriptionRowOutcome.Decoded) return@forEach
            val start = (table.offset + row.rowIndex.toLong() * table.recordSize).toInt()
            val category = if (width == 6) NativeGbaDescriptionCategory.decode(rom, start, codec, cancellation)
                else decodeInline(rom, start, width, codec, cancellation)
            if (category != null && category == row.category) categories[row.rowIndex] = category
        }
        cancellation.throwIfCancellationRequested()
        return Collections.unmodifiableMap(categories)
    }

    private fun decodeInline(
        rom: RomImage,
        start: Int,
        width: Int,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
    ): String? {
        if (codec.terminator != 0xFF) return null
        val text = StringBuilder()
        val end = start + width
        var offset = start
        while (offset < end) {
            cancellation.throwIfCancellationRequested()
            val token = codec.decodeToken(rom, offset, end)
            when (token) {
                is PokemonTextToken.Terminator -> return text.toString().trim().takeIf(String::isNotBlank)
                is PokemonTextToken.Glyph -> text.append(token.text)
                is PokemonTextToken.Whitespace -> text.append(token.text)
                else -> return null
            }
            offset += token.byteCount
        }
        return null
    }
}
