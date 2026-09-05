package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.LanguageTextPlausibility
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

/** The GBA cartridge header after the entry branch is data, never Thumb consumers. */
internal fun executableGbaTextSites(index: GbaReferenceIndex, root: Int): List<Int>? {
    if (index.overflowed) return null
    val evidence = index.target(root) ?: return null
    if (!evidence.siteEvidenceAvailable || evidence.count != evidence.instructionSites.size) return null
    return evidence.instructionSites.filterNot { it in 4 until 0xC0 }.takeIf { it.isNotEmpty() }
}

/** A root and inline stride proven by every executable consumer, not inferred from language. */
internal data class CompiledInlineAbilityText(val offset: Int, val stride: Int) {
    fun decode(
        rom: RomImage,
        count: Int,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
        extentLimit: Long = ResolutionLimits().maxDatasetExtentBytes,
    ): Map<Int, String>? {
        if (count < 2 || offset < 0 || count.toLong() * stride > extentLimit ||
            offset.toLong() + count.toLong() * stride > rom.size.toLong()
        ) return null
        val result = linkedMapOf<Int, String>()
        repeat(count) { id ->
            cancellation.throwIfCancellationRequested()
            val record = offset + id * stride
            val decoded = codec.decodeDetailed(rom, record, stride, cancellation)
            if (!decoded.terminated || decoded.invalidUnits != 0 || decoded.text.isBlank() ||
                (decoded.consumedBytes until stride).any { rom.u8(record + it) != 0 }
            ) return null
            val text = decoded.text.replace(Regex("\\s+"), " ").trim()
            if (id > 0) {
                // Compiled inline records include concise native prose (for example, five-glyph sentences).
                // The proven ABI, exact terminator and padding remain mandatory; Western pointer prose is unchanged.
                if (!LanguageTextPlausibility.looksLikeNaturalDescription(text, codec.language, 5, 2)) return null
                result[id] = text
            }
        }
        return result
    }
}

internal fun compiledInlineAbilityTexts(
    rom: RomImage,
    index: GbaReferenceIndex,
    cancellation: ParserCancellationToken,
): List<CompiledInlineAbilityText> = index.targets.keys.mapNotNull { root ->
    cancellation.throwIfCancellationRequested()
    val sites = executableGbaTextSites(index, root) ?: return@mapNotNull null
    val strides = sites.map { site -> inlineTextStride(rom, site) ?: return@mapNotNull null }
    strides.distinct().singleOrNull()?.let { CompiledInlineAbilityText(root, it) }
}

internal fun compiledInlineAbilityNameCount(
    session: com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession,
    names: Int,
    width: Int,
    maximumDirectId: Int,
    codec: PokemonTextCodec,
    descriptions: List<CompiledInlineAbilityText>,
): Int? {
    if (names < 0 || width <= 0 || maximumDirectId <= 0) return null
    return descriptions.mapNotNull { description ->
        session.cancellation.throwIfCancellationRequested()
        val bytes = description.offset.toLong() - names
        if (bytes <= 0 || bytes % width != 0L || bytes > session.limits.maxDatasetExtentBytes) return@mapNotNull null
        val count = bytes / width
        if (count !in (maximumDirectId.toLong() + 1)..512L) return@mapNotNull null
        description.decode(session.rom, count.toInt(), codec, session.cancellation, session.limits.maxDatasetExtentBytes)
            ?: return@mapNotNull null
        count.toInt()
    }.singleOrNull()
}

private fun inlineTextStride(rom: RomImage, site: Int): Int? {
    if (site < 8 || site.toLong() + 4 > rom.size) return null
    val first = rom.u16le(site - 8)
    val sum = rom.u16le(site - 6)
    val second = rom.u16le(site - 4)
    val subtract = rom.u16le(site - 2)
    val root = rom.u16le(site)
    val address = rom.u16le(site + 2)
    if (first and 0xF800 != 0 || second and 0xF800 != 0 ||
        sum and 0xFE00 != 0x1800 || subtract and 0xFE00 != 0x1A00 ||
        root and 0xF800 != 0x4800 || address and 0xFE00 != 0x1800
    ) return null
    val original = (first ushr 3) and 7
    val product = first and 7
    val base = (root ushr 8) and 7
    if (original == product || base == product ||
        sum and 7 != product || setOf((sum ushr 3) and 7, (sum ushr 6) and 7) != setOf(original, product) ||
        second and 7 != product || (second ushr 3) and 7 != product ||
        subtract and 7 != product || (subtract ushr 3) and 7 != product || (subtract ushr 6) and 7 != original ||
        setOf((address ushr 3) and 7, (address ushr 6) and 7) != setOf(base, product)
    ) return null
    val firstShift = (first ushr 6) and 31
    val secondShift = (second ushr 6) and 31
    if (firstShift !in 1..5 || secondShift !in 1..5) return null
    return (((1 shl firstShift) + 1) * (1 shl secondShift) - 1).takeIf { it in 8..192 }
}
