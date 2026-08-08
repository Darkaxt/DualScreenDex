package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.io.RomBoundsException
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

data class CombinedEvolutionLearnsetEvidence(
    val evolutions: ValidationEvidence,
    val learnsets: ValidationEvidence,
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
    ): ValidationEvidence = safely(offset, slotsPerSpecies * recordSize, speciesCount) {
        require(recordSize >= 6) { "evolution record size must contain three u16 fields" }
        var valid = 0
        repeat(speciesCount) { species ->
            val base = offset + species * slotsPerSpecies * recordSize
            val slotsValid = (0 until slotsPerSpecies).all { slot ->
                val record = base + slot * recordSize
                val method = rom.u16le(record)
                val parameter = rom.u16le(record + 2)
                val target = rom.u16le(record + 4)
                if (method == 0) {
                    parameter == 0 && target == 0
                } else {
                    method in 1..maximumMethod && target in 1 until speciesCount
                }
            }
            if (slotsValid) valid++
        }
        result(valid, speciesCount, offset, slotsPerSpecies * recordSize, "valid Gen 3 evolution records", 0.90)
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
        repeat(speciesCount) { index ->
            val offset = rom.gbaPointer(pointerTableOffset + index * 4)
            if (offset != null && validGen3Learnset(rom, offset, moveCount, moveBits)) valid++
        }
        result(valid, speciesCount, pointerTableOffset, 4, "valid Gen 3 learnsets", 0.90)
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

        var previousLevel = 0
        var learnsetValid = true
        var learnsetEntries = 0
        while (learnsetEntries < MAX_LEARNSET_ENTRIES) {
            val level = rom.u8(cursor)
            if (level == 0) break
            val move = rom.u8(cursor + 1)
            if (level !in previousLevel.coerceAtLeast(1)..100 || move !in 1..moveCount) {
                learnsetValid = false
                break
            }
            previousLevel = level
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

    private fun validGen3Learnset(rom: RomImage, offset: Int, moveCount: Int, moveBits: Int): Boolean {
        var cursor = offset
        var previousLevel = 0
        val moveMask = (1 shl moveBits) - 1
        repeat(MAX_LEARNSET_ENTRIES) {
            val packed = rom.u16le(cursor)
            if (packed == GEN3_LEARNSET_END) return true
            val move = packed and moveMask
            val level = packed ushr moveBits
            if (move !in 1..moveCount || level !in previousLevel.coerceAtLeast(1)..100) return false
            previousLevel = level
            cursor += 2
        }
        return false
    }

    private fun validGen3ExpandedLearnset(rom: RomImage, offset: Int, moveCount: Int): Boolean {
        var cursor = offset
        var previousLevel = 0
        repeat(MAX_LEARNSET_ENTRIES) {
            val move = rom.u16le(cursor)
            val level = rom.u8(cursor + 2)
            if (move == 0 && level == 0xFF) return true
            if (move !in 1..moveCount || level !in 0..100) return false
            if (level > 0) {
                if (level < previousLevel.coerceAtLeast(1)) return false
                previousLevel = level
            }
            cursor += 3
        }
        return false
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

    private data class Gen12SpeciesValidation(
        val evolutions: Boolean,
        val learnset: Boolean,
    )
}
