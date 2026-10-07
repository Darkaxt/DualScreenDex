package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.TypeSemanticRole
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

internal data class DecodedTypeName(
    val name: String,
    val semanticRole: TypeSemanticRole?,
)

/** Resolves type labels only when a complete localized semantic domain has compiled-ROM evidence. */
internal object CompiledTypeNameResolver {
    fun resolve(
        session: RomAnalysisSession,
        generation: Int,
        codec: PokemonTextCodec,
    ): TableLayout? {
        if (!codec.supports(generation, session.header.platform)) return null
        val candidates = when (session.header.platform) {
            Platform.GB, Platform.GBC -> gbCandidates(session, generation, codec)
            Platform.GBA -> gbaCandidates(session, generation, codec)
            Platform.UNKNOWN -> emptyList()
        }
        return candidates.distinct().singleOrNull()
    }

    fun decode(
        rom: RomImage,
        generation: Int,
        layout: TableLayout,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): Map<Int, DecodedTypeName>? {
        val compactGen2 = layout.format == TableRecordFormat.GEN2_COMPACT_TYPE_NAMES
        val extendedGen2 = layout.format == TableRecordFormat.GEN2_EXTENDED_TYPE_NAMES
        val referencedGba = layout.format == TableRecordFormat.GBA_REFERENCED_TYPE_NAMES
        if (referencedGba && (generation != 3 || layout.count !in GEN3_TYPE_COUNT + 1..STANDARD_ROLES.size ||
                layout.recordSize != GEN3_TYPE_NAME_WIDTH || layout.variableLength || layout.valuesArePointers ||
                (layout.stride ?: layout.recordSize) != GEN3_TYPE_NAME_WIDTH)
        ) return null
        if ((compactGen2 || extendedGen2) && generation != 2) return null
        if (extendedGen2 && (layout.count !in 29..64 || layout.recordSize != 2 ||
                !layout.valuesArePointers || layout.variableLength || layout.stride != null && layout.stride != 2)
        ) return null
        val requiredIds = when {
            compactGen2 -> (0 until COMPACT_GEN2_TYPE_COUNT).toSet()
            extendedGen2 -> (0 until layout.count).toSet() - (10..18).toSet()
            referencedGba -> (0 until layout.count).toSet()
            else -> requiredTypeIds(generation) ?: return null
        }
        val expectedRoles = if (compactGen2 || extendedGen2 || referencedGba) STANDARD_ROLES else expectedRoles(generation) ?: return null
        val expectedCount = if (extendedGen2 || referencedGba) layout.count else if (compactGen2) COMPACT_GEN2_TYPE_COUNT else typeCount(generation)
        if (layout.count != expectedCount || layout.offset < 0) return null
        if (compactGen2 || extendedGen2) {
            val bank = layout.bank ?: return null
            val width = if (layout.valuesArePointers) 2 else 1
            val end = layout.offset.toLong() + layout.count.toLong() * width
            if (layout.variableLength || bank <= 0 || layout.offset / GB_BANK_SIZE != bank || layout.recordSize != width ||
                layout.stride != null && layout.stride != width ||
                end > minOf((bank + 1L) * GB_BANK_SIZE, rom.size.toLong())
            ) return null
        }
        val decoded = requiredIds.mapNotNull { id ->
            cancellation.throwIfCancellationRequested()
            val name = decodeName(rom, layout, id, codec, cancellation) ?: return null
            val role = LocalizedTypeNameLexicon.role(codec.language, name)
            if (role == null && (!extendedGen2 || id < 28 || name.none(Char::isLetterOrDigit))) return null
            id to DecodedTypeName(name, role)
        }.toMap(linkedMapOf())
        return decoded.takeIf { values ->
            values.size == requiredIds.size &&
                values.values.mapNotNull(DecodedTypeName::semanticRole).toSet() == expectedRoles
        }
    }

