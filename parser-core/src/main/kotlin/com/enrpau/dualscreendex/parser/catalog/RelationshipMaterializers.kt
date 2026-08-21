package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Gen3LearnsetEncoding
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.text.Gen1DescriptionTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.validate.PokemonDatasetValidators
import com.enrpau.dualscreendex.parser.validate.Gen3PackedLearnsetDecoder
import com.enrpau.dualscreendex.parser.validate.Gen3DescriptionPointerRecovery

object RelationshipMaterializers {
    fun descriptions(rom: RomImage, layout: ResolvedRomLayout): Map<Int, DescriptionRecord> {
        if (layout.generation == 3 && layout.pokeemeraldExpansion == null) {
            return layout.resolvedDatasets.descriptions?.catalogDescriptions().orEmpty()
        }
        val table = layout.tables.descriptions ?: return emptyMap()
        if (layout.generation < 3) return gen12Descriptions(rom, layout)
        layout.pokeemeraldExpansion?.let { expansion ->
            val stride = table.stride ?: expansion.speciesRecordSize
            return buildMap {
                repeat(table.count) { id ->
                    val base = table.offset + id * stride
                    val description = rom.gbaPointer(base + expansion.descriptionPointerOffset) ?: return@repeat
                    put(
                        id,
                        DescriptionRecord(
                            text = decodeTerminated(rom, description, 512, PokemonTextCodec.gbaEnglish),
                            height = rom.u16le(base + expansion.heightOffset),
                            weight = rom.u16le(base + expansion.weightOffset),
                            category = decodeTerminated(
                                rom,
                                base + expansion.categoryOffset,
                                expansion.speciesNameOffset - expansion.categoryOffset,
                                PokemonTextCodec.gbaEnglish,
                            ),
                        ),
                    )
                }
            }
        }
        val codec = PokemonTextCodec.gbaEnglish
        val pointerFields = table.pointerOffsets.ifEmpty {
            if (table.recordSize >= 36) listOf(16, 20) else listOf(16)
        }
        val recoveredPointers = Gen3DescriptionPointerRecovery.recover(rom, table, codec)
        return buildMap {
            repeat(table.count) { id ->
                val base = table.offset + id * table.recordSize
                val directText = pointerFields.firstNotNullOfOrNull { field ->
                    rom.gbaPointer(base + field)
                        ?.let { decodeTerminated(rom, it, 512, codec) }
                        ?.takeIf(String::isNotBlank)
                }
                val descriptionText = directText ?: recoveredPointers[id]?.text ?: return@repeat
                put(
                    id,
                    DescriptionRecord(
                        text = descriptionText,
                        height = rom.u16le(base + 12),
                        weight = rom.u16le(base + 14),
                        category = decodeTerminated(rom, base, 12, codec),
                    ),
                )
            }
        }
    }

    fun evolutions(rom: RomImage, layout: ResolvedRomLayout): Map<Int, List<EvolutionEdge>> {
        val table = layout.tables.evolutions ?: return emptyMap()
        if (table.count <= 0) return emptyMap()
        if (layout.generation < 3) return gen12Relationships(rom, layout, table).first
        if (layout.pokeemeraldExpansion != null) {
            val stride = table.stride ?: layout.pokeemeraldExpansion.speciesRecordSize
            return buildMap {
                repeat(table.count) { speciesId ->
                    val pointer = rom.gbaPointer(table.offset + speciesId * stride)
                    val edges = if (pointer == null) emptyList() else readExpansionEvolutions(rom, pointer)
                    put(speciesId, edges)
                }
            }
        }
        return layout.resolvedDatasets.evolutions?.catalogEvolutions().orEmpty()
    }

    fun learnsets(rom: RomImage, layout: ResolvedRomLayout): Map<Int, List<LearnsetEntry>> {
        val table = layout.tables.learnsets ?: return emptyMap()
        if (layout.generation < 3) return gen12Relationships(rom, layout, table).second
        if (layout.pokeemeraldExpansion != null) {
            val stride = table.stride ?: layout.pokeemeraldExpansion.speciesRecordSize
            return buildMap {
                repeat(table.count) { speciesId ->
                    val offset = rom.gbaPointer(table.offset + speciesId * stride) ?: return@repeat
                    put(speciesId, readExpansionLearnset(rom, offset))
                }
            }
        }
        return layout.resolvedDatasets.learnsets?.catalogPrimaryEntries().orEmpty()
    }

    private fun readExpansionLearnset(rom: RomImage, offset: Int): List<LearnsetEntry> = buildList {
        var cursor = offset
        repeat(256) {
            val move = rom.u16le(cursor)
            if (move == 0xFFFF) return@buildList
            add(LearnsetEntry(level = rom.u16le(cursor + 2), moveId = move))
            cursor += 4
        }
    }

