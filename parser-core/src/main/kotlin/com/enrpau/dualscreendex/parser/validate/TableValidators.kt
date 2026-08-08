package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.io.RomBoundsException
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

object TableValidators {
    fun fixedNames(
        rom: RomImage,
        offset: Int,
        count: Int,
        width: Int,
        codec: PokemonTextCodec,
        minimumRatio: Double = 0.85,
    ): ValidationEvidence = safely(offset, width, count) {
        var valid = 0
        val reasons = mutableListOf<String>()
        repeat(count) { index ->
            val decoded = codec.decodeDetailed(rom.slice(offset + index * width, width))
            val plausible = decoded.terminated && decoded.text.isNotBlank() && decoded.validRatio >= minimumRatio
            if (plausible) valid++
        }
        val confidence = valid.toDouble() / count.coerceAtLeast(1)
        if (confidence < minimumRatio) reasons += "valid fixed names $valid/$count below $minimumRatio"
        ValidationEvidence(confidence >= minimumRatio, valid, count, confidence, reasons, offset, width)
    }

    fun inferFixedNameCount(
        rom: RomImage,
        offset: Int,
        width: Int,
        codec: PokemonTextCodec,
        minimumCount: Int,
        maximumCount: Int,
    ): Int? {
        if (offset < 0 || width <= 0 || offset >= rom.size) return null
        var lastGood = 0
        var consecutiveInvalid = 0
        for (index in 0 until maximumCount) {
            val recordOffset = offset.toLong() + index.toLong() * width
            if (recordOffset + width > rom.size) break
            val decoded = codec.decodeDetailed(rom.slice(recordOffset.toInt(), width))
            val valid = decoded.terminated && decoded.text.isNotBlank() && decoded.validRatio >= 0.8
            if (valid) {
                lastGood = index + 1
                consecutiveInvalid = 0
            } else {
                consecutiveInvalid++
                if (consecutiveInvalid >= 3 && index + 1 >= minimumCount) break
            }
        }
        return lastGood.takeIf { it >= minimumCount }
    }

    fun variableNames(
        rom: RomImage,
        offset: Int,
        count: Int,
        codec: PokemonTextCodec,
        maximumWidth: Int = 24,
    ): ValidationEvidence = safely(offset, 0, count) {
        var cursor = offset
        var valid = 0
        val reasons = mutableListOf<String>()
        repeat(count) {
            val buffer = ArrayList<Byte>()
            var terminated = false
            var length = 0
            while (length < maximumWidth && cursor < rom.size && !terminated) {
                val value = rom.u8(cursor++)
                buffer += value.toByte()
                if (value == codec.terminator) {
                    terminated = true
                }
                length++
            }
            val decoded = codec.decodeDetailed(buffer.toByteArray())
            if (terminated && decoded.text.isNotBlank() && decoded.validRatio >= 0.8) valid++
        }
        val confidence = valid.toDouble() / count.coerceAtLeast(1)
        if (confidence < 0.85) reasons += "valid variable names $valid/$count below 85%"
        ValidationEvidence(confidence >= 0.85, valid, count, confidence, reasons, offset, null)
    }

    fun baseStats(
        rom: RomImage,
        offset: Int,
        count: Int,
        recordSize: Int,
        generation: Int,
    ): ValidationEvidence = safely(offset, recordSize, count) {
        var valid = 0
        repeat(count) { index ->
            val base = offset + index * recordSize
            val statStart = if (generation == 1) 1 else 0
            val statCount = if (generation == 1) 5 else 6
            val statsValid = (0 until statCount).all { rom.u8(base + statStart + it) in 1..255 }
            val typeOffset = if (generation == 1) 6 else 6
            val maxType = if (generation == 3) 17 else 27
            val typesValid = rom.u8(base + typeOffset) in 0..maxType && rom.u8(base + typeOffset + 1) in 0..maxType
            if (statsValid && typesValid) valid++
        }
        val confidence = valid.toDouble() / count.coerceAtLeast(1)
        val compatible = confidence >= 0.90
        ValidationEvidence(
            compatible, valid, count, confidence,
            if (compatible) emptyList() else listOf("plausible base stats $valid/$count below 90%"),
            offset, recordSize,
        )
    }

    fun moveData(
        rom: RomImage,
        offset: Int,
        count: Int,
        recordSize: Int,
        generation: Int,
    ): ValidationEvidence = safely(offset, recordSize, count) {
        var valid = 0
        repeat(count) { index ->
            val base = offset + index * recordSize
            if (generation == 3 && index == 0) {
                valid++
            } else {
                val type = rom.u8(base + if (generation == 1) 3 else if (generation == 2) 3 else 2)
                val pp = rom.u8(base + if (generation == 3) 4 else 5)
                val accuracy = rom.u8(base + if (generation == 3) 3 else 4)
                val maxType = if (generation == 3) 17 else 27
                if (type in 0..maxType && pp in 1..64 && accuracy in 0..255) valid++
            }
        }
        val confidence = valid.toDouble() / count.coerceAtLeast(1)
        val compatible = confidence >= 0.90
        ValidationEvidence(
            compatible, valid, count, confidence,
            if (compatible) emptyList() else listOf("plausible move records $valid/$count below 90%"),
            offset, recordSize,
        )
    }

    fun gbaPointerTable(
        rom: RomImage,
        offset: Int,
        count: Int,
        recordSize: Int,
    ): ValidationEvidence = safely(offset, recordSize, count) {
        var valid = 0
        repeat(count) { index ->
            if (rom.gbaPointer(offset + index * recordSize) != null) valid++
        }
        val confidence = valid.toDouble() / count.coerceAtLeast(1)
        val compatible = confidence >= 0.90
        ValidationEvidence(
            compatible, valid, count, confidence,
            if (compatible) emptyList() else listOf("valid GBA pointers $valid/$count below 90%"),
            offset, recordSize,
        )
    }

    fun typeChart(
        rom: RomImage,
        offset: Int,
        generation: Int,
        maximumEntries: Int = 256,
    ): ValidationEvidence = safely(offset, 3, maximumEntries) {
        var cursor = offset
        var valid = 0
        var terminated = false
        val maxType = if (generation == 3) 17 else 27
        repeat(maximumEntries) {
            val attacker = rom.u8(cursor)
            if (attacker == 0xFE || attacker == 0xFF) {
                terminated = true
                return@repeat
            }
            val defender = rom.u8(cursor + 1)
            val multiplier = rom.u8(cursor + 2)
            if (attacker in 0..maxType && defender in 0..maxType && multiplier in setOf(0, 5, 10, 20)) {
                valid++
            }
            cursor += 3
        }
        val compatible = terminated && valid >= 10
        ValidationEvidence(
            compatible, valid, valid, if (compatible) 1.0 else 0.0,
            if (compatible) emptyList() else listOf("type chart lacks a valid terminator or enough entries"),
            offset, 3,
        )
    }

    private inline fun safely(
        offset: Int,
        recordSize: Int?,
        count: Int,
        block: () -> ValidationEvidence,
    ): ValidationEvidence = try {
        block()
    } catch (error: RomBoundsException) {
        ValidationEvidence(false, 0, count, 0.0, listOf(error.message ?: "out-of-bounds table"), offset, recordSize)
    }
}