    private fun gbCandidates(
        session: RomAnalysisSession,
        generation: Int,
        codec: PokemonTextCodec,
    ): List<TableLayout> {
        val rom = session.rom
        val count = typeCount(generation) ?: return emptyList()
        val candidates = linkedSetOf<TableLayout>()
        val extendedRoots = linkedSetOf<Int>()
        for (site in 0..rom.size - GB_CONSUMER_SIZE) {
            if (site % RomImage.DEFAULT_SCAN_CHECK_INTERVAL_BYTES == 0) {
                session.cancellation.throwIfCancellationRequested()
            }
            if (generation == 2 && codec.terminator == 0x53) {
                compactGen2Consumer(rom, site)?.let { layout ->
                    if (decode(rom, generation, layout, codec, session.cancellation) != null) {
                        candidates += layout
                    }
                }
                if (candidates.size > 1) return candidates.toList()
            }
            if (generation == 2 && codec.terminator == 0x50) {
                extendedGen2Consumer(rom, site)?.let { layout ->
                    if (decode(rom, generation, layout, codec, session.cancellation) != null) {
                        extendedRoots += layout.offset
                        candidates.removeAll { it.offset == layout.offset }
                        candidates += layout
                    }
                }
            }
            if (!isGbTypeNameConsumer(rom, site)) continue
            val bank = site / GB_BANK_SIZE
            val table = rom.gbBankAddress(bank, rom.u16le(site + 2)) ?: continue
            if (table in extendedRoots) continue
            val layout = TableLayout(
                offset = table,
                count = count,
                recordSize = 2,
                bank = bank,
                valuesArePointers = true,
            )
            if (decode(rom, generation, layout, codec, session.cancellation) != null) {
                candidates += layout
                if (candidates.size > 1) return candidates.toList()
            }
        }
        return candidates.toList()
    }

    private fun extendedGen2Consumer(rom: RomImage, site: Int): TableLayout? {
        val bank = site / GB_BANK_SIZE
        val bankEnd = minOf((bank + 1L) * GB_BANK_SIZE, rom.size.toLong())
        if (bank <= 0 || site.toLong() + 23 > bankEnd || rom.u8(site) != 0xFA ||
            rom.u16le(site + 1) !in 0xC000..0xDFFF || rom.u8(site + 3) != 0x21 ||
            !bytesAt(rom, site + 6, 0x5F, 0x16, 0, 0x19, 0x19, 0x2A, 0x66, 0x6F, 0x11) ||
            rom.u16le(site + 15) !in 0xC000..0xDFFF - 12 ||
            !bytesAt(rom, site + 17, 0x01, 13, 0, 0xC3) ||
            !GbCompiledBankCalls.byteCopy(rom, rom.u16le(site + 21))
        ) return null
        val root = rom.gbBankAddress(bank, rom.u16le(site + 4)) ?: return null
        if (root / GB_BANK_SIZE != bank || root.toLong() + 2 > bankEnd) return null
        val first = rom.gbBankAddress(bank, rom.u16le(root)) ?: return null
        val bytes = first - root
        if (bytes % 2 != 0 || bytes / 2 !in 29..64 || first >= bankEnd) return null
        val count = bytes / 2
        for (id in 0 until count) {
            val name = rom.gbBankAddress(bank, rom.u16le(root + id * 2)) ?: return null
            if (name !in first.toLong() until bankEnd) return null
        }
        return TableLayout(root, count, 2, bank = bank, valuesArePointers = true,
            format = TableRecordFormat.GEN2_EXTENDED_TYPE_NAMES)
    }

