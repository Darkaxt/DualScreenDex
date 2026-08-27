package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomBoundsException
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.PokeemeraldExpansionMetadata
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.sprite.GbaDecodeContract
import com.enrpau.dualscreendex.parser.sprite.GbaRomCompression
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

data class PokeemeraldExpansionFirstRegisters(
    val speciesName: String,
    val speciesNationalDex: Int,
    val moveName: String,
    val abilityName: String,
)

data class PokeemeraldExpansionResolution(
    val speciesCount: Int,
    val moveCount: Int,
    val abilityCount: Int,
    val tables: ProfileTables,
    val metadata: PokeemeraldExpansionMetadata,
    val firstRegisters: PokeemeraldExpansionFirstRegisters,
)

/** Resolves the append-only public headers emitted by pokeemerald-expansion. */
object PokeemeraldExpansionResolver {
    private val magic = "RHHEXP".toByteArray(Charsets.US_ASCII)
    private val codec = PokemonTextCodec.gbaEnglish

    fun resolve(rom: RomImage): PokeemeraldExpansionResolution? {
        return rom.findAll(magic).asSequence().mapNotNull { header ->
            tryResolve(rom, header)
        }.singleOrNull()
    }

    fun validateSpeciesNames(rom: RomImage, resolution: PokeemeraldExpansionResolution): ValidationEvidence {
        val table = resolution.tables.speciesNames ?: error("resolved expansion species-name table is absent")
        return validateActiveSpeciesRows(
            rom = rom,
            resolution = resolution,
            label = "species names",
            minimumRatio = 0.85,
            offset = table.offset,
            recordSize = table.recordSize,
        ) { record ->
            plausibleInlineName(
                rom,
                record + resolution.metadata.speciesNameOffset,
                resolution.metadata.speciesNameWidth,
            )
        }
    }

    fun validateBaseStats(rom: RomImage, resolution: PokeemeraldExpansionResolution): ValidationEvidence {
        val table = resolution.tables.baseStats ?: error("resolved expansion species table is absent")
        return validateActiveSpeciesRows(
            rom = rom,
            resolution = resolution,
            label = "base stats",
            minimumRatio = 0.90,
            offset = table.offset,
            recordSize = resolution.metadata.speciesRecordSize,
        ) { record -> plausibleStats(rom, record) }
    }

    fun validateDescriptions(rom: RomImage, resolution: PokeemeraldExpansionResolution): ValidationEvidence {
        val table = resolution.tables.baseStats ?: error("resolved expansion species table is absent")
        return validateSpeciesPointerField(
            rom,
            resolution,
            resolution.metadata.descriptionPointerOffset,
            "description",
            minimumRatio = 0.80,
        ) { pointer -> plausibleInlineName(rom, pointer, 512) }.copy(
            offset = table.offset,
            recordSize = resolution.metadata.speciesRecordSize,
        )
    }

    fun validateSprites(rom: RomImage, resolution: PokeemeraldExpansionResolution): ValidationEvidence {
        val table = resolution.tables.baseStats ?: error("resolved expansion species table is absent")
        val stride = resolution.metadata.speciesRecordSize
        val active = activeSpeciesIds(rom, resolution)
        var valid = 0
        active.forEach { id ->
            val record = table.offset + id * stride
            val graphicsPointer = runCatching {
                rom.gbaPointer(record + resolution.metadata.frontSpritePointerOffset)
            }.getOrNull()
            val palettePointer = runCatching {
                rom.gbaPointer(record + resolution.metadata.normalPalettePointerOffset)
            }.getOrNull()
            val graphicsValid = graphicsPointer?.let { pointer ->
                runCatching {
                    val decoded = GbaRomCompression.decodeAt(rom, pointer, GbaDecodeContract.SPECIES_SPRITE)
                    decoded.size >= 2048 && decoded.size % 2048 == 0
                }.getOrDefault(false)
            } == true
            val paletteValid = palettePointer?.let { pointer ->
                runCatching { rom.slice(pointer, 32).size == 32 }.getOrDefault(false)
            } == true
            if (graphicsValid && paletteValid) valid++
        }
        val confidence = valid.toDouble() / active.size.coerceAtLeast(1)
        return ValidationEvidence(
            compatible = active.isNotEmpty() && confidence >= 0.80,
            validRecords = valid,
            totalRecords = resolution.speciesCount,
            confidence = confidence,
            reasons = if (active.isNotEmpty() && confidence >= 0.80) emptyList() else {
                listOf("decodable expansion front sprites and raw palettes $valid/${active.size} below 80%")
            },
            offset = table.offset + resolution.metadata.frontSpritePointerOffset,
            recordSize = 4,
            coveredRecords = valid,
            expectedRecords = active.size,
            incompleteRecords = active.size - valid,
        )
    }

