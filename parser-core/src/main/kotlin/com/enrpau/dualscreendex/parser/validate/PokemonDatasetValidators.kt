package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.io.RomBoundsException
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import java.util.Locale

data class CombinedEvolutionLearnsetEvidence(
    val evolutions: ValidationEvidence,
    val learnsets: ValidationEvidence,
)

internal data class Gen3WideLearnsetValidation(
    val evidence: ValidationEvidence,
    val structuralQuality: Double,
)

internal data class Gen3WideLearnsetRecord(
    val level: Int,
    val moveId: Int,
)

internal data class Gen3EvolutionValidation(
    val evidence: ValidationEvidence,
    val structuralQuality: Double,
    val activeEdges: Int,
)

internal data class Gen3EvolutionRecord(
    val methodId: Int,
    val parameter: Int,
    val targetSpeciesId: Int,
    val conditionValue: Int?,
    val raw: ByteArray,
)

internal data class Gen3EvolutionRowValidation(
    val records: List<Gen3EvolutionRecord>,
    val validSlots: Int,
    val invalidSlots: Int,
)

object PokemonDatasetValidators {
    fun gen1Descriptions(
        rom: RomImage,
        pointerTableOffset: Int,
        count: Int,
        entryBank: Int,
        codec: PokemonTextCodec,
    ): ValidationEvidence = safely(pointerTableOffset, 2, count) {
        var valid = 0
        repeat(count) { index ->
            val entry = rom.gbBankAddress(entryBank, rom.u16le(pointerTableOffset + index * 2))
            if (entry != null && validGen1Description(rom, entry, codec)) valid++
        }
        // The 190-slot Gen I internal index contains MissingNo entries without Pokédex data.
        result(valid, count, pointerTableOffset, 2, "valid Gen 1 Pokédex entries", 0.75)
    }

    fun gen2Descriptions(
        rom: RomImage,
        pointerTableOffset: Int,
        count: Int,
        entryBanks: IntArray,
        entriesPerBank: Int = 64,
        codec: PokemonTextCodec,
    ): ValidationEvidence = safely(pointerTableOffset, 2, count) {
        var valid = 0
        repeat(count) { index ->
            val bank = entryBanks.getOrNull(index / entriesPerBank)
            val entry = bank?.let { rom.gbBankAddress(it, rom.u16le(pointerTableOffset + index * 2)) }
            if (entry != null && validGen2Description(rom, entry, codec)) valid++
        }
        result(valid, count, pointerTableOffset, 2, "valid Gen 2 Pokédex entries")
    }

    fun gen3Descriptions(
        rom: RomImage,
        offset: Int,
        count: Int,
        recordSize: Int,
        descriptionPointerOffsets: IntArray,
        codec: PokemonTextCodec,
    ): ValidationEvidence = safely(offset, recordSize, count) {
        var valid = 0
        repeat(count) { index ->
            val base = offset + index * recordSize
            val category = decodeAt(rom, base, 12, codec, 0.70)
            val dimensions = rom.u16le(base + 12) in 0..10000 && rom.u16le(base + 14) in 0..100000
            val descriptions = descriptionPointerOffsets.all { fieldOffset ->
                rom.gbaPointer(base + fieldOffset)?.let { decodeAt(rom, it, 512, codec, 0.65) } == true
            }
            if (category && dimensions && descriptions) valid++
        }
        result(valid, count, offset, recordSize, "valid Gen 3 Pokédex entries")
    }

    fun inferGen3DescriptionCount(
        rom: RomImage,
        offset: Int,
        maximumCount: Int,
        minimumCount: Int,
        recordSize: Int,
        descriptionPointerOffsets: IntArray,
        codec: PokemonTextCodec,
    ): ValidationEvidence = safely(offset, recordSize, maximumCount) {
        var valid = 0
        var lastValid = 0
        var consecutiveInvalid = 0
        repeat(maximumCount) { index ->
            val base = offset + index * recordSize
            if (validGen3DescriptionRecord(rom, base, descriptionPointerOffsets, codec)) {
                valid++
                lastValid = index + 1
                consecutiveInvalid = 0
            } else {
                consecutiveInvalid++
                if (lastValid >= minimumCount && consecutiveInvalid >= 4) {
                    return@safely result(valid, lastValid, offset, recordSize, "valid Gen 3 Pokédex prefix")
                }
            }
        }
        result(valid, lastValid, offset, recordSize, "valid Gen 3 Pokédex prefix")
    }

