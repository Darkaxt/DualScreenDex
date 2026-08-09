package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

object RelationshipMaterializers {
    fun descriptions(rom: RomImage, layout: ResolvedRomLayout): Map<Int, DescriptionRecord> {
        val table = layout.tables.descriptions ?: return emptyMap()
        if (layout.generation < 3) return gen12Descriptions(rom, layout)
        val codec = PokemonTextCodec.gbaEnglish
        val pointerFields = table.pointerOffsets.ifEmpty {
            if (table.recordSize >= 36) listOf(16, 20) else listOf(16)
        }
        return buildMap {
            repeat(table.count) { id ->
                val base = table.offset + id * table.recordSize
                val descriptionOffset = pointerFields.firstNotNullOfOrNull { field -> rom.gbaPointer(base + field) }
                    ?: return@repeat
                put(
                    id,
                    DescriptionRecord(
                        text = decodeTerminated(rom, descriptionOffset, 512, codec),
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
        if (layout.generation < 3) return gen12Relationships(rom, layout, table).first
        val elementSize = table.elementSize ?: 6
        val slots = table.recordSize / elementSize
        return buildMap {
            repeat(table.count) { speciesId ->
                val edges = buildList {
                    repeat(slots) { slot ->
                        val offset = table.offset + speciesId * table.recordSize + slot * elementSize
                        val method = rom.u16le(offset)
                        if (method != 0) {
                            add(
                                EvolutionEdge(
                                    targetSpeciesId = rom.u16le(offset + 4),
                                    methodId = method,
                                    parameter = rom.u16le(offset + 2),
                                    raw = rom.slice(offset, elementSize),
                                ),
                            )
                        }
                    }
                }
                put(speciesId, edges)
            }
        }
    }

    fun learnsets(rom: RomImage, layout: ResolvedRomLayout): Map<Int, List<LearnsetEntry>> {
        val table = layout.tables.learnsets ?: return emptyMap()
        if (layout.generation < 3) return gen12Relationships(rom, layout, table).second
        val moveBits = if ((layout.moveCount ?: 0) > 511) 10 else 9
        val moveMask = (1 shl moveBits) - 1
        return buildMap {
            repeat(table.count) { speciesId ->
                val offset = rom.gbaPointer(table.offset + speciesId * table.recordSize) ?: return@repeat
                put(speciesId, readGen3Learnset(rom, offset, moveBits, moveMask))
            }
        }
    }

    private fun readGen3Learnset(
        rom: RomImage,
        offset: Int,
        moveBits: Int,
        moveMask: Int,
    ): List<LearnsetEntry> {
        val expanded = rom.u16le(offset) == 0 && rom.u8(offset + 2) == 0xFF
        if (expanded) return emptyList()
        return buildList {
            var cursor = offset
            repeat(128) {
                val packed = rom.u16le(cursor)
                if (packed == 0xFFFF) return@buildList
                add(LearnsetEntry(level = packed ushr moveBits, moveId = packed and moveMask))
                cursor += 2
            }
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
                    put(index + 1, DescriptionRecord(decodeTerminated(rom, text, 512, codec), category = category))
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
                    add(LearnsetEntry(level, rom.u8(cursor + 1)))
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
