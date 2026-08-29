package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.validate.Gen3BaseStatAbilitySlots

internal data class SpeciesMaterialization(
    val records: Map<Int, SpeciesRecord>,
    val indexResolution: SpeciesIndexResolution,
)

object RecordMaterializers {
    fun species(rom: RomImage, layout: ResolvedRomLayout): Map<Int, SpeciesRecord> =
        speciesWithIndexResolution(rom, layout).records

    internal fun speciesWithIndexResolution(rom: RomImage, layout: ResolvedRomLayout): SpeciesMaterialization {
        val names = layout.tables.speciesNames ?: return SpeciesMaterialization(
            emptyMap(),
            SpeciesIndexResolution.Unavailable(emptyMap(), "species-name table is unavailable"),
        )
        val stats = layout.tables.baseStats
        val codec = codecFor(layout.generation)
        val firstId = if (layout.generation == 3) 0 else 1
        val indexResolution = SpeciesIndexResolver.resolveWithEvidence(rom, layout)
        if (indexResolution.values.values.none { it > 0 } && names.count > 1) {
            return SpeciesMaterialization(emptyMap(), indexResolution)
        }
        val dexNumbers = indexResolution.values
        val nonPokedexSpeciesIds = compiledNonPokedexSpeciesIds(
            rom = rom,
            layout = layout,
            names = names,
            indexResolution = indexResolution,
        )
        val detachedGen1 = if (layout.generation == 1 && stats != null) {
            Gen1DetachedSpeciesResolver.resolve(rom, stats)
        } else {
            emptyMap()
        }
        val expansion = layout.pokeemeraldExpansion
        val unified = layout.headerlessUnifiedSpecies
        val nameIndexes = when {
            expansion != null -> (1 until names.count).filter { id ->
                (dexNumbers[id] ?: 0) > 0
            }
            unified != null -> {
                val table = requireNotNull(stats)
                val stride = table.stride ?: unified.speciesRecordSize
                (1 until names.count).filter { id ->
                    rom.u8(table.offset + id * stride + unified.activePredicateOffset) != 0
                }
            }
            else -> (0 until names.count).toList()
        }
        val records = nameIndexes.associate { nameIndex ->
            val id = firstId + nameIndex
            val dexNumber = dexNumbers[id] ?: id
            val name = readName(rom, names, nameIndex, codec)
            val statsIndex = when (layout.generation) {
                1 -> dexNumber - 1
                2 -> id - 1
                else -> id
            }
            val ordinaryStatsOffset = stats?.let {
                validatedRecordOffset(rom, it, statsIndex, baseStatBytes(layout.generation))
            }
            val ordinaryBaseStats = ordinaryStatsOffset?.let { readBaseStats(rom, it, layout.generation) }
            val statsOffset = if (ordinaryBaseStats != null) {
                ordinaryStatsOffset
            } else {
                detachedGen1[dexNumber]?.offset
            }
            val baseStats = statsOffset?.let { readBaseStats(rom, it, layout.generation) }
            val typeIds = baseStats?.second
            val validRetailEcologyTail = layout.generation == 3 && expansion == null && unified == null &&
                stats != null && statsOffset != null &&
                validGen3RetailEcologyTail(rom, statsOffset, stats.recordSize)
            val abilities = when {
                layout.generation != 3 -> CatalogField.notApplicable("abilities are not part of this engine")
                stats == null || statsIndex !in 0 until stats.count -> {
                    CatalogField.notFound("base-stat ability layout is unsupported or malformed")
                }
                else -> {
                    val expansionAbilityOffset = expansion?.let {
                        statsOffset?.let { offset ->
                            validatedFieldOffset(offset, stats.recordSize, it.abilitiesOffset, 6)
                        }
                    }
                    val unifiedAbilities = unified?.abilities
                    val unifiedAbilityOffset = unifiedAbilities?.let { metadata ->
                        statsOffset?.let { offset ->
                            validatedFieldOffset(
                                offset,
                                stats.recordSize,
                                metadata.speciesAbilityOffset,
                                metadata.speciesAbilitySlotCount * metadata.speciesAbilityElementSize,
                            )
                        }
                    }
                    if (expansion != null && expansionAbilityOffset != null) {
                        CatalogField.available(
                            (0 until 3).map { rom.u16le(expansionAbilityOffset + it * 2) }
                                .filter { it != 0 }.distinct(),
                        )
                    } else if (unifiedAbilities != null && unifiedAbilityOffset != null) {
                        CatalogField.available(
                            (0 until unifiedAbilities.speciesAbilitySlotCount).map { slot ->
                                val offset = unifiedAbilityOffset + slot * unifiedAbilities.speciesAbilityElementSize
                                if (unifiedAbilities.speciesAbilityElementSize == 1) {
                                    rom.u8(offset)
                                } else {
                                    rom.u16le(offset)
                                }
                            }.filter { it != 0 }.distinct(),
                        )
                    } else if (
                        expansion == null && statsOffset != null && baseStats != null &&
                        Gen3BaseStatAbilitySlots.supportsLayout(rom, stats) &&
                        validRetailEcologyTail
                    ) {
                        CatalogField.available(Gen3BaseStatAbilitySlots.read(rom, statsOffset, stats.recordSize))
                    } else {
                        CatalogField.notFound("base-stat ability layout is unsupported or malformed")
                    }
                }
            }
            val growthRate = if (layout.generation == 3 && stats != null && statsIndex in 0 until stats.count && stats.recordSize >= 20) {
                val offset = statsOffset?.let {
                    validatedFieldOffset(it, stats.recordSize, expansion?.growthRateOffset ?: 19, 1)
                }
                offset?.takeIf { baseStats != null && (expansion != null || validRetailEcologyTail) }
                    ?.let { CatalogField.available(rom.u8(it)) }
                    ?: CatalogField.notFound("base-stat growth-rate layout is unsupported or malformed")
            } else if (layout.generation == 3) {
                CatalogField.notFound("base-stat record has no growth-rate field")
            } else {
                CatalogField.notApplicable("this save generation does not use Gen III growth-rate IDs")
            }
            id to SpeciesRecord(
                id = id,
                dexNumber = if (
                    id in nonPokedexSpeciesIds ||
                    layout.generation == 1 && dexNumber == 0
                ) {
                    CatalogField.notApplicable(
                        "compiled species record is outside the ROM's complete Pokédex-entry domain",
                    )
                } else {
                    CatalogField.available(dexNumber)
                },
                name = CatalogField.available(name),
                typeIds = typeIds?.let(CatalogField.Companion::available)
                    ?: CatalogField.notFound("base stats were not resolved for species $id"),
                baseStats = baseStats?.first?.let(CatalogField.Companion::available)
                    ?: CatalogField.notFound("base stats were not resolved for species $id"),
                sprite = CatalogField.notFound("sprite was not materialized"),
                abilityIds = abilities,
                growthRate = growthRate,
            )
        }
        return SpeciesMaterialization(records, indexResolution)
    }