    fun gen12EvolutionsAndLearnsets(
        rom: RomImage,
        pointerTableOffset: Int,
        speciesCount: Int,
        tableBank: Int,
        moveCount: Int,
        generation: Int,
    ): CombinedEvolutionLearnsetEvidence = try {
        var validEvolutions = 0
        var validLearnsets = 0
        repeat(speciesCount) { index ->
            val offset = rom.gbBankAddress(tableBank, rom.u16le(pointerTableOffset + index * 2))
            if (offset != null) {
                val parsed = validateGen12SpeciesRecord(rom, offset, speciesCount, moveCount, generation)
                if (parsed.evolutions) validEvolutions++
                if (parsed.learnset) validLearnsets++
            }
        }
        CombinedEvolutionLearnsetEvidence(
            result(validEvolutions, speciesCount, pointerTableOffset, 2, "valid Gen $generation evolution records", 0.90),
            result(validLearnsets, speciesCount, pointerTableOffset, 2, "valid Gen $generation learnsets", 0.90),
        )
    } catch (error: RomBoundsException) {
        val failed = ValidationEvidence(
            false, 0, speciesCount, 0.0,
            listOf(error.message ?: "out-of-bounds table"), pointerTableOffset, 2,
        )
        CombinedEvolutionLearnsetEvidence(failed, failed)
    }

    fun gen3Evolutions(
        rom: RomImage,
        offset: Int,
        speciesCount: Int,
        slotsPerSpecies: Int,
        maximumMethod: Int = 255,
        recordSize: Int = 6,
    ): ValidationEvidence = gen3EvolutionValidation(
        rom, offset, speciesCount, slotsPerSpecies, maximumMethod, recordSize,
    ).evidence

    internal fun gen3EvolutionValidation(
        rom: RomImage,
        offset: Int,
        speciesCount: Int,
        slotsPerSpecies: Int,
        maximumMethod: Int = 255,
        recordSize: Int = 6,
    ): Gen3EvolutionValidation {
        val stride = slotsPerSpecies.toLong() * recordSize.toLong()
        val extent = stride * speciesCount.toLong()
        if (speciesCount <= 0 || slotsPerSpecies <= 0 || recordSize !in setOf(6, 8) ||
            offset < 0 || stride > Int.MAX_VALUE || extent > Int.MAX_VALUE ||
            offset.toLong() + extent > rom.size.toLong()
        ) {
            return Gen3EvolutionValidation(
                evidence = ValidationEvidence(
                    compatible = false,
                    validRecords = 0,
                    totalRecords = speciesCount.coerceAtLeast(0),
                    confidence = 0.0,
                    reasons = listOf("Gen 3 evolution table is out of bounds or has invalid layout metadata"),
                    offset = offset,
                    recordSize = stride.takeIf { it in 1..Int.MAX_VALUE }?.toInt(),
                    elementSize = recordSize.takeIf { it in setOf(6, 8) },
                ),
                structuralQuality = 0.0,
                activeEdges = 0,
            )
        }

        var validRows = 0
        var validSlots = 0
        var invalidSlots = 0
        var activeEdges = 0
        repeat(speciesCount) { species ->
            if (species == 0) {
                validRows++
                validSlots += slotsPerSpecies
            } else {
                val row = decodeGen3EvolutionRow(
                    rom = rom,
                    offset = offset + species * stride.toInt(),
                    slotsPerSpecies = slotsPerSpecies,
                    recordSize = recordSize,
                    speciesCount = speciesCount,
                    maximumMethod = maximumMethod,
                )
                validSlots += row.validSlots
                invalidSlots += row.invalidSlots
                activeEdges += row.records.size
                if (row.invalidSlots == 0) validRows++
            }
        }
        val confidence = validRows.toDouble() / speciesCount
        val structuralQuality = validSlots.toDouble() / (validSlots + invalidSlots).coerceAtLeast(1)
        val compatible = activeEdges > 0 && confidence > GEN3_EVOLUTION_STRUCTURAL_CREDIBILITY_FLOOR
        val reasons = buildList {
            if (compatible && validRows < speciesCount) {
                add("partial Gen 3 evolution coverage $validRows/$speciesCount; manual review recommended")
                add("evolution slot quality $validSlots/${validSlots + invalidSlots}; $invalidSlots invalid slots skipped")
            }
            if (activeEdges == 0) add("no active evolution edges were found")
            if (confidence <= GEN3_EVOLUTION_STRUCTURAL_CREDIBILITY_FLOOR) {
                add("valid Gen 3 evolution rows $validRows/$speciesCount do not have a strict majority")
            }
        }
        return Gen3EvolutionValidation(
            evidence = ValidationEvidence(
                compatible = compatible,
                validRecords = validRows,
                totalRecords = speciesCount,
                confidence = confidence,
                reasons = reasons,
                offset = offset,
                recordSize = stride.toInt(),
                elementSize = recordSize,
            ),
            structuralQuality = structuralQuality,
            activeEdges = activeEdges,
        )
    }