    fun validateLearnsets(rom: RomImage, resolution: PokeemeraldExpansionResolution): ValidationEvidence =
        validateSpeciesPointerField(
            rom,
            resolution,
            resolution.metadata.levelUpPointerOffset,
            "level-up learnset",
            minimumRatio = 0.80,
        ) { pointer -> plausibleLevelUp(rom, pointer, resolution.moveCount) }

    fun validateEvolutions(rom: RomImage, resolution: PokeemeraldExpansionResolution): ValidationEvidence {
        val table = resolution.tables.baseStats ?: error("resolved expansion species table is absent")
        val stride = resolution.metadata.speciesRecordSize
        val active = activeSpeciesIds(rom, resolution)
        val elementSize = resolution.metadata.evolutionRecordSize
        var valid = 0
        if (elementSize != null) {
            active.forEach { id ->
                val field = table.offset + id * stride + resolution.metadata.evolutionPointerOffset
                val raw = rom.u32le(field)
                val rowIsValid = when {
                    raw == 0L -> true
                    else -> rom.gbaPointer(field)?.let { pointer ->
                        validateEvolutionList(rom, pointer, elementSize, resolution.speciesCount).valid
                    } == true
                }
                if (rowIsValid) valid++
            }
        }
        val confidence = valid.toDouble() / active.size.coerceAtLeast(1)
        return ValidationEvidence(
            compatible = elementSize != null && active.isNotEmpty() && confidence >= 0.98,
            validRecords = valid,
            totalRecords = resolution.speciesCount,
            confidence = confidence,
            reasons = when {
                elementSize == null -> listOf("expansion evolution record ABI was not structurally resolved")
                active.isNotEmpty() && confidence >= 0.98 -> emptyList()
                else -> listOf("complete expansion evolution fields $valid/${active.size} below 98%")
            },
            offset = table.offset + resolution.metadata.evolutionPointerOffset,
            recordSize = 4,
            elementSize = elementSize,
            coveredRecords = valid,
            expectedRecords = active.size,
            incompleteRecords = active.size - valid,
        )
    }

    private fun validateSpeciesPointerField(
        rom: RomImage,
        resolution: PokeemeraldExpansionResolution,
        fieldOffset: Int,
        label: String,
        minimumRatio: Double,
        targetIsValid: (Int) -> Boolean,
    ): ValidationEvidence {
        val table = resolution.tables.baseStats ?: error("resolved expansion species table is absent")
        val stride = resolution.metadata.speciesRecordSize
        val active = activeSpeciesIds(rom, resolution)
        var valid = 0
        active.forEach { id ->
            val pointer = rom.gbaPointer(table.offset + id * stride + fieldOffset)
            if (pointer != null && targetIsValid(pointer)) valid++
        }
        val confidence = valid.toDouble() / active.size.coerceAtLeast(1)
        return ValidationEvidence(
            compatible = active.isNotEmpty() && confidence >= minimumRatio,
            validRecords = valid,
            totalRecords = resolution.speciesCount,
            confidence = confidence,
            reasons = if (active.isNotEmpty() && confidence >= minimumRatio) emptyList() else {
                listOf("valid expansion $label fields $valid/${active.size} below $minimumRatio")
            },
            offset = table.offset + fieldOffset,
            recordSize = 4,
            coveredRecords = valid,
            expectedRecords = active.size,
            incompleteRecords = active.size - valid,
        )
    }