    /**
     * Separates battle-only species records from the ROM's Pokédex domain. This is
     * admitted only when the compiled species-to-Dex map covers the complete 1..N description
     * domain exactly, a consecutive overflow block continues at N alongside that ordinary mapping, and
     * the selected compiled-referenced description table ends in an erased record rather than
     * hidden rows.
     */
    private fun compiledNonPokedexSpeciesIds(
        rom: RomImage,
        layout: ResolvedRomLayout,
        names: TableLayout,
        indexResolution: SpeciesIndexResolution,
    ): Set<Int> {
        if (layout.generation != 3 || layout.pokeemeraldExpansion != null) return emptySet()
        if (indexResolution !is SpeciesIndexResolution.Resolved) return emptySet()
        val references = layout.compiledGbaReferences?.takeUnless { it.overflowed }?.counts ?: return emptySet()
        val descriptions = layout.resolvedDatasets.descriptions?.table ?: return emptySet()
        val descriptionOffset = descriptions.offset.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
            ?: return emptySet()
        val descriptionCount = descriptions.count.takeIf { it in 2..Int.MAX_VALUE.toLong() }?.toInt()
            ?: return emptySet()
        if ((references[descriptionOffset] ?: 0) < MINIMUM_COMPILED_POKEDEX_REFERENCES) return emptySet()
        val tableEnd = descriptionOffset.toLong() + descriptions.count * descriptions.recordSize
        if (tableEnd < 0 || tableEnd + descriptions.recordSize > rom.size.toLong()) return emptySet()
        if ((0 until descriptions.recordSize).any { rom.u8(tableEnd.toInt() + it) != 0xFF }) return emptySet()

        val storedIds = (1 until names.count).toList()
        val dexValues = storedIds.map { id -> indexResolution.values[id] ?: return emptySet() }
        val encodedMap = ByteArray(dexValues.size * 2)
        dexValues.forEachIndexed { index, dex ->
            if (dex !in 0..0xFFFF) return emptySet()
            encodedMap[index * 2] = dex.toByte()
            encodedMap[index * 2 + 1] = (dex ushr 8).toByte()
        }
        val compiledMapRoots = references.asSequence()
            .filter { (_, count) -> count >= MINIMUM_COMPILED_POKEDEX_REFERENCES }
            .map { (root, _) -> root }
            .filter { root ->
                root >= 0 && root.toLong() + encodedMap.size <= rom.size.toLong() &&
                    encodedMap.indices.all { index ->
                        rom.u8(root + index) == (encodedMap[index].toInt() and 0xFF)
                    }
            }
            .toList()
        if (compiledMapRoots.size != 1) return emptySet()

        val mapped = storedIds.zip(dexValues).filter { (_, dex) -> dex > 0 }
        val ordinaryDex = mapped.mapNotNull { (_, dex) -> dex.takeIf { it < descriptionCount } }.toSet()
        if (ordinaryDex != (1 until descriptionCount).toSet()) return emptySet()
        val overflow = mapped.filter { (_, dex) -> dex >= descriptionCount }
        if (overflow.isEmpty()) return emptySet()
        val overflowIds = overflow.map(Pair<Int, Int>::first)
        if (overflowIds.zipWithNext().any { (first, second) -> second != first + 1 }) return emptySet()
        val overflowDex = overflow.map(Pair<Int, Int>::second)
        if (overflowDex != (descriptionCount until descriptionCount + overflow.size).toList()) return emptySet()
        return overflowIds.toSet()
    }

