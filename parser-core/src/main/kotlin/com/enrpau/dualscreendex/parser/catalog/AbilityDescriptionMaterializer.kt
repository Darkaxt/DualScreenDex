package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import kotlin.math.abs

data class AbilityDescriptionResult(
    val sourceOffset: Int,
    val confidence: Double,
    val descriptions: Map<Int, String>,
)

object AbilityDescriptionMaterializer {
    private const val SEARCH_RADIUS = 0x10000

    fun materialize(rom: RomImage, layout: ResolvedRomLayout): AbilityDescriptionResult? {
        if (layout.generation != 3) return null
        val names = layout.tables.abilities ?: return null
        if (names.count < 2) return null
        val tableBytes = names.count * 4
        val expectedOffset = align4(names.offset + names.count * names.recordSize)
        val searchStart = align4(maxOf(0, names.offset - SEARCH_RADIUS))
        val searchEnd = minOf(rom.size - tableBytes, expectedOffset + SEARCH_RADIUS)
        if (searchEnd < searchStart) return null

        return generateSequence(searchStart) { current -> (current + 4).takeIf { it <= searchEnd } }
            .mapNotNull { offset -> decodeCandidate(rom, offset, names.count) }
            .maxWithOrNull(
                compareBy<AbilityDescriptionResult> { it.confidence }
                    .thenByDescending { abs(it.sourceOffset - expectedOffset) },
            )
    }

    private fun decodeCandidate(rom: RomImage, offset: Int, count: Int): AbilityDescriptionResult? {
        val codec = PokemonTextCodec.gbaEnglish
        val noneOffset = runCatching { rom.gbaPointer(offset) }.getOrNull() ?: return null
        val noneLength = minOf(64, rom.size - noneOffset)
        val none = runCatching { codec.decodeDetailed(rom.slice(noneOffset, noneLength)) }.getOrNull() ?: return null
        val validNone = none.text.isBlank() || (none.validRatio >= 0.85 && none.text.length >= 5)
        if (!none.terminated || !validNone) return null

        val descriptions = linkedMapOf<Int, String>()
        repeat(count - 1) { index ->
            val id = index + 1
            val textOffset = runCatching { rom.gbaPointer(offset + id * 4) }.getOrNull() ?: return@repeat
            val length = minOf(192, rom.size - textOffset)
            val decoded = runCatching { codec.decodeDetailed(rom.slice(textOffset, length)) }.getOrNull() ?: return@repeat
            val normalized = decoded.text.replace(Regex("\\s+"), " ").trim()
            if (decoded.terminated && decoded.validRatio >= 0.85 && normalized.length >= 5) {
                descriptions[id] = normalized
            }
        }
        val expectedDescriptions = count - 1
        val confidence = descriptions.size.toDouble() / expectedDescriptions
        val minimum = maxOf(2, (expectedDescriptions * 0.8).toInt())
        return if (descriptions.size >= minimum) AbilityDescriptionResult(offset, confidence, descriptions) else null
    }

    private fun align4(value: Int): Int = (value + 3) and 3.inv()
}
