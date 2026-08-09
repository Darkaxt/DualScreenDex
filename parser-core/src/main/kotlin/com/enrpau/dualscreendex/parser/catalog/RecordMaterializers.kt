package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

object RecordMaterializers {
    fun species(rom: RomImage, layout: ResolvedRomLayout): Map<Int, SpeciesRecord> {
        val names = layout.tables.speciesNames ?: return emptyMap()
        val stats = layout.tables.baseStats
        val codec = codecFor(layout.generation)
        val firstId = if (layout.generation == 3) 0 else 1
        return (0 until names.count).associate { nameIndex ->
            val id = firstId + nameIndex
            val name = readName(rom, names, nameIndex, codec)
            val statsIndex = if (layout.generation == 3) id else id - 1
            val baseStats = stats?.takeIf { statsIndex in 0 until it.count }?.let {
                readBaseStats(rom, it.offset + statsIndex * it.recordSize, layout.generation)
            }
            val typeIds = baseStats?.second
            val abilities = if (layout.generation == 3 && stats != null && statsIndex in 0 until stats.count) {
                val offset = stats.offset + statsIndex * stats.recordSize
                if (stats.recordSize >= 24) {
                    CatalogField.available(listOf(rom.u8(offset + 22), rom.u8(offset + 23)).distinct())
                } else {
                    CatalogField.notFound("base-stat record has no ability fields")
                }
            } else {
                CatalogField.notApplicable("abilities are not part of this engine")
            }
            id to SpeciesRecord(
                id = id,
                dexNumber = CatalogField.available(id),
                name = CatalogField.available(name),
                typeIds = typeIds?.let(CatalogField.Companion::available)
                    ?: CatalogField.notFound("base stats were not resolved for species $id"),
                baseStats = baseStats?.first?.let(CatalogField.Companion::available)
                    ?: CatalogField.notFound("base stats were not resolved for species $id"),
                sprite = CatalogField.notFound("sprite was not materialized"),
                abilityIds = abilities,
            )
        }
    }

    fun moves(rom: RomImage, layout: ResolvedRomLayout): Map<Int, MoveRecord> {
        val names = layout.tables.moveNames ?: return emptyMap()
        val data = layout.tables.moveData ?: return emptyMap()
        val count = minOf(names.count, data.count)
        val codec = codecFor(layout.generation)
        val firstId = if (layout.generation == 3) 0 else 1
        return (0 until count).associate { index ->
            val id = firstId + index
            val base = data.offset + index * data.recordSize
            val gen3 = layout.generation == 3
            val effectOffset = if (gen3) 0 else 1
            val powerOffset = if (gen3) 1 else 2
            val typeOffset = if (gen3) 2 else 3
            val accuracyOffset = if (gen3) 3 else 4
            val ppOffset = if (gen3) 4 else 5
            val power = rom.u8(base + powerOffset)
            val typeId = rom.u8(base + typeOffset)
            id to MoveRecord(
                id = id,
                name = CatalogField.available(readName(rom, names, index, codec)),
                typeId = CatalogField.available(typeId),
                category = CatalogField.available(category(layout.generation, typeId, power)),
                power = CatalogField.available(power),
                accuracy = CatalogField.available(rom.u8(base + accuracyOffset)),
                pp = CatalogField.available(rom.u8(base + ppOffset)),
                priority = if (gen3 && data.recordSize >= 8) {
                    CatalogField.available(rom.u8(base + 7).toByte().toInt())
                } else {
                    CatalogField.notFound("priority is not stored in this move record")
                },
                effectId = CatalogField.available(rom.u8(base + effectOffset)),
            )
        }
    }

    fun abilities(rom: RomImage, layout: ResolvedRomLayout): Map<Int, AbilityRecord> {
        val table = layout.tables.abilities ?: return emptyMap()
        val codec = codecFor(layout.generation)
        return (0 until table.count).associateWith { id ->
            AbilityRecord(id, CatalogField.available(readName(rom, table, id, codec)))
        }
    }

    fun typeChart(rom: RomImage, layout: ResolvedRomLayout): List<TypeMatchup> {
        val table = layout.tables.typeChart ?: return emptyList()
        val output = mutableListOf<TypeMatchup>()
        var cursor = table.offset
        repeat(table.count.takeIf { it > 0 } ?: 256) {
            val attacker = rom.u8(cursor)
            val defender = rom.u8(cursor + 1)
            if (attacker == 0xFE || attacker == 0xFF) return output
            output += TypeMatchup(attacker, defender, rom.u8(cursor + 2) * 10)
            cursor += 3
        }
        return output
    }

    fun types(
        layout: ResolvedRomLayout,
        species: Map<Int, SpeciesRecord>,
        chart: List<TypeMatchup>,
        moves: Map<Int, MoveRecord> = emptyMap(),
    ): Map<Int, TypeRecord> {
        val ids = buildSet {
            species.values.flatMap { it.typeIds.value.orEmpty() }.forEach(::add)
            moves.values.mapNotNull { it.typeId.value }.forEach(::add)
            chart.forEach {
                add(it.attackingTypeId)
                add(it.defendingTypeId)
            }
        }
        return ids.sorted().associateWith { id ->
            TypeRecord(id, CatalogField.available(TypeMappings.name(layout.generation, id)))
        }
    }

    private fun readBaseStats(rom: RomImage, offset: Int, generation: Int): Pair<BaseStats, List<Int>> {
        val start = if (generation <= 2) 1 else 0
        val hp = rom.u8(offset + start)
        val attack = rom.u8(offset + start + 1)
        val defense = rom.u8(offset + start + 2)
        val speed = rom.u8(offset + start + 3)
        val specialAttack = rom.u8(offset + start + 4)
        val specialDefense = if (generation == 1) specialAttack else rom.u8(offset + start + 5)
        val typeOffset = when (generation) {
            1 -> 6
            2 -> 7
            else -> 6
        }
        return BaseStats(hp, attack, defense, speed, specialAttack, specialDefense) to
            listOf(rom.u8(offset + typeOffset), rom.u8(offset + typeOffset + 1))
    }

    private fun readName(
        rom: RomImage,
        table: TableLayout,
        index: Int,
        codec: PokemonTextCodec,
    ): String {
        if (!table.variableLength) return codec.decode(rom.slice(table.offset + index * table.recordSize, table.recordSize))
        var cursor = table.offset
        repeat(index) {
            while (rom.u8(cursor++) != codec.terminator) {
                // Advance to the next terminated record.
            }
        }
        val bytes = ArrayList<Byte>()
        while (true) {
            val value = rom.u8(cursor++)
            bytes += value.toByte()
            if (value == codec.terminator) break
        }
        return codec.decode(bytes.toByteArray())
    }

    private fun category(generation: Int, typeId: Int, power: Int): MoveCategory {
        if (power == 0) return MoveCategory.STATUS
        val physical = if (generation == 3) typeId in 0..8 else typeId in 0..9
        val special = if (generation == 3) typeId in 10..17 else typeId in 20..27
        return when {
            physical -> MoveCategory.PHYSICAL
            special -> MoveCategory.SPECIAL
            else -> MoveCategory.UNKNOWN
        }
    }

    private fun codecFor(generation: Int): PokemonTextCodec =
        if (generation == 3) PokemonTextCodec.gbaEnglish else PokemonTextCodec.gbEnglish
}