    private fun validateActiveSpeciesRows(
        rom: RomImage,
        resolution: PokeemeraldExpansionResolution,
        label: String,
        minimumRatio: Double,
        offset: Int,
        recordSize: Int,
        rowIsValid: (Int) -> Boolean,
    ): ValidationEvidence {
        val table = resolution.tables.baseStats ?: error("resolved expansion species table is absent")
        val stride = resolution.metadata.speciesRecordSize
        val active = activeSpeciesIds(rom, resolution)
        val valid = active.count { id -> rowIsValid(table.offset + id * stride) }
        val confidence = valid.toDouble() / active.size.coerceAtLeast(1)
        return ValidationEvidence(
            compatible = active.isNotEmpty() && confidence >= minimumRatio,
            validRecords = valid,
            totalRecords = resolution.speciesCount,
            confidence = confidence,
            reasons = if (active.isNotEmpty() && confidence >= minimumRatio) emptyList() else {
                listOf("valid active expansion $label $valid/${active.size} below $minimumRatio")
            },
            offset = offset,
            recordSize = recordSize,
            coveredRecords = valid,
            expectedRecords = active.size,
            incompleteRecords = active.size - valid,
        )
    }

    private fun activeSpeciesIds(
        rom: RomImage,
        resolution: PokeemeraldExpansionResolution,
    ): List<Int> {
        val table = resolution.tables.baseStats ?: error("resolved expansion species table is absent")
        val stride = resolution.metadata.speciesRecordSize
        return (1 until resolution.speciesCount).filter { id ->
            rom.u16le(table.offset + id * stride + resolution.metadata.nationalDexOffset) > 0
        }
    }

    private fun tryResolve(rom: RomImage, header: Int): PokeemeraldExpansionResolution? = try {
        val moveCount = rom.u16le(header + 0x0A)
        val speciesCount = rom.u16le(header + 0x0C)
        val abilityCount = rom.u16le(header + 0x0E)
        val abilityBase = rom.gbaPointer(header + 0x10) ?: return null
        if (moveCount !in 2..4096 || speciesCount !in 2..4096 || abilityCount !in 2..2048) return null

        val gfHeader = findGfHeader(rom) ?: return null
        val speciesBase = rom.gbaPointer(gfHeader + 0xBC) ?: return null
        val moveBase = rom.gbaPointer(gfHeader + 0xCC) ?: return null
        val species = inferSpeciesShape(rom, speciesBase, speciesCount, moveCount) ?: return null
        val evolutionRecordSize = inferEvolutionRecordSize(rom, speciesBase, species, speciesCount)
        val moveStride = inferMoveStride(rom, moveBase, moveCount) ?: return null
        val ability = inferAbilityShape(rom, abilityBase, abilityCount) ?: return null
        val typeChart = locateTypeChart(rom, moveBase, moveCount, moveStride) ?: return null

        val metadata = PokeemeraldExpansionMetadata(
            headerOffset = header,
            versionMajor = rom.u8(header + 6),
            versionMinor = rom.u8(header + 7),
            versionPatch = rom.u8(header + 8),
            speciesRecordSize = species.stride,
            speciesNameOffset = species.nameOffset,
            speciesNameWidth = species.nameWidth,
            categoryOffset = species.categoryOffset,
            nationalDexOffset = species.nationalDexOffset,
            heightOffset = species.nationalDexOffset + 2,
            weightOffset = species.nationalDexOffset + 4,
            descriptionPointerOffset = species.descriptionOffset,
            frontSpritePointerOffset = species.frontSpriteOffset,
            normalPalettePointerOffset = species.paletteOffset,
            abilitiesOffset = species.abilitiesOffset,
            growthRateOffset = species.growthRateOffset,
            levelUpPointerOffset = species.levelUpOffset,
            teachablePointerOffset = species.levelUpOffset + 4,
            eggMovePointerOffset = species.levelUpOffset + 8,
            evolutionPointerOffset = species.levelUpOffset + 12,
            moveRecordSize = moveStride,
            abilityRecordSize = ability.stride,
            abilityNameWidth = ability.nameWidth,
            abilityDescriptionPointerOffset = ability.descriptionOffset,
            evolutionRecordSize = evolutionRecordSize,
        )
        val tables = ProfileTables(
            speciesNames = TableLayout(
                speciesBase + species.nameOffset,
                speciesCount,
                species.nameWidth,
                stride = species.stride,
            ),
            baseStats = TableLayout(speciesBase, speciesCount, species.stride, stride = species.stride),
            moveNames = TableLayout(
                moveBase,
                moveCount,
                4,
                stride = moveStride,
                valuesArePointers = true,
            ),
            moveData = TableLayout(moveBase, moveCount, moveStride, stride = moveStride),
            typeChart = TableLayout(
                offset = typeChart.offset,
                count = typeChart.typeCount * typeChart.typeCount,
                recordSize = typeChart.typeCount * 4,
                elementSize = 4,
            ),
            evolutions = evolutionRecordSize?.let { recordSize ->
                TableLayout(
                    speciesBase + species.levelUpOffset + 12,
                    speciesCount,
                    4,
                    stride = species.stride,
                    valuesArePointers = true,
                    elementSize = recordSize,
                )
            },
            learnsets = TableLayout(
                speciesBase + species.levelUpOffset,
                speciesCount,
                4,
                stride = species.stride,
                valuesArePointers = true,
                elementSize = 4,
            ),
            sprites = TableLayout(
                speciesBase + species.frontSpriteOffset,
                speciesCount,
                4,
                stride = species.stride,
                pointerOffsets = listOf(species.paletteOffset - species.frontSpriteOffset),
            ),
            descriptions = TableLayout(
                speciesBase,
                speciesCount,
                species.stride,
                stride = species.stride,
                pointerOffsets = listOf(species.descriptionOffset),
            ),
            abilities = TableLayout(
                abilityBase,
                abilityCount,
                ability.nameWidth,
                stride = ability.stride,
                pointerOffsets = listOf(ability.descriptionOffset),
            ),
        )
        PokeemeraldExpansionResolution(
            speciesCount = speciesCount,
            moveCount = moveCount,
            abilityCount = abilityCount,
            tables = tables,
            metadata = metadata,
            firstRegisters = PokeemeraldExpansionFirstRegisters(
                speciesName = decodeInline(rom, speciesBase + species.stride + species.nameOffset, species.nameWidth),
                speciesNationalDex = rom.u16le(speciesBase + species.stride + species.nationalDexOffset),
                moveName = decodePointerName(rom, moveBase + moveStride),
                abilityName = decodeInline(rom, abilityBase + ability.stride, ability.nameWidth),
            ),
        )
    } catch (_: RomBoundsException) {
        null
    }

