package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomBoundsException
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.PokeemeraldExpansionMetadata
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
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

    fun validateDescriptions(rom: RomImage, resolution: PokeemeraldExpansionResolution): ValidationEvidence =
        validateSpeciesPointerField(
            rom,
            resolution,
            resolution.metadata.descriptionPointerOffset,
            "description",
            minimumRatio = 0.80,
        ) { pointer -> plausibleInlineName(rom, pointer, 512) }

    fun validateSprites(rom: RomImage, resolution: PokeemeraldExpansionResolution): ValidationEvidence =
        validateSpeciesPointerField(
            rom,
            resolution,
            resolution.metadata.frontSpritePointerOffset,
            "front sprite",
            minimumRatio = 0.80,
        ) { pointer -> pointer in 0 until rom.size }

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
        var valid = 0
        repeat(resolution.speciesCount) { id ->
            val field = table.offset + id * stride + resolution.metadata.evolutionPointerOffset
            val raw = rom.u32le(field)
            if (raw == 0L || rom.gbaPointer(field)?.let { plausibleEvolution(rom, it, resolution.speciesCount) } == true) valid++
        }
        val confidence = valid.toDouble() / resolution.speciesCount
        return ValidationEvidence(
            compatible = confidence >= 0.98,
            validRecords = valid,
            totalRecords = resolution.speciesCount,
            confidence = confidence,
            reasons = if (confidence >= 0.98) emptyList() else listOf("valid expansion evolution fields $valid/${resolution.speciesCount} below 98%"),
            offset = table.offset + resolution.metadata.evolutionPointerOffset,
            recordSize = 4,
            elementSize = 12,
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
        var valid = 0
        repeat(resolution.speciesCount) { id ->
            val pointer = rom.gbaPointer(table.offset + id * stride + fieldOffset)
            if (pointer != null && targetIsValid(pointer)) valid++
        }
        val confidence = valid.toDouble() / resolution.speciesCount
        return ValidationEvidence(
            compatible = confidence >= minimumRatio,
            validRecords = valid,
            totalRecords = resolution.speciesCount,
            confidence = confidence,
            reasons = if (confidence >= minimumRatio) emptyList() else listOf("valid expansion $label fields $valid/${resolution.speciesCount} below $minimumRatio"),
            offset = table.offset + fieldOffset,
            recordSize = 4,
        )
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
            evolutions = TableLayout(
                speciesBase + species.levelUpOffset + 12,
                speciesCount,
                4,
                stride = species.stride,
                valuesArePointers = true,
                elementSize = 12,
            ),
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
                val natDexOffset = ((nameOffset + 12)..minOf(stride - 2, nameOffset + 32))
                    .firstOrNull { field -> field % 2 == 0 && sample.count { id -> rom.u16le(base + id * stride + field) == id } >= sample.size * 0.90 }
                    ?: continue
                val levelUp = inferLearnsetPointerOffset(rom, base, stride, sample, count, moveCount)
                    ?: continue
                candidates += SpeciesShape(
                    stride = stride,
                    nameOffset = nameOffset,
                    nameWidth = 13,
                    categoryOffset = nameOffset - 13,
                    nationalDexOffset = natDexOffset,
                    descriptionOffset = natDexOffset + 16,
                    frontSpriteOffset = natDexOffset + 28,
                    paletteOffset = natDexOffset + 36,
                    abilitiesOffset = 24,
                    growthRateOffset = 21,
                    levelUpOffset = levelUp,
                    score = statsScore + names + categories,
                )
            }
        }
        return candidates.maxWithOrNull(compareBy<SpeciesShape> { it.score }.thenByDescending { it.stride })
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
        val sample = sampleIds(count, 32)
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

    private fun sampleIds(count: Int, maximum: Int): List<Int> {
        val live = count - 1
        return (1..minOf(live, maximum)).toList()
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