    private fun compactGen2Consumer(rom: RomImage, site: Int): TableLayout? {
        val bank = site / GB_BANK_SIZE
        val bankEnd = minOf((bank + 1L) * GB_BANK_SIZE, rom.size.toLong())
        if (bank <= 0 || site.toLong() + 20 > bankEnd || rom.u8(site) != 0xFA ||
            rom.u8(site + 3) != 0x21 ||
            !bytesAt(rom, site + 6, 0x5F, 0x16, 0, 0x19) ||
            rom.u16le(site + 1) !in 0xC000..0xDFFF
        ) return null
        val address = rom.u16le(site + 4).takeIf { it in 0x4000..0x7FFF } ?: return null
        val root = rom.gbBankAddress(bank, address) ?: return null
        val pointers: Boolean
        val destination: Int
        val copyOpcode: Int
        when {
            rom.u8(site + 10) == 0x5E && rom.u8(site + 11) == 0x19 &&
                rom.u8(site + 12) == 0x11 && rom.u8(site + 15) == 0x01 &&
                rom.u16le(site + 16) == 13 && rom.u8(site + 19) == 0xC9 -> {
                pointers = false
                destination = rom.u16le(site + 13)
                copyOpcode = rom.u8(site + 18)
            }
            site.toLong() + 22 <= bankEnd &&
                bytesAt(rom, site + 10, 0x19, 0x2A, 0x66, 0x6F, 0x11) &&
                rom.u8(site + 17) == 0x01 && rom.u16le(site + 18) == 13 &&
                rom.u8(site + 21) == 0xC9 -> {
                pointers = true
                destination = rom.u16le(site + 15)
                copyOpcode = rom.u8(site + 20)
            }
            else -> return null
        }
        if (destination !in 0xC000..0xDFFF - 12 || !GbCompiledBankCalls.restartCopy(rom, copyOpcode)) {
            return null
        }
        return TableLayout(
            root, COMPACT_GEN2_TYPE_COUNT, if (pointers) 2 else 1,
            bank = bank, valuesArePointers = pointers, format = TableRecordFormat.GEN2_COMPACT_TYPE_NAMES,
        )
    }

    private fun bytesAt(rom: RomImage, offset: Int, vararg values: Int): Boolean =
        offset >= 0 && offset.toLong() + values.size <= rom.size &&
            values.indices.all { rom.u8(offset + it) == values[it] }

    private fun gbaCandidates(
        session: RomAnalysisSession,
        generation: Int,
        codec: PokemonTextCodec,
    ): List<TableLayout> {
        if (generation != 3) return emptyList()
        val index = session.gbaReferenceIndex?.takeUnless { it.overflowed } ?: return emptyList()
        val candidates = linkedSetOf<TableLayout>()
        index.targets.forEach { (root, references) ->
            session.cancellation.throwIfCancellationRequested()
            val standard = TableLayout(root, GEN3_TYPE_COUNT, GEN3_TYPE_NAME_WIDTH)
            if (decode(session.rom, generation, standard, codec, session.cancellation) != null) {
                candidates += standard
            }
            val hasCompactConsumer = references.siteEvidenceAvailable && references.instructionSites.any { site ->
                session.cancellation.throwIfCancellationRequested()
                isGbaFiveByteTypeNameConsumer(session.rom, site, root)
            }
            if (hasCompactConsumer) {
                val compact = TableLayout(root, GEN3_TYPE_COUNT, GEN3_COMPACT_TYPE_NAME_WIDTH)
                if (decode(session.rom, generation, compact, codec, session.cancellation) != null) {
                    candidates += compact
                }
            }
            if (candidates.size > 1) return candidates.toList()
        }
        return candidates.toList()
    }

    /** Proves root + type * 5; readable five-byte rows or a bare literal load are insufficient. */
    private fun isGbaFiveByteTypeNameConsumer(rom: RomImage, site: Int, root: Int): Boolean {
        if (site < 4 || site % 2 != 0 || site.toLong() + 4 > rom.size.toLong()) return false
        val load = rom.u16le(site)
        if (load and 0xF800 != 0x4800) return false
        val literal = ((site.toLong() + 4) and -4L) + (load and 0xFF) * 4L
        if (literal !in 0..rom.size.toLong() - 4L ||
            rom.u32le(literal.toInt()) != 0x08000000L + root.toLong()
        ) return false

        val shift = rom.u16le(site - 4)
        if (shift and 0xF800 != 0 || (shift ushr 6) and 0x1F != 2) return false
        val typeRegister = (shift ushr 3) and 7
        val productRegister = shift and 7
        val rootRegister = (load ushr 8) and 7
        // The shift must preserve the original type, and the root load must preserve its product.
        if (productRegister == typeRegister || productRegister == rootRegister) return false
        return rom.u16le(site - 2) == thumbAdds(productRegister, productRegister, typeRegister) &&
            rom.u16le(site + 2) == thumbAdds(productRegister, productRegister, rootRegister)
    }