    private fun findGfHeader(rom: RomImage): Int? {
        val candidates = buildList {
            if (rom.size >= 0x1D0) add(0x100)
            val marker = "pokemon ".toByteArray(Charsets.US_ASCII)
            addAll(rom.findAll(marker).mapNotNull { name -> (name - 8).takeIf { it >= 0 } })
        }.distinct()
        return candidates.singleOrNull { offset ->
            runCatching {
                val name = rom.slice(offset + 8, 32).toString(Charsets.US_ASCII).trimEnd('\u0000')
                name.startsWith("pokemon ", ignoreCase = true) &&
                    rom.gbaPointer(offset + 0xBC) != null && rom.gbaPointer(offset + 0xCC) != null
            }.getOrDefault(false)
        }
    }

    private fun inferSpeciesShape(
        rom: RomImage,
        base: Int,
        count: Int,
        moveCount: Int,
    ): SpeciesShape? {
        val sample = sampleIds(count, 24)
        val candidates = mutableListOf<SpeciesShape>()
        for (stride in 64..384 step 4) {
            if (base.toLong() + count.toLong() * stride > rom.size) continue
            val statsScore = sample.count { id -> plausibleStats(rom, base + id * stride) }
            if (statsScore < sample.size * 0.80) continue
            for (nameOffset in 24..96) {
                val names = sample.count { id -> plausibleInlineName(rom, base + id * stride + nameOffset, 13) }
                if (names < sample.size * 0.85) continue
                val categories = sample.count { id -> plausibleInlineName(rom, base + id * stride + nameOffset - 13, 13) }
                if (categories < sample.size * 0.80) continue
                val natDexOffset = inferNationalDexOffset(
                    rom = rom,
                    base = base,
                    stride = stride,
                    count = count,
                    nameOffset = nameOffset,
                    sample = sample,
                ) ?: continue
                val levelUp = inferLearnsetPointerOffset(rom, base, stride, sample, count, moveCount)
                    ?: continue
                val descriptionOffset = alignToWord(natDexOffset + 14)
                candidates += SpeciesShape(
                    stride = stride,
                    nameOffset = nameOffset,
                    nameWidth = 13,
                    categoryOffset = nameOffset - 13,
                    nationalDexOffset = natDexOffset,
                    descriptionOffset = descriptionOffset,
                    frontSpriteOffset = descriptionOffset + 12,
                    paletteOffset = descriptionOffset + 20,
                    abilitiesOffset = 24,
                    growthRateOffset = 21,
                    levelUpOffset = levelUp,
                    score = statsScore + names + categories,
                )
            }
        }
        return candidates.maxWithOrNull(compareBy<SpeciesShape> { it.score }.thenByDescending { it.stride })
    }