    internal fun decodeGen3EvolutionRow(
        rom: RomImage,
        offset: Int,
        slotsPerSpecies: Int,
        recordSize: Int,
        speciesCount: Int,
        maximumMethod: Int = 255,
    ): Gen3EvolutionRowValidation {
        if (offset < 0 || slotsPerSpecies <= 0 || recordSize !in setOf(6, 8) || speciesCount <= 0) {
            return Gen3EvolutionRowValidation(emptyList(), 0, slotsPerSpecies.coerceAtLeast(0))
        }
        val records = mutableListOf<Gen3EvolutionRecord>()
        var validSlots = 0
        var invalidSlots = 0
        repeat(slotsPerSpecies) { slot ->
            val recordOffset = offset.toLong() + slot.toLong() * recordSize
            if (recordOffset < 0 || recordOffset + recordSize > rom.size.toLong()) {
                invalidSlots++
                return@repeat
            }
            val record = recordOffset.toInt()
            val method = rom.u16le(record)
            val parameter = rom.u16le(record + 2)
            val target = rom.u16le(record + 4)
            val conditionValue = if (recordSize == 8) rom.u16le(record + 6) else null
            val structurallyValid = (
                method == 0 || (
                    (method in 1..maximumMethod || method in GEN3_RESERVED_TRANSFORMATION_METHODS) &&
                        target in 1 until speciesCount
                )
            )
            if (!structurallyValid) {
                invalidSlots++
                return@repeat
            }
            validSlots++
            if (method != 0) {
                records += Gen3EvolutionRecord(
                    methodId = method,
                    parameter = parameter,
                    targetSpeciesId = target,
                    conditionValue = conditionValue,
                    raw = rom.slice(record, recordSize),
                )
            }
        }
        return Gen3EvolutionRowValidation(records, validSlots, invalidSlots)
    }

    fun gen3Learnsets(
        rom: RomImage,
        pointerTableOffset: Int,
        speciesCount: Int,
        moveCount: Int,
        moveBits: Int = 9,
    ): ValidationEvidence = safely(pointerTableOffset, 4, speciesCount) {
        require(moveBits in 1..15) { "move bit width must be between 1 and 15" }
        var valid = 0
        var clean = 0
        var recovered = 0
        var quarantined = 0
        var missingPointers = 0
        val offsets = List(speciesCount) { index ->
            rom.gbaPointer(pointerTableOffset + index * 4)
        }
        val boundaries = Gen3PackedLearnsetDecoder.adjacentPointerBoundaries(offsets)
        repeat(speciesCount) { index ->
            val offset = offsets[index]
            if (offset == null) {
                missingPointers++
            } else {
                when (
                    Gen3PackedLearnsetDecoder.decode(
                        rom, offset, moveCount, moveBits, endExclusive = boundaries[offset],
                    ).disposition
                ) {
                    Gen3PackedLearnsetDisposition.CLEAN -> {
                        valid++
                        clean++
                    }
                    Gen3PackedLearnsetDisposition.RECOVERED_SHORT_TAIL -> {
                        valid++
                        recovered++
                    }
                    Gen3PackedLearnsetDisposition.QUARANTINED -> quarantined++
                }
            }
        }
        val base = result(valid, speciesCount, pointerTableOffset, 4, "valid Gen 3 learnsets", 0.90)
        val recoveredRowsDominate = recovered > 0 && recovered >= clean
        base.copy(
            compatible = base.compatible && !recoveredRowsDominate,
            elementSize = 2,
            format = TableRecordFormat.GEN3_PACKED_U16,
            incompleteRecords = (quarantined + missingPointers).takeIf { it > 0 },
            reviewRecommended = recovered > 0 || quarantined > 0 || missingPointers > 0,
            reasons = base.reasons + buildList {
                if (recovered > 0) add("recovered $recovered short malformed tails before the Gen 3 learnset terminator")
                if (recoveredRowsDominate) add("recovered packed rows do not have a strict clean-row majority")
                if (quarantined > 0) add("quarantined $quarantined malformed rows without a bounded terminator tail")
                if (missingPointers > 0) add("$missingPointers Gen 3 learnset pointers were null or out of bounds")
            },
        )
    }

