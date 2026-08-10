package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomBoundsException
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Gen3LearnsetEncoding
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.validate.PokemonDatasetValidators
import kotlin.math.abs

object DatasetResolvers {
    fun reconciledMoveCount(inferredNameCount: Int?, moveData: ValidationEvidence): Int? = when {
        !moveData.compatible || moveData.totalRecords <= 0 -> inferredNameCount
        inferredNameCount == null -> moveData.totalRecords
        else -> minOf(inferredNameCount, moveData.totalRecords)
    }

    fun gen3Descriptions(
        rom: RomImage,
        speciesCount: Int,
        inherited: TableLayout?,
        codec: PokemonTextCodec,
    ): ValidationEvidence {
        inherited?.let { layout ->
            validateDescription(rom, speciesCount, layout, codec).takeIf { it.compatible }?.let { return it }
        }

        val candidates = mutableListOf<ValidationEvidence>()
        DESCRIPTION_LAYOUTS.forEach { layout ->
            DESCRIPTION_ANCHORS.forEach { anchor ->
                rom.findAll(anchor).forEach { seedOffset ->
                    val tableOffset = seedOffset - layout.recordSize
                    if (tableOffset >= 0 && tableOffset % 4 == 0 && validDescriptionStart(rom, tableOffset, codec)) {
                        validateDescription(
                            rom,
                            speciesCount,
                            TableLayout(
                                tableOffset,
                                speciesCount,
                                layout.recordSize,
                                pointerOffsets = layout.pointerOffsets,
                            ),
                            codec,
                        ).takeIf { it.compatible }?.let(candidates::add)
                            ?: inferDescription(
                                rom,
                                speciesCount,
                                TableLayout(
                                    tableOffset,
                                    speciesCount,
                                    layout.recordSize,
                                    pointerOffsets = layout.pointerOffsets,
                                ),
                                codec,
                            ).takeIf { it.compatible }?.let(candidates::add)
                    }
                }
            }
        }
        return choose(candidates, inherited?.offset, "Gen 3 Pokédex description table", coverageFirst = true)
    }

    fun gen3Evolutions(
        rom: RomImage,
        speciesCount: Int,
        inherited: TableLayout?,
    ): ValidationEvidence {
        inherited?.let { layout ->
            validateEvolution(rom, speciesCount, layout).takeIf { it.compatible }?.let { return it }
        }

        val candidates = mutableListOf<ValidationEvidence>()
        val levelEvolutionMethods = rom.findAll(byteArrayOf(4, 0))
        for (recordSize in listOf(8, 6)) {
            for (slots in listOf(5, 8, 16, 32)) {
                val stride = slots * recordSize
                levelEvolutionMethods.asSequence().map { firstSpeciesOffset -> firstSpeciesOffset - stride }
                    .filter { tableOffset -> tableOffset >= 0 && tableOffset % 2 == 0 }
                    .filter { tableOffset ->
                        val first = tableOffset + stride
                        val second = tableOffset + stride * 2
                        second + 6 <= rom.size &&
                            rom.u16le(first + 2) in 1..100 && rom.u16le(first + 4) == 2 &&
                            rom.u16le(second) == 4 && rom.u16le(second + 2) in 1..100 && rom.u16le(second + 4) == 3
                    }
                    .distinct()
                    .forEach { tableOffset ->
                    validateEvolution(
                        rom,
                        speciesCount,
                        TableLayout(tableOffset, speciesCount, stride, elementSize = recordSize),
                    ).takeIf { it.compatible }?.let(candidates::add)
                    }
            }
        }
        return choose(candidates, inherited?.offset, "Gen 3 evolution table")
    }

    fun gen3Learnsets(
        rom: RomImage,
        speciesCount: Int,
        moveCount: Int,
        inherited: TableLayout?,
    ): ValidationEvidence {
        val moveBits = Gen3LearnsetEncoding.packedMoveBits(moveCount)
        inherited?.let { layout ->
            PokemonDatasetValidators.gen3Learnsets(
                rom, layout.offset, speciesCount, moveCount, moveBits,
            ).takeIf { it.compatible }?.let { return it }
        }

        val candidates = mutableListOf<ValidationEvidence>()
        val last = rom.size - speciesCount * 4
        var offset = 0
        while (offset <= last) {
            try {
                val none = rom.u32le(offset)
                val firstTarget = rom.gbaPointer(offset)
                val firstIsEmpty = firstTarget != null && firstTarget <= rom.size - 2 &&
                    rom.u16le(firstTarget) == 0xFFFF
                val noneReusesFirstSpecies =
                    none == rom.u32le(offset + 4) && none in 0x08000000L..0x09FFFFFFL
                if (firstIsEmpty || noneReusesFirstSpecies) {
                    val evidence = PokemonDatasetValidators.gen3Learnsets(
                        rom, offset, speciesCount, moveCount, moveBits,
                    )
                    if (evidence.compatible) candidates += evidence
                }
            } catch (_: RomBoundsException) {
                break
            }
            offset += 4
        }
        if (candidates.isNotEmpty()) {
            return choose(candidates, inherited?.offset, "Gen 3 packed learnset pointer table")
        }

        offset = 0
        while (offset <= last) {
            try {
                val empty = rom.gbaPointer(offset)
                if (empty != null && rom.u16le(empty) == 0 && rom.u8(empty + 2) == 0xFF) {
                    val evidence = PokemonDatasetValidators.gen3ExpandedLearnsets(
                        rom, offset, speciesCount, moveCount,
                    )
                    if (evidence.compatible) candidates += evidence
                }
            } catch (_: RomBoundsException) {
                break
            }
            offset += 4
        }
        if (candidates.isEmpty()) {
            val referencedTargets = linkedSetOf<Int>()
            offset = 0
            while (offset <= rom.size - 4) {
                rom.gbaPointer(offset)?.let { target ->
                    if (target % 4 == 0 && target <= last) referencedTargets += target
                }
                offset += 4
            }
            referencedTargets.forEach { tableOffset ->
                val firstLearnset = rom.gbaPointer(tableOffset) ?: return@forEach
                if (!looksLikeExpandedLearnset(rom, firstLearnset, moveCount)) return@forEach
                val evidence = PokemonDatasetValidators.gen3ExpandedLearnsets(
                    rom, tableOffset, speciesCount, moveCount,
                )
                if (evidence.compatible) candidates += evidence
            }
        }
        return choose(candidates, inherited?.offset, "Gen 3 CFRU/DPE learnset pointer table")
    }