    private fun inferNationalDexOffset(
        rom: RomImage,
        base: Int,
        stride: Int,
        count: Int,
        nameOffset: Int,
        sample: List<Int>,
    ): Int? {
        val eligible = ((nameOffset + 12)..minOf(stride - 2, nameOffset + 32))
            .filter { field ->
                field % 2 == 0 &&
                    sample.count { id -> rom.u16le(base + id * stride + field) == id } >= sample.size * 0.90
            }
        if (eligible.isEmpty()) return null
        val active = (1 until count).filter { id ->
            val record = base + id * stride
            plausibleStats(rom, record) && plausibleInlineName(rom, record + nameOffset, 13)
        }
        val scores = eligible.associateWith { field ->
            active.count { id -> rom.u16le(base + id * stride + field) == id }
        }
        val bestScore = scores.values.maxOrNull() ?: return null
        return scores.filterValues { it == bestScore }.keys.minOrNull()
    }

    private fun inferLearnsetPointerOffset(
        rom: RomImage,
        base: Int,
        stride: Int,
        sample: List<Int>,
        speciesCount: Int,
        moveCount: Int,
    ): Int? {
        val candidates = (64..stride - 16 step 4).mapNotNull { field ->
            var valid = 0
            sample.forEach { id ->
                val record = base + id * stride
                val level = rom.gbaPointer(record + field)
                val teach = rom.gbaPointer(record + field + 4)
                val egg = rom.gbaPointer(record + field + 8)
                val evolution = rom.gbaPointer(record + field + 12)
                val teachValid = rom.u32le(record + field + 4) == 0L ||
                    (teach != null && plausibleMoveList(rom, teach, moveCount))
                val eggValid = rom.u32le(record + field + 8) == 0L ||
                    (egg != null && plausibleMoveList(rom, egg, moveCount))
                val evolutionValid = rom.u32le(record + field + 12) == 0L ||
                    (evolution != null && plausibleEvolution(rom, evolution, speciesCount))
                if (level != null && plausibleLevelUp(rom, level, moveCount) &&
                    teachValid && eggValid && evolutionValid
                ) valid++
            }
            valid.takeIf { it >= sample.size * 0.75 }?.let { field to it }
        }
        return candidates.maxWithOrNull(compareBy<Pair<Int, Int>> { it.second }.thenBy { it.first })?.first
    }

    private fun inferMoveStride(rom: RomImage, base: Int, count: Int): Int? {
        val sample = sampleIdsAcrossExtent(count, 32)
        return (20..128 step 4).mapNotNull { stride ->
            if (base.toLong() + count.toLong() * stride > rom.size) return@mapNotNull null
            val valid = sample.count { id ->
                val record = base + id * stride
                val name = rom.gbaPointer(record)?.let { plausibleInlineName(rom, it, 32) } == true
                val packed = rom.u16le(record + 10)
                val type = packed and 0x1F
                val category = (packed ushr 5) and 0x3
                val power = packed ushr 7
                val accuracy = rom.u16le(record + 12) and 0x7F
                name && type in 0..31 && category in 0..2 && power in 0..511 &&
                    accuracy in 0..100 && rom.u8(record + 14) in 0..64
            }
            valid.takeIf { it >= sample.size * 0.90 }?.let { stride to it }
        }.maxWithOrNull(compareBy<Pair<Int, Int>> { it.second }.thenBy { it.first })?.first
    }