    fun gen3ExpandedLearnsets(
        rom: RomImage,
        pointerTableOffset: Int,
        speciesCount: Int,
        moveCount: Int,
    ): ValidationEvidence = safely(pointerTableOffset, 4, speciesCount) {
        var valid = 0
        repeat(speciesCount) { index ->
            val offset = rom.gbaPointer(pointerTableOffset + index * 4)
            if (offset != null && validGen3ExpandedLearnset(rom, offset, moveCount)) valid++
        }
        result(valid, speciesCount, pointerTableOffset, 4, "valid CFRU/DPE Gen 3 learnsets", 0.90)
            .copy(elementSize = 3, format = TableRecordFormat.GEN3_MOVE_U16_LEVEL_U8)
    }

    fun gen3LevelMoveLearnsets(
        rom: RomImage,
        pointerTableOffset: Int,
        speciesCount: Int,
        moveCount: Int,
    ): ValidationEvidence = safely(pointerTableOffset, 4, speciesCount) {
        val offsets = List(speciesCount) { index ->
            rom.gbaPointer(pointerTableOffset + index * 4)
        }
        val boundaries = Gen3PackedLearnsetDecoder.adjacentPointerBoundaries(offsets)
        val valid = offsets.count { offset ->
            offset != null && decodeGen3LevelMoveLearnset(
                rom = rom,
                offset = offset,
                moveCount = moveCount,
                endExclusive = boundaries[offset],
            ) != null
        }
        result(
            valid,
            speciesCount,
            pointerTableOffset,
            4,
            "valid compiled level-byte/move-halfword Gen 3 learnsets",
            0.90,
        ).copy(
            elementSize = 3,
            format = TableRecordFormat.GEN3_LEVEL_U8_MOVE_U16,
        )
    }

    internal fun decodeGen3LevelMoveLearnset(
        rom: RomImage,
        offset: Int,
        moveCount: Int,
        endExclusive: Int? = null,
    ): List<Gen3WideLearnsetRecord>? {
        if (offset !in 0 until rom.size || moveCount <= 1) return null
        val boundary = minOf(endExclusive ?: rom.size, rom.size)
        if (boundary <= offset) return null
        val records = mutableListOf<Gen3WideLearnsetRecord>()
        var cursor = offset
        repeat(256) {
            if (cursor >= boundary) return null
            val level = rom.u8(cursor)
            if (level == 0xFE) return records
            if (cursor + 3 > boundary) return null
            val move = rom.u16le(cursor + 1)
            if (level !in 0..100 || move !in 1 until moveCount) {
                return null
            }
            records += Gen3WideLearnsetRecord(level = level, moveId = move)
            cursor += 3
        }
        return null
    }

    fun gen3WideLearnsets(
        rom: RomImage,
        pointerTableOffset: Int,
        speciesCount: Int,
        moveCount: Int,
    ): ValidationEvidence = gen3WideLearnsetValidation(
        rom, pointerTableOffset, speciesCount, moveCount,
    ).evidence