    private fun looksLikeExpandedLearnset(rom: RomImage, start: Int, moveCount: Int): Boolean {
        var offset = start
        var previousLevel = 0
        repeat(256) {
            if (offset + 3 > rom.size) return false
            val move = rom.u16le(offset)
            val level = rom.u8(offset + 2)
            if (move == 0 && level == 0xFF) return true
            if (move !in 1..moveCount || level !in 0..100) return false
            if (level > 0) {
                if (level < previousLevel.coerceAtLeast(1)) return false
                previousLevel = level
            }
            offset += 3
        }
        return false
    }

    private fun validateDescription(
        rom: RomImage,
        count: Int,
        layout: TableLayout,
        codec: PokemonTextCodec,
    ): ValidationEvidence {
        val pointerOffsets = layout.pointerOffsets.ifEmpty {
            if (layout.recordSize >= 36) listOf(16, 20) else listOf(16)
        }
        return PokemonDatasetValidators.gen3Descriptions(
            rom, layout.offset, count, layout.recordSize, pointerOffsets.toIntArray(), codec,
        )
    }

    private fun inferDescription(
        rom: RomImage,
        maximumCount: Int,
        layout: TableLayout,
        codec: PokemonTextCodec,
    ): ValidationEvidence {
        val pointerOffsets = layout.pointerOffsets.ifEmpty {
            if (layout.recordSize >= 36) listOf(16, 20) else listOf(16)
        }
        return PokemonDatasetValidators.inferGen3DescriptionCount(
            rom = rom,
            offset = layout.offset,
            maximumCount = maximumCount,
            minimumCount = minOf(300, maxOf(2, maximumCount / 4)),
            recordSize = layout.recordSize,
            descriptionPointerOffsets = pointerOffsets.toIntArray(),
            codec = codec,
        )
    }

    private fun validDescriptionStart(rom: RomImage, offset: Int, codec: PokemonTextCodec): Boolean = runCatching {
        codec.decode(rom.slice(offset, 12)).equals("Unknown", ignoreCase = true) &&
            rom.u16le(offset + 12) == 0 && rom.u16le(offset + 14) == 0
    }.getOrDefault(false)

    private fun validateEvolution(rom: RomImage, count: Int, layout: TableLayout): ValidationEvidence {
        val elementSize = layout.elementSize ?: 6
        if (layout.recordSize <= 0 || layout.recordSize % elementSize != 0) {
            return missing("invalid Gen 3 evolution layout metadata")
        }
        return PokemonDatasetValidators.gen3Evolutions(
            rom,
            layout.offset,
            count,
            slotsPerSpecies = layout.recordSize / elementSize,
            recordSize = elementSize,
        )
    }

    private fun choose(
        candidates: List<ValidationEvidence>,
        inheritedOffset: Int?,
        label: String,
        coverageFirst: Boolean = false,
    ): ValidationEvidence {
        val unique = candidates.distinctBy { it.offset to it.recordSize }
        if (unique.isEmpty()) return missing("$label not resolved by structural validation")
        val distance: (ValidationEvidence) -> Int = { evidence ->
            inheritedOffset?.let { abs((evidence.offset ?: 0) - it) } ?: (evidence.offset ?: 0)
        }
        val ranked = if (coverageFirst) {
            unique.sortedWith(
                compareByDescending<ValidationEvidence> { it.validRecords }
                    .thenByDescending { it.confidence }
                    .thenBy(distance),
            )
        } else {
            unique.sortedWith(
                compareByDescending<ValidationEvidence> { it.confidence }
                    .thenByDescending { it.validRecords }
                    .thenBy(distance),
            )
        }
        if (ranked.size > 1) {
            val first = ranked[0]
            val second = ranked[1]
            val firstDistance = distance(first)
            val secondDistance = distance(second)
            if (first.confidence == second.confidence && first.validRecords == second.validRecords && firstDistance == secondDistance) {
                return missing("$label has conflicting structural candidates")
            }
        }
        return ranked.first()
    }

    private fun gbaText(value: String): ByteArray = ByteArray(value.length + 1).also { bytes ->
        value.forEachIndexed { index, character ->
            bytes[index] = when (character) {
                ' ' -> 0
                in 'A'..'Z' -> 0xBB + (character - 'A')
                in 'a'..'z' -> 0xD5 + (character - 'a')
                else -> error("unsupported anchor character")
            }.toByte()
        }
        bytes[value.length] = 0xFF.toByte()
    }

    private fun missing(reason: String) = ValidationEvidence(false, 0, 0, 0.0, listOf(reason))

    private val DESCRIPTION_LAYOUTS = listOf(
        TableLayout(0, 0, 32, pointerOffsets = listOf(16)),
        TableLayout(0, 0, 36, pointerOffsets = listOf(16)),
        TableLayout(0, 0, 36, pointerOffsets = listOf(16, 20)),
    )
    private val DESCRIPTION_ANCHORS = listOf(gbaText("SEED"), gbaText("Seed"))
}