    private fun inferAbilityShape(rom: RomImage, base: Int, count: Int): AbilityShape? {
        val sample = sampleIds(count, 24)
        val candidates = mutableListOf<AbilityShape>()
        for (stride in 20..64 step 4) {
            if (base.toLong() + count.toLong() * stride > rom.size) continue
            for (descriptionOffset in 12..stride - 4 step 4) {
                val valid = sample.count { id ->
                    val record = base + id * stride
                    plausibleInlineName(rom, record, descriptionOffset) &&
                        rom.gbaPointer(record + descriptionOffset)?.let { plausibleInlineName(rom, it, 96) } == true
                }
                if (valid >= sample.size * 0.85) {
                    candidates += AbilityShape(stride, descriptionOffset, descriptionOffset, valid)
                }
            }
        }
        return candidates.maxWithOrNull(compareBy<AbilityShape> { it.score }.thenByDescending { it.stride })
    }

    private fun locateTypeChart(rom: RomImage, moveBase: Int, moveCount: Int, moveStride: Int): TypeChartShape? {
        var maximumType = 0
        repeat(moveCount) { id ->
            val packed = rom.u16le(moveBase + id * moveStride + 10)
            maximumType = maxOf(maximumType, packed and 0x1F)
        }
        val neutral = byteArrayOf(0x00, 0x10, 0x00, 0x00)
        val candidates = ((maximumType + 1).coerceAtLeast(18)..32).flatMap { typeCount ->
            val rowZero = ByteArray(typeCount * 4) { index -> neutral[index % 4] }
            rom.findAll(rowZero).mapNotNull { offset ->
                TypeChartShape(offset, typeCount).takeIf {
                    offset % 4 == 0 && offset.toLong() + typeCount.toLong() * typeCount * 4 <= rom.size &&
                        (0 until typeCount * typeCount).all { index ->
                            rom.u32le(offset + index * 4) in TYPE_MULTIPLIERS_Q12
                        } &&
                        rom.u32le(offset + (1 * typeCount + 6) * 4) == 2048L &&
                        rom.u32le(offset + (1 * typeCount + 8) * 4) == 0L &&
                        rom.u32le(offset + (2 * typeCount + 1) * 4) == 8192L
                }
            }
        }
        return candidates.singleOrNull()
    }

    private fun alignToWord(value: Int): Int = (value + 3) and -4

    private fun sampleIds(count: Int, maximum: Int): List<Int> {
        val live = count - 1
        return (1..minOf(live, maximum)).toList()
    }

    private fun sampleIdsAcrossExtent(count: Int, maximum: Int): List<Int> {
        val last = count - 1
        if (last <= maximum) return (1..last).toList()
        if (maximum == 1) return listOf(1)
        return List(maximum) { index ->
            1 + (index.toLong() * (last - 1) / (maximum - 1)).toInt()
        }.distinct()
    }

    private fun plausibleStats(rom: RomImage, offset: Int): Boolean =
        (0 until 6).all { rom.u8(offset + it) in 1..255 } &&
            rom.u8(offset + 6) in 0..31 && rom.u8(offset + 7) in 0..31

    private fun plausibleLevelUp(rom: RomImage, offset: Int, moveCount: Int): Boolean {
        val move = rom.u16le(offset)
        val level = rom.u16le(offset + 2)
        return move == 0xFFFF || (move in 0 until moveCount && level in 0..100)
    }

    private fun plausibleMoveList(rom: RomImage, offset: Int, moveCount: Int): Boolean {
        val move = rom.u16le(offset)
        return move == 0xFFFF || move in 0 until moveCount
    }