    internal fun gen3WideLearnsetValidation(
        rom: RomImage,
        pointerTableOffset: Int,
        speciesCount: Int,
        moveCount: Int,
    ): Gen3WideLearnsetValidation {
        if (speciesCount <= 0 || pointerTableOffset < 0 ||
            pointerTableOffset.toLong() + speciesCount.toLong() * 4 > rom.size
        ) {
            return Gen3WideLearnsetValidation(
                evidence = ValidationEvidence(
                    compatible = false,
                    validRecords = 0,
                    totalRecords = speciesCount.coerceAtLeast(0),
                    confidence = 0.0,
                    reasons = listOf("wide Gen 3 learnset pointer table is out of bounds or empty"),
                    offset = pointerTableOffset,
                    recordSize = 4,
                    elementSize = 4,
                    format = TableRecordFormat.GEN3_MOVE_U16_LEVEL_U16,
                ),
                structuralQuality = 0.0,
            )
        }

        var valid = 0
        var nullPointers = 0
        var invalid = 0
        repeat(speciesCount) { index ->
            val pointerCell = pointerTableOffset + index * 4
            if (rom.u32le(pointerCell) == 0L) {
                nullPointers++
            } else {
                val offset = rom.gbaPointer(pointerCell)
                val structurallyValid = offset?.let { target ->
                    decodeGen3WideLearnset(rom, target, moveCount)
                } != null
                if (structurallyValid) valid++ else invalid++
            }
        }
        val structuralRecords = valid + invalid
        val structuralQuality = valid.toDouble() / structuralRecords.coerceAtLeast(1)
        val coverage = valid.toDouble() / speciesCount
        val minimumEvidence = minOf(speciesCount, MIN_WIDE_LEARNSET_EVIDENCE)
        val compatible = valid >= minimumEvidence && structuralQuality > WIDE_LEARNSET_STRUCTURAL_CREDIBILITY_FLOOR
        val coveragePercent = String.format(Locale.ROOT, "%.2f%%", coverage * 100.0)
        val structuralPercent = String.format(Locale.ROOT, "%.2f%%", structuralQuality * 100.0)
        val summary = "wide Gen 3 learnset coverage $valid/$speciesCount ($coveragePercent); " +
            "structural quality $valid/$structuralRecords ($structuralPercent); $nullPointers null; $invalid invalid"
        val reasons = buildList {
            add(summary)
            if (valid < minimumEvidence) add("fewer than $minimumEvidence structurally valid wide learnsets")
            if (structuralQuality <= WIDE_LEARNSET_STRUCTURAL_CREDIBILITY_FLOOR) {
                add("wide learnset records do not have a strict valid non-null majority")
            }
        }
        return Gen3WideLearnsetValidation(
            evidence = ValidationEvidence(
                compatible = compatible,
                validRecords = valid,
                totalRecords = speciesCount,
                confidence = coverage,
                reasons = reasons,
                offset = pointerTableOffset,
                recordSize = 4,
                elementSize = 4,
                format = TableRecordFormat.GEN3_MOVE_U16_LEVEL_U16,
            ),
            structuralQuality = structuralQuality,
        )
    }

    private fun validGen1Description(rom: RomImage, offset: Int, codec: PokemonTextCodec): Boolean {
        val categoryEnd = terminatorOffset(rom, offset, 24, codec.terminator) ?: return false
        val metadata = categoryEnd + 1
        if (metadata + 9 > rom.size || rom.u8(metadata + 4) != GEN1_TEXT_FAR) return false
        val target = rom.gbBankAddress(rom.u8(metadata + 7), rom.u16le(metadata + 5)) ?: return false
        return decodeAt(rom, offset, 24, codec, 0.70) && decodeAt(rom, target, 512, codec, 0.55)
    }

    private fun validGen2Description(rom: RomImage, offset: Int, codec: PokemonTextCodec): Boolean {
        val categoryEnd = terminatorOffset(rom, offset, 24, codec.terminator) ?: return false
        val description = categoryEnd + 5
        return decodeAt(rom, offset, 24, codec, 0.70) &&
            terminatorOffset(rom, description, 512, codec.terminator) != null
    }