    private fun readExpansionEvolutions(rom: RomImage, offset: Int): List<EvolutionEdge> = buildList {
        var cursor = offset
        repeat(32) {
            val method = rom.u16le(cursor)
            if (method == 0xFFFF) return@buildList
            val raw = rom.slice(cursor, 12)
            add(
                EvolutionEdge(
                    targetSpeciesId = rom.u16le(cursor + 4),
                    methodId = method,
                    parameter = rom.u16le(cursor + 2),
                    raw = raw,
                ),
            )
            cursor += 12
        }
    }

    private fun gen12Descriptions(
        rom: RomImage,
        layout: ResolvedRomLayout,
    ): Map<Int, DescriptionRecord> {
        val table = layout.tables.descriptions ?: return emptyMap()
        val codec = PokemonTextCodec.gbEnglish
        return buildMap {
            repeat(table.count) { index ->
                val bank = if (layout.generation == 1) table.bank else table.banks.getOrNull(index / 64)
                val entry = bank?.let { rom.gbBankAddress(it, rom.u16le(table.offset + index * table.recordSize)) }
                    ?: return@repeat
                val categoryLength = terminatedLength(rom, entry, 24, codec.terminator) ?: return@repeat
                val category = codec.decode(rom.slice(entry, categoryLength))
                val metadata = entry + categoryLength
                if (layout.generation == 1) {
                    val text = rom.gbBankAddress(rom.u8(metadata + 7), rom.u16le(metadata + 5))
                        ?: return@repeat
                    val description = Gen1DescriptionTextCodec.decode(rom, text, 512) ?: return@repeat
                    put(index + 1, DescriptionRecord(description, category = category))
                } else {
                    put(
                        index + 1,
                        DescriptionRecord(
                            text = decodeTerminated(rom, metadata + 4, 512, codec),
                            height = rom.u16le(metadata),
                            weight = rom.u16le(metadata + 2),
                            category = category,
                        ),
                    )
                }
            }
        }
    }

    private fun gen12Relationships(
        rom: RomImage,
        layout: ResolvedRomLayout,
        table: com.enrpau.dualscreendex.parser.model.TableLayout,
    ): Pair<Map<Int, List<EvolutionEdge>>, Map<Int, List<LearnsetEntry>>> {
        val evolutions = mutableMapOf<Int, List<EvolutionEdge>>()
        val learnsets = mutableMapOf<Int, List<LearnsetEntry>>()
        val bank = table.bank ?: return evolutions to learnsets
        repeat(table.count) { index ->
            var cursor = rom.gbBankAddress(bank, rom.u16le(table.offset + index * table.recordSize)) ?: return@repeat
            val edges = buildList {
                repeat(16) {
                    val method = rom.u8(cursor)
                    if (method == 0) return@buildList
                    val width = evolutionWidth(layout.generation, method) ?: return@buildList
                    val raw = rom.slice(cursor, width)
                    add(EvolutionEdge(raw.last().toInt() and 0xFF, method, raw[1].toInt() and 0xFF, raw))
                    cursor += width
                }
            }
            evolutions[index + 1] = edges
            while (rom.u8(cursor) != 0) cursor++
            cursor++
            val moves = buildList {
                repeat(128) {
                    val level = rom.u8(cursor)
                    if (level == 0) return@buildList
                    val move = rom.u8(cursor + 1)
                    // Gen I/II consumers use move 0 as NO_MOVE, matching empty party move slots.
                    // It is a no-op relationship, not a catalog move identity.
                    if (move != 0) add(LearnsetEntry(level, move))
                    cursor += 2
                }
            }
            learnsets[index + 1] = moves
        }
        return evolutions to learnsets
    }

    private fun evolutionWidth(generation: Int, method: Int): Int? = when (generation) {
        1 -> when (method) {
            1, 3 -> 3
            2 -> 4
            else -> null
        }
        2 -> when (method) {
            in 1..4 -> 3
            5 -> 4
            else -> null
        }
        else -> null
    }

    private fun decodeTerminated(
        rom: RomImage,
        offset: Int,
        maximumLength: Int,
        codec: PokemonTextCodec,
    ): String {
        val length = terminatedLength(rom, offset, maximumLength, codec.terminator) ?: maximumLength
        return codec.decode(rom.slice(offset, length))
    }

    private fun terminatedLength(rom: RomImage, offset: Int, maximumLength: Int, terminator: Int): Int? =
        (0 until maximumLength).firstOrNull { rom.u8(offset + it) == terminator }?.plus(1)
}