    private fun inferEvolutionRecordSize(
        rom: RomImage,
        speciesBase: Int,
        species: SpeciesShape,
        speciesCount: Int,
    ): Int? {
        val active = (1 until speciesCount).filter { id ->
            rom.u16le(speciesBase + id * species.stride + species.nationalDexOffset) > 0
        }
        val evaluated = listOf(6, 8, 12).map { recordSize ->
            var validPointers = 0
            var invalidPointers = 0
            var edges = 0
            active.forEach { id ->
                val field = speciesBase + id * species.stride + species.levelUpOffset + 12
                if (rom.u32le(field) == 0L) return@forEach
                val result = rom.gbaPointer(field)?.let { pointer ->
                    validateEvolutionList(rom, pointer, recordSize, speciesCount)
                }
                if (result?.valid == true) {
                    validPointers++
                    edges += result.edges
                } else {
                    invalidPointers++
                }
            }
            EvolutionAbiCandidate(recordSize, validPointers, invalidPointers, edges)
        }
        val candidates = evaluated.filter { candidate ->
            candidate.validPointers > 0 && candidate.confidence >= 0.98
        }
        val ranking = compareBy<EvolutionAbiCandidate> { it.confidence }
            .thenBy { it.validPointers }
            .thenBy { it.edges }
        val best = candidates.maxWithOrNull(ranking) ?: return null
        return best.recordSize.takeUnless { selected ->
            candidates.any { candidate ->
                candidate.recordSize != selected && ranking.compare(candidate, best) == 0
            }
        }
    }

    private fun validateEvolutionList(
        rom: RomImage,
        offset: Int,
        recordSize: Int,
        speciesCount: Int,
    ): EvolutionListValidation {
        var cursor = offset
        var edges = 0
        repeat(32) {
            if (cursor !in 0..rom.size - 2) return EvolutionListValidation(false, edges)
            val method = rom.u16le(cursor)
            if (method == 0xFFFF) return EvolutionListValidation(true, edges)
            if (cursor.toLong() + recordSize > rom.size || method !in 0..1024) {
                return EvolutionListValidation(false, edges)
            }
            val target = rom.u16le(cursor + 4)
            if (target !in 1 until speciesCount) return EvolutionListValidation(false, edges)
            edges++
            cursor += recordSize
        }
        return EvolutionListValidation(false, edges)
    }

    private fun plausibleEvolution(rom: RomImage, offset: Int, speciesCount: Int): Boolean {
        val method = rom.u16le(offset)
        return method == 0xFFFF || (method in 0..1024 && rom.u16le(offset + 4) in 0 until speciesCount)
    }

    private fun plausibleInlineName(rom: RomImage, offset: Int, width: Int): Boolean = runCatching {
        if (rom.u8(offset) == 0 || rom.u8(offset) == codec.terminator) return@runCatching false
        val decoded = codec.decodeDetailed(rom.slice(offset, minOf(width, rom.size - offset)))
        decoded.terminated && decoded.text.isNotBlank() && decoded.validRatio >= 0.8
    }.getOrDefault(false)

    private fun decodeInline(rom: RomImage, offset: Int, width: Int): String = codec.decode(rom.slice(offset, width))

    private fun decodePointerName(rom: RomImage, pointerField: Int): String {
        val offset = rom.gbaPointer(pointerField) ?: return ""
        val length = (0 until minOf(32, rom.size - offset)).firstOrNull { rom.u8(offset + it) == codec.terminator }
            ?.plus(1) ?: minOf(32, rom.size - offset)
        return codec.decode(rom.slice(offset, length))
    }

    private data class EvolutionAbiCandidate(
        val recordSize: Int,
        val validPointers: Int,
        val invalidPointers: Int,
        val edges: Int,
    ) {
        val confidence: Double
            get() = validPointers.toDouble() / (validPointers + invalidPointers).coerceAtLeast(1)
    }

    private data class EvolutionListValidation(
        val valid: Boolean,
        val edges: Int,
    )

    private data class SpeciesShape(
        val stride: Int,
        val nameOffset: Int,
        val nameWidth: Int,
        val categoryOffset: Int,
        val nationalDexOffset: Int,
        val descriptionOffset: Int,
        val frontSpriteOffset: Int,
        val paletteOffset: Int,
        val abilitiesOffset: Int,
        val growthRateOffset: Int,
        val levelUpOffset: Int,
        val score: Int,
    )

    private data class AbilityShape(
        val stride: Int,
        val nameWidth: Int,
        val descriptionOffset: Int,
        val score: Int,
    )

    private data class TypeChartShape(val offset: Int, val typeCount: Int)

    private val TYPE_MULTIPLIERS_Q12 = setOf(0L, 2048L, 4096L, 8192L)
}