    private fun thumbAdds(destination: Int, left: Int, right: Int): Int =
        0x1800 or (right shl 6) or (left shl 3) or destination

    private fun decodeName(
        rom: RomImage,
        layout: TableLayout,
        id: Int,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
    ): String? {
        if (id !in 0 until layout.count) return null
        val offset: Int
        val maximumBytes: Int
        val gbNameLimit = if (layout.format in setOf(TableRecordFormat.GEN2_COMPACT_TYPE_NAMES,
                TableRecordFormat.GEN2_EXTENDED_TYPE_NAMES)) {
            COMPACT_GEN2_TYPE_NAME_BYTES
        } else {
            MAX_GB_TYPE_NAME_BYTES
        }
        if (layout.format == TableRecordFormat.GEN2_COMPACT_TYPE_NAMES && !layout.valuesArePointers) {
            val bank = layout.bank ?: return null
            val cell = layout.offset.toLong() + id
            val tableEnd = layout.offset.toLong() + layout.count
            val bankEnd = minOf((bank + 1L) * GB_BANK_SIZE, rom.size.toLong())
            if (cell !in 0 until tableEnd || cell >= bankEnd) return null
            val value = cell + rom.u8(cell.toInt())
            if (value !in tableEnd until bankEnd) return null
            offset = value.toInt()
            maximumBytes = minOf(gbNameLimit.toLong(), bankEnd - value).toInt()
        } else if (layout.valuesArePointers) {
            val bank = layout.bank ?: return null
            val pointerCell = layout.offset.toLong() + id.toLong() * layout.recordSize
            if (layout.recordSize != 2 || pointerCell !in 0..(rom.size - 2).toLong()) return null
            offset = rom.gbBankAddress(bank, rom.u16le(pointerCell.toInt())) ?: return null
            if (layout.format in setOf(TableRecordFormat.GEN2_COMPACT_TYPE_NAMES, TableRecordFormat.GEN2_EXTENDED_TYPE_NAMES) &&
                (offset / GB_BANK_SIZE != bank || offset.toLong() < layout.offset.toLong() + layout.count * 2L)
            ) return null
            maximumBytes = minOf(gbNameLimit, (bank + 1) * GB_BANK_SIZE - offset, rom.size - offset)
        } else {
            val stride = layout.stride ?: layout.recordSize
            val row = layout.offset.toLong() + id.toLong() * stride
            if (layout.recordSize <= 0 || stride < layout.recordSize || row !in 0..Int.MAX_VALUE.toLong() ||
                row + layout.recordSize > rom.size.toLong()
            ) return null
            offset = row.toInt()
            maximumBytes = layout.recordSize
        }
        if (maximumBytes <= 0) return null
        val decoded = codec.decodeDetailed(rom, offset, maximumBytes, cancellation)
        val name = decoded.text.trim()
        return name.takeIf {
            decoded.terminated && decoded.invalidUnits == 0 && it.isNotBlank()
        }
    }

    private fun isGbTypeNameConsumer(rom: RomImage, offset: Int): Boolean =
        rom.u8(offset) == 0x87 &&
            rom.u8(offset + 1) == 0x21 &&
            rom.u8(offset + 4) == 0x5F &&
            rom.u8(offset + 5) == 0x16 &&
            rom.u8(offset + 6) == 0x00 &&
            rom.u8(offset + 7) == 0x19 &&
            rom.u8(offset + 8) == 0x2A &&
            rom.u8(offset + 9) == 0x5F &&
            rom.u8(offset + 10) == 0x56

