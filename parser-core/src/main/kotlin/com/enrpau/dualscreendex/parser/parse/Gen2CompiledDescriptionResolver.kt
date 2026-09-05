package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.GbDescriptionSegment
import com.enrpau.dualscreendex.parser.model.GbInlineDescriptionLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.GbInlineDescriptions
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

/** Resolves native dex pointer segmentation from the complete compiled index/bank consumer. */
internal object Gen2CompiledDescriptionResolver {
    fun resolve(session: RomAnalysisSession, count: Int, codec: PokemonTextCodec): TableLayout? {
        session.cancellation.throwIfCancellationRequested()
        if (count !in 1..255 || count * 2L > session.limits.maxDatasetExtentBytes ||
            codec.language !in setOf(LanguageTag.JAPANESE, LanguageTag.KOREAN)) return null
        val rom = session.rom
        val roots = linkedSetOf<GbInlineDescriptionLayout>()
        fun matches(offset: Int, vararg pattern: Int): Boolean = pattern.indices.all {
            pattern[it] < 0 || rom.u8(offset + it) == pattern[it]
        }
        for (offset in 0..rom.size - 24) {
            if (offset % 4096 == 0) session.cancellation.throwIfCancellationRequested()
            val bank = offset / 0x4000
            if (bank == 0 || offset / 0x4000 != (offset + 23) / 0x4000) continue
            fun root(at: Int): Int? = rom.u16le(offset + at).takeIf { it in 0x4000..0x7fff }
                ?.let { rom.gbBankAddress(bank, it) }
            val candidate = when {
                codec.language == LanguageTag.JAPANESE && matches(offset,
                    0x21,-1,-1,0xfa,-1,-1,0xfe,-1,0x38,5,0xd6,-1,0x21,-1,-1,
                    0x3d,0x5f,0x16,0,0x19,0x19,0x5e,0x23,0x56) -> {
                    val split = rom.u8(offset + 11)
                    val first = root(1)
                    val second = root(13)
                    if (rom.u16le(offset + 4) !in 0xc000..0xdfff || split !in 1 until count ||
                        rom.u8(offset + 7) != split + 1 || first == null || second == null || first == second) null
                    else GbInlineDescriptionLayout(GbDescriptionSegment(first, split, bank),
                        GbDescriptionSegment(second, count - split, bank))
                }
                codec.language == LanguageTag.KOREAN && matches(offset,
                    0x21,-1,-1,0x78,0x3d,0x06,0,0x4f,0x09,0x09,0x07,0xe6,1,0xc6,-1,
                    0x47,0x2a,0x66,0x6f,0xc9) -> root(1)?.let {
                    GbInlineDescriptionLayout(GbDescriptionSegment(it, count, rom.u8(offset + 14), 128))
                }
                else -> null
            }
            if (candidate != null) roots += candidate
            if (roots.size > session.limits.maxCandidatesPerDataset) return null
        }
        var selected: TableLayout? = null
        var work = 0
        for (candidate in roots) {
            session.cancellation.throwIfCancellationRequested()
            val entries = GbInlineDescriptions.entries(rom, candidate)
            work += entries.size
            if (work > session.limits.maxProbeWorkPerDataset) return null
            val valid = entries.count { it != null && GbInlineDescriptions.decode(rom, it, codec, session.cancellation) != null }
            if (valid < kotlin.math.ceil(count * 0.75).toInt()) continue
            if (selected != null) return null
            selected = TableLayout(candidate.first.pointerTableOffset, count, 2, gbDescriptions = candidate)
        }
        return selected
    }
}
