package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
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
            codec.language !in SUPPORTED_LANGUAGES || session.rom.size < MIN_CONSUMER_BYTES) return null
        val rom = session.rom
        val candidates = linkedSetOf<TableLayout>()
        for (offset in 0..rom.size - MIN_CONSUMER_BYTES) {
            if (offset % 4096 == 0) session.cancellation.throwIfCancellationRequested()
            val bank = offset / 0x4000
            if (bank == 0) continue
            fun root(at: Int): Int? = rom.u16le(offset + at).takeIf { it in 0x4000..0x7fff }
                ?.let { rom.gbBankAddress(bank, it) }
            val candidate = when {
                codec.language in WESTERN_LANGUAGES && matches(rom, offset, GOLD_SILVER_CONSUMER) -> {
                    val pointerTable = root(2)
                    val metadataBytes = metadataBytesForConsumer(rom, offset)
                    if (pointerTable == null || metadataBytes == null) null else {
                        val bankCount = (count + ENTRIES_PER_BANK - 1) / ENTRIES_PER_BANK
                        val firstBank = rom.u8(offset + 19)
                        TableLayout(
                            pointerTable,
                            count,
                            2,
                            banks = List(bankCount) { firstBank + it },
                            gbDescriptionMetadataBytes = metadataBytes,
                        )
                    }
                }
                codec.language in WESTERN_LANGUAGES && matches(rom, offset, CRYSTAL_CONSUMER) -> {
                    val pointerTable = root(2)
                    val bankTable = root(20)
                    val bankCount = (count + ENTRIES_PER_BANK - 1) / ENTRIES_PER_BANK
                    val metadataBytes = metadataBytesForConsumer(rom, offset)
                    if (pointerTable == null || bankTable == null || metadataBytes == null ||
                        bankTable.toLong() + bankCount > rom.size.toLong()) null
                    else TableLayout(
                        pointerTable,
                        count,
                        2,
                        banks = List(bankCount) { rom.u8(bankTable + it) },
                        gbDescriptionMetadataBytes = metadataBytes,
                    )
                }
                codec.language == LanguageTag.JAPANESE && matches(rom, offset, JAPANESE_CONSUMER) -> {
                    val split = rom.u8(offset + 11)
                    val first = root(1)
                    val second = root(13)
                    if (rom.u16le(offset + 4) !in 0xc000..0xdfff || split !in 1 until count ||
                        rom.u8(offset + 7) != split + 1 || first == null || second == null || first == second) null
                    else TableLayout(
                        first,
                        count,
                        2,
                        gbDescriptions = GbInlineDescriptionLayout(
                            GbDescriptionSegment(first, split, bank),
                            GbDescriptionSegment(second, count - split, bank),
                        ),
                    )
                }
                codec.language == LanguageTag.KOREAN && matches(rom, offset, KOREAN_CONSUMER) -> {
                    root(1)?.let {
                        TableLayout(
                            it,
                            count,
                            2,
                            gbDescriptions = GbInlineDescriptionLayout(
                                GbDescriptionSegment(it, count, rom.u8(offset + 14), 128),
                            ),
                        )
                    }
                }
                else -> null
            }
            if (candidate != null) candidates += candidate
            if (candidates.size > session.limits.maxCandidatesPerDataset) return null
        }
        var selected: TableLayout? = null
        var work = 0
        for (candidate in candidates) {
            session.cancellation.throwIfCancellationRequested()
            val valid = validDescriptionCount(rom, candidate, codec, session)
            work += count
            if (work > session.limits.maxProbeWorkPerDataset) return null
            if (valid < kotlin.math.ceil(count * 0.75).toInt()) continue
            if (selected != null) return null
            selected = candidate
        }
        return selected
    }

    private fun metadataBytesForConsumer(rom: RomImage, functionOffset: Int): Int? {
        // GetDexEntryPagePointer calls this root consumer, then advances past the native metadata fields.
        val functionAddress = 0x4000 + functionOffset % 0x4000
        val callPattern = PAGE_CONSUMER_PREFIX.copyOf().also {
            it[1] = functionAddress and 0xff
            it[2] = functionAddress ushr 8
        }
        val bankStart = functionOffset / 0x4000 * 0x4000
        val bankEnd = minOf(rom.size, bankStart + 0x4000)
        val widths = linkedSetOf<Int>()
        for (offset in bankStart until bankEnd - callPattern.size) {
            if (!matches(rom, offset, callPattern)) continue
            var cursor = offset + callPattern.size
            while (cursor < bankEnd && rom.u8(cursor) == 0x23) cursor++
            val width = cursor - offset - callPattern.size
            if (width in 3..4 && matches(rom, cursor, PAGE_CONSUMER_SUFFIX)) widths += width
        }
        return widths.singleOrNull()
    }

    private fun validDescriptionCount(
        rom: RomImage,
        table: TableLayout,
        codec: PokemonTextCodec,
        session: RomAnalysisSession,
    ): Int {
        table.gbDescriptions?.let { inline ->
            val entries = GbInlineDescriptions.entries(rom, inline)
            return entries.count {
                it != null && GbInlineDescriptions.decode(rom, it, codec, session.cancellation) != null
            }
        }
        if (table.banks.size != (table.count + ENTRIES_PER_BANK - 1) / ENTRIES_PER_BANK ||
            table.banks.any { it <= 0 } || table.offset < 0 ||
            table.offset.toLong() + table.count * 2L > rom.size.toLong() ||
            table.offset / 0x4000 != (table.offset + table.count * 2 - 1) / 0x4000) return 0
        return (0 until table.count).count { index ->
            session.cancellation.throwIfCancellationRequested()
            val address = rom.u16le(table.offset + index * 2)
            val bank = table.banks[index / ENTRIES_PER_BANK]
            val entry = rom.gbBankAddress(bank, address) ?: return@count false
            val bankEnd = minOf(rom.size, (entry / 0x4000 + 1) * 0x4000)
            val category = codec.decodeDetailed(
                rom,
                entry,
                minOf(MAX_CATEGORY_BYTES, bankEnd - entry),
                session.cancellation,
            )
            if (!category.terminated || category.invalidUnits != 0 || category.text.isBlank()) return@count false
            val textOffset = entry + category.consumedBytes + table.gbDescriptionMetadataBytes
            if (textOffset >= bankEnd) return@count false
            val text = codec.decodeDetailed(
                rom,
                textOffset,
                minOf(MAX_DESCRIPTION_BYTES, bankEnd - textOffset),
                session.cancellation,
            )
            text.terminated && text.invalidUnits == 0 && text.text.isNotBlank()
        }
    }

    private fun matches(rom: RomImage, offset: Int, pattern: IntArray): Boolean =
        offset >= 0 && offset.toLong() + pattern.size <= rom.size.toLong() && pattern.indices.all {
            pattern[it] < 0 || rom.u8(offset + it) == pattern[it]
        } && offset / 0x4000 == (offset + pattern.size - 1) / 0x4000

    private val WESTERN_LANGUAGES = setOf(
        LanguageTag.ENGLISH,
        LanguageTag.FRENCH,
        LanguageTag.GERMAN,
        LanguageTag.ITALIAN,
        LanguageTag.SPANISH,
    )
    private val SUPPORTED_LANGUAGES = WESTERN_LANGUAGES + LanguageTag.JAPANESE + LanguageTag.KOREAN

    private val GOLD_SILVER_CONSUMER = intArrayOf(
        0xe5, 0x21, -1, -1, 0x78, 0x3d, 0x16, 0x00, 0x5f, 0x19, 0x19, 0x5e, 0x23, 0x56,
        0x07, 0x07, 0xe6, 0x03, 0xc6, -1, 0x47, 0xe1, 0xc9,
    )
    private val CRYSTAL_CONSUMER = intArrayOf(
        0xe5, 0x21, -1, -1, 0x78, 0x3d, 0x16, 0x00, 0x5f, 0x19, 0x19, 0x5e, 0x23, 0x56, 0xd5,
        0x07, 0x07, 0xe6, 0x03, 0x21, -1, -1, 0x16, 0x00, 0x5f, 0x19, 0x46, 0xd1, 0xe1, 0xc9,
    )
    private val PAGE_CONSUMER_PREFIX = intArrayOf(
        0xcd, -1, -1, 0xe5, 0x62, 0x6b, 0x78, 0xcd, -1, -1, 0x23, 0xfe, 0x50, 0x20, 0xf7,
    )
    private val PAGE_CONSUMER_SUFFIX = intArrayOf(0x0d, 0x28, 0x09)
    private val JAPANESE_CONSUMER = intArrayOf(
        0x21, -1, -1, 0xfa, -1, -1, 0xfe, -1, 0x38, 5, 0xd6, -1, 0x21, -1, -1,
        0x3d, 0x5f, 0x16, 0, 0x19, 0x19, 0x5e, 0x23, 0x56,
    )
    private val KOREAN_CONSUMER = intArrayOf(
        0x21, -1, -1, 0x78, 0x3d, 0x06, 0, 0x4f, 0x09, 0x09, 0x07, 0xe6, 1, 0xc6, -1,
        0x47, 0x2a, 0x66, 0x6f, 0xc9,
    )

    private const val ENTRIES_PER_BANK = 64
    private const val MAX_CATEGORY_BYTES = 24
    private const val MAX_DESCRIPTION_BYTES = 512
    private const val MIN_CONSUMER_BYTES = 20
}