    private fun typeCount(generation: Int): Int? = when (generation) {
        1 -> 27
        2 -> 28
        3 -> GEN3_TYPE_COUNT
        else -> null
    }

    private fun requiredTypeIds(generation: Int): Set<Int>? = when (generation) {
        1 -> (0..5).toSet() + setOf(7, 8) + (20..26)
        2 -> (0..5).toSet() + (7..9) + (19..27)
        3 -> (0 until GEN3_TYPE_COUNT).toSet()
        else -> null
    }

    private fun expectedRoles(generation: Int): Set<TypeSemanticRole>? = when (generation) {
        1 -> STANDARD_ROLES - setOf(
            TypeSemanticRole.STEEL,
            TypeSemanticRole.MYSTERY,
            TypeSemanticRole.DARK,
            TypeSemanticRole.FAIRY,
        )
        2, 3 -> STANDARD_ROLES - TypeSemanticRole.FAIRY
        else -> null
    }

    private val STANDARD_ROLES = TypeSemanticRole.entries.toSet()
    private const val COMPACT_GEN2_TYPE_COUNT = 19
    private const val COMPACT_GEN2_TYPE_NAME_BYTES = 13
    private const val GB_BANK_SIZE = 0x4000
    private const val GB_CONSUMER_SIZE = 11
    private const val MAX_GB_TYPE_NAME_BYTES = 16
    private const val GEN3_TYPE_COUNT = 18
    private const val GEN3_TYPE_NAME_WIDTH = 7
    private const val GEN3_COMPACT_TYPE_NAME_WIDTH = 5
}

private object LocalizedTypeNameLexicon {
    fun role(language: LanguageTag, name: String): TypeSemanticRole? =
        entries[language]?.get(name.uppercase())

