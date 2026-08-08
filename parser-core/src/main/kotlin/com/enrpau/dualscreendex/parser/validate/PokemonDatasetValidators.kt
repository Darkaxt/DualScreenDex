package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.io.RomBoundsException
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

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
        result(valid, count, pointerTableOffset, 2, "valid Gen 1 Pokédex entries")
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

    private fun validGen1Description(rom: RomImage, offset: Int, codec: PokemonTextCodec): Boolean {
        val categoryEnd = terminatorOffset(rom, offset, 24, codec.terminator) ?: return false
        val metadata = categoryEnd + 1
        if (metadata + 9 > rom.size || rom.u8(metadata + 4) != GEN1_TEXT_FAR) return false
        val target = rom.gbBankAddress(rom.u8(metadata + 7), rom.u16le(metadata + 5)) ?: return false
        return decodeAt(rom, offset, 24, codec, 0.70) && decodeAt(rom, target, 512, codec, 0.55)
    }

    private fun validGen2Description(rom: RomImage, offset: Int, codec: PokemonTextCodec): Boolean {
        val categoryEnd = terminatorOffset(rom, offset, 24, codec.terminator) ?: return false
        val first = categoryEnd + 5
        val firstEnd = terminatorOffset(rom, first, 512, codec.terminator) ?: return false
        val second = firstEnd + 1
        return decodeAt(rom, offset, 24, codec, 0.70) &&
            decodeAt(rom, first, 512, codec, 0.55) &&
            decodeAt(rom, second, 512, codec, 0.55)
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
}