    private fun validateGen12SpeciesRecord(
        rom: RomImage,
        offset: Int,
        speciesCount: Int,
        moveCount: Int,
        generation: Int,
    ): Gen12SpeciesValidation {
        var cursor = offset
        var evolutionsValid = generation in 1..2
        var evolutionEntries = 0
        while (evolutionsValid && evolutionEntries < MAX_EVOLUTIONS_PER_SPECIES) {
            val method = rom.u8(cursor)
            if (method == 0) break
            val width = evolutionWidth(generation, method)
            if (width == null) {
                evolutionsValid = false
            } else {
                val target = rom.u8(cursor + width - 1)
                if (target !in 1..speciesCount) evolutionsValid = false
                cursor += width
                evolutionEntries++
            }
        }
        if (!evolutionsValid || rom.u8(cursor) != 0) return Gen12SpeciesValidation(false, false)
        cursor++

        var learnsetValid = true
        var learnsetEntries = 0
        while (learnsetEntries < MAX_LEARNSET_ENTRIES) {
            val level = rom.u8(cursor)
            if (level == 0) break
            val move = rom.u8(cursor + 1)
            if (level !in 1..100 || move !in 1..moveCount) {
                learnsetValid = false
                break
            }
            cursor += 2
            learnsetEntries++
        }
        if (rom.u8(cursor) != 0) learnsetValid = false
        return Gen12SpeciesValidation(true, learnsetValid)
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

    private fun validGen3ExpandedLearnset(rom: RomImage, offset: Int, moveCount: Int): Boolean {
        var cursor = offset
        repeat(MAX_LEARNSET_ENTRIES) {
            val move = rom.u16le(cursor)
            val level = rom.u8(cursor + 2)
            if (move == 0 && level == 0xFF) return true
            if (move !in 1..moveCount || level !in 0..100) return false
            cursor += 3
        }
        return false
    }

    internal fun decodeGen3WideLearnset(
        rom: RomImage,
        offset: Int,
        moveCount: Int,
    ): List<Gen3WideLearnsetRecord>? {
        if (offset !in 0 until rom.size || moveCount <= 0) return null
        val records = mutableListOf<Gen3WideLearnsetRecord>()
        var cursor = offset
        try {
            repeat(MAX_LEARNSET_ENTRIES) {
                val move = rom.u16le(cursor)
                if (move == GEN3_LEARNSET_END) return records
                val level = rom.u16le(cursor + 2)
                if (move !in 1..moveCount || level !in 0..100) return null
                records += Gen3WideLearnsetRecord(level = level, moveId = move)
                cursor += 4
            }
        } catch (_: RomBoundsException) {
            return null
        }
        return null
    }

    private fun validGen3DescriptionRecord(
        rom: RomImage,
        base: Int,
        descriptionPointerOffsets: IntArray,
        codec: PokemonTextCodec,
    ): Boolean {
        val category = decodeAt(rom, base, 12, codec, 0.70)
        val dimensions = rom.u16le(base + 12) in 0..10000 && rom.u16le(base + 14) in 0..100000
        val descriptions = descriptionPointerOffsets.all { fieldOffset ->
            rom.gbaPointer(base + fieldOffset)?.let { decodeAt(rom, it, 512, codec, 0.65) } == true
        }
        return category && dimensions && descriptions
    }

    private fun decodeAt(
        rom: RomImage,
        offset: Int,
        maximumLength: Int,
        codec: PokemonTextCodec,
        minimumValidRatio: Double,
    ): Boolean {
        val end = terminatorOffset(rom, offset, maximumLength, codec.terminator) ?: return false
        val decoded = codec.decodeDetailed(rom.slice(offset, end - offset + 1))
        return decoded.terminated && decoded.text.isNotBlank() && decoded.validRatio >= minimumValidRatio
    }

    private fun terminatorOffset(rom: RomImage, offset: Int, maximumLength: Int, terminator: Int): Int? {
        if (offset !in 0 until rom.size || maximumLength <= 0) return null
        val end = minOf(rom.size, offset + maximumLength)
        for (cursor in offset until end) {
            if (rom.u8(cursor) == terminator) return cursor
        }
        return null
    }

    private fun result(
        valid: Int,
        count: Int,
        offset: Int,
        recordSize: Int,
        label: String,
        minimumRatio: Double = 0.85,
    ): ValidationEvidence {
        val confidence = valid.toDouble() / count.coerceAtLeast(1)
        val compatible = count > 0 && confidence >= minimumRatio
        return ValidationEvidence(
            compatible = compatible,
            validRecords = valid,
            totalRecords = count,
            confidence = confidence,
            reasons = if (compatible) emptyList() else listOf("$label $valid/$count below $minimumRatio"),
            offset = offset,
            recordSize = recordSize,
            coveredRecords = valid,
            expectedRecords = count,
        )
    }

    private inline fun safely(
        offset: Int,
        recordSize: Int,
        count: Int,
        block: () -> ValidationEvidence,
    ): ValidationEvidence = try {
        block()
    } catch (error: RomBoundsException) {
        ValidationEvidence(
            false, 0, count, 0.0,
            listOf(error.message ?: "out-of-bounds table"), offset, recordSize,
        )
    }

    private const val GEN1_TEXT_FAR = 0x17
    private const val MAX_EVOLUTIONS_PER_SPECIES = 16
    private const val MAX_LEARNSET_ENTRIES = 128
    private const val GEN3_LEARNSET_END = 0xFFFF
    private const val MIN_WIDE_LEARNSET_EVIDENCE = 3
    private const val WIDE_LEARNSET_STRUCTURAL_CREDIBILITY_FLOOR = 0.50
    private const val GEN3_EVOLUTION_STRUCTURAL_CREDIBILITY_FLOOR = 0.50
    private val GEN3_RESERVED_TRANSFORMATION_METHODS = 0xFFFD..0xFFFF

    private data class Gen12SpeciesValidation(
        val evolutions: Boolean,
        val learnset: Boolean,
    )
}