    private val entries = mapOf(
        LanguageTag.JAPANESE to roles(
            TypeSemanticRole.NORMAL to listOf("ノーマル"),
            TypeSemanticRole.FIGHTING to listOf("かくとう"),
            TypeSemanticRole.FLYING to listOf("ひこう"),
            TypeSemanticRole.POISON to listOf("どく"),
            TypeSemanticRole.GROUND to listOf("じめん"),
            TypeSemanticRole.ROCK to listOf("いわ"),
            TypeSemanticRole.BUG to listOf("むし"),
            TypeSemanticRole.GHOST to listOf("ゴースト"),
            TypeSemanticRole.STEEL to listOf("はがね"),
            TypeSemanticRole.MYSTERY to listOf("？？？"),
            TypeSemanticRole.FIRE to listOf("ほのお"),
            TypeSemanticRole.WATER to listOf("みず"),
            TypeSemanticRole.GRASS to listOf("くさ"),
            TypeSemanticRole.ELECTRIC to listOf("でんき"),
            TypeSemanticRole.PSYCHIC to listOf("エスパー"),
            TypeSemanticRole.ICE to listOf("こおり"),
            TypeSemanticRole.DRAGON to listOf("ドラゴン"),
            TypeSemanticRole.DARK to listOf("あく"),
        ),
        LanguageTag.KOREAN to roles(
            TypeSemanticRole.NORMAL to listOf("노말"),
            TypeSemanticRole.FIGHTING to listOf("격투"),
            TypeSemanticRole.FLYING to listOf("비행"),
            TypeSemanticRole.POISON to listOf("독"),
            TypeSemanticRole.GROUND to listOf("땅"),
            TypeSemanticRole.ROCK to listOf("바위"),
            TypeSemanticRole.BUG to listOf("벌레"),
            TypeSemanticRole.GHOST to listOf("고스트"),
            TypeSemanticRole.STEEL to listOf("강철"),
            TypeSemanticRole.MYSTERY to listOf("???"),
            TypeSemanticRole.FIRE to listOf("화염"),
            TypeSemanticRole.WATER to listOf("물"),
            TypeSemanticRole.GRASS to listOf("풀"),
            TypeSemanticRole.ELECTRIC to listOf("전기"),
            TypeSemanticRole.PSYCHIC to listOf("에스퍼"),
            TypeSemanticRole.ICE to listOf("얼음"),
            TypeSemanticRole.DRAGON to listOf("드래곤"),
            TypeSemanticRole.DARK to listOf("악"),
        ),
        LanguageTag.ENGLISH to roles(
            TypeSemanticRole.NORMAL to listOf("NORMAL"),
            TypeSemanticRole.FIGHTING to listOf("FIGHT", "FIGHTING"),
            TypeSemanticRole.FLYING to listOf("FLYING"),
            TypeSemanticRole.POISON to listOf("POISON"),
            TypeSemanticRole.GROUND to listOf("GROUND"),
            TypeSemanticRole.ROCK to listOf("ROCK"),
            TypeSemanticRole.BUG to listOf("BUG"),
            TypeSemanticRole.GHOST to listOf("GHOST"),
            TypeSemanticRole.STEEL to listOf("STEEL"),
            TypeSemanticRole.MYSTERY to listOf("???", "(?)"),
            TypeSemanticRole.FIRE to listOf("FIRE"),
            TypeSemanticRole.WATER to listOf("WATER"),
            TypeSemanticRole.GRASS to listOf("GRASS"),
            TypeSemanticRole.ELECTRIC to listOf("ELECTR", "ELECTRIC"),
            TypeSemanticRole.PSYCHIC to listOf("PSYCHC", "PSYCHIC"),
            TypeSemanticRole.ICE to listOf("ICE"),
            TypeSemanticRole.DRAGON to listOf("DRAGON"),
            TypeSemanticRole.DARK to listOf("DARK"),
            TypeSemanticRole.FAIRY to listOf("FAIRY"),
        ),
        LanguageTag.FRENCH to roles(
            TypeSemanticRole.NORMAL to listOf("NORMAL"),
            TypeSemanticRole.FIGHTING to listOf("COMBAT"),
            TypeSemanticRole.FLYING to listOf("VOL"),
            TypeSemanticRole.POISON to listOf("POISON"),
            TypeSemanticRole.GROUND to listOf("SOL"),
            TypeSemanticRole.ROCK to listOf("ROCHE"),
            TypeSemanticRole.BUG to listOf("INSECT", "INSECTE"),
            TypeSemanticRole.GHOST to listOf("SPECTR", "SPECTRE"),
            TypeSemanticRole.STEEL to listOf("ACIER"),
            TypeSemanticRole.MYSTERY to listOf("???", "(?)"),
            TypeSemanticRole.FIRE to listOf("FEU"),
            TypeSemanticRole.WATER to listOf("EAU"),
            TypeSemanticRole.GRASS to listOf("PLANTE"),
            TypeSemanticRole.ELECTRIC to listOf("ELECTR", "ELECTRIK"),
            TypeSemanticRole.PSYCHIC to listOf("PSY"),
            TypeSemanticRole.ICE to listOf("GLACE"),
            TypeSemanticRole.DRAGON to listOf("DRAGON"),
            TypeSemanticRole.DARK to listOf("TENEBR", "TENEBRES"),
        ),
        LanguageTag.GERMAN to roles(
            TypeSemanticRole.NORMAL to listOf("NORMAL"),
            TypeSemanticRole.FIGHTING to listOf("KAMPF"),
            TypeSemanticRole.FLYING to listOf("FLUG"),
            TypeSemanticRole.POISON to listOf("GIFT"),
            TypeSemanticRole.GROUND to listOf("BODEN"),
            TypeSemanticRole.ROCK to listOf("GEST.", "GESTEIN"),
            TypeSemanticRole.BUG to listOf("KÄFER"),
            TypeSemanticRole.GHOST to listOf("GEIST"),
            TypeSemanticRole.STEEL to listOf("STAHL"),
            TypeSemanticRole.MYSTERY to listOf("???", "(?)"),
            TypeSemanticRole.FIRE to listOf("FEUER"),
            TypeSemanticRole.WATER to listOf("WASSER"),
            TypeSemanticRole.GRASS to listOf("PFLAN.", "PFLANZE"),
            TypeSemanticRole.ELECTRIC to listOf("ELEK.", "ELEKTRO"),
            TypeSemanticRole.PSYCHIC to listOf("PSYCHO"),
            TypeSemanticRole.ICE to listOf("EIS"),
            TypeSemanticRole.DRAGON to listOf("DRA.", "DRACHEN"),
            TypeSemanticRole.DARK to listOf("UNL.", "UNLICHT"),
        ),
        LanguageTag.ITALIAN to roles(
            TypeSemanticRole.NORMAL to listOf("NORM", "NORMALE"),
            TypeSemanticRole.FIGHTING to listOf("LOTTA"),
            TypeSemanticRole.FLYING to listOf("VOLAN", "VOLANTE"),
            TypeSemanticRole.POISON to listOf("VELENO"),
            TypeSemanticRole.GROUND to listOf("TERRA"),
            TypeSemanticRole.ROCK to listOf("ROCCIA"),
            TypeSemanticRole.BUG to listOf("COLEOT", "COLEOTT."),
            TypeSemanticRole.GHOST to listOf("SP.TRO", "SPETTRO"),
            TypeSemanticRole.STEEL to listOf("ACC.IO", "ACCIAIO"),
            TypeSemanticRole.MYSTERY to listOf("???", "(?)"),
            TypeSemanticRole.FIRE to listOf("FUOCO"),
            TypeSemanticRole.WATER to listOf("ACQUA"),
            TypeSemanticRole.GRASS to listOf("ERBA"),
            TypeSemanticRole.ELECTRIC to listOf("ELETT", "ELETTRO"),
            TypeSemanticRole.PSYCHIC to listOf("PSICO"),
            TypeSemanticRole.ICE to listOf("GHIACC", "GHIACCIO"),
            TypeSemanticRole.DRAGON to listOf("DRAGO"),
            TypeSemanticRole.DARK to listOf("BUIO"),
        ),
        LanguageTag.SPANISH to roles(
            TypeSemanticRole.NORMAL to listOf("NORMAL"),
            TypeSemanticRole.FIGHTING to listOf("LUCHA"),
            TypeSemanticRole.FLYING to listOf("VOLAD.", "VOLADOR"),
            TypeSemanticRole.POISON to listOf("VENENO"),
            TypeSemanticRole.GROUND to listOf("TIERRA"),
            TypeSemanticRole.ROCK to listOf("ROCA"),
            TypeSemanticRole.BUG to listOf("BICHO"),
            TypeSemanticRole.GHOST to listOf("FANT.", "FANTASMA"),
            TypeSemanticRole.STEEL to listOf("ACERO"),
            TypeSemanticRole.MYSTERY to listOf("???", "(?)", "¿¿??"),
            TypeSemanticRole.FIRE to listOf("FUEGO"),
            TypeSemanticRole.WATER to listOf("AGUA"),
            TypeSemanticRole.GRASS to listOf("PLANTA"),
            TypeSemanticRole.ELECTRIC to listOf("ELÉCT.", "ELÉCTRIC"),
            TypeSemanticRole.PSYCHIC to listOf("PSÍQ.", "PSÍQUICO"),
            TypeSemanticRole.ICE to listOf("HIELO"),
            TypeSemanticRole.DRAGON to listOf("DRAGÓN"),
            TypeSemanticRole.DARK to listOf("SINIE.", "SINIEST."),
        ),
    )

    private fun roles(
        vararg entries: Pair<TypeSemanticRole, List<String>>,
    ): Map<String, TypeSemanticRole> = buildMap {
        entries.forEach { (role, names) ->
            names.forEach { name -> require(put(name, role) == null) }
        }
    }
}