    fun moves(rom: RomImage, layout: ResolvedRomLayout): Map<Int, MoveRecord> {
        val names = layout.tables.moveNames ?: return emptyMap()
        val codec = codecFor(layout.generation)
        val firstId = if (layout.generation == 3) 0 else 1
        if (layout.generation == 3 && layout.pokeemeraldExpansion == null) {
            val details = layout.resolvedDatasets.moveDetails?.catalogDetails().orEmpty()
            val referencedMoveIds = layout.resolvedDatasets.learnsets
                ?.catalogPrimaryEntries()
                ?.values
                ?.asSequence()
                ?.flatten()
                ?.map(LearnsetEntry::moveId)
                ?.toSet()
                .orEmpty()
            return (0 until names.count).mapNotNull { index ->
                val id = firstId + index
                if (id == 0) return@mapNotNull null
                val name = readName(rom, names, index, codec)
                val detail = details[index]
                val named = name.any(Char::isLetterOrDigit)
                if (!named && (id !in referencedMoveIds || detail == null)) return@mapNotNull null
                id to MoveRecord(
                    id = id,
                    name = if (named) {
                        CatalogField.available(name)
                    } else {
                        CatalogField.notFound("referenced move has no decoded ROM name")
                    },
                    typeId = detail?.typeId?.let(CatalogField.Companion::available)
                        ?: CatalogField.notFound("move details were not resolved from the ROM"),
                    category = detail?.category?.let(CatalogField.Companion::available)
                        ?: CatalogField.notFound("move details were not resolved from the ROM"),
                    power = detail?.power?.let(CatalogField.Companion::available)
                        ?: CatalogField.notFound("move details were not resolved from the ROM"),
                    accuracy = detail?.accuracy?.let(CatalogField.Companion::available)
                        ?: CatalogField.notFound("move details were not resolved from the ROM"),
                    pp = detail?.pp?.let(CatalogField.Companion::available)
                        ?: CatalogField.notFound("move details were not resolved from the ROM"),
                    priority = detail?.priority?.let(CatalogField.Companion::available)
                        ?: CatalogField.notFound("move details were not resolved from the ROM"),
                    effectId = detail?.effectId?.let(CatalogField.Companion::available)
                        ?: CatalogField.notFound("move details were not resolved from the ROM"),
                )
            }.toMap()
        }
        val data = layout.tables.moveData ?: return (0 until names.count).mapNotNull { index ->
            val id = firstId + index
            if (id == 0) return@mapNotNull null
            val name = readName(rom, names, index, codec)
            if (name.none(Char::isLetterOrDigit)) return@mapNotNull null
            id to MoveRecord(
                id = id,
                name = CatalogField.available(name),
                typeId = CatalogField.notFound("move details were not resolved from the ROM"),
                category = CatalogField.notFound("move details were not resolved from the ROM"),
                power = CatalogField.notFound("move details were not resolved from the ROM"),
                accuracy = CatalogField.notFound("move details were not resolved from the ROM"),
                pp = CatalogField.notFound("move details were not resolved from the ROM"),
                priority = CatalogField.notFound("move details were not resolved from the ROM"),
                effectId = CatalogField.notFound("move details were not resolved from the ROM"),
            )
        }.toMap()
        val count = minOf(names.count, data.count)
        val expansion = layout.pokeemeraldExpansion
        return (0 until count).associate { index ->
            val id = firstId + index
            val base = data.offset + index * (data.stride ?: data.recordSize)
            val gen3 = layout.generation == 3
            if (expansion != null) {
                val packed = rom.u16le(base + 10)
                val category = when ((packed ushr 5) and 0x3) {
                    0 -> MoveCategory.PHYSICAL
                    1 -> MoveCategory.SPECIAL
                    2 -> MoveCategory.STATUS
                    else -> MoveCategory.UNKNOWN
                }
                val rawPriority = (rom.u32le(base + 16).toInt() and 0xF)
                val priority = if (rawPriority >= 8) rawPriority - 16 else rawPriority
                return@associate id to MoveRecord(
                    id = id,
                    name = CatalogField.available(readName(rom, names, index, codec)),
                    typeId = CatalogField.available(packed and 0x1F),
                    category = CatalogField.available(category),
                    power = CatalogField.available(packed ushr 7),
                    accuracy = CatalogField.available(rom.u16le(base + 12) and 0x7F),
                    pp = CatalogField.available(rom.u8(base + 14)),
                    priority = CatalogField.available(priority),
                    effectId = CatalogField.available(rom.u16le(base + 8)),
                )
            }
            if (data.format == TableRecordFormat.CFRU_MOVE_16) {
                val typeId = rom.u8(base + 4)
                val priority = rom.u8(base + 9).toByte().toInt()
                val category = when (rom.u8(base + 10)) {
                    0 -> MoveCategory.PHYSICAL
                    1 -> MoveCategory.SPECIAL
                    2 -> MoveCategory.STATUS
                    else -> category(layout.generation, typeId, rom.u16le(base + 2))
                }
                return@associate id to MoveRecord(
                    id = id,
                    name = CatalogField.available(readName(rom, names, index, codec)),
                    typeId = CatalogField.available(typeId),
                    category = CatalogField.available(category),
                    power = CatalogField.available(rom.u16le(base + 2)),
                    accuracy = CatalogField.available(rom.u8(base + 5)),
                    pp = CatalogField.available(rom.u8(base + 6)),
                    priority = CatalogField.available(priority),
                    effectId = CatalogField.available(rom.u16le(base)),
                )
            }
            if (data.format == TableRecordFormat.BATTLE_ENGINE_MOVE_20) {
                val typeId = rom.u8(base + 4)
                val priority = rom.u8(base + 10).toByte().toInt()
                val category = when (rom.u8(base + 16)) {
                    0 -> MoveCategory.PHYSICAL
                    1 -> MoveCategory.SPECIAL
                    2 -> MoveCategory.STATUS
                    else -> category(layout.generation, typeId, rom.u16le(base + 2))
                }
                return@associate id to MoveRecord(
                    id = id,
                    name = CatalogField.available(readName(rom, names, index, codec)),
                    typeId = CatalogField.available(typeId),
                    category = CatalogField.available(category),
                    power = CatalogField.available(rom.u16le(base + 2)),
                    accuracy = CatalogField.available(rom.u8(base + 5)),
                    pp = CatalogField.available(rom.u8(base + 6)),
                    priority = CatalogField.available(priority),
                    effectId = CatalogField.available(rom.u16le(base)),
                )
            }
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

    private const val MINIMUM_COMPILED_POKEDEX_REFERENCES = 2

    fun abilities(
        rom: RomImage,
        layout: ResolvedRomLayout,
    ): Map<Int, AbilityRecord> {
        if (layout.generation == 3 && layout.pokeemeraldExpansion == null) {
            return layout.resolvedDatasets.abilityNames?.catalogAbilities().orEmpty()
        }
        val table = layout.tables.abilities ?: return emptyMap()
        val codec = codecFor(layout.generation)
        return (0 until table.count).associateWith { id ->
            AbilityRecord(id, CatalogField.available(readName(rom, table, id, codec)))
        }
    }

    fun typeChart(rom: RomImage, layout: ResolvedRomLayout): List<TypeMatchup> {
        val table = layout.tables.typeChart ?: return emptyList()
        if (table.elementSize == 2 || table.elementSize == 4) {
            val elementSize = requireNotNull(table.elementSize)
            val typeCount = table.recordSize / elementSize
            return buildList {
                repeat(typeCount) { attacker ->
                    repeat(typeCount) { defender ->
                        val offset = table.offset + (attacker * typeCount + defender) * elementSize
                        val raw = when (elementSize) {
                            2 -> rom.u16le(offset).toLong()
                            else -> rom.u32le(offset)
                        }
                        if (raw != 4096L) {
                            add(TypeMatchup(attacker, defender, ((raw * 100L + 2048L) / 4096L).toInt()))
                        }
                    }
                }
            }
        }
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

    private fun readBaseStats(rom: RomImage, offset: Int, generation: Int): Pair<BaseStats, List<Int>>? {
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
        val types = listOf(rom.u8(offset + typeOffset), rom.u8(offset + typeOffset + 1))
        if (listOf(hp, attack, defense, speed, specialAttack, specialDefense).any { it !in 1..255 }) return null
        val maximumType = if (generation == 3) 31 else 27
        if (types.any { it !in 0..maximumType }) return null
        return BaseStats(hp, attack, defense, speed, specialAttack, specialDefense) to types
    }

    private fun validatedRecordOffset(
        rom: RomImage,
        table: TableLayout,
        index: Int,
        minimumRecordSize: Int,
    ): Int? {
        val stride = table.stride ?: table.recordSize
        if (table.offset < 0 || table.count <= 0 || index !in 0 until table.count ||
            table.recordSize < minimumRecordSize || stride < table.recordSize
        ) {
            return null
        }
        val tableEnd = table.offset.toLong() + (table.count - 1L) * stride + table.recordSize
        if (tableEnd > rom.size.toLong()) return null
        val recordOffset = table.offset.toLong() + index.toLong() * stride
        if (recordOffset !in 0..Int.MAX_VALUE.toLong()) return null
        return recordOffset.toInt()
    }

    private fun validatedFieldOffset(recordOffset: Int, recordSize: Int, fieldOffset: Int, width: Int): Int? {
        if (fieldOffset < 0 || width <= 0 || fieldOffset.toLong() + width > recordSize.toLong()) return null
        return (recordOffset.toLong() + fieldOffset).toInt()
    }

    private fun validGen3RetailEcologyTail(rom: RomImage, recordOffset: Int, recordSize: Int): Boolean {
        if (recordSize != 28) return true
        return rom.u8(recordOffset + 19) in 0..5 &&
            rom.u8(recordOffset + 20) in 0..15 &&
            rom.u8(recordOffset + 21) in 0..15 &&
            (rom.u8(recordOffset + 25) and 0x7F) in 0..13 &&
            rom.u8(recordOffset + 26) == 0 && rom.u8(recordOffset + 27) == 0
    }

    private fun baseStatBytes(generation: Int): Int = if (generation == 2) 9 else 8

    internal fun readName(
        rom: RomImage,
        table: TableLayout,
        index: Int,
        codec: PokemonTextCodec,
    ): String {
        if (!table.variableLength) {
            val record = table.offset + index * (table.stride ?: table.recordSize)
            if (!table.valuesArePointers) return codec.decode(rom.slice(record, table.recordSize))
            val value = rom.gbaPointer(record) ?: return ""
            val length = (0 until minOf(64, rom.size - value))
                .firstOrNull { rom.u8(value + it) == codec.terminator }
                ?.plus(1) ?: minOf(64, rom.size - value)
            return codec.decode(rom.slice(value, length))
        }
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
